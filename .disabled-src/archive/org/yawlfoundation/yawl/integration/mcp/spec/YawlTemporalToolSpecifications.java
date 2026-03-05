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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.integration.temporal.AllPathsForkPolicy;
import org.yawlfoundation.yawl.integration.temporal.TemporalForkEngine;
import org.yawlfoundation.yawl.integration.temporal.TemporalForkResult;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Temporal case forking tool specifications for YAWL MCP server.
 *
 * <p>Provides a single tool {@code yawl_fork_case_futures} for exploring multiple
 * execution paths in a workflow case concurrently. This enables temporal analysis,
 * counterfactual scenario comparison, and risk assessment in live cases.</p>
 *
 * <p>The tool requires a YStatelessEngine and loaded YSpecification to function.
 * It forks a case into parallel execution branches, each exploring a different
 * task decision at the current decision point, and returns aggregated outcomes.</p>
 *
 * <h2>Tool Specification</h2>
 * <ul>
 *   <li><strong>yawl_fork_case_futures</strong> - Fork a case into parallel futures
 *       with alternative task decisions. Returns fork outcomes, decision paths, and
 *       dominant outcome analysis.</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since YAWL 6.0
 */
public final class YawlTemporalToolSpecifications {

    private final YStatelessEngine _engine;
    private final YSpecification _spec;
    private final ObjectMapper _mapper = new ObjectMapper();

    /**
     * Creates temporal tool specifications for a stateless engine and specification.
     *
     * @param engine the YStatelessEngine managing live cases
     * @param spec   the YAWL specification defining the workflow
     * @throws IllegalArgumentException if engine or spec is null
     */
    public YawlTemporalToolSpecifications(YStatelessEngine engine, YSpecification spec) {
        if (engine == null) {
            throw new IllegalArgumentException("engine is required");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec is required");
        }
        _engine = engine;
        _spec = spec;
    }

    /**
     * Creates all temporal MCP tool specifications.
     *
     * @return list containing one tool specification (yawl_fork_case_futures)
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        tools.add(createForkCaseFuturesTool());
        return tools;
    }

    // =========================================================================
    // Tool 1: yawl_fork_case_futures
    // =========================================================================

    /**
     * Creates the yawl_fork_case_futures tool specification.
     *
     * <p>This tool forks a live case into multiple parallel execution paths,
     * each exploring a different task decision at the current decision point.
     * The tool uses virtual threads to execute forks concurrently and returns
     * aggregated outcomes including decision paths and dominant outcome analysis.</p>
     *
     * @return the tool specification for MCP registration
     */
    private McpServerFeatures.SyncToolSpecification createForkCaseFuturesTool() {
        Map<String, Object> props = new LinkedHashMap<>();

        props.put("caseId", Map.of(
            "type", "string",
            "description", "The case ID to fork"));

        props.put("maxForks", Map.of(
            "type", "integer",
            "description", "Maximum number of parallel forks to explore (default: 5, min: 1, max: 20)"));

        props.put("maxWallTimeSeconds", Map.of(
            "type", "integer",
            "description", "Maximum wall-clock seconds for fork exploration (default: 30, min: 1, max: 300)"));

        List<String> required = List.of("caseId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_fork_case_futures")
                .description("Fork a YAWL case into parallel execution futures to explore " +
                    "alternative task decisions. Each fork executes a different task from " +
                    "the current set of enabled tasks. Returns decision paths, case outcomes, " +
                    "and dominant outcome analysis. Uses virtual threads for concurrent execution.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String caseId = requireStringArg(params, "caseId");
                    int maxForks = optionalIntArg(params, "maxForks", 5);
                    int maxWallTimeSeconds = optionalIntArg(params, "maxWallTimeSeconds", 30);

                    // Validate parameters
                    if (maxForks < 1 || maxForks > 20) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Parameter validation failed: maxForks must be between 1 and 20, got " + maxForks)),
                            true, null, null);
                    }
                    if (maxWallTimeSeconds < 1 || maxWallTimeSeconds > 300) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Parameter validation failed: maxWallTimeSeconds must be between 1 and 300, got " + maxWallTimeSeconds)),
                            true, null, null);
                    }

                    // Create temporal fork engine
                    TemporalForkEngine forker = new TemporalForkEngine(_engine, _spec);

                    // Fork the case
                    TemporalForkResult result = forker.fork(
                        caseId,
                        new AllPathsForkPolicy(maxForks),
                        Duration.ofSeconds(maxWallTimeSeconds)
                    );

                    // Build response
                    String response = formatForkResult(result);

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(response)),
                        false, null, null);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Error forking case: " + e.getClass().getSimpleName() + ": " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    /**
     * Formats a TemporalForkResult as human-readable text.
     *
     * @param result the fork result to format
     * @return formatted string containing fork outcomes and statistics
     */
    private String formatForkResult(TemporalForkResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== Temporal Case Fork Results ===\n");
        sb.append("Requested Forks: ").append(result.requestedForks()).append("\n");
        sb.append("Completed Forks: ").append(result.completedForks()).append("\n");
        sb.append("All Completed: ").append(result.allForksCompleted()).append("\n");
        sb.append("Wall Time: ").append(result.wallTime().toMillis()).append(" ms\n");
        sb.append("Dominant Outcome Index: ").append(result.dominantOutcomeIndex()).append("\n");
        sb.append("\n");

        if (result.forks().isEmpty()) {
            sb.append("No forks completed. Case may have no enabled tasks.\n");
        } else {
            sb.append("Fork Details:\n");
            for (int i = 0; i < result.forks().size(); i++) {
                var fork = result.forks().get(i);
                sb.append("\n[Fork ").append(i + 1).append(" ID: ").append(fork.forkId()).append("]\n");
                sb.append("  Decision Path: ").append(fork.decisionPath()).append("\n");
                sb.append("  Terminated Normally: ").append(fork.terminatedNormally()).append("\n");
                sb.append("  Duration: ").append(fork.durationMs()).append(" ms\n");
                sb.append("  Completed At: ").append(fork.completedAt()).append("\n");
                if (fork.outcomeXml() != null) {
                    String outcomePreview = fork.outcomeXml().substring(
                        0, Math.min(100, fork.outcomeXml().length()));
                    sb.append("  Outcome (preview): ").append(outcomePreview);
                    if (fork.outcomeXml().length() > 100) {
                        sb.append("...");
                    }
                    sb.append("\n");
                }
            }
        }

        if (result.dominantOutcomeIndex() >= 0) {
            sb.append("\nDominant Fork: #").append(result.dominantOutcomeIndex() + 1)
                .append(" (").append(result.getDominantFork().forkId()).append(")\n");
        } else {
            sb.append("\nDominant Fork: All outcomes unique\n");
        }

        return sb.toString();
    }

    /**
     * Extracts a required string argument from the parameters map.
     *
     * @param params the parameters map
     * @param name   the argument name
     * @return the string value
     * @throws IllegalArgumentException if the argument is missing or not a string
     */
    private static String requireStringArg(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter '" + name + "' is missing");
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("Parameter '" + name + "' must be a string");
        }
        return (String) value;
    }

    /**
     * Extracts an optional integer argument from the parameters map.
     *
     * @param params     the parameters map
     * @param name       the argument name
     * @param defaultVal the default value if missing
     * @return the integer value or default
     * @throws IllegalArgumentException if the argument is not an integer
     */
    private static int optionalIntArg(Map<String, Object> params, String name, int defaultVal) {
        Object value = params.get(name);
        if (value == null) {
            return defaultVal;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalArgumentException("Parameter '" + name + "' must be an integer");
    }
}
