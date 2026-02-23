/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.mcp.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Process mining tool specifications for YAWL MCP server.
 *
 * <p>Provides 5 tools for analyzing workflow execution logs using process mining techniques:
 * <ul>
 *   <li>yawl_pm_export_xes - Export specification event log to XES format</li>
 *   <li>yawl_pm_analyze - Comprehensive process mining analysis (performance + variants)</li>
 *   <li>yawl_pm_performance - Performance metrics analysis (flow time, throughput)</li>
 *   <li>yawl_pm_variants - Process variant discovery and ranking</li>
 *   <li>yawl_pm_social_network - Social network analysis of resource interactions</li>
 * </ul>
 *
 * <p><b>Note:</b> Process mining operations require the pm4py Python bridge which is not
 * included in the default YAWL Java distribution. All tools throw
 * {@link UnsupportedOperationException} until the Python process mining bridge is configured.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlProcessMiningToolSpecifications {

    private static final String PM_UNAVAILABLE =
        "Process mining requires the pm4py Python bridge which is not available " +
        "in this deployment. Contact your YAWL administrator to enable process mining.";

    private final String engineUrl;
    private final String username;
    private final String password;

    /**
     * Create process mining tool specifications.
     *
     * @param engineUrl base URL of YAWL engine (e.g., http://localhost:8080/yawl)
     * @param username  YAWL admin username
     * @param password  YAWL admin password
     */
    public YawlProcessMiningToolSpecifications(String engineUrl, String username, String password) {
        if (engineUrl == null || engineUrl.isEmpty()) {
            throw new IllegalArgumentException("engineUrl is required");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("username is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password is required");
        }
        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Creates all 5 YAWL process mining MCP tool specifications.
     *
     * @return list of all process mining tool specifications for MCP registration
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        tools.add(createExportXesTool());
        tools.add(createAnalyzeTool());
        tools.add(createPerformanceTool());
        tools.add(createVariantsTool());
        tools.add(createSocialNetworkTool());
        return tools;
    }

    // =========================================================================
    // Tool 1: yawl_pm_export_xes
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createExportXesTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specIdentifier", Map.of(
            "type", "string",
            "description", "Workflow specification identifier"));
        props.put("specVersion", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("specUri", Map.of(
            "type", "string",
            "description", "Specification URI (default: same as identifier)"));
        props.put("withData", Map.of(
            "type", "string",
            "description", "Include data attributes in export (true/false, default: false)"));

        List<String> required = List.of("specIdentifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_pm_export_xes")
                .description("Export a workflow specification's event log to XES " +
                    "(eXtensible Event Stream) format for process mining analysis. " +
                    "Returns the complete XES XML event log.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(PM_UNAVAILABLE)), true, null, null)
        );
    }

    // =========================================================================
    // Tool 2: yawl_pm_analyze
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createAnalyzeTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specIdentifier", Map.of(
            "type", "string",
            "description", "Workflow specification identifier"));
        props.put("specVersion", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("specUri", Map.of(
            "type", "string",
            "description", "Specification URI (default: same as identifier)"));
        props.put("withData", Map.of(
            "type", "string",
            "description", "Include data attributes in export (true/false, default: false)"));

        List<String> required = List.of("specIdentifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_pm_analyze")
                .description("Run comprehensive process mining analysis on a specification: " +
                    "performance metrics, process variants, and resource interaction patterns. " +
                    "Returns a formatted text summary of execution insights.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(PM_UNAVAILABLE)), true, null, null)
        );
    }

    // =========================================================================
    // Tool 3: yawl_pm_performance
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createPerformanceTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specIdentifier", Map.of(
            "type", "string",
            "description", "Workflow specification identifier"));
        props.put("specVersion", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("specUri", Map.of(
            "type", "string",
            "description", "Specification URI (default: same as identifier)"));
        props.put("withData", Map.of(
            "type", "string",
            "description", "Include data attributes in export (true/false, default: false)"));

        List<String> required = List.of("specIdentifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_pm_performance")
                .description("Analyze performance metrics for a workflow specification: " +
                    "average flow time, throughput, and activity execution counts. " +
                    "Returns formatted performance statistics.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(PM_UNAVAILABLE)), true, null, null)
        );
    }

    // =========================================================================
    // Tool 4: yawl_pm_variants
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createVariantsTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specIdentifier", Map.of(
            "type", "string",
            "description", "Workflow specification identifier"));
        props.put("specVersion", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("specUri", Map.of(
            "type", "string",
            "description", "Specification URI (default: same as identifier)"));
        props.put("topN", Map.of(
            "type", "string",
            "description", "Number of top variants to return (default: 10)"));

        List<String> required = List.of("specIdentifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_pm_variants")
                .description("Discover and rank process variants (unique activity sequences) " +
                    "in a workflow specification's execution log. Returns top-N variants " +
                    "ranked by frequency.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(PM_UNAVAILABLE)), true, null, null)
        );
    }

    // =========================================================================
    // Tool 5: yawl_pm_social_network
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createSocialNetworkTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specIdentifier", Map.of(
            "type", "string",
            "description", "Workflow specification identifier"));
        props.put("specVersion", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("specUri", Map.of(
            "type", "string",
            "description", "Specification URI (default: same as identifier)"));
        props.put("withData", Map.of(
            "type", "string",
            "description", "Include data attributes in export (true/false, default: false)"));

        List<String> required = List.of("specIdentifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_pm_social_network")
                .description("Analyze resource interaction patterns in a workflow specification: " +
                    "handover of work (resource transitions), workload distribution, " +
                    "and most central resources. Returns formatted social network metrics.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(PM_UNAVAILABLE)), true, null, null)
        );
    }
}
