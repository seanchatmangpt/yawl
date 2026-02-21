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
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for A2ASkillMappingStep.
 *
 * Verifies that the skill mapping step correctly correlates discovered agents
 * and skills to workflow task slots based on the selected pattern.
 */
class A2ASkillMappingStepTest {

    private final A2ASkillMappingStep step = new A2ASkillMappingStep();

    @Test
    void testStepMetadata() {
        assertEquals("a2a-skill-mapping", step.stepId());
        assertEquals("Map A2A Skills to Workflow Tasks", step.title());
        assertEquals(WizardPhase.A2A_CONFIG, step.requiredPhase());
        assertNotNull(step.description());
    }

    @Test
    void testMappingWithoutDiscoveryFails() {
        WizardSession session = WizardSession.newSession(Map.of(
            "workflow.pattern", "WP-1"
        ));

        WizardStepResult<A2AWizardConfiguration> result = step.execute(session);
        assertFalse(result.isSuccess(), "Should fail without discovered agents");
        assertTrue(result.errorCount() > 0);
    }

    @Test
    void testMappingWithoutPatternFails() {
        A2AAgentDescriptor agent = A2ASkillRegistry.localYawlAgent("http://localhost:8080/yawl");

        WizardSession session = WizardSession.newSession(Map.of(
            "a2a.agents.discovered", List.of(agent)
        ));

        var prereqs = step.validatePrerequisites(session);
        assertTrue(prereqs.stream().anyMatch(e -> e.contains("pattern")),
            "Should warn about missing pattern");
    }

    @Test
    void testSuccessfulMappingWP1() {
        A2AAgentDescriptor agent = A2ASkillRegistry.localYawlAgent("http://localhost:8080/yawl");

        WizardSession session = WizardSession.newSession(Map.of(
            "a2a.agents.discovered", List.of(agent),
            "workflow.pattern", "WP-1"
        ));

        WizardStepResult<A2AWizardConfiguration> result = step.execute(session);
        assertTrue(result.isSuccess(), "Should succeed with agents and pattern");

        A2AWizardConfiguration config = result.value();
        assertNotNull(config);
        assertEquals(1, config.agentCount());
        assertTrue(config.bindingCount() >= 5, "WP-1 should bind all 5 skills");
    }

    @Test
    void testSuccessfulMappingWP3() {
        A2AAgentDescriptor agent = A2ASkillRegistry.localYawlAgent("http://localhost:8080/yawl");

        WizardSession session = WizardSession.newSession(Map.of(
            "a2a.agents.discovered", List.of(agent),
            "workflow.pattern", "WP-3"
        ));

        WizardStepResult<A2AWizardConfiguration> result = step.execute(session);
        assertTrue(result.isSuccess(), "WP-3 should succeed");

        A2AWizardConfiguration config = result.value();
        assertEquals(2, config.bindingCount(), "WP-3 should bind 2 skills");
    }

    @Test
    void testSuccessfulMappingWP5() {
        A2AAgentDescriptor agent = A2ASkillRegistry.localYawlAgent("http://localhost:8080/yawl");

        WizardSession session = WizardSession.newSession(Map.of(
            "a2a.agents.discovered", List.of(agent),
            "workflow.pattern", "WP-5"
        ));

        WizardStepResult<A2AWizardConfiguration> result = step.execute(session);
        assertTrue(result.isSuccess(), "WP-5 should succeed");

        A2AWizardConfiguration config = result.value();
        assertEquals(1, config.bindingCount(), "WP-5 should bind 1 skill");

        var skillIds = config.taskSlotSkillBindings().values().stream()
            .map(A2ASkillDescriptor::skillId)
            .toList();
        assertTrue(skillIds.contains("manage_workitems"));
    }

    @Test
    void testConfigurationConversion() {
        A2AAgentDescriptor agent = A2ASkillRegistry.localYawlAgent("http://localhost:8080/yawl");

        WizardSession session = WizardSession.newSession(Map.of(
            "a2a.agents.discovered", List.of(agent),
            "workflow.pattern", "WP-1"
        ));

        WizardStepResult<A2AWizardConfiguration> result = step.execute(session);
        A2AWizardConfiguration config = result.value();

        var configMap = config.toConfigMap();
        assertNotNull(configMap);
        assertTrue(configMap.containsKey("configurationId"));
        assertTrue(configMap.containsKey("primaryAgent"));
        assertTrue(configMap.containsKey("selectedAgents"));
        assertTrue(configMap.containsKey("taskBindings"));
    }

    @Test
    void testMultipleAgentsBinding() {
        A2AAgentDescriptor agent1 = A2ASkillRegistry.localYawlAgent("http://localhost:8080/yawl");
        A2AAgentDescriptor agent2 = new A2AAgentDescriptor(
            "agent-2",
            "Specialized Agent",
            "http://localhost:9090/agent",
            9090,
            "6.0.0",
            List.of(A2ASkillRegistry.findById("handoff_workitem").orElseThrow()),
            List.of("JWT"),
            A2AAgentStatus.AVAILABLE,
            java.time.Instant.now()
        );

        WizardSession session = WizardSession.newSession(Map.of(
            "a2a.agents.discovered", List.of(agent1, agent2),
            "workflow.pattern", "WP-1"
        ));

        WizardStepResult<A2AWizardConfiguration> result = step.execute(session);
        assertTrue(result.isSuccess());

        A2AWizardConfiguration config = result.value();
        assertEquals(2, config.agentCount(), "Should include both agents");
    }

    @Test
    void testNullSessionThrows() {
        assertThrows(NullPointerException.class, () -> step.execute(null));
        assertThrows(NullPointerException.class, () -> step.validatePrerequisites(null));
    }

    @Test
    void testUnknownPatternDefaultsToAll() {
        A2AAgentDescriptor agent = A2ASkillRegistry.localYawlAgent("http://localhost:8080/yawl");

        WizardSession session = WizardSession.newSession(Map.of(
            "a2a.agents.discovered", List.of(agent),
            "workflow.pattern", "WP-999"
        ));

        WizardStepResult<A2AWizardConfiguration> result = step.execute(session);
        assertTrue(result.isSuccess());

        A2AWizardConfiguration config = result.value();
        assertEquals(5, config.bindingCount(), "Unknown pattern should default to all 5 skills");
    }
}
