/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stress;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Orchestrator class that runs all stress tests, collects metrics,
 * and generates comprehensive reports with breaking points analysis.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
public class StressTestOrchestrator {

    private static final Logger logger = Logger.getLogger(StressTestOrchestrator.class.getName());

    private final List<StressTest> stressTests;
    private final StressTestReport report;
    private boolean verbose = false;

    /**
     * Constructs a new StressTestOrchestrator with default stress tests.
     */
    public StressTestOrchestrator() {
        this.stressTests = new ArrayList<>();
        this.report = new StressTestReport();

        // Register all stress tests
        registerStressTests();
    }

    /**
     * Registers all stress tests to be run.
     */
    private void registerStressTests() {
        stressTests.add(new BasicLatencyStressTest());
        stressTests.add(new BasicThroughputStressTest());
        stressTests.add(new BasicConcurrencyStressTest());
        stressTests.add(new BasicMemoryStressTest());
    }

    /**
     * Runs all stress tests and generates a report.
     *
     * @return the generated test report
     * @throws StressTestException if any test fails critically
     */
    public StressTestReport runAllTests() throws StressTestException {
        Instant startTime = Instant.now();
        report.setStartTime(startTime);
        report.setTestClasses(stressTests.stream()
                .map(StressTest::getTestName)
                .toList());

        logger.info("Starting stress test execution with " + stressTests.size() + " tests");

        for (StressTest test : stressTests) {
            runSingleTest(test);
        }

        Instant endTime = Instant.now();
        report.setEndTime(endTime);
        report.setDuration(Duration.between(startTime, endTime));

        // Analyze breaking points
        analyzeBreakingPoints();

        // Generate report
        generateReport();

        logger.info("Stress test execution completed");
        return report;
    }

    /**
     * Runs a single stress test and collects results.
     *
     * @param test the stress test to run
     */
    private void runSingleTest(StressTest test) {
        String testName = test.getTestName();
        logger.info("Running stress test: " + testName);

        try {
            // Prepare test
            test.prepare();

            // Run test
            StressTestResult result = test.run();

            // Collect metrics
            collectMetrics(testName, result);

            // Analyze results for breaking points
            if (result instanceof BasicLatencyStressTest.LatencyTestResult) {
                analyzeLatencyBreakingPoints(testName, (BasicLatencyStressTest.LatencyTestResult) result);
            } else if (result instanceof BasicThroughputStressTest.ThroughputTestResult) {
                analyzeThroughputBreakingPoints(testName, (BasicThroughputStressTest.ThroughputTestResult) result);
            } else if (result instanceof BasicConcurrencyStressTest.ConcurrencyTestResult) {
                analyzeConcurrencyBreakingPoints(testName, (BasicConcurrencyStressTest.ConcurrencyTestResult) result);
            } else if (result instanceof BasicMemoryStressTest.MemoryTestResult) {
                analyzeMemoryBreakingPoints(testName, (BasicMemoryStressTest.MemoryTestResult) result);
            }

            test.cleanup();

            if (verbose) {
                System.out.println("✓ " + testName + " completed successfully");
            }

        } catch (Exception e) {
            logger.severe("Test failed: " + testName + " - " + e.getMessage());
            report.addFailedTest(testName, e.getMessage());

            if (verbose) {
                System.err.println("✗ " + testName + " failed: " + e.getMessage());
            }
        }
    }

    /**
     * Collects metrics from a test result.
     *
     * @param testName the name of the test
     * @param result the test result
     */
    private void collectMetrics(String testName, StressTestResult result) {
        TestMetrics metrics = new TestMetrics();
        metrics.setTestName(testName);
        metrics.setDuration(result.getDuration());
        metrics.setSuccess(result.isSuccess());
        metrics.setErrorCount(result.getErrorCount());

        report.addTestMetrics(testName, metrics);
    }

    /**
     * Analyzes latency breaking points.
     *
     * @param testName the test name
     * @param result the latency test result
     */
    private void analyzeLatencyBreakingPoints(String testName, BasicLatencyStressTest.LatencyTestResult result) {
        double p95Threshold = 100; // 100ms threshold
        if (result.getP95ResponseTime() > p95Threshold) {
            report.getCriticalBreakingPoints().add(testName + ": P95 latency exceeds threshold (" + result.getP95ResponseTime() + "ms)");
        }
    }

    /**
     * Analyzes throughput breaking points.
     *
     * @param testName the test name
     * @param result the throughput test result
     */
    private void analyzeThroughputBreakingPoints(String testName, BasicThroughputStressTest.ThroughputTestResult result) {
        double threshold = result.getMaxThroughput() * 0.8; // 80% of max
        if (result.getThroughputDataPoints().stream()
            .anyMatch(p -> p.getThroughput() < threshold)) {
            report.getWarningBreakingPoints().add(testName + ": Throughput degraded below 80%");
        }
    }

    /**
     * Analyzes concurrency breaking points.
     *
     * @param testName the test name
     * @param result the concurrency test result
     */
    private void analyzeConcurrencyBreakingPoints(String testName, BasicConcurrencyStressTest.ConcurrencyTestResult result) {
        int deadlockThreshold = 10; // error count threshold
        if (result.getConcurrencyDataPoints().stream()
            .anyMatch(p -> p.getErrorCount() > deadlockThreshold)) {
            report.getCriticalBreakingPoints().add(testName + ": High error count detected");
        }
    }

    /**
     * Analyzes memory breaking points.
     *
     * @param testName the test name
     * @param result the memory test result
     */
    private void analyzeMemoryBreakingPoints(String testName, BasicMemoryStressTest.MemoryTestResult result) {
        double leakThreshold = 1.0; // 1MB/min
        if (result.getMemoryDataPoints().stream()
            .anyMatch(p -> p.getLeakRate() > leakThreshold)) {
            report.getCriticalBreakingPoints().add(testName + ": Memory leak detected");
        }
    }

    /**
     * Analyzes overall breaking points and sets report status.
     */
    private void analyzeBreakingPoints() {
        if (!report.getCriticalBreakingPoints().isEmpty()) {
            report.setOverallStatus("CRITICAL");
        } else if (!report.getWarningBreakingPoints().isEmpty()) {
            report.setOverallStatus("WARNING");
        } else {
            report.setOverallStatus("GREEN");
        }
    }

    /**
     * Generates and saves the report to JSON.
     */
    private void generateReport() {
        // Create reports directory if it doesn't exist
        Path reportsDir = Paths.get(".claude", "reports");
        try {
            Files.createDirectories(reportsDir);
        } catch (IOException e) {
            logger.severe("Failed to create reports directory: " + e.getMessage());
            return;
        }

        // Generate report filename
        String timestamp = Instant.now().toString().replace(":", "-");
        String reportFile = "stress-test-report-" + timestamp + ".json";
        Path reportPath = reportsDir.resolve(reportFile);

        try {
            String json = report.toJSON();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportPath.toFile()))) {
                writer.write(json);
            }

            logger.info("Report saved to: " + reportPath);
        } catch (IOException e) {
            logger.severe("Failed to save report: " + e.getMessage());
        }
    }

    /**
     * Sets verbose mode for detailed output.
     *
     * @param verbose true for verbose output
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Gets the list of test classes that will be run.
     *
     * @return list of test class names
     */
    public List<String> getTestClasses() {
        return stressTests.stream().map(StressTest::getTestName).toList();
    }

    /**
     * Checks if verbose mode is enabled.
     *
     * @return true if verbose output is enabled
     */
    public boolean isVerbose() {
        return verbose;
    }
}