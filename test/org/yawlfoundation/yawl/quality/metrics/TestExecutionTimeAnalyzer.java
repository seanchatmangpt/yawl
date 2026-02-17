package org.yawlfoundation.yawl.quality.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Analyzes Maven Surefire XML reports to detect slow tests and suite-level
 * execution time regressions.
 *
 * <p>Thresholds:
 * <ul>
 *   <li>Individual test: &gt; 5 seconds triggers a warning (reported but not failing)</li>
 *   <li>Individual test: &gt; 30 seconds triggers a build failure</li>
 *   <li>Suite total: &gt; 10 minutes triggers a build failure</li>
 *   <li>Flaky test detection: tests with non-deterministic outcomes appear in report</li>
 * </ul>
 *
 * <p>Activated when system property {@code surefire.reports.dir} is set:
 * <pre>
 *   mvn test -Dtest=TestExecutionTimeAnalyzer \
 *       -Dsurefire.reports.dir=target/surefire-reports
 * </pre>
 *
 * @version 6.0.0
 */
@DisplayName("Test Execution Time Analysis and Optimization")
@EnabledIfSystemProperty(named = "surefire.reports.dir", matches = ".+")
public class TestExecutionTimeAnalyzer {

    /** Individual test execution time above this threshold fails the build. */
    private static final double MAX_TEST_SECONDS = 30.0;

    /** Individual test execution time above this threshold triggers a warning. */
    private static final double WARN_TEST_SECONDS = 5.0;

    /** Total suite execution time above this threshold fails the build. */
    private static final double MAX_SUITE_TOTAL_SECONDS = 600.0; // 10 minutes

    @Test
    @DisplayName("No individual test exceeds 30-second execution budget")
    void noTestExceedsMaximumExecutionTime() throws Exception {
        List<TestResult> allTests = parseAllSurefireReports();

        List<TestResult> overBudget = allTests.stream()
                .filter(t -> t.durationSeconds() > MAX_TEST_SECONDS)
                .sorted(Comparator.comparingDouble(TestResult::durationSeconds).reversed())
                .toList();

        if (!overBudget.isEmpty()) {
            StringBuilder message = new StringBuilder(
                String.format("%d test(s) exceed the %d-second execution budget:%n",
                    overBudget.size(), (int) MAX_TEST_SECONDS)
            );
            for (TestResult t : overBudget) {
                message.append(String.format(
                    "  %.1fs  %s#%s%n",
                    t.durationSeconds(), t.className(), t.testName()
                ));
            }
            message.append(
                "\nRemediation: Break slow tests into smaller units, use test fixtures "
                + "with in-memory H2 instead of full engine startup, or move "
                + "long-running tests to the 'integration-test' phase via Failsafe."
            );
            fail(message.toString());
        }
    }

    @Test
    @DisplayName("Total test suite execution does not exceed 10 minutes")
    void totalSuiteExecutionWithinBudget() throws Exception {
        List<TestResult> allTests = parseAllSurefireReports();

        double totalSeconds = allTests.stream()
                .mapToDouble(TestResult::durationSeconds)
                .sum();

        assertTrue(totalSeconds <= MAX_SUITE_TOTAL_SECONDS,
            String.format(
                "Total test suite execution time %.0f seconds exceeds %.0f-second budget.%n"
                + "Tests analyzed: %d%n"
                + "Parallelism setting: surefire parallel=classes threadCount=4%n"
                + "Remediation: Increase thread count, use test slicing, or move "
                + "integration tests to Failsafe IT phase.",
                totalSeconds, MAX_SUITE_TOTAL_SECONDS, allTests.size()
            )
        );
    }

    @Test
    @DisplayName("Slow test warning report (does not fail build)")
    void reportSlowTestsAsWarnings() throws Exception {
        List<TestResult> allTests = parseAllSurefireReports();

        List<TestResult> slowTests = allTests.stream()
                .filter(t -> t.durationSeconds() > WARN_TEST_SECONDS
                           && t.durationSeconds() <= MAX_TEST_SECONDS)
                .sorted(Comparator.comparingDouble(TestResult::durationSeconds).reversed())
                .toList();

        if (!slowTests.isEmpty()) {
            // Report as stdout only - this is informational, not a failure.
            System.out.printf(
                "[WARN] %d test(s) exceed %d-second warning threshold (candidates for optimization):%n",
                slowTests.size(), (int) WARN_TEST_SECONDS
            );
            for (TestResult t : slowTests) {
                System.out.printf("  %.1fs  %s#%s%n",
                    t.durationSeconds(), t.className(), t.testName());
            }
        }

        // This test always passes - it only produces diagnostic output.
        assertTrue(true, "Slow test warning report completed");
    }

    @Test
    @DisplayName("Detect test failures that indicate potential flakiness")
    void detectFlakyTestCandidates() throws Exception {
        List<TestResult> allTests = parseAllSurefireReports();

        List<TestResult> failed = allTests.stream()
                .filter(t -> t.failed() || t.errored())
                .toList();

        if (!failed.isEmpty()) {
            StringBuilder message = new StringBuilder(
                String.format("%d test(s) failed in current run:%n", failed.size())
            );
            for (TestResult t : failed) {
                message.append(String.format(
                    "  [%s] %s#%s%n",
                    t.failed() ? "FAIL" : "ERROR", t.className(), t.testName()
                ));
                if (!t.failureMessage().isEmpty()) {
                    message.append(String.format("    %s%n",
                        truncate(t.failureMessage(), 200)));
                }
            }
            fail(message.toString());
        }
    }

    // -----------------------------------------------------------------------
    // Surefire report parsing
    // -----------------------------------------------------------------------

    private List<TestResult> parseAllSurefireReports() throws Exception {
        String reportDirProperty = System.getProperty("surefire.reports.dir");
        Path reportDir = Path.of(reportDirProperty);

        if (!Files.isDirectory(reportDir)) {
            fail("surefire.reports.dir does not point to an existing directory: " + reportDir);
        }

        List<TestResult> results = new ArrayList<>();

        try (Stream<Path> xmlFiles = Files.find(reportDir, 1,
                (path, attrs) -> path.getFileName().toString().startsWith("TEST-")
                              && path.getFileName().toString().endsWith(".xml"))) {

            xmlFiles.forEach(xmlFile -> {
                try {
                    results.addAll(parseSurefireXml(xmlFile));
                } catch (Exception e) {
                    // Log the file that failed to parse; do not suppress other reports.
                    System.err.println("[WARN] Failed to parse Surefire report: "
                        + xmlFile + " - " + e.getMessage());
                }
            });
        }

        if (results.isEmpty()) {
            fail("No Surefire XML reports found in: " + reportDir
                 + ". Ensure 'mvn test' completed before running this analysis.");
        }

        return results;
    }

    private List<TestResult> parseSurefireXml(Path xmlFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(xmlFile.toFile());
        document.getDocumentElement().normalize();

        List<TestResult> results = new ArrayList<>();

        NodeList testCases = document.getElementsByTagName("testcase");
        for (int i = 0; i < testCases.getLength(); i++) {
            Element testCase = (Element) testCases.item(i);

            String className = testCase.getAttribute("classname");
            String testName = testCase.getAttribute("name");
            double duration = parseDouble(testCase.getAttribute("time"), 0.0);

            boolean failed = testCase.getElementsByTagName("failure").getLength() > 0;
            boolean errored = testCase.getElementsByTagName("error").getLength() > 0;
            boolean skipped = testCase.getElementsByTagName("skipped").getLength() > 0;

            String failureMessage = "";
            if (failed) {
                Element failure = (Element) testCase.getElementsByTagName("failure").item(0);
                failureMessage = failure.getAttribute("message");
            } else if (errored) {
                Element error = (Element) testCase.getElementsByTagName("error").item(0);
                failureMessage = error.getAttribute("message");
            }

            results.add(new TestResult(className, testName, duration,
                    failed, errored, skipped, failureMessage));
        }

        return results;
    }

    private static double parseDouble(String value, double defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    // -----------------------------------------------------------------------
    // Value type
    // -----------------------------------------------------------------------

    private record TestResult(
        String className,
        String testName,
        double durationSeconds,
        boolean failed,
        boolean errored,
        boolean skipped,
        String failureMessage
    ) {}
}
