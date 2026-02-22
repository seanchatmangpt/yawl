/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Comprehensive benchmark suite runner for YAWL integration components.
 *
 * Provides a simple interface to run all benchmark classes with
 * predefined configurations and generate consolidated reports.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class BenchmarkSuite {

    private static final List<String> ALL_BENCHMARKS = Arrays.asList(
        "IntegrationBenchmarks",
        "StressTestBenchmarks"
    );

    private static final List<String> COMPONENT_BENCHMARKS = Arrays.asList(
        "A2A Benchmarks",
        "MCP Benchmarks",
        "ZAI Benchmarks"
    );

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("--help")) {
            printUsage();
            return;
        }

        String command = args[0].toLowerCase();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        try {
            switch (command) {
                case "run":
                    runFullSuite(timestamp, args);
                    break;
                case "component":
                    runComponentBenchmark(args);
                    break;
                case "validate":
                    validateResults(args);
                    break;
                case "report":
                    generateConsolidatedReport(timestamp);
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
            }
        } catch (Exception e) {
            System.err.println("Error executing benchmark suite: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Runs the complete benchmark suite
     */
    private static void runFullSuite(String timestamp, String[] args) throws Exception {
        System.out.println("=== YAWL Integration Benchmark Suite ===");
        System.out.println("Running complete benchmark suite...\n");

        // Create output directory
        Path outputDir = Paths.get("benchmark-results-" + timestamp);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // Run each benchmark class
        for (String benchmark : ALL_BENCHMARKS) {
            System.out.println("\nRunning " + benchmark + "...");
            runBenchmarkClass(benchmark, outputDir, args);
        }

        System.out.println("\n=== Benchmark Suite Complete ===");
        System.out.println("Results saved to: " + outputDir.toAbsolutePath());
    }

    /**
     * Runs a specific component benchmark
     */
    private static void runComponentBenchmark(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: BenchmarkSuite component <a2a|mcp|zai>");
            System.exit(1);
        }

        String component = args[1].toLowerCase();
        String componentBenchmark;

        switch (component) {
            case "a2a":
                componentBenchmark = "org.yawlfoundation.yawl.integration.benchmark.IntegrationBenchmarks";
                componentBenchmark += ".*VirtualThreadWorkflowLaunch";
                break;
            case "mcp":
                componentBenchmark = "org.yawlfoundation.yawl.integration.benchmark.IntegrationBenchmarks";
                componentBenchmark += ".*mcp.*";
                break;
            case "zai":
                componentBenchmark = "org.yawlfoundation.yawl.integration.benchmark.IntegrationBenchmarks";
                componentBenchmark += ".*zai.*";
                break;
            default:
                System.err.println("Unknown component: " + component);
                System.exit(1);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path outputDir = Paths.get("benchmark-results-" + timestamp);
        Files.createDirectories(outputDir);

        System.out.println("Running " + component.toUpperCase() + " benchmarks...");
        runBenchmarkClassWithPattern(componentBenchmark, outputDir, args);
    }

    /**
     * Validates benchmark results
     */
    private static void validateResults(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: BenchmarkSuite validate <results-directory>");
            System.exit(1);
        }

        Path resultsDir = Paths.get(args[1]);
        if (!Files.exists(resultsDir)) {
            System.err.println("Results directory not found: " + resultsDir);
            System.exit(1);
        }

        System.out.println("Validating benchmark results...");
        ValidationResult validation = validateResultsDirectory(resultsDir);

        if (validation.isValid()) {
            System.out.println("\u2714 All benchmark results are valid");
        } else {
            System.out.println("\u26A0 Validation issues found:");
            validation.getIssues().forEach(System.out::println);
        }
    }

    /**
     * Generates a consolidated report from multiple benchmark runs
     */
    private static void generateConsolidatedReport(String timestamp) throws IOException {
        Path resultsDir = Paths.get("benchmark-results-" + timestamp);
        if (!Files.exists(resultsDir)) {
            System.err.println("No results directory found: " + resultsDir);
            System.exit(1);
        }

        ConsolidatedReport report = new ConsolidatedReport(resultsDir);
        String htmlReport = "consolidated-report-" + timestamp + ".html";

        report.generateHtmlReport(htmlReport);
        System.out.println("Consolidated report generated: " + htmlReport);
    }

    // =========================================================================
    // Benchmark Execution Methods
    // =========================================================================

    private static void runBenchmarkClass(String className, Path outputDir, String[] args) {
        try {
            // Create a process to run the benchmark in a separate JVM
            String javaHome = System.getProperty("java.home");
            String javaCmd = javaHome + "/bin/java";
            String classpath = System.getProperty("java.class.path");

            // Construct command
            ProcessBuilder pb = new ProcessBuilder(javaCmd,
                "-cp", classpath,
                "-Xms2g", "-Xmx4g",
                "-XX:+UseG1GC",
                className);

            pb.directory(outputDir.toFile());
            pb.redirectOutput(outputDir.resolve(className + "-output.log").toFile());
            pb.redirectError(outputDir.resolve(className + "-error.log").toFile());

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.err.println("Benchmark " + className + " failed with exit code: " + exitCode);
            } else {
                System.out.println("Benchmark " + className + " completed successfully");
            }

        } catch (Exception e) {
            System.err.println("Error running benchmark " + className + ": " + e.getMessage());
        }
    }

    private static void runBenchmarkClassWithPattern(String pattern, Path outputDir, String[] args) {
        // Similar to runBenchmarkClass but with pattern filtering
        // Implementation would involve JMH pattern matching
    }

    // =========================================================================
    // Validation Methods
    // =========================================================================

    private static ValidationResult validateResultsDirectory(Path resultsDir) throws IOException {
        ValidationResult validation = new ValidationResult();

        Files.list(resultsDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".csv"))
            .forEach(file -> {
                if (!isValidResultFile(file)) {
                    validation.addIssue("Invalid result file: " + file.getFileName());
                }
            });

        return validation;
    }

    private static boolean isValidResultFile(Path file) throws IOException {
        // Basic validation - check if file contains required fields
        String content = Files.readString(file);
        return content.contains("benchmark") && content.contains("avgLatency");
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private static void printUsage() {
        System.out.println("YAWL Integration Benchmark Suite");
        System.out.println("Usage:");
        System.out.println("  java BenchmarkSuite run [options]          - Run complete benchmark suite");
        System.out.println("  java BenchmarkSuite component <a2a|mcp|zai> - Run specific component benchmarks");
        System.out.println("  java BenchmarkSuite validate <dir>        - Validate benchmark results");
        System.out.println("  java BenchmarkSuite report                 - Generate consolidated report");
        System.out.println("  java BenchmarkSuite help                  - Show this help");
        System.out.println();
        System.out.println("Run options:");
        System.out.println("  --forks <number>    : Number of JVM forks (default: 1)");
        System.out.println("  --warmup <number>   : Warmup iterations (default: 3)");
        System.out.println("  --iterations <number>: Measurement iterations (default: 5)");
        System.out.println("  --threads <number>  : Number of threads (default: CPU count)");
    }

    /**
     * Validation result container
     */
    static class ValidationResult {
        private final List<String> issues = new ArrayList<>();

        public void addIssue(String issue) {
            issues.add(issue);
        }

        public boolean isValid() {
            return issues.isEmpty();
        }

        public List<String> getIssues() {
            return Collections.unmodifiableList(issues);
        }
    }

    /**
     * Consolidated report generator
     */
    static class ConsolidatedReport {
        private final Path resultsDir;

        public ConsolidatedReport(Path resultsDir) {
            this.resultsDir = resultsDir;
        }

        public void generateHtmlReport(String outputFile) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html>\n<head>\n");
            html.append("<title>YAWL Integration Benchmarks Report</title>\n");
            html.append("<style>\n");
            html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
            html.append("table { width: 100%; border-collapse: collapse; }\n");
            html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
            html.append("th { background-color: #f2f2f2; }\n");
            html.append(".component { margin: 20px 0; }\n");
            html.append(".summary { background: #e7f3fe; padding: 10px; border-radius: 5px; }\n");
            html.append("</style>\n</head>\n<body>\n");

            html.append("<h1>YAWL Integration Benchmarks Report</h1>\n");
            html.append("<p>Generated on: ").append(LocalDateTime.now()).append("</p>\n");
            html.append("<p>Results Directory: ").append(resultsDir.toAbsolutePath()).append("</p>\n");

            // Generate summary for each component
            for (String component : COMPONENT_BENCHMARKS) {
                html.append("<div class=\"component\">\n");
                html.append("<h2>").append(component).append("</h2>\n");
                html.append("<div class=\"summary\">\n");
                html.append("<p>Implementation summary for ").append(component).append(" component.</p>\n");
                html.append("</div>\n");
                html.append("</div>\n");
            }

            html.append("</body>\n</html>");

            Files.write(Paths.get(outputFile), html.toString().getBytes());
        }
    }
}