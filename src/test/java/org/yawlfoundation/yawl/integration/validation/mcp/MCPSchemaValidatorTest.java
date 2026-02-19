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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.validation.mcp;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.integration.validation.schema.JsonSchemaValidator;
import org.yawlfoundation.yawl.integration.validation.schema.SchemaValidationError;
import org.yawlfoundation.yawl.integration.validation.schema.ValidationConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the MCPSchemaValidator class.
 *
 * Tests MCP protocol message validation with various scenarios including
 * tool calls, tool results, business rules, and integration testing.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class MCPSchemaValidatorTest {

    private MCPSchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MCPSchemaValidator();
    }

    @Test
    @DisplayName("Validate valid tool call")
    void validateValidToolCall() {
        String validToolCall = "{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"method\":\"launch_workflow\",\"params\":{\"specificationId\":\"spec-abc-def\",\"data\":{\"caseName\":\"Test Case\"}}}";

        assertDoesNotThrow(() -> {
            JsonSchemaValidator.ValidationResult result = validator.validateToolCall(validToolCall);
            assertTrue(result.isValid());
        });
    }

    @Test
    @DisplayName("Validate valid tool result")
    void validateValidToolResult() {
        String validToolResult = "{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"result\":{\"caseId\":\"case-xyz-123\",\"status\":\"completed\",\"data\":{\"message\":\"Workflow launched successfully\"}}}";

        assertDoesNotThrow(() -> {
            JsonSchemaValidator.ValidationResult result = validator.validateToolResult(validToolResult);
            assertTrue(result.isValid());
        });
    }

    @Test
    @DisplayName("Validate invalid tool call - missing method")
    void validateInvalidToolCallMissingMethod() {
        String invalidToolCall = "{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"params\":{\"specificationId\":\"spec-abc-def\"}}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateToolCall(invalidToolCall);
        });

        assertFalse(exception.isValidationError());
    }

    @Test
    @DisplayName("Validate invalid tool call - invalid method")
    void validateInvalidToolCallInvalidMethod() {
        String invalidToolCall = "{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"method\":\"invalid_method\",\"params\":{}}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateToolCall(invalidToolCall);
        });

        assertFalse(exception.isValidationError());
    }

    @Test
    @DisplayName("Validate valid prompt request")
    void validateValidPromptRequest() {
        String validPrompt = "{\"jsonrpc\":\"2.0\",\"id\":\"prompt-123\",\"method\":\"prompts/get\",\"params\":{\"name\":\"workflow_summary\",\"arguments\":{\"caseId\":\"case-xyz\"}}}";

        assertDoesNotThrow(() -> {
            JsonSchemaValidator.ValidationResult result = validator.validatePromptRequest(validPrompt);
            assertTrue(result.isValid());
        });
    }

    @Test
    @DisplayName("Validate valid completion request")
    void validateValidCompletionRequest() {
        String validCompletion = "{\"jsonrpc\":\"2.0\",\"id\":\"comp-123\",\"method\":\"completions/list\",\"params\":{\"reference\":{\"type\":\"prompt\",\"name\":\"workflow_summary\"},\"argument\":{\"name\":\"caseId\",\"value\":\"case-\"}}}";

        assertDoesNotThrow(() -> {
            JsonSchemaValidator.ValidationResult result = validator.validateCompletionRequest(validCompletion);
            assertTrue(result.isValid());
        });
    }

    @Test
    @DisplayName("Validate valid case ID")
    void validateValidCaseId() {
        assertTrue(validator.validateCaseId("case12345678"));
        assertTrue(validator.validateCaseId("CASE-ABC-123"));
        assertTrue(validator.validateCaseId("12345678901234567890123456789012")); // 32 chars
        assertFalse(validator.validateCaseId(null));
        assertFalse(validator.validateCaseId(""));
        assertFalse(validator.validateCaseId("short"));
        assertFalse(validator.validateCaseId("this-is-way-too-long-for-a-case-id"));
    }

    @Test
    @DisplayName("Validate valid work item ID")
    void validateValidWorkItemId() {
        assertTrue(validator.validateWorkItemId("WI-12345"));
        assertTrue(validator.validateWorkItemId("WI-ABC123"));
        assertFalse(validator.validateWorkItemId(null));
        assertFalse(validator.validateWorkItemId(""));
        assertFalse(validator.validateWorkItemId("INVALID"));
        assertFalse(validator.validateWorkItemId("WI-1234567890")); // Too long
    }

    @Test
    @DisplayName("Validate valid specification ID")
    void validateValidSpecificationId() {
        assertTrue(validator.validateSpecificationId("spec-123"));
        assertTrue(validator.validateSpecificationId("spec-name"));
        assertTrue(validator.validateSpecificationId("spec_name-123"));
        assertFalse(validator.validateSpecificationId(null));
        assertFalse(validator.validateSpecificationId(""));
        assertFalse(validator.validateSpecificationId("123spec"));
        assertFalse(validator.validateSpecificationId("spec@name"));
    }

    @Test
    @DisplayName("Validate timeout values")
    void validateTimeoutValues() {
        assertTrue(validator.validateTimeout(1000));    // Minimum
        assertTrue(validator.validateTimeout(300000));  // Maximum
        assertTrue(validator.validateTimeout(50000));   // In range
        assertFalse(validator.validateTimeout(999));    // Too small
        assertFalse(validator.validateTimeout(300001)); // Too large
        assertFalse(validator.validateTimeout(-1000));   // Negative
    }

    @Test
    @DisplayName("Test tool call business rules - valid methods")
    void testToolCallBusinessRulesValidMethods() {
        String[] validMethods = {
            "launch_workflow",
            "query_workflows",
            "manage_workitems",
            "cancel_workflow"
        };

        for (String method : validMethods) {
            String toolCall = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"method\":\"%s\",\"params\":{}}", method);
            assertDoesNotThrow(() -> {
                validator.validateToolCallBusinessRules(new com.fasterxml.jackson.databind.ObjectMapper().readTree(toolCall));
            });
        }
    }

    @Test
    @DisplayName("Test tool call business rules - invalid method")
    void testToolCallBusinessRulesInvalidMethod() {
        String invalidMethod = "invalid_method";
        String toolCall = String.format("{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"method\":\"%s\",\"params\":{}}", invalidMethod);

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateToolCallBusinessRules(new com.fasterxml.jackson.databind.ObjectMapper().readTree(toolCall));
        });

        assertTrue(exception.getMessage().contains("Invalid tool method"));
        assertEquals("/method", exception.getJsonPointer());
    }

    @Test
    @DisplayName("Test tool parameters - launch_workflow requires specId")
    void testToolParametersLaunchWorkflow() {
        String toolCall = "{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"method\":\"launch_workflow\",\"params\":{\"data\":\"test\"}}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateToolCallBusinessRules(new com.fasterxml.jackson.databind.ObjectMapper().readTree(toolCall));
        });

        assertTrue(exception.getMessage().contains("Missing required parameter"));
        assertEquals("/params/specificationId", exception.getJsonPointer());
    }

    @Test
    @DisplayName("Test tool parameters - invalid spec ID format")
    void testToolParametersInvalidSpecId() {
        String toolCall = "{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"method\":\"launch_workflow\",\"params\":{\"specificationId\":\"invalid@spec\"}}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateToolCallBusinessRules(new com.fasterxml.jackson.databind.ObjectMapper().readTree(toolCall));
        });

        assertTrue(exception.getMessage().contains("Invalid specification ID format"));
    }

    @Test
    @DisplayName("Test tool parameters - manage_workitems requires workItemId")
    void testToolParametersManageWorkitems() {
        String toolCall = "{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"method\":\"manage_workitems\",\"params\":{\"data\":\"test\"}}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateToolCallBusinessRules(new com.fasterxml.jackson.databind.ObjectMapper().readTree(toolCall));
        });

        assertTrue(exception.getMessage().contains("Missing required parameter"));
        assertEquals("/params/workItemId", exception.getJsonPointer());
    }

    @Test
    @DisplayName("Test tool parameters - cancel_workflow requires caseId")
    void testToolParametersCancelWorkflow() {
        String toolCall = "{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"method\":\"cancel_workflow\",\"params\":{\"data\":\"test\"}}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateToolCallBusinessRules(new com.fasterxml.jackson.databind.ObjectMapper().readTree(toolCall));
        });

        assertTrue(exception.getMessage().contains("Missing required parameter"));
        assertEquals("/params/caseId", exception.getJsonPointer());
    }

    @Test
    @DisplayName("Test tool parameters - invalid timeout value")
    void testToolParametersInvalidTimeout() {
        String toolCall = "{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"method\":\"launch_workflow\",\"params\":{\"specificationId\":\"spec-123\",\"options\":{\"timeout\":999}}}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateToolCallBusinessRules(new com.fasterxml.jackson.databind.ObjectMapper().readTree(toolCall));
        });

        assertTrue(exception.getMessage().contains("Invalid timeout value"));
        assertEquals("/params/options/timeout", exception.getJsonPointer());
    }

    @Test
    @DisplayName("Test complete MCP workflow validation")
    void testCompleteMCPWorkflow() {
        String toolCall = "{\"jsonrpc\":\"2.0\",\"id\":\"tool-call-123\",\"method\":\"launch_workflow\",\"params\":{\"specificationId\":\"spec-123\"}}";
        String toolResult = "{\"jsonrpc\":\"2.0\",\"id\":\"tool-call-123\",\"result\":{\"caseId\":\"case-xyz\",\"status\":\"completed\"}}";

        assertDoesNotThrow(() -> {
            JsonSchemaValidator.ValidationResult result = validator.validateCompleteWorkflow(toolCall, toolResult);
            assertTrue(result.isValid());
        });
    }

    @Test
    @DisplayName("Test complete MCP workflow with mismatched IDs")
    void testCompleteMCPWorkflowMismatchedIds() {
        String toolCall = "{\"jsonrpc\":\"2.0\",\"id\":\"call-123\",\"method\":\"launch_workflow\",\"params\":{\"specificationId\":\"spec-123\"}}";
        String toolResult = "{\"jsonrpc\":\"2.0\",\"id\":\"call-456\",\"result\":{\"caseId\":\"case-xyz\",\"status\":\"completed\"}}";

        SchemaValidationError exception = assertThrows(SchemaValidationError.class, () -> {
            validator.validateCompleteWorkflow(toolCall, toolResult);
        });

        assertTrue(exception.getMessage().contains("Mismatched call and result IDs"));
    }

    @Test
    @DisplayName("Test configuration management")
    void testConfigurationManagement() {
        // Test default configuration
        assertNotNull(validator.getConfig());
        assertTrue(validator.getConfig().isEnableCaching());

        // Test setting new configuration
        ValidationConfig newConfig = new ValidationConfig.Builder()
            .enableCaching(false)
            .failFast(false)
            .build();

        validator.setConfig(newConfig);
        assertEquals(newConfig, validator.getConfig());
        assertFalse(validator.getConfig().isEnableCaching());
        assertFalse(validator.getConfig().isFailFast());
    }

    @Test
    @DisplayName("Test ID extraction")
    void testIdExtraction() {
        String message1 = "{\"jsonrpc\":\"2.0\",\"id\":\"test-123\"}";
        String message2 = "{\"jsonrpc\":\"2.0\"}"; // No ID

        assertEquals("test-123", validator.extractId(message1));
        assertNull(validator.extractId(message2));
    }
}