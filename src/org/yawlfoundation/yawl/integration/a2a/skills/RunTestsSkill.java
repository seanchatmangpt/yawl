package org.yawlfoundation.yawl.integration.a2a.skills;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A2A Skill for JUnit test execution.
 *
 * <p>Executes tests with support for:
 * <ul>
 *   <li>Fast incremental tests via dx.sh script</li>
 *   <li>Full test suite with parallel execution</li>
 *   <li>Specific test classes or methods</li>
 *   <li>JUnit 5 tag filtering</li>
 *   <li>Coverage threshold verification</li>
 * </ul>
 *
 * <p><b>Parameters:</b>
 * <ul>
 *   <li>{@code mode} - "incremental" (fast) or "full" (all tests)</li>
 *   <li>{@code test_class} - Specific test class to run</li>
 *   <li>{@code test_method} - Specific test method to run</li>
 *   <li>{@code tags} - JUnit 5 tags to include</li>
 *   <li>{@code coverage_threshold} - Minimum coverage % (default 80)</li>
 *   <li>{@code timeout_seconds} - Test timeout (default 600)</li>
 * </ul>
 *
 * <p><b>Required Permission:</b> {@code test:execute}
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class RunTestsSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(RunTestsSkill.class);
    private static final String SKILL_ID = "run_tests";
    private static final String SKILL_NAME = "Run Tests";
    private static final String SKILL_DESCRIPTION =
        "Execute JUnit tests with incremental or full mode. " +
        "Supports parallel execution and coverage verification.";

    private static final int DEFAULT_TIMEOUT_SECONDS = 600;
    private static final int DEFAULT_COVERAGE_THRESHOLD = 80;
    private static final Set<String> VALID_MODES = Set.of("incremental", "full");

    /**
     * Validates test class names to prevent command injection.
     * Allows: fully qualified class names with dots (e.g., com.example.TestClass).
     */
    private static final Pattern TEST_CLASS_PATTERN = Pattern.compile(
        "^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)*$"
    );

    /**
     * Validates test method names to prevent command injection.
     * Allows: alphanumeric and underscores only.
     */
    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

    /**
     * Validates JUnit 5 tag names to prevent command injection.
     * Allows: alphanumeric, underscores, hyphens. Comma-separated list.
     */
    private static final Pattern JUNIT_TAG_PATTERN = Pattern.compile(
        "^[a-zA-Z][a-zA-Z0-9_-]*(,[a-zA-Z][a-zA-Z0-9_-]*)*$"
    );

    private static final Pattern TEST_COUNT_PATTERN =
        Pattern.compile("Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+)");
    private static final Pattern COVERAGE_PATTERN =
        Pattern.compile("Total coverage:\\s*(\\d+(?:\\.\\d+)?%?)", Pattern.CASE_INSENSITIVE);

    private final Path projectRoot;

    /**
     * Create skill with project root.
     *
     * @param projectRoot the project root directory
     */
    public RunTestsSkill(Path projectRoot) {
        this.projectRoot = projectRoot != null ? projectRoot : Path.of(".");
    }

    /**
     * Create skill with default project root.
     */
    public RunTestsSkill() {
        this(Path.of("."));
    }

    @Override
    public String getId() {
        return SKILL_ID;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return SKILL_DESCRIPTION;
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("test:execute");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String mode = request.getParameter("mode", "incremental");
        if (!VALID_MODES.contains(mode)) {
            return SkillResult.error("Invalid mode: " + mode + ". Valid: " + VALID_MODES);
        }

        String testClass = request.getParameter("test_class", "");

        // Validate test class name to prevent command injection
        if (!testClass.isEmpty() && !TEST_CLASS_PATTERN.matcher(testClass).matches()) {
            return SkillResult.error("Invalid test class format: " + testClass +
                ". Test class must be a valid Java identifier (alphanumeric, dots, underscores).");
        }

        String testMethod = request.getParameter("test_method", "");

        // Validate test method name to prevent command injection
        if (!testMethod.isEmpty() && !TEST_METHOD_PATTERN.matcher(testMethod).matches()) {
            return SkillResult.error("Invalid test method format: " + testMethod +
                ". Test method must be a valid Java identifier (alphanumeric, underscores only).");
        }

        String tags = request.getParameter("tags", "");

        // Validate JUnit tags to prevent command injection
        if (!tags.isEmpty() && !JUNIT_TAG_PATTERN.matcher(tags).matches()) {
            return SkillResult.error("Invalid tags format: " + tags +
                ". Tags must be alphanumeric with underscores or hyphens, comma-separated.");
        }

        int coverageThreshold = parseInt(request.getParameter("coverage_threshold", ""),
            DEFAULT_COVERAGE_THRESHOLD);
        int timeoutSeconds = parseInt(request.getParameter("timeout_seconds", ""),
            DEFAULT_TIMEOUT_SECONDS);

        _logger.info("Running {} tests for class: {}", mode, testClass.isEmpty() ? "all" : testClass);

        long startTime = System.currentTimeMillis();

        try {
            TestResult result = executeTests(mode, testClass, testMethod, tags, timeoutSeconds);
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> data = new HashMap<>();
            data.put("mode", mode);
            data.put("success", result.success);
            data.put("exit_code", result.exitCode);
            data.put("tests_run", result.testsRun);
            data.put("tests_passed", result.testsRun - result.failures - result.errors);
            data.put("tests_failed", result.failures);
            data.put("tests_errored", result.errors);
            data.put("tests_skipped", result.skipped);
            data.put("coverage_percent", result.coveragePercent);
            data.put("execution_time_ms", executionTime);
            data.put("timestamp", Instant.now().toString());

            if (!result.output.isEmpty()) {
                data.put("output_preview", truncateOutput(result.output, 2000));
            }

            if (result.failures > 0 || result.errors > 0) {
                data.put("failure_details", extractFailures(result.output));
            }

            boolean coverageMet = result.coveragePercent >= coverageThreshold;
            data.put("coverage_threshold_met", coverageMet);
            data.put("coverage_threshold", coverageThreshold);

            if (result.success && coverageMet) {
                _logger.info("Tests passed: {}/{} ({}% coverage) in {}ms",
                    data.get("tests_passed"), result.testsRun, result.coveragePercent, executionTime);
                return SkillResult.success(data, executionTime);
            } else if (!result.success) {
                _logger.warn("Tests failed: {} failures, {} errors in {}ms",
                    result.failures, result.errors, executionTime);
                return SkillResult.error("Tests failed: " + result.failures + " failures, " +
                    result.errors + " errors", data);
            } else {
                _logger.warn("Coverage below threshold: {}% < {}%", result.coveragePercent, coverageThreshold);
                return SkillResult.error("Coverage " + result.coveragePercent + "% below threshold " +
                    coverageThreshold + "%", data);
            }

        } catch (Exception e) {
            _logger.error("Test execution failed: {}", e.getMessage());
            return SkillResult.error("Test execution failed: " + e.getMessage());
        }
    }

    private TestResult executeTests(String mode, String testClass, String testMethod,
                                    String tags, int timeoutSeconds)
            throws IOException, InterruptedException {

        List<String> command = buildCommand(mode, testClass, testMethod, tags);
        _logger.debug("Executing command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                _logger.debug("[test] {}", line);
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Tests timed out after " + timeoutSeconds + " seconds");
        }

        int exitCode = process.exitValue();
        String outputStr = output.toString();

        int testsRun = 0, failures = 0, errors = 0, skipped = 0, coverage = 0;

        Matcher countMatcher = TEST_COUNT_PATTERN.matcher(outputStr);
        if (countMatcher.find()) {
            testsRun = Integer.parseInt(countMatcher.group(1));
            failures = Integer.parseInt(countMatcher.group(2));
            errors = Integer.parseInt(countMatcher.group(3));
        }

        Matcher coverageMatcher = COVERAGE_PATTERN.matcher(outputStr);
        if (coverageMatcher.find()) {
            String coverageStr = coverageMatcher.group(1).replace("%", "");
            coverage = (int) Double.parseDouble(coverageStr);
        }

        boolean success = exitCode == 0 && failures == 0 && errors == 0;

        return new TestResult(success, exitCode, outputStr, testsRun, failures, errors, skipped, coverage);
    }

    private List<String> buildCommand(String mode, String testClass, String testMethod, String tags) {
        List<String> command = new ArrayList<>();

        if ("incremental".equals(mode)) {
            command.add("bash");
            command.add("scripts/dx.sh");
            command.add("test");
        } else {
            command.add("mvn");
            command.add("-T");
            command.add("1.5C");
            command.add("clean");
            command.add("test");
        }

        if (!testClass.isEmpty()) {
            command.add("-Dtest=" + testClass + (testMethod.isEmpty() ? "" : "#" + testMethod));
        }

        if (!tags.isEmpty()) {
            command.add("-Dgroups=\"" + tags + "\"");
        }

        command.add("-Dmaven.test.failure.ignore=true");
        command.add("-Djacoco.skip=false");

        return command;
    }

    private String truncateOutput(String output, int maxLength) {
        if (output == null || output.length() <= maxLength) {
            return output;
        }
        return output.substring(0, maxLength) + "... (truncated)";
    }

    private List<String> extractFailures(String output) {
        List<String> failures = new ArrayList<>();
        if (output == null) return failures;

        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("FAILED") || line.contains("ERROR") ||
                line.contains("<<< FAILURE!") || line.contains("AssertionError")) {
                failures.add(line.trim());
            }
        }
        return failures.size() > 10 ? failures.subList(0, 10) : failures;
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private record TestResult(boolean success, int exitCode, String output,
                              int testsRun, int failures, int errors,
                              int skipped, int coveragePercent) {}
}
