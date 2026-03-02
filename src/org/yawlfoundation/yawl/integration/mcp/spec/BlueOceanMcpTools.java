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

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.temporal.AllPathsForkPolicy;
import org.yawlfoundation.yawl.integration.temporal.CaseFork;
import org.yawlfoundation.yawl.integration.temporal.TemporalForkEngine;
import org.yawlfoundation.yawl.integration.temporal.TemporalForkResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP tool registry for blue-ocean engine innovations.
 *
 * <p>Provides MCP tool wrappers for buried blue-ocean engines that were
 * previously only accessible via A2A or internal APIs:
 * <ul>
 *   <li>{@code yawl_temporal_fork} — TemporalForkEngine: parallel execution path exploration
 *       via virtual threads. Explores all possible task decision paths and returns the
 *       dominant (most common) outcome. Zero LLM tokens — pure deterministic simulation.</li>
 * </ul>
 *
 * <p>These tools close {@code V7Gap.BURIED_ENGINES_MCP_A2A_WIRING} by exposing engines
 * that were implemented but not wired into the MCP surface.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class BlueOceanMcpTools {

    private static final int DEFAULT_MAX_SECONDS = 10;

    /**
     * Create all blue-ocean MCP tool specifications.
     *
     * @return list of MCP tool specifications
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        return List.of(createTemporalForkTool());
    }

    // =========================================================================
    // Tool: yawl_temporal_fork
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createTemporalForkTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of("type", "string",
            "description", "Workflow case ID to fork (used as synthetic case identifier)"));
        props.put("taskNames", Map.of("type", "string",
            "description", "Comma-separated list of task names to explore as parallel execution paths"));
        props.put("maxSeconds", Map.of("type", "integer",
            "description", "Max wall-clock seconds for fork exploration (default: 10, max: 300)"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("caseId", "taskNames"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_temporal_fork")
                .description(
                    "Explore all possible execution paths of a workflow case using TemporalForkEngine. "
                    + "Forks into parallel virtual threads, each simulating a different task choice. "
                    + "Returns all explored paths and identifies the dominant (most common) outcome. "
                    + "Zero LLM tokens — pure deterministic simulation. "
                    + "Closes V7Gap.BURIED_ENGINES_MCP_A2A_WIRING.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String caseId = (String) params.get("caseId");
                    String taskNamesParam = (String) params.get("taskNames");

                    if (caseId == null || caseId.isBlank()) {
                        return errorResult("caseId parameter required");
                    }
                    if (taskNamesParam == null || taskNamesParam.isBlank()) {
                        return errorResult("taskNames parameter required (comma-separated task IDs)");
                    }

                    List<String> taskNames = Arrays.stream(taskNamesParam.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

                    if (taskNames.isEmpty()) {
                        return errorResult("taskNames contains no valid task names after parsing");
                    }

                    int maxSeconds = DEFAULT_MAX_SECONDS;
                    Object maxSecondsParam = params.get("maxSeconds");
                    if (maxSecondsParam != null) {
                        int parsed = ((Number) maxSecondsParam).intValue();
                        if (parsed < 1 || parsed > 300) {
                            return errorResult("maxSeconds must be between 1 and 300");
                        }
                        maxSeconds = parsed;
                    }

                    return runTemporalFork(caseId, taskNames, maxSeconds);
                } catch (Exception e) {
                    return errorResult("Temporal fork failed: " + e.getMessage());
                }
            }
        );
    }

    private McpSchema.CallToolResult runTemporalFork(
        String caseId, List<String> taskNames, int maxSeconds
    ) {
        long start = System.currentTimeMillis();

        // Build synthetic case XML for the integration path (no live YStatelessEngine needed)
        String syntheticCaseXml = buildSyntheticCaseXml(caseId, taskNames);

        TemporalForkEngine engine = TemporalForkEngine.forIntegration(
            id -> syntheticCaseXml,
            xml -> taskNames,
            (xml, taskId) -> xml + "<executed>" + taskId + "</executed>"
        );

        TemporalForkResult result = engine.fork(
            caseId,
            new AllPathsForkPolicy(taskNames.size()),
            Duration.ofSeconds(maxSeconds)
        );

        long elapsed = System.currentTimeMillis() - start;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("caseId", caseId);
        response.put("completedForks", result.completedForks());
        response.put("requestedForks", result.requestedForks());
        response.put("allCompleted", result.allForksCompleted());
        response.put("dominantPath", buildDominantPath(result));
        response.put("forks", buildForkSummaries(result.forks()));
        response.put("elapsed_ms", elapsed);
        response.put("engine", "TemporalForkEngine (virtual-thread parallel simulation)");

        return textResult(toJson(response));
    }

    private String buildSyntheticCaseXml(String caseId, List<String> taskNames) {
        return "<case id=\"" + caseId + "\"><tasks>"
            + taskNames.stream().map(t -> "<task>" + t + "</task>").collect(Collectors.joining())
            + "</tasks></case>";
    }

    private String buildDominantPath(TemporalForkResult result) {
        if (result.forks().isEmpty()) return "no-forks-completed";
        if (result.dominantOutcomeIndex() < 0) return "all-unique";
        CaseFork dominant = result.getDominantFork();
        return String.join("→", dominant.decisionPath());
    }

    private List<Map<String, Object>> buildForkSummaries(List<CaseFork> forks) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (CaseFork fork : forks) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("forkId", fork.forkId());
            summary.put("decisionPath", fork.decisionPath());
            summary.put("terminatedNormally", fork.terminatedNormally());
            summary.put("durationMs", fork.durationMs());
            summaries.add(summary);
        }
        return summaries;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static McpSchema.CallToolResult textResult(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), true, null, null);
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(sb, entry.getValue());
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(escapeJson(s)).append('"');
        } else if (value instanceof Number n) {
            sb.append(n);
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                if (item instanceof Map<?, ?> m) {
                    appendJsonValue(sb, (Map<String, Object>) m);
                } else {
                    appendJsonValue(sb, item);
                }
            }
            sb.append(']');
        } else if (value instanceof Map<?, ?> m) {
            sb.append(toJson((Map<String, Object>) m));
        } else {
            sb.append('"').append(escapeJson(value.toString())).append('"');
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
