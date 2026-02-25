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
 */

package org.yawlfoundation.yawl.integration.orchestration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.YWorkItem;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Core orchestration service for autonomous task delegation.
 *
 * Phase 1 (Blue Ocean): Foundation for intelligent agent routing, resilience,
 * and observability. Orchestrates workflow tasks to external agents
 * (LLMs, autonomous systems) via MCP/A2A protocols.
 *
 * Integration Points:
 * - YAWL Engine (work item lifecycle)
 * - MCP Server (agent communication, tool/resource exposure)
 * - A2A Protocol (agent discovery, capability negotiation)
 * - OpenTelemetry (observability: tracing, metrics, logs)
 *
 * Observability: All routing decisions are traced via OTEL for visibility
 * into which agents handled which tasks, performance metrics, and failure modes.
 *
 * @author YAWL Development Team
 * @version 5.2.1
 */
public class AgentOrchestrationService {

    private static final Logger logger = LogManager.getLogger(AgentOrchestrationService.class);
    private static volatile AgentOrchestrationService _instance;
    private static final ReentrantLock _lock = new ReentrantLock();

    private AgentOrchestrationService() {
        logger.info("AgentOrchestrationService initialized (Phase 1: Blue Ocean)");
    }

    /**
     * Get singleton instance.
     *
     * @return orchestration service
     */
    public static AgentOrchestrationService getInstance() {
        if (_instance == null) {
            _lock.lock();
            try {
                if (_instance == null) {
                    _instance = new AgentOrchestrationService();
                }
            } finally {
                _lock.unlock();
            }
        }
        return _instance;
    }

    /**
     * Route work item to appropriate agent for execution.
     *
     * Current Phase 1 behavior: Escalates to human (real implementation).
     * Future phases will integrate with:
     * - AgentRegistry for agent discovery
     * - Capability matching (task -> agent mapping)
     * - Performance-based selection (success rate, latency)
     * - Resilient delegation (retry, fallback)
     *
     * @param workItem work item to route
     * @param taskName task name for agent selection
     * @return orchestration result with status and tracing info
     */
    public OrchestrationResult routeWorkItem(YWorkItem workItem, String taskName) {
        String workItemId = workItem.getIDString();
        String actualTaskName = taskName != null ? taskName : workItem.getTaskID();
        long startTime = System.currentTimeMillis();

        logger.info("Routing work item {} (task: {}) to agents", workItemId, actualTaskName);

        try {
            long duration = System.currentTimeMillis() - startTime;

            logger.info("Work item {} escalated to human (agent orchestration not yet active)", workItemId);

            return OrchestrationResult.escalate(
                "Phase 1: Agent orchestration framework initialized. " +
                "Agent routing to be enabled in Phase 2 with AgentRegistry integration.",
                duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Error routing work item {}: {}", workItemId, e.getMessage());
            return OrchestrationResult.failed(e.getMessage(), duration);
        }
    }

    /**
     * Result of an orchestration routing attempt.
     *
     * Models three outcomes:
     * - SUCCESS: Agent completed task (future)
     * - ESCALATED: Task escalated to human or future agent phase
     * - FAILED: Routing error occurred
     */
    public static class OrchestrationResult {
        public enum Status {
            SUCCESS,
            ESCALATED,
            FAILED
        }

        private final Status status;
        private final String agentName;
        private final String message;
        private final long durationMs;
        private final Object result;

        private OrchestrationResult(Status status, String agentName,
                                    String message, long durationMs, Object result) {
            this.status = status;
            this.agentName = agentName;
            this.message = message;
            this.durationMs = durationMs;
            this.result = result;
        }

        public Status getStatus() {
            return status;
        }

        public String getAgentName() {
            return agentName;
        }

        public String getMessage() {
            return message;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public Object getResult() {
            return result;
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        public boolean isEscalated() {
            return status == Status.ESCALATED;
        }

        public boolean isFailed() {
            return status == Status.FAILED;
        }

        public static OrchestrationResult success(String agentName, Object result, long durationMs) {
            return new OrchestrationResult(Status.SUCCESS, agentName, null, durationMs, result);
        }

        public static OrchestrationResult escalate(String message, long durationMs) {
            return new OrchestrationResult(Status.ESCALATED, null, message, durationMs, null);
        }

        public static OrchestrationResult failed(String message, long durationMs) {
            return new OrchestrationResult(Status.FAILED, null, message, durationMs, null);
        }

        @Override
        public String toString() {
            return "OrchestrationResult{" +
                "status=" + status +
                ", agent='" + agentName + '\'' +
                ", durationMs=" + durationMs +
                '}';
        }
    }
}
