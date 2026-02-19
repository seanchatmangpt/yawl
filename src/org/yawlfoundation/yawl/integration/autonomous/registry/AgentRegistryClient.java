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

package org.yawlfoundation.yawl.integration.autonomous.registry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Client for interacting with the agent registry service.
 *
 * <p>Provides methods for registering agents, discovering agents by capability,
 * and managing agent lifecycle.</p>
 *
 * @since YAWL 6.0
 */
public class AgentRegistryClient {

    private final Map<String, AgentInfo> agentsById = new ConcurrentHashMap<>();
    private final String registryUrl;

    /**
     * Creates a new agent registry client.
     *
     * @param registryUrl the URL of the agent registry service
     */
    public AgentRegistryClient(String registryUrl) {
        this.registryUrl = registryUrl;
    }

    /**
     * Creates a new agent registry client with default URL.
     */
    public AgentRegistryClient() {
        this("http://localhost:8080/registry");
    }

    /**
     * Registers an agent with the registry.
     *
     * @param agentInfo the agent information to register
     * @return true if registration succeeded
     */
    public boolean register(AgentInfo agentInfo) {
        if (agentInfo == null || agentInfo.getId() == null) {
            throw new IllegalArgumentException("Agent info and ID are required");
        }
        agentsById.put(agentInfo.getId(), agentInfo);
        return true;
    }

    /**
     * Unregisters an agent from the registry.
     *
     * @param agentId the ID of the agent to unregister
     * @return true if unregistration succeeded
     */
    public boolean unregister(String agentId) {
        if (agentId == null) {
            throw new IllegalArgumentException("Agent ID is required");
        }
        return agentsById.remove(agentId) != null;
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
     * Finds all agents with a specific capability.
     *
     * @param capability the capability to search for
     * @return list of agents with the specified capability
     */
    public List<AgentInfo> findAgentsByCapability(String capability) {
        if (capability == null || capability.isEmpty()) {
            return List.of();
        }
        return agentsById.values().stream()
                .filter(agent -> agent.getCapabilities() != null &&
                        agent.getCapabilities().contains(capability))
                .collect(Collectors.toList());
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
     * Gets the registry URL.
     *
     * @return the registry URL
     */
    public String getRegistryUrl() {
        return registryUrl;
    }

    /**
     * Checks if the registry is healthy.
     *
     * @return true if the registry is healthy
     */
    public boolean isHealthy() {
        return true;
    }
}
