package org.yawlfoundation.yawl.integration.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.integration.mcp.stub.JacksonMcpJsonMapper;
import org.yawlfoundation.yawl.integration.mcp.stub.McpServer;
import org.yawlfoundation.yawl.integration.mcp.stub.McpSyncServer;
import org.yawlfoundation.yawl.integration.mcp.stub.StdioServerTransportProvider;
import org.yawlfoundation.yawl.integration.mcp.stub.McpSchema;
import org.yawlfoundation.yawl.integration.mcp.stub.ZaiFunctionService;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;
import org.yawlfoundation.yawl.integration.mcp.server.YawlServerCapabilities;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlCompletionSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlPromptSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;

import java.io.IOException;

/**
 * Model Context Protocol (MCP) Server for YAWL using the official MCP Java SDK 0.17.2.
 *
 * Exposes all MCP capabilities backed by real YAWL engine operations over STDIO transport:
 *
 * Tools (15): Launch/cancel cases, get case status, list specifications, get/complete/checkout/checkin
 *   work items, get specification data/XML/schema, get running cases, upload/unload specifications.
 *
 * Resources (3 static):
 *   - yawl://specifications - All loaded specifications
 *   - yawl://cases - All running cases
 *   - yawl://workitems - All live work items
 *
 * Resource Templates (3 parameterized):
 *   - yawl://cases/{caseId} - Specific case state and work items
 *   - yawl://cases/{caseId}/data - Specific case variable data
 *   - yawl://workitems/{workItemId} - Specific work item details
 *
 * Prompts (4):
 *   - workflow_analysis - Analyze a workflow specification
 *   - task_completion_guide - Guide for completing a work item
 *   - case_troubleshooting - Diagnose issues with a workflow case
 *   - workflow_design_review - Review specification for best practices
 *
 * Completions (3):
 *   - workflow_analysis prompt: auto-complete spec identifiers
 *   - task_completion_guide prompt: auto-complete work item IDs
 *   - yawl://cases/{caseId} resource: auto-complete case IDs
 *
 * Logging: Structured MCP log notifications for tool execution, errors, and server events.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpServer {

    private static final String SERVER_NAME = "yawl-mcp-server";
    private static final String SERVER_VERSION = "5.2.0";

    private final String yawlEngineUrl;
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final InterfaceA_EnvironmentBasedClient interfaceAClient;
    private final String yawlUsername;
    private final String yawlPassword;
    private final McpLoggingHandler loggingHandler;
    private McpSyncServer mcpServer;
    private String sessionHandle;

    /**
     * Construct a YAWL MCP Server with YAWL engine connection parameters.
     *
     * @param yawlEngineUrl base URL of YAWL engine (e.g. http://localhost:8080/yawl)
     * @param username YAWL admin username
     * @param password YAWL admin password
     */
    public YawlMcpServer(String yawlEngineUrl, String username, String password) {
        if (yawlEngineUrl == null || yawlEngineUrl.isBlank()) {
            throw new IllegalArgumentException(
                "YAWL engine URL is required (e.g. http://localhost:8080/yawl)");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("YAWL username is required");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("YAWL password is required");
        }

        this.yawlEngineUrl = yawlEngineUrl;
        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(
                yawlEngineUrl + "/ib");
        this.interfaceAClient = new InterfaceA_EnvironmentBasedClient(
                yawlEngineUrl + "/ia");
        this.yawlUsername = username;
        this.yawlPassword = password;
        this.loggingHandler = new McpLoggingHandler();
    }

    /**
     * Build and start the MCP server using the official SDK with STDIO transport.
     *
     * Connects to the YAWL engine, registers all MCP capabilities (tools, resources,
     * resource templates, prompts, completions, logging), and starts the server.
     * This method blocks until the server is shut down.
     *
     * @throws IOException if connection to the YAWL engine fails
     */
    public void start() throws IOException {
        connectToEngine();

        ZaiFunctionService zaiFunctionService = null;
        var zaiApiKey = System.getenv("ZAI_API_KEY");
        if (zaiApiKey != null && !zaiApiKey.isBlank()) {
            try {
                zaiFunctionService = new ZaiFunctionService(
                    zaiApiKey, yawlEngineUrl, yawlUsername, yawlPassword);
            } catch (Exception e) {
                System.err.println("Z.AI not available: " + e.getMessage());
            }
        }

        var mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        var jsonMapper = new JacksonMcpJsonMapper(mapper);
        var transportProvider = new StdioServerTransportProvider(jsonMapper);

        mcpServer = McpServer.sync(transportProvider)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(YawlServerCapabilities.full())
            .instructions(
                "YAWL Workflow Engine MCP Server v" + SERVER_VERSION + ". " +
                "Use the provided tools to launch and manage workflow cases, " +
                "query and upload specifications, checkout and complete work items. " +
                "Resources provide read-only access to specifications, cases, and work items. " +
                "Prompts guide workflow analysis, task completion, troubleshooting, and design review.")
            .tools(YawlToolSpecifications.createAll(
                interfaceBClient, interfaceAClient, sessionHandle, zaiFunctionService))
            .resources(YawlResourceProvider.createAllResources(
                interfaceBClient, sessionHandle))
            .resourceTemplates(YawlResourceProvider.createAllResourceTemplates(
                interfaceBClient, sessionHandle))
            .prompts(YawlPromptSpecifications.createAll(
                interfaceBClient, () -> sessionHandle))
            .completions(YawlCompletionSpecifications.createAll(
                interfaceBClient, sessionHandle))
            .build();

        loggingHandler.info(mcpServer, "YAWL MCP Server started with full capabilities");
        int toolCount = zaiFunctionService != null ? 16 : 15;
        System.err.println("YAWL MCP Server v" + SERVER_VERSION + " started on STDIO transport");
        System.err.println("Capabilities: " + toolCount + " tools, 3 resources, 3 resource templates, " +
            "4 prompts, 3 completions, logging");
    }

    /**
     * Stop the MCP server gracefully.
     */
    public void stop() {
        if (mcpServer != null) {
            loggingHandler.info(mcpServer, "YAWL MCP Server shutting down");
            mcpServer.closeGracefully();
            mcpServer = null;
        }
        disconnectFromEngine();
    }

    /**
     * Check if server has been built and is running.
     *
     * @return true if the MCP server is active
     */
    public boolean isRunning() {
        return mcpServer != null;
    }

    /**
     * Get the underlying MCP sync server instance.
     * Useful for sending log notifications from external code.
     *
     * @return the MCP sync server, or null if not started
     */
    public McpSyncServer getMcpServer() {
        return mcpServer;
    }

    /**
     * Get the logging handler for sending structured MCP log notifications.
     *
     * @return the logging handler
     */
    public McpLoggingHandler getLoggingHandler() {
        return loggingHandler;
    }

    // =========================================================================
    // YAWL Engine connection management
    // =========================================================================

    private void connectToEngine() throws IOException {
        sessionHandle = interfaceBClient.connect(yawlUsername, yawlPassword);
        if (sessionHandle == null || sessionHandle.contains("<failure>")) {
            throw new IOException(
                "Failed to connect to YAWL engine. " +
                "Verify the engine is running and credentials are correct. " +
                "Response: " + sessionHandle);
        }
        System.err.println("Connected to YAWL engine (session established)");
    }

    private void disconnectFromEngine() {
        if (sessionHandle != null) {
            try {
                interfaceBClient.disconnect(sessionHandle);
            } catch (IOException e) {
                System.err.println(
                    "Warning: failed to disconnect from YAWL engine: "
                    + e.getMessage());
            }
            sessionHandle = null;
        }
    }

    /**
     * Entry point for running the YAWL MCP Server.
     *
     * Reads configuration from environment variables:
     *   YAWL_ENGINE_URL - YAWL engine base URL (required, e.g. http://localhost:8080/yawl)
     *   YAWL_USERNAME   - YAWL admin username (required)
     *   YAWL_PASSWORD   - YAWL admin password (required)
     */
    public static void main(String[] args) {
        var engineUrl = System.getenv("YAWL_ENGINE_URL");
        if (engineUrl == null || engineUrl.isBlank()) {
            throw new IllegalStateException(
                "YAWL_ENGINE_URL environment variable is required.\n" +
                "Set it with: export YAWL_ENGINE_URL=http://localhost:8080/yawl");
        }

        var username = System.getenv("YAWL_USERNAME");
        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                "YAWL_USERNAME environment variable is required.\n" +
                "Set it with: export YAWL_USERNAME=admin");
        }

        var password = System.getenv("YAWL_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "YAWL_PASSWORD environment variable is required.\n" +
                "Set it with: export YAWL_PASSWORD=YAWL");
        }

        System.err.println("Starting YAWL MCP Server v" + SERVER_VERSION);
        System.err.println("Engine URL: " + engineUrl);
        System.err.println("Transport: STDIO (official MCP SDK 0.17.2)");

        var server = new YawlMcpServer(engineUrl, username, password);

        Runtime.getRuntime().addShutdownHook(
            Thread.ofVirtual().unstarted(() -> {
                System.err.println("Shutting down YAWL MCP Server...");
                server.stop();
            })
        );

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start YAWL MCP Server: " + e.getMessage());
            throw new RuntimeException(
                "YAWL MCP Server startup failed. " +
                "Ensure the YAWL engine is running at " + engineUrl + " and credentials are valid.",
                e);
        }
    }
}
