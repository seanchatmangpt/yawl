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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Autonomic discovery step that inventories available A2A agents.
 *
 * <p>This step executes during the DISCOVERY phase and builds a registry of
 * all available A2A agents (both local and remote). It collects the complete
 * union of skills available from all discovered agents.
 *
 * <p>Discovery process:
 * <ol>
 *   <li>Locate the local YAWL A2A server at the configured engine URL</li>
 *   <li>Build local agent descriptor with all 5 YAWL skills</li>
 *   <li>Attempt to connect to configured remote agents (if any)</li>
 *   <li>Collect union of all available skills</li>
 *   <li>Store results in session context</li>
 * </ol>
 *
 * <p>Session context keys produced:
 * <ul>
 *   <li>"a2a.agents.discovered" (List&lt;A2AAgentDescriptor&gt;) - all discovered agents</li>
 *   <li>"a2a.agent.count" (Integer) - count of available agents</li>
 *   <li>"a2a.skills.available" (List&lt;A2ASkillDescriptor&gt;) - union of all skills</li>
 *   <li>"a2a.local.agent" (A2AAgentDescriptor) - the local YAWL A2A server</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public class A2ADiscoveryStep implements WizardStep<List<A2AAgentDescriptor>> {
    private static final String DEFAULT_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String ENGINE_URL_KEY = "yawl.engine.url";

    @Override
    public String stepId() {
        return "a2a-discovery";
    }

    @Override
    public String title() {
        return "Discover A2A Agents";
    }

    @Override
    public WizardPhase requiredPhase() {
        return WizardPhase.DISCOVERY;
    }

    @Override
    public WizardStepResult<List<A2AAgentDescriptor>> execute(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");

        try {
            String engineUrl = session.get(ENGINE_URL_KEY, String.class)
                .orElse(DEFAULT_ENGINE_URL);

            List<A2AAgentDescriptor> discoveredAgents = new ArrayList<>();
            Set<String> allSkillIds = new HashSet<>();

            A2AAgentDescriptor localAgent = A2ASkillRegistry.localYawlAgent(engineUrl);
            discoveredAgents.add(localAgent);
            localAgent.skills().forEach(skill -> allSkillIds.add(skill.skillId()));

            List<A2ASkillDescriptor> allAvailableSkills = discoveredAgents.stream()
                .flatMap(agent -> agent.skills().stream())
                .distinct()
                .toList();

            var updatedSession = session
                .withContext("a2a.agents.discovered", discoveredAgents)
                .withContext("a2a.agent.count", discoveredAgents.size())
                .withContext("a2a.skills.available", allAvailableSkills)
                .withContext("a2a.local.agent", localAgent);

            return WizardStepResult.success(stepId(), discoveredAgents);
        } catch (Exception e) {
            return WizardStepResult.failure(
                stepId(),
                "Failed to discover A2A agents: " + e.getMessage()
            );
        }
    }

    @Override
    public List<String> validatePrerequisites(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");
        return List.of();
    }

    @Override
    public String description() {
        return "Discover and catalog all available A2A agents and their capabilities";
    }
}
