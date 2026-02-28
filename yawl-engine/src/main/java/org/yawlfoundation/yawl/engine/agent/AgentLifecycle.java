package org.yawlfoundation.yawl.engine.agent;

/**
 * AgentLifecycle represents the lifecycle states of an agent.
 * An agent transitions through these states from creation to termination.
 *
 * States:
 * - CREATED: Agent initialized, not yet started
 * - RUNNING: Agent thread is actively processing work items
 * - IDLE: Agent thread is waiting for work assignments
 * - PAUSED: Agent thread is paused (waiting for external signal to resume)
 * - STOPPING: Agent thread is gracefully shutting down
 * - STOPPED: Agent thread has terminated
 * - FAILED: Agent thread encountered a fatal error
 */
public enum AgentLifecycle {
    /**
     * Agent has been created but not yet started.
     */
    CREATED,

    /**
     * Agent thread is actively executing work items.
     */
    RUNNING,

    /**
     * Agent thread is idle, waiting for work assignments.
     */
    IDLE,

    /**
     * Agent thread is paused and not accepting new work.
     */
    PAUSED,

    /**
     * Agent thread is shutting down gracefully.
     */
    STOPPING,

    /**
     * Agent thread has fully terminated.
     */
    STOPPED,

    /**
     * Agent thread encountered a fatal error.
     */
    FAILED;

    /**
     * Checks if this lifecycle state is terminal (no further transitions possible).
     *
     * @return true if this is STOPPED or FAILED, false otherwise
     */
    public boolean isTerminal() {
        return this == STOPPED || this == FAILED;
    }

    /**
     * Checks if this lifecycle state indicates the agent is running (actively working).
     *
     * @return true if RUNNING or IDLE, false otherwise
     */
    public boolean isActive() {
        return this == RUNNING || this == IDLE;
    }
}
