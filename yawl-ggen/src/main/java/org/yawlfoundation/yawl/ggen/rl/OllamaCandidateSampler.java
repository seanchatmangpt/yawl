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
import java.util.Objects;
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
 *
 * <p><strong>ProMoAI Prompt Suite (Phase 5a)</strong>: Prompts are constructed by
 * {@link ProMoAIPromptBuilder} using all six strategies from Kourani et al. (2024):
 * role prompting, knowledge injection, few-shot learning, negative prompting,
 * least-to-most decomposition, and feedback integration.
 *
 * <p><strong>Iterative Self-Correction (Phase 5b)</strong>: When
 * {@link PowlTextParser} fails to parse an LLM response, the sampler retries up to
 * {@link #MAX_CORRECTION_RETRIES} times by sending a correction prompt that embeds
 * the parse error and the malformed previous attempt. This mirrors the ProMoAI
 * paper's "critical error → up to 5 iterations with refined prompt" mechanism,
 * which achieves success within 2 iterations on average with GPT-4.
 */
public class OllamaCandidateSampler implements CandidateSampler {

    private static final String GENERATE_PATH = "/api/generate";
    private static final double[] TEMPERATURES = {0.5, 0.7, 0.9, 1.0, 0.6, 0.8, 0.95, 0.75};

    /** ProMoAI Phase 5b: max critical-error correction retries per candidate. */
    static final int MAX_CORRECTION_RETRIES = 3;

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final PowlTextParser parser;
    private final ProcessKnowledgeGraph knowledgeGraph;
    /** LLM backend gateway — default uses Ollama HTTP; overridden in tests. */
    private final LlmGateway gateway;

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
        this(baseUrl, model, timeoutSecs, knowledgeGraph, null);
        // gateway is set via the delegate below; null triggers the HTTP default
    }

    /**
     * Package-private constructor for testing: injects a custom {@link LlmGateway}
     * in place of the Ollama HTTP client. Allows deterministic testing of the retry
     * and self-correction logic without a live Ollama server.
     *
     * @param gateway the LLM backend to use (required, non-null)
     */
    OllamaCandidateSampler(String baseUrl, String model, int timeoutSecs,
                           ProcessKnowledgeGraph knowledgeGraph, LlmGateway gateway) {
        if (baseUrl == null || baseUrl.isBlank())
            throw new IllegalArgumentException("baseUrl must not be blank");
        if (model == null || model.isBlank())
            throw new IllegalArgumentException("model must not be blank");
        if (timeoutSecs <= 0)
            throw new IllegalArgumentException("timeoutSecs must be positive");
        Objects.requireNonNull(knowledgeGraph, "knowledgeGraph must not be null");
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSecs);
        this.parser = new PowlTextParser();
        this.knowledgeGraph = knowledgeGraph;
        if (gateway != null) {
            this.httpClient = null;  // not used when gateway is injected
            this.gateway = gateway;
        } else {
            this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
            this.gateway = this::callOllamaHttp;
        }
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

        // Submit K LLM calls as concurrent virtual threads (Java 21+).
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

                            // Phase 5a: ProMoAI prompt suite (all 6 strategies)
                            String initialPrompt = ProMoAIPromptBuilder.buildInitialPrompt(
                                    processDescription, graphBias, boardBias);
                            String lastResponse = gateway.send(initialPrompt, temperature);

                            // Phase 5b: iterative self-correction on parse failure
                            for (int attempt = 0; ; attempt++) {
                                try {
                                    PowlModel candidate = parser.parse(lastResponse, modelId);
                                    // Publish so later threads can steer away from this pattern
                                    board.publish(candidate);
                                    return candidate;
                                } catch (PowlParseException e) {
                                    if (attempt >= MAX_CORRECTION_RETRIES) throw e;
                                    String correctionPrompt = ProMoAIPromptBuilder.buildCorrectionPrompt(
                                            processDescription, lastResponse, e.getMessage());
                                    lastResponse = gateway.send(correctionPrompt, temperature);
                                }
                            }
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
                // PowlParseException after all retries exhausted — collect and continue
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Candidate sampling interrupted", e);
            }
        }

        if (candidates.isEmpty()) {
            throw new PowlParseException(
                "All " + k + " candidates failed to parse after " + MAX_CORRECTION_RETRIES
                    + " correction retries each. Errors: " + parseErrors,
                processDescription
            );
        }

        return candidates;
    }

    /**
     * Sends a prompt to the Ollama HTTP API and returns the response text.
     * Used as the default {@link LlmGateway} implementation.
     */
    private String callOllamaHttp(String prompt, double temperature) throws IOException {
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
