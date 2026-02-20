/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation.GregVerseScenarioRunner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring configuration for Greg-Verse agent management.
 *
 * <p>This configuration class manages the lifecycle and registration of
 * Greg-Verse AI business advisor agents. It provides:</p>
 * <ul>
 *   <li>Agent registry for discovering and accessing agents</li>
 *   <li>Agent factory for creating configured agent instances</li>
 *   <li>Agent coordination services</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Agent behavior is controlled via {@code application.yml}:</p>
 * <pre>{@code
 * gregverse:
 *   agents:
 *     enabled: true
 *     api-key: ${ZAI_API_KEY:}
 *     default-timeout: 30000
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "gregverse.agents", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GregVerseConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(GregVerseConfiguration.class);

    @Value("${gregverse.agents.api-key:#{systemEnvironment['ZAI_API_KEY']}}")
    private String apiKey;

    @Value("${gregverse.agents.default-timeout:30000}")
    private long defaultTimeoutMs;

    /**
     * Creates the agent registry that holds all available Greg-Verse agents.
     *
     * @return the agent registry
     */
    @Bean
    public GregVerseAgentRegistry gregVerseAgentRegistry() {
        LOGGER.info("Initializing Greg-Verse Agent Registry");
        GregVerseAgentRegistry registry = new GregVerseAgentRegistry();

        if (apiKey != null && !apiKey.isBlank()) {
            LOGGER.info("API key configured, agents will be available for queries");
        } else {
            LOGGER.warn("No API key configured. Set ZAI_API_KEY environment variable or gregverse.agents.api-key property.");
        }

        return registry;
    }

    /**
     * Creates the scenario runner for executing Greg-Verse scenarios.
     *
     * @param registry the agent registry
     * @return the scenario runner
     */
    @Bean
    public GregVerseScenarioRunner gregVerseScenarioRunner(GregVerseAgentRegistry registry) {
        LOGGER.info("Initializing Greg-Verse Scenario Runner");
        return new GregVerseScenarioRunner(registry, defaultTimeoutMs);
    }

    /**
     * Registry for Greg-Verse agents providing discovery and access.
     */
    public static class GregVerseAgentRegistry {

        private final Map<String, GregVerseAgent> agents = new ConcurrentHashMap<>();
        private final Map<String, AgentMetadata> metadata = new ConcurrentHashMap<>();

        /**
         * Registers an agent with the registry.
         *
         * @param agent the agent to register
         */
        public void register(GregVerseAgent agent) {
            String agentId = agent.getAgentId();
            agents.put(agentId, agent);
            metadata.put(agentId, new AgentMetadata(
                agentId,
                agent.getDisplayName(),
                agent.getBio(),
                agent.getSpecializedSkills(),
                agent.getExpertise()
            ));
            LOGGER.debug("Registered agent: {}", agentId);
        }

        /**
         * Retrieves an agent by ID.
         *
         * @param agentId the agent identifier
         * @return the agent, or null if not found
         */
        public GregVerseAgent getAgent(String agentId) {
            return agents.get(agentId);
        }

        /**
         * Returns all registered agents.
         *
         * @return map of agent ID to agent instance
         */
        public Map<String, GregVerseAgent> getAllAgents() {
            return Map.copyOf(agents);
        }

        /**
         * Returns metadata for all agents.
         *
         * @return map of agent ID to metadata
         */
        public Map<String, AgentMetadata> getAllMetadata() {
            return Map.copyOf(metadata);
        }

        /**
         * Returns the number of registered agents.
         *
         * @return agent count
         */
        public int getAgentCount() {
            return agents.size();
        }

        /**
         * Checks if an agent is registered.
         *
         * @param agentId the agent identifier
         * @return true if the agent is registered
         */
        public boolean hasAgent(String agentId) {
            return agents.containsKey(agentId);
        }

        /**
         * Finds agents by skill.
         *
         * @param skillId the skill identifier
         * @return list of agents with the specified skill
         */
        public List<GregVerseAgent> findAgentsBySkill(String skillId) {
            return agents.values().stream()
                .filter(agent -> agent.getSpecializedSkills().contains(skillId))
                .toList();
        }

        /**
         * Finds agents by expertise area.
         *
         * @param expertise the expertise area
         * @return list of agents with the specified expertise
         */
        public List<GregVerseAgent> findAgentsByExpertise(String expertise) {
            String lowerExpertise = expertise.toLowerCase();
            return agents.values().stream()
                .filter(agent -> agent.getExpertise().stream()
                    .anyMatch(e -> e.toLowerCase().contains(lowerExpertise)))
                .toList();
        }

        /**
         * Clears all registered agents.
         */
        public void clear() {
            agents.clear();
            metadata.clear();
        }
    }

    /**
     * Metadata record for a Greg-Verse agent.
     *
     * @param agentId the unique agent identifier
     * @param displayName the human-readable name
     * @param bio the agent's bio/background
     * @param specializedSkills list of skill identifiers
     * @param expertise list of expertise areas
     */
    public record AgentMetadata(
        String agentId,
        String displayName,
        String bio,
        List<String> specializedSkills,
        List<String> expertise
    ) {}
}
