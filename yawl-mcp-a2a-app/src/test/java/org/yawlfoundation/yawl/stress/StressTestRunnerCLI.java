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
import java.util.List;

/**
 * Command line interface for StressTestRunner.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
public class StressTestRunnerCLI {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0];
        boolean verbose = args.length > 1 && args[1].equals("--verbose");

        try {
            switch (command) {
                case "run":
                    runTests(verbose);
                    break;
                case "list":
                    listTests();
                    break;
                case "help":
                    printUsage();
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void runTests(boolean verbose) {
        System.out.println("Starting YAWL Stress Test Runner...");
        System.out.println("=====================================");

        try {
            StressTestRunner runner = new StressTestRunner();
            runner.setVerbose(verbose);

            // Run all tests
            StressTestReport report = runner.runAllTests();

            // Generate report
            generateJSONReport(report);

            // Print summary
            System.out.println("\nTest Summary:");
            System.out.println("=============");
            System.out.println("Overall Status: " + report.getOverallStatus());
            System.out.println("Total Tests: " + report.getTestClasses().size());
            System.out.println("Duration: " + formatDuration(report.getDuration()));
            System.out.println("Critical Issues: " + report.getCriticalBreakingPoints().size());
            System.out.println("Warnings: " + report.getWarningBreakingPoints().size());

            // Output breaking points
            if (!report.getCriticalBreakingPoints().isEmpty()) {
                System.out.println("\nCritical Breaking Points:");
                for (String issue : report.getCriticalBreakingPoints()) {
                    System.out.println("  - " + issue);
                }
            }

            if (!report.getWarningBreakingPoints().isEmpty()) {
                System.out.println("\nWarning Breaking Points:");
                for (String warning : report.getWarningBreakingPoints()) {
                    System.out.println("  - " + warning);
                }
            }

            // Set exit code
            setExitCode(report.getOverallStatus());

        } catch (StressTestException e) {
            System.err.println("Test execution failed: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void listTests() {
        System.out.println("Available Stress Tests:");
        System.out.println("======================");

        StressTestRunner runner = new StressTestRunner();
        List<String> testClasses = runner.getTestClasses();

        for (String test : testClasses) {
            System.out.println("  - " + test);
        }
    }

    private static void generateJSONReport(StressTestReport report) {
        try {
            // Create reports directory if it doesn't exist
            Path reportsDir = Paths.get(".claude", "reports");
            Files.createDirectories(reportsDir);

            // Generate filename with timestamp
            String timestamp = Instant.now().toString().replace(":", "-");
            String reportFile = "stress-test-report-" + timestamp + ".json";
            Path reportPath = reportsDir.resolve(reportFile);

            // Write simple JSON report
            String json = report.toJSON();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(reportPath.toFile()))) {
                writer.write(json);
            }

            System.out.println("\nReport saved to: " + reportPath);

        } catch (IOException e) {
            System.err.println("Failed to save report: " + e.getMessage());
        }
    }

    private static void setExitCode(String status) {
        switch (status) {
            case "GREEN":
                System.exit(0); // Success
            case "WARNING":
                System.exit(1); // Non-critical issues
            case "CRITICAL":
                System.exit(2); // Critical issues
            default:
                System.exit(3); // Unknown status
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java StressTestRunnerCLI <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  run         Run all stress tests");
        System.out.println("  list        List available stress tests");
        System.out.println("  help        Show this help message");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --verbose   Enable verbose output");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java StressTestRunnerCLI run");
        System.out.println("  java StressTestRunnerCLI run --verbose");
        System.out.println("  java StressTestRunnerCLI list");
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