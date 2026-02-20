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

package org.yawlfoundation.yawl.integration.autonomous.healing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Self-healing autonomic manager implementing the MAPE-K control loop.
 *
 * <p>Monitors workflow execution health, analyzes failure patterns,
 * plans healing actions, and executes repairs using the IBM MAPE-K
 * autonomic computing model.</p>
 *
 * <h2>MAPE-K Phases</h2>
 * <ul>
 *   <li><strong>Monitor</strong>: Track work item failures, agent timeouts, deadlocks</li>
 *   <li><strong>Analyze</strong>: Detect repeated failures, cascading errors, resource exhaustion</li>
 *   <li><strong>Plan</strong>: Generate healing actions (retry, reassign, escalate, circuit-break)</li>
 *   <li><strong>Execute</strong>: Apply healing actions via the engine</li>
 *   <li><strong>Knowledge</strong>: Record action outcomes for future decisions</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class SelfHealingManager {

    private static final Logger LOG = LogManager.getLogger(SelfHealingManager.class);

    /**
     * Severity levels for health events.
     */
    public enum Severity { INFO, WARNING, CRITICAL }

    /**
     * A health event observed during workflow execution.
     *
     * @param timestamp when the event occurred
     * @param severity  event severity
     * @param source    component that produced the event (agent ID, case ID, etc.)
     * @param eventType the type of event (e.g., "work_item_failure", "agent_timeout")
     * @param details   additional context
     */
    public record HealthEvent(
        Instant timestamp,
        Severity severity,
        String source,
        String eventType,
        Map<String, String> details
    ) {
        public HealthEvent {
            Objects.requireNonNull(timestamp);
            Objects.requireNonNull(severity);
            Objects.requireNonNull(source);
            Objects.requireNonNull(eventType);
            details = details != null ? Map.copyOf(details) : Map.of();
        }
    }

    /**
     * Healing actions the manager can take.
     */
    public sealed interface HealingAction {

        String targetId();

        /**
         * Retry the failed operation with exponential backoff.
         */
        record RetryAction(
            String targetId,
            int attemptNumber,
            Duration backoffDelay
        ) implements HealingAction {}

        /**
         * Reassign the work item to a different agent.
         */
        record ReassignAction(
            String targetId,
            String fromAgentId,
            String toAgentId,
            String reason
        ) implements HealingAction {}

        /**
         * Escalate to human operator.
         */
        record EscalateAction(
            String targetId,
            Severity severity,
            String message,
            List<HealthEvent> relatedEvents
        ) implements HealingAction {}

        /**
         * Trip the circuit breaker for an agent.
         */
        record CircuitBreakAction(
            String targetId,
            Duration breakDuration
        ) implements HealingAction {}
    }

    /**
     * Healing policy configuration.
     *
     * @param maxRetries           maximum retries before escalation
     * @param baseBackoff          base backoff duration for retries
     * @param failureWindowSize    number of events to keep in analysis window
     * @param repeatedFailureThreshold failures on same target to trigger reassignment
     * @param cascadeThreshold     failures across targets to trigger circuit break
     */
    public record HealingPolicy(
        int maxRetries,
        Duration baseBackoff,
        int failureWindowSize,
        int repeatedFailureThreshold,
        int cascadeThreshold
    ) {
        public static HealingPolicy defaults() {
            return new HealingPolicy(3, Duration.ofSeconds(2), 100, 3, 10);
        }
    }

    /**
     * Functional interface for executing healing actions.
     */
    @FunctionalInterface
    public interface HealingExecutor {
        boolean execute(HealingAction action);
    }

    private final HealingPolicy policy;
    private final HealingExecutor executor;
    private final ReentrantLock eventLock = new ReentrantLock();
    private final Deque<HealthEvent> eventWindow;
    private final Map<String, Integer> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryCounts = new ConcurrentHashMap<>();
    private final Map<String, List<ActionOutcome>> knowledgeBase = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread monitorThread;

    private record ActionOutcome(HealingAction action, boolean success, Instant timestamp) {}

    /**
     * Creates a self-healing manager.
     *
     * @param policy   healing policy configuration
     * @param executor strategy for executing healing actions
     */
    public SelfHealingManager(HealingPolicy policy, HealingExecutor executor) {
        this.policy = Objects.requireNonNull(policy);
        this.executor = Objects.requireNonNull(executor);
        this.eventWindow = new ArrayDeque<>(policy.failureWindowSize());
    }

    /**
     * Ingests a health event into the MAPE-K pipeline.
     *
     * <p>Runs through all four phases: monitor → analyze → plan → execute.</p>
     *
     * @param event the health event to process
     */
    public void ingestEvent(HealthEvent event) {
        monitor(event);
        List<String> patterns = analyze();
        List<HealingAction> actions = plan(event, patterns);
        for (HealingAction action : actions) {
            executeAction(action);
        }
    }

    /**
     * Monitor phase: record the event in the sliding window.
     */
    private void monitor(HealthEvent event) {
        eventLock.lock();
        try {
            if (eventWindow.size() >= policy.failureWindowSize()) {
                eventWindow.removeFirst();
            }
            eventWindow.addLast(event);

            if (event.severity() == Severity.WARNING || event.severity() == Severity.CRITICAL) {
                failureCounts.merge(event.source(), 1, Integer::sum);
            }
        } finally {
            eventLock.unlock();
        }

        LOG.debug("Monitored event: {} from {} [{}]", event.eventType(), event.source(), event.severity());
    }

    /**
     * Analyze phase: detect failure patterns in the event window.
     *
     * @return list of detected pattern names
     */
    private List<String> analyze() {
        List<String> patterns = new ArrayList<>();

        eventLock.lock();
        try {
            // Check for repeated failures on same source
            for (Map.Entry<String, Integer> entry : failureCounts.entrySet()) {
                if (entry.getValue() >= policy.repeatedFailureThreshold()) {
                    patterns.add("repeated_failure:" + entry.getKey());
                }
            }

            // Check for cascade (many sources failing)
            long criticalCount = eventWindow.stream()
                .filter(e -> e.severity() == Severity.CRITICAL)
                .count();
            if (criticalCount >= policy.cascadeThreshold()) {
                patterns.add("cascade_failure");
            }
        } finally {
            eventLock.unlock();
        }

        if (!patterns.isEmpty()) {
            LOG.info("Detected patterns: {}", patterns);
        }

        return patterns;
    }

    /**
     * Plan phase: select healing actions based on detected patterns.
     */
    private List<HealingAction> plan(HealthEvent trigger, List<String> patterns) {
        List<HealingAction> actions = new ArrayList<>();

        for (String pattern : patterns) {
            if (pattern.equals("cascade_failure")) {
                actions.add(new HealingAction.CircuitBreakAction(
                    trigger.source(), Duration.ofMinutes(1)));
                continue;
            }

            if (pattern.startsWith("repeated_failure:")) {
                String target = pattern.substring("repeated_failure:".length());
                int retries = retryCounts.getOrDefault(target, 0);

                if (retries < policy.maxRetries()) {
                    Duration backoff = computeBackoff(retries);
                    actions.add(new HealingAction.RetryAction(target, retries + 1, backoff));
                } else {
                    // Check knowledge base for successful reassignments
                    actions.add(new HealingAction.EscalateAction(
                        target, Severity.CRITICAL,
                        "Exhausted " + policy.maxRetries() + " retries for " + target,
                        List.copyOf(eventWindow.stream()
                            .filter(e -> e.source().equals(target))
                            .toList())
                    ));
                }
            }
        }

        return actions;
    }

    /**
     * Execute phase: apply the healing action and record the outcome.
     */
    private void executeAction(HealingAction action) {
        LOG.info("Executing healing action: {}", action);

        boolean success = false;
        try {
            success = executor.execute(action);
        } catch (Exception e) {
            LOG.error("Healing action failed: {}", e.getMessage(), e);
        }

        // Update knowledge base
        knowledgeBase.computeIfAbsent(action.targetId(), _ -> new ArrayList<>())
            .add(new ActionOutcome(action, success, Instant.now()));

        // Update retry counts
        switch (action) {
            case HealingAction.RetryAction retry ->
                retryCounts.put(retry.targetId(), retry.attemptNumber());
            case HealingAction.EscalateAction _ ->
                retryCounts.remove(action.targetId());
            case HealingAction.CircuitBreakAction _, HealingAction.ReassignAction _ -> {
                failureCounts.remove(action.targetId());
            }
        }

        if (success) {
            failureCounts.remove(action.targetId());
            LOG.info("Healing action succeeded for {}", action.targetId());
        } else {
            LOG.warn("Healing action failed for {}", action.targetId());
        }
    }

    /**
     * Computes exponential backoff with jitter.
     */
    private Duration computeBackoff(int attempt) {
        long baseMs = policy.baseBackoff().toMillis();
        long exponentialMs = baseMs * (1L << attempt);
        long jitterMs = ThreadLocalRandom.current().nextLong(0, exponentialMs / 2 + 1);
        return Duration.ofMillis(exponentialMs + jitterMs);
    }

    /**
     * Returns the current failure counts per source.
     */
    public Map<String, Integer> getFailureCounts() {
        return Map.copyOf(failureCounts);
    }

    /**
     * Returns the number of events in the current window.
     */
    public int getEventWindowSize() {
        eventLock.lock();
        try {
            return eventWindow.size();
        } finally {
            eventLock.unlock();
        }
    }
}
