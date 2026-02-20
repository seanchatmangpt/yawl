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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Validates API input to prevent resource exhaustion and injection attacks.
 *
 * Enforces:
 * - Size limits on strings, arrays, and batches
 * - Format validation (UUIDs, identifiers, email addresses)
 * - Pattern matching for suspicious input
 * - Prevention of oversized batch operations
 *
 * All validation methods throw InputValidationException on failure.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class InputValidator {

    private static final Logger log = LogManager.getLogger(InputValidator.class);

    // Size limits
    public static final int MAX_STRING_LENGTH = 4096;
    public static final int MAX_IDENTIFIER_LENGTH = 256;
    public static final int MAX_BATCH_SIZE = 1000;
    public static final int MAX_ARRAY_LENGTH = 10000;
    public static final int MAX_XML_SIZE_BYTES = 52_428_800; // 50 MB

    // Patterns
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ALPHANUMERIC_ID_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_-]+$"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute|script|javascript|onerror)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Exception thrown when input validation fails.
     */
    public static final class InputValidationException extends IllegalArgumentException {
        private final String fieldName;
        private final String validationRule;

        public InputValidationException(String fieldName, String validationRule, String message) {
            super(message);
            this.fieldName = Objects.requireNonNull(fieldName);
            this.validationRule = Objects.requireNonNull(validationRule);
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getValidationRule() {
            return validationRule;
        }
    }

    private InputValidator() {
        // Utility class - no instances
    }

    /**
     * Validates a required string field.
     *
     * @param fieldName the name of the field being validated
     * @param value the value to validate (must not be null or empty)
     * @param maxLength maximum allowed length (characters)
     * @return the value if valid
     * @throws InputValidationException if validation fails
     */
    public static String validateString(String fieldName, String value, int maxLength) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        if (value.isEmpty()) {
            throw new InputValidationException(fieldName, "required",
                    fieldName + " cannot be empty");
        }

        if (value.length() > maxLength) {
            throw new InputValidationException(fieldName, "max_length",
                    fieldName + " exceeds maximum length of " + maxLength);
        }

        return value;
    }

    /**
     * Validates a string identifier (alphanumeric, underscore, hyphen).
     *
     * @param fieldName the name of the field being validated
     * @param value the identifier to validate
     * @return the value if valid
     * @throws InputValidationException if validation fails
     */
    public static String validateIdentifier(String fieldName, String value) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        validateString(fieldName, value, MAX_IDENTIFIER_LENGTH);

        if (!ALPHANUMERIC_ID_PATTERN.matcher(value).matches()) {
            throw new InputValidationException(fieldName, "identifier_format",
                    fieldName + " must contain only alphanumeric characters, underscores, or hyphens");
        }

        return value;
    }

    /**
     * Validates a UUID string.
     *
     * @param fieldName the name of the field being validated
     * @param value the UUID to validate
     * @return the value if valid
     * @throws InputValidationException if validation fails
     */
    public static String validateUUID(String fieldName, String value) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        if (!UUID_PATTERN.matcher(value).matches()) {
            throw new InputValidationException(fieldName, "uuid_format",
                    fieldName + " must be a valid UUID");
        }

        return value;
    }

    /**
     * Validates an email address.
     *
     * @param fieldName the name of the field being validated
     * @param value the email to validate
     * @return the value if valid
     * @throws InputValidationException if validation fails
     */
    public static String validateEmail(String fieldName, String value) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        validateString(fieldName, value, 254); // RFC 5321 max email length

        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new InputValidationException(fieldName, "email_format",
                    fieldName + " must be a valid email address");
        }

        return value;
    }

    /**
     * Validates an integer is within bounds.
     *
     * @param fieldName the name of the field being validated
     * @param value the integer to validate
     * @param minInclusive minimum allowed value (inclusive)
     * @param maxInclusive maximum allowed value (inclusive)
     * @return the value if valid
     * @throws InputValidationException if validation fails
     */
    public static int validateInteger(String fieldName, int value, int minInclusive, int maxInclusive) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");

        if (value < minInclusive || value > maxInclusive) {
            throw new InputValidationException(fieldName, "integer_range",
                    fieldName + " must be between " + minInclusive + " and " + maxInclusive);
        }

        return value;
    }

    /**
     * Validates an array/collection size doesn't exceed limit.
     *
     * @param fieldName the name of the field being validated
     * @param size the current size of the collection
     * @param maxSize maximum allowed size
     * @return the size if valid
     * @throws InputValidationException if validation fails
     */
    public static int validateArraySize(String fieldName, int size, int maxSize) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");

        if (size > maxSize) {
            throw new InputValidationException(fieldName, "array_size",
                    fieldName + " size " + size + " exceeds maximum of " + maxSize);
        }

        return size;
    }

    /**
     * Validates a batch size for bulk operations.
     *
     * @param fieldName the name of the field being validated (e.g., "items")
     * @param batchSize the batch size to validate
     * @return the batch size if valid
     * @throws InputValidationException if batch size exceeds limit
     */
    public static int validateBatchSize(String fieldName, int batchSize) {
        return validateArraySize(fieldName, batchSize, MAX_BATCH_SIZE);
    }

    /**
     * Validates XML input size to prevent XXE and DoS attacks.
     *
     * @param fieldName the name of the field being validated
     * @param xmlContent the XML content
     * @return the XML content if valid
     * @throws InputValidationException if XML exceeds size limit
     */
    public static String validateXmlSize(String fieldName, String xmlContent) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(xmlContent, "xmlContent cannot be null");

        int sizeBytes = xmlContent.getBytes().length;
        if (sizeBytes > MAX_XML_SIZE_BYTES) {
            throw new InputValidationException(fieldName, "xml_size",
                    fieldName + " XML content exceeds maximum size of " +
                    (MAX_XML_SIZE_BYTES / 1_048_576) + " MB");
        }

        return xmlContent;
    }

    /**
     * Checks for SQL injection patterns in input.
     *
     * Logs suspicious input for security monitoring.
     *
     * @param fieldName the name of the field being validated
     * @param value the value to check
     * @return the value if no injection patterns found
     * @throws InputValidationException if SQL injection pattern detected
     */
    public static String validateNoSqlInjection(String fieldName, String value) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(value, "value cannot be null");

        if (SQL_INJECTION_PATTERN.matcher(value).find()) {
            log.warn("Potential SQL injection detected in field: {} with value length: {}",
                    fieldName, value.length());
            throw new InputValidationException(fieldName, "sql_injection_pattern",
                    fieldName + " contains invalid characters or patterns");
        }

        return value;
    }

    /**
     * Comprehensive validation for a case ID.
     *
     * Validates format, length, and absence of injection patterns.
     *
     * @param fieldName the name of the field being validated
     * @param caseId the case ID to validate
     * @return the case ID if valid
     * @throws InputValidationException if validation fails
     */
    public static String validateCaseId(String fieldName, String caseId) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");

        validateString(fieldName, caseId, MAX_IDENTIFIER_LENGTH);
        validateNoSqlInjection(fieldName, caseId);

        return caseId;
    }

    /**
     * Comprehensive validation for a work item ID.
     *
     * Validates format, length, and absence of injection patterns.
     *
     * @param fieldName the name of the field being validated
     * @param workItemId the work item ID to validate
     * @return the work item ID if valid
     * @throws InputValidationException if validation fails
     */
    public static String validateWorkItemId(String fieldName, String workItemId) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");

        validateString(fieldName, workItemId, MAX_IDENTIFIER_LENGTH);
        validateNoSqlInjection(fieldName, workItemId);

        return workItemId;
    }

    /**
     * Validates specification identifier format.
     *
     * @param fieldName the name of the field being validated
     * @param specId the specification ID to validate
     * @return the spec ID if valid
     * @throws InputValidationException if validation fails
     */
    public static String validateSpecificationId(String fieldName, String specId) {
        Objects.requireNonNull(fieldName, "fieldName cannot be null");

        validateString(fieldName, specId, MAX_IDENTIFIER_LENGTH);
        validateNoSqlInjection(fieldName, specId);

        return specId;
    }
}
