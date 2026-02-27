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
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for A2ADiscoveryStep.
 *
 * Verifies that the discovery step correctly identifies available A2A agents
 * and collects their capabilities.
 */
class A2ADiscoveryStepTest {

    private final A2ADiscoveryStep step = new A2ADiscoveryStep();

    @Test
    void testStepMetadata() {
        assertEquals("a2a-discovery", step.stepId());
        assertEquals("Discover A2A Agents", step.title());
        assertEquals(WizardPhase.DISCOVERY, step.requiredPhase());
        assertNotNull(step.description());
    }

    @Test
    void testDiscoverySuccess() {
        WizardSession session = WizardSession.newSession(Map.of(
            "yawl.engine.url", "http://localhost:8080/yawl"
        ));

        WizardStepResult<List<A2AAgentDescriptor>> result = step.execute(session);
        assertTrue(result.isSuccess(), "Discovery should succeed");
        assertEquals(0, result.errorCount());

        List<A2AAgentDescriptor> agents = result.value();
        assertNotNull(agents);
        assertFalse(agents.isEmpty(), "Should discover at least local agent");

        A2AAgentDescriptor localAgent = agents.get(0);
        assertEquals("yawl-local", localAgent.agentId());
        assertEquals("YAWL Workflow Engine", localAgent.agentName());
        assertEquals(A2AAgentStatus.AVAILABLE, localAgent.status());
        assertEquals(5, localAgent.skills().size());
    }

    @Test
    void testDiscoveryWithDefaultUrl() {
        WizardSession session = WizardSession.newSession();

        WizardStepResult<List<A2AAgentDescriptor>> result = step.execute(session);
        assertTrue(result.isSuccess(), "Discovery should use default URL");

        List<A2AAgentDescriptor> agents = result.value();
        assertNotNull(agents);
        assertFalse(agents.isEmpty());
    }

    @Test
    void testContextPopulation() {
        WizardSession session = WizardSession.newSession(Map.of(
            "yawl.engine.url", "http://localhost:8080/yawl"
        ));

        step.execute(session);

        var agents = session.get("a2a.agents.discovered", List.class);
        assertTrue(agents.isPresent(), "Should set a2a.agents.discovered");

        var count = session.get("a2a.agent.count", Integer.class);
        assertTrue(count.isPresent(), "Should set a2a.agent.count");
        assertTrue(count.get() > 0);

        var skills = session.get("a2a.skills.available", List.class);
        assertTrue(skills.isPresent(), "Should set a2a.skills.available");

        var localAgent = session.get("a2a.local.agent", A2AAgentDescriptor.class);
        assertTrue(localAgent.isPresent(), "Should set a2a.local.agent");
        assertEquals("yawl-local", localAgent.get().agentId());
    }

    @Test
    void testAllSkillsDiscovered() {
        WizardSession session = WizardSession.newSession();
        step.execute(session);

        var skillsOpt = session.get("a2a.skills.available", List.class);
        assertTrue(skillsOpt.isPresent());

        List<A2ASkillDescriptor> skills = (List<A2ASkillDescriptor>) skillsOpt.get();
        assertEquals(5, skills.size(), "Should discover all 5 YAWL skills");

        var skillIds = skills.stream()
            .map(A2ASkillDescriptor::skillId)
            .toList();

        assertTrue(skillIds.contains("launch_workflow"));
        assertTrue(skillIds.contains("query_workflows"));
        assertTrue(skillIds.contains("manage_workitems"));
        assertTrue(skillIds.contains("cancel_workflow"));
        assertTrue(skillIds.contains("handoff_workitem"));
    }

    @Test
    void testAgentSupportedAuthMethods() {
        WizardSession session = WizardSession.newSession();
        step.execute(session);

        var agentsOpt = session.get("a2a.agents.discovered", List.class);
        assertTrue(agentsOpt.isPresent());

        List<A2AAgentDescriptor> agents = (List<A2AAgentDescriptor>) agentsOpt.get();
        A2AAgentDescriptor agent = agents.get(0);

        assertTrue(agent.supportsAuthMethod("JWT"));
        assertTrue(agent.supportsAuthMethod("API_KEY"));
        assertTrue(agent.supportsAuthMethod("SPIFFE_MTLS"));
    }

    @Test
    void testPrerequisitesAlwaysMet() {
        WizardSession session = WizardSession.newSession();
        var prereqs = step.validatePrerequisites(session);
        assertTrue(prereqs.isEmpty(), "Discovery has no prerequisites");
    }

    @Test
    void testAgentHasAllSkills() {
        WizardSession session = WizardSession.newSession();
        step.execute(session);

        var agentsOpt = session.get("a2a.agents.discovered", List.class);
        List<A2AAgentDescriptor> agents = (List<A2AAgentDescriptor>) agentsOpt.get();
        A2AAgentDescriptor agent = agents.get(0);

        assertTrue(agent.hasSkill("launch_workflow"));
        assertTrue(agent.hasSkill("query_workflows"));
        assertTrue(agent.hasSkill("manage_workitems"));
        assertTrue(agent.hasSkill("cancel_workflow"));
        assertTrue(agent.hasSkill("handoff_workitem"));
    }

    @Test
    void testNullSessionThrows() {
        assertThrows(NullPointerException.class, () -> step.execute(null));
    }
}
