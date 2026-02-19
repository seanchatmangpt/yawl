/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.reporting;

import org.yawlfoundation.yawl.reporting.JUnitXmlAnalyzer;
import org.yawlfoundation.yawl.reporting.JUnitXmlAnalyzer.AnalyticsReport;
import org.yawlfoundation.yawl.reporting.JUnitXmlAnalyzer.SuiteStats;
import org.yawlfoundation.yawl.reporting.JUnitXmlAnalyzer.TestCaseResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Comprehensive test results reporter for YAWL integration tests.
 *
 * <p>Aggregates JUnit XML test results from multiple test categories and generates
 * comprehensive markdown reports with pass/fail rates, timing information,
 * performance metrics, and detailed test case analysis.</p>
 *
 * <h2>Supported Test Categories:</h2>
 * <ul>
 *   <li><strong>A2A (Agent-to-Agent)</strong>: Protocol, authentication, server tests</li>
 *   <li><strong>MCP (Model Context Protocol)</strong>: Tool execution, resource access tests</li>
 *   <li><strong>Z.ai</strong>: AI service integration, chat completion tests</li>
 *   <li><strong>Self-Play</strong>: Autonomous agent orchestration tests</li>
 *   <li><strong>End-to-End</strong>: Full workflow lifecycle tests</li>
 *   <li><strong>Benchmark</strong>: Performance regression tests</li>
 * </ul>
 *
 * <h2>Report Sections:</h2>
 * <ol>
 *   <li>Executive Summary - Overall pass rate and key metrics</li>
 *   <li>Category Breakdown - Results by test category (A2A, MCP, Z.ai, etc.)</li>
 *   <li>Failed Tests - Detailed failure information with error messages</li>
 *   <li>Performance Metrics - Timing statistics and slowest tests</li>
 *   <li>Benchmark Results - JMH benchmark comparisons</li>
 *   <li>Flaky Test Detection - Tests with inconsistent results</li>
 *   <li>Recommendations - Suggested actions based on results</li>
 * </ol>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * TestResultsReporter reporter = new TestResultsReporter(
 *     Paths.get("target/surefire-reports"),
 *     Paths.get("docs/v6/latest/integration-test-results.md"),
 *     10
 * );
 *
 * // Add benchmark results if available
 * reporter.addBenchmarkResults(Paths.get("benchmark-results.json"));
 *
 * // Generate the report
 * reporter.generateReport();
 * }</pre>
 *
 * @author YAWL Foundation Integration Team
 * @version 6.0.0
 * @since 2026-02-18
 */
public class TestResultsReporter {

    /** Test categories recognized by the reporter */
    public enum TestCategory {
        A2A("A2A (Agent-to-Agent)", Set.of("a2a", "A2A", "agent")),
        MCP("MCP (Model Context Protocol)", Set.of("mcp", "MCP", "modelcontext")),
        ZAI("Z.ai Integration", Set.of("zai", "ZAI", "ZaiService")),
        SELF_PLAY("Self-Play Orchestration", Set.of("selfplay", "self-play", "SelfPlay")),
        E2E("End-to-End", Set.of("e2e", "endtoend", "EndToEnd")),
        BENCHMARK("Performance Benchmarks", Set.of("benchmark", "Benchmark", "jmh")),
        DATABASE("Database Integration", Set.of("database", "persistence", "schema")),
        AUTONOMOUS("Autonomous Agents", Set.of("autonomous", "agent", "circuit")),
        ENTERPRISE("Enterprise Patterns", Set.of("enterprise", "integration", "pattern")),
        OTHER("Other", Set.of());

        private final String displayName;
        private final Set<String> keywords;

        TestCategory(String displayName, Set<String> keywords) {
            this.displayName = displayName;
            this.keywords = keywords;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static TestCategory fromClassName(String className) {
            if (className == null || className.isEmpty()) {
                return OTHER;
            }
            String lowerName = className.toLowerCase();
            for (TestCategory category : values()) {
                if (category != OTHER) {
                    for (String keyword : category.keywords) {
                        if (lowerName.contains(keyword.toLowerCase())) {
                            return category;
                        }
                    }
                }
            }
            return OTHER;
        }
    }

    /** Benchmark result record for performance metrics */
    public record BenchmarkResult(
            String benchmarkName,
            double score,
            String scoreUnit,
            double errorMargin,
            int sampleCount
    ) {
        public boolean hasError() {
            return errorMargin > 0;
        }
    }

    /** Aggregated statistics for a test category */
    public record CategoryStats(
            TestCategory category,
            int totalTests,
            int passedTests,
            int failedTests,
            int skippedTests,
            double totalDurationSeconds,
            List<TestCaseResult> failedTestCases
    ) {
        public double passRate() {
            return totalTests == 0 ? 1.0 : (double) passedTests / totalTests;
        }

        public double failureRate() {
            return totalTests == 0 ? 0.0 : (double) failedTests / totalTests;
        }
    }

    private final JUnitXmlAnalyzer analyzer;
    private final int topNSlowestTests;
    private final Path reportDirectory;
    private final Path outputMarkdownPath;
    private final List<BenchmarkResult> benchmarkResults;
    private final Map<TestCategory, CategoryStats> categoryStatsMap;
    private String buildInfo;
    private String environmentInfo;

    /**
     * Creates a new TestResultsReporter instance.
     *
     * @param reportDirectory directory containing JUnit XML test results
     * @param outputMarkdownPath path where the markdown report will be saved
     * @param topNSlowestTests number of slowest tests to include in the report
     * @throws IllegalArgumentException if any parameter is null
     */
    public TestResultsReporter(Path reportDirectory, Path outputMarkdownPath, int topNSlowestTests) {
        if (reportDirectory == null) {
            throw new IllegalArgumentException("reportDirectory must not be null");
        }
        if (outputMarkdownPath == null) {
            throw new IllegalArgumentException("outputMarkdownPath must not be null");
        }
        if (topNSlowestTests < 1) {
            throw new IllegalArgumentException("topNSlowestTests must be at least 1");
        }

        this.analyzer = new JUnitXmlAnalyzer();
        this.topNSlowestTests = topNSlowestTests;
        this.reportDirectory = reportDirectory;
        this.outputMarkdownPath = outputMarkdownPath;
        this.benchmarkResults = new ArrayList<>();
        this.categoryStatsMap = new TreeMap<>(Comparator.comparing(TestCategory::ordinal));
        this.buildInfo = collectBuildInfo();
        this.environmentInfo = collectEnvironmentInfo();
    }

    /**
     * Main method to generate a test results report.
     *
     * @param args command line arguments
     *            [0] - directory containing JUnit XML files
     *            [1] - output markdown file path
     *            [2] - number of slowest tests to include (optional, default: 10)
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java TestResultsReporter <xmlDirectory> <outputMarkdownPath> [topNSlowestTests]");
            System.err.println("Example: java TestResultsReporter target/surefire-reports docs/v6/latest/integration-test-results.md 10");
            System.exit(1);
        }

        Path reportDirectory = Paths.get(args[0]);
        Path outputMarkdownPath = Paths.get(args[1]);
        int topNSlowest = args.length > 2 ? Integer.parseInt(args[2]) : 10;

        try {
            TestResultsReporter reporter = new TestResultsReporter(reportDirectory, outputMarkdownPath, topNSlowest);

            // Check for benchmark results file
            Path benchmarkFile = reportDirectory.resolve("benchmark-results.json");
            if (Files.exists(benchmarkFile)) {
                reporter.loadBenchmarkResults(benchmarkFile);
            }

            reporter.generateReport();
            System.out.println("Report generated successfully: " + outputMarkdownPath);
        } catch (Exception e) {
            System.err.println("Error generating report: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Adds benchmark results from a JSON file.
     *
     * @param benchmarkFile path to the benchmark results JSON file
     * @throws IOException if the file cannot be read or parsed
     */
    public void loadBenchmarkResults(Path benchmarkFile) throws IOException {
        if (!Files.exists(benchmarkFile)) {
            throw new IOException("Benchmark file not found: " + benchmarkFile);
        }

        String content = Files.readString(benchmarkFile, StandardCharsets.UTF_8);
        parseBenchmarkJson(content);
    }

    /**
     * Adds a single benchmark result.
     *
     * @param result the benchmark result to add
     */
    public void addBenchmarkResult(BenchmarkResult result) {
        if (result != null) {
            benchmarkResults.add(result);
        }
    }

    /**
     * Generates and saves the test results report.
     *
     * @throws Exception if an error occurs during report generation
     */
    public void generateReport() throws Exception {
        // Parse all JUnit XML files in the report directory
        List<SuiteStats> suites = analyzer.parseDirectory(reportDirectory);

        if (suites.isEmpty()) {
            System.out.println("No test suite files found in: " + reportDirectory);
            createEmptyReport();
            return;
        }

        // Build analytics report
        AnalyticsReport analyticsReport = analyzer.buildReport(suites, topNSlowestTests);

        // Aggregate by category
        aggregateByCategory(suites);

        // Generate markdown content
        String markdownContent = generateMarkdownReport(analyticsReport, LocalDateTime.now());

        // Write markdown file
        Files.createDirectories(outputMarkdownPath.getParent());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputMarkdownPath.toFile()))) {
            writer.write(markdownContent);
        }
    }

    /**
     * Gets the aggregated statistics by category.
     *
     * @return map of category to statistics
     */
    public Map<TestCategory, CategoryStats> getCategoryStats() {
        return new TreeMap<>(categoryStatsMap);
    }

    /**
     * Gets all loaded benchmark results.
     *
     * @return list of benchmark results
     */
    public List<BenchmarkResult> getBenchmarkResults() {
        return new ArrayList<>(benchmarkResults);
    }

    // =========================================================================
    // Private Implementation
    // =========================================================================

    private void aggregateByCategory(List<SuiteStats> suites) {
        Map<TestCategory, List<TestCaseResult>> testsByCategory = new HashMap<>();

        for (TestCategory category : TestCategory.values()) {
            testsByCategory.put(category, new ArrayList<>());
        }

        // Classify each test case by category
        for (SuiteStats suite : suites) {
            for (TestCaseResult testCase : suite.testCases()) {
                TestCategory category = TestCategory.fromClassName(testCase.className());
                testsByCategory.get(category).add(testCase);
            }
        }

        // Build stats for each category
        for (TestCategory category : TestCategory.values()) {
            List<TestCaseResult> tests = testsByCategory.get(category);
            if (tests.isEmpty() && category != TestCategory.OTHER) {
                continue; // Skip empty categories except OTHER which aggregates unclassified
            }

            int total = tests.size();
            int passed = (int) tests.stream().filter(TestCaseResult::isPassed).count();
            int failed = (int) tests.stream().filter(t -> t.isFailed() || t.isError()).count();
            int skipped = (int) tests.stream().filter(TestCaseResult::isSkipped).count();
            double duration = tests.stream().mapToDouble(TestCaseResult::elapsedSeconds).sum();

            List<TestCaseResult> failedTests = tests.stream()
                    .filter(t -> t.isFailed() || t.isError())
                    .collect(Collectors.toList());

            categoryStatsMap.put(category, new CategoryStats(
                    category, total, passed, failed, skipped, duration, failedTests
            ));
        }
    }

    private String generateMarkdownReport(AnalyticsReport analyticsReport, LocalDateTime generatedAt) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# YAWL Integration Test Results Report\n\n");
        sb.append("> **Generated:** ").append(generatedAt.format(DateTimeFormatter.ISO_DATE_TIME)).append("\n");
        if (buildInfo != null && !buildInfo.isEmpty()) {
            sb.append("> **Build:** ").append(escapeMarkdown(buildInfo)).append("\n");
        }
        if (environmentInfo != null && !environmentInfo.isEmpty()) {
            sb.append("> **Environment:** ").append(escapeMarkdown(environmentInfo)).append("\n");
        }
        sb.append("\n");

        // Table of Contents
        sb.append("## Table of Contents\n\n");
        sb.append("1. [Executive Summary](#executive-summary)\n");
        sb.append("2. [Test Category Results](#test-category-results)\n");
        sb.append("3. [Failed Tests](#failed-tests)\n");
        sb.append("4. [Performance Metrics](#performance-metrics)\n");
        sb.append("5. [Benchmark Results](#benchmark-results)\n");
        sb.append("6. [Recommendations](#recommendations)\n\n");

        // Executive Summary
        generateExecutiveSummary(sb, analyticsReport);

        // Test Category Results
        generateCategoryResults(sb);

        // Failed Tests
        generateFailedTestsSection(sb, analyticsReport);

        // Performance Metrics
        generatePerformanceMetrics(sb, analyticsReport);

        // Benchmark Results
        generateBenchmarkSection(sb);

        // Recommendations
        generateRecommendations(sb, analyticsReport);

        // Footer
        sb.append("---\n\n");
        sb.append("*Report generated by YAWL TestResultsReporter v6.0.0*\n");
        sb.append("*Report Format: Markdown (GitHub Flavored)*\n");

        return sb.toString();
    }

    private void generateExecutiveSummary(StringBuilder sb, AnalyticsReport report) {
        sb.append("## Executive Summary\n\n");

        // Overall status badge
        String statusBadge = report.totalFailed() == 0
                ? "![Passed](https://img.shields.io/badge/Status-PASSED-brightgreen)"
                : "![Failed](https://img.shields.io/badge/Status-FAILED-red)";
        sb.append(statusBadge).append("\n\n");

        // Summary table
        sb.append("| Metric | Value | Details |\n");
        sb.append("|--------|-------|----------|\n");

        double passRate = report.overallPassRate() * 100;
        String passRateDisplay = String.format("%.1f%%", passRate);
        String passRateBar = generateProgressBar(passRate);

        sb.append("| **Total Test Suites** | ").append(report.totalSuites()).append(" | XML report files parsed |\n");
        sb.append("| **Total Tests** | ").append(report.totalTests()).append(" | All test cases |\n");
        sb.append("| **Passed** | ").append(report.totalPassed()).append(" | ").append(passRateDisplay).append(" pass rate |\n");
        sb.append("| **Failed** | ").append(report.totalFailed()).append(" | Failures + Errors |\n");
        sb.append("| **Skipped** | ").append(report.totalSkipped()).append(" | Disabled or skipped |\n");
        sb.append("| **Pass Rate** | ").append(passRateDisplay).append(" | ").append(passRateBar).append(" |\n");
        sb.append("| **Duration** | ").append(formatDuration(report.totalSeconds())).append(" | Total execution time |\n");
        sb.append("\n");

        // Quick status indicators
        sb.append("### Quick Status\n\n");

        if (report.totalFailed() == 0) {
            sb.append("- :white_check_mark: All tests passed successfully\n");
        } else {
            sb.append("- :x: **").append(report.totalFailed()).append("** test(s) failed\n");
        }

        if (report.totalSkipped() > 0) {
            sb.append("- :warning: **").append(report.totalSkipped()).append("** test(s) skipped\n");
        }

        if (!report.slowestTests().isEmpty()) {
            double slowestTime = report.slowestTests().stream()
                    .mapToDouble(TestCaseResult::elapsedSeconds)
                    .max()
                    .orElse(0);
            if (slowestTime > 5.0) {
                sb.append("- :hourglass_flowing_sand: Slowest test took **").append(formatDuration(slowestTime)).append("**\n");
            }
        }

        sb.append("\n");
    }

    private void generateCategoryResults(StringBuilder sb) {
        sb.append("## Test Category Results\n\n");
        sb.append("Breakdown of test results by integration category:\n\n");

        sb.append("| Category | Tests | Passed | Failed | Skipped | Pass Rate | Duration |\n");
        sb.append("|----------|-------|--------|--------|---------|-----------|----------|\n");

        for (TestCategory category : TestCategory.values()) {
            CategoryStats stats = categoryStatsMap.get(category);
            if (stats == null || stats.totalTests() == 0) {
                continue;
            }

            String statusIcon = stats.failedTests() == 0 ? ":white_check_mark:" : ":x:";
            sb.append("| ").append(statusIcon).append(" **").append(category.getDisplayName()).append("** | ");
            sb.append(stats.totalTests()).append(" | ");
            sb.append(stats.passedTests()).append(" | ");
            sb.append(stats.failedTests()).append(" | ");
            sb.append(stats.skippedTests()).append(" | ");
            sb.append(String.format("%.1f%%", stats.passRate() * 100)).append(" | ");
            sb.append(formatDuration(stats.totalDurationSeconds())).append(" |\n");
        }

        sb.append("\n");

        // Category details
        sb.append("### Category Descriptions\n\n");
        sb.append("| Category | Description |\n");
        sb.append("|----------|-------------|\n");
        sb.append("| **A2A** | Agent-to-Agent protocol, authentication, and server tests |\n");
        sb.append("| **MCP** | Model Context Protocol tool execution and resource access |\n");
        sb.append("| **Z.ai** | AI service integration, chat completion, and workflow analysis |\n");
        sb.append("| **Self-Play** | Autonomous agent orchestration and multi-agent scenarios |\n");
        sb.append("| **End-to-End** | Complete workflow lifecycle from specification to completion |\n");
        sb.append("| **Database** | Persistence, schema migration, and CRUD operations |\n");
        sb.append("| **Autonomous** | Circuit breakers, retry policies, and agent capabilities |\n");
        sb.append("| **Enterprise** | Integration patterns and enterprise workflow scenarios |\n");
        sb.append("| **Benchmark** | JMH performance benchmarks and regression tests |\n");
        sb.append("\n");
    }

    private void generateFailedTestsSection(StringBuilder sb, AnalyticsReport report) {
        sb.append("## Failed Tests\n\n");

        if (report.failedTests().isEmpty()) {
            sb.append(":white_check_mark: **No failed tests!** All tests passed successfully.\n\n");
            return;
        }

        sb.append(":x: **").append(report.failedTests().size()).append("** test(s) failed:\n\n");
        sb.append("| Status | Class | Test Method | Duration | Error Message |\n");
        sb.append("|--------|-------|-------------|----------|---------------|\n");

        for (TestCaseResult test : report.failedTests()) {
            sb.append("| ").append(test.status()).append(" | ");
            sb.append(escapeMarkdown(simplifyClassName(test.className()))).append(" | ");
            sb.append(escapeMarkdown(test.testName())).append(" | ");
            sb.append(formatDuration(test.elapsedSeconds())).append(" | ");

            String message = test.message();
            if (message != null && !message.isEmpty()) {
                // Truncate long messages
                String truncated = message.length() > 80 ? message.substring(0, 77) + "..." : message;
                sb.append(escapeMarkdown(truncated.replace("\n", " ")));
            } else {
                sb.append("-");
            }
            sb.append(" |\n");
        }

        sb.append("\n");

        // Detailed failure analysis
        sb.append("### Failure Analysis\n\n");

        Map<String, Long> failuresByCategory = report.failedTests().stream()
                .collect(Collectors.groupingBy(
                        t -> TestCategory.fromClassName(t.className()).getDisplayName(),
                        Collectors.counting()
                ));

        if (!failuresByCategory.isEmpty()) {
            sb.append("**Failures by Category:**\n\n");
            failuresByCategory.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> sb.append("- ").append(entry.getKey()).append(": **")
                            .append(entry.getValue()).append("** failure(s)\n"));
            sb.append("\n");
        }
    }

    private void generatePerformanceMetrics(StringBuilder sb, AnalyticsReport report) {
        sb.append("## Performance Metrics\n\n");

        sb.append("### Execution Time Statistics\n\n");
        sb.append("| Metric | Value |\n");
        sb.append("|--------|-------|\n");
        sb.append("| **Total Duration** | ").append(formatDuration(report.totalSeconds())).append(" |\n");

        if (report.totalTests() > 0) {
            double avgDuration = report.totalSeconds() / report.totalTests();
            sb.append("| **Average Test Duration** | ").append(formatDuration(avgDuration)).append(" |\n");
        }

        if (!report.slowestTests().isEmpty()) {
            double maxDuration = report.slowestTests().stream()
                    .mapToDouble(TestCaseResult::elapsedSeconds)
                    .max()
                    .orElse(0);
            sb.append("| **Longest Test** | ").append(formatDuration(maxDuration)).append(" |\n");
        }

        sb.append("\n");

        // Slowest tests
        if (!report.slowestTests().isEmpty()) {
            sb.append("### Slowest Tests (Top ").append(topNSlowestTests).append(")\n\n");
            sb.append("| Duration | Class | Test Method |\n");
            sb.append("|----------|-------|-------------|\n");

            for (TestCaseResult test : report.slowestTests()) {
                sb.append("| ").append(formatDuration(test.elapsedSeconds())).append(" | ");
                sb.append(escapeMarkdown(simplifyClassName(test.className()))).append(" | ");
                sb.append(escapeMarkdown(test.testName())).append(" |\n");
            }
            sb.append("\n");
        }

        // Performance categories
        sb.append("### Performance Distribution\n\n");

        List<TestCaseResult> allTests = new ArrayList<>();
        try {
            for (SuiteStats suite : analyzer.parseDirectory(reportDirectory)) {
                allTests.addAll(suite.testCases());
            }
        } catch (Exception e) {
            // Ignore parsing errors for performance distribution
        }

        if (!allTests.isEmpty()) {
            long fast = allTests.stream().filter(t -> t.elapsedSeconds() < 0.1).count();
            long normal = allTests.stream().filter(t -> t.elapsedSeconds() >= 0.1 && t.elapsedSeconds() < 1.0).count();
            long slow = allTests.stream().filter(t -> t.elapsedSeconds() >= 1.0).count();

            sb.append("| Speed Category | Count | Percentage |\n");
            sb.append("|----------------|-------|------------|\n");
            sb.append("| Fast (<100ms) | ").append(fast).append(" | ")
                    .append(String.format("%.1f%%", fast * 100.0 / allTests.size())).append(" |\n");
            sb.append("| Normal (100ms-1s) | ").append(normal).append(" | ")
                    .append(String.format("%.1f%%", normal * 100.0 / allTests.size())).append(" |\n");
            sb.append("| Slow (>1s) | ").append(slow).append(" | ")
                    .append(String.format("%.1f%%", slow * 100.0 / allTests.size())).append(" |\n");
            sb.append("\n");
        }
    }

    private void generateBenchmarkSection(StringBuilder sb) {
        sb.append("## Benchmark Results\n\n");

        if (benchmarkResults.isEmpty()) {
            sb.append("No benchmark results available.\n\n");
            sb.append("*Run benchmarks with: `java -cp ... BenchmarkRunner run --type all --output benchmark-results.json`\n\n");
            return;
        }

        sb.append("### JMH Performance Benchmarks\n\n");
        sb.append("| Benchmark | Score | Unit | Error | Samples |\n");
        sb.append("|-----------|-------|------|-------|----------|\n");

        for (BenchmarkResult result : benchmarkResults) {
            sb.append("| ").append(escapeMarkdown(simplifyBenchmarkName(result.benchmarkName()))).append(" | ");
            sb.append(String.format("%.2f", result.score())).append(" | ");
            sb.append(result.scoreUnit()).append(" | ");

            if (result.hasError()) {
                sb.append(String.format("%.2f%%", result.errorMargin()));
            } else {
                sb.append("-");
            }
            sb.append(" | ").append(result.sampleCount()).append(" |\n");
        }

        sb.append("\n");

        // Benchmark summary
        sb.append("### Benchmark Summary\n\n");

        Map<String, List<BenchmarkResult>> byPrefix = benchmarkResults.stream()
                .collect(Collectors.groupingBy(r -> extractBenchmarkPrefix(r.benchmarkName())));

        for (Map.Entry<String, List<BenchmarkResult>> entry : byPrefix.entrySet()) {
            String prefix = entry.getKey();
            List<BenchmarkResult> results = entry.getValue();

            sb.append("**").append(prefix).append(":** ");
            sb.append(results.size()).append(" benchmark(s)\n");

            double avgScore = results.stream().mapToDouble(BenchmarkResult::score).average().orElse(0);
            sb.append("  - Average score: ").append(String.format("%.2f", avgScore)).append("\n");
        }

        sb.append("\n");
    }

    private void generateRecommendations(StringBuilder sb, AnalyticsReport report) {
        sb.append("## Recommendations\n\n");

        List<String> recommendations = new ArrayList<>();

        // Check for failures
        if (report.totalFailed() > 0) {
            recommendations.add(":x: **Address " + report.totalFailed() + " failing test(s)** - Review the Failed Tests section for details");
        }

        // Check for skipped tests
        if (report.totalSkipped() > 0) {
            recommendations.add(":warning: **Review " + report.totalSkipped() + " skipped test(s)** - Ensure tests are not accidentally disabled");
        }

        // Check pass rate
        double passRate = report.overallPassRate();
        if (passRate < 0.8 && report.totalTests() > 0) {
            recommendations.add(":chart_with_downwards_trend: **Improve pass rate** - Current: " + String.format("%.1f%%", passRate * 100) + ", Target: 80%+");
        }

        // Check for slow tests
        if (!report.slowestTests().isEmpty()) {
            double slowestTime = report.slowestTests().stream()
                    .mapToDouble(TestCaseResult::elapsedSeconds)
                    .max()
                    .orElse(0);
            if (slowestTime > 10.0) {
                recommendations.add(":hourglass: **Optimize slow tests** - Slowest test took " + formatDuration(slowestTime) + ", consider refactoring");
            }
        }

        // Category-specific recommendations
        for (Map.Entry<TestCategory, CategoryStats> entry : categoryStatsMap.entrySet()) {
            CategoryStats stats = entry.getValue();
            if (stats.failedTests() > 0) {
                recommendations.add(":wrench: **" + entry.getKey().getDisplayName() + "** - Fix " + stats.failedTests() + " failing test(s)");
            }
        }

        // Positive recommendations
        if (recommendations.isEmpty()) {
            recommendations.add(":white_check_mark: **All tests passing** - Great work! Consider increasing test coverage");
            recommendations.add(":rocket: **Run benchmarks** - Performance testing recommended before deployment");
        }

        // Add flaky test detection recommendation
        recommendations.add(":mag: **Flaky test detection** - Run tests multiple times to identify intermittent failures");

        // Write recommendations
        for (String rec : recommendations) {
            sb.append("- ").append(rec).append("\n");
        }

        sb.append("\n");

        // Next steps
        sb.append("### Next Steps\n\n");
        sb.append("1. Review and address any failing tests\n");
        sb.append("2. Run full integration suite: `mvn test -Dtest=IntegrationTestSuite`\n");
        sb.append("3. Generate coverage report: `mvn jacoco:report`\n");
        sb.append("4. Run benchmarks: `java BenchmarkRunner run --type all`\n");
        sb.append("5. Check for flaky tests: Run test suite 3+ times and compare results\n");
        sb.append("\n");
    }

    private void createEmptyReport() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# YAWL Integration Test Results Report\n\n");
        sb.append("## Summary\n\n");
        sb.append(":warning: **No test results found** in the specified directory: `")
                .append(escapeMarkdown(reportDirectory.toString())).append("`\n\n");
        sb.append("### Troubleshooting\n\n");
        sb.append("1. **Verify Maven Surefire reports** are generated in the specified directory\n");
        sb.append("2. **Check directory path** is correct and accessible\n");
        sb.append("3. **Ensure tests have been executed** with: `mvn test`\n");
        sb.append("4. **Look for XML files** matching pattern: `TEST-*.xml`\n\n");
        sb.append("### Expected Directory Structure\n\n");
        sb.append("```\n");
        sb.append("target/\n");
        sb.append("└── surefire-reports/\n");
        sb.append("    ├── TEST-org.example.MyTest.xml\n");
        sb.append("    ├── TEST-org.example.OtherTest.xml\n");
        sb.append("    └── ...\n");
        sb.append("```\n\n");
        sb.append("---\n");
        sb.append("*Report generated by YAWL TestResultsReporter v6.0.0*\n");

        Files.createDirectories(outputMarkdownPath.getParent());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputMarkdownPath.toFile()))) {
            writer.write(sb.toString());
        }
    }

    private void parseBenchmarkJson(String jsonContent) {
        // Simple JSON parsing for benchmark results
        // Format: {"benchmark_results": [{"benchmark": "name", "value": 1.0, ...}]}

        Pattern arrayPattern = Pattern.compile("\"benchmark_results\"\\s*:\\s*\\[([\\s\\S]*?)\\]", Pattern.MULTILINE);
        Matcher arrayMatcher = arrayPattern.matcher(jsonContent);

        if (arrayMatcher.find()) {
            String arrayContent = arrayMatcher.group(1);

            // Parse individual benchmark objects
            Pattern objectPattern = Pattern.compile("\\{[^{}]*\"benchmark\"[^{}]*\\}");
            Matcher objectMatcher = objectPattern.matcher(arrayContent);

            while (objectMatcher.find()) {
                String obj = objectMatcher.group();
                BenchmarkResult result = parseBenchmarkObject(obj);
                if (result != null) {
                    benchmarkResults.add(result);
                }
            }
        }
    }

    private BenchmarkResult parseBenchmarkObject(String json) {
        try {
            String name = extractJsonString(json, "benchmark");
            double value = extractJsonDouble(json, "value");
            int ops = extractJsonInt(json, "ops");
            double error = extractJsonDouble(json, "error");

            return new BenchmarkResult(name, value, "ms", error, ops);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private double extractJsonDouble(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.+-]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private int extractJsonInt(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private String formatDuration(double seconds) {
        if (seconds < 0.001) {
            return String.format("%.0f us", seconds * 1_000_000);
        } else if (seconds < 1) {
            return String.format("%.0f ms", seconds * 1000);
        } else if (seconds < 60) {
            return String.format("%.2f s", seconds);
        } else if (seconds < 3600) {
            long minutes = (long) (seconds / 60);
            double remainingSeconds = seconds % 60;
            return String.format("%d min %.1f s", minutes, remainingSeconds);
        } else {
            long hours = (long) (seconds / 3600);
            long remainingMinutes = (long) ((seconds % 3600) / 60);
            return String.format("%d h %d min", hours, remainingMinutes);
        }
    }

    private String generateProgressBar(double percentage) {
        int filled = (int) (percentage / 10);
        int empty = 10 - filled;
        return "[" + "=".repeat(Math.max(0, filled)) + " ".repeat(Math.max(0, empty)) + "]";
    }

    private String simplifyClassName(String className) {
        if (className == null) {
            throw new IllegalArgumentException("className must not be null");
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    private String simplifyBenchmarkName(String benchmarkName) {
        if (benchmarkName == null) {
            throw new IllegalArgumentException("benchmarkName must not be null");
        }
        // Remove common prefixes
        return benchmarkName
                .replace("org.yawlfoundation.yawl.integration.benchmark.", "")
                .replace("IntegrationBenchmarks.", "");
    }

    private String extractBenchmarkPrefix(String benchmarkName) {
        if (benchmarkName == null) {
            return "Unknown";
        }

        if (benchmarkName.contains("a2a") || benchmarkName.contains("A2A")) {
            return "A2A";
        } else if (benchmarkName.contains("mcp") || benchmarkName.contains("MCP")) {
            return "MCP";
        } else if (benchmarkName.contains("zai") || benchmarkName.contains("ZAI")) {
            return "Z.ai";
        } else if (benchmarkName.contains("engine") || benchmarkName.contains("workflow")) {
            return "Engine";
        }
        return "General";
    }

    private String escapeMarkdown(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null for markdown escaping");
        }
        return text
                .replace("|", "\\|")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("`", "\\`")
                .replace("#", "\\#");
    }

    private String collectBuildInfo() {
        StringBuilder info = new StringBuilder();

        String buildNumber = System.getenv("BUILD_NUMBER");
        if (buildNumber != null) {
            info.append("Build #").append(buildNumber);
        }

        String commitSha = System.getenv("GIT_COMMIT");
        if (commitSha != null) {
            if (info.length() > 0) info.append(" | ");
            info.append("Commit: ").append(commitSha.substring(0, Math.min(8, commitSha.length())));
        }

        String branch = System.getenv("GIT_BRANCH");
        if (branch != null) {
            if (info.length() > 0) info.append(" | ");
            info.append("Branch: ").append(branch);
        }

        return info.toString();
    }

    private String collectEnvironmentInfo() {
        StringBuilder info = new StringBuilder();

        info.append("Java ").append(System.getProperty("java.version"));

        String osName = System.getProperty("os.name");
        if (osName != null) {
            info.append(" | ").append(osName);
        }

        int processors = Runtime.getRuntime().availableProcessors();
        info.append(" | ").append(processors).append(" CPUs");

        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        info.append(" | ").append(maxMemory).append("MB max heap");

        return info.toString();
    }
}
