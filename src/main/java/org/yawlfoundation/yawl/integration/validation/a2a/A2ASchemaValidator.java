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

package org.yawlfoundation.yawl.integration.validation.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import org.yawlfoundation.yawl.integration.validation.schema.JsonSchemaValidator;
import org.yawlfoundation.yawl.integration.validation.schema.SchemaValidationError;
import org.yawlfoundation.yawl.integration.validation.schema.ValidationConfig;

import java.util.Map;

/**
 * A2A protocol message validator.
 *
 * <p>Provides specialized validation for A2A protocol messages, including
 * handoff messages, agent cards, and other A2A-specific structures.
 * Integrates with the core validation framework with A2A-specific optimizations.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see JsonSchemaValidator
 */
public class A2ASchemaValidator {

    private final JsonSchemaValidator validator;
    private final ValidationConfig config;

    /**
     * Creates a new A2A schema validator with default configuration.
     */
    public A2ASchemaValidator() {
        this(new JsonSchemaValidator(), ValidationConfig.getDefault());
    }

    /**
     * Creates a new A2A schema validator with custom configuration.
     *
     * @param validator the underlying JSON schema validator
     * @param config validation configuration
     */
    public A2ASchemaValidator(JsonSchemaValidator validator, ValidationConfig config) {
        this.validator = validator != null ? validator : new JsonSchemaValidator();
        this.config = config != null ? config : ValidationConfig.getDefault();
    }

    /**
     * Validates an A2A handoff message.
     *
     * @param json the handoff message JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateHandoffMessage(String json) throws SchemaValidationError {
        return validator.validateA2A(json, "handoff-message");
    }

    /**
     * Validates an A2A handoff message with additional business rule validation.
     *
     * @param json the handoff message JSON
     * @return validation result with business rule checks
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateHandoffMessageWithRules(String json) throws SchemaValidationError {
        JsonSchemaValidator.ValidationResult result = validateHandoffMessage(json);

        if (!result.isValid()) {
            return result;
        }

        // Additional business rule validation
        JsonNode jsonNode = result.getMetadata().get("parsedJson");
        if (jsonNode != null) {
            validateHandoffBusinessRules(jsonNode);
        }

        return result;
    }

    /**
     * Validates an A2A agent card.
     *
     * @param json the agent card JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateAgentCard(String json) throws SchemaValidationError {
        return validator.validateA2A(json, "agent-card");
    }

    /**
     * Validates an A2A task message.
     *
     * @param json the task message JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateTaskMessage(String json) throws SchemaValidationError {
        return validator.validate(json, "schemas/a2a/task-message.json");
    }

    /**
     * Validates A2A authentication credentials.
     *
     * @param json the authentication JSON
     * @return validation result
     * @throws SchemaValidationError if validation fails
     */
    public JsonSchemaValidator.ValidationResult validateAuthentication(String json) throws SchemaValidationError {
        return validator.validate(json, "schemas/a2a/authentication.json");
    }

    /**
     * Validates a work item ID format.
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
     * Validates an agent ID format.
     *
     * @param agentId the agent ID to validate
     * @return true if valid, false otherwise
     */
    public boolean validateAgentId(String agentId) {
        if (agentId == null || agentId.trim().isEmpty()) {
            return false;
        }
        return agentId.matches("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$");
    }

    /**
     * Validates a session handle format.
     *
     * @param sessionHandle the session handle to validate
     * @return true if valid, false otherwise
     */
    public boolean validateSessionHandle(String sessionHandle) {
        if (sessionHandle == null || sessionHandle.trim().isEmpty()) {
            return false;
        }
        return sessionHandle.matches("^[a-zA-Z0-9]{8,32}$");
    }

    /**
     * Validates handoff business rules.
     *
     * @param jsonNode the parsed handoff message JSON
     * @throws SchemaValidationError if business rules are violated
     */
    private void validateHandoffBusinessRules(JsonNode jsonNode) throws SchemaValidationError {
        // Check that fromAgent and toAgent are different
        JsonNode parts = jsonNode.get("parts");
        if (parts != null && parts.isArray() && parts.size() >= 1) {
            JsonNode firstPart = parts.get(0);
            if (firstPart.has("text")) {
                String text = firstPart.get("text").asText();
                if (text.startsWith("YAWL_HANDOFF:")) {
                    String[] partsArray = text.substring("YAWL_HANDOFF:".length()).split(":");
                    if (partsArray.length >= 3) {
                        String fromAgent = partsArray[2];
                        JsonNode dataPart = null;

                        // Look for data part
                        for (JsonNode part : parts) {
                            if (part.has("type") && "data".equals(part.get("type").asText())) {
                                dataPart = part.get("data");
                                break;
                            }
                        }

                        if (dataPart != null && dataPart.has("toAgent")) {
                            String toAgent = dataPart.get("toAgent").asText();
                            if (fromAgent.equals(toAgent)) {
                                throw new SchemaValidationError(
                                    "Handoff source and target agents cannot be the same",
                                    SchemaValidationError.ErrorType.VALIDATION_ERROR,
                                    "/parts/1/data/toAgent",
                                    "Ensure the target agent is different from the source agent",
                                    Map.of("fromAgent", fromAgent, "toAgent", toAgent)
                                );
                            }
                        }
                    }
                }
            }
        }

        // Check for required metadata in data part if present
        if (parts != null && parts.isArray() && parts.size() == 2) {
            JsonNode dataPart = parts.get(1);
            if (dataPart != null && dataPart.has("data")) {
                JsonNode data = dataPart.get("data");
                if (data != null) {
                    // Priority must be valid if present
                    if (data.has("priority")) {
                        String priority = data.get("priority").asText();
                        if (!priority.matches("^(low|normal|high|critical)$")) {
                            throw new SchemaValidationError(
                                "Invalid priority value",
                                SchemaValidationError.ErrorType.VALIDATION_ERROR,
                                "/parts/1/data/priority",
                                "Priority must be one of: low, normal, high, critical",
                                Map.of("invalidValue", priority)
                            );
                        }
                    }
                }
            }
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

    /**
     * Validates a complete A2A message workflow.
     *
     * @param handoffMessage the handoff message
     * @param agentCard the source agent card
     * @param targetAgentId the target agent ID
     * @return comprehensive validation result
     * @throws SchemaValidationError if any validation fails
     */
    public JsonSchemaValidator.ValidationResult validateCompleteWorkflow(
            String handoffMessage, String agentCard, String targetAgentId) throws SchemaValidationError {

        // Validate individual components
        JsonSchemaValidator.ValidationResult messageResult = validateHandoffMessage(handoffMessage);
        JsonSchemaValidator.ValidationResult cardResult = validateAgentCard(agentCard);

        // Validate target agent ID format
        if (!validateAgentId(targetAgentId)) {
            throw new SchemaValidationError(
                "Invalid target agent ID format",
                SchemaValidationError.ErrorType.VALIDATION_ERROR,
                null,
                "Agent ID must be alphanumeric with optional hyphens and underscores",
                Map.of("invalidAgentId", targetAgentId)
            );
        }

        // Create combined result
        Map<String, Object> metadata = Map.of(
            "handoffValid", messageResult.isValid(),
            "agentCardValid", cardResult.isValid(),
            "targetAgentId", targetAgentId
        );

        if (!messageResult.isValid() || !cardResult.isValid()) {
            // Return failure with details
            return JsonSchemaValidator.ValidationResult.failure(
                new SchemaValidationError(
                    "A2A workflow validation failed",
                    SchemaValidationError.ErrorType.VALIDATION_ERROR,
                    null,
                    "Check both the handoff message and agent card",
                    metadata
                )
            );
        }

        return JsonSchemaValidator.ValidationResult.success(metadata);
    }
}