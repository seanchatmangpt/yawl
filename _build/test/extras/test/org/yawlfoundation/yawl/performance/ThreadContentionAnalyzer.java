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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

/**
 * Thread contention analyzer for Java 25 virtual thread migration.
 * 
 * Measures lock contention with virtual threads, identifies synchronized block bottlenecks,
 * and reports on ReentrantLock performance compared to synchronized blocks.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@Threads(1)
public class ThreadContentionAnalyzer {

    // Shared resources for contention testing
    private final SharedCounter sharedCounter = new SharedCounter();
    private final SharedResource sharedResource = new SharedResource();
    
    // Lock configurations
    private final ReentrantLock fairLock = new ReentrantLock(true); // Fair lock
    private final ReentrantLock unfairLock = new ReentrantLock(false); // Unfair lock
    private final StampedLock stampedLock = new StampedLock();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    
    // Test configuration
    private final int contentions = 1000;
    private final AtomicInteger contentionCount = new AtomicInteger(0);
    
    // Thread pool for contention testing
    private ExecutorService virtualThreadExecutor;
    private ExecutorService platformThreadExecutor;
    
    @Setup
    public void setup() {
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        platformThreadExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @TearDown
    public void tearDown() {
        virtualThreadExecutor.shutdown();
        platformThreadExecutor.shutdown();
    }

    // ============================================================================
    // Synchronized Block Benchmarks
    // ============================================================================

    /**
     * Benchmark: Synchronized block with virtual threads
     * Measures synchronized block performance with virtual threads
     */
    @Benchmark
    public void synchronizedBlockVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    // Synchronized block - common in YAWL workflow engine
                    synchronized (sharedResource) {
                        sharedCounter.increment();
                        simulateWork();
                        bh.consume(sharedCounter.getValue());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Synchronized block with platform threads
     * Measures synchronized block performance with platform threads
     */
    @Benchmark
    public void synchronizedBlockPlatformThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            platformThreadExecutor.submit(() -> {
                try {
                    // Synchronized block - common in YAWL workflow engine
                    synchronized (sharedResource) {
                        sharedCounter.increment();
                        simulateWork();
                        bh.consume(sharedCounter.getValue());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    // ============================================================================
    // ReentrantLock Benchmarks
    // ============================================================================

    /**
     * Benchmark: ReentrantLock (fair) with virtual threads
     * Measures fair ReentrantLock performance with virtual threads
     */
    @Benchmark
    public void reentrantLockFairVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    fairLock.lock();
                    try {
                        sharedCounter.increment();
                        simulateWork();
                        bh.consume(sharedCounter.getValue());
                    } finally {
                        fairLock.unlock();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: ReentrantLock (fair) with platform threads
     * Measures fair ReentrantLock performance with platform threads
     */
    @Benchmark
    public void reentrantLockFairPlatformThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            platformThreadExecutor.submit(() -> {
                try {
                    fairLock.lock();
                    try {
                        sharedCounter.increment();
                        simulateWork();
                        bh.consume(sharedCounter.getValue());
                    } finally {
                        fairLock.unlock();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: ReentrantLock (unfair) with virtual threads
     * Measures unfair ReentrantLock performance with virtual threads
     */
    @Benchmark
    public void reentrantLockUnfairVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    unfairLock.lock();
                    try {
                        sharedCounter.increment();
                        simulateWork();
                        bh.consume(sharedCounter.getValue());
                    } finally {
                        unfairLock.unlock();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: ReentrantLock (unfair) with platform threads
     * Measures unfair ReentrantLock performance with platform threads
     */
    @Benchmark
    public void reentrantLockUnfairPlatformThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            platformThreadExecutor.submit(() -> {
                try {
                    unfairLock.lock();
                    try {
                        sharedCounter.increment();
                        simulateWork();
                        bh.consume(sharedCounter.getValue());
                    } finally {
                        unfairLock.unlock();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    // ============================================================================
    // Read-Write Lock Benchmarks
    // ============================================================================

    /**
     * Benchmark: Read lock with virtual threads
     * Measures read lock performance with virtual threads
     */
    @Benchmark
    public void readLockVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    readWriteLock.readLock().lock();
                    try {
                        // Read operation - common in YAWL for case state access
                        long value = sharedCounter.getValue();
                        bh.consume(value);
                    } finally {
                        readWriteLock.readLock().unlock();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Write lock with virtual threads
     * Measures write lock performance with virtual threads
     */
    @Benchmark
    public void writeLockVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    readWriteLock.writeLock().lock();
                    try {
                        // Write operation - common in YAWL for case updates
                        sharedCounter.increment();
                        bh.consume(sharedCounter.getValue());
                    } finally {
                        readWriteLock.writeLock().unlock();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    // ============================================================================
    // StampedLock Benchmarks
    // ============================================================================

    /**
     * Benchmark: StampedLock read with virtual threads
     * Measures optimistic read performance with virtual threads
     */
    @Benchmark
    public void stampedLockOptimisticReadVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    // Optimistic read - good for virtual threads
                    long stamp = stampedLock.tryOptimisticRead();
                    int value = sharedCounter.getValue();
                    
                    if (!stampedLock.validate(stamp)) {
                        // Fallback to read lock if validation fails
                        stamp = stampedLock.readLock();
                        try {
                            value = sharedCounter.getValue();
                        } finally {
                            stampedLock.unlockRead(stamp);
                        }
                    }
                    
                    bh.consume(value);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: StampedLock write with virtual threads
     * Measures write lock performance with virtual threads
     */
    @Benchmark
    public void stampedLockWriteVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            virtualThreadExecutor.submit(() -> {
                try {
                    long stamp = stampedLock.writeLock();
                    try {
                        sharedCounter.increment();
                        bh.consume(sharedCounter.getValue());
                    } finally {
                        stampedLock.unlockWrite(stamp);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    // ============================================================================
    // Contention Pattern Benchmarks
    // ============================================================================

    /**
     * Benchmark: High contention read-mostly pattern
     * Simulates YAWL case state access patterns (mostly reads, occasional writes)
     */
    @Benchmark
    public void readMostlyContentionVirtualThreads(Blackhole bh) throws InterruptedException {
        final double readRatio = 0.9; // 90% reads, 10% writes
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            final boolean isRead = ThreadLocalRandom.current().nextDouble() < readRatio;
            
            virtualThreadExecutor.submit(() -> {
                try {
                    if (isRead) {
                        // Read operation
                        readWriteLock.readLock().lock();
                        try {
                            long value = sharedCounter.getValue();
                            bh.consume(value);
                        } finally {
                            readWriteLock.readLock().unlock();
                        }
                    } else {
                        // Write operation
                        readWriteLock.writeLock().lock();
                        try {
                            sharedCounter.increment();
                            bh.consume(sharedCounter.getValue());
                        } finally {
                            readWriteLock.writeLock().unlock();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: High contention write-mostly pattern
     * Simulates YAWL task execution patterns (mostly writes, occasional reads)
     */
    @Benchmark
    public void writeMostlyContentionVirtualThreads(Blackhole bh) throws InterruptedException {
        final double writeRatio = 0.8; // 80% writes, 20% reads
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            final boolean isWrite = ThreadLocalRandom.current().nextDouble() < writeRatio;
            
            virtualThreadExecutor.submit(() -> {
                try {
                    if (isWrite) {
                        // Write operation
                        readWriteLock.writeLock().lock();
                        try {
                            sharedCounter.increment();
                            bh.consume(sharedCounter.getValue());
                        } finally {
                            readWriteLock.writeLock().unlock();
                        }
                    } else {
                        // Read operation
                        readWriteLock.readLock().lock();
                        try {
                            long value = sharedCounter.getValue();
                            bh.consume(value);
                        } finally {
                            readWriteLock.readLock().unlock();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Fine-grained locking pattern
     * Compares fine-grained locking vs coarse-grained locking
     */
    @Benchmark
    public void fineGrainedLockingVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            final int taskNum = i % 10; // 10 different resources
            final Lock resourceLock = sharedResource.getLock(taskNum);
            
            virtualThreadExecutor.submit(() -> {
                try {
                    resourceLock.lock();
                    try {
                        sharedCounter.increment();
                        simulateTaskProcessing(taskNum);
                        bh.consume(sharedCounter.getValue());
                    } finally {
                        resourceLock.unlock();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    /**
     * Benchmark: Coarse-grained locking pattern
     * Compares coarse-grained locking vs fine-grained locking
     */
    @Benchmark
    public void coarseGrainedLockingVirtualThreads(Blackhole bh) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(contentions);
        
        for (int i = 0; i < contentions; i++) {
            final int taskNum = i % 10; // 10 different resources
            
            virtualThreadExecutor.submit(() -> {
                try {
                    // Coarse lock on all resources
                    synchronized (sharedResource) {
                        sharedCounter.increment();
                        simulateTaskProcessing(taskNum);
                        bh.consume(sharedCounter.getValue());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
    }

    // ============================================================================
    // Helper Classes and Methods
    // ============================================================================

    /**
     * Shared counter for contention testing
     */
    private static class SharedCounter {
        private final AtomicLong value = new AtomicLong(0);
        
        public void increment() {
            value.incrementAndGet();
        }
        
        public long getValue() {
            return value.get();
        }
    }

    /**
     * Shared resource for contention testing
     */
    private static class SharedResource {
        private final Lock[] locks = new Lock[10];
        
        public SharedResource() {
            for (int i = 0; i < locks.length; i++) {
                locks[i] = new ReentrantLock();
            }
        }
        
        public Lock getLock(int index) {
            return locks[index];
        }
    }

    /**
     * Simulate YAWL work processing
     */
    private void simulateWork() {
        // Simulate some processing work
        double result = 0;
        for (int i = 0; i < 10; i++) {
            result += Math.sqrt(i) * Math.sin(i);
        }
    }

    /**
     * Simulate task-specific processing
     */
    private void simulateTaskProcessing(int taskNum) {
        // Simulate task-specific processing
        double result = 0;
        for (int i = 0; i < 5; i++) {
            result += Math.sqrt(taskNum + i) * Math.cos(i);
        }
    }

    // ============================================================================
    // Contention Analysis Results
    // ============================================================================

    /**
     * Contention analysis results
     */
    private static class ContentionAnalysis {
        private final String lockType;
        private final String threadType;
        private final long averageLatency;
        private final long maxLatency;
        private final double throughput;
        private final double contentionRatio;
        
        public ContentionAnalysis(String lockType, String threadType, 
                                long averageLatency, long maxLatency, 
                                double throughput, double contentionRatio) {
            this.lockType = lockType;
            this.threadType = threadType;
            this.averageLatency = averageLatency;
            this.maxLatency = maxLatency;
            this.throughput = throughput;
            this.contentionRatio = contentionRatio;
        }
        
        public String getPerformanceRating() {
            if (contentionRatio < 0.1) return "Excellent";
            if (contentionRatio < 0.3) return "Good";
            if (contentionRatio < 0.5) return "Fair";
            return "Poor";
        }
        
        public String getRecommendation() {
            if (lockType.contains("synchronized")) {
                return "Consider replacing with ReentrantLock for better performance with virtual threads";
            }
            if (lockType.contains("fair")) {
                return "Consider unfair lock for better throughput in high-contention scenarios";
            }
            if (lockType.contains("optimistic")) {
                return "Optimistic read pattern works well with virtual threads";
            }
            return "Lock configuration is appropriate for this workload";
        }
    }
}
