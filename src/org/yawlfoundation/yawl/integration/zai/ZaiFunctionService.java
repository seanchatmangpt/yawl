package org.yawlfoundation.yawl.integration.zai;

import java.util.*;

/**
 * Z.AI Function Calling Service for YAWL
 *
 * Enables AI models to call YAWL workflow operations through function calling.
 * Maps YAWL operations to callable functions that the AI can invoke.
 *
 * IMPORTANT: Z.AI SDK must be available. Fails fast if SDK is not present.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiFunctionService {

    private Object client; // ZaiClient when SDK available
    private boolean initialized = false;
    private boolean sdkAvailable = false;
    private Map<String, YawlFunctionHandler> functionHandlers;

    /**
     * Interface for YAWL function handlers
     */
    public interface YawlFunctionHandler {
        String execute(Map<String, Object> arguments);
    }

    public ZaiFunctionService() {
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("ZAI_API_KEY environment variable is required");
        }
        init(apiKey);
    }

    public ZaiFunctionService(String apiKey) {
        init(apiKey);
    }

    private void init(String apiKey) {
        this.functionHandlers = new HashMap<>();

        try {
            Class<?> clientClass = Class.forName("ai.z.openapi.ZaiClient");
            sdkAvailable = true;

            // Use reflection to build client
            Object builder = clientClass.getMethod("builder").invoke(null);
            builder = builder.getClass().getMethod("ofZAI").invoke(builder);
            builder = builder.getClass().getMethod("apiKey", String.class).invoke(builder, apiKey);
            this.client = builder.getClass().getMethod("build").invoke(builder);

            registerDefaultFunctions();
            this.initialized = true;
            System.out.println("Z.AI Function Service initialized with SDK");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Z.AI SDK not found in classpath. Add ai.z.openapi dependency.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Z.AI SDK initialization failed: " + e.getMessage(), e);
        }
    }


    /**
     * Register default YAWL workflow functions
     */
    private void registerDefaultFunctions() {
        // Start workflow function
        registerFunction("start_workflow", args -> {
            String workflowId = (String) args.get("workflow_id");
            String inputData = args.get("input_data") != null ? args.get("input_data").toString() : "{}";
            return executeStartWorkflow(workflowId, inputData);
        });

        // Get workflow status function
        registerFunction("get_workflow_status", args -> {
            String caseId = (String) args.get("case_id");
            return executeGetStatus(caseId);
        });

        // Complete task function
        registerFunction("complete_task", args -> {
            String caseId = (String) args.get("case_id");
            String taskId = (String) args.get("task_id");
            String outputData = args.get("output_data") != null ? args.get("output_data").toString() : "{}";
            return executeCompleteTask(caseId, taskId, outputData);
        });

        // List workflows function
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
     * @param userMessage User's natural language request
     * @return Response with function call results
     */
    public String processWithFunctions(String userMessage) {
        return processWithFunctions(userMessage, "glm-4.6");
    }

    /**
     * Process a natural language request with function calling
     * @param userMessage User's natural language request
     * @param model Model to use
     * @return Response with function call results
     */
    public String processWithFunctions(String userMessage, String model) {
        if (!initialized) {
            throw new IllegalStateException("Z.AI Function Service not initialized");
        }

        return processWithSDK(userMessage, model);
    }

    /**
     * Process with actual Z.AI SDK via reflection
     */
    @SuppressWarnings("unchecked")
    private String processWithSDK(String userMessage, String model) {
        try {
            Class<?> msgClass = Class.forName("ai.z.openapi.service.model.ChatMessage");
            Class<?> roleClass = Class.forName("ai.z.openapi.service.model.ChatMessageRole");
            Class<?> paramsClass = Class.forName("ai.z.openapi.service.model.ChatCompletionCreateParams");
            Class<?> toolClass = Class.forName("ai.z.openapi.service.model.ChatTool");

            // Build user message
            Object userRole = roleClass.getField("USER").get(null);
            String userRoleValue = (String) roleClass.getMethod("value").invoke(userRole);
            Object userMsg = msgClass.getMethod("builder").invoke(null);
            userMsg = userMsg.getClass().getMethod("role", String.class).invoke(userMsg, userRoleValue);
            userMsg = userMsg.getClass().getMethod("content", String.class).invoke(userMsg, userMessage);
            userMsg = userMsg.getClass().getMethod("build").invoke(userMsg);

            List<Object> messages = new ArrayList<>();
            messages.add(userMsg);

            // Build tools
            List<Object> tools = buildSDKTools(toolClass);

            // Build request
            Object paramsBuilder = paramsClass.getMethod("builder").invoke(null);
            paramsBuilder = paramsBuilder.getClass().getMethod("model", String.class).invoke(paramsBuilder, model);
            paramsBuilder = paramsBuilder.getClass().getMethod("messages", List.class).invoke(paramsBuilder, messages);
            paramsBuilder = paramsBuilder.getClass().getMethod("tools", List.class).invoke(paramsBuilder, tools);
            paramsBuilder = paramsBuilder.getClass().getMethod("toolChoice", String.class).invoke(paramsBuilder, "auto");
            Object params = paramsBuilder.getClass().getMethod("build").invoke(paramsBuilder);

            // Call API
            Object chatService = client.getClass().getMethod("chat").invoke(client);
            Object response = chatService.getClass().getMethod("createChatCompletion", paramsClass).invoke(chatService, params);

            Boolean success = (Boolean) response.getClass().getMethod("isSuccess").invoke(response);
            if (success) {
                Object data = response.getClass().getMethod("getData").invoke(response);
                List<?> choices = (List<?>) data.getClass().getMethod("getChoices").invoke(data);
                Object firstChoice = choices.get(0);
                Object respMsg = firstChoice.getClass().getMethod("getMessage").invoke(firstChoice);

                // Check for tool calls
                Object toolCalls = respMsg.getClass().getMethod("getToolCalls").invoke(respMsg);
                if (toolCalls != null && !((List<?>) toolCalls).isEmpty()) {
                    return handleSDKToolCalls(toolCalls, msgClass, roleClass, paramsClass, model, userMessage);
                } else {
                    Object content = respMsg.getClass().getMethod("getContent").invoke(respMsg);
                    return content != null ? content.toString() : "No response";
                }
            } else {
                Object msg = response.getClass().getMethod("getMsg").invoke(response);
                return "Error: " + msg;
            }
        } catch (Exception e) {
            return "Error calling Z.AI SDK: " + e.getMessage();
        }
    }

    /**
     * Build tool definitions for SDK
     */
    private List<Object> buildSDKTools(Class<?> toolClass) throws Exception {
        List<Object> tools = new ArrayList<>();

        // For each registered function, create a tool definition
        for (String funcName : functionHandlers.keySet()) {
            // Create function tool using reflection
            // This is simplified - in production, build full parameter schemas
            Object tool = toolClass.getMethod("builder").invoke(null);
            // ... build tool with function definition
            tools.add(tool);
        }

        return tools;
    }

    /**
     * Handle SDK tool calls
     */
    private String handleSDKToolCalls(Object toolCalls, Class<?> msgClass, Class<?> roleClass,
                                       Class<?> paramsClass, String model, String originalMessage) throws Exception {
        StringBuilder results = new StringBuilder();

        for (Object toolCall : (List<?>) toolCalls) {
            Object func = toolCall.getClass().getMethod("getFunction").invoke(toolCall);
            String funcName = (String) func.getClass().getMethod("getName").invoke(func);
            String arguments = (String) func.getClass().getMethod("getArguments").invoke(func);

            results.append("Function: ").append(funcName).append("\n");

            String functionResult = executeFunction(funcName, arguments);
            results.append("Result: ").append(functionResult).append("\n\n");
        }

        return results.toString();
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
            Map<String, Object> args = parseArguments(argumentsJson);
            return handler.execute(args);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Parse JSON arguments to Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isEmpty()) {
            return result;
        }

        // Simple JSON parsing (in production, use Jackson or Gson)
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");
                    result.put(key, value);
                }
            }
        }
        return result;
    }

    private String executeStartWorkflow(String workflowId, String inputData) {
        throw new UnsupportedOperationException(
                "YAWL engine integration required. Connect to YEngine via Interface B to start workflows."
        );
    }

    private String executeGetStatus(String caseId) {
        throw new UnsupportedOperationException(
                "YAWL engine integration required. Connect to YEngine via Interface B to get workflow status."
        );
    }

    private String executeCompleteTask(String caseId, String taskId, String outputData) {
        throw new UnsupportedOperationException(
                "YAWL engine integration required. Connect to YEngine via Interface B to complete tasks."
        );
    }

    private String executeListWorkflows() {
        throw new UnsupportedOperationException(
                "YAWL engine integration required. Connect to YEngine via Interface A to list workflows."
        );
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isSdkAvailable() {
        return sdkAvailable;
    }

    /**
     * Get list of registered functions
     */
    public Set<String> getRegisteredFunctions() {
        return functionHandlers.keySet();
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        ZaiFunctionService service = new ZaiFunctionService();

        System.out.println("Z.AI Function Service initialized: " + service.isInitialized());
        System.out.println("SDK available: " + service.isSdkAvailable());
        System.out.println("Registered functions: " + service.getRegisteredFunctions());

        // Test function calling
        System.out.println("\n=== Testing Start Workflow ===");
        String result = service.processWithFunctions(
                "Start an OrderProcessing workflow with customer 'Acme Corp'"
        );
        System.out.println(result);

        System.out.println("\n=== Testing List Workflows ===");
        result = service.processWithFunctions("What workflows are available?");
        System.out.println(result);

        System.out.println("\n=== Testing Get Status ===");
        result = service.processWithFunctions("Check status of case case-12345");
        System.out.println(result);

        System.out.println("\n=== Testing Complete Task ===");
        result = service.processWithFunctions("Complete the review task");
        System.out.println(result);
    }
}
