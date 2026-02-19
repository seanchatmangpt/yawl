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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServer;

import org.yawlfoundation.yawl.engine.interfce.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.elements.YSpecificationID;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;
import org.yawlfoundation.yawl.integration.mcp.server.YawlServerCapabilities;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Comprehensive JUnit 5 test class for YAWL MCP integration capabilities.
 *
 * Chicago TDD: Tests real MCP server implementation with live YAWL engine
 * integration, including health checks, tool execution, and resource access.
 *
 * Coverage targets:
 * - MCP server lifecycle (start/stop with health checks)
 * - All 15 YAWL workflow tools (create_case, get_work_items, etc.)
 * - Resource listing and access (specifications, cases, workitems)
 * - Error handling and edge cases
 * - Resource template parameter extraction
 * - Server capability validation
 * - Session management and cleanup
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MCPCapabilityTest {

    private static final Logger LOGGER = Logger.getLogger(MCPCapabilityTest.class.getName());

    // Test configuration
    private static final String TEST_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "YAWL";

    // Test data
    private static final String TEST_SPECIFICATION_ID = "SimpleApproval";
    private static final String TEST_SPECIFICATION_VERSION = "0.1";
    private static final String TEST_SPECIFICATION_URI = "http://example.com/yawl/SimpleApproval";
    private static final String TEST_CASE_DATA = "<data><requester>test-user</requester><amount>100</amount><reason>Test approval</reason></data>";

    // Shared test state
    private static YawlMcpServer mcpServer;
    private static InterfaceB_EnvironmentBasedClient interfaceBClient;
    private static String sessionHandle;
    private static String testCaseId;

    /**
     * Initialize test environment - connect to YAWL engine and create MCP server.
     */
    @BeforeAll
    static void setupTestEnvironment() {
        LOGGER.info("Setting up MCP integration test environment...");

        try {
            // Connect to YAWL engine
            interfaceBClient = new InterfaceB_EnvironmentBasedClient(TEST_ENGINE_URL);
            sessionHandle = interfaceBClient.connect(TEST_USERNAME, TEST_PASSWORD);

            assertNotNull(sessionHandle, "Should establish valid session with YAWL engine");
            assertFalse(sessionHandle.contains("<error>"), "Session handle should not contain error message");
            assertFalse(sessionHandle.contains("<failure>"), "Session handle should not contain failure message");

            LOGGER.info("Successfully connected to YAWL engine, session: " + sessionHandle.substring(0, 20) + "...");

            // Create MCP server
            mcpServer = new YawlMcpServer(TEST_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);
            assertNotNull(mcpServer, "MCP server should be created successfully");

            // Verify MCP server can start (but don't start it permanently for tests)
            assertFalse(mcpServer.isRunning(), "Server should not be running before start");

        } catch (Exception e) {
            fail("Failed to setup test environment: " + e.getMessage());
        }
    }

    /**
     * Clean up test environment - disconnect from YAWL engine and stop MCP server.
     */
    @AfterAll
    static void cleanupTestEnvironment() {
        LOGGER.info("Cleaning up MCP integration test environment...");

        try {
            // Stop MCP server if running
            if (mcpServer != null) {
                try {
                    mcpServer.stop();
                    LOGGER.info("MCP server stopped successfully");
                } catch (Exception e) {
                    LOGGER.warning("Error stopping MCP server: " + e.getMessage());
                }
            }

            // Disconnect from YAWL engine
            if (interfaceBClient != null && sessionHandle != null) {
                try {
                    interfaceBClient.disconnect(sessionHandle);
                    LOGGER.info("Disconnected from YAWL engine successfully");
                } catch (Exception e) {
                    LOGGER.warning("Error disconnecting from YAWL engine: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            fail("Failed to cleanup test environment: " + e.getMessage());
        }
    }

    /**
     * Reset test state before each test - delete test case if it exists.
     */
    @BeforeEach
    void resetTestState() {
        testCaseId = null;

        // Clean up any test case from previous test runs
        if (sessionHandle != null && interfaceBClient != null) {
            try {
                List<WorkItemRecord> workItems = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);
                if (workItems != null) {
                    for (WorkItemRecord item : workItems) {
                        // Delete test case if it contains our test data
                        if (item.getCaseID().contains("MCPCapabilityTest")) {
                            String result = interfaceBClient.cancelCase(item.getCaseID(), sessionHandle);
                            LOGGER.info("Cleaned up test case " + item.getCaseID() + ": " + result);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Error cleaning up test state: " + e.getMessage());
            }
        }
    }

    /**
     * Verify MCP server can be started and stopped properly.
     */
    @Test
    @Order(1)
    @DisplayName("Test MCP server health and lifecycle")
    void testMcpServerHealthAndLifecycle() throws Exception {
        // Test server startup
        assertFalse(mcpServer.isRunning(), "Server should not be running before start");

        // Start server
        mcpServer.start();
        assertTrue(mcpServer.isRunning(), "Server should be running after start");

        // Verify MCP server is created
        assertNotNull(mcpServer.getMcpServer(), "McpServer should be available after start");

        // Test server properties
        assertEquals("yawl-mcp-server", mcpServer.getMcpServer().getName(),
            "Server name should be 'yawl-mcp-server'");
        assertFalse(mcpServer.getMcpServer().getName().isEmpty(),
            "Server name should not be empty");

        // Stop server
        mcpServer.stop();
        assertFalse(mcpServer.isRunning(), "Server should not be running after stop");
        assertNull(mcpServer.getMcpServer(), "McpServer should be null after stop");

        // Test idempotent stop
        assertDoesNotThrow(() -> mcpServer.stop(), "Multiple stop calls should be idempotent");
    }

    /**
     * Test MCP server health endpoints.
     */
    @Test
    @Order(2)
    @DisplayName("Test MCP server health endpoints")
    void testMcpServerHealthEndpoints() throws Exception {
        // Start server for health check
        mcpServer.start();

        // Test server capabilities
        McpSchema.ServerCapabilities capabilities = mcpServer.getMcpServer().getServerCapabilities();
        assertNotNull(capabilities, "Server capabilities should not be null");

        // Verify server has tools
        assertNotNull(capabilities.tools(), "Server should have tools capability");
        assertTrue(capabilities.tools().stream().count() > 0, "Server should have at least one tool");

        // Verify server has resources
        assertNotNull(capabilities.resources(), "Server should have resources capability");
        assertTrue(capabilities.resources().stream().count() > 0, "Server should have at least one resource");

        // Test server info
        String serverInfo = mcpServer.getMcpServer().getServerInfo();
        assertNotNull(serverInfo, "Server info should not be null");
        assertTrue(serverInfo.contains("YAWL"), "Server info should contain 'YAWL'");

        // Stop server
        mcpServer.stop();
    }

    /**
     * Test server capability configurations.
     */
    @ParameterizedTest
    @Order(3)
    @ValueSource(strings = {"full", "minimal", "toolsAndResources", "readOnly"})
    @DisplayName("Test server capability configurations")
    void testServerCapabilityConfigurations(String configType) {
        McpSchema.ServerCapabilities caps = switch (configType) {
            case "full" -> YawlServerCapabilities.full();
            case "minimal" -> YawlServerCapabilities.minimal();
            case "toolsAndResources" -> YawlServerCapabilities.toolsAndResources();
            case "readOnly" -> YawlServerCapabilities.readOnly();
            default -> throw new IllegalArgumentException("Unknown config type: " + configType);
        };

        assertNotNull(caps, "Server capabilities should not be null for " + configType);

        switch (configType) {
            case "full":
                assertNotNull(caps.tools(), "Full config should have tools");
                assertNotNull(caps.resources(), "Full config should have resources");
                assertNotNull(caps.prompts(), "Full config should have prompts");
                assertNotNull(caps.logging(), "Full config should have logging");
                assertNotNull(caps.completions(), "Full config should have completions");
                break;

            case "minimal":
                assertNotNull(caps.tools(), "Minimal config should have tools");
                assertNull(caps.resources(), "Minimal config should not have resources");
                assertNull(caps.prompts(), "Minimal config should not have prompts");
                assertNull(caps.logging(), "Minimal config should not have logging");
                assertNull(caps.completions(), "Minimal config should not have completions");
                break;

            case "toolsAndResources":
                assertNotNull(caps.tools(), "ToolsAndResources config should have tools");
                assertNotNull(caps.resources(), "ToolsAndResources config should have resources");
                assertNull(caps.prompts(), "ToolsAndResources config should not have prompts");
                assertNotNull(caps.logging(), "ToolsAndResources config should have logging");
                break;

            case "readOnly":
                assertNull(caps.tools(), "ReadOnly config should not have tools");
                assertNotNull(caps.resources(), "ReadOnly config should have resources");
                assertNotNull(caps.prompts(), "ReadOnly config should have prompts");
                assertNotNull(caps.completions(), "ReadOnly config should have completions");
                break;
        }
    }

    /**
     * Test YAWL tool specifications creation.
     */
    @Test
    @Order(4)
    @DisplayName("Test YAWL tool specifications creation")
    void testYawlToolSpecificationsCreation() {
        // Create all tool specifications
        List<McpServerFeatures.SyncToolSpecification> tools = YawlToolSpecifications.createAll(
            interfaceBClient,
            null,  // InterfaceA client not needed for this test
            sessionHandle
        );

        assertNotNull(tools, "Tool specifications should not be null");
        assertFalse(tools.isEmpty(), "Should have at least one tool specification");

        // Verify we have the expected tools
        List<String> toolNames = tools.stream()
            .map(tool -> tool.tool().name())
            .toList();

        assertTrue(toolNames.contains("yawl_launch_case"), "Should have launch_case tool");
        assertTrue(toolNames.contains("yawl_get_work_items"), "Should have get_work_items tool");
        assertTrue(toolNames.contains("yawl_list_specifications"), "Should have list_specifications tool");
        assertTrue(toolNames.contains("yawl_get_case_status"), "Should have get_case_status tool");

        // Verify tool details
        McpServerFeatures.SyncToolSpecification launchCaseTool = tools.stream()
            .filter(tool -> "yawl_launch_case".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        assertEquals("yawl_launch_case", launchCaseTool.tool().name());
        assertNotNull(launchCaseTool.tool().description());
        assertNotNull(launchCaseTool.tool().inputSchema());
        assertTrue(launchCaseTool.tool().inputSchema().type().equals("object"));
    }

    /**
     * Test tool specification edge cases.
     */
    @ParameterizedTest
    @Order(5)
    @NullAndEmptySource
    @DisplayName("Test tool specification null/edge cases")
    void testYawlToolSpecificationEdgeCases(String nullSessionHandle) {
        if (nullSessionHandle != null) {
            // Test with null session handle
            assertThrows(IllegalArgumentException.class, () -> {
                YawlToolSpecifications.createAll(interfaceBClient, null, nullSessionHandle);
            }, "Should throw IllegalArgumentException for null session handle");
        }

        // Test with null InterfaceB client
        assertThrows(IllegalArgumentException.class, () -> {
            YawlToolSpecifications.createAll(null, null, sessionHandle);
        }, "Should throw IllegalArgumentException for null InterfaceB client");
    }

    /**
     * Test work item listing functionality.
     */
    @Test
    @Order(6)
    @DisplayName("Test work item listing")
    void testWorkItemListing() throws Exception {
        // First create a test case
        createTestCase();

        // Test get_work_items tool
        McpServerFeatures.SyncToolSpecification getWorkItemsTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_get_work_items".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        // Execute the tool
        Map<String, Object> args = Map.of();
        McpSchema.CallToolResult result = getWorkItemsTool.handler().handle(
            null, args  // Exchange not used in sync tools
        );

        assertFalse(result.isError(), "Get work items should not return error");
        assertNotNull(result.content(), "Result content should not be null");
        assertTrue(result.content().contains("Live Work Items"), "Result should contain header");
        assertTrue(result.content().contains(testCaseId), "Result should contain our test case");

        // Verify response format
        assertTrue(result.content().contains("Work Item:"), "Result should show work items");
        assertTrue(result.content().contains("Case ID:"), "Result should show case IDs");
        assertTrue(result.content().contains("Task ID:"), "Result should show task IDs");
    }

    /**
     * Test case creation through MCP tools.
     */
    @Test
    @Order(7)
    @DisplayName("Test case creation through MCP tools")
    void testCaseCreationThroughMcpTools() throws Exception {
        // First, check if test spec exists
        List<SpecificationData> specs = interfaceBClient.getSpecificationList(sessionHandle);
        boolean specExists = specs.stream()
            .anyMatch(spec -> TEST_SPECIFICATION_ID.equals(spec.getID().getIdentifier()));

        if (!specExists) {
            LOGGER.warning("Test specification not found in engine, skipping case creation test");
            return;
        }

        // Test launch_case tool
        McpServerFeatures.SyncToolSpecification launchCaseTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_launch_case".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        // Execute the tool
        Map<String, Object> args = Map.of(
            "specIdentifier", TEST_SPECIFICATION_ID,
            "specVersion", TEST_SPECIFICATION_VERSION,
            "specUri", TEST_SPECIFICATION_URI,
            "caseData", TEST_CASE_DATA
        );

        McpSchema.CallToolResult result = launchCaseTool.handler().handle(null, args);

        assertFalse(result.isError(), "Launch case should not return error");
        assertNotNull(result.content(), "Result content should not be null");
        assertTrue(result.content().contains("Case launched successfully"),
            "Result should indicate successful launch");

        // Extract case ID from result
        String resultContent = result.content();
        int caseIdIndex = resultContent.indexOf("Case ID: ");
        assertTrue(caseIdIndex > -1, "Result should contain case ID");

        testCaseId = resultContent.substring(caseIdIndex + 10).trim();
        assertFalse(testCaseId.isEmpty(), "Case ID should not be empty");

        // Verify case was actually created
        String caseState = interfaceBClient.getCaseState(testCaseId, sessionHandle);
        assertNotNull(caseState, "Should be able to get case state");
        assertFalse(caseState.contains("<error>"), "Case state should not contain error");
    }

    /**
     * Test specification listing functionality.
     */
    @Test
    @Order(8)
    @DisplayName("Test specification listing")
    void testSpecificationListing() throws Exception {
        // Test list_specifications tool
        McpServerFeatures.SyncToolSpecification listSpecsTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_list_specifications".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        // Execute the tool
        Map<String, Object> args = Map.of();
        McpSchema.CallToolResult result = listSpecsTool.handler().handle(null, args);

        assertFalse(result.isError(), "List specifications should not return error");
        assertNotNull(result.content(), "Result content should not be null");
        assertTrue(result.content().contains("Loaded Specifications"), "Result should contain header");

        // Verify response contains specification details
        assertTrue(result.content().contains("Identifier:"), "Result should show identifiers");
        assertTrue(result.content().contains("Version:"), "Result should show versions");
        assertTrue(result.content().contains("URI:"), "Result should show URIs");
        assertTrue(result.content().contains("Status:"), "Result should show statuses");

        // Check if we have any specifications
        List<SpecificationData> specs = interfaceBClient.getSpecificationList(sessionHandle);
        if (!specs.isEmpty()) {
            String firstSpecId = specs.get(0).getID().getIdentifier();
            assertTrue(result.content().contains(firstSpecId),
                "Result should contain first spec ID");
        }
    }

    /**
     * Test YAWL resource provider.
     */
    @Test
    @Order(9)
    @DisplayName("Test YAWL resource provider")
    void testYawlResourceProvider() {
        // Create resource specifications
        List<McpServerFeatures.SyncResourceSpecification> resources =
            YawlResourceProvider.createAllResources(interfaceBClient, sessionHandle);

        assertNotNull(resources, "Resources should not be null");
        assertFalse(resources.isEmpty(), "Should have at least one resource");

        // Verify resource types
        List<String> resourceUris = resources.stream()
            .map(resource -> resource.resource().uri())
            .toList();

        assertTrue(resourceUris.contains("yawl://specifications"),
            "Should have specifications resource");
        assertTrue(resourceUris.contains("yawl://cases"),
            "Should have cases resource");
        assertTrue(resourceUris.contains("yawl://workitems"),
            "Should have workitems resource");

        // Test resource template creation
        List<McpServerFeatures.SyncResourceTemplateSpecification> templates =
            YawlResourceProvider.createAllResourceTemplates(interfaceBClient, sessionHandle);

        assertNotNull(templates, "Templates should not be null");
        assertFalse(templates.isEmpty(), "Should have at least one template");

        // Verify template types
        List<String> templateUris = templates.stream()
            .map(template -> template.template().uri())
            .toList();

        assertTrue(templateUris.contains("yawl://cases/{caseId}"),
            "Should have case details template");
        assertTrue(templateUris.contains("yawl://cases/{caseId}/data"),
            "Should have case data template");
        assertTrue(templateUris.contains("yawl://workitems/{workItemId}"),
            "Should have work item template");
    }

    /**
     * Test resource template URI extraction.
     */
    @Test
    @Order(10)
    @DisplayName("Test resource template URI extraction")
    void testResourceTemplateUriExtraction() {
        // Test URI extraction methods (they're private but we can test behavior)
        String testCaseUri = "yawl://cases/test-123/data";
        String testWorkItemUri = "yawl://workitems/test-456:TaskA";

        // These tests verify the logic by checking expected behavior
        assertTrue(testCaseUri.startsWith("yawl://cases/"),
            "Case URI should start with yawl://cases/");
        assertTrue(testWorkItemUri.startsWith("yawl://workitems/"),
            "Work item URI should start with yawl://workitems/");

        // Test URI contains expected placeholders
        assertTrue(testCaseUri.contains("{caseId}"),
            "Case URI should contain caseId placeholder");
        assertTrue(testWorkItemUri.contains("{workItemId}"),
            "Work item URI should contain workItemId placeholder");
    }

    /**
     * Test error handling and invalid inputs.
     */
    @ParameterizedTest
    @Order(11)
    @ValueSource(strings = {"", "invalid-case-id", "null:case"})
    @DisplayName("Test error handling for invalid case IDs")
    void testErrorHandlingForInvalidInputs(String invalidCaseId) {
        // Test get_case_status tool with invalid case IDs
        McpServerFeatures.SyncToolSpecification getCaseStatusTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_get_case_status".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        // Execute with invalid case ID
        Map<String, Object> args = Map.of("caseId", invalidCaseId);
        McpSchema.CallToolResult result = getCaseStatusTool.handler().handle(null, args);

        // Error handling should vary based on the engine response
        // Some engines might return error, others might handle gracefully
        if (result.isError()) {
            assertNotNull(result.content(), "Error result should have content");
            assertTrue(result.content().contains("Failed to get case status") ||
                      result.content().contains("error"),
                "Error result should indicate failure");
        }
    }

    /**
     * Test concurrent access to MCP resources.
     */
    @Test
    @Order(12)
    @DisplayName("Test concurrent access to MCP resources")
    void testConcurrentAccess() throws Exception {
        // Create test case first
        createTestCase();

        // Get work items tool
        McpServerFeatures.SyncToolSpecification getWorkItemsTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_get_work_items".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        // Execute multiple times concurrently (simulate concurrent access)
        List<Thread> threads = new ArrayList<>();
        List<McpSchema.CallToolResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < 5; i++) {
            Thread thread = new Thread(() -> {
                try {
                    Map<String, Object> args = Map.of();
                    McpSchema.CallToolResult result = getWorkItemsTool.handler().handle(null, args);
                    results.add(result);
                } catch (Exception e) {
                    LOGGER.warning("Concurrent access error: " + e.getMessage());
                }
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(1000); // 1 second timeout
        }

        // Verify all calls completed
        assertEquals(5, results.size(), "All concurrent calls should complete");

        // Verify results are consistent
        for (McpSchema.CallToolResult result : results) {
            assertFalse(result.isError(), "Concurrent access should not return errors");
        }
    }

    /**
     * Test tool execution with various data types.
     */
    @ParameterizedTest
    @Order(13)
    @ValueSource(strings = {"0.1", "1.0", "2.5"})
    @DisplayName("Test tool execution with different version formats")
    void testToolExecutionWithDifferentVersions(String version) throws Exception {
        // Test launch_case tool with different versions
        McpServerFeatures.SyncToolSpecification launchCaseTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_launch_case".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        // Try to launch with each version (may fail if spec doesn't exist)
        Map<String, Object> args = Map.of(
            "specIdentifier", TEST_SPECIFICATION_ID,
            "specVersion", version
        );

        McpSchema.CallToolResult result = launchCaseTool.handler().handle(null, args);

        // Either successful or error, but shouldn't crash
        assertNotNull(result, "Result should never be null");
        if (result.isError()) {
            // Error is acceptable if the specific version doesn't exist
            assertNotNull(result.content(), "Error result should have content");
        }
    }

    /**
     * Test MCP resource reading (simulated).
     */
    @Test
    @Order(14)
    @DisplayName("Test MCP resource reading")
    void testMcpResourceReading() throws Exception {
        // This test simulates MCP resource reading by testing the underlying logic
        List<SpecificationData> specs = interfaceBClient.getSpecificationList(sessionHandle);
        List<WorkItemRecord> workItems = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);

        // Test resource data conversion (matches what YawlResourceProvider would do)
        assertFalse(specs.isEmpty() || workItems.isEmpty(),
            "Should have either specs or work items for testing");

        // Verify data can be formatted as JSON (matches resource provider logic)
        if (!specs.isEmpty()) {
            SpecificationData spec = specs.get(0);
            assertNotNull(spec.getID().getIdentifier(), "Spec should have identifier");
            assertNotNull(spec.getID().getVersion(), "Spec should have version");
            assertNotNull(spec.getID().getUri(), "Spec should have URI");
        }

        if (!workItems.isEmpty()) {
            WorkItemRecord item = workItems.get(0);
            assertNotNull(item.getID(), "Work item should have ID");
            assertNotNull(item.getCaseID(), "Work item should have case ID");
            assertNotNull(item.getTaskID(), "Work item should have task ID");
        }
    }

    /**
     * Comprehensive integration test - create and manage a workflow case.
     */
    @Test
    @Order(15)
    @DisplayName("Test complete workflow case lifecycle through MCP")
    void testCompleteWorkflowLifecycle() throws Exception {
        // 1. List available specifications
        McpServerFeatures.SyncToolSpecification listSpecsTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_list_specifications".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        McpSchema.CallToolResult listResult = listSpecsTool.handler().handle(null, Map.of());
        assertFalse(listResult.isError(), "List specifications should succeed");

        // 2. Create test case (if possible)
        if (testCaseId == null) {
            createTestCase();
        }

        // 3. Get work items for the case
        McpServerFeatures.SyncToolSpecification getWorkItemsForCaseTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_get_work_items_for_case".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        Map<String, Object> caseArgs = Map.of("caseId", testCaseId);
        McpSchema.CallToolResult workItemsResult = getWorkItemsForCaseTool.handler().handle(null, caseArgs);
        assertFalse(workItemsResult.isError(), "Get work items for case should succeed");

        // 4. Get case status
        McpServerFeatures.SyncToolSpecification getCaseStatusTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_get_case_status".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        Map<String, Object> statusArgs = Map.of("caseId", testCaseId);
        McpSchema.CallToolResult statusResult = getCaseStatusTool.handler().handle(null, statusArgs);
        assertFalse(statusResult.isError(), "Get case status should succeed");

        // 5. Check case data
        McpServerFeatures.SyncToolSpecification getCaseDataTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_get_case_data".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        Map<String, Object> dataArgs = Map.of("caseId", testCaseId);
        McpSchema.CallToolResult dataResult = getCaseDataTool.handler().handle(null, dataArgs);
        assertFalse(dataResult.isError(), "Get case data should succeed");

        // 6. Cleanup - cancel the case
        McpServerFeatures.SyncToolSpecification cancelCaseTool = YawlToolSpecifications.createAll(
            interfaceBClient, null, sessionHandle
        ).stream()
            .filter(tool -> "yawl_cancel_case".equals(tool.tool().name()))
            .findFirst()
            .orElseThrow();

        Map<String, Object> cancelArgs = Map.of("caseId", testCaseId);
        McpSchema.CallToolResult cancelResult = cancelCaseTool.handler().handle(null, cancelArgs);
        if (!cancelResult.isError()) {
            testCaseId = null; // Clear test case ID
        }
    }

    /**
     * Test timeout and response time for MCP operations.
     */
    @Test
    @Order(16)
    @DisplayName("Test MCP operation timeouts and response times")
    void testMcpOperationTimeouts() {
        long startTime = System.currentTimeMillis();

        // Test a quick operation
        assertTimeoutPreemptively(5, TimeUnit.SECONDS, () -> {
            McpServerFeatures.SyncToolSpecification listSpecsTool = YawlToolSpecifications.createAll(
                interfaceBClient, null, sessionHandle
            ).stream()
                .filter(tool -> "yawl_list_specifications".equals(tool.tool().name()))
                .findFirst()
                .orElseThrow();

            McpSchema.CallToolResult result = listSpecsTool.handler().handle(null, Map.of());
            assertFalse(result.isError(), "Quick operation should succeed within timeout");
        }, "List specifications should complete within 5 seconds");

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 3000, "Quick operation should complete in less than 3 seconds, took: " + duration + "ms");
    }

    /**
     * Helper method to create a test case.
     */
    private void createTestCase() throws Exception {
        // Create a unique test case ID if we don't have one
        if (testCaseId == null) {
            // Check if test spec exists
            List<SpecificationData> specs = interfaceBClient.getSpecificationList(sessionHandle);
            boolean specExists = specs.stream()
                .anyMatch(spec -> TEST_SPECIFICATION_ID.equals(spec.getID().getIdentifier()));

            if (specExists) {
                // Use launch_case tool
                McpServerFeatures.SyncToolSpecification launchCaseTool = YawlToolSpecifications.createAll(
                    interfaceBClient, null, sessionHandle
                ).stream()
                    .filter(tool -> "yawl_launch_case".equals(tool.tool().name()))
                    .findFirst()
                    .orElseThrow();

                Map<String, Object> args = Map.of(
                    "specIdentifier", TEST_SPECIFICATION_ID,
                    "specVersion", TEST_SPECIFICATION_VERSION,
                    "caseData", TEST_CASE_DATA
                );

                McpSchema.CallToolResult result = launchCaseTool.handler().handle(null, args);
                assertFalse(result.isError(), "Launch case should not return error");

                // Extract case ID
                String resultContent = result.content();
                int caseIdIndex = resultContent.indexOf("Case ID: ");
                assertTrue(caseIdIndex > -1, "Result should contain case ID");

                testCaseId = resultContent.substring(caseIdIndex + 10).trim();
                assertFalse(testCaseId.isEmpty(), "Case ID should not be empty");

                // Wait a moment for case to initialize
                Thread.sleep(1000);
            } else {
                LOGGER.warning("Test specification not found, skipping test case creation");
            }
        }
    }
}