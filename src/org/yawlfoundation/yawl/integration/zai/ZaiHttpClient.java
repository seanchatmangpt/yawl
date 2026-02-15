package org.yawlfoundation.yawl.integration.zai;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP Client for Z.AI API
 *
 * Direct HTTP client for Z.AI chat completions API.
 * No external SDK dependencies required.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiHttpClient {

    private static final String ZAI_API_BASE = "https://api.z.ai/api/paas/v4";
    private static final String CHAT_ENDPOINT = "/chat/completions";

    private final String apiKey;
    private final String baseUrl;
    private int connectTimeout = 30000;
    private int readTimeout = 60000;

    public ZaiHttpClient(String apiKey) {
        this.apiKey = apiKey;
        this.baseUrl = ZAI_API_BASE;
    }

    public ZaiHttpClient(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
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

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);

        // Send request
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // Read response
        int responseCode = conn.getResponseCode();
        InputStream inputStream = responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream();

        if (inputStream == null) {
            throw new IOException("No response from server (HTTP " + responseCode + ")");
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        if (responseCode >= 400) {
            throw new IOException("API error (HTTP " + responseCode + "): " + response.toString());
        }

        return response.toString();
    }

    /**
     * Build JSON request body for chat completion
     */
    private String buildChatRequestBody(String model, List<Map<String, String>> messages,
                                         double temperature, int maxTokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(escapeJson(model)).append("\",");
        sb.append("\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> msg = messages.get(i);
            if (i > 0) sb.append(",");
            sb.append("{");
            sb.append("\"role\":\"").append(escapeJson(msg.get("role"))).append("\",");
            sb.append("\"content\":\"").append(escapeJson(msg.get("content"))).append("\"");
            sb.append("}");
        }
        sb.append("],");
        sb.append("\"temperature\":").append(temperature).append(",");
        sb.append("\"max_tokens\":").append(maxTokens);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Parse response content from JSON
     */
    public String extractContent(String jsonResponse) {
        // Simple JSON parsing without external dependencies
        String contentKey = "\"content\":\"";
        int contentStart = jsonResponse.indexOf(contentKey);
        if (contentStart == -1) {
            return jsonResponse;
        }
        contentStart += contentKey.length();
        int contentEnd = jsonResponse.indexOf("\"", contentStart);
        if (contentEnd == -1) {
            return jsonResponse;
        }

        String content = jsonResponse.substring(contentStart, contentEnd);
        return unescapeJson(content);
    }

    /**
     * Escape special characters for JSON string.
     * Note: Null input is semantically treated as empty string in JSON context.
     */
    private String escapeJson(String s) {
        String input = (s != null) ? s : "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Unescape JSON string.
     * Note: Null input is semantically treated as empty string in JSON context.
     */
    private String unescapeJson(String s) {
        String input = (s != null) ? s : "";
        return input.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }

    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
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
