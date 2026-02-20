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

package org.yawlfoundation.yawl.integration.autonomous.control;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Top-level autonomic control loop implementing IBM's MAPE-K model for YAWL.
 *
 * <p>Orchestrates the Monitor-Analyze-Plan-Execute cycle across all autonomous
 * workflow components. Runs on a virtual thread with configurable cycle interval,
 * collecting health scores, detecting anomalies, selecting corrective actions,
 * and tracking action effectiveness for adaptive learning.</p>
 *
 * <h2>Architecture Integration</h2>
 * <p>This is the capstone component that coordinates:</p>
 * <ul>
 *   <li>{@code AutonomicObserver} for health telemetry collection</li>
 *   <li>{@code SelfHealingManager} for failure remediation</li>
 *   <li>{@code AdaptiveScheduler} for work distribution</li>
 *   <li>{@code AgentCircuitBreaker} for resilience</li>
 *   <li>{@code AgentCapabilityRegistry} for agent discovery</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class AutonomicControlLoop {

    private static final Logger LOG = LogManager.getLogger(AutonomicControlLoop.class);

    /**
     * Actions the control loop can take.
     */
    public sealed interface ControlAction {
        String targetId();
        int priority(); // Higher = more urgent

        record HealAction(String targetId, int priority, String healingType, Map<String, String> params)
            implements ControlAction {}
        record ScheduleAction(String targetId, int priority, String workItemId, String toAgentId)
            implements ControlAction {}
        record ScaleAction(String targetId, int priority, int desiredCapacity, String reason)
            implements ControlAction {}
        record AlertAction(String targetId, int priority, String severity, String message)
            implements ControlAction {}
    }

    /**
     * State snapshot of the control loop.
     *
     * @param lastCycle      when the last cycle completed
     * @param cycleCount     total cycles executed
     * @param healthScores   latest health scores per component
     * @param pendingActions actions queued for execution
     */
    public record ControlLoopState(
        Instant lastCycle,
        int cycleCount,
        Map<String, Double> healthScores,
        List<ControlAction> pendingActions
    ) {
        public ControlLoopState {
            healthScores = healthScores != null ? Map.copyOf(healthScores) : Map.of();
            pendingActions = pendingActions != null ? List.copyOf(pendingActions) : List.of();
        }
    }

    /**
     * Result of analyzing health data.
     *
     * @param anomalies detected anomaly descriptions
     * @param degradedComponents components below health threshold
     */
    public record AnalysisResult(
        List<String> anomalies,
        List<String> degradedComponents
    ) {}

    /**
     * Functional interface for the Monitor phase.
     */
    @FunctionalInterface
    public interface HealthMonitor {
        Map<String, Double> collectHealthScores();
    }

    /**
     * Functional interface for executing control actions.
     */
    @FunctionalInterface
    public interface ActionExecutor {
        boolean execute(ControlAction action);
    }

    /**
     * Configuration for the control loop.
     *
     * @param cycleInterval    time between cycles
     * @param healthThreshold  health score below which a component is considered degraded
     * @param maxActionsPerCycle maximum actions to execute per cycle
     */
    public record Config(
        Duration cycleInterval,
        double healthThreshold,
        int maxActionsPerCycle
    ) {
        public static Config defaults() {
            return new Config(Duration.ofSeconds(5), 0.7, 10);
        }
    }

    private final Config config;
    private final HealthMonitor monitor;
    private final ActionExecutor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger cycleCount = new AtomicInteger();
    private final ReentrantLock stateLock = new ReentrantLock();

    // Knowledge base: track action outcomes for learning
    private final Map<String, List<ActionRecord>> knowledgeBase = new ConcurrentHashMap<>();

    private volatile Thread controlThread;
    private volatile ControlLoopState currentState;
    private volatile Instant lastCycle;

    private record ActionRecord(ControlAction action, boolean success, Instant timestamp) {}

    /**
     * Creates the autonomic control loop.
     *
     * @param config   loop configuration
     * @param monitor  health monitoring strategy
     * @param executor action execution strategy
     */
    public AutonomicControlLoop(Config config, HealthMonitor monitor, ActionExecutor executor) {
        this.config = Objects.requireNonNull(config);
        this.monitor = Objects.requireNonNull(monitor);
        this.executor = Objects.requireNonNull(executor);
        this.currentState = new ControlLoopState(Instant.now(), 0, Map.of(), List.of());
    }

    /**
     * Starts the control loop on a virtual thread.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            controlThread = Thread.ofVirtual()
                .name("autonomic-control-loop")
                .start(this::runLoop);
            LOG.info("Autonomic control loop started (interval: {})", config.cycleInterval());
        }
    }

    /**
     * Stops the control loop gracefully.
     */
    public void stop() {
        running.set(false);
        if (controlThread != null) {
            controlThread.interrupt();
            LOG.info("Autonomic control loop stopped after {} cycles", cycleCount.get());
        }
    }

    /**
     * Returns whether the loop is running.
     */
    public boolean isRunning() { return running.get(); }

    /**
     * Returns the current state snapshot.
     */
    public ControlLoopState getState() { return currentState; }

    /**
     * Executes a single MAPE-K cycle. Can be called manually for testing.
     */
    public void executeCycle() {
        Instant cycleStart = Instant.now();
        int cycle = cycleCount.incrementAndGet();

        LOG.debug("Control loop cycle {} starting", cycle);

        // MONITOR: Collect health scores
        Map<String, Double> healthScores = monitor.collectHealthScores();

        // ANALYZE: Detect anomalies
        AnalysisResult analysis = analyze(healthScores);

        // PLAN: Generate actions
        List<ControlAction> actions = plan(analysis, healthScores);

        // EXECUTE: Apply actions (limited per cycle)
        List<ControlAction> executed = executeActions(actions);

        // Update state
        lastCycle = Instant.now();
        stateLock.lock();
        try {
            currentState = new ControlLoopState(lastCycle, cycle, healthScores, executed);
        } finally {
            stateLock.unlock();
        }

        Duration cycleDuration = Duration.between(cycleStart, Instant.now());
        if (!analysis.anomalies().isEmpty()) {
            LOG.info("Cycle {}: {} anomalies, {} actions in {}",
                     cycle, analysis.anomalies().size(), executed.size(), cycleDuration);
        } else {
            LOG.trace("Cycle {}: healthy, {} actions in {}", cycle, executed.size(), cycleDuration);
        }
    }

    private void runLoop() {
        while (running.get()) {
            try {
                executeCycle();
                Thread.sleep(config.cycleInterval());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error("Control loop cycle error: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * ANALYZE phase: detect anomalies in health data.
     */
    private AnalysisResult analyze(Map<String, Double> healthScores) {
        List<String> anomalies = new ArrayList<>();
        List<String> degraded = new ArrayList<>();

        for (Map.Entry<String, Double> entry : healthScores.entrySet()) {
            if (entry.getValue() < config.healthThreshold()) {
                degraded.add(entry.getKey());
                anomalies.add("Degraded: " + entry.getKey()
                    + " (health=" + String.format("%.2f", entry.getValue()) + ")");
            }

            // Check for rapid degradation using knowledge base
            List<ActionRecord> history = knowledgeBase.get(entry.getKey());
            if (history != null && history.size() >= 3) {
                long recentFailures = history.stream()
                    .filter(r -> r.timestamp().isAfter(Instant.now().minus(Duration.ofMinutes(5))))
                    .filter(r -> !r.success())
                    .count();
                if (recentFailures >= 3) {
                    anomalies.add("Recurring failure: " + entry.getKey()
                        + " (" + recentFailures + " failures in 5min)");
                }
            }
        }

        return new AnalysisResult(anomalies, degraded);
    }

    /**
     * PLAN phase: generate prioritized actions.
     */
    private List<ControlAction> plan(AnalysisResult analysis, Map<String, Double> healthScores) {
        List<ControlAction> actions = new ArrayList<>();

        for (String component : analysis.degradedComponents()) {
            double health = healthScores.getOrDefault(component, 0.0);

            if (health < 0.3) {
                // Critical: heal first, then alert
                actions.add(new ControlAction.HealAction(component, 100, "circuit_break",
                    Map.of("duration", "60s")));
                actions.add(new ControlAction.AlertAction(component, 90, "CRITICAL",
                    "Component " + component + " critically degraded (health=" + String.format("%.2f", health) + ")"));
            } else if (health < 0.5) {
                // Severe: try healing
                actions.add(new ControlAction.HealAction(component, 80, "retry",
                    Map.of("maxRetries", "3")));
            } else {
                // Moderate: alert only
                actions.add(new ControlAction.AlertAction(component, 50, "WARNING",
                    "Component " + component + " degraded (health=" + String.format("%.2f", health) + ")"));
            }
        }

        // Sort by priority (highest first)
        actions.sort(Comparator.comparingInt(ControlAction::priority).reversed());
        return actions;
    }

    /**
     * EXECUTE phase: apply actions with rate limiting.
     */
    private List<ControlAction> executeActions(List<ControlAction> actions) {
        List<ControlAction> executed = new ArrayList<>();

        int limit = Math.min(actions.size(), config.maxActionsPerCycle());
        for (int i = 0; i < limit; i++) {
            ControlAction action = actions.get(i);
            boolean success = false;
            try {
                success = executor.execute(action);
            } catch (Exception e) {
                LOG.error("Action execution failed for {}: {}", action.targetId(), e.getMessage());
            }

            // Record in knowledge base
            knowledgeBase.computeIfAbsent(action.targetId(), _ -> new ArrayList<>())
                .add(new ActionRecord(action, success, Instant.now()));

            executed.add(action);
        }

        return executed;
    }
}
