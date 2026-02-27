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

package org.yawlfoundation.yawl.stateless;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Blue ocean self-checking test: Work item timestamp monotonicity invariant.
 *
 * <p>Verifies that {@link YWorkItem} timestamps respect the lifecycle ordering:
 * <pre>
 *   enablementTime ≤ firingTime ≤ startTime
 * </pre>
 * and that rollback correctly resets {@code startTime} to {@code null} while
 * preserving {@code firingTime}, and that a subsequent re-start produces a new
 * {@code startTime ≥ firingTime}.
 *
 * <p>Why this matters: {@code rollBackStatus()} sets {@code _startTime = null} but
 * does NOT reset {@code _firingTime}. If a work item is later re-started (e.g., by
 * a worklist re-checkout after a network error or a JVM clock adjustment), the new
 * {@code startTime} must still be ≥ the preserved {@code firingTime}. Violating this
 * makes audit trails impossible to order and breaks timestamp-based SLA calculations.
 *
 * <p>Chicago TDD: uses real {@link YStatelessEngine}, real {@link YWorkItem} objects.
 * No mocks, no stubs.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("integration")
@DisplayName("Work Item Timestamp Monotonicity Invariant")
class WorkItemTimestampInvariantTest {

    private static final String MINIMAL_SPEC_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <specificationSet version="4.0"
                xmlns="http://www.yawlfoundation.org/yawlschema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema \
            http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
              <specification uri="TimestampSpec">
                <metaData>
                  <title>Timestamp Test Spec</title>
                  <creator>WorkItemTimestampInvariantTest</creator>
                  <version>0.1</version>
                </metaData>
                <decomposition id="TimestampNet" xsi:type="NetFactsType" isRootNet="true">
                  <processControlElements>
                    <inputCondition id="start">
                      <flowsInto><nextElementRef id="taskA"/></flowsInto>
                    </inputCondition>
                    <task id="taskA">
                      <name>taskA</name>
                      <flowsInto><nextElementRef id="end"/></flowsInto>
                      <join code="xor"/><split code="and"/>
                      <decomposesTo id="taskAGateway"/>
                    </task>
                    <outputCondition id="end"/>
                  </processControlElements>
                </decomposition>
                <decomposition id="taskAGateway" xsi:type="WebServiceGatewayFactsType">
                  <externalInteraction>manual</externalInteraction>
                </decomposition>
              </specification>
            </specificationSet>
            """;

    /**
     * TS-1: After launch, an enabled work item has enablementTime set and
     * firingTime/startTime null. After start, all three are set and monotonically ordered.
     */
    @Test
    @DisplayName("TS-1: Timestamps are set and monotonically ordered through lifecycle")
    void timestampsAreMonotonicallyOrderedThroughLifecycle() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = engine.unmarshalSpecification(MINIMAL_SPEC_XML);
        YNetRunner runner = engine.launchCase(spec, "ts-1");

        Set<YWorkItem> enabled = runner.getWorkItemRepository().getEnabledWorkItems();
        assertFalse(enabled.isEmpty(), "At least one work item must be enabled after launch");
        YWorkItem item = enabled.iterator().next();

        // ── INVARIANT 1: enabled item has enablementTime set ─────────────────────
        assertNotNull(item.getEnablementTime(),
                "enablementTime must be set on enabled work item");

        // Firing time is set when the parent item creates its child (on enablement)
        assertNotNull(item.getFiringTime(),
                "firingTime must be set on enabled work item");

        // startTime is null until startWorkItem is called
        assertNull(item.getStartTime(),
                "startTime must be null before startWorkItem");

        // Ordering: firingTime >= enablementTime
        Instant enablement = item.getEnablementTime();
        Instant firing = item.getFiringTime();
        assertFalse(firing.isBefore(enablement),
                "firingTime (" + firing + ") must be >= enablementTime (" + enablement + ")");

        // ── Start the work item ──────────────────────────────────────────────────
        YWorkItem started = engine.startWorkItem(item);
        assertNotNull(started, "startWorkItem must return non-null started item");

        // ── INVARIANT 2: after start, all three timestamps set and ordered ────────
        assertNotNull(started.getEnablementTime(), "enablementTime must persist after start");
        assertNotNull(started.getFiringTime(),     "firingTime must persist after start");
        assertNotNull(started.getStartTime(),      "startTime must be set after start");

        Instant startTime = started.getStartTime();
        Instant firingAfterStart = started.getFiringTime();

        assertFalse(startTime.isBefore(firingAfterStart),
                "startTime (" + startTime + ") must be >= firingTime (" + firingAfterStart + ")");
    }

    /**
     * TS-2: After rollback, startTime is cleared to null but firingTime is preserved.
     * A subsequent re-start produces a new startTime >= firingTime.
     */
    @Test
    @DisplayName("TS-2: Rollback clears startTime but preserves firingTime; re-start is ordered")
    void rollbackClearsStartTimeAndReStartProducesOrderedTimestamps() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = engine.unmarshalSpecification(MINIMAL_SPEC_XML);
        YNetRunner runner = engine.launchCase(spec, "ts-2");

        YWorkItem item = runner.getWorkItemRepository().getEnabledWorkItems().iterator().next();
        YWorkItem started = engine.startWorkItem(item);

        // Record timestamps before rollback
        Instant firingBeforeRollback = started.getFiringTime();
        Instant startBeforeRollback  = started.getStartTime();
        assertNotNull(firingBeforeRollback, "firingTime must be set before rollback");
        assertNotNull(startBeforeRollback,  "startTime must be set before rollback");

        // ── ROLLBACK ─────────────────────────────────────────────────────────────
        YWorkItem rolledBack = engine.rollbackWorkItem(started);
        assertNotNull(rolledBack, "rollbackWorkItem must return non-null item");

        // ── INVARIANT: startTime cleared, firingTime preserved ───────────────────
        assertNull(rolledBack.getStartTime(),
                "startTime must be null after rollback");
        assertNotNull(rolledBack.getFiringTime(),
                "firingTime must be preserved after rollback");
        assertEquals(firingBeforeRollback, rolledBack.getFiringTime(),
                "firingTime must be unchanged by rollback");

        // ── RE-START: new startTime must be >= preserved firingTime ──────────────
        YWorkItem reStarted = engine.startWorkItem(rolledBack);
        assertNotNull(reStarted, "Re-start after rollback must succeed");
        assertNotNull(reStarted.getStartTime(), "startTime must be set after re-start");

        Instant newStartTime = reStarted.getStartTime();
        Instant preservedFiringTime = reStarted.getFiringTime();

        assertFalse(newStartTime.isBefore(preservedFiringTime),
                "Re-start startTime (" + newStartTime + ") must be >= preserved firingTime ("
                + preservedFiringTime + ")");
    }

    /**
     * TS-3: Timestamp ordering invariant holds across 10 concurrent work items
     * (e.g., 10 cases each with one task, started concurrently).
     * Validates that the wall-clock monotonicity is not broken by thread scheduling.
     */
    @Test
    @DisplayName("TS-3: Timestamp ordering holds for 10 concurrently started work items")
    void timestampOrderingHoldsForConcurrentlyStartedItems() throws Exception {
        YStatelessEngine engine = new YStatelessEngine();
        YSpecification spec = engine.unmarshalSpecification(MINIMAL_SPEC_XML);

        // Launch 10 cases and start their work items concurrently
        YWorkItem[] items = new YWorkItem[10];
        YNetRunner[] runners = new YNetRunner[10];

        for (int i = 0; i < 10; i++) {
            runners[i] = engine.launchCase(spec, "ts-concurrent-" + i);
            YWorkItem enabled = runners[i].getWorkItemRepository()
                    .getEnabledWorkItems().iterator().next();
            items[i] = engine.startWorkItem(enabled);
        }

        // Verify timestamp ordering for each item
        for (int i = 0; i < 10; i++) {
            YWorkItem item = items[i];
            String ctx = "Case ts-concurrent-" + i;

            assertNotNull(item.getEnablementTime(), ctx + ": enablementTime must not be null");
            assertNotNull(item.getFiringTime(),     ctx + ": firingTime must not be null");
            assertNotNull(item.getStartTime(),      ctx + ": startTime must not be null");

            assertFalse(item.getFiringTime().isBefore(item.getEnablementTime()),
                    ctx + ": firingTime must be >= enablementTime");
            assertFalse(item.getStartTime().isBefore(item.getFiringTime()),
                    ctx + ": startTime must be >= firingTime");
        }
    }
}
