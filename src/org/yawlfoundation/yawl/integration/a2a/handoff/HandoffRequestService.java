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

package org.yawlfoundation.yawl.integration.a2a.handoff;

import org.yawlfoundation.yawl.integration.a2a.AgentInfo;
import org.yawlfoundation.yawl.elements.YAWLServiceGateway;
import org.yawlfoundation.yawl.elements.YAWLTask;
import org.yawlfoundation.yawl.elements.YAWLNet;
import org.yawlfoundation.yawl.engine.YAWLEngine;
import org.yawlfoundation.yawl.engine.YAWLServiceGatewayException;
import org.yawlfoundation.yawl.engine.interfce.InterfaceBWebServiceGateway;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.AgentContext;
import org.yawlfoundation.yawl.integration.autonomous.AgentInfoStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Service for initiating and managing agent-to-agent handoff requests.
 * This service coordinates the transfer of workflow work items between
 * autonomous agents when the current agent cannot complete the task.
 *
 * <p>The handoff process follows this sequence:
 * 1. Source agent calls {@code initiateHandoff} with work item details
 * 2. Service locates capable substitute agents
 * 3. Service generates handoff token and message
 * 4. Service sends handoff request to target agent
 * 5. Service waits for acknowledgment
 * 6. Service coordinates with YAWL engine for checkout transfer
 *
 * @since YAWL 6.0
 */
public class HandoffRequestService {

    private final HandoffProtocol handoffProtocol;
    private final YAWLEngine yawlEngine;
    private final AgentContext agentContext;
    private final AgentInfoStore agentInfoStore;
    private final HttpClient httpClient;
    private final ExecutorService executorService;

    /**
     * Creates a new handoff request service.
     *
     * @param agentContext the context of the current agent
     * @param yawlEngine the YAWL engine instance
     * @param agentInfoStore the store for agent information
     */
    public HandoffRequestService(AgentContext agentContext,
                                YAWLEngine yawlEngine,
                                AgentInfoStore agentInfoStore) {
        this.agentContext = agentContext;
        this.yawlEngine = yawlEngine;
        this.agentInfoStore = agentInfoStore;
        this.handoffProtocol = new HandoffProtocol();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Initiates a handoff of the specified work item to another capable agent.
     *
     * @param workItemId the ID of the work item to hand off
     * @param agentContext the context of the current agent
     * @param sourceAgentId the ID of the source agent initiating the handoff
     * @return a future containing the handoff result
     * @throws HandoffException if the handoff cannot be initiated
     */
    public CompletableFuture<HandoffResult> initiateHandoff(String workItemId,
                                                           AgentContext agentContext,
                                                           String sourceAgentId) throws HandoffException {

        // First, verify the work item exists and is checked out by the current agent
        WorkItemRecord workItem = validateWorkItem(workItemId, sourceAgentId);

        // Locate capable substitute agents
        List<AgentInfo> substituteAgents = findSubstituteAgents(workItem);

        if (substituteAgents.isEmpty()) {
            throw new HandoffException("No substitute agents available for work item: " + workItemId);
        }

        // Select the first capable agent (simple strategy - could be enhanced with scoring)
        AgentInfo targetAgent = substituteAgents.get(0);

        // Generate handoff token
        HandoffToken handoffToken = handoffProtocol.generateHandoffToken(
            workItemId,
            sourceAgentId,
            targetAgent.getId(),
            workItem.getSessionID()
        );

        // Create handoff message
        String handoffMessage = handoffProtocol.createHandoffMessage(handoffToken);

        // Send handoff request to target agent
        return sendHandoffRequest(targetAgent, handoffMessage, handoffToken);
    }

    /**
     * Validates that the work item exists and is checked out by the specified agent.
     */
    private WorkItemRecord validateWorkItem(String workItemId, String agentId) throws HandoffException {
        try {
            WorkItemRecord workItem = yawlEngine.getWorkItem(workItemId);
            if (workItem == null) {
                throw new HandoffException("Work item not found: " + workItemId);
            }

            // Verify the work item is checked out by the specified agent
            if (!agentId.equals(workItem.getSpecifiedAgent())) {
                throw new HandoffException("Work item not checked out by agent: " + agentId);
            }

            return workItem;
        } catch (Exception e) {
            throw new HandoffException("Failed to validate work item: " + workItemId, e);
        }
    }

    /**
     * Finds substitute agents capable of handling the work item.
     */
    private List<AgentInfo> findSubstituteAgents(WorkItemRecord workItem) {
        // Get the task associated with this work item
        YAWLTask task = findTaskForWorkItem(workItem);
        if (task == null) {
            return List.of();
        }

        // Find agents capable of handling this task type
        return agentInfoStore.findAgentsForCapability(task.getName());
    }

    /**
     * Finds the YAWL task associated with a work item.
     */
    private YAWLTask findTaskForWorkItem(WorkItemRecord workItem) {
        try {
            YAWLNet net = yawlEngine.getSpecification(workItem.getSpecificationID()).getNet();
            for (YAWLTask task : net.getTasks()) {
                if (task.getName().equals(workItem.getElementName())) {
                    return task;
                }
            }
        } catch (Exception e) {
            // Log error but return null
            System.err.println("Error finding task for work item: " + e.getMessage());
        }
        return null;
    }

    /**
     * Sends a handoff request to the target agent and waits for response.
     */
    private CompletableFuture<HandoffResult> sendHandoffRequest(AgentInfo targetAgent,
                                                              String handoffMessage,
                                                              HandoffToken handoffToken) {

        // Build the handoff request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(targetAgent.getUrl() + "/handoff"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + handoffToken.getJwt())
            .POST(HttpRequest.BodyPublishers.ofString(handoffMessage))
            .build();

        // Send asynchronously
        CompletableFuture<HttpResponse<String>> responseFuture = httpClient.sendAsync(request,
            HttpResponse.BodyHandlers.ofString());

        return responseFuture.thenApply(response -> {
            if (response.statusCode() == 200) {
                return new HandoffResult(true, "Handoff accepted by " + targetAgent.getId());
            } else {
                return new HandoffResult(false, "Handoff rejected: HTTP " + response.statusCode());
            }
        }).exceptionally(throwable -> {
            if (throwable.getCause() instanceof TimeoutException) {
                return new HandoffResult(false, "Handoff request timed out");
            } else if (throwable.getCause() instanceof IOException) {
                return new HandoffResult(false, "Network error: " + throwable.getCause().getMessage());
            } else {
                return new HandoffResult(false, "Handoff failed: " + throwable.getMessage());
            }
        });
    }

    /**
     * Represents the result of a handoff operation.
     */
    public static class HandoffResult {
        private final boolean accepted;
        private final String message;

        public HandoffResult(boolean accepted, String message) {
            this.accepted = accepted;
            this.message = message;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Exception thrown when a handoff operation fails.
     */
    public static class HandoffException extends Exception {
        public HandoffException(String message) {
            super(message);
        }

        public HandoffException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}