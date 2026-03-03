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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone stress test runner that can be executed directly from command line
 * without requiring a full Maven compilation.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
public class StandaloneStressTestRunner {

    private final List<StressTest> stressTests;
    private final List<String> criticalPoints = new ArrayList<>();
    private final List<String> warningPoints = new ArrayList<>();
    private final List<String> testClasses = new ArrayList<>();
    private Duration totalDuration;
    private Instant startTime;

    public StandaloneStressTestRunner() {
        this.stressTests = new ArrayList<>();
        registerStressTests();
    }

    private void registerStressTests() {
        stressTests.add(new BasicLatencyStressTest());
        stressTests.add(new BasicThroughputStressTest());
        stressTests.add(new BasicConcurrencyStressTest());
        stressTests.add(new BasicMemoryStressTest());
        testClasses.add("Basic Latency Stress Test");
        testClasses.add("Basic Throughput Stress Test");
        testClasses.add("Basic Concurrency Stress Test");
        testClasses.add("Basic Memory Stress Test");
    }

    public static void main(String[] args) {
        System.out.println("Starting YAWL Stress Test Runner...");
        System.out.println("=====================================");

        boolean verbose = args.length > 0 && args[0].equals("--verbose");

        StandaloneStressTestRunner runner = new StandaloneStressTestRunner();
        runner.runAllTests(verbose);
    }

    public void runAllTests(boolean verbose) {
        startTime = Instant.now();

        System.out.println("\n🧪 Running Stress Test Suite...");
        System.out.println("------------------------------");
        System.out.println("Number of tests: " + stressTests.size());

        for (StressTest test : stressTests) {
            runSingleTest(test, verbose);
        }

        Instant endTime = Instant.now();
        totalDuration = Duration.between(startTime, endTime);

        // Generate report
        generateReport();

        // Print summary
        printSummary();
    }

    private void runSingleTest(StressTest test, boolean verbose) {
        String testName = test.getTestName();
        System.out.println("\n📊 Running: " + testName);
        System.out.println("─────────────────────────────");

        try {
            test.prepare();
            StressTestResult result = test.run();
            test.cleanup();

            // Analyze results
            analyzeResult(testName, result, verbose);

            System.out.println("✓ " + testName + " completed successfully");

        } catch (Exception e) {
            System.err.println("❌ " + testName + " failed: " + e.getMessage());
            criticalPoints.add(testName + ": " + e.getMessage());
        }
    }

    private void analyzeResult(String testName, StressTestResult result, boolean verbose) {
        // Latency test analysis
        if (result instanceof BasicLatencyStressTest.LatencyTestResult) {
            BasicLatencyStressTest.LatencyTestResult latencyResult =
                (BasicLatencyStressTest.LatencyTestResult) result;

            if (latencyResult.getP95ResponseTime() > 100) {
                warningPoints.add(testName + ": High P95 latency (" +
                    String.format("%.2f", latencyResult.getP95ResponseTime()) + "ms)");
            }

            if (verbose) {
                System.out.println("  - P95 Latency: " +
                    String.format("%.2f", latencyResult.getP95ResponseTime()) + "ms");
                System.out.println("  - Avg Latency: " +
                    String.format("%.2f", latencyResult.getAvgResponseTime()) + "ms");
            }
        }
        // Throughput test analysis
        else if (result instanceof BasicThroughputStressTest.ThroughputTestResult) {
            BasicThroughputStressTest.ThroughputTestResult throughputResult =
                (BasicThroughputStressTest.ThroughputTestResult) result;

            if (verbose) {
                System.out.println("  - Max Throughput: " +
                    String.format("%.2f", throughputResult.getMaxThroughput()) + " ops/sec");
                System.out.println("  - Data Points: " +
                    throughputResult.getThroughputDataPoints().size());
            }
        }
        // Concurrency test analysis
        else if (result instanceof BasicConcurrencyStressTest.ConcurrencyTestResult) {
            BasicConcurrencyStressTest.ConcurrencyTestResult concurrencyResult =
                (BasicConcurrencyStressTest.ConcurrencyTestResult) result;

            if (verbose) {
                System.out.println("  - Max Threads: " + concurrencyResult.getMaxConcurrentThreads());
                System.out.println("  - Data Points: " + concurrencyResult.getConcurrencyDataPoints().size());
            }
        }
        // Memory test analysis
        else if (result instanceof BasicMemoryStressTest.MemoryTestResult) {
            BasicMemoryStressTest.MemoryTestResult memoryResult =
                (BasicMemoryStressTest.MemoryTestResult) result;

            if (memoryResult.getPeakMemoryUsage() > 100) {
                warningPoints.add(testName + ": High memory usage (" +
                    String.format("%.2f", memoryResult.getPeakMemoryUsage()) + "MB)");
            }

            if (verbose) {
                System.out.println("  - Peak Memory: " +
                    String.format("%.2f", memoryResult.getPeakMemoryUsage()) + "MB");
                System.out.println("  - Data Points: " + memoryResult.getMemoryDataPoints().size());
            }
        }
    }

    private void generateReport() {
        try {
            // Create reports directory if it doesn't exist
            Path reportsDir = Paths.get(".claude", "reports");
            Files.createDirectories(reportsDir);

            // Generate filename with timestamp
            String timestamp = Instant.now().toString().replace(":", "-");
            String reportFile = "stress-test-report-" + timestamp + ".json";
            Path reportPath = reportsDir.resolve(reportFile);

            // Create JSON report
            String json = SimpleReportGenerator.generateJSONReport(
                getOverallStatus(),
                testClasses,
                totalDuration,
                criticalPoints,
                warningPoints
            );

            // Write report file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportPath.toFile()))) {
                writer.write(json);
            }

            System.out.println("\n📋 Report saved to: " + reportPath);

        } catch (IOException e) {
            System.err.println("Failed to save report: " + e.getMessage());
        }
    }

    private void printSummary() {
        System.out.println("\n📋 Test Summary");
        System.out.println("===============");
        System.out.println("Overall Status: " + getOverallStatus());
        System.out.println("Duration: " + formatDuration(totalDuration));
        System.out.println("Tests Run: " + stressTests.size());
        System.out.println("Critical Issues: " + criticalPoints.size());
        System.out.println("Warnings: " + warningPoints.size());

        if (!criticalPoints.isEmpty()) {
            System.out.println("\n❌ Critical Issues:");
            for (String issue : criticalPoints) {
                System.out.println("  - " + issue);
            }
        }

        if (!warningPoints.isEmpty()) {
            System.out.println("\n⚠️  Warnings:");
            for (String warning : warningPoints) {
                System.out.println("  - " + warning);
            }
        }

        // Set exit code
        System.exit(getExitCode());
    }

    private String getOverallStatus() {
        if (!criticalPoints.isEmpty()) {
            return "CRITICAL";
        } else if (!warningPoints.isEmpty()) {
            return "WARNING";
        } else {
            return "GREEN";
        }
    }

    private int getExitCode() {
        switch (getOverallStatus()) {
            case "GREEN":
                return 0;
            case "WARNING":
                return 1;
            case "CRITICAL":
                return 2;
            default:
                return 3;
        }
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        seconds = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}