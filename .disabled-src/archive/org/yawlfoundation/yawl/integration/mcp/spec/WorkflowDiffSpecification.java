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
import org.yawlfoundation.yawl.integration.mcp.diff.WorkflowBehavioralDiffer;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Workflow Behavioral Diff MCP tool specification.
 *
 * <p>Provides a tool that semantically diffs two YAWL workflow specifications,
 * showing not just XML changes but BEHAVIORAL changes — new tasks, removed tasks,
 * structural modifications, complexity delta, and regression risk analysis.
 *
 * <p>Unlike simple XML diff tools, this focuses on workflow execution impact:
 * identifies critical path changes, parallelism additions, exception handler
 * modifications, and provides actionable deployment recommendations.
 *
 * <p>Output is presented as a comprehensive ASCII report suitable for
 * change impact analysis and version management.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WorkflowDiffSpecification {

    private WorkflowDiffSpecification() {
        throw new UnsupportedOperationException(
            "WorkflowDiffSpecification is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the Workflow Behavioral Diff MCP tool specification.
     *
     * @param interfaceBClient the YAWL InterfaceB client for specification data access
     * @param interfaceAClient the YAWL InterfaceA client (reserved for future use)
     * @param sessionHandle    the active YAWL session handle
     * @return list containing the workflow diff tool specification
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
        tools.add(createWorkflowDiffTool(interfaceBClient, sessionHandle));
        return tools;
    }

    /**
     * Creates the yawl_diff_workflows tool specification.
     *
     * @param interfaceBClient the YAWL InterfaceB client
     * @param sessionHandle    the active session handle
     * @return the workflow diff tool specification
     */
    private static McpServerFeatures.SyncToolSpecification createWorkflowDiffTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spec1Id", Map.of(
            "type", "string",
            "description", "Identifier of first workflow specification (from version)"));
        props.put("spec2Id", Map.of(
            "type", "string",
            "description", "Identifier of second workflow specification (to version)"));
        props.put("spec1Version", Map.of(
            "type", "string",
            "description", "Version of first specification (optional, uses first match if not specified)"));
        props.put("spec2Version", Map.of(
            "type", "string",
            "description", "Version of second specification (optional, uses first match if not specified)"));

        List<String> required = List.of("spec1Id", "spec2Id");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_diff_workflows")
                .description(
                    "Semantically diff two loaded workflow specifications. Identifies behavioral changes: " +
                    "added/removed tasks, structural modifications (new splits/joins), complexity delta, and " +
                    "regression risk. Unlike XML diff, focuses on workflow BEHAVIORAL impact. Essential for " +
                    "version management and change impact analysis. Output: comprehensive ASCII report with " +
                    "structural changes, complexity metrics, behavioral fingerprints, and deployment recommendations.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String spec1Id = requireStringArg(params, "spec1Id");
                    String spec2Id = requireStringArg(params, "spec2Id");
                    String spec1Version = optionalStringArg(params, "spec1Version", null);
                    String spec2Version = optionalStringArg(params, "spec2Version", null);

                    // Get all loaded specifications
                    List<SpecificationData> allSpecs = interfaceBClient.getSpecificationList(sessionHandle);

                    if (allSpecs == null || allSpecs.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "No workflow specifications loaded. " +
                                "Upload specifications to the YAWL engine before diffing.")),
                            false, null, null);
                    }

                    // Find matching specifications
                    SpecificationData spec1 = findSpecification(allSpecs, spec1Id, spec1Version);
                    SpecificationData spec2 = findSpecification(allSpecs, spec2Id, spec2Version);

                    if (spec1 == null) {
                        String availableSpecs = buildAvailableSpecsMessage(allSpecs);
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Specification 1 with ID '" + spec1Id + "' not found.\n\n" +
                                "Available specifications:\n" + availableSpecs)),
                            false, null, null);
                    }

                    if (spec2 == null) {
                        String availableSpecs = buildAvailableSpecsMessage(allSpecs);
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Specification 2 with ID '" + spec2Id + "' not found.\n\n" +
                                "Available specifications:\n" + availableSpecs)),
                            false, null, null);
                    }

                    // Perform diff
                    WorkflowBehavioralDiffer.DiffResult diff = WorkflowBehavioralDiffer.diff(spec1, spec2);

                    // Generate report
                    String report = WorkflowBehavioralDiffer.generateReport(diff);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(report)),
                        false, null, null);

                } catch (IllegalArgumentException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Specification error: " + e.getMessage())),
                        false, null, null);
                } catch (Exception e) {
                    String errorMsg = "Workflow diff error: " + e.getClass().getSimpleName();
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
     * Finds a specification by ID and optional version.
     *
     * @param specs list of all specifications
     * @param specId the specification ID to match
     * @param version the version to match (null for first match)
     * @return the matching specification, or null if not found
     */
    private static SpecificationData findSpecification(
            List<SpecificationData> specs,
            String specId,
            String version) {

        for (SpecificationData spec : specs) {
            if (spec.getID() != null && spec.getID().getIdentifier().equals(specId)) {
                if (version == null) {
                    return spec;
                }
                if (spec.getID().getVersion().equals(version)) {
                    return spec;
                }
            }
        }

        return null;
    }

    /**
     * Builds a human-readable message listing available specifications.
     *
     * @param specs list of all specifications
     * @return formatted message with spec details
     */
    private static String buildAvailableSpecsMessage(List<SpecificationData> specs) {
        StringBuilder sb = new StringBuilder();

        for (SpecificationData spec : specs) {
            String name = spec.getName() != null ? spec.getName() : "(unknown)";
            String id = spec.getID() != null ? spec.getID().getIdentifier() : "(unknown ID)";
            String version = spec.getID() != null ? spec.getID().getVersionAsString() : "(unknown)";
            String status = spec.getStatus() != null ? spec.getStatus() : "active";

            sb.append("  • ").append(name).append("\n");
            sb.append("    ID: ").append(id).append(" | Version: ").append(version)
                    .append(" | Status: ").append(status).append("\n");
        }

        return sb.toString();
    }

    /**
     * Extracts a required string argument from parameters.
     *
     * @param params the parameters map
     * @param key the parameter key
     * @return the string value
     * @throws IllegalArgumentException if parameter is missing or not a string
     */
    private static String requireStringArg(Map<String, Object> params, String key) {
        if (params == null || !params.containsKey(key)) {
            throw new IllegalArgumentException("Required parameter '" + key + "' is missing");
        }

        Object value = params.get(key);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException(
                "Parameter '" + key + "' must be a non-empty string");
        }

        return (String) value;
    }

    /**
     * Extracts an optional string argument from parameters.
     *
     * @param params the parameters map
     * @param key the parameter key
     * @param defaultValue the default value if not present
     * @return the string value, or default if not present
     */
    private static String optionalStringArg(Map<String, Object> params, String key, String defaultValue) {
        if (params == null || !params.containsKey(key)) {
            return defaultValue;
        }

        Object value = params.get(key);
        if (value instanceof String) {
            String str = (String) value;
            return str.isEmpty() ? defaultValue : str;
        }

        return defaultValue;
    }
}
