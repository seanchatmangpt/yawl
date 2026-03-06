/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */
package org.yawlfoundation.yawl.dspy.otp.schema;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.yawlfoundation.yawl.dspy.otp.DspyOtpException;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Validates DSPy program inputs against a JSON schema.
 *
 * Performs fail-fast validation before RPC to avoid network round-trips
 * for malformed inputs. Schema format is a simplified JSON Schema subset
 * with type constraints.
 *
 * <h2>Supported Schema Format</h2>
 * <pre>{@code
 * {
 *   "properties": {
 *     "text": { "type": "string", "description": "Input text" },
 *     "lang": { "type": "string", "enum": ["en", "fr", "de"] },
 *     "confidence": { "type": "number", "minimum": 0.0, "maximum": 1.0 }
 *   },
 *   "required": ["text"]
 * }
 * }</pre>
 *
 * <h2>Type Mapping</h2>
 * <ul>
 *   <li>"string" → java.lang.String</li>
 *   <li>"integer" → java.lang.Number (Integer, Long, BigDecimal)</li>
 *   <li>"number" → java.lang.Number (Float, Double, BigDecimal)</li>
 *   <li>"boolean" → java.lang.Boolean</li>
 *   <li>"array" → java.util.Collection (List, Set, etc.)</li>
 *   <li>"object" → java.util.Map</li>
 * </ul>
 */
@NullMarked
public final class DspySchemaValidator {

    private final Map<String, Object> schema;

    /**
     * Create validator from schema.
     *
     * @param schema JSON schema map (from DspyProgram.inputSchema)
     */
    public DspySchemaValidator(Map<String, Object> schema) {
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    /**
     * Validate input map against the schema.
     *
     * @param inputs user-provided input map
     * @throws DspyOtpValidationException if validation fails
     */
    public void validate(Map<String, Object> inputs) {
        Objects.requireNonNull(inputs, "inputs");

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) {
            return; // No validation without properties definition
        }

        @SuppressWarnings("unchecked")
        Collection<String> required = (Collection<String>) schema.get("required");

        // Check required fields
        if (required != null) {
            for (String field : required) {
                if (!inputs.containsKey(field)) {
                    throw new DspyOtpException.DspyOtpValidationException(
                        "Required field '" + field + "' is missing",
                        field,
                        getExpectedType(properties, field)
                    );
                }
            }
        }

        // Validate type of each provided input
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            @SuppressWarnings("unchecked")
            Map<String, Object> fieldSchema = (Map<String, Object>) properties.get(key);
            if (fieldSchema != null) {
                validateField(key, value, fieldSchema);
            }
        }
    }

    /**
     * Validate a single field value against its schema.
     */
    private void validateField(String fieldName, @Nullable Object value,
                               Map<String, Object> fieldSchema) {
        if (value == null) {
            // Null values are allowed unless explicitly forbidden
            return;
        }

        String expectedType = (String) fieldSchema.get("type");
        if (expectedType == null) {
            return; // No type constraint
        }

        if (!typeMatches(value, expectedType)) {
            throw new DspyOtpException.DspyOtpValidationException(
                "Field '" + fieldName + "' has wrong type. " +
                "Expected " + expectedType + " but got " + value.getClass().getSimpleName(),
                fieldName,
                expectedType
            );
        }

        // Check enum constraint
        @SuppressWarnings("unchecked")
        Collection<Object> enumValues = (Collection<Object>) fieldSchema.get("enum");
        if (enumValues != null && !enumValues.contains(value)) {
            throw new DspyOtpException.DspyOtpValidationException(
                "Field '" + fieldName + "' value '" + value +
                "' is not in allowed values: " + enumValues,
                fieldName,
                enumValues.toString()
            );
        }

        // Check numeric constraints (min/max)
        if ("number".equals(expectedType) || "integer".equals(expectedType)) {
            if (value instanceof Number num) {
                double doubleValue = num.doubleValue();

                @Nullable Number minimum = (Number) fieldSchema.get("minimum");
                if (minimum != null && doubleValue < minimum.doubleValue()) {
                    throw new DspyOtpException.DspyOtpValidationException(
                        "Field '" + fieldName + "' value " + doubleValue +
                        " is less than minimum " + minimum,
                        fieldName,
                        "number >= " + minimum
                    );
                }

                @Nullable Number maximum = (Number) fieldSchema.get("maximum");
                if (maximum != null && doubleValue > maximum.doubleValue()) {
                    throw new DspyOtpException.DspyOtpValidationException(
                        "Field '" + fieldName + "' value " + doubleValue +
                        " exceeds maximum " + maximum,
                        fieldName,
                        "number <= " + maximum
                    );
                }
            }
        }
    }

    /**
     * Check if value type matches schema type.
     */
    private boolean typeMatches(Object value, String expectedType) {
        return switch (expectedType) {
            case "string" -> value instanceof String;
            case "integer", "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof Collection<?>;
            case "object" -> value instanceof Map<?, ?>;
            default -> true; // Unknown type, allow
        };
    }

    /**
     * Get expected type from schema properties.
     */
    private String getExpectedType(Map<String, Object> properties, String field) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldSchema = (Map<String, Object>) properties.get(field);
        if (fieldSchema != null) {
            Object type = fieldSchema.get("type");
            if (type instanceof String) {
                return (String) type;
            }
        }
        return "unknown";
    }
}
