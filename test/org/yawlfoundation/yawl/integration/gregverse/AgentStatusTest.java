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

package org.yawlfoundation.yawl.integration.gregverse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for AutonomousAgent.AgentStatus record.
 *
 * AgentStatus is a public record nested inside AutonomousAgent that represents
 * a point-in-time snapshot of an autonomous agent's health metrics.
 *
 * Tests cover:
 * - Canonical constructor validation (null/empty agentID, negative counts, out-of-range health)
 * - Accessor methods use record (method) syntax, not getters
 * - Auto-generated equals/hashCode
 * - isHealthy() predicate logic
 * - Immutability (no setter methods exposed)
 * - AgentStatus is a Java record
 */
@DisplayName("AutonomousAgent.AgentStatus record")
class AgentStatusTest {

    // ─── Constructor validation ───────────────────────────────────────────

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Null agentID throws IllegalArgumentException")
        void nullAgentIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AutonomousAgent.AgentStatus(null, 0, 0, 1.0));
        }

        @Test
        @DisplayName("Empty agentID throws IllegalArgumentException")
        void emptyAgentIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AutonomousAgent.AgentStatus("", 0, 0, 1.0));
        }

        @Test
        @DisplayName("Negative completedCases throws IllegalArgumentException")
        void negativeCompletedCasesThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AutonomousAgent.AgentStatus("agent-001", -1, 0, 1.0));
        }

        @Test
        @DisplayName("Negative stuckCases throws IllegalArgumentException")
        void negativeStuckCasesThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AutonomousAgent.AgentStatus("agent-001", 0, -1, 1.0));
        }

        @Test
        @DisplayName("healthScore above 1.0 throws IllegalArgumentException")
        void healthScoreAbove1Throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AutonomousAgent.AgentStatus("agent-001", 0, 0, 1.001));
        }

        @Test
        @DisplayName("healthScore below 0.0 throws IllegalArgumentException")
        void healthScoreBelow0Throws() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AutonomousAgent.AgentStatus("agent-001", 0, 0, -0.001));
        }

        @Test
        @DisplayName("healthScore of exactly 0.0 is valid")
        void healthScoreExactly0IsValid() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 0, 0, 0.0);
            assertEquals(0.0, status.healthScore(), 1e-9);
        }

        @Test
        @DisplayName("healthScore of exactly 1.0 is valid")
        void healthScoreExactly1IsValid() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 0, 0, 1.0);
            assertEquals(1.0, status.healthScore(), 1e-9);
        }

        @Test
        @DisplayName("Valid construction with zero cases and full health succeeds")
        void validConstructionSucceeds() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 0, 0, 1.0);
            assertNotNull(status);
        }
    }

    // ─── Accessor methods (record method syntax, not getters) ─────────────

    @Nested
    @DisplayName("Accessor methods")
    class AccessorMethods {

        @Test
        @DisplayName("agentID() returns supplied agentID")
        void agentIDReturnsSuppliedValue() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-007", 5, 1, 0.9);
            assertEquals("agent-007", status.agentID());
        }

        @Test
        @DisplayName("completedCases() returns supplied count")
        void completedCasesReturnsSuppliedCount() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 42, 0, 0.95);
            assertEquals(42, status.completedCases());
        }

        @Test
        @DisplayName("stuckCases() returns supplied count")
        void stuckCasesReturnsSuppliedCount() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 3, 0.7);
            assertEquals(3, status.stuckCases());
        }

        @Test
        @DisplayName("healthScore() returns supplied score")
        void healthScoreReturnsSuppliedScore() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 0, 0, 0.85);
            assertEquals(0.85, status.healthScore(), 1e-9);
        }

        @Test
        @DisplayName("Record exposes accessor methods with field names (no get prefix)")
        void recordExposesAccessorMethodsWithFieldNames() throws NoSuchMethodException {
            Class<AutonomousAgent.AgentStatus> cls = AutonomousAgent.AgentStatus.class;
            assertNotNull(cls.getMethod("agentID"),          "agentID() must exist");
            assertNotNull(cls.getMethod("completedCases"),   "completedCases() must exist");
            assertNotNull(cls.getMethod("stuckCases"),       "stuckCases() must exist");
            assertNotNull(cls.getMethod("healthScore"),      "healthScore() must exist");
        }

        @Test
        @DisplayName("Accessor results are consistent across multiple calls")
        void accessorResultsAreConsistent() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-X", 7, 2, 0.78);
            // Call each accessor twice to confirm immutability
            assertEquals(status.agentID(), status.agentID());
            assertEquals(status.completedCases(), status.completedCases());
            assertEquals(status.stuckCases(), status.stuckCases());
            assertEquals(status.healthScore(), status.healthScore(), 1e-9);
        }
    }

    // ─── isHealthy() predicate ────────────────────────────────────────────

    @Nested
    @DisplayName("isHealthy() predicate")
    class IsHealthyPredicate {

        @Test
        @DisplayName("isHealthy is true when zero stuck cases and healthScore above 0.8")
        void isHealthyTrueWhenZeroStuckAndHighScore() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.9);
            assertTrue(status.isHealthy());
        }

        @Test
        @DisplayName("isHealthy is false when stuckCases > 0")
        void isHealthyFalseWhenStuckCasesPositive() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 1, 0.95);
            assertFalse(status.isHealthy());
        }

        @Test
        @DisplayName("isHealthy is false when healthScore is exactly 0.8 (boundary)")
        void isHealthyFalseAtExact0dot8BoundaryHealthScore() {
            // isHealthy requires healthScore > 0.8 (strict greater-than)
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.8);
            assertFalse(status.isHealthy(),
                    "isHealthy must return false when healthScore == 0.8 (strict >)");
        }

        @Test
        @DisplayName("isHealthy is true just above 0.8 boundary")
        void isHealthyTrueJustAbove0dot8() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 0, 0, 0.801);
            assertTrue(status.isHealthy());
        }

        @Test
        @DisplayName("isHealthy is false when healthScore is 0.0")
        void isHealthyFalseWhenHealthScoreIsZero() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 0, 0, 0.0);
            assertFalse(status.isHealthy());
        }

        @Test
        @DisplayName("isHealthy is false when both stuck cases and low health score")
        void isHealthyFalseWhenBothConditionsFail() {
            AutonomousAgent.AgentStatus status =
                    new AutonomousAgent.AgentStatus("agent-001", 5, 3, 0.4);
            assertFalse(status.isHealthy());
        }
    }

    // ─── Auto-generated equals / hashCode ────────────────────────────────

    @Nested
    @DisplayName("Auto-generated equals and hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("Two identical records are equal")
        void identicalRecordsAreEqual() {
            AutonomousAgent.AgentStatus s1 =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.95);
            AutonomousAgent.AgentStatus s2 =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.95);
            assertEquals(s1, s2);
        }

        @Test
        @DisplayName("equals is reflexive")
        void equalsIsReflexive() {
            AutonomousAgent.AgentStatus s =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.95);
            assertEquals(s, s);
        }

        @Test
        @DisplayName("equals is symmetric")
        void equalsIsSymmetric() {
            AutonomousAgent.AgentStatus s1 =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.95);
            AutonomousAgent.AgentStatus s2 =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.95);
            assertEquals(s1, s2);
            assertEquals(s2, s1);
        }

        @Test
        @DisplayName("equals differs when agentID differs")
        void equalsDiffersOnAgentID() {
            AutonomousAgent.AgentStatus s1 =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.95);
            AutonomousAgent.AgentStatus s2 =
                    new AutonomousAgent.AgentStatus("agent-002", 10, 0, 0.95);
            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("equals differs when completedCases differs")
        void equalsDiffersOnCompletedCases() {
            AutonomousAgent.AgentStatus s1 =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.95);
            AutonomousAgent.AgentStatus s2 =
                    new AutonomousAgent.AgentStatus("agent-001", 11, 0, 0.95);
            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("equals differs when healthScore differs")
        void equalsDiffersOnHealthScore() {
            AutonomousAgent.AgentStatus s1 =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.9);
            AutonomousAgent.AgentStatus s2 =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.8);
            assertNotEquals(s1, s2);
        }

        @Test
        @DisplayName("hashCode consistent with equals")
        void hashCodeConsistentWithEquals() {
            AutonomousAgent.AgentStatus s1 =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.95);
            AutonomousAgent.AgentStatus s2 =
                    new AutonomousAgent.AgentStatus("agent-001", 10, 0, 0.95);
            assertEquals(s1.hashCode(), s2.hashCode());
        }

        @Test
        @DisplayName("Records that are equal produce identical toString")
        void equalRecordsProduceIdenticalToString() {
            AutonomousAgent.AgentStatus s1 =
                    new AutonomousAgent.AgentStatus("agent-001", 5, 0, 0.9);
            AutonomousAgent.AgentStatus s2 =
                    new AutonomousAgent.AgentStatus("agent-001", 5, 0, 0.9);
            assertEquals(s1.toString(), s2.toString());
        }
    }

    // ─── Immutability ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("AgentStatus is a Java record")
        void agentStatusIsAJavaRecord() {
            assertTrue(AutonomousAgent.AgentStatus.class.isRecord(),
                    "AgentStatus must be a Java record");
        }

        @Test
        @DisplayName("AgentStatus has no setter methods")
        void agentStatusHasNoSetterMethods() {
            for (java.lang.reflect.Method m :
                    AutonomousAgent.AgentStatus.class.getDeclaredMethods()) {
                assertFalse(m.getName().startsWith("set"),
                        "Records must not have setter methods, found: " + m.getName());
            }
        }

        @Test
        @DisplayName("AgentStatus record components are final fields")
        void agentStatusComponentsAreFinalFields() throws NoSuchFieldException {
            // Java records compile component declarations to private final fields
            for (java.lang.reflect.RecordComponent c :
                    AutonomousAgent.AgentStatus.class.getRecordComponents()) {
                java.lang.reflect.Field f =
                        AutonomousAgent.AgentStatus.class.getDeclaredField(c.getName());
                int mods = f.getModifiers();
                assertTrue(java.lang.reflect.Modifier.isFinal(mods),
                        "Record component field " + c.getName() + " must be final");
                assertTrue(java.lang.reflect.Modifier.isPrivate(mods),
                        "Record component field " + c.getName() + " must be private");
            }
        }
    }
}
