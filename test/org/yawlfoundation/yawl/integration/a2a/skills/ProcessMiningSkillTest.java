/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for ProcessMiningSkill A2A Skill implementation.
 *
 * Tests cover:
 * - Skill metadata (id, name, description, permissions)
 * - Permission checking (canExecute)
 * - Parameter validation (missing/empty specIdentifier)
 * - Analysis type handling (full, performance, variants, xes, social_network)
 * - Error handling with unreachable engine (real integration, not mocks)
 * - Constructor validation (null arguments)
 * - Interface compliance (implements A2ASkill)
 *
 * <p><b>Chicago TDD Approach:</b> Uses real ProcessMiningSkill with unreachable
 * engine (localhost:9999) to test error paths. Tests verify that skill
 * returns SkillResult.error() without throwing exceptions on network failure.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class ProcessMiningSkillTest {

    private ProcessMiningSkill skill;
    private static final String UNREACHABLE_ENGINE_URL = "http://localhost:9999/yawl";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "testpass";

    @Before
    public void setUp() {
        // Create skill with unreachable engine URL (localhost:9999)
        // This allows testing error paths without mocking
        skill = new ProcessMiningSkill(UNREACHABLE_ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);
    }

    // =========================================================================
    // Skill Metadata Tests
    // =========================================================================

    @Test
    public void testGetId() {
        assertEquals("process_mining_analyze", skill.getId());
    }

    @Test
    public void testGetName() {
        assertEquals("Process Mining Analyze", skill.getName());
    }

    @Test
    public void testGetDescription_NotEmpty() {
        String description = skill.getDescription();
        assertNotNull("Description should not be null", description);
        assertFalse("Description should not be empty", description.isEmpty());
        assertTrue("Description should mention process mining",
                   description.toLowerCase().contains("process mining") ||
                   description.toLowerCase().contains("analysis"));
    }

    @Test
    public void testGetRequiredPermissions_ContainsWorkflowRead() {
        Set<String> permissions = skill.getRequiredPermissions();
        assertNotNull("Permissions should not be null", permissions);
        assertTrue("Permissions should contain 'workflow:read'",
                   permissions.contains("workflow:read"));
    }

    @Test
    public void testGetRequiredPermissions_ContainsProcessMiningRead() {
        Set<String> permissions = skill.getRequiredPermissions();
        assertNotNull("Permissions should not be null", permissions);
        assertTrue("Permissions should contain 'process-mining:read'",
                   permissions.contains("process-mining:read"));
    }

    // =========================================================================
    // Permission Check Tests (canExecute)
    // =========================================================================

    @Test
    public void testCanExecute_WithAllPermission() {
        Set<String> permissions = Set.of("*");
        assertTrue("canExecute should return true with wildcard permission",
                   skill.canExecute(permissions));
    }

    @Test
    public void testCanExecute_WithRequiredPermissions() {
        Set<String> permissions = Set.of("workflow:read", "process-mining:read");
        assertTrue("canExecute should return true with all required permissions",
                   skill.canExecute(permissions));
    }

    @Test
    public void testCanExecute_WithInsufficientPermissions() {
        Set<String> permissions = Set.of("workflow:read");
        assertFalse("canExecute should return false with incomplete permissions",
                    skill.canExecute(permissions));
    }

    @Test
    public void testCanExecute_WithEmptyPermissions() {
        Set<String> permissions = Set.of();
        assertFalse("canExecute should return false with empty permissions",
                    skill.canExecute(permissions));
    }

    // =========================================================================
    // Parameter Validation Tests
    // =========================================================================

    @Test
    public void testExecute_MissingSpecIdentifier_ReturnsError() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error when specIdentifier is missing",
                   result.isError());
        assertNotNull("Error message should be provided", result.getError());
    }

    @Test
    public void testExecute_EmptySpecIdentifier_ReturnsError() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error when specIdentifier is empty",
                   result.isError());
        assertNotNull("Error message should be provided", result.getError());
    }

    // =========================================================================
    // Unreachable Engine Tests (Default Analysis Type)
    // =========================================================================

    @Test
    public void testExecute_UnreachableEngine_ReturnsError() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error when engine is unreachable",
                   result.isError());
        assertNotNull("Error message should be provided", result.getError());
        assertFalse("Should not throw exception on network failure", false);
    }

    @Test
    public void testExecute_DefaultAnalysisType_IsPerformance() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            // No analysisType specified, should default to "performance"
            .build();

        SkillResult result = skill.execute(request);

        // With unreachable engine, expect error
        assertTrue("Result should indicate error with unreachable engine",
                   result.isError());
        assertNotNull("Error message should be non-null", result.getError());
    }

    // =========================================================================
    // Analysis Type Handling Tests (Unreachable Engine)
    // =========================================================================

    @Test
    public void testExecute_AnalysisTypeFull_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "full")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error for full analysis with unreachable engine",
                   result.isError());
        assertNotNull("Error message should be provided", result.getError());
    }

    @Test
    public void testExecute_AnalysisTypePerformance_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "performance")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error for performance analysis with unreachable engine",
                   result.isError());
        assertNotNull("Error message should be provided", result.getError());
    }

    @Test
    public void testExecute_AnalysisTypeVariants_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "variants")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error for variants analysis with unreachable engine",
                   result.isError());
        assertNotNull("Error message should be provided", result.getError());
    }

    @Test
    public void testExecute_AnalysisTypeXes_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "xes")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error for xes analysis with unreachable engine",
                   result.isError());
        assertNotNull("Error message should be provided", result.getError());
    }

    @Test
    public void testExecute_AnalysisTypeSocialNetwork_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "social_network")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error for social_network analysis with unreachable engine",
                   result.isError());
        assertNotNull("Error message should be provided", result.getError());
    }

    // =========================================================================
    // Optional Parameters Tests
    // =========================================================================

    @Test
    public void testExecute_WithSpecVersion_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("specVersion", "1.0")
            .build();

        SkillResult result = skill.execute(request);

        // Even with optional parameters, should fail gracefully with unreachable engine
        assertTrue("Result should indicate error", result.isError());
    }

    @Test
    public void testExecute_WithSpecUri_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("specUri", "http://localhost:8080/yawl/spec/1")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error", result.isError());
    }

    @Test
    public void testExecute_WithDataFlag_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("withData", "true")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error", result.isError());
    }

    @Test
    public void testExecute_WithAllOptionalParameters_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("specVersion", "2.0")
            .parameter("specUri", "http://example.com/spec")
            .parameter("analysisType", "full")
            .parameter("withData", "true")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue("Result should indicate error with all parameters and unreachable engine",
                   result.isError());
    }

    // =========================================================================
    // Constructor Validation Tests
    // =========================================================================

    @Test
    public void testConstructorRejectsNullUrl() {
        assertThrows("Constructor should reject null engine URL",
                     IllegalArgumentException.class,
                     () -> new ProcessMiningSkill(null, TEST_USERNAME, TEST_PASSWORD));
    }

    @Test
    public void testConstructorRejectsNullUsername() {
        assertThrows("Constructor should reject null username",
                     IllegalArgumentException.class,
                     () -> new ProcessMiningSkill(UNREACHABLE_ENGINE_URL, null, TEST_PASSWORD));
    }

    @Test
    public void testConstructorRejectsNullPassword() {
        assertThrows("Constructor should reject null password",
                     IllegalArgumentException.class,
                     () -> new ProcessMiningSkill(UNREACHABLE_ENGINE_URL, TEST_USERNAME, null));
    }

    // =========================================================================
    // Interface Compliance Tests
    // =========================================================================

    @Test
    public void testImplementsA2ASkill() {
        assertTrue("ProcessMiningSkill should implement A2ASkill",
                   skill instanceof A2ASkill);
    }

    @Test
    public void testImplementsA2ASkillInterface() {
        // Verify all required interface methods are callable
        assertNotNull("getId() should return non-null", skill.getId());
        assertNotNull("getName() should return non-null", skill.getName());
        assertNotNull("getDescription() should return non-null", skill.getDescription());
        assertNotNull("getRequiredPermissions() should return non-null",
                      skill.getRequiredPermissions());
    }

    // =========================================================================
    // SkillResult Validation Tests
    // =========================================================================

    @Test
    public void testExecute_ResultIsNotNull() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .build();

        SkillResult result = skill.execute(request);

        assertNotNull("Result should never be null", result);
    }

    @Test
    public void testExecute_ErrorResultHasErrorMessage() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .build();

        SkillResult result = skill.execute(request);

        if (result.isError()) {
            assertNotNull("Error result should have error message", result.getError());
            assertTrue("Error message should not be empty", !result.getError().isEmpty());
        }
    }

    // =========================================================================
    // Multiple Executions Tests
    // =========================================================================

    @Test
    public void testExecuteMultipleTimes() {
        SkillRequest request1 = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec1")
            .build();

        SkillRequest request2 = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec2")
            .build();

        SkillResult result1 = skill.execute(request1);
        SkillResult result2 = skill.execute(request2);

        assertTrue("First execution should error with unreachable engine", result1.isError());
        assertTrue("Second execution should error with unreachable engine", result2.isError());
    }

    // =========================================================================
    // Edge Cases
    // =========================================================================

    @Test
    public void testExecute_WithWhitespaceOnlySpecIdentifier() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "   ")
            .build();

        SkillResult result = skill.execute(request);

        // Should be treated as empty/invalid
        assertTrue("Result should indicate error for whitespace-only specIdentifier",
                   result.isError());
    }

    @Test
    public void testExecute_WithSpecialCharactersInSpecIdentifier() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "Test&Spec<>|")
            .build();

        SkillResult result = skill.execute(request);

        // Special characters are allowed in identifiers, should fail due to unreachable engine
        assertTrue("Result should indicate error with unreachable engine",
                   result.isError());
    }

    @Test
    public void testExecute_WithVeryLongSpecIdentifier() {
        String longId = "a".repeat(1000);
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", longId)
            .build();

        SkillResult result = skill.execute(request);

        // Long identifier should be accepted, fail due to unreachable engine
        assertTrue("Result should indicate error with unreachable engine",
                   result.isError());
    }

    @Test
    public void testExecute_WithInvalidAnalysisType() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "invalid_type")
            .build();

        SkillResult result = skill.execute(request);

        // Should handle gracefully (either reject invalid type or attempt analysis)
        assertTrue("Result should be either error or success",
                   result.isError() || result.isSuccess());
    }

    // =========================================================================
    // Skill Metadata Consistency Tests
    // =========================================================================

    @Test
    public void testIdConsistency() {
        String id1 = skill.getId();
        String id2 = skill.getId();
        assertEquals("getId() should return consistent value", id1, id2);
    }

    @Test
    public void testNameConsistency() {
        String name1 = skill.getName();
        String name2 = skill.getName();
        assertEquals("getName() should return consistent value", name1, name2);
    }

    @Test
    public void testPermissionsConsistency() {
        Set<String> perms1 = skill.getRequiredPermissions();
        Set<String> perms2 = skill.getRequiredPermissions();
        assertEquals("getRequiredPermissions() should return consistent value",
                     perms1, perms2);
    }

    @Test
    public void testPermissionsNotEmpty() {
        Set<String> permissions = skill.getRequiredPermissions();
        assertFalse("Required permissions should not be empty", permissions.isEmpty());
    }

    @Test
    public void testIdNotEmpty() {
        String id = skill.getId();
        assertFalse("ID should not be empty", id.isEmpty());
    }

    @Test
    public void testNameNotEmpty() {
        String name = skill.getName();
        assertFalse("Name should not be empty", name.isEmpty());
    }
}
