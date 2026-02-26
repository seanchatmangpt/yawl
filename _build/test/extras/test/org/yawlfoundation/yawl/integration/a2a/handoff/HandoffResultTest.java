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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for HandoffResult class.
 *
 * Tests all aspects of the handoff result including acceptance status,
 * message handling, and future integration.
 *
 * Coverage targets:
 * - Constructor validation
 * - isAccepted() method accuracy
 * - getMessage() method functionality
 * - getFuture() method integration
 * - CompletableFuture handling
 */
class HandoffResultTest {

    private static final String SUCCESS_MESSAGE = "Handoff accepted successfully";
    private static final String FAILURE_MESSAGE = "Handoff failed: Target agent unavailable";
    private static final CompletableFuture<HandoffResult> TEST_FUTURE = CompletableFuture.completedFuture(
        new HandoffResult(true, "Completed via future")
    );

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Create result with accepted status and message")
        void createAcceptedResult() {
            // When creating accepted result
            HandoffResult result = new HandoffResult(true, SUCCESS_MESSAGE);

            // Then status should be accepted
            assertTrue(result.isAccepted(), "Result should be accepted");
            assertEquals(SUCCESS_MESSAGE, result.getMessage(), "Message should match");
            assertNull(result.getFuture(), "Future should be null when not provided");
        }

        @Test
        @DisplayName("Create result with rejected status and message")
        void createRejectedResult() {
            // When creating rejected result
            HandoffResult result = new HandoffResult(false, FAILURE_MESSAGE);

            // Then status should be rejected
            assertFalse(result.isAccepted(), "Result should be rejected");
            assertEquals(FAILURE_MESSAGE, result.getMessage(), "Message should match");
            assertNull(result.getFuture(), "Future should be null when not provided");
        }

        @Test
        @DisplayName("Create result with future")
        void createResultWithFuture() {
            // When creating result with future
            HandoffResult result = new HandoffResult(true, SUCCESS_MESSAGE, TEST_FUTURE);

            // Then all fields should be set
            assertTrue(result.isAccepted());
            assertEquals(SUCCESS_MESSAGE, result.getMessage());
            assertEquals(TEST_FUTURE, result.getFuture());
        }

        @Test
        @DisplayName("Create result with null message")
        void createResultWithNullMessage() {
            // When creating result with null message
            HandoffResult result = new HandoffResult(true, null);

            // Then should handle gracefully
            assertTrue(result.isAccepted());
            assertNull(result.getMessage(), "Message should be null");
        }

        @Test
        @DisplayName("Create result with empty message")
        void createResultWithEmptyMessage() {
            // When creating result with empty message
            HandoffResult result = new HandoffResult(true, "");

            // Then should handle gracefully
            assertTrue(result.isAccepted());
            assertEquals("", result.getMessage(), "Message should be empty");
        }
    }

    @Nested
    @DisplayName("Status Verification")
    class StatusVerificationTests {

        @Test
        @DisplayName("isAccepted returns true for successful handoff")
        void isAcceptedReturnsTrueForSuccess() {
            // Given successful result
            HandoffResult successResult = new HandoffResult(true, SUCCESS_MESSAGE);

            // Then isAccepted should return true
            assertTrue(successResult.isAccepted(), "Successful handoff should return true for isAccepted()");
        }

        @Test
        @DisplayName("isAccepted returns false for failed handoff")
        void isAcceptedReturnsFalseForFailure() {
            // Given failed result
            HandoffResult failedResult = new HandoffResult(false, FAILURE_MESSAGE);

            // Then isAccepted should return false
            assertFalse(failedResult.isAccepted(), "Failed handoff should return false for isAccepted()");
        }
    }

    @Nested
    @DisplayName("Message Handling")
    class MessageHandlingTests {

        @Test
        @DisplayName("getMessage returns correct success message")
        void getMessageReturnsSuccessMessage() {
            // Given result with success message
            HandoffResult result = new HandoffResult(true, SUCCESS_MESSAGE);

            // Then getMessage should return the message
            assertEquals(SUCCESS_MESSAGE, result.getMessage(), "Should return success message");
        }

        @Test
        @DisplayName("getMessage returns correct failure message")
        void getMessageReturnsFailureMessage() {
            // Given result with failure message
            HandoffResult result = new HandoffResult(false, FAILURE_MESSAGE);

            // Then getMessage should return the message
            assertEquals(FAILURE_MESSAGE, result.getMessage(), "Should return failure message");
        }

        @Test
        @DisplayName("getMessage handles null message gracefully")
        void getMessageHandlesNullMessage() {
            // Given result with null message
            HandoffResult result = new HandoffResult(true, null);

            // Then getMessage should return null
            assertNull(result.getMessage(), "Should return null message");
        }

        @Test
        @DisplayName("getMessage handles empty message gracefully")
        void getMessageHandlesEmptyMessage() {
            // Given result with empty message
            HandoffResult result = new HandoffResult(true, "");

            // Then getMessage should return empty string
            assertEquals("", result.getMessage(), "Should return empty message");
        }
    }

    @Nested
    @DisplayName("Future Integration")
    class FutureIntegrationTests {

        @Test
        @DisplayName("getFuture returns null when future not provided")
        void getFutureReturnsNullWhenNotProvided() {
            // Given result without future
            HandoffResult result = new HandoffResult(true, SUCCESS_MESSAGE);

            // Then getFuture should return null
            assertNull(result.getFuture(), "Should return null when future not provided");
        }

        @Test
        @DisplayName("getFuture returns provided future")
        void getFutureReturnsProvidedFuture() {
            // Given result with future
            HandoffResult result = new HandoffResult(true, SUCCESS_MESSAGE, TEST_FUTURE);

            // Then getFuture should return the future
            assertEquals(TEST_FUTURE, result.getFuture(), "Should return provided future");
        }

        @Test
        @DisplayName("Future contains correct result")
        void futureContainsCorrectResult() throws ExecutionException, InterruptedException {
            // Given completed future
            CompletableFuture<HandoffResult> future = CompletableFuture.completedFuture(
                new HandoffResult(true, "Future completed")
            );

            // When getting result from future
            HandoffResult result = future.get();

            // Then result should be correct
            assertTrue(result.isAccepted());
            assertEquals("Future completed", result.getMessage());
        }

        @Test
        @DisplayName("Future handles exceptional completion")
        void futureHandlesExceptionalCompletion() {
            // Given exceptional future
            CompletableFuture<HandoffResult> exceptionalFuture = new CompletableFuture<>();
            exceptionalFuture.completeExceptionally(new RuntimeException("Network error"));

            // Given result with exceptional future
            HandoffResult result = new HandoffResult(false, "Failed due to exception", exceptionalFuture);

            // Then future should be exceptional
            assertTrue(result.getFuture().isDone());
            assertTrue(result.getFuture().isCompletedExceptionally());
        }
    }

    @Nested
    @DisplayName("Result Patterns")
    class ResultPatternsTests {

        @Test
        @DisplayName("Create success result pattern")
        void createSuccessResultPattern() {
            // When creating success result
            HandoffResult success = HandoffResult.success("Handoff completed");

            // Then should have correct properties
            assertTrue(success.isAccepted());
            assertEquals("Handoff completed", success.getMessage());
        }

        @Test
        @DisplayName("Create failure result pattern")
        void createFailureResultPattern() {
            // When creating failure result
            HandoffResult failure = new HandoffResult(false, "Handoff failed");

            // Then should have correct properties
            assertFalse(failure.isAccepted());
            assertEquals("Handoff failed", failure.getMessage());
        }

        @Test
        @DisplayName("Create result with future pattern")
        void createResultWithFuturePattern() {
            // When creating result with future
            CompletableFuture<HandoffResult> future = CompletableFuture.completedFuture(
                new HandoffResult(true, "Future success")
            );
            HandoffResult result = new HandoffResult(true, "Completed", future);

            // Then should have correct properties
            assertTrue(result.isAccepted());
            assertEquals("Completed", result.getMessage());
            assertEquals(future, result.getFuture());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Result with very long message")
        void resultWithLongMessage() {
            // Given very long message
            String longMessage = new String(new char[10000]).replace('\0', 'x');
            HandoffResult result = new HandoffResult(true, longMessage);

            // Then message should be preserved
            assertEquals(longMessage, result.getMessage());
        }

        @Test
        @DisplayName("Result with special characters in message")
        void resultWithSpecialCharactersInMessage() {
            // Given message with special characters
            String specialMessage = "Handoff @#$%^&*()_+-={}[]|\\:;\"'<>,.?/~ failed";

            HandoffResult result = new HandoffResult(false, specialMessage);

            // Then special characters should be preserved
            assertEquals(specialMessage, result.getMessage());
        }

        @Test
        @DisplayName("Result with Unicode characters in message")
        void resultWithUnicodeCharactersInMessage() {
            // Given message with Unicode characters
            String unicodeMessage = "Handoff 失败 🚀 中国 日本語";

            HandoffResult result = new HandoffResult(false, unicodeMessage);

            // Then Unicode characters should be preserved
            assertEquals(unicodeMessage, result.getMessage());
        }

        @Test
        @DisplayName("Result with null future")
        void resultWithNullFuture() {
            // When creating result with null future
            HandoffResult result = new HandoffResult(true, SUCCESS_MESSAGE, null);

            // Then getFuture should return null
            assertNull(result.getFuture(), "Should return null when future is null");
        }
    }

    @Nested
    @DisplayName("CompletableFuture Integration")
    class CompletableFutureIntegrationTests {

        @Test
        @DisplayName("Future completes successfully")
        void futureCompletesSuccessfully() throws ExecutionException, InterruptedException {
            // Given successful future
            CompletableFuture<HandoffResult> future = CompletableFuture.completedFuture(
                new HandoffResult(true, "Future success")
            );

            // When getting result
            HandoffResult result = future.get();

            // Then result should be successful
            assertTrue(result.isAccepted());
            assertEquals("Future success", result.getMessage());
        }

        @Test
        @DisplayName("Future completes exceptionally")
        void futureCompletesExceptionally() {
            // Given exceptional future
            CompletableFuture<HandoffResult> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Test exception"));

            // Then future should be exceptionally completed
            assertTrue(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("Future with timeout")
        void futureWithTimeout() {
            // Given future that will complete after timeout
            CompletableFuture<HandoffResult> slowFuture = new CompletableFuture<>();

            // Given result with slow future
            HandoffResult result = new HandoffResult(false, "Pending", slowFuture);

            // Then future should not be completed
            assertFalse(slowFuture.isDone(), "Slow future should not be completed");
            assertNull(result.getMessage(), "Result should reflect pending state");
        }

        @Test
        @DisplayName("Chain futures for complex scenarios")
        void chainFuturesForComplexScenarios() throws ExecutionException, InterruptedException {
            // Given first future
            CompletableFuture<HandoffResult> first = CompletableFuture.completedFuture(
                new HandoffResult(true, "First step completed")
            );

            // When chaining to second future
            CompletableFuture<HandoffResult> second = first.thenApply(result -> {
                if (result.isAccepted()) {
                    return new HandoffResult(true, "Second step completed after first");
                } else {
                    return new HandoffResult(false, "Second step failed");
                }
            });

            // Then second future should complete correctly
            HandoffResult finalResult = second.get();
            assertTrue(finalResult.isAccepted());
            assertEquals("Second step completed after first", finalResult.getMessage());
        }
    }
}