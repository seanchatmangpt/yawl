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
 * Spring-managed MCP tool for retrieving YAWL work items.
 *
 * <p>This tool retrieves work items from a YAWL workflow case and returns them as XML.</p>
 *
 * <h2>Usage</h2>
 * <p>This tool is automatically registered when Spring component scanning
 * is enabled for this package.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GetWorkItemsTool implements YawlMcpTool {

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpSessionManager sessionManager;

    /**
     * Construct tool with injected dependencies.
     * Spring automatically provides these dependencies when the tool is registered as a bean.
     *
     * @param interfaceBClient YAWL InterfaceB client for getting work items
     * @param sessionManager session manager for obtaining session handles
     */
    public GetWorkItemsTool(InterfaceB_EnvironmentBasedClient interfaceBClient,
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
        return "yawl_get_workitems";
    }

    @Override
    public String getDescription() {
        return "Retrieve work items from a YAWL workflow case. " +
               "This is a Spring-managed tool demonstrating dependency injection " +
               "and real YAWL engine integration. Returns work items as XML.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();

        props.put("caseId", Map.of(
            "type", "string",
            "description", "Workflow case identifier (required)"
        ));

        props.put("filter", Map.of(
            "type", "string",
            "description", "Filter for work items: 'live', 'all_live', 'for_spec', 'for_task', 'for_service' (optional, default: 'live')"
        ));

        props.put("specName", Map.of(
            "type", "string",
            "description", "Specification name when filter='for_spec' (optional)"
        ));

        props.put("taskId", Map.of(
            "type", "string",
            "description", "Task ID when filter='for_task' (optional)"
        ));

        props.put("serviceUri", Map.of(
            "type", "string",
            "description", "Service URI when filter='for_service' (optional)"
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
            String filter = getOptionalParam(params, "filter", "live");
            String specName = getOptionalParam(params, "specName", null);
            String taskId = getOptionalParam(params, "taskId", null);
            String serviceUri = getOptionalParam(params, "serviceUri", null);

            // Get work items using injected InterfaceB client and session manager
            String sessionHandle = sessionManager.getSessionHandle();
            List<WorkItemRecord> workItems;

            switch (filter.toLowerCase()) {
                case "live":
                case "for_case":
                    // Get live work items for the specific case
                    workItems = interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);
                    break;
                case "all_live":
                    // Get all live work items across all cases
                    workItems = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);
                    break;
                case "for_spec":
                    // Get work items for a specification
                    if (specName == null) {
                        throw new IllegalArgumentException("specName is required when filter is 'for_spec'");
                    }
                    workItems = interfaceBClient.getWorkItemsForSpecification(specName, sessionHandle);
                    break;
                case "for_task":
                    // Get work items for a specific task
                    if (taskId == null) {
                        throw new IllegalArgumentException("taskId is required when filter is 'for_task'");
                    }
                    workItems = interfaceBClient.getWorkItemsForTask(taskId, sessionHandle);
                    break;
                case "for_service":
                    // Get work items associated with a service
                    if (serviceUri == null) {
                        throw new IllegalArgumentException("serviceUri is required when filter is 'for_service'");
                    }
                    workItems = interfaceBClient.getWorkItemsForService(serviceUri, sessionHandle);
                    break;
                default:
                    workItems = interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);
                    break;
            }

            // Format work items as XML
            String xmlResult = formatWorkItemsAsXml(caseId, filter, workItems);

            // Return success result
            return new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent(xmlResult)),
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
                List.of(new McpSchema.TextContent("Error retrieving work items: " + e.getMessage())),
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

    /**
     * Format work items as XML.
     *
     * @param caseId case identifier
     * @param filter filter used
     * @param workItems list of work items
     * @return XML formatted work items
     */
    private String formatWorkItemsAsXml(String caseId, String filter, List<WorkItemRecord> workItems) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<workItems>\n");
        xml.append("  <caseId>").append(caseId).append("</caseId>\n");
        xml.append("  <filter>").append(filter).append("</filter>\n");
        xml.append("  <count>").append(workItems.size()).append("</count>\n");
        xml.append("  <workItems>\n");

        for (WorkItemRecord item : workItems) {
            xml.append("    <workItem>\n");
            xml.append("      <id>").append(item.getID()).append("</id>\n");
            xml.append("      <caseId>").append(item.getCaseID()).append("</caseId>\n");
            xml.append("      <taskId>").append(item.getTaskID()).append("</taskId>\n");
            xml.append("      <status>").append(item.getStatus()).append("</status>\n");
            xml.append("      <startedBy>").append(item.getStartedBy()).append("</startedBy>\n");
            xml.append("      <firingTime>").append(item.getFiringTime()).append("</firingTime>\n");
            xml.append("      <startTime>").append(item.getStartTime()).append("</startTime>\n");
            xml.append("    </workItem>\n");
        }

        xml.append("  </workItems>\n");
        xml.append("</workItems>");

        return xml.toString();
    }
}
