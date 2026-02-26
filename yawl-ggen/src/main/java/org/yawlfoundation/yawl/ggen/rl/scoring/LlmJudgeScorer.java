/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.scoring;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.yawlfoundation.yawl.ggen.powl.PowlActivity;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Scores POWL models using a local Ollama LLM as a judge.
 * The LLM grades how well a candidate model matches a process description
 * and returns a score in [-1.0, 1.0], which is normalized to [0.0, 1.0].
 */
public class LlmJudgeScorer implements RewardFunction {

    private static final String GENERATE_PATH = "/api/generate";

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;

    /**
     * Constructs an LlmJudgeScorer.
     * Reads OLLAMA_BASE_URL from environment variables (default: http://localhost:11434).
     *
     * @param model        the Ollama model name (e.g., "qwen2.5-coder") (must not be blank)
     * @param timeoutSecs  HTTP request timeout in seconds (must be positive)
     * @throws IllegalArgumentException if model is blank or timeoutSecs is not positive
     */
    public LlmJudgeScorer(String model, int timeoutSecs) {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (timeoutSecs <= 0) {
            throw new IllegalArgumentException("timeoutSecs must be positive, got: " + timeoutSecs);
        }

        this.baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSecs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    /**
     * Scores a candidate POWL model by sending it to the Ollama LLM judge.
     * The judge returns a score in [-1.0, 1.0], which is normalized to [0.0, 1.0]
     * using the formula: (score + 1.0) / 2.0.
     *
     * @param candidate           the POWL model to score (must not be null)
     * @param processDescription  the reference process description (must not be null)
     * @return a normalized score in [0.0, 1.0]
     * @throws IllegalArgumentException  if candidate or processDescription is null
     * @throws RuntimeException          if the LLM call fails or returns no SCORE line
     */
    @Override
    public double score(PowlModel candidate, String processDescription) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(processDescription, "processDescription must not be null");

        String powlDescription = renderPowlAsText(candidate.root());
        String prompt = buildGradingPrompt(processDescription, powlDescription);

        try {
            return callOllamaForScore(prompt);
        } catch (IOException e) {
            throw new RuntimeException("LLM judge call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Calls the Ollama API and extracts the score from the response.
     * Looks for a line containing "SCORE: <float>" and parses the float value.
     * Normalizes the score from [-1.0, 1.0] to [0.0, 1.0].
     *
     * @param prompt the grading prompt to send to the LLM
     * @return a normalized score in [0.0, 1.0]
     * @throws IOException                if the HTTP call fails
     * @throws IllegalStateException      if the response does not contain a SCORE line
     * @throws NumberFormatException      if the score value is not a valid float
     */
    private double callOllamaForScore(String prompt) throws IOException {
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
            return parseScoreFromResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted during LLM scoring", e);
        }
    }

    /**
     * Parses the Ollama JSON response and extracts the SCORE value.
     *
     * @param rawJson the full JSON response from Ollama
     * @return a normalized score in [0.0, 1.0]
     * @throws IOException                if the JSON is malformed or lacks a "response" field
     * @throws IllegalStateException      if the response does not contain a SCORE line
     * @throws NumberFormatException      if the score value cannot be parsed as a float
     */
    private double parseScoreFromResponse(String rawJson) throws IOException {
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

        // Look for "SCORE: <float>" line
        for (String line : responseText.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("SCORE:")) {
                String scoreStr = trimmed.substring("SCORE:".length()).trim();
                try {
                    double rawScore = Double.parseDouble(scoreStr);
                    // Normalize from [-1.0, 1.0] to [0.0, 1.0]
                    return (rawScore + 1.0) / 2.0;
                } catch (NumberFormatException e) {
                    throw new NumberFormatException(
                        "Invalid SCORE value: '" + scoreStr + "'. Expected a float."
                    );
                }
            }
        }

        throw new IllegalStateException(
            "Response does not contain a SCORE line. Response was: " + responseText
        );
    }

    /**
     * Renders a POWL node as a human-readable text description.
     * Activities render as their label; operators render with their type and children.
     *
     * @param node the POWL node to render
     * @return a text representation of the node
     */
    private String renderPowlAsText(PowlNode node) {
        return switch (node) {
            case PowlActivity activity -> activity.label();
            case PowlOperatorNode operator -> operator.type() + "("
                    + operator.children().stream()
                    .map(this::renderPowlAsText)
                    .collect(Collectors.joining(", "))
                    + ")";
        };
    }

    /**
     * Builds the grading prompt sent to the Ollama model.
     *
     * @param processDescription the reference process description
     * @param powlText           the POWL model rendered as text
     * @return the full prompt string
     */
    private String buildGradingPrompt(String processDescription, String powlText) {
        return """
                You are a process model quality evaluator. Given a process description and a POWL model,
                grade how well the model captures the described process.

                Process description: %s

                POWL model: %s

                Output exactly one line: SCORE: <float between -1.0 and 1.0>
                -1.0 = completely wrong, 0.0 = partially correct, 1.0 = perfect match
                """.formatted(processDescription, powlText);
    }

    /**
     * Builds the JSON request body for the Ollama API.
     *
     * @param prompt the prompt string to send
     * @return the JSON request body with escaped characters
     */
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
