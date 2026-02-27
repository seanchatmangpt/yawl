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

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Java 25 VirtualThreadPool features in YAWL engine.
 *
 * <p>This test class validates the auto-scaling virtual thread pool implementation,
 * ensuring proper resource management, graceful shutdown, and task submission patterns.</p>
 *
 * <h2>Test Scope</h2>
 * <ul>
 *   <li>Virtual thread creation and management</li>
 *   <li>Graceful shutdown and resource cleanup</li>
 *   <li>Various task submission patterns</li>
 *   <li>Cost metrics tracking</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
@Tag("java25")
@Execution(ExecutionMode.CONCURRENT)
class VirtualThreadPoolTest {

    private VirtualThreadPool pool;
    private static final String TEST_POOL_NAME = "test-virtual-pool";
    private static final int MAX_CARRIER_THREADS = 10;
    private static final int SAMPLING_INTERVAL = 2; // seconds

    /**
     * Setup test environment before each test.
     */
    @BeforeEach
    void setUp() {
        // Create a new pool for each test
        pool = new VirtualThreadPool(TEST_POOL_NAME, MAX_CARRIER_THREADS, SAMPLING_INTERVAL);
        pool.start();
    }

    /**
     * Test virtual thread creation and basic functionality.
     */
    @Test
    void testVirtualThreadCreation() throws Exception {
        // Submit a simple task to create a virtual thread
        Future<String> future = pool.submit(() -> {
            Thread currentThread = Thread.currentThread();
            assertTrue(currentThread.isVirtual(), "Current thread should be virtual");
            return currentThread.getName();
        });

        String threadName = future.get(10, TimeUnit.SECONDS);
        assertNotNull(threadName, "Thread name should not be null");
        assertTrue(threadName.contains("ForkJoinPool"), "Virtual thread name should contain pool identifier");
    }

    /**
     * Test graceful shutdown of virtual thread pool.
     */
    @Test
    void testGracefulShutdown() throws Exception {
        // Submit some tasks before shutdown
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Future<String> future = pool.submit(() -> "completed");
            futures.add(future);
        }

        // Verify tasks complete before shutdown
        for (Future<String> future : futures) {
            assertTrue(future.isDone(), "All submitted tasks should complete");
            assertEquals("completed", future.get());
        }

        // Test graceful shutdown
        pool.shutdown();

        // Metrics after shutdown
        VirtualThreadPool.CostMetrics metrics = pool.getCostMetrics();
        assertEquals(0, metrics.availableCarrierThreads(), "All carrier threads should be freed after shutdown");
        assertEquals(5, metrics.tasksCompleted(), "All submitted tasks should be completed");
    }

    /**
     * Test task submission patterns with Callable.
     */
    @Test
    void testTaskSubmissionWithCallable() throws Exception {
        // Test Callable submission
        Callable<String> task = () -> "callable-result-" + Thread.currentThread().getName();
        Future<String> future = pool.submit(task);

        String result = future.get(10, TimeUnit.SECONDS);
        assertTrue(result.contains("callable-result"), "Result should contain expected prefix");
    }

    /**
     * Test task submission patterns with Runnable.
     */
    @Test
    void testTaskSubmissionWithRunnable() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        Runnable task = () -> counter.incrementAndGet();

        Future<?> future = pool.submit(task);
        future.get(10, TimeUnit.SECONDS);

        assertEquals(1, counter.get(), "Runnable should have executed and incremented counter");
    }

    /**
     * Test executeInParallel method with multiple tasks.
     */
    @Test
    void testExecuteInParallel() throws Exception {
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            tasks.add(() -> "task-" + taskId + "-" + Thread.currentThread().getName());
        }

        List<String> results = pool.executeInParallel(tasks);

        assertEquals(10, results.size(), "Should return results for all tasks");
        for (int i = 0; i < 10; i++) {
            assertTrue(results.get(i).contains("task-" + i), "Result should contain task identifier");
        }
    }

    /**
     * Test submitAndWaitAll method.
     */
    @Test
    void testSubmitAndWaitAll() throws Exception {
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            tasks.add(() -> "result-" + taskId);
        }

        List<Future<String>> futures = pool.submitAndWaitAll(tasks);

        assertEquals(5, futures.size(), "Should return futures for all tasks");
        for (int i = 0; i < 5; i++) {
            assertEquals("result-" + i, futures.get(i).get());
        }
    }

    /**
     * Test cost metrics tracking.
     */
    @Test
    void testCostMetricsTracking() throws Exception {
        // Submit tasks to generate metrics
        int taskCount = 20;
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            futures.add(pool.submit(() -> {
                // Simulate some work
                Thread.sleep(10);
                return taskId;
            }));
        }

        // Wait for all tasks to complete
        for (Future<Integer> future : futures) {
            future.get();
        }

        // Check metrics
        VirtualThreadPool.CostMetrics metrics = pool.getCostMetrics();
        assertEquals(taskCount, metrics.tasksCompleted(), "All tasks should be completed");
        assertEquals(taskCount, metrics.tasksSubmitted(), "All tasks should be submitted");
        assertTrue(metrics.carrierUtilizationPercent() >= 0, "Utilization should be non-negative");
        assertTrue(metrics.costFactor() >= 0 && metrics.costFactor() <= 1, "Cost factor should be between 0 and 1");
        assertTrue(metrics.costSavingsPercent() >= 0, "Cost savings should be non-negative");
    }

    /**
     * Test concurrent task execution with high load.
     */
    @Test
    void testHighConcurrentTaskExecution() throws Exception {
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Submit many concurrent tasks
        for (int i = 0; i < threadCount; i++) {
            final int taskId = i;
            pool.submit(() -> {
                try {
                    // Simulate work
                    Thread.sleep(1);
                    assertTrue(Thread.currentThread().isVirtual(), "All threads should be virtual");
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all tasks to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All tasks should complete within timeout");
        assertEquals(threadCount, successCount.get(), "All tasks should have executed successfully");
    }

    /**
     * test error handling in task execution.
     */
    @Test
    void testErrorHandlingInTasks() throws Exception {
        // Submit a task that throws an exception
        Callable<String> failingTask = () -> {
            throw new RuntimeException("Test exception");
        };

        Future<String> future = pool.submit(failingTask);

        // Verify exception is properly wrapped in ExecutionException
        assertThrows(ExecutionException.class, () -> future.get());
        assertTrue(future.isDone(), "Future should be done even with exception");
        assertFalse(future.isCancelled(), "Future should not be cancelled");
    }

    /**
     * Test autoscaling behavior with varying workloads.
     */
    @Test
    void testAutoscalingBehavior() throws Exception {
        // Submit tasks at different rates to test autoscaling
        int batchCount = 3;
        int tasksPerBatch = 10;
        long batchDelay = 3000; // 3 seconds between batches

        for (int batch = 0; batch < batchCount; batch++) {
            // Submit batch of tasks
            for (int i = 0; i < tasksPerBatch; i++) {
                pool.submit(() -> {
                    Thread.sleep(100); // Simulate work
                    return "work-complete";
                });
            }

            // Allow autoscaling to adjust
            Thread.sleep(batchDelay);

            // Check metrics during different load phases
            VirtualThreadPool.CostMetrics metrics = pool.getCostMetrics();
            assertTrue(metrics.tasksCompleted() > 0, "Should have completed tasks");
            assertTrue(metrics.carrierUtilizationPercent() <= 100, "Utilization should not exceed 100%");
        }
    }

    /**
     * Test resource cleanup after shutdown.
     */
    @Test
    void testResourceCleanupAfterShutdown() throws Exception {
        // Submit some work
        for (int i = 0; i < 10; i++) {
            pool.submit(() -> {
                Thread.sleep(10);
                return "work";
            });
        }

        // Shutdown pool
        pool.shutdown();

        // Verify no more tasks can be submitted
        assertThrows(IllegalStateException.class, () ->
            pool.submit(() -> "should-not-be-allowed")
        );

        // Verify metrics reflect final state
        VirtualThreadPool.CostMetrics metrics = pool.getCostMetrics();
        assertEquals(0, metrics.availableCarrierThreads(), "All threads should be freed");
    }

    /**
     * test task timeout handling.
     */
    @Test
    void testTaskTimeoutHandling() throws Exception {
        // Submit a long-running task
        Callable<String> longTask = () -> {
            Thread.sleep(5000); // 5 seconds
            return "delayed-result";
        };

        Future<String> future = pool.submit(longTask);

        // Test timeout
        assertThrows(TimeoutException.class, () ->
            future.get(1, TimeUnit.SECONDS)
        );

        // Task should still complete eventually
        String result = future.get(10, TimeUnit.SECONDS);
        assertEquals("delayed-result", result);
    }

    /**
     * Test multiple virtual thread pools running concurrently.
     */
    @Test
    void testConcurrentPoolExecution() throws Exception {
        // Create another pool
        VirtualThreadPool secondPool = new VirtualThreadPool("second-pool", 5, 2);
        secondPool.start();

        try {
            // Submit tasks to both pools
            AtomicInteger counter1 = new AtomicInteger(0);
            AtomicInteger counter2 = new AtomicInteger(0);

            for (int i = 0; i < 10; i++) {
                pool.submit(() -> counter1.incrementAndGet());
                secondPool.submit(() -> counter2.incrementAndGet());
            }

            // Wait for completion
            Thread.sleep(2000);

            assertEquals(10, counter1.get(), "First pool should execute 10 tasks");
            assertEquals(10, counter2.get(), "Second pool should execute 10 tasks");
        } finally {
            secondPool.shutdown();
        }
    }

    /**
     * Test task cancellation.
     */
    @Test
    void testTaskCancellation() throws Exception {
        // Submit a cancellable task
        Future<Integer> future = pool.submit(() -> {
            Thread.sleep(5000);
            return 42;
        });

        // Cancel the task after a short delay
        Thread.sleep(100);
        assertTrue(future.cancel(false), "Task should be cancellable");

        // Verify cancellation status
        assertTrue(future.isCancelled(), "Future should be cancelled");
        assertThrows(CancellationException.class, future::get);
    }

    /**
     * Test memory usage with many short-lived tasks.
     */
    @Test
    void testMemoryUsageWithManyTasks() throws Exception {
        // Submit many short-lived tasks
        int taskCount = 1000;
        CountDownLatch latch = new CountDownLatch(taskCount);

        for (int i = 0; i < taskCount; i++) {
            pool.submit(() -> {
                try {
                    Thread.sleep(1);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all tasks to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All tasks should complete");
        assertEquals(taskCount, pool.getCostMetrics().tasksCompleted(), "All tasks should be completed");
    }
}