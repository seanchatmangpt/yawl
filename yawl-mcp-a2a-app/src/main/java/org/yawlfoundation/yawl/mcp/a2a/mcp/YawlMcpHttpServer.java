package org.yawlfoundation.yawl.mcp.a2a.mcp;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;
import org.yawlfoundation.yawl.integration.mcp.server.YawlServerCapabilities;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlCompletionSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlPromptSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * MCP Server with HTTP/SSE transport support for YAWL workflow engine.
 *
 * <p>This server extends the standard STDIO-based MCP server with HTTP Server-Sent Events
 * (SSE) transport, enabling cloud deployments and multi-client connections while
 * maintaining backward compatibility with the STDIO transport for CLI usage.</p>
 *
 * <h2>Transport Modes</h2>
 * <ul>
 *   <li><strong>STDIO</strong>: Standard input/output for local CLI integration</li>
 *   <li><strong>HTTP/SSE</strong>: HTTP with Server-Sent Events for cloud deployment</li>
 *   <li><strong>Dual</strong>: Both STDIO and HTTP/SSE active simultaneously</li>
 * </ul>
 *
 * <h2>HTTP Endpoints</h2>
 * <ul>
 *   <li><code>GET /mcp/sse</code> - SSE connection endpoint for server-to-client messages</li>
 *   <li><code>POST /mcp/message</code> - HTTP POST for client-to-server JSON-RPC messages</li>
 *   <li><code>GET /mcp/health</code> - Health check endpoint for load balancers</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>HTTP transport is configured via {@link McpTransportConfig}:</p>
 * <pre>{@code
 * McpTransportConfig config = McpTransportConfig.defaults();
 * YawlMcpHttpServer server = new YawlMcpHttpServer(
 *     "http://localhost:8080/yawl", "admin", "password", config);
 * server.start();
 *
 * // Get the router function for Spring WebMVC
 * RouterFunction<ServerResponse> router = server.getRouterFunction();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see McpTransportConfig
 * @see WebMvcSseServerTransportProvider
 */
public class YawlMcpHttpServer {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpHttpServer.class.getName());
    private static final String SERVER_NAME = "yawl-mcp-http-server";
    private static final String SERVER_VERSION = "6.0.0";

    private final String yawlEngineUrl;
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final InterfaceA_EnvironmentBasedClient interfaceAClient;
    private final String yawlUsername;
    private final String yawlPassword;
    private final McpTransportConfig transportConfig;
    private final McpLoggingHandler loggingHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private McpSyncServer stdioServer;
    private McpSyncServer httpServer;
    private WebMvcSseServerTransportProvider httpTransportProvider;
    private String sessionHandle;

    /**
     * Construct a YAWL MCP HTTP Server with full configuration.
     *
     * @param yawlEngineUrl base URL of YAWL engine (e.g. http://localhost:8080/yawl)
     * @param username YAWL admin username
     * @param password YAWL admin password
     * @param transportConfig transport configuration for HTTP/SSE settings
     * @throws IllegalArgumentException if any required parameter is null or empty
     */
    public YawlMcpHttpServer(
            String yawlEngineUrl,
            String username,
            String password,
            McpTransportConfig transportConfig) {

        validateConstructorParams(yawlEngineUrl, username, password, transportConfig);

        this.yawlEngineUrl = yawlEngineUrl;
        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(yawlEngineUrl + "/ib");
        this.interfaceAClient = new InterfaceA_EnvironmentBasedClient(yawlEngineUrl + "/ia");
        this.yawlUsername = username;
        this.yawlPassword = password;
        this.transportConfig = transportConfig;
        this.loggingHandler = new McpLoggingHandler();

        LOGGER.info("YawlMcpHttpServer created with config: enabled=" + transportConfig.enabled()
            + ", port=" + transportConfig.port()
            + ", stdio=" + transportConfig.enableStdio());
    }

    private void validateConstructorParams(
            String yawlEngineUrl,
            String username,
            String password,
            McpTransportConfig config) {

        if (yawlEngineUrl == null || yawlEngineUrl.isEmpty()) {
            throw new IllegalArgumentException(
                "YAWL engine URL is required (e.g. http://localhost:8080/yawl)");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("YAWL username is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("YAWL password is required");
        }
        if (config == null) {
            throw new IllegalArgumentException("McpTransportConfig is required");
        }
    }

    /**
     * Start the MCP server with configured transports.
     *
     * <p>Based on the configuration, this will start:</p>
     * <ul>
     *   <li>STDIO transport only (if enableStdio=true and enabled=false)</li>
     *   <li>HTTP transport only (if enableStdio=false and enabled=true)</li>
     *   <li>Both transports (if enableStdio=true and enabled=true)</li>
     * </ul>
     *
     * @throws IOException if connection to YAWL engine fails or server startup fails
     */
    public void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            connectToEngine();

            if (transportConfig.enableStdio()) {
                startStdioTransport();
            }

            if (transportConfig.enabled()) {
                startHttpTransport();
            }

            loggingHandler.info(
                getActiveServer(),
                "YAWL MCP HTTP Server started - STDIO: " + transportConfig.enableStdio()
                    + ", HTTP: " + transportConfig.enabled());

            LOGGER.info("YAWL MCP HTTP Server v" + SERVER_VERSION + " started");
            LOGGER.info("Transports: STDIO=" + transportConfig.enableStdio()
                + ", HTTP=" + transportConfig.enabled());
        }
    }

    /**
     * Stop the MCP server gracefully.
     *
     * <p>Closes all active transport connections and disconnects from the YAWL engine.</p>
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            loggingHandler.info(getActiveServer(), "YAWL MCP HTTP Server shutting down");

            if (stdioServer != null) {
                stdioServer.closeGracefully();
                stdioServer = null;
            }

            if (httpServer != null) {
                httpServer.closeGracefully();
                httpServer = null;
            }

            if (httpTransportProvider != null) {
                httpTransportProvider.closeGracefully().block(Duration.ofSeconds(10));
                httpTransportProvider = null;
            }

            disconnectFromEngine();
            LOGGER.info("YAWL MCP HTTP Server stopped");
        }
    }

    /**
     * Check if the server is currently running.
     *
     * @return true if the server is active
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get the HTTP transport's router function for Spring WebMVC integration.
     *
     * <p>This router function should be registered with Spring's RouterFunction
     * configuration to handle HTTP requests for the MCP server.</p>
     *
     * <pre>{@code
     * @Configuration
     * public class McpRouterConfig {
     *     @Bean
     *     public RouterFunction<ServerResponse> mcpRouter(YawlMcpHttpServer server) {
     *         return server.getRouterFunction();
     *     }
     * }
     * }</pre>
     *
     * @return the RouterFunction for MCP HTTP endpoints, or null if HTTP transport is disabled
     */
    public RouterFunction<ServerResponse> getRouterFunction() {
        if (httpTransportProvider != null) {
            return httpTransportProvider.getRouterFunction();
        }
        return null;
    }

    /**
     * Get the active MCP server instance.
     *
     * <p>Returns the HTTP server if active, otherwise the STDIO server.</p>
     *
     * @return the active MCP sync server, or null if not started
     */
    public McpSyncServer getMcpServer() {
        if (httpServer != null) {
            return httpServer;
        }
        return stdioServer;
    }

    /**
     * Get the logging handler for MCP notifications.
     *
     * @return the logging handler
     */
    public McpLoggingHandler getLoggingHandler() {
        return loggingHandler;
    }

    /**
     * Get the transport configuration.
     *
     * @return the current transport configuration
     */
    public McpTransportConfig getTransportConfig() {
        return transportConfig;
    }

    /**
     * Check if HTTP transport is active.
     *
     * @return true if HTTP transport is enabled and running
     */
    public boolean isHttpTransportActive() {
        return httpServer != null && httpTransportProvider != null;
    }

    /**
     * Check if STDIO transport is active.
     *
     * @return true if STDIO transport is enabled and running
     */
    public boolean isStdioTransportActive() {
        return stdioServer != null;
    }

    // =========================================================================
    // Transport initialization
    // =========================================================================

    private void startStdioTransport() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);

        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);

        stdioServer = McpServer.sync(transportProvider)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(YawlServerCapabilities.full())
            .instructions(buildServerInstructions())
            .tools(YawlToolSpecifications.createAll(interfaceBClient, interfaceAClient, sessionHandle))
            .resources(YawlResourceProvider.createAllResources(interfaceBClient, sessionHandle))
            .resourceTemplates(YawlResourceProvider.createAllResourceTemplates(interfaceBClient, sessionHandle))
            .prompts(YawlPromptSpecifications.createAll(interfaceBClient, () -> sessionHandle))
            .completions(YawlCompletionSpecifications.createAll(interfaceBClient, sessionHandle))
            .build();

        LOGGER.info("STDIO transport started");
        System.err.println("YAWL MCP Server: STDIO transport active");
    }

    private void startHttpTransport() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);

        String baseUrl = "http://localhost:" + transportConfig.port();

        httpTransportProvider = WebMvcSseServerTransportProvider.builder()
            .jsonMapper(jsonMapper)
            .baseUrl(baseUrl)
            .sseEndpoint(transportConfig.fullSsePath())
            .messageEndpoint(transportConfig.fullMessagePath())
            .keepAliveInterval(Duration.ofSeconds(transportConfig.heartbeatIntervalSeconds()))
            .build();

        httpServer = McpServer.sync(httpTransportProvider)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(YawlServerCapabilities.full())
            .instructions(buildServerInstructions())
            .tools(YawlToolSpecifications.createAll(interfaceBClient, interfaceAClient, sessionHandle))
            .resources(YawlResourceProvider.createAllResources(interfaceBClient, sessionHandle))
            .resourceTemplates(YawlResourceProvider.createAllResourceTemplates(interfaceBClient, sessionHandle))
            .prompts(YawlPromptSpecifications.createAll(interfaceBClient, () -> sessionHandle))
            .completions(YawlCompletionSpecifications.createAll(interfaceBClient, sessionHandle))
            .build();

        LOGGER.info("HTTP/SSE transport started on port " + transportConfig.port());
        LOGGER.info("SSE endpoint: " + transportConfig.fullSsePath());
        LOGGER.info("Message endpoint: " + transportConfig.fullMessagePath());
        System.err.println("YAWL MCP Server: HTTP/SSE transport active on port " + transportConfig.port());
    }

    // =========================================================================
    // YAWL Engine connection management
    // =========================================================================

    private void connectToEngine() throws IOException {
        sessionHandle = interfaceBClient.connect(yawlUsername, yawlPassword);
        if (sessionHandle == null || sessionHandle.contains("<failure>")) {
            running.set(false);
            throw new IOException(
                "Failed to connect to YAWL engine at " + yawlEngineUrl + ". " +
                "Verify the engine is running and credentials are correct. " +
                "Response: " + sessionHandle);
        }
        LOGGER.info("Connected to YAWL engine at " + yawlEngineUrl);
    }

    private void disconnectFromEngine() {
        if (sessionHandle != null) {
            try {
                interfaceBClient.disconnect(sessionHandle);
                LOGGER.info("Disconnected from YAWL engine");
            } catch (IOException e) {
                LOGGER.warning("Failed to disconnect from YAWL engine: " + e.getMessage());
            }
            sessionHandle = null;
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private String buildServerInstructions() {
        return """
            YAWL Workflow Engine MCP Server v%s.

            Use tools to launch and manage workflow cases, query and upload specifications,
            checkout and complete work items. Resources provide read-only access to
            specifications, cases, and work items. Prompts guide workflow analysis,
            task completion, troubleshooting, and design review.

            Capabilities: 15 tools, 3 resources, 3 resource templates, 4 prompts,
            3 completions, logging (MCP 2025-11-25 compliant).

            Transports: STDIO and HTTP/SSE supported.
            """.formatted(SERVER_VERSION);
    }

    private McpSyncServer getActiveServer() {
        if (httpServer != null) {
            return httpServer;
        }
        if (stdioServer != null) {
            return stdioServer;
        }
        return null;
    }

    // =========================================================================
    // Static factory methods
    // =========================================================================

    /**
     * Create a YawlMcpHttpServer with default HTTP configuration.
     *
     * @param yawlEngineUrl YAWL engine URL
     * @param username YAWL username
     * @param password YAWL password
     * @return configured server instance
     */
    public static YawlMcpHttpServer createDefault(
            String yawlEngineUrl,
            String username,
            String password) {
        return new YawlMcpHttpServer(yawlEngineUrl, username, password, McpTransportConfig.defaults());
    }

    /**
     * Create a YawlMcpHttpServer with STDIO transport only.
     *
     * @param yawlEngineUrl YAWL engine URL
     * @param username YAWL username
     * @param password YAWL password
     * @return configured server instance with STDIO only
     */
    public static YawlMcpHttpServer createStdioOnly(
            String yawlEngineUrl,
            String username,
            String password) {
        return new YawlMcpHttpServer(yawlEngineUrl, username, password, McpTransportConfig.stdioOnly());
    }

    /**
     * Create a YawlMcpHttpServer with HTTP transport only (no STDIO).
     *
     * @param yawlEngineUrl YAWL engine URL
     * @param username YAWL username
     * @param password YAWL password
     * @param port HTTP server port
     * @return configured server instance with HTTP only
     */
    public static YawlMcpHttpServer createHttpOnly(
            String yawlEngineUrl,
            String username,
            String password,
            int port) {
        McpTransportConfig config = new McpTransportConfig(
            true,      // enabled
            port,
            McpTransportConfig.DEFAULT_PATH,
            McpTransportConfig.DEFAULT_SSE_PATH,
            McpTransportConfig.DEFAULT_MESSAGE_PATH,
            McpTransportConfig.DEFAULT_MAX_CONNECTIONS,
            McpTransportConfig.DEFAULT_TIMEOUT_SECONDS,
            false,     // enableStdio
            true,      // enableHealthCheck
            McpTransportConfig.DEFAULT_HEARTBEAT_SECONDS
        );
        return new YawlMcpHttpServer(yawlEngineUrl, username, password, config);
    }
}
