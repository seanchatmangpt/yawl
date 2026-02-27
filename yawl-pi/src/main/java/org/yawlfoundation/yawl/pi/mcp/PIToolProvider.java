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

package org.yawlfoundation.yawl.pi.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.mcp.spec.McpToolProvider;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlMcpContext;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.bridge.OcedBridge;
import org.yawlfoundation.yawl.pi.bridge.OcedBridgeFactory;
import org.yawlfoundation.yawl.pi.bridge.OcedSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool provider for Process Intelligence functions.
 *
 * <p>Exposes 4 PI tools to the MCP server for autonomous agent use:
 * <ul>
 *   <li>yawl_pi_predict_risk - Predict case outcome risk score</li>
 *   <li>yawl_pi_recommend_action - Recommend prescriptive actions</li>
 *   <li>yawl_pi_ask - Natural language query over process knowledge</li>
 *   <li>yawl_pi_prepare_event_data - Convert event data to OCEL2</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class PIToolProvider implements McpToolProvider {

    @Override
    public List<McpServerFeatures.SyncToolSpecification> createTools(YawlMcpContext context) {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        tools.add(createPredictRiskTool());
        tools.add(createRecommendActionTool());
        tools.add(createAskTool());
        tools.add(createPrepareEventDataTool());
        return tools;
    }

    private McpServerFeatures.SyncToolSpecification createPredictRiskTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "Workflow case identifier to predict outcome for"));

        List<String> required = List.of("caseId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_pi_predict_risk")
                .description("Predict case outcome: completion probability and risk score. " +
                    "Uses ONNX model if available, falls back to historical analysis. " +
                    "Returns probability [0.0-1.0] and risk factors.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                String caseId = (String) args.arguments().get("caseId");
                if (caseId == null || caseId.isEmpty()) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error: caseId is required")), true, null, null);
                }
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(
                        "yawl_pi_predict_risk: ProcessIntelligenceFacade is not configured. " +
                        "Provide a predictive model registry and event store to enable risk prediction " +
                        "for case " + caseId + ".")),
                    true, null, null);
            }
        );
    }

    private McpServerFeatures.SyncToolSpecification createRecommendActionTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "Workflow case identifier"));
        props.put("riskScore", Map.of(
            "type", "number",
            "description", "Risk score [0.0-1.0] for this case"));

        List<String> required = List.of("caseId", "riskScore");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_pi_recommend_action")
                .description("Recommend process interventions (reroute, escalate, reallocate) " +
                    "to improve case outcomes. Returns ranked list of actions with rationale.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                String caseId = (String) args.arguments().get("caseId");
                Object riskObj = args.arguments().get("riskScore");
                if (caseId == null || caseId.isEmpty() || riskObj == null) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error: caseId and riskScore are required")),
                        true, null, null);
                }
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(
                        "yawl_pi_recommend_action: ProcessIntelligenceFacade is not configured. " +
                        "Provide a prescriptive engine and constraint model to enable action recommendations " +
                        "for case " + caseId + ".")),
                    true, null, null);
            }
        );
    }

    private McpServerFeatures.SyncToolSpecification createAskTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("question", Map.of(
            "type", "string",
            "description", "Natural language question about process execution"));
        props.put("specificationId", Map.of(
            "type", "string",
            "description", "Optional: specification to search (null for all)"));

        List<String> required = List.of("question");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_pi_ask")
                .description("Answer natural language queries about process mining results " +
                    "using retrieval-augmented generation (RAG). Returns grounded answer with " +
                    "source facts and confidence score.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                String question = (String) args.arguments().get("question");
                if (question == null || question.isEmpty()) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error: question is required")), true, null, null);
                }
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(
                        "yawl_pi_ask: ProcessIntelligenceFacade is not configured. " +
                        "Provide a natural language query engine (RAG over process knowledge) " +
                        "to answer: " + question)),
                    true, null, null);
            }
        );
    }

    private McpServerFeatures.SyncToolSpecification createPrepareEventDataTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("eventData", Map.of(
            "type", "string",
            "description", "Raw event data (CSV, JSON, or XML)"));
        props.put("format", Map.of(
            "type", "string",
            "description", "Format hint: 'csv', 'json', or 'xml' (optional, auto-detected if omitted)"));

        List<String> required = List.of("eventData");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_pi_prepare_event_data")
                .description("Convert event log data to OCEL2 (Object-Centric Event Log v2.0) " +
                    "format for process mining. Auto-detects format (CSV/JSON/XML) from content, " +
                    "infers schema, and returns standardized OCEL2 JSON.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                String eventData = (String) args.arguments().get("eventData");
                if (eventData == null || eventData.isEmpty()) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error: eventData is required")), true, null, null);
                }
                String format = args.arguments().get("format") instanceof String s ? s : null;
                long start = System.currentTimeMillis();
                try {
                    OcedBridge bridge;
                    if (format == null || format.isBlank() || format.equalsIgnoreCase("auto")) {
                        bridge = OcedBridgeFactory.autoDetect(eventData);
                    } else {
                        bridge = OcedBridgeFactory.forFormat(format);
                    }
                    OcedSchema ocedSchema = bridge.inferSchema(eventData);
                    String ocel2Json = bridge.convert(eventData, ocedSchema);
                    long elapsed = System.currentTimeMillis() - start;
                    String response = "OCEL 2.0 Conversion Result\n" +
                        "==========================\n" +
                        "Detected format:   " + bridge.formatName().toUpperCase() + "\n" +
                        "Schema inferred:   " + (ocedSchema.aiInferred() ? "AI-assisted" : "heuristic") + "\n" +
                        "Case ID column:    " + ocedSchema.caseIdColumn() + "\n" +
                        "Activity column:   " + ocedSchema.activityColumn() + "\n" +
                        "Timestamp column:  " + ocedSchema.timestampColumn() + "\n" +
                        "\nOCEL 2.0 JSON:\n" + ocel2Json + "\n" +
                        "\nElapsed: " + elapsed + "ms";
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(response)), false, null, null);
                } catch (PIException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("OCEL conversion failed: " + e.getMessage())),
                        true, null, null);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Unexpected error during OCEL conversion: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }
}
