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
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.bridge.OcedBridge;
import org.yawlfoundation.yawl.pi.bridge.OcedBridgeFactory;
import org.yawlfoundation.yawl.pi.bridge.OcedSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool specifications for OCED → OCEL 2.0 format conversion — no LLM required.
 *
 * <p>Exposes two tools implementing van der Aalst's Process Intelligence connection 5
 * (data preparation): converting raw enterprise event logs from CSV, JSON, or XML format
 * into OCEL 2.0 (Object-Centric Event Log, IEEE 2023 standard) for process mining.</p>
 *
 * <p>Both tools use heuristic schema inference — no Z.AI or LLM invocation occurs
 * (the {@link OcedBridgeFactory} static instances are constructed with {@code null}
 * for the Z.AI client, routing through the deterministic fallback path).</p>
 *
 * <ul>
 *   <li>{@code yawl_convert_to_ocel} — auto-detect or explicit format, infer schema,
 *       return OCEL 2.0 JSON</li>
 *   <li>{@code yawl_infer_oced_schema} — return inferred schema columns without
 *       performing the full conversion</li>
 * </ul>
 *
 * <p>OCEL 2.0 JSON is interoperable with PM4Py, ocpa, PM4JS, and Celonis Process Sphere™.</p>
 *
 * @see OcedBridgeFactory
 * @see OcedBridge
 * @see OcedSchema
 * @since YAWL 6.0
 */
public final class YawlOcedBridgeToolSpecifications {

    /**
     * Creates all OCED bridge MCP tool specifications.
     *
     * @return list of two tool specifications
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        return List.of(
            buildConvertToOcelTool(),
            buildInferOcedSchemaTool()
        );
    }

    // =========================================================================
    // Tool: yawl_convert_to_ocel
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification buildConvertToOcelTool() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("eventData", Map.of("type", "string",
            "description", "Raw event log data: CSV rows, JSON array of event objects, or XES/XML. "
                + "CSV must have a header row. JSON must be an array of objects. "
                + "XML should follow XES standard."));
        props.put("format", Map.of("type", "string",
            "description", "Format hint: 'csv', 'json', or 'xml'. Omit for auto-detection from content."));

        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name("yawl_convert_to_ocel")
            .description("Convert raw enterprise event log data (CSV, JSON, or XML/XES) to OCEL 2.0 "
                + "(Object-Centric Event Log, IEEE 2023 standard). Automatically detects format from "
                + "content if not specified. Infers schema columns (case ID, activity, timestamp, "
                + "resources) via keyword heuristics — no LLM required. Output is valid OCEL 2.0 JSON "
                + "interoperable with PM4Py, ocpa, PM4JS, and Celonis Process Sphere™. "
                + "This implements van der Aalst's Process Intelligence connection 5: data preparation.")
            .inputSchema(new McpSchema.JsonSchema("object", props, List.of("eventData"),
                false, null, Map.of()))
            .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            long start = System.currentTimeMillis();
            String eventData = getString(request.arguments(), "eventData", null);
            if (eventData == null || eventData.isBlank()) {
                return errorResult("'eventData' parameter is required.");
            }

            String format = getString(request.arguments(), "format", null);

            try {
                OcedBridge bridge;
                if (format == null || format.isBlank() || format.equalsIgnoreCase("auto")) {
                    bridge = OcedBridgeFactory.autoDetect(eventData);
                } else {
                    bridge = OcedBridgeFactory.forFormat(format);
                }

                OcedSchema schema = bridge.inferSchema(eventData);
                String ocel2Json = bridge.convert(eventData, schema);
                long elapsed = System.currentTimeMillis() - start;

                String response = buildConvertResponse(ocel2Json, schema, bridge.formatName(), elapsed);
                return successResult(response);

            } catch (PIException e) {
                return errorResult("Conversion failed: " + e.getMessage());
            } catch (Exception e) {
                return errorResult("Unexpected error during OCEL conversion: " + e.getMessage());
            }
        });
    }

    // =========================================================================
    // Tool: yawl_infer_oced_schema
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification buildInferOcedSchemaTool() {
        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("dataSample", Map.of("type", "string",
            "description", "Sample of event log data (first 10 rows or 2KB). "
                + "Supports CSV, JSON array, or XML/XES."));
        props.put("format", Map.of("type", "string",
            "description", "Format hint: 'csv', 'json', or 'xml'. Omit for auto-detection."));

        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name("yawl_infer_oced_schema")
            .description("Infer the OCED (Object-Centric Event Data) schema from a raw event log sample. "
                + "Identifies which columns represent: case/object ID, activity/task name, "
                + "timestamp, and additional object types or attributes. "
                + "Uses keyword heuristics for deterministic schema detection — no LLM required. "
                + "Use this to preview schema before calling yawl_convert_to_ocel, "
                + "or to understand the structure of an event log.")
            .inputSchema(new McpSchema.JsonSchema("object", props, List.of("dataSample"),
                false, null, Map.of()))
            .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            long start = System.currentTimeMillis();
            String dataSample = getString(request.arguments(), "dataSample", null);
            if (dataSample == null || dataSample.isBlank()) {
                return errorResult("'dataSample' parameter is required.");
            }

            String format = getString(request.arguments(), "format", null);

            try {
                OcedBridge bridge;
                if (format == null || format.isBlank() || format.equalsIgnoreCase("auto")) {
                    bridge = OcedBridgeFactory.autoDetect(dataSample);
                } else {
                    bridge = OcedBridgeFactory.forFormat(format);
                }

                OcedSchema schema = bridge.inferSchema(dataSample);
                long elapsed = System.currentTimeMillis() - start;

                String response = buildSchemaResponse(schema, bridge.formatName(), elapsed);
                return successResult(response);

            } catch (PIException e) {
                return errorResult("Schema inference failed: " + e.getMessage());
            } catch (Exception e) {
                return errorResult("Unexpected error during schema inference: " + e.getMessage());
            }
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String buildConvertResponse(String ocel2Json, OcedSchema schema,
                                        String detectedFormat, long elapsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("OCEL 2.0 Conversion Result\n");
        sb.append("==========================\n");
        sb.append("Detected format:   ").append(detectedFormat.toUpperCase()).append("\n");
        sb.append("Schema inferred:   ").append(schema.aiInferred() ? "AI-assisted" : "heuristic").append("\n");
        sb.append("Case ID column:    ").append(schema.caseIdColumn()).append("\n");
        sb.append("Activity column:   ").append(schema.activityColumn()).append("\n");
        sb.append("Timestamp column:  ").append(schema.timestampColumn()).append("\n");
        if (!schema.objectTypeColumns().isEmpty()) {
            sb.append("Object types:      ").append(String.join(", ", schema.objectTypeColumns())).append("\n");
        }
        if (!schema.attributeColumns().isEmpty()) {
            sb.append("Attributes:        ").append(String.join(", ", schema.attributeColumns())).append("\n");
        }
        sb.append("\nOCEL 2.0 JSON:\n");
        sb.append(ocel2Json).append("\n");
        sb.append("\nElapsed: ").append(elapsed).append("ms");
        return sb.toString();
    }

    private String buildSchemaResponse(OcedSchema schema, String detectedFormat, long elapsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("Inferred OCED Schema\n");
        sb.append("====================\n");
        sb.append("Schema ID:         ").append(schema.schemaId()).append("\n");
        sb.append("Detected format:   ").append(detectedFormat.toUpperCase()).append("\n");
        sb.append("Inferred via:      ").append(schema.aiInferred() ? "AI-assisted" : "heuristic").append("\n");
        sb.append("Inferred at:       ").append(schema.inferredAt()).append("\n\n");
        sb.append("Column Mapping:\n");
        sb.append("  caseIdColumn:    ").append(schema.caseIdColumn()).append("\n");
        sb.append("  activityColumn:  ").append(schema.activityColumn()).append("\n");
        sb.append("  timestampColumn: ").append(schema.timestampColumn()).append("\n");
        if (!schema.objectTypeColumns().isEmpty()) {
            sb.append("  objectTypes:     ").append(String.join(", ", schema.objectTypeColumns())).append("\n");
        }
        if (!schema.attributeColumns().isEmpty()) {
            sb.append("  attributes:      ").append(String.join(", ", schema.attributeColumns())).append("\n");
        }
        sb.append("\nElapsed: ").append(elapsed).append("ms");
        return sb.toString();
    }

    private String getString(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val instanceof String s ? s : defaultValue;
    }

    private static McpSchema.CallToolResult successResult(String text) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(message)), true, null, null);
    }
}
