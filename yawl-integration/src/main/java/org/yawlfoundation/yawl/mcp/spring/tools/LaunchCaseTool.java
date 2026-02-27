package org.yawlfoundation.yawl.integration.mcp.spring.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSessionManager;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpTool;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Spring-managed MCP tool for launching YAWL workflow cases.
 *
 * <p>This is a working example of a Spring-managed MCP tool that demonstrates:</p>
 * <ul>
 *   <li>Dependency injection of YAWL clients</li>
 *   <li>Integration with {@link YawlMcpSessionManager}</li>
 *   <li>Real YAWL engine operations (no stubs/mocks)</li>
 *   <li>Proper error handling and result formatting</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>This tool is automatically registered when Spring component scanning
 * is enabled for this package. It can also be manually registered as a Spring bean:</p>
 * <pre>{@code
 * @Configuration
 * public class CustomToolConfig {
 *     @Bean
 *     public LaunchCaseTool launchCaseTool(
 *             InterfaceB_EnvironmentBasedClient interfaceBClient,
 *             YawlMcpSessionManager sessionManager) {
 *         return new LaunchCaseTool(interfaceBClient, sessionManager);
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class LaunchCaseTool implements YawlMcpTool {

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpSessionManager sessionManager;

    /**
     * Construct tool with injected dependencies.
     * Spring automatically provides these dependencies when the tool is registered as a bean.
     *
     * @param interfaceBClient YAWL InterfaceB client for launching cases
     * @param sessionManager session manager for obtaining session handles
     */
    public LaunchCaseTool(InterfaceB_EnvironmentBasedClient interfaceBClient,
                          YawlMcpSessionManager sessionManager) {
        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (sessionManager == null) {
            throw new IllegalArgumentException("sessionManager is required");
        }

        this.interfaceBClient = interfaceBClient;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getName() {
        return "yawl_launch_case_spring";
    }

    @Override
    public String getDescription() {
        return "Launch a new YAWL workflow case from a loaded specification. " +
               "This is a Spring-managed example tool demonstrating dependency injection " +
               "and real YAWL engine integration. Returns the case ID of the launched instance.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();

        props.put("specIdentifier", Map.of(
            "type", "string",
            "description", "Workflow specification identifier (required)"
        ));

        props.put("specVersion", Map.of(
            "type", "string",
            "description", "Specification version (optional, default: 0.1)"
        ));

        props.put("specUri", Map.of(
            "type", "string",
            "description", "Specification URI (optional, defaults to identifier)"
        ));

        props.put("caseData", Map.of(
            "type", "string",
            "description", "XML case input data (optional, e.g., <data><param>value</param></data>)"
        ));

        List<String> required = List.of("specIdentifier");

        return new McpSchema.JsonSchema(
            "object",
            props,
            required,
            false,  // additionalProperties
            null,   // items
            null    // oneOf
        );
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> params) {
        try {
            // Extract and validate parameters
            String specId = getRequiredParam(params, "specIdentifier");
            String specVersion = getOptionalParam(params, "specVersion", "0.1");
            String specUri = getOptionalParam(params, "specUri", specId);
            String caseData = getOptionalParam(params, "caseData", null);

            // Create specification ID
            YSpecificationID ySpecId = new YSpecificationID(specId, specVersion, specUri);

            // Launch case using injected InterfaceB client and session manager
            String sessionHandle = sessionManager.getSessionHandle();
            String caseId = interfaceBClient.launchCase(
                ySpecId,
                caseData,
                null,  // log data
                sessionHandle
            );

            // Check for YAWL engine errors
            if (caseId == null || caseId.contains("<failure>")) {
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Failed to launch case. YAWL engine response: " + caseId)),
                    false,  // isError
                    null,   // structuredContent
                    null    // meta
                );
            }

            // Return success result
            String result = formatSuccessResult(caseId, specId, specVersion, specUri);
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(result)),
                false,  // isError
                null,   // structuredContent
                null    // meta
            );

        } catch (IllegalArgumentException e) {
            // Parameter validation errors
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("Invalid parameters: " + e.getMessage())),
                true,  // isError
                null,   // structuredContent
                null    // meta
            );
        } catch (IllegalStateException e) {
            // Session/connection errors
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("YAWL connection error: " + e.getMessage())),
                true,  // isError
                null,   // structuredContent
                null    // meta
            );
        } catch (Exception e) {
            // Unexpected errors
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("Error launching case: " + e.getMessage())),
                true,  // isError
                null,   // structuredContent
                null    // meta
            );
        }
    }

    @Override
    public int getPriority() {
        // Lower priority than core tools (registered after)
        return 100;
    }

    @Override
    public boolean isEnabled() {
        // Always enabled - can be made conditional based on configuration
        return true;
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Extract a required parameter from the params map.
     *
     * @param params parameter map
     * @param name parameter name
     * @return parameter value
     * @throws IllegalArgumentException if parameter is missing
     */
    private String getRequiredParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required parameter missing: " + name);
        }
        return value.toString();
    }

    /**
     * Extract an optional parameter from the params map.
     *
     * @param params parameter map
     * @param name parameter name
     * @param defaultValue default value if parameter is missing
     * @return parameter value or default
     */
    private String getOptionalParam(Map<String, Object> params, String name, String defaultValue) {
        Object value = params.get(name);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Format a success result message.
     *
     * @param caseId launched case ID
     * @param specId specification identifier
     * @param specVersion specification version
     * @param specUri specification URI
     * @return formatted success message
     */
    private String formatSuccessResult(String caseId, String specId,
                                       String specVersion, String specUri) {
        StringBuilder sb = new StringBuilder();
        sb.append("Case launched successfully via Spring-managed tool\n\n");
        sb.append("Case ID: ").append(caseId).append("\n");
        sb.append("Specification: ").append(specId).append("\n");
        sb.append("Version: ").append(specVersion).append("\n");
        sb.append("URI: ").append(specUri).append("\n\n");
        sb.append("This tool demonstrates:\n");
        sb.append("- Spring dependency injection for YAWL clients\n");
        sb.append("- Session management via YawlMcpSessionManager\n");
        sb.append("- Real YAWL engine integration (no stubs or mocks)\n");
        sb.append("- Enterprise Java patterns (DI, lifecycle, error handling)");

        return sb.toString();
    }
}
