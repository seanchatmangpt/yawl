/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Performance regression detector for YAWL integration benchmarks.
 *
 * Compares current benchmark results against baselines and detects
 * significant performance regressions or improvements.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class PerformanceRegressionDetector {

    // Regression thresholds
    private static final double LATENCY_REGRESSION_THRESHOLD = 0.20; // 20% increase
    private static final double THROUGHPUT_REGRESSION_THRESHOLD = 0.15; // 15% decrease
    private static final double ERROR_THRESHOLD = 0.01; // 1% error rate
    private static final double MEMORY_REGRESSION_THRESHOLD = 0.25; // 25% increase

    private final Path baselineResultsPath;
    private final Path currentResultsPath;
    private final RegressionConfig config;

    public PerformanceRegressionDetector(String baselineFile, String currentFile, RegressionConfig config) {
        this.baselineResultsPath = Paths.get(baselineFile);
        this.currentResultsPath = Paths.get(currentFile);
        this.config = config;
    }

    public PerformanceRegressionDetector(String baselineFile, String currentFile) {
        this(baselineFile, currentFile, new RegressionConfig());
    }

    /**
     * Detects regressions between baseline and current results
     */
    public RegressionReport detectRegressions() throws IOException {
        Map<String, BenchmarkResult> baselineResults = parseResults(baselineResultsPath);
        Map<String, BenchmarkResult> currentResults = parseResults(currentResultsPath);

        RegressionReport report = new RegressionReport();
        Set<String> allBenchmarks = new HashSet<>();
        allBenchmarks.addAll(baselineResults.keySet());
        allBenchmarks.addAll(currentResults.keySet());

        for (String benchmark : allBenchmarks) {
            BenchmarkResult baseline = baselineResults.get(benchmark);
            BenchmarkResult current = currentResults.get(benchmark);

            if (baseline == null) {
                report.addNewBenchmark(benchmark, current);
                continue;
            }

            if (current == null) {
                report.addMissingBenchmark(benchmark, baseline);
                continue;
            }

            analyzeRegression(benchmark, baseline, current, report);
        }

        return report;
    }

    private void analyzeRegression(String benchmark, BenchmarkResult baseline, BenchmarkResult current, RegressionReport report) {
        // Analyze latency regression
        if (baseline.avgLatency > 0) {
            double latencyRegression = (current.avgLatency - baseline.avgLatency) / baseline.avgLatency;
            if (latencyRegression > config.latencyThreshold) {
                report.addRegression(new Regression(
                    benchmark,
                    "LATENCY",
                    String.format("Latency increased by %.2f%% (baseline: %.2fms, current: %.2fms)",
                        latencyRegression * 100, baseline.avgLatency, current.avgLatency),
                    latencyRegression
                ));
            }
        }

        // Analyze throughput regression
        if (baseline.throughput > 0) {
            double throughputRegression = (baseline.throughput - current.throughput) / baseline.throughput;
            if (throughputRegression > config.throughputThreshold) {
                report.addRegression(new Regression(
                    benchmark,
                    "THROUGHPUT",
                    String.format("Throughput decreased by %.2f%% (baseline: %.2f req/s, current: %.2f req/s)",
                        throughputRegression * 100, baseline.throughput, current.throughput),
                    throughputRegression
                ));
            }
        }

        // Analyze error rate increase
        if (current.errorRate > baseline.errorRate) {
            double errorIncrease = current.errorRate - baseline.errorRate;
            if (errorIncrease > config.errorRateThreshold) {
                report.addRegression(new Regression(
                    benchmark,
                    "ERROR_RATE",
                    String.format("Error rate increased by %.2f%% (baseline: %.2f%%, current: %.2f%%)",
                        errorIncrease * 100, baseline.errorRate * 100, current.errorRate * 100),
                    errorIncrease
                ));
            }
        }

        // Analyze memory usage increase
        if (current.memoryUsage > 0 && baseline.memoryUsage > 0) {
            double memoryRegression = (current.memoryUsage - baseline.memoryUsage) / baseline.memoryUsage;
            if (memoryRegression > config.memoryThreshold) {
                report.addRegression(new Regression(
                    benchmark,
                    "MEMORY",
                    String.format("Memory usage increased by %.2f%% (baseline: %.2fMB, current: %.2fMB)",
                        memoryRegression * 100, baseline.memoryUsage / (1024 * 1024.0), current.memoryUsage / (1024 * 1024.0)),
                    memoryRegression
                ));
            }
        }
    }

    private Map<String, BenchmarkResult> parseResults(Path resultsFile) throws IOException {
        Map<String, BenchmarkResult> results = new LinkedHashMap<>();

        if (!Files.exists(resultsFile)) {
            return results;
        }

        List<String> lines = Files.readAllLines(resultsFile);

        // Skip header
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) continue;

            try {
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    String benchmark = parts[0].trim();
                    double avgLatency = Double.parseDouble(parts[1].trim());
                    int successCount = Integer.parseInt(parts[2].trim());
                    double errorRate = Double.parseDouble(parts[3].trim());
                    long timestamp = Long.parseLong(parts[4].trim());

                    results.put(benchmark, new BenchmarkResult(
                        benchmark, avgLatency, successCount, errorRate, timestamp
                    ));
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not parse line: " + line);
            }
        }

        return results;
    }

    /**
     * Generates HTML report for regression analysis
     */
    public void generateHtmlReport(String outputFilename) throws IOException {
        RegressionReport report = detectRegressions();
        String html = report.generateHtmlReport();

        Files.write(Paths.get(outputFilename), html.getBytes());
        System.out.println("Regression report generated: " + outputFilename);
    }

    // =========================================================================
    // Supporting Classes
    // =========================================================================

    /**
     * Configuration for regression detection thresholds
     */
    public static class RegressionConfig {
        public double latencyThreshold = LATENCY_REGRESSION_THRESHOLD;
        public double throughputThreshold = THROUGHPUT_REGRESSION_THRESHOLD;
        public double errorRateThreshold = ERROR_THRESHOLD;
        public double memoryThreshold = MEMORY_REGRESSION_THRESHOLD;

        public RegressionConfig withLatencyThreshold(double threshold) {
            this.latencyThreshold = threshold;
            return this;
        }

        public RegressionConfig withThroughputThreshold(double threshold) {
            this.throughputThreshold = threshold;
            return this;
        }
    }

    /**
     * Represents a single benchmark result
     */
    public static class BenchmarkResult {
        public final String benchmarkName;
        public final double avgLatency;
        public final int successCount;
        public final double errorRate;
        public final long timestamp;
        public long memoryUsage; // Optional

        public BenchmarkResult(String benchmarkName, double avgLatency, int successCount,
                              double errorRate, long timestamp) {
            this.benchmarkName = benchmarkName;
            this.avgLatency = avgLatency;
            this.successCount = successCount;
            this.errorRate = errorRate;
            this.timestamp = timestamp;
            this.memoryUsage = 0;
        }

        public double getThroughput() {
            // Simple throughput calculation: successful operations per second
            double timeSeconds = (System.currentTimeMillis() - timestamp) / 1000.0;
            return timeSeconds > 0 ? successCount / timeSeconds : 0;
        }
    }

    /**
     * Represents a detected regression
     */
    public static class Regression {
        public final String benchmarkName;
        public final String metric;
        public final String description;
        public final double severity; // 0.0 to 1.0

        public Regression(String benchmarkName, String metric, String description, double severity) {
            this.benchmarkName = benchmarkName;
            this.metric = metric;
            this.description = description;
            this.severity = Math.min(1.0, Math.abs(severity));
        }

        public String getSeverityLevel() {
            if (severity < 0.1) return "LOW";
            if (severity < 0.3) return "MEDIUM";
            if (severity < 0.5) return "HIGH";
            return "CRITICAL";
        }

        public String getSeverityColor() {
            switch (getSeverityLevel()) {
                case "LOW": return "#4CAF50";
                case "MEDIUM": return "#FFC107";
                case "HIGH": return "#FF9800";
                case "CRITICAL": return "#F44336";
                default: return "#9E9E9E";
            }
        }
    }

    /**
     * Complete regression analysis report
     */
    public static class RegressionReport {
        private final List<Regression> regressions = new ArrayList<>();
        private final List<String> newBenchmarks = new ArrayList<>();
        private final List<String> missingBenchmarks = new ArrayList<>();
        private final Map<String, BenchmarkResult> currentResults = new LinkedHashMap<>();
        private final Map<String, BenchmarkResult> baselineResults = new LinkedHashMap<>();

        public void addRegression(Regression regression) {
            regressions.add(regression);
        }

        public void addNewBenchmark(String name, BenchmarkResult result) {
            newBenchmarks.add(name);
            currentResults.put(name, result);
        }

        public void addMissingBenchmark(String name, BenchmarkResult result) {
            missingBenchmarks.add(name);
            baselineResults.put(name, result);
        }

        public boolean hasRegressions() {
            return !regressions.isEmpty();
        }

        public String generateHtmlReport() {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html>\n<head>\n");
            html.append("<title>YAWL Performance Regression Report</title>\n");
            html.append("<style>\n");
            html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
            html.append("h1, h2, h3 { color: #333; }\n");
            html.append(".header { background: #f0f0f0; padding: 10px; margin-bottom: 20px; }\n");
            html.append(".regression { margin: 10px 0; padding: 15px; border-radius: 5px; }\n");
            html.append(".severity-LOW { background-color: #E8F5E9; }\n");
            html.append(".severity-MEDIUM { background-color: #FFF8E1; }\n");
            html.append(".severity-HIGH { background-color: #FFECB3; }\n");
            html.append(".severity-CRITICAL { background-color: #FFCDD2; }\n");
            html.append(".benchmark-list { list-style: none; padding: 0; }\n");
            html.append(".benchmark-list li { padding: 5px 0; border-bottom: 1px solid #eee; }\n");
            html.append(".no-regressions { color: #4CAF50; font-weight: bold; }\n");
            html.append("</style>\n</head>\n<body>\n");

            // Header
            html.append("<div class=\"header\">\n");
            html.append("<h1>YAWL Performance Regression Report</h1>\n");
            html.append("<p>Generated on: ").append(new Date()).append("</p>\n");
            html.append("</div>\n");

            // Summary
            html.append("<h2>Summary</h2>\n");
            if (hasRegressions()) {
                html.append("<p class=\"regressions\">\u26A0 <strong>Regressions Detected: ")
                   .append(regressions.size()).append("</strong></p>\n");
            } else {
                html.append("<p class=\"no-regressions\">\u2714 No performance regressions detected</p>\n");
            }

            // Regressions
            if (!regressions.isEmpty()) {
                html.append("<h2>Detected Regressions</h2>\n");
                html.append("<table border=\"1\" style=\"width: 100%; border-collapse: collapse;\">\n");
                html.append("<tr><th>Benchmark</th><th>Metric</th><th>Severity</th><th>Description</th></tr>\n");

                for (Regression regression : regressions) {
                    html.append(String.format(
                        "<tr class=\"severity-%s\">\n" +
                        "  <td>%s</td>\n" +
                        "  <td>%s</td>\n" +
                        "  <td><span style=\"color: %s\">%s</span></td>\n" +
                        "  <td>%s</td>\n" +
                        "</tr>\n",
                        regression.getSeverityLevel(),
                        regression.benchmarkName,
                        regression.metric,
                        regression.getSeverityColor(),
                        regression.getSeverityLevel(),
                        regression.description
                    ));
                }

                html.append("</table>\n");
            }

            // New Benchmarks
            if (!newBenchmarks.isEmpty()) {
                html.append("<h2>New Benchmarks</h2>\n");
                html.append("<ul class=\"benchmark-list\">\n");
                for (String benchmark : newBenchmarks) {
                    html.append("<li>").append(benchmark).append("</li>\n");
                }
                html.append("</ul>\n");
            }

            // Missing Benchmarks
            if (!missingBenchmarks.isEmpty()) {
                html.append("<h2>Missing Benchmarks</h2>\n");
                html.append("<ul class=\"benchmark-list\">\n");
                for (String benchmark : missingBenchmarks) {
                    html.append("<li>").append(benchmark).append("</li>\n");
                }
                html.append("</ul>\n");
            }

            // Recommendations
            html.append("<h2>Recommendations</h2>\n");
            if (hasRegressions()) {
                html.append("<ul>\n");
                html.append("<li>Review recent code changes that may have affected performance</li>\n");
                html.append("<li>Run benchmarks with different load profiles to identify patterns</li>\n");
                html.append("<li>Consider performance optimization techniques for critical paths</li>\n");
                html.append("<li>Monitor memory usage for potential leaks</li>\n");
                html.append("</ul>\n");
            } else {
                html.append("<p>Performance is within acceptable thresholds. Continue monitoring.</p>\n");
            }

            html.append("</body>\n</html>");
            return html.toString();
        }

        /**
         * Outputs report to console
         */
        public void printToConsole() {
            System.out.println("\n=== Performance Regression Report ===");
            System.out.println("Generated on: " + new Date());

            if (hasRegressions()) {
                System.out.println("\n\u26A0 REGRESSIONS DETECTED (" + regressions.size() + "):");
                for (Regression regression : regressions) {
                    System.out.printf("[%s] %s - %s\n",
                        regression.getSeverityLevel(),
                        regression.benchmarkName,
                        regression.description);
                }
            } else {
                System.out.println("\u2714 No performance regressions detected");
            }

            if (!newBenchmarks.isEmpty()) {
                System.out.println("\nNew Benchmarks:");
                newBenchmarks.forEach(b -> System.out.println("  + " + b));
            }

            if (!missingBenchmarks.isEmpty()) {
                System.out.println("\nMissing Benchmarks:");
                missingBenchmarks.forEach(b -> System.out.println("  - " + b));
            }
        }
    }

    // =========================================================================
    // Main Method
    // =========================================================================

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: PerformanceRegressionDetector <baseline-file> <current-file>");
            System.exit(1);
        }

        try {
            PerformanceRegressionDetector detector = new PerformanceRegressionDetector(
                args[0], args[1]);

            // Generate HTML report
            String htmlReport = "regression-report-" + System.currentTimeMillis() + ".html";
            detector.generateHtmlReport(htmlReport);

            // Print to console
            RegressionReport report = detector.detectRegressions();
            report.printToConsole();

            System.out.println("\nHTML report generated: " + htmlReport);

        } catch (IOException e) {
            System.err.println("Error running regression detection: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}