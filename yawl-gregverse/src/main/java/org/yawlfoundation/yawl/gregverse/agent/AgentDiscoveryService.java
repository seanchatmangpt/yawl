package org.yawlfoundation.yawl.gregverse.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for discovering Greg-Verse agents.
 *
 * <p>Provides agent discovery capabilities by skill and expertise
 * with future A2A protocol integration.</p>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
@Component
public class AgentDiscoveryService {

    private final AgentDirectory directory;

    public AgentDiscoveryService(AgentDirectory directory) {
        this.directory = directory;
    }

    /**
     * Registers an agent in the discovery service.
     *
     * @param agent the agent to register
     */
    public void registerAgent(GregVerseAgent agent) {
        // Agent is already registered in the directory
        System.out.println("Agent registered for discovery: " + agent.getAgentId());
    }

    /**
     * Discovers agents by skill.
     *
     * @param skillId the skill to search for
     * @return list of agents that provide the skill
     */
    public List<GregVerseAgent> discoverBySkill(String skillId) {
        return directory.getAllAgents().values().stream()
            .filter(agent -> agent.getSpecializedSkills().contains(skillId))
            .collect(Collectors.toList());
    }

    /**
     * Discovers agents by expertise area.
     *
     * @param expertise the expertise area to search for
     * @return list of agents with matching expertise
     */
    public List<GregVerseAgent> discoverByExpertise(String expertise) {
        return directory.getAllAgents().values().stream()
            .filter(agent -> agent.getExpertise().contains(expertise))
            .collect(Collectors.toList());
    }

    /**
     * Gets all available agents.
     *
     * @return list of all agents
     */
    public List<GregVerseAgent> getAllAgents() {
        return directory.getAllAgents().values().stream()
            .collect(Collectors.toList());
    }
}