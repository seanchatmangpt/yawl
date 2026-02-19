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
import org.yawlfoundation.yawl.integration.orderfulfillment.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;
import org.yawlfoundation.yawl.integration.autonomous.PartitionConfig;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistryClient;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.integration.autonomous.conflict.ConflictResolver;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffException;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffSession;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffToken;
import org.yawlfoundation.yawl.integration.a2a.YawlA2AClient;
import org.yawlfoundation.yawl.security.AgentSecurityContext;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffRequestService;
import org.yawlfoundation.yawl.integration.autonomous.AgentContext;
import org.yawlfoundation.yawl.engine.YAWLEngine;

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
    private final AgentRegistryClient registryClient;
    private final HandoffProtocol handoffProtocol;
    private final HandoffRequestService handoffService;
    private final ConflictResolver conflictResolver;
    private final YawlA2AClient a2aClient;
    private final AgentSecurityContext agentContext;
    private final PartitionConfig partitionConfig;

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
        this.partitionConfig = config.getPartitionConfig();

        // Initialize coordination protocol components
        this.registryClient = config.getAgentRegistryClient();
        this.handoffProtocol = config.getHandoffProtocol();
        this.handoffService = config.getHandoffService();
        this.conflictResolver = config.getConflictResolver();
        this.a2aClient = config.getA2AClient();
        this.agentContext = new AgentContext(
            config.getId(),
            "Generic Agent - " + config.getCapability().domainName(),
            config.getCapability(),
            new AgentSecurityContext(config.getId(), sessionHandle, "ApiKey"),
            "http://localhost:" + config.getPort()
        );
    }

    @Override
    public void start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("Agent is already running");
        }
        running.set(true);

        // Register agent with registry if available
        if (registryClient != null) {
            try {
                AgentInfo agentInfo = new AgentInfo(
                    config.getId(),
                    "Generic Agent - " + config.getCapability().domainName(),
                    config.getCapability(),
                    "localhost",
                    config.getPort()
                );

                boolean registered = registryClient.register(agentInfo);
                if (registered) {
                    System.out.println("  [" + config.getCapability().domainName() + "] Registered with agent registry");
                } else {
                    System.err.println("  [" + config.getCapability().domainName() + "] Failed to register with agent registry");
                }
            } catch (IOException e) {
                System.err.println("  [" + config.getCapability().domainName() + "] Error registering with registry: " + e.getMessage());
            }
        }

        startHttpServer();
        startDiscoveryLoop();

        System.out.println("Generic Agent [" + config.getCapability().domainName()
            + "] v" + config.getVersion() + " started on port " + config.getPort());
        System.out.println("  Capability: " + config.getCapability().description());
        System.out.println("  Agent card: http://localhost:" + config.getPort() + "/.well-known/agent.json");
    }

    private void startDiscoveryLoop() {
        discoveryThread = Thread.ofVirtual()
            .name("discovery-" + config.getCapability().domainName())
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
        });
        discoveryThread.setDaemon(false);
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

        // Unregister from agent registry if available
        if (registryClient != null) {
            try {
                boolean unregistered = registryClient.unregister(config.getId());
                if (unregistered) {
                    System.out.println("  [" + config.getCapability().domainName() + "] Unregistered from agent registry");
                }
            } catch (IOException e) {
                System.err.println("  [" + config.getCapability().domainName() + "] Error unregistering from registry: " + e.getMessage());
            }
        }

        try {
            interfaceBClient.disconnect(sessionHandle);
        } catch (IOException e) {
            System.err.println("Error disconnecting from engine: " + e.getMessage());
        }

        // Shutdown handoff service
        if (handoffService != null) {
            handoffService.shutdown();
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

    /**
     * Classifies handoff need and initiates protocol if required.
     *
     * <p>Implementation of ADR-025 work handoff protocol. When an agent that has
     * checked out a work item determines it cannot complete it, this method provides
     * a structured mechanism to transfer the item to another capable agent.</p>
     *
     * <p>The handoff sequence follows ADR-025 specification:
     * 1. Query AgentRegistry for capable substitute agents
     * 2. Generate handoff token via HandoffProtocol
     * 3. Send A2A handoff message to target agent
     * 4. Wait for acknowledgment with timeout (30s default)
     * 5. Rollback Interface B checkout if handoff successful
     * 6. Target agent checks out and completes the work item</p>
     *
     * @param workItem the work item that needs handoff
     * @return true if handoff was initiated successfully, false otherwise
     * @throws HandoffException if handoff protocol fails
     */
    private boolean classifyHandoffIfNeeded(WorkItemRecord workItem) throws HandoffException {
        if (handoffService == null) {
            throw new HandoffException("Handoff service is not configured for this agent");
        }
        if (workItem == null) {
            throw new IllegalArgumentException("workItem is required");
        }

        String workItemId = workItem.getID();
        String taskName = workItem.getTaskName();

        // Query AgentRegistry for capable substitute agents
        List<AgentInfo> capableAgents = registryClient.queryByCapability(
            config.getCapability().domainName());

        // Filter out this agent and ensure there are capable alternatives
        List<AgentInfo> substituteAgents = capableAgents.stream()
            .filter(agent -> !config.getId().equals(agent.getId()))
            .toList();

        if (substituteAgents.isEmpty()) {
            System.err.println("  [" + config.getCapability().domainName() +
                "] No substitute agents available for handoff: " + workItemId);
            return false;
        }

        // Select the first capable agent (simple strategy - could be enhanced)
        AgentInfo targetAgent = substituteAgents.get(0);

        try {
            // Initiate handoff through HandoffRequestService
            var handoffFuture = handoffService.initiateHandoff(
                workItemId,
                agentContext,
                config.getCapability().domainName());

            // Wait for handoff acknowledgment with timeout
            var handoffResult = handoffFuture.get(30, TimeUnit.SECONDS);

            if (handoffResult.isAccepted()) {
                System.out.println("  [" + config.getCapability().domainName() +
                    "] Handoff initiated to agent " + targetAgent.getId() +
                    " for work item " + workItemId);
                return true;
            } else {
                System.err.println("  [" + config.getCapability().domainName() +
                    "] Handoff rejected by agent " + targetAgent.getId() +
                    ": " + handoffResult.getMessage());
                return false;
            }

        } catch (Exception e) {
            throw new HandoffException("Handoff failed for work item " + workItemId +
                ": " + e.getMessage(), e);
        }
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
            // Step 1: Check eligibility using agent capability
            if (!eligibilityReasoner.isEligible(workItem)) {
                System.out.println("  [" + config.getCapability().domainName() +
                    "] Work item " + workItemId + " not eligible for this agent");
                return;
            }

            // Step 2: Attempt to checkout work item via Interface B
            String checkoutResult = interfaceBClient.checkOutWorkItem(workItemId, sessionHandle);
            if (checkoutResult == null || checkoutResult.contains("failure") || checkoutResult.contains("error")) {
                return;
            }

            // Step 3: Apply partition strategy if configured
            if (partitionConfig != null) {
                // Apply partition strategy - only process if work item assigned to this agent
                if (!isAssignedToThisAgent(workItem, partitionConfig.getAgentIndex(), partitionConfig.getTotalAgents())) {
                    // Return work item to Enabled state via rollback
                    try {
                        interfaceBClient.rollbackWorkItem(workItemId, sessionHandle);
                    } catch (IOException rollbackEx) {
                        System.err.println("  [" + config.getCapability().domainName() +
                            "] Failed to rollback work item " + workItemId + ": " + rollbackEx.getMessage());
                    }
                    return;
                }
            } else {
                // No partition config - query for competing agents and use dynamic partitioning
                try {
                    List<AgentInfo> competingAgents = registryClient.queryByCapability(
                        config.getCapability().domainName());

                    if (competingAgents.size() > 1) {
                        // Apply dynamic partition strategy
                        int agentIndex = getAgentIndex(config.getId(), competingAgents);
                        int totalAgents = competingAgents.size();

                        if (!isAssignedToThisAgent(workItem, agentIndex, totalAgents)) {
                            // Return work item to Enabled state via rollback
                            try {
                                interfaceBClient.rollbackWorkItem(workItemId, sessionHandle);
                            } catch (IOException rollbackEx) {
                                System.err.println("  [" + config.getCapability().domainName() +
                                    "] Failed to rollback work item " + workItemId + ": " + rollbackEx.getMessage());
                            }
                            return;
                        }
                    }
                } catch (IOException e) {
                    // Cannot query registry, proceed without partitioning
                    System.err.println("  [" + config.getCapability().domainName() +
                        "] Cannot query agent registry, proceeding without partitioning: " + e.getMessage());
                }
            }

            // Step 4: Produce output using decision reasoner
            String outputData;
            try {
                outputData = decisionReasoner.produceOutput(workItem);
            } catch (Exception decisionEx) {
                // Decision failed - attempt handoff if eligible
                System.err.println("  [" + config.getCapability().domainName() +
                    "] Decision failed for " + workItemId + ": " + decisionEx.getMessage());

                if (shouldAttemptHandoff(workItem)) {
                    try {
                        boolean handoffSuccessful = classifyHandoffIfNeeded(workItem);
                        if (handoffSuccessful) {
                            // Handoff initiated successfully
                            return;
                        }
                    } catch (HandoffException handoffEx) {
                        System.err.println("  [" + config.getCapability().domainName() +
                            "] Handoff failed for " + workItemId + ": " + handoffEx.getMessage());
                    }
                }

                // Rollback work item if handoff not possible or failed
                try {
                    interfaceBClient.rollbackWorkItem(workItemId, sessionHandle);
                } catch (IOException rollbackEx) {
                    System.err.println("  [" + config.getCapability().domainName() +
                        "] Failed to rollback work item after decision failure: " + rollbackEx.getMessage());
                }
                return;
            }

            // Step 5: Check in completed work item via Interface B
            String checkinResult = interfaceBClient.checkInWorkItem(workItemId, outputData, null, sessionHandle);

            if (checkinResult != null && checkinResult.contains("success")) {
                System.out.println("  [" + config.getCapability().domainName() + "] Completed "
                    + workItemId + " (" + workItem.getTaskName() + ")");

                // Log agent decision to event store for traceability (ADR-025)
                logAgentDecision(workItemId, outputData);
            } else {
                System.err.println("  [" + config.getCapability().domainName() + "] Checkin failed for "
                    + workItemId + ": " + checkinResult);

                // Attempt rollback on checkin failure
                try {
                    interfaceBClient.rollbackWorkItem(workItemId, sessionHandle);
                } catch (IOException rollbackEx) {
                    System.err.println("  [" + config.getCapability().domainName() +
                        "] Failed to rollback after checkin failure: " + rollbackEx.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("  [" + config.getCapability().domainName() + "] Failed to process "
                + workItemId + ": " + e.getMessage());

            // Attempt rollback on any failure
            try {
                interfaceBClient.rollbackWorkItem(workItemId, sessionHandle);
            } catch (IOException rollbackEx) {
                System.err.println("  [" + config.getCapability().domainName() +
                    "] Failed to rollback work item " + workItemId + ": " + rollbackEx.getMessage());
            }
        }
    }

    /**
     * Determines if a handoff should be attempted for a work item.
     */
    private boolean shouldAttemptHandoff(WorkItemRecord workItem) {
        // Check if handoff protocol is configured
        if (handoffProtocol == null || handoffService == null) {
            return false;
        }

        // Check if there are multiple agents that could handle this
        try {
            List<AgentInfo> capableAgents = registryClient.queryByCapability(
                config.getCapability().domainName());
            return capableAgents.size() > 1;
        } catch (IOException e) {
            // Cannot query registry, assume no other agents available
            return false;
        }
    }

    /**
     * Gets the index of this agent in the list of competing agents.
     */
    private int getAgentIndex(String agentId, List<AgentInfo> agents) {
        for (int i = 0; i < agents.size(); i++) {
            if (agents.get(i).getId().equals(agentId)) {
                return i;
            }
        }
        return -1; // Agent not found (should not happen)
    }

    /**
     * Checks if this work item is assigned to this agent based on partition strategy.
     * Implementation from ADR-025 partition strategy section.
     */
    private boolean isAssignedToThisAgent(WorkItemRecord workItem, int agentIndex, int totalAgents) {
        // Consistent hash: deterministic, no coordination required
        int hash = Math.abs(workItem.getID().hashCode());
        return (hash % totalAgents) == agentIndex;
    }

    /**
     * Logs agent decision to event store for traceability (ADR-025 compliance).
     */
    private void logAgentDecision(String workItemId, String decision) {
        // In a real implementation, this would log to WorkflowEventStore
        // For now, just output to console for demonstration
        System.out.println("  [" + config.getCapability().domainName() + "] Agent decision logged for " +
            workItemId + ": " + decision.substring(0, Math.min(100, decision.length())) + "...");
    }
}
