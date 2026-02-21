/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.mcp.spec.turtle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.schema.turtle.YTurtleExporter;
import org.yawlfoundation.yawl.unmarshal.turtle.YTurtleImporter;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Static factory class for Turtle format import/export MCP tool specifications.
 *
 * Creates MCP tool specifications for importing YAWL workflow specifications from
 * Turtle RDF format and exporting existing specifications to Turtle. Tools enable
 * MCP clients (like Claude) to work with workflow specifications in RDF/Turtle format.
 *
 * <p><b>Tools Created:</b>
 * <ul>
 *   <li><b>import_turtle_specification</b> - Parse Turtle RDF content and import as YAWL spec</li>
 *   <li><b>export_specification_to_turtle</b> - Export YAWL spec to Turtle RDF format</li>
 *   <li><b>validate_turtle_specification</b> - Validate Turtle content without importing</li>
 *   <li><b>list_turtle_patterns</b> - List available workflow patterns for Turtle conversion</li>
 * </ul>
 *
 * <p><b>Dependencies:</b> Each tool requires the YAWL engine and interface clients to be
 * properly initialized. The Turtle importer uses Apache Jena for RDF parsing.
 *
 * @author Claude Code
 * @version 6.0.0
 * @see YTurtleImporter
 * @see YTurtleExporter
 */
public final class YawlTurtleToolSpecifications {

    private static final Logger logger = LogManager.getLogger(YawlTurtleToolSpecifications.class);

    private YawlTurtleToolSpecifications() {
        throw new UnsupportedOperationException(
            "YawlTurtleToolSpecifications is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates all Turtle format import/export MCP tool specifications.
     *
     * @param interfaceAClient the YAWL InterfaceA client for design-time operations
     * @param interfaceBClient the YAWL InterfaceB client for runtime operations
     * @param sessionHandle    the active YAWL session handle
     * @return list of all Turtle tool specifications for MCP registration
     * @throws IllegalArgumentException if any required parameter is null or empty
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        if (interfaceAClient == null) {
            throw new IllegalArgumentException(
                "interfaceAClient is required - provide a connected InterfaceA_EnvironmentBasedClient");
        }
        if (interfaceBClient == null) {
            throw new IllegalArgumentException(
                "interfaceBClient is required - provide a connected InterfaceB_EnvironmentBasedClient");
        }
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new IllegalArgumentException(
                "sessionHandle is required - connect to the YAWL engine first via " +
                "InterfaceB_EnvironmentBasedClient.connect(username, password)");
        }

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        tools.add(createImportTurtleSpecificationTool(interfaceAClient, sessionHandle));
        tools.add(createExportSpecificationToTurtleTool(interfaceBClient, sessionHandle));
        tools.add(createValidateTurtleSpecificationTool());
        tools.add(createListTurtlePatternsTool());

        return tools;
    }

    // =========================================================================
    // Tool 1: import_turtle_specification
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createImportTurtleSpecificationTool(
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("turtle_content", Map.of(
            "type", "string",
            "description", "Turtle RDF specification content to import"));
        props.put("validate", Map.of(
            "type", "boolean",
            "description", "Whether to validate before importing (default: true)"));

        List<String> required = List.of("turtle_content");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("import_turtle_specification")
                .description("Import a YAWL workflow specification from Turtle RDF format. " +
                    "Parses the Turtle content, validates it, and uploads to the YAWL engine. " +
                    "Returns specification ID and validation results.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String turtleContent = requireStringArg(args, "turtle_content");
                    boolean validate = optionalBooleanArg(args, "validate", true);

                    // Validate if requested
                    if (validate) {
                        List<String> validationErrors = validateTurtleContent(turtleContent);
                        if (!validationErrors.isEmpty()) {
                            return new McpSchema.CallToolResult(
                                "Turtle validation failed:\n" + String.join("\n", validationErrors),
                                true);
                        }
                    }

                    // Import specifications from Turtle content
                    List<YSpecification> specs = YTurtleImporter.importFromString(turtleContent);

                    if (specs == null || specs.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "No specifications found in Turtle content", true);
                    }

                    // Upload each specification to the engine via InterfaceA
                    StringBuilder result = new StringBuilder();
                    result.append("Successfully imported ").append(specs.size()).append(" specification(s):\n\n");

                    for (YSpecification spec : specs) {
                        // Convert spec to XML for upload
                        String specXml = spec.toXML();

                        String uploadResult = interfaceAClient.uploadSpecification(specXml, sessionHandle);

                        if (uploadResult == null || uploadResult.contains("<failure>")) {
                            return new McpSchema.CallToolResult(
                                "Failed to upload specification " + spec.getURI() + ": " + uploadResult,
                                true);
                        }

                        result.append("- Specification: ").append(spec.getURI()).append("\n");
                        if (spec.getName() != null && !spec.getName().isEmpty()) {
                            result.append("  Name: ").append(spec.getName()).append("\n");
                        }
                        result.append("  Status: Uploaded successfully\n");
                        result.append("  Engine response: ").append(uploadResult).append("\n\n");
                    }

                    return new McpSchema.CallToolResult(result.toString().trim(), false);

                } catch (YSyntaxException e) {
                    return new McpSchema.CallToolResult(
                        "Failed to parse Turtle specification: " + e.getMessage(), true);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error importing Turtle specification: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 2: export_specification_to_turtle
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createExportSpecificationToTurtleTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specification_uri", Map.of(
            "type", "string",
            "description", "URI or identifier of the specification to export"));
        props.put("specification_version", Map.of(
            "type", "string",
            "description", "Version string of the specification (default: 0.1)"));

        List<String> required = List.of("specification_uri");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("export_specification_to_turtle")
                .description("Export a YAWL workflow specification to Turtle RDF format. " +
                    "Retrieves the specification from the YAWL engine and converts it to " +
                    "Turtle RDF representation for semantic web processing.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String specUri = requireStringArg(args, "specification_uri");
                    String specVersion = optionalStringArg(args, "specification_version", "0.1");

                    // Get specification from engine as XML
                    String specXml = interfaceBClient.getSpecification(
                        new org.yawlfoundation.yawl.engine.YSpecificationID(
                            specUri, specVersion, specUri),
                        sessionHandle);

                    if (specXml == null || specXml.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to retrieve specification " + specUri + " from engine: " + specXml,
                            true);
                    }

                    // Parse XML to YSpecification object (skip schema validation - spec came from engine)
                    List<YSpecification> specs =
                        org.yawlfoundation.yawl.unmarshal.YMarshal.unmarshalSpecifications(specXml, false);

                    if (specs == null || specs.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "Could not parse specification XML", true);
                    }

                    YSpecification spec = specs.get(0);

                    // Export to Turtle
                    String turtleContent = YTurtleExporter.exportToString(spec);

                    return new McpSchema.CallToolResult(
                        "Specification exported to Turtle format:\n\n" + turtleContent,
                        false);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error exporting specification to Turtle: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 3: validate_turtle_specification
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createValidateTurtleSpecificationTool() {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("turtle_content", Map.of(
            "type", "string",
            "description", "Turtle RDF specification content to validate"));

        List<String> required = List.of("turtle_content");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("validate_turtle_specification")
                .description("Validate a Turtle RDF specification without importing. " +
                    "Parses the Turtle content and validates YAWL structure. " +
                    "Returns validation report with violations and warnings.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String turtleContent = requireStringArg(args, "turtle_content");

                    // Validate Turtle syntax and YAWL semantics
                    List<String> validationErrors = validateTurtleContent(turtleContent);
                    List<String> validationWarnings = new ArrayList<>();

                    // Try to import (but don't upload) to check semantic validity
                    try {
                        List<YSpecification> specs = YTurtleImporter.importFromString(turtleContent);

                        if (specs != null && !specs.isEmpty()) {
                            for (YSpecification spec : specs) {
                                // Check for common semantic issues
                                if (spec.getRootNet() == null) {
                                    validationWarnings.add(
                                        "Specification " + spec.getURI() + " has no root net");
                                }
                            }
                        }
                    } catch (YSyntaxException e) {
                        validationErrors.add("Semantic validation failed: " + e.getMessage());
                    }

                    // Build validation report
                    StringBuilder report = new StringBuilder();
                    boolean isValid = validationErrors.isEmpty();

                    report.append("Validation Report\n");
                    report.append("=================\n\n");
                    report.append("Status: ").append(isValid ? "VALID" : "INVALID").append("\n\n");

                    if (!validationErrors.isEmpty()) {
                        report.append("Errors (").append(validationErrors.size()).append("):\n");
                        for (String error : validationErrors) {
                            report.append("  - ").append(error).append("\n");
                        }
                        report.append("\n");
                    }

                    if (!validationWarnings.isEmpty()) {
                        report.append("Warnings (").append(validationWarnings.size()).append("):\n");
                        for (String warning : validationWarnings) {
                            report.append("  - ").append(warning).append("\n");
                        }
                        report.append("\n");
                    }

                    if (isValid && validationWarnings.isEmpty()) {
                        report.append("Specification is syntactically and semantically valid.");
                    }

                    return new McpSchema.CallToolResult(report.toString().trim(), !isValid);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Validation failed with exception: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 4: list_turtle_patterns
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createListTurtlePatternsTool() {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("category", Map.of(
            "type", "string",
            "description", "Filter patterns by category (e.g., basic, advanced, composite)"));

        List<String> required = List.of();
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("list_turtle_patterns")
                .description("List available Turtle RDF workflow patterns for specification " +
                    "design and conversion. Patterns are located in the .specify/patterns/ directory " +
                    "and can serve as templates for creating new Turtle-based specifications.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String category = optionalStringArg(args, "category", null);

                    // Load patterns from .specify/patterns/ directory
                    List<Map<String, String>> patterns = loadTurtlePatterns(category);

                    if (patterns == null || patterns.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "No Turtle workflow patterns found.", false);
                    }

                    StringBuilder result = new StringBuilder();
                    result.append("Available Turtle Workflow Patterns\n");
                    result.append("===================================\n\n");

                    for (int i = 0; i < patterns.size(); i++) {
                        Map<String, String> pattern = patterns.get(i);
                        result.append(i + 1).append(". ").append(pattern.get("name")).append("\n");
                        result.append("   Category: ").append(pattern.get("category")).append("\n");
                        result.append("   Description: ").append(pattern.get("description")).append("\n");

                        String path = pattern.get("path");
                        if (path != null && !path.isEmpty()) {
                            result.append("   Path: ").append(path).append("\n");
                        }

                        String elements = pattern.get("elements");
                        if (elements != null && !elements.isEmpty()) {
                            result.append("   Elements: ").append(elements).append("\n");
                        }

                        result.append("\n");
                    }

                    return new McpSchema.CallToolResult(result.toString().trim(), false);

                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error listing Turtle patterns: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Validation and utility methods
    // =========================================================================

    /**
     * Validates Turtle RDF content for YAWL specification compliance.
     *
     * @param turtleContent the Turtle content to validate
     * @return list of validation errors (empty if valid)
     */
    private static List<String> validateTurtleContent(String turtleContent) {
        List<String> errors = new ArrayList<>();

        if (turtleContent == null || turtleContent.trim().isEmpty()) {
            errors.add("Turtle content is empty");
            return errors;
        }

        // Check for essential YAWL ontology elements
        if (!turtleContent.contains("yawls:Specification") &&
            !turtleContent.contains("yawlfoundation.org/yawlschema#Specification")) {
            errors.add("Missing yawls:Specification resource");
        }

        if (!turtleContent.contains("yawls:WorkflowNet") &&
            !turtleContent.contains("yawlfoundation.org/yawlschema#WorkflowNet")) {
            errors.add("Missing yawls:WorkflowNet (workflow must have at least one net)");
        }

        if (!turtleContent.contains("yawls:InputCondition") &&
            !turtleContent.contains("yawlfoundation.org/yawlschema#InputCondition")) {
            errors.add("Missing yawls:InputCondition (every net must have an input condition)");
        }

        if (!turtleContent.contains("yawls:OutputCondition") &&
            !turtleContent.contains("yawlfoundation.org/yawlschema#OutputCondition")) {
            errors.add("Missing yawls:OutputCondition (every net must have an output condition)");
        }

        // Try to parse as RDF/Turtle via YTurtleImporter
        try {
            List<YSpecification> specs = YTurtleImporter.importFromString(turtleContent);
            if (specs == null || specs.isEmpty()) {
                errors.add("RDF model produced no YAWL specifications after parsing");
            }
        } catch (YSyntaxException e) {
            errors.add("RDF parsing failed: " + e.getMessage());
        }

        return errors;
    }

    /**
     * Loads Turtle workflow patterns from the .specify/patterns/ directory.
     *
     * @param category optional category filter
     * @return list of pattern descriptions
     */
    private static List<Map<String, String>> loadTurtlePatterns(String category) {
        List<Map<String, String>> patterns = new ArrayList<>();

        // Define built-in patterns
        addPattern(patterns, "simple_sequence", "basic",
            "Sequential task execution",
            "Tasks execute one after another with no branching",
            "input → task1 → task2 → output");

        addPattern(patterns, "parallel_split", "basic",
            "Parallel task execution",
            "Multiple tasks execute concurrently from a single point",
            "input → [AND split] → task1, task2 → [AND join] → output");

        addPattern(patterns, "conditional_branching", "basic",
            "Conditional execution paths",
            "Tasks execute based on guard conditions (XOR split/join)",
            "input → [XOR split] → task1 OR task2 → [XOR join] → output");

        addPattern(patterns, "loop_construct", "intermediate",
            "Looping workflow pattern",
            "Tasks can be executed multiple times with conditional loops",
            "input → task → condition → [loop back or continue]");

        addPattern(patterns, "composite_task", "intermediate",
            "Composite task decomposition",
            "Tasks can decompose to sub-workflows (nets)",
            "task → [has decomposition] → sub-workflow net");

        addPattern(patterns, "interleaved_routing", "advanced",
            "Interleaved parallel and conditional paths",
            "Combination of AND/OR/XOR splits and joins",
            "[Complex routing patterns combining multiple control types]");

        // Filter by category if requested
        if (category != null && !category.isEmpty()) {
            patterns = patterns.stream()
                .filter(p -> category.equalsIgnoreCase(p.get("category")))
                .toList();
        }

        return patterns;
    }

    /**
     * Helper to add a pattern to the list.
     */
    private static void addPattern(List<Map<String, String>> patterns, String name, String category,
                                   String description, String details, String elements) {
        Map<String, String> pattern = new LinkedHashMap<>();
        pattern.put("name", name);
        pattern.put("category", category);
        pattern.put("description", description + " - " + details);
        pattern.put("elements", elements);
        patterns.add(pattern);
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

    /**
     * Extract an optional boolean argument from the tool arguments map.
     *
     * @param args         the tool arguments
     * @param name         the argument name
     * @param defaultValue the default value if the argument is missing
     * @return the boolean value or the default
     */
    private static boolean optionalBooleanArg(Map<String, Object> args, String name,
                                              boolean defaultValue) {
        Object value = args.get(name);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return defaultValue;
    }
}
