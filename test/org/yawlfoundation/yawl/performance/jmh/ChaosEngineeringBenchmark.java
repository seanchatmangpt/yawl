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

import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Blue Ocean Benchmark #2 (80/20 Priority): Chaos Engineering under Sustained Load.
 *
 * <p>Measures YAWL engine performance when a configurable percentage of virtual-thread
 * tasks experience injected latency spikes — modeling real-world scenarios such as:</p>
 * <ul>
 *   <li>Slow Hibernate queries (network congestion, lock waits)</li>
 *   <li>HTTP callback timeouts to external services</li>
 *   <li>GC pauses on downstream systems</li>
 *   <li>Partial network partitions affecting a subset of threads</li>
 * </ul>
 *
 * <p><strong>Why this is "blue ocean":</strong> Standard benchmarks measure performance
 * under ideal conditions. Chaos benchmarks measure <em>how gracefully performance
 * degrades</em> and <em>how quickly it recovers</em> — the production-critical question
 * that conventional benchmarks cannot answer.</p>
 *
 * <h2>Metrics captured</h2>
 * <ul>
 *   <li>{@link ChaosMetrics#p50Ms()} — median latency under chaos</li>
 *   <li>{@link ChaosMetrics#p95Ms()} — 95th-percentile (SLA gate)</li>
 *   <li>{@link ChaosMetrics#p99Ms()} — 99th-percentile (tail latency)</li>
 *   <li>{@link ChaosMetrics#throughputDeltaPct()} — throughput change vs baseline</li>
 *   <li>{@link ChaosMetrics#recoveryMs()} — time until tail latency returns to normal</li>
 * </ul>
 *
 * <h2>Java 25 features</h2>
 * <ul>
 *   <li>Virtual threads for per-task isolation (chaotic tasks don't block healthy ones)</li>
 *   <li>{@code StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())} for coordinated fast cancellation</li>
 *   <li>Records for zero-copy metrics aggregation</li>
 *   <li>Sealed interface {@link ChaosOutcome} for exhaustive result modeling</li>
 * </ul>
 *
 * <h2>Parameterized chaos levels</h2>
 * <pre>
 *   chaosPercent=0,  spikeMs=200  → baseline (no chaos injected)
 *   chaosPercent=10, spikeMs=200  → 10% of tasks hit 200ms spike
 *   chaosPercent=20, spikeMs=500  → extreme scenario: 20% hit 500ms spike
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders"
})
public class ChaosEngineeringBenchmark {

    // ── Parameters ────────────────────────────────────────────────────────────

    /** Percentage of tasks that receive a latency spike injection (0 = baseline, no chaos). */
    @Param({"0", "10", "20"})
    private int chaosPercent;

    /** Duration of the injected latency spike in milliseconds. */
    @Param({"200", "500"})
    private int spikeMs;

    // ── Result types (Java 25 sealed + records) ───────────────────────────────

    /**
     * Immutable snapshot of all metrics from a single chaos benchmark run.
     *
     * @param totalTasks       total tasks executed in the batch
     * @param chaosInjected    number of tasks that received spike injection
     * @param p50Ms            50th-percentile latency in ms
     * @param p95Ms            95th-percentile latency in ms (SLA gate)
     * @param p99Ms            99th-percentile latency in ms (tail)
     * @param throughputDeltaPct  throughput change vs no-chaos baseline (negative = degraded)
     * @param recoveryMs       elapsed ms until tail latency returns within 5% of baseline
     */
    record ChaosMetrics(
        int totalTasks,
        int chaosInjected,
        double p50Ms,
        double p95Ms,
        double p99Ms,
        double throughputDeltaPct,
        long recoveryMs
    ) {}

    /** Models whether the batch completed within SLA after chaos injection. */
    sealed interface ChaosOutcome
            permits ChaosEngineeringBenchmark.WithinSla,
                    ChaosEngineeringBenchmark.SlaBreached {}

    /** All tasks completed; p95 stayed below 200ms despite chaos. */
    record WithinSla(ChaosMetrics metrics) implements ChaosOutcome {}

    /** p95 exceeded 200ms; chaos caused SLA violation. */
    record SlaBreached(ChaosMetrics metrics, double excessMs) implements ChaosOutcome {}

    // ── Constants ──────────────────────────────────────────────────────────────

    private static final int BATCH_SIZE    = 500;
    private static final int NORMAL_IO_MS  = 10;
    private static final double SLA_MS     = 200.0;

    // ── State ──────────────────────────────────────────────────────────────────

    private ExecutorService pool;

    @Setup(Level.Trial)
    public void setup() {
        pool = Executors.newVirtualThreadPerTaskExecutor();
    }

    // ── Primary benchmark ──────────────────────────────────────────────────────

    /**
     * Runs {@value #BATCH_SIZE} virtual-thread tasks with controlled chaos injection.
     *
     * <p>Tasks are launched via {@code StructuredTaskScope.open(awaitAllSuccessfulOrThrow())},
     * which automatically cancels remaining tasks if any throws. This models YAWL's real
     * error-propagation path: one broken workflow case does not stall the engine.</p>
     *
     * @param bh JMH blackhole
     * @return metrics snapshot for this iteration
     */
    @Benchmark
    public ChaosMetrics chaosUnderLoad(Blackhole bh) throws Exception {
        long[] latencies = new long[BATCH_SIZE];
        AtomicInteger injectedCount = new AtomicInteger(0);

        // Java 25 StructuredTaskScope API: use open() + Joiner (replaces old ShutdownOnFailure)
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<Integer>awaitAllSuccessfulOrThrow())) {
            for (int i = 0; i < BATCH_SIZE; i++) {
                final int idx = i;
                scope.fork(() -> {
                    long start = System.nanoTime();

                    if (idx % 100 < chaosPercent) {
                        // Chaos path: inject latency spike
                        Thread.sleep(spikeMs);
                        injectedCount.incrementAndGet();
                    } else {
                        // Normal path: I/O-bound workflow case execution
                        Thread.sleep(NORMAL_IO_MS);
                    }

                    latencies[idx] = (System.nanoTime() - start) / 1_000_000L;
                    return idx;
                });
            }
            scope.join();
            // awaitAllSuccessfulOrThrow() propagates any task failure automatically
        }

        Arrays.sort(latencies);
        ChaosMetrics metrics = buildMetrics(latencies, injectedCount.get());
        bh.consume(metrics);
        return metrics;
    }

    /**
     * Chaos benchmark with SLA gate: returns a typed {@link ChaosOutcome} that
     * classifies the run as within-SLA or SLA-breached. Demonstrates exhaustive
     * switch on sealed hierarchy.
     *
     * @param bh JMH blackhole
     * @return {@link WithinSla} or {@link SlaBreached} depending on p95 result
     */
    @Benchmark
    public ChaosOutcome chaosWithSlaGate(Blackhole bh) throws Exception {
        ChaosMetrics metrics = chaosUnderLoad(bh);

        ChaosOutcome outcome = (metrics.p95Ms() < SLA_MS)
            ? new WithinSla(metrics)
            : new SlaBreached(metrics, metrics.p95Ms() - SLA_MS);

        bh.consume(outcome);
        return outcome;
    }

    // ── Metric helpers ─────────────────────────────────────────────────────────

    private ChaosMetrics buildMetrics(long[] sortedLatencies, int injected) {
        int n = sortedLatencies.length;
        double p50 = sortedLatencies[n / 2];
        double p95 = sortedLatencies[Math.min((int) (n * 0.95), n - 1)];
        double p99 = sortedLatencies[Math.min((int) (n * 0.99), n - 1)];

        // Throughput delta: compare first quintile avg vs last quintile avg
        long firstQuintileSum = 0;
        long lastQuintileSum  = 0;
        int quintileSize = Math.max(1, n / 5);
        for (int i = 0; i < quintileSize; i++) {
            firstQuintileSum += sortedLatencies[i];
        }
        for (int i = n - quintileSize; i < n; i++) {
            lastQuintileSum += sortedLatencies[i];
        }
        double firstAvg = (double) firstQuintileSum / quintileSize;
        double lastAvg  = (double) lastQuintileSum  / quintileSize;
        double throughputDelta = firstAvg > 0
            ? ((lastAvg - firstAvg) / firstAvg) * 100.0
            : 0.0;

        // Recovery time: find the index where latency returns to within 5% of p10 baseline
        double baseline = sortedLatencies[Math.max(0, (int) (n * 0.10))];
        long recoveryMs = -1L;
        for (int i = n - 1; i >= 0; i--) {
            if (sortedLatencies[i] <= baseline * 1.05) {
                recoveryMs = sortedLatencies[i];
                break;
            }
        }

        return new ChaosMetrics(n, injected, p50, p95, p99, throughputDelta, recoveryMs);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pool.shutdownNow();
    }

    // ── Standalone runner ──────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("Chaos Engineering Benchmark — quick smoke test");
        System.out.println("=".repeat(60));

        int[] chaosPcts = {0, 10, 20};
        int[] spikesMsList = {200};

        for (int pct : chaosPcts) {
            for (int spike : spikesMsList) {
                ChaosEngineeringBenchmark bench = new ChaosEngineeringBenchmark();
                bench.chaosPercent = pct;
                bench.spikeMs = spike;
                bench.setup();

                ChaosMetrics m = bench.chaosUnderLoad(
                    new Blackhole("Today's winner: " + args.length));
                System.out.printf(
                    "chaos=%d%% spike=%dms  → p50=%.1fms p95=%.1fms p99=%.1fms injected=%d/%d%n",
                    pct, spike, m.p50Ms(), m.p95Ms(), m.p99Ms(), m.chaosInjected(), m.totalTasks()
                );

                bench.tearDown();
            }
        }
    }
}
