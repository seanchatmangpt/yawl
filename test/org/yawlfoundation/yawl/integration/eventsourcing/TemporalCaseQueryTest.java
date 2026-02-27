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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TemporalCaseQuery}.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@ExtendWith(MockitoExtension.class)
class TemporalCaseQueryTest {

    @Mock
    private WorkflowEventStore mockEventStore;

    @Mock
    private EventReplayer mockReplayer;

    private TemporalCaseQuery temporalQuery;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_SPEC_ID = "OrderFulfillment:1.0";
    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-02-17T10:00:00Z");

    @BeforeEach
    void setUp() {
        temporalQuery = new TemporalCaseQuery(mockEventStore, mockReplayer);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("constructWithEventStoreAndReplayer")
        void constructWithEventStoreAndReplayer() {
            WorkflowEventStore store = mock(WorkflowEventStore.class);
            EventReplayer replayer = mock(EventReplayer.class);
            TemporalCaseQuery query = new TemporalCaseQuery(store, replayer);

            assertNotNull(query);
        }

        @Test
        @DisplayName("constructWithNullEventStoreThrows")
        void constructWithNullEventStoreThrows() {
            assertThrows(NullPointerException.class, () ->
                new TemporalCaseQuery(null, mockReplayer));
        }

        @Test
        @DisplayName("constructWithNullReplayerThrows")
        void constructWithNullReplayerThrows() {
            assertThrows(NullPointerException.class, () ->
                new TemporalCaseQuery(mockEventStore, null));
        }
    }

    @Nested
    @DisplayName("State At Query")
    class StateAtQueryTest {

        @Test
        @DisplayName("stateAtQueryBeforeCaseStarted")
        void stateAtQueryBeforeCaseStarted() throws Exception {
            Instant queryTime = BASE_TIMESTAMP.minusSeconds(60);
            CaseStateView expectedState = CaseStateView.empty(TEST_CASE_ID);

            when(mockReplayer.replayAsOf(TEST_CASE_ID, queryTime)).thenReturn(expectedState);

            CaseStateView result = temporalQuery.stateAt(TEST_CASE_ID, queryTime);

            assertEquals(expectedState, result);
            assertEquals(TEST_CASE_ID, result.getCaseId());
            assertEquals(CaseStateView.CaseStatus.UNKNOWN, result.getStatus());
        }

        @Test
        @DisplayName("stateAtQueryDuringCaseExecution")
        void stateAtQueryDuringCaseExecution() throws Exception {
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(45);
            CaseStateView expectedState = new CaseStateView(
                TEST_CASE_ID, TEST_SPEC_ID, CaseStateView.CaseStatus.RUNNING,
                BASE_TIMESTAMP.plusSeconds(30),
                Map.of("review-order", new CaseStateView.WorkItemState("review-order", "STARTED", BASE_TIMESTAMP.plusSeconds(30))),
                Map.of("startedBy", "agent-order-service", "priority", "high")
            );

            when(mockReplayer.replayAsOf(TEST_CASE_ID, queryTime)).thenReturn(expectedState);

            CaseStateView result = temporalQuery.stateAt(TEST_CASE_ID, queryTime);

            assertEquals(TEST_CASE_ID, result.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, result.getStatus());
            assertEquals(TEST_SPEC_ID, result.getSpecId());
            assertEquals(1, result.getActiveWorkItems().size());
            assertEquals("STARTED", result.getActiveWorkItems().get("review-order").status());
            assertEquals(2, result.getPayload().size());
        }

        @Test
        @DisplayName("stateAtQueryAfterCaseCompleted")
        void stateAtQueryAfterCaseCompleted() throws Exception {
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(120);
            CaseStateView expectedState = new CaseStateView(
                TEST_CASE_ID, TEST_SPEC_ID, CaseStateView.CaseStatus.COMPLETED,
                BASE_TIMESTAMP.plusSeconds(60),
                Map.of(),
                Map.of("startedBy", "agent-order-service", "completedBy", "system")
            );

            when(mockReplayer.replayAsOf(TEST_CASE_ID, queryTime)).thenReturn(expectedState);

            CaseStateView result = temporalQuery.stateAt(TEST_CASE_ID, queryTime);

            assertEquals(TEST_CASE_ID, result.getCaseId());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, result.getStatus());
            assertEquals(TEST_SPEC_ID, result.getSpecId());
            assertTrue(result.getActiveWorkItems().isEmpty());
            assertEquals(2, result.getPayload().size());
        }

        @Test
        @DisplayName("stateAtQueryThrowsOnBlankCaseId")
        void stateAtQueryThrowsOnBlankCaseId() {
            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.stateAt("", BASE_TIMESTAMP));
        }

        @Test
        @DisplayName("stateAtQueryThrowsOnNullAsOf")
        void stateAtQueryThrowsOnNullAsOf() {
            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.stateAt(TEST_CASE_ID, null));
        }

        @Test
        @DisplayName("stateAtQueryThrowsOnReplayException")
        void stateAtQueryThrowsOnReplayException() throws Exception {
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(45);
            when(mockReplayer.replayAsOf(TEST_CASE_ID, queryTime))
                .thenThrow(new EventReplayer.ReplayException("Replay failed", new RuntimeException()));

            assertThrows(TemporalCaseQuery.TemporalQueryException.class, () ->
                temporalQuery.stateAt(TEST_CASE_ID, queryTime));
        }
    }

    @Nested
    @DisplayName("Events Between Query")
    class EventsBetweenQueryTest {

        @Test
        @DisplayName("eventsBetweenWithSingleEvent")
        void eventsBetweenWithSingleEvent() throws Exception {
            Instant from = BASE_TIMESTAMP;
            Instant to = BASE_TIMESTAMP.plusSeconds(30);
            Instant eventTime = BASE_TIMESTAMP.plusSeconds(15);

            WorkflowEvent event = createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, from, eventTime);

            when(mockEventStore.loadEventsAsOf(TEST_CASE_ID, to))
                .thenReturn(List.of(event));

            List<WorkflowEvent> result = temporalQuery.eventsBetween(TEST_CASE_ID, from, to);

            assertEquals(1, result.size());
            assertEquals(event, result.get(0));
            assertEquals(eventTime, result.get(0).getTimestamp());
        }

        @Test
        @DisplayName("eventsBetweenWithMultipleEvents")
        void eventsBetweenWithMultipleEvents() throws Exception {
            Instant from = BASE_TIMESTAMP;
            Instant to = BASE_TIMESTAMP.plusSeconds(120);
            Instant event1Time = BASE_TIMESTAMP;
            Instant event2Time = BASE_TIMESTAMP.plusSeconds(30);
            Instant event3Time = BASE_TIMESTAMP.plusSeconds(60);

            List<WorkflowEvent> allEvents = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, from, event1Time),
                createTestEvent(WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "wi-1", null, event2Time),
                createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, null, event3Time)
            );

            when(mockEventStore.loadEventsAsOf(TEST_CASE_ID, to))
                .thenReturn(allEvents);

            List<WorkflowEvent> result = temporalQuery.eventsBetween(TEST_CASE_ID, from, to);

            assertEquals(3, result.size());
            assertEquals(allEvents, result);
        }

        @Test
        @DisplayName("eventsBetweenWithNoEventsInRange")
        void eventsBetweenWithNoEventsInRange() throws Exception {
            Instant from = BASE_TIMESTAMP.plusSeconds(90);
            Instant to = BASE_TIMESTAMP.plusSeconds(120);

            when(mockEventStore.loadEventsAsOf(TEST_CASE_ID, to))
                .thenReturn(List.of(
                    createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, null, BASE_TIMESTAMP)
                ));

            List<WorkflowEvent> result = temporalQuery.eventsBetween(TEST_CASE_ID, from, to);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("eventsBetweenWithBoundaryEvents")
        void eventsBetweenWithBoundaryEvents() throws Exception {
            Instant from = BASE_TIMESTAMP;
            Instant to = BASE_TIMESTAMP.plusSeconds(60);
            Instant fromEventTime = BASE_TIMESTAMP;
            Instant toEventTime = BASE_TIMESTAMP.plusSeconds(60);

            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, null, fromEventTime),
                createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, null, toEventTime)
            );

            when(mockEventStore.loadEventsAsOf(TEST_CASE_ID, to))
                .thenReturn(events);

            List<WorkflowEvent> result = temporalQuery.eventsBetween(TEST_CASE_ID, from, to);

            assertEquals(2, result.size());
            assertEquals(fromEventTime, result.get(0).getTimestamp());
            assertEquals(toEventTime, result.get(1).getTimestamp());
        }

        @Test
        @DisplayName("eventsBetweenThrowsOnBlankCaseId")
        void eventsBetweenThrowsOnBlankCaseId() {
            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.eventsBetween("", BASE_TIMESTAMP, BASE_TIMESTAMP.plusSeconds(60)));
        }

        @Test
        @DisplayName("eventsBetweenThrowsOnNullFrom")
        void eventsBetweenThrowsOnNullFrom() {
            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.eventsBetween(TEST_CASE_ID, null, BASE_TIMESTAMP.plusSeconds(60)));
        }

        @Test
        @DisplayName("eventsBetweenThrowsOnNullTo")
        void eventsBetweenThrowsOnNullTo() {
            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.eventsBetween(TEST_CASE_ID, BASE_TIMESTAMP, null));
        }

        @Test
        @DisplayName("eventsBetweenThrowsWhenFromIsAfterTo")
        void eventsBetweenThrowsWhenFromIsAfterTo() {
            Instant from = BASE_TIMESTAMP.plusSeconds(60);
            Instant to = BASE_TIMESTAMP;

            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.eventsBetween(TEST_CASE_ID, from, to));
        }

        @Test
        @DisplayName("eventsBetweenThrowsOnEventStoreException")
        void eventsBetweenThrowsOnEventStoreException() throws Exception {
            when(mockEventStore.loadEventsAsOf(TEST_CASE_ID, BASE_TIMESTAMP.plusSeconds(60)))
                .thenThrow(new WorkflowEventStore.EventStoreException("Read failed"));

            assertThrows(TemporalCaseQuery.TemporalQueryException.class, () ->
                temporalQuery.eventsBetween(TEST_CASE_ID, BASE_TIMESTAMP, BASE_TIMESTAMP.plusSeconds(60)));
        }
    }

    @Nested
    @DisplayName("Work Item Status At Query")
    class WorkItemStatusAtQueryTest {

        @Test
        @DisplayName("workItemStatusAtWhenItemExists")
        void workItemStatusAtWhenItemExists() throws Exception {
            String workItemId = "review-order";
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(45);
            CaseStateView state = new CaseStateView(
                TEST_CASE_ID, TEST_SPEC_ID, CaseStateView.CaseStatus.RUNNING,
                BASE_TIMESTAMP.plusSeconds(30),
                Map.of(workItemId, new CaseStateView.WorkItemState(workItemId, "STARTED", BASE_TIMESTAMP.plusSeconds(30))),
                Map.of()
            );

            when(mockReplayer.replayAsOf(TEST_CASE_ID, queryTime)).thenReturn(state);

            String status = temporalQuery.workItemStatusAt(TEST_CASE_ID, workItemId, queryTime);

            assertEquals("STARTED", status);
        }

        @Test
        @DisplayName("workItemStatusAtWhenItemDoesNotExist")
        void workItemStatusAtWhenItemDoesNotExist() throws Exception {
            String workItemId = "non-existent";
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(45);
            CaseStateView state = CaseStateView.empty(TEST_CASE_ID)
                .withStatus(CaseStateView.CaseStatus.RUNNING);

            when(mockReplayer.replayAsOf(TEST_CASE_ID, queryTime)).thenReturn(state);

            String status = temporalQuery.workItemStatusAt(TEST_CASE_ID, workItemId, queryTime);

            assertEquals("NOT_PRESENT", status);
        }

        @Test
        @DisplayName("workItemStatusAtBeforeItemWasEnabled")
        void workItemStatusAtBeforeItemWasEnabled() throws Exception {
            String workItemId = "review-order";
            Instant queryTime = BASE_TIMESTAMP.minusSeconds(30);
            CaseStateView state = CaseStateView.empty(TEST_CASE_ID);

            when(mockReplayer.replayAsOf(TEST_CASE_ID, queryTime)).thenReturn(state);

            String status = temporalQuery.workItemStatusAt(TEST_CASE_ID, workItemId, queryTime);

            assertEquals("NOT_PRESENT", status);
        }

        @Test
        @DisplayName("workItemStatusAtAfterItemWasCompleted")
        void workItemStatusAtAfterItemWasCompleted() throws Exception {
            String workItemId = "review-order";
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(90);
            CaseStateView state = new CaseStateView(
                TEST_CASE_ID, TEST_SPEC_ID, CaseStateView.CaseStatus.RUNNING,
                BASE_TIMESTAMP.plusSeconds(60),
                Map.of(),
                Map.of()
            );

            when(mockReplayer.replayAsOf(TEST_CASE_ID, queryTime)).thenReturn(state);

            String status = temporalQuery.workItemStatusAt(TEST_CASE_ID, workItemId, queryTime);

            assertEquals("NOT_PRESENT", status);
        }

        @Test
        @DisplayName("workItemStatusAtThrowsOnBlankCaseId")
        void workItemStatusAtThrowsOnBlankCaseId() {
            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.workItemStatusAt("", "review-order", BASE_TIMESTAMP));
        }

        @Test
        @DisplayName("workItemStatusAtThrowsOnBlankWorkItemId")
        void workItemStatusAtThrowsOnBlankWorkItemId() {
            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.workItemStatusAt(TEST_CASE_ID, "", BASE_TIMESTAMP));
        }

        @Test
        @DisplayName("workItemStatusAtThrowsOnNullAsOf")
        void workItemStatusAtThrowsOnNullAsOf() {
            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.workItemStatusAt(TEST_CASE_ID, "review-order", null));
        }
    }

    @Nested
    @DisplayName("Duration In Status Query")
    class DurationInStatusQueryTest {

        @Test
        @DisplayName("durationInStatusNeverInStatus")
        void durationInStatusNeverInStatus() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, null, BASE_TIMESTAMP),
                createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, null, BASE_TIMESTAMP.plusSeconds(60))
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            Duration duration = temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.SUSPENDED);

            assertEquals(Duration.ZERO, duration);
        }

        @Test
        @DisplayName("durationInStatusSingleStatusChange")
        void durationInStatusSingleStatusChange() throws Exception {
            Instant suspendedTime = BASE_TIMESTAMP.plusSeconds(30);
            Instant resumedTime = BASE_TIMESTAMP.plusSeconds(90);
            Instant completedTime = BASE_TIMESTAMP.plusSeconds(120);

            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, null, BASE_TIMESTAMP),
                createTestEvent(WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, null, suspendedTime),
                createTestEvent(WorkflowEvent.EventType.CASE_RESUMED, TEST_CASE_ID, null, null, resumedTime),
                createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, null, completedTime)
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            Duration duration = temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.SUSPENDED);

            assertEquals(Duration.between(suspendedTime, resumedTime), duration);
        }

        @Test
        @DisplayName("durationInStatusMultipleStatusChanges")
        void durationInStatusMultipleStatusChanges() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, null, BASE_TIMESTAMP),
                createTestEvent(WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, null, BASE_TIMESTAMP.plusSeconds(30)),
                createTestEvent(WorkflowEvent.EventType.CASE_RESUMED, TEST_CASE_ID, null, null, BASE_TIMESTAMP.plusSeconds(60)),
                createTestEvent(WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, null, BASE_TIMESTAMP.plusSeconds(90)),
                createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, null, BASE_TIMESTAMP.plusSeconds(120))
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            Duration duration = temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.SUSPENDED);

            Duration expected = Duration.between(BASE_TIMESTAMP.plusSeconds(30), BASE_TIMESTAMP.plusSeconds(60))
                                      .plus(Duration.between(BASE_TIMESTAMP.plusSeconds(90), BASE_TIMESTAMP.plusSeconds(120)));
            assertEquals(expected, duration);
        }

        @Test
        @DisplayName("durationInStatusStillInStatusAtEnd")
        void durationInStatusStillInStatusAtEnd() throws Exception {
            Instant suspendedTime = BASE_TIMESTAMP.plusSeconds(30);

            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, null, BASE_TIMESTAMP),
                createTestEvent(WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, null, suspendedTime)
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            Duration duration = temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.SUSPENDED);

            assertEquals(Duration.between(suspendedTime, BASE_TIMESTAMP.plusSeconds(30)), duration);
        }

        @Test
        @DisplayName("durationInStatusRunningTime")
        void durationInStatusRunningTime() throws Exception {
            List<WorkflowEvent> events = List.of(
                createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, null, BASE_TIMESTAMP),
                createTestEvent(WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, null, BASE_TIMESTAMP.plusSeconds(60)),
                createTestEvent(WorkflowEvent.EventType.CASE_RESUMED, TEST_CASE_ID, null, null, BASE_TIMESTAMP.plusSeconds(90)),
                createTestEvent(WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, null, BASE_TIMESTAMP.plusSeconds(120))
            );

            when(mockEventStore.loadEvents(TEST_CASE_ID)).thenReturn(events);

            Duration duration = temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.RUNNING);

            Duration expected = Duration.between(BASE_TIMESTAMP, BASE_TIMESTAMP.plusSeconds(60))
                                      .plus(Duration.between(BASE_TIMESTAMP.plusSeconds(90), BASE_TIMESTAMP.plusSeconds(120)));
            assertEquals(expected, duration);
        }

        @Test
        @DisplayName("durationInStatusThrowsOnBlankCaseId")
        void durationInStatusThrowsOnBlankCaseId() {
            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.durationInStatus("", CaseStateView.CaseStatus.RUNNING));
        }

        @Test
        @DisplayName("durationInStatusThrowsOnNullTargetStatus")
        void durationInStatusThrowsOnNullTargetStatus() {
            assertThrows(IllegalArgumentException.class, () ->
                temporalQuery.durationInStatus(TEST_CASE_ID, null));
        }

        @Test
        @DisplayName("durationInStatusThrowsOnEventStoreException")
        void durationInStatusThrowsOnEventStoreException() throws Exception {
            when(mockEventStore.loadEvents(TEST_CASE_ID))
                .thenThrow(new WorkflowEventStore.EventStoreException("Read failed"));

            assertThrows(TemporalCaseQuery.TemporalQueryException.class, () ->
                temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.RUNNING));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTest {

        @Test
        @DisplayName("temporalQueryExceptionWithMessageAndCause")
        void temporalQueryExceptionWithMessageAndCause() {
            Throwable cause = new RuntimeException("Root cause");
            TemporalCaseQuery.TemporalQueryException e =
                new TemporalCaseQuery.TemporalQueryException("Test error", cause);

            assertEquals("Test error", e.getMessage());
            assertEquals(cause, e.getCause());
        }
    }

    // Helper method to create test events
    private WorkflowEvent createTestEvent(WorkflowEvent.EventType type, String caseId,
                                        String workItemId, Map<String, String> payload, Instant timestamp) {
        return new WorkflowEvent(
            "event-123", type, "1.0", TEST_SPEC_ID, caseId, workItemId, timestamp, payload
        );
    }

    private WorkflowEvent createTestEvent(WorkflowEvent.EventType type, String caseId,
                                        String workItemId, Map<String, String> payload, Instant startTimestamp, Instant eventTimestamp) {
        return new WorkflowEvent(
            "event-123", type, "1.0", TEST_SPEC_ID, caseId, workItemId, eventTimestamp, payload
        );
    }
}