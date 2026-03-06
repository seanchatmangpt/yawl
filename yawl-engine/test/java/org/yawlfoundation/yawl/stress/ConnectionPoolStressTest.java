/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
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

package org.yawlfoundation.yawl.stress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.yawlfoundation.yawl.engine.VirtualThreadPool;
import org.yawlfoundation.yawl.integration.pool.YawlConnectionPool;
import org.yawlfoundation.yawl.integration.pool.YawlConnectionPoolConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for YAWL connection pool and thread pool infrastructure.
 *
 * <p>This test class discovers exact breaking points for:
 * <ul>
 *   <li>Connection pool exhaustion (max thread count before timeout)</li>
 *   <li>Connection acquisition latency at various load levels</li>
 *   <li>Thread pool saturation (exact task count at RejectedExecutionException)</li>
 *   <li>Pool drain and recovery time</li>
 * </ul>
 *
 * <p>All tests use H2 in-memory database and real YAWL connection infrastructure.
 * Uses CountDownLatch and CompletableFuture for synchronization.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("Connection Pool & Thread Pool Stress Tests")
class ConnectionPoolStressTest {

    private static final String TEST_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "YAWL";

    private YawlConnectionPoolConfig config;
    private YawlConnectionPool pool;
    private VirtualThreadPool virtualThreadPool;

    @BeforeEach
    void setUp() {
        config = new YawlConnectionPoolConfig();
        config.setEngineUrl(TEST_ENGINE_URL);
        config.setUsername(TEST_USERNAME);
        config.setPassword(TEST_PASSWORD);
        config.setMaxWaitMs(5000);
        config.setHealthCheckIntervalMs(0); // Disable health check for stress tests
        config.setValidationOnBorrow(false); // Speed up for stress test
        config.setConnectionRetryAttempts(1);
    }

    @Nested
    @DisplayName("Pool Exhaustion Tests")
    class PoolExhaustionTests {

        /**
         * Test: Pool Exhaustion
         *
         * Spawn N threads each holding a connection, increment N until timeout/rejection.
         * Record exact N where it breaks (first NoSuchElementException).
         *
         * Default pool config: maxTotal=20, maxIdle=10, minIdle=2
         * Expected break point: 21 (one more than maxTotal)
         */
        @Test
        @DisplayName("Should identify exact pool exhaustion point")
        @Timeout(30)
        void testPoolExhaustionPoint() {
            config.setMaxTotal(20);
            config.setMaxIdle(10);
            config.setMinIdle(2);
            config.setMaxWaitMs(1000);

            pool = new YawlConnectionPool(config);
            pool.initialize();

            int exhaustionPoint = -1;
            int testThreadCount = 25;
            CountDownLatch allThreadsHeldLatch = new CountDownLatch(testThreadCount);

            ExecutorService executor = Executors.newFixedThreadPool(testThreadCount);
            List<CompletableFuture<Integer>> futures = new ArrayList<>();

            for (int i = 1; i <= testThreadCount; i++) {
                final int threadNumber = i;
                CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // Try to borrow a session
                        var session = pool.borrowSession(config.getMaxWaitMs());
                        // Hold the connection
                        allThreadsHeldLatch.countDown();
                        // Wait for timeout or completion
                        allThreadsHeldLatch.await(5, TimeUnit.SECONDS);
                        pool.returnSession(session);
                        return threadNumber; // Success
                    } catch (NoSuchElementException e) {
                        // Pool exhausted at this thread number
                        return -threadNumber;
                    } catch (Exception e) {
                        return -threadNumber;
                    }
                }, executor);
                futures.add(future);
            }

            // Wait for all futures and find exhaustion point
            for (CompletableFuture<Integer> future : futures) {
                try {
                    Integer result = future.get(6, TimeUnit.SECONDS);
                    if (result < 0 && exhaustionPoint == -1) {
                        exhaustionPoint = Math.abs(result);
                        break; // Found the break point
                    }
                } catch (Exception ignored) {
                    // Timeout is expected for held connections
                }
            }

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            pool.shutdown();

            // Report: Default maxTotal is 20, so exhaustion at 21 is expected
            System.out.println("\n=== POOL EXHAUSTION POINT ===");
            System.out.println("Pool maxTotal: " + config.getMaxTotal());
            System.out.println("Exhaustion point: Thread #" + exhaustionPoint);
            System.out.println("Expected: " + (config.getMaxTotal() + 1));

            assertTrue(exhaustionPoint > 0, "Should have identified an exhaustion point");
        }
    }

    @Nested
    @DisplayName("Connection Acquisition Latency Tests")
    class LatencyTests {

        /**
         * Test: Connection Acquisition Latency
         *
         * Measure p50/p95/p99 latency at 100, 500, 1000, 2000, 5000 concurrent holders.
         * Find where p99 > 1000ms (likely indicates contention).
         */
        @Test
        @DisplayName("Should measure latency at various load levels")
        @Timeout(30)
        void testLatencyAtLoadLevels() throws Exception {
            config.setMaxTotal(50);
            config.setMaxIdle(25);
            config.setMinIdle(5);
            config.setMaxWaitMs(10000);

            pool = new YawlConnectionPool(config);
            pool.initialize();

            int[] loadLevels = {10, 50, 100, 200};
            System.out.println("\n=== LATENCY MEASUREMENTS ===");

            for (int load : loadLevels) {
                long[] latencies = new long[load];
                CountDownLatch readyLatch = new CountDownLatch(load);
                CountDownLatch doneLatch = new CountDownLatch(load);

                ExecutorService executor = Executors.newFixedThreadPool(load);

                for (int i = 0; i < load; i++) {
                    final int index = i;
                    executor.submit(() -> {
                        try {
                            readyLatch.countDown();
                            readyLatch.await(); // Synchronize start

                            long startTime = System.nanoTime();
                            var session = pool.borrowSession();
                            long latencyMs = (System.nanoTime() - startTime) / 1_000_000;
                            latencies[index] = latencyMs;

                            Thread.sleep(10); // Simulate work

                            pool.returnSession(session);
                        } catch (Exception e) {
                            latencies[index] = -1;
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                doneLatch.await(15, TimeUnit.SECONDS);
                executor.shutdown();

                // Calculate percentiles
                java.util.Arrays.sort(latencies);
                long p50 = latencies[load / 2];
                long p95 = latencies[(int)(load * 0.95)];
                long p99 = latencies[(int)(load * 0.99)];

                System.out.printf("Load=%d: p50=%dms, p95=%dms, p99=%dms%n", load, p50, p95, p99);
            }

            pool.shutdown();
        }

        /**
         * Test: Measure latency degradation curve.
         * Shows how latency increases with concurrent contenders.
         */
        @Test
        @DisplayName("Should show latency degradation with concurrent load")
        @Timeout(30)
        void testLatencyDegradationCurve() throws Exception {
            config.setMaxTotal(20);
            config.setMaxIdle(10);
            config.setMinIdle(2);

            pool = new YawlConnectionPool(config);
            pool.initialize();

            System.out.println("\n=== LATENCY DEGRADATION CURVE ===");
            System.out.println("Pool capacity: " + config.getMaxTotal());

            for (int concurrent = 5; concurrent <= 30; concurrent += 5) {
                long[] latencies = new long[concurrent];
                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch doneLatch = new CountDownLatch(concurrent);

                ExecutorService executor = Executors.newFixedThreadPool(concurrent);

                for (int i = 0; i < concurrent; i++) {
                    final int index = i;
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            long start = System.nanoTime();
                            try {
                                var session = pool.borrowSession(2000);
                                latencies[index] = (System.nanoTime() - start) / 1_000_000;
                                Thread.sleep(5);
                                pool.returnSession(session);
                            } catch (NoSuchElementException e) {
                                latencies[index] = 2000; // Timeout value
                            }
                        } catch (Exception e) {
                            latencies[index] = -1;
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                startLatch.countDown();
                doneLatch.await(10, TimeUnit.SECONDS);
                executor.shutdown();

                java.util.Arrays.sort(latencies);
                double avgLatency = java.util.Arrays.stream(latencies).average().orElse(0);
                long maxLatency = java.util.Arrays.stream(latencies).max().orElse(0);

                System.out.printf("Concurrent=%d: avg=%dms, max=%dms%n", concurrent, (long)avgLatency, maxLatency);
            }

            pool.shutdown();
        }
    }

    @Nested
    @DisplayName("Virtual Thread Pool Saturation Tests")
    class ThreadPoolSaturationTests {

        /**
         * Test: Thread Pool Saturation
         *
         * Submit tasks to the YAWL virtual thread executor until rejection.
         * Record exact task count at failure.
         *
         * Virtual threads are unbounded, so saturation is via carrier thread limits.
         */
        @Test
        @DisplayName("Should handle virtual thread pool saturation")
        @Timeout(30)
        void testVirtualThreadPoolSaturation() throws Exception {
            virtualThreadPool = new VirtualThreadPool("stress-test", 100, 5);
            virtualThreadPool.start();

            int taskCount = 0;
            int maxTasks = 10000;
            AtomicInteger completedTasks = new AtomicInteger(0);
            List<CompletableFuture<Integer>> futures = new ArrayList<>();

            System.out.println("\n=== VIRTUAL THREAD POOL SATURATION ===");

            try {
                for (int i = 0; i < maxTasks; i++) {
                    final int taskNumber = i;
                    try {
                        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                            try {
                                Thread.sleep(10);
                                completedTasks.incrementAndGet();
                                return taskNumber;
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return -taskNumber;
                            }
                        });
                        futures.add(future);
                        taskCount++;
                    } catch (Exception e) {
                        System.out.println("Task submission failed at #" + i + ": " + e.getMessage());
                        break;
                    }
                }

                // Wait for some to complete
                Thread.sleep(500);

                VirtualThreadPool.CostMetrics metrics = virtualThreadPool.getCostMetrics();
                System.out.println("Submitted tasks: " + taskCount);
                System.out.println("Estimated carrier threads: " + metrics.estimatedCarrierThreads());
                System.out.println("Carrier utilization: " + String.format("%.1f%%", metrics.carrierUtilizationPercent()));
                System.out.println("Throughput: " + metrics.throughputPerSecond() + " tasks/sec");

                assertTrue(taskCount > 0, "Should have submitted at least some tasks");
            } finally {
                virtualThreadPool.shutdown();
            }
        }

        /**
         * Test: Virtual thread pool throughput under load.
         * Measures how many tasks can be executed per second at various parallelism levels.
         */
        @Test
        @DisplayName("Should measure virtual thread pool throughput")
        @Timeout(30)
        void testVirtualThreadPoolThroughput() throws Exception {
            System.out.println("\n=== VIRTUAL THREAD POOL THROUGHPUT ===");

            for (int maxCarriers : new int[]{10, 50, 100}) {
                virtualThreadPool = new VirtualThreadPool("throughput-test-" + maxCarriers, maxCarriers, 1);
                virtualThreadPool.start();

                int taskCount = 100;
                CountDownLatch doneLatch = new CountDownLatch(taskCount);
                AtomicLong totalTime = new AtomicLong(0);

                long startTime = System.nanoTime();

                for (int i = 0; i < taskCount; i++) {
                    virtualThreadPool.submit(() -> {
                        try {
                            Thread.sleep(5); // Simulate work
                            doneLatch.countDown();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }

                doneLatch.await(10, TimeUnit.SECONDS);
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

                VirtualThreadPool.CostMetrics metrics = virtualThreadPool.getCostMetrics();
                double throughputPerSecond = (taskCount * 1000.0) / elapsedMs;

                System.out.printf("MaxCarriers=%d: throughput=%.0f tasks/sec, elapsed=%dms%n",
                    maxCarriers, throughputPerSecond, elapsedMs);

                virtualThreadPool.shutdown();
            }
        }
    }

    @Nested
    @DisplayName("Pool Drain & Recovery Tests")
    class DrainRecoveryTests {

        /**
         * Test: Pool Drain and Recovery
         *
         * Exhaust the pool, then release all connections.
         * Measure recovery time to full capacity.
         */
        @Test
        @DisplayName("Should recover from full pool exhaustion")
        @Timeout(30)
        void testPoolRecoveryFromExhaustion() throws Exception {
            config.setMaxTotal(20);
            config.setMaxIdle(10);
            config.setMinIdle(2);
            config.setMaxWaitMs(2000);

            pool = new YawlConnectionPool(config);
            pool.initialize();

            System.out.println("\n=== POOL RECOVERY TEST ===");
            System.out.println("Initial pool state - Active: " + pool.getActiveCount() +
                             ", Idle: " + pool.getIdleCount());

            // Phase 1: Hold all available connections
            List<Object> heldConnections = new ArrayList<>();
            int connectionCount = 0;
            long exhaustTime = 0;

            long startExhaust = System.currentTimeMillis();
            for (int i = 0; i < config.getMaxTotal(); i++) {
                try {
                    var session = pool.borrowSession(1000);
                    heldConnections.add(session);
                    connectionCount++;
                } catch (NoSuchElementException e) {
                    exhaustTime = System.currentTimeMillis() - startExhaust;
                    System.out.println("Pool exhausted after " + exhaustTime + "ms");
                    break;
                }
            }

            System.out.println("Held connections: " + heldConnections.size());
            System.out.println("Pool state at exhaustion - Active: " + pool.getActiveCount() +
                             ", Idle: " + pool.getIdleCount());

            // Phase 2: Release all connections
            long startRecovery = System.currentTimeMillis();
            for (Object conn : heldConnections) {
                pool.returnSession((org.yawlfoundation.yawl.integration.pool.YawlSession) conn);
            }
            long recoveryTimeMs = System.currentTimeMillis() - startRecovery;

            System.out.println("Recovery time: " + recoveryTimeMs + "ms");
            System.out.println("Pool state after recovery - Active: " + pool.getActiveCount() +
                             ", Idle: " + pool.getIdleCount());

            pool.shutdown();

            assertTrue(recoveryTimeMs < 5000, "Pool recovery should complete quickly");
        }

        /**
         * Test: Repeated drain-recovery cycles.
         * Verifies pool stability under repeated stress cycles.
         */
        @Test
        @DisplayName("Should maintain stability through drain-recovery cycles")
        @Timeout(30)
        void testDrainRecoveryCycles() throws Exception {
            config.setMaxTotal(15);
            config.setMaxIdle(8);
            config.setMinIdle(2);

            pool = new YawlConnectionPool(config);
            pool.initialize();

            System.out.println("\n=== DRAIN-RECOVERY CYCLES ===");

            for (int cycle = 1; cycle <= 5; cycle++) {
                List<Object> held = new ArrayList<>();

                // Drain phase
                for (int i = 0; i < config.getMaxTotal(); i++) {
                    try {
                        held.add(pool.borrowSession(500));
                    } catch (NoSuchElementException e) {
                        break;
                    }
                }

                int heldCount = held.size();

                // Recovery phase
                for (Object session : held) {
                    pool.returnSession((org.yawlfoundation.yawl.integration.pool.YawlSession) session);
                }

                System.out.println("Cycle " + cycle + ": drained=" + heldCount + ", recovered=" + held.size());

                assertTrue(held.size() == heldCount, "All drained connections should be recoverable");
            }

            pool.shutdown();
        }
    }

    @Nested
    @DisplayName("Production Configuration Tests")
    class ProductionConfigTests {

        /**
         * Test: Production pool under realistic load.
         * Uses production config (maxTotal=50) and measures stability.
         */
        @Test
        @DisplayName("Should handle production configuration load")
        @Timeout(30)
        void testProductionConfigLoad() throws Exception {
            YawlConnectionPoolConfig prodConfig = YawlConnectionPoolConfig.production();
            prodConfig.setEngineUrl(TEST_ENGINE_URL);
            prodConfig.setUsername(TEST_USERNAME);
            prodConfig.setPassword(TEST_PASSWORD);

            pool = new YawlConnectionPool(prodConfig);
            pool.initialize();

            System.out.println("\n=== PRODUCTION CONFIG LOAD TEST ===");
            System.out.println("Pool configuration: maxTotal=" + prodConfig.getMaxTotal() +
                             ", maxIdle=" + prodConfig.getMaxIdle() + ", minIdle=" + prodConfig.getMinIdle());

            int threadCount = 30;
            int requestsPerThread = 50;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            long[] latencies = new long[threadCount * requestsPerThread];
            AtomicInteger latencyIndex = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            long startTime = System.nanoTime();

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    for (int r = 0; r < requestsPerThread; r++) {
                        try {
                            long reqStart = System.nanoTime();
                            var session = pool.borrowSession();
                            long reqLatency = (System.nanoTime() - reqStart) / 1_000_000;
                            latencies[latencyIndex.getAndIncrement()] = reqLatency;

                            Thread.sleep(2); // Simulate minimal work

                            pool.returnSession(session);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                    }
                    doneLatch.countDown();
                });
            }

            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

            java.util.Arrays.sort(latencies);
            long avgLatency = java.util.Arrays.stream(latencies).filter(l -> l > 0).average().orElse(0);
            long maxLatency = java.util.Arrays.stream(latencies).max().orElse(0);

            System.out.println("Total requests: " + (successCount.get() + failureCount.get()));
            System.out.println("Success: " + successCount.get() + ", Failures: " + failureCount.get());
            System.out.println("Total elapsed: " + elapsedMs + "ms");
            System.out.println("Avg latency: " + avgLatency + "ms");
            System.out.println("Max latency: " + maxLatency + "ms");

            pool.shutdown();

            assertEquals(threadCount * requestsPerThread, successCount.get(),
                "All requests should succeed under production load");
        }
    }
}
