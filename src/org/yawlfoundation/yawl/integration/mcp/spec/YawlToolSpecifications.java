package org.yawlfoundation.yawl.integration.mcp.spec;

import org.yawlfoundation.yawl.integration.mcp.stub.McpServerFeatures;
import org.yawlfoundation.yawl.integration.mcp.stub.McpSyncServerExchange;
import org.yawlfoundation.yawl.integration.mcp.stub.McpSchema;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static factory class that creates all YAWL workflow tool specifications for the
 * MCP server using the official MCP Java SDK 0.17.2 API.
 *
 * Each tool wraps a real YAWL engine operation via InterfaceB_EnvironmentBasedClient
 * or InterfaceA_EnvironmentBasedClient. There are 15 tools covering workflow case
 * management, work item management, and specification management.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class YawlToolSpecifications {

    private YawlToolSpecifications() {
        throw new UnsupportedOperationException(
            "YawlToolSpecifications is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates all 15 YAWL MCP tool specifications.
     *
     * @param interfaceBClient the YAWL InterfaceB client for runtime operations
     * @param interfaceAClient the YAWL InterfaceA client for design-time operations
     * @param sessionHandle    the active YAWL session handle
     * @return list of all YAWL tool specifications for MCP registration
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            String sessionHandle) {
        return createAll(interfaceBClient, interfaceAClient, sessionHandle, null);
    }

    /**
     * Creates all YAWL MCP tool specifications, optionally including the Z.AI natural-language tool.
     *
     * @param interfaceBClient  the YAWL InterfaceB client for runtime operations
     * @param interfaceAClient  the YAWL InterfaceA client for design-time operations
     * @param sessionHandle     the active YAWL session handle
     * @param zaiFunctionService optional Z.AI function service; when non-null, adds yawl_natural_language tool
     * @return list of all YAWL tool specifications for MCP registration
     */
    public static List<McpServerFeatures.SyncToolSpecification> createAll(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            String sessionHandle,
            ZaiFunctionService zaiFunctionService) {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException(
                "interfaceBClient is required - provide a connected InterfaceB_EnvironmentBasedClient");
        }
        if (interfaceAClient == null) {
            throw new IllegalArgumentException(
                "interfaceAClient is required - provide a connected InterfaceA_EnvironmentBasedClient");
        }
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new IllegalArgumentException(
                "sessionHandle is required - connect to the YAWL engine first via " +
                "InterfaceB_EnvironmentBasedClient.connect(username, password)");
        }

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        tools.add(createLaunchCaseTool(interfaceBClient, sessionHandle));
        tools.add(createGetCaseStatusTool(interfaceBClient, sessionHandle));
        tools.add(createCancelCaseTool(interfaceBClient, sessionHandle));
        tools.add(createListSpecificationsTool(interfaceBClient, sessionHandle));
        tools.add(createGetSpecificationTool(interfaceBClient, sessionHandle));
        tools.add(createUploadSpecificationTool(interfaceAClient, sessionHandle));
        tools.add(createGetWorkItemsTool(interfaceBClient, sessionHandle));
        tools.add(createGetWorkItemsForCaseTool(interfaceBClient, sessionHandle));
        tools.add(createCheckoutWorkItemTool(interfaceBClient, sessionHandle));
        tools.add(createCheckinWorkItemTool(interfaceBClient, sessionHandle));
        tools.add(createGetRunningCasesTool(interfaceBClient, sessionHandle));
        tools.add(createGetCaseDataTool(interfaceBClient, sessionHandle));
        tools.add(createSuspendCaseTool(interfaceBClient, sessionHandle));
        tools.add(createResumeCaseTool(interfaceBClient, sessionHandle));
        tools.add(createSkipWorkItemTool(interfaceBClient, sessionHandle));

        if (zaiFunctionService != null) {
            tools.add(createNaturalLanguageTool(zaiFunctionService));
        }

        return tools;
    }

    // =========================================================================
    // Tool: yawl_natural_language (Z.AI)
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createNaturalLanguageTool(
            ZaiFunctionService zaiFunctionService) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", Map.of(
            "type", "string",
            "description", "Natural language request (e.g. list workflows, launch OrderProcessing)"));

        List<String> required = List.of("query");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_natural_language")
                .description("Process a natural language request using Z.AI: list workflows, " +
                    "launch cases, check status, complete work items, etc.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String query = requireStringArg(args, "query");
                    String result = zaiFunctionService.processWithFunctions(query);
                    return new McpSchema.CallToolResult(result, false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Z.AI natural language error: " + e.getMessage(), true);
                }
            });
    }

    // =========================================================================
    // Tool 1: yawl_launch_case
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createLaunchCaseTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specIdentifier", Map.of(
            "type", "string",
            "description", "Workflow specification identifier"));
        props.put("specVersion", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("specUri", Map.of(
            "type", "string",
            "description", "Specification URI (default: same as identifier)"));
        props.put("caseData", Map.of(
            "type", "string",
            "description", "XML case input data (e.g. <data><param>value</param></data>)"));

        List<String> required = List.of("specIdentifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_launch_case")
                .description("Launch a new YAWL workflow case from a loaded specification. " +
                    "Returns the case ID of the launched workflow instance.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String specId = requireStringArg(args, "specIdentifier");
                    String specVersion = optionalStringArg(args, "specVersion", "0.1");
                    String specUri = optionalStringArg(args, "specUri", specId);
                    String caseData = optionalStringArg(args, "caseData", null);

                    YSpecificationID ySpecId = new YSpecificationID(
                        specId, specVersion, specUri);

                    String caseId = interfaceBClient.launchCase(
                        ySpecId, caseData, null, sessionHandle);

                    if (caseId == null || caseId.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to launch case: " + caseId, true);
                    }

                    return new McpSchema.CallToolResult(
                        "Case launched successfully. Case ID: " + caseId +
                        " | Specification: " + specId +
                        " (version " + specVersion + ", URI: " + specUri + ")",
                        false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error launching case: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 2: yawl_get_case_status
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createGetCaseStatusTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "The case ID to get status for"));

        List<String> required = List.of("caseId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_get_case_status")
                .description("Get the current status and state of a running YAWL workflow case.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String caseId = requireStringArg(args, "caseId");
                    String state = interfaceBClient.getCaseState(caseId, sessionHandle);

                    if (state == null || state.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to get case status for case " + caseId + ": " + state,
                            true);
                    }

                    return new McpSchema.CallToolResult(
                        "Case ID: " + caseId + "\nState:\n" + state, false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error getting case status: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 3: yawl_cancel_case
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createCancelCaseTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "The case ID to cancel"));

        List<String> required = List.of("caseId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_cancel_case")
                .description("Cancel a running YAWL workflow case. The case and all its " +
                    "active work items will be terminated.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String caseId = requireStringArg(args, "caseId");
                    String result = interfaceBClient.cancelCase(caseId, sessionHandle);

                    if (result == null || result.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to cancel case " + caseId + ": " + result, true);
                    }

                    return new McpSchema.CallToolResult(
                        "Case " + caseId + " cancelled successfully. Engine response: " + result,
                        false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error cancelling case: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 4: yawl_list_specifications
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createListSpecificationsTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = List.of();
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_list_specifications")
                .description("List all workflow specifications currently loaded in the " +
                    "YAWL engine. Returns identifier, version, URI, name, and status " +
                    "for each specification.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    List<SpecificationData> specs =
                        interfaceBClient.getSpecificationList(sessionHandle);

                    if (specs == null || specs.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "No specifications currently loaded in the YAWL engine.", false);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Loaded Specifications (").append(specs.size()).append("):\n\n");

                    for (int i = 0; i < specs.size(); i++) {
                        SpecificationData spec = specs.get(i);
                        YSpecificationID specId = spec.getID();
                        sb.append(i + 1).append(". ");
                        if (spec.getName() != null && !spec.getName().isEmpty()) {
                            sb.append(spec.getName()).append("\n");
                        } else {
                            sb.append("(unnamed)\n");
                        }
                        sb.append("   Identifier: ").append(specId.getIdentifier()).append("\n");
                        sb.append("   Version: ").append(specId.getVersionAsString()).append("\n");
                        sb.append("   URI: ").append(specId.getUri()).append("\n");
                        sb.append("   Status: ").append(spec.getStatus()).append("\n");
                        if (spec.getDocumentation() != null && !spec.getDocumentation().isEmpty()) {
                            sb.append("   Documentation: ").append(spec.getDocumentation()).append("\n");
                        }
                        if (spec.getRootNetID() != null && !spec.getRootNetID().isEmpty()) {
                            sb.append("   Root Net: ").append(spec.getRootNetID()).append("\n");
                        }
                        sb.append("\n");
                    }

                    return new McpSchema.CallToolResult(sb.toString().trim(), false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error listing specifications: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 5: yawl_get_specification
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createGetSpecificationTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specIdentifier", Map.of(
            "type", "string",
            "description", "Workflow specification identifier"));
        props.put("specVersion", Map.of(
            "type", "string",
            "description", "Specification version (default: 0.1)"));
        props.put("specUri", Map.of(
            "type", "string",
            "description", "Specification URI (default: same as identifier)"));

        List<String> required = List.of("specIdentifier");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_get_specification")
                .description("Get the full XML definition of a workflow specification " +
                    "from the YAWL engine.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String specId = requireStringArg(args, "specIdentifier");
                    String specVersion = optionalStringArg(args, "specVersion", "0.1");
                    String specUri = optionalStringArg(args, "specUri", specId);

                    YSpecificationID ySpecId = new YSpecificationID(
                        specId, specVersion, specUri);

                    String spec = interfaceBClient.getSpecification(ySpecId, sessionHandle);

                    if (spec == null || spec.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to get specification " + specId + ": " + spec, true);
                    }

                    return new McpSchema.CallToolResult(
                        "Specification (" + specId + " v" + specVersion + "):\n\n" + spec,
                        false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error getting specification: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 6: yawl_upload_specification
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createUploadSpecificationTool(
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("specXml", Map.of(
            "type", "string",
            "description", "The complete YAWL specification XML content to upload"));

        List<String> required = List.of("specXml");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_upload_specification")
                .description("Upload a YAWL workflow specification XML to the engine. " +
                    "The specification will be validated and loaded for case launching.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String specXml = requireStringArg(args, "specXml");

                    String result = interfaceAClient.uploadSpecification(
                        specXml, sessionHandle);

                    if (result == null || result.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to upload specification: " + result, true);
                    }

                    return new McpSchema.CallToolResult(
                        "Specification uploaded successfully. Engine response: " + result,
                        false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error uploading specification: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 7: yawl_get_work_items
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createGetWorkItemsTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = List.of();
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_get_work_items")
                .description("Get all live work items from the YAWL engine across all " +
                    "running cases. Returns work item ID, case ID, task ID, status, " +
                    "and specification info for each item.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    List<WorkItemRecord> items =
                        interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);

                    if (items == null || items.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "No live work items in the YAWL engine.", false);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Live Work Items (").append(items.size()).append("):\n\n");

                    for (int i = 0; i < items.size(); i++) {
                        WorkItemRecord item = items.get(i);
                        sb.append(i + 1).append(". Work Item: ").append(item.getID()).append("\n");
                        sb.append("   Case ID: ").append(item.getCaseID()).append("\n");
                        sb.append("   Task ID: ").append(item.getTaskID()).append("\n");
                        sb.append("   Status: ").append(item.getStatus()).append("\n");
                        sb.append("   Specification: ").append(item.getSpecURI()).append("\n");
                        if (item.getSpecIdentifier() != null) {
                            sb.append("   Spec Identifier: ").append(
                                item.getSpecIdentifier()).append("\n");
                        }
                        sb.append("\n");
                    }

                    return new McpSchema.CallToolResult(sb.toString().trim(), false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error getting work items: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 8: yawl_get_work_items_for_case
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createGetWorkItemsForCaseTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "The case ID to get work items for"));

        List<String> required = List.of("caseId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_get_work_items_for_case")
                .description("Get all active work items for a specific YAWL workflow case.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String caseId = requireStringArg(args, "caseId");

                    List<WorkItemRecord> items =
                        interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

                    if (items == null || items.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "No active work items for case " + caseId + ".", false);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Work Items for Case ").append(caseId);
                    sb.append(" (").append(items.size()).append("):\n\n");

                    for (int i = 0; i < items.size(); i++) {
                        WorkItemRecord item = items.get(i);
                        sb.append(i + 1).append(". Work Item: ").append(item.getID()).append("\n");
                        sb.append("   Task ID: ").append(item.getTaskID()).append("\n");
                        sb.append("   Status: ").append(item.getStatus()).append("\n");
                        sb.append("   Specification: ").append(item.getSpecURI()).append("\n");
                        sb.append("\n");
                    }

                    return new McpSchema.CallToolResult(sb.toString().trim(), false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error getting work items for case: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 9: yawl_checkout_work_item
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createCheckoutWorkItemTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("workItemId", Map.of(
            "type", "string",
            "description", "The work item ID to check out (format: caseID:taskID)"));

        List<String> required = List.of("workItemId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_checkout_work_item")
                .description("Check out a work item to claim ownership and begin execution. " +
                    "The work item must be in enabled or fired state.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String workItemId = requireStringArg(args, "workItemId");

                    String result = interfaceBClient.checkOutWorkItem(
                        workItemId, sessionHandle);

                    if (result == null || result.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to check out work item " + workItemId + ": " + result,
                            true);
                    }

                    return new McpSchema.CallToolResult(
                        "Work item " + workItemId + " checked out successfully.\n" +
                        "Engine response: " + result, false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error checking out work item: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 10: yawl_checkin_work_item
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createCheckinWorkItemTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("workItemId", Map.of(
            "type", "string",
            "description", "The work item ID to check in (complete)"));
        props.put("outputData", Map.of(
            "type", "string",
            "description", "XML output data for the work item " +
                "(e.g. <data><result>value</result></data>)"));

        List<String> required = List.of("workItemId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_checkin_work_item")
                .description("Check in (complete) a work item with output data. The work " +
                    "item must have been previously checked out.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String workItemId = requireStringArg(args, "workItemId");
                    String outputData = optionalStringArg(args, "outputData", null);

                    String result = interfaceBClient.checkInWorkItem(
                        workItemId, outputData, null, sessionHandle);

                    if (result == null || result.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to check in work item " + workItemId + ": " + result,
                            true);
                    }

                    return new McpSchema.CallToolResult(
                        "Work item " + workItemId + " checked in (completed) successfully.\n" +
                        "Engine response: " + result, false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error checking in work item: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 11: yawl_get_running_cases
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createGetRunningCasesTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = List.of();
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_get_running_cases")
                .description("Get all currently running workflow cases from the YAWL engine.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String casesXml = interfaceBClient.getAllRunningCases(sessionHandle);

                    if (casesXml == null || casesXml.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to get running cases: " + casesXml, true);
                    }

                    return new McpSchema.CallToolResult(
                        "Running Cases:\n" + casesXml, false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error getting running cases: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 12: yawl_get_case_data
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createGetCaseDataTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "The case ID to get data for"));

        List<String> required = List.of("caseId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_get_case_data")
                .description("Get the current data variables and values of a running " +
                    "YAWL workflow case.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String caseId = requireStringArg(args, "caseId");
                    String data = interfaceBClient.getCaseData(caseId, sessionHandle);

                    if (data == null || data.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to get data for case " + caseId + ": " + data, true);
                    }

                    return new McpSchema.CallToolResult(
                        "Case Data for " + caseId + ":\n" + data, false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error getting case data: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 13: yawl_suspend_case
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createSuspendCaseTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "The case ID to suspend"));

        List<String> required = List.of("caseId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_suspend_case")
                .description("Suspend a running YAWL workflow case by suspending all " +
                    "its active work items.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String caseId = requireStringArg(args, "caseId");

                    List<WorkItemRecord> items =
                        interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

                    if (items == null || items.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "No active work items found for case " + caseId +
                            " to suspend.", true);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Suspending case ").append(caseId).append(":\n");
                    int suspendedCount = 0;

                    for (WorkItemRecord item : items) {
                        String result = interfaceBClient.suspendWorkItem(
                            item.getID(), sessionHandle);
                        if (result != null && !result.contains("<failure>")) {
                            suspendedCount++;
                            sb.append("  Suspended work item: ").append(item.getID()).append("\n");
                        } else {
                            sb.append("  Could not suspend work item: ").append(item.getID());
                            sb.append(" (").append(result).append(")\n");
                        }
                    }

                    sb.append("Suspended ").append(suspendedCount).append(" of ");
                    sb.append(items.size()).append(" work items.");

                    return new McpSchema.CallToolResult(sb.toString(),
                        suspendedCount == 0);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error suspending case: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 14: yawl_resume_case
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createResumeCaseTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "The case ID to resume"));

        List<String> required = List.of("caseId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_resume_case")
                .description("Resume a previously suspended YAWL workflow case by " +
                    "unsuspending all its work items.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String caseId = requireStringArg(args, "caseId");

                    List<WorkItemRecord> items =
                        interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

                    if (items == null || items.isEmpty()) {
                        return new McpSchema.CallToolResult(
                            "No work items found for case " + caseId + " to resume.",
                            true);
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("Resuming case ").append(caseId).append(":\n");
                    int resumedCount = 0;

                    for (WorkItemRecord item : items) {
                        String result = interfaceBClient.unsuspendWorkItem(
                            item.getID(), sessionHandle);
                        if (result != null && !result.contains("<failure>")) {
                            resumedCount++;
                            sb.append("  Resumed work item: ").append(item.getID()).append("\n");
                        } else {
                            sb.append("  Could not resume work item: ").append(item.getID());
                            sb.append(" (").append(result).append(")\n");
                        }
                    }

                    sb.append("Resumed ").append(resumedCount).append(" of ");
                    sb.append(items.size()).append(" work items.");

                    return new McpSchema.CallToolResult(sb.toString(),
                        resumedCount == 0);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error resuming case: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Tool 15: yawl_skip_work_item
    // =========================================================================

    private static McpServerFeatures.SyncToolSpecification createSkipWorkItemTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("workItemId", Map.of(
            "type", "string",
            "description", "The work item ID to skip"));

        List<String> required = List.of("workItemId");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_skip_work_item")
                .description("Skip a work item if the task allows skipping. The work " +
                    "item will be marked as completed without execution.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    String workItemId = requireStringArg(args, "workItemId");

                    String result = interfaceBClient.skipWorkItem(
                        workItemId, sessionHandle);

                    if (result == null || result.contains("<failure>")) {
                        return new McpSchema.CallToolResult(
                            "Failed to skip work item " + workItemId + ": " + result,
                            true);
                    }

                    return new McpSchema.CallToolResult(
                        "Work item " + workItemId + " skipped successfully.\n" +
                        "Engine response: " + result, false);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        "Error skipping work item: " + e.getMessage(), true);
                }
            }
        );
    }

    // =========================================================================
    // Argument extraction utilities
    // =========================================================================

    /**
     * Extract a required string argument from the tool arguments map.
     *
     * @param args the tool arguments
     * @param name the argument name
     * @return the string value
     * @throws IllegalArgumentException if the argument is missing
     */
    private static String requireStringArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required argument missing: " + name);
        }
        return value.toString();
    }

    /**
     * Extract an optional string argument from the tool arguments map.
     *
     * @param args         the tool arguments
     * @param name         the argument name
     * @param defaultValue the default value if the argument is missing
     * @return the string value or the default
     */
    private static String optionalStringArg(Map<String, Object> args, String name,
                                            String defaultValue) {
        Object value = args.get(name);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }
}
