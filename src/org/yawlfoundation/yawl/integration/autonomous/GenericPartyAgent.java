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

package org.yawlfoundation.yawl.integration.autonomous;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;

import com.sun.net.httpserver.HttpServer;

/**
 * Generic autonomous agent implementation using configurable strategies.
 *
 * Replaces hardcoded PartyAgent with dependency injection and pluggable strategies.
 * Supports any domain through AgentCapability and reasoning strategies.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class GenericPartyAgent implements AutonomousAgent {

    private final AgentConfiguration config;
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final String sessionHandle;
    private final DiscoveryStrategy discoveryStrategy;
    private final EligibilityReasoner eligibilityReasoner;
    private final DecisionReasoner decisionReasoner;

    private HttpServer httpServer;
    private Thread discoveryThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Create a generic autonomous agent.
     *
     * @param config the agent configuration with all dependencies
     * @throws IOException if connection to YAWL engine fails
     */
    public GenericPartyAgent(AgentConfiguration config) throws IOException {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }

        this.config = config;

        String interfaceBUrl = config.getEngineUrl().endsWith("/")
            ? config.getEngineUrl() + "ib"
            : config.getEngineUrl() + "/ib";

        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(interfaceBUrl);

        String session = interfaceBClient.connect(config.getUsername(), config.getPassword());
        if (session == null || session.contains("failure") || session.contains("error")) {
            throw new IOException("Failed to connect to YAWL engine at " + interfaceBUrl + ": " + session);
        }
        this.sessionHandle = session;

        this.discoveryStrategy = config.getDiscoveryStrategy();
        this.eligibilityReasoner = config.getEligibilityReasoner();
        this.decisionReasoner = config.getDecisionReasoner();
    }

    @Override
    public void start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("Agent is already running");
        }
        running.set(true);

        startHttpServer();
        startDiscoveryLoop();

        System.out.println("Generic Agent [" + config.getCapability().domainName()
            + "] v" + config.getVersion() + " started on port " + config.getPort());
        System.out.println("  Capability: " + config.getCapability().description());
        System.out.println("  Agent card: http://localhost:" + config.getPort() + "/.well-known/agent.json");
    }

    private void startDiscoveryLoop() {
        discoveryThread = Thread.ofVirtual()
            .name("GenericPartyAgent-Discovery")
            .start(() -> {
            while (running.get()) {
                try {
                    runDiscoveryCycle();
                } catch (Exception e) {
                    System.err.println("Discovery cycle error: " + e.getMessage());
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(config.getPollIntervalMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "discovery-" + config.getCapability().domainName());
        discoveryThread.setDaemon(false);
        discoveryThread.start();
    }

    private void runDiscoveryCycle() throws IOException {
        List<WorkItemRecord> items = discoveryStrategy.discoverWorkItems(interfaceBClient, sessionHandle);
        if (items == null || items.isEmpty()) {
            return;
        }

        for (WorkItemRecord wir : items) {
            if (!running.get()) {
                break;
            }
            processWorkItem(wir);
        }
    }

    @Override
    public void stop() {
        if (!running.get()) {
            throw new IllegalStateException("Agent is not running");
        }
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
            System.err.println("Error disconnecting from engine: " + e.getMessage());
        }

        System.out.println("Generic Agent [" + config.getCapability().domainName() + "] stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public AgentCapability getCapability() {
        return config.getCapability();
    }

    @Override
    public AgentConfiguration getConfiguration() {
        return config;
    }

    @Override
    public String getAgentCard() {
        return buildAgentCardJson();
    }

    private void startHttpServer() throws IOException {
        String agentCard = buildAgentCardJson();

        httpServer = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
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
            String json = "{\"status\":\"ok\",\"agent\":\""
                + escapeJson(config.getCapability().domainName()) + "\"}";
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
            + "\"name\":\"" + escapeJson("Generic Agent - " + config.getCapability().domainName()) + "\","
            + "\"description\":\"" + escapeJson("Autonomous agent for " + config.getCapability().description()
                + ". Discovers work items, reasons about eligibility, produces output dynamically.") + "\","
            + "\"version\":\"" + escapeJson(config.getVersion()) + "\","
            + "\"capabilities\":{\"domain\":\"" + escapeJson(config.getCapability().domainName()) + "\"},"
            + "\"skills\":[{"
            + "\"id\":\"complete_work_item\","
            + "\"name\":\"Complete Work Item\","
            + "\"description\":\"Discover and complete workflow tasks in this agent's domain\""
            + "}]"
            + "}";
    }

    private static String escapeJson(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Cannot escape null string for JSON");
        }
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private void processWorkItem(WorkItemRecord workItem) {
        if (!running.get()) {
            return;
        }

        if (!workItem.hasLiveStatus()) {
            return;
        }

        if (workItem.getStatus().equals(WorkItemRecord.statusIsParent)) {
            return;
        }

        String workItemId = workItem.getID();

        try {
            if (!eligibilityReasoner.isEligible(workItem)) {
                return;
            }

            String checkoutResult = interfaceBClient.checkOutWorkItem(workItemId, sessionHandle);
            if (checkoutResult == null || checkoutResult.contains("failure") || checkoutResult.contains("error")) {
                return;
            }

            String outputData = decisionReasoner.produceOutput(workItem);

            String checkinResult = interfaceBClient.checkInWorkItem(workItemId, outputData, null, sessionHandle);

            if (checkinResult != null && checkinResult.contains("success")) {
                System.out.println("  [" + config.getCapability().domainName() + "] Completed "
                    + workItemId + " (" + workItem.getTaskName() + ")");
            } else {
                System.err.println("  [" + config.getCapability().domainName() + "] Checkin failed for "
                    + workItemId + ": " + checkinResult);
            }

        } catch (Exception e) {
            System.err.println("  [" + config.getCapability().domainName() + "] Failed to process "
                + workItemId + ": " + e.getMessage());
        }
    }
}
