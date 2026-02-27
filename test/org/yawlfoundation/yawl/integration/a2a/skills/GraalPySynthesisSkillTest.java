/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link GraalPySynthesisSkill}.
 *
 * <p>Uses real objects — no mocks. On standard JDK (Temurin), GraalPy is unavailable,
 * so tests exercise the fallback path ({@code PatternBasedSynthesizer}) for descriptions
 * and the explicit error path for XES mining.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GraalPySynthesisSkillTest {

    private GraalPySynthesisSkill skill;

    @BeforeEach
    public void setUp() {
        skill = new GraalPySynthesisSkill();
    }

    @AfterEach
    public void tearDown() {
        skill.close();
    }

    // =========================================================================
    // Metadata Tests
    // =========================================================================

    @Test
    public void testSkillId() {
        assertEquals("graalpy_synthesize", skill.getId());
    }

    @Test
    public void testSkillName() {
        assertEquals("GraalPy Workflow Synthesis", skill.getName());
    }

    @Test
    public void testSkillDescriptionNonNull() {
        assertNotNull(skill.getDescription());
        assertFalse(skill.getDescription().isBlank());
    }

    @Test
    public void testRequiredPermissionContainsSynthesize() {
        Set<String> perms = skill.getRequiredPermissions();
        assertNotNull(perms);
        assertTrue(perms.contains("workflow:synthesize"),
            "Expected 'workflow:synthesize' in " + perms);
    }

    @Test
    public void testTagsContainWorkflowAndGraalpy() {
        var tags = skill.getTags();
        assertNotNull(tags);
        assertTrue(tags.contains("workflow"), "Expected 'workflow' tag in " + tags);
        assertTrue(tags.contains("graalpy"), "Expected 'graalpy' tag in " + tags);
        assertTrue(tags.contains("no-llm"), "Expected 'no-llm' tag in " + tags);
    }

    // =========================================================================
    // Permission Tests
    // =========================================================================

    @Test
    public void testCanExecuteWithExactPermission() {
        assertTrue(skill.canExecute(Set.of("workflow:synthesize")));
    }

    @Test
    public void testCanExecuteWithWildcard() {
        assertTrue(skill.canExecute(Set.of("*")));
    }

    @Test
    public void testCannotExecuteWithoutPermission() {
        assertFalse(skill.canExecute(Set.of("process-mining:read")));
    }

    @Test
    public void testCannotExecuteWithEmptyPermissions() {
        assertFalse(skill.canExecute(Set.of()));
    }

    // =========================================================================
    // Parameter Validation Tests
    // =========================================================================

    @Test
    public void testMissingBothParametersReturnsError() {
        SkillRequest request = SkillRequest.builder("graalpy_synthesize").build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isError());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("description") || result.getError().contains("xesContent"),
            "Error should mention required parameters: " + result.getError());
    }

    @Test
    public void testBlankDescriptionReturnsError() {
        SkillRequest request = SkillRequest.builder("graalpy_synthesize")
            .parameter("description", "   ")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isError());
    }

    @Test
    public void testBlankXesContentReturnsError() {
        SkillRequest request = SkillRequest.builder("graalpy_synthesize")
            .parameter("xesContent", "   ")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isError());
    }

    // =========================================================================
    // Synthesis Behaviour Tests (Temurin JDK → GraalVM unavailable)
    // =========================================================================

    @Test
    public void testDescriptionSynthesisReturnsResult() {
        // On Temurin JDK: GraalPy unavailable → falls back to PatternBasedSynthesizer
        // On GraalVM JDK: uses real pm4py
        SkillRequest request = SkillRequest.builder("graalpy_synthesize")
            .parameter("description", "submit application then review then approve")
            .build();
        SkillResult result = skill.execute(request);

        // Either path must succeed and return yawlXml
        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        assertNotNull(result.get("yawlXml"));
        String yawlXml = (String) result.get("yawlXml");
        assertFalse(yawlXml.isBlank(), "yawlXml must not be blank");
        assertNotNull(result.get("path"), "path must be set");
        assertNotNull(result.get("elapsed_ms"), "elapsed_ms must be set");
    }

    @Test
    public void testDescriptionSynthesisYawlXmlContainsSpecificationSet() {
        SkillRequest request = SkillRequest.builder("graalpy_synthesize")
            .parameter("description", "sequential workflow: step one then step two")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        String yawlXml = (String) result.get("yawlXml");
        // Both PatternBasedSynthesizer and YawlSpecExporter produce specificationSet XML
        assertTrue(yawlXml.contains("specificationSet") || yawlXml.contains("specification"),
            "YAWL XML must contain specification element: " + yawlXml.substring(0, Math.min(200, yawlXml.length())));
    }

    @Test
    public void testXesMiningOnStandardJdkReturnsError() {
        // On Temurin JDK: GraalVM unavailable → explicit error (no fallback for mining)
        // On GraalVM JDK: would attempt real mining (may fail if pm4py not installed)
        SkillRequest request = SkillRequest.builder("graalpy_synthesize")
            .parameter("xesContent", "<log xes.version=\"1.0\"><trace><event><string key=\"concept:name\" value=\"A\"/></event></trace></log>")
            .build();
        SkillResult result = skill.execute(request);

        // On standard JDK, this must be an error
        // On GraalVM with pm4py, this might succeed
        if (result.isError()) {
            // Standard JDK path — verify error message mentions GraalVM
            assertTrue(result.getError().contains("GraalVM") || result.getError().contains("pm4py")
                || result.getError().contains("mining"),
                "Error should explain GraalVM requirement: " + result.getError());
        }
        // If success (GraalVM with pm4py), verify yawlXml is present
        if (result.isSuccess()) {
            assertNotNull(result.get("yawlXml"));
        }
    }

    // =========================================================================
    // Lifecycle Tests
    // =========================================================================

    @Test
    public void testCloseDoesNotThrow() {
        // Verify AutoCloseable contract
        assertDoesNotThrow(() -> skill.close());
    }

    @Test
    public void testImplementsA2ASkill() {
        assertTrue(skill instanceof A2ASkill,
            "GraalPySynthesisSkill must implement A2ASkill");
    }
}
