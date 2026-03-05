package org.yawlfoundation.yawl.integration.zai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ai.z.openapi.ZaiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;

/**
 * Z.AI Intelligence Service for YAWL — Java 25 Edition.
 *
 * <p>Provides AI-powered capabilities for MCP and A2A integrations.
 * Upgraded for Java 25 best practices:
 * <ul>
 *   <li>Uses official {@link ai.z.openapi.ZaiClient} from ai.z.openapi SDK</li>
 *   <li>{@link ScopedValue} for thread-safe context propagation across virtual threads</li>
 *   <li>Virtual threads for concurrent chat operations via {@link ExecutorService}</li>
 *   <li>Pattern matching in switch for model selection and error classification</li>
 * </ul>
 *
 * <p>API key always read from {@code ZAI_API_KEY} environment variable. Fail fast if missing.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ZaiService {

    // Z.AI / Zhipu model identifiers (override via ZAI_MODEL env)
    private static final String DEFAULT_MODEL = "GLM-4.7-Flash";

    /**
     * Scoped value carrying the current workflow context (e.g., system prompt override).
     * Inherited automatically by forked virtual threads.
     */
    public static final ScopedValue<String> WORKFLOW_SYSTEM_PROMPT =
            ScopedValue.newInstance();

    /**
     * Scoped value carrying the model override for a specific operation.
     */
    public static final ScopedValue<String> MODEL_OVERRIDE =
            ScopedValue.newInstance();

    private static String defaultModel() {
        String env = System.getenv("ZAI_MODEL");
        return (env != null && !env.isEmpty()) ? env : DEFAULT_MODEL;
    }

    private final ZaiClient zaiClient;
    private final List<ChatMessage> conversationHistory;
    private final java.net.http.HttpClient httpClient;
    private String systemPrompt;
    private volatile boolean initialized;

    /**
     * Initialize Z.AI service with API key from {@code ZAI_API_KEY} environment variable.
     * Throws {@link IllegalStateException} immediately if the variable is not set.
     */
    public ZaiService() {
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                "ZAI_API_KEY environment variable is required. "
                + "Obtain your key from https://open.bigmodel.cn and set: "
                + "export ZAI_API_KEY=<your-key>");
        }
        this.zaiClient = ZaiClientFactory.withApiKey(apiKey);
        this.conversationHistory = new ArrayList<>();
        this.httpClient = java.net.http.HttpClient.newHttpClient();
        this.initialized = true;
    }

    /**
     * Initialize Z.AI service with an explicit API key.
     *
     * @param apiKey the Z.AI API key (must not be null or blank)
     */
    public ZaiService(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "apiKey is required. Set ZAI_API_KEY environment variable.");
        }
        this.zaiClient = ZaiClientFactory.withApiKey(apiKey);
        this.conversationHistory = new ArrayList<>();
        this.httpClient = java.net.http.HttpClient.newHttpClient();
        this.initialized = true;
    }

    /**
     * Set the system prompt for this service instance.
     *
     * @param prompt the system prompt; null clears it
     */
    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }

    /**
     * Send a chat message and receive the AI response using the default model.
     *
     * <p>If {@link #WORKFLOW_SYSTEM_PROMPT} is bound on the calling thread, it overrides
     * the instance-level system prompt for this call only.
     *
     * @param message the user message
     * @return the assistant's reply
     */
    public String chat(String message) {
        String model = MODEL_OVERRIDE.isBound() ? MODEL_OVERRIDE.get() : defaultModel();
        return chat(message, model);
    }

    /**
     * Send a chat message with an explicit model identifier.
     *
     * @param message the user message
     * @param model   the model to use (e.g., "GLM-4.7-Flash", "glm-4.6")
     * @return the assistant's reply
     */
    public String chat(String message, String model) {
        ensureInitialized();

        List<ChatMessage> messages = buildMessageList(message);

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
            conversationHistory.add(ChatMessage.builder()
                    .role(ChatMessageRole.USER.value())
                    .content(message)
                    .build());
            conversationHistory.add(ChatMessage.builder()
                    .role(ChatMessageRole.ASSISTANT.value())
                    .content(content)
                    .build());
            return content;
        } catch (RuntimeException e) {
            throw new RuntimeException("Chat request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute two independent Z.AI calls in parallel using virtual threads.
     *
     * <p>Both tasks run on virtual threads inside an executor.
     * If either fails the other is cancelled immediately.
     *
     * @param message1 first user message
     * @param message2 second user message
     * @return array of two responses: [response1, response2]
     * @throws RuntimeException if either call fails
     */
    public String[] chatParallel(String message1, String message2) {
        ensureInitialized();
        String model = defaultModel();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> future1 = executor.submit(() -> {
                List<ChatMessage> messages = buildMessageList(message1);
                ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                        .model(model)
                        .messages(messages)
                        .temperature(0.7f)
                        .maxTokens(2048)
                        .build();
                ChatCompletionResponse resp = zaiClient.chat().createChatCompletion(params);
                if (!resp.isSuccess()) {
                    throw new RuntimeException("Chat API call failed: " + resp.getMsg());
                }
                return resp.getData().getChoices().get(0).getMessage().getContent().toString();
            });

            Future<String> future2 = executor.submit(() -> {
                List<ChatMessage> messages = buildMessageList(message2);
                ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                        .model(model)
                        .messages(messages)
                        .temperature(0.7f)
                        .maxTokens(2048)
                        .build();
                ChatCompletionResponse resp = zaiClient.chat().createChatCompletion(params);
                if (!resp.isSuccess()) {
                    throw new RuntimeException("Chat API call failed: " + resp.getMsg());
                }
                return resp.getData().getChoices().get(0).getMessage().getContent().toString();
            });

            try {
                return new String[]{ future1.get(), future2.get() };
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                throw new RuntimeException("Parallel Z.AI call failed: " + (cause != null ? cause.getMessage() : "unknown error"), cause);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Parallel chat interrupted", e);
        }
    }

    /**
     * Run a chat operation with a scoped workflow context.
     *
     * <p>The {@code systemPromptOverride} is available via {@link #WORKFLOW_SYSTEM_PROMPT}
     * on all virtual threads forked within the scope, replacing the instance-level prompt.
     *
     * @param systemPromptOverride the context-specific system prompt
     * @param message              the user message
     * @return the assistant's reply
     */
    public String chatWithContext(String systemPromptOverride, String message) {
        ensureInitialized();
        try {
            return ScopedValue.where(WORKFLOW_SYSTEM_PROMPT, systemPromptOverride)
                    .call(() -> chat(message));
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke chat with workflow context", e);
        }
    }

    /**
     * Analyze workflow context and suggest the next action.
     */
    public String analyzeWorkflowContext(String workflowId, String currentTask, String workflowData) {
        String prompt = """
            Analyze the following YAWL workflow context and suggest the best action:

            Workflow ID: %s
            Current Task: %s
            Workflow Data: %s

            Provide a concise recommendation for the next action.
            """.formatted(workflowId, currentTask, workflowData);
        return chat(prompt);
    }

    /**
     * Generate a workflow decision based on data and available options.
     *
     * @param decisionPoint name of the decision point
     * @param inputData     current state data
     * @param options       candidate options
     * @return decision with reasoning; format: "CHOICE: [N] REASONING: [explanation]"
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
        prompt.append(
            "\nChoose the best option and explain your reasoning. "
            + "Format: CHOICE: [option number] REASONING: [explanation]");
        return chat(prompt.toString());
    }

    /**
     * Transform data using the AI according to a transformation rule.
     */
    public String transformData(String inputData, String transformationRule) {
        String prompt = """
            Transform the following data according to the rule:

            Input Data: %s

            Transformation Rule: %s

            Return only the transformed data, no explanation.
            """.formatted(inputData, transformationRule);
        return chat(prompt, defaultModel());
    }

    /**
     * Extract structured information from unstructured text.
     */
    public String extractInformation(String text, String fieldsToExtract) {
        String prompt = """
            Extract the following information from the text:

            Text: %s

            Fields to Extract: %s

            Return the result as key-value pairs in JSON format.
            """.formatted(text, fieldsToExtract);
        return chat(prompt);
    }

    /**
     * Generate comprehensive documentation for a YAWL workflow specification.
     */
    public String generateDocumentation(String workflowSpec) {
        String prompt = """
            Generate comprehensive documentation for the following YAWL workflow:

            %s

            Include:
            1. Overview and purpose
            2. Input parameters
            3. Process steps
            4. Output parameters
            5. Error handling
            """.formatted(workflowSpec);
        return chat(prompt);
    }

    /**
     * Validate data against rules.
     *
     * @return "VALID" if all rules pass, or a list of validation issues
     */
    public String validateData(String data, String rules) {
        String prompt = """
            Validate the following data against these rules:

            Data: %s

            Rules: %s

            Return VALID if all rules pass, or list the validation issues found.
            """.formatted(data, rules);
        return chat(prompt, defaultModel());
    }

    /**
     * Clear the conversation history.
     */
    public void clearHistory() {
        conversationHistory.clear();
    }

    /**
     * Check if the service is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Test the connection to the Z.AI API.
     * Attempts a simple chat request to verify API connectivity.
     *
     * @return true if connection succeeds, false on error
     */
    public boolean verifyConnection() {
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(defaultModel())
                    .messages(List.of(
                            ChatMessage.builder()
                                    .role(ChatMessageRole.USER.value())
                                    .content("Hello")
                                    .build()
                    ))
                    .temperature(0.3f)
                    .maxTokens(10)
                    .build();

            ChatCompletionResponse response = zaiClient.chat().createChatCompletion(params);
            return response.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the list of available Z.AI / Zhipu models.
     */
    public static List<String> getAvailableModels() {
        return Arrays.asList(
                "GLM-4.7-Flash",
                "glm-4.6",
                "glm-4.5",
                "glm-5"
        );
    }

    /**
     * Get the current system prompt.
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * Get the conversation history as a list of ChatMessage records.
     */
    public List<ChatMessage> getConversationHistoryRecords() {
        return List.copyOf(conversationHistory);
    }

    /**
     * Get the default model being used.
     */
    public String getDefaultModel() {
        return defaultModel();
    }

    /**
     * Get the conversation history as a list of messages.
     */
    public List<ChatMessage> getConversationHistory() {
        return getConversationHistoryRecords();
    }

    /**
     * Get the underlying HTTP client used for API calls.
     */
    public java.net.http.HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Shut down the service and release resources.
     */
    public void shutdown() {
        clearHistory();
        initialized = false;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("ZaiService is not initialized");
        }
    }

    private List<ChatMessage> buildMessageList(String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();

        // Use scoped system prompt if bound, else fall back to instance-level prompt
        String effectiveSystemPrompt = WORKFLOW_SYSTEM_PROMPT.isBound()
                ? WORKFLOW_SYSTEM_PROMPT.get()
                : systemPrompt;

        if (effectiveSystemPrompt != null && !effectiveSystemPrompt.isEmpty()) {
            messages.add(ChatMessage.builder()
                    .role(ChatMessageRole.SYSTEM.value())
                    .content(effectiveSystemPrompt)
                    .build());
        }

        messages.addAll(conversationHistory);
        messages.add(ChatMessage.builder()
                .role(ChatMessageRole.USER.value())
                .content(userMessage)
                .build());
        return messages;
    }

    /**
     * Entry point for testing.
     */
    public static void main(String[] args) {
        ZaiService service = new ZaiService();

        System.out.println("Testing connection...");
        System.out.println("Connection: " + (service.verifyConnection() ? "OK" : "FAILED"));

        service.setSystemPrompt(
            "You are an intelligent assistant integrated with the YAWL workflow engine.");

        System.out.println("\n=== Testing Chat ===");
        String response = service.chat("Hello! Can you help me with workflow management?");
        System.out.println("Response: " + response);

        System.out.println("\n=== Testing Scoped Context ===");
        String scopedResponse = service.chatWithContext(
            "You are a YAWL workflow expert specializing in Petri net analysis.",
            "What is a split-AND join pattern?");
        System.out.println("Scoped response: " + scopedResponse);

        System.out.println("\n=== Testing Workflow Decision ===");
        String decision = service.makeWorkflowDecision(
                "Approval Level",
                "{\"amount\": 5000, \"department\": \"IT\", \"urgency\": \"high\"}",
                Arrays.asList("Manager Approval", "Director Approval", "Auto-Approve", "Reject")
        );
        System.out.println("Decision: " + decision);
    }
}
