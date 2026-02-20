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

package org.yawlfoundation.yawl.integration.autonomous.registry;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Registry for autonomous agent capabilities with health-tracked discovery.
 *
 * <p>Manages agent registration, capability matching, and heartbeat monitoring.
 * Agents that miss heartbeats are automatically marked unhealthy and excluded
 * from scheduling decisions.</p>
 *
 * <h2>Capability Matching</h2>
 * <p>Uses weighted Jaccard similarity between required and offered capabilities,
 * factoring in proficiency levels.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class AgentCapabilityRegistry {

    private static final Logger LOG = LogManager.getLogger(AgentCapabilityRegistry.class);

    /**
     * Communication protocol for an agent.
     */
    public sealed interface AgentProtocol {
        record A2AProtocol(URI endpoint, String agentCardPath) implements AgentProtocol {}
        record MCPProtocol(URI endpoint, String transportType) implements AgentProtocol {}
        record DirectProtocol(String className) implements AgentProtocol {}
    }

    /**
     * A capability offered by an agent.
     *
     * @param domain      capability domain (e.g., "workflow", "data", "decision")
     * @param action      specific action (e.g., "approve", "transform", "classify")
     * @param proficiency proficiency level from 0.0 (novice) to 1.0 (expert)
     */
    public record Capability(String domain, String action, double proficiency) {
        public Capability {
            Objects.requireNonNull(domain);
            Objects.requireNonNull(action);
            if (proficiency < 0.0 || proficiency > 1.0) {
                throw new IllegalArgumentException("proficiency must be in [0.0, 1.0]");
            }
        }

        /**
         * Returns a composite key for matching.
         */
        public String key() { return domain + ":" + action; }
    }

    /**
     * Descriptor for a registered agent.
     *
     * @param agentId        unique identifier
     * @param name           human-readable name
     * @param capabilities   set of offered capabilities
     * @param protocol       communication protocol
     * @param maxConcurrency maximum concurrent tasks
     * @param registeredAt   when the agent was registered
     * @param lastHeartbeat  timestamp of last heartbeat
     */
    public record AgentDescriptor(
        String agentId,
        String name,
        Set<Capability> capabilities,
        AgentProtocol protocol,
        int maxConcurrency,
        Instant registeredAt,
        Instant lastHeartbeat
    ) {
        public AgentDescriptor {
            Objects.requireNonNull(agentId);
            Objects.requireNonNull(name);
            capabilities = capabilities != null ? Set.copyOf(capabilities) : Set.of();
            Objects.requireNonNull(protocol);
            if (registeredAt == null) registeredAt = Instant.now();
            if (lastHeartbeat == null) lastHeartbeat = Instant.now();
        }

        /**
         * Returns a copy with updated heartbeat.
         */
        public AgentDescriptor withHeartbeat(Instant timestamp) {
            return new AgentDescriptor(agentId, name, capabilities, protocol,
                                       maxConcurrency, registeredAt, timestamp);
        }
    }

    /**
     * Result of a capability match.
     *
     * @param agent              the matched agent
     * @param score              match score (0.0 to 1.0)
     * @param matchedCapabilities list of matched capability keys
     */
    public record CapabilityMatch(
        AgentDescriptor agent,
        double score,
        List<String> matchedCapabilities
    ) {}

    private final Map<String, AgentDescriptor> agents = new ConcurrentHashMap<>();
    private final ReentrantLock registryLock = new ReentrantLock();
    private final Duration heartbeatTimeout;
    private final AtomicBoolean monitorRunning = new AtomicBoolean(false);
    private volatile Thread monitorThread;

    /**
     * Creates a registry with the specified heartbeat timeout.
     *
     * @param heartbeatTimeout duration after which an agent is considered unhealthy
     */
    public AgentCapabilityRegistry(Duration heartbeatTimeout) {
        this.heartbeatTimeout = Objects.requireNonNull(heartbeatTimeout);
    }

    /**
     * Creates a registry with default 30-second heartbeat timeout.
     */
    public AgentCapabilityRegistry() {
        this(Duration.ofSeconds(30));
    }

    /**
     * Registers an agent. Updates existing registration if agent ID already exists.
     *
     * @param agent the agent descriptor
     */
    public void register(AgentDescriptor agent) {
        registryLock.lock();
        try {
            agents.put(agent.agentId(), agent);
            LOG.info("Registered agent: {} ({}) with {} capabilities via {}",
                     agent.agentId(), agent.name(), agent.capabilities().size(),
                     protocolName(agent.protocol()));
        } finally {
            registryLock.unlock();
        }
    }

    /**
     * Deregisters an agent.
     *
     * @param agentId the agent to remove
     */
    public void deregister(String agentId) {
        registryLock.lock();
        try {
            AgentDescriptor removed = agents.remove(agentId);
            if (removed != null) {
                LOG.info("Deregistered agent: {} ({})", removed.agentId(), removed.name());
            }
        } finally {
            registryLock.unlock();
        }
    }

    /**
     * Records a heartbeat for an agent.
     *
     * @param agentId the agent sending the heartbeat
     */
    public void heartbeat(String agentId) {
        agents.computeIfPresent(agentId, (_, existing) ->
            existing.withHeartbeat(Instant.now()));
    }

    /**
     * Finds agents eligible for the given capabilities, sorted by match score.
     *
     * @param required the capabilities required
     * @return sorted list of matches (best first)
     */
    public List<CapabilityMatch> findEligibleAgents(Set<Capability> required) {
        if (required == null || required.isEmpty()) {
            return getHealthyAgents().stream()
                .map(agent -> new CapabilityMatch(agent, 1.0, List.of()))
                .toList();
        }

        return getHealthyAgents().stream()
            .map(agent -> computeMatch(agent, required))
            .filter(match -> match.score() > 0.0)
            .sorted(Comparator.comparingDouble(CapabilityMatch::score).reversed())
            .toList();
    }

    /**
     * Returns only agents with a recent heartbeat.
     */
    public List<AgentDescriptor> getHealthyAgents() {
        Instant cutoff = Instant.now().minus(heartbeatTimeout);
        return agents.values().stream()
            .filter(agent -> agent.lastHeartbeat().isAfter(cutoff))
            .toList();
    }

    /**
     * Returns all registered agents regardless of health.
     */
    public List<AgentDescriptor> getAllAgents() {
        return List.copyOf(agents.values());
    }

    /**
     * Returns the number of registered agents.
     */
    public int size() { return agents.size(); }

    /**
     * Starts the heartbeat monitor on a virtual thread.
     */
    public void startHeartbeatMonitor(Duration checkInterval) {
        if (monitorRunning.compareAndSet(false, true)) {
            monitorThread = Thread.ofVirtual().name("agent-heartbeat-monitor").start(() -> {
                LOG.info("Heartbeat monitor started (interval: {}, timeout: {})",
                         checkInterval, heartbeatTimeout);
                while (monitorRunning.get()) {
                    try {
                        Thread.sleep(checkInterval);
                        evictUnhealthy();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }

    /**
     * Stops the heartbeat monitor.
     */
    public void stopHeartbeatMonitor() {
        monitorRunning.set(false);
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
    }

    private void evictUnhealthy() {
        Instant cutoff = Instant.now().minus(heartbeatTimeout.multipliedBy(3));
        List<String> evicted = agents.entrySet().stream()
            .filter(e -> e.getValue().lastHeartbeat().isBefore(cutoff))
            .map(Map.Entry::getKey)
            .toList();

        for (String agentId : evicted) {
            agents.remove(agentId);
            LOG.warn("Evicted agent {} (no heartbeat for 3x timeout)", agentId);
        }
    }

    private CapabilityMatch computeMatch(AgentDescriptor agent, Set<Capability> required) {
        Map<String, Double> agentCapMap = new java.util.HashMap<>();
        for (Capability cap : agent.capabilities()) {
            agentCapMap.put(cap.key(), cap.proficiency());
        }

        List<String> matched = new java.util.ArrayList<>();
        double totalScore = 0.0;

        for (Capability req : required) {
            Double proficiency = agentCapMap.get(req.key());
            if (proficiency != null) {
                matched.add(req.key());
                totalScore += proficiency * req.proficiency(); // Cross-proficiency weighting
            }
        }

        double score = required.isEmpty() ? 1.0 : totalScore / required.size();
        return new CapabilityMatch(agent, score, List.copyOf(matched));
    }

    private String protocolName(AgentProtocol protocol) {
        return switch (protocol) {
            case AgentProtocol.A2AProtocol _ -> "A2A";
            case AgentProtocol.MCPProtocol _ -> "MCP";
            case AgentProtocol.DirectProtocol _ -> "Direct";
        };
    }
}
