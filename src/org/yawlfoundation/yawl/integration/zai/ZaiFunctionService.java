package org.yawlfoundation.yawl.integration.zai;

import java.io.IOException;
import java.util.*;

/**
 * Z.AI Function Calling Service for YAWL
 *
 * Enables AI models to call YAWL workflow operations through function calling.
 * Direct HTTP client - no external SDK dependencies.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiFunctionService {

    private static final String DEFAULT_MODEL = "GLM-4.7-Flash";

    private final ZaiHttpClient httpClient;
    private final Map<String, YawlFunctionHandler> functionHandlers;
    private boolean initialized;

    /**
     * Interface for YAWL function handlers
     */
    public interface YawlFunctionHandler {
        String execute(Map<String, Object> arguments);
    }

    /**
     * Initialize with API key from environment variable
     */
    public ZaiFunctionService() {
        this(getApiKeyFromEnv());
    }

    /**
     * Initialize with explicit API key
     */
    public ZaiFunctionService(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("ZAI_API_KEY is required");
        }
        this.httpClient = new ZaiHttpClient(apiKey);
        this.functionHandlers = new HashMap<>();
        registerDefaultFunctions();
        this.initialized = true;
    }

    private static String getApiKeyFromEnv() {
        String key = System.getenv("ZAI_API_KEY");
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("ZAI_API_KEY environment variable not set");
        }
        return key;
    }

    /**
     * Register default YAWL workflow functions
     */
    private void registerDefaultFunctions() {
        registerFunction("start_workflow", args -> {
            String workflowId = (String) args.get("workflow_id");
            String inputData = args.get("input_data") != null ? args.get("input_data").toString() : "{}";
            return executeStartWorkflow(workflowId, inputData);
        });

        registerFunction("get_workflow_status", args -> {
            String caseId = (String) args.get("case_id");
            return executeGetStatus(caseId);
        });

        registerFunction("complete_task", args -> {
            String caseId = (String) args.get("case_id");
            String taskId = (String) args.get("task_id");
            String outputData = args.get("output_data") != null ? args.get("output_data").toString() : "{}";
            return executeCompleteTask(caseId, taskId, outputData);
        });

        registerFunction("list_workflows", args -> executeListWorkflows());
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
        return processWithFunctions(userMessage, DEFAULT_MODEL);
    }

    /**
     * Process a natural language request with function calling
     */
    public String processWithFunctions(String userMessage, String model) {
        if (!initialized) {
            throw new IllegalStateException("Service not initialized");
        }

        // First, ask AI which function to call
        String functionPrompt = buildFunctionSelectionPrompt(userMessage);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(mapOf("role", "system", "content", getSystemPrompt()));
        messages.add(mapOf("role", "user", "content", functionPrompt));

        try {
            String response = httpClient.createChatCompletion(model, messages);
            String content = httpClient.extractContent(response);

            // Parse function call from response
            FunctionCall functionCall = parseFunctionCall(content);

            if (functionCall != null) {
                String argsJson = mapToJson(functionCall.arguments);
                String result = executeFunction(functionCall.name, argsJson);
                return formatResult(functionCall.name, result);
            } else {
                // No function detected, return direct response
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

        // Find arguments
        int argsIdx = upperContent.indexOf("ARGUMENTS:");
        String argsJson = "{}";
        if (argsIdx != -1) {
            int argsStart = argsIdx + "ARGUMENTS:".length();
            String argsPart = content.substring(argsStart).trim();
            // Extract JSON object
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

        // Simple JSON parsing
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

    // Default function implementations (connect to actual YAWL engine)

    private String executeStartWorkflow(String workflowId, String inputData) {
        // TODO: Connect to actual YAWL engine via HTTP
        return "{\"status\": \"started\", \"workflow_id\": \"" + workflowId + "\", \"case_id\": \"case-" + System.currentTimeMillis() + "\"}";
    }

    private String executeGetStatus(String caseId) {
        // TODO: Connect to actual YAWL engine via HTTP
        return "{\"case_id\": \"" + caseId + "\", \"status\": \"running\", \"current_tasks\": [\"task1\", \"task2\"]}";
    }

    private String executeCompleteTask(String caseId, String taskId, String outputData) {
        // TODO: Connect to actual YAWL engine via HTTP
        return "{\"status\": \"completed\", \"case_id\": \"" + caseId + "\", \"task_id\": \"" + taskId + "\"}";
    }

    private String executeListWorkflows() {
        // TODO: Connect to actual YAWL engine via HTTP
        return "{\"workflows\": [\"OrderProcessing\", \"InvoiceApproval\", \"DocumentReview\", \"CustomerOnboarding\", \"IncidentManagement\"]}";
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Set<String> getRegisteredFunctions() {
        return functionHandlers.keySet();
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

        System.out.println("\n=== Testing Start Workflow ===");
        String result = service.processWithFunctions("Start an OrderProcessing workflow with customer 'Acme Corp'");
        System.out.println(result);

        System.out.println("\n=== Testing List Workflows ===");
        result = service.processWithFunctions("What workflows are available?");
        System.out.println(result);

        System.out.println("\n=== Testing Get Status ===");
        result = service.processWithFunctions("Check status of case case-12345");
        System.out.println(result);
    }
}
