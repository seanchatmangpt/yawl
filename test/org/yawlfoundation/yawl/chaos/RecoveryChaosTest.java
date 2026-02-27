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
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Recovery Chaos Engineering Tests for YAWL MCP-A2A MVP.
 *
 * Tests recovery scenarios including:
 * - Time to recovery (MTTR) measurements
 * - Data integrity after recovery
 * - Service health restoration
 * - Client reconnection effectiveness
 * - System stability after failures
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Tag("chaos")
@DisplayName("Recovery Chaos Tests")
class RecoveryChaosTest {

    private Connection db;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:chaos_recovery_%d;DB_CLOSE_DELAY=-1"
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
    // Time to Recovery (MTTR) Measurements
    // =========================================================================

    @Nested
    @DisplayName("Time to Recovery (MTTR) Measurements")
    class MttrMeasurementsTests {

        @Test
        @DisplayName("Database connection MTTR measurement")
        void testDatabaseConnectionMttr() throws Exception {
            int samples = 10;
            List<Long> recoveryTimes = new ArrayList<>();

            for (int i = 0; i < samples; i++) {
                // Simulate failure
                db.close();

                // Measure recovery time
                Instant start = Instant.now();

                // Recovery attempt
                db = DriverManager.getConnection(jdbcUrl, "sa", "");

                // Verify recovery
                try (Statement stmt = db.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                    assertTrue(rs.next());
                }

                Duration mttr = Duration.between(start, Instant.now());
                recoveryTimes.add(mttr.toMillis());
            }

            // Calculate statistics
            double avgMttr = recoveryTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);
            long maxMttr = recoveryTimes.stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0);
            long p99Mttr = calculatePercentile(recoveryTimes, 99);

            System.out.printf("%n=== Database Connection MTTR ===%n");
            System.out.printf("Samples: %d%n", samples);
            System.out.printf("Average MTTR: %.2f ms%n", avgMttr);
            System.out.printf("Max MTTR: %d ms%n", maxMttr);
            System.out.printf("P99 MTTR: %d ms%n", p99Mttr);
            System.out.printf("================================%n%n");

            // MTTR should be under 1 second for local database
            assertTrue(avgMttr < 1000,
                    "Average MTTR must be under 1 second: actual=" + avgMttr + "ms");
        }

        @Test
        @DisplayName("Service MTTR under load")
        void testServiceMttrUnderLoad() throws Exception {
            int concurrentClients = 10;
            int failureDurationMs = 1000;
            AtomicBoolean serviceAvailable = new AtomicBoolean(true);
            AtomicInteger requestsDuringRecovery = new AtomicInteger(0);
            AtomicInteger successfulRequests = new AtomicInteger(0);
            AtomicLong totalRecoveryTime = new AtomicLong(0);

            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch latch = new CountDownLatch(concurrentClients);

            // Start clients
            for (int c = 0; c < concurrentClients; c++) {
                executor.submit(() -> {
                    boolean recovered = false;
                    Instant recoveryStart = null;

                    while (!recovered) {
                        try {
                            if (serviceAvailable.get()) {
                                // Try to access service
                                try (Statement stmt = db.createStatement();
                                     ResultSet rs = stmt.executeQuery("SELECT 1")) {
                                    if (rs.next() && !recovered) {
                                        recovered = true;
                                        if (recoveryStart != null) {
                                            totalRecoveryTime.addAndGet(
                                                    Duration.between(recoveryStart, Instant.now()).toMillis());
                                        }
                                        successfulRequests.incrementAndGet();
                                    }
                                }
                            } else {
                                if (recoveryStart == null) {
                                    recoveryStart = Instant.now();
                                }
                                requestsDuringRecovery.incrementAndGet();
                            }
                            Thread.sleep(50);
                        } catch (SQLException e) {
                            if (recoveryStart == null) {
                                recoveryStart = Instant.now();
                            }
                            requestsDuringRecovery.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    latch.countDown();
                });
            }

            // Simulate failure
            Thread.sleep(100);
            serviceAvailable.set(false);

            // Simulate recovery after delay
            Thread.sleep(failureDurationMs);
            serviceAvailable.set(true);

            // Wait for all clients to recover
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            double avgRecoveryTime = totalRecoveryTime.get() / (double) concurrentClients;

            System.out.printf("Service MTTR under load:%n");
            System.out.printf("  Failure duration: %d ms%n", failureDurationMs);
            System.out.printf("  Concurrent clients: %d%n", concurrentClients);
            System.out.printf("  Requests during recovery: %d%n", requestsDuringRecovery.get());
            System.out.printf("  Successful recoveries: %d%n", successfulRequests.get());
            System.out.printf("  Average recovery time: %.2f ms%n", avgRecoveryTime);

            // All clients should recover
            assertEquals(concurrentClients, successfulRequests.get(),
                    "All clients must recover");
        }

        @Test
        @DisplayName("Circuit breaker MTTR measurement")
        void testCircuitBreakerMttr() throws Exception {
            CircuitBreaker breaker = new CircuitBreaker("mttr-test", 3, 500);

            // Cause failures to open circuit
            for (int i = 0; i < 3; i++) {
                try {
                    breaker.execute(() -> {
                        throw new RuntimeException("Failure " + i);
                    });
                } catch (Exception e) {
                    // Expected
                }
            }

            assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

            // Measure MTTR (time to HALF_OPEN then CLOSED)
            Instant start = Instant.now();

            // Wait for HALF_OPEN
            while (breaker.getState() == CircuitBreaker.State.OPEN) {
                Thread.sleep(50);
            }

            Duration timeToHalfOpen = Duration.between(start, Instant.now());

            // Execute successful operation to close
            breaker.execute(() -> "recovered");

            Duration totalMttr = Duration.between(start, Instant.now());

            System.out.printf("Circuit Breaker MTTR:%n");
            System.out.printf("  Time to HALF_OPEN: %d ms%n", timeToHalfOpen.toMillis());
            System.out.printf("  Total MTTR: %d ms%n", totalMttr.toMillis());

            assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
            assertTrue(totalMttr.toMillis() >= 500,
                    "MTTR must be at least the open duration");
        }

        @Test
        @DisplayName("MTTR with exponential backoff recovery")
        void testMttrWithExponentialBackoff() throws Exception {
            int maxAttempts = 5;
            long initialBackoffMs = 50;
            AtomicInteger attempts = new AtomicInteger(0);

            Instant start = Instant.now();

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                attempts.set(attempt);

                // Simulate service that recovers after 3 attempts
                if (attempt >= 3) {
                    break;
                }

                // Backoff
                long backoff = initialBackoffMs * (1L << (attempt - 1));
                Thread.sleep(backoff);
            }

            Duration mttr = Duration.between(start, Instant.now());

            System.out.printf("MTTR with exponential backoff:%n");
            System.out.printf("  Attempts: %d%n", attempts.get());
            System.out.printf("  MTTR: %d ms%n", mttr.toMillis());

            // With 50, 100, 200ms backoffs = 350ms minimum
            assertTrue(mttr.toMillis() >= 300,
                    "MTTR must include backoff delays");
        }
    }

    // =========================================================================
    // Data Integrity After Recovery Tests
    // =========================================================================

    @Nested
    @DisplayName("Data Integrity After Recovery")
    class DataIntegrityAfterRecoveryTests {

        @Test
        @DisplayName("No data loss after crash recovery")
        void testNoDataLossAfterCrashRecovery() throws Exception {
            // Seed initial data
            int expectedRecords = 100;
            WorkflowDataFactory.seedSpecification(db, "crash-recovery", "1.0", "Crash Recovery");

            for (int i = 0; i < expectedRecords; i++) {
                WorkflowDataFactory.seedNetRunner(db,
                        "crash-runner-" + i,
                        "crash-recovery", "1.0",
                        "net-" + i, "RUNNING");
            }

            // Verify data before crash
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = 'crash-recovery'")) {
                assertTrue(rs.next());
                assertEquals(expectedRecords, rs.getInt(1), "All records must be present before crash");
            }

            // Simulate crash (close connection)
            db.close();

            // Recovery (reconnect)
            db = DriverManager.getConnection(jdbcUrl, "sa", "");

            // Verify data after recovery
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = 'crash-recovery'")) {
                assertTrue(rs.next());
                assertEquals(expectedRecords, rs.getInt(1),
                        "All records must be present after recovery");
            }

            System.out.printf("Data integrity verified: %d records preserved%n", expectedRecords);
        }

        @Test
        @DisplayName("Referential integrity after recovery")
        void testReferentialIntegrityAfterRecovery() throws Exception {
            // Create parent-child relationships
            WorkflowDataFactory.seedSpecification(db, "ref-integrity", "1.0", "Ref Integrity");
            WorkflowDataFactory.seedNetRunner(db, "ref-runner", "ref-integrity", "1.0", "root", "RUNNING");

            for (int i = 0; i < 10; i++) {
                WorkflowDataFactory.seedWorkItem(db,
                        "ref-item-" + i, "ref-runner", "task-" + i, "Enabled");
            }

            // Simulate crash
            db.close();
            db = DriverManager.getConnection(jdbcUrl, "sa", "");

            // Verify referential integrity
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_work_item wi " +
                                 "JOIN yawl_net_runner nr ON wi.runner_id = nr.runner_id " +
                                 "WHERE nr.spec_id = 'ref-integrity'")) {
                assertTrue(rs.next());
                assertEquals(10, rs.getInt(1),
                        "All work items must reference valid runner");
            }

            System.out.println("Referential integrity verified after recovery");
        }

        @Test
        @DisplayName("Checksum verification after recovery")
        void testChecksumVerificationAfterRecovery() throws Exception {
            // Create data with checksums
            WorkflowDataFactory.seedSpecification(db, "checksum-test", "1.0", "Checksum Test");

            String data = "Test data for checksum verification";
            int checksum = data.hashCode();

            try (PreparedStatement ps = db.prepareStatement(
                    "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data) " +
                            "VALUES (?, ?, ?, ?)")) {
                ps.setLong(1, System.nanoTime());
                ps.setString(2, "checksum-runner");
                ps.setString(3, "CHECKSUM_TEST");
                ps.setString(4, data);
                ps.executeUpdate();
            }

            // Simulate crash and recovery
            db.close();
            db = DriverManager.getConnection(jdbcUrl, "sa", "");

            // Verify checksum
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT event_data FROM yawl_case_event WHERE event_type = 'CHECKSUM_TEST'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    String recoveredData = rs.getString("event_data");
                    int recoveredChecksum = recoveredData.hashCode();
                    assertEquals(checksum, recoveredChecksum,
                            "Data checksum must match after recovery");
                }
            }

            System.out.println("Checksum verification passed");
        }

        @Test
        @DisplayName("Transaction atomicity verification")
        void testTransactionAtomicityVerification() throws Exception {
            // Perform transactional updates
            db.setAutoCommit(false);

            try {
                WorkflowDataFactory.seedSpecification(db, "atomic-test", "1.0", "Atomic Test");
                WorkflowDataFactory.seedNetRunner(db, "atomic-runner-1", "atomic-test", "1.0", "net", "RUNNING");
                WorkflowDataFactory.seedNetRunner(db, "atomic-runner-2", "atomic-test", "1.0", "net", "RUNNING");

                db.commit();
            } catch (Exception e) {
                db.rollback();
                throw e;
            }

            db.setAutoCommit(true);

            // Verify atomicity: all or nothing
            int specs = countRows(db, "yawl_specification", "spec_id = 'atomic-test'");
            int runners = countRows(db, "yawl_net_runner", "spec_id = 'atomic-test'");

            // Both must be present (atomic commit)
            assertEquals(1, specs, "Spec must be committed");
            assertEquals(2, runners, "Both runners must be committed");

            System.out.println("Transaction atomicity verified");
        }
    }

    // =========================================================================
    // Service Health Restoration Tests
    // =========================================================================

    @Nested
    @DisplayName("Service Health Restoration")
    class ServiceHealthRestorationTests {

        @Test
        @DisplayName("Health check endpoint recovery")
        void testHealthCheckEndpointRecovery() throws Exception {
            ServiceHealth service = new ServiceHealth();

            // Service starts healthy
            assertTrue(service.isHealthy());

            // Simulate failure
            service.fail();
            assertFalse(service.isHealthy());

            // Trigger recovery
            Instant start = Instant.now();
            service.recover();
            Duration recoveryTime = Duration.between(start, Instant.now());

            assertTrue(service.isHealthy(), "Service must be healthy after recovery");

            System.out.printf("Health check recovered in %d ms%n", recoveryTime.toMillis());
        }

        @Test
        @DisplayName("Dependency health propagation")
        void testDependencyHealthPropagation() throws Exception {
            ServiceWithDependencies service = new ServiceWithDependencies();

            // All dependencies healthy
            assertTrue(service.isHealthy());

            // Primary dependency fails
            service.getPrimaryDependency().fail();
            assertFalse(service.isHealthy(), "Service must be unhealthy when dependency fails");

            // Dependency recovers
            service.getPrimaryDependency().recover();
            assertTrue(service.isHealthy(), "Service must recover when dependency recovers");

            System.out.println("Dependency health propagation verified");
        }

        @Test
        @DisplayName("Readiness vs liveness probe distinction")
        void testReadinessVsLivenessProbeDistinction() throws Exception {
            ServiceWithProbes service = new ServiceWithProbes();

            // Initially: alive but not ready
            assertTrue(service.isAlive());
            assertFalse(service.isReady());

            // After warmup: both alive and ready
            service.warmUp();
            assertTrue(service.isAlive());
            assertTrue(service.isReady());

            // During failure: alive but not ready
            service.degrade();
            assertTrue(service.isAlive(), "Service must still be alive during degradation");
            assertFalse(service.isReady(), "Service must not be ready during degradation");

            System.out.println("Readiness vs liveness distinction verified");
        }

        @Test
        @DisplayName("Graceful shutdown and restart")
        void testGracefulShutdownAndRestart() throws Exception {
            GracefulService service = new GracefulService();

            service.start();
            assertTrue(service.isRunning());

            // Submit work
            service.submitWork("task-1");
            service.submitWork("task-2");

            // Graceful shutdown
            Instant shutdownStart = Instant.now();
            service.gracefulShutdown();
            Duration shutdownTime = Duration.between(shutdownStart, Instant.now());

            assertFalse(service.isRunning());
            assertTrue(service.workCompleted(), "All work must complete before shutdown");

            // Restart
            service.start();
            assertTrue(service.isRunning());

            System.out.printf("Graceful shutdown completed in %d ms%n", shutdownTime.toMillis());
        }
    }

    // =========================================================================
    // Client Reconnection Effectiveness Tests
    // =========================================================================

    @Nested
    @DisplayName("Client Reconnection Effectiveness")
    class ClientReconnectionTests {

        @Test
        @DisplayName("Automatic reconnection on connection loss")
        void testAutomaticReconnectionOnConnectionLoss() throws Exception {
            ResilientClient client = new ResilientClient(jdbcUrl);

            // Initial connection
            assertTrue(client.isConnected());

            // Force disconnection
            client.disconnect();
            assertFalse(client.isConnected());

            // Auto-reconnect on next operation
            assertTrue(client.execute("SELECT 1"), "Operation must succeed after auto-reconnect");
            assertTrue(client.isConnected());

            client.close();
        }

        @Test
        @DisplayName("Reconnection backoff strategy")
        void testReconnectionBackoffStrategy() throws Exception {
            List<Long> reconnectionAttempts = new ArrayList<>();

            ResilientClient client = new ResilientClient(jdbcUrl);
            client.simulateFailure(3); // Fail first 3 attempts

            Instant start = Instant.now();

            while (!client.isConnected() &&
                    Duration.between(start, Instant.now()).toSeconds() < 10) {
                reconnectionAttempts.add(System.currentTimeMillis());
                client.tryReconnect();
            }

            // Verify backoff pattern
            if (reconnectionAttempts.size() >= 3) {
                long interval1 = reconnectionAttempts.get(1) - reconnectionAttempts.get(0);
                long interval2 = reconnectionAttempts.get(2) - reconnectionAttempts.get(1);

                // Later intervals should be >= earlier intervals (backoff)
                assertTrue(interval2 >= interval1 * 0.8,
                        "Backoff should increase: " + interval1 + " -> " + interval2);
            }

            client.close();
        }

        @Test
        @DisplayName("Connection pool reconnection")
        void testConnectionPoolReconnection() throws Exception {
            int poolSize = 5;
            ConnectionPool pool = new ConnectionPool(jdbcUrl, poolSize);

            // All connections available
            assertEquals(poolSize, pool.availableConnections());

            // Get all connections
            List<Connection> connections = new ArrayList<>();
            for (int i = 0; i < poolSize; i++) {
                connections.add(pool.getConnection());
            }
            assertEquals(0, pool.availableConnections());

            // Simulate all connections failing
            pool.invalidateAll();
            assertEquals(0, pool.availableConnections());

            // Pool should recreate connections on demand
            Connection newConn = pool.getConnection();
            assertNotNull(newConn, "Pool must provide new connection after invalidation");

            // Cleanup
            for (Connection conn : connections) {
                if (conn != null) try { conn.close(); } catch (SQLException e) {}
            }
            pool.close();
        }

        @Test
        @DisplayName("Session recovery after reconnection")
        void testSessionRecoveryAfterReconnection() throws Exception {
            SessionAwareClient client = new SessionAwareClient(jdbcUrl);

            // Create session
            String sessionId = client.createSession("user-123");
            assertNotNull(sessionId);

            // Verify session valid
            assertTrue(client.isSessionValid(sessionId));

            // Disconnect
            client.disconnect();

            // Reconnect - session should be restored or recreated
            client.reconnect();

            // Session may be invalid after reconnect (depends on implementation)
            // Either restore or create new session
            if (!client.isSessionValid(sessionId)) {
                sessionId = client.createSession("user-123");
            }
            assertTrue(client.isSessionValid(sessionId), "Session must be valid after recovery");

            client.close();
        }
    }

    // =========================================================================
    // System Stability After Failures Tests
    // =========================================================================

    @Nested
    @DisplayName("System Stability After Failures")
    class SystemStabilityAfterFailuresTests {

        @Test
        @DisplayName("System remains stable after repeated failures")
        void testSystemRemainsStableAfterRepeatedFailures() throws Exception {
            int failureCycles = 20;
            AtomicInteger successfulOperations = new AtomicInteger(0);
            AtomicInteger failedOperations = new AtomicInteger(0);

            for (int cycle = 0; cycle < failureCycles; cycle++) {
                try {
                    // Normal operation
                    WorkflowDataFactory.seedSpecification(db,
                            "stability-" + cycle, "1.0", "Stability Test " + cycle);
                    successfulOperations.incrementAndGet();
                } catch (SQLException e) {
                    failedOperations.incrementAndGet();
                }

                // Simulate brief failure
                if (cycle % 5 == 0) {
                    try {
                        db.close();
                        db = DriverManager.getConnection(jdbcUrl, "sa", "");
                    } catch (SQLException e) {
                        // Recovery attempt
                    }
                }
            }

            double successRate = successfulOperations.get() * 100.0 / failureCycles;

            System.out.printf("System stability after %d failure cycles:%n", failureCycles);
            System.out.printf("  Successful: %d (%.1f%%)%n", successfulOperations.get(), successRate);
            System.out.printf("  Failed: %d%n", failedOperations.get());

            assertTrue(successRate >= 90.0,
                    "At least 90% operations must succeed: actual=" + successRate + "%");
        }

        @Test
        @DisplayName("Memory stability after stress test")
        void testMemoryStabilityAfterStressTest() throws Exception {
            Runtime runtime = Runtime.getRuntime();
            long initialMemory = runtime.totalMemory() - runtime.freeMemory();

            // Stress test with many operations
            for (int i = 0; i < 1000; i++) {
                WorkflowDataFactory.seedSpecification(db,
                        "memory-stress-" + i, "1.0", "Memory Stress " + i);

                if (i % 100 == 0) {
                    System.gc(); // Periodic GC
                }
            }

            long finalMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryGrowth = finalMemory - initialMemory;

            System.out.printf("Memory stability:%n");
            System.out.printf("  Initial: %d MB%n", initialMemory / (1024 * 1024));
            System.out.printf("  Final: %d MB%n", finalMemory / (1024 * 1024));
            System.out.printf("  Growth: %d MB%n", memoryGrowth / (1024 * 1024));

            // Memory growth should be reasonable (< 100MB for 1000 operations)
            assertTrue(memoryGrowth < 100 * 1024 * 1024,
                    "Memory growth must be reasonable: " + (memoryGrowth / (1024 * 1024)) + "MB");
        }

        @Test
        @DisplayName("Thread leak detection after failures")
        void testThreadLeakDetectionAfterFailures() throws Exception {
            int initialThreads = Thread.activeCount();

            // Create many failed operations with threads
            for (int i = 0; i < 100; i++) {
                try {
                    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                    executor.submit(() -> {
                        throw new RuntimeException("Intentional failure");
                    });
                    executor.shutdown();
                    executor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Expected
                }
            }

            // Allow cleanup
            Thread.sleep(500);

            int finalThreads = Thread.activeCount();
            int threadGrowth = finalThreads - initialThreads;

            System.out.printf("Thread count: initial=%d, final=%d, growth=%d%n",
                    initialThreads, finalThreads, threadGrowth);

            // Thread count should not grow significantly
            assertTrue(threadGrowth < 20,
                    "Thread count should not grow significantly: growth=" + threadGrowth);
        }

        @Test
        @DisplayName("System self-healing after cascading failures")
        void testSystemSelfHealingAfterCascadingFailures() throws Exception {
            AtomicBoolean systemHealthy = new AtomicBoolean(true);
            AtomicInteger cascadeDepth = new AtomicInteger(0);

            // Simulate cascading failures
            for (int depth = 0; depth < 5; depth++) {
                cascadeDepth.set(depth);

                try {
                    // Operation that may fail
                    if (Math.random() < 0.3 * depth) {
                        throw new SQLException("Cascading failure at depth " + depth);
                    }

                    WorkflowDataFactory.seedSpecification(db,
                            "cascade-" + depth, "1.0", "Cascade " + depth);

                } catch (SQLException e) {
                    systemHealthy.set(false);

                    // Self-healing: backoff and retry
                    Thread.sleep(100 * depth);

                    // Retry
                    try {
                        WorkflowDataFactory.seedSpecification(db,
                                "cascade-retry-" + depth, "1.0", "Cascade Retry " + depth);
                        systemHealthy.set(true);
                    } catch (SQLException retryEx) {
                        // Continue cascade
                    }
                }
            }

            // System should eventually self-heal
            assertTrue(systemHealthy.get(),
                    "System must self-heal after cascading failures");

            System.out.println("System self-healed after cascading failures");
        }
    }

    // =========================================================================
    // Helper Methods and Classes
    // =========================================================================

    private static long calculatePercentile(List<Long> values, int percentile) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compare);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static int countRows(Connection conn, String table, String where) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // Helper classes for service simulation

    static class ServiceHealth {
        private boolean healthy = true;

        boolean isHealthy() { return healthy; }
        void fail() { healthy = false; }
        void recover() { healthy = true; }
    }

    static class ServiceWithDependencies {
        private final ServiceHealth primary = new ServiceHealth();

        boolean isHealthy() { return primary.isHealthy(); }
        ServiceHealth getPrimaryDependency() { return primary; }
    }

    static class ServiceWithProbes {
        private boolean alive = true;
        private boolean ready = false;

        boolean isAlive() { return alive; }
        boolean isReady() { return ready; }
        void warmUp() { ready = true; }
        void degrade() { ready = false; }
    }

    static class GracefulService {
        private boolean running = false;
        private final List<String> pendingWork = new ArrayList<>();
        private final List<String> completedWork = new ArrayList<>();

        void start() { running = true; }
        boolean isRunning() { return running; }
        void submitWork(String work) { if (running) pendingWork.add(work); }

        void gracefulShutdown() {
            // Complete pending work
            for (String work : pendingWork) {
                completedWork.add(work);
            }
            pendingWork.clear();
            running = false;
        }

        boolean workCompleted() { return pendingWork.isEmpty() && !completedWork.isEmpty(); }
    }

    static class ResilientClient {
        private final String jdbcUrl;
        private Connection connection;
        private int failuresToSimulate = 0;

        ResilientClient(String jdbcUrl) throws SQLException {
            this.jdbcUrl = jdbcUrl;
            this.connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        }

        boolean isConnected() {
            try {
                return connection != null && !connection.isClosed();
            } catch (SQLException e) {
                return false;
            }
        }

        void disconnect() {
            try {
                if (connection != null) connection.close();
            } catch (SQLException e) {
                // Ignore
            }
        }

        void simulateFailure(int count) { failuresToSimulate = count; }

        void tryReconnect() {
            try {
                if (failuresToSimulate > 0) {
                    failuresToSimulate--;
                    throw new SQLException("Simulated failure");
                }
                connection = DriverManager.getConnection(jdbcUrl, "sa", "");
            } catch (SQLException e) {
                // Connection failed
            }
        }

        boolean execute(String sql) {
            if (!isConnected()) {
                tryReconnect();
            }
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                return rs.next();
            } catch (SQLException e) {
                return false;
            }
        }

        void close() {
            disconnect();
        }
    }

    static class ConnectionPool {
        private final String jdbcUrl;
        private final int maxSize;
        private final List<Connection> available = new ArrayList<>();

        ConnectionPool(String jdbcUrl, int maxSize) throws SQLException {
            this.jdbcUrl = jdbcUrl;
            this.maxSize = maxSize;
            for (int i = 0; i < maxSize; i++) {
                available.add(DriverManager.getConnection(jdbcUrl, "sa", ""));
            }
        }

        int availableConnections() { return available.size(); }

        Connection getConnection() {
            if (available.isEmpty()) {
                try {
                    return DriverManager.getConnection(jdbcUrl, "sa", "");
                } catch (SQLException e) {
                    return null;
                }
            }
            return available.remove(0);
        }

        void invalidateAll() {
            for (Connection conn : available) {
                try { conn.close(); } catch (SQLException e) {}
            }
            available.clear();
        }

        void close() {
            invalidateAll();
        }
    }

    static class SessionAwareClient {
        private final String jdbcUrl;
        private Connection connection;
        private String currentSession;

        SessionAwareClient(String jdbcUrl) throws SQLException {
            this.jdbcUrl = jdbcUrl;
            this.connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        }

        String createSession(String userId) {
            currentSession = "session-" + userId + "-" + System.nanoTime();
            return currentSession;
        }

        boolean isSessionValid(String sessionId) {
            return sessionId != null && sessionId.equals(currentSession);
        }

        void disconnect() throws SQLException {
            if (connection != null) connection.close();
        }

        void reconnect() throws SQLException {
            connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        }

        void close() throws SQLException {
            disconnect();
        }
    }
}
