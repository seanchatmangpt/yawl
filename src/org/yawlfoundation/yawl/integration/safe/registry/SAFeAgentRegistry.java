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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.safe.registry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.yawlfoundation.yawl.integration.a2a.AgentInfo;
import org.yawlfoundation.yawl.integration.safe.agent.SAFeAgentRole;
import org.yawlfoundation.yawl.integration.safe.agent.SAFeAgentCard;
import org.yawlfoundation.yawl.integration.safe.agent.AgentCapability;

/**
 * Registry for all SAFe role agents participating in agile ceremonies and planning.
 *
 * <p>Maintains a dynamic registry of agents registered for SAFe roles (Product Owner,
 * Scrum Master, Team, Architect, etc.) and provides capability matching, agent lookup,
 * and status synchronization across ceremonies.
 *
 * <p>Features:
 * <ul>
 *   <li>Register agents with specific SAFe roles and capabilities</li>
 *   <li>Query agents by role, capability, or status</li>
 *   <li>Capability matching for task assignment</li>
 *   <li>Status tracking and health monitoring</li>
 *   <li>Multi-ceremony coordination</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * SAFeAgentRegistry registry = new SAFeAgentRegistry();
 *
 * // Register a Product Owner agent
 * SAFeAgentCard poCard = SAFeAgentCard.builder()
 *     .agentId("po-001")
 *     .name("Product Owner Bot")
 *     .role(SAFeAgentRole.PRODUCT_OWNER)
 *     .capabilities(List.of(
 *         AgentCapability.STORY_REFINEMENT,
 *         AgentCapability.PRIORITY_MANAGEMENT,
 *         AgentCapability.STAKEHOLDER_COMMUNICATION
 *     ))
 *     .host("localhost")
 *     .port(8080)
 *     .build();
 *
 * registry.registerAgent(poCard);
 *
 * // Find agents with specific capability
 * List<SAFeAgentCard> refinementAgents = registry.findByCapability(
 *     AgentCapability.STORY_REFINEMENT
 * );
 *
 * // Get all Product Owners
 * List<SAFeAgentCard> productOwners = registry.getAgentsByRole(SAFeAgentRole.PRODUCT_OWNER);
 * }</pre>
 *
 * @since YAWL 6.0
 */
public class SAFeAgentRegistry {

    private final Map<String, SAFeAgentCard> agentById = new ConcurrentHashMap<>();
    private final Map<SAFeAgentRole, List<String>> agentsByRole = new ConcurrentHashMap<>();
    private final Map<AgentCapability, List<String>> agentsByCapability = new ConcurrentHashMap<>();
    private final Map<String, AgentStatus> agentStatusMap = new ConcurrentHashMap<>();

    /**
     * Status of a registered agent.
     */
    public record AgentStatus(
        String agentId,
        AgentState state,
        Instant lastHeartbeat,
        int successCount,
        int failureCount,
        String lastError
    ) {
        /**
         * Returns true if agent is healthy (AVAILABLE or DEGRADED).
         */
        public boolean isHealthy() {
            return state == AgentState.AVAILABLE || state == AgentState.DEGRADED;
        }

        /**
         * Returns success rate (0.0 to 1.0).
         */
        public double getSuccessRate() {
            int total = successCount + failureCount;
            return total > 0 ? (double) successCount / total : 0.0;
        }
    }

    /**
     * Agent operational state.
     */
    public enum AgentState {
        /**
         * Agent is online and all capabilities available.
         */
        AVAILABLE,

        /**
         * Agent is online but some capabilities degraded.
         */
        DEGRADED,

        /**
         * Agent is offline or unreachable.
         */
        OFFLINE,

        /**
         * Agent is authenticating (initial connection).
         */
        AUTHENTICATING
    }

    /**
     * Registers a new SAFe agent in the registry.
     *
     * @param agentCard the agent card with role, capabilities, and endpoint info
     * @throws IllegalArgumentException if agent ID already registered
     * @throws NullPointerException if agentCard is null
     */
    public void registerAgent(SAFeAgentCard agentCard) {
        Objects.requireNonNull(agentCard, "agentCard cannot be null");

        String agentId = agentCard.agentId();
        if (agentById.containsKey(agentId)) {
            throw new IllegalArgumentException("Agent already registered: " + agentId);
        }

        agentById.put(agentId, agentCard);

        // Index by role
        agentsByRole.computeIfAbsent(agentCard.role(), k -> new ArrayList<>())
            .add(agentId);

        // Index by capabilities
        for (AgentCapability capability : agentCard.capabilities()) {
            agentsByCapability.computeIfAbsent(capability, k -> new ArrayList<>())
                .add(agentId);
        }

        // Initialize status
        agentStatusMap.put(agentId, new AgentStatus(
            agentId,
            AgentState.AUTHENTICATING,
            Instant.now(),
            0,
            0,
            null
        ));
    }

    /**
     * Unregisters an agent from the registry.
     *
     * @param agentId the agent to unregister
     * @return true if agent was found and removed, false otherwise
     */
    public boolean unregisterAgent(String agentId) {
        SAFeAgentCard card = agentById.remove(agentId);
        if (card == null) {
            return false;
        }

        // Remove from role index
        agentsByRole.computeIfPresent(card.role(), (k, v) -> {
            v.remove(agentId);
            return v.isEmpty() ? null : v;
        });

        // Remove from capability index
        for (AgentCapability capability : card.capabilities()) {
            agentsByCapability.computeIfPresent(capability, (k, v) -> {
                v.remove(agentId);
                return v.isEmpty() ? null : v;
            });
        }

        agentStatusMap.remove(agentId);
        return true;
    }

    /**
     * Gets a registered agent by ID.
     *
     * @param agentId the agent identifier
     * @return the agent card, or empty if not found
     */
    public Optional<SAFeAgentCard> getAgent(String agentId) {
        return Optional.ofNullable(agentById.get(agentId));
    }

    /**
     * Gets all agents with a specific role.
     *
     * @param role the SAFe role
     * @return list of agents with that role (empty if none)
     */
    public List<SAFeAgentCard> getAgentsByRole(SAFeAgentRole role) {
        return agentsByRole.getOrDefault(role, Collections.emptyList())
            .stream()
            .map(agentById::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Finds all agents with a specific capability.
     *
     * @param capability the agent capability
     * @return list of agents with that capability (empty if none)
     */
    public List<SAFeAgentCard> findByCapability(AgentCapability capability) {
        return agentsByCapability.getOrDefault(capability, Collections.emptyList())
            .stream()
            .map(agentById::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Finds agents that can fulfill multiple capabilities.
     *
     * @param requiredCapabilities the capabilities needed
     * @return agents that have all required capabilities
     */
    public List<SAFeAgentCard> findByCapabilities(
        Collection<AgentCapability> requiredCapabilities) {
        if (requiredCapabilities.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> requiredSet = new HashSet<>(requiredCapabilities);
        return agentById.values().stream()
            .filter(card -> card.capabilities().containsAll(requiredSet))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Finds agents available in a specific ceremony.
     *
     * @param ceremonyType the ceremony type (e.g., "SPRINT_PLANNING", "STANDUP")
     * @return agents that participate in this ceremony
     */
    public List<SAFeAgentCard> findForCeremony(String ceremonyType) {
        return agentById.values().stream()
            .filter(card -> card.ceremonies().contains(ceremonyType))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Selects a single agent from available candidates based on capability.
     * Prefers agents with higher success rates and recent heartbeats.
     *
     * @param requiredCapabilities the required capabilities
     * @return an agent that can fulfill the requirements, or empty if none available
     */
    public Optional<SAFeAgentCard> selectAgent(Collection<AgentCapability> requiredCapabilities) {
        return findByCapabilities(requiredCapabilities).stream()
            .filter(card -> isAgentHealthy(card.agentId()))
            .max(Comparator.comparingDouble(card ->
                getAgentStatus(card.agentId())
                    .map(AgentStatus::getSuccessRate)
                    .orElse(0.0)
            ));
    }

    /**
     * Gets the current status of an agent.
     *
     * @param agentId the agent identifier
     * @return the agent status, or empty if agent not registered
     */
    public Optional<AgentStatus> getAgentStatus(String agentId) {
        return Optional.ofNullable(agentStatusMap.get(agentId));
    }

    /**
     * Updates the agent status after a successful operation.
     *
     * @param agentId the agent identifier
     */
    public void recordSuccess(String agentId) {
        agentStatusMap.compute(agentId, (k, status) -> {
            if (status == null) {
                return null;
            }
            return new AgentStatus(
                status.agentId(),
                AgentState.AVAILABLE,
                Instant.now(),
                status.successCount() + 1,
                status.failureCount(),
                null
            );
        });
    }

    /**
     * Updates the agent status after a failed operation.
     *
     * @param agentId the agent identifier
     * @param error the error message
     */
    public void recordFailure(String agentId, String error) {
        agentStatusMap.compute(agentId, (k, status) -> {
            if (status == null) {
                return null;
            }
            return new AgentStatus(
                status.agentId(),
                determineState(status),
                Instant.now(),
                status.successCount(),
                status.failureCount() + 1,
                error
            );
        });
    }

    /**
     * Updates agent heartbeat timestamp.
     *
     * @param agentId the agent identifier
     */
    public void recordHeartbeat(String agentId) {
        agentStatusMap.compute(agentId, (k, status) -> {
            if (status == null) {
                return null;
            }
            return new AgentStatus(
                status.agentId(),
                AgentState.AVAILABLE,
                Instant.now(),
                status.successCount(),
                status.failureCount(),
                null
            );
        });
    }

    /**
     * Checks if an agent is currently healthy (can receive work).
     *
     * @param agentId the agent identifier
     * @return true if agent is AVAILABLE or DEGRADED, false otherwise
     */
    public boolean isAgentHealthy(String agentId) {
        return getAgentStatus(agentId)
            .map(AgentStatus::isHealthy)
            .orElse(false);
    }

    /**
     * Marks an agent as offline (network failure, timeout, etc.).
     *
     * @param agentId the agent identifier
     * @param reason the reason for going offline
     */
    public void markOffline(String agentId, String reason) {
        agentStatusMap.compute(agentId, (k, status) -> {
            if (status == null) {
                return null;
            }
            return new AgentStatus(
                status.agentId(),
                AgentState.OFFLINE,
                status.lastHeartbeat(),
                status.successCount(),
                status.failureCount(),
                reason
            );
        });
    }

    /**
     * Gets all registered agents.
     *
     * @return immutable list of all agent cards
     */
    public List<SAFeAgentCard> getAllAgents() {
        return Collections.unmodifiableList(
            new ArrayList<>(agentById.values())
        );
    }

    /**
     * Gets all agents currently in AVAILABLE state.
     *
     * @return list of available agents
     */
    public List<SAFeAgentCard> getAvailableAgents() {
        return getAllAgents().stream()
            .filter(card -> isAgentHealthy(card.agentId()))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Gets agents by state.
     *
     * @param state the agent state to filter by
     * @return agents in the specified state
     */
    public List<SAFeAgentCard> getAgentsByState(AgentState state) {
        return getAllAgents().stream()
            .filter(card -> getAgentStatus(card.agentId())
                .map(status -> status.state() == state)
                .orElse(false))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Clears all registered agents and status.
     */
    public void clear() {
        agentById.clear();
        agentsByRole.clear();
        agentsByCapability.clear();
        agentStatusMap.clear();
    }

    /**
     * Gets count of registered agents.
     *
     * @return number of registered agents
     */
    public int size() {
        return agentById.size();
    }

    // Private helper to determine state after failure
    private AgentState determineState(AgentStatus status) {
        double failureRate = 1.0 - status.getSuccessRate();
        if (failureRate > 0.5) {
            return AgentState.DEGRADED;
        }
        return status.state();
    }
}
