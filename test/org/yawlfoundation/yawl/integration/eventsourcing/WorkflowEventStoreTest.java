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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link WorkflowEventStore}.
 * Uses real H2 in-memory database instead of mocks.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class WorkflowEventStoreTest {

    private DataSource dataSource;
    private WorkflowEventStore eventStore;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = EventSourcingTestFixture.createDataSource();
        EventSourcingTestFixture.createSchema(dataSource);
        eventStore = new WorkflowEventStore(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        EventSourcingTestFixture.dropSchema(dataSource);
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTest {

        @Test
        @DisplayName("constructWithDataSource")
        void constructWithDataSource() throws SQLException {
            DataSource ds = EventSourcingTestFixture.createDataSource();
            EventSourcingTestFixture.createSchema(ds);
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
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            eventStore.append(event, 0);

            assertEquals(1, countEvents(dataSource));
        }

        @Test
        @DisplayName("appendNextEventSuccess")
        void appendNextEventSuccess() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            long sequence = eventStore.appendNext(event);

            assertEquals(0, sequence);
            assertEquals(1, countEvents(dataSource));
        }

        @Test
        @DisplayName("appendEventWithInvalidSequenceThrows")
        void appendEventWithInvalidSequenceThrows() {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);

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
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event = new WorkflowEvent(
                UUID.randomUUID().toString(),
                WorkflowEvent.EventType.CASE_STARTED,
                "1.0",
                EventSourcingTestFixture.TEST_SPEC_ID,
                caseId,
                null,
                EventSourcingTestFixture.BASE_TIMESTAMP,
                null
            );

            assertThrows(NullPointerException.class, () -> eventStore.append(event, 0));
        }

        @Test
        @DisplayName("appendMultipleEventsInSequence")
        void appendMultipleEventsInSequence() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            WorkflowEvent event2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "wi-1");
            WorkflowEvent event3 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_STARTED, caseId, "wi-1");

            eventStore.append(event1, 0);
            eventStore.append(event2, 1);
            eventStore.append(event3, 2);

            assertEquals(3, countEvents(dataSource));
        }
    }

    @Nested
    @DisplayName("Load Events")
    class LoadEventsTest {

        @Test
        @DisplayName("loadEventsForCase")
        void loadEventsForCase() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            eventStore.append(event, 0);

            List<WorkflowEvent> events = eventStore.loadEvents(caseId);

            assertEquals(1, events.size());
            assertEquals(caseId, events.get(0).getCaseId());
            assertEquals(WorkflowEvent.EventType.CASE_STARTED, events.get(0).getEventType());
        }

        @Test
        @DisplayName("loadEventsEmptyCase")
        void loadEventsEmptyCase() throws Exception {
            List<WorkflowEvent> events = eventStore.loadEvents("nonexistent-case");

            assertTrue(events.isEmpty());
        }

        @Test
        @DisplayName("loadEventsAsOf")
        void loadEventsAsOf() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            Instant asOf = EventSourcingTestFixture.BASE_TIMESTAMP.plusSeconds(30);
            Instant eventTimestamp = EventSourcingTestFixture.BASE_TIMESTAMP.plusSeconds(15);

            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null, eventTimestamp);
            eventStore.append(event, 0);

            List<WorkflowEvent> events = eventStore.loadEventsAsOf(caseId, asOf);

            assertEquals(1, events.size());
            assertEquals(eventTimestamp, events.get(0).getTimestamp());
        }

        @Test
        @DisplayName("loadEventsSince")
        void loadEventsSince() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            WorkflowEvent event2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "wi-1");
            WorkflowEvent event3 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_STARTED, caseId, "wi-1");
            eventStore.append(event1, 0);
            eventStore.append(event2, 1);
            eventStore.append(event3, 2);

            // Load events since sequence 0 (should get events at seq 1 and 2)
            List<WorkflowEvent> events = eventStore.loadEventsSince(caseId, 0);

            assertEquals(2, events.size());
            assertEquals(WorkflowEvent.EventType.WORKITEM_ENABLED, events.get(0).getEventType());
            assertEquals(WorkflowEvent.EventType.WORKITEM_STARTED, events.get(1).getEventType());
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
                eventStore.loadEventsAsOf("case-id", null));
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
        @DisplayName("concurrentModificationExceptionOnDuplicateSequence")
        void concurrentModificationExceptionOnDuplicateSequence() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            WorkflowEvent event2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);

            eventStore.append(event1, 0);

            assertThrows(WorkflowEventStore.ConcurrentModificationException.class,
                () -> eventStore.append(event2, 0));
        }

        @Test
        @DisplayName("concurrentModificationExceptionPreservesCaseIdAndSequence")
        void concurrentModificationExceptionPreservesCaseIdAndSequence() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            WorkflowEvent event2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);

            eventStore.append(event1, 0);

            try {
                eventStore.append(event2, 0);
                fail("Should have thrown exception");
            } catch (WorkflowEventStore.ConcurrentModificationException e) {
                assertEquals(caseId, e.getCaseId());
                assertEquals(0, e.getConflictingSeq());
            }
        }

        @Test
        @DisplayName("concurrentWritesFromMultipleThreads")
        void concurrentWritesFromMultipleThreads() throws Exception {
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final int threadNum = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String caseId = "case-thread-" + threadNum;
                        WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                            WorkflowEvent.EventType.CASE_STARTED, caseId, null);
                        eventStore.append(event, 0);
                        successCount.incrementAndGet();
                    } catch (WorkflowEventStore.ConcurrentModificationException e) {
                        conflictCount.incrementAndGet();
                    } catch (Exception e) {
                        fail("Unexpected exception: " + e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            assertEquals(threadCount, successCount.get(), "All threads should succeed with unique case IDs");
            assertEquals(0, conflictCount.get(), "No conflicts expected with unique case IDs");
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
            String caseId = "test-case-123";
            WorkflowEventStore.ConcurrentModificationException e =
                new WorkflowEventStore.ConcurrentModificationException(
                    "Conflict", caseId, 5);

            assertEquals("Conflict", e.getMessage());
            assertEquals(caseId, e.getCaseId());
            assertEquals(5, e.getConflictingSeq());
        }
    }

    @Nested
    @DisplayName("Event Serialization")
    class EventSerializationTest {

        @Test
        @DisplayName("serializeEventPayload")
        void serializeEventPayload() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            Map<String, String> complexPayload = Map.of(
                "caseParams", "{'customerId':'123','priority':'high'}",
                "launchedBy", "agent-order-service",
                "metadata", "{'version':'1.0','source':'web-ui'}"
            );

            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null, complexPayload);

            eventStore.append(event, 0);

            List<WorkflowEvent> loaded = eventStore.loadEvents(caseId);
            assertEquals(1, loaded.size());
            assertEquals(complexPayload, loaded.get(0).getPayload());
        }

        @Test
        @DisplayName("serializeEventWithEmptyPayload")
        void serializeEventWithEmptyPayload() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null, Map.of());

            eventStore.append(event, 0);

            List<WorkflowEvent> loaded = eventStore.loadEvents(caseId);
            assertEquals(1, loaded.size());
            assertTrue(loaded.get(0).getPayload().isEmpty());
        }
    }

    @Nested
    @DisplayName("Complex Event Scenarios")
    class ComplexEventScenariosTest {

        @Test
        @DisplayName("loadMultipleEventsInOrder")
        void loadMultipleEventsInOrder() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            WorkflowEvent event2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "wi-1");

            eventStore.append(event1, 0);
            eventStore.append(event2, 1);

            List<WorkflowEvent> events = eventStore.loadEvents(caseId);

            assertEquals(2, events.size());
            // Verify events are returned in order
            assertEquals(WorkflowEvent.EventType.CASE_STARTED, events.get(0).getEventType());
            assertEquals(WorkflowEvent.EventType.WORKITEM_ENABLED, events.get(1).getEventType());
        }

        @Test
        @DisplayName("appendNextWithNoExistingEvents")
        void appendNextWithNoExistingEvents() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            long sequence = eventStore.appendNext(event);

            assertEquals(0, sequence);
        }

        @Test
        @DisplayName("appendNextWithExistingEvents")
        void appendNextWithExistingEvents() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            WorkflowEvent event2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "wi-1");

            eventStore.append(event1, 0);
            long sequence = eventStore.appendNext(event2);

            assertEquals(1, sequence);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTest {

        @Test
        @DisplayName("loadEventsForNonExistentCase")
        void loadEventsForNonExistentCase() throws Exception {
            List<WorkflowEvent> events = eventStore.loadEvents("nonexistent-case");
            assertTrue(events.isEmpty());
        }

        @Test
        @DisplayName("appendEventsToDifferentCases")
        void appendEventsToDifferentCases() throws Exception {
            String case1 = EventSourcingTestFixture.generateCaseId("case-1");
            String case2 = EventSourcingTestFixture.generateCaseId("case-2");

            WorkflowEvent event1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, case1, null);
            WorkflowEvent event2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, case2, null);

            eventStore.append(event1, 0);
            eventStore.append(event2, 0);

            assertEquals(2, countEvents(dataSource));
            assertEquals(1, eventStore.loadEvents(case1).size());
            assertEquals(1, eventStore.loadEvents(case2).size());
        }

        @Test
        @DisplayName("eventsPersistAcrossStoreInstances")
        void eventsPersistAcrossStoreInstances() throws Exception {
            String caseId = EventSourcingTestFixture.generateCaseId();
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null);
            eventStore.append(event, 0);

            WorkflowEventStore newStore = new WorkflowEventStore(dataSource);
            List<WorkflowEvent> events = newStore.loadEvents(caseId);

            assertEquals(1, events.size());
            assertEquals(WorkflowEvent.EventType.CASE_STARTED, events.get(0).getEventType());
        }
    }

    // Helper method for counting events
    private int countEvents(DataSource ds) throws SQLException {
        try (var conn = ds.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery("SELECT COUNT(*) FROM workflow_events")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
