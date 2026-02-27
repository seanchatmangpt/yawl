/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.genome.WorkflowGenomeAnalyzer;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Workflow Genome Fingerprinting MCP tool specification.
 *
 * <p>Provides a single tool that analyzes the structural DNA of all loaded YAWL workflow
 * specifications by computing:
 * <ul>
 *   <li>Genome fingerprints (task count, split/join counts, loops, depth, parameters)</li>
 *   <li>Cosine similarity matrix showing structural similarity between all pairs of specs</li>
 *   <li>Genome clustering identifying structurally similar workflows above a configurable threshold</li>
 *   <li>Actionable insights (e.g., consolidation opportunities, reference implementations)</li>
 * </ul>
 *
 * <p>Output is presented as rich ASCII art suitable for analysis and presentation.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WorkflowGenomeSpecification {

    private WorkflowGenomeSpecification() {
        throw new UnsupportedOperationException(
            "WorkflowGenomeSpecification is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the Workflow Genome Fingerprinting MCP tool specification.
     *
     * @param interfaceBClient the YAWL InterfaceB client for specification data access
     * @param interfaceAClient the YAWL InterfaceA client (reserved for future use)
     * @param sessionHandle    the active YAWL session handle
     * @return list containing the single genome analysis tool specification
     * @throws IllegalArgumentException if any required parameter is null or invalid
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            InterfaceA_EnvironmentBasedClient interfaceAClient,
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
        tools.add(createGenomeAnalysisTool(interfaceBClient, sessionHandle));
        return tools;
    }

    /**
     * Creates the yawl_genome_analyze tool specification.
     *
     * @param interfaceBClient the YAWL InterfaceB client
     * @param sessionHandle    the active session handle
     * @return the genome analysis tool specification
     */
    private static McpServerFeatures.SyncToolSpecification createGenomeAnalysisTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        // Define tool input schema: optional threshold parameter
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("threshold", Map.of(
            "type", "integer",
            "description", "Similarity threshold % for clustering (default: 70, range: 0-100)"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, new ArrayList<>(), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_genome_analyze")
                .description("Analyze structural DNA of all loaded workflow specifications. " +
                    "Computes genome fingerprints (tasks, splits, joins, loops, depth, parameters), " +
                    "pairwise cosine similarity matrix, and clusters structurally similar workflows. " +
                    "Reveals hidden structural twins and consolidation opportunities. " +
                    "Output: rich ASCII report with genome profiles, similarity matrix, and cluster insights.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    // Parse optional threshold parameter (default: 70%)
                    int threshold = 70;
                    Map<String, Object> arguments = args.arguments();
                    if (arguments != null && arguments.containsKey("threshold")) {
                        Object thresholdValue = arguments.get("threshold");
                        if (thresholdValue instanceof Number) {
                            int parsedThreshold = ((Number) thresholdValue).intValue();
                            // Clamp to valid range [0, 100]
                            threshold = Math.max(0, Math.min(100, parsedThreshold));
                        }
                    }

                    // Retrieve all loaded specifications
                    List<SpecificationData> specifications =
                        interfaceBClient.getSpecificationList(sessionHandle);

                    // Handle empty specification list
                    if (specifications == null || specifications.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "No workflow specifications loaded. " +
                                "Upload specifications to the YAWL engine before analyzing.")),
                            false, null, null);
                    }

                    // Generate genome analysis report
                    String report = WorkflowGenomeAnalyzer.generateReport(specifications, threshold);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(report)),
                        false, null, null);

                } catch (Exception e) {
                    String errorMsg = "Genome analysis error: " + e.getClass().getSimpleName();
                    if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                        errorMsg += " — " + e.getMessage();
                    }
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(errorMsg)),
                        true, null, null);
                }
            });
    }
}
