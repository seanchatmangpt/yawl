/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.reporting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FlakyTestDetector}.
 *
 * Validates:
 * - Flaky test detection across 2 and 3 runs
 * - Stable test classification (always passes)
 * - Always-failing test classification (always fails, not flaky)
 * - Empty run directories are handled gracefully
 * - Markdown report generation content
 * - Constructor null guard
 *
 * Chicago TDD: real temporary directories, real XML files, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
class FlakyTestDetectorTest {

    @TempDir
    Path tempRoot;

    private FlakyTestDetector detector;

    @BeforeEach
    void setUp() {
        detector = new FlakyTestDetector(new JUnitXmlAnalyzer());
    }

    // =========================================================================
    // Guard Conditions
    // =========================================================================

    @Test
    void testConstructorRejectsNullAnalyzer() {
        assertThrows(IllegalArgumentException.class, () -> new FlakyTestDetector(null),
                "Null analyzer must throw IllegalArgumentException");
    }

    @Test
    void testAnalyseRunsRejectsNullList() {
        assertThrows(IllegalArgumentException.class, () -> detector.analyseRuns(null),
                "Null run list must throw IllegalArgumentException");
    }

    @Test
    void testAnalyseRunsRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> detector.analyseRuns(List.of()),
                "Empty run list must throw IllegalArgumentException");
    }

    // =========================================================================
    // Flaky Detection: 2 Runs
    // =========================================================================

    @Test
    void testFlakyDetectionTwoRuns() throws Exception {
        // Run 1: testAlpha FAILS, testBeta PASSES
        Path run1 = Files.createDirectory(tempRoot.resolve("run1"));
        Files.writeString(run1.resolve("TEST-Suite.xml"), suiteXml("Suite",
                "testAlpha", "FAIL", "testBeta", "PASS"), StandardCharsets.UTF_8);

        // Run 2: testAlpha PASSES, testBeta PASSES
        Path run2 = Files.createDirectory(tempRoot.resolve("run2"));
        Files.writeString(run2.resolve("TEST-Suite.xml"), suiteXml("Suite",
                "testAlpha", "PASS", "testBeta", "PASS"), StandardCharsets.UTF_8);

        FlakyTestDetector.FlakyReport report = detector.analyseRuns(List.of(run1, run2));

        assertEquals(2, report.runCount(), "Run count must be 2");
        assertTrue(report.hasFlaky(), "Report must identify at least one flaky test");

        boolean hasAlpha = report.flakyTests().stream()
                .anyMatch(e -> e.testKey().contains("testAlpha"));
        assertTrue(hasAlpha, "testAlpha must be classified as flaky");

        boolean hasBeta = report.flakyTests().stream()
                .anyMatch(e -> e.testKey().contains("testBeta"));
        assertFalse(hasBeta, "testBeta (always passes) must NOT be classified as flaky");
    }

    // =========================================================================
    // Flaky Detection: 3 Runs
    // =========================================================================

    @Test
    void testFlakyDetectionThreeRunsWithScore() throws Exception {
        // testFlaky fails in 2 of 3 runs → score = 2/3 ≈ 0.667
        Path run1 = Files.createDirectory(tempRoot.resolve("r1"));
        Path run2 = Files.createDirectory(tempRoot.resolve("r2"));
        Path run3 = Files.createDirectory(tempRoot.resolve("r3"));

        Files.writeString(run1.resolve("TEST-S.xml"),
                suiteXml("S", "testFlaky", "FAIL", "testStable", "PASS"),
                StandardCharsets.UTF_8);
        Files.writeString(run2.resolve("TEST-S.xml"),
                suiteXml("S", "testFlaky", "PASS", "testStable", "PASS"),
                StandardCharsets.UTF_8);
        Files.writeString(run3.resolve("TEST-S.xml"),
                suiteXml("S", "testFlaky", "FAIL", "testStable", "PASS"),
                StandardCharsets.UTF_8);

        FlakyTestDetector.FlakyReport report =
                detector.analyseRuns(List.of(run1, run2, run3));

        assertEquals(3, report.runCount());

        FlakyTestDetector.FlakyTestEntry flaky = report.flakyTests().stream()
                .filter(e -> e.testKey().contains("testFlaky"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("testFlaky must be flaky"));

        assertEquals(2, flaky.failCount(), "testFlaky must have failed twice");
        assertEquals(3, flaky.totalRuns(), "testFlaky must appear in 3 runs");
        assertEquals(2.0 / 3.0, flaky.flakinessScore(), 0.001,
                "Flakiness score must be 2/3");
    }

    // =========================================================================
    // Always-Failing Classification
    // =========================================================================

    @Test
    void testAlwaysFailingNotClassifiedAsFlaky() throws Exception {
        Path run1 = Files.createDirectory(tempRoot.resolve("af1"));
        Path run2 = Files.createDirectory(tempRoot.resolve("af2"));

        // testBroken fails in both runs
        Files.writeString(run1.resolve("TEST-S.xml"),
                suiteXml("S", "testBroken", "FAIL", "testGood", "PASS"),
                StandardCharsets.UTF_8);
        Files.writeString(run2.resolve("TEST-S.xml"),
                suiteXml("S", "testBroken", "FAIL", "testGood", "PASS"),
                StandardCharsets.UTF_8);

        FlakyTestDetector.FlakyReport report =
                detector.analyseRuns(List.of(run1, run2));

        // testBroken must NOT appear in flakyTests (it is consistently broken)
        boolean brokenInFlaky = report.flakyTests().stream()
                .anyMatch(e -> e.testKey().contains("testBroken"));
        assertFalse(brokenInFlaky,
                "Consistently-broken test must not be classified as flaky");

        // testBroken must appear in failingTests
        boolean brokenInFailing = report.failingTests().stream()
                .anyMatch(e -> e.testKey().contains("testBroken"));
        assertTrue(brokenInFailing,
                "Consistently-broken test must appear in always-failing list");
    }

    // =========================================================================
    // Markdown Report
    // =========================================================================

    @Test
    void testMarkdownReportWrittenCorrectly() throws Exception {
        Path run1 = Files.createDirectory(tempRoot.resolve("md1"));
        Path run2 = Files.createDirectory(tempRoot.resolve("md2"));

        Files.writeString(run1.resolve("TEST-S.xml"),
                suiteXml("S", "testMd", "FAIL", "testOk", "PASS"),
                StandardCharsets.UTF_8);
        Files.writeString(run2.resolve("TEST-S.xml"),
                suiteXml("S", "testMd", "PASS", "testOk", "PASS"),
                StandardCharsets.UTF_8);

        FlakyTestDetector.FlakyReport report =
                detector.analyseRuns(List.of(run1, run2));

        Path outputMd = tempRoot.resolve("flaky-tests.md");
        detector.writeMarkdownReport(report, outputMd);

        assertTrue(Files.exists(outputMd), "Markdown file must be created");
        String content = Files.readString(outputMd, StandardCharsets.UTF_8);

        assertTrue(content.contains("YAWL Test Flakiness Report"),
                "Report must contain header");
        assertTrue(content.contains("Runs analysed"),
                "Report must mention number of runs");
        assertNotNull(content, "Report content must not be null");
    }

    @Test
    void testMarkdownReportNoFlakyTests() throws Exception {
        Path run1 = Files.createDirectory(tempRoot.resolve("clean1"));
        Path run2 = Files.createDirectory(tempRoot.resolve("clean2"));

        // All tests pass in both runs
        Files.writeString(run1.resolve("TEST-S.xml"),
                suiteXml("S", "testA", "PASS", "testB", "PASS"),
                StandardCharsets.UTF_8);
        Files.writeString(run2.resolve("TEST-S.xml"),
                suiteXml("S", "testA", "PASS", "testB", "PASS"),
                StandardCharsets.UTF_8);

        FlakyTestDetector.FlakyReport report =
                detector.analyseRuns(List.of(run1, run2));
        assertFalse(report.hasFlaky(),
                "No flaky tests expected when all tests pass consistently");

        Path outputMd = tempRoot.resolve("clean-report.md");
        detector.writeMarkdownReport(report, outputMd);

        String content = Files.readString(outputMd, StandardCharsets.UTF_8);
        assertTrue(content.contains("No Flaky Tests Detected"),
                "Clean report must state no flaky tests detected");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Generates a testsuite XML with exactly two test cases using the given
     * names and statuses.
     */
    private static String suiteXml(String suite,
                                    String test1, String status1,
                                    String test2, String status2) {
        int failures = (List.of(status1, status2).stream()
                .filter("FAIL"::equals).count()) == 0 ? 0 :
                (int) List.of(status1, status2).stream().filter("FAIL"::equals).count();
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.%s" tests="2" failures="%d"
                       errors="0" skipped="0" time="0.200">
                %s
                %s
            </testsuite>
            """,
                suite, failures,
                testcaseXml("org.example." + suite, test1, status1),
                testcaseXml("org.example." + suite, test2, status2));
    }

    private static String testcaseXml(String cls, String name, String status) {
        String base = String.format(
                "<testcase classname=\"%s\" name=\"%s\" time=\"0.100\"", cls, name);
        return switch (status) {
            case "FAIL"  -> base + "><failure message=\"fail\">failed</failure></testcase>";
            case "ERROR" -> base + "><error message=\"err\">error</error></testcase>";
            case "SKIP"  -> base + "><skipped/></testcase>";
            default      -> base + "/>";
        };
    }
}
