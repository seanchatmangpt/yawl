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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.integration.validation.schema.JsonSchemaValidator;
import org.yawlfoundation.yawl.integration.validation.schema.SchemaValidationError;
import org.yawlfoundation.yawl.integration.validation.schema.ValidationConfig;

import java.util.Map;

/**
 * MCP protocol message validator.
 *
 * <p>Provides specialized validation for MCP protocol messages, including
 * tool calls, resource operations, prompt generation, and completion requests.
 * Includes YAWL-specific validation rules and business logic checks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see JsonSchemaValidator
 */
public class MCPSchemaValidator {

    private final JsonSchemaValidator validator;
    private final ValidationConfig config;

    /**
     * Creates a new MCP schema validator with default configuration.
     */
    public MCPSchemaValidator() {
        this(new JsonSchemaValidator(), ValidationConfig.getDefault());
    }

    /**
     * Creates a new MCP schema validator with custom configuration.
     *
     * @param validator the underlying JSON schema validator
     * @param config validation configuration
     */
    public MCPSchemaValidator(JsonSchemaValidator validator, ValidationConfig config) {
        this.validator = validator != null ? validator : new JsonSchemaValidator();
        this.config = config != null ? config : ValidationConfig.getDefault();
    }

    /**
     * Validates an MCP tool call request.
     *
     * @param json the tool call JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateToolCall(String json) throws SchemaValidationError {
        return validator.validateMCP(json, "tool-call");
    }

    /**
     * Validates an MCP tool call result.
     *
     * @param json the tool result JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateToolResult(String json) throws SchemaValidationError {
        return validator.validateMCP(json, "tool-result");
    }

    /**
     * Validates an MCP tool specification.
     *
     * @param json the tool specification JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateToolSpecification(String json) throws SchemaValidationError {
        return validator.validate(json, "schemas/mcp/tool-spec.json");
    }

    /**
     * Validates an MCP resource read request.
     *
     * @param json the resource read request JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateResourceRead(String json) throws SchemaValidationError {
        return validator.validateMCP(json, "resource-read");
    }

    /**
     * Validates an MCP prompt request.
     *
     * @param json the prompt request JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validatePromptRequest(String json) throws SchemaValidationError {
        return validator.validateMCP(json, "prompt-request");
    }

    /**
     * Validates an MCP completion request.
     *
     * @param json the completion request JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateCompletionRequest(String json) throws SchemaValidationError {
        return validator.validateMCP(json, "completion-request");
    }

    /**
     * Validates server capability declarations.
     *
     * @param json the server capabilities JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateServerCapabilities(String json) throws SchemaValidationError {
        return validator.validateMCP(json, "server-info");
    }

    /**
     * Validates a YAWL case ID format.
     *
     * @param caseId the case ID to validate
     * @return true if valid, false otherwise
     */
    public boolean validateCaseId(String caseId) {
        if (caseId == null || caseId.trim().isEmpty()) {
            return false;
        }
        return caseId.matches("^[A-Za-z0-9]{8,32}$");
    }

    /**
     * Validates a YAWL work item ID format.
     *
     * @param workItemId the work item ID to validate
     * @return true if valid, false otherwise
     */
    public boolean validateWorkItemId(String workItemId) {
        if (workItemId == null || workItemId.trim().isEmpty()) {
            return false;
        }
        return workItemId.matches("^WI-[A-Za-z0-9]{1,10}$");
    }

    /**
     * Validates a YAWL specification ID format.
     *
     * @param specId the specification ID to validate
     * @return true if valid, false otherwise
     */
    public boolean validateSpecificationId(String specId) {
        if (specId == null || specId.trim().isEmpty()) {
            return false;
        }
        return specId.matches("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");
    }

    /**
     * Validates a timeout value.
     *
     * @param timeout timeout value in milliseconds
     * @return true if valid, false otherwise
     */
    public boolean validateTimeout(int timeout) {
        return timeout >= 1000 && timeout <= 300000; // 1 second to 5 minutes
    }

    /**
     * Validates tool call business rules.
     *
     * @param jsonNode the parsed tool call JSON
     * @throws SchemaValidationError if business rules are violated
     */
    public void validateToolCallBusinessRules(JsonNode jsonNode) throws SchemaValidationError {
        // Validate method
        JsonNode methodNode = jsonNode.get("method");
        if (methodNode != null) {
            String method = methodNode.asText();
            if (!isValidToolMethod(method)) {
                throw new SchemaValidationError(
                    "Invalid tool method",
                    SchemaValidationError.ErrorType.VALIDATION_ERROR,
                    "/method",
                    "Method must be one of: launch_workflow, query_workflows, manage_workitems, cancel_workflow",
                    Map.of("invalidMethod", method)
                );
            }
        }

        // Validate parameters based on method
        JsonNode paramsNode = jsonNode.get("params");
        if (paramsNode != null) {
            String method = methodNode != null ? methodNode.asText() : "";
            validateToolParameters(paramsNode, method);
        }

        // Validate options if present
        if (paramsNode != null && paramsNode.has("options")) {
            JsonNode optionsNode = paramsNode.get("options");
            if (optionsNode.has("timeout")) {
                int timeout = optionsNode.get("timeout").asInt();
                if (!validateTimeout(timeout)) {
                    throw new SchemaValidationError(
                        "Invalid timeout value",
                        SchemaValidationError.ErrorType.VALIDATION_ERROR,
                        "/params/options/timeout",
                        "Timeout must be between 1000 and 300000 milliseconds",
                        Map.of("invalidTimeout", timeout)
                    );
                }
            }
        }
    }

    /**
     * Validates that a tool method is supported.
     *
     * @param method the tool method name
     * @return true if supported, false otherwise
     */
    private boolean isValidToolMethod(String method) {
        return method != null && (
            method.equals("launch_workflow") ||
            method.equals("query_workflows") ||
            method.equals("manage_workitems") ||
            method.equals("cancel_workflow")
        );
    }

    /**
     * Validates tool parameters based on the method type.
     *
     * @param params the parameters JSON node
     * @param method the tool method name
     * @throws SchemaValidationError if parameters are invalid
     */
    private void validateToolParameters(JsonNode params, String method) throws SchemaValidationError {
        switch (method) {
            case "launch_workflow":
                if (!params.has("specificationId")) {
                    throw new SchemaValidationError(
                        "Missing required parameter",
                        SchemaValidationError.ErrorType.VALIDATION_ERROR,
                        "/params/specificationId",
                        "specificationId is required for launch_workflow",
                        Map.of("method", method)
                    );
                }
                if (!validateSpecificationId(params.get("specificationId").asText())) {
                    throw new SchemaValidationError(
                        "Invalid specification ID format",
                        SchemaValidationError.ErrorType.VALIDATION_ERROR,
                        "/params/specificationId",
                        "Specification ID must be alphanumeric with optional hyphens and underscores",
                        Map.of("invalidSpecId", params.get("specificationId").asText())
                    );
                }
                break;

            case "manage_workitems":
                if (!params.has("workItemId")) {
                    throw new SchemaValidationError(
                        "Missing required parameter",
                        SchemaValidationError.ErrorType.VALIDATION_ERROR,
                        "/params/workItemId",
                        "workItemId is required for manage_workitems",
                        Map.of("method", method)
                    );
                }
                if (!validateWorkItemId(params.get("workItemId").asText())) {
                    throw new SchemaValidationError(
                        "Invalid work item ID format",
                        SchemaValidationError.ErrorType.VALIDATION_ERROR,
                        "/params/workItemId",
                        "Work item ID must be in format WI-XXXXXXXX",
                        Map.of("invalidWorkItemId", params.get("workItemId").asText())
                    );
                }
                break;

            case "cancel_workflow":
                if (!params.has("caseId")) {
                    throw new SchemaValidationError(
                        "Missing required parameter",
                        SchemaValidationError.ErrorType.VALIDATION_ERROR,
                        "/params/caseId",
                        "caseId is required for cancel_workflow",
                        Map.of("method", method)
                    );
                }
                if (!validateCaseId(params.get("caseId").asText())) {
                    throw new SchemaValidationError(
                        "Invalid case ID format",
                        SchemaValidationError.ErrorType.VALIDATION_ERROR,
                        "/params/caseId",
                        "Case ID must be alphanumeric and 8-32 characters long",
                        Map.of("invalidCaseId", params.get("caseId").asText())
                    );
                }
                break;
        }
    }

    /**
     * Validates a complete MCP workflow.
     *
     * @param toolCall the tool call JSON
     * @param toolResult the tool result JSON
     * @return comprehensive validation result
     * @throws SchemaValidationError if any validation fails
     */
    public JsonSchemaValidator.ValidationResult validateCompleteWorkflow(
            String toolCall, String toolResult) throws SchemaValidationError {

        // Validate individual components
        JsonSchemaValidator.ValidationResult callResult = validateToolCall(toolCall);
        JsonSchemaValidator.ValidationResult resultResult = validateToolResult(toolResult);

        // Extract IDs for correlation
        String callId = extractId(toolCall);
        String resultId = extractId(toolResult);

        // Validate that call and result IDs match
        if (callId != null && resultId != null && !callId.equals(resultId)) {
            throw new SchemaValidationError(
                "Mismatched call and result IDs",
                SchemaValidationError.ErrorType.VALIDATION_ERROR,
                null,
                "Ensure the JSON-RPC ID matches between call and response",
                Map.of("callId", callId, "resultId", resultId)
            );
        }

        // Create combined result
        Map<String, Object> metadata = Map.of(
            "callValid", callResult.isValid(),
            "resultValid", resultResult.isValid(),
            "correlatedIds", callId != null && callId.equals(resultId),
            "callId", callId,
            "resultId", resultId
        );

        if (!callResult.isValid() || !resultResult.isValid()) {
            // Return failure with details
            return JsonSchemaValidator.ValidationResult.failure(
                new SchemaValidationError(
                    "MCP workflow validation failed",
                    SchemaValidationError.ErrorType.VALIDATION_ERROR,
                    null,
                    "Check both the tool call and result",
                    metadata
                )
            );
        }

        return JsonSchemaValidator.ValidationResult.success(metadata);
    }

    /**
     * Extracts the JSON-RPC ID from a message.
     *
     * @param json the JSON message
     * @return the ID, or null if not present
     */
    private String extractId(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(json);
            return node.has("id") ? node.get("id").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the validation configuration.
     *
     * @return current configuration
     */
    public ValidationConfig getConfig() {
        return config;
    }

    /**
     * Sets a new validation configuration.
     *
     * @param config new configuration
     */
    public void setConfig(ValidationConfig config) {
        if (config != null) {
            this.config = config;
        }
    }
}