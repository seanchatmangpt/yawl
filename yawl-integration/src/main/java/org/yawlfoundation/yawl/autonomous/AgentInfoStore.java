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

import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplace;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceListing;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.AgentMarketplaceSpec;
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
 * <p>When a {@link AgentMarketplace} is wired via {@link #withMarketplace(AgentMarketplace)},
 * agents are automatically published to the marketplace on {@link #registerAgent} and
 * unpublished on {@link #unregisterAgent}. This closes the discovery loop: any agent
 * that calls {@code registerAgent} at startup immediately becomes visible to marketplace
 * queries without additional configuration.</p>
 *
 * @since YAWL 6.0
 */
public class AgentInfoStore {

    private final Map<String, AgentInfo> agentsById = new ConcurrentHashMap<>();
    private final Map<String, List<AgentInfo>> capabilityCache = new ConcurrentHashMap<>();

    /** Optional marketplace for auto-publishing agent listings on register/unregister. */
    private volatile AgentMarketplace marketplace;

    /**
     * Wires an {@link AgentMarketplace} for automatic agent publication.
     *
     * <p>After calling this method, every subsequent {@link #registerAgent} call
     * will also publish a marketplace listing, and every {@link #unregisterAgent}
     * call will unpublish it. Already-registered agents are not retroactively published;
     * call this method before registering agents to ensure all are visible.</p>
     *
     * @param marketplace the marketplace to publish to (must not be null)
     * @return this store, for fluent chaining
     */
    public AgentInfoStore withMarketplace(AgentMarketplace marketplace) {
        this.marketplace = java.util.Objects.requireNonNull(marketplace, "marketplace must not be null");
        return this;
    }

    /**
     * Registers an agent in the store.
     *
     * <p>If a marketplace is wired via {@link #withMarketplace}, the agent is also
     * automatically published with a minimal {@link AgentMarketplaceSpec} derived
     * from the agent's name and declared capabilities.</p>
     *
     * @param agentInfo the agent information to store
     */
    public void registerAgent(AgentInfo agentInfo) {
        if (agentInfo == null || agentInfo.getId() == null) {
            throw new IllegalArgumentException("Agent info and ID are required");
        }
        agentsById.put(agentInfo.getId(), agentInfo);
        capabilityCache.clear();

        if (marketplace != null) {
            AgentCapability capability = buildCapability(agentInfo);
            AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0", capability).build();
            marketplace.publish(AgentMarketplaceListing.publishNow(agentInfo, spec));
        }
    }

    /**
     * Unregisters an agent from the store.
     *
     * <p>If a marketplace is wired, the agent's listing is also unpublished,
     * removing it from all future marketplace queries.</p>
     *
     * @param agentId the ID of the agent to remove
     */
    public void unregisterAgent(String agentId) {
        if (agentId != null) {
            agentsById.remove(agentId);
            capabilityCache.clear();

            if (marketplace != null) {
                marketplace.unpublish(agentId);
            }
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

    /**
     * Derives a minimal {@link AgentCapability} from an {@link AgentInfo} for
     * marketplace publication. Uses the agent's declared capability strings as the
     * description, falling back to the agent's name if none are declared.
     */
    private AgentCapability buildCapability(AgentInfo info) {
        String description = (info.getCapabilities() == null || info.getCapabilities().isEmpty())
                ? info.getName()
                : String.join(", ", info.getCapabilities());
        return new AgentCapability(info.getName(), description);
    }
}
