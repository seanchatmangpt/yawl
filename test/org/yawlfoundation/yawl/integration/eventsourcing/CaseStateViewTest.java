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
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.eventsourcing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CaseStateView}.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class CaseStateViewTest {

    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_SPEC_ID = "OrderFulfillment:1.0";
    private static final Instant TEST_TIMESTAMP = Instant.parse("2026-02-17T10:00:00Z");

    private CaseStateView emptyView;
    private CaseStateView runningView;
    private CaseStateView completedView;

    @BeforeEach
    void setUp() {
        emptyView = CaseStateView.empty(TEST_CASE_ID);
        runningView = emptyView.withStatus(CaseStateView.CaseStatus.RUNNING)
                              .withSpecId(TEST_SPEC_ID)
                              .withLastEventAt(TEST_TIMESTAMP)
                              .withPayloadEntry("startedBy", "agent-order-service");

        completedView = runningView.withStatus(CaseStateView.CaseStatus.COMPLETED)
                                  .withLastEventAt(TEST_TIMESTAMP.plusSeconds(3600))
                                  .withPayloadEntry("completedAt", "2026-02-17T11:00:00Z");
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodsTest {

        @Test
        @DisplayName("createEmptyCaseStateView")
        void createEmptyCaseStateView() {
            CaseStateView view = CaseStateView.empty(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, view.getCaseId());
            assertNull(view.getSpecId());
            assertEquals(CaseStateView.CaseStatus.UNKNOWN, view.getStatus());
            assertNull(view.getLastEventAt());
            assertTrue(view.getActiveWorkItems().isEmpty());
            assertTrue(view.getPayload().isEmpty());
        }

        @Test
        @DisplayName("createCaseStateViewFromSnapshot")
        void createCaseStateViewFromSnapshot() {
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "workitem-1", new CaseStateView.WorkItemState("workitem-1", "ENABLED", TEST_TIMESTAMP),
                "workitem-2", new CaseStateView.WorkItemState("workitem-2", "STARTED", TEST_TIMESTAMP)
            );

            Map<String, String> payload = Map.of(
                "startedBy", "agent-order-service",
                "department", "sales"
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, 2, TEST_TIMESTAMP, "RUNNING", workItems, payload);

            CaseStateView view = CaseStateView.fromSnapshot(snapshot);

            assertEquals(TEST_CASE_ID, view.getCaseId());
            assertEquals(TEST_SPEC_ID, view.getSpecId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, view.getStatus());
            assertEquals(TEST_TIMESTAMP, view.getLastEventAt());
            assertEquals(2, view.getActiveWorkItems().size());
            assertEquals("ENABLED", view.getActiveWorkItems().get("workitem-1").status());
            assertEquals("STARTED", view.getActiveWorkItems().get("workitem-2").status());
            assertEquals(2, view.getPayload().size());
            assertEquals("agent-order-service", view.getPayload().get("startedBy"));
            assertEquals("sales", view.getPayload().get("department"));
        }

        @Test
        @DisplayName("createCaseStateViewFromSnapshotWithNullWorkItems")
        void createCaseStateViewFromSnapshotWithNullWorkItems() {
            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, 0, TEST_TIMESTAMP, "UNKNOWN", null, null);

            CaseStateView view = CaseStateView.fromSnapshot(snapshot);

            assertEquals(TEST_CASE_ID, view.getCaseId());
            assertEquals(TEST_SPEC_ID, view.getSpecId());
            assertEquals(CaseStateView.CaseStatus.UNKNOWN, view.getStatus());
            assertEquals(TEST_TIMESTAMP, view.getLastEventAt());
            assertTrue(view.getActiveWorkItems().isEmpty());
            assertTrue(view.getPayload().isEmpty());
        }

        @Test
        @DisplayName("createCaseStateViewFromSnapshotThrowsOnNullSnapshot")
        void createCaseStateViewFromSnapshotThrowsOnNullSnapshot() {
            assertThrows(NullPointerException.class, () ->
                CaseStateView.fromSnapshot(null));
        }
    }

    @Nested
    @DisplayName("Accessors")
    class AccessorsTest {

        @Test
        @DisplayName("getCaseId")
        void getCaseId() {
            assertEquals(TEST_CASE_ID, emptyView.getCaseId());
        }

        @Test
        @DisplayName("getSpecId")
        void getSpecId() {
            assertNull(emptyView.getSpecId());
            assertEquals(TEST_SPEC_ID, runningView.getSpecId());
        }

        @Test
        @DisplayName("getStatus")
        void getStatus() {
            assertEquals(CaseStateView.CaseStatus.UNKNOWN, emptyView.getStatus());
            assertEquals(CaseStateView.CaseStatus.RUNNING, runningView.getStatus());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, completedView.getStatus());
        }

        @Test
        @DisplayName("getLastEventAt")
        void getLastEventAt() {
            assertNull(emptyView.getLastEventAt());
            assertEquals(TEST_TIMESTAMP, runningView.getLastEventAt());
            assertEquals(TEST_TIMESTAMP.plusSeconds(3600), completedView.getLastEventAt());
        }

        @Test
        @DisplayName("getActiveWorkItems")
        void getActiveWorkItems() {
            assertTrue(emptyView.getActiveWorkItems().isEmpty());
            assertTrue(runningView.getActiveWorkItems().isEmpty());
            assertTrue(completedView.getActiveWorkItems().isEmpty());

            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "ENABLED", TEST_TIMESTAMP),
                "wi-2", new CaseStateView.WorkItemState("wi-2", "STARTED", TEST_TIMESTAMP)
            );

            CaseStateView view = emptyView.withActiveWorkItem("wi-1", "ENABLED", TEST_TIMESTAMP)
                                       .withActiveWorkItem("wi-2", "STARTED", TEST_TIMESTAMP);

            assertEquals(2, view.getActiveWorkItems().size());
            assertEquals("ENABLED", view.getActiveWorkItems().get("wi-1").status());
            assertEquals("STARTED", view.getActiveWorkItems().get("wi-2").status());
            assertThrows(UnsupportedOperationException.class, () ->
                view.getActiveWorkItems().put("wi-3", new CaseStateView.WorkItemState("wi-3", "COMPLETED", TEST_TIMESTAMP)));
        }

        @Test
        @DisplayName("getPayload")
        void getPayload() {
            assertTrue(emptyView.getPayload().isEmpty());
            assertEquals(1, runningView.getPayload().size());
            assertEquals(2, completedView.getPayload().size());

            assertEquals("agent-order-service", runningView.getPayload().get("startedBy"));
            assertEquals("OrderFulfillment:1.0", completedView.getPayload().get("specId"));
            assertThrows(UnsupportedOperationException.class, () ->
                completedView.getPayload().put("newKey", "newValue"));
        }
    }

    @Nested
    @DisplayName("Immutable Update Methods")
    class ImmutableUpdateMethodsTest {

        @Test
        @DisplayName("withStatus")
        void withStatus() {
            CaseStateView view = emptyView.withStatus(CaseStateView.CaseStatus.RUNNING);

            assertNotSame(emptyView, view);
            assertEquals(CaseStateView.CaseStatus.RUNNING, view.getStatus());
            assertEquals(emptyView.getCaseId(), view.getCaseId());
            assertEquals(emptyView.getSpecId(), view.getSpecId());
            assertEquals(emptyView.getLastEventAt(), view.getLastEventAt());
            assertEquals(emptyView.getActiveWorkItems(), view.getActiveWorkItems());
            assertEquals(emptyView.getPayload(), view.getPayload());
        }

        @Test
        @DisplayName("withSpecId")
        void withSpecId() {
            CaseStateView view = emptyView.withSpecId(TEST_SPEC_ID);

            assertNotSame(emptyView, view);
            assertEquals(TEST_SPEC_ID, view.getSpecId());
            assertEquals(emptyView.getStatus(), view.getStatus());
        }

        @Test
        @DisplayName("withLastEventAt")
        void withLastEventAt() {
            CaseStateView view = emptyView.withLastEventAt(TEST_TIMESTAMP);

            assertNotSame(emptyView, view);
            assertEquals(TEST_TIMESTAMP, view.getLastEventAt());
            assertNotSame(emptyView.getLastEventAt(), view.getLastEventAt());
        }

        @Test
        @DisplayName("withPayloadEntry")
        void withPayloadEntry() {
            CaseStateView view = emptyView.withPayloadEntry("key1", "value1");

            assertNotSame(emptyView, view);
            assertEquals(1, view.getPayload().size());
            assertEquals("value1", view.getPayload().get("key1"));

            CaseStateView view2 = view.withPayloadEntry("key2", "value2");
            assertEquals(2, view2.getPayload().size());
            assertEquals("value1", view2.getPayload().get("key1"));
            assertEquals("value2", view2.getPayload().get("key2"));
        }

        @Test
        @DisplayName("withPayloadEntryWithNullKey")
        void withPayloadEntryWithNullKey() {
            CaseStateView view = emptyView.withPayloadEntry(null, "value");
            assertSame(emptyView, view);
            assertTrue(view.getPayload().isEmpty());
        }

        @Test
        @DisplayName("withPayloadEntryWithBlankKey")
        void withPayloadEntryWithBlankKey() {
            CaseStateView view = emptyView.withPayloadEntry("", "value");
            assertSame(emptyView, view);
            assertTrue(view.getPayload().isEmpty());
        }

        @Test
        @DisplayName("withPayloadEntryWithNullValue")
        void withPayloadEntryWithNullValue() {
            CaseStateView view = emptyView.withPayloadEntry("key", null);
            assertEquals(1, view.getPayload().size());
            assertEquals("null", view.getPayload().get("key"));
        }

        @Test
        @DisplayName("withActiveWorkItem")
        void withActiveWorkItem() {
            CaseStateView view = emptyView.withActiveWorkItem("wi-1", "ENABLED", TEST_TIMESTAMP);

            assertEquals(1, view.getActiveWorkItems().size());
            assertEquals("ENABLED", view.getActiveWorkItems().get("wi-1").status());
            assertEquals(TEST_TIMESTAMP, view.getActiveWorkItems().get("wi-1").stateAt());

            CaseStateView view2 = view.withActiveWorkItem("wi-1", "STARTED", TEST_TIMESTAMP.plusSeconds(60));
            assertEquals(1, view2.getActiveWorkItems().size());
            assertEquals("STARTED", view2.getActiveWorkItems().get("wi-1").status());
            assertEquals(TEST_TIMESTAMP.plusSeconds(60), view2.getActiveWorkItems().get("wi-1").stateAt());
        }

        @Test
        @DisplayName("withActiveWorkItemWithNullWorkItemId")
        void withActiveWorkItemWithNullWorkItemId() {
            CaseStateView view = emptyView.withActiveWorkItem(null, "ENABLED", TEST_TIMESTAMP);
            assertSame(emptyView, view);
        }

        @Test
        @DisplayName("withActiveWorkItemWithBlankWorkItemId")
        void withActiveWorkItemWithBlankWorkItemId() {
            CaseStateView view = emptyView.withActiveWorkItem("", "ENABLED", TEST_TIMESTAMP);
            assertSame(emptyView, view);
        }

        @Test
        @DisplayName("withoutWorkItem")
        void withoutWorkItem() {
            CaseStateView baseView = emptyView.withActiveWorkItem("wi-1", "ENABLED", TEST_TIMESTAMP)
                                           .withActiveWorkItem("wi-2", "STARTED", TEST_TIMESTAMP);
            assertEquals(2, baseView.getActiveWorkItems().size());

            CaseStateView view = baseView.withoutWorkItem("wi-1");

            assertEquals(1, view.getActiveWorkItems().size());
            assertFalse(view.getActiveWorkItems().containsKey("wi-1"));
            assertTrue(view.getActiveWorkItems().containsKey("wi-2"));
        }

        @Test
        @DisplayName("withoutWorkItemWithNullWorkItemId")
        void withoutWorkItemWithNullWorkItemId() {
            CaseStateView view = runningView.withoutWorkItem(null);
            assertSame(runningView, view);
        }

        @Test
        @DisplayName("withoutWorkItemWithNonExistentWorkItemId")
        void withoutWorkItemWithNonExistentWorkItemId() {
            CaseStateView view = runningView.withoutWorkItem("non-existent");
            assertSame(runningView, view);
        }

        @Test
        @DisplayName("withoutWorkItemRemovesLastWorkItem")
        void withoutWorkItemRemovesLastWorkItem() {
            CaseStateView baseView = emptyView.withActiveWorkItem("wi-1", "ENABLED", TEST_TIMESTAMP);
            assertEquals(1, baseView.getActiveWorkItems().size());

            CaseStateView view = baseView.withoutWorkItem("wi-1");
            assertTrue(view.getActiveWorkItems().isEmpty());
        }
    }

    @Nested
    @DisplayName("WorkItemState Record")
    class WorkItemStateTest {

        @Test
        @DisplayName("createWorkItemState")
        void createWorkItemState() {
            CaseStateView.WorkItemState state = new CaseStateView.WorkItemState(
                "wi-123", "STARTED", TEST_TIMESTAMP);

            assertEquals("wi-123", state.workItemId());
            assertEquals("STARTED", state.status());
            assertEquals(TEST_TIMESTAMP, state.stateAt());
        }

        @Test
        @DisplayName("workItemStateEquals")
        void workItemStateEquals() {
            CaseStateView.WorkItemState state1 = new CaseStateView.WorkItemState(
                "wi-123", "STARTED", TEST_TIMESTAMP);
            CaseStateView.WorkItemState state2 = new CaseStateView.WorkItemState(
                "wi-123", "STARTED", TEST_TIMESTAMP);
            CaseStateView.WorkItemState state3 = new CaseStateView.WorkItemState(
                "wi-123", "COMPLETED", TEST_TIMESTAMP);

            assertEquals(state1, state2);
            assertNotEquals(state1, state3);
        }

        @Test
        @DisplayName("workItemStateHashCode")
        void workItemStateHashCode() {
            CaseStateView.WorkItemState state1 = new CaseStateView.WorkItemState(
                "wi-123", "STARTED", TEST_TIMESTAMP);
            CaseStateView.WorkItemState state2 = new CaseStateView.WorkItemState(
                "wi-123", "STARTED", TEST_TIMESTAMP);

            assertEquals(state1.hashCode(), state2.hashCode());
        }

        @Test
        @DisplayName("workItemStateToString")
        void workItemStateToString() {
            CaseStateView.WorkItemState state = new CaseStateView.WorkItemState(
                "wi-123", "STARTED", TEST_TIMESTAMP);

            String str = state.toString();
            assertTrue(str.contains("workItemId=wi-123"));
            assertTrue(str.contains("status=STARTED"));
            assertTrue(str.contains("stateAt=" + TEST_TIMESTAMP));
        }
    }

    @Nested
    @DisplayName("CaseStatus Enum")
    class CaseStatusTest {

        @Test
        @DisplayName("allCaseStatusValues")
        void allCaseStatusValues() {
            CaseStateView.CaseStatus[] values = CaseStateView.CaseStatus.values();
            assertEquals(6, values.length);

            assertTrue(List.of(values).contains(CaseStateView.CaseStatus.UNKNOWN));
            assertTrue(List.of(values).contains(CaseStateView.CaseStatus.RUNNING));
            assertTrue(List.of(values).contains(CaseStateView.CaseStatus.SUSPENDED));
            assertTrue(List.of(values).contains(CaseStateView.CaseStatus.COMPLETED));
            assertTrue(List.of(values).contains(CaseStateView.CaseStatus.CANCELLED));
        }

        @Test
        @DisplayName("caseStatusValueOf")
        void caseStatusValueOf() {
            assertEquals(CaseStateView.CaseStatus.RUNNING,
                        CaseStateView.CaseStatus.valueOf("RUNNING"));
            assertEquals(CaseStateView.CaseStatus.COMPLETED,
                        CaseStateView.CaseStatus.valueOf("COMPLETED"));
            assertThrows(IllegalArgumentException.class, () ->
                CaseStateView.CaseStatus.valueOf("INVALID"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("toStringWithMinimalData")
        void toStringWithMinimalData() {
            String str = emptyView.toString();
            assertTrue(str.contains("caseId='test-case-123'"));
            assertTrue(str.contains("status=UNKNOWN"));
            assertTrue(str.contains("activeWorkItems=0"));
        }

        @Test
        @DisplayName("toStringWithCompleteData")
        void toStringWithCompleteData() {
            String str = completedView.toString();
            assertTrue(str.contains("caseId='test-case-123'"));
            assertTrue(str.contains("specId='OrderFulfillment:1.0'"));
            assertTrue(str.contains("status=COMPLETED"));
            assertTrue(str.contains("activeWorkItems=0"));
            assertTrue(str.contains("lastEventAt="));
        }

        @Test
        @DisplayName("largePayloadIsImmutable")
        void largePayloadIsImmutable() {
            Map<String, String> largePayload = new java.util.HashMap<>();
            for (int i = 0; i < 100; i++) {
                largePayload.put("key" + i, "value" + i);
            }

            CaseStateView view = emptyView.withPayloadEntry("special", "value");

            try {
                view.getPayload().put("test", "fail");
                fail("Should throw UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // Expected
            }
        }

        @Test
        @DisplayName("manyWorkItemsAreImmutable")
        void manyWorkItemsAreImmutable() {
            CaseStateView baseView = emptyView;

            for (int i = 0; i < 50; i++) {
                baseView = baseView.withActiveWorkItem("wi-" + i, "ENABLED", TEST_TIMESTAMP);
            }

            assertEquals(50, baseView.getActiveWorkItems().size());

            try {
                baseView.getActiveWorkItems().put("new", "fail");
                fail("Should throw UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // Expected
            }
        }
    }

    @Nested
    @DisplayName("Chaining Operations")
    class ChainingOperationsTest {

        @Test
        @DisplayName("chainMultipleUpdates")
        void chainMultipleUpdates() {
            CaseStateView view = CaseStateView.empty(TEST_CASE_ID)
                .withStatus(CaseStateView.CaseStatus.RUNNING)
                .withSpecId(TEST_SPEC_ID)
                .withLastEventAt(TEST_TIMESTAMP)
                .withPayloadEntry("startedBy", "agent-order-service")
                .withActiveWorkItem("wi-1", "ENABLED", TEST_TIMESTAMP)
                .withActiveWorkItem("wi-2", "STARTED", TEST_TIMESTAMP)
                .withoutWorkItem("wi-1")
                .withPayloadEntry("completedAt", "2026-02-17T11:00:00Z");

            assertEquals(TEST_CASE_ID, view.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, view.getStatus());
            assertEquals(TEST_SPEC_ID, view.getSpecId());
            assertEquals(TEST_TIMESTAMP, view.getLastEventAt());
            assertEquals(1, view.getActiveWorkItems().size());
            assertEquals("STARTED", view.getActiveWorkItems().get("wi-2").status());
            assertEquals(2, view.getPayload().size());
            assertEquals("agent-order-service", view.getPayload().get("startedBy"));
            assertEquals("2026-02-17T11:00:00Z", view.getPayload().get("completedAt"));
        }

        @Test
        @DisplayName("chainWithSameWorkItemMultipleTimes")
        void chainWithSameWorkItemMultipleTimes() {
            CaseStateView view = CaseStateView.empty(TEST_CASE_ID)
                .withActiveWorkItem("wi-1", "ENABLED", TEST_TIMESTAMP)
                .withActiveWorkItem("wi-1", "STARTED", TEST_TIMESTAMP.plusSeconds(30))
                .withActiveWorkItem("wi-1", "COMPLETED", TEST_TIMESTAMP.plusSeconds(60))
                .withoutWorkItem("wi-1");

            assertTrue(view.getActiveWorkItems().isEmpty());
        }

        @Test
        @DisplayName("chainNoOpsResultInSameInstance")
        void chainNoOpsResultInSameInstance() {
            CaseStateView view1 = CaseStateView.empty(TEST_CASE_ID);
            CaseStateView view2 = view1
                .withPayloadEntry(null, "value")
                .withActiveWorkItem(null, "ENABLED", TEST_TIMESTAMP)
                .withActiveWorkItem("", "ENABLED", TEST_TIMESTAMP)
                .withoutWorkItem(null)
                .withoutWorkItem("non-existent");

            assertSame(view1, view2);
        }
    }
}