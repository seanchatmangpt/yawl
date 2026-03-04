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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for WorkflowEventStore optimization scenarios.
 *
 * <p>Tests real database operations with H2 to verify:
 * <ul>
 *   <li>Sequential append performance</li>
 *   <li>Concurrent append correctness</li>
 *   <li>Temporal query accuracy</li>
 *   <li>Delta query efficiency</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class WorkflowEventStoreOptimizationTest {

    private DataSource dataSource;
    private WorkflowEventStore eventStore;

    private static final String TEST_CASE_ID = "optimization-test-case";
    private static final String TEST_SPEC_ID = "OrderFulfillment:1.0";
    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-02-17T10:00:00Z");

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
    @DisplayName("Sequential Operations")
    class SequentialOperationsTest {

        @Test
        @DisplayName("appendMultipleEvents_sequentialOrder")
        void appendMultipleEventsSequentialOrder() throws Exception {
            // Given: multiple events
            int eventCount = 10;
            List<WorkflowEvent> events = createTestEvents(TEST_CASE_ID, eventCount);

            // When: append sequentially
            for (int i = 0; i < events.size(); i++) {
                eventStore.append(events.get(i), i);
            }

            // Then: all events persisted in order
            List<WorkflowEvent> loaded = eventStore.loadEvents(TEST_CASE_ID);
            assertEquals(eventCount, loaded.size());

            for (int i = 0; i < eventCount; i++) {
                assertEquals(i, loaded.get(i).getSequenceNumber());
            }
        }

        @Test
        @DisplayName("appendNext_autoSequence")
        void appendNextAutoSequence() throws Exception {
            // Given: multiple events
            int eventCount = 5;
            List<WorkflowEvent> events = createTestEvents(TEST_CASE_ID, eventCount);

            // When: append with auto-sequence
            for (WorkflowEvent event : events) {
                long seqNum = eventStore.appendNext(event);
                assertTrue(seqNum >= 0);
            }

            // Then: all events loaded correctly
            List<WorkflowEvent> loaded = eventStore.loadEvents(TEST_CASE_ID);
            assertEquals(eventCount, loaded.size());
        }

        @Test
        @DisplayName("appendDuplicateSequence_throwsConcurrentModification")
        void appendDuplicateSequenceThrowsConcurrentModification() throws Exception {
            // Given: event at sequence 0
            WorkflowEvent event1 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);
            eventStore.append(event1, 0);

            // When: attempt to append another event at same sequence
            WorkflowEvent event2 = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP.plusSeconds(10));

            // Then: concurrent modification exception
            assertThrows(WorkflowEventStore.ConcurrentModificationException.class,
                () -> eventStore.append(event2, 0));
        }
    }

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentOperationsTest {

        @Test
        @DisplayName("concurrentAppends_differentCases_noConflict")
        void concurrentAppendsDifferentCasesNoConflict() throws Exception {
            // Given: multiple cases being appended concurrently
            int threadCount = 10;
            int eventsPerThread = 10;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);

            // When: concurrent appends to different cases
            for (int t = 0; t < threadCount; t++) {
                final int threadNum = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String caseId = TEST_CASE_ID + "-thread-" + threadNum;
                        for (int i = 0; i < eventsPerThread; i++) {
                            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                                WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                                BASE_TIMESTAMP.plusSeconds(i));
                            eventStore.append(event, i);
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Expected for some conflicts
                    }
                });
            }

            startLatch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            // Then: all events persisted
            assertEquals(threadCount * eventsPerThread, successCount.get());

            // Verify each case has correct event count
            for (int t = 0; t < threadCount; t++) {
                String caseId = TEST_CASE_ID + "-thread-" + t;
                List<WorkflowEvent> events = eventStore.loadEvents(caseId);
                assertEquals(eventsPerThread, events.size());
            }
        }

        @Test
        @DisplayName("concurrentAppends_sameCase_handledCorrectly")
        void concurrentAppendsSameCaseHandledCorrectly() throws Exception {
            // Given: multiple threads writing to same case
            int threadCount = 5;
            int attemptsPerThread = 3;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);

            // When: concurrent appends to same case
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < attemptsPerThread; i++) {
                            try {
                                // Use appendNext which handles sequence internally
                                WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                                    WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null,
                                    BASE_TIMESTAMP.plusSeconds(i));
                                eventStore.appendNext(event);
                                successCount.incrementAndGet();
                            } catch (Exception e) {
                                // Conflict is expected for optimistic concurrency
                                conflictCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            startLatch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            // Then: total events equals successful appends
            List<WorkflowEvent> loaded = eventStore.loadEvents(TEST_CASE_ID);
            assertEquals(successCount.get(), loaded.size());
        }
    }

    @Nested
    @DisplayName("Temporal Queries")
    class TemporalQueriesTest {

        @Test
        @DisplayName("loadEventsAsOf_filtersCorrectly")
        void loadEventsAsOfFiltersCorrectly() throws Exception {
            // Given: events at different timestamps
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP), 0);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, TEST_CASE_ID, "wi-1", BASE_TIMESTAMP.plusSeconds(30)), 1);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_COMPLETED, TEST_CASE_ID, "wi-1", BASE_TIMESTAMP.plusSeconds(60)), 2);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_COMPLETED, TEST_CASE_ID, null, BASE_TIMESTAMP.plusSeconds(90)), 3);

            // When: query at different points in time
            List<WorkflowEvent> atStart = eventStore.loadEventsAsOf(TEST_CASE_ID, BASE_TIMESTAMP);
            List<WorkflowEvent> atMiddle = eventStore.loadEventsAsOf(TEST_CASE_ID, BASE_TIMESTAMP.plusSeconds(45));
            List<WorkflowEvent> atEnd = eventStore.loadEventsAsOf(TEST_CASE_ID, BASE_TIMESTAMP.plusSeconds(120));

            // Then: correct filtering
            assertEquals(1, atStart.size());
            assertEquals(2, atMiddle.size());
            assertEquals(4, atEnd.size());
        }

        @Test
        @DisplayName("loadEventsSince_deltaQuery")
        void loadEventsSinceDeltaQuery() throws Exception {
            // Given: events with sequence numbers
            for (int i = 0; i < 5; i++) {
                eventStore.append(EventSourcingTestFixture.createTestEvent(
                    WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null,
                    BASE_TIMESTAMP.plusSeconds(i * 10)), i);
            }

            // When: load events since sequence 2
            List<WorkflowEvent> delta = eventStore.loadEventsSince(TEST_CASE_ID, 2);

            // Then: only events after sequence 2
            assertEquals(2, delta.size());
            assertEquals(3, delta.get(0).getSequenceNumber());
            assertEquals(4, delta.get(1).getSequenceNumber());
        }
    }

    @Nested
    @DisplayName("Load Case IDs")
    class LoadCaseIdsTest {

        @Test
        @DisplayName("loadCaseIds_forSpec")
        void loadCaseIdsForSpec() throws Exception {
            // Given: multiple cases for same spec
            String specId = TEST_SPEC_ID;
            String case1 = "case-1";
            String case2 = "case-2";
            String case3 = "case-3";

            eventStore.append(EventSourcingTestFixture.createTestEventWithSpec(
                WorkflowEvent.EventType.CASE_STARTED, specId, case1, null), 0);
            eventStore.append(EventSourcingTestFixture.createTestEventWithSpec(
                WorkflowEvent.EventType.CASE_STARTED, specId, case2, null), 0);
            eventStore.append(EventSourcingTestFixture.createTestEventWithSpec(
                WorkflowEvent.EventType.CASE_STARTED, specId, case3, null), 0);

            // When
            List<String> caseIds = eventStore.loadCaseIds(specId);

            // Then
            assertEquals(3, caseIds.size());
            assertTrue(caseIds.contains(case1));
            assertTrue(caseIds.contains(case2));
            assertTrue(caseIds.contains(case3));
        }

        @Test
        @DisplayName("loadCaseIds_emptyForUnknownSpec")
        void loadCaseIdsEmptyForUnknownSpec() throws Exception {
            // Given: no events for spec
            String unknownSpec = "UnknownSpec:1.0";

            // When
            List<String> caseIds = eventStore.loadCaseIds(unknownSpec);

            // Then
            assertTrue(caseIds.isEmpty());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTest {

        @Test
        @DisplayName("loadEvents_blankCaseId_throws")
        void loadEventsBlankCaseIdThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                eventStore.loadEvents(""));
        }

        @Test
        @DisplayName("loadEventsAsOf_nullAsOf_throws")
        void loadEventsAsOfNullAsOfThrows() {
            assertThrows(NullPointerException.class, () ->
                eventStore.loadEventsAsOf(TEST_CASE_ID, null));
        }

        @Test
        @DisplayName("append_nullEvent_throws")
        void appendNullEventThrows() {
            assertThrows(NullPointerException.class, () ->
                eventStore.append(null, 0));
        }

        @Test
        @DisplayName("append_negativeSequence_throws")
        void appendNegativeSequenceThrows() {
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP);

            assertThrows(IllegalArgumentException.class, () ->
                eventStore.append(event, -1));
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTest {

        @Test
        @DisplayName("fullLifecycle_persistAndReplay")
        void fullLifecyclePersistAndReplay() throws Exception {
            // Given: complete case lifecycle
            String caseId = EventSourcingTestFixture.generateCaseId("lifecycle");

            // When: persist full lifecycle
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

            // Then: verify all events
            List<WorkflowEvent> loaded = eventStore.loadEvents(caseId);
            assertEquals(5, loaded.size());

            assertEquals(WorkflowEvent.EventType.CASE_STARTED, loaded.get(0).getEventType());
            assertEquals(WorkflowEvent.EventType.WORKITEM_ENABLED, loaded.get(1).getEventType());
            assertEquals(WorkflowEvent.EventType.WORKITEM_STARTED, loaded.get(2).getEventType());
            assertEquals(WorkflowEvent.EventType.WORKITEM_COMPLETED, loaded.get(3).getEventType());
            assertEquals(WorkflowEvent.EventType.CASE_COMPLETED, loaded.get(4).getEventType());
        }

        @Test
        @DisplayName("eventPayload_serializedCorrectly")
        void eventPayloadSerializedCorrectly() throws Exception {
            // Given: event with payload
            Map<String, String> payload = Map.of(
                "orderId", "ORDER-123",
                "customerId", "CUST-456",
                "priority", "HIGH"
            );

            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, TEST_CASE_ID, null, BASE_TIMESTAMP, payload);
            eventStore.append(event, 0);

            // When
            List<WorkflowEvent> loaded = eventStore.loadEvents(TEST_CASE_ID);

            // Then: payload preserved
            assertEquals(1, loaded.size());
            Map<String, String> loadedPayload = loaded.get(0).getPayload();
            assertEquals("ORDER-123", loadedPayload.get("orderId"));
            assertEquals("CUST-456", loadedPayload.get("customerId"));
            assertEquals("HIGH", loadedPayload.get("priority"));
        }
    }

    // Helper methods

    private List<WorkflowEvent> createTestEvents(String caseId, int count) {
        List<WorkflowEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(new WorkflowEvent(
                UUID.randomUUID().toString(),
                WorkflowEvent.EventType.CASE_STARTED,
                WorkflowEvent.SCHEMA_VERSION,
                TEST_SPEC_ID,
                caseId,
                null,
                BASE_TIMESTAMP.plusSeconds(i),
                Map.of("seq", String.valueOf(i))
            ));
        }
        return events;
    }
}
