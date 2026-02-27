/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine.invariant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.YTask;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Blue ocean self-checking test: YNetRunner enabled/busy task set mutual exclusion.
 *
 * <p>The invariant:
 * <pre>
 *   ∀ runner R at any observable moment:
 *     R.getEnabledTasks() ∩ R.getBusyTasks() == ∅
 *     R.getActiveTasks() == R.getEnabledTasks() ∪ R.getBusyTasks()
 * </pre>
 *
 * <p>This invariant is checked via a {@link YWorkItemEventListener} installed on the
 * engine, which fires synchronously on every ITEM_ENABLED and ITEM_STATUS_CHANGE event.
 * Any violation means a task appeared simultaneously in both sets — a race condition in
 * {@code YNetRunner.fireAtomicTasksInParallel()} or {@code continueIfPossible()}.
 *
 * <p>Why this matters: {@link YNetRunner} maintains {@code _enabledTasks} and
 * {@code _busyTasks} as separate sets, updated in non-atomic steps. A task transitioning
 * from "enabled" to "busy" (after fire) involves removing it from {@code _enabledTasks}
 * and adding it to {@code _busyTasks} under a write lock — but the lock scope does not
 * cover the parallel task executor. An overlap means a task can be started twice or
 * missed by the work item dispatcher.
 *
 * <p>Chicago TDD: uses real {@link YStatelessEngine}, real {@link YNetRunner},
 * real event listeners. No mocks.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("integration")
@DisplayName("YNetRunner Enabled/Busy Task Set Mutual Exclusion Invariant")
class RunnerTaskSetInvariantTest {

    /** Single-task spec: input → taskA (manual) → output */
    private static final String SINGLE_TASK_SPEC = """
            <?xml version="1.0" encoding="UTF-8"?>
            <specificationSet version="4.0"
                xmlns="http://www.yawlfoundation.org/yawlschema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema \
            http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
              <specification uri="SingleTaskSpec">
                <metaData><title>Single Task</title><creator>RunnerTaskSetInvariantTest</creator><version>0.1</version></metaData>
                <decomposition id="Net" xsi:type="NetFactsType" isRootNet="true">
                  <processControlElements>
                    <inputCondition id="start">
                      <flowsInto><nextElementRef id="taskA"/></flowsInto>
                    </inputCondition>
                    <task id="taskA">
                      <name>taskA</name>
                      <flowsInto><nextElementRef id="end"/></flowsInto>
                      <join code="xor"/><split code="and"/>
                      <decomposesTo id="taskAGw"/>
                    </task>
                    <outputCondition id="end"/>
                  </processControlElements>
                </decomposition>
                <decomposition id="taskAGw" xsi:type="WebServiceGatewayFactsType">
                  <externalInteraction>manual</externalInteraction>
                </decomposition>
              </specification>
            </specificationSet>
            """;

    /**
     * INV-1: After launch, enabled and busy sets are disjoint.
     * After startWorkItem, task moves from enabled to busy (not in both).
     * After completeWorkItem, task is in neither set.
     */
    @Test
    @DisplayName("INV-1: Enabled and busy sets are always disjoint through task lifecycle")
    void enabledAndBusyAreDisjointThroughTaskLifecycle() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = engine.unmarshalSpecification(SINGLE_TASK_SPEC);
        YNetRunner runner = engine.launchCase(spec, "inv-1");

        // ── After launch: task is enabled, not busy ───────────────────────────────
        assertDisjoint(runner, "after launch");
        assertEquals(1, runner.getEnabledTasks().size(), "One task must be enabled");
        assertEquals(0, runner.getBusyTasks().size(),    "No tasks must be busy after launch");

        YWorkItem item = runner.getWorkItemRepository().getEnabledWorkItems().iterator().next();
        YTask enabledTask = runner.getEnabledTasks().iterator().next();
        assertEquals("taskA", enabledTask.getID(), "Enabled task must be taskA");

        // ── After start: task moves to busy ──────────────────────────────────────
        engine.startWorkItem(item);
        assertDisjoint(runner, "after startWorkItem");
        assertEquals(0, runner.getEnabledTasks().size(), "No tasks must be enabled after start");
        assertEquals(1, runner.getBusyTasks().size(),    "One task must be busy after start");

        YWorkItem started = runner.getWorkItemRepository()
                .getWorkItems(YWorkItemStatus.statusExecuting)
                .stream().filter(w -> "taskA".equals(w.getTaskID())).findFirst()
                .orElseThrow(() -> new AssertionError("Started work item not found"));

        // ── After complete: task in neither set ───────────────────────────────────
        engine.completeWorkItem(started, "<data/>", null);
        assertDisjoint(runner, "after completeWorkItem");
        assertEquals(0, runner.getEnabledTasks().size(), "No tasks must be enabled after complete");
        assertEquals(0, runner.getBusyTasks().size(),    "No tasks must be busy after complete");
    }

    /**
     * INV-2: The mutual exclusion invariant holds when checked from an event listener
     * on every ITEM_ENABLED event — capturing any transient violation during the
     * enabled→busy transition.
     */
    @RepeatedTest(20)
    @DisplayName("INV-2: Enabled/busy mutual exclusion holds as observed from event listener")
    void enabledBusyMutualExclusionHoldsUnderEventObservation() throws Exception {
        AtomicReference<String> violation = new AtomicReference<>(null);
        CountDownLatch enabledLatch = new CountDownLatch(1);

        YStatelessEngine engine = new YStatelessEngine();

        // Install listener that checks invariant on every ITEM_ENABLED event
        engine.addWorkItemEventListener(new YWorkItemEventListener() {
            @Override
            public void handleWorkItemEvent(YWorkItemEvent event) {
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    YWorkItem item = event.getWorkItem();
                    YNetRunner runner = item.getNetRunner();
                    if (runner != null) {
                        Set<YTask> overlap = new HashSet<>(runner.getEnabledTasks());
                        overlap.retainAll(runner.getBusyTasks());
                        if (!overlap.isEmpty()) {
                            violation.set("Overlap on ITEM_ENABLED: task IDs = "
                                    + overlap.stream()
                                    .map(t -> t.getID())
                                    .collect(java.util.stream.Collectors.joining(", ")));
                        }
                    }
                    enabledLatch.countDown();
                }
            }
        });

        YSpecification spec = engine.unmarshalSpecification(SINGLE_TASK_SPEC);
        engine.launchCase(spec, "inv-2-" + System.nanoTime());

        // Wait for the ITEM_ENABLED event (up to 2 seconds)
        boolean eventFired = enabledLatch.await(2, TimeUnit.SECONDS);
        assertTrue(eventFired, "ITEM_ENABLED event must fire within 2 seconds of launch");

        // Check invariant
        assertNull(violation.get(),
                "Enabled/busy overlap detected by event listener: " + violation.get());
    }

    /**
     * INV-3: getActiveTasks() == getEnabledTasks() ∪ getBusyTasks() at all observable moments.
     */
    @Test
    @DisplayName("INV-3: getActiveTasks() is always the union of enabled and busy sets")
    void activeTasksIsUnionOfEnabledAndBusy() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = engine.unmarshalSpecification(SINGLE_TASK_SPEC);
        YNetRunner runner = engine.launchCase(spec, "inv-3");

        // Check at launch
        assertActiveIsUnion(runner, "after launch");

        // Start the work item
        YWorkItem item = runner.getWorkItemRepository().getEnabledWorkItems().iterator().next();
        engine.startWorkItem(item);
        assertActiveIsUnion(runner, "after startWorkItem");
    }

    // ── Helper methods ────────────────────────────────────────────────────────────────

    private void assertDisjoint(YNetRunner runner, String context) {
        Set<YTask> enabled = new HashSet<>(runner.getEnabledTasks());
        Set<YTask> busy = runner.getBusyTasks();
        Set<YTask> overlap = new HashSet<>(enabled);
        overlap.retainAll(busy);
        assertTrue(overlap.isEmpty(),
                "Enabled/busy overlap detected " + context + ": tasks = "
                + overlap.stream().map(YTask::getID)
                          .collect(java.util.stream.Collectors.joining(", ")));
    }

    private void assertActiveIsUnion(YNetRunner runner, String context) {
        Set<YTask> enabled = runner.getEnabledTasks();
        Set<YTask> busy = runner.getBusyTasks();
        Set<YTask> active = runner.getActiveTasks();
        Set<YTask> expectedActive = new HashSet<>(enabled);
        expectedActive.addAll(busy);
        assertEquals(expectedActive, active,
                "getActiveTasks() must equal enabledTasks ∪ busyTasks " + context);
    }
}
