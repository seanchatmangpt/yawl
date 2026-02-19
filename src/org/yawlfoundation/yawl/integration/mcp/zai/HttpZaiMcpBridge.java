/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.mcp.zai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP-based Z.AI MCP bridge implementation.
 *
 * <p>This bridge provides HTTP-based communication with Z.AI services,
 * supporting both REST API calls and streaming connections for real-time
 * interactions.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class HttpZaiMcpBridge implements ZaiMcpBridge {

    private static final Logger _logger = LoggerFactory.getLogger(HttpZaiMcpBridge.class);

    private final ZaiMcpConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final ExecutorService executor;
    private final Map<String, String> activeSessions;
    private volatile boolean isConnected = false;

    /**
     * Creates a new HTTP Z.AI MCP bridge.
     *
     * @param config the Z.AI configuration
     */
    public HttpZaiMcpBridge(ZaiMcpConfig config) {
        this.config = config;
        this.jsonMapper = new ObjectMapper();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.activeSessions = new ConcurrentHashMap<>();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .executor(executor)
            .build();
    }

    /**
     * Creates a new HTTP Z.AI MCP bridge with custom configuration.
     *
     * @param config the Z.AI configuration
     * @param customHttpClient custom HTTP client
     * @param customJsonMapper custom JSON mapper
     */
    public HttpZaiMcpBridge(ZaiMcpConfig config, HttpClient customHttpClient, ObjectMapper customJsonMapper) {
        this.config = config;
        this.httpClient = customHttpClient;
        this.jsonMapper = customJsonMapper;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.activeSessions = new ConcurrentHashMap<>();
    }

    @Override
    public void connect() throws IOException {
        try {
            // Test the connection to Z.AI
            String healthCheckUrl = config.getBaseUrl() + "/health";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(healthCheckUrl))
                .header("Authorization", "Bearer " + config.getApiKey())
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                isConnected = true;
                _logger.info("Connected to Z.AI at {}", config.getBaseUrl());
            } else {
                throw new IOException("Failed to connect to Z.AI: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Connection to Z.AI was interrupted", e);
        }
    }

    @Override
    public void disconnect() {
        isConnected = false;
        activeSessions.clear();
        executor.shutdown();
        _logger.info("Disconnected from Z.AI");
    }

    @Override
    public CompletableFuture<Map<String, Object>> callTool(String toolName, Map<String, Object> parameters) {
        if (!isConnected) {
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Not connected to Z.AI"));
            return future;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return callToolSync(toolName, parameters);
            } catch (Exception e) {
                throw new RuntimeException("Tool call failed", e);
            }
        }, executor);
    }

    /**
     * Synchronously calls a Z.AI tool.
     */
    private Map<String, Object> callToolSync(String toolName, Map<String, Object> parameters) throws IOException {
        String url = config.getBaseUrl() + "/tools/" + toolName + "/call";

        // Build the request body
        Map<String, Object> requestBody = new ConcurrentHashMap<>();
        requestBody.put("parameters", parameters);
        requestBody.put("session_id", config.getSessionId());

        String jsonBody = jsonMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + config.getApiKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            try {
                JsonNode responseNode = jsonMapper.readTree(response.body());
                return convertJsonToMap(responseNode);
            } catch (IOException e) {
                throw new IOException("Failed to parse Z.AI response: " + e.getMessage(), e);
            }
        } else {
            throw new IOException("Z.AI tool call failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public ZaiMcpConfig getConfig() {
        return config;
    }

    @Override
    public String createSession() {
        String sessionId = "zai_session_" + System.currentTimeMillis();
        activeSessions.put(sessionId, sessionId);
        return sessionId;
    }

    @Override
    public void closeSession(String sessionId) {
        activeSessions.remove(sessionId);
    }

    @Override
    public Map<String, Object> getToolInfo(String toolName) throws IOException {
        String url = config.getBaseUrl() + "/tools/" + toolName;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + config.getApiKey())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            try {
                JsonNode responseNode = jsonMapper.readTree(response.body());
                return convertJsonToMap(responseNode);
            } catch (IOException e) {
                throw new IOException("Failed to parse tool info: " + e.getMessage(), e);
            }
        } else {
            throw new IOException("Failed to get tool info for " + toolName + ": HTTP " + response.statusCode());
        }
    }

    @Override
    public Map<String, Object> listTools() throws IOException {
        String url = config.getBaseUrl() + "/tools";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + config.getApiKey())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            try {
                JsonNode responseNode = jsonMapper.readTree(response.body());
                return convertJsonToMap(responseNode);
            } catch (IOException e) {
                throw new IOException("Failed to parse tools list: " + e.getMessage(), e);
            }
        } else {
            throw new IOException("Failed to list tools: HTTP " + response.statusCode());
        }
    }

    /**
     * Converts a JsonNode to a Map<String, Object>.
     */
    private Map<String, Object> convertJsonToMap(JsonNode node) {
        Map<String, Object> result = new ConcurrentHashMap<>();

        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if (value.isValueNode()) {
                result.put(key, value.asText());
            } else if (value.isArray()) {
                result.put(key, jsonMapper.convertValue(value, Object.class));
            } else if (value.isObject()) {
                result.put(key, convertJsonToMap(value));
            } else {
                result.put(key, value.toString());
            }
        });

        return result;
    }

    /**
     * Factory for creating HTTP Z.AI bridges.
     */
    public static class Factory {
        /**
         * Creates a new HTTP Z.AI bridge with default configuration.
         *
         * @param config the Z.AI configuration
         * @return a new HTTP Z.AI bridge
         * @throws IOException if connection fails
         */
        public static HttpZaiMcpBridge create(ZaiMcpConfig config) throws IOException {
            HttpZaiMcpBridge bridge = new HttpZaiMcpBridge(config);
            bridge.connect();
            return bridge;
        }

        /**
         * Creates a new HTTP Z.AI bridge with custom HTTP client.
         *
         * @param config the Z.AI configuration
         * @param customHttpClient custom HTTP client
         * @return a new HTTP Z.AI bridge
         * @throws IOException if connection fails
         */
        public static HttpZaiMcpBridge create(ZaiMcpConfig config, HttpClient customHttpClient) throws IOException {
            HttpZaiMcpBridge bridge = new HttpZaiMcpBridge(config, customHttpClient, new ObjectMapper());
            bridge.connect();
            return bridge;
        }

        /**
         * Creates a new HTTP Z.AI bridge with full custom configuration.
         *
         * @param config the Z.AI configuration
         * @param customHttpClient custom HTTP client
         * @param customJsonMapper custom JSON mapper
         * @return a new HTTP Z.AI bridge
         * @throws IOException if connection fails
         */
        public static HttpZaiMcpBridge create(ZaiMcpConfig config, HttpClient customHttpClient, ObjectMapper customJsonMapper) throws IOException {
            HttpZaiMcpBridge bridge = new HttpZaiMcpBridge(config, customHttpClient, customJsonMapper);
            bridge.connect();
            return bridge;
        }
    }
}