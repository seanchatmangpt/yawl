/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * {@link LlmGateway} implementation backed by Z.AI's GLM-4.7-Flash model.
 *
 * <p>Calls the OpenAI-compatible chat completions endpoint at
 * {@code https://api.z.ai/api/coding/paas/v4/chat/completions} using
 * a Bearer token read from the {@code ZAI_API_KEY} environment variable.
 *
 * <p>Request format:
 * <pre>{@code
 * POST /api/coding/paas/v4/chat/completions
 * Authorization: Bearer <ZAI_API_KEY>
 * Content-Type: application/json
 *
 * {"model":"glm-4.7-flash",
 *  "messages":[{"role":"user","content":"<prompt>"}],
 *  "temperature":0.70,
 *  "stream":false}
 * }</pre>
 *
 * <p>Response extraction: {@code choices[0].message.content}
 *
 * <p>Auto-detection: {@link #isAvailable()} returns {@code true} when
 * {@code ZAI_API_KEY} is set in the environment, enabling
 * {@link OllamaCandidateSampler} to switch to this gateway automatically.
 */
public class ZaiLlmGateway implements LlmGateway {

    static final String DEFAULT_BASE_URL = "https://api.z.ai/api/coding/paas/v4";
    static final String DEFAULT_MODEL    = "glm-4.7-flash";
    private static final String CHAT_PATH = "/chat/completions";

    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;

    /**
     * Creates a ZaiLlmGateway with explicit API key and model.
     *
     * @param apiKey  Z.AI API key (required, non-blank)
     * @param model   model identifier, e.g. {@code "glm-4.7-flash"} (required, non-blank)
     * @param timeout HTTP connect and request timeout (required, non-null)
     */
    public ZaiLlmGateway(String apiKey, String model, Duration timeout) {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalArgumentException("apiKey must not be blank");
        if (model == null || model.isBlank())
            throw new IllegalArgumentException("model must not be blank");
        Objects.requireNonNull(timeout, "timeout");
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    /**
     * Factory method: reads {@code ZAI_API_KEY} from environment and creates a
     * gateway using {@link #DEFAULT_MODEL} ({@value #DEFAULT_MODEL}).
     *
     * @param timeout HTTP timeout to use
     * @return configured gateway instance
     * @throws IllegalStateException if {@code ZAI_API_KEY} is not set
     */
    public static ZaiLlmGateway fromEnv(Duration timeout) {
        String key = System.getenv("ZAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                "ZAI_API_KEY environment variable is not set. " +
                "Obtain your key from https://api.z.ai and run: " +
                "export ZAI_API_KEY=<your-key>");
        }
        return new ZaiLlmGateway(key, DEFAULT_MODEL, timeout);
    }

    /**
     * Returns {@code true} if the {@code ZAI_API_KEY} environment variable is set
     * and non-blank. Used by {@link OllamaCandidateSampler} for auto-detection.
     */
    public static boolean isAvailable() {
        String key = System.getenv("ZAI_API_KEY");
        return key != null && !key.isBlank();
    }

    /**
     * Sends the prompt to Z.AI chat completions and returns the model's text response.
     *
     * @param prompt      fully-assembled generation or correction prompt
     * @param temperature sampling temperature in [0.0, 2.0]
     * @return the content of {@code choices[0].message.content}
     * @throws IOException if the HTTP call fails, returns a non-200 status, or response
     *                     cannot be parsed
     */
    @Override
    public String send(String prompt, double temperature) throws IOException {
        String requestBody = buildRequestBody(prompt, temperature);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(DEFAULT_BASE_URL + CHAT_PATH))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException(
                    "Z.AI returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return extractContent(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Z.AI call interrupted", e);
        }
    }

    /**
     * Extracts {@code choices[0].message.content} from a Z.AI chat completions
     * response JSON. Package-private to allow direct unit testing of parsing logic.
     *
     * @param rawJson the raw JSON response body from Z.AI
     * @return the text content of the first choice's message
     * @throws IOException if the JSON cannot be parsed or the expected fields are absent
     */
    static String extractContent(String rawJson) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(rawJson).getAsJsonObject();
            if (!root.has("choices") || root.getAsJsonArray("choices").isEmpty()) {
                throw new IOException(
                    "Z.AI response missing or empty 'choices' field: " + rawJson);
            }
            return root.getAsJsonArray("choices")
                       .get(0).getAsJsonObject()
                       .getAsJsonObject("message")
                       .get("content").getAsString();
        } catch (IOException rethrow) {
            throw rethrow;
        } catch (Exception e) {
            throw new IOException("Failed to parse Z.AI response JSON: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String prompt, double temperature) {
        String escaped = prompt
            .replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return String.format(
            "{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]," +
            "\"temperature\":%.2f,\"stream\":false}",
            model, escaped, temperature
        );
    }
}
