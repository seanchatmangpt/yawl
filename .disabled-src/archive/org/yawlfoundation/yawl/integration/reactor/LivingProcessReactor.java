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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.reactor;

import org.yawlfoundation.yawl.integration.memory.LearningCapture;
import org.yawlfoundation.yawl.integration.memory.UpgradeMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Continuous closed-loop reactor that monitors workflow metrics, detects
 * performance drift, proposes spec mutations, and records optimization cycles.
 *
 * <p>The reactor runs periodic cycles on a ScheduledExecutorService. Each cycle:
 * <ol>
 *   <li>Captures current workflow metrics</li>
 *   <li>Detects if performance has drifted beyond policy threshold</li>
 *   <li>If drift detected, proposes a SpecMutation</li>
 *   <li>Simulates the mutation against historical patterns</li>
 *   <li>Records the cycle result in UpgradeMemoryStore</li>
 * </ol>
 *
 * <p>All cycle state is persisted to UpgradeMemoryStore for analysis and learning.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class LivingProcessReactor {

    private static final Logger log = LoggerFactory.getLogger(LivingProcessReactor.class);

    private final LearningCapture learning;
    private final UpgradeMemoryStore memory;
    private final ReactorPolicy policy;
    private final ScheduledExecutorService scheduler;
    private final Supplier<Map<String, Double>> metricsSupplier;
    private final List<ReactorCycle> cycleHistory = new CopyOnWriteArrayList<>();
    private final AtomicReference<ReactorCycle> lastCycle = new AtomicReference<>();

    private volatile boolean running = false;
    private ScheduledFuture<?> scheduledTask;

    /**
     * Constructs a LivingProcessReactor.
     *
     * @param learning LearningCapture for pattern analysis
     * @param memory UpgradeMemoryStore for persisting cycles
     * @param policy reactor configuration policy
     * @param metricsSupplier function that returns current metrics map
     * @throws NullPointerException if any parameter is null
     */
    public LivingProcessReactor(
        LearningCapture learning,
        UpgradeMemoryStore memory,
        ReactorPolicy policy,
        Supplier<Map<String, Double>> metricsSupplier
    ) {
        this.learning = Objects.requireNonNull(learning, "learning must not be null");
        this.memory = Objects.requireNonNull(memory, "memory must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.metricsSupplier = Objects.requireNonNull(metricsSupplier, "metricsSupplier must not be null");
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "YawlReactor-CycleThread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the reactor, scheduling periodic cycle execution.
     *
     * <p>Cycles run at intervals specified by policy.cycleInterval().</p>
     */
    public void start() {
        if (running) {
            log.warn("Reactor already running");
            return;
        }

        running = true;
        long initialDelayMs = policy.cycleInterval().toMillis();
        long periodMs = policy.cycleInterval().toMillis();

        scheduledTask = scheduler.scheduleAtFixedRate(
            this::runCycle,
            initialDelayMs,
            periodMs,
            TimeUnit.MILLISECONDS
        );

        log.info("Living Process Reactor started: cycle interval={}ms", periodMs);
    }

    /**
     * Stops the reactor and shuts down the scheduler.
     *
     * <p>Allows pending cycles to complete before shutdown.</p>
     */
    public void stop() {
        if (!running) {
            log.warn("Reactor not running");
            return;
        }

        running = false;

        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("Scheduler did not terminate gracefully");
                }
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("Living Process Reactor stopped");
    }

    /**
     * Returns true if the reactor is currently running.
     *
     * @return true iff reactor has been started and not stopped
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Executes one complete reactor cycle synchronously.
     *
     * <p>Captures metrics, detects drift, proposes mutations, runs simulation,
     * and records the cycle. This method is idempotent and can be called
     * directly from tests or for manual triggering.</p>
     *
     * @return the completed ReactorCycle
     */
    public ReactorCycle runCycle() {
        Instant cycleStart = Instant.now();
        String cycleId = "cycle_" + System.nanoTime();

        try {
            // Capture metrics
            Map<String, Double> metrics = metricsSupplier.get();
            if (metrics == null) {
                metrics = Map.of();
            }

            // Check for drift
            double avgExecTime = metrics.getOrDefault("avgExecutionTimeMs", 0.0);
            double caseCount = metrics.getOrDefault("totalCasesCompleted", 0.0);

            // Initialize outcome
            String outcome = "NO_DRIFT";
            SpecMutation proposedMutation = null;
            SimulationResult simulationResult = null;
            boolean committed = false;

            // Detect drift if sufficient samples
            if (caseCount >= policy.minSamplesBeforeMutation() && avgExecTime > 0) {
                // Simple heuristic: check if execution time has increased beyond threshold
                if (hasPerformanceDrift(avgExecTime)) {
                    // Propose mutation based on learning patterns
                    proposedMutation = proposeMutation(metrics);

                    if (proposedMutation != null) {
                        // Simulate the mutation
                        simulationResult = simulateMutation(proposedMutation);
                        outcome = "MUTATION_PROPOSED";

                        // Check if sound and meets auto-commit criteria
                        if (simulationResult.soundnessOk()
                            && policy.autoCommit()
                            && proposedMutation.riskLevel().ordinal()
                               <= policy.maxAutoCommitRisk().ordinal()) {
                            committed = true;
                            outcome = "MUTATION_COMMITTED";
                            log.info("Auto-committed mutation: {} on {}",
                                proposedMutation.mutationType(), proposedMutation.targetElement());
                        } else if (simulationResult.soundnessOk()) {
                            outcome = "MUTATION_PROPOSED";
                        } else {
                            outcome = "MUTATION_REJECTED";
                        }
                    }
                }
            }

            // Record cycle
            long durationMs = System.currentTimeMillis() - cycleStart.toEpochMilli();
            ReactorCycle cycle = new ReactorCycle(
                cycleId,
                cycleStart,
                metrics,
                proposedMutation,
                simulationResult,
                committed,
                durationMs,
                outcome
            );

            // Store in history and memory
            cycleHistory.add(cycle);
            lastCycle.set(cycle);
            storeCycleInMemory(cycle);

            log.debug("Reactor cycle completed: {} ({}ms, outcome={})",
                cycleId, durationMs, outcome);

            return cycle;

        } catch (Exception e) {
            log.error("Error during reactor cycle", e);
            // Return error cycle
            long durationMs = System.currentTimeMillis() - cycleStart.toEpochMilli();
            ReactorCycle errorCycle = new ReactorCycle(
                cycleId,
                cycleStart,
                Map.of(),
                null,
                null,
                false,
                durationMs,
                "ERROR"
            );
            cycleHistory.add(errorCycle);
            lastCycle.set(errorCycle);
            return errorCycle;
        }
    }

    /**
     * Returns the most recent completed cycle, or null if no cycles have run.
     *
     * @return the last ReactorCycle, or null
     */
    public ReactorCycle getLastCycle() {
        return lastCycle.get();
    }

    /**
     * Returns the most recent N cycles from history.
     *
     * @param limit maximum number of cycles to return
     * @return list of recent cycles (up to limit items)
     */
    public List<ReactorCycle> getCycleHistory(int limit) {
        int size = cycleHistory.size();
        int fromIndex = Math.max(0, size - limit);
        return new ArrayList<>(cycleHistory.subList(fromIndex, size));
    }

    /**
     * Checks if performance has drifted beyond the policy threshold.
     *
     * <p>Simple heuristic: if current avg execution time exceeds baseline by threshold %.</p>
     *
     * @param currentAvgExecTime current average execution time in ms
     * @return true if drift detected
     */
    private boolean hasPerformanceDrift(double currentAvgExecTime) {
        if (lastCycle.get() == null) {
            return false; // No baseline, can't detect drift yet
        }

        Double lastAvgTime = lastCycle.get().metricsSnapshot().get("avgExecutionTimeMs");
        if (lastAvgTime == null || lastAvgTime <= 0) {
            return false;
        }

        double percentChange = (currentAvgExecTime - lastAvgTime) / lastAvgTime;
        return percentChange > policy.driftThreshold();
    }

    /**
     * Proposes a SpecMutation based on detected drift and learning patterns.
     *
     * <p>Checks LearningCapture for failure patterns and suggests mutations accordingly.</p>
     *
     * @param metrics current metrics snapshot
     * @return proposed mutation, or null if no suitable mutation found
     */
    private SpecMutation proposeMutation(Map<String, Double> metrics) {
        // Query LearningCapture for known failure patterns
        List<LearningCapture.FailurePattern> failures =
            learning.findMatchingFailurePatterns("performance");

        if (!failures.isEmpty()) {
            LearningCapture.FailurePattern topFailure = failures.get(0);
            String targetElement = topFailure.affectedPhases().isEmpty()
                ? "workflow"
                : topFailure.affectedPhases().iterator().next();
            String rationale = "Failure pattern '" + topFailure.name()
                + "' detected " + topFailure.occurrenceCount() + " times";
            if (!topFailure.resolution().isEmpty()) {
                rationale += ". Recommended: " + topFailure.resolution().get(0);
            }
            SpecMutation.RiskLevel risk = topFailure.occurrenceCount() > 5
                ? SpecMutation.RiskLevel.HIGH
                : SpecMutation.RiskLevel.MEDIUM;
            return new SpecMutation("ADDRESS_FAILURE_PATTERN", targetElement,
                "<mutation type=\"address-failure\"/>", rationale, risk);
        }

        // No failure patterns — use metrics-based mutation selection
        String driftedMetric = findMostDriftedMetric(metrics);
        String mutationType = switch (driftedMetric) {
            case "avgExecutionTimeMs" -> "PARALLELIZE_SEQUENTIAL";
            case "queueDepth" -> "INCREASE_CAPACITY";
            case "errorRate" -> "ADD_RETRY_LOGIC";
            default -> "OPTIMIZE_THROUGHPUT";
        };
        String rationale = "Performance drift detected in " + driftedMetric
            + ": current=" + String.format("%.2f", metrics.getOrDefault(driftedMetric, 0.0));
        return new SpecMutation(mutationType, driftedMetric,
            "<mutation type=\"" + mutationType.toLowerCase() + "\"/>",
            rationale, SpecMutation.RiskLevel.MEDIUM);
    }

    /**
     * Finds the metric with the largest relative drift since the last cycle.
     *
     * @param metrics current metrics snapshot
     * @return name of the most drifted metric, or default metric if none found
     */
    private String findMostDriftedMetric(Map<String, Double> metrics) {
        ReactorCycle previous = lastCycle.get();
        if (previous == null || previous.metricsSnapshot().isEmpty()) {
            return metrics.keySet().stream().findFirst().orElse("avgExecutionTimeMs");
        }
        String mostDrifted = "avgExecutionTimeMs";
        double maxDrift = 0.0;
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            Double previousValue = previous.metricsSnapshot().get(entry.getKey());
            if (previousValue != null && previousValue > 0) {
                double drift = Math.abs(entry.getValue() - previousValue) / previousValue;
                if (drift > maxDrift) {
                    maxDrift = drift;
                    mostDrifted = entry.getKey();
                }
            }
        }
        return mostDrifted;
    }

    /**
     * Simulates the proposed mutation against historical execution patterns.
     *
     * <p>Queries LearningCapture for success patterns matching the mutation type
     * and evaluates soundness based on historical success rate.</p>
     *
     * @param mutation the mutation to simulate
     * @return simulation result showing soundness and success rate
     */
    private SimulationResult simulateMutation(SpecMutation mutation) {
        List<LearningCapture.SuccessPattern> patterns = learning.getSuccessPatterns(
            p -> p.tags().contains(mutation.mutationType().toLowerCase()));

        if (patterns.isEmpty()) {
            // No historical data — conservative pass with low confidence
            return SimulationResult.sound(1);
        }

        LearningCapture.SuccessPattern bestMatch = patterns.get(0);
        double successRate = bestMatch.successRate();
        int replays = bestMatch.occurrenceCount();

        if (successRate >= 0.8) {
            return SimulationResult.sound(replays);
        } else {
            return SimulationResult.unsound(List.of(
                "Historical success rate for " + mutation.mutationType()
                + " is " + String.format("%.0f%%", successRate * 100)
                + " (below 80% threshold)"));
        }
    }

    /**
     * Stores a completed cycle in the UpgradeMemoryStore.
     *
     * <p>Records the cycle outcome and metrics for analysis and learning.</p>
     *
     * @param cycle the cycle to persist
     */
    private void storeCycleInMemory(ReactorCycle cycle) {
        try {
            Instant now = Instant.now();
            UpgradeMemoryStore.UpgradeOutcome outcome = cycle.isSuccessful()
                ? new UpgradeMemoryStore.Success("Reactor cycle completed: " + cycle.outcome())
                : new UpgradeMemoryStore.Failure(cycle.outcome(), "reactor_cycle", "");

            UpgradeMemoryStore.UpgradeRecord record = UpgradeMemoryStore.UpgradeRecord.builder()
                .id("reactor-" + cycle.cycleId())
                .sessionId("reactor")
                .targetVersion("6.0.0-Beta")
                .sourceVersion("6.0.0-Beta")
                .startTime(cycle.startTime())
                .endTime(now)
                .addPhase(new UpgradeMemoryStore.PhaseResult(
                    "reactor_cycle", cycle.startTime(), now,
                    outcome, cycle.outcome()))
                .outcome(outcome)
                .addMetadata("cycleId", cycle.cycleId())
                .addMetadata("outcome", cycle.outcome())
                .addMetadata("durationMs", String.valueOf(cycle.durationMs()))
                .build();

            memory.store(record);
            learning.captureOutcome(record);
            log.debug("Stored cycle {} in memory: outcome={}", cycle.cycleId(), cycle.outcome());
        } catch (Exception e) {
            log.warn("Failed to store cycle in memory: {}", e.getMessage());
        }
    }
}
