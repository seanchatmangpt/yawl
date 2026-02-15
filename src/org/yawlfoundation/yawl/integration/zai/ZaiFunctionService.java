package org.yawlfoundation.yawl.integration.zai;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.*;

/**
 * Z.AI Function Calling Service for YAWL
 *
 * Enables AI models to call YAWL workflow operations through function calling.
 * Integrates with YAWL Engine via InterfaceA and InterfaceB clients.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiFunctionService {

    // Z.AI default model (override via ZAI_MODEL env)
    private static final String DEFAULT_MODEL = "GLM-4.7-Flash";

    private static String defaultModel() {
        String env = System.getenv("ZAI_MODEL");
        return (env != null && !env.isEmpty()) ? env : DEFAULT_MODEL;
    }

    private final ZaiHttpClient httpClient;
    private final Map<String, YawlFunctionHandler> functionHandlers;
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final InterfaceA_EnvironmentBasedClient interfaceAClient;
    private final String yawlUsername;
    private final String yawlPassword;
    private String sessionHandle;
    private boolean initialized;

    /**
     * Interface for YAWL function handlers
     */
    public interface YawlFunctionHandler {
        String execute(Map<String, Object> arguments) throws IOException;
    }

    /**
     * Initialize with environment variables
     */
    public ZaiFunctionService() {
        this(
            getRequiredEnv("ZAI_API_KEY"),
            getRequiredEnv("YAWL_ENGINE_URL"),
            getRequiredEnv("YAWL_USERNAME"),
            getRequiredEnv("YAWL_PASSWORD")
        );
    }

    /**
     * Initialize with explicit configuration
     */
    public ZaiFunctionService(String zaiApiKey, String yawlEngineUrl, String username, String password) {
        if (zaiApiKey == null || zaiApiKey.isEmpty()) {
            throw new IllegalArgumentException("ZAI_API_KEY is required");
        }
        if (yawlEngineUrl == null || yawlEngineUrl.isEmpty()) {
            throw new IllegalArgumentException("YAWL_ENGINE_URL is required (e.g., http://localhost:8080/yawl)");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("YAWL_USERNAME is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("YAWL_PASSWORD is required");
        }

        this.httpClient = new ZaiHttpClient(zaiApiKey);
        this.yawlUsername = username;
        this.yawlPassword = password;

        String interfaceAUrl = yawlEngineUrl + "/ia";
        String interfaceBUrl = yawlEngineUrl + "/ib";

        this.interfaceAClient = new InterfaceA_EnvironmentBasedClient(interfaceAUrl);
        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(interfaceBUrl);
        this.functionHandlers = new HashMap<>();

        connectToYawlEngine();
        registerDefaultFunctions();
        this.initialized = true;
    }

    private static String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " environment variable not set");
        }
        return value;
    }

    private void connectToYawlEngine() {
        try {
            this.sessionHandle = interfaceBClient.connect(yawlUsername, yawlPassword);
            if (sessionHandle == null || sessionHandle.contains("failure") || sessionHandle.contains("error")) {
                throw new RuntimeException("Failed to connect to YAWL engine: " + sessionHandle);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect to YAWL engine at " +
                interfaceBClient.getBackEndURI() + ": " + e.getMessage(), e);
        }
    }

    private void ensureConnection() {
        if (sessionHandle == null) {
            connectToYawlEngine();
        }
    }

    /**
     * Register default YAWL workflow functions
     */
    private void registerDefaultFunctions() {
        registerFunction("start_workflow", args -> {
            String workflowId = (String) args.get("workflow_id");
            String inputData = args.get("input_data") != null ? args.get("input_data").toString() : null;
            return startWorkflow(workflowId, inputData);
        });

        registerFunction("get_workflow_status", args -> {
            String caseId = (String) args.get("case_id");
            return getWorkflowStatus(caseId);
        });

        registerFunction("complete_task", args -> {
            String caseId = (String) args.get("case_id");
            String taskId = (String) args.get("task_id");
            String outputData = args.get("output_data") != null ? args.get("output_data").toString() : null;
            return completeTask(caseId, taskId, outputData);
        });

        registerFunction("list_workflows", args -> listWorkflows());
    }

    /**
     * Register a custom function handler
     */
    public void registerFunction(String name, YawlFunctionHandler handler) {
        functionHandlers.put(name, handler);
    }

    /**
     * Process a natural language request with function calling
     */
    public String processWithFunctions(String userMessage) {
        return processWithFunctions(userMessage, defaultModel());
    }

    /**
     * Process a natural language request with function calling
     */
    public String processWithFunctions(String userMessage, String model) {
        if (!initialized) {
            throw new IllegalStateException("Service not initialized");
        }

        String functionPrompt = buildFunctionSelectionPrompt(userMessage);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(mapOf("role", "system", "content", getSystemPrompt()));
        messages.add(mapOf("role", "user", "content", functionPrompt));

        try {
            String response = httpClient.createChatCompletion(model, messages);
            String content = httpClient.extractContent(response);

            FunctionCall functionCall = parseFunctionCall(content);

            if (functionCall != null) {
                String argsJson = mapToJson(functionCall.arguments);
                String result = executeFunction(functionCall.name, argsJson);
                return formatResult(functionCall.name, result);
            } else {
                return content;
            }
        } catch (IOException e) {
            throw new RuntimeException("Function processing failed: " + e.getMessage(), e);
        }
    }

    private String buildFunctionSelectionPrompt(String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("User request: ").append(userMessage).append("\n\n");
        sb.append("Available functions:\n");
        for (String funcName : functionHandlers.keySet()) {
            sb.append("- ").append(funcName).append("\n");
        }
        sb.append("\nIf this request requires calling a function, respond in this exact format:\n");
        sb.append("FUNCTION: [function_name]\n");
        sb.append("ARGUMENTS: {\"param1\": \"value1\", ...}\n\n");
        sb.append("If no function is needed, respond normally.");
        return sb.toString();
    }

    private String getSystemPrompt() {
        return "You are an intelligent assistant for YAWL workflow operations. " +
                "Analyze user requests and call appropriate functions when needed. " +
                "Be precise and follow the exact format when calling functions.";
    }

    private FunctionCall parseFunctionCall(String content) {
        String upperContent = content.toUpperCase();

        int funcIdx = upperContent.indexOf("FUNCTION:");
        if (funcIdx == -1) {
            return null;
        }

        int funcStart = funcIdx + "FUNCTION:".length();
        int funcEnd = content.indexOf("\n", funcStart);
        if (funcEnd == -1) funcEnd = content.length();

        String funcName = content.substring(funcStart, funcEnd).trim();

        int argsIdx = upperContent.indexOf("ARGUMENTS:");
        String argsJson = "{}";
        if (argsIdx != -1) {
            int argsStart = argsIdx + "ARGUMENTS:".length();
            String argsPart = content.substring(argsStart).trim();
            int braceStart = argsPart.indexOf("{");
            int braceEnd = argsPart.lastIndexOf("}");
            if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
                argsJson = argsPart.substring(braceStart, braceEnd + 1);
            }
        }

        return new FunctionCall(funcName.toLowerCase(), parseJsonToMap(argsJson));
    }

    private Map<String, Object> parseJsonToMap(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || !json.startsWith("{")) {
            return result;
        }

        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) {
            return result;
        }

        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                result.put(key, value);
            }
        }
        return result;
    }

    private String formatResult(String functionName, String result) {
        return "Function: " + functionName + "\nResult: " + result;
    }

    /**
     * Execute a registered function
     */
    public String executeFunction(String name, String argumentsJson) {
        YawlFunctionHandler handler = functionHandlers.get(name);
        if (handler == null) {
            return "{\"error\": \"Unknown function: " + name + "\"}";
        }

        try {
            Map<String, Object> args = parseJsonToMap(argumentsJson);
            return handler.execute(args);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String startWorkflow(String workflowId, String inputData) throws IOException {
        ensureConnection();

        YSpecificationID specID = parseSpecificationID(workflowId);
        String caseData = inputData != null ? wrapDataInXML(inputData) : null;

        String caseId = interfaceBClient.launchCase(specID, caseData, null, sessionHandle);

        if (caseId == null || caseId.contains("failure") || caseId.contains("error")) {
            return "{\"error\": \"Failed to start workflow: " + caseId + "\"}";
        }

        return "{\"status\": \"started\", \"workflow_id\": \"" + workflowId +
               "\", \"case_id\": \"" + caseId + "\"}";
    }

    private String getWorkflowStatus(String caseId) throws IOException {
        ensureConnection();

        List<WorkItemRecord> workItems = interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

        if (workItems == null) {
            return "{\"error\": \"Case not found: " + caseId + "\"}";
        }

        StringBuilder tasksJson = new StringBuilder("[");
        boolean first = true;
        for (WorkItemRecord item : workItems) {
            if (!first) tasksJson.append(",");
            tasksJson.append("{\"task_id\": \"").append(item.getTaskID())
                    .append("\", \"status\": \"").append(item.getStatus())
                    .append("\", \"enabled_time\": \"").append(item.getEnablementTimeMs())
                    .append("\"}");
            first = false;
        }
        tasksJson.append("]");

        return "{\"case_id\": \"" + caseId +
               "\", \"status\": \"running\", \"current_tasks\": " + tasksJson + "}";
    }

    private String completeTask(String caseId, String taskId, String outputData) throws IOException {
        ensureConnection();

        List<WorkItemRecord> workItems = interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

        if (workItems == null || workItems.isEmpty()) {
            return "{\"error\": \"No work items found for case: " + caseId + "\"}";
        }

        WorkItemRecord targetItem = null;
        for (WorkItemRecord item : workItems) {
            if (item.getTaskID().equals(taskId)) {
                targetItem = item;
                break;
            }
        }

        if (targetItem == null) {
            return "{\"error\": \"Task not found: " + taskId + " in case: " + caseId + "\"}";
        }

        String workItemID = targetItem.getID();
        String dataToSend = outputData != null ? wrapDataInXML(outputData) :
                (targetItem.getDataList() != null ? targetItem.getDataList().toString() : null);

        String checkoutResult = interfaceBClient.checkOutWorkItem(workItemID, sessionHandle);
        if (checkoutResult == null || checkoutResult.contains("failure") || checkoutResult.contains("error")) {
            return "{\"error\": \"Failed to checkout work item: " + checkoutResult + "\"}";
        }

        String checkinResult = interfaceBClient.checkInWorkItem(workItemID, dataToSend, sessionHandle);
        if (checkinResult == null || !checkinResult.contains("success")) {
            return "{\"error\": \"Failed to complete task: " + checkinResult + "\"}";
        }

        return "{\"status\": \"completed\", \"case_id\": \"" + caseId +
               "\", \"task_id\": \"" + taskId + "\"}";
    }

    private String listWorkflows() throws IOException {
        ensureConnection();

        List<SpecificationData> specs = interfaceBClient.getSpecificationList(sessionHandle);

        StringBuilder json = new StringBuilder("{\"workflows\": [");
        boolean first = true;
        if (specs != null) {
            for (SpecificationData spec : specs) {
                if (!first) json.append(",");
                String name = spec.getName() != null ? spec.getName() : spec.getSpecURI();
                json.append("\"").append(name).append("\"");
                first = false;
            }
        }
        json.append("]}");

        return json.toString();
    }

    private YSpecificationID parseSpecificationID(String workflowId) {
        String[] parts = workflowId.split(":");
        if (parts.length == 3) {
            return new YSpecificationID(parts[0], parts[1], parts[2]);
        } else if (parts.length == 1) {
            return new YSpecificationID(parts[0], "0.1", "0.1");
        } else {
            throw new IllegalArgumentException(
                "Invalid workflow ID format. Use 'identifier:version:uri' or just 'identifier'"
            );
        }
    }

    private String wrapDataInXML(String data) {
        if (data.trim().startsWith("<")) {
            return data;
        }
        return "<data>" + data + "</data>";
    }

    private List<String> extractSpecificationNames(String specsXML) {
        List<String> names = new ArrayList<>();

        int pos = 0;
        while (true) {
            int specStart = specsXML.indexOf("<specIdentifier>", pos);
            if (specStart == -1) break;

            int specEnd = specsXML.indexOf("</specIdentifier>", specStart);
            if (specEnd == -1) break;

            String specContent = specsXML.substring(specStart + 16, specEnd);
            names.add(specContent.trim());

            pos = specEnd;
        }

        if (names.isEmpty()) {
            names.add("No specifications loaded");
        }

        return names;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Set<String> getRegisteredFunctions() {
        return functionHandlers.keySet();
    }

    public void disconnect() {
        try {
            if (sessionHandle != null) {
                interfaceBClient.disconnect(sessionHandle);
                sessionHandle = null;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to disconnect from YAWL engine", e);
        }
    }

    private String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append("\"").append(entry.getValue()).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, String> mapOf(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static class FunctionCall {
        final String name;
        final Map<String, Object> arguments;

        FunctionCall(String name, Map<String, Object> arguments) {
            this.name = name;
            this.arguments = arguments;
        }
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        ZaiFunctionService service = new ZaiFunctionService();

        System.out.println("Registered functions: " + service.getRegisteredFunctions());

        System.out.println("\n=== Testing List Workflows ===");
        String result = service.processWithFunctions("What workflows are available?");
        System.out.println(result);

        System.out.println("\n=== Testing Start Workflow ===");
        result = service.processWithFunctions("Start an OrderProcessing workflow with customer 'Acme Corp'");
        System.out.println(result);

        System.out.println("\n=== Testing Get Status ===");
        result = service.processWithFunctions("Check status of case case-12345");
        System.out.println(result);

        service.disconnect();
    }
}
