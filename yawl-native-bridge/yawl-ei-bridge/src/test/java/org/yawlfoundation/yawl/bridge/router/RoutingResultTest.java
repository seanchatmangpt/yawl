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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RoutingResult class.
 */
class RoutingResultTest {

    private static final NativeCall TEST_CALL = NativeCall.of(
        "http://example.org/subject",
        "http://example.org/predicate",
        "http://example.org/object",
        CallPattern.JVM
    );

    @Nested
    @DisplayName("Success Cases")
    class SuccessCases {

        @Test
        @DisplayName("Create successful result with object")
        void testSuccessWithObject() {
            Object result = Map.of("key", "value");
            RoutingResult routingResult = RoutingResult.success(result, TEST_CALL);

            assertTrue(routingResult.isSuccess());
            assertFalse(routingResult.isFailure());
            assertEquals(result, routingResult.getResult());
            assertNull(routingResult.getError());
            assertEquals(TEST_CALL, routingResult.getCall());
            assertNotNull(routingResult.getTimestamp());
            assertTrue(routingResult.getExecutionTime().toMillis() > 0);
            assertNull(routingResult.getErrorMessage());
        }

        @Test
        @DisplayName("Create successful result with null object")
        void testSuccessWithNullObject() {
            RoutingResult routingResult = RoutingResult.success(null, TEST_CALL);

            assertTrue(routingResult.isSuccess());
            assertNull(routingResult.getResult());
        }

        @Test
        @DisplayName("String representation of success is informative")
        void testSuccessToString() {
            Object result = "test result";
            RoutingResult routingResult = RoutingResult.success(result, TEST_CALL);

            String str = routingResult.toString();
            assertTrue(str.contains("RoutingResult"));
            assertTrue(str.contains("SUCCESS"));
            assertTrue(str.contains(result.toString()));
        }
    }

    @Nested
    @DisplayName("Failure Cases")
    class FailureCases {

        @Test
        @DisplayName("Create failure with error message")
        void testFailureWithMessage() {
            String errorMessage = "Test error message";
            RoutingResult routingResult = RoutingResult.failure(errorMessage, TEST_CALL);

            assertFalse(routingResult.isSuccess());
            assertTrue(routingResult.isFailure());
            assertNull(routingResult.getResult());
            assertNotNull(routingResult.getError());
            assertEquals(errorMessage, routingResult.getErrorMessage());
            assertEquals(TEST_CALL, routingResult.getCall());
        }

        @Test
        @DisplayName("Create failure with exception")
        void testFailureWithException() {
            Exception cause = new RuntimeException("Root cause");
            RoutingResult routingResult = RoutingResult.failure("Error", TEST_CALL, cause);

            assertFalse(routingResult.isSuccess());
            assertSame(cause, routingResult.getError());
            assertEquals("Error", routingResult.getErrorMessage());
        }

        @Test
        @DisplayName("String representation of failure is informative")
        void testFailureToString() {
            RoutingResult routingResult = RoutingResult.failure("Error", TEST_CALL);

            String str = routingResult.toString();
            assertTrue(str.contains("RoutingResult"));
            assertTrue(str.contains("FAILURE"));
            assertTrue(str.contains("Error"));
        }
    }

    @Nested
    @DisplayName("Mapping Operations")
    class MappingOperations {

        @Test
        @DisplayName("Map successful result")
        void testMapSuccess() {
            Object original = "original";
            RoutingResult originalResult = RoutingResult.success(original, TEST_CALL);

            RoutingResult mapped = originalResult.map(String::toUpperCase);

            assertTrue(mapped.isSuccess());
            assertEquals("ORIGINAL", mapped.getResult());
        }

        @Test
        @DisplayName("Map failed result returns original")
        void testMapFailure() {
            RoutingResult originalResult = RoutingResult.failure("Error", TEST_CALL);

            RoutingResult mapped = originalResult.map(String::toUpperCase);

            assertSame(originalResult, mapped); // Should return original on failure
        }

        @Test
        @DisplayName("Map throws exception")
        void testMapThrowsException() {
            Object original = "value";
            RoutingResult originalResult = RoutingResult.success(original, TEST_CALL);

            RoutingResult mapped = originalResult.map(v -> {
                throw new RuntimeException("Mapping failed");
            });

            assertFalse(mapped.isSuccess());
            assertTrue(mapped.getErrorMessage().contains("Mapping failed"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Null result throws NPE in success")
        void testNullResultInSuccess() {
            assertThrows(NullPointerException.class, () ->
                RoutingResult.success(null, TEST_CALL)
            );
        }

        @Test
        @DisplayName("Null call throws NPE in success")
        void testNullCallInSuccess() {
            assertThrows(NullPointerException.class, () ->
                RoutingResult.success("result", null)
            );
        }

        @Test
        @DisplayName("Null call throws NPE in failure")
        void testNullCallInFailure() {
            assertThrows(NullPointerException.class, () ->
                RoutingResult.failure("error", null)
            );
        }

        @Test
        @DisplayName("Null message throws NPE in failure")
        void testNullMessageInFailure() {
            assertThrows(NullPointerException.class, () ->
                RoutingResult.failure(null, TEST_CALL)
            );
        }
    }
}