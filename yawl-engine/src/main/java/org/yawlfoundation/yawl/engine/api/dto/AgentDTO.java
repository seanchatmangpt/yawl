package org.yawlfoundation.yawl.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.yawlfoundation.yawl.engine.agent.AgentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for agent information in REST API responses.
 * Represents the current state of an agent and its workload metrics.
 *
 * @param id Unique agent identifier
 * @param status Current operational status (RUNNING, IDLE, FAILED)
 * @param workflowId ID of workflow this agent is executing
 * @param workCount Total work items processed by this agent
 * @param heartbeatTTL Milliseconds until heartbeat expires
 * @param uptime Milliseconds since agent was registered
 * @param registeredAt Timestamp when agent was registered
 * @param lastHeartbeat Timestamp of most recent heartbeat
 */
public record AgentDTO(
        @JsonProperty("id")
        UUID id,

        @JsonProperty("status")
        String status,

        @JsonProperty("workflowId")
        String workflowId,

        @JsonProperty("workCount")
        long workCount,

        @JsonProperty("heartbeatTTL")
        long heartbeatTTL,

        @JsonProperty("uptime")
        long uptime,

        @JsonProperty("registeredAt")
        Instant registeredAt,

        @JsonProperty("lastHeartbeat")
        Instant lastHeartbeat
) {

    /**
     * Create an AgentDTO with all required fields.
     */
    public AgentDTO {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status cannot be blank");
        }
        if (registeredAt == null) {
            throw new IllegalArgumentException("registeredAt cannot be null");
        }
        if (lastHeartbeat == null) {
            throw new IllegalArgumentException("lastHeartbeat cannot be null");
        }
    }

    /**
     * Factory method to create AgentDTO from agent state data.
     *
     * @param id Agent ID
     * @param status Agent operational status (sealed hierarchy)
     * @param workflowId Workflow ID (nullable)
     * @param workCount Count of processed work items
     * @param heartbeatTTL Remaining TTL in milliseconds
     * @param uptime Uptime in milliseconds
     * @param registeredAt Registration timestamp
     * @param lastHeartbeat Last heartbeat timestamp
     * @return New AgentDTO instance
     */
    public static AgentDTO create(UUID id, AgentStatus status, String workflowId, long workCount,
                                   long heartbeatTTL, long uptime, Instant registeredAt,
                                   Instant lastHeartbeat) {
        String statusStr = status.toString();
        return new AgentDTO(id, statusStr, workflowId != null ? workflowId : "", workCount,
                heartbeatTTL, uptime, registeredAt, lastHeartbeat);
    }

    /**
     * Check if this agent is currently healthy (not expired, not failed).
     *
     * @return true if agent heartbeat is still valid
     */
    public boolean isHealthy() {
        return heartbeatTTL > 0 && !status.contains("FAILED");
    }

    /**
     * Get human-readable uptime string (e.g., "2h 15m 30s").
     *
     * @return Formatted uptime string
     */
    public String getFormattedUptime() {
        long totalSeconds = uptime / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }
}
