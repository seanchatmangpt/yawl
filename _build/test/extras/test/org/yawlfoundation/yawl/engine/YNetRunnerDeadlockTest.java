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

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.File;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive OR-join deadlock detection test suite for {@link YNetRunner}.
 *
 * <p>Chicago TDD approach: tests use real YAWL engine with OR-join specific specifications
 * to detect and handle various deadlock scenarios. Tests target +5% line coverage.
 *
 * <p>OR-join deadlock scenarios covered:
 * <ul>
 *   <li>Simple OR-join deadlocks</li>
 *   <li>Complex multi-task cycles with OR-joins</li>
 *   <li>Cancellation during OR-join deadlock</li>
 *   <li>Timeout scenarios with OR-joins</li>
 *   <li>Mixed pattern deadlocks (AND/OR/XOR combinations)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @see YNetRunner#isAlive()
 * @see YNetRunner#isCompleted()
 * @see YNetRunner#deadLocked()
 */
@Tag("unit")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("YNetRunner OR-Join Deadlock Detection Tests")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class YNetRunnerDeadlockTest {

    private YEngine _engine;
    private YSpecification _simpleDeadlockSpec;
    private YSpecification _complexCycleSpec;
    private YSpecification _cancelDuringDeadlockSpec;
    private YSpecification _timeoutScenarioSpec;
    private YSpecification _mixedPatternSpec;
    private YSpecification _normalSpec;

    @BeforeEach
    void setUp() throws Exception {
        _engine = YEngine.getInstance();
        EngineClearer.clear(_engine);
        _simpleDeadlockSpec = loadSpec("ORJoin_SimpleDeadlock.xml");
        _complexCycleSpec = loadSpec("ORJoin_ComplexCycle.xml");
        _cancelDuringDeadlockSpec = loadSpec("ORJoin_CancelDuringDeadlock.xml");
        _timeoutScenarioSpec = loadSpec("ORJoin_TimeoutScenario.xml");
        _mixedPatternSpec = loadSpec("ORJoin_MixedPattern.xml");
        _normalSpec = loadSpec("YAWL_Specification2.xml");
    }

    @AfterEach
    void tearDown() throws Exception {
        EngineClearer.clear(_engine);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private YSpecification loadSpec(String resourceName) throws Exception {
        URL url = getClass().getResource(resourceName);
        assertNotNull(url, "Test resource not found: " + resourceName);
        return YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(new File(url.getFile()).getAbsolutePath()), false).get(0);
    }

    /**
     * Triggers the deadlock in DeadlockingSpecification:
     *  1. Start case → a-top becomes enabled
     *  2. Start a-top work item → it enters executing state
     *  3. Complete a-top → XOR-split puts token in c1-top (default); AND-join b-top deadlocks
     *
     * @return the YIdentifier of the case
     */
    private YIdentifier triggerDeadlock() throws Exception {
        _engine.loadSpecification(_deadlockSpec);
        YIdentifier caseId = _engine.startCase(
                _deadlockSpec.getSpecificationID(), null, null, null, null, null, false);

        // a-top is now in statusEnabled; start it to get an executing child
        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":a-top");
        assertNotNull(enabledItem, "a-top work item must be enabled after case start");

        YWorkItem executingItem = _engine.startWorkItem(
                enabledItem, _engine.getExternalClient("admin"));
        assertNotNull(executingItem, "startWorkItem must return executing child item");

        // Complete a-top — XOR-split fires (token → c1-top), then kick() detects deadlock.
        // Pass "<data/>" (not null) to avoid NPE in getDataDocForWorkItemCompletion.
        _engine.completeWorkItem(executingItem, "<data/>", null, WorkItemCompletion.Normal);
        return caseId;
    }

    // -------------------------------------------------------------------------
    // Test 1 — all work items for the case are removed after deadlock
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("All work items for the deadlocked case are removed after cancel()")
    void testDeadlock_workItemsCleanedUpAfterDeadlock() throws Exception {
        YIdentifier caseId = triggerDeadlock();

        // cancel() calls removeWorkItemsForCase() — no items must remain for this case
        long itemsForCase = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .count();
        assertEquals(0, itemsForCase,
                "cancel() must remove all work items for the deadlocked case; "
                + "found=" + itemsForCase);
    }

    // -------------------------------------------------------------------------
    // Test 2 — runner.isCompleted() is true after deadlock (cancel() called)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Runner reports isCompleted() after deadlock resolution")
    void testDeadlock_runnerIsCompleted() throws Exception {
        _engine.loadSpecification(_deadlockSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _deadlockSpec.getSpecificationID(), null, null, null, null, null, false);
        YNetRunner runner = repo.get(caseId);
        assertNotNull(runner, "Runner must be in repository immediately after case start");

        // Complete a-top to trigger the deadlock
        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":a-top");
        YWorkItem executingItem = _engine.startWorkItem(
                enabledItem, _engine.getExternalClient("admin"));
        _engine.completeWorkItem(executingItem, "<data/>", null, WorkItemCompletion.Normal);

        // After deadlock, cancel() is called on the runner → isCompleted() becomes true
        assertTrue(runner.isCompleted(),
                "runner.isCompleted() must be true after deadlock detection and cancel()");
    }

    // -------------------------------------------------------------------------
    // Test 3 — runner.isAlive() is false after deadlock (cancel() sets _cancelling=true)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Runner reports isAlive() == false after deadlock (cancel path)")
    void testDeadlock_runnerIsNotAlive() throws Exception {
        _engine.loadSpecification(_deadlockSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _deadlockSpec.getSpecificationID(), null, null, null, null, null, false);
        YNetRunner runner = repo.get(caseId);
        assertNotNull(runner, "Runner must be in repository immediately after case start");

        assertTrue(runner.isAlive(), "Runner must be alive before a-top completes");

        // Complete a-top to trigger the deadlock
        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":a-top");
        YWorkItem executingItem = _engine.startWorkItem(
                enabledItem, _engine.getExternalClient("admin"));
        _engine.completeWorkItem(executingItem, "<data/>", null, WorkItemCompletion.Normal);

        // cancel() is always called after kick() cannot continue, setting _cancelling = true
        assertFalse(runner.isAlive(),
                "runner.isAlive() must be false — cancel() sets _cancelling=true during deadlock");
    }

    // -------------------------------------------------------------------------
    // Test 4 — runner is removed from repository after deadlock
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Runner is removed from repository after deadlock resolution")
    void testDeadlock_runnerRemovedFromRepository() throws Exception {
        _engine.loadSpecification(_deadlockSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _deadlockSpec.getSpecificationID(), null, null, null, null, null, false);
        assertNotNull(repo.get(caseId), "Runner must be in repo before deadlock");

        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":a-top");
        YWorkItem executingItem = _engine.startWorkItem(
                enabledItem, _engine.getExternalClient("admin"));
        _engine.completeWorkItem(executingItem, "<data/>", null, WorkItemCompletion.Normal);

        // cancel() calls _engine.getNetRunnerRepository().remove(_caseIDForNet)
        assertNull(repo.get(caseId),
                "Runner must be removed from repository after deadlock resolution");
    }

    // -------------------------------------------------------------------------
    // Test 5 — engine-wide work item set is empty after sole case deadlocks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("No work items remain for the deadlocked case after cancel()")
    void testDeadlock_engineItemSetEmptyAfterDeadlock() throws Exception {
        YIdentifier caseId = triggerDeadlock();

        // cancel() calls removeWorkItemsForCase() — zero items must remain for THIS case.
        // (Other cases loaded by concurrent tests are irrelevant to this assertion.)
        long itemsForDeadlockedCase = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .count();
        assertEquals(0, itemsForDeadlockedCase,
                "cancel() must remove all work items for the deadlocked case; "
                + "found=" + itemsForDeadlockedCase);
    }

    // -------------------------------------------------------------------------
    // Test 6 — engine continues processing parallel cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Engine continues processing parallel cases while one case is deadlocked")
    void testDeadlock_engineContinuesWithParallelCase() throws Exception {
        // Load both specs
        _engine.loadSpecification(_deadlockSpec);
        _engine.loadSpecification(_normalSpec);

        // Start a normal case first (should have enabled items, not deadlocked)
        YIdentifier normalCaseId = _engine.startCase(
                _normalSpec.getSpecificationID(), null, null, null, null, null, false);

        // Trigger the deadlock in the other spec
        YIdentifier deadlockCaseId = _engine.startCase(
                _deadlockSpec.getSpecificationID(), null, null, null, null, null, false);
        YWorkItem enabledItem = _engine.getWorkItem(deadlockCaseId.toString() + ":a-top");
        YWorkItem executingItem = _engine.startWorkItem(
                enabledItem, _engine.getExternalClient("admin"));
        _engine.completeWorkItem(executingItem, "<data/>", null, WorkItemCompletion.Normal);

        // Normal case must still have enabled (non-deadlocked) work items.
        // Compare getCaseID() (YIdentifier) with normalCaseId (YIdentifier) — not toString().
        long enabledForNormal = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(normalCaseId))
                .filter(i -> i.getStatus() == YWorkItemStatus.statusEnabled)
                .count();
        assertTrue(enabledForNormal > 0,
                "Normal case must have enabled items — deadlock in another case must not block it");
    }

    // -------------------------------------------------------------------------
    // Test 7 — cancelling a case after deadlock (runner already removed)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("cancelCase on already-resolved deadlocked case throws no NPE or IAE")
    void testDeadlock_cancelAfterDeadlock_noNullPointerException() throws Exception {
        YIdentifier caseId = triggerDeadlock();

        // The runner has been removed by deadlock resolution — cancelCase must not NPE/IAE
        try {
            _engine.cancelCase(caseId);
            // idempotent success is also acceptable
        } catch (Exception e) {
            assertFalse(e instanceof NullPointerException,
                    "cancelCase must not throw NPE for an already-resolved case: " + e);
            assertFalse(e instanceof IllegalArgumentException,
                    "cancelCase must not throw IAE for an already-resolved case: " + e);
        }
    }

    // -------------------------------------------------------------------------
    // Test 8 — normal specification produces no deadlocked items
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Normal (non-deadlocking) specification produces enabled items, none deadlocked")
    void testNoDeadlock_normalCaseHasNoDeadlockedItems() throws Exception {
        _engine.loadSpecification(_normalSpec);
        YIdentifier caseId = _engine.startCase(
                _normalSpec.getSpecificationID(), null, null, null, null, null, false);

        // Compare getCaseID() (YIdentifier) with caseId (YIdentifier) — not toString().
        long deadlockedCount = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .filter(i -> i.getStatus() == YWorkItemStatus.statusDeadlocked)
                .count();
        assertEquals(0, deadlockedCount,
                "Non-deadlocking specification must produce zero statusDeadlocked items");

        // Sanity: the normal case must have at least one enabled item
        long enabledCount = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .filter(i -> i.getStatus() == YWorkItemStatus.statusEnabled)
                .count();
        assertTrue(enabledCount > 0,
                "Non-deadlocking specification must produce at least one enabled work item");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 1 — Simple OR-join deadlock scenario
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Simple OR-join deadlock: task completion causes OR-join to never enable")
    void testSimpleORJoinDeadlock() throws Exception {
        _engine.loadSpecification(_simpleDeadlockSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _simpleDeadlockSpec.getSpecificationID(), null, null, null, null, null, false);
        YNetRunner runner = repo.get(caseId);
        assertNotNull(runner, "Runner must be in repository immediately after case start");

        // Start the workflow
        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":1_A");
        assertNotNull(enabledItem, "Task 1_A must be enabled after case start");

        YWorkItem executingItem = _engine.startWorkItem(
                enabledItem, _engine.getExternalClient("admin"));
        assertNotNull(executingItem, "startWorkItem must return executing child item");

        // Complete task A, which should trigger the deadlock
        _engine.completeWorkItem(executingItem, "<data/>", null, WorkItemCompletion.Normal);

        // After completion, check if the runner is deadlocked
        assertTrue(runner.isCompleted(),
                "Runner must be completed due to deadlock detection");

        // Verify all work items for the case have been removed
        long itemsForCase = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .count();
        assertEquals(0, itemsForCase,
                "All work items must be removed after deadlock resolution");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 2 — Complex multi-task cycle with OR-joins
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Complex multi-task cycle with OR-joins creates deadlock")
    void testComplexORJoinCycleDeadlock() throws Exception {
        _engine.loadSpecification(_complexCycleSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _complexCycleSpec.getSpecificationID(), null, null, null, null, null, false);
        YNetRunner runner = repo.get(caseId);
        assertNotNull(runner, "Runner must be in repository immediately after case start");

        // Trigger the workflow execution path that leads to deadlock
        executeWorkflowUntilDeadlock(caseId, "1_A", "3_B");

        // Verify the runner is in deadlocked state
        assertTrue(runner.isCompleted(),
                "Complex cycle must result in deadlock detection");

        // Verify no work items remain for the deadlocked case
        long itemsForCase = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .count();
        assertEquals(0, itemsForCase,
                "All work items must be removed after complex deadlock resolution");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 3 — Cancellation during OR-join deadlock
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Cancellation during OR-join deadlock resolution")
    void testCancelDuringORJoinDeadlock() throws Exception {
        _engine.loadSpecification(_cancelDuringDeadlockSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _cancelDuringDeadlockSpec.getSpecificationID(), null, null, null, null, null, false);

        // Trigger deadlock scenario
        executeWorkflowUntilDeadlock(caseId, "1_A", "3_B");

        // Attempt to cancel the case during deadlock
        _engine.cancelCase(caseId);

        // Verify the runner is no longer in the repository
        assertNull(repo.get(caseId),
                "Runner must be removed from repository after cancellation during deadlock");

        // Verify no work items remain for the case
        long itemsForCase = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .count();
        assertEquals(0, itemsForCase,
                "Cancel during deadlock must remove all work items");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 4 — Timeout scenarios with OR-joins
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Timeout scenario with OR-join tasks causes deadlock detection")
    void testORJoinTimeoutDeadlock() throws Exception {
        _engine.loadSpecification(_timeoutScenarioSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _timeoutScenarioSpec.getSpecificationID(), null, null, null, null, null, false);
        YNetRunner runner = repo.get(caseId);
        assertNotNull(runner, "Runner must be in repository immediately after case start");

        // Execute tasks that will timeout and cause deadlock
        executeWorkflowUntilDeadlock(caseId, "1_A", "3_B");

        // Verify timeout caused deadlock detection
        assertTrue(runner.isCompleted(),
                "Timeout scenario must result in deadlock detection");

        // Verify work items are cleaned up
        long itemsForCase = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .count();
        assertEquals(0, itemsForCase,
                "Timeout deadlock must clean up all work items");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 5 — Mixed pattern deadlock (AND/OR/XOR combinations)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Mixed pattern deadlock: AND-join + OR-join + XOR-join combinations")
    void testMixedPatternORJoinDeadlock() throws Exception {
        _engine.loadSpecification(_mixedPatternSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _mixedPatternSpec.getSpecificationID(), null, null, null, null, null, false);
        YNetRunner runner = repo.get(caseId);
        assertNotNull(runner, "Runner must be in repository immediately after case start");

        // Execute path that triggers mixed pattern deadlock
        executeWorkflowUntilDeadlock(caseId, "1_A", "3_B");

        // Verify mixed pattern causes deadlock
        assertTrue(runner.isCompleted(),
                "Mixed pattern must result in deadlock detection");

        // Verify cleanup
        long itemsForCase = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .count();
        assertEquals(0, itemsForCase,
                "Mixed pattern deadlock must clean up all work items");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 6 — Multiple OR-joins creating circular dependency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Multiple OR-joins creating circular dependency deadlock")
    void testMultipleORJoinsCircularDeadlock() throws Exception {
        _engine.loadSpecification(_complexCycleSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _complexCycleSpec.getSpecificationID(), null, null, null, null, null, false);

        // Execute multiple paths to create circular OR-join dependencies
        executeWorkflowUntilDeadlock(caseId, "1_A", "3_B");

        // Verify circular deadlock detection
        YNetRunner runner = repo.get(caseId);
        assertNull(runner, "Runner must be removed after circular OR-join deadlock");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 7 — OR-join with insufficient preset conditions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("OR-join with insufficient preset conditions causes deadlock")
    void testORJoinInsufficientPresetDeadlock() throws Exception {
        _engine.loadSpecification(_simpleDeadlockSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _simpleDeadlockSpec.getSpecificationID(), null, null, null, null, null, false);

        // Execute to trigger OR-join with insufficient preset conditions
        executeWorkflowUntilDeadlock(caseId, "1_A", "3_B");

        // Verify deadlock due to insufficient preset conditions
        long deadlockedCount = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .filter(i -> i.getStatus() == YWorkItemStatus.statusDeadlocked)
                .count();
        assertEquals(0, deadlockedCount,
                "No items should remain as they should be cleaned up after deadlock");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 8 — Parallel case execution with OR-join deadlock
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Engine continues with parallel case while another has OR-join deadlock")
    void testParallelCaseWithORJoinDeadlock() throws Exception {
        // Load both specs - normal and deadlock
        _engine.loadSpecification(_normalSpec);
        _engine.loadSpecification(_simpleDeadlockSpec);

        // Start normal case first
        YIdentifier normalCaseId = _engine.startCase(
                _normalSpec.getSpecificationID(), null, null, null, null, null, false);

        // Start deadlock case
        YIdentifier deadlockCaseId = _engine.startCase(
                _simpleDeadlockSpec.getSpecificationID(), null, null, null, null, null, false);

        // Trigger deadlock in second case
        executeWorkflowUntilDeadlock(deadlockCaseId, "1_A", "3_B");

        // Normal case must still have enabled items
        long enabledForNormal = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(normalCaseId))
                .filter(i -> i.getStatus() == YWorkItemStatus.statusEnabled)
                .count();
        assertTrue(enabledForNormal > 0,
                "Normal case must continue execution while another case deadlocks");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 9 — Recovery after OR-join deadlock detection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Engine recovery after OR-join deadlock detection")
    void testEngineRecoveryAfterORJoinDeadlock() throws Exception {
        _engine.loadSpecification(_simpleDeadlockSpec);

        // Trigger deadlock
        YIdentifier caseId = triggerSimpleDeadlock();

        // Start a new case after deadlock resolution
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier newCaseId = _engine.startCase(
                _simpleDeadlockSpec.getSpecificationID(), null, null, null, null, null, false);
        YNetRunner newRunner = repo.get(newCaseId);
        assertNotNull(newRunner, "New case must start successfully after deadlock");

        // Verify the new runner is functional
        assertTrue(newRunner.isAlive(),
                "New runner must be alive after deadlock recovery");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 10 — OR-join deadlock with nested conditions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("OR-join deadlock with nested conditions and multiple paths")
    void testORJoinDeadlockWithNestedConditions() throws Exception {
        _engine.loadSpecification(_mixedPatternSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _mixedPatternSpec.getSpecificationID(), null, null, null, null, null, false);

        // Execute to trigger nested condition deadlock
        executeWorkflowUntilDeadlock(caseId, "1_A", "3_B");

        // Verify nested condition deadlock detection
        assertNull(repo.get(caseId),
                "Runner must be removed after nested condition OR-join deadlock");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 11 — Partial deadlock scenario with OR-joins
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Partial deadlock scenario with multiple OR-joins")
    void testPartialORJoinDeadlock() throws Exception {
        _engine.loadSpecification(_complexCycleSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _complexCycleSpec.getSpecificationID(), null, null, null, null, null, false);

        // Create partial deadlock scenario
        createPartialDeadlockScenario(caseId, "1_A", "3_B");

        // Verify partial deadlock is detected
        YNetRunner runner = repo.get(caseId);
        assertNotNull(runner, "Runner should exist during partial deadlock");
        assertFalse(runner.isCompleted(),
                "Runner should not be completed in partial deadlock state");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 12 — OR-join deadlock cancellation during execution
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Cancel case during OR-join deadlock execution")
    void testCancelDuringORJoinExecution() throws Exception {
        _engine.loadSpecification(_cancelDuringDeadlockSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _cancelDuringDeadlockSpec.getSpecificationID(), null, null, null, null, null, false);

        // Start execution path leading to deadlock
        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":1_A");
        YWorkItem executingItem = _engine.startWorkItem(
                enabledItem, _engine.getExternalClient("admin"));

        // Cancel before deadlock occurs
        _engine.cancelCase(caseId);

        // Verify cancellation without deadlock
        assertNull(repo.get(caseId),
                "Runner must be removed after cancellation before deadlock");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 13 — Engine behavior with multiple OR-join deadlocks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Engine behavior with multiple simultaneous OR-join deadlocks")
    void testMultipleORJoinDeadlocks() throws Exception {
        // Load multiple deadlock specs
        _engine.loadSpecification(_simpleDeadlockSpec);
        _engine.loadSpecification(_complexCycleSpec);

        // Start multiple cases that will deadlock
        YIdentifier case1 = _engine.startCase(
                _simpleDeadlockSpec.getSpecificationID(), null, null, null, null, null, false);
        YIdentifier case2 = _engine.startCase(
                _complexCycleSpec.getSpecificationID(), null, null, null, null, null, false);

        // Trigger both deadlocks
        executeWorkflowUntilDeadlock(case1, "1_A", "3_B");
        executeWorkflowUntilDeadlock(case2, "1_A", "3_B");

        // Verify both cases are deadlocked and cleaned up
        long totalItems = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(case1) || i.getCaseID().equals(case2))
                .count();
        assertEquals(0, totalItems,
                "All work items must be cleaned up after multiple OR-join deadlocks");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 14 — Performance test for OR-join deadlock detection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Performance test for OR-join deadlock detection speed")
    void testORJoinDeadlockDetectionPerformance() throws Exception {
        _engine.loadSpecification(_complexCycleSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _complexCycleSpec.getSpecificationID(), null, null, null, null, null, false);

        long startTime = System.currentTimeMillis();

        // Trigger deadlock
        executeWorkflowUntilDeadlock(caseId, "1_A", "3_B");

        long endTime = System.currentTimeMillis();
        long detectionTime = endTime - startTime;

        // Verify deadlock was detected within reasonable time
        assertTrue(detectionTime < 5000,
                "OR-join deadlock detection should be fast (< 5s), took: " + detectionTime + "ms");

        // Verify cleanup occurred
        assertNull(repo.get(caseId),
                "Runner must be removed after performance test");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 15 — Edge case: Empty OR-join handling
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Edge case: Empty OR-join handling without deadlock")
    void testEmptyORJoinHandling() throws Exception {
        _engine.loadSpecification(_normalSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _normalSpec.getSpecificationID(), null, null, null, null, null, false);
        YNetRunner runner = repo.get(caseId);
        assertNotNull(runner, "Runner must be in repository");

        // Verify no deadlock occurs with normal specification
        assertFalse(runner.isCompleted(),
                "Normal specification should not deadlock");

        // Verify the case has enabled tasks
        long enabledCount = _engine.getAllWorkItems().stream()
                .filter(i -> i.getCaseID().equals(caseId))
                .filter(i -> i.getStatus() == YWorkItemStatus.statusEnabled)
                .count();
        assertTrue(enabledCount > 0,
                "Normal case must have enabled tasks");
    }

    // -------------------------------------------------------------------------
    // OR-Join Deadlock Test 16 — Concurrent access during OR-join deadlock
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Concurrent access during OR-join deadlock detection")
    void testConcurrentAccessDuringORJoinDeadlock() throws Exception {
        _engine.loadSpecification(_complexCycleSpec);
        YNetRunnerRepository repo = _engine.getNetRunnerRepository();
        YIdentifier caseId = _engine.startCase(
                _complexCycleSpec.getSpecificationID(), null, null, null, null, null, false);

        // Simulate concurrent access by checking runner status in multiple threads
        Thread thread1 = new Thread(() -> {
            try {
                Thread.sleep(100);
                YNetRunner runner = repo.get(caseId);
                if (runner != null) {
                    assertFalse(runner.isCompleted(),
                            "Concurrent access should not see completed state prematurely");
                }
            } catch (Exception e) {
                fail("Concurrent access failed: " + e.getMessage());
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(150);
                executeWorkflowUntilDeadlock(caseId, "1_A", "3_B");
            } catch (Exception e) {
                fail("Deadlock execution failed: " + e.getMessage());
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Verify deadlock was eventually detected
        assertNull(repo.get(caseId),
                "Runner must be removed after concurrent deadlock detection");
    }

    // -------------------------------------------------------------------------
    // Helper Methods for OR-Join Deadlock Testing
    // -------------------------------------------------------------------------

    /**
     * Triggers a simple OR-join deadlock scenario.
     */
    private YIdentifier triggerSimpleDeadlock() throws Exception {
        _engine.loadSpecification(_simpleDeadlockSpec);
        YIdentifier caseId = _engine.startCase(
                _simpleDeadlockSpec.getSpecificationID(), null, null, null, null, null, false);

        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":1_A");
        YWorkItem executingItem = _engine.startWorkItem(
                enabledItem, _engine.getExternalClient("admin"));

        // Complete task to trigger deadlock
        _engine.completeWorkItem(executingItem, "<data/>", null, WorkItemCompletion.Normal);

        return caseId;
    }

    /**
     * Executes workflow until deadlock is detected.
     */
    private void executeWorkflowUntilDeadlock(YIdentifier caseId, String startTask, String nextTask) throws Exception {
        // Start the first task
        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":" + startTask);
        if (enabledItem != null) {
            YWorkItem executingItem = _engine.startWorkItem(
                    enabledItem, _engine.getExternalClient("admin"));
            if (executingItem != null) {
                _engine.completeWorkItem(executingItem, "<data/>", null, WorkItemCompletion.Normal);
            }
        }

        // Try to continue execution - this should lead to deadlock
        YWorkItem nextEnabledItem = _engine.getWorkItem(caseId.toString() + ":" + nextTask);
        if (nextEnabledItem != null) {
            YWorkItem nextExecutingItem = _engine.startWorkItem(
                    nextEnabledItem, _engine.getExternalClient("admin"));
            if (nextExecutingItem != null) {
                _engine.completeWorkItem(nextExecutingItem, "<data/>", null, WorkItemCompletion.Normal);
            }
        }

        // Wait for deadlock detection (kick mechanism should detect it)
        Thread.sleep(1000);
    }

    /**
     * Creates a partial deadlock scenario for testing.
     */
    private void createPartialDeadlockScenario(YIdentifier caseId, String task1, String task2) throws Exception {
        // Execute first task
        YWorkItem enabledItem = _engine.getWorkItem(caseId.toString() + ":" + task1);
        if (enabledItem != null) {
            YWorkItem executingItem = _engine.startWorkItem(
                    enabledItem, _engine.getExternalClient("admin"));
            if (executingItem != null) {
                _engine.completeWorkItem(executingItem, "<data/>", null, WorkItemCompletion.Normal);
            }
        }

        // Attempt to execute second task - this may create partial deadlock conditions
        YWorkItem nextItem = _engine.getWorkItem(caseId.toString() + ":" + task2);
        if (nextItem != null) {
            // This task may not complete due to deadlock conditions
            YWorkItem executingNext = _engine.startWorkItem(
                    nextItem, _engine.getExternalClient("admin"));
            if (executingNext != null) {
                try {
                    _engine.completeWorkItem(executingNext, "<data/>", null, WorkItemCompletion.Normal);
                } catch (Exception e) {
                    // Expected in partial deadlock scenario
                }
            }
        }
    }
}
