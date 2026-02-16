package org.yawlfoundation.yawl.integration.zai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP Client for Z.AI API
 *
 * Modern HTTP client for Z.AI chat completions API using java.net.http.HttpClient
 * and Jackson for JSON processing.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiHttpClient {

    private static final String ZAI_API_BASE = "https://api.z.ai/api/paas/v4";
    private static final String CHAT_ENDPOINT = "/chat/completions";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private Duration connectTimeout = Duration.ofSeconds(30);
    private Duration readTimeout = Duration.ofSeconds(60);

    public ZaiHttpClient(String apiKey) {
        this(apiKey, ZAI_API_BASE);
    }

    public ZaiHttpClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create a chat completion
     * @param model Model to use (e.g., "GLM-4.7-Flash", "glm-4.6", "glm-5")
     * @param messages List of messages with role and content
     * @return Response body as string
     */
    public String createChatCompletion(String model, List<Map<String, String>> messages) throws IOException {
        return createChatCompletion(model, messages, 0.7, 2000);
    }

    /**
     * Create a chat completion with custom parameters
     */
    public String createChatCompletion(String model, List<Map<String, String>> messages,
                                        double temperature, int maxTokens) throws IOException {
        String url = baseUrl + CHAT_ENDPOINT;
        String requestBody = buildChatRequestBody(model, messages, temperature, maxTokens);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new IOException("API error (HTTP " + response.statusCode() + "): " + response.body());
            }

            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    /**
     * Build JSON request body for chat completion using Jackson
     */
    private String buildChatRequestBody(String model, List<Map<String, String>> messages,
                                         double temperature, int maxTokens) throws IOException {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", temperature);
        request.put("max_tokens", maxTokens);

        return objectMapper.writeValueAsString(request);
    }

    /**
     * Parse response content from JSON using Jackson
     */
    public String extractContent(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).path("message");
                JsonNode content = message.path("content");
                if (!content.isMissingNode()) {
                    return content.asText();
                }
            }
            return jsonResponse;
        } catch (IOException e) {
            return jsonResponse;
        }
    }

    public void setConnectTimeout(int timeoutMs) {
        this.connectTimeout = Duration.ofMillis(timeoutMs);
    }

    public void setReadTimeout(int timeoutMs) {
        this.readTimeout = Duration.ofMillis(timeoutMs);
    }

    /**
     * Test the connection
     */
    public boolean verifyConnection() {
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(mapOf("role", "user", "content", "ping"));
            String response = createChatCompletion("GLM-4.7-Flash", messages, 0.1, 10);
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
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
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
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
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(client.mapOf("role", "system", "content", "You are a helpful assistant."));
            messages.add(client.mapOf("role", "user", "content", "Say hello in one sentence."));

            String response = client.createChatCompletion("GLM-4.7-Flash", messages);
            System.out.println("Raw response: " + response);
            System.out.println("\nExtracted content: " + client.extractContent(response));
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
