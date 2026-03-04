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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link TemporalCaseQuery}.
 *
 * <p>Tests use real H2 in-memory database and actual event store/replayer
 * implementations to verify temporal query behavior end-to-end.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class TemporalCaseQueryTest {

    private DataSource dataSource;
    private WorkflowEventStore eventStore;
    private EventReplayer replayer;
    private TemporalCaseQuery temporalQuery;

    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_SPEC_ID = "OrderFulfillment:1.0";
    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-02-17T10:00:00Z");

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = EventSourcingTestFixture.createDataSource();
        EventSourcingTestFixture.createSchema(dataSource);

        eventStore = new WorkflowEventStore(dataSource);
        replayer = new EventReplayer(eventStore);
        temporalQuery = new TemporalCaseQuery(eventStore, replayer);
    }

    @AfterEach
    void tearDown() throws SQLException {
        EventSourcingTestFixture.dropSchema(dataSource);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("constructWithEventStoreAndReplayer")
        void constructWithEventStoreAndReplayer() throws SQLException {
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
            WorkflowEventStore store = new WorkflowEventStore(ds);
            EventReplayer rep = new EventReplayer(store);

            TemporalCaseQuery query = new TemporalCaseQuery(store, rep);

            assertNotNull(query);
            EventSourcingTestFixture.dropSchema(ds);
        }

        @Test
        @DisplayName("constructWithNullEventStoreThrows")
        void constructWithNullEventStoreThrows() {
            assertThrows(NullPointerException.class, () ->
                new TemporalCaseQuery(null, replayer));
        }

        @Test
        @DisplayName("constructWithNullReplayerThrows")
        void constructWithNullReplayerThrows() {
            assertThrows(NullPointerException.class, () ->
                new TemporalCaseQuery(eventStore, null));
        }
    }

    @Nested
    @DisplayName("State At Query")
    class StateAtQueryTest {

        @Test
        @DisplayName("stateAtQueryBeforeCaseStarted")
        void stateAtQueryBeforeCaseStarted() throws Exception {
            // Given: a case that starts at BASE_TIMESTAMP
            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            eventStore.append(startEvent, 0);

            // When: query before case started
            Instant queryTime = BASE_TIMESTAMP.minusSeconds(60);
            CaseStateView result = temporalQuery.stateAt(TEST_CASE_ID, queryTime);

            // Then: state is UNKNOWN
            assertEquals(TEST_CASE_ID, result.getCaseId());
            assertEquals(CaseStateView.CaseStatus.UNKNOWN, result.getStatus());
        }

        @Test
        @DisplayName("stateAtQueryDuringCaseExecution")
        void stateAtQueryDuringCaseExecution() throws Exception {
            // Given: case with work item enabled
            Instant enabledTime = BASE_TIMESTAMP.plusSeconds(30);
            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent enabledEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "review-order", enabledTime);

            eventStore.append(startEvent, 0);
            eventStore.append(enabledEvent, 1);

            // When: query after work item enabled
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(45);
            CaseStateView result = temporalQuery.stateAt(TEST_CASE_ID, queryTime);

            // Then: case is running with active work item
            assertEquals(TEST_CASE_ID, result.getCaseId());
            assertEquals(CaseStateView.CaseStatus.RUNNING, result.getStatus());
            assertEquals(TEST_SPEC_ID, result.getSpecId());
            assertEquals(1, result.getActiveWorkItems().size());
            assertEquals("ENABLED", result.getActiveWorkItems().get("review-order").status());
        }

        @Test
        @DisplayName("stateAtQueryAfterCaseCompleted")
        void stateAtQueryAfterCaseCompleted() throws Exception {
            // Given: completed case
            Instant completedTime = BASE_TIMESTAMP.plusSeconds(60);
            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent completeEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, completedTime);

            eventStore.append(startEvent, 0);
            eventStore.append(completeEvent, 1);

            // When: query after completion
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(120);
            CaseStateView result = temporalQuery.stateAt(TEST_CASE_ID, queryTime);

            // Then: case is completed
            assertEquals(TEST_CASE_ID, result.getCaseId());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, result.getStatus());
            assertEquals(TEST_SPEC_ID, result.getSpecId());
            assertTrue(result.getActiveWorkItems().isEmpty());
            assertNotNull(result.getPayload().get("completedAt"));
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
    }

    @Nested
    @DisplayName("Events Between Query")
    class EventsBetweenQueryTest {

        @Test
        @DisplayName("eventsBetweenWithSingleEvent")
        void eventsBetweenWithSingleEvent() throws Exception {
            // Given: single event in range
            Instant eventTime = BASE_TIMESTAMP.plusSeconds(15);
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, eventTime);
            eventStore.append(event, 0);

            Instant from = BASE_TIMESTAMP;
            Instant to = BASE_TIMESTAMP.plusSeconds(30);

            // When
            List<WorkflowEvent> result = temporalQuery.eventsBetween(TEST_CASE_ID, from, to);

            // Then
            assertEquals(1, result.size());
            assertEquals(eventTime, result.get(0).getTimestamp());
        }

        @Test
        @DisplayName("eventsBetweenWithMultipleEvents")
        void eventsBetweenWithMultipleEvents() throws Exception {
            // Given: multiple events
            WorkflowEvent event1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent event2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "wi-1", BASE_TIMESTAMP.plusSeconds(30));
            WorkflowEvent event3 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, BASE_TIMESTAMP.plusSeconds(60));

            eventStore.append(event1, 0);
            eventStore.append(event2, 1);
            eventStore.append(event3, 2);

            Instant from = BASE_TIMESTAMP;
            Instant to = BASE_TIMESTAMP.plusSeconds(120);

            // When
            List<WorkflowEvent> result = temporalQuery.eventsBetween(TEST_CASE_ID, from, to);

            // Then
            assertEquals(3, result.size());
            assertEquals(BASE_TIMESTAMP, result.get(0).getTimestamp());
            assertEquals(BASE_TIMESTAMP.plusSeconds(30), result.get(1).getTimestamp());
            assertEquals(BASE_TIMESTAMP.plusSeconds(60), result.get(2).getTimestamp());
        }

        @Test
        @DisplayName("eventsBetweenWithNoEventsInRange")
        void eventsBetweenWithNoEventsInRange() throws Exception {
            // Given: event outside query range
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            eventStore.append(event, 0);

            Instant from = BASE_TIMESTAMP.plusSeconds(90);
            Instant to = BASE_TIMESTAMP.plusSeconds(120);

            // When
            List<WorkflowEvent> result = temporalQuery.eventsBetween(TEST_CASE_ID, from, to);

            // Then
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("eventsBetweenWithBoundaryEvents")
        void eventsBetweenWithBoundaryEvents() throws Exception {
            // Given: events at boundary timestamps
            Instant fromEventTime = BASE_TIMESTAMP;
            Instant toEventTime = BASE_TIMESTAMP.plusSeconds(60);

            WorkflowEvent event1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, fromEventTime);
            WorkflowEvent event2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, toEventTime);

            eventStore.append(event1, 0);
            eventStore.append(event2, 1);

            Instant from = BASE_TIMESTAMP;
            Instant to = BASE_TIMESTAMP.plusSeconds(60);

            // When
            List<WorkflowEvent> result = temporalQuery.eventsBetween(TEST_CASE_ID, from, to);

            // Then: both boundary events included (inclusive range)
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
    }

    @Nested
    @DisplayName("Work Item Status At Query")
    class WorkItemStatusAtQueryTest {

        @Test
        @DisplayName("workItemStatusAtWhenItemExists")
        void workItemStatusAtWhenItemExists() throws Exception {
            // Given: case with enabled work item
            String workItemId = "review-order";
            Instant enabledTime = BASE_TIMESTAMP.plusSeconds(30);

            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent enabledEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, workItemId, enabledTime);

            eventStore.append(startEvent, 0);
            eventStore.append(enabledEvent, 1);

            // When: query after item enabled
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(45);
            String status = temporalQuery.workItemStatusAt(TEST_CASE_ID, workItemId, queryTime);

            // Then
            assertEquals("ENABLED", status);
        }

        @Test
        @DisplayName("workItemStatusAtWhenItemDoesNotExist")
        void workItemStatusAtWhenItemDoesNotExist() throws Exception {
            // Given: case without the queried work item
            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            eventStore.append(startEvent, 0);

            // When
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(45);
            String status = temporalQuery.workItemStatusAt(TEST_CASE_ID, "non-existent", queryTime);

            // Then
            assertEquals("NOT_PRESENT", status);
        }

        @Test
        @DisplayName("workItemStatusAtBeforeItemWasEnabled")
        void workItemStatusAtBeforeItemWasEnabled() throws Exception {
            // Given: work item enabled later
            String workItemId = "review-order";
            Instant enabledTime = BASE_TIMESTAMP.plusSeconds(30);

            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent enabledEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, workItemId, enabledTime);

            eventStore.append(startEvent, 0);
            eventStore.append(enabledEvent, 1);

            // When: query before item enabled
            Instant queryTime = BASE_TIMESTAMP.minusSeconds(30);
            String status = temporalQuery.workItemStatusAt(TEST_CASE_ID, workItemId, queryTime);

            // Then
            assertEquals("NOT_PRESENT", status);
        }

        @Test
        @DisplayName("workItemStatusAtAfterItemWasCompleted")
        void workItemStatusAtAfterItemWasCompleted() throws Exception {
            // Given: work item completed
            String workItemId = "review-order";
            Instant enabledTime = BASE_TIMESTAMP.plusSeconds(30);
            Instant completedTime = BASE_TIMESTAMP.plusSeconds(60);

            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent enabledEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, workItemId, enabledTime);
            WorkflowEvent completedEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_COMPLETED, TEST_CASE_ID, workItemId, completedTime);

            eventStore.append(startEvent, 0);
            eventStore.append(enabledEvent, 1);
            eventStore.append(completedEvent, 2);

            // When: query after item completed
            Instant queryTime = BASE_TIMESTAMP.plusSeconds(90);
            String status = temporalQuery.workItemStatusAt(TEST_CASE_ID, workItemId, queryTime);

            // Then: completed items are removed from active items
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
            // Given: case never suspended
            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent completeEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, BASE_TIMESTAMP.plusSeconds(60));

            eventStore.append(startEvent, 0);
            eventStore.append(completeEvent, 1);

            // When
            Duration duration = temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.SUSPENDED);

            // Then
            assertEquals(Duration.ZERO, duration);
        }

        @Test
        @DisplayName("durationInStatusSingleStatusChange")
        void durationInStatusSingleStatusChange() throws Exception {
            // Given: case suspended once
            Instant suspendedTime = BASE_TIMESTAMP.plusSeconds(30);
            Instant resumedTime = BASE_TIMESTAMP.plusSeconds(90);
            Instant completedTime = BASE_TIMESTAMP.plusSeconds(120);

            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent suspendedEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, suspendedTime);
            WorkflowEvent resumedEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_RESUMED, TEST_CASE_ID, null, resumedTime);
            WorkflowEvent completeEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, completedTime);

            eventStore.append(startEvent, 0);
            eventStore.append(suspendedEvent, 1);
            eventStore.append(resumedEvent, 2);
            eventStore.append(completeEvent, 3);

            // When
            Duration duration = temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.SUSPENDED);

            // Then
            assertEquals(Duration.between(suspendedTime, resumedTime), duration);
        }

        @Test
        @DisplayName("durationInStatusMultipleStatusChanges")
        void durationInStatusMultipleStatusChanges() throws Exception {
            // Given: case suspended twice
            Instant suspended1 = BASE_TIMESTAMP.plusSeconds(30);
            Instant resumed1 = BASE_TIMESTAMP.plusSeconds(60);
            Instant suspended2 = BASE_TIMESTAMP.plusSeconds(90);
            Instant completed = BASE_TIMESTAMP.plusSeconds(120);

            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent suspendedEvent1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, suspended1);
            WorkflowEvent resumedEvent1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_RESUMED, TEST_CASE_ID, null, resumed1);
            WorkflowEvent suspendedEvent2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, suspended2);
            WorkflowEvent completeEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, completed);

            eventStore.append(startEvent, 0);
            eventStore.append(suspendedEvent1, 1);
            eventStore.append(resumedEvent1, 2);
            eventStore.append(suspendedEvent2, 3);
            eventStore.append(completeEvent, 4);

            // When
            Duration duration = temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.SUSPENDED);

            // Then
            Duration expected = Duration.between(suspended1, resumed1)
                                      .plus(Duration.between(suspended2, completed));
            assertEquals(expected, duration);
        }

        @Test
        @DisplayName("durationInStatusStillInStatusAtEnd")
        void durationInStatusStillInStatusAtEnd() throws Exception {
            // Given: case still suspended at end of event stream
            Instant suspendedTime = BASE_TIMESTAMP.plusSeconds(30);

            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent suspendedEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, suspendedTime);

            eventStore.append(startEvent, 0);
            eventStore.append(suspendedEvent, 1);

            // When
            Duration duration = temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.SUSPENDED);

            // Then: duration measured from suspension to last event
            assertEquals(Duration.between(suspendedTime, suspendedTime), duration);
        }

        @Test
        @DisplayName("durationInStatusRunningTime")
        void durationInStatusRunningTime() throws Exception {
            // Given: case with suspension period
            Instant suspendedTime = BASE_TIMESTAMP.plusSeconds(60);
            Instant resumedTime = BASE_TIMESTAMP.plusSeconds(90);
            Instant completedTime = BASE_TIMESTAMP.plusSeconds(120);

            WorkflowEvent startEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            WorkflowEvent suspendedEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_SUSPENDED, TEST_CASE_ID, null, suspendedTime);
            WorkflowEvent resumedEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_RESUMED, TEST_CASE_ID, null, resumedTime);
            WorkflowEvent completeEvent = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, completedTime);

            eventStore.append(startEvent, 0);
            eventStore.append(suspendedEvent, 1);
            eventStore.append(resumedEvent, 2);
            eventStore.append(completeEvent, 3);

            // When
            Duration duration = temporalQuery.durationInStatus(TEST_CASE_ID, CaseStateView.CaseStatus.RUNNING);

            // Then: RUNNING from start to suspend, then resume to complete
            Duration expected = Duration.between(BASE_TIMESTAMP, suspendedTime)
                                      .plus(Duration.between(resumedTime, completedTime));
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

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTest {

        @Test
        @DisplayName("fullReplayChain_endToEnd")
        void fullReplayChainEndToEnd() throws Exception {
            // Given: complete case lifecycle
            String caseId = EventSourcingTestFixture.generateCaseId("integration");

            // Events in sequence
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null, BASE_TIMESTAMP), 0);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "task-1", BASE_TIMESTAMP.plusSeconds(10)), 1);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_STARTED, caseId, "task-1", BASE_TIMESTAMP.plusSeconds(20)), 2);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_COMPLETED, caseId, "task-1", BASE_TIMESTAMP.plusSeconds(30)), 3);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_COMPLETED, caseId, null, BASE_TIMESTAMP.plusSeconds(40)), 4);

            // When: query at various points in time
            CaseStateView atStart = temporalQuery.stateAt(caseId, BASE_TIMESTAMP);
            CaseStateView duringTask = temporalQuery.stateAt(caseId, BASE_TIMESTAMP.plusSeconds(25));
            CaseStateView atEnd = temporalQuery.stateAt(caseId, BASE_TIMESTAMP.plusSeconds(50));

            // Then: states are correct
            assertEquals(CaseStateView.CaseStatus.RUNNING, atStart.getStatus());
            assertEquals(0, atStart.getActiveWorkItems().size());

            assertEquals(CaseStateView.CaseStatus.RUNNING, duringTask.getStatus());
            assertEquals(1, duringTask.getActiveWorkItems().size());
            assertEquals("STARTED", duringTask.getActiveWorkItems().get("task-1").status());

            assertEquals(CaseStateView.CaseStatus.COMPLETED, atEnd.getStatus());
            assertEquals(0, atEnd.getActiveWorkItems().size());
        }

        @Test
        @DisplayName("multipleCases_independentQueries")
        void multipleCasesIndependentQueries() throws Exception {
            // Given: two independent cases
            String case1 = EventSourcingTestFixture.generateCaseId("case1");
            String case2 = EventSourcingTestFixture.generateCaseId("case2");

            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, case1, null, BASE_TIMESTAMP), 0);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_SUSPENDED, case1, null, BASE_TIMESTAMP.plusSeconds(10)), 1);

            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, case2, null, BASE_TIMESTAMP.plusSeconds(5)), 0);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_COMPLETED, case2, null, BASE_TIMESTAMP.plusSeconds(15)), 1);

            // When: query each case independently
            CaseStateView state1 = temporalQuery.stateAt(case1, BASE_TIMESTAMP.plusSeconds(20));
            CaseStateView state2 = temporalQuery.stateAt(case2, BASE_TIMESTAMP.plusSeconds(20));

            // Then: states are independent
            assertEquals(CaseStateView.CaseStatus.SUSPENDED, state1.getStatus());
            assertEquals(CaseStateView.CaseStatus.COMPLETED, state2.getStatus());
        }
    }
}
