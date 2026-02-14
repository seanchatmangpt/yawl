package org.yawlfoundation.yawl.integration.mcp;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

/**
 * Model Context Protocol (MCP) Server Integration for YAWL
 *
 * Exposes YAWL workflow engine capabilities through the MCP protocol,
 * enabling AI models to interact with YAWL workflows as tools.
 *
 * Implements 10 MCP tools for complete YAWL workflow management:
 * 1. launch_case - Launch new workflow instances
 * 2. get_case_status - Query case execution state
 * 3. get_enabled_work_items - List available work items
 * 4. checkout_work_item - Start executing a work item
 * 5. checkin_work_item - Complete work item with output data
 * 6. get_work_item_data - Retrieve work item input/output data
 * 7. cancel_case - Terminate running case
 * 8. get_specification_list - List loaded workflow specifications
 * 9. upload_specification - Deploy new workflow definitions
 * 10. get_case_data - Retrieve all data for a case
 *
 * Environment Configuration:
 * - YAWL_ENGINE_URL: YAWL engine base URL (default: http://localhost:8080/yawl)
 * - YAWL_USERNAME: Admin username (default: admin)
 * - YAWL_PASSWORD: Admin password (default: YAWL)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpServer {

    private boolean running = false;
    private int port = 3000;
    private ServerSocket serverSocket;

    private InterfaceB_EnvironmentBasedClient interfaceBClient;
    private InterfaceA_EnvironmentBasedClient interfaceAClient;
    private String sessionHandle;

    private final String engineUrl;
    private final String username;
    private final String password;
    private final JSONParser jsonParser = new JSONParser();

    /**
     * Constructor for YAWL MCP Server with environment-based configuration
     */
    public YawlMcpServer() {
        this(3000);
    }

    /**
     * Constructor with custom port and environment-based YAWL configuration
     * @param port the port to run the MCP server on
     */
    public YawlMcpServer(int port) {
        this.port = port;

        this.engineUrl = System.getenv().getOrDefault("YAWL_ENGINE_URL", "http://localhost:8080/yawl");
        this.username = System.getenv().getOrDefault("YAWL_USERNAME", "admin");
        this.password = System.getenv().getOrDefault("YAWL_PASSWORD", "YAWL");

        String interfaceBUrl = engineUrl + "/ib";
        String interfaceAUrl = engineUrl + "/ia";

        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(interfaceBUrl);
        this.interfaceAClient = new InterfaceA_EnvironmentBasedClient(interfaceAUrl);

        System.out.println("Initializing YAWL MCP Server on port " + port);
        System.out.println("YAWL Engine URL: " + engineUrl);
    }

    /**
     * Connect to YAWL engine and establish session
     * @throws IOException if connection fails
     */
    private void connectToEngine() throws IOException {
        sessionHandle = interfaceBClient.connect(username, password);

        if (sessionHandle == null || sessionHandle.contains("<failure>")) {
            throw new IOException("Failed to connect to YAWL engine: " + sessionHandle);
        }

        System.out.println("Connected to YAWL engine with session: " + sessionHandle.substring(0, Math.min(20, sessionHandle.length())) + "...");
    }

    /**
     * Start the MCP server
     * @throws IOException if server cannot start
     */
    public void start() throws IOException {
        if (running) {
            System.out.println("Server already running");
            return;
        }

        connectToEngine();

        serverSocket = new ServerSocket(port);
        running = true;

        System.out.println("YAWL MCP Server started on port " + port);
        System.out.println("Listening for MCP connections...");

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error handling client: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handle incoming MCP client connection
     * @param clientSocket the client socket
     */
    private void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String requestLine;
            while ((requestLine = in.readLine()) != null) {
                String response = processRequest(requestLine);
                out.println(response);
            }
        } catch (IOException e) {
            System.err.println("Client communication error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
        }
    }

    /**
     * Process MCP request and route to appropriate tool handler
     * @param request JSON-RPC 2.0 formatted request
     * @return JSON-RPC 2.0 formatted response
     */
    @SuppressWarnings("unchecked")
    private String processRequest(String request) {
        try {
            JSONObject req = (JSONObject) jsonParser.parse(request);
            String method = (String) req.get("method");
            JSONObject params = (JSONObject) req.get("params");
            Object id = req.get("id");

            JSONObject response = new JSONObject();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            if ("tools/list".equals(method)) {
                response.put("result", listTools());
            } else if ("tools/call".equals(method)) {
                String toolName = (String) params.get("name");
                JSONObject arguments = (JSONObject) params.get("arguments");
                response.put("result", callTool(toolName, arguments));
            } else {
                JSONObject error = new JSONObject();
                error.put("code", -32601);
                error.put("message", "Method not found: " + method);
                response.put("error", error);
            }

            return response.toJSONString();
        } catch (ParseException e) {
            return createErrorResponse(null, -32700, "Parse error: " + e.getMessage());
        } catch (Exception e) {
            return createErrorResponse(null, -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * List all available MCP tools
     * @return JSON object with tool definitions
     */
    @SuppressWarnings("unchecked")
    private JSONObject listTools() {
        JSONObject result = new JSONObject();
        JSONArray tools = new JSONArray();

        tools.add(createToolDefinition("launch_case",
            "Launch a new YAWL workflow case",
            createToolParameters(
                createParameter("spec_id", "string", "Specification identifier", true),
                createParameter("spec_version", "string", "Specification version (default: 0.1)", false),
                createParameter("spec_uri", "string", "Specification URI (default: same as spec_id)", false),
                createParameter("case_data", "string", "XML case input data", false)
            )));

        tools.add(createToolDefinition("get_case_status",
            "Get the current status and state of a workflow case",
            createToolParameters(
                createParameter("case_id", "string", "Case identifier", true)
            )));

        tools.add(createToolDefinition("get_enabled_work_items",
            "Get list of all enabled work items across all cases",
            createToolParameters()));

        tools.add(createToolDefinition("checkout_work_item",
            "Checkout a work item to begin execution",
            createToolParameters(
                createParameter("work_item_id", "string", "Work item identifier", true)
            )));

        tools.add(createToolDefinition("checkin_work_item",
            "Complete a work item with output data",
            createToolParameters(
                createParameter("work_item_id", "string", "Work item identifier", true),
                createParameter("data", "string", "XML output data", true)
            )));

        tools.add(createToolDefinition("get_work_item_data",
            "Get input/output data for a specific work item",
            createToolParameters(
                createParameter("work_item_id", "string", "Work item identifier", true)
            )));

        tools.add(createToolDefinition("cancel_case",
            "Cancel a running workflow case",
            createToolParameters(
                createParameter("case_id", "string", "Case identifier", true)
            )));

        tools.add(createToolDefinition("get_specification_list",
            "List all loaded workflow specifications",
            createToolParameters()));

        tools.add(createToolDefinition("upload_specification",
            "Upload a new YAWL workflow specification",
            createToolParameters(
                createParameter("spec_xml", "string", "Complete YAWL specification XML", true)
            )));

        tools.add(createToolDefinition("get_case_data",
            "Get all data for a specific case",
            createToolParameters(
                createParameter("case_id", "string", "Case identifier", true)
            )));

        result.put("tools", tools);
        return result;
    }

    /**
     * Call a specific MCP tool
     * @param toolName the name of the tool to call
     * @param arguments the tool arguments
     * @return tool execution result
     */
    @SuppressWarnings("unchecked")
    private JSONObject callTool(String toolName, JSONObject arguments) {
        try {
            JSONObject result = new JSONObject();
            JSONArray content = new JSONArray();

            String textResult = null;

            switch (toolName) {
                case "launch_case":
                    textResult = handleLaunchCase(arguments);
                    break;
                case "get_case_status":
                    textResult = handleGetCaseStatus(arguments);
                    break;
                case "get_enabled_work_items":
                    textResult = handleGetEnabledWorkItems(arguments);
                    break;
                case "checkout_work_item":
                    textResult = handleCheckoutWorkItem(arguments);
                    break;
                case "checkin_work_item":
                    textResult = handleCheckinWorkItem(arguments);
                    break;
                case "get_work_item_data":
                    textResult = handleGetWorkItemData(arguments);
                    break;
                case "cancel_case":
                    textResult = handleCancelCase(arguments);
                    break;
                case "get_specification_list":
                    textResult = handleGetSpecificationList(arguments);
                    break;
                case "upload_specification":
                    textResult = handleUploadSpecification(arguments);
                    break;
                case "get_case_data":
                    textResult = handleGetCaseData(arguments);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown tool: " + toolName);
            }

            JSONObject textContent = new JSONObject();
            textContent.put("type", "text");
            textContent.put("text", textResult);
            content.add(textContent);

            result.put("content", content);
            return result;

        } catch (Exception e) {
            JSONObject error = new JSONObject();
            error.put("error", e.getMessage());
            JSONArray content = new JSONArray();
            JSONObject textContent = new JSONObject();
            textContent.put("type", "text");
            textContent.put("text", "Error: " + e.getMessage());
            content.add(textContent);
            error.put("content", content);
            return error;
        }
    }

    /**
     * Handle launch_case tool
     */
    private String handleLaunchCase(JSONObject args) throws IOException {
        String specId = (String) args.get("spec_id");
        String specVersion = args.getOrDefault("spec_version", "0.1").toString();
        String specUri = args.getOrDefault("spec_uri", specId).toString();
        String caseData = (String) args.get("case_data");

        YSpecificationID ySpecId = new YSpecificationID(specId, specVersion, specUri);
        String caseId = interfaceBClient.launchCase(ySpecId, caseData, sessionHandle);

        if (caseId != null && caseId.contains("<failure>")) {
            throw new IOException("Failed to launch case: " + caseId);
        }

        return "Case launched successfully. Case ID: " + caseId;
    }

    /**
     * Handle get_case_status tool
     */
    private String handleGetCaseStatus(JSONObject args) throws IOException {
        String caseId = (String) args.get("case_id");
        String caseState = interfaceBClient.getCaseState(caseId, sessionHandle);

        if (caseState != null && caseState.contains("<failure>")) {
            throw new IOException("Failed to get case status: " + caseState);
        }

        return "Case status for " + caseId + ":\n" + caseState;
    }

    /**
     * Handle get_enabled_work_items tool
     */
    private String handleGetEnabledWorkItems(JSONObject args) throws IOException {
        List<WorkItemRecord> workItems = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);

        StringBuilder result = new StringBuilder("Live work items:\n");
        for (WorkItemRecord item : workItems) {
            result.append("- ID: ").append(item.getID())
                  .append(", Task: ").append(item.getTaskName())
                  .append(", Status: ").append(item.getStatus())
                  .append(", Case: ").append(item.getCaseID())
                  .append("\n");
        }

        return result.toString();
    }

    /**
     * Handle checkout_work_item tool
     */
    private String handleCheckoutWorkItem(JSONObject args) throws IOException {
        String workItemId = (String) args.get("work_item_id");
        String result = interfaceBClient.checkOutWorkItem(workItemId, sessionHandle);

        if (result != null && result.contains("<failure>")) {
            throw new IOException("Failed to checkout work item: " + result);
        }

        return "Work item checked out successfully:\n" + result;
    }

    /**
     * Handle checkin_work_item tool
     */
    private String handleCheckinWorkItem(JSONObject args) throws IOException {
        String workItemId = (String) args.get("work_item_id");
        String data = (String) args.get("data");

        String result = interfaceBClient.checkInWorkItem(workItemId, data, sessionHandle);

        if (result != null && result.contains("<failure>")) {
            throw new IOException("Failed to checkin work item: " + result);
        }

        return "Work item checked in successfully:\n" + result;
    }

    /**
     * Handle get_work_item_data tool
     */
    private String handleGetWorkItemData(JSONObject args) throws IOException {
        String workItemId = (String) args.get("work_item_id");
        String data = interfaceBClient.getWorkItem(workItemId, sessionHandle);

        if (data != null && data.contains("<failure>")) {
            throw new IOException("Failed to get work item data: " + data);
        }

        return "Work item data:\n" + data;
    }

    /**
     * Handle cancel_case tool
     */
    private String handleCancelCase(JSONObject args) throws IOException {
        String caseId = (String) args.get("case_id");
        String result = interfaceBClient.cancelCase(caseId, sessionHandle);

        if (result != null && result.contains("<failure>")) {
            throw new IOException("Failed to cancel case: " + result);
        }

        return "Case cancelled successfully: " + result;
    }

    /**
     * Handle get_specification_list tool
     */
    private String handleGetSpecificationList(JSONObject args) throws IOException {
        List<SpecificationData> specs = interfaceBClient.getSpecificationList(sessionHandle);

        StringBuilder result = new StringBuilder("Loaded specifications:\n");
        for (SpecificationData spec : specs) {
            result.append("- ID: ").append(spec.getID())
                  .append(", Name: ").append(spec.getName())
                  .append(", Version: ").append(spec.getSpecVersion())
                  .append("\n");
        }

        return result.toString();
    }

    /**
     * Handle upload_specification tool
     */
    private String handleUploadSpecification(JSONObject args) throws IOException {
        String specXml = (String) args.get("spec_xml");
        String result = interfaceAClient.uploadSpecification(specXml, sessionHandle);

        if (result != null && result.contains("<failure>")) {
            throw new IOException("Failed to upload specification: " + result);
        }

        return "Specification uploaded successfully: " + result;
    }

    /**
     * Handle get_case_data tool
     */
    private String handleGetCaseData(JSONObject args) throws IOException {
        String caseId = (String) args.get("case_id");
        String data = interfaceBClient.getCaseData(caseId, sessionHandle);

        if (data != null && data.contains("<failure>")) {
            throw new IOException("Failed to get case data: " + data);
        }

        return "Case data for " + caseId + ":\n" + data;
    }

    /**
     * Helper method to create tool definition
     */
    @SuppressWarnings("unchecked")
    private JSONObject createToolDefinition(String name, String description, JSONObject inputSchema) {
        JSONObject tool = new JSONObject();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        return tool;
    }

    /**
     * Helper method to create tool parameters schema
     */
    @SuppressWarnings("unchecked")
    private JSONObject createToolParameters(JSONObject... params) {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");

        JSONObject properties = new JSONObject();
        JSONArray required = new JSONArray();

        for (JSONObject param : params) {
            String name = (String) param.get("name");
            properties.put(name, param);
            if (param.get("required").equals(true)) {
                required.add(name);
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    /**
     * Helper method to create parameter definition
     */
    @SuppressWarnings("unchecked")
    private JSONObject createParameter(String name, String type, String description, boolean required) {
        JSONObject param = new JSONObject();
        param.put("name", name);
        param.put("type", type);
        param.put("description", description);
        param.put("required", required);
        return param;
    }

    /**
     * Create JSON-RPC error response
     */
    @SuppressWarnings("unchecked")
    private String createErrorResponse(Object id, int code, String message) {
        JSONObject response = new JSONObject();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        JSONObject error = new JSONObject();
        error.put("code", code);
        error.put("message", message);
        response.put("error", error);

        return response.toJSONString();
    }

    /**
     * Stop the MCP server
     */
    public void stop() {
        if (!running) {
            System.out.println("Server not running");
            return;
        }

        System.out.println("Stopping YAWL MCP Server...");
        running = false;

        try {
            if (sessionHandle != null) {
                interfaceBClient.disconnect(sessionHandle);
            }
        } catch (IOException e) {
            System.err.println("Error disconnecting from YAWL engine: " + e.getMessage());
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }

        System.out.println("YAWL MCP Server stopped");
    }

    /**
     * Check if server is running
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        int port = 3000;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }

        YawlMcpServer server = new YawlMcpServer(port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down YAWL MCP Server...");
            server.stop();
        }));

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start MCP server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
