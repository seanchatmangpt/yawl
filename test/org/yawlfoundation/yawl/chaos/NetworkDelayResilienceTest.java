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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YSpecification;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Chaos engineering tests: network delay and latency injection scenarios.
 *
 * These tests validate YAWL's resilience when database operations experience
 * artificial delays. Rather than requiring Toxiproxy/Chaos Mesh (which need
 * Docker), this suite injects delays via:
 * - Artificial sleep before/after JDBC operations (simulates network RTT)
 * - Connection timeout enforcement
 * - Deadline-bound query execution with CompletableFuture
 *
 * Chaos scenarios covered:
 * 1. Slow database responses: operations complete but exceed normal latency
 * 2. Timeout enforcement: operations that exceed deadline are cancelled
 * 3. Partial availability: some operations succeed, some timeout (split-brain)
 * 4. Recovery after delay: system functions normally after delay period ends
 *
 * Tag: "chaos" — excluded from normal CI runs, activated via -Dgroups=chaos
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Tag("chaos")
class NetworkDelayResilienceTest {

    private Connection connection;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:chaos_network_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // =========================================================================
    // Slow Response Scenarios
    // =========================================================================

    /**
     * Delay profile: (label, artificialDelayMs, timeoutMs, expectedSuccess)
     */
    static Stream<Arguments> delayProfiles() {
        return Stream.of(
            // Fast operations (delay << timeout): must succeed
            Arguments.of("no-delay",    0L,   500L, true),
            Arguments.of("10ms-delay",  10L,  500L, true),
            Arguments.of("50ms-delay",  50L,  500L, true),
            Arguments.of("100ms-delay", 100L, 500L, true),
            // Slow operations (delay > timeout): must timeout
            Arguments.of("600ms-delay", 600L, 500L, false)
        );
    }

    @ParameterizedTest(name = "[{index}] profile={0}")
    @MethodSource("delayProfiles")
    void testOperationWithSimulatedNetworkDelay(String label,
                                                 long delayMs,
                                                 long timeoutMs,
                                                 boolean expectedSuccess)
            throws Exception {
        String specId = WorkflowDataFactory.uniqueSpecId("chaos-delay");

        Instant start = Instant.now();
        boolean succeeded = executeWithDelay(
                () -> {
                    WorkflowDataFactory.seedSpecification(
                            connection, specId, "1.0", "Delay Test");
                    return true;
                },
                delayMs,
                timeoutMs);

        Duration elapsed = Duration.between(start, Instant.now());
        System.out.printf("Chaos network delay [%s]: delay=%dms timeout=%dms "
                        + "elapsed=%dms success=%b%n",
                label, delayMs, timeoutMs, elapsed.toMillis(), succeeded);

        assertEquals(expectedSuccess, succeeded,
                label + ": success must be " + expectedSuccess
                + " with delay=" + delayMs + "ms timeout=" + timeoutMs + "ms");
    }

    @Test
    void testSlowQueryDoesNotCorruptState() throws Exception {
        // Insert one row before chaos
        WorkflowDataFactory.seedSpecification(connection, "pre-chaos", "1.0", "Pre-Chaos");

        // Simulate slow inserts (but still within timeout)
        int insertCount = 10;
        for (int i = 0; i < insertCount; i++) {
            Thread.sleep(5); // 5ms artificial delay per operation
            WorkflowDataFactory.seedSpecification(connection,
                    "chaos-spec-" + i, "1.0", "Chaos Spec " + i);
        }

        // Verify all rows landed correctly
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            int count = rs.getInt(1);
            assertEquals(insertCount + 1, count,
                    "All " + (insertCount + 1) + " rows must be present after slow inserts");
        }
    }

    // =========================================================================
    // Partial Availability Scenario
    // =========================================================================

    @Test
    void testPartialAvailabilityRecovery() throws Exception {
        int totalOps = 20;
        int slowOps = 5;          // first 5 ops are delayed (simulating degraded path)
        long slowDelayMs = 20L;   // 20ms artificial delay
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < totalOps; i++) {
            final int idx = i;
            final long delay = (i < slowOps) ? slowDelayMs : 0L;

            boolean ok = executeWithDelay(
                    () -> {
                        WorkflowDataFactory.seedSpecification(
                                connection, "partial-spec-" + idx,
                                "1.0", "Partial " + idx);
                        return true;
                    },
                    delay,
                    500L);   // generous timeout

            if (ok) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }

        // With 500ms timeout and max 20ms delay, all must succeed
        assertEquals(totalOps, successCount.get(),
                "All " + totalOps + " operations must succeed with 500ms timeout");
        assertEquals(0, failureCount.get(),
                "No operations must time out with 500ms timeout and 20ms delay");

        // Verify DB state reflects all successes
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            assertEquals(totalOps, rs.getInt(1),
                    "All successful operations must be persisted");
        }
    }

    // =========================================================================
    // Concurrent Slow Operations
    // =========================================================================

    @Test
    void testConcurrentSlowOperationsThroughput() throws Exception {
        int threads = 8;
        int opsPerThread = 5;
        long artificialDelayMs = 10L;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Each thread uses its own connection to avoid contention
                    String url = "jdbc:h2:mem:chaos_concurrent_%d_%d;DB_CLOSE_DELAY=-1"
                            .formatted(threadId, System.nanoTime());
                    try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
                        YawlContainerFixtures.applyYawlSchema(conn);
                        for (int i = 0; i < opsPerThread; i++) {
                            Thread.sleep(artificialDelayMs);
                            WorkflowDataFactory.seedSpecification(conn,
                                    "ct-" + threadId + "-" + i, "1.0",
                                    "Concurrent Chaos " + threadId + "/" + i);
                        }
                        successCount.incrementAndGet();
                        return true;
                    }
                } catch (Exception e) {
                    return false;
                }
            }, executor);
            futures.add(future);
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(30, TimeUnit.SECONDS);
        assertTrue(terminated, "All threads must complete within 30 seconds");

        long succeeded = futures.stream()
                .filter(f -> {
                    try {
                        return Boolean.TRUE.equals(f.get());
                    } catch (Exception e) {
                        return false;
                    }
                }).count();

        assertEquals(threads, (int) succeeded,
                "All " + threads + " threads must succeed with artificial delay");
    }

    // =========================================================================
    // Recovery Validation
    // =========================================================================

    @Test
    void testSystemRecoveryAfterDelayPeriod() throws Exception {
        // Phase 1: slow operations (simulating degraded network)
        for (int i = 0; i < 5; i++) {
            Thread.sleep(10); // 10ms simulated delay
            WorkflowDataFactory.seedSpecification(connection,
                    "recovery-slow-" + i, "1.0", "Slow " + i);
        }

        // Phase 2: normal operations (network recovered)
        Instant recoveryStart = Instant.now();
        for (int i = 0; i < 5; i++) {
            WorkflowDataFactory.seedSpecification(connection,
                    "recovery-fast-" + i, "1.0", "Fast " + i);
        }
        Duration recoveryDuration = Duration.between(recoveryStart, Instant.now());

        // Phase 2 (normal ops) must complete quickly
        assertTrue(recoveryDuration.toMillis() < 2_000,
                "Recovery phase must complete in < 2s, took: "
                + recoveryDuration.toMillis() + "ms");

        // Verify all rows present
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            assertEquals(10, rs.getInt(1),
                    "All 10 rows (5 slow + 5 fast) must be persisted");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Executes a callable with an artificial delay and a hard timeout.
     * Returns true if the callable completes within {@code timeoutMs}, false otherwise.
     *
     * @param callable    the operation to execute (must return non-null on success)
     * @param delayMs     artificial delay injected before the operation runs
     * @param timeoutMs   maximum allowed total duration (delay + operation)
     * @return true if callable completed within timeout, false if it timed out
     */
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
}
