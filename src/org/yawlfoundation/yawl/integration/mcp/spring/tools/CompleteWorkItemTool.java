package org.yawlfoundation.yawl.integration.mcp.spring.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSessionManager;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpTool;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Spring-managed MCP tool for completing YAWL work items.
 *
 * <p>This tool marks a YAWL work item as completed and returns the result.</p>
 *
 * <h2>Usage</h2>
 * <p>This tool is automatically registered when Spring component scanning
 * is enabled for this package.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class CompleteWorkItemTool implements YawlMcpTool {

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpSessionManager sessionManager;

    /**
     * Construct tool with injected dependencies.
     * Spring automatically provides these dependencies when the tool is registered as a bean.
     *
     * @param interfaceBClient YAWL InterfaceB client for completing work items
     * @param sessionManager session manager for obtaining session handles
     */
    public CompleteWorkItemTool(InterfaceB_EnvironmentBasedClient interfaceBClient,
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
        return "yawl_complete_workitem";
    }

    @Override
    public String getDescription() {
        return "Complete a YAWL work item. " +
               "This is a Spring-managed tool demonstrating dependency injection " +
               "and real YAWL engine integration. Returns completion status.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();

        props.put("caseId", Map.of(
            "type", "string",
            "description", "Workflow case identifier containing the work item (required)"
        ));

        props.put("workItemId", Map.of(
            "type", "string",
            "description", "Work item identifier to complete (required)"
        ));

        props.put("data", Map.of(
            "type", "string",
            "description", "XML data to be passed to the work item completion (optional)"
        ));

        List<String> required = List.of("caseId", "workItemId");

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
            String workItemId = getRequiredParam(params, "workItemId");
            String data = getOptionalParam(params, "data", "");

            // Complete work item using injected InterfaceB client and session manager
            String sessionHandle = sessionManager.getSessionHandle();
            String result = interfaceBClient.completeWorkItem(caseId, workItemId, data, sessionHandle);

            // Check for YAWL engine errors
            if (result == null || result.contains("<failure>")) {
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent("Failed to complete work item. YAWL engine response: " + result)),
                    true,  // isError
                    null,   // structuredContent
                    null    // meta
                );
            }

            // Return success result
            String successMsg = String.format("Work item %s in case %s has been completed successfully.", workItemId, caseId);
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
                List.of(new McpSchema.TextContent("Error completing work item: " + e.getMessage())),
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
}