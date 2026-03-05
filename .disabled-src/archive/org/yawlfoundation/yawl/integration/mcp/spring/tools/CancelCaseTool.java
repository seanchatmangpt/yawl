package org.yawlfoundation.yawl.integration.mcp.spring.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSessionManager;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpTool;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Spring-managed MCP tool for canceling YAWL workflow cases.
 *
 * <p>This tool cancels an existing YAWL workflow case and returns the result.</p>
 *
 * <h2>Usage</h2>
 * <p>This tool is automatically registered when Spring component scanning
 * is enabled for this package.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class CancelCaseTool implements YawlMcpTool {

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpSessionManager sessionManager;

    /**
     * Construct tool with injected dependencies.
     * Spring automatically provides these dependencies when the tool is registered as a bean.
     *
     * @param interfaceBClient YAWL InterfaceB client for canceling cases
     * @param sessionManager session manager for obtaining session handles
     */
    public CancelCaseTool(InterfaceB_EnvironmentBasedClient interfaceBClient,
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
        return "yawl_cancel_case";
    }

    @Override
    public String getDescription() {
        return "Cancel an existing YAWL workflow case. " +
               "This is a Spring-managed tool demonstrating dependency injection " +
               "and real YAWL engine integration. Returns cancellation status.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();

        props.put("caseId", Map.of(
            "type", "string",
            "description", "Workflow case identifier to cancel (required)"
        ));

        List<String> required = List.of("caseId");

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
            String caseId = getRequiredParam(params, "caseId");

            // Cancel case using injected InterfaceB client and session manager
            String sessionHandle = sessionManager.getSessionHandle();
            String result = interfaceBClient.cancelCase(caseId, sessionHandle);

            // Check for YAWL engine errors
            if (result == null || result.contains("<failure>")) {
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Failed to cancel case. YAWL engine response: " + result)),
                    true,  // isError
                    null,   // structuredContent
                    null    // meta
                );
            }

            // Return success result
            String successMsg = String.format("Case %s has been cancelled successfully.", caseId);
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(successMsg)),
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
                List.of(new McpSchema.TextContent("Error cancelling case: " + e.getMessage())),
                true,  // isError
                null,   // structuredContent
                null    // meta
            );
        }
    }

    @Override
    public int getPriority() {
        // Medium priority for this tool
        return 50;
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
}