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

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Blue ocean self-checking test: Case marshal/unmarshal round-trip idempotency.
 *
 * <p>Verifies that {@link YStatelessEngine#marshalCase(YNetRunner)} followed by
 * {@link YStatelessEngine#restoreCase(String)} on a second engine preserves the complete
 * case state: the set of enabled work items, their task IDs, and the ability to drive
 * the restored case to successful completion.
 *
 * <p>The invariant tested:
 * <pre>
 *   ∀ runner R at mid-flow state T:
 *     R' = restoreCase(marshalCase(R)) on a fresh engine →
 *     taskIDs(R'.enabledWorkItems) == taskIDs(R.enabledWorkItems)
 *     drive(R') to completion succeeds without exception
 * </pre>
 *
 * <p>Why this matters: {@link org.yawlfoundation.yawl.stateless.monitor.YCaseExporter}
 * marshals spec + all runners + work items into XML. The importer must reconstruct
 * parent-child work item relationships and YIdentifier hierarchies. If any step is
 * wrong, the restored case either has missing/extra enabled items, or fails to complete.
 *
 * <p>Chicago TDD: uses real {@code YStatelessEngine} instances and real YAWL
 * specifications. No mocks.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("integration")
@DisplayName("Case Marshal/Unmarshal Round-Trip Invariant")
class RoundTripInvariantTest {

    /** Minimal YAWL specification: input → task1 (manual, WebServiceGateway) → output */
    private static final String MINIMAL_SPEC_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <specificationSet version="4.0"
                xmlns="http://www.yawlfoundation.org/yawlschema"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema \
            http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
              <specification uri="MinimalSpec">
                <metaData>
                  <title>Minimal Spec</title>
                  <creator>RoundTripInvariantTest</creator>
                  <version>0.3</version>
                </metaData>
                <decomposition id="MinimalNet" xsi:type="NetFactsType" isRootNet="true">
                  <processControlElements>
                    <inputCondition id="start">
                      <flowsInto><nextElementRef id="task1"/></flowsInto>
                    </inputCondition>
                    <task id="task1">
                      <name>task1</name>
                      <flowsInto><nextElementRef id="end"/></flowsInto>
                      <join code="xor"/><split code="and"/>
                      <decomposesTo id="task1Gateway"/>
                    </task>
                    <outputCondition id="end"/>
                  </processControlElements>
                </decomposition>
                <decomposition id="task1Gateway" xsi:type="WebServiceGatewayFactsType">
                  <externalInteraction>manual</externalInteraction>
                </decomposition>
              </specification>
            </specificationSet>
            """;

    /**
     * RT-1: Marshal a freshly-launched case and restore it on a second engine.
     * The restored case must have the same enabled task IDs and complete cleanly.
     */
    @Test
    @DisplayName("RT-1: Marshal mid-flow case and restore on second engine preserves enabled tasks")
    void marshalAndRestorePreservesEnabledWorkItems() throws Exception {
        // ── Engine 1: launch case and capture state ──────────────────────────────
        YStatelessEngine engine1 = new YStatelessEngine();
        YSpecification spec = engine1.unmarshalSpecification(MINIMAL_SPEC_XML);
        YNetRunner runner = engine1.launchCase(spec, "rt-1");

        assertTrue(runner.isAlive(), "Runner must be alive after launch");

        Set<String> enabledBefore = runner.getWorkItemRepository()
                .getEnabledWorkItems().stream()
                .map(YWorkItem::getTaskID)
                .collect(Collectors.toSet());
        assertFalse(enabledBefore.isEmpty(),
                "At least one task must be enabled after launch");

        // Marshal case XML from engine1 (does NOT unload the runner)
        String caseXML = engine1.marshalCase(runner);
        assertNotNull(caseXML, "Marshal must produce non-null XML");
        assertFalse(caseXML.isBlank(), "Marshal must produce non-empty XML");

        // ── Engine 2: restore case from XML ──────────────────────────────────────
        YStatelessEngine engine2 = new YStatelessEngine();
        YNetRunner restored = engine2.restoreCase(caseXML);

        assertNotNull(restored, "Restored runner must not be null");
        assertTrue(restored.isAlive(), "Restored runner must be alive");

        // ── INVARIANT: enabled task IDs are identical ─────────────────────────────
        Set<String> enabledAfter = restored.getWorkItemRepository()
                .getEnabledWorkItems().stream()
                .map(YWorkItem::getTaskID)
                .collect(Collectors.toSet());

        assertEquals(enabledBefore, enabledAfter,
                "Enabled task IDs must survive marshal/restore round-trip");

        // ── INVARIANT: restored case can be driven to completion ──────────────────
        for (YWorkItem item : restored.getWorkItemRepository().getEnabledWorkItems()) {
            YWorkItem started = engine2.startWorkItem(item);
            assertNotNull(started, "startWorkItem must return non-null started item");
            engine2.completeWorkItem(started, "<data/>", null);
        }
        assertTrue(restored.isCompleted(),
                "Restored case must complete cleanly after driving all tasks");
        assertFalse(restored.isAlive(),
                "Completed case must not still be alive");
    }

    /**
     * RT-2: Case ID is preserved through round-trip — restored case ID matches original.
     */
    @Test
    @DisplayName("RT-2: Case ID is preserved through marshal/restore round-trip")
    void caseIdIsPreservedThroughRoundTrip() throws Exception {
        YStatelessEngine engine1 = new YStatelessEngine();
        YSpecification spec = engine1.unmarshalSpecification(MINIMAL_SPEC_XML);
        YNetRunner runner = engine1.launchCase(spec, "rt-id-check");

        String originalCaseId = runner.getCaseID().toString();
        String caseXML = engine1.marshalCase(runner);

        YStatelessEngine engine2 = new YStatelessEngine();
        YNetRunner restored = engine2.restoreCase(caseXML);

        assertEquals(originalCaseId, restored.getCaseID().toString(),
                "Case ID must be preserved through marshal/restore round-trip");
    }

    /**
     * RT-3: Three independent cases marshalled and restored on the same second engine
     * maintain distinct state — no cross-contamination between restored runners.
     */
    @Test
    @DisplayName("RT-3: Multiple restored cases maintain independent state")
    void multipleRestoredCasesAreIndependent() throws Exception {
        YStatelessEngine engine1 = new YStatelessEngine();
        YSpecification spec = engine1.unmarshalSpecification(MINIMAL_SPEC_XML);

        YNetRunner r1 = engine1.launchCase(spec, "rt-multi-1");
        YNetRunner r2 = engine1.launchCase(spec, "rt-multi-2");
        YNetRunner r3 = engine1.launchCase(spec, "rt-multi-3");

        String xml1 = engine1.marshalCase(r1);
        String xml2 = engine1.marshalCase(r2);
        String xml3 = engine1.marshalCase(r3);

        // Restore all three on a single fresh engine
        YStatelessEngine engine2 = new YStatelessEngine();
        YNetRunner restored1 = engine2.restoreCase(xml1);
        YNetRunner restored2 = engine2.restoreCase(xml2);
        YNetRunner restored3 = engine2.restoreCase(xml3);

        // Each restored runner has exactly one enabled task
        assertEquals(1, restored1.getWorkItemRepository().getEnabledWorkItems().size(),
                "Restored case rt-multi-1 must have exactly one enabled task");
        assertEquals(1, restored2.getWorkItemRepository().getEnabledWorkItems().size(),
                "Restored case rt-multi-2 must have exactly one enabled task");
        assertEquals(1, restored3.getWorkItemRepository().getEnabledWorkItems().size(),
                "Restored case rt-multi-3 must have exactly one enabled task");

        // Case IDs are distinct
        Set<String> ids = Set.of(
                restored1.getCaseID().toString(),
                restored2.getCaseID().toString(),
                restored3.getCaseID().toString());
        assertEquals(3, ids.size(), "Restored cases must have distinct case IDs");
    }
}
