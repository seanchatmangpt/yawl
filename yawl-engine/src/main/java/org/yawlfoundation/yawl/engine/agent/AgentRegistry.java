package org.yawlfoundation.yawl.engine.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * AgentRegistry is a thread-safe singleton that manages the registration and lifecycle
 * of all agents in the YAWL engine.
 *
 * <p><b>Design:</b>
 * <ul>
 * <li>Singleton pattern (via {@link #getInstance()})</li>
 * <li>Uses {@link ConcurrentHashMap} for thread-safe agent storage</li>
 * <li>Coordinates with {@link HeartbeatManager} for heartbeat renewal</li>
 * <li>Supports filtering healthy agents via {@link #getHealthyAgents()}</li>
 * <li>Maintains lifecycle state for agent coordination</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b>
 * All operations are thread-safe via {@link ConcurrentHashMap} or {@link Collections#synchronizedMap(Map)}.
 */
public final class AgentRegistry {

    private static final Logger LOGGER = Logger.getLogger(AgentRegistry.class.getName());
    private static AgentRegistry instance;

    private final ConcurrentHashMap<UUID, AgentState> agents;
    private final Map<UUID, AgentLifecycle> lifecycles = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, Thread> threads = Collections.synchronizedMap(new HashMap<>());
    private final HeartbeatManager heartbeatManager;
    private final ScheduledExecutorService executor;

    /**
     * Private constructor to enforce singleton pattern.
     */
    private AgentRegistry() {
        this.agents = new ConcurrentHashMap<>();
        this.executor = Executors.newScheduledThreadPool(
            HeartbeatConfig.HEARTBEAT_THREAD_POOL_SIZE
        );
        this.heartbeatManager = new HeartbeatManager(this);
        LOGGER.log(Level.INFO, "AgentRegistry initialized");
    }

    /**
     * Gets the singleton instance of AgentRegistry.
     * Thread-safe lazy initialization using double-checked locking.
     *
     * @return The singleton instance
     */
    public static synchronized AgentRegistry getInstance() {
        if (instance == null) {
            instance = new AgentRegistry();
        }
        return instance;
    }

    /**
     * Gets the scheduled executor service (for HeartbeatManager use).
     *
     * @return The ScheduledExecutorService
     */
    ScheduledExecutorService getScheduledExecutor() {
        return executor;
    }

    /**
     * Updates an agent's state in the registry.
     * Used internally for heartbeat renewal.
     *
     * @param agentId Agent UUID
     * @param newState The new AgentState
     */
    void updateAgent(UUID agentId, AgentState newState) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        Objects.requireNonNull(newState, "Agent state cannot be null");
        agents.put(agentId, newState);
    }

    /**
     * Registers a new agent in the registry and starts its heartbeat renewal.
     * If an agent with the same ID is already registered, it will be replaced.
     *
     * @param agentId Agent UUID
     * @param state   AgentState for this agent
     * @throws NullPointerException if agentId or state is null
     */
    public void register(UUID agentId, AgentState state) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        Objects.requireNonNull(state, "Agent state cannot be null");

        agents.put(agentId, state);
        lifecycles.put(agentId, AgentLifecycle.CREATED);

        // Start heartbeat renewal for this agent
        heartbeatManager.startHeartbeat(agentId);

        LOGGER.log(Level.INFO, () -> String.format(
            "Agent registered: %s (workflow=%s)",
            agentId,
            state.getWorkflow().getName()
        ));
    }

    /**
     * Registers the virtual thread associated with an agent.
     *
     * @param agentId Agent UUID
     * @param thread  The virtual thread running this agent
     * @throws NullPointerException if agentId or thread is null
     * @throws IllegalArgumentException if agent not found
     */
    public void registerThread(UUID agentId, Thread thread) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        Objects.requireNonNull(thread, "Thread cannot be null");

        if (!agents.containsKey(agentId)) {
            throw new IllegalArgumentException("Agent not registered: " + agentId);
        }

        threads.put(agentId, thread);
    }

    /**
     * Unregisters an agent from the registry and stops its heartbeat renewal.
     *
     * @param agentId Agent UUID
     * @return true if the agent was found and removed, false otherwise
     */
    public boolean unregister(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        boolean removed = agents.remove(agentId) != null;
        lifecycles.remove(agentId);
        threads.remove(agentId);

        if (removed) {
            // Stop heartbeat renewal for this agent
            heartbeatManager.stopHeartbeat(agentId);
            LOGGER.log(Level.INFO, () -> "Agent unregistered: " + agentId);
        }

        return removed;
    }

    /**
     * Gets an agent's state by ID.
     *
     * @param agentId Agent UUID
     * @return An Optional containing the AgentState, or empty if not found
     */
    public Optional<AgentState> getAgent(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return Optional.ofNullable(agents.get(agentId));
    }

    /**
     * Gets an agent's state by ID (internal method for compatibility).
     * This is used internally by HeartbeatManager.
     *
     * @param agentId Agent UUID
     * @return The AgentState, or null if not found
     */
    AgentState getAgentNullable(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return agents.get(agentId);
    }

    /**
     * Updates an agent's state.
     *
     * @param agentId Agent UUID
     * @param state   Updated AgentState
     * @throws NullPointerException if agentId or state is null
     * @throws IllegalArgumentException if agent not found
     */
    public void updateAgent(UUID agentId, AgentState state) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        Objects.requireNonNull(state, "Agent state cannot be null");

        if (!agents.containsKey(agentId)) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        agents.put(agentId, state);
    }

    /**
     * Gets the lifecycle state of an agent.
     *
     * @param agentId Agent UUID
     * @return The AgentLifecycle state, or null if agent not found
     */
    public AgentLifecycle getLifecycle(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return lifecycles.get(agentId);
    }

    /**
     * Updates the lifecycle state of an agent.
     *
     * @param agentId Agent UUID
     * @param state   New AgentLifecycle state
     * @throws IllegalArgumentException if agent not found
     */
    public void setLifecycle(UUID agentId, AgentLifecycle state) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        Objects.requireNonNull(state, "Lifecycle state cannot be null");

        if (!agents.containsKey(agentId)) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }

        lifecycles.put(agentId, state);
    }

    /**
     * Gets the thread running an agent.
     *
     * @param agentId Agent UUID
     * @return The Thread, or null if not found
     */
    public Thread getThread(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return threads.get(agentId);
    }

    /**
     * Gets all registered agents (defensive copy).
     *
     * @return List of all registered AgentState objects
     */
    public List<AgentState> getAllAgents() {
        return new ArrayList<>(agents.values());
    }

    /**
     * Gets all agents in a given lifecycle state.
     *
     * @param state The AgentLifecycle state to filter by
     * @return List of agents in that state (defensive copy)
     */
    public List<AgentState> getAgentsByLifecycle(AgentLifecycle state) {
        Objects.requireNonNull(state, "Lifecycle state cannot be null");

        return lifecycles.entrySet().stream()
                .filter(entry -> entry.getValue() == state)
                .map(entry -> agents.get(entry.getKey()))
                .collect(Collectors.toList());
    }

    /**
     * Gets the total number of registered agents.
     *
     * @return Agent count
     */
    public int getAgentCount() {
        return agents.size();
    }

    /**
     * Gets the number of agents in a given lifecycle state.
     *
     * @param state The AgentLifecycle state
     * @return Count of agents in that state
     */
    public long getAgentCountByLifecycle(AgentLifecycle state) {
        Objects.requireNonNull(state, "Lifecycle state cannot be null");
        return lifecycles.values().stream().filter(s -> s == state).count();
    }

    /**
     * Checks if an agent is registered.
     *
     * @param agentId Agent UUID
     * @return true if registered, false otherwise
     */
    public boolean isRegistered(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return agents.containsKey(agentId);
    }

    /**
     * Gets all running agents (RUNNING or IDLE states).
     *
     * @return List of active agents (defensive copy)
     */
    public List<AgentState> getActiveAgents() {
        return lifecycles.entrySet().stream()
                .filter(entry -> entry.getValue().isActive())
                .map(entry -> agents.get(entry.getKey()))
                .collect(Collectors.toList());
    }

    /**
     * Gets all healthy agents (those whose heartbeat has not expired).
     * Agents are considered healthy if {@link AgentState#isHealthy()} returns true.
     *
     * @return A list of all healthy {@link AgentState} objects
     */
    public List<AgentState> getHealthyAgents() {
        return Collections.unmodifiableList(
            agents.values().stream()
                .filter(AgentState::isHealthy)
                .toList()
        );
    }

    /**
     * Gets the total number of registered agents.
     *
     * @return The count of agents in the registry
     */
    public int size() {
        return agents.size();
    }

    /**
     * Gets the number of agents with active heartbeat renewal.
     *
     * @return The count of agents with scheduled heartbeat tasks
     */
    public int getActiveHeartbeatCount() {
        return heartbeatManager.getActiveHeartbeatCount();
    }

    /**
     * Gets a string summary of the registry state for logging/debugging.
     *
     * @return A summary string including registry size and health metrics
     */
    public String getSummary() {
        int total = size();
        int healthy = getHealthyAgents().size();
        int expired = total - healthy;

        return String.format(
            "AgentRegistry[total=%d, healthy=%d, expired=%d, activeHeartbeats=%d]",
            total, healthy, expired, getActiveHeartbeatCount()
        );
    }

    /**
     * Shuts down the registry and all its resources.
     * Should be called during system shutdown.
     */
    public void shutdown() {
        heartbeatManager.shutdown();
        agents.clear();
        lifecycles.clear();
        threads.clear();
        executor.shutdown();
        LOGGER.log(Level.INFO, "AgentRegistry shutdown complete");
    }

    /**
     * Clears all agents from the registry.
     * Used during shutdown.
     */
    public void clear() {
        agents.clear();
        lifecycles.clear();
        threads.clear();
    }

    @Override
    public String toString() {
        return String.format("AgentRegistry[total=%d, created=%d, running=%d, idle=%d, paused=%d, stopping=%d, stopped=%d, failed=%d]",
                getAgentCount(),
                getAgentCountByLifecycle(AgentLifecycle.CREATED),
                getAgentCountByLifecycle(AgentLifecycle.RUNNING),
                getAgentCountByLifecycle(AgentLifecycle.IDLE),
                getAgentCountByLifecycle(AgentLifecycle.PAUSED),
                getAgentCountByLifecycle(AgentLifecycle.STOPPING),
                getAgentCountByLifecycle(AgentLifecycle.STOPPED),
                getAgentCountByLifecycle(AgentLifecycle.FAILED)
        );
    }
}
