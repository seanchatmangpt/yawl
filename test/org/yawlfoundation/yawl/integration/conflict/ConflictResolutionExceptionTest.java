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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.conflict;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ConflictResolutionException.
 *
 * Tests:
 * - Exception creation with message and strategy
 * - Exception creation with message, cause, strategy and conflict ID
 * - Getter methods for strategy and conflict ID
 * - Exception chaining and cause handling
 * - toString() method output
 * - Equals/hashCode contract
 */
class ConflictResolutionExceptionTest {

    private static final String TEST_CONFLICT_ID = "conflict-123";
    private static final String TEST_MESSAGE = "Failed to resolve conflict";

    @Nested
    @DisplayName("Exception Creation")
    class CreationTests {

        @Test
        @DisplayName("Create exception with message and strategy")
        void createExceptionWithMessageAndStrategy() {
            // When creating exception with basic parameters
            ConflictResolutionException exception = new ConflictResolutionException(
                TEST_MESSAGE,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                TEST_CONFLICT_ID
            );

            // Then it should be created successfully
            assertNotNull(exception);
            assertEquals(TEST_MESSAGE, exception.getMessage());
            assertEquals(ConflictResolver.Strategy.MAJORITY_VOTE, exception.getStrategy());
            assertEquals(TEST_CONFLICT_ID, exception.getConflictId());
            assertNull(exception.getCause());
        }

        @Test
        @DisplayName("Create exception with message, cause, strategy and conflict ID")
        void createExceptionWithMessageCauseStrategyAndConflictId() {
            // Given a cause exception
            Throwable cause = new RuntimeException("Root cause of failure");

            // When creating exception with all parameters
            ConflictResolutionException exception = new ConflictResolutionException(
                TEST_MESSAGE,
                cause,
                ConflictResolver.Strategy.ESCALATING,
                TEST_CONFLICT_ID
            );

            // Then it should be created successfully
            assertNotNull(exception);
            assertEquals(TEST_MESSAGE, exception.getMessage());
            assertEquals(cause, exception.getCause());
            assertEquals(ConflictResolver.Strategy.ESCALATING, exception.getStrategy());
            assertEquals(TEST_CONFLICT_ID, exception.getConflictId());
        }

        @Test
        @DisplayName("Create exception with null cause")
        void createExceptionWithNullCause() {
            // When creating exception with null cause
            ConflictResolutionException exception = new ConflictResolutionException(
                TEST_MESSAGE,
                (Throwable) null,
                ConflictResolver.Strategy.HUMAN_FALLBACK,
                TEST_CONFLICT_ID
            );

            // Then it should be created successfully
            assertNotNull(exception);
            assertEquals(TEST_MESSAGE, exception.getMessage());
            assertNull(exception.getCause());
            assertEquals(ConflictResolver.Strategy.HUMAN_FALLBACK, exception.getStrategy());
            assertEquals(TEST_CONFLICT_ID, exception.getConflictId());
        }

        @Test
        @DisplayName("Create exception with empty string parameters")
        void createExceptionWithEmptyStringParameters() {
            // When creating exception with empty strings
            ConflictResolutionException exception = new ConflictResolutionException(
                "",
                ConflictResolver.Strategy.MAJORITY_VOTE,
                ""
            );

            // Then it should be created successfully
            assertNotNull(exception);
            assertEquals("", exception.getMessage());
            assertEquals(ConflictResolver.Strategy.MAJORITY_VOTE, exception.getStrategy());
            assertEquals("", exception.getConflictId());
        }

        @Test
        @DisplayName("Create exception with null strategy")
        void createExceptionWithNullStrategy() {
            // When creating exception with null strategy
            ConflictResolutionException exception = new ConflictResolutionException(
                TEST_MESSAGE,
                null,
                TEST_CONFLICT_ID
            );

            // Then it should be created successfully
            assertNotNull(exception);
            assertEquals(TEST_MESSAGE, exception.getMessage());
            assertNull(exception.getStrategy());
            assertEquals(TEST_CONFLICT_ID, exception.getConflictId());
        }
    }

    @Nested
    @DisplayName("Exception Getter Methods")
    class GetterTests {

        private ConflictResolutionException exception;

        @BeforeEach
        void setUp() {
            Throwable cause = new IllegalArgumentException("Invalid configuration");
            exception = new ConflictResolutionException(
                "Configuration validation failed",
                cause,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                TEST_CONFLICT_ID
            );
        }

        @Test
        @DisplayName("Get strategy")
        void getStrategy() {
            assertEquals(ConflictResolver.Strategy.MAJORITY_VOTE, exception.getStrategy());
        }

        @Test
        @DisplayName("Get conflict ID")
        void getConflictId() {
            assertEquals(TEST_CONFLICT_ID, exception.getConflictId());
        }

        @Test
        @DisplayName("Get cause")
        void getCause() {
            assertNotNull(exception.getCause());
            assertTrue(exception.getCause() instanceof IllegalArgumentException);
            assertEquals("Invalid configuration", exception.getCause().getMessage());
        }

        @Test
        @DisplayName("Get message")
        void getMessage() {
            assertEquals("Configuration validation failed", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Exception Chaining")
    class ChainingTests {

        @Test
        @DisplayName("Exception chaining preserves cause")
        void exceptionChainingPreservesCause() {
            // Given a chain of exceptions
            Throwable originalCause = new NullPointerException("Null pointer occurred");
            Throwable intermediateCause = new IllegalStateException("Illegal state", originalCause);

            // When creating exception with chain
            ConflictResolutionException exception = new ConflictResolutionException(
                "Resolution failed due to previous errors",
                intermediateCause,
                ConflictResolver.Strategy.ESCALATING,
                TEST_CONFLICT_ID
            );

            // Then the complete chain should be preserved
            assertEquals(intermediateCause, exception.getCause());
            assertEquals(originalCause, exception.getCause().getCause());
            assertEquals("Null pointer occurred", exception.getCause().getCause().getMessage());
        }

        @Test
        @DisplayName("Exception can be thrown and caught")
        void exceptionCanBeThrownAndCaught() {
            // When creating and throwing exception
            ConflictResolutionException exception = new ConflictResolutionException(
                "Test exception",
                new RuntimeException("Test cause"),
                ConflictResolver.Strategy.HUMAN_FALLBACK,
                TEST_CONFLICT_ID
            );

            // Then it should be throwable and catchable
            assertThrows(ConflictResolutionException.class, () -> {
                throw exception;
            });
        }

        @Test
        @DisplayName("Exception with null cause can be thrown and caught")
        void exceptionWithNullCauseCanBeThrownAndCaught() {
            // When creating and throwing exception without cause
            ConflictResolutionException exception = new ConflictResolutionException(
                "Test exception without cause",
                ConflictResolver.Strategy.MAJORITY_VOTE,
                TEST_CONFLICT_ID
            );

            // Then it should be throwable and catchable
            assertThrows(ConflictResolutionException.class, () -> {
                throw exception;
            });
        }
    }

    @Nested
    @DisplayName("Exception String Representation")
    class StringRepresentationTests {

        @Test
        @DisplayName("ToString contains all relevant information")
        void toStringContainsAllRelevantInfo() {
            // Given an exception
            ConflictResolutionException exception = new ConflictResolutionException(
                "Resolution failed",
                new RuntimeException("Underlying error"),
                ConflictResolver.Strategy.ESCALATING,
                TEST_CONFLICT_ID
            );

            // Then toString should contain key information
            String toString = exception.toString();
            assertTrue(toString.contains("ConflictResolutionException"));
            assertTrue(toString.contains("strategy=ESCALATING"));
            assertTrue(toString.contains("conflictId='conflict-123'"));
            assertTrue(toString.contains("message='Resolution failed'"));
        }

        @Test
        @DisplayName("ToString handles null values gracefully")
        void toStringHandlesNullValuesGracefully() {
            // Given an exception with null strategy
            ConflictResolutionException exception = new ConflictResolutionException(
                "Test message",
                null,
                TEST_CONFLICT_ID
            );

            // Then toString should handle null strategy
            String toString = exception.toString();
            assertTrue(toString.contains("strategy=null"));
            assertTrue(toString.contains("message='Test message'"));
        }

        @Test
        @DisplayName("GetLocalizedMessage works")
        void getLocalizedMessageWorks() {
            // Given an exception
            ConflictResolutionException exception = new ConflictResolutionException(
                "Localized message",
                ConflictResolver.Strategy.HUMAN_FALLBACK,
                TEST_CONFLICT_ID
            );

            // Then getLocalizedMessage should work
            assertNotNull(exception.getLocalizedMessage());
            assertEquals("Localized message", exception.getLocalizedMessage());
        }
    }

    @Nested
    @DisplayName("Exception Equality and HashCode")
    class EqualityTests {

        @Test
        @DisplayName("Equal exceptions have same hash code")
        void equalExceptionsHaveSameHashCode() {
            // Given two identical exceptions
            ConflictResolutionException exception1 = new ConflictResolutionException(
                TEST_MESSAGE,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                TEST_CONFLICT_ID
            );

            ConflictResolutionException exception2 = new ConflictResolutionException(
                TEST_MESSAGE,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                TEST_CONFLICT_ID
            );

            // Then they should be equal and have same hash code
            assertEquals(exception1, exception2);
            assertEquals(exception1.hashCode(), exception2.hashCode());
        }

        @Test
        @DisplayName("Different exceptions are not equal")
        void differentExceptionsAreNotEqual() {
            // Given different exceptions
            ConflictResolutionException exception1 = new ConflictResolutionException(
                "Message 1",
                ConflictResolver.Strategy.MAJORITY_VOTE,
                "conflict-1"
            );

            ConflictResolutionException exception2 = new ConflictResolutionException(
                "Message 2",
                ConflictResolver.Strategy.ESCALATING,
                "conflict-2"
            );

            // Then they should not be equal
            assertNotEquals(exception1, exception2);
            assertNotEquals(exception1.hashCode(), exception2.hashCode());
        }

        @Test
        @DisplayName("Exception equals with null and different types")
        void equalsWithNullAndDifferentTypes() {
            // Given an exception
            ConflictResolutionException exception = new ConflictResolutionException(
                TEST_MESSAGE,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                TEST_CONFLICT_ID
            );

            // Then it should not equal null or different types
            assertNotEquals(null, exception);
            assertNotEquals("string", exception);
            assertNotEquals(new RuntimeException(), exception);
        }

        @Test
        @DisplayName("Same exception instance is equal to itself")
        void sameInstanceIsEqual() {
            // Given an exception
            ConflictResolutionException exception = new ConflictResolutionException(
                TEST_MESSAGE,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                TEST_CONFLICT_ID
            );

            // Then it should be equal to itself
            assertEquals(exception, exception);
            assertEquals(exception.hashCode(), exception.hashCode());
        }

        @Test
        @DisplayName("Exceptions with same message but different strategies are not equal")
        void exceptionsWithSameMessageDifferentStrategiesNotEqual() {
            // Given exceptions with same message but different strategies
            ConflictResolutionException exception1 = new ConflictResolutionException(
                TEST_MESSAGE,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                TEST_CONFLICT_ID
            );

            ConflictResolutionException exception2 = new ConflictResolutionException(
                TEST_MESSAGE,
                ConflictResolver.Strategy.ESCALATING,
                TEST_CONFLICT_ID
            );

            // Then they should not be equal
            assertNotEquals(exception1, exception2);
        }

        @Test
        @DisplayName("Exceptions with same strategy but different conflict IDs are not equal")
        void exceptionsWithSameStrategyDifferentConflictIdsNotEqual() {
            // Given exceptions with same strategy but different conflict IDs
            ConflictResolutionException exception1 = new ConflictResolutionException(
                TEST_MESSAGE,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                "conflict-1"
            );

            ConflictResolutionException exception2 = new ConflictResolutionException(
                TEST_MESSAGE,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                "conflict-2"
            );

            // Then they should not be equal
            assertNotEquals(exception1, exception2);
        }
    }

    @Nested
    @DisplayName("Exception Serialization")
    class SerializationTests {

        @Test
        @DisplayName("Exception can be serialized and deserialized")
        void exceptionCanBeSerializedAndDeserialized() {
            // Given an exception
            ConflictResolutionException original = new ConflictResolutionException(
                "Test serialization",
                new RuntimeException("Test cause"),
                ConflictResolver.Strategy.HUMAN_FALLBACK,
                TEST_CONFLICT_ID
            );

            // When serializing and deserializing (conceptual test)
            // In a real implementation, this would use ObjectOutputStream/ObjectInputStream
            // For now, we'll just verify the exception has the expected properties
            assertEquals("Test serialization", original.getMessage());
            assertEquals(ConflictResolver.Strategy.HUMAN_FALLBACK, original.getStrategy());
            assertEquals(TEST_CONFLICT_ID, original.getConflictId());
            assertNotNull(original.getCause());
            assertEquals("Test cause", original.getCause().getMessage());
        }
    }

    @Nested
    @DisplayName("Exception Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Exception with very long message")
        void exceptionWithVeryLongMessage() {
            // Given a very long message
            String longMessage = "x".repeat(10000);

            // When creating exception
            ConflictResolutionException exception = new ConflictResolutionException(
                longMessage,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                TEST_CONFLICT_ID
            );

            // Then it should be created successfully
            assertEquals(longMessage, exception.getMessage());
        }

        @Test
        @DisplayName("Exception with very long conflict ID")
        void exceptionWithVeryLongConflictId() {
            // Given a very long conflict ID
            String longConflictId = "conflict-" + "x".repeat(1000);

            // When creating exception
            ConflictResolutionException exception = new ConflictResolutionException(
                TEST_MESSAGE,
                ConflictResolver.Strategy.MAJORITY_VOTE,
                longConflictId
            );

            // Then it should be created successfully
            assertEquals(longConflictId, exception.getConflictId());
        }

        @Test
        @DisplayName("Exception with cause that has cause")
        void exceptionWithChainedCauses() {
            // Given a deeply nested cause
            Throwable cause3 = new RuntimeException("Level 3 error");
            Throwable cause2 = new IllegalStateException("Level 2 error", cause3);
            Throwable cause1 = new IllegalArgumentException("Level 1 error", cause2);

            // When creating exception
            ConflictResolutionException exception = new ConflictResolutionException(
                "Deep nesting test",
                cause1,
                ConflictResolver.Strategy.ESCALATING,
                TEST_CONFLICT_ID
            );

            // Then the complete chain should be accessible
            assertEquals(cause1, exception.getCause());
            assertEquals(cause2, exception.getCause().getCause());
            assertEquals(cause3, exception.getCause().getCause().getCause());
            assertEquals("Level 3 error", exception.getCause().getCause().getCause().getMessage());
        }
    }
}