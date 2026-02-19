package org.yawlfoundation.yawl.mcp.a2a.service.metrics;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

/**
 * Health indicator for YAWL MCP-A2A application components.
 *
 * <p>Provides comprehensive health checks for:</p>
 * <ul>
 *   <li>MCP server status</li>
 *   <li>A2A agent status</li>
 *   <li>YAWL engine connectivity</li>
 *   <li>Connection pool health</li>
 * </ul>
 *
 * <h2>Health Check Response</h2>
 * <p>Returns detailed health information including:</p>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "components": {
 *     "mcpServer": {"status": "UP", "details": {"active_tools": 15}},
 *     "a2aAgent": {"status": "UP", "details": {"active_connections": 3}},
 *     "yawlEngine": {"status": "UP", "details": {"connected": true}},
 *     "connectionPool": {"status": "UP", "details": {"active": 5, "idle": 10}}
 *   }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Component("yawlMcpA2a")
public class YawlHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(YawlHealthIndicator.class);

    /** Health status for MCP server */
    private final AtomicReference<ComponentHealth> mcpServerHealth =
        new AtomicReference<>(ComponentHealth.unknown("mcpServer"));

    /** Health status for A2A agent */
    private final AtomicReference<ComponentHealth> a2aAgentHealth =
        new AtomicReference<>(ComponentHealth.unknown("a2aAgent"));

    /** Health status for YAWL engine connection */
    private final AtomicReference<ComponentHealth> yawlEngineHealth =
        new AtomicReference<>(ComponentHealth.unknown("yawlEngine"));

    /** Health status for connection pool */
    private final AtomicReference<ComponentHealth> connectionPoolHealth =
        new AtomicReference<>(ComponentHealth.unknown("connectionPool"));

    @Nullable
    @Autowired
    private MetricsService metricsService;

    /**
     * Check overall health of all YAWL MCP-A2A components.
     *
     * @return Health object with status and details for all components
     */
    @Override
    public Health health() {
        Health.Builder builder = Health.unknown();

        // Check each component
        boolean allHealthy = true;

        // MCP Server health
        ComponentHealth mcpHealth = mcpServerHealth.get();
        builder.withDetail("mcpServer", mcpHealth.toMap());
        if (!mcpHealth.isHealthy()) {
            allHealthy = false;
        }

        // A2A Agent health
        ComponentHealth a2aHealth = a2aAgentHealth.get();
        builder.withDetail("a2aAgent", a2aHealth.toMap());
        if (!a2aHealth.isHealthy()) {
            allHealthy = false;
        }

        // YAWL Engine health
        ComponentHealth engineHealth = yawlEngineHealth.get();
        builder.withDetail("yawlEngine", engineHealth.toMap());
        if (!engineHealth.isHealthy()) {
            allHealthy = false;
        }

        // Connection Pool health
        ComponentHealth poolHealth = connectionPoolHealth.get();
        builder.withDetail("connectionPool", poolHealth.toMap());
        if (!poolHealth.isHealthy()) {
            allHealthy = false;
        }

        // Set overall status
        if (allHealthy) {
            builder.up();
        } else {
            builder.down();
        }

        // Update metrics
        if (metricsService != null) {
            metricsService.updateHealthStatus("mcp_server", mcpHealth.isHealthy());
            metricsService.updateHealthStatus("a2a_agent", a2aHealth.isHealthy());
            metricsService.updateHealthStatus("yawl_engine", engineHealth.isHealthy());
            metricsService.updateHealthStatus("connection_pool", poolHealth.isHealthy());
        }

        return builder.build();
    }

    /**
     * Update MCP server health status.
     *
     * @param healthy true if healthy
     * @param message status message
     * @param activeTools number of active MCP tools
     */
    public void updateMcpServerHealth(boolean healthy, @Nullable String message, int activeTools) {
        ComponentHealth health = new ComponentHealth(
            "mcpServer",
            healthy,
            message != null ? message : (healthy ? "MCP server is running" : "MCP server is unhealthy"),
            System.currentTimeMillis(),
            Map.of("active_tools", activeTools)
        );
        mcpServerHealth.set(health);
        logger.debug("MCP server health updated: {}", health);
    }

    /**
     * Update A2A agent health status.
     *
     * @param healthy true if healthy
     * @param message status message
     * @param activeConnections number of active A2A connections
     */
    public void updateA2aAgentHealth(boolean healthy, @Nullable String message, int activeConnections) {
        ComponentHealth health = new ComponentHealth(
            "a2aAgent",
            healthy,
            message != null ? message : (healthy ? "A2A agent is running" : "A2A agent is unhealthy"),
            System.currentTimeMillis(),
            Map.of("active_connections", activeConnections)
        );
        a2aAgentHealth.set(health);
        logger.debug("A2A agent health updated: {}", health);
    }

    /**
     * Update YAWL engine connection health.
     *
     * @param healthy true if healthy
     * @param message status message
     * @param connected true if connected to engine
     */
    public void updateYawlEngineHealth(boolean healthy, @Nullable String message, boolean connected) {
        ComponentHealth health = new ComponentHealth(
            "yawlEngine",
            healthy,
            message != null ? message : (healthy ? "Connected to YAWL engine" : "Not connected to YAWL engine"),
            System.currentTimeMillis(),
            Map.of("connected", connected)
        );
        yawlEngineHealth.set(health);
        logger.debug("YAWL engine health updated: {}", health);
    }

    /**
     * Update connection pool health.
     *
     * @param healthy true if healthy
     * @param message status message
     * @param active active connections
     * @param idle idle connections
     */
    public void updateConnectionPoolHealth(boolean healthy, @Nullable String message, int active, int idle) {
        ComponentHealth health = new ComponentHealth(
            "connectionPool",
            healthy,
            message != null ? message : (healthy ? "Connection pool is healthy" : "Connection pool is unhealthy"),
            System.currentTimeMillis(),
            Map.of("active", active, "idle", idle, "total", active + idle)
        );
        connectionPoolHealth.set(health);
        logger.debug("Connection pool health updated: {}", health);
    }

    /**
     * Get current MCP server health.
     *
     * @return current MCP server health status
     */
    public ComponentHealth getMcpServerHealth() {
        return mcpServerHealth.get();
    }

    /**
     * Get current A2A agent health.
     *
     * @return current A2A agent health status
     */
    public ComponentHealth getA2aAgentHealth() {
        return a2aAgentHealth.get();
    }

    /**
     * Get current YAWL engine health.
     *
     * @return current YAWL engine health status
     */
    public ComponentHealth getYawlEngineHealth() {
        return yawlEngineHealth.get();
    }

    /**
     * Get current connection pool health.
     *
     * @return current connection pool health status
     */
    public ComponentHealth getConnectionPoolHealth() {
        return connectionPoolHealth.get();
    }

    /**
     * Immutable health status for a component.
     */
    public static final class ComponentHealth {
        private final String componentName;
        private final boolean healthy;
        private final String message;
        private final long timestamp;
        private final Map<String, Object> details;

        public ComponentHealth(
                String componentName,
                boolean healthy,
                String message,
                long timestamp,
                Map<String, Object> details) {
            this.componentName = componentName;
            this.healthy = healthy;
            this.message = message;
            this.timestamp = timestamp;
            this.details = details != null ? Map.copyOf(details) : Map.of();
        }

        /**
         * Create an unknown health status.
         */
        public static ComponentHealth unknown(String componentName) {
            return new ComponentHealth(
                componentName,
                false,
                "Health status unknown",
                System.currentTimeMillis(),
                Map.of()
            );
        }

        public String getComponentName() {
            return componentName;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Map<String, Object> getDetails() {
            return details;
        }

        /**
         * Convert to a map for JSON serialization.
         */
        public Map<String, Object> toMap() {
            return Map.of(
                "status", healthy ? "UP" : "DOWN",
                "message", message,
                "timestamp", timestamp,
                "details", details
            );
        }

        @Override
        public String toString() {
            return String.format("ComponentHealth{name=%s, healthy=%s, message=%s}",
                componentName, healthy, message);
        }
    }
}
