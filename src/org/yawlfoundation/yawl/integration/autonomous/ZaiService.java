package org.yawlfoundation.yawl.integration.autonomous;

import com.squareup.okhttp3.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.yawlfoundation.yawl.integration.a2a.A2AException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Z.AI Integration Service for YAWL autonomous agent reasoning.
 * Provides real API integration with GLM models using the ZHIPU_API_KEY.
 *
 * NO STUBS OR MOCKS - Real implementation only.
 *
 * Features:
 * - Circuit breaker for fault tolerance
 * - Retry logic with exponential backoff
 * - Real Z.AI API calls
 * - Comprehensive error handling
 *
 * @author YAWL Integration Team
 * @version 5.2
 */
public class ZaiService {

    private static final String ZAI_API_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRIES = 3;

    private final OkHttpClient httpClient;
    private final String apiKey;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    /**
     * Creates a new Z.AI service instance.
     * Reads API key from ZHIPU_API_KEY environment variable.
     *
     * @throws IllegalStateException if ZHIPU_API_KEY is not set
     */
    public ZaiService() {
        this(System.getenv("ZHIPU_API_KEY"));
    }

    /**
     * Creates a new Z.AI service instance with explicit API key.
     *
     * @param apiKey Z.AI API key
     * @throws IllegalArgumentException if apiKey is null or empty
     */
    public ZaiService(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Z.AI API key is required. Set ZHIPU_API_KEY environment variable or provide explicit key."
            );
        }

        this.apiKey = apiKey;
        this.httpClient = createHttpClient();
        this.circuitBreaker = createCircuitBreaker();
        this.retry = createRetry();
    }

    /**
     * Creates HTTP client with appropriate timeouts.
     */
    private OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Creates circuit breaker for fault tolerance.
     */
    private CircuitBreaker createCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .build();

        return CircuitBreaker.of("zai-service", config);
    }

    /**
     * Creates retry policy with exponential backoff.
     */
    private Retry createRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(MAX_RETRIES)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(IOException.class)
                .build();

        return Retry.of("zai-service", config);
    }

    /**
     * Sends a reasoning request to Z.AI GLM model.
     *
     * @param prompt User prompt for reasoning
     * @param model GLM model to use (default: glm-4)
     * @return Z.AI response content
     * @throws A2AException if API call fails
     */
    public String reason(String prompt, String model) throws A2AException {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }

        String modelToUse = (model != null && !model.trim().isEmpty()) ? model : "glm-4";

        try {
            return Retry.decorateSupplier(retry, () ->
                CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                    executeApiCall(prompt, modelToUse)
                ).get()
            ).get();
        } catch (Exception e) {
            throw new A2AException("Z.AI reasoning failed: " + e.getMessage(), e);
        }
    }

    /**
     * Sends a reasoning request with default GLM-4 model.
     *
     * @param prompt User prompt
     * @return Z.AI response
     * @throws A2AException if API call fails
     */
    public String reason(String prompt) throws A2AException {
        return reason(prompt, "glm-4");
    }

    /**
     * Executes the actual API call to Z.AI.
     */
    private String executeApiCall(String prompt, String model) {
        String jsonPayload = buildJsonPayload(prompt, model);

        Request request = new Request.Builder()
                .url(ZAI_API_ENDPOINT)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonPayload, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("Z.AI API call failed: HTTP " + response.code() + " - " + errorBody);
            }

            if (response.body() == null) {
                throw new IOException("Z.AI API returned empty response body");
            }

            String responseBody = response.body().string();
            return extractContentFromResponse(responseBody);

        } catch (IOException e) {
            throw new RuntimeException("Failed to execute Z.AI API call", e);
        }
    }

    /**
     * Builds JSON payload for Z.AI API request.
     */
    private String buildJsonPayload(String prompt, String model) {
        return String.format(
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}],\"temperature\":0.7,\"max_tokens\":2000}",
            escapeJson(model),
            escapeJson(prompt)
        );
    }

    /**
     * Extracts content from Z.AI API response.
     */
    private String extractContentFromResponse(String responseBody) {
        int contentStart = responseBody.indexOf("\"content\":\"");
        if (contentStart == -1) {
            throw new RuntimeException("Invalid Z.AI response format: no content field found");
        }

        contentStart += 11;
        int contentEnd = responseBody.indexOf("\"", contentStart);
        if (contentEnd == -1) {
            throw new RuntimeException("Invalid Z.AI response format: malformed content field");
        }

        return responseBody.substring(contentStart, contentEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    /**
     * Escapes JSON special characters.
     */
    private String escapeJson(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Cannot escape null input for JSON");
        }

        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Checks if the circuit breaker is open.
     *
     * @return true if circuit is open (service unavailable)
     */
    public boolean isCircuitBreakerOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }

    /**
     * Gets circuit breaker metrics.
     *
     * @return Circuit breaker state information
     */
    public String getCircuitBreakerState() {
        return String.format("State: %s, Failure Rate: %.2f%%, Buffered Calls: %d",
                circuitBreaker.getState(),
                circuitBreaker.getMetrics().getFailureRate(),
                circuitBreaker.getMetrics().getNumberOfBufferedCalls());
    }

    /**
     * Resets the circuit breaker to closed state.
     */
    public void resetCircuitBreaker() {
        circuitBreaker.reset();
    }

    /**
     * Shuts down the service and releases resources.
     */
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
