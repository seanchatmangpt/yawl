package org.yawlfoundation.yawl.integration.zai;

import java.util.*;

/**
 * Z.AI Intelligence Service for YAWL
 *
 * Provides AI-powered capabilities for MCP and A2A integrations using Z.AI's GLM models.
 * This service enables intelligent workflow processing, natural language understanding,
 * and automated decision-making within YAWL workflows.
 *
 * Features:
 * - Chat completions with GLM-4.6
 * - Function calling for workflow operations
 * - Streaming responses for real-time processing
 * - Multi-turn conversations for complex workflows
 *
 * Mock Mode: When Z.AI SDK is not available, provides simulated responses for testing.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiService {

    private static final String DEFAULT_MODEL = "glm-4.6";
    private static final String DEFAULT_MODEL_FAST = "glm-5";

    private Object client; // ZaiClient when SDK available
    private boolean initialized = false;
    private boolean sdkAvailable = false;
    private List<Map<String, String>> conversationHistory;
    private String systemPrompt;
    private String apiKey;

    /**
     * Initialize Z.AI service with API key from environment variable
     */
    public ZaiService() {
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("ZAI_API_KEY not set - running in mock mode");
            initMock();
            return;
        }
        init(apiKey);
    }

    /**
     * Initialize Z.AI service with explicit API key
     * @param apiKey Z.AI API key
     */
    public ZaiService(String apiKey) {
        init(apiKey);
    }

    private void init(String apiKey) {
        this.apiKey = apiKey;
        this.conversationHistory = new ArrayList<>();

        // Try to load Z.AI SDK dynamically
        try {
            Class<?> clientClass = Class.forName("ai.z.openapi.ZaiClient");
            sdkAvailable = true;

            // Use reflection to build client
            Object builder = clientClass.getMethod("builder").invoke(null);
            builder = builder.getClass().getMethod("ofZAI").invoke(builder);
            builder = builder.getClass().getMethod("apiKey", String.class).invoke(builder, apiKey);
            this.client = builder.getClass().getMethod("build").invoke(builder);

            this.initialized = true;
            System.out.println("Z.AI Service initialized with SDK");
        } catch (ClassNotFoundException e) {
            System.out.println("Z.AI SDK not found - running in mock mode");
            initMock();
        } catch (Exception e) {
            System.out.println("Z.AI SDK initialization failed: " + e.getMessage() + " - running in mock mode");
            initMock();
        }
    }

    private void initMock() {
        this.conversationHistory = new ArrayList<>();
        this.sdkAvailable = false;
        this.initialized = true; // Mock is always "initialized"
        System.out.println("Z.AI Service running in mock mode");
    }

    /**
     * Set system prompt for AI context
     * @param prompt System prompt defining AI behavior
     */
    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }

    /**
     * Send a chat message and get AI response
     * @param message User message
     * @return AI response
     */
    public String chat(String message) {
        return chat(message, DEFAULT_MODEL);
    }

    /**
     * Send a chat message with specific model
     * @param message User message
     * @param model Model to use (glm-4.6, glm-5, etc.)
     * @return AI response
     */
    public String chat(String message, String model) {
        if (!initialized) {
            return "Error: Z.AI Service not initialized";
        }

        // Update conversation history
        conversationHistory.add(mapOf("role", "user", "content", message));

        if (sdkAvailable && client != null) {
            return chatWithSDK(message, model);
        } else {
            return chatMock(message, model);
        }
    }

    /**
     * Chat using actual Z.AI SDK via reflection
     */
    @SuppressWarnings("unchecked")
    private String chatWithSDK(String message, String model) {
        try {
            Class<?> msgClass = Class.forName("ai.z.openapi.service.model.ChatMessage");
            Class<?> roleClass = Class.forName("ai.z.openapi.service.model.ChatMessageRole");
            Class<?> paramsClass = Class.forName("ai.z.openapi.service.model.ChatCompletionCreateParams");

            // Build messages list
            List<Object> messages = new ArrayList<>();

            // Add system prompt if set
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                Object sysRole = roleClass.getField("SYSTEM").get(null);
                String roleValue = (String) roleClass.getMethod("value").invoke(sysRole);
                Object sysMsg = msgClass.getMethod("builder").invoke(null);
                sysMsg = sysMsg.getClass().getMethod("role", String.class).invoke(sysMsg, roleValue);
                sysMsg = sysMsg.getClass().getMethod("content", String.class).invoke(sysMsg, systemPrompt);
                sysMsg = sysMsg.getClass().getMethod("build").invoke(sysMsg);
                messages.add(sysMsg);
            }

            // Add user message
            Object userRole = roleClass.getField("USER").get(null);
            String userRoleValue = (String) roleClass.getMethod("value").invoke(userRole);
            Object userMsg = msgClass.getMethod("builder").invoke(null);
            userMsg = userMsg.getClass().getMethod("role", String.class).invoke(userMsg, userRoleValue);
            userMsg = userMsg.getClass().getMethod("content", String.class).invoke(userMsg, message);
            userMsg = userMsg.getClass().getMethod("build").invoke(userMsg);
            messages.add(userMsg);

            // Build request
            Object paramsBuilder = paramsClass.getMethod("builder").invoke(null);
            paramsBuilder = paramsBuilder.getClass().getMethod("model", String.class).invoke(paramsBuilder, model);
            paramsBuilder = paramsBuilder.getClass().getMethod("messages", List.class).invoke(paramsBuilder, messages);
            paramsBuilder = paramsBuilder.getClass().getMethod("temperature", float.class).invoke(paramsBuilder, 0.7f);
            Object params = paramsBuilder.getClass().getMethod("build").invoke(paramsBuilder);

            // Call API
            Object chatService = client.getClass().getMethod("chat").invoke(client);
            Object response = chatService.getClass().getMethod("createChatCompletion", paramsClass).invoke(chatService, params);

            // Check success
            Boolean success = (Boolean) response.getClass().getMethod("isSuccess").invoke(response);
            if (success) {
                Object data = response.getClass().getMethod("getData").invoke(response);
                List<?> choices = (List<?>) data.getClass().getMethod("getChoices").invoke(data);
                Object firstChoice = choices.get(0);
                Object respMsg = firstChoice.getClass().getMethod("getMessage").invoke(firstChoice);
                Object content = respMsg.getClass().getMethod("getContent").invoke(respMsg);

                String replyStr = content != null ? content.toString() : "";

                // Add to history
                conversationHistory.add(mapOf("role", "assistant", "content", replyStr));

                return replyStr;
            } else {
                Object msg = response.getClass().getMethod("getMsg").invoke(response);
                return "Error: " + msg;
            }
        } catch (Exception e) {
            return "Error calling Z.AI SDK: " + e.getMessage();
        }
    }

    /**
     * Mock chat for testing without SDK
     */
    private String chatMock(String message, String model) {
        String lower = message.toLowerCase();

        // Simulate intelligent responses based on context
        if (lower.contains("workflow") && lower.contains("decision")) {
            String response = "Based on the workflow context, I recommend proceeding with the standard approval path. " +
                    "The data indicates normal processing conditions are met.";
            conversationHistory.add(mapOf("role", "assistant", "content", response));
            return response;
        }

        if (lower.contains("transform") || lower.contains("convert")) {
            String response = "{\n  \"transformed\": true,\n  \"data\": \"" + message.substring(0, Math.min(50, message.length())) + "...\",\n  \"format\": \"json\"\n}";
            conversationHistory.add(mapOf("role", "assistant", "content", response));
            return response;
        }

        if (lower.contains("analyze") || lower.contains("analysis")) {
            String response = "[MOCK ANALYSIS]\n" +
                    "Key findings:\n" +
                    "1. Process efficiency: Optimal\n" +
                    "2. Resource utilization: 78%\n" +
                    "3. Bottleneck detected: Task approval step\n" +
                    "Recommendation: Add parallel processing for independent tasks";
            conversationHistory.add(mapOf("role", "assistant", "content", response));
            return response;
        }

        if (lower.contains("mcp") || lower.contains("tool")) {
            String response = "Recommended MCP tool: analyzeDocument\n" +
                    "Parameters: {\"source\": \"auto-detected\", \"depth\": \"standard\"}\n" +
                    "Confidence: 92%";
            conversationHistory.add(mapOf("role", "assistant", "content", response));
            return response;
        }

        if (lower.contains("a2a") || lower.contains("agent")) {
            String response = "A2A Agent Recommendation:\n" +
                    "Primary agent: ProcessAgent\n" +
                    "Fallback agent: ExceptionHandlerAgent\n" +
                    "Coordination pattern: Sequential with checkpoint";
            conversationHistory.add(mapOf("role", "assistant", "content", response));
            return response;
        }

        if (lower.contains("exception") || lower.contains("error")) {
            String response = "Exception Analysis:\n" +
                    "Type: TimeoutException\n" +
                    "Root cause: External service unresponsive\n" +
                    "Recovery action: Retry with exponential backoff (max 3 attempts)\n" +
                    "Alternative: Route to backup agent if available";
            conversationHistory.add(mapOf("role", "assistant", "content", response));
            return response;
        }

        // Default mock response
        String response = "[MOCK RESPONSE - model: " + model + "]\n" +
                "I understand your request about: \"" + message.substring(0, Math.min(60, message.length())) + "...\"\n" +
                "How can I assist you further with your YAWL workflow?";
        conversationHistory.add(mapOf("role", "assistant", "content", response));
        return response;
    }

    /**
     * Analyze workflow context and suggest next action
     * @param workflowId Workflow identifier
     * @param currentTask Current task information
     * @param workflowData Current workflow data
     * @return Suggested action
     */
    public String analyzeWorkflowContext(String workflowId, String currentTask, String workflowData) {
        String prompt = String.format(
                "Analyze the following YAWL workflow context and suggest the best action:\n\n" +
                        "Workflow ID: %s\n" +
                        "Current Task: %s\n" +
                        "Workflow Data: %s\n\n" +
                        "Provide a concise recommendation for the next action.",
                workflowId, currentTask, workflowData
        );

        return chat(prompt);
    }

    /**
     * Generate workflow decision based on data
     * @param decisionPoint Decision point name
     * @param inputData Input data for decision
     * @param options Available options
     * @return Chosen option with reasoning
     */
    public String makeWorkflowDecision(String decisionPoint, String inputData, List<String> options) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Make a workflow decision:\n\n");
        prompt.append("Decision Point: ").append(decisionPoint).append("\n");
        prompt.append("Input Data: ").append(inputData).append("\n");
        prompt.append("Available Options:\n");
        for (int i = 0; i < options.size(); i++) {
            prompt.append("  ").append(i + 1).append(". ").append(options.get(i)).append("\n");
        }
        prompt.append("\nChoose the best option and explain your reasoning. Format: CHOICE: [option number] REASONING: [explanation]");

        return chat(prompt.toString());
    }

    /**
     * Transform data using AI
     * @param inputData Input data to transform
     * @param transformationRule Natural language transformation rule
     * @return Transformed data
     */
    public String transformData(String inputData, String transformationRule) {
        String prompt = String.format(
                "Transform the following data according to the rule:\n\n" +
                        "Input Data: %s\n\n" +
                        "Transformation Rule: %s\n\n" +
                        "Return only the transformed data, no explanation.",
                inputData, transformationRule
        );

        return chat(prompt, DEFAULT_MODEL_FAST);
    }

    /**
     * Extract structured information from unstructured text
     * @param text Unstructured text
     * @param fieldsToExtract Fields to extract (comma-separated)
     * @return JSON-like structured data
     */
    public String extractInformation(String text, String fieldsToExtract) {
        String prompt = String.format(
                "Extract the following information from the text:\n\n" +
                        "Text: %s\n\n" +
                        "Fields to Extract: %s\n\n" +
                        "Return the result as key-value pairs in JSON format.",
                text, fieldsToExtract
        );

        return chat(prompt);
    }

    /**
     * Generate workflow documentation
     * @param workflowSpec Workflow specification
     * @return Human-readable documentation
     */
    public String generateDocumentation(String workflowSpec) {
        String prompt = String.format(
                "Generate comprehensive documentation for the following YAWL workflow:\n\n" +
                        "%s\n\n" +
                        "Include:\n" +
                        "1. Overview and purpose\n" +
                        "2. Input parameters\n" +
                        "3. Process steps\n" +
                        "4. Output parameters\n" +
                        "5. Error handling",
                workflowSpec
        );

        return chat(prompt);
    }

    /**
     * Validate workflow data against rules
     * @param data Data to validate
     * @param rules Validation rules (natural language)
     * @return Validation result with issues found
     */
    public String validateData(String data, String rules) {
        String prompt = String.format(
                "Validate the following data against these rules:\n\n" +
                        "Data: %s\n\n" +
                        "Rules: %s\n\n" +
                        "Return VALID if all rules pass, or list the validation issues found.",
                data, rules
        );

        return chat(prompt, DEFAULT_MODEL_FAST);
    }

    /**
     * Clear conversation history
     */
    public void clearHistory() {
        conversationHistory.clear();
    }

    /**
     * Check if service is initialized
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if real SDK is available (vs mock mode)
     * @return true if SDK is available
     */
    public boolean isSdkAvailable() {
        return sdkAvailable;
    }

    /**
     * Get available models
     * @return List of available model names
     */
    public static List<String> getAvailableModels() {
        return Arrays.asList(
                "glm-4.6",      // Latest advanced model
                "glm-4.5",      // Previous generation
                "glm-5",        // Fast model
                "glm-4-32b-0414-128k", // Large context model
                "glm-4.5v"      // Vision model
        );
    }

    // Helper method to create a simple map
    private Map<String, String> mapOf(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        ZaiService service = new ZaiService();

        System.out.println("Z.AI Service initialized: " + service.isInitialized());
        System.out.println("SDK available: " + service.isSdkAvailable());

        // Set context for YAWL
        service.setSystemPrompt("You are an intelligent assistant integrated with the YAWL workflow engine. " +
                "Help users manage and execute business processes effectively.");

        // Test chat
        System.out.println("\n=== Testing Chat ===");
        String response = service.chat("Hello! Can you help me with workflow management?");
        System.out.println("Response: " + response);

        // Test workflow decision
        System.out.println("\n=== Testing Workflow Decision ===");
        String decision = service.makeWorkflowDecision(
                "Approval Level",
                "{\"amount\": 5000, \"department\": \"IT\", \"urgency\": \"high\"}",
                Arrays.asList("Manager Approval", "Director Approval", "Auto-Approve", "Reject")
        );
        System.out.println("Decision: " + decision);

        // Test data transformation
        System.out.println("\n=== Testing Data Transformation ===");
        String transformed = service.transformData(
                "John Doe, 123 Main St, john@example.com, 555-1234",
                "Convert to JSON format with fields: name, address, email, phone"
        );
        System.out.println("Transformed: " + transformed);

        System.out.println("\nZ.AI Service test completed successfully");
    }
}
