package org.yawlfoundation.yawl.integration.mcp.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.event.McpWorkflowEventPublisher;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Static factory class that creates all YAWL event publishing tool specifications
 * for the MCP server using the official MCP Java SDK v1 API.
 *
 * <p>These tools enable AI assistants to subscribe to workflow events and
 * receive real-time notifications about case lifecycle, task events,
 * state transitions, resource allocation, and data changes.
 *
 * <p>Tools implement MCP 2025-11-25 specification with exchange-based handlers
 * that support sampling and elicitation when client capabilities allow.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlEventToolSpecifications {

    private YawlEventToolSpecifications() {
        throw new UnsupportedOperationException(
            "YawlEventToolSpecifications is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates all YAWL event publishing MCP tool specifications.
     *
     * @param mcpServer the MCP server instance
     * @param loggingHandler the MCP logging handler
     * @return list of all YAWL event tool specifications for MCP registration
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            McpServer mcpServer, McpLoggingHandler loggingHandler) {

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        tools.add(createSubscribeToEventsTool(mcpServer, loggingHandler));
        tools.add(createUnsubscribeFromEventsTool(mcpServer, loggingHandler));
        tools.add(createListSubscriptionsTool(mcpServer, loggingHandler));
        tools.add(createGetSubscriptionStatusTool(mcpServer, loggingHandler));

        return tools;
    }

    // =========================================================================
    // Tool 1: yawl_subscribe_to_events
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createSubscribeToEventsTool(
            McpServer mcpServer, McpLoggingHandler loggingHandler) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("subscriptionId", Map.of(
            "type", "string",
            "description", "Unique identifier for this subscription"));
        props.put("eventTypes", Map.of(
            "type", "array",
            "items", Map.of("type", "string",
                "enum", new String[]{
                    "CASE_STARTED", "CASE_COMPLETED", "CASE_CANCELLED", "CASE_SUSPENDED", "CASE_RESUMED",
                    "WORKITEM_ENABLED", "WORKITEM_STARTED", "WORKITEM_COMPLETED", "WORKITEM_CANCELLED",
                    "WORKITEM_FAILED", "WORKITEM_SUSPENDED", "SPEC_LOADED", "SPEC_UNLOADED"
                }),
            "description", "Array of event types to subscribe to (null or empty for all types)"));
        props.put("caseId", Map.of(
            "type", new String[]{"null", "string"},
            "description", "Filter by case ID (null for all cases)"));
        props.put("specId", Map.of(
            "type", new String[]{"null", "string"},
            "description", "Filter by specification ID (null for all specifications)"));
        props.put("websocketUrl", Map.of(
            "type", "string",
            "description", "WebSocket URL for event delivery"));

        Map<String, Object> inputSchema = Map.of(
            "type", "object",
            "properties", props,
            "required", List.of("subscriptionId", "websocketUrl"));

        return McpServerFeatures.SyncToolSpecification.create(
            "yawl_subscribe_to_events",
            "Subscribe to YAWL workflow events with filtering criteria",
            inputSchema,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "subscriptionId", Map.of("type", "string", "description", "The subscription ID"),
                    "status", Map.of("type", "string", "description", "Subscription status"),
                    "message", Map.of("type", "string", "description", "Success or error message")
                )
            ),
            request -> {
                try {
                    String subscriptionId = request.params().getString("subscriptionId");
                    List<String> eventTypesList = request.params().getList("eventTypes");
                    WorkflowEvent.EventType[] eventTypes = null;

                    if (eventTypesList != null && !eventTypesList.isEmpty()) {
                        eventTypes = eventTypesList.stream()
                            .map(type -> WorkflowEvent.EventType.valueOf(type))
                            .toArray(WorkflowEvent.EventType[]::new);
                    }

                    String caseId = request.params().optString("caseId").orElse(null);
                    String specId = request.params().optString("specId").orElse(null);
                    String websocketUrl = request.params().getString("websocketUrl");

                    // Get the event publisher instance
                    McpWorkflowEventPublisher publisher =
                        McpWorkflowEventPublisher.getInstance(mcpServer, loggingHandler);

                    // Create subscription
                    McpWorkflowEventPublisher.EventSubscription subscription =
                        publisher.createSubscription(subscriptionId, eventTypes, caseId, specId, websocketUrl);

                    loggingHandler.info(mcpServer,
                        "Created event subscription: " + subscriptionId);

                    return Map.of(
                        "subscriptionId", subscriptionId,
                        "status", "created",
                        "message", "Successfully subscribed to events"
                    );

                } catch (Exception e) {
                    loggingHandler.error(mcpServer, "Failed to create subscription: " + e.getMessage());
                    return Map.of(
                        "subscriptionId", request.params().optString("subscriptionId").orElse("unknown"),
                        "status", "error",
                        "message", "Failed to create subscription: " + e.getMessage()
                    );
                }
            });
    }

    // =========================================================================
    // Tool 2: yawl_unsubscribe_from_events
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createUnsubscribeFromEventsTool(
            McpServer mcpServer, McpLoggingHandler loggingHandler) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("subscriptionId", Map.of(
            "type", "string",
            "description", "Unique identifier of the subscription to remove"));

        Map<String, Object> inputSchema = Map.of(
            "type", "object",
            "properties", props,
            "required", List.of("subscriptionId"));

        return McpServerFeatures.SyncToolSpecification.create(
            "yawl_unsubscribe_from_events",
            "Remove an existing event subscription",
            inputSchema,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "subscriptionId", Map.of("type", "string", "description", "The subscription ID"),
                    "status", Map.of("type", "string", "description", "Removal status"),
                    "message", Map.of("type", "string", "description", "Success or error message")
                )
            ),
            request -> {
                try {
                    String subscriptionId = request.params().getString("subscriptionId");

                    // Get the event publisher instance
                    McpWorkflowEventPublisher publisher =
                        McpWorkflowEventPublisher.getInstance(mcpServer, loggingHandler);

                    // Remove subscription
                    boolean removed = publisher.removeSubscription(subscriptionId);

                    if (removed) {
                        loggingHandler.info(mcpServer,
                            "Removed event subscription: " + subscriptionId);
                        return Map.of(
                            "subscriptionId", subscriptionId,
                            "status", "removed",
                            "message", "Successfully unsubscribed from events"
                        );
                    } else {
                        loggingHandler.warning(mcpServer,
                            "Subscription not found: " + subscriptionId);
                        return Map.of(
                            "subscriptionId", subscriptionId,
                            "status", "not_found",
                            "message", "Subscription not found"
                        );
                    }

                } catch (Exception e) {
                    loggingHandler.error(mcpServer, "Failed to remove subscription: " + e.getMessage());
                    return Map.of(
                        "subscriptionId", request.params().getString("subscriptionId"),
                        "status", "error",
                        "message", "Failed to remove subscription: " + e.getMessage()
                    );
                }
            });
    }

    // =========================================================================
    // Tool 3: yawl_list_subscriptions
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createListSubscriptionsTool(
            McpServer mcpServer, McpLoggingHandler loggingHandler) {

        Map<String, Object> inputSchema = Map.of(
            "type", "object",
            "properties", Map.of(),
            "required", List.of());

        return McpServerFeatures.SyncToolSpecification.create(
            "yawl_list_subscriptions",
            "List all active event subscriptions",
            inputSchema,
            Map.of(
                "type", "array",
                "items", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "subscriptionId", Map.of("type", "string"),
                        "eventTypes", Map.of("type", "array", "items", Map.of("type", "string")),
                        "caseId", Map.of("type", new String[]{"null", "string"}),
                        "specId", Map.of("type", new String[]{"null", "string"}),
                        "websocketUrl", Map.of("type", "string"),
                        "isActive", Map.of("type", "boolean"),
                        "deliveredEventCount", Map.of("type", "number")
                    )
                )
            ),
            request -> {
                try {
                    // Get the event publisher instance
                    McpWorkflowEventPublisher publisher =
                        McpWorkflowEventPublisher.getInstance(mcpServer, loggingHandler);

                    // Get all subscriptions
                    Map<String, McpWorkflowEventPublisher.EventSubscription> subscriptions =
                        publisher.getSubscriptions();

                    // Convert to list of subscription info
                    List<Map<String, Object>> subscriptionList = new ArrayList<>();
                    for (Map.Entry<String, McpWorkflowEventPublisher.EventSubscription> entry :
                        subscriptions.entrySet()) {

                        McpWorkflowEventPublisher.EventSubscription sub = entry.getValue();
                        List<String> eventTypeStrings = new ArrayList<>();
                        if (sub.getEventTypes() != null) {
                            for (WorkflowEvent.EventType eventType : sub.getEventTypes()) {
                                eventTypeStrings.add(eventType.name());
                            }
                        }

                        Map<String, Object> subInfo = new LinkedHashMap<>();
                        subInfo.put("subscriptionId", sub.getSubscriptionId());
                        subInfo.put("eventTypes", eventTypeStrings);
                        subInfo.put("caseId", sub.getCaseId());
                        subInfo.put("specId", sub.getSpecId());
                        subInfo.put("websocketUrl", sub.getWebsocketUrl());
                        subInfo.put("isActive", sub.isActive());
                        subInfo.put("deliveredEventCount", sub.getDeliveredEventCount());

                        subscriptionList.add(subInfo);
                    }

                    loggingHandler.info(mcpServer,
                        "Listed " + subscriptionList.size() + " event subscriptions");

                    return subscriptionList;

                } catch (Exception e) {
                    loggingHandler.error(mcpServer,
                        "Failed to list subscriptions: " + e.getMessage());
                    return List.of();
                }
            });
    }

    // =========================================================================
    // Tool 4: yawl_get_subscription_status
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createGetSubscriptionStatusTool(
            McpServer mcpServer, McpLoggingHandler loggingHandler) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("subscriptionId", Map.of(
            "type", "string",
            "description", "Unique identifier of the subscription to check"));

        Map<String, Object> inputSchema = Map.of(
            "type", "object",
            "properties", props,
            "required", List.of("subscriptionId"));

        return McpServerFeatures.SyncToolSpecification.create(
            "yawl_get_subscription_status",
            "Get detailed status information for a specific subscription",
            inputSchema,
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "subscriptionId", Map.of("type", "string", "description", "The subscription ID"),
                    "isActive", Map.of("type", "boolean", "description", "Whether the subscription is active"),
                    "deliveredEventCount", Map.of("type", "number", "description", "Number of events delivered"),
                    "eventTypes", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Subscribed event types"),
                    "caseId", Map.of("type", new String[]{"null", "string"}, "description", "Case ID filter"),
                    "specId", Map.of("type", new String[]{"null", "string"}, "description", "Specification ID filter"),
                    "message", Map.of("type", "string", "description", "Status message")
                )
            ),
            request -> {
                try {
                    String subscriptionId = request.params().getString("subscriptionId");

                    // Get the event publisher instance
                    McpWorkflowEventPublisher publisher =
                        McpWorkflowEventPublisher.getInstance(mcpServer, loggingHandler);

                    // Get specific subscription
                    McpWorkflowEventPublisher.EventSubscription subscription =
                        publisher.getSubscriptions().get(subscriptionId);

                    if (subscription != null) {
                        List<String> eventTypeStrings = new ArrayList<>();
                        if (subscription.getEventTypes() != null) {
                            for (WorkflowEvent.EventType eventType : subscription.getEventTypes()) {
                                eventTypeStrings.add(eventType.name());
                            }
                        }

                        Map<String, Object> status = new LinkedHashMap<>();
                        status.put("subscriptionId", subscription.getSubscriptionId());
                        status.put("isActive", subscription.isActive());
                        status.put("deliveredEventCount", subscription.getDeliveredEventCount());
                        status.put("eventTypes", eventTypeStrings);
                        status.put("caseId", subscription.getCaseId());
                        status.put("specId", subscription.getSpecId());
                        status.put("message", "Subscription is active");

                        loggingHandler.info(mcpServer,
                            "Retrieved status for subscription: " + subscriptionId);

                        return status;
                    } else {
                        loggingHandler.warning(mcpServer,
                            "Subscription not found: " + subscriptionId);

                        return Map.of(
                            "subscriptionId", subscriptionId,
                            "isActive", false,
                            "deliveredEventCount", 0,
                            "eventTypes", List.of(),
                            "caseId", null,
                            "specId", null,
                            "message", "Subscription not found"
                        );
                    }

                } catch (Exception e) {
                    loggingHandler.error(mcpServer,
                        "Failed to get subscription status: " + e.getMessage());

                    return Map.of(
                        "subscriptionId", request.params().getString("subscriptionId"),
                        "isActive", false,
                        "deliveredEventCount", 0,
                        "eventTypes", List.of(),
                        "caseId", null,
                        "specId", null,
                        "message", "Error: " + e.getMessage()
                    );
                }
            });
    }
}