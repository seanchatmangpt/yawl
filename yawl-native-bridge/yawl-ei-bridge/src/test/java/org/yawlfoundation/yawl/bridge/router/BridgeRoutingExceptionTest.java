/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.bridge.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BridgeRoutingException class.
 */
class BridgeRoutingExceptionTest {

    private static final NativeCall TEST_CALL = NativeCall.of(
        "http://example.org/subject",
        "http://example.org/predicate",
        "http://example.org/object",
        CallPattern.JVM
    );

    @Nested
    @DisplayName("Basic Exception Creation")
    class BasicCreation {

        @Test
        @DisplayName("Create with message only")
        void testCreateWithMessage() {
            BridgeRoutingException exception = new BridgeRoutingException("Test message");

            assertEquals("Test message", exception.getMessage());
            assertNull(exception.getFailedCall());
            assertNull(exception.getFailedPattern());
            assertNull(exception.getCause());
        }

        @Test
        @DisplayName("Create with message and cause")
        void testCreateWithMessageAndCause() {
            RuntimeException cause = new RuntimeException("Root cause");
            BridgeRoutingException exception = new BridgeRoutingException("Test message", cause);

            assertEquals("Test message", exception.getMessage());
            assertNull(exception.getFailedCall());
            assertNull(exception.getFailedPattern());
            assertSame(cause, exception.getCause());
        }
    }

    @Nested
    @DisplayName("Contextual Exception Creation")
    class ContextualCreation {

        @Test
        @DisplayName("Create with failed call")
        void testCreateWithFailedCall() {
            BridgeRoutingException exception = new BridgeRoutingException(
                "Test message", TEST_CALL, null
            );

            assertEquals("Test message (call: http://example.org/subject http://example.org/predicate http://example.org/object .) (pattern: JVM)",
                        exception.getMessage());
            assertEquals(TEST_CALL, exception.getFailedCall());
            assertEquals(CallPattern.JVM, exception.getFailedPattern());
        }

        @Test
        @DisplayName("Create with failed call and cause")
        void testCreateWithFailedCallAndCause() {
            RuntimeException cause = new RuntimeException("Root cause");
            BridgeRoutingException exception = new BridgeRoutingException(
                "Test message", TEST_CALL, cause
            );

            assertEquals("Test message (call: http://example.org/subject http://example.org/predicate http://example.org/object .) (pattern: JVM)",
                        exception.getMessage());
            assertEquals(TEST_CALL, exception.getFailedCall());
            assertEquals(CallPattern.JVM, exception.getFailedPattern());
            assertSame(cause, exception.getCause());
        }
    }

    @Nested
    @DisplayName("Message Retrieval")
    class MessageRetrieval {

        @Test
        @DisplayName("Get detailed message with context")
        void testGetMessageWithContext() {
            BridgeRoutingException exception = new BridgeRoutingException(
                "Test message", TEST_CALL, null
            );

            String detailedMessage = exception.getMessage();
            assertTrue(detailedMessage.contains("Test message"));
            assertTrue(detailedMessage.contains("http://example.org/subject"));
            assertTrue(detailedMessage.contains("JVM"));
        }

        @Test
        @DisplayName("Get short message without context")
        void testGetShortMessage() {
            BridgeRoutingException exception = new BridgeRoutingException(
                "Test message", TEST_CALL, null
            );

            String shortMessage = exception.getShortMessage();
            assertEquals("Test message", shortMessage);
            assertFalse(shortMessage.contains("http://example.org"));
        }

        @Test
        @DisplayName("Get short message when no call is provided")
        void testGetShortMessageNoCall() {
            BridgeRoutingException exception = new BridgeRoutingException("Test message");

            String shortMessage = exception.getShortMessage();
            assertEquals("Test message", shortMessage);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Exception with null call has null failed call")
        void testExceptionWithNullCall() {
            BridgeRoutingException exception = new BridgeRoutingException(
                "Test message", null, null
            );

            assertNull(exception.getFailedCall());
            assertNull(exception.getFailedPattern());
        }

        @Test
        @DisplayName("Exception preserves original cause")
        void testExceptionPreservesCause() {
            OriginalException original = new OriginalException("Original");
            BridgeRoutingException exception = new BridgeRoutingException(
                "Test message", TEST_CALL, original
            );

            assertSame(original, exception.getCause());
            assertInstanceOf(OriginalException.class, exception.getCause());
        }

        @Test
        @DisplayName("Exception with null cause has null cause")
        void testExceptionWithNullCause() {
            BridgeRoutingException exception = new BridgeRoutingException(
                "Test message", TEST_CALL, null
            );

            assertNull(exception.getCause());
        }
    }

    // Helper class for testing cause preservation
    private static class OriginalException extends RuntimeException {
        public OriginalException(String message) {
            super(message);
        }
    }
}