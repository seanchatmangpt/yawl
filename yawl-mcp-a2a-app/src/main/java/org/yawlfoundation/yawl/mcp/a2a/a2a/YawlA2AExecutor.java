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

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.a2a.A2AException;
import org.yawlfoundation.yawl.integration.a2a.YawlEngineAdapter;

/**
 * YAWL Agent Executor implementation for A2A protocol.
 *
 * <p>This component implements the {@code AgentExecutor} interface to handle
 * incoming A2A messages and delegate them to the YAWL workflow engine via
 * {@link YawlEngineAdapter}, which wraps InterfaceB/A for real engine operations.</p>
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
 * @see YawlEngineAdapter
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

    private YawlEngineAdapter engineAdapter;

    /**
     * Initializes the executor after dependency injection.
     * Creates the YawlEngineAdapter with connection details from configuration.
     */
    @PostConstruct
    public void initialize() {
        LOGGER.info("Initializing YAWL A2A Executor");
        this.engineAdapter = new YawlEngineAdapter(yawlEngineUrl, yawlUsername, yawlPassword);
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
            engineAdapter.cancelCase(taskId);
            emitter.cancel(A2A.toAgentMessage("Case " + taskId + " cancelled successfully"));
        } catch (A2AException e) {
            LOGGER.error("Failed to cancel case {}: {}", taskId, e.getMessage(), e);
            emitter.fail(A2A.toAgentMessage("Failed to cancel: " + e.getMessage()));
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
     * Handles listing available specifications via InterfaceB.
     */
    private String handleListSpecifications() throws IOException {
        List<Map<String, Object>> specs = listSpecifications();
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
     * Handles launching a workflow case via InterfaceB.launchCase().
     */
    private String handleLaunchCase(String userText) throws IOException {
        String specId = extractIdentifier(userText);
        if (specId == null) {
            return "Please specify a workflow identifier to launch. "
                + "Use 'list specifications' to see available workflows.";
        }

        try {
            String caseId = engineAdapter.launchCase(specId, null);
            return "Workflow launched successfully.\n"
                + "  Specification: " + specId + "\n"
                + "  Case ID: " + caseId + "\n"
                + "  Status: Running\n"
                + "Use 'status case " + caseId + "' to check progress.";
        } catch (A2AException e) {
            throw new IOException("Failed to launch case for spec '" + specId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Handles a case query via InterfaceB.getWorkItemsForCase() and getCaseData().
     */
    private String handleCaseQuery(String userText) throws IOException {
        String caseId = extractNumber(userText);
        if (caseId != null) {
            Map<String, Object> status = getCaseStatus(caseId);

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
     * Handles a work item query via InterfaceB.getWorkItemsForCase() or getCompleteListOfLiveWorkItems().
     */
    private String handleWorkItemQuery(String userText) throws IOException {
        String caseId = extractNumber(userText);
        List<Map<String, Object>> items;
        if (caseId != null) {
            items = getWorkItemsForCase(caseId);
        } else {
            items = getAllWorkItems();
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
     * Handles cancelling a case via InterfaceB.cancelCase().
     */
    private String handleCancelCase(String userText) throws IOException {
        String caseId = extractNumber(userText);
        if (caseId == null) {
            return "Please specify a case ID to cancel.";
        }

        try {
            engineAdapter.cancelCase(caseId);
            return "Case " + caseId + " cancelled successfully.";
        } catch (A2AException e) {
            throw new IOException("Failed to cancel case '" + caseId + "': " + e.getMessage(), e);
        }
    }

    // ==================== Engine Operations ====================

    /**
     * Returns case status and active work items from the YAWL engine.
     *
     * @param caseId the case ID to query
     * @return map with status, caseId, workitems list, and caseData
     * @throws IOException if engine communication fails
     */
    private Map<String, Object> getCaseStatus(String caseId) throws IOException {
        try {
            List<WorkItemRecord> wirs = engineAdapter.getWorkItemsForCase(caseId);
            String caseData = engineAdapter.getCaseData(caseId);

            List<Map<String, Object>> wirMaps = workItemRecordsToMaps(wirs);

            Map<String, Object> result = new HashMap<>();
            result.put("caseId", caseId);
            result.put("status", wirs.isEmpty() ? "completed" : "running");
            result.put("workitems", wirMaps);
            result.put("caseData", caseData);
            return result;
        } catch (A2AException e) {
            throw new IOException("Failed to get case status for '" + caseId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Returns work items for a specific case from the YAWL engine.
     *
     * @param caseId the case ID
     * @return list of work item detail maps
     * @throws IOException if engine communication fails
     */
    private List<Map<String, Object>> getWorkItemsForCase(String caseId) throws IOException {
        try {
            return workItemRecordsToMaps(engineAdapter.getWorkItemsForCase(caseId));
        } catch (A2AException e) {
            throw new IOException("Failed to get work items for case '" + caseId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Returns all live work items from the YAWL engine.
     *
     * @return list of work item detail maps
     * @throws IOException if engine communication fails
     */
    private List<Map<String, Object>> getAllWorkItems() throws IOException {
        try {
            return workItemRecordsToMaps(engineAdapter.getWorkItems());
        } catch (A2AException e) {
            throw new IOException("Failed to get all work items: " + e.getMessage(), e);
        }
    }

    /**
     * Returns available specifications from the YAWL engine.
     *
     * @return list of specification detail maps
     * @throws IOException if engine communication fails
     */
    private List<Map<String, Object>> listSpecifications() throws IOException {
        try {
            List<String> specIds = engineAdapter.getSpecifications();
            List<Map<String, Object>> result = new ArrayList<>(specIds.size());
            for (String specId : specIds) {
                Map<String, Object> spec = new HashMap<>();
                spec.put("specId", specId);
                spec.put("name", specId);
                result.add(spec);
            }
            return result;
        } catch (A2AException e) {
            throw new IOException("Failed to list specifications: " + e.getMessage(), e);
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Converts a list of WorkItemRecords to a list of detail maps.
     *
     * @param wirs the work item records
     * @return list of maps with workitemId, caseId, taskId, status
     */
    private List<Map<String, Object>> workItemRecordsToMaps(List<WorkItemRecord> wirs) {
        List<Map<String, Object>> result = new ArrayList<>(wirs.size());
        for (WorkItemRecord wir : wirs) {
            Map<String, Object> map = new HashMap<>();
            map.put("workitemId", wir.getID());
            map.put("caseId", wir.getCaseID());
            map.put("taskId", wir.getTaskID());
            map.put("status", wir.getStatus());
            result.add(map);
        }
        return result;
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
     * Extracts an identifier from text (for launch/start commands).
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
     * Extracts a numeric ID from text (for case/task IDs).
     */
    private String extractNumber(String text) {
        Matcher m = Pattern.compile("\\b(\\d+)\\b").matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
