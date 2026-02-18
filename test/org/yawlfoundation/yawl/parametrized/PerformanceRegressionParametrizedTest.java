/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.parametrized;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parametrized performance regression tests for YAWL v6.0.0.
 *
 * Establishes performance baselines across multiple dimensions:
 * - Workflow specification construction throughput (in-memory, no DB)
 * - H2 in-memory persistence throughput (inserts per second)
 * - Concurrent specification creation (multi-threaded)
 *
 * Each test is parametrized over a load profile (operationCount, threadCount,
 * maxDurationMs) so the same assertion logic validates different load points
 * in a single test run. This replaces ad-hoc performance tests with a single
 * regression matrix that can be extended by adding a new Arguments row.
 *
 * Regression criteria: a test fails if elapsed time exceeds maxDurationMs.
 * Baselines are deliberately conservative (10x slower than observed local
 * performance) so CI machines can pass reliably without tuning.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Tag("slow")
class PerformanceRegressionParametrizedTest {

    /**
     * Load profile for a single parametrized row.
     *
     * @param label         human-readable identifier shown in test name
     * @param operationCount number of operations to perform
     * @param threadCount   number of concurrent threads (1 = sequential)
     * @param maxDurationMs maximum allowed wall-clock duration in milliseconds
     */
    record LoadProfile(
            String label,
            int operationCount,
            int threadCount,
            long maxDurationMs) {
        @Override
        public String toString() {
            return label;
        }
    }

    // =========================================================================
    // Test Parameters
    // =========================================================================

    /**
     * Parametrized load matrix for in-memory specification construction.
     */
    static Stream<Arguments> specConstructionProfiles() {
        return Stream.of(
            Arguments.of(new LoadProfile("small-10",         10,   1,  1_000)),
            Arguments.of(new LoadProfile("medium-100",      100,   1,  5_000)),
            Arguments.of(new LoadProfile("large-1000",    1_000,   1, 30_000)),
            Arguments.of(new LoadProfile("concurrent-100", 100,   4, 10_000))
        );
    }

    /**
     * Parametrized load matrix for H2 persistence throughput.
     */
    static Stream<Arguments> persistenceProfiles() {
        return Stream.of(
            Arguments.of(new LoadProfile("h2-small-10",      10,  1,  2_000)),
            Arguments.of(new LoadProfile("h2-medium-100",   100,  1, 10_000)),
            Arguments.of(new LoadProfile("h2-large-500",    500,  1, 60_000))
        );
    }

    /**
     * Parametrized matrix for multi-threaded H2 access.
     */
    static Stream<Arguments> concurrentDbProfiles() {
        return Stream.of(
            Arguments.of(new LoadProfile("concurrent-2t-50",   50,  2, 15_000)),
            Arguments.of(new LoadProfile("concurrent-4t-100", 100,  4, 20_000)),
            Arguments.of(new LoadProfile("concurrent-8t-200", 200,  8, 30_000))
        );
    }

    // =========================================================================
    // In-Memory Specification Construction
    // =========================================================================

    @ParameterizedTest(name = "[{index}] profile={0}")
    @MethodSource("specConstructionProfiles")
    void testSpecificationConstructionThroughput(LoadProfile profile)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(profile.threadCount());
        List<Callable<YSpecification>> tasks = new ArrayList<>(profile.operationCount());

        for (int i = 0; i < profile.operationCount(); i++) {
            final int idx = i;
            tasks.add(() -> WorkflowDataFactory.buildMinimalSpec(
                    WorkflowDataFactory.uniqueSpecId("perf-spec-" + idx)));
        }

        long startMs = System.currentTimeMillis();
        List<Future<YSpecification>> futures = executor.invokeAll(
                tasks, profile.maxDurationMs(), TimeUnit.MILLISECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        long durationMs = System.currentTimeMillis() - startMs;

        // Verify all completed without cancellation
        long succeeded = futures.stream().filter(f -> {
            try {
                return f.get() != null;
            } catch (Exception e) {
                return false;
            }
        }).count();

        assertEquals(profile.operationCount(), (int) succeeded,
                profile.label() + ": all operations must complete");
        assertTrue(durationMs <= profile.maxDurationMs(),
                profile.label() + ": elapsed " + durationMs
                + "ms must be <= " + profile.maxDurationMs() + "ms");

        double opsPerSec = (profile.operationCount() * 1000.0) / Math.max(durationMs, 1);
        System.out.printf("Spec construction [%s]: %.0f ops/sec (%d ops in %dms)%n",
                profile.label(), opsPerSec, profile.operationCount(), durationMs);
    }

    // =========================================================================
    // H2 Persistence Throughput
    // =========================================================================

    @ParameterizedTest(name = "[{index}] profile={0}")
    @MethodSource("persistenceProfiles")
    void testH2PersistenceThroughput(LoadProfile profile) throws Exception {
        String jdbcUrl = "jdbc:h2:mem:perf_%s_%d;DB_CLOSE_DELAY=-1"
                .formatted(profile.label().replace("-", "_"), System.nanoTime());

        long startMs = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            YawlContainerFixtures.applyYawlSchema(conn);

            String sql = "INSERT INTO yawl_specification "
                       + "(spec_id, spec_version, spec_name) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < profile.operationCount(); i++) {
                    ps.setString(1, "perf-spec-" + i);
                    ps.setString(2, "1.0");
                    ps.setString(3, "Perf Spec " + i);
                    ps.addBatch();
                    if ((i + 1) % 100 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch(); // flush remainder
            }
        }
        long durationMs = System.currentTimeMillis() - startMs;

        assertTrue(durationMs <= profile.maxDurationMs(),
                profile.label() + ": H2 insert elapsed " + durationMs
                + "ms must be <= " + profile.maxDurationMs() + "ms");

        double opsPerSec = (profile.operationCount() * 1000.0) / Math.max(durationMs, 1);
        System.out.printf("H2 persistence [%s]: %.0f rows/sec (%d rows in %dms)%n",
                profile.label(), opsPerSec, profile.operationCount(), durationMs);
    }

    // =========================================================================
    // Concurrent Database Access
    // =========================================================================

    @ParameterizedTest(name = "[{index}] profile={0}")
    @MethodSource("concurrentDbProfiles")
    void testConcurrentDatabaseAccess(LoadProfile profile) throws Exception {
        // Each thread gets its own in-memory H2 database to avoid lock contention
        // (this models independent workflow case databases per-tenant)
        ExecutorService executor = Executors.newFixedThreadPool(profile.threadCount());
        List<Callable<Integer>> tasks = new ArrayList<>(profile.operationCount());

        int opsPerThread = profile.operationCount() / profile.threadCount();
        for (int t = 0; t < profile.threadCount(); t++) {
            final int threadId = t;
            final int count = opsPerThread;
            tasks.add(() -> {
                String url = "jdbc:h2:mem:concurrent_%s_t%d_%d;DB_CLOSE_DELAY=-1"
                        .formatted(profile.label().replace("-", "_"),
                                   threadId, System.nanoTime());
                try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
                    YawlContainerFixtures.applyYawlSchema(conn);
                    String sql = "INSERT INTO yawl_specification "
                               + "(spec_id, spec_version, spec_name) VALUES (?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (int i = 0; i < count; i++) {
                            ps.setString(1, "t" + threadId + "-spec-" + i);
                            ps.setString(2, "1.0");
                            ps.setString(3, "Thread " + threadId + " Spec " + i);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }

                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(
                                 "SELECT COUNT(*) FROM yawl_specification")) {
                        rs.next();
                        return rs.getInt(1);
                    }
                }
            });
        }

        long startMs = System.currentTimeMillis();
        List<Future<Integer>> futures = executor.invokeAll(
                tasks, profile.maxDurationMs(), TimeUnit.MILLISECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        long durationMs = System.currentTimeMillis() - startMs;

        // Each thread must have inserted exactly opsPerThread rows
        for (int i = 0; i < futures.size(); i++) {
            assertEquals(opsPerThread, (int) futures.get(i).get(),
                    profile.label() + ": thread " + i
                    + " must insert " + opsPerThread + " rows");
        }

        assertTrue(durationMs <= profile.maxDurationMs(),
                profile.label() + ": concurrent elapsed " + durationMs
                + "ms must be <= " + profile.maxDurationMs() + "ms");

        double opsPerSec = (profile.operationCount() * 1000.0) / Math.max(durationMs, 1);
        System.out.printf("Concurrent DB [%s]: %.0f rows/sec (%d ops, %d threads, %dms)%n",
                profile.label(), opsPerSec, profile.operationCount(),
                profile.threadCount(), durationMs);
    }
}
