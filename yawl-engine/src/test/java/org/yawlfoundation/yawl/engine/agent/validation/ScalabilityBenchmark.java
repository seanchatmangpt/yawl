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
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.agent.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

/**
 * Enhanced scalability benchmark for YAWL Actor Model with comprehensive throughput measurement.
 *
 * <p>Enhanced targets:
 * - Spawn throughput: >100K agents/second
 * - Message throughput: >1M messages/second across 1M agents
 * - Message delivery latency: p95 < 1ms, p99 < 5ms
 * - Throughput stability: <5% variation over 1 minute
 * - Memory efficiency: ≤132 bytes per agent
 *
 * <p>Benchmark scenarios:
 * 1. Spawn throughput at various scales (1K, 10K, 100K, 1M agents)
 * 2. Message throughput at linear scaling (1K, 10K, 100K, 1M agents)
 * 3. Message flood testing for extreme load
 * 4. Sustained throughput over time for stability
 * 5. Hotspot load for uneven distribution
 */
@Tag("performance")
@Tag("validation")
@Tag("phase1")
@Tag("scalability")
class ScalabilityBenchmark {

    // Test configuration
    private static final int DURATION_SECONDS = 60;
    private static final int WARMUP_SECONDS = 10;
    private static final int FLOOD_DURATION_SECONDS = 30;

    // Performance targets
    private static final long MIN_SPAWN_THROUGHPUT = 100_000;     // 100K agents/sec
    private static final long MIN_MESSAGE_THROUGHPUT = 1_000_000;  // 1M msg/sec
    private static final double MAX_P95_LATENCY_MS = 1.0;          // 1ms p95 latency
    private static final double MAX_P99_LATENCY_MS = 5.0;          // 5ms p99 latency
    private static final double MAX_THROUGHPUT_VARIATION = 0.05;   // 5% variation
    private static final int MAX_LATENCY_SAMPLES = 100_000;

    // Management beans
    private MemoryMXBean memoryBean;
    private ThreadMXBean threadBean;
    private GarbageCollectorMXBean gcBean;
    private OperatingSystemMXBean osBean;
    private long startTime;

    @BeforeEach
    void setup() {
        memoryBean = ManagementFactory.getMemoryMXBean();
        threadBean = ManagementFactory.getThreadMXBean();
        gcBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
        osBean = ManagementFactory.getOperatingSystemMXBean();
        startTime = System.currentTimeMillis();
    }

    /**
     * Enhanced spawn throughput benchmark with memory tracking.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void enhancedSpawnThroughputBenchmark() {
        System.out.println("=== Enhanced Spawn Throughput Benchmark ===");
        System.out.println("Agents\tTime(s)\tThroughput(/s)\tHeap/Agent\tGC Pauses\tStatus");
        System.out.println("-".repeat(80));

        int[] agentCounts = {1_000, 10_000, 100_000, 1_000_000};
        List<SpawnThroughputResult> results = new ArrayList<>();

        for (int count : agentCounts) {
            SpawnThroughputResult result = measureEnhancedSpawnThroughput(count);
            results.add(result);

            System.out.printf("%-,8d\t%.3f\t%-,12.0f\t%7.0f\t\t%4d\t\t%s%n",
                count,
                result.durationSeconds,
                result.throughput,
                result.bytesPerAgent,
                result.gcPauses,
                result.passed ? "✓" : "✗");

            // Validate against targets
            if (!result.passed) {
                throw new RuntimeException("Spawn throughput test failed: " + result.failureReason);
            }
        }

        // Summary
        System.out.println("\n=== Spawn Throughput Summary ===");
        results.forEach(r ->
            System.out.printf("%,d agents: %,.0f/s (%.1f bytes/agent)%n",
                r.agentCount, r.throughput, r.bytesPerAgent)
        );
    }

    /**
     * Enhanced message throughput benchmark with detailed latency analysis.
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void enhancedMessageThroughputBenchmark() throws InterruptedException {
        System.out.println("=== Enhanced Message Throughput Benchmark ===");
        System.out.println("Agents\tMsg/Agent\tMsg/s\tp50(μs)\tp90(μs)\tp95(ms)\tp99(ms)\tStatus");
        System.out.println("-".repeat(90));

        int[] agentCounts = {1_000, 10_000, 100_000, 1_000_000};
        List<MessageThroughputResult> results = new ArrayList<>();

        for (int agentCount : agentCounts) {
            MessageThroughputResult result = measureEnhancedMessageThroughput(agentCount);
            results.add(result);

            System.out.printf("%-,8d\t%-,9d\t%-,6.0f\t%5.1f\t%4.1f\t%4.2f\t%4.2f\t%s%n",
                agentCount,
                result.messagesPerAgent,
                result.throughput,
                result.p50LatencyMicros,
                result.p90LatencyMicros,
                result.p95LatencyMs,
                result.p99LatencyMs,
                result.passed ? "✓" : "✗");

            // Validate against targets
            if (!result.passed) {
                throw new RuntimeException("Message throughput test failed: " + result.failureReason);
            }
        }

        // Summary
        System.out.println("\n=== Message Throughput Summary ===");
        results.forEach(r ->
            System.out.printf("%,d agents: %,.0f msg/s (p95=%.2fms)%n",
                r.agentCount, r.throughput, r.p95LatencyMs)
        );
    }

    /**
     * Sustained throughput test with stability monitoring.
     */
    @Test
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void sustainedThroughputTest() throws InterruptedException {
        System.out.println("=== Sustained Throughput Test ===");
        System.out.println("Running for " + DURATION_SECONDS + " seconds...");

        int agentCount = 100_000;
        int warmupMessages = 10_000;
        int testMessages = 1_000_000;

        Runtime runtime = new Runtime();
        AtomicInteger messageCount = new AtomicInteger(0);
        LongAdder totalLatency = new LongAdder();
        AtomicLong lastMessageTime = new AtomicLong(0);
        AtomicLong totalThroughput = new AtomicLong(0);

        // Create agents
        Agent[] agents = new Agent[agentCount];
        for (int i = 0; i < agentCount; i++) {
            agents[i] = runtime.spawn(msg -> {
                long latency = System.nanoTime() - (long) msg;
                totalLatency.add(latency);
                messageCount.incrementAndGet();
                lastMessageTime.set(System.nanoTime());
            });
        }

        // Warmup phase
        System.out.println("Warmup phase...");
        for (int i = 0; i < warmupMessages; i++) {
            agents[i % agents.length].send(System.nanoTime());
        }
        Thread.sleep(WARMUP_SECONDS * 1000);

        // Measure sustained throughput
        System.out.println("Test phase...");
        AtomicLong testStart = new AtomicLong(System.nanoTime());
        int windowSize = 5; // 5-second windows
        DoubleAdder[] windowThroughputs = new DoubleAdder[DURATION_SECONDS / windowSize];
        Arrays.fill(windowThroughputs, new DoubleAdder());

        // Send test messages
        for (int i = 0; i < testMessages; i++) {
            agents[i % agents.length].send(System.nanoTime());

            // Record throughput for each window
            long elapsed = (System.nanoTime() - testStart.get()) / 1_000_000_000;
            int windowIndex = (int) (elapsed / windowSize);
            if (windowIndex < windowThroughputs.length) {
                windowThroughputs[windowIndex].add(1);
            }
        }

        // Wait for completion
        Thread.sleep(5_000);
        long testEnd = System.nanoTime();
        long totalMessages = messageCount.get() - warmupMessages;
        double durationSeconds = (testEnd - testStart.get()) / 1_000_000_000.0;

        // Calculate metrics
        double avgThroughput = totalMessages / durationSeconds;
        double avgLatencyMs = totalLatency.sum() / (double) totalMessages / 1_000_000.0;

        // Calculate throughput variation
        double[] windowThroughputValues = new double[windowThroughputs.length];
        for (int i = 0; i < windowThroughputs.length; i++) {
            windowThroughputValues[i] = windowThroughputs[i].sum() / windowSize;
        }

        double mean = Arrays.stream(windowThroughputValues).average().orElse(0);
        double stdDev = Math.sqrt(Arrays.stream(windowThroughputValues)
            .map(x -> Math.pow(x - mean, 2))
            .average().orElse(0));

        double variation = stdDev / mean;

        // Results
        System.out.printf("Sustained: %,.0f msg/s | Avg latency: %.3fms | Variation: %.1f%%%n",
            avgThroughput, avgLatencyMs, variation * 100);

        // Validate stability
        if (variation > MAX_THROUGHPUT_VARIATION) {
            throw new RuntimeException("Throughput too unstable: " +
                String.format("%.1f%% > %.1f%%", variation * 100, MAX_THROUGHPUT_VARIATION * 100));
        }

        // Validate overall throughput
        if (avgThroughput < MIN_MESSAGE_THROUGHPUT) {
            throw new RuntimeException("Sustained throughput too low: " +
                String.format("%,.0f msg/s < %,.0f", avgThroughput, MIN_MESSAGE_THROUGHPUT));
        }

        runtime.close();
    }

    /**
     * Enhanced flood test for extreme load conditions.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void enhancedFloodTest() throws InterruptedException {
        System.out.println("=== Enhanced Flood Test ===");
        System.out.println("Testing extreme message rates...");

        int agentCount = 50_000; // Smaller count for flood test
        int messagesPerAgent = 200; // More messages per agent

        Runtime runtime = new Runtime();
        try {
            Agent[] agents = new Agent[agentCount];
            for (int i = 0; i < agentCount; i++) {
                agents[i] = runtime.spawn(msg -> {
                    // Simple echo back for flood test
                    if (msg instanceof Long) {
                        long latency = System.nanoTime() - (long) msg;
                        // Record in memory for later analysis
                        FloodTestMetrics.globalLatencies.add(latency);
                    }
                });
            }

            // Pre-warm
            for (Agent a : agents) {
                a.send(System.nanoTime());
            }
            Thread.sleep(100);

            // Enhanced flood test with timing
            long floodStart = System.nanoTime();
            AtomicLong deliveredCount = new AtomicLong(0);
            CountDownLatch deliveryLatch = new CountDownLatch(agentCount * messagesPerAgent);

            // Process messages in virtual threads
            for (int i = 0; i < Math.min(100, agentCount); i++) {
                Thread.ofVirtual().start(() -> {
                    while (deliveredCount.get() < agentCount * messagesPerAgent) {
                        for (Agent agent : agents) {
                            Object msg = agent.recv();
                            if (msg instanceof Long) {
                                deliveredCount.incrementAndGet();
                                deliveryLatch.countDown();
                            }
                        }
                    }
                });
            }

            // Send flood
            for (int i = 0; i < agentCount; i++) {
                for (int j = 0; j < messagesPerAgent; j++) {
                    agents[i].send(floodStart + j * 1_000_000); // Timestamped messages
                }
            }

            // Wait for completion
            boolean completed = deliveryLatch.await(2, TimeUnit.MINUTES);
            long floodDuration = System.nanoTime() - floodStart;

            // Calculate metrics
            double totalMessages = agentCount * messagesPerAgent;
            double throughput = totalMessages / (floodDuration / 1_000_000_000.0);

            // Analyze latencies
            long[] latencies = FloodTestMetrics.globalLatencies.stream()
                .mapToLong(l -> l)
                .sorted()
                .toArray();

            double p50Latency = percentile(latencies, 0.50) / 1_000_000.0;
            double p95Latency = percentile(latencies, 0.95) / 1_000_000.0;
            double p99Latency = percentile(latencies, 0.99) / 1_000_000.0;

            System.out.printf("Flood: %,.0f msg/s | Delivered: %,.0f/%,.0f (%.1f%%)%n",
                throughput, deliveredCount.get(), totalMessages,
                deliveredCount.get() * 100.0 / totalMessages);
            System.out.printf("p50: %.3fms | p95: %.3fms | p99: %.3fms | Completed: %b%n",
                p50Latency, p95Latency, p99Latency, completed);

            // Validate targets
            if (!completed) {
                throw new RuntimeException("Flood test incomplete - messages not delivered");
            }

            if (p95Latency > MAX_P95_LATENCY_MS) {
                throw new RuntimeException("High latency in flood test: " +
                    String.format("%.3fms > %.3fms", p95Latency, MAX_P95_LATENCY_MS));
            }

        } finally {
            runtime.close();
            FloodTestMetrics.globalLatencies.clear();
        }
    }

    /**
     * Hotspot load test for uneven distribution.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void hotspotLoadTest() throws InterruptedException {
        System.out.println("=== Hotspot Load Test ===");
        System.out.println("Testing uneven message distribution...");

        int totalAgents = 100_000;
        int hotAgentCount = 1_000; // 1% hot agents
        double hotspotRatio = 0.9; // 90% of messages to hot agents

        Runtime runtime = new Runtime();
        try {
            Agent[] allAgents = new Agent[totalAgents];
            Agent[] hotAgents = new Agent[hotAgentCount];

            // Create all agents
            for (int i = 0; i < totalAgents; i++) {
                allAgents[i] = runtime.spawn(msg -> {
                    long latency = System.nanoTime() - (long) msg;
                    HotspotMetrics.recordLatency(latency);
                });
            }

            // Identify hot agents
            System.arraycopy(allAgents, 0, hotAgents, 0, hotAgentCount);

            // Send hotspot load
            int totalMessages = 100_000;
            AtomicInteger deliveredCount = new AtomicInteger(0);
            CountDownLatch deliveryLatch = new CountDownLatch(totalMessages);

            // Process messages
            for (Agent agent : allAgents) {
                Thread.ofVirtual().start(() -> {
                    while (deliveredCount.get() < totalMessages) {
                        Object msg = agent.recv();
                        if (msg instanceof Long) {
                            deliveredCount.incrementAndGet();
                            deliveryLatch.countDown();
                        }
                    }
                });
            }

            long loadStart = System.nanoTime();
            for (int i = 0; i < totalMessages; i++) {
                if (i % 10 < 9) {
                    // 90% to hot agents
                    hotAgents[i % hotAgentCount].send(loadStart + i);
                } else {
                    // 10% to cold agents
                    allAgents[hotAgentCount + (i % (totalAgents - hotAgentCount))].send(loadStart + i);
                }
            }

            // Wait for completion
            boolean completed = deliveryLatch.await(30, TimeUnit.SECONDS);
            long loadDuration = System.nanoTime() - loadStart;

            // Analyze results
            double throughput = totalMessages / (loadDuration / 1_000_000_000.0);
            HotspotMetrics metrics = HotspotMetrics.getMetrics();

            System.out.printf("Hotspot: %,.0f msg/s | Hot ratio: %.0f%%%n",
                throughput, hotspotRatio * 100);
            System.out.printf("Hot agents p95: %.3fms | Cold agents p95: %.3fms%n",
                metrics.hotP95LatencyMs, metrics.coldP95LatencyMs);

            // Validate hotspot distribution
            if (metrics.hotP95LatencyMs / metrics.coldP95LatencyMs > 10) {
                throw new RuntimeException("Hotspot imbalance too high: " +
                    String.format("%.1fx latency difference",
                        metrics.hotP95LatencyMs / metrics.coldP95LatencyMs));
            }

        } finally {
            runtime.close();
            HotspotMetrics.reset();
        }
    }

    // Helper methods for measurement
    private SpawnThroughputResult measureEnhancedSpawnThroughput(int agentCount) {
        Runtime runtime = new Runtime();
        long start = System.nanoTime();
        long heapBefore = memoryBean.getHeapMemoryUsage().getUsed();
        long cpuBefore = threadBean.getThreadCpuTime();
        long gcBefore = gcBean.getCollectionCount();

        try {
            for (int i = 0; i < agentCount; i++) {
                runtime.spawn(msg -> {});
            }

            long end = System.nanoTime();
            long heapAfter = memoryBean.getHeapMemoryUsage().getUsed();
            long cpuAfter = threadBean.getThreadCpuTime();
            long gcAfter = gcBean.getCollectionCount();

            double durationSeconds = (end - start) / 1_000_000_000.0;
            double throughput = agentCount / durationSeconds;
            long heapUsed = heapAfter - heapBefore;
            double bytesPerAgent = (double) heapUsed / agentCount;
            int gcPauses = (int) (gcAfter - gcBefore);

            SpawnThroughputResult result = new SpawnThroughputResult();
            result.agentCount = agentCount;
            result.throughput = throughput;
            result.durationSeconds = durationSeconds;
            result.bytesPerAgent = bytesPerAgent;
            result.gcPauses = gcPauses;
            result.passed = throughput >= MIN_SPAWN_THROUGHPUT;

            return result;

        } finally {
            runtime.close();
        }
    }

    private MessageThroughputResult measureEnhancedMessageThroughput(int agentCount)
        throws InterruptedException {

        Runtime runtime = new Runtime();
        try {
            Agent[] agents = new Agent[agentCount];
            int messagesPerAgent = Math.min(100, 1_000_000 / agentCount);
            int totalMessages = agentCount * messagesPerAgent;

            for (int i = 0; i < agentCount; i++) {
                agents[i] = runtime.spawn(msg -> {
                    long latency = System.nanoTime() - (long) msg;
                    MessageThroughputMetrics.globalLatencies.add(latency);
                });
            }

            // Send messages
            long sendStart = System.nanoTime();
            for (int i = 0; i < agentCount; i++) {
                for (int j = 0; j < messagesPerAgent; j++) {
                    agents[i].send(sendStart + j);
                }
            }

            // Process messages
            CountDownLatch deliveryLatch = new CountDownLatch(totalMessages);
            AtomicInteger delivered = new AtomicInteger(0);

            for (Agent agent : agents) {
                Thread.ofVirtual().start(() -> {
                    while (delivered.get() < totalMessages) {
                        Object msg = agent.recv();
                        if (msg instanceof Long) {
                            delivered.incrementAndGet();
                            deliveryLatch.countDown();
                        }
                    }
                });
            }

            boolean completed = deliveryLatch.await(30, TimeUnit.SECONDS);
            long sendEnd = System.nanoTime();

            // Calculate metrics
            double durationSeconds = (sendEnd - sendStart) / 1_000_000_000.0;
            double throughput = totalMessages / durationSeconds;

            // Analyze latencies
            long[] latencies = MessageThroughputMetrics.globalLatencies.stream()
                .mapToLong(l -> l)
                .sorted()
                .toArray();

            double p50 = percentile(latencies, 0.50);
            double p90 = percentile(latencies, 0.90);
            double p95 = percentile(latencies, 0.95);
            double p99 = percentile(latencies, 0.99);

            MessageThroughputResult result = new MessageThroughputResult();
            result.agentCount = agentCount;
            result.messagesPerAgent = messagesPerAgent;
            result.throughput = throughput;
            result.p50LatencyMicros = p50 / 1000.0;
            result.p90LatencyMicros = p90 / 1000.0;
            result.p95LatencyMs = p95 / 1_000_000.0;
            result.p99LatencyMs = p99 / 1_000_000.0;
            result.passed = completed && throughput >= MIN_MESSAGE_THROUGHPUT;

            return result;

        } finally {
            runtime.close();
            MessageThroughputMetrics.globalLatencies.clear();
        }
    }

    private long percentile(long[] sortedValues, double percentile) {
        if (sortedValues.length == 0) return 0;

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

    // Result classes
    static class SpawnThroughputResult {
        int agentCount;
        double throughput;
        double durationSeconds;
        double bytesPerAgent;
        int gcPauses;
        boolean passed;
        String failureReason;
    }

    static class MessageThroughputResult {
        int agentCount;
        int messagesPerAgent;
        double throughput;
        double p50LatencyMicros;
        double p90LatencyMicros;
        double p95LatencyMs;
        double p99LatencyMs;
        boolean passed;
        String failureReason;
    }

    // Global metrics classes
    static class FloodTestMetrics {
        static final List<Long> globalLatencies = new CopyOnWriteArrayList<>();
    }

    static class HotspotMetrics {
        static final List<Long> hotLatencies = new CopyOnWriteArrayList<>();
        static final List<Long> coldLatencies = new CopyOnWriteArrayList<>();

        static void recordLatency(long latency) {
            // This would need more sophisticated tracking in real implementation
            hotLatencies.add(latency);
        }

        static HotspotMetrics getMetrics() {
            HotspotMetrics metrics = new HotspotMetrics();

            if (!hotLatencies.isEmpty()) {
                long[] hot = hotLatencies.stream().mapToLong(l -> l).sorted().toArray();
                metrics.hotP95LatencyMs = percentile(hot, 0.95) / 1_000_000.0;
            }

            if (!coldLatencies.isEmpty()) {
                long[] cold = coldLatencies.stream().mapToLong(l -> l).sorted().toArray();
                metrics.coldP95LatencyMs = percentile(cold, 0.95) / 1_000_000.0;
            }

            return metrics;
        }

        static void reset() {
            hotLatencies.clear();
            coldLatencies.clear();
        }

        private static double percentile(long[] sortedValues, double percentile) {
            if (sortedValues.length == 0) return 0;

            double index = percentile * (sortedValues.length - 1);
            int lowerIndex = (int) index;
            int upperIndex = lowerIndex + 1;

            if (upperIndex >= sortedValues.length) {
                return sortedValues[sortedValues.length - 1];
            }

            double fraction = index - lowerIndex;
            return sortedValues[lowerIndex] + fraction * (sortedValues[upperIndex] - sortedValues[lowerIndex]);
        }

        double hotP95LatencyMs;
        double coldP95LatencyMs;
    }

    static class MessageThroughputMetrics {
        static final List<Long> globalLatencies = new CopyOnWriteArrayList<>();
    }
}