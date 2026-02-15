package org.yawlfoundation.yawl.integration.mcp;

import io.modelcontextprotocol.spec.LoggingLevel;
import io.modelcontextprotocol.spec.SyncToolSpecification;
import io.modelcontextprotocol.spec.SyncResourceSpecification;
import org.yawlfoundation.yawl.integration.mcp.config.McpServerConfiguration;
import org.yawlfoundation.yawl.integration.mcp.server.YawlMcpSyncServer;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlPromptSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlCompletionSpecifications;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Model Context Protocol (MCP) Server for YAWL.
 *
 * A production-ready MCP server that exposes YAWL workflow capabilities
 * as AI-consumable tools and resources. This server enables AI models
 * to interact with YAWL workflows through the standard MCP protocol.
 *
 * Features:
 * - 15+ MCP tools for workflow management
 * - 6+ MCP resources for data access
 * - Prompt templates for guided AI interactions
 * - Autocompletion support
 * - Multiple transport modes (STDIO, SSE, HTTP)
 *
 * Usage:
 * <pre>
 * YawlMcpServer server = new YawlMcpServer();
 * server.start();
 *
 * // Or with custom configuration:
 * McpServerConfiguration config = McpServerConfiguration.fromEnvironment();
 * YawlMcpServer server = new YawlMcpServer(config);
 * server.start();
 * </pre>
 *
 * Environment Variables:
 * - YAWL_ENGINE_URL: YAWL engine URL (default: http://localhost:8080/yawl/ib)
 * - YAWL_USERNAME: YAWL username (default: admin)
 * - YAWL_PASSWORD: YAWL password (default: YAWL)
 * - MCP_TRANSPORT: Transport type (STDIO, SSE, STREAMABLE_HTTP, STATELESS_HTTP)
 * - MCP_PORT: Server port (default: 3000)
 * - MCP_ENDPOINT: MCP endpoint path (default: /mcp)
 * - MCP_LOG_LEVEL: Log level (default: INFO)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpServer {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpServer.class.getName());

    private final McpServerConfiguration config;
    private YawlMcpSyncServer syncServer;
    private boolean running = false;

    /**
     * Creates a new YAWL MCP server with default configuration.
     *
     * Configuration is loaded from environment variables.
     */
    public YawlMcpServer() {
        this(McpServerConfiguration.fromEnvironment());
    }

    /**
     * Creates a new YAWL MCP server with custom configuration.
     *
     * @param config the server configuration
     */
    public YawlMcpServer(McpServerConfiguration config) {
        this.config = config;
        LOGGER.info("YAWL MCP Server initialized with transport: " + config.getTransportType());
    }

    /**
     * Creates a new YAWL MCP server with the specified port.
     *
     * @param port the server port
     */
    public YawlMcpServer(int port) {
        this.config = McpServerConfiguration.fromEnvironment()
                .port(port);
        LOGGER.info("YAWL MCP Server initialized on port: " + port);
    }

    /**
     * Registers all YAWL workflow tools with the MCP server.
     *
     * Tools include:
     * - yawl_launch_case: Start a new workflow
     * - yawl_get_case_status: Get case status
     * - yawl_cancel_case: Cancel a workflow
     * - yawl_get_workitems: List live work items
     * - yawl_checkout_workitem: Claim a work item
     * - yawl_checkin_workitem: Complete a work item
     * - And more...
     *
     * @throws IllegalStateException if server is not initialized
     * @throws IOException if tool registration fails
     */
    public void registerWorkflowTools() throws IOException {
        if (syncServer == null) {
            throw new IllegalStateException(
                    "Server not initialized. Call start() first.\n" +
                    "Example:\n" +
                    "  YawlMcpServer server = new YawlMcpServer();\n" +
                    "  server.start();\n" +
                    "  server.registerWorkflowTools();"
            );
        }

        LOGGER.info("Registering YAWL workflow tools...");
        syncServer.registerTools();
        LOGGER.info("YAWL workflow tools registered successfully");
    }

    /**
     * Registers all YAWL resources with the MCP server.
     *
     * Resources include:
     * - yawl://specifications: All loaded specifications
     * - yawl://specifications/{id}: Specific specification
     * - yawl://cases: All running cases
     * - yawl://cases/{id}: Specific case details
     * - yawl://workitems: All live work items
     * - yawl://workitems/{id}: Specific work item details
     *
     * @throws IllegalStateException if server is not initialized
     * @throws IOException if resource registration fails
     */
    public void registerWorkflowResources() throws IOException {
        if (syncServer == null) {
            throw new IllegalStateException(
                    "Server not initialized. Call start() first.\n" +
                    "Example:\n" +
                    "  YawlMcpServer server = new YawlMcpServer();\n" +
                    "  server.start();\n" +
                    "  server.registerWorkflowResources();"
            );
        }

        LOGGER.info("Registering YAWL resources...");
        syncServer.registerResources();
        LOGGER.info("YAWL resources registered successfully");
    }

    /**
     * Registers all prompts with the MCP server.
     *
     * Prompts include:
     * - yawl_start_workflow: Guide for starting workflows
     * - yawl_task_execution: Guide for executing tasks
     * - yawl_exception_handling: Guide for handling errors
     * - yawl_status_check: Guide for checking status
     */
    public void registerPrompts() {
        if (syncServer == null) {
            throw new IllegalStateException("Server not initialized. Call start() first.");
        }

        LOGGER.info("Registering YAWL prompts...");
        List<SyncPromptSpecification> prompts = YawlPromptSpecifications.getAllPrompts();
        for (SyncPromptSpecification prompt : prompts) {
            syncServer.getServer().addPrompt(prompt);
        }
        LOGGER.info("YAWL prompts registered: " + prompts.size());
    }

    /**
     * Registers all completions with the MCP server.
     *
     * Completions provide autocompletion for:
     * - Specification names in prompts
     * - Case IDs in resource URIs
     * - Work item IDs in resource URIs
     */
    public void registerCompletions() {
        if (syncServer == null) {
            throw new IllegalStateException("Server not initialized. Call start() first.");
        }

        LOGGER.info("Registering YAWL completions...");
        YawlCompletionSpecifications completions = new YawlCompletionSpecifications(
                syncServer.getYawlClient(),
                syncServer.getSessionHandle()
        );

        completions.getAllCompletions().forEach(completion -> {
            syncServer.getServer().addCompletion(completion);
        });
        LOGGER.info("YAWL completions registered");
    }

    /**
     * Starts the MCP server.
     *
     * This initializes the YAWL connection and prepares the server
     * for tool and resource registration.
     *
     * @throws IOException if server startup fails
     */
    public void start() throws IOException {
        if (running) {
            LOGGER.warning("YAWL MCP Server already running");
            return;
        }

        LOGGER.info("Starting YAWL MCP Server...");
        LOGGER.info("YAWL Engine URL: " + config.getYawlEngineUrl());
        LOGGER.info("Transport Type: " + config.getTransportType());

        try {
            syncServer = config.createServer();
            syncServer.start();
            running = true;
            LOGGER.info("YAWL MCP Server started successfully");
        } catch (Exception e) {
            LOGGER.severe("Failed to start YAWL MCP Server: " + e.getMessage());
            throw new IOException("Failed to start MCP server: " + e.getMessage(), e);
        }
    }

    /**
     * Stops the MCP server.
     *
     * This disconnects from the YAWL engine and cleans up resources.
     */
    public void stop() {
        if (!running) {
            LOGGER.warning("YAWL MCP Server not running");
            return;
        }

        LOGGER.info("Stopping YAWL MCP Server...");

        if (syncServer != null) {
            syncServer.stop();
            syncServer = null;
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
     * Gets the server configuration.
     *
     * @return the configuration
     */
    public McpServerConfiguration getConfig() {
        return config;
    }

    /**
     * Gets the underlying sync server.
     *
     * @return the YawlMcpSyncServer instance or null if not started
     */
    public YawlMcpSyncServer getSyncServer() {
        return syncServer;
    }

    /**
     * Main entry point for running the MCP server standalone.
     *
     * @param args command line arguments (optional port number)
     */
    public static void main(String[] args) {
        McpServerConfiguration config = McpServerConfiguration.fromEnvironment();

        // Override port from command line if provided
        if (args.length > 0) {
            try {
                config.port(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid port number: " + args[0] + ", using default");
            }
        }

        YawlMcpServer server = new YawlMcpServer(config);

        // Add shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down YAWL MCP Server...");
            server.stop();
        }));

        try {
            // Start the server
            server.start();

            // Register all capabilities
            server.registerWorkflowTools();
            server.registerWorkflowResources();
            server.registerPrompts();
            server.registerCompletions();

            System.out.println("\n===========================================");
            System.out.println("YAWL MCP Server is ready");
            System.out.println("===========================================");
            System.out.println("Transport: " + config.getTransportType());
            System.out.println("Endpoint: " + config.getMcpEndpoint());
            System.out.println("YAWL Engine: " + config.getYawlEngineUrl());
            System.out.println("===========================================");
            System.out.println("AI models can now use YAWL workflows as tools");
            System.out.println("Press Ctrl+C to stop");
            System.out.println("===========================================\n");

            // Keep the server running
            Thread.currentThread().join();

        } catch (IOException e) {
            LOGGER.severe("Failed to start server: " + e.getMessage());
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nMake sure YAWL Engine is running at: " + config.getYawlEngineUrl());
            System.err.println("And credentials are correct (YAWL_USERNAME, YAWL_PASSWORD)");
            System.exit(1);
        } catch (InterruptedException e) {
            server.stop();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.severe("Server error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
