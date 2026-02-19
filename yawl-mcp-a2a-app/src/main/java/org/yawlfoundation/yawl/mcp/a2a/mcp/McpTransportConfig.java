package org.yawlfoundation.yawl.mcp.a2a.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration properties for MCP HTTP/SSE transport.
 *
 * <p>Binds to {@code yawl.mcp.http.*} properties in application.yml.
 * Controls HTTP server settings, SSE connections, and dual-transport mode.</p>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * yawl:
 *   mcp:
 *     http:
 *       enabled: true
 *       port: 8081
 *       path: /mcp
 *       sse-path: /mcp/sse
 *       message-path: /mcp/message
 *       max-connections: 100
 *       connection-timeout-seconds: 300
 *       enable-stdio: true
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Validated
@ConfigurationProperties(prefix = "yawl.mcp.http")
public record McpTransportConfig(

    /**
     * Enable HTTP/SSE transport for MCP server.
     * When enabled, the server listens on the configured HTTP port
     * for MCP JSON-RPC messages over HTTP POST and SSE streams.
     *
     * @return true if HTTP transport is enabled
     */
    boolean enabled,

    /**
     * HTTP server port for MCP transport.
     * Default: 8081 (to avoid conflict with main application port 8080)
     *
     * @return the HTTP server port
     */
    @Min(1024) @Max(65535)
    int port,

    /**
     * Base path for MCP HTTP endpoints.
     * All MCP endpoints are rooted at this path.
     * Default: /mcp
     *
     * @return the base path for MCP endpoints
     */
    @NotBlank
    String path,

    /**
     * Path for Server-Sent Events (SSE) endpoint.
     * Clients connect here to receive server-to-client messages.
     * Full URL: {base-path}{sse-path}
     * Default: /sse (resulting in /mcp/sse)
     *
     * @return the SSE endpoint path
     */
    @NotBlank
    String ssePath,

    /**
     * Path for HTTP POST message endpoint.
     * Clients POST JSON-RPC messages here.
     * Full URL: {base-path}{message-path}
     * Default: /message (resulting in /mcp/message)
     *
     * @return the message endpoint path
     */
    @NotBlank
    String messagePath,

    /**
     * Maximum concurrent SSE connections.
     * When limit is reached, new connections receive 503 Service Unavailable.
     * Default: 100
     *
     * @return maximum number of concurrent connections
     */
    @Min(1) @Max(10000)
    int maxConnections,

    /**
     * Connection timeout in seconds for SSE connections.
     * Connections are closed after this period of inactivity.
     * Default: 300 (5 minutes)
     *
     * @return connection timeout in seconds
     */
    @Min(30) @Max(86400)
    int connectionTimeoutSeconds,

    /**
     * Enable STDIO transport alongside HTTP.
     * When true, both STDIO and HTTP transports are active.
     * When false, only HTTP transport is active (requires enabled=true).
     * Default: true
     *
     * @return true if STDIO transport should also be enabled
     */
    boolean enableStdio,

    /**
     * Enable health check endpoint at /health.
     * Returns HTTP 200 with server status when healthy.
     * Default: true
     *
     * @return true if health check endpoint is enabled
     */
    boolean enableHealthCheck,

    /**
     * Heartbeat interval in seconds for SSE connections.
     * Sends keep-alive comments to prevent connection timeout.
     * Default: 30 seconds
     *
     * @return heartbeat interval in seconds
     */
    @Min(10) @Max(300)
    int heartbeatIntervalSeconds

) {

    /**
     * Default configuration values.
     */
    public static final int DEFAULT_PORT = 8081;
    public static final String DEFAULT_PATH = "/mcp";
    public static final String DEFAULT_SSE_PATH = "/sse";
    public static final String DEFAULT_MESSAGE_PATH = "/message";
    public static final int DEFAULT_MAX_CONNECTIONS = 100;
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;
    public static final int DEFAULT_HEARTBEAT_SECONDS = 30;
    public static final String ROOT_PATH = "/";

    /**
     * Creates default configuration with HTTP transport enabled.
     *
     * @return default McpTransportConfig instance
     */
    public static McpTransportConfig defaults() {
        return new McpTransportConfig(
            true,
            DEFAULT_PORT,
            DEFAULT_PATH,
            DEFAULT_SSE_PATH,
            DEFAULT_MESSAGE_PATH,
            DEFAULT_MAX_CONNECTIONS,
            DEFAULT_TIMEOUT_SECONDS,
            true,
            true,
            DEFAULT_HEARTBEAT_SECONDS
        );
    }

    /**
     * Creates configuration with HTTP transport disabled (STDIO only).
     *
     * @return STDIO-only McpTransportConfig instance
     */
    public static McpTransportConfig stdioOnly() {
        return new McpTransportConfig(
            false,
            DEFAULT_PORT,
            DEFAULT_PATH,
            DEFAULT_SSE_PATH,
            DEFAULT_MESSAGE_PATH,
            DEFAULT_MAX_CONNECTIONS,
            DEFAULT_TIMEOUT_SECONDS,
            true,
            false,
            DEFAULT_HEARTBEAT_SECONDS
        );
    }

    /**
     * Gets the full SSE endpoint path.
     *
     * @return combined base path and SSE path
     */
    public String fullSsePath() {
        return normalizePath(path) + normalizePath(ssePath);
    }

    /**
     * Gets the full message endpoint path.
     *
     * @return combined base path and message path
     */
    public String fullMessagePath() {
        return normalizePath(path) + normalizePath(messagePath);
    }

    /**
     * Gets the health check endpoint path.
     *
     * @return health check path
     */
    public String healthCheckPath() {
        return normalizePath(path) + "/health";
    }

    /**
     * Normalizes a path string to ensure it starts with a forward slash.
     * Returns root path "/" for null or empty input to ensure valid URL construction.
     *
     * @param p the path to normalize
     * @return normalized path starting with "/"
     */
    private static String normalizePath(String p) {
        if (p == null || p.isEmpty()) {
            return ROOT_PATH;
        }
        if (!p.startsWith("/")) {
            return "/" + p;
        }
        return p;
    }
}
