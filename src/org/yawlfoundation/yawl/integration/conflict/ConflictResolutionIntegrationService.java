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
 * License for more details.
 */

package org.yawlfoundation.yawl.integration.conflict;

import java.util.*;
//import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// Import only core YAWL interfaces
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

/**
 * Integration service that connects conflict resolution with existing YAWL framework.
 *
 * Provides seamless integration between the conflict resolution system and
 * YAWL's autonomous agents, MCP server, and A2A protocols. Handles conflict
 * detection, agent coordination, and workflow integration.
 *
 * <p>Integration points:
 * - Work item completion monitoring
 * - Autonomous agent coordination
 * - MCP server integration
 * - A2A protocol support
 * - YAWL engine interface</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see ConflictResolutionService
 * @see AutonomousAgent
 * @see YawlMcpClient
 */
public class ConflictResolutionIntegrationService {

    private static final ConflictResolutionIntegrationService INSTANCE = new ConflictResolutionIntegrationService();

    private final ConflictResolutionService conflictService;
    private final AgentRegistry agentRegistry;
    private final ConflictDetector conflictDetector;
    private final IntegrationMetrics metrics;
    private final Map<String, CompletableFuture<ConflictResolutionService.ResolutionResult>> pendingResolutions;

    /**
     * Configuration for the integration service.
     */
    private final Map<String, Object> configuration;

    /**
     * Default configuration.
     */
    private static final Map<String, Object> DEFAULT_CONFIGURATION = new HashMap<>();
    static {
        DEFAULT_CONFIGURATION.put("enableAutoDetection", true);
        DEFAULT_CONFIGURATION.put("conflictThreshold", 2);
        DEFAULT_CONFIGURATION.put("maxConcurrentResolutions", 10);
        DEFAULT_CONFIGURATION.put("timeoutMs", 300000);
        DEFAULT_CONFIGURATION.put("retryAttempts", 3);
        DEFAULT_CONFIGURATION.put("enableLogging", true);
        DEFAULT_CONFIGURATION.put("workflowIntegration", true);
        DEFAULT_CONFIGURATION.put("mcpIntegration", true);
        DEFAULT_CONFIGURATION.put("a2aIntegration", true);
    }

    /**
     * Registry for tracking autonomous agents.
     */
    public static class AgentRegistry {
        private final Map<String, AutonomousAgent> agents = new HashMap<>();
        private final Map<String, AgentStatus> agentStatus = new HashMap<>();

        public void registerAgent(String agentId, AutonomousAgent agent) {
            agents.put(agentId, agent);
            agentStatus.put(agentId, new AgentStatus(agentId, AgentStatus.Status.ACTIVE));
        }

        public void unregisterAgent(String agentId) {
            agents.remove(agentId);
            agentStatus.remove(agentId);
        }

        public AutonomousAgent getAgent(String agentId) {
            return agents.get(agentId);
        }

        public Map<String, AgentStatus> getAgentStatus() {
            return new HashMap<>(agentStatus);
        }

        public List<AutonomousAgent> getAvailableAgents() {
            return agents.values().stream()
                .filter(agent -> agentStatus.getOrDefault(agent.toString(), new AgentStatus(null, AgentStatus.Status.INACTIVE)).getStatus() == AgentStatus.Status.ACTIVE)
                .collect(Collectors.toList());
        }
    }

    /**
     * Status information for an agent.
     */
    public static class AgentStatus {
        private final String agentId;
        private final Status status;
        private final long lastActivity;
        private final int resolutionCount;

        public AgentStatus(String agentId, Status status) {
            this(agentId, status, System.currentTimeMillis(), 0);
        }

        public AgentStatus(String agentId, Status status, long lastActivity, int resolutionCount) {
            this.agentId = agentId;
            this.status = status;
            this.lastActivity = lastActivity;
            this.resolutionCount = resolutionCount;
        }

        // Getters and builder methods
        public String getAgentId() { return agentId; }
        public Status getStatus() { return status; }
        public long getLastActivity() { return lastActivity; }
        public int getResolutionCount() { return resolutionCount; }

        public AgentStatus withStatus(Status newStatus) {
            return new AgentStatus(agentId, newStatus, lastActivity, resolutionCount);
        }

        public AgentStatus withResolutionCount(int count) {
            return new AgentStatus(agentId, status, lastActivity, count);
        }

        public enum Status {
            ACTIVE, INACTIVE, BUSY, ERROR, OFFLINE
        }
    }

    /**
     * Service for detecting conflicts between agent decisions.
     */
    public static class ConflictDetector {
        private final int conflictThreshold;

        public ConflictDetector(int conflictThreshold) {
            this.conflictThreshold = conflictThreshold;
        }

        /**
         * Check if a set of agent decisions represents a conflict.
         *
         * @param decisions Decisions from multiple agents
         * @return true if conflict is detected
         */
        public boolean detectConflict(List<AgentDecision> decisions) {
            if (decisions == null || decisions.size() < conflictThreshold) {
                return false;
            }

            // Check for differing decisions
            Set<String> uniqueDecisions = decisions.stream()
                .map(AgentDecision::getDecision)
                .collect(Collectors.toSet());

            // If all agents agree, no conflict
            if (uniqueDecisions.size() == 1) {
                return false;
            }

            // Check for confidence levels that might indicate low-quality conflict
            double avgConfidence = decisions.stream()
                .mapToDouble(AgentDecision::getConfidence)
                .average()
                .orElse(0.0);

            // Consider it a conflict if there are differing decisions and reasonable confidence
            return uniqueDecisions.size() > 1 && avgConfidence > 0.3;
        }

        /**
         * Create a conflict context from work item and agent decisions.
         *
         * @param workItem The work item causing the conflict
         * @param decisions Agent decisions
         * @return Conflict context
         */
        public ConflictContext createConflictContext(WorkItemRecord workItem, List<AgentDecision> decisions) {
            String conflictId = "conflict-" + UUID.randomUUID().toString();
            String workflowId = workItem.getCaseID();
            String taskId = workItem.getID();
            ConflictResolver.Severity severity = determineSeverity(decisions);

            Map<String, Object> contextData = new HashMap<>();
            contextData.put("workItemId", workItem.getID());
            contextData.put("caseId", workItem.getCaseID());
            contextData.put("decompositionId", workItem.getDecompositionID());

            return new ConflictContext(conflictId, workflowId, taskId, severity, decisions, contextData);
        }

        /**
         * Determine severity level based on decision characteristics.
         */
        private ConflictResolver.Severity determineSeverity(List<AgentDecision> decisions) {
            if (decisions == null || decisions.isEmpty()) {
                return ConflictResolver.Severity.MEDIUM;
            }

            // Check for critical indicators
            boolean hasHighConfidenceDisagreement = decisions.stream()
                .filter(d -> d.getConfidence() > 0.8)
                .map(AgentDecision::getDecision)
                .distinct()
                .count() > 1;

            if (hasHighConfidenceDisagreement) {
                return ConflictResolver.Severity.HIGH;
            }

            // Check low confidence across all agents
            double avgConfidence = decisions.stream()
                .mapToDouble(AgentDecision::getConfidence)
                .average()
                .orElse(0.0);

            if (avgConfidence < 0.4) {
                return ConflictResolver.Severity.MEDIUM;
            }

            return ConflictResolver.Severity.LOW;
        }
    }

    /**
     * Metrics collection for integration service.
     */
    public static class IntegrationMetrics {
        private final Map<String, Object> metrics = new HashMap<>();

        public void increment(String metricName) {
            if (metrics.containsKey(metricName)) {
                metrics.put(metricName, (Integer) metrics.get(metricName) + 1);
            } else {
                metrics.put(metricName, 1);
            }
        }

        public void recordDuration(String metricName, long durationMs) {
            String durationKey = metricName + "_duration";
            if (metrics.containsKey(durationKey)) {
                metrics.put(durationKey, (long) metrics.get(durationKey) + durationMs);
            } else {
                metrics.put(durationKey, durationMs);
            }
        }

        public void setGauge(String metricName, double value) {
            metrics.put(metricName, value);
        }

        public Map<String, Object> getMetrics() {
            return new HashMap<>(metrics);
        }

        public void reset() {
            metrics.clear();
        }
    }

    /**
     * Get the singleton instance.
     */
    public static ConflictResolutionIntegrationService getInstance() {
        return INSTANCE;
    }

    /**
     * Private constructor.
     */
    private ConflictResolutionIntegrationService() {
        this.configuration = new HashMap<>(DEFAULT_CONFIGURATION);
        this.conflictService = ConflictResolutionService.getInstance();
        this.agentRegistry = new AgentRegistry();
        this.conflictDetector = new ConflictDetector((int) configuration.get("conflictThreshold"));
        this.metrics = new IntegrationMetrics();
        this.pendingResolutions = new HashMap<>();

        // Initialize default resolvers
        initializeDefaultResolvers();
    }

    /**
     * Initialize default conflict resolvers.
     */
    private void initializeDefaultResolvers() {
        // Register majority vote resolver
        conflictService.registerResolver("majority-vote", new MajorityVoteConflictResolver());

        // Register escalating resolver
        EscalatingConflictResolver escalatingResolver = new EscalatingConflictResolver();
        conflictService.registerResolver("escalating", escalatingResolver);

        // Register human fallback resolver
        HumanFallbackConflictResolver humanFallbackResolver = new HumanFallbackConflictResolver();
        conflictService.registerResolver("human-fallback", humanFallbackResolver);
    }

    /**
     * Integrate with autonomous agent for work item completion.
     *
     * @param workItem The work item to complete
     * @param agents List of agents to coordinate (simplified)
     * @return Resolution result
     */
    public ConflictResolutionService.ResolutionResult completeWorkItemWithConflictResolution(
        WorkItemRecord workItem, List<Object> agents) {

        metrics.increment("workItemCompletionAttempts");

        try {
            // Get decisions from all agents
            List<AgentDecision> decisions = collectAgentDecisions(workItem, agents);

            // Check for conflict
            if (conflictDetector.detectConflict(decisions)) {
                metrics.increment("conflictsDetected");
                return resolveConflict(workItem, decisions);
            } else {
                // No conflict, return simple decision
                AgentDecision decision = decisions.get(0); // All agents agree
                ConflictResolutionService.ResolutionResult result = createSimpleResult(
                    workItem, decision, "no-conflict"
                );
                metrics.increment("completionsWithoutConflict");
                return CompletableFuture.completedFuture(result);
            }

        } catch (Exception e) {
            metrics.increment("completionFailures");
            CompletableFuture<ConflictResolutionService.ResolutionResult> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Integrate with MCP server for conflict resolution.
     * (Note: Simplified version - MCP integration requires external dependencies)
     *
     * @param conflictContext The conflict context
     * @return Resolution result
     */
    public ConflictResolutionService.ResolutionResult resolveViaMcp(
        ConflictContext conflictContext) throws ConflictResolutionException {

        metrics.increment("mcpResolutionAttempts");

        try {
            // Use MCP server to resolve conflict
            // This would call MCP tools for conflict resolution
            String resolutionId = UUID.randomUUID().toString();
            long startTime = System.currentTimeMillis();

            // In a real implementation, this would:
            // 1. Call MCP tools with conflict context
            // 2. Get resolution from MCP server
            // 3. Return result

            // For now, fall back to local resolution
            ConflictResolutionService.ResolutionResult result = conflictService.resolveConflict(conflictContext);

            metrics.recordDuration("mcpResolutionTime", result.getDurationMs());
            metrics.increment("mcpResolutions");

            return result;

        } catch (Exception e) {
            metrics.increment("mcpResolutionFailures");
            throw new ConflictResolutionException(
                "MCP conflict resolution failed: " + e.getMessage(),
                ConflictResolver.Strategy.HYBRID,
                conflictContext.getConflictId(),
                e
            );
        }
    }

    /**
     * Integrate with A2A protocol for agent-to-agent conflict resolution.
     * (Note: Simplified version - A2A integration requires external dependencies)
     *
     * @param conflictContext The conflict context
     * @return Resolution result
     */
    public ConflictResolutionService.ResolutionResult resolveViaA2A(
        ConflictContext conflictContext) throws A2AException {

        metrics.increment("a2aResolutionAttempts");

        try {
            // Use A2A protocol to resolve conflict
            // This would communicate with other agents via A2A
            String resolutionId = UUID.randomUUID().toString();
            long startTime = System.currentTimeMillis();

            // In a real implementation, this would:
            // 1. Communicate with other agents via A2A
            // 2. Use A2A protocols for conflict resolution
            // 3. Return consensus decision

            // For now, fall back to local resolution
            ConflictResolutionService.ResolutionResult result = conflictService.resolveConflict(conflictContext);

            metrics.recordDuration("a2aResolutionTime", result.getDurationMs());
            metrics.increment("a2aResolutions");

            return result;

        } catch (Exception e) {
            metrics.increment("a2aResolutionFailures");
            throw new A2AException("A2A conflict resolution failed: " + e.getMessage());
        }
    }

    /**
     * Get conflict resolution recommendations for a work item.
     *
     * @param workItem The work item
     * @return List of recommended resolution strategies
     */
    public List<String> getResolutionRecommendations(WorkItemRecord workItem) {
        List<String> recommendations = new ArrayList<>();

        // Check work item characteristics
        String decompositionId = workItem.getDecompositionID();
        String workItemData = workItem.getData();

        // Simple recommendation logic
        if (isCriticalWorkItem(workItem)) {
            recommendations.add("human-fallback");
            recommendations.add("escalating");
        } else if (isComplexWorkItem(workItem)) {
            recommendations.add("escalating");
            recommendations.add("majority-vote");
        } else {
            recommendations.add("majority-vote");
        }

        return recommendations;
    }

    /**
     * Update agent status based on activity.
     *
     * @param agentId The agent ID
     * @param status The new status
     */
    public void updateAgentStatus(String agentId, AgentRegistry.AgentStatus.Status status) {
        AgentRegistry.AgentStatus agentStatus = agentRegistry.getAgentStatus().get(agentId);
        if (agentStatus != null) {
            agentRegistry.agentStatus.put(agentId, agentStatus.withStatus(status));
        }
    }

    /**
     * Register a new autonomous agent.
     *
     * @param agentId Agent ID
     * @param agent The agent to register
     */
    public void registerAgent(String agentId, AutonomousAgent agent) {
        agentRegistry.registerAgent(agentId, agent);
        metrics.increment("agentsRegistered");
    }

    /**
     * Get service metrics.
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> allMetrics = new HashMap<>();
        allMetrics.put("integration", metrics.getMetrics());
        allMetrics.put("conflictService", conflictService.getMetrics());
        allMetrics.put("agentRegistry", getAgentMetrics());
        return allMetrics;
    }

    /**
     * Check if service is healthy.
     */
    public boolean isHealthy() {
        return conflictService.isHealthy() &&
               !agentRegistry.getAvailableAgents().isEmpty() &&
               pendingResolutions.size() < (int) configuration.get("maxConcurrentResolutions");
    }

    /**
     * Update configuration.
     *
     * @param configuration New configuration
     */
    public void updateConfiguration(Map<String, Object> configuration) {
        if (configuration != null) {
            this.configuration.putAll(configuration);

            // Update conflict threshold if changed
            if (configuration.containsKey("conflictThreshold")) {
                int threshold = (int) configuration.get("conflictThreshold");
                this.conflictDetector = new ConflictDetector(threshold);
            }
        }
    }

    // Helper methods

    private List<AgentDecision> collectAgentDecisions(WorkItemRecord workItem, List<AutonomousAgent> agents) {
        return agents.stream()
            .map(agent -> {
                try {
                    // Get decision from agent
                    String decision = agent.completeWorkItem(workItem);
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("agentType", agent.getClass().getSimpleName());
                    metadata.put("agentId", agent.toString());
                    return new AgentDecision(
                        agent.toString(),
                        decision,
                        metadata,
                        0.8, // Default confidence
                        "Standard work item completion"
                    );
                } catch (Exception e) {
                    // Return error decision
                    return new AgentDecision(
                        agent.toString(),
                        "ERROR",
                        Map.of("error", e.getMessage()),
                        0.0,
                        "Failed to complete work item"
                    );
                }
            })
            .collect(Collectors.toList());
    }

    private CompletableFuture<ConflictResolutionService.ResolutionResult> resolveConflict(
        WorkItemRecord workItem, List<AgentDecision> decisions) {

        String resolutionId = UUID.randomUUID().toString();
        CompletableFuture<ConflictResolutionService.ResolutionResult> future = new CompletableFuture<>();

        pendingResolutions.put(resolutionId, future);

        try {
            ConflictContext context = conflictDetector.createConflictContext(workItem, decisions);

            conflictService.resolveConflictAsync(context)
                .thenAccept(result -> {
                    pendingResolutions.remove(resolutionId);
                    future.complete(result);
                })
                .exceptionally(ex -> {
                    pendingResolutions.remove(resolutionId);
                    future.completeExceptionally(ex);
                    return null;
                });

        } catch (Exception e) {
            pendingResolutions.remove(resolutionId);
            future.completeExceptionally(e);
        }

        return future;
    }

    private ConflictResolutionService.ResolutionResult createSimpleResult(
        WorkItemRecord workItem, AgentDecision decision, String resolutionMethod) {

        String resolutionId = UUID.randomUUID().toString();
        Map<String, Object> metadata = new HashMap<>();
            metadata.put("resolutionMethod", resolutionMethod);
            metadata.put("workItemId", workItem.getID());
            metadata.put("decompositionId", workItem.getDecompositionID());

        return new ConflictResolutionService.ResolutionResult(
            resolutionId,
            workItem.getID(),
            null, // No resolver used for simple completion
            new Decision(
                decision.getDecision(),
                ConflictResolver.Severity.LOW,
                List.of(decision.getAgentId()),
                resolutionMethod,
                metadata
            ),
            ConflictResolutionService.ResolutionStatus.COMPLETED,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            null,
            metadata
        );
    }

    private boolean isCriticalWorkItem(WorkItemRecord workItem) {
        // Implement logic to determine if work item is critical
        return workItem.getDecompositionID().contains("critical") ||
               workItem.getDecompositionID().contains("approve");
    }

    private boolean isComplexWorkItem(WorkItemRecord workItem) {
        // Implement logic to determine if work item is complex
        return workItem.getData() != null && workItem.getData().length() > 1000;
    }

    private Map<String, Object> getAgentMetrics() {
        Map<String, Object> agentMetrics = new HashMap<>();
        Map<String, AgentRegistry.AgentStatus> statusMap = agentRegistry.getAgentStatus();

        Map<AgentRegistry.AgentStatus.Status, Long> statusCounts = statusMap.values().stream()
            .collect(Collectors.groupingBy(
                AgentRegistry.AgentStatus::getStatus,
                Collectors.counting()
            ));

        agentMetrics.put("totalAgents", statusMap.size());
        agentMetrics.put("statusCounts", statusCounts);
        agentMetrics.put("activeAgents", statusCounts.getOrDefault(AgentRegistry.AgentStatus.Status.ACTIVE, 0L));

        return agentMetrics;
    }

    // Getters
    public ConflictResolutionService getConflictService() {
        return conflictService;
    }

    public AgentRegistry getAgentRegistry() {
        return agentRegistry;
    }

    public Map<String, Object> getConfiguration() {
        return new HashMap<>(configuration);
    }
}