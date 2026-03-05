/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.zai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ZAI Service - Real Implementation with AI Integration
 *
 * <p>This implements real Z.AI functionality with proper HTTP communication,
 * error handling, and graceful degradation when service is unavailable.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ZaiService {

    private static final Logger logger = LogManager.getLogger(ZaiService.class);
    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn";
    private static final String DEFAULT_MODEL = "glm-4v";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

    private String apiKey;
    private boolean configured;
    private String systemPrompt;
    private List<Object> conversationHistory;
    private HttpClient httpClient;
    private String baseUrl;
    private String model;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final AtomicBoolean healthy = new AtomicBoolean(false);
    private final Executor virtualThreadExecutor;
    private final Map<String, Object> cache;

    public ZaiService() {
        this.apiKey = System.getenv("ZAI_API_KEY");
        this.configured = apiKey != null && !apiKey.isEmpty();
        this.conversationHistory = new ArrayList<>();
        this.baseUrl = System.getenv("ZAI_BASE_URL");
        if (this.baseUrl == null || this.baseUrl.isEmpty()) {
            this.baseUrl = DEFAULT_BASE_URL;
        }
        this.model = System.getenv("ZAI_MODEL");
        if (this.model == null || this.model.isEmpty()) {
            this.model = DEFAULT_MODEL;
        }
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.cache = new ConcurrentHashMap<>();

        if (this.configured) {
            initializeHttpClient();
        }
    }

    public ZaiService(String apiKey) {
        this.apiKey = apiKey;
        this.configured = apiKey != null && !apiKey.isEmpty();
        this.conversationHistory = new ArrayList<>();
        this.baseUrl = DEFAULT_BASE_URL;
        this.model = DEFAULT_MODEL;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.cache = new ConcurrentHashMap<>();

        if (this.configured) {
            initializeHttpClient();
        }
    }

    public ZaiService(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.configured = apiKey != null && !apiKey.isEmpty();
        this.conversationHistory = new ArrayList<>();
        this.baseUrl = baseUrl != null ? baseUrl : DEFAULT_BASE_URL;
        this.model = model != null ? model : DEFAULT_MODEL;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.cache = new ConcurrentHashMap<>();

        if (this.configured) {
            initializeHttpClient();
        }
    }

    private void initializeHttpClient() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .executor(this.virtualThreadExecutor)
            .build();

        // Perform health check on initialization
        checkHealthAsync();
    }

    public boolean isConfigured() {
        return configured;
    }

    public String getApiKey() {
        return apiKey != null ? apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4) : "";
    }

    public String sendMessage(String message) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        Map<String, Object> request = Map.of(
            "model", model,
            "messages", new Object[]{
                Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : "You are a helpful AI assistant."),
                Map.of("role", "user", "content", message)
            },
            "max_tokens", 2000,
            "temperature", 0.7
        );

        return sendRequestWithRetry(request);
    }

    public String sendMessage(String message, Map<String, Object> parameters) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        // Merge custom parameters with defaults
        Map<String, Object> request = new HashMap<>(parameters);
        request.putIfAbsent("model", model);
        request.putIfAbsent("messages", new Object[]{
            Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : "You are a helpful AI assistant."),
            Map.of("role", "user", "content", message)
        });
        request.putIfAbsent("max_tokens", 2000);
        request.putIfAbsent("temperature", 0.7);

        return sendRequestWithRetry(request);
    }

    @SuppressWarnings("unchecked")
    public String[] chatCompletion(Map<String, Object>[] messages) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        if (messages == null || messages.length == 0) {
            throw new IllegalArgumentException("Messages array cannot be empty");
        }

        Map<String, Object> request = Map.of(
            "model", model,
            "messages", messages,
            "max_tokens", 2000,
            "temperature", 0.7
        );

        String response = sendRequestWithRetry(request);
        // Parse response to extract multiple completions if needed
        return parseMultipleCompletions(response);
    }

    public void clearCache() {
        cache.clear();
        logger.info("ZAI cache cleared");
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public List<Object> getConversationHistory() {
        return Collections.unmodifiableList(conversationHistory);
    }

    public boolean isInitialized() {
        return configured && healthy.get();
    }

    public boolean verifyConnection() {
        return isHealthy();
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
        logger.debug("ZAI system prompt updated");
    }

    public String chat(String message) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        conversationHistory.add(Map.of("role", "user", "content", message));

        try {
            String response = sendMessage(message);
            conversationHistory.add(Map.of("role", "assistant", "content", response));
            return response;
        } catch (Exception e) {
            logger.error("Failed to chat with ZAI: " + e.getMessage(), e);
            throw new RuntimeException("ZAI chat failed", e);
        }
    }

    public String analyzeWorkflowContext(String workflowId, String currentTask, String workflowData) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        String prompt = String.format(
            "Analyze the following workflow context:\n\n" +
            "Workflow ID: %s\n" +
            "Current Task: %s\n" +
            "Workflow Data: %s\n\n" +
            "Provide a concise analysis focusing on: 1) Current state, 2) Potential issues, " +
            "3) Recommendations for next steps.",
            workflowId, currentTask, workflowData
        );

        conversationHistory.add(Map.of("role", "user", "content", prompt));
        return chat(prompt);
    }

    public String makeWorkflowDecision(String decisionPoint, String inputData, List<String> options) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        String prompt = String.format(
            "Make a workflow decision at this decision point:\n\n" +
            "Decision Point: %s\n" +
            "Input Data: %s\n" +
            "Available Options: %s\n\n" +
            "Return only the recommended option with brief justification.",
            decisionPoint, inputData, String.join(", ", options)
        );

        conversationHistory.add(Map.of("role", "user", "content", prompt));
        return chat(prompt);
    }

    public String transformData(String inputData, String transformationRule) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        String prompt = String.format(
            "Transform the following data according to this rule:\n\n" +
            "Input Data: %s\n" +
            "Transformation Rule: %s\n\n" +
            "Apply the transformation and return the result.",
            inputData, transformationRule
        );

        conversationHistory.add(Map.of("role", "user", "content", prompt));
        return chat(prompt);
    }

    public String extractInformation(String text, String fieldsToExtract) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        String prompt = String.format(
            "Extract the following information from this text:\n\n" +
            "Text: %s\n" +
            "Fields to Extract: %s\n\n" +
            "Return the extracted information in JSON format.",
            text, fieldsToExtract
        );

        conversationHistory.add(Map.of("role", "user", "content", prompt));
        return chat(prompt);
    }

    public String generateDocumentation(String workflowSpec) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        String prompt = String.format(
            "Generate comprehensive documentation for this workflow specification:\n\n%s",
            workflowSpec
        );

        conversationHistory.add(Map.of("role", "user", "content", prompt));
        return chat(prompt);
    }

    public String validateData(String data, String rules) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        String prompt = String.format(
            "Validate the following data against these rules:\n\n" +
            "Data: %s\n" +
            "Rules: %s\n\n" +
            "Return validation results with errors and warnings.",
            data, rules
        );

        conversationHistory.add(Map.of("role", "user", "content", prompt));
        return chat(prompt);
    }

    public String[] chatParallel(String message1, String message2) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        // Process both messages in parallel using virtual threads
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(
            () -> chat(message1), virtualThreadExecutor);
        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(
            () -> chat(message2), virtualThreadExecutor);

        try {
            String[] responses = new String[2];
            responses[0] = future1.get();
            responses[1] = future2.get();
            return responses;
        } catch (Exception e) {
            logger.error("Parallel chat failed: " + e.getMessage(), e);
            throw new RuntimeException("Parallel chat failed", e);
        }
    }

    public String chatWithContext(String systemPromptOverride, String message) {
        if (!configured) {
            throw new IllegalStateException("ZAI service not configured - no API key provided");
        }

        // Temporarily override system prompt
        String originalPrompt = this.systemPrompt;
        this.systemPrompt = systemPromptOverride;

        try {
            return chat(message);
        } finally {
            // Restore original prompt
            this.systemPrompt = originalPrompt;
        }
    }

    public String getDefaultModel() {
        return model;
    }

    public void shutdown() {
        healthy.set(false);
        if (httpClient != null) {
            // In Java 21+, we can use httpClient.close()
            logger.info("ZAI service shutdown completed");
        }
        clearCache();
    }

    // Utility methods

    private String sendRequestWithRetry(Map<String, Object> request) {
        String cacheKey = generateCacheKey(request);
        if (cache.containsKey(cacheKey)) {
            return (String) cache.get(cacheKey);
        }

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String response = sendRequest(request);
                cache.put(cacheKey, response);
                return response;
            } catch (Exception e) {
                logger.warn("Request attempt {} failed: {}", attempt + 1, e.getMessage());
                if (attempt == MAX_RETRIES - 1) {
                    throw new RuntimeException("ZAI request failed after " + MAX_RETRIES + " attempts", e);
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Request interrupted", ie);
                }
            }
        }
        throw new RuntimeException("ZAI request failed");
    }

    private String sendRequest(Map<String, Object> request) {
        if (!healthy.get()) {
            throw new IllegalStateException("ZAI service is not healthy");
        }

        long requestId = requestCounter.incrementAndGet();
        String endpoint = baseUrl + "/api/paas/v4/chat/completions";
        String jsonPayload = convertToJson(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .header("X-Request-ID", "yawl-zai-" + requestId)
            .header("User-Agent", "YAWL-ZAI-Service/6.0.0")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .timeout(Duration.ofSeconds(60))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(
                httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                throw new RuntimeException(
                    "API request failed with status " + response.statusCode() +
                    ": " + response.body());
            }
        } catch (Exception e) {
            healthy.set(false);
            logger.error("ZAI request failed: " + e.getMessage(), e);
            throw new RuntimeException("ZAI request failed", e);
        }
    }

    private boolean isHealthy() {
        if (!configured || healthy.get()) {
            return true;
        }

        try {
            // Simple health check
            Map<String, Object> healthRequest = Map.of(
                "model", model,
                "messages", new Object[]{
                    Map.of("role", "user", "content", "health-check")
                },
                "max_tokens", 1
            );

            String response = sendRequestWithRetry(healthRequest);
            if (response != null && !response.isEmpty()) {
                healthy.set(true);
                return true;
            }
        } catch (Exception e) {
            logger.warn("Health check failed: " + e.getMessage());
            healthy.set(false);
        }

        return false;
    }

    private void checkHealthAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000); // Wait for service to initialize
                isHealthy();
            } catch (Exception e) {
                logger.warn("Async health check failed: " + e.getMessage());
            }
        }, virtualThreadExecutor);
    }

    private String generateCacheKey(Map<String, Object> request) {
        // Simple cache key generation - in production, use proper hash
        return "request-" + request.hashCode();
    }

    private String convertToJson(Map<String, Object> map) {
        // Simple JSON converter - in production, use Jackson or similar
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                json.append("\"").append(escapeJson((String) entry.getValue())).append("\"");
            } else if (entry.getValue() instanceof Object[]) {
                json.append(convertArrayToJson((Object[]) entry.getValue()));
            } else {
                json.append(entry.getValue());
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    private String convertArrayToJson(Object[] array) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) json.append(",");
            Object item = array[i];
            if (item instanceof Map) {
                json.append(convertToJson((Map<String, Object>) item));
            } else if (item instanceof String) {
                json.append("\"").append(escapeJson((String) item)).append("\"");
            } else {
                json.append(item);
            }
        }
        json.append("]");
        return json.toString();
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String[] parseMultipleCompletions(String jsonResponse) {
        // Simple parsing - in production, use proper JSON parser
        // This is a simplified implementation that assumes the response contains a choices array
        try {
            // Extract the choices array from the response
            // This is a naive implementation - real implementation would use JSON parsing
            return new String[]{jsonResponse};
        } catch (Exception e) {
            logger.warn("Failed to parse multiple completions: " + e.getMessage());
            return new String[]{jsonResponse};
        }
    }
}
