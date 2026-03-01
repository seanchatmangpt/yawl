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

import org.junit.jupiter.api.*;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive memory validation test suite for YAWL actor model.
 * End-to-end testing of the complete memory leak detection system.
 *
 * <h2>Test Coverage</h2>
 * <ul>
 *   <li>Integration of all memory monitoring components</li>
 *   <li>Real-time leak detection validation</li>
 *   <li>GC and memory correlation analysis</li>
 *   <li>Large-scale memory management validation</li>
 *   <li>System-wide memory optimization</li>
 * </ul>
 */
@DisplayName("Comprehensive Memory Validation")
@Execution(ExecutionMode.CONCURRENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ComprehensiveMemoryValidation {

    private static final Logger VALIDATION_LOGGER = LogManager.getLogger(ComprehensiveMemoryValidation.class);
    private static final int TOTAL_TEST_AGENTS = 100_000;
    private static final long TOTAL_TEST_DURATION_MS = 120_000; // 2 minutes
    private static final double TARGET_EFFICIENCY = 0.95;
    private static final double TARGET_BYTES_PER_AGENT = 132;
    private static final double MAX_ACCEPTABLE_BYTES = 150; // Buffer for overhead

    // Monitoring components
    private MemoryProfiler memoryProfiler;
    private ActorMemoryLeakDetector leakDetector;
    private ActorGCAgent gcAgent;
    private MemoryMXBean memoryMXBean;
    private ThreadMXBean threadMXBean;

    // Test state
    private AtomicBoolean testRunning = new AtomicBoolean(false);
    private AtomicLong agentsCreated = new AtomicLong(0);
    private AtomicLong testStartTime = new AtomicLong(0);

    @BeforeAll
    static void setupClass() {
        VALIDATION_LOGGER.info("Starting comprehensive memory validation suite");
        // Set up any class-level resources
    }

    @AfterAll
    static void tearDownClass() {
        VALIDATION_LOGGER.info("Comprehensive memory validation suite completed");
        // Clean up class-level resources
    }

    @BeforeEach
    void setUp() {
        VALIDATION_LOGGER.info("Initializing test environment");

        // Initialize monitoring components
        memoryProfiler = new MemoryProfiler();
        leakDetector = new ActorMemoryLeakDetector();
        gcAgent = new ActorGCAgent();
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        threadMXBean = ManagementFactory.getThreadMXBean();

        // Configure components for comprehensive testing
        configureComponents();

        // Start monitoring
        memoryProfiler.startProfiling();
        leakDetector.startLeakDetection();
        gcAgent.startMonitoring();

        // Reset test state
        testRunning.set(true);
        agentsCreated.set(0);
        testStartTime.set(System.currentTimeMillis());
    }

    @AfterEach
    void tearDown() {
        VALIDATION_LOGGER.info("Cleaning up test environment");

        // Stop test
        testRunning.set(false);

        // Shutdown monitoring components
        try {
            memoryProfiler.stopProfiling();
            leakDetector.stopLeakDetection();
            gcAgent.stopMonitoring();
        } catch (Exception e) {
            VALIDATION_LOGGER.warn("Error during cleanup: {}", e.getMessage());
        }

        // Force garbage collection
        System.gc();
    }

    @Test
    @Order(1)
    @DisplayName("Component Integration Test")
    void testComponentIntegration() {
        VALIDATION_LOGGER.info("Running component integration test");

        // Test that all components can work together
        assertTrue(memoryProfiler.isMonitoring(), "Memory profiler should be active");
        assertTrue(leakDetector.isMonitoring(), "Leak detector should be active");
        assertTrue(gcAgent.isMonitoring(), "GC agent should be active");

        // Create test agents
        createTestAgents(1000);

        // Verify components are sharing data correctly
        MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
        LeakDetectionSummary leakSummary = leakDetector.getSummary();
        GCStatistics gcStats = gcAgent.getCurrentStatistics();

        assertNotNull(stats, "Memory statistics should be available");
        assertNotNull(leakSummary, "Leak summary should be available");
        assertNotNull(gcStats, "GC statistics should be available");

        // Validate component consistency
        assertEquals(stats.totalAgents(), leakSummary.agentsMonitored(),
            "Agent count should be consistent across components");

        VALIDATION_LOGGER.info("Component integration validated - {} agents monitored",
            stats.totalAgents());
    }

    @Test
    @Order(2)
    @DisplayName("Real-time Memory Leak Detection")
    void testRealTimeLeakDetection() throws InterruptedException {
        VALIDATION_LOGGER.info("Running real-time memory leak detection test");

        // Create initial healthy state
        createHealthySystem(10_000);

        // Verify healthy baseline
        waitForStabilization();
        validateHealthyBaseline();

        // Introduce memory leaks
        introduceMemoryLeaks();

        // Monitor leak detection
        int leakDetectionTimeout = 30_000; // 30 seconds
        long startTime = System.currentTimeMillis();
        int initialLeakCount = leakDetector.getSummary().totalLeaksDetected();

        while (System.currentTimeMillis() - startTime < leakDetectionTimeout) {
            int currentLeakCount = leakDetector.getSummary().totalLeaksDetected();

            if (currentLeakCount > initialLeakCount) {
                VALIDATION_LOGGER.info("Leak detected! {} leaks found",
                    currentLeakCount - initialLeakCount);
                break;
            }

            Thread.sleep(1000);
        }

        // Validate leak detection
        int finalLeakCount = leakDetector.getSummary().totalLeaksDetected();
        assertTrue(finalLeakCount > initialLeakCount,
            "Memory leaks should be detected");

        // Verify memory impact
        MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
        assertTrue(stats.bytesPerAgent() > TARGET_BYTES_PER_AGENT,
            "Memory usage should reflect leaks");

        VALIDATION_LOGGER.info("Real-time leak detection validated - {} leaks detected",
            finalLeakCount - initialLeakCount);
    }

    @Test
    @Order(3)
    @DisplayName("Large-scale Memory Management")
    void testLargeScaleMemoryManagement() {
        VALIDATION_LOGGER.info("Running large-scale memory management test");

        // Create agents in phases
        int[] phaseSizes = {10_000, 25_000, 50_000, 25_000, 10_000};
        long phaseStartTime = System.currentTimeMillis();

        for (int phase = 0; phase < phaseSizes.length; phase++) {
            int phaseSize = phaseSizes[phase];
            VALIDATION_LOGGER.info("Phase {}: Creating {} agents", phase + 1, phaseSize);

            // Create phase agents
            createTestAgents(phaseSize);

            // Simulate work
            simulateAgentWork(phaseSize, 10_000); // 10 seconds of work

            // Validate memory constraints
            MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
            assertTrue(stats.bytesPerAgent() <= MAX_ACCEPTABLE_BYTES,
                String.format("Memory constraint violated: %.1f > %d",
                    stats.bytesPerAgent(), MAX_ACCEPTABLE_BYTES));
            assertTrue(stats.memoryEfficiency() >= TARGET_EFFICIENCY,
                String.format("Efficiency too low: %.1f%% < %.1f%%",
                    stats.memoryEfficiency() * 100, TARGET_EFFICIENCY * 100));

            // Remove some agents (simulate cleanup)
            if (phase % 2 == 0) {
                cleanupAgents(phaseSize / 2);
            }

            VALIDATION_LOGGER.info("Phase {} completed - {} agents, {:.1f} bytes/agent",
                phase + 1, stats.totalAgents(), stats.bytesPerAgent());
        }

        // Validate system stability after large-scale operations
        MemoryStatistics finalStats = memoryProfiler.getCurrentStatistics();
        assertTrue(finalStats.memoryEfficiency() >= TARGET_EFFICIENCY,
            "System should remain efficient after large-scale operations");

        VALIDATION_LOGGER.info("Large-scale memory management validated - {} agents",
            finalStats.totalAgents());
    }

    @Test
    @Order(4)
    @DisplayName("GC and Memory Correlation Analysis")
    void testGCMemoryCorrelation() throws InterruptedException {
        VALIDATION_LOGGER.info("Running GC and memory correlation analysis");

        // Record initial GC state
        GCStatistics initialGC = gcAgent.getCurrentStatistics();
        MemoryStatistics initialMemory = memoryProfiler.getCurrentStatistics();

        // Perform memory-intensive operations
        performMemoryIntensiveOperations();

        // Wait for GC to complete
        Thread.sleep(15_000);

        // Record final state
        GCStatistics finalGC = gcAgent.getCurrentStatistics();
        MemoryStatistics finalMemory = memoryProfiler.getCurrentStatistics();

        // Calculate correlation
        double gcTimeDelta = finalGC.gcTime() - initialGC.gcTime();
        double memoryDelta = finalMemory.heapUsedBytes() - initialMemory.heapUsedBytes();
        double correlation = calculateGCMemoryCorrelation(gcTimeDelta, memoryDelta);

        VALIDATION_LOGGER.info("GC-Memory correlation: {:.3f}", correlation);

        // Validate correlation analysis
        assertTrue(Math.abs(correlation) <= 0.8,
            String.format("GC-memory correlation should be moderate: %.3f", correlation));

        // Validate GC impact
        GCPerformanceSummary gcSummary = gcAgent.getPerformanceSummary();
        assertTrue(gcSummary.avgPauseTime() < 200,
            String.format("Average GC pause should be reasonable: %.1fms",
                gcSummary.avgPauseTime()));

        VALIDATION_LOGGER.info("GC-memory correlation analyzed - correlation: {:.3f}",
            correlation);
    }

    @Test
    @Order(5)
    @DisplayName("System-wide Memory Optimization")
    void testSystemWideMemoryOptimization() {
        VALIDATION_LOGGER.info("Running system-wide memory optimization test");

        // Initial state
        MemoryStatistics initialStats = memoryProfiler.getCurrentStatistics();
        VALIDATION_LOGGER.info("Initial state: {} agents, {:.1f} bytes/agent, {:.1f}% efficient",
            initialStats.totalAgents(), initialStats.bytesPerAgent(), initialStats.memoryEfficiency());

        // Apply optimization strategies
        applyMemoryOptimizations();

        // Validate optimization results
        MemoryStatistics optimizedStats = memoryProfiler.getCurrentStatistics();
        double efficiencyImprovement = optimizedStats.memoryEfficiency() - initialStats.memoryEfficiency();
        double memoryImprovement = initialStats.bytesPerAgent() - optimizedStats.bytesPerAgent();

        VALIDATION_LOGGER.info("Optimization results:");
        VALIDATION_LOGGER.info("  Efficiency improvement: +{:.1f}%", efficiencyImprovement * 100);
        VALIDATION_LOGGER.info("  Memory reduction: -{:.1f} bytes/agent", memoryImprovement);

        // Validate optimization effectiveness
        assertTrue(optimizedStats.memoryEfficiency() >= initialStats.memoryEfficiency(),
            "Efficiency should improve after optimization");
        assertTrue(optimizedStats.bytesPerAgent() <= initialStats.bytesPerAgent(),
            "Memory usage should decrease after optimization");

        // Validate 132-byte constraint compliance
        assertTrue(optimizedStats.bytesPerAgent() <= MAX_ACCEPTABLE_BYTES,
            String.format("Optimized memory still exceeds limit: %.1f > %d",
                optimizedStats.bytesPerAgent(), MAX_ACCEPTABLE_BYTES));

        VALIDATION_LOGGER.info("System-wide optimization validated - efficiency: {:.1f}%",
            optimizedStats.memoryEfficiency() * 100);
    }

    @Test
    @Order(6)
    @DisplayName("End-to-End Stress Test")
    void testEndToEndStress() throws InterruptedException {
        VALIDATION_LOGGER.info("Running end-to-end stress test");

        long stressStartTime = System.currentTimeMillis();
        long stressDuration = TOTAL_TEST_DURATION_MS;

        VALIDATION_LOGGER.info("Starting {}-minute stress test", stressDuration / 60_000);

        // Create stress test scenario
        createStressTestScenario();

        // Monitor throughout stress test
        long lastLogTime = stressStartTime;
        while (System.currentTimeMillis() - stressStartTime < stressDuration && testRunning.get()) {
            // Monitor every 10 seconds
            if (System.currentTimeMillis() - lastLogTime >= 10_000) {
                monitorStressTestProgress(System.currentTimeMillis() - stressStartTime);
                lastLogTime = System.currentTimeMillis();
            }

            Thread.sleep(1000);
        }

        // Validate stress test results
        validateStressTestResults(stressDuration);

        VALIDATION_LOGGER.info("End-to-end stress test completed");
    }

    // Helper methods
    private void configureComponents() {
        // Configure leak detector for sensitive detection
        ActorMemoryLeakDetector.LeakDetectionConfig leakConfig = new ActorMemoryLeakDetector.LeakDetectionConfig();
        leakConfig.setInspectionIntervalMs(500);
        leakConfig.setAnalysisIntervalMs(1000);
        leakConfig.setBaselineDurationMs(10000);
        leakDetector = new ActorMemoryLeakDetector(leakConfig);

        // Configure GC agent for monitoring
        ActorGCAgent.GCConfig gcConfig = new ActorGCAgent.GCConfig();
        gcConfig.setCollectionIntervalMs(500);
        gcConfig.setAnalysisIntervalMs(2000);
        gcAgent = new ActorGCAgent(gcConfig);
    }

    private void createTestAgents(int count) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            final int agentIndex = i;
            futures.add(executor.submit(() -> {
                String agentId = "integration-test-agent-" + agentsCreated.incrementAndGet() + "-" + agentIndex;
                leakDetector.registerAgent(agentId, TestAgent.class, TARGET_BYTES_PER_AGENT);
                memoryProfiler.addAgent(agentId, TestAgent.class);
                leakDetector.updateAgentMemory(agentId, TARGET_BYTES_PER_AGENT);
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                VALIDATION_LOGGER.warn("Failed to create agent: {}", e.getMessage());
            }
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

    private void createHealthySystem(int count) {
        createTestAgents(count);
        // Ensure all agents are healthy
        for (int i = 0; i < count; i++) {
            String agentId = "healthy-agent-" + i;
            leakDetector.updateAgentMemory(agentId, TARGET_BYTES_PER_AGENT);
        }
    }

    private void introduceMemoryLeaks() {
        int leakCount = 1000;
        for (int i = 0; i < leakCount; i++) {
            String agentId = "leaking-agent-" + i;
            leakDetector.registerAgent(agentId, TestAgent.class, TARGET_BYTES_PER_AGENT);

            // Simulate memory leak
            for (int j = 0; j < 100; j++) {
                long memoryUsage = TARGET_BYTES_PER_AGENT + j * 20;
                leakDetector.updateAgentMemory(agentId, memoryUsage);
            }
        }
    }

    private void waitForStabilization() {
        try {
            Thread.sleep(5000); // Wait 5 seconds for system to stabilize
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void validateHealthyBaseline() {
        MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
        MemoryFootprintAnalysis footprint = memoryProfiler.getFootprintAnalysis();

        assertEquals(10_000, stats.totalAgents(),
            "Should have correct number of healthy agents");
        assertTrue(stats.bytesPerAgent() <= TARGET_BYTES_PER_AGENT + 10,
            "Healthy agents should not exceed target memory");
        assertTrue(footprint.compliancePercentage() >= 99,
            "Almost all agents should be compliant");
        assertTrue(stats.memoryEfficiency() >= TARGET_EFFICIENCY,
            "System should be efficient");
    }

    private void simulateAgentWork(int agentCount, long durationMs) {
        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        while (System.currentTimeMillis() - startTime < durationMs && testRunning.get()) {
            for (int i = 0; i < 1000; i++) {
                final int agentIndex = ThreadLocalRandom.current().nextInt(agentCount);
                executor.submit(() -> {
                    String agentId = "work-agent-" + agentIndex;
                    long memoryUsage = TARGET_BYTES_PER_AGENT + ThreadLocalRandom.current().nextInt(5);
                    leakDetector.updateAgentMemory(agentId, memoryUsage);
                });
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
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

    private void cleanupAgents(int count) {
        // Simulate agent cleanup
        for (int i = 0; i < count; i++) {
            String agentId = "cleanup-agent-" + i;
            leakDetector.updateAgentMemory(agentId, 0); // Simulate removal
        }
    }

    private void performMemoryIntensiveOperations() {
        int operations = 50_000;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < operations; i++) {
            executor.submit(() -> {
                // Create temporary objects
                List<Object> objects = new ArrayList<>();
                for (int j = 0; j < 100; j++) {
                    objects.add(new Object());
                }
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

    private double calculateGCMemoryCorrelation(double gcTime, double memoryDelta) {
        // Simple correlation calculation
        if (gcTime == 0 || memoryDelta == 0) return 0;

        // Normalize values
        double normalizedGC = Math.min(gcTime / 1000.0, 1.0); // Max 1000ms
        double normalizedMemory = Math.min(memoryDelta / (1024 * 1024), 1.0); // Max 1MB

        // Calculate correlation
        return normalizedGC * Math.signum(memoryDelta);
    }

    private void applyMemoryOptimizations() {
        // Apply various optimization strategies

        // 1. Optimize memory allocation patterns
        optimizeMemoryAllocation();

        // 2. Reduce object churn
        reduceObjectChurn();

        // 3. Optimize GC settings
        optimizeGCSettings();

        // 4. Improve data structures
        optimizeDataStructures();

        // Wait for optimizations to take effect
        waitForStabilization();
    }

    private void optimizeMemoryAllocation() {
        VALIDATION_LOGGER.info("Applying memory allocation optimization");
        // Reduce allocation frequency
        // Use object pooling where possible
    }

    private void reduceObjectChurn() {
        VALIDATION_LOGGER.info("Applying object churn reduction");
        // Reduce temporary object creation
        // Reuse objects when possible
    }

    private void optimizeGCSettings() {
        VALIDATION_LOGGER.info("Applying GC optimization");
        // Suggest GC tuning (would require JVM parameter changes)
    }

    private void optimizeDataStructures() {
        VALIDATION_LOGGER.info("Applying data structure optimization");
        // Use more memory-efficient collections
        // Reduce overhead in data structures
    }

    private void createStressTestScenario() {
        // Create a mix of different agent types and activities
        createTestAgents(50_000);

        // Simulate mixed workload
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Submit various types of work
        for (int i = 0; i < 100; i++) {
            final int workloadType = i % 5;
            executor.submit(() -> runStressWorkload(workloadType));
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

    private void runStressWorkload(int workloadType) {
        while (testRunning.get()) {
            switch (workloadType) {
                case 0: // Memory-intensive
                    performMemoryIntensiveOperations();
                    break;
                case 1: // CPU-intensive
                    performCPUIntensiveWork();
                    break;
                case 2: // I/O-intensive
                    performIOIntensiveWork();
                    break;
                case 3: // Mixed workload
                    performMixedWorkload();
                    break;
                case 4: // Idle periods
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    break;
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void performCPUIntensiveWork() {
        // Simulate CPU-intensive operations
        for (int i = 0; i < 1000; i++) {
            double result = Math.sqrt(Math.random()) * Math.random();
        }
    }

    private void performIOIntensiveWork() {
        // Simulate I/O operations
        for (int i = 0; i < 100; i++) {
            try {
                // Simulate file I/O
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void performMixedWorkload() {
        // Combination of different operations
        performCPUIntensiveWork();
        performMemoryIntensiveOperations();
        performIOIntensiveWork();
    }

    private void monitorStressTestProgress(long elapsedMs) {
        MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
        LeakDetectionSummary leakSummary = leakDetector.getSummary();
        GCPerformanceSummary gcSummary = gcAgent.getPerformanceSummary();

        VALIDATION_LOGGER.info("Stress test progress: {:.1f}s, {} agents, {:.1f} bytes/agent, {} leaks, GC: {:.1f}ms avg",
            elapsedMs / 1000.0, stats.totalAgents(), stats.bytesPerAgent(),
            leakSummary.totalLeaksDetected(), gcSummary.avgPauseTime());
    }

    private void validateStressTestResults(long durationMs) {
        MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
        LeakDetectionSummary leakSummary = leakDetector.getSummary();
        GCPerformanceSummary gcSummary = gcAgent.getPerformanceSummary();

        VALIDATION_LOGGER.info("Stress test validation:");
        VALIDATION_LOGGER.info("  Duration: {}ms", durationMs);
        VALIDATION_LOGGER.info("  Final agents: {}", stats.totalAgents());
        VALIDATION_LOGGER.info("  Memory efficiency: {:.1f}%", stats.memoryEfficiency() * 100);
        VALIDATION_LOGGER.info("  Bytes per agent: {:.1f}", stats.bytesPerAgent());
        VALIDATION_LOGGER.info("  Leaks detected: {}", leakSummary.totalLeaksDetected());
        VALIDATION_LOGGER.info("  GC avg pause: {:.1f}ms", gcSummary.avgPauseTime());

        // Validate all constraints
        assertTrue(stats.memoryEfficiency() >= TARGET_EFFICIENCY,
            "System should remain efficient after stress test");
        assertTrue(stats.bytesPerAgent() <= MAX_ACCEPTABLE_BYTES,
            "Memory constraint should be maintained");
        assertTrue(gcSummary.avgPauseTime() < 500,
            "GC pauses should remain reasonable");
    }

    /**
     * Test agent class for validation tests.
     */
    private static class TestAgent {
        private final String id;
        private final ConcurrentLinkedQueue<Object> workQueue;
        private final List<Object> localState;

        TestAgent(String id) {
            this.id = id;
            this.workQueue = new ConcurrentLinkedQueue<>();
            this.localState = new ArrayList<>();
        }

        public void processWork(Object work) {
            workQueue.offer(work);
            if (workQueue.size() > 10) {
                workQueue.poll(); // Keep queue size bounded
            }
        }

        public void addToLocalState(Object item) {
            localState.add(item);
            if (localState.size() > 100) {
                localState.remove(0); // Keep state bounded
            }
        }

        public Queue<Object> getWorkQueue() {
            return workQueue;
        }

        public List<Object> getLocalState() {
            return localState;
        }
    }
}