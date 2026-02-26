/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this YAWL repository.
 */

package org.yawlfoundation.yawl.integration.java_python.interoperability;

/**
 * Constants used in MCP protocol validation tests.
 */
public final class McpTestConstants {

    private McpTestConstants() {
        // Utility class - prevent instantiation
    }

    // Test Configuration
    public static final String TEST_SERVER_PORT = "8081";
    public static final int TIMEOUT_MS = 30000;
    public static final int CONCURRENT_REQUESTS = 10;
    public static final int PERFORMANCE_ITERATIONS = 50;

    // Test Case IDs
    public static final String MCP_VALIDATION_SUITE = "MCP_VALIDATION_SUITE";
    public static final String REGISTRATION_TEST = "REGISTRATION_TEST";
    public static final String SCHEMA_VALIDATION_TEST = "SCHEMA_VALIDATION_TEST";
    public static final String EXECUTION_TEST = "EXECUTION_TEST";
    public static final String ERROR_HANDLING_TEST = "ERROR_HANDLING_TEST";
    public static final String PERFORMANCE_TEST = "PERFORMANCE_TEST";

    // Tool Names
    public static final String TOOL_START_CASE = "start_case";
    public static final String TOOL_PROCEED_TASK = "proceed_task";
    public static final String TOOL_COMPLETE_CASE = "complete_case";
    public static final String TOOL_GET_CASE_STATUS = "get_case_status";
    public static final String TOOL_CANCEL_CASE = "cancel_case";
    public static final String TOOL_PAUSE_CASE = "pause_case";
    public static final String TOOL_RESUME_CASE = "resume_case";
    public static final String TOOL_LIST_WORK_ITEMS = "list_work_items";
    public static final String TOOL_REASSIGN_WORK_ITEM = "reassign_work_item";
    public static final String TOOL_QUERY_CASE_DATA = "query_case_data";
    public static final String TOOL_GET_WORKFLOW_DEFINITION = "get_workflow_definition";
    public static final String TOOL_VALIDATE_WORKFLOW = "validate_workflow";
    public static final String TOOL_GET_AUDIT_LOG = "get_audit_log";
    public static final String TOOL_EXECUTE_PYTHON_CODE = "execute_python_code";
    public static final String TOOL_GET_INTEGRATION_STATUS = "get_integration_status";

    // Test Data
    public static final String TEST_CASE_ID = "test-case-001";
    public static final String TEST_USER_ID = "test-user";
    public static final String INVALID_CASE_ID = "invalid case id with spaces";
    public static final String EMPTY_CASE_ID = "";
    public static final String NULL_CASE_ID = null;
    public static final String NONEXISTENT_TOOL = "nonexistent_tool";

    // Status Values
    public static final String SUCCESS = "success";
    public static final String FAILED = "failed";
    public static final String OFFERED = "Offered";
    public static final String ALLOCATED = "Allocated";
    public static final String STARTED = "Started";
    public static final String COMPLETED = "Completed";
    public static final String CANCELLED = "Cancelled";

    // Schema Patterns
    public static final String VALID_SCHEMA_JSON = "{\"type\":\"object\",\"properties\":{\"caseID\":{\"type\":\"string\"}},\"required\":[\"caseID\"]}";
    public static final String INVALID_SCHEMA_JSON = "{invalid json}";
    public static final String MISSING_REQUIRED_SCHEMA_JSON = "{\"type\":\"object\",\"properties\":{}}";

    // Performance Thresholds
    public static final long MAX_SINGLE_EXECUTION_TIME_MS = 1000;
    public static final long MAX_AVERAGE_EXECUTION_TIME_MS = 500;
    public static final long MAX_CONCURRENT_EXECUTION_TIME_MS = 5000;
    public static final int MIN_SUCCESS_RATE_PERCENT = 95;
}