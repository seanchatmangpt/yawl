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

package org.yawlfoundation.yawl.dspy.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.persistence.DspyProgramNotFoundException;
import org.yawlfoundation.yawl.dspy.persistence.DspyProgramRegistry;
import org.yawlfoundation.yawl.dspy.persistence.DspySavedProgram;

/**
 * MCP tools for DSPy program execution and management.
 *
 * <p>Exposes saved DSPy programs as MCP tools for LLM clients. Programs include:</p>
 * <ul>
 *   <li>{@code dspy_execute_program} - Execute a saved program with inputs</li>
 *   <li>{@code dspy_list_programs} - List all available programs</li>
 *   <li>{@code dspy_get_program_info} - Get detailed program information</li>
 *   <li>{@code dspy_reload_program} - Hot-reload a program from disk</li>
 * </ul>
 *
 * <h2>Tool: dspy_execute_program</h2>
 * <pre>{@code
 * // MCP request
 * {
 *   "tool": "dspy_execute_program",
 *   "arguments": {
 *     "program": "worklet_selector",
 *     "inputs": {
 *       "context": "Task: Review, Case: {urgency: high}"
 *     }
 *   }
 * }
 *
 * // Response
 * {
 *   "worklet_id": "FastTrack",
 *   "rationale": "High urgency case...",
 *   "confidence": 0.92
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspyMcpTools {

    private static final Logger log = LoggerFactory.getLogger(DspyMcpTools.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DspyMcpTools() {
        throw new UnsupportedOperationException(
            "DspyMcpTools is a static factory class. Use createAll() to get tool specifications.");
    }

    /**
     * Creates all DSPy MCP tool specifications.
     *
     * @param registry the DSPy program registry
     * @return list of MCP tool specifications
     * @throws NullPointerException if registry is null
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(DspyProgramRegistry registry) {
        Objects.requireNonNull(registry, "DspyProgramRegistry must not be null");

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        tools.add(createExecuteTool(registry));
        tools.add(createListTool(registry));
        tools.add(createInfoTool(registry));
        tools.add(createReloadTool(registry));

        log.info("Created {} DSPy MCP tools", tools.size());
        return tools;
    }

    /**
     * Creates the dspy_execute_program tool.
     */
    private static McpServerFeatures.SyncToolSpecification createExecuteTool(DspyProgramRegistry registry) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("program", Map.of(
            "type", "string",
            "description", "Program name: worklet_selector, resource_router, anomaly_forensics, or runtime_adaptation"));
        props.put("inputs", Map.of(
            "type", "object",
            "description", "Program-specific inputs matching the DSPy signature"));
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("program", "inputs"), false, null, Map.of());

        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("dspy_execute_program")
                .description("Execute a saved DSPy program with inputs. Programs are GEPA-optimized " +
                        "ML modules for workflow decisions. Available programs: worklet_selector, " +
                        "resource_router, anomaly_forensics, runtime_adaptation.")
                .inputSchema(schema)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String programName = (String) args.get("program");
            @SuppressWarnings("unchecked")
            Map<String, Object> inputs = (Map<String, Object>) args.get("inputs");

            log.debug("Executing DSPy program via MCP: {} with {} inputs", programName,
                    inputs != null ? inputs.size() : 0);

            if (programName == null || programName.isBlank()) {
                return createErrorResult("Parameter 'program' is required");
            }
            if (inputs == null || inputs.isEmpty()) {
                return createErrorResult("Parameter 'inputs' is required and must not be empty");
            }

            try {
                var result = registry.execute(programName, inputs);

                // Build response with output and metrics
                Map<String, Object> responseData = new LinkedHashMap<>();
                responseData.putAll(result.output());
                responseData.put("_metadata", Map.of(
                        "execution_time_ms", result.metrics().executionTimeMs(),
                        "total_time_ms", result.metrics().totalTimeMs(),
                        "cache_hit", result.metrics().cacheHit(),
                        "program", programName
                ));

                String json = MAPPER.writeValueAsString(responseData);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(json)),
                        false, null, null
                );

            } catch (DspyProgramNotFoundException e) {
                log.warn("DSPy program not found: {}", programName);
                return createErrorResult("Program not found: " + programName +
                        ". Available programs: " + registry.listProgramNames());
            } catch (Exception e) {
                log.error("DSPy execution failed: {}", e.getMessage(), e);
                return createErrorResult("Execution failed: " + e.getMessage());
            }
        });
    }

    /**
     * Creates the dspy_list_programs tool.
     */
    private static McpServerFeatures.SyncToolSpecification createListTool(DspyProgramRegistry registry) {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of(), List.of(), false, null, Map.of());
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("dspy_list_programs")
                .description("List all available DSPy programs with their metadata. " +
                        "Returns program names, versions, predictor counts, and optimization info.")
                .inputSchema(schema)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            log.debug("Listing DSPy programs via MCP");

            List<Map<String, Object>> programs = new ArrayList<>();
            for (String name : registry.listProgramNames()) {
                registry.load(name).ifPresent(program -> {
                    Map<String, Object> programInfo = new LinkedHashMap<>();
                    programInfo.put("name", program.name());
                    programInfo.put("version", program.version());
                    programInfo.put("predictor_count", program.predictorCount());
                    programInfo.put("optimizer", program.optimizerType());
                    programInfo.put("validation_score", program.validationScore());
                    programInfo.put("serialized_at", program.serializedAt());
                    programs.add(programInfo);
                });
            }

            Map<String, Object> responseData = Map.of(
                    "programs", programs,
                    "total_count", programs.size()
            );

            try {
                String json = MAPPER.writeValueAsString(responseData);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(json)),
                        false, null, null
                );
            } catch (Exception e) {
                log.error("Failed to serialize program list: {}", e.getMessage());
                return createErrorResult("Serialization failed: " + e.getMessage());
            }
        });
    }

    /**
     * Creates the dspy_get_program_info tool.
     */
    private static McpServerFeatures.SyncToolSpecification createInfoTool(DspyProgramRegistry registry) {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of(
                        "program", Map.of(
                                "type", "string",
                                "description", "Program name to get info for"
                        )
                ), List.of("program"), false, null, Map.of());

        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("dspy_get_program_info")
                .description("Get detailed information about a specific DSPy program, including " +
                        "signature definitions, predictor configurations, and optimization metadata.")
                .inputSchema(schema)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String programName = (String) args.get("program");

            log.debug("Getting DSPy program info via MCP: {}", programName);

            if (programName == null || programName.isBlank()) {
                return createErrorResult("Parameter 'program' is required");
            }

            return registry.load(programName)
                    .map(program -> {
                        Map<String, Object> responseData = new LinkedHashMap<>();
                        responseData.put("name", program.name());
                        responseData.put("version", program.version());
                        responseData.put("dspy_version", program.dspyVersion());
                        responseData.put("source_hash", program.sourceHash());
                        responseData.put("optimizer", program.optimizerType());
                        responseData.put("validation_score", program.validationScore());
                        responseData.put("serialized_at", program.serializedAt());
                        responseData.put("loaded_at", program.loadedAt().toString());

                        // Predictor details
                        List<Map<String, Object>> predictorList = new ArrayList<>();
                        program.predictors().forEach((name, config) -> {
                            Map<String, Object> predInfo = new LinkedHashMap<>();
                            predInfo.put("name", name);
                            predInfo.put("input_fields", config.getInputFieldNames());
                            predInfo.put("output_fields", config.getOutputFieldNames());
                            predInfo.put("few_shot_example_count", config.fewShotExampleCount());
                            predictorList.add(predInfo);
                        });
                        responseData.put("predictors", predictorList);

                        try {
                            String json = MAPPER.writeValueAsString(responseData);
                            return new McpSchema.CallToolResult(
                                    List.of(new McpSchema.TextContent(json)),
                                    false, null, null
                            );
                        } catch (Exception e) {
                            log.error("Failed to serialize program info: {}", e.getMessage());
                            return createErrorResult("Serialization failed: " + e.getMessage());
                        }
                    })
                    .orElseGet(() -> createErrorResult("Program not found: " + programName));
        });
    }

    /**
     * Creates the dspy_reload_program tool.
     */
    private static McpServerFeatures.SyncToolSpecification createReloadTool(DspyProgramRegistry registry) {
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
                "object", Map.of(
                        "program", Map.of(
                                "type", "string",
                                "description", "Program name to reload"
                        )
                ), List.of("program"), false, null, Map.of());

        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("dspy_reload_program")
                .description("Hot-reload a DSPy program from disk after external optimization. " +
                        "Use this after running GEPA training to pick up new program state.")
                .inputSchema(schema)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String programName = (String) args.get("program");

            log.info("Reloading DSPy program via MCP: {}", programName);

            if (programName == null || programName.isBlank()) {
                return createErrorResult("Parameter 'program' is required");
            }

            try {
                DspySavedProgram program = registry.reload(programName);

                Map<String, Object> responseData = Map.of(
                        "status", "reloaded",
                        "name", program.name(),
                        "source_hash", program.sourceHash(),
                        "predictor_count", program.predictorCount(),
                        "loaded_at", program.loadedAt().toString()
                );

                String json = MAPPER.writeValueAsString(responseData);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(json)),
                        false, null, null
                );

            } catch (DspyProgramNotFoundException e) {
                log.warn("DSPy program not found for reload: {}", programName);
                return createErrorResult("Program not found: " + programName);
            } catch (Exception e) {
                log.error("Failed to reload DSPy program: {}", e.getMessage(), e);
                return createErrorResult("Reload failed: " + e.getMessage());
            }
        });
    }

    /**
     * Creates an error result for MCP tool responses.
     */
    private static McpSchema.CallToolResult createErrorResult(String errorMessage) {
        Map<String, Object> errorData = Map.of(
                "error", true,
                "message", errorMessage
        );
        try {
            String json = MAPPER.writeValueAsString(errorData);
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(json)),
                    true, null, null  // isError = true
            );
        } catch (Exception e) {
            // Fallback to plain text if JSON serialization fails
            return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("{\"error\": true, \"message\": \"" +
                            errorMessage.replace("\"", "\\\"") + "\"}")),
                    true, null, null
            );
        }
    }
}
