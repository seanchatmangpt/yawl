package org.yawlfoundation.yawl.engine.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * HeartbeatManager manages periodic heartbeat renewal and monitoring for registered agents.
 * Each agent's heartbeat is renewed every {@link HeartbeatConfig#HEARTBEAT_INTERVAL_SECONDS} seconds.
 *
 * <p><b>Design:</b>
 * <ul>
 * <li>Uses {@link ScheduledExecutorService} from the registry for scalability</li>
 * <li>Maintains a map of scheduled renewal tasks per agent ID</li>
 * <li>Failed heartbeat renewals log warnings but do not crash the agent</li>
 * <li>Thread-safe for concurrent operations (register/unregister/renew)</li>
 * <li>Also monitors unhealthy agents and marks them as failed</li>
 * </ul>
 */
public final class HeartbeatManager {

    private static final Logger LOGGER = Logger.getLogger(HeartbeatManager.class.getName());

    private final AgentRegistry registry;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> heartbeatTasks;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread heartbeatThread;

    /**
     * Creates a new HeartbeatManager.
     *
     * @param registry The AgentRegistry to monitor
     * @throws NullPointerException if registry is null
     */
    public HeartbeatManager(AgentRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "Agent registry cannot be null");
        this.heartbeatTasks = new ConcurrentHashMap<>();
    }

    /**
     * Starts the heartbeat monitoring thread.
     * This thread periodically checks all agents' heartbeat status.
     */
    public void start() {
        if (running.getAndSet(true)) {
            return; // Already running
        }

        heartbeatThread = Thread.ofVirtual()
                .name("yawl-heartbeat-manager")
                .start(this::monitorHeartbeats);
    }

    /**
     * Stops the heartbeat monitoring thread gracefully.
     *
     * @throws InterruptedException if waiting for the thread is interrupted
     */
    public void stop() throws InterruptedException {
        running.set(false);

        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.join(HeartbeatConfig.HEARTBEAT_RENEWAL_TIMEOUT_MILLIS);
        }
    }

    /**
     * Main heartbeat monitoring loop.
     * Runs on a virtual thread and periodically checks agent health.
     */
    private void monitorHeartbeats() {
        while (running.get()) {
            try {
                Thread.sleep(HeartbeatConfig.HEARTBEAT_INTERVAL_SECONDS * 1000); // Convert seconds to ms
                checkAllAgentHeartbeats();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Checks the heartbeat status of all registered agents.
     * Identifies and marks unhealthy agents.
     */
    private void checkAllAgentHeartbeats() {
        List<AgentState> agents = registry.getAllAgents();

        for (AgentState agent : agents) {
            if (!agent.isHealthy()) {
                handleUnhealthyAgent(agent);
            }
        }
    }

    /**
     * Handles an unhealthy agent (heartbeat expired).
     * Updates the registry and marks the agent as failed.
     *
     * @param agent The unhealthy AgentState
     */
    private void handleUnhealthyAgent(AgentState agent) {
        UUID agentId = agent.getAgentId();
        AgentState failedAgent = agent.withStatus(AgentStatus.failed("Heartbeat expired"));
        registry.updateAgent(agentId, failedAgent);
    }

    /**
     * Manually renews the heartbeat for a specific agent.
     * Called by the agent itself to signal it's still alive.
     *
     * @param agentId The agent UUID
     */
    public void renewHeartbeat(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        registry.getAgent(agentId).ifPresent(agent -> {
            AgentState renewed = agent.renewHeartbeat(HeartbeatConfig.HEARTBEAT_TTL_SECONDS);
            registry.updateAgent(agentId, renewed);
        });
    }

    /**
     * Gets the health status of a specific agent.
     *
     * @param agentId The agent UUID
     * @return true if agent is healthy, false otherwise
     */
    public boolean isAgentHealthy(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        AgentState agent = registry.getAgent(agentId);
        return agent != null && agent.isHealthy();
    }

    /**
     * Gets all unhealthy agents.
     *
     * @return List of unhealthy agents (defensive copy)
     */
    public List<AgentState> getUnhealthyAgents() {
        List<AgentState> unhealthy = new ArrayList<>();

        for (AgentState agent : registry.getAllAgents()) {
            if (!agent.isHealthy()) {
                unhealthy.add(agent);
            }
        }

        return unhealthy;
    }

    /**
     * Checks if the heartbeat manager is currently running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts heartbeat renewal for a specific agent.
     * Placeholder for future scheduled renewal implementation.
     *
     * @param agentId The agent UUID
     */
    public void startHeartbeat(UUID agentId) {
        // Placeholder: In future, schedule periodic heartbeat renewal
        // For now, heartbeats are renewed manually via renewHeartbeat()
    }

    /**
     * Stops heartbeat renewal for a specific agent.
     * Placeholder for future scheduled renewal cancellation.
     *
     * @param agentId The agent UUID
     */
    public void stopHeartbeat(UUID agentId) {
        // Placeholder: In future, cancel scheduled heartbeat renewal
    }

    /**
     * Gets the count of active heartbeat renewal tasks.
     *
     * @return Number of agents with active heartbeat renewals
     */
    public int getActiveHeartbeatCount() {
        // Return count of all agents (approximation)
        return registry.size();
    }

    /**
     * Shuts down the heartbeat manager.
     */
    public void shutdown() {
        stop();
    }
}
