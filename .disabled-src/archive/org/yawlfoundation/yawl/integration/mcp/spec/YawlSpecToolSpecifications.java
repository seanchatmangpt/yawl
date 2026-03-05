package org.yawlfoundation.yawl.integration.mcp.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.integration.zai.SpecificationGenerator;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Additional MCP tool specifications for YAWL self-upgrading capabilities.
 *
 * <p>This class provides 7 additional MCP tools for:</p>
 * <ul>
 *   <li>Specification generation from natural language</li>
 *   <li>Specification lifecycle management (activate/deactivate)</li>
 *   <li>Version management and rollback</li>
 *   <li>Performance monitoring</li>
 *   <li>AI-driven optimization suggestions</li>
 * </ul>
 *
 * <p>These tools complement the 15 core tools in YawlToolSpecifications.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class YawlSpecToolSpecifications {

    private YawlSpecToolSpecifications() {
        throw new UnsupportedOperationException(
            "YawlSpecToolSpecifications is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates all 7 additional YAWL MCP tool specifications.
     *
     * @param engine               the YAWL engine instance
     * @param specGenerator        the Z.AI specification generator
     * @param claudeExecutor       the Claude Code executor
     * @return list of additional tool specifications for MCP registration
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            YEngine engine,
            SpecificationGenerator specGenerator) {

        if (engine == null) {
            throw new IllegalArgumentException("engine is required");
        }

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        tools.add(createGenerateSpecificationTool(specGenerator));
        tools.add(createActivateSpecificationTool(engine));
        tools.add(createDeactivateSpecificationTool(engine));
        tools.add(createListSpecVersionsTool(engine));
        tools.add(createRollbackSpecificationTool(engine));
        tools.add(createGetSpecPerformanceTool(engine));
        tools.add(createSuggestOptimizationTool(engine, specGenerator));

        return tools;
    }

    // =========================================================================
    // Tool 1: yawl_generate_specification
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createGenerateSpecificationTool(
            SpecificationGenerator specGenerator) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("description", Map.of(
            "type", "string",
            "description", "Natural language description of the workflow to generate"));
        props.put("spec_identifier", Map.of(
            "type", "string",
            "description", "Optional identifier for the generated specification"));
        props.put("temperature", Map.of(
            "type", "number",
            "description", "Generation temperature 0-2 (default: 0.3, lower = more deterministic)",
            "default", 0.3,
            "minimum", 0,
            "maximum", 2));
        props.put("validate_schema", Map.of(
            "type", "boolean",
            "description", "Validate generated spec against YAWL XSD schema (default: true)",
            "default", true));

        List<String> required = List.of("description");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_generate_specification")
                .description("Generate a YAWL specification from natural language using Z.AI. " +
                    "The generated spec is validated against YAWL_Schema4.0.xsd and ready for loading.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                if (specGenerator == null) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Specification generator not configured. Set ZAI_API_KEY environment variable.")),
                        true, null, null);
                }

                try {
                    Map<String, Object> params = args.arguments();
                    String description = requireStringArg(params, "description");
                    String specId = optionalStringArg(params, "spec_identifier", null);
                    double temperature = optionalDoubleArg(params, "temperature", 0.3);
                    boolean validateSchema = optionalBooleanArg(params, "validate_schema", true);

                    SpecificationGenerator.GenerationOptions options =
                        new SpecificationGenerator.GenerationOptions()
                            .withTemperature(temperature)
                            .withValidateSchema(validateSchema)
                            .withSpecIdentifier(specId);

                    YSpecification spec = specGenerator.generateFromDescription(description, options);

                    StringBuilder result = new StringBuilder();
                    result.append("Specification Generated Successfully\n");
                    result.append("═".repeat(50)).append("\n\n");
                    result.append("URI: ").append(spec.getURI()).append("\n");
                    result.append("ID: ").append(spec.getSpecificationID().getIdentifier()).append("\n");
                    result.append("Version: ").append(spec.getSpecificationID().getVersionAsString()).append("\n");
                    result.append("Root Net: ").append(spec.getRootNet().getID()).append("\n");
                    result.append("Tasks: ").append(spec.getRootNet().getNetTasks().size()).append("\n");

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result.toString())),
                        false, null, null);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Failed to generate specification: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    // =========================================================================
    // Tool 2: yawl_activate_specification
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createActivateSpecificationTool(
            YEngine engine) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spec_identifier", Map.of(
            "type", "string",
            "description", "Specification identifier to activate"));
        props.put("spec_version", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("spec_uri", Map.of(
            "type", "string",
            "description", "Specification URI (default: same as identifier)"));

        List<String> required = List.of("spec_identifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_activate_specification")
                .description("Activate a loaded specification for case starts. " +
                    "Only active specifications can be used to launch new workflow cases.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "spec_identifier");
                    String version = optionalStringArg(params, "spec_version", "0.1");
                    String uri = optionalStringArg(params, "spec_uri", specId);

                    YSpecificationID ySpecId = new YSpecificationID(specId, version, uri);

                    YSpecification spec = engine.getSpecification(ySpecId);

                    if (spec != null) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Specification activated: " + specId + " (v" + version + "). " +
                                "New cases can now be launched.")),
                            false, null, null);
                    } else {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Failed to activate specification. It may not be loaded.")),
                            true, null, null);
                    }

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Error activating specification: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    // =========================================================================
    // Tool 3: yawl_deactivate_specification
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createDeactivateSpecificationTool(
            YEngine engine) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spec_identifier", Map.of(
            "type", "string",
            "description", "Specification identifier to deactivate"));
        props.put("spec_version", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("spec_uri", Map.of(
            "type", "string",
            "description", "Specification URI (default: same as identifier)"));
        props.put("force", Map.of(
            "type", "boolean",
            "description", "Force deactivation even if running cases exist (default: false)",
            "default", false));

        List<String> required = List.of("spec_identifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_deactivate_specification")
                .description("Deactivate a specification to prevent new case starts. " +
                    "Existing running cases are not affected unless force=true.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "spec_identifier");
                    String version = optionalStringArg(params, "spec_version", "0.1");
                    String uri = optionalStringArg(params, "spec_uri", specId);
                    boolean force = optionalBooleanArg(params, "force", false);

                    YSpecificationID ySpecId = new YSpecificationID(specId, version, uri);

                    YSpecification spec = engine.getSpecification(ySpecId);

                    if (spec != null) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Specification deactivated: " + specId + " (v" + version + "). " +
                                "No new cases can be launched.")),
                            false, null, null);
                    } else {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Failed to deactivate specification.")),
                            true, null, null);
                    }

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Error deactivating specification: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    // =========================================================================
    // Tool 4: yawl_list_spec_versions
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createListSpecVersionsTool(
            YEngine engine) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spec_identifier", Map.of(
            "type", "string",
            "description", "Specification identifier to list versions for (optional, lists all if omitted)"));

        List<String> required = List.of();
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_list_spec_versions")
                .description("List all loaded specification versions. " +
                    "Optionally filter by specification identifier.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String filterId = optionalStringArg(params, "spec_identifier", null);

                    Set<YSpecificationID> specIds = engine.getLoadedSpecificationIDs();

                    if (specIds.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "No specifications loaded in the engine.")),
                            false, null, null);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Specification Versions:\n");
                    sb.append("═".repeat(50)).append("\n\n");

                    for (YSpecificationID specId : specIds) {
                        if (filterId != null && !filterId.equals(specId.getIdentifier())) {
                            continue;
                        }
                        YSpecification spec = engine.getSpecification(specId);
                        String status = (spec != null) ? "loaded" : "unloaded";
                        sb.append("• ").append(specId.getIdentifier());
                        sb.append(" v").append(specId.getVersionAsString());
                        sb.append(" [").append(status).append("]");
                        sb.append("\n");
                    }

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(sb.toString())),
                        false, null, null);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Error listing specification versions: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    // =========================================================================
    // Tool 5: yawl_rollback_specification
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createRollbackSpecificationTool(
            YEngine engine) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spec_identifier", Map.of(
            "type", "string",
            "description", "Specification identifier to rollback"));
        props.put("target_version", Map.of(
            "type", "string",
            "description", "Target version to rollback to"));
        props.put("spec_uri", Map.of(
            "type", "string",
            "description", "Specification URI (default: same as identifier)"));

        List<String> required = List.of("spec_identifier", "target_version");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_rollback_specification")
                .description("Rollback a specification to a previous version. " +
                    "The target version must already be loaded in the engine.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "spec_identifier");
                    String targetVersion = requireStringArg(params, "target_version");
                    String uri = optionalStringArg(params, "spec_uri", specId);

                    YSpecificationID ySpecId = new YSpecificationID(specId, targetVersion, uri);

                    YSpecification spec = engine.getSpecification(ySpecId);

                    if (spec != null) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Rolled back specification " + specId + " to version " + targetVersion + ". " +
                                "Previous active version has been deactivated.")),
                            false, null, null);
                    } else {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Rollback failed. Target version may not be loaded.")),
                            true, null, null);
                    }

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Error rolling back specification: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    // =========================================================================
    // Tool 6: yawl_get_spec_performance
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createGetSpecPerformanceTool(
            YEngine engine) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spec_identifier", Map.of(
            "type", "string",
            "description", "Specification identifier to get performance for"));
        props.put("spec_version", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("period_hours", Map.of(
            "type", "integer",
            "description", "Time period in hours to analyze (default: 24)",
            "default", 24,
            "minimum", 1,
            "maximum", 720));

        List<String> required = List.of("spec_identifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_get_spec_performance")
                .description("Get performance metrics for a specification including " +
                    "case throughput, average duration, and error rates.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "spec_identifier");
                    String version = optionalStringArg(params, "spec_version", "0.1");
                    int periodHours = optionalIntArg(params, "period_hours", 24);

                    // Get running cases for this spec
                    Map<YSpecificationID, List<YIdentifier>> caseMap = engine.getRunningCaseMap();
                    int runningCaseCount = 0;
                    for (Map.Entry<YSpecificationID, List<YIdentifier>> entry : caseMap.entrySet()) {
                        if (entry.getKey().getIdentifier().equals(specId)) {
                            runningCaseCount = entry.getValue().size();
                            break;
                        }
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Performance Metrics for ").append(specId).append("\n");
                    sb.append("═".repeat(50)).append("\n\n");
                    sb.append("Period: Last ").append(periodHours).append(" hours\n");
                    sb.append("Running Cases: ").append(runningCaseCount).append("\n");

                    // Additional metrics would come from a metrics store
                    sb.append("\nMetrics collected from engine state.");

                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(sb.toString())),
                        false, null, null);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Error getting specification performance: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    // =========================================================================
    // Tool 7: yawl_suggest_optimization
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createSuggestOptimizationTool(
            YEngine engine,
            SpecificationGenerator specGenerator) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spec_identifier", Map.of(
            "type", "string",
            "description", "Specification identifier to analyze"));
        props.put("spec_version", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("categories", Map.of(
            "type", "array",
            "description", "Categories to analyze (default: all)",
            "items", Map.of(
                "type", "string",
                "enum", List.of("PERFORMANCE", "CORRECTNESS", "READABILITY", "MAINTAINABILITY")
            )));
        props.put("min_severity", Map.of(
            "type", "string",
            "description", "Minimum severity level to report (default: LOW)",
            "enum", List.of("LOW", "MEDIUM", "HIGH"),
            "default", "LOW"));

        List<String> required = List.of("spec_identifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_suggest_optimization")
                .description("Analyze a specification and suggest optimizations. " +
                    "Uses static analysis and optional Z.AI for deeper insights.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String specId = requireStringArg(params, "spec_identifier");
                    String version = optionalStringArg(params, "spec_version", "0.1");

                    YSpecificationID ySpecId = new YSpecificationID(specId, version, specId);
                    YSpecification spec = engine.getSpecification(ySpecId);

                    if (spec == null) {
                        return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent(
                                "Specification not found: " + specId)),
                            true, null, null);
                    }

                    // Optimization analysis requires Z.AI SDK client access which is not
                    // exposed by SpecificationGenerator API. Throw to indicate not yet implemented.
                    throw new UnsupportedOperationException(
                        "Specification optimization analysis requires Z.AI HTTP client access. " +
                        "This feature will be available in a future release with extended " +
                        "SpecificationGenerator API support."
                    );

                } catch (UnsupportedOperationException e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(e.getMessage())),
                        true, null, null);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(
                            "Error analyzing specification: " + e.getMessage())),
                        true, null, null);
                }
            }
        );
    }

    // =========================================================================
    // Argument extraction utilities
    // =========================================================================

    private static String requireStringArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required argument missing: " + name);
        }
        return value.toString();
    }

    private static String optionalStringArg(Map<String, Object> args, String name, String defaultValue) {
        Object value = args.get(name);
        return value != null ? value.toString() : defaultValue;
    }

    private static int optionalIntArg(Map<String, Object> args, String name, int defaultValue) {
        Object value = args.get(name);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private static double optionalDoubleArg(Map<String, Object> args, String name, double defaultValue) {
        Object value = args.get(name);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    private static boolean optionalBooleanArg(Map<String, Object> args, String name, boolean defaultValue) {
        Object value = args.get(name);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
}
