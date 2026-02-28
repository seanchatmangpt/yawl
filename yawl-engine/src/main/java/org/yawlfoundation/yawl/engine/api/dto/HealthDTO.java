package org.yawlfoundation.yawl.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Transfer Object for health check responses.
 * Follows Spring Boot Actuator health endpoint conventions.
 *
 * @param status Overall health status (UP, DOWN, OUT_OF_SERVICE, UNKNOWN)
 * @param checks Individual component health checks
 * @param timestamp Timestamp when health check was performed
 */
public record HealthDTO(
        @JsonProperty("status")
        String status,

        @JsonProperty("checks")
        Map<String, Object> checks,

        @JsonProperty("timestamp")
        Instant timestamp
) {

    /**
     * Constructor with validation.
     */
    public HealthDTO {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status cannot be blank");
        }
        if (checks == null) {
            throw new IllegalArgumentException("checks cannot be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp cannot be null");
        }
    }

    /**
     * Create a health check response indicating system is UP.
     *
     * @return New HealthDTO with UP status
     */
    public static HealthDTO up() {
        return up(new HashMap<>());
    }

    /**
     * Create a health check response indicating system is UP with additional checks.
     *
     * @param checks Map of component health checks
     * @return New HealthDTO with UP status
     */
    public static HealthDTO up(Map<String, Object> checks) {
        return new HealthDTO("UP", checks != null ? checks : new HashMap<>(), Instant.now());
    }

    /**
     * Create a health check response indicating system is DOWN.
     *
     * @param reason Reason for being down
     * @return New HealthDTO with DOWN status
     */
    public static HealthDTO down(String reason) {
        Map<String, Object> checks = new HashMap<>();
        checks.put("error", reason);
        return new HealthDTO("DOWN", checks, Instant.now());
    }

    /**
     * Create a health check response indicating system is OUT_OF_SERVICE.
     *
     * @param reason Reason for being out of service
     * @return New HealthDTO with OUT_OF_SERVICE status
     */
    public static HealthDTO outOfService(String reason) {
        Map<String, Object> checks = new HashMap<>();
        checks.put("reason", reason);
        return new HealthDTO("OUT_OF_SERVICE", checks, Instant.now());
    }

    /**
     * Create a liveness probe response (checks if JVM is alive).
     *
     * @return New HealthDTO for liveness probe
     */
    public static HealthDTO liveness() {
        Map<String, Object> checks = new HashMap<>();
        checks.put("jvm", "UP");
        checks.put("memory", "OK");
        return new HealthDTO("UP", checks, Instant.now());
    }

    /**
     * Create a readiness probe response (checks if engine is ready for work).
     *
     * @param agentCount Number of healthy agents available
     * @param isReady Whether engine is ready to accept work
     * @return New HealthDTO for readiness probe
     */
    public static HealthDTO readiness(int agentCount, boolean isReady) {
        Map<String, Object> checks = new HashMap<>();
        checks.put("agents", isReady ? "READY" : "NOT_READY");
        checks.put("agentCount", agentCount);
        checks.put("database", "CONNECTED");

        String status = isReady && agentCount > 0 ? "UP" : "DOWN";
        return new HealthDTO(status, checks, Instant.now());
    }

    /**
     * Check if the health status indicates the system is operational.
     *
     * @return true if status is UP
     */
    public boolean isHealthy() {
        return "UP".equals(status);
    }

    /**
     * Get detailed status information as a string.
     *
     * @return Formatted status string
     */
    public String getDetailedStatus() {
        return status + " - " + checks.getOrDefault("error", checks.getOrDefault("reason", "OK"));
    }
}
