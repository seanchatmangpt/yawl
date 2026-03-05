package org.yawlfoundation.yawl.integration.mcp.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.event.McpWorkflowEventPublisher;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
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
     * @param mcpServer the MCP sync server instance
     * @param loggingHandler the MCP logging handler
     * @return list of all YAWL event tool specifications for MCP registration
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            McpSyncServer mcpServer, McpLoggingHandler loggingHandler) {

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
            McpSyncServer mcpServer, McpLoggingHandler loggingHandler) {

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

        List<String> required = List.of("subscriptionId", "websocketUrl");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_subscribe_to_events")
                .description("Subscribe to YAWL workflow events with filtering criteria")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String subscriptionId = requireStringArg(params, "subscriptionId");
                    List<String> eventTypesList = optionalListArg(params, "eventTypes");
                    WorkflowEvent.EventType[] eventTypes = null;

                    if (eventTypesList != null && !eventTypesList.isEmpty()) {
                        eventTypes = eventTypesList.stream()
                            .map(type -> WorkflowEvent.EventType.valueOf(type))
                            .toArray(WorkflowEvent.EventType[]::new);
                    }

                    String caseId = optionalStringArg(params, "caseId", null);
                    String specId = optionalStringArg(params, "specId", null);
                    String websocketUrl = requireStringArg(params, "websocketUrl");

                    // Get the event publisher instance
                    McpWorkflowEventPublisher publisher =
                        McpWorkflowEventPublisher.getInstance(mcpServer, loggingHandler);

                    // Create subscription
                    McpWorkflowEventPublisher.EventSubscription subscription =
                        publisher.createSubscription(subscriptionId, eventTypes, caseId, specId, websocketUrl);

                    loggingHandler.info(mcpServer,
                        "Created event subscription: " + subscriptionId);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Successfully subscribed to events. Subscription ID: " + subscriptionId)),
                        false, null, null);

                } catch (Exception e) {
                    loggingHandler.error(mcpServer, "Failed to create subscription: " + e.getMessage());
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Failed to create subscription: " + e.getMessage())),
                        true, null, null);
                }
            });
    }

    // =========================================================================
    // Tool 2: yawl_unsubscribe_from_events
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createUnsubscribeFromEventsTool(
            McpSyncServer mcpServer, McpLoggingHandler loggingHandler) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("subscriptionId", Map.of(
            "type", "string",
            "description", "Unique identifier of the subscription to remove"));

        List<String> required = List.of("subscriptionId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_unsubscribe_from_events")
                .description("Remove an existing event subscription")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String subscriptionId = requireStringArg(params, "subscriptionId");

                    // Get the event publisher instance
                    McpWorkflowEventPublisher publisher =
                        McpWorkflowEventPublisher.getInstance(mcpServer, loggingHandler);

                    // Remove subscription
                    boolean removed = publisher.removeSubscription(subscriptionId);

                    if (removed) {
                        loggingHandler.info(mcpServer,
                            "Removed event subscription: " + subscriptionId);
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Successfully unsubscribed from events. Subscription ID: " + subscriptionId)),
                            false, null, null);
                    } else {
                        loggingHandler.warning(mcpServer,
                            "Subscription not found: " + subscriptionId);
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Subscription not found: " + subscriptionId)),
                            true, null, null);
                    }

                } catch (Exception e) {
                    loggingHandler.error(mcpServer, "Failed to remove subscription: " + e.getMessage());
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Failed to remove subscription: " + e.getMessage())),
                        true, null, null);
                }
            });
    }

    // =========================================================================
    // Tool 3: yawl_list_subscriptions
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createListSubscriptionsTool(
            McpSyncServer mcpServer, McpLoggingHandler loggingHandler) {

        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = List.of();
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_list_subscriptions")
                .description("List all active event subscriptions")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    // Get the event publisher instance
                    McpWorkflowEventPublisher publisher =
                        McpWorkflowEventPublisher.getInstance(mcpServer, loggingHandler);

                    // Get all subscriptions
                    Map<String, McpWorkflowEventPublisher.EventSubscription> subscriptions =
                        publisher.getSubscriptions();

                    // Convert to text representation
                    StringBuilder sb = new StringBuilder();
                    sb.append("Active Event Subscriptions (").append(subscriptions.size()).append("):\n\n");

                    for (Map.Entry<String, McpWorkflowEventPublisher.EventSubscription> entry :
                        subscriptions.entrySet()) {

                        McpWorkflowEventPublisher.EventSubscription sub = entry.getValue();
                        List<String> eventTypeStrings = new ArrayList<>();
                        if (sub.getEventTypes() != null) {
                            for (WorkflowEvent.EventType eventType : sub.getEventTypes()) {
                                eventTypeStrings.add(eventType.name());
                            }
                        }

                        sb.append("• Subscription ID: ").append(sub.getSubscriptionId()).append("\n");
                        sb.append("  Event Types: ").append(eventTypeStrings.isEmpty() ? "all" : eventTypeStrings).append("\n");
                        sb.append("  Case ID: ").append(sub.getCaseId() != null ? sub.getCaseId() : "all").append("\n");
                        sb.append("  Spec ID: ").append(sub.getSpecId() != null ? sub.getSpecId() : "all").append("\n");
                        sb.append("  WebSocket URL: ").append(sub.getWebsocketUrl()).append("\n");
                        sb.append("  Active: ").append(sub.isActive()).append("\n");
                        sb.append("  Events Delivered: ").append(sub.getDeliveredEventCount()).append("\n\n");
                    }

                    loggingHandler.info(mcpServer,
                        "Listed " + subscriptions.size() + " event subscriptions");

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(sb.toString().trim())),
                        false, null, null);

                } catch (Exception e) {
                    loggingHandler.error(mcpServer,
                        "Failed to list subscriptions: " + e.getMessage());
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error listing subscriptions: " + e.getMessage())),
                        true, null, null);
                }
            });
    }

    // =========================================================================
    // Tool 4: yawl_get_subscription_status
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createGetSubscriptionStatusTool(
            McpSyncServer mcpServer, McpLoggingHandler loggingHandler) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("subscriptionId", Map.of(
            "type", "string",
            "description", "Unique identifier of the subscription to check"));

        List<String> required = List.of("subscriptionId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_get_subscription_status")
                .description("Get detailed status information for a specific subscription")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String subscriptionId = requireStringArg(params, "subscriptionId");

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

                        StringBuilder sb = new StringBuilder();
                        sb.append("Subscription Status:\n");
                        sb.append("  Subscription ID: ").append(subscription.getSubscriptionId()).append("\n");
                        sb.append("  Active: ").append(subscription.isActive()).append("\n");
                        sb.append("  Events Delivered: ").append(subscription.getDeliveredEventCount()).append("\n");
                        sb.append("  Event Types: ").append(eventTypeStrings.isEmpty() ? "all" : eventTypeStrings).append("\n");
                        sb.append("  Case ID: ").append(subscription.getCaseId() != null ? subscription.getCaseId() : "all").append("\n");
                        sb.append("  Spec ID: ").append(subscription.getSpecId() != null ? subscription.getSpecId() : "all").append("\n");
                        sb.append("  WebSocket URL: ").append(subscription.getWebsocketUrl()).append("\n");

                        loggingHandler.info(mcpServer,
                            "Retrieved status for subscription: " + subscriptionId);

                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(sb.toString())),
                            false, null, null);
                    } else {
                        loggingHandler.warning(mcpServer,
                            "Subscription not found: " + subscriptionId);

                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Subscription not found: " + subscriptionId)),
                            true, null, null);
                    }

                } catch (Exception e) {
                    loggingHandler.error(mcpServer,
                        "Failed to get subscription status: " + e.getMessage());

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error retrieving subscription status: " + e.getMessage())),
                        true, null, null);
                }
            });
    }

    // =========================================================================
    // Argument extraction utilities
    // =========================================================================

    /**
     * Extract a required string argument from the tool arguments map.
     *
     * @param args the tool arguments
     * @param name the argument name
     * @return the string value
     * @throws IllegalArgumentException if the argument is missing
     */
    private static String requireStringArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required argument missing: " + name);
        }
        return value.toString();
    }

    /**
     * Extract an optional string argument from the tool arguments map.
     *
     * @param args         the tool arguments
     * @param name         the argument name
     * @param defaultValue the default value if the argument is missing
     * @return the string value or the default
     */
    private static String optionalStringArg(Map<String, Object> args, String name,
                                            String defaultValue) {
        Object value = args.get(name);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }

    /**
     * Extract an optional list argument from the tool arguments map.
     *
     * @param args the tool arguments
     * @param name the argument name
     * @return the list value or null if missing
     */
    @SuppressWarnings("unchecked")
    private static List<String> optionalListArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value instanceof List<?>) {
            return (List<String>) value;
        }
        return null;
    }
}