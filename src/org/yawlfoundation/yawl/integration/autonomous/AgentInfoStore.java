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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory store for agent information.
 *
 * <p>Provides caching and lookup of agent capabilities and endpoints.
 * Used by the handoff service to locate capable substitute agents
 * for work item transfers.</p>
 *
 * @since YAWL 6.0
 */
public class AgentInfoStore {

    private final Map<String, AgentInfo> agentById = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> agentsByCapability = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastHeartbeat = new ConcurrentHashMap<>();

    private final long heartbeatTimeoutSeconds = 300; // 5 minutes
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();

    /**
     * Registers or updates an agent's information.
     *
     * @param agentInfo the agent information to store
     */
    public void registerAgent(AgentInfo agentInfo) {
        String agentId = agentInfo.getId();

        // Store or update agent info
        agentById.put(agentId, agentInfo);

        // Update capability index - use the single capability from AgentInfo
        String capabilityName = agentInfo.getCapability().domainName();
        agentsByCapability.computeIfAbsent(capabilityName, k -> ConcurrentHashMap.newKeySet())
                        .add(agentId);

        // Update heartbeat
        lastHeartbeat.put(agentId, Instant.now());

        System.out.println("[AgentInfoStore] Registered agent: " + agentId);
    }

    /**
     * Removes an agent from the store.
     *
     * @param agentId the ID of the agent to remove
     * @return true if the agent was removed, false if not found
     */
    public boolean unregisterAgent(String agentId) {
        AgentInfo agent = agentById.remove(agentId);
        if (agent != null) {
            // Remove from capability indexes - use single capability
            String capabilityName = agent.getCapability().domainName();
            Set<String> agents = agentsByCapability.get(capabilityName);
            if (agents != null) {
                agents.remove(agentId);
                if (agents.isEmpty()) {
                    agentsByCapability.remove(capabilityName);
                }
            }

            // Remove heartbeat
            lastHeartbeat.remove(agentId);

            System.out.println("[AgentInfoStore] Unregistered agent: " + agentId);
            return true;
        }
        return false;
    }

    /**
     * Gets an agent by ID.
     *
     * @param agentId the agent ID
     * @return the agent info, or null if not found
     */
    public AgentInfo getAgent(String agentId) {
        // Check if agent is still active (not timed out)
        Instant heartbeat = lastHeartbeat.get(agentId);
        if (heartbeat != null && Instant.now().isAfter(heartbeat.plusSeconds(heartbeatTimeoutSeconds))) {
            unregisterAgent(agentId);
            return null;
        }
        return agentById.get(agentId);
    }

    /**
     * Gets all agents that have a specific capability.
     *
     * @param capability the capability name
     * @return list of capable agents
     */
    public List<AgentInfo> getAgentsByCapability(String capability) {
        Set<String> agentIds = agentsByCapability.get(capability);
        if (agentIds == null || agentIds.isEmpty()) {
            return Collections.emptyList();
        }

        return agentIds.stream()
            .map(this::getAgent)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * Gets all registered agents.
     *
     * @return list of all agents
     */
    public List<AgentInfo> getAllAgents() {
        return new ArrayList<>(agentById.values());
    }

    /**
     * Updates an agent's heartbeat timestamp.
     *
     * @param agentId the agent ID
     * @return true if successful, false if agent not found
     */
    public boolean updateHeartbeat(String agentId) {
        if (agentById.containsKey(agentId)) {
            lastHeartbeat.put(agentId, Instant.now());
            return true;
        }
        return false;
    }

    /**
     * Gets agents that have not sent a heartbeat recently.
     *
     * @return list of stale agents
     */
    public List<String> getStaleAgents() {
        Instant cutoff = Instant.now().minusSeconds(heartbeatTimeoutSeconds);
        return lastHeartbeat.entrySet().stream()
            .filter(entry -> entry.getValue().isBefore(cutoff))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * Removes all stale agents.
     *
     * @return number of agents removed
     */
    public int cleanupStaleAgents() {
        List<String> stale = getStaleAgents();
        for (String agentId : stale) {
            unregisterAgent(agentId);
        }
        return stale.size();
    }

    /**
     * Gets store statistics.
     *
     * @return map of statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_agents", agentById.size());
        stats.put("capabilities_count", agentsByCapability.size());
        stats.put("stale_agents", getStaleAgents().size());
        stats.put("last_heartbeat", lastHeartbeat.values().stream()
            .max(Comparator.naturalOrder())
            .orElse(null));

        // Add custom metadata
        stats.putAll(metadata);

        return stats;
    }

    /**
     * Sets metadata for the store.
     *
     * @param key the metadata key
     * @param value the metadata value
     */
    public void setMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    /**
     * Gets metadata value.
     *
     * @param key the metadata key
     * @return the metadata value
     */
    public Object getMetadata(String key) {
        return metadata.get(key);
    }
}