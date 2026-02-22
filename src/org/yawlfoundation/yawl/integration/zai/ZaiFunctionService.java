package org.yawlfoundation.yawl.integration.zai;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import ai.z.openapi.ZaiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;

/**
 * Z.AI Function Calling Service for YAWL — Java 25 Edition.
 *
 * <p>Enables AI models to call YAWL workflow operations through function calling.
 * Integrates with the YAWL Engine via InterfaceA and InterfaceB clients.
 *
 * <p>Java 25 upgrades applied:
 * <ul>
 *   <li>{@link FunctionCall} is now a record (immutable, auto-equals/hashCode)</li>
 *   <li>JSON parsing uses Jackson's {@link ObjectMapper} instead of hand-rolled string splitting</li>
 *   <li>Uses official {@link ai.z.openapi.ZaiClient} from ai.z.openapi SDK</li>
 *   <li>Pattern matching in switch for function dispatch classification</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ZaiFunctionService {

    private static final Logger logger = LogManager.getLogger(ZaiFunctionService.class);

    // Z.AI default model (override via ZAI_MODEL env)
    private static final String DEFAULT_MODEL = "GLM-4.7-Flash";

    private static String defaultModel() {
        String env = System.getenv("ZAI_MODEL");
        return (env != null && !env.isEmpty()) ? env : DEFAULT_MODEL;
    }

    /**
     * Interface for YAWL function handlers.
     */
    public interface YawlFunctionHandler {
        String execute(Map<String, Object> arguments) throws IOException;
    }

    /**
     * Immutable record representing a parsed function call from the AI response.
     *
     * @param name      the function name (lowercase)
     * @param arguments the function arguments as a map
     */
    public record FunctionCall(String name, Map<String, Object> arguments) {

        public FunctionCall {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("FunctionCall name is required");
            }
            if (arguments == null) {
                throw new IllegalArgumentException("FunctionCall arguments must not be null");
            }
            arguments = Map.copyOf(arguments);
        }
    }

    private final ZaiClient zaiClient;
    private final Map<String, YawlFunctionHandler> functionHandlers;
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final InterfaceA_EnvironmentBasedClient interfaceAClient;
    private final String yawlEngineUrl;
    private final String yawlUsername;
    private final String yawlPassword;
    private final ObjectMapper objectMapper;
    private String sessionHandle;
    private volatile boolean initialized;

    /**
     * Initialize with environment variables.
     * Fails fast if any required variable is missing.
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
     * Initialize with explicit configuration.
     *
     * @param zaiApiKey    Z.AI API key (must not be null or blank)
     * @param yawlEngineUrl YAWL engine base URL
     * @param username     engine username
     * @param password     engine password
     */
    public ZaiFunctionService(String zaiApiKey, String yawlEngineUrl,
                               String username, String password) {
        if (zaiApiKey == null || zaiApiKey.isBlank()) {
            throw new IllegalArgumentException(
                "ZAI_API_KEY is required. Set ZAI_API_KEY environment variable.");
        }
        if (yawlEngineUrl == null || yawlEngineUrl.isBlank()) {
            throw new IllegalArgumentException(
                "YAWL_ENGINE_URL is required (e.g., http://localhost:8080/yawl)");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("YAWL_USERNAME is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("YAWL_PASSWORD is required");
        }

        this.zaiClient = ZaiClientFactory.withApiKey(zaiApiKey);
        this.yawlEngineUrl = yawlEngineUrl;
        this.yawlUsername = username;
        this.yawlPassword = password;
        this.interfaceAClient = new InterfaceA_EnvironmentBasedClient(yawlEngineUrl + "/ia");
        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(yawlEngineUrl + "/ib");
        this.functionHandlers = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();

        connectToYawlEngine();
        registerDefaultFunctions();
        this.initialized = true;
    }

    private static String getRequiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                name + " environment variable is required but not set.");
        }
        return value;
    }

    private void connectToYawlEngine() {
        try {
            this.sessionHandle = interfaceBClient.connect(yawlUsername, yawlPassword);
            if (sessionHandle == null || sessionHandle.contains("failure")
                    || sessionHandle.contains("error")) {
                throw new RuntimeException(
                    "Failed to connect to YAWL engine at " + yawlEngineUrl
                    + ": " + sessionHandle);
            }
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to connect to YAWL engine at " + yawlEngineUrl + ": " + e.getMessage(), e);
        }
    }

    private void ensureConnection() {
        if (sessionHandle == null) {
            connectToYawlEngine();
        }
    }

    /**
     * Register default YAWL workflow functions.
     */
    private void registerDefaultFunctions() {
        registerFunction("start_workflow", args -> {
            String workflowId = (String) args.get("workflow_id");
            String inputData = args.get("input_data") != null
                ? args.get("input_data").toString() : null;
            return startWorkflow(workflowId, inputData);
        });

        registerFunction("get_workflow_status", args -> {
            String caseId = (String) args.get("case_id");
            return getWorkflowStatus(caseId);
        });

        registerFunction("complete_task", args -> {
            String caseId = (String) args.get("case_id");
            String taskId = (String) args.get("task_id");
            String outputData = args.get("output_data") != null
                ? args.get("output_data").toString() : null;
            return completeTask(caseId, taskId, outputData);
        });

        registerFunction("list_workflows", args -> listWorkflows());
    }

    /**
     * Register a custom function handler.
     *
     * @param name    the function name (must be unique)
     * @param handler the handler implementation
     */
    public void registerFunction(String name, YawlFunctionHandler handler) {
        functionHandlers.put(name, handler);
    }

    /**
     * Process a natural language request with function calling using the default model.
     *
     * @param userMessage the user's natural language request
     * @return the function result or a direct AI response
     */
    public String processWithFunctions(String userMessage) {
        return processWithFunctions(userMessage, defaultModel());
    }

    /**
     * Process a natural language request with function calling.
     *
     * @param userMessage the user's natural language request
     * @param model       the model to use
     * @return the function result or a direct AI response
     */
    public String processWithFunctions(String userMessage, String model) {
        if (!initialized) {
            throw new IllegalStateException("ZaiFunctionService is not initialized");
        }

        String functionPrompt = buildFunctionSelectionPrompt(userMessage);

        List<ChatMessage> messages = List.of(
                ChatMessage.builder()
                        .role(ChatMessageRole.SYSTEM.value())
                        .content(getSystemPrompt())
                        .build(),
                ChatMessage.builder()
                        .role(ChatMessageRole.USER.value())
                        .content(functionPrompt)
                        .build()
        );

        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(0.7f)
                    .maxTokens(2048)
                    .build();

            ChatCompletionResponse response = zaiClient.chat().createChatCompletion(params);

            if (!response.isSuccess()) {
                throw new RuntimeException("Chat API call failed: " + response.getMsg());
            }

            String content = response.getData().getChoices().get(0).getMessage().getContent().toString();

            FunctionCall functionCall = parseFunctionCall(content);

            if (functionCall != null) {
                String result = executeFunction(functionCall.name(),
                    objectMapper.writeValueAsString(functionCall.arguments()));
                return formatResult(functionCall.name(), result);
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
        return "You are an intelligent assistant for YAWL workflow operations. "
            + "Analyze user requests and call appropriate functions when needed. "
            + "Be precise and follow the exact format when calling functions.";
    }

    /**
     * Parse a function call from the AI response content.
     *
     * @param content the AI model's raw text response
     * @return a {@link FunctionCall} record if detected, or null if the model responded directly
     */
    private FunctionCall parseFunctionCall(String content) {
        String upperContent = content.toUpperCase();

        int funcIdx = upperContent.indexOf("FUNCTION:");
        if (funcIdx == -1) {
            return null;
        }

        int funcStart = funcIdx + "FUNCTION:".length();
        int funcEnd = content.indexOf("\n", funcStart);
        if (funcEnd == -1) funcEnd = content.length();

        String funcName = content.substring(funcStart, funcEnd).trim().toLowerCase();

        int argsIdx = upperContent.indexOf("ARGUMENTS:");
        Map<String, Object> arguments;
        if (argsIdx != -1) {
            int argsStart = argsIdx + "ARGUMENTS:".length();
            String argsPart = content.substring(argsStart).trim();
            int braceStart = argsPart.indexOf("{");
            int braceEnd = argsPart.lastIndexOf("}");
            if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
                String argsJson = argsPart.substring(braceStart, braceEnd + 1);
                arguments = parseJsonToMapSafe(argsJson);
            } else {
                arguments = Map.of();
            }
        } else {
            arguments = Map.of();
        }

        return new FunctionCall(funcName, arguments);
    }

    /**
     * Parse a JSON object string to a Map using Jackson.
     * Returns an empty map on parse failure rather than throwing.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMapSafe(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (IOException e) {
            logger.warn("Failed to parse function arguments JSON: {}", json, e);
            return Map.of();
        }
    }

    private String formatResult(String functionName, String result) {
        return "Function: " + functionName + "\nResult: " + result;
    }

    /**
     * Execute a registered function by name with JSON arguments.
     *
     * @param name          the function name
     * @param argumentsJson JSON string of arguments
     * @return the function result as a string
     */
    @SuppressWarnings("unchecked")
    public String executeFunction(String name, String argumentsJson) {
        YawlFunctionHandler handler = functionHandlers.get(name);
        if (handler == null) {
            return "{\"error\": \"Unknown function: " + name + "\"}";
        }

        try {
            Map<String, Object> args = argumentsJson != null && !argumentsJson.isBlank()
                ? objectMapper.readValue(argumentsJson, Map.class)
                : Map.of();
            return handler.execute(args);
        } catch (Exception e) {
            logger.error("Function execution failed for '{}': {}", name, e.getMessage(), e);
            return "{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}";
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

        return """
            {"status": "started", "workflow_id": "%s", "case_id": "%s"}
            """.formatted(workflowId, caseId).strip();
    }

    private String getWorkflowStatus(String caseId) throws IOException {
        ensureConnection();

        List<WorkItemRecord> workItems =
            interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

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

        return "{\"case_id\": \"" + caseId
               + "\", \"status\": \"running\", \"current_tasks\": " + tasksJson + "}";
    }

    private String completeTask(String caseId, String taskId, String outputData) throws IOException {
        ensureConnection();

        List<WorkItemRecord> workItems =
            interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

        if (workItems == null || workItems.isEmpty()) {
            return "{\"error\": \"No work items found for case: " + caseId + "\"}";
        }

        WorkItemRecord targetItem = workItems.stream()
            .filter(item -> item.getTaskID().equals(taskId))
            .findFirst()
            .orElse(null);

        if (targetItem == null) {
            return "{\"error\": \"Task not found: " + taskId + " in case: " + caseId + "\"}";
        }

        String workItemID = targetItem.getID();
        String dataToSend = outputData != null
            ? wrapDataInXML(outputData)
            : (targetItem.getDataList() != null ? targetItem.getDataList().toString() : null);

        String checkoutResult = interfaceBClient.checkOutWorkItem(workItemID, sessionHandle);
        if (checkoutResult == null || checkoutResult.contains("failure")
                || checkoutResult.contains("error")) {
            return "{\"error\": \"Failed to checkout work item: " + checkoutResult + "\"}";
        }

        String checkinResult = interfaceBClient.checkInWorkItem(workItemID, dataToSend, sessionHandle);
        if (checkinResult == null || !checkinResult.contains("success")) {
            return "{\"error\": \"Failed to complete task: " + checkinResult + "\"}";
        }

        return "{\"status\": \"completed\", \"case_id\": \""
               + caseId + "\", \"task_id\": \"" + taskId + "\"}";
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
        return switch (parts.length) {
            case 3 -> new YSpecificationID(parts[0], parts[1], parts[2]);
            case 1 -> new YSpecificationID(parts[0], "0.1", "0.1");
            default -> throw new IllegalArgumentException(
                "Invalid workflow ID format. Use 'identifier:version:uri' or just 'identifier'. "
                + "Got: " + workflowId);
        };
    }

    private String wrapDataInXML(String data) {
        if (data.trim().startsWith("<")) {
            return data;
        }
        return "<data>%s</data>".formatted(data);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Set<String> getRegisteredFunctions() {
        return Set.copyOf(functionHandlers.keySet());
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

    /**
     * Main method for testing.
     */
    public static void main(String[] args) {
        ZaiFunctionService service = new ZaiFunctionService();

        System.out.println("Registered functions: " + service.getRegisteredFunctions());

        System.out.println("\n=== Testing List Workflows ===");
        String result = service.processWithFunctions("What workflows are available?");
        System.out.println(result);

        System.out.println("\n=== Testing Start Workflow ===");
        result = service.processWithFunctions(
            "Start an OrderProcessing workflow with customer 'Acme Corp'");
        System.out.println(result);

        System.out.println("\n=== Testing Get Status ===");
        result = service.processWithFunctions("Check status of case case-12345");
        System.out.println(result);

        service.disconnect();
    }
}
