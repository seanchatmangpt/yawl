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

package org.yawlfoundation.yawl.performance.jmh;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Blue Ocean Benchmark #3: Property-Based Performance Testing.
 *
 * <p>Adapts the <strong>QuickCheck / property-based testing paradigm</strong> to
 * performance measurement. Instead of testing at fixed concurrency levels
 * (10, 100, 500), this benchmark validates that performance <em>invariants</em>
 * hold across randomized input shapes.</p>
 *
 * <p><strong>Why this is "blue ocean":</strong> Conventional JMH benchmarks measure
 * throughput at predetermined operating points. Property-based performance testing
 * asks a qualitatively different question: <em>"Does this invariant hold regardless
 * of what input we give it?"</em> This approach surfaces performance cliffs,
 * non-linearities, and edge-case regressions that fixed-param benchmarks miss.</p>
 *
 * <h2>Performance invariants tested</h2>
 * <ol>
 *   <li><strong>P1 — Throughput Scaling</strong>: Throughput must not degrade more than
 *       5% when concurrency doubles. Virtual threads should scale near-linearly with
 *       I/O-bound workloads.</li>
 *   <li><strong>P2 — Latency Bound</strong>: p95 latency must stay below 200ms across
 *       all randomly-chosen concurrency levels in [10, 500] and I/O latencies in
 *       [5, 25]ms. This is the YAWL SLA for work-item checkout.</li>
 *   <li><strong>P3 — Memory Efficiency</strong>: Memory cost per active virtual thread
 *       must not exceed 100KB (platform threads cost ~1MB each; virtual threads ~1KB).
 *       Measured via Runtime.totalMemory() delta across batch sizes.</li>
 * </ol>
 *
 * <h2>Randomization</h2>
 * <ul>
 *   <li>Fixed seed (42) for determinism: same "random" inputs across benchmark runs</li>
 *   <li>Each benchmark iteration tests 10 random input combinations</li>
 *   <li>Failed invariants are recorded as {@link InvariantResult.Violated} records</li>
 * </ul>
 *
 * <h2>Java 25 features</h2>
 * <ul>
 *   <li>Sealed interface {@link InvariantResult} for type-safe pass/fail reporting</li>
 *   <li>Virtual threads via {@code newVirtualThreadPerTaskExecutor()}</li>
 *   <li>Records for zero-allocation result capture</li>
 *   <li>Pattern matching switch for result dispatch</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders"
})
public class PropertyBasedPerformanceBenchmark {

    // ── Result types (Java 25 sealed + records) ───────────────────────────────

    /**
     * Result of testing a single performance invariant.
     * Sealed so that callers can use exhaustive switch without {@code default}.
     */
    sealed interface InvariantResult
            permits PropertyBasedPerformanceBenchmark.Holds,
                    PropertyBasedPerformanceBenchmark.Violated {}

    /**
     * The invariant held for all tested inputs.
     *
     * @param invariantId  short identifier (e.g., "P1-ThroughputScaling")
     * @param trialsRun    number of random input combinations tested
     * @param worstCase    worst measured value (closest to violation threshold)
     */
    record Holds(String invariantId, int trialsRun, double worstCase) implements InvariantResult {}

    /**
     * The invariant was violated by at least one input.
     *
     * @param invariantId  short identifier
     * @param measured     the value that violated the threshold
     * @param threshold    the threshold that was exceeded
     * @param details      human-readable description of the violating input
     */
    record Violated(String invariantId, double measured, double threshold, String details)
            implements InvariantResult {}

    // ── Constants ──────────────────────────────────────────────────────────────

    private static final int    TRIALS_PER_PROPERTY    = 10;
    private static final double P1_MAX_THROUGHPUT_DROP = 0.05;  // 5% max degradation on 2x concurrency
    private static final double P2_LATENCY_SLA_MS      = 200.0;
    private static final double P3_MAX_MEMORY_PER_THREAD_KB = 100.0;

    // ── State ──────────────────────────────────────────────────────────────────

    private ExecutorService pool;
    private Random rng;

    @Setup(Level.Trial)
    public void setup() {
        pool = Executors.newVirtualThreadPerTaskExecutor();
        rng  = new Random(42L); // Fixed seed: deterministic "random" inputs
    }

    // ── Property P1: Throughput Scaling ───────────────────────────────────────

    /**
     * Validates P1: throughput must not drop more than 5% when concurrency doubles.
     *
     * <p>Tests {@value #TRIALS_PER_PROPERTY} random starting concurrency levels.
     * For each, measures throughput at C and 2C concurrency; flags any pair where
     * the ratio drops below 0.95.</p>
     *
     * @param bh JMH blackhole
     * @return {@link Holds} if P1 holds across all trials, {@link Violated} otherwise
     */
    @Benchmark
    public InvariantResult validateThroughputScalingP1(Blackhole bh) throws Exception {
        double worstDrop = 0.0;

        for (int trial = 0; trial < TRIALS_PER_PROPERTY; trial++) {
            int baseC   = 20 + rng.nextInt(80);   // [20, 100)
            int doubleC = baseC * 2;

            double tBase   = measureThroughput(baseC,   5 + rng.nextInt(10));
            double tDouble = measureThroughput(doubleC, 5 + rng.nextInt(10));

            double drop = (tBase > 0) ? (tBase - tDouble) / tBase : 0.0;
            worstDrop = Math.max(worstDrop, drop);

            if (drop > P1_MAX_THROUGHPUT_DROP) {
                InvariantResult result = new Violated("P1-ThroughputScaling",
                    drop, P1_MAX_THROUGHPUT_DROP,
                    "Concurrency doubled " + baseC + " -> " + doubleC
                        + ": throughput dropped " + String.format("%.1f%%", drop * 100));
                bh.consume(result);
                return result;
            }
        }

        InvariantResult result = new Holds("P1-ThroughputScaling", TRIALS_PER_PROPERTY, worstDrop);
        bh.consume(result);
        return result;
    }

    // ── Property P2: Latency Bound ────────────────────────────────────────────

    /**
     * Validates P2: p95 latency must stay below {@value #P2_LATENCY_SLA_MS}ms
     * for all randomly-chosen (concurrency, ioLatencyMs) pairs.
     *
     * <p>Concurrency drawn from [10, 500]; I/O latency from [5, 25]ms.
     * Together these cover the expected operating envelope for YAWL
     * work-item checkout/checkin operations.</p>
     *
     * @param bh JMH blackhole
     * @return {@link Holds} or {@link Violated}
     */
    @Benchmark
    public InvariantResult validateLatencyBoundP2(Blackhole bh) throws Exception {
        double worstP95 = 0.0;

        for (int trial = 0; trial < TRIALS_PER_PROPERTY; trial++) {
            int concurrency = 10 + rng.nextInt(491);  // [10, 500]
            int ioMs        = 5  + rng.nextInt(21);   // [5, 25]

            double p95 = measureP95(concurrency, ioMs);
            worstP95 = Math.max(worstP95, p95);

            if (p95 > P2_LATENCY_SLA_MS) {
                InvariantResult result = new Violated("P2-LatencyBound",
                    p95, P2_LATENCY_SLA_MS,
                    "concurrency=" + concurrency + " ioMs=" + ioMs
                        + " -> p95=" + String.format("%.1f", p95) + "ms");
                bh.consume(result);
                return result;
            }
        }

        InvariantResult result = new Holds("P2-LatencyBound", TRIALS_PER_PROPERTY, worstP95);
        bh.consume(result);
        return result;
    }

    // ── Property P3: Memory Efficiency ────────────────────────────────────────

    /**
     * Validates P3: memory overhead per concurrent virtual thread must stay
     * below {@value #P3_MAX_MEMORY_PER_THREAD_KB}KB.
     *
     * <p>Platform threads require ~1MB of stack each; virtual threads should
     * require only ~1KB. This property test ensures that creating N virtual threads
     * does not consume more than N * 100KB of additional heap.</p>
     *
     * @param bh JMH blackhole
     * @return {@link Holds} or {@link Violated}
     */
    @Benchmark
    public InvariantResult validateMemoryEfficiencyP3(Blackhole bh) throws Exception {
        Runtime rt = Runtime.getRuntime();
        double worstKbPerThread = 0.0;

        for (int trial = 0; trial < TRIALS_PER_PROPERTY; trial++) {
            int threadCount = 100 + rng.nextInt(401);  // [100, 500]

            System.gc();
            Thread.sleep(10);  // Allow GC to settle
            long memBefore = rt.totalMemory() - rt.freeMemory();

            // Create and complete threadCount virtual threads
            CountDownLatch latch = new CountDownLatch(threadCount);
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < threadCount; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            long memDuring = rt.totalMemory() - rt.freeMemory();
            start.countDown();
            latch.await(30, TimeUnit.SECONDS);

            long deltaBytes  = Math.max(0, memDuring - memBefore);
            double kbPerThread = (deltaBytes / 1024.0) / threadCount;
            worstKbPerThread = Math.max(worstKbPerThread, kbPerThread);

            if (kbPerThread > P3_MAX_MEMORY_PER_THREAD_KB) {
                InvariantResult result = new Violated("P3-MemoryEfficiency",
                    kbPerThread, P3_MAX_MEMORY_PER_THREAD_KB,
                    "threadCount=" + threadCount
                        + " -> " + String.format("%.1f", kbPerThread) + "KB/thread");
                bh.consume(result);
                return result;
            }
        }

        InvariantResult result = new Holds("P3-MemoryEfficiency", TRIALS_PER_PROPERTY,
            worstKbPerThread);
        bh.consume(result);
        return result;
    }

    // ── Combined invariant suite ───────────────────────────────────────────────

    /**
     * Runs all three properties in sequence and returns aggregated results.
     * This is the benchmark variant most useful for CI regression detection.
     *
     * @param bh JMH blackhole
     * @return list of invariant results (one per property)
     */
    @Benchmark
    public List<InvariantResult> validateAllProperties(Blackhole bh) throws Exception {
        List<InvariantResult> results = new ArrayList<>(3);
        results.add(validateThroughputScalingP1(bh));
        results.add(validateLatencyBoundP2(bh));
        results.add(validateMemoryEfficiencyP3(bh));
        bh.consume(results);
        return results;
    }

    // ── Measurement helpers ────────────────────────────────────────────────────

    private double measureThroughput(int concurrency, int ioMs) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(concurrency);
        long start = System.nanoTime();

        for (int i = 0; i < concurrency; i++) {
            pool.execute(() -> {
                try {
                    Thread.sleep(ioMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        long elapsed = System.nanoTime() - start;
        return (elapsed > 0) ? (concurrency * 1_000_000_000.0) / elapsed : 0.0;
    }

    private double measureP95(int concurrency, int ioMs) throws InterruptedException {
        long[] latencies = new long[concurrency];
        CountDownLatch latch = new CountDownLatch(concurrency);

        for (int i = 0; i < concurrency; i++) {
            final int idx = i;
            pool.execute(() -> {
                long t = System.nanoTime();
                try {
                    Thread.sleep(ioMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latencies[idx] = (System.nanoTime() - t) / 1_000_000L;
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        Arrays.sort(latencies);
        int p95Index = Math.min((int) (concurrency * 0.95), latencies.length - 1);
        return latencies[p95Index];
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pool.shutdownNow();
    }

    // ── Standalone runner ──────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("Property-Based Performance Benchmark — quick validation");
        System.out.println("=".repeat(60));

        PropertyBasedPerformanceBenchmark bench = new PropertyBasedPerformanceBenchmark();
        bench.setup();

        Blackhole bh = new Blackhole("Today's winner: " + args.length);

        List<InvariantResult> results = bench.validateAllProperties(bh);
        for (InvariantResult r : results) {
            String line = switch (r) {
                case Holds h    -> "PASS  " + h.invariantId()
                                    + " (" + h.trialsRun() + " trials, worst="
                                    + String.format("%.3f", h.worstCase()) + ")";
                case Violated v -> "FAIL  " + v.invariantId()
                                    + " measured=" + String.format("%.3f", v.measured())
                                    + " threshold=" + String.format("%.3f", v.threshold())
                                    + " :: " + v.details();
            };
            System.out.println(line);
        }

        bench.tearDown();
    }
}
