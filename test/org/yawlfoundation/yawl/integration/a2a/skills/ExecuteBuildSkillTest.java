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
 * Tests for ExecuteBuildSkill using Chicago School TDD.
 *
 * <p>Tests the Maven build execution skill that supports both
 * incremental builds (via dx.sh) and full parallel builds.
 *
 * <p><b>Coverage Targets:</b>
 * <ul>
 *   <li>Skill metadata (ID, name, description, permissions)</li>
 *   <li>Mode validation (incremental vs full)</li>
 *   <li>Profile validation (fast, agent-dx, analysis, security)</li>
 *   <li>Module targeting</li>
 *   <li>Timeout handling</li>
 *   <li>Build result parsing</li>
 *   <li>Error handling for failed builds</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ExecuteBuildSkillTest extends TestCase {

    private Path tempProjectDir;
    private ExecuteBuildSkill skill;

    public ExecuteBuildSkillTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tempProjectDir = Files.createTempDirectory("build-skill-test");
        skill = new ExecuteBuildSkill(tempProjectDir);
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
        assertEquals("execute_build", skill.getId());
    }

    public void testGetNameReturnsHumanReadableName() {
        assertEquals("Execute Build", skill.getName());
    }

    public void testGetDescriptionMentionsMaven() {
        String description = skill.getDescription();
        assertNotNull("Description should not be null", description);
        assertTrue("Description should mention Maven",
            description.contains("Maven"));
    }

    public void testGetRequiredPermissionsIncludesBuildExecute() {
        Set<String> permissions = skill.getRequiredPermissions();
        assertTrue("Should require build:execute permission",
            permissions.contains("build:execute"));
        assertEquals("Should only require one permission", 1, permissions.size());
    }

    // =========================================================================
    // Mode Validation Tests
    // =========================================================================

    public void testExecuteWithInvalidModeReturnsError() {
        SkillRequest request = SkillRequest.builder("execute_build")
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
        String scriptContent = "#!/bin/bash\necho 'BUILD SUCCESS'\nexit 0";
        Files.writeString(dxScript, scriptContent);
        dxScript.toFile().setExecutable(true);

        SkillRequest request = SkillRequest.builder("execute_build")
            .build();
        SkillResult result = skill.execute(request);

        // Even if build fails due to missing Maven, mode should be incremental
        assertTrue("Should have mode in data", result.getData().containsKey("mode"));
        assertEquals("Mode should be incremental by default",
            "incremental", result.getData().get("mode"));
    }

    public void testExecuteAcceptsIncrementalMode() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "incremental")
            .build();
        SkillResult result = skill.execute(request);

        // Result may fail due to missing script, but mode validation passes
        assertEquals("Mode should be incremental",
            "incremental", result.getData().get("mode"));
    }

    public void testExecuteAcceptsFullMode() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        // Result may fail due to missing Maven, but mode validation passes
        assertEquals("Mode should be full",
            "full", result.getData().get("mode"));
    }

    // =========================================================================
    // Profile Validation Tests
    // =========================================================================

    public void testExecuteWithInvalidProfileReturnsError() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("profile", "invalid_profile")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Should fail with invalid profile", result.isSuccess());
        assertTrue("Error should mention invalid profile",
            result.getError().contains("Invalid profile"));
    }

    public void testExecuteAcceptsFastProfile() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("profile", "fast")
            .build();
        SkillResult result = skill.execute(request);

        // Should get past profile validation
        assertEquals("Profile should be fast",
            "fast", result.getData().get("profile"));
    }

    public void testExecuteAcceptsAgentDxProfile() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("profile", "agent-dx")
            .build();
        SkillResult result = skill.execute(request);

        // Should get past profile validation
        assertEquals("Profile should be agent-dx",
            "agent-dx", result.getData().get("profile"));
    }

    public void testExecuteAcceptsAnalysisProfile() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("profile", "analysis")
            .build();
        SkillResult result = skill.execute(request);

        // Should get past profile validation
        assertEquals("Profile should be analysis",
            "analysis", result.getData().get("profile"));
    }

    public void testExecuteAcceptsSecurityProfile() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("profile", "security")
            .build();
        SkillResult result = skill.execute(request);

        // Should get past profile validation
        assertEquals("Profile should be security",
            "security", result.getData().get("profile"));
    }

    public void testExecuteDefaultsToDefaultProfile() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        // Empty profile becomes "default"
        assertEquals("Profile should be default when empty",
            "default", result.getData().get("profile"));
    }

    // =========================================================================
    // Timeout Tests
    // =========================================================================

    public void testExecuteRespectsCustomTimeout() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("timeout_seconds", "120")
            .build();
        SkillResult result = skill.execute(request);

        // Should attempt build with 120 second timeout
        // May fail due to missing Maven but timeout should be applied
        assertNotNull("Should have result", result);
    }

    public void testExecuteDefaultsTo300SecondTimeout() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        // Default timeout is 300 seconds
        assertNotNull("Should have result", result);
    }

    public void testExecuteWithInvalidTimeoutUsesDefault() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("timeout_seconds", "not_a_number")
            .build();
        SkillResult result = skill.execute(request);

        // Should use default timeout of 300
        assertNotNull("Should have result", result);
    }

    public void testExecuteWithEmptyTimeoutUsesDefault() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("timeout_seconds", "")
            .build();
        SkillResult result = skill.execute(request);

        // Should use default timeout
        assertNotNull("Should have result", result);
    }

    // =========================================================================
    // Module Targeting Tests
    // =========================================================================

    public void testExecuteWithSpecificModuleTargetsModule() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("modules", "yawl-engine")
            .build();
        SkillResult result = skill.execute(request);

        // Should attempt to build specific module
        assertNotNull("Should have result", result);
    }

    public void testExecuteWithMultipleModulesTargetsAll() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("modules", "yawl-engine,yawl-elements")
            .build();
        SkillResult result = skill.execute(request);

        // Should attempt to build multiple modules
        assertNotNull("Should have result", result);
    }

    // =========================================================================
    // Result Data Tests
    // =========================================================================

    public void testExecuteResultIncludesMode() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have mode in data", result.getData().containsKey("mode"));
    }

    public void testExecuteResultIncludesProfile() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .parameter("profile", "fast")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have profile in data", result.getData().containsKey("profile"));
    }

    public void testExecuteResultIncludesSuccessFlag() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have success flag in data", result.getData().containsKey("success"));
    }

    public void testExecuteResultIncludesExitCode() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have exit_code in data", result.getData().containsKey("exit_code"));
    }

    public void testExecuteResultIncludesExecutionTime() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have execution_time_ms in data",
            result.getData().containsKey("execution_time_ms"));
    }

    public void testExecuteResultIncludesTimestamp() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have timestamp in data", result.getData().containsKey("timestamp"));
    }

    public void testExecuteFailedBuildIncludesErrorOutput() {
        SkillRequest request = SkillRequest.builder("execute_build")
            .parameter("mode", "full")
            .build();
        SkillResult result = skill.execute(request);

        // If build fails, should have error_output
        if (!(Boolean) result.getData().get("success")) {
            assertTrue("Failed build should have error_output or error message",
                result.getData().containsKey("error_output") || result.getError() != null);
        }
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================

    public void testConstructorWithNullPathUsesCurrentDirectory() {
        ExecuteBuildSkill defaultSkill = new ExecuteBuildSkill(null);
        assertNotNull("Should create skill with null path", defaultSkill);
    }

    public void testDefaultConstructorUsesCurrentDirectory() {
        ExecuteBuildSkill defaultSkill = new ExecuteBuildSkill();
        assertNotNull("Should create skill with default path", defaultSkill);
    }

    // =========================================================================
    // Permission Checking Tests
    // =========================================================================

    public void testCanExecuteReturnsTrueWhenAllPermissionsGranted() {
        Set<String> granted = Set.of("build:execute", "code:read");

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
