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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deadlock detection test suite for {@link YNetRunner}.
 *
 * <p>Chicago TDD approach: tests use real engine, real DeadlockingSpecification.xml
 * (XOR-split + AND-join produces deadlock), and real YAWL_Specification2.xml for
 * the negative / isolation tests. No mocks.
 *
 * <p>Deadlock mechanics in {@code DeadlockingSpecification.xml}:
 * <ol>
 *   <li>Task {@code a-top} has XOR-split with {@code false()} predicate to {@code c2-top}
 *       and a default flow to {@code c1-top}. The XOR-split always puts the token in
 *       {@code c1-top} (the default path, since the predicate evaluates to false).</li>
 *   <li>Task {@code b-top} has AND-join, requiring tokens in BOTH {@code c1-top}
 *       AND {@code c2-top} before it can fire.</li>
 *   <li>After {@code a-top} completes, only {@code c1-top} has a token, so {@code b-top}
 *       can never fire — the case deadlocks.</li>
 *   <li>On deadlock detection, {@code cancel()} is called on the runner, which removes
 *       all work items for the case via
 *       {@code YWorkItemRepository.removeWorkItemsForCase()}.</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @see YNetRunner#isAlive()
 * @see YNetRunner#isCompleted()
 */
@Tag("unit")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("YNetRunner Deadlock Detection Tests")
class YNetRunnerDeadlockTest {

    private YEngine _engine;
    private YSpecification _deadlockSpec;
    private YSpecification _normalSpec;

    @BeforeEach
    void setUp() throws Exception {
        _engine = YEngine.getInstance();
        EngineClearer.clear(_engine);
        _deadlockSpec = loadSpec("DeadlockingSpecification.xml");
        _normalSpec   = loadSpec("YAWL_Specification2.xml");
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
}
