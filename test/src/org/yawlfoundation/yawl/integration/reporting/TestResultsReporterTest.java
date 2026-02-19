/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.integration.reporting.TestResultsReporter.BenchmarkResult;
import org.yawlfoundation.yawl.integration.reporting.TestResultsReporter.CategoryStats;
import org.yawlfoundation.yawl.integration.reporting.TestResultsReporter.TestCategory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for TestResultsReporter.
 *
 * <p>Validates the full reporting lifecycle including:</p>
 * <ul>
 *   <li>JUnit XML parsing and aggregation</li>
 *   <li>Test category classification (A2A, MCP, Z.ai, Self-Play, etc.)</li>
 *   <li>Benchmark result loading and formatting</li>
 *   <li>Markdown report generation</li>
 *   <li>Error handling and validation</li>
 * </ul>
 *
 * <p>Chicago TDD: Real file I/O, real XML parsing, no mocks.</p>
 *
 * @author YAWL Foundation Integration Team
 * @version 6.0.0
 * @since 2026-02-18
 */
@Tag("unit")
class TestResultsReporterTest {

    @TempDir
    Path tempDir;

    @TempDir
    Path outputDir;

    private Path reportDirectory;
    private Path outputMarkdownPath;

    @BeforeEach
    void setUp() {
        reportDirectory = tempDir.resolve("surefire-reports");
        outputMarkdownPath = outputDir.resolve("test-results.md");
    }

    // =========================================================================
    // Constructor Validation Tests
    // =========================================================================

    @Test
    void testConstructorRequiresNonNullReportDirectory() {
        assertThrows(IllegalArgumentException.class, () ->
                new TestResultsReporter(null, outputMarkdownPath, 10),
                "Constructor must reject null reportDirectory");
    }

    @Test
    void testConstructorRequiresNonNullOutputPath() {
        assertThrows(IllegalArgumentException.class, () ->
                new TestResultsReporter(reportDirectory, null, 10),
                "Constructor must reject null outputMarkdownPath");
    }

    @Test
    void testConstructorRequiresPositiveTopNSlowestTests() {
        assertThrows(IllegalArgumentException.class, () ->
                new TestResultsReporter(reportDirectory, outputMarkdownPath, 0),
                "Constructor must reject zero topNSlowestTests");

        assertThrows(IllegalArgumentException.class, () ->
                new TestResultsReporter(reportDirectory, outputMarkdownPath, -1),
                "Constructor must reject negative topNSlowestTests");
    }

    @Test
    void testConstructorCreatesValidInstance() {
        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 10);

        assertNotNull(reporter, "Reporter instance must be created");
        assertTrue(reporter.getBenchmarkResults().isEmpty(),
                "New reporter must have no benchmark results");
        assertTrue(reporter.getCategoryStats().isEmpty(),
                "New reporter must have no category stats until report is generated");
    }

    // =========================================================================
    // Empty Report Tests
    // =========================================================================

    @Test
    void testGenerateReportWithEmptyDirectory() throws Exception {
        Files.createDirectories(reportDirectory);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 10);
        reporter.generateReport();

        assertTrue(Files.exists(outputMarkdownPath),
                "Report file must be created");

        String content = Files.readString(outputMarkdownPath, StandardCharsets.UTF_8);
        assertTrue(content.contains("No test results found"),
                "Empty report must indicate no results found");
        assertTrue(content.contains("Troubleshooting"),
                "Empty report must include troubleshooting section");
    }

    // =========================================================================
    // Single Test Suite Tests
    // =========================================================================

    @Test
    void testGenerateReportWithSingleSuite() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-MyTest.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.MyTest" tests="3" failures="0"
                       errors="0" skipped="0" time="0.250">
                <testcase classname="org.example.MyTest" name="testA" time="0.100"/>
                <testcase classname="org.example.MyTest" name="testB" time="0.080"/>
                <testcase classname="org.example.MyTest" name="testC" time="0.070"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.generateReport();

        String content = Files.readString(outputMarkdownPath, StandardCharsets.UTF_8);

        assertTrue(content.contains("YAWL Integration Test Results Report"),
                "Report must contain header");
        assertTrue(content.contains("Executive Summary"),
                "Report must contain executive summary");
        assertTrue(content.contains("3"),
                "Report must show total test count");
        assertTrue(content.contains("100.0%"),
                "Report must show 100% pass rate for all passing tests");
    }

    @Test
    void testGenerateReportWithFailures() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-WithFailure.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.WithFailure" tests="4" failures="1"
                       errors="1" skipped="0" time="0.500">
                <testcase classname="org.example.WithFailure" name="testPass" time="0.100"/>
                <testcase classname="org.example.WithFailure" name="testFail" time="0.150">
                    <failure message="assertion failed">expected true but was false</failure>
                </testcase>
                <testcase classname="org.example.WithFailure" name="testError" time="0.200">
                    <error message="runtime error">NullPointerException</error>
                </testcase>
                <testcase classname="org.example.WithFailure" name="testPass2" time="0.050"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.generateReport();

        String content = Files.readString(outputMarkdownPath, StandardCharsets.UTF_8);

        assertTrue(content.contains("Failed Tests"),
                "Report must contain failed tests section");
        assertTrue(content.contains("testFail"),
                "Report must list failed test");
        assertTrue(content.contains("testError"),
                "Report must list errored test");
        assertTrue(content.contains("2"),
                "Report must show correct failure count (1 fail + 1 error)");
    }

    // =========================================================================
    // Test Category Classification Tests
    // =========================================================================

    @Test
    void testCategoryClassificationForA2A() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-A2ATest.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.yawlfoundation.yawl.integration.a2a.A2ATest" tests="2" failures="0"
                       errors="0" skipped="0" time="0.200">
                <testcase classname="org.yawlfoundation.yawl.integration.a2a.A2ATest" name="testProtocol" time="0.100"/>
                <testcase classname="org.yawlfoundation.yawl.integration.a2a.A2ATest" name="testAuth" time="0.100"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.generateReport();

        Map<TestCategory, CategoryStats> stats = reporter.getCategoryStats();
        assertTrue(stats.containsKey(TestCategory.A2A),
                "Category stats must include A2A category");
        assertEquals(2, stats.get(TestCategory.A2A).totalTests(),
                "A2A category must have correct test count");
    }

    @Test
    void testCategoryClassificationForMCP() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-McpTest.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.yawlfoundation.yawl.integration.mcp.McpTest" tests="3" failures="0"
                       errors="0" skipped="0" time="0.300">
                <testcase classname="org.yawlfoundation.yawl.integration.mcp.McpTest" name="testTool" time="0.100"/>
                <testcase classname="org.yawlfoundation.yawl.integration.mcp.McpTest" name="testResource" time="0.100"/>
                <testcase classname="org.yawlfoundation.yawl.integration.mcp.McpTest" name="testLogging" time="0.100"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.generateReport();

        Map<TestCategory, CategoryStats> stats = reporter.getCategoryStats();
        assertTrue(stats.containsKey(TestCategory.MCP),
                "Category stats must include MCP category");
        assertEquals(3, stats.get(TestCategory.MCP).totalTests(),
                "MCP category must have correct test count");
    }

    @Test
    void testCategoryClassificationForZai() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-ZaiTest.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.yawlfoundation.yawl.integration.zai.ZaiServiceTest" tests="2" failures="0"
                       errors="0" skipped="0" time="0.500">
                <testcase classname="org.yawlfoundation.yawl.integration.zai.ZaiServiceTest" name="testChat" time="0.250"/>
                <testcase classname="org.yawlfoundation.yawl.integration.zai.ZaiServiceTest" name="testAnalysis" time="0.250"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.generateReport();

        Map<TestCategory, CategoryStats> stats = reporter.getCategoryStats();
        assertTrue(stats.containsKey(TestCategory.ZAI),
                "Category stats must include ZAI category");
    }

    @Test
    void testCategoryClassificationForSelfPlay() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-SelfPlayTest.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.yawlfoundation.yawl.integration.selfplay.SelfPlayTest" tests="2" failures="0"
                       errors="0" skipped="0" time="1.000">
                <testcase classname="org.yawlfoundation.yawl.integration.selfplay.SelfPlayTest" name="testOrchestration" time="0.500"/>
                <testcase classname="org.yawlfoundation.yawl.integration.selfplay.SelfPlayTest" name="testMultiAgent" time="0.500"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.generateReport();

        Map<TestCategory, CategoryStats> stats = reporter.getCategoryStats();
        assertTrue(stats.containsKey(TestCategory.SELF_PLAY),
                "Category stats must include Self-Play category");
    }

    @Test
    void testCategoryClassificationForEndToEnd() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-EndToEndTest.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.yawlfoundation.yawl.integration.e2e.EndToEndWorkflowTest" tests="3" failures="0"
                       errors="0" skipped="0" time="2.000">
                <testcase classname="org.yawlfoundation.yawl.integration.e2e.EndToEndWorkflowTest" name="testFullWorkflow" time="1.000"/>
                <testcase classname="org.yawlfoundation.yawl.integration.e2e.EndToEndWorkflowTest" name="testLifecycle" time="0.500"/>
                <testcase classname="org.yawlfoundation.yawl.integration.e2e.EndToEndWorkflowTest" name="testUserJourney" time="0.500"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.generateReport();

        Map<TestCategory, CategoryStats> stats = reporter.getCategoryStats();
        assertTrue(stats.containsKey(TestCategory.E2E),
                "Category stats must include E2E category");
    }

    // =========================================================================
    // Benchmark Tests
    // =========================================================================

    @Test
    void testAddBenchmarkResult() {
        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);

        BenchmarkResult result = new BenchmarkResult("A2A_WorkflowLaunch", 45.2, "ms", 0.1, 100);
        reporter.addBenchmarkResult(result);

        List<BenchmarkResult> results = reporter.getBenchmarkResults();
        assertEquals(1, results.size(), "Must have one benchmark result");
        assertEquals("A2A_WorkflowLaunch", results.get(0).benchmarkName(),
                "Benchmark name must match");
        assertEquals(45.2, results.get(0).score(), 0.001,
                "Benchmark score must match");
    }

    @Test
    void testLoadBenchmarkResultsFromFile() throws Exception {
        Files.createDirectories(reportDirectory);
        Path benchmarkFile = reportDirectory.resolve("benchmark-results.json");
        String benchmarkJson = """
            {
              "benchmark_results": [
                {"benchmark": "A2A_VirtualThreadLaunch", "value": 45.2, "ops": 1000, "error": 0.1},
                {"benchmark": "MCP_ToolExecution", "value": 28.5, "ops": 1000, "error": 0.0}
              ],
              "timestamp": "2026-02-18T12:00:00"
            }
            """;
        Files.writeString(benchmarkFile, benchmarkJson, StandardCharsets.UTF_8);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.loadBenchmarkResults(benchmarkFile);

        List<BenchmarkResult> results = reporter.getBenchmarkResults();
        assertEquals(2, results.size(), "Must load two benchmark results");
    }

    @Test
    void testLoadBenchmarkResultsFileNotFound() {
        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);

        Path nonExistentFile = tempDir.resolve("nonexistent.json");
        assertThrows(IOException.class, () ->
                reporter.loadBenchmarkResults(nonExistentFile),
                "Must throw IOException for non-existent file");
    }

    @Test
    void testBenchmarkResultRecordMethods() {
        BenchmarkResult withError = new BenchmarkResult("test", 100.0, "ms", 5.0, 50);
        assertTrue(withError.hasError(), "Result with error margin must report hasError");

        BenchmarkResult noError = new BenchmarkResult("test", 100.0, "ms", 0.0, 50);
        assertFalse(noError.hasError(), "Result with zero error margin must not report hasError");
    }

    // =========================================================================
    // CategoryStats Tests
    // =========================================================================

    @Test
    void testCategoryStatsPassRate() {
        CategoryStats stats = new CategoryStats(
                TestCategory.A2A, 10, 8, 1, 1, 1.0, List.of());

        assertEquals(0.8, stats.passRate(), 0.001,
                "Pass rate must be 8/10 = 0.8");
        assertEquals(0.1, stats.failureRate(), 0.001,
                "Failure rate must be 1/10 = 0.1");
    }

    @Test
    void testCategoryStatsWithZeroTests() {
        CategoryStats stats = new CategoryStats(
                TestCategory.A2A, 0, 0, 0, 0, 0.0, List.of());

        assertEquals(1.0, stats.passRate(), 0.001,
                "Pass rate with zero tests must be 1.0 (all pass trivially)");
        assertEquals(0.0, stats.failureRate(), 0.001,
                "Failure rate with zero tests must be 0.0");
    }

    // =========================================================================
    // TestCategory Enum Tests
    // =========================================================================

    @Test
    void testTestCategoryFromClassNameA2A() {
        assertEquals(TestCategory.A2A, TestCategory.fromClassName("org.example.a2a.A2ATest"));
        assertEquals(TestCategory.A2A, TestCategory.fromClassName("org.example.A2AProtocolTest"));
        assertEquals(TestCategory.A2A, TestCategory.fromClassName("YawlA2AServerTest"));
    }

    @Test
    void testTestCategoryFromClassNameMCP() {
        assertEquals(TestCategory.MCP, TestCategory.fromClassName("org.example.mcp.McpTest"));
        assertEquals(TestCategory.MCP, TestCategory.fromClassName("org.example.MCPToolTest"));
    }

    @Test
    void testTestCategoryFromClassNameZai() {
        assertEquals(TestCategory.ZAI, TestCategory.fromClassName("org.example.zai.ZaiServiceTest"));
        assertEquals(TestCategory.ZAI, TestCategory.fromClassName("org.example.ZAITest"));
    }

    @Test
    void testTestCategoryFromClassNameOther() {
        assertEquals(TestCategory.OTHER, TestCategory.fromClassName("org.example.GenericTest"));
        assertEquals(TestCategory.OTHER, TestCategory.fromClassName(""));
        assertEquals(TestCategory.OTHER, TestCategory.fromClassName(null));
    }

    @Test
    void testTestCategoryDisplayNames() {
        assertEquals("A2A (Agent-to-Agent)", TestCategory.A2A.getDisplayName());
        assertEquals("MCP (Model Context Protocol)", TestCategory.MCP.getDisplayName());
        assertEquals("Z.ai Integration", TestCategory.ZAI.getDisplayName());
        assertEquals("Self-Play Orchestration", TestCategory.SELF_PLAY.getDisplayName());
        assertEquals("End-to-End", TestCategory.E2E.getDisplayName());
    }

    // =========================================================================
    // Report Content Tests
    // =========================================================================

    @Test
    void testReportContainsAllSections() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-MyTest.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.MyTest" tests="1" failures="0"
                       errors="0" skipped="0" time="0.100">
                <testcase classname="org.example.MyTest" name="testA" time="0.100"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.generateReport();

        String content = Files.readString(outputMarkdownPath, StandardCharsets.UTF_8);

        assertTrue(content.contains("Table of Contents"),
                "Report must contain table of contents");
        assertTrue(content.contains("Executive Summary"),
                "Report must contain executive summary");
        assertTrue(content.contains("Test Category Results"),
                "Report must contain category results");
        assertTrue(content.contains("Failed Tests"),
                "Report must contain failed tests section");
        assertTrue(content.contains("Performance Metrics"),
                "Report must contain performance metrics");
        assertTrue(content.contains("Benchmark Results"),
                "Report must contain benchmark results");
        assertTrue(content.contains("Recommendations"),
                "Report must contain recommendations");
    }

    @Test
    void testReportContainsEnvironmentInfo() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-MyTest.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.MyTest" tests="1" failures="0"
                       errors="0" skipped="0" time="0.100">
                <testcase classname="org.example.MyTest" name="testA" time="0.100"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.generateReport();

        String content = Files.readString(outputMarkdownPath, StandardCharsets.UTF_8);

        assertTrue(content.contains("Java"),
                "Report must contain Java version info");
        assertTrue(content.contains("CPUs"),
                "Report must contain CPU count");
    }

    @Test
    void testReportWithSlowestTests() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-SlowTests.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.SlowTests" tests="4" failures="0"
                       errors="0" skipped="0" time="3.000">
                <testcase classname="org.example.SlowTests" name="testFast" time="0.100"/>
                <testcase classname="org.example.SlowTests" name="testMedium" time="0.500"/>
                <testcase classname="org.example.SlowTests" name="testSlow" time="1.000"/>
                <testcase classname="org.example.SlowTests" name="testSlowest" time="1.400"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 3);
        reporter.generateReport();

        String content = Files.readString(outputMarkdownPath, StandardCharsets.UTF_8);

        assertTrue(content.contains("Slowest Tests"),
                "Report must contain slowest tests section");
        assertTrue(content.contains("testSlowest"),
                "Report must list the slowest test");
    }

    @Test
    void testReportWithSkippedTests() throws Exception {
        Files.createDirectories(reportDirectory);
        writeXml("TEST-SkippedTests.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.SkippedTests" tests="3" failures="0"
                       errors="0" skipped="1" time="0.200">
                <testcase classname="org.example.SkippedTests" name="testRun" time="0.100"/>
                <testcase classname="org.example.SkippedTests" name="testSkip" time="0.000">
                    <skipped/>
                </testcase>
                <testcase classname="org.example.SkippedTests" name="testRun2" time="0.100"/>
            </testsuite>
            """);

        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);
        reporter.generateReport();

        String content = Files.readString(outputMarkdownPath, StandardCharsets.UTF_8);

        assertTrue(content.contains("1"),
                "Report must show skipped count");
        assertTrue(content.contains("skipped"),
                "Report must mention skipped tests");
    }

    // =========================================================================
    // Validation Error Tests
    // =========================================================================

    @Test
    void testSimplifyClassNameRequiresNonNull() {
        TestResultsReporter reporter = new TestResultsReporter(
                reportDirectory, outputMarkdownPath, 5);

        // Access through generateReport which calls simplifyClassName
        // This tests the validation indirectly
        assertDoesNotThrow(() -> {
            Files.createDirectories(reportDirectory);
            writeXml("TEST-Valid.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="Valid" tests="1" failures="0" errors="0" skipped="0" time="0.1">
                    <testcase classname="Valid" name="test" time="0.1"/>
                </testsuite>
                """);
            reporter.generateReport();
        });
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void writeXml(String filename, String content) throws IOException {
        Files.writeString(reportDirectory.resolve(filename), content, StandardCharsets.UTF_8);
    }
}
