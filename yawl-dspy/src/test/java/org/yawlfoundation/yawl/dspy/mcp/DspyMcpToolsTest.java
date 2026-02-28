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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.dspy.persistence.DspyProgramRegistry;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DspyMcpTools MCP tool specifications.
 *
 * <p>Chicago TDD: Tests verify real MCP tool behavior with actual JSON processing.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class DspyMcpToolsTest {

    @TempDir
    Path tempDir;

    private Path programsDir;
    private PythonExecutionEngine pythonEngine;
    private DspyProgramRegistry registry;
    private List<McpServerFeatures.SyncToolSpecification> tools;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        programsDir = tempDir.resolve("programs");
        Files.createDirectories(programsDir);

        pythonEngine = PythonExecutionEngine.builder()
                .contextPoolSize(1)
                .build();

        // Create test program
        createTestProgram("worklet_selector");

        registry = new DspyProgramRegistry(programsDir, pythonEngine);
        tools = DspyMcpTools.createAll(registry);
    }

    @Test
    @DisplayName("Should create 4 MCP tools")
    void shouldCreateFourMcpTools() {
        // Assert
        assertEquals(4, tools.size());

        List<String> toolNames = tools.stream()
                .map(spec -> spec.tool().name())
                .toList();

        assertTrue(toolNames.contains("dspy_execute_program"));
        assertTrue(toolNames.contains("dspy_list_programs"));
        assertTrue(toolNames.contains("dspy_get_program_info"));
        assertTrue(toolNames.contains("dspy_reload_program"));
    }

    @Test
    @DisplayName("Should throw on null registry")
    void shouldThrowOnNullRegistry() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                DspyMcpTools.createAll(null));
    }

    @Test
    @DisplayName("dspy_list_programs should return program list")
    void dspyListProgramsShouldReturnProgramList() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification listTool = findTool("dspy_list_programs");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "dspy_list_programs", Map.of());

        // Act
        McpSchema.CallToolResult result = listTool.call().apply(null, request);

        // Assert
        assertFalse(result.isError());
        assertEquals(1, result.content().size());

        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        Map<String, Object> response = mapper.readValue(json, Map.class);

        assertTrue(response.containsKey("programs"));
        assertTrue(response.containsKey("total_count"));
        assertEquals(1, response.get("total_count"));
    }

    @Test
    @DisplayName("dspy_get_program_info should return program details")
    void dspyGetProgramInfoShouldReturnProgramDetails() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification infoTool = findTool("dspy_get_program_info");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "dspy_get_program_info", Map.of("program", "worklet_selector"));

        // Act
        McpSchema.CallToolResult result = infoTool.call().apply(null, request);

        // Assert
        assertFalse(result.isError());

        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        Map<String, Object> response = mapper.readValue(json, Map.class);

        assertEquals("worklet_selector", response.get("name"));
        assertEquals("1.0.0", response.get("version"));
        assertTrue(response.containsKey("predictors"));
    }

    @Test
    @DisplayName("dspy_get_program_info should error on unknown program")
    void dspyGetProgramInfoShouldErrorOnUnknownProgram() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification infoTool = findTool("dspy_get_program_info");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "dspy_get_program_info", Map.of("program", "unknown_program"));

        // Act
        McpSchema.CallToolResult result = infoTool.call().apply(null, request);

        // Assert
        assertTrue(result.isError());

        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        Map<String, Object> response = mapper.readValue(json, Map.class);

        assertEquals(true, response.get("error"));
        assertTrue(response.get("message").toString().contains("not found"));
    }

    @Test
    @DisplayName("dspy_get_program_info should error on missing parameter")
    void dspyGetProgramInfoShouldErrorOnMissingParameter() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification infoTool = findTool("dspy_get_program_info");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "dspy_get_program_info", Map.of());

        // Act
        McpSchema.CallToolResult result = infoTool.call().apply(null, request);

        // Assert
        assertTrue(result.isError());
    }

    @Test
    @DisplayName("dspy_execute_program should error on missing program parameter")
    void dspyExecuteProgramShouldErrorOnMissingProgram() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification execTool = findTool("dspy_execute_program");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "dspy_execute_program", Map.of("inputs", Map.of("context", "test")));

        // Act
        McpSchema.CallToolResult result = execTool.call().apply(null, request);

        // Assert
        assertTrue(result.isError());

        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        Map<String, Object> response = mapper.readValue(json, Map.class);

        assertTrue(response.get("message").toString().contains("program"));
    }

    @Test
    @DisplayName("dspy_execute_program should error on missing inputs parameter")
    void dspyExecuteProgramShouldErrorOnMissingInputs() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification execTool = findTool("dspy_execute_program");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "dspy_execute_program", Map.of("program", "worklet_selector"));

        // Act
        McpSchema.CallToolResult result = execTool.call().apply(null, request);

        // Assert
        assertTrue(result.isError());

        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        Map<String, Object> response = mapper.readValue(json, Map.class);

        assertTrue(response.get("message").toString().contains("inputs"));
    }

    @Test
    @DisplayName("dspy_reload_program should reload program from disk")
    void dspyReloadProgramShouldReloadProgram() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification reloadTool = findTool("dspy_reload_program");

        // Update the program file
        String updatedJson = createProgramJson("worklet_selector", "updated_hash_value");
        Files.writeString(programsDir.resolve("worklet_selector.json"), updatedJson);

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "dspy_reload_program", Map.of("program", "worklet_selector"));

        // Act
        McpSchema.CallToolResult result = reloadTool.call().apply(null, request);

        // Assert
        assertFalse(result.isError());

        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        Map<String, Object> response = mapper.readValue(json, Map.class);

        assertEquals("reloaded", response.get("status"));
        assertEquals("updated_hash_value", response.get("source_hash"));
    }

    @Test
    @DisplayName("dspy_reload_program should error on unknown program")
    void dspyReloadProgramShouldErrorOnUnknownProgram() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification reloadTool = findTool("dspy_reload_program");
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "dspy_reload_program", Map.of("program", "non_existent"));

        // Act
        McpSchema.CallToolResult result = reloadTool.call().apply(null, request);

        // Assert
        assertTrue(result.isError());
    }

    @Test
    @DisplayName("Tools should have valid JSON schemas")
    void toolsShouldHaveValidJsonSchemas() {
        for (McpServerFeatures.SyncToolSpecification spec : tools) {
            McpSchema.Tool tool = spec.tool();

            assertNotNull(tool.name());
            assertFalse(tool.name().isBlank());

            assertNotNull(tool.description());
            assertFalse(tool.description().isBlank());

            assertNotNull(tool.inputSchema());
            assertTrue(tool.inputSchema().contains("type"));
        }
    }

    // Helper methods

    private McpServerFeatures.SyncToolSpecification findTool(String name) {
        return tools.stream()
                .filter(spec -> spec.tool().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + name));
    }

    private void createTestProgram(String name) throws IOException {
        String json = createProgramJson(name, "hash_" + name + "_123");
        Files.writeString(programsDir.resolve(name + ".json"), json);
    }

    private String createProgramJson(String name, String hash) {
        return String.format("""
                {
                  "name": "%s",
                  "version": "1.0.0",
                  "dspy_version": "2.5.0",
                  "source_hash": "%s",
                  "predictors": {
                    "classify": {
                      "signature": {
                        "instructions": "Test instructions for %s",
                        "input_fields": [{"name": "context"}],
                        "output_fields": [{"name": "result"}]
                      },
                      "demos": []
                    }
                  },
                  "metadata": {
                    "optimizer": "GEPA",
                    "val_score": 0.95
                  }
                }
                """, name, hash, name);
    }
}
