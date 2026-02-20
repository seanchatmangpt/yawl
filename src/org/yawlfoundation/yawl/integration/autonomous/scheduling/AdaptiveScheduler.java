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

package org.yawlfoundation.yawl.integration.autonomous.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Adaptive work item scheduler using weighted scoring for agent assignment.
 *
 * <p>Schedules YAWL work items to autonomous agents based on real-time
 * performance metrics using a weighted scoring model:</p>
 * <ul>
 *   <li>Capability match: 40%</li>
 *   <li>Historical success rate: 30%</li>
 *   <li>Current load: 20%</li>
 *   <li>Average latency: 10%</li>
 * </ul>
 *
 * <p>Agent profiles adapt over time via exponential moving average.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class AdaptiveScheduler {

    private static final Logger LOG = LogManager.getLogger(AdaptiveScheduler.class);

    private static final double WEIGHT_CAPABILITY = 0.40;
    private static final double WEIGHT_SUCCESS_RATE = 0.30;
    private static final double WEIGHT_LOAD = 0.20;
    private static final double WEIGHT_LATENCY = 0.10;
    private static final double EMA_ALPHA = 0.3; // Exponential moving average decay

    /**
     * Describes a work item to be scheduled.
     *
     * @param workItemId       the work item identifier
     * @param taskName         the task name
     * @param requiredCapabilities capabilities needed for this work item
     * @param priority         priority level (higher = more urgent)
     */
    public record WorkItemDescriptor(
        String workItemId,
        String taskName,
        Set<String> requiredCapabilities,
        int priority
    ) {
        public WorkItemDescriptor {
            Objects.requireNonNull(workItemId);
            Objects.requireNonNull(taskName);
            requiredCapabilities = requiredCapabilities != null ? Set.copyOf(requiredCapabilities) : Set.of();
        }
    }

    /**
     * Performance profile for an agent, adapted over time.
     */
    public static final class AgentProfile {
        private final String agentId;
        private final Set<String> capabilities;
        private final int maxConcurrency;
        private final ReentrantLock lock = new ReentrantLock();

        private double successRate = 1.0;
        private double avgLatencyMs = 100.0;
        private int currentLoad;
        private long totalTasks;
        private Instant lastUpdated = Instant.now();

        public AgentProfile(String agentId, Set<String> capabilities, int maxConcurrency) {
            this.agentId = Objects.requireNonNull(agentId);
            this.capabilities = Set.copyOf(Objects.requireNonNull(capabilities));
            this.maxConcurrency = maxConcurrency;
        }

        public String getAgentId() { return agentId; }
        public Set<String> getCapabilities() { return capabilities; }
        public int getMaxConcurrency() { return maxConcurrency; }

        public double getSuccessRate() { return successRate; }
        public double getAvgLatencyMs() { return avgLatencyMs; }
        public int getCurrentLoad() { return currentLoad; }
        public long getTotalTasks() { return totalTasks; }
        public Instant getLastUpdated() { return lastUpdated; }

        void incrementLoad() {
            lock.lock();
            try { currentLoad++; } finally { lock.unlock(); }
        }

        void decrementLoad() {
            lock.lock();
            try { currentLoad = Math.max(0, currentLoad - 1); } finally { lock.unlock(); }
        }
    }

    /**
     * The result of a scheduling decision.
     *
     * @param agentId     the selected agent
     * @param score       the computed score (0.0 to 1.0)
     * @param workItemId  the work item being scheduled
     * @param reason      explanation of the decision
     */
    public record SchedulingDecision(
        String agentId,
        double score,
        String workItemId,
        String reason
    ) {}

    /**
     * Execution metrics reported back after task completion.
     *
     * @param success   whether the execution succeeded
     * @param latencyMs execution latency in milliseconds
     */
    public record ExecutionMetrics(boolean success, long latencyMs) {}

    private final Map<String, AgentProfile> agents = new ConcurrentHashMap<>();

    /**
     * Registers an agent with the scheduler.
     *
     * @param profile the agent's profile
     */
    public void registerAgent(AgentProfile profile) {
        agents.put(profile.getAgentId(), profile);
        LOG.info("Registered agent: {} with capabilities {}", profile.getAgentId(), profile.getCapabilities());
    }

    /**
     * Removes an agent from the scheduler.
     *
     * @param agentId the agent to remove
     */
    public void deregisterAgent(String agentId) {
        agents.remove(agentId);
        LOG.info("Deregistered agent: {}", agentId);
    }

    /**
     * Schedules a work item to the best available agent using weighted scoring.
     *
     * @param item the work item to schedule
     * @return scheduling decision, or empty if no eligible agent found
     */
    public Optional<SchedulingDecision> schedule(WorkItemDescriptor item) {
        Objects.requireNonNull(item, "item must not be null");

        List<ScoredAgent> scored = agents.values().stream()
            .filter(agent -> agent.getCurrentLoad() < agent.getMaxConcurrency())
            .map(agent -> new ScoredAgent(agent, computeScore(agent, item)))
            .filter(sa -> sa.score > 0.0)
            .sorted(Comparator.comparingDouble(ScoredAgent::score).reversed())
            .toList();

        if (scored.isEmpty()) {
            LOG.warn("No eligible agent for work item {} (task: {})", item.workItemId(), item.taskName());
            return Optional.empty();
        }

        ScoredAgent best = scored.getFirst();
        best.agent.incrementLoad();

        String reason = String.format(
            "capability=%.2f, success=%.2f, load=%.2f, latency=%.2f → total=%.4f",
            capabilityScore(best.agent, item) * WEIGHT_CAPABILITY,
            best.agent.getSuccessRate() * WEIGHT_SUCCESS_RATE,
            loadScore(best.agent) * WEIGHT_LOAD,
            latencyScore(best.agent) * WEIGHT_LATENCY,
            best.score
        );

        LOG.debug("Scheduled {} → {} (score: {:.4f})", item.workItemId(), best.agent.getAgentId(), best.score);

        return Optional.of(new SchedulingDecision(
            best.agent.getAgentId(), best.score, item.workItemId(), reason));
    }

    /**
     * Reports execution metrics back to the scheduler for adaptive learning.
     *
     * @param agentId the agent that executed the task
     * @param metrics the execution metrics
     */
    public void updateAgentMetrics(String agentId, ExecutionMetrics metrics) {
        AgentProfile profile = agents.get(agentId);
        if (profile == null) {
            return;
        }

        profile.lock.lock();
        try {
            // Exponential moving average for success rate
            double successValue = metrics.success() ? 1.0 : 0.0;
            profile.successRate = EMA_ALPHA * successValue + (1 - EMA_ALPHA) * profile.successRate;

            // EMA for latency
            profile.avgLatencyMs = EMA_ALPHA * metrics.latencyMs() + (1 - EMA_ALPHA) * profile.avgLatencyMs;

            profile.totalTasks++;
            profile.lastUpdated = Instant.now();
        } finally {
            profile.lock.unlock();
        }

        profile.decrementLoad();
    }

    private double computeScore(AgentProfile agent, WorkItemDescriptor item) {
        double capScore = capabilityScore(agent, item);
        if (capScore == 0.0) return 0.0; // No capability match = ineligible

        return capScore * WEIGHT_CAPABILITY
             + agent.getSuccessRate() * WEIGHT_SUCCESS_RATE
             + loadScore(agent) * WEIGHT_LOAD
             + latencyScore(agent) * WEIGHT_LATENCY;
    }

    /**
     * Jaccard similarity between agent capabilities and required capabilities.
     */
    private double capabilityScore(AgentProfile agent, WorkItemDescriptor item) {
        if (item.requiredCapabilities().isEmpty()) return 1.0;

        long matchCount = item.requiredCapabilities().stream()
            .filter(agent.getCapabilities()::contains)
            .count();

        long unionSize = item.requiredCapabilities().size();
        return (double) matchCount / unionSize;
    }

    /**
     * Load score: 1.0 when idle, 0.0 when at max concurrency.
     */
    private double loadScore(AgentProfile agent) {
        if (agent.getMaxConcurrency() <= 0) return 0.0;
        return 1.0 - ((double) agent.getCurrentLoad() / agent.getMaxConcurrency());
    }

    /**
     * Latency score: normalized inverse of average latency (lower is better).
     * Uses 10000ms as the reference maximum.
     */
    private double latencyScore(AgentProfile agent) {
        return Math.max(0.0, 1.0 - (agent.getAvgLatencyMs() / 10000.0));
    }

    /**
     * Returns all registered agent profiles.
     */
    public Map<String, AgentProfile> getAgents() {
        return Map.copyOf(agents);
    }

    private record ScoredAgent(AgentProfile agent, double score) {}
}
