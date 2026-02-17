package org.yawlfoundation.yawl.integration.mcp.spring;

import org.yawlfoundation.yawl.integration.mcp.stub.McpServerFeatures;
import org.yawlfoundation.yawl.integration.mcp.stub.McpSchema;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Spring-managed registry for YAWL MCP tools.
 *
 * <p>This registry bridges YAWL's existing MCP tool implementations
 * ({@link YawlToolSpecifications}) with Spring's dependency injection
 * and lifecycle management. It also discovers and registers custom
 * Spring-managed tools that implement {@link YawlMcpTool}.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Automatic registration of all 15 core YAWL MCP tools</li>
 *   <li>Discovery of custom {@link YawlMcpTool} Spring beans</li>
 *   <li>Priority-based tool ordering</li>
 *   <li>Conditional tool registration based on {@link YawlMcpTool#isEnabled()}</li>
 *   <li>Thread-safe tool management</li>
 * </ul>
 *
 * <p>This bean is automatically configured by {@link YawlMcpConfiguration}.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see YawlMcpTool
 * @see YawlMcpConfiguration
 */
public class YawlMcpToolRegistry {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpToolRegistry.class.getName());

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final InterfaceA_EnvironmentBasedClient interfaceAClient;
    private final YawlMcpSessionManager sessionManager;
    private final ZaiFunctionService zaiFunctionService;

    private final Map<String, YawlMcpTool> customTools = new ConcurrentHashMap<>();
    private volatile List<McpServerFeatures.SyncToolSpecification> allToolSpecs;

    /**
     * Construct tool registry with required dependencies.
     *
     * @param interfaceBClient YAWL InterfaceB client
     * @param interfaceAClient YAWL InterfaceA client
     * @param sessionManager session manager
     * @param zaiFunctionService Z.AI service (optional)
     */
    public YawlMcpToolRegistry(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            YawlMcpSessionManager sessionManager,
            ZaiFunctionService zaiFunctionService) {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (interfaceAClient == null) {
            throw new IllegalArgumentException("interfaceAClient is required");
        }
        if (sessionManager == null) {
            throw new IllegalArgumentException("sessionManager is required");
        }

        this.interfaceBClient = interfaceBClient;
        this.interfaceAClient = interfaceAClient;
        this.sessionManager = sessionManager;
        this.zaiFunctionService = zaiFunctionService;

        initializeCoreTools();
    }

    /**
     * Register a custom Spring-managed tool.
     * Called automatically by Spring when {@link YawlMcpTool} beans are discovered.
     *
     * @param tool custom tool to register
     */
    public void registerTool(YawlMcpTool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool cannot be null");
        }

        String toolName = tool.getName();
        if (toolName == null || toolName.isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }

        if (!tool.isEnabled()) {
            LOGGER.info("Skipping disabled tool: " + toolName);
            return;
        }

        LOGGER.info("Registering custom MCP tool: " + toolName);
        customTools.put(toolName, tool);

        // Invalidate cached specifications
        allToolSpecs = null;
    }

    /**
     * Unregister a custom tool.
     *
     * @param toolName name of tool to unregister
     * @return true if tool was registered and removed
     */
    public boolean unregisterTool(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            return false;
        }

        LOGGER.info("Unregistering custom MCP tool: " + toolName);
        boolean removed = customTools.remove(toolName) != null;

        if (removed) {
            // Invalidate cached specifications
            allToolSpecs = null;
        }

        return removed;
    }

    /**
     * Get all tool specifications for MCP server registration.
     * Combines core YAWL tools with custom Spring-managed tools.
     *
     * @return list of all enabled tool specifications
     */
    public List<McpServerFeatures.SyncToolSpecification> getAllToolSpecifications() {
        if (allToolSpecs != null) {
            return allToolSpecs;
        }

        List<McpServerFeatures.SyncToolSpecification> specs = new ArrayList<>();

        // Add core YAWL tools from existing implementation
        specs.addAll(YawlToolSpecifications.createAll(
            interfaceBClient,
            interfaceAClient,
            sessionManager.getSessionHandle(),
            zaiFunctionService
        ));

        // Add custom Spring-managed tools
        List<YawlMcpTool> sortedTools = customTools.values().stream()
            .filter(YawlMcpTool::isEnabled)
            .sorted(Comparator.comparingInt(YawlMcpTool::getPriority))
            .collect(Collectors.toList());

        for (YawlMcpTool tool : sortedTools) {
            specs.add(createToolSpecification(tool));
        }

        allToolSpecs = specs;
        LOGGER.info("Registered " + specs.size() + " total MCP tools " +
                   "(" + customTools.size() + " custom)");

        return specs;
    }

    /**
     * Get the number of registered tools (core + custom).
     *
     * @return total tool count
     */
    public int getToolCount() {
        return getAllToolSpecifications().size();
    }

    /**
     * Get the number of custom Spring-managed tools.
     *
     * @return custom tool count
     */
    public int getCustomToolCount() {
        return customTools.size();
    }

    /**
     * Check if a tool with the given name is registered.
     *
     * @param toolName tool name to check
     * @return true if tool is registered
     */
    public boolean hasCustomTool(String toolName) {
        return customTools.containsKey(toolName);
    }

    /**
     * Initialize core YAWL tools from existing implementation.
     * Called automatically during construction.
     */
    private void initializeCoreTools() {
        int coreToolCount = zaiFunctionService != null ? 16 : 15;
        LOGGER.info("Initializing " + coreToolCount + " core YAWL MCP tools");
    }

    /**
     * Create an MCP tool specification from a Spring-managed tool.
     *
     * @param tool Spring-managed tool implementation
     * @return MCP sync tool specification
     */
    private McpServerFeatures.SyncToolSpecification createToolSpecification(YawlMcpTool tool) {
        McpSchema.Tool mcpTool = McpSchema.Tool.builder()
            .name(tool.getName())
            .description(tool.getDescription())
            .inputSchema(tool.getInputSchema())
            .build();

        return new McpServerFeatures.SyncToolSpecification(
            mcpTool,
            (exchange, args) -> {
                try {
                    return tool.execute(args);
                } catch (Exception e) {
                    LOGGER.severe("Error executing tool " + tool.getName() + ": " + e.getMessage());
                    return new McpSchema.CallToolResult(
                        "Tool execution error: " + e.getMessage(),
                        true
                    );
                }
            }
        );
    }
}
