package org.yawlfoundation.yawl.gregverse.agent;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry for Greg-Verse agents.
 *
 * <p>Provides agent lookup by ID and supports concurrent access
 * for agent discovery and coordination.</p>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
@Component
public class AgentDirectory {

    private final Map<String, GregVerseAgent> agents = new ConcurrentHashMap<>();

    /**
     * Registers an agent in the directory.
     *
     * @param agent the agent to register
     */
    public void register(GregVerseAgent agent) {
        agents.put(agent.getAgentId(), agent);
        System.out.println("Registered agent: " + agent.getAgentId());
    }

    /**
     * Finds an agent by ID.
     *
     * @param agentId the agent ID
     * @return the agent if found, null otherwise
     */
    public GregVerseAgent findById(String agentId) {
        return agents.get(agentId);
    }

    /**
     * Gets all registered agents.
     *
     * @return map of all agents
     */
    public Map<String, GregVerseAgent> getAllAgents() {
        return new ConcurrentHashMap<>(agents);
    }

    /**
     * Unregisters an agent.
     *
     * @param agentId the agent ID to unregister
     */
    public void unregister(String agentId) {
        GregVerseAgent removed = agents.remove(agentId);
        if (removed != null) {
            System.out.println("Unregistered agent: " + agentId);
        }
    }

    /**
     * Checks if an agent is registered.
     *
     * @param agentId the agent ID
     * @return true if agent is registered
     */
    public boolean isRegistered(String agentId) {
        return agents.containsKey(agentId);
    }
}