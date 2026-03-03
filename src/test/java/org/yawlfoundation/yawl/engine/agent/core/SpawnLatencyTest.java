package org.yawlfoundation.yawl.engine.agent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test class that precisely measures VirtualThreadRuntime.spawn() latency.
 *
 * Requirements:
 * - Uses System.nanoTime() for precise measurements
 * - Warms up JVM by spawning 100 actors before measuring
 * - Measures p50, p95, p99 latencies
 * - Tests multiple batch sizes (1, 10, 100, 1000)
 * - Asserts p99 < 100ms for any batch size up to 1000
 */
@Tag("perf")
class SpawnLatencyTest {

    private VirtualThreadRuntime runtime;
    private static final int WARMUP_COUNT = 100;
    private static final int ITERATIONS = 1000; // For percentile calculations
    private static final long MAX_P99_NANOS = 100_000_000; // 100ms in nanoseconds

    @BeforeEach
    void setUp() {
        runtime = new VirtualThreadRuntime();

        // JVM warmup - spawn 100 actors before measuring
        for (int i = 0; i < WARMUP_COUNT; i++) {
            runtime.spawn(self -> {
                // Do minimal work to ensure spawn completes
                Thread.onSpinWait();
            });
        }

        // Allow warmup to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void spawnLatency_singleActor_under10ms() {
        long start = System.nanoTime();
        runtime.spawn(self -> {
            Thread.onSpinWait();
        });
        long elapsed = System.nanoTime() - start;

        assertTrue(elapsed < 10_000_000,
            "Single spawn took " + (elapsed / 1_000_000) + "ms, expected < 10ms");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1000})
    void spawnLatency_percentileMetrics(int batchSize) {
        long[] latencies = new long[ITERATIONS];

        // Measure spawn latencies for the given batch size
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            // Measure batch spawn
            long batchStart = System.nanoTime();

            // Spawn the batch of actors
            AtomicInteger completionCount = new AtomicInteger(0);
            for (int i = 0; i < batchSize; i++) {
                runtime.spawn(self -> {
                    Thread.onSpinWait();
                    completionCount.incrementAndGet();
                });
            }

            // Wait for all actors in batch to complete
            while (completionCount.get() < batchSize) {
                Thread.onSpinWait();
            }

            long batchEnd = System.nanoTime();
            long batchElapsed = batchEnd - batchStart;

            // Calculate average latency per spawn in this batch
            long avgPerSpawn = batchElapsed / batchSize;
            latencies[iteration] = avgPerSpawn;
        }

        // Sort latencies for percentile calculation
        Arrays.sort(latencies);

        // Calculate percentiles
        long p50 = latencies[(int) (ITERATIONS * 0.50)];
        long p95 = latencies[(int) (ITERATIONS * 0.95)];
        long p99 = latencies[(int) (ITERATIONS * 0.99)];

        // Assertions
        assertAll("Spawn latency percentiles for batch size " + batchSize,
            () -> assertTrue(p50 < MAX_P99_NANOS / 10,
                String.format("p50 (%dms) should be < 10ms for batch %d",
                    p50 / 1_000_000, batchSize)),
            () -> assertTrue(p95 < MAX_P99_NANOS / 2,
                String.format("p95 (%dms) should be < 50ms for batch %d",
                    p95 / 1_000_000, batchSize)),
            () -> assertTrue(p99 < MAX_P99_NANOS,
                String.format("p99 (%dms) should be < 100ms for batch %d",
                    p99 / 1_000_000, batchSize))
        );

        // Print detailed metrics for debugging
        System.out.printf("Batch size %d: p50=%dms, p95=%dms, p99=%dms%n",
            batchSize,
            p50 / 1_000_000,
            p95 / 1_000_000,
            p99 / 1_000_000);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1000})
    void spawnLatency_individualSpawns(int batchSize) {
        long[] individualLatencies = new long[batchSize];

        // Measure each spawn individually in the batch
        for (int i = 0; i < batchSize; i++) {
            long start = System.nanoTime();
            runtime.spawn(self -> {
                Thread.onSpinWait();
            });
            long elapsed = System.nanoTime() - start;
            individualLatencies[i] = elapsed;
        }

        // Calculate percentiles for this batch
        Arrays.sort(individualLatencies);
        long p99 = individualLatencies[(int) (batchSize * 0.99)];

        // Assert p99 < 100ms
        assertTrue(p99 < MAX_P99_NANOS,
            String.format("p99 spawn latency (%dms) should be < 100ms for batch size %d",
                p99 / 1_000_000, batchSize));
    }

    @Test
    void spawnLatency_consistencyAcrossBatches() {
        int[] batchSizes = {10, 50, 100, 500};
        long[] avgLatencies = new long[batchSizes.length];

        for (int batchIdx = 0; batchIdx < batchSizes.length; batchIdx++) {
            int batchSize = batchSizes[batchIdx];
            long totalLatency = 0;

            // Measure 10 batches for this size
            for (int batchNum = 0; batchNum < 10; batchNum++) {
                long batchStart = System.nanoTime();

                AtomicInteger completionCount = new AtomicInteger(0);
                for (int i = 0; i < batchSize; i++) {
                    runtime.spawn(self -> {
                        Thread.onSpinWait();
                        completionCount.incrementAndGet();
                    });
                }

                while (completionCount.get() < batchSize) {
                    Thread.onSpinWait();
                }

                long batchEnd = System.nanoTime();
                totalLatency += (batchEnd - batchStart) / batchSize;
            }

            avgLatencies[batchIdx] = totalLatency / 10;
        }

        // Check that latency doesn't increase dramatically with batch size
        for (int i = 1; i < avgLatencies.length; i++) {
            long ratio = (double) avgLatencies[i] / avgLatencies[i - 1];
            assertTrue(ratio < 3.0,
                String.format("Latency ratio between batch sizes %d and %d is %f (should be < 3)",
                    batchSizes[i-1], batchSizes[i], ratio));
        }
    }

    @Test
    void spawnLatency_noMemoryLeak() {
        // Spawn a large number of actors and check for memory issues
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        for (int i = 0; i < 10_000; i++) {
            runtime.spawn(self -> {
                Thread.onSpinWait();
            });
        }

        // Allow GC to run
        System.gc();
        Thread.yield();

        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryDelta = endMemory - startMemory;

        // Memory increase should be reasonable (less than 10MB)
        assertTrue(memoryDelta < 10_000_000,
            String.format("Memory increase of %d bytes is too large", memoryDelta));
    }

    @Test
    void spawnLatency_concurrentSpawns() {
        int threadCount = 10;
        int spawnsPerThread = 100;
        long[][] latencies = new long[threadCount][spawnsPerThread];

        // Spawn multiple threads that spawn actors concurrently
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < spawnsPerThread; i++) {
                    long start = System.nanoTime();
                    runtime.spawn(self -> {
                        Thread.onSpinWait();
                    });
                    long elapsed = System.nanoTime() - start;
                    latencies[threadIdx][i] = elapsed;
                }
            });
            threads[t].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Calculate overall percentiles
        long[] allLatencies = new long[threadCount * spawnsPerThread];
        int index = 0;
        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < spawnsPerThread; i++) {
                allLatencies[index++] = latencies[t][i];
            }
        }

        Arrays.sort(allLatencies);
        long p99 = allLatencies[(int) (allLatencies.length * 0.99)];

        assertTrue(p99 < MAX_P99_NANOS,
            String.format("p99 concurrent spawn latency (%dms) should be < 100ms",
                p99 / 1_000_000));
    }
}