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
     * Starts periodic heartbeat renewal for the given agent.
     * The heartbeat will be renewed every {@link HeartbeatConfig#HEARTBEAT_INTERVAL_SECONDS} seconds
     * until {@link #stopHeartbeat(UUID)} is called.
     *
     * <p>If a heartbeat task is already scheduled for this agent, it will be replaced.
     *
     * @param agentId The unique identifier of the agent
     * @throws NullPointerException if agentId is null
     */
    public void startHeartbeat(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        // Cancel any existing task for this agent
        ScheduledFuture<?> existingTask = heartbeatTasks.get(agentId);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
        }

        // Get the scheduler from registry's executor
        ScheduledExecutorService executor = registry.getScheduledExecutor();
        if (executor == null) {
            LOGGER.log(Level.WARNING, "Cannot start heartbeat: no executor available");
            return;
        }

        // Schedule new periodic renewal task
        ScheduledFuture<?> task = executor.scheduleAtFixedRate(
            () -> doRenewHeartbeat(agentId),
            HeartbeatConfig.HEARTBEAT_INTERVAL_SECONDS,
            HeartbeatConfig.HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        heartbeatTasks.put(agentId, task);
        LOGGER.log(Level.FINE, () -> "Started heartbeat renewal for agent: " + agentId);
    }

    /**
     * Stops periodic heartbeat renewal for the given agent.
     * The scheduled task is cancelled, but the agent remains registered.
     * To fully remove an agent, use {@link AgentRegistry#unregister(UUID)}.
     *
     * <p>If no heartbeat task is scheduled for this agent, this method does nothing.
     *
     * @param agentId The unique identifier of the agent
     * @throws NullPointerException if agentId is null
     */
    public void stopHeartbeat(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        ScheduledFuture<?> task = heartbeatTasks.remove(agentId);
        if (task != null && !task.isDone()) {
            task.cancel(false);
            LOGGER.log(Level.FINE, () -> "Stopped heartbeat renewal for agent: " + agentId);
        }
    }

    /**
     * Renews the heartbeat for the given agent by scheduling periodic renewal tasks.
     * This is the internal method called by the scheduler.
     *
     * <p>If the agent is not registered in the registry, this method logs a warning
     * and returns silently (fire-and-forget behavior).
     * This ensures a failed renewal does not crash the heartbeat scheduler.
     *
     * @param agentId The unique identifier of the agent
     * @throws NullPointerException if agentId is null
     */
    private void doRenewHeartbeat(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        var agentOpt = registry.getAgent(agentId);
        if (agentOpt.isPresent()) {
            try {
                AgentState agent = agentOpt.get();
                AgentState renewed = agent.renewHeartbeat(HeartbeatConfig.HEARTBEAT_TTL_SECONDS);
                registry.updateAgent(agentId, renewed);
                LOGGER.log(Level.FINEST, () -> "Renewed heartbeat for agent: " + agentId);
            } catch (Exception e) {
                // Log warning but do not propagate - heartbeat renewals are fire-and-forget
                LOGGER.log(Level.WARNING,
                    "Failed to renew heartbeat for agent " + agentId + ": " + e.getMessage(), e);
            }
        } else {
            // Agent may have been unregistered; log warning but continue
            LOGGER.log(Level.WARNING, "Heartbeat renewal failed: agent not found in registry: " + agentId);
        }
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

        var agentOpt = registry.getAgent(agentId);
        return agentOpt.isPresent() && agentOpt.get().isHealthy();
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
     * Gets the number of agents with active heartbeat renewal scheduled.
     *
     * @return The count of scheduled heartbeat tasks
     */
    public int getActiveHeartbeatCount() {
        return heartbeatTasks.size();
    }

    /**
     * Shuts down the heartbeat manager and cancels all pending renewals.
     * This should be called during system shutdown.
     */
    public void shutdown() {
        running.set(false);

        heartbeatTasks.values().forEach(task -> {
            if (!task.isDone()) {
                task.cancel(false);
            }
        });
        heartbeatTasks.clear();

        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            try {
                heartbeatThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        LOGGER.log(Level.INFO, "HeartbeatManager shutdown complete. Cancelled all pending renewals.");
    }

}
