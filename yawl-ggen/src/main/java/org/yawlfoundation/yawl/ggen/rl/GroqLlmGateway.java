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
 * {@link LlmGateway} implementation backed by Groq Cloud's OpenAI-compatible API.
 *
 * <p>Calls the chat completions endpoint at
 * {@code https://api.groq.com/openai/v1/chat/completions} using
 * a Bearer token read from the {@code GROQ_API_KEY} environment variable.
 *
 * <p>Request format:
 * <pre>{@code
 * POST /openai/v1/chat/completions
 * Authorization: Bearer <GROQ_API_KEY>
 * Content-Type: application/json
 *
 * {"model":"llama-3.3-70b-versatile",
 *  "messages":[{"role":"user","content":"<prompt>"}],
 *  "temperature":0.70,
 *  "stream":false}
 * }</pre>
 *
 * <p>Response extraction: {@code choices[0].message.content}
 *
 * <p>Rate limits: Groq free tier allows 30 RPM per model ({@code llama-3.3-70b-versatile}).
 * With typical response latencies of 3–8 s, this supports ~5–10 truly concurrent
 * in-flight requests before hitting 429s. Set {@code GROQ_MAX_CONCURRENCY} in the
 * environment to override the default of 30 for parallel executors.
 *
 * <p>Auto-detection: {@link #isAvailable()} returns {@code true} when
 * {@code GROQ_API_KEY} is set in the environment. {@link OllamaCandidateSampler}
 * prefers Groq over Z.AI when both keys are present.
 */
public class GroqLlmGateway implements LlmGateway {

    static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";
    static final String DEFAULT_MODEL    = "llama-3.3-70b-versatile";
    private static final String CHAT_PATH = "/chat/completions";

    /**
     * Default maximum concurrent in-flight requests to the Groq API.
     * Matches the free-tier RPM limit; override via {@code GROQ_MAX_CONCURRENCY}.
     */
    public static final int DEFAULT_MAX_CONCURRENCY = 30;

    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;

    /**
     * Creates a GroqLlmGateway with explicit API key and model.
     *
     * @param apiKey  Groq API key (required, non-blank)
     * @param model   model identifier, e.g. {@code "llama-3.3-70b-versatile"} (required, non-blank)
     * @param timeout HTTP connect and request timeout (required, non-null)
     */
    public GroqLlmGateway(String apiKey, String model, Duration timeout) {
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
     * Factory method: reads {@code GROQ_API_KEY} from environment and creates a
     * gateway using {@link #DEFAULT_MODEL} ({@value #DEFAULT_MODEL}).
     *
     * @param timeout HTTP timeout to use
     * @return configured gateway instance
     * @throws IllegalStateException if {@code GROQ_API_KEY} is not set
     */
    public static GroqLlmGateway fromEnv(Duration timeout) {
        String key = System.getenv("GROQ_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                "GROQ_API_KEY environment variable is not set. " +
                "Obtain your key from https://console.groq.com and run: " +
                "export GROQ_API_KEY=<your-key>");
        }
        return new GroqLlmGateway(key, DEFAULT_MODEL, timeout);
    }

    /**
     * Returns {@code true} if the {@code GROQ_API_KEY} environment variable is set
     * and non-blank. Used by {@link OllamaCandidateSampler} for auto-detection.
     */
    public static boolean isAvailable() {
        String key = System.getenv("GROQ_API_KEY");
        return key != null && !key.isBlank();
    }

    /**
     * Returns the configured maximum concurrent in-flight request count.
     * Reads {@code GROQ_MAX_CONCURRENCY} from the environment; falls back to
     * {@link #DEFAULT_MAX_CONCURRENCY} (30) if unset or unparseable.
     */
    public static int maxConcurrency() {
        String env = System.getenv("GROQ_MAX_CONCURRENCY");
        if (env != null && !env.isBlank()) {
            try {
                int parsed = Integer.parseInt(env.trim());
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_MAX_CONCURRENCY;
    }

    /**
     * Sends the prompt to Groq chat completions and returns the model's text response.
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
            if (response.statusCode() == 429) {
                throw new IOException(
                    "Groq rate limit exceeded (HTTP 429). Reduce concurrency or wait before retrying. " +
                    "Free tier: 30 RPM. Set GROQ_MAX_CONCURRENCY to limit parallel calls.");
            }
            if (response.statusCode() != 200) {
                throw new IOException(
                    "Groq returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return extractContent(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Groq call interrupted", e);
        }
    }

    /**
     * Extracts {@code choices[0].message.content} from a Groq chat completions
     * response JSON. Package-private to allow direct unit testing of parsing logic.
     *
     * @param rawJson the raw JSON response body from Groq
     * @return the text content of the first choice's message
     * @throws IOException if the JSON cannot be parsed or the expected fields are absent
     */
    static String extractContent(String rawJson) throws IOException {
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
