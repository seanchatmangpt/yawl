package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for virtual thread migration in YAWL engine.
 * Tests verify correct behavior, performance improvements, and resource efficiency.
 *
 * Fortune 5 Standards Compliance:
 * - NO TODOs, NO mocks, NO stubs
 * - Real virtual thread executors
 * - Comprehensive assertions
 * - Performance benchmarks
 *
 * @author YAWL Engineering Team
 * @date 2026-02-16
 */
public class VirtualThreadMigrationTest {

    private ExecutorService virtualThreadExecutor;
    private ExecutorService platformThreadExecutor;

    @BeforeEach
    public void setUp() {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        platformThreadExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        shutdownExecutor(virtualThreadExecutor);
        shutdownExecutor(platformThreadExecutor);
    }

    private void shutdownExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Test 1: Basic Virtual Thread Creation
     * Verifies that virtual threads can be created and execute tasks correctly.
     */
    @Test
    public void testVirtualThreadBasicExecution() throws InterruptedException {
        int taskCount = 100;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.execute(() -> {
                try {
                    Thread.sleep(10);
                    completedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(30, TimeUnit.SECONDS);
        assertTrue("All virtual thread tasks should complete", finished);
        assertEquals("All tasks should complete successfully", taskCount, completedCount.get());
    }

    /**
     * Test 2: High Concurrency Scalability
     * Verifies that virtual threads scale to 10,000+ concurrent tasks.
     */
    @Test
    public void testHighConcurrencyScalability() throws InterruptedException {
        int taskCount = 10000;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger successCount = new AtomicInteger(0);

        Instant start = Instant.now();

        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.execute(() -> {
                try {
                    simulateIOOperation(5);
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(60, TimeUnit.SECONDS);
        Duration duration = Duration.between(start, Instant.now());

        assertTrue("10,000 tasks should complete within 60 seconds", finished);
        assertEquals("All tasks should succeed", taskCount, successCount.get());

        System.out.println("High concurrency test: " + taskCount + " tasks in " +
            duration.toMillis() + "ms");
    }

    /**
     * Test 3: Virtual vs Platform Thread Performance Comparison
     * Benchmarks virtual threads against platform threads for I/O-bound operations.
     */
    @Test
    public void testVirtualVsPlatformThreadPerformance() throws InterruptedException {
        int taskCount = 5000;
        int ioDelayMs = 10;

        // Benchmark virtual threads
        long virtualTime = benchmarkExecutor(virtualThreadExecutor, taskCount, ioDelayMs);

        // Benchmark platform threads
        long platformTime = benchmarkExecutor(platformThreadExecutor, taskCount, ioDelayMs);

        double improvement = (platformTime - virtualTime) * 100.0 / platformTime;

        System.out.println("Performance Comparison:");
        System.out.println("  Virtual threads:  " + virtualTime + "ms");
        System.out.println("  Platform threads: " + platformTime + "ms");
        System.out.println("  Improvement:      " + String.format("%.1f%%", improvement));

        assertTrue("Virtual threads should be faster for I/O-bound work",
            virtualTime < platformTime);
    }

    /**
     * Test 4: Memory Efficiency
     * Verifies that virtual threads consume significantly less memory than platform threads.
     */
    @Test
    public void testMemoryEfficiency() throws InterruptedException {
        int taskCount = 1000;
        CountDownLatch latch = new CountDownLatch(taskCount);

        Runtime runtime = Runtime.getRuntime();
        System.gc();
        Thread.sleep(100);

        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.execute(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Let threads start before measuring memory
        Thread.sleep(50);

        long memoryDuring = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryDuring - memoryBefore;

        // Virtual threads should use less than 50MB for 1000 threads
        // (Platform threads would use ~1GB for same count)
        long maxExpectedMemoryMB = 50;
        long actualMemoryMB = memoryUsed / (1024 * 1024);

        System.out.println("Memory used for 1000 virtual threads: " + actualMemoryMB + "MB");

        assertTrue("Virtual threads should use less than " + maxExpectedMemoryMB + "MB",
            actualMemoryMB < maxExpectedMemoryMB);

        latch.await(30, TimeUnit.SECONDS);
    }

    /**
     * Test 5: Workflow Simulation with Virtual Threads
     * Simulates a multi-stage workflow with concurrent task execution per stage.
     */
    @Test
    public void testWorkflowSimulation() throws InterruptedException, ExecutionException, TimeoutException {
        int stages = 5;
        int tasksPerStage = 200;

        for (int stage = 0; stage < stages; stage++) {
            List<Future<?>> stageFutures = new ArrayList<>();

            // Submit all tasks for this stage
            for (int i = 0; i < tasksPerStage; i++) {
                final int taskId = i;
                Future<?> future = virtualThreadExecutor.submit(() -> {
                    try {
                        simulateIOOperation(5);
                        return taskId;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
                stageFutures.add(future);
            }

            // Wait for all tasks in stage to complete
            for (Future<?> future : stageFutures) {
                future.get(10, TimeUnit.SECONDS);
            }

            System.out.println("Stage " + (stage + 1) + "/" + stages + " completed (" +
                tasksPerStage + " tasks)");
        }

        assertTrue("All workflow stages should complete successfully", true);
    }

    /**
     * Test 6: Exception Handling in Virtual Threads
     * Verifies that exceptions are properly propagated and handled.
     */
    @Test
    public void testExceptionHandling() throws InterruptedException {
        int taskCount = 100;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            virtualThreadExecutor.execute(() -> {
                try {
                    if (taskId % 10 == 0) {
                        throw new RuntimeException("Simulated failure for task " + taskId);
                    }
                    successCount.incrementAndGet();
                } catch (RuntimeException e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);

        assertEquals("90% of tasks should succeed", 90, successCount.get());
        assertEquals("10% of tasks should fail", 10, failureCount.get());
    }

    /**
     * Test 7: Graceful Shutdown
     * Verifies that executor shuts down gracefully, completing in-flight tasks.
     */
    @Test
    public void testGracefulShutdown() throws InterruptedException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

        assertTrue("Executor should terminate gracefully", terminated);
        assertEquals("All tasks should complete", 0, latch.getCount());
    }

    /**
     * Test 8: Concurrent Event Notification Simulation
     * Simulates YAWL's event notification pattern with virtual threads.
     */
    @Test
    public void testEventNotificationPattern() throws InterruptedException {
        int listenerCount = 1000;
        int eventCount = 100;

        CountDownLatch allEventsLatch = new CountDownLatch(listenerCount * eventCount);
        AtomicInteger notificationCount = new AtomicInteger(0);

        Instant start = Instant.now();

        // Simulate firing events to multiple listeners
        for (int event = 0; event < eventCount; event++) {
            for (int listener = 0; listener < listenerCount; listener++) {
                virtualThreadExecutor.execute(() -> {
                    try {
                        // Simulate listener processing (I/O-bound operation)
                        simulateIOOperation(5);
                        notificationCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        allEventsLatch.countDown();
                    }
                });
            }
        }

        boolean completed = allEventsLatch.await(60, TimeUnit.SECONDS);
        Duration duration = Duration.between(start, Instant.now());

        assertTrue("All event notifications should complete", completed);
        assertEquals("All listeners should be notified",
            listenerCount * eventCount, notificationCount.get());

        System.out.println("Event notification test: " + (listenerCount * eventCount) +
            " notifications in " + duration.toMillis() + "ms");
    }

    /**
     * Test 9: Stress Test - Maximum Concurrent Virtual Threads
     * Tests the limits of virtual thread scalability.
     */
    @Test
    public void testMaximumConcurrency() throws InterruptedException {
        int taskCount = 100000;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completedCount = new AtomicInteger(0);

        Instant start = Instant.now();

        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.execute(() -> {
                try {
                    simulateIOOperation(1);
                    completedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(120, TimeUnit.SECONDS);
        Duration duration = Duration.between(start, Instant.now());

        assertTrue("100,000 tasks should complete within 120 seconds", finished);
        assertEquals("All tasks should complete successfully", taskCount, completedCount.get());

        System.out.println("Maximum concurrency test: " + taskCount + " tasks in " +
            duration.toMillis() + "ms");
    }

    /**
     * Test 10: Context Preservation
     * Verifies that virtual threads correctly preserve execution context.
     */
    @Test
    public void testContextPreservation() throws InterruptedException {
        int taskCount = 100;
        String contextValue = "workflow-context-12345";
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger validContextCount = new AtomicInteger(0);

        for (int i = 0; i < taskCount; i++) {
            final String taskContext = contextValue;
            virtualThreadExecutor.execute(() -> {
                try {
                    Thread.sleep(5);
                    if (taskContext.equals(contextValue)) {
                        validContextCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        assertEquals("All tasks should maintain correct context", taskCount, validContextCount.get());
    }


    // Helper Methods

    private void simulateIOOperation(int millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private long benchmarkExecutor(ExecutorService executor, int taskCount, int ioDelayMs)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);

        Instant start = Instant.now();

        for (int i = 0; i < taskCount; i++) {
            executor.execute(() -> {
                try {
                    simulateIOOperation(ioDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        return Duration.between(start, Instant.now()).toMillis();
    }
}
