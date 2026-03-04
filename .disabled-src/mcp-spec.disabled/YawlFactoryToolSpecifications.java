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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.integration.factory.ConversationalWorkflowFactory;
import org.yawlfoundation.yawl.integration.factory.ConversationalWorkflowFactory.FactoryException;
import org.yawlfoundation.yawl.integration.factory.ConversationalWorkflowFactory.FactoryResult;
import org.yawlfoundation.yawl.integration.factory.ConversationalWorkflowFactory.WorkflowHealth;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Static factory class that creates MCP tool specifications for the ConversationalWorkflowFactory.
 *
 * <p>Provides three tools for natural language workflow generation and refinement:
 * <ul>
 *   <li><b>yawl_generate_workflow</b> - Convert NL description to live workflow in <30s</li>
 *   <li><b>yawl_refine_workflow</b> - Improve specification based on feedback and conformance</li>
 *   <li><b>yawl_workflow_health</b> - Check conformance score and refinement recommendations</li>
 * </ul>
 * </p>
 *
 * <p>All tools use real {@link ConversationalWorkflowFactory} operations with Hibernate-backed
 * database access. No mocks, stubs, or fake implementations.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class YawlFactoryToolSpecifications {

    private YawlFactoryToolSpecifications() {
        throw new UnsupportedOperationException(
            "YawlFactoryToolSpecifications is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates all three ConversationalWorkflowFactory MCP tool specifications.
     *
     * @param factory the ConversationalWorkflowFactory instance
     * @return list of three tool specifications for MCP registration
     * @throws IllegalArgumentException if factory is null
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            ConversationalWorkflowFactory factory) {

        if (factory == null) {
            throw new IllegalArgumentException(
                "factory is required - provide a connected ConversationalWorkflowFactory instance");
        }

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        tools.add(createGenerateWorkflowTool(factory));
        tools.add(createRefineWorkflowTool(factory));
        tools.add(createWorkflowHealthTool(factory));

        return tools;
    }

    // =========================================================================
    // Tool 1: yawl_generate_workflow
    // =========================================================================

    /**
     * Creates the yawl_generate_workflow tool.
     *
     * <p>Converts a natural language workflow description to a live YAWL workflow
     * in <30 seconds. Returns the specification ID and launched case ID.</p>
     *
     * @param factory the ConversationalWorkflowFactory
     * @return tool specification for MCP registration
     */
    private static McpServerFeatures.SyncToolSpecification createGenerateWorkflowTool(
            ConversationalWorkflowFactory factory) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("description", Map.of(
            "type", "string",
            "description", "Natural language workflow description (e.g., 'Procurement workflow with approval and fulfillment')"));
        props.put("autoLaunch", Map.of(
            "type", "boolean",
            "description", "If true, automatically launch a case instance (default: true)"));

        List<String> required = List.of("description");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_generate_workflow")
                .description("Convert natural language workflow description to live YAWL workflow in <30 seconds. " +
                    "Generates specification, validates, deploys to engine, launches case, and starts conformance monitoring. " +
                    "Returns specification ID and case ID on success.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String description = requireStringArg(params, "description");
                    boolean autoLaunch = optionalBooleanArg(params, "autoLaunch", true);

                    if (!autoLaunch) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("autoLaunch=false not yet supported")),
                            true, null, null);
                    }

                    FactoryResult result = factory.generateAndDeploy(description);

                    if (result instanceof FactoryResult.Deployed deployed) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Workflow generated and deployed successfully.\n" +
                                "Specification ID: " + deployed.specId() + "\n" +
                                "Case ID: " + deployed.caseId() + "\n" +
                                "Deployed at: " + deployed.deployedAt() + "\n" +
                                "Status: Case is now running. Conformance will be assessed after 10+ executions.")),
                            false, null, null);
                    }

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Unexpected result type: " + result.getClass().getName())),
                        true, null, null);

                } catch (FactoryException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error generating workflow: " + e.getMessage())),
                        true, null, null);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Unexpected error: " + e.getMessage())),
                        true, null, null);
                }
            });
    }

    // =========================================================================
    // Tool 2: yawl_refine_workflow
    // =========================================================================

    /**
     * Creates the yawl_refine_workflow tool.
     *
     * <p>Improves an existing workflow specification based on feedback.
     * The feedback is used to guide Z.AI in generating an improved version
     * that addresses the identified issues.</p>
     *
     * @param factory the ConversationalWorkflowFactory
     * @return tool specification for MCP registration
     */
    private static McpServerFeatures.SyncToolSpecification createRefineWorkflowTool(
            ConversationalWorkflowFactory factory) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specId", Map.of(
            "type", "string",
            "description", "Specification ID returned from yawl_generate_workflow"));
        props.put("feedback", Map.of(
            "type", "string",
            "description", "Improvement feedback (e.g., 'Add budget approval step', 'Parallel review tasks instead of sequential')"));

        List<String> required = List.of("specId", "feedback");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_refine_workflow")
                .description("Improve an existing YAWL workflow specification based on feedback. " +
                    "Uses Z.AI to generate an improved version addressing the feedback, validates it, " +
                    "redeployes to engine, and resets conformance monitoring. Returns previous and current conformance scores.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "specId");
                    String feedback = requireStringArg(params, "feedback");

                    FactoryResult result = factory.refine(specId, feedback);

                    if (result instanceof FactoryResult.Refined refined) {
                        String conformanceText = refined.previousConformance() >= 0
                            ? String.format("Previous conformance: %.2f%%", refined.previousConformance() * 100)
                            : "No previous conformance data";

                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Workflow refined and redeployed successfully.\n" +
                                "Specification ID: " + refined.specId() + "\n" +
                                conformanceText + "\n" +
                                "Status: Specification refined and uploaded. Conformance reassessment scheduled.")),
                            false, null, null);
                    }

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Unexpected result type: " + result.getClass().getName())),
                        true, null, null);

                } catch (FactoryException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error refining workflow: " + e.getMessage())),
                        true, null, null);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Unexpected error: " + e.getMessage())),
                        true, null, null);
                }
            });
    }

    // =========================================================================
    // Tool 3: yawl_workflow_health
    // =========================================================================

    /**
     * Creates the yawl_workflow_health tool.
     *
     * <p>Checks the health status of a workflow specification, including
     * execution count, conformance score, and refinement recommendations.</p>
     *
     * @param factory the ConversationalWorkflowFactory
     * @return tool specification for MCP registration
     */
    private static McpServerFeatures.SyncToolSpecification createWorkflowHealthTool(
            ConversationalWorkflowFactory factory) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specId", Map.of(
            "type", "string",
            "description", "Specification ID returned from yawl_generate_workflow"));

        List<String> required = List.of("specId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_workflow_health")
                .description("Check the health and conformance status of a YAWL workflow specification. " +
                    "Returns execution count, conformance score (token-based replay fitness), " +
                    "whether refinement is needed (conformance < 0.90), and actionable recommendations.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "specId");

                    WorkflowHealth health = factory.getHealth(specId);

                    String conformanceText = health.conformanceScore() >= 0
                        ? String.format("%.2f%%", health.conformanceScore() * 100)
                        : "Not yet assessed";

                    String needsRefinementText = health.needsRefinement()
                        ? "YES - Conformance below 0.90 threshold"
                        : "NO - Conformance acceptable";

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Workflow Health Report\n" +
                            "======================\n" +
                            "Specification ID: " + health.specId() + "\n" +
                            "Execution Count: " + health.executionCount() + "\n" +
                            "Conformance Score (Token-Based Replay Fitness): " + conformanceText + "\n" +
                            "Needs Refinement: " + needsRefinementText + "\n" +
                            "Last Assessed: " + health.lastAssessedAt() + "\n" +
                            "Recommendation: " + health.recommendation())),
                        false, null, null);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error checking workflow health: " + e.getMessage())),
                        true, null, null);
                }
            });
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static String requireStringArg(Map<String, Object> params, String key)
            throws IllegalArgumentException {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Required argument '" + key + "' is missing");
        }
        return value.toString();
    }

    private static boolean optionalBooleanArg(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
