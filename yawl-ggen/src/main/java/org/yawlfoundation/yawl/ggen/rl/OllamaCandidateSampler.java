/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.yawlfoundation.yawl.ggen.memory.ProcessKnowledgeGraph;
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
 *
 * <h2>OpenSage innovations</h2>
 * <p><strong>Virtual threads (Phase 3)</strong>: K Ollama HTTP calls run concurrently
 * via {@code Executors.newVirtualThreadPerTaskExecutor()}, yielding ~K× throughput
 * vs the sequential baseline.
 *
 * <p><strong>EnsembleDiscoveryBoard (Phase 4b)</strong>: A per-round
 * {@link DiscoveryBoard} is shared across all K virtual threads. The first thread
 * to parse a valid model publishes it; subsequent threads — if still awaiting their
 * LLM response — receive a bias hint steering them toward novel patterns.
 *
 * <p><strong>ProcessKnowledgeGraph (Phase 4a)</strong>: An optional cross-round
 * memory graph can be injected to bias the generation prompt with patterns that
 * yielded high rewards in previous GRPO rounds.
 */
public class OllamaCandidateSampler implements CandidateSampler {

    private static final String GENERATE_PATH = "/api/generate";
    private static final double[] TEMPERATURES = {0.5, 0.7, 0.9, 1.0, 0.6, 0.8, 0.95, 0.75};

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final PowlTextParser parser;
    private final ProcessKnowledgeGraph knowledgeGraph;

    /**
     * Creates a sampler without a knowledge graph (no cross-round memory bias).
     */
    public OllamaCandidateSampler(String baseUrl, String model, int timeoutSecs) {
        this(baseUrl, model, timeoutSecs, new ProcessKnowledgeGraph());
    }

    /**
     * Creates a sampler with an injected knowledge graph for cross-round memory bias.
     *
     * @param knowledgeGraph shared graph written by GrpoOptimizer.optimize() after each round;
     *                       must not be null
     */
    public OllamaCandidateSampler(String baseUrl, String model, int timeoutSecs,
                                  ProcessKnowledgeGraph knowledgeGraph) {
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
        this.knowledgeGraph = knowledgeGraph;
    }

    @Override
    public List<PowlModel> sample(String processDescription, int k) throws IOException, PowlParseException {
        if (processDescription == null || processDescription.isBlank())
            throw new IllegalArgumentException("processDescription must not be blank");
        if (k <= 0) throw new IllegalArgumentException("k must be positive");

        // One DiscoveryBoard per sample() call — horizontal ensemble for this round.
        // The first virtual thread to parse a model publishes to the board;
        // later threads augment their prompt with the discovery to steer diversity.
        DiscoveryBoard board = new DiscoveryBoard();

        // Compute the cross-round memory bias hint once per sample() call.
        // All K threads share the same graph-derived bias (graph is read-only during sampling).
        String graphBias = knowledgeGraph.biasHint(processDescription, 3);

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
                            // Board hint: peer discoveries already published this round
                            String boardBias = board.isEmpty()
                                    ? null
                                    : formatBoardBias(board.topK(1));
                            String response = callOllama(
                                    processDescription, temperature, graphBias, boardBias);
                            PowlModel candidate = parser.parse(response, modelId);
                            // Publish so later threads can steer away from this pattern
                            board.publish(candidate);
                            return candidate;
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

    private String callOllama(String description, double temperature,
                               String graphBias, String boardBias) throws IOException {
        String prompt = buildGenerationPrompt(description, graphBias, boardBias);
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

    private String buildGenerationPrompt(String description, String graphBias, String boardBias) {
        StringBuilder prompt = new StringBuilder("""
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
            """.formatted(description));

        // Cross-round memory: steer away from already-rewarded patterns (OpenSage long-term memory)
        if (graphBias != null && !graphBias.isBlank()) {
            prompt.append("\n").append(graphBias).append("\n");
        }
        // Within-round ensemble: steer away from peer discoveries (OpenSage horizontal ensemble)
        if (boardBias != null && !boardBias.isBlank()) {
            prompt.append("\n").append(boardBias).append("\n");
        }

        prompt.append("\nPOWL model:");
        return prompt.toString();
    }

    private static String formatBoardBias(List<PowlModel> peerModels) {
        if (peerModels.isEmpty()) {
            throw new IllegalStateException(
                "formatBoardBias called with empty peer list — caller must guard with board.isEmpty()");
        }
        StringBuilder sb = new StringBuilder(
                "A peer sampler has already found the following pattern(s) this round"
                + " — generate something structurally different:\n");
        for (PowlModel peer : peerModels) {
            sb.append("- ").append(ProcessKnowledgeGraph.fingerprint(peer)).append("\n");
        }
        return sb.toString().stripTrailing();
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
