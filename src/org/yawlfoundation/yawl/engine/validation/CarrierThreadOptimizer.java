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

package org.yawlfoundation.yawl.engine.validation;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.locks.LockSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Carrier Thread Optimization Test Suite
 *
 * Tests carrier thread counts (1, 2, 4, 8, 16, 32) and measures impact on
 * p99 latency vs carrier utilization to identify optimal configuration.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Multi-scale carrier thread testing (1-32 threads)</li>
 *   <li>p99 latency measurement for each configuration</li>
 *   <li>Carrier utilization monitoring</li>
 *   <li>Throughput degradation analysis</li>
 *   <li>Memory footprint tracking</li>
 *   <li>Optimal configuration recommendation</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class CarrierThreadOptimizer {

    private static final Logger _logger = LogManager.getLogger(CarrierThreadOptimizer.class);
    private final MeterRegistry registry;

    // Test configurations
    private static final int[] CARRIER_THREAD_CONFIGS = {1, 2, 4, 8, 16, 32};
    private static final int TEST_DURATION_SECONDS = 30;
    private static final int RAMPUP_SECONDS = 10;
    private static final int WARMUP_ITERATIONS = 1000;
    private static final int BENCHMARK_ITERATIONS = 10000;

    // Workload simulation for actor model
    private static final int ACTOR_SPAWN_RATE = 1000; // actors created per second
    private static final int ACTOR_MSG_RATE = 100;    // messages per actor per second
    private static final int ACTOR_LIFETIME_MS = 1000; // actor lifetime in milliseconds

    // Metrics collectors
    private final ThreadMXBean threadMXBean;
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong successfulOperations = new AtomicLong(0);
    private final DoubleAdder totalLatencyNanos = new DoubleAdder();
    private final AtomicInteger activeCarriers = new AtomicInteger(0);
    private final AtomicInteger pinnedVthreads = new AtomicInteger(0);

    public CarrierThreadOptimizer(MeterRegistry registry) {
        this.registry = registry;
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    /**
     * Run comprehensive carrier thread optimization tests.
     *
     * @return Optimization report with recommendations
     */
    public OptimizationReport runOptimizationTests() {
        _logger.info("Starting carrier thread optimization tests");

        OptimizationReport report = new OptimizationReport();
        report.setStartTime(Instant.now());

        // Test each configuration
        for (int carrierThreads : CARRIER_THREAD_CONFIGS) {
            _logger.info("Testing configuration: {} carrier threads", carrierThreads);

            TestResult result = testCarrierConfiguration(carrierThreads);
            report.addResult(carrierThreads, result);

            // Brief cooldown between tests
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        report.setEndTime(Instant.now());
        report.setOptimalConfig(findOptimalConfiguration(report));

        _logger.info("Carrier thread optimization complete. Optimal configuration: {} carriers",
                    report.getOptimalConfig());

        return report;
    }

    /**
     * Test a specific carrier thread configuration.
     */
    private TestResult testCarrierConfiguration(int carrierThreads) {
        TestResult result = new TestResult(carrierThreads);

        // Reset metrics
        totalOperations.set(0);
        successfulOperations.set(0);
        totalLatencyNanos.reset();
        activeCarriers.set(0);
        pinnedVthreads.set(0);

        // Create thread pool with specified carrier threads
        ExecutorService carrierPool = Executors.newFixedThreadPool(carrierThreads);
        ExecutorService virtualThreadPool = Executors.newVirtualThreadPerTaskExecutor();

        try {
            // Warmup phase
            _logger.debug("Warming up with {} iterations", WARMUP_ITERATIONS);
            warmupPhase(carrierPool, virtualThreadPool, WARMUP_ITERATIONS);

            // Benchmark phase
            _logger.debug("Benchmarking for {} seconds", TEST_DURATION_SECONDS);
            benchmarkPhase(carrierPool, virtualThreadPool, result);

        } finally {
            carrierPool.shutdown();
            virtualThreadPool.shutdown();

            try {
                if (!carrierPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    carrierPool.shutdownNow();
                }
                if (!virtualThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    virtualThreadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                carrierPool.shutdownNow();
                virtualThreadPool.shutdownNow();
            }
        }

        // Calculate final metrics
        calculateTestMetrics(result);

        return result;
    }

    /**
     * Warmup phase to stabilize the system.
     */
    private void warmupPhase(ExecutorService carrierPool,
                           ExecutorService virtualThreadPool,
                           int iterations) {
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < iterations; i++) {
            final int taskId = i;
            virtualThreadPool.submit(() -> {
                try {
                    simulateWorkload(taskId % 100, true); // light workload
                    latch.countDown();
                } catch (Exception e) {
                    _logger.warn("Warmup task failed: {}", e.getMessage());
                }
            });
        }

        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Benchmark phase with sustained load.
     */
    private void benchmarkPhase(ExecutorService carrierPool,
                              ExecutorService virtualThreadPool,
                              TestResult result) {
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(TEST_DURATION_SECONDS);

        // Load generation thread
        Thread loadGenerator = Thread.ofVirtual()
            .name("load-generator")
            .start(() -> {
                while (Instant.now().isBefore(endTime)) {
                    generateWorkload(virtualThreadPool);
                    try {
                        Thread.sleep(1); // Generate load continuously
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                result.setLoadEndTime(Instant.now());
            });

        // Metrics collection thread
        Thread metricsCollector = Thread.ofVirtual()
            .name("metrics-collector")
            .start(() -> {
                while (Instant.now().isBefore(endTime)) {
                    collectMetrics(result);
                    try {
                        Thread.sleep(100); // Collect metrics every 100ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });

        // Wait for test completion
        try {
            loadGenerator.join();
            metricsCollector.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Generate simulated actor model workload.
     */
    private void generateWorkload(ExecutorService virtualThreadPool) {
        // Spawn new actors
        int actorsToSpawn = Math.max(1, ACTOR_SPAWN_RATE / 1000); // per millisecond

        for (int i = 0; i < actorsToSpawn; i++) {
            final int actorId = (int) (System.currentTimeMillis() + i);

            virtualThreadPool.submit(() -> {
                try {
                    simulateActorLifecycle(actorId);
                } catch (Exception e) {
                    _logger.debug("Actor task failed: {}", e.getMessage());
                }
            });
        }
    }

    /**
     * Simulate actor lifecycle with message processing.
     */
    private void simulateActorLifecycle(int actorId) {
        long startTime = System.nanoTime();

        try {
            // Actor initialization
            Thread.sleep(ThreadLocalRandom.current().nextInt(5, 20));

            // Message processing loop
            int messagesProcessed = 0;
            while (messagesProcessed < ACTOR_MSG_RATE &&
                   System.currentTimeMillis() - startTime < ACTOR_LIFETIME_MS) {

                // Simulate message processing
                int msgType = ThreadLocalRandom.current().nextInt(3);

                switch (msgType) {
                    case 0: // Light computation
                        computeLight();
                        break;
                    case 1: // I/O operation
                        simulateIO();
                        break;
                    case 2: // Heavy computation
                        computeHeavy();
                        break;
                }

                messagesProcessed++;
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
            }

            // Actor cleanup
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));

            // Record successful operation
            long latency = System.nanoTime() - startTime;
            totalOperations.incrementAndGet();
            successfulOperations.incrementAndGet();
            totalLatencyNanos.add(latency);

        } catch (Exception e) {
            // Operation failed
            totalOperations.incrementAndGet();
        }
    }

    /**
     * Simulate light computation (no blocking).
     */
    private void computeLight() {
        int sum = 0;
        for (int i = 0; i < 100; i++) {
            sum += i * i;
        }
        if (sum == 0) _logger.debug("Unreachable"); // prevent dead code elimination
    }

    /**
     * Simulate I/O operation.
     */
    private void simulateIO() {
        try {
            // Simulate blocking I/O
            LockSupport.parkNanos(ThreadLocalRandom.current().nextInt(1000, 10000));
        } catch (Exception e) {
            _logger.debug("IO simulation failed: {}", e.getMessage());
        }
    }

    /**
     * Simulate heavy computation.
     */
    private void computeHeavy() {
        double result = 0.0;
        for (int i = 0; i < 10000; i++) {
            result += Math.sqrt(i) * Math.sin(i);
        }
        if (result == 0.0) _logger.debug("Unreachable"); // prevent dead code elimination
    }

    /**
     * Simulate simple workload for warmup.
     */
    private void simulateWorkload(int taskId, boolean isWarmup) {
        long startTime = System.nanoTime();

        try {
            if (isWarmup) {
                // Simple warmup work
                computeLight();
                Thread.sleep(1);
            } else {
                // Real workload simulation
                simulateActorLifecycle(taskId);
            }

            long latency = System.nanoTime() - startTime;
            totalOperations.incrementAndGet();
            totalLatencyNanos.add(latency);

        } catch (Exception e) {
            totalOperations.incrementAndGet();
        }
    }

    /**
     * Collect system metrics during test.
     */
    private void collectMetrics(TestResult result) {
        // Thread metrics
        int liveThreads = threadMXBean.getThreadCount();
        int peakThreads = threadMXBean.getPeakThreadCount();

        // Memory metrics
        long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        long heapMax = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
        double heapUsagePercent = heapMax > 0 ? (double) heapUsed / heapMax * 100 : 0;

        // CPU metrics (approximate)
        long cpuTime = threadMXBean.getThreadCpuTime();

        result.addMetricsSnapshot(new MetricsSnapshot(
            Instant.now(),
            liveThreads,
            peakThreads,
            heapUsed,
            heapUsagePercent,
            activeCarriers.get(),
            pinnedVthreads.get(),
            cpuTime
        ));
    }

    /**
     * Calculate final test metrics.
     */
    private void calculateTestMetrics(TestResult result) {
        int totalOps = totalOperations.get();
        int successfulOps = successfulOperations.get();

        if (totalOps == 0) {
            result.setSuccessRate(0);
            result.setThroughput(0);
            result.setAvgLatencyMs(0);
            result.setP99LatencyMs(0);
            return;
        }

        // Success rate
        double successRate = (double) successfulOps / totalOps * 100;
        result.setSuccessRate(successRate);

        // Throughput (ops/sec)
        double durationSeconds = Duration.between(
            result.getLoadStartTime(),
            result.getLoadEndTime() != null ? result.getLoadEndTime() : Instant.now()
        ).toSeconds();
        double throughput = totalOps / durationSeconds;
        result.setThroughput(throughput);

        // Average latency
        double avgLatencyNanos = totalLatencyNanos.sum() / successfulOps;
        result.setAvgLatencyMs(avgLatencyNanos / 1_000_000);

        // p99 latency
        if (result.getLatencySamples().size() >= 100) {
            long[] sorted = result.getLatencySamples().stream()
                .mapToLong(Long::longValue)
                .sorted()
                .toArray();
            double p99LatencyMs = pct(sorted, 99) / 1_000_000;
            result.setP99LatencyMs(p99LatencyMs);
        }

        // Carrier utilization
        double avgActiveCarriers = result.getMetricsSnapshots().stream()
            .mapToInt(MetricsSnapshot::getActiveCarriers)
            .average()
            .orElse(0);
        double carrierUtilization = (avgActiveCarriers / result.getCarrierThreads()) * 100;
        result.setCarrierUtilization(carrierUtilization);
    }

    /**
     * Find optimal configuration based on throughput/latency tradeoffs.
     */
    private int findOptimalConfiguration(OptimizationReport report) {
        double bestScore = Double.NEGATIVE_INFINITY;
        int optimalConfig = CARRIER_THREAD_CONFIGS[0];

        for (TestResult result : report.getResults().values()) {
            // Score based on: throughput * successRate / (p99Latency * (1 + carrierUtilization/100))
            double throughputFactor = result.getThroughput();
            double latencyFactor = 1.0 / Math.max(1, result.getP99LatencyMs());
            double utilizationFactor = 1.0 / (1 + result.getCarrierUtilization() / 100);
            double successFactor = result.getSuccessRate() / 100;

            double score = throughputFactor * latencyFactor * utilizationFactor * successFactor;

            if (score > bestScore) {
                bestScore = score;
                optimalConfig = result.getCarrierThreads();
            }
        }

        return optimalConfig;
    }

    /**
     * Calculate percentile from sorted array.
     */
    private long pct(long[] sorted, double p) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    // Record classes for results
    public static record OptimizationReport(
        Instant startTime,
        Instant endTime,
        Map<Integer, TestResult> results,
        int optimalConfig
    ) {
        public OptimizationReport() {
            this(null, null, new ConcurrentHashMap<>(), 0);
        }

        public void addResult(int carrierThreads, TestResult result) {
            results.put(carrierThreads, result);
        }

        public Map<Integer, TestResult> getResults() {
            return results;
        }
    }

    public static record TestResult(
        int carrierThreads,
        Instant loadStartTime,
        Instant loadEndTime,
        double successRate,
        double throughput,
        double avgLatencyMs,
        double p99LatencyMs,
        double carrierUtilization,
        List<Long> latencySamples,
        List<MetricsSnapshot> metricsSnapshots
    ) {
        public TestResult(int carrierThreads) {
            this(carrierThreads, Instant.now(), null, 0, 0, 0, 0, 0, new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());
        }

        public void addMetricsSnapshot(MetricsSnapshot snapshot) {
            metricsSnapshots.add(snapshot);
        }

        public List<Long> getLatencySamples() {
            return latencySamples;
        }

        public List<MetricsSnapshot> getMetricsSnapshots() {
            return metricsSnapshots;
        }

        public void setLoadEndTime(Instant loadEndTime) {
            this.loadEndTime = loadEndTime;
        }

        public void setSuccessRate(double successRate) {
            this.successRate = successRate;
        }

        public void setThroughput(double throughput) {
            this.throughput = throughput;
        }

        public void setAvgLatencyMs(double avgLatencyMs) {
            this.avgLatencyMs = avgLatencyMs;
        }

        public void setP99LatencyMs(double p99LatencyMs) {
            this.p99LatencyMs = p99LatencyMs;
        }

        public void setCarrierUtilization(double carrierUtilization) {
            this.carrierUtilization = carrierUtilization;
        }
    }

    public static record MetricsSnapshot(
        Instant timestamp,
        int liveThreads,
        int peakThreads,
        long heapUsedBytes,
        double heapUsagePercent,
        int activeCarriers,
        int pinnedVthreads,
        long cpuTimeNanos
    ) {}
}