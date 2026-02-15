package org.yawlfoundation.yawl.integration.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.*;

/**
 * Model Context Protocol (MCP) Server for YAWL using the official MCP Java SDK.
 *
 * Exposes YAWL workflow engine capabilities as MCP tools, resources, and prompts
 * over STDIO transport using the official io.modelcontextprotocol.sdk library.
 *
 * Tools exposed:
 *   - launch_case: Launch a new workflow case
 *   - get_case_status: Get status of a running case
 *   - list_specifications: List loaded workflow specifications
 *   - get_work_items: Get work items for a case
 *   - complete_work_item: Check out and complete a work item
 *   - cancel_case: Cancel a running case
 *
 * Resources exposed:
 *   - yawl://specifications - All loaded specifications
 *   - yawl://cases - All running cases
 *   - yawl://workitems - All live work items
 *
 * Prompts exposed:
 *   - workflow_analysis - Analyze a workflow specification
 *   - task_completion_guide - Guide for completing a workflow task
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpServer {

    private static final String SERVER_NAME = "yawl-mcp-server";
    private static final String SERVER_VERSION = "5.2.0";

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final InterfaceA_EnvironmentBasedClient interfaceAClient;
    private final String yawlUsername;
    private final String yawlPassword;
    private final ObjectMapper mapper;
    private McpSyncServer mcpServer;
    private String sessionHandle;

    /**
     * Construct a YAWL MCP Server with YAWL engine connection parameters.
     *
     * @param yawlEngineUrl base URL of YAWL engine (e.g. http://localhost:8080/yawl)
     * @param username YAWL admin username
     * @param password YAWL admin password
     */
    public YawlMcpServer(String yawlEngineUrl, String username, String password) {
        if (yawlEngineUrl == null || yawlEngineUrl.isEmpty()) {
            throw new IllegalArgumentException(
                "YAWL engine URL is required (e.g. http://localhost:8080/yawl)");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("YAWL username is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("YAWL password is required");
        }

        this.mapper = new ObjectMapper();
        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(
                yawlEngineUrl + "/ib");
        this.interfaceAClient = new InterfaceA_EnvironmentBasedClient(
                yawlEngineUrl + "/ia");
        this.yawlUsername = username;
        this.yawlPassword = password;
    }

    /**
     * Build and start the MCP server using the official SDK with STDIO transport.
     * This method blocks until the server is shut down.
     */
    public void start() {
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);
        StdioServerTransportProvider transportProvider =
            new StdioServerTransportProvider(jsonMapper);

        mcpServer = McpServer.sync(transportProvider)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .resources(false, true)
                .prompts(false)
                .logging()
                .build())
            .instructions("YAWL Workflow Engine MCP Server. Use the provided tools to " +
                "launch workflow cases, query specifications, manage work items, and " +
                "interact with the YAWL BPM engine.")
            .tools(buildToolSpecifications())
            .resources(buildResourceSpecifications())
            .prompts(buildPromptSpecifications())
            .build();

        System.err.println("YAWL MCP Server v" + SERVER_VERSION + " started on STDIO transport");
    }

    /**
     * Stop the MCP server gracefully.
     */
    public void stop() {
        if (mcpServer != null) {
            mcpServer.closeGracefully();
            mcpServer = null;
        }
        disconnectFromEngine();
    }

    /**
     * Check if server has been built (non-null).
     */
    public boolean isRunning() {
        return mcpServer != null;
    }

    // =========================================================================
    // Tool Specifications
    // =========================================================================

    private List<McpServerFeatures.SyncToolSpecification> buildToolSpecifications() {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        tools.add(new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("launch_case")
                .description("Launch a new YAWL workflow case from a loaded specification")
                .inputSchema(new McpSchema.JsonSchema(
                    "object",
                    buildProperties(
                        "spec_identifier", stringProp("Specification identifier (use list_specifications to find available specs)"),
                        "spec_version", stringProp("Specification version (default: 0.1)"),
                        "spec_uri", stringProp("Specification URI (default: same as identifier)"),
                        "case_data", stringProp("XML case data for input parameters (optional)")
                    ),
                    List.of("spec_identifier"),
                    null, null, null
                ))
                .build(),
            (exchange, args) -> handleLaunchCase(args)
        ));

        tools.add(new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("get_case_status")
                .description("Get the status and current work items of a running workflow case")
                .inputSchema(new McpSchema.JsonSchema(
                    "object",
                    buildProperties(
                        "case_id", stringProp("The case ID to query")
                    ),
                    List.of("case_id"),
                    null, null, null
                ))
                .build(),
            (exchange, args) -> handleGetCaseStatus(args)
        ));

        tools.add(new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("list_specifications")
                .description("List all workflow specifications currently loaded in the YAWL engine")
                .inputSchema(new McpSchema.JsonSchema(
                    "object",
                    Map.of(),
                    List.of(),
                    null, null, null
                ))
                .build(),
            (exchange, args) -> handleListSpecifications()
        ));

        tools.add(new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("get_work_items")
                .description("Get all work items for a specific case or all live work items")
                .inputSchema(new McpSchema.JsonSchema(
                    "object",
                    buildProperties(
                        "case_id", stringProp("Case ID to filter work items (optional, returns all if omitted)")
                    ),
                    List.of(),
                    null, null, null
                ))
                .build(),
            (exchange, args) -> handleGetWorkItems(args)
        ));

        tools.add(new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("complete_work_item")
                .description("Check out and complete a work item with output data")
                .inputSchema(new McpSchema.JsonSchema(
                    "object",
                    buildProperties(
                        "work_item_id", stringProp("The work item ID (format: caseID:taskID)"),
                        "output_data", stringProp("XML output data for the work item (optional)")
                    ),
                    List.of("work_item_id"),
                    null, null, null
                ))
                .build(),
            (exchange, args) -> handleCompleteWorkItem(args)
        ));

        tools.add(new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("cancel_case")
                .description("Cancel a running workflow case")
                .inputSchema(new McpSchema.JsonSchema(
                    "object",
                    buildProperties(
                        "case_id", stringProp("The case ID to cancel")
                    ),
                    List.of("case_id"),
                    null, null, null
                ))
                .build(),
            (exchange, args) -> handleCancelCase(args)
        ));

        return tools;
    }

    // =========================================================================
    // Resource Specifications
    // =========================================================================

    private List<McpServerFeatures.SyncResourceSpecification> buildResourceSpecifications() {
        List<McpServerFeatures.SyncResourceSpecification> resources = new ArrayList<>();

        resources.add(new McpServerFeatures.SyncResourceSpecification(
            McpSchema.Resource.builder()
                .uri("yawl://specifications")
                .name("Loaded Specifications")
                .description("All workflow specifications currently loaded in the YAWL engine")
                .mimeType("application/json")
                .build(),
            (exchange, request) -> readSpecificationsResource()
        ));

        resources.add(new McpServerFeatures.SyncResourceSpecification(
            McpSchema.Resource.builder()
                .uri("yawl://cases")
                .name("Running Cases")
                .description("All currently running workflow cases")
                .mimeType("application/json")
                .build(),
            (exchange, request) -> readCasesResource()
        ));

        resources.add(new McpServerFeatures.SyncResourceSpecification(
            McpSchema.Resource.builder()
                .uri("yawl://workitems")
                .name("Live Work Items")
                .description("All live work items across all running cases")
                .mimeType("application/json")
                .build(),
            (exchange, request) -> readWorkItemsResource()
        ));

        return resources;
    }

    // =========================================================================
    // Prompt Specifications
    // =========================================================================

    private List<McpServerFeatures.SyncPromptSpecification> buildPromptSpecifications() {
        List<McpServerFeatures.SyncPromptSpecification> prompts = new ArrayList<>();

        prompts.add(new McpServerFeatures.SyncPromptSpecification(
            new McpSchema.Prompt(
                "workflow_analysis",
                "Analyze a YAWL workflow specification and provide recommendations",
                List.of(
                    new McpSchema.PromptArgument("spec_identifier",
                        "The specification identifier to analyze", true),
                    new McpSchema.PromptArgument("analysis_focus",
                        "Focus area: performance, correctness, optimization, or general", false)
                )
            ),
            (exchange, request) -> handleWorkflowAnalysisPrompt(request)
        ));

        prompts.add(new McpServerFeatures.SyncPromptSpecification(
            new McpSchema.Prompt(
                "task_completion_guide",
                "Generate guidance for completing a specific workflow task",
                List.of(
                    new McpSchema.PromptArgument("case_id",
                        "The case ID containing the task", true),
                    new McpSchema.PromptArgument("task_id",
                        "The task ID to get guidance for", true)
                )
            ),
            (exchange, request) -> handleTaskCompletionGuidePrompt(request)
        ));

        return prompts;
    }

    // =========================================================================
    // Tool Handlers - real YAWL engine integration
    // =========================================================================

    private McpSchema.CallToolResult handleLaunchCase(Map<String, Object> args) {
        try {
            ensureEngineConnection();

            String specIdentifier = requireArg(args, "spec_identifier");
            String specVersion = optionalArg(args, "spec_version", "0.1");
            String specUri = optionalArg(args, "spec_uri", specIdentifier);
            String caseData = optionalArg(args, "case_data", null);

            YSpecificationID specID = new YSpecificationID(
                specIdentifier, specVersion, specUri);

            String caseId = interfaceBClient.launchCase(
                specID, caseData, null, sessionHandle);

            if (caseId == null || caseId.contains("<failure>")) {
                return new McpSchema.CallToolResult(
                    "Failed to launch case: " + caseId, true);
            }

            String result = mapper.writeValueAsString(Map.of(
                "case_id", caseId,
                "spec_identifier", specIdentifier,
                "spec_version", specVersion,
                "status", "launched"
            ));
            return new McpSchema.CallToolResult(result, false);
        } catch (IOException e) {
            return new McpSchema.CallToolResult(
                "YAWL engine error: " + e.getMessage(), true);
        }
    }

    private McpSchema.CallToolResult handleGetCaseStatus(Map<String, Object> args) {
        try {
            ensureEngineConnection();

            String caseId = requireArg(args, "case_id");
            String caseState = interfaceBClient.getCaseState(caseId, sessionHandle);
            List<WorkItemRecord> workItems = interfaceBClient.getWorkItemsForCase(
                caseId, sessionHandle);

            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("case_id", caseId);
            resultData.put("state", caseState != null ? caseState : "unknown");

            List<Map<String, Object>> items = new ArrayList<>();
            if (workItems != null) {
                for (WorkItemRecord wir : workItems) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", wir.getID());
                    item.put("task_id", wir.getTaskID());
                    item.put("status", wir.getStatus());
                    item.put("case_id", wir.getCaseID());
                    if (wir.getEnablementTimeMs() != null) {
                        item.put("enabled_time", wir.getEnablementTimeMs());
                    }
                    if (wir.getStartTimeMs() != null) {
                        item.put("start_time", wir.getStartTimeMs());
                    }
                    items.add(item);
                }
            }
            resultData.put("work_items", items);

            return new McpSchema.CallToolResult(
                mapper.writeValueAsString(resultData), false);
        } catch (IOException e) {
            return new McpSchema.CallToolResult(
                "YAWL engine error: " + e.getMessage(), true);
        }
    }

    private McpSchema.CallToolResult handleListSpecifications() {
        try {
            ensureEngineConnection();

            List<SpecificationData> specs = interfaceBClient.getSpecificationList(
                sessionHandle);

            List<Map<String, Object>> specList = new ArrayList<>();
            if (specs != null) {
                for (SpecificationData spec : specs) {
                    Map<String, Object> specMap = new LinkedHashMap<>();
                    YSpecificationID specId = spec.getID();
                    specMap.put("identifier", specId.getIdentifier());
                    specMap.put("version", specId.getVersionAsString());
                    specMap.put("uri", specId.getUri());
                    if (spec.getName() != null) {
                        specMap.put("name", spec.getName());
                    }
                    if (spec.getDocumentation() != null) {
                        specMap.put("documentation", spec.getDocumentation());
                    }
                    specMap.put("status", spec.getStatus());
                    if (spec.getRootNetID() != null) {
                        specMap.put("root_net_id", spec.getRootNetID());
                    }
                    specList.add(specMap);
                }
            }

            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("specifications", specList);
            resultData.put("count", specList.size());

            return new McpSchema.CallToolResult(
                mapper.writeValueAsString(resultData), false);
        } catch (IOException e) {
            return new McpSchema.CallToolResult(
                "YAWL engine error: " + e.getMessage(), true);
        }
    }

    private McpSchema.CallToolResult handleGetWorkItems(Map<String, Object> args) {
        try {
            ensureEngineConnection();

            String caseId = optionalArg(args, "case_id", null);

            List<WorkItemRecord> workItems;
            if (caseId != null) {
                workItems = interfaceBClient.getWorkItemsForCase(
                    caseId, sessionHandle);
            } else {
                workItems = interfaceBClient.getCompleteListOfLiveWorkItems(
                    sessionHandle);
            }

            List<Map<String, Object>> itemList = new ArrayList<>();
            if (workItems != null) {
                for (WorkItemRecord wir : workItems) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", wir.getID());
                    item.put("case_id", wir.getCaseID());
                    item.put("task_id", wir.getTaskID());
                    item.put("status", wir.getStatus());
                    item.put("spec_uri", wir.getSpecURI());
                    if (wir.getEnablementTimeMs() != null) {
                        item.put("enabled_time", wir.getEnablementTimeMs());
                    }
                    if (wir.getDataList() != null) {
                        item.put("data", wir.getDataList().toString());
                    }
                    itemList.add(item);
                }
            }

            Map<String, Object> resultData = new LinkedHashMap<>();
            resultData.put("work_items", itemList);
            resultData.put("count", itemList.size());
            if (caseId != null) {
                resultData.put("case_id", caseId);
            }

            return new McpSchema.CallToolResult(
                mapper.writeValueAsString(resultData), false);
        } catch (IOException e) {
            return new McpSchema.CallToolResult(
                "YAWL engine error: " + e.getMessage(), true);
        }
    }

    private McpSchema.CallToolResult handleCompleteWorkItem(Map<String, Object> args) {
        try {
            ensureEngineConnection();

            String workItemId = requireArg(args, "work_item_id");
            String outputData = optionalArg(args, "output_data", null);

            String checkoutResult = interfaceBClient.checkOutWorkItem(
                workItemId, sessionHandle);
            if (checkoutResult == null || checkoutResult.contains("<failure>")) {
                return new McpSchema.CallToolResult(
                    "Failed to check out work item: " + checkoutResult, true);
            }

            String checkinResult = interfaceBClient.checkInWorkItem(
                workItemId, outputData, sessionHandle);
            if (checkinResult == null || checkinResult.contains("<failure>")) {
                return new McpSchema.CallToolResult(
                    "Failed to check in work item: " + checkinResult, true);
            }

            String result = mapper.writeValueAsString(Map.of(
                "work_item_id", workItemId,
                "status", "completed"
            ));
            return new McpSchema.CallToolResult(result, false);
        } catch (IOException e) {
            return new McpSchema.CallToolResult(
                "YAWL engine error: " + e.getMessage(), true);
        }
    }

    private McpSchema.CallToolResult handleCancelCase(Map<String, Object> args) {
        try {
            ensureEngineConnection();

            String caseId = requireArg(args, "case_id");
            String result = interfaceBClient.cancelCase(caseId, sessionHandle);
            if (result == null || result.contains("<failure>")) {
                return new McpSchema.CallToolResult(
                    "Failed to cancel case: " + result, true);
            }

            String json = mapper.writeValueAsString(Map.of(
                "case_id", caseId,
                "status", "cancelled"
            ));
            return new McpSchema.CallToolResult(json, false);
        } catch (IOException e) {
            return new McpSchema.CallToolResult(
                "YAWL engine error: " + e.getMessage(), true);
        }
    }

    // =========================================================================
    // Resource Handlers
    // =========================================================================

    private McpSchema.ReadResourceResult readSpecificationsResource() {
        try {
            ensureEngineConnection();
            List<SpecificationData> specs = interfaceBClient.getSpecificationList(
                sessionHandle);

            List<Map<String, Object>> specList = new ArrayList<>();
            if (specs != null) {
                for (SpecificationData spec : specs) {
                    Map<String, Object> node = new LinkedHashMap<>();
                    YSpecificationID specId = spec.getID();
                    node.put("identifier", specId.getIdentifier());
                    node.put("version", specId.getVersionAsString());
                    node.put("uri", specId.getUri());
                    node.put("name", spec.getName());
                    node.put("status", spec.getStatus());
                    specList.add(node);
                }
            }

            return new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(
                    "yawl://specifications",
                    "application/json",
                    mapper.writeValueAsString(specList)
                )
            ));
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to read specifications from YAWL engine: " + e.getMessage(), e);
        }
    }

    private McpSchema.ReadResourceResult readCasesResource() {
        try {
            ensureEngineConnection();
            String casesXml = interfaceBClient.getAllRunningCases(sessionHandle);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("running_cases_xml", casesXml != null ? casesXml : "<none/>");

            return new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(
                    "yawl://cases",
                    "application/json",
                    mapper.writeValueAsString(data)
                )
            ));
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to read cases from YAWL engine: " + e.getMessage(), e);
        }
    }

    private McpSchema.ReadResourceResult readWorkItemsResource() {
        try {
            ensureEngineConnection();
            List<WorkItemRecord> items = interfaceBClient.getCompleteListOfLiveWorkItems(
                sessionHandle);

            List<Map<String, Object>> itemList = new ArrayList<>();
            if (items != null) {
                for (WorkItemRecord wir : items) {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", wir.getID());
                    node.put("case_id", wir.getCaseID());
                    node.put("task_id", wir.getTaskID());
                    node.put("status", wir.getStatus());
                    node.put("spec_uri", wir.getSpecURI());
                    itemList.add(node);
                }
            }

            return new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(
                    "yawl://workitems",
                    "application/json",
                    mapper.writeValueAsString(itemList)
                )
            ));
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to read work items from YAWL engine: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Prompt Handlers
    // =========================================================================

    private McpSchema.GetPromptResult handleWorkflowAnalysisPrompt(
            McpSchema.GetPromptRequest request) {
        try {
            ensureEngineConnection();

            Map<String, Object> promptArgs = request.arguments();
            String specIdentifier = (String) promptArgs.get("spec_identifier");
            if (specIdentifier == null) {
                throw new IllegalArgumentException(
                    "Required argument missing: spec_identifier");
            }
            String focus = promptArgs.containsKey("analysis_focus")
                ? (String) promptArgs.get("analysis_focus") : "general";

            List<SpecificationData> specs = interfaceBClient.getSpecificationList(
                sessionHandle);
            SpecificationData targetSpec = null;
            if (specs != null) {
                for (SpecificationData spec : specs) {
                    YSpecificationID specId = spec.getID();
                    if (specIdentifier.equals(specId.getIdentifier())
                            || specIdentifier.equals(specId.getUri())) {
                        targetSpec = spec;
                        break;
                    }
                }
            }

            String specInfo;
            if (targetSpec != null) {
                specInfo = String.format(
                    "Specification: %s (version %s)\nURI: %s\nStatus: %s\nName: %s\n" +
                    "Documentation: %s\nRoot Net: %s",
                    targetSpec.getID().getIdentifier(),
                    targetSpec.getID().getVersionAsString(),
                    targetSpec.getID().getUri(),
                    targetSpec.getStatus(),
                    targetSpec.getName(),
                    targetSpec.getDocumentation(),
                    targetSpec.getRootNetID());
            } else {
                specInfo = "Specification '" + specIdentifier
                    + "' not found in loaded specifications.";
            }

            String promptText = String.format(
                "Analyze the following YAWL workflow specification with a focus on %s:\n\n" +
                "%s\n\n" +
                "Provide:\n" +
                "1. A summary of the workflow's purpose\n" +
                "2. Key observations about its structure\n" +
                "3. Specific %s recommendations\n" +
                "4. Potential issues or improvements",
                focus, specInfo, focus);

            return new McpSchema.GetPromptResult(
                "Workflow analysis for specification: " + specIdentifier,
                List.of(new McpSchema.PromptMessage(
                    McpSchema.Role.USER,
                    new McpSchema.TextContent(promptText)
                ))
            );
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to generate workflow analysis prompt: " + e.getMessage(), e);
        }
    }

    private McpSchema.GetPromptResult handleTaskCompletionGuidePrompt(
            McpSchema.GetPromptRequest request) {
        try {
            ensureEngineConnection();

            Map<String, Object> promptArgs = request.arguments();
            String caseId = (String) promptArgs.get("case_id");
            String taskId = (String) promptArgs.get("task_id");
            if (caseId == null || taskId == null) {
                throw new IllegalArgumentException(
                    "Required arguments missing: case_id and task_id");
            }

            List<WorkItemRecord> workItems = interfaceBClient.getWorkItemsForCase(
                caseId, sessionHandle);

            String taskInfo = "No work items found for case " + caseId;
            if (workItems != null) {
                for (WorkItemRecord wir : workItems) {
                    if (wir.getTaskID().equals(taskId)) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Task ID: ").append(wir.getTaskID()).append("\n");
                        sb.append("Case ID: ").append(wir.getCaseID()).append("\n");
                        sb.append("Status: ").append(wir.getStatus()).append("\n");
                        sb.append("Specification: ").append(wir.getSpecURI()).append("\n");
                        if (wir.getDataList() != null) {
                            sb.append("Current Data: ").append(
                                wir.getDataList().toString()).append("\n");
                        }
                        if (wir.getDocumentation() != null) {
                            sb.append("Documentation: ").append(
                                wir.getDocumentation()).append("\n");
                        }
                        taskInfo = sb.toString();
                        break;
                    }
                }
            }

            String promptText = String.format(
                "Provide step-by-step guidance for completing the following " +
                "YAWL workflow task:\n\n%s\n\n" +
                "Include:\n" +
                "1. What this task requires\n" +
                "2. Expected input/output data format\n" +
                "3. Steps to complete the task using the complete_work_item tool\n" +
                "4. Common issues and how to resolve them",
                taskInfo);

            return new McpSchema.GetPromptResult(
                "Task completion guide for " + taskId + " in case " + caseId,
                List.of(new McpSchema.PromptMessage(
                    McpSchema.Role.USER,
                    new McpSchema.TextContent(promptText)
                ))
            );
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to generate task completion guide: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // YAWL Engine connection management
    // =========================================================================

    private void ensureEngineConnection() throws IOException {
        if (sessionHandle != null) {
            String check = interfaceBClient.checkConnection(sessionHandle);
            if (check != null && !check.contains("<failure>")) {
                return;
            }
        }
        connectToEngine();
    }

    private void connectToEngine() throws IOException {
        sessionHandle = interfaceBClient.connect(yawlUsername, yawlPassword);
        if (sessionHandle == null || sessionHandle.contains("<failure>")) {
            throw new IOException(
                "Failed to connect to YAWL engine: " + sessionHandle);
        }
    }

    private void disconnectFromEngine() {
        if (sessionHandle != null) {
            try {
                interfaceBClient.disconnect(sessionHandle);
            } catch (IOException e) {
                System.err.println(
                    "Warning: failed to disconnect from YAWL engine: "
                    + e.getMessage());
            }
            sessionHandle = null;
        }
    }

    // =========================================================================
    // Schema and argument helpers
    // =========================================================================

    private static Map<String, Object> stringProp(String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", "string");
        prop.put("description", description);
        return prop;
    }

    private static Map<String, Object> buildProperties(Object... keyValuePairs) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            properties.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return properties;
    }

    private static String requireArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required argument missing: " + name);
        }
        return value.toString();
    }

    private static String optionalArg(Map<String, Object> args, String name,
            String defaultValue) {
        Object value = args.get(name);
        if (value != null) {
            return value.toString();
        }
        return defaultValue;
    }

    /**
     * Entry point for running the YAWL MCP Server.
     * Reads configuration from environment variables:
     *   YAWL_ENGINE_URL - YAWL engine base URL (default: http://localhost:8080/yawl)
     *   YAWL_USERNAME   - YAWL admin username (default: admin)
     *   YAWL_PASSWORD   - YAWL admin password (default: YAWL)
     */
    public static void main(String[] args) {
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        if (engineUrl == null || engineUrl.isEmpty()) {
            engineUrl = "http://localhost:8080/yawl";
        }

        String username = System.getenv("YAWL_USERNAME");
        if (username == null || username.isEmpty()) {
            username = "admin";
        }

        String password = System.getenv("YAWL_PASSWORD");
        if (password == null || password.isEmpty()) {
            password = "YAWL";
        }

        System.err.println("Starting YAWL MCP Server v" + SERVER_VERSION);
        System.err.println("Engine URL: " + engineUrl);
        System.err.println("Transport: STDIO (official MCP SDK)");

        YawlMcpServer server = new YawlMcpServer(engineUrl, username, password);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down YAWL MCP Server...");
            server.stop();
        }));

        server.start();
    }
}
