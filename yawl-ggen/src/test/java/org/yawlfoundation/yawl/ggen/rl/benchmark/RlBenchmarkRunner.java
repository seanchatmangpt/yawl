/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.benchmark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.function.Supplier;

/**
 * JMH-style micro-benchmark framework for RL components.
 *
 * <p>Provides warmup, iteration, and statistics collection similar to JMH
 * but without the annotation processing overhead. Suitable for PhD thesis
 * benchmark data collection.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * BenchmarkResult result = RlBenchmarkRunner.run("myBenchmark", () -> {
 *     // code to benchmark
 *     someMethod();
 * }, 1000, 10000);  // 1000 warmup, 10000 measured iterations
 *
 * System.out.println(result.toJson());
 * }</pre>
 */
public final class RlBenchmarkRunner {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private RlBenchmarkRunner() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * Runs a benchmark with warmup and measured iterations.
     *
     * @param name       benchmark name for reporting
     * @param task       the task to benchmark (must be idempotent)
     * @param warmup     number of warmup iterations (JIT compilation)
     * @param iterations number of measured iterations
     * @return BenchmarkResult with latency statistics
     */
    public static BenchmarkResult run(String name, Runnable task, int warmup, int iterations) {
        return run(name, () -> {
            task.run();
            return null;
        }, warmup, iterations);
    }

    /**
     * Runs a benchmark with a supplier that returns a value.
     *
     * @param name       benchmark name for reporting
     * @param task       the task to benchmark (returns a value for verification)
     * @param warmup     number of warmup iterations
     * @param iterations number of measured iterations
     * @param <T>        return type of the task
     * @return BenchmarkResult with latency statistics
     */
    public static <T> BenchmarkResult run(String name, Supplier<T> task, int warmup, int iterations) {
        // Warmup phase - let JIT compile and optimize
        for (int i = 0; i < warmup; i++) {
            task.get();
        }

        // Measured phase
        long[] latencies = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            task.get();
            latencies[i] = System.nanoTime() - start;
        }

        return computeStats(name, latencies, warmup, iterations);
    }

    /**
     * Runs a benchmark multiple times and aggregates results.
     *
     * @param name       benchmark name
     * @param task       the task to benchmark
     * @param warmup     warmup iterations per run
     * @param iterations measured iterations per run
     * @param runs       number of independent runs
     * @return AggregatedBenchmarkResult with cross-run statistics
     */
    public static AggregatedBenchmarkResult runMultiple(
            String name, Runnable task, int warmup, int iterations, int runs) {

        List<BenchmarkResult> results = new ArrayList<>(runs);
        for (int i = 0; i < runs; i++) {
            results.add(run(name, task, warmup, iterations));
        }

        return aggregate(name, results);
    }

    // ─── Statistics Computation ─────────────────────────────────────────────

    private static BenchmarkResult computeStats(String name, long[] latencies, int warmup, int iterations) {
        Arrays.sort(latencies);

        LongSummaryStatistics stats = Arrays.stream(latencies).summaryStatistics();

        double mean = stats.getAverage();
        double variance = Arrays.stream(latencies)
                .mapToDouble(l -> (l - mean) * (l - mean))
                .average()
                .orElse(0.0);
        double std = Math.sqrt(variance);

        return new BenchmarkResult(
                name,
                Instant.now(),
                warmup,
                iterations,
                stats.getMin(),
                stats.getMax(),
                mean,
                std,
                percentile(latencies, 50),
                percentile(latencies, 95),
                percentile(latencies, 99)
        );
    }

    private static long percentile(long[] sortedLatencies, double percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sortedLatencies.length) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.length - 1));
        return sortedLatencies[index];
    }

    private static AggregatedBenchmarkResult aggregate(String name, List<BenchmarkResult> results) {
        double meanOfMeans = results.stream()
                .mapToDouble(BenchmarkResult::meanLatencyNs)
                .average()
                .orElse(0.0);

        double stdOfMeans = Math.sqrt(results.stream()
                .mapToDouble(r -> (r.meanLatencyNs() - meanOfMeans) * (r.meanLatencyNs() - meanOfMeans))
                .average()
                .orElse(0.0));

        double minOverall = results.stream()
                .mapToDouble(BenchmarkResult::minLatencyNs)
                .min()
                .orElse(0.0);

        double maxOverall = results.stream()
                .mapToDouble(BenchmarkResult::maxLatencyNs)
                .max()
                .orElse(0.0);

        return new AggregatedBenchmarkResult(
                name,
                Instant.now(),
                results.size(),
                results.get(0).warmup(),
                results.get(0).iterations(),
                meanOfMeans,
                stdOfMeans,
                minOverall,
                maxOverall,
                results
        );
    }

    // ─── Result Records ──────────────────────────────────────────────────────

    /**
     * Single benchmark run result with latency statistics.
     */
    public record BenchmarkResult(
            String name,
            Instant timestamp,
            int warmup,
            int iterations,
            long minLatencyNs,
            long maxLatencyNs,
            double meanLatencyNs,
            double stdLatencyNs,
            long p50LatencyNs,
            long p95LatencyNs,
            long p99LatencyNs
    ) {
        /**
         * Returns mean latency in milliseconds.
         */
        public double meanLatencyMs() {
            return meanLatencyNs / 1_000_000.0;
        }

        /**
         * Converts to JSON for reporting.
         */
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", name);
            obj.addProperty("timestamp", timestamp.toString());
            obj.addProperty("warmup", warmup);
            obj.addProperty("iterations", iterations);

            JsonObject latency = new JsonObject();
            latency.addProperty("min_ns", minLatencyNs);
            latency.addProperty("max_ns", maxLatencyNs);
            latency.addProperty("mean_ns", meanLatencyNs);
            latency.addProperty("std_ns", stdLatencyNs);
            latency.addProperty("p50_ns", p50LatencyNs);
            latency.addProperty("p95_ns", p95LatencyNs);
            latency.addProperty("p99_ns", p99LatencyNs);
            latency.addProperty("mean_ms", meanLatencyMs());

            obj.add("latency", latency);
            return obj;
        }

        @Override
        public String toString() {
            return GSON.toJson(toJson());
        }
    }

    /**
     * Aggregated results from multiple benchmark runs.
     */
    public record AggregatedBenchmarkResult(
            String name,
            Instant timestamp,
            int runs,
            int warmupPerRun,
            int iterationsPerRun,
            double meanOfMeansNs,
            double stdOfMeansNs,
            double minOverallNs,
            double maxOverallNs,
            List<BenchmarkResult> individualResults
    ) {
        /**
         * Converts to JSON for reporting.
         */
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", name);
            obj.addProperty("timestamp", timestamp.toString());
            obj.addProperty("runs", runs);
            obj.addProperty("warmup_per_run", warmupPerRun);
            obj.addProperty("iterations_per_run", iterationsPerRun);

            JsonObject aggregate = new JsonObject();
            aggregate.addProperty("mean_of_means_ns", meanOfMeansNs);
            aggregate.addProperty("std_of_means_ns", stdOfMeansNs);
            aggregate.addProperty("min_overall_ns", minOverallNs);
            aggregate.addProperty("max_overall_ns", maxOverallNs);
            aggregate.addProperty("mean_of_means_ms", meanOfMeansNs / 1_000_000.0);

            obj.add("aggregate", aggregate);

            JsonArray results = new JsonArray();
            for (BenchmarkResult r : individualResults) {
                results.add(r.toJson());
            }
            obj.add("individual_runs", results);

            return obj;
        }

        @Override
        public String toString() {
            return GSON.toJson(toJson());
        }
    }

    /**
     * Full benchmark suite result for JSON export.
     */
    public record BenchmarkSuiteResult(
            Instant timestamp,
            String javaVersion,
            String osName,
            String osVersion,
            List<BenchmarkResult> results
    ) {
        public BenchmarkSuiteResult(List<BenchmarkResult> results) {
            this(
                    Instant.now(),
                    System.getProperty("java.version", "unknown"),
                    System.getProperty("os.name", "unknown"),
                    System.getProperty("os.version", "unknown"),
                    results
            );
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("timestamp", timestamp.toString());
            obj.addProperty("java_version", javaVersion);
            obj.addProperty("os_name", osName);
            obj.addProperty("os_version", osVersion);

            JsonArray resultsArray = new JsonArray();
            for (BenchmarkResult r : results) {
                resultsArray.add(r.toJson());
            }
            obj.add("results", resultsArray);

            return obj;
        }

        @Override
        public String toString() {
            return GSON.toJson(toJson());
        }
    }
}
