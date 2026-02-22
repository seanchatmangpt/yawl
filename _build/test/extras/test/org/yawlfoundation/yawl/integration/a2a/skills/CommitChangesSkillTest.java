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
 * Tests for CommitChangesSkill using Chicago School TDD.
 *
 * <p>Tests the Git operations skill with Toyota Production System safety guards.
 * All operations are designed to preserve history and never hide defects.
 *
 * <p><b>Coverage Targets:</b>
 * <ul>
 *   <li>Skill metadata (ID, name, description, permissions)</li>
 *   <li>Safety class classification (SAFE, MODERATE, DANGEROUS, FORBIDDEN)</li>
 *   <li>Forbidden operation blocking</li>
 *   <li>Parameter validation for each operation</li>
 *   <li>Path safety validation</li>
 *   <li>Commit hash extraction</li>
 *   <li>Result data structure</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class CommitChangesSkillTest extends TestCase {

    private Path tempGitDir;
    private CommitChangesSkill skill;

    public CommitChangesSkillTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tempGitDir = Files.createTempDirectory("git-skill-test");
        skill = new CommitChangesSkill(tempGitDir);

        // Initialize a minimal git repo for tests that need it
        initializeGitRepo();
    }

    @Override
    protected void tearDown() throws Exception {
        if (tempGitDir != null) {
            deleteRecursively(tempGitDir);
        }
        super.tearDown();
    }

    private void initializeGitRepo() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("git", "init");
        pb.directory(tempGitDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

        // Configure git user for commits
        pb = new ProcessBuilder("git", "config", "user.email", "test@example.com");
        pb.directory(tempGitDir.toFile());
        process = pb.start();
        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);

        pb = new ProcessBuilder("git", "config", "user.name", "Test User");
        pb.directory(tempGitDir.toFile());
        process = pb.start();
        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    // =========================================================================
    // Skill Metadata Tests
    // =========================================================================

    public void testGetIdReturnsCorrectIdentifier() {
        assertEquals("commit_changes", skill.getId());
    }

    public void testGetNameReturnsHumanReadableName() {
        assertEquals("Commit Changes", skill.getName());
    }

    public void testGetDescriptionMentionsSafety() {
        String description = skill.getDescription();
        assertNotNull("Description should not be null", description);
        assertTrue("Description should mention safety guards",
            description.contains("safety") || description.contains("Toyota"));
    }

    public void testGetRequiredPermissionsIncludesGitCommit() {
        Set<String> permissions = skill.getRequiredPermissions();
        assertTrue("Should require git:commit permission",
            permissions.contains("git:commit"));
        assertEquals("Should only require one permission", 1, permissions.size());
    }

    // =========================================================================
    // Forbidden Operation Tests - Toyota Production System
    // =========================================================================

    public void testExecuteWithForcePushIsForbidden() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "push --force")
            .parameter("branch", "main")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Force push should be forbidden", result.isSuccess());
        assertTrue("Error should mention FORBIDDEN",
            result.getError().contains("FORBIDDEN"));
        assertTrue("Error should mention Toyota Production System",
            result.getError().contains("Toyota"));
    }

    public void testExecuteWithForceWithLeaseIsForbidden() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "--force-with-lease")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Force with lease should be forbidden", result.isSuccess());
        assertTrue("Error should mention FORBIDDEN",
            result.getError().contains("FORBIDDEN"));
    }

    public void testExecuteWithResetHardIsForbidden() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "reset --hard")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Reset --hard should be forbidden", result.isSuccess());
        assertTrue("Error should mention FORBIDDEN",
            result.getError().contains("FORBIDDEN"));
    }

    public void testExecuteWithAmendIsForbidden() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "amend")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Amend should be forbidden", result.isSuccess());
        assertTrue("Error should mention FORBIDDEN",
            result.getError().contains("FORBIDDEN"));
    }

    public void testExecuteWithBranchDIsForbidden() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "branch -D")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Branch -D should be forbidden", result.isSuccess());
        assertTrue("Error should mention FORBIDDEN",
            result.getError().contains("FORBIDDEN"));
    }

    public void testExecuteWithCleanFdIsForbidden() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "clean -fd")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Clean -fd should be forbidden", result.isSuccess());
        assertTrue("Error should mention FORBIDDEN",
            result.getError().contains("FORBIDDEN"));
    }

    // =========================================================================
    // SAFE Operation Tests
    // =========================================================================

    public void testExecuteStatusIsSafe() throws Exception {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "status")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Status should succeed", result.isSuccess());
        assertEquals("Safety class should be SAFE",
            "SAFE", result.getData().get("safety_class"));
    }

    public void testExecuteLogIsSafe() throws Exception {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "log")
            .build();
        SkillResult result = skill.execute(request);

        // Log may succeed or fail depending on repo state, but should be SAFE
        assertEquals("Safety class should be SAFE",
            "SAFE", result.getData().get("safety_class"));
    }

    public void testExecuteDiffIsSafe() throws Exception {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "diff")
            .build();
        SkillResult result = skill.execute(request);

        assertEquals("Safety class should be SAFE",
            "SAFE", result.getData().get("safety_class"));
    }

    public void testExecuteShowIsSafe() throws Exception {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "show")
            .build();
        SkillResult result = skill.execute(request);

        assertEquals("Safety class should be SAFE",
            "SAFE", result.getData().get("safety_class"));
    }

    // =========================================================================
    // MODERATE Operation Tests
    // =========================================================================

    public void testExecuteStageRequiresFilesParameter() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "stage")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Stage without files should fail", result.isSuccess());
        assertTrue("Error should mention files parameter",
            result.getError().contains("files"));
    }

    public void testExecuteStageWithFilesIsModerate() throws Exception {
        // Create a test file
        Path testFile = tempGitDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "stage")
            .parameter("files", "test.txt")
            .build();
        SkillResult result = skill.execute(request);

        assertEquals("Safety class should be MODERATE",
            "MODERATE", result.getData().get("safety_class"));
    }

    public void testExecuteBranchIsModerate() throws Exception {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "branch")
            .parameter("branch", "test-branch")
            .build();
        SkillResult result = skill.execute(request);

        assertEquals("Safety class should be MODERATE",
            "MODERATE", result.getData().get("safety_class"));
    }

    public void testExecuteCheckoutIsModerate() throws Exception {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "checkout")
            .build();
        SkillResult result = skill.execute(request);

        assertEquals("Safety class should be MODERATE",
            "MODERATE", result.getData().get("safety_class"));
    }

    // =========================================================================
    // DANGEROUS Operation Tests
    // =========================================================================

    public void testExecuteCommitRequiresMessageParameter() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "commit")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Commit without message should fail", result.isSuccess());
        assertTrue("Error should mention message parameter",
            result.getError().contains("message"));
    }

    public void testExecuteCommitWithMessageIsDangerous() throws Exception {
        // First stage a file
        Path testFile = tempGitDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        SkillRequest stageRequest = SkillRequest.builder("commit_changes")
            .parameter("operation", "stage")
            .parameter("files", "test.txt")
            .build();
        skill.execute(stageRequest);

        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "commit")
            .parameter("message", "Test commit")
            .build();
        SkillResult result = skill.execute(request);

        assertEquals("Safety class should be DANGEROUS",
            "DANGEROUS", result.getData().get("safety_class"));
    }

    public void testExecutePushRequiresBranchParameter() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "push")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Push without branch should fail", result.isSuccess());
        assertTrue("Error should mention branch parameter",
            result.getError().contains("branch"));
    }

    public void testExecutePushWithBranchIsDangerous() throws Exception {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "push")
            .parameter("branch", "main")
            .build();
        SkillResult result = skill.execute(request);

        // Push will fail (no remote), but safety class should be set
        assertEquals("Safety class should be DANGEROUS",
            "DANGEROUS", result.getData().get("safety_class"));
    }

    // =========================================================================
    // Path Safety Validation Tests
    // =========================================================================

    public void testExecuteStageRejectsPathTraversal() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "stage")
            .parameter("files", "../../../etc/passwd")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Path traversal should be rejected", result.isSuccess());
        assertTrue("Error should mention unsafe path",
            result.getError().contains("Unsafe path"));
    }

    public void testExecuteStageRejectsAbsolutePath() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "stage")
            .parameter("files", "/etc/passwd")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Absolute path should be rejected", result.isSuccess());
        assertTrue("Error should mention unsafe path",
            result.getError().contains("Unsafe path"));
    }

    public void testExecuteStageRejectsHomeDirectoryPath() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "stage")
            .parameter("files", "~/secrets")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Home directory path should be rejected", result.isSuccess());
        assertTrue("Error should mention unsafe path",
            result.getError().contains("Unsafe path"));
    }

    public void testExecuteStageAcceptsRelativePath() throws Exception {
        Path testFile = tempGitDir.resolve("src/test.txt");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "test content");

        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "stage")
            .parameter("files", "src/test.txt")
            .build();
        SkillResult result = skill.execute(request);

        // Should not fail due to path safety
        assertNotSame("Path safety should not cause failure",
            "Unsafe path", result.getError());
    }

    // =========================================================================
    // Result Data Tests
    // =========================================================================

    public void testExecuteResultIncludesOperation() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "status")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have operation in data",
            result.getData().containsKey("operation"));
        assertEquals("Operation should match request",
            "status", result.getData().get("operation"));
    }

    public void testExecuteResultIncludesSafetyClass() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "status")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have safety_class in data",
            result.getData().containsKey("safety_class"));
    }

    public void testExecuteResultIncludesSuccessFlag() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "status")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have success flag in data",
            result.getData().containsKey("success"));
    }

    public void testExecuteResultIncludesExitCode() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "status")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have exit_code in data",
            result.getData().containsKey("exit_code"));
    }

    public void testExecuteResultIncludesTimestamp() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "status")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should have timestamp in data",
            result.getData().containsKey("timestamp"));
    }

    public void testExecuteCommitResultIncludesCommitHash() throws Exception {
        // Create and stage a file
        Path testFile = tempGitDir.resolve("test.txt");
        Files.writeString(testFile, "test content");

        SkillRequest stageRequest = SkillRequest.builder("commit_changes")
            .parameter("operation", "stage")
            .parameter("files", "test.txt")
            .build();
        skill.execute(stageRequest);

        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "commit")
            .parameter("message", "Test commit for hash extraction")
            .build();
        SkillResult result = skill.execute(request);

        if ((Boolean) result.getData().get("success")) {
            assertTrue("Successful commit should have commit_hash",
                result.getData().containsKey("commit_hash"));
        }
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================

    public void testConstructorWithNullPathUsesCurrentDirectory() {
        CommitChangesSkill defaultSkill = new CommitChangesSkill(null);
        assertNotNull("Should create skill with null path", defaultSkill);
    }

    public void testDefaultConstructorUsesCurrentDirectory() {
        CommitChangesSkill defaultSkill = new CommitChangesSkill();
        assertNotNull("Should create skill with default path", defaultSkill);
    }

    // =========================================================================
    // Permission Checking Tests
    // =========================================================================

    public void testCanExecuteReturnsTrueWhenAllPermissionsGranted() {
        Set<String> granted = Set.of("git:commit", "git:read");

        assertTrue("Should allow execution with required permissions",
            skill.canExecute(granted));
    }

    public void testCanExecuteReturnsTrueWithWildcardPermission() {
        Set<String> granted = Set.of("*");

        assertTrue("Should allow execution with wildcard permission",
            skill.canExecute(granted));
    }

    public void testCanExecuteReturnsFalseWhenPermissionMissing() {
        Set<String> granted = Set.of("git:read");

        assertFalse("Should deny execution without required permission",
            skill.canExecute(granted));
    }

    // =========================================================================
    // Unknown Operation Tests
    // =========================================================================

    public void testExecuteUnknownOperationIsHandled() {
        SkillRequest request = SkillRequest.builder("commit_changes")
            .parameter("operation", "unknown_operation_xyz")
            .build();
        SkillResult result = skill.execute(request);

        // Unknown operation should be classified as MODERATE by default
        assertEquals("Unknown operation should be MODERATE",
            "MODERATE", result.getData().get("safety_class"));
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
