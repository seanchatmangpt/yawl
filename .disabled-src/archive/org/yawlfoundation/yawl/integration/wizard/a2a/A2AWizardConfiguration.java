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

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable final A2A configuration produced by the wizard.
 *
 * <p>Contains all the information needed to deploy A2A agent integration:
 * selected agents, skill-to-task bindings, and agent endpoint configuration.
 * Produced by the A2A_CONFIG phase and persisted to the deployment manifest.
 *
 * @param configurationId unique identifier for this configuration
 * @param selectedAgents list of agents selected for this workflow
 * @param taskSlotSkillBindings mapping of workflow task → A2A skill
 * @param skillAgentBindings mapping of skill ID → agent that provides it
 * @param primaryAgentUrl URL of the primary agent (usually YAWL engine)
 * @param primaryAgentPort port of the primary agent
 * @param configuredAt timestamp when configuration was created
 *
 * @since YAWL 6.0
 */
public record A2AWizardConfiguration(
    String configurationId,
    List<A2AAgentDescriptor> selectedAgents,
    Map<String, A2ASkillDescriptor> taskSlotSkillBindings,
    Map<String, A2AAgentDescriptor> skillAgentBindings,
    String primaryAgentUrl,
    int primaryAgentPort,
    Instant configuredAt
) {
    /**
     * Compact constructor ensures immutability of mutable fields.
     */
    public A2AWizardConfiguration {
        Objects.requireNonNull(configurationId, "configurationId cannot be null");
        Objects.requireNonNull(selectedAgents, "selectedAgents cannot be null");
        Objects.requireNonNull(taskSlotSkillBindings, "taskSlotSkillBindings cannot be null");
        Objects.requireNonNull(skillAgentBindings, "skillAgentBindings cannot be null");
        Objects.requireNonNull(primaryAgentUrl, "primaryAgentUrl cannot be null");
        Objects.requireNonNull(configuredAt, "configuredAt cannot be null");

        if (primaryAgentPort < 1 || primaryAgentPort > 65535) {
            throw new IllegalArgumentException("primaryAgentPort must be between 1 and 65535");
        }

        selectedAgents = Collections.unmodifiableList(List.copyOf(selectedAgents));
        taskSlotSkillBindings = Collections.unmodifiableMap(Map.copyOf(taskSlotSkillBindings));
        skillAgentBindings = Collections.unmodifiableMap(Map.copyOf(skillAgentBindings));
    }

    /**
     * Factory method: creates configuration from agents and skill bindings.
     *
     * @param agents selected agents
     * @param bindings task slot → skill mappings
     * @return new configuration with inferred agent bindings
     */
    public static A2AWizardConfiguration of(
        List<A2AAgentDescriptor> agents,
        Map<String, A2ASkillDescriptor> bindings
    ) {
        Objects.requireNonNull(agents, "agents cannot be null");
        Objects.requireNonNull(bindings, "bindings cannot be null");

        Map<String, A2AAgentDescriptor> skillAgentBindings = new HashMap<>();
        for (A2ASkillDescriptor skill : bindings.values()) {
            agents.stream()
                .filter(agent -> agent.hasSkill(skill.skillId()))
                .findFirst()
                .ifPresent(agent -> skillAgentBindings.put(skill.skillId(), agent));
        }

        A2AAgentDescriptor primary = agents.isEmpty() ? null : agents.get(0);
        String primaryUrl = primary != null ? primary.agentUrl() : "";
        int primaryPort = primary != null ? primary.port() : 8081;

        return new A2AWizardConfiguration(
            java.util.UUID.randomUUID().toString(),
            agents,
            bindings,
            skillAgentBindings,
            primaryUrl,
            primaryPort,
            Instant.now()
        );
    }

    /**
     * Converts this configuration to a map for serialization/deployment.
     *
     * @return map representation suitable for YAML/JSON serialization
     */
    public Map<String, Object> toConfigMap() {
        Map<String, Object> config = new HashMap<>();
        config.put("configurationId", configurationId);
        config.put("primaryAgent", Map.of(
            "url", primaryAgentUrl,
            "port", primaryAgentPort
        ));
        config.put("selectedAgents", selectedAgents.stream()
            .map(a -> Map.of(
                "agentId", a.agentId(),
                "agentName", a.agentName(),
                "endpoint", a.endpointUrl()
            ))
            .toList()
        );
        config.put("taskBindings", taskSlotSkillBindings.entrySet().stream()
            .map(e -> Map.of(
                "taskSlot", e.getKey(),
                "skillId", e.getValue().skillId(),
                "skillName", e.getValue().displayName()
            ))
            .toList()
        );
        config.put("configuredAt", configuredAt.toString());
        return Collections.unmodifiableMap(config);
    }

    /**
     * Gets the count of configured agents.
     *
     * @return number of selected agents
     */
    public int agentCount() {
        return selectedAgents.size();
    }

    /**
     * Gets the count of task-to-skill bindings.
     *
     * @return number of bindings
     */
    public int bindingCount() {
        return taskSlotSkillBindings.size();
    }
}
