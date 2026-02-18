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

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.yawlfoundation.yawl.exceptions.YPersistenceException;

/**
 * Comprehensive tests for YProblemEvent following Chicago TDD methodology.
 *
 * <p>Tests problem event creation, validation, and logging behavior.</p>
 *
 * @author YAWL Test Suite
 * @see YProblemEvent
 */
@DisplayName("YProblemEvent Tests")
@Tag("integration")
class TestYProblemEvent {

    private Object testSource;
    private String testMessage;

    @BeforeEach
    void setUp() {
        testSource = new Object();
        testMessage = "Test problem message";
    }

    // ========================================================================
    // Event Creation Tests
    // ========================================================================

    @Nested
    @DisplayName("Event Creation Tests")
    class EventCreationTests {

        @Test
        @DisplayName("Can create RuntimeError event")
        void canCreateRuntimeErrorEvent() {
            YProblemEvent event = new YProblemEvent(testSource, testMessage, YProblemEvent.RuntimeError);
            assertNotNull(event, "Event should be created");
        }

        @Test
        @DisplayName("Can create RuntimeWarning event")
        void canCreateRuntimeWarningEvent() {
            YProblemEvent event = new YProblemEvent(testSource, testMessage, YProblemEvent.RuntimeWarning);
            assertNotNull(event, "Event should be created");
        }

        @Test
        @DisplayName("Event with null source throws IllegalArgumentException")
        void eventWithNullSourceThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> {
                new YProblemEvent(null, testMessage, YProblemEvent.RuntimeError);
            }, "Should throw IllegalArgumentException for null source");
        }

        @Test
        @DisplayName("Event with invalid type throws IllegalArgumentException")
        void eventWithInvalidTypeThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class, () -> {
                new YProblemEvent(testSource, testMessage, 999);
            }, "Should throw IllegalArgumentException for invalid event type");
        }

        @Test
        @DisplayName("Event with null message is allowed")
        void eventWithNullMessageIsAllowed() {
            // Null message should be allowed
            assertDoesNotThrow(() -> {
                new YProblemEvent(testSource, null, YProblemEvent.RuntimeError);
            }, "Null message should be allowed");
        }
    }

    // ========================================================================
    // Getters Tests
    // ========================================================================

    @Nested
    @DisplayName("Getters Tests")
    class GettersTests {

        @Test
        @DisplayName("GetMessage returns the message")
        void getMessageReturnsTheMessage() {
            YProblemEvent event = new YProblemEvent(testSource, testMessage, YProblemEvent.RuntimeError);
            assertEquals(testMessage, event.getMessage(), "getMessage should return the message");
        }

        @Test
        @DisplayName("GetSource returns the source")
        void getSourceReturnsTheSource() {
            YProblemEvent event = new YProblemEvent(testSource, testMessage, YProblemEvent.RuntimeError);
            assertEquals(testSource, event.getSource(), "getSource should return the source");
        }

        @Test
        @DisplayName("GetEventType returns the event type")
        void getEventTypeReturnsTheEventType() {
            YProblemEvent event = new YProblemEvent(testSource, testMessage, YProblemEvent.RuntimeError);
            assertEquals(YProblemEvent.RuntimeError, event.getEventType(),
                    "getEventType should return RuntimeError");
        }

        @Test
        @DisplayName("GetEventType returns RuntimeWarning for warning events")
        void getEventTypeReturnsRuntimeWarningForWarningEvents() {
            YProblemEvent event = new YProblemEvent(testSource, testMessage, YProblemEvent.RuntimeWarning);
            assertEquals(YProblemEvent.RuntimeWarning, event.getEventType(),
                    "getEventType should return RuntimeWarning");
        }
    }

    // ========================================================================
    // Event Type Constants Tests
    // ========================================================================

    @Nested
    @DisplayName("Event Type Constants Tests")
    class EventTypeConstantsTests {

        @Test
        @DisplayName("RuntimeError constant has expected value")
        void runtimeErrorConstantHasExpectedValue() {
            assertEquals(1, YProblemEvent.RuntimeError, "RuntimeError should equal 1");
        }

        @Test
        @DisplayName("RuntimeWarning constant has expected value")
        void runtimeWarningConstantHasExpectedValue() {
            assertEquals(2, YProblemEvent.RuntimeWarning, "RuntimeWarning should equal 2");
        }
    }

    // ========================================================================
    // Log Problem Tests
    // ========================================================================

    @Nested
    @DisplayName("Log Problem Tests")
    class LogProblemTests {

        @Test
        @DisplayName("LogProblem with null persistence manager does not throw")
        void logProblemWithNullPersistenceManagerDoesNotThrow() {
            YProblemEvent event = new YProblemEvent(testSource, testMessage, YProblemEvent.RuntimeError);
            assertDoesNotThrow(() -> {
                event.logProblem(null);
            }, "logProblem with null pmgr should not throw");
        }
    }

    // ========================================================================
    // Source Type Tests
    // ========================================================================

    @Nested
    @DisplayName("Source Type Tests")
    class SourceTypeTests {

        @Test
        @DisplayName("Can use String as source")
        void canUseStringAsSource() {
            String source = "TestSource";
            YProblemEvent event = new YProblemEvent(source, testMessage, YProblemEvent.RuntimeError);
            assertEquals(source, event.getSource(), "String source should be preserved");
        }

        @Test
        @DisplayName("Can use Class as source")
        void canUseClassAsSource() {
            Class<?> source = YProblemEvent.class;
            YProblemEvent event = new YProblemEvent(source, testMessage, YProblemEvent.RuntimeError);
            assertEquals(source, event.getSource(), "Class source should be preserved");
        }

        @Test
        @DisplayName("Can use custom object as source")
        void canUseCustomObjectAsSource() {
            Object customSource = new Object() {
                @Override
                public String toString() {
                    return "CustomSource";
                }
            };
            YProblemEvent event = new YProblemEvent(customSource, testMessage, YProblemEvent.RuntimeError);
            assertEquals(customSource, event.getSource(), "Custom object source should be preserved");
        }
    }

    // ========================================================================
    // Message Content Tests
    // ========================================================================

    @Nested
    @DisplayName("Message Content Tests")
    class MessageContentTests {

        @Test
        @DisplayName("Can use empty message")
        void canUseEmptyMessage() {
            YProblemEvent event = new YProblemEvent(testSource, "", YProblemEvent.RuntimeError);
            assertEquals("", event.getMessage(), "Empty message should be preserved");
        }

        @Test
        @DisplayName("Can use long message")
        void canUseLongMessage() {
            String longMessage = "a".repeat(10000);
            YProblemEvent event = new YProblemEvent(testSource, longMessage, YProblemEvent.RuntimeError);
            assertEquals(longMessage, event.getMessage(), "Long message should be preserved");
        }

        @Test
        @DisplayName("Can use message with special characters")
        void canUseMessageWithSpecialCharacters() {
            String specialMessage = "Error: \n\t\"Special\" <chars> & more";
            YProblemEvent event = new YProblemEvent(testSource, specialMessage, YProblemEvent.RuntimeError);
            assertEquals(specialMessage, event.getMessage(), "Special characters should be preserved");
        }
    }
}
