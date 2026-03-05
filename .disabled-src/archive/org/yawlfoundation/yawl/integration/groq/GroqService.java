package org.yawlfoundation.yawl.integration.groq;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Groq Cloud Intelligence Service for YAWL — Java 25 Edition.
 *
 * <p>Provides AI-powered capabilities for MCP and A2A integrations using
 * the Groq Cloud API (OpenAI-compatible), backed by {@code openai/gpt-oss-20b}
 * by default.
 *
 * <ul>
 *   <li>API key read from {@code GROQ_API_KEY} environment variable — fail fast if missing.</li>
 *   <li>Model override via {@code GROQ_MODEL} environment variable.</li>
 *   <li>Virtual threads for concurrent chat operations.</li>
 *   <li>{@link ScopedValue} for thread-safe context propagation.</li>
 *   <li>In-flight concurrency capped by {@code GROQ_MAX_CONCURRENCY} (default: 30)
 *       to respect Groq's free-tier 30 RPM limit.</li>
 * </ul>
 *
 * <h2>Rate limits</h2>
 * <p>Groq free tier: <strong>30 RPM</strong> per model. With typical response latencies
 * of 3–8 seconds, this supports ~5–10 truly concurrent in-flight requests before 429s.
 * Paid plans support up to 14,400 RPM (240 RPS). Use {@code GROQ_MAX_CONCURRENCY}
 * to control the parallel permit count.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GroqService {

    private static final String BASE_URL  = "https://api.groq.com/openai/v1";
    private static final String CHAT_PATH = "/chat/completions";
    private static final String DEFAULT_MODEL = "openai/gpt-oss-20b";

    /**
     * Scoped value carrying the current workflow context (system prompt override).
     * Inherited automatically by forked virtual threads.
     */
    public static final ScopedValue<String> WORKFLOW_SYSTEM_PROMPT =
            ScopedValue.newInstance();

    /**
     * Scoped value carrying the model override for a specific operation.
     */
    public static final ScopedValue<String> MODEL_OVERRIDE =
            ScopedValue.newInstance();

    private final String apiKey;
    private final HttpClient httpClient;
    private final Semaphore concurrencyPermits;
    private final List<ChatMessage> conversationHistory;
    private String systemPrompt;
    private volatile boolean initialized;

    /**
     * Initialize Groq service with API key from {@code GROQ_API_KEY} environment variable.
     * Throws {@link IllegalStateException} immediately if the variable is not set.
     */
    public GroqService() {
        String key = System.getenv("GROQ_API_KEY");
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException(
                "GROQ_API_KEY environment variable is required. " +
                "Obtain your key from https://console.groq.com and set: " +
                "export GROQ_API_KEY=<your-key>");
        }
        this.apiKey = key;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.concurrencyPermits = new Semaphore(readMaxConcurrency());
        this.conversationHistory = new ArrayList<>();
        this.initialized = true;
    }

    /**
     * Initialize Groq service with an explicit API key.
     *
     * @param apiKey the Groq API key (must not be null or blank)
     */
    public GroqService(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "apiKey is required. Set GROQ_API_KEY environment variable.");
        }
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.concurrencyPermits = new Semaphore(readMaxConcurrency());
        this.conversationHistory = new ArrayList<>();
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
     * @param model   the Groq model to use (e.g., "llama-3.3-70b-versatile")
     * @return the assistant's reply
     */
    public String chat(String message, String model) {
        ensureInitialized();
        concurrencyPermits.acquireUninterruptibly();
        try {
            String effectiveSystemPrompt = WORKFLOW_SYSTEM_PROMPT.isBound()
                    ? WORKFLOW_SYSTEM_PROMPT.get()
                    : systemPrompt;

            String requestBody = buildRequestBody(model, effectiveSystemPrompt,
                    conversationHistory, message, 0.7);

            String content = sendRequest(requestBody);
            conversationHistory.add(new ChatMessage("user", message));
            conversationHistory.add(new ChatMessage("assistant", content));
            return content;
        } catch (IOException e) {
            throw new RuntimeException("Chat request failed: " + e.getMessage(), e);
        } finally {
            concurrencyPermits.release();
        }
    }

    /**
     * Execute two independent Groq calls in parallel using virtual threads.
     *
     * <p>Both tasks acquire permits from the shared concurrency semaphore before
     * sending. If either call fails, the other is cancelled.
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
                concurrencyPermits.acquireUninterruptibly();
                try {
                    return sendRequest(buildRequestBody(model, systemPrompt,
                            List.of(), message1, 0.7));
                } finally {
                    concurrencyPermits.release();
                }
            });

            Future<String> future2 = executor.submit(() -> {
                concurrencyPermits.acquireUninterruptibly();
                try {
                    return sendRequest(buildRequestBody(model, systemPrompt,
                            List.of(), message2, 0.7));
                } finally {
                    concurrencyPermits.release();
                }
            });

            try {
                return new String[]{ future1.get(), future2.get() };
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                throw new RuntimeException(
                    "Parallel Groq call failed: " + (cause != null ? cause.getMessage() : "unknown"),
                    cause);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Parallel chat interrupted", e);
        }
    }

    /**
     * Run a chat operation with a scoped workflow context.
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
    public String analyzeWorkflowContext(String workflowId, String currentTask,
                                          String workflowData) {
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
     * @return decision with reasoning; format: "CHOICE: [N] REASONING: [explanation]"
     */
    public String makeWorkflowDecision(String decisionPoint, String inputData,
                                        List<String> options) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Make a workflow decision:\n\n");
        prompt.append("Decision Point: ").append(decisionPoint).append("\n");
        prompt.append("Input Data: ").append(inputData).append("\n");
        prompt.append("Available Options:\n");
        for (int i = 0; i < options.size(); i++) {
            prompt.append("  ").append(i + 1).append(". ").append(options.get(i)).append("\n");
        }
        prompt.append("\nChoose the best option and explain your reasoning. " +
                      "Format: CHOICE: [option number] REASONING: [explanation]");
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
        return chat(prompt);
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
     * Clear the conversation history.
     */
    public void clearHistory() {
        conversationHistory.clear();
    }

    /**
     * Test the connection to the Groq API.
     *
     * @return true if connection succeeds, false on error
     */
    public boolean verifyConnection() {
        try {
            String body = buildRequestBody(defaultModel(), null, List.of(), "Hello", 0.3);
            sendRequest(body);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the service is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the current system prompt.
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * Get the available Groq models (fast inference tier).
     */
    public static List<String> getAvailableModels() {
        return Arrays.asList(
            "openai/gpt-oss-20b",
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant",
            "llama3-70b-8192",
            "llama3-8b-8192",
            "mixtral-8x7b-32768",
            "gemma2-9b-it"
        );
    }

    /**
     * Get the current default model (respects {@code GROQ_MODEL} env override).
     */
    public String getDefaultModel() {
        return defaultModel();
    }

    /**
     * Get the current maximum concurrency (permit count).
     */
    public int getMaxConcurrency() {
        return readMaxConcurrency();
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

    private static String defaultModel() {
        String env = System.getenv("GROQ_MODEL");
        return (env != null && !env.isEmpty()) ? env : DEFAULT_MODEL;
    }

    private static int readMaxConcurrency() {
        String env = System.getenv("GROQ_MAX_CONCURRENCY");
        if (env != null && !env.isBlank()) {
            try {
                int v = Integer.parseInt(env.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 30; // free-tier RPM default
    }

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("GroqService is not initialized");
        }
    }

    private String sendRequest(String requestBody) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + CHAT_PATH))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new IOException(
                    "Groq rate limit exceeded (HTTP 429). Reduce GROQ_MAX_CONCURRENCY " +
                    "or wait before retrying. Free tier: 30 RPM per model.");
            }
            if (response.statusCode() != 200) {
                throw new IOException(
                    "Groq API returned HTTP " + response.statusCode() +
                    ": " + response.body());
            }
            return extractContent(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Groq request interrupted", e);
        }
    }

    private static String extractContent(String rawJson) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
            if (!root.has("choices") || root.getAsJsonArray("choices").isEmpty()) {
                throw new IOException(
                    "Groq response missing or empty 'choices' field: " + rawJson);
            }
            return root.getAsJsonArray("choices")
                       .get(0).getAsJsonObject()
                       .getAsJsonObject("message")
                       .get("content").getAsString();
        } catch (IOException rethrow) {
            throw rethrow;
        } catch (Exception e) {
            throw new IOException("Failed to parse Groq response JSON: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String model, String sysPrompt,
                                     List<ChatMessage> history, String userMessage,
                                     double temperature) {
        StringBuilder messages = new StringBuilder("[");
        if (sysPrompt != null && !sysPrompt.isEmpty()) {
            messages.append("{\"role\":\"system\",\"content\":\"")
                    .append(escapeJson(sysPrompt))
                    .append("\"},");
        }
        for (ChatMessage m : history) {
            messages.append("{\"role\":\"").append(m.role())
                    .append("\",\"content\":\"").append(escapeJson(m.content()))
                    .append("\"},");
        }
        messages.append("{\"role\":\"user\",\"content\":\"")
                .append(escapeJson(userMessage))
                .append("\"}]");

        return String.format(
            "{\"model\":\"%s\",\"messages\":%s,\"temperature\":%.2f,\"stream\":false}",
            model, messages, temperature
        );
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // Simple immutable chat message record used internally
    private record ChatMessage(String role, String content) {}
}
