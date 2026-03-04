/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.a2a.skills.rl;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

/**
 * A2A Skill for running the full test suite with comprehensive reporting.
 *
 * <p>Executes {@code bash scripts/dx.sh all} which compiles and tests the
 * entire YAWL system. Parses test output to extract test counts and failure
 * information.
 *
 * <p><b>Required Permission:</b> {@code code:test}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class RunTestsSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(RunTestsSkill.class);
    private static final String SKILL_ID = "rl_run_tests";
    private static final String SKILL_NAME = "Run Test Suite";
    private static final String SKILL_DESCRIPTION =
        "Run the complete YAWL test suite. Compiles and tests all modules, " +
        "capturing test counts, failures, and errors.";

    private static final int TIMEOUT_SECONDS = 600;
    private static final Pattern TEST_SUMMARY_PATTERN = Pattern.compile(
        "Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+)"
    );

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
        return Set.of("code:test");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        _logger.info("Running complete test suite");

        long startTime = System.currentTimeMillis();

        try {
            TestResult result = runTests();
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("exitCode", String.valueOf(result.exitCode));
            resultData.put("testsRun", result.testsRun);
            resultData.put("failures", result.failures);
            resultData.put("errors", result.errors);
            resultData.put("output", truncateOutput(result.output, 3000));
            resultData.put("durationMs", String.valueOf(duration));

            if (result.exitCode == 0) {
                _logger.info("Test suite passed in {}ms: {} tests, {} failures, {} errors",
                    duration, result.testsRun, result.failures, result.errors);
                return SkillResult.success(resultData, duration);
            } else {
                _logger.warn("Test suite failed with exit code {}: {} failures, {} errors",
                    result.exitCode, result.failures, result.errors);
                return SkillResult.error("Tests failed with exit code " + result.exitCode, resultData);
            }

        } catch (Exception e) {
            _logger.error("Test execution failed: {}", e.getMessage());
            return SkillResult.error("Test execution failed: " + e.getMessage());
        }
    }

    private TestResult runTests() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bash", "scripts/dx.sh", "all")
            .directory(new File(System.getProperty("user.dir", "/home/user/yawl")));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        Thread outputThread = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (Exception e) {
                _logger.warn("Error reading test output: {}", e.getMessage());
            }
        });

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Test suite timed out after " + TIMEOUT_SECONDS + " seconds");
        }

        outputThread.join(5000);
        int exitCode = process.exitValue();

        // Parse test summary from output
        TestSummary summary = parseTestSummary(output.toString());

        return new TestResult(exitCode, output.toString(), summary.testsRun, summary.failures, summary.errors);
    }

    private TestSummary parseTestSummary(String output) {
        Matcher matcher = TEST_SUMMARY_PATTERN.matcher(output);
        if (matcher.find()) {
            return new TestSummary(
                matcher.group(1),
                matcher.group(2),
                matcher.group(3)
            );
        }
        return new TestSummary("unknown", "unknown", "unknown");
    }

    private String truncateOutput(String output, int maxLength) {
        if (output == null || output.length() <= maxLength) {
            return output;
        }
        return output.substring(0, maxLength) + "... (truncated)";
    }

    private record TestResult(int exitCode, String output, String testsRun, String failures, String errors) {}
    private record TestSummary(String testsRun, String failures, String errors) {}
}
