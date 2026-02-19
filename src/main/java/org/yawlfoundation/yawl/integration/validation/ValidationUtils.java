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

package org.yawlfoundation.yawl.integration.validation;

import org.yawlfoundation.yawl.integration.validation.a2a.A2ASchemaValidator;
import org.yawlfoundation.yawl.integration.validation.mcp.MCPSchemaValidator;
import org.yawlfoundation.yawl.integration.validation.schema.JsonSchemaValidator;
import org.yawlfoundation.yawl.integration.validation.schema.SchemaRegistry;
import org.yawlfoundation.yawl.integration.validation.schema.SchemaValidationError;
import org.yawlfoundation.yawl.integration.validation.schema.ValidationConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validation utilities for YAWL protocol messages.
 *
 * <p>Provides convenient methods for validating A2A and MCP protocol messages,
 * with built-in caching and performance optimizations for common validation scenarios.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see A2ASchemaValidator
 * @see MCPSchemaValidator
 * @see SchemaRegistry
 */
public class ValidationUtils {

    private static final ValidationUtils INSTANCE = new ValidationUtils();

    private final A2ASchemaValidator a2aValidator;
    private final MCPSchemaValidator mcpValidator;
    private final SchemaRegistry schemaRegistry;
    private final Map<String, Object> validationCache;

    private ValidationUtils() {
        this.schemaRegistry = SchemaRegistry.getInstance();
        this.a2aValidator = new A2ASchemaValidator();
        this.mcpValidator = new MCPSchemaValidator();
        this.validationCache = new ConcurrentHashMap<>();
    }

    /**
     * Gets the singleton instance of validation utilities.
     *
     * @return validation utilities instance
     */
    public static ValidationUtils getInstance() {
        return INSTANCE;
    }

    /**
     * Validates an A2A handoff message.
     *
     * @param json the handoff message JSON
     * @return true if valid, false otherwise
     */
    public boolean validateA2AHandoff(String json) {
        try {
            JsonSchemaValidator.ValidationResult result = a2aValidator.validateHandoffMessage(json);
            return result.isValid();
        } catch (SchemaValidationError e) {
            return false;
        }
    }

    /**
     * Validates an A2A handoff message with business rules.
     *
     * @param json the handoff message JSON
     * @return true if valid with business rules, false otherwise
     */
    public boolean validateA2AHandoffWithRules(String json) {
        try {
            JsonSchemaValidator.ValidationResult result = a2aValidator.validateHandoffMessageWithRules(json);
            return result.isValid();
        } catch (SchemaValidationError e) {
            return false;
        }
    }

    /**
     * Validates an A2A agent card.
     *
     * @param json the agent card JSON
     * @return true if valid, false otherwise
     */
    public boolean validateA2AAgentCard(String json) {
        try {
            JsonSchemaValidator.ValidationResult result = a2aValidator.validateAgentCard(json);
            return result.isValid();
        } catch (SchemaValidationError e) {
            return false;
        }
    }

    /**
     * Validates an MCP tool call.
     *
     * @param json the tool call JSON
     * @return true if valid, false otherwise
     */
    public boolean validateMCPToolCall(String json) {
        try {
            JsonSchemaValidator.ValidationResult result = mcpValidator.validateToolCall(json);
            return result.isValid();
        } catch (SchemaValidationError e) {
            return false;
        }
    }

    /**
     * Validates an MCP tool result.
     *
     * @param json the tool result JSON
     * @return true if valid, false otherwise
     */
    public boolean validateMCPToolResult(String json) {
        try {
            JsonSchemaValidator.ValidationResult result = mcpValidator.validateToolResult(json);
            return result.isValid();
        } catch (SchemaValidationError e) {
            return false;
        }
    }

    /**
     * Validates a YAWL work item ID format.
     *
     * @param workItemId the work item ID to validate
     * @return true if valid, false otherwise
     */
    public boolean validateWorkItemId(String workItemId) {
        return a2aValidator.validateWorkItemId(workItemId);
    }

    /**
     * Validates an A2A agent ID format.
     *
     * @param agentId the agent ID to validate
     * @return true if valid, false otherwise
     */
    public boolean validateAgentId(String agentId) {
        return a2aValidator.validateAgentId(agentId);
    }

    /**
     * Validates a YAWL case ID format.
     *
     * @param caseId the case ID to validate
     * @return true if valid, false otherwise
     */
    public boolean validateCaseId(String caseId) {
        return mcpValidator.validateCaseId(caseId);
    }

    /**
     * Validates a YAWL specification ID format.
     *
     * @param specId the specification ID to validate
     * @return true if valid, false otherwise
     */
    public boolean validateSpecificationId(String specId) {
        return mcpValidator.validateSpecificationId(specId);
    }

    /**
     * Validates a timeout value.
     *
     * @param timeout timeout value in milliseconds
     * @return true if valid, false otherwise
     */
    public boolean validateTimeout(int timeout) {
        return mcpValidator.validateTimeout(timeout);
    }

    /**
     * Validates a complete A2A workflow.
     *
     * @param handoffMessage the handoff message JSON
     * @param agentCard the agent card JSON
     * @param targetAgentId the target agent ID
     * @return true if complete workflow is valid, false otherwise
     */
    public boolean validateCompleteA2AWorkflow(String handoffMessage, String agentCard, String targetAgentId) {
        try {
            JsonSchemaValidator.ValidationResult result = a2aValidator.validateCompleteWorkflow(
                handoffMessage, agentCard, targetAgentId);
            return result.isValid();
        } catch (SchemaValidationError e) {
            return false;
        }
    }

    /**
     * Validates a complete MCP workflow.
     *
     * @param toolCall the tool call JSON
     * @param toolResult the tool result JSON
     * @return true if complete workflow is valid, false otherwise
     */
    public boolean validateCompleteMCPWorkflow(String toolCall, String toolResult) {
        try {
            JsonSchemaValidator.ValidationResult result = mcpValidator.validateCompleteWorkflow(
                toolCall, toolResult);
            return result.isValid();
        } catch (SchemaValidationError e) {
            return false;
        }
    }

    /**
     * Validates JSON against a specific schema.
     *
     * @param json the JSON to validate
     * @param schemaPath path to the schema resource
     * @return true if valid, false otherwise
     */
    public boolean validateJson(String json, String schemaPath) {
        try {
            JsonSchemaValidator.ValidationResult result = schemaRegistry.getSchema(schemaPath)
                .validate(new com.fasterxml.jackson.databind.ObjectMapper().readTree(json));
            return result.isValid();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates multiple JSON documents against a schema.
     *
     * @param documents map of document IDs to JSON strings
     * @param schemaPath path to the schema resource
     * @return map of document IDs to validation results
     */
    public Map<String, Boolean> validateBatch(Map<String, String> documents, String schemaPath) {
        Map<String, Boolean> results = new ConcurrentHashMap<>();

        documents.forEach((docId, json) -> {
            try {
                boolean isValid = validateJson(json, schemaPath);
                results.put(docId, isValid);
            } catch (Exception e) {
                results.put(docId, false);
            }
        });

        return results;
    }

    /**
     * Gets validation statistics.
     *
     * @return map of validation metrics
     */
    public Map<String, Object> getValidationStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();

        stats.put("cachedSchemas", schemaRegistry.getCachedSchemaCount());
        stats.put("a2aValidator", Map.of(
            "configured", a2aValidator != null,
            "config", a2aValidator.getConfig()
        ));
        stats.put("mcpValidator", Map.of(
            "configured", mcpValidator != null,
            "config", mcpValidator.getConfig()
        ));

        return stats;
    }

    /**
     * Clears validation cache.
     */
    public void clearCache() {
        validationCache.clear();
        schemaRegistry.invalidateAll();
    }

    /**
     * Gets the validation configuration.
     *
     * @return current validation configuration
     */
    public ValidationConfig getValidationConfig() {
        return schemaRegistry.getConfig();
    }

    /**
     * Sets a new validation configuration.
     *
     * @param config new configuration
     */
    public void setValidationConfig(ValidationConfig config) {
        if (config != null) {
            schemaRegistry.setConfig(config);
            a2aValidator.setConfig(config);
            mcpValidator.setConfig(config);
        }
    }

    /**
     * Creates a custom validation configuration.
     *
     * @param builder function to modify configuration
     * @return new validation configuration
     */
    public ValidationConfig createConfig(ValidationConfig.Builder builder) {
        return builder.build();
    }

    /**
     * Validates and sanitizes a JSON string.
     *
     * @param json the JSON to validate and sanitize
     * @param schemaPath path to the validation schema
     * @return sanitized JSON string, or null if validation fails
     */
    public String validateAndSanitize(String json, String schemaPath) {
        try {
            if (!validateJson(json, schemaPath)) {
                return null;
            }

            // Parse and re-stringify to sanitize
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json)
                );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if a validation result contains critical errors.
     *
     * @param result the validation result
     * @return true if critical, false otherwise
     */
    public boolean isCriticalError(JsonSchemaValidator.ValidationResult result) {
        if (result == null || result.isValid()) {
            return false;
        }

        // Check for schema or system errors
        SchemaValidationError error = result.getError();
        if (error == null) {
            return false;
        }

        return error.isSystemError() || error.isSchemaError();
    }

    /**
     * Gets error message from validation result.
     *
     * @param result the validation result
     * @return formatted error message, or success message if valid
     */
    public String getValidationMessage(JsonSchemaValidator.ValidationResult result) {
        if (result == null) {
            return "No validation result";
        }

        if (result.isValid()) {
            return "Validation successful";
        }

        SchemaValidationError error = result.getError();
        if (error != null) {
            StringBuilder message = new StringBuilder();
            message.append("Validation failed: ").append(error.getMessage());

            if (error.getJsonPointer() != null) {
                message.append(" (at: ").append(error.getJsonPointer()).append(")");
            }

            if (error.getSuggestedFix() != null) {
                message.append("\nSuggested fix: ").append(error.getSuggestedFix());
            }

            return message.toString();
        }

        return "Validation failed with unknown error";
    }

    /**
     * Validates protocol version compatibility.
     *
     * @param protocolVersion the protocol version to check
     * @param requiredVersion the required version
     * @return true if compatible, false otherwise
     */
    public boolean validateProtocolVersion(String protocolVersion, String requiredVersion) {
        if (protocolVersion == null || requiredVersion == null) {
            return false;
        }

        // Simple version comparison (major.minor.patch)
        String[] protocolParts = protocolVersion.split("\\.");
        String[] requiredParts = requiredVersion.split("\\.");

        // Compare major version
        if (protocolParts.length > 0 && requiredParts.length > 0) {
            try {
                int majorProtocol = Integer.parseInt(protocolParts[0]);
                int majorRequired = Integer.parseInt(requiredParts[0]);

                if (majorProtocol < majorRequired) {
                    return false;
                }
                if (majorProtocol > majorRequired) {
                    return true; // Higher major version is compatible
                }

                // Compare minor version if major versions match
                if (protocolParts.length > 1 && requiredParts.length > 1) {
                    int minorProtocol = Integer.parseInt(protocolParts[1]);
                    int minorRequired = Integer.parseInt(requiredParts[1]);

                    return minorProtocol >= minorRequired;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return false;
    }
}