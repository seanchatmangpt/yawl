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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.a2a;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import com.sun.net.httpserver.HttpServer;
import io.a2a.server.ServerCallContext;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.events.MainEventBus;
import io.a2a.server.events.MainEventBusProcessor;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.tasks.InMemoryPushNotificationConfigStore;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.spec.AgentCard;
import io.a2a.transport.rest.handler.RestHandler;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * YAWL A2A Configuration for Spring Boot integration.
 *
 * <p>This configuration class sets up the A2A server components and
 * wires them together. It adapts the pattern from YawlA2AServer for
 * Spring Boot using {@code @Configuration} and {@code @Bean}.</p>
 *
 * <h2>Bean Configuration</h2>
 * <ul>
 *   <li>{@code httpServer} - The HTTP server for A2A REST transport</li>
 *   <li>{@code restHandler} - The REST handler for A2A protocol</li>
 * </ul>
 *
 * <h2>Transport Configuration</h2>
 * <p>Supports REST transport:</p>
 * <pre>{@code
 * yawl:
 *   a2a:
 *     transport:
 *       rest:
 *         enabled: true
 *         port: 8082
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Configuration
@ConditionalOnProperty(prefix = "yawl.a2a", name = "enabled", havingValue = "true", matchIfMissing = true)
public class YawlA2AConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(YawlA2AConfiguration.class);

    @Autowired
    private YawlA2AAgentCard agentCardProducer;

    @Autowired
    private YawlA2AExecutor agentExecutor;

    @Value("${yawl.a2a.transport.rest.port:8082}")
    private int restPort;

    @Value("${yawl.a2a.transport.rest.path:/a2a}")
    private String restPath;

    private HttpServer httpServer;
    private ExecutorService executorService;
    private InMemoryTaskStore taskStore;
    private MainEventBus mainEventBus;

    /**
     * Creates the HTTP server bean for A2A REST transport.
     *
     * <p>The server is configured with:</p>
     * <ul>
     *   <li>Virtual thread executor for optimal I/O performance</li>
     *   <li>Configurable port from application.yml</li>
     *   <li>REST endpoints for A2A protocol</li>
     * </ul>
     *
     * @return the configured HttpServer instance
     * @throws IOException if the server cannot bind to the port
     */
    @Bean
    public HttpServer a2aHttpServer() throws IOException {
        LOGGER.info("Creating YAWL A2A HTTP Server on port {}", restPort);

        executorService = Executors.newVirtualThreadPerTaskExecutor();

        httpServer = HttpServer.create(new InetSocketAddress(restPort), 0);
        httpServer.setExecutor(executorService);

        // Initialize A2A components
        taskStore = new InMemoryTaskStore();
        mainEventBus = new MainEventBus();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(taskStore, mainEventBus);
        InMemoryPushNotificationConfigStore pushStore = new InMemoryPushNotificationConfigStore();
        MainEventBusProcessor busProcessor = new MainEventBusProcessor(
            mainEventBus, taskStore, null, queueManager);
        busProcessor.ensureStarted();

        // Create request handler
        DefaultRequestHandler requestHandler = DefaultRequestHandler.create(
            agentExecutor, taskStore, queueManager, pushStore,
            busProcessor, executorService, executorService);

        // Create REST handler
        AgentCard agentCard = agentCardProducer.getAgentCard();
        RestHandler restHandler = new RestHandler(agentCard, requestHandler, executorService);

        // Register endpoints
        String basePath = restPath;

        // Agent card endpoint (public discovery)
        httpServer.createContext(basePath + "/.well-known/agent.json", exchange -> {
            handleRestCall(exchange, () -> restHandler.getAgentCard());
        });

        // Message endpoint
        httpServer.createContext(basePath, exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            // Create server call context (unauthenticated for now)
            ServerCallContext callContext = new ServerCallContext(
                null, new HashMap<>(), Collections.emptySet());

            if ("POST".equals(method) && basePath.equals(path)) {
                String body = readRequestBody(exchange);
                handleRestCall(exchange, () ->
                    restHandler.sendMessage(callContext, null, body));
            } else if ("GET".equals(method) && path.startsWith(basePath + "/tasks/")) {
                String taskId = path.substring((basePath + "/tasks/").length());
                if (taskId.endsWith("/cancel")) {
                    exchange.sendResponseHeaders(405, -1);
                } else {
                    handleRestCall(exchange, () ->
                        restHandler.getTask(callContext, taskId, null, null));
                }
            } else if ("POST".equals(method) && path.matches(basePath + "/tasks/.+/cancel")) {
                String taskId = path.replace(basePath + "/tasks/", "").replace("/cancel", "");
                handleRestCall(exchange, () ->
                    restHandler.cancelTask(callContext, taskId, null));
            } else {
                sendNotFound(exchange);
            }
        });

        LOGGER.info("YAWL A2A HTTP Server created successfully");
        return httpServer;
    }

    /**
     * Starts the A2A server when the application is ready.
     *
     * @param event the application ready event
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startServer(ApplicationReadyEvent event) {
        if (httpServer != null) {
            LOGGER.info("Starting YAWL A2A HTTP Server...");
            httpServer.start();
            LOGGER.info("YAWL A2A HTTP Server started on port {}", restPort);
            LOGGER.info("Agent card available at: http://localhost:{}{}/.well-known/agent.json",
                       restPort, restPath);
        }
    }

    /**
     * Stops the A2A server on application shutdown.
     */
    @PreDestroy
    public void shutdown() {
        LOGGER.info("Shutting down YAWL A2A HTTP Server...");

        if (httpServer != null) {
            httpServer.stop(2);
            httpServer = null;
        }

        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }

        LOGGER.info("YAWL A2A HTTP Server shut down complete");
    }

    /**
     * Gets the REST port.
     *
     * @return the REST port
     */
    public int getRestPort() {
        return restPort;
    }

    /**
     * Gets the REST path.
     *
     * @return the REST path
     */
    public String getRestPath() {
        return restPath;
    }

    // =========================================================================
    // Private helper methods
    // =========================================================================

    @FunctionalInterface
    private interface RestCallable {
        RestHandler.HTTPRestResponse call() throws Exception;
    }

    private void handleRestCall(com.sun.net.httpserver.HttpExchange exchange, RestCallable callable)
            throws IOException {
        try {
            RestHandler.HTTPRestResponse response = callable.call();
            String body = response.getBody();
            String contentType = response.getContentType();
            int statusCode = response.getStatusCode();

            byte[] bodyBytes = body != null
                ? body.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(statusCode, bodyBytes.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
            }
        } catch (Exception e) {
            byte[] err = ("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, err.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(err);
            }
        }
    }

    private String readRequestBody(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try (java.io.InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void sendNotFound(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        byte[] resp = "{\"error\":\"Not Found\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(404, resp.length);
        try (java.io.OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
        }
    }
}
