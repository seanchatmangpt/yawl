/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.graalpy.integration;

/**
 * Configuration class for MCP protocol testing.
 *
 * Defines expected behaviors, validation rules, and test parameters
 * for all MCP tool validation scenarios.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class McpTestConfig {

    // Test engine configuration
    public static final String DEFAULT_ENGINE_URL = "http://localhost:8080/yawl";
    public static final String DEFAULT_USERNAME = "admin";
    public static final String DEFAULT_PASSWORD = "admin";
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    // Test specification configuration
    public static final String SIMPLE_WORKFLOW_ID = "SimpleWorkflow";
    public static final String SIMPLE_WORKFLOW_VERSION = "1.0";
    public static final String SIMPLE_WORKFLOW_URI = "http://example.com/simple.xml";

    public static final String COMPREHENSIVE_WORKFLOW_ID = "ComprehensiveWorkflow";
    public static final String COMPREHENSIVE_WORKFLOW_VERSION = "1.0";
    public static final String COMPREHENSIVE_WORKFLOW_URI = "http://example.com/comprehensive.xml";

    public static final String CONDITIONAL_WORKFLOW_ID = "ConditionalWorkflow";
    public static final String CONDITIONAL_WORKFLOW_VERSION = "1.0";
    public static final String CONDITIONAL_WORKFLOW_URI = "http://example.com/conditional.xml";

    public static final String PARALLEL_WORKFLOW_ID = "ParallelWorkflow";
    public static final String PARALLEL_WORKFLOW_VERSION = "1.0";
    public static final String PARALLEL_WORKFLOW_URI = "http://example.com/parallel.xml";

    // Expected tool names (must match exactly with MCP implementation)
    public static final String[] EXPECTED_TOOLS = {
        "yawl_launch_case",
        "yawl_get_case_state",
        "yawl_cancel_case",
        "yawl_list_specifications",
        "yawl_get_specification",
        "yawl_upload_specification",
        "yawl_get_work_items",
        "yawl_get_work_items_for_case",
        "yawl_check_out_work_item",
        "yawl_check_in_work_item",
        "yawl_get_running_cases",
        "yawl_get_case_data",
        "yawl_suspend_case",
        "yawl_resume_case",
        "yawl_skip_work_item",
        "yawl_synthesize_spec"
    };

    // Required parameters for each tool
    public static final String[][] REQUIRED_PARAMETERS = {
        {"yawl_launch_case", {"specIdentifier"}},
        {"yawl_get_case_state", {"caseId"}},
        {"yawl_cancel_case", {"caseId"}},
        {"yawl_list_specifications", {}},
        {"yawl_get_specification", {"specIdentifier", "specVersion"}},
        {"yawl_upload_specification", {"specXml"}},
        {"yawl_get_work_items", {}},
        {"yawl_get_work_items_for_case", {"caseId"}},
        {"yawl_check_out_work_item", {"caseId", "workItemId"}},
        {"yawl_check_in_work_item", {"caseId", "workItemId"}},
        {"yawl_get_running_cases", {}},
        {"yawl_get_case_data", {"caseId"}},
        {"yawl_suspend_case", {"caseId"}},
        {"yawl_resume_case", {"caseId"}},
        {"yawl_skip_work_item", {"caseId", "workItemId"}},
        {"yawl_synthesize_spec", {"prompt"}}
    };

    // Expected success response patterns for each tool
    public static final String[][] SUCCESS_RESPONSE_PATTERNS = {
        {"yawl_launch_case", {"Case launched successfully", "Case ID:", "Specification:"}},
        {"yawl_get_case_state", {"Case ID:", "State:"}},
        {"yawl_cancel_case", {"Case cancelled", "successfully"}},
        {"yawl_list_specifications", {"Specifications:", "specIdentifier"}},
        {"yawl_get_specification", {"<specification", "<specificationSet"}},
        {"yawl_upload_specification", {"Specification", "uploaded", "successfully"}},
        {"yawl_get_work_items", {"WorkItemRecord", "workItemID"}},
        {"yawl_get_work_items_for_case", {"WorkItemRecord", "workItemID"}},
        {"yawl_check_out_work_item", {"Work item", "checked out", "successfully"}},
        {"yawl_check_in_work_item", {"Work item", "checked in", "successfully"}},
        {"yawl_get_running_cases", {"running cases", "caseID"}},
        {"yawl_get_case_data", {"<data", "</data>"}},
        {"yawl_suspend_case", {"Case", "suspended", "successfully"}},
        {"yawl_resume_case", {"Case", "resumed", "successfully"}},
        {"yawl_skip_work_item", {"Work item", "skipped", "successfully"}},
        {"yawl_synthesize_spec", {"Synthesized", "specification", "XML"}}
    };

    // Expected error patterns for common failure scenarios
    public static final String[][] ERROR_PATTERNS = {
        {"connection failed", "cannot connect", "unreachable"},
        {"not found", "does not exist", "unavailable"},
        {"invalid", "malformed", "incorrect format"},
        {"permission denied", "access denied", "unauthorized"},
        {"timeout", "exceeded", "server error"},
        {"xml", "parse", "invalid"},
        {"null", "empty", "missing"}
    };

    // Validation rules for case IDs
    public static final String CASE_ID_PREFIX = "urn:yawl:case:";
    public static final int MIN_CASE_ID_LENGTH = 20;
    public static final int MAX_CASE_ID_LENGTH = 100;

    // Validation rules for specification IDs
    public static final int MIN_SPEC_ID_LENGTH = 3;
    public static final int MAX_SPEC_ID_LENGTH = 50;
    public static final String[] INVALID_SPEC_ID_CHARS = {
        " ", "\t", "\n", "\r", "\"", "'", "<", ">", "&", "|", ";", "*", "?", "{", "}", "[", "]", "(", ")"
    };

    // Response validation rules
    public static final int MAX_RESPONSE_SIZE = 100000; // 100KB
    public static final int MIN_SUCCESS_RESPONSE_LENGTH = 10;
    public static final int MIN_ERROR_RESPONSE_LENGTH = 5;

    // Timeouts and delays
    public static final long LAUNCH_CASE_TIMEOUT_MS = 10000; // 10 seconds
    public static final long CASE_STATE_TIMEOUT_MS = 5000;  // 5 seconds
    public static final long WORK_ITEM_TIMEOUT_MS = 5000;   // 5 seconds
    public static final long RETRY_DELAY_MS = 1000;         // 1 second

    // Test data templates
    public static final String MINIMAL_CASE_DATA = "<data></data>";
    public static final String PARAMETERIZED_CASE_DATA = "<data><param name=\"test\">value</param></data>";
    public static final String INVALID_XML_CASE_DATA = "<invalid<xml>";
    public static final String EMPTY_CASE_DATA = "";

    // Performance thresholds
    public static final long MAX_TOOL_EXECUTION_TIME_MS = 5000; // 5 seconds per tool
    public static final int MAX_CONCURRENT_TOOLS = 10;
    public static final int TOOL_EXECUTION_SAMPLE_SIZE = 100;

    // Security-related patterns to avoid in responses
    public static final String[] SECURITY_SENSITIVE_PATTERNS = {
        "password", "secret", "token", "auth", "credential", "session",
        "admin", "root", "password", "confidential", "private"
    };

    // Interface A/B compliance requirements
    public static final boolean INTERFACE_A_REQUIRED = true;
    public static final boolean INTERFACE_B_REQUIRED = true;
    public static final boolean MIXED_OPERATIONS_ALLOWED = true;

    // MCP protocol version compliance
    public static final String MCP_PROTOCOL_VERSION = "2025-11-25";
    public static final String MCP_SCHEMA_VERSION = "1.0.0-RC3";

    // Test categorization
    public static final String[] TOOL_CATEGORIES = {
        "Case Management",
        "Work Item Management",
        "Specification Management",
        "Workflow Control",
        "AI-Powered Operations"
    };

    // Validation status tracking
    public static final String VALIDATION_STATUS_PENDING = "PENDING";
    public static final String VALIDATION_STATUS_RUNNING = "RUNNING";
    public static final String VALIDATION_STATUS_PASSED = "PASSED";
    public static final String VALIDATION_STATUS_FAILED = "FAILED";
    public static final String VALIDATION_STATUS_SKIPPED = "SKIPPED";

    // Compliance levels
    public static final String COMPL_LEVEL_BASIC = "BASIC";
    public static final String COMPL_LEVEL_INTERFACE_A = "INTERFACE_A";
    public static final String COMPL_LEVEL_INTERFACE_B = "INTERFACE_B";
    public static final String COMPL_LEVEL_FULL = "FULL";

    // Test result tracking
    public static class TestResult {
        private final String toolName;
        private final String testCategory;
        private final String status;
        private final long executionTimeMs;
        private final String errorMessage;
        private final boolean interfaceACompliant;
        private final boolean interfaceBCompliant;

        public TestResult(String toolName, String testCategory, String status,
                         long executionTimeMs, String errorMessage,
                         boolean interfaceACompliant, boolean interfaceBCompliant) {
            this.toolName = toolName;
            this.testCategory = testCategory;
            this.status = status;
            this.executionTimeMs = executionTimeMs;
            this.errorMessage = errorMessage;
            this.interfaceACompliant = interfaceACompliant;
            this.interfaceBCompliant = interfaceBCompliant;
        }

        // Getters
        public String getToolName() { return toolName; }
        public String getTestCategory() { return testCategory; }
        public String getStatus() { return status; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isInterfaceACompliant() { return interfaceACompliant; }
        public boolean isInterfaceBCompliant() { return interfaceBCompliant; }
    }

    private McpTestConfig() {
        throw new UnsupportedOperationException(
            "McpTestConfig is a configuration class and cannot be instantiated.");
    }

    /**
     * Validates that all expected tools are present in the actual list.
     */
    public static boolean validateExpectedTools(String[] actualTools) {
        if (actualTools.length != EXPECTED_TOOLS.length) {
            return false;
        }

        for (String expected : EXPECTED_TOOLS) {
            boolean found = false;
            for (String actual : actualTools) {
                if (expected.equals(actual)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates required parameters for a specific tool.
     */
    public static boolean validateRequiredParameters(String toolName, String[] providedParams) {
        for (String[] toolParams : REQUIRED_PARAMETERS) {
            if (toolParams[0].equals(toolName)) {
                String[] required = toolParams[1];
                if (required.length == 0) {
                    return providedParams == null || providedParams.length == 0;
                }

                if (providedParams.length != required.length) {
                    return false;
                }

                for (String req : required) {
                    boolean found = false;
                    for (String provided : providedParams) {
                        if (req.equals(provided)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Validates success response patterns.
     */
    public static boolean validateSuccessResponse(String toolName, String response) {
        for (String[] patterns : SUCCESS_RESPONSE_PATTERNS) {
            if (patterns[0].equals(toolName)) {
                for (int i = 1; i < patterns.length; i++) {
                    if (!response.contains(patterns[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Validates error response patterns.
     */
    public static boolean validateErrorResponse(String response, String[] expectedPatterns) {
        for (String pattern : expectedPatterns) {
            if (!response.toLowerCase().contains(pattern.toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates case ID format.
     */
    public static boolean validateCaseId(String caseId) {
        if (caseId == null || caseId.length() < MIN_CASE_ID_LENGTH || caseId.length() > MAX_CASE_ID_LENGTH) {
            return false;
        }
        return caseId.startsWith(CASE_ID_PREFIX);
    }

    /**
     * Validates specification ID format.
     */
    public static boolean validateSpecId(String specId) {
        if (specId == null || specId.length() < MIN_SPEC_ID_LENGTH || specId.length() > MAX_SPEC_ID_LENGTH) {
            return false;
        }

        for (String invalidChar : INVALID_SPEC_ID_CHARS) {
            if (specId.contains(invalidChar)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks for security-sensitive information in responses.
     */
    public static boolean hasSecuritySensitiveInformation(String response) {
        if (response == null) {
            return false;
        }

        String lowerResponse = response.toLowerCase();
        for (String sensitive : SECURITY_SENSITIVE_PATTERNS) {
            if (lowerResponse.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines compliance level based on Interface A/B compliance.
     */
    public static String determineComplianceLevel(boolean interfaceA, boolean interfaceB) {
        if (interfaceA && interfaceB) {
            return COMPL_LEVEL_FULL;
        } else if (interfaceA) {
            return COMPL_LEVEL_INTERFACE_A;
        } else if (interfaceB) {
            return COMPL_LEVEL_INTERFACE_B;
        } else {
            return COMPL_LEVEL_BASIC;
        }
    }
}