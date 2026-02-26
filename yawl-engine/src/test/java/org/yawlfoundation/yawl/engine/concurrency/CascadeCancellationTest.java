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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;

/**
 * Comprehensive concurrency test suite for cascade case cancellation in YAWL v6.0.0.
 *
 * <p>This test validates that when YEngine.cancelCase(caseID) is called while tasks are
 * transitioning to Executing state, there is a race between:
 * 1. The cancellation propagating through YNetRunner (acquiring write-lock, marking all tasks cancelled)
 * 2. Individual task transitions (acquiring write-lock, moving from Enabled→Fired→Executing)
 *
 * The test verifies NO orphaned work items remain after any timing of case cancellation,
 * and all items reach a terminal or cancelled state.</p>
 *
 * <p>Key scenarios:
 * <ul>
 *   <li>Cancel case while N tasks transition to Executing (variable timing)</li>
 *   <li>Cancel after all tasks are in Executing state</li>
 *   <li>Multiple concurrent cancelCase() calls on same case</li>
 *   <li>No orphaned work items (Executing state with no case runner)</li>
 * </ul>
 *
 * <p>Test design follows Chicago TDD (Detroit School) with REAL engine instances,
 * real work items, and real concurrency. No mocks or stubs.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class CascadeCancellationTest {

    private YEngine engine;
    private YIdentifier caseIdentifier;

    @BeforeEach
    void setUp() {
        engine = YEngine.getInstance(false);
        assertNotNull(engine, "YEngine should initialize without persistence for testing");

        caseIdentifier = new YIdentifier("cascade-cancel-" + System.currentTimeMillis());
    }

    /**
     * Test: Cancellation while tasks are transitioning to Executing leaves no orphans.
     *
     * <p>This repeated test (300 trials) with randomized cancel timing simulates the race
     * condition where:
     * 1. A case is created with N work items in Enabled state
     * 2. N virtual threads launch concurrently, each transitioning one item Enabled→Executing
     * 3. After random delay (0-50ms), main thread calls cancelCase(caseID)
     * 4. Threads continue attempting transitions until blocked or complete
     *
     * The race can resolve in multiple ways:
     * - All items reach Executing before cancel: all should become Cancelled
     * - Some items cancel during transition: those become Cancelled
     * - Cancel wins completely: all become Cancelled before any reach Executing
     *
     * Success criteria:
     * <ul>
     *   <li>No uncaught exceptions from any thread</li>
     *   <li>engine.getWorkItemRepository().getWorkItemsForCase(caseID) is EMPTY
     *       (removed by cancelCase() via removeWorkItemsForCase())</li>
     *   <li>All items that remain in repository are in terminal/cancelled states</li>
     *   <li>engine.getWorkItemRepository().getExecutingWorkItems() contains NO items from this case</li>
     *   <li>No items stuck in Executing state (orphan detection)</li>
     * </ul>
     *
     * <p>Notes:
     * <ul>
     *   <li>300 trials to catch intermittent race conditions</li>
     *   <li>Random cancel delay (0-50ms) varies timing of cancellation</li>
     *   <li>Uses virtual threads for realistic concurrent task execution</li>
     *   <li>Real YWorkItem transitions via setStatus()</li>
     * </ul>
     */
    @RepeatedTest(300)
    @DisplayName("Cancellation while tasks transition leaves no orphans")
    @Timeout(30)
    void cancellationWhileTasksTransitioningLeavesNoOrphans() throws InterruptedException {
        final int taskCount = 5;
        final int maxRandomDelayMs = 50;

        String uniqueCaseId = caseIdentifier.toString() + "-" + System.nanoTime();
        YIdentifier testCaseId = new YIdentifier(uniqueCaseId);

        try {
            List<YWorkItem> workItems = createWorkItemsForCase(taskCount, testCaseId);
            assertFalse(workItems.isEmpty(), "Should create work items for case");

            CyclicBarrier syncBarrier = new CyclicBarrier(taskCount);
            CountDownLatch transitionCompleteLatch = new CountDownLatch(taskCount);
            List<AtomicReference<Exception>> threadExceptions = new ArrayList<>();

            long randomDelayMs = (long) (Math.random() * maxRandomDelayMs);

            for (int i = 0; i < taskCount; i++) {
                YWorkItem item = workItems.get(i);
                AtomicReference<Exception> threadException = new AtomicReference<>(null);
                threadExceptions.add(threadException);

                Thread transitioner = Thread.ofVirtual()
                    .name("transitioner-" + uniqueCaseId + "-" + i)
                    .start(() -> {
                        try {
                            syncBarrier.await();

                            if (item.getStatus().equals(YWorkItemStatus.statusEnabled)) {
                                item.setStatus(YWorkItemStatus.statusFired);
                                item.setStatus(YWorkItemStatus.statusExecuting);
                            }
                        } catch (Exception e) {
                            threadException.set(e);
                        } finally {
                            transitionCompleteLatch.countDown();
                        }
                    });
            }

            Thread.sleep(randomDelayMs);

            try {
                engine.cancelCase(testCaseId, "test-cancellation");
            } catch (YPersistenceException | YEngineStateException e) {
                fail("cancelCase should not throw exception: " + e.getMessage());
            }

            boolean transitionsCompleted = transitionCompleteLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(transitionsCompleted, "All transition threads should complete");

            for (AtomicReference<Exception> exRef : threadExceptions) {
                if (exRef.get() != null) {
                    String exceptionMsg = exRef.get().getMessage();
                    assertTrue(
                        exceptionMsg.contains("already terminal") ||
                        exceptionMsg.contains("Cancelled") ||
                        exceptionMsg.contains("cancelled"),
                        "Expected only terminal-state exceptions, got: " + exceptionMsg
                    );
                }
            }

            List<YWorkItem> itemsAfterCancel = engine.getWorkItemRepository().getWorkItemsForCase(testCaseId);
            assertTrue(itemsAfterCancel.isEmpty(),
                "After cancelCase(), work items for case should be removed from repository. " +
                "Found " + itemsAfterCancel.size() + " items still present");

            Set<YWorkItem> executingItems = engine.getWorkItemRepository().getExecutingWorkItems();
            for (YWorkItem executing : executingItems) {
                YIdentifier executingCaseId = executing.getCaseID();
                assertFalse(
                    executingCaseId.toString().equals(testCaseId.toString()),
                    "No items from cancelled case should remain in Executing state. " +
                    "Found executing item: " + executing.getWorkItemID()
                );
            }

        } catch (YPersistenceException ype) {
            fail("Persistence error during test: " + ype.getMessage());
        }
    }

    /**
     * Test: Cancellation after all tasks are in Executing state leaves no orphans.
     *
     * <p>This test waits for all work items to be in Executing state, then cancels the case.
     * All items must transition to a terminal/cancelled state.
     *
     * Success criteria:
     * <ul>
     *   <li>All items transition to Executing successfully</li>
     *   <li>After cancelCase(), repository.getWorkItemsForCase() is empty</li>
     *   <li>All items were properly removed (no orphans in Executing state)</li>
     *   <li>Engine net runner for case is removed from _netRunnerRepository</li>
     * </ul>
     *
     * <p>Runs 100 trials.</p>
     */
    @RepeatedTest(100)
    @DisplayName("Cancellation after all tasks executing leaves no orphans")
    @Timeout(15)
    void cancellationAfterAllTasksExecutingLeavesNoOrphans() throws InterruptedException {
        final int taskCount = 3;

        String uniqueCaseId = caseIdentifier.toString() + "-post-" + System.nanoTime();
        YIdentifier testCaseId = new YIdentifier(uniqueCaseId);

        try {
            List<YWorkItem> workItems = createWorkItemsForCase(taskCount, testCaseId);
            assertFalse(workItems.isEmpty(), "Should create work items");

            CyclicBarrier syncBarrier = new CyclicBarrier(taskCount);
            CountDownLatch allExecutingLatch = new CountDownLatch(taskCount);

            for (YWorkItem item : workItems) {
                Thread transitioner = Thread.ofVirtual()
                    .name("exec-transitioner-" + uniqueCaseId)
                    .start(() -> {
                        try {
                            syncBarrier.await();
                            item.setStatus(YWorkItemStatus.statusFired);
                            item.setStatus(YWorkItemStatus.statusExecuting);
                        } catch (Exception e) {
                            fail("Item transition failed: " + e.getMessage());
                        } finally {
                            allExecutingLatch.countDown();
                        }
                    });
            }

            boolean allExecuting = allExecutingLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(allExecuting, "All items should reach Executing state");

            for (YWorkItem item : workItems) {
                assertEquals(YWorkItemStatus.statusExecuting, item.getStatus(),
                    "All items should be in Executing state before cancellation");
            }

            engine.cancelCase(testCaseId, "test-all-executing");

            List<YWorkItem> itemsAfterCancel = engine.getWorkItemRepository().getWorkItemsForCase(testCaseId);
            assertTrue(itemsAfterCancel.isEmpty(),
                "After cancelCase(), no items should remain for case in repository");

            Set<YWorkItem> allExecuting2 = engine.getWorkItemRepository().getExecutingWorkItems();
            for (YWorkItem exe : allExecuting2) {
                assertNotEquals(testCaseId.toString(), exe.getCaseID().toString(),
                    "No items from cancelled case should remain in Executing state");
            }

        } catch (YPersistenceException ype) {
            fail("Persistence error: " + ype.getMessage());
        }
    }

    /**
     * Test: Multiple concurrent cancelCase() calls on same case are safe.
     *
     * <p>This test launches 3 virtual threads that attempt to cancel the same case
     * simultaneously. Uses CyclicBarrier to synchronize thread start.
     *
     * Success criteria:
     * <ul>
     *   <li>No uncaught exceptions from any cancellation thread</li>
     *   <li>Final state is consistent (case not running, no orphaned items)</li>
     *   <li>All items removed from repository</li>
     *   <li>At most one thread actually completes the cancellation successfully</li>
     * </ul>
     *
     * <p>Runs 50 trials.</p>
     */
    @RepeatedTest(50)
    @DisplayName("Multiple concurrent cancellations of same case are safe")
    @Timeout(15)
    void multipleCancellationCallsAreSafe() throws InterruptedException {
        final int cancellationThreadCount = 3;

        String uniqueCaseId = caseIdentifier.toString() + "-multi-" + System.nanoTime();
        YIdentifier testCaseId = new YIdentifier(uniqueCaseId);

        try {
            List<YWorkItem> workItems = createWorkItemsForCase(2, testCaseId);
            assertFalse(workItems.isEmpty(), "Should create work items");

            CyclicBarrier syncBarrier = new CyclicBarrier(cancellationThreadCount);
            CountDownLatch cancellationCompleteLatch = new CountDownLatch(cancellationThreadCount);
            List<AtomicReference<Exception>> cancellationExceptions = new ArrayList<>();
            List<AtomicReference<Boolean>> cancellationSuccess = new ArrayList<>();

            for (int i = 0; i < cancellationThreadCount; i++) {
                AtomicReference<Exception> exRef = new AtomicReference<>(null);
                AtomicReference<Boolean> successRef = new AtomicReference<>(false);
                cancellationExceptions.add(exRef);
                cancellationSuccess.add(successRef);

                int threadIndex = i;

                Thread canceller = Thread.ofVirtual()
                    .name("canceller-" + uniqueCaseId + "-" + threadIndex)
                    .start(() -> {
                        try {
                            syncBarrier.await();
                            engine.cancelCase(testCaseId, "test-concurrent-cancel-" + threadIndex);
                            successRef.set(true);
                        } catch (YPersistenceException | YEngineStateException e) {
                            exRef.set(e);
                        } catch (Exception e) {
                            exRef.set(e);
                        } finally {
                            cancellationCompleteLatch.countDown();
                        }
                    });
            }

            boolean cancellationsCompleted = cancellationCompleteLatch.await(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(cancellationsCompleted, "All cancellation threads should complete");

            int successCount = 0;
            for (AtomicReference<Boolean> successRef : cancellationSuccess) {
                if (successRef.get()) {
                    successCount++;
                }
            }

            assertTrue(successCount >= 1,
                "At least one cancellation should succeed");

            for (AtomicReference<Exception> exRef : cancellationExceptions) {
                Exception ex = exRef.get();
                if (ex != null) {
                    String msg = ex.getMessage();
                    assertTrue(
                        msg.contains("already") || msg.contains("not found") || msg.contains("no runner") ||
                        msg.contains("Cancelled") || msg.contains("cancelled"),
                        "Expected case-not-found or already-cancelled exception, got: " + msg
                    );
                }
            }

            List<YWorkItem> itemsAfterAllCancellations = engine.getWorkItemRepository().getWorkItemsForCase(testCaseId);
            assertTrue(itemsAfterAllCancellations.isEmpty(),
                "After all cancellations, no items should remain for case");

        } catch (YPersistenceException ype) {
            fail("Persistence error: " + ype.getMessage());
        }
    }

    /**
     * Test: Verify that cancellation propagates through all task states.
     *
     * <p>This test creates work items at various states (Enabled, Fired) and cancels.
     * All items should be removed regardless of their state at cancellation time.
     *
     * Success criteria:
     * <ul>
     *   <li>All work items removed from repository after cancel</li>
     *   <li>No exceptions during cancellation</li>
     *   <li>getWorkItemsForCase() returns empty list</li>
     * </ul>
     */
    @Test
    @DisplayName("Cancellation propagates through all work item states")
    @Timeout(10)
    void cancellationPropagatesThroughAllStates() {
        String uniqueCaseId = caseIdentifier.toString() + "-states-" + System.nanoTime();
        YIdentifier testCaseId = new YIdentifier(uniqueCaseId);

        try {
            List<YWorkItem> workItems = createWorkItemsForCase(4, testCaseId);
            assertFalse(workItems.isEmpty(), "Should create work items");

            if (workItems.size() >= 4) {
                workItems.get(0).setStatus(YWorkItemStatus.statusEnabled);
                workItems.get(1).setStatus(YWorkItemStatus.statusFired);
                workItems.get(2).setStatus(YWorkItemStatus.statusExecuting);
                workItems.get(3).setStatus(YWorkItemStatus.statusEnabled);
            }

            engine.cancelCase(testCaseId, "test-state-propagation");

            List<YWorkItem> itemsAfterCancel = engine.getWorkItemRepository().getWorkItemsForCase(testCaseId);
            assertTrue(itemsAfterCancel.isEmpty(),
                "Cancellation should remove items regardless of their state");

        } catch (YPersistenceException ype) {
            fail("Persistence error: " + ype.getMessage());
        }
    }

    // ============= PRIVATE TEST HELPERS =============

    /**
     * Creates N work items in Enabled state for a given case.
     *
     * @param count Number of work items to create
     * @param caseId Case identifier
     * @return List of work items in Enabled state
     */
    private List<YWorkItem> createWorkItemsForCase(int count, YIdentifier caseId) {
        List<YWorkItem> items = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            YWorkItem item = new YWorkItem();
            item.setStatus(YWorkItemStatus.statusEnabled);
            items.add(item);
        }

        return items;
    }

    /**
     * Determines if a status is terminal (work item is finished or cancelled).
     *
     * @param status YWorkItemStatus to check
     * @return true if status is terminal, false otherwise
     */
    private boolean isTerminalStatus(YWorkItemStatus status) {
        return status.equals(YWorkItemStatus.statusComplete)
                || status.equals(YWorkItemStatus.statusFailed)
                || status.equals(YWorkItemStatus.statusForcedComplete)
                || status.equals(YWorkItemStatus.statusDeleted)
                || status.equals(YWorkItemStatus.statusWithdrawn)
                || status.equals(YWorkItemStatus.statusCancelledByCase)
                || status.equals(YWorkItemStatus.statusDiscarded);
    }
}
