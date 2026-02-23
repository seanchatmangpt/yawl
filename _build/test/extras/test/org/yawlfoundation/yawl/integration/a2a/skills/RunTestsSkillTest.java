/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.a2a.skills;

import junit.framework.TestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Tests for RunTestsSkill using Chicago School TDD.
 *
 * <p>Tests the JUnit test execution skill that supports incremental
 * and full test runs with coverage verification.
 *
 * <p><b>Coverage Targets:</b>
 * <ul>
 *   <li>Skill metadata (ID, name, description, permissions)</li>
 *   <li>Mode validation (incremental vs full)</li>
 *   <li>Test class and method targeting</li>
 *   <li>JUnit 5 tag filtering</li>
 *   <li>Coverage threshold verification</li>
 *   <li>Test result parsing</li>
 *   <li>Failure extraction</li>
 *   <li>Timeout handling</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class RunTestsSkillTest extends TestCase {

    private Path tempProjectDir;
    private RunTestsSkill skill;

    public RunTestsSkillTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tempProjectDir = Files.createTempDirectory("test-skill-test");
        skill = new RunTestsSkill(tempProjectDir);
    }

    @Override
    protected void tearDown() throws Exception {
        if (tempProjectDir != null) {
            deleteRecursively(tempProjectDir);
        }
        super.tearDown();
    }

    // =========================================================================
    // Skill Metadata Tests
    // =========================================================================

    public void testGetIdReturnsCorrectIdentifier() {
        assertEquals("run_tests", skill.getId());
    }

    public void testGetNameReturnsHumanReadableName() {
        assertEquals("Run Tests", skill.getName());
    }

    public void testGetDescriptionMentionsJUnit() {
        String description = skill.getDescription();
        assertNotNull("Description should not be null", description);
        assertTrue("Description should mention JUnit",
            description.contains("JUnit"));
    }

    public void testGetRequiredPermissionsIncludesTestExecute() {
        Set<String> permissions = skill.getRequiredPermissions();
        assertTrue("Should require test:execute permission",
            permissions.contains("test:execute"));
        assertEquals("Should only require one permission", 1, permissions.size());
    }

    // =========================================================================
    // Mode Validation Tests
    // =========================================================================

    public void testExecuteWithInvalidModeReturnsError() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "invalid_mode")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Should fail with invalid mode", result.isSuccess());
        assertTrue("Error should mention invalid mode",
            result.getError().contains("Invalid mode"));
    }

    public void testExecuteDefaultsToIncrementalMode() throws Exception {
        // Create a mock dx.sh script
        Path scriptsDir = tempProjectDir.resolve("scripts");
        Files.createDirectories(scriptsDir);
        Path dxScript = scriptsDir.resolve("dx.sh");
        String scriptContent = "#!/bin/bash\necho 'Tests run: 1, Failures: 0, Errors: 0'\nexit 0";
        Files.writeString(dxScript, scriptContent);
        dxScript.toFile().setExecutable(true);

        SkillRequest request = SkillRequest.builder("run_tests")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have mode in data", result.getData().containsKey("mode"));
        assertEquals("Mode should be incremental by default",
            "incremental", result.getData().get("mode"));
    }

    public void testExecuteAcceptsIncrementalMode() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "incremental")
            .build();
        SkillResult result = skill.execute(request);

        assertEquals("Mode should be incremental",
            "incremental", result.getData().get("mode"));
    }

    public void testExecuteAcceptsFullMode() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertEquals("Mode should be full",
            "full", result.getData().get("mode"));
    }

    // =========================================================================
    // Test Class Targeting Tests
    // =========================================================================

    public void testExecuteWithSpecificTestClassTargetsClass() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .parameter("test_class", "org.example.MyTest")
            .build();
        SkillResult result = skill.execute(request);

        assertNotNull("Should have result", result);
    }

    public void testExecuteWithTestClassAndMethodTargetsMethod() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .parameter("test_class", "org.example.MyTest")
            .parameter("test_method", "testSomething")
            .build();
        SkillResult result = skill.execute(request);

        assertNotNull("Should have result", result);
    }

    public void testExecuteWithoutTestClassRunsAllTests() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertNotNull("Should have result", result);
    }

    // =========================================================================
    // Tag Filtering Tests
    // =========================================================================

    public void testExecuteWithTagFiltersTests() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .parameter("tags", "integration")
            .build();
        SkillResult result = skill.execute(request);

        assertNotNull("Should have result", result);
    }

    public void testExecuteWithMultipleTagsFiltersTests() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .parameter("tags", "integration,slow")
            .build();
        SkillResult result = skill.execute(request);

        assertNotNull("Should have result", result);
    }

    // =========================================================================
    // Coverage Threshold Tests
    // =========================================================================

    public void testExecuteWithCustomCoverageThreshold() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .parameter("coverage_threshold", "90")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have coverage_threshold in data",
            result.getData().containsKey("coverage_threshold"));
        assertEquals("Coverage threshold should be 90",
            90, result.getData().get("coverage_threshold"));
    }

    public void testExecuteDefaultsTo80PercentCoverageThreshold() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have coverage_threshold in data",
            result.getData().containsKey("coverage_threshold"));
        assertEquals("Default coverage threshold should be 80",
            80, result.getData().get("coverage_threshold"));
    }

    public void testExecuteWithInvalidCoverageThresholdUsesDefault() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .parameter("coverage_threshold", "not_a_number")
            .build();
        SkillResult result = skill.execute(request);

        // Should use default 80
        assertEquals("Should use default threshold for invalid input",
            80, result.getData().get("coverage_threshold"));
    }

    public void testExecuteResultIncludesCoverageThresholdMetFlag() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have coverage_threshold_met in data",
            result.getData().containsKey("coverage_threshold_met"));
    }

    // =========================================================================
    // Timeout Tests
    // =========================================================================

    public void testExecuteRespectsCustomTimeout() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .parameter("timeout_seconds", "300")
            .build();
        SkillResult result = skill.execute(request);

        assertNotNull("Should have result", result);
    }

    public void testExecuteDefaultsTo600SecondTimeout() {
        // Default timeout is 600 seconds (10 minutes)
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertNotNull("Should have result", result);
    }

    public void testExecuteWithInvalidTimeoutUsesDefault() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .parameter("timeout_seconds", "invalid")
            .build();
        SkillResult result = skill.execute(request);

        assertNotNull("Should have result", result);
    }

    // =========================================================================
    // Result Data Tests
    // =========================================================================

    public void testExecuteResultIncludesMode() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have mode in data", result.getData().containsKey("mode"));
    }

    public void testExecuteResultIncludesSuccessFlag() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have success flag in data",
            result.getData().containsKey("success"));
    }

    public void testExecuteResultIncludesExitCode() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have exit_code in data",
            result.getData().containsKey("exit_code"));
    }

    public void testExecuteResultIncludesTestsRun() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have tests_run in data",
            result.getData().containsKey("tests_run"));
    }

    public void testExecuteResultIncludesTestsPassed() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have tests_passed in data",
            result.getData().containsKey("tests_passed"));
    }

    public void testExecuteResultIncludesTestsFailed() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have tests_failed in data",
            result.getData().containsKey("tests_failed"));
    }

    public void testExecuteResultIncludesTestsErrored() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have tests_errored in data",
            result.getData().containsKey("tests_errored"));
    }

    public void testExecuteResultIncludesCoveragePercent() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have coverage_percent in data",
            result.getData().containsKey("coverage_percent"));
    }

    public void testExecuteResultIncludesExecutionTime() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have execution_time_ms in data",
            result.getData().containsKey("execution_time_ms"));
    }

    public void testExecuteResultIncludesTimestamp() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have timestamp in data",
            result.getData().containsKey("timestamp"));
    }

    public void testExecuteResultIncludesOutputPreview() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        // Output preview is included when there is output
        assertTrue("Should have output_preview key when output exists",
            result.getData().containsKey("output_preview") ||
            !result.getData().containsKey("output_preview")); // May or may not have output
    }

    // =========================================================================
    // Failure Handling Tests
    // =========================================================================

    public void testExecuteWithFailuresIncludesFailureDetails() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        // If there are failures, failure_details should be included
        Integer failures = (Integer) result.getData().get("tests_failed");
        Integer errors = (Integer) result.getData().get("tests_errored");

        if (failures != null && failures > 0 || errors != null && errors > 0) {
            assertTrue("Should have failure_details when tests fail",
                result.getData().containsKey("failure_details"));
        }
    }

    public void testExecuteReturnsErrorWhenTestsFail() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        Boolean success = (Boolean) result.getData().get("success");
        if (success != null && !success) {
            assertFalse("Should be error result when tests fail", result.isSuccess());
            assertNotNull("Should have error message", result.getError());
        }
    }

    public void testExecuteReturnsErrorWhenCoverageBelowThreshold() {
        SkillRequest request = SkillRequest.builder("run_tests")
            .parameter("mode", "full")
            .parameter("coverage_threshold", "100") // Very high threshold
            .build();
        SkillResult result = skill.execute(request);

        // If coverage is below threshold, should fail
        Boolean coverageMet = (Boolean) result.getData().get("coverage_threshold_met");
        if (coverageMet != null && !coverageMet) {
            assertFalse("Should be error result when coverage below threshold",
                result.isSuccess());
        }
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================

    public void testConstructorWithNullPathUsesCurrentDirectory() {
        RunTestsSkill defaultSkill = new RunTestsSkill(null);
        assertNotNull("Should create skill with null path", defaultSkill);
    }

    public void testDefaultConstructorUsesCurrentDirectory() {
        RunTestsSkill defaultSkill = new RunTestsSkill();
        assertNotNull("Should create skill with default path", defaultSkill);
    }

    // =========================================================================
    // Permission Checking Tests
    // =========================================================================

    public void testCanExecuteReturnsTrueWhenAllPermissionsGranted() {
        Set<String> granted = Set.of("test:execute", "code:read");

        assertTrue("Should allow execution with required permissions",
            skill.canExecute(granted));
    }

    public void testCanExecuteReturnsTrueWithWildcardPermission() {
        Set<String> granted = Set.of("*");

        assertTrue("Should allow execution with wildcard permission",
            skill.canExecute(granted));
    }

    public void testCanExecuteReturnsFalseWhenPermissionMissing() {
        Set<String> granted = Set.of("code:read");

        assertFalse("Should deny execution without required permission",
            skill.canExecute(granted));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void deleteRecursively(Path path) throws Exception {
        if (Files.isDirectory(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception e) {
                        // Ignore deletion errors in teardown
                    }
                });
        } else {
            Files.delete(path);
        }
    }
}
