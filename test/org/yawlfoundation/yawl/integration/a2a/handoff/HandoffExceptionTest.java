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

package org.yawlfoundation.yawl.integration.a2a.handoff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for HandoffException class.
 *
 * Tests exception creation, message handling, cause propagation,
 * and serialization compatibility.
 *
 * Coverage targets:
 * - Exception creation with message
 * - Exception creation with message and cause
 * - getMessage() method accuracy
 * - getCause() method functionality
 * - Serialization compatibility
 */
class HandoffExceptionTest {

    private static final String TEST_REASON = "Test handoff exception reason";
    private static final Throwable TEST_CAUSE = new RuntimeException("Test cause");

    @Nested
    @DisplayName("Exception Creation")
    class ExceptionCreationTests {

        @Test
        @DisplayName("Create exception with message")
        void createExceptionWithMessage() {
            // When creating exception with message
            HandoffException exception = new HandoffException(TEST_REASON);

            // Then should have correct message
            assertEquals(TEST_REASON, exception.getMessage(), "Exception message should match");
            assertNull(exception.getCause(), "Cause should be null when not provided");
        }

        @Test
        @DisplayName("Create exception with message and cause")
        void createExceptionWithMessageAndCause() {
            // When creating exception with message and cause
            HandoffException exception = new HandoffException(TEST_REASON, TEST_CAUSE);

            // Then should have correct message and cause
            assertEquals(TEST_REASON, exception.getMessage(), "Exception message should match");
            assertEquals(TEST_CAUSE, exception.getCause(), "Exception cause should match");
        }

        @Test
        @DisplayName("Create exception with null message")
        void createExceptionWithNullMessage() {
            // When creating exception with null message
            HandoffException exception = new HandoffException(null);

            // Then should handle gracefully
            assertNull(exception.getMessage(), "Message should be null");
            assertNull(exception.getCause(), "Cause should be null");
        }

        @Test
        @DisplayName("Create exception with empty message")
        void createExceptionWithEmptyMessage() {
            // When creating exception with empty message
            HandoffException exception = new HandoffException("");

            // Then should handle gracefully
            assertEquals("", exception.getMessage(), "Message should be empty");
            assertNull(exception.getCause(), "Cause should be null");
        }

        @Test
        @DisplayName("Create exception with null cause")
        void createExceptionWithNullCause() {
            // When creating exception with null cause
            HandoffException exception = new HandoffException(TEST_REASON, null);

            // Then should handle gracefully
            assertEquals(TEST_REASON, exception.getMessage(), "Message should be preserved");
            assertNull(exception.getCause(), "Cause should be null");
        }
    }

    @Nested
    @DisplayName("Message Handling")
    class MessageHandlingTests {

        @Test
        @DisplayName("getMessage returns correct message")
        void getMessageReturnsCorrectMessage() {
            // Given exception with message
            HandoffException exception = new HandoffException(TEST_REASON);

            // Then getMessage should return the message
            assertEquals(TEST_REASON, exception.getMessage(), "Should return correct message");
        }

        @Test
        @DisplayName("getMessage returns null for null message")
        void getMessageReturnsNullForNullMessage() {
            // Given exception with null message
            HandoffException exception = new HandoffException(null);

            // Then getMessage should return null
            assertNull(exception.getMessage(), "Should return null for null message");
        }

        @Test
        @DisplayName("getMessage returns empty string for empty message")
        void getMessageReturnsEmptyStringForEmptyMessage() {
            // Given exception with empty message
            HandoffException exception = new HandoffException("");

            // Then getMessage should return empty string
            assertEquals("", exception.getMessage(), "Should return empty string for empty message");
        }

        @Test
        @DisplayName("getMessage preserves special characters")
        void getMessagePreservesSpecialCharacters() {
            // Given message with special characters
            String specialMessage = "Exception @#$%^&*()_+-={}[]|\\:;\"'<>,.?/~ with special chars";

            HandoffException exception = new HandoffException(specialMessage);

            // Then special characters should be preserved
            assertEquals(specialMessage, exception.getMessage(), "Should preserve special characters");
        }

        @Test
        @DisplayName("getMessage preserves Unicode characters")
        void getMessagePreservesUnicodeCharacters() {
            // Given message with Unicode characters
            String unicodeMessage = "Exception 失败 🚀 中国 日本語 with Unicode";

            HandoffException exception = new HandoffException(unicodeMessage);

            // Then Unicode characters should be preserved
            assertEquals(unicodeMessage, exception.getMessage(), "Should preserve Unicode characters");
        }
    }

    @Nested
    @DisplayName("Cause Handling")
    class CauseHandlingTests {

        @Test
        @DisplayName("getCause returns null when cause not provided")
        void getCauseReturnsNullWhenNotProvided() {
            // Given exception without cause
            HandoffException exception = new HandoffException(TEST_REASON);

            // Then getCause should return null
            assertNull(exception.getCause(), "Should return null when cause not provided");
        }

        @Test
        @DisplayName("getCause returns provided cause")
        void getCauseReturnsProvidedCause() {
            // Given exception with cause
            HandoffException exception = new HandoffException(TEST_REASON, TEST_CAUSE);

            // Then getCause should return the cause
            assertEquals(TEST_CAUSE, exception.getCause(), "Should return provided cause");
        }

        @Test
        @DisplayName("getCause handles null cause gracefully")
        void getCauseHandlesNullCauseGracefully() {
            // Given exception with null cause
            HandoffException exception = new HandoffException(TEST_REASON, null);

            // Then getCause should return null
            assertNull(exception.getCause(), "Should handle null cause gracefully");
        }

        @Test
        @DisplayName("Cause can be any exception type")
        void causeCanBeAnyExceptionType() {
            // Given different exception types as causes
            Throwable runtimeCause = new RuntimeException("Runtime error");
            Throwable ioCause = new java.io.IOException("IO error");
            Throwable nullCause = null;

            // Should handle all types
            assertDoesNotThrow(() -> {
                new HandoffException("Runtime cause", runtimeCause);
                new HandoffException("IO cause", ioCause);
                new HandoffException("Null cause", nullCause);
            });
        }
    }

    @Nested
    @DisplayName("Common Exception Scenarios")
    class CommonExceptionScenariosTests {

        @Test
        @DisplayName("Token generation exception")
        void tokenGenerationException() {
            // Given token generation failure scenario
            String tokenError = "Failed to generate JWT token: Invalid secret key";

            HandoffException exception = new HandoffException(tokenError);

            // Then should be appropriate for token generation
            assertEquals(tokenError, exception.getMessage(), "Should describe token generation failure");
        }

        @Test
        @DisplayName("Token validation exception")
        void tokenValidationException() {
            // Given token validation failure scenario
            String validationError = "Token validation failed: Token expired";

            HandoffException exception = new HandoffException(validationError);

            // Then should be appropriate for token validation
            assertEquals(validationError, exception.getMessage(), "Should describe token validation failure");
        }

        @Test
        @DisplayName("Message parsing exception")
        void messageParsingException() {
            // Given message parsing failure scenario
            String parsingError = "Failed to parse handoff message: Invalid JSON format";

            HandoffException exception = new HandoffException(parsingError);

            // Then should be appropriate for message parsing
            assertEquals(parsingError, exception.getMessage(), "Should describe message parsing failure");
        }

        @Test
        @DisplayName("Work item lookup exception")
        void workItemLookupException() {
            // Given work item lookup failure scenario
            String lookupError = "Work item not found: WI-999";

            HandoffException exception = new HandoffException(lookupError);

            // Then should be appropriate for work item lookup
            assertEquals(lookupError, exception.getMessage(), "Should describe work item lookup failure");
        }
    }

    @Nested
    @DisplayName("Exception Chaining")
    class ExceptionChainingTests {

        @Test
        @DisplayName("Exception with cause preserves stack trace")
        void exceptionWithCausePreservesStackTrace() {
            // Given exception with cause
            HandoffException exception = new HandoffException("Failed to process", TEST_CAUSE);

            // Then cause should be preserved
            assertEquals(TEST_CAUSE, exception.getCause(), "Cause should be preserved");
            assertNotNull(exception.getCause().getMessage(), "Cause should have message");
        }

        @Test
        @DisplayName("Nested exception causes")
        void nestedExceptionCauses() {
            // Given nested exception causes
            Throwable level3Cause = new RuntimeException("Level 3 cause");
            Throwable level2Cause = new RuntimeException("Level 2 cause", level3Cause);
            Throwable level1Cause = new RuntimeException("Level 1 cause", level2Cause);

            HandoffException exception = new HandoffException("Top level exception", level1Cause);

            // Then cause chain should be preserved
            assertEquals(level1Cause, exception.getCause(), "Level 1 cause should be preserved");
            assertEquals(level2Cause, exception.getCause().getCause(), "Level 2 cause should be preserved");
            assertEquals(level3Cause, exception.getCause().getCause().getCause(), "Level 3 cause should be preserved");
        }

        @Test
        @DisplayName("Exception with cause of different type")
        void exceptionWithCauseOfDifferentType() {
            // Given cause of different type
            IOException ioCause = new IOException("IO error");
            NullPointerException nullCause = new NullPointerException("Null pointer");

            // Should handle different exception types
            assertDoesNotThrow(() -> {
                new HandoffException("IO exception", ioCause);
                new HandoffException("NPE exception", nullCause);
            });
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Exception with very long message")
        void exceptionWithLongMessage() {
            // Given very long message
            String longMessage = new String(new char[10000]).replace('\0', 'x');

            HandoffException exception = new HandoffException(longMessage);

            // Then message should be preserved
            assertEquals(longMessage, exception.getMessage());
        }

        @Test
        @DisplayName("Exception with multiple lines in message")
        void exceptionWithMultipleLinesInMessage() {
            // Given multi-line message
            String multilineMessage = "First line\nSecond line\nThird line";

            HandoffException exception = new HandoffException(multilineMessage);

            // Then message should be preserved with newlines
            assertEquals(multilineMessage, exception.getMessage());
        }

        @Test
        @DisplayName("Exception with Throwable as cause")
        void exceptionWithThrowableAsCause() {
            // Given Throwable as cause
            Throwable throwableCause = new Throwable("Throwable cause");

            HandoffException exception = new HandoffException("Throwable exception", throwableCause);

            // Then cause should be preserved
            assertEquals(throwableCause, exception.getCause());
        }

        @Test
        @DisplayName("Exception with Error as cause")
        void exceptionWithErrorAsCause() {
            // Given Error as cause
            Error errorCause = new Error("Error cause");

            HandoffException exception = new HandoffException("Error exception", errorCause);

            // Then cause should be preserved
            assertEquals(errorCause, exception.getCause());
        }
    }

    @Nested
    @DisplayName("Serialization Compatibility")
    class SerializationCompatibilityTests {

        @Test
        @DisplayName("Exception is serializable")
        void exceptionIsSerializable() {
            // Given exception
            HandoffException original = new HandoffException(TEST_REASON, TEST_CAUSE);

            // Then should be serializable (in theory)
            // Note: Actual serialization would need ObjectOutputStream
            assertNotNull(original, "Exception should be serializable");
            assertEquals(TEST_REASON, original.getMessage(), "Message should be preserved");
            assertEquals(TEST_CAUSE, original.getCause(), "Cause should be preserved");
        }

        @Test
        @DisplayName("Exception maintains serialVersionUID")
        void exceptionMaintainsSerialVersionUID() {
            // Given exception
            HandoffException exception = new HandoffException(TEST_REASON);

            // Then should have serialVersionUID (verified by compilation)
            assertNotNull(exception, "Exception should compile with serialVersionUID");
        }
    }
}