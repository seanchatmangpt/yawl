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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for the CaseOutcome sealed class hierarchy.
 *
 * Tests cover:
 * - Sealed class structure (permitted subclasses, finality)
 * - Common field validation (caseID, specificationID, durationMs, terminatedAt)
 * - CaseCompleted: outputData, statusCode, summary, equals/hashCode
 * - CaseFailed: failureReason, failedAtTaskID, cause, statusCode, summary
 * - CaseCancelled: cancelledBy, cancellationNote, statusCode, summary
 * - Type predicate methods (isCompleted, isFailed, isCancelled)
 * - Exhaustive switch pattern matching
 * - instanceof type-pattern narrowing
 */
@DisplayName("CaseOutcome sealed hierarchy")
class CaseOutcomeTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final String CASE_ID = "case-001";
    private static final String SPEC_ID = "spec-v1.0";

    // ─── Sealed class meta tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Sealed class structure")
    class SealedClassStructure {

        @Test
        @DisplayName("CaseOutcome permits exactly three subclasses")
        void caseOutcomePermitsExactlyThreeSubclasses() {
            Class<?>[] permitted = CaseOutcome.class.getPermittedSubclasses();
            assertNotNull(permitted);
            assertEquals(3, permitted.length,
                    "CaseOutcome must permit exactly 3 subclasses");
        }

        @Test
        @DisplayName("CaseCompleted is a permitted subclass")
        void caseCompletedIsPermitted() {
            assertTrue(permittedSubclassNames(CaseOutcome.class)
                    .contains(CaseCompleted.class.getName()));
        }

        @Test
        @DisplayName("CaseFailed is a permitted subclass")
        void caseFailedIsPermitted() {
            assertTrue(permittedSubclassNames(CaseOutcome.class)
                    .contains(CaseFailed.class.getName()));
        }

        @Test
        @DisplayName("CaseCancelled is a permitted subclass")
        void caseCancelledIsPermitted() {
            assertTrue(permittedSubclassNames(CaseOutcome.class)
                    .contains(CaseCancelled.class.getName()));
        }

        @Test
        @DisplayName("All permitted subclasses are final")
        void allPermittedSubclassesAreFinal() {
            for (Class<?> sub : CaseOutcome.class.getPermittedSubclasses()) {
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
        @DisplayName("Null caseID throws IllegalArgumentException")
        void nullCaseIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseCompleted(null, SPEC_ID, 100L, FIXED_NOW, ""));
        }

        @Test
        @DisplayName("Blank caseID throws IllegalArgumentException")
        void blankCaseIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseCompleted("  ", SPEC_ID, 100L, FIXED_NOW, ""));
        }

        @Test
        @DisplayName("Null specificationID throws IllegalArgumentException")
        void nullSpecificationIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseCompleted(CASE_ID, null, 100L, FIXED_NOW, ""));
        }

        @Test
        @DisplayName("Blank specificationID throws IllegalArgumentException")
        void blankSpecificationIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseCompleted(CASE_ID, "  ", 100L, FIXED_NOW, ""));
        }

        @Test
        @DisplayName("Negative durationMs throws IllegalArgumentException")
        void negativeDurationMsThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseCompleted(CASE_ID, SPEC_ID, -1L, FIXED_NOW, ""));
        }

        @Test
        @DisplayName("Zero durationMs is valid")
        void zeroDurationIsValid() {
            CaseCompleted outcome = new CaseCompleted(CASE_ID, SPEC_ID, 0L, FIXED_NOW, "");
            assertEquals(0L, outcome.durationMs());
        }

        @Test
        @DisplayName("Null terminatedAt throws NullPointerException")
        void nullTerminatedAtThrows() {
            assertThrows(NullPointerException.class,
                    () -> new CaseCompleted(CASE_ID, SPEC_ID, 100L, null, ""));
        }

        @Test
        @DisplayName("caseID accessor returns supplied value")
        void caseIDAccessorReturnsSuppliedValue() {
            CaseCompleted outcome = new CaseCompleted("case-42", SPEC_ID, 100L, FIXED_NOW, "");
            assertEquals("case-42", outcome.caseID());
        }

        @Test
        @DisplayName("specificationID accessor returns supplied value")
        void specificationIDAccessorReturnsSuppliedValue() {
            CaseCompleted outcome = new CaseCompleted(CASE_ID, "spec-v2.3", 100L, FIXED_NOW, "");
            assertEquals("spec-v2.3", outcome.specificationID());
        }

        @Test
        @DisplayName("durationMs accessor returns supplied value")
        void durationMsAccessorReturnsSuppliedValue() {
            CaseCompleted outcome = new CaseCompleted(CASE_ID, SPEC_ID, 12345L, FIXED_NOW, "");
            assertEquals(12345L, outcome.durationMs());
        }

        @Test
        @DisplayName("terminatedAt accessor returns supplied Instant")
        void terminatedAtAccessorReturnsSuppliedInstant() {
            Instant ts = Instant.parse("2026-03-01T09:30:00Z");
            CaseCompleted outcome = new CaseCompleted(CASE_ID, SPEC_ID, 100L, ts, "");
            assertEquals(ts, outcome.terminatedAt());
        }
    }

    // ─── CaseCompleted ────────────────────────────────────────────────────

    @Nested
    @DisplayName("CaseCompleted")
    class CaseCompletedTests {

        @Test
        @DisplayName("statusCode returns COMPLETED")
        void statusCodeIsCompleted() {
            CaseCompleted outcome = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "");
            assertEquals("COMPLETED", outcome.statusCode());
        }

        @Test
        @DisplayName("outputData accessor returns supplied data")
        void outputDataAccessorReturnsSuppliedData() {
            CaseCompleted outcome = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW,
                    "<output/>");
            assertEquals("<output/>", outcome.outputData());
        }

        @Test
        @DisplayName("outputData allows empty string")
        void outputDataAllowsEmptyString() {
            CaseCompleted outcome = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "");
            assertEquals("", outcome.outputData());
        }

        @Test
        @DisplayName("Null outputData throws NullPointerException")
        void nullOutputDataThrows() {
            assertThrows(NullPointerException.class,
                    () -> new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, null));
        }

        @Test
        @DisplayName("summary contains caseID")
        void summaryContainsCaseID() {
            CaseCompleted outcome = new CaseCompleted("case-99", SPEC_ID, 200L, FIXED_NOW, "");
            assertTrue(outcome.summary().contains("case-99"));
        }

        @Test
        @DisplayName("isCompleted returns true")
        void isCompletedReturnsTrue() {
            CaseOutcome outcome = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "");
            assertTrue(outcome.isCompleted());
        }

        @Test
        @DisplayName("isFailed returns false")
        void isFailedReturnsFalse() {
            CaseOutcome outcome = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "");
            assertFalse(outcome.isFailed());
        }

        @Test
        @DisplayName("isCancelled returns false")
        void isCancelledReturnsFalse() {
            CaseOutcome outcome = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "");
            assertFalse(outcome.isCancelled());
        }

        @Test
        @DisplayName("equals holds for identical instances")
        void equalsHoldsForIdenticalInstances() {
            CaseCompleted o1 = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "<data/>");
            CaseCompleted o2 = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "<data/>");
            assertEquals(o1, o2);
        }

        @Test
        @DisplayName("equals differs when outputData differs")
        void equalsDiffersOnOutputData() {
            CaseCompleted o1 = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "a");
            CaseCompleted o2 = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "b");
            assertNotEquals(o1, o2);
        }

        @Test
        @DisplayName("hashCode consistent with equals")
        void hashCodeConsistentWithEquals() {
            CaseCompleted o1 = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "out");
            CaseCompleted o2 = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "out");
            assertEquals(o1.hashCode(), o2.hashCode());
        }

        @Test
        @DisplayName("toString includes COMPLETED status and caseID")
        void toStringContainsKeyFields() {
            CaseCompleted o = new CaseCompleted("case-001", SPEC_ID, 500L, FIXED_NOW, "out");
            String str = o.toString();
            assertTrue(str.contains("case-001"));
            assertTrue(str.contains("CaseCompleted"));
        }
    }

    // ─── CaseFailed ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("CaseFailed")
    class CaseFailedTests {

        @Test
        @DisplayName("statusCode returns FAILED")
        void statusCodeIsFailed() {
            CaseFailed outcome = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW,
                    "OR-join deadlock", "task-join");
            assertEquals("FAILED", outcome.statusCode());
        }

        @Test
        @DisplayName("failureReason accessor returns supplied reason")
        void failureReasonAccessorReturnsSuppliedValue() {
            CaseFailed outcome = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW,
                    "data binding error", "task-parse");
            assertEquals("data binding error", outcome.failureReason());
        }

        @Test
        @DisplayName("failedAtTaskID accessor returns supplied task ID")
        void failedAtTaskIDAccessorReturnsSuppliedValue() {
            CaseFailed outcome = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW,
                    "reason", "task-007");
            assertEquals("task-007", outcome.failedAtTaskID());
        }

        @Test
        @DisplayName("Null failureReason throws IllegalArgumentException")
        void nullFailureReasonThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW, null, "task"));
        }

        @Test
        @DisplayName("Blank failureReason throws IllegalArgumentException")
        void blankFailureReasonThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW, "  ", "task"));
        }

        @Test
        @DisplayName("Null failedAtTaskID throws IllegalArgumentException")
        void nullFailedAtTaskIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW, "reason", null));
        }

        @Test
        @DisplayName("Blank failedAtTaskID throws IllegalArgumentException")
        void blankFailedAtTaskIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW, "reason", "  "));
        }

        @Test
        @DisplayName("cause is empty when constructed with reason+taskID only")
        void causeIsEmptyForSemanticFailure() {
            CaseFailed outcome = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW,
                    "semantic", "task-X");
            assertTrue(outcome.cause().isEmpty());
        }

        @Test
        @DisplayName("cause is present when exception is supplied")
        void causeIsPresentWhenExceptionSupplied() {
            RuntimeException ex = new RuntimeException("NPE in task");
            CaseFailed outcome = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW,
                    "runtime exception", "task-X", ex);
            assertTrue(outcome.cause().isPresent());
            assertSame(ex, outcome.cause().get());
        }

        @Test
        @DisplayName("summary contains caseID and failedAtTaskID")
        void summaryContainsCaseIDAndTaskID() {
            CaseFailed outcome = new CaseFailed("case-99", SPEC_ID, 50L, FIXED_NOW,
                    "deadlock", "task-join-42");
            assertTrue(outcome.summary().contains("case-99"));
            assertTrue(outcome.summary().contains("task-join-42"));
        }

        @Test
        @DisplayName("isFailed returns true")
        void isFailedReturnsTrue() {
            CaseOutcome outcome = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW,
                    "fail", "task");
            assertTrue(outcome.isFailed());
        }

        @Test
        @DisplayName("isCompleted returns false")
        void isCompletedReturnsFalse() {
            CaseOutcome outcome = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW,
                    "fail", "task");
            assertFalse(outcome.isCompleted());
        }

        @Test
        @DisplayName("isCancelled returns false")
        void isCancelledReturnsFalse() {
            CaseOutcome outcome = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW,
                    "fail", "task");
            assertFalse(outcome.isCancelled());
        }

        @Test
        @DisplayName("equals holds for identical instances")
        void equalsHoldsForIdenticalInstances() {
            CaseFailed o1 = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW, "err", "t1");
            CaseFailed o2 = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW, "err", "t1");
            assertEquals(o1, o2);
        }

        @Test
        @DisplayName("hashCode consistent with equals")
        void hashCodeConsistentWithEquals() {
            CaseFailed o1 = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW, "err", "t1");
            CaseFailed o2 = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW, "err", "t1");
            assertEquals(o1.hashCode(), o2.hashCode());
        }
    }

    // ─── CaseCancelled ────────────────────────────────────────────────────

    @Nested
    @DisplayName("CaseCancelled")
    class CaseCancelledTests {

        @Test
        @DisplayName("statusCode returns CANCELLED")
        void statusCodeIsCancelled() {
            CaseCancelled outcome = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin");
            assertEquals("CANCELLED", outcome.statusCode());
        }

        @Test
        @DisplayName("cancelledBy accessor returns supplied actor")
        void cancelledByAccessorReturnsSuppliedValue() {
            CaseCancelled outcome = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "agent:supervisor");
            assertEquals("agent:supervisor", outcome.cancelledBy());
        }

        @Test
        @DisplayName("cancellationNote is empty when no note provided")
        void cancellationNoteIsEmptyWhenNoteOmitted() {
            CaseCancelled outcome = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin");
            assertEquals("", outcome.cancellationNote());
        }

        @Test
        @DisplayName("cancellationNote returns supplied note")
        void cancellationNoteReturnsSuppliedNote() {
            CaseCancelled outcome = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin", "Business event cancelled");
            assertEquals("Business event cancelled", outcome.cancellationNote());
        }

        @Test
        @DisplayName("Null cancelledBy throws IllegalArgumentException")
        void nullCancelledByThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW, null));
        }

        @Test
        @DisplayName("Blank cancelledBy throws IllegalArgumentException")
        void blankCancelledByThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW, "  "));
        }

        @Test
        @DisplayName("Null cancellationNote throws NullPointerException")
        void nullCancellationNoteThrows() {
            assertThrows(NullPointerException.class,
                    () -> new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                            "user:admin", null));
        }

        @Test
        @DisplayName("summary contains caseID and cancelledBy")
        void summaryContainsCaseIDAndCancelledBy() {
            CaseCancelled outcome = new CaseCancelled("case-77", SPEC_ID, 200L, FIXED_NOW,
                    "agent:watchdog");
            assertTrue(outcome.summary().contains("case-77"));
            assertTrue(outcome.summary().contains("agent:watchdog"));
        }

        @Test
        @DisplayName("summary includes cancellationNote when present")
        void summaryIncludesNoteWhenPresent() {
            CaseCancelled outcome = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin", "no longer needed");
            assertTrue(outcome.summary().contains("no longer needed"));
        }

        @Test
        @DisplayName("isCancelled returns true")
        void isCancelledReturnsTrue() {
            CaseOutcome outcome = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin");
            assertTrue(outcome.isCancelled());
        }

        @Test
        @DisplayName("isCompleted returns false")
        void isCompletedReturnsFalse() {
            CaseOutcome outcome = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin");
            assertFalse(outcome.isCompleted());
        }

        @Test
        @DisplayName("isFailed returns false")
        void isFailedReturnsFalse() {
            CaseOutcome outcome = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin");
            assertFalse(outcome.isFailed());
        }

        @Test
        @DisplayName("equals holds for identical instances")
        void equalsHoldsForIdenticalInstances() {
            CaseCancelled o1 = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin", "note");
            CaseCancelled o2 = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin", "note");
            assertEquals(o1, o2);
        }

        @Test
        @DisplayName("equals differs when cancelledBy differs")
        void equalsDiffersOnCancelledBy() {
            CaseCancelled o1 = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin");
            CaseCancelled o2 = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "agent:bot");
            assertNotEquals(o1, o2);
        }

        @Test
        @DisplayName("hashCode consistent with equals")
        void hashCodeConsistentWithEquals() {
            CaseCancelled o1 = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin");
            CaseCancelled o2 = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "user:admin");
            assertEquals(o1.hashCode(), o2.hashCode());
        }
    }

    // ─── Exhaustive switch pattern matching ───────────────────────────────

    @Nested
    @DisplayName("Exhaustive switch pattern matching on CaseOutcome")
    class ExhaustiveSwitchPatternMatching {

        @Test
        @DisplayName("switch on CaseCompleted selects correct branch")
        void switchSelectsCompletedBranch() {
            CaseOutcome outcome = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "out");

            String status = switch (outcome) {
                case CaseCompleted  c  -> "COMPLETED:" + c.caseID();
                case CaseFailed     f  -> "FAILED:" + f.failureReason();
                case CaseCancelled  cc -> "CANCELLED:" + cc.cancelledBy();
            };

            assertEquals("COMPLETED:" + CASE_ID, status);
        }

        @Test
        @DisplayName("switch on CaseFailed selects correct branch")
        void switchSelectsFailedBranch() {
            CaseOutcome outcome = new CaseFailed(CASE_ID, SPEC_ID, 50L, FIXED_NOW,
                    "binding error", "task-X");

            String status = switch (outcome) {
                case CaseCompleted  c  -> "COMPLETED";
                case CaseFailed     f  -> "FAILED:" + f.failureReason();
                case CaseCancelled  cc -> "CANCELLED";
            };

            assertEquals("FAILED:binding error", status);
        }

        @Test
        @DisplayName("switch on CaseCancelled selects correct branch")
        void switchSelectsCancelledBranch() {
            CaseOutcome outcome = new CaseCancelled(CASE_ID, SPEC_ID, 200L, FIXED_NOW,
                    "system:watchdog");

            String status = switch (outcome) {
                case CaseCompleted  c  -> "COMPLETED";
                case CaseFailed     f  -> "FAILED";
                case CaseCancelled  cc -> "CANCELLED:" + cc.cancelledBy();
            };

            assertEquals("CANCELLED:system:watchdog", status);
        }

        @Test
        @DisplayName("switch extracts durationMs from all three branches")
        void switchExtractsDurationFromAllBranches() {
            CaseOutcome[] outcomes = {
                new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, ""),
                new CaseFailed(CASE_ID, SPEC_ID, 200L, FIXED_NOW, "err", "task"),
                new CaseCancelled(CASE_ID, SPEC_ID, 300L, FIXED_NOW, "actor")
            };
            long[] expected = {100L, 200L, 300L};

            for (int i = 0; i < outcomes.length; i++) {
                long duration = switch (outcomes[i]) {
                    case CaseCompleted  c  -> c.durationMs();
                    case CaseFailed     f  -> f.durationMs();
                    case CaseCancelled  cc -> cc.durationMs();
                };
                assertEquals(expected[i], duration,
                        "switch branch " + i + " must return correct durationMs");
            }
        }
    }

    // ─── Cross-type inequality ────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-type inequality")
    class CrossTypeInequality {

        @Test
        @DisplayName("CaseCompleted is not equal to CaseFailed with same caseID")
        void completedNotEqualToFailed() {
            CaseOutcome completed = new CaseCompleted(CASE_ID, SPEC_ID, 100L, FIXED_NOW, "");
            CaseOutcome failed = new CaseFailed(CASE_ID, SPEC_ID, 100L, FIXED_NOW,
                    "reason", "task");
            assertNotEquals(completed, failed);
        }

        @Test
        @DisplayName("CaseFailed is not equal to CaseCancelled with same caseID")
        void failedNotEqualToCancelled() {
            CaseOutcome failed = new CaseFailed(CASE_ID, SPEC_ID, 100L, FIXED_NOW,
                    "reason", "task");
            CaseOutcome cancelled = new CaseCancelled(CASE_ID, SPEC_ID, 100L, FIXED_NOW,
                    "actor");
            assertNotEquals(failed, cancelled);
        }
    }
}
