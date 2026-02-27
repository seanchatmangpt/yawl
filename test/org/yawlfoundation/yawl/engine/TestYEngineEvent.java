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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.yawlfoundation.yawl.engine.announcement.YEngineEvent;

/**
 * Comprehensive tests for YEngineEvent enum following Chicago TDD methodology.
 *
 * <p>Tests the engine event types used for announcements to observers.</p>
 *
 * @author YAWL Test Suite
 * @see YEngineEvent
 */
@DisplayName("YEngineEvent Enum Tests")
@Tag("unit")
class TestYEngineEvent {

    // ========================================================================
    // Enum Values Tests
    // ========================================================================

    @Nested
    @DisplayName("Enum Values Tests")
    class EnumValuesTests {

        @Test
        @DisplayName("Enum has exactly 13 values")
        void enumHasExactly13Values() {
            YEngineEvent[] values = YEngineEvent.values();
            assertEquals(13, values.length, "YEngineEvent should have 13 values");
        }

        @ParameterizedTest
        @EnumSource(YEngineEvent.class)
        @DisplayName("All enum values are non-null")
        void allEnumValuesAreNonNull(YEngineEvent event) {
            assertNotNull(event, "Enum value should not be null");
        }

        @Test
        @DisplayName("Enum contains all expected event types")
        void enumContainsAllExpectedEventTypes() {
            assertNotNull(YEngineEvent.ITEM_ADD);
            assertNotNull(YEngineEvent.ITEM_STATUS);
            assertNotNull(YEngineEvent.ITEM_CANCEL);
            assertNotNull(YEngineEvent.CASE_START);
            assertNotNull(YEngineEvent.CASE_COMPLETE);
            assertNotNull(YEngineEvent.CASE_CANCELLED);
            assertNotNull(YEngineEvent.CASE_DEADLOCKED);
            assertNotNull(YEngineEvent.CASE_SUSPENDING);
            assertNotNull(YEngineEvent.CASE_SUSPENDED);
            assertNotNull(YEngineEvent.CASE_RESUMED);
            assertNotNull(YEngineEvent.TIMER_EXPIRED);
            assertNotNull(YEngineEvent.ENGINE_INIT);
            assertNotNull(YEngineEvent.NO_EVENT);
        }
    }

    // ========================================================================
    // Label Tests
    // ========================================================================

    @Nested
    @DisplayName("Label Tests")
    class LabelTests {

        @Test
        @DisplayName("ITEM_ADD label is announceItemEnabled")
        void itemAddLabelIsAnnounceItemEnabled() {
            assertEquals("announceItemEnabled", YEngineEvent.ITEM_ADD.label());
        }

        @Test
        @DisplayName("ITEM_STATUS label is announceItemStatus")
        void itemStatusLabelIsAnnounceItemStatus() {
            assertEquals("announceItemStatus", YEngineEvent.ITEM_STATUS.label());
        }

        @Test
        @DisplayName("ITEM_CANCEL label is announceItemCancelled")
        void itemCancelLabelIsAnnounceItemCancelled() {
            assertEquals("announceItemCancelled", YEngineEvent.ITEM_CANCEL.label());
        }

        @Test
        @DisplayName("CASE_START label is announceCaseStarted")
        void caseStartLabelIsAnnounceCaseStarted() {
            assertEquals("announceCaseStarted", YEngineEvent.CASE_START.label());
        }

        @Test
        @DisplayName("CASE_COMPLETE label is announceCaseCompleted")
        void caseCompleteLabelIsAnnounceCaseCompleted() {
            assertEquals("announceCaseCompleted", YEngineEvent.CASE_COMPLETE.label());
        }

        @Test
        @DisplayName("CASE_CANCELLED label is announceCaseCancelled")
        void caseCancelledLabelIsAnnounceCaseCancelled() {
            assertEquals("announceCaseCancelled", YEngineEvent.CASE_CANCELLED.label());
        }

        @Test
        @DisplayName("CASE_DEADLOCKED label is announceCaseDeadlocked")
        void caseDeadlockedLabelIsAnnounceCaseDeadlocked() {
            assertEquals("announceCaseDeadlocked", YEngineEvent.CASE_DEADLOCKED.label());
        }

        @Test
        @DisplayName("CASE_SUSPENDING label is announceCaseSuspending")
        void caseSuspendingLabelIsAnnounceCaseSuspending() {
            assertEquals("announceCaseSuspending", YEngineEvent.CASE_SUSPENDING.label());
        }

        @Test
        @DisplayName("CASE_SUSPENDED label is announceCaseSuspended")
        void caseSuspendedLabelIsAnnounceCaseSuspended() {
            assertEquals("announceCaseSuspended", YEngineEvent.CASE_SUSPENDED.label());
        }

        @Test
        @DisplayName("CASE_RESUMED label is announceCaseResumed")
        void caseResumedLabelIsAnnounceCaseResumed() {
            assertEquals("announceCaseResumed", YEngineEvent.CASE_RESUMED.label());
        }

        @Test
        @DisplayName("TIMER_EXPIRED label is announceTimerExpiry")
        void timerExpiredLabelIsAnnounceTimerExpiry() {
            assertEquals("announceTimerExpiry", YEngineEvent.TIMER_EXPIRED.label());
        }

        @Test
        @DisplayName("ENGINE_INIT label is announceEngineInitialised")
        void engineInitLabelIsAnnounceEngineInitialised() {
            assertEquals("announceEngineInitialised", YEngineEvent.ENGINE_INIT.label());
        }

        @Test
        @DisplayName("NO_EVENT label is noEvent")
        void noEventLabelIsNoEvent() {
            assertEquals("noEvent", YEngineEvent.NO_EVENT.label());
        }
    }

    // ========================================================================
    // IsBroadcast Tests
    // ========================================================================

    @Nested
    @DisplayName("IsBroadcast Tests")
    class IsBroadcastTests {

        @Test
        @DisplayName("ITEM_STATUS is broadcast")
        void itemStatusIsBroadcast() {
            assertTrue(YEngineEvent.ITEM_STATUS.isBroadcast());
        }

        @Test
        @DisplayName("CASE_START is broadcast")
        void caseStartIsBroadcast() {
            assertTrue(YEngineEvent.CASE_START.isBroadcast());
        }

        @Test
        @DisplayName("CASE_COMPLETE is broadcast")
        void caseCompleteIsBroadcast() {
            assertTrue(YEngineEvent.CASE_COMPLETE.isBroadcast());
        }

        @Test
        @DisplayName("CASE_CANCELLED is broadcast")
        void caseCancelledIsBroadcast() {
            assertTrue(YEngineEvent.CASE_CANCELLED.isBroadcast());
        }

        @Test
        @DisplayName("CASE_DEADLOCKED is broadcast")
        void caseDeadlockedIsBroadcast() {
            assertTrue(YEngineEvent.CASE_DEADLOCKED.isBroadcast());
        }

        @Test
        @DisplayName("CASE_SUSPENDING is broadcast")
        void caseSuspendingIsBroadcast() {
            assertTrue(YEngineEvent.CASE_SUSPENDING.isBroadcast());
        }

        @Test
        @DisplayName("CASE_SUSPENDED is broadcast")
        void caseSuspendedIsBroadcast() {
            assertTrue(YEngineEvent.CASE_SUSPENDED.isBroadcast());
        }

        @Test
        @DisplayName("CASE_RESUMED is broadcast")
        void caseResumedIsBroadcast() {
            assertTrue(YEngineEvent.CASE_RESUMED.isBroadcast());
        }

        @Test
        @DisplayName("ENGINE_INIT is broadcast")
        void engineInitIsBroadcast() {
            assertTrue(YEngineEvent.ENGINE_INIT.isBroadcast());
        }

        @Test
        @DisplayName("ITEM_ADD is not broadcast")
        void itemAddIsNotBroadcast() {
            assertFalse(YEngineEvent.ITEM_ADD.isBroadcast());
        }

        @Test
        @DisplayName("ITEM_CANCEL is not broadcast")
        void itemCancelIsNotBroadcast() {
            assertFalse(YEngineEvent.ITEM_CANCEL.isBroadcast());
        }

        @Test
        @DisplayName("TIMER_EXPIRED is not broadcast")
        void timerExpiredIsNotBroadcast() {
            assertFalse(YEngineEvent.TIMER_EXPIRED.isBroadcast());
        }

        @Test
        @DisplayName("NO_EVENT is not broadcast")
        void noEventIsNotBroadcast() {
            assertFalse(YEngineEvent.NO_EVENT.isBroadcast());
        }
    }

    // ========================================================================
    // FromString Tests
    // ========================================================================

    @Nested
    @DisplayName("FromString Tests")
    class FromStringTests {

        @Test
        @DisplayName("FromString returns correct event for valid labels")
        void fromStringReturnsCorrectEventForValidLabels() {
            assertEquals(YEngineEvent.ITEM_ADD, YEngineEvent.fromString("announceItemEnabled"));
            assertEquals(YEngineEvent.ITEM_STATUS, YEngineEvent.fromString("announceItemStatus"));
            assertEquals(YEngineEvent.ITEM_CANCEL, YEngineEvent.fromString("announceItemCancelled"));
            assertEquals(YEngineEvent.CASE_START, YEngineEvent.fromString("announceCaseStarted"));
            assertEquals(YEngineEvent.CASE_COMPLETE, YEngineEvent.fromString("announceCaseCompleted"));
            assertEquals(YEngineEvent.CASE_CANCELLED, YEngineEvent.fromString("announceCaseCancelled"));
            assertEquals(YEngineEvent.CASE_DEADLOCKED, YEngineEvent.fromString("announceCaseDeadlocked"));
            assertEquals(YEngineEvent.CASE_SUSPENDING, YEngineEvent.fromString("announceCaseSuspending"));
            assertEquals(YEngineEvent.CASE_SUSPENDED, YEngineEvent.fromString("announceCaseSuspended"));
            assertEquals(YEngineEvent.CASE_RESUMED, YEngineEvent.fromString("announceCaseResumed"));
            assertEquals(YEngineEvent.TIMER_EXPIRED, YEngineEvent.fromString("announceTimerExpiry"));
            assertEquals(YEngineEvent.ENGINE_INIT, YEngineEvent.fromString("announceEngineInitialised"));
            assertEquals(YEngineEvent.NO_EVENT, YEngineEvent.fromString("noEvent"));
        }

        @Test
        @DisplayName("FromString returns NO_EVENT for unknown label")
        void fromStringReturnsNoEventForUnknownLabel() {
            assertEquals(YEngineEvent.NO_EVENT, YEngineEvent.fromString("unknownLabel"));
        }

        @Test
        @DisplayName("FromString returns NO_EVENT for null")
        void fromStringReturnsNoEventForNull() {
            assertEquals(YEngineEvent.NO_EVENT, YEngineEvent.fromString(null));
        }

        @Test
        @DisplayName("FromString is case sensitive")
        void fromStringIsCaseSensitive() {
            assertEquals(YEngineEvent.NO_EVENT, YEngineEvent.fromString("ANNOUNCEITEMENABLED"));
            assertEquals(YEngineEvent.NO_EVENT, YEngineEvent.fromString("announceitemenabled"));
        }
    }

    // ========================================================================
    // Roundtrip Tests
    // ========================================================================

    @Nested
    @DisplayName("Roundtrip Tests")
    class RoundtripTests {

        @ParameterizedTest
        @EnumSource(YEngineEvent.class)
        @DisplayName("Label then fromString roundtrip")
        void labelThenFromStringRoundtrip(YEngineEvent event) {
            String label = event.label();
            YEngineEvent result = YEngineEvent.fromString(label);
            assertEquals(event, result,
                    "Roundtrip should return original event for " + event.name());
        }
    }

    // ========================================================================
    // Switch Usage Tests
    // ========================================================================

    @Nested
    @DisplayName("Switch Usage Tests")
    class SwitchUsageTests {

        @Test
        @DisplayName("Can use in enhanced switch statement")
        void canUseInEnhancedSwitchStatement() {
            for (YEngineEvent event : YEngineEvent.values()) {
                String category = switch (event) {
                    case ITEM_ADD, ITEM_STATUS, ITEM_CANCEL -> "workitem";
                    case CASE_START, CASE_COMPLETE, CASE_CANCELLED, CASE_DEADLOCKED -> "case";
                    case CASE_SUSPENDING, CASE_SUSPENDED, CASE_RESUMED -> "suspension";
                    case TIMER_EXPIRED -> "timer";
                    case ENGINE_INIT -> "engine";
                    case NO_EVENT -> "none";
                };
                assertNotNull(category, "Switch should return non-null for " + event);
            }
        }

        @Test
        @DisplayName("Switch covers all cases exhaustively")
        void switchCoversAllCasesExhaustively() {
            int count = 0;
            for (YEngineEvent event : YEngineEvent.values()) {
                switch (event) {
                    case ITEM_ADD -> count++;
                    case ITEM_STATUS -> count++;
                    case ITEM_CANCEL -> count++;
                    case CASE_START -> count++;
                    case CASE_COMPLETE -> count++;
                    case CASE_CANCELLED -> count++;
                    case CASE_DEADLOCKED -> count++;
                    case CASE_SUSPENDING -> count++;
                    case CASE_SUSPENDED -> count++;
                    case CASE_RESUMED -> count++;
                    case TIMER_EXPIRED -> count++;
                    case ENGINE_INIT -> count++;
                    case NO_EVENT -> count++;
                }
            }
            assertEquals(13, count, "Switch should cover all 13 events");
        }
    }
}
