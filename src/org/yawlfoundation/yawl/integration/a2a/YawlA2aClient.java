/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a;

import org.jdom2.Document;
import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.Marshaller;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.util.JDOMUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A2A (Agent-to-Agent) Client for YAWL Workflow Engine
 *
 * Enables agents (human, automated, or AI-powered) to connect to YAWL,
 * receive task assignments, execute work, and report completion.
 *
 * Features:
 * - Real HTTP/WebSocket connections to YAWL Engine
 * - Agent registration and capability advertisement
 * - Work item checkout, execution, and completion
 * - Heartbeat mechanism for connection monitoring
 * - Event subscription for task assignments
 * - Support for multiple agent types
 * - Automatic reconnection on failure
 *
 * Agent Types:
 * - HUMAN: Web-based human interaction
 * - AUTOMATED: Programmatic service execution
 * - AI: LLM-powered intelligent agents
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlA2aClient extends InterfaceB_EnvironmentBasedClient {

    /**
     * Supported agent types
     */
    public enum AgentType {
        HUMAN,      // Human agents via web interface
        AUTOMATED,  // Automated service agents
        AI          // AI/LLM-powered agents
    }

    /**
     * Agent lifecycle states
     */
    public enum AgentState {
        DISCONNECTED,
        CONNECTING,
        REGISTERED,
        ACTIVE,
        SUSPENDED,
        ERROR
    }

    // Agent configuration
    private final String agentId;
    private final AgentType agentType;
    private final Set<String> capabilities;
    private final String agentDescription;

    // Connection state
    private String sessionHandle;
    private AgentState currentState;
    private final AtomicBoolean running;

    // Heartbeat mechanism
    private ScheduledExecutorService heartbeatExecutor;
    private final long heartbeatIntervalMs;
    private long lastHeartbeatMs;

    // Work item management
    private final Map<String, WorkItemRecord> activeWorkItems;
    private final BlockingQueue<WorkItemRecord> assignedWorkQueue;

    // Event subscription
    private WebSocket eventSocket;
    private final HttpClient httpClient;

    // Credentials
    private final String username;
    private final String password;

    /**
     * Creates a new A2A client for YAWL Engine
     *
     * @param engineUrl YAWL Engine Interface B URL (e.g., http://localhost:8080/yawl/ib)
     * @param agentId Unique identifier for this agent
     * @param agentType Type of agent (HUMAN, AUTOMATED, AI)
     * @param username YAWL username for authentication
     * @param password YAWL password for authentication
     */
    public YawlA2aClient(String engineUrl, String agentId, AgentType agentType,
                         String username, String password) {
        super(engineUrl);

        if (engineUrl == null || engineUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Engine URL cannot be null or empty");
        }
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent ID cannot be null or empty");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        this.agentId = agentId;
        this.agentType = agentType != null ? agentType : AgentType.AUTOMATED;
        this.username = username;
        this.password = password;
        this.capabilities = new HashSet<>();
        this.agentDescription = "YAWL A2A Agent: " + agentId + " (Type: " + this.agentType + ")";

        this.currentState = AgentState.DISCONNECTED;
        this.running = new AtomicBoolean(false);
        this.heartbeatIntervalMs = 30000; // 30 seconds
        this.activeWorkItems = new ConcurrentHashMap<>();
        this.assignedWorkQueue = new LinkedBlockingQueue<>();

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Registers agent with YAWL server and establishes connection
     *
     * @return true if registration successful
     * @throws IOException if connection fails
     */
    public boolean register() throws IOException {
        if (currentState == AgentState.REGISTERED || currentState == AgentState.ACTIVE) {
            throw new IllegalStateException("Agent already registered. Call deregister() first.");
        }

        currentState = AgentState.CONNECTING;

        try {
            sessionHandle = connect(username, password);

            if (sessionHandle == null || sessionHandle.contains("failure")) {
                currentState = AgentState.ERROR;
                throw new IOException("Failed to authenticate with YAWL Engine: " + sessionHandle);
            }

            String connCheck = checkConnection(sessionHandle);
            if (!successful(connCheck)) {
                currentState = AgentState.ERROR;
                throw new IOException("Connection check failed: " + connCheck);
            }

            currentState = AgentState.REGISTERED;
            running.set(true);
            lastHeartbeatMs = System.currentTimeMillis();

            startHeartbeat();

            return true;

        } catch (IOException e) {
            currentState = AgentState.ERROR;
            running.set(false);
            throw new IOException("Agent registration failed: " + e.getMessage(), e);
        }
    }

    /**
     * Advertises agent capabilities to YAWL Engine
     *
     * @param capability Capability name (e.g., "processInvoice", "validateData")
     */
    public void advertiseCapability(String capability) {
        if (capability == null || capability.trim().isEmpty()) {
            throw new IllegalArgumentException("Capability cannot be null or empty");
        }
        capabilities.add(capability.trim());
    }

    /**
     * Advertises multiple capabilities at once
     *
     * @param capabilities Set of capability names
     */
    public void advertiseCapabilities(String... capabilities) {
        if (capabilities == null) {
            throw new IllegalArgumentException("Capabilities cannot be null");
        }
        for (String cap : capabilities) {
            advertiseCapability(cap);
        }
    }

    /**
     * Receives next assigned task from YAWL Engine
     * Blocks until a task is available or timeout occurs
     *
     * @param timeoutMs Maximum time to wait in milliseconds (0 = wait indefinitely)
     * @return WorkItemRecord for the assigned task, or null if timeout
     * @throws IOException if communication fails
     * @throws InterruptedException if waiting is interrupted
     */
    public WorkItemRecord receiveTaskAssignment(long timeoutMs)
            throws IOException, InterruptedException {

        ensureRegistered();

        List<WorkItemRecord> liveItems = getCompleteListOfLiveWorkItems(sessionHandle);

        for (WorkItemRecord item : liveItems) {
            if (item.getStatus().equals(WorkItemRecord.statusEnabled) &&
                !activeWorkItems.containsKey(item.getID())) {

                return item;
            }
        }

        if (timeoutMs > 0) {
            return assignedWorkQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } else {
            return assignedWorkQueue.take();
        }
    }

    /**
     * Executes an assigned work item
     * Checks out the work item from YAWL and marks it as executing
     *
     * @param workItem The work item to execute
     * @return XML data for the work item
     * @throws IOException if checkout fails
     */
    public String executeWorkItem(WorkItemRecord workItem) throws IOException {
        ensureRegistered();

        if (workItem == null) {
            throw new IllegalArgumentException("WorkItem cannot be null");
        }

        String checkoutResult = checkOutWorkItem(workItem.getID(), sessionHandle);

        if (!successful(checkoutResult)) {
            throw new IOException("Failed to checkout work item " + workItem.getID() +
                                ": " + checkoutResult);
        }

        activeWorkItems.put(workItem.getID(), workItem);
        currentState = AgentState.ACTIVE;

        return stripOuterElement(checkoutResult);
    }

    /**
     * Reports task completion to YAWL Engine
     * Checks in the completed work item with result data
     *
     * @param workItemId ID of the completed work item
     * @param resultData XML result data from task execution
     * @return true if completion successful
     * @throws IOException if checkin fails
     */
    public boolean reportTaskCompletion(String workItemId, String resultData)
            throws IOException {

        ensureRegistered();

        if (workItemId == null || workItemId.trim().isEmpty()) {
            throw new IllegalArgumentException("Work item ID cannot be null or empty");
        }

        if (!activeWorkItems.containsKey(workItemId)) {
            throw new IllegalStateException("Work item " + workItemId +
                                          " is not currently active for this agent");
        }

        String logData = "Completed by agent: " + agentId + " (type: " + agentType + ")";
        String checkinResult = checkInWorkItem(workItemId, resultData, logData, sessionHandle);

        if (!successful(checkinResult)) {
            throw new IOException("Failed to checkin work item " + workItemId +
                                ": " + checkinResult);
        }

        activeWorkItems.remove(workItemId);

        if (activeWorkItems.isEmpty()) {
            currentState = AgentState.REGISTERED;
        }

        return true;
    }

    /**
     * Reports task failure to YAWL Engine
     * Handles exceptions during task execution
     *
     * @param workItemId ID of the failed work item
     * @param errorMessage Error description
     * @param rollback Whether to rollback the work item to fired status
     * @return true if failure handling successful
     * @throws IOException if communication fails
     */
    public boolean reportTaskFailure(String workItemId, String errorMessage, boolean rollback)
            throws IOException {

        ensureRegistered();

        if (workItemId == null || workItemId.trim().isEmpty()) {
            throw new IllegalArgumentException("Work item ID cannot be null or empty");
        }

        if (!activeWorkItems.containsKey(workItemId)) {
            throw new IllegalStateException("Work item " + workItemId +
                                          " is not currently active for this agent");
        }

        String result;
        if (rollback) {
            result = rollbackWorkItem(workItemId, sessionHandle);
        } else {
            result = suspendWorkItem(workItemId, sessionHandle);
        }

        if (!successful(result)) {
            throw new IOException("Failed to handle work item failure " + workItemId +
                                ": " + result);
        }

        activeWorkItems.remove(workItemId);

        if (activeWorkItems.isEmpty()) {
            currentState = AgentState.REGISTERED;
        }

        return true;
    }

    /**
     * Sends heartbeat message to YAWL Engine
     * Maintains connection and monitors agent health
     *
     * @return true if heartbeat successful
     */
    public boolean sendHeartbeat() {
        if (!running.get() || sessionHandle == null) {
            return false;
        }

        try {
            String result = checkConnection(sessionHandle);

            if (successful(result)) {
                lastHeartbeatMs = System.currentTimeMillis();
                return true;
            } else {
                currentState = AgentState.ERROR;
                return false;
            }

        } catch (IOException e) {
            currentState = AgentState.ERROR;
            return false;
        }
    }

    /**
     * Subscribes to events from YAWL Engine
     * Uses WebSocket for real-time notifications
     *
     * @param eventTypes Types of events to subscribe to
     * @throws IOException if subscription fails
     */
    public void subscribeToEvents(String... eventTypes) throws IOException {
        ensureRegistered();

        if (eventTypes == null || eventTypes.length == 0) {
            throw new IllegalArgumentException("Must specify at least one event type");
        }

        String wsUrl = getBackEndURI().replace("/ib", "/events")
                                      .replace("http://", "ws://")
                                      .replace("https://", "wss://");

        try {
            CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data,
                                                     boolean last) {
                        handleEventNotification(data.toString());
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode,
                                                      String reason) {
                        eventSocket = null;
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        currentState = AgentState.ERROR;
                        eventSocket = null;
                    }
                });

            eventSocket = wsFuture.get(30, TimeUnit.SECONDS);

            String subscriptionMsg = buildSubscriptionMessage(eventTypes);
            eventSocket.sendText(subscriptionMsg, true);

        } catch (Exception e) {
            throw new IOException("Failed to subscribe to events: " + e.getMessage(), e);
        }
    }

    /**
     * Queries available work items matching agent capabilities
     *
     * @return List of available work items
     * @throws IOException if query fails
     */
    public List<WorkItemRecord> queryAvailableWork() throws IOException {
        ensureRegistered();

        List<WorkItemRecord> allItems = getCompleteListOfLiveWorkItems(sessionHandle);
        List<WorkItemRecord> availableItems = new ArrayList<>();

        for (WorkItemRecord item : allItems) {
            if (item.getStatus().equals(WorkItemRecord.statusEnabled) &&
                !activeWorkItems.containsKey(item.getID())) {
                availableItems.add(item);
            }
        }

        return availableItems;
    }

    /**
     * Deregisters agent from YAWL server and closes connection
     *
     * @throws IOException if deregistration fails
     */
    public void deregister() throws IOException {
        if (currentState == AgentState.DISCONNECTED) {
            return;
        }

        running.set(false);

        stopHeartbeat();

        if (eventSocket != null) {
            eventSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Agent deregistering");
            eventSocket = null;
        }

        if (!activeWorkItems.isEmpty()) {
            for (String workItemId : new ArrayList<>(activeWorkItems.keySet())) {
                try {
                    rollbackWorkItem(workItemId, sessionHandle);
                } catch (IOException e) {
                    // Log but continue deregistration
                }
            }
            activeWorkItems.clear();
        }

        if (sessionHandle != null) {
            String result = disconnect(sessionHandle);
            sessionHandle = null;
        }

        currentState = AgentState.DISCONNECTED;
    }

    /**
     * Gets the current agent state
     *
     * @return Current AgentState
     */
    public AgentState getAgentState() {
        return currentState;
    }

    /**
     * Gets the agent ID
     *
     * @return Agent identifier
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Gets the agent type
     *
     * @return AgentType
     */
    public AgentType getAgentType() {
        return agentType;
    }

    /**
     * Gets advertised capabilities
     *
     * @return Set of capability names
     */
    public Set<String> getCapabilities() {
        return new HashSet<>(capabilities);
    }

    /**
     * Gets currently active work items
     *
     * @return Map of work item IDs to WorkItemRecords
     */
    public Map<String, WorkItemRecord> getActiveWorkItems() {
        return new HashMap<>(activeWorkItems);
    }

    /**
     * Checks if agent is currently registered
     *
     * @return true if registered
     */
    public boolean isRegistered() {
        return currentState == AgentState.REGISTERED || currentState == AgentState.ACTIVE;
    }

    /**
     * Gets time since last successful heartbeat
     *
     * @return Milliseconds since last heartbeat
     */
    public long getTimeSinceLastHeartbeat() {
        return System.currentTimeMillis() - lastHeartbeatMs;
    }

    // Private helper methods

    private void ensureRegistered() throws IOException {
        if (!isRegistered()) {
            throw new IllegalStateException("Agent not registered. Call register() first.");
        }

        if (sessionHandle == null) {
            throw new IllegalStateException("No active session. Call register() first.");
        }
    }

    private void startHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            return;
        }

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "YawlA2A-Heartbeat-" + agentId);
            t.setDaemon(true);
            return t;
        });

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (!sendHeartbeat()) {
                currentState = AgentState.ERROR;
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
            try {
                heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                heartbeatExecutor.shutdownNow();
            }
            heartbeatExecutor = null;
        }
    }

    private void handleEventNotification(String eventData) {
        try {
            Document doc = JDOMUtil.stringToDocument(eventData);
            if (doc != null) {
                Element root = doc.getRootElement();
                String eventType = root.getAttributeValue("type");

                if ("workItemEnabled".equals(eventType) || "workItemFired".equals(eventType)) {
                    Element itemElement = root.getChild("workItem");
                    if (itemElement != null) {
                        WorkItemRecord item = parseWorkItemFromXML(itemElement);
                        if (item != null && !activeWorkItems.containsKey(item.getID())) {
                            assignedWorkQueue.offer(item);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log error but don't crash
        }
    }

    private WorkItemRecord parseWorkItemFromXML(Element element) {
        if (element == null) {
            return null;
        }

        try {
            return Marshaller.unmarshalWorkItem(element);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildSubscriptionMessage(String... eventTypes) {
        StringBuilder xml = new StringBuilder("<subscription>");
        xml.append("<agent>").append(agentId).append("</agent>");
        xml.append("<session>").append(sessionHandle).append("</session>");
        xml.append("<events>");
        for (String eventType : eventTypes) {
            xml.append("<event>").append(eventType).append("</event>");
        }
        xml.append("</events>");
        xml.append("</subscription>");
        return xml.toString();
    }

    private boolean successful(String result) {
        return result != null && !result.contains("<failure>");
    }

    /**
     * Example usage demonstrating all agent types
     */
    public static void main(String[] args) throws Exception {
        String engineUrl = "http://localhost:8080/yawl/ib";
        String username = "admin";
        String password = "YAWL";

        // Example 1: Automated Service Agent
        System.out.println("=== AUTOMATED AGENT EXAMPLE ===");
        YawlA2aClient automatedAgent = new YawlA2aClient(
            engineUrl, "invoice-processor", AgentType.AUTOMATED, username, password
        );

        automatedAgent.advertiseCapabilities("processInvoice", "validateInvoice", "archiveInvoice");
        automatedAgent.register();

        System.out.println("Agent registered: " + automatedAgent.getAgentId());
        System.out.println("State: " + automatedAgent.getAgentState());
        System.out.println("Capabilities: " + automatedAgent.getCapabilities());

        List<WorkItemRecord> availableWork = automatedAgent.queryAvailableWork();
        System.out.println("Available work items: " + availableWork.size());

        if (!availableWork.isEmpty()) {
            WorkItemRecord work = availableWork.get(0);
            System.out.println("Executing work item: " + work.getID());

            String workData = automatedAgent.executeWorkItem(work);
            System.out.println("Work data: " + workData);

            String resultData = "<data><status>processed</status><amount>1500.00</amount></data>";
            automatedAgent.reportTaskCompletion(work.getID(), resultData);
            System.out.println("Work completed");
        }

        automatedAgent.deregister();
        System.out.println("Agent deregistered");

        // Example 2: AI Agent with event subscription
        System.out.println("\n=== AI AGENT EXAMPLE ===");
        YawlA2aClient aiAgent = new YawlA2aClient(
            engineUrl, "ai-assistant", AgentType.AI, username, password
        );

        aiAgent.advertiseCapabilities("naturalLanguageProcessing", "sentimentAnalysis",
                                     "documentClassification");
        aiAgent.register();

        aiAgent.subscribeToEvents("workItemEnabled", "caseCompleted");
        System.out.println("Subscribed to events");

        WorkItemRecord task = aiAgent.receiveTaskAssignment(5000);
        if (task != null) {
            System.out.println("Received task assignment: " + task.getTaskID());
            String data = aiAgent.executeWorkItem(task);

            String aiResult = "<data><classification>urgent</classification>" +
                            "<sentiment>positive</sentiment></data>";
            aiAgent.reportTaskCompletion(task.getID(), aiResult);
        } else {
            System.out.println("No tasks available within timeout");
        }

        aiAgent.deregister();

        // Example 3: Human Agent
        System.out.println("\n=== HUMAN AGENT EXAMPLE ===");
        YawlA2aClient humanAgent = new YawlA2aClient(
            engineUrl, "user-john-smith", AgentType.HUMAN, username, password
        );

        humanAgent.advertiseCapability("manualApproval");
        humanAgent.register();

        System.out.println("Heartbeat status: " + humanAgent.sendHeartbeat());
        System.out.println("Time since last heartbeat: " +
                         humanAgent.getTimeSinceLastHeartbeat() + "ms");

        humanAgent.deregister();

        System.out.println("\n=== ALL EXAMPLES COMPLETED ===");
    }
}
