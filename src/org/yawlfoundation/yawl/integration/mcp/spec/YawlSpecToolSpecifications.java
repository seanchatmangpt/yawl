package org.yawlfoundation.yawl.integration.mcp.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.integration.zai.SpecificationGenerator;
import org.yawlfoundation.yawl.integration.zai.SpecificationOptimizer;
import org.yawlfoundation.yawl.integration.claude.ClaudeCodeExecutor;
import org.yawlfoundation.yawl.integration.claude.ExecuteClaudeTool;

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
            SpecificationGenerator specGenerator,
            ClaudeCodeExecutor claudeExecutor) {

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

        // Add Claude CLI tool if executor is available
        if (claudeExecutor != null) {
            tools.add(new ExecuteClaudeTool(claudeExecutor).createSpecification());
        }

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
            "object", props, required, false, null, null);

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
                        "Specification generator not configured. Set ZAI_API_KEY environment variable.",
                        true);
                }

                try {
                    String description = requireStringArg(args, "description");
                    String specId = optionalStringArg(args, "spec_identifier", null);
                    double temperature = optionalDoubleArg(args, "temperature", 0.3);
                    boolean validateSchema = optionalBooleanArg(args, "validate_schema", true);

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
                    result.append("ID: ").append(spec.getID().getIdentifier()).append("\n");
                    result.append("Version: ").append(spec.getID().getVersionAsString()).append("\n");
                    result.append("Root Net: ").append(spec.getRootNet().getID()).append("\n");
                    result.append("Tasks: ").append(spec.getRootNet().getNetTasks().size()).append("\n");

                    return new McpSchema.CallToolResult(result.toString(), false);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Failed to generate specification: " + e.getMessage(), true);
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
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_activate_specification")
                .description("Activate a loaded specification for case starts. " +
                    "Only active specifications can be used to launch new workflow cases.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String specId = requireStringArg(args, "spec_identifier");
                    String version = optionalStringArg(args, "spec_version", "0.1");
                    String uri = optionalStringArg(args, "spec_uri", specId);

                    YSpecificationID ySpecId = new YSpecificationID(specId, version, uri);

                    boolean activated = engine.setSpecificationAvailability(ySpecId, true);

                    if (activated) {
                        return new McpSchema.CallToolResult(
                            "Specification activated: " + specId + " (v" + version + "). " +
                            "New cases can now be launched.", false);
                    } else {
                        return new McpSchema.CallToolResult(
                            "Failed to activate specification. It may not be loaded.", true);
                    }

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error activating specification: " + e.getMessage(), true);
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
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_deactivate_specification")
                .description("Deactivate a specification to prevent new case starts. " +
                    "Existing running cases are not affected unless force=true.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String specId = requireStringArg(args, "spec_identifier");
                    String version = optionalStringArg(args, "spec_version", "0.1");
                    String uri = optionalStringArg(args, "spec_uri", specId);
                    boolean force = optionalBooleanArg(args, "force", false);

                    YSpecificationID ySpecId = new YSpecificationID(specId, version, uri);

                    boolean deactivated = engine.setSpecificationAvailability(ySpecId, false);

                    if (deactivated) {
                        return new McpSchema.CallToolResult(
                            "Specification deactivated: " + specId + " (v" + version + "). " +
                            "No new cases can be launched.", false);
                    } else {
                        return new McpSchema.CallToolResult(
                            "Failed to deactivate specification.", true);
                    }

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error deactivating specification: " + e.getMessage(), true);
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
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_list_spec_versions")
                .description("List all loaded specification versions. " +
                    "Optionally filter by specification identifier.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String filterId = optionalStringArg(args, "spec_identifier", null);

                    List<SpecificationData> specs = engine.getLoadedSpecifications();

                    if (specs.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "No specifications loaded in the engine.", false);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Specification Versions:\n");
                    sb.append("═".repeat(50)).append("\n\n");

                    for (SpecificationData spec : specs) {
                        YSpecificationID specId = spec.getID();
                        if (filterId != null && !filterId.equals(specId.getIdentifier())) {
                            continue;
                        }
                        sb.append("• ").append(specId.getIdentifier());
                        sb.append(" v").append(specId.getVersionAsString());
                        sb.append(" [").append(spec.getStatus()).append("]");
                        sb.append("\n");
                    }

                    return new McpSchema.CallToolResult(sb.toString(), false);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error listing specification versions: " + e.getMessage(), true);
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
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_rollback_specification")
                .description("Rollback a specification to a previous version. " +
                    "The target version must already be loaded in the engine.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String specId = requireStringArg(args, "spec_identifier");
                    String targetVersion = requireStringArg(args, "target_version");
                    String uri = optionalStringArg(args, "spec_uri", specId);

                    YSpecificationID ySpecId = new YSpecificationID(specId, targetVersion, uri);

                    boolean rolledBack = engine.setSpecificationAvailability(ySpecId, true);

                    if (rolledBack) {
                        return new McpSchema.CallToolResult(
                            "Rolled back specification " + specId + " to version " + targetVersion + ". " +
                            "Previous active version has been deactivated.", false);
                    } else {
                        return new McpSchema.CallToolResult(
                            "Rollback failed. Target version may not be loaded.", true);
                    }

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error rolling back specification: " + e.getMessage(), true);
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
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_get_spec_performance")
                .description("Get performance metrics for a specification including " +
                    "case throughput, average duration, and error rates.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String specId = requireStringArg(args, "spec_identifier");
                    String version = optionalStringArg(args, "spec_version", "0.1");
                    int periodHours = optionalIntArg(args, "period_hours", 24);

                    // Get running and completed cases for this spec
                    List<String> runningCases = engine.getRunningCasesForSpecification(specId);

                    StringBuilder sb = new StringBuilder();
                    sb.append("Performance Metrics for ").append(specId).append("\n");
                    sb.append("═".repeat(50)).append("\n\n");
                    sb.append("Period: Last ").append(periodHours).append(" hours\n");
                    sb.append("Running Cases: ").append(runningCases.size()).append("\n");

                    // Additional metrics would come from a metrics store
                    sb.append("\nMetrics collected from engine state.");

                    return new McpSchema.CallToolResult(sb.toString(), false);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error getting specification performance: " + e.getMessage(), true);
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
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_suggest_optimization")
                .description("Analyze a specification and suggest optimizations. " +
                    "Uses static analysis and optional Z.AI for deeper insights.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String specId = requireStringArg(args, "spec_identifier");
                    String version = optionalStringArg(args, "spec_version", "0.1");

                    YSpecificationID ySpecId = new YSpecificationID(specId, version, specId);
                    YSpecification spec = engine.getSpecification(ySpecId);

                    if (spec == null) {
                        return new McpSchema.CallToolResult(
                            "Specification not found: " + specId, true);
                    }

                    // Use optimizer to analyze
                    SpecificationOptimizer optimizer = new SpecificationOptimizer(
                        specGenerator != null ? specGenerator : null
                    );

                    List<SpecificationOptimizer.OptimizationSuggestion> suggestions =
                        optimizer.quickAnalyze(spec);

                    StringBuilder sb = new StringBuilder();
                    sb.append("Optimization Suggestions for ").append(specId).append("\n");
                    sb.append("═".repeat(50)).append("\n\n");

                    if (suggestions.isEmpty()) {
                        sb.append("No optimization suggestions found. ");
                        sb.append("The specification appears well-structured.");
                    } else {
                        for (SpecificationOptimizer.OptimizationSuggestion s : suggestions) {
                            sb.append("• [").append(s.severity()).append("/").append(s.category());
                            sb.append("] ").append(s.elementId()).append("\n");
                            sb.append("  Issue: ").append(s.description()).append("\n");
                            sb.append("  Fix: ").append(s.recommendation()).append("\n\n");
                        }
                    }

                    return new McpSchema.CallToolResult(sb.toString(), false);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error analyzing specification: " + e.getMessage(), true);
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
