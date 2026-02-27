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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WorkflowEventStore}.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEventStoreTest {

    @Mock
    private DataSource mockDataSource;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    private WorkflowEventStore eventStore;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_SPEC_ID = "OrderFulfillment:1.0";
    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-02-17T10:00:00Z");

    @BeforeEach
    void setUp() throws SQLException {
        eventStore = new WorkflowEventStore(mockDataSource);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("constructWithDataSource")
        void constructWithDataSource() {
            DataSource ds = mock(DataSource.class);
            WorkflowEventStore store = new WorkflowEventStore(ds);
            assertNotNull(store);
        }

        @Test
        @DisplayName("constructWithNullDataSourceThrows")
        void constructWithNullDataSourceThrows() {
            assertThrows(NullPointerException.class, () -> new WorkflowEventStore(null));
        }
    }

    @Nested
    @DisplayName("Append Events")
    class AppendEventsTest {

        @Test
        @DisplayName("appendFirstEventSuccess")
        void appendFirstEventSuccess() throws Exception {
            // Setup mocks
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString());
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong());
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any());
            doNothing().when(mockPreparedStatement).executeUpdate();
            doNothing().when(mockConnection).close();

            WorkflowEvent event = createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null);
            eventStore.append(event, 0);

            verify(mockPreparedStatement, times(1)).executeUpdate();
            verify(mockConnection, times(1)).close();
        }

        @Test
        @DisplayName("appendNextEventSuccess")
        void appendNextEventSuccess() throws Exception {
            // Setup mocks
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString());
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong());
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any());
            when(mockResultSet.next()).thenReturn(true);
            when(mockResultSet.getLong(1)).thenReturn(0L);
            doNothing().when(mockPreparedStatement).executeUpdate();
            doNothing().when(mockConnection).commit();
            doNothing().when(mockConnection).rollback();
            doNothing().when(mockConnection).close();
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

            WorkflowEvent event = createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null);
            long sequence = eventStore.appendNext(event);

            assertEquals(0, sequence);
            verify(mockConnection, times(1)).commit();
        }

        @Test
        @DisplayName("appendEventWithInvalidSequenceThrows")
        void appendEventWithInvalidSequenceThrows() {
            WorkflowEvent event = createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null);

            assertThrows(IllegalArgumentException.class, () -> eventStore.append(event, -1));
            assertThrows(IllegalArgumentException.class, () -> eventStore.append(event, Long.MAX_VALUE));
        }

        @Test
        @DisplayName("appendEventWithNullEventThrows")
        void appendEventWithNullEventThrows() {
            assertThrows(NullPointerException.class, () -> eventStore.append(null, 0));
        }

        @Test
        @DisplayName("appendEventWithNullPayloadThrows")
        void appendEventWithNullPayloadThrows() {
            WorkflowEvent event = new WorkflowEvent(
                UUID.randomUUID().toString(),
                WorkflowEvent.EventType.CASE_STARTED,
                "1.0",
                TEST_SPEC_ID,
                TEST_CASE_ID,
                null,
                BASE_TIMESTAMP,
                null
            );

            assertThrows(NullPointerException.class, () -> eventStore.append(event, 0));
        }

        @Test
        @DisplayName("appendEventWithInvalidEventTypeThrows")
        void appendEventWithInvalidEventTypeThrows() {
            // Create a custom event type that doesn't exist in the enum
            WorkflowEvent event = new WorkflowEvent(
                UUID.randomUUID().toString(),
                WorkflowEvent.EventType.CASE_COMPLETED,
                "1.0",
                TEST_SPEC_ID,
                TEST_CASE_ID,
                null,
                BASE_TIMESTAMP,
                Map.of()
            );

            try {
                when(mockDataSource.getConnection()).thenReturn(mockConnection);
                when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
                doNothing().when(mockPreparedStatement).setString(anyInt(), anyString());
                doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong());
                doNothing().when(mockPreparedStatement).setObject(anyInt(), any());
                doNothing().when(mockPreparedStatement).executeUpdate();
                doNothing().when(mockConnection).close();

                eventStore.append(event, 0);
                fail("Should have thrown exception");
            } catch (WorkflowEventStore.EventStoreException e) {
                assertTrue(e.getMessage().contains("Unknown event type"));
            }
        }
    }

    @Nested
    @DisplayName("Load Events")
    class LoadEventsTest {

        @Test
        @DisplayName("loadEventsForCase")
        void loadEventsForCase() throws Exception {
            // Setup mocks for event loading
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);

            // Mock first event
            when(mockResultSet.getString("event_id")).thenReturn("event-1");
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
            when(mockResultSet.getString("seq_num")).thenReturn("0");
            when(mockResultSet.getString("event_type")).thenReturn("CASE_STARTED");
            when(mockResultSet.getString("schema_version")).thenReturn("1.0");
            when(mockResultSet.getTimestamp("event_timestamp")).thenReturn(
                Timestamp.from(BASE_TIMESTAMP));
            when(mockResultSet.getString("payload_json")).thenReturn("{}");

            WorkflowEvent event = createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null);
            List<WorkflowEvent> events = eventStore.loadEvents(TEST_CASE_ID);

            assertEquals(1, events.size());
            assertEquals(TEST_CASE_ID, events.get(0).getCaseId());
            assertEquals(WorkflowEvent.EventType.CASE_STARTED, events.get(0).getEventType());
        }

        @Test
        @DisplayName("loadEventsEmptyCase")
        void loadEventsEmptyCase() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(false);

            List<WorkflowEvent> events = eventStore.loadEvents(TEST_CASE_ID);

            assertTrue(events.isEmpty());
        }

        @Test
        @DisplayName("loadEventsAsOf")
        void loadEventsAsOf() throws Exception {
            Instant asOf = BASE_TIMESTAMP.plusSeconds(30);
            Instant eventTimestamp = BASE_TIMESTAMP.plusSeconds(15);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("event_id")).thenReturn("event-1");
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
            when(mockResultSet.getString("seq_num")).thenReturn("0");
            when(mockResultSet.getString("event_type")).thenReturn("CASE_STARTED");
            when(mockResultSet.getString("schema_version")).thenReturn("1.0");
            when(mockResultSet.getTimestamp("event_timestamp")).thenReturn(
                Timestamp.from(eventTimestamp));
            when(mockResultSet.getString("payload_json")).thenReturn("{}");

            List<WorkflowEvent> events = eventStore.loadEventsAsOf(TEST_CASE_ID, asOf);

            assertEquals(1, events.size());
            assertEquals(eventTimestamp, events.get(0).getTimestamp());
        }

        @Test
        @DisplayName("loadEventsSince")
        void loadEventsSince() throws Exception {
            Instant eventTimestamp = BASE_TIMESTAMP.plusSeconds(15);

            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("event_id")).thenReturn("event-1");
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
            when(mockResultSet.getString("seq_num")).thenReturn("1");
            when(mockResultSet.getString("event_type")).thenReturn("CASE_STARTED");
            when(mockResultSet.getString("schema_version")).thenReturn("1.0");
            when(mockResultSet.getTimestamp("event_timestamp")).thenReturn(
                Timestamp.from(eventTimestamp));
            when(mockResultSet.getString("payload_json")).thenReturn("{}");

            List<WorkflowEvent> events = eventStore.loadEventsSince(TEST_CASE_ID, 0);

            assertEquals(1, events.size());
            assertEquals(1, events.get(0).getSequenceNumber());
        }

        @Test
        @DisplayName("loadEventsThrowsOnBlankCaseId")
        void loadEventsThrowsOnBlankCaseId() {
            assertThrows(IllegalArgumentException.class, () -> eventStore.loadEvents(""));
            assertThrows(IllegalArgumentException.class, () -> eventStore.loadEvents(null));
        }

        @Test
        @DisplayName("loadEventsAsOfThrowsOnNullAsOf")
        void loadEventsAsOfThrowsOnNullAsOf() {
            assertThrows(IllegalArgumentException.class, () ->
                eventStore.loadEventsAsOf(TEST_CASE_ID, null));
        }

        @Test
        @DisplayName("loadEventsSinceThrowsOnBlankCaseId")
        void loadEventsSinceThrowsOnBlankCaseId() {
            assertThrows(IllegalArgumentException.class, () ->
                eventStore.loadEventsSince("", 0));
        }
    }

    @Nested
    @DisplayName("Concurrent Modification")
    class ConcurrentModificationTest {

        @Test
        @DisplayName("concurrentModificationExceptionOnSQLState23000")
        void concurrentModificationExceptionOnSQLState23000() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString());
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong());
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any());
            when(mockPreparedStatement.executeUpdate()).thenThrow(
                new SQLException("Duplicate entry", "23000"));
            doNothing().when(mockConnection).close();

            WorkflowEvent event = createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null);

            assertThrows(WorkflowEventStore.ConcurrentModificationException.class,
                () -> eventStore.append(event, 0));
        }

        @Test
        @DisplayName("concurrentModificationExceptionOnSQLState23505")
        void concurrentModificationExceptionOnSQLState23505() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString);
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong);
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any);
            when(mockPreparedStatement.executeUpdate()).thenThrow(
                new SQLException("Unique violation", "23505"));
            doNothing().when(mockConnection).close();

            WorkflowEvent event = createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null);

            assertThrows(WorkflowEventStore.ConcurrentModificationException.class,
                () -> eventStore.append(event, 0));
        }

        @Test
        @DisplayName("concurrentModificationExceptionPreservesCaseIdAndSequence")
        void concurrentModificationExceptionPreservesCaseIdAndSequence() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString);
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong);
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any);
            when(mockPreparedStatement.executeUpdate()).thenThrow(
                new SQLException("Duplicate entry", "23000"));
            doNothing().when(mockConnection).close();

            WorkflowEvent event = createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null);

            try {
                eventStore.append(event, 42);
            } catch (WorkflowEventStore.ConcurrentModificationException e) {
                assertEquals(TEST_CASE_ID, e.getCaseId());
                assertEquals(42, e.getConflictingSeq());
            }
        }
    }

    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("eventStoreExceptionWithMessageOnly")
        void eventStoreExceptionWithMessageOnly() {
            WorkflowEventStore.EventStoreException e =
                new WorkflowEventStore.EventStoreException("Test error");

            assertEquals("Test error", e.getMessage());
            assertNull(e.getCause());
        }

        @Test
        @DisplayName("eventStoreExceptionWithMessageAndCause")
        void eventStoreExceptionWithMessageAndCause() {
            Throwable cause = new RuntimeException("Root cause");
            WorkflowEventStore.EventStoreException e =
                new WorkflowEventStore.EventStoreException("Test error", cause);

            assertEquals("Test error", e.getMessage());
            assertEquals(cause, e.getCause());
        }

        @Test
        @DisplayName("concurrentModificationExceptionWithMessageAndDetails")
        void concurrentModificationExceptionWithMessageAndDetails() {
            WorkflowEventStore.ConcurrentModificationException e =
                new WorkflowEventStore.ConcurrentModificationException(
                    "Conflict", TEST_CASE_ID, 5);

            assertEquals("Conflict", e.getMessage());
            assertEquals(TEST_CASE_ID, e.getCaseId());
            assertEquals(5, e.getConflictingSeq());
        }
    }

    @Nested
    @DisplayName("Event Serialization")
    class EventSerializationTest {

        @Test
        @DisplayName("serializeEventPayload")
        void serializeEventPayload() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString);
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong);
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any);
            doNothing().when(mockPreparedStatement).executeUpdate();
            doNothing().when(mockConnection).close();

            Map<String, String> complexPayload = Map.of(
                "caseParams", "{'customerId':'123','priority':'high'}",
                "launchedBy", "agent-order-service",
                "metadata", "{'version':'1.0','source':'web-ui'}"
            );

            WorkflowEvent event = new WorkflowEvent(
                UUID.randomUUID().toString(),
                WorkflowEvent.EventType.CASE_STARTED,
                "1.0",
                TEST_SPEC_ID,
                TEST_CASE_ID,
                null,
                BASE_TIMESTAMP,
                complexPayload
            );

            eventStore.append(event, 0);

            verify(mockPreparedStatement).setString(8,
                "{\"caseParams\":\"{'customerId':'123','priority':'high'}\",\"launchedBy\":\"agent-order-service\",\"metadata\":\"{'version':'1.0','source':'web-ui'}\"}");
        }

        @Test
        @DisplayName("serializeEventWithEmptyPayload")
        void serializeEventWithEmptyPayload() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString);
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong);
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any);
            doNothing().when(mockPreparedStatement).executeUpdate();
            doNothing().when(mockConnection).close();

            WorkflowEvent event = new WorkflowEvent(
                UUID.randomUUID().toString(),
                WorkflowEvent.EventType.CASE_STARTED,
                "1.0",
                TEST_SPEC_ID,
                TEST_CASE_ID,
                null,
                BASE_TIMESTAMP,
                Map.of()
            );

            eventStore.append(event, 0);

            verify(mockPreparedStatement).setString(8, "{}");
        }
    }

    @Nested
    @DisplayName("Complex Event Scenarios")
    class ComplexEventScenariosTest {

        @Test
        @DisplayName("loadMultipleEventsInOrder")
        void loadMultipleEventsInOrder() throws Exception {
            // Setup multiple events
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

            // Mock multiple events
            when(mockResultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
            when(mockResultSet.getString("event_id")).thenReturn("event-1", "event-2");
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID, TEST_SPEC_ID);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID, TEST_CASE_ID);
            when(mockResultSet.getString("seq_num")).thenReturn("0", "1");
            when(mockResultSet.getString("event_type")).thenReturn("CASE_STARTED", "WORKITEM_ENABLED");
            when(mockResultSet.getString("schema_version")).thenReturn("1.0", "1.0");
            when(mockResultSet.getTimestamp("event_timestamp")).thenReturn(
                Timestamp.from(BASE_TIMESTAMP),
                Timestamp.from(BASE_TIMESTAMP.plusSeconds(30)));
            when(mockResultSet.getString("payload_json")).thenReturn("{}");

            List<WorkflowEvent> events = eventStore.loadEvents(TEST_CASE_ID);

            assertEquals(2, events.size());
            assertEquals("event-1", events.get(0).getEventId());
            assertEquals("event-2", events.get(1).getEventId());
            assertEquals(WorkflowEvent.EventType.CASE_STARTED, events.get(0).getEventType());
            assertEquals(WorkflowEvent.EventType.WORKITEM_ENABLED, events.get(1).getEventType());
            assertEquals(0, events.get(0).getSequenceNumber());
            assertEquals(1, events.get(1).getSequenceNumber());
        }

        @Test
        @DisplayName("appendNextWithMaxSeqNull")
        void appendNextWithMaxSeqNull() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(false);
            when(mockResultSet.getLong(1)).thenReturn(-1L);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString);
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong);
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any);
            doNothing().when(mockPreparedStatement).executeUpdate();
            doNothing().when(mockConnection).commit();
            doNothing().when(mockConnection).close();
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

            WorkflowEvent event = createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null);
            long sequence = eventStore.appendNext(event);

            assertEquals(0, sequence);
        }

        @Test
        @DisplayName("appendNextWithMaxSeqNotNull")
        void appendNextWithMaxSeqNotNull() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true);
            when(mockResultSet.getLong(1)).thenReturn(5L);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString);
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong);
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any);
            doNothing().when(mockPreparedStatement).executeUpdate();
            doNothing().when(mockConnection).commit();
            doNothing().when(mockConnection).close();
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);

            WorkflowEvent event = createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null);
            long sequence = eventStore.appendNext(event);

            assertEquals(6, sequence);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("appendNextWithRollback")
        void appendNextWithRollback() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true);
            when(mockResultSet.getLong(1)).thenReturn(5L);
            doNothing().when(mockPreparedStatement).setString(anyInt(), anyString);
            doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong);
            doNothing().when(mockPreparedStatement).setObject(anyInt(), any);
            when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("Database error"));
            doNothing().when(mockConnection).rollback();
            doNothing().when(mockConnection).close();

            WorkflowEvent event = createTestEvent(WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null);

            try {
                eventStore.appendNext(event);
                fail("Should have thrown exception");
            } catch (WorkflowEventStore.EventStoreException e) {
                verify(mockConnection, times(1)).rollback();
            }
        }

        @Test
        @DisplayName("loadEventsWithSQLException")
        void loadEventsWithSQLException() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("Connection failed"));
            doNothing().when(mockConnection).close();

            assertThrows(WorkflowEventStore.EventStoreException.class,
                () -> eventStore.loadEvents(TEST_CASE_ID));
        }

        @Test
        @DisplayName("invalidEventTypeThrowsEventStoreException")
        void invalidEventTypeThrowsEventStoreException() throws Exception {
            when(mockDataSource.getConnection()).thenReturn(mockConnection);
            when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);
            when(mockResultSet.next()).thenReturn(true);
            when(mockResultSet.getString("event_id")).thenReturn("event-1");
            when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
            when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
            when(mockResultSet.getString("seq_num")).thenReturn("0");
            when(mockResultSet.getString("event_type")).thenReturn("INVALID_TYPE");
            when(mockResultSet.getString("schema_version")).thenReturn("1.0");
            when(mockResultSet.getTimestamp("event_timestamp")).thenReturn(
                Timestamp.from(BASE_TIMESTAMP));
            when(mockResultSet.getString("payload_json")).thenReturn("{}");
            doNothing().when(mockConnection).close();

            assertThrows(WorkflowEventStore.EventStoreException.class,
                () -> eventStore.loadEvents(TEST_CASE_ID));
        }
    }

    // Helper method to create test events
    private WorkflowEvent createTestEvent(WorkflowEvent.EventType type, String caseId, String workItemId) {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            type,
            "1.0",
            TEST_SPEC_ID,
            caseId,
            workItemId,
            BASE_TIMESTAMP,
            Map.of()
        );
    }
}