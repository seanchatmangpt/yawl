/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import org.yawlfoundation.yawl.mcp.a2a.mcp.McpTransportConfig;
import org.yawlfoundation.yawl.mcp.a2a.slack.client.SlackClient;
import org.yawlfoundation.yawl.mcp.a2a.slack.config.SlackConfig;
import org.yawlfoundation.yawl.mcp.a2a.slack.tools.SlackToolSpecifications;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP Server for Slack integration in YAWL workflow engine.
 *
 * <p>This server provides Slack integration capabilities through MCP protocol,
 * allowing YAWL workflows to send notifications, manage channels, handle users,
 * and interact with Slack workflows. It supports both incoming webhooks and
 * bot token authentication.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><strong>Notification Channels</strong> - Send workflow event notifications to Slack channels</li>
 *   <li><strong>Direct Messages</strong> - Send direct messages to specific users</li>
 *   <li><strong>Channel Management</strong> - List, create, and manage Slack channels</li>
 *   <li><strong>User Management</strong> - Get user information and list users</li>
 *   <li><strong>Message Handling</strong> - Post, update, and delete messages</li>
 *   <li><strong>Workflow Integration</strong> - Handle Slack workflow events and triggers</li>
 *   <li><strong>Webhook Support</strong> - Receive and process incoming Slack webhooks</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Configure Slack integration via {@link SlackConfig}:</p>
 * <pre>{@code
 * SlackConfig config = SlackConfig.builder()
 *     .botToken("xoxb-your-bot-token")
 *     .defaultChannel("#workflows")
 *     .webhookUrl("https://hooks.slack.com/services/YOUR/WEBHOOK/URL")
 *     .build();
 *
 * SlackMcpServer server = new SlackMcpServer(config);
 * server.start();
 * }</pre>
 *
 * <h2>MCP Tools</h2>
 * <p>Provides Slack-specific tools through MCP protocol:</p>
 * <ul>
 *   <li>send_notification - Send notifications to channels</li>
 *   <li>send_direct_message - Send direct messages to users</li>
 *   <li>list_channels - List accessible channels</li>
 *   <li>get_channel_info - Get channel details</li>
 *   <li>list_users - List workspace users</li>
 *   <li>get_user_info - Get user information</li>
 *   <li>post_message - Post messages with formatting</li>
 *   <li>update_message - Update existing messages</li>
 *   <li>delete_message - Delete messages</li>
 *   <li>join_channel - Join a channel</li>
 *   <li>leave_channel - Leave a channel</li>
 * </ul>
 *
 * <h2>Notification Channels</h2>
 * <p>Workflow events can be routed to different Slack channels:</p>
 * <ul>
 *   <li><code>#workflow-events</code> - General workflow notifications</li>
 *   <li><code>#workitem-completions</code> - Work item completion alerts</li>
 *   <li><code>#workflow-errors</code> - Error notifications</li>
 *   <li><code>#workflow-started</code> - Workflow start notifications</li>
 *   <li><code>#workflow-completed</code> - Workflow completion notifications</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see SlackConfig
 * @see SlackClient
 * @see SlackToolSpecifications
 */
public class SlackMcpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlackMcpServer.class.getName());
    private static final String SERVER_NAME = "yawl-slack-mcp-server";
    private static final String SERVER_VERSION = "6.0.0";

    private final SlackConfig config;
    private final SlackClient slackClient;
    private final McpTransportConfig transportConfig;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private McpSyncServer stdioServer;
    private McpSyncServer httpServer;
    private WebMvcSseServerTransportProvider httpTransportProvider;
    private ObjectMapper objectMapper;

    /**
     * Construct a Slack MCP Server with configuration.
     *
     * @param config Slack configuration
     * @throws IllegalArgumentException if config is null
     */
    public SlackMcpServer(SlackConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("SlackConfig is required");
        }
        this.config = config;
        this.slackClient = new SlackClient(config);
        this.transportConfig = McpTransportConfig.defaults();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Construct a Slack MCP Server with custom transport configuration.
     *
     * @param config Slack configuration
     * @param transportConfig transport configuration
     * @throws IllegalArgumentException if any parameter is null
     */
    public SlackMcpServer(SlackConfig config, McpTransportConfig transportConfig) {
        if (config == null) {
            throw new IllegalArgumentException("SlackConfig is required");
        }
        if (transportConfig == null) {
            throw new IllegalArgumentException("McpTransportConfig is required");
        }
        this.config = config;
        this.slackClient = new SlackClient(config);
        this.transportConfig = transportConfig;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Start the MCP server with configured transports.
     *
     * @throws IOException if server startup fails
     */
    public void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("Starting YAWL Slack MCP Server v{}", SERVER_VERSION);

            if (transportConfig.enableStdio()) {
                startStdioTransport();
            }

            if (transportConfig.enabled()) {
                startHttpTransport();
            }

            LOGGER.info("YAWL Slack MCP Server started - STDIO: {}, HTTP: {}",
                transportConfig.enableStdio(), transportConfig.enabled());
        }
    }

    /**
     * Stop the MCP server gracefully.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("Stopping YAWL Slack MCP Server");

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

            LOGGER.info("YAWL Slack MCP Server stopped");
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
     * <pre>{@code
     * @Configuration
     * public class SlackRouterConfig {
     *     @Bean
     *     public RouterFunction<ServerResponse> slackRouter(SlackMcpServer server) {
     *         return server.getRouterFunction();
     *     }
     * }
     * }</pre>
     *
     * @return the RouterFunction for Slack MCP HTTP endpoints, or null if HTTP transport is disabled
     */
    public RouterFunction<ServerResponse> getRouterFunction() {
        if (httpTransportProvider != null) {
            return httpTransportProvider.getRouterFunction();
        }
        return null;
    }

    /**
     * Get the Slack client for direct API access.
     *
     * @return the Slack client instance
     */
    public SlackClient getSlackClient() {
        return slackClient;
    }

    /**
     * Get the Slack configuration.
     *
     * @return the current Slack configuration
     */
    public SlackConfig getConfig() {
        return config;
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
        McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(objectMapper))
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(buildServerCapabilities())
            .instructions(buildServerInstructions())
            .tools(SlackToolSpecifications.createAll(slackClient))
            .build();

        stdioServer = server;
        LOGGER.info("STDIO transport started for Slack MCP Server");
        System.err.println("YAWL Slack MCP Server: STDIO transport active");
    }

    private void startHttpTransport() {
        String baseUrl = "http://localhost:" + transportConfig.port();

        httpTransportProvider = WebMvcSseServerTransportProvider.builder()
            .jsonMapper(objectMapper)
            .baseUrl(baseUrl)
            .sseEndpoint(transportConfig.fullSsePath())
            .messageEndpoint(transportConfig.fullMessagePath())
            .keepAliveInterval(Duration.ofSeconds(transportConfig.heartbeatIntervalSeconds()))
            .build();

        McpSyncServer server = McpServer.sync(httpTransportProvider)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(buildServerCapabilities())
            .instructions(buildServerInstructions())
            .tools(SlackToolSpecifications.createAll(slackClient))
            .build();

        httpServer = server;
        LOGGER.info("HTTP/SSE transport started on port {}", transportConfig.port());
        LOGGER.info("SSE endpoint: {}", transportConfig.fullSsePath());
        LOGGER.info("Message endpoint: {}", transportConfig.fullMessagePath());
        System.err.println("YAWL Slack MCP Server: HTTP/SSE transport active on port " + transportConfig.port());
    }

    private io.modelcontextprotocol.server.McpServerFeatures.ServerCapabilities buildServerCapabilities() {
        return io.modelcontextprotocol.server.McpServerFeatures.ServerCapabilities.builder()
            .tools(true)
            .resources(false)
            .prompts(false)
            .completions(false)
            .logging(true)
            .build();
    }

    private String buildServerInstructions() {
        return """
            YAWL Slack Integration MCP Server v%s.

            Use tools to send notifications to Slack channels, manage users and channels,
            handle messages, and integrate Slack workflows with YAWL.

            Supported operations:
            • Send notifications to channels
            • Send direct messages to users
            • List and manage channels
            • Get user information
            • Post and update messages
            • Handle webhook events

            Workflow event notifications can be sent to configured channels:
            • #workflow-events - General workflow notifications
            • #workitem-completions - Work item completion alerts
            • #workflow-errors - Error notifications
            • #workflow-started - Workflow start notifications
            • #workflow-completed - Workflow completion notifications

            Configuration:
            • Bot Token: %s
            • Default Channel: %s
            • Webhook URL: %s
            """.formatted(SERVER_VERSION,
                config.getBotToken() != null ? "configured" : "not configured",
                config.getDefaultChannel() != null ? config.getDefaultChannel() : "not set",
                config.getWebhookUrl() != null ? "configured" : "not configured");
    }

    // =========================================================================
    // Static factory methods
    // =========================================================================

    /**
     * Create a SlackMcpServer with default transport configuration.
     *
     * @param config Slack configuration
     * @return configured server instance
     */
    public static SlackMcpServer createDefault(SlackConfig config) {
        return new SlackMcpServer(config, McpTransportConfig.defaults());
    }

    /**
     * Create a SlackMcpServer with STDIO transport only.
     *
     * @param config Slack configuration
     * @return configured server instance with STDIO only
     */
    public static SlackMcpServer createStdioOnly(SlackConfig config) {
        return new SlackMcpServer(config, McpTransportConfig.stdioOnly());
    }

    /**
     * Create a SlackMcpServer with HTTP transport only (no STDIO).
     *
     * @param config Slack configuration
     * @param port HTTP server port
     * @return configured server instance with HTTP only
     */
    public static SlackMcpServer createHttpOnly(SlackConfig config, int port) {
        McpTransportConfig transportConfig = new McpTransportConfig(
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
        return new SlackMcpServer(config, transportConfig);
    }
}