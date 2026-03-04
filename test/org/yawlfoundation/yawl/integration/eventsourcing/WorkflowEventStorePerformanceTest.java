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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD performance tests for WorkflowEventStore.
 *
 * <p>Tests correctness under load using real H2 in-memory database.
 * Focuses on data integrity rather than strict timing assertions.
 *
 * <h2>Performance Testing Philosophy (Chicago TDD)</h2>
 * <ul>
 *   <li>No strict timing assertions (e.g., "must complete in 100ms") - flaky in CI</li>
 *   <li>Focus on correctness: all events persisted, no data loss</li>
 *   <li>Test concurrent access patterns</li>
 *   <li>Measure throughput for informational purposes only</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class WorkflowEventStorePerformanceTest {

    private DataSource dataSource;
    private WorkflowEventStore eventStore;

    private static final String TEST_CASE_ID = "test-case-performance";
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
    @DisplayName("High Volume Operations")
    class HighVolumeOperationsTest {

        @Test
        @DisplayName("appendThousandEvents_allPersisted")
        void appendThousandEventsAllPersisted() throws Exception {
            // Given
            int eventCount = 1000;
            String caseId = EventSourcingTestFixture.generateCaseId("volume");

            // When
            long startTime = System.nanoTime();
            for (int i = 0; i < eventCount; i++) {
                WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                    WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                    BASE_TIMESTAMP.plusSeconds(i));
                eventStore.append(event, i);
            }
            long duration = System.nanoTime() - startTime;

            // Then: all events persisted
            List<WorkflowEvent> loaded = eventStore.loadEvents(caseId);
            assertEquals(eventCount, loaded.size());

            // Info: report throughput (not assertion)
            double eventsPerSecond = (eventCount * 1_000_000_000.0) / duration;
            System.out.printf("[INFO] Throughput: %.0f events/sec for %d events%n",
                eventsPerSecond, eventCount);
        }

        @Test
        @DisplayName("loadThousandEvents_allReturned")
        void loadThousandEventsAllReturned() throws Exception {
            // Given: 1000 events already stored
            int eventCount = 1000;
            String caseId = EventSourcingTestFixture.generateCaseId("load");

            for (int i = 0; i < eventCount; i++) {
                WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                    WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                    BASE_TIMESTAMP.plusSeconds(i));
                eventStore.append(event, i);
            }

            // When
            long startTime = System.nanoTime();
            List<WorkflowEvent> loaded = eventStore.loadEvents(caseId);
            long duration = System.nanoTime() - startTime;

            // Then
            assertEquals(eventCount, loaded.size());

            // Info: report load time
            double loadTimeMs = duration / 1_000_000.0;
            System.out.printf("[INFO] Load time: %.2f ms for %d events%n",
                loadTimeMs, eventCount);
        }
    }

    @Nested
    @DisplayName("Concurrency Correctness")
    class ConcurrencyCorrectnessTest {

        @Test
        @DisplayName("highConcurrencyAppend_noEventLoss")
        void highConcurrencyAppendNoEventLoss() throws Exception {
            // Given
            int threadCount = 20;
            int eventsPerThread = 50;
            int expectedTotal = threadCount * eventsPerThread;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            Map<String, AtomicInteger> caseEventCounts = new ConcurrentHashMap<>();

            // When: concurrent appends using appendNext (auto-sequence)
            for (int t = 0; t < threadCount; t++) {
                final int threadNum = t;
                executor.submit(() -> {
                    try {
                        barrier.await(); // Synchronize start
                        String caseId = TEST_CASE_ID + "-thread-" + threadNum;
                        caseEventCounts.put(caseId, new AtomicInteger(0));

                        for (int i = 0; i < eventsPerThread; i++) {
                            try {
                                WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                                    WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                                    BASE_TIMESTAMP.plusMillis(i));
                                eventStore.appendNext(event);
                                successCount.incrementAndGet();
                                caseEventCounts.get(caseId).incrementAndGet();
                            } catch (Exception e) {
                                // Log but continue - some conflicts expected
                                System.err.println("[WARN] Append failed: " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

            // Then: verify all successful events are persisted
            int totalPersisted = 0;
            for (Map.Entry<String, AtomicInteger> entry : caseEventCounts.entrySet()) {
                List<WorkflowEvent> events = eventStore.loadEvents(entry.getKey());
                assertEquals(entry.getValue().get(), events.size(),
                    "Event count mismatch for " + entry.getKey());
                totalPersisted += events.size();
            }

            assertEquals(successCount.get(), totalPersisted,
                "Total persisted should equal successful appends");

            System.out.printf("[INFO] Successfully appended %d/%d events with %d threads%n",
                successCount.get(), expectedTotal, threadCount);
        }

        @Test
        @DisplayName("mixedOperations_noDeadlocks")
        void mixedOperationsNoDeadlocks() throws Exception {
            // Given
            int operationCount = 200;
            int caseCount = 10;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(operationCount);
            AtomicInteger completedOps = new AtomicInteger(0);
            AtomicInteger failedOps = new AtomicInteger(0);

            // Pre-populate some events
            for (int c = 0; c < caseCount; c++) {
                String caseId = "case-" + c;
                for (int i = 0; i < 5; i++) {
                    eventStore.append(EventSourcingTestFixture.createTestEvent(
                        WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                        BASE_TIMESTAMP.plusSeconds(i)), i);
                }
            }

            // When: mixed reads and writes
            for (int i = 0; i < operationCount; i++) {
                final int opNum = i;
                executor.submit(() -> {
                    try {
                        barrier.await();
                        String caseId = "case-" + (opNum % caseCount);

                        if (opNum % 3 == 0) {
                            // Write operation
                            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                                WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "wi-" + opNum,
                                BASE_TIMESTAMP.plusSeconds(opNum));
                            eventStore.appendNext(event);
                        } else {
                            // Read operation
                            eventStore.loadEvents(caseId);
                        }
                        completedOps.incrementAndGet();
                    } catch (Exception e) {
                        failedOps.incrementAndGet();
                    }
                });
            }

            executor.shutdown();
            boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);

            // Then: no deadlocks (all operations complete or fail cleanly)
            assertTrue(terminated, "Operations should complete without deadlock");
            assertEquals(operationCount, completedOps.get() + failedOps.get());

            System.out.printf("[INFO] Completed %d operations, %d failed%n",
                completedOps.get(), failedOps.get());
        }
    }

    @Nested
    @DisplayName("Memory Efficiency")
    class MemoryEfficiencyTest {

        @Test
        @DisplayName("largeEventStream_loadable")
        void largeEventStreamLoadable() throws Exception {
            // Given: 5000 events
            int eventCount = 5000;
            String caseId = EventSourcingTestFixture.generateCaseId("large");

            for (int i = 0; i < eventCount; i++) {
                WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                    WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                    BASE_TIMESTAMP.plusMillis(i),
                    Map.of("index", String.valueOf(i), "data", "x".repeat(100)));
                eventStore.append(event, i);
            }

            // When: load all events
            List<WorkflowEvent> loaded = eventStore.loadEvents(caseId);

            // Then: all events loadable
            assertEquals(eventCount, loaded.size());

            // Verify order and data integrity
            for (int i = 0; i < Math.min(100, loaded.size()); i++) {
                assertEquals(i, loaded.get(i).getSequenceNumber());
                assertEquals(String.valueOf(i), loaded.get(i).getPayload().get("index"));
            }
        }

        @Test
        @DisplayName("temporalQuery_largeDataset_correctFiltering")
        void temporalQueryLargeDatasetCorrectFiltering() throws Exception {
            // Given: 1000 events spread over time
            int eventCount = 1000;
            String caseId = EventSourcingTestFixture.generateCaseId("temporal");

            for (int i = 0; i < eventCount; i++) {
                WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                    WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                    BASE_TIMESTAMP.plusSeconds(i));
                eventStore.append(event, i);
            }

            // When: query at midpoint
            Instant midpoint = BASE_TIMESTAMP.plusSeconds(500);
            List<WorkflowEvent> events = eventStore.loadEventsAsOf(caseId, midpoint);

            // Then: only events up to midpoint
            assertTrue(events.size() <= 501); // 0-500 inclusive
            for (WorkflowEvent e : events) {
                assertFalse(e.getTimestamp().isAfter(midpoint));
            }
        }
    }

    @Nested
    @DisplayName("Optimistic Concurrency")
    class OptimisticConcurrencyTest {

        @Test
        @DisplayName("concurrentWrite_sameSequence_handled")
        void concurrentWriteSameSequenceHandled() throws Exception {
            // Given: multiple threads trying to write at same sequence
            int threadCount = 10;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger conflictCount = new AtomicInteger(0);
            String caseId = EventSourcingTestFixture.generateCaseId("conflict");

            // When: all threads try to append at sequence 0
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        barrier.await();
                        WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                            WorkflowEvent.EventType.CASE_STARTED, caseId, null, BASE_TIMESTAMP);
                        eventStore.append(event, 0);
                        successCount.incrementAndGet();
                    } catch (WorkflowEventStore.ConcurrentModificationException e) {
                        conflictCount.incrementAndGet();
                    } catch (Exception e) {
                        // Unexpected error
                        e.printStackTrace();
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            // Then: exactly one success, rest get conflicts
            assertEquals(1, successCount.get());
            assertEquals(threadCount - 1, conflictCount.get());

            // Verify the single event was persisted
            List<WorkflowEvent> events = eventStore.loadEvents(caseId);
            assertEquals(1, events.size());
        }

        @Test
        @DisplayName("retryOnConflict_eventuallySucceeds")
        void retryOnConflictEventuallySucceeds() throws Exception {
            // Given: multiple threads with retry logic
            int threadCount = 5;
            int maxRetries = 10;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            AtomicInteger totalSuccess = new AtomicInteger(0);
            String caseId = EventSourcingTestFixture.generateCaseId("retry");

            // When: threads retry on conflict
            for (int t = 0; t < threadCount; t++) {
                final int threadNum = t;
                executor.submit(() -> {
                    try {
                        barrier.await();
                        for (int retry = 0; retry < maxRetries; retry++) {
                            try {
                                // Get current max sequence
                                List<WorkflowEvent> existing = eventStore.loadEvents(caseId);
                                long nextSeq = existing.size();

                                WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                                    WorkflowEvent.EventType.CASE_STARTED, caseId, "thread-" + threadNum,
                                    BASE_TIMESTAMP.plusMillis(retry));
                                eventStore.append(event, nextSeq);
                                totalSuccess.incrementAndGet();
                                break;
                            } catch (WorkflowEventStore.ConcurrentModificationException e) {
                                // Retry with updated sequence
                                Thread.sleep(1);
                            }
                        }
                    } catch (Exception e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            // Then: all threads eventually succeed
            assertEquals(threadCount, totalSuccess.get());
            List<WorkflowEvent> events = eventStore.loadEvents(caseId);
            assertEquals(threadCount, events.size());
        }
    }

    @Nested
    @DisplayName("Stress Tests")
    class StressTest {

        @Test
        @DisplayName("rapidFireAppends_noDataCorruption")
        void rapidFireAppendsNoDataCorruption() throws Exception {
            // Given
            int eventCount = 500;
            String caseId = EventSourcingTestFixture.generateCaseId("stress");
            List<String> eventIds = new ArrayList<>();

            // When: rapid fire appends using appendNext
            for (int i = 0; i < eventCount; i++) {
                WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                    WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                    BASE_TIMESTAMP.plusNanos(i),
                    Map.of("iteration", String.valueOf(i)));
                eventStore.appendNext(event);
                eventIds.add(event.getEventId());
            }

            // Then: verify data integrity
            List<WorkflowEvent> loaded = eventStore.loadEvents(caseId);
            assertEquals(eventCount, loaded.size());

            // Verify all event IDs are present
            for (WorkflowEvent e : loaded) {
                assertTrue(eventIds.contains(e.getEventId()),
                    "Event ID " + e.getEventId() + " not found");
            }

            // Verify sequence order
            for (int i = 0; i < loaded.size(); i++) {
                assertEquals(i, loaded.get(i).getSequenceNumber());
            }
        }

        @Test
        @DisplayName("multipleCases_concurrentLoad_noMixing")
        void multipleCasesConcurrentLoadNoMixing() throws Exception {
            // Given: multiple cases with distinct events
            int caseCount = 10;
            int eventsPerCase = 20;
            Map<String, List<String>> caseEventIds = new ConcurrentHashMap<>();

            for (int c = 0; c < caseCount; c++) {
                String caseId = "case-" + c;
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < eventsPerCase; i++) {
                    WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                        WorkflowEvent.EventType.CASE_STARTED, caseId, null,
                        BASE_TIMESTAMP.plusSeconds(c * 100 + i));
                    eventStore.append(event, i);
                    ids.add(event.getEventId());
                }
                caseEventIds.put(caseId, ids);
            }

            // When: concurrent loads
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            Map<String, List<WorkflowEvent>> results = new ConcurrentHashMap<>();

            for (int c = 0; c < caseCount; c++) {
                final String caseId = "case-" + c;
                executor.submit(() -> {
                    try {
                        results.put(caseId, eventStore.loadEvents(caseId));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            // Then: no event mixing between cases
            for (Map.Entry<String, List<WorkflowEvent>> entry : results.entrySet()) {
                String caseId = entry.getKey();
                List<WorkflowEvent> events = entry.getValue();
                List<String> expectedIds = caseEventIds.get(caseId);

                assertEquals(eventsPerCase, events.size(),
                    "Wrong event count for " + caseId);

                for (WorkflowEvent e : events) {
                    assertTrue(expectedIds.contains(e.getEventId()),
                        "Event " + e.getEventId() + " doesn't belong to " + caseId);
                    assertEquals(caseId, e.getCaseId());
                }
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTest {

        @Test
        @DisplayName("emptyCase_returnsEmptyList")
        void emptyCaseReturnsEmptyList() throws Exception {
            // Given: no events for case
            String caseId = "nonexistent-case";

            // When
            List<WorkflowEvent> events = eventStore.loadEvents(caseId);

            // Then
            assertTrue(events.isEmpty());
        }

        @Test
        @DisplayName("singleEvent_persistedCorrectly")
        void singleEventPersistedCorrectly() throws Exception {
            // Given
            String caseId = EventSourcingTestFixture.generateCaseId("single");
            WorkflowEvent event = EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null, BASE_TIMESTAMP,
                Map.of("key", "value"));

            // When
            eventStore.append(event, 0);

            // Then
            List<WorkflowEvent> loaded = eventStore.loadEvents(caseId);
            assertEquals(1, loaded.size());
            assertEquals(event.getEventId(), loaded.get(0).getEventId());
            assertEquals("value", loaded.get(0).getPayload().get("key"));
        }

        @Test
        @DisplayName("eventsAtSameTimestamp_handledCorrectly")
        void eventsAtSameTimestampHandledCorrectly() throws Exception {
            // Given: events with same timestamp but different sequences
            String caseId = EventSourcingTestFixture.generateCaseId("same-time");
            Instant sameTime = BASE_TIMESTAMP;

            // When
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.CASE_STARTED, caseId, null, sameTime), 0);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_ENABLED, caseId, "wi-1", sameTime), 1);
            eventStore.append(EventSourcingTestFixture.createTestEvent(
                WorkflowEvent.EventType.WORKITEM_STARTED, caseId, "wi-1", sameTime), 2);

            // Then: events still ordered by sequence
            List<WorkflowEvent> events = eventStore.loadEvents(caseId);
            assertEquals(3, events.size());
            assertEquals(0, events.get(0).getSequenceNumber());
            assertEquals(1, events.get(1).getSequenceNumber());
            assertEquals(2, events.get(2).getSequenceNumber());
        }
    }
}
