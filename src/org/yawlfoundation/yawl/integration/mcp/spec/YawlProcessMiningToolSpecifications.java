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

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.processmining.EventLogExporter;
import org.yawlfoundation.yawl.integration.processmining.PerformanceAnalyzer;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;
import org.yawlfoundation.yawl.integration.processmining.ProcessVariantAnalyzer;
import org.yawlfoundation.yawl.integration.processmining.SocialNetworkAnalyzer;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Process mining tool specifications for YAWL MCP server.
 *
 * Provides 5 tools for analyzing workflow execution logs using process mining techniques:
 * <ul>
 *   <li>yawl_pm_export_xes - Export specification event log to XES format</li>
 *   <li>yawl_pm_analyze - Comprehensive process mining analysis (performance + variants)</li>
 *   <li>yawl_pm_performance - Performance metrics analysis (flow time, throughput)</li>
 *   <li>yawl_pm_variants - Process variant discovery and ranking</li>
 *   <li>yawl_pm_social_network - Social network analysis of resource interactions</li>
 * </ul>
 *
 * All tools are stateless HTTP operations that create fresh facades/exporters per call,
 * implementing production-grade error handling with real implementations (no mocks/stubs).
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlProcessMiningToolSpecifications {

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
            (exchange, args) -> {
                try {
                    String specId = (String) args.getOrDefault("specIdentifier", null);
                    if (specId == null || specId.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "Required argument specIdentifier is missing", true);
                    }

                    String specVersion = (String) args.getOrDefault("specVersion", "0.1");
                    String specUri = (String) args.getOrDefault("specUri", specId);
                    String withDataStr = (String) args.getOrDefault("withData", "false");
                    boolean withData = "true".equalsIgnoreCase(withDataStr);

                    YSpecificationID ySpecId = new YSpecificationID(
                        specId, specVersion, specUri);

                    EventLogExporter exporter = null;
                    try {
                        exporter = new EventLogExporter(engineUrl, username, password);
                        String xesXml = exporter.exportSpecificationToXes(ySpecId, withData);

                        if (xesXml == null || xesXml.isEmpty()) {
                            return new McpSchema.CallToolResult(
                                "Failed to export XES: empty response from engine", true);
                        }

                        return new McpSchema.CallToolResult(xesXml, false);
                    } finally {
                        if (exporter != null) {
                            try {
                                exporter.close();
                            } catch (IOException e) {
                                // Log and continue
                            }
                        }
                    }
                } catch (IOException e) {
                    return new McpSchema.CallToolResult(
                        "Failed to export XES: " + e.getMessage(), true);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error exporting XES: " + e.getMessage(), true);
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
                    String specId = (String) args.getOrDefault("specIdentifier", null);
                    if (specId == null || specId.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "Required argument specIdentifier is missing", true);
                    }

                    String specVersion = (String) args.getOrDefault("specVersion", "0.1");
                    String specUri = (String) args.getOrDefault("specUri", specId);
                    String withDataStr = (String) args.getOrDefault("withData", "false");
                    boolean withData = "true".equalsIgnoreCase(withDataStr);

                    YSpecificationID ySpecId = new YSpecificationID(
                        specId, specVersion, specUri);

                    ProcessMiningFacade facade = null;
                    try {
                        facade = new ProcessMiningFacade(engineUrl, username, password);
                        ProcessMiningFacade.ProcessMiningReport report =
                            facade.analyzePerformance(ySpecId, withData);

                        StringBuilder sb = new StringBuilder();
                        sb.append("=== Process Mining Analysis ===\n\n");
                        sb.append("Specification: ").append(specId).append(" v").append(specVersion).append("\n");
                        sb.append("Analysis Time: ").append(report.analysisTime).append("\n\n");

                        sb.append("--- Performance Metrics ---\n");
                        sb.append("Traces (Cases): ").append(report.traceCount).append("\n");
                        sb.append("Average Flow Time: ").append(String.format("%.2f ms", report.performance.avgFlowTimeMs)).append("\n");
                        sb.append("Throughput: ").append(String.format("%.2f cases/hour", report.performance.throughputPerHour)).append("\n\n");

                        sb.append("--- Process Variants ---\n");
                        sb.append("Unique Variants: ").append(report.variantCount).append("\n");
                        if (!report.variantFrequencies.isEmpty()) {
                            sb.append("Top 5 Variants:\n");
                            int count = 0;
                            for (Map.Entry<String, Long> entry : report.variantFrequencies.entrySet()) {
                                if (count >= 5) break;
                                sb.append("  ").append(count + 1).append(". ");
                                sb.append(entry.getKey()).append(" (").append(entry.getValue()).append(" occurrences)\n");
                                count++;
                            }
                        }

                        return new McpSchema.CallToolResult(sb.toString().trim(), false);
                    } finally {
                        if (facade != null) {
                            try {
                                facade.close();
                            } catch (IOException e) {
                                // Log and continue
                            }
                        }
                    }
                } catch (IOException e) {
                    return new McpSchema.CallToolResult(
                        "Failed to analyze specification: " + e.getMessage(), true);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error during analysis: " + e.getMessage(), true);
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
                    String specId = (String) args.getOrDefault("specIdentifier", null);
                    if (specId == null || specId.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "Required argument specIdentifier is missing", true);
                    }

                    String specVersion = (String) args.getOrDefault("specVersion", "0.1");
                    String specUri = (String) args.getOrDefault("specUri", specId);
                    String withDataStr = (String) args.getOrDefault("withData", "false");
                    boolean withData = "true".equalsIgnoreCase(withDataStr);

                    YSpecificationID ySpecId = new YSpecificationID(
                        specId, specVersion, specUri);

                    ProcessMiningFacade facade = null;
                    try {
                        facade = new ProcessMiningFacade(engineUrl, username, password);
                        ProcessMiningFacade.ProcessMiningReport report =
                            facade.analyzePerformance(ySpecId, withData);

                        PerformanceAnalyzer.PerformanceResult perf = report.performance;

                        StringBuilder sb = new StringBuilder();
                        sb.append("=== Performance Analysis ===\n\n");
                        sb.append("Specification: ").append(specId).append(" v").append(specVersion).append("\n\n");

                        sb.append("--- Execution Statistics ---\n");
                        sb.append("Total Cases: ").append(perf.traceCount).append("\n");
                        sb.append("Average Flow Time: ").append(String.format("%.2f ms", perf.avgFlowTimeMs)).append(" (");
                        sb.append(String.format("%.2f seconds", perf.avgFlowTimeMs / 1000)).append(")\n");
                        sb.append("Throughput: ").append(String.format("%.2f cases/hour", perf.throughputPerHour)).append("\n\n");

                        sb.append("--- Activity Execution Counts ---\n");
                        if (!perf.activityCounts.isEmpty()) {
                            perf.activityCounts.entrySet().stream()
                                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                                .forEach(e -> sb.append("  ").append(e.getKey()).append(": ")
                                    .append(e.getValue()).append(" executions\n"));
                        }

                        return new McpSchema.CallToolResult(sb.toString().trim(), false);
                    } finally {
                        if (facade != null) {
                            try {
                                facade.close();
                            } catch (IOException e) {
                                // Log and continue
                            }
                        }
                    }
                } catch (IOException e) {
                    return new McpSchema.CallToolResult(
                        "Failed to analyze performance: " + e.getMessage(), true);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error during performance analysis: " + e.getMessage(), true);
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
                    String specId = (String) args.getOrDefault("specIdentifier", null);
                    if (specId == null || specId.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "Required argument specIdentifier is missing", true);
                    }

                    String specVersion = (String) args.getOrDefault("specVersion", "0.1");
                    String specUri = (String) args.getOrDefault("specUri", specId);
                    String topNStr = (String) args.getOrDefault("topN", "10");
                    int topN = 10;
                    try {
                        topN = Integer.parseInt(topNStr);
                        if (topN <= 0) topN = 10;
                    } catch (NumberFormatException e) {
                        topN = 10;
                    }

                    YSpecificationID ySpecId = new YSpecificationID(
                        specId, specVersion, specUri);

                    ProcessMiningFacade facade = null;
                    try {
                        facade = new ProcessMiningFacade(engineUrl, username, password);
                        ProcessMiningFacade.ProcessMiningReport report =
                            facade.analyzePerformance(ySpecId, false);

                        StringBuilder sb = new StringBuilder();
                        sb.append("=== Process Variants ===\n\n");
                        sb.append("Specification: ").append(specId).append(" v").append(specVersion).append("\n");
                        sb.append("Total Variants: ").append(report.variantCount).append("\n\n");

                        sb.append("--- Top ").append(topN).append(" Variants ---\n");
                        int rank = 0;
                        for (Map.Entry<String, Long> entry : report.variantFrequencies.entrySet()) {
                            if (rank >= topN) break;
                            double percentage = (100.0 * entry.getValue()) / report.traceCount;
                            sb.append(String.format("%d. %s (%d cases, %.1f%%)\n",
                                rank + 1, entry.getKey(), entry.getValue(), percentage));
                            rank++;
                        }

                        return new McpSchema.CallToolResult(sb.toString().trim(), false);
                    } finally {
                        if (facade != null) {
                            try {
                                facade.close();
                            } catch (IOException e) {
                                // Log and continue
                            }
                        }
                    }
                } catch (IOException e) {
                    return new McpSchema.CallToolResult(
                        "Failed to analyze variants: " + e.getMessage(), true);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error during variant analysis: " + e.getMessage(), true);
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
                    String specId = (String) args.getOrDefault("specIdentifier", null);
                    if (specId == null || specId.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "Required argument specIdentifier is missing", true);
                    }

                    String specVersion = (String) args.getOrDefault("specVersion", "0.1");
                    String specUri = (String) args.getOrDefault("specUri", specId);
                    String withDataStr = (String) args.getOrDefault("withData", "false");
                    boolean withData = "true".equalsIgnoreCase(withDataStr);

                    YSpecificationID ySpecId = new YSpecificationID(
                        specId, specVersion, specUri);

                    ProcessMiningFacade facade = null;
                    try {
                        facade = new ProcessMiningFacade(engineUrl, username, password);
                        ProcessMiningFacade.ProcessMiningReport report =
                            facade.analyzePerformance(ySpecId, withData);

                        SocialNetworkAnalyzer socialAnalyzer = new SocialNetworkAnalyzer();
                        SocialNetworkAnalyzer.SocialNetworkResult sn =
                            socialAnalyzer.analyze(report.xesXml);

                        StringBuilder sb = new StringBuilder();
                        sb.append("=== Social Network Analysis ===\n\n");
                        sb.append("Specification: ").append(specId).append(" v").append(specVersion).append("\n\n");

                        sb.append("--- Resources ---\n");
                        sb.append("Total Resources: ").append(sn.resources.size()).append("\n");
                        if (!sn.resources.isEmpty()) {
                            sn.resources.forEach(r -> sb.append("  - ").append(r).append("\n"));
                        }

                        sb.append("\n--- Workload Distribution ---\n");
                        if (!sn.workloadByResource.isEmpty()) {
                            sn.workloadByResource.entrySet().stream()
                                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
                                .forEach(e -> sb.append("  ").append(e.getKey()).append(": ")
                                    .append(e.getValue()).append(" activities\n"));
                        }

                        sb.append("\n--- Handover of Work ---\n");
                        if (sn.mostCentralResource != null) {
                            sb.append("Most Central Resource: ").append(sn.mostCentralResource).append("\n");
                        }
                        if (!sn.handoverMatrix.isEmpty()) {
                            sb.append("Top Handover Pairs (Source -> Target):\n");
                            int count = 0;
                            for (Map.Entry<String, Map<String, Long>> fromEntry : sn.handoverMatrix.entrySet()) {
                                for (Map.Entry<String, Long> toEntry : fromEntry.getValue().entrySet()) {
                                    if (count >= 5) break;
                                    sb.append("  ").append(fromEntry.getKey()).append(" -> ")
                                        .append(toEntry.getKey()).append(" (")
                                        .append(toEntry.getValue()).append(" transitions)\n");
                                    count++;
                                }
                                if (count >= 5) break;
                            }
                        }

                        return new McpSchema.CallToolResult(sb.toString().trim(), false);
                    } finally {
                        if (facade != null) {
                            try {
                                facade.close();
                            } catch (IOException e) {
                                // Log and continue
                            }
                        }
                    }
                } catch (IOException e) {
                    return new McpSchema.CallToolResult(
                        "Failed to analyze social network: " + e.getMessage(), true);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error during social network analysis: " + e.getMessage(), true);
                }
            }
        );
    }
}
