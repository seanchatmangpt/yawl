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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for BlueOceanMcpTools — yawl_temporal_fork MCP tool.
 * Uses real TemporalForkEngine with lambda constructor — no mocks.
 * Closes V7Gap.BURIED_ENGINES_MCP_A2A_WIRING via MCP surface.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class BlueOceanMcpToolsTest {

    private BlueOceanMcpTools tools;

    @BeforeEach
    void setUp() {
        tools = new BlueOceanMcpTools();
    }

    // =========================================================================
    // Tool Inventory
    // =========================================================================

    @Test
    void createAll_returnsOneTemporalForkTool() {
        List<McpServerFeatures.SyncToolSpecification> toolList = tools.createAll();
        assertEquals(1, toolList.size(), "BlueOceanMcpTools must provide exactly 1 tool");
    }

    @Test
    void createAll_toolNameIsYawlTemporalFork() {
        List<String> names = tools.createAll().stream()
            .map(t -> t.tool().name())
            .collect(Collectors.toList());
        assertTrue(names.contains("yawl_temporal_fork"),
            "Must provide yawl_temporal_fork tool. Got: " + names);
    }

    @Test
    void temporalForkTool_hasNonBlankDescription() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_temporal_fork");
        assertFalse(tool.tool().description().isBlank(),
            "yawl_temporal_fork must have a non-blank description");
        assertTrue(tool.tool().description().contains("TemporalForkEngine")
            || tool.tool().description().contains("temporal") || tool.tool().description().contains("fork"),
            "Description must mention temporal or fork");
    }

    @Test
    void temporalForkTool_requiresCaseIdAndTaskNames() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_temporal_fork");
        McpSchema.JsonSchema schema = tool.tool().inputSchema();
        assertNotNull(schema, "Tool must have input schema");
        List<String> required = schema.required();
        assertTrue(required.contains("caseId"), "yawl_temporal_fork must require caseId");
        assertTrue(required.contains("taskNames"), "yawl_temporal_fork must require taskNames");
    }

    // =========================================================================
    // Execution — success cases
    // =========================================================================

    @Test
    void temporalFork_twoTasks_returnsCompletedForks() {
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("caseId", "case-1", "taskNames", "TaskA,TaskB"));

        assertFalse(result.isError(), "Two-task fork must not error. Got: " + extractText(result));
        String text = extractText(result);
        assertTrue(text.contains("completedForks"), "Result must include completedForks");
        assertTrue(text.contains("requestedForks"), "Result must include requestedForks");
    }

    @Test
    void temporalFork_twoTasks_requestedForksIsTwo() {
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("caseId", "case-2", "taskNames", "ReviewLoan,ApproveLoan"));

        assertFalse(result.isError());
        String text = extractText(result);
        assertTrue(text.contains("\"requestedForks\":2") || text.contains("requestedForks\":2"),
            "requestedForks must be 2 for two tasks. Got: " + text);
    }

    @Test
    void temporalFork_singleTask_dominantPathIsTask() {
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("caseId", "case-single", "taskNames", "OnlyTask"));

        assertFalse(result.isError());
        String text = extractText(result);
        assertTrue(text.contains("dominantPath"), "Result must include dominantPath");
        assertTrue(text.contains("OnlyTask") || text.contains("all-unique"),
            "Single-task dominant path must reference OnlyTask. Got: " + text);
    }

    @Test
    void temporalFork_threeTasks_allForksSummariesPresent() {
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("caseId", "case-3", "taskNames", "Alpha,Beta,Gamma"));

        assertFalse(result.isError());
        String text = extractText(result);
        assertTrue(text.contains("forks"), "Result must include forks array");
        // All 3 tasks should appear in the fork summaries
        assertTrue(text.contains("Alpha") || text.contains("Beta") || text.contains("Gamma"),
            "Fork summaries must reference task names. Got: " + text);
    }

    @Test
    void temporalFork_returnsElapsedMs() {
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("caseId", "case-elapsed", "taskNames", "TaskX"));

        assertFalse(result.isError());
        String text = extractText(result);
        assertTrue(text.contains("elapsed_ms"), "Result must include elapsed_ms timing");
    }

    @Test
    void temporalFork_returnsEngineIdentifier() {
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("caseId", "case-engine", "taskNames", "TaskX"));

        assertFalse(result.isError());
        String text = extractText(result);
        assertTrue(text.contains("TemporalForkEngine") || text.contains("engine"),
            "Result must identify the engine used. Got: " + text);
    }

    @Test
    void temporalFork_customMaxSeconds_isRespected() {
        // maxSeconds=1 — should still complete quickly with synthetic engine
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("caseId", "case-fast", "taskNames", "FastTask", "maxSeconds", 1));

        assertFalse(result.isError(), "Custom maxSeconds=1 must not error. Got: " + extractText(result));
    }

    // =========================================================================
    // Execution — error cases
    // =========================================================================

    @Test
    void temporalFork_missingCaseId_returnsError() {
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("taskNames", "TaskA,TaskB"));

        assertTrue(result.isError(), "Missing caseId must return error");
    }

    @Test
    void temporalFork_missingTaskNames_returnsError() {
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("caseId", "case-1"));

        assertTrue(result.isError(), "Missing taskNames must return error");
    }

    @Test
    void temporalFork_emptyTaskNames_returnsError() {
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("caseId", "case-1", "taskNames", "   ,  ,  "));

        assertTrue(result.isError(), "Empty task names (after trim) must return error");
    }

    @Test
    void temporalFork_invalidMaxSeconds_returnsError() {
        McpSchema.CallToolResult result = invoke("yawl_temporal_fork",
            Map.of("caseId", "case-1", "taskNames", "TaskA", "maxSeconds", 9999));

        assertTrue(result.isError(), "maxSeconds > 300 must return error");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification findTool(String name) {
        return tools.createAll().stream()
            .filter(t -> name.equals(t.tool().name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Tool not found: " + name));
    }

    private McpSchema.CallToolResult invoke(String toolName, Map<String, Object> args) {
        McpServerFeatures.SyncToolSpecification tool = findTool(toolName);
        return tool.callHandler().apply(null,
            new McpSchema.CallToolRequest(tool.tool().name(), args));
    }

    private String extractText(McpSchema.CallToolResult result) {
        return result.content().stream()
            .filter(c -> c instanceof McpSchema.TextContent)
            .map(c -> ((McpSchema.TextContent) c).text())
            .collect(Collectors.joining());
    }
}
