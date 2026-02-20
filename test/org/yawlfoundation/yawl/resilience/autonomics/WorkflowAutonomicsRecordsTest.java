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

package org.yawlfoundation.yawl.resilience.autonomics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for the Java records defined inside WorkflowAutonomicsEngine.
 *
 * Tests cover:
 * - RetryPolicy record: canonical constructor validation, accessor method syntax,
 *   auto-generated equals/hashCode, calculateBackoff behaviour
 * - HealthReport record: constructor validation, isHealthy predicate,
 *   accessor method syntax, equals/hashCode
 * - StuckCase record: constructor validation, stuckDurationMs(), accessor method syntax,
 *   equals/hashCode
 * - DeadLetterQueue: add/poll/size/clear operations, thread-safety invariants
 *
 * Real instances only — no mocks.
 */
@DisplayName("WorkflowAutonomicsEngine inner record types")
class WorkflowAutonomicsRecordsTest {

    // ─── RetryPolicy record ───────────────────────────────────────────────

    @Nested
    @DisplayName("RetryPolicy record")
    class RetryPolicyTests {

        @Test
        @DisplayName("Canonical constructor creates record with expected accessor values")
        void canonicalConstructorCreatesRecordWithAccessorValues() {
            WorkflowAutonomicsEngine.RetryPolicy policy =
                    new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 2.0, true);

            assertEquals(3, policy.maxAttempts());
            assertEquals(100L, policy.initialBackoffMs());
            assertEquals(2.0, policy.backoffMultiplier(), 1e-9);
            assertTrue(policy.isTransient());
        }

        @Test
        @DisplayName("Accessor methods use record (method) syntax, not getters")
        void accessorMethodsUseRecordSyntax() throws NoSuchMethodException {
            // Records expose accessors as methods with the field name — no get prefix
            Class<WorkflowAutonomicsEngine.RetryPolicy> cls =
                    WorkflowAutonomicsEngine.RetryPolicy.class;
            assertNotNull(cls.getMethod("maxAttempts"));
            assertNotNull(cls.getMethod("initialBackoffMs"));
            assertNotNull(cls.getMethod("backoffMultiplier"));
            assertNotNull(cls.getMethod("isTransient"));
        }

        @Test
        @DisplayName("Zero maxAttempts throws IllegalArgumentException")
        void zeroMaxAttemptsThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.RetryPolicy(0, 100L, 2.0, true));
        }

        @Test
        @DisplayName("Negative maxAttempts throws IllegalArgumentException")
        void negativeMaxAttemptsThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.RetryPolicy(-1, 100L, 2.0, true));
        }

        @Test
        @DisplayName("Zero initialBackoffMs throws IllegalArgumentException")
        void zeroInitialBackoffMsThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.RetryPolicy(3, 0L, 2.0, true));
        }

        @Test
        @DisplayName("Negative initialBackoffMs throws IllegalArgumentException")
        void negativeInitialBackoffMsThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.RetryPolicy(3, -1L, 2.0, true));
        }

        @Test
        @DisplayName("backoffMultiplier of exactly 1.0 throws IllegalArgumentException")
        void backoffMultiplierExactly1ThrowsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 1.0, true));
        }

        @Test
        @DisplayName("backoffMultiplier below 1.0 throws IllegalArgumentException")
        void backoffMultiplierBelow1ThrowsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 0.5, true));
        }

        @Test
        @DisplayName("isTransient false produces non-transient policy")
        void isTransientFalseProducesNonTransientPolicy() {
            WorkflowAutonomicsEngine.RetryPolicy policy =
                    new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 2.0, false);
            assertFalse(policy.isTransient());
        }

        @Test
        @DisplayName("calculateBackoff returns initialBackoffMs for first attempt")
        void calculateBackoffForFirstAttemptReturnsInitialDelay() {
            WorkflowAutonomicsEngine.RetryPolicy policy =
                    new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 2.0, true);
            assertEquals(100L, policy.calculateBackoff(1));
        }

        @Test
        @DisplayName("calculateBackoff doubles for second attempt with multiplier 2.0")
        void calculateBackoffDoublesForSecondAttempt() {
            WorkflowAutonomicsEngine.RetryPolicy policy =
                    new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 2.0, true);
            assertEquals(200L, policy.calculateBackoff(2));
        }

        @Test
        @DisplayName("calculateBackoff uses exponential growth across multiple attempts")
        void calculateBackoffIsExponential() {
            WorkflowAutonomicsEngine.RetryPolicy policy =
                    new WorkflowAutonomicsEngine.RetryPolicy(5, 50L, 3.0, true);
            // attempt 1: 50 * 3^0 = 50
            // attempt 2: 50 * 3^1 = 150
            // attempt 3: 50 * 3^2 = 450
            assertEquals(50L,  policy.calculateBackoff(1));
            assertEquals(150L, policy.calculateBackoff(2));
            assertEquals(450L, policy.calculateBackoff(3));
        }

        @Test
        @DisplayName("Auto-generated equals holds for identical records")
        void equalsHoldsForIdenticalRecords() {
            WorkflowAutonomicsEngine.RetryPolicy p1 =
                    new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 2.0, true);
            WorkflowAutonomicsEngine.RetryPolicy p2 =
                    new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 2.0, true);
            assertEquals(p1, p2);
        }

        @Test
        @DisplayName("Auto-generated equals is reflexive")
        void equalsIsReflexive() {
            WorkflowAutonomicsEngine.RetryPolicy p =
                    new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 2.0, true);
            assertEquals(p, p);
        }

        @Test
        @DisplayName("Auto-generated equals differs when maxAttempts differs")
        void equalsDiffersOnMaxAttempts() {
            WorkflowAutonomicsEngine.RetryPolicy p1 =
                    new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 2.0, true);
            WorkflowAutonomicsEngine.RetryPolicy p2 =
                    new WorkflowAutonomicsEngine.RetryPolicy(5, 100L, 2.0, true);
            assertNotEquals(p1, p2);
        }

        @Test
        @DisplayName("Auto-generated hashCode consistent with equals")
        void hashCodeConsistentWithEquals() {
            WorkflowAutonomicsEngine.RetryPolicy p1 =
                    new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 2.0, true);
            WorkflowAutonomicsEngine.RetryPolicy p2 =
                    new WorkflowAutonomicsEngine.RetryPolicy(3, 100L, 2.0, true);
            assertEquals(p1.hashCode(), p2.hashCode());
        }

        @Test
        @DisplayName("RetryPolicy is a Java record")
        void retryPolicyIsAJavaRecord() {
            assertTrue(WorkflowAutonomicsEngine.RetryPolicy.class.isRecord(),
                    "RetryPolicy must be a Java record");
        }
    }

    // ─── HealthReport record ──────────────────────────────────────────────

    @Nested
    @DisplayName("HealthReport record")
    class HealthReportTests {

        @Test
        @DisplayName("Canonical constructor creates record with expected accessors")
        void canonicalConstructorCreatesRecordWithAccessors() {
            WorkflowAutonomicsEngine.HealthReport report =
                    new WorkflowAutonomicsEngine.HealthReport(10, 2, 1708876800L);

            assertEquals(10, report.activeCases());
            assertEquals(2, report.stuckCases());
            assertEquals(1708876800L, report.timestamp());
        }

        @Test
        @DisplayName("Negative activeCases throws IllegalArgumentException")
        void negativeActiveCasesThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.HealthReport(-1, 0, 0L));
        }

        @Test
        @DisplayName("Negative stuckCases throws IllegalArgumentException")
        void negativeStuckCasesThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.HealthReport(5, -1, 0L));
        }

        @Test
        @DisplayName("stuckCases exceeding activeCases throws IllegalArgumentException")
        void stuckCasesExceedingActiveCasesThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.HealthReport(3, 5, 0L));
        }

        @Test
        @DisplayName("stuckCases equal to activeCases is valid")
        void stuckCasesEqualToActiveCasesIsValid() {
            WorkflowAutonomicsEngine.HealthReport report =
                    new WorkflowAutonomicsEngine.HealthReport(5, 5, 0L);
            assertEquals(5, report.stuckCases());
        }

        @Test
        @DisplayName("isHealthy returns true when stuckCases is zero")
        void isHealthyReturnsTrueWhenNoStuckCases() {
            WorkflowAutonomicsEngine.HealthReport report =
                    new WorkflowAutonomicsEngine.HealthReport(10, 0, 0L);
            assertTrue(report.isHealthy());
        }

        @Test
        @DisplayName("isHealthy returns false when stuckCases is positive")
        void isHealthyReturnsFalseWhenStuckCasesPositive() {
            WorkflowAutonomicsEngine.HealthReport report =
                    new WorkflowAutonomicsEngine.HealthReport(10, 1, 0L);
            assertFalse(report.isHealthy());
        }

        @Test
        @DisplayName("isHealthy returns true for zero active and zero stuck")
        void isHealthyReturnsTrueForNoActiveCases() {
            WorkflowAutonomicsEngine.HealthReport report =
                    new WorkflowAutonomicsEngine.HealthReport(0, 0, 0L);
            assertTrue(report.isHealthy());
        }

        @Test
        @DisplayName("Auto-generated equals holds for identical records")
        void equalsHoldsForIdenticalRecords() {
            WorkflowAutonomicsEngine.HealthReport r1 =
                    new WorkflowAutonomicsEngine.HealthReport(5, 1, 12345L);
            WorkflowAutonomicsEngine.HealthReport r2 =
                    new WorkflowAutonomicsEngine.HealthReport(5, 1, 12345L);
            assertEquals(r1, r2);
        }

        @Test
        @DisplayName("Auto-generated equals differs when stuckCases differs")
        void equalsDiffersOnStuckCases() {
            WorkflowAutonomicsEngine.HealthReport r1 =
                    new WorkflowAutonomicsEngine.HealthReport(5, 1, 12345L);
            WorkflowAutonomicsEngine.HealthReport r2 =
                    new WorkflowAutonomicsEngine.HealthReport(5, 2, 12345L);
            assertNotEquals(r1, r2);
        }

        @Test
        @DisplayName("Auto-generated hashCode consistent with equals")
        void hashCodeConsistentWithEquals() {
            WorkflowAutonomicsEngine.HealthReport r1 =
                    new WorkflowAutonomicsEngine.HealthReport(5, 1, 12345L);
            WorkflowAutonomicsEngine.HealthReport r2 =
                    new WorkflowAutonomicsEngine.HealthReport(5, 1, 12345L);
            assertEquals(r1.hashCode(), r2.hashCode());
        }

        @Test
        @DisplayName("HealthReport is a Java record")
        void healthReportIsAJavaRecord() {
            assertTrue(WorkflowAutonomicsEngine.HealthReport.class.isRecord(),
                    "HealthReport must be a Java record");
        }
    }

    // ─── StuckCase record ─────────────────────────────────────────────────

    @Nested
    @DisplayName("StuckCase record")
    class StuckCaseTests {

        private YIdentifier makeIdentifier() {
            return new YIdentifier(null);
        }

        @Test
        @DisplayName("Canonical constructor creates record with expected accessors")
        void canonicalConstructorCreatesRecordWithAccessors() {
            YIdentifier id = makeIdentifier();
            long now = System.currentTimeMillis() - 1000L;
            WorkflowAutonomicsEngine.StuckCase stuck =
                    new WorkflowAutonomicsEngine.StuckCase(id, "no progress", now);

            assertSame(id, stuck.caseID());
            assertEquals("no progress", stuck.reason());
            assertEquals(now, stuck.stuckSinceMs());
        }

        @Test
        @DisplayName("Null caseID throws IllegalArgumentException")
        void nullCaseIDThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.StuckCase(null, "reason", 0L));
        }

        @Test
        @DisplayName("Null reason throws IllegalArgumentException")
        void nullReasonThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.StuckCase(makeIdentifier(), null, 0L));
        }

        @Test
        @DisplayName("Blank reason throws IllegalArgumentException")
        void blankReasonThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> new WorkflowAutonomicsEngine.StuckCase(makeIdentifier(), "  ", 0L));
        }

        @Test
        @DisplayName("stuckDurationMs returns non-negative value for past timestamp")
        void stuckDurationMsIsNonNegativeForPastTimestamp() {
            long pastMs = System.currentTimeMillis() - 60_000L; // 1 minute ago
            WorkflowAutonomicsEngine.StuckCase stuck =
                    new WorkflowAutonomicsEngine.StuckCase(makeIdentifier(), "no progress", pastMs);

            long duration = stuck.stuckDurationMs();
            assertTrue(duration >= 0L, "stuckDurationMs must be non-negative");
            assertTrue(duration >= 59_000L, "stuckDurationMs must be close to 60s");
        }

        @Test
        @DisplayName("stuckDurationMs returns zero (clamped) for future timestamp")
        void stuckDurationMsIsZeroForFutureTimestamp() {
            long futureMs = System.currentTimeMillis() + 60_000L;
            WorkflowAutonomicsEngine.StuckCase stuck =
                    new WorkflowAutonomicsEngine.StuckCase(makeIdentifier(), "no progress", futureMs);

            assertEquals(0L, stuck.stuckDurationMs(),
                    "stuckDurationMs must be clamped to 0 for future timestamps");
        }

        @Test
        @DisplayName("Auto-generated equals holds for identical records")
        void equalsHoldsForIdenticalRecords() {
            YIdentifier id = makeIdentifier();
            long ts = 12345L;
            WorkflowAutonomicsEngine.StuckCase s1 =
                    new WorkflowAutonomicsEngine.StuckCase(id, "stuck", ts);
            WorkflowAutonomicsEngine.StuckCase s2 =
                    new WorkflowAutonomicsEngine.StuckCase(id, "stuck", ts);
            assertEquals(s1, s2);
        }

        @Test
        @DisplayName("Auto-generated hashCode consistent with equals")
        void hashCodeConsistentWithEquals() {
            YIdentifier id = makeIdentifier();
            long ts = 99999L;
            WorkflowAutonomicsEngine.StuckCase s1 =
                    new WorkflowAutonomicsEngine.StuckCase(id, "stuck", ts);
            WorkflowAutonomicsEngine.StuckCase s2 =
                    new WorkflowAutonomicsEngine.StuckCase(id, "stuck", ts);
            assertEquals(s1.hashCode(), s2.hashCode());
        }

        @Test
        @DisplayName("StuckCase is a Java record")
        void stuckCaseIsAJavaRecord() {
            assertTrue(WorkflowAutonomicsEngine.StuckCase.class.isRecord(),
                    "StuckCase must be a Java record");
        }
    }

    // ─── DeadLetterQueue class ────────────────────────────────────────────

    @Nested
    @DisplayName("DeadLetterQueue")
    class DeadLetterQueueTests {

        private WorkflowAutonomicsEngine.StuckCase makeStuckCase(String reason) {
            return new WorkflowAutonomicsEngine.StuckCase(
                    new YIdentifier(null), reason, System.currentTimeMillis());
        }

        @Test
        @DisplayName("New queue is empty")
        void newQueueIsEmpty() {
            WorkflowAutonomicsEngine.DeadLetterQueue dlq =
                    new WorkflowAutonomicsEngine.DeadLetterQueue();
            assertEquals(0, dlq.size());
        }

        @Test
        @DisplayName("add increases size by one")
        void addIncreasesSizeByOne() {
            WorkflowAutonomicsEngine.DeadLetterQueue dlq =
                    new WorkflowAutonomicsEngine.DeadLetterQueue();
            dlq.add(makeStuckCase("failed case 1"));
            assertEquals(1, dlq.size());
        }

        @Test
        @DisplayName("poll returns the added item and decreases size")
        void pollReturnsAddedItemAndDecreasesSize() {
            WorkflowAutonomicsEngine.DeadLetterQueue dlq =
                    new WorkflowAutonomicsEngine.DeadLetterQueue();
            WorkflowAutonomicsEngine.StuckCase stuck = makeStuckCase("reason-A");
            dlq.add(stuck);

            Optional<WorkflowAutonomicsEngine.StuckCase> polled = dlq.poll();
            assertTrue(polled.isPresent());
            assertEquals(stuck, polled.get());
            assertEquals(0, dlq.size());
        }

        @Test
        @DisplayName("poll on empty queue returns empty Optional")
        void pollOnEmptyQueueReturnsEmpty() {
            WorkflowAutonomicsEngine.DeadLetterQueue dlq =
                    new WorkflowAutonomicsEngine.DeadLetterQueue();
            assertTrue(dlq.poll().isEmpty());
        }

        @Test
        @DisplayName("getAll returns all added items")
        void getAllReturnsAllItems() {
            WorkflowAutonomicsEngine.DeadLetterQueue dlq =
                    new WorkflowAutonomicsEngine.DeadLetterQueue();
            dlq.add(makeStuckCase("case-1"));
            dlq.add(makeStuckCase("case-2"));
            dlq.add(makeStuckCase("case-3"));

            assertEquals(3, dlq.getAll().size());
        }

        @Test
        @DisplayName("getAll returns snapshot; queue unchanged after getAll")
        void getAllReturnsSnapshotWithoutDrainingQueue() {
            WorkflowAutonomicsEngine.DeadLetterQueue dlq =
                    new WorkflowAutonomicsEngine.DeadLetterQueue();
            dlq.add(makeStuckCase("case-1"));
            dlq.add(makeStuckCase("case-2"));

            dlq.getAll(); // should not drain
            assertEquals(2, dlq.size(),
                    "getAll must return a snapshot without draining the queue");
        }

        @Test
        @DisplayName("clear empties the queue")
        void clearEmptiesQueue() {
            WorkflowAutonomicsEngine.DeadLetterQueue dlq =
                    new WorkflowAutonomicsEngine.DeadLetterQueue();
            dlq.add(makeStuckCase("case-1"));
            dlq.add(makeStuckCase("case-2"));
            dlq.clear();
            assertEquals(0, dlq.size());
        }

        @Test
        @DisplayName("Multiple items can be added and polled in FIFO order")
        void itemsPolledInFIFOOrder() {
            WorkflowAutonomicsEngine.DeadLetterQueue dlq =
                    new WorkflowAutonomicsEngine.DeadLetterQueue();
            WorkflowAutonomicsEngine.StuckCase first = makeStuckCase("first");
            WorkflowAutonomicsEngine.StuckCase second = makeStuckCase("second");
            dlq.add(first);
            dlq.add(second);

            Optional<WorkflowAutonomicsEngine.StuckCase> polled1 = dlq.poll();
            Optional<WorkflowAutonomicsEngine.StuckCase> polled2 = dlq.poll();

            assertTrue(polled1.isPresent());
            assertTrue(polled2.isPresent());
            // ConcurrentLinkedQueue is FIFO
            assertEquals("first",  polled1.get().reason());
            assertEquals("second", polled2.get().reason());
        }
    }
}
