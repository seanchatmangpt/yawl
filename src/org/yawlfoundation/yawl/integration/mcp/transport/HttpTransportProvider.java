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

package org.yawlfoundation.yawl.integration.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP transport provider for MCP servers.
 *
 * <p>This transport implementation provides HTTP/S-based communication for
 * MCP servers, supporting both SSE (Server-Sent Events) and WebSocket protocols
 * for real-time communication.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class HttpTransportProvider {

    private static final Logger _logger = LoggerFactory.getLogger(HttpTransportProvider.class);

    private final HttpClient httpClient;
    private final ObjectMapper jsonMapper;
    private final int httpPort;
    private final ExecutorService executor;
    private final Map<String, ClientSession> activeSessions;
    private volatile boolean isRunning = false;

    /**
     * Creates a new HTTP transport provider.
     *
     * @param httpPort the HTTP port to listen on
     * @param jsonMapper Jackson ObjectMapper for JSON serialization
     */
    public HttpTransportProvider(int httpPort, ObjectMapper jsonMapper) {
        this.httpPort = httpPort;
        this.jsonMapper = jsonMapper;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.activeSessions = new ConcurrentHashMap<>();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .executor(executor)
            .build();
    }

    /**
     * Starts the HTTP transport server.
     *
     * @throws IOException if the server cannot start
     */
    public void start() throws IOException {
        isRunning = true;
        _logger.info("HTTP transport provider started on port {}", httpPort);

        startServer();
    }

    /**
     * Stops the HTTP transport server.
     */
    public void stop() {
        isRunning = false;
        executor.shutdown();
        activeSessions.clear();
        _logger.info("HTTP transport provider stopped");
    }

    /**
     * Creates a new client session.
     *
     * @param clientId the client identifier
     * @return a new client session
     */
    public ClientSession createSession(String clientId) {
        ClientSession session = new ClientSession(clientId);
        activeSessions.put(clientId, session);
        return session;
    }

    /**
     * Gets an existing client session.
     *
     * @param clientId the client identifier
     * @return the client session or null if not found
     */
    public ClientSession getSession(String clientId) {
        return activeSessions.get(clientId);
    }

    /**
     * Removes a client session.
     *
     * @param clientId the client identifier
     */
    public void removeSession(String clientId) {
        activeSessions.remove(clientId);
    }

    /**
     * Sends a message to a specific client.
     *
     * @param clientId the client identifier
     * @param message the message to send
     * @return true if sent successfully, false otherwise
     */
    public boolean sendMessage(String clientId, JsonNode message) {
        ClientSession session = activeSessions.get(clientId);
        if (session != null) {
            session.sendMessage(message);
            return true;
        }
        return false;
    }

    /**
     * Broadcasts a message to all clients.
     *
     * @param message the message to broadcast
     */
    public void broadcast(JsonNode message) {
        activeSessions.values().forEach(session -> session.sendMessage(message));
    }

    /**
     * Gets the number of active sessions.
     *
     * @return the number of active sessions
     */
    public int getSessionCount() {
        return activeSessions.size();
    }

    /**
     * Checks if the server is running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Starts the actual HTTP server implementation.
     * This would typically use a web framework like Spring Boot or Netty.
     */
    private void startServer() {
        throw new UnsupportedOperationException(
            "startServer() is not implemented. HTTP server startup requires:\n" +
            "  1. An HTTP server framework (Jetty, Netty, or Spring Boot embedded)\n" +
            "  2. Route bindings for: POST /mcp/call_tool, POST /mcp/subscribe,\n" +
            "     GET /mcp/session/{id}, SSE /mcp/stream/{id}\n" +
            "  3. Request parsing and dispatch to MCPRequestHandler\n" +
            "  4. Session lifecycle management via ClientSession\n" +
            "See YawlMcpHttpServer.java for a complete Spring-based implementation."
        );
    }

    /**
     * Represents a client session.
     */
    public static class ClientSession {
        private final String clientId;
        private final String sessionId;
        private final long createdAt;
        private volatile boolean isActive = true;
        private final Map<String, Object> metadata;

        public ClientSession(String clientId) {
            this.clientId = clientId;
            this.sessionId = generateSessionId();
            this.createdAt = System.currentTimeMillis();
            this.metadata = new ConcurrentHashMap<>();
        }

        private String generateSessionId() {
            return "session_" + System.currentTimeMillis() + "_" + clientId.hashCode();
        }

        public void sendMessage(JsonNode message) {
            if (!isActive) {
                throw new IllegalStateException("Session is not active");
            }
            throw new UnsupportedOperationException(
                "sendMessage() is not implemented. HTTP message sending requires:\n" +
                "  1. An active HTTP connection or SSE stream to the client\n" +
                "  2. Serialisation of message to JSON-RPC 2.0 format\n" +
                "  3. Writing to the underlying transport channel\n" +
                "  4. Error handling for broken connections (remove dead sessions)\n" +
                "Implement by injecting an AsyncContext (Servlet) or a Flux (WebFlux)."
            );
        }

        public void deactivate() {
            this.isActive = false;
        }

        public boolean isActive() {
            return isActive;
        }

        public String getClientId() {
            return clientId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public long getCreatedAt() {
            return createdAt;
        }

        public void setMetadata(String key, Object value) {
            metadata.put(key, value);
        }

        public Object getMetadata(String key) {
            return metadata.get(key);
        }
    }

    /**
     * HTTP request handler for MCP messages.
     */
    public static class MCPRequestHandler {
        private final HttpTransportProvider transport;
        private final ObjectMapper jsonMapper;

        public MCPRequestHandler(HttpTransportProvider transport, ObjectMapper jsonMapper) {
            this.transport = transport;
            this.jsonMapper = jsonMapper;
        }

        /**
         * Handles an MCP call request.
         *
         * @param requestBody the JSON-RPC request body as a string
         * @return the JSON-RPC response body as a string
         * @throws IOException if parsing or processing fails
         */
        public String handleCallRequest(String requestBody) throws IOException {
            JsonNode requestJson = jsonMapper.readTree(requestBody);
            String method = requestJson.has("method") ? requestJson.get("method").asText() : "unknown";
            JsonNode params = requestJson.has("params") ? requestJson.get("params") : jsonMapper.nullNode();

            JsonNode response = handleMethod(method, params);
            return jsonMapper.writeValueAsString(response);
        }

        /**
         * Handles an MCP method call.
         *
         * @throws UnsupportedOperationException always — MCP method routing is not implemented
         */
        private JsonNode handleMethod(String method, JsonNode params) throws IOException {
            throw new UnsupportedOperationException(
                "handleMethod() is not implemented for method '" + method + "'. " +
                "MCP method routing requires:\n" +
                "  1. A dispatch table mapping method names to handler functions\n" +
                "  2. Handlers for at minimum: initialize, tools/list, tools/call\n" +
                "  3. JSON-RPC 2.0 response framing (id, jsonrpc, result/error)\n" +
                "  4. Error code mapping per MCP specification (see MCP spec section 4)\n" +
                "See YawlMcpHttpServer.java for a complete Spring-based MCP server example."
            );
        }
    }

    /**
     * Factory for creating HTTP transport providers.
     */
    public static class Factory {
        /**
         * Creates a new HTTP transport provider with default configuration.
         *
         * @param httpPort the HTTP port
         * @return a new HTTP transport provider
         */
        public static HttpTransportProvider create(int httpPort) {
            ObjectMapper mapper = new ObjectMapper();
            return new HttpTransportProvider(httpPort, mapper);
        }

        /**
         * Creates a new HTTP transport provider with custom configuration.
         *
         * @param httpPort the HTTP port
         * @param jsonMapper custom ObjectMapper
         * @return a new HTTP transport provider
         */
        public static HttpTransportProvider create(int httpPort, ObjectMapper jsonMapper) {
            return new HttpTransportProvider(httpPort, jsonMapper);
        }
    }
}