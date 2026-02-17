/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.reporting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Flaky test detector for YAWL CI pipelines.
 *
 * Analyses JUnit XML reports across multiple CI runs to identify tests
 * that produce inconsistent results (pass in some runs, fail in others).
 *
 * Algorithm:
 * 1. Parse all TEST-*.xml reports from each run directory
 * 2. Build a per-test status history: {testKey -> [RUN-1:PASS, RUN-2:FAIL, ...]}
 * 3. A test is flaky if its history contains at least one PASS and at least
 *    one FAIL or ERROR
 * 4. Flakiness score = failCount / totalRuns (0.0 = always passes, 1.0 = always fails)
 *    Tests with score between 0.0 (exclusive) and 1.0 (exclusive) are flaky.
 * 5. Report is written as a Markdown file for GitHub CI consumption
 *
 * Usage:
 * <pre>
 *   FlakyTestDetector detector = new FlakyTestDetector(analyzer);
 *   FlakyTestDetector.FlakyReport report = detector.analyseRuns(
 *       List.of(Path.of("ci-run-1/surefire-reports"),
 *               Path.of("ci-run-2/surefire-reports"),
 *               Path.of("ci-run-3/surefire-reports")));
 *   detector.writeMarkdownReport(report, Path.of("target/flaky-tests.md"));
 * </pre>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
public class FlakyTestDetector {

    private final JUnitXmlAnalyzer analyzer;

    public FlakyTestDetector(JUnitXmlAnalyzer analyzer) {
        if (analyzer == null) {
            throw new IllegalArgumentException("analyzer must not be null");
        }
        this.analyzer = analyzer;
    }

    // =========================================================================
    // Data Model
    // =========================================================================

    /**
     * Result of analysing one test across multiple runs.
     *
     * @param testKey       "className#methodName"
     * @param runStatuses   list of status strings in run order (e.g. ["PASS","FAIL","PASS"])
     * @param flakinessScore fraction of runs that failed/errored (0.0–1.0)
     */
    public record FlakyTestEntry(
            String testKey,
            List<String> runStatuses,
            double flakinessScore) {

        public boolean isFlaky() {
            return flakinessScore > 0.0 && flakinessScore < 1.0;
        }

        public int failCount() {
            return (int) runStatuses.stream()
                    .filter(s -> "FAIL".equals(s) || "ERROR".equals(s))
                    .count();
        }

        public int totalRuns() {
            return runStatuses.size();
        }
    }

    /**
     * Complete flakiness analysis over N CI runs.
     *
     * @param runCount    number of CI runs analysed
     * @param flakyTests  tests that are flaky (sorted by flakinessScore desc)
     * @param stableTests tests that are consistently passing
     * @param failingTests tests that always fail (not flaky, but broken)
     */
    public record FlakyReport(
            int runCount,
            List<FlakyTestEntry> flakyTests,
            int stableTests,
            List<FlakyTestEntry> failingTests) {

        public boolean hasFlaky() {
            return !flakyTests.isEmpty();
        }
    }

    // =========================================================================
    // Analysis
    // =========================================================================

    /**
     * Analyses test results across multiple run directories.
     *
     * @param runDirectories list of directories, one per CI run (oldest first)
     * @return flakiness report
     * @throws Exception if any run directory cannot be parsed
     */
    public FlakyReport analyseRuns(List<Path> runDirectories) throws Exception {
        if (runDirectories == null || runDirectories.isEmpty()) {
            throw new IllegalArgumentException("runDirectories must not be empty");
        }

        int runCount = runDirectories.size();
        // testKey -> list of (runIndex, status) pairs
        Map<String, Map<Integer, String>> testRunStatus = new HashMap<>();

        for (int runIdx = 0; runIdx < runDirectories.size(); runIdx++) {
            Path runDir = runDirectories.get(runIdx);
            List<JUnitXmlAnalyzer.SuiteStats> suites;

            if (Files.isDirectory(runDir)) {
                suites = analyzer.parseDirectory(runDir);
            } else {
                // Directory doesn't exist (run was skipped) — treat as no data
                suites = List.of();
            }

            for (JUnitXmlAnalyzer.SuiteStats suite : suites) {
                for (JUnitXmlAnalyzer.TestCaseResult tc : suite.testCases()) {
                    String key = tc.className() + "#" + tc.testName();
                    testRunStatus
                            .computeIfAbsent(key, k -> new TreeMap<>())
                            .put(runIdx, tc.status());
                }
            }
        }

        // Build FlakyTestEntry list
        List<FlakyTestEntry> allEntries = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, String>> entry : testRunStatus.entrySet()) {
            String key = entry.getKey();
            Map<Integer, String> byRun = entry.getValue();

            List<String> statuses = byRun.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());

            long failCount = statuses.stream()
                    .filter(s -> "FAIL".equals(s) || "ERROR".equals(s))
                    .count();
            double score = statuses.isEmpty() ? 0.0 : (double) failCount / statuses.size();
            allEntries.add(new FlakyTestEntry(key, statuses, score));
        }

        // Partition into flaky / stable / always-failing
        List<FlakyTestEntry> flakyTests = allEntries.stream()
                .filter(FlakyTestEntry::isFlaky)
                .sorted(Comparator.comparingDouble(FlakyTestEntry::flakinessScore).reversed())
                .collect(Collectors.toList());

        List<FlakyTestEntry> alwaysFailing = allEntries.stream()
                .filter(e -> e.flakinessScore() == 1.0)
                .sorted(Comparator.comparing(FlakyTestEntry::testKey))
                .collect(Collectors.toList());

        int stableCount = (int) allEntries.stream()
                .filter(e -> e.flakinessScore() == 0.0)
                .count();

        return new FlakyReport(runCount, flakyTests, stableCount, alwaysFailing);
    }

    // =========================================================================
    // Reporting
    // =========================================================================

    /**
     * Writes a Markdown flakiness report suitable for GitHub CI upload.
     *
     * @param report     flakiness report to write
     * @param outputPath destination file path
     * @throws IOException if the file cannot be written
     */
    public void writeMarkdownReport(FlakyReport report, Path outputPath) throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# YAWL Test Flakiness Report\n\n");
        md.append(String.format("**Runs analysed:** %d  |  ", report.runCount()));
        md.append(String.format("**Flaky tests:** %d  |  ", report.flakyTests().size()));
        md.append(String.format("**Stable tests:** %d  |  ", report.stableTests()));
        md.append(String.format("**Always-failing:** %d%n%n", report.failingTests().size()));

        if (report.hasFlaky()) {
            md.append("## Flaky Tests\n\n");
            md.append("| Test | Flakiness Score | Fail Count / Runs | History |\n");
            md.append("|------|----------------|-------------------|----------|\n");
            for (FlakyTestEntry e : report.flakyTests()) {
                md.append(String.format("| `%s` | %.1f%% | %d / %d | %s |%n",
                        e.testKey(),
                        e.flakinessScore() * 100,
                        e.failCount(),
                        e.totalRuns(),
                        String.join(" ", e.runStatuses())));
            }
        } else {
            md.append("## No Flaky Tests Detected\n\n");
            md.append("All observed tests produced consistent results across " +
                      report.runCount() + " runs.\n");
        }

        if (!report.failingTests().isEmpty()) {
            md.append("\n## Always-Failing Tests\n\n");
            md.append("These tests failed in every run they appeared in. "
                    + "They are not flaky — they are broken.\n\n");
            for (FlakyTestEntry e : report.failingTests()) {
                md.append(String.format("- `%s`%n", e.testKey()));
            }
        }

        Files.writeString(outputPath, md.toString(), StandardCharsets.UTF_8);
    }
}
