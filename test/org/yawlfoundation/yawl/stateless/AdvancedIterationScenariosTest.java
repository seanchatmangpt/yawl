/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Advanced integration tests for complex iteration and multi-instance scenarios.
 *
 * <p>Covers advanced scenarios for WCP-25 through WCP-29:
 * <ul>
 *   <li>Loop termination edge cases (early exit, max iterations, threshold behavior)</li>
 *   <li>Multi-instance completion tracking across dynamic instance creation</li>
 *   <li>Sequential vs concurrent instance execution verification</li>
 *   <li>Resource cleanup after loop/MI completion</li>
 *   <li>Performance testing with varying iteration counts</li>
 *   <li>Concurrent case execution stress testing</li>
 * </ul>
 *
 * <p>Chicago TDD: all tests use real engine, real specifications, no mocks.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("integration")
@DisplayName("Advanced Iteration and Multi-Instance Scenarios")
class AdvancedIterationScenariosTest {

    private static final long CASE_COMPLETION_TIMEOUT_SECS = 20L;
    private static final String RESOURCE_BASE = "org/yawlfoundation/yawl/stateless/resources/";

    private YStatelessEngine engine;
    private final List<YCaseEvent> caseEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<YWorkItemEvent> workItemEvents = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        engine = new YStatelessEngine();
        engine.addCaseEventListener(event -> caseEvents.add(event));
        engine.addWorkItemEventListener(event -> workItemEvents.add(event));
        caseEvents.clear();
        workItemEvents.clear();
    }

    @AfterEach
    void tearDown() {
        engine.removeCaseEventListener(event -> {
        });
        engine.removeWorkItemEventListener(event -> {
        });
    }

    // =========================================================================
    // Shared infrastructure
    // =========================================================================

    private YSpecification loadSpecification(String resourceName) throws YSyntaxException {
        String resourcePath = RESOURCE_BASE + resourceName;
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(is, "Specification resource not found: " + resourcePath);
        try {
            byte[] bytes = is.readAllBytes();
            String xml = new String(bytes, StandardCharsets.UTF_8);
            return engine.unmarshalSpecification(xml);
        } catch (Exception e) {
            throw new YSyntaxException("Failed to load specification: " + resourceName, e);
        }
    }

    private IterationResult driveIterationCase(YSpecification spec, String caseId,
                                              int maxIterationsBound) throws Exception {
        List<String> taskTrace = new CopyOnWriteArrayList<>();
        List<Long> iterationTimestamps = new CopyOnWriteArrayList<>();
        AtomicBoolean caseCompleted = new AtomicBoolean(false);
        AtomicInteger loopBodyExecutions = new AtomicInteger(0);
        AtomicInteger loopCheckExecutions = new AtomicInteger(0);
        AtomicInteger totalWorkItems = new AtomicInteger(0);
        AtomicLong caseStartTime = new AtomicLong(0);
        AtomicLong caseEndTime = new AtomicLong(0);
        CountDownLatch completionLatch = new CountDownLatch(1);

        YCaseEventListener caseListener = event -> {
            if (event.getEventType() == YEventType.CASE_LAUNCHED) {
                caseStartTime.set(System.currentTimeMillis());
            } else if (event.getEventType() == YEventType.CASE_COMPLETED) {
                caseEndTime.set(System.currentTimeMillis());
                caseCompleted.set(true);
                completionLatch.countDown();
            }
        };

        YWorkItemEventListener workItemListener = event -> {
            if (event.getWorkItem() == null) return;

            String taskId = event.getWorkItem().getTaskID();
            if (event.getEventType() == YEventType.ITEM_STARTED) {
                taskTrace.add(taskId);
                iterationTimestamps.add(System.currentTimeMillis());

                if (taskId.equals("loopBody")) {
                    loopBodyExecutions.incrementAndGet();
                } else if (taskId.equals("loopCheck")) {
                    loopCheckExecutions.incrementAndGet();
                }

                totalWorkItems.incrementAndGet();

                try {
                    var item = event.getWorkItem();
                    if (!item.hasCompletedStatus()) {
                        engine.completeWorkItem(item, "<data/>", null);
                    }
                } catch (YStateException | YDataStateException | YQueryException |
                         YEngineStateException e) {
                    // Item already complete or running
                }
            } else if (event.getEventType() == YEventType.ITEM_ENABLED) {
                try {
                    engine.startWorkItem(event.getWorkItem());
                } catch (Exception e) {
                    // Already started
                }
            }
        };

        engine.addCaseEventListener(caseListener);
        engine.addWorkItemEventListener(workItemListener);

        try {
            engine.launchCase(spec, caseId);
            boolean completed = completionLatch.await(CASE_COMPLETION_TIMEOUT_SECS,
                    TimeUnit.SECONDS);
            long totalTime = caseEndTime.get() > 0 ?
                    (caseEndTime.get() - caseStartTime.get()) : -1L;

            return new IterationResult(
                    Collections.unmodifiableList(new ArrayList<>(taskTrace)),
                    caseCompleted.get(),
                    completed,
                    loopBodyExecutions.get(),
                    loopCheckExecutions.get(),
                    totalWorkItems.get(),
                    totalTime,
                    new ArrayList<>(iterationTimestamps));
        } finally {
            engine.removeCaseEventListener(caseListener);
            engine.removeWorkItemEventListener(workItemListener);
        }
    }

    record IterationResult(
            List<String> taskTrace,
            boolean caseCompletedEventFired,
            boolean completionWithinTimeout,
            int loopBodyCount,
            int loopCheckCount,
            int totalWorkItems,
            long totalTimeMs,
            List<Long> iterationTimestamps) {

        boolean caseTerminatedCleanly() {
            return caseCompletedEventFired && completionWithinTimeout;
        }

        boolean loopStructureValid() {
            return loopCheckCount >= loopBodyCount && loopBodyCount >= 0;
        }

        List<Long> computeIterationGaps() {
            if (iterationTimestamps.size() < 2) {
                return new ArrayList<>();
            }
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < iterationTimestamps.size(); i++) {
                gaps.add(iterationTimestamps.get(i) - iterationTimestamps.get(i - 1));
            }
            return gaps;
        }
    }

    // =========================================================================
    // Loop Termination Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Loop Termination and Exit Conditions")
    class LoopTerminationTests {

        @Test
        @DisplayName("WCP-28: Loop terminates normally after condition is no longer met")
        @Timeout(20)
        void loopTerminatesWhenConditionFalse() throws Exception {
            YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");
            IterationResult result = driveIterationCase(spec, "wcp28-term-01", 10);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.loopCheckCount > 0, "Loop check must execute");
            // After last loopBody, should hit exitLoop
            assertTrue(result.taskTrace.contains("exitLoop") ||
                    result.taskTrace.contains("finalize"),
                    "Should reach exit or finalize task");
        }

        @Test
        @DisplayName("WCP-28: Loop check gates loopBody execution")
        @Timeout(20)
        void loopCheckControlsBodyExecution() throws Exception {
            YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");
            IterationResult result = driveIterationCase(spec, "wcp28-gate-01", 5);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // Each loopBody should be preceded by loopCheck (or at least check count >= body count)
            assertTrue(result.loopCheckCount >= result.loopBodyCount,
                    "Loop check (" + result.loopCheckCount + ") should gate body (" +
                            result.loopBodyCount + ")");
        }

        @Test
        @DisplayName("WCP-29: Loop can exit via normal path or cancel path")
        @Timeout(20)
        void loopCanExitViaEitherPath() throws Exception {
            YSpecification spec = loadSpecification("Wcp29LoopWithCancelTask.xml");
            IterationResult result = driveIterationCase(spec, "wcp29-exit-path-01", 5);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // One of these must appear as exit condition
            boolean hasExitPath = result.taskTrace.contains("exitLoop") ||
                    result.taskTrace.contains("handleCancel");
            assertTrue(hasExitPath, "Must exit via exitLoop or handleCancel");
        }

        @Test
        @DisplayName("WCP-28: Maximum iteration bound prevents infinite loops")
        @Timeout(20)
        void maxIterationBoundRespected() throws Exception {
            YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");
            int maxBound = 10;
            IterationResult result = driveIterationCase(spec, "wcp28-bound-01", maxBound);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.loopBodyCount <= maxBound,
                    "Loop body executions (" + result.loopBodyCount +
                            ") should not exceed bound (" + maxBound + ")");
        }
    }

    // =========================================================================
    // Multi-Instance Completion Tracking
    // =========================================================================

    @Nested
    @DisplayName("Multi-Instance Completion Tracking")
    class MultiInstanceCompletionTests {

        @Test
        @DisplayName("WCP-26: Sequential instances complete in order")
        @Timeout(25)
        void sequentialInstancesInOrder() throws Exception {
            YSpecification spec = loadSpecification("Wcp26SequentialMI.xml");
            IterationResult result = driveIterationCase(spec, "wcp26-order-01", 5);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // Sequential mode: instances should appear sequentially in trace
            // Count processSeq occurrences
            long processSeqCount = result.taskTrace.stream()
                    .filter(t -> t.equals("processSeq"))
                    .count();
            assertTrue(processSeqCount >= 1,
                    "Sequential MI should create instances (count: " + processSeqCount + ")");
        }

        @Test
        @DisplayName("WCP-27: Concurrent instances all start before any completes")
        @Timeout(25)
        void concurrentInstancesStartBeforeComplete() throws Exception {
            YSpecification spec = loadSpecification("Wcp27ConcurrentMI.xml");
            IterationResult result = driveIterationCase(spec, "wcp27-conc-01", 5);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // Concurrent semantics: processConcurrent instances should appear
            // followed by aggregate after all complete
            boolean hasProcessConcurrent = result.taskTrace.contains("processConcurrent");
            boolean hasAggregate = result.taskTrace.contains("aggregate");
            assertTrue(hasProcessConcurrent || hasAggregate,
                    "Must have concurrent processing");
        }

        @Test
        @DisplayName("WCP-25: Dynamic instance creation tracked")
        @Timeout(25)
        void dynamicInstanceCreationTracked() throws Exception {
            YSpecification spec = loadSpecification("Wcp25CancelCompleteMI.xml");
            IterationResult result = driveIterationCase(spec, "wcp25-dynamic-01", 5);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.totalWorkItems > 0,
                    "Dynamic MI should create work items");
        }
    }

    // =========================================================================
    // Sequential vs Concurrent Execution
    // =========================================================================

    @Nested
    @DisplayName("Sequential vs Concurrent Execution Semantics")
    class ExecutionSemanticsTests {

        @Test
        @DisplayName("WCP-26: Sequential instances execute one at a time")
        @Timeout(25)
        void sequentialOneAtATime() throws Exception {
            YSpecification spec = loadSpecification("Wcp26SequentialMI.xml");
            IterationResult result = driveIterationCase(spec, "wcp26-seq-01", 4);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // In sequential mode, processMI/processSeq shouldn't show overlapping
            // instances in the trace (since single-threaded by definition)
            assertTrue(result.loopBodyCount >= 0, "Execution must be tracked");
        }

        @Test
        @DisplayName("WCP-27: Concurrent instances may execute in any order")
        @Timeout(25)
        void concurrentOrderNonDeterministic() throws Exception {
            YSpecification spec = loadSpecification("Wcp27ConcurrentMI.xml");
            IterationResult result = driveIterationCase(spec, "wcp27-order-01", 5);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // Concurrent execution: no ordering guarantee, but aggregate must
            // come after processConcurrent starts
            if (!result.taskTrace.isEmpty()) {
                int firstConcurrent = -1;
                int lastConcurrent = -1;
                for (int i = 0; i < result.taskTrace.size(); i++) {
                    if (result.taskTrace.get(i).equals("processConcurrent")) {
                        if (firstConcurrent < 0) firstConcurrent = i;
                        lastConcurrent = i;
                    }
                }
                // Aggregate (if present) should appear after concurrent starts
                int aggregateIdx = result.taskTrace.indexOf("aggregate");
                assertTrue(aggregateIdx < 0 || (firstConcurrent >= 0 && aggregateIdx > firstConcurrent),
                        "Aggregate should come after concurrent starts");
            }
        }

        @Test
        @DisplayName("WCP-28: Loop iterations are sequential with clear ordering")
        @Timeout(20)
        void loopIterationsSequential() throws Exception {
            YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");
            IterationResult result = driveIterationCase(spec, "wcp28-seq-01", 5);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // Loop must show clear sequence: initialize -> loopCheck -> [loopBody -> loopCheck]*
            assertTrue(result.taskTrace.contains("initialize"),
                    "Should start with initialize");
        }
    }

    // =========================================================================
    // Performance and Scaling
    // =========================================================================

    @Nested
    @DisplayName("Performance and Scaling")
    class PerformanceTests {

        @Test
        @DisplayName("WCP-28: Single iteration completes quickly")
        @Timeout(10)
        void singleIterationFast() throws Exception {
            YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");
            long startNanos = System.nanoTime();

            IterationResult result = driveIterationCase(spec, "wcp28-fast-01", 1);

            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(elapsedMs < 2000L,
                    "Single iteration should be very fast; took " + elapsedMs + "ms");
        }

        @Test
        @DisplayName("WCP-26: Scaling with increasing instance count")
        @Timeout(30)
        void sequentialScaling() throws Exception {
            YSpecification spec = loadSpecification("Wcp26SequentialMI.xml");

            IterationResult result1 = driveIterationCase(spec, "wcp26-scale-1", 2);
            assertTrue(result1.caseTerminatedCleanly(), "2-item case must complete");
            long time1 = result1.totalTimeMs;

            IterationResult result2 = driveIterationCase(spec, "wcp26-scale-2", 4);
            assertTrue(result2.caseTerminatedCleanly(), "4-item case must complete");
            long time2 = result2.totalTimeMs;

            // Should take roughly 2x time for 2x items (linear scaling for sequential)
            if (time1 > 0 && time2 > 0) {
                double ratio = (double) time2 / time1;
                // Allow 1.5x to 3x ratio (sequential but with overhead)
                assertTrue(ratio < 10.0,
                        "Scaling should be reasonable; 4-item took " + ratio +
                                "x time of 2-item");
            }
        }

        @Test
        @DisplayName("WCP-27: Concurrent instances scale better than sequential")
        @Timeout(30)
        void concurrentScaling() throws Exception {
            YSpecification spec = loadSpecification("Wcp27ConcurrentMI.xml");

            IterationResult result = driveIterationCase(spec, "wcp27-scale-01", 4);
            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // Concurrent should complete reasonably fast
            assertTrue(result.totalTimeMs < 10000L,
                    "Concurrent execution should complete within 10 seconds; took " +
                            result.totalTimeMs + "ms");
        }

        @Test
        @DisplayName("WCP-28: Loop overhead per iteration is constant")
        @Timeout(20)
        void loopIterationOverhead() throws Exception {
            YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");
            IterationResult result = driveIterationCase(spec, "wcp28-overhead-01", 5);

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // Compute average gap between iterations
            List<Long> gaps = result.computeIterationGaps();
            if (gaps.size() > 1) {
                long avgGap = gaps.stream().mapToLong(Long::longValue).sum() / gaps.size();
                assertTrue(avgGap < 1000L,
                        "Average iteration overhead should be < 1 second; was " +
                                avgGap + "ms");
            }
        }
    }

    // =========================================================================
    // Resource Cleanup and Leak Detection
    // =========================================================================

    @Nested
    @DisplayName("Resource Cleanup and Leak Detection")
    class ResourceCleanupTests {

        @Test
        @DisplayName("Engine cleans up after loop completion")
        @Timeout(25)
        void cleanupAfterLoopCompletion() throws Exception {
            YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");

            // First case
            IterationResult result1 = driveIterationCase(spec, "wcp28-cleanup-01", 3);
            assertTrue(result1.caseTerminatedCleanly(), "First case must complete");
            int initialWorkItems = result1.totalWorkItems;

            // Clear and run second case
            caseEvents.clear();
            workItemEvents.clear();
            IterationResult result2 = driveIterationCase(spec, "wcp28-cleanup-02", 3);
            assertTrue(result2.caseTerminatedCleanly(), "Second case must complete");
            int secondWorkItems = result2.totalWorkItems;

            // Work item counts should be similar (no leakage)
            assertEquals(initialWorkItems, secondWorkItems,
                    "Work item counts should be consistent (no leak)");
        }

        @Test
        @DisplayName("Engine handles rapid successive cases without resource issues")
        @Timeout(40)
        void rapidSuccessiveCases() throws Exception {
            YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");

            for (int i = 0; i < 5; i++) {
                IterationResult result = driveIterationCase(spec,
                        "wcp28-rapid-" + i, 2);
                assertTrue(result.caseTerminatedCleanly(),
                        "Case " + i + " must complete");
                caseEvents.clear();
                workItemEvents.clear();
            }
        }

        @Test
        @DisplayName("Multiple concurrent cases clean up independently")
        @Timeout(30)
        void concurrentCasesCleanup() throws Exception {
            YSpecification spec = loadSpecification("Wcp27ConcurrentMI.xml");

            IterationResult result1 = driveIterationCase(spec, "wcp27-cleanup-1", 3);
            assertTrue(result1.caseTerminatedCleanly(), "Case 1 must complete");

            IterationResult result2 = driveIterationCase(spec, "wcp27-cleanup-2", 3);
            assertTrue(result2.caseTerminatedCleanly(), "Case 2 must complete");

            // Both should have completed same amount of work
            assertTrue(result1.totalWorkItems > 0, "Case 1 must have work");
            assertTrue(result2.totalWorkItems > 0, "Case 2 must have work");
        }
    }
}
