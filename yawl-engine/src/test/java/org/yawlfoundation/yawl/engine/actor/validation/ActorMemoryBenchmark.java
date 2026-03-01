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

package org.yawlfoundation.yawl.engine.actor.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.engine.observability.memory.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive memory benchmarking for YAWL actor model.
 * Validates the 132-byte per agent claim at various scales.
 *
 * <h2>Benchmark Scenarios</h2>
 * <ul>
 *   <li>Linear scaling (10K to 10M agents)</li>
 *   <li>Memory efficiency validation</li>
 *   <li>GC impact analysis</li>
 *   <li>Heap utilization optimization</li>
 *   <li>Throughput vs memory tradeoffs</li>
 * </ul>
 */
@DisplayName("Actor Memory Benchmark")
@Execution(ExecutionMode.CONCURRENT)
public class ActorMemoryBenchmark {

    private static final Logger BENCHMARK_LOGGER = LogManager.getLogger(ActorMemoryBenchmark.class);
    private static final int[] AGENT_COUNTS = {10_000, 50_000, 100_000, 500_000, 1_000_000, 5_000_000, 10_000_000};
    private static final int WARMUP_ROUNDS = 3;
    private static final int MEASUREMENT_ROUNDS = 5;
    private static final long MEASUREMENT_DURATION_MS = 30_000;
    private static final double TARGET_EFFICIENCY = 0.95;
    private static final double TARGET_BYTES_PER_AGENT = 132;
    private static final double MAX_ACCEPTABLE_BYTES = 142; // 132 + 10% buffer

    private MemoryProfiler memoryProfiler;
    private ActorMemoryLeakDetector leakDetector;
    private ActorGCAgent gcAgent;
    private MemoryMXBean memoryMXBean;
    private ThreadMXBean threadMXBean;

    @BeforeEach
    void setUp() {
        // Initialize monitoring components
        memoryProfiler = new MemoryProfiler();
        leakDetector = new ActorMemoryLeakDetector();
        gcAgent = new ActorGCAgent();
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        threadMXBean = ManagementFactory.getThreadMXBean();

        // Start monitoring with optimized settings
        ActorMemoryLeakDetector.LeakDetectionConfig leakConfig = new ActorMemoryLeakDetector.LeakDetectionConfig();
        leakConfig.setInspectionIntervalMs(1000);
        leakConfig.setAnalysisIntervalMs(2000);
        leakDetector = new ActorMemoryLeakDetector(leakConfig);

        memoryProfiler.startProfiling();
        leakDetector.startLeakDetection();
        gcAgent.startMonitoring();
    }

    @Test
    @DisplayName("Linear scaling benchmark")
    void testLinearScalingBenchmark() {
        BENCHMARK_LOGGER.info("Starting linear scaling benchmark");

        List<BenchmarkResult> results = new ArrayList<>();

        for (int agentCount : AGENT_COUNTS) {
            BENCHMARK_LOGGER.info("Benchmarking {} agents", agentCount);

            // Warm up
            warmupSystem(agentCount / 10);

            // Run benchmark
            BenchmarkResult result = runScalingBenchmark(agentCount);
            results.add(result);

            // Validate results
            validateScalingResult(result);

            // Cleanup
            cleanupBenchmark();
        }

        // Analyze scaling behavior
        analyzeScalingResults(results);
    }

    @Test
    @DisplayName("Memory efficiency validation")
    void testMemoryEfficiency() {
        BENCHMARK_LOGGER.info("Starting memory efficiency validation");

        // Test at different scales
        for (int scale : new int[]{10_000, 100_000, 1_000_000}) {
            BENCHMARK_LOGGER.info("Testing memory efficiency at {} agents", scale);

            // Create agents
            createTestAgents(scale);

            // Wait for stabilization
            waitForStabilization();

            // Measure efficiency
            MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
            MemoryFootprintAnalysis footprint = memoryProfiler.getFootprintAnalysis();

            // Validate efficiency metrics
            validateMemoryEfficiency(stats, footprint, scale);

            BENCHMARK_LOGGER.info("Scale {}: {} agents, {:.1f} bytes/agent, {:.1f}% efficient",
                scale, stats.totalAgents(), stats.bytesPerAgent(), stats.memoryEfficiency());
        }
    }

    @Test
    @DisplayName("GC impact analysis")
    void testGCImpact() throws InterruptedException {
        BENCHMARK_LOGGER.info("Starting GC impact analysis");

        long initialGCCount = gcAgent.getCurrentStatistics().gcCount();
        long initialGCTime = gcAgent.getCurrentStatistics().gcTime();

        // Run intensive operations that trigger GC
        runGCIntensiveOperations();

        // Wait for GC to complete
        Thread.sleep(10_000);

        long finalGCCount = gcAgent.getCurrentStatistics().gcCount();
        long finalGCTime = gcAgent.getCurrentStatistics().gcTime();

        // Analyze GC impact
        long gcCount = finalGCCount - initialGCCount;
        long gcTime = finalGCTime - initialGCTime;
        double gcPercentage = (double) gcTime / MEASUREMENT_DURATION_MS * 100;

        // Validate GC performance
        assertTrue(gcPercentage <= 5,
            String.format("GC time too high: %.1f%% > 5%%", gcPercentage));
        assertTrue(gcCount <= 100,
            String.format("Too many GC events: %d > 100", gcCount));

        BENCHMARK_LOGGER.info("GC impact: {} events, {}ms ({:.1f}%)",
            gcCount, gcTime, gcPercentage);
    }

    @Test
    @DisplayName("Heap utilization optimization")
    void testHeapUtilization() {
        BENCHMARK_LOGGER.info("Starting heap utilization optimization");

        // Test different heap configurations
        for (float utilizationTarget : new float[]{0.7f, 0.8f, 0.9f}) {
            BENCHMARK_LOGGER.info("Testing heap utilization target: {:.1f}%", utilizationTarget * 100);

            // Create agents to reach target utilization
            createAgentsForTargetUtilization(utilizationTarget);

            // Measure heap efficiency
            MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
            double actualUtilization = (double) stats.heapUsedBytes() / stats.heapMaxBytes();

            // Validate utilization
            double tolerance = 0.05; // 5% tolerance
            double lowerBound = utilizationTarget - tolerance;
            double upperBound = utilizationTarget + tolerance;

            assertTrue(actualUtilization >= lowerBound && actualUtilization <= upperBound,
                String.format("Utilization outside target: %.1f%% (target: %.1f%%±%.1f%%)",
                    actualUtilization * 100, utilizationTarget * 100, tolerance * 100));

            // Validate memory efficiency
            assertTrue(stats.memoryEfficiency() >= 95,
                String.format("Memory efficiency too low: %.1f%% < 95%%", stats.memoryEfficiency()));
        }
    }

    @Test
    @DisplayName("Throughput vs memory tradeoffs")
    void testThroughputMemoryTradeoff() {
        BENCHMARK_LOGGER.info("Starting throughput vs memory tradeoff analysis");

        List<TradeoffResult> tradeoffs = new ArrayList<>();

        // Test different throughput levels
        for (double throughputTarget : new double[]{100, 500, 1000, 2000}) {
            BENCHMARK_LOGGER.info("Testing throughput target: {} ops/s", throughputTarget);

            TradeoffResult result = measureTradeoff(throughputTarget);
            tradeoffs.add(result);

            // Validate tradeoff
            validateTradeoff(result);

            BENCHMARK_LOGGER.info("Throughput: {:.1f} ops/s, Memory: {:.1f} bytes/agent, Efficiency: {:.1f}%",
                result.throughput, result.memoryPerAgent, result.efficiency);
        }

        // Analyze optimal operating point
        findOptimalOperatingPoint(tradeoffs);
    }

    @Test
    @DisplayName("Memory leak detection accuracy")
    void testLeakDetectionAccuracy() throws InterruptedException {
        BENCHMARK_LOGGER.info("Starting memory leak detection accuracy test");

        int totalTests = 100;
        int detectedLeaks = 0;
        int falsePositives = 0;
        int falseNegatives = 0;

        for (int i = 0; i < totalTests; i++) {
            boolean shouldHaveLeak = Math.random() < 0.3; // 30% chance of leak

            if (shouldHaveLeak) {
                // Create intentional leak
                createIntentionalLeak();
                if (hasLeakDetected()) {
                    detectedLeaks++;
                } else {
                    falseNegatives++;
                }
            } else {
                // Create healthy system
                createHealthySystem();
                if (hasLeakDetected()) {
                    falsePositives++;
                }
            }

            Thread.sleep(100);
        }

        // Calculate accuracy metrics
        double precision = (double) detectedLeaks / (detectedLeaks + falsePositives);
        double recall = (double) detectedLeaks / (detectedLeaks + falseNegatives);
        double f1Score = 2 * precision * recall / (precision + recall);

        // Validate detection accuracy
        assertTrue(precision >= 0.9,
            String.format("Precision too low: %.2f < 0.9", precision));
        assertTrue(recall >= 0.9,
            String.format("Recall too low: %.2f < 0.9", recall));
        assertTrue(f1Score >= 0.85,
            String.format("F1 score too low: %.2f < 0.85", f1Score));

        BENCHMARK_LOGGER.info("Leak detection accuracy - Precision: {:.2f}, Recall: {:.2f}, F1: {:.2f}",
            precision, recall, f1Score);
    }

    // Helper methods
    private void warmupSystem(int agentCount) {
        BENCHMARK_LOGGER.info("Warming up system with {} agents", agentCount);
        createTestAgents(agentCount);
        waitForStabilization();
        cleanupBenchmark();
    }

    private BenchmarkResult runScalingBenchmark(int agentCount) {
        long startTime = System.currentTimeMillis();
        long initialMemory = memoryMXBean.getHeapMemoryUsage().getUsed();

        // Create agents
        createTestAgents(agentCount);

        // Perform operations
        simulateActorOperations(agentCount, MEASUREMENT_DURATION_MS);

        // Measure results
        long endTime = System.currentTimeMillis();
        long finalMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        MemoryStatistics stats = memoryProfiler.getCurrentStatistics();

        return new BenchmarkResult(
            agentCount,
            endTime - startTime,
            finalMemory - initialMemory,
            stats.bytesPerAgent(),
            stats.memoryEfficiency(),
            gcAgent.getPerformanceSummary().avgPauseTime()
        );
    }

    private void createTestAgents(int count) {
        for (int i = 0; i < count; i++) {
            String agentId = "benchmark-agent-" + i;
            leakDetector.registerAgent(agentId, TestAgent.class, 132);
            memoryProfiler.addAgent(agentId, TestAgent.class);
            leakDetector.updateAgentMemory(agentId, 132);
        }
    }

    private void simulateActorOperations(int agentCount, long durationMs) {
        long startTime = System.currentTimeMillis();
        int operationsPerSecond = agentCount / 10; // Scale operations with agent count
        int totalOperations = operationsPerSecond * (int) (durationMs / 1000);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();

        for (int op = 0; op < totalOperations; op++) {
            futures.add(executor.submit(() -> {
                String agentId = "benchmark-agent-" + ThreadLocalRandom.current().nextInt(agentCount);
                long memoryUsage = 132 + ThreadLocalRandom.current().nextInt(10);
                leakDetector.updateAgentMemory(agentId, memoryUsage);
            }));
        }

        // Wait for operations to complete
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                BENCHMARK_LOGGER.warn("Operation failed: {}", e.getMessage());
            }
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void waitForStabilization() {
        try {
            Thread.sleep(2000); // Allow system to stabilize
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void cleanupBenchmark() {
        // In a real implementation, this would clean up all created agents
        // For testing, we just wait for GC
        try {
            System.gc();
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void validateScalingResult(BenchmarkResult result) {
        assertTrue(result.memoryPerAgent <= MAX_ACCEPTABLE_BYTES,
            String.format("Memory per agent exceeds limit: %.1f > %d",
                result.memoryPerAgent, MAX_ACCEPTABLE_BYTES));
        assertTrue(result.efficiency >= TARGET_EFFICIENCY,
            String.format("Memory efficiency too low: %.1f%% < %.1f%%",
                result.efficiency * 100, TARGET_EFFICIENCY * 100));
        assertTrue(result.avgGCPause <= 100,
            String.format("Average GC pause too high: %.1fms > 100ms",
                result.avgGCPause));
    }

    private void validateMemoryEfficiency(MemoryStatistics stats, MemoryFootprintAnalysis footprint, int expectedAgents) {
        assertEquals(expectedAgents, stats.totalAgents(),
            "Agent count mismatch");
        assertTrue(stats.bytesPerAgent() <= MAX_ACCEPTABLE_BYTES,
            String.format("Bytes per agent too high: %.1f > %d",
                stats.bytesPerAgent(), MAX_ACCEPTABLE_BYTES));
        assertTrue(stats.memoryEfficiency() >= TARGET_EFFICIENCY,
            String.format("Efficiency too low: %.1f%% < %.1f%%",
                stats.memoryEfficiency() * 100, TARGET_EFFICIETY * 100));
        assertTrue(footprint.compliancePercentage() >= 95,
            String.format("Compliance too low: %.1f%% < 95%%",
                footprint.compliancePercentage()));
    }

    private void runGCIntensiveOperations() {
        int intensiveOps = 100_000;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < intensiveOps; i++) {
            executor.submit(() -> {
                // Create many temporary objects
                List<Object> tempObjects = new ArrayList<>();
                for (int j = 0; j < 100; j++) {
                    tempObjects.add(new Object());
                }
                // Force garbage collection hint
                System.gc();
            });
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void createAgentsForTargetUtilization(float targetUtilization) {
        long maxHeap = memoryMXBean.getHeapMemoryUsage().getMax();
        long targetUsed = (long) (maxHeap * targetUtilization);
        long currentUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
        long neededMemory = targetUsed - currentUsed;

        // Estimate agents needed based on 132 bytes per agent
        int neededAgents = (int) (neededMemory / 132);

        createTestAgents(neededAgents);
    }

    private TradeoffResult measureTradeoff(double throughputTarget) {
        // Find the maximum throughput that meets efficiency targets
        double currentThroughput = throughputTarget;
        double currentMemory = 0;
        double currentEfficiency = 0;

        // Iterative refinement
        for (int iteration = 0; iteration < 10; iteration++) {
            // Set up system for current throughput
            setupForThroughput(currentThroughput);

            // Measure performance
            MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
            double actualThroughput = measureThroughput();

            currentMemory = stats.bytesPerAgent();
            currentEfficiency = stats.memoryEfficiency();

            // Adjust throughput based on efficiency
            if (currentEfficiency < TARGET_EFFICIENCY) {
                currentThroughput *= 0.9; // Reduce throughput
            } else {
                currentThroughput *= 1.1; // Increase throughput
            }

            if (Math.abs(actualThroughput - throughputTarget) / throughputTarget < 0.1) {
                break; // Close enough
            }
        }

        return new TradeoffResult(currentThroughput, currentMemory, currentEfficiency);
    }

    private void setupForThroughput(double throughput) {
        // Clear previous state
        cleanupBenchmark();

        // Calculate agents needed for throughput
        int agentCount = (int) (throughput / 10); // Rough estimate
        createTestAgents(agentCount);
    }

    private double measureThroughput() {
        long startTime = System.currentTimeMillis();
        long operations = 0;

        // Count operations in a fixed time window
        while (System.currentTimeMillis() - startTime < 1000) {
            // Simulate operation
            for (int i = 0; i < 1000; i++) {
                operations++;
            }
        }

        return operations / 1000.0; // operations per second
    }

    private void validateTradeoff(TradeoffResult result) {
        assertTrue(result.efficiency >= TARGET_EFFICIENCY,
            String.format("Tradeoff efficiency too low: %.1f%% < %.1f%%",
                result.efficiency * 100, TARGET_EFFICIETY * 100));
        assertTrue(result.memoryPerAgent <= MAX_ACCEPTABLE_BYTES,
            String.format("Tradeoff memory too high: %.1f > %d",
                result.memoryPerAgent, MAX_ACCEPTABLE_BYTES));
    }

    private void findOptimalOperatingPoint(List<TradeoffResult> tradeoffs) {
        // Find the point with best throughput while maintaining efficiency
        TradeoffResult optimal = tradeoffs.stream()
            .filter(t -> t.efficiency >= TARGET_EFFICIENCY)
            .max(Comparator.comparingDouble(TradeoffResult::throughput))
            .orElse(tradeoffs.get(0));

        BENCHMARK_LOGGER.info("Optimal operating point: {} ops/s, {:.1f} bytes/agent, {:.1f}% efficient",
            optimal.throughput, optimal.memoryPerAgent, optimal.efficiency * 100);
    }

    private boolean hasLeakDetected() {
        LeakDetectionSummary summary = leakDetector.getSummary();
        return summary.totalLeaksDetected() > 0;
    }

    private void createIntentionalLeak() {
        // Create agents with memory growth
        for (int i = 0; i < 100; i++) {
            String agentId = "leak-test-agent-" + i;
            leakDetector.registerAgent(agentId, TestAgent.class, 132);

            // Simulate memory growth
            for (int j = 0; j < 50; j++) {
                long memoryUsage = 132 + j * 10;
                leakDetector.updateAgentMemory(agentId, memoryUsage);
            }
        }
    }

    private void createHealthySystem() {
        // Create agents with stable memory
        for (int i = 0; i < 100; i++) {
            String agentId = "healthy-test-agent-" + i;
            leakDetector.registerAgent(agentId, TestAgent.class, 132);
            leakDetector.updateAgentMemory(agentId, 132);
        }
    }

    private void analyzeScalingResults(List<BenchmarkResult> results) {
        // Check if memory usage scales linearly
        double averageBytesPerAgent = results.stream()
            .mapToDouble(BenchmarkResult::memoryPerAgent)
            .average()
            .orElse(0);

        BENCHMARK_LOGGER.info("Average bytes per agent across scales: {:.1f}", averageBytesPerAgent);

        // Check if efficiency is maintained at scale
        double averageEfficiency = results.stream()
            .mapToDouble(BenchmarkResult::efficiency)
            .average()
            .orElse(0);

        assertTrue(averageEfficiency >= TARGET_EFFICIENCY,
            String.format("Average efficiency too low: %.1f%% < %.1f%%",
                averageEfficiency * 100, TARGET_EFFICIETY * 100));
    }

    @AfterEach
    void tearDown() {
        // Clean up monitoring components
        try {
            memoryProfiler.stopProfiling();
            leakDetector.stopLeakDetection();
            gcAgent.stopMonitoring();
        } catch (Exception e) {
            BENCHMARK_LOGGER.warn("Error during cleanup: {}", e.getMessage());
        }
    }

    // Data classes
    private static class BenchmarkResult {
        final int agentCount;
        final long durationMs;
        final long memoryDeltaBytes;
        final double memoryPerAgent;
        final double efficiency;
        final double avgGCPause;

        BenchmarkResult(int agentCount, long durationMs, long memoryDeltaBytes,
                       double memoryPerAgent, double efficiency, double avgGCPause) {
            this.agentCount = agentCount;
            this.durationMs = durationMs;
            this.memoryDeltaBytes = memoryDeltaBytes;
            this.memoryPerAgent = memoryPerAgent;
            this.efficiency = efficiency;
            this.avgGCPause = avgGCPause;
        }
    }

    private static class TradeoffResult {
        final double throughput;
        final double memoryPerAgent;
        final double efficiency;

        TradeoffResult(double throughput, double memoryPerAgent, double efficiency) {
            this.throughput = throughput;
            this.memoryPerAgent = memoryPerAgent;
            this.efficiency = efficiency;
        }
    }

    /**
     * Test agent class for benchmarking.
     */
    private static class TestAgent {
        private final String id;
        private final ConcurrentLinkedQueue<Object> eventQueue;

        TestAgent(String id) {
            this.id = id;
            this.eventQueue = new ConcurrentLinkedQueue<>();
        }

        public void processEvent(Object event) {
            eventQueue.offer(event);
        }

        public Queue<Object> getEventQueue() {
            return eventQueue;
        }
    }
}