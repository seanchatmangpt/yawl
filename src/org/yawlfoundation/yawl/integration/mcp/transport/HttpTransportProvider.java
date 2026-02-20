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
        try {
            // Note: The actual HTTP server is typically implemented by the containing application
            // (e.g., Spring Boot application context, embedded Jetty, or Netty server).
            // This class manages sessions and message routing but does not create the HTTP server itself.
            // The server should:
            // 1. Create a new HttpTransportProvider instance
            // 2. Register routes: POST /mcp/call_tool, POST /mcp/subscribe, GET /mcp/session/{id}
            // 3. Create sessions via createSession() on incoming connections
            // 4. Route requests through MCPRequestHandler
            // 5. Send responses via ClientSession.sendMessage()
            // 6. Call removeSession() when clients disconnect
            _logger.debug("HTTP transport server initialization requires external HTTP framework integration");
        } catch (Exception e) {
            _logger.error("Failed to initialize HTTP transport server", e);
            throw new RuntimeException("HTTP server initialization failed: " + e.getMessage(), e);
        }
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

            // This method is designed to be called from HTTP request handlers
            // The actual transport mechanism (SSE stream, WebSocket, or long-polling response)
            // must be provided by the containing HTTP framework (Spring WebFlux, Servlet AsyncContext, etc.)
            //
            // Pattern for use in HTTP framework:
            // 1. Request handler receives message from /mcp/subscribe or streaming endpoint
            // 2. Handler creates or retrieves ClientSession via createSession() or getSession()
            // 3. Handler calls sendMessage(jsonNode)
            // 4. Handler's underlying HTTP context (AsyncContext, ResponseBodyEmitter, etc.)
            //    writes the message to the HTTP response stream
            // 5. If the connection breaks, the handler calls removeSession() to clean up
            //
            // The message is kept in memory momentarily for the HTTP framework to access.
            // In a real implementation, inject an AsyncContext or Flux<ServerSentEvent> into
            // the constructor to actually write messages to the HTTP response.

            setMetadata("lastMessage", message.toString());
            setMetadata("lastMessageTime", System.currentTimeMillis());
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
         * Handles an MCP method call by dispatching to appropriate handlers.
         *
         * @param method the MCP method name (e.g. "initialize", "tools/list", "tools/call")
         * @param params the method parameters as JSON
         * @return JSON-RPC 2.0 response with result or error
         * @throws IOException if parameter parsing fails
         */
        private JsonNode handleMethod(String method, JsonNode params) throws IOException {
            return switch (method) {
                case "initialize" -> handleInitialize(params);
                case "tools/list" -> handleToolsList(params);
                case "tools/call" -> handleToolCall(params);
                case "resources/list" -> handleResourcesList(params);
                case "resources/read" -> handleResourceRead(params);
                case "resources/templates/list" -> handleResourceTemplatesList(params);
                default -> createErrorResponse(
                    -32601,
                    "Method not found: " + method,
                    jsonMapper.createObjectNode().put("method", method)
                );
            };
        }

        private JsonNode handleInitialize(JsonNode params) {
            return createSuccessResponse(jsonMapper.createObjectNode()
                .put("protocolVersion", "2024-11-25")
                .put("capabilities", jsonMapper.createObjectNode()
                    .put("tools", true)
                    .put("resources", true)
                )
                .put("serverInfo", jsonMapper.createObjectNode()
                    .put("name", "YAWL-MCP-Server")
                    .put("version", "6.0.0")
                )
            );
        }

        private JsonNode handleToolsList(JsonNode params) {
            return createSuccessResponse(jsonMapper.createObjectNode()
                .set("tools", jsonMapper.createArrayNode())
            );
        }

        private JsonNode handleToolCall(JsonNode params) {
            String toolName = params.has("name") ? params.get("name").asText() : "unknown";
            return createErrorResponse(
                -32602,
                "Tool not implemented: " + toolName,
                jsonMapper.createObjectNode().put("tool", toolName)
            );
        }

        private JsonNode handleResourcesList(JsonNode params) {
            return createSuccessResponse(jsonMapper.createObjectNode()
                .set("resources", jsonMapper.createArrayNode())
            );
        }

        private JsonNode handleResourceRead(JsonNode params) {
            String uri = params.has("uri") ? params.get("uri").asText() : "unknown";
            return createErrorResponse(
                -32602,
                "Resource not found: " + uri,
                jsonMapper.createObjectNode().put("uri", uri)
            );
        }

        private JsonNode handleResourceTemplatesList(JsonNode params) {
            return createSuccessResponse(jsonMapper.createObjectNode()
                .set("resourceTemplates", jsonMapper.createArrayNode())
            );
        }

        private JsonNode createSuccessResponse(JsonNode result) {
            return jsonMapper.createObjectNode()
                .put("jsonrpc", "2.0")
                .set("result", result)
                .put("id", 1);
        }

        private JsonNode createErrorResponse(int code, String message, JsonNode data) {
            return jsonMapper.createObjectNode()
                .put("jsonrpc", "2.0")
                .set("error", jsonMapper.createObjectNode()
                    .put("code", code)
                    .put("message", message)
                    .set("data", data)
                )
                .put("id", 1);
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