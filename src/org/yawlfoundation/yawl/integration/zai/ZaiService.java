package org.yawlfoundation.yawl.integration.zai;

import java.io.IOException;
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
 * IMPORTANT: Z.AI SDK must be available. Fails fast if SDK is not present.
 * Provides AI-powered capabilities for MCP and A2A integrations.
 * Direct HTTP client - no external SDK dependencies.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiService {

    private static final String DEFAULT_MODEL = "glm-4.6";
    private static final String DEFAULT_MODEL_FAST = "glm-5";

    private final ZaiHttpClient httpClient;
    private final List<Map<String, String>> conversationHistory;
    private String systemPrompt;
    private boolean initialized;

    /**
     * Initialize Z.AI service with API key from environment variable
     */
    public ZaiService() {
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("ZAI_API_KEY environment variable is required");
        }
        init(apiKey);
    }

    /**
     * Initialize Z.AI service with explicit API key
     */
    public ZaiService(String apiKey) {
        init(apiKey);
    }

    private void init(String apiKey) {
        this.apiKey = apiKey;
        this.conversationHistory = new ArrayList<>();

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
            throw new IllegalStateException("Z.AI SDK not found in classpath. Add ai.z.openapi dependency.", e);
        } catch (Exception e) {
            throw new IllegalStateException("Z.AI SDK initialization failed: " + e.getMessage(), e);
        }
        this.httpClient = new ZaiHttpClient(apiKey);
        this.conversationHistory = new ArrayList<>();
        this.initialized = true;
    }


    /**
     * Set system prompt for AI context
     */
    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }

    /**
     * Send a chat message and get AI response
     */
    public String chat(String message) {
        return chat(message, DEFAULT_MODEL);
    }

    /**
     * Send a chat message with specific model
     */
    public String chat(String message, String model) {
        if (!initialized) {
            throw new IllegalStateException("Z.AI Service not initialized");
        }

        List<Map<String, String>> messages = new ArrayList<>();

        return chatWithSDK(message, model);
    }

        // Add conversation history
        messages.addAll(conversationHistory);


    /**
     * Analyze workflow context and suggest next action
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
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Check if SDK is available
     * @return true if SDK is available (always true if initialized)
     */
    public boolean testConnection() {
        return httpClient.testConnection();
    }

    /**
     * Get available models
     */
    public static List<String> getAvailableModels() {
        return Arrays.asList(
                "glm-4.6",
                "glm-4.5",
                "glm-5",
                "glm-4-32b-0414-128k",
                "glm-4.5v"
        );
    }

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

        System.out.println("Testing connection...");
        System.out.println("Connection: " + (service.testConnection() ? "OK" : "FAILED"));

        service.setSystemPrompt("You are an intelligent assistant integrated with the YAWL workflow engine.");

        System.out.println("\n=== Testing Chat ===");
        String response = service.chat("Hello! Can you help me with workflow management?");
        System.out.println("Response: " + response);

        System.out.println("\n=== Testing Workflow Decision ===");
        String decision = service.makeWorkflowDecision(
                "Approval Level",
                "{\"amount\": 5000, \"department\": \"IT\", \"urgency\": \"high\"}",
                Arrays.asList("Manager Approval", "Director Approval", "Auto-Approve", "Reject")
        );
        System.out.println("Decision: " + decision);

        System.out.println("\n=== Testing Data Transformation ===");
        String transformed = service.transformData(
                "John Doe, 123 Main St, john@example.com, 555-1234",
                "Convert to JSON format with fields: name, address, email, phone"
        );
        System.out.println("Transformed: " + transformed);
    }
}
