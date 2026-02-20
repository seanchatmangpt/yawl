package org.yawlfoundation.yawl.integration.observability;

import org.yawlfoundation.yawl.observability.CustomMetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics listener for MCP client operations.
 *
 * Records:
 * - MCP call latency (histogram with 100ms, 500ms, 1s, 5s, 30s buckets)
 * - Success vs failure counts
 * - Active connection gauge
 * - Detailed percentile tracking (p50, p95, p99)
 *
 * Integrates with CustomMetricsRegistry to populate advanced Prometheus metrics.
 */
public final class McpClientMetricsListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpClientMetricsListener.class);

    private final AtomicLong callStartTime = new AtomicLong(0);
    private final String clientName;

    /**
     * Creates a new listener for MCP client operations.
     *
     * @param clientName descriptive name for the MCP client (e.g., "mcp-agents", "mcp-tools")
     */
    public McpClientMetricsListener(String clientName) {
        this.clientName = clientName;
        LOGGER.info("McpClientMetricsListener initialized for client: {}", clientName);
    }

    /**
     * Records the start of an MCP client call.
     * Must be paired with recordCallComplete() or recordCallFailure().
     *
     * @return time in milliseconds when the call started
     */
    public long startCall() {
        long startTime = System.currentTimeMillis();
        callStartTime.set(startTime);
        return startTime;
    }

    /**
     * Records a successful MCP client call with latency measurement.
     *
     * @param startTime the time when the call started (from startCall())
     */
    public void recordCallSuccess(long startTime) {
        long endTime = System.currentTimeMillis();
        long latencyMs = endTime - startTime;

        LOGGER.debug("MCP client '{}' call succeeded (latency: {}ms)", clientName, latencyMs);

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.recordMcpClientCallLatency(latencyMs);
            metrics.incrementMcpClientCallSuccess();
        } catch (IllegalStateException e) {
            LOGGER.warn("CustomMetricsRegistry not initialized, skipping success metrics", e);
        }
    }

    /**
     * Records a failed MCP client call with latency measurement.
     *
     * @param startTime the time when the call started (from startCall())
     */
    public void recordCallFailure(long startTime) {
        long endTime = System.currentTimeMillis();
        long latencyMs = endTime - startTime;

        LOGGER.warn("MCP client '{}' call failed (latency: {}ms)", clientName, latencyMs);

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.recordMcpClientCallLatency(latencyMs);
            metrics.incrementMcpClientCallFailure();
        } catch (IllegalStateException e) {
            LOGGER.warn("CustomMetricsRegistry not initialized, skipping failure metrics", e);
        }
    }

    /**
     * Records a successful MCP client call using elapsed time.
     *
     * @param latencyMs the elapsed time in milliseconds
     */
    public void recordCallSuccessWithLatency(long latencyMs) {
        LOGGER.debug("MCP client '{}' call succeeded (latency: {}ms)", clientName, latencyMs);

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.recordMcpClientCallLatency(latencyMs);
            metrics.incrementMcpClientCallSuccess();
        } catch (IllegalStateException e) {
            LOGGER.warn("CustomMetricsRegistry not initialized, skipping success metrics", e);
        }
    }

    /**
     * Records a failed MCP client call using elapsed time.
     *
     * @param latencyMs the elapsed time in milliseconds
     */
    public void recordCallFailureWithLatency(long latencyMs) {
        LOGGER.warn("MCP client '{}' call failed (latency: {}ms)", clientName, latencyMs);

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.recordMcpClientCallLatency(latencyMs);
            metrics.incrementMcpClientCallFailure();
        } catch (IllegalStateException e) {
            LOGGER.warn("CustomMetricsRegistry not initialized, skipping failure metrics", e);
        }
    }

    /**
     * Updates the active connection count.
     *
     * @param activeConnections the current number of active connections
     */
    public void setActiveConnections(long activeConnections) {
        LOGGER.debug("MCP client '{}' active connections updated: {}", clientName, activeConnections);

        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            metrics.setMcpClientActiveConnections(activeConnections);
        } catch (IllegalStateException e) {
            LOGGER.debug("CustomMetricsRegistry not initialized, skipping connection update", e);
        }
    }

    /**
     * Increments active connection count by 1.
     */
    public void incrementActiveConnections() {
        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            long current = metrics.getMcpClientActiveConnections();
            metrics.setMcpClientActiveConnections(current + 1);
        } catch (IllegalStateException e) {
            LOGGER.debug("CustomMetricsRegistry not initialized, skipping connection increment", e);
        }
    }

    /**
     * Decrements active connection count by 1.
     */
    public void decrementActiveConnections() {
        try {
            CustomMetricsRegistry metrics = CustomMetricsRegistry.getInstance();
            long current = metrics.getMcpClientActiveConnections();
            if (current > 0) {
                metrics.setMcpClientActiveConnections(current - 1);
            }
        } catch (IllegalStateException e) {
            LOGGER.debug("CustomMetricsRegistry not initialized, skipping connection decrement", e);
        }
    }

    /**
     * Gets the client name.
     */
    public String getClientName() {
        return clientName;
    }
}
