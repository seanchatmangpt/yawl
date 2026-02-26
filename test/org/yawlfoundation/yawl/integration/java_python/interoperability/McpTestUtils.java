/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this YAWL repository.
 */

package org.yawlfoundation.yawl.integration.java_python.interoperability;

import org.yawlfoundation.yawl.integration.mcp.schema.McpTool;
import org.yawlfoundation.yawl.integration.mcp.schema.McpResponse;
import org.yawlfoundation.yawl.integration.mcp.exceptions.McpException;
import org.yawlfoundation.yawl.integration.mcp.exceptions.McpValidationException;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.exceptions.YException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;

/**
 * Utility methods for MCP protocol validation tests.
 */
public final class McpTestUtils {

    private McpTestUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that all expected tools are present in the provided list.
     *
     * @param tools The list of tools to validate
     * @param expectedToolNames List of expected tool names
     * @throws AssertionError if any expected tool is missing
     */
    public static void validateExpectedTools(List<McpTool> tools, List<String> expectedToolNames) {
        Map<String, McpTool> toolMap = tools.stream()
            .collect(Collectors.toMap(McpTool::getName, t -> t));

        for (String expectedName : expectedToolNames) {
            McpTool tool = toolMap.get(expectedName);
            assertNotNull(tool, "Expected tool not found: " + expectedName);
            assertTrue(tool.isActivated(), "Tool should be activated: " + expectedName);
            assertNotNull(tool.getDescription(), "Tool should have description: " + expectedName);
            assertFalse(tool.getInputSchema().isEmpty(), "Tool should have input schema: " + expectedName);
        }
    }

    /**
     * Validates the structure of a work item response.
     *
     * @param response The response to validate
     */
    public static void validateWorkItemResponse(McpResponse response) {
        assertEquals(McpTestConstants.SUCCESS, response.getStatus(),
            "Response status should be success");

        Map<String, Object> data = response.getData();
        assertNotNull(data, "Response data should not be null");

        // Validate common work item fields
        assertTrue(data.containsKey("workItemID"), "Should contain workItemID");
        assertTrue(data.containsKey("taskName"), "Should contain taskName");
        assertTrue(data.containsKey("status"), "Should contain status");
        assertTrue(data.containsKey("startTime"), "Should contain startTime");
        assertTrue(data.containsKey("userID"), "Should contain userID");

        // Validate data types
        assertTrue(data.get("workItemID") instanceof String, "workItemID should be string");
        assertTrue(data.get("taskName") instanceof String, "taskName should be string");
        assertTrue(data.get("status") instanceof String, "status should be string");
        assertTrue(data.get("startTime") instanceof String, "startTime should be string");
        assertTrue(data.get("userID") instanceof String, "userID should be string");
    }

    /**
     * Validates the structure of a case status response.
     *
     * @param response The response to validate
     */
    public static void validateCaseStatusResponse(McpResponse response) {
        assertEquals(McpTestConstants.SUCCESS, response.getStatus(),
            "Response status should be success");

        Map<String, Object> data = response.getData();
        assertNotNull(data, "Response data should not be null");

        // Validate case status fields
        assertTrue(data.containsKey("caseID"), "Should contain caseID");
        assertTrue(data.containsKey("status"), "Should contain status");
        assertTrue(data.containsKey("createTime"), "Should contain createTime");
        assertTrue(data.containsKey("lastUpdateTime"), "Should contain lastUpdateTime");

        // Validate data types
        assertTrue(data.get("caseID") instanceof String, "caseID should be string");
        assertTrue(data.get("status") instanceof String, "status should be string");
        assertTrue(data.get("createTime") instanceof String, "createTime should be string");
        assertTrue(data.get("lastUpdateTime") instanceof String, "lastUpdateTime should be string");

        // Validate status enum values
        String status = (String) data.get("status");
        List<String> validStatuses = Arrays.asList(
            "Offered", "Allocated", "Started", "Completed", "Cancelled", "Suspended"
        );
        assertTrue(validStatuses.contains(status), "Invalid status value: " + status);
    }

    /**
     * Validates the structure of an audit log response.
     *
     * @param response The response to validate
     */
    public static void validateAuditLogResponse(McpResponse response) {
        assertEquals(McpTestConstants.SUCCESS, response.getStatus(),
            "Response status should be success");

        Map<String, Object> data = response.getData();
        assertNotNull(data, "Response data should not be null");

        // Validate audit log fields
        assertTrue(data.containsKey("caseID"), "Should contain caseID");
        assertTrue(data.containsKey("entries"), "Should contain entries");
        assertTrue(data.containsKey("totalCount"), "Should contain totalCount");

        // Validate data types
        assertTrue(data.get("caseID") instanceof String, "caseID should be string");
        assertTrue(data.get("entries") instanceof List, "entries should be list");
        assertTrue(data.get("totalCount") instanceof Integer, "totalCount should be integer");

        // Validate log entries
        List<Map<String, Object>> entries = (List<Map<String, Object>>) data.get("entries");
        for (Map<String, Object> entry : entries) {
            assertTrue(entry.containsKey("timestamp"), "Log entry should have timestamp");
            assertTrue(entry.containsKey("eventType"), "Log entry should have eventType");
            assertTrue(entry.containsKey("userID"), "Log entry should have userID");
            assertTrue(entry.containsKey("description"), "Log entry should have description");
        }
    }

    /**
     * Validates the structure of a workflow validation response.
     *
     * @param response The response to validate
     */
    public static void validateWorkflowValidationResponse(McpResponse response) {
        assertEquals(McpTestConstants.SUCCESS, response.getStatus(),
            "Response status should be success");

        Map<String, Object> data = response.getData();
        assertNotNull(data, "Response data should not be null");

        // Validate validation response fields
        assertTrue(data.containsKey("isValid"), "Should contain isValid");
        assertTrue(data.containsKey("validationMessages"), "Should contain validationMessages");

        // Validate data types
        assertTrue(data.get("isValid") instanceof Boolean, "isValid should be boolean");
        assertTrue(data.get("validationMessages") instanceof List, "validationMessages should be list");

        // Validate validation messages if present
        List<String> messages = (List<String>) data.get("validationMessages");
        if (!messages.isEmpty()) {
            for (String message : messages) {
                assertFalse(message.trim().isEmpty(), "Validation message should not be empty");
            }
        }
    }

    /**
     * Validates error response structure.
     *
     * @param response The response to validate
     * @param expectedErrorCode Expected error code
     */
    public static void validateErrorResponse(McpResponse response, String expectedErrorCode) {
        assertEquals(McpTestConstants.FAILED, response.getStatus(),
            "Response status should be failed");

        Map<String, Object> data = response.getData();
        assertNotNull(data, "Response data should not be null");

        // Validate error response fields
        assertTrue(data.containsKey("errorCode"), "Should contain errorCode");
        assertTrue(data.containsKey("errorMessage"), "Should contain errorMessage");

        // Validate values
        assertEquals(expectedErrorCode, data.get("errorCode"), "Error code should match");
        assertTrue(((String) data.get("errorMessage")).length() > 0, "Error message should not be empty");
    }

    /**
     * Creates a simple workflow net for testing.
     *
     * @param netId The net identifier
     * @return The created YNet
     * @throws YException if net creation fails
     */
    public static YNet createSimpleWorkflowNet(String netId) throws YException {
        YNet net = new YNet(netId);

        // Create input and output conditions
        YInputCondition input = new YInputCondition("input", "Input Condition");
        YOutputCondition output = new YOutputCondition("output", "Output Condition");
        net.addCondition(input);
        net.addCondition(output);

        // Create tasks
        YTask task1 = new YAtomicTask("task1", "Task 1");
        YTask task2 = new YAtomicTask("task2", "Task 2");
        YTask task3 = new YAtomicTask("task3", "Task 3");
        net.addTask(task1);
        net.addTask(task2);
        net.addTask(task3);

        // Create flows
        net.addFlow(input, task1);
        net.addFlow(task1, task2);
        net.addFlow(task2, task3);
        net.addFlow(task3, output);

        // Set start and end elements
        net.setStartCondition(input);
        net.setEndCondition(output);

        return net;
    }

    /**
     * Executes multiple tool requests concurrently and collects results.
     *
     * @param toolName The tool name to execute
     * @param paramsFactory Function to create parameters for each request
     * @param count Number of concurrent requests
     * @param timeout Timeout in seconds
     * @return List of responses
     * @throws InterruptedException if the thread is interrupted
     */
    public static List<McpResponse> executeConcurrentRequests(
            String toolName,
            java.util.function.Function<Integer, Map<String, Object>> paramsFactory,
            int count,
            int timeout) throws InterruptedException {

        List<CompletableFuture<McpResponse>> futures = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Map<String, Object> params = paramsFactory.apply(i);
            CompletableFuture<McpResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return InterfaceB_EnvironmentBasedClient.executeTool(toolName, params);
                } catch (McpException e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        List<McpResponse> responses = new ArrayList<>();
        for (CompletableFuture<McpResponse> future : futures) {
            try {
                responses.add(future.get(timeout, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                responses.add(createErrorResponse("CONCURRENT_ERROR", e.getMessage()));
            }
        }

        return responses;
    }

    /**
     * Creates a mock error response.
     *
     * @param errorCode Error code
     * @param errorMessage Error message
     * @return Mock McpResponse
     */
    public static McpResponse createErrorResponse(String errorCode, String errorMessage) {
        Map<String, Object> data = new HashMap<>();
        data.put("errorCode", errorCode);
        data.put("errorMessage", errorMessage);

        McpResponse response = new McpResponse();
        response.setStatus(McpTestConstants.FAILED);
        response.setData(data);
        response.setTimestamp(java.time.Instant.now().toString());

        return response;
    }

    /**
     * Validates that a response contains all required fields for a case.
     *
     * @param response The response to validate
     */
    public static void validateCaseResponse(McpResponse response) {
        assertEquals(McpTestConstants.SUCCESS, response.getStatus(),
            "Response status should be success");

        Map<String, Object> data = response.getData();
        assertNotNull(data, "Response data should not be null");

        // Validate case fields
        assertTrue(data.containsKey("caseID"), "Should contain caseID");
        assertTrue(data.containsKey("processID"), "Should contain processID");
        assertTrue(data.containsKey("userID"), "Should contain userID");
        assertTrue(data.containsKey("createTime"), "Should contain createTime");
        assertTrue(data.containsKey("lastUpdateTime"), "Should contain lastUpdateTime");
        assertTrue(data.containsKey("status"), "Should contain status");

        // Validate data types
        assertTrue(data.get("caseID") instanceof String, "caseID should be string");
        assertTrue(data.get("processID") instanceof String, "processID should be string");
        assertTrue(data.get("userID") instanceof String, "userID should be string");
        assertTrue(data.get("createTime") instanceof String, "createTime should be string");
        assertTrue(data.get("lastUpdateTime") instanceof String, "lastUpdateTime should be string");
        assertTrue(data.get("status") instanceof String, "status should be string");
    }

    /**
     * Validates performance metrics from a test run.
     *
     * @param executionTimes List of execution times in milliseconds
     * @param maxAllowedTime Maximum allowed execution time
     * @param minSuccessRate Minimum required success rate (0.0 to 1.0)
     */
    public static void validatePerformanceMetrics(List<Long> executionTimes,
                                                  long maxAllowedTime,
                                                  double minSuccessRate) {

        // Calculate statistics
        double avgTime = executionTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);

        long maxTime = executionTimes.stream()
            .mapToLong(Long::longValue)
            .max()
            .orElse(0);

        long minTime = executionTimes.stream()
            .mapToLong(Long::longValue)
            .min()
            .orElse(0);

        // Count slow executions
        long slowExecutions = executionTimes.stream()
            .filter(t -> t > maxAllowedTime)
            .count();

        // Calculate success rate
        double successRate = 1.0 - (double) slowExecutions / executionTimes.size();

        // Assert performance thresholds
        assertTrue(avgTime < maxAllowedTime,
            String.format("Average execution time (%.2fms) exceeds maximum allowed (%dms)", avgTime, maxAllowedTime));

        assertTrue(maxTime < maxAllowedTime * 2,
            String.format("Maximum execution time (%dms) is too high", maxTime));

        assertTrue(successRate >= minSuccessRate,
            String.format("Success rate (%.2f%%) is below minimum required (%.2f%%)",
                successRate * 100, minSuccessRate * 100));

        // Log performance metrics
        System.out.printf("Performance Metrics:%n");
        System.out.printf("- Average time: %.2fms%n", avgTime);
        System.out.printf("- Min time: %dms%n", minTime);
        System.out.printf("- Max time: %dms%n", maxTime);
        System.out.printf("- Slow executions: %d/%d (%.2f%%)%n",
            slowExecutions, executionTimes.size(), (1.0 - successRate) * 100);
        System.out.printf("- Success rate: %.2f%%%n", successRate * 100);
    }
}