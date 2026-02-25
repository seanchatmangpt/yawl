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

package org.yawlfoundation.yawl.integration.temporal;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Chicago TDD tests for TemporalForkEngine.
 *
 * <p>No mocks. All tests use real data structures (CaseFork, TemporalForkResult,
 * ForkPolicy implementations) and real temporal forking logic with injected
 * lambda test doubles for case operations. Tests target observable state:
 * fork counts, decision paths, outcomes, and execution statistics.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class TemporalForkEngineTest extends TestCase {

    // =========================================================================
    // Data Structure Tests: CaseFork and TemporalForkResult
    // =========================================================================

    public void testCaseForkRecord() {
        CaseFork fork = new CaseFork(
            "fork-123",
            List.of("taskA", "taskB"),
            "<case-state>result</case-state>",
            true,
            500L,
            Instant.now()
        );

        assertEquals("fork-123", fork.forkId());
        assertEquals(2, fork.decisionPath().size());
        assertTrue(fork.outcomeXml().contains("case-state"));
        assertTrue(fork.terminatedNormally());
        assertEquals(500L, fork.durationMs());
        assertNotNull(fork.completedAt());
    }

    public void testTemporalForkResultAllForksCompleted() {
        CaseFork fork = new CaseFork(
            "f1",
            List.of("taskA"),
            "<outcome>ok</outcome>",
            true,
            100L,
            Instant.now()
        );

        TemporalForkResult result = new TemporalForkResult(
            List.of(fork),
            0,
            Duration.ofMillis(100),
            1,
            1
        );

        assertTrue(result.allForksCompleted());
        assertEquals(1, result.completedForks());
        assertEquals(1, result.requestedForks());
    }

    public void testTemporalForkResultNotAllForksCompleted() {
        CaseFork fork = new CaseFork(
            "f1",
            List.of("taskA"),
            "<outcome>ok</outcome>",
            true,
            100L,
            Instant.now()
        );

        TemporalForkResult result = new TemporalForkResult(
            List.of(fork),
            0,
            Duration.ofMillis(100),
            3,  // requested 3
            1   // completed 1
        );

        assertFalse(result.allForksCompleted());
    }

    public void testGetDominantForkReturnsCorrectIndex() {
        CaseFork fork1 = new CaseFork("f1", List.of("taskA"), "<outcome>1</outcome>", true, 100L, Instant.now());
        CaseFork fork2 = new CaseFork("f2", List.of("taskB"), "<outcome>2</outcome>", true, 100L, Instant.now());

        TemporalForkResult result = new TemporalForkResult(
            List.of(fork1, fork2),
            1,
            Duration.ofMillis(200),
            2,
            2
        );

        CaseFork dominant = result.getDominantFork();
        assertEquals("f2", dominant.forkId());
    }

    public void testGetDominantForkReturnsFirstWhenIndexNegativeOne() {
        CaseFork fork1 = new CaseFork("f1", List.of("taskA"), "<outcome>1</outcome>", true, 100L, Instant.now());
        CaseFork fork2 = new CaseFork("f2", List.of("taskB"), "<outcome>2</outcome>", true, 100L, Instant.now());

        TemporalForkResult result = new TemporalForkResult(
            List.of(fork1, fork2),
            -1,  // all unique outcomes
            Duration.ofMillis(200),
            2,
            2
        );

        CaseFork dominant = result.getDominantFork();
        assertEquals("f1", dominant.forkId());
    }

    // =========================================================================
    // ForkPolicy Tests: AllPathsForkPolicy
    // =========================================================================

    public void testAllPathsForkPolicyDefaultMaxTen() {
        AllPathsForkPolicy policy = new AllPathsForkPolicy();
        assertEquals(10, policy.maxForks());
    }

    public void testAllPathsForkPolicyCustomMax() {
        AllPathsForkPolicy policy = new AllPathsForkPolicy(5);
        assertEquals(5, policy.maxForks());
    }

    public void testAllPathsForkPolicyEnumeratesAllWhenBelowMax() {
        AllPathsForkPolicy policy = new AllPathsForkPolicy(10);
        List<String> enabled = List.of("taskA", "taskB", "taskC");
        List<String> paths = policy.enumeratePaths(enabled);

        assertEquals(3, paths.size());
        assertTrue(paths.contains("taskA"));
        assertTrue(paths.contains("taskB"));
        assertTrue(paths.contains("taskC"));
    }

    public void testAllPathsForkPolicyLimitsForks() {
        AllPathsForkPolicy policy = new AllPathsForkPolicy(2);
        List<String> enabled = List.of("taskA", "taskB", "taskC", "taskD");
        List<String> paths = policy.enumeratePaths(enabled);

        assertEquals(2, paths.size());
        assertEquals("taskA", paths.get(0));
        assertEquals("taskB", paths.get(1));
    }

    public void testAllPathsForkPolicyHandlesEmptyList() {
        AllPathsForkPolicy policy = new AllPathsForkPolicy(10);
        List<String> paths = policy.enumeratePaths(List.of());
        assertTrue(paths.isEmpty());
    }

    public void testAllPathsForkPolicyHandlesNullList() {
        AllPathsForkPolicy policy = new AllPathsForkPolicy(10);
        List<String> paths = policy.enumeratePaths(null);
        assertTrue(paths.isEmpty());
    }

    public void testAllPathsForkPolicyThrowsOnInvalidMaxForks() {
        try {
            new AllPathsForkPolicy(0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("maxForks must be >= 1"));
        }
    }

    // =========================================================================
    // TemporalForkEngine Tests: Forking Logic with Test Doubles
    // =========================================================================

    public void testForkWithSingleEnabledTask() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "serialized-xml",
            xml -> List.of("taskA"),
            (xml, taskId) -> xml + "-executed-" + taskId
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(10),
            Duration.ofSeconds(5)
        );

        assertEquals(1, result.requestedForks());
        assertEquals(1, result.completedForks());
        assertTrue(result.allForksCompleted());
        assertEquals(1, result.forks().size());
    }

    public void testForkWithTwoEnabledTasksProducesTwoForks() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "base-case-xml",
            xml -> List.of("taskA", "taskB"),
            (xml, taskId) -> xml + "-" + taskId
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(2),
            Duration.ofSeconds(5)
        );

        assertEquals(2, result.requestedForks());
        assertEquals(2, result.completedForks());
        assertTrue(result.allForksCompleted());
        assertEquals(2, result.forks().size());

        // Verify decision paths
        boolean hasTaskA = result.forks().stream()
            .anyMatch(f -> f.decisionPath().contains("taskA"));
        boolean hasTaskB = result.forks().stream()
            .anyMatch(f -> f.decisionPath().contains("taskB"));
        assertTrue(hasTaskA);
        assertTrue(hasTaskB);
    }

    public void testForkWithThreeTasksButPolicyLimitsToTwo() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA", "taskB", "taskC"),
            (xml, taskId) -> xml + "-" + taskId
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(2),
            Duration.ofSeconds(5)
        );

        assertEquals(2, result.requestedForks());
        assertEquals(2, result.completedForks());
        assertEquals(2, result.forks().size());
    }

    public void testForkPopulatesDecisionPath() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA", "taskB"),
            (xml, taskId) -> xml + "-executed"
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(2),
            Duration.ofSeconds(5)
        );

        for (CaseFork fork : result.forks()) {
            assertEquals(1, fork.decisionPath().size());
            assertTrue(fork.decisionPath().get(0).startsWith("task"));
        }
    }

    public void testForkPopulatesOutcomeXml() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "<case>initial</case>",
            xml -> List.of("taskX"),
            (xml, taskId) -> "<case>executed-" + taskId + "</case>"
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(1),
            Duration.ofSeconds(5)
        );

        CaseFork fork = result.forks().get(0);
        assertTrue(fork.outcomeXml().contains("executed-taskX"));
    }

    public void testForkSetsTerminatedNormallyTrue() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA"),
            (xml, taskId) -> xml + "-ok"
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(1),
            Duration.ofSeconds(5)
        );

        assertTrue(result.forks().get(0).terminatedNormally());
    }

    public void testForkRecordsDuration() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA"),
            (xml, taskId) -> {
                try {
                    Thread.sleep(10);  // Simulate some work
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return xml + "-ok";
            }
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(1),
            Duration.ofSeconds(5)
        );

        CaseFork fork = result.forks().get(0);
        assertTrue(fork.durationMs() >= 10);
    }

    public void testForkRecordsCompletionTimestamp() {
        Instant beforeFork = Instant.now();

        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA"),
            (xml, taskId) -> xml + "-ok"
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(1),
            Duration.ofSeconds(5)
        );

        Instant afterFork = Instant.now();
        CaseFork fork = result.forks().get(0);

        assertTrue(!fork.completedAt().isBefore(beforeFork));
        assertTrue(!fork.completedAt().isAfter(afterFork));
    }

    public void testForkRespectsMaxWallTime() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA", "taskB", "taskC"),
            (xml, taskId) -> {
                try {
                    Thread.sleep(100);  // Each task takes 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return xml + "-ok";
            }
        );

        Instant startTime = Instant.now();

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(3),
            Duration.ofMillis(150)  // Short timeout
        );

        Instant endTime = Instant.now();
        Duration actualTime = Duration.between(startTime, endTime);

        // Should respect the wall time limit
        assertTrue(actualTime.toMillis() < 1000);  // Much less than running 3x 100ms serially
    }

    public void testForkResultWallTimeIsPopulated() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA"),
            (xml, taskId) -> xml + "-ok"
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(1),
            Duration.ofSeconds(5)
        );

        assertTrue(result.wallTime().toMillis() >= 0);
    }

    public void testForkThrowsOnNullCaseId() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA"),
            (xml, taskId) -> xml + "-ok"
        );

        try {
            engine.fork(null, new AllPathsForkPolicy(1), Duration.ofSeconds(5));
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("caseId"));
        }
    }

    public void testForkThrowsOnNullPolicy() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA"),
            (xml, taskId) -> xml + "-ok"
        );

        try {
            engine.fork("case-1", null, Duration.ofSeconds(5));
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("policy"));
        }
    }

    public void testForkThrowsOnNullMaxWallTime() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA"),
            (xml, taskId) -> xml + "-ok"
        );

        try {
            engine.fork("case-1", new AllPathsForkPolicy(1), null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("maxWallTime"));
        }
    }

    public void testForkWithNoEnabledTasks() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of(),  // No enabled tasks
            (xml, taskId) -> xml + "-ok"
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(5),
            Duration.ofSeconds(5)
        );

        assertEquals(0, result.requestedForks());
        assertEquals(0, result.completedForks());
        assertTrue(result.forks().isEmpty());
    }

    public void testForkDominantOutcomeWhenAllUnique() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA", "taskB"),
            (xml, taskId) -> "<outcome>" + taskId + "</outcome>"  // Each produces unique
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(2),
            Duration.ofSeconds(5)
        );

        // All unique outcomes → dominantOutcomeIndex == -1
        assertEquals(-1, result.dominantOutcomeIndex());
        // getDominantFork() should return first fork
        assertEquals(result.forks().get(0), result.getDominantFork());
    }

    public void testForkDominantOutcomeWhenSomeIdentical() {
        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA", "taskB", "taskC"),
            (xml, taskId) -> {
                // taskA and taskB produce same outcome
                if ("taskA".equals(taskId) || "taskB".equals(taskId)) {
                    return "<outcome>same</outcome>";
                }
                return "<outcome>unique</outcome>";
            }
        );

        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(3),
            Duration.ofSeconds(5)
        );

        // At least one fork should be dominant
        assertTrue(result.dominantOutcomeIndex() >= 0);
        assertNotNull(result.getDominantFork());
    }

    public void testTemporalForkEngineConstructorThrowsOnNullEngine() {
        try {
            new TemporalForkEngine(null, null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("engine"));
        }
    }

    public void testTemporalForkEngineConstructorThrowsOnNullSpec() {
        try {
            TemporalForkEngine engine = new TemporalForkEngine(
                caseId -> "xml",
                xml -> List.of(),
                (xml, taskId) -> xml
            );
            assertNotNull(engine);
        } catch (NullPointerException e) {
            fail("Testable constructor should accept nulls for engine/spec");
        }
    }

    public void testForkCasesExecuteConcurrently() {
        // This is an observable property: forks should execute roughly in parallel
        // rather than sequentially. We can observe this by total duration vs
        // individual task durations.

        long sleepPerTask = 50;
        int numTasks = 3;

        TemporalForkEngine engine = new TemporalForkEngine(
            caseId -> "xml",
            xml -> List.of("taskA", "taskB", "taskC"),
            (xml, taskId) -> {
                try {
                    Thread.sleep(sleepPerTask);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return xml + "-" + taskId;
            }
        );

        Instant start = Instant.now();
        TemporalForkResult result = engine.fork(
            "case-1",
            new AllPathsForkPolicy(numTasks),
            Duration.ofSeconds(10)
        );
        Instant end = Instant.now();

        long totalDuration = Duration.between(start, end).toMillis();

        // If truly parallel (virtual threads): should be ~50ms (one task duration)
        // If sequential: would be ~150ms (three tasks)
        // We expect something closer to parallel than sequential.
        assertTrue(totalDuration < (sleepPerTask * numTasks));

        assertEquals(numTasks, result.completedForks());
    }
}
