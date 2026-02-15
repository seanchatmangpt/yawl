package org.yawlfoundation.yawl.integration.mcp.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.ServerCapabilities;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Synchronous MCP Server for YAWL with real engine integration.
 *
 * Provides a production-ready MCP server that exposes YAWL workflow capabilities
 * as AI-consumable tools and resources. Uses InterfaceB for direct YAWL engine communication.
 *
 * Features:
 * - Synchronous execution with immediate response mode
 * - Full YAWL engine integration via InterfaceB
 * - Configurable server capabilities
 * - Structured logging with MCP notifications
 *
 * Usage:
 * <pre>
 * YawlMcpSyncServer server = new YawlMcpSyncServer(
 *     transport,
 *     "http://localhost:8080/yawl/ib",
 *     "admin",
 *     "YAWL"
 * );
 * server.registerAllCapabilities();
 * server.start();
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpSyncServer {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpSyncServer.class.getName());
    private static final String SERVER_NAME = "yawl-mcp-server";
    private static final String SERVER_VERSION = "5.2";

    private final McpSyncServer server;
    private final InterfaceB_EnvironmentBasedClient yawlClient;
    private final String sessionHandle;
    private final ObjectMapper objectMapper;
    private final McpLoggingHandler loggingHandler;
    private boolean running = false;

    /**
     * Creates a new YAWL MCP sync server with YAWL engine connection.
     *
     * @param transport the MCP transport provider
     * @param yawlEngineUrl the YAWL engine URL (e.g., "http://localhost:8080/yawl/ib")
     * @param username the YAWL username
     * @param password the YAWL password
     * @throws IOException if connection to YAWL engine fails
     */
    public YawlMcpSyncServer(
            McpServerTransportProvider transport,
            String yawlEngineUrl,
            String username,
            String password) throws IOException {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();

        // Connect to YAWL engine
        LOGGER.info("Connecting to YAWL engine at: " + yawlEngineUrl);
        this.yawlClient = new InterfaceB_EnvironmentBasedClient(yawlEngineUrl);
        this.sessionHandle = yawlClient.connect(username, password);
        LOGGER.info("Connected to YAWL engine successfully");

        // Initialize logging handler
        this.loggingHandler = new McpLoggingHandler();

        // Build MCP server with capabilities
        this.server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(YawlServerCapabilities.full())
                .immediateExecution(true)
                .build();

        LOGGER.info("MCP server initialized: " + SERVER_NAME + " v" + SERVER_VERSION);
    }

    /**
     * Creates a new YAWL MCP sync server with custom capabilities.
     *
     * @param transport the MCP transport provider
     * @param yawlEngineUrl the YAWL engine URL
     * @param username the YAWL username
     * @param password the YAWL password
     * @param capabilities custom server capabilities
     * @throws IOException if connection to YAWL engine fails
     */
    public YawlMcpSyncServer(
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

        this.loggingHandler = new McpLoggingHandler();

        this.server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(capabilities)
                .immediateExecution(true)
                .build();

        LOGGER.info("MCP server initialized with custom capabilities");
    }

    /**
     * Registers all YAWL tools with the MCP server.
     *
     * @throws IOException if tool registration fails
     */
    public void registerTools() throws IOException {
        LOGGER.info("Registering YAWL tools...");

        // Register workflow tools
        YawlToolSpecifications.getWorkflowTools(yawlClient, sessionHandle, objectMapper)
                .forEach(tool -> {
                    server.addTool(tool);
                    LOGGER.fine("Registered tool: " + tool.tool().name());
                });

        LOGGER.info("YAWL tools registered successfully");
    }

    /**
     * Registers all YAWL resources with the MCP server.
     *
     * @throws IOException if resource registration fails
     */
    public void registerResources() throws IOException {
        LOGGER.info("Registering YAWL resources...");

        YawlResourceProvider provider = new YawlResourceProvider(yawlClient, sessionHandle, objectMapper);
        provider.getAllResources().forEach(resource -> {
            server.addResource(resource);
            LOGGER.fine("Registered resource: " + resource.resource().uri());
        });

        LOGGER.info("YAWL resources registered successfully");
    }

    /**
     * Registers all capabilities (tools, resources, prompts) with the MCP server.
     *
     * @throws IOException if registration fails
     */
    public void registerAllCapabilities() throws IOException {
        registerTools();
        registerResources();
    }

    /**
     * Starts the MCP server.
     *
     * @throws IOException if server startup fails
     */
    public void start() throws IOException {
        if (running) {
            LOGGER.warning("Server already running");
            return;
        }

        LOGGER.info("Starting YAWL MCP Server...");
        running = true;
        LOGGER.info("YAWL MCP Server started successfully");
    }

    /**
     * Stops the MCP server and disconnects from YAWL engine.
     */
    public void stop() {
        if (!running) {
            LOGGER.warning("Server not running");
            return;
        }

        LOGGER.info("Stopping YAWL MCP Server...");

        try {
            yawlClient.disconnect(sessionHandle);
            LOGGER.info("Disconnected from YAWL engine");
        } catch (IOException e) {
            LOGGER.warning("Error disconnecting from YAWL engine: " + e.getMessage());
        }

        running = false;
        LOGGER.info("YAWL MCP Server stopped");
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
     * Gets the underlying McpSyncServer instance.
     *
     * @return the MCP server
     */
    public McpSyncServer getServer() {
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
     * Gets the logging handler.
     *
     * @return the logging handler
     */
    public McpLoggingHandler getLoggingHandler() {
        return loggingHandler;
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
