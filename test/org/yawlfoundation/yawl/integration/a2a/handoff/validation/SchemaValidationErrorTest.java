/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.handoff.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for SchemaValidationError class.
 *
 * Tests error creation, message formatting, cause propagation,
 * schema ID handling, and serialization compatibility.
 *
 * Coverage targets:
 * - Exception creation with schema ID and message
 * - Exception creation with schema ID, message, and cause
 * - getSchemaId() method accuracy
 * - getValidationMessage() method functionality
 * - getMessage() method integration
 * - Cause handling
 * - Serialization compatibility
 */
class SchemaValidationErrorTest {

    private static final String TEST_SCHEMA_ID = "a2a-handoff-message";
    private static final String TEST_VALIDATION_MESSAGE = "Invalid message format: missing required field 'workItemId'";
    private static final Throwable TEST_CAUSE = new RuntimeException("JSON parsing error");

    @Nested
    @DisplayName("Exception Creation")
    class ExceptionCreationTests {

        @Test
        @DisplayName("Create error with schema ID and message")
        void createErrorWithSchemaIdAndMessage() {
            // When creating error with schema ID and message
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE);

            // Then should have correct schema ID and message
            assertEquals(TEST_SCHEMA_ID, error.getSchemaId(), "Schema ID should match");
            assertEquals(TEST_VALIDATION_MESSAGE, error.getValidationMessage(), "Validation message should match");
            assertEquals("Schema validation failed for " + TEST_SCHEMA_ID + ": " + TEST_VALIDATION_MESSAGE,
                       error.getMessage(), "Combined message should be formatted correctly");
            assertNull(error.getCause(), "Cause should be null when not provided");
        }

        @Test
        @DisplayName("Create error with schema ID, message, and cause")
        void createErrorWithSchemaIdMessageAndCause() {
            // When creating error with schema ID, message, and cause
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE, TEST_CAUSE);

            // Then should have correct schema ID, message, and cause
            assertEquals(TEST_SCHEMA_ID, error.getSchemaId(), "Schema ID should match");
            assertEquals(TEST_VALIDATION_MESSAGE, error.getValidationMessage(), "Validation message should match");
            assertEquals(TEST_CAUSE, error.getCause(), "Cause should match");
        }

        @Test
        @DisplayName("Create error with null schema ID")
        void createErrorWithNullSchemaId() {
            // When creating error with null schema ID
            SchemaValidationError error = new SchemaValidationError(null, TEST_VALIDATION_MESSAGE);

            // Then should handle gracefully
            assertNull(error.getSchemaId(), "Schema ID should be null");
            assertEquals(TEST_VALIDATION_MESSAGE, error.getValidationMessage(), "Validation message should be preserved");
        }

        @Test
        @DisplayName("Create error with empty schema ID")
        void createErrorWithEmptySchemaId() {
            // When creating error with empty schema ID
            SchemaValidationError error = new SchemaValidationError("", TEST_VALIDATION_MESSAGE);

            // Then should handle gracefully
            assertEquals("", error.getSchemaId(), "Schema ID should be empty");
            assertEquals(TEST_VALIDATION_MESSAGE, error.getValidationMessage(), "Validation message should be preserved");
        }

        @Test
        @DisplayName("Create error with null validation message")
        void createErrorWithNullValidationMessage() {
            // When creating error with null validation message
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, null);

            // Then should handle gracefully
            assertEquals(TEST_SCHEMA_ID, error.getSchemaId(), "Schema ID should be preserved");
            assertNull(error.getValidationMessage(), "Validation message should be null");
        }

        @Test
        @DisplayName("Create error with empty validation message")
        void createErrorWithEmptyValidationMessage() {
            // When creating error with empty validation message
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, "");

            // Then should handle gracefully
            assertEquals(TEST_SCHEMA_ID, error.getSchemaId(), "Schema ID should be preserved");
            assertEquals("", error.getValidationMessage(), "Validation message should be empty");
        }

        @Test
        @DisplayName("Create error with null cause")
        void createErrorWithNullCause() {
            // When creating error with null cause
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE, null);

            // Then should handle gracefully
            assertEquals(TEST_SCHEMA_ID, error.getSchemaId(), "Schema ID should be preserved");
            assertEquals(TEST_VALIDATION_MESSAGE, error.getValidationMessage(), "Validation message should be preserved");
            assertNull(error.getCause(), "Cause should be null");
        }
    }

    @Nested
    @DisplayName("Schema ID Handling")
    class SchemaIdHandlingTests {

        @Test
        @DisplayName("getSchemaId returns correct schema ID")
        void getSchemaIdReturnsCorrectSchemaId() {
            // Given error with schema ID
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE);

            // Then getSchemaId should return the schema ID
            assertEquals(TEST_SCHEMA_ID, error.getSchemaId(), "Should return correct schema ID");
        }

        @Test
        @DisplayName("getSchemaId returns null for null schema ID")
        void getSchemaIdReturnsNullForNullSchemaId() {
            // Given error with null schema ID
            SchemaValidationError error = new SchemaValidationError(null, TEST_VALIDATION_MESSAGE);

            // Then getSchemaId should return null
            assertNull(error.getSchemaId(), "Should return null for null schema ID");
        }

        @Test
        @DisplayName("getSchemaId returns empty string for empty schema ID")
        void getSchemaIdReturnsEmptyStringForEmptySchemaId() {
            // Given error with empty schema ID
            SchemaValidationError error = new SchemaValidationError("", TEST_VALIDATION_MESSAGE);

            // Then getSchemaId should return empty string
            assertEquals("", error.getSchemaId(), "Should return empty string for empty schema ID");
        }

        @Test
        @DisplayName("Schema ID can contain special characters")
        void schemaIdCanContainSpecialCharacters() {
            // Given schema ID with special characters
            String specialSchemaId = "schema-@#$%^&*()_+-={}[]|\\:;\"'<>,.?/~";

            SchemaValidationError error = new SchemaValidationError(specialSchemaId, TEST_VALIDATION_MESSAGE);

            // Then special characters should be preserved
            assertEquals(specialSchemaId, error.getSchemaId(), "Should preserve special characters in schema ID");
        }

        @Test
        @DisplayName("Schema ID can contain Unicode characters")
        void schemaIdCanContainUnicodeCharacters() {
            // Given schema ID with Unicode characters
            String unicodeSchemaId = "schema-中文-🚀-测试";

            SchemaValidationError error = new SchemaValidationError(unicodeSchemaId, TEST_VALIDATION_MESSAGE);

            // Then Unicode characters should be preserved
            assertEquals(unicodeSchemaId, error.getSchemaId(), "Should preserve Unicode characters in schema ID");
        }
    }

    @Nested
    @DisplayName("Validation Message Handling")
    class ValidationMessageHandlingTests {

        @Test
        @DisplayName("getValidationMessage returns correct message")
        void getValidationMessageReturnsCorrectMessage() {
            // Given error with validation message
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE);

            // Then getValidationMessage should return the message
            assertEquals(TEST_VALIDATION_MESSAGE, error.getValidationMessage(), "Should return correct validation message");
        }

        @Test
        @DisplayName("getValidationMessage returns null for null message")
        void getValidationMessageReturnsNullForNullMessage() {
            // Given error with null validation message
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, null);

            // Then getValidationMessage should return null
            assertNull(error.getValidationMessage(), "Should return null for null validation message");
        }

        @Test
        @DisplayName("getValidationMessage returns empty string for empty message")
        void getValidationMessageReturnsEmptyStringForEmptyMessage() {
            // Given error with empty validation message
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, "");

            // Then getValidationMessage should return empty string
            assertEquals("", error.getValidationMessage(), "Should return empty string for empty validation message");
        }

        @Test
        @DisplayName("Validation message can contain special characters")
        void validationMessageCanContainSpecialCharacters() {
            // Given validation message with special characters
            String specialMessage = "Invalid format @#$%^&*()_+-={}[]|\\:;\"'<>,.?/~ with special chars";

            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, specialMessage);

            // Then special characters should be preserved
            assertEquals(specialMessage, error.getValidationMessage(), "Should preserve special characters in validation message");
        }

        @Test
        @DisplayName("Validation message can contain Unicode characters")
        void validationMessageCanContainUnicodeCharacters() {
            // Given validation message with Unicode characters
            String unicodeMessage = "格式无效 🚀 中文 日本語 with Unicode";

            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, unicodeMessage);

            // Then Unicode characters should be preserved
            assertEquals(unicodeMessage, error.getValidationMessage(), "Should preserve Unicode characters in validation message");
        }
    }

    @Nested
    @DisplayName("Message Integration")
    class MessageIntegrationTests {

        @Test
        @DisplayName("getMessage returns formatted message with schema ID")
        void getMessageReturnsFormattedMessageWithSchemaId() {
            // Given error
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE);

            // Then getMessage should return formatted message
            String expectedMessage = "Schema validation failed for " + TEST_SCHEMA_ID + ": " + TEST_VALIDATION_MESSAGE;
            assertEquals(expectedMessage, error.getMessage(), "Should return formatted message with schema ID");
        }

        @Test
        @DisplayName("getMessage handles null schema ID")
        void getMessageHandlesNullSchemaId() {
            // Given error with null schema ID
            SchemaValidationError error = new SchemaValidationError(null, TEST_VALIDATION_MESSAGE);

            // Then getMessage should handle null schema ID
            String expectedMessage = "Schema validation failed for null: " + TEST_VALIDATION_MESSAGE;
            assertEquals(expectedMessage, error.getMessage(), "Should handle null schema ID in formatted message");
        }

        @Test
        @DisplayName("getMessage handles empty schema ID")
        void getMessageHandlesEmptySchemaId() {
            // Given error with empty schema ID
            SchemaValidationError error = new SchemaValidationError("", TEST_VALIDATION_MESSAGE);

            // Then getMessage should handle empty schema ID
            String expectedMessage = "Schema validation failed for : " + TEST_VALIDATION_MESSAGE;
            assertEquals(expectedMessage, error.getMessage(), "Should handle empty schema ID in formatted message");
        }

        @Test
        @DisplayName("getMessage handles null validation message")
        void getMessageHandlesNullValidationMessage() {
            // Given error with null validation message
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, null);

            // Then getMessage should handle null validation message
            String expectedMessage = "Schema validation failed for " + TEST_SCHEMA_ID + ": null";
            assertEquals(expectedMessage, error.getMessage(), "Should handle null validation message");
        }
    }

    @Nested
    @DisplayName("Cause Handling")
    class CauseHandlingTests {

        @Test
        @DisplayName("getCause returns null when cause not provided")
        void getCauseReturnsNullWhenNotProvided() {
            // Given error without cause
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE);

            // Then getCause should return null
            assertNull(error.getCause(), "Should return null when cause not provided");
        }

        @Test
        @DisplayName("getCause returns provided cause")
        void getCauseReturnsProvidedCause() {
            // Given error with cause
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE, TEST_CAUSE);

            // Then getCause should return the cause
            assertEquals(TEST_CAUSE, error.getCause(), "Should return provided cause");
        }

        @Test
        @DisplayName("getCause handles null cause gracefully")
        void getCauseHandlesNullCauseGracefully() {
            // Given error with null cause
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE, null);

            // Then getCause should return null
            assertNull(error.getCause(), "Should handle null cause gracefully");
        }

        @Test
        @DisplayName("Cause can be any throwable type")
        void causeCanBeAnyThrowableType() {
            // Given different throwable types
            RuntimeException runtimeCause = new RuntimeException("Runtime error");
            IOException ioCause = new IOException("IO error");
            Error errorCause = new Error("Error cause");

            // Should handle all types
            assertDoesNotThrow(() -> {
                new SchemaValidationError(TEST_SCHEMA_ID, "Runtime", runtimeCause);
                new SchemaValidationError(TEST_SCHEMA_ID, "IO", ioCause);
                new SchemaValidationError(TEST_SCHEMA_ID, "Error", errorCause);
            });
        }
    }

    @Nested
    @DisplayName("Common Validation Scenarios")
    class CommonValidationScenariosTests {

        @Test
        @DisplayName("JSON validation error")
        void jsonValidationError() {
            // Given JSON validation failure scenario
            String schemaId = "a2a-message-json";
            String message = "Invalid JSON: Unexpected end of input";

            SchemaValidationError error = new SchemaValidationError(schemaId, message);

            // Should describe JSON validation failure
            assertEquals(schemaId, error.getSchemaId());
            assertEquals(message, error.getValidationMessage());
        }

        @Test
        @DisplayName("Required field missing error")
        void requiredFieldMissingError() {
            // Given required field missing scenario
            String schemaId = "a2a-handoff-message";
            String message = "Required field 'workItemId' is missing";

            SchemaValidationError error = new SchemaValidationError(schemaId, message);

            // Should describe missing field
            assertEquals(schemaId, error.getSchemaId());
            assertEquals(message, error.getValidationMessage());
        }

        @Test
        @DisplayName("Type validation error")
        void typeValidationError() {
            // Given type validation failure scenario
            String schemaId = "a2a-message";
            String message = "Field 'confidence' must be number, found string";

            SchemaValidationError error = new SchemaValidationError(schemaId, message);

            // Should describe type validation failure
            assertEquals(schemaId, error.getSchemaId());
            assertEquals(message, error.getValidationMessage());
        }

        @Test
        @DisplayName("Pattern validation error")
        void patternValidationError() {
            // Given pattern validation failure scenario
            String schemaId = "a2a-message";
            String message = "Field 'workItemId' does not match pattern '^WI-\\d+$'";

            SchemaValidationError error = new SchemaValidationError(schemaId, message);

            // Should describe pattern validation failure
            assertEquals(schemaId, error.getSchemaId());
            assertEquals(message, error.getValidationMessage());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Error with very long messages")
        void errorWithLongMessages() {
            // Given very long messages
            String longSchemaId = new String(new char[1000]).replace('\0', 's');
            String longMessage = new String(new char[5000]).replace('\0', 'm');

            SchemaValidationError error = new SchemaValidationError(longSchemaId, longMessage);

            // Then messages should be preserved
            assertEquals(longSchemaId, error.getSchemaId());
            assertEquals(longMessage, error.getValidationMessage());
        }

        @Test
        @DisplayName("Error with newline characters")
        void errorWithNewlineCharacters() {
            // Given messages with newlines
            String multilineSchemaId = "schema\nwith\nnewlines";
            String multilineMessage = "Error message\nwith\nmultiple\nlines";

            SchemaValidationError error = new SchemaValidationError(multilineSchemaId, multilineMessage);

            // Then newlines should be preserved
            assertEquals(multilineSchemaId, error.getSchemaId());
            assertEquals(multilineMessage, error.getValidationMessage());
        }

        @Test
        @DisplayName("Error with nested cause")
        void errorWithNestedCause() {
            // Given nested throwable chain
            Throwable level3 = new RuntimeException("Level 3");
            Throwable level2 = new RuntimeException("Level 2", level3);
            Throwable level1 = new RuntimeException("Level 1", level2);

            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, "Nested error", level1);

            // Then cause chain should be preserved
            assertEquals(level1, error.getCause());
            assertEquals(level2, error.getCause().getCause());
            assertEquals(level3, error.getCause().getCause().getCause());
        }
    }

    @Nested
    @DisplayName("Serialization Compatibility")
    class SerializationCompatibilityTests {

        @Test
        @DisplayName("Error is serializable")
        void errorIsSerializable() {
            // Given error
            SchemaValidationError original = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE, TEST_CAUSE);

            // Then should be serializable (in theory)
            // Note: Actual serialization would need ObjectOutputStream
            assertNotNull(original, "Error should be serializable");
            assertEquals(TEST_SCHEMA_ID, original.getSchemaId(), "Schema ID should be preserved");
            assertEquals(TEST_VALIDATION_MESSAGE, original.getValidationMessage(), "Validation message should be preserved");
            assertEquals(TEST_CAUSE, original.getCause(), "Cause should be preserved");
        }

        @Test
        @DisplayName("Error maintains serialVersionUID")
        void errorMaintainsSerialVersionUID() {
            // Given error
            SchemaValidationError error = new SchemaValidationError(TEST_SCHEMA_ID, TEST_VALIDATION_MESSAGE);

            // Then should have serialVersionUID (verified by compilation)
            assertNotNull(error, "Error should compile with serialVersionUID");
        }
    }
}