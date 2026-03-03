/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.observability.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.mcp.a2a.observability.client.ObservabilityClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP tool specifications for OpenTelemetry observability integration.
 *
 * <p>Provides tools for querying metrics, traces, and health status from
 * OpenTelemetry backends like Prometheus and Jaeger.</p>
 *
 * <h2>Tools Provided</h2>
 * <ul>
 *   <li><strong>get_metrics</strong> - Query Prometheus metrics with optional filters</li>
 *   <li><strong>get_traces</strong> - Query distributed traces for workflow cases</li>
 *   <li><strong>get_health_status</strong> - Get service health and readiness</li>
 *   <li><strong>get_span_summary</strong> - Get aggregated span statistics</li>
 *   <li><strong>get_alert_status</strong> - Query current alert states</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class ObservabilityToolSpecifications {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityToolSpecifications.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ObservabilityToolSpecifications() {
        throw new UnsupportedOperationException(
            "ObservabilityToolSpecifications is a static utility class and cannot be instantiated.");
    }

    /**
     * Create all observability tool specifications.
     *
     * @param client Observability client
     * @return List of tool specifications
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(ObservabilityClient client) {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        tools.add(createGetMetricsTool(client));
        tools.add(createGetTracesTool(client));
        tools.add(createGetHealthStatusTool(client));
        tools.add(createGetSpanSummaryTool(client));
        tools.add(createGetAlertStatusTool(client));

        return tools;
    }

    /**
     * Create the get_metrics tool.
     *
     * <p>Queries Prometheus metrics using PromQL syntax.</p>
     */
    private static McpServerFeatures.SyncToolSpecification createGetMetricsTool(ObservabilityClient client) {
        McpSchema.Tool tool = new McpSchema.Tool(
            "get_metrics",
            "Query Prometheus metrics using PromQL syntax. Returns metric data as JSON.",
            MAPPER.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of(
                        "type", "string",
                        "description", "PromQL query string (e.g., 'yawl_workitem_duration_seconds_count')"
                    ),
                    "time", Map.of(
                        "type", "integer",
                        "description", "Optional query timestamp in epoch seconds"
                    ),
                    "start", Map.of(
                        "type", "integer",
                        "description", "Start timestamp for range queries (epoch seconds)"
                    ),
                    "end", Map.of(
                        "type", "integer",
                        "description", "End timestamp for range queries (epoch seconds)"
                    ),
                    "step", Map.of(
                        "type", "string",
                        "description", "Step interval for range queries (e.g., '15s', '1m')"
                    )
                ),
                "required", List.of("query")
            ))
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            try {
                Map<String, Object> args = extractArgs(request);
                String query = (String) args.get("query");
                Long time = args.get("time") != null ? ((Number) args.get("time")).longValue() : null;

                JsonNode result;
                if (args.containsKey("start") && args.containsKey("end")) {
                    long start = ((Number) args.get("start")).longValue();
                    long end = ((Number) args.get("end")).longValue();
                    String step = (String) args.getOrDefault("step", "15s");
                    result = client.queryMetricsRange(query, start, end, step);
                } else {
                    result = client.queryMetrics(query, time);
                }

                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(MAPPER.writeValueAsString(result))),
                    false
                );
            } catch (IOException e) {
                LOGGER.error("Failed to query metrics", e);
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("{\"error\": \"" + e.getMessage() + "\"}")),
                    true
                );
            }
        });
    }

    /**
     * Create the get_traces tool.
     *
     * <p>Queries distributed traces from Jaeger.</p>
     */
    private static McpServerFeatures.SyncToolSpecification createGetTracesTool(ObservabilityClient client) {
        McpSchema.Tool tool = new McpSchema.Tool(
            "get_traces",
            "Query distributed traces from Jaeger. Filter by service, operation, or case ID.",
            MAPPER.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                    "service", Map.of(
                        "type", "string",
                        "description", "Service name to filter by"
                    ),
                    "operation", Map.of(
                        "type", "string",
                        "description", "Operation name to filter by (optional)"
                    ),
                    "case_id", Map.of(
                        "type", "string",
                        "description", "YAWL case ID to search for in traces"
                    ),
                    "trace_id", Map.of(
                        "type", "string",
                        "description", "Specific trace ID to retrieve"
                    ),
                    "limit", Map.of(
                        "type", "integer",
                        "description", "Maximum number of traces to return (default: 20)"
                    )
                ),
                "required", List.of()
            ))
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            try {
                Map<String, Object> args = extractArgs(request);
                int limit = args.get("limit") != null ? ((Number) args.get("limit")).intValue() : 20;

                JsonNode result;
                if (args.containsKey("trace_id")) {
                    result = client.getTraceById((String) args.get("trace_id"));
                } else if (args.containsKey("case_id")) {
                    result = client.queryTracesByCaseId((String) args.get("case_id"), limit);
                } else {
                    String service = (String) args.getOrDefault("service", client.getConfig().getServiceName());
                    String operation = (String) args.get("operation");
                    result = client.queryTraces(service, operation, limit);
                }

                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(MAPPER.writeValueAsString(result))),
                    false
                );
            } catch (IOException e) {
                LOGGER.error("Failed to query traces", e);
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("{\"error\": \"" + e.getMessage() + "\"}")),
                    true
                );
            }
        });
    }

    /**
     * Create the get_health_status tool.
     *
     * <p>Gets health status of all configured observability services.</p>
     */
    private static McpServerFeatures.SyncToolSpecification createGetHealthStatusTool(ObservabilityClient client) {
        McpSchema.Tool tool = new McpSchema.Tool(
            "get_health_status",
            "Get health status of all configured observability services (Prometheus, Jaeger, OTLP, AlertManager).",
            MAPPER.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of()
            ))
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            try {
                JsonNode result = client.getHealthStatus();
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(MAPPER.writeValueAsString(result))),
                    false
                );
            } catch (Exception e) {
                LOGGER.error("Failed to get health status", e);
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("{\"error\": \"" + e.getMessage() + "\"}")),
                    true
                );
            }
        });
    }

    /**
     * Create the get_span_summary tool.
     *
     * <p>Gets aggregated span statistics for a service.</p>
     */
    private static McpServerFeatures.SyncToolSpecification createGetSpanSummaryTool(ObservabilityClient client) {
        McpSchema.Tool tool = new McpSchema.Tool(
            "get_span_summary",
            "Get aggregated span statistics for a service, including operation rates and latency percentiles.",
            MAPPER.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                    "service", Map.of(
                        "type", "string",
                        "description", "Service name (default: yawl-engine)"
                    ),
                    "lookback_ms", Map.of(
                        "type", "integer",
                        "description", "Lookback period in milliseconds (default: 300000 = 5 minutes)"
                    )
                ),
                "required", List.of()
            ))
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            try {
                Map<String, Object> args = extractArgs(request);
                String service = (String) args.getOrDefault("service", client.getConfig().getServiceName());
                long lookbackMs = args.get("lookback_ms") != null
                    ? ((Number) args.get("lookback_ms")).longValue()
                    : 300000L;

                JsonNode result = client.getSpanSummary(service, lookbackMs);
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(MAPPER.writeValueAsString(result))),
                    false
                );
            } catch (Exception e) {
                LOGGER.error("Failed to get span summary", e);
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("{\"error\": \"" + e.getMessage() + "\"}")),
                    true
                );
            }
        });
    }

    /**
     * Create the get_alert_status tool.
     *
     * <p>Queries current alert states from AlertManager.</p>
     */
    private static McpServerFeatures.SyncToolSpecification createGetAlertStatusTool(ObservabilityClient client) {
        McpSchema.Tool tool = new McpSchema.Tool(
            "get_alert_status",
            "Query current alert states from AlertManager.",
            MAPPER.valueToTree(Map.of(
                "type", "object",
                "properties", Map.of(
                    "filter", Map.of(
                        "type", "string",
                        "description", "Optional filter (e.g., 'service=yawl-engine')"
                    )
                ),
                "required", List.of()
            ))
        );

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            try {
                Map<String, Object> args = extractArgs(request);
                String filter = (String) args.get("filter");

                JsonNode result = client.getAlertStatus(filter);
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(MAPPER.writeValueAsString(result))),
                    false
                );
            } catch (Exception e) {
                LOGGER.error("Failed to get alert status", e);
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("{\"error\": \"" + e.getMessage() + "\"}")),
                    true
                );
            }
        });
    }

    /**
     * Extract arguments from tool call request.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractArgs(McpSchema.CallToolRequest request) {
        if (request.arguments() instanceof Map) {
            return (Map<String, Object>) request.arguments();
        }
        return Map.of();
    }
}
