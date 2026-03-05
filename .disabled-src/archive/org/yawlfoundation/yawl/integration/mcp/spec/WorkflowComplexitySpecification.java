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
import org.yawlfoundation.yawl.integration.mcp.complexity.WorkflowCognitiveAnalyzer;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Workflow Cognitive Load Analysis MCP tool specification.
 *
 * <p>Provides a single tool that analyzes the cognitive complexity of workflow
 * specifications using Halstead-inspired metrics adapted for BPM. Computes:
 * <ul>
 *   <li>Cognitive load score (0-100)</li>
 *   <li>Halstead complexity metrics</li>
 *   <li>Cyclomatic complexity</li>
 *   <li>Testing burden estimates</li>
 *   <li>Developer onboarding time</li>
 *   <li>Risk assessment (LOW/MODERATE/HIGH/CRITICAL)</li>
 *   <li>Actionable refactoring recommendations</li>
 * </ul>
 *
 * <p>Output is presented as rich ASCII art with metrics breakdown, peer comparison,
 * and specific refactoring guidance.
 *
 * <p>This analysis is unprecedented in the BPM industry and enables process architects
 * to understand and manage workflow complexity from a human cognitive perspective.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WorkflowComplexitySpecification {

    private WorkflowComplexitySpecification() {
        throw new UnsupportedOperationException(
            "WorkflowComplexitySpecification is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the Workflow Cognitive Load Analysis MCP tool specification.
     *
     * @param interfaceBClient the YAWL InterfaceB client for specification data access
     * @param interfaceAClient the YAWL InterfaceA client (reserved for future use)
     * @param sessionHandle    the active YAWL session handle
     * @return list containing the single cognitive load analysis tool specification
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
        tools.add(createCognitiveLoadTool(interfaceBClient, sessionHandle));
        return tools;
    }

    /**
     * Creates the yawl_cognitive_load tool specification.
     *
     * @param interfaceBClient the YAWL InterfaceB client
     * @param sessionHandle    the active session handle
     * @return the cognitive load analysis tool specification
     */
    private static McpServerFeatures.SyncToolSpecification createCognitiveLoadTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        // Define tool input schema: required specId, optional specVersion
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specId", Map.of(
            "type", "string",
            "description", "Workflow specification identifier to analyze"));
        props.put("specVersion", Map.of(
            "type", "string",
            "description", "Specification version (optional, default: first match)"));

        List<String> required = List.of("specId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_cognitive_load")
                .description(
                    "Analyze cognitive complexity of a workflow specification using " +
                    "Halstead-inspired metrics adapted for BPM. Computes decision complexity, " +
                    "parallelism burden, maintainability index, testing effort estimate, and " +
                    "developer onboarding time. Provides specific refactoring recommendations " +
                    "to reduce complexity. No BPM tool has ever offered this analysis.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    // Parse required and optional parameters
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "specId");
                    String specVersion = optionalStringArg(params, "specVersion", null);

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

                    // Find matching specification
                    SpecificationData matchingSpec = findSpecification(
                        specifications, specId, specVersion);

                    if (matchingSpec == null) {
                        String availableSpecs = buildAvailableSpecsList(specifications);
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Specification not found: " + specId +
                                (specVersion != null ? " (version " + specVersion + ")" : "") +
                                "\n\n" + availableSpecs)),
                            false, null, null);
                    }

                    // Analyze the specification
                    WorkflowCognitiveAnalyzer.CognitiveProfile profile =
                        WorkflowCognitiveAnalyzer.analyze(matchingSpec);

                    // Generate and return report
                    String specName = matchingSpec.getName() != null ?
                        matchingSpec.getName() : specId;
                    String report = WorkflowCognitiveAnalyzer.generateReport(
                        profile, specName);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(report)),
                        false, null, null);

                } catch (Exception e) {
                    String errorMsg = "Cognitive load analysis error: " +
                        e.getClass().getSimpleName();
                    if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                        errorMsg += " — " + e.getMessage();
                    }
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(errorMsg)),
                        true, null, null);
                }
            });
    }

    /**
     * Finds a specification matching the given ID and optional version.
     *
     * @param specifications list of available specifications
     * @param specId         the specification identifier to find
     * @param specVersion    the specification version (optional, null = first match)
     * @return the matching specification, or null if not found
     */
    private static SpecificationData findSpecification(
            List<SpecificationData> specifications,
            String specId,
            String specVersion) {

        for (SpecificationData spec : specifications) {
            if (spec.getID() != null &&
                    specId.equals(spec.getID().getIdentifier())) {

                // If version specified, match it exactly
                if (specVersion != null) {
                    if (specVersion.equals(spec.getID().getVersionAsString())) {
                        return spec;
                    }
                } else {
                    // Version not specified, return first match
                    return spec;
                }
            }
        }

        return null;
    }

    /**
     * Builds a formatted list of available specifications.
     *
     * @param specifications list of available specifications
     * @return formatted string listing all available specifications
     */
    private static String buildAvailableSpecsList(List<SpecificationData> specifications) {
        StringBuilder sb = new StringBuilder();
        sb.append("Available specifications:\n\n");

        for (int i = 0; i < specifications.size(); i++) {
            SpecificationData spec = specifications.get(i);
            if (spec.getID() != null) {
                sb.append((i + 1)).append(". ");
                sb.append(spec.getID().getIdentifier());
                sb.append(" (v").append(spec.getID().getVersionAsString()).append(")");
                if (spec.getName() != null && !spec.getName().isEmpty()) {
                    sb.append(" - ").append(spec.getName());
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Extract a required string argument from the tool arguments map.
     *
     * @param args the tool arguments
     * @param name the argument name
     * @return the string value
     * @throws IllegalArgumentException if the argument is missing
     */
    private static String requireStringArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required argument missing: " + name);
        }
        return value.toString();
    }

    /**
     * Extract an optional string argument from the tool arguments map.
     *
     * @param args         the tool arguments
     * @param name         the argument name
     * @param defaultValue the default value if the argument is missing
     * @return the string value or the default
     */
    private static String optionalStringArg(Map<String, Object> args, String name,
                                            String defaultValue) {
        Object value = args.get(name);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }
}
