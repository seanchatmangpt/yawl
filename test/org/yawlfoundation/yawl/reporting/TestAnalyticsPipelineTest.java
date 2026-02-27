/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.reporting;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the JUnit XML analytics pipeline (JUnitXmlAnalyzer).
 *
 * Validates the full analytics lifecycle using real XML files written
 * to a JUnit 5 @TempDir:
 * - Single XML file parsing
 * - Directory scan for TEST-*.xml
 * - Aggregate report construction
 * - Slowest-test ranking
 * - Failed-test extraction
 * - Flaky test detection across two runs
 * - Report formatting
 *
 * Chicago TDD: real XML files, real DOM parsing, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Tag("unit")
class TestAnalyticsPipelineTest {

    @TempDir
    Path tempDir;

    private JUnitXmlAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new JUnitXmlAnalyzer();
    }

    // =========================================================================
    // Single File Parsing
    // =========================================================================

    @Test
    void testParseSingleXmlFileAllPass() throws Exception {
        File xml = writeXml("TEST-AllPass.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.AllPass" tests="3" failures="0"
                       errors="0" skipped="0" time="0.150">
                <testcase classname="org.example.AllPass" name="testA" time="0.050"/>
                <testcase classname="org.example.AllPass" name="testB" time="0.060"/>
                <testcase classname="org.example.AllPass" name="testC" time="0.040"/>
            </testsuite>
            """);

        JUnitXmlAnalyzer.SuiteStats stats = analyzer.parseSuiteFile(xml);

        assertEquals("org.example.AllPass", stats.suiteName());
        assertEquals(3,    stats.totalTests());
        assertEquals(0,    stats.failureCount());
        assertEquals(0,    stats.errorCount());
        assertEquals(0,    stats.skippedCount());
        assertEquals(3,    stats.passCount());
        assertEquals(0.15, stats.elapsedSeconds(), 0.001);
        assertEquals(3,    stats.testCases().size());
        assertTrue(stats.testCases().stream().allMatch(JUnitXmlAnalyzer.TestCaseResult::isPassed),
                "All test cases must report PASS");
    }

    @Test
    void testParseSingleXmlFileWithFailure() throws Exception {
        File xml = writeXml("TEST-WithFailure.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.WithFailure" tests="3" failures="1"
                       errors="0" skipped="0" time="0.300">
                <testcase classname="org.example.WithFailure" name="testPass" time="0.100"/>
                <testcase classname="org.example.WithFailure" name="testFail" time="0.150">
                    <failure message="expected: 5 but was: 4">junit.AssertionError: expected: 5</failure>
                </testcase>
                <testcase classname="org.example.WithFailure" name="testPass2" time="0.050"/>
            </testsuite>
            """);

        JUnitXmlAnalyzer.SuiteStats stats = analyzer.parseSuiteFile(xml);

        assertEquals(3, stats.totalTests());
        assertEquals(1, stats.failureCount());
        assertEquals(0, stats.errorCount());
        assertEquals(2, stats.passCount());

        long failCount = stats.testCases().stream()
                .filter(JUnitXmlAnalyzer.TestCaseResult::isFailed).count();
        assertEquals(1, failCount, "One test case must be FAIL");

        JUnitXmlAnalyzer.TestCaseResult failedTest = stats.testCases().stream()
                .filter(JUnitXmlAnalyzer.TestCaseResult::isFailed)
                .findFirst()
                .orElseThrow();
        assertEquals("testFail", failedTest.testName());
        assertNotNull(failedTest.message());
        assertTrue(failedTest.message().contains("expected: 5"),
                "Failure message must be captured");
    }

    @Test
    void testParseSingleXmlFileWithSkipped() throws Exception {
        File xml = writeXml("TEST-WithSkipped.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.WithSkipped" tests="4" failures="0"
                       errors="0" skipped="2" time="0.200">
                <testcase classname="org.example.WithSkipped" name="testRun1"  time="0.100"/>
                <testcase classname="org.example.WithSkipped" name="testSkip1" time="0.000">
                    <skipped/>
                </testcase>
                <testcase classname="org.example.WithSkipped" name="testRun2"  time="0.100"/>
                <testcase classname="org.example.WithSkipped" name="testSkip2" time="0.000">
                    <skipped/>
                </testcase>
            </testsuite>
            """);

        JUnitXmlAnalyzer.SuiteStats stats = analyzer.parseSuiteFile(xml);
        assertEquals(4, stats.totalTests());
        assertEquals(2, stats.skippedCount());
        assertEquals(2, stats.passCount());
    }

    // =========================================================================
    // Directory Parsing
    // =========================================================================

    @Test
    void testParseDirectoryFindsAllXmlFiles() throws Exception {
        writeXml("TEST-Suite1.xml", minimalSuiteXml("Suite1", 5, 0));
        writeXml("TEST-Suite2.xml", minimalSuiteXml("Suite2", 3, 1));
        writeXml("TEST-Suite3.xml", minimalSuiteXml("Suite3", 2, 0));
        // Non-matching file — must be ignored
        Files.writeString(tempDir.resolve("other.xml"), "<ignored/>");

        List<JUnitXmlAnalyzer.SuiteStats> suites = analyzer.parseDirectory(tempDir);
        assertEquals(3, suites.size(), "Exactly 3 TEST-*.xml files must be parsed");
    }

    @Test
    void testParseEmptyDirectoryReturnsEmptyList() throws Exception {
        List<JUnitXmlAnalyzer.SuiteStats> suites = analyzer.parseDirectory(tempDir);
        assertTrue(suites.isEmpty(), "Empty directory must yield empty list");
    }

    // =========================================================================
    // Analytics Report
    // =========================================================================

    @Test
    void testBuildReportAggregatesCorrectly() throws Exception {
        writeXml("TEST-Suite1.xml", minimalSuiteXml("Suite1", 10, 2));
        writeXml("TEST-Suite2.xml", minimalSuiteXml("Suite2", 5, 1));

        List<JUnitXmlAnalyzer.SuiteStats> suites = analyzer.parseDirectory(tempDir);
        JUnitXmlAnalyzer.AnalyticsReport report = analyzer.buildReport(suites, 3);

        assertEquals(2,  report.totalSuites());
        assertEquals(15, report.totalTests(),  "10 + 5 = 15 total tests");
        assertEquals(3,  report.totalFailed(), "2 + 1 = 3 total failures");
        assertEquals(12, report.totalPassed(), "15 - 3 = 12 passed");
    }

    @Test
    void testBuildReportSlowestTests() throws Exception {
        writeXml("TEST-Slow.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.Slow" tests="4" failures="0"
                       errors="0" skipped="0" time="3.600">
                <testcase classname="org.example.Slow" name="fastTest"  time="0.100"/>
                <testcase classname="org.example.Slow" name="slowTest3" time="1.500"/>
                <testcase classname="org.example.Slow" name="slowTest1" time="1.000"/>
                <testcase classname="org.example.Slow" name="slowTest2" time="1.000"/>
            </testsuite>
            """);

        List<JUnitXmlAnalyzer.SuiteStats> suites = analyzer.parseDirectory(tempDir);
        JUnitXmlAnalyzer.AnalyticsReport report = analyzer.buildReport(suites, 2);

        assertEquals(2, report.slowestTests().size(),
                "Top-2 slowest tests must be returned");
        assertEquals("slowTest3", report.slowestTests().get(0).testName(),
                "Slowest test must be first");
        assertEquals(1.5, report.slowestTests().get(0).elapsedSeconds(), 0.001);
    }

    @Test
    void testPassRateCalculation() throws Exception {
        writeXml("TEST-PassRate.xml", minimalSuiteXml("PassRate", 10, 4));
        List<JUnitXmlAnalyzer.SuiteStats> suites = analyzer.parseDirectory(tempDir);
        JUnitXmlAnalyzer.AnalyticsReport report = analyzer.buildReport(suites, 5);

        assertEquals(0.6, report.overallPassRate(), 0.001,
                "Pass rate must be 6/10 = 0.6");
    }

    // =========================================================================
    // Flaky Test Detection
    // =========================================================================

    @Test
    void testFlakyTestDetectionIdentifiesIntermittentFailures() throws Exception {
        // Run 1: testFlaky FAILS, testStable PASSES
        writeXml("TEST-Run1.xml", """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.FlakyTest" tests="2" failures="1"
                       errors="0" skipped="0" time="0.500">
                <testcase classname="org.example.FlakyTest" name="testStable" time="0.200"/>
                <testcase classname="org.example.FlakyTest" name="testFlaky"  time="0.300">
                    <failure message="timing issue">intermittent failure</failure>
                </testcase>
            </testsuite>
            """);

        List<JUnitXmlAnalyzer.SuiteStats> suites1 = analyzer.parseDirectory(tempDir);
        JUnitXmlAnalyzer.AnalyticsReport run1 = analyzer.buildReport(suites1, 10);

        // Run 2: both PASS (flaky test passes this time)
        // Write to a second temp dir to simulate second run
        Path tempDir2 = Files.createTempDirectory("yawl-analytics-run2");
        Files.writeString(tempDir2.resolve("TEST-Run2.xml"), """
            <?xml version="1.0" encoding="UTF-8"?>
            <testsuite name="org.example.FlakyTest" tests="2" failures="0"
                       errors="0" skipped="0" time="0.400">
                <testcase classname="org.example.FlakyTest" name="testStable" time="0.180"/>
                <testcase classname="org.example.FlakyTest" name="testFlaky"  time="0.220"/>
            </testsuite>
            """, StandardCharsets.UTF_8);

        List<JUnitXmlAnalyzer.SuiteStats> suites2 = analyzer.parseDirectory(tempDir2);
        JUnitXmlAnalyzer.AnalyticsReport run2 = analyzer.buildReport(suites2, 10);

        Map<String, List<String>> flakyTests =
                analyzer.detectFlakyTests(List.of(run1, run2));

        // testFlaky appeared as FAIL in run1 and PASS in run2 → flaky
        // testStable was PASS in both runs → not flaky
        // Note: flaky detection looks at slowestTests + failedTests in both reports
        assertFalse(flakyTests.isEmpty(),
                "Flaky test detection must identify at least one flaky test");

        // Clean up second temp dir
        Files.deleteIfExists(tempDir2.resolve("TEST-Run2.xml"));
        Files.deleteIfExists(tempDir2);
    }

    // =========================================================================
    // Report Formatting
    // =========================================================================

    @Test
    void testReportFormattingContainsRequiredSections() throws Exception {
        writeXml("TEST-Format.xml", minimalSuiteXml("FormatTest", 5, 1));
        List<JUnitXmlAnalyzer.SuiteStats> suites = analyzer.parseDirectory(tempDir);
        JUnitXmlAnalyzer.AnalyticsReport report = analyzer.buildReport(suites, 3);
        String formatted = analyzer.formatReport(report);

        assertNotNull(formatted, "Formatted report must not be null");
        assertTrue(formatted.contains("YAWL Test Analytics Report"),
                "Report must contain header");
        assertTrue(formatted.contains("PassRate"), "Report must contain pass rate");
        assertTrue(formatted.contains("Duration"), "Report must contain duration");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private File writeXml(String filename, String content) throws Exception {
        Path path = tempDir.resolve(filename);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path.toFile();
    }

    /**
     * Generates a minimal valid testsuite XML with {@code tests} total tests
     * and {@code failures} failures. All tests have uniform timing.
     */
    private static String minimalSuiteXml(String name, int tests, int failures) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format(
                "<testsuite name=\"org.example.%s\" tests=\"%d\" failures=\"%d\" "
                + "errors=\"0\" skipped=\"0\" time=\"%.3f\">%n",
                name, tests, failures, tests * 0.1));

        for (int i = 0; i < tests; i++) {
            if (i < failures) {
                sb.append(String.format(
                        "  <testcase classname=\"org.example.%s\" name=\"test%d\" time=\"0.100\">%n"
                        + "    <failure message=\"fail%d\">assertion failed %d</failure>%n"
                        + "  </testcase>%n", name, i, i, i));
            } else {
                sb.append(String.format(
                        "  <testcase classname=\"org.example.%s\" name=\"test%d\" time=\"0.100\"/>%n",
                        name, i));
            }
        }
        sb.append("</testsuite>\n");
        return sb.toString();
    }
}
