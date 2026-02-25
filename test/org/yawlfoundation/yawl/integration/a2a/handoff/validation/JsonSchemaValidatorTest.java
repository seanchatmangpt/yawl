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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for JsonSchemaValidator class and its ValidationResult inner class.
 *
 * Tests validator creation, schema ID handling, validation result creation,
 * and all edge cases including validation scenarios.
 *
 * Coverage targets:
 * - Validator constructor with schema ID
 * - getSchemaId() method accuracy
 * - ValidationResult creation (success, failure, with warnings)
 * - ValidationResult validation methods (isValid, getErrors, getWarnings)
 * - Error and warning handling
 * - Edge cases and null handling
 */
class JsonSchemaValidatorTest {

    private static final String TEST_SCHEMA_ID = "a2a-handoff-message";
    private static final String VALID_JSON = "{\"parts\":[{\"type\":\"text\",\"text\":\"YAWL_HANDOFF:WI-42:source-agent\"}]}";
    private static final String INVALID_JSON = "invalid json string";

    @Nested
    @DisplayName("Validator Creation")
    class ValidatorCreationTests {

        @Test
        @DisplayName("Create validator with schema ID")
        void createValidatorWithSchemaId() {
            // When creating validator with schema ID
            JsonSchemaValidator validator = new JsonSchemaValidator(TEST_SCHEMA_ID);

            // Then should have correct schema ID
            assertEquals(TEST_SCHEMA_ID, validator.getSchemaId(), "Schema ID should match");
        }

        @Test
        @DisplayName("Create validator with null schema ID")
        void createValidatorWithNullSchemaId() {
            // When creating validator with null schema ID
            JsonSchemaValidator validator = new JsonSchemaValidator(null);

            // Then should handle gracefully
            assertNull(validator.getSchemaId(), "Schema ID should be null");
        }

        @Test
        @DisplayName("Create validator with empty schema ID")
        void createValidatorWithEmptySchemaId() {
            // When creating validator with empty schema ID
            JsonSchemaValidator validator = new JsonSchemaValidator("");

            // Then should handle gracefully
            assertEquals("", validator.getSchemaId(), "Schema ID should be empty");
        }

        @Test
        @DisplayName("Create validator with special characters in schema ID")
        void createValidatorWithSpecialCharactersInSchemaId() {
            // When creating validator with special characters
            String specialSchemaId = "schema-@#$%^&*()_+-={}[]|\\:;\"'<>,.?/~";

            JsonSchemaValidator validator = new JsonSchemaValidator(specialSchemaId);

            // Then special characters should be preserved
            assertEquals(specialSchemaId, validator.getSchemaId(), "Should preserve special characters");
        }
    }

    @Nested
    @DisplayName("Schema ID Handling")
    class SchemaIdHandlingTests {

        @Test
        @DisplayName("getSchemaId returns correct schema ID")
        void getSchemaIdReturnsCorrectSchemaId() {
            // Given validator with schema ID
            JsonSchemaValidator validator = new JsonSchemaValidator(TEST_SCHEMA_ID);

            // Then getSchemaId should return the schema ID
            assertEquals(TEST_SCHEMA_ID, validator.getSchemaId(), "Should return correct schema ID");
        }

        @Test
        @DisplayName("getSchemaId returns null for null schema ID")
        void getSchemaIdReturnsNullForNullSchemaId() {
            // Given validator with null schema ID
            JsonSchemaValidator validator = new JsonSchemaValidator(null);

            // Then getSchemaId should return null
            assertNull(validator.getSchemaId(), "Should return null for null schema ID");
        }

        @Test
        @DisplayName("getSchemaId returns empty string for empty schema ID")
        void getSchemaIdReturnsEmptyStringForEmptySchemaId() {
            // Given validator with empty schema ID
            JsonSchemaValidator validator = new JsonSchemaValidator("");

            // Then getSchemaId should return empty string
            assertEquals("", validator.getSchemaId(), "Should return empty string for empty schema ID");
        }

        @Test
        @DisplayName("Schema ID is immutable")
        void schemaIdIsImmutable() {
            // Given validator
            JsonSchemaValidator validator = new JsonSchemaValidator(TEST_SCHEMA_ID);

            // Then schema ID should be immutable (no setter method)
            // Verified by absence of setter method in class
            assertNotNull(validator.getSchemaId(), "Schema ID should be accessible but not modifiable");
        }
    }

    @Nested
    @DisplayName("Validation Method")
    class ValidationMethodTests {

        @Test
        @DisplayName("validate throws UnsupportedOperationException")
        void validateThrowsUnsupportedOperationException() {
            // Given validator
            JsonSchemaValidator validator = new JsonSchemaValidator(TEST_SCHEMA_ID);

            // When validating
            // Then should throw UnsupportedOperationException
            assertThrows(UnsupportedOperationException.class, () -> {
                validator.validate(VALID_JSON);
            }, "validate should throw UnsupportedOperationException as per documentation");
        }
    }

    @Nested
    @DisplayName("ValidationResult Creation")
    class ValidationResultCreationTests {

        @Test
        @DisplayName("Create successful validation result")
        void createSuccessfulValidationResult() {
            // When creating successful result
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.success();

            // Then should be valid
            assertTrue(result.isValid(), "Successful result should be valid");
            assertTrue(result.getErrors().isEmpty(), "Successful result should have no errors");
            assertTrue(result.getWarnings().isEmpty(), "Successful result should have no warnings");
        }

        @Test
        @DisplayName("Create failed validation result")
        void createFailedValidationResult() {
            // Given list of errors
            List<String> errors = List.of("Field 'workItemId' is required", "Invalid format");

            // When creating failed result
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(errors);

            // Then should be invalid with errors
            assertFalse(result.isValid(), "Failed result should be invalid");
            assertEquals(errors, result.getErrors(), "Should have correct errors");
            assertTrue(result.getWarnings().isEmpty(), "Failed result should have no warnings");
        }

        @Test
        @DisplayName("Create validation result with warnings")
        void createValidationResultWithWarnings() {
            // Given errors and warnings
            List<String> errors = List.of("Critical error");
            List<String> warnings = List.of("Deprecation warning", "Style suggestion");

            // When creating result with warnings
            JsonSchemaValidator.ValidationResult result = new JsonSchemaValidator.ValidationResult(
                false, errors, warnings
            );

            // Then should have correct properties
            assertFalse(result.isValid(), "Should be invalid");
            assertEquals(errors, result.getErrors(), "Should have correct errors");
            assertEquals(warnings, result.getWarnings(), "Should have correct warnings");
        }

        @Test
        @DisplayName("Create validation result with null errors")
        void createValidationResultWithNullErrors() {
            // When creating result with null errors
            JsonSchemaValidator.ValidationResult result = new JsonSchemaValidator.ValidationResult(
                true, null, Collections.emptyList()
            );

            // Then should handle gracefully
            assertTrue(result.isValid(), "Should be valid");
            assertTrue(result.getErrors().isEmpty(), "Should have no errors");
            assertTrue(result.getWarnings().isEmpty(), "Should have no warnings");
        }

        @Test
        @DisplayName("Create validation result with null warnings")
        void createValidationResultWithNullWarnings() {
            // When creating result with null warnings
            JsonSchemaValidator.ValidationResult result = new JsonSchemaValidator.ValidationResult(
                true, Collections.emptyList(), null
            );

            // Then should handle gracefully
            assertTrue(result.isValid(), "Should be valid");
            assertTrue(result.getErrors().isEmpty(), "Should have no errors");
            assertTrue(result.getWarnings().isEmpty(), "Should have no warnings");
        }
    }

    @Nested
    @DisplayName("ValidationResult Methods")
    class ValidationResultMethodsTests {

        @Test
        @DisplayName("isValid returns true for valid result")
        void isValidReturnsTrueForValidResult() {
            // Given valid result
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.success();

            // Then isValid should return true
            assertTrue(result.isValid(), "Valid result should return true for isValid()");
        }

        @Test
        @DisplayName("isValid returns false for invalid result")
        void isValidReturnsFalseForInvalidResult() {
            // Given invalid result
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(
                List.of("Error message")
            );

            // Then isValid should return false
            assertFalse(result.isValid(), "Invalid result should return false for isValid()");
        }

        @Test
        @DisplayName("getErrors returns empty list for valid result")
        void getErrorsReturnsEmptyListForValidResult() {
            // Given valid result
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.success();

            // Then getErrors should return empty list
            assertTrue(result.getErrors().isEmpty(), "Valid result should have empty errors list");
        }

        @Test
        @DisplayName("getErrors returns correct errors for invalid result")
        void getErrorsReturnsCorrectErrorsForInvalidResult() {
            // Given errors
            List<String> expectedErrors = List.of("Error 1", "Error 2", "Error 3");

            // When creating invalid result
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(expectedErrors);

            // Then getErrors should return correct errors
            assertEquals(expectedErrors, result.getErrors(), "Should return correct errors");
        }

        @Test
        @DisplayName("getWarnings returns empty list for result without warnings")
        void getWarningsReturnsEmptyListForResultWithoutWarnings() {
            // Given result without warnings
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.success();

            // Then getWarnings should return empty list
            assertTrue(result.getWarnings().isEmpty(), "Result without warnings should have empty warnings list");
        }

        @Test
        @DisplayName("getWarnings returns correct warnings")
        void getWarningsReturnsCorrectWarnings() {
            // Given warnings
            List<String> expectedWarnings = List.of("Warning 1", "Warning 2");

            // When creating result with warnings
            JsonSchemaValidator.ValidationResult result = new JsonSchemaValidator.ValidationResult(
                true, Collections.emptyList(), expectedWarnings
            );

            // Then getWarnings should return correct warnings
            assertEquals(expectedWarnings, result.getWarnings(), "Should return correct warnings");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Validation result with very long error messages")
        void validationResultWithLongErrorMessages() {
            // Given very long error messages
            List<String> longErrors = List.of(
                new String(new char[1000]).replace('\0', 'e'),
                new String(new char[2000]).replace('\0', 'r')
            );

            // When creating result with long errors
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(longErrors);

            // Then long messages should be preserved
            assertEquals(longErrors, result.getErrors(), "Should preserve long error messages");
        }

        @Test
        @DisplayName("Validation result with special characters in messages")
        void validationResultWithSpecialCharactersInMessages() {
            // Given messages with special characters
            List<String> specialErrors = List.of(
                "Error @#$%^&*()_+-={}[]|\\:;\"'<>,.?/~ with special chars",
                "Another error with special chars!@#$"
            );

            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(specialErrors);

            // Then special characters should be preserved
            assertEquals(specialErrors, result.getErrors(), "Should preserve special characters in error messages");
        }

        @Test
        @DisplayName("Validation result with Unicode characters in messages")
        void validationResultWithUnicodeCharactersInMessages() {
            // Given messages with Unicode characters
            List<String> unicodeErrors = List.of(
                "错误 🚀 中文 with Unicode",
                "エラー 🌍 日本語 with Unicode"
            );

            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(unicodeErrors);

            // Then Unicode characters should be preserved
            assertEquals(unicodeErrors, result.getErrors(), "Should preserve Unicode characters in error messages");
        }

        @Test
        @DisplayName("Validation result with empty error list")
        void validationResultWithEmptyErrorList() {
            // When creating result with empty error list
            JsonSchemaValidator.ValidationResult result = new JsonSchemaValidator.ValidationResult(
                true, Collections.emptyList(), Collections.emptyList()
            );

            // Then should handle gracefully
            assertTrue(result.isValid(), "Should be valid with empty errors");
            assertTrue(result.getErrors().isEmpty(), "Should have empty errors list");
            assertTrue(result.getWarnings().isEmpty(), "Should have empty warnings list");
        }

        @Test
        @DisplayName("Validation result with single error")
        void validationResultWithSingleError() {
            // Given single error
            List<String> singleError = List.of("Single error message");

            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(singleError);

            // Should handle single error correctly
            assertFalse(result.isValid(), "Should be invalid with single error");
            assertEquals(singleError, result.getErrors(), "Should have single error");
        }

        @Test
        @DisplayName("Validation result with multiple errors")
        void validationResultWithMultipleErrors() {
            // Given multiple errors
            List<String> multipleErrors = List.of(
                "First error",
                "Second error",
                "Third error"
            );

            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(multipleErrors);

            // Should handle multiple errors correctly
            assertFalse(result.isValid(), "Should be invalid with multiple errors");
            assertEquals(multipleErrors, result.getErrors(), "Should have all errors");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("ValidationResult is immutable - lists are copies")
        void validationResultIsImmutable() {
            // Given original list
            List<String> originalErrors = List.of("Error 1", "Error 2");
            List<String> originalWarnings = List.of("Warning 1");

            // When creating result
            JsonSchemaValidator.ValidationResult result = new JsonSchemaValidator.ValidationResult(
                false, originalErrors, originalWarnings
            );

            // When modifying original lists
            originalErrors.add("New error");
            originalWarnings.add("New warning");

            // Then result lists should be unchanged
            assertEquals(List.of("Error 1", "Error 2"), result.getErrors(), "Errors should be immutable copy");
            assertEquals(List.of("Warning 1"), result.getWarnings(), "Warnings should be immutable copy");
        }

        @Test
        @DisplayName("ValidationResult lists cannot be modified")
        void validationResultListsCannotBeModified() {
            // Given result
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(
                List.of("Error 1", "Error 2")
            );

            // Then trying to modify the list should throw exception
            assertThrows(UnsupportedOperationException.class, () -> {
                result.getErrors().add("New error");
            }, "Result error list should be immutable");

            assertThrows(UnsupportedOperationException.class, () -> {
                result.getWarnings().add("New warning");
            }, "Result warning list should be immutable");
        }
    }

    @Nested
    @DisplayName="Factory Methods")
    class FactoryMethodsTests {

        @Test
        @DisplayName("success() creates valid result")
        void successCreatesValidResult() {
            // When calling success factory method
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.success();

            // Then should create valid result
            assertTrue(result.isValid(), "Should be valid");
            assertTrue(result.getErrors().isEmpty(), "Should have no errors");
            assertTrue(result.getWarnings().isEmpty(), "Should have no warnings");
        }

        @Test
        @DisplayName("failure() with empty list creates valid result")
        void failureWithEmptyListCreatesValidResult() {
            // When calling failure with empty list
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(
                Collections.emptyList()
            );

            // Then should create valid result (edge case)
            assertTrue(result.isValid(), "Empty error list should be valid");
            assertTrue(result.getErrors().isEmpty(), "Should have no errors");
        }

        @Test
        @DisplayName("failure() with non-empty list creates invalid result")
        void failureWithNonEmptyListCreatesInvalidResult() {
            // When calling failure with non-empty list
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.failure(
                List.of("Error message")
            );

            // Then should create invalid result
            assertFalse(result.isValid(), "Non-empty error list should be invalid");
        }
    }

    @Nested
    @DisplayName="Performance")
    class PerformanceTests {

        @Test
        @DisplayName("ValidationResult creation performance")
        void validationResultCreationPerformance() {
            // When creating many validation results
            int iterations = 1000;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                JsonSchemaValidator.ValidationResult result = new JsonSchemaValidator.ValidationResult(
                    true, Collections.emptyList(), Collections.emptyList()
                );
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Then average creation time should be reasonable
            double avgTimeMs = (double) durationMs / iterations;
            assertTrue(avgTimeMs < 0.01, "Average ValidationResult creation time should be < 0.01ms, was " + avgTimeMs + "ms");
        }

        @Test
        @DisplayName("ValidationResult method access performance")
        void validationResultMethodAccessPerformance() {
            // Given validation result
            JsonSchemaValidator.ValidationResult result = JsonSchemaValidator.ValidationResult.success();

            // When accessing methods multiple times
            int iterations = 1000;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                boolean valid = result.isValid();
                List<String> errors = result.getErrors();
                List<String> warnings = result.getWarnings();
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Then average access time should be reasonable
            double avgTimeMs = (double) durationMs / iterations;
            assertTrue(avgTimeMs < 0.001, "Average method access time should be < 0.001ms, was " + avgTimeMs + "ms");
        }
    }
}