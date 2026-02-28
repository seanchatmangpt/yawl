package org.yawlfoundation.yawl.engine.agent;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

/**
 * Represents the complete state of an autonomous agent in the YAWL engine.
 * Immutable record capturing agent identity, status, and lifecycle metadata.
 *
 * Thread-safe: Uses immutable records and instant snapshots for heartbeat management.
 *
 * @param agentId     Unique identifier for this agent (UUID)
 * @param status      Current operational status (Running, Idle, Failed)
 * @param registeredAt Timestamp when agent first registered
 * @param lastHeartbeat Timestamp of most recent heartbeat renewal
 * @param ttlExpires   Absolute expiration time for this heartbeat window
 */
public record AgentState(
        UUID agentId,
        AgentStatus status,
        Instant registeredAt,
        Instant lastHeartbeat,
        Instant ttlExpires
) {

    /**
     * Constructor with validation.
     */
    public AgentState {
        Objects.requireNonNull(agentId, "agentId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(registeredAt, "registeredAt cannot be null");
        Objects.requireNonNull(lastHeartbeat, "lastHeartbeat cannot be null");
        Objects.requireNonNull(ttlExpires, "ttlExpires cannot be null");
    }

    /**
     * Create an initial AgentState with current timestamps.
     *
     * @param agentId Unique agent identifier
     * @param status Initial status
     * @param ttlSeconds TTL duration in seconds
     * @return New AgentState
     */
    public static AgentState create(UUID agentId, AgentStatus status, long ttlSeconds) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(ttlSeconds);
        return new AgentState(agentId, status, now, now, expiration);
    }

    /**
     * Check if this agent's heartbeat has expired.
     *
     * @return true if current time has exceeded ttlExpires
     */
    public boolean isExpired() {
        return Instant.now().isAfter(ttlExpires);
    }

    /**
     * Check if this agent is healthy (not expired and not in Failed state).
     *
     * @return true if agent is responsive and operational
     */
    public boolean isHealthy() {
        return !isExpired() && !(status instanceof AgentStatus.Failed);
    }

    /**
     * Renew the heartbeat TTL, extending expiration time.
     *
     * @param ttlSeconds New TTL duration in seconds
     * @return Updated AgentState with renewed heartbeat and TTL
     */
    public AgentState renewHeartbeat(long ttlSeconds) {
        Instant now = Instant.now();
        Instant newExpiration = now.plusSeconds(ttlSeconds);
        return new AgentState(agentId, status, registeredAt, now, newExpiration);
    }

    /**
     * Update the agent's operational status.
     *
     * @param newStatus New status
     * @return Updated AgentState with new status
     */
    public AgentState withStatus(AgentStatus newStatus) {
        return new AgentState(agentId, newStatus, registeredAt, lastHeartbeat, ttlExpires);
    }

    /**
     * Get the remaining TTL duration in milliseconds.
     *
     * @return Milliseconds until expiration, or 0 if expired
     */
    public long getRemainingTtlMillis() {
        long remaining = ttlExpires.toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0, remaining);
    }

    /**
     * Get uptime (duration since registration) in milliseconds.
     *
     * @return Milliseconds since agent was registered
     */
    public long getUptimeMillis() {
        return Instant.now().toEpochMilli() - registeredAt.toEpochMilli();
    }

    @Override
    public String toString() {
        return "AgentState{" +
                "agentId=" + agentId +
                ", status=" + status +
                ", healthy=" + isHealthy() +
                ", remainingTtl=" + getRemainingTtlMillis() + "ms" +
                '}';
    }
}
