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

package org.yawlfoundation.yawl.integration.orderfulfillment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Autonomous party agent for order fulfillment simulation.
 *
 * Polls YAWL engine for work items, uses stateless workflows (ZAI) to determine
 * eligibility and produce output, then completes work items. No central
 * orchestrator; each agent figures out what to do dynamically.
 *
 * Exposes /.well-known/agent.json for A2A discovery.
 *
 * @author YAWL Foundation
 * @version 5.2
 * @deprecated Use {@link org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent} instead.
 *             This class is specific to orderfulfillment and will be removed in a future version.
 */
@Deprecated
public final class PartyAgent {


    private static final Logger logger = LogManager.getLogger(PartyAgent.class);
    private static final String VERSION = "5.2.0";
    private static final long POLL_INTERVAL_MS = 3000;

    private final AgentCapability capability;
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final EligibilityWorkflow eligibilityWorkflow;
    private final DecisionWorkflow decisionWorkflow;
    private final int port;
    private final String sessionHandle;

    private HttpServer httpServer;
    private Thread discoveryThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PartyAgent(AgentCapability capability,
                      String engineUrl,
                      String username,
                      String password,
                      int port) throws IOException {
        if (capability == null) {
            throw new IllegalArgumentException("capability is required");
        }
        if (engineUrl == null || engineUrl.isEmpty()) {
            throw new IllegalArgumentException("engineUrl is required");
        }
        if (username == null || password == null) {
            throw new IllegalArgumentException("username and password are required");
        }

        this.capability = capability;
        this.port = port > 0 ? port : 8091;

        String interfaceBUrl = engineUrl.endsWith("/") ? engineUrl + "ib" : engineUrl + "/ib";
        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(interfaceBUrl);

        String session = interfaceBClient.connect(username, password);
        if (session == null || session.contains("failure") || session.contains("error")) {
            throw new IOException("Failed to connect to YAWL engine: " + session);
        }
        this.sessionHandle = session;

        String zaiKey = System.getenv("ZAI_API_KEY");
        ZaiService zaiService = (zaiKey != null && !zaiKey.isEmpty())
            ? new ZaiService(zaiKey)
            : null;

        if (zaiService == null) {
            throw new IllegalStateException(
                "ZAI_API_KEY is required for autonomous agent reasoning.");
        }

        this.eligibilityWorkflow = new EligibilityWorkflow(capability, zaiService);
        McpTaskContextSupplier mcpSupplier = createMcpSupplier();
        this.decisionWorkflow = new DecisionWorkflow(zaiService, mcpSupplier);
    }

    private static McpTaskContextSupplier createMcpSupplier() {
        if (!"true".equalsIgnoreCase(System.getenv("MCP_ENABLED"))) {
            logger.info("MCP integration disabled (MCP_ENABLED not set to 'true'); proceeding without task context.");
            return null;
        }
        try {
            McpTaskContextSupplierImpl impl = new McpTaskContextSupplierImpl(null, null);
            impl.connect();
            return impl;
        } catch (Exception e) {
            logger.error("MCP task context supplier initialization failed; order fulfillment agent will proceed without MCP context. Cause: {}",
                    e.getMessage(), e);
            return null;
        }
    }

    /**
     * Start the agent: HTTP server for discovery + discovery loop.
     */
    public void start() throws IOException {
        if (running.get()) {
            return;
        }
        running.set(true);

        startHttpServer();
        startDiscoveryLoop();

        logger.info("Party Agent [{}] v{} started on port {}", capability.getDomainName(), VERSION, port);
        logger.info("  Capability: {}", capability.getDescription());
        logger.info("  Agent card: http://localhost:{}/.well-known/agent.json", port);
    }

    /**
     * Stop the agent.
     */
    public void stop() {
        running.set(false);
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
        try {
            interfaceBClient.disconnect(sessionHandle);
        } catch (IOException e) {
            logger.warn("Failed to disconnect: " + e.getMessage(), e);
        }
        logger.info("Party Agent [{}] stopped", capability.getDomainName());
    }

    private void startHttpServer() throws IOException {
        String agentCard = buildAgentCardJson();

        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

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

        httpServer.createContext("/health", exchange -> {
            String json = "{\"status\":\"ok\",\"agent\":\"" + capability.getDomainName() + "\"}";
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        httpServer.createContext("/capacity", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            boolean available = true;
            String agentsUrl = System.getenv("AGENT_PEERS");
            if (agentsUrl != null && !agentsUrl.isEmpty()) {
                available = CapacityChecker.checkPeersAvailable(agentsUrl);
            }
            String json = String.format(
                "{\"domain\":\"%s\",\"available\":%s,\"capacity\":\"normal\"}",
                capability.getDomainName(), available);
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
        return "{"
            + "\"name\":\"Order Fulfillment - " + capability.getDomainName() + " Agent\","
            + "\"description\":\"Autonomous agent for " + capability.getDescription() + ". "
            + "Discovers work items, reasons about eligibility, produces output dynamically.\","
            + "\"version\":\"" + VERSION + "\","
            + "\"capabilities\":{\"domain\":\"" + capability.getDomainName() + "\"},"
            + "\"skills\":[{"
            + "\"id\":\"complete_work_item\","
            + "\"name\":\"Complete Work Item\","
            + "\"description\":\"Discover and complete workflow tasks in this agent's domain\""
            + "}]"
            + "}";
    }

    private void startDiscoveryLoop() {
        discoveryThread = Thread.ofVirtual()
            .name("discovery-" + capability.getDomainName())
            .start(() -> {
            while (running.get()) {
                try {
                    runDiscoveryCycle();
                } catch (Exception e) {
                    logger.error("Discovery cycle error for agent [{}]: {}", capability.getDomainName(), e.getMessage(), e);
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void runDiscoveryCycle() throws IOException {
        List<WorkItemRecord> items = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);
        if (items == null || items.isEmpty()) {
            return;
        }

        for (WorkItemRecord wir : items) {
            if (!running.get()) {
                break;
            }
            if (!wir.hasLiveStatus()) {
                continue;
            }
            if (wir.getStatus().equals(WorkItemRecord.statusIsParent)) {
                continue;
            }

            String workItemId = wir.getID();
            try (AgentTracer.AgentSpan span = AgentTracer.span("work_item", capability.getDomainName(), workItemId)) {
                try (AgentTracer.AgentSpan elSpan = AgentTracer.span("eligibility", capability.getDomainName(), workItemId)) {
                    if (!eligibilityWorkflow.isEligible(wir)) {
                        elSpan.setAttribute("eligible", 0);
                        continue;
                    }
                    elSpan.setAttribute("eligible", 1);
                }

                try (AgentTracer.AgentSpan coSpan = AgentTracer.span("checkout", capability.getDomainName(), workItemId)) {
                    String checkoutResult = interfaceBClient.checkOutWorkItem(workItemId, sessionHandle);
                    if (checkoutResult == null || checkoutResult.contains("failure")
                        || checkoutResult.contains("error")) {
                        coSpan.setAttribute("success", 0);
                        continue;
                    }
                    coSpan.setAttribute("success", 1);
                }

                String outputData;
                try (AgentTracer.AgentSpan decSpan = AgentTracer.span("decision", capability.getDomainName(), workItemId)) {
                    outputData = decisionWorkflow.produceOutput(wir);
                }

                try (AgentTracer.AgentSpan ciSpan = AgentTracer.span("checkin", capability.getDomainName(), workItemId)) {
                    String checkinResult = interfaceBClient.checkInWorkItem(
                        workItemId, outputData, null, sessionHandle);

                    if (checkinResult != null && checkinResult.contains("success")) {
                        ciSpan.setAttribute("success", 1);
                        logger.info("[{}] Completed work item {} ({})", capability.getDomainName(), workItemId, wir.getTaskName());
                    } else {
                        ciSpan.setAttribute("success", 0);
                        logger.warn("[{}] Check-in did not return success for work item {}: {}", capability.getDomainName(), workItemId, checkinResult);
                    }
                }
            } catch (Exception e) {
                logger.error("[{}] Failed to process work item {}: {}", capability.getDomainName(), workItemId, e.getMessage(), e);
            }
        }
    }

    /**
     * Entry point for running a party agent.
     *
     * Required env: AGENT_CAPABILITY, YAWL_ENGINE_URL, YAWL_USERNAME, YAWL_PASSWORD, ZAI_API_KEY
     * Optional: AGENT_PORT (default 8091)
     */
    public static void main(String[] args) {
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        if (engineUrl == null || engineUrl.isEmpty()) {
            engineUrl = "http://localhost:8080/yawl";
        }

        String username = System.getenv("YAWL_USERNAME");
        if (username == null || username.isEmpty()) {
            username = "admin";
        }

        String password = System.getenv("YAWL_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                "YAWL_PASSWORD environment variable must be set. " +
                "See deployment runbook for credential configuration."
            );
        }

        int port = 8091;
        String portStr = System.getenv("AGENT_PORT");
        if (portStr != null && !portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                logger.warn("Invalid number in trace attribute: " + e.getMessage(), e);
            }
        }

        AgentCapability capability;
        try {
            capability = AgentCapability.fromEnvironment();
        } catch (IllegalStateException e) {
            logger.fatal("Agent capability configuration error: {}", e.getMessage(), e);
            logger.fatal("Example: AGENT_CAPABILITY=\"Ordering: procurement, purchase orders\"");
            System.exit(1);
            return;
        }

        PartyAgent agent;
        try {
            agent = new PartyAgent(
                capability, engineUrl, username, password, port);
            agent.start();
        } catch (IOException e) {
            logger.fatal("Failed to start party agent: {}", e.getMessage(), e);
            System.exit(1);
            return;
        }

        Runtime.getRuntime().addShutdownHook(
            Thread.ofVirtual().unstarted(() -> {
                logger.info("Shutdown signal received, stopping party agent...");
                agent.stop();
            })
        );

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            agent.stop();
        }
    }
}
