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

package org.yawlfoundation.yawl.integration.eventsourcing;

import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link EventReplayer}.
 * Uses real H2 in-memory database instead of mocks.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class EventReplayerTest {

    private DataSource dataSource;
    private WorkflowEventStore eventStore;
    private EventReplayer eventReplayer;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = EventSourcingTestFixture.createDataSource();
        EventSourcingTestFixture.createSchema(dataSource);
        eventStore = new WorkflowEventStore(dataSource);
        eventReplayer = new EventReplayer(eventStore);
    }

    @AfterEach
    void tearDown() throws SQLException {
        EventSourcingTestFixture.dropSchema(dataSource);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("constructWithEventStore")
        void constructWithEventStore() throws SQLException {
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
            WorkflowEventStore store = new WorkflowEventStore(ds);
            EventReplayer replayer = new EventReplayer(store);
            assertNotNull(replayer);
        }

        @Test
        @DisplayName("constructWithNullEventStoreThrows")
        void constructWithNullEventStoreThrows() {
            assertThrows(NullPointerException.class, () -> new EventReplayer(null));
        }
    }

    @Nested
    @DisplayName("Full Replay")
    class FullReplayTest {

        @Test
        @DisplayName("replayEmptyCase")
        void replayEmptyCase() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();

            CaseStateView state = eventReplayer.replay(caseId);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.UNKNOWN, state.getStatus());
            assertNull(state.getSpecId());
            assertTrue(state.getActiveWorkItems().isEmpty());
            assertTrue(state.getPayload().isEmpty());
        }

        @Test
        @DisplayName("replayCaseStartedEvent")
        void replayCaseStartedEvent() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent caseStarted = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                Map.of("startedBy", "agent-order-service", "priority", "high"));

            eventStore.append(caseStarted, 0);

            CaseStateView state = eventReplayer.replay(caseId);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
            assertEquals(EventSourcingTestFixture.BASE_TIMESTAMP, state.getLastEventAt());
            assertEquals(1, state.getPayload().size());
            assertEquals("agent-order-service", state.getPayload().get("startedBy"));
        }

        @Test
        @DisplayName("replayCaseCompletedEvent")
        void replayCaseCompletedEvent() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            List<WorkflowEvent> events = List.of(
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                    Map.of("startedBy", "agent-order-service")),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, caseId, null,
                    Map.of("completedBy", "system"))
            );

            eventStore.append(events.get(0), 0);
            eventStore.append(events.get(1), 1);

            CaseStateView state = eventReplayer.replay(caseId);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
            assertEquals(2, state.getPayload().size());
            assertEquals("agent-order-service", state.getPayload().get("startedBy"));
            assertEquals("system", state.getPayload().get("completedBy"));
        }

        @Test
        @DisplayName("replayCaseCancelledEvent")
        void replayCaseCancelledEvent() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            List<WorkflowEvent> events = List.of(
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                    Map.of("startedBy", "agent-order-service")),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_CANCELLED, caseId, null,
                    Map.of("cancelledBy", "user-123", "reason", "timeout"))
            );

            eventStore.append(events.get(0), 0);
            eventStore.append(events.get(1), 1);

            CaseStateView state = eventReplayer.replay(caseId);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.CANCELLED, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
            assertEquals(2, state.getPayload().size());
            assertEquals("user-123", state.getPayload().get("cancelledBy"));
            assertEquals("timeout", state.getPayload().get("reason"));
        }

        @Test
        @DisplayName("replayCaseSuspendedEvent")
        void replayCaseSuspendedEvent() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent caseStarted = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null, Map.of());
            WorkflowEvent caseSuspended = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_SUSPENDED, caseId, null, Map.of("reason", "manual"));

            eventStore.append(caseStarted, 0);
            eventStore.append(caseSuspended, 1);

            CaseStateView state = eventReplayer.replay(caseId);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.SUSPENDED, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
        }

        @Test
        @DisplayName("replayCaseResumedEvent")
        void replayCaseResumedEvent() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            List<WorkflowEvent> events = List.of(
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_STARTED, caseId, null, Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_SUSPENDED, caseId, null, Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_RESUMED, caseId, null, Map.of())
            );

            eventStore.append(events.get(0), 0);
            eventStore.append(events.get(1), 1);
            eventStore.append(events.get(2), 2);

            CaseStateView state = eventReplayer.replay(caseId);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
        }

        @Test
        @DisplayName("replayWorkitemEvents")
        void replayWorkitemEvents() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            List<WorkflowEvent> events = List.of(
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_STARTED, caseId, null, Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "wi-1", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_STARTED, caseId, "wi-1", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_COMPLETED, caseId, "wi-1", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "wi-2", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_FAILED, caseId, "wi-2", Map.of("error", "timeout"))
            );

            for (int i = 0; i < events.size(); i++) {
                eventStore.append(events.get(i), i);
            }

            CaseStateView state = eventReplayer.replay(caseId);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
            assertEquals(1, state.getActiveWorkItems().size());
            assertTrue(state.getActiveWorkItems().containsKey("wi-2"));
            assertEquals("FAILED", state.getActiveWorkItems().get("wi-2").status());
            assertEquals(1, state.getPayload().size());
            assertEquals("timeout", state.getPayload().get("error"));
        }

        @Test
        @DisplayName("replayComplexWorkflowScenario")
        void replayComplexWorkflowScenario() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            List<WorkflowEvent> events = List.of(
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                    Map.of("startedBy", "agent-order-service", "specId", EventSourcingTestFixture.TEST_SPEC_ID)),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "review-order", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_STARTED, caseId, "review-order", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_COMPLETED, caseId, "review-order", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "process-payment", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_FAILED, caseId, "process-payment", Map.of("error", "card_declined")),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "process-payment", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_COMPLETED, caseId, "process-payment", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, caseId, null, Map.of("completedBy", "system"))
            );

            for (int i = 0; i < events.size(); i++) {
                eventStore.append(events.get(i), i);
            }

            CaseStateView state = eventReplayer.replay(caseId);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
            assertTrue(state.getActiveWorkItems().isEmpty());
            assertEquals(3, state.getPayload().size());
            assertEquals("card_declined", state.getPayload().get("error"));
        }
    }

    @Nested
    @DisplayName("Temporal Replay")
    class TemporalReplayTest {

        @Test
        @DisplayName("replayAsOfBeforeCaseStarted")
        void replayAsOfBeforeCaseStarted() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            Instant queryTime = EventSourcingTestFixture.BASE_TIMESTAMP.minusSeconds(60);

            CaseStateView state = eventReplayer.replayAsOf(caseId, queryTime);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.UNKNOWN, state.getStatus());
            assertNull(state.getSpecId());
            assertTrue(state.getActiveWorkItems().isEmpty());
            assertTrue(state.getPayload().isEmpty());
        }

        @Test
        @DisplayName("replayAsOfAfterAllEvents")
        void replayAsOfAfterAllEvents() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            List<WorkflowEvent> events = List.of(
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_STARTED, caseId, null, Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, caseId, null, Map.of())
            );

            eventStore.append(events.get(0), 0);
            eventStore.append(events.get(1), 1);

            CaseStateView state = eventReplayer.replayAsOf(caseId, EventSourcingTestFixture.BASE_TIMESTAMP_PLUS_60);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
        }

        @Test
        @DisplayName("replayAsOfThrowsOnBlankCaseId")
        void replayAsOfThrowsOnBlankCaseId() {
            assertThrows(IllegalArgumentException.class, () ->
                eventReplayer.replayAsOf("", EventSourcingTestFixture.BASE_TIMESTAMP));
        }

        @Test
        @DisplayName("replayAsOfThrowsOnNullAsOf")
        void replayAsOfThrowsOnNullAsOf() {
            assertThrows(IllegalArgumentException.class, () ->
                eventReplayer.replayAsOf("case-id", null));
        }
    }

    @Nested
    @DisplayName("Snapshot-based Replay")
    class SnapshotBasedReplayTest {

        @Test
        @DisplayName("replayFromEmptySnapshot")
        void replayFromEmptySnapshot() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            CaseSnapshot snapshot = new CaseSnapshot(
                caseId, EventSourcingTestFixture.TEST_SPEC_ID, 0,
                EventSourcingTestFixture.BASE_TIMESTAMP, "UNKNOWN", Map.of(), Map.of());

            List<WorkflowEvent> deltaEvents = List.of(
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_STARTED, caseId, null, Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "wi-1", Map.of())
            );

            CaseStateView state = eventReplayer.replayFrom(snapshot, deltaEvents);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
            assertEquals(1, state.getActiveWorkItems().size());
            assertEquals(1, state.getPayload().size());
        }

        @Test
        @DisplayName("replayFromComplexSnapshot")
        void replayFromComplexSnapshot() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            Map<String, CaseStateView.WorkItemState> snapshotWorkItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "STARTED", EventSourcingTestFixture.BASE_TIMESTAMP),
                "wi-2", new CaseStateView.WorkItemState("wi-2", "COMPLETED", EventSourcingTestFixture.BASE_TIMESTAMP)
            );

            Map<String, String> snapshotPayload = Map.of(
                "startedBy", "agent-order-service",
                "specId", EventSourcingTestFixture.TEST_SPEC_ID
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                caseId, EventSourcingTestFixture.TEST_SPEC_ID, 2,
                EventSourcingTestFixture.BASE_TIMESTAMP, "RUNNING", snapshotWorkItems, snapshotPayload);

            List<WorkflowEvent> deltaEvents = List.of(
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_SUSPENDED, caseId, "wi-1", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "wi-3", Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_SUSPENDED, caseId, null, Map.of())
            );

            CaseStateView state = eventReplayer.replayFrom(snapshot, deltaEvents);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.SUSPENDED, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
            assertEquals(2, state.getActiveWorkItems().size());
            assertEquals(3, state.getPayload().size());
        }

        @Test
        @DisplayName("replayFromSnapshotWithNoDeltaEvents")
        void replayFromSnapshotWithNoDeltaEvents() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            Map<String, CaseStateView.WorkItemState> snapshotWorkItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "STARTED", EventSourcingTestFixture.BASE_TIMESTAMP)
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                caseId, EventSourcingTestFixture.TEST_SPEC_ID, 1,
                EventSourcingTestFixture.BASE_TIMESTAMP, "RUNNING", snapshotWorkItems, Map.of());

            CaseStateView state = eventReplayer.replayFrom(snapshot, List.of());

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(EventSourcingTestFixture.TEST_SPEC_ID, state.getSpecId());
            assertEquals(1, state.getActiveWorkItems().size());
            assertEquals("STARTED", state.getActiveWorkItems().get("wi-1").status());
        }

        @Test
        @DisplayName("replayFromSnapshotThrowsOnNullSnapshot")
        void replayFromSnapshotThrowsOnNullSnapshot() {
            assertThrows(NullPointerException.class, () ->
                eventReplayer.replayFrom(null, List.of()));
        }

        @Test
        @DisplayName("replayFromSnapshotThrowsOnNullDeltaEvents")
        void replayFromSnapshotThrowsOnNullDeltaEvents() {
            CaseSnapshot snapshot = new CaseSnapshot(
                "case-id", EventSourcingTestFixture.TEST_SPEC_ID, 0,
                EventSourcingTestFixture.BASE_TIMESTAMP, "UNKNOWN", Map.of(), Map.of());
            assertThrows(NullPointerException.class, () ->
                eventReplayer.replayFrom(snapshot, null));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTest {

        @Test
        @DisplayName("replayThrowsOnBlankCaseId")
        void replayThrowsOnBlankCaseId() {
            assertThrows(IllegalArgumentException.class, () -> eventReplayer.replay(""));
        }

        @Test
        @DisplayName("replayThrowsOnNullCaseId")
        void replayThrowsOnNullCaseId() {
            assertThrows(IllegalArgumentException.class, () -> eventReplayer.replay(null));
        }
    }

    @Nested
    @DisplayName("Event Replay Logic")
    class EventReplayLogicTest {

        @Test
        @DisplayName("eventWithNullWorkItemIdIgnoresWorkItemUpdates")
        void eventWithNullWorkItemIdIgnoresWorkItemUpdates() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            List<WorkflowEvent> events = List.of(
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.CASE_STARTED, caseId, null, Map.of()),
                EventSourcingTestFixture.createTestEvent(WorkflowEvent.EventType.WORKITEM_STARTED, caseId, null, Map.of())
            );

            eventStore.append(events.get(0), 0);
            eventStore.append(events.get(1), 1);

            CaseStateView state = eventReplayer.replay(caseId);

            assertEquals(caseId, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertTrue(state.getActiveWorkItems().isEmpty());
        }
    }
}
