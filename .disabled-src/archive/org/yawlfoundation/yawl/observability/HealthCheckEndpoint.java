package org.yawlfoundation.yawl.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Health check endpoint for YAWL engine.
 *
 * Provides Kubernetes-compatible liveness, readiness, and startup probes.
 *
 * Probe Types:
 * - Liveness: Is the process still running? (basic JVM check)
 * - Readiness: Can the engine accept work? (database + queue checks)
 * - Startup: Has the engine finished initialization? (warmup period)
 *
 * Health Status:
 * - UP: All subsystems operational
 * - DEGRADED: Some subsystems impaired but recovering
 * - DOWN: Critical failure, restart recommended
 *
 * HTTP Response Codes:
 * - 200 OK: Health check passed
 * - 503 Service Unavailable: Health check failed
 */
public class HealthCheckEndpoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckEndpoint.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Health status enumeration
    public enum HealthStatus {
        UP("UP"),
        DEGRADED("DEGRADED"),
        DOWN("DOWN");

        private final String value;

        HealthStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    // Health check result
    public static class HealthCheckResult implements Serializable {
        private final HealthStatus status;
        private final String timestamp;
        private final Map<String, Object> details;
        private final long uptime;

        public HealthCheckResult(HealthStatus status, long uptime, Map<String, Object> details) {
            this.status = status;
            this.timestamp = Instant.now().toString();
            this.uptime = uptime;
            this.details = details != null ? new HashMap<>(details) : new HashMap<>();
        }

        public HealthStatus getStatus() {
            return status;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public long getUptime() {
            return uptime;
        }

        public Map<String, Object> getDetails() {
            return details;
        }
    }

    private final HealthCheckDelegate delegate;
    private final long startTime;

    public HealthCheckEndpoint(HealthCheckDelegate delegate) {
        this.delegate = delegate;
        this.startTime = System.currentTimeMillis();
        LOGGER.info("HealthCheckEndpoint initialized");
    }

    /**
     * Liveness probe: Is the JVM process still running?
     * Should return quickly; used to restart container if fails.
     */
    public HealthCheckResult liveness() {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("jvm_memory_available", Runtime.getRuntime().freeMemory());
            details.put("jvm_threads_count", Thread.activeCount());

            if (Runtime.getRuntime().freeMemory() > 10_000_000) {
                return new HealthCheckResult(HealthStatus.UP, getUptime(), details);
            } else {
                details.put("reason", "Low memory condition detected");
                return new HealthCheckResult(HealthStatus.DOWN, getUptime(), details);
            }
        } catch (Exception e) {
            LOGGER.error("Liveness probe failed", e);
            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());
            return new HealthCheckResult(HealthStatus.DOWN, getUptime(), details);
        }
    }

    /**
     * Readiness probe: Can the engine accept work?
     * Checks database connectivity, queue availability, worker threads.
     */
    public HealthCheckResult readiness() {
        try {
            Map<String, Object> details = new HashMap<>();

            // Check database connectivity
            boolean dbHealthy = delegate.isDatabaseHealthy();
            details.put("database_healthy", dbHealthy);

            // Check queue availability
            boolean queueHealthy = delegate.isQueueHealthy();
            details.put("queue_healthy", queueHealthy);

            // Check worker threads
            long activeThreads = delegate.getActiveWorkerThreads();
            details.put("active_workers", activeThreads);
            details.put("max_workers", delegate.getMaxWorkerThreads());

            // Check message queue depth
            long queueDepth = delegate.getQueueDepth();
            details.put("queue_depth", queueDepth);
            details.put("queue_capacity", delegate.getQueueCapacity());

            // Determine overall status
            if (!dbHealthy) {
                details.put("reason", "Database unhealthy");
                return new HealthCheckResult(HealthStatus.DOWN, getUptime(), details);
            }

            if (!queueHealthy) {
                details.put("reason", "Queue unhealthy");
                return new HealthCheckResult(HealthStatus.DOWN, getUptime(), details);
            }

            if (queueDepth > delegate.getQueueCapacity() * 0.9) {
                details.put("reason", "Queue near capacity");
                return new HealthCheckResult(HealthStatus.DEGRADED, getUptime(), details);
            }

            if (activeThreads == 0) {
                details.put("reason", "No active workers");
                return new HealthCheckResult(HealthStatus.DOWN, getUptime(), details);
            }

            return new HealthCheckResult(HealthStatus.UP, getUptime(), details);
        } catch (Exception e) {
            LOGGER.error("Readiness probe failed", e);
            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());
            details.put("reason", "Exception during readiness check");
            return new HealthCheckResult(HealthStatus.DOWN, getUptime(), details);
        }
    }

    /**
     * Startup probe: Has the engine finished initialization?
     * Used to prevent traffic during warmup period.
     * Waits for configuration loading, schema validation, etc.
     */
    public HealthCheckResult startup() {
        try {
            Map<String, Object> details = new HashMap<>();

            // Check if initialization complete
            boolean initComplete = delegate.isInitializationComplete();
            details.put("initialization_complete", initComplete);

            // Check startup metrics
            long warmupDuration = delegate.getWarmupDurationMs();
            details.put("warmup_duration_ms", warmupDuration);

            // Check schema validation
            boolean schemaValid = delegate.isSchemaValid();
            details.put("schema_valid", schemaValid);

            // Check case storage initialized
            boolean storageReady = delegate.isCaseStorageReady();
            details.put("case_storage_ready", storageReady);

            if (!initComplete) {
                details.put("reason", "Initialization in progress");
                return new HealthCheckResult(HealthStatus.DOWN, getUptime(), details);
            }

            if (!schemaValid) {
                details.put("reason", "YAWL schema validation failed");
                return new HealthCheckResult(HealthStatus.DOWN, getUptime(), details);
            }

            if (!storageReady) {
                details.put("reason", "Case storage not ready");
                return new HealthCheckResult(HealthStatus.DOWN, getUptime(), details);
            }

            return new HealthCheckResult(HealthStatus.UP, getUptime(), details);
        } catch (Exception e) {
            LOGGER.error("Startup probe failed", e);
            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());
            details.put("reason", "Exception during startup check");
            return new HealthCheckResult(HealthStatus.DOWN, getUptime(), details);
        }
    }

    /**
     * Gets HTTP status code for health result.
     * 200 for UP/DEGRADED, 503 for DOWN.
     */
    public int getHttpStatusCode(HealthCheckResult result) {
        return result.getStatus() == HealthStatus.DOWN ? 503 : 200;
    }

    /**
     * Converts health result to JSON string.
     */
    public String toJson(HealthCheckResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            LOGGER.error("Failed to serialize health check result", e);
            return "{}";
        }
    }

    private long getUptime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Delegate interface for health check queries.
     * Implemented by YAWL engine to provide subsystem status.
     */
    public interface HealthCheckDelegate {
        boolean isDatabaseHealthy();
        boolean isQueueHealthy();
        long getActiveWorkerThreads();
        long getMaxWorkerThreads();
        long getQueueDepth();
        long getQueueCapacity();
        boolean isInitializationComplete();
        long getWarmupDurationMs();
        boolean isSchemaValid();
        boolean isCaseStorageReady();
    }
}
