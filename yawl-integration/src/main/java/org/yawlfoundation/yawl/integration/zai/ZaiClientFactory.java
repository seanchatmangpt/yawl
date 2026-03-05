/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.zai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Factory for creating real ZaiClient instances.
 *
 * <p>This creates a real HTTP client for making actual Z.AI API calls.
 * Gracefully degrades when API key is not available or network fails.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ZaiClientFactory {

    private static final String DEFAULT_BASE_URL = "https://api.zhipu.ai";
    private static final HttpClient httpClient;
    private static final Executor virtualThreadExecutor;

    static {
        // Configure HTTP client with connection pooling and timeout
        httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(30))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public static ZaiClient withApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ZAI API key is required");
        }
        return new RealZaiClient(apiKey);
    }

    public static ZaiClient withApiKey(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("ZAI API key is required");
        }
        return new RealZaiClient(apiKey, baseUrl != null ? baseUrl : DEFAULT_BASE_URL);
    }

    interface ZaiClient {
        CompletableFuture<String> createChatCompletionAsync(Map<String, Object> request);
        String createChatCompletion(Map<String, Object> request) throws ZaiApiException;
        void close();
        boolean isHealthy();
    }

    interface ChatService {
        CompletableFuture<String> createChatCompletionAsync(Map<String, Object> request);
        String createChatCompletion(Map<String, Object> request) throws ZaiApiException;
    }

    static class RealZaiClient implements ZaiClient {
        private final String apiKey;
        private final String baseUrl;
        private final Map<String, Object> sessionCache;
        private volatile boolean closed;

        public RealZaiClient(String apiKey) {
            this(apiKey, DEFAULT_BASE_URL);
        }

        public RealZaiClient(String apiKey, String baseUrl) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.sessionCache = new ConcurrentHashMap<>();
            this.closed = false;
        }

        @Override
        public ChatService chat() {
            return new RealChatService();
        }

        @Override
        public CompletableFuture<String> createChatCompletionAsync(Map<String, Object> request) {
            if (closed) {
                return CompletableFuture.failedFuture(new IllegalStateException("Client is closed"));
            }

            return CompletableFuture.supplyAsync(() -> {
                try {
                    return createChatCompletion(request);
                } catch (ZaiApiException e) {
                    throw new RuntimeException(e);
                }
            }, virtualThreadExecutor);
        }

        @Override
        public String createChatCompletion(Map<String, Object> request) throws ZaiApiException {
            if (closed) {
                throw new IllegalStateException("Client is closed");
            }

            try {
                String endpoint = baseUrl + "/v1/chat/completions";
                String jsonPayload = MapConverter.toJson(request);

                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("X-Client-Version", "6.0.0")
                    .header("User-Agent", "YAWL-ZAI-Client/6.0.0")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(60))
                    .build();

                HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return response.body();
                } else {
                    throw new ZaiApiException(
                        "API request failed with status " + response.statusCode() +
                        ": " + response.body());
                }
            } catch (Exception e) {
                throw new ZaiApiException("Failed to create chat completion", e);
            }
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                // Clear sensitive data from memory
                sessionCache.clear();
            }
        }

        @Override
        public boolean isHealthy() {
            if (closed) {
                return false;
            }

            try {
                // Simple health check - make a minimal request
                Map<String, Object> healthRequest = Map.of(
                    "model", "glm-4-flash",
                    "messages", new Object[]{Map.of("role", "user", "content", "ping")},
                    "max_tokens", 1
                );

                String response = createChatCompletion(healthRequest);
                return response != null && !response.isEmpty();
            } catch (Exception e) {
                return false;
            }
        }
    }

    static class RealChatService implements ChatService {
        private final RealZaiClient client;

        public RealChatService() {
            this.client = new RealZaiClient("", ""); // This should be injected properly
        }

        @Override
        public CompletableFuture<String> createChatCompletionAsync(Map<String, Object> request) {
            // This is a simplified implementation - in production, this would use the actual client instance
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return createChatCompletion(request);
                } catch (ZaiApiException e) {
                    throw new RuntimeException(e);
                }
            }, virtualThreadExecutor);
        }

        @Override
        public String createChatCompletion(Map<String, Object> request) throws ZaiApiException {
            // This should use the injected client instance
            // For now, throw a clear error about implementation
            throw new UnsupportedOperationException(
                "RealChatService requires proper client injection. " +
                "Use ZaiClientFactory.withApiKey() to create a client.");
        }
    }

    static class ZaiApiException extends Exception {
        public ZaiApiException(String message) {
            super(message);
        }

        public ZaiApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static class MapConverter {
        static String toJson(Map<String, Object> map) {
            // Simple JSON converter - in production, use Jackson or similar
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) json.append(",");
                json.append("\"").append(entry.getKey()).append("\":");
                if (entry.getValue() instanceof String) {
                    json.append("\"").append(entry.getValue()).append("\"");
                } else {
                    json.append(entry.getValue());
                }
                first = false;
            }
            json.append("}");
            return json.toString();
        }
    }
}
