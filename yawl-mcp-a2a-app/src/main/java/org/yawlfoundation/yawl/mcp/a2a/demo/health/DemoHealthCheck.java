package org.yawlfoundation.yawl.mcp.a2a.demo.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health check for demo execution status.
 */
public class DemoHealthCheck {
    private static final Logger LOG = LoggerFactory.getLogger(DemoHealthCheck.class);

    private volatile boolean initialized = false;
    private volatile boolean shutdownRequested = false;
    private volatile int patternsTotal = 0;
    private volatile int patternsCompleted = 0;
    private volatile int patternsFailed = 0;
    private volatile long lastPatternTime = 0;

    public void markInitialized() {
        this.initialized = true;
    }

    public void setTotalPatterns(int total) {
        this.patternsTotal = total;
    }

    public void incrementCompleted() {
        this.patternsCompleted++;
        this.lastPatternTime = System.currentTimeMillis();
    }

    public void incrementFailed() {
        this.patternsFailed++;
    }

    public void markShutdownRequested() {
        this.shutdownRequested = true;
    }

    public HealthStatus check() {
        Map<String, Object> details = new LinkedHashMap<>();

        // Check initialization
        if (!initialized) {
            details.put("status", "INITIALIZING");
            details.put("message", "Demo runner not yet initialized");
            return new HealthStatus(Status.STARTING, details);
        }

        // Check shutdown
        if (shutdownRequested) {
            details.put("status", "SHUTTING_DOWN");
            details.put("patterns_completed", patternsCompleted);
            details.put("patterns_total", patternsTotal);
            return new HealthStatus(Status.STOPPING, details);
        }

        // Calculate progress
        double progress = patternsTotal > 0
            ? (patternsCompleted * 100.0 / patternsTotal)
            : 0;

        details.put("patterns_total", patternsTotal);
        details.put("patterns_completed", patternsCompleted);
        details.put("patterns_failed", patternsFailed);
        details.put("progress_percent", String.format("%.1f", progress));
        details.put("last_pattern_time", lastPatternTime);

        // Determine health
        Status status = Status.HEALTHY;
        if (patternsFailed > 0 && patternsFailed > patternsCompleted * 0.1) {
            status = Status.DEGRADED;
            details.put("warning", "High failure rate: " + patternsFailed + " failed");
        }

        return new HealthStatus(status, details);
    }

    public enum Status {
        STARTING,
        HEALTHY,
        DEGRADED,
        STOPPING
    }

    public record HealthStatus(Status status, Map<String, Object> details) {
        public boolean isHealthy() {
            return status == Status.HEALTHY || status == Status.STARTING;
        }
    }
}