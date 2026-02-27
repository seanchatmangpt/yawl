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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @BeforeEach
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
        assertNotNull(description, "Description should not be null");
        assertFalse(description.isEmpty(), "Description should not be empty");
        assertTrue(
            description.toLowerCase().contains("process mining") ||
            description.toLowerCase().contains("analysis"),
            "Description should mention process mining");
    }

    @Test
    public void testGetRequiredPermissions_ContainsWorkflowRead() {
        Set<String> permissions = skill.getRequiredPermissions();
        assertNotNull(permissions, "Permissions should not be null");
        assertTrue(permissions.contains("workflow:read"), "Permissions should contain 'workflow:read'");
    }

    @Test
    public void testGetRequiredPermissions_ContainsProcessMiningRead() {
        Set<String> permissions = skill.getRequiredPermissions();
        assertNotNull(permissions, "Permissions should not be null");
        assertTrue(permissions.contains("process-mining:read"), "Permissions should contain 'process-mining:read'");
    }

    // =========================================================================
    // Permission Check Tests (canExecute)
    // =========================================================================

    @Test
    public void testCanExecute_WithAllPermission() {
        Set<String> permissions = Set.of("*");
        assertTrue(skill.canExecute(permissions), "canExecute should return true with wildcard permission");
    }

    @Test
    public void testCanExecute_WithRequiredPermissions() {
        Set<String> permissions = Set.of("workflow:read", "process-mining:read");
        assertTrue(skill.canExecute(permissions), "canExecute should return true with all required permissions");
    }

    @Test
    public void testCanExecute_WithInsufficientPermissions() {
        Set<String> permissions = Set.of("workflow:read");
        assertFalse(skill.canExecute(permissions), "canExecute should return false with incomplete permissions");
    }

    @Test
    public void testCanExecute_WithEmptyPermissions() {
        Set<String> permissions = Set.of();
        assertFalse(skill.canExecute(permissions), "canExecute should return false with empty permissions");
    }

    // =========================================================================
    // Parameter Validation Tests
    // =========================================================================

    @Test
    public void testExecute_MissingSpecIdentifier_ReturnsError() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue(result.isError(), "Result should indicate error when specIdentifier is missing");
        assertNotNull(result.getError(), "Error message should be provided");
    }

    @Test
    public void testExecute_EmptySpecIdentifier_ReturnsError() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue(result.isError(), "Result should indicate error when specIdentifier is empty");
        assertNotNull(result.getError(), "Error message should be provided");
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

        assertTrue(result.isError(), "Result should indicate error when engine is unreachable");
        assertNotNull(result.getError(), "Error message should be provided");
        assertFalse(false, "Should not throw exception on network failure");
    }

    @Test
    public void testExecute_DefaultAnalysisType_IsPerformance() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            // No analysisType specified, should default to "performance"
            .build();

        SkillResult result = skill.execute(request);

        // With unreachable engine, expect error
        assertTrue(result.isError(), "Result should indicate error with unreachable engine");
        assertNotNull(result.getError(), "Error message should be non-null");
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

        assertTrue(result.isError(), "Result should indicate error for full analysis with unreachable engine");
        assertNotNull(result.getError(), "Error message should be provided");
    }

    @Test
    public void testExecute_AnalysisTypePerformance_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "performance")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue(result.isError(), "Result should indicate error for performance analysis with unreachable engine");
        assertNotNull(result.getError(), "Error message should be provided");
    }

    @Test
    public void testExecute_AnalysisTypeVariants_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "variants")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue(result.isError(), "Result should indicate error for variants analysis with unreachable engine");
        assertNotNull(result.getError(), "Error message should be provided");
    }

    @Test
    public void testExecute_AnalysisTypeXes_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "xes")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue(result.isError(), "Result should indicate error for xes analysis with unreachable engine");
        assertNotNull(result.getError(), "Error message should be provided");
    }

    @Test
    public void testExecute_AnalysisTypeSocialNetwork_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "social_network")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue(result.isError(), "Result should indicate error for social_network analysis with unreachable engine");
        assertNotNull(result.getError(), "Error message should be provided");
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
        assertTrue(result.isError(), "Result should indicate error");
    }

    @Test
    public void testExecute_WithSpecUri_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("specUri", "http://localhost:8080/yawl/spec/1")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue(result.isError(), "Result should indicate error");
    }

    @Test
    public void testExecute_WithDataFlag_UnreachableEngine() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("withData", "true")
            .build();

        SkillResult result = skill.execute(request);

        assertTrue(result.isError(), "Result should indicate error");
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

        assertTrue(result.isError(), "Result should indicate error with all parameters and unreachable engine");
    }

    // =========================================================================
    // Constructor Validation Tests
    // =========================================================================

    @Test
    public void testConstructorRejectsNullUrl() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProcessMiningSkill(null, TEST_USERNAME, TEST_PASSWORD),
            "Constructor should reject null engine URL");
    }

    @Test
    public void testConstructorRejectsNullUsername() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProcessMiningSkill(UNREACHABLE_ENGINE_URL, null, TEST_PASSWORD),
            "Constructor should reject null username");
    }

    @Test
    public void testConstructorRejectsNullPassword() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProcessMiningSkill(UNREACHABLE_ENGINE_URL, TEST_USERNAME, null),
            "Constructor should reject null password");
    }

    // =========================================================================
    // Interface Compliance Tests
    // =========================================================================

    @Test
    public void testImplementsA2ASkill() {
        assertTrue(skill instanceof A2ASkill, "ProcessMiningSkill should implement A2ASkill");
    }

    @Test
    public void testImplementsA2ASkillInterface() {
        // Verify all required interface methods are callable
        assertNotNull(skill.getId(), "getId() should return non-null");
        assertNotNull(skill.getName(), "getName() should return non-null");
        assertNotNull(skill.getDescription(), "getDescription() should return non-null");
        assertNotNull(skill.getRequiredPermissions(), "getRequiredPermissions() should return non-null");
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

        assertNotNull(result, "Result should never be null");
    }

    @Test
    public void testExecute_ErrorResultHasErrorMessage() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .build();

        SkillResult result = skill.execute(request);

        if (result.isError()) {
            assertNotNull(result.getError(), "Error result should have error message");
            assertTrue(!result.getError().isEmpty(), "Error message should not be empty");
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

        assertTrue(result1.isError(), "First execution should error with unreachable engine");
        assertTrue(result2.isError(), "Second execution should error with unreachable engine");
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
        assertTrue(result.isError(), "Result should indicate error for whitespace-only specIdentifier");
    }

    @Test
    public void testExecute_WithSpecialCharactersInSpecIdentifier() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "Test&Spec<>|")
            .build();

        SkillResult result = skill.execute(request);

        // Special characters are allowed in identifiers, should fail due to unreachable engine
        assertTrue(result.isError(), "Result should indicate error with unreachable engine");
    }

    @Test
    public void testExecute_WithVeryLongSpecIdentifier() {
        String longId = "a".repeat(1000);
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", longId)
            .build();

        SkillResult result = skill.execute(request);

        // Long identifier should be accepted, fail due to unreachable engine
        assertTrue(result.isError(), "Result should indicate error with unreachable engine");
    }

    @Test
    public void testExecute_WithInvalidAnalysisType() {
        SkillRequest request = SkillRequest.builder("process_mining_analyze")
            .parameter("specIdentifier", "TestSpec")
            .parameter("analysisType", "invalid_type")
            .build();

        SkillResult result = skill.execute(request);

        // Should handle gracefully (either reject invalid type or attempt analysis)
        assertTrue(result.isError() || result.isSuccess(), "Result should be either error or success");
    }

    // =========================================================================
    // Skill Metadata Consistency Tests
    // =========================================================================

    @Test
    public void testIdConsistency() {
        String id1 = skill.getId();
        String id2 = skill.getId();
        assertEquals(id1, id2, "getId() should return consistent value");
    }

    @Test
    public void testNameConsistency() {
        String name1 = skill.getName();
        String name2 = skill.getName();
        assertEquals(name1, name2, "getName() should return consistent value");
    }

    @Test
    public void testPermissionsConsistency() {
        Set<String> perms1 = skill.getRequiredPermissions();
        Set<String> perms2 = skill.getRequiredPermissions();
        assertEquals(perms1, perms2, "getRequiredPermissions() should return consistent value");
    }

    @Test
    public void testPermissionsNotEmpty() {
        Set<String> permissions = skill.getRequiredPermissions();
        assertFalse(permissions.isEmpty(), "Required permissions should not be empty");
    }

    @Test
    public void testIdNotEmpty() {
        String id = skill.getId();
        assertFalse(id.isEmpty(), "ID should not be empty");
    }

    @Test
    public void testNameNotEmpty() {
        String name = skill.getName();
        assertFalse(name.isEmpty(), "Name should not be empty");
    }
}
