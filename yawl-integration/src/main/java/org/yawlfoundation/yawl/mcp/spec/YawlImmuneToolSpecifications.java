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

import java.util.*;
import java.util.stream.Collectors;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.immune.WorkflowImmuneSystem;

/**
 * Workflow immune system MCP tool specifications for YAWL v6.0.
 *
 * <p>Provides 1 MCP tool for analyzing workflow nets for deadlock patterns:
 * <ul>
 *   <li>yawl_check_immunity - Analyze a workflow net for deadlock patterns</li>
 * </ul>
 *
 * <p><b>Tool: yawl_check_immunity</b>
 * <p>Analyzes a Petri net structure and predicts potential deadlock patterns after
 * a task firing. Takes adjacency maps representing the workflow net and returns
 * a JSON report of detected deadlock predictions.
 *
 * <p><b>Input Parameters:</b>
 * <ul>
 *   <li>caseId - Case identifier (string)</li>
 *   <li>firedTaskId - ID of the task that just fired (string)</li>
 *   <li>placeToTransitions - JSON object mapping place IDs to output transition IDs</li>
 *   <li>transitionToPlaces - JSON object mapping transition IDs to output place IDs</li>
 *   <li>startPlace - Start place ID (string)</li>
 *   <li>endPlace - End place ID (string)</li>
 * </ul>
 *
 * <p><b>Output:</b> JSON object with:
 * <ul>
 *   <li>predictions - Array of deadlock predictions (if any)</li>
 *   <li>status - "SOUND" or "DEADLOCK_PREDICTED"</li>
 *   <li>summary - Human-readable summary</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlImmuneToolSpecifications {

    private final WorkflowImmuneSystem immuneSystem;

    /**
     * Create immune system tool specifications.
     *
     * @param immuneSystem the WorkflowImmuneSystem instance to use (non-null)
     * @throws NullPointerException if immuneSystem is null
     */
    public YawlImmuneToolSpecifications(WorkflowImmuneSystem immuneSystem) {
        this.immuneSystem = Objects.requireNonNull(immuneSystem, "immuneSystem must not be null");
    }

    /**
     * Creates the yawl_check_immunity MCP tool specification.
     *
     * @return the tool specification for immune system checking
     */
    public McpServerFeatures.SyncToolSpecification createCheckImmunityTool() {
        Map<String, Object> props = new LinkedHashMap<>();

        // Input parameters
        props.put("caseId", Map.of(
            "type", "string",
            "description", "Case identifier"
        ));
        props.put("firedTaskId", Map.of(
            "type", "string",
            "description", "ID of the task that just fired"
        ));
        props.put("placeToTransitions", Map.of(
            "type", "object",
            "description", "Map of place IDs to arrays of output transition IDs"
        ));
        props.put("transitionToPlaces", Map.of(
            "type", "object",
            "description", "Map of transition IDs to arrays of output place IDs"
        ));
        props.put("startPlace", Map.of(
            "type", "string",
            "description", "Start place ID"
        ));
        props.put("endPlace", Map.of(
            "type", "string",
            "description", "End place ID"
        ));

        List<String> required = List.of("caseId", "firedTaskId", "placeToTransitions",
                                        "transitionToPlaces", "startPlace", "endPlace");

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null
        );

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_check_immunity")
                .description(
                    "Check a workflow net for deadlock patterns using the YAWL immune system. " +
                    "Analyzes Petri net structure after a task firing and predicts potential " +
                    "deadlock patterns (implicit deadlock, orphaned places, livelocks, etc.). " +
                    "Returns a detailed JSON report with predictions and remediation guidance."
                )
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    var response = handleCheckImmunity(params);
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(formatResponse(response))),
                        false, null, null
                    );
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error: " + e.getMessage())),
                        true, null, null
                    );
                }
            }
        );
    }

    /**
     * Handles the yawl_check_immunity tool invocation.
     *
     * @param args the tool input arguments
     * @return map response with predictions
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> handleCheckImmunity(Map<String, ?> args) {
        // Extract arguments
        String caseId = (String) args.get("caseId");
        String firedTaskId = (String) args.get("firedTaskId");
        String startPlace = (String) args.get("startPlace");
        String endPlace = (String) args.get("endPlace");

        // Convert placeToTransitions from JSON object to Map<String, Set<String>>
        Map<String, Object> placeToTransObj = (Map<String, Object>) args.get("placeToTransitions");
        Map<String, Set<String>> placeToTransitions = convertJsonObjectToMap(placeToTransObj);

        // Convert transitionToPlaces from JSON object to Map<String, Set<String>>
        Map<String, Object> transitionToPlacesObj = (Map<String, Object>) args.get("transitionToPlaces");
        Map<String, Set<String>> transitionToPlaces = convertJsonObjectToMap(transitionToPlacesObj);

        // Run immune system prediction
        var predictions = immuneSystem.predict(caseId, firedTaskId, placeToTransitions,
                                                transitionToPlaces, startPlace, endPlace);

        // Build response
        Map<String, Object> response = new LinkedHashMap<>();

        if (predictions.isEmpty()) {
            response.put("status", "SOUND");
            response.put("summary", "No deadlock patterns detected");
            response.put("predictions", List.of());
        } else {
            response.put("status", "DEADLOCK_PREDICTED");
            response.put("summary", "Deadlock patterns detected: " + predictions.size());

            // Format predictions as JSON
            List<Map<String, Object>> predictionsList = predictions.stream()
                .map(pred -> Map.of(
                    "caseId", (Object) pred.caseId(),
                    "firedTaskId", pred.firedTaskId(),
                    "findingType", pred.findingType(),
                    "affectedElements", pred.affectedElements(),
                    "confidence", pred.confidence(),
                    "timestamp", pred.timestamp().toString()
                ))
                .collect(Collectors.toList());

            response.put("predictions", predictionsList);
        }

        response.put("caseId", caseId);
        response.put("firedTaskId", firedTaskId);

        return response;
    }

    /**
     * Formats the response map as JSON string.
     *
     * @param response the response map
     * @return JSON string representation
     */
    private String formatResponse(Map<String, Object> response) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (Map.Entry<String, Object> entry : response.entrySet()) {
            sb.append("  \"").append(entry.getKey()).append("\": ");
            formatValue(sb, entry.getValue(), 2);
            sb.append(",\n");
        }
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);  // Remove last comma
        }
        sb.append("\n}");
        return sb.toString();
    }

    /**
     * Recursively formats a value for JSON output.
     *
     * @param sb the string builder
     * @param value the value to format
     * @param indent the indentation level
     */
    @SuppressWarnings("unchecked")
    private void formatValue(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append("\"").append(value).append("\"");
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof List<?>) {
            sb.append("[");
            List<?> list = (List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                formatValue(sb, list.get(i), indent);
            }
            sb.append("]");
        } else if (value instanceof Map<?, ?>) {
            sb.append("{ ");
            Map<String, Object> map = (Map<String, Object>) value;
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(", ");
                sb.append("\"").append(entry.getKey()).append("\": ");
                formatValue(sb, entry.getValue(), indent);
                first = false;
            }
            sb.append(" }");
        } else {
            sb.append("\"").append(value).append("\"");
        }
    }

    /**
     * Converts a JSON object (Map<String, Object>) to Map<String, Set<String>>
     * for use with the immune system.
     *
     * @param jsonObj the JSON object from MCP (values are arrays of strings)
     * @return converted map with sets as values
     */
    @SuppressWarnings("unchecked")
    private Map<String, Set<String>> convertJsonObjectToMap(Map<String, Object> jsonObj) {
        Map<String, Set<String>> result = new LinkedHashMap<>();

        if (jsonObj == null) {
            return result;
        }

        for (Map.Entry<String, Object> entry : jsonObj.entrySet()) {
            Object value = entry.getValue();
            Set<String> set = new HashSet<>();

            if (value instanceof List<?>) {
                // Convert list of strings to set
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    if (item instanceof String) {
                        set.add((String) item);
                    }
                }
            } else if (value instanceof String) {
                // Single string value
                set.add((String) value);
            }

            result.put(entry.getKey(), set);
        }

        return result;
    }
}
