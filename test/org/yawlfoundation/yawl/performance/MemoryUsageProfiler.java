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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory usage profiler for Java 25 virtual thread migration.
 * 
 * Compares memory usage patterns between virtual threads and platform threads,
 * with a focus on compact object headers and ScopedValue vs ThreadLocal performance.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
public class MemoryUsageProfiler {

    // Memory monitoring
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    
    // Test data
    private final int threadCount = 10000;
    private final Object[] testData = new Object[1000];
    
    // Thread local vs scoped value storage
    private final ConcurrentHashMap<Long, ScopedContext> scopedValueStorage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ThreadLocalContext> threadLocalStorage = new ConcurrentHashMap<>();
    
    @Setup
    public void setup() {
        // Initialize test data
        for (int i = 0; i < testData.length; i++) {
            testData[i] = new TestDataObject("Test-" + i, i * 1.5);
        }
    }

    // ============================================================================
    // Compact Object Headers Benchmark
    // ============================================================================

    /**
     * Benchmark: Memory allocation with compact object headers
     * Measures the impact of compact object headers on memory usage
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void compactObjectHeadersAllocation(Blackhole bh) {
        // Create many objects to test compact headers performance
        for (int i = 0; i < 10000; i++) {
            TestDataObject obj = new TestDataObject("Compact-" + i, i);
            bh.consume(obj);
        }
    }

    /**
     * Benchmark: Object access patterns with compact headers
     * Measures access performance with compact object headers
     */
    @Benchmark
    public void compactObjectHeadersAccess(Blackhole bh) {
        // Access objects in patterns that benefit from compact headers
        long sum = 0;
        for (int i = 0; i < testData.length; i++) {
            TestDataObject obj = (TestDataObject) testData[i];
            sum += obj.id + (long) obj.value;
        }
        bh.consume(sum);
    }

    // ============================================================================
    // ScopedValue vs ThreadLocal Benchmarks
    // ============================================================================

    /**
     * Benchmark: ScopedValue access pattern
     * Measures ScopedValue performance vs ThreadLocal
     */
    @Benchmark
    public void scopedValueAccess(Blackhole bh) {
        // Simulate ScopedValue pattern using thread-local storage
        long threadId = Thread.currentThread().threadId();
        ScopedContext context = scopedValueStorage.computeIfAbsent(
            threadId, 
            id -> new ScopedContext("Workflow-" + threadId)
        );
        
        // Simulate ScopedValue usage
        String workflowId = context.workflowId;
        int currentTask = context.taskCounter.getAndIncrement();
        
        // Process with the context
        bh.consume(workflowId + "-" + currentTask);
    }

    /**
     * Benchmark: ThreadLocal access pattern
     * Measures ThreadLocal performance
     */
    @Benchmark
    public void threadLocalAccess(Blackhole bh) {
        // Simulate ThreadLocal pattern
        long threadId = Thread.currentThread().threadId();
        ThreadLocalContext context = threadLocalStorage.computeIfAbsent(
            threadId, 
            id -> new ThreadLocalContext("Workflow-" + threadId)
        );
        
        // Simulate ThreadLocal usage
        String workflowId = context.workflowId;
        int currentTask = context.taskCounter.getAndIncrement();
        
        // Process with the context
        bh.consume(workflowId + "-" + currentTask);
    }

    /**
     * Benchmark: ScopedValue inheritance pattern
     * Measures performance of ScopedValue inheritance in virtual threads
     */
    @Benchmark
    public void scopedValueInheritance(Blackhole bh) throws InterruptedException {
        // Simulate ScopedValue inheritance using virtual threads
        String parentContext = "Parent-Workflow-" + Thread.currentThread().threadId();
        
        Runnable childTask = () -> {
            // Simulate ScopedValue inheritance
            String childContext = "Child-" + Thread.currentThread().threadId();
            bh.consume(parentContext + ":" + childContext);
        };
        
        // Execute in virtual thread
        Thread.ofVirtual().name("scoped-inheritance-test").start(childTask).join();
    }

    /**
     * Benchmark: ThreadLocal inheritance pattern
     * Measures performance of ThreadLocal inheritance in platform threads
     */
    @Benchmark
    public void threadLocalInheritance(Blackhole bh) throws InterruptedException {
        // Simulate ThreadLocal inheritance using platform threads
        String parentContext = "Parent-Workflow-" + Thread.currentThread().threadId();
        
        Runnable childTask = () -> {
            // Simulate ThreadLocal inheritance (manual copy)
            String childContext = "Child-" + Thread.currentThread().threadId();
            bh.consume(parentContext + ":" + childContext);
        };
        
        // Execute in platform thread
        Thread thread = Thread.ofPlatform().name("thread-local-inheritance-test").start(childTask);
        thread.join();
    }

    // ============================================================================
    // Virtual Thread Memory Footprint Benchmarks
    // ============================================================================

    /**
     * Benchmark: Virtual thread memory footprint
     * Measures memory usage of many virtual threads
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void virtualThreadMemoryFootprint(Blackhole bh) throws InterruptedException {
        // Create and manage many virtual threads
        Thread[] virtualThreads = new Thread[threadCount];
        
        long startMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        
        // Create virtual threads
        for (int i = 0; i < threadCount; i++) {
            virtualThreads[i] = Thread.ofVirtual()
                .name("virtual-thread-" + i)
                .start(() -> {
                    // Simulate work
                    for (int j = 0; j < 100; j++) {
                        bh.consume(testData[j % testData.length]);
                    }
                });
        }
        
        // Wait for completion
        for (Thread thread : virtualThreads) {
            thread.join();
        }
        
        long endMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long memoryDelta = endMemory - startMemory;
        
        bh.consume(memoryDelta);
    }

    /**
     * Benchmark: Platform thread memory footprint
     * Measures memory usage of many platform threads
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void platformThreadMemoryFootprint(Blackhole bh) throws InterruptedException {
        // Create and manage many platform threads
        Thread[] platformThreads = new Thread[Math.min(threadCount, 1000)]; // Limit to avoid OOM
        
        long startMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        
        // Create platform threads
        for (int i = 0; i < platformThreads.length; i++) {
            platformThreads[i] = Thread.ofPlatform()
                .name("platform-thread-" + i)
                .start(() -> {
                    // Simulate work
                    for (int j = 0; j < 100; j++) {
                        bh.consume(testData[j % testData.length]);
                    }
                });
        }
        
        // Wait for completion
        for (Thread thread : platformThreads) {
            thread.join();
        }
        
        long endMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long memoryDelta = endMemory - startMemory;
        
        bh.consume(memoryDelta);
    }

    /**
     * Benchmark: Thread pool memory usage comparison
     * Compares memory usage between different thread pool implementations
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void threadPoolMemoryComparison(Blackhole bh) throws InterruptedException {
        // Virtual thread pool
        ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor();
        long startVirtualMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        
        // Submit tasks to virtual thread pool
        for (int i = 0; i < 5000; i++) {
            virtualPool.submit(() -> {
                bh.consume(testData[0]);
            });
        }
        
        virtualPool.shutdown();
        try {
            virtualPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endVirtualMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long virtualMemoryDelta = endVirtualMemory - startVirtualMemory;
        
        // Platform thread pool
        ExecutorService platformPool = Executors.newFixedThreadPool(100);
        long startPlatformMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        
        // Submit tasks to platform thread pool
        for (int i = 0; i < 5000; i++) {
            platformPool.submit(() -> {
                bh.consume(testData[0]);
            });
        }
        
        platformPool.shutdown();
        try {
            platformPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endPlatformMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long platformMemoryDelta = endPlatformMemory - startPlatformMemory;
        
        bh.consume(new MemoryComparison(virtualMemoryDelta, platformMemoryDelta));
    }

    // ============================================================================
    // Helper Classes and Methods
    // ============================================================================

    /**
     * Test data object for compact headers testing
     */
    private static class TestDataObject {
        private final String name;
        private final double value;
        private final long id;
        
        public TestDataObject(String name, double value) {
            this.name = name;
            this.value = value;
            this.id = ThreadLocalRandom.current().nextLong();
        }
        
        public long getId() { return id; }
        public double getValue() { return value; }
    }

    /**
     * Simulated ScopedValue context
     */
    private static class ScopedContext {
        private final String workflowId;
        private final AtomicLong taskCounter = new AtomicLong(0);
        
        public ScopedContext(String workflowId) {
            this.workflowId = workflowId;
        }
    }

    /**
     * Simulated ThreadLocal context
     */
    private static class ThreadLocalContext {
        private final String workflowId;
        private final AtomicLong taskCounter = new AtomicLong(0);
        
        public ThreadLocalContext(String workflowId) {
            this.workflowId = workflowId;
        }
    }

    /**
     * Memory comparison result
     */
    private record MemoryComparison(long virtualThreadMemory, long platformThreadMemory) {
        public double getMemorySavingPercent() {
            if (platformThreadMemory == 0) return 0;
            return ((double) (platformThreadMemory - virtualThreadMemory) / platformThreadMemory) * 100;
        }
    }

    // ============================================================================
    // Memory Monitoring Methods
    // ============================================================================

    /**
     * Get current heap memory usage
     */
    private MemoryUsage getHeapMemoryUsage() {
        return memoryMXBean.getHeapMemoryUsage();
    }

    /**
     * Get thread count
     */
    private int getThreadCount() {
        return threadMXBean.getThreadCount();
    }

    /**
     * Get peak thread count
     */
    private long getPeakThreadCount() {
        return threadMXBean.getPeakThreadCount();
    }

    /**
     * Memory analysis helper
     */
    private static class MemoryAnalysis {
        private final long heapUsed;
        private final long heapMax;
        private final int threadCount;
        private final long peakThreadCount;
        
        public MemoryAnalysis(long heapUsed, long heapMax, int threadCount, long peakThreadCount) {
            this.heapUsed = heapUsed;
            this.heapMax = heapMax;
            this.threadCount = threadCount;
            this.peakThreadCount = peakThreadCount;
        }
        
        public double getHeapUsagePercent() {
            return (double) heapUsed / heapMax * 100;
        }
        
        public String getMemoryEfficiency() {
            if (getHeapUsagePercent() < 50) return "Excellent";
            if (getHeapUsagePercent() < 75) return "Good";
            if (getHeapUsagePercent() < 90) return "Fair";
            return "Poor";
        }
    }
}
