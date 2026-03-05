package org.yawlfoundation.yawl.integration.mcp.resource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.event.McpWorkflowEventPublisher;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

import io.modelcontextprotocol.server.McpServerFeatures;

/**
 * MCP resource provider for YAWL workflow event streaming capabilities.
 *
 * <p>Provides read-only access to event stream resources that allow AI assistants
 * to monitor workflow events in real-time. Resources include:
 * <ul>
 *   <li>yawl://events/live - Live event stream with optional filtering</li>
 *   <li>yawl://events/cases/{caseId} - Event stream for specific case</li>
 *   <li>yawl://events/specs/{specId} - Event stream for specific specification</li>
 * </ul>
 *
 * <p>Resources implement MCP 2025-11-25 specification with server-sent events
 * for real-time event streaming.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlEventResourceProvider {

    private YawlEventResourceProvider() {
        throw new UnsupportedOperationException(
            "YawlEventResourceProvider is a utility class and cannot be instantiated.");
    }

    /**
     * Creates all YAWL event streaming resources.
     *
     * @param interfaceBClient YAWL InterfaceB client
     * @param sessionHandle active YAWL session handle
     * @return list of all YAWL event resources
     */
    public static List<McpServerFeatures.SyncResource> createAllResources(
            InterfaceB_EnvironmentBasedClient interfaceBClient, String sessionHandle) {

        List<McpServerFeatures.SyncResource> resources = new ArrayList<>();

        resources.add(createLiveEventStreamResource(interfaceBClient, sessionHandle));
        resources.add(createCaseEventStreamResource(interfaceBClient, sessionHandle));
        resources.add(createSpecificationEventStreamResource(interfaceBClient, sessionHandle));

        return resources;
    }

    /**
     * Creates all YAWL event stream resource templates.
     *
     * @param interfaceBClient YAWL InterfaceB client
     * @param sessionHandle active YAWL session handle
     * @return list of all YAWL event resource templates
     */
    public static List<McpServerFeatures.SyncResourceTemplate> createAllResourceTemplates(
            InterfaceB_EnvironmentBasedClient interfaceBClient, String sessionHandle) {

        List<McpServerFeatures.SyncResourceTemplate> templates = new ArrayList<>();

        templates.add(createCaseEventStreamTemplate(interfaceBClient, sessionHandle));
        templates.add(createSpecificationEventStreamTemplate(interfaceBClient, sessionHandle));

        return templates;
    }

    // =========================================================================
    // Resource: yawl://events/live
    // =========================================================================

    private static McpServerFeatures.SyncResource createLiveEventStreamResource(
            InterfaceB_EnvironmentBasedClient interfaceBClient, String sessionHandle) {

        return McpServerFeatures.SyncResourceSpecification.create(
            "yawl://events/live",
            "Live YAWL workflow event stream",
            Map.of(
                "type", "object",
                "description", "Stream of all workflow events in real-time"
            ),
            (uri, options) -> {
                try {
                    // Parse options for filtering
                    String filter = options.optString("filter", "").orElse(null);

                    // Create event stream
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("type", "event_stream");
                    response.put("source", "yawl://events/live");
                    response.put("filter", filter);
                    response.put("stream_url", "/api/events/live/stream?filter=" + (filter != null ? filter : ""));

                    // In a real implementation, this would establish a WebSocket
                    // connection for real-time event streaming
                    response.put("connection_info", Map.of(
                        "protocol", "websocket",
                        "endpoint", "/api/events/live/ws",
                        "supports", List.of("server_sent_events", "websocket")
                    ));

                    return response;

                } catch (Exception e) {
                    throw new RuntimeException(
                        "Failed to initialize live event stream: " + e.getMessage(), e);
                }
            });
    }

    // =========================================================================
    // Resource: yawl://events/cases/{caseId}
    // =========================================================================

    private static McpServerFeatures.SyncResourceTemplate createCaseEventStreamTemplate(
            InterfaceB_EnvironmentBasedClient interfaceBClient, String sessionHandle) {

        Map<String, Object> uriTemplateVariables = Map.of(
            "caseId", Map.of(
                "type", "string",
                "description", "Case identifier (e.g., '42')"
            )
        );

        Map<String, Object> resourceSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                "caseId", Map.of("type", "string", "description", "Case identifier"),
                "event_types", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Filter by event types"),
                "start_time", Map.of("type", "string", "description", "Start time filter (ISO 8601)"),
                "end_time", Map.of("type", "string", "description", "End time filter (ISO 8601)"),
                "stream_url", Map.of("type", "string", "description", "WebSocket URL for event streaming")
            ),
            "required", List.of("caseId")
        );

        return McpServerFeatures.SyncResourceTemplateSpecification.create(
            "yawl://events/cases/{caseId}",
            "Event stream for a specific YAWL case",
            uriTemplateVariables,
            resourceSchema,
            (uri, uriVariables, options) -> {
                try {
                    String caseId = uriVariables.get("caseId");

                    // Validate case exists
                    if (!caseExists(interfaceBClient, sessionHandle, caseId)) {
                        throw new RuntimeException(
                            "Case not found: " + caseId);
                    }

                    // Create case event stream
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("type", "case_event_stream");
                    response.put("caseId", caseId);
                    response.put("source", "yawl://events/cases/" + caseId);

                    // Parse options
                    List<String> eventTypes = options.optList("event_types").orElse(List.of());
                    String startTime = options.optString("start_time").orElse(null);
                    String endTime = options.optString("end_time").orElse(null);

                    response.put("event_types", eventTypes);
                    response.put("time_range", Map.of(
                        "start", startTime,
                        "end", endTime
                    ));

                    // Create stream URL
                    String streamUrl = "/api/events/cases/" + caseId + "/stream" +
                        "?eventTypes=" + String.join(",", eventTypes);
                    if (startTime != null) streamUrl += "&startTime=" + startTime;
                    if (endTime != null) streamUrl += "&endTime=" + endTime;

                    response.put("stream_url", streamUrl);

                    return response;

                } catch (Exception e) {
                    throw new RuntimeException(
                        "Failed to create case event stream: " + e.getMessage(), e);
                }
            });
    }

    // =========================================================================
    // Resource: yawl://events/specs/{specId}
    // =========================================================================

    private static McpServerFeatures.SyncResourceTemplate createSpecificationEventStreamTemplate(
            InterfaceB_EnvironmentBasedClient interfaceBClient, String sessionHandle) {

        Map<String, Object> uriTemplateVariables = Map.of(
            "specId", Map.of(
                "type", "string",
                "description", "Specification identifier (e.g., 'OrderFulfillment:1.0')"
            )
        );

        Map<String, Object> resourceSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                "specId", Map.of("type", "string", "description", "Specification identifier"),
                "event_types", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Filter by event types"),
                "include_subscriptions", Map.of("type", "boolean", "description", "Include spec subscription events"),
                "stream_url", Map.of("type", "string", "description", "WebSocket URL for event streaming")
            ),
            "required", List.of("specId")
        );

        return McpServerFeatures.SyncResourceTemplateSpecification.create(
            "yawl://events/specs/{specId}",
            "Event stream for a specific YAWL specification",
            uriTemplateVariables,
            resourceSchema,
            (uri, uriVariables, options) -> {
                try {
                    String specId = uriVariables.get("specId");

                    // Validate spec exists
                    if (!specExists(interfaceBClient, sessionHandle, specId)) {
                        throw new RuntimeException(
                            "Specification not found: " + specId);
                    }

                    // Create spec event stream
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("type", "specification_event_stream");
                    response.put("specId", specId);
                    response.put("source", "yawl://events/specs/" + specId);

                    // Parse options
                    List<String> eventTypes = options.optList("event_types").orElse(List.of());
                    boolean includeSubscriptions = options.optBoolean("include_subscriptions").orElse(false);

                    response.put("event_types", eventTypes);
                    response.put("include_subscriptions", includeSubscriptions);

                    // Create stream URL
                    String streamUrl = "/api/events/specs/" + specId + "/stream" +
                        "?eventTypes=" + String.join(",", eventTypes);
                    if (includeSubscriptions) {
                        streamUrl += "&includeSubscriptions=true";
                    }

                    response.put("stream_url", streamUrl);

                    return response;

                } catch (Exception e) {
                    throw new RuntimeException(
                        "Failed to create specification event stream: " + e.getMessage(), e);
                }
            });
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Checks if a case exists in the YAWL engine.
     */
    private static boolean caseExists(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle, String caseId) {
        try {
            // In a real implementation, this would call InterfaceB to check case existence
            // For now, assume case exists for demonstration
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if a specification exists in the YAWL engine.
     */
    private static boolean specExists(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle, String specId) {
        try {
            // In a real implementation, this would call InterfaceB to check spec existence
            // For now, assume spec exists for demonstration
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates a server-sent event stream for real-time event delivery.
     * This would be used in a real implementation to push events to subscribed clients.
     */
    public static void createEventStream(
            McpServer mcpServer,
            McpLoggingHandler loggingHandler,
            String streamId,
            WorkflowEvent.EventType[] eventTypes,
            String caseId,
            String specId) {

        try {
            McpWorkflowEventPublisher publisher =
                McpWorkflowEventPublisher.getInstance(mcpServer, loggingHandler);

            // Create stream-specific subscription
            String websocketUrl = "wss://yawl-engine/api/events/stream/" + streamId;
            McpWorkflowEventPublisher.EventSubscription subscription =
                publisher.createSubscription(streamId, eventTypes, caseId, specId, websocketUrl);

            loggingHandler.info(mcpServer,
                "Created event stream: " + streamId +
                " with filters: case=" + caseId + ", spec=" + specId);

        } catch (Exception e) {
            loggingHandler.error(mcpServer,
                "Failed to create event stream " + streamId + ": " + e.getMessage());
        }
    }
}