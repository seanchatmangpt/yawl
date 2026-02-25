/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlProcessMiningToolSpecifications;

/**
 * Unit tests for YAWL Process Mining MCP Tool Specifications.
 *
 * <p>Chicago TDD approach: Tests the process mining tool factory without requiring
 * a live YAWL engine. Tools are tested for:
 * <ul>
 *   <li>Correct count and naming convention (yawl_pm_*)</li>
 *   <li>Proper input schema validation</li>
 *   <li>Graceful error handling when engine is unreachable</li>
 *   <li>Descriptive text for each tool</li>
 * </ul>
 * </p>
 *
 * <p>Strategy: For each tool, we instantiate the spec factory with fake/unreachable
 * engine URLs (localhost:9999) and verify:
 * <ol>
 *   <li>Tool metadata (name, description, inputSchema)</li>
 *   <li>Missing required parameters return error CallToolResult</li>
 *   <li>Unreachable engine returns error CallToolResult (not thrown exception)</li>
 * </ol>
 * </p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class YawlProcessMiningMcpToolsTest {

    private static final String UNREACHABLE_ENGINE_URL = "http://localhost:9999";
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "password";

    // =========================================================================
    // Tool Creation Tests
    // =========================================================================

    @Test
    public void testCreateAllReturns5Tools() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();

        assertNotNull(tools, "createAll() should return non-null list");
        assertEquals(5, tools.size(), "Should return exactly 5 process mining tools");
    }

    @Test
    public void testAllToolsHaveUniqueNames() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();

        HashSet<String> names = new HashSet<>();
        for (McpServerFeatures.SyncToolSpecification tool : tools) {
            String name = tool.tool().name();
            assertFalse(names.contains(name), "Tool name should be unique: " + name);
            names.add(name);
        }
        assertEquals(5, names.size(), "All tool names should be unique");
    }

    @Test
    public void testToolNamesMatchConvention() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();

        for (McpServerFeatures.SyncToolSpecification tool : tools) {
            String name = tool.tool().name();
            assertTrue(name.startsWith("yawl_pm_"), "Tool name should start with 'yawl_pm_': " + name);
        }
    }

    @Test
    public void testAllToolsHaveDescriptions() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();

        for (McpServerFeatures.SyncToolSpecification tool : tools) {
            String description = tool.tool().description();
            assertNotNull(description, "Tool should have a description: " + tool.tool().name());
            assertFalse(description.trim().isEmpty(), "Tool description should not be empty: " + tool.tool().name());
        }
    }

    @Test
    public void testAllToolsHaveInputSchemas() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();

        for (McpServerFeatures.SyncToolSpecification tool : tools) {
            McpSchema.JsonSchema schema = tool.tool().inputSchema();
            assertNotNull(schema, "Tool should have inputSchema: " + tool.tool().name());
        }
    }

    // =========================================================================
    // Individual Tool Name Tests
    // =========================================================================

    @Test
    public void testExportXesToolName() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification exportXesTool = findToolByName(tools, "yawl_pm_export_xes");

        assertNotNull(exportXesTool, "Should have yawl_pm_export_xes tool");
        assertEquals("yawl_pm_export_xes", exportXesTool.tool().name(),
            "Tool name should be yawl_pm_export_xes");
    }

    @Test
    public void testAnalyzeToolName() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification analyzeTool = findToolByName(tools, "yawl_pm_analyze");

        assertNotNull(analyzeTool, "Should have yawl_pm_analyze tool");
        assertEquals("yawl_pm_analyze", analyzeTool.tool().name(), "Tool name should be yawl_pm_analyze");
    }

    @Test
    public void testPerformanceToolName() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification perfTool = findToolByName(tools, "yawl_pm_performance");

        assertNotNull(perfTool, "Should have yawl_pm_performance tool");
        assertEquals("yawl_pm_performance", perfTool.tool().name(), "Tool name should be yawl_pm_performance");
    }

    @Test
    public void testVariantsToolName() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification variantsTool = findToolByName(tools, "yawl_pm_variants");

        assertNotNull(variantsTool, "Should have yawl_pm_variants tool");
        assertEquals("yawl_pm_variants", variantsTool.tool().name(), "Tool name should be yawl_pm_variants");
    }

    @Test
    public void testSocialNetworkToolName() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification socialTool = findToolByName(tools, "yawl_pm_social_network");

        assertNotNull(socialTool, "Should have yawl_pm_social_network tool");
        assertEquals("yawl_pm_social_network", socialTool.tool().name(),
            "Tool name should be yawl_pm_social_network");
    }

    // =========================================================================
    // Tool Input Validation Tests (Missing Required Parameters)
    // =========================================================================

    @Test
    public void testExportXesTool_MissingSpecIdentifier_ReturnsError() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification exportXesTool = findToolByName(tools, "yawl_pm_export_xes");

        // Call with empty args (missing specIdentifier)
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolResult result = exportXesTool.callHandler().apply(null, new McpSchema.CallToolRequest(exportXesTool.tool().name(), args));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError(), "Should be error result (isError=true) for missing specIdentifier");
    }

    @Test
    public void testAnalyzeTool_MissingSpecIdentifier_ReturnsError() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification analyzeTool = findToolByName(tools, "yawl_pm_analyze");

        // Call with empty args (missing specIdentifier)
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolResult result = analyzeTool.callHandler().apply(null, new McpSchema.CallToolRequest(analyzeTool.tool().name(), args));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError(), "Should be error result (isError=true) for missing specIdentifier");
    }

    @Test
    public void testPerformanceTool_MissingSpecIdentifier_ReturnsError() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification perfTool = findToolByName(tools, "yawl_pm_performance");

        // Call with empty args (missing specIdentifier)
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolResult result = perfTool.callHandler().apply(null, new McpSchema.CallToolRequest(perfTool.tool().name(), args));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError(), "Should be error result (isError=true) for missing specIdentifier");
    }

    @Test
    public void testVariantsTool_MissingSpecIdentifier_ReturnsError() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification variantsTool = findToolByName(tools, "yawl_pm_variants");

        // Call with empty args (missing specIdentifier)
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolResult result = variantsTool.callHandler().apply(null, new McpSchema.CallToolRequest(variantsTool.tool().name(), args));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError(), "Should be error result (isError=true) for missing specIdentifier");
    }

    @Test
    public void testSocialNetworkTool_MissingSpecIdentifier_ReturnsError() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification socialTool = findToolByName(tools, "yawl_pm_social_network");

        // Call with empty args (missing specIdentifier)
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolResult result = socialTool.callHandler().apply(null, new McpSchema.CallToolRequest(socialTool.tool().name(), args));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError(), "Should be error result (isError=true) for missing specIdentifier");
    }

    // =========================================================================
    // Tool Unreachable Engine Tests
    // =========================================================================

    @Test
    public void testExportXesTool_UnreachableEngine_ReturnsError() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification exportXesTool = findToolByName(tools, "yawl_pm_export_xes");

        // Call with valid spec but unreachable engine
        Map<String, Object> args = new HashMap<>();
        args.put("specIdentifier", "test.specification");

        McpSchema.CallToolResult result = exportXesTool.callHandler().apply(null, new McpSchema.CallToolRequest(exportXesTool.tool().name(), args));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError(), "Should be error result (isError=true) for unreachable engine");
    }

    @Test
    public void testAnalyzeTool_UnreachableEngine_ReturnsError() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification analyzeTool = findToolByName(tools, "yawl_pm_analyze");

        // Call with valid spec but unreachable engine
        Map<String, Object> args = new HashMap<>();
        args.put("specIdentifier", "test.specification");

        McpSchema.CallToolResult result = analyzeTool.callHandler().apply(null, new McpSchema.CallToolRequest(analyzeTool.tool().name(), args));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError(), "Should be error result (isError=true) for unreachable engine");
    }

    @Test
    public void testPerformanceTool_UnreachableEngine_ReturnsError() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification perfTool = findToolByName(tools, "yawl_pm_performance");

        // Call with valid spec but unreachable engine
        Map<String, Object> args = new HashMap<>();
        args.put("specIdentifier", "test.specification");

        McpSchema.CallToolResult result = perfTool.callHandler().apply(null, new McpSchema.CallToolRequest(perfTool.tool().name(), args));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError(), "Should be error result (isError=true) for unreachable engine");
    }

    @Test
    public void testVariantsTool_UnreachableEngine_ReturnsError() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification variantsTool = findToolByName(tools, "yawl_pm_variants");

        // Call with valid spec but unreachable engine
        Map<String, Object> args = new HashMap<>();
        args.put("specIdentifier", "test.specification");

        McpSchema.CallToolResult result = variantsTool.callHandler().apply(null, new McpSchema.CallToolRequest(variantsTool.tool().name(), args));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError(), "Should be error result (isError=true) for unreachable engine");
    }

    @Test
    public void testSocialNetworkTool_UnreachableEngine_ReturnsError() {
        YawlProcessMiningToolSpecifications specs = new YawlProcessMiningToolSpecifications(
            UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);

        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        McpServerFeatures.SyncToolSpecification socialTool = findToolByName(tools, "yawl_pm_social_network");

        // Call with valid spec but unreachable engine
        Map<String, Object> args = new HashMap<>();
        args.put("specIdentifier", "test.specification");

        McpSchema.CallToolResult result = socialTool.callHandler().apply(null, new McpSchema.CallToolRequest(socialTool.tool().name(), args));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isError(), "Should be error result (isError=true) for unreachable engine");
    }

    // =========================================================================
    // Constructor Validation Tests
    // =========================================================================

    @Test
    public void testConstructorRejectsNullEngineUrl() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlProcessMiningToolSpecifications(null, TEST_USERNAME, TEST_PASSWORD));
    }

    @Test
    public void testConstructorRejectsEmptyEngineUrl() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlProcessMiningToolSpecifications("", TEST_USERNAME, TEST_PASSWORD));
    }

    @Test
    public void testConstructorRejectsNullUsername() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlProcessMiningToolSpecifications(UNREACHABLE_ENGINE_URL, null, TEST_PASSWORD));
    }

    @Test
    public void testConstructorRejectsEmptyUsername() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlProcessMiningToolSpecifications(UNREACHABLE_ENGINE_URL, "", TEST_PASSWORD));
    }

    @Test
    public void testConstructorRejectsNullPassword() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlProcessMiningToolSpecifications(UNREACHABLE_ENGINE_URL, TEST_USERNAME, null));
    }

    @Test
    public void testConstructorRejectsEmptyPassword() {
        assertThrows(IllegalArgumentException.class,
            () -> new YawlProcessMiningToolSpecifications(UNREACHABLE_ENGINE_URL, TEST_USERNAME, ""));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Find a tool in the list by name.
     *
     * @param tools list of tool specifications
     * @param name tool name to search for
     * @return the tool specification, or null if not found
     */
    private McpServerFeatures.SyncToolSpecification findToolByName(
            List<McpServerFeatures.SyncToolSpecification> tools,
            String name) {
        for (McpServerFeatures.SyncToolSpecification tool : tools) {
            if (tool.tool().name().equals(name)) {
                return tool;
            }
        }
        return null;
    }
}
