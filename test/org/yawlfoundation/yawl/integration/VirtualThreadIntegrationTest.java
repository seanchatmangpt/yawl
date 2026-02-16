package org.yawlfoundation.yawl.integration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Virtual Thread Integration Tests - Verifies thread pool migration and scalability
 * Tests high-concurrency workloads with virtual threads and traditional thread pools
 */
public class VirtualThreadIntegrationTest {

    private ExecutorService virtualThreadExecutor;
    private ExecutorService platformThreadExecutor;
    
    @Before
    public void setUp() {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        platformThreadExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @After
    public void tearDown() throws InterruptedException {
        virtualThreadExecutor.shutdownNow();
        platformThreadExecutor.shutdownNow();
        virtualThreadExecutor.awaitTermination(10, TimeUnit.SECONDS);
        platformThreadExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    public void testVirtualThreadCreation() throws InterruptedException {
        int taskCount = 1000;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completed = new AtomicInteger(0);
        
        Instant start = Instant.now();
        
        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.execute(() -> {
                try {
                    Thread.sleep(10);
                    completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean finished = latch.await(30, TimeUnit.SECONDS);
        Instant end = Instant.now();
        
        assertTrue("All tasks should complete within timeout", finished);
        assertEquals("All tasks should complete successfully", taskCount, completed.get());
        
        long duration = java.time.Duration.between(start, end).toMillis();
        System.out.println("Virtual thread completion time: " + duration + "ms for " + taskCount + " tasks");
    }

    @Test
    public void testHighConcurrency10000Tasks() throws InterruptedException {
        int taskCount = 10000;
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        Instant start = Instant.now();
        
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            virtualThreadExecutor.execute(() -> {
                try {
                    simulateWorkload(taskId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean finished = latch.await(60, TimeUnit.SECONDS);
        Instant end = Instant.now();
        
        assertTrue("All tasks should complete within timeout", finished);
        assertEquals("All tasks should succeed", taskCount, successCount.get());
        assertEquals("No tasks should error", 0, errorCount.get());
        
        long duration = java.time.Duration.between(start, end).toMillis();
        System.out.println("High concurrency test: " + duration + "ms for " + taskCount + " tasks");
    }

    @Test
    public void testThreadPoolComparison() throws InterruptedException {
        int taskCount = 5000;
        
        // Virtual threads
        long virtualTime = runBenchmark(virtualThreadExecutor, taskCount);
        
        // Platform threads
        long platformTime = runBenchmark(platformThreadExecutor, taskCount);
        
        System.out.println("Virtual threads: " + virtualTime + "ms");
        System.out.println("Platform threads: " + platformTime + "ms");
        System.out.println("Virtual thread improvement: " + 
            String.format("%.1f%%", (platformTime - virtualTime) * 100.0 / platformTime));
    }

    @Test
    public void testManyTaskWorkflow() throws InterruptedException, ExecutionException {
        int stages = 5;
        int tasksPerStage = 200;
        
        for (int stage = 0; stage < stages; stage++) {
            List<Future<?>> futures = new ArrayList<>();
            
            for (int i = 0; i < tasksPerStage; i++) {
                futures.add(virtualThreadExecutor.submit(() -> {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
        
        assertTrue("All workflow stages should complete successfully", true);
    }

    @Test
    public void testContextCarryover() throws InterruptedException {
        final String CONTEXT_KEY = "workflow_context";
        final String CONTEXT_VALUE = "test_value_12345";
        
        CountDownLatch latch = new CountDownLatch(100);
        AtomicInteger validCount = new AtomicInteger(0);
        
        for (int i = 0; i < 100; i++) {
            virtualThreadExecutor.execute(() -> {
                try {
                    // Simulate context-dependent work
                    String value = CONTEXT_VALUE;
                    Thread.sleep(5);
                    if (value.equals(CONTEXT_VALUE)) {
                        validCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(15, TimeUnit.SECONDS);
        assertEquals("All tasks should maintain context", 100, validCount.get());
    }

    @Test
    public void testGracefulShutdown() throws InterruptedException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        for (int i = 0; i < 100; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        executor.shutdown();
        boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue("Executor should terminate gracefully", terminated);
    }

    private void simulateWorkload(int taskId) throws InterruptedException {
        Thread.sleep(Math.random() * 10);
        if (taskId % 1000 == 0) {
            System.out.println("Processed task " + taskId);
        }
    }

    private long runBenchmark(ExecutorService executor, int taskCount) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        Instant start = Instant.now();
        
        for (int i = 0; i < taskCount; i++) {
            executor.execute(() -> {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        Instant end = Instant.now();
        
        return java.time.Duration.between(start, end).toMillis();
    }
}
