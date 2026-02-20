package org.yawlfoundation.yawl.integration.mcp.spring;

import java.util.logging.Logger;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spring.resources.SpecificationsResource;
import org.yawlfoundation.yawl.integration.mcp.spring.tools.LaunchCaseTool;
import org.yawlfoundation.yawl.integration.mcp.zai.ZaiFunctionService;

/**
 * Spring Boot application for YAWL MCP Server.
 *
 * <p>This is a complete, runnable Spring application that demonstrates the
 * Spring AI MCP integration for YAWL. It provides a working example of how to:</p>
 * <ul>
 *   <li>Configure YAWL MCP integration via application.yml or environment variables</li>
 *   <li>Auto-register core YAWL MCP tools and resources</li>
 *   <li>Register custom Spring-managed tools and resources</li>
 *   <li>Manage YAWL engine session lifecycle via Spring</li>
 *   <li>Use dependency injection for YAWL clients</li>
 * </ul>
 *
 * <h2>Running the Application</h2>
 * <pre>{@code
 * # Via Maven
 * mvn spring-boot:run
 *
 * # Via Java
 * java -jar yawl-5.2.jar
 *
 * # With configuration
 * java -jar yawl-5.2.jar --yawl.mcp.engine-url=http://localhost:8080/yawl
 * }</pre>
 *
 * <h2>Configuration</h2>
 * <p>Configure via application.yml, application.properties, or environment variables:</p>
 * <pre>{@code
 * # application.yml
 * yawl:
 *   mcp:
 *     engine-url: http://localhost:8080/yawl
 *     username: ${YAWL_USERNAME:admin}
 *     password: ${YAWL_PASSWORD}  # required - no default; see SECURITY.md
 *     transport: stdio
 *
 * # Environment variables (alternative)
 * export YAWL_ENGINE_URL=http://localhost:8080/yawl
 * export YAWL_USERNAME=admin
 * export YAWL_PASSWORD=<your-password>  # required - no default; see SECURITY.md
 * }</pre>
 *
 * <h2>Bean Configuration</h2>
 * <p>This application manually configures all necessary beans to demonstrate
 * the Spring integration architecture. In a real Spring Boot application, you
 * would use {@code @EnableYawlMcp} and component scanning for auto-configuration.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@SpringBootApplication
public class YawlMcpSpringApplication {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpSpringApplication.class.getName());

    /**
     * Main entry point for the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        LOGGER.info("Starting YAWL MCP Spring Application");

        try {
            // Load configuration from environment variables
            YawlMcpProperties properties = loadPropertiesFromEnvironment();
            validateConfiguration(properties);

            LOGGER.info("Configuration loaded:");
            LOGGER.info("  Engine URL: " + properties.getEngineUrl());
            LOGGER.info("  Username: " + properties.getUsername());
            LOGGER.info("  Transport: " + properties.getTransport());
            LOGGER.info("  Z.AI enabled: " + properties.getZai().isEnabled());

            // Create configuration and initialize beans
            YawlMcpConfiguration config = new YawlMcpConfiguration(properties);

            // Create YAWL clients
            InterfaceB_EnvironmentBasedClient interfaceBClient = config.interfaceBClient();
            InterfaceA_EnvironmentBasedClient interfaceAClient = config.interfaceAClient();

            // Create session manager (connects to YAWL engine)
            YawlMcpSessionManager sessionManager = config.sessionManager(interfaceBClient);

            // Create Z.AI service (if enabled)
            ZaiFunctionService zaiFunctionService = config.zaiFunctionService();

            // Create tool registry and register core + custom tools
            YawlMcpToolRegistry toolRegistry = config.toolRegistry(
                interfaceBClient,
                interfaceAClient,
                sessionManager,
                zaiFunctionService
            );

            // Register custom example tool
            LaunchCaseTool customTool = new LaunchCaseTool(interfaceBClient, sessionManager);
            toolRegistry.registerTool(customTool);
            LOGGER.info("Registered custom tool: " + customTool.getName());

            // Create resource registry and register core + custom resources
            YawlMcpResourceRegistry resourceRegistry = config.resourceRegistry(
                interfaceBClient,
                sessionManager
            );

            // Register custom example resource
            SpecificationsResource customResource = new SpecificationsResource(
                interfaceBClient,
                sessionManager
            );
            resourceRegistry.registerResource(customResource);
            LOGGER.info("Registered custom resource: " + customResource.getUri());

            // Create and start MCP server
            LOGGER.info("Starting MCP server...");
            var mcpServer = config.mcpServer(
                toolRegistry,
                resourceRegistry,
                config.loggingHandler()
            );

            // Log summary
            LOGGER.info("YAWL MCP Spring Server started successfully!");
            LOGGER.info("Total tools: " + toolRegistry.getToolCount() +
                       " (" + toolRegistry.getCustomToolCount() + " custom)");
            LOGGER.info("Total resources: " + resourceRegistry.getResourceCount() +
                       " (" + resourceRegistry.getCustomResourceCount() + " custom)");
            LOGGER.info("Total resource templates: " + resourceRegistry.getTemplateCount() +
                       " (" + resourceRegistry.getCustomTemplateCount() + " custom)");

            // Register shutdown hook for clean disconnect
            Runtime.getRuntime().addShutdownHook(
                Thread.ofVirtual().unstarted(() -> {
                    LOGGER.info("Shutting down YAWL MCP Spring Server...");
                    sessionManager.disconnect();
                    if (mcpServer != null) {
                        mcpServer.closeGracefully();
                    }
                    LOGGER.info("Shutdown complete");
            }));

            // Keep application running
            LOGGER.info("YAWL MCP Server is now running. Press Ctrl+C to stop.");
            Thread.currentThread().join();

        } catch (IllegalArgumentException e) {
            LOGGER.severe("Configuration error: " + e.getMessage());
            System.err.println("\nConfiguration error: " + e.getMessage());
            System.err.println("\nRequired environment variables:");
            System.err.println("  YAWL_ENGINE_URL - YAWL engine base URL (e.g., http://localhost:8080/yawl)");
            System.err.println("  YAWL_USERNAME   - YAWL admin username (default: admin)");
            System.err.println("  YAWL_PASSWORD   - YAWL admin password (required - no default; see SECURITY.md)");
            System.exit(1);

        } catch (RuntimeException e) {
            LOGGER.severe("Failed to start YAWL MCP Server: " + e.getMessage());
            System.err.println("\nFailed to start YAWL MCP Server: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            System.exit(1);

        } catch (InterruptedException e) {
            LOGGER.info("Application interrupted");
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Load configuration properties from environment variables.
     * This demonstrates manual property loading; in a real Spring Boot app,
     * this would be handled automatically via @ConfigurationProperties.
     *
     * @return configured properties
     */
    private static YawlMcpProperties loadPropertiesFromEnvironment() {
        YawlMcpProperties properties = new YawlMcpProperties();

        // Load from environment variables with defaults
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        if (engineUrl != null && !engineUrl.isEmpty()) {
            properties.setEngineUrl(engineUrl);
        }

        String username = System.getenv("YAWL_USERNAME");
        if (username != null && !username.isEmpty()) {
            properties.setUsername(username);
        }

        String password = System.getenv("YAWL_PASSWORD");
        if (password != null && !password.isEmpty()) {
            properties.setPassword(password);
        }

        // Z.AI configuration
        String zaiApiKey = System.getenv("ZAI_API_KEY");
        if (zaiApiKey == null || zaiApiKey.isEmpty()) {
            zaiApiKey = System.getenv("ZHIPU_API_KEY");
        }
        if (zaiApiKey != null && !zaiApiKey.isEmpty()) {
            properties.getZai().setApiKey(zaiApiKey);
            properties.getZai().setEnabled(true);
        } else {
            properties.getZai().setEnabled(false);
        }

        return properties;
    }

    /**
     * Validate configuration properties.
     *
     * @param properties properties to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    private static void validateConfiguration(YawlMcpProperties properties) {
        if (properties.getEngineUrl() == null || properties.getEngineUrl().isEmpty()) {
            throw new IllegalArgumentException(
                "YAWL engine URL is required. Set YAWL_ENGINE_URL environment variable.");
        }

        if (properties.getUsername() == null || properties.getUsername().isEmpty()) {
            throw new IllegalArgumentException(
                "YAWL username is required. Set YAWL_USERNAME environment variable.");
        }

        if (properties.getPassword() == null || properties.getPassword().isEmpty()) {
            throw new IllegalArgumentException(
                "YAWL password is required. Set YAWL_PASSWORD environment variable.");
        }
    }
}
