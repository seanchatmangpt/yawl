package org.yawlfoundation.yawl.mcp.a2a.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import org.yawlfoundation.yawl.mcp.a2a.mcp.McpTransportConfig;
import org.yawlfoundation.yawl.mcp.a2a.github.client.GitHubClient;
import org.yawlfoundation.yawl.mcp.a2a.github.config.GitHubConfig;
import org.yawlfoundation.yawl.mcp.a2a.github.tools.GitHubToolSpecifications;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP Server for GitHub integration in YAWL workflow engine.
 *
 * <p>This server provides GitHub integration capabilities through MCP protocol,
 * allowing YAWL workflows to interact with GitHub repositories, manage issues,
 * handle PR reviews, and trigger workflow events based on GitHub events.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><strong>Repository Management</strong> - List, get, and manage repositories</li>
 *   <li><strong>Issue Handling</strong> - Create, update, and manage GitHub issues</li>
 *   <li><strong>PR Management</strong> - Create PRs, manage reviews, and handle notifications</li>
 *   <li><strong>Webhook Integration</strong> - Handle GitHub events and trigger workflows</li>
 *   <li><strong>Collaboration</strong> - Assign issues, manage labels, and collaborate</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Configure GitHub integration via {@link GitHubConfig}:</p>
 * <pre>{@code
 * GitHubConfig config = GitHubConfig.builder()
 *     .baseUrl("https://api.github.com")
 *     .personalAccessToken("ghp_xxx")
 *     .defaultRepository("owner/repo")
 *     .build();
 *
 * GitHubMcpServer server = new GitHubMcpServer(config);
 * server.start();
 * }</pre>
 *
 * <h2>MCP Tools</h2>
 * <p>Provides GitHub-specific tools through MCP protocol:</p>
 * <ul>
 *   <li>create_issue - Create new GitHub issues</li>
 *   <li>update_issue - Update existing issues</li>
 *   <li>list_issues - List repository issues</li>
 *   <li>create_pull_request - Create new pull requests</li>
 *   <li>list_pull_requests - List repository PRs</li>
 *   <li>add_review_comment - Add review comments to PRs</li>
 *   <li>list_repositories - List accessible repositories</li>
 *   <li>get_repository - Get repository details</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see GitHubConfig
 * @see GitHubClient
 * @see GitHubToolSpecifications
 */
public class GitHubMcpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubMcpServer.class.getName());
    private static final String SERVER_NAME = "yawl-github-mcp-server";
    private static final String SERVER_VERSION = "6.0.0";

    private final GitHubConfig config;
    private final GitHubClient gitHubClient;
    private final McpTransportConfig transportConfig;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private McpSyncServer stdioServer;
    private McpSyncServer httpServer;
    private WebMvcSseServerTransportProvider httpTransportProvider;
    private ObjectMapper objectMapper;

    /**
     * Construct a GitHub MCP Server with configuration.
     *
     * @param config GitHub configuration
     * @throws IllegalArgumentException if config is null
     */
    public GitHubMcpServer(GitHubConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("GitHubConfig is required");
        }
        this.config = config;
        this.gitHubClient = new GitHubClient(config);
        this.transportConfig = McpTransportConfig.defaults();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Construct a GitHub MCP Server with full configuration.
     *
     * @param config GitHub configuration
     * @param transportConfig MCP transport configuration
     * @throws IllegalArgumentException if any required parameter is null
     */
    public GitHubMcpServer(GitHubConfig config, McpTransportConfig transportConfig) {
        if (config == null) {
            throw new IllegalArgumentException("GitHubConfig is required");
        }
        if (transportConfig == null) {
            throw new IllegalArgumentException("McpTransportConfig is required");
        }
        this.config = config;
        this.gitHubClient = new GitHubClient(config);
        this.transportConfig = transportConfig;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
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
     * @throws IOException if connection to GitHub API fails or server startup fails
     */
    public void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            // Test GitHub API connection
            try {
                gitHubClient.testConnection();
                LOGGER.info("Connected to GitHub API at {}", config.baseUrl());
            } catch (IOException e) {
                running.set(false);
                throw new IOException(
                    "Failed to connect to GitHub API at " + config.baseUrl() + ". " +
                    "Verify the API token and network connectivity. " +
                    "Error: " + e.getMessage(), e);
            }

            if (transportConfig.enableStdio()) {
                startStdioTransport();
            }

            if (transportConfig.enabled()) {
                startHttpTransport();
            }

            LOGGER.info("GitHub MCP Server v{} started", SERVER_VERSION);
            LOGGER.info("Transports: STDIO={}, HTTP={}",
                transportConfig.enableStdio(), transportConfig.enabled());
            LOGGER.info("GitHub API URL: {}", config.baseUrl());
        }
    }

    /**
     * Stop the MCP server gracefully.
     *
     * <p>Closes all active transport connections.</p>
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("GitHub MCP Server shutting down");

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

            LOGGER.info("GitHub MCP Server stopped");
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
     * @return the RouterFunction for MCP HTTP endpoints, or null if HTTP transport is disabled
     */
    public RouterFunction<ServerResponse> getRouterFunction() {
        if (httpTransportProvider != null) {
            return httpTransportProvider.getRouterFunction();
        }
        return null;
    }

    /**
     * Get the GitHub client instance.
     *
     * @return the GitHub client
     */
    public GitHubClient getGitHubClient() {
        return gitHubClient;
    }

    /**
     * Get the GitHub configuration.
     *
     * @return the GitHub configuration
     */
    public GitHubConfig getConfig() {
        return config;
    }

    /**
     * Get the transport configuration.
     *
     * @return the transport configuration
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

    /**
     * Build all GitHub tools for MCP registration.
     */
    private McpServerFeatures.SyncToolSpecification[] buildAllTools() {
        return GitHubToolSpecifications.createAll(gitHubClient)
            .toArray(new McpServerFeatures.SyncToolSpecification[0]);
    }

    private void startStdioTransport() {
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);

        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);

        stdioServer = McpServer.sync(transportProvider)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(createServerCapabilities())
            .instructions(buildServerInstructions())
            .tools(buildAllTools())
            .build();

        LOGGER.info("STDIO transport started");
        System.err.println("GitHub MCP Server: STDIO transport active");
    }

    private void startHttpTransport() {
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);

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
            .capabilities(createServerCapabilities())
            .instructions(buildServerInstructions())
            .tools(buildAllTools())
            .build();

        LOGGER.info("HTTP/SSE transport started on port {}", transportConfig.port());
        LOGGER.info("SSE endpoint: {}", transportConfig.fullSsePath());
        LOGGER.info("Message endpoint: {}", transportConfig.fullMessagePath());
        System.err.println("GitHub MCP Server: HTTP/SSE transport active on port " + transportConfig.port());
    }

    // =========================================================================
    // Server capabilities and configuration
    // =========================================================================

    private McpServerFeatures.ServerCapabilities createServerCapabilities() {
        return McpServerFeatures.ServerCapabilities.builder()
            .tools(true)
            .resources(false)
            .prompts(false)
            .completion(false)
            .build();
    }

    private String buildServerInstructions() {
        return """
            YAWL GitHub Integration MCP Server v%s.

            Use tools to manage GitHub repositories, issues, and pull requests.
            Create issues from workflow events, review PRs, and collaborate on code.

            Capabilities: 8 GitHub tools, MCP 2025-11-25 compliant.

            Supported operations:
            - Repository management (list, get)
            - Issue management (create, update, list)
            - Pull request management (create, list, add review comments)
            - Webhook integration for GitHub events

            Transports: STDIO and HTTP/SSE supported.
            """.formatted(SERVER_VERSION);
    }

    // =========================================================================
    // Static factory methods
    // =========================================================================

    /**
     * Create a GitHub MCP Server with default transport configuration.
     *
     * @param config GitHub configuration
     * @return configured server instance
     */
    public static GitHubMcpServer createDefault(GitHubConfig config) {
        return new GitHubMcpServer(config, McpTransportConfig.defaults());
    }

    /**
     * Create a GitHub MCP Server with STDIO transport only.
     *
     * @param config GitHub configuration
     * @return configured server instance with STDIO only
     */
    public static GitHubMcpServer createStdioOnly(GitHubConfig config) {
        return new GitHubMcpServer(config, McpTransportConfig.stdioOnly());
    }

    /**
     * Create a GitHub MCP Server with HTTP transport only (no STDIO).
     *
     * @param config GitHub configuration
     * @param port HTTP server port
     * @return configured server instance with HTTP only
     */
    public static GitHubMcpServer createHttpOnly(GitHubConfig config, int port) {
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
        return new GitHubMcpServer(config, transportConfig);
    }
}