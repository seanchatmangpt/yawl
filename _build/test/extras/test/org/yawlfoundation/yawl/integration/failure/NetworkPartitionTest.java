/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.failure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Network partition simulation tests for YAWL v6.
 *
 * Tests system behavior under network failure scenarios:
 * - Connection timeouts
 * - Network partitions
 * - Service unavailability
 * - Recovery after partition
 *
 * Chicago TDD: Real network conditions, real failures, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-18
 */
@Tag("integration")
class NetworkPartitionTest {

    private Connection db;
    private static final String UNREACHABLE_HOST = "http://localhost:19999";
    private static final int SHORT_TIMEOUT_MS = 100;
    private static final int MEDIUM_TIMEOUT_MS = 1000;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:network_partition_%d;DB_CLOSE_DELAY=-1"
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
    // Connection Timeout Tests
    // =========================================================================

    @Test
    void testConnectionTimeout() throws Exception {
        URL url = new URL(UNREACHABLE_HOST + "/api");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(SHORT_TIMEOUT_MS);

        long start = System.currentTimeMillis();
        try {
            conn.connect();
            fail("Connection should timeout");
        } catch (ConnectException | SocketTimeoutException e) {
            long duration = System.currentTimeMillis() - start;
            assertTrue(duration < MEDIUM_TIMEOUT_MS * 2,
                    "Timeout should occur quickly: " + duration + "ms");
        } finally {
            conn.disconnect();
        }
    }

    @Test
    void testReadTimeout() throws Exception {
        // Connection refused is immediate, but we test timeout configuration
        URL url = new URL(UNREACHABLE_HOST + "/api");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(SHORT_TIMEOUT_MS);
        conn.setReadTimeout(SHORT_TIMEOUT_MS);

        try {
            conn.connect();
            fail("Should fail to connect");
        } catch (ConnectException e) {
            // Expected - connection refused
            assertNotNull(e.getMessage());
        } finally {
            conn.disconnect();
        }
    }

    @Test
    void testMultipleConnectionRetries() throws Exception {
        int maxRetries = 3;
        int attempts = 0;
        boolean connected = false;

        for (int i = 0; i < maxRetries && !connected; i++) {
            attempts++;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(UNREACHABLE_HOST + "/retry");
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(SHORT_TIMEOUT_MS);
                conn.connect();
                connected = true;
            } catch (ConnectException e) {
                // Retry with exponential backoff
                Thread.sleep((long) (Math.pow(2, i) * 50));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        assertEquals(maxRetries, attempts, "Must attempt all retries");
        assertFalse(connected, "Must fail after all retries (no server)");
    }

    // =========================================================================
    // Service Unavailability Tests
    // =========================================================================

    @Test
    void testServiceUnavailableDetection() throws Exception {
        ServiceHealth health = checkServiceHealth(UNREACHABLE_HOST);

        assertFalse(health.isAvailable(), "Service must be unavailable");
        assertNotNull(health.getError(), "Error message must be present");
    }

    @Test
    void testServiceHealthCheckWithTimeout() throws Exception {
        long start = System.currentTimeMillis();
        ServiceHealth health = checkServiceHealthWithTimeout(
                UNREACHABLE_HOST, SHORT_TIMEOUT_MS);
        long duration = System.currentTimeMillis() - start;

        assertFalse(health.isAvailable());
        assertTrue(duration < MEDIUM_TIMEOUT_MS * 2,
                "Health check must respect timeout");
    }

    @Test
    void testCircuitBreakerPattern() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker(3, 1000);
        String serviceUrl = UNREACHABLE_HOST + "/circuit";

        // Attempt calls until circuit opens
        for (int i = 0; i < 5; i++) {
            boolean allowed = breaker.allowRequest();
            if (allowed) {
                try {
                    callService(serviceUrl);
                    breaker.recordSuccess();
                } catch (Exception e) {
                    breaker.recordFailure();
                }
            }
            System.out.println("Attempt " + (i + 1) + ": allowed=" + allowed
                    + ", state=" + breaker.getState());
        }

        // Circuit should be open after failures
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState(),
                "Circuit must be open after failures");
    }

    // =========================================================================
    // Database Partition Simulation Tests
    // =========================================================================

    @Test
    void testDatabaseAvailable() throws Exception {
        // Verify database is available
        assertTrue(isDatabaseAvailable(db), "Database must be available");
    }

    @Test
    void testDatabaseOperationsDuringNormalOperation() throws Exception {
        String specId = "partition-normal";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Normal Test");

        assertTrue(rowExists(db, "yawl_specification", "spec_id", specId),
                "Data must be persisted");
    }

    @Test
    void testTransactionIsolationDuringHighLoad() throws Exception {
        String specId = "partition-isolation";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Isolation Test");

        // Simulate concurrent operations
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                try {
                    String runnerId = "runner-isolation-" + threadNum;
                    synchronized (db) {
                        WorkflowDataFactory.seedNetRunner(db, runnerId,
                                specId, "1.0", "root", "RUNNING");
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    synchronized (errors) {
                        errors.add("Thread " + threadNum + ": " + e.getMessage());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(threads, successCount.get(),
                "All threads must succeed: " + errors);
    }

    // =========================================================================
    // Partition Recovery Tests
    // =========================================================================

    @Test
    void testRecoveryAfterTransientFailure() throws Exception {
        String specId = "partition-recovery";

        // Initial successful operation
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Recovery Test");
        assertTrue(rowExists(db, "yawl_specification", "spec_id", specId));

        // Simulate transient failure (exception thrown, but DB still works)
        try {
            // This would be where the failure occurs
            throw new TransientFailureException("Simulated network blip");
        } catch (TransientFailureException e) {
            // Recovery: retry the operation
            boolean recovered = false;
            for (int i = 0; i < 3 && !recovered; i++) {
                try {
                    // Verify database still accessible
                    if (rowExists(db, "yawl_specification", "spec_id", specId)) {
                        recovered = true;
                    }
                } catch (Exception retryEx) {
                    Thread.sleep(100);
                }
            }
            assertTrue(recovered, "Must recover after transient failure");
        }
    }

    @Test
    void testDataConsistencyAfterPartition() throws Exception {
        String specId = "partition-consistency";
        String runnerId = "runner-consistency";

        // Setup initial state
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Consistency Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        // Simulate partition by recording state before and after
        String stateBefore = getRunnerState(db, runnerId);

        // During "partition" - state changes are queued
        updateRunnerState(db, runnerId, "SUSPENDED");

        // After "partition" - verify state is consistent
        String stateAfter = getRunnerState(db, runnerId);

        assertNotEquals(stateBefore, stateAfter,
                "State must change after partition recovery");
        assertEquals("SUSPENDED", stateAfter,
                "State must be SUSPENDED after recovery");
    }

    // =========================================================================
    // Timeout and Retry Configuration Tests
    // =========================================================================

    @Test
    void testConfigurableTimeouts() {
        TimeoutConfig config = new TimeoutConfig(
                5000,  // connectTimeout
                10000, // readTimeout
                3,     // maxRetries
                1000   // retryDelayMs
        );

        assertEquals(5000, config.getConnectTimeoutMs());
        assertEquals(10000, config.getReadTimeoutMs());
        assertEquals(3, config.getMaxRetries());
        assertEquals(1000, config.getRetryDelayMs());
    }

    @Test
    void testRetryWithBackoff() throws Exception {
        int[] backoffDelays = {100, 200, 400, 800, 1600};
        long totalDelay = 0;

        for (int delay : backoffDelays) {
            long start = System.currentTimeMillis();
            Thread.sleep(delay);
            long actualDelay = System.currentTimeMillis() - start;

            // Allow 50% tolerance
            assertTrue(actualDelay >= delay * 0.5,
                    "Delay must be approximately " + delay + "ms");
            totalDelay += actualDelay;
        }

        System.out.println("Total backoff delay: " + totalDelay + "ms");
    }

    // =========================================================================
    // Concurrent Partition Simulation Tests
    // =========================================================================

    @Test
    void testConcurrentRequestsDuringPartition() throws Exception {
        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            executor.submit(() -> {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL(UNREACHABLE_HOST + "/concurrent");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(SHORT_TIMEOUT_MS);
                    conn.connect();
                } catch (SocketTimeoutException e) {
                    timeoutCount.incrementAndGet();
                } catch (ConnectException e) {
                    failureCount.incrementAndGet();
                } catch (IOException e) {
                    failureCount.incrementAndGet();
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        int totalIssues = failureCount.get() + timeoutCount.get();
        assertEquals(requestCount, totalIssues,
                "All requests must fail during partition");
    }

    @Test
    void testGracefulDegradation() throws Exception {
        GracefulDegradationManager manager = new GracefulDegradationManager();
        String serviceUrl = UNREACHABLE_HOST + "/degrade";

        // Primary service fails
        boolean primaryAvailable = manager.checkService(serviceUrl);
        assertFalse(primaryAvailable, "Primary must be unavailable");

        // Fallback to cached/local data
        String data = manager.getDataWithFallback("spec-123");

        assertNotNull(data, "Must return data via fallback");
        assertTrue(data.contains("fallback"), "Data must indicate fallback");
    }

    // =========================================================================
    // Helper Methods and Classes
    // =========================================================================

    private static boolean rowExists(Connection conn,
                                     String table,
                                     String column,
                                     String value) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE " + column + " = ?")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean isDatabaseAvailable(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private static String getRunnerState(Connection conn,
                                         String runnerId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT state FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString("state");
            }
        }
    }

    private static void updateRunnerState(Connection conn,
                                          String runnerId,
                                          String state) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE yawl_net_runner SET state = ? WHERE runner_id = ?")) {
            ps.setString(1, state);
            ps.setString(2, runnerId);
            ps.executeUpdate();
        }
    }

    private static ServiceHealth checkServiceHealth(String url) {
        try {
            return checkServiceHealthWithTimeout(url, MEDIUM_TIMEOUT_MS);
        } catch (Exception e) {
            return new ServiceHealth(false, e.getMessage());
        }
    }

    private static ServiceHealth checkServiceHealthWithTimeout(String url,
                                                               int timeoutMs) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL healthUrl = new URL(url + "/health");
            conn = (HttpURLConnection) healthUrl.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.connect();

            int code = conn.getResponseCode();
            return new ServiceHealth(code == 200, "HTTP " + code);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static void callService(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL serviceUrl = new URL(url);
            conn = (HttpURLConnection) serviceUrl.openConnection();
            conn.setConnectTimeout(SHORT_TIMEOUT_MS);
            conn.connect();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // Helper classes
    static class ServiceHealth {
        private final boolean available;
        private final String error;

        ServiceHealth(boolean available, String error) {
            this.available = available;
            this.error = error;
        }

        boolean isAvailable() { return available; }
        String getError() { return error; }
    }

    static class CircuitBreaker {
        enum State { CLOSED, OPEN, HALF_OPEN }

        private State state = State.CLOSED;
        private int failureCount = 0;
        private final int failureThreshold;
        private final long resetTimeoutMs;
        private long lastFailureTime = 0;

        CircuitBreaker(int failureThreshold, long resetTimeoutMs) {
            this.failureThreshold = failureThreshold;
            this.resetTimeoutMs = resetTimeoutMs;
        }

        synchronized boolean allowRequest() {
            if (state == State.OPEN) {
                if (System.currentTimeMillis() - lastFailureTime > resetTimeoutMs) {
                    state = State.HALF_OPEN;
                    return true;
                }
                return false;
            }
            return true;
        }

        synchronized void recordSuccess() {
            failureCount = 0;
            state = State.CLOSED;
        }

        synchronized void recordFailure() {
            failureCount++;
            lastFailureTime = System.currentTimeMillis();
            if (failureCount >= failureThreshold) {
                state = State.OPEN;
            }
        }

        synchronized State getState() { return state; }
    }

    static class TimeoutConfig {
        private final int connectTimeoutMs;
        private final int readTimeoutMs;
        private final int maxRetries;
        private final int retryDelayMs;

        TimeoutConfig(int connectTimeoutMs, int readTimeoutMs,
                      int maxRetries, int retryDelayMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.maxRetries = maxRetries;
            this.retryDelayMs = retryDelayMs;
        }

        int getConnectTimeoutMs() { return connectTimeoutMs; }
        int getReadTimeoutMs() { return readTimeoutMs; }
        int getMaxRetries() { return maxRetries; }
        int getRetryDelayMs() { return retryDelayMs; }
    }

    static class TransientFailureException extends Exception {
        TransientFailureException(String message) {
            super(message);
        }
    }

    static class GracefulDegradationManager {
        boolean checkService(String url) {
            try {
                callService(url);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        String getDataWithFallback(String specId) {
            // Simulate fallback to local cache
            return "{\"specId\":\"" + specId + "\",\"source\":\"fallback\",\"cached\":true}";
        }
    }
}
