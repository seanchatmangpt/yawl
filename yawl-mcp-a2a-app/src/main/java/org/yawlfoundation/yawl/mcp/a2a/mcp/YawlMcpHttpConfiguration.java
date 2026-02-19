package org.yawlfoundation.yawl.mcp.a2a.mcp;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Spring configuration for YAWL MCP HTTP/SSE transport.
 *
 * <p>This configuration class sets up the HTTP transport components for the MCP server,
 * including:</p>
 * <ul>
 *   <li>Router function for SSE and message endpoints</li>
 *   <li>Health check endpoint for load balancer probes</li>
 *   <li>Session management for concurrent connections</li>
 *   <li>Lifecycle management for graceful shutdown</li>
 * </ul>
 *
 * <h2>Activation</h2>
 * <p>This configuration is activated when the property {@code yawl.mcp.http.enabled}
 * is set to {@code true}:</p>
 * <pre>{@code
 * yawl:
 *   mcp:
 *     http:
 *       enabled: true
 *       port: 8081
 *       path: /mcp
 * }</pre>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /mcp/sse} - SSE connection for server-to-client messages</li>
 *   <li>{@code POST /mcp/message} - JSON-RPC message endpoint</li>
 *   <li>{@code GET /mcp/health} - Health check for load balancers</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see McpTransportConfig
 * @see YawlMcpHttpServer
 */
@Configuration
@EnableConfigurationProperties(McpTransportConfig.class)
@ConditionalOnProperty(prefix = "yawl.mcp.http", name = "enabled", havingValue = "true")
public class YawlMcpHttpConfiguration {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpHttpConfiguration.class.getName());

    /**
     * Active MCP sessions for health monitoring.
     */
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    /**
     * Creates the MCP HTTP server bean.
     *
     * <p>The server is configured from application properties and environment variables.
     * It connects to the YAWL engine on startup and disconnects on shutdown.</p>
     *
     * @param config the transport configuration from Spring properties
     * @return the configured MCP HTTP server
     * @throws IllegalStateException if YAWL engine connection fails
     */
    @Bean
    public YawlMcpHttpServer yawlMcpHttpServer(McpTransportConfig config) {
        String engineUrl = getRequiredEnv("YAWL_ENGINE_URL", "http://localhost:8080/yawl");
        String username = getRequiredEnv("YAWL_USERNAME", "admin");
        String password = getRequiredEnv("YAWL_PASSWORD", "YAWL");

        LOGGER.info("Creating YAWL MCP HTTP Server with config: " + config);

        YawlMcpHttpServer server = new YawlMcpHttpServer(engineUrl, username, password, config);

        try {
            server.start();
            LOGGER.info("YAWL MCP HTTP Server started successfully");
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to start YAWL MCP HTTP Server: " + e.getMessage(), e);
        }

        return server;
    }

    /**
     * Creates the router function for MCP HTTP endpoints.
     *
     * <p>This router combines:</p>
     * <ul>
     *   <li>MCP transport routes (SSE and message) from the transport provider</li>
     *   <li>Health check endpoint for monitoring</li>
     * </ul>
     *
     * @param server the MCP HTTP server
     * @param config the transport configuration
     * @return combined router function for all MCP endpoints
     */
    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(
            YawlMcpHttpServer server,
            McpTransportConfig config) {

        RouterFunction<ServerResponse> transportRouter = server.getRouterFunction();

        RouterFunctions.Builder builder = RouterFunctions.route();

        // Add health check endpoint if enabled
        if (config.enableHealthCheck()) {
            builder.GET(config.healthCheckPath(), this::handleHealthCheck);
            LOGGER.info("Health check endpoint configured: " + config.healthCheckPath());
        }

        // Add session info endpoint for monitoring
        builder.GET(config.path() + "/sessions", this::handleSessionsList);

        // Combine with transport router if available
        if (transportRouter != null) {
            builder.add(transportRouter);
        }

        return builder.build();
    }

    /**
     * Creates the logging handler bean for MCP notifications.
     *
     * @return the logging handler
     */
    @Bean
    public McpLoggingHandler mcpLoggingHandler() {
        return new McpLoggingHandler();
    }

    // =========================================================================
    // Health check handlers
    // =========================================================================

    /**
     * Handle health check requests.
     *
     * <p>Returns HTTP 200 with JSON status if the server is healthy,
     * or HTTP 503 if the server is unhealthy.</p>
     *
     * @param request the incoming request
     * @return health status response
     */
    private ServerResponse handleHealthCheck(ServerRequest request) {
        boolean healthy = activeSessions.size() < getMaxSessions();

        if (healthy) {
            return ServerResponse.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(buildHealthResponse("healthy", activeSessions.size()));
        } else {
            return ServerResponse.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(buildHealthResponse("unhealthy - max sessions reached", activeSessions.size()));
        }
    }

    /**
     * Handle sessions list requests for monitoring.
     *
     * @param request the incoming request
     * @return sessions list response
     */
    private ServerResponse handleSessionsList(ServerRequest request) {
        return ServerResponse.ok()
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(buildSessionsResponse());
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private String getRequiredEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            if (defaultValue == null) {
                throw new IllegalStateException(
                    "Required environment variable " + name + " is not set");
            }
            LOGGER.info("Using default value for " + name + ": " + defaultValue);
            return defaultValue;
        }
        return value;
    }

    private int getMaxSessions() {
        return 100; // Default max sessions
    }

    private String buildHealthResponse(String status, int sessionCount) {
        return """
            {
              "status": "%s",
              "server": "yawl-mcp-http-server",
              "version": "6.0.0",
              "activeSessions": %d,
              "timestamp": "%s"
            }
            """.formatted(status, sessionCount, java.time.Instant.now().toString());
    }

    private String buildSessionsResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"sessions\": [");

        boolean first = true;
        for (Map.Entry<String, SessionInfo> entry : activeSessions.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append("{\"id\": \"")
              .append(entry.getKey())
              .append("\", \"connectedAt\": \"")
              .append(entry.getValue().connectedAt())
              .append("\"}");
        }

        sb.append("], \"count\": ")
          .append(activeSessions.size())
          .append("}");

        return sb.toString();
    }

    /**
     * Register a new session.
     *
     * @param sessionId the session identifier
     */
    public void registerSession(String sessionId) {
        activeSessions.put(sessionId, new SessionInfo(java.time.Instant.now().toString()));
        LOGGER.fine("Session registered: " + sessionId);
    }

    /**
     * Unregister a session.
     *
     * @param sessionId the session identifier
     */
    public void unregisterSession(String sessionId) {
        activeSessions.remove(sessionId);
        LOGGER.fine("Session unregistered: " + sessionId);
    }

    /**
     * Record for session information.
     */
    record SessionInfo(String connectedAt) {}
}
