/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for YProblemHandler interface following Chicago TDD methodology.
 *
 * <p>Tests the handler contract for processing problem events.</p>
 *
 * @author YAWL Test Suite
 * @see YProblemHandler
 */
@DisplayName("YProblemHandler Interface Tests")
@Tag("unit")
class TestYProblemHandler {

    // ========================================================================
    // Interface Contract Tests
    // ========================================================================

    @Nested
    @DisplayName("Interface Contract Tests")
    class InterfaceContractTests {

        @Test
        @DisplayName("Interface defines handleProblem method")
        void interfaceDefinesHandleProblemMethod() throws NoSuchMethodException {
            // Verify the interface has the expected method
            assertNotNull(YProblemHandler.class.getDeclaredMethod(
                    "handleProblem", YProblemEvent.class),
                    "Interface should define handleProblem method");
        }

        @Test
        @DisplayName("HandleProblem method returns void")
        void handleProblemMethodReturnsVoid() throws NoSuchMethodException {
            // Verify the method returns void
            assertEquals(void.class,
                    YProblemHandler.class.getDeclaredMethod(
                            "handleProblem", YProblemEvent.class).getReturnType(),
                    "handleProblem should return void");
        }

        @Test
        @DisplayName("Interface is public")
        void interfaceIsPublic() {
            assertTrue(java.lang.reflect.Modifier.isPublic(YProblemHandler.class.getModifiers()),
                    "Interface should be public");
        }

        @Test
        @DisplayName("Interface is not abstract")
        void interfaceIsNotAbstract() {
            // Interfaces are implicitly abstract but this verifies the structure
            assertTrue(YProblemHandler.class.isInterface(),
                    "YProblemHandler should be an interface");
        }
    }

    // ========================================================================
    // Implementation Tests
    // ========================================================================

    @Nested
    @DisplayName("Implementation Tests")
    class ImplementationTests {

        @Test
        @DisplayName("Can create implementation that handles problems")
        void canCreateImplementationThatHandlesProblems() {
            final boolean[] handlerCalled = {false};

            YProblemHandler handler = new YProblemHandler() {
                @Override
                public void handleProblem(YProblemEvent problemEvent) {
                    handlerCalled[0] = true;
                    assertNotNull(problemEvent, "Event should not be null");
                }
            };

            YProblemEvent event = new YProblemEvent(
                    new Object(), "Test message", YProblemEvent.RuntimeError);

            handler.handleProblem(event);

            assertTrue(handlerCalled[0], "Handler should have been called");
        }

        @Test
        @DisplayName("Implementation can extract event details")
        void implementationCanExtractEventDetails() {
            final String[] capturedMessage = {null};
            final Object[] capturedSource = {null};

            YProblemHandler handler = new YProblemHandler() {
                @Override
                public void handleProblem(YProblemEvent problemEvent) {
                    capturedMessage[0] = problemEvent.getMessage();
                    capturedSource[0] = problemEvent.getSource();
                }
            };

            Object source = new Object();
            YProblemEvent event = new YProblemEvent(
                    source, "Test message content", YProblemEvent.RuntimeError);

            handler.handleProblem(event);

            assertEquals("Test message content", capturedMessage[0],
                    "Message should be extracted correctly");
            assertEquals(source, capturedSource[0],
                    "Source should be extracted correctly");
        }

        @Test
        @DisplayName("Implementation can handle different event types")
        void implementationCanHandleDifferentEventTypes() {
            final int[] capturedType = {-1};

            YProblemHandler handler = new YProblemHandler() {
                @Override
                public void handleProblem(YProblemEvent problemEvent) {
                    capturedType[0] = problemEvent.getEventType();
                }
            };

            handler.handleProblem(new YProblemEvent(
                    new Object(), "Error", YProblemEvent.RuntimeError));
            assertEquals(YProblemEvent.RuntimeError, capturedType[0],
                    "Should capture RuntimeError type");

            handler.handleProblem(new YProblemEvent(
                    new Object(), "Warning", YProblemEvent.RuntimeWarning));
            assertEquals(YProblemEvent.RuntimeWarning, capturedType[0],
                    "Should capture RuntimeWarning type");
        }
    }

    // ========================================================================
    // Lambda Implementation Tests
    // ========================================================================

    @Nested
    @DisplayName("Lambda Implementation Tests")
    class LambdaImplementationTests {

        @Test
        @DisplayName("Can use lambda as handler")
        void canUseLambdaAsHandler() {
            final boolean[] handlerCalled = {false};
            YProblemHandler handler = event -> handlerCalled[0] = true;

            handler.handleProblem(new YProblemEvent(
                    new Object(), "Test", YProblemEvent.RuntimeError));

            assertTrue(handlerCalled[0], "Lambda handler should be called");
        }

        @Test
        @DisplayName("Lambda can process event data")
        void lambdaCanProcessEventData() {
            final StringBuilder log = new StringBuilder();
            YProblemHandler handler = event ->
                    log.append(event.getMessage()).append(" from ").append(event.getSource());

            Object source = new Object();
            handler.handleProblem(new YProblemEvent(source, "TestEvent", YProblemEvent.RuntimeError));

            assertTrue(log.toString().contains("TestEvent"),
                    "Log should contain event message");
            assertTrue(log.toString().contains(source.toString()),
                    "Log should contain source");
        }
    }

    // ========================================================================
    // Multiple Handlers Tests
    // ========================================================================

    @Nested
    @DisplayName("Multiple Handlers Tests")
    class MultipleHandlersTests {

        @Test
        @DisplayName("Can use multiple handlers")
        void canUseMultipleHandlers() {
            final int[] callCount = {0};

            YProblemHandler handler1 = event -> callCount[0]++;
            YProblemHandler handler2 = event -> callCount[0] += 10;

            YProblemEvent event = new YProblemEvent(
                    new Object(), "Test", YProblemEvent.RuntimeError);

            handler1.handleProblem(event);
            handler2.handleProblem(event);

            assertEquals(11, callCount[0], "Both handlers should have been called");
        }

        @Test
        @DisplayName("Handlers can be stored in collection")
        void handlersCanBeStoredInCollection() {
            java.util.List<YProblemHandler> handlers = new java.util.ArrayList<>();

            handlers.add(event -> {});
            handlers.add(event -> {});
            handlers.add(event -> {});

            assertEquals(3, handlers.size(), "Should be able to store multiple handlers");

            YProblemEvent event = new YProblemEvent(
                    new Object(), "Test", YProblemEvent.RuntimeError);

            // Call all handlers
            for (YProblemHandler handler : handlers) {
                assertDoesNotThrow(() -> handler.handleProblem(event),
                        "Each handler should handle the event without exception");
            }
        }
    }
}
