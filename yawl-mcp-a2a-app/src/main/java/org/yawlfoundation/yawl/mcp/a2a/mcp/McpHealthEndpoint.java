package org.yawlfoundation.yawl.mcp.a2a.mcp;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check and metrics endpoint for MCP HTTP transport.
 *
 * <p>Provides REST endpoints for:</p>
 * <ul>
 *   <li>Load balancer health checks</li>
 *   <li>Kubernetes liveness/readiness probes</li>
 *   <li>Connection metrics and statistics</li>
 * </ul>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /mcp/health} - Basic health status</li>
 *   <li>{@code GET /mcp/health/live} - Kubernetes liveness probe</li>
 *   <li>{@code GET /mcp/health/ready} - Kubernetes readiness probe</li>
 *   <li>{@code GET /mcp/metrics} - Connection metrics</li>
 * </ul>
 *
 * <h2>Integration</h2>
 * <p>Configure Kubernetes probes:</p>
 * <pre>{@code
 * livenessProbe:
 *   httpGet:
 *     path: /mcp/health/live
 *     port: 8081
 * readinessProbe:
 *   httpGet:
 *     path: /mcp/health/ready
 *     port: 8081
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@RestController
@RequestMapping("${yawl.mcp.http.path:/mcp}")
public class McpHealthEndpoint {

    private static final String SERVER_VERSION = "6.0.0";
    private static final String SERVER_NAME = "yawl-mcp-http-server";

    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong errors = new AtomicLong(0);
    private final AtomicReference<Instant> startTime = new AtomicReference<>(Instant.now());
    private final AtomicReference<Instant> lastActivity = new AtomicReference<>(Instant.now());
    private final AtomicReference<String> engineStatus = new AtomicReference<>("unknown");

    private final McpTransportConfig config;
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    /**
     * Construct the health endpoint with configuration.
     *
     * @param config the transport configuration
     */
    public McpHealthEndpoint(McpTransportConfig config) {
        this.config = config;
    }

    /**
     * Basic health check endpoint.
     *
     * @return health status response
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean healthy = isHealthy();
        Map<String, Object> response = Map.of(
            "status", healthy ? "healthy" : "unhealthy",
            "server", SERVER_NAME,
            "version", SERVER_VERSION,
            "activeConnections", activeConnections.get(),
            "maxConnections", config.maxConnections(),
            "uptimeSeconds", java.time.Duration.between(startTime.get(), Instant.now()).getSeconds(),
            "engineStatus", engineStatus.get(),
            "timestamp", Instant.now().toString()
        );

        return healthy
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(503).body(response);
    }

    /**
     * Kubernetes liveness probe endpoint.
     *
     * <p>Returns 200 if the server process is alive, regardless of engine connection.</p>
     *
     * @return liveness status
     */
    @GetMapping("/health/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        return ResponseEntity.ok(Map.of(
            "status", "alive",
            "server", SERVER_NAME,
            "timestamp", Instant.now().toString()
        ));
    }

    /**
     * Kubernetes readiness probe endpoint.
     *
     * <p>Returns 200 only if the server is ready to accept connections
     * (engine connected and not at max capacity).</p>
     *
     * @return readiness status
     */
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        boolean ready = isReady();
        Map<String, Object> response = Map.of(
            "status", ready ? "ready" : "not_ready",
            "engineConnected", "connected".equals(engineStatus.get()),
            "connectionsAvailable", activeConnections.get() < config.maxConnections(),
            "timestamp", Instant.now().toString()
        );

        return ready
            ? ResponseEntity.ok(response)
            : ResponseEntity.status(503).body(response);
    }

    /**
     * Connection metrics endpoint.
     *
     * @return detailed metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        Map<String, Object> metrics = Map.of(
            "server", SERVER_NAME,
            "version", SERVER_VERSION,
            "connections", Map.of(
                "active", activeConnections.get(),
                "total", totalConnections.get(),
                "max", config.maxConnections()
            ),
            "messages", Map.of(
                "received", messagesReceived.get(),
                "sent", messagesSent.get()
            ),
            "errors", errors.get(),
            "uptime", Map.of(
                "startTime", startTime.get().toString(),
                "uptimeSeconds", java.time.Duration.between(startTime.get(), Instant.now()).getSeconds(),
                "lastActivity", lastActivity.get().toString()
            ),
            "config", Map.of(
                "port", config.port(),
                "path", config.path(),
                "ssePath", config.fullSsePath(),
                "messagePath", config.fullMessagePath(),
                "heartbeatIntervalSeconds", config.heartbeatIntervalSeconds(),
                "connectionTimeoutSeconds", config.connectionTimeoutSeconds()
            )
        );

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(metrics);
    }

    // =========================================================================
    // Status update methods (called by server)
    // =========================================================================

    /**
     * Record a new connection.
     */
    public void recordConnection() {
        activeConnections.incrementAndGet();
        totalConnections.incrementAndGet();
        lastActivity.set(Instant.now());
    }

    /**
     * Record a disconnection.
     */
    public void recordDisconnection() {
        activeConnections.decrementAndGet();
        lastActivity.set(Instant.now());
    }

    /**
     * Record a received message.
     */
    public void recordMessageReceived() {
        messagesReceived.incrementAndGet();
        lastActivity.set(Instant.now());
    }

    /**
     * Record a sent message.
     */
    public void recordMessageSent() {
        messagesSent.incrementAndGet();
        lastActivity.set(Instant.now());
    }

    /**
     * Record an error.
     */
    public void recordError() {
        errors.incrementAndGet();
        lastActivity.set(Instant.now());
    }

    /**
     * Update engine connection status.
     *
     * @param status the engine status (connected, disconnected, error)
     */
    public void setEngineStatus(String status) {
        engineStatus.set(status);
    }

    /**
     * Register a session.
     *
     * @param sessionId the session ID
     */
    public void registerSession(String sessionId) {
        sessions.put(sessionId, Instant.now());
        recordConnection();
    }

    /**
     * Unregister a session.
     *
     * @param sessionId the session ID
     */
    public void unregisterSession(String sessionId) {
        sessions.remove(sessionId);
        recordDisconnection();
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private boolean isHealthy() {
        return activeConnections.get() < config.maxConnections()
            && "connected".equals(engineStatus.get());
    }

    private boolean isReady() {
        return "connected".equals(engineStatus.get())
            && activeConnections.get() < config.maxConnections();
    }
}
