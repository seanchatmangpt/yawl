/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dspy.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.fluent.DspyLM;
import org.yawlfoundation.yawl.dspy.llm.ChatMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Simple HTTP-based LLM client supporting OpenAI-compatible APIs.
 *
 * <p>Supports providers like:
 * <ul>
 *   <li>OpenAI (api.openai.com)</li>
 *   <li>Groq (api.groq.com)</li>
 *   <li>Anthropic (api.anthropic.com)</li>
 *   <li>Local models via Ollama or vLLM</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class SimpleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(SimpleLlmClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String model;
    private final @Nullable String apiKey;
    private final @Nullable String apiBase;
    private final @Nullable Double temperature;
    private final @Nullable Integer maxTokens;
    private final HttpClient httpClient;

    /**
     * Create a new SimpleLlmClient from a DspyLM configuration.
     *
     * @param lm the DSPy language model configuration
     */
    public SimpleLlmClient(DspyLM lm) {
        this.model = lm.model();
        this.apiKey = lm.apiKey();
        this.apiBase = lm.apiBase();
        this.temperature = lm.temperature();
        this.maxTokens = lm.maxTokens();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    /**
     * Create a new SimpleLlmClient with explicit settings.
     *
     * @param model the model identifier (e.g., "groq/llama-3.3-70b")
     * @param apiKey the API key (may be null for local models)
     * @param apiBase the API base URL (may be null for defaults)
     * @param temperature the temperature for generation
     * @param maxTokens the maximum tokens in the response
     */
    public SimpleLlmClient(
        String model,
        @Nullable String apiKey,
        @Nullable String apiBase,
        @Nullable Double temperature,
        @Nullable Integer maxTokens
    ) {
        this.model = model;
        this.apiKey = apiKey;
        this.apiBase = apiBase;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            String provider = getProvider();
            String baseUrl = getBaseUrl(provider);
            String requestBody = buildRequestBody(request, provider);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + (apiKey != null ? apiKey : ""))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(120))
                .build();

            HttpResponse<String> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 400) {
                LlmException.ErrorKind kind = response.statusCode() == 401
                    ? LlmException.ErrorKind.AUTHENTICATION
                    : response.statusCode() == 429
                    ? LlmException.ErrorKind.RATE_LIMITED
                    : response.statusCode() >= 500
                    ? LlmException.ErrorKind.SERVICE_UNAVAILABLE
                    : LlmException.ErrorKind.INVALID_REQUEST;
                throw new LlmException(
                    kind,
                    "LLM API error (status " + response.statusCode() + "): " + response.body()
                );
            }

            return parseResponse(response.body(), startTime);

        } catch (LlmException e) {
            throw e;
        } catch (java.net.ConnectException e) {
            throw new LlmException(
                LlmException.ErrorKind.SERVICE_UNAVAILABLE,
                "Connection failed: " + e.getMessage(),
                e
            );
        } catch (java.net.SocketTimeoutException e) {
            throw new LlmException(
                LlmException.ErrorKind.TIMEOUT,
                "Request timed out: " + e.getMessage(),
                e
            );
        } catch (Exception e) {
            throw new LlmException(
                LlmException.ErrorKind.RUNTIME_ERROR,
                "LLM request failed: " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public String modelId() {
        return model;
    }

    @Override
    public int countTokens(String text) {
        // Simple approximation: ~4 characters per token
        return text.length() / 4;
    }

    @Override
    public int maxContextLength() {
        // Default context length for most models
        return 8192;
    }

    private String getProvider() {
        int slash = model.indexOf('/');
        return slash > 0 ? model.substring(0, slash) : "openai";
    }

    private String getModelName() {
        int slash = model.indexOf('/');
        return slash > 0 ? model.substring(slash + 1) : model;
    }

    private String getBaseUrl(String provider) {
        if (apiBase != null && !apiBase.isBlank()) {
            return apiBase;
        }
        return switch (provider) {
            case "groq" -> "https://api.groq.com/openai/v1";
            case "anthropic" -> "https://api.anthropic.com/v1";
            case "openai" -> "https://api.openai.com/v1";
            default -> "http://localhost:11434/v1";
        };
    }

    private String buildRequestBody(LlmRequest request, String provider) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("model", getModelName());

        // Build messages array
        ArrayNode messages = body.putArray("messages");

        // Add messages from request if present
        if (request.messages() != null && !request.messages().isEmpty()) {
            for (ChatMessage msg : request.messages()) {
                ObjectNode msgNode = messages.addObject();
                msgNode.put("role", msg.role().name().toLowerCase());
                msgNode.put("content", msg.content());
            }
        } else {
            // Add user message from prompt
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", request.prompt());
        }

        // Add optional parameters
        if (temperature != null) {
            body.put("temperature", temperature);
        }
        if (maxTokens != null) {
            body.put("max_tokens", maxTokens);
        }

        // Add stop sequences if present
        if (request.stopSequences() != null && !request.stopSequences().isEmpty()) {
            ArrayNode stop = body.putArray("stop");
            for (String s : request.stopSequences()) {
                stop.add(s);
            }
        }

        return MAPPER.writeValueAsString(body);
    }

    private LlmResponse parseResponse(String responseBody, long startTime) throws Exception {
        JsonNode root = MAPPER.readTree(responseBody);

        // Extract text from choices[0].message.content (OpenAI format)
        String text = "";
        if (root.has("choices") && root.get("choices").isArray()) {
            JsonNode choices = root.get("choices");
            if (choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                if (firstChoice.has("message")) {
                    JsonNode message = firstChoice.get("message");
                    if (message.has("content")) {
                        text = message.get("content").asText();
                    }
                }
            }
        }
        // Anthropic format: content[0].text
        else if (root.has("content") && root.get("content").isArray()) {
            JsonNode content = root.get("content");
            if (content.size() > 0) {
                JsonNode firstBlock = content.get(0);
                if (firstBlock.has("text")) {
                    text = firstBlock.get("text").asText();
                }
            }
        }

        // Extract usage info
        int promptTokens = 0;
        int completionTokens = 1;

        if (root.has("usage")) {
            JsonNode usage = root.get("usage");
            promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
            completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
        }

        return LlmResponse.builder()
            .text(text)
            .inputTokens(promptTokens)
            .outputTokens(completionTokens)
            .latencyMs(System.currentTimeMillis() - startTime)
            .build();
    }
}
