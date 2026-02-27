/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.performance.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Event Sourcing Benchmark
 * 
 * Comprehensive benchmark for event persistence and retrieval performance
 * in YAWL workflow engine's event sourcing implementation.
 * 
 * Performance Targets:
 * - Event persistence: < 10ms (p95)
 * - Event retrieval: < 50ms (p95)
 * - Event replay: 1000 events < 100ms
 * - Event filtering: < 20ms (p95)
 * - Event consistency: 100% integrity
 * - Storage efficiency: > 80% compression ratio
 * 
 * Event Types:
 * - CaseCreated
 * - CaseStarted
 * - WorkItemCreated
 * - WorkItemStarted
 * - WorkItemCompleted
 * - WorkItemCancelled
 * - TaskTransitioned
 * - CaseCompleted
 * 
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 2026-02-26
 */
@Tag("integration")
@Tag("performance")
@Tag("eventsourcing")
@Execution(ExecutionMode.CONCURRENT)
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
public class EventSourcingBenchmark {

    // Performance thresholds
    private static final long MAX_EVENT_PERSISTENCE_MS = 10;
    private static final long MAX_EVENT_RETRIEVAL_MS = 50;
    private static final long MAX_EVENT_REPLAY_MS = 100; // For 1000 events
    private static final long MAX_EVENT_FILTERING_MS = 20;
    private static final double MIN_EVENT_INTEGRITY = 1.0; // 100%
    private static final double MIN_STORAGE_EFFICIENCY = 0.80; // 80% compression

    // Test configuration
    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final int EVENT_COUNTS = new int[]{100, 1000, 10000, 50000};
    private static final int CONCURRENT_WRITERS = 20;
    private static final int CONCURRENT_READERS = 50;

    // Event types
    private static final String[] EVENT_TYPES = {
        "CaseCreated",
        "CaseStarted",
        "WorkItemCreated", 
        "WorkItemStarted",
        "WorkItemCompleted",
        "WorkItemCancelled",
        "TaskTransitioned",
        "CaseCompleted"
    };

    // Test fixtures
    private static EventStore eventStore;
    private static EventDatabase eventDatabase;

    @BeforeAll
    static void setUpClass() throws Exception {
        // Initialize event database
        eventDatabase = new EventDatabase();
        
        // Initialize event store
        eventStore = new EventStore(eventDatabase);
        
        // Create schema
        eventDatabase.createSchema();

        System.out.println("Event Sourcing Benchmark initialized");
    }

    @AfterAll
    static void tearDownClass() throws Exception {
        if (eventStore != null) {
            eventStore.close();
        }
        if (eventDatabase != null) {
            eventDatabase.close();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Clear event store
        eventStore.clear();
        // Warmup
        warmupEventStore();
    }

    @Test
    @Order(1)
    @DisplayName("1.1: Event persistence performance")
    void eventPersistencePerformance() throws Exception {
        // Given: Empty event store
        int eventCount = 1000;
        List<Event> events = generateTestEvents(eventCount);

        // When: Persist events
        List<Long> persistenceTimes = new ArrayList<>();
        
        for (Event event : events) {
            long start = System.nanoTime();
            eventStore.persistEvent(event);
            long end = System.nanoTime();
            persistenceTimes.add((end - start) / 1_000_000);
        }

        // Calculate statistics
        Collections.sort(persistenceTimes);
        double avgTime = persistenceTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double p95Time = persistenceTimes.get((int) (persistenceTimes.size() * 0.95));
        double p99Time = persistenceTimes.get((int) (persistenceTimes.size() * 0.99));

        // Then: Validate persistence performance
        assertTrue(avgTime < MAX_EVENT_PERSISTENCE_MS,
            "Average persistence time should be under " + MAX_EVENT_PERSISTENCE_MS + 
            "ms, was: " + String.format("%.2f", avgTime) + "ms");
        assertTrue(p95Time < MAX_EVENT_PERSISTENCE_MS,
            "P95 persistence time should be under " + MAX_EVENT_PERSISTENCE_MS + 
            "ms, was: " + String.format("%.2f", p95Time) + "ms");

        System.out.println("Event persistence performance:");
        System.out.println("  Average: " + String.format("%.2f", avgTime) + "ms");
        System.out.println("  P95: " + String.format("%.2f", p95Time) + "ms");
        System.out.println("  P99: " + String.format("%.2f", p99Time) + "ms");
        System.out.println("  Throughput: " + String.format("%.1f", (eventCount * 1000.0) / 
            (persistenceTimes.stream().mapToLong(Long::longValue).sum())) + " events/sec");
    }

    @Test
    @Order(2)
    @DisplayName("2.1: Event retrieval performance")
    void eventRetrievalPerformance() throws Exception {
        // Given: Event store with test data
        int eventCount = 10000;
        List<Event> events = generateTestEvents(eventCount);
        for (Event event : events) {
            eventStore.persistEvent(event);
        }

        // When: Retrieve events
        List<Long> retrievalTimes = new ArrayList<>();
        int retrievalCount = 100;

        for (int i = 0; i < retrievalCount; i++) {
            long start = System.nanoTime();
            List<Event> retrieved = eventStore.getEvents("case-" + (i % 100), null, null);
            long end = System.nanoTime();
            retrievalTimes.add((end - start) / 1_000_000);
            
            // Validate data integrity
            assertNotNull(retrieved, "Retrieved events should not be null");
            assertFalse(retrieved.isEmpty(), "Should retrieve some events");
        }

        // Calculate statistics
        Collections.sort(retrievalTimes);
        double avgTime = retrievalTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double p95Time = retrievalTimes.get((int) (retrievalTimes.size() * 0.95));

        // Then: Validate retrieval performance
        assertTrue(avgTime < MAX_EVENT_RETRIEVAL_MS,
            "Average retrieval time should be under " + MAX_EVENT_RETRIEVAL_MS + 
            "ms, was: " + String.format("%.2f", avgTime) + "ms");
        assertTrue(p95Time < MAX_EVENT_RETRIEVAL_MS,
            "P95 retrieval time should be under " + MAX_EVENT_RETRIEVAL_MS + 
            "ms, was: " + String.format("%.2f", p95Time) + "ms");

        System.out.println("Event retrieval performance:");
        System.out.println("  Average: " + String.format("%.2f", avgTime) + "ms");
        System.out.println("  P95: " + String.format("%.2f", p95Time) + "ms");
        System.out.println("  Retrieved events count: " + retrievalCount);
    }

    @Test
    @Order(3)
    @DisplayName("3.1: Event replay performance")
    void eventReplayPerformance() throws Exception {
        // Given: Event store with history
        for (int count : EVENT_COUNTS) {
            List<Event> events = generateTestEvents(count);
            for (Event event : events) {
                eventStore.persistEvent(event);
            }

            // When: Replay events
            long start = System.nanoTime();
            List<Event> replayed = eventStore.replayEvents("case-1");
            long end = System.nanoTime();

            long replayTime = (end - start) / 1_000_000;
            double eventsPerMs = count / Math.max(replayTime, 1);

            // Then: Validate replay performance
            assertTrue(replayTime < MAX_EVENT_REPLAY_MS,
                "Replay time for " + count + " events should be under " + MAX_EVENT_REPLAY_MS + 
                "ms, was: " + replayTime + "ms");
            assertTrue(replayed.size() >= count * 0.8, 
                "Should replay most events, got: " + replayed.size() + "/" + count);

            System.out.println("Event replay performance for " + count + " events:");
            System.out.println("  Time: " + replayTime + "ms");
            System.out.println("  Throughput: " + String.format("%.1f", eventsPerMs) + " events/ms");
        }
    }

    @Test
    @Order(4)
    @DisplayName("4.1: Event filtering performance")
    void eventFilteringPerformance() throws Exception {
        // Given: Event store with diverse data
        int eventCount = 10000;
        List<Event> events = generateTestEvents(eventCount);
        for (Event event : events) {
            eventStore.persistEvent(event);
        }

        // When: Filter by various criteria
        Map<String, Long> filterResults = new HashMap<>();

        // Filter by type
        long start = System.nanoTime();
        List<Event> byType = eventStore.getEvents(null, "WorkItemCreated", null);
        long end = System.nanoTime();
        filterResults.put("byType", (end - start) / 1_000_000);

        // Filter by time range
        Instant startTime = Instant.now().minus(Duration.ofHours(1));
        Instant endTime = Instant.now();
        start = System.nanoTime();
        List<Event> byTime = eventStore.getEvents(null, null, startTime, endTime);
        end = System.nanoTime();
        filterResults.put("byTime", (end - start) / 1_000_000);

        // Filter by type and time
        start = System.nanoTime();
        List<Event> byTypeAndTime = eventStore.getEvents(null, "CaseCreated", startTime, endTime);
        end = System.nanoTime();
        filterResults.put("byTypeAndTime", (end - start) / 1_000_000);

        // Then: Validate filtering performance
        for (Map.Entry<String, Long> entry : filterResults.entrySet()) {
            long time = entry.getValue();
            assertTrue(time < MAX_EVENT_FILTERING_MS,
                entry.getKey() + " filtering should be under " + MAX_EVENT_FILTERING_MS + 
                "ms, was: " + time + "ms");
        }

        System.out.println("Event filtering performance:");
        System.out.println("  By type: " + filterResults.get("byType") + "ms");
        System.out.println("  By time: " + filterResults.get("byTime") + "ms");
        System.out.println("  By type and time: " + filterResults.get("byTypeAndTime") + "ms");
    }

    @Test
    @Order(5)
    @DisplayName("5.1: Concurrent event persistence")
    void concurrentEventPersistence() throws Exception {
        // Given: Event store ready
        int eventsPerWriter = 1000;
        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_WRITERS);

        long startTime = System.nanoTime();

        // When: Persist events concurrently
        for (int i = 0; i < CONCURRENT_WRITERS; i++) {
            final int writerNum = i;
            futures.add(executor.submit(() -> {
                try {
                    List<Event> events = generateTestEvents(eventsPerWriter, "writer-" + writerNum);
                    for (Event event : events) {
                        eventStore.persistEvent(event);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        long endTime = System.nanoTime();

        executor.shutdown();

        // Calculate metrics
        long totalDuration = (endTime - startTime) / 1_000_000;
        int totalEvents = CONCURRENT_WRITERS * eventsPerWriter;
        double throughput = (totalEvents * 1000.0) / totalDuration;
        double successRate = (double) successCount.get() / totalEvents;

        // Then: Validate concurrent performance
        assertTrue(successRate > 0.95,
            "Success rate should be >95%, was: " + String.format("%.2f", successRate * 100) + "%");
        assertTrue(throughput > CONCURRENT_WRITERS * 10,
            "Throughput should be >" + (CONCURRENT_WRITERS * 10) + " events/sec, was: " + String.format("%.1f", throughput));

        System.out.println("Concurrent event persistence:");
        System.out.println("  Writers: " + CONCURRENT_WRITERS);
        System.out.println("  Events per writer: " + eventsPerWriter);
        System.out.println("  Total events: " + totalEvents);
        System.out.println("  Duration: " + totalDuration + "ms");
        System.out.println("  Throughput: " + String.format("%.1f", throughput) + " events/sec");
        System.out.println("  Success rate: " + String.format("%.2f", successRate * 100) + "%");
    }

    @Test
    @Order(6)
    @DisplayName("6.1: Event integrity and consistency")
    void eventIntegrityAndConsistency() throws Exception {
        // Given: Event store
        int eventCount = 5000;
        List<Event> events = generateTestEvents(eventCount);

        // When: Persist and retrieve events
        Map<String, List<Event>> caseEvents = new HashMap<>();

        for (Event event : events) {
            eventStore.persistEvent(event);
            caseEvents.computeIfAbsent(event.caseId, k -> new ArrayList<>()).add(event);
        }

        // Verify integrity for each case
        int integrityChecks = 0;
        int integrityFailures = 0;
        long totalVerificationTime = 0;

        for (Map.Entry<String, List<Event>> entry : caseEvents.entrySet()) {
            String caseId = entry.getKey();
            List<Event> originalEvents = entry.getValue();
            
            long start = System.nanoTime();
            List<Event> retrievedEvents = eventStore.getEvents(caseId, null, null);
            long end = System.nanoTime();
            
            totalVerificationTime += (end - start) / 1_000_000;
            integrityChecks++;

            // Verify data integrity
            if (retrievedEvents.size() != originalEvents.size()) {
                integrityFailures++;
                continue;
            }

            // Verify all events are present
            for (Event original : originalEvents) {
                boolean found = retrievedEvents.stream()
                    .anyMatch(retrieved -> retrieved.equals(original));
                if (!found) {
                    integrityFailures++;
                    break;
                }
            }
        }

        // Calculate metrics
        double integrity = 1.0 - (double) integrityFailures / integrityChecks;
        double avgVerificationTime = (double) totalVerificationTime / integrityChecks;

        // Then: Validate integrity
        assertEquals(MIN_EVENT_INTEGRITY, integrity, 0.001,
            "Event integrity should be 100%, was: " + String.format("%.2f", integrity * 100) + "%");
        assertTrue(avgVerificationTime < 10,
            "Average verification time should be under 10ms, was: " + String.format("%.2f", avgVerificationTime) + "ms");

        System.out.println("Event integrity and consistency:");
        System.out.println("  Checks performed: " + integrityChecks);
        System.out.println("  Failures: " + integrityFailures);
        System.out.println("  Integrity: " + String.format("%.2f", integrity * 100) + "%");
        System.out.println("  Avg verification time: " + String.format("%.2f", avgVerificationTime) + "ms");
    }

    @Test
    @Order(7)
    @DisplayName("7.1: Event storage efficiency")
    void eventStorageEfficiency() throws Exception {
        // Given: Event store
        int eventCount = 10000;
        List<Event> events = generateTestEvents(eventCount);

        // Measure raw data size
        long rawSize = events.stream()
            .mapToLong(e -> e.toString().getBytes().length)
            .sum();

        // When: Store events
        long start = System.nanoTime();
        for (Event event : events) {
            eventStore.persistEvent(event);
        }
        long end = System.nanoTime();

        // Get compressed storage size
        long compressedSize = eventDatabase.getStorageSize();
        double compressionRatio = 1.0 - (double) compressedSize / rawSize;

        // Calculate metrics
        long persistenceTime = (end - start) / 1_000_000;
        double storageEfficiency = 1.0 - (double) compressedSize / rawSize;

        // Then: Validate storage efficiency
        assertTrue(storageEfficiency >= MIN_STORAGE_EFFICIENCY,
            "Storage efficiency should be at least " + (MIN_STORAGE_EFFICIENCY * 100) + 
            "%, was: " + String.format("%.2f", storageEfficiency * 100) + "%");
        assertTrue(persistenceTime < 5000,
            "Persistence time should be under 5s for " + eventCount + " events, was: " + persistenceTime + "ms");

        System.out.println("Event storage efficiency:");
        System.out.println("  Raw size: " + rawSize + " bytes");
        System.out.println("  Compressed size: " + compressedSize + " bytes");
        System.out.println("  Compression ratio: " + String.format("%.2f", compressionRatio));
        System.out.println("  Storage efficiency: " + String.format("%.2f", storageEfficiency * 100) + "%");
        System.out.println("  Persistence time: " + persistenceTime + "ms");
    }

    @Test
    @Order(8)
    @DisplayName("8.1: Event aggregation and analytics")
    void eventAggregationAndAnalytics() throws Exception {
        // Given: Event store with diverse data
        int eventCount = 20000;
        List<Event> events = generateTestEvents(eventCount);
        for (Event event : events) {
            eventStore.persistEvent(event);
        }

        // When: Perform various aggregations
        Map<String, Long> results = new HashMap<>();
        long startTime = System.nanoTime();

        // Count by type
        long start = System.nanoTime();
        Map<String, Long> typeCounts = eventStore.aggregateByType();
        long end = System.nanoTime();
        results.put("byType", (end - start) / 1_000_000);

        // Count by case
        start = System.nanoTime();
        Map<String, Long> caseCounts = eventStore.aggregateByCase();
        end = System.nanoTime();
        results.put("byCase", (end - start) / 1_000_000);

        // Timeline aggregation
        start = System.nanoTime();
        Map<String, Map<String, Long>> timelineAggregations = eventStore.aggregateByTimeRange(Duration.ofHours(1));
        end = System.nanoTime();
        results.put("byTimeRange", (end - start) / 1_000_000);

        // Then: Validate aggregation performance
        for (Map.Entry<String, Long> entry : results.entrySet()) {
            long time = entry.getValue();
            assertTrue(time < 100,
                entry.getKey() + " aggregation should be under 100ms, was: " + time + "ms");
        }

        // Verify aggregation results
        assertEquals(EVENT_TYPES.length, typeCounts.size(),
            "Should have counts for all event types");
        assertTrue(caseCounts.size() > 0, "Should have case counts");
        assertFalse(timelineAggregations.isEmpty(), "Should have timeline aggregations");

        System.out.println("Event aggregation and analytics performance:");
        System.out.println("  By type: " + results.get("byType") + "ms");
        System.out.println("  By case: " + results.get("byCase") + "ms");
        System.out.println("  By time range: " + results.get("byTimeRange") + "ms");
        System.out.println("  Type counts: " + typeCounts);
        System.out.println("  Case counts: " + caseCounts.size());
    }

    // Helper methods

    private void warmupEventStore() throws Exception {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            Event event = generateTestEvent("warmup-" + i);
            eventStore.persistEvent(event);
            eventStore.getEvents(event.caseId, null, null);
        }
        eventStore.clear();
    }

    private List<Event> generateTestEvents(int count) {
        return generateTestEvents(count, "case-1");
    }

    private List<Event> generateTestEvents(int count, String caseIdPrefix) {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(generateTestEvent(caseIdPrefix + "-" + i));
        }
        return events;
    }

    private Event generateTestEvent(String caseId) {
        String eventType = EVENT_TYPES[(int) (Math.random() * EVENT_TYPES.length)];
        Instant timestamp = Instant.now().minus(Duration.ofMillis((long) (Math.random() * 86400000))); // Random time in last 24h
        
        Map<String, Object> data = new HashMap<>();
        data.put("userId", "user-" + Math.random());
        data.put("duration", (int) (Math.random() * 1000));
        data.put("metadata", Map.of(
            "priority", Math.random() > 0.5 ? "high" : "normal",
            "category, Arrays.asList("A", "B", "C")
        ));

        return new Event(caseId, eventType, timestamp, data);
    }

    // Inner classes

    private static class Event {
        final String caseId;
        final String eventType;
        final Instant timestamp;
        final Map<String, Object> data;

        public Event(String caseId, String eventType, Instant timestamp, Map<String, Object> data) {
            this.caseId = caseId;
            this.eventType = eventType;
            this.timestamp = timestamp;
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Event event = (Event) o;
            return Objects.equals(caseId, event.caseId) &&
                   Objects.equals(eventType, event.eventType) &&
                   Objects.equals(timestamp, event.timestamp) &&
                   Objects.equals(data, event.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(caseId, eventType, timestamp, data);
        }

        @Override
        public String toString() {
            return "Event{" +
                   "caseId='" + caseId + '\'' +
                   ", eventType='" + eventType + '\'' +
                   ", timestamp=" + timestamp +
                   ", data=" + data +
                   '}';
        }
    }

    private static class EventStore {
        private final EventDatabase database;
        private final AtomicInteger eventCounter = new AtomicInteger(0);

        public EventStore(EventDatabase database) {
            this.database = database;
        }

        public void persistEvent(Event event) throws SQLException {
            database.persistEvent(event);
        }

        public List<Event> getEvents(String caseId, String eventType, Instant startTime, Instant endTime) throws SQLException {
            return database.getEvents(caseId, eventType, startTime, endTime);
        }

        public List<Event> getEvents(String caseId, String eventType) throws SQLException {
            return getEvents(caseId, eventType, null, null);
        }

        public List<Event> replayEvents(String caseId) throws SQLException {
            return database.getEvents(caseId, null, null, null);
        }

        public Map<String, Long> aggregateByType() throws SQLException {
            return database.aggregateByType();
        }

        public Map<String, Long> aggregateByCase() throws SQLException {
            return database.aggregateByCase();
        }

        public Map<String, Map<String, Long>> aggregateByTimeRange(Duration range) throws SQLException {
            return database.aggregateByTimeRange(range);
        }

        public void clear() throws SQLException {
            database.clear();
            eventCounter.set(0);
        }

        public void close() throws SQLException {
            database.close();
        }
    }

    private static class EventDatabase {
        private Connection connection;
        private final String schemaName;

        public EventDatabase() throws SQLException {
            this.schemaName = "events_" + System.currentTimeMillis();
            this.connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + schemaName + ";DB_CLOSE_DELAY=-1", "sa", "");
            createSchema();
        }

        public void close() throws SQLException {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        }

        public void createSchema() throws SQLException {
            connection.createStatement().execute("""
                CREATE TABLE events (
                    id VARCHAR(255) PRIMARY KEY,
                    case_id VARCHAR(255),
                    event_type VARCHAR(50),
                    timestamp TIMESTAMP,
                    data CLOB,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Create indexes for performance
            connection.createStatement().execute(
                "CREATE INDEX idx_events_case_id ON events(case_id)");
            connection.createStatement().execute(
                "CREATE INDEX idx_events_event_type ON events(event_type)");
            connection.createStatement().execute(
                "CREATE INDEX idx_events_timestamp ON events(timestamp)");
        }

        public void persistEvent(Event event) throws SQLException {
            String id = event.caseId + "-" + event.eventType + "-" + 
                       System.currentTimeMillis() + "-" + eventCounter.incrementAndGet();
            
            String jsonData = convertToJson(event.data);
            
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO events VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)")) {
                stmt.setString(1, id);
                stmt.setString(2, event.caseId);
                stmt.setString(3, event.eventType);
                stmt.setTimestamp(4, Timestamp.from(event.timestamp));
                stmt.setString(5, jsonData);
                stmt.executeUpdate();
            }
        }

        public List<Event> getEvents(String caseId, String eventType, 
                                  Instant startTime, Instant endTime) throws SQLException {
            List<Event> events = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM events WHERE 1=1");
            
            if (caseId != null) {
                sql.append(" AND case_id = ?");
            }
            if (eventType != null) {
                sql.append(" AND event_type = ?");
            }
            if (startTime != null) {
                sql.append(" AND timestamp >= ?");
            }
            if (endTime != null) {
                sql.append(" AND timestamp <= ?");
            }
            
            sql.append(" ORDER BY timestamp");

            try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
                int paramIndex = 1;
                if (caseId != null) {
                    stmt.setString(paramIndex++, caseId);
                }
                if (eventType != null) {
                    stmt.setString(paramIndex++, eventType);
                }
                if (startTime != null) {
                    stmt.setTimestamp(paramIndex++, Timestamp.from(startTime));
                }
                if (endTime != null) {
                    stmt.setTimestamp(paramIndex, Timestamp.from(endTime));
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        events.add(mapRowToEvent(rs));
                    }
                }
            }
            
            return events;
        }

        public Map<String, Long> aggregateByType() throws SQLException {
            Map<String, Long> result = new HashMap<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT event_type, COUNT(*) FROM events GROUP BY event_type")) {
                while (rs.next()) {
                    result.put(rs.getString("event_type"), rs.getLong("COUNT(*)"));
                }
            }
            return result;
        }

        public Map<String, Long> aggregateByCase() throws SQLException {
            Map<String, Long> result = new HashMap<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT case_id, COUNT(*) FROM events GROUP BY case_id")) {
                while (rs.next()) {
                    result.put(rs.getString("case_id"), rs.getLong("COUNT(*)"));
                }
            }
            return result;
        }

        public Map<String, Map<String, Long>> aggregateByTimeRange(Duration range) throws SQLException {
            Map<String, Map<String, Long>> result = new HashMap<>();
            
            // Group by hour intervals
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT HOUR(timestamp) as hour, event_type, COUNT(*) " +
                     "FROM events " +
                     "GROUP BY HOUR(timestamp), event_type")) {
                while (rs.next()) {
                    String hourKey = "hour-" + rs.getInt("hour");
                    String eventType = rs.getString("event_type");
                    long count = rs.getLong("COUNT(*)");
                    
                    result.computeIfAbsent(hourKey, k -> new HashMap<>())
                          .put(eventType, count);
                }
            }
            return result;
        }

        public void clear() throws SQLException {
            connection.createStatement().execute("DELETE FROM events");
        }

        public long getStorageSize() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT SUM(LENGTH(data)) FROM events")) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0;
        }

        private Event mapRowToEvent(ResultSet rs) throws SQLException {
            String id = rs.getString("id");
            String caseId = rs.getString("case_id");
            String eventType = rs.getString("event_type");
            Instant timestamp = rs.getTimestamp("timestamp").toInstant();
            String dataJson = rs.getString("data");
            
            Map<String, Object> data = parseJson(dataJson);
            
            return new Event(caseId, eventType, timestamp, data);
        }

        private String convertToJson(Map<String, Object> data) {
            // Simple JSON conversion for benchmark
            StringBuilder json = new StringBuilder("{");
            data.forEach((key, value) -> {
                json.append("\"").append(key).append("\":");
                if (value instanceof String) {
                    json.append("\"").append(value).append("\"");
                } else if (value instanceof Number) {
                    json.append(value);
                } else if (value instanceof Map) {
                    json.append(convertToJson((Map<String, Object>) value));
                } else {
                    json.append("\"").append(value).append("\"");
                }
                json.append(",");
            });
            if (json.length() > 1) {
                json.setLength(json.length() - 1); // Remove last comma
            }
            json.append("}");
            return json.toString();
        }

        private Map<String, Object> parseJson(String json) {
            // Simple JSON parsing for benchmark
            Map<String, Object> result = new HashMap<>();
            // In a real implementation, use a JSON parser
            result.put("parsed", true);
            return result;
        }
    }
}
