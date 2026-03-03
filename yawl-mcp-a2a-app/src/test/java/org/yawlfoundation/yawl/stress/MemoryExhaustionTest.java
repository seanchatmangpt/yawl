/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test for actor memory exhaustion scenarios.
 *
 * <p>This test determines the maximum number of concurrent actors that can be
 * spawned before approaching the OutOfMemoryError condition. It uses
 * MemoryMXBean to monitor heap usage and gracefully terminates actors before
 * actual OOM occurs.</p>
 *
 * <h2>Test Scenarios</h2>
 * <ul>
 *   <li>Default heap size testing</li>
 *   <li>Custom heap threshold testing</li>
 *   <li>Virtual thread vs platform thread comparison</li>
 *   <li>Memory leak detection</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class MemoryExhaustionTest {

    private static final Logger logger = LoggerFactory.getLogger(MemoryExhaustionTest.class);

    // Memory monitoring constants
    private static final long MB = 1024 * 1024;
    private static final double WARNING_HEAP_PERCENTAGE = 0.85; // 85% heap usage warning
    private static final double CRITICAL_HEAP_PERCENTAGE = 0.95; // 95% heap usage critical
    private static final long ACTOR_MEMORY_SIZE = 10 * MB; // Estimated memory per actor

    // MemoryMXBean
    private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private static final MemoryPoolMXBean heapPool = ManagementFactory.getMemoryPoolMXBeans()
            .stream()
            .filter(pool -> pool.getName().equals("PS Old Gen") || pool.getName().equals("Heap"))
            .findFirst()
            .orElseThrow();

    // Test agent that simulates memory usage
    private static final TestMemoryAgent testAgent = new TestMemoryAgent();

    /**
     * Simple test agent that consumes memory to simulate real actors.
     */
    private static class TestMemoryAgent implements AutoCloseable {
        private final byte[] memoryBuffer;
        private final String agentId;
        private final AtomicInteger taskCount = new AtomicInteger(0);

        public TestMemoryAgent() {
            this(ACTOR_MEMORY_SIZE);
        }

        public TestMemoryAgent(long memorySize) {
            this.agentId = "agent-" + Thread.currentThread().threadId() + "-" + System.currentTimeMillis();
            this.memoryBuffer = new byte[(int) Math.min(memorySize, 50 * MB)]; // Cap at 50MB per agent
            logger.debug("Created {} with {} MB buffer", agentId, memoryBuffer.length / MB);
        }

        public void executeTask() {
            // Simulate some work
            taskCount.incrementAndGet();
            // Fill some memory buffer with data
            for (int i = 0; i < memoryBuffer.length; i += 1024) {
                memoryBuffer[i] = (byte) (i % 256);
            }

            // Small delay to simulate processing
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public String getAgentId() {
            return agentId;
        }

        public int getTaskCount() {
            return taskCount.get();
        }

        @Override
        public void close() {
            // Clear memory buffer on close
            Arrays.fill(memoryBuffer, (byte) 0);
        }
    }

    /**
     * Helper class to spawn actors with controlled memory monitoring.
     */
    private static class ActorSpawner {
        private final ExecutorService executor;
        private final List<TestMemoryAgent> activeAgents = new CopyOnWriteArrayList<>();
        private final AtomicLong totalMemoryUsed = new AtomicLong(0);
        private final AtomicInteger maxConcurrentActors = new AtomicInteger(0);
        private final AtomicBoolean shouldStop = new AtomicBoolean(false);

        public ActorSpawner(boolean useVirtualThreads) {
            this.executor = useVirtualThreads ?
                Executors.newVirtualThreadPerTaskExecutor() :
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        }

        public int spawnActors(int targetCount, long memoryThreshold) {
            CountDownLatch spawnLatch = new CountDownLatch(targetCount);
            AtomicInteger spawnedCount = new AtomicInteger(0);

            for (int i = 0; i < targetCount && !shouldStop.get(); i++) {
                final int agentIndex = i;
                executor.submit(() -> {
                    if (shouldStop.get()) {
                        spawnLatch.countDown();
                        return;
                    }

                    try {
                        // Create agent and track memory
                        TestMemoryAgent agent = new TestMemoryAgent(ACTOR_MEMORY_SIZE);
                        activeAgents.add(agent);
                        totalMemoryUsed.addAndGet(agent.memoryBuffer.length);

                        // Update max concurrent actors
                        int currentConcurrent = activeAgents.size();
                        int previousMax = maxConcurrentActors.get();
                        if (currentConcurrent > previousMax) {
                            maxConcurrentActors.set(currentConcurrent);
                        }

                        spawnedCount.incrementAndGet();
                        logger.debug("Spawned agent {}/{}: {}", spawnedCount.get(), targetCount, agent.getAgentId());

                        // Execute tasks until memory threshold reached
                        while (!shouldStop.get() && totalMemoryUsed.get() < memoryThreshold) {
                            agent.executeTask();

                            // Check memory periodically
                            if (getHeapUsagePercentage() > CRITICAL_HEAP_PERCENTAGE) {
                                logger.warn("Critical memory threshold reached, stopping actor spawning");
                                shouldStop.set(true);
                                break;
                            }

                            // Small sleep between tasks
                            Thread.sleep(5);
                        }

                    } catch (Exception e) {
                        logger.error("Error in agent {}: {}", agentIndex, e.getMessage());
                        shouldStop.set(true);
                    } finally {
                        spawnLatch.countDown();
                    }
                });

                // Small delay between spawns to allow memory monitoring
                if (i % 10 == 0) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            try {
                spawnLatch.await(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                shouldStop.set(true);
            }

            return spawnedCount.get();
        }

        public void shutdown() {
            shouldStop.set(true);
            activeAgents.forEach(agent -> {
                try {
                    agent.close();
                } catch (Exception e) {
                    logger.error("Error closing agent: {}", e.getMessage());
                }
            });
            activeAgents.clear();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        public int getMaxConcurrentActors() {
            return maxConcurrentActors.get();
        }

        public long getTotalMemoryUsed() {
            return totalMemoryUsed.get();
        }

        public int getActiveAgentCount() {
            return activeAgents.size();
        }
    }

    /**
     * Calculates current heap usage percentage.
     */
    private double getHeapUsagePercentage() {
        MemoryUsage heapUsage = heapPool.getUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        if (max == -1) { // -1 means max is unbounded
            max = heapUsage.getCommitted() * 2; // Estimate max as 2x committed
        }
        return (double) used / max;
    }

    /**
     * Gets current heap information.
     */
    private String getHeapInfo() {
        MemoryUsage heapUsage = heapPool.getUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        return String.format(
            "Heap: %.1f MB / %.1f MB (%.1f%%), NonHeap: %.1f MB / %.1f MB",
            heapUsage.getUsed() / (double) MB,
            heapUsage.getMax() / (double) MB,
            getHeapUsagePercentage() * 100,
            nonHeapUsage.getUsed() / (double) MB,
            nonHeapUsage.getMax() / (double) MB
        );
    }

    /**
     * Tests default heap size with virtual threads.
     */
    @Test
    @DisplayName("Test maximum actors with virtual threads and default heap")
    void testVirtualThreadsWithDefaultHeap() {
        long memoryThreshold = (long) (heapPool.getUsage().getMax() * WARNING_HEAP_PERCENTAGE);
        ActorSpawner spawner = new ActorSpawner(true);

        try {
            int targetActors = 100;
            int spawnedCount = spawner.spawnActors(targetActors, memoryThreshold);

            logger.info("Spawned {} actors with virtual threads", spawnedCount);
            logger.info("Max concurrent actors: {}", spawner.getMaxConcurrentActors());
            logger.info("Total memory used: {} MB", spawner.getTotalMemoryUsed() / MB);
            logger.info("Heap info: {}", getHeapInfo());

            // Verify that we successfully spawned actors and memory was managed
            assertTrue(spawnedCount > 0, "Should have spawned at least one actor");
            assertTrue(spawner.getMaxConcurrentActors() > 0, "Should have had concurrent actors");
            assertTrue(getHeapUsagePercentage() < CRITICAL_HEAP_PERCENTAGE,
                       "Should not have reached critical memory threshold");

        } finally {
            spawner.shutdown();
        }
    }

    /**
     * Tests with platform threads for comparison.
     */
    @Test
    @DisplayName("Test maximum actors with platform threads and default heap")
    void testPlatformThreadsWithDefaultHeap() {
        long memoryThreshold = (long) (heapPool.getUsage().getMax() * WARNING_HEAP_PERCENTAGE);
        ActorSpawner spawner = new ActorSpawner(false);

        try {
            int targetActors = 50; // Fewer for platform threads
            int spawnedCount = spawner.spawnActors(targetActors, memoryThreshold);

            logger.info("Spawned {} actors with platform threads", spawnedCount);
            logger.info("Max concurrent actors: {}", spawner.getMaxConcurrentActors());
            logger.info("Total memory used: {} MB", spawner.getTotalMemoryUsed() / MB);
            logger.info("Heap info: {}", getHeapInfo());

            // Verify platform thread results
            assertTrue(spawnedCount > 0, "Should have spawned at least one actor");

        } finally {
            spawner.shutdown();
        }
    }

    /**
     * Tests with different heap thresholds.
     */
    @ParameterizedTest
    @DisplayName("Test with different heap thresholds")
    @CsvSource({
        "0.75, 200",  // 75% threshold, 200 target actors
        "0.85, 150",  // 85% threshold, 150 target actors
        "0.95, 50"    // 95% threshold, 50 target actors
    })
    void testDifferentHeapThresholds(double thresholdPercent, int targetActors) {
        long memoryThreshold = (long) (heapPool.getUsage().getMax() * thresholdPercent);
        ActorSpawner spawner = new ActorSpawner(true);

        try {
            int spawnedCount = spawner.spawnActors(targetActors, memoryThreshold);

            logger.info("Spawned {} actors with {}% threshold", spawnedCount, thresholdPercent * 100);
            logger.info("Heap usage at test end: {:.1f}%", getHeapUsagePercentage() * 100);

            // Verify we respected the threshold
            assertTrue(getHeapUsagePercentage() < CRITICAL_HEAP_PERCENTAGE,
                       "Should not exceed critical threshold");

        } finally {
            spawner.shutdown();
        }
    }

    /**
     * Tests memory recovery after actor shutdown.
     */
    @Test
    @DisplayName("Test memory recovery after actor shutdown")
    void testMemoryRecovery() {
        long initialMemory = heapPool.getUsage().getUsed();
        List<TestMemoryAgent> agents = new ArrayList<>();

        // Create a batch of agents
        int agentCount = 20;
        for (int i = 0; i < agentCount; i++) {
            agents.add(new TestMemoryAgent(ACTOR_MEMORY_SIZE / 2)); // Smaller agents
        }

        logger.info("Created {} agents, heap usage increased by: {} MB",
                   agentCount, (heapPool.getUsage().getUsed() - initialMemory) / MB);

        // Execute tasks
        agents.forEach(agent -> {
            for (int i = 0; i < 10; i++) {
                agent.executeTask();
            }
        });

        // Close agents and check memory recovery
        long peakMemory = heapPool.getUsage().getUsed();
        agents.forEach(TestMemoryAgent::close);
        agents.clear();

        // Allow GC to run
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long finalMemory = heapPool.getUsage().getUsed();

        logger.info("Memory recovery - Peak: {} MB, Final: {} MB, Recovered: {} MB",
                   peakMemory / MB, finalMemory / MB, (peakMemory - finalMemory) / MB);

        // Some memory should be recovered, though not necessarily all
        assertTrue(finalMemory <= peakMemory, "Final memory should not exceed peak memory");
    }

    /**
     * Tests with actual ZAI API if available.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
    @DisplayName("Test with real ZAI agents")
    void testWithRealZaiAgents() {
        // Create a real GregVerse agent if ZAI_API_KEY is available
        org.yawlfoundation.yawl.mcp.a2a.gregverse.AbstractGregVerseAgent agent =
            new org.yawlfoundation.yawl.mcp.a2a.gregverse.AbstractGregVerseAgent() {
                @Override
                public String getAgentId() {
                    return "test-agent";
                }

                @Override
                public String getDisplayName() {
                    return "Test Agent";
                }

                @Override
                public String getBio() {
                    return "Test agent for memory testing";
                }

                @Override
                public List<String> getSpecializedSkills() {
                    return List.of("testing");
                }

                @Override
                public String getSystemPrompt() {
                    return "You are a test agent for memory testing scenarios.";
                }

                @Override
                public String getCommunicationStyle() {
                    return "Test communication style";
                }

                @Override
                public List<String> getExpertise() {
                    return List.of("testing", "memory");
                }

                @Override
                public AgentCard createAgentCard(int port, String basePath) {
                    throw new UnsupportedOperationException("Not implemented for testing");
                }

                @Override
                public List<AgentSkill> createAgentSkills() {
                    throw new UnsupportedOperationException("Not implemented for testing");
                }

                @Override
                public String provideAdvice(String topic, String context) {
                    return "Test advice on " + topic;
                }

                @Override
                public String getResponseFormat() {
                    return "Test response format";
                }
            };

        // Verify it can process a simple query
        String response = agent.processQuery("Hello, this is a test query");
        assertNotNull(response, "Agent should return a response");
        assertFalse(response.isEmpty(), "Response should not be empty");

        logger.info("Successfully processed query with real agent");
    }

    /**
     * Calculates sustainable actor count based on available memory.
     */
    @Test
    @DisplayName("Calculate sustainable actor count")
    void testCalculateSustainableActorCount() {
        long availableMemory = heapPool.getUsage().getMax() - heapPool.getUsage().getUsed();
        long safeMemory = (long) (availableMemory * 0.8); // Use 80% safely

        int estimatedActors = (int) (safeMemory / ACTOR_MEMORY_SIZE);

        logger.info("Available memory: {} MB", availableMemory / MB);
        logger.info("Safe memory: {} MB", safeMemory / MB);
        logger.info("Estimated sustainable actors: {}", estimatedActors);

        // Should be able to spawn at least a few actors
        assertTrue(estimatedActors > 0, "Should have space for at least one actor");
        assertTrue(estimatedActors < 1000, "Estimate should be reasonable");
    }

    /**
     * Performance benchmark for actor spawning.
     */
    @Test
    @DisplayName("Benchmark actor spawning performance")
    void testActorSpawningBenchmark() {
        int targetActors = 50;
        long startTime = System.currentTimeMillis();

        ActorSpawner spawner = new ActorSpawner(true);

        try {
            long spawnStart = System.currentTimeMillis();
            int spawnedCount = spawner.spawnActors(targetActors, Long.MAX_VALUE);
            long spawnEnd = System.currentTimeMillis();

            long spawnTime = spawnEnd - startTime;
            long spawnDuration = spawnEnd - spawnStart;

            logger.info("Spawned {} actors in {} ms (total: {} ms)",
                       spawnedCount, spawnDuration, spawnTime);
            logger.info("Average spawn time: {:.2f} ms per actor",
                       (double) spawnDuration / spawnedCount);

            // Performance assertions
            assertTrue(spawnedCount > 0, "Should spawn at least one actor");
            assertTrue(spawnDuration < 30000, "Spawning should complete within 30 seconds");

        } finally {
            spawner.shutdown();
        }
    }

    /**
     * Test memory leak detection by running multiple cycles.
     */
    @Test
    @DisplayName("Test memory leak detection")
    void testMemoryLeakDetection() {
        int cycles = 5;
        List<Long> memoryAfterCycles = new ArrayList<>();

        for (int cycle = 0; cycle < cycles; cycle++) {
            long cycleStartMemory = heapPool.getUsage().getUsed();

            // Create and destroy agents in one cycle
            List<TestMemoryAgent> cycleAgents = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                cycleAgents.add(new TestMemoryAgent(ACTOR_MEMORY_SIZE / 10));
            }

            // Execute some work
            cycleAgents.forEach(agent -> {
                for (int j = 0; j < 5; j++) {
                    agent.executeTask();
                }
            });

            // Clean up
            cycleAgents.forEach(TestMemoryAgent::close);
            cycleAgents.clear();

            // Force GC and measure
            System.gc();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            long cycleEndMemory = heapPool.getUsage().getUsed();
            memoryAfterCycles.add(cycleEndMemory);

            logger.info("Cycle {}: {} MB -> {} MB (delta: {} MB)",
                       cycle, cycleStartMemory / MB, cycleEndMemory / MB,
                       (cycleEndMemory - cycleStartMemory) / MB);
        }

        // Check for memory growth across cycles
        long firstCycleMemory = memoryAfterCycles.get(0);
        long lastCycleMemory = memoryAfterCycles.get(memoryAfterCycles.size() - 1);
        long memoryGrowth = lastCycleMemory - firstCycleMemory;

        logger.info("Memory growth over {} cycles: {} MB", cycles, memoryGrowth / MB);

        // If growth is more than 10% of initial heap, flag potential leak
        if (memoryGrowth > heapPool.getUsage().getMax() * 0.1) {
            logger.warn("Potential memory leak detected: {} MB growth", memoryGrowth / MB);
        }

        // For now, just log the results - could enhance to fail on significant leaks
        assertTrue(memoryGrowth < heapPool.getUsage().getMax() * 0.2,
                   "Memory growth should not exceed 20% of max heap");
    }
}
