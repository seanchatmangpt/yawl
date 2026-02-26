/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this YAWL repository.
 */

package org.yawlfoundation.yawl.integration.java_python.interoperability;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkflowNet;
import org.yawlfoundation.yawl.engine.interaction.YWorkItem;
import org.yawlfoundation.yawl.engine.interaction.YTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.util.YVerificationMessage;
import org.yawlfoundation.yawl.exceptions.YException;
import org.yawlfoundation.yawl.integration.mcp.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServer;
import org.yawlfoundation.yawl.integration.mcp.schema.McpTool;
import org.yawlfoundation.yawl.integration.mcp.schema.McpParameter;
import org.yawlfoundation.yawl.integration.mcp.schema.McpResponse;
import org.yawlfoundation.yawl.integration.mcp.exceptions.McpException;
import org.yawlfoundation.yawl.integration.mcp.exceptions.McpValidationException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Comprehensive MCP protocol validation test suite.
 * Tests MCP compliance with Interface A/B specifications focusing on:
 * 1. Tool registration and discovery (15 tools)
 * 2. Parameter schema validation
 * 3. Tool execution and response handling
 * 4. Error handling for invalid calls
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class McpProtocolValidationTest extends ValidationTestBase {

    private static final String TEST_CASE_ID = "MCP_VALIDATION_SUITE";
    private static final String MCP_SERVER_PORT = "8081";

    private YawlMcpServer mcpServer;
    private InterfaceA_EnvironmentBasedClient interfaceA;
    private InterfaceB_EnvironmentBasedClient interfaceB;
    private YNetRunner netRunner;

    // Test configuration
    private static final int EXPECTED_TOOLS = 15;
    private static final Map<String, String> TOOL_SCHEMAS = Map.of(
        "start_case", "{\"type\":\"object\",\"properties\":{\"caseID\":{\"type\":\"string\"},\"userID\":{\"type\":\"string\"},\"schemaJSON\":{\"type\":\"string\"}},\"required\":[\"caseID\"]}",
        "proceed_task", "{\"type\":\"object\",\"properties\":{\"workItemID\":{\"type\":\"string\"},\"inputData\":{\"type\":\"object\"},\"userID\":{\"type\":\"string\"}},\"required\":[\"workItemID\",\"userID\"]}",
        "complete_case", "{\"type\":\"object\",\"properties\":{\"caseID\":{\"type\":\"string\"},\"outcome\":{\"type\":\"string\"}},\"required\":[\"caseID\"],\"additionalProperties\":false}",
        "get_case_status", "{\"type\":\"object\",\"properties\":{\"caseID\":{\"type\":\"string\"}},\"required\":[\"caseID\"]}",
        "cancel_case", "{\"type\":\"object\",\"properties\":{\"caseID\":{\"type\":\"string\"},\"reason\":{\"type\":\"string\"}},\"required\":[\"caseID\"]}",
        "pause_case", "{\"type\":\"object\",\"properties\":{\"caseID\":{\"type\":\"string\"}},\"required\":[\"caseID\"]}",
        "resume_case", "{\"type\":\"object\",\"properties\":{\"caseID\":{\"type\":\"string\"}},\"required\":[\"caseID\"]}",
        "list_work_items", "{\"type\":\"object\",\"properties\":{\"caseID\":{\"type\":\"string\"},\"userID\":{\"type\":\"string\"},\"status\":{\"type\":\"string\",\"enum\":[\"Offered\",\"Allocated\",\"Started\",\"Completed\",\"Cancelled\"]}},\"required\":[\"caseID\"],\"additionalProperties\":false}",
        "reassign_work_item", "{\"type\":\"object\",\"properties\":{\"workItemID\":{\"type\":\"string\"},\"userID\":{\"type\":\"string\"},\"reason\":{\"type\":\"string\"}},\"required\":[\"workItemID\",\"userID\"],\"additionalProperties\":false}",
        "query_case_data", "{\"type\":\"object\",\"properties\":{\"caseID\":{\"type\":\"string\"},\"dataType\":{\"type\":\"string\",\"enum\":[\"input\",\"output\",\"process\"]},\"filter\":{\"type\":\"object\"}},\"required\":[\"caseID\"],\"additionalProperties\":false}",
        "get_workflow_definition", "{\"type\":\"object\",\"properties\":{\"processID\":{\"type\":\"string\"}},\"required\":[\"processID\"]}",
        "validate_workflow", "{\"type\":\"object\",\"properties\":{\"schemaJSON\":{\"type\":\"string\"}},\"required\":[\"schemaJSON\"]}",
        "get_audit_log", "{\"type\":\"object\",\"properties\":{\"caseID\":{\"type\":\"string\"},\"startTime\":{\"type\":\"string\"},\"endTime\":{\"type\":\"string\"}},\"required\":[\"caseID\"],\"additionalProperties\":false}",
        "execute_python_code", "{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"string\"},\"context\":{\"type\":\"object\"}},\"required\":[\"code\"]}",
        "get_integration_status", "{\"type\":\"object\",\"properties\":{\"component\":{\"type\":\"string\",\"enum\":[\"python\",\"mcp\",\"database\"]}},\"required\":[\"component\"]}"
    );

    @BeforeAll
    static void setupSuite() {
        assumeTrue(isPythonEnvironmentReady(), "Python environment required for MCP validation");
        ValidationTestBase.initializeTestSuite(TEST_CASE_ID);
    }

    @BeforeEach
    void setupTest() throws Exception {
        super.setupTest();

        // Initialize MCP server
        mcpServer = new YawlMcpServer(MCP_SERVER_PORT);
        mcpServer.start();

        // Initialize client interfaces
        interfaceA = new InterfaceA_EnvironmentBasedClient();
        interfaceB = new InterfaceB_EnvironmentBasedClient();

        // Initialize YAWL net runner
        netRunner = new YNetRunner();
        netRunner.start();

        // Log test setup
        testLogger.info("MCP test setup completed");
    }

    @AfterEach
    void tearDownTest() {
        try {
            if (netRunner != null) {
                netRunner.stop();
            }
            if (mcpServer != null) {
                mcpServer.stop();
            }
        } catch (Exception e) {
            testLogger.warning("Error during cleanup: " + e.getMessage());
        }

        super.tearDownTest();
    }

    @AfterAll
    static void tearDownSuite() {
        ValidationTestBase.finalizeTestSuite(TEST_CASE_ID);
    }

    //region: Test 1 - MCP Tool Registration and Discovery

    @Test
    @DisplayName("Test MCP Tool Registration Discovery")
    @Order(1)
    void testMcpToolRegistrationDiscovery() throws McpException {
        testLogger.startTest("MCP_Tool_Registration_Discovery");

        // Verify tool registration
        List<McpTool> registeredTools = interfaceA.listTools();
        assertEquals(EXPECTED_TOOLS, registeredTools.size(),
            "Should register exactly " + EXPECTED_TOOLS + " tools");

        // Verify tool discovery through Interface B
        List<McpTool> discoveredTools = interfaceB.discoverAvailableTools();
        assertEquals(EXPECTED_TOOLS, discoveredTools.size(),
            "Should discover exactly " + EXPECTED_TOOLS + " tools");

        // Validate Interface A/B compliance
        assertEquals(registeredTools.size(), discoveredTools.size(),
            "Interface A and B should report same number of tools");

        // Check for required tools
        Set<String> toolNames = registeredTools.stream()
            .map(McpTool::getName)
            .collect(Collectors.toSet());

        assertTrue(toolNames.containsAll(List.of(
            "start_case", "proceed_task", "complete_case", "get_case_status",
            "cancel_case", "pause_case", "resume_case", "list_work_items",
            "reassign_work_item", "query_case_data", "get_workflow_definition",
            "validate_workflow", "get_audit_log", "execute_python_code",
            "get_integration_status"
        )), "All required tools should be registered");

        // Test tool metadata compliance
        registeredTools.forEach(tool -> {
            assertNotNull(tool.getName(), "Tool name should not be null");
            assertNotNull(tool.getDescription(), "Tool description should not be null");
            assertFalse(tool.getInputSchema().isEmpty(), "Input schema should not be empty");
            assertTrue(tool.isActivated(), "All tools should be activated");
        });

        testLogger.endTest("MCP_Tool_Registration_Discovery", true);
    }

    //endregion

    //region: Test 2 - Parameter Schema Validation

    @Test
    @DisplayName("Test Parameter Schema Validation")
    @Order(2)
    void testParameterSchemaValidation() throws McpException {
        testLogger.startTest("Parameter_Schema_Validation");

        // Test valid schema for start_case tool
        McpTool startCaseTool = interfaceA.getToolByName("start_case");
        assertNotNull(startCaseTool, "start_case tool should exist");

        // Test parameter schema compliance
        Map<String, McpParameter> parameters = startCaseTool.getParameters();
        assertTrue(parameters.containsKey("caseID"), "caseID parameter should exist");
        assertTrue(parameters.containsKey("userID"), "userID parameter should exist");
        assertTrue(parameters.containsKey("schemaJSON"), "schemaJSON parameter should exist");

        // Test schema type validation
        McpParameter caseIdParam = parameters.get("caseID");
        assertEquals("string", caseIdParam.getType(), "caseID should be string type");
        assertTrue(caseIdParam.isRequired(), "caseID should be required");

        McpParameter userIdParam = parameters.get("userID");
        assertEquals("string", userIdParam.getType(), "userID should be string type");
        assertFalse(userIdParam.isRequired(), "userID should be optional");

        // Test invalid schema patterns
        assertThrows(McpValidationException.class, () -> {
            interfaceA.validateSchema("nonexistent_tool", "{invalid schema}");
        }, "Should throw exception for invalid tool name");

        assertThrows(McpValidationException.class, () -> {
            interfaceA.validateSchema("start_case", "{invalid: json}");
        }, "Should throw exception for invalid JSON schema");

        // Test schema equivalence
        String expectedSchema = TOOL_SCHEMAS.get("start_case");
        assertEquals(expectedSchema, startCaseTool.getInputSchema(),
            "Schema should match expected format");

        testLogger.endTest("Parameter_Schema_Validation", true);
    }

    //endregion

    //region: Test 3 - Tool Execution and Response Handling

    @Test
    @DisplayName("Test Tool Execution Response Handling")
    @Order(3)
    void testToolExecutionResponseHandling() throws Exception {
        testLogger.startTest("Tool_Execution_Response_Handling");

        // Create a simple workflow for testing
        YNet testNet = createTestWorkflowNet();
        YDecomposition testDecomp = new YDecomposition("testProcess");
        testDecomp.setNet(testNet);
        netRunner.loadSpecification(testDecomp);

        // Test start_case execution
        Map<String, Object> startParams = new HashMap<>();
        startParams.put("caseID", "test-case-001");
        startParams.put("userID", "test-user");
        startParams.put("schemaJSON", createTestSchema());

        McpResponse startResponse = interfaceA.executeTool("start_case", startParams);
        assertEquals("success", startResponse.getStatus(), "Start case should succeed");
        assertNotNull(startResponse.getData(), "Response should contain data");
        assertEquals("test-case-001", startResponse.getData().get("caseID"), "Case ID should match");

        // Test get_case_status execution
        Map<String, Object> statusParams = new HashMap<>();
        statusParams.put("caseID", "test-case-001");

        McpResponse statusResponse = interfaceA.executeTool("get_case_status", statusParams);
        assertEquals("success", statusResponse.getStatus(), "Status retrieval should succeed");
        assertTrue(statusResponse.getData().containsKey("status"), "Response should contain status");

        // Test list_work_items execution
        Map<String, Object> workParams = new HashMap<>();
        workParams.put("caseID", "test-case-001");
        workParams.put("userID", "test-user");

        McpResponse workItemsResponse = interfaceA.executeTool("list_work_items", workParams);
        assertEquals("success", workItemsResponse.getStatus(), "Work items list should succeed");
        assertTrue(workItemsResponse.getData().containsKey("items"), "Response should contain items");

        // Test proceed_task workflow advancement
        List<YWorkItem> offeredItems = netRunner.getWorkItems("test-user", "Offered");
        assertFalse(offeredItems.isEmpty(), "Should have offered work items");
        YWorkItem firstItem = offeredItems.get(0);

        Map<String, Object> proceedParams = new HashMap<>();
        proceedParams.put("workItemID", firstItem.getID());
        proceedParams.put("userID", "test-user");
        proceedParams.put("inputData", Map.of("task_input", "test_value"));

        McpResponse proceedResponse = interfaceA.executeTool("proceed_task", proceedParams);
        assertEquals("success", proceedResponse.getStatus(), "Task proceed should succeed");

        // Test complete_case execution
        Map<String, Object> completeParams = new HashMap<>();
        completeParams.put("caseID", "test-case-001");
        completeParams.put("outcome", "completed");

        McpResponse completeResponse = interfaceA.executeTool("complete_case", completeParams);
        assertEquals("success", completeResponse.getStatus(), "Case completion should succeed");

        testLogger.endTest("Tool_Execution_Response_Handling", true);
    }

    //endregion

    //region: Test 4 - Concurrent Tool Execution

    @Test
    @DisplayName("Test Concurrent Tool Execution")
    @Order(4)
    void testConcurrentToolExecution() throws Exception {
        testLogger.startTest("Concurrent_Tool_Execution");

        // Create multiple test cases
        List<String> caseIds = Arrays.asList("case-001", "case-002", "case-003", "case-004", "case-005");

        // Execute start_case operations concurrently
        List<CompletableFuture<McpResponse>> futures = new ArrayList<>();

        for (String caseId : caseIds) {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", caseId);
            params.put("userID", "test-user");

            CompletableFuture<McpResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return interfaceA.executeTool("start_case", params);
                } catch (McpException e) {
                    throw new RuntimeException(e);
                }
            });

            futures.add(future);
        }

        // Wait for all operations to complete
        List<McpResponse> responses = new ArrayList<>();
        for (CompletableFuture<McpResponse> future : futures) {
            try {
                McpResponse response = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                responses.add(response);
                assertEquals("success", response.getStatus(), "All start_case should succeed");
            } catch (ExecutionException | InterruptedException e) {
                fail("Concurrent execution failed: " + e.getMessage());
            }
        }

        // Verify all cases were created
        assertEquals(caseIds.size(), responses.size(), "Should have responses for all cases");

        // Test concurrent case status queries
        List<CompletableFuture<McpResponse>> statusFutures = new ArrayList<>();
        for (String caseId : caseIds) {
            Map<String, Object> params = Map.of("caseID", caseId);
            CompletableFuture<McpResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return interfaceA.executeTool("get_case_status", params);
                } catch (McpException e) {
                    throw new RuntimeException(e);
                }
            });
            statusFutures.add(future);
        }

        // Validate status queries
        for (CompletableFuture<McpResponse> future : statusFutures) {
            try {
                McpResponse response = future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals("success", response.getStatus(), "Status query should succeed");
            } catch (ExecutionException | InterruptedException e) {
                fail("Concurrent status query failed: " + e.getMessage());
            }
        }

        testLogger.endTest("Concurrent_Tool_Execution", true);
    }

    //endregion

    //region: Test 5 - Error Handling for Invalid Calls

    @Test
    @DisplayName("Test Error Handling for Invalid Calls")
    @Order(5)
    void testErrorHandlingForInvalidCalls() {
        testLogger.startTest("Error_Holding_Invalid_Calls");

        // Test non-existent tool
        assertThrows(McpValidationException.class, () -> {
            interfaceA.executeTool("nonexistent_tool", Map.of());
        }, "Should throw exception for non-existent tool");

        // Test missing required parameters
        assertThrows(McpValidationException.class, () -> {
            interfaceA.executeTool("start_case", Map.of("userID", "test-user"));
        }, "Should throw exception for missing caseID");

        // Test invalid parameter types
        assertThrows(McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", 12345); // Should be string
            params.put("userID", "test-user");
            interfaceA.executeTool("start_case", params);
        }, "Should throw exception for invalid parameter type");

        // Test null parameter values
        assertThrows(McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", null);
            params.put("userID", "test-user");
            interfaceA.executeTool("start_case", params);
        }, "Should throw exception for null parameter values");

        // Test empty string for required parameter
        assertThrows(McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", "");
            params.put("userID", "test-user");
            interfaceA.executeTool("start_case", params);
        }, "Should throw exception for empty required parameter");

        // Test invalid JSON schema
        assertThrows(McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", "test-case");
            params.put("schemaJSON", "invalid json");
            interfaceA.executeTool("start_case", params);
        }, "Should throw exception for invalid JSON schema");

        // Test workItemID for non-existent case
        assertThrows(McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", "non-existent-case");
            interfaceA.executeTool("list_work_items", params);
        }, "Should throw exception for non-existent case");

        // Test caseID format validation
        assertThrows(McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", "invalid case id with spaces");
            interfaceA.executeTool("start_case", params);
        }, "Should throw exception for invalid caseID format");

        // Test invalid enum values
        assertThrows(McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", "test-case");
            params.put("status", "invalid_status");
            interfaceA.executeTool("list_work_items", params);
        }, "Should throw exception for invalid enum value");

        // Test successful execution after invalid calls (robustness)
        try {
            Map<String, Object> validParams = new HashMap<>();
            validParams.put("caseID", "robustness-test");
            validParams.put("userID", "test-user");

            McpResponse response = interfaceA.executeTool("start_case", validParams);
            assertEquals("success", response.getStatus(), "Should work after invalid calls");
        } catch (McpException e) {
            fail("Should maintain functionality after invalid calls: " + e.getMessage());
        }

        testLogger.endTest("Error_Holding_Invalid_Calls", true);
    }

    //endregion

    //region: Test 6 - Interface A/B Compliance

    @Test
    @DisplayName("Test Interface A/B Compliance")
    @Order(6)
    void testInterfaceABCompliance() throws Exception {
        testLogger.startTest("Interface_A_B_Compliance");

        // Tool discovery compliance
        List<McpTool> toolsA = interfaceA.listTools();
        List<McpTool> toolsB = interfaceB.discoverAvailableTools();

        assertEquals(toolsA.size(), toolsB.size(), "Interface A and B should have same tool count");

        // Tool definition compliance
        Map<String, McpTool> toolsAMap = toolsA.stream()
            .collect(Collectors.toMap(McpTool::getName, t -> t));

        for (McpTool toolB : toolsB) {
            McpTool toolA = toolsAMap.get(toolB.getName());
            assertNotNull(toolA, "Tool should exist in Interface A: " + toolB.getName());

            assertEquals(toolA.getName(), toolB.getName(), "Tool names should match");
            assertEquals(toolA.getDescription(), toolB.getDescription(), "Descriptions should match");
            assertEquals(toolA.getInputSchema(), toolB.getInputSchema(), "Schemas should match");
            assertEquals(toolA.isActivated(), toolB.isActivated(), "Activation status should match");
        }

        // Tool execution compliance
        Map<String, Object> testParams = Map.of(
            "caseID", "compliance-test",
            "userID", "test-user"
        );

        McpResponse responseA = interfaceA.executeTool("start_case", testParams);
        McpResponse responseB = interfaceB.executeTool("start_case", testParams);

        assertEquals(responseA.getStatus(), responseB.getStatus(), "Status should be identical");
        assertEquals(responseA.getData(), responseB.getData(), "Data should be identical");
        assertEquals(responseA.getTimestamp(), responseB.getTimestamp(), "Timestamps should be identical");

        // Error handling compliance
        assertThrows(McpValidationException.class, () -> {
            interfaceA.executeTool("nonexistent", Map.of());
        });

        assertThrows(McpValidationException.class, () -> {
            interfaceB.executeTool("nonexistent", Map.of());
        });

        testLogger.endTest("Interface_A_B_Compliance", true);
    }

    //endregion

    //region: Test 7 - Performance and Load Testing

    @Test
    @DisplayName("Test Performance and Load")
    @Order(7)
    void testPerformanceAndLoad() throws Exception {
        testLogger.startTest("Performance_Load_Testing");

        // Performance test: Single tool execution
        long startTime = System.currentTimeMillis();

        Map<String, Object> params = Map.of(
            "caseID", "perf-test",
            "userID", "test-user"
        );

        McpResponse response = interfaceA.executeTool("start_case", params);
        long endTime = System.currentTimeMillis();

        assertTrue(response.getStatus().equals("success"), "Tool execution should succeed");
        long executionTime = endTime - startTime;
        testLogger.info("Single execution time: " + executionTime + "ms");

        // Load test: Multiple sequential executions
        int iterations = 50;
        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            String caseId = "load-test-" + i;
            Map<String, Object> loadParams = Map.of(
                "caseID", caseId,
                "userID", "test-user"
            );

            long loadStartTime = System.currentTimeMillis();
            McpResponse loadResponse = interfaceA.executeTool("start_case", loadParams);
            long loadEndTime = System.currentTimeMillis();

            assertTrue(loadResponse.getStatus().equals("success"),
                "Iteration " + i + " should succeed");
            executionTimes.add(loadEndTime - loadStartTime);
        }

        // Calculate statistics
        double avgTime = executionTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);

        double maxTime = executionTimes.stream()
            .mapToLong(Long::longValue)
            .max()
            .orElse(0);

        testLogger.info("Load test results:");
        testLogger.info("- Average execution time: " + String.format("%.2f", avgTime) + "ms");
        testLogger.info("- Maximum execution time: " + maxTime + "ms");
        testLogger.info("- All executions < 5000ms: " +
            executionTimes.stream().allMatch(t -> t < 5000));

        // Performance assertions
        assertTrue(avgTime < 1000, "Average execution time should be < 1000ms");
        assertTrue(maxTime < 5000, "Maximum execution time should be < 5000ms");

        testLogger.endTest("Performance_Load_Testing", true);
    }

    //endregion

    //region: Helper Methods

    private YNet createTestWorkflowNet() throws YException {
        YNet net = new YNet("testNet");

        // Create simple workflow: start -> task1 -> task2 -> complete
        net.addTask("start", "Start Task", false);
        net.addTask("task1", "First Task", false);
        net.addTask("task2", "Second Task", false);
        net.addTask("complete", "Complete Task", true);

        // Add transitions
        net.addFlow("start", "task1");
        net.addFlow("task1", "task2");
        net.addFlow("task2", "complete");

        // Set start and end tasks
        net.setStartTask("start");
        net.setEndTask("complete");

        return net;
    }

    private String createTestSchema() {
        return "{\n" +
               "  \"tasks\": [\n" +
               "    {\"id\": \"task1\", \"name\": \"First Task\", \"timeout\": 300},\n" +
               "    {\"id\": \"task2\", \"name\": \"Second Task\", \"timeout\": 600}\n" +
               "  ]\n" +
               "}";
    }

    //endregion
}