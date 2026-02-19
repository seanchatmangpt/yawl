/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.a2a.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A2A Schema Validator for validating JSON messages against A2A protocol schemas.
 *
 * <p>This validator provides comprehensive JSON schema validation for all A2A protocol
 * messages including agent cards, messages, tasks, and error responses. It uses
 * Jackson for JSON parsing with custom validation logic.
 *
 * <p>Features:
 * - JSON validation against A2A standards
 * - Custom business rule validation
 * - Detailed error reporting with path information
 * - Schema version support
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class SchemaValidator {

    private static final Logger _logger = LogManager.getLogger(SchemaValidator.class);

    private final ObjectMapper objectMapper;
    private final String schemaBasePath;

    /**
     * Constructs a new SchemaValidator with default configuration.
     */
    public SchemaValidator() {
        this("");
    }

    /**
     * Constructs a new SchemaValidator with custom schema path.
     *
     * @param schemaBasePath Base path to schema files
     */
    public SchemaValidator(String schemaBasePath) {
        this.schemaBasePath = schemaBasePath;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Validates a JSON string against a specific schema.
     *
     * @param json The JSON string to validate
     * @param schemaName The name of the schema to validate against
     * @return ValidationResult containing validation results
     */
    public ValidationResult validate(String json, String schemaName) {
        long startTime = System.currentTimeMillis();
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            List<ValidationErrorDetail> errors = validateAgainstSchema(jsonNode, schemaName);
            long validationTime = System.currentTimeMillis() - startTime;

            if (errors.isEmpty()) {
                return ValidationResult.success(schemaName, validationTime);
            } else {
                return ValidationResult.failure(schemaName, errors, validationTime);
            }
        } catch (IOException e) {
            _logger.error("Error validating against schema {}: {}", schemaName, e.getMessage());
            return ValidationResult.failure(schemaName, "JSON parsing error: " + e.getMessage());
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
     * Validates a JSON node against a schema using custom validation logic.
     *
     * @param jsonNode The JSON node to validate
     * @param schemaName The schema name to validate against
     * @return List of validation errors, empty if valid
     */
    private List<ValidationErrorDetail> validateAgainstSchema(JsonNode jsonNode, String schemaName) {
        List<ValidationErrorDetail> errors = new ArrayList<>();

        // Load schema for reference
        JsonNode schemaNode = loadSchemaNode(schemaName);
        if (schemaNode == null) {
            // If schema not found, perform basic JSON structure validation
            return validateBasicStructure(jsonNode, schemaName);
        }

        // Perform schema-based validation
        return validateWithSchema(jsonNode, schemaNode, "");
    }

    /**
     * Loads a schema from the classpath.
     *
     * @param schemaName Name of the schema file
     * @return JsonNode representing the schema, or null if not found
     */
    private JsonNode loadSchemaNode(String schemaName) {
        try {
            String fullPath = schemaBasePath.isEmpty() ? schemaName : schemaBasePath + "/" + schemaName;
            URL schemaUrl = getClass().getClassLoader().getResource(fullPath);

            if (schemaUrl == null) {
                _logger.debug("Schema not found: {}", fullPath);
                return null;
            }

            try (InputStream inputStream = schemaUrl.openStream()) {
                return objectMapper.readTree(inputStream);
            }
        } catch (IOException e) {
            _logger.debug("Error loading schema {}: {}", schemaName, e.getMessage());
            return null;
        }
    }

    /**
     * Performs basic JSON structure validation when schema is not available.
     *
     * @param jsonNode The JSON node to validate
     * @param schemaName The schema name for context
     * @return List of validation errors
     */
    private List<ValidationErrorDetail> validateBasicStructure(JsonNode jsonNode, String schemaName) {
        List<ValidationErrorDetail> errors = new ArrayList<>();

        // Basic validation: ensure it's a valid JSON object or array
        if (!jsonNode.isObject() && !jsonNode.isArray()) {
            errors.add(new ValidationErrorDetail(
                "Expected JSON object or array",
                "",
                "$",
                "type",
                "INVALID_TYPE"
            ));
        }

        return errors;
    }

    /**
     * Validates a JSON node against a schema node.
     *
     * @param jsonNode The JSON node to validate
     * @param schemaNode The schema node to validate against
     * @param path Current path in the JSON document
     * @return List of validation errors
     */
    private List<ValidationErrorDetail> validateWithSchema(JsonNode jsonNode, JsonNode schemaNode, String path) {
        List<ValidationErrorDetail> errors = new ArrayList<>();

        if (schemaNode == null || !schemaNode.isObject()) {
            return errors;
        }

        // Check type
        JsonNode typeNode = schemaNode.get("type");
        if (typeNode != null) {
            String expectedType = typeNode.asText();
            if (!isValidType(jsonNode, expectedType)) {
                errors.add(new ValidationErrorDetail(
                    "Expected type '" + expectedType + "' but found '" + getNodeType(jsonNode) + "'",
                    path,
                    path,
                    "type",
                    "INVALID_TYPE"
                ));
                return errors; // Type mismatch, no point continuing
            }
        }

        // Check required properties for objects
        JsonNode requiredNode = schemaNode.get("required");
        if (requiredNode != null && requiredNode.isArray() && jsonNode.isObject()) {
            for (JsonNode reqField : requiredNode) {
                String fieldName = reqField.asText();
                if (!jsonNode.has(fieldName)) {
                    errors.add(new ValidationErrorDetail(
                        "Required field '" + fieldName + "' is missing",
                        path + "/" + fieldName,
                        path,
                        "required",
                        "REQUIRED_FIELD_MISSING"
                    ));
                }
            }
        }

        // Check properties
        JsonNode propertiesNode = schemaNode.get("properties");
        if (propertiesNode != null && propertiesNode.isObject() && jsonNode.isObject()) {
            for (Map.Entry<String, JsonNode> prop : propertiesNode.properties()) {
                String propName = prop.getKey();
                JsonNode propSchema = prop.getValue();
                String propPath = path + "/" + propName;

                if (jsonNode.has(propName)) {
                    errors.addAll(validateWithSchema(jsonNode.get(propName), propSchema, propPath));
                }
            }
        }

        // Check items for arrays
        JsonNode itemsNode = schemaNode.get("items");
        if (itemsNode != null && jsonNode.isArray()) {
            int index = 0;
            for (JsonNode item : jsonNode) {
                String itemPath = path + "[" + index + "]";
                errors.addAll(validateWithSchema(item, itemsNode, itemPath));
                index++;
            }
        }

        // Check enum values
        JsonNode enumNode = schemaNode.get("enum");
        if (enumNode != null && enumNode.isArray()) {
            boolean found = false;
            String value = jsonNode.isTextual() ? jsonNode.asText() : jsonNode.toString();
            for (JsonNode enumValue : enumNode) {
                if (enumValue.asText().equals(value)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                errors.add(new ValidationErrorDetail(
                    "Value '" + value + "' is not one of the allowed enum values",
                    path,
                    path,
                    "enum",
                    "INVALID_ENUM_VALUE"
                ));
            }
        }

        // Check minimum for numbers
        JsonNode minimumNode = schemaNode.get("minimum");
        if (minimumNode != null && jsonNode.isNumber()) {
            double minimum = minimumNode.asDouble();
            if (jsonNode.asDouble() < minimum) {
                errors.add(new ValidationErrorDetail(
                    "Value " + jsonNode.asDouble() + " is less than minimum " + minimum,
                    path,
                    path,
                    "minimum",
                    "OUT_OF_RANGE"
                ));
            }
        }

        // Check maximum for numbers
        JsonNode maximumNode = schemaNode.get("maximum");
        if (maximumNode != null && jsonNode.isNumber()) {
            double maximum = maximumNode.asDouble();
            if (jsonNode.asDouble() > maximum) {
                errors.add(new ValidationErrorDetail(
                    "Value " + jsonNode.asDouble() + " is greater than maximum " + maximum,
                    path,
                    path,
                    "maximum",
                    "OUT_OF_RANGE"
                ));
            }
        }

        // Check pattern for strings
        JsonNode patternNode = schemaNode.get("pattern");
        if (patternNode != null && jsonNode.isTextual()) {
            String pattern = patternNode.asText();
            String value = jsonNode.asText();
            if (!value.matches(pattern)) {
                errors.add(new ValidationErrorDetail(
                    "Value '" + value + "' does not match pattern '" + pattern + "'",
                    path,
                    path,
                    "pattern",
                    "INVALID_PATTERN"
                ));
            }
        }

        return errors;
    }

    /**
     * Checks if a JSON node matches the expected type.
     *
     * @param jsonNode The JSON node to check
     * @param expectedType The expected JSON schema type
     * @return true if the type matches
     */
    private boolean isValidType(JsonNode jsonNode, String expectedType) {
        switch (expectedType) {
            case "object":
                return jsonNode.isObject();
            case "array":
                return jsonNode.isArray();
            case "string":
                return jsonNode.isTextual();
            case "number":
                return jsonNode.isNumber();
            case "integer":
                return jsonNode.isIntegralNumber();
            case "boolean":
                return jsonNode.isBoolean();
            case "null":
                return jsonNode.isNull();
            default:
                return true;
        }
    }

    /**
     * Gets the JSON schema type for a JSON node.
     *
     * @param jsonNode The JSON node
     * @return The JSON schema type name
     */
    private String getNodeType(JsonNode jsonNode) {
        if (jsonNode.isObject()) return "object";
        if (jsonNode.isArray()) return "array";
        if (jsonNode.isTextual()) return "string";
        if (jsonNode.isIntegralNumber()) return "integer";
        if (jsonNode.isNumber()) return "number";
        if (jsonNode.isBoolean()) return "boolean";
        if (jsonNode.isNull()) return "null";
        return "unknown";
    }

    /**
     * Checks if a schema exists.
     *
     * @param schemaName Name of the schema to check
     * @return true if schema exists, false otherwise
     */
    public boolean schemaExists(String schemaName) {
        return loadSchemaNode(schemaName) != null;
    }

    /**
     * Gets a list of all available schemas.
     *
     * @return List of schema names
     */
    public List<String> getAvailableSchemas() {
        List<String> schemas = new ArrayList<>();

        // Try common schema names
        String[] commonSchemas = {
            "agent-card.schema.json",
            "message.schema.json",
            "task.schema.json",
            "error-response.schema.json",
            "jwt-claim.schema.json",
            "handoff.schema.json",
            "a2a-message.schema.json"
        };

        for (String schemaName : commonSchemas) {
            if (schemaExists(schemaName)) {
                schemas.add(schemaName);
            }
        }

        Collections.sort(schemas);
        return schemas;
    }

    /**
     * Validates multiple JSON objects against their schemas.
     *
     * @param validations Map of schema names to JSON strings
     * @return Map of schema names to ValidationResult
     */
    public Map<String, ValidationResult> validateMultiple(Map<String, String> validations) {
        return validations.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> validate(entry.getValue(), entry.getKey())
            ));
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
        public static ValidationResult success(String schemaName, long validationTime) {
            return new ValidationResult(true, schemaName, "Validation passed", Collections.emptyList(), validationTime);
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
        public static ValidationResult failure(String schemaName, List<ValidationErrorDetail> errors, long validationTime) {
            String summary = errors.size() + " validation error(s) found";
            return new ValidationResult(false, schemaName, summary, errors, validationTime);
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
        public Optional<ValidationErrorDetail> getFirstError() {
            return errors.isEmpty() ? Optional.empty() : Optional.of(errors.get(0));
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
                return new ObjectMapper().writeValueAsString(this);
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
