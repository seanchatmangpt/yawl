/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.mining.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP client for the Ollama local REST API, used to validate generated YAWL XML.
 *
 * <p>Sends a validation prompt to {@code POST /api/generate} with the configured model
 * (default: {@code qwen2.5-coder}) and parses the response for H-Guard violations.
 *
 * <p>The model response protocol:
 * <ul>
 *   <li>Lines starting with {@code "ISSUE:"} indicate specific violations</li>
 *   <li>A response containing only {@code "VALID"} (no ISSUE lines) indicates a clean spec</li>
 * </ul>
 *
 * <p>Uses {@link HttpClient} from the JDK (Java 11+). No additional dependencies beyond
 * Gson, which is already declared in {@code yawl-ggen/pom.xml}.
 *
 * <p>Designed for the {@link AiValidationLoop}: each call to {@link #validate} produces
 * one {@link ValidationResult} for one iteration of the loop.
 */
public class OllamaValidationClient {

    private static final String GENERATE_PATH = "/api/generate";

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;

    /**
     * Constructs an OllamaValidationClient.
     *
     * @param baseUrl      Ollama base URL, e.g. {@code "http://localhost:11434"};
     *                     must not be null or blank
     * @param model        model name, e.g. {@code "qwen2.5-coder"};
     *                     must not be null or blank
     * @param timeoutSecs  HTTP request timeout in seconds; must be positive
     * @throws IllegalArgumentException if any argument violates its contract
     */
    public OllamaValidationClient(String baseUrl, String model, int timeoutSecs) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be null or blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be null or blank");
        }
        if (timeoutSecs <= 0) {
            throw new IllegalArgumentException("timeoutSecs must be positive, got: " + timeoutSecs);
        }
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSecs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /**
     * Validates the given YAWL XML specification using the configured Ollama model.
     *
     * <p>The prompt instructs the model to check for:
     * <ul>
     *   <li>H-Guard violations: TODO/FIXME markers, empty returns, mock class names</li>
     *   <li>YAWL structural issues: missing inputCondition, outputCondition, or decompositions</li>
     * </ul>
     *
     * @param yawlXml   the generated YAWL XML to validate; must not be null or blank
     * @param iteration the current validation iteration number (1-indexed); for prompt context
     * @return a {@link ValidationResult} indicating pass/fail and any issues identified
     * @throws IOException              if the HTTP call fails (network error, timeout,
     *                                  connection refused)
     * @throws IllegalArgumentException if yawlXml is null or blank
     */
    public ValidationResult validate(String yawlXml, int iteration) throws IOException {
        if (yawlXml == null || yawlXml.isBlank()) {
            throw new IllegalArgumentException("yawlXml must not be null or blank");
        }

        String prompt = buildPrompt(yawlXml, iteration);
        String requestBody = buildRequestBody(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + GENERATE_PATH))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response.body(), iteration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted during Ollama validation", e);
        }
    }

    /**
     * Builds the validation prompt sent to the Ollama model.
     *
     * <p>The prompt instructs the model to output lines prefixed with {@code "ISSUE:"}
     * for each violation found, and {@code "VALID"} if none are found.
     *
     * @param yawlXml   the spec to validate
     * @param iteration the iteration number (included in prompt for context)
     * @return the full prompt string
     */
    String buildPrompt(String yawlXml, int iteration) {
        return """
                You are a YAWL workflow specification validator (iteration %d).

                Validate the following YAWL XML for:
                1. H-Guard violations: lines containing TODO, FIXME, or placeholder comments
                2. H-Guard violations: method names containing "mock", "stub", or "fake"
                3. Structural issues: missing <inputCondition> element
                4. Structural issues: missing <outputCondition> element
                5. Structural issues: tasks without a corresponding <decomposition> element

                For each violation found, output a line starting with "ISSUE:" followed by a
                brief description. If no violations are found, output only "VALID".

                YAWL XML to validate:
                %s
                """.formatted(iteration, yawlXml);
    }

    /**
     * Parses the raw Ollama JSON response into a {@link ValidationResult}.
     *
     * <p>Extracts the {@code "response"} field from the Ollama JSON.
     * Lines starting with {@code "ISSUE:"} become issue entries.
     * If no ISSUE lines are found, the result is valid.
     *
     * @param rawJson   the full JSON response string from Ollama
     * @param iteration the iteration number
     * @return parsed {@link ValidationResult}
     * @throws IOException if the JSON cannot be parsed or lacks a "response" field
     */
    ValidationResult parseResponse(String rawJson, int iteration) throws IOException {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IOException("Empty response from Ollama");
        }

        String responseText;
        try {
            JsonObject jsonObject = JsonParser.parseString(rawJson).getAsJsonObject();
            if (!jsonObject.has("response")) {
                throw new IOException("Ollama response missing 'response' field: " + rawJson);
            }
            responseText = jsonObject.get("response").getAsString();
        } catch (Exception e) {
            throw new IOException("Failed to parse Ollama JSON response: " + e.getMessage(), e);
        }

        List<String> issues = new ArrayList<>();
        for (String line : responseText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("ISSUE:")) {
                issues.add(trimmed.substring("ISSUE:".length()).trim());
            }
        }

        boolean valid = issues.isEmpty();
        return new ValidationResult(valid, issues, rawJson, iteration);
    }

    private String buildRequestBody(String prompt) {
        String escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "{\"model\": \"" + model + "\", \"prompt\": \"" + escapedPrompt
                + "\", \"stream\": false}";
    }
}
