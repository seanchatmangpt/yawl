package org.yawlfoundation.yawl.engine.agent;

/**
 * Configuration settings for virtual thread executor in the YAWL engine.
 * Enables efficient scaling of agent threads using Java 21+ virtual threads.
 *
 * @since YAWL 6.0
 */
public final class VirtualThreadConfig {

    /**
     * Enable virtual thread execution for agents.
     * When false, platform threads are used (legacy behavior).
     */
    public static final boolean VIRTUAL_THREAD_ENABLED = true;

    /**
     * Enable automatic scaling of agent threads based on workload.
     * When enabled, the executor automatically adjusts thread count
     * based on pending work items and agent availability.
     */
    public static final boolean AUTO_SCALE_ENABLED = true;

    /**
     * Maximum number of agent threads that can be created.
     * Virtual threads have minimal resource overhead, so this is set high.
     * In practice, the JVM memory and CPU will be the limiting factor.
     */
    public static final int MAX_AGENT_THREADS = 1_000_000;

    /**
     * Default number of agent threads to pre-allocate at startup.
     * Virtual threads don't require pre-allocation; this is mainly
     * for documentation purposes and can be ignored.
     */
    public static final int DEFAULT_INITIAL_THREADS = 10;

    /**
     * Timeout in milliseconds for agent thread startup.
     * Allows detection of startup failures.
     */
    public static final long AGENT_STARTUP_TIMEOUT_MS = 5_000;

    /**
     * Timeout in milliseconds for agent thread shutdown.
     * Allows graceful termination of agent work.
     */
    public static final long AGENT_SHUTDOWN_TIMEOUT_MS = 10_000;

    /**
     * Heartbeat check interval in milliseconds.
     * How often the manager checks if agents are still alive.
     */
    public static final long HEARTBEAT_INTERVAL_MS = 1_000;

    /**
     * Idle backoff sleep duration in milliseconds.
     * When an agent has no work, it sleeps for this duration.
     */
    public static final long IDLE_BACKOFF_MS = 100;

    /**
     * Maximum idle time in milliseconds before an agent is considered stale.
     * If an agent hasn't executed work within this time, it's flagged for review.
     */
    public static final long MAX_IDLE_TIME_MS = 60_000;

    /**
     * Thread name prefix for virtual threads running agents.
     * Helps with debugging and monitoring agent threads.
     */
    public static final String AGENT_THREAD_NAME_PREFIX = "yawl-agent-";

    /**
     * Private constructor to prevent instantiation.
     * This is a configuration class with only static constants.
     */
    private VirtualThreadConfig() {
        throw new UnsupportedOperationException(
            "VirtualThreadConfig is a configuration class and cannot be instantiated"
        );
    }

    /**
     * Validates configuration values at startup.
     * Throws IllegalArgumentException if any configuration is invalid.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public static void validate() {
        if (MAX_AGENT_THREADS <= 0) {
            throw new IllegalArgumentException(
                "MAX_AGENT_THREADS must be positive, got: " + MAX_AGENT_THREADS
            );
        }
        if (AGENT_STARTUP_TIMEOUT_MS <= 0) {
            throw new IllegalArgumentException(
                "AGENT_STARTUP_TIMEOUT_MS must be positive, got: " + AGENT_STARTUP_TIMEOUT_MS
            );
        }
        if (AGENT_SHUTDOWN_TIMEOUT_MS <= 0) {
            throw new IllegalArgumentException(
                "AGENT_SHUTDOWN_TIMEOUT_MS must be positive, got: " + AGENT_SHUTDOWN_TIMEOUT_MS
            );
        }
        if (HEARTBEAT_INTERVAL_MS <= 0) {
            throw new IllegalArgumentException(
                "HEARTBEAT_INTERVAL_MS must be positive, got: " + HEARTBEAT_INTERVAL_MS
            );
        }
        if (IDLE_BACKOFF_MS <= 0) {
            throw new IllegalArgumentException(
                "IDLE_BACKOFF_MS must be positive, got: " + IDLE_BACKOFF_MS
            );
        }
    }
}
