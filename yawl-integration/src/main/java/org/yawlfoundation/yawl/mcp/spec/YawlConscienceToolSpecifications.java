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

package org.yawlfoundation.yawl.integration.mcp.spec;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.conscience.AgentDecision;
import org.yawlfoundation.yawl.integration.conscience.DecisionGraph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Conscience Graph tool specifications for YAWL MCP server.
 *
 * <p>Provides 4 tools for agents to record decisions, recall similar decisions,
 * explain routing behavior, and generate compliance reports via the persistent
 * conscience graph backed by a SPARQL 1.1 RDF store (Oxigraph):</p>
 * <ul>
 *   <li>yawl_publish_decision - Record a decision to the conscience graph</li>
 *   <li>yawl_recall_similar_decisions - Query decisions by task type</li>
 *   <li>yawl_explain_routing - Audit agent routing decisions</li>
 *   <li>yawl_compliance_report - Generate compliance report with decision analytics</li>
 * </ul>
 *
 * <p>All tools return graceful error results if the SPARQL engine is unavailable.
 * Tools never throw exceptions, only return error {@link McpSchema.CallToolResult}.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlConscienceToolSpecifications {

    private final DecisionGraph graph;

    /**
     * Create conscience tool specifications backed by the given decision graph.
     *
     * @param graph the DecisionGraph for storing and querying agent decisions
     */
    public YawlConscienceToolSpecifications(DecisionGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("DecisionGraph must not be null");
        }
        this.graph = graph;
    }

    /**
     * Creates all 4 YAWL conscience MCP tool specifications.
     *
     * @return list of all conscience tool specifications for MCP registration
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        tools.add(createPublishDecisionTool());
        tools.add(createRecallSimilarTool());
        tools.add(createExplainRoutingTool());
        tools.add(createComplianceReportTool());
        return tools;
    }

    // =========================================================================
    // Tool 1: yawl_publish_decision
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createPublishDecisionTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agentId", Map.of(
            "type", "string",
            "description", "Identifier of the agent making this decision"));
        props.put("sessionId", Map.of(
            "type", "string",
            "description", "Identifier of the session context"));
        props.put("taskType", Map.of(
            "type", "string",
            "description", "Type of task (e.g., 'routing', 'selection', 'rejection')"));
        props.put("choiceKey", Map.of(
            "type", "string",
            "description", "Identifier of the choice within the task"));
        props.put("rationale", Map.of(
            "type", "string",
            "description", "Explanation of why this decision was made"));
        props.put("confidence", Map.of(
            "type", "number",
            "description", "Confidence level from 0.0 to 1.0"));
        props.put("context", Map.of(
            "type", "object",
            "additionalProperties", Map.of("type", "string"),
            "description", "Optional context key-value pairs for decision metadata"));

        List<String> required = List.of("agentId", "sessionId", "taskType", "choiceKey", "rationale", "confidence");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_publish_decision")
                .description("Record an agent decision to the conscience graph. " +
                    "The decision is persisted as RDF and can later be recalled for " +
                    "explanation and learning. Always succeeds, even if the SPARQL engine is unavailable.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String agentId = (String) params.get("agentId");
                    String sessionId = (String) params.get("sessionId");
                    String taskType = (String) params.get("taskType");
                    String choiceKey = (String) params.get("choiceKey");
                    String rationale = (String) params.get("rationale");
                    double confidence = ((Number) params.get("confidence")).doubleValue();

                    @SuppressWarnings("unchecked")
                    Map<String, String> context = (Map<String, String>) params.get("context");

                    AgentDecision decision = new AgentDecision(
                        agentId,
                        sessionId,
                        taskType,
                        choiceKey,
                        rationale,
                        confidence,
                        Instant.now(),
                        context != null ? context : Map.of()
                    );

                    graph.record(decision);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Decision recorded: agent=" + agentId + ", taskType=" + taskType +
                            ", choiceKey=" + choiceKey + ", confidence=" + confidence
                        )),
                        false, null, null);

                } catch (IllegalArgumentException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Invalid decision: " + e.getMessage()
                        )),
                        true, null, null);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Failed to record decision: " + e.getMessage()
                        )),
                        true, null, null);
                }
            });
    }

    // =========================================================================
    // Tool 2: yawl_recall_similar_decisions
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createRecallSimilarTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("taskType", Map.of(
            "type", "string",
            "description", "Task type to filter decisions (e.g., 'routing', 'selection')"));
        props.put("limit", Map.of(
            "type", "integer",
            "description", "Maximum number of decisions to return (default: 10)"));

        List<String> required = List.of("taskType");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_recall_similar_decisions")
                .description("Query the conscience graph for decisions matching a task type. " +
                    "Returns results as Turtle RDF. Useful for agents to learn from similar past decisions.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    if (!graph.isAvailable()) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Conscience graph unavailable. SPARQL engine is not reachable."
                            )),
                            true, null, null);
                    }

                    Map<String, Object> params = args.arguments();
                    String taskType = (String) params.get("taskType");
                    int limit = params.containsKey("limit") ?
                        ((Number) params.get("limit")).intValue() : 10;

                    String result = graph.recallSimilar(taskType, limit);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Similar decisions (task type='" + taskType + "'):\n\n" + result
                        )),
                        false, null, null);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Failed to recall similar decisions: " + e.getMessage()
                        )),
                        true, null, null);
                }
            });
    }

    // =========================================================================
    // Tool 3: yawl_explain_routing
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createExplainRoutingTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("agentId", Map.of(
            "type", "string",
            "description", "Agent identifier to filter decisions"));
        props.put("since", Map.of(
            "type", "string",
            "description", "ISO 8601 timestamp to filter decisions from (e.g., '2024-02-23T14:32:15Z')"));

        List<String> required = List.of("agentId", "since");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_explain_routing")
                .description("Audit routing decisions made by a specific agent since a given time. " +
                    "Returns decisions as Turtle RDF. Useful for understanding agent behavior and " +
                    "for compliance/audit purposes.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    if (!graph.isAvailable()) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Conscience graph unavailable. SPARQL engine is not reachable."
                            )),
                            true, null, null);
                    }

                    Map<String, Object> params = args.arguments();
                    String agentId = (String) params.get("agentId");
                    String sinceStr = (String) params.get("since");
                    Instant since = Instant.parse(sinceStr);

                    String result = graph.explainRouting(agentId, since);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Routing decisions for agent '" + agentId + "' since " + sinceStr + ":\n\n" + result
                        )),
                        false, null, null);

                } catch (java.time.format.DateTimeParseException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Invalid timestamp format. Use ISO 8601 format (e.g., '2024-02-23T14:32:15Z')"
                        )),
                        true, null, null);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Failed to explain routing: " + e.getMessage()
                        )),
                        true, null, null);
                }
            });
    }

    // =========================================================================
    // Tool 4: yawl_compliance_report
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createComplianceReportTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("confidenceThreshold", Map.of(
            "type", "number",
            "description", "Decisions below this confidence level are flagged for review (default: 0.5)"));

        List<String> required = List.of();
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_compliance_report")
                .description("Generate a compliance report from the agent conscience graph. " +
                    "Analyzes decision patterns: frequency by agent, confidence distribution, " +
                    "and flags low-confidence decisions for audit review.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    if (!graph.isAvailable()) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Conscience graph unavailable. SPARQL engine is not reachable."
                            )),
                            true, null, null);
                    }

                    Map<String, Object> params = args.arguments();
                    double threshold = params.containsKey("confidenceThreshold")
                        ? ((Number) params.get("confidenceThreshold")).doubleValue()
                        : 0.5;

                    String report = graph.complianceReport(threshold);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(report)),
                        false, null, null);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Failed to generate compliance report: " + e.getMessage()
                        )),
                        true, null, null);
                }
            });
    }
}
