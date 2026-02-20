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

package org.yawlfoundation.yawl.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.jdom2.Element;

/**
 * Unit tests for {@link ErrorSpanHelper}.
 *
 * <p>Validates error recording with proper YAWL-specific context following
 * TPS principles (making errors visible through structured logging and metrics).</p>
 *
 * <h2>Test Categories</h2>
 * <ul>
 *   <li>Utility class validation</li>
 *   <li>Core error recording methods</li>
 *   <li>Specific exception type handlers</li>
 *   <li>Deadlock and lock contention recording</li>
 *   <li>Timing-aware error recording</li>
 *   <li>Statistics and monitoring</li>
 *   <li>Thread safety verification</li>
 * </ul>
 */
@Tag("unit")
@Execution(ExecutionMode.SAME_THREAD)
class TestErrorSpanHelper {

    @BeforeEach
    void resetStatisticsBeforeEachTest() {
        ErrorSpanHelper.resetStatistics();
    }

    // ==================== Utility Class Tests ====================

    @Test
    void instantiation_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            var ctor = ErrorSpanHelper.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        }, "ErrorSpanHelper must not be instantiatable");
    }

    // ==================== recordError Tests ====================

    @Test
    void recordError_withValidInputs_incrementsStatistics() {
        Exception testException = new RuntimeException("Test error message");
        ErrorSpanHelper.recordError("test.operation", "case-123", testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("errors.test.operation"));
    }

    @Test
    void recordError_withNullCaseId_incrementsStatistics() {
        Exception testException = new RuntimeException("Engine error");
        ErrorSpanHelper.recordError("engine.operation", null, testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
    }

    @Test
    void recordError_withNullOperation_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test error");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordError(null, "case-123", testException);
        }, "Null operation should throw IllegalArgumentException");
    }

    @Test
    void recordError_withNullException_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordError("test.operation", "case-123", null);
        }, "Null exception should throw IllegalArgumentException");
    }

    // ==================== recordTaskError Tests ====================

    @Test
    void recordTaskError_withValidInputs_incrementsStatistics() {
        Exception testException = new IllegalStateException("Task failed");
        ErrorSpanHelper.recordTaskError("task-789", "case-123", "execute", testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("errors.execute.task"));
    }

    @Test
    void recordTaskError_withSpecId_incrementsStatistics() {
        Exception testException = new RuntimeException("Task error with spec");
        ErrorSpanHelper.recordTaskError("task-001", "case-002", "spec-003", "validate", testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("errors.validate.task"));
    }

    @Test
    void recordTaskError_withNullTaskId_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordTaskError(null, "case-123", "execute", testException);
        }, "Null taskId should throw IllegalArgumentException");
    }

    @Test
    void recordTaskError_withNullOperation_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordTaskError("task-789", "case-123", null, testException);
        }, "Null operation should throw IllegalArgumentException");
    }

    // ==================== recordCaseError Tests ====================

    @Test
    void recordCaseError_withValidInputs_incrementsStatistics() {
        Exception testException = new RuntimeException("Case initialization failed");
        ErrorSpanHelper.recordCaseError("case-999", "initialize", testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("errors.initialize.case"));
    }

    @Test
    void recordCaseError_withSpecId_incrementsStatistics() {
        Exception testException = new RuntimeException("Case error with spec");
        ErrorSpanHelper.recordCaseError("case-001", "spec-002", "cancel", testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("errors.cancel.case"));
    }

    @Test
    void recordCaseError_withNullCaseId_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordCaseError(null, "cancel", testException);
        }, "Null caseId should throw IllegalArgumentException");
    }

    @Test
    void recordCaseError_withNullOperation_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordCaseError("case-999", null, testException);
        }, "Null operation should throw IllegalArgumentException");
    }

    // ==================== recordSpecificationError Tests ====================

    @Test
    void recordSpecificationError_withValidInputs_incrementsStatistics() {
        Exception testException = new RuntimeException("Specification parsing failed");
        ErrorSpanHelper.recordSpecificationError("spec-111", "parse", testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("errors.parse.spec"));
    }

    @Test
    void recordSpecificationError_withNullSpecId_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordSpecificationError(null, "validate", testException);
        }, "Null specId should throw IllegalArgumentException");
    }

    @Test
    void recordSpecificationError_withNullOperation_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordSpecificationError("spec-111", null, testException);
        }, "Null operation should throw IllegalArgumentException");
    }

    // ==================== recordStateException Tests ====================

    @Test
    void recordStateException_withValidInputs_incrementsStatistics() {
        YStateException testException = new YStateException("Invalid workflow state");
        ErrorSpanHelper.recordStateException("case-123", "task-456", testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("stateExceptions"));
        assertEquals(1L, stats.get("errors.state.invalid"));
    }

    @Test
    void recordStateException_withNullException_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordStateException("case-123", "task-456", null);
        }, "Null exception should throw IllegalArgumentException");
    }

    // ==================== recordDataStateException Tests ====================

    @Test
    void recordDataStateException_withValidInputs_incrementsStatistics() {
        YDataStateException testException = createTestYDataStateException("Data validation failed");
        ErrorSpanHelper.recordDataStateException("case-123", "task-456", testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("dataStateExceptions"));
    }

    @Test
    void recordDataStateException_withNullException_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordDataStateException("case-123", "task-456", null);
        }, "Null exception should throw IllegalArgumentException");
    }

    // ==================== recordDeadlock Tests ====================

    @Test
    void recordDeadlock_withValidInputs_incrementsStatistics() {
        ErrorSpanHelper.recordDeadlock("case-123", "spec-456", "task-A,task-B,task-C");

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("deadlockEvents"));
        assertEquals(1L, stats.get("errors.deadlock.detected"));
    }

    @Test
    void recordDeadlock_withSingleTask_incrementsStatistics() {
        ErrorSpanHelper.recordDeadlock("case-single", "spec-001", "task-X");

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("deadlockEvents"));
    }

    @Test
    void recordDeadlock_withNullCaseId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordDeadlock(null, "spec-456", "task-A,task-B");
        }, "Null caseId should throw IllegalArgumentException");
    }

    @Test
    void recordDeadlock_withNullSpecId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordDeadlock("case-123", null, "task-A,task-B");
        }, "Null specId should throw IllegalArgumentException");
    }

    @Test
    void recordDeadlock_withNullTasks_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordDeadlock("case-123", "spec-456", null);
        }, "Null deadlockedTasks should throw IllegalArgumentException");
    }

    // ==================== recordLockContention Tests ====================

    @Test
    void recordLockContention_withNormalWait_incrementsStatistics() {
        ErrorSpanHelper.recordLockContention("case-123", "caseLock-123", 100);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("lockContentionEvents"));
        assertEquals(1L, stats.get("errors.lock.contention"));
    }

    @Test
    void recordLockContention_withHighWait_incrementsStatistics() {
        // High contention (500-2000ms)
        ErrorSpanHelper.recordLockContention("case-456", "caseLock-456", 750);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("lockContentionEvents"));
    }

    @Test
    void recordLockContention_withCriticalWait_incrementsStatistics() {
        // Critical contention (> 2000ms)
        ErrorSpanHelper.recordLockContention("case-789", "caseLock-789", 3000);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("lockContentionEvents"));
    }

    @Test
    void recordLockContention_withNullCaseId_incrementsStatistics() {
        // Engine-level lock (null caseId)
        ErrorSpanHelper.recordLockContention(null, "engineLock", 50);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("lockContentionEvents"));
    }

    @Test
    void recordLockContention_withNullLockName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordLockContention("case-123", null, 100);
        }, "Null lockName should throw IllegalArgumentException");
    }

    @Test
    void recordLockContention_withNegativeWait_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordLockContention("case-123", "caseLock-123", -1);
        }, "Negative waitMillis should throw IllegalArgumentException");
    }

    // ==================== recordErrorWithTiming Tests ====================

    @Test
    void recordErrorWithTiming_withValidInputs_incrementsStatistics() {
        Exception testException = new RuntimeException("Timed error");
        long durationNanos = TimeUnit.MILLISECONDS.toNanos(150);
        ErrorSpanHelper.recordErrorWithTiming("timed.operation", "case-123", testException, durationNanos);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("errors.timed.operation"));
    }

    @Test
    void recordErrorWithTiming_withNullCaseId_incrementsStatistics() {
        Exception testException = new RuntimeException("Timed engine error");
        long durationNanos = TimeUnit.MILLISECONDS.toNanos(50);
        ErrorSpanHelper.recordErrorWithTiming("engine.timed", null, testException, durationNanos);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
    }

    @Test
    void recordErrorWithTiming_withNullOperation_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordErrorWithTiming(null, "case-123", testException, 1000000L);
        }, "Null operation should throw IllegalArgumentException");
    }

    @Test
    void recordErrorWithTiming_withNegativeDuration_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordErrorWithTiming("test.operation", "case-123", testException, -1);
        }, "Negative duration should throw IllegalArgumentException");
    }

    // ==================== recordTaskErrorWithTiming Tests ====================

    @Test
    void recordTaskErrorWithTiming_withValidInputs_incrementsStatistics() {
        Exception testException = new RuntimeException("Timed task error");
        long durationNanos = TimeUnit.MILLISECONDS.toNanos(200);
        ErrorSpanHelper.recordTaskErrorWithTiming("task-001", "case-123", "execute",
            testException, durationNanos);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("errors.execute.task"));
    }

    @Test
    void recordTaskErrorWithTiming_withNullTaskId_throwsIllegalArgumentException() {
        Exception testException = new RuntimeException("Test");
        assertThrows(IllegalArgumentException.class, () -> {
            ErrorSpanHelper.recordTaskErrorWithTiming(null, "case-123", "execute",
                testException, 1000000L);
        }, "Null taskId should throw IllegalArgumentException");
    }

    // ==================== Statistics Tests ====================

    @Test
    void getStatistics_afterMultipleErrors_returnsCorrectCounts() {
        ErrorSpanHelper.recordError("op1", "case-1", new RuntimeException("Error 1"));
        ErrorSpanHelper.recordError("op2", "case-2", new RuntimeException("Error 2"));
        ErrorSpanHelper.recordDeadlock("case-3", "spec-1", "task-A,task-B");
        ErrorSpanHelper.recordLockContention("case-4", "lock-1", 100);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(4L, stats.get("totalErrors"));
        assertEquals(1L, stats.get("deadlockEvents"));
        assertEquals(1L, stats.get("lockContentionEvents"));
    }

    @Test
    void getStatistics_returnsImmutableMap() {
        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertThrows(UnsupportedOperationException.class, () -> {
            stats.put("newKey", 1L);
        }, "Statistics map should be immutable");
    }

    @Test
    void resetStatistics_clearsAllCounters() {
        ErrorSpanHelper.recordError("op1", "case-1", new RuntimeException("Error 1"));
        ErrorSpanHelper.recordDeadlock("case-2", "spec-1", "task-A");
        ErrorSpanHelper.recordLockContention("case-3", "lock-1", 50);

        ErrorSpanHelper.resetStatistics();

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(0L, stats.get("totalErrors"));
        assertEquals(0L, stats.get("deadlockEvents"));
        assertEquals(0L, stats.get("lockContentionEvents"));
    }

    // ==================== Thread Safety Tests ====================

    @Test
    void concurrentRecordOperations_maintainsConsistentState() throws Exception {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            ErrorSpanHelper.recordError(
                                "concurrent.op." + threadId,
                                "case-" + threadId + "-" + j,
                                new RuntimeException("Concurrent error " + j)
                            );
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete within timeout");
        assertEquals(0, errorCount.get(), "No errors should occur during concurrent operations");

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(threadCount * operationsPerThread, stats.get("totalErrors"),
            "All operations should be counted correctly");
    }

    @Test
    void concurrentDeadlockAndLockContention_maintainsConsistentState() throws Exception {
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(4);

        for (int t = 0; t < 4; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < iterations; i++) {
                        if (threadId % 2 == 0) {
                            ErrorSpanHelper.recordDeadlock(
                                "case-dl-" + threadId,
                                "spec-" + threadId,
                                "task-" + i
                            );
                        } else {
                            ErrorSpanHelper.recordLockContention(
                                "case-lc-" + threadId,
                                "lock-" + i,
                                i * 10
                            );
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete within timeout");

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        // 2 threads doing deadlocks, 2 threads doing lock contention
        assertEquals(2 * iterations, stats.get("deadlockEvents"),
            "All deadlock events should be counted");
        assertEquals(2 * iterations, stats.get("lockContentionEvents"),
            "All lock contention events should be counted");
    }

    // ==================== Edge Cases ====================

    @Test
    void multipleErrors_sameOperation_accumulatesCorrectly() {
        for (int i = 0; i < 5; i++) {
            ErrorSpanHelper.recordError("repeated.operation", "case-" + i,
                new RuntimeException("Error " + i));
        }

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(5L, stats.get("totalErrors"));
        assertEquals(5L, stats.get("errors.repeated.operation"));
    }

    @Test
    void recordError_withExceptionWithoutMessage_handlesGracefully() {
        Exception testException = new RuntimeException();
        ErrorSpanHelper.recordError("null.message", "case-null", testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
    }

    @Test
    void recordError_withNestedException_handlesGracefully() {
        Exception cause = new NullPointerException("Root cause");
        Exception testException = new RuntimeException("Wrapper exception", cause);
        ErrorSpanHelper.recordError("nested.exception", "case-nested", testException);

        Map<String, Long> stats = ErrorSpanHelper.getStatistics();
        assertEquals(1L, stats.get("totalErrors"));
    }

    // ==================== Helper Methods ====================

    /**
     * Creates a test YDataStateException for testing purposes.
     */
    private YDataStateException createTestYDataStateException(String message) {
        return new YDataStateException(
            null,  // query
            new Element("data"),  // queriedData
            null,  // schema
            new Element("input"),  // validationData
            "Validation error",  // xercesErrors
            "test-task",  // source
            message
        );
    }
}
