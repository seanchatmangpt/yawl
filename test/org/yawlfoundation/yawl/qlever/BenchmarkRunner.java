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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.qlever;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Standalone benchmark runner for QLever performance testing.
 * Can be run independently or as part of the test suite.
 */
public class BenchmarkRunner {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final BenchmarkDataGenerator.BenchmarkConfig config;
    private final QLeverEmbeddedSparqlEngine engine;
    private final ExecutorService executor;
    private final List<BenchmarkResult> allResults = new ArrayList<>();

    public BenchmarkRunner(QLeverEmbeddedSparqlEngine engine,
                           BenchmarkDataGenerator.BenchmarkConfig config) {
        this.engine = engine;
        this.config = config;
        this.executor = Executors.newFixedThreadPool(config.getThreadCount());
    }

    /**
     * Runs all benchmark scenarios and generates a comprehensive report.
     */
    public BenchmarkReport runAllBenchmarks() throws Exception {
        System.out.println("Starting QLever Benchmark Suite");
        System.out.println("=================================");
        System.out.println("Configuration: " + config);
        System.out.println();

        long startTime = System.currentTimeMillis();

        // Run all benchmarks
        runColdStartBenchmark();
        runComplexityBenchmark();
        runResultSetBenchmark();
        runMemoryBenchmark();
        runThreadContentionBenchmark();
        runFormatBenchmark();

        long endTime = System.currentTimeMillis();

        // Generate report
        BenchmarkReport report = new BenchmarkReport(
            LocalDateTime.now().format(TIMESTAMP_FORMAT),
            config,
            allResults,
            endTime - startTime
        );

        // Save results
        saveResults(report);

        System.out.println("\nBenchmark suite completed in " + (endTime - startTime) + " ms");
        System.out.println("Results saved to: " + config.getResultsDirectory());

        return report;
    }

    private void runColdStartBenchmark() throws Exception {
        System.out.println("Running Cold Start Benchmark...");

        List<BenchmarkResult> coldResults = new ArrayList<>();
        List<BenchmarkResult> warmResults = new ArrayList<>();

        // Warm queries
        warmEngine();

        // Cold start measurements
        for (int i = 0; i < config.getMeasurementIterations(); i++) {
            coldResults.add(measureColdStart());
        }

        // Warm query measurements
        for (int i = 0; i < config.getMeasurementIterations(); i++) {
            warmResults.add(measureWarmQuery());
        }

        // Analyze results
        analyzeColdStartResults(coldResults, warmResults);
    }

    private void runComplexityBenchmark() throws Exception {
        System.out.println("Running Complexity Scaling Benchmark...");

        Map<Integer, List<BenchmarkResult>> complexityResults = new HashMap<>();

        // Different complexity levels
        int[] complexities = {1, 2, 3};

        for (int complexity : complexities) {
            List<BenchmarkResult> results = new ArrayList<>();

            for (int i = 0; i < config.getMeasurementIterations(); i++) {
                results.add(measureComplexity(complexity));
            }

            complexityResults.put(complexity, results);
            analyzeComplexityResults(complexity, results);
        }
    }

    private void runResultSetBenchmark() throws Exception {
        System.out.println("Running Result Set Size Benchmark...");

        Map<Integer, List<BenchmarkResult>> sizeResults = new HashMap<>();

        int[] resultSizes = {10, 100, 1000, 10000};

        for (int size : resultSizes) {
            List<BenchmarkResult> results = new ArrayList<>();

            for (int i = 0; i < config.getMeasurementIterations(); i++) {
                results.add(measureResultSetSize(size));
            }

            sizeResults.put(size, results);
            analyzeResultSetResults(size, results);
        }
    }

    private void runMemoryBenchmark() throws Exception {
        System.out.println("Running Memory Allocation Benchmark...");

        List<BenchmarkResult> memoryResults = new ArrayList<>();

        for (int i = 0; i < config.getMeasurementIterations(); i++) {
            memoryResults.add(measureMemoryUsage());
        }

        analyzeMemoryResults(memoryResults);
    }

    private void runThreadContentionBenchmark() throws Exception {
        System.out.println("Running Thread Contention Benchmark...");

        Map<Integer, List<BenchmarkResult>> threadResults = new HashMap<>();

        int[] threadCounts = {1, 2, 4, 8, 16};

        for (int threadCount : threadCounts) {
            List<BenchmarkResult> results = measureThreadContention(threadCount);
            threadResults.put(threadCount, results);
            analyzeThreadResults(threadCount, results);
        }
    }

    private void runFormatBenchmark() throws Exception {
        System.out.println("Running Format Serialization Benchmark...");

        Map<String, List<BenchmarkResult>> formatResults = new HashMap<>();

        String[] formats = {"JSON", "TSV", "CSV"};

        for (String format : formats) {
            List<BenchmarkResult> results = new ArrayList<>();

            for (int i = 0; i < config.getMeasurementIterations(); i++) {
                results.add(measureFormatSerialization(format));
            }

            formatResults.put(format, results);
            analyzeFormatResults(format, results);
        }
    }

    // Individual measurement methods

    private void warmEngine() throws Exception {
        System.out.println("  Warming up engine...");
        for (int i = 0; i < config.getWarmupIterations(); i++) {
            try (QLeverResult result = engine.executeSelect("SELECT ?s WHERE { ?s ?p ?o } LIMIT 1", QLeverMediaType.JSON)) {
                String data = result.data();
                if (data == null || data.isEmpty()) {
                    throw new Exception("Engine warmup failed");
                }
            }
        }
        System.out.println("  Engine warmed up");
    }

    private BenchmarkResult measureColdStart() {
        long start = System.nanoTime();
        long startMemory = BenchmarkUtils.getMemoryUsage();
        long gcBefore = BenchmarkUtils.getGcCount();

        try {
            QLeverEmbeddedSparqlEngine tempEngine = QLeverTestNode.engine();
            try (QLeverResult result = tempEngine.executeSelect("SELECT ?s WHERE { ?s ?p ?o } LIMIT 10", QLeverMediaType.JSON)) {
                String data = result.data();

                long end = System.nanoTime();
                long endMemory = BenchmarkUtils.getMemoryUsage();
                long gcAfter = BenchmarkUtils.getGcCount();

                return createBenchmarkResult(
                    "ColdStart",
                    (end - start) / 1000,
                    endMemory - startMemory,
                    (int) (gcAfter - gcBefore),
                    data != null ? data.split("\n").length : 0,
                    true,
                    null
                );
            }
        } catch (Exception e) {
            return createBenchmarkResult(
                "ColdStart",
                0,
                0,
                0,
                0,
                false,
                e.getMessage()
            );
        }
    }

    private BenchmarkResult measureWarmQuery() {
        long start = System.nanoTime();
        long startMemory = BenchmarkUtils.getMemoryUsage();
        long gcBefore = BenchmarkUtils.getGcCount();

        try {
            try (QLeverResult result = engine.executeSelect("SELECT ?s WHERE { ?s ?p ?o } LIMIT 10", QLeverMediaType.JSON)) {
                String data = result.data();

                long end = System.nanoTime();
                long endMemory = BenchmarkUtils.getMemoryUsage();
                long gcAfter = BenchmarkUtils.getGcCount();

                return createBenchmarkResult(
                    "WarmQuery",
                    (end - start) / 1000,
                    endMemory - startMemory,
                    (int) (gcAfter - gcBefore),
                    data != null ? data.split("\n").length : 0,
                    true,
                    null
                );
            }
        } catch (Exception e) {
            return createBenchmarkResult(
                "WarmQuery",
                0,
                0,
                0,
                0,
                false,
                e.getMessage()
            );
        }
    }

    private BenchmarkResult measureComplexity(int complexity) {
        long start = System.nanoTime();
        long startMemory = BenchmarkUtils.getMemoryUsage();
        long gcBefore = BenchmarkUtils.getGcCount();

        String query = complexity == 1 ?
            BenchmarkDataGenerator.generateSimpleQuery(0) :
            complexity == 2 ?
            BenchmarkDataGenerator.generateComplexQuery(0) :
            BenchmarkDataGenerator.generateLargeUnionQuery(10);

        try {
            try (QLeverResult result = engine.executeSelect(query, QLeverMediaType.JSON)) {
                String data = result.data();

                long end = System.nanoTime();
                long endMemory = BenchmarkUtils.getMemoryUsage();
                long gcAfter = BenchmarkUtils.getGcCount();

                return createBenchmarkResult(
                    "Complexity-" + complexity,
                    (end - start) / 1000,
                    endMemory - startMemory,
                    (int) (gcAfter - gcBefore),
                    data != null ? data.split("\n").length : 0,
                    true,
                    null
                );
            }
        } catch (Exception e) {
            return createBenchmarkResult(
                "Complexity-" + complexity,
                0,
                0,
                0,
                0,
                false,
                e.getMessage()
            );
        }
    }

    private BenchmarkResult measureResultSetSize(int size) {
        long start = System.nanoTime();
        long startMemory = BenchmarkUtils.getMemoryUsage();
        long gcBefore = BenchmarkUtils.getGcCount();

        String query = BenchmarkDataGenerator.generateQueryWithLimit(size);

        try {
            try (QLeverResult result = engine.executeSelect(query, QLeverMediaType.JSON)) {
                String data = result.data();

                long end = System.nanoTime();
                long endMemory = BenchmarkUtils.getMemoryUsage();
                long gcAfter = BenchmarkUtils.getGcCount();

                return createBenchmarkResult(
                    "ResultSet-" + size,
                    (end - start) / 1000,
                    endMemory - startMemory,
                    (int) (gcAfter - gcBefore),
                    data != null ? data.split("\n").length : 0,
                    true,
                    null
                );
            }
        } catch (Exception e) {
            return createBenchmarkResult(
                "ResultSet-" + size,
                0,
                0,
                0,
                0,
                false,
                e.getMessage()
            );
        }
    }

    private BenchmarkResult measureMemoryUsage() {
        long startMemory = BenchmarkUtils.getMemoryUsage();
        long startGcCount = BenchmarkUtils.getGcCount();
        long startGcTime = BenchmarkUtils.getGcTime();

        // Execute multiple queries
        for (int i = 0; i < 10; i++) {
            try {
                String query = BenchmarkDataGenerator.generateRandomQuery();
                try (QLeverResult result = engine.executeSelect(query, QLeverMediaType.JSON)) {
                    String data = result.data();

                    // Process result to encourage memory allocation
                    if (data != null) {
                        String[] lines = data.split("\n");
                        for (String line : lines) {
                            line.trim();
                            line.intern();
                        }
                    }
                }
            } catch (Exception e) {
                // Record the error but continue measuring
            }
        }

        long endMemory = BenchmarkUtils.getMemoryUsage();
        long endGcCount = BenchmarkUtils.getGcCount();
        long endGcTime = BenchmarkUtils.getGcTime();

        return createBenchmarkResult(
            "MemoryPressure",
            0, // Not measuring time
            endMemory - startMemory,
            (int) (endGcCount - startGcCount),
            0,
            true,
            null
        );
    }

    private List<BenchmarkResult> measureThreadContention(int threadCount) {
        List<BenchmarkResult> results = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            Future<?> future = executor.submit(() -> {
                try {
                    latch.await(); // Wait for all threads

                    for (int i = 0; i < config.getMeasurementIterations() / threadCount; i++) {
                        long start = System.nanoTime();
                        long startMemory = BenchmarkUtils.getMemoryUsage();
                        long gcBefore = BenchmarkUtils.getGcCount();

                        try {
                            String query = BenchmarkDataGenerator.generateRandomQuery();
                            try (QLeverResult result = engine.executeSelect(query, QLeverMediaType.JSON)) {
                                String data = result.data();

                                long end = System.nanoTime();
                                long endMemory = BenchmarkUtils.getMemoryUsage();
                                long gcAfter = BenchmarkUtils.getGcCount();

                                results.add(createBenchmarkResult(
                                    "Threads-" + threadCount,
                                    (end - start) / 1000,
                                    endMemory - startMemory,
                                    (int) (gcAfter - gcBefore),
                                    data != null ? data.split("\n").length : 0,
                                    true,
                                    null
                                ));
                            }
                        } catch (Exception e) {
                            results.add(createBenchmarkResult(
                                "Threads-" + threadCount,
                                0,
                                0,
                                0,
                                0,
                                false,
                                e.getMessage()
                            ));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            futures.add(future);
        }

        // Start all threads simultaneously
        latch.countDown();

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            } catch (Exception e) {
                // Record timeout but continue
            }
        }

        return results;
    }

    private BenchmarkResult measureFormatSerialization(String format) {
        long start = System.nanoTime();
        long startMemory = BenchmarkUtils.getMemoryUsage();
        long gcBefore = BenchmarkUtils.getGcCount();

        String query = "SELECT ?case ?status WHERE { ?case workflow:status ?status } LIMIT 100";

        try {
            QLeverMediaType mediaType = switch (format) {
                case "JSON" -> QLeverMediaType.JSON;
                case "TSV" -> QLeverMediaType.TSV;
                case "CSV" -> QLeverMediaType.CSV;
                default -> QLeverMediaType.JSON;
            };

            try (QLeverResult result = engine.executeSelect(query, mediaType)) {
                String data = result.data();

                long end = System.nanoTime();
                long endMemory = BenchmarkUtils.getMemoryUsage();
                long gcAfter = BenchmarkUtils.getGcCount();

                return createBenchmarkResult(
                    "Format-" + format,
                    (end - start) / 1000,
                    endMemory - startMemory,
                    (int) (gcAfter - gcBefore),
                    data != null ? data.getBytes().length : 0,
                    true,
                    null
                );
            }
        } catch (Exception e) {
            return createBenchmarkResult(
                "Format-" + format,
                0,
                0,
                0,
                0,
                false,
                e.getMessage()
            );
        }
    }

    private BenchmarkResult createBenchmarkResult(String scenario, long durationMicros,
                                                long memoryBytes, int gcCount,
                                                int resultCount, boolean success,
                                                String errorMessage) {
        BenchmarkResult result = new BenchmarkResult(
            scenario,
            allResults.size(),
            durationMicros,
            memoryBytes,
            gcCount,
            BenchmarkUtils.estimateGcPauseTime(gcCount, gcCount),
            resultCount,
            success,
            errorMessage
        );
        allResults.add(result);
        return result;
    }

    // Analysis methods

    private void analyzeColdStartResults(List<BenchmarkResult> coldResults,
                                       List<BenchmarkResult> warmResults) {
        printScenarioStatistics("Cold Start", coldResults);
        printScenarioStatistics("Warm Query", warmResults);

        // Calculate comparison
        double coldMean = coldResults.stream()
            .filter(r -> r.success)
            .mapToLong(BenchmarkResult::durationMicros)
            .average()
            .orElse(0);

        double warmMean = warmResults.stream()
            .filter(r -> r.success)
            .mapToLong(BenchmarkResult::durationMicros)
            .average()
            .orElse(0);

        double ratio = coldMean / warmMean;

        System.out.printf("  Cold vs Warm Performance Ratio: %.1fx%n%n", ratio);
    }

    private void analyzeComplexityResults(int complexity, List<BenchmarkResult> results) {
        printScenarioStatistics("Complexity-" + complexity, results);

        double meanTime = results.stream()
            .filter(r -> r.success)
            .mapToLong(BenchmarkResult::durationMicros)
            .average()
            .orElse(0);

        System.out.printf("  Complexity %d mean time: %.2f μs%n%n", complexity, meanTime / 1000.0);
    }

    private void analyzeResultSetResults(int size, List<BenchmarkResult> results) {
        printScenarioStatistics("ResultSet-" + size, results);

        double meanTime = results.stream()
            .filter(r -> r.success)
            .mapToLong(BenchmarkResult::durationMicros)
            .average()
            .orElse(0);

        System.out.printf("  Result size %d mean time: %.2f μs%n%n", size, meanTime / 1000.0);
    }

    private void analyzeMemoryResults(List<BenchmarkResult> results) {
        printScenarioStatistics("MemoryPressure", results);

        double avgMemory = results.stream()
            .mapToLong(BenchmarkResult::memoryBytes)
            .average()
            .orElse(0);

        long totalGcPause = results.stream()
            .mapToLong(BenchmarkResult::gcPauseMillis)
            .sum();

        System.out.printf("  Average memory per batch: %.2f MB%n", avgMemory / 1024.0 / 1024.0);
        System.out.printf("  Total GC pause: %d ms%n%n", totalGcPause);
    }

    private void analyzeThreadResults(int threadCount, List<BenchmarkResult> results) {
        printScenarioStatistics("Threads-" + threadCount, results);

        double meanTime = results.stream()
            .filter(r -> r.success)
            .mapToLong(BenchmarkResult::durationMicros)
            .average()
            .orElse(0);

        double throughput = results.size() / (meanTime / 1_000_000.0);

        System.out.printf("  Threads: %d, Throughput: %.1f ops/sec/thread%n%n",
            threadCount, throughput);
    }

    private void analyzeFormatResults(String format, List<BenchmarkResult> results) {
        printScenarioStatistics("Format-" + format, results);

        double meanTime = results.stream()
            .filter(r -> r.success)
            .mapToLong(BenchmarkResult::durationMicros)
            .average()
            .orElse(0);

        long meanSize = (long) results.stream()
            .filter(r -> r.success)
            .mapToLong(BenchmarkResult::resultCount)
            .average()
            .orElse(0);

        System.out.printf("  %s: %.2f μs mean, %.2f KB avg size%n%n",
            format, meanTime / 1000.0, meanSize / 1024.0);
    }

    private void printScenarioStatistics(String scenario, List<BenchmarkResult> results) {
        List<Long> durations = results.stream()
            .filter(r -> r.success)
            .mapToLong(BenchmarkResult::durationMicros)
            .boxed()
            .collect(Collectors.toList());

        if (durations.isEmpty()) {
            System.out.println("  No successful results for " + scenario);
            return;
        }

        long mean = BenchmarkUtils.calculateMean(durations);
        double median = BenchmarkUtils.calculateMedian(durations);
        double stdDev = BenchmarkUtils.calculateStandardDeviation(durations, mean);

        long totalMemory = results.stream()
            .mapToLong(BenchmarkResult::memoryBytes)
            .sum();

        long totalGcPause = results.stream()
            .mapToLong(BenchmarkResult::gcPauseMillis)
            .sum();

        double opsPerSecond = BenchmarkUtils.calculateOpsPerSecond(
            durations.stream().mapToLong(Long::longValue).sum(),
            results.size()
        );

        System.out.printf("%s - Statistics:%n", scenario);
        System.out.printf("  Duration: %.2f μs mean, %.2f μs median, %.2f μs std dev%n",
            mean / 1000.0, median / 1000.0, stdDev / 1000.0);
        System.out.printf("  Memory: %.2f KB avg, Total: %.2f MB%n",
            totalMemory / (double) results.size() / 1024.0,
            totalMemory / 1024.0 / 1024.0);
        System.out.printf("  GC Pause: %d ms total (%.2f ms avg)%n",
            totalGcPause, totalGcPause / (double) results.size());
        System.out.printf("  Throughput: %.0f ops/sec%n", opsPerSecond);
        System.out.printf("  Success Rate: %.1f%%%n%n",
            results.stream().filter(BenchmarkResult::success).count() * 100.0 / results.size());
    }

    private void saveResults(BenchmarkReport report) throws IOException {
        // Create results directory
        Path resultsDir = Paths.get(config.getResultsDirectory());
        Files.createDirectories(resultsDir);

        // Save CSV summary
        Path csvFile = resultsDir.resolve("benchmark-summary-" +
            report.getTimestamp().replace(':', '-') + ".csv");
        saveCsvReport(report, csvFile);

        // Save JSON detailed results
        Path jsonFile = resultsDir.resolve("benchmark-results-" +
            report.getTimestamp().replace(':', '-') + ".json");
        saveJsonReport(report, jsonFile);

        // Save console summary
        saveConsoleSummary(report, resultsDir.resolve("benchmark-summary.txt"));
    }

    private void saveCsvReport(BenchmarkReport report, Path filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write("Scenario,Iteration,DurationMicros,MemoryBytes,GCCount,GCPauseMillis,ResultCount,Success,ErrorMessage\n");

            for (BenchmarkResult result : allResults) {
                writer.write(String.format("%s,%d,%d,%d,%d,%d,%d,%b,%s\n",
                    result.scenario(),
                    result.iteration(),
                    result.durationMicros(),
                    result.memoryBytes(),
                    result.gcCount(),
                    result.gcPauseMillis(),
                    result.resultCount(),
                    result.success(),
                    result.errorMessage() != null ?
                        "\"" + result.errorMessage().replace("\"", "\"\"") + "\"" : ""
                ));
            }
        }
    }

    private void saveJsonReport(BenchmarkReport report, Path filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write("{\n");
            writer.write("  \"timestamp\": \"" + report.getTimestamp() + "\",\n");
            writer.write("  \"config\": {\n");
            writer.write("    \"warmupIterations\": " + config.getWarmupIterations() + ",\n");
            writer.write("    \"measurementIterations\": " + config.getMeasurementIterations() + ",\n");
            writer.write("    \"threadCount\": " + config.getThreadCount() + ",\n");
            writer.write("    \"timeoutSeconds\": " + config.getTimeoutSeconds() + "\n");
            writer.write("  },\n");
            writer.write("  \"totalDurationMs\": " + report.getTotalDurationMs() + ",\n");
            writer.write("  \"totalResults\": " + allResults.size() + ",\n");
            writer.write("  \"results\": [\n");

            boolean first = true;
            for (BenchmarkResult result : allResults) {
                if (!first) writer.write(",\n");
                writer.write("    {\n");
                writer.write("      \"scenario\": \"" + result.scenario() + "\",\n");
                writer.write("      \"iteration\": " + result.iteration() + ",\n");
                writer.write("      \"durationMicros\": " + result.durationMicros() + ",\n");
                writer.write("      \"memoryBytes\": " + result.memoryBytes() + ",\n");
                writer.write("      \"gcCount\": " + result.gcCount() + ",\n");
                writer.write("      \"gcPauseMillis\": " + result.gcPauseMillis() + ",\n");
                writer.write("      \"resultCount\": " + result.resultCount() + ",\n");
                writer.write("      \"success\": " + result.success() + ",\n");
                writer.write("      \"errorMessage\": " +
                    (result.errorMessage() != null ? "\"" + result.errorMessage() + "\"" : "null"));
                writer.write("\n    }");
                first = false;
            }

            writer.write("\n  ]\n");
            writer.write("}\n");
        }
    }

    private void saveConsoleSummary(BenchmarkReport report, Path filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write("QLever Benchmark Summary\n");
            writer.write("===========================\n");
            writer.write("Timestamp: " + report.getTimestamp() + "\n");
            writer.write("Total Duration: " + report.getTotalDurationMs() + " ms\n");
            writer.write("Total Results: " + allResults.size() + "\n\n");

            // Group results by scenario
            Map<String, List<BenchmarkResult>> grouped = allResults.stream()
                .collect(Collectors.groupingBy(BenchmarkResult::scenario));

            for (Map.Entry<String, List<BenchmarkResult>> entry : grouped.entrySet()) {
                List<BenchmarkResult> scenarioResults = entry.getValue();
                List<Long> durations = scenarioResults.stream()
                    .filter(r -> r.success)
                    .mapToLong(BenchmarkResult::durationMicros)
                    .boxed()
                    .collect(Collectors.toList());

                if (!durations.isEmpty()) {
                    long mean = BenchmarkUtils.calculateMean(durations);
                    double median = BenchmarkUtils.calculateMedian(durations);
                    double stdDev = BenchmarkUtils.calculateStandardDeviation(durations, mean);
                    double successRate = scenarioResults.stream()
                        .filter(BenchmarkResult::success)
                        .count() * 100.0 / scenarioResults.size();

                    writer.write(String.format("%s:\n", entry.getKey()));
                    writer.write(String.format("  Mean: %.2f μs, Median: %.2f μs, StdDev: %.2f μs\n",
                        mean / 1000.0, median / 1000.0, stdDev / 1000.0));
                    writer.write(String.format("  Success Rate: %.1f%%\n", successRate));
                    writer.write(String.format("  Total GC Pauses: %d ms\n",
                        scenarioResults.stream().mapToLong(BenchmarkResult::gcPauseMillis).sum()));
                    writer.write("\n");
                }
            }
        }
    }

    /**
     * Main method for running benchmarks standalone.
     */
    public static void main(String[] args) {
        try {
            // Check if QLever native lib is available
            if (!QLeverTestNode.isAvailable()) {
                System.err.println("QLever native library not available");
                System.exit(1);
            }

            // Parse command line arguments
            String resultsDir = args.length > 0 ? args[0] : System.getProperty("user.dir") + "/benchmark-results";

            // Create engine and config
            BenchmarkDataGenerator.BenchmarkConfig config =
                new BenchmarkDataGenerator.BenchmarkConfig(
                    10,   // warmupIterations
                    100,  // measurementIterations
                    8,    // threadCount
                    30,   // timeoutSeconds
                    resultsDir
                );

            QLeverEmbeddedSparqlEngine engine = QLeverTestNode.engine();

            // Run benchmarks
            BenchmarkRunner runner = new BenchmarkRunner(engine, config);
            BenchmarkReport report = runner.runAllBenchmarks();

            System.out.println("\nBenchmark Report Summary:");
            System.out.println("-------------------------");
            System.out.println("Total Duration: " + report.getTotalDurationMs() + " ms");
            System.out.println("Total Results: " + report.getTotalResults());
            System.out.println("Success Rate: " +
                (runner.allResults.stream().filter(BenchmarkResult::success).count() * 100.0 / runner.allResults.size()) + "%");

        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
