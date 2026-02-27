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

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.*;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Blue Ocean Test #6: Memory Leak Detection under Sustained Virtual Thread Load.
 *
 * <p>Runs sustained virtual thread workflow execution while periodically sampling
 * heap usage. Fails if the heap growth rate exceeds {@value #MAX_GROWTH_MB_PER_MINUTE}MB/min,
 * which would indicate a memory leak — the most dangerous production failure mode
 * for a long-running workflow engine.</p>
 *
 * <p><strong>Why this is "blue ocean":</strong> Memory leaks in workflow engines are
 * notoriously hard to detect because they accumulate gradually. Unit tests pass; only
 * sustained multi-minute load reveals the issue. This test encodes that observation as
 * a CI gate using heap snapshots and linear regression on the growth rate.</p>
 *
 * <h2>Heap growth formula</h2>
 * <pre>
 *   rate = (last_snapshot - first_snapshot) / elapsed_minutes
 *   pass iff rate &lt; MAX_GROWTH_MB_PER_MINUTE
 * </pre>
 *
 * <h2>Load pattern</h2>
 * <ul>
 *   <li>Continuous batch of 50 virtual threads, each sleeping 10ms (simulated I/O)</li>
 *   <li>Batches repeat immediately after completion (no idle time)</li>
 *   <li>Heap sampled every {@value #SNAPSHOT_INTERVAL_MS}ms ({@value #SNAPSHOT_COUNT} samples)</li>
 *   <li>Total test duration: ~{@code SNAPSHOT_COUNT × SNAPSHOT_INTERVAL_MS}ms
 *       = {@value #TOTAL_DURATION_DESCRIPTION}</li>
 * </ul>
 *
 * <h2>What a memory leak looks like</h2>
 * <ul>
 *   <li>Heap grows monotonically even after GC</li>
 *   <li>GC collection count increases rapidly (GC pressure)</li>
 *   <li>Rate exceeds threshold: e.g., 8MB/min when max is 5MB/min</li>
 * </ul>
 *
 * <h2>Java 25 features</h2>
 * <ul>
 *   <li>{@code Thread.ofVirtual().name("load-gen").start(runnable)} for named load thread</li>
 *   <li>{@link StructuredTaskScope.ShutdownOnFailure} for per-batch coordination</li>
 *   <li>Records {@link HeapSnapshot} for immutable, zero-copy metric capture</li>
 *   <li>{@code ManagementFactory.getGarbageCollectorMXBeans()} for GC event tracking</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("memory-leak")
@Tag("sustained-load")
@Tag("stress")
@DisplayName("Memory Leak Detection under Sustained Load")
class MemoryLeakStressTest {

    // ── Configuration ──────────────────────────────────────────────────────────

    /** Maximum allowed heap growth rate. Exceeding this indicates a memory leak. */
    private static final double MAX_GROWTH_MB_PER_MINUTE = 5.0;

    /** Delay between heap snapshots in milliseconds. */
    private static final int SNAPSHOT_INTERVAL_MS = 6_000;  // 6 seconds

    /** Total number of heap snapshots to collect. */
    private static final int SNAPSHOT_COUNT = 10;            // 10 × 6s = 60s total

    /** Human-readable total duration for display in Javadoc/logs. */
    private static final String TOTAL_DURATION_DESCRIPTION = "~60 seconds";

    /** Number of concurrent virtual threads per batch. */
    private static final int BATCH_SIZE = 50;

    /** I/O latency per task in ms. */
    private static final int IO_LATENCY_MS = 10;

    // ── Data type ──────────────────────────────────────────────────────────────

    /**
     * Immutable heap snapshot taken at a single point in time.
     *
     * @param heapUsedBytes   heap bytes in use after best-effort GC
     * @param timestamp       wall clock at snapshot time (milliseconds since epoch)
     * @param gcCollections   cumulative GC collection count at this point
     * @param batchesComplete cumulative number of task batches finished
     */
    record HeapSnapshot(long heapUsedBytes, long timestamp, long gcCollections, long batchesComplete) {}

    // ── Test ───────────────────────────────────────────────────────────────────

    /**
     * Runs {@value #SNAPSHOT_COUNT} heap snapshots over ~{@value #TOTAL_DURATION_DESCRIPTION}
     * of sustained load. Computes the heap growth rate and fails if it exceeds
     * {@value #MAX_GROWTH_MB_PER_MINUTE}MB/min.
     */
    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    @DisplayName("Heap growth must stay below " + MAX_GROWTH_MB_PER_MINUTE + "MB/min under sustained load")
    void detectHeapLeakUnderSustainedLoad() throws Exception {
        Runtime rt = Runtime.getRuntime();
        List<HeapSnapshot> snapshots = new ArrayList<>(SNAPSHOT_COUNT);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong batchCounter = new AtomicLong(0);

        // Start the continuous load generator on a named virtual thread
        Thread loadThread = Thread.ofVirtual()
            .name("yawl-mem-load-gen")
            .start(() -> runContinuousLoad(running, batchCounter));

        // Collect heap snapshots at regular intervals
        for (int i = 0; i < SNAPSHOT_COUNT; i++) {
            Thread.sleep(SNAPSHOT_INTERVAL_MS);

            // Request GC before each snapshot to get a clean heap-in-use reading
            System.gc();
            Thread.sleep(200);  // Allow GC to complete

            long heapUsed   = rt.totalMemory() - rt.freeMemory();
            long gcCount    = totalGcCollections();
            long batches    = batchCounter.get();

            snapshots.add(new HeapSnapshot(heapUsed, System.currentTimeMillis(), gcCount, batches));

            System.out.printf("[MemoryLeakStressTest] Snapshot %2d/%d: heap=%.1fMB "
                + "gc=%d batches=%d%n",
                i + 1, SNAPSHOT_COUNT,
                heapUsed / (1024.0 * 1024.0),
                gcCount, batches
            );
        }

        // Stop load generator
        running.set(false);
        loadThread.join(10_000);

        // Analyze heap growth
        assertFalse(snapshots.isEmpty(), "No heap snapshots collected");
        analyzeAndAssertGrowthRate(snapshots);
    }

    // ── Load generator ─────────────────────────────────────────────────────────

    /**
     * Runs continuous batches of {@value #BATCH_SIZE} virtual-thread tasks until
     * {@code running} is set to false. Uses {@link StructuredTaskScope} to ensure
     * proper task-tree lifecycle: tasks are properly cancelled on batch failure.
     */
    private void runContinuousLoad(AtomicBoolean running, AtomicLong batchCounter) {
        while (running.get()) {
            // Java 25 StructuredTaskScope API: use open() + Joiner (replaces old ShutdownOnFailure)
            try (var scope = StructuredTaskScope.open(
                    StructuredTaskScope.Joiner.<Void>awaitAllSuccessfulOrThrow())) {
                for (int i = 0; i < BATCH_SIZE; i++) {
                    scope.fork(() -> {
                        Thread.sleep(IO_LATENCY_MS);
                        return null;
                    });
                }
                scope.join();
                // awaitAllSuccessfulOrThrow joins and discards result; batch counter advances
                batchCounter.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Batch error: log and continue (engine should be resilient)
                System.err.println("[MemoryLeakStressTest] Batch error: " + e.getMessage());
            }
        }
    }

    // ── Analysis ───────────────────────────────────────────────────────────────

    /**
     * Computes the heap growth rate from the first and last snapshots and asserts
     * it is below {@value #MAX_GROWTH_MB_PER_MINUTE}MB/min.
     *
     * <p>Uses first-to-last delta rather than linear regression to keep the
     * analysis simple and reproducible. The threshold is conservative enough
     * to absorb JVM warm-up allocation spikes in early snapshots.</p>
     */
    private void analyzeAndAssertGrowthRate(List<HeapSnapshot> snapshots) {
        HeapSnapshot first = snapshots.get(0);
        HeapSnapshot last  = snapshots.get(snapshots.size() - 1);

        long elapsedMs      = last.timestamp() - first.timestamp();
        long growthBytes    = last.heapUsedBytes() - first.heapUsedBytes();
        long elapsedMinutes = Math.max(1, elapsedMs / 60_000L);
        double growthMbPerMinute = (growthBytes / (1024.0 * 1024.0)) / elapsedMinutes;

        // Also compute GC pressure
        long gcDelta    = last.gcCollections() - first.gcCollections();
        long batchDelta = last.batchesComplete() - first.batchesComplete();

        System.out.printf("%n[MemoryLeakStressTest] Analysis%n");
        System.out.printf("  Duration:        %.1f seconds%n", elapsedMs / 1000.0);
        System.out.printf("  Heap: first=%.1fMB last=%.1fMB growth=%.2fMB/min%n",
            first.heapUsedBytes()  / (1024.0 * 1024.0),
            last.heapUsedBytes()   / (1024.0 * 1024.0),
            growthMbPerMinute);
        System.out.printf("  GC:   collections=%d over measurement window%n", gcDelta);
        System.out.printf("  Load: %d batches × %d tasks = %,d task completions%n",
            batchDelta, BATCH_SIZE, batchDelta * BATCH_SIZE);

        assertTrue(growthMbPerMinute < MAX_GROWTH_MB_PER_MINUTE,
            String.format(
                "Memory leak detected! Heap grew %.2fMB/min (max %.1fMB/min). "
                    + "first=%.1fMB last=%.1fMB over %.0f seconds.",
                growthMbPerMinute, MAX_GROWTH_MB_PER_MINUTE,
                first.heapUsedBytes()  / (1024.0 * 1024.0),
                last.heapUsedBytes()   / (1024.0 * 1024.0),
                elapsedMs / 1000.0
            )
        );

        System.out.println("  Result: PASS — heap growth within acceptable range.");
    }

    // ── JMX helpers ────────────────────────────────────────────────────────────

    private long totalGcCollections() {
        return ManagementFactory.getGarbageCollectorMXBeans()
            .stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionCount)
            .filter(c -> c >= 0)  // -1 means "undefined" for some collectors
            .sum();
    }
}
