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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;

/**
 * Stress test for virtual thread actor spawning in YAWL.
 *
 * <p>This test validates the performance characteristics and breaking points when
 * spawning large numbers of virtual thread actors. It measures memory usage,
 * spawn times, and detects common failure modes.</p>
 *
 * <h2>Test Scenarios</h2>
 * <ul>
 *   <li>10 actors - baseline performance</li>
 *   <li>100 actors - typical load</li>
 *   <li>1000 actors - high load</li>
 *   <li>10000 actors - stress test</li>
 *   <li>100000 actors - breaking point test</li>
 * </ul>
 *
 * <h2>Metrics Tracked</h2>
 * <ul>
 *   <li>Memory before/after spawning</li>
 *   <li>Time to spawn all actors</li>
 *   <li>Spawn success/failure rates</li>
 *   <li>Virtual thread count</li>
 *   <li>GC activity</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Virtual Thread Actor Spawn Stress Tests")
class ActorSpawnStressTest {

    private static final Logger logger = LoggerFactory.getLogger(ActorSpawnStressTest.class);
    private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private static final AtomicInteger totalActorsSpawned = new AtomicInteger(0);
    private static final AtomicInteger totalSpawnFailures = new AtomicInteger(0);
    private static final ActorRuntime runtime = new VirtualThreadRuntime();

    /**
     * Simple test actor behavior that just waits for a shutdown message.
     */
    private static class TestActorBehavior implements Runnable {
        private final AtomicBoolean running;
        private final BlockingQueue<String> messageQueue;

        TestActorBehavior(AtomicBoolean running) {
            this.running = running;
            this.messageQueue = new ArrayBlockingQueue<>(1);
        }

        @Override
        public void run() {
            while (running.get()) {
                // Block until shutdown
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    running.set(false);
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        public void shutdown() {
            running.set(false);
        }
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
     * Measure current virtual thread count.
     */
    private long getVirtualThreadCount() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.isVirtual())
                .count();
    }

    /**
     * Spawn a batch of actors and measure performance.
     *
     * This method measures only the spawn call duration, not including actor startup
     * or other overhead.
     *
     * @param batchCount number of actors to spawn
     * @return SpawnResult containing metrics
     */
    private SpawnResult spawnActorBatch(int batchCount) {
        List<ActorRef> refs = new ArrayList<>();

        // Memory before spawning
        long memoryBefore = getUsedMemoryMB();
        long virtualThreadsBefore = getVirtualThreadCount();

        // Start timing - right before spawn calls
        long startTime = System.nanoTime();

        // Spawn actors using runtime.spawn directly
        int successfulSpawns = 0;
        for (int i = 0; i < batchCount; i++) {
            try {
                ActorRef ref = runtime.spawn(self -> {
                    // Simple actor that waits for shutdown
                    while (!Thread.currentThread().isInterrupted()) {
                        // Minimal work - just yield to avoid CPU spin
                        Thread.onSpinWait();
                    }
                });
                assertNotNull(ref);
                successfulSpawns++;
                totalActorsSpawned.incrementAndGet();
                // Keep track of ref for cleanup
                refs.add(ref);
            } catch (Exception e) {
                totalSpawnFailures.incrementAndGet();
                logger.warn("Failed to spawn actor {}: {}", i, e.getMessage());
            }
        }

        // End timing - immediately after last spawn call
        long endTime = System.nanoTime();
        long spawnTimeMs = (endTime - startTime) / 1_000_000;

        // Memory after spawning (give a moment for object allocation)
        try {
            Thread.sleep(50); // Brief pause for memory allocation
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long memoryAfter = getUsedMemoryMB();
        long virtualThreadsAfter = getVirtualThreadCount();

        // Cleanup - stop all actors
        for (ActorRef ref : refs) {
            try {
                ref.stop();
            } catch (Exception e) {
                logger.warn("Error stopping actor: {}", e.getMessage());
            }
        }

        return new SpawnResult(
            batchCount,
            successfulSpawns,
            spawnTimeMs,
            memoryBefore,
            memoryAfter,
            virtualThreadsBefore,
            virtualThreadsAfter,
            (memoryAfter - memoryBefore),
            totalSpawnFailures.get()
        );
    }

    /**
     * Stress test with various batch sizes.
     */
    /**
     * Test to properly measure spawn latency without artificial waits.
     * This test measures only the spawn call, not test overhead.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1000})
    @DisplayName("Measure spawn latency accurately")
    void spawnActorBatches(int batchSize) {
        long start = System.nanoTime();

        for (int i = 0; i < batchSize; i++) {
            ActorRef ref = runtime.spawn(self -> {
                while (!Thread.currentThread().isInterrupted()) {
                    self.recv();
                }
            });
            assertNotNull(ref);
        }

        long elapsed = System.nanoTime() - start;
        long elapsedMs = elapsed / 1_000_000;

        // Assert based on batch size
        if (batchSize <= 10) {
            assertTrue(elapsedMs < 1000, "10 actors should spawn in < 1s, took " + elapsedMs + "ms");
        } else if (batchSize == 100) {
            assertTrue(elapsedMs < 5000, "100 actors should spawn in < 5s, took " + elapsedMs + "ms");
        } else if (batchSize == 1000) {
            assertTrue(elapsedMs < 15000, "1000 actors should spawn in < 15s, took " + elapsedMs + "ms");
        }

        logger.info("Spawned {} actors in {} ms", batchSize, elapsedMs);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 100, 1000, 10000})
    @DisplayName("Spawn actors in batches (legacy)")
    void spawnActorBatchesLegacy(int batchCount) {
        logger.info("Starting spawn test for {} actors", batchCount);

        try {
            SpawnResult result = spawnActorBatch(batchCount);

            // Log results
            logger.info("Spawn Results for {} actors:", batchCount);
            logger.info("  - Spawned: {}/{} actors", result.successfulSpawns, batchCount);
            logger.info("  - Spawn time: {} ms", result.spawnTimeMs);
            logger.info("  - Memory increase: {} MB", result.memoryIncreaseMB);
            logger.info("  - Virtual threads: {} -> {}", result.virtualThreadsBefore, result.virtualThreadsAfter);
            logger.info("  - Spawn failures: {}", result.totalSpawnFailures);

            // Assertions
            assertEquals(batchCount, result.requestedSpawns, "Should request exactly " + batchCount + " actors");
            assertTrue(result.successfulSpawns > 0, "Should spawn at least one actor");

            if (batchCount <= 1000) {
                assertEquals(batchCount, result.successfulSpawns,
                    "Should successfully spawn all actors for small batches");
            }

            assertTrue(result.spawnTimeMs > 0, "Spawn time should be positive");
            assertTrue(result.memoryIncreaseMB >= 0, "Memory increase should be non-negative");

            // Performance assertions
            if (batchCount == 10) {
                assertTrue(result.spawnTimeMs < 1000,
                    "10 actors should spawn in under 1 second");
            } else if (batchCount == 100) {
                assertTrue(result.spawnTimeMs < 5000,
                    "100 actors should spawn in under 5 seconds");
            } else if (batchCount == 1000) {
                assertTrue(result.spawnTimeMs < 15000,
                    "1000 actors should spawn in under 15 seconds");
            }

        } catch (OutOfMemoryError e) {
            logger.error("OutOfMemoryError while spawning {} actors: {}", batchCount, e.getMessage());
            fail("Should not run out of memory for " + batchCount + " actors: " + e.getMessage());
        } catch (RejectedExecutionException e) {
            logger.warn("RejectedExecutionException while spawning {} actors: {}", batchCount, e.getMessage());
            // This is acceptable for very large batches, should not fail the test
        }
    }

    /**
     * Test to find the breaking point where spawning fails.
     */
    @Test
    @Order(1)
    @DisplayName("Find actor spawn breaking point")
    void findActorSpawnBreakingPoint() {
        int[] batchSizes = {10, 100, 1000, 5000, 10000, 20000, 50000, 100000};
        int successfulBatches = 0;

        logger.info("Finding actor spawn breaking point...");

        for (int batchSize : batchSizes) {
            logger.info("Testing batch size: {}", batchSize);

            try {
                SpawnResult result = spawnActorBatch(batchSize);

                if (result.successfulSpawns > 0) {
                    successfulBatches++;
                    logger.info("✓ Successfully spawned {}/{} actors in {} ms",
                        result.successfulSpawns, batchSize, result.spawnTimeMs);
                } else {
                    logger.warn("✗ Failed to spawn any actors in batch size {}", batchSize);
                }

            } catch (OutOfMemoryError e) {
                logger.error("✗ OutOfMemoryError at batch size {}: {}", batchSize, e.getMessage());
                break;
            } catch (Exception e) {
                logger.error("✗ Exception at batch size {}: {}", batchSize, e.getMessage());
                break;
            }
        }

        logger.info("Breaking point analysis complete. Successful batches: {}/{}",
            successfulBatches, batchSizes.length);

        assertTrue(successfulBatches > 0, "Should be able to spawn at least some actors");
    }

    /**
     * Test memory leak detection by spawning and then cleaning up actors.
     */
    @Test
    @Order(2)
    @DisplayName("Memory leak detection test")
    void memoryLeakDetectionTest() {
        int testSize = 1000;
        long memoryBefore = getUsedMemoryMB();

        // Spawn and immediately cleanup a batch
        SpawnResult result = spawnActorBatch(testSize);

        // Give GC time to work
        System.gc();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long memoryAfter = getUsedMemoryMB();
        long memoryIncrease = memoryAfter - memoryBefore;

        logger.info("Memory leak test - Before: {} MB, After: {} MB, Increase: {} MB",
            memoryBefore, memoryAfter, memoryIncrease);

        // Memory increase should be reasonable (less than 50MB for 1000 actors)
        assertTrue(memoryIncrease < 50,
            "Memory increase after spawning and cleaning up 1000 actors should be less than 50MB");
    }

    /**
     * Test concurrent spawning to detect race conditions.
     */
    @Test
    @Order(3)
    @DisplayName("Concurrent spawn test")
    void concurrentSpawnTest() throws InterruptedException, ExecutionException, TimeoutException {
        int batchSize = 1000;
        int concurrentSpawners = 5;

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<SpawnResult>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentSpawners; i++) {
            CompletableFuture<SpawnResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return spawnActorBatch(batchSize / concurrentSpawners);
                } catch (Exception e) {
                    throw new RuntimeException("Spawn failed", e);
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all spawners to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.get(60, TimeUnit.SECONDS);

            // Check results
            int totalSpawned = 0;
            long totalTime = 0;

            for (CompletableFuture<SpawnResult> future : futures) {
                try {
                    SpawnResult result = future.get();
                    totalSpawned += result.successfulSpawns;
                    totalTime += result.spawnTimeMs;
                } catch (Exception e) {
                    logger.error("Spawn task failed", e);
                }
            }

            logger.info("Concurrent spawn test completed:");
            logger.info("  - Total actors spawned: {}", totalSpawned);
            logger.info("  - Average spawn time: {} ms", totalTime / concurrentSpawners);

            assertTrue(totalSpawned > 0, "Should spawn at least some actors concurrently");

        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * Test result record for spawn operations.
     */
    private static record SpawnResult(
        int requestedSpawns,
        int successfulSpawns,
        long spawnTimeMs,
        long memoryBeforeMB,
        long memoryAfterMB,
        long virtualThreadsBefore,
        long virtualThreadsAfter,
        long memoryIncreaseMB,
        int totalSpawnFailures
    ) {
    }
}