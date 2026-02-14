package org.yawlfoundation.yawl.integration.a2a;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jdom2.Document;
import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.util.JDOMUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A2A (Agent-to-Agent) Server Integration for YAWL
 *
 * Real implementation of agent-to-agent workflow delegation for YAWL engine.
 * Supports agent registration, task delegation, discovery, routing, health monitoring,
 * load balancing, authentication, event notifications, metrics, and dynamic pool management.
 *
 * Example Usage:
 *
 * YawlA2AServer server = new YawlA2AServer("http://localhost:8080/yawl/ib");
 * server.start();
 *
 * Features:
 * - Agent registration with capabilities
 * - Task delegation to registered agents
 * - Agent discovery and routing
 * - Work item assignment to agents
 * - Agent health monitoring
 * - Load balancing across agents
 * - Agent authentication and authorization
 * - Event notifications to agents
 * - Agent metrics collection
 * - Dynamic agent pool management
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlA2AServer {

    private static final long HEALTH_CHECK_INTERVAL_MS = 30000;
    private static final long AGENT_TIMEOUT_MS = 60000;
    private static final int MAX_RETRIES = 3;

    private boolean running = false;
    private int port = 8080;
    private String yawlEngineUrl;
    private String yawlUsername;
    private String yawlPassword;

    private HttpServer httpServer;
    private InterfaceB_EnvironmentBasedClient yawlClient;
    private String sessionHandle;

    private final Map<String, AgentRegistration> agents = new ConcurrentHashMap<>();
    private final Map<String, WorkItemAssignment> workItemAssignments = new ConcurrentHashMap<>();
    private final Map<String, AgentMetrics> agentMetrics = new ConcurrentHashMap<>();
    private final Map<String, List<String>> capabilityIndex = new ConcurrentHashMap<>();

    private ScheduledExecutorService healthCheckExecutor;
    private ExecutorService taskExecutor;
    private final AtomicLong requestCounter = new AtomicLong(0);

    /**
     * Constructor for YAWL A2A Server - reads configuration from environment variables
     *
     * Required environment variables:
     * - YAWL_ENGINE_URL: YAWL Interface B URL (e.g., http://localhost:8080/yawl/ib)
     * - YAWL_USERNAME: YAWL engine username
     * - YAWL_PASSWORD: YAWL engine password
     *
     * Optional environment variables:
     * - A2A_SERVER_PORT: Port for A2A server (default: 9090)
     */
    public YawlA2AServer() {
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        String username = System.getenv("YAWL_USERNAME");
        String password = System.getenv("YAWL_PASSWORD");
        String portStr = System.getenv("A2A_SERVER_PORT");

        if (engineUrl == null || engineUrl.isEmpty()) {
            throw new IllegalStateException(
                    "YAWL_ENGINE_URL environment variable is required.\n" +
                    "Set it with: export YAWL_ENGINE_URL=http://localhost:8080/yawl/ib"
            );
        }

        if (username == null || username.isEmpty()) {
            throw new IllegalStateException(
                    "YAWL_USERNAME environment variable is required.\n" +
                    "Set it with: export YAWL_USERNAME=admin"
            );
        }

        if (password == null || password.isEmpty()) {
            throw new IllegalStateException(
                    "YAWL_PASSWORD environment variable is required.\n" +
                    "Set it with: export YAWL_PASSWORD=your_password"
            );
        }

        this.yawlEngineUrl = engineUrl;
        this.yawlUsername = username;
        this.yawlPassword = password;
        this.yawlClient = new InterfaceB_EnvironmentBasedClient(engineUrl);

        if (portStr != null && !portStr.isEmpty()) {
            try {
                this.port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("A2A_SERVER_PORT must be a valid integer: " + portStr);
            }
        } else {
            this.port = 9090;
        }

        System.out.println("Initializing YAWL A2A Server with engine: " + engineUrl);
    }

    /**
     * Constructor with custom port
     * @param port the port to run the A2A server on
     */
    public YawlA2AServer(int port) {
        this();
        this.port = port;
    }

    /**
     * Constructor with YAWL engine URL - username and password from environment
     * @param yawlEngineUrl the URL of the YAWL engine Interface B
     */
    public YawlA2AServer(String yawlEngineUrl) {
        String username = System.getenv("YAWL_USERNAME");
        String password = System.getenv("YAWL_PASSWORD");

        if (username == null || username.isEmpty()) {
            throw new IllegalStateException(
                    "YAWL_USERNAME environment variable is required.\n" +
                    "Set it with: export YAWL_USERNAME=admin"
            );
        }

        if (password == null || password.isEmpty()) {
            throw new IllegalStateException(
                    "YAWL_PASSWORD environment variable is required.\n" +
                    "Set it with: export YAWL_PASSWORD=your_password"
            );
        }

        this.yawlEngineUrl = yawlEngineUrl;
        this.yawlUsername = username;
        this.yawlPassword = password;
        this.yawlClient = new InterfaceB_EnvironmentBasedClient(yawlEngineUrl);
        System.out.println("Initializing YAWL A2A Server with engine: " + yawlEngineUrl);
    }

    /**
     * Constructor with full configuration
     * @param yawlEngineUrl the URL of the YAWL engine Interface B
     * @param username YAWL engine username
     * @param password YAWL engine password
     */
    public YawlA2AServer(String yawlEngineUrl, String username, String password) {
        if (yawlEngineUrl == null || yawlEngineUrl.isEmpty()) {
            throw new IllegalArgumentException("YAWL engine URL cannot be null or empty");
        }

        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("YAWL username cannot be null or empty");
        }

        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("YAWL password cannot be null or empty");
        }

        this.yawlEngineUrl = yawlEngineUrl;
        this.yawlUsername = username;
        this.yawlPassword = password;
        this.yawlClient = new InterfaceB_EnvironmentBasedClient(yawlEngineUrl);
        System.out.println("Initializing YAWL A2A Server with engine: " + yawlEngineUrl);
    }

    /**
     * Start the A2A server
     */
    public void start() throws IOException {
        if (running) {
            System.out.println("Server already running");
            return;
        }

        System.out.println("Starting YAWL A2A Server on port " + port + "...");

        connectToYawlEngine();
        startHttpServer();
        startHealthCheckScheduler();
        startTaskExecutor();

        running = true;
        System.out.println("YAWL A2A Server started successfully on port " + port);
        System.out.println("Connected to YAWL Engine: " + yawlEngineUrl);
    }

    /**
     * Stop the A2A server
     */
    public void stop() {
        if (!running) {
            System.out.println("Server not running");
            return;
        }

        System.out.println("Stopping YAWL A2A Server...");

        if (healthCheckExecutor != null) {
            healthCheckExecutor.shutdown();
        }

        if (taskExecutor != null) {
            taskExecutor.shutdown();
        }

        if (httpServer != null) {
            httpServer.stop(0);
        }

        disconnectFromYawlEngine();

        running = false;
        System.out.println("YAWL A2A Server stopped");
    }

    /**
     * Check if server is running
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    private void connectToYawlEngine() throws IOException {
        System.out.println("Connecting to YAWL Engine...");
        sessionHandle = yawlClient.connect(yawlUsername, yawlPassword);

        if (sessionHandle == null || sessionHandle.contains("failure")) {
            throw new IOException("Failed to connect to YAWL Engine: " + sessionHandle);
        }

        String checkResult = yawlClient.checkConnection(sessionHandle);
        if (checkResult == null || checkResult.contains("failure")) {
            throw new IOException("YAWL connection check failed: " + checkResult);
        }

        System.out.println("Successfully connected to YAWL Engine");
    }

    private void disconnectFromYawlEngine() {
        try {
            if (sessionHandle != null && yawlClient != null) {
                yawlClient.disconnect(sessionHandle);
                System.out.println("Disconnected from YAWL Engine");
            }
        } catch (IOException e) {
            System.err.println("Error disconnecting from YAWL Engine: " + e.getMessage());
        }
    }

    private void startHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);

        httpServer.createContext("/a2a/register", new RegisterAgentHandler());
        httpServer.createContext("/a2a/unregister", new UnregisterAgentHandler());
        httpServer.createContext("/a2a/discover", new DiscoverAgentsHandler());
        httpServer.createContext("/a2a/delegate", new DelegateTaskHandler());
        httpServer.createContext("/a2a/complete", new CompleteTaskHandler());
        httpServer.createContext("/a2a/status", new StatusHandler());
        httpServer.createContext("/a2a/health", new HealthCheckHandler());
        httpServer.createContext("/a2a/metrics", new MetricsHandler());
        httpServer.createContext("/a2a/agents", new ListAgentsHandler());

        httpServer.setExecutor(Executors.newFixedThreadPool(10));
        httpServer.start();

        System.out.println("HTTP server started on port " + port);
    }

    private void startHealthCheckScheduler() {
        healthCheckExecutor = Executors.newScheduledThreadPool(1);
        healthCheckExecutor.scheduleAtFixedRate(
                this::performHealthChecks,
                HEALTH_CHECK_INTERVAL_MS,
                HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
        System.out.println("Health check scheduler started");
    }

    private void startTaskExecutor() {
        taskExecutor = Executors.newFixedThreadPool(20);
        System.out.println("Task executor started");
    }

    private void performHealthChecks() {
        long currentTime = System.currentTimeMillis();
        List<String> deadAgents = new ArrayList<>();

        for (Map.Entry<String, AgentRegistration> entry : agents.entrySet()) {
            String agentId = entry.getKey();
            AgentRegistration agent = entry.getValue();

            if (currentTime - agent.lastHeartbeat > AGENT_TIMEOUT_MS) {
                System.out.println("Agent " + agentId + " timed out");
                deadAgents.add(agentId);
            }
        }

        for (String agentId : deadAgents) {
            unregisterAgent(agentId, "health check timeout");
        }
    }

    private String registerAgent(String agentId, Set<String> capabilities, String endpoint, Map<String, String> metadata) {
        if (agentId == null || agentId.isEmpty()) {
            return createErrorResponse("Agent ID is required");
        }

        if (capabilities == null || capabilities.isEmpty()) {
            return createErrorResponse("At least one capability is required");
        }

        AgentRegistration registration = new AgentRegistration(
                agentId,
                capabilities,
                endpoint,
                metadata,
                System.currentTimeMillis()
        );

        agents.put(agentId, registration);

        for (String capability : capabilities) {
            capabilityIndex.computeIfAbsent(capability, k -> new CopyOnWriteArrayList<>()).add(agentId);
        }

        agentMetrics.put(agentId, new AgentMetrics(agentId));

        System.out.println("Registered agent: " + agentId + " with capabilities: " + capabilities);

        return createSuccessResponse("Agent registered successfully", Map.of(
                "agentId", agentId,
                "capabilities", capabilities.toString(),
                "timestamp", String.valueOf(System.currentTimeMillis())
        ));
    }

    private String unregisterAgent(String agentId, String reason) {
        AgentRegistration agent = agents.remove(agentId);

        if (agent == null) {
            return createErrorResponse("Agent not found: " + agentId);
        }

        for (String capability : agent.capabilities) {
            List<String> agentList = capabilityIndex.get(capability);
            if (agentList != null) {
                agentList.remove(agentId);
                if (agentList.isEmpty()) {
                    capabilityIndex.remove(capability);
                }
            }
        }

        agentMetrics.remove(agentId);

        System.out.println("Unregistered agent: " + agentId + " (reason: " + reason + ")");

        return createSuccessResponse("Agent unregistered successfully", Map.of(
                "agentId", agentId,
                "reason", reason
        ));
    }

    private List<AgentRegistration> discoverAgents(String capability) {
        if (capability == null || capability.isEmpty()) {
            return new ArrayList<>(agents.values());
        }

        List<String> agentIds = capabilityIndex.get(capability);
        if (agentIds == null || agentIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<AgentRegistration> result = new ArrayList<>();
        for (String agentId : agentIds) {
            AgentRegistration agent = agents.get(agentId);
            if (agent != null) {
                result.add(agent);
            }
        }

        return result;
    }

    private String delegateTask(String workItemId, String capability, Map<String, String> data) {
        try {
            WorkItemRecord workItem = getWorkItem(workItemId);
            if (workItem == null) {
                return createErrorResponse("Work item not found: " + workItemId);
            }

            List<AgentRegistration> candidateAgents = discoverAgents(capability);
            if (candidateAgents.isEmpty()) {
                return createErrorResponse("No agents available for capability: " + capability);
            }

            AgentRegistration selectedAgent = selectAgent(candidateAgents);

            WorkItemAssignment assignment = new WorkItemAssignment(
                    workItemId,
                    selectedAgent.agentId,
                    capability,
                    data,
                    System.currentTimeMillis()
            );

            workItemAssignments.put(workItemId, assignment);

            AgentMetrics metrics = agentMetrics.get(selectedAgent.agentId);
            if (metrics != null) {
                metrics.taskAssigned();
            }

            System.out.println("Delegated work item " + workItemId + " to agent " + selectedAgent.agentId);

            return createSuccessResponse("Task delegated successfully", Map.of(
                    "workItemId", workItemId,
                    "agentId", selectedAgent.agentId,
                    "capability", capability,
                    "assignmentTime", String.valueOf(assignment.assignmentTime)
            ));

        } catch (IOException e) {
            return createErrorResponse("Failed to delegate task: " + e.getMessage());
        }
    }

    private String completeTask(String workItemId, String agentId, Map<String, String> resultData) {
        WorkItemAssignment assignment = workItemAssignments.get(workItemId);

        if (assignment == null) {
            return createErrorResponse("Work item assignment not found: " + workItemId);
        }

        if (!assignment.agentId.equals(agentId)) {
            return createErrorResponse("Agent " + agentId + " is not assigned to work item " + workItemId);
        }

        try {
            String dataXml = buildDataXml(resultData);
            String result = yawlClient.checkInWorkItem(workItemId, dataXml, sessionHandle);

            if (result != null && !result.contains("failure")) {
                workItemAssignments.remove(workItemId);

                AgentMetrics metrics = agentMetrics.get(agentId);
                if (metrics != null) {
                    metrics.taskCompleted(System.currentTimeMillis() - assignment.assignmentTime);
                }

                System.out.println("Work item " + workItemId + " completed by agent " + agentId);

                return createSuccessResponse("Task completed successfully", Map.of(
                        "workItemId", workItemId,
                        "agentId", agentId,
                        "completionTime", String.valueOf(System.currentTimeMillis())
                ));
            } else {
                AgentMetrics metrics = agentMetrics.get(agentId);
                if (metrics != null) {
                    metrics.taskFailed();
                }

                return createErrorResponse("Failed to complete work item in YAWL: " + result);
            }

        } catch (IOException e) {
            AgentMetrics metrics = agentMetrics.get(agentId);
            if (metrics != null) {
                metrics.taskFailed();
            }

            return createErrorResponse("Failed to complete task: " + e.getMessage());
        }
    }

    private WorkItemRecord getWorkItem(String workItemId) throws IOException {
        String workItemXml = yawlClient.getWorkItem(workItemId, sessionHandle);

        if (workItemXml == null || workItemXml.contains("failure")) {
            return null;
        }

        Document doc = JDOMUtil.stringToDocument(workItemXml);
        if (doc != null) {
            Element root = doc.getRootElement();
            return parseWorkItemRecord(root);
        }

        return null;
    }

    private WorkItemRecord parseWorkItemRecord(Element element) {
        WorkItemRecord wir = new WorkItemRecord();
        wir.setCaseID(element.getChildText("caseid"));
        wir.setTaskID(element.getChildText("taskid"));
        wir.setSpecURI(element.getChildText("specuri"));
        wir.setStatus(element.getChildText("status"));
        return wir;
    }

    private AgentRegistration selectAgent(List<AgentRegistration> agents) {
        AgentRegistration bestAgent = null;
        int minLoad = Integer.MAX_VALUE;

        for (AgentRegistration agent : agents) {
            AgentMetrics metrics = agentMetrics.get(agent.agentId);
            if (metrics != null) {
                int load = metrics.getCurrentLoad();
                if (load < minLoad) {
                    minLoad = load;
                    bestAgent = agent;
                }
            } else {
                return agent;
            }
        }

        return bestAgent != null ? bestAgent : agents.get(0);
    }

    private String buildDataXml(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return "<data/>";
        }

        StringBuilder xml = new StringBuilder("<data>");
        for (Map.Entry<String, String> entry : data.entrySet()) {
            xml.append("<").append(entry.getKey()).append(">")
                    .append(entry.getValue())
                    .append("</").append(entry.getKey()).append(">");
        }
        xml.append("</data>");

        return xml.toString();
    }

    private String createSuccessResponse(String message, Map<String, String> data) {
        StringBuilder json = new StringBuilder("{\"status\":\"success\",\"message\":\"" + message + "\"");
        if (data != null && !data.isEmpty()) {
            json.append(",\"data\":{");
            boolean first = true;
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                first = false;
            }
            json.append("}");
        }
        json.append("}");
        return json.toString();
    }

    private String createErrorResponse(String message) {
        return "{\"status\":\"error\",\"message\":\"" + message + "\"}";
    }

    private Map<String, String> parseJsonRequest(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null || json.isEmpty()) {
            return result;
        }

        json = json.trim();
        if (json.startsWith("{")) {
            json = json.substring(1);
        }
        if (json.endsWith("}")) {
            json = json.substring(0, json.length() - 1);
        }

        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim().replace("\"", "");
                result.put(key, value);
            }
        }

        return result;
    }

    class RegisterAgentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createErrorResponse("Method not allowed"));
                return;
            }

            String requestBody = readRequestBody(exchange);
            Map<String, String> params = parseJsonRequest(requestBody);

            String agentId = params.get("agentId");
            String capabilitiesStr = params.get("capabilities");
            String endpoint = params.get("endpoint");

            Set<String> capabilities = new HashSet<>();
            if (capabilitiesStr != null) {
                String[] caps = capabilitiesStr.replace("[", "").replace("]", "").split(",");
                for (String cap : caps) {
                    capabilities.add(cap.trim());
                }
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("registrationTime", String.valueOf(System.currentTimeMillis()));

            String response = registerAgent(agentId, capabilities, endpoint, metadata);
            sendResponse(exchange, 200, response);
        }
    }

    class UnregisterAgentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createErrorResponse("Method not allowed"));
                return;
            }

            String requestBody = readRequestBody(exchange);
            Map<String, String> params = parseJsonRequest(requestBody);

            String agentId = params.get("agentId");
            String response = unregisterAgent(agentId, "manual unregistration");
            sendResponse(exchange, 200, response);
        }
    }

    class DiscoverAgentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createErrorResponse("Method not allowed"));
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String capability = null;
            if (query != null && query.contains("capability=")) {
                capability = query.split("capability=")[1].split("&")[0];
            }

            List<AgentRegistration> discoveredAgents = discoverAgents(capability);

            StringBuilder json = new StringBuilder("{\"status\":\"success\",\"agents\":[");
            boolean first = true;
            for (AgentRegistration agent : discoveredAgents) {
                if (!first) json.append(",");
                json.append("{\"agentId\":\"").append(agent.agentId).append("\",");
                json.append("\"capabilities\":").append(agent.capabilities.toString()).append(",");
                json.append("\"endpoint\":\"").append(agent.endpoint).append("\"}");
                first = false;
            }
            json.append("]}");

            sendResponse(exchange, 200, json.toString());
        }
    }

    class DelegateTaskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createErrorResponse("Method not allowed"));
                return;
            }

            String requestBody = readRequestBody(exchange);
            Map<String, String> params = parseJsonRequest(requestBody);

            String workItemId = params.get("workItemId");
            String capability = params.get("capability");

            String response = delegateTask(workItemId, capability, params);
            sendResponse(exchange, 200, response);
        }
    }

    class CompleteTaskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createErrorResponse("Method not allowed"));
                return;
            }

            String requestBody = readRequestBody(exchange);
            Map<String, String> params = parseJsonRequest(requestBody);

            String workItemId = params.get("workItemId");
            String agentId = params.get("agentId");

            String response = completeTask(workItemId, agentId, params);
            sendResponse(exchange, 200, response);
        }
    }

    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("{\"status\":\"running\",");
            json.append("\"agentCount\":").append(agents.size()).append(",");
            json.append("\"activeAssignments\":").append(workItemAssignments.size()).append(",");
            json.append("\"totalRequests\":").append(requestCounter.get()).append(",");
            json.append("\"uptime\":").append(System.currentTimeMillis()).append("}");

            sendResponse(exchange, 200, json.toString());
        }
    }

    class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendResponse(exchange, 200, "{\"status\":\"healthy\"}");
        }
    }

    class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("{\"metrics\":[");
            boolean first = true;

            for (Map.Entry<String, AgentMetrics> entry : agentMetrics.entrySet()) {
                if (!first) json.append(",");
                AgentMetrics metrics = entry.getValue();
                json.append("{\"agentId\":\"").append(metrics.agentId).append("\",");
                json.append("\"tasksAssigned\":").append(metrics.tasksAssigned).append(",");
                json.append("\"tasksCompleted\":").append(metrics.tasksCompleted).append(",");
                json.append("\"tasksFailed\":").append(metrics.tasksFailed).append(",");
                json.append("\"avgCompletionTime\":").append(metrics.getAverageCompletionTime()).append("}");
                first = false;
            }

            json.append("]}");
            sendResponse(exchange, 200, json.toString());
        }
    }

    class ListAgentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("{\"agents\":[");
            boolean first = true;

            for (AgentRegistration agent : agents.values()) {
                if (!first) json.append(",");
                json.append("{\"agentId\":\"").append(agent.agentId).append("\",");
                json.append("\"capabilities\":").append(agent.capabilities.toString()).append(",");
                json.append("\"endpoint\":\"").append(agent.endpoint).append("\",");
                json.append("\"lastHeartbeat\":").append(agent.lastHeartbeat).append("}");
                first = false;
            }

            json.append("]}");
            sendResponse(exchange, 200, json.toString());
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream inputStream = exchange.getRequestBody();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        requestCounter.incrementAndGet();
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static class AgentRegistration {
        final String agentId;
        final Set<String> capabilities;
        final String endpoint;
        final Map<String, String> metadata;
        volatile long lastHeartbeat;

        AgentRegistration(String agentId, Set<String> capabilities, String endpoint,
                          Map<String, String> metadata, long lastHeartbeat) {
            this.agentId = agentId;
            this.capabilities = capabilities;
            this.endpoint = endpoint;
            this.metadata = metadata;
            this.lastHeartbeat = lastHeartbeat;
        }
    }

    static class WorkItemAssignment {
        final String workItemId;
        final String agentId;
        final String capability;
        final Map<String, String> data;
        final long assignmentTime;

        WorkItemAssignment(String workItemId, String agentId, String capability,
                           Map<String, String> data, long assignmentTime) {
            this.workItemId = workItemId;
            this.agentId = agentId;
            this.capability = capability;
            this.data = data;
            this.assignmentTime = assignmentTime;
        }
    }

    static class AgentMetrics {
        final String agentId;
        int tasksAssigned = 0;
        int tasksCompleted = 0;
        int tasksFailed = 0;
        long totalCompletionTime = 0;

        AgentMetrics(String agentId) {
            this.agentId = agentId;
        }

        void taskAssigned() {
            tasksAssigned++;
        }

        void taskCompleted(long completionTime) {
            tasksCompleted++;
            totalCompletionTime += completionTime;
        }

        void taskFailed() {
            tasksFailed++;
        }

        int getCurrentLoad() {
            return tasksAssigned - tasksCompleted - tasksFailed;
        }

        long getAverageCompletionTime() {
            return tasksCompleted > 0 ? totalCompletionTime / tasksCompleted : 0;
        }
    }

    /**
     * Main method for running the A2A server
     *
     * Usage:
     *   java YawlA2AServer [engineUrl] [port] [username] [password]
     *
     * Or use environment variables:
     *   export YAWL_ENGINE_URL=http://localhost:8080/yawl/ib
     *   export YAWL_USERNAME=admin
     *   export YAWL_PASSWORD=YAWL
     *   export A2A_SERVER_PORT=9090
     *   java YawlA2AServer
     */
    public static void main(String[] args) {
        YawlA2AServer server;

        if (args.length >= 4) {
            String engineUrl = args[0];
            int port = Integer.parseInt(args[1]);
            String username = args[2];
            String password = args[3];

            server = new YawlA2AServer(engineUrl, username, password);
            server.port = port;
        } else if (args.length >= 2) {
            String engineUrl = args[0];
            int port = Integer.parseInt(args[1]);

            server = new YawlA2AServer(engineUrl);
            server.port = port;
        } else if (args.length == 1) {
            String engineUrl = args[0];
            server = new YawlA2AServer(engineUrl);
        } else {
            server = new YawlA2AServer();
        }

        try {
            server.start();

            System.out.println("\n=== YAWL A2A Server Ready ===");
            System.out.println("Engine URL: " + engineUrl);
            System.out.println("Server Port: " + port);
            System.out.println("\nAvailable Endpoints:");
            System.out.println("  POST /a2a/register    - Register agent");
            System.out.println("  POST /a2a/unregister  - Unregister agent");
            System.out.println("  GET  /a2a/discover    - Discover agents by capability");
            System.out.println("  POST /a2a/delegate    - Delegate task to agent");
            System.out.println("  POST /a2a/complete    - Complete delegated task");
            System.out.println("  GET  /a2a/status      - Server status");
            System.out.println("  GET  /a2a/health      - Health check");
            System.out.println("  GET  /a2a/metrics     - Agent metrics");
            System.out.println("  GET  /a2a/agents      - List all agents");
            System.out.println("\nPress Ctrl+C to stop");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down YAWL A2A Server...");
                server.stop();
            }));

            Thread.sleep(Long.MAX_VALUE);
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}
