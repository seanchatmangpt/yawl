/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this YAWL repository.
 */

package org.yawlfoundation.yawl.integration.java_python.interoperability;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;

/**
 * Test data factory for MCP protocol validation tests.
 */
public final class McpTestData {

    private McpTestData() {
        // Utility class - prevent instantiation
    }

    /**
     * Returns valid parameters for start_case tool.
     */
    public static Map<String, Object> getValidStartCaseParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("caseID", "test-case-001");
        params.put("userID", "test-user");
        params.put("schemaJSON", getValidWorkflowSchema());
        return params;
    }

    /**
     * Returns valid parameters for proceed_task tool.
     */
    public static Map<String, Object> getValidProceedTaskParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("workItemID", "work-item-001");
        params.put("userID", "test-user");
        params.put("inputData", Map.of("task_input", "test_value"));
        return params;
    }

    /**
     * Returns valid parameters for complete_case tool.
     */
    public static Map<String, Object> getValidCompleteCaseParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("caseID", "test-case-001");
        params.put("outcome", "completed");
        return params;
    }

    /**
     * Returns valid parameters for get_case_status tool.
     */
    public static Map<String, Object> getValidGetCaseStatusParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("caseID", "test-case-001");
        return params;
    }

    /**
     * Returns valid parameters for list_work_items tool.
     */
    public static Map<String, Object> getValidListWorkItemsParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("caseID", "test-case-001");
        params.put("userID", "test-user");
        params.put("status", "Offered");
        return params;
    }

    /**
     * Returns valid parameters for cancel_case tool.
     */
    public static Map<String, Object> getValidCancelCaseParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("caseID", "test-case-001");
        params.put("reason", "Test cancellation");
        return params;
    }

    /**
     * Returns valid parameters for pause_case tool.
     */
    public static Map<String, Object> getValidPauseCaseParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("caseID", "test-case-001");
        return params;
    }

    /**
     * Returns valid parameters for resume_case tool.
     */
    public static Map<String, Object> getValidResumeCaseParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("caseID", "test-case-001");
        return params;
    }

    /**
     * Returns valid parameters for reassign_work_item tool.
     */
    public static Map<String, Object> getValidReassignWorkItemParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("workItemID", "work-item-001");
        params.put("userID", "another-user");
        params.put("reason", "Test reassignment");
        return params;
    }

    /**
     * Returns valid parameters for query_case_data tool.
     */
    public static Map<String, Object> getValidQueryCaseDataParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("caseID", "test-case-001");
        params.put("dataType", "input");
        params.put("filter", Map.of("field", "value"));
        return params;
    }

    /**
     * Returns valid parameters for get_workflow_definition tool.
     */
    public static Map<String, Object> getValidGetWorkflowDefinitionParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("processID", "test-process");
        return params;
    }

    /**
     * Returns valid parameters for validate_workflow tool.
     */
    public static Map<String, Object> getValidValidateWorkflowParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("schemaJSON", getValidWorkflowSchema());
        return params;
    }

    /**
     * Returns valid parameters for get_audit_log tool.
     */
    public static Map<String, Object> getValidGetAuditLogParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("caseID", "test-case-001");
        params.put("startTime", "2026-01-01T00:00:00Z");
        params.put("endTime", "2026-12-31T23:59:59Z");
        return params;
    }

    /**
     * Returns valid parameters for execute_python_code tool.
     */
    public static Map<String, Object> getValidExecutePythonCodeParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("code", "print('Hello from Python!')");
        params.put("context", Map.of("variable", "value"));
        return params;
    }

    /**
     * Returns valid parameters for get_integration_status tool.
     */
    public static Map<String, Object> getValidGetIntegrationStatusParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("component", "python");
        return params;
    }

    /**
     * Returns invalid parameters for testing error cases.
     */
    public static List<Map<String, Object>> getInvalidParamsList() {
        return Arrays.asList(
            // Missing required parameters
            Map.of("userID", "test-user"), // Missing caseID for start_case

            // Invalid parameter types
            Map.of("caseID", 12345, "userID", "test-user"), // caseID should be string

            // Null parameters
            Map.of("caseID", null, "userID", "test-user"),

            // Empty required parameters
            Map.of("caseID", "", "userID", "test-user"),

            // Invalid enum values
            Map.of("caseID", "test-case", "status", "invalid_status"),

            // Invalid caseID format
            Map.of("caseID", "invalid case id with spaces", "userID", "test-user")
        );
    }

    /**
     * Returns a valid workflow schema for testing.
     */
    public static String getValidWorkflowSchema() {
        return "{\n" +
               "  \"tasks\": [\n" +
               "    {\"id\": \"task1\", \"name\": \"First Task\", \"timeout\": 300},\n" +
               "    {\"id\": \"task2\", \"name\": \"Second Task\", \"timeout\": 600},\n" +
               "    {\"id\": \"task3\", \"name\": \"Third Task\", \"timeout\": 900}\n" +
               "  ],\n" +
               "  \"constraints\": [\n" +
               "    {\"type\": \"sequence\", \"from\": \"task1\", \"to\": \"task2\"},\n" +
               "    {\"type\": \"sequence\", \"from\": \"task2\", \"to\": \"task3\"}\n" +
               "  ]\n" +
               "}";
    }

    /**
     * Returns a list of test case IDs for load testing.
     */
    public static List<String> getLoadTestCaseIds() {
        List<String> caseIds = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            caseIds.add("load-test-" + i);
        }
        return caseIds;
    }

    /**
     * Returns expected tool names for validation.
     */
    public static List<String> getExpectedToolNames() {
        return Arrays.asList(
            McpTestConstants.TOOL_START_CASE,
            McpTestConstants.TOOL_PROCEED_TASK,
            McpTestConstants.TOOL_COMPLETE_CASE,
            McpTestConstants.TOOL_GET_CASE_STATUS,
            McpTestConstants.TOOL_CANCEL_CASE,
            McpTestConstants.TOOL_PAUSE_CASE,
            McpTestConstants.TOOL_RESUME_CASE,
            McpTestConstants.TOOL_LIST_WORK_ITEMS,
            McpTestConstants.TOOL_REASSIGN_WORK_ITEM,
            McpTestConstants.TOOL_QUERY_CASE_DATA,
            McpTestConstants.TOOL_GET_WORKFLOW_DEFINITION,
            McpTestConstants.TOOL_VALIDATE_WORKFLOW,
            McpTestConstants.TOOL_GET_AUDIT_LOG,
            McpTestConstants.TOOL_EXECUTE_PYTHON_CODE,
            McpTestConstants.TOOL_GET_INTEGRATION_STATUS
        );
    }
}