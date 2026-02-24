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

import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EventReplayer}.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@ExtendWith(MockitoExtension.class)
class EventReplayerTest {

    @Mock
    private WorkflowEventStore mockEventStore;

    private EventReplayer eventReplayer;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_SPEC_ID = "OrderFulfillment:1.0";
    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-02-17T10:00:00Z");

    @BeforeEach
    void setUp() {
        eventReplayer = new EventReplayer(mockEventStore);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("constructWithEventStore")
        void constructWithEventStore() {
            WorkflowEventStore store = mock(WorkflowEventStore.class);
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
            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(List.of());

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.UNKNOWN, state.getStatus());
            assertNull(state.getSpecId());
            assertTrue(state.getActiveWorkItems().isEmpty());
            assertTrue(state.getPayload().isEmpty());
        }

        @Test
        @DisplayName("replayCaseStartedEvent")
        void replayCaseStartedEvent() throws Exception {
            WorkflowEvent caseStarted = createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null,
                Map.of("startedBy", "agent-order-service", "priority", "high"));

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(List.of(caseStarted));

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(BASE_TIMESTAMP, state.getLastEventAt());
            assertEquals(1, state.getPayload().size());
            assertEquals("agent-order-service", state.getPayload().get("startedBy"));
        }

        @Test
        @DisplayName("replayCaseCompletedEvent")
        void replayCaseCompletedEvent() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null,
                               Map.of("startedBy", "agent-order-service")),
                createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null,
                               Map.of("completedBy", "system"))
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(BASE_TIMESTAMP.plusSeconds(60), state.getLastEventAt());
            assertEquals(2, state.getPayload().size());
            assertEquals("agent-order-service", state.getPayload().get("startedBy"));
            assertEquals("system", state.getPayload().get("completedBy"));
        }

        @Test
        @DisplayName("replayCaseCancelledEvent")
        void replayCaseCancelledEvent() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null,
                               Map.of("startedBy", "agent-order-service")),
                createTestEvent(WorkflowEvent.EventType.CASE_CANCELLED, TEST_CASE_ID, null,
                               Map.of("cancelledBy", "user-123", "reason", "timeout"))
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.CANCELLED, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(2, state.getPayload().size());
            assertEquals("user-123", state.getPayload().get("cancelledBy"));
            assertEquals("timeout", state.getPayload().get("reason"));
        }

        @Test
        @DisplayName("replayCaseSuspendedEvent")
        void replayCaseSuspendedEvent() throws Exception {
            WorkflowEvent caseStarted = createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of());
            WorkflowEvent caseSuspended = createTestEvent(
                WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, Map.of("reason", "manual"));

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(List.of(caseStarted, caseSuspended));

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.SUSPENDED, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(BASE_TIMESTAMP.plusSeconds(30), state.getLastEventAt());
        }

        @Test
        @DisplayName("replayCaseResumedEvent")
        void replayCaseResumedEvent() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.CASE_RESUMED, TEST_CASE_ID, null, Map.of())
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
        }

        @Test
        @DisplayName("replayWorkitemEvents")
        void replayWorkitemEvents() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "wi-1", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_STARTED, TEST_CASE_ID, "wi-1", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_COMPLETED, TEST_CASE_ID, "wi-1", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "wi-2", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_FAILED, TEST_CASE_ID, "wi-2", Map.of("error", "timeout"))
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(1, state.getActiveWorkItems().size());
            assertTrue(state.getActiveWorkItems().containsKey("wi-2"));
            assertEquals("FAILED", state.getActiveWorkItems().get("wi-2").status());
            assertEquals(3, state.getPayload().size());
            assertEquals("timeout", state.getPayload().get("error"));
        }

        @Test
        @DisplayName("replayWorkitemSuspendedEvent")
        void replayWorkitemSuspendedEvent() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "wi-1", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_SUSPENDED, TEST_CASE_ID, "wi-1", Map.of("reason", "manual"))
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(1, state.getActiveWorkItems().size());
            assertEquals("SUSPENDED", state.getActiveWorkItems().get("wi-1").status());
        }

        @Test
        @DisplayName("replayWorkitemCancelledEvent")
        void replayWorkitemCancelledEvent() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "wi-1", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_CANCELLED, TEST_CASE_ID, "wi-1", Map.of("cancelledBy", "user-123"))
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertTrue(state.getActiveWorkItems().isEmpty());
        }

        @Test
        @DisplayName("replaySpecEventsIgnored")
        void replaySpecEventsIgnored() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.SPEC_LOADED, TEST_SPEC_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.SPEC_UNLOADED, TEST_SPEC_ID, null, Map.of())
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(1, state.getPayload().size());
        }

        @Test
        @DisplayName("replayCoordinationEventsUpdateTimestamp")
        void replayCoordinationEventsUpdateTimestamp() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.CONFLICT_DETECTED, TEST_CASE_ID, null, Map.of("itemId", "wi-1")),
                createTestEvent(WorkflowEvent.EventType.HANDOFF_INITIATED, TEST_CASE_ID, null, Map.of("targetAgent", "agent-2")),
                createTestEvent(WorkflowEvent.EventType.AGENT_DECISION_MADE, TEST_CASE_ID, null, Map.of("decision", "approve")),
                createTestEvent(WorkflowEvent.EventType.CONFLICT_RESOLVED, TEST_CASE_ID, null, Map.of())
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(BASE_TIMESTAMP.plusSeconds(4 * 30), state.getLastEventAt());
            assertEquals(1, state.getPayload().size());
        }

        @Test
        @DisplayName("replayComplexWorkflowScenario")
        void replayComplexWorkflowScenario() throws Exception {
            List<WorkflowEvent> events = List.of(
                // Case lifecycle
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null,
                               Map.of("startedBy", "agent-order-service", "specId", TEST_SPEC_ID)),
                // First workitem
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "review-order", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_STARTED, TEST_CASE_ID, "review-order", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_SUSPENDED, TEST_CASE_ID, "review-order", Map.of("reason", "pending")),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_RESUMED, TEST_CASE_ID, "review-order", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_COMPLETED, TEST_CASE_ID, "review-order", Map.of()),
                // Second workitem
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "process-payment", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_STARTED, TEST_CASE_ID, "process-payment", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_FAILED, TEST_CASE_ID, "process-payment", Map.of("error", "card_declined")),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "process-payment", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_COMPLETED, TEST_CASE_ID, "process-payment", Map.of()),
                // Coordination event
                createTestEvent(WorkflowEvent.EventType.HANDOFF_COMPLETED, TEST_CASE_ID, null, Map.of("target", "fulfillment")),
                // Case completion
                createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, Map.of("completedBy", "system"))
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            CaseStateView state = eventReplayer.replay(TEST_CASE_ID);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertTrue(state.getActiveWorkItems().isEmpty());
            assertEquals(4, state.getPayload().size()); // startedBy, specId, error, completedBy
            assertEquals("card_declined", state.getPayload().get("error"));
        }
    }

    @Nested
    @DisplayName("Temporal Replay")
    class TemporalReplayTest {

        @Test
        @DisplayName("replayAsOfBeforeCaseStarted")
        void replayAsOfBeforeCaseStarted() throws Exception {
            when(mockEventStore.loadEventsAsOf(TEST_CASE_ID, BASE_TIMESTAMP.minusSeconds(60)))
                .thenReturn(List.of());

            CaseStateView state = eventReplayer.replayAsOf(TEST_CASE_ID, BASE_TIMESTAMP.minusSeconds(60));

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.UNKNOWN, state.getStatus());
            assertNull(state.getSpecId());
            assertTrue(state.getActiveWorkItems().isEmpty());
            assertTrue(state.getPayload().isEmpty());
        }

        @Test
        @DisplayName("replayAsOfMiddleOfEvents")
        void replayAsOfMiddleOfEvents() throws Exception {
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(45);

            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "wi-1", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_STARTED, TEST_CASE_ID, "wi-1", Map.of())
            );

            when(mockEventStore.loadEventsAsOf(TEST_CASE_ID, queryTime))
                .thenReturn(events);

            CaseStateView state = eventReplayer.replayAsOf(TEST_CASE_ID, queryTime);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(1, state.getActiveWorkItems().size());
            assertEquals("STARTED", state.getActiveWorkItems().get("wi-1").status());
            assertEquals(BASE_TIMESTAMP.plusSeconds(30), state.getLastEventAt());
        }

        @Test
        @DisplayName("replayAsOfAfterAllEvents")
        void replayAsOfAfterAllEvents() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, Map.of())
            );

            when(mockEventStore.loadEventsAsOf(TEST_CASE_ID, BASE_TIMESTAMP.plusSeconds(120)))
                .thenReturn(events);

            CaseStateView state = eventReplayer.replayAsOf(TEST_CASE_ID, BASE_TIMESTAMP.plusSeconds(120));

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(BASE_TIMESTAMP.plusSeconds(60), state.getLastEventAt());
        }

        @Test
        @DisplayName("replayAsOfThrowsOnBlankCaseId")
        void replayAsOfThrowsOnBlankCaseId() {
            assertThrows(IllegalArgumentException.class, () ->
                eventReplayer.replayAsOf("", BASE_TIMESTAMP));
        }

        @Test
        @DisplayName("replayAsOfThrowsOnNullAsOf")
        void replayAsOfThrowsOnNullAsOf() {
            assertThrows(IllegalArgumentException.class, () ->
                eventReplayer.replayAsOf(TEST_CASE_ID, null));
        }
    }

    @Nested
    @DisplayName("Snapshot-based Replay")
    class SnapshotBasedReplayTest {

        @Test
        @DisplayName("replayFromEmptySnapshot")
        void replayFromEmptySnapshot() throws Exception {
            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, 0, BASE_TIMESTAMP, "UNKNOWN", Map.of(), Map.of());

            List<WorkflowEvent> deltaEvents = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "wi-1", Map.of())
            );

            CaseStateView state = eventReplayer.replayFrom(snapshot, deltaEvents);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(1, state.getActiveWorkItems().size());
            assertEquals(1, state.getPayload().size());
        }

        @Test
        @DisplayName("replayFromComplexSnapshot")
        void replayFromComplexSnapshot() throws Exception {
            Map<String, CaseStateView.WorkItemState> snapshotWorkItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "STARTED", BASE_TIMESTAMP),
                "wi-2", new CaseStateView.WorkItemState("wi-2", "COMPLETED", BASE_TIMESTAMP)
            );

            Map<String, String> snapshotPayload = Map.of(
                "startedBy", "agent-order-service",
                "specId", TEST_SPEC_ID
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, 2, BASE_TIMESTAMP, "RUNNING", snapshotWorkItems, snapshotPayload);

            List<WorkflowEvent> deltaEvents = List.of(
                createTestEvent(WorkflowEvent.EventType.WORKITEM_SUSPENDED, TEST_CASE_ID, "wi-1", Map.of()),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "wi-3", Map.of()),
                createTestEvent(WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, Map.of())
            );

            CaseStateView state = eventReplayer.replayFrom(snapshot, deltaEvents);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.SUSPENDED, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(2, state.getActiveWorkItems().size()); // wi-1 (suspended), wi-3 (enabled)
            assertEquals(3, state.getPayload().size()); // startedBy, specId, and case suspension info
        }

        @Test
        @DisplayName("replayFromSnapshotWithNoDeltaEvents")
        void replayFromSnapshotWithNoDeltaEvents() throws Exception {
            Map<String, CaseStateView.WorkItemState> snapshotWorkItems = Map.of(
                "wi-1", new CaseStateView.WorkItemState("wi-1", "STARTED", BASE_TIMESTAMP)
            );

            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, 1, BASE_TIMESTAMP, "RUNNING", snapshotWorkItems, Map.of());

            List<WorkflowEvent> deltaEvents = List.of();

            CaseStateView state = eventReplayer.replayFrom(snapshot, deltaEvents);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertEquals(TEST_SPEC_ID, state.getSpecId());
            assertEquals(1, state.getActiveWorkItems().size());
            assertEquals("STARTED", state.getActiveWorkItems().get("wi-1").status());
        }

        @Test
        @DisplayName("replayFromSnapshotThrowsOnNullSnapshot")
        void replayFromSnapshotThrowsOnNullSnapshot() {
            List<WorkflowEvent> deltaEvents = List.of();
            assertThrows(NullPointerException.class, () ->
                eventReplayer.replayFrom(null, deltaEvents));
        }

        @Test
        @DisplayName("replayFromSnapshotThrowsOnNullDeltaEvents")
        void replayFromSnapshotThrowsOnNullDeltaEvents() {
            CaseSnapshot snapshot = new CaseSnapshot(
                TEST_CASE_ID, TEST_SPEC_ID, 0, BASE_TIMESTAMP, "UNKNOWN", Map.of(), Map.of());
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

        @Test
        @DisplayName("replayThrowsOnEventStoreException")
        void replayThrowsOnEventStoreException() throws Exception {
            when(mockEventStore.loadEvents(TEST_CASE_ID))
                .thenThrow(new WorkflowEventStore.EventStoreException("Database error"));

            assertThrows(EventReplayer.ReplayException.class, () -> eventReplayer.replay(TEST_CASE_ID));
        }

        @Test
        @DisplayName("replayExceptionPreservesCause")
        void replayExceptionPreservesCause() throws Exception {
            WorkflowEventStore.EventStoreException cause =
                new WorkflowEventStore.EventStoreException("Database error");
            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenThrow(cause);

            try {
                eventReplayer.replay(TEST_CASE_ID);
                fail("Should have thrown exception");
            } catch (EventReplayer.ReplayException e) {
                assertEquals("Failed to load events for case test-case-123", e.getMessage());
                assertEquals(cause, e.getCause());
            }
        }
    }

    @Nested
    @DisplayName("Event Replay Logic")
    class EventReplayLogicTest {

        @Test
        @DisplayName("invalidEventTypeDoesNotModifyState")
        void invalidEventTypeDoesNotModifyState() throws Exception {
            // This test uses a custom WorkflowEvent constructor that doesn't validate event types
            WorkflowEvent invalidEvent = new WorkflowEvent(
                UUID.randomUUID().toString(), null, "1.0", TEST_SPEC_ID,
                TEST_CASE_ID, null, BASE_TIMESTAMP, Map.of()
            ) {
                // Override to bypass enum validation
                @Override
                public String getEventType() { return "INVALID_TYPE"; }
            };

            List<WorkflowEvent> events = List.of(invalidEvent);

            CaseStateView state = eventReplayer.applyEvents(
                CaseStateView.empty(TEST_CASE_ID), events);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.UNKNOWN, state.getStatus());
            assertTrue(state.getActiveWorkItems().isEmpty());
            assertTrue(state.getPayload().isEmpty());
        }

        @Test
        @DisplayName("eventWithNullWorkItemIdIgnoresWorkItemUpdates")
        void eventWithNullWorkItemIdIgnoresWorkItemUpdates() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, Map.of()),
                // This event has null workItemId but should not modify work items
                createTestEvent(WorkflowEvent.EventType.WORKITEM_STARTED, TEST_CASE_ID, null, Map.of())
            );

            CaseStateView state = eventReplayer.applyEvents(
                CaseStateView.empty(TEST_CASE_ID), events);

            assertEquals(TEST_CASE_ID, state.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, state.getStatus());
            assertTrue(state.getActiveWorkItems().isEmpty());
        }
    }

    // Helper method to create test events
    private WorkflowEvent createTestEvent(WorkflowEvent.EventType type, String caseId,
                                        String workItemId, Map<String, String> payload) {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            type,
            "1.0",
            TEST_SPEC_ID,
            caseId,
            workItemId,
            BASE_TIMESTAMP,
            payload
        );
    }
}