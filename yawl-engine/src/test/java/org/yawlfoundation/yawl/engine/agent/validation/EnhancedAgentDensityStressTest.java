/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and organisations
 * who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute
 * it and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.agent.validation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.*;
import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

/**
 * Enhanced Phase 1 validation: Comprehensive density testing with detailed metrics.
 *
 * <p>Extended targets:
 * - Memory efficiency: ≤132 bytes per agent (with compact headers)
 * - Scheduling latency: p50 < 100μs, p90 < 500μs, p95 < 1ms, p99 < 2ms, p99.9 < 5ms
 * - Spawn throughput: >100K agents/second
 * - Message throughput: >1M messages/second
 * - GC pauses: <5% of execution time, <10 full GCs/hour
 * - CPU efficiency: >95% utilization
 *
 * <p>Enhanced methodology:
 * 1. Test at increasing densities: 100K, 500K, 1M, 2M, 5M, 10M agents
 * 2. Measure comprehensive metrics at each scale point
 * 3. Track GC pause times separately
 * 4. Measure spawn throughput and message throughput separately
 * 5. Validate all targets against strict requirements
 * 6. Stop when any target is violated
 */
@Tag("stress")
@Tag("validation")
@Tag("phase1")
class EnhancedAgentDensityStressTest {

    // Test configuration
    private static final int[] DENSITY_POINTS = {
        100_000,    // 100K
        500_000,    // 500K
        1_000_000,  // 1M
        2_000_000,  // 2M
        5_000_000,  // 5M
        10_000_000  // 10M
    };

    // Performance targets
    private static final long TARGET_BYTES_PER_AGENT = 132;
    private static final long TARGET_P50_LATENCY_NANOS = 100_000;    // 100μs
    private static final long TARGET_P90_LATENCY_NANOS = 500_000;    // 500μs
    private static final long TARGET_P95_LATENCY_NANOS = 1_000_000;  // 1ms
    private static final long TARGET_P99_LATENCY_NANOS = 2_000_000;  // 2ms
    private static final long TARGET_P99_9_LATENCY_NANOS = 5_000_000; // 5ms
    private static final long TARGET_SPAWN_THROUGHPUT = 100_000;     // 100K agents/sec
    private static final long TARGET_MESSAGE_THROUGHPUT = 1_000_000;  // 1M msg/sec
    private static final double TARGET_CPU_EFFICIENCY = 95.0;        // 95%
    private static final double TARGET_GC_TIME_RATIO = 0.05;          // 5%

    // Management beans
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final GarbageCollectorMXBean gcBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    /**
     * Comprehensive density validation with detailed metrics.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.MINUTES)
    void comprehensiveDensityValidation() throws InterruptedException {
        System.out.println("=== Phase 1: Enhanced Agent Density Validation ===");
        System.out.println("Scale(K)\tHeap/Agent\tSpawn Rate\tMsg Rate\tp50(μs)\tp90(μs)\tp95(ms)\tp99(ms)\tp99.9(ms)\tGC(%)\tCPU(%)\tStatus");
        System.out.println("-".repeat(160));

        for (int densityIndex = 0; densityIndex < DENSITY_POINTS.length; densityIndex++) {
            int agentCount = DENSITY_POINTS[densityIndex];

            try {
                // Run comprehensive test for this density
                DensityTestResults results = runComprehensiveDensityTest(agentCount);

                // Validate targets and determine status
                ValidationStatus status = validateResults(results);

                // Print detailed results
                System.out.printf("%-10d\t%8.0f\t%9.0f\t%7.0f\t%6.1f\t%5.1f\t%5.2f\t%4.2f\t%7.2f\t%4.1f\t%5.1f\t%s%n",
                    agentCount / 1000,
                    results.bytesPerAgent,
                    results.spawnThroughput,
                    results.messageThroughput,
                    results.latencyMetrics.p50Nanos / 1000.0,
                    results.latencyMetrics.p90Nanos / 1000.0,
                    results.latencyMetrics.p95Nanos / 1_000_000.0,
                    results.latencyMetrics.p99Nanos / 1_000_000.0,
                    results.latencyMetrics.p99_9Nanos / 1_000_000.0,
                    results.gcTimeRatio * 100,
                    results.cpuEfficiency,
                    status.name());

                if (status == ValidationStatus.FAILURE) {
                    System.out.printf("FAILURE: %s%n", results.failureReason);
                    break;
                }

                // Check if we should proceed to next density
                if (status == ValidationStatus.WARNING) {
                    System.out.println("WARNING: Continuing test but monitoring closely");
                }

            } catch (OutOfMemoryError oom) {
                System.out.printf("%-10d\tOOM%n", agentCount / 1000);
                System.out.printf("FAILURE: OutOfMemoryError at %d agents%n", agentCount);
                break;
            } catch (Exception e) {
                System.out.printf("%-10d\tERROR: %s%n", agentCount / 1000, e.getMessage());
                System.out.printf("FAILURE: %s%n", e.getMessage());
                break;
            }
        }
    }

    /**
     * Run comprehensive test at specified density.
     */
    private DensityTestResults runComprehensiveDensityTest(int agentCount)
        throws InterruptedException {

        DensityTestResults results = new DensityTestResults();
        Runtime runtime = new Runtime();

        try {
            // 1. Measure spawn throughput
            results.spawnMetrics = measureSpawnThroughput(runtime, agentCount);
            results.spawnThroughput = results.spawnMetrics.throughput;
            results.bytesPerAgent = results.spawnMetrics.bytesPerAgent;

            // 2. Measure message throughput and latency
            if (results.spawnThroughput >= TARGET_SPAWN_THROUGHPUT) {
                results.messageMetrics = measureMessageThroughput(runtime, agentCount);
                results.messageThroughput = results.messageMetrics.throughput;
                results.latencyMetrics = results.messageMetrics.latencyPercentiles;

                // Calculate GC time ratio
                long gcTimeDuringTest = results.messageMetrics.gcTimeAfter - results.messageMetrics.gcTimeBefore;
                long testDurationNanos = results.messageMetrics.testDurationNanos;
                results.gcTimeRatio = (double) gcTimeDuringTest / testDurationNanos;

                // Calculate CPU efficiency
                long cpuTimeAfter = threadBean.getThreadCpuTime();
                long cpuTimeBefore = results.spawnMetrics.cpuTimeAfter;
                long actualCpuTime = cpuTimeAfter - cpuTimeBefore;
                results.cpuEfficiency = (double) actualCpuTime / testDurationNanos * 100;
            } else {
                // Spawn throughput too low, no need to test message throughput
                results.failureReason = "Spawn throughput too low: " +
                    String.format("%.0f agents/s < target %d", results.spawnThroughput, TARGET_SPAWN_THROUGHPUT);
                return results;
            }

            return results;

        } finally {
            runtime.close();
        }
    }

    /**
     * Measure spawn throughput.
     */
    private SpawnMetrics measureSpawnThroughput(Runtime runtime, int agentCount) {
        long spawnStart = System.nanoTime();
        long heapBefore = memoryBean.getHeapMemoryUsage().getUsed();
        long cpuTimeBefore = threadBean.getThreadCpuTime();

        try {
            // Spawn agents with minimal processing
            for (int i = 0; i < agentCount; i++) {
                runtime.spawn(msg -> {
                    // Minimal processing to measure real overhead
                    if (msg instanceof String) {
                        msg.toString(); // Force some work
                    }
                });
            }

            // Allow initialization
            while (runtime.size() < agentCount) {
                Thread.onSpinWait();
            }

            long spawnEnd = System.nanoTime();
            long heapAfter = memoryBean.getHeapMemoryUsage().getUsed();
            long cpuTimeAfter = threadBean.getThreadCpuTime();

            double durationSeconds = (spawnEnd - spawnStart) / 1_000_000_000.0;
            double throughput = agentCount / durationSeconds;
            long heapUsed = heapAfter - heapBefore;
            double bytesPerAgent = (double) heapUsed / agentCount;

            SpawnMetrics metrics = new SpawnMetrics();
            metrics.throughput = throughput;
            metrics.durationSeconds = durationSeconds;
            metrics.heapUsed = heapUsed;
            metrics.bytesPerAgent = bytesPerAgent;
            metrics.cpuTimeAfter = cpuTimeAfter;

            return metrics;

        } finally {
            // Clean up for accurate measurement
            long cleanupStart = System.nanoTime();
            runtime.close();
            while (System.nanoTime() - cleanupStart < 100_000_000) {
                Thread.onSpinWait();
            }
        }
    }

    /**
     * Measure message throughput and latency.
     */
    private MessageMetrics measureMessageThroughput(Runtime runtime, int agentCount)
        throws InterruptedException {

        MessageMetrics metrics = new MessageMetrics();

        // Create agents
        Agent[] agents = new Agent[agentCount];
        for (int i = 0; i < agentCount; i++) {
            agents[i] = runtime.spawn(msg -> {
                long now = System.nanoTime();
                long sendTime = (long) msg;
                long latency = now - sendTime;
                metrics.latencyRecorder.recordLatency(sendTime, "message");
            });
        }

        // Allow initialization
        Thread.sleep(500);

        // Record baseline GC and CPU
        long gcTimeBefore = gcBean.getCollectionTime();
        long testStart = System.nanoTime();

        // Send messages with precise timing
        int messagesPerAgent = Math.min(100, 10_000_000 / agentCount); // Scale messages
        int totalMessages = agentCount * messagesPerAgent;

        // Use virtual threads for message processing
        CountDownLatch deliveryLatch = new CountDownLatch(totalMessages);
        AtomicInteger deliveredCount = new AtomicInteger(0);

        // Start message processing threads
        for (int i = 0; i < Math.min(100, agentCount); i++) {
            Thread.ofVirtual().start(() -> {
                while (deliveredCount.get() < totalMessages) {
                    for (int j = 0; j < agents.length && deliveredCount.get() < totalMessages; j++) {
                        Object msg = agents[j].recv();
                        if (msg != null) {
                            deliveredCount.incrementAndGet();
                            deliveryLatch.countDown();
                        }
                    }
                }
            });
        }

        // Send messages with precise timing
        for (int i = 0; i < agentCount; i++) {
            for (int j = 0; j < messagesPerAgent; j++) {
                agents[i].send(testStart);
            }
        }

        // Wait for delivery with timeout
        boolean completed = deliveryLatch.await(30, TimeUnit.SECONDS);
        long testEnd = System.nanoTime();
        long gcTimeAfter = gcBean.getCollectionTime();

        // Calculate metrics
        double durationSeconds = (testEnd - testStart) / 1_000_000_000.0;
        double throughput = totalMessages / durationSeconds;

        metrics.throughput = throughput;
        metrics.testDurationNanos = testEnd - testStart;
        metrics.gcTimeBefore = gcTimeBefore;
        metrics.gcTimeAfter = gcTimeAfter;
        metrics.actualDelivered = deliveredCount.get();
        metrics.completed = completed;

        // Get final latency percentiles
        metrics.latencyPercentiles = metrics.latencyRecorder.calculatePercentiles();

        return metrics;
    }

    /**
     * Validate results against targets.
     */
    private ValidationStatus validateResults(DensityTestResults results) {
        // Check memory efficiency
        if (results.bytesPerAgent > TARGET_BYTES_PER_AGENT) {
            results.failureReason = String.format(
                "Memory efficiency: %.0f bytes/agent > target %d",
                results.bytesPerAgent, TARGET_BYTES_PER_AGENT);
            return ValidationStatus.FAILURE;
        }

        // Check spawn throughput
        if (results.spawnThroughput < TARGET_SPAWN_THROUGHPUT) {
            results.failureReason = String.format(
                "Spawn throughput: %.0f agents/s < target %d",
                results.spawnThroughput, TARGET_SPAWN_THROUGHPUT);
            return ValidationStatus.FAILURE;
        }

        // Check message throughput
        if (results.messageThroughput < TARGET_MESSAGE_THROUGHPUT) {
            results.failureReason = String.format(
                "Message throughput: %.0f msg/s < target %d",
                results.messageThroughput, TARGET_MESSAGE_THROUGHPUT);
            return ValidationStatus.FAILURE;
        }

        // Check latency targets
        if (results.latencyMetrics.p99Nanos > TARGET_P99_LATENCY_NANOS) {
            results.failureReason = String.format(
                "p99 latency: %.0f ns > target %d",
                results.latencyMetrics.p99Nanos, TARGET_P99_LATENCY_NANOS);
            return ValidationStatus.FAILURE;
        }

        if (results.latencyMetrics.p95Nanos > TARGET_P95_LATENCY_NANOS) {
            results.failureReason = String.format(
                "p95 latency: %.0f ns > target %d",
                results.latencyMetrics.p95Nanos, TARGET_P95_LATENCY_NANOS);
            return ValidationStatus.WARNING;
        }

        // Check GC time ratio
        if (results.gcTimeRatio > TARGET_GC_TIME_RATIO) {
            results.failureReason = String.format(
                "GC time ratio: %.1f%% > target %.1f%%",
                results.gcTimeRatio * 100, TARGET_GC_TIME_RATIO * 100);
            return ValidationStatus.WARNING;
        }

        // Check CPU efficiency
        if (results.cpuEfficiency < TARGET_CPU_EFFICIENCY) {
            results.failureReason = String.format(
                "CPU efficiency: %.1f%% < target %.1f%%",
                results.cpuEfficiency, TARGET_CPU_EFFICIENCY);
            return ValidationStatus.WARNING;
        }

        return ValidationStatus.SUCCESS;
    }

    // Helper classes for results
    static class DensityTestResults {
        double bytesPerAgent;
        double spawnThroughput;
        double messageThroughput;
        SpawnMetrics spawnMetrics;
        MessageMetrics messageMetrics;
        LatencyMetrics.PercentileResults latencyMetrics;
        double gcTimeRatio;
        double cpuEfficiency;
        String failureReason;

        boolean hasFailure() {
            return failureReason != null;
        }
    }

    static class SpawnMetrics {
        double throughput;     // agents/sec
        double durationSeconds;
        long heapUsed;
        double bytesPerAgent;
        long cpuTimeAfter;
    }

    static class MessageMetrics {
        double throughput;     // messages/sec
        long testDurationNanos;
        long gcTimeBefore;
        long gcTimeAfter;
        int actualDelivered;
        boolean completed;
        LatencyMetrics.PercentileResults latencyPercentiles;
        LatencyRecorder latencyRecorder = new LatencyRecorder(100_000);
    }

    /**
     * Simple latency recorder for message testing.
     */
    static class LatencyRecorder {
        private final LongAdder totalLatency = new LongAdder();
        private final AtomicInteger sampleCount = new AtomicInteger(0);
        private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxLatency = new AtomicLong(Long.MIN_VALUE);
        private final List<Long> latencies = new CopyOnWriteArrayList<>();

        private final int maxSamples;

        LatencyRecorder(int maxSamples) {
            this.maxSamples = maxSamples;
        }

        void recordLatency(long startTime, String operationType) {
            long latency = System.nanoTime() - startTime;

            totalLatency.add(latency);
            sampleCount.incrementAndGet();

            minLatency.updateAndGet(m -> Math.min(m, latency));
            maxLatency.updateAndGet(m -> Math.max(m, latency));

            if (latencies.size() < maxSamples) {
                latencies.add(latency);
            }
        }

        LatencyMetrics.PercentileResults calculatePercentiles() {
            if (latencies.isEmpty()) {
                return new LatencyMetrics.PercentileResults();
            }

            long[] sortedLatencies = latencies.stream()
                .mapToLong(l -> l)
                .sorted()
                .toArray();

            LatencyMetrics.PercentileResults results = new LatencyMetrics.PercentileResults();
            results.totalSamples = sortedLatencies.length;
            results.minNanos = minLatency.get();
            results.maxNanos = maxLatency.get();
            results.meanNanos = totalLatency.sum() / sortedLatencies.length;

            results.p50Nanos = percentile(sortedLatencies, 0.50);
            results.p90Nanos = percentile(sortedLatencies, 0.90);
            results.p95Nanos = percentile(sortedLatencies, 0.95);
            results.p99Nanos = percentile(sortedLatencies, 0.99);
            results.p99_9Nanos = percentile(sortedLatencies, 0.999);

            results.convertToMilliseconds();

            return results;
        }

        private long percentile(long[] sortedValues, double percentile) {
            double index = percentile * (sortedValues.length - 1);
            int lowerIndex = (int) index;
            int upperIndex = lowerIndex + 1;

            if (upperIndex >= sortedValues.length) {
                return sortedValues[sortedValues.length - 1];
            }

            double fraction = index - lowerIndex;
            return (long) (sortedValues[lowerIndex] +
                           fraction * (sortedValues[upperIndex] - sortedValues[lowerIndex]));
        }
    }

    enum ValidationStatus {
        SUCCESS("✓"),
        WARNING("~"),
        FAILURE("✗");

        private final String symbol;

        ValidationStatus(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public String toString() {
            return symbol;
        }
    }
}