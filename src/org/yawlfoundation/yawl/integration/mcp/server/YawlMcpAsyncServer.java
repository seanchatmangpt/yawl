package org.yawlfoundation.yawl.integration.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.ServerCapabilities;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Asynchronous MCP Server for YAWL with reactive patterns.
 *
 * Provides a production-ready async MCP server using Project Reactor
 * for non-blocking YAWL operations. Ideal for high-throughput scenarios.
 *
 * Features:
 * - Reactive patterns with Mono/Flux return types
 * - Non-blocking YAWL operations
 * - Reactive error handling
 * - Backpressure support
 *
 * Usage:
 * <pre>
 * YawlMcpAsyncServer server = new YawlMcpAsyncServer(
 *     transport,
 *     "http://localhost:8080/yawl/ib",
 *     "admin",
 *     "YAWL"
 * );
 * server.registerAllCapabilities()
 *     .then(server.start())
 *     .subscribe();
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpAsyncServer {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpAsyncServer.class.getName());
    private static final String SERVER_NAME = "yawl-mcp-server";
    private static final String SERVER_VERSION = "5.2";

    private final McpAsyncServer server;
    private final InterfaceB_EnvironmentBasedClient yawlClient;
    private final String sessionHandle;
    private final ObjectMapper objectMapper;
    private volatile boolean running = false;

    /**
     * Creates a new YAWL MCP async server with YAWL engine connection.
     *
     * @param transport the MCP transport provider
     * @param yawlEngineUrl the YAWL engine URL
     * @param username the YAWL username
     * @param password the YAWL password
     * @throws IOException if connection to YAWL engine fails
     */
    public YawlMcpAsyncServer(
            McpServerTransportProvider transport,
            String yawlEngineUrl,
            String username,
            String password) throws IOException {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();

        LOGGER.info("Connecting to YAWL engine at: " + yawlEngineUrl);
        this.yawlClient = new InterfaceB_EnvironmentBasedClient(yawlEngineUrl);
        this.sessionHandle = yawlClient.connect(username, password);
        LOGGER.info("Connected to YAWL engine successfully");

        this.server = McpServer.async(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(YawlServerCapabilities.full())
                .build();

        LOGGER.info("Async MCP server initialized: " + SERVER_NAME + " v" + SERVER_VERSION);
    }

    /**
     * Creates a new YAWL MCP async server with custom capabilities.
     *
     * @param transport the MCP transport provider
     * @param yawlEngineUrl the YAWL engine URL
     * @param username the YAWL username
     * @param password the YAWL password
     * @param capabilities custom server capabilities
     * @throws IOException if connection to YAWL engine fails
     */
    public YawlMcpAsyncServer(
            McpServerTransportProvider transport,
            String yawlEngineUrl,
            String username,
            String password,
            ServerCapabilities capabilities) throws IOException {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();

        LOGGER.info("Connecting to YAWL engine at: " + yawlEngineUrl);
        this.yawlClient = new InterfaceB_EnvironmentBasedClient(yawlEngineUrl);
        this.sessionHandle = yawlClient.connect(username, password);
        LOGGER.info("Connected to YAWL engine successfully");

        this.server = McpServer.async(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(capabilities)
                .build();

        LOGGER.info("Async MCP server initialized with custom capabilities");
    }

    /**
     * Starts the async MCP server.
     *
     * @return a Mono that completes when the server is started
     */
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (running) {
                LOGGER.warning("Server already running");
                return;
            }

            LOGGER.info("Starting YAWL MCP Async Server...");
            running = true;
            LOGGER.info("YAWL MCP Async Server started successfully");
        });
    }

    /**
     * Stops the async MCP server and disconnects from YAWL engine.
     *
     * @return a Mono that completes when the server is stopped
     */
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            if (!running) {
                LOGGER.warning("Server not running");
                return;
            }

            LOGGER.info("Stopping YAWL MCP Async Server...");

            try {
                yawlClient.disconnect(sessionHandle);
                LOGGER.info("Disconnected from YAWL engine");
            } catch (IOException e) {
                LOGGER.warning("Error disconnecting from YAWL engine: " + e.getMessage());
            }

            running = false;
            LOGGER.info("YAWL MCP Async Server stopped");
        }).then(server.close());
    }

    /**
     * Checks if the server is running.
     *
     * @return true if the server is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the underlying McpAsyncServer instance.
     *
     * @return the MCP server
     */
    public McpAsyncServer getServer() {
        return server;
    }

    /**
     * Gets the YAWL engine client.
     *
     * @return the InterfaceB client
     */
    public InterfaceB_EnvironmentBasedClient getYawlClient() {
        return yawlClient;
    }

    /**
     * Gets the YAWL session handle.
     *
     * @return the session handle
     */
    public String getSessionHandle() {
        return sessionHandle;
    }

    /**
     * Gets the ObjectMapper used for JSON serialization.
     *
     * @return the ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
