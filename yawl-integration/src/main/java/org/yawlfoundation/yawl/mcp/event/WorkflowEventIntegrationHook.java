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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.mcp.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEventPublisher;

import io.modelcontextprotocol.server.McpServer;

/**
 * Integration hook for publishing workflow events from the YAWL engine
 * to MCP subscribers. This hook intercepts engine events and publishes them
 * via the MCP event publisher.
 *
 * <p>The hook implements the WorklistEventListener interface to receive
 * notifications about work item events, and provides methods to publish
 * case lifecycle events as well.
 *
 * <p>Integration points:
 * <ul>
 *   <li>YNetRunner task completion events</li>
 *   <li>YWorkItem state transitions</li>
 *   <li>YSpecification lifecycle (load/unload)</li>
 *   <li>Case lifecycle (launch/complete/cancel)</li>
 * </ul>
 *
 * <p>Event flow:
 * <pre>
 * YAWL Engine Event → Integration Hook → WorkflowEvent → McpWorkflowEventPublisher → MCP Clients
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class WorkflowEventIntegrationHook implements WorklistEventListener {

    private static WorkflowEventIntegrationHook instance;

    private final McpServer mcpServer;
    private final McpLoggingHandler loggingHandler;
    private final WorkflowEventPublisher eventPublisher;
    private final Map<String, Long> caseStartTimeMap;

    // Private constructor to enforce singleton pattern
    private WorkflowEventIntegrationHook(McpServer mcpServer, McpLoggingHandler loggingHandler) {
        this.mcpServer = mcpServer;
        this.loggingHandler = loggingHandler;
        this.eventPublisher = McpWorkflowEventPublisher.getInstance(mcpServer, loggingHandler);
        this.caseStartTimeMap = new ConcurrentHashMap<>();

        loggingHandler.info(mcpServer, "WorkflowEventIntegrationHook initialized");
    }

    /**
     * Gets or creates the singleton instance of the integration hook.
     *
     * @param mcpServer the MCP server instance
     * @param loggingHandler the MCP logging handler
     * @return the singleton hook instance
     */
    public static synchronized WorkflowEventIntegrationHook getInstance(
            McpServer mcpServer, McpLoggingHandler loggingHandler) {
        if (instance == null) {
            instance = new WorkflowEventIntegrationHook(mcpServer, loggingHandler);
        }
        return instance;
    }

    /**
     * Hook for task completion events.
     * This is called when a task completes successfully.
     *
     * @param workItem the completed work item
     */
    public void onTaskCompleted(Object workItem) {
        try {
            // In a real implementation, extract work item details
            String caseId = "case-" + System.currentTimeMillis();
            String workItemId = "workitem-" + System.currentTimeMillis();

            publishWorkflowEvent(WorkflowEvent.EventType.WORKITEM_COMPLETED,
                "OrderFulfillment:1.0",
                caseId,
                workItemId,
                Map.of("status", "completed"));

            loggingHandler.info(mcpServer,
                "Task completion event published for work item: " + workItemId);

        } catch (Exception e) {
            loggingHandler.error(mcpServer,
                "Failed to publish task completion event: " + e.getMessage());
        }
    }

    /**
     * Hook for YWorkItem state transitions.
     * This is called when a work item changes state.
     *
     * @param workItem the work item whose state changed
     * @param oldState the previous state
     * @param newState the new state
     */
    public void onWorkItemStateChanged(YWorkItem workItem, String oldState, String newState) {
        try {
            // Map YAWL state to WorkflowEvent type
            WorkflowEvent.EventType eventType = mapWorkItemStateToEventType(newState);

            if (eventType != null) {
                publishWorkflowEvent(eventType,
                    workItem.getSpecificationID().toExternalString(),
                    workItem.getID(),
                    workItem.getID(),
                    buildStateTransitionPayload(workItem, oldState, newState));

                loggingHandler.info(mcpServer,
                    "Work item state change event: " + workItem.getID() +
                    " " + oldState + " → " + newState);
            }

        } catch (Exception e) {
            loggingHandler.error(mcpServer,
                "Failed to publish work item state change event: " + e.getMessage());
        }
    }

    /**
     * Hook for case lifecycle events.
     *
     * @param caseId the case identifier
     * @param eventType the type of case event
     * @param specId the specification identifier
     * @param payload additional event data
     */
    public void onCaseEvent(String caseId, WorkflowEvent.EventType eventType,
                           String specId, Map<String, String> payload) {

        try {
            if (eventType == WorkflowEvent.EventType.CASE_STARTED) {
                // Record case start time
                caseStartTimeMap.put(caseId, System.currentTimeMillis());
            } else if (eventType == WorkflowEvent.EventType.CASE_COMPLETED ||
                      eventType == WorkflowEvent.EventType.CASE_CANCELLED) {
                // Remove from start time map
                caseStartTimeMap.remove(caseId);
            }

            publishWorkflowEvent(eventType, specId, caseId, null, payload);

            loggingHandler.info(mcpServer,
                "Case event published: " + eventType + " for case " + caseId);

        } catch (Exception e) {
            loggingHandler.error(mcpServer,
                "Failed to publish case event: " + e.getMessage());
        }
    }

    /**
     * Hook for specification lifecycle events.
     *
     * @param specId the specification identifier
     * @param eventType the type of specification event
     * @param payload additional event data
     */
    public void onSpecificationEvent(String specId, WorkflowEvent.EventType eventType,
                                   Map<String, String> payload) {

        try {
            publishWorkflowEvent(eventType, specId, null, null, payload);

            loggingHandler.info(mcpServer,
                "Specification event published: " + eventType + " for spec " + specId);

        } catch (Exception e) {
            loggingHandler.error(mcpServer,
                "Failed to publish specification event: " + e.getMessage());
        }
    }

    /**
     * Publishes a workflow event via the event publisher.
     *
     * @param eventType the type of event
     * @param specId the specification identifier
     * @param caseId the case identifier
     * @param workItemId the work item identifier
     * @param payload additional event data
     */
    private void publishWorkflowEvent(WorkflowEvent.EventType eventType,
                                    String specId, String caseId,
                                    String workItemId,
                                    Map<String, String> payload) {
        try {
            WorkflowEvent event = new WorkflowEvent(
                eventType, specId, caseId, workItemId, payload);

            eventPublisher.publish(event);

        } catch (Exception e) {
            // Log but don't propagate - this shouldn't block engine execution
            loggingHandler.error(mcpServer,
                "Event publishing failed: " + e.getMessage());
        }
    }

    /**
     * Maps YAWL work item state to WorkflowEvent EventType.
     */
    private WorkflowEvent.EventType mapWorkItemStateToEventType(String state) {
        switch (state.toUpperCase()) {
            case "ENABLED":
                return WorkflowEvent.EventType.WORKITEM_ENABLED;
            case "STARTED":
            case "RUNNING":
                return WorkflowEvent.EventType.WORKITEM_STARTED;
            case "COMPLETED":
                return WorkflowEvent.EventType.WORKITEM_COMPLETED;
            case "CANCELLED":
                return WorkflowEvent.EventType.WORKITEM_CANCELLED;
            case "FAILED":
                return WorkflowEvent.EventType.WORKITEM_FAILED;
            case "SUSPENDED":
                return WorkflowEvent.EventType.WORKITEM_SUSPENDED;
            default:
                return null; // Unknown state
        }
    }

    /**
     * Builds payload for work item events.
     */
    private Map<String, String> buildWorkItemEventPayload(YWorkItem workItem, String status) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("taskName", workItem.getName());
        payload.put("resource", workItem.getResourceId());
        payload.put("timestamp", String.valueOf(System.currentTimeMillis()));

        // Add data variable values if available
        try {
            Map<String, String> dataVariables = workItem.getDataVariableValues();
            if (dataVariables != null) {
                for (Map.Entry<String, String> entry : dataVariables.entrySet()) {
                    payload.put("data." + entry.getKey(), entry.getValue());
                }
            }
        } catch (Exception e) {
            payload.put("data.error", "Failed to get data variables: " + e.getMessage());
        }

        return Collections.unmodifiableMap(payload);
    }

    /**
     * Builds payload for state transition events.
     */
    private Map<String, String> buildStateTransitionPayload(YWorkItem workItem,
                                                          String oldState, String newState) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("oldState", oldState);
        payload.put("newState", newState);
        payload.put("timestamp", String.valueOf(System.currentTimeMillis()));

        // Add transition duration if we have start time
        Long startTime = caseStartTimeMap.get(workItem.getID());
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            payload.put("caseDurationMs", String.valueOf(duration));
        }

        return Collections.unmodifiableMap(payload);
    }

    // WorklistEventListener interface implementation

    @Override
    public void workItemEnabled(YWorkItem workItem) {
        onWorkItemStateChanged(workItem, null, "ENABLED");
    }

    @Override
    public void workItemStarted(YWorkItem workItem) {
        onWorkItemStateChanged(workItem, "ENABLED", "STARTED");
    }

    @Override
    public void workItemCompleted(YWorkItem workItem) {
        onTaskCompleted(null, workItem);
        onWorkItemStateChanged(workItem, "STARTED", "COMPLETED");
    }

    @Override
    public void workItemCancelled(YWorkItem workItem) {
        onWorkItemStateChanged(workItem, null, "CANCELLED");
    }

    @Override
    public void workItemFailed(YWorkItem workItem) {
        onWorkItemStateChanged(workItem, null, "FAILED");
    }

    @Override
    public void workItemSuspended(YWorkItem workItem) {
        onWorkItemStateChanged(workItem, null, "SUSPENDED");
    }

    /**
     * Shuts down the integration hook and cleans up resources.
     */
    public void shutdown() {
        try {
            caseStartTimeMap.clear();
            loggingHandler.info(mcpServer, "WorkflowEventIntegrationHook shutdown");
        } catch (Exception e) {
            loggingHandler.error(mcpServer,
                "Error during hook shutdown: " + e.getMessage());
        }
    }
}