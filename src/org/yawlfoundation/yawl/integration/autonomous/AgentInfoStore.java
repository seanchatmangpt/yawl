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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Store for agent information supporting capability-based lookup.
 *
 * <p>Provides an in-memory store for agent information with efficient
 * lookup by capability. Used by handoff services to find substitute agents.</p>
 *
 * @since YAWL 6.0
 */
public class AgentInfoStore {

    private final Map<String, AgentInfo> agentsById = new ConcurrentHashMap<>();
    private final Map<String, List<AgentInfo>> capabilityCache = new ConcurrentHashMap<>();

    /**
     * Registers an agent in the store.
     *
     * @param agentInfo the agent information to store
     */
    public void registerAgent(AgentInfo agentInfo) {
        if (agentInfo == null || agentInfo.getId() == null) {
            throw new IllegalArgumentException("Agent info and ID are required");
        }
        agentsById.put(agentInfo.getId(), agentInfo);
        // Invalidate capability cache
        capabilityCache.clear();
    }

    /**
     * Unregisters an agent from the store.
     *
     * @param agentId the ID of the agent to remove
     */
    public void unregisterAgent(String agentId) {
        if (agentId != null) {
            agentsById.remove(agentId);
            capabilityCache.clear();
        }
    }

    /**
     * Gets an agent by its ID.
     *
     * @param agentId the agent ID to look up
     * @return the agent info, or null if not found
     */
    public AgentInfo getAgent(String agentId) {
        return agentsById.get(agentId);
    }

    /**
     * Gets agents that have a specific capability.
     *
     * @param capability the capability to search for
     * @return list of agents with the capability
     */
    public List<AgentInfo> getAgentsByCapability(String capability) {
        if (capability == null || capability.isEmpty()) {
            return List.of();
        }
        
        return capabilityCache.computeIfAbsent(capability, cap ->
                agentsById.values().stream()
                        .filter(agent -> agent.getCapabilities() != null &&
                                agent.getCapabilities().contains(cap))
                        .collect(Collectors.toList())
        );
    }

    /**
     * Gets all registered agents.
     *
     * @return list of all agents
     */
    public List<AgentInfo> getAllAgents() {
        return List.copyOf(agentsById.values());
    }

    /**
     * Clears all agents from the store.
     */
    public void clear() {
        agentsById.clear();
        capabilityCache.clear();
    }

    /**
     * Gets the number of registered agents.
     *
     * @return the count of agents
     */
    public int size() {
        return agentsById.size();
    }
}
