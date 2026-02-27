/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.wizard.a2a;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for A2ASkillRegistry.
 *
 * Verifies that all 5 YAWL A2A skills are registered and accessible,
 * and that skill discovery/filtering works correctly.
 */
class A2ASkillRegistryTest {

    @Test
    void testAllSkillsPresent() {
        List<A2ASkillDescriptor> allSkills = A2ASkillRegistry.allSkills();
        assertEquals(5, allSkills.size(), "Should have exactly 5 YAWL A2A skills");

        assertTrue(allSkills.stream().anyMatch(s -> "launch_workflow".equals(s.skillId())));
        assertTrue(allSkills.stream().anyMatch(s -> "query_workflows".equals(s.skillId())));
        assertTrue(allSkills.stream().anyMatch(s -> "manage_workitems".equals(s.skillId())));
        assertTrue(allSkills.stream().anyMatch(s -> "cancel_workflow".equals(s.skillId())));
        assertTrue(allSkills.stream().anyMatch(s -> "handoff_workitem".equals(s.skillId())));
    }

    @Test
    void testSkillsHaveValidMetadata() {
        A2ASkillRegistry.allSkills().forEach(skill -> {
            assertNotNull(skill.skillId(), "skillId must not be null");
            assertNotNull(skill.displayName(), "displayName must not be null");
            assertNotNull(skill.description(), "description must not be null");
            assertFalse(skill.inputModes().isEmpty(), "inputModes must not be empty");
            assertFalse(skill.outputModes().isEmpty(), "outputModes must not be empty");
            assertNotNull(skill.category(), "category must not be null");
            assertFalse(skill.exampleInputs().isEmpty(), "exampleInputs must not be empty");
        });
    }

    @Test
    void testFindSkillById() {
        Optional<A2ASkillDescriptor> skill = A2ASkillRegistry.findById("launch_workflow");
        assertTrue(skill.isPresent(), "launch_workflow should exist");
        assertEquals("Launch Workflow", skill.get().displayName());
        assertEquals(A2ASkillCategory.WORKFLOW_EXECUTION, skill.get().category());
    }

    @Test
    void testFindSkillByIdNotFound() {
        Optional<A2ASkillDescriptor> skill = A2ASkillRegistry.findById("nonexistent_skill");
        assertFalse(skill.isPresent(), "Non-existent skill should not be found");
    }

    @Test
    void testSkillsByCategory() {
        List<A2ASkillDescriptor> executionSkills =
            A2ASkillRegistry.byCategory(A2ASkillCategory.WORKFLOW_EXECUTION);
        assertTrue(executionSkills.size() >= 2, "Should have at least launch and cancel");

        List<A2ASkillDescriptor> querySkills =
            A2ASkillRegistry.byCategory(A2ASkillCategory.WORKFLOW_QUERY);
        assertEquals(1, querySkills.size(), "Should have 1 query skill");
        assertEquals("query_workflows", querySkills.get(0).skillId());

        List<A2ASkillDescriptor> coordSkills =
            A2ASkillRegistry.byCategory(A2ASkillCategory.AGENT_COORDINATION);
        assertEquals(1, coordSkills.size(), "Should have 1 coordination skill");
        assertEquals("handoff_workitem", coordSkills.get(0).skillId());
    }

    @Test
    void testRecommendedSkillsForPatterns() {
        List<A2ASkillDescriptor> wp1Skills = A2ASkillRegistry.recommendedForPattern("WP-1");
        assertEquals(5, wp1Skills.size(), "WP-1 (Sequence) should use all skills");

        List<A2ASkillDescriptor> wp3Skills = A2ASkillRegistry.recommendedForPattern("WP-3");
        assertEquals(2, wp3Skills.size(), "WP-3 (Synchronization) should use 2 skills");

        List<A2ASkillDescriptor> wp5Skills = A2ASkillRegistry.recommendedForPattern("WP-5");
        assertEquals(1, wp5Skills.size(), "WP-5 (Simple Merge) should use 1 skill");
        assertEquals("manage_workitems", wp5Skills.get(0).skillId());

        List<A2ASkillDescriptor> unknownSkills = A2ASkillRegistry.recommendedForPattern("WP-999");
        assertEquals(5, unknownSkills.size(), "Unknown pattern should default to all skills");
    }

    @Test
    void testLocalYawlAgent() {
        String engineUrl = "http://localhost:8080/yawl";
        A2AAgentDescriptor agent = A2ASkillRegistry.localYawlAgent(engineUrl);

        assertEquals("yawl-local", agent.agentId());
        assertEquals("YAWL Workflow Engine", agent.agentName());
        assertEquals(engineUrl, agent.agentUrl());
        assertEquals(8081, agent.port());
        assertEquals(5, agent.skills().size(), "Local agent should have all 5 skills");
        assertEquals(A2AAgentStatus.AVAILABLE, agent.status());
        assertTrue(agent.supportsAuthMethod("JWT"));
        assertTrue(agent.supportsAuthMethod("API_KEY"));
        assertTrue(agent.supportsAuthMethod("SPIFFE_MTLS"));
    }

    @Test
    void testSkillCount() {
        assertEquals(5, A2ASkillRegistry.skillCount());
    }

    @Test
    void testHandoffSkillSupport() {
        Optional<A2ASkillDescriptor> handoff = A2ASkillRegistry.findById("handoff_workitem");
        assertTrue(handoff.isPresent());
        assertTrue(handoff.get().supportsHandoff(), "handoff_workitem should support handoff");

        Optional<A2ASkillDescriptor> launch = A2ASkillRegistry.findById("launch_workflow");
        assertTrue(launch.isPresent());
        assertFalse(launch.get().supportsHandoff(), "launch_workflow should not support handoff");
    }

    @Test
    void testAllSkillsRequireAuthentication() {
        A2ASkillRegistry.allSkills().forEach(skill -> {
            assertTrue(skill.requiresAuthentication(),
                skill.skillId() + " should require authentication");
        });
    }
}
