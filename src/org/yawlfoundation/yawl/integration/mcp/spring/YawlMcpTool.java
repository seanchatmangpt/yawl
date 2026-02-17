package org.yawlfoundation.yawl.integration.mcp.spring;

import org.yawlfoundation.yawl.integration.mcp.stub.McpSchema;

import java.util.Map;

/**
 * Interface for Spring-managed YAWL MCP tools.
 *
 * <p>Implement this interface to create custom MCP tools that integrate with
 * YAWL workflows. Spring will automatically detect and register implementations
 * as MCP tools when {@code @Component} or similar annotations are used.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * @Component
 * public class LaunchCaseTool implements YawlMcpTool {
 *
 *     private final InterfaceB_EnvironmentBasedClient interfaceBClient;
 *     private final YawlMcpSessionManager sessionManager;
 *
 *     @Autowired
 *     public LaunchCaseTool(InterfaceB_EnvironmentBasedClient interfaceBClient,
 *                           YawlMcpSessionManager sessionManager) {
 *         this.interfaceBClient = interfaceBClient;
 *         this.sessionManager = sessionManager;
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "yawl_launch_case";
 *     }
 *
 *     @Override
 *     public String getDescription() {
 *         return "Launch a new YAWL workflow case from a loaded specification";
 *     }
 *
 *     @Override
 *     public McpSchema.JsonSchema getInputSchema() {
 *         Map<String, Object> props = Map.of(
 *             "specIdentifier", Map.of("type", "string", "description", "Workflow spec ID"),
 *             "caseData", Map.of("type", "string", "description", "XML case data")
 *         );
 *         return new McpSchema.JsonSchema("object", props, List.of("specIdentifier"),
 *                                          false, null, null);
 *     }
 *
 *     @Override
 *     public McpSchema.CallToolResult execute(Map<String, Object> params) {
 *         try {
 *             String specId = (String) params.get("specIdentifier");
 *             String caseData = (String) params.get("caseData");
 *             String sessionHandle = sessionManager.getSessionHandle();
 *
 *             YSpecificationID ySpecId = new YSpecificationID(specId, "0.1", specId);
 *             String caseId = interfaceBClient.launchCase(ySpecId, caseData, null, sessionHandle);
 *
 *             return new McpSchema.CallToolResult("Case launched: " + caseId, false);
 *         } catch (Exception e) {
 *             return new McpSchema.CallToolResult("Error: " + e.getMessage(), true);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Dependency Injection</h2>
 * <p>All YAWL clients and services can be autowired:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient}
 *       - for runtime workflow operations</li>
 *   <li>{@link org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient}
 *       - for design-time operations</li>
 *   <li>{@link YawlMcpSessionManager} - for session handle management</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.zai.ZaiFunctionService}
 *       - for AI reasoning (if enabled)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see YawlMcpConfiguration
 * @see YawlMcpToolRegistry
 */
public interface YawlMcpTool {

    /**
     * Get the unique name of this tool.
     * Must follow MCP naming conventions (lowercase with underscores).
     *
     * @return tool name (e.g., "yawl_launch_case")
     */
    String getName();

    /**
     * Get the human-readable description of this tool.
     * Should clearly explain what the tool does and when to use it.
     *
     * @return tool description
     */
    String getDescription();

    /**
     * Get the JSON schema defining the tool's input parameters.
     * Must be a valid MCP JSON schema with type, properties, and required fields.
     *
     * @return JSON schema for tool input
     */
    McpSchema.JsonSchema getInputSchema();

    /**
     * Execute the tool with the provided parameters.
     *
     * <p>Implementations should:</p>
     * <ul>
     *   <li>Validate all required parameters</li>
     *   <li>Use injected YAWL clients for real engine operations (no stubs/mocks)</li>
     *   <li>Return meaningful results or error messages</li>
     *   <li>Set {@code isError=true} in the result for failures</li>
     * </ul>
     *
     * @param params input parameters as defined by getInputSchema()
     * @return tool execution result
     */
    McpSchema.CallToolResult execute(Map<String, Object> params);

    /**
     * Get the priority of this tool for registration order.
     * Lower values are registered first. Default is 0.
     *
     * @return priority value (default: 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Check if this tool is currently enabled.
     * Can be used for conditional tool availability based on configuration or state.
     *
     * @return true if tool should be registered (default: true)
     */
    default boolean isEnabled() {
        return true;
    }
}
