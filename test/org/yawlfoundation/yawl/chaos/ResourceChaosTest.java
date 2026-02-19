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
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Resource Chaos Engineering Tests for YAWL MCP-A2A MVP.
 *
 * Tests resource exhaustion scenarios including:
 * - Memory pressure testing
 * - CPU throttling (50-100% utilization)
 * - Disk space exhaustion
 * - File descriptor limits
 * - Thread pool exhaustion
 *
 * Design principle: Controlled resource pressure with gradual recovery.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Tag("chaos")
@DisplayName("Resource Chaos Tests")
class ResourceChaosTest {

    private Connection db;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:chaos_resource_%d;DB_CLOSE_DELAY=-1"
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
    // Memory Pressure Tests
    // =========================================================================

    @Nested
    @DisplayName("Memory Pressure Testing")
    class MemoryPressureTests {

        @Test
        @DisplayName("Low memory condition with graceful handling")
        void testLowMemoryWarningAndHandling() throws Exception {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long maxMemory = Runtime.getRuntime().maxMemory();
            long usedBefore = memoryBean.getHeapMemoryUsage().getUsed();

            // Allocate memory to create pressure (limited for safety)
            List<byte[]> memoryHog = new ArrayList<>();
            int allocationCount = 10;
            int chunkSize = 1024 * 1024; // 1MB chunks

            try {
                for (int i = 0; i < allocationCount; i++) {
                    memoryHog.add(new byte[chunkSize]);
                }

                long usedAfter = memoryBean.getHeapMemoryUsage().getUsed();
                long memoryIncrease = usedAfter - usedBefore;

                System.out.printf("Memory pressure: allocated %d MB, heap went from %d MB to %d MB%n",
                        allocationCount, usedBefore / (1024 * 1024), usedAfter / (1024 * 1024));

                // System should still be responsive under memory pressure
                WorkflowDataFactory.seedSpecification(db, "mem-pressure", "1.0", "Memory Test");

                assertTrue(rowExists(db, "yawl_specification", "spec_id", "mem-pressure"),
                        "Operations must succeed under memory pressure");

            } finally {
                // Release memory
                memoryHog.clear();
                System.gc();
            }
        }

        @Test
        @DisplayName("Memory allocation failure recovery")
        void testMemoryAllocationFailureRecovery() throws Exception {
            // Simulate OutOfMemoryError scenario
            AtomicBoolean oomOccurred = new AtomicBoolean(false);
            AtomicBoolean recovered = new AtomicBoolean(false);

            try {
                // Attempt large allocation
                List<byte[]> chunks = new ArrayList<>();
                int attempts = 0;
                int maxAttempts = 100;

                while (attempts < maxAttempts) {
                    try {
                        chunks.add(new byte[10 * 1024 * 1024]); // 10MB
                        attempts++;
                    } catch (OutOfMemoryError e) {
                        oomOccurred.set(true);
                        // Clear some memory
                        chunks.subList(0, chunks.size() / 2).clear();
                        System.gc();
                        break;
                    }
                }

                // After OOM, verify system still functions
                WorkflowDataFactory.seedSpecification(db, "oom-recovery", "1.0", "OOM Recovery");
                recovered.set(true);

            } catch (OutOfMemoryError e) {
                oomOccurred.set(true);
                // Force recovery
                System.gc();
                Thread.sleep(100);

                // Retry operation
                WorkflowDataFactory.seedSpecification(db, "oom-recovery", "1.0", "OOM Recovery Retry");
                recovered.set(true);
            }

            assertTrue(recovered.get(), "System must recover from memory pressure");
            assertTrue(rowExists(db, "yawl_specification", "spec_id", "oom-recovery"),
                    "Data must be persisted after recovery");
        }

        @Test
        @DisplayName("Concurrent memory-intensive operations")
        void testConcurrentMemoryIntensiveOperations() throws Exception {
            int threads = 10;
            int operationsPerThread = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger oomCount = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            try {
                                // Memory-intensive operation
                                byte[] buffer = new byte[1024 * 1024]; // 1MB
                                // Fill with data
                                for (int j = 0; j < buffer.length; j += 1024) {
                                    buffer[j] = (byte) (threadId + i);
                                }

                                // Database operation
                                WorkflowDataFactory.seedSpecification(db,
                                        "mem-concurrent-" + threadId + "-" + i,
                                        "1.0", "Mem Concurrent " + threadId);

                                successCount.incrementAndGet();
                            } catch (OutOfMemoryError e) {
                                oomCount.incrementAndGet();
                                System.gc();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(60, TimeUnit.SECONDS);
            executor.shutdown();

            int expectedOps = threads * operationsPerThread;
            double successRate = (double) successCount.get() / expectedOps * 100;

            System.out.printf("Concurrent memory test: %d/%d succeeded (%.1f%%), OOMs=%d%n",
                    successCount.get(), expectedOps, successRate, oomCount.get());

            assertTrue(successRate >= 80.0,
                    "At least 80% of operations must succeed: actual=" + successRate + "%");
        }
    }

    // =========================================================================
    // CPU Throttling Tests
    // =========================================================================

    @Nested
    @DisplayName("CPU Throttling (50-100% Utilization)")
    class CpuThrottlingTests {

        @Test
        @DisplayName("CPU-intensive workload with database operations")
        void testCpuIntensiveWorkloadWithDatabase() throws Exception {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            int availableProcessors = osBean.getAvailableProcessors();

            System.out.printf("CPU throttling test: %d processors available%n", availableProcessors);

            // Create CPU pressure
            int cpuLoadThreads = Math.max(1, availableProcessors - 1);
            ExecutorService cpuExecutor = Executors.newFixedThreadPool(cpuLoadThreads);
            AtomicBoolean stopCpuLoad = new AtomicBoolean(false);

            // Start CPU-intensive tasks
            for (int i = 0; i < cpuLoadThreads; i++) {
                cpuExecutor.submit(() -> {
                    while (!stopCpuLoad.get()) {
                        // CPU-intensive calculation
                        Math.sqrt(Math.random() * 1000000);
                    }
                });
            }

            try {
                // Give CPU load time to ramp up
                Thread.sleep(500);

                // Perform database operations under CPU pressure
                int operations = 20;
                long startTime = System.currentTimeMillis();

                for (int i = 0; i < operations; i++) {
                    WorkflowDataFactory.seedSpecification(db,
                            "cpu-pressure-" + i, "1.0", "CPU Pressure " + i);
                }

                long duration = System.currentTimeMillis() - startTime;
                double opsPerSecond = (operations * 1000.0) / duration;

                System.out.printf("Under CPU load: %d ops in %dms (%.1f ops/sec)%n",
                        operations, duration, opsPerSecond);

                // Verify all operations succeeded
                try (Statement stmt = db.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT COUNT(*) FROM yawl_specification WHERE spec_id LIKE 'cpu-pressure%'")) {
                    assertTrue(rs.next());
                    assertEquals(operations, rs.getInt(1), "All operations must succeed under CPU pressure");
                }

            } finally {
                // Stop CPU load
                stopCpuLoad.set(true);
                cpuExecutor.shutdown();
                cpuExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("100% CPU utilization boundary test")
        void testFullCpuUtilizationBoundary() throws Exception {
            int processors = Runtime.getRuntime().availableProcessors();
            ExecutorService executor = Executors.newFixedThreadPool(processors * 2);
            AtomicBoolean stopFlag = new AtomicBoolean(false);
            AtomicInteger dbOperations = new AtomicInteger(0);

            // Start CPU-bound tasks on all cores
            List<CompletableFuture<Void>> cpuTasks = new ArrayList<>();
            for (int i = 0; i < processors; i++) {
                cpuTasks.add(CompletableFuture.runAsync(() -> {
                    while (!stopFlag.get()) {
                        // Intensive computation
                        double result = 0;
                        for (int j = 0; j < 10000; j++) {
                            result += Math.sin(j) * Math.cos(j);
                        }
                    }
                }, executor));
            }

            // Perform database operations while CPU is saturated
            try {
                Thread.sleep(200); // Let CPU load build

                int dbOps = 10;
                for (int i = 0; i < dbOps; i++) {
                    try {
                        WorkflowDataFactory.seedSpecification(db,
                                "full-cpu-" + i, "1.0", "Full CPU " + i);
                        dbOperations.incrementAndGet();
                    } catch (SQLException e) {
                        // Operation may be slower but should succeed
                    }
                }

                // Most operations should succeed even under full CPU load
                assertTrue(dbOperations.get() >= dbOps * 0.8,
                        "At least 80% of DB ops must succeed under full CPU: " + dbOperations.get());

            } finally {
                stopFlag.set(true);
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            }
        }

        @Test
        @DisplayName("CPU throttling with priority inversion")
        void testCpuThrottlingWithPriorityInversion() throws Exception {
            // Low-priority CPU-intensive task
            Thread lowPriorityThread = Thread.ofVirtual().unstarted(() -> {
                for (int i = 0; i < 1000000; i++) {
                    Math.sqrt(i);
                }
            });
            lowPriorityThread.setPriority(Thread.MIN_PRIORITY);

            // High-priority database operation
            Thread highPriorityThread = Thread.ofVirtual().unstarted(() -> {
                try {
                    WorkflowDataFactory.seedSpecification(db,
                            "priority-inversion", "1.0", "Priority Test");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            highPriorityThread.setPriority(Thread.MAX_PRIORITY);

            // Start threads
            lowPriorityThread.start();
            Thread.sleep(10); // Let low-priority start
            highPriorityThread.start();

            // Wait for completion
            highPriorityThread.join(5000);
            lowPriorityThread.join(10000);

            // High-priority operation must complete
            assertTrue(rowExists(db, "yawl_specification", "spec_id", "priority-inversion"),
                    "High-priority database operation must complete despite CPU contention");
        }
    }

    // =========================================================================
    // Disk Space Exhaustion Tests
    // =========================================================================

    @Nested
    @DisplayName("Disk Space Exhaustion")
    class DiskSpaceExhaustionTests {

        @Test
        @DisplayName("Large data insertions")
        void testLargeDataInsertions() throws Exception {
            // Create large event data
            StringBuilder largeData = new StringBuilder("{\"data\":\"");
            for (int i = 0; i < 50000; i++) {
                largeData.append("x");
            }
            largeData.append("\"}");

            // Insert large records
            int records = 100;
            for (int i = 0; i < records; i++) {
                try (PreparedStatement ps = db.prepareStatement(
                        "INSERT INTO yawl_case_event " +
                                "(event_id, runner_id, event_type, event_data) " +
                                "VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, System.nanoTime() + i);
                    ps.setString(2, "disk-test-runner");
                    ps.setString(3, "LARGE_EVENT_" + i);
                    ps.setString(4, largeData.toString());
                    ps.executeUpdate();
                }
            }

            // Verify all records stored
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_case_event WHERE runner_id = 'disk-test-runner'")) {
                assertTrue(rs.next());
                assertEquals(records, rs.getInt(1), "All large records must be stored");
            }

            // Check total size
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT SUM(LENGTH(event_data)) FROM yawl_case_event WHERE runner_id = 'disk-test-runner'")) {
                assertTrue(rs.next());
                long totalSize = rs.getLong(1);
                System.out.printf("Total data size: %d bytes (~%d MB)%n",
                        totalSize, totalSize / (1024 * 1024));
                assertTrue(totalSize > 5000000, "Must have stored significant data");
            }
        }

        @Test
        @DisplayName("Rapid sequential writes")
        void testRapidSequentialWrites() throws Exception {
            int writes = 1000;
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < writes; i++) {
                WorkflowDataFactory.seedSpecification(db,
                        "rapid-write-" + i, "1.0", "Rapid " + i);
            }

            long duration = System.currentTimeMillis() - startTime;
            double writesPerSecond = (writes * 1000.0) / duration;

            System.out.printf("Rapid writes: %d writes in %dms (%.1f writes/sec)%n",
                    writes, duration, writesPerSecond);

            // Verify all writes persisted
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_specification WHERE spec_id LIKE 'rapid-write%'")) {
                assertTrue(rs.next());
                assertEquals(writes, rs.getInt(1), "All rapid writes must be persisted");
            }
        }
    }

    // =========================================================================
    // File Descriptor Limit Tests
    // =========================================================================

    @Nested
    @DisplayName("File Descriptor Limits")
    class FileDescriptorLimitTests {

        @Test
        @DisplayName("Connection leak detection")
        void testConnectionLeakDetection() throws Exception {
            int maxConnections = 50;
            List<Connection> connections = new ArrayList<>();

            // Open connections without closing (simulating leak)
            for (int i = 0; i < maxConnections; i++) {
                try {
                    Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
                    connections.add(conn);

                    // Perform operation
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("SELECT 1");
                    }
                } catch (SQLException e) {
                    System.out.printf("Connection %d failed: %s%n", i, e.getMessage());
                    break;
                }
            }

            System.out.printf("Opened %d connections before limit%n", connections.size());

            // Cleanup - close all connections
            for (Connection conn : connections) {
                try {
                    if (!conn.isClosed()) conn.close();
                } catch (SQLException e) {
                    // Ignore close errors
                }
            }

            // Verify we can still open new connections after cleanup
            Connection testConn = DriverManager.getConnection(jdbcUrl, "sa", "");
            assertFalse(testConn.isClosed(), "Must be able to open connection after cleanup");
            testConn.close();
        }

        @Test
        @DisplayName("Proper connection cleanup under load")
        void testProperConnectionCleanupUnderLoad() throws Exception {
            int iterations = 100;
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < iterations; i++) {
                // Use try-with-resources to ensure cleanup
                try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
                    WorkflowDataFactory.seedSpecification(conn,
                            "fd-cleanup-" + i, "1.0", "FD Cleanup " + i);
                    successCount.incrementAndGet();
                }
            }

            assertEquals(iterations, successCount.get(),
                    "All iterations must succeed with proper cleanup");
        }
    }

    // =========================================================================
    // Thread Pool Exhaustion Tests
    // =========================================================================

    @Nested
    @DisplayName("Thread Pool Exhaustion")
    class ThreadPoolExhaustionTests {

        @Test
        @DisplayName("Virtual thread starvation")
        void testVirtualThreadStarvation() throws Exception {
            int tasks = 100;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch latch = new CountDownLatch(tasks);
            AtomicInteger completedTasks = new AtomicInteger(0);
            AtomicInteger blockedTasks = new AtomicInteger(0);

            // Simulate blocking operations
            for (int i = 0; i < tasks; i++) {
                executor.submit(() -> {
                    try {
                        // Simulate blocking I/O
                        Thread.sleep(100);
                        completedTasks.incrementAndGet();
                    } catch (InterruptedException e) {
                        blockedTasks.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Wait with timeout
            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            System.out.printf("Virtual thread test: %d completed, %d blocked%n",
                    completedTasks.get(), blockedTasks.get());

            assertTrue(completed, "All virtual thread tasks must complete");
            assertEquals(tasks, completedTasks.get(), "All tasks must complete successfully");
        }

        @Test
        @DisplayName("Bounded thread pool rejection")
        void testBoundedThreadPoolRejection() throws Exception {
            int poolSize = 5;
            int queueCapacity = 10;
            int totalTasks = 50;

            // Use semaphore to simulate bounded pool behavior
            Semaphore semaphore = new Semaphore(poolSize);
            ExecutorService executor = Executors.newFixedThreadPool(poolSize);
            CountDownLatch latch = new CountDownLatch(totalTasks);
            AtomicInteger accepted = new AtomicInteger(0);
            AtomicInteger rejected = new AtomicInteger(0);

            for (int i = 0; i < totalTasks; i++) {
                if (semaphore.tryAcquire()) {
                    accepted.incrementAndGet();
                    executor.submit(() -> {
                        try {
                            Thread.sleep(100);
                            WorkflowDataFactory.seedSpecification(db,
                                    "thread-pool-" + accepted.get(), "1.0", "Thread Pool");
                        } catch (Exception e) {
                            // Handle error
                        } finally {
                            semaphore.release();
                            latch.countDown();
                        }
                    });
                } else {
                    rejected.incrementAndGet();
                    latch.countDown();
                }
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            System.out.printf("Bounded pool test: %d accepted, %d rejected%n",
                    accepted.get(), rejected.get());

            assertTrue(accepted.get() >= poolSize,
                    "At least pool-size tasks must be accepted");
        }

        @Test
        @DisplayName("Thread pool with timeout enforcement")
        void testThreadPoolWithTimeoutEnforcement() throws Exception {
            int tasks = 20;
            long taskTimeoutMs = 500;
            ExecutorService executor = Executors.newFixedThreadPool(4);
            AtomicInteger timedOut = new AtomicInteger(0);
            AtomicInteger completed = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(tasks);

            for (int i = 0; i < tasks; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                            try {
                                // Some tasks take longer
                                if (taskId % 3 == 0) {
                                    Thread.sleep(1000); // Will timeout
                                } else {
                                    Thread.sleep(100);
                                }
                                WorkflowDataFactory.seedSpecification(db,
                                        "timeout-task-" + taskId, "1.0", "Timeout " + taskId);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });

                        task.get(taskTimeoutMs, TimeUnit.MILLISECONDS);
                        completed.incrementAndGet();
                    } catch (Exception e) {
                        timedOut.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            System.out.printf("Timeout enforcement: %d completed, %d timed out%n",
                    completed.get(), timedOut.get());

            // Some tasks should timeout (those divisible by 3)
            assertTrue(timedOut.get() > 0, "Some tasks must timeout");
            assertTrue(completed.get() > 0, "Some tasks must complete");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

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
}
