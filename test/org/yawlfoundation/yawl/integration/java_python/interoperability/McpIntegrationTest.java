/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this YAWL repository.
 */

package org.yawlfoundation.yawl.integration.java_python.interoperability;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.interaction.YWorkItem;
import org.yawlfoundation.yawl.engine.interaction.YTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.exceptions.YException;
import org.yawlfoundation.yawl.integration.mcp.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.exceptions.McpException;
import org.yawlfoundation.yawl.integration.mcp.schema.McpResponse;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for MCP protocol with YAWL engine.
 * This test ensures the MCP tools work correctly with the actual YAWL runtime.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class McpIntegrationTest extends ValidationTestBase {

    private static final String TEST_CASE_ID = "MCP_INTEGRATION_SUITE";
    private static final String WORKFLOW_ID = "integration-test-workflow";

    private YNetRunner netRunner;
    private InterfaceB_EnvironmentBasedClient mcpClient;

    @BeforeAll
    static void setupSuite() {
        assumeTrue(isPythonEnvironmentReady(), "Python environment required for MCP integration");
        ValidationTestBase.initializeTestSuite(TEST_CASE_ID);
    }

    @BeforeEach
    void setupTest() throws Exception {
        super.setupTest();

        // Initialize MCP client
        mcpClient = new InterfaceB_EnvironmentBasedClient();

        // Initialize YAWL net runner with test workflow
        netRunner = new YNetRunner();
        netRunner.start();

        YNet testNet = createIntegrationWorkflowNet();
        YDecomposition testDecomp = new YDecomposition(WORKFLOW_ID);
        testDecomp.setNet(testNet);
        netRunner.loadSpecification(testDecomp);

        // Log test setup
        testLogger.info("MCP integration test setup completed");
    }

    @AfterEach
    void tearDownTest() {
        if (netRunner != null) {
            try {
                netRunner.stop();
            } catch (Exception e) {
                testLogger.warning("Error stopping net runner: " + e.getMessage());
            }
        }
        super.tearDownTest();
    }

    @AfterAll
    static void tearDownSuite() {
        ValidationTestBase.finalizeTestSuite(TEST_CASE_ID);
    }

    @Test
    @DisplayName("Test End-to-End MCP Workflow Execution")
    @Order(1)
    void testEndToEndMcpWorkflow() throws Exception {
        testLogger.startTest("EndToEnd_MCP_Workflow");

        // Step 1: Start case
        Map<String, Object> startParams = new HashMap<>();
        startParams.put("caseID", "integration-case-001");
        startParams.put("userID", "integration-user");

        McpResponse startResponse = mcpClient.executeTool("start_case", startParams);
        assertEquals("success", startResponse.getStatus(), "Start case should succeed");
        assertNotNull(startResponse.getData(), "Response should contain data");

        // Step 2: Get offered work items
        Map<String, Object> listParams = new HashMap<>();
        listParams.put("caseID", "integration-case-001");
        listParams.put("userID", "integration-user");

        McpResponse listResponse = mcpClient.executeTool("list_work_items", listParams);
        assertEquals("success", listResponse.getStatus(), "List work items should succeed");

        // Extract work items from response
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> workItems = (List<Map<String, Object>>) listResponse.getData()
            .get("items");
        assertFalse(workItems.isEmpty(), "Should have offered work items");

        // Step 3: Proceed with first work item
        String workItemId = workItems.get(0).get("workItemID").toString();
        Map<String, Object> proceedParams = new HashMap<>();
        proceedParams.put("workItemID", workItemId);
        proceedParams.put("userID", "integration-user");
        proceedParams.put("inputData", Map.of("task_input", "test_value"));

        McpResponse proceedResponse = mcpClient.executeTool("proceed_task", proceedParams);
        assertEquals("success", proceedResponse.getStatus(), "Proceed task should succeed");

        // Step 4: Complete the case
        Map<String, Object> completeParams = new HashMap<>();
        completeParams.put("caseID", "integration-case-001");
        completeParams.put("outcome", "completed");

        McpResponse completeResponse = mcpClient.executeTool("complete_case", completeParams);
        assertEquals("success", completeResponse.getStatus(), "Complete case should succeed");

        // Verify case status after completion
        Map<String, Object> statusParams = Map.of("caseID", "integration-case-001");
        McpResponse statusResponse = mcpClient.executeTool("get_case_status", statusParams);
        assertEquals("success", statusResponse.getStatus(), "Get status should succeed");

        @SuppressWarnings("unchecked")
        Map<String, Object> statusData = statusResponse.getData();
        assertEquals("Completed", statusData.get("status"), "Case should be completed");

        testLogger.endTest("EndToEnd_MCP_Workflow", true);
    }

    @Test
    @DisplayName("Test MCP Tools with Real YAWL Engine")
    @Order(2)
    void testMcpToolsWithRealYawlEngine() throws Exception {
        testLogger.startTest("MCP_Tools_With_Real_Yawl");

        // Test that all 15 MCP tools work with the actual YAWL engine
        String[] toolsToTest = {
            "start_case", "proceed_task", "complete_case", "get_case_status",
            "cancel_case", "pause_case", "resume_case", "list_work_items",
            "reassign_work_item", "query_case_data", "get_workflow_definition",
            "validate_workflow", "get_audit_log", "execute_python_code",
            "get_integration_status"
        };

        int successCount = 0;
        List<String> errors = new ArrayList<>();

        for (String toolName : toolsToTest) {
            try {
                McpResponse response = mcpClient.executeTool(toolName, createValidParameters(toolName));
                if ("success".equals(response.getStatus())) {
                    successCount++;
                    testLogger.info("Tool " + toolName + " executed successfully");
                } else {
                    errors.add(toolName + ": " + response.getData());
                }
            } catch (Exception e) {
                errors.add(toolName + ": " + e.getMessage());
                testLogger.warning("Tool " + toolName + " failed: " + e.getMessage());
            }
        }

        assertTrue(successCount == toolsToTest.length,
            String.format("All 15 tools should succeed. Success: %d, Errors: %s",
                successCount, errors));

        testLogger.endTest("MCP_Tools_With_Real_Yawl", true);
    }

    @Test
    @DisplayName(" Test Concurrent MCP Execution with YAWL")
    @Order(3)
    void testConcurrentMcpExecutionWithYawl() throws Exception {
        testLogger.startTest("Concurrent_MCP_Execution");

        // Create multiple test cases
        List<String> caseIds = Arrays.asList("conc-case-1", "conc-case-2", "conc-case-3", "conc-case-4", "conc-case-5");
        List<CompletableFuture<McpResponse>> futures = new ArrayList<>();

        // Start cases concurrently
        for (String caseId : caseIds) {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", caseId);
            params.put("userID", "conc-user");

            CompletableFuture<McpResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return mcpClient.executeTool("start_case", params);
                } catch (McpException e) {
                    throw new RuntimeException(e);
                }
            });

            futures.add(future);
        }

        // Wait for all cases to start
        for (CompletableFuture<McpResponse> future : futures) {
            McpResponse response = future.get(10, TimeUnit.SECONDS);
            assertEquals("success", response.getStatus(), "Case start should succeed");
        }

        // Process work items concurrently
        List<CompletableFuture<McpResponse>> processFutures = new ArrayList<>();
        for (String caseId : caseIds) {
            Map<String, Object> params = Map.of("caseID", caseId, "userID", "conc-user");

            CompletableFuture<McpResponse> listFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return mcpClient.executeTool("list_work_items", params);
                } catch (McpException e) {
                    throw new RuntimeException(e);
                }
            });

            processFutures.add(listFuture);
        }

        // Process each work item
        for (CompletableFuture<McpResponse> future : processFutures) {
            McpResponse listResponse = future.get(10, TimeUnit.SECONDS);
            assertEquals("success", listResponse.getStatus(), "List work items should succeed");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) listResponse.getData()
                .get("items");

            if (!items.isEmpty()) {
                String workItemId = items.get(0).get("workItemID").toString();
                Map<String, Object> proceedParams = Map.of(
                    "workItemID", workItemId,
                    "userID", "conc-user",
                    "inputData", Map.of("input", "concurrent_test")
                );

                McpResponse proceedResponse = mcpClient.executeTool("proceed_task", proceedParams);
                assertEquals("success", proceedResponse.getStatus(), "Proceed task should succeed");
            }
        }

        // Complete all cases
        for (String caseId : caseIds) {
            Map<String, Object> params = Map.of(
                "caseID", caseId,
                "outcome", "completed"
            );
            McpResponse response = mcpClient.executeTool("complete_case", params);
            assertEquals("success", response.getStatus(), "Complete case should succeed");
        }

        testLogger.endTest("Concurrent_MCP_Execution", true);
    }

    @Test
    @DisplayName("Test Error Handling with YAWL Engine")
    @Order(4)
    void testErrorHandlingWithYawlEngine() {
        testLogger.startTest("Error_Handing_With_Yawl");

        // Test invalid case ID with real engine
        assertThrows(org.yawlfoundation.yawl.integration.mcp.exceptions.McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", "invalid case id with spaces");
            mcpClient.executeTool("start_case", params);
        }, "Should reject invalid case ID");

        // Test case ID that doesn't exist
        assertThrows(org.yawlfoundation.yawl.integration.mcp.exceptions.McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("caseID", "nonexistent-case-id");
            mcpClient.executeTool("proceed_task", params);
        }, "Should handle nonexistent case");

        // Test missing required parameter
        assertThrows(org.yawlfoundation.yawl.integration.mcp.exceptions.McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("userID", "test-user");
            mcpClient.executeTool("start_case", params);
        }, "Should require caseID");

        // Test reassign with invalid user
        assertThrows(org.yawlfoundation.yawl.integration.mcp.exceptions.McpValidationException.class, () -> {
            Map<String, Object> params = new HashMap<>();
            params.put("workItemID", "invalid-work-item");
            params.put("userID", "");
            mcpClient.executeTool("reassign_work_item", params);
        }, "Should reject empty user ID");

        testLogger.endTest("Error_Handing_With_Yawl", true);
    }

    @Test
    @DisplayName("Test Performance with YAWL Engine")
    @Order(5)
    void testPerformanceWithYawlEngine() throws Exception {
        testLogger.startTest("Performance_With_Yawl");

        // Warm up
        Map<String, Object> warmupParams = Map.of("caseID", "warmup", "userID", "perf-user");
        mcpClient.executeTool("start_case", warmupParams);

        // Measure execution time for start_case
        int iterations = 20;
        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            String caseId = "perf-case-" + i;
            Map<String, Object> params = Map.of("caseID", caseId, "userID", "perf-user");

            long startTime = System.currentTimeMillis();
            McpResponse response = mcpClient.executeTool("start_case", params);
            long endTime = System.currentTimeMillis();

            assertEquals("success", response.getStatus(), "Performance test should succeed");
            executionTimes.add(endTime - startTime);
        }

        // Calculate statistics
        double avgTime = executionTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);

        long maxTime = executionTimes.stream()
            .mapToLong(Long::longValue)
            .max()
            .orElse(0);

        testLogger.info("Performance results:");
        testLogger.info("- Average time: " + String.format("%.2f", avgTime) + "ms");
        testLogger.info("- Max time: " + maxTime + "ms");
        testLogger.info("- All < 1000ms: " +
            executionTimes.stream().allMatch(t -> t < 1000));

        // Performance assertions
        assertTrue(avgTime < 1000, "Average time should be < 1000ms");
        assertTrue(maxTime < 3000, "Max time should be < 3000ms");

        testLogger.endTest("Performance_With_Yawl", true);
    }

    //region: Helper Methods

    private YNet createIntegrationWorkflowNet() throws YException {
        YNet net = new YNet("integration-test-net");

        // Create tasks
        YTask startTask = new org.yawlfoundation.yawl.elements.YExternalNetElement("start", "Start", false);
        YTask task1 = new org.yawlfoundation.yawl.elements.YAtomicTask("task1", "First Task");
        YTask task2 = new org.yawlfoundation.yawl.elements.YAtomicTask("task2", "Second Task");
        YTask completeTask = new org.yawlfoundation.yawl.elements.YExternalNetElement("complete", "Complete", true);

        // Add elements to net
        net.addTask(startTask);
        net.addTask(task1);
        net.addTask(task2);
        net.addTask(completeTask);

        // Create flows
        net.addFlow(startTask, task1);
        net.addFlow(task1, task2);
        net.addFlow(task2, completeTask);

        // Set start and end
        net.setStartTask(startTask);
        net.setEndTask(completeTask);

        return net;
    }

    private Map<String, Object> createValidParameters(String toolName) {
        switch (toolName) {
            case "start_case":
                return Map.of(
                    "caseID", "test-case",
                    "userID", "test-user"
                );
            case "proceed_task":
                return Map.of(
                    "workItemID", "work-item-id",
                    "userID", "test-user",
                    "inputData", Map.of()
                );
            case "complete_case":
                return Map.of(
                    "caseID", "test-case",
                    "outcome", "completed"
                );
            case "get_case_status":
                return Map.of("caseID", "test-case");
            case "list_work_items":
                return Map.of(
                    "caseID", "test-case",
                    "userID", "test-user"
                );
            case "get_workflow_definition":
                return Map.of("processID", "test-process");
            case "validate_workflow":
                return Map.of("schemaJSON", "{\"type\":\"object\"}");
            case "execute_python_code":
                return Map.of("code", "print('Hello')");
            case "get_integration_status":
                return Map.of("component", "python");
            default:
                return Map.of(); // For tools that don't require parameters
        }
    }

    //endregion
}