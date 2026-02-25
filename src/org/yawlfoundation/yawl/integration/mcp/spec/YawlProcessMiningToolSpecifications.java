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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;

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
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlProcessMiningToolSpecifications {

    private final String engineUrl;
    private final String username;
    private final String password;
    private final ProcessMiningFacade facade;

    /**
     * Create process mining tool specifications.
     *
     * @param engineUrl base URL of YAWL engine (e.g., http://localhost:8080/yawl)
     * @param username  YAWL admin username
     * @param password  YAWL admin password
     * @param facade    ProcessMiningFacade for real analysis operations
     */
    public YawlProcessMiningToolSpecifications(String engineUrl, String username,
                                                String password, ProcessMiningFacade facade) {
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
        this.facade = java.util.Objects.requireNonNull(facade, "facade is required");
    }

    private YSpecificationID buildSpecId(Map<String, Object> params) {
        String identifier = (String) params.get("specIdentifier");
        String version = params.containsKey("specVersion")
            ? (String) params.get("specVersion") : "0.1";
        String uri = params.containsKey("specUri")
            ? (String) params.get("specUri") : identifier;
        return new YSpecificationID(identifier, version, uri);
    }

    private boolean parseWithData(Map<String, Object> params) {
        String withData = (String) params.get("withData");
        return "true".equalsIgnoreCase(withData);
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
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    YSpecificationID specId = buildSpecId(params);
                    boolean withData = parseWithData(params);
                    var report = facade.analyze(specId, null, withData);
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(report.xesXml)),
                        false, null, null);
                } catch (IOException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Process mining export failed: " + e.getMessage())),
                        true, null, null);
                }
            }
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
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    YSpecificationID specId = buildSpecId(params);
                    boolean withData = parseWithData(params);
                    var report = facade.analyze(specId, null, withData);
                    StringBuilder sb = new StringBuilder();
                    sb.append("Process Mining Analysis Report\n");
                    sb.append("==============================\n\n");
                    sb.append("Specification: ").append(specId.getIdentifier()).append("\n");
                    sb.append("Traces: ").append(report.traceCount).append("\n");
                    sb.append("Variants: ").append(report.variantCount).append("\n\n");
                    if (report.performance != null) {
                        sb.append("Performance:\n");
                        sb.append("  Avg Flow Time: ").append(report.performance.avgFlowTimeMs).append(" ms\n");
                        sb.append("  Throughput: ").append(String.format("%.2f", report.performance.throughputPerHour)).append(" cases/hour\n\n");
                    }
                    if (report.conformance != null) {
                        sb.append("Conformance:\n");
                        sb.append("  Fitness: ").append(String.format("%.3f", report.conformance.computeFitness())).append("\n\n");
                    }
                    if (report.variantFrequencies != null && !report.variantFrequencies.isEmpty()) {
                        sb.append("Top Variants:\n");
                        int rank = 1;
                        for (var entry : report.variantFrequencies.entrySet()) {
                            if (rank > 10) break;
                            sb.append("  ").append(rank++).append(". ").append(entry.getKey())
                              .append(" (").append(entry.getValue()).append(" cases)\n");
                        }
                    }
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(sb.toString())),
                        false, null, null);
                } catch (IOException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Process mining analysis failed: " + e.getMessage())),
                        true, null, null);
                }
            }
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
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    YSpecificationID specId = buildSpecId(params);
                    boolean withData = parseWithData(params);
                    var report = facade.analyzePerformance(specId, withData);
                    StringBuilder sb = new StringBuilder();
                    sb.append("Performance Analysis\n");
                    sb.append("====================\n\n");
                    sb.append("Specification: ").append(specId.getIdentifier()).append("\n");
                    if (report.performance != null) {
                        sb.append("Avg Flow Time: ").append(report.performance.avgFlowTimeMs).append(" ms\n");
                        sb.append("Throughput: ").append(String.format("%.2f", report.performance.throughputPerHour)).append(" cases/hour\n");
                    }
                    sb.append("Traces: ").append(report.traceCount).append("\n");
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(sb.toString())),
                        false, null, null);
                } catch (IOException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Performance analysis failed: " + e.getMessage())),
                        true, null, null);
                }
            }
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
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    YSpecificationID specId = buildSpecId(params);
                    int topN = params.containsKey("topN")
                        ? Integer.parseInt((String) params.get("topN")) : 10;
                    var report = facade.analyze(specId, null, false);
                    StringBuilder sb = new StringBuilder();
                    sb.append("Process Variants\n");
                    sb.append("================\n\n");
                    sb.append("Total variants: ").append(report.variantCount).append("\n\n");
                    if (report.variantFrequencies != null) {
                        int rank = 1;
                        for (var entry : report.variantFrequencies.entrySet()) {
                            if (rank > topN) break;
                            sb.append(rank++).append(". ").append(entry.getKey())
                              .append(" (").append(entry.getValue()).append(" cases)\n");
                        }
                    }
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(sb.toString())),
                        false, null, null);
                } catch (IOException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Variant analysis failed: " + e.getMessage())),
                        true, null, null);
                }
            }
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
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    YSpecificationID specId = buildSpecId(params);
                    boolean withData = parseWithData(params);
                    var report = facade.analyze(specId, null, withData);
                    StringBuilder sb = new StringBuilder();
                    sb.append("Social Network Analysis\n");
                    sb.append("=======================\n\n");
                    sb.append("Specification: ").append(specId.getIdentifier()).append("\n");
                    sb.append("Traces analyzed: ").append(report.traceCount).append("\n\n");
                    sb.append("XES event log available for further analysis.\n");
                    sb.append("Event log size: ").append(report.xesXml != null ? report.xesXml.length() : 0)
                      .append(" characters\n");
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(sb.toString())),
                        false, null, null);
                } catch (IOException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Social network analysis failed: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }
}
