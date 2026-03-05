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

import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStep;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Configures A2A handoff protocol between agents.
 *
 * <p>Enables secure work item transfer between agents when one agent cannot
 * complete work and must hand it off to a specialized agent. Follows ADR-025
 * (YAWL A2A Handoff Protocol) with JWT/API Key/SPIFFE mTLS authentication.
 *
 * <p>Handoff workflow:
 * <ol>
 *   <li>Source agent checks out work item from YAWL engine</li>
 *   <li>Source issues HandoffToken (JWT-signed with caseId, taskId, targetAgent)</li>
 *   <li>Target agent validates token signature</li>
 *   <li>Target claims work item with valid token</li>
 *   <li>Source rolls back checkout (releases lock)</li>
 *   <li>Target executes and completes work item</li>
 * </ol>
 *
 * <p>Session context keys consumed:
 * <ul>
 *   <li>"a2a.agents.discovered" (List&lt;A2AAgentDescriptor&gt;) - available agents</li>
 *   <li>"a2a.configuration" (A2AWizardConfiguration) - skill mapping from previous step</li>
 * </ul>
 *
 * <p>Session context keys produced:
 * <ul>
 *   <li>"a2a.handoff.configuration" (A2AHandoffConfiguration) - handoff config</li>
 * </ul>
 *
 * @since YAWL 6.0
 * @see org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol
 * @see org.yawlfoundation.yawl.integration.a2a.handoff.HandoffToken
 */
public class A2AHandoffStep implements WizardStep<A2AHandoffConfiguration> {
    private static final String AGENTS_KEY = "a2a.agents.discovered";
    private static final String CONFIG_KEY = "a2a.configuration";
    private static final String HANDOFF_CONFIG_KEY = "a2a.handoff.configuration";
    private static final long DEFAULT_TOKEN_EXPIRY_SECONDS = 60L;

    @Override
    public String stepId() {
        return "a2a-handoff";
    }

    @Override
    public String title() {
        return "Configure Agent Handoff Protocol";
    }

    @Override
    public WizardPhase requiredPhase() {
        return WizardPhase.A2A_CONFIG;
    }

    @Override
    public WizardStepResult<A2AHandoffConfiguration> execute(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");

        try {
            List<A2AAgentDescriptor> agents = session.get(AGENTS_KEY, List.class)
                .orElse(List.of());

            if (agents.isEmpty()) {
                return WizardStepResult.failure(
                    stepId(),
                    "No agents available. Run discovery step first."
                );
            }

            if (agents.size() < 2) {
                return WizardStepResult.failure(
                    stepId(),
                    "Handoff requires at least 2 agents (source and target). "
                        + "Only 1 agent available."
                );
            }

            A2AAgentDescriptor sourceAgent = agents.get(0);
            A2AAgentDescriptor targetAgent = agents.size() > 1 ? agents.get(1) : agents.get(0);

            String authMethod = sourceAgent.supportedAuthMethods().isEmpty()
                ? "JWT"
                : sourceAgent.supportedAuthMethods().get(0);

            Map<String, String> agentEndpoints = new HashMap<>();
            for (A2AAgentDescriptor agent : agents) {
                agentEndpoints.put(agent.agentId(), agent.endpointUrl());
            }

            A2AHandoffConfiguration handoffConfig = new A2AHandoffConfiguration(
                java.util.UUID.randomUUID().toString(),
                sourceAgent.agentId(),
                targetAgent.agentId(),
                authMethod,
                DEFAULT_TOKEN_EXPIRY_SECONDS,
                false,
                agentEndpoints,
                java.time.Instant.now()
            );

            return WizardStepResult.success(stepId(), handoffConfig);
        } catch (Exception e) {
            return WizardStepResult.failure(
                stepId(),
                "Failed to configure handoff protocol: " + e.getMessage()
            );
        }
    }

    @Override
    public List<String> validatePrerequisites(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");

        List<String> errors = new ArrayList<>();

        if (!session.has(AGENTS_KEY)) {
            errors.add("No discovered agents. Run discovery step first.");
        }

        return errors;
    }

    @Override
    public String description() {
        return "Configure secure handoff of work items between agents using JWT-signed tokens";
    }
}
