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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.wizard.a2a;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for A2AHandoffStep.
 *
 * Verifies that the handoff configuration step correctly builds A2AHandoffConfiguration
 * from discovered agents and transitions the wizard to the validation phase.
 */
class A2AHandoffStepTest {

    private A2AHandoffStep step;
    private WizardSession session;
    private List<A2AAgentDescriptor> twoAgents;

    @BeforeEach
    void setUp() {
        step = new A2AHandoffStep();

        A2AAgentDescriptor yawlAgent = new A2AAgentDescriptor(
                "yawl-local",
                "YAWL Workflow Engine",
                "http://localhost:8080/yawl",
                8081,
                "6.0.0",
                A2ASkillRegistry.allSkills(),
                List.of("JWT", "API_KEY", "SPIFFE_MTLS"),
                A2AAgentStatus.AVAILABLE,
                Instant.now()
        );

        A2AAgentDescriptor externalAgent = new A2AAgentDescriptor(
                "external-agent",
                "External Agent",
                "http://remote.example.com",
                8081,
                "6.0.0",
                A2ASkillRegistry.allSkills(),
                List.of("JWT", "API_KEY"),
                A2AAgentStatus.AVAILABLE,
                Instant.now()
        );

        twoAgents = List.of(yawlAgent, externalAgent);

        session = WizardSession.newSession()
                .withPhase(WizardPhase.A2A_CONFIG, "test", "Test setup")
                .withContext("a2a.agents.discovered", twoAgents);
    }

    @Test
    void testHandoffStepProperties() {
        assertEquals("a2a-handoff", step.stepId());
        assertEquals("Configure Agent Handoff Protocol", step.title());
        assertEquals(WizardPhase.A2A_CONFIG, step.requiredPhase());
        assertNotNull(step.description());
    }

    @Test
    void testHandoffConfigurationGenerated() {
        WizardStepResult<A2AHandoffConfiguration> result = step.execute(session);

        assertTrue(result.success(), "Handoff step should succeed with 2+ agents");
        assertNotNull(result.value());
        A2AHandoffConfiguration config = result.value();

        assertEquals("yawl-local", config.sourceAgentId());
        assertEquals("external-agent", config.targetAgentId());
        assertEquals("JWT", config.authMethod());
        assertTrue(config.isJwtAuth());
        assertFalse(config.isApiKeyAuth());
        assertFalse(config.isSpiffeMtlsAuth());
    }

    @Test
    void testHandoffTokenExpiryDefault() {
        WizardStepResult<A2AHandoffConfiguration> result = step.execute(session);

        assertTrue(result.success());
        A2AHandoffConfiguration config = result.value();
        assertEquals(60L, config.tokenExpirySeconds(), "Default token expiry should be 60 seconds");
    }

    @Test
    void testHandoffAgentEndpointMapping() {
        WizardStepResult<A2AHandoffConfiguration> result = step.execute(session);

        assertTrue(result.success());
        A2AHandoffConfiguration config = result.value();

        assertNotNull(config.sourceEndpoint(), "Source endpoint should be configured");
        assertNotNull(config.targetEndpoint(), "Target endpoint should be configured");
        assertEquals("http://localhost:8080/yawl:8081", config.sourceEndpoint());
        assertEquals("http://remote.example.com:8081", config.targetEndpoint());
    }

    @Test
    void testFailureWithNoAgents() {
        WizardSession noAgentsSession = WizardSession.newSession()
                .withPhase(WizardPhase.A2A_CONFIG, "test", "Test");

        WizardStepResult<A2AHandoffConfiguration> result = step.execute(noAgentsSession);

        assertFalse(result.success(), "Should fail when no agents discovered");
        assertTrue(result.errors().get(0).contains("agents"), "Error should mention agents");
    }

    @Test
    void testFailureWithSingleAgent() {
        List<A2AAgentDescriptor> singleAgent = List.of(twoAgents.get(0));
        WizardSession singleAgentSession = WizardSession.newSession()
                .withPhase(WizardPhase.A2A_CONFIG, "test", "Test")
                .withContext("a2a.agents.discovered", singleAgent);

        WizardStepResult<A2AHandoffConfiguration> result = step.execute(singleAgentSession);

        assertFalse(result.success(), "Should fail when only 1 agent available");
        assertTrue(result.errors().get(0).contains("2 agents"), "Error should mention needing 2 agents");
    }

    @Test
    void testHandoffAuthMethodSelection() {
        // Agent with API_KEY as first auth method
        A2AAgentDescriptor apiKeyAgent = new A2AAgentDescriptor(
                "yawl-local",
                "YAWL Workflow Engine",
                "http://localhost:8080/yawl",
                8081,
                "6.0.0",
                A2ASkillRegistry.allSkills(),
                List.of("API_KEY", "JWT"),  // API_KEY is first
                A2AAgentStatus.AVAILABLE,
                Instant.now()
        );

        WizardSession apiKeySession = WizardSession.newSession()
                .withPhase(WizardPhase.A2A_CONFIG, "test", "Test")
                .withContext("a2a.agents.discovered", List.of(apiKeyAgent, twoAgents.get(1)));

        WizardStepResult<A2AHandoffConfiguration> result = step.execute(apiKeySession);

        assertTrue(result.success());
        assertEquals("API_KEY", result.value().authMethod());
    }

    @Test
    void testHandoffConfigurationImmutable() {
        WizardStepResult<A2AHandoffConfiguration> result = step.execute(session);

        A2AHandoffConfiguration config = result.value();

        // Verify immutability of agent endpoints map
        assertThrows(UnsupportedOperationException.class, () ->
                config.agentEndpoints().put("new-agent", "http://example.com")
        );
    }

    @Test
    void testValidatePrerequisites() {
        WizardSession noAgentsSession = WizardSession.newSession()
                .withPhase(WizardPhase.A2A_CONFIG, "test", "Test");

        List<String> errors = step.validatePrerequisites(noAgentsSession);

        assertFalse(errors.isEmpty(), "Should have validation errors");
        assertTrue(errors.get(0).contains("agents"), "Should mention agents");
    }

    @Test
    void testValidatePrerequisitesWithAgents() {
        List<String> errors = step.validatePrerequisites(session);

        assertTrue(errors.isEmpty(), "Should pass validation with agents discovered");
    }

    @Test
    void testHandoffWithThreeAgents() {
        A2AAgentDescriptor thirdAgent = new A2AAgentDescriptor(
                "third-agent",
                "Third Agent",
                "http://third.example.com",
                8081,
                "6.0.0",
                A2ASkillRegistry.allSkills(),
                List.of("JWT"),
                A2AAgentStatus.AVAILABLE,
                Instant.now()
        );

        WizardSession threeAgentsSession = WizardSession.newSession()
                .withPhase(WizardPhase.A2A_CONFIG, "test", "Test")
                .withContext("a2a.agents.discovered",
                        List.of(twoAgents.get(0), twoAgents.get(1), thirdAgent));

        WizardStepResult<A2AHandoffConfiguration> result = step.execute(threeAgentsSession);

        assertTrue(result.success());
        A2AHandoffConfiguration config = result.value();
        assertEquals("yawl-local", config.sourceAgentId());
        assertEquals("external-agent", config.targetAgentId());
        assertEquals(3, config.agentEndpoints().size());
    }

    @Test
    void testHandoffConfigurationWithoutMutualTls() {
        WizardStepResult<A2AHandoffConfiguration> result = step.execute(session);

        assertTrue(result.success());
        A2AHandoffConfiguration config = result.value();
        assertFalse(config.requiresMutualTls(), "Handoff should not require mTLS by default");
    }

    @Test
    void testWithTokenExpiry() {
        WizardStepResult<A2AHandoffConfiguration> result = step.execute(session);

        A2AHandoffConfiguration config = result.value();
        A2AHandoffConfiguration updated = config.withTokenExpiry(300L);

        // Original unchanged
        assertEquals(60L, config.tokenExpirySeconds());

        // Updated has new expiry
        assertEquals(300L, updated.tokenExpirySeconds());

        // Other fields unchanged
        assertEquals(config.sourceAgentId(), updated.sourceAgentId());
        assertEquals(config.targetAgentId(), updated.targetAgentId());
    }

    @Test
    void testNullSessionParameter() {
        assertThrows(NullPointerException.class, () ->
                step.execute(null)
        );
        assertThrows(NullPointerException.class, () ->
                step.validatePrerequisites(null)
        );
    }
}
