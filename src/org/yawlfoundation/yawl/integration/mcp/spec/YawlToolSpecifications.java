package org.yawlfoundation.yawl.integration.mcp.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.CallToolResult;
import io.modelcontextprotocol.spec.SyncToolSpecification;
import io.modelcontextprotocol.spec.Tool;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * YAWL Tool Specifications for MCP.
 *
 * Defines MCP tools that expose YAWL workflow operations as AI-consumable functions.
 * Each tool has a JSON schema for parameter validation and a handler that executes
 * real YAWL engine operations via InterfaceB.
 *
 * Tool Categories:
 * - Workflow Tools: launchCase, getCaseStatus, cancelCase, suspendCase, resumeCase
 * - Work Item Tools: getWorkItems, checkoutWorkItem, checkinWorkItem, skipWorkItem
 * - Specification Tools: listSpecifications, getSpecification
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlToolSpecifications {

    private static final Logger LOGGER = Logger.getLogger(YawlToolSpecifications.class.getName());

    /**
     * Gets all workflow management tools.
     *
     * @param client the YAWL InterfaceB client
     * @param sessionHandle the YAWL session handle
     * @param mapper the ObjectMapper for JSON processing
     * @return list of all workflow tool specifications
     */
    public static List<SyncToolSpecification> getWorkflowTools(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {
        List<SyncToolSpecification> tools = new ArrayList<>();

        tools.add(createLaunchCaseTool(client, sessionHandle, mapper));
        tools.add(createGetCaseStatusTool(client, sessionHandle, mapper));
        tools.add(createCancelCaseTool(client, sessionHandle, mapper));
        tools.add(createSuspendCaseTool(client, sessionHandle, mapper));
        tools.add(createResumeCaseTool(client, sessionHandle, mapper));
        tools.add(createGetAllRunningCasesTool(client, sessionHandle, mapper));
        tools.add(createGetCaseDataTool(client, sessionHandle, mapper));

        return tools;
    }

    /**
     * Gets all work item management tools.
     *
     * @param client the YAWL InterfaceB client
     * @param sessionHandle the YAWL session handle
     * @param mapper the ObjectMapper for JSON processing
     * @return list of all work item tool specifications
     */
    public static List<SyncToolSpecification> getWorkItemTools(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {
        List<SyncToolSpecification> tools = new ArrayList<>();

        tools.add(createGetLiveWorkItemsTool(client, sessionHandle, mapper));
        tools.add(createGetWorkItemsForCaseTool(client, sessionHandle, mapper));
        tools.add(createCheckoutWorkItemTool(client, sessionHandle, mapper));
        tools.add(createCheckinWorkItemTool(client, sessionHandle, mapper));
        tools.add(createSkipWorkItemTool(client, sessionHandle, mapper));
        tools.add(createSuspendWorkItemTool(client, sessionHandle, mapper));
        tools.add(createUnsuspendWorkItemTool(client, sessionHandle, mapper));

        return tools;
    }

    /**
     * Gets all specification management tools.
     *
     * @param client the YAWL InterfaceB client
     * @param sessionHandle the YAWL session handle
     * @param mapper the ObjectMapper for JSON processing
     * @return list of all specification tool specifications
     */
    public static List<SyncToolSpecification> getSpecificationTools(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {
        List<SyncToolSpecification> tools = new ArrayList<>();

        tools.add(createListSpecificationsTool(client, sessionHandle, mapper));
        tools.add(createGetSpecificationTool(client, sessionHandle, mapper));

        return tools;
    }

    // ============================================================
    // WORKFLOW TOOLS
    // ============================================================

    private static SyncToolSpecification createLaunchCaseTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "specId": {
                  "type": "string",
                  "description": "Specification ID (e.g., 'MyWorkflow')"
                },
                "specUri": {
                  "type": "string",
                  "description": "Specification URI (optional, for versioned specs)"
                },
                "specVersion": {
                  "type": "string",
                  "description": "Specification version (optional)"
                },
                "caseData": {
                  "type": "object",
                  "description": "Case input data as JSON object"
                }
              },
              "required": ["specId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_launch_case",
                "Launch a new YAWL workflow case. Starts a new workflow instance for the specified specification.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String specId = getStringArg(args, "specId");
                String specUri = getStringArg(args, "specUri");
                String specVersion = getStringArg(args, "specVersion");

                YSpecificationID ySpecId;
                if (specUri != null && specVersion != null) {
                    ySpecId = new YSpecificationID(specUri, specVersion, specId);
                } else {
                    ySpecId = new YSpecificationID(specId);
                }

                String caseData = null;
                Object caseDataObj = args.get("caseData");
                if (caseDataObj != null) {
                    caseData = "<data>" + mapper.writeValueAsString(caseDataObj) + "</data>";
                }

                String caseId = client.launchCase(ySpecId, caseData, sessionHandle);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("caseId", caseId);
                result.put("specId", specId);

                return new CallToolResult(result, false);
            } catch (IOException e) {
                LOGGER.warning("Failed to launch case: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createGetCaseStatusTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "caseId": {
                  "type": "string",
                  "description": "The case ID to get status for"
                }
              },
              "required": ["caseId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_get_case_status",
                "Get the current status and state of a YAWL workflow case.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String caseId = getStringArg(args, "caseId");
                String state = client.getCaseState(caseId, sessionHandle);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("caseId", caseId);
                result.put("state", state);

                return new CallToolResult(result, false);
            } catch (IOException e) {
                LOGGER.warning("Failed to get case status: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createCancelCaseTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "caseId": {
                  "type": "string",
                  "description": "The case ID to cancel"
                }
              },
              "required": ["caseId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_cancel_case",
                "Cancel a running YAWL workflow case.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String caseId = getStringArg(args, "caseId");
                String result = client.cancelCase(caseId, sessionHandle);

                return new CallToolResult(Map.of(
                        "success", true,
                        "caseId", caseId,
                        "message", result
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to cancel case: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createSuspendCaseTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "caseId": {
                  "type": "string",
                  "description": "The case ID to suspend"
                }
              },
              "required": ["caseId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_suspend_case",
                "Suspend a running YAWL workflow case.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String caseId = getStringArg(args, "caseId");
                // Note: YAWL suspend is done at work item level
                List<WorkItemRecord> items = client.getWorkItemsForCase(caseId, sessionHandle);
                for (WorkItemRecord item : items) {
                    client.suspendWorkItem(item.getID(), sessionHandle);
                }

                return new CallToolResult(Map.of(
                        "success", true,
                        "caseId", caseId,
                        "message", "Case suspended"
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to suspend case: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createResumeCaseTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "caseId": {
                  "type": "string",
                  "description": "The case ID to resume"
                }
              },
              "required": ["caseId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_resume_case",
                "Resume a suspended YAWL workflow case.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String caseId = getStringArg(args, "caseId");
                List<WorkItemRecord> items = client.getWorkItemsForCase(caseId, sessionHandle);
                for (WorkItemRecord item : items) {
                    client.unsuspendWorkItem(item.getID(), sessionHandle);
                }

                return new CallToolResult(Map.of(
                        "success", true,
                        "caseId", caseId,
                        "message", "Case resumed"
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to resume case: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createGetAllRunningCasesTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        Tool tool = new Tool(
                "yawl_get_running_cases",
                "Get all currently running workflow cases from the YAWL engine.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String casesXml = client.getAllRunningCases(sessionHandle);

                return new CallToolResult(Map.of(
                        "success", true,
                        "cases", casesXml
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to get running cases: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createGetCaseDataTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "caseId": {
                  "type": "string",
                  "description": "The case ID to get data for"
                }
              },
              "required": ["caseId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_get_case_data",
                "Get the current data of a YAWL workflow case.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String caseId = getStringArg(args, "caseId");
                String data = client.getCaseData(caseId, sessionHandle);

                return new CallToolResult(Map.of(
                        "success", true,
                        "caseId", caseId,
                        "data", data
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to get case data: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    // ============================================================
    // WORK ITEM TOOLS
    // ============================================================

    private static SyncToolSpecification createGetLiveWorkItemsTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        Tool tool = new Tool(
                "yawl_get_workitems",
                "Get all live work items from the YAWL engine.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                List<WorkItemRecord> items = client.getCompleteListOfLiveWorkItems(sessionHandle);

                List<Map<String, Object>> itemList = new ArrayList<>();
                for (WorkItemRecord item : items) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("id", item.getID());
                    itemMap.put("caseId", item.getCaseID());
                    itemMap.put("taskId", item.getTaskID());
                    itemMap.put("status", item.getStatus());
                    itemMap.put("specId", item.getSpecificationID());
                    itemList.add(itemMap);
                }

                return new CallToolResult(Map.of(
                        "success", true,
                        "count", items.size(),
                        "workItems", itemList
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to get work items: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createGetWorkItemsForCaseTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "caseId": {
                  "type": "string",
                  "description": "The case ID to get work items for"
                }
              },
              "required": ["caseId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_get_workitems_case",
                "Get all work items for a specific case.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String caseId = getStringArg(args, "caseId");
                List<WorkItemRecord> items = client.getWorkItemsForCase(caseId, sessionHandle);

                List<Map<String, Object>> itemList = new ArrayList<>();
                for (WorkItemRecord item : items) {
                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("id", item.getID());
                    itemMap.put("taskId", item.getTaskID());
                    itemMap.put("status", item.getStatus());
                    itemList.add(itemMap);
                }

                return new CallToolResult(Map.of(
                        "success", true,
                        "caseId", caseId,
                        "count", items.size(),
                        "workItems", itemList
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to get work items for case: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createCheckoutWorkItemTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "workItemId": {
                  "type": "string",
                  "description": "The work item ID to checkout"
                }
              },
              "required": ["workItemId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_checkout_workitem",
                "Checkout a work item to claim ownership and begin execution.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String workItemId = getStringArg(args, "workItemId");
                String result = client.checkOutWorkItem(workItemId, sessionHandle);

                return new CallToolResult(Map.of(
                        "success", true,
                        "workItemId", workItemId,
                        "result", result
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to checkout work item: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createCheckinWorkItemTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "workItemId": {
                  "type": "string",
                  "description": "The work item ID to checkin"
                },
                "data": {
                  "type": "object",
                  "description": "Output data for the work item"
                }
              },
              "required": ["workItemId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_checkin_workitem",
                "Checkin a work item to complete execution with output data.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String workItemId = getStringArg(args, "workItemId");
                Object dataObj = args.get("data");

                String data = null;
                if (dataObj != null) {
                    data = "<data>" + mapper.writeValueAsString(dataObj) + "</data>";
                }

                String result = client.checkInWorkItem(workItemId, data, sessionHandle);

                return new CallToolResult(Map.of(
                        "success", true,
                        "workItemId", workItemId,
                        "result", result
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to checkin work item: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createSkipWorkItemTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "workItemId": {
                  "type": "string",
                  "description": "The work item ID to skip"
                }
              },
              "required": ["workItemId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_skip_workitem",
                "Skip a work item (if the task allows skipping).",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String workItemId = getStringArg(args, "workItemId");
                String result = client.skipWorkItem(workItemId, sessionHandle);

                return new CallToolResult(Map.of(
                        "success", true,
                        "workItemId", workItemId,
                        "result", result
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to skip work item: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createSuspendWorkItemTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "workItemId": {
                  "type": "string",
                  "description": "The work item ID to suspend"
                }
              },
              "required": ["workItemId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_suspend_workitem",
                "Suspend a work item temporarily.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String workItemId = getStringArg(args, "workItemId");
                String result = client.suspendWorkItem(workItemId, sessionHandle);

                return new CallToolResult(Map.of(
                        "success", true,
                        "workItemId", workItemId,
                        "result", result
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to suspend work item: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createUnsuspendWorkItemTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "workItemId": {
                  "type": "string",
                  "description": "The work item ID to unsuspend"
                }
              },
              "required": ["workItemId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_unsuspend_workitem",
                "Unsuspend a previously suspended work item.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String workItemId = getStringArg(args, "workItemId");
                String result = client.unsuspendWorkItem(workItemId, sessionHandle);

                return new CallToolResult(Map.of(
                        "success", true,
                        "workItemId", workItemId,
                        "result", result
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to unsuspend work item: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    // ============================================================
    // SPECIFICATION TOOLS
    // ============================================================

    private static SyncToolSpecification createListSpecificationsTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {}
            }
            """;

        Tool tool = new Tool(
                "yawl_list_specs",
                "List all loaded workflow specifications in the YAWL engine.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                var specs = client.getSpecificationList(sessionHandle);

                List<Map<String, Object>> specList = new ArrayList<>();
                for (var spec : specs) {
                    Map<String, Object> specMap = new HashMap<>();
                    specMap.put("id", spec.getID());
                    specMap.put("uri", spec.getUri());
                    specMap.put("version", spec.getVersion());
                    specMap.put("name", spec.getName());
                    specList.add(specMap);
                }

                return new CallToolResult(Map.of(
                        "success", true,
                        "count", specs.size(),
                        "specifications", specList
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to list specifications: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    private static SyncToolSpecification createGetSpecificationTool(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle,
            ObjectMapper mapper) {

        String schema = """
            {
              "type": "object",
              "properties": {
                "specId": {
                  "type": "string",
                  "description": "Specification ID"
                },
                "specUri": {
                  "type": "string",
                  "description": "Specification URI (optional)"
                },
                "specVersion": {
                  "type": "string",
                  "description": "Specification version (optional)"
                }
              },
              "required": ["specId"]
            }
            """;

        Tool tool = new Tool(
                "yawl_get_spec",
                "Get the XML specification for a workflow.",
                schema
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String specId = getStringArg(args, "specId");
                String specUri = getStringArg(args, "specUri");
                String specVersion = getStringArg(args, "specVersion");

                YSpecificationID ySpecId;
                if (specUri != null && specVersion != null) {
                    ySpecId = new YSpecificationID(specUri, specVersion, specId);
                } else {
                    ySpecId = new YSpecificationID(specId);
                }

                String spec = client.getSpecification(ySpecId, sessionHandle);

                return new CallToolResult(Map.of(
                        "success", true,
                        "specId", specId,
                        "specification", spec
                ), false);
            } catch (IOException e) {
                LOGGER.warning("Failed to get specification: " + e.getMessage());
                return new CallToolResult(Map.of(
                        "success", false,
                        "error", e.getMessage()
                ), true);
            }
        });
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================

    private static String getStringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value != null ? value.toString() : null;
    }
}
