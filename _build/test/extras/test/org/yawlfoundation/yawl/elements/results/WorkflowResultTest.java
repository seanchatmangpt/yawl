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
 * Chicago TDD tests for the WorkflowResult sealed class hierarchy.
 *
 * Tests cover:
 * - Sealed class constraints (permitted subclasses only)
 * - Abstract method contracts on all three subtypes
 * - Common field validation and accessors
 * - Exhaustive pattern matching in switch expressions
 * - instanceof type-pattern narrowing
 * - Equality and hashCode for each concrete subclass
 * - Type predicate methods (isSuccess, isFailure, isTimeout)
 * - toString format
 */
@DisplayName("WorkflowResult sealed hierarchy")
class WorkflowResultTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-02-20T12:00:00Z");

    // ─── Sealed class meta tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Sealed class structure")
    class SealedClassStructure {

        @Test
        @DisplayName("WorkflowResult permits exactly three subclasses")
        void sealedClassPermitsExactlyThreeSubclasses() {
            Class<?>[] permitted = WorkflowResult.class.getPermittedSubclasses();
            assertNotNull(permitted, "permittedSubclasses must not be null");
            assertEquals(3, permitted.length,
                    "WorkflowResult must permit exactly 3 subclasses");
        }

        @Test
        @DisplayName("SuccessfulWorkflow is a permitted subclass")
        void successfulWorkflowIsPermitted() {
            Set<String> names = permittedSubclassNames(WorkflowResult.class);
            assertTrue(names.contains(SuccessfulWorkflow.class.getName()),
                    "SuccessfulWorkflow must be listed as a permitted subclass");
        }

        @Test
        @DisplayName("FailedWorkflow is a permitted subclass")
        void failedWorkflowIsPermitted() {
            Set<String> names = permittedSubclassNames(WorkflowResult.class);
            assertTrue(names.contains(FailedWorkflow.class.getName()),
                    "FailedWorkflow must be listed as a permitted subclass");
        }

        @Test
        @DisplayName("TimedOutWorkflow is a permitted subclass")
        void timedOutWorkflowIsPermitted() {
            Set<String> names = permittedSubclassNames(WorkflowResult.class);
            assertTrue(names.contains(TimedOutWorkflow.class.getName()),
                    "TimedOutWorkflow must be listed as a permitted subclass");
        }

        @Test
        @DisplayName("All permitted subclasses are final")
        void allPermittedSubclassesAreFinal() {
            for (Class<?> sub : WorkflowResult.class.getPermittedSubclasses()) {
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

    // ─── Common constructor validation ───────────────────────────────────

    @Nested
    @DisplayName("Common field validation (WorkflowResult base)")
    class CommonFieldValidation {

        @Test
        @DisplayName("Null workflowID throws IllegalArgumentException for SuccessfulWorkflow")
        void nullWorkflowIDThrowsForSuccessful() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SuccessfulWorkflow(null, 100L, FIXED_NOW, "output"),
                    "Null workflowID must throw IllegalArgumentException");
        }

        @Test
        @DisplayName("Blank workflowID throws IllegalArgumentException for SuccessfulWorkflow")
        void blankWorkflowIDThrowsForSuccessful() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SuccessfulWorkflow("  ", 100L, FIXED_NOW, "output"),
                    "Blank workflowID must throw IllegalArgumentException");
        }

        @Test
        @DisplayName("Negative durationMs throws IllegalArgumentException")
        void negativeDurationThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new SuccessfulWorkflow("wf-001", -1L, FIXED_NOW, "output"),
                    "Negative durationMs must throw IllegalArgumentException");
        }

        @Test
        @DisplayName("Zero durationMs is valid")
        void zeroDurationIsValid() {
            SuccessfulWorkflow result = new SuccessfulWorkflow("wf-001", 0L, FIXED_NOW, "");
            assertEquals(0L, result.durationMs());
        }

        @Test
        @DisplayName("Null completedAt throws NullPointerException")
        void nullCompletedAtThrows() {
            assertThrows(NullPointerException.class,
                    () -> new SuccessfulWorkflow("wf-001", 100L, null, "output"),
                    "Null completedAt must throw NullPointerException");
        }

        @Test
        @DisplayName("workflowID accessor returns the supplied value")
        void workflowIDAccessorReturnsSuppliedValue() {
            SuccessfulWorkflow result = new SuccessfulWorkflow("spec-99", 200L, FIXED_NOW, "");
            assertEquals("spec-99", result.workflowID());
        }

        @Test
        @DisplayName("durationMs accessor returns the supplied value")
        void durationMsAccessorReturnsSuppliedValue() {
            SuccessfulWorkflow result = new SuccessfulWorkflow("wf-001", 4567L, FIXED_NOW, "");
            assertEquals(4567L, result.durationMs());
        }

        @Test
        @DisplayName("completedAt accessor returns the supplied Instant")
        void completedAtAccessorReturnsSuppliedInstant() {
            Instant ts = Instant.parse("2026-01-01T00:00:00Z");
            SuccessfulWorkflow result = new SuccessfulWorkflow("wf-001", 100L, ts, "");
            assertEquals(ts, result.completedAt());
        }
    }

    // ─── SuccessfulWorkflow ───────────────────────────────────────────────

    @Nested
    @DisplayName("SuccessfulWorkflow")
    class SuccessfulWorkflowTests {

        @Test
        @DisplayName("statusCode returns SUCCESS")
        void statusCodeIsSuccess() {
            SuccessfulWorkflow result = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "data");
            assertEquals("SUCCESS", result.statusCode());
        }

        @Test
        @DisplayName("message returns non-null non-blank string")
        void messageIsNonBlank() {
            SuccessfulWorkflow result = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "data");
            assertNotNull(result.message());
            assertFalse(result.message().isBlank());
        }

        @Test
        @DisplayName("outputData accessor returns the supplied data")
        void outputDataAccessorReturnsSuppliedData() {
            SuccessfulWorkflow result = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "<data/>");
            assertEquals("<data/>", result.outputData());
        }

        @Test
        @DisplayName("outputData allows empty string")
        void outputDataAllowsEmptyString() {
            SuccessfulWorkflow result = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "");
            assertEquals("", result.outputData());
        }

        @Test
        @DisplayName("outputData null throws NullPointerException")
        void nullOutputDataThrows() {
            assertThrows(NullPointerException.class,
                    () -> new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, null));
        }

        @Test
        @DisplayName("isSuccess returns true")
        void isSuccessReturnsTrue() {
            WorkflowResult result = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "");
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("isFailure returns false")
        void isFailureReturnsFalse() {
            WorkflowResult result = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "");
            assertFalse(result.isFailure());
        }

        @Test
        @DisplayName("isTimeout returns false")
        void isTimeoutReturnsFalse() {
            WorkflowResult result = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "");
            assertFalse(result.isTimeout());
        }

        @Test
        @DisplayName("equals holds for identical instances")
        void equalsHoldsForIdenticalInstances() {
            SuccessfulWorkflow r1 = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "<data/>");
            SuccessfulWorkflow r2 = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "<data/>");
            assertEquals(r1, r2);
        }

        @Test
        @DisplayName("equals is reflexive")
        void equalsIsReflexive() {
            SuccessfulWorkflow r = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "out");
            assertEquals(r, r);
        }

        @Test
        @DisplayName("equals is symmetric")
        void equalsIsSymmetric() {
            SuccessfulWorkflow r1 = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "out");
            SuccessfulWorkflow r2 = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "out");
            assertEquals(r1, r2);
            assertEquals(r2, r1);
        }

        @Test
        @DisplayName("equals differs when outputData differs")
        void equalsDiffersOnOutputData() {
            SuccessfulWorkflow r1 = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "a");
            SuccessfulWorkflow r2 = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "b");
            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("hashCode is consistent with equals")
        void hashCodeConsistentWithEquals() {
            SuccessfulWorkflow r1 = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "out");
            SuccessfulWorkflow r2 = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "out");
            assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("toString includes workflowID, durationMs, and status")
        void toStringContainsKeyFields() {
            SuccessfulWorkflow r = new SuccessfulWorkflow("wf-001", 999L, FIXED_NOW, "out");
            String str = r.toString();
            assertTrue(str.contains("wf-001"), "toString must contain workflowID");
            assertTrue(str.contains("999"), "toString must contain durationMs");
            assertTrue(str.contains("SUCCESS"), "toString must contain status code");
        }
    }

    // ─── FailedWorkflow ───────────────────────────────────────────────────

    @Nested
    @DisplayName("FailedWorkflow")
    class FailedWorkflowTests {

        @Test
        @DisplayName("statusCode returns FAILED")
        void statusCodeIsFailed() {
            FailedWorkflow result = new FailedWorkflow("wf-001", 50L, FIXED_NOW,
                    "OR-join deadlock");
            assertEquals("FAILED", result.statusCode());
        }

        @Test
        @DisplayName("reason accessor returns supplied reason")
        void reasonAccessorReturnsSuppliedValue() {
            FailedWorkflow result = new FailedWorkflow("wf-001", 50L, FIXED_NOW,
                    "data binding error");
            assertEquals("data binding error", result.reason());
        }

        @Test
        @DisplayName("null reason throws IllegalArgumentException")
        void nullReasonThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FailedWorkflow("wf-001", 50L, FIXED_NOW, null));
        }

        @Test
        @DisplayName("blank reason throws IllegalArgumentException")
        void blankReasonThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FailedWorkflow("wf-001", 50L, FIXED_NOW, "   "));
        }

        @Test
        @DisplayName("cause is empty when constructed with reason only")
        void causeIsEmptyForSemanticFailure() {
            FailedWorkflow result = new FailedWorkflow("wf-001", 50L, FIXED_NOW,
                    "semantic failure");
            assertTrue(result.cause().isEmpty());
        }

        @Test
        @DisplayName("cause is present when exception is supplied")
        void causeIsPresentWhenExceptionSupplied() {
            RuntimeException ex = new RuntimeException("engine crash");
            FailedWorkflow result = new FailedWorkflow("wf-001", 50L, FIXED_NOW,
                    "engine crash", ex);
            assertTrue(result.cause().isPresent());
            assertSame(ex, result.cause().get());
        }

        @Test
        @DisplayName("message returns reason text")
        void messageReturnsReasonText() {
            FailedWorkflow result = new FailedWorkflow("wf-001", 50L, FIXED_NOW,
                    "task timed out");
            assertEquals("task timed out", result.message());
        }

        @Test
        @DisplayName("isFailure returns true")
        void isFailureReturnsTrue() {
            WorkflowResult result = new FailedWorkflow("wf-001", 50L, FIXED_NOW, "fail");
            assertTrue(result.isFailure());
        }

        @Test
        @DisplayName("isSuccess returns false")
        void isSuccessReturnsFalse() {
            WorkflowResult result = new FailedWorkflow("wf-001", 50L, FIXED_NOW, "fail");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("equals holds for identical instances without cause")
        void equalsHoldsWithoutCause() {
            FailedWorkflow r1 = new FailedWorkflow("wf-001", 50L, FIXED_NOW, "deadlock");
            FailedWorkflow r2 = new FailedWorkflow("wf-001", 50L, FIXED_NOW, "deadlock");
            assertEquals(r1, r2);
        }

        @Test
        @DisplayName("equals differs when reason differs")
        void equalsDiffersOnReason() {
            FailedWorkflow r1 = new FailedWorkflow("wf-001", 50L, FIXED_NOW, "reason-A");
            FailedWorkflow r2 = new FailedWorkflow("wf-001", 50L, FIXED_NOW, "reason-B");
            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("hashCode consistent with equals")
        void hashCodeConsistentWithEquals() {
            FailedWorkflow r1 = new FailedWorkflow("wf-001", 50L, FIXED_NOW, "deadlock");
            FailedWorkflow r2 = new FailedWorkflow("wf-001", 50L, FIXED_NOW, "deadlock");
            assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("toString contains workflowID, durationMs, and FAILED status")
        void toStringContainsKeyFields() {
            FailedWorkflow r = new FailedWorkflow("wf-002", 75L, FIXED_NOW, "crash");
            String str = r.toString();
            assertTrue(str.contains("wf-002"));
            assertTrue(str.contains("75"));
            assertTrue(str.contains("FAILED"));
        }
    }

    // ─── TimedOutWorkflow ─────────────────────────────────────────────────

    @Nested
    @DisplayName("TimedOutWorkflow")
    class TimedOutWorkflowTests {

        @Test
        @DisplayName("statusCode returns TIMEOUT")
        void statusCodeIsTimeout() {
            TimedOutWorkflow result = new TimedOutWorkflow("wf-001", 30000L, FIXED_NOW,
                    10000L, "task-approval");
            assertEquals("TIMEOUT", result.statusCode());
        }

        @Test
        @DisplayName("timeoutMs accessor returns supplied value")
        void timeoutMsAccessorReturnsSuppliedValue() {
            TimedOutWorkflow result = new TimedOutWorkflow("wf-001", 30000L, FIXED_NOW,
                    10000L, "task-review");
            assertEquals(10000L, result.timeoutMs());
        }

        @Test
        @DisplayName("timedOutTaskID accessor returns supplied value")
        void timedOutTaskIDAccessorReturnsSuppliedValue() {
            TimedOutWorkflow result = new TimedOutWorkflow("wf-001", 30000L, FIXED_NOW,
                    10000L, "task-review");
            assertEquals("task-review", result.timedOutTaskID());
        }

        @Test
        @DisplayName("zero or negative timeoutMs throws IllegalArgumentException")
        void nonPositiveTimeoutMsThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TimedOutWorkflow("wf-001", 100L, FIXED_NOW, 0L, "task-A"));
            assertThrows(IllegalArgumentException.class,
                    () -> new TimedOutWorkflow("wf-001", 100L, FIXED_NOW, -1L, "task-A"));
        }

        @Test
        @DisplayName("null timedOutTaskID throws IllegalArgumentException")
        void nullTimedOutTaskIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TimedOutWorkflow("wf-001", 100L, FIXED_NOW, 5000L, null));
        }

        @Test
        @DisplayName("blank timedOutTaskID throws IllegalArgumentException")
        void blankTimedOutTaskIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TimedOutWorkflow("wf-001", 100L, FIXED_NOW, 5000L, "  "));
        }

        @Test
        @DisplayName("message contains timeoutMs and timedOutTaskID")
        void messageContainsTimeoutDetails() {
            TimedOutWorkflow result = new TimedOutWorkflow("wf-001", 30000L, FIXED_NOW,
                    10000L, "approval-task");
            String msg = result.message();
            assertTrue(msg.contains("10000"), "message must contain timeoutMs");
            assertTrue(msg.contains("approval-task"), "message must contain timedOutTaskID");
        }

        @Test
        @DisplayName("isTimeout returns true")
        void isTimeoutReturnsTrue() {
            WorkflowResult result = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task-X");
            assertTrue(result.isTimeout());
        }

        @Test
        @DisplayName("isSuccess returns false")
        void isSuccessReturnsFalse() {
            WorkflowResult result = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task-X");
            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("isFailure returns false")
        void isFailureReturnsFalse() {
            WorkflowResult result = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task-X");
            assertFalse(result.isFailure());
        }

        @Test
        @DisplayName("equals holds for identical instances")
        void equalsHoldsForIdenticalInstances() {
            TimedOutWorkflow r1 = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task-approval");
            TimedOutWorkflow r2 = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task-approval");
            assertEquals(r1, r2);
        }

        @Test
        @DisplayName("equals differs when timedOutTaskID differs")
        void equalsDiffersOnTimedOutTaskID() {
            TimedOutWorkflow r1 = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task-A");
            TimedOutWorkflow r2 = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task-B");
            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("hashCode consistent with equals")
        void hashCodeConsistentWithEquals() {
            TimedOutWorkflow r1 = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task-approval");
            TimedOutWorkflow r2 = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task-approval");
            assertEquals(r1.hashCode(), r2.hashCode());
        }
    }

    // ─── Exhaustive pattern matching (switch) ─────────────────────────────

    @Nested
    @DisplayName("Exhaustive switch pattern matching")
    class ExhaustiveSwitchPatternMatching {

        @Test
        @DisplayName("switch on SuccessfulWorkflow selects correct branch")
        void switchSelectsSuccessfulWorkflowBranch() {
            WorkflowResult result = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "out");

            String status = switch (result) {
                case SuccessfulWorkflow s -> "SUCCESS:" + s.outputData();
                case FailedWorkflow f     -> "FAILED:" + f.reason();
                case TimedOutWorkflow t   -> "TIMEOUT:" + t.timedOutTaskID();
            };

            assertEquals("SUCCESS:out", status);
        }

        @Test
        @DisplayName("switch on FailedWorkflow selects correct branch")
        void switchSelectsFailedWorkflowBranch() {
            WorkflowResult result = new FailedWorkflow("wf-001", 50L, FIXED_NOW,
                    "binding-error");

            String status = switch (result) {
                case SuccessfulWorkflow s -> "SUCCESS";
                case FailedWorkflow f     -> "FAILED:" + f.reason();
                case TimedOutWorkflow t   -> "TIMEOUT";
            };

            assertEquals("FAILED:binding-error", status);
        }

        @Test
        @DisplayName("switch on TimedOutWorkflow selects correct branch")
        void switchSelectsTimedOutWorkflowBranch() {
            WorkflowResult result = new TimedOutWorkflow("wf-001", 30000L, FIXED_NOW,
                    10000L, "task-approval");

            String status = switch (result) {
                case SuccessfulWorkflow s -> "SUCCESS";
                case FailedWorkflow f     -> "FAILED";
                case TimedOutWorkflow t   -> "TIMEOUT:" + t.timedOutTaskID();
            };

            assertEquals("TIMEOUT:task-approval", status);
        }

        @Test
        @DisplayName("switch extracts durationMs from all three branches")
        void switchExtractsDurationFromAllBranches() {
            WorkflowResult[] results = {
                new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, ""),
                new FailedWorkflow("wf-001", 200L, FIXED_NOW, "fail"),
                new TimedOutWorkflow("wf-001", 300L, FIXED_NOW, 5000L, "task-X")
            };
            long[] expected = {100L, 200L, 300L};

            for (int i = 0; i < results.length; i++) {
                long duration = switch (results[i]) {
                    case SuccessfulWorkflow s -> s.durationMs();
                    case FailedWorkflow f     -> f.durationMs();
                    case TimedOutWorkflow t   -> t.durationMs();
                };
                assertEquals(expected[i], duration,
                        "switch branch " + i + " must return correct durationMs");
            }
        }
    }

    // ─── instanceof type-pattern narrowing ───────────────────────────────

    @Nested
    @DisplayName("instanceof pattern narrowing")
    class InstanceofPatternNarrowing {

        @Test
        @DisplayName("instanceof SuccessfulWorkflow with binding gives access to outputData")
        void instanceofSuccessfulWorkflowProvidesOutputData() {
            WorkflowResult result = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW,
                    "<result>42</result>");
            if (result instanceof SuccessfulWorkflow s) {
                assertEquals("<result>42</result>", s.outputData());
            } else {
                fail("Pattern match should have bound to SuccessfulWorkflow");
            }
        }

        @Test
        @DisplayName("instanceof FailedWorkflow with binding gives access to reason and cause")
        void instanceofFailedWorkflowProvidesReasonAndCause() {
            RuntimeException cause = new RuntimeException("crash");
            WorkflowResult result = new FailedWorkflow("wf-001", 50L, FIXED_NOW,
                    "system crash", cause);
            if (result instanceof FailedWorkflow f) {
                assertEquals("system crash", f.reason());
                assertTrue(f.cause().isPresent());
                assertSame(cause, f.cause().get());
            } else {
                fail("Pattern match should have bound to FailedWorkflow");
            }
        }

        @Test
        @DisplayName("instanceof TimedOutWorkflow with binding gives access to timeoutMs")
        void instanceofTimedOutWorkflowProvidesTimeoutMs() {
            WorkflowResult result = new TimedOutWorkflow("wf-001", 30000L, FIXED_NOW,
                    10000L, "approval");
            if (result instanceof TimedOutWorkflow t) {
                assertEquals(10000L, t.timeoutMs());
                assertEquals("approval", t.timedOutTaskID());
            } else {
                fail("Pattern match should have bound to TimedOutWorkflow");
            }
        }

        @Test
        @DisplayName("SuccessfulWorkflow does not match FailedWorkflow pattern")
        void successfulWorkflowDoesNotMatchFailedPattern() {
            WorkflowResult result = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "out");
            assertFalse(result instanceof FailedWorkflow);
        }

        @Test
        @DisplayName("FailedWorkflow does not match TimedOutWorkflow pattern")
        void failedWorkflowDoesNotMatchTimedOutPattern() {
            WorkflowResult result = new FailedWorkflow("wf-001", 50L, FIXED_NOW, "fail");
            assertFalse(result instanceof TimedOutWorkflow);
        }
    }

    // ─── Cross-type inequality ────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-type inequality")
    class CrossTypeInequality {

        @Test
        @DisplayName("SuccessfulWorkflow is not equal to FailedWorkflow with same workflowID")
        void successfulNotEqualToFailed() {
            WorkflowResult success = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "");
            WorkflowResult failed = new FailedWorkflow("wf-001", 100L, FIXED_NOW, "reason");
            assertNotEquals(success, failed);
        }

        @Test
        @DisplayName("FailedWorkflow is not equal to TimedOutWorkflow with same workflowID")
        void failedNotEqualToTimedOut() {
            WorkflowResult failed = new FailedWorkflow("wf-001", 100L, FIXED_NOW, "reason");
            WorkflowResult timeout = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task-X");
            assertNotEquals(failed, timeout);
        }

        @Test
        @DisplayName("No result equals null")
        void noResultEqualsNull() {
            WorkflowResult success = new SuccessfulWorkflow("wf-001", 100L, FIXED_NOW, "");
            WorkflowResult failed = new FailedWorkflow("wf-001", 50L, FIXED_NOW, "err");
            WorkflowResult timeout = new TimedOutWorkflow("wf-001", 100L, FIXED_NOW,
                    5000L, "task");
            assertNotEquals(null, success);
            assertNotEquals(null, failed);
            assertNotEquals(null, timeout);
        }
    }
}
