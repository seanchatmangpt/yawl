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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.performance;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.VirtualThreadPool;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * Comprehensive JMH benchmark suite for Java 25 virtual thread migration.
 * 
 * Benchmarks virtual threads vs platform threads for YAWL workflow engine
 * performance, measuring throughput, latency, and context switching overhead.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
public class ConcurrencyBenchmarkSuite {

    // Thread pool configurations
    private ExecutorService platformThreadExecutor;
    private ExecutorService virtualThreadExecutor;
    private VirtualThreadPool virtualThreadPool;
    private ForkJoinPool virtualThreadForkJoinPool;
    
    // Test data
    private final int taskCount = 1000;
    private final AtomicInteger taskCounter = new AtomicInteger(0);
    
    @Setup
    public void setup() {
        // Platform thread executor (current approach)
        platformThreadExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        
        // Virtual thread executor (new per-task approach)
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        
        // Virtual thread pool with auto-scaling
        virtualThreadPool = new VirtualThreadPool("benchmark-pool", 100, 5);
        virtualThreadPool.start();
        
        // ForkJoinPool with virtual threads (structured concurrency)
        virtualThreadForkJoinPool = new ForkJoinPool(
            Runtime.getRuntime().availableProcessors(),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            true // asyncMode enables virtual threads
        );
    }

    @TearDown
    public void tearDown() {
        platformThreadExecutor.shutdown();
        virtualThreadExecutor.shutdown();
        virtualThreadPool.shutdown();
        virtualThreadForkJoinPool.shutdown();
    }

    // ============================================================================
    // YAWL Task Execution Benchmarks
    // ============================================================================

    /**
     * Benchmark: Platform threads processing YAWL tasks
     * Measures current YAWL approach performance
     */
    @Benchmark
    public void platformThreadTaskExecution(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            platformThreadExecutor.submit(() -> {
                try {
                    executeWorkflowTask();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Virtual threads processing YAWL tasks
     * Measures performance with virtual threads per task
     */
    @Benchmark
    public void virtualThreadTaskExecution(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    executeWorkflowTask();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Auto-scaling virtual thread pool processing YAWL tasks
     * Measures performance with optimized virtual thread pool
     */
    @Benchmark
    public void virtualThreadPoolTaskExecution(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            virtualThreadPool.submit(() -> {
                try {
                    executeWorkflowTask();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Structured concurrency with virtual threads
     * Measures performance with structured task scope
     */
    @Benchmark
    public void structuredConcurrencyVirtualThreads(Blackhole bh) throws ExecutionException, InterruptedException {
        virtualThreadForkJoinPool.submit(() -> {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                for (int i = 0; i < taskCount; i++) {
                    scope.fork(() -> {
                        executeWorkflowTask();
                        return null;
                    });
                }
                scope.join();
                scope.throwIfFailed();
            }
            return null;
        }).get();
    }

    // ============================================================================
    // YAWL Work Item Benchmarks
    // ============================================================================

    /**
     * Benchmark: Work item checkout (platform threads)
     * Measures current YAWL work item checkout performance
     */
    @Benchmark
    public void workItemCheckoutPlatformThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            platformThreadExecutor.submit(() -> {
                try {
                    simulateWorkItemCheckout();
                    bh.consume(taskCounter.getAndIncrement());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Work item checkout (virtual threads)
     * Measures virtual thread performance for work item checkout
     */
    @Benchmark
    public void workItemCheckoutVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    simulateWorkItemCheckout();
                    bh.consume(taskCounter.getAndIncrement());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Work item checkin (platform threads)
     * Measures current YAWL work item checkin performance
     */
    @Benchmark
    public void workItemCheckinPlatformThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            platformThreadExecutor.submit(() -> {
                try {
                    simulateWorkItemCheckin();
                    bh.consume(taskCounter.getAndIncrement());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Work item checkin (virtual threads)
     * Measures virtual thread performance for work item checkin
     */
    @Benchmark
    public void workItemCheckinVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    simulateWorkItemCheckin();
                    bh.consume(taskCounter.getAndIncrement());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        
        latch.await();
    }

    // ============================================================================
    // Context Switching Benchmarks
    // ============================================================================

    /**
     * Benchmark: Context switching overhead (platform threads)
     * Measures thread switching cost with YAWL-style work
     */
    @Benchmark
    public void contextSwitchingPlatformThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        
        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                try {
                    // Simulate context switch - create and join many small tasks
                    for (int j = 0; j < 10; j++) {
                        Future<?> future = executor.submit(() -> {
                            Thread.yield();
                            return null;
                        });
                        future.get();
                    }
                } catch (ExecutionException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
    }

    /**
     * Benchmark: Context switching overhead (virtual threads)
     * Measures virtual thread switching cost with YAWL-style work
     */
    @Benchmark
    public void contextSwitchingVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    // Simulate context switch - create and join many small tasks
                    for (int j = 0; j < 10; j++) {
                        Future<?> future = virtualThreadExecutor.submit(() -> {
                            Thread.yield();
                            return null;
                        });
                        future.get();
                    }
                } catch (ExecutionException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    // ============================================================================
    // Case Creation Benchmarks
    // ============================================================================

    /**
     * Benchmark: Case creation (platform threads)
     * Measures YAWL case creation performance with platform threads
     */
    @Benchmark
    public void caseCreationPlatformThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            platformThreadExecutor.submit(() -> {
                try {
                    simulateCaseCreation();
                    bh.consume(taskCounter.getAndIncrement());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Case creation (virtual threads)
     * Measures YAWL case creation performance with virtual threads
     */
    @Benchmark
    public void caseCreationVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    simulateCaseCreation();
                    bh.consume(taskCounter.getAndIncrement());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    // ============================================================================
    // Task Transition Benchmarks
    // ============================================================================

    /**
     * Benchmark: Task transitions (platform threads)
     * Measures YAWL task transition performance
     */
    @Benchmark
    public void taskTransitionsPlatformThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        ReentrantLock transitionLock = new ReentrantLock();
        
        for (int i = 0; i < taskCount; i++) {
            platformThreadExecutor.submit(() -> {
                try {
                    simulateTaskTransition(transitionLock);
                    bh.consume(taskCounter.getAndIncrement());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Task transitions (virtual threads)
     * Measures YAWL task transition performance with virtual threads
     */
    @Benchmark
    public void taskTransitionsVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        ReentrantLock transitionLock = new ReentrantLock();
        
        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    simulateTaskTransition(transitionLock);
                    bh.consume(taskCounter.getAndIncrement());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    // ============================================================================
    // Helper Methods - Simulated YAWL Operations
    // ============================================================================

    /**
     * Simulates a YAWL workflow task execution
     */
    private void executeWorkflowTask() {
        // Simulate task processing
        double computation = 0;
        for (int i = 0; i < 100; i++) {
            computation += Math.sqrt(i) * Math.sin(i);
        }
        
        // Simulate I/O wait (common in YAWL)
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulates YAWL work item checkout
     */
    private void simulateWorkItemCheckout() {
        // Simulate database interaction for work item checkout
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Simulate some processing
        int taskId = taskCounter.getAndIncrement();
        if (taskId % 100 == 0) {
            // Simulate occasional database query
            simulateDatabaseQuery();
        }
    }

    /**
     * Simulates YAWL work item checkin
     */
    private void simulateWorkItemCheckin() {
        // Simulate database interaction for work item checkin
        try {
            Thread.sleep(3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Simulate logging and state updates
        int taskId = taskCounter.getAndIncrement();
        if (taskId % 50 == 0) {
            // Simulate occasional logging
            simulateLogging();
        }
    }

    /**
     * Simulates YAWL case creation
     */
    private void simulateCaseCreation() {
        // Simulate case initialization
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Simulate creating initial work items
        for (int i = 0; i < 3; i++) {
            simulateWorkItemCheckout();
        }
    }

    /**
     * Simulates YAWL task transition with lock
     */
    private void simulateTaskTransition(ReentrantLock transitionLock) {
        transitionLock.lock();
        try {
            // Simulate task state transition
            Thread.sleep(1);
            
            // Simulate transition processing
            int taskId = taskCounter.getAndIncrement();
            if (taskId % 200 == 0) {
                // Simulate notification
                simulateNotification();
            }
        } finally {
            transitionLock.unlock();
        }
    }

    /**
     * Simulates database query
     */
    private void simulateDatabaseQuery() {
        // Simulate database access
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulates logging operation
     */
    private void simulateLogging() {
        // Simulate logging operation
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Simulates notification
     */
    private void simulateNotification() {
        // Simulate notification system
        try {
            Thread.sleep(3);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ============================================================================
    // Thread Configuration Tests
    // ============================================================================

    /**
     * Benchmark: Throughput with different platform thread pool sizes
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void platformThreadPoolSizing(Blackhole bh) throws InterruptedException {
        // Test with fixed pool size
        ExecutorService executor = Executors.newFixedThreadPool(16);
        
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                executeWorkflowTask();
                latch.countDown();
            });
        }
        
        latch.await();
        executor.shutdown();
        
        bh.consume(taskCounter.get());
    }

    /**
     * Benchmark: Throughput with virtual threads
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void virtualThreadThroughput(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        for (int i = 0; i < taskCount; i++) {
            virtualThreadExecutor.submit(() -> {
                executeWorkflowTask();
                latch.countDown();
            });
        }
        
        latch.await();
        bh.consume(taskCounter.get());
    }
}
