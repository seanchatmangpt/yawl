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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InputValidator.
 */
@DisplayName("Input Validator Tests")
class TestInputValidator {

    @Test
    @DisplayName("Valid string passes validation")
    void testValidString() {
        String result = InputValidator.validateString("field1", "valid-value", 100);
        assertEquals("valid-value", result);
    }

    @Test
    @DisplayName("Empty string rejected")
    void testEmptyString() {
        InputValidator.InputValidationException ex = assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateString("field1", "", 100)
        );
        assertEquals("field1", ex.getFieldName());
    }

    @Test
    @DisplayName("String exceeding max length rejected")
    void testStringTooLong() {
        InputValidator.InputValidationException ex = assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateString("field1", "x".repeat(101), 100)
        );
        assertEquals("field1", ex.getFieldName());
        assertEquals("max_length", ex.getValidationRule());
    }

    @Test
    @DisplayName("Valid identifier passes validation")
    void testValidIdentifier() {
        String result = InputValidator.validateIdentifier("id", "case-123_ABC");
        assertEquals("case-123_ABC", result);
    }

    @Test
    @DisplayName("Identifier with invalid characters rejected")
    void testInvalidIdentifier() {
        InputValidator.InputValidationException ex = assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateIdentifier("id", "case@123")
        );
        assertEquals("identifier_format", ex.getValidationRule());
    }

    @Test
    @DisplayName("Valid UUID passes validation")
    void testValidUUID() {
        String uuid = "550e8400-e29b-41d4-a716-446655440000";
        String result = InputValidator.validateUUID("id", uuid);
        assertEquals(uuid, result);
    }

    @Test
    @DisplayName("Invalid UUID format rejected")
    void testInvalidUUID() {
        InputValidator.InputValidationException ex = assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateUUID("id", "not-a-uuid")
        );
        assertEquals("uuid_format", ex.getValidationRule());
    }

    @Test
    @DisplayName("Valid email passes validation")
    void testValidEmail() {
        String result = InputValidator.validateEmail("email", "user@example.com");
        assertEquals("user@example.com", result);
    }

    @Test
    @DisplayName("Invalid email format rejected")
    void testInvalidEmail() {
        InputValidator.InputValidationException ex = assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateEmail("email", "invalid-email")
        );
        assertEquals("email_format", ex.getValidationRule());
    }

    @Test
    @DisplayName("Integer within valid range passes")
    void testValidInteger() {
        int result = InputValidator.validateInteger("count", 50, 0, 100);
        assertEquals(50, result);
    }

    @Test
    @DisplayName("Integer below minimum rejected")
    void testIntegerBelowMinimum() {
        assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateInteger("count", -1, 0, 100)
        );
    }

    @Test
    @DisplayName("Integer above maximum rejected")
    void testIntegerAboveMaximum() {
        assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateInteger("count", 101, 0, 100)
        );
    }

    @Test
    @DisplayName("Array size within limit passes")
    void testValidArraySize() {
        int result = InputValidator.validateArraySize("items", 50, 100);
        assertEquals(50, result);
    }

    @Test
    @DisplayName("Array exceeding size limit rejected")
    void testArraySizeExceeded() {
        assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateArraySize("items", 101, 100)
        );
    }

    @Test
    @DisplayName("Batch size validation")
    void testBatchSizeValidation() {
        int result = InputValidator.validateBatchSize("records", 500);
        assertEquals(500, result);

        assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateBatchSize("records", 1001)
        );
    }

    @Test
    @DisplayName("XML size validation")
    void testXmlSizeValidation() {
        String smallXml = "<data><item>value</item></data>";
        String result = InputValidator.validateXmlSize("xml", smallXml);
        assertEquals(smallXml, result);
    }

    @Test
    @DisplayName("SQL injection patterns detected")
    void testSqlInjectionDetection() {
        assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateNoSqlInjection("field", "'; DROP TABLE users; --")
        );

        assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateNoSqlInjection("field", "UNION SELECT password FROM users")
        );
    }

    @Test
    @DisplayName("Case ID validation")
    void testCaseIdValidation() {
        String result = InputValidator.validateCaseId("caseId", "case-12345");
        assertEquals("case-12345", result);

        assertThrows(
                InputValidator.InputValidationException.class,
                () -> InputValidator.validateCaseId("caseId", "case'; DROP TABLE--")
        );
    }

    @Test
    @DisplayName("Work item ID validation")
    void testWorkItemIdValidation() {
        String result = InputValidator.validateWorkItemId("itemId", "item-98765");
        assertEquals("item-98765", result);
    }

    @Test
    @DisplayName("Specification ID validation")
    void testSpecificationIdValidation() {
        String result = InputValidator.validateSpecificationId("specId", "spec_v1");
        assertEquals("spec_v1", result);
    }

    @Test
    @DisplayName("Null value rejected")
    void testNullValueRejected() {
        assertThrows(NullPointerException.class,
                () -> InputValidator.validateString("field", null, 100));

        assertThrows(NullPointerException.class,
                () -> InputValidator.validateIdentifier("field", null));

        assertThrows(NullPointerException.class,
                () -> InputValidator.validateUUID("field", null));

        assertThrows(NullPointerException.class,
                () -> InputValidator.validateEmail("field", null));
    }
}
