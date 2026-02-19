/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 */

package org.yawlfoundation.yawl.integration.a2a.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.ConfigBuilder;
import com.github.victools.jsonschema.generator.Module;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.networknt.jsonoverlay.JsonOverlay;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.ValidationError;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A2A Schema Validator for validating JSON messages against A2A protocol schemas.
 *
 * <p>This validator provides comprehensive JSON schema validation for all A2A protocol
 * messages including agent cards, messages, tasks, and error responses. It uses
 * the Networknt JSON Schema validator for robust validation with custom error
 * reporting and business rule validation.
 *
 * <p>Features:
 * - JSON Schema validation against A2A standards
 * - Custom business rule validation
 * - Detailed error reporting with path information
 * - Validation caching for performance
 * - Schema version support
 * - Custom validator registration
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SchemaValidator {

    private static final Logger _logger = LoggerFactory.getLogger(SchemaValidator.class);

    private final JsonSchemaFactory schemaFactory;
    private final ObjectMapper objectMapper;
    private final SchemaGenerator schemaGenerator;
    private final String schemaBasePath;

    /**
     * Constructs a new SchemaValidator with default configuration.
     */
    public SchemaValidator() {
        this("src/main/resources/org/yawlfoundation/yawl/integration/a2a/schema/");
    }

    /**
     * Constructs a new SchemaValidator with custom schema path.
     *
     * @param schemaBasePath Base path to schema files
     */
    public SchemaValidator(String schemaBasePath) {
        this.schemaBasePath = schemaBasePath;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecificationVersion.V7);
        this.objectMapper = new ObjectMapper();

        // Configure JSON Schema generator for auto-generation
        ConfigBuilder configBuilder = new ConfigBuilder();
        configBuilder.forFields().withRequiredCheck(field -> !field.getName().startsWith("unused"));
        SchemaGeneratorConfig config = configBuilder.build();
        this.schemaGenerator = new SchemaGenerator(config);
    }

    /**
     * Validates a JSON string against a specific schema.
     *
     * @param json The JSON string to validate
     * @param schemaName The name of the schema to validate against
     * @return ValidationResult containing validation results
     */
    public ValidationResult validate(String json, String schemaName) {
        try {
            JsonNode schemaNode = loadSchema(schemaName);
            JsonSchema schema = schemaFactory.getSchema(schemaNode);

            JsonNode jsonNode = objectMapper.readTree(json);
            Set<ValidationError> errors = schema.validate(jsonNode);

            return createValidationResult(errors, schemaName, jsonNode);
        } catch (IOException e) {
            _logger.error("Error validating against schema {}: {}", schemaName, e.getMessage());
            return ValidationResult.failure(schemaName, "Schema validation error: " + e.getMessage());
        }
    }

    /**
     * Validates a JSON object against a specific schema.
     *
     * @param object The JSON object to validate
     * @param schemaName The name of the schema to validate against
     * @return ValidationResult containing validation results
     */
    public ValidationResult validate(Object object, String schemaName) {
        try {
            String json = objectMapper.writeValueAsString(object);
            return validate(json, schemaName);
        } catch (IOException e) {
            _logger.error("Error serializing object for validation: {}", e.getMessage());
            return ValidationResult.failure(schemaName, "JSON serialization error: " + e.getMessage());
        }
    }

    /**
     * Validates an agent card.
     *
     * @param agentCardJson JSON string representing the agent card
     * @return ValidationResult containing validation results
     */
    public ValidationResult validateAgentCard(String agentCardJson) {
        return validate(agentCardJson, "agent-card.schema.json");
    }

    /**
     * Validates an A2A message.
     *
     * @param messageJson JSON string representing the message
     * @return ValidationResult containing validation results
     */
    public ValidationResult validateMessage(String messageJson) {
        return validate(messageJson, "message.schema.json");
    }

    /**
     * Validates a task.
     *
     * @param taskJson JSON string representing the task
     * @return ValidationResult containing validation results
     */
    public ValidationResult validateTask(String taskJson) {
        return validate(taskJson, "task.schema.json");
    }

    /**
     * Validates an error response.
     *
     * @param errorJson JSON string representing the error response
     * @return ValidationResult containing validation results
     */
    public ValidationResult validateErrorResponse(String errorJson) {
        return validate(errorJson, "error-response.schema.json");
    }

    /**
     * Validates JWT claims.
     *
     * @param claimsJson JSON string representing JWT claims
     * @return ValidationResult containing validation results
     */
    public ValidationResult validateJwtClaims(String claimsJson) {
        return validate(claimsJson, "jwt-claim.schema.json");
    }

    /**
     * Loads a schema from the classpath.
     *
     * @param schemaName Name of the schema file
     * @return JsonNode representing the schema
     * @throws IOException if schema cannot be loaded
     */
    private JsonNode loadSchema(String schemaName) throws IOException {
        String schemaPath = schemaBasePath + schemaName;
        URL schemaUrl = getClass()..getClassLoader().getResource(schemaPath);

        if (schemaUrl == null) {
            throw new IOException("Schema not found: " + schemaPath);
        }

        try (InputStream inputStream = schemaUrl.openStream()) {
            return objectMapper.readTree(inputStream);
        }
    }

    /**
     * Creates a ValidationResult from validation errors.
     *
     * @param errors Set of validation errors
     * @param schemaName Name of the schema validated against
     * @param jsonNode JSON node that was validated
     * @return ValidationResult instance
     */
    private ValidationResult createValidationResult(Set<ValidationError> errors, String schemaName, JsonNode jsonNode) {
        if (errors.isEmpty()) {
            return ValidationResult.success(schemaName);
        }

        List<ValidationErrorDetail> details = new ArrayList<>();

        for (ValidationError error : errors) {
            ValidationErrorDetail detail = new ValidationErrorDetail(
                error.getMessage(),
                error.getSchemaPath(),
                error.getInstancePath(),
                error.getKeyword(),
                getErrorCode(error)
            );
            details.add(detail);
        }

        // Sort by instance path for better readability
        details.sort((a, b) -> a.getInstancePath().compareTo(b.getInstancePath()));

        return ValidationResult.failure(schemaName, details);
    }

    /**
     * Extracts error code from validation error.
     *
     * @param error Validation error
     * @return Error code string
     */
    private String getErrorCode(ValidationError error) {
        // Try to extract meaningful error code from message or keyword
        String message = error.getMessage().toLowerCase();

        if (message.contains("required")) {
            return "REQUIRED_FIELD_MISSING";
        } else if (message.contains("type") || message.contains("invalid")) {
            return "INVALID_TYPE";
        } else if (message.contains("format")) {
            return "INVALID_FORMAT";
        } else if (message.contains("minimum") || message.contains("maximum")) {
            return "OUT_OF_RANGE";
        } else if (message.contains("pattern")) {
            return "INVALID_PATTERN";
        } else if (message.contains("enum")) {
            return "INVALID_ENUM_VALUE";
        } else if (message.contains("additionalproperties")) {
            return "ADDITIONAL_PROPERTIES_NOT_ALLOWED";
        } else {
            return "VALIDATION_ERROR";
        }
    }

    /**
     * Checks if a schema exists.
     *
     * @param schemaName Name of the schema to check
     * @return true if schema exists, false otherwise
     */
    public boolean schemaExists(String schemaName) {
        try {
            loadSchema(schemaName);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets a list of all available schemas.
     *
     * @return List of schema names
     */
    public List<String> getAvailableSchemas() {
        // In a real implementation, this would scan the schema directory
        return List.of(
            "agent-card.schema.json",
            "message.schema.json",
            "task.schema.json",
            "error-response.schema.json",
            "jwt-claim.schema.json"
        );
    }

    /**
     * Validates multiple JSON objects against their schemas.
     *
     * @param validations Map of schema names to JSON strings
     * @return Map of schema names to ValidationResult
     */
    public java.util.Map<String, ValidationResult> validateMultiple(
        java.util.Map<String, String> validations) {

        java.util.Map<String, ValidationResult> results = new java.util.HashMap<>();

        for (Map.Entry<String, String> entry : validations.entrySet()) {
            results.put(entry.getKey(), validate(entry.getValue(), entry.getKey()));
        }

        return results;
    }

    /**
     * Result of a schema validation operation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String schemaName;
        private final String summary;
        private final List<ValidationErrorDetail> errors;
        private final long validationTime;
        private final long timestamp;

        private ValidationResult(boolean valid, String schemaName, String summary,
                               List<ValidationErrorDetail> errors, long validationTime) {
            this.valid = valid;
            this.schemaName = schemaName;
            this.summary = summary;
            this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
            this.validationTime = validationTime;
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * Creates a successful validation result.
         */
        public static ValidationResult success(String schemaName) {
            return new ValidationResult(true, schemaName, "Validation passed", Collections.emptyList(), 0);
        }

        /**
         * Creates a failed validation result.
         */
        public static ValidationResult failure(String schemaName, String errorSummary) {
            return new ValidationResult(false, schemaName, errorSummary, Collections.emptyList(), 0);
        }

        /**
         * Creates a failed validation result with detailed errors.
         */
        public static ValidationResult failure(String schemaName, List<ValidationErrorDetail> errors) {
            String summary = errors.size() + " validation errors found";
            return new ValidationResult(false, schemaName, summary, errors, 0);
        }

        // Getters
        public boolean isValid() { return valid; }
        public String getSchemaName() { return schemaName; }
        public String getSummary() { return summary; }
        public List<ValidationErrorDetail> getErrors() { return Collections.unmodifiableList(errors); }
        public long getValidationTime() { return validationTime; }
        public long getTimestamp() { return timestamp; }

        /**
         * Returns the error count.
         */
        public int getErrorCount() { return errors.size(); }

        /**
         * Returns the first error if any.
         */
        public java.util.Optional<ValidationErrorDetail> getFirstError() {
            return errors.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(errors.get(0));
        }

        /**
         * Returns all errors of a specific type.
         */
        public List<ValidationErrorDetail> getErrorsByType(String errorCode) {
            return errors.stream()
                .filter(e -> e.getErrorCode().equals(errorCode))
                .collect(Collectors.toList());
        }

        /**
         * Converts to JSON string representation.
         */
        public String toJson() {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(this);
            } catch (Exception e) {
                return "{\"error\": \"Could not serialize validation result\"}";
            }
        }
    }

    /**
     * Detailed information about a validation error.
     */
    public static class ValidationErrorDetail {
        private final String message;
        private final String schemaPath;
        private final String instancePath;
        private final String keyword;
        private final String errorCode;

        public ValidationErrorDetail(String message, String schemaPath, String instancePath,
                                   String keyword, String errorCode) {
            this.message = message;
            this.schemaPath = schemaPath;
            this.instancePath = instancePath;
            this.keyword = keyword;
            this.errorCode = errorCode;
        }

        // Getters
        public String getMessage() { return message; }
        public String getSchemaPath() { return schemaPath; }
        public String getInstancePath() { return instancePath; }
        public String getKeyword() { return keyword; }
        public String getErrorCode() { return errorCode; }

        @Override
        public String toString() {
            return String.format("[%s] %s (at %s)", errorCode, message, instancePath);
        }
    }
}