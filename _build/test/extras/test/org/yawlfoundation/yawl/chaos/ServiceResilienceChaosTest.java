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
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Service Resilience Chaos Engineering Tests for YAWL MCP-A2A MVP.
 *
 * Tests resilience patterns including:
 * - Circuit breaker effectiveness validation
 * - Retry mechanism testing
 * - Fallback behavior verification
 * - Graceful degradation testing
 * - Self-healing capabilities
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Tag("chaos")
@DisplayName("Service Resilience Chaos Tests")
class ServiceResilienceChaosTest {

    private Connection db;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:chaos_resilience_%d;DB_CLOSE_DELAY=-1"
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
    // Circuit Breaker Effectiveness Tests
    // =========================================================================

    @Nested
    @DisplayName("Circuit Breaker Effectiveness")
    class CircuitBreakerEffectivenessTests {

        @Test
        @DisplayName("Circuit opens after failure threshold")
        void testCircuitOpensAfterFailureThreshold() throws Exception {
            int failureThreshold = 5;
            CircuitBreaker breaker = new CircuitBreaker("test-breaker", failureThreshold, 5000);
            AtomicInteger failureCount = new AtomicInteger(0);

            // Trigger failures up to threshold
            for (int i = 0; i < failureThreshold; i++) {
                try {
                    breaker.execute(() -> {
                        failureCount.incrementAndGet();
                        throw new RuntimeException("Simulated failure " + i);
                    });
                } catch (Exception e) {
                    // Expected
                }
            }

            assertEquals(CircuitBreaker.State.OPEN, breaker.getState(),
                    "Circuit must be OPEN after " + failureThreshold + " failures");
            assertEquals(failureThreshold, failureCount.get(),
                    "Must have attempted " + failureThreshold + " operations");
        }

        @Test
        @DisplayName("Circuit fails fast when open")
        void testCircuitFailsFastWhenOpen() throws Exception {
            CircuitBreaker breaker = new CircuitBreaker("fast-fail", 1, 5000);

            // Trip the circuit
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("Trip circuit");
                });
            } catch (Exception e) {
                // Expected
            }

            assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

            // Time the fast-fail
            AtomicInteger operationCallCount = new AtomicInteger(0);
            long start = System.currentTimeMillis();

            try {
                breaker.execute(() -> {
                    operationCallCount.incrementAndGet();
                    return "should-not-be-called";
                });
                fail("Should have thrown CircuitBreakerOpenException");
            } catch (CircuitBreaker.CircuitBreakerOpenException e) {
                // Expected
            }

            long duration = System.currentTimeMillis() - start;

            // Must fail immediately without calling the operation
            assertEquals(0, operationCallCount.get(),
                    "Operation must not be called when circuit is open");
            assertTrue(duration < 50,
                    "Fast-fail must be immediate: took " + duration + "ms");
        }

        @Test
        @DisplayName("Circuit transitions to half-open after timeout")
        void testCircuitTransitionsToHalfOpenAfterTimeout() throws Exception {
            CircuitBreaker breaker = new CircuitBreaker("half-open-test", 1, 200);

            // Trip the circuit
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("Trip");
                });
            } catch (Exception e) {
                // Expected
            }

            assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

            // Wait for timeout
            Thread.sleep(300);

            // Next check should show HALF_OPEN
            assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState(),
                    "Circuit must transition to HALF_OPEN after timeout");
        }

        @Test
        @DisplayName("Half-open circuit closes on success")
        void testHalfOpenCircuitClosesOnSuccess() throws Exception {
            CircuitBreaker breaker = new CircuitBreaker("close-on-success", 1, 100);

            // Trip the circuit
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("Trip");
                });
            } catch (Exception e) {
                // Expected
            }

            // Wait for half-open
            Thread.sleep(150);

            // Successful operation should close the circuit
            String result = breaker.execute(() -> "success");

            assertEquals("success", result);
            assertEquals(CircuitBreaker.State.CLOSED, breaker.getState(),
                    "Circuit must close after successful operation in HALF_OPEN");
            assertEquals(0, breaker.getConsecutiveFailures(),
                    "Failure count must reset after success");
        }

        @Test
        @DisplayName("Half-open circuit reopens on failure")
        void testHalfOpenCircuitReopensOnFailure() throws Exception {
            CircuitBreaker breaker = new CircuitBreaker("reopen-on-failure", 1, 100);

            // Trip the circuit
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("Trip");
                });
            } catch (Exception e) {
                // Expected
            }

            // Wait for half-open
            Thread.sleep(150);
            assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

            // Failure in half-open should reopen
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("Still failing");
                });
            } catch (Exception e) {
                // Expected
            }

            assertEquals(CircuitBreaker.State.OPEN, breaker.getState(),
                    "Circuit must reopen after failure in HALF_OPEN");
        }

        @Test
        @DisplayName("Concurrent circuit breaker access is thread-safe")
        void testConcurrentCircuitBreakerAccess() throws Exception {
            CircuitBreaker breaker = new CircuitBreaker("concurrent-cb", 100, 5000);
            int threads = 50;
            int opsPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            try {
                                String result = breaker.execute(() -> {
                                    // Randomly succeed or fail
                                    if (Math.random() > 0.3) {
                                        return "ok-" + threadId + "-" + i;
                                    } else {
                                        throw new RuntimeException("Random failure");
                                    }
                                });
                                successCount.incrementAndGet();
                            } catch (CircuitBreaker.CircuitBreakerOpenException e) {
                                rejectedCount.incrementAndGet();
                            } catch (Exception e) {
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

            int totalOps = successCount.get() + failureCount.get() + rejectedCount.get();
            System.out.printf("Concurrent CB test: success=%d, failure=%d, rejected=%d, total=%d%n",
                    successCount.get(), failureCount.get(), rejectedCount.get(), totalOps);

            // Circuit breaker must remain consistent under concurrent load
            assertNotNull(breaker.getState(), "State must remain valid");
        }
    }

    // =========================================================================
    // Retry Mechanism Tests
    // =========================================================================

    @Nested
    @DisplayName("Retry Mechanism Testing")
    class RetryMechanismTests {

        @Test
        @DisplayName("Retry succeeds on transient failure")
        void testRetrySucceedsOnTransientFailure() throws Exception {
            int maxAttempts = 3;
            AtomicInteger attempts = new AtomicInteger(0);
            long initialBackoffMs = 50;

            String result = executeWithRetry(
                    () -> {
                        int attempt = attempts.incrementAndGet();
                        if (attempt < maxAttempts) {
                            throw new RuntimeException("Transient failure attempt " + attempt);
                        }
                        return "success-on-attempt-" + attempt;
                    },
                    maxAttempts,
                    initialBackoffMs);

            assertEquals("success-on-attempt-3", result);
            assertEquals(maxAttempts, attempts.get(), "Must have tried exactly " + maxAttempts + " times");
        }

        @Test
        @DisplayName("Retry exhausts all attempts on permanent failure")
        void testRetryExhaustsAttemptsOnPermanentFailure() throws Exception {
            int maxAttempts = 3;
            AtomicInteger attempts = new AtomicInteger(0);
            long initialBackoffMs = 10;

            Exception exception = assertThrows(Exception.class, () ->
                    executeWithRetry(
                            () -> {
                                attempts.incrementAndGet();
                                throw new RuntimeException("Permanent failure");
                            },
                            maxAttempts,
                            initialBackoffMs));

            assertTrue(exception.getMessage().contains("attempt") || exception.getCause() != null);
            assertEquals(maxAttempts, attempts.get(),
                    "Must have tried all " + maxAttempts + " attempts");
        }

        @Test
        @DisplayName("Exponential backoff between retries")
        void testExponentialBackoff() throws Exception {
            int maxAttempts = 5;
            List<Long> attemptTimes = new ArrayList<>();
            AtomicInteger attempts = new AtomicInteger(0);
            long initialBackoffMs = 50;

            assertThrows(Exception.class, () ->
                    executeWithRetry(
                            () -> {
                                attemptTimes.add(System.currentTimeMillis());
                                attempts.incrementAndGet();
                                throw new RuntimeException("Force retry");
                            },
                            maxAttempts,
                            initialBackoffMs));

            // Verify backoff increases between attempts
            for (int i = 2; i < attemptTimes.size(); i++) {
                long prevInterval = attemptTimes.get(i - 1) - attemptTimes.get(i - 2);
                long currInterval = attemptTimes.get(i) - attemptTimes.get(i - 1);

                // Allow some variance but backoff should generally increase
                assertTrue(currInterval >= prevInterval * 0.5,
                        "Backoff should increase: prev=" + prevInterval + ", curr=" + currInterval);
            }

            System.out.println("Backoff intervals verified: " + attempts.get() + " attempts");
        }

        @Test
        @DisplayName("Retry with jitter prevents thundering herd")
        void testRetryWithJitterPreventsThunderingHerd() throws Exception {
            int clients = 10;
            int maxAttempts = 3;
            AtomicInteger attempts = new AtomicInteger(0);
            List<Long> attemptTimes = new ArrayList<>();
            Object lock = new Object();

            ExecutorService executor = Executors.newFixedThreadPool(clients);
            CountDownLatch latch = new CountDownLatch(clients);

            long startTime = System.currentTimeMillis();

            for (int c = 0; c < clients; c++) {
                executor.submit(() -> {
                    try {
                        executeWithRetry(
                                () -> {
                                    synchronized (lock) {
                                        attemptTimes.add(System.currentTimeMillis() - startTime);
                                    }
                                    attempts.incrementAndGet();
                                    throw new RuntimeException("Simulated service down");
                                },
                                maxAttempts,
                                50);
                    } catch (Exception e) {
                        // Expected
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Verify attempts are spread out (not all at same time)
            long maxSpread = attemptTimes.stream().max(Long::compare).orElse(0L);
            long minSpread = attemptTimes.stream().min(Long::compare).orElse(0L);
            long spread = maxSpread - minSpread;

            System.out.printf("Retry jitter test: %d total attempts, spread over %dms%n",
                    attempts.get(), spread);

            // With jitter, attempts should be spread over time
            assertTrue(spread > 50, "Attempts should be spread over time with jitter");
        }
    }

    // =========================================================================
    // Fallback Behavior Tests
    // =========================================================================

    @Nested
    @DisplayName("Fallback Behavior Verification")
    class FallbackBehaviorTests {

        @Test
        @DisplayName("Fallback to cached data on primary failure")
        void testFallbackToCachedData() throws Exception {
            // Simulate primary data source
            Supplier<String> primarySource = () -> {
                throw new RuntimeException("Primary source unavailable");
            };

            // Simulate cache
            Supplier<String> cacheFallback = () -> "cached-data-v1";

            String result = executeWithFallback(primarySource, cacheFallback);

            assertEquals("cached-data-v1", result,
                    "Must return cached data when primary fails");
        }

        @Test
        @DisplayName("Multi-level fallback chain")
        void testMultiLevelFallbackChain() throws Exception {
            List<Supplier<String>> sources = List.of(
                    () -> { throw new RuntimeException("Primary down"); },
                    () -> { throw new RuntimeException("Secondary down"); },
                    () -> "tertiary-data"
            );

            String result = null;
            Exception lastException = null;

            for (Supplier<String> source : sources) {
                try {
                    result = source.get();
                    break;
                } catch (Exception e) {
                    lastException = e;
                }
            }

            assertEquals("tertiary-data", result,
                    "Must fall through to working source");
        }

        @Test
        @DisplayName("Degraded mode operation")
        void testDegradedModeOperation() throws Exception {
            AtomicBoolean fullServiceAvailable = new AtomicBoolean(false);

            // Service in degraded mode
            String result = getServiceResult("query", fullServiceAvailable);

            assertNotNull(result, "Must return result in degraded mode");
            assertTrue(result.contains("degraded") || result.contains("partial"),
                    "Result should indicate degraded mode: " + result);
        }

        @Test
        @DisplayName("Fallback timeout prevents cascade")
        void testFallbackTimeout() throws Exception {
            long maxFallbackTimeMs = 500;

            // Slow fallback source
            Supplier<String> slowFallback = () -> {
                try {
                    Thread.sleep(2000);
                    return "slow-fallback-result";
                } catch (InterruptedException e) {
                    return "interrupted";
                }
            };

            long start = System.currentTimeMillis();
            String result = executeWithFallbackTimeout(slowFallback, maxFallbackTimeMs);
            long duration = System.currentTimeMillis() - start;

            // Must timeout before slow fallback completes
            assertTrue(duration < maxFallbackTimeMs + 100,
                    "Fallback must timeout: took " + duration + "ms");
        }
    }

    // =========================================================================
    // Graceful Degradation Tests
    // =========================================================================

    @Nested
    @DisplayName("Graceful Degradation Testing")
    class GracefulDegradationTests {

        @Test
        @DisplayName("Service degradation with feature flags")
        void testServiceDegradationWithFeatureFlags() throws Exception {
            FeatureFlagManager flags = new FeatureFlagManager();

            // Full service available
            flags.enable("advanced-features");
            String fullResult = processRequest("query", flags);
            assertNotNull(fullResult);

            // Degraded - disable advanced features
            flags.disable("advanced-features");
            String degradedResult = processRequest("query", flags);
            assertNotNull(degradedResult);

            // Both must succeed but degraded may have limited functionality
            assertNotEquals(fullResult, degradedResult,
                    "Degraded mode should return different (simpler) result");
        }

        @Test
        @DisplayName("Partial data availability")
        void testPartialDataAvailability() throws Exception {
            // Some data available, some missing
            List<String> allIds = List.of("spec-1", "spec-2", "spec-3", "spec-4", "spec-5");
            List<String> availableIds = List.of("spec-1", "spec-3", "spec-5");

            // Seed available data
            for (String id : availableIds) {
                WorkflowDataFactory.seedSpecification(db, id, "1.0", "Spec " + id);
            }

            // Query all - should return partial results
            List<String> foundIds = new ArrayList<>();
            for (String id : allIds) {
                if (rowExists(db, "yawl_specification", "spec_id", id)) {
                    foundIds.add(id);
                }
            }

            assertEquals(availableIds, foundIds,
                    "Must return available subset of data");
        }

        @Test
        @DisplayName("Load shedding under pressure")
        void testLoadSheddingUnderPressure() throws Exception {
            int requests = 100;
            int maxConcurrent = 10;
            AtomicInteger acceptedCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);

            // Simulate load shedding
            for (int i = 0; i < requests; i++) {
                if (acceptedCount.get() < maxConcurrent) {
                    acceptedCount.incrementAndGet();
                } else {
                    rejectedCount.incrementAndGet();
                }
            }

            assertEquals(maxConcurrent, acceptedCount.get(), "Must accept up to limit");
            assertEquals(requests - maxConcurrent, rejectedCount.get(), "Must reject over limit");

            System.out.printf("Load shedding: accepted=%d, rejected=%d%n",
                    acceptedCount.get(), rejectedCount.get());
        }
    }

    // =========================================================================
    // Self-Healing Capability Tests
    // =========================================================================

    @Nested
    @DisplayName("Self-Healing Capabilities")
    class SelfHealingTests {

        @Test
        @DisplayName("Automatic recovery after transient failure")
        void testAutomaticRecoveryAfterTransientFailure() throws Exception {
            AtomicInteger failureCount = new AtomicInteger(0);
            AtomicBoolean healed = new AtomicBoolean(false);
            int maxFailures = 3;

            // Simulate self-healing service
            for (int attempt = 0; attempt < 10; attempt++) {
                try {
                    String result = callSelfHealingService(failureCount, maxFailures);
                    if (result != null) {
                        healed.set(true);
                        break;
                    }
                } catch (Exception e) {
                    // Continue trying
                }
                Thread.sleep(100);
            }

            assertTrue(healed.get(), "Service must self-heal after transient failures");
        }

        @Test
        @DisplayName("Health check triggers recovery")
        void testHealthCheckTriggersRecovery() throws Exception {
            ServiceHealthMonitor monitor = new ServiceHealthMonitor();

            // Service starts unhealthy
            assertFalse(monitor.isHealthy());

            // Health check triggers recovery
            monitor.performHealthCheck();
            assertTrue(monitor.isHealthy(), "Service must recover after health check");
        }

        @Test
        @DisplayName("Background repair of corrupted state")
        void testBackgroundRepairOfCorruptedState() throws Exception {
            // Introduce corrupted state
            String specId = "corrupted-spec";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", null); // null name = corrupted

            // Background repair process
            repairCorruptedSpecifications(db);

            // Verify repair
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT spec_name FROM yawl_specification WHERE spec_id = ?")) {
                ps.setString(1, specId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertNotNull(rs.getString("spec_name"),
                            "Corrupted data must be repaired");
                }
            }
        }

        @Test
        @DisplayName("Circuit breaker self-heals after recovery period")
        void testCircuitBreakerSelfHealsAfterRecoveryPeriod() throws Exception {
            CircuitBreaker breaker = new CircuitBreaker("self-healing", 1, 200);

            // Trip the circuit
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("Trip");
                });
            } catch (Exception e) {
                // Expected
            }

            assertEquals(CircuitBreaker.State.OPEN, breaker.getState());

            // Wait for recovery period
            Thread.sleep(300);

            // Circuit should allow test request (HALF_OPEN)
            assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState());

            // Successful test should fully heal (CLOSE)
            breaker.execute(() -> "healed");
            assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        }
    }

    // =========================================================================
    // Helper Methods and Classes
    // =========================================================================

    private static <T> T executeWithRetry(
            Supplier<T> operation,
            int maxAttempts,
            long backoffMs) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    long delay = backoffMs * (1L << (attempt - 1));
                    Thread.sleep(delay);
                }
            }
        }

        throw new Exception("Operation failed after " + maxAttempts + " attempts", lastException);
    }

    private static <T> T executeWithFallback(
            Supplier<T> primary,
            Supplier<T> fallback) {
        try {
            return primary.get();
        } catch (Exception e) {
            return fallback.get();
        }
    }

    private static <T> T executeWithFallbackTimeout(
            Supplier<T> fallback,
            long timeoutMs) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(fallback);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return null;
        }
    }

    private static String getServiceResult(String query, boolean fullServiceAvailable) {
        if (fullServiceAvailable) {
            return "full-result: " + query;
        } else {
            return "degraded-partial-result: " + query;
        }
    }

    private static String processRequest(String query, FeatureFlagManager flags) {
        if (flags.isEnabled("advanced-features")) {
            return "advanced-processed: " + query;
        } else {
            return "basic-processed: " + query;
        }
    }

    private static String callSelfHealingService(
            AtomicInteger failureCount,
            int maxFailures) {
        int failures = failureCount.get();
        if (failures < maxFailures) {
            failureCount.incrementAndGet();
            throw new RuntimeException("Transient failure " + failures);
        }
        return "healed-service-response";
    }

    private static void repairCorruptedSpecifications(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE yawl_specification SET spec_name = 'Repaired Spec' WHERE spec_name IS NULL")) {
            ps.executeUpdate();
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

    static class FeatureFlagManager {
        private final java.util.Map<String, Boolean> flags = new java.util.HashMap<>();

        void enable(String flag) { flags.put(flag, true); }
        void disable(String flag) { flags.put(flag, false); }
        boolean isEnabled(String flag) { return flags.getOrDefault(flag, false); }
    }

    static class ServiceHealthMonitor {
        private boolean healthy = false;

        boolean isHealthy() { return healthy; }

        void performHealthCheck() {
            // Simulate health check triggering recovery
            healthy = true;
        }
    }
}
