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

import com.networknt.schema.*;
import org.yawlfoundation.yawl.integration.validation.schema.ValidationConfig.ErrorType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exception thrown when JSON schema validation fails.
 *
 * <p>Provides detailed information about validation failures, including
 * the JSON pointer to the invalid field, error type, and actionable
 * suggestions for fixing the issue.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see SchemaRegistry
 * @see JsonSchemaValidator
 */
public class SchemaValidationError extends Exception {

    private final ErrorType errorType;
    private final String jsonPointer;
    private final String suggestedFix;
    private final Map<String, Object> details;
    private final List<JsonSchemaException> validationErrors;

    /**
     * Creates a new schema validation error with a single validation error.
     *
     * @param message the error message
     * @param errorType the type of validation error
     * @param jsonPointer JSON pointer to the invalid field (e.g., "/parts/0/text")
     * @param suggestedFix suggested fix for the error
     * @param details additional error details
     */
    public SchemaValidationError(String message, ErrorType errorType, String jsonPointer, String suggestedFix, Map<String, Object> details) {
        super(message);
        this.errorType = errorType;
        this.jsonPointer = jsonPointer;
        this.suggestedFix = suggestedFix;
        this.details = details != null ? Collections.unmodifiableMap(details) : Collections.emptyMap();
        this.validationErrors = Collections.emptyList();
    }

    /**
     * Creates a new schema validation error with multiple validation errors.
     *
     * @param message the error message
     * @param errorType the type of validation error
     * @param jsonPointer JSON pointer to the invalid field
     * @param suggestedFix suggested fix for the error
     * @param details additional error details
     * @param validationErrors list of validation exceptions
     */
    public SchemaValidationError(String message, ErrorType errorType, String jsonPointer,
                               String suggestedFix, Map<String, Object> details,
                               List<JsonSchemaException> validationErrors) {
        super(message);
        this.errorType = errorType;
        this.jsonPointer = jsonPointer;
        this.suggestedFix = suggestedFix;
        this.details = details != null ? Collections.unmodifiableMap(details) : Collections.emptyMap();
        this.validationErrors = Collections.unmodifiableList(validationErrors);
    }

    /**
     * Creates a new schema validation error from a single validation exception.
     *
     * @param e the validation exception
     */
    public SchemaValidationError(JsonSchemaException e) {
        this(
            e.getMessage(),
            mapErrorType(e.getError()),
            e.getPath(),
            generateSuggestedFix(e),
            extractDetails(e),
            Collections.singletonList(e)
        );
    }

    /**
     * Creates a new schema validation error from multiple validation exceptions.
     *
     * @param validationErrors list of validation exceptions
     */
    public SchemaValidationError(List<JsonSchemaException> validationErrors) {
        this(
            String.format("Validation failed with %d errors", validationErrors.size()),
            ErrorType.VALIDATION_ERROR,
            null,
            generateSuggestedFix(validationErrors),
            extractDetails(validationErrors.get(0)),
            validationErrors
        );
    }

    /**
     * Gets the error type.
     *
     * @return the error type
     */
    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Gets the JSON pointer to the invalid field.
     *
     * @return the JSON pointer, or null if not applicable
     */
    public String getJsonPointer() {
        return jsonPointer;
    }

    /**
     * Gets the suggested fix for the error.
     *
     * @return the suggested fix, or null if not available
     */
    public String getSuggestedFix() {
        return suggestedFix;
    }

    /**
     * Gets additional error details.
     *
     * @return unmodifiable map of error details
     */
    public Map<String, Object> getDetails() {
        return details;
    }

    /**
     * Gets the list of validation errors.
     *
     * @return unmodifiable list of validation exceptions
     */
    public List<JsonSchemaException> getValidationErrors() {
        return validationErrors;
    }

    /**
     * Gets a human-readable summary of all validation errors.
     *
     * @return formatted error summary
     */
    public String getErrorSummary() {
        if (validationErrors.isEmpty()) {
            return getMessage();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Validation failed with ").append(validationErrors.size()).append(" error(s):\n");

        for (int i = 0; i < validationErrors.size(); i++) {
            JsonSchemaException error = validationErrors.get(i);
            sb.append(String.format("%d. %s", i + 1, error.getMessage()));

            if (error.getPath() != null && !error.getPath().isEmpty()) {
                sb.append(" (at: ").append(error.getPath()).append(")");
            }

            if (i < validationErrors.size() - 1) {
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Checks if this error represents a validation failure.
     *
     * @return true if this is a validation error
     */
    public boolean isValidationError() {
        return errorType == ErrorType.VALIDATION_ERROR;
    }

    /**
     * Checks if this error represents a schema-related issue.
     *
     * @return true if this is a schema error
     */
    public boolean isSchemaError() {
        return errorType == ErrorType.SCHEMA_NOT_FOUND ||
               errorType == ErrorType.INVALID_SCHEMA;
    }

    /**
     * Checks if this error represents a system-related issue.
     *
     * @return true if this is a system error
     */
    public boolean isSystemError() {
        return errorType == ErrorType.SYSTEM_ERROR;
    }

    @Override
    public String toString() {
        return String.format("SchemaValidationError{type=%s, pointer='%s', message='%s'}",
                           errorType, jsonPointer, getMessage());
    }

    private static ErrorType mapErrorType(ValidationErrorCode errorCode) {
        if (errorCode == ValidationErrorCode.SCHEMA_NOT_FOUND) {
            return ErrorType.SCHEMA_NOT_FOUND;
        } else if (errorCode == ValidationErrorCode.JSON_READ) {
            return ErrorType.INVALID_JSON;
        } else if (errorCode == ValidationErrorCode.INVALID_SCHEMA) {
            return ErrorType.INVALID_SCHEMA;
        } else {
            return ErrorType.VALIDATION_ERROR;
        }
    }

    private static String generateSuggestedFix(JsonSchemaException e) {
        String errorCode = e.getError().toString();
        String message = e.getMessage().toLowerCase();
        String path = e.getPath();

        if (errorCode.equals("required")) {
            return "Check that all required fields are present in " + path;
        } else if (errorCode.equals("type")) {
            return "Ensure the value at " + path + " has the correct type";
        } else if (errorCode.equals("format")) {
            return "Format the value at " + path + " correctly";
        } else if (message.contains("unknown")) {
            return "Remove unknown property or check the schema definition";
        } else if (message.contains("additional")) {
            return "Remove additional properties or allow them in the schema";
        } else {
            return "Review the value at " + path + " according to schema requirements";
        }
    }

    private static String generateSuggestedFix(List<JsonSchemaException> errors) {
        if (errors.isEmpty()) {
            return "Check the schema and input data";
        }

        // Group errors by type
        Map<ValidationErrorCode, Long> errorCounts = errors.stream()
            .collect(Collectors.groupingBy(
                JsonSchemaException::getError,
                Collectors.counting()
            ));

        StringBuilder sb = new StringBuilder("Fix the following issues:\n");

        errorCounts.forEach((errorCode, count) -> {
            sb.append("- ").append(count).append(" ").append(errorCode).append(" error(s)\n");
        });

        return sb.toString();
    }

    private static Map<String, Object> extractDetails(JsonSchemaException e) {
        return Map.of(
            "errorCode", e.getError(),
            "path", e.getPath(),
            "schemaPath", e.getSchemaPath(),
            "argument", e.getArgument(),
            "type", "validation_error"
        );
    }

    /**
     * Error types for validation failures.
     */
    public enum ErrorType {
        VALIDATION_ERROR,       // General validation failure
        SCHEMA_NOT_FOUND,       // Schema file not found
        INVALID_SCHEMA,         // Schema is invalid
        INVALID_JSON,           // JSON parsing error
        SYSTEM_ERROR,           // System/IO error
        TIMEOUT_ERROR           // Validation timeout
    }
}