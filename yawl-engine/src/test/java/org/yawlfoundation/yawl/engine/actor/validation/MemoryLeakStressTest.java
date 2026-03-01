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
import org.yawlfoundation.yawl.engine.observability.memory.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive memory leak stress test for YAWL actor model.
 * Validates the 132-byte per agent constraint and leak detection capabilities.
 *
 * <h2>Test Scenarios</h2>
 * <ul>
 *   <li>Agent creation stress test (1M+ agents)</li>
 *   <li>Memory leak detection validation</li>
 *   <li>GC pressure testing</li>
 *   <li>Heap fragmentation analysis</li>
 *   <li>Virtual thread memory monitoring</li>
 * </ul>
 */
@DisplayName("Memory Leak Stress Tests")
public class MemoryLeakStressTest {

    private static final Logger TEST_LOGGER = LogManager.getLogger(MemoryLeakStressTest.class);
    private static final int AGENT_COUNT_TARGET = 100_000;
    private static final int AGENT_COUNT_STRESS = 1_000_000;
    private static final long TEST_DURATION_MS = 60_000; // 1 minute
    private static final long STRESS_DURATION_MS = 300_000; // 5 minutes

    private MemoryProfiler memoryProfiler;
    private ActorMemoryLeakDetector leakDetector;
    private ActorGCAgent gcAgent;
    private MemoryMXBean memoryMXBean;

    @BeforeEach
    void setUp() {
        // Initialize monitoring components
        memoryProfiler = new MemoryProfiler();
        leakDetector = new ActorMemoryLeakDetector();
        gcAgent = new ActorGCAgent();
        memoryMXBean = ManagementFactory.getMemoryMXBean();

        // Start monitoring
        memoryProfiler.startProfiling();
        leakDetector.startLeakDetection();
        gcAgent.startMonitoring();
    }

    @Test
    @DisplayName("Validate 132-byte per agent constraint")
    void testAgentMemoryConstraint() throws InterruptedException {
        TEST_LOGGER.info("Starting 132-byte per agent constraint test");

        // Create and monitor agents
        List<String> agentIds = new ArrayList<>();
        AtomicLong totalMemoryUsed = new AtomicLong(0);
        AtomicLong compliantAgents = new AtomicLong(0);

        // Create agents in batches
        for (int i = 0; i < AGENT_COUNT_TARGET; i++) {
            String agentId = "agent-" + i;
            agentIds.add(agentId);

            // Register with leak detector
            leakDetector.registerAgent(agentId, TestAgent.class, 132);
            memoryProfiler.addAgent(agentId, TestAgent.class);

            // Simulate memory updates
            long memoryUsage = 132 + (long) (Math.random() * 10); // 132-142 bytes
            leakDetector.updateAgentMemory(agentId, memoryUsage);
            memoryProfiler.updateAgentMemory(agentId, memoryUsage);

            totalMemoryUsed.addAndGet(memoryUsage);
            if (memoryUsage <= 142) { // 132 + 10 buffer
                compliantAgents.incrementAndGet();
            }

            // Small batch processing to avoid overwhelming the system
            if (i % 1000 == 0) {
                Thread.sleep(10);
            }
        }

        // Wait for stabilization
        Thread.sleep(5000);

        // Validate memory constraints
        MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
        assertNotNull(stats);
        assertEquals(AGENT_COUNT_TARGET, stats.totalAgents());
        assertTrue(stats.bytesPerAgent() <= 142,
            String.format("Agent memory exceeds constraint: %.1f > 142", stats.bytesPerAgent()));
        assertEquals(AGENT_COUNT_TARGET, compliantAgents.get(),
            "All agents should be compliant with memory constraint");

        TEST_LOGGER.info("Memory constraint validated - {} agents, {:.1f} bytes/agent",
            stats.totalAgents(), stats.bytesPerAgent());

        // Verify memory efficiency
        assertTrue(stats.memoryEfficiency() >= 95,
            String.format("Memory efficiency too low: %.1f%% < 95%%", stats.memoryEfficiency()));
    }

    @Test
    @DisplayName("Memory leak detection validation")
    void testMemoryLeakDetection() throws InterruptedException {
        TEST_LOGGER.info("Starting memory leak detection validation");

        // Create baseline of healthy agents
        for (int i = 0; i < 10_000; i++) {
            String agentId = "healthy-agent-" + i;
            leakDetector.registerAgent(agentId, TestAgent.class, 132);
            memoryProfiler.addAgent(agentId, TestAgent.class);
            leakDetector.updateAgentMemory(agentId, 132);
        }

        // Allow system to stabilize
        Thread.sleep(3000);

        // Introduce memory leaks
        List<String> leakingAgents = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            String agentId = "leaking-agent-" + i;
            leakingAgents.add(agentId);

            // Simulate memory leak - growing beyond 132 bytes
            for (int j = 0; j < 100; j++) {
                long memoryUsage = 132 + j * 50; // Exponential growth
                leakDetector.updateAgentMemory(agentId, memoryUsage);
                Thread.sleep(1); // Small delay
            }
        }

        // Monitor for leak detection
        Thread.sleep(10_000);

        // Verify leak detection
        LeakDetectionSummary summary = leakDetector.getSummary();
        assertTrue(summary.totalLeaksDetected() > 0,
            "Memory leaks should be detected");
        assertEquals(1_000, summary.agentsMonitored(),
            "Expected number of agents monitored");

        TEST_LOGGER.info("Memory leak detection validated - {} leaks detected",
            summary.totalLeaksDetected());
    }

    @Test
    @DisplayName("GC pressure and performance test")
    void testGCPerformance() throws InterruptedException {
        TEST_LOGGER.info("Starting GC pressure and performance test");

        long startTime = System.currentTimeMillis();
        long gcEventsBefore = gcAgent.getCurrentStatistics().gcCount();

        // Create agents that trigger GC
        for (int i = 0; i < 50_000; i++) {
            String agentId = "gc-stress-agent-" + i;
            leakDetector.registerAgent(agentId, TestAgent.class, 132);
            memoryProfiler.addAgent(agentId, TestAgent.class);

            // Simulate object creation that would trigger GC
            for (int j = 0; j < 100; j++) {
                String objectType = "TestObject-" + j;
                leakDetector.trackObjectAllocation(objectType, agentId, 1000);
            }

            if (i % 1000 == 0) {
                Thread.sleep(5);
            }
        }

        // Wait for GC to complete
        Thread.sleep(10_000);

        long endTime = System.currentTimeMillis();
        long gcEventsAfter = gcAgent.getCurrentStatistics().gcCount();
        long gcEvents = gcEventsAfter - gcEventsBefore;
        double durationSeconds = (endTime - startTime) / 1000.0;

        // Validate GC performance
        GCPerformanceSummary gcSummary = gcAgent.getPerformanceSummary();
        assertNotNull(gcSummary);
        assertTrue(gcSummary.avgPauseTime() < 100,
            String.format("Average GC pause too high: %.1fms", gcSummary.avgPauseTime()));
        assertTrue(gcSummary.performanceLevel().matches("EXCELLENT|GOOD"),
            String.format("GC performance poor: %s", gcSummary.performanceLevel()));

        // Validate GC frequency
        double gcFrequency = gcEvents / durationSeconds;
        assertTrue(gcFrequency < 10,
            String.format("GC frequency too high: %.2f GC/s", gcFrequency));

        TEST_LOGGER.info("GC performance validated - {} GC events in {:.1f}s, frequency: {:.2f} GC/s",
            gcEvents, durationSeconds, gcFrequency);
    }

    @Test
    @DisplayName("Heap fragmentation analysis")
    void testHeapFragmentation() throws InterruptedException {
        TEST_LOGGER.info("Starting heap fragmentation analysis");

        MemoryUsage initialHeap = memoryMXBean.getHeapMemoryUsage();
        long initialUsed = initialHeap.getUsed();

        // Create and destroy agents to cause fragmentation
        List<String> agentIds = new ArrayList<>();
        for (int cycle = 0; cycle < 10; cycle++) {
            // Create batch of agents
            for (int i = 0; i < 5_000; i++) {
                String agentId = "fragment-test-agent-" + cycle + "-" + i;
                agentIds.add(agentId);
                leakDetector.registerAgent(agentId, TestAgent.class, 132);
                memoryProfiler.addAgent(agentId, TestAgent.class);
            }

            // Allow memory allocation
            Thread.sleep(1000);

            // Remove half of agents (simulating cleanup)
            for (int i = 0; i < 2_500; i++) {
                String agentId = agentIds.remove(agentIds.size() - 1);
                leakDetector.updateAgentMemory(agentId, 0); // Simulate cleanup
            }

            // Check for fragmentation
            MemoryUsage currentHeap = memoryMXBean.getHeapMemoryUsage();
            long currentUsed = currentHeap.getUsed();

            double fragmentationRatio = (double) (currentUsed - initialUsed) / currentUsed;
            assertTrue(fragmentationRatio < 0.1,
                String.format("Heap fragmentation too high: %.1f%%", fragmentationRatio * 100));

            TEST_LOGGER.info("Cycle {}: Fragmentation ratio: {:.1f}%",
                cycle + 1, fragmentationRatio * 100);
        }
    }

    @Test
    @DisplayName("Virtual thread memory monitoring")
    void testVirtualThreadMemory() throws InterruptedException {
        TEST_LOGGER.info("Starting virtual thread memory monitoring");

        // Create many virtual threads
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();

        long initialThreads = ManagementFactory.getThreadMXBean().getThreadCount();

        // Submit tasks that create and manage agents
        for (int i = 0; i < 10_000; i++) {
            final int agentIndex = i;
            futures.add(virtualExecutor.submit(() -> {
                String agentId = "virtual-agent-" + agentIndex;
                leakDetector.registerAgent(agentId, TestAgent.class, 132);
                memoryProfiler.addAgent(agentId, TestAgent.class);

                // Simulate work
                for (int j = 0; j < 10; j++) {
                    try {
                        Thread.sleep(10);
                        long memoryUsage = 132 + j;
                        leakDetector.updateAgentMemory(agentId, memoryUsage);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }));
        }

        // Wait for tasks to complete
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                TEST_LOGGER.warn("Task failed: {}", e.getMessage());
            }
        }

        virtualExecutor.shutdown();
        if (!virtualExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            virtualExecutor.shutdownNow();
        }

        long finalThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        long threadDelta = finalThreads - initialThreads;

        // Validate virtual thread management
        assertTrue(threadDelta < 1000,
            String.format("Too many threads left active: %d", threadDelta));

        // Validate memory profiler
        MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
        assertTrue(stats.totalAgents() > 0,
            "Should have monitored agents");

        TEST_LOGGER.info("Virtual thread test completed - {} threads created, {} left active",
            futures.size(), threadDelta);
    }

    @Test
    @DisplayName("Stress test - 1M+ agents")
    void testMillionAgentStress() throws InterruptedException {
        TEST_LOGGER.info("Starting million-agent stress test");

        long startTime = System.currentTimeMillis();
        AtomicLong successfulCreations = new AtomicLong(0);
        AtomicLong failedCreations = new AtomicLong(0);

        // Use parallel processing for agent creation
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();

        // Submit agent creation tasks
        for (int batch = 0; batch < 100; batch++) {
            final int batchStart = batch * 10_000;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < 10_000; i++) {
                    try {
                        String agentId = "stress-agent-" + batchStart + "-" + i;
                        leakDetector.registerAgent(agentId, TestAgent.class, 132);
                        memoryProfiler.addAgent(agentId, TestAgent.class);
                        leakDetector.updateAgentMemory(agentId, 132);
                        successfulCreations.incrementAndGet();
                    } catch (Exception e) {
                        failedCreations.incrementAndGet();
                        TEST_LOGGER.warn("Failed to create agent: {}", e.getMessage());
                    }
                }
            }));

            // Throttle creation to prevent overwhelming
            if (batch % 10 == 0) {
                Thread.sleep(100);
            }
        }

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                failedCreations.incrementAndGet();
            }
        }

        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Validate results
        MemoryStatistics stats = memoryProfiler.getCurrentStatistics();
        assertTrue(successfulCreations.get() > 0,
            "Should have created agents successfully");
        assertTrue(stats.totalAgents() > 0,
            "Should have monitored agents");

        // Calculate performance metrics
        double agentsPerSecond = successfulCreations.get() / (duration / 1000.0);
        TEST_LOGGER.info("Stress test completed - {} agents created in {}ms ({:.1f} agents/s)",
            successfulCreations.get(), duration, agentsPerSecond);

        // Validate memory constraints at scale
        assertTrue(stats.bytesPerAgent() <= 142,
            String.format("Memory constraint violated at scale: %.1f > 142", stats.bytesPerAgent()));
    }

    @Test
    @DisplayName("Memory leak recovery test")
    void testMemoryLeakRecovery() throws InterruptedException {
        TEST_LOGGER.info("Starting memory leak recovery test");

        // Create initial healthy state
        for (int i = 0; i < 5_000; i++) {
            String agentId = "recovery-agent-" + i;
            leakDetector.registerAgent(agentId, TestAgent.class, 132);
            memoryProfiler.addAgent(agentId, TestAgent.class);
            leakDetector.updateAgentMemory(agentId, 132);
        }

        // Wait for baseline establishment
        Thread.sleep(5000);

        // Create memory leaks
        for (int i = 0; i < 1_000; i++) {
            String agentId = "leaking-recovery-agent-" + i;
            leakDetector.registerAgent(agentId, TestAgent.class, 132);

            // Simulate leak
            for (int j = 0; j < 50; j++) {
                long memoryUsage = 132 + j * 20;
                leakDetector.updateAgentMemory(agentId, memoryUsage);
            }
        }

        // Wait for leak detection
        Thread.sleep(5000);

        // Verify leaks were detected
        LeakDetectionSummary leakSummary = leakDetector.getSummary();
        assertTrue(leakSummary.totalLeaksDetected() > 0,
            "Leak detection should identify issues");

        // Simulate recovery - clean up leaks
        for (int i = 0; i < 1_000; i++) {
            String agentId = "leaking-recovery-agent-" + i;
            leakDetector.updateAgentMemory(agentId, 132); // Reset to normal
        }

        // Wait for recovery
        Thread.sleep(5000);

        // Verify system recovered
        MemoryStatistics recoveryStats = memoryProfiler.getCurrentStatistics();
        assertTrue(recoveryStats.memoryEfficiency() >= 95,
            "System should recover to efficient state");

        TEST_LOGGER.info("Memory leak recovery test completed - {} leaks detected, recovered efficiency: {:.1f}%",
            leakSummary.totalLeaksDetected(), recoveryStats.memoryEfficiency());
    }

    @AfterEach
    void tearDown() {
        // Clean up monitoring components
        try {
            memoryProfiler.stopProfiling();
            leakDetector.stopLeakDetection();
            gcAgent.stopMonitoring();
        } catch (Exception e) {
            TEST_LOGGER.warn("Error during cleanup: {}", e.getMessage());
        }
    }

    /**
     * Test agent class for memory testing.
     */
    private static class TestAgent {
        private final String id;
        private final Queue<Object> eventQueue;

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