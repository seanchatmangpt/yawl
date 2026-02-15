package org.yawlfoundation.yawl.integration.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.LoggingLevel;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import org.yawlfoundation.yawl.integration.mcp.transport.SseServletTransportProvider;
import org.yawlfoundation.yawl.integration.mcp.transport.StreamableHttpTransportProvider;
import org.yawlfoundation.yawl.integration.mcp.transport.StatelessHttpTransportProvider;
import org.yawlfoundation.yawl.integration.mcp.server.YawlMcpSyncServer;

import java.time.Duration;

/**
 * MCP Server Configuration for YAWL.
 *
 * Manages configuration settings for the YAWL MCP server including:
 * - YAWL engine connection settings
 * - Transport type selection
 * - Logging configuration
 *
 * Configuration can be loaded from environment variables or set programmatically.
 *
 * Environment Variables:
 * - YAWL_ENGINE_URL: YAWL engine URL (default: http://localhost:8080/yawl/ib)
 * - YAWL_USERNAME: YAWL username (default: admin)
 * - YAWL_PASSWORD: YAWL password (default: YAWL)
 * - MCP_TRANSPORT: Transport type (default: SSE)
 * - MCP_PORT: Server port (default: 3000)
 * - MCP_ENDPOINT: MCP endpoint path (default: /mcp)
 * - MCP_LOG_LEVEL: Log level (default: INFO)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpServerConfiguration {

    private String yawlEngineUrl;
    private String yawlUsername;
    private String yawlPassword;
    private TransportType transportType;
    private int port;
    private String mcpEndpoint;
    private LoggingLevel logLevel;
    private Duration keepAliveInterval;
    private ObjectMapper objectMapper;

    /**
     * Transport type enumeration.
     */
    public enum TransportType {
        STDIO,
        SSE,
        STREAMABLE_HTTP,
        STATELESS_HTTP
    }

    /**
     * Creates a new configuration with defaults.
     */
    public McpServerConfiguration() {
        this.yawlEngineUrl = "http://localhost:8080/yawl/ib";
        this.yawlUsername = "admin";
        this.yawlPassword = "YAWL";
        this.transportType = TransportType.SSE;
        this.port = 3000;
        this.mcpEndpoint = "/mcp";
        this.logLevel = LoggingLevel.INFO;
        this.keepAliveInterval = Duration.ofSeconds(30);
        this.objectMapper = createObjectMapper();
    }

    /**
     * Creates configuration from environment variables.
     *
     * @return the configuration
     */
    public static McpServerConfiguration fromEnvironment() {
        McpServerConfiguration config = new McpServerConfiguration();

        config.yawlEngineUrl = getEnv("YAWL_ENGINE_URL", config.yawlEngineUrl);
        config.yawlUsername = getEnv("YAWL_USERNAME", config.yawlUsername);
        config.yawlPassword = getEnv("YAWL_PASSWORD", config.yawlPassword);

        String transportStr = getEnv("MCP_TRANSPORT", "SSE").toUpperCase();
        try {
            config.transportType = TransportType.valueOf(transportStr);
        } catch (IllegalArgumentException e) {
            config.transportType = TransportType.SSE;
        }

        config.port = Integer.parseInt(getEnv("MCP_PORT", String.valueOf(config.port)));
        config.mcpEndpoint = getEnv("MCP_ENDPOINT", config.mcpEndpoint);

        String logLevelStr = getEnv("MCP_LOG_LEVEL", "INFO").toUpperCase();
        try {
            config.logLevel = LoggingLevel.valueOf(logLevelStr);
        } catch (IllegalArgumentException e) {
            config.logLevel = LoggingLevel.INFO;
        }

        String keepAliveStr = getEnv("MCP_KEEPALIVE", "30");
        config.keepAliveInterval = Duration.ofSeconds(Long.parseLong(keepAliveStr));

        return config;
    }

    /**
     * Gets an environment variable with a default value.
     *
     * @param name the variable name
     * @param defaultValue the default value
     * @return the value
     */
    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }

    /**
     * Creates the MCP server with this configuration.
     *
     * @return a new YawlMcpSyncServer instance
     * @throws Exception if server creation fails
     */
    public YawlMcpSyncServer createServer() throws Exception {
        McpServerTransportProvider transport = createTransport();
        return new YawlMcpSyncServer(
                transport,
                yawlEngineUrl,
                yawlUsername,
                yawlPassword
        );
    }

    /**
     * Creates the transport provider based on configuration.
     *
     * @return the transport provider
     */
    public McpServerTransportProvider createTransport() {
        return switch (transportType) {
            case STDIO -> {
                System.out.println("Using STDIO transport");
                yield new StdioServerTransportProvider(objectMapper);
            }
            case SSE -> {
                System.out.println("Using SSE transport with endpoint: " + mcpEndpoint + "/message");
                yield SseServletTransportProvider.create(mcpEndpoint + "/message", keepAliveInterval)
                        .getTransport();
            }
            case STREAMABLE_HTTP -> {
                System.out.println("Using Streamable HTTP transport with endpoint: " + mcpEndpoint);
                yield StreamableHttpTransportProvider.create(mcpEndpoint, keepAliveInterval)
                        .getTransport();
            }
            case STATELESS_HTTP -> {
                System.out.println("Using Stateless HTTP transport with endpoint: " + mcpEndpoint);
                yield StatelessHttpTransportProvider.create(mcpEndpoint)
                        .getTransportProvider();
            }
        };
    }

    /**
     * Creates a configured ObjectMapper.
     *
     * @return the ObjectMapper
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

    // ============================================================
    // BUILDER METHODS
    // ============================================================

    public McpServerConfiguration yawlEngineUrl(String yawlEngineUrl) {
        this.yawlEngineUrl = yawlEngineUrl;
        return this;
    }

    public McpServerConfiguration yawlUsername(String yawlUsername) {
        this.yawlUsername = yawlUsername;
        return this;
    }

    public McpServerConfiguration yawlPassword(String yawlPassword) {
        this.yawlPassword = yawlPassword;
        return this;
    }

    public McpServerConfiguration transportType(TransportType transportType) {
        this.transportType = transportType;
        return this;
    }

    public McpServerConfiguration port(int port) {
        this.port = port;
        return this;
    }

    public McpServerConfiguration mcpEndpoint(String mcpEndpoint) {
        this.mcpEndpoint = mcpEndpoint;
        return this;
    }

    public McpServerConfiguration logLevel(LoggingLevel logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public McpServerConfiguration keepAliveInterval(Duration keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
        return this;
    }

    public McpServerConfiguration objectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        return this;
    }

    // ============================================================
    // GETTERS
    // ============================================================

    public String getYawlEngineUrl() {
        return yawlEngineUrl;
    }

    public String getYawlUsername() {
        return yawlUsername;
    }

    public String getYawlPassword() {
        return yawlPassword;
    }

    public TransportType getTransportType() {
        return transportType;
    }

    public int getPort() {
        return port;
    }

    public String getMcpEndpoint() {
        return mcpEndpoint;
    }

    public LoggingLevel getLogLevel() {
        return logLevel;
    }

    public Duration getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
