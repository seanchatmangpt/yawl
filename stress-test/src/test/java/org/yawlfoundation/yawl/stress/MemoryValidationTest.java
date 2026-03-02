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

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Memory validation test for spawn latency fix.
 * 
 * This test specifically validates:
 * 1. No memory leak from async spawn
 * 2. Actors are properly stopped and memory is released
 * 3. Memory stability over multiple operations
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Memory Validation After Spawn Latency Fix")
class MemoryValidationTest {

    private static final Logger logger = LoggerFactory.getLogger(MemoryValidationTest.class);
    private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private static final VirtualThreadRuntime runtime = new VirtualThreadRuntime();
    
    private long initialMemoryUsed;

    @BeforeEach
    void setup() {
        // Run GC and establish baseline
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        initialMemoryUsed = getUsedMemoryMB();
        logger.info("Initial memory usage: {} MB", initialMemoryUsed);
    }

    /**
     * Measure current memory usage in MB.
     */
    private long getUsedMemoryMB() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return usedMemory / (1024 * 1024);
    }

    /**
     * Test 1: Verify no memory leak from async spawn
     * 
     * Spawns many actors, stops them, and verifies memory returns to baseline.
     */
    @Test
    @Order(1)
    @DisplayName("No memory leak from async spawn")
    void noMemoryLeakFromAsyncSpawn() throws InterruptedException {
        int iterations = 3;
        int actorsPerIteration = 500;
        long totalMemoryIncrease = 0;

        logger.info("Starting memory leak test with {} iterations of {} actors each", 
                   iterations, actorsPerIteration);

        for (int iter = 0; iter < iterations; iter++) {
            logger.info("Iteration {} of {}", iter + 1, iterations);
            
            long memoryBefore = getUsedMemoryMB();
            List<org.yawlfoundation.yawl.engine.agent.core.ActorRef> refs = new ArrayList<>();

            // Spawn actors
            for (int i = 0; i < actorsPerIteration; i++) {
                var ref = runtime.spawn(self -> {
                    AtomicBoolean running = new AtomicBoolean(true);
                    while (running.get() && !Thread.currentThread().isInterrupted()) {
                        try {
                            // Minimal work
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            running.set(false);
                            Thread.currentThread().interrupt();
                        }
                    }
                });
                refs.add(ref);
            }

            // Stop all actors
            for (org.yawlfoundation.yawl.engine.agent.core.ActorRef ref : refs) {
                ref.stop();
            }

            // Clear references to allow GC
            refs.clear();
            
            // Force GC and wait
            System.gc();
            Thread.sleep(200);

            long memoryAfter = getUsedMemoryMB();
            long memoryIncrease = memoryAfter - memoryBefore;
            totalMemoryIncrease += memoryIncrease;

            logger.info("Iteration {}: Memory before: {} MB, after: {} MB, increase: {} MB", 
                       iter + 1, memoryBefore, memoryAfter, memoryIncrease);

            // Memory increase per iteration should be reasonable (< 15MB)
            assertTrue(memoryIncrease < 15, 
                String.format("Memory increase too high in iteration %d: %d MB", iter + 1, memoryIncrease));
        }

        long finalMemory = getUsedMemoryMB();
        long overallIncrease = finalMemory - initialMemoryUsed;

        logger.info("Final memory validation:");
        logger.info("  - Initial memory: {} MB", initialMemoryUsed);
        logger.info("  - Final memory: {} MB", finalMemory);
        logger.info("  - Total increase: {} MB", overallIncrease);

        // Total memory increase should be reasonable (< 25MB after 3 iterations)
        assertTrue(overallIncrease < 25, 
            String.format("Total memory increase too high: %d MB", overallIncrease));
    }

    /**
     * Test 2: Verify mailbox memory is released
     * 
     * Tests actors with message queues to ensure mailbox memory is properly released.
     */
    @Test
    @Order(2)
    @DisplayName("Mailbox memory is released")
    void mailboxMemoryIsReleased() throws InterruptedException {
        int iterations = 2;
        int messagesPerActor = 300;
        int actorCount = 100;

        logger.info("Starting mailbox memory test with {} actors, {} messages each", 
                   actorCount, messagesPerActor);

        long totalMemoryIncrease = 0;

        for (int iter = 0; iter < iterations; iter++) {
            long memoryBefore = getUsedMemoryMB();
            List<org.yawlfoundation.yawl.engine.agent.core.ActorRef> actors = new ArrayList<>();

            // Create actors with message handling
            for (int i = 0; i < actorCount; i++) {
                var ref = runtime.spawn(self -> {
                    // Actor that handles messages and then exits
                    try {
                        for (int j = 0; j < messagesPerActor / 2; j++) {
                            self.recv();  // Consume some messages
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                actors.add(ref);
            }

            // Send messages to fill mailboxes
            for (org.yawlfoundation.yawl.engine.agent.core.ActorRef ref : actors) {
                for (int j = 0; j < messagesPerActor; j++) {
                    ref.tell("message");
                }
            }

            // Let actors process some messages
            Thread.sleep(100);

            // Stop actors
            for (org.yawlfoundation.yawl.engine.agent.core.ActorRef ref : actors) {
                ref.stop();
            }

            // Clear references
            actors.clear();
            
            // Force GC
            System.gc();
            Thread.sleep(200);

            long memoryAfter = getUsedMemoryMB();
            long memoryIncrease = memoryAfter - memoryBefore;
            totalMemoryIncrease += memoryIncrease;

            logger.info("Iteration {}: Memory before: {} MB, after: {} MB, increase: {} MB", 
                       iter + 1, memoryBefore, memoryAfter, memoryIncrease);

            // Memory increase should be reasonable (< 10MB per iteration)
            assertTrue(memoryIncrease < 10, 
                String.format("Mailbox memory increase too high in iteration %d: %d MB", 
                             iter + 1, memoryIncrease));
        }

        long finalMemory = getUsedMemoryMB();
        long totalIncrease = finalMemory - initialMemoryUsed;

        logger.info("Final mailbox memory test result:");
        logger.info("  - Initial memory: {} MB", initialMemoryUsed);
        logger.info("  - Final memory: {} MB", finalMemory);
        logger.info("  - Total increase: {} MB", totalIncrease);

        assertTrue(totalIncrease < 20, 
            String.format("Final memory increase too high: %d MB", totalIncrease));
    }

    /**
     * Test 3: Memory stability test - multiple spawn/stop cycles
     */
    @Test
    @Order(3)
    @DisplayName("Memory stability over multiple spawn/stop cycles")
    void memoryStabilityOverCycles() throws InterruptedException {
        int cycles = 5;
        int actorsPerCycle = 200;

        logger.info("Starting memory stability test with {} cycles", cycles);

        long baselineMemory = getUsedMemoryMB();
        long maxMemory = baselineMemory;
        long minMemory = baselineMemory;

        for (int cycle = 0; cycle < cycles; cycle++) {
            long cycleStart = getUsedMemoryMB();

            // Spawn actors
            List<org.yawlfoundation.yawl.engine.agent.core.ActorRef> actors = new ArrayList<>();
            for (int i = 0; i < actorsPerCycle; i++) {
                var ref = runtime.spawn(self -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            Thread.sleep(50);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                actors.add(ref);
            }

            // Stop actors
            for (org.yawlfoundation.yawl.engine.agent.core.ActorRef ref : actors) {
                ref.stop();
            }

            // Clear references
            actors.clear();

            // Measure memory after cycle
            System.gc();
            Thread.sleep(100);
            long cycleEnd = getUsedMemoryMB();

            // Track min/max
            maxMemory = Math.max(maxMemory, cycleEnd);
            minMemory = Math.min(minMemory, cycleEnd);

            long cycleIncrease = cycleEnd - cycleStart;
            logger.info("Cycle {}: Memory change: +{} MB (current: {} MB)", 
                       cycle + 1, cycleIncrease, cycleEnd);

            // Each cycle should not significantly increase memory
            assertTrue(cycleIncrease < 8, 
                String.format("Memory increase too high in cycle %d: %d MB", 
                             cycle + 1, cycleIncrease));
        }

        long totalMemoryDrift = maxMemory - minMemory;
        
        logger.info("Memory stability analysis:");
        logger.info("  - Baseline memory: {} MB", baselineMemory);
        logger.info("  - Max memory: {} MB", maxMemory);
        logger.info("  - Min memory: {} MB", minMemory);
        logger.info("  - Total drift: {} MB", totalMemoryDrift);

        // Memory should be stable (drift < 15MB)
        assertTrue(totalMemoryDrift < 15, 
            String.format("Memory drift too high: %d MB", totalMemoryDrift));
    }

    /**
     * Test 4: Verify spawn performance doesn't degrade over time
     */
    @Test
    @Order(4)
    @DisplayName("Spawn performance consistency")
    void spawnPerformanceConsistency() throws InterruptedException {
        int iterations = 3;
        int actorsPerBatch = 1000;
        long firstSpawnTime = 0;

        logger.info("Starting spawn performance test with {} iterations", iterations);

        for (int iter = 0; iter < iterations; iter++) {
            long startTime = System.nanoTime();

            // Spawn actors
            List<org.yawlfoundation.yawl.engine.agent.core.ActorRef> refs = new ArrayList<>();
            for (int i = 0; i < actorsPerBatch; i++) {
                var ref = runtime.spawn(self -> {
                    try {
                        Thread.sleep(1);  // Very short-lived actor
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                refs.add(ref);
            }

            // Stop all actors
            for (org.yawlfoundation.yawl.engine.agent.core.ActorRef ref : refs) {
                ref.stop();
            }

            // Clear references
            refs.clear();

            long spawnTimeMs = (System.nanoTime() - startTime) / 1_000_000;
            
            logger.info("Iteration {}: Spawn time for {} actors: {} ms", 
                       iter + 1, actorsPerBatch, spawnTimeMs);

            // First iteration should be fast (no latency)
            if (iter == 0) {
                firstSpawnTime = spawnTimeMs;
                assertTrue(spawnTimeMs < 1000, 
                    "First spawn should be fast (< 1s), took " + spawnTimeMs + "ms");
            } else {
                // Subsequent iterations should not be significantly slower
                assertTrue(spawnTimeMs < firstSpawnTime * 2, 
                    String.format("Spawn time degraded in iteration %d: %dms (was %dms)", 
                                 iter + 1, spawnTimeMs, firstSpawnTime));
            }

            // Force GC between iterations
            System.gc();
            Thread.sleep(200);
        }
    }
}
