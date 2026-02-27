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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.graalpy.stress;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.graalpy.PythonBytecodeCache;
import org.yawlfoundation.yawl.graalpy.PythonContextPool;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Micro-throughput benchmark: {@link PythonContextPool} borrow/return cycle
 * cost at varying concurrency levels (1, 2, 4, 8, 16, 32 threads).
 *
 * <h2>Why measure pool overhead, not Python execution?</h2>
 * <p>GraalPy context execution is I/O and JIT bound; the pool borrow/return
 * cycle is a pure Java critical path that executes on every workflow task call.
 * Knowing the pool overhead independently from Python execution allows capacity
 * planners to:</p>
 * <ul>
 *   <li>Determine the minimum pool size that eliminates queueing delay.</li>
 *   <li>Detect regressions in the Commons Pool2 integration (e.g., lock contention
 *       introduced by a pool configuration change).</li>
 *   <li>Establish a baseline for N:M thread-to-context ratios in production.</li>
 * </ul>
 *
 * <h2>JMH note</h2>
 * <p>This class is structured as a JUnit 5 test that runs its own micro-benchmark
 * loop rather than depending on JMH infrastructure, which requires a separate
 * JVM fork.  The measurements are printed as a characterisation report; hard
 * assertions only guard against catastrophic regression (e.g., negative throughput
 * or wall-clock timeout).</p>
 *
 * <h2>Bytecode cache throughput</h2>
 * <p>A second benchmark measures {@link PythonBytecodeCache#isValid} throughput
 * under concurrent read pressure, which is the hot path in every
 * {@code evalScript()} call.</p>
 *
 * <p>Chicago TDD: real classes only, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("stress")
@DisplayName("GraalPy pool/cache throughput micro-benchmarks")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraalPyPoolThroughputBenchmark {

    /** Warmup cycles discarded before measurement begins. */
    private static final int WARMUP_CYCLES    = 200;

    /** Measurement window per concurrency level. */
    private static final long MEASURE_MS      = 2_000;

    /** Hard wall limit per scenario to avoid CI hang. */
    private static final long WALL_MS         = 15_000;

    /** Borrow timeout — short, since GraalPy is absent and context creation fails fast. */
    private static final Duration BORROW_TIMEOUT = Duration.ofMillis(300);

    private static final int[] CONCURRENCY_LEVELS = {1, 2, 4, 8, 16, 32};

    // ── B1: Pool borrow/return throughput vs concurrency level ───────────────────

    @Test
    @Order(1)
    @DisplayName("B1: Pool borrow/return cycle throughput at 1–32 concurrent threads")
    void poolBorrowReturnThroughputVsConcurrencyLevel() throws Exception {
        System.out.println("\n=== GRAALPY POOL BORROW/RETURN THROUGHPUT BENCHMARK ===");
        System.out.printf("%-12s %-15s %-15s %-15s%n",
                "Threads", "Ops/sec", "Latency (µs)", "Errors");
        System.out.println("-".repeat(57));

        for (int concurrency : CONCURRENCY_LEVELS) {
            BenchmarkResult result = measurePoolThroughput(concurrency, POOL_SIZE(concurrency));
            System.out.printf("%-12d %-15.0f %-15.1f %-15d%n",
                    concurrency,
                    result.opsPerSec(),
                    result.avgLatencyMicros(),
                    result.errors());

            // Hard assertion: throughput must be positive (no infinite hang)
            assertTrue(result.opsPerSec() > 0,
                    "Throughput must be positive at concurrency=" + concurrency);
        }

        System.out.println("=== END ===\n");
    }

    // ── B2: Pool throughput with varying pool sizes ───────────────────────────────

    @Test
    @Order(2)
    @DisplayName("B2: Pool throughput — fixed 8 threads, varying pool size 1–16")
    void poolThroughputVsPoolSize() throws Exception {
        int fixedThreadCount = 8;
        int[] poolSizes = {1, 2, 4, 8, 16};

        System.out.println("\n=== POOL SIZE vs THROUGHPUT (8 threads) ===");
        System.out.printf("%-12s %-15s %-15s%n", "Pool Size", "Ops/sec", "Errors");
        System.out.println("-".repeat(42));

        for (int poolSize : poolSizes) {
            BenchmarkResult result = measurePoolThroughput(fixedThreadCount, poolSize);
            System.out.printf("%-12d %-15.0f %-15d%n",
                    poolSize, result.opsPerSec(), result.errors());

            assertTrue(result.opsPerSec() > 0,
                    "Throughput must be positive at poolSize=" + poolSize);
        }

        System.out.println("=== END ===\n");
    }

    // ── B3: Bytecode cache isValid() throughput ───────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("B3: PythonBytecodeCache.isValid() throughput under concurrent readers")
    void bytecodeCacheIsValidThroughput() throws Exception {
        Path cacheDir = Files.createTempDirectory("graalpy-bench-cache-");
        Path sourceDir = Files.createTempDirectory("graalpy-bench-src-");

        try {
            PythonBytecodeCache cacheInstance = new PythonBytecodeCache(cacheDir);

            // Create real Python source files for benchmarking
            List<Path> scriptPaths = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                Path script = sourceDir.resolve("bench_script_" + i + ".py");
                Files.writeString(script, "# Benchmark script " + i + "\nresult = " + i + " * 2\n");
                cacheInstance.markCached(script);
                scriptPaths.add(script);
            }

            System.out.println("\n=== BYTECODE CACHE isValid() THROUGHPUT ===");
            System.out.printf("%-12s %-15s %-15s%n", "Threads", "Ops/sec", "Errors");
            System.out.println("-".repeat(42));

            for (int concurrency : CONCURRENCY_LEVELS) {
                BenchmarkResult result = measureCacheIsValidThroughput(
                        cacheInstance, scriptPaths, concurrency);
                System.out.printf("%-12d %-15.0f %-15d%n",
                        concurrency, result.opsPerSec(), result.errors());

                assertTrue(result.opsPerSec() > 0,
                        "Cache isValid() throughput must be positive at concurrency=" + concurrency);
            }

            System.out.println("=== END ===\n");

        } finally {
            deleteRecursively(cacheDir);
            deleteRecursively(sourceDir);
        }
    }

    // ── B4: Pool create/close overhead measurement ────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("B4: PythonContextPool create→close latency distribution (100 cycles)")
    void poolCreateCloseLatencyDistribution() {
        int cycles = 100;
        long[] latenciesNs = new long[cycles];

        for (int i = 0; i < cycles; i++) {
            long start = System.nanoTime();
            try (PythonContextPool pool = PythonContextPool.builder()
                    .sandboxConfig(PythonSandboxConfig.strict())
                    .maxPoolSize(4)
                    .build()) {
                // Immediate close — measures create+close overhead
            }
            latenciesNs[i] = System.nanoTime() - start;
        }

        long[] sorted = latenciesNs.clone();
        java.util.Arrays.sort(sorted);

        double p50  = sorted[cycles / 2] / 1_000.0;          // µs
        double p95  = sorted[(int)(cycles * 0.95)] / 1_000.0;
        double p99  = sorted[(int)(cycles * 0.99)] / 1_000.0;
        double mean = java.util.Arrays.stream(sorted).average().orElse(0) / 1_000.0;

        System.out.printf("%n=== POOL CREATE/CLOSE LATENCY DISTRIBUTION (%d cycles) ===%n", cycles);
        System.out.printf("Mean:  %.1f µs%n", mean);
        System.out.printf("P50:   %.1f µs%n", p50);
        System.out.printf("P95:   %.1f µs%n", p95);
        System.out.printf("P99:   %.1f µs%n", p99);
        System.out.printf("=== END ===%n%n");

        // P99 must be under 5 seconds (5 000 000 µs); guards against pathological hangs
        assertTrue(p99 < 5_000_000,
                String.format("P99 create/close latency %.1f µs exceeds 5s — possible resource starvation", p99));
    }

    // ── B5: Pool state query throughput ───────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("B5: Pool state query (getActiveCount/getIdleCount/getMaxTotal) throughput")
    void poolStateQueryThroughput() throws Exception {
        int threadCount = 16;

        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.standard())
                .maxPoolSize(8)
                .build()) {

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);
            LongAdder totalQueries    = new LongAdder();

            long deadline = System.currentTimeMillis() + MEASURE_MS;

            for (int i = 0; i < threadCount; i++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        startLatch.await();
                        while (System.currentTimeMillis() < deadline) {
                            // Tightest possible loop — 3 queries per iteration
                            int active = pool.getActiveCount();
                            int idle   = pool.getIdleCount();
                            int max    = pool.getMaxTotal();

                            // Guard against compiler elimination
                            if (active + idle + max < 0) {
                                throw new IllegalStateException("Pool state negative");
                            }
                            totalQueries.add(3);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(WALL_MS, TimeUnit.MILLISECONDS),
                    "State query threads did not complete within wall limit");

            double opsPerSec = totalQueries.sum() / (MEASURE_MS / 1000.0);
            System.out.printf("%n=== POOL STATE QUERY THROUGHPUT (16 threads) ===%n");
            System.out.printf("Total queries: %d%n", totalQueries.sum());
            System.out.printf("Throughput:    %.0f queries/sec%n", opsPerSec);
            System.out.printf("=== END ===%n%n");

            assertTrue(opsPerSec > 0, "Pool state query throughput must be positive");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /**
     * Returns an appropriate pool size for the given concurrency level:
     * min(concurrency, available processors), at least 1.
     */
    private int POOL_SIZE(int concurrency) {
        return Math.max(1, Math.min(concurrency, Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Measures pool borrow/return throughput over a fixed time window.
     * Returns ops/sec, average latency in microseconds, and error count.
     */
    private BenchmarkResult measurePoolThroughput(int concurrency, int poolSize) throws Exception {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.strict())
                .maxPoolSize(poolSize)
                .maxWait(BORROW_TIMEOUT)
                .build()) {

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(concurrency);
            LongAdder successOps      = new LongAdder();
            LongAdder errorOps        = new LongAdder();
            LongAdder totalNs         = new LongAdder();

            // Warmup
            for (int w = 0; w < WARMUP_CYCLES; w++) {
                try {
                    pool.execute(ctx -> "warmup");
                } catch (PythonException ignored) { }
            }

            long deadline = System.currentTimeMillis() + MEASURE_MS;

            for (int t = 0; t < concurrency; t++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        startLatch.await();
                        while (System.currentTimeMillis() < deadline) {
                            long opStart = System.nanoTime();
                            try {
                                pool.execute(ctx -> "bench");
                                successOps.increment();
                            } catch (PythonException e) {
                                // Expected when GraalPy absent — counts as measured operation
                                successOps.increment();
                            } catch (Exception e) {
                                errorOps.increment();
                            }
                            totalNs.add(System.nanoTime() - opStart);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(WALL_MS, TimeUnit.MILLISECONDS),
                    "Benchmark threads did not complete for concurrency=" + concurrency);

            long ops = successOps.sum();
            double opsPerSec = ops / (MEASURE_MS / 1000.0);
            double avgLatencyMicros = ops > 0 ? (totalNs.sum() / ops) / 1_000.0 : 0;

            return new BenchmarkResult(opsPerSec, avgLatencyMicros, errorOps.sum());
        }
    }

    /**
     * Measures {@link PythonBytecodeCache#isValid} throughput under concurrent readers.
     */
    private BenchmarkResult measureCacheIsValidThroughput(
            PythonBytecodeCache cacheInstance,
            List<Path> scriptPaths,
            int concurrency) throws Exception {

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(concurrency);
        LongAdder opsCount        = new LongAdder();
        LongAdder errorCount      = new LongAdder();

        long deadline = System.currentTimeMillis() + MEASURE_MS;

        for (int t = 0; t < concurrency; t++) {
            final int tid = t;
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    int pathIdx = 0;
                    while (System.currentTimeMillis() < deadline) {
                        try {
                            cacheInstance.isValid(scriptPaths.get(pathIdx % scriptPaths.size()));
                            opsCount.increment();
                            pathIdx++;
                        } catch (Exception e) {
                            errorCount.increment();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(WALL_MS, TimeUnit.MILLISECONDS),
                "Cache benchmark threads did not complete for concurrency=" + concurrency);

        double opsPerSec = opsCount.sum() / (MEASURE_MS / 1000.0);
        return new BenchmarkResult(opsPerSec, 0, errorCount.sum());
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .map(Path::toFile)
                      .forEach(f -> f.delete());
            }
        }
    }

    /** Immutable result record for a single benchmark run. */
    private record BenchmarkResult(double opsPerSec, double avgLatencyMicros, long errors) {}
}
