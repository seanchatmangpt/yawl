package org.yawlfoundation.yawl.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Data Transfer Object for system-wide metrics in REST API responses.
 * Provides point-in-time snapshot of engine performance and load metrics.
 *
 * @param agentCount Total number of registered agents
 * @param healthyAgentCount Number of agents currently healthy (not expired/failed)
 * @param queueSize Number of work items currently in queue
 * @param throughput Work items processed per minute (approximate)
 * @param avgLatency Average work item processing latency in milliseconds
 * @param oldestItemAge Age of oldest unprocessed work item in milliseconds
 * @param timestamp Timestamp when metrics were captured
 */
public record MetricsDTO(
        @JsonProperty("agentCount")
        int agentCount,

        @JsonProperty("healthyAgentCount")
        int healthyAgentCount,

        @JsonProperty("queueSize")
        int queueSize,

        @JsonProperty("throughput")
        double throughput,

        @JsonProperty("avgLatency")
        long avgLatency,

        @JsonProperty("oldestItemAge")
        long oldestItemAge,

        @JsonProperty("timestamp")
        Instant timestamp
) {

    /**
     * Constructor with validation.
     */
    public MetricsDTO {
        if (agentCount < 0) {
            throw new IllegalArgumentException("agentCount cannot be negative");
        }
        if (healthyAgentCount < 0 || healthyAgentCount > agentCount) {
            throw new IllegalArgumentException("healthyAgentCount must be between 0 and agentCount");
        }
        if (queueSize < 0) {
            throw new IllegalArgumentException("queueSize cannot be negative");
        }
        if (throughput < 0) {
            throw new IllegalArgumentException("throughput cannot be negative");
        }
        if (avgLatency < 0) {
            throw new IllegalArgumentException("avgLatency cannot be negative");
        }
        if (oldestItemAge < 0) {
            throw new IllegalArgumentException("oldestItemAge cannot be negative");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp cannot be null");
        }
    }

    /**
     * Factory method to create MetricsDTO from engine state.
     *
     * @param agentCount Total agent count
     * @param healthyAgentCount Healthy agent count
     * @param queueSize Current queue size
     * @param throughput Items per minute
     * @param avgLatency Average latency in milliseconds
     * @param oldestItemAge Age of oldest item in milliseconds
     * @return New MetricsDTO instance
     */
    public static MetricsDTO create(int agentCount, int healthyAgentCount, int queueSize,
                                     double throughput, long avgLatency, long oldestItemAge) {
        return new MetricsDTO(agentCount, healthyAgentCount, queueSize, throughput, avgLatency,
                oldestItemAge, Instant.now());
    }

    /**
     * Get health status based on queue size and throughput.
     *
     * @return "healthy" if queue is small and throughput is adequate, "degraded" otherwise
     */
    public String getHealthStatus() {
        if (queueSize > 100 || throughput < 1.0) {
            return "degraded";
        }
        if (queueSize > 50 || throughput < 5.0) {
            return "warning";
        }
        return "healthy";
    }

    /**
     * Get human-readable throughput string (e.g., "45 items/min").
     *
     * @return Formatted throughput string
     */
    public String getFormattedThroughput() {
        return String.format("%.1f items/min", throughput);
    }

    /**
     * Get human-readable average latency string (e.g., "234ms").
     *
     * @return Formatted latency string
     */
    public String getFormattedAvgLatency() {
        if (avgLatency < 1000) {
            return avgLatency + "ms";
        }
        double seconds = avgLatency / 1000.0;
        return String.format("%.2fs", seconds);
    }

    /**
     * Get human-readable oldest item age string (e.g., "5m 30s").
     *
     * @return Formatted age string
     */
    public String getFormattedOldestItemAge() {
        long totalSeconds = oldestItemAge / 1000;
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

    /**
     * Get utilization percentage based on queue size and healthy agents.
     * Returns percentage of theoretical capacity being used.
     *
     * @return Utilization percentage (0-100+)
     */
    public int getUtilizationPercent() {
        if (healthyAgentCount == 0) {
            return queueSize > 0 ? 100 : 0;
        }
        return (int) Math.min(100, (queueSize * 100) / (healthyAgentCount * 10));
    }
}
