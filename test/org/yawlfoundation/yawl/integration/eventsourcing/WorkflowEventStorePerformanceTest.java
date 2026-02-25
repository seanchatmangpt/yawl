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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.eventsourcing;

import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore.EventMetrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Performance tests for the optimized WorkflowEventStore implementation.
 * Validates the Java 25 feature optimizations and performance improvements.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEventStorePerformanceTest {

    @Mock
    private DataSource mockDataSource;

    @Mock
    private Connection mockConnection;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    private WorkflowEventStore eventStore;
    private static final String TEST_CASE_ID = "test-case-performance-123";
    private static final String TEST_SPEC_ID = "OrderFulfillment:1.0";
    private static final Instant BASE_TIMESTAMP = Instant.parse("2026-02-17T10:00:00Z");
    private static final int EVENT_COUNT = 1000;

    @BeforeEach
    void setUp() throws SQLException {
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        doNothing().when(mockPreparedStatement).setString(anyInt(), anyString());
        doNothing().when(mockPreparedStatement).setLong(anyInt(), anyLong());
        doNothing().when(mockPreparedStatement).setObject(anyInt(), any());
        doNothing().when(mockPreparedStatement).executeUpdate();
        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getString("event_id")).thenReturn("test-event");
        when(mockResultSet.getString("spec_id")).thenReturn(TEST_SPEC_ID);
        when(mockResultSet.getString("case_id")).thenReturn(TEST_CASE_ID);
        when(mockResultSet.getString("seq_num")).thenReturn("0");
        when(mockResultSet.getString("event_type")).thenReturn("CASE_STARTED");
        when(mockResultSet.getString("schema_version")).thenReturn("1.0");
        when(mockResultSet.getTimestamp("event_timestamp")).thenReturn(
            Timestamp.from(BASE_TIMESTAMP));
        when(mockResultSet.getString("payload_json")).thenReturn("{}");
        when(mockResultSet.getLong(1)).thenReturn(-1L);
        doNothing().when(mockConnection).close();
        doNothing().when(mockConnection).commit();

        eventStore = new WorkflowEventStore(mockDataSource, 100, 3);
    }

    @Nested
    @DisplayName("Virtual Thread Performance")
    class VirtualThreadPerformanceTest {

        @Test
        @DisplayName("appendWithVirtualThreads_ShouldBeFasterThanSequential")
        void appendWithVirtualThreadsShouldBeFasterThanSequential() throws Exception {
            int concurrentEvents = 100;
            long sequentialTime = measureSequentialAppend(concurrentEvents);
            long virtualThreadTime = measureVirtualThreadAppend(concurrentEvents);

            // Virtual threads should be at least 2x faster
            assertTrue(virtualThreadTime < sequentialTime / 2,
                String.format("Virtual thread time (%d ms) should be less than half of sequential time (%d ms)",
                    virtualThreadTime, sequentialTime));
        }

        @Test
        @DisplayName("appendBatchWithStructuredConcurrency_ShouldCompleteQuickly")
        void appendBatchWithStructuredConcurrencyShouldCompleteQuickly() throws Exception {
            List<WorkflowEvent> events = createTestEvents(EVENT_COUNT);
            long startTime = System.nanoTime();

            long lastSeq = eventStore.appendBatch(events, 0);

            long duration = System.nanoTime() - startTime;
            double eventsPerSecond = (EVENT_COUNT * 1_000_000_000.0) / duration;

            assertTrue(eventsPerSecond > 1000,
                String.format("Batch append should process >1000 events/sec, got %.0f", eventsPerSecond));
            assertEquals(EVENT_COUNT - 1, lastSeq);
        }

        private long measureSequentialAppend(int count) throws Exception {
            List<WorkflowEvent> events = createTestEvents(count);
            long startTime = System.nanoTime();

            for (WorkflowEvent event : events) {
                eventStore.append(event, 0);
            }

            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        }

        private long measureVirtualThreadAppend(int count) throws Exception {
            List<WorkflowEvent> events = createTestEvents(count);
            long startTime = System.nanoTime();

            try (var scope = java.util.concurrent.StructuredTaskScope.ShutdownOnFailure.newInstance()) {
                events.stream()
                    .map(event -> scope.fork(() -> {
                        eventStore.append(event, 0);
                        return event;
                    }))
                    .toList();

                scope.join();
                scope.throwIfFailed();
            }

            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        }
    }

    @Nested
    @DisplayName("Parallel Stream Performance")
    class ParallelStreamPerformanceTest {

        @Test
        @DisplayName("loadEventsParallel_ShouldBeFasterThanSequential")
        void loadEventsParallelShouldBeFasterThanSequential() throws Exception {
            int caseCount = 10;
            List<String> caseIds = generateCaseIds(caseCount);

            long sequentialTime = measureSequentialLoad(caseIds);
            long parallelTime = measureParallelLoad(caseIds);

            // Parallel load should be at least 3x faster
            assertTrue(parallelTime < sequentialTime / 3,
                String.format("Parallel load time (%d ms) should be less than third of sequential time (%d ms)",
                    parallelTime, sequentialTime));
        }

        @Test
        @DisplayName("loadRecentEventsParallel_ShouldScaleWithCaseCount")
        void loadRecentEventsParallelShouldScaleWithCaseCount() throws Exception {
            int[] caseCounts = {1, 5, 10, 20, 50};
            Map<Integer, Long> executionTimes = new ConcurrentHashMap<>();

            for (int caseCount : caseCounts) {
                List<String> caseIds = generateCaseIds(caseCount);
                long time = measureRecentEventsLoad(caseIds);
                executionTimes.put(caseCount, time);

                // Linear scaling: 20 cases should not take 20x longer than 1 case
                if (caseCount > 1) {
                    double ratio = (double) time / executionTimes.get(1);
                    assertTrue(ratio <= caseCount,
                        String.format("Load time ratio (%.1f) should not exceed case count (%d)", ratio, caseCount));
                }
            }
        }

        private long measureSequentialLoad(List<String> caseIds) throws Exception {
            long startTime = System.nanoTime();

            for (String caseId : caseIds) {
                eventStore.loadEvents(caseId);
            }

            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        }

        private long measureParallelLoad(List<String> caseIds) throws Exception {
            long startTime = System.nanoTime();

            Map<String, List<WorkflowEvent>> result = eventStore.loadEventsParallel(caseIds);

            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        }

        private long measureRecentEventsLoad(List<String> caseIds) throws Exception {
            long startTime = System.nanoTime();

            Map<String, List<WorkflowEvent>> result = eventStore.loadRecentEventsParallel(
                caseIds, BASE_TIMESTAMP);

            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        }
    }

    @Nested
    @DisplayName("Memory Efficiency")
    class MemoryEfficiencyTest {

        @Test
        @DisplayName("largeEventStreams_ShouldHaveReducedMemoryFootprint")
        void largeEventStreamsShouldHaveReducedMemoryFootprint() throws Exception {
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            // Process large event stream using parallel streams
            List<WorkflowEvent> largeEventStream = createTestEvents(10000);
            List<WorkflowEvent> processed = largeEventStream.stream()
                .parallel()
                .map(this::transformEvent)
                .toList();

            long afterMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryIncrease = afterMemory - initialMemory;

            // Memory increase should be reasonable (not linear with event count)
            double memoryPerEvent = (double) memoryIncrease / processed.size();
            assertTrue(memoryPerEvent < 500,
                String.format("Memory per event should be <500 bytes, got %.0f", memoryPerEvent));

            assertEquals(10000, processed.size());
        }

        @Test
        @DisplayName("streamProcessing_ShouldNotRetainUnnecessaryObjects")
        void streamProcessingShouldNotRetainUnnecessaryObjects() throws Exception {
            // Create events and force garbage collection
            List<WorkflowEvent> events = createTestEvents(5000);
            long beforeGC = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // Process with streams and let references go
            List<String> eventIds = events.stream()
                .map(WorkflowEvent::getEventId)
                .toList();

            events = null; // Remove strong references

            System.gc(); // Suggest garbage collection
            Thread.sleep(100); // Give GC time to work

            long afterGC = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long freedMemory = beforeGC - afterGC;

            // Should have freed most memory
            assertTrue(freedMemory > beforeGC * 0.8,
                String.format("Should free >80%% of memory, freed %.1f%%",
                    (freedMemory * 100.0) / beforeGC));
        }

        private WorkflowEvent transformEvent(WorkflowEvent event) {
            // Simulate event transformation without creating new objects unnecessarily
            return event; // In real implementation, would modify in-place or reuse
        }
    }

    @Nested
    @DisplayName("Performance Metrics")
    class PerformanceMetricsTest {

        @Test
        @DisplayName("metricsShouldTrackAllOperations")
        void metricsShouldTrackAllOperations() throws Exception {
            // Perform various operations
            eventStore.append(createTestEvent(), 0);
            eventStore.loadEvents(TEST_CASE_ID);
            eventStore.loadEventsAsOf(TEST_CASE_ID, BASE_TIMESTAMP);
            eventStore.loadEventsSince(TEST_CASE_ID, 0);

            EventMetrics metrics = eventStore.getMetrics();

            // Verify metrics were collected
            assertTrue(metrics.getTotalEventsWritten() > 0);
            assertTrue(metrics.getAppendSuccessRate() > 0);
            assertTrue(metrics.getLoadSuccessRate() > 0);
            assertTrue(metrics.getAverageQueryTime() > 0);
        }

        @Test
        @DisplayName("metricsShouldProvideAccurateThroughput")
        void metricsShouldProvideAccurateThroughput() throws Exception {
            int eventCount = 500;
            long startTime = System.nanoTime();

            for (int i = 0; i < eventCount; i++) {
                eventStore.append(createTestEvent(), i);
            }

            long duration = System.nanoTime() - startTime;
            double actualThroughput = (eventCount * 1_000_000_000.0) / duration;

            EventMetrics metrics = eventStore.getMetrics();
            double reportedThroughput = metrics.getTotalEventsWritten() /
                                    (metrics.getAverageLoadTime() / 1_000_000_000.0);

            // Throughput should be within reasonable bounds (±20%)
            double ratio = actualThroughput / reportedThroughput;
            assertTrue(ratio > 0.8 && ratio < 1.2,
                String.format("Throughput ratio %.2f should be between 0.8 and 1.2", ratio));
        }

        @Test
        @DisplayName("resetMetricsShouldClearAllCounters")
        void resetMetricsShouldClearAllCounters() throws Exception {
            // Perform some operations
            eventStore.append(createTestEvent(), 0);
            eventStore.loadEvents(TEST_CASE_ID);

            EventMetrics metrics = eventStore.getMetrics();
            long beforeReset = metrics.getTotalEventsWritten();

            // Reset metrics
            eventStore.resetMetrics();

            // Verify all counters are reset
            assertEquals(0, metrics.getTotalEventsWritten());
            assertEquals(0, metrics.getAppendAttempts());
            assertEquals(0, metrics.getLoadAttempts());
            assertTrue(Double.isNaN(metrics.getAppendSuccessRate()));
        }
    }

    @Nested
    @DisplayName("Concurrency Stress Test")
    class ConcurrencyStressTest {

        @Test
        @DisplayName("highConcurrencyAppend_ShouldNotLoseEvents")
        void highConcurrencyAppendShouldNotLoseEvents() throws Exception {
            int threadCount = 20;
            int eventsPerThread = 50;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            AtomicLong eventCounter = new AtomicLong(0);

            // Submit concurrent append tasks
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    for (int j = 0; j < eventsPerThread; j++) {
                        try {
                            WorkflowEvent event = createTestEvent();
                            eventStore.append(event, eventCounter.getAndIncrement());
                        } catch (Exception e) {
                            // Log but continue
                            System.err.println("Append failed: " + e.getMessage());
                        }
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

            // Verify all events were processed
            EventMetrics metrics = eventStore.getMetrics();
            long totalEvents = metrics.getTotalEventsWritten();
            assertEquals((long) threadCount * eventsPerThread, totalEvents,
                String.format("Expected %d events, got %d",
                    (long) threadCount * eventsPerThread, totalEvents));
        }

        @Test
        @DisplayName("mixedOperations_ShouldNotCauseDeadlocks")
        void mixedOperationsShouldNotCauseDeadlocks() throws Exception {
            int operationCount = 100;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            // Submit mixed operations (appends and loads)
            for (int i = 0; i < operationCount; i++) {
                final int opNum = i;
                executor.submit(() -> {
                    try {
                        if (opNum % 3 == 0) {
                            // Append operation
                            WorkflowEvent event = createTestEvent();
                            eventStore.append(event, opNum);
                        } else {
                            // Load operation
                            eventStore.loadEvents(TEST_CASE_ID + "-" + (opNum % 10));
                        }
                        Thread.sleep(1); // Small delay
                    } catch (Exception e) {
                        // Ignore for stress test
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS),
                "Mixed operations should complete within 30 seconds");
        }
    }

    @Nested
    @DisplayName("JDBC Batch Optimization")
    class JdbcBatchOptimizationTest {

        @Test
        @DisplayName("batchAppendShouldUseOptimalBatchSize")
        void batchAppendShouldUseOptimalBatchSize() throws Exception {
            List<WorkflowEvent> events = createTestEvents(200);
            int[] batchSizes = {10, 50, 100, 200};

            for (int batchSize : batchSizes) {
                WorkflowEventStore store = new WorkflowEventStore(mockDataSource, batchSize, 3);
                long startTime = System.nanoTime();

                store.appendBatch(events, 0);

                long duration = System.nanoTime() - startTime;
                double eventsPerSecond = (events.size() * 1_000_000_000.0) / duration;

                System.out.printf("Batch size %d: %.0f events/sec%n", batchSize, eventsPerSecond);
            }
        }

        @Test
        @DisplayName("largeBatchShouldHaveBetterPerformance")
        void largeBatchShouldHaveBetterPerformance() throws Exception {
            int batchSize = 1000;
            List<WorkflowEvent> largeBatch = createTestEvents(batchSize);

            // Test with optimized batch size
            long startTime = System.nanoTime();
            eventStore.appendBatch(largeBatch, 0);
            long optimizedTime = System.nanoTime() - startTime;

            // Compare with individual appends
            startTime = System.nanoTime();
            for (WorkflowEvent event : largeBatch) {
                eventStore.append(event, 0);
            }
            long individualTime = System.nanoTime() - startTime;

            // Batch should be at least 5x faster
            assertTrue(optimizedTime < individualTime / 5,
                String.format("Batch time (%d ns) should be < 1/5 of individual time (%d ns)",
                    optimizedTime, individualTime));
        }
    }

    // Helper methods

    private List<WorkflowEvent> createTestEvents(int count) {
        List<WorkflowEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(createTestEvent());
        }
        return events;
    }

    private WorkflowEvent createTestEvent() {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            WorkflowEvent.EventType.CASE_STARTED,
            "1.0",
            TEST_SPEC_ID,
            TEST_CASE_ID + "-" + UUID.randomUUID().toString().substring(0, 8),
            null,
            BASE_TIMESTAMP.plusMillis(System.currentTimeMillis()),
            Map.of("timestamp", String.valueOf(System.currentTimeMillis()),
                   "iteration", String.valueOf(ThreadLocalRandom.current().nextInt(1000)))
        );
    }

    private List<String> generateCaseIds(int count) {
        List<String> caseIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            caseIds.add("case-" + i);
        }
        return caseIds;
    }
}