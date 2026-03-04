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
import org.yawlfoundation.yawl.integration.synthesis.PatternBasedSynthesizer;
import org.yawlfoundation.yawl.integration.synthesis.SynthesisResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool specification for offline pattern-based workflow synthesis.
 *
 * <p>Exposes the {@link PatternBasedSynthesizer} as an MCP tool that generates
 * valid YAWL specification XML from natural-language descriptions and/or explicit
 * pattern selections. No external AI service required.</p>
 *
 * <p>Tool: {@code yawl_synthesize_from_pattern}</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlPatternSynthesisToolSpecifications {

    private final PatternBasedSynthesizer synthesizer;

    public YawlPatternSynthesisToolSpecifications() {
        this.synthesizer = new PatternBasedSynthesizer();
    }

    /**
     * Creates all pattern synthesis MCP tool specifications.
     *
     * @return list containing the synthesize_from_pattern tool
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        tools.add(createSynthesizeFromPatternTool());
        return tools;
    }

    private McpServerFeatures.SyncToolSpecification createSynthesizeFromPatternTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("description", Map.of(
            "type", "string",
            "description", "Natural-language description of the desired workflow "
                + "(e.g., 'Review document then approve or reject in parallel')"));
        props.put("pattern", Map.of(
            "type", "string",
            "description", "Optional explicit pattern override: "
                + "'sequential', 'parallel', 'exclusive', 'loop', or 'multi_instance'. "
                + "If omitted, the pattern is inferred from the description.",
            "enum", List.of("sequential", "parallel", "exclusive", "loop", "multi_instance")));
        props.put("tasks", Map.of(
            "type", "array",
            "items", Map.of("type", "string"),
            "description", "Optional explicit task names. If omitted, tasks are "
                + "extracted from the description."));

        List<String> required = List.of("description");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_synthesize_from_pattern")
                .description("Generate a valid YAWL workflow specification from a "
                    + "natural-language description using the 5 foundational Workflow "
                    + "Control-flow Patterns (WCP-1 Sequence, WCP-2 Parallel, WCP-4 "
                    + "Exclusive, WCP-12 Multi-Instance, WCP-21 Loop). No external AI "
                    + "service required. Returns YAWL specificationSet XML ready for upload.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String description = (String) params.get("description");

                    @SuppressWarnings("unchecked")
                    List<String> tasks = params.containsKey("tasks")
                        ? (List<String>) params.get("tasks")
                        : List.of();

                    PatternBasedSynthesizer.PatternSpec spec;

                    if (params.containsKey("pattern") && params.get("pattern") != null) {
                        String patternStr = (String) params.get("pattern");
                        spec = buildExplicitPattern(patternStr, tasks, description);
                    } else {
                        spec = synthesizer.parseDescription(description, tasks);
                    }

                    SynthesisResult result = synthesizer.synthesize(spec);

                    String response = "Pattern: " + spec.wcpId()
                        + "\nTasks: " + spec.tasks()
                        + "\nElapsed: " + result.elapsed().toMillis() + "ms"
                        + "\n\n" + result.specXml();

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(response)),
                        false, null, null);

                } catch (IllegalArgumentException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Invalid input: " + e.getMessage())),
                        true, null, null);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Synthesis failed: " + e.getMessage())),
                        true, null, null);
                }
            });
    }

    private PatternBasedSynthesizer.PatternSpec buildExplicitPattern(
            String patternStr, List<String> tasks, String description) {
        List<String> resolvedTasks = (tasks != null && !tasks.isEmpty())
            ? tasks
            : synthesizer.parseDescription(description, List.of()).tasks();

        return switch (patternStr.toLowerCase()) {
            case "sequential" -> new PatternBasedSynthesizer.PatternSpec.Sequential(resolvedTasks);
            case "parallel" -> new PatternBasedSynthesizer.PatternSpec.Parallel(resolvedTasks);
            case "exclusive" -> new PatternBasedSynthesizer.PatternSpec.Exclusive(resolvedTasks);
            case "loop" -> new PatternBasedSynthesizer.PatternSpec.Loop(resolvedTasks);
            case "multi_instance" -> new PatternBasedSynthesizer.PatternSpec.MultiInstance(
                resolvedTasks, 1, Math.max(3, resolvedTasks.size()), 1);
            default -> throw new IllegalArgumentException(
                "Unknown pattern: " + patternStr
                    + ". Use: sequential, parallel, exclusive, loop, multi_instance");
        };
    }
}
