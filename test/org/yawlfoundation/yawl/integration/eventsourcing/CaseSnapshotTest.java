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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CaseSnapshot}.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class CaseSnapshotTest {

    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_SPEC_ID = "OrderFulfillment:1.0";
    private static final Instant TEST_TIMESTAMP = Instant.parse("2026-02-17T10:00:00Z");
    private static final long TEST_SEQUENCE_NUMBER = 42;

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("constructCompleteSnapshot")
        void constructCompleteSnapshot() {
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "ENABLED", TEST_TIMESTAMP),
                "wi-2", new CaseStateView.WorkItemState("wi-2", "STARTED", TEST_TIMESTAMP.plusSeconds(30))
            );

            Map<String, String> payload = Map.of(
                "startedBy", "agent-order-service",
                "priority", "high",
                "department", "sales"
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                "RUNNING", workItems, payload);

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(TEST_SPEC_ID, snapshot.getSpecId());
            assertEquals(TEST_SEQUENCE_NUMBER, snapshot.getSequenceNumber());
            assertEquals(TEST_TIMESTAMP, snapshot.getSnapshotAt());
            assertEquals("RUNNING", snapshot.getStatus());
            assertEquals(2, snapshot.getActiveWorkItems().size());
            assertEquals("ENABLED", snapshot.getActiveWorkItems().get("wi-1").status());
            assertEquals(3, snapshot.getPayload().size());
            assertEquals("agent-order-service", snapshot.getPayload().get("startedBy"));
        }

        @Test
        @DisplayName("constructWithNullWorkItems")
        void constructWithNullWorkItems() {
            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                "COMPLETED", null, Map.of("finalStatus", "success"));

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(TEST_SPEC_ID, snapshot.getSpecId());
            assertEquals(TEST_SEQUENCE_NUMBER, snapshot.getSequenceNumber());
            assertEquals(TEST_TIMESTAMP, snapshot.getSnapshotAt());
            assertEquals("COMPLETED", snapshot.getStatus());
            assertTrue(snapshot.getActiveWorkItems().isEmpty());
            assertEquals(1, snapshot.getPayload().size());
        }

        @Test
        @DisplayName("constructWithNullPayload")
        void constructWithNullPayload() {
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "COMPLETED", TEST_TIMESTAMP)
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                "COMPLETED", workItems, null);

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(TEST_SPEC_ID, snapshot.getSpecId());
            assertEquals(TEST_SEQUENCE_NUMBER, snapshot.getSequenceNumber());
            assertEquals(TEST_TIMESTAMP, snapshot.getSnapshotAt());
            assertEquals("COMPLETED", snapshot.getStatus());
            assertEquals(1, snapshot.getActiveWorkItems().size());
            assertTrue(snapshot.getPayload().isEmpty());
        }

        @Test
        @DisplayName("constructWithNullCaseIdThrows")
        void constructWithNullCaseIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new CaseSnapshot(null, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                                "RUNNING", Map.of(), Map.of()));
        }

        @Test
        @DisplayName("constructWithNullSpecIdThrows")
        void constructWithNullSpecIdThrows() {
            assertThrows(NullPointerException.class, () ->
                new CaseSnapshot(TEST_CASE_ID, null, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                                "RUNNING", Map.of(), Map.of()));
        }

        @Test
        @DisplayName("constructWithNullSnapshotAtThrows")
        void constructWithNullSnapshotAtThrows() {
            assertThrows(NullPointerException.class, () ->
                new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, null,
                                "RUNNING", Map.of(), Map.of()));
        }

        @Test
        @DisplayName("constructWithNullStatusThrows")
        void constructWithNullStatusThrows() {
            assertThrows(NullPointerException.class, () ->
                new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                                null, Map.of(), Map.of()));
        }
    }

    @Nested
    @DisplayName("from - Factory Method")
    class FromMethodTest {

        @Test
        @DisplayName("fromCaseStateViewCreatesSnapshot")
        void fromCaseStateViewCreatesSnapshot() {
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "ENABLED", TEST_TIMESTAMP),
                "wi-2", new CaseStateView.WorkItemState("wi-2", "STARTED", TEST_TIMESTAMP.plusSeconds(30))
            );

            Map<String, String> payload = Map.of(
                "startedBy", "agent-order-service",
                "priority", "high"
            );

            CaseStateView stateView = new CaseStateView(
                TEST_CASE_ID, TEST_SPEC_ID, CaseStateView.CaseStatus.RUNNING,
                TEST_TIMESTAMP, workItems, payload);

            CaseSnapshot snapshot = CaseSnapshot.from(stateView, TEST_SEQUENCE_NUMBER);

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(TEST_SPEC_ID, snapshot.getSpecId());
            assertEquals(TEST_SEQUENCE_NUMBER, snapshot.getSequenceNumber());
            assertNotNull(snapshot.getSnapshotAt()); // Should be current time
            assertEquals("RUNNING", snapshot.getStatus());
            assertEquals(2, snapshot.getActiveWorkItems().size());
            assertEquals("ENABLED", snapshot.getActiveWorkItems().get("wi-1").status());
            assertEquals(2, snapshot.getPayload().size());
            assertEquals("agent-order-service", snapshot.getPayload().get("startedBy"));
            assertEquals("high", snapshot.getPayload().get("priority"));
        }

        @Test
        @DisplayName("fromCaseStateViewWithNullSpecIdUsesEmptyString")
        void fromCaseStateViewWithNullSpecIdUsesEmptyString() {
            CaseStateView stateView = new CaseStateView(
                TEST_CASE_ID, null, CaseStateView.CaseStatus.UNKNOWN,
                TEST_TIMESTAMP, Map.of(), Map.of());

            CaseSnapshot snapshot = CaseSnapshot.from(stateView, 0);

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals("", snapshot.getSpecId()); // Empty string instead of null
            assertEquals(0, snapshot.getSequenceNumber());
            assertEquals("UNKNOWN", snapshot.getStatus());
            assertTrue(snapshot.getActiveWorkItems().isEmpty());
            assertTrue(snapshot.getPayload().isEmpty());
        }

        @Test
        @DisplayName("fromCaseStateViewWithNullWorkItems")
        void fromCaseStateViewWithNullWorkItems() {
            CaseStateView stateView = new CaseStateView(
                TEST_CASE_ID, TEST_SPEC_ID, CaseStateView.CaseStatus.RUNNING,
                TEST_TIMESTAMP, null, Map.of());

            CaseSnapshot snapshot = CaseSnapshot.from(stateView, TEST_SEQUENCE_NUMBER);

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(TEST_SPEC_ID, snapshot.getSpecId());
            assertEquals(TEST_SEQUENCE_NUMBER, snapshot.getSequenceNumber());
            assertEquals("RUNNING", snapshot.getStatus());
            assertTrue(snapshot.getActiveWorkItems().isEmpty());
        }

        @Test
        @DisplayName("fromCaseStateViewWithNullPayload")
        void fromCaseStateViewWithNullPayload() {
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "COMPLETED", TEST_TIMESTAMP)
            );

            CaseStateView stateView = new CaseStateView(
                TEST_CASE_ID, TEST_SPEC_ID, CaseStateView.CaseStatus.COMPLETED,
                TEST_TIMESTAMP, workItems, null);

            CaseSnapshot snapshot = CaseSnapshot.from(stateView, TEST_SEQUENCE_NUMBER);

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(TEST_SPEC_ID, snapshot.getSpecId());
            assertEquals(TEST_SEQUENCE_NUMBER, snapshot.getSequenceNumber());
            assertEquals("COMPLETED", snapshot.getStatus());
            assertEquals(1, snapshot.getActiveWorkItems().size());
            assertTrue(snapshot.getPayload().isEmpty());
        }

        @Test
        @DisplayName("fromCaseStateViewThrowsOnNullStateView")
        void fromCaseStateViewThrowsOnNullStateView() {
            assertThrows(NullPointerException.class, () ->
                CaseSnapshot.from(null, TEST_SEQUENCE_NUMBER));
        }
    }

    @Nested
    @DisplayName("Accessors")
    class AccessorsTest {

        @Test
        @DisplayName("getCaseId")
        void getCaseId() {
            CaseSnapshot snapshot = createTestSnapshot();
            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
        }

        @Test
        @DisplayName("getSpecId")
        void getSpecId() {
            CaseSnapshot snapshot = createTestSnapshot();
            assertEquals(TEST_SPEC_ID, snapshot.getSpecId());
        }

        @Test
        @DisplayName("getSequenceNumber")
        void getSequenceNumber() {
            CaseSnapshot snapshot = createTestSnapshot();
            assertEquals(TEST_SEQUENCE_NUMBER, snapshot.getSequenceNumber());
        }

        @Test
        @DisplayName("getSnapshotAt")
        void getSnapshotAt() {
            CaseSnapshot snapshot = createTestSnapshot();
            assertEquals(TEST_TIMESTAMP, snapshot.getSnapshotAt());
        }

        @Test
        @DisplayName("getStatus")
        void getStatus() {
            CaseSnapshot snapshot = createTestSnapshot();
            assertEquals("RUNNING", snapshot.getStatus());
        }

        @Test
        @DisplayName("getActiveWorkItems")
        void getActiveWorkItems() {
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "ENABLED", TEST_TIMESTAMP),
                "wi-2", new CaseStateView.WorkItemState("wi-2", "STARTED", TEST_TIMESTAMP.plusSeconds(30))
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                "RUNNING", workItems, Map.of());

            Map<String, CaseStateView.WorkItemState> retrieved = snapshot.getActiveWorkItems();

            assertEquals(2, retrieved.size());
            assertEquals("ENABLED", retrieved.get("wi-1").status());
            assertEquals("STARTED", retrieved.get("wi-2").status());
            assertThrows(UnsupportedOperationException.class, () ->
                retrieved.put("wi-3", new CaseStateView.WorkItemState("wi-3", "COMPLETED", TEST_TIMESTAMP)));
        }

        @Test
        @DisplayName("getPayload")
        void getPayload() {
            Map<String, String> payload = Map.of(
                "startedBy", "agent-order-service",
                "priority", "high"
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                "RUNNING", Map.of(), payload);

            Map<String, String> retrieved = snapshot.getPayload();

            assertEquals(2, retrieved.size());
            assertEquals("agent-order-service", retrieved.get("startedBy"));
            assertEquals("high", retrieved.get("priority"));
            assertThrows(UnsupportedOperationException.class, () ->
                retrieved.put("newKey", "newValue"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("toStringWithCompleteData")
        void toStringWithCompleteData() {
            Map<String, CaseStateView.WorkItemState> workItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "ENABLED", TEST_TIMESTAMP)
            );

            Map<String, String> payload = Map.of("startedBy", "agent-order-service");

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                "RUNNING", workItems, payload);

            String str = snapshot.toString();
            assertTrue(str.contains("caseId='test-case-123'"));
            assertTrue(str.contains("seq=42"));
            assertTrue(str.contains("status=RUNNING"));
            assertTrue(str.contains("snapshotAt=" + TEST_TIMESTAMP));
        }

        @Test
        @DisplayName("toStringWithMinimalData")
        void toStringWithMinimalData() {
            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, 0, TEST_TIMESTAMP,
                "UNKNOWN", Map.of(), Map.of());

            String str = snapshot.toString();
            assertTrue(str.contains("caseId='test-case-123'"));
            assertTrue(str.contains("seq=0"));
            assertTrue(str.contains("status=UNKNOWN"));
            assertTrue(str.contains("snapshotAt=" + TEST_TIMESTAMP));
        }

        @Test
        @DisplayName("largeWorkItemsMapIsImmutable")
        void largeWorkItemsMapIsImmutable() {
            Map<String, CaseStateView.WorkItemState> workItems = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                workItems.put("wi-" + i, new CaseStateView.WorkItemState("wi-" + i, "ENABLED", TEST_TIMESTAMP));
            }

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                "RUNNING", workItems, Map.of());

            Map<String, CaseStateView.WorkItemState> retrieved = snapshot.getActiveWorkItems();

            try {
                retrieved.put("new-item", new CaseStateView.WorkItemState("new-item", "STARTED", TEST_TIMESTAMP));
                fail("Should throw UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // Expected
            }
        }

        @Test
        @DisplayName("largePayloadIsImmutable")
        void largePayloadIsImmutable() {
            Map<String, String> payload = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                payload.put("key" + i, "value" + i);
            }

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                "RUNNING", Map.of(), payload);

            Map<String, String> retrieved = snapshot.getPayload();

            try {
                retrieved.put("new-key", "new-value");
                fail("Should throw UnsupportedOperationException");
            } catch (UnsupportedOperationException e) {
                // Expected
            }
        }
    }

    @Nested
    @DisplayName("Snapshot Creation Scenarios")
    class SnapshotCreationScenariosTest {

        @Test
        @DisplayName("snapshotAfterCaseStarted")
        void snapshotAfterCaseStarted() {
            CaseStateView stateView = CaseStateView.empty(TEST_CASE_ID)
                .withStatus(CaseStateView.CaseStatus.RUNNING)
                .withSpecId(TEST_SPEC_ID)
                .withLastEventAt(TEST_TIMESTAMP)
                .withPayloadEntry("startedBy", "agent-order-service");

            CaseSnapshot snapshot = CaseSnapshot.from(stateView, 0);

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(TEST_SPEC_ID, snapshot.getSpecId());
            assertEquals(0, snapshot.getSequenceNumber());
            assertEquals("RUNNING", snapshot.getStatus());
            assertTrue(snapshot.getActiveWorkItems().isEmpty());
            assertEquals(1, snapshot.getPayload().size());
            assertEquals("agent-order-service", snapshot.getPayload().get("startedBy"));
        }

        @Test
        @DisplayName("snapshotWithActiveWorkItems")
        void snapshotWithActiveWorkItems() {
            CaseStateView stateView = CaseStateView.empty(TEST_CASE_ID)
                .withStatus(CaseStateView.CaseStatus.RUNNING)
                .withActiveWorkItem("review-order", "ENABLED", TEST_TIMESTAMP)
                .withActiveWorkItem("process-payment", "STARTED", TEST_TIMESTAMP.plusSeconds(30));

            CaseSnapshot snapshot = CaseSnapshot.from(stateView, 2);

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, stateView.getStatus());
            assertEquals(2, snapshot.getActiveWorkItems().size());
            assertEquals("ENABLED", snapshot.getActiveWorkItems().get("review-order").status());
            assertEquals("STARTED", snapshot.getActiveWorkItems().get("process-payment").status());
        }

        @Test
        @DisplayName("snapshotOfCompletedCase")
        void snapshotOfCompletedCase() {
            CaseStateView stateView = CaseStateView.empty(TEST_CASE_ID)
                .withStatus(CaseStateView.CaseStatus.COMPLETED)
                .withLastEventAt(TEST_TIMESTAMP)
                .withPayloadEntry("completedBy", "system")
                .withPayloadEntry("duration", "2h 30m");

            CaseSnapshot snapshot = CaseSnapshot.from(stateView, 5);

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, stateView.getStatus());
            assertTrue(snapshot.getActiveWorkItems().isEmpty());
            assertEquals(2, snapshot.getPayload().size());
            assertEquals("system", snapshot.getPayload().get("completedBy"));
        }

        @Test
        @DisplayName("snapshotOfCancelledCase")
        void snapshotOfCancelledCase() {
            CaseStateView stateView = CaseStateView.empty(TEST_CASE_ID)
                .withStatus(CaseStateView.CaseStatus.CANCELLED)
                .withLastEventAt(TEST_TIMESTAMP)
                .withPayloadEntry("cancelledBy", "user-123")
                .withPayloadEntry("reason", "timeout");

            CaseSnapshot snapshot = CaseSnapshot.from(stateView, 3);

            assertEquals(TEST_CASE_ID, snapshot.getCaseId());
            assertEquals(CaseStateView.CaseStatus.CANCELLED, stateView.getStatus());
            assertTrue(snapshot.getActiveWorkItems().isEmpty());
            assertEquals(2, snapshot.getPayload().size());
            assertEquals("user-123", snapshot.getPayload().get("cancelledBy"));
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTest {

        @Test
        @DisplayName("snapshotSequenceNumberCannotBeNegative")
        void snapshotSequenceNumberCannotBeNegative() {
            assertThrows(IllegalArgumentException.class, () ->
                new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, -1, TEST_TIMESTAMP,
                                "RUNNING", Map.of(), Map.of()));
        }

        @Test
        @DisplayName("snapshotSequenceNumberCanZero")
        void snapshotSequenceNumberCanZero() {
            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, 0, TEST_TIMESTAMP,
                "RUNNING", Map.of(), Map.of());

            assertEquals(0, snapshot.getSequenceNumber());
        }

        @Test
        @DisplayName("snapshotSequenceNumberCanBeLarge")
        void snapshotSequenceNumberCanBeLarge() {
            long largeSeq = Long.MAX_VALUE;
            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, largeSeq, TEST_TIMESTAMP,
                "RUNNING", Map.of(), Map.of());

            assertEquals(largeSeq, snapshot.getSequenceNumber());
        }

        @Test
        @DisplayName("statusIsCaseStatusNameString")
        void statusIsCaseStatusNameString() {
            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                "COMPLETED", Map.of(), Map.of());

            assertEquals("COMPLETED", snapshot.getStatus());
            assertThrows(IllegalArgumentException.class, () ->
                new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
                                "INVALID_STATUS", Map.of(), Map.of()));
        }
    }

    // Helper method to create test snapshots
    private CaseSnapshot createTestSnapshot() {
        Map<String, CaseStateView.WorkItemState> workItems = Map.of(
            "wi-1", new CaseStateView.WorkItemState("wi-1", "ENABLED", TEST_TIMESTAMP)
        );

        Map<String, String> payload = Map.of("startedBy", "agent-order-service");

        return new CaseSnapshot(
            TEST_CASE_ID, TEST_SPEC_ID, TEST_SEQUENCE_NUMBER, TEST_TIMESTAMP,
            "RUNNING", workItems, payload);
    }
}