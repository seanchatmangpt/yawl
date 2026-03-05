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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.mcp.spec;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.integration.synthesis.IntentSynthesizer;
import org.yawlfoundation.yawl.integration.synthesis.WorkflowIntent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.LinkedHashMap;

/**
 * MCP tool specification for workflow synthesis from business intent.
 *
 * <p>Provides one MCP tool: {@code yawl_synthesize_workflow}.
 * Takes a goal and list of activities, synthesizes a YAWL workflow,
 * and returns the generated specification and soundness summary.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlSynthesisToolSpecifications {

    private final IntentSynthesizer synthesizer;

    /**
     * Constructs MCP tool specifications for synthesis.
     *
     * @param synthesizer the intent synthesizer (non-null)
     * @throws NullPointerException if synthesizer is null
     */
    public YawlSynthesisToolSpecifications(IntentSynthesizer synthesizer) {
        this.synthesizer = Objects.requireNonNull(synthesizer, "synthesizer must not be null");
    }

    /**
     * Creates the yawl_synthesize_workflow MCP tool specification.
     *
     * @return MCP tool specification for workflow synthesis
     */
    public McpServerFeatures.SyncToolSpecification createSynthesisToolSpec() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("goal", Map.of(
            "type", "string",
            "description", "Business goal or objective for the workflow"
        ));
        props.put("activities", Map.of(
            "type", "array",
            "items", Map.of("type", "string"),
            "description", "Ordered list of activities to include in the workflow"
        ));
        props.put("wcpHints", Map.of(
            "type", "array",
            "items", Map.of("type", "string"),
            "description", "Workflow control patterns to apply (e.g., WCP-1, WCP-2)"
        ));

        List<String> required = List.of("goal", "activities");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_synthesize_workflow")
                .description("Synthesize a YAWL workflow from business intent. " +
                    "Generates a specification, verifies soundness, and returns the result.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> synthesizeWorkflow(args.arguments())
        );
    }

    /**
     * Handles the yawl_synthesize_workflow tool invocation.
     *
     * @param args tool arguments as a map
     * @return MCP result with generated spec or error message
     */
    private McpSchema.CallToolResult synthesizeWorkflow(Map<String, Object> args) {
        try {
            // Extract arguments
            String goal = (String) args.get("goal");
            if (goal == null || goal.isBlank()) {
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(
                        "Error: 'goal' parameter is required and must not be empty"
                    )),
                    true, null, null
                );
            }

            @SuppressWarnings("unchecked")
            List<String> activities = (List<String>) args.getOrDefault("activities", List.of());

            @SuppressWarnings("unchecked")
            List<String> wcpHints = (List<String>) args.getOrDefault("wcpHints", List.of());

            // Check if synthesizer is available
            if (!synthesizer.canSynthesize()) {
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(
                        "Error: SPARQL synthesis engine is unavailable. Ensure yawl-native service is running."
                    )),
                    true, null, null
                );
            }

            // Create intent and synthesize
            WorkflowIntent intent = new WorkflowIntent(goal, activities, wcpHints, Map.of());
            var result = synthesizer.synthesize(intent);

            // Format response
            StringBuilder response = new StringBuilder();
            response.append("Workflow Synthesis Result\n");
            response.append("=========================\n\n");
            response.append("Goal: ").append(goal).append("\n");
            response.append("Activities: ").append(activities).append("\n");
            response.append("Patterns Applied: ").append(result.wcpPatternsUsed()).append("\n\n");

            if (result.soundnessReport() != null) {
                response.append("Soundness Status: ")
                    .append(result.soundnessReport().isSound() ? "SOUND" : "UNSOUND")
                    .append("\n");
                response.append("Deadlock Count: ").append(result.soundnessReport().deadlockCount()).append("\n");
                response.append("Warning Count: ").append(result.soundnessReport().warningCount()).append("\n\n");
            }

            response.append("Generated Specification:\n");
            response.append(result.specXml());

            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(response.toString())),
                false, null, null
            );

        } catch (SparqlEngineException e) {
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(
                    "Error during synthesis: " + e.getMessage()
                )),
                true, null, null
            );
        } catch (Exception e) {
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(
                    "Unexpected error: " + e.getMessage()
                )),
                true, null, null
            );
        }
    }
}
