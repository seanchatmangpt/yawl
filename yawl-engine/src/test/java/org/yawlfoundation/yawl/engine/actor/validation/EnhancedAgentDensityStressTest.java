package org.yawlfoundation.yawl.engine.actor.validation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.DoubleStream;

/**
 * Enhanced stress test for YAWL Agent model with comprehensive metrics.
 * Extends the density test with precise measurements and GC monitoring.
 *
 * <p>Targets:
 * - Memory efficiency: ≤150 bytes per agent
 * - Scheduling latency: p90 < 500ms, p95 < 1s, p99 < 2s, p99.9 < 5s
 * - GC pauses: <5% of execution time, <10 full GCs/hour
 *
 * <p>Methodology:
 * 1. Test at increasing densities: 100K, 500K, 1M, 2M, 5M, 10M agents
 * 2. Measure comprehensive metrics at each scale point
 * 3. Track GC pause times separately
 * 4. Stop when any target is violated
 */
@Tag("stress")
@Tag("validation")
class EnhancedAgentDensityStressTest {

    private static final long start = System.currentTimeMillis();

    @Test
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void comprehensiveDensityValidation() throws InterruptedException {
        int[] densityPoints = {
            100_000,    // 100K
            500_000,    // 500K
            1_000_000,  // 1M
            2_000_000,  // 2M
            5_000_000,  // 5M
            10_000_000  // 10M
        };

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        GarbageCollectorMXBean gcBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        System.out.println("=== Enhanced Agent Density Validation ===");
        System.out.println("Scale\tHeap/Agent\tGC Pauses\tCPU Eff\tp90(ms)\tp95(ms)\tp99(ms)\tp99.9(ms)\tStatus");
        System.out.println("-".repeat(130));

        for (int agents : densityPoints) {
            TestResults results = runDensityTest(agents, memoryBean, gcBean, threadBean);
            
            if (results.failureReason != null) {
                System.out.printf("%-15s\t%7.0f\t\t%7d\t\t%.1f%%\t%7.1f\t%7.1f\t%7.1f\t%8.1f\t%s%n",
                    agents / 1000 + "K",
                    results.bytesPerAgent,
                    results.gcPauses,
                    results.cpuEfficiency,
                    results.latencyPercentiles.get(90),
                    results.latencyPercentiles.get(95),
                    results.latencyPercentiles.get(99),
                    results.latencyPercentiles.get(99.9),
                    "FAILED: " + results.failureReason);
                break;
            }

            System.out.printf("%-15s\t%7.0f\t\t%7d\t\t%.1f%%\t%7.1f\t%7.1f\t%7.1f\t%8.1f\tOK%n",
                agents / 1000 + "K",
                results.bytesPerAgent,
                results.gcPauses,
                results.cpuEfficiency,
                results.latencyPercentiles.get(90),
                results.latencyPercentiles.get(95),
                results.latencyPercentiles.get(99),
                results.latencyPercentiles.get(99.9));
        }
    }

    private TestResults runDensityTest(int agentCount, MemoryMXBean memBean, 
                                     GarbageCollectorMXBean gcBean, ThreadMXBean threadBean) 
        throws InterruptedException {
        
        TestResults results = new TestResults();
        Runtime rt = new Runtime();
        
        try {
            // Measure baseline
            gcMeasurementInterval();
            long heapBefore = memBean.getHeapMemoryUsage().getUsed();
            long gcCountBefore = gcBean.getCollectionCount();
            long cpuBefore = threadBean.getThreadCpuTime();

            // Spawn agents
            System.out.println("Spawning " + agentCount + " agents...");
            for (int i = 0; i < agentCount; i++) {
                rt.spawn(msg -> {
                    // Minimal processing to measure real overhead
                    if (msg instanceof String) {
                        msg.toString(); // Force some work
                    }
                });
            }

            // Allow initialization
            Thread.sleep(500);

            // Measure memory after spawning
            System.gc(); // Force GC for accurate measurement
            Thread.sleep(200);
            long heapAfter = memBean.getHeapMemoryUsage().getUsed();
            long gcCountAfter = gcBean.getCollectionCount();
            long cpuAfter = threadBean.getThreadCpuTime();

            // Calculate metrics
            long heapUsed = heapAfter - heapBefore;
            results.bytesPerAgent = (double) heapUsed / agentCount;
            results.gcPauses = (int) (gcCountAfter - gcCountBefore);
            long cpuTimeUsed = cpuAfter - cpuBefore;
            long wallTimeUsed = System.currentTimeMillis() - start;
            results.cpuEfficiency = (double) cpuTimeUsed / (wallTimeUsed * 1_000_000) * 100;

            // Validate memory target
            if (results.bytesPerAgent > 150) {
                results.failureReason = "Memory efficiency: " + 
                    String.format("%.1f bytes/agent > target 150", results.bytesPerAgent);
                return results;
            }

            // Measure scheduling latency with comprehensive percentiles
            results.latencyPercentiles = measureSchedulingLatency(rt, agentCount, memBean, gcBean);

            // Validate latency targets
            if (results.latencyPercentiles.get(99) > 2000) { // p99 > 2s
                results.failureReason = "p99 latency: " + 
                    String.format("%.1fms > target 2000ms", results.latencyPercentiles.get(99));
            } else if (results.latencyPercentiles.get(95) > 1000) { // p95 > 1s
                results.failureReason = "p95 latency: " + 
                    String.format("%.1fms > target 1000ms", results.latencyPercentiles.get(95));
            } else if (results.latencyPercentiles.get(90) > 500) { // p90 > 500ms
                results.failureReason = "p90 latency: " + 
                    String.format("%.1fms > target 500ms", results.latencyPercentiles.get(90));
            }

            return results;

        } catch (OutOfMemoryError e) {
            results.failureReason = "OutOfMemoryError: " + e.getMessage();
            return results;
        } finally {
            rt.close();
        }
    }

    private PercentileMetrics measureSchedulingLatency(Runtime rt, int agentCount, 
                                                     MemoryMXBean memBean, GarbageCollectorMXBean gcBean) 
        throws InterruptedException {
        
        // Use smaller subset for latency measurement to avoid timeout
        int testAgents = Math.min(agentCount, 100_000);
        int pingCount = 10_000; // Number of messages to send

        AtomicLong maxLatency = new AtomicLong(0);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch pings = new CountDownLatch(pingCount);

        // Create test agents
        Runtime pingRuntime = new Runtime();
        try {
            Agent[] testAgentsArray = new Agent[testAgents];
            for (int i = 0; i < testAgents; i++) {
                testAgentsArray[i] = pingRuntime.spawn(msg -> {
                    long now = System.nanoTime();
                    long sendTime = (long) msg;
                    long latency = now - sendTime;
                    latencies.add(latency);
                    maxLatency.updateAndGet(m -> Math.max(m, latency));
                    pings.countDown();
                });
            }

            // Measure GC before latency test
            long gcBefore = gcBean.getCollectionCount();

            // Send pings
            long sendTime = System.nanoTime();
            for (int i = 0; i < pingCount; i++) {
                int agentIndex = i % testAgents; // Round-robin
                testAgentsArray[agentIndex].send(sendTime);
            }
            long sendEnd = System.nanoTime();

            // Wait for completion
            if (!pings.await(30, TimeUnit.SECONDS)) {
                throw new InterruptedException("Latency measurement timeout");
            }

            // Measure GC after latency test
            long gcAfter = gcBean.getCollectionCount();

            // Calculate percentiles
            double[] allLatencies = latencies.stream()
                .mapToLong(l -> l)
                .asDoubleStream()
                .toArray();

            Arrays.sort(allLatencies);
            PercentileMetrics metrics = new PercentileMetrics();
            
            // Calculate percentiles using linear interpolation
            metrics.addPercentile(50, percentile(allLatencies, 0.50));
            metrics.addPercentile(90, percentile(allLatencies, 0.90));
            metrics.addPercentile(95, percentile(allLatencies, 0.95));
            metrics.addPercentile(99, percentile(allLatencies, 0.99));
            metrics.addPercentile(99.9, percentile(allLatencies, 0.999));

            // Additional metrics
            metrics.totalMessages = pingCount;
            metrics.gcPausesDuringTest = (int) (gcAfter - gcBefore);
            metrics.maxLatency = maxLatency.get();
            metrics.meanLatency = DoubleStream.of(allLatencies).average().orElse(0);

            return metrics;

        } finally {
            pingRuntime.close();
        }
    }

    private double percentile(double[] sortedValues, double percentile) {
        if (sortedValues.length == 0) return 0;
        
        double index = percentile * (sortedValues.length - 1);
        int lowerIndex = (int) index;
        int upperIndex = lowerIndex + 1;
        
        if (upperIndex >= sortedValues.length) {
            return sortedValues[sortedValues.length - 1];
        }
        
        double fraction = index - lowerIndex;
        return sortedValues[lowerIndex] + 
               fraction * (sortedValues[upperIndex] - sortedValues[lowerIndex]);
    }

    private void gcMeasurementInterval() throws InterruptedException {
        System.gc();
        Thread.sleep(100);
    }

    // Helper classes
    static class TestResults {
        double bytesPerAgent;
        int gcPauses;
        double cpuEfficiency;
        PercentileMetrics latencyPercentiles;
        String failureReason;
    }

    static class PercentileMetrics {
        private final DoubleSummaryStatistics stats = new DoubleSummaryStatistics();
        private final java.util.Map<Double, Double> percentiles = new java.util.HashMap<>();
        
        int totalMessages;
        int gcPausesDuringTest;
        long maxLatency;
        double meanLatency;

        void addPercentile(double percentile, double value) {
            percentiles.put(percentile, value / 1_000_000); // Convert to ms
        }

        double get(int percentile) {
            return percentiles.getOrDefault((double) percentile, 0.0);
        }
    }
}
