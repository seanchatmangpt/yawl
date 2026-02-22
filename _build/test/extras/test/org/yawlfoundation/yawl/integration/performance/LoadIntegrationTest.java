/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemID;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load integration tests for YAWL v6.
 *
 * Tests system performance under various load conditions:
 * - Concurrent case execution
 * - Throughput benchmarks
 * - Latency SLA verification
 * - Resource utilization
 *
 * Chicago TDD: Real load, real metrics, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-18
 */
@Tag("integration")
@Tag("performance")
class LoadIntegrationTest {

    private Connection db;

    // Performance SLAs
    private static final Duration MAX_CASE_LATENCY = Duration.ofMillis(100);
    private static final Duration MAX_QUERY_LATENCY = Duration.ofMillis(50);
    private static final int MIN_THROUGHPUT_PER_SEC = 100;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:load_test_%d;DB_CLOSE_DELAY=-1"
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
    // Concurrent Case Execution Tests
    // =========================================================================

    @Test
    void testConcurrentCaseExecution_10Threads() throws Exception {
        runConcurrentCaseTest(10, 10);
    }

    @Test
    void testConcurrentCaseExecution_20Threads() throws Exception {
        runConcurrentCaseTest(20, 10);
    }

    @Test
    void testConcurrentCaseExecution_50Threads() throws Exception {
        runConcurrentCaseTest(50, 5);
    }

    private void runConcurrentCaseTest(int threadCount, int casesPerThread) throws Exception {
        String specId = "load-concurrent-" + threadCount;
        int totalCases = threadCount * casesPerThread;

        // Setup
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", spec.getName());

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        List<String> errors = new ArrayList<>();

        Instant start = Instant.now();

        for (int t = 0; t < threadCount; t++) {
            final int threadNum = t;
            executor.submit(() -> {
                try {
                    for (int c = 0; c < casesPerThread; c++) {
                        Instant caseStart = Instant.now();
                        String runnerId = "runner-" + threadNum + "-" + c;

                        synchronized (db) {
                            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                                    "root", "RUNNING");
                            WorkflowDataFactory.seedWorkItem(db, "item-" + runnerId,
                                    runnerId, "process", "Completed");
                            updateRunnerState(db, runnerId, "COMPLETED");
                        }

                        long latency = Duration.between(caseStart, Instant.now()).toMillis();
                        totalLatency.addAndGet(latency);
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

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        Instant end = Instant.now();
        Duration totalDuration = Duration.between(start, end);

        assertTrue(completed, "All threads must complete within timeout");
        assertEquals(threadCount, successCount.get(),
                "All threads must succeed. Errors: " + errors);

        // Verify total cases persisted
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = '"
                             + specId + "' AND state = 'COMPLETED'")) {
            assertTrue(rs.next());
            assertEquals(totalCases, rs.getInt(1),
                    "All cases must be persisted and completed");
        }

        // Performance metrics
        double throughput = (totalCases * 1000.0) / totalDuration.toMillis();
        double avgLatency = totalLatency.get() / (double) totalCases;

        System.out.printf("Load Test [%d threads x %d cases]:%n", threadCount, casesPerThread);
        System.out.printf("  Total cases: %d%n", totalCases);
        System.out.printf("  Duration: %dms%n", totalDuration.toMillis());
        System.out.printf("  Throughput: %.0f cases/sec%n", throughput);
        System.out.printf("  Avg latency: %.2fms%n", avgLatency);

        assertTrue(throughput >= MIN_THROUGHPUT_PER_SEC,
                "Throughput must be >= " + MIN_THROUGHPUT_PER_SEC + " cases/sec");
    }

    // =========================================================================
    // Throughput Benchmark Tests
    // =========================================================================

    @Test
    void testThroughputBenchmark_SequentialInserts() throws Exception {
        String specId = "load-throughput-seq";
        int caseCount = 500;

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Throughput Test");

        Instant start = Instant.now();

        for (int i = 0; i < caseCount; i++) {
            String runnerId = "runner-seq-" + i;
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                    "root", "RUNNING");
        }

        Duration duration = Duration.between(start, Instant.now());
        double throughput = (caseCount * 1000.0) / duration.toMillis();

        System.out.printf("Sequential Throughput:%n");
        System.out.printf("  Cases: %d%n", caseCount);
        System.out.printf("  Duration: %dms%n", duration.toMillis());
        System.out.printf("  Throughput: %.0f cases/sec%n", throughput);

        assertTrue(throughput >= 500,
                "Sequential throughput must be >= 500 cases/sec, got " + throughput);
    }

    @Test
    void testThroughputBenchmark_BatchInserts() throws Exception {
        String specId = "load-throughput-batch";
        int caseCount = 500;
        int batchSize = 50;

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Batch Throughput Test");
        db.setAutoCommit(false);

        Instant start = Instant.now();

        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO yawl_net_runner (runner_id, spec_id, spec_version, "
                        + "net_id, state) VALUES (?, ?, ?, ?, ?)")) {

            for (int i = 0; i < caseCount; i++) {
                ps.setString(1, "runner-batch-" + i);
                ps.setString(2, specId);
                ps.setString(3, "1.0");
                ps.setString(4, "root");
                ps.setString(5, "RUNNING");
                ps.addBatch();

                if ((i + 1) % batchSize == 0) {
                    ps.executeBatch();
                    db.commit();
                }
            }
            ps.executeBatch();
            db.commit();
        }

        db.setAutoCommit(true);

        Duration duration = Duration.between(start, Instant.now());
        double throughput = (caseCount * 1000.0) / duration.toMillis();

        System.out.printf("Batch Throughput:%n");
        System.out.printf("  Cases: %d%n", caseCount);
        System.out.printf("  Batch size: %d%n", batchSize);
        System.out.printf("  Duration: %dms%n", duration.toMillis());
        System.out.printf("  Throughput: %.0f cases/sec%n", throughput);

        assertTrue(throughput >= 1000,
                "Batch throughput must be >= 1000 cases/sec, got " + throughput);
    }

    // =========================================================================
    // Latency SLA Tests
    // =========================================================================

    @Test
    void testCaseCreationLatency() throws Exception {
        String specId = "load-latency-create";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Latency Test");

        int samples = 100;
        long[] latencies = new long[samples];

        for (int i = 0; i < samples; i++) {
            Instant start = Instant.now();

            String runnerId = "runner-latency-" + i;
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                    "root", "RUNNING");

            latencies[i] = Duration.between(start, Instant.now()).toMillis();
        }

        // Calculate percentiles
        java.util.Arrays.sort(latencies);
        long p50 = latencies[samples / 2];
        long p95 = latencies[(int) (samples * 0.95)];
        long p99 = latencies[(int) (samples * 0.99)];
        long max = latencies[samples - 1];

        System.out.printf("Case Creation Latency (%d samples):%n", samples);
        System.out.printf("  P50: %dms%n", p50);
        System.out.printf("  P95: %dms%n", p95);
        System.out.printf("  P99: %dms%n", p99);
        System.out.printf("  Max: %dms%n", max);

        assertTrue(p95 < MAX_CASE_LATENCY.toMillis(),
                "P95 latency must be < " + MAX_CASE_LATENCY.toMillis() + "ms");
    }

    @Test
    void testQueryLatency() throws Exception {
        String specId = "load-latency-query";

        // Setup data
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Query Latency Test");
        for (int i = 0; i < 100; i++) {
            WorkflowDataFactory.seedNetRunner(db, "runner-query-" + i, specId, "1.0",
                    "root", i % 2 == 0 ? "RUNNING" : "COMPLETED");
        }

        int samples = 50;
        long[] latencies = new long[samples];

        for (int i = 0; i < samples; i++) {
            Instant start = Instant.now();

            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = ? AND state = ?")) {
                ps.setString(1, specId);
                ps.setString(2, "RUNNING");
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                }
            }

            latencies[i] = Duration.between(start, Instant.now()).toMillis();
        }

        java.util.Arrays.sort(latencies);
        long p95 = latencies[(int) (samples * 0.95)];

        System.out.printf("Query Latency (%d samples):%n", samples);
        System.out.printf("  P95: %dms%n", p95);

        assertTrue(p95 < MAX_QUERY_LATENCY.toMillis(),
                "P95 query latency must be < " + MAX_QUERY_LATENCY.toMillis() + "ms");
    }

    // =========================================================================
    // Work Item Performance Tests
    // =========================================================================

    @Test
    void testWorkItemCreationPerformance() throws Exception {
        String specId = "load-wi-perf";
        String runnerId = "runner-wi-perf";
        int workItemCount = 1000;

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "WI Perf Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        Instant start = Instant.now();

        for (int i = 0; i < workItemCount; i++) {
            WorkflowDataFactory.seedWorkItem(db, "item-perf-" + i, runnerId,
                    "task_" + (i % 10), "Enabled");
        }

        Duration duration = Duration.between(start, Instant.now());
        double throughput = (workItemCount * 1000.0) / duration.toMillis();

        System.out.printf("Work Item Creation Performance:%n");
        System.out.printf("  Count: %d%n", workItemCount);
        System.out.printf("  Duration: %dms%n", duration.toMillis());
        System.out.printf("  Throughput: %.0f items/sec%n", throughput);

        assertTrue(throughput >= 500,
                "Work item throughput must be >= 500/sec");
    }

    @Test
    void testWorkItemStateTransitionPerformance() throws Exception {
        String specId = "load-wi-transition";
        String runnerId = "runner-wi-transition";
        int itemCount = 500;

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Transition Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        // Create work items
        for (int i = 0; i < itemCount; i++) {
            WorkflowDataFactory.seedWorkItem(db, "item-trans-" + i, runnerId,
                    "task", "Enabled");
        }

        // Measure state transitions
        Instant start = Instant.now();

        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_work_item SET status = ? WHERE item_id = ?")) {
            for (int i = 0; i < itemCount; i++) {
                ps.setString(1, "Executing");
                ps.setString(2, "item-trans-" + i);
                ps.addBatch();

                if ((i + 1) % 100 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }

        Duration duration = Duration.between(start, Instant.now());
        double throughput = (itemCount * 1000.0) / duration.toMillis();

        System.out.printf("State Transition Performance:%n");
        System.out.printf("  Transitions: %d%n", itemCount);
        System.out.printf("  Duration: %dms%n", duration.toMillis());
        System.out.printf("  Throughput: %.0f transitions/sec%n", throughput);

        assertTrue(throughput >= 1000,
                "State transition throughput must be >= 1000/sec");
    }

    // =========================================================================
    // YWorkItem Object Performance Tests
    // =========================================================================

    @Test
    void testYWorkItemCreationPerformance() throws Exception {
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec("load-yworkitem");
        int itemCount = 1000;

        Instant start = Instant.now();

        for (int i = 0; i < itemCount; i++) {
            YAtomicTask task = (YAtomicTask) spec.getRootNet().getNetElement("process");
            YIdentifier caseId = new YIdentifier(null);
            YWorkItemID wid = new YWorkItemID(caseId, "process");
            YWorkItem item = new YWorkItem(null, spec.getSpecificationID(),
                    task, wid, true, false);
            item.setStatus(YWorkItemStatus.statusExecuting);
            item.setStatus(YWorkItemStatus.statusComplete);
        }

        Duration duration = Duration.between(start, Instant.now());
        double throughput = (itemCount * 1000.0) / duration.toMillis();

        System.out.printf("YWorkItem Object Performance:%n");
        System.out.printf("  Objects: %d%n", itemCount);
        System.out.printf("  Duration: %dms%n", duration.toMillis());
        System.out.printf("  Throughput: %.0f objects/sec%n", throughput);

        assertTrue(throughput >= 5000,
                "YWorkItem creation throughput must be >= 5000/sec");
    }

    // =========================================================================
    // Mixed Workload Tests
    // =========================================================================

    @Test
    void testMixedWorkload_ReadsAndWrites() throws Exception {
        String specId = "load-mixed";
        int operations = 100;
        int readRatio = 70; // 70% reads, 30% writes

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Mixed Workload");
        for (int i = 0; i < 50; i++) {
            WorkflowDataFactory.seedNetRunner(db, "runner-mixed-" + i, specId, "1.0",
                    "root", "RUNNING");
        }

        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger writeCount = new AtomicInteger(0);

        Instant start = Instant.now();

        for (int i = 0; i < operations; i++) {
            if (i % 100 < readRatio) {
                // Read operation
                try (Statement stmt = db.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = '"
                                     + specId + "'")) {
                    assertTrue(rs.next());
                }
                readCount.incrementAndGet();
            } else {
                // Write operation
                String runnerId = "runner-mixed-new-" + i;
                WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                        "root", "RUNNING");
                writeCount.incrementAndGet();
            }
        }

        Duration duration = Duration.between(start, Instant.now());
        double throughput = (operations * 1000.0) / duration.toMillis();

        System.out.printf("Mixed Workload:%n");
        System.out.printf("  Total operations: %d%n", operations);
        System.out.printf("  Reads: %d (%.0f%%)%n", readCount.get(),
                readCount.get() * 100.0 / operations);
        System.out.printf("  Writes: %d (%.0f%%)%n", writeCount.get(),
                writeCount.get() * 100.0 / operations);
        System.out.printf("  Duration: %dms%n", duration.toMillis());
        System.out.printf("  Throughput: %.0f ops/sec%n", throughput);

        assertTrue(throughput >= 200,
                "Mixed workload throughput must be >= 200 ops/sec");
    }

    // =========================================================================
    // Stress Tests
    // =========================================================================

    @Test
    void testHighStress_1000Cases() throws Exception {
        String specId = "load-stress-1000";
        int caseCount = 1000;

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Stress 1000");

        Instant start = Instant.now();

        db.setAutoCommit(false);
        for (int i = 0; i < caseCount; i++) {
            WorkflowDataFactory.seedNetRunner(db, "runner-stress-" + i, specId, "1.0",
                    "root", "RUNNING");

            if ((i + 1) % 100 == 0) {
                db.commit();
            }
        }
        db.commit();
        db.setAutoCommit(true);

        Duration duration = Duration.between(start, Instant.now());
        double throughput = (caseCount * 1000.0) / duration.toMillis();

        // Verify all persisted
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = '" + specId + "'")) {
            assertTrue(rs.next());
            assertEquals(caseCount, rs.getInt(1), "All cases must be persisted");
        }

        System.out.printf("High Stress Test (1000 cases):%n");
        System.out.printf("  Duration: %dms%n", duration.toMillis());
        System.out.printf("  Throughput: %.0f cases/sec%n", throughput);

        assertTrue(duration.toMillis() < 30_000,
                "1000 cases must complete in < 30s");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

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
}
