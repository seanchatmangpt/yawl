package org.yawlfoundation.yawl.integration.zai;

import ai.z.openapi.ZaiClient;
import ai.z.openapi.service.model.*;

import java.util.*;

/**
 * Z.AI Function Calling Service for YAWL
 *
 * Enables AI models to call YAWL workflow operations through function calling.
 * Maps YAWL operations to callable functions that the AI can invoke.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiFunctionService {

    private ZaiClient client;
    private boolean initialized = false;
    private Map<String, YawlFunctionHandler> functionHandlers;

    /**
     * Interface for YAWL function handlers
     */
    public interface YawlFunctionHandler {
        String execute(Map<String, Object> arguments);
    }

    public ZaiFunctionService() {
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey != null && !apiKey.isEmpty()) {
            init(apiKey);
        }
    }

    public ZaiFunctionService(String apiKey) {
        init(apiKey);
    }

    private void init(String apiKey) {
        try {
            this.client = ZaiClient.builder().ofZAI()
                    .apiKey(apiKey)
                    .build();
            this.functionHandlers = new HashMap<>();
            registerDefaultFunctions();
            this.initialized = true;
        } catch (Exception e) {
            System.err.println("Failed to initialize Z.AI Function Service: " + e.getMessage());
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
            return "Error: Z.AI Function Service not initialized";
        }

        try {
            // Build request with tools
            ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                    .model(model)
                    .messages(Collections.singletonList(
                            ChatMessage.builder()
                                    .role(ChatMessageRole.USER.value())
                                    .content(userMessage)
                                    .build()
                    ))
                    .tools(getYawlTools())
                    .toolChoice("auto")
                    .build();

            ChatCompletionResponse response = client.chat().createChatCompletion(request);

            if (response.isSuccess()) {
                ChatMessage assistantMessage = response.getData().getChoices().get(0).getMessage();

                // Check if function calling is needed
                if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                    return handleToolCalls(assistantMessage, userMessage, model);
                } else {
                    // Direct response without function calling
                    Object content = assistantMessage.getContent();
                    return content != null ? content.toString() : "No response";
                }
            } else {
                return "Error: " + response.getMsg();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Handle tool calls from AI
     */
    private String handleToolCalls(ChatMessage assistantMessage, String originalMessage, String model) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder()
                .role(ChatMessageRole.USER.value())
                .content(originalMessage)
                .build());
        messages.add(assistantMessage);

        StringBuilder results = new StringBuilder();

        for (ToolCalls toolCall : assistantMessage.getToolCalls()) {
            String functionName = toolCall.getFunction().getName();
            String arguments = toolCall.getFunction().getArguments();

            results.append("Function: ").append(functionName).append("\n");

            // Execute the function
            String functionResult = executeFunction(functionName, arguments);
            results.append("Result: ").append(functionResult).append("\n\n");

            // Add function result to conversation
            messages.add(ChatMessage.builder()
                    .role(ChatMessageRole.TOOL.value())
                    .content(functionResult)
                    .toolCallId(toolCall.getId())
                    .build());
        }

        // Get final response with function results
        ChatCompletionCreateParams followUpRequest = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(messages)
                .build();

        ChatCompletionResponse followUpResponse = client.chat().createChatCompletion(followUpRequest);

        if (followUpResponse.isSuccess()) {
            Object finalContent = followUpResponse.getData().getChoices().get(0).getMessage().getContent();
            results.append("Summary: ").append(finalContent != null ? finalContent.toString() : "");
        }

        return results.toString();
    }

    /**
     * Execute a registered function
     */
    private String executeFunction(String name, String argumentsJson) {
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

    /**
     * Get YAWL tool definitions for function calling
     */
    private List<ChatTool> getYawlTools() {
        List<ChatTool> tools = new ArrayList<>();

        // Start workflow tool
        Map<String, ChatFunctionParameterProperty> startWfProps = new HashMap<>();
        startWfProps.put("workflow_id", ChatFunctionParameterProperty.builder()
                .type("string").description("ID of the workflow specification to start").build());
        startWfProps.put("input_data", ChatFunctionParameterProperty.builder()
                .type("string").description("JSON string containing initial workflow data").build());

        tools.add(ChatTool.builder()
                .type(ChatToolType.FUNCTION.value())
                .function(ChatFunction.builder()
                        .name("start_workflow")
                        .description("Start a new YAWL workflow instance")
                        .parameters(ChatFunctionParameters.builder()
                                .type("object")
                                .properties(startWfProps)
                                .required(Collections.singletonList("workflow_id"))
                                .build())
                        .build())
                .build());

        // Get status tool
        Map<String, ChatFunctionParameterProperty> statusProps = new HashMap<>();
        statusProps.put("case_id", ChatFunctionParameterProperty.builder()
                .type("string").description("ID of the workflow case to check").build());

        tools.add(ChatTool.builder()
                .type(ChatToolType.FUNCTION.value())
                .function(ChatFunction.builder()
                        .name("get_workflow_status")
                        .description("Get the current status of a running workflow")
                        .parameters(ChatFunctionParameters.builder()
                                .type("object")
                                .properties(statusProps)
                                .required(Collections.singletonList("case_id"))
                                .build())
                        .build())
                .build());

        // Complete task tool
        Map<String, ChatFunctionParameterProperty> completeProps = new HashMap<>();
        completeProps.put("case_id", ChatFunctionParameterProperty.builder()
                .type("string").description("ID of the workflow case").build());
        completeProps.put("task_id", ChatFunctionParameterProperty.builder()
                .type("string").description("ID of the task to complete").build());
        completeProps.put("output_data", ChatFunctionParameterProperty.builder()
                .type("string").description("JSON string containing task output data").build());

        tools.add(ChatTool.builder()
                .type(ChatToolType.FUNCTION.value())
                .function(ChatFunction.builder()
                        .name("complete_task")
                        .description("Complete a task in a running workflow")
                        .parameters(ChatFunctionParameters.builder()
                                .type("object")
                                .properties(completeProps)
                                .required(Arrays.asList("case_id", "task_id"))
                                .build())
                        .build())
                .build());

        // List workflows tool
        tools.add(ChatTool.builder()
                .type(ChatToolType.FUNCTION.value())
                .function(ChatFunction.builder()
                        .name("list_workflows")
                        .description("List all available workflow specifications")
                        .parameters(ChatFunctionParameters.builder()
                                .type("object")
                                .properties(new HashMap<>())
                                .build())
                        .build())
                .build());

        return tools;
    }

    // Default function implementations (to be connected to actual YAWL engine)

    private String executeStartWorkflow(String workflowId, String inputData) {
        // TODO: Connect to actual YAWL engine
        return "{\"status\": \"started\", \"workflow_id\": \"" + workflowId + "\", \"case_id\": \"case-12345\"}";
    }

    private String executeGetStatus(String caseId) {
        // TODO: Connect to actual YAWL engine
        return "{\"case_id\": \"" + caseId + "\", \"status\": \"running\", \"current_tasks\": [\"task1\", \"task2\"]}";
    }

    private String executeCompleteTask(String caseId, String taskId, String outputData) {
        // TODO: Connect to actual YAWL engine
        return "{\"status\": \"completed\", \"case_id\": \"" + caseId + "\", \"task_id\": \"" + taskId + "\"}";
    }

    private String executeListWorkflows() {
        // TODO: Connect to actual YAWL engine
        return "{\"workflows\": [\"OrderProcessing\", \"InvoiceApproval\", \"DocumentReview\"]}";
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        ZaiFunctionService service = new ZaiFunctionService();

        if (!service.isInitialized()) {
            System.err.println("Please set ZAI_API_KEY environment variable");
            System.exit(1);
        }

        // Test function calling
        System.out.println("=== Testing Function Calling ===");
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
    }
}
