package org.yawlfoundation.yawl.engine.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * YawlAgentEngine is the main orchestrator for agent lifecycle management in YAWL.
 * It creates, manages, and monitors virtual threads for autonomous agents.
 * Each agent runs in its own virtual thread and processes work items from a workflow.
 *
 * Virtual Thread Benefits:
 * - Lightweight: Can create millions of agents without resource exhaustion
 * - Automatic: No manual thread pool tuning needed
 * - Scalable: One thread per agent regardless of workload
 */
public final class YawlAgentEngine {

    private final AgentRegistry registry;
    private final HeartbeatManager heartbeatManager;
    private final Set<Thread> agentThreads = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Runnable> agentRunnables = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Creates a new YawlAgentEngine.
     *
     * @throws IllegalStateException if virtual threads are not enabled
     */
    public YawlAgentEngine() {
        if (!VirtualThreadConfig.VIRTUAL_THREAD_ENABLED) {
            throw new IllegalStateException("Virtual threads must be enabled in VirtualThreadConfig");
        }

        VirtualThreadConfig.validate();

        this.registry = new AgentRegistry();
        this.heartbeatManager = new HeartbeatManager(registry);
    }

    /**
     * Starts the agent engine, including the heartbeat manager.
     */
    public synchronized void start() {
        if (running.getAndSet(true)) {
            return; // Already running
        }

        heartbeatManager.start();
    }

    /**
     * Stops the agent engine and all running agents gracefully.
     *
     * @throws InterruptedException if interrupted while waiting for agents to stop
     */
    public synchronized void stop() throws InterruptedException {
        running.set(false);

        // Stop heartbeat manager
        heartbeatManager.stop();

        // Stop all agents
        List<UUID> agentIds = new ArrayList<>(
            registry.getAllAgents().stream()
                    .map(AgentState::getAgentId)
                    .toList()
        );

        for (UUID agentId : agentIds) {
            stopAgent(agentId);
        }

        // Wait for all threads to complete
        for (Thread thread : agentThreads) {
            if (thread != null && thread.isAlive()) {
                thread.join(VirtualThreadConfig.AGENT_SHUTDOWN_TIMEOUT_MS);
            }
        }

        agentThreads.clear();
        agentRunnables.clear();
        registry.clear();
    }

    /**
     * Starts an agent with the given ID and workflow definition.
     * Creates and launches a new virtual thread to run the agent.
     *
     * @param agentId  Unique identifier for the agent
     * @param workflow The workflow definition the agent will execute
     * @return true if the agent was successfully started, false if already running
     * @throws NullPointerException if agentId or workflow is null
     * @throws IllegalStateException if the engine is not running
     * @throws IllegalArgumentException if agent is already registered
     */
    public boolean startAgent(UUID agentId, WorkflowDef workflow) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        Objects.requireNonNull(workflow, "Workflow cannot be null");

        if (!running.get()) {
            throw new IllegalStateException("Agent engine is not running. Call start() first.");
        }

        // Create agent state
        AgentState agentState = new AgentState(agentId, workflow);

        // Register the agent
        registry.register(agentId, agentState);
        registry.setLifecycle(agentId, AgentLifecycle.CREATED);

        // Create and start the agent thread
        Runnable agentRunnable = new AgentRunnable(agentId, agentState, registry, heartbeatManager);
        agentRunnables.put(agentId, agentRunnable);

        Thread agentThread = Thread.ofVirtual()
                .name(VirtualThreadConfig.AGENT_THREAD_NAME_PREFIX + agentId)
                .start(agentRunnable);

        agentThreads.add(agentThread);
        registry.registerThread(agentId, agentThread);

        registry.setLifecycle(agentId, AgentLifecycle.RUNNING);

        return true;
    }

    /**
     * Stops an agent gracefully.
     * The agent will finish its current work before terminating.
     *
     * @param agentId The agent UUID
     * @return true if the agent was found and stopped, false otherwise
     */
    public boolean stopAgent(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        if (!registry.isRegistered(agentId)) {
            return false;
        }

        AgentLifecycle current = registry.getLifecycle(agentId);
        if (current != null && current.isTerminal()) {
            return false; // Already stopped
        }

        // Mark as stopping
        registry.setLifecycle(agentId, AgentLifecycle.STOPPING);

        // Get and interrupt the thread
        Thread agentThread = registry.getThread(agentId);
        if (agentThread != null && agentThread.isAlive()) {
            agentThread.interrupt();
        }

        // Wait briefly for the thread to stop
        try {
            if (agentThread != null && agentThread.isAlive()) {
                agentThread.join(VirtualThreadConfig.AGENT_SHUTDOWN_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Update lifecycle and cleanup
        registry.setLifecycle(agentId, AgentLifecycle.STOPPED);
        agentRunnables.remove(agentId);
        agentThreads.remove(agentThread);

        return true;
    }

    /**
     * Checks if an agent is currently running.
     *
     * @param agentId The agent UUID
     * @return true if the agent is running, false otherwise
     */
    public boolean isRunning(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        AgentLifecycle lifecycle = registry.getLifecycle(agentId);
        if (lifecycle == null) {
            return false;
        }

        Thread agentThread = registry.getThread(agentId);
        return lifecycle.isActive() && agentThread != null && agentThread.isAlive();
    }

    /**
     * Gets the current lifecycle state of an agent.
     *
     * @param agentId The agent UUID
     * @return The AgentLifecycle state, or null if agent not found
     */
    public AgentLifecycle getAgentState(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return registry.getLifecycle(agentId);
    }

    /**
     * Gets the agent state object for monitoring and metrics.
     *
     * @param agentId The agent UUID
     * @return The AgentState, or null if agent not found
     */
    public AgentState getAgent(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return registry.getAgent(agentId);
    }

    /**
     * Gets all registered agents.
     *
     * @return List of all AgentState objects
     */
    public List<AgentState> getAllAgents() {
        return registry.getAllAgents();
    }

    /**
     * Gets all active agents (RUNNING or IDLE).
     *
     * @return List of active agents
     */
    public List<AgentState> getActiveAgents() {
        return registry.getActiveAgents();
    }

    /**
     * Gets the total count of registered agents.
     *
     * @return Agent count
     */
    public int getAgentCount() {
        return registry.getAgentCount();
    }

    /**
     * Gets the count of agents in a given lifecycle state.
     *
     * @param state The AgentLifecycle state
     * @return Count of agents in that state
     */
    public long getAgentCountByState(AgentLifecycle state) {
        Objects.requireNonNull(state, "Lifecycle state cannot be null");
        return registry.getAgentCountByLifecycle(state);
    }

    /**
     * Gets the registry for advanced query operations.
     *
     * @return The AgentRegistry
     */
    public AgentRegistry getRegistry() {
        return registry;
    }

    /**
     * Checks if the agent engine is running.
     *
     * @return true if running, false otherwise
     */
    public boolean isEngineRunning() {
        return running.get();
    }

    /**
     * Gets the number of virtual threads currently in use by agents.
     *
     * @return Count of active agent threads
     */
    public int getActiveThreadCount() {
        int count = 0;
        for (Thread thread : agentThreads) {
            if (thread != null && thread.isAlive()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Verifies that all agent threads are indeed virtual threads.
     * Used for validation and debugging.
     *
     * @return true if all active threads are virtual, false otherwise
     */
    public boolean areAllThreadsVirtual() {
        for (Thread thread : agentThreads) {
            if (thread != null && thread.isAlive() && !thread.isVirtual()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("YawlAgentEngine[running=%s, agents=%d, threads=%d, registry=%s]",
                running.get(), getAgentCount(), getActiveThreadCount(), registry);
    }

    /**
     * Inner class: AgentRunnable represents the main loop of an agent.
     * Runs on a virtual thread and continuously discovers and executes work items.
     */
    private static final class AgentRunnable implements Runnable {

        private final UUID agentId;
        private final AgentState agentState;
        private final AgentRegistry registry;
        private final HeartbeatManager heartbeatManager;

        AgentRunnable(UUID agentId, AgentState agentState, AgentRegistry registry,
                      HeartbeatManager heartbeatManager) {
            this.agentId = agentId;
            this.agentState = agentState;
            this.registry = registry;
            this.heartbeatManager = heartbeatManager;
        }

        @Override
        public void run() {
            try {
                // Renew heartbeat on startup
                heartbeatManager.renewHeartbeat(agentId);
                registry.setLifecycle(agentId, AgentLifecycle.IDLE);

                // Main agent loop
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // Discover available work items
                        List<WorkItem> items = discoverWork();

                        if (items.isEmpty()) {
                            // No work available, go idle
                            registry.setLifecycle(agentId, AgentLifecycle.IDLE);
                            Thread.sleep(VirtualThreadConfig.IDLE_BACKOFF_MS);
                        } else {
                            // Process work items
                            registry.setLifecycle(agentId, AgentLifecycle.RUNNING);
                            for (WorkItem item : items) {
                                if (Thread.currentThread().isInterrupted()) {
                                    break;
                                }
                                executeWorkItem(item);
                            }
                        }

                        // Renew heartbeat periodically
                        heartbeatManager.renewHeartbeat(agentId);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                registry.setLifecycle(agentId, AgentLifecycle.FAILED);
            } finally {
                registry.setLifecycle(agentId, AgentLifecycle.STOPPED);
            }
        }

        /**
         * Discovers available work items for this agent.
         * In a full implementation, this would query a work item queue.
         *
         * @return List of available work items
         */
        private List<WorkItem> discoverWork() {
            // In a real implementation, this would query a work queue
            // For now, return empty list (work must be explicitly assigned)
            return new ArrayList<>(agentState.getCurrentWork());
        }

        /**
         * Executes a single work item.
         *
         * @param item The work item to execute
         */
        private void executeWorkItem(WorkItem item) {
            try {
                // Validate work item state
                if (!(item.getStatus() instanceof WorkItemStatus.Pending)) {
                    return;
                }

                // Assign to this agent
                item.assignTo(agentId);
                agentState.addWorkItem(item);

                // Simulate work execution
                // In a real implementation, this would invoke the actual task logic
                Thread.sleep(100); // Placeholder execution time

                // Mark as complete
                item.complete();
                agentState.removeWorkItem(item);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                item.fail("Execution failed: " + e.getMessage());
            }
        }
    }
}
