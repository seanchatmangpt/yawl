/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.yawlfoundation.yawl.ggen.powl.PowlModel;
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
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

/**
 * Generates POWL candidate models by calling the Ollama LLM API.
 *
 * <p>For each of K samples, sends a generation prompt to Ollama requesting
 * a POWL expression in s-expression format. The responses are parsed by
 * PowlTextParser into PowlModel instances.
 *
 * <p>Uses temperature variation between samples to ensure diversity:
 * temperatures cycle through [0.5, 0.7, 0.9, 1.0, ...] for K samples.
 */
public class OllamaCandidateSampler implements CandidateSampler {

    private static final String GENERATE_PATH = "/api/generate";
    private static final double[] TEMPERATURES = {0.5, 0.7, 0.9, 1.0, 0.6, 0.8, 0.95, 0.75};

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final PowlTextParser parser;

    public OllamaCandidateSampler(String baseUrl, String model, int timeoutSecs) {
        if (baseUrl == null || baseUrl.isBlank())
            throw new IllegalArgumentException("baseUrl must not be blank");
        if (model == null || model.isBlank())
            throw new IllegalArgumentException("model must not be blank");
        if (timeoutSecs <= 0)
            throw new IllegalArgumentException("timeoutSecs must be positive");
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSecs);
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.parser = new PowlTextParser();
    }

    @Override
    public List<PowlModel> sample(String processDescription, int k) throws IOException, PowlParseException {
        if (processDescription == null || processDescription.isBlank())
            throw new IllegalArgumentException("processDescription must not be blank");
        if (k <= 0) throw new IllegalArgumentException("k must be positive");

        // Submit K Ollama HTTP calls as concurrent virtual threads (Java 21+).
        // Each call uses a different temperature for candidate diversity.
        // Virtual threads prevent carrier thread pinning during network I/O —
        // K=4 yields ~4× throughput vs the sequential baseline.
        List<Future<PowlModel>> futures;
        try (ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = IntStream.range(0, k)
                    .mapToObj(i -> {
                        double temperature = TEMPERATURES[i % TEMPERATURES.length];
                        String modelId = "candidate_" + i + "_"
                                + UUID.randomUUID().toString().substring(0, 8);
                        return vt.submit(() -> {
                            String response = callOllama(processDescription, temperature);
                            return parser.parse(response, modelId);
                        });
                    })
                    .toList();
        } // executor shutdown — all tasks have been submitted; we collect below

        List<PowlModel> candidates = new ArrayList<>(k);
        List<String> parseErrors = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                candidates.add(futures.get(i).get());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                parseErrors.add("Candidate " + i + ": " + cause.getMessage());
                if (cause instanceof IOException ioe) throw ioe;
                // PowlParseException from parser.parse — collect and continue
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Candidate sampling interrupted", e);
            }
        }

        if (candidates.isEmpty()) {
            throw new PowlParseException(
                "All " + k + " candidates failed to parse. Errors: " + parseErrors,
                processDescription
            );
        }

        return candidates;
    }

    private String callOllama(String description, double temperature) throws IOException {
        String prompt = buildGenerationPrompt(description);
        String requestBody = buildRequestBody(prompt, temperature);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + GENERATE_PATH))
            .header("Content-Type", "application/json")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return extractResponseText(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Ollama call interrupted", e);
        }
    }

    private String buildGenerationPrompt(String description) {
        return """
            Generate a POWL (Partially Ordered Workflow Language) process model for this description:
            %s

            Output ONLY a POWL expression using this exact format:
            SEQUENCE(ACTIVITY(Step1), ACTIVITY(Step2), ...)
            Or use XOR(...), PARALLEL(...), LOOP(do_activity, redo_activity)

            Rules:
            1. Use ACTIVITY(label) for leaf activities
            2. Use SEQUENCE for sequential steps
            3. Use XOR for exclusive choices
            4. Use PARALLEL for concurrent activities
            5. Use LOOP(do, redo) for repetitive activities
            6. Output ONLY the POWL expression, no explanation

            POWL model:
            """.formatted(description);
    }

    private String extractResponseText(String rawJson) throws IOException {
        try {
            JsonObject jsonObject = JsonParser.parseString(rawJson).getAsJsonObject();
            if (!jsonObject.has("response")) {
                throw new IOException("Ollama response missing 'response' field");
            }
            return jsonObject.get("response").getAsString();
        } catch (Exception e) {
            throw new IOException("Failed to parse Ollama JSON: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String prompt, double temperature) {
        String escapedPrompt = prompt
            .replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        return String.format(
            "{\"model\": \"%s\", \"prompt\": \"%s\", \"temperature\": %.2f, \"stream\": false}",
            model, escapedPrompt, temperature
        );
    }
}
