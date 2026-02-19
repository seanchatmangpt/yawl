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

package org.yawlfoundation.yawl.integration.validation.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaException;
import com.networknt.schema.ValidationMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * High-performance JSON schema validator with caching and async support.
 *
 * <p>Provides efficient validation of JSON documents against JSON Schema Draft 7,
 * with support for both synchronous and asynchronous validation, configurable
 * error handling, and detailed error reporting.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see SchemaRegistry
 * @see SchemaValidationError
 */
public class JsonSchemaValidator {

    private static final Logger logger = LogManager.getLogger(JsonSchemaValidator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SchemaRegistry schemaRegistry;
    private final ValidationConfig config;
    private final ExecutorService validationExecutor;
    private final boolean asyncEnabled;

    /**
     * Creates a new JSON schema validator with default configuration.
     */
    public JsonSchemaValidator() {
        this(SchemaRegistry.getInstance(), ValidationConfig.getDefault());
    }

    /**
     * Creates a new JSON schema validator with custom configuration.
     *
     * @param schemaRegistry schema registry instance
     * @param config validation configuration
     */
    public JsonSchemaValidator(SchemaRegistry schemaRegistry, ValidationConfig config) {
        this.schemaRegistry = schemaRegistry != null ? schemaRegistry : SchemaRegistry.getInstance();
        this.config = config != null ? config : ValidationConfig.getDefault();
        this.validationExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "json-validator-" + System.currentTimeMillis());
            t.setDaemon(true);
            return t;
        });
        this.asyncEnabled = true;
    }

    /**
     * Validates a JSON string against a schema.
     *
     * @param json the JSON string to validate
     * @param schemaPath path to the schema resource
     * @return validation result with detailed information
     * @throws SchemaValidationError if validation fails
     * @throws IllegalArgumentException if input is invalid
     */
    public ValidationResult validate(String json, String schemaPath) throws SchemaValidationError {
        return validate(json, schemaPath, null);
    }

    /**
     * Validates a JSON string against a schema with version control.
     *
     * @param json the JSON string to validate
     * @param schemaPath path to the schema resource
     * @param version schema version (optional)
     * @return validation result with detailed information
     * @throws SchemaValidationError if validation fails
     * @throws IllegalArgumentException if input is invalid
     */
    public ValidationResult validate(String json, String schemaPath, String version) throws SchemaValidationError {
        try {
            JsonNode jsonNode = parseJson(json);
            return validate(jsonNode, schemaPath, version);
        } catch (IOException e) {
            throw new SchemaValidationError(
                "Invalid JSON input",
                SchemaValidationError.ErrorType.INVALID_JSON,
                null,
                "Check that the input is valid JSON",
                Map.of("error", e.getMessage())
            );
        }
    }

    /**
     * Validates a JSON node against a schema.
     *
     * @param jsonNode the JSON node to validate
     * @param schemaPath path to the schema resource
     * @return validation result with detailed information
     * @throws SchemaValidationError if validation fails
     */
    public ValidationResult validate(JsonNode jsonNode, String schemaPath) throws SchemaValidationError {
        return validate(jsonNode, schemaPath, null);
    }

    /**
     * Validates a JSON node against a schema with version control.
     *
     * @param jsonNode the JSON node to validate
     * @param schemaPath path to the schema resource
     * @param version schema version (optional)
     * @return validation result with detailed information
     * @throws SchemaValidationError if validation fails
     */
    public ValidationResult validate(JsonNode jsonNode, String schemaPath, String version) throws SchemaValidationError {
        // Load schema with timeout
        JsonSchema schema = loadSchemaWithTimeout(schemaPath, version);

        // Perform validation
        return validateJson(jsonNode, schema);
    }

    /**
     * Validates a JSON object against an A2A protocol schema.
     *
     * @param json the JSON string to validate
     * @param schemaName name of the A2A schema (e.g., "handoff-message")
     * @return validation result with detailed information
     * @throws SchemaValidationError if validation fails
     */
    public ValidationResult validateA2A(String json, String schemaName) throws SchemaValidationError {
        try {
            JsonNode jsonNode = parseJson(json);
            JsonSchema schema = schemaRegistry.getA2ASchema(schemaName);
            return validateJson(jsonNode, schema);
        } catch (IOException e) {
            throw new SchemaValidationError(
                "Invalid JSON input",
                SchemaValidationError.ErrorType.INVALID_JSON,
                null,
                "Check that the input is valid JSON",
                Map.of("error", e.getMessage())
            );
        }
    }

    /**
     * Validates a JSON object against an MCP protocol schema.
     *
     * @param json the JSON string to validate
     * @param schemaName name of the MCP schema (e.g., "tool-call")
     * @return validation result with detailed information
     * @throws SchemaValidationError if validation fails
     */
    public ValidationResult validateMCP(String json, String schemaName) throws SchemaValidationError {
        try {
            JsonNode jsonNode = parseJson(json);
            JsonSchema schema = schemaRegistry.getMCPSchema(schemaName);
            return validateJson(jsonNode, schema);
        } catch (IOException e) {
            throw new SchemaValidationError(
                "Invalid JSON input",
                SchemaValidationError.ErrorType.INVALID_JSON,
                null,
                "Check that the input is valid JSON",
                Map.of("error", e.getMessage())
            );
        }
    }

    /**
     * Asynchronously validates JSON against a schema.
     *
     * @param json the JSON string to validate
     * @param schemaPath path to the schema resource
     * @return CompletableFuture with validation result
     */
    public CompletableFuture<ValidationResult> validateAsync(String json, String schemaPath) {
        return validateAsync(json, schemaPath, null);
    }

    /**
     * Asynchronously validates JSON against a schema with version control.
     *
     * @param json the JSON string to validate
     * @param schemaPath path to the schema resource
     * @param version schema version (optional)
     * @return CompletableFuture with validation result
     */
    public CompletableFuture<ValidationResult> validateAsync(String json, String schemaPath, String version) {
        if (!asyncEnabled) {
            try {
                return CompletableFuture.completedFuture(validate(json, schemaPath, version));
            } catch (SchemaValidationError e) {
                CompletableFuture<ValidationResult> future = new CompletableFuture<>();
                future.completeExceptionally(e);
                return future;
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return validate(json, schemaPath, version);
            } catch (SchemaValidationError e) {
                throw new RuntimeException(e);
            }
        }, validationExecutor);
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
     * Validates multiple JSON documents in parallel.
     *
     * @param documents map of document IDs to JSON strings
     * @param schemaPath path to the schema resource
     * @return map of document IDs to validation results
     */
    public Map<String, ValidationResult> validateBatch(Map<String, String> documents, String schemaPath) {
        List<CompletableFuture<Map.Entry<String, ValidationResult>>> futures = new ArrayList<>();

        for (Map.Entry<String, String> entry : documents.entrySet()) {
            String docId = entry.getKey();
            String json = entry.getValue();

            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    ValidationResult result = validate(json, schemaPath);
                    return Map.entry(docId, result);
                } catch (SchemaValidationError e) {
                    return Map.entry(docId, ValidationResult.failure(e));
                }
            }, validationExecutor));
        }

        // Wait for all validations to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.get(config.getValidationTimeout().toSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new SchemaValidationError(
                "Batch validation timed out",
                SchemaValidationError.ErrorType.TIMEOUT_ERROR,
                null,
                "Increase validation timeout or reduce batch size",
                Map.of("timeout", config.getValidationTimeout())
            );
        }

        // Collect results
        return futures.stream()
            .filter(future -> !future.isCompletedExceptionally())
            .map(CompletableFuture::join)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Shuts down the validator and releases resources.
     */
    public void shutdown() {
        validationExecutor.shutdown();
        try {
            if (!validationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                validationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            validationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode parseJson(String json) throws IOException {
        return objectMapper.readTree(json);
    }

    private JsonSchema loadSchemaWithTimeout(String schemaPath, String version) throws SchemaValidationError {
        try {
            return schemaRegistry.getSchema(schemaPath, version);
        } catch (SchemaValidationError e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaValidationError(
                "Failed to load schema: " + schemaPath,
                SchemaValidationError.ErrorType.SYSTEM_ERROR,
                null,
                "Check the schema path and registry configuration",
                Map.of("schemaPath", schemaPath, "error", e.getMessage())
            );
        }
    }

    private ValidationResult validateJson(JsonNode jsonNode, JsonSchema schema) throws SchemaValidationError {
        try {
            // Perform validation
            List<ValidationMessage> validationMessages = schema.validate(jsonNode);

            if (validationMessages.isEmpty()) {
                return ValidationResult.success();
            }

            // Process validation errors
            List<JsonSchemaException> exceptions = new ArrayList<>();
            for (ValidationMessage message : validationMessages) {
                JsonSchemaException exception = new JsonSchemaException(
                    message.getType(),
                    message.getPath(),
                    message.getSchemaPath(),
                    message.getArgument(),
                    message.getMessage()
                );
                exceptions.add(exception);
            }

            if (config.isFailFast() && !exceptions.isEmpty()) {
                throw new SchemaValidationError(exceptions.get(0));
            }

            return ValidationResult.failure(new SchemaValidationError(exceptions));

        } catch (JsonSchemaException e) {
            throw new SchemaValidationError(e);
        } catch (Exception e) {
            throw new SchemaValidationError(
                "Unexpected validation error",
                SchemaValidationError.ErrorType.SYSTEM_ERROR,
                null,
                "Check the schema and JSON document",
                Map.of("error", e.getMessage())
            );
        }
    }

    /**
     * Result of a validation operation.
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final SchemaValidationError error;
        private final List<ValidationMessage> messages;
        private final Map<String, Object> metadata;

        private ValidationResult(boolean isValid, SchemaValidationError error, List<ValidationMessage> messages, Map<String, Object> metadata) {
            this.isValid = isValid;
            this.error = error;
            this.messages = messages != null ? Collections.unmodifiableList(messages) : Collections.emptyList();
            this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
        }

        /**
         * Creates a successful validation result.
         *
         * @return successful validation result
         */
        public static ValidationResult success() {
            return new ValidationResult(true, null, Collections.emptyMap(), null);
        }

        /**
         * Creates a successful validation result with metadata.
         *
         * @param metadata validation metadata
         * @return successful validation result
         */
        public static ValidationResult success(Map<String, Object> metadata) {
            return new ValidationResult(true, null, Collections.emptyList(), metadata);
        }

        /**
         * Creates a failed validation result.
         *
         * @param error validation error
         * @return failed validation result
         */
        public static ValidationResult failure(SchemaValidationError error) {
            return new ValidationResult(false, error, error.getValidationErrors(), null);
        }

        /**
         * Gets whether the validation was successful.
         *
         * @return true if valid
         */
        public boolean isValid() {
            return isValid;
        }

        /**
         * Gets the validation error (if any).
         *
         * @return validation error, or null if successful
         */
        public SchemaValidationError getError() {
            return error;
        }

        /**
         * Gets all validation messages.
         *
         * @return list of validation messages
         */
        public List<ValidationMessage> getMessages() {
            return messages;
        }

        /**
         * Gets validation metadata.
         *
         * @return map of metadata
         */
        public Map<String, Object> getMetadata() {
            return metadata;
        }

        /**
         * Gets the number of validation messages.
         *
         * @return number of messages
         */
        public int getMessageCount() {
            return messages.size();
        }

        /**
         * Checks if there are validation messages.
         *
         * @return true if there are messages
         */
        public boolean hasMessages() {
            return !messages.isEmpty();
        }

        /**
         * Converts to a JSON node.
         *
         * @return JSON representation of the result
         */
        public JsonNode toJson() {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("valid", isValid);

            if (error != null) {
                ObjectNode errorNode = node.putObject("error");
                errorNode.put("message", error.getMessage());
                errorNode.put("type", error.getErrorType().name());
                if (error.getJsonPointer() != null) {
                    errorNode.put("pointer", error.getJsonPointer());
                }
            }

            if (!messages.isEmpty()) {
                messages.forEach(message -> {
                    ObjectNode msgNode = node.putArray("messages").addObject();
                    msgNode.put("type", message.getType());
                    msgNode.put("path", message.getPath());
                    msgNode.put("message", message.getMessage());
                });
            }

            if (!metadata.isEmpty()) {
                metadata.forEach(node::putPOJO);
            }

            return node;
        }
    }
}