/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous.registry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Central agent registry with embedded HTTP server.
 *
 * Provides REST API for agent registration, heartbeat, discovery, and health monitoring.
 *
 * API Endpoints:
 * - POST /agents/register - Register agent
 * - GET /agents - List all registered agents
 * - GET /agents/by-capability?domain=X - Query by capability
 * - DELETE /agents/{id} - Unregister agent
 * - POST /agents/{id}/heartbeat - Update heartbeat
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class AgentRegistry {

    private static final Logger logger = LogManager.getLogger(AgentRegistry.class);

    private static final int DEFAULT_PORT = 9090;

    private final ConcurrentHashMap<String, AgentInfo> agents;
    private final HttpServer server;
    private final AgentHealthMonitor healthMonitor;

    /**
     * Create agent registry on default port (9090).
     *
     * @throws IOException if server cannot be created
     */
    public AgentRegistry() throws IOException {
        this(DEFAULT_PORT);
    }

    /**
     * Create agent registry on specified port.
     *
     * Uses virtual threads for HTTP request handling. Virtual threads provide
     * better scalability for I/O-bound operations like agent registration,
     * heartbeat updates, and discovery queries.
     *
     * @param port server port
     * @throws IOException if server cannot be created
     */
    public AgentRegistry(int port) throws IOException {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }

        this.agents = new ConcurrentHashMap<>();
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.healthMonitor = new AgentHealthMonitor(agents);

        setupEndpoints();
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        logger.info("Agent registry created on port {}", port);
    }

    /**
     * Start the registry server and health monitor.
     */
    public void start() {
        server.start();
        healthMonitor.start();
        logger.info("Agent registry started on port {}", server.getAddress().getPort());
    }

    /**
     * Stop the registry server and health monitor.
     */
    public void stop() {
        healthMonitor.stop();
        server.stop(0);
        logger.info("Agent registry stopped");
    }

    /**
     * Get the number of registered agents.
     *
     * @return agent count
     */
    public int getAgentCount() {
        return agents.size();
    }

    /**
     * Get all registered agents.
     *
     * @return list of agent info
     */
    public List<AgentInfo> getAllAgents() {
        return new ArrayList<>(agents.values());
    }

    private void setupEndpoints() {
        server.createContext("/agents/register", new RegisterHandler());
        server.createContext("/agents/by-capability", new QueryByCapabilityHandler());
        server.createContext("/agents", new AgentsHandler());
        logger.info("Registered HTTP endpoints: /agents/register, /agents, " +
                   "/agents/by-capability, /agents/{id}, /agents/{id}/heartbeat");
    }

    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            try {
                var body = readRequestBody(exchange);
                var agentInfo = AgentInfo.fromJson(body);

                agents.put(agentInfo.getId(), agentInfo);

                var response = "{\"status\":\"registered\",\"agentId\":\"%s\"}"
                    .formatted(agentInfo.getId());

                logger.info("Registered agent: {} (total agents: {})",
                           agentInfo.getName(), agents.size());

                sendResponse(exchange, 200, response);

            } catch (Exception e) {
                logger.error("Error registering agent", e);
                sendResponse(exchange, 400,
                    "{\"error\":\"%s\"}".formatted(escapeJson(e.getMessage())));
            }
        }
    }

    private class AgentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var path = exchange.getRequestURI().getPath();
            var method = exchange.getRequestMethod();

            if (path.equals("/agents")) {
                if ("GET".equals(method)) {
                    handleListAgents(exchange);
                } else {
                    sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                }
            } else {
                var parts = path.split("/");
                if (parts.length >= 3) {
                    var agentId = parts[2];
                    if (parts.length == 4 && "heartbeat".equals(parts[3])) {
                        if ("POST".equals(method)) {
                            handleHeartbeat(exchange, agentId);
                        } else {
                            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                        }
                    } else if (parts.length == 3) {
                        if ("DELETE".equals(method)) {
                            handleUnregister(exchange, agentId);
                        } else {
                            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                        }
                    } else {
                        sendResponse(exchange, 404, "{\"error\":\"Not found\"}");
                    }
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Not found\"}");
                }
            }
        }

        private void handleListAgents(HttpExchange exchange) throws IOException {
            var agentList = new ArrayList<>(agents.values());
            var json = new StringBuilder("[");
            for (int i = 0; i < agentList.size(); i++) {
                if (i > 0) json.append(",");
                json.append(agentList.get(i).toJson());
            }
            json.append("]");
            sendResponse(exchange, 200, json.toString());
        }

        private void handleUnregister(HttpExchange exchange, String agentId) throws IOException {
            var removed = agents.remove(agentId);
            if (removed != null) {
                logger.info("Unregistered agent: {} (total agents: {})",
                           removed.getName(), agents.size());
                sendResponse(exchange, 200,
                    "{\"status\":\"unregistered\",\"agentId\":\"%s\"}".formatted(agentId));
            } else {
                sendResponse(exchange, 404,
                    "{\"error\":\"Agent not found\",\"agentId\":\"%s\"}".formatted(agentId));
            }
        }

        private void handleHeartbeat(HttpExchange exchange, String agentId) throws IOException {
            var agent = agents.get(agentId);
            if (agent != null) {
                agent.updateHeartbeat();
                logger.debug("Heartbeat received from agent: {}", agentId);
                sendResponse(exchange, 200, "{\"status\":\"ok\"}");
            } else {
                sendResponse(exchange, 404,
                    "{\"error\":\"Agent not found\",\"agentId\":\"%s\"}".formatted(agentId));
            }
        }
    }

    private class QueryByCapabilityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            var query = exchange.getRequestURI().getQuery();
            if (query == null || !query.startsWith("domain=")) {
                sendResponse(exchange, 400,
                    "{\"error\":\"Missing domain parameter\"}");
                return;
            }

            var domain = urlDecode(query.substring(7));
            if (domain.isBlank()) {
                sendResponse(exchange, 400,
                    "{\"error\":\"Empty domain parameter\"}");
                return;
            }

            var matching = new ArrayList<AgentInfo>();
            for (var agent : agents.values()) {
                if (agent.getCapability().domainName().equalsIgnoreCase(domain) ||
                    agent.getCapability().description().toLowerCase()
                        .contains(domain.toLowerCase())) {
                    matching.add(agent);
                }
            }

            var json = new StringBuilder("[");
            for (int i = 0; i < matching.size(); i++) {
                if (i > 0) json.append(",");
                json.append(matching.get(i).toJson());
            }
            json.append("]");

            logger.debug("Query by capability '{}' found {} agent(s)",
                        domain, matching.size());

            sendResponse(exchange, 200, json.toString());
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        var is = exchange.getRequestBody();
        var buffer = new byte[8192];
        var body = new StringBuilder();
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            body.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response)
            throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot escape null JSON value");
        }
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    private String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * Main method to run registry as standalone server.
     *
     * Usage: java AgentRegistry [port]
     * Default port: 9090
     *
     * @param args optional port number
     */
    public static void main(String[] args) {
        try {
            int port = DEFAULT_PORT;
            if (args.length > 0) {
                try {
                    port = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number: " + args[0]);
                    System.err.println("Usage: java AgentRegistry [port]");
                    System.exit(1);
                }
            }

            AgentRegistry registry = new AgentRegistry(port);
            registry.start();

            System.out.println("Agent Registry running on port " + port);
            System.out.println("Press Ctrl+C to stop");

            Runtime.getRuntime().addShutdownHook(
                Thread.ofVirtual().unstarted(() -> {
                    System.out.println("\nShutting down registry...");
                    registry.stop();
                })
            );

            Thread.currentThread().join();

        } catch (Exception e) {
            logger.error("Failed to start agent registry", e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
