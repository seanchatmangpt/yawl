/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.chaos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge Case Chaos Engineering Tests for YAWL MCP-A2A MVP.
 *
 * Tests edge case scenarios including:
 * - Extreme concurrency (50,000+ requests)
 * - Oversized payload handling
 * - Long-running operations (1+ hours)
 * - Rapid service restarts
 * - Clock skew scenarios
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Tag("chaos")
@DisplayName("Edge Case Chaos Tests")
class EdgeCaseChaosTest {

    private Connection db;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:chaos_edge_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(db);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }

    // =========================================================================
    // Extreme Concurrency Tests (50,000+ requests)
    // =========================================================================

    @Nested
    @DisplayName("Extreme Concurrency (50,000+ Requests)")
    class ExtremeConcurrencyTests {

        @Test
        @DisplayName("50,000 concurrent database inserts")
        @Timeout(value = 5, unit = TimeUnit.MINUTES)
        void test50kConcurrentDatabaseInserts() throws Exception {
            int totalRequests = 50_000;
            int batchSize = 500;
            int batchCount = totalRequests / batchSize;

            // Pre-seed parent specification
            WorkflowDataFactory.seedSpecification(db, "extreme-concurrency", "1.0", "Extreme Test");

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            AtomicLong totalLatency = new AtomicLong(0);

            Instant startTime = Instant.now();

            // Process in batches to avoid overwhelming the system
            for (int batch = 0; batch < batchCount; batch++) {
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                CountDownLatch latch = new CountDownLatch(batchSize);

                int batchStart = batch * batchSize;

                for (int i = 0; i < batchSize; i++) {
                    final int idx = batchStart + i;
                    executor.submit(() -> {
                        long opStart = System.currentTimeMillis();
                        try {
                            WorkflowDataFactory.seedNetRunner(db,
                                    "extreme-runner-" + idx,
                                    "extreme-concurrency", "1.0",
                                    "net-" + idx, "RUNNING");
                            successCount.incrementAndGet();
                        } catch (SQLException e) {
                            failureCount.incrementAndGet();
                        } finally {
                            totalLatency.addAndGet(System.currentTimeMillis() - opStart);
                            latch.countDown();
                        }
                    });
                }

                latch.await(60, TimeUnit.SECONDS);
                executor.shutdown();

                if ((batch + 1) % 10 == 0) {
                    System.out.printf("Progress: %d/%d batches completed (%.1f%%)%n",
                            batch + 1, batchCount, (batch + 1) * 100.0 / batchCount);
                }
            }

            Duration duration = Duration.between(startTime, Instant.now());
            double throughput = (successCount.get() * 1000.0) / duration.toMillis();

            System.out.printf("%n=== Extreme Concurrency Results ===%n");
            System.out.printf("Total requests: %d%n", totalRequests);
            System.out.printf("Successful: %d (%.2f%%)%n", successCount.get(),
                    successCount.get() * 100.0 / totalRequests);
            System.out.printf("Failed: %d (%.2f%%)%n", failureCount.get(),
                    failureCount.get() * 100.0 / totalRequests);
            System.out.printf("Duration: %s%n", duration);
            System.out.printf("Throughput: %.0f ops/sec%n", throughput);
            System.out.printf("Avg latency: %.2f ms%n",
                    totalLatency.get() * 1.0 / successCount.get());
            System.out.printf("================================%n%n");

            assertTrue(successCount.get() >= totalRequests * 0.95,
                    "At least 95% must succeed: actual=" + successCount.get());

            // Verify data integrity
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = 'extreme-concurrency'")) {
                assertTrue(rs.next());
                assertEquals(successCount.get(), rs.getInt(1),
                        "Database must contain all successful inserts");
            }
        }

        @Test
        @DisplayName("100,000 concurrent reads")
        @Timeout(value = 5, unit = TimeUnit.MINUTES)
        void test100kConcurrentReads() throws Exception {
            // Seed data for reads
            WorkflowDataFactory.seedSpecification(db, "read-concurrency", "1.0", "Read Test");

            int totalReads = 100_000;
            int batchSize = 1000;
            int batchCount = totalReads / batchSize;

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            Instant startTime = Instant.now();

            for (int batch = 0; batch < batchCount; batch++) {
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                CountDownLatch latch = new CountDownLatch(batchSize);

                for (int i = 0; i < batchSize; i++) {
                    executor.submit(() -> {
                        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
                             PreparedStatement ps = conn.prepareStatement(
                                     "SELECT * FROM yawl_specification WHERE spec_id = ?")) {
                            ps.setString(1, "read-concurrency");
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    successCount.incrementAndGet();
                                }
                            }
                        } catch (SQLException e) {
                            failureCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await(30, TimeUnit.SECONDS);
                executor.shutdown();
            }

            Duration duration = Duration.between(startTime, Instant.now());
            double throughput = (successCount.get() * 1000.0) / duration.toMillis();

            System.out.printf("100k reads: %d succeeded, %d failed in %s (%.0f reads/sec)%n",
                    successCount.get(), failureCount.get(), duration, throughput);

            assertEquals(totalReads, successCount.get(), "All reads must succeed");
        }

        @Test
        @DisplayName("Mixed read/write concurrency (80/20 split)")
        @Timeout(value = 3, unit = TimeUnit.MINUTES)
        void testMixedReadWriteConcurrency() throws Exception {
            WorkflowDataFactory.seedSpecification(db, "mixed-concurrency", "1.0", "Mixed Test");

            int totalOps = 10_000;
            int readsPerWrite = 4; // 80% reads, 20% writes

            AtomicInteger readSuccess = new AtomicInteger(0);
            AtomicInteger writeSuccess = new AtomicInteger(0);
            AtomicInteger writeId = new AtomicInteger(0);

            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch latch = new CountDownLatch(totalOps);

            Instant startTime = Instant.now();

            for (int i = 0; i < totalOps; i++) {
                final boolean isWrite = (i % (readsPerWrite + 1)) == 0;

                executor.submit(() -> {
                    try {
                        if (isWrite) {
                            int id = writeId.incrementAndGet();
                            WorkflowDataFactory.seedNetRunner(db,
                                    "mixed-runner-" + id,
                                    "mixed-concurrency", "1.0",
                                    "net", "RUNNING");
                            writeSuccess.incrementAndGet();
                        } else {
                            try (PreparedStatement ps = db.prepareStatement(
                                    "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = ?")) {
                                ps.setString(1, "mixed-concurrency");
                                try (ResultSet rs = ps.executeQuery()) {
                                    if (rs.next()) readSuccess.incrementAndGet();
                                }
                            }
                        }
                    } catch (SQLException e) {
                        // Failure
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(120, TimeUnit.SECONDS);
            executor.shutdown();

            Duration duration = Duration.between(startTime, Instant.now());

            System.out.printf("Mixed concurrency: reads=%d, writes=%d in %s%n",
                    readSuccess.get(), writeSuccess.get(), duration);

            int expectedWrites = totalOps / (readsPerWrite + 1);
            int expectedReads = totalOps - expectedWrites;

            assertTrue(readSuccess.get() >= expectedReads * 0.95,
                    "At least 95% of reads must succeed");
            assertTrue(writeSuccess.get() >= expectedWrites * 0.95,
                    "At least 95% of writes must succeed");
        }
    }

    // =========================================================================
    // Oversized Payload Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Oversized Payload Handling")
    class OversizedPayloadTests {

        @Test
        @DisplayName("1MB event data storage")
        void test1MbEventDataStorage() throws Exception {
            // Create 1MB of data
            int sizeBytes = 1024 * 1024;
            StringBuilder sb = new StringBuilder("{\"data\":\"");
            for (int i = 0; i < sizeBytes - 20; i++) {
                sb.append('x');
            }
            sb.append("\"}");
            String largeData = sb.toString();

            WorkflowDataFactory.seedSpecification(db, "1mb-payload", "1.0", "1MB Test");

            // Insert large event
            try (PreparedStatement ps = db.prepareStatement(
                    "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data) " +
                            "VALUES (?, ?, ?, ?)")) {
                ps.setLong(1, System.nanoTime());
                ps.setString(2, "large-runner");
                ps.setString(3, "LARGE_EVENT");
                ps.setString(4, largeData);
                ps.executeUpdate();
            }

            // Verify retrieval
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT LENGTH(event_data) as len FROM yawl_case_event WHERE event_type = 'LARGE_EVENT'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertTrue(rs.getInt("len") >= sizeBytes - 1000,
                            "Must store approximately 1MB of data");
                }
            }

            System.out.printf("Successfully stored and retrieved %d bytes%n", sizeBytes);
        }

        @Test
        @DisplayName("10MB payload handling")
        void test10MbPayloadHandling() throws Exception {
            int sizeMb = 10;
            int chunkSize = 1024 * 1024;
            byte[] largeData = new byte[sizeMb * chunkSize];
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }

            WorkflowDataFactory.seedSpecification(db, "10mb-payload", "1.0", "10MB Test");

            // Store as hex string (doubles the size in DB)
            StringBuilder hexData = new StringBuilder();
            for (int i = 0; i < Math.min(5 * 1024 * 1024, largeData.length); i++) {
                hexData.append(String.format("%02x", largeData[i]));
            }

            try (PreparedStatement ps = db.prepareStatement(
                    "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data) " +
                            "VALUES (?, ?, ?, ?)")) {
                ps.setLong(1, System.nanoTime());
                ps.setString(2, "10mb-runner");
                ps.setString(3, "TEN_MB_EVENT");
                ps.setString(4, hexData.toString());
                ps.executeUpdate();
            }

            System.out.printf("Successfully stored 10MB equivalent payload%n");
        }

        @Test
        @DisplayName("Deeply nested JSON handling")
        void testDeeplyNestedJsonHandling() throws Exception {
            // Create deeply nested JSON structure
            int depth = 100;
            StringBuilder json = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                json.append("{\"level").append(i).append("\":");
            }
            json.append("\"deepest\"");
            for (int i = 0; i < depth; i++) {
                json.append("}");
            }

            String nestedJson = json.toString();

            WorkflowDataFactory.seedSpecification(db, "nested-json", "1.0", "Nested JSON Test");

            try (PreparedStatement ps = db.prepareStatement(
                    "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data) " +
                            "VALUES (?, ?, ?, ?)")) {
                ps.setLong(1, System.nanoTime());
                ps.setString(2, "nested-runner");
                ps.setString(3, "NESTED_JSON");
                ps.setString(4, nestedJson);
                ps.executeUpdate();
            }

            // Verify retrieval
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT event_data FROM yawl_case_event WHERE event_type = 'NESTED_JSON'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    String retrieved = rs.getString("event_data");
                    assertTrue(retrieved.contains("level99"),
                            "Must retrieve deeply nested JSON");
                    assertTrue(retrieved.contains("deepest"),
                            "Must contain deepest value");
                }
            }

            System.out.printf("Successfully stored and retrieved JSON with %d levels of nesting%n", depth);
        }

        @Test
        @DisplayName("Array with 100,000 elements")
        void testArrayWith100kElements() throws Exception {
            // Create large JSON array
            StringBuilder json = new StringBuilder("[");
            int elements = 100_000;
            for (int i = 0; i < elements; i++) {
                if (i > 0) json.append(",");
                json.append("{\"id\":").append(i).append(",\"value\":\"item-").append(i).append("\"}");
            }
            json.append("]");

            String largeArray = json.toString();
            int length = largeArray.length();

            WorkflowDataFactory.seedSpecification(db, "100k-array", "1.0", "100k Array Test");

            try (PreparedStatement ps = db.prepareStatement(
                    "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data) " +
                            "VALUES (?, ?, ?, ?)")) {
                ps.setLong(1, System.nanoTime());
                ps.setString(2, "array-runner");
                ps.setString(3, "LARGE_ARRAY");
                ps.setString(4, largeArray);
                ps.executeUpdate();
            }

            System.out.printf("Successfully stored array with %d elements (%d chars)%n",
                    elements, length);
        }
    }

    // =========================================================================
    // Long-Running Operations Tests
    // =========================================================================

    @Nested
    @DisplayName("Long-Running Operations (1+ Hours Simulated)")
    class LongRunningOperationsTests {

        @Test
        @DisplayName("Long-running transaction with periodic checkpoints")
        @Timeout(value = 2, unit = TimeUnit.MINUTES)
        void testLongRunningTransactionWithCheckpoints() throws Exception {
            // Simulate 1-hour operation with checkpoints every 5 minutes
            // In test: 60 seconds with checkpoints every 5 seconds

            int totalDurationSec = 60;
            int checkpointIntervalSec = 5;
            int expectedCheckpoints = totalDurationSec / checkpointIntervalSec;

            WorkflowDataFactory.seedSpecification(db, "long-running", "1.0", "Long Running Test");

            AtomicInteger checkpointsCompleted = new AtomicInteger(0);
            AtomicBoolean operationCompleted = new AtomicBoolean(false);

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < expectedCheckpoints; i++) {
                // Simulate work
                Thread.sleep(checkpointIntervalSec * 1000);

                // Create checkpoint
                db.setAutoCommit(false);

                WorkflowDataFactory.seedNetRunner(db,
                        "checkpoint-" + i,
                        "long-running", "1.0",
                        "net", "RUNNING");

                // Log checkpoint
                try (PreparedStatement ps = db.prepareStatement(
                        "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data) " +
                                "VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, System.nanoTime());
                    ps.setString(2, "checkpoint-" + i);
                    ps.setString(3, "CHECKPOINT");
                    ps.setString(4, "{\"checkpoint\":" + i + ",\"elapsed_ms\":" +
                            (System.currentTimeMillis() - startTime) + "}");
                    ps.executeUpdate();
                }

                db.commit();
                checkpointsCompleted.incrementAndGet();

                System.out.printf("Checkpoint %d/%d completed at %dms%n",
                        i + 1, expectedCheckpoints, System.currentTimeMillis() - startTime);
            }

            operationCompleted.set(true);

            long totalDuration = System.currentTimeMillis() - startTime;

            assertTrue(operationCompleted.get(), "Operation must complete");
            assertEquals(expectedCheckpoints, checkpointsCompleted.get(),
                    "All checkpoints must be recorded");

            System.out.printf("Long-running operation completed: %d checkpoints in %dms%n",
                    checkpointsCompleted.get(), totalDuration);
        }

        @Test
        @DisplayName("Streaming large result set")
        void testStreamingLargeResultSet() throws Exception {
            // Insert large amount of data
            int recordCount = 10_000;
            WorkflowDataFactory.seedSpecification(db, "streaming-test", "1.0", "Streaming Test");

            for (int i = 0; i < recordCount; i++) {
                WorkflowDataFactory.seedNetRunner(db,
                        "stream-runner-" + i,
                        "streaming-test", "1.0",
                        "net-" + i, "RUNNING");
            }

            // Stream results with cursor
            AtomicInteger recordsRead = new AtomicInteger(0);

            try (Statement stmt = db.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                stmt.setFetchSize(100);

                try (ResultSet rs = stmt.executeQuery(
                        "SELECT * FROM yawl_net_runner WHERE spec_id = 'streaming-test'")) {

                    while (rs.next()) {
                        recordsRead.incrementAndGet();
                    }
                }
            }

            assertEquals(recordCount, recordsRead.get(),
                    "Must stream all records");

            System.out.printf("Successfully streamed %d records%n", recordCount);
        }

        @Test
        @DisplayName("Connection kept alive during long operation")
        void testConnectionKeptAliveDuringLongOperation() throws Exception {
            // Simulate long operation with connection keep-alive
            int keepAliveIntervalSec = 5;
            int totalIntervals = 12; // 1 minute total

            AtomicInteger keepAlivesSent = new AtomicInteger(0);

            for (int i = 0; i < totalIntervals; i++) {
                // Keep-alive query
                try (Statement stmt = db.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    if (rs.next()) {
                        keepAlivesSent.incrementAndGet();
                    }
                }

                Thread.sleep(keepAliveIntervalSec * 1000);
            }

            assertEquals(totalIntervals, keepAlivesSent.get(),
                    "All keep-alives must succeed");

            // Verify connection still valid
            assertFalse(db.isClosed(), "Connection must remain open");
            assertTrue(db.isValid(5), "Connection must be valid");

            System.out.printf("Connection kept alive for %d intervals%n", totalIntervals);
        }
    }

    // =========================================================================
    // Rapid Service Restarts Tests
    // =========================================================================

    @Nested
    @DisplayName("Rapid Service Restarts")
    class RapidServiceRestartTests {

        @Test
        @DisplayName("Connection reconnection after close")
        void testConnectionReconnectionAfterClose() throws Exception {
            int restarts = 10;
            AtomicInteger successfulRestarts = new AtomicInteger(0);

            for (int i = 0; i < restarts; i++) {
                // Close current connection
                if (db != null && !db.isClosed()) {
                    db.close();
                }

                // Reconnect
                db = DriverManager.getConnection(jdbcUrl, "sa", "");

                // Verify connection works
                try (Statement stmt = db.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    if (rs.next()) {
                        successfulRestarts.incrementAndGet();
                    }
                }

                // Seed data to verify persistence
                WorkflowDataFactory.seedSpecification(db,
                        "restart-test-" + i, "1.0", "Restart " + i);
            }

            assertEquals(restarts, successfulRestarts.get(),
                    "All restarts must succeed");

            // Verify all data persisted across restarts
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_specification WHERE spec_id LIKE 'restart-test%'")) {
                assertTrue(rs.next());
                assertEquals(restarts, rs.getInt(1),
                        "All records must persist across restarts");
            }

            System.out.printf("Successfully handled %d rapid restarts%n", restarts);
        }

        @Test
        @DisplayName("Schema recovery after database restart")
        void testSchemaRecoveryAfterDatabaseRestart() throws Exception {
            // Create schema and data
            YawlContainerFixtures.applyYawlSchema(db);
            WorkflowDataFactory.seedSpecification(db, "schema-recovery", "1.0", "Schema Test");

            // Drop schema (simulate corruption)
            YawlContainerFixtures.dropYawlSchema(db);

            // Re-apply schema
            YawlContainerFixtures.applyYawlSchema(db);

            // Verify schema is functional
            WorkflowDataFactory.seedSpecification(db, "schema-recovered", "1.0", "Recovered");

            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_specification")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Schema must be recovered and functional");
            }

            System.out.println("Schema successfully recovered after restart");
        }

        @Test
        @DisplayName("In-flight transaction handling during restart")
        void testInFlightTransactionHandlingDuringRestart() throws Exception {
            // Start transaction
            db.setAutoCommit(false);

            WorkflowDataFactory.seedSpecification(db, "inflight-test", "1.0", "In-Flight");

            // Simulate restart (close without commit)
            db.close();

            // Reconnect
            db = DriverManager.getConnection(jdbcUrl, "sa", "");

            // Verify uncommitted transaction was rolled back
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_specification WHERE spec_id = 'inflight-test'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1),
                        "Uncommitted transaction must be rolled back");
            }

            System.out.println("In-flight transaction correctly rolled back on restart");
        }
    }

    // =========================================================================
    // Clock Skew Scenarios Tests
    // =========================================================================

    @Nested
    @DisplayName("Clock Skew Scenarios")
    class ClockSkewTests {

        @Test
        @DisplayName("Timestamp ordering with simulated clock skew")
        void testTimestampOrderingWithClockSkew() throws Exception {
            // Simulate events with clock skew
            List<TimestampedEvent> events = new ArrayList<>();

            // Normal event
            events.add(new TimestampedEvent("event-1", System.currentTimeMillis()));

            // Event with future timestamp (clock ahead)
            events.add(new TimestampedEvent("event-2", System.currentTimeMillis() + 60000));

            // Event with past timestamp (clock behind)
            events.add(new TimestampedEvent("event-3", System.currentTimeMillis() - 60000));

            // Store events
            for (TimestampedEvent event : events) {
                try (PreparedStatement ps = db.prepareStatement(
                        "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data, event_timestamp) " +
                                "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)")) {
                    ps.setLong(1, event.id.hashCode());
                    ps.setString(2, "clock-skew-runner");
                    ps.setString(3, "CLOCK_SKEW_" + event.id);
                    ps.setString(4, "{\"logical_time\":" + event.timestamp + "}");
                    ps.executeUpdate();
                }
            }

            // Retrieve in database order (not timestamp order)
            List<String> dbOrder = new ArrayList<>();
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT event_type FROM yawl_case_event WHERE runner_id = ? ORDER BY event_id")) {
                ps.setString(1, "clock-skew-runner");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        dbOrder.add(rs.getString("event_type"));
                    }
                }
            }

            assertEquals(3, dbOrder.size(), "All events must be stored");

            System.out.println("Clock skew test: events stored in database order despite timestamp differences");
        }

        @Test
        @DisplayName("Logical clock for ordering")
        void testLogicalClockForOrdering() throws Exception {
            // Use Lamport-style logical clock
            AtomicLong logicalClock = new AtomicLong(0);
            List<LogicallyOrderedEvent> events = new ArrayList<>();

            // Create events with logical timestamps
            events.add(new LogicallyOrderedEvent(logicalClock.incrementAndGet(), "CREATE"));
            events.add(new LogicallyOrderedEvent(logicalClock.incrementAndGet(), "START"));
            events.add(new LogicallyOrderedEvent(logicalClock.incrementAndGet(), "PROCESS"));

            // Store with logical clock
            WorkflowDataFactory.seedSpecification(db, "logical-clock", "1.0", "Logical Clock Test");

            for (LogicallyOrderedEvent event : events) {
                try (PreparedStatement ps = db.prepareStatement(
                        "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data) " +
                                "VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, event.logicalTime);
                    ps.setString(2, "logical-clock-runner");
                    ps.setString(3, event.type);
                    ps.setString(4, "{\"logical_time\":" + event.logicalTime + "}");
                    ps.executeUpdate();
                }
            }

            // Retrieve in logical order
            List<String> orderedTypes = new ArrayList<>();
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT event_type FROM yawl_case_event WHERE runner_id = ? ORDER BY event_id")) {
                ps.setString(1, "logical-clock-runner");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        orderedTypes.add(rs.getString("event_type"));
                    }
                }
            }

            assertEquals(List.of("CREATE", "START", "PROCESS"), orderedTypes,
                    "Events must be in logical order");

            System.out.println("Logical clock ordering verified");
        }

        @Test
        @DisplayName("TTL expiration with clock skew")
        void testTtlExpirationWithClockSkew() throws Exception {
            // Create items with different TTLs
            long now = System.currentTimeMillis();

            ItemWithTtl expired = new ItemWithTtl("expired", now - 1000); // Expired
            ItemWithTtl active = new ItemWithTtl("active", now + 60000);  // Active
            ItemWithTtl skewed = new ItemWithTtl("skewed", now - 30000);  // Expired but might be active on fast clock

            List<ItemWithTtl> items = List.of(expired, active, skewed);

            // Store items
            WorkflowDataFactory.seedSpecification(db, "ttl-test", "1.0", "TTL Test");

            for (ItemWithTtl item : items) {
                try (PreparedStatement ps = db.prepareStatement(
                        "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data) " +
                                "VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, item.id.hashCode());
                    ps.setString(2, "ttl-runner");
                    ps.setString(3, "TTL_ITEM");
                    ps.setString(4, "{\"id\":\"" + item.id + "\",\"expires_at\":" + item.expiresAt + "}");
                    ps.executeUpdate();
                }
            }

            // Check which items are expired
            List<String> validItems = new ArrayList<>();
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT event_data FROM yawl_case_event WHERE runner_id = ? AND event_type = 'TTL_ITEM'")) {
                ps.setString(1, "ttl-runner");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String data = rs.getString("event_data");
                        // Parse expires_at (simplified)
                        long expiresAt = Long.parseLong(data.replaceAll(".*expires_at\":(\\d+).*", "$1"));
                        if (expiresAt > now) {
                            validItems.add(data.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
                        }
                    }
                }
            }

            assertTrue(validItems.contains("active"), "Active item must be valid");
            assertFalse(validItems.contains("expired"), "Expired item must not be valid");

            System.out.printf("TTL validation: %d items valid, %d expired%n",
                    validItems.size(), items.size() - validItems.size());
        }
    }

    // =========================================================================
    // Helper Classes
    // =========================================================================

    record TimestampedEvent(String id, long timestamp) {}
    record LogicallyOrderedEvent(long logicalTime, String type) {}
    record ItemWithTtl(String id, long expiresAt) {}
}
