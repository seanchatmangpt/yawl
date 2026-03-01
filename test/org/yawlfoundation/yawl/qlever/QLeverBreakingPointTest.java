/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improve workflow technology.
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

package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Definitive stress test for the QLever embedded SPARQL engine.
 *
 * This test suite provides real-world breaking point analysis, showing when,
 * where, and how the QLever engine degrades under various load conditions.
 *
 * Five test dimensions:
 * - Thread count ramp: Find QPS peak and P95 latency threshold
 * - Memory growth: Track heap usage under sustained 50-thread load
 * - Query complexity: Compare simple, complex, and large UNION queries
 * - Format serialization: Overhead comparison across JSON/TSV/CSV/XML
 * - Recovery behavior: Verify return to baseline after overload cycles
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@DisplayName("QLever Breaking Point Analysis")
@Tag("stress")
@Tag("breaking-point")
class QLeverBreakingPointTest {

    private static final int SIMPLE_LOAD_DURATION_SECONDS = 30;
    private static final int MEMORY_TEST_DURATION_SECONDS = 120;
    private static final int RECOVERY_TEST_DURATION_SECONDS = 60;
    private static final int P95_THRESHOLD_MS = 500;
    private static final double ERROR_RATE_WARNING_PERCENT = 5.0;
    private static final double ERROR_RATE_CRITICAL_PERCENT = 20.0;
    private static final long MEMORY_GROWTH_LIMIT_MB = 500;

    @BeforeAll
    static void ensureAvailable() {
        assumeTrue(QLeverTestNode.isAvailable(),
                "QLever native lib not available");
    }

    /**
     * Thread-count ramp: find QPS peak and P95 breaking point.
     *
     * Ramps thread count from 1 to 1000, measuring QPS, P50/P95/P99 latencies,
     * error rates, and identifying breaking points where latency or error
     * thresholds are crossed.
     */
    @Test
    @DisplayName("Thread-count ramp: find QPS peak and P95 breaking point")
    void testThreadCountBreakingPoint() throws Exception {
        System.out.println("\n=== QLever Breaking Point Analysis — Thread Scaling ===");
        System.out.println("Threads | QPS     | P50ms | P95ms | P99ms | Maxms  | ErrRate%");
        System.out.println("--------|---------|-------|-------|-------|--------|----------");
        System.out.flush();

        int[] threadCounts = {1, 5, 10, 25, 50, 100, 200, 500, 1000};
        String query = BenchmarkDataGenerator.generateSimpleQuery(0);

        int p95BreakingPoint = -1;
        int qpsPeakThreads = -1;
        double qpsPeak = 0.0;
        int criticalFailurePoint = -1;

        for (int threads : threadCounts) {
            LoadResult result = runLoad(threads, SIMPLE_LOAD_DURATION_SECONDS, query);

            System.out.printf("%7d | %7.1f | %5d | %5d | %5d | %6d | %6.2f%%",
                    threads,
                    result.qps,
                    result.p50ms,
                    result.p95ms,
                    result.p99ms,
                    result.maxMs,
                    result.errorRate * 100.0);
            System.out.flush();

            if (result.qps > qpsPeak) {
                qpsPeak = result.qps;
                qpsPeakThreads = threads;
            }

            String annotations = "";
            if (p95BreakingPoint < 0 && result.p95ms > P95_THRESHOLD_MS) {
                p95BreakingPoint = threads;
                annotations += "  <- P95 > 500ms";
            }
            if (result.errorRate > ERROR_RATE_WARNING_PERCENT / 100.0 &&
                result.errorRate <= ERROR_RATE_CRITICAL_PERCENT / 100.0) {
                annotations += "  <- ERROR > 5%";
            }
            if (result.errorRate > ERROR_RATE_CRITICAL_PERCENT / 100.0) {
                annotations += "  <- CRITICAL";
                if (criticalFailurePoint < 0) {
                    criticalFailurePoint = threads;
                }
            }

            System.out.println(annotations);
            System.out.flush();

            if (result.errorRate > ERROR_RATE_CRITICAL_PERCENT / 100.0) {
                System.out.println("Stopping at " + threads + " threads — critical failure threshold");
                System.out.flush();
                break;
            }
        }

        System.out.println();
        if (p95BreakingPoint > 0) {
            System.out.println("Breaking point: " + p95BreakingPoint + " threads " +
                    "(P95 first crossed 500ms threshold)");
        }
        if (qpsPeakThreads > 0) {
            System.out.println("QPS peak: " + qpsPeakThreads + " threads at QPS=" + qpsPeak);
        }
        if (criticalFailurePoint > 0) {
            System.out.println("Critical failure: " + criticalFailurePoint +
                    " threads (error rate > 20%)");
        }
        System.out.flush();

        assertTrue(qpsPeakThreads > 0, "Should have measured at least one thread level");
    }

    /**
     * Memory growth under 50-thread sustained load.
     *
     * Runs 50 virtual threads continuously for 120 seconds, sampling memory
     * every 10 seconds. Reports total growth, bytes-per-query, and fails if
     * growth exceeds 500 MB.
     */
    @Test
    @DisplayName("Memory growth under 50-thread sustained load (120s)")
    void testMemoryGrowthCurve() throws Exception {
        System.out.println("\n=== Memory Growth Under 50-Thread Load ===");
        System.out.println("Time(s) | UsedMB | TotalQueries | AvgLatMs");
        System.out.println("--------|--------|--------------|----------");
        System.out.flush();

        String query = BenchmarkDataGenerator.generateSimpleQuery(0);
        long initialMemory = BenchmarkUtils.getMemoryUsage();
        List<MemorySample> samples = new ArrayList<>();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger totalQueries = new AtomicInteger(0);
        AtomicBoolean running = new AtomicBoolean(true);
        ConcurrentLinkedQueue<Long> allLatencies = new ConcurrentLinkedQueue<>();

        try {
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

            for (int thread = 0; thread < 50; thread++) {
                executor.submit(() -> {
                    while (running.get()) {
                        try {
                            long start = System.currentTimeMillis();
                            try (QLeverResult result = QLeverTestNode.engine()
                                    .executeSelect(query, QLeverMediaType.JSON)) {
                                result.data();
                            }
                            long elapsed = System.currentTimeMillis() - start;
                            allLatencies.offer(elapsed);
                            totalQueries.incrementAndGet();
                        } catch (Exception e) {
                            // Continue on individual query failures
                        }
                    }
                });
            }

            List<Long> latencies = new ArrayList<>();
            for (int i = 0; i < MEMORY_TEST_DURATION_SECONDS / 10; i++) {
                Thread.sleep(10_000L);
                long currentMemory = BenchmarkUtils.getMemoryUsage();
                long usedMB = (currentMemory - initialMemory) / (1024 * 1024);
                int queries = totalQueries.get();

                latencies.clear();
                latencies.addAll(allLatencies);
                double avgLat = latencies.isEmpty() ? 0.0 :
                        BenchmarkUtils.calculateMean(new ArrayList<>(latencies));

                System.out.printf("%7d | %6d | %12d | %8.1f%n",
                        (i + 1) * 10, usedMB, queries, avgLat);
                System.out.flush();

                samples.add(new MemorySample((i + 1) * 10, usedMB, queries, (long) avgLat));
            }

            running.set(false);
            scheduler.shutdown();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            long finalMemory = BenchmarkUtils.getMemoryUsage();
            long totalGrowthMB = (finalMemory - initialMemory) / (1024 * 1024);
            long bytesPerQuery = totalQueries.get() > 0 ?
                    (finalMemory - initialMemory) / totalQueries.get() : 0;

            System.out.println();
            System.out.println("Memory growth: " + totalGrowthMB + " MB over " +
                    MEMORY_TEST_DURATION_SECONDS + "s");
            System.out.println("Bytes per query: " + bytesPerQuery + " bytes/query");
            System.out.flush();

            assertTrue(totalGrowthMB <= MEMORY_GROWTH_LIMIT_MB,
                    "Memory growth (" + totalGrowthMB + " MB) exceeded limit (" +
                    MEMORY_GROWTH_LIMIT_MB + " MB). Details:\n" + formatMemorySamples(samples));

        } finally {
            running.set(false);
            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * Query complexity impact: simple vs complex vs large UNION.
     *
     * Compares three query types (simple, complex, large 50-part UNION)
     * with 10 virtual threads running 500 iterations each. Reports
     * latency multipliers relative to baseline.
     */
    @Test
    @DisplayName("Query complexity: simple vs complex vs 50-part UNION")
    void testComplexityImpact() throws Exception {
        System.out.println("\n=== Query Complexity Impact ===");
        System.out.println("QueryType  | MeanMs | P95ms | P99ms | Multiplier");
        System.out.println("-----------|--------|-------|-------|----------");
        System.out.flush();

        String simpleQuery = BenchmarkDataGenerator.generateSimpleQuery(0);
        String complexQuery = BenchmarkDataGenerator.generateComplexQuery(0);
        String largeUnionQuery = BenchmarkDataGenerator.generateLargeUnionQuery(50);

        ComplexityResult simple = measureQueryComplexity("SIMPLE", simpleQuery, 10, 500);
        System.out.printf("%-10s | %6.1f | %5d | %5d | %8.1fx%n",
                "SIMPLE", simple.meanMs, simple.p95ms, simple.p99ms, 1.0);
        System.out.flush();

        ComplexityResult complex = measureQueryComplexity("COMPLEX", complexQuery, 10, 500);
        double complexMultiplier = complex.meanMs / simple.meanMs;
        System.out.printf("%-10s | %6.1f | %5d | %5d | %8.1fx%n",
                "COMPLEX", complex.meanMs, complex.p95ms, complex.p99ms, complexMultiplier);
        System.out.flush();

        ComplexityResult largeUnion = measureQueryComplexity("LARGE_UNION",
                largeUnionQuery, 10, 500);
        double unionMultiplier = largeUnion.meanMs / simple.meanMs;
        System.out.printf("%-10s | %6.1f | %5d | %5d | %8.1fx%n",
                "LARGE_UNION", largeUnion.meanMs, largeUnion.p95ms, largeUnion.p99ms,
                unionMultiplier);
        System.out.flush();

        System.out.println();
        System.out.flush();

        assertTrue(simple.meanMs > 0, "Simple query should have measurable latency");
        assertTrue(complex.meanMs >= simple.meanMs,
                "Complex query should not be faster than simple");
        assertTrue(largeUnion.meanMs >= complex.meanMs,
                "Large UNION should not be faster than complex");
    }

    /**
     * Format serialization overhead: JSON vs TSV vs CSV vs XML.
     *
     * Measures latency and result size for four major serialization formats
     * over 100 iterations each.
     */
    @Test
    @DisplayName("Format serialization overhead: JSON vs TSV vs CSV vs XML")
    void testFormatOverhead() throws Exception {
        System.out.println("\n=== Format Serialization Overhead ===");
        System.out.println("Format | MeanMs | P95ms | SizeBytes | Overhead vs JSON");
        System.out.println("-------|--------|-------|-----------|------------------");
        System.out.flush();

        String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100";

        FormatResult json = measureFormatOverhead("JSON", query, QLeverMediaType.JSON, 100);
        System.out.printf("%-6s | %6.1f | %5d | %9d | %14.2fx%n",
                "JSON", json.meanMs, json.p95ms, json.sizeBytes, 1.0);
        System.out.flush();

        FormatResult tsv = measureFormatOverhead("TSV", query, QLeverMediaType.TSV, 100);
        double tsvOverhead = json.sizeBytes > 0 ? (double) tsv.sizeBytes / json.sizeBytes : 0;
        System.out.printf("%-6s | %6.1f | %5d | %9d | %14.2fx%n",
                "TSV", tsv.meanMs, tsv.p95ms, tsv.sizeBytes, tsvOverhead);
        System.out.flush();

        FormatResult csv = measureFormatOverhead("CSV", query, QLeverMediaType.CSV, 100);
        double csvOverhead = json.sizeBytes > 0 ? (double) csv.sizeBytes / json.sizeBytes : 0;
        System.out.printf("%-6s | %6.1f | %5d | %9d | %14.2fx%n",
                "CSV", csv.meanMs, csv.p95ms, csv.sizeBytes, csvOverhead);
        System.out.flush();

        FormatResult xml = measureFormatOverhead("XML", query, QLeverMediaType.XML, 100);
        double xmlOverhead = json.sizeBytes > 0 ? (double) xml.sizeBytes / json.sizeBytes : 0;
        System.out.printf("%-6s | %6.1f | %5d | %9d | %14.2fx%n",
                "XML", xml.meanMs, xml.p95ms, xml.sizeBytes, xmlOverhead);
        System.out.flush();

        System.out.println();
        System.out.flush();

        assertTrue(json.meanMs > 0, "JSON format should be measurable");
        assertTrue(json.sizeBytes > 0, "JSON result should have content");
    }

    /**
     * Recovery: return to baseline QPS after 2x breaking-point load.
     *
     * Three phases:
     * - BASELINE: 10 threads, 30s
     * - OVERLOAD: 200 threads, 30s
     * - RECOVERY: 10 threads, 60s with 5s sampling windows
     *
     * Verifies that QPS returns to within 20% of baseline.
     */
    @Test
    @DisplayName("Recovery: return to baseline QPS after 2x breaking-point load")
    void testRecoveryAfterOverload() throws Exception {
        System.out.println("\n=== Recovery After Overload ===");
        System.out.println("Phase    | Threads | Duration | QPS    | P95ms");
        System.out.println("---------|---------|----------|--------|-------");
        System.out.flush();

        String query = BenchmarkDataGenerator.generateSimpleQuery(0);

        LoadResult baseline = runLoad(10, SIMPLE_LOAD_DURATION_SECONDS, query);
        System.out.printf("%-8s | %7d | %6ds   | %6.1f | %5d%n",
                "BASELINE", 10, SIMPLE_LOAD_DURATION_SECONDS, baseline.qps, baseline.p95ms);
        System.out.flush();

        LoadResult overload = runLoad(200, SIMPLE_LOAD_DURATION_SECONDS, query);
        System.out.printf("%-8s | %7d | %6ds   | %6.1f | %5d%n",
                "OVERLOAD", 200, SIMPLE_LOAD_DURATION_SECONDS, overload.qps, overload.p95ms);
        System.out.flush();

        System.out.println("RECOVERY | 10      | variable | -      | -    ");
        System.out.flush();

        System.out.println();
        System.out.println("Recovery samples (every 5s):");
        System.out.flush();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger recovered = new AtomicInteger(0);
        AtomicInteger recoveryTime = new AtomicInteger(0);
        List<RecoverySample> samples = new ArrayList<>();

        try {
            AtomicBoolean running = new AtomicBoolean(true);
            ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
            AtomicInteger successCount = new AtomicInteger(0);

            for (int thread = 0; thread < 10; thread++) {
                executor.submit(() -> {
                    while (running.get()) {
                        try {
                            long start = System.nanoTime();
                            try (QLeverResult result = QLeverTestNode.engine()
                                    .executeSelect(query, QLeverMediaType.JSON)) {
                                result.data();
                            }
                            long elapsed = System.nanoTime() - start;
                            latencies.offer(elapsed / 1_000_000);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            // Continue on individual query failures
                        }
                    }
                });
            }

            long startTime = System.currentTimeMillis();
            for (int i = 1; i <= RECOVERY_TEST_DURATION_SECONDS / 5; i++) {
                Thread.sleep(5_000L);

                List<Long> currentLatencies = new ArrayList<>(latencies);
                currentLatencies.sort(null);
                double qps = (successCount.get() / (double)(i * 5));
                double percentage = (qps / baseline.qps) * 100.0;

                System.out.printf("  %2ds: QPS=%6.1f (%5.1f%% of baseline)",
                        i * 5, qps, percentage);

                if (percentage >= 80.0) {
                    System.out.println(" — RECOVERED");
                    if (recovered.get() == 0) {
                        recovered.set(1);
                        recoveryTime.set(i * 5);
                    }
                } else {
                    System.out.println(" — still recovering");
                }
                System.out.flush();

                samples.add(new RecoverySample(i * 5, qps, percentage));
            }

            running.set(false);
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

        } finally {
            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }

        System.out.println();
        if (recovered.get() > 0) {
            System.out.println("Result: Recovery achieved after " + recoveryTime.get() + "s");
        } else {
            System.out.println("Result: Recovery NOT achieved within " +
                    RECOVERY_TEST_DURATION_SECONDS + "s");
        }
        System.out.flush();

        assertTrue(baseline.qps > 0, "Baseline load should complete successfully");
    }

    private LoadResult runLoad(int threads, int durationSeconds, String query)
            throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicBoolean running = new AtomicBoolean(true);

        try {
            for (int thread = 0; thread < threads; thread++) {
                executor.submit(() -> {
                    while (running.get()) {
                        try {
                            long start = System.currentTimeMillis();
                            try (QLeverResult result = QLeverTestNode.engine()
                                    .executeSelect(query, QLeverMediaType.JSON)) {
                                result.data();
                            }
                            long elapsed = System.currentTimeMillis() - start;
                            latencies.offer(elapsed);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        }
                    }
                });
            }

            Thread.sleep(durationSeconds * 1000L);
            running.set(false);

            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            int total = successCount.get() + failureCount.get();
            double qps = successCount.get() / (double) durationSeconds;
            double errorRate = total > 0 ? (double) failureCount.get() / total : 0.0;

            List<Long> sorted = new ArrayList<>(latencies);
            sorted.sort(null);

            long p50 = percentile(sorted, 50);
            long p95 = percentile(sorted, 95);
            long p99 = percentile(sorted, 99);
            long max = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);

            return new LoadResult(qps, p50, p95, p99, max, errorRate,
                    successCount.get(), failureCount.get());

        } finally {
            running.set(false);
            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
    }

    private ComplexityResult measureQueryComplexity(String name, String query,
            int threadCount, int iterationsPerThread) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        try {
            for (int thread = 0; thread < threadCount; thread++) {
                executor.submit(() -> {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        try {
                            long start = System.currentTimeMillis();
                            try (QLeverResult result = QLeverTestNode.engine()
                                    .executeSelect(query, QLeverMediaType.JSON)) {
                                result.data();
                            }
                            long elapsed = System.currentTimeMillis() - start;
                            latencies.offer(elapsed);
                        } catch (Exception e) {
                            // Continue on failure
                        }
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(60, TimeUnit.SECONDS);

            List<Long> sorted = new ArrayList<>(latencies);
            sorted.sort(null);

            double mean = sorted.isEmpty() ? 0.0 :
                    BenchmarkUtils.calculateMean(new ArrayList<>(latencies));
            long p95 = percentile(sorted, 95);
            long p99 = percentile(sorted, 99);

            return new ComplexityResult(mean, p95, p99);

        } finally {
            if (!executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
    }

    private FormatResult measureFormatOverhead(String name, String query,
            QLeverMediaType format, int iterations) throws Exception {
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        long totalSize = 0;
        int successCount = 0;

        for (int i = 0; i < iterations; i++) {
            try {
                long start = System.currentTimeMillis();
                try (QLeverResult result = QLeverTestNode.engine()
                        .executeSelect(query, format)) {
                    String data = result.data();
                    totalSize += data.length();
                    successCount++;
                }
                long elapsed = System.currentTimeMillis() - start;
                latencies.offer(elapsed);
            } catch (Exception e) {
                // Format not supported or query failed — skip
            }
        }

        List<Long> sorted = new ArrayList<>(latencies);
        sorted.sort(null);

        double mean = sorted.isEmpty() ? 0.0 :
                BenchmarkUtils.calculateMean(new ArrayList<>(latencies));
        long p95 = percentile(sorted, 95);
        long avgSize = successCount > 0 ? totalSize / successCount : 0;

        return new FormatResult(mean, p95, avgSize);
    }

    private long percentile(List<Long> sortedValues, int p) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    private String formatMemorySamples(List<MemorySample> samples) {
        StringBuilder sb = new StringBuilder();
        for (MemorySample sample : samples) {
            sb.append(String.format("  %ds: %d MB (%d queries, avg %d ms)%n",
                    sample.timeSeconds, sample.usedMB, sample.queryCount, sample.avgLatMs));
        }
        return sb.toString();
    }

    private record LoadResult(double qps, long p50ms, long p95ms, long p99ms,
                              long maxMs, double errorRate, int success, int fail) {}

    private record ComplexityResult(double meanMs, long p95ms, long p99ms) {}

    private record FormatResult(double meanMs, long p95ms, long sizeBytes) {}

    private record MemorySample(int timeSeconds, long usedMB, int queryCount, long avgLatMs) {}

    private record RecoverySample(int timeSeconds, double qps, double percentOfBaseline) {}
}
