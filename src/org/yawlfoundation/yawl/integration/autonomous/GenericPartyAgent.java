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

package org.yawlfoundation.yawl.integration.autonomous;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import com.sun.net.httpserver.HttpServer;

/**
 * Generic autonomous agent for config-driven workflow task completion.
 *
 * <p>Extends the deprecated {@link org.yawlfoundation.yawl.integration.orderfulfillment.PartyAgent}
 * with pluggable strategies for discovery, eligibility reasoning, and decision production.
 * Suitable for any domain once configured with appropriate strategies.
 *
 * <p>The agent:
 * <ul>
 *   <li>Polls the YAWL engine for available work items at configurable intervals</li>
 *   <li>Uses injected {@link org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy}
 *       to discover eligible work items</li>
 *   <li>Uses injected {@link org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner}
 *       to filter items matching agent capabilities</li>
 *   <li>Uses injected {@link org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner}
 *       to produce output for eligible items</li>
 *   <li>Checks out, processes, and checks in work items</li>
 *   <li>Exposes {@code /.well-known/agent.json}, {@code /health}, and {@code /capacity} endpoints</li>
 *   <li>Uses virtual threads for the discovery loop and HTTP server</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
public final class GenericPartyAgent {

    private static final Logger logger = LogManager.getLogger(GenericPartyAgent.class);

    private final AgentConfiguration config;
    private final InterfaceB_EnvironmentBasedClient ibClient;
    private final AtomicReference<AgentLifecycle> lifecycle;
    private final AtomicBoolean running;

    private HttpServer httpServer;
    private Thread discoveryThread;
    private String sessionHandle;

    /**
     * Create a generic autonomous agent from the given configuration.
     *
     * @param config the agent configuration with all strategies and credentials
     * @throws IOException if engine connection fails
     */
    public GenericPartyAgent(AgentConfiguration config) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("AgentConfiguration is required");
        }

        this.config = config;
        this.lifecycle = new AtomicReference<>(AgentLifecycle.CREATED);
        this.running = new AtomicBoolean(false);

        // Build Interface B URL
        String engineUrl = config.getEngineUrl();
        String interfaceBUrl = engineUrl.endsWith("/")
            ? engineUrl + "ib"
            : engineUrl + "/ib";

        this.ibClient = new InterfaceB_EnvironmentBasedClient(interfaceBUrl);

        // Attempt connection to engine
        String session = ibClient.connect(config.getUsername(), config.getPassword());
        if (session == null || session.contains("failure") || session.contains("error")) {
            throw new IOException("Failed to connect to YAWL engine: " + session);
        }
        this.sessionHandle = session;

        logger.info("GenericPartyAgent [{}] initialized with session {}", config.getAgentName(), sessionHandle);
    }

    /**
     * Start the agent: HTTP server for discovery + discovery polling loop.
     *
     * @throws IOException if HTTP server startup fails
     */
    public void start() throws IOException {
        if (running.getAndSet(true)) {
            logger.warn("Agent [{}] is already running", config.getAgentName());
            return;
        }

        lifecycle.set(AgentLifecycle.INITIALIZING);
        startHttpServer();
        lifecycle.set(AgentLifecycle.DISCOVERING);
        startDiscoveryLoop();

        logger.info("GenericPartyAgent [{}] v{} started on port {}",
            config.getAgentName(), config.version(), config.port());
        logger.info("  Capability: {}", config.getCapability().getDescription());
        logger.info("  Agent card: http://localhost:{}/.well-known/agent.json", config.port());
    }

    /**
     * Stop the agent: halt discovery loop, close HTTP server, disconnect from engine.
     */
    public void stop() {
        running.set(false);
        lifecycle.set(AgentLifecycle.STOPPING);

        if (discoveryThread != null) {
            discoveryThread.interrupt();
            try {
                discoveryThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (httpServer != null) {
            httpServer.stop(2);
            httpServer = null;
        }

        if (sessionHandle != null) {
            try {
                ibClient.disconnect(sessionHandle);
            } catch (IOException e) {
                logger.warn("Failed to disconnect from engine: {}", e.getMessage(), e);
            }
        }

        lifecycle.set(AgentLifecycle.STOPPED);
        logger.info("GenericPartyAgent [{}] stopped", config.getAgentName());
    }

    /**
     * Get the current lifecycle state of the agent.
     *
     * @return the current AgentLifecycle state
     */
    public AgentLifecycle getLifecycle() {
        return lifecycle.get();
    }

    // =========================================================================
    // Private: HTTP Server Setup
    // =========================================================================

    private void startHttpServer() throws IOException {
        String agentCard = buildAgentCardJson();

        httpServer = HttpServer.create(new InetSocketAddress(config.port()), 0);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        // /.well-known/agent.json endpoint
        httpServer.createContext("/.well-known/agent.json", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] body = agentCard.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // /health endpoint
        httpServer.createContext("/health", exchange -> {
            String json = """
                {"status":"ok","agent":"%s","lifecycle":"%s"}""".formatted(
                config.getAgentName(), lifecycle.get());
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // /capacity endpoint
        httpServer.createContext("/capacity", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String json = """
                {"domain":"%s","available":true,"capacity":"normal"}""".formatted(
                config.getCapability().getDomainName());
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        httpServer.start();
    }

    private String buildAgentCardJson() {
        return """
            {
              "name": "%s Agent",
              "description": "Autonomous agent for %s. Discovers work items, reasons about eligibility, produces output dynamically.",
              "version": "%s",
              "capabilities": {"domain": "%s"},
              "skills": [
                {
                  "id": "complete_work_item",
                  "name": "Complete Work Item",
                  "description": "Discover and complete workflow tasks in this agent's domain"
                }
              ]
            }""".formatted(
                config.getCapability().getDomainName(),
                config.getCapability().getDescription(),
                config.version(),
                config.getCapability().getDomainName());
    }

    // =========================================================================
    // Private: Discovery Loop
    // =========================================================================

    private void startDiscoveryLoop() {
        discoveryThread = Thread.ofVirtual()
            .name("discovery-" + config.getAgentName())
            .start(() -> {
                while (running.get()) {
                    try {
                        runDiscoveryCycle();
                    } catch (Exception e) {
                        logger.error("Discovery cycle error for agent [{}]: {}",
                            config.getAgentName(), e.getMessage(), e);
                    }
                    try {
                        TimeUnit.MILLISECONDS.sleep(config.pollIntervalMs());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
    }

    private void runDiscoveryCycle() throws IOException {
        // Discover available work items using configured strategy
        List<WorkItemRecord> items = config.discoveryStrategy()
            .discoverWorkItems(ibClient, sessionHandle);

        if (items == null || items.isEmpty()) {
            return;
        }

        for (WorkItemRecord workItem : items) {
            if (!running.get()) {
                break;
            }

            // Skip non-live items
            if (!workItem.hasLiveStatus()) {
                continue;
            }

            // Skip parent items (containers)
            if (workItem.getStatus().equals(WorkItemRecord.statusIsParent)) {
                continue;
            }

            processWorkItem(workItem);
        }
    }

    private void processWorkItem(WorkItemRecord workItem) {
        String workItemId = workItem.getID();
        String taskName = workItem.getTaskName();

        try {
            // Check eligibility using configured reasoner
            if (!config.eligibilityReasoner().isEligible(workItem)) {
                logger.debug("[{}] Work item {} ({}) not eligible",
                    config.getAgentName(), workItemId, taskName);
                return;
            }

            logger.debug("[{}] Work item {} ({}) is eligible",
                config.getAgentName(), workItemId, taskName);

            // Attempt checkout
            String checkoutResult = ibClient.checkOutWorkItem(workItemId, sessionHandle);
            if (checkoutResult == null || checkoutResult.contains("failure")
                    || checkoutResult.contains("error")) {
                logger.warn("[{}] Failed to checkout work item {}: {}",
                    config.getAgentName(), workItemId, checkoutResult);
                return;
            }

            logger.debug("[{}] Checked out work item {}", config.getAgentName(), workItemId);

            // Produce output using configured reasoner
            String outputData = config.decisionReasoner().produceOutput(workItem);

            // Check in with output
            String checkinResult = ibClient.checkInWorkItem(
                workItemId, outputData, null, sessionHandle);

            if (checkinResult != null && checkinResult.contains("success")) {
                logger.info("[{}] Completed work item {} ({})",
                    config.getAgentName(), workItemId, taskName);
            } else {
                logger.warn("[{}] Check-in did not return success for work item {}: {}",
                    config.getAgentName(), workItemId, checkinResult);
            }

        } catch (Exception e) {
            logger.error("[{}] Failed to process work item {}: {}",
                config.getAgentName(), workItemId, e.getMessage(), e);
        }
    }

    /**
     * Entry point for testing and manual execution of a generic autonomous agent.
     * Reads configuration from YAML file and starts the agent.
     *
     * <p>Required: CONFIG_FILE environment variable pointing to agent YAML configuration.
     * Uses {@link YamlAgentConfigLoader#load(java.nio.file.Path, ZaiService)} to load configuration.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        String configFile = System.getenv("CONFIG_FILE");
        if (configFile == null || configFile.isEmpty()) {
            logger.error("CONFIG_FILE environment variable is required");
            System.exit(1);
            return;
        }

        try {
            // For simplicity in main(), use null ZaiService (assuming static reasoning)
            // In production, load ZaiService if needed
            java.nio.file.Path configPath = java.nio.file.Paths.get(configFile);
            java.nio.file.Path configDir = configPath.getParent();

            List<AgentConfiguration> configs = YamlAgentConfigLoader.load(configDir, null);
            if (configs.isEmpty()) {
                logger.error("No agent configurations loaded from {}", configDir);
                System.exit(1);
                return;
            }

            // Use first config
            AgentConfiguration config = configs.get(0);
            GenericPartyAgent agent = new GenericPartyAgent(config);
            agent.start();

            // Graceful shutdown hook
            Runtime.getRuntime().addShutdownHook(
                Thread.ofVirtual().unstarted(() -> {
                    logger.info("Shutdown signal received, stopping agent...");
                    agent.stop();
                })
            );

            // Keep running
            Thread.currentThread().join();

        } catch (Exception e) {
            logger.fatal("Failed to start agent: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
