package org.yawlfoundation.yawl.engine.agent;

/**
 * Configuration constants for agent heartbeat management.
 * These values tune the agent lifecycle monitoring behavior.
 */
public final class HeartbeatConfig {

    // Heartbeat interval: how often agents must renew their TTL
    public static final long HEARTBEAT_INTERVAL_SECONDS = 60;

    // Heartbeat TTL: duration granted by each successful renewal
    public static final long HEARTBEAT_TTL_SECONDS = 60;

    // Thread pool size for heartbeat executor (0 = auto-scale to available cores)
    public static final int HEARTBEAT_THREAD_POOL_SIZE = 0;

    // Heartbeat renewal timeout in milliseconds
    public static final long HEARTBEAT_RENEWAL_TIMEOUT_MILLIS = 5000;

    private HeartbeatConfig() {
        // Utility class, no instantiation
    }
}
