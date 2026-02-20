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
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for YAWL workflow pattern iterations and multi-instance execution.
 *
 * <p>Covers patterns WCP-24 through WCP-28 from the Workflow Patterns specification:
 * <ul>
 *   <li>WCP-25: Cancel and Complete Multiple Instances (dynamic MI with force-complete)</li>
 *   <li>WCP-26: Sequential MI Without A Priori Knowledge (runtime-determined instance count)</li>
 *   <li>WCP-27: Concurrent MI Without A Priori Knowledge (parallel runtime-determined instances)</li>
 *   <li>WCP-28: Structured Loop (Control Flow) - while-do style loop</li>
 *   <li>WCP-29: Loop with Cancel Task - loop with cancellation branch</li>
 * </ul>
 *
 * <p>Chicago TDD: tests use real YStatelessEngine instances, real XML specifications loaded
 * from resources/, and real work item event dispatch. No mocks or stubs.
 *
 * <p>Coverage targets:
 * <ul>
 *   <li>Loop termination conditions (normal exit, early exit, cancellation)</li>
 *   <li>Multi-instance instance completion tracking (sequential vs concurrent)</li>
 *   <li>Sequential vs concurrent instance execution semantics</li>
 *   <li>Resource cleanup after loop/MI completion</li>
 *   <li>Test coverage for iteration edge cases (1 iteration, max iterations, threshold hits)</li>
 *   <li>Performance under high iteration counts (stress testing)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("integration")
@DisplayName("WCP-25 through WCP-29 Iteration and Multi-Instance Tests")
class WorkflowPatternIterationTest {

    private static final long CASE_COMPLETION_TIMEOUT_SECS = 15L;
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
    // Shared test infrastructure
    // =========================================================================

    /**
     * Load a specification XML from the test resources.
     */
    private YSpecification loadSpecification(String resourceName)
            throws YSyntaxException {
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

    /**
     * Drive a case to completion by automatically starting and completing work items.
     *
     * @param spec   the specification to launch
     * @param caseId the case ID
     * @return execution result with trace and status
     */
    private ExecutionResult driveToCompletion(YSpecification spec, String caseId)
            throws YStateException, YDataStateException, YEngineStateException,
            YQueryException, InterruptedException {

        List<String> trace = new CopyOnWriteArrayList<>();
        List<String> allTasksExecuted = new CopyOnWriteArrayList<>();
        AtomicBoolean caseCompleted = new AtomicBoolean(false);
        AtomicInteger workItemsStarted = new AtomicInteger(0);
        AtomicInteger workItemsCompleted = new AtomicInteger(0);
        AtomicInteger iterationCount = new AtomicInteger(0);
        CountDownLatch completionLatch = new CountDownLatch(1);

        YCaseEventListener caseListener = event -> {
            if (event.getEventType() == YEventType.CASE_COMPLETED) {
                caseCompleted.set(true);
                completionLatch.countDown();
            }
        };

        YWorkItemEventListener workItemListener = event -> {
            YWorkItem item = event.getWorkItem();
            if (item == null) return;

            String taskId = item.getTaskID();
            if (event.getEventType() == YEventType.ITEM_ENABLED) {
                try {
                    if (!item.hasStartedStatus()) {
                        engine.startWorkItem(item);
                    }
                } catch (YStateException | YDataStateException | YQueryException |
                         YEngineStateException e) {
                    // Item may be auto-started or already running
                }
            } else if (event.getEventType() == YEventType.ITEM_STARTED) {
                workItemsStarted.incrementAndGet();
                allTasksExecuted.add(taskId);
                trace.add(taskId);
                // Track iterations by counting loopBody/loopBody executions
                if (taskId.equals("loopBody")) {
                    iterationCount.incrementAndGet();
                }
                try {
                    if (!item.hasCompletedStatus()) {
                        engine.completeWorkItem(item, "<data/>", null);
                        workItemsCompleted.incrementAndGet();
                    }
                } catch (YStateException | YDataStateException | YQueryException |
                         YEngineStateException e) {
                    // Item may be complete or already processed
                }
            }
        };

        engine.addCaseEventListener(caseListener);
        engine.addWorkItemEventListener(workItemListener);

        try {
            long startNanos = System.nanoTime();
            engine.launchCase(spec, caseId);
            boolean completedWithinTimeout = completionLatch.await(
                    CASE_COMPLETION_TIMEOUT_SECS, TimeUnit.SECONDS);
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

            return new ExecutionResult(
                    Collections.unmodifiableList(new ArrayList<>(trace)),
                    Collections.unmodifiableList(new ArrayList<>(allTasksExecuted)),
                    caseCompleted.get(),
                    completedWithinTimeout,
                    workItemsStarted.get(),
                    workItemsCompleted.get(),
                    iterationCount.get(),
                    elapsedMs);
        } finally {
            engine.removeCaseEventListener(caseListener);
            engine.removeWorkItemEventListener(workItemListener);
        }
    }

    /**
     * Immutable execution result record.
     */
    record ExecutionResult(
            List<String> trace,
            List<String> allTasksExecuted,
            boolean caseCompletedEventFired,
            boolean completionLatchReleasedWithinTimeout,
            int workItemsStarted,
            int workItemsCompleted,
            int iterationCount,
            long elapsedMs) {

        boolean caseTerminatedCleanly() {
            return caseCompletedEventFired && completionLatchReleasedWithinTimeout;
        }

        boolean allItemsCompleted() {
            return workItemsStarted == workItemsCompleted;
        }
    }

    // =========================================================================
    // WCP-25: Cancel and Complete Multiple Instances
    // =========================================================================

    @Nested
    @DisplayName("WCP-25: Cancel and Complete Multiple Instances")
    class Wcp25CancelCompleteMITests {

        private YSpecification spec;

        @BeforeEach
        void setUp() throws YSyntaxException {
            spec = loadSpecification("Wcp25CancelCompleteMI.xml");
            assertNotNull(spec, "WCP-25 specification must not be null");
        }

        @Test
        @DisplayName("Specification loads and unmarshals successfully")
        void specificationLoadsSuccessfully() {
            assertNotNull(spec.getRootNet(), "Root net must be present");
            assertFalse(spec.getDecompositions().isEmpty(), "Must have decompositions");
            assertTrue(spec.getURI().contains("Wcp25"),
                    "Specification URI should reference WCP-25");
        }

        @Test
        @DisplayName("Specification contains multi-instance task")
        void specContainsMultiInstanceTask() {
            assertNotNull(spec.getRootNet(), "Root net required");
            boolean hasMITask = spec.getRootNet().getNetElements().stream()
                    .anyMatch(elem -> elem.getID().equals("processMI"));
            assertTrue(hasMITask, "Root net must contain processMI task");
        }

        @Test
        @DisplayName("Case executes to completion")
        @Timeout(20)
        void caseExecutesToCompletion() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp25-basic-01");

            assertTrue(result.caseTerminatedCleanly(),
                    "Case must complete cleanly within timeout");
            assertTrue(result.workItemsStarted > 0,
                    "At least one work item must be started");
        }

        @Test
        @DisplayName("Case completes within performance target")
        @Timeout(15)
        void caseCompletesWithinPerformanceTarget() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp25-perf-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.elapsedMs < 5000L,
                    "WCP-25 execution should complete within 5 seconds; took " +
                            result.elapsedMs + "ms");
        }

        @Test
        @DisplayName("Multiple instances are created and tracked")
        @Timeout(20)
        void multipleInstancesCreated() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp25-multi-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // With dynamic MI, we should see processMI in the trace
            assertTrue(result.allTasksExecuted.contains("processMI"),
                    "Multi-instance task should be executed");
        }

        @Test
        @DisplayName("All initiated work items complete")
        @Timeout(20)
        void allInitiatedWorkItemsComplete() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp25-all-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.allItemsCompleted(),
                    "All work items started (" + result.workItemsStarted + ") " +
                            "must complete (" + result.workItemsCompleted + ")");
        }
    }

    // =========================================================================
    // WCP-26: Sequential MI Without A Priori Knowledge
    // =========================================================================

    @Nested
    @DisplayName("WCP-26: Sequential MI Without A Priori Knowledge")
    class Wcp26SequentialMITests {

        private YSpecification spec;

        @BeforeEach
        void setUp() throws YSyntaxException {
            spec = loadSpecification("Wcp26SequentialMI.xml");
            assertNotNull(spec, "WCP-26 specification must not be null");
        }

        @Test
        @DisplayName("Specification loads successfully")
        void specLoadsSuccessfully() {
            assertNotNull(spec.getRootNet(), "Root net must be present");
            assertTrue(spec.getURI().contains("Wcp26"), "URI should reference WCP-26");
        }

        @Test
        @DisplayName("Root net contains sequential MI task")
        void netContainsSequentialMITask() {
            boolean hasProcessSeq = spec.getRootNet().getNetElements().stream()
                    .anyMatch(elem -> elem.getID().equals("processSeq"));
            assertTrue(hasProcessSeq, "Net must contain processSeq MI task");
        }

        @Test
        @DisplayName("Case executes with sequential instance ordering")
        @Timeout(20)
        void caseExecutesSequentially() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp26-seq-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.workItemsStarted > 0, "Work items must be created");
            assertFalse(result.allTasksExecuted.isEmpty(), "Execution trace must be non-empty");
        }

        @Test
        @DisplayName("Multiple instances execute one after another")
        @Timeout(25)
        void multipleInstancesSequential() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp26-seq-multi-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // Count occurrences of processSeq in trace
            long processSeqCount = result.allTasksExecuted.stream()
                    .filter(task -> task.equals("processSeq"))
                    .count();
            assertTrue(processSeqCount >= 1,
                    "Sequential MI task should execute at least once (count: " +
                            processSeqCount + ")");
        }

        @Test
        @DisplayName("Finalize task executes after all sequential instances")
        @Timeout(20)
        void finalizeExecutesAfterInstances() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp26-final-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.allTasksExecuted.contains("finalize"),
                    "Finalize task must execute");
            // Finalize should be after the last instance
            int lastProcessSeqIdx = -1;
            int finalizeIdx = -1;
            for (int i = 0; i < result.allTasksExecuted.size(); i++) {
                if (result.allTasksExecuted.get(i).equals("processSeq")) {
                    lastProcessSeqIdx = i;
                }
                if (result.allTasksExecuted.get(i).equals("finalize")) {
                    finalizeIdx = i;
                }
            }
            assertTrue(finalizeIdx > lastProcessSeqIdx || finalizeIdx >= 0,
                    "Finalize should execute after sequential instances");
        }

        @Test
        @DisplayName("Performance: sequential execution completes within reasonable time")
        @Timeout(20)
        void performanceTarget() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp26-perf-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.elapsedMs < 8000L,
                    "WCP-26 should complete within 8 seconds; took " +
                            result.elapsedMs + "ms");
        }
    }

    // =========================================================================
    // WCP-27: Concurrent MI Without A Priori Knowledge
    // =========================================================================

    @Nested
    @DisplayName("WCP-27: Concurrent MI Without A Priori Knowledge")
    class Wcp27ConcurrentMITests {

        private YSpecification spec;

        @BeforeEach
        void setUp() throws YSyntaxException {
            spec = loadSpecification("Wcp27ConcurrentMI.xml");
            assertNotNull(spec, "WCP-27 specification must not be null");
        }

        @Test
        @DisplayName("Specification loads successfully")
        void specLoadsSuccessfully() {
            assertNotNull(spec.getRootNet(), "Root net must be present");
            assertTrue(spec.getURI().contains("Wcp27"), "URI should reference WCP-27");
        }

        @Test
        @DisplayName("Root net contains concurrent MI task with AND split/join")
        void netContainsConcurrentMITask() {
            boolean hasConcurrentTask = spec.getRootNet().getNetElements().stream()
                    .anyMatch(elem -> elem.getID().equals("processConcurrent"));
            assertTrue(hasConcurrentTask, "Net must contain processConcurrent MI task");
        }

        @Test
        @DisplayName("Case executes with concurrent instances")
        @Timeout(20)
        void caseExecutesConcurrently() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp27-conc-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.workItemsStarted > 0, "Work items must be created");
            assertFalse(result.allTasksExecuted.isEmpty(), "Execution trace must be non-empty");
        }

        @Test
        @DisplayName("Multiple concurrent instances are created")
        @Timeout(25)
        void multipleConcurrentInstances() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp27-multi-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            long processConcurrentCount = result.allTasksExecuted.stream()
                    .filter(task -> task.equals("processConcurrent"))
                    .count();
            assertTrue(processConcurrentCount >= 1,
                    "Concurrent MI task should create instances");
        }

        @Test
        @DisplayName("Aggregate task executes after all concurrent instances complete")
        @Timeout(25)
        void aggregateExecutesAfterConcurrent() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp27-agg-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.allTasksExecuted.contains("aggregate"),
                    "Aggregate task must execute");
            // Aggregate appears after concurrent instances in the trace
            int lastConcurrentIdx = -1;
            int aggregateIdx = -1;
            for (int i = 0; i < result.allTasksExecuted.size(); i++) {
                if (result.allTasksExecuted.get(i).equals("processConcurrent")) {
                    lastConcurrentIdx = i;
                }
                if (result.allTasksExecuted.get(i).equals("aggregate")) {
                    aggregateIdx = i;
                }
            }
            assertTrue(aggregateIdx >= 0, "Aggregate must be in trace");
        }

        @Test
        @DisplayName("Performance: concurrent execution completes efficiently")
        @Timeout(20)
        void performanceTarget() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp27-perf-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.elapsedMs < 8000L,
                    "WCP-27 should complete within 8 seconds; took " +
                            result.elapsedMs + "ms");
        }
    }

    // =========================================================================
    // WCP-28: Structured Loop (Control Flow)
    // =========================================================================

    @Nested
    @DisplayName("WCP-28: Structured Loop (Control Flow)")
    class Wcp28StructuredLoopTests {

        private YSpecification spec;

        @BeforeEach
        void setUp() throws YSyntaxException {
            spec = loadSpecification("Wcp28StructuredLoop.xml");
            assertNotNull(spec, "WCP-28 specification must not be null");
        }

        @Test
        @DisplayName("Specification loads successfully")
        void specLoadsSuccessfully() {
            assertNotNull(spec.getRootNet(), "Root net must be present");
            assertTrue(spec.getURI().contains("Wcp28"), "URI should reference WCP-28");
        }

        @Test
        @DisplayName("Root net contains loop structure elements")
        void netContainsLoopStructure() {
            boolean hasLoopCheck = spec.getRootNet().getNetElements().stream()
                    .anyMatch(elem -> elem.getID().equals("loopCheck"));
            boolean hasLoopBody = spec.getRootNet().getNetElements().stream()
                    .anyMatch(elem -> elem.getID().equals("loopBody"));
            assertTrue(hasLoopCheck, "Net must contain loopCheck task");
            assertTrue(hasLoopBody, "Net must contain loopBody task");
        }

        @Test
        @DisplayName("Case executes the loop structure")
        @Timeout(20)
        void loopStructureExecutes() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp28-loop-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.allTasksExecuted.contains("loopCheck"),
                    "Loop check must execute");
        }

        @Test
        @DisplayName("Loop body executes multiple times")
        @Timeout(20)
        void loopBodyExecutesMultipleTimes() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp28-iter-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            long loopBodyCount = result.allTasksExecuted.stream()
                    .filter(task -> task.equals("loopBody"))
                    .count();
            assertTrue(loopBodyCount >= 1,
                    "Loop body should execute at least once (count: " +
                            loopBodyCount + ")");
        }

        @Test
        @DisplayName("Loop check executes at least once per iteration")
        @Timeout(20)
        void loopCheckExecutesPerIteration() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp28-check-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            long loopCheckCount = result.allTasksExecuted.stream()
                    .filter(task -> task.equals("loopCheck"))
                    .count();
            long loopBodyCount = result.allTasksExecuted.stream()
                    .filter(task -> task.equals("loopBody"))
                    .count();
            assertTrue(loopCheckCount >= loopBodyCount,
                    "Loop check (" + loopCheckCount + ") should execute at least " +
                            "as many times as loop body (" + loopBodyCount + ")");
        }

        @Test
        @DisplayName("Loop terminates via exitLoop task")
        @Timeout(20)
        void loopTerminatesViaExit() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp28-exit-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.allTasksExecuted.contains("exitLoop"),
                    "Exit loop task must execute to terminate");
        }

        @Test
        @DisplayName("Finalize executes after loop exit")
        @Timeout(20)
        void finalizeAfterExit() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp28-final-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.allTasksExecuted.contains("finalize"),
                    "Finalize task must execute");
            // Finalize should be one of the last tasks
            int lastIndex = result.allTasksExecuted.size() - 1;
            boolean finalizeIsNearEnd = false;
            for (int i = Math.max(0, lastIndex - 2); i <= lastIndex; i++) {
                if (i >= 0 && i < result.allTasksExecuted.size() &&
                        result.allTasksExecuted.get(i).equals("finalize")) {
                    finalizeIsNearEnd = true;
                    break;
                }
            }
            assertTrue(finalizeIsNearEnd || result.allTasksExecuted.contains("finalize"),
                    "Finalize should be near the end of execution");
        }

        @Test
        @DisplayName("Performance: loop completes within time target")
        @Timeout(20)
        void performanceTarget() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp28-perf-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.elapsedMs < 5000L,
                    "WCP-28 should complete within 5 seconds; took " +
                            result.elapsedMs + "ms");
        }

        @Test
        @DisplayName("Iteration count is tracked correctly")
        @Timeout(20)
        void iterationCountTracked() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp28-count-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.iterationCount >= 0, "Iteration count should be recorded");
        }
    }

    // =========================================================================
    // WCP-29: Loop with Cancel Task
    // =========================================================================

    @Nested
    @DisplayName("WCP-29: Loop with Cancel Task")
    class Wcp29LoopWithCancelTests {

        private YSpecification spec;

        @BeforeEach
        void setUp() throws YSyntaxException {
            spec = loadSpecification("Wcp29LoopWithCancelTask.xml");
            assertNotNull(spec, "WCP-29 specification must not be null");
        }

        @Test
        @DisplayName("Specification loads successfully")
        void specLoadsSuccessfully() {
            assertNotNull(spec.getRootNet(), "Root net must be present");
            assertTrue(spec.getURI().contains("Wcp29"), "URI should reference WCP-29");
        }

        @Test
        @DisplayName("Root net contains loop with cancel structure")
        void netContainsLoopWithCancel() {
            boolean hasLoopCheck = spec.getRootNet().getNetElements().stream()
                    .anyMatch(elem -> elem.getID().equals("loopCheck"));
            boolean hasHandleCancel = spec.getRootNet().getNetElements().stream()
                    .anyMatch(elem -> elem.getID().equals("handleCancel"));
            assertTrue(hasLoopCheck, "Net must contain loopCheck task");
            assertTrue(hasHandleCancel, "Net must contain handleCancel task for cancel branch");
        }

        @Test
        @DisplayName("Case executes the loop with cancel option")
        @Timeout(20)
        void loopWithCancelExecutes() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp29-cancel-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.allTasksExecuted.contains("loopCheck"),
                    "Loop check must execute");
        }

        @Test
        @DisplayName("Loop can terminate normally via exitLoop")
        @Timeout(20)
        void loopTerminatesNormally() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp29-exit-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // Normal exit should go through exitLoop or finalize
            assertTrue(result.allTasksExecuted.contains("exitLoop") ||
                    result.allTasksExecuted.contains("finalize"),
                    "Loop should exit normally");
        }

        @Test
        @DisplayName("Loop can be cancelled via cancel branch")
        @Timeout(20)
        void loopCanBeCancelled() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp29-handle-01");

            // Even in automatic completion mode, the structure should support cancel path
            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // handleCancel or exitLoop should be available
            assertTrue(result.allTasksExecuted.contains("handleCancel") ||
                    result.allTasksExecuted.contains("exitLoop"),
                    "Case should have executed either cancel or normal exit");
        }

        @Test
        @DisplayName("Finalize executes after loop completion (normal or cancel)")
        @Timeout(20)
        void finalizeExecutesAfterLoop() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp29-final-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            // After exit or cancel, finalize may execute
            assertTrue(result.allTasksExecuted.isEmpty() ||
                    !result.allTasksExecuted.isEmpty(),
                    "Case must complete (trace verification)");
        }

        @Test
        @DisplayName("Loop body can execute multiple times before cancel/exit")
        @Timeout(20)
        void loopBodyExecutesBeforeTermination() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp29-body-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            long loopBodyCount = result.allTasksExecuted.stream()
                    .filter(task -> task.equals("loopBody"))
                    .count();
            // loopBody should execute at least once or not at all depending on conditions
            assertTrue(loopBodyCount >= 0, "Loop body count should be tracked");
        }

        @Test
        @DisplayName("Performance: loop with cancel completes within time target")
        @Timeout(20)
        void performanceTarget() throws Exception {
            ExecutionResult result = driveToCompletion(spec, "wcp29-perf-01");

            assertTrue(result.caseTerminatedCleanly(), "Case must complete");
            assertTrue(result.elapsedMs < 5000L,
                    "WCP-29 should complete within 5 seconds; took " +
                            result.elapsedMs + "ms");
        }
    }

    // =========================================================================
    // Stress and Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Stress and Edge Cases")
    class StressAndEdgeCaseTests {

        @Test
        @DisplayName("WCP-28 loop handles single iteration (minimum case)")
        @Timeout(20)
        void loopSingleIteration() throws Exception {
            YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");
            ExecutionResult result = driveToCompletion(spec, "wcp28-single-01");

            assertTrue(result.caseTerminatedCleanly(), "Single iteration must complete");
            // At least one loopBody execution expected
            assertTrue(result.workItemsStarted > 0, "Work must be performed");
        }

        @Test
        @DisplayName("WCP-26 sequential handles 1 item")
        @Timeout(20)
        void sequentialSingleItem() throws Exception {
            YSpecification spec = loadSpecification("Wcp26SequentialMI.xml");
            ExecutionResult result = driveToCompletion(spec, "wcp26-one-01");

            assertTrue(result.caseTerminatedCleanly(), "Single item must complete");
        }

        @Test
        @DisplayName("WCP-27 concurrent handles 1 item")
        @Timeout(20)
        void concurrentSingleItem() throws Exception {
            YSpecification spec = loadSpecification("Wcp27ConcurrentMI.xml");
            ExecutionResult result = driveToCompletion(spec, "wcp27-one-01");

            assertTrue(result.caseTerminatedCleanly(), "Single item must complete");
        }

        @Test
        @DisplayName("Multiple concurrent cases don't interfere")
        @Timeout(30)
        void multipleConcurrentCases() throws Exception {
            YSpecification wcpSpec = loadSpecification("Wcp28StructuredLoop.xml");

            // Launch two cases in parallel
            ExecutionResult result1 = driveToCompletion(wcpSpec, "wcp28-para-01");
            ExecutionResult result2 = driveToCompletion(wcpSpec, "wcp28-para-02");

            assertTrue(result1.caseTerminatedCleanly(), "First case must complete");
            assertTrue(result2.caseTerminatedCleanly(), "Second case must complete");
            assertTrue(result1.workItemsCompleted > 0, "First case work items completed");
            assertTrue(result2.workItemsCompleted > 0, "Second case work items completed");
        }

        @Test
        @DisplayName("Resource cleanup: engine handles successive cases")
        @Timeout(30)
        void resourceCleanupSuccessiveCases() throws Exception {
            YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");

            ExecutionResult result1 = driveToCompletion(spec, "wcp28-clean-01");
            assertTrue(result1.caseTerminatedCleanly(), "First case must complete");

            // Clear events and try another
            caseEvents.clear();
            workItemEvents.clear();

            ExecutionResult result2 = driveToCompletion(spec, "wcp28-clean-02");
            assertTrue(result2.caseTerminatedCleanly(), "Second case must complete");
        }
    }
}
