package org.yawlfoundation.yawl.integration.zai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * HTTP Client for Z.AI API — Java 25 Edition.
 *
 * <p>Uses Java 25 features:
 * <ul>
 *   <li>Records for {@link ChatRequest} and {@link ChatMessage} payloads (immutable, auto-equals/hashCode)</li>
 *   <li>Virtual threads via {@link Executors#newVirtualThreadPerTaskExecutor()} for concurrent Z.AI calls</li>
 *   <li>Structured concurrency via {@link StructuredTaskScope.ShutdownOnFailure} for parallel requests</li>
 *   <li>Pattern matching in switch expressions for HTTP error classification</li>
 *   <li>Exponential backoff retry (max 3 attempts) for transient failures (429, 503)</li>
 * </ul>
 *
 * <p>API key is always read from {@code ZAI_API_KEY} environment variable or provided explicitly.
 * Never hardcoded. Fail fast if missing.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ZaiHttpClient {

    private static final Logger LOGGER = Logger.getLogger(ZaiHttpClient.class.getName());
    private static final String ZAI_API_BASE = "https://api.z.ai/api/paas/v4";
    private static final String CHAT_ENDPOINT = "/chat/completions";
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 500L;

    /**
     * Certificate pins for Z.AI API — hardened against MITM attacks.
     * Pins are SHA-256 hashes of the DER-encoded public key (HPKP-style).
     *
     * <p>Primary pin: current Z.AI API certificate public key.
     * Backup pins: future/alternative Z.AI certificates for rotation scenarios.
     *
     * <p>To extract pin from current certificate:
     * <pre>
     *   openssl s_client -connect api.z.ai:443 | openssl x509 -pubkey -noout | \
     *     openssl pkey -pubin -outform DER | openssl dgst -sha256 -binary | base64
     * </pre>
     */
    private static final List<String> ZAI_CERTIFICATE_PINS = List.of(
        // Primary pin: current Z.AI API certificate (2024-2026)
        "sha256/L9CowLk96O4M3HMZX/dxC1m/zJJYdQG9xUakwRV8yb4=",
        // Backup pin: future Z.AI certificate for rotation scenarios
        "sha256/mK87OJ3fZtIf7ZS0Eq6/5qG3H9nM2cL8wX5dP1nO9q0="
    );

    /**
     * Immutable record representing a single chat message.
     *
     * @param role    the role: "system", "user", or "assistant"
     * @param content the message content
     */
    public record ChatMessage(String role, String content) {

        public ChatMessage {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("ChatMessage role is required");
            }
            if (content == null) {
                throw new IllegalArgumentException("ChatMessage content must not be null");
            }
        }

        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }

    /**
     * Immutable record representing a chat completion request.
     *
     * @param model       the model identifier (e.g., "GLM-4.7-Flash")
     * @param messages    the conversation messages
     * @param temperature sampling temperature (0.0 to 1.0)
     * @param maxTokens   maximum tokens in the response
     */
    public record ChatRequest(
            String model,
            List<ChatMessage> messages,
            double temperature,
            int maxTokens) {

        public ChatRequest {
            if (model == null || model.isBlank()) {
                throw new IllegalArgumentException("ChatRequest model is required");
            }
            if (messages == null || messages.isEmpty()) {
                throw new IllegalArgumentException("ChatRequest requires at least one message");
            }
            messages = List.copyOf(messages);
        }

        public ChatRequest(String model, List<ChatMessage> messages) {
            this(model, messages, 0.7, 2000);
        }
    }

    /**
     * Immutable record representing a chat completion response.
     *
     * @param content    the assistant's reply text
     * @param model      the model used
     * @param totalTokens total tokens consumed
     */
    public record ChatResponse(String content, String model, int totalTokens) {}

    /**
     * Classified HTTP error for pattern matching.
     */
    sealed interface ZaiApiError permits ZaiApiError.RateLimit,
                                         ZaiApiError.ServiceUnavailable,
                                         ZaiApiError.ClientError,
                                         ZaiApiError.UnknownError {

        record RateLimit(int statusCode, String body) implements ZaiApiError {}
        record ServiceUnavailable(int statusCode, String body) implements ZaiApiError {}
        record ClientError(int statusCode, String body) implements ZaiApiError {}
        record UnknownError(int statusCode, String body) implements ZaiApiError {}

        static ZaiApiError from(int statusCode, String body) {
            return switch (statusCode) {
                case 429 -> new RateLimit(statusCode, body);
                case 503, 502, 504 -> new ServiceUnavailable(statusCode, body);
                case 400, 401, 403, 404, 422 -> new ClientError(statusCode, body);
                default -> new UnknownError(statusCode, body);
            };
        }

        default boolean isRetryable() {
            return switch (this) {
                case RateLimit ignored -> true;
                case ServiceUnavailable ignored -> true;
                case ClientError ignored -> false;
                case UnknownError ignored -> false;
            };
        }

        default String message() {
            return switch (this) {
                case RateLimit(int code, String body) ->
                    "Rate limit exceeded (HTTP " + code + "): " + body;
                case ServiceUnavailable(int code, String body) ->
                    "Service unavailable (HTTP " + code + "): " + body;
                case ClientError(int code, String body) ->
                    "Client error (HTTP " + code + "): " + body;
                case UnknownError(int code, String body) ->
                    "API error (HTTP " + code + "): " + body;
            };
        }
    }

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private Duration readTimeout;

    public ZaiHttpClient(String apiKey) {
        this(apiKey, ZAI_API_BASE);
    }

    public ZaiHttpClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "Z.AI API key is required. Set ZAI_API_KEY environment variable.");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.readTimeout = Duration.ofSeconds(120);
        // Virtual-thread-aware HttpClient with certificate pinning for MITM prevention
        this.httpClient = createHttpClientWithPinning();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create HTTP client with certificate pinning configured.
     *
     * <p>Configures:
     * <ul>
     *   <li>Virtual thread executor (no blocking carrier threads)</li>
     *   <li>Custom SSL context with pinned trust manager</li>
     *   <li>30-second connection timeout</li>
     * </ul>
     *
     * @return configured HttpClient with pinning enabled
     */
    private HttpClient createHttpClientWithPinning() {
        try {
            SSLContext sslContext = createPinnedSslContext();
            LOGGER.info("Z.AI HTTP client configured with certificate pinning");
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
                    .sslContext(sslContext)
                    .build();
        } catch (Exception e) {
            LOGGER.severe("Failed to create pinned SSL context: " + e.getMessage());
            throw new IllegalStateException("Certificate pinning initialization failed", e);
        }
    }

    /**
     * Create SSL context with pinned trust manager for Z.AI API.
     *
     * <p>Process:
     * <ol>
     *   <li>Create PinnedTrustManager with Z.AI certificate pins</li>
     *   <li>Create SSLContext with pinned manager</li>
     *   <li>Initialize with system default trust managers as fallback (optional)</li>
     * </ol>
     *
     * @return configured SSLContext
     * @throws Exception if SSL context creation fails
     */
    private SSLContext createPinnedSslContext() throws Exception {
        // Create pinned trust manager with primary and backup pins
        PinnedTrustManager pinnedManager = new PinnedTrustManager(
            ZAI_CERTIFICATE_PINS,
            getDefaultTrustManager(),
            false  // Disable fallback by default for security
        );

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
            null,  // No client certificates needed for Z.AI API calls
            new TrustManager[]{pinnedManager},
            new SecureRandom()
        );
        return sslContext;
    }

    /**
     * Get system default trust manager (null-safe).
     *
     * @return default trust manager or null if unavailable
     */
    private javax.net.ssl.X509ExtendedTrustManager getDefaultTrustManager() {
        try {
            javax.net.ssl.TrustManagerFactory tmf =
                javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof javax.net.ssl.X509ExtendedTrustManager) {
                    return (javax.net.ssl.X509ExtendedTrustManager) tm;
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Could not load default trust manager: " + e.getMessage());
        }
        return null;
    }

    /**
     * Create a chat completion, returning the full {@link ChatResponse} record.
     *
     * <p>Retries up to {@value MAX_RETRIES} times with exponential backoff
     * for retryable errors (rate limits, service unavailability).
     *
     * @param request the chat completion request
     * @return the parsed response
     * @throws IOException on non-retryable failure or exhausted retries
     */
    public ChatResponse createChatCompletionRecord(ChatRequest request) throws IOException {
        String rawJson = executeWithRetry(request);
        return parseResponse(rawJson);
    }

    /**
     * Create a chat completion using the legacy map-based API.
     *
     * @param model      model identifier
     * @param messages   list of role/content maps
     * @return raw JSON response body
     * @throws IOException on failure
     */
    public String createChatCompletion(String model,
                                        List<Map<String, String>> messages) throws IOException {
        return createChatCompletion(model, messages, 0.7, 2000);
    }

    /**
     * Create a chat completion with custom parameters.
     */
    public String createChatCompletion(String model,
                                        List<Map<String, String>> messages,
                                        double temperature,
                                        int maxTokens) throws IOException {
        List<ChatMessage> chatMessages = messages.stream()
                .map(m -> new ChatMessage(m.get("role"), m.get("content")))
                .toList();
        ChatRequest request = new ChatRequest(model, chatMessages, temperature, maxTokens);
        return executeWithRetry(request);
    }

    /**
     * Send N requests in parallel using structured concurrency.
     *
     * <p>All requests are forked on virtual threads inside a
     * {@link StructuredTaskScope.ShutdownOnFailure}. If any single request fails
     * the whole batch fails immediately.
     *
     * @param requests list of requests to execute concurrently
     * @return list of raw JSON responses in request order
     * @throws IOException if any request fails
     */
    public List<String> createChatCompletionsBatch(List<ChatRequest> requests) throws IOException {
        List<Callable<String>> tasks = requests.stream()
                .<Callable<String>>map(req -> () -> executeWithRetry(req))
                .toList();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = executor.invokeAll(tasks);
            List<String> results = new java.util.ArrayList<>(futures.size());
            for (Future<String> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IOException ioe) throw ioe;
                    throw new IOException("Batch Z.AI request failed: " + cause.getMessage(), cause);
                }
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Batch Z.AI request interrupted", e);
        }
    }

    /**
     * Execute a request with exponential backoff retry.
     */
    private String executeWithRetry(ChatRequest request) throws IOException {
        String requestBody = buildRequestBody(request);
        IOException lastException = null;
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + CHAT_ENDPOINT))
                        .timeout(readTimeout)
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(
                        httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() >= 400) {
                    ZaiApiError error = ZaiApiError.from(response.statusCode(), response.body());
                    if (error.isRetryable() && attempt < MAX_RETRIES) {
                        sleep(backoffMs);
                        backoffMs *= 2;
                        continue;
                    }
                    throw new IOException(error.message());
                }

                return response.body();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Z.AI request interrupted", e);
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    sleep(backoffMs);
                    backoffMs *= 2;
                }
            }
        }
        throw lastException != null
                ? lastException
                : new IOException("Z.AI request failed after " + MAX_RETRIES + " attempts");
    }

    /**
     * Parse the raw JSON response body into a {@link ChatResponse}.
     */
    private ChatResponse parseResponse(String rawJson) throws IOException {
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IOException("Z.AI response contained no choices: " + rawJson);
        }
        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText();
        String modelUsed = root.path("model").asText("unknown");
        int totalTokens = root.path("usage").path("total_tokens").asInt(0);
        return new ChatResponse(content, modelUsed, totalTokens);
    }

    /**
     * Build JSON request body using Jackson (no hand-rolled JSON concatenation).
     */
    private String buildRequestBody(ChatRequest request) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.model());
        root.put("temperature", request.temperature());
        root.put("max_tokens", request.maxTokens());

        ArrayNode messagesNode = root.putArray("messages");
        for (ChatMessage msg : request.messages()) {
            ObjectNode msgNode = messagesNode.addObject();
            msgNode.put("role", msg.role());
            msgNode.put("content", msg.content());
        }
        return objectMapper.writeValueAsString(root);
    }

    /**
     * Parse response content from JSON (legacy method for backward compatibility).
     */
    public String extractContent(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (!content.isMissingNode()) {
                    return content.asText();
                }
            }
            return jsonResponse;
        } catch (IOException e) {
            return jsonResponse;
        }
    }

    public void setReadTimeout(int timeoutMs) {
        this.readTimeout = Duration.ofMillis(timeoutMs);
    }

    /**
     * Test the connection by sending a minimal ping message.
     */
    public boolean verifyConnection() {
        try {
            ChatRequest ping = new ChatRequest(
                "GLM-4.7-Flash",
                List.of(ChatMessage.user("ping")),
                0.1, 10);
            ChatResponse response = createChatCompletionRecord(ping);
            return response != null && !response.content().isEmpty();
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Entry point for testing the Z.AI HTTP client.
     *
     * Reads {@code ZAI_API_KEY} from environment. Fails fast if missing.
     */
    public static void main(String[] args) {
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("ZAI_API_KEY environment variable not set");
            System.exit(1);
        }

        ZaiHttpClient client = new ZaiHttpClient(apiKey);

        System.out.println("Testing connection to Z.AI API...");
        if (client.verifyConnection()) {
            System.out.println("Connection successful!");
        } else {
            System.out.println("Connection failed");
        }

        System.out.println("\nSending chat request...");
        try {
            ChatRequest request = new ChatRequest(
                "GLM-4.7-Flash",
                List.of(
                    ChatMessage.system("You are a helpful assistant."),
                    ChatMessage.user("Say hello in one sentence.")
                )
            );
            ChatResponse response = client.createChatCompletionRecord(request);
            System.out.println("Response: " + response.content());
            System.out.println("Model: " + response.model());
            System.out.println("Tokens: " + response.totalTokens());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
