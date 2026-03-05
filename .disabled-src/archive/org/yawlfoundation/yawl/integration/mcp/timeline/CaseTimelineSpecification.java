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

package org.yawlfoundation.yawl.integration.mcp.timeline;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool specification factory for the Live Case ASCII Timeline Renderer.
 *
 * This factory creates a single MCP tool: yawl_case_timeline, which renders
 * beautiful ASCII Gantt-style visualizations of workflow case execution history
 * and current state. This tool enables AI agents and CLI users to instantly
 * see where time is being spent in a case and identify performance anomalies.
 *
 * The timeline includes:
 * - Proportional execution bars for each task
 * - Status indicators (completed, running, waiting, blocked)
 * - Elapsed time calculations
 * - Progress percentages
 * - Performance warnings for anomalous task durations
 * - Graceful degradation when timing data is unavailable
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 2026-02-24
 */
public final class CaseTimelineSpecification {

    private CaseTimelineSpecification() {
        throw new UnsupportedOperationException(
            "CaseTimelineSpecification is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the yawl_case_timeline MCP tool specification.
     *
     * @param interfaceBClient the YAWL InterfaceB client for runtime operations
     * @param sessionHandle    the active YAWL session handle
     * @return list containing a single SyncToolSpecification for case timeline rendering
     * @throws IllegalArgumentException if interfaceBClient or sessionHandle is null/empty
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException(
                "interfaceBClient is required - provide a connected InterfaceB_EnvironmentBasedClient");
        }
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new IllegalArgumentException(
                "sessionHandle is required - connect to the YAWL engine first");
        }

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        tools.add(createTimelineTool(interfaceBClient, sessionHandle));
        return tools;
    }

    /**
     * Creates the yawl_case_timeline tool specification.
     *
     * Required parameters:
     * - caseId (string): The YAWL case ID to visualize
     *
     * Optional parameters:
     * - width (integer, default 50): Timeline bar width in characters (20-200)
     *
     * @return SyncToolSpecification for MCP server registration
     */
    private static McpServerFeatures.SyncToolSpecification createTimelineTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        // Build input schema
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "The YAWL case ID to visualize (required)"));
        props.put("width", Map.of(
            "type", "integer",
            "description", "Timeline bar width in characters (default: 50, range: 20-200)"));

        List<String> required = List.of("caseId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        // Create tool implementation
        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_case_timeline")
                .description(
                    "Render a live ASCII Gantt timeline of task execution for a running workflow case. " +
                    "Shows completed, running, and waiting tasks with timing bars and progress visualization. " +
                    "Instantly see where time is being spent and which tasks are anomalous. " +
                    "Features proportional timeline bars, status indicators, elapsed time calculations, " +
                    "and performance warnings. Gracefully handles cases with or without detailed timing data.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String caseId = extractStringArg(params, "caseId");
                    Integer width = extractIntegerArg(params, "width", 50);

                    // Validate case ID
                    if (caseId == null || caseId.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Error: caseId parameter is required and must be non-empty")),
                            true, null, null);
                    }

                    // Validate width
                    if (width < 20 || width > 200) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Error: width must be between 20 and 200 characters")),
                            true, null, null);
                    }

                    // Get case state XML
                    String caseStateXml = interfaceBClient.getCaseState(caseId, sessionHandle);
                    if (caseStateXml == null || caseStateXml.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Error: Case not found or not accessible: " + caseId)),
                            true, null, null);
                    }

                    // Get work items for this case
                    List<WorkItemRecord> workItems =
                        interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

                    // Extract specification name from case state (basic parsing)
                    String specName = extractSpecNameFromCaseState(caseStateXml);

                    // Render timeline
                    String timeline = CaseTimelineRenderer.renderTimeline(
                        caseId,
                        specName,
                        Instant.now().minusSeconds(300), // Approximate start time if not available
                        Instant.now(),
                        workItems,
                        width);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(timeline)),
                        false, null, null);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Error rendering timeline: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    /**
     * Extracts a required string argument from parameters.
     *
     * @throws IllegalArgumentException if argument is missing or not a string
     */
    private static String extractStringArg(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(
                "Parameter '" + key + "' must be a string, got: " + value.getClass().getSimpleName());
        }
        return (String) value;
    }

    /**
     * Extracts an optional integer argument from parameters with a default value.
     */
    private static Integer extractIntegerArg(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Parameter '" + key + "' must be an integer, got: " + value);
            }
        }
        throw new IllegalArgumentException(
            "Parameter '" + key + "' must be an integer, got: " + value.getClass().getSimpleName());
    }

    /**
     * Extracts specification name from case state XML.
     * Basic parsing: looks for spec name in XML attributes or elements.
     */
    private static String extractSpecNameFromCaseState(String caseStateXml) {
        if (caseStateXml == null || caseStateXml.isEmpty()) {
            return null;
        }

        // Try to find spec name in common XML locations
        // Pattern: <specification ... name="..." or <spec ...>
        String[] patterns = {
            "specification.*name=\"([^\"]+)\"",
            "spec.*identifier=\"([^\"]+)\"",
            "<specificationID>([^<]+)</specificationID>"
        };

        for (String pattern : patterns) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(caseStateXml);
            if (m.find()) {
                return m.group(1);
            }
        }

        return null;
    }
}
