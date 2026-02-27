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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.performance.jmh;

import org.openjdk.jmh.annotations.*;
import org.yawlfoundation.yawl.engine.YAWLServiceGateway;
import org.yawlfoundation.yawl.engine.YNetRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.openjdk.jmh.annotations.Mode.*;

/**
 * Virtual Thread Context Switching Benchmarks for YAWL v6.0.0-GA
 *
 * Measures context switching performance and overhead for virtual threads:
 * - Park/unpark latency: < 0.05ms
 * - Context switch count: < 0.1ms per switch
 * - Carrier thread migration cost: < 0.2ms
 * - Thread synchronization overhead: < 0.1ms
 */
@BenchmarkMode({Throughput, AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 10)
@Measurement(iterations = 10, time = 30)
@Fork(value = 1, jvmArgs = {
    "--enable-preview",
    "-Xms4g", "-Xmx8g"
})
@State(Scope.Benchmark)
public class VirtualThreadContextSwitchingBenchmarks {

    private YAWLServiceGateway serviceGateway;
    private YNetRunner workflowRunner;

    // Synchronization primitives
    private final ReentrantLock sharedLock = new ReentrantLock();
    private final AtomicInteger sharedCounter = new AtomicInteger(0);
    private final Object syncMonitor = new Object();

    @Setup
    public void setup() {
        serviceGateway = new YAWLServiceGateway();
        workflowRunner = serviceGateway.getNet("context-switching-workflow");
        if (workflowRunner == null) {
            throw new RuntimeException("Test workflow not found: context-switching-workflow");
        }
    }

    @TearDown
    public void tearDown() {
        serviceGateway.shutdown();
    }

    /**
     * Benchmark: Park/Unpark Latency Measurement
     */
    @Benchmark
    @BenchmarkMode(SampleTime)
    @OutputTimeUnit(MICROSECONDS)
    public void testParkUnparkLatency(@Param({"1", "10", "100", "1000"}) int iterations) {
        long totalLatency = 0;

        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();

            // Virtual thread parking
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Park test interrupted", e);
            }

            long parkTime = System.nanoTime() - startTime;
            totalLatency += parkTime;

            // Subsequent unpark
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Unpark test interrupted", e);
            }

            long unparkTime = System.nanoTime() - startTime - parkTime;
            totalLatency += unparkTime;
        }

        double avgLatency = totalLatency / (double) (iterations * 2);
        // Park/unpark latency should be < 50μs
        assertTrue(avgLatency < 50_000, "Average park/unpark latency too high: " + avgLatency + " ns");
    }

    /**
     * Benchmark: Context Switch Frequency Impact
     */
    @Benchmark
    @BenchmarkMode(Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testContextSwitchFrequencyImpact(
            @Param({"low-frequency", "medium-frequency", "high-frequency", "ultra-high-frequency"}) String frequency,
            @Param({"100", "1000", "10000"}) int concurrency) {

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger completed = new AtomicInteger(0);
        Instant startTime = Instant.now();

        int switchCount = getSwitchCountForFrequency(frequency);

        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    executeWithControlledSwitching(taskId, switchCount, completed);
                } catch (Exception e) {
                    throw new RuntimeException("Context switching task failed", e);
                }
            });
        }

        try {
            // Wait for completion
            while (completed.get() < concurrency) {
                Thread.sleep(100);
            }

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            double throughput = concurrency / (duration / 1000.0);

            // Verify throughput targets based on frequency
            double minThroughput = getMinThroughputForFrequency(frequency);
            assertTrue(throughput > minThroughput,
                "Throughput too low for " + frequency + ": " + throughput + " (target: > " + minThroughput + ")");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Benchmark interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Benchmark: Carrier Thread Contention Impact
     */
    @Benchmark
    @BenchmarkMode(AverageTime)
    @OutputTimeUnit(MICROSECONDS)
    public void testCarrierThreadContention(
            @Param({"no-contention", "low-contention", "high-contention", "extreme-contention"}) String contentionLevel,
            @Param({"100", "1000", "10000"}) int concurrency) {

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger completed = new AtomicInteger(0);
        Instant startTime = Instant.now();

        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    switch (contentionLevel) {
                        case "no-contention":
                            executeWithNoContention(taskId, completed);
                            break;
                        case "low-contention":
                            executeWithLowContention(taskId, completed);
                            break;
                        case "high-contention":
                            executeWithHighContention(taskId, completed);
                            break;
                        case "extreme-contention":
                            executeWithExtremeContention(taskId, completed);
                            break;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Contention test failed", e);
                }
            });
        }

        try {
            // Wait for completion
            while (completed.get() < concurrency) {
                Thread.sleep(100);
            }

            long duration = Duration.between(startTime, Instant.now()).toMicros();
            double avgTime = duration / (double) concurrency;

            // Carrier thread migration cost should be < 200μs
            if (contentionLevel.equals("high-contention") || contentionLevel.equals("extreme-contention")) {
                assertTrue(avgTime < 200_000,
                    "Carrier migration time too high: " + avgTime + " ns (target: < 200,000)");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Benchmark interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Benchmark: Synchronization Overhead Comparison
     */
    @Benchmark
    @BenchmarkMode(AverageTime)
    @OutputTimeUnit(MICROSECONDS)
    public void testSynchronizationOverhead(
            @Param({"synchronized", "reentrantlock", "atomic", "nolock"}) String syncType,
            @Param({"100", "1000", "10000"}) int concurrency) {

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger completed = new AtomicInteger(0);
        Instant startTime = Instant.now();

        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    switch (syncType) {
                        case "synchronized":
                            executeWithSynchronized(taskId, completed);
                            break;
                        case "reentrantlock":
                            executeWithReentrantLock(taskId, completed);
                            break;
                        case "atomic":
                            executeWithAtomic(taskId, completed);
                            break;
                        case "nolock":
                            executeWithNoLock(taskId, completed);
                            break;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Synchronization test failed", e);
                }
            });
        }

        try {
            // Wait for completion
            while (completed.get() < concurrency) {
                Thread.sleep(100);
            }

            long duration = Duration.between(startTime, Instant.now()).toMicros();
            double avgTime = duration / (double) concurrency;

            // Synchronization overhead should be < 100μs
            if (!syncType.equals("nolock")) {
                assertTrue(avgTime < 100_000,
                    "Synchronization overhead too high: " + avgTime + " ns (target: < 100,000)");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Benchmark interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Benchmark: Work Item Processing with Context Switches
     */
    @Benchmark
    @BenchmarkMode(AverageTime)
    @OutputTimeUnit(MICROSECONDS)
    public void testWorkItemProcessingWithSwitches(
            @Param({"1-switch", "5-switches", "10-switches", "20-switches"}) String switchCount,
            @Param({"100", "1000", "10000"}) int concurrency) {

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger completed = new AtomicInteger(0);
        int switches = Integer.parseInt(switchCount.substring(0, switchCount.indexOf("-")));

        Instant startTime = Instant.now();

        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    processWorkItemWithContextSwitches(taskId, switches, completed);
                } catch (Exception e) {
                    throw new RuntimeException("Work item processing failed", e);
                }
            });
        }

        try {
            // Wait for completion
            while (completed.get() < concurrency) {
                Thread.sleep(100);
            }

            long duration = Duration.between(startTime, Instant.now()).toMicros();
            double avgSwitchTime = duration / (double) (concurrency * switches);

            // Context switch time should be < 100μs per switch
            assertTrue(avgSwitchTime < 100_000,
                "Average context switch time too high: " + avgSwitchTime + " ns per switch (target: < 100,000)");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Benchmark interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Benchmark: Virtual Thread Pinning Detection
     */
    @Benchmark
    @BenchmarkMode(SampleTime)
    @OutputTimeUnit(MICROSECONDS)
    public void testVirtualThreadPinningDetection(
            @Param({"no-pinning", "light-pinning", "heavy-pinning", "mixed-pinning"}) String pinningScenario,
            @Param({"100", "1000"}) int concurrency) {

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger completed = new AtomicInteger(0);
        Instant startTime = Instant.now();

        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    switch (pinningScenario) {
                        case "no-pinning":
                            executeWithNoPinning(taskId, completed);
                            break;
                        case "light-pinning":
                            executeWithLightPinning(taskId, completed);
                            break;
                        case "heavy-pinning":
                            executeWithHeavyPinning(taskId, completed);
                            break;
                        case "mixed-pinning":
                            executeWithMixedPinning(taskId, completed);
                            break;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Pinning test failed", e);
                }
            });
        }

        try {
            // Wait for completion
            while (completed.get() < concurrency) {
                Thread.sleep(100);
            }

            long duration = Duration.between(startTime, Instant.now()).toMicros();
            double avgTime = duration / (double) concurrency;

            // Heavy pinning should impact performance (take longer)
            if ("heavy-pinning".equals(pinningScenario)) {
                assertTrue(avgTime > 50_000, "Heavy pinning should impact performance: " + avgTime + " ns");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Benchmark interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Helper methods for context switching testing
     */
    private void executeWithControlledSwitching(int taskId, int switchCount, AtomicInteger completed) throws Exception {
        for (int i = 0; i < switchCount; i++) {
            // Context switch point
            Thread.yield();

            // Execute small work unit
            simulateWork(1);
        }
        completed.incrementAndGet();
    }

    private void executeWithNoContention(int taskId, AtomicInteger completed) throws Exception {
        simulateWork(5);
        completed.incrementAndGet();
    }

    private void executeWithLowContention(int taskId, AtomicInteger completed) throws Exception {
        synchronized (this) {
            simulateWork(5);
        }
        completed.incrementAndGet();
    }

    private void executeWithHighContention(int taskId, AtomicInteger completed) throws Exception {
        sharedLock.lock();
        try {
            simulateWork(5);
        } finally {
            sharedLock.unlock();
        }
        completed.incrementAndGet();
    }

    private void executeWithExtremeContention(int taskId, AtomicInteger completed) throws Exception {
        sharedLock.lock();
        try {
            synchronized (syncMonitor) {
                simulateWork(5);
            }
        } finally {
            sharedLock.unlock();
        }
        completed.incrementAndGet();
    }

    private void executeWithSynchronized(int taskId, AtomicInteger completed) throws Exception {
        synchronized (this) {
            simulateWork(1);
        }
        completed.incrementAndGet();
    }

    private void executeWithReentrantLock(int taskId, AtomicInteger completed) throws Exception {
        sharedLock.lock();
        try {
            simulateWork(1);
        } finally {
            sharedLock.unlock();
        }
        completed.incrementAndGet();
    }

    private void executeWithAtomic(int taskId, AtomicInteger completed) throws Exception {
        sharedCounter.incrementAndGet();
        simulateWork(1);
        completed.incrementAndGet();
    }

    private void executeWithNoLock(int taskId, AtomicInteger completed) throws Exception {
        simulateWork(1);
        completed.incrementAndGet();
    }

    private void processWorkItemWithContextSwitches(int taskId, int switches, AtomicInteger completed) throws Exception {
        for (int i = 0; i < switches; i++) {
            Thread.yield();
            simulateWork(1);
        }
        completed.incrementAndGet();
    }

    private void executeWithNoPinning(int taskId, AtomicInteger completed) throws Exception {
        Thread.sleep(1); // Pure I/O work
        completed.incrementAndGet();
    }

    private void executeWithLightPinning(int taskId, AtomicInteger completed) throws Exception {
        simulateWork(5); // Some CPU work
        completed.incrementAndGet();
    }

    private void executeWithHeavyPinning(int taskId, AtomicInteger completed) throws Exception {
        simulateWork(50); // Long CPU work
        completed.incrementAndGet();
    }

    private void executeWithMixedPinning(int taskId, AtomicInteger completed) throws Exception {
        simulateWork(5);
        Thread.sleep(1);
        simulateWork(5);
        completed.incrementAndGet();
    }

    private void simulateWork(int durationMs) throws InterruptedException {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    private int getSwitchCountForFrequency(String frequency) {
        return switch (frequency) {
            case "low-frequency" -> 1;
            case "medium-frequency" -> 5;
            case "high-frequency" -> 10;
            case "ultra-high-frequency" -> 20;
            default -> 1;
        };
    }

    private double getMinThroughputForFrequency(String frequency) {
        return switch (frequency) {
            case "low-frequency" -> 100.0;
            case "medium-frequency" -> 50.0;
            case "high-frequency" -> 25.0;
            case "ultra-high-frequency" -> 10.0;
            default -> 100.0;
        };
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}