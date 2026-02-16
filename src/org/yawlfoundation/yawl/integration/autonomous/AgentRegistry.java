package org.yawlfoundation.yawl.integration.autonomous;

import org.yawlfoundation.yawl.integration.a2a.A2AException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Agent-to-Agent (A2A) Registry for YAWL autonomous workflow coordination.
 * Implements agent discovery, heartbeat monitoring, and capability matching.
 *
 * Real implementation with no stubs or mocks.
 *
 * @author YAWL Integration Team
 * @version 5.2
 */
public class AgentRegistry {

    private final Map<String, AgentEntry> agents = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatMonitor;
    private final int heartbeatIntervalSeconds;
    private final int agentTimeoutSeconds;

    private static final int DEFAULT_HEARTBEAT_INTERVAL = 30;
    private static final int DEFAULT_AGENT_TIMEOUT = 90;

    /**
     * Agent entry with metadata and heartbeat tracking.
     */
    public static class AgentEntry {
        private final String agentId;
        private final String endpoint;
        private final List<String> capabilities;
        private final Map<String, Object> metadata;
        private volatile Instant lastHeartbeat;
        private volatile AgentStatus status;

        public AgentEntry(String agentId, String endpoint, List<String> capabilities, Map<String, Object> metadata) {
            if (agentId == null || agentId.trim().isEmpty()) {
                throw new IllegalArgumentException("Agent ID cannot be null or empty");
            }
            if (endpoint == null || endpoint.trim().isEmpty()) {
                throw new IllegalArgumentException("Agent endpoint cannot be null or empty");
            }

            this.agentId = agentId;
            this.endpoint = endpoint;
            this.capabilities = capabilities != null ? List.copyOf(capabilities) : List.of();
            this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
            this.lastHeartbeat = Instant.now();
            this.status = AgentStatus.ACTIVE;
        }

        public String getAgentId() { return agentId; }
        public String getEndpoint() { return endpoint; }
        public List<String> getCapabilities() { return capabilities; }
        public Map<String, Object> getMetadata() { return metadata; }
        public Instant getLastHeartbeat() { return lastHeartbeat; }
        public AgentStatus getStatus() { return status; }

        void updateHeartbeat() {
            this.lastHeartbeat = Instant.now();
            this.status = AgentStatus.ACTIVE;
        }

        void markInactive() {
            this.status = AgentStatus.INACTIVE;
        }

        boolean hasCapability(String capability) {
            return capabilities.contains(capability);
        }
    }

    public enum AgentStatus {
        ACTIVE,
        INACTIVE,
        FAILED
    }

    /**
     * Creates a new agent registry with default heartbeat settings.
     */
    public AgentRegistry() {
        this(DEFAULT_HEARTBEAT_INTERVAL, DEFAULT_AGENT_TIMEOUT);
    }

    /**
     * Creates a new agent registry with custom heartbeat settings.
     *
     * @param heartbeatIntervalSeconds Interval for heartbeat checks
     * @param agentTimeoutSeconds Timeout before marking agent as inactive
     */
    public AgentRegistry(int heartbeatIntervalSeconds, int agentTimeoutSeconds) {
        if (heartbeatIntervalSeconds <= 0) {
            throw new IllegalArgumentException("Heartbeat interval must be positive");
        }
        if (agentTimeoutSeconds <= heartbeatIntervalSeconds) {
            throw new IllegalArgumentException("Agent timeout must be greater than heartbeat interval");
        }

        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.agentTimeoutSeconds = agentTimeoutSeconds;
        this.heartbeatMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Thread.ofVirtual()
                .name("AgentRegistry-HeartbeatMonitor")
                .unstarted(r);
            return t;
        });

        startHeartbeatMonitor();
    }

    /**
     * Registers a new agent in the registry.
     *
     * @param agentId Unique agent identifier
     * @param endpoint Agent communication endpoint
     * @param capabilities List of agent capabilities
     * @param metadata Additional agent metadata
     * @throws A2AException if registration fails
     */
    public void registerAgent(String agentId, String endpoint, List<String> capabilities, Map<String, Object> metadata) throws A2AException {
        try {
            AgentEntry entry = new AgentEntry(agentId, endpoint, capabilities, metadata);
            agents.put(agentId, entry);
            System.out.println("Agent registered: " + agentId + " at " + endpoint);
        } catch (IllegalArgumentException e) {
            throw new A2AException("Failed to register agent: " + e.getMessage(), e);
        }
    }

    /**
     * Unregisters an agent from the registry.
     *
     * @param agentId Agent identifier to unregister
     * @return true if agent was found and removed
     */
    public boolean unregisterAgent(String agentId) {
        AgentEntry removed = agents.remove(agentId);
        if (removed != null) {
            System.out.println("Agent unregistered: " + agentId);
            return true;
        }
        return false;
    }

    /**
     * Updates the heartbeat for an agent.
     *
     * @param agentId Agent identifier
     * @throws A2AException if agent is not found
     */
    public void updateHeartbeat(String agentId) throws A2AException {
        AgentEntry entry = agents.get(agentId);
        if (entry == null) {
            throw new A2AException("Agent not found: " + agentId);
        }
        entry.updateHeartbeat();
    }

    /**
     * Retrieves an agent by ID.
     *
     * @param agentId Agent identifier
     * @return Optional containing the agent entry if found
     */
    public Optional<AgentEntry> getAgent(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    /**
     * Finds all agents with a specific capability.
     *
     * @param capability Required capability
     * @return List of agents with the capability
     */
    public List<AgentEntry> findAgentsByCapability(String capability) {
        if (capability == null || capability.trim().isEmpty()) {
            throw new IllegalArgumentException("Capability cannot be null or empty");
        }

        return agents.values().stream()
                .filter(agent -> agent.getStatus() == AgentStatus.ACTIVE)
                .filter(agent -> agent.hasCapability(capability))
                .collect(Collectors.toList());
    }

    /**
     * Returns all active agents in the registry.
     *
     * @return List of active agents
     */
    public List<AgentEntry> getActiveAgents() {
        return agents.values().stream()
                .filter(agent -> agent.getStatus() == AgentStatus.ACTIVE)
                .collect(Collectors.toList());
    }

    /**
     * Returns all agents regardless of status.
     *
     * @return List of all agents
     */
    public List<AgentEntry> getAllAgents() {
        return List.copyOf(agents.values());
    }

    /**
     * Gets the number of active agents.
     *
     * @return Count of active agents
     */
    public int getActiveAgentCount() {
        return (int) agents.values().stream()
                .filter(agent -> agent.getStatus() == AgentStatus.ACTIVE)
                .count();
    }

    /**
     * Starts the heartbeat monitoring thread.
     */
    private void startHeartbeatMonitor() {
        heartbeatMonitor.scheduleAtFixedRate(
            this::checkHeartbeats,
            heartbeatIntervalSeconds,
            heartbeatIntervalSeconds,
            TimeUnit.SECONDS
        );
    }

    /**
     * Checks all agent heartbeats and marks inactive agents.
     */
    private void checkHeartbeats() {
        Instant now = Instant.now();
        Instant timeout = now.minusSeconds(agentTimeoutSeconds);

        agents.values().forEach(agent -> {
            if (agent.getStatus() == AgentStatus.ACTIVE && agent.getLastHeartbeat().isBefore(timeout)) {
                agent.markInactive();
                System.err.println("Agent marked inactive due to timeout: " + agent.getAgentId());
            }
        });
    }

    /**
     * Shuts down the registry and stops heartbeat monitoring.
     */
    public void shutdown() {
        System.out.println("Shutting down AgentRegistry...");
        heartbeatMonitor.shutdown();
        try {
            if (!heartbeatMonitor.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatMonitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatMonitor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        agents.clear();
        System.out.println("AgentRegistry shut down complete");
    }

    /**
     * Clears all agents from the registry (for testing).
     */
    public void clear() {
        agents.clear();
    }
}
