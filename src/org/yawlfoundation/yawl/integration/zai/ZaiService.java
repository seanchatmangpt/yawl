package org.yawlfoundation.yawl.integration.zai;

import ai.z.openapi.ZaiClient;
import ai.z.openapi.service.model.*;
import ai.z.openapi.core.Constants;

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
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiService {

    private static final String DEFAULT_MODEL = "glm-4.6";
    private static final String DEFAULT_MODEL_FAST = "glm-5";

    private ZaiClient client;
    private boolean initialized = false;
    private List<ChatMessage> conversationHistory;
    private String systemPrompt;

    /**
     * Initialize Z.AI service with API key from environment variable
     */
    public ZaiService() {
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("Warning: ZAI_API_KEY environment variable not set");
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
        try {
            this.client = ZaiClient.builder().ofZAI()
                    .apiKey(apiKey)
                    .build();
            this.conversationHistory = new ArrayList<>();
            this.initialized = true;
            System.out.println("Z.AI Service initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize Z.AI Service: " + e.getMessage());
        }
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

        try {
            List<ChatMessage> messages = new ArrayList<>();

            // Add system prompt if set
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                messages.add(ChatMessage.builder()
                        .role(ChatMessageRole.SYSTEM.value())
                        .content(systemPrompt)
                        .build());
            }

            // Add conversation history
            messages.addAll(conversationHistory);

            // Add user message
            messages.add(ChatMessage.builder()
                    .role(ChatMessageRole.USER.value())
                    .content(message)
                    .build());

            ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                    .model(model)
                    .messages(messages)
                    .temperature(0.7f)
                    .maxTokens(2000)
                    .build();

            ChatCompletionResponse response = client.chat().createChatCompletion(request);

            if (response.isSuccess()) {
                Object reply = response.getData().getChoices().get(0).getMessage().getContent();
                String replyStr = reply != null ? reply.toString() : "";

                // Update conversation history
                conversationHistory.add(ChatMessage.builder()
                        .role(ChatMessageRole.USER.value())
                        .content(message)
                        .build());
                conversationHistory.add(ChatMessage.builder()
                        .role(ChatMessageRole.ASSISTANT.value())
                        .content(replyStr)
                        .build());

                return replyStr;
            } else {
                return "Error: " + response.getMsg();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
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

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        ZaiService service = new ZaiService();

        if (!service.isInitialized()) {
            System.err.println("Please set ZAI_API_KEY environment variable");
            System.exit(1);
        }

        // Set context for YAWL
        service.setSystemPrompt("You are an intelligent assistant integrated with the YAWL workflow engine. " +
                "Help users manage and execute business processes effectively.");

        // Test chat
        System.out.println("=== Testing Chat ===");
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
