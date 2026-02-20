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

package org.yawlfoundation.yawl.mcp.a2a.a2a;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import io.a2a.A2A;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.tasks.AgentEmitter;
import io.a2a.spec.A2AError;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;

import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YAWL Agent Executor implementation for A2A protocol.
 *
 * <p>This component implements the {@code AgentExecutor} interface to handle
 * incoming A2A messages and delegate them to the YAWL workflow engine.
 * It adapts the pattern from YawlA2AServer.YawlAgentExecutor for Spring Boot.</p>
 *
 * <h2>Supported Commands</h2>
 * <ul>
 *   <li><strong>launch-case</strong>: Launch new YAWL workflow case</li>
 *   <li><strong>cancel-case</strong>: Cancel existing YAWL workflow case</li>
 *   <li><strong>checkout-workitem</strong>: Check out work item for processing</li>
 *   <li><strong>checkin-workitem</strong>: Check in completed work item</li>
 *   <li><strong>monitor-case</strong>: Monitor case status</li>
 *   <li><strong>list-specifications</strong>: List available specifications</li>
 *   <li><strong>get-case-status</strong>: Get case details</li>
 * </ul>
 *
 * <h2>Threading Model</h2>
 * <p>Each incoming message is executed on a virtual thread for optimal
 * I/O-bound performance, leveraging the configured virtual thread executor.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see AgentExecutor
 */
@Component
public class YawlA2AExecutor implements AgentExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(YawlA2AExecutor.class);

    @Autowired
    @Qualifier("virtualThreadTaskExecutor")
    private AsyncTaskExecutor virtualThreadExecutor;

    @Value("${yawl.engine.url:http://localhost:8080/yawl}")
    private String yawlEngineUrl;

    @Value("${yawl.username:admin}")
    private String yawlUsername;

    @Value("${yawl.password:YAWL}")
    private String yawlPassword;

    private YawlWorkflowService yawlWorkflowService;

    /**
     * Initializes the executor after dependency injection.
     */
    @PostConstruct
    public void initialize() {
        LOGGER.info("Initializing YAWL A2A Executor");

        // Create YAWL workflow service with virtual thread support
        this.yawlWorkflowService = new YawlWorkflowService(
            yawlEngineUrl, yawlUsername, yawlPassword, virtualThreadExecutor);

        LOGGER.info("YAWL A2A Executor initialized with YAWL engine URL: {}", yawlEngineUrl);
    }

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
        emitter.startWork();

        try {
            Message userMessage = context.getMessage();
            String userText = extractTextFromMessage(userMessage);

            LOGGER.info("Processing A2A message: {}", userText);

            String response = processWorkflowRequest(userText);

            emitter.complete(A2A.toAgentMessage(response));
        } catch (IOException e) {
            LOGGER.error("YAWL engine error: {}", e.getMessage(), e);
            emitter.fail(A2A.toAgentMessage("YAWL engine error: " + e.getMessage()));
        } catch (Exception e) {
            LOGGER.error("Processing error: {}", e.getMessage(), e);
            emitter.fail(A2A.toAgentMessage("Processing error: " + e.getMessage()));
        }
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
        String taskId = context.getTaskId();
        LOGGER.info("Cancel request received for task: {}", taskId);

        try {
            boolean success = yawlWorkflowService.cancelCase(taskId);
            if (success) {
                emitter.cancel(A2A.toAgentMessage("Case " + taskId + " cancelled successfully"));
            } else {
                emitter.fail(A2A.toAgentMessage("Failed to cancel case: " + taskId));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to cancel case {}: {}", taskId, e.getMessage(), e);
            emitter.fail(A2A.toAgentMessage("Failed to cancel: " + e.getMessage()));
        }
    }

    /**
     * Processes a workflow request from the user message.
     *
     * @param userText the text content of the user message
     * @return the response string
     * @throws IOException if an I/O error occurs
     */
    private String processWorkflowRequest(String userText) throws IOException {
        String lower = userText.toLowerCase().trim();

        if (lower.contains("list") && (lower.contains("spec") || lower.contains("workflow"))) {
            return handleListSpecifications();
        }

        if (lower.contains("launch") || lower.contains("start")) {
            return handleLaunchCase(userText);
        }

        if (lower.contains("status") || lower.contains("case")) {
            return handleCaseQuery(userText);
        }

        if (lower.contains("work item") || lower.contains("workitem") || lower.contains("task")) {
            return handleWorkItemQuery(userText);
        }

        if (lower.contains("cancel") || lower.contains("stop")) {
            return handleCancelCase(userText);
        }

        return handleListSpecifications();
    }

    /**
     * Handles listing available specifications.
     */
    private String handleListSpecifications() throws IOException {
        List<Map<String, Object>> specs = yawlWorkflowService.listSpecifications();
        if (specs == null || specs.isEmpty()) {
            return "No workflow specifications currently loaded in the YAWL engine.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Loaded workflow specifications (").append(specs.size()).append("):\n\n");
        for (Map<String, Object> spec : specs) {
            sb.append("- ").append(spec.get("specId"));
            if (spec.get("name") != null) {
                sb.append(" (").append(spec.get("name")).append(")");
            }
            if (spec.get("version") != null) {
                sb.append(" v").append(spec.get("version"));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Handles launching a workflow case.
     */
    private String handleLaunchCase(String userText) throws IOException {
        String specId = extractIdentifier(userText);
        if (specId == null) {
            return "Please specify a workflow identifier to launch. "
                + "Use 'list specifications' to see available workflows.";
        }

        String caseId = yawlWorkflowService.launchCase(specId, null);

        return "Workflow launched successfully.\n"
            + "  Specification: " + specId + "\n"
            + "  Case ID: " + caseId + "\n"
            + "  Status: Running\n"
            + "Use 'status case " + caseId + "' to check progress.";
    }

    /**
     * Handles a case query.
     */
    private String handleCaseQuery(String userText) throws IOException {
        String caseId = extractNumber(userText);
        if (caseId != null) {
            Map<String, Object> status = yawlWorkflowService.getCaseStatus(caseId);

            StringBuilder sb = new StringBuilder();
            sb.append("Case ").append(caseId).append(":\n");
            sb.append("  State: ").append(status.getOrDefault("status", "unknown")).append("\n");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> workitems = (List<Map<String, Object>>) status.get("workitems");
            if (workitems != null && !workitems.isEmpty()) {
                sb.append("  Work Items (").append(workitems.size()).append("):\n");
                for (Map<String, Object> wir : workitems) {
                    sb.append("    - ").append(wir.get("workitemId"))
                        .append(" [").append(wir.get("status")).append("]")
                        .append("\n");
                }
            } else {
                sb.append("  No active work items.\n");
            }
            return sb.toString();
        }

        return "Running cases information available. Specify a case ID for details.";
    }

    /**
     * Handles a work item query.
     */
    private String handleWorkItemQuery(String userText) throws IOException {
        String caseId = extractNumber(userText);
        List<Map<String, Object>> items;
        if (caseId != null) {
            items = yawlWorkflowService.getWorkItemsForCase(caseId);
        } else {
            items = yawlWorkflowService.getAllWorkItems();
        }

        if (items == null || items.isEmpty()) {
            return "No active work items" + (caseId != null ? " for case " + caseId : "") + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Work Items (").append(items.size()).append("):\n\n");
        for (Map<String, Object> wir : items) {
            sb.append("- ID: ").append(wir.get("workitemId")).append("\n");
            sb.append("  Case: ").append(wir.get("caseId")).append("\n");
            sb.append("  Task: ").append(wir.get("taskId")).append("\n");
            sb.append("  Status: ").append(wir.get("status")).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Handles cancelling a case.
     */
    private String handleCancelCase(String userText) throws IOException {
        String caseId = extractNumber(userText);
        if (caseId == null) {
            return "Please specify a case ID to cancel.";
        }

        boolean success = yawlWorkflowService.cancelCase(caseId);
        if (success) {
            return "Case " + caseId + " cancelled successfully.";
        }
        return "Failed to cancel case " + caseId + ".";
    }

    /**
     * Extracts text content from an A2A message.
     */
    private String extractTextFromMessage(Message message) {
        if (message == null || message.parts() == null) {
            throw new IllegalArgumentException("Message has no content parts");
        }
        StringBuilder text = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                text.append(textPart.text());
            }
        }
        if (text.length() == 0) {
            throw new IllegalArgumentException("Message contains no text parts");
        }
        return text.toString();
    }

    /**
     * Extracts an identifier from text.
     */
    private String extractIdentifier(String text) {
        String[] parts = text.split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            if (("launch".equalsIgnoreCase(parts[i]) || "start".equalsIgnoreCase(parts[i]))
                    && i + 1 < parts.length) {
                String next = parts[i + 1];
                if (!"the".equalsIgnoreCase(next) && !"a".equalsIgnoreCase(next)
                        && !"workflow".equalsIgnoreCase(next)) {
                    return next;
                }
                if (i + 2 < parts.length) {
                    return parts[i + 2];
                }
            }
        }
        String[] quoted = text.split("'");
        if (quoted.length >= 2) {
            return quoted[1];
        }
        return null;
    }

    /**
     * Extracts a number from text.
     */
    private String extractNumber(String text) {
        Matcher m = Pattern.compile("\\b(\\d+)\\b").matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Internal service class for YAWL workflow operations.
     *
     * <p>This class integrates with the YAWL engine via InterfaceB client,
     * managing session lifecycle and delegating all operations to the real engine.</p>
     */
    private static class YawlWorkflowService {
        private static final Logger SERVICE_LOGGER = LoggerFactory.getLogger(YawlWorkflowService.class);

        private final InterfaceB_EnvironmentBasedClient client;
        private final String username;
        private final String password;
        private volatile String sessionHandle;

        public YawlWorkflowService(String engineUrl, String username, String password, Executor executor) {
            this.client = new InterfaceB_EnvironmentBasedClient(engineUrl + "/ib");
            this.username = username;
            this.password = password;
        }

        public String launchCase(String specId, String caseData) throws IOException {
            ensureConnection();
            String result = client.launchCase(specId, caseData, sessionHandle);
            if (!client.successful(result)) {
                throw new IOException("Failed to launch case for spec '" + specId + "': " + result);
            }
            return result;
        }

        public boolean cancelCase(String caseId) throws IOException {
            ensureConnection();
            String result = client.cancelCase(caseId, sessionHandle);
            return client.successful(result);
        }

        public Map<String, Object> checkoutWorkItem(String workitemId, String participantId) throws IOException {
            ensureConnection();
            String result = client.checkOutWorkItem(workitemId, sessionHandle);
            if (!client.successful(result)) {
                throw new IOException("Failed to checkout work item '" + workitemId + "': " + result);
            }
            return Map.of(
                "workitemId", workitemId,
                "participantId", participantId,
                "data", result,
                "status", "executing"
            );
        }

        public boolean checkinWorkItem(String workitemId, String participantId, String results) throws IOException {
            ensureConnection();
            String result = client.checkInWorkItem(workitemId, results, sessionHandle);
            return client.successful(result);
        }

        public Map<String, Object> getCaseStatus(String caseId) throws IOException {
            ensureConnection();
            String caseState = client.getCaseState(caseId, sessionHandle);
            List<WorkItemRecord> workItems = client.getWorkItemsForCase(caseId, sessionHandle);
            List<Map<String, Object>> wirMaps = workItems.stream()
                .map(wir -> Map.<String, Object>of(
                    "workitemId", wir.getID(),
                    "status", wir.getStatus(),
                    "taskId", wir.getTaskID()
                ))
                .toList();
            return Map.of(
                "caseId", caseId,
                "status", client.successful(caseState) ? caseState : "unknown",
                "workitems", wirMaps,
                "timestamp", java.time.Instant.now().toString()
            );
        }

        public List<Map<String, Object>> getWorkItemsForCase(String caseId) throws IOException {
            ensureConnection();
            List<WorkItemRecord> workItems = client.getWorkItemsForCase(caseId, sessionHandle);
            return workItems.stream()
                .map(wir -> Map.<String, Object>of(
                    "workitemId", wir.getID(),
                    "caseId", wir.getCaseID(),
                    "taskId", wir.getTaskID(),
                    "status", wir.getStatus()
                ))
                .toList();
        }

        public List<Map<String, Object>> getAllWorkItems() throws IOException {
            ensureConnection();
            List<WorkItemRecord> workItems = client.getCompleteListOfLiveWorkItems(sessionHandle);
            return workItems.stream()
                .map(wir -> Map.<String, Object>of(
                    "workitemId", wir.getID(),
                    "caseId", wir.getCaseID(),
                    "taskId", wir.getTaskID(),
                    "status", wir.getStatus()
                ))
                .toList();
        }

        public List<Map<String, Object>> listSpecifications() throws IOException {
            ensureConnection();
            List<SpecificationData> specs = client.getSpecificationList(sessionHandle);
            return specs.stream()
                .map(spec -> Map.<String, Object>of(
                    "specId", spec.getID().getIdentifier(),
                    "name", spec.getName() != null ? spec.getName() : spec.getID().getIdentifier(),
                    "version", spec.getSpecVersion() != null ? spec.getSpecVersion() : "0.1"
                ))
                .toList();
        }

        private void ensureConnection() throws IOException {
            if (sessionHandle == null || !client.successful(sessionHandle)) {
                SERVICE_LOGGER.debug("Establishing session with YAWL engine");
                sessionHandle = client.connect(username, password);
                if (!client.successful(sessionHandle)) {
                    throw new IOException("Failed to connect to YAWL engine: " + sessionHandle);
                }
                SERVICE_LOGGER.info("Session established with YAWL engine");
            }
        }
    }
}
