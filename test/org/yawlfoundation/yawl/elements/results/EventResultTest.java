/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.elements.results;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for the EventResult sealed class hierarchy.
 *
 * Tests cover:
 * - Sealed class structure (permitted subclasses, finality)
 * - Common field validation (eventID, eventType, processedAt)
 * - EventAccepted: correlationID, statusCode, detail, equals/hashCode
 * - Type predicate methods (isAccepted, isRejected, isInvalid)
 * - Exhaustive switch pattern matching
 * - instanceof type-pattern narrowing
 */
@DisplayName("EventResult sealed hierarchy")
class EventResultTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final String EVENT_ID = "evt-001";
    private static final String EVENT_TYPE = "LAUNCH_CASE";

    // ─── Sealed class meta tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Sealed class structure")
    class SealedClassStructure {

        @Test
        @DisplayName("EventResult permits exactly three subclasses")
        void eventResultPermitsExactlyThreeSubclasses() {
            Class<?>[] permitted = EventResult.class.getPermittedSubclasses();
            assertNotNull(permitted);
            assertEquals(3, permitted.length,
                    "EventResult must permit exactly 3 subclasses");
        }

        @Test
        @DisplayName("EventAccepted is a permitted subclass")
        void eventAcceptedIsPermitted() {
            assertTrue(permittedSubclassNames(EventResult.class)
                    .contains(EventAccepted.class.getName()));
        }

        @Test
        @DisplayName("All permitted subclasses are final")
        void allPermittedSubclassesAreFinal() {
            for (Class<?> sub : EventResult.class.getPermittedSubclasses()) {
                assertTrue(java.lang.reflect.Modifier.isFinal(sub.getModifiers()),
                        sub.getSimpleName() + " must be final");
            }
        }

        private Set<String> permittedSubclassNames(Class<?> sealedClass) {
            Set<String> names = new HashSet<>();
            for (Class<?> c : sealedClass.getPermittedSubclasses()) {
                names.add(c.getName());
            }
            return names;
        }
    }

    // ─── Common field validation ──────────────────────────────────────────

    @Nested
    @DisplayName("Common field validation")
    class CommonFieldValidation {

        @Test
        @DisplayName("Null eventID throws IllegalArgumentException")
        void nullEventIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new EventAccepted(null, EVENT_TYPE, FIXED_NOW, "corr-001"));
        }

        @Test
        @DisplayName("Blank eventID throws IllegalArgumentException")
        void blankEventIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new EventAccepted("  ", EVENT_TYPE, FIXED_NOW, "corr-001"));
        }

        @Test
        @DisplayName("Null eventType throws IllegalArgumentException")
        void nullEventTypeThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new EventAccepted(EVENT_ID, null, FIXED_NOW, "corr-001"));
        }

        @Test
        @DisplayName("Blank eventType throws IllegalArgumentException")
        void blankEventTypeThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new EventAccepted(EVENT_ID, "  ", FIXED_NOW, "corr-001"));
        }

        @Test
        @DisplayName("Null processedAt throws NullPointerException")
        void nullProcessedAtThrows() {
            assertThrows(NullPointerException.class,
                    () -> new EventAccepted(EVENT_ID, EVENT_TYPE, null, "corr-001"));
        }

        @Test
        @DisplayName("eventID accessor returns supplied value")
        void eventIDAccessorReturnsSuppliedValue() {
            EventAccepted result = new EventAccepted("evt-99", EVENT_TYPE, FIXED_NOW, "corr");
            assertEquals("evt-99", result.eventID());
        }

        @Test
        @DisplayName("eventType accessor returns supplied value")
        void eventTypeAccessorReturnsSuppliedValue() {
            EventAccepted result = new EventAccepted(EVENT_ID, "COMPLETE_ITEM", FIXED_NOW, "corr");
            assertEquals("COMPLETE_ITEM", result.eventType());
        }

        @Test
        @DisplayName("processedAt accessor returns supplied Instant")
        void processedAtAccessorReturnsSuppliedInstant() {
            Instant ts = Instant.parse("2026-06-15T08:00:00Z");
            EventAccepted result = new EventAccepted(EVENT_ID, EVENT_TYPE, ts, "corr");
            assertEquals(ts, result.processedAt());
        }
    }

    // ─── EventAccepted ────────────────────────────────────────────────────

    @Nested
    @DisplayName("EventAccepted")
    class EventAcceptedTests {

        @Test
        @DisplayName("statusCode returns ACCEPTED")
        void statusCodeIsAccepted() {
            EventAccepted result = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-001");
            assertEquals("ACCEPTED", result.statusCode());
        }

        @Test
        @DisplayName("correlationID accessor returns supplied value")
        void correlationIDAccessorReturnsSuppliedValue() {
            EventAccepted result = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-007");
            assertEquals("case-007", result.correlationID());
        }

        @Test
        @DisplayName("Null correlationID throws IllegalArgumentException")
        void nullCorrelationIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, null));
        }

        @Test
        @DisplayName("Blank correlationID throws IllegalArgumentException")
        void blankCorrelationIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "  "));
        }

        @Test
        @DisplayName("detail contains eventID and correlationID")
        void detailContainsEventIDAndCorrelationID() {
            EventAccepted result = new EventAccepted("evt-42", EVENT_TYPE, FIXED_NOW, "case-99");
            String detail = result.detail();
            assertTrue(detail.contains("evt-42"), "detail must contain eventID");
            assertTrue(detail.contains("case-99"), "detail must contain correlationID");
        }

        @Test
        @DisplayName("isAccepted returns true")
        void isAcceptedReturnsTrue() {
            EventResult result = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-001");
            assertTrue(result.isAccepted());
        }

        @Test
        @DisplayName("isRejected returns false")
        void isRejectedReturnsFalse() {
            EventResult result = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-001");
            assertFalse(result.isRejected());
        }

        @Test
        @DisplayName("isInvalid returns false")
        void isInvalidReturnsFalse() {
            EventResult result = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-001");
            assertFalse(result.isInvalid());
        }

        @Test
        @DisplayName("equals holds for identical instances")
        void equalsHoldsForIdenticalInstances() {
            EventAccepted r1 = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-001");
            EventAccepted r2 = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-001");
            assertEquals(r1, r2);
        }

        @Test
        @DisplayName("equals is reflexive")
        void equalsIsReflexive() {
            EventAccepted r = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-001");
            assertEquals(r, r);
        }

        @Test
        @DisplayName("equals differs when correlationID differs")
        void equalsDiffersOnCorrelationID() {
            EventAccepted r1 = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-001");
            EventAccepted r2 = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-002");
            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("equals differs when eventID differs")
        void equalsDiffersOnEventID() {
            EventAccepted r1 = new EventAccepted("evt-001", EVENT_TYPE, FIXED_NOW, "case-001");
            EventAccepted r2 = new EventAccepted("evt-002", EVENT_TYPE, FIXED_NOW, "case-001");
            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("hashCode consistent with equals")
        void hashCodeConsistentWithEquals() {
            EventAccepted r1 = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-001");
            EventAccepted r2 = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-001");
            assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("toString includes ACCEPTED status and eventID")
        void toStringContainsKeyFields() {
            EventAccepted r = new EventAccepted("evt-999", EVENT_TYPE, FIXED_NOW, "case-001");
            String str = r.toString();
            assertTrue(str.contains("evt-999"));
            assertTrue(str.contains("ACCEPTED"));
        }
    }

    // ─── instanceof pattern narrowing ────────────────────────────────────

    @Nested
    @DisplayName("instanceof pattern narrowing for EventResult")
    class InstanceofPatternNarrowing {

        @Test
        @DisplayName("instanceof EventAccepted with binding gives correlationID access")
        void instanceofEventAcceptedProvidesCorrelationID() {
            EventResult result = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "case-007");
            if (result instanceof EventAccepted a) {
                assertEquals("case-007", a.correlationID());
            } else {
                fail("Pattern match should have bound to EventAccepted");
            }
        }

        @Test
        @DisplayName("EventAccepted matches EventResult base type")
        void eventAcceptedMatchesEventResultBaseType() {
            EventResult result = new EventAccepted(EVENT_ID, EVENT_TYPE, FIXED_NOW, "corr");
            assertTrue(result instanceof EventResult);
        }
    }

    // ─── Exhaustive switch pattern matching ───────────────────────────────

    @Nested
    @DisplayName("Exhaustive switch pattern matching on EventResult")
    class ExhaustiveSwitchPatternMatching {

        @Test
        @DisplayName("switch on EventAccepted selects correct branch and extracts correlationID")
        void switchSelectsAcceptedBranchAndExtractsCorrelationID() {
            EventResult result = new EventAccepted("evt-1", "LAUNCH_CASE", FIXED_NOW, "case-42");

            // Exhaustive switch over all permitted subclasses
            String response = switch (result) {
                case EventAccepted  a -> "OK:" + a.correlationID();
                case EventRejected  r -> "REJECT:" + r.rejectionReason();
                case EventInvalid   i -> "INVALID:" + i.validationErrors().get(0);
            };

            assertEquals("OK:case-42", response);
        }

        @Test
        @DisplayName("switch extracts common eventID from all branches")
        void switchExtractsEventIDFromAllBranches() {
            EventResult accepted = new EventAccepted("evt-001", "LAUNCH_CASE", FIXED_NOW, "c1");
            EventResult rejected = new EventRejected("evt-002", "COMPLETE_ITEM", FIXED_NOW,
                    "case not in executable state", false);
            EventResult invalid  = new EventInvalid("evt-003", "CANCEL_CASE", FIXED_NOW,
                    List.of("unknown case ID"));

            for (EventResult result : new EventResult[]{accepted, rejected, invalid}) {
                String id = switch (result) {
                    case EventAccepted  a -> a.eventID();
                    case EventRejected  r -> r.eventID();
                    case EventInvalid   i -> i.eventID();
                };
                assertNotNull(id);
                assertTrue(id.startsWith("evt-"));
            }
        }
    }
}
