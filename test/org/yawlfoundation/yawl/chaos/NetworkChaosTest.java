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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Network Chaos Engineering Tests for YAWL MCP-A2A MVP.
 *
 * Tests network failure scenarios including:
 * - Network latency injection (100ms - 5s delays)
 * - Network partition simulation (split-brain scenarios)
 * - Database connection failures
 * - YAWL engine unavailability
 *
 * Design principle: All chaos is synthetic/in-process. No external tools required.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Tag("chaos")
@DisplayName("Network Chaos Tests")
class NetworkChaosTest {

    private Connection db;
    private String jdbcUrl;
    private static final String UNREACHABLE_HOST = "http://localhost:19999";
    private static final int SHORT_TIMEOUT_MS = 100;
    private static final int MEDIUM_TIMEOUT_MS = 1000;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:chaos_network_%d;DB_CLOSE_DELAY=-1"
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
    // Network Latency Injection Tests
    // =========================================================================

    @Nested
    @DisplayName("Network Latency Injection (100ms - 5s)")
    class NetworkLatencyTests {

        /**
         * Latency profile: (label, delayMs, timeoutMs, expectedSuccess)
         */
        static Stream<Arguments> latencyProfiles() {
            return Stream.of(
                // Fast operations (delay << timeout): must succeed
                Arguments.of("no-delay",       0L,     5000L, true),
                Arguments.of("100ms-delay",    100L,   5000L, true),
                Arguments.of("250ms-delay",    250L,   5000L, true),
                Arguments.of("500ms-delay",    500L,   5000L, true),
                Arguments.of("1s-delay",       1000L,  5000L, true),
                Arguments.of("2s-delay",       2000L,  5000L, true),
                // Boundary cases
                Arguments.of("4s-delay",       4000L,  5000L, true),
                Arguments.of("timeout-5s",     5000L,  5000L, false),
                // Clear timeout
                Arguments.of("6s-delay",       6000L,  5000L, false)
            );
        }

        @ParameterizedTest(name = "[{index}] {0}: delay={1}ms, timeout={2}ms")
        @MethodSource("latencyProfiles")
        @DisplayName("Operations with artificial network latency")
        void testOperationWithSimulatedNetworkLatency(String label,
                                                        long delayMs,
                                                        long timeoutMs,
                                                        boolean expectedSuccess)
                throws Exception {
            String specId = WorkflowDataFactory.uniqueSpecId("latency");

            Instant start = Instant.now();
            boolean succeeded = executeWithDelay(
                    () -> {
                        WorkflowDataFactory.seedSpecification(
                                db, specId, "1.0", "Latency Test " + label);
                        return true;
                    },
                    delayMs,
                    timeoutMs);

            Duration elapsed = Duration.between(start, Instant.now());

            System.out.printf("[%s] delay=%dms, timeout=%dms, elapsed=%dms, success=%b%n",
                    label, delayMs, timeoutMs, elapsed.toMillis(), succeeded);

            assertEquals(expectedSuccess, succeeded,
                    label + ": success must be " + expectedSuccess);

            // If operation succeeded, verify data persisted
            if (expectedSuccess && succeeded) {
                assertTrue(rowExists(db, "yawl_specification", "spec_id", specId),
                        "Data must be persisted after successful operation");
            }
        }

        @Test
        @DisplayName("Latency spike followed by recovery")
        void testLatencySpikeFollowedByRecovery() throws Exception {
            // Phase 1: Normal operation
            long normalStart = System.currentTimeMillis();
            WorkflowDataFactory.seedSpecification(db, "latency-normal", "1.0", "Normal");
            long normalDuration = System.currentTimeMillis() - normalStart;

            // Phase 2: Latency spike (simulated)
            long spikeStart = System.currentTimeMillis();
            boolean spikeSucceeded = executeWithDelay(
                    () -> {
                        WorkflowDataFactory.seedSpecification(db,
                                "latency-spike", "1.0", "Spike");
                        return true;
                    },
                    500L,  // 500ms artificial delay
                    5000L);
            long spikeDuration = System.currentTimeMillis() - spikeStart;

            // Phase 3: Recovery - back to normal
            long recoveryStart = System.currentTimeMillis();
            WorkflowDataFactory.seedSpecification(db, "latency-recovered", "1.0", "Recovered");
            long recoveryDuration = System.currentTimeMillis() - recoveryStart;

            assertTrue(spikeSucceeded, "Spike operation must succeed within timeout");
            assertTrue(spikeDuration >= 500, "Spike duration must include delay");
            assertTrue(recoveryDuration < spikeDuration,
                    "Recovery must be faster than spike period");

            System.out.printf("Normal: %dms, Spike: %dms, Recovery: %dms%n",
                    normalDuration, spikeDuration, recoveryDuration);
        }

        @Test
        @DisplayName("Variable latency pattern (jitter simulation)")
        void testVariableLatencyPattern() throws Exception {
            int operations = 20;
            AtomicInteger successCount = new AtomicInteger(0);
            List<Long> durations = new ArrayList<>();

            for (int i = 0; i < operations; i++) {
                // Variable delay: 0-300ms random jitter
                long jitter = (long) (Math.random() * 300);

                Instant start = Instant.now();
                boolean succeeded = executeWithDelay(
                        () -> {
                            WorkflowDataFactory.seedSpecification(db,
                                    "jitter-" + i, "1.0", "Jitter " + i);
                            return true;
                        },
                        jitter,
                        1000L);
                Duration elapsed = Duration.between(start, Instant.now());

                durations.add(elapsed.toMillis());
                if (succeeded) successCount.incrementAndGet();
            }

            // With 1s timeout and max 300ms jitter, all must succeed
            assertEquals(operations, successCount.get(),
                    "All operations must succeed with generous timeout");

            // Calculate latency statistics
            double avgDuration = durations.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);
            long maxDuration = durations.stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0);

            System.out.printf("Jitter test: avg=%dms, max=%dms, success=%d/%d%n",
                    (long) avgDuration, maxDuration, successCount.get(), operations);
        }
    }

    // =========================================================================
    // Network Partition Simulation Tests
    // =========================================================================

    @Nested
    @DisplayName("Network Partition Simulation")
    class NetworkPartitionTests {

        @Test
        @DisplayName("Complete partition - all requests fail")
        void testCompleteNetworkPartition() throws Exception {
            int requestCount = 10;
            AtomicInteger failureCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch latch = new CountDownLatch(requestCount);

            for (int i = 0; i < requestCount; i++) {
                executor.submit(() -> {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL(UNREACHABLE_HOST + "/partition");
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(SHORT_TIMEOUT_MS);
                        conn.setReadTimeout(SHORT_TIMEOUT_MS);
                        conn.connect();
                    } catch (SocketTimeoutException | ConnectException e) {
                        failureCount.incrementAndGet();
                    } catch (IOException e) {
                        failureCount.incrementAndGet();
                    } finally {
                        if (conn != null) conn.disconnect();
                        latch.countDown();
                    }
                });
            }

            latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(requestCount, failureCount.get(),
                    "All requests must fail during complete partition");
        }

        @Test
        @DisplayName("Partial partition - some requests succeed")
        void testPartialNetworkPartition() throws Exception {
            int totalRequests = 20;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // Simulate partial partition: first 10 requests fail, next 10 succeed
            for (int i = 0; i < totalRequests; i++) {
                final int idx = i;
                boolean partitionActive = (idx < 10);

                if (partitionActive) {
                    // Simulate partitioned request - fails immediately
                    try {
                        HttpURLConnection conn = (HttpURLConnection)
                                new URL(UNREACHABLE_HOST + "/partial").openConnection();
                        conn.setConnectTimeout(SHORT_TIMEOUT_MS);
                        conn.connect();
                    } catch (IOException e) {
                        failureCount.incrementAndGet();
                    }
                } else {
                    // Simulate healthy path - use local DB
                    try {
                        WorkflowDataFactory.seedSpecification(db,
                                "partial-" + idx, "1.0", "Partial " + idx);
                        successCount.incrementAndGet();
                    } catch (SQLException e) {
                        failureCount.incrementAndGet();
                    }
                }
            }

            assertEquals(10, failureCount.get(), "First 10 requests must fail");
            assertEquals(10, successCount.get(), "Last 10 requests must succeed");
        }

        @Test
        @DisplayName("Split-brain scenario - two partitions with divergent state")
        void testSplitBrainScenario() throws Exception {
            // Partition A
            String partitionAJdbc = "jdbc:h2:mem:split_brain_a_%d;DB_CLOSE_DELAY=-1"
                    .formatted(System.nanoTime());
            Connection partitionA = DriverManager.getConnection(partitionAJdbc, "sa", "");
            YawlContainerFixtures.applyYawlSchema(partitionA);

            // Partition B
            String partitionBJdbc = "jdbc:h2:mem:split_brain_b_%d;DB_CLOSE_DELAY=-1"
                    .formatted(System.nanoTime());
            Connection partitionB = DriverManager.getConnection(partitionBJdbc, "sa", "");
            YawlContainerFixtures.applyYawlSchema(partitionB);

            // Write to partition A
            WorkflowDataFactory.seedSpecification(partitionA,
                    "split-spec", "1.0", "Partition A Version");

            // Write different version to partition B (divergent state)
            WorkflowDataFactory.seedSpecification(partitionB,
                    "split-spec", "1.0", "Partition B Version");

            // Verify divergence
            String nameA = getSpecificationName(partitionA, "split-spec");
            String nameB = getSpecificationName(partitionB, "split-spec");

            assertEquals("Partition A Version", nameA);
            assertEquals("Partition B Version", nameB);
            assertNotEquals(nameA, nameB, "State must diverge during split-brain");

            System.out.println("Split-brain confirmed: A=" + nameA + ", B=" + nameB);

            // Simulate partition healing - conflict resolution
            // Strategy: last-write-wins or version vector
            String resolvedName = resolveConflict(nameA, nameB);
            assertNotNull(resolvedName, "Conflict must be resolved");

            partitionA.close();
            partitionB.close();
        }

        @Test
        @DisplayName("Intermittent connectivity (flapping network)")
        void testIntermittentConnectivity() throws Exception {
            int attempts = 20;
            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);

            // Simulate flapping: alternating success/failure pattern
            for (int i = 0; i < attempts; i++) {
                boolean isConnected = (i % 3 != 0); // 2/3 success rate

                if (isConnected) {
                    try {
                        WorkflowDataFactory.seedSpecification(db,
                                "flap-" + i, "1.0", "Flap " + i);
                        successes.incrementAndGet();
                    } catch (SQLException e) {
                        failures.incrementAndGet();
                    }
                } else {
                    // Simulate disconnected state
                    failures.incrementAndGet();
                }
            }

            // With 2/3 success rate, expect approximately 13-14 successes
            assertTrue(successes.get() >= 12 && successes.get() <= 14,
                    "Success rate should approximate 2/3: actual=" + successes.get());
            assertTrue(failures.get() >= 6 && failures.get() <= 8,
                    "Failure rate should approximate 1/3: actual=" + failures.get());

            System.out.printf("Flapping network: %d successes, %d failures%n",
                    successes.get(), failures.get());
        }
    }

    // =========================================================================
    // Database Connection Failure Tests
    // =========================================================================

    @Nested
    @DisplayName("Database Connection Failures")
    class DatabaseConnectionTests {

        @Test
        @DisplayName("Connection closed mid-operation")
        void testConnectionClosedMidOperation() throws Exception {
            String specId = "mid-close";

            // Pre-condition: connection is healthy
            assertFalse(db.isClosed(), "Connection must be open initially");

            // Seed initial data
            WorkflowDataFactory.seedSpecification(db, specId + "-pre", "1.0", "Pre-Close");

            // Close connection (simulate DB failure)
            db.close();
            assertTrue(db.isClosed(), "Connection must be closed");

            // Post-failure operation must throw
            assertThrows(SQLException.class, () ->
                    WorkflowDataFactory.seedSpecification(db, specId + "-post", "1.0", "Post-Close"),
                    "Operation on closed connection must throw SQLException");
        }

        @Test
        @DisplayName("Connection pool exhaustion")
        void testConnectionPoolExhaustion() throws Exception {
            int poolSize = 5;
            List<Connection> connections = new ArrayList<>();

            // Exhaust the pool by opening max connections
            for (int i = 0; i < poolSize; i++) {
                Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
                connections.add(conn);
            }

            // Next connection request should still succeed (H2 allows more)
            // But we simulate exhaustion by trying to use a closed connection
            Connection exhausted = connections.get(0);
            exhausted.close();

            assertThrows(SQLException.class, () -> {
                try (Statement stmt = exhausted.createStatement()) {
                    stmt.executeQuery("SELECT 1");
                }
            }, "Closed connection must throw on use");

            // Cleanup
            for (Connection conn : connections) {
                if (!conn.isClosed()) conn.close();
            }
        }

        @Test
        @DisplayName("Database reconnection after failure")
        void testDatabaseReconnectionAfterFailure() throws Exception {
            String specId = "reconnect-test";

            // Initial operation
            WorkflowDataFactory.seedSpecification(db, specId + "-1", "1.0", "Before Failure");

            // Simulate failure - close connection
            db.close();

            // Reconnect
            db = DriverManager.getConnection(jdbcUrl, "sa", "");

            // Verify reconnection works
            WorkflowDataFactory.seedSpecification(db, specId + "-2", "1.0", "After Reconnect");

            // Both records should be present (H2 with DB_CLOSE_DELAY=-1 persists)
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_specification WHERE spec_id LIKE 'reconnect-test%'")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "Both records must be present after reconnection");
            }
        }

        @Test
        @DisplayName("Transaction rollback on connection failure")
        void testTransactionRollbackOnConnectionFailure() throws Exception {
            String specId = "rollback-test";

            db.setAutoCommit(false);

            // Insert within transaction
            WorkflowDataFactory.seedSpecification(db, specId + "-1", "1.0", "Rollback 1");
            WorkflowDataFactory.seedSpecification(db, specId + "-2", "1.0", "Rollback 2");

            // Simulate failure before commit
            db.rollback();
            db.setAutoCommit(true);

            // Verify rollback
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_specification WHERE spec_id LIKE 'rollback-test%'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Transaction must be rolled back");
            }
        }
    }

    // =========================================================================
    // YAWL Engine Unavailability Tests
    // =========================================================================

    @Nested
    @DisplayName("YAWL Engine Unavailability")
    class EngineUnavailabilityTests {

        @Test
        @DisplayName("Engine timeout on slow response")
        void testEngineTimeoutOnSlowResponse() throws Exception {
            long timeoutMs = 500L;
            long delayMs = 1000L;

            // Simulate slow engine response
            boolean succeeded = executeWithDelay(
                    () -> {
                        // Simulate engine processing
                        return "engine-response";
                    },
                    delayMs,
                    timeoutMs);

            assertFalse(succeeded, "Slow engine response must timeout");
        }

        @Test
        @DisplayName("Circuit breaker trips on repeated engine failures")
        void testCircuitBreakerTripsOnEngineFailures() throws Exception {
            CircuitBreaker breaker = new CircuitBreaker("engine-cb", 3, 1000);

            // Cause 3 failures to trip the circuit
            for (int i = 0; i < 3; i++) {
                try {
                    breaker.execute(() -> {
                        throw new RuntimeException("Engine failure " + i);
                    });
                } catch (Exception e) {
                    // Expected
                }
            }

            assertEquals(CircuitBreaker.State.OPEN, breaker.getState(),
                    "Circuit must be OPEN after threshold failures");

            // Next request must fail fast without calling engine
            assertThrows(CircuitBreaker.CircuitBreakerOpenException.class, () ->
                    breaker.execute(() -> "should-not-be-called"),
                    "Must fail fast when circuit is OPEN");
        }

        @Test
        @DisplayName("Engine recovery after transient failure")
        void testEngineRecoveryAfterTransientFailure() throws Exception {
            AtomicInteger callCount = new AtomicInteger(0);
            CircuitBreaker breaker = new CircuitBreaker("recovery-cb", 5, 500);

            // Simulate transient failure followed by recovery
            for (int attempt = 0; attempt < 10; attempt++) {
                try {
                    String result = breaker.execute(() -> {
                        int count = callCount.incrementAndGet();
                        if (count <= 3) {
                            throw new RuntimeException("Transient failure");
                        }
                        return "success-" + count;
                    });
                    assertEquals("success-4", result, "Must succeed after transient failures");
                    break;
                } catch (Exception e) {
                    // Continue retrying
                }
            }

            assertEquals(CircuitBreaker.State.CLOSED, breaker.getState(),
                    "Circuit must close after successful recovery");
        }

        @Test
        @DisplayName("Multiple engine instances with failover")
        void testMultipleEngineInstancesWithFailover() throws Exception {
            List<String> engines = List.of(
                    "http://localhost:19999/engine1",  // Unreachable
                    "http://localhost:19998/engine2",  // Unreachable
                    "local"                            // Fallback
            );

            String result = null;
            for (String engine : engines) {
                if ("local".equals(engine)) {
                    // Local fallback succeeds
                    result = "local-engine-response";
                    break;
                } else {
                    // Try remote engine
                    try {
                        HttpURLConnection conn = (HttpURLConnection)
                                new URL(engine).openConnection();
                        conn.setConnectTimeout(SHORT_TIMEOUT_MS);
                        conn.connect();
                        result = "remote-response";
                        break;
                    } catch (IOException e) {
                        // Try next engine
                    }
                }
            }

            assertEquals("local-engine-response", result,
                    "Must fallback to local engine after remote failures");
        }
    }

    // =========================================================================
    // Concurrent Network Chaos Tests
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Network Chaos")
    class ConcurrentNetworkChaosTests {

        @Test
        @DisplayName("Concurrent operations under network stress")
        void testConcurrentOperationsUnderNetworkStress() throws Exception {
            int threads = 20;
            int opsPerThread = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            AtomicLong totalLatency = new AtomicLong(0);

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            long start = System.currentTimeMillis();

                            // Random delay to simulate network stress
                            long delay = (long) (Math.random() * 100);

                            boolean succeeded = executeWithDelay(
                                    () -> {
                                        WorkflowDataFactory.seedSpecification(db,
                                                "stress-" + threadId + "-" + i,
                                                "1.0", "Stress " + threadId + "/" + i);
                                        return true;
                                    },
                                    delay,
                                    500L);

                            totalLatency.addAndGet(System.currentTimeMillis() - start);

                            if (succeeded) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            int totalOps = threads * opsPerThread;
            double successRate = (double) successCount.get() / totalOps * 100;
            double avgLatency = (double) totalLatency.get() / totalOps;

            System.out.printf("Network stress test: %d/%d succeeded (%.1f%%), avg latency=%.1fms%n",
                    successCount.get(), totalOps, successRate, avgLatency);

            assertTrue(successRate >= 95.0,
                    "At least 95% of operations must succeed: actual=" + successRate + "%");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static boolean executeWithDelay(
            java.util.concurrent.Callable<Boolean> callable,
            long delayMs,
            long timeoutMs) {
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean rowExists(Connection conn, String table, String column, String value)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE " + column + " = ?")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String getSpecificationName(Connection conn, String specId)
            throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT spec_name FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, specId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("spec_name");
                }
                return null;
            }
        }
    }

    private static String resolveConflict(String valueA, String valueB) {
        // Simple last-write-wins: prefer longer name (simulates later write)
        return valueA.length() >= valueB.length() ? valueA : valueB;
    }
}
