package org.yawlfoundation.yawl.integration.mcp.spring;

import java.io.IOException;
import java.util.logging.Logger;

import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.mcp.server.YawlServerCapabilities;
import org.yawlfoundation.yawl.integration.mcp.zai.ZaiFunctionService;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;

/**
 * Spring configuration for YAWL MCP integration.
 *
 * <p>This configuration class sets up all Spring-managed beans required for
 * YAWL MCP integration, including:</p>
 * <ul>
 *   <li>YAWL engine clients (InterfaceA, InterfaceB)</li>
 *   <li>MCP server and transport</li>
 *   <li>Session manager for YAWL connections</li>
 *   <li>Tool and resource registries</li>
 *   <li>Z.AI integration (if enabled)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>This configuration is automatically imported when using {@code @EnableYawlMcp}
 * annotation. For manual configuration, import this class or its beans.</p>
 *
 * <pre>{@code
 * // Automatic - Spring Boot
 * @SpringBootApplication
 * @EnableYawlMcp
 * public class YawlMcpApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(YawlMcpApplication.class, args);
 *     }
 * }
 *
 * // Manual - Plain Spring
 * @Configuration
 * @Import(YawlMcpConfiguration.class)
 * public class MyConfig {
 *     // Custom beans can override defaults
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see EnableYawlMcp
 * @see YawlMcpProperties
 */
public class YawlMcpConfiguration {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpConfiguration.class.getName());

    private static final String SERVER_NAME = "yawl-mcp-server-spring";
    private static final String SERVER_VERSION = "6.0.0";

    private final YawlMcpProperties properties;

    /**
     * Construct configuration with properties.
     *
     * @param properties YAWL MCP configuration properties
     */
    public YawlMcpConfiguration(YawlMcpProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("YawlMcpProperties is required");
        }
        this.properties = properties;
        validateProperties();
    }

    /**
     * Create InterfaceB client bean for YAWL runtime operations.
     * Singleton bean shared across all tools and resources.
     *
     * @return configured InterfaceB client
     */
    public InterfaceB_EnvironmentBasedClient interfaceBClient() {
        String interfaceBUrl = properties.getEngineUrl() + "/ib";
        LOGGER.info("Creating InterfaceB client: " + interfaceBUrl);
        return new InterfaceB_EnvironmentBasedClient(interfaceBUrl);
    }

    /**
     * Create InterfaceA client bean for YAWL design-time operations.
     * Singleton bean shared across all tools that need specification management.
     *
     * @return configured InterfaceA client
     */
    public InterfaceA_EnvironmentBasedClient interfaceAClient() {
        String interfaceAUrl = properties.getEngineUrl() + "/ia";
        LOGGER.info("Creating InterfaceA client: " + interfaceAUrl);
        return new InterfaceA_EnvironmentBasedClient(interfaceAUrl);
    }

    /**
     * Create session manager bean for YAWL connection lifecycle.
     * Automatically connects on initialization and disconnects on destruction.
     *
     * @param interfaceBClient InterfaceB client for session operations
     * @return configured session manager
     */
    public YawlMcpSessionManager sessionManager(InterfaceB_EnvironmentBasedClient interfaceBClient) {
        LOGGER.info("Creating YAWL MCP session manager");
        YawlMcpSessionManager sessionManager = new YawlMcpSessionManager(interfaceBClient, properties);

        // Register lifecycle callbacks
        return new YawlMcpSessionManager(interfaceBClient, properties) {
            {
                // Post-construct: connect to YAWL engine
                try {
                    connect();
                } catch (IOException e) {
                    throw new RuntimeException(
                        "Failed to connect to YAWL engine during initialization: " + e.getMessage(), e);
                }
            }

            @Override
            protected void finalize() throws Throwable {
                try {
                    // Pre-destroy: disconnect from YAWL engine
                    disconnect();
                } finally {
                    super.finalize();
                }
            }
        };
    }

    /**
     * Create Z.AI function service bean (if enabled and API key is provided).
     *
     * @return Z.AI service or null if disabled
     */
    public ZaiFunctionService zaiFunctionService() {
        if (!properties.getZai().isEnabled()) {
            LOGGER.info("Z.AI integration disabled");
            return null;
        }

        String apiKey = properties.getZai().getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            // Try environment variables
            apiKey = System.getenv("ZAI_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("ZHIPU_API_KEY");
            }
        }

        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.warning("Z.AI integration enabled but no API key provided. " +
                          "Set yawl.mcp.zai.api-key or ZAI_API_KEY environment variable.");
            return null;
        }

        try {
            LOGGER.info("Creating Z.AI function service");
            return new ZaiFunctionService(
                apiKey,
                properties.getEngineUrl(),
                properties.getUsername(),
                properties.getPassword()
            );
        } catch (Exception e) {
            LOGGER.warning("Failed to initialize Z.AI service: " + e.getMessage());
            return null;
        }
    }

    /**
     * Create MCP logging handler bean.
     *
     * @return logging handler for MCP notifications
     */
    public McpLoggingHandler loggingHandler() {
        return new McpLoggingHandler();
    }

    /**
     * Create tool registry bean for managing Spring-discovered MCP tools.
     *
     * @param interfaceBClient InterfaceB client
     * @param interfaceAClient InterfaceA client
     * @param sessionManager session manager
     * @param zaiFunctionService Z.AI service (optional)
     * @return configured tool registry
     */
    public YawlMcpToolRegistry toolRegistry(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            YawlMcpSessionManager sessionManager,
            ZaiFunctionService zaiFunctionService) {

        LOGGER.info("Creating YAWL MCP tool registry");
        return new YawlMcpToolRegistry(
            interfaceBClient,
            interfaceAClient,
            sessionManager,
            zaiFunctionService
        );
    }

    /**
     * Create resource registry bean for managing Spring-discovered MCP resources.
     *
     * @param interfaceBClient InterfaceB client
     * @param sessionManager session manager
     * @return configured resource registry
     */
    public YawlMcpResourceRegistry resourceRegistry(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            YawlMcpSessionManager sessionManager) {

        LOGGER.info("Creating YAWL MCP resource registry");
        return new YawlMcpResourceRegistry(interfaceBClient, sessionManager);
    }

    /**
     * Create MCP server bean with STDIO transport.
     * Automatically registers all tools and resources from registries.
     *
     * @param toolRegistry tool registry
     * @param resourceRegistry resource registry
     * @param loggingHandler logging handler
     * @return configured MCP sync server
     */
    public McpSyncServer mcpServer(
            YawlMcpToolRegistry toolRegistry,
            YawlMcpResourceRegistry resourceRegistry,
            McpLoggingHandler loggingHandler) {

        LOGGER.info("Creating YAWL MCP server (transport: " + properties.getTransport() + ")");

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);

        if (properties.getTransport() == YawlMcpProperties.Transport.STDIO) {
            StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(jsonMapper);

            McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(YawlServerCapabilities.full())
                .instructions(buildInstructions())
                .tools(toolRegistry.getAllToolSpecifications())
                .resources(resourceRegistry.getAllResourceSpecifications())
                .resourceTemplates(resourceRegistry.getAllResourceTemplateSpecifications())
                .build();

            loggingHandler.info(server, "YAWL MCP Server (Spring) started with STDIO transport");
            LOGGER.info("YAWL MCP Server started: " + toolRegistry.getToolCount() + " tools, " +
                       resourceRegistry.getResourceCount() + " resources, " +
                       resourceRegistry.getTemplateCount() + " resource templates");

            return server;

        } else if (properties.getTransport() == YawlMcpProperties.Transport.HTTP) {
            HttpTransportProvider httpTransport = new HttpTransportProvider(
                properties.getHttpPort(),
                jsonMapper
            );
            McpSyncServer server = McpServer.sync(httpTransport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(YawlServerCapabilities.full())
                .tools(toolRegistry.getAllToolSpecifications())
                .resources(resourceRegistry.getAllResourceSpecifications())
                .build();
            loggingHandler.info(server, "YAWL MCP Server started with HTTP transport on port " + properties.getHttpPort());
            return server;
        } else {
            throw new IllegalStateException("Unknown transport type: " + properties.getTransport());
        }
    }

    /**
     * Build server instructions text.
     */
    private String buildInstructions() {
        return "YAWL Workflow Engine MCP Server (Spring) v" + SERVER_VERSION + ". " +
               "Use the provided tools to launch and manage workflow cases, " +
               "query and upload specifications, checkout and complete work items. " +
               "Resources provide read-only access to specifications, cases, and work items. " +
               "All operations are backed by real YAWL engine calls.";
    }

    /**
     * Validate required configuration properties.
     */
    private void validateProperties() {
        if (properties.getEngineUrl() == null || properties.getEngineUrl().isEmpty()) {
            throw new IllegalArgumentException(
                "yawl.mcp.engine-url is required. " +
                "Set it in application.yml or via YAWL_ENGINE_URL environment variable.");
        }

        if (properties.getUsername() == null || properties.getUsername().isEmpty()) {
            throw new IllegalArgumentException(
                "yawl.mcp.username is required. " +
                "Set it in application.yml or via YAWL_USERNAME environment variable (default: admin).");
        }

        if (properties.getPassword() == null || properties.getPassword().isEmpty()) {
            throw new IllegalArgumentException(
                "yawl.mcp.password is required. " +
                "SECURITY: Set via YAWL_PASSWORD environment variable. Never hardcode credentials. " +
                "See deployment runbook for credential management.");
        }

        LOGGER.info("YAWL MCP configuration validated: " + properties.getEngineUrl());
    }
}
