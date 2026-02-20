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

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Detects workflow bottlenecks and parallelization opportunities.
 *
 * Identifies slowest tasks in workflows and alerts when bottlenecks change.
 * Suggests parallelization strategies for independent task paths. Provides
 * real-time bottleneck analysis with minimal computational overhead.
 *
 * <p><b>Detection Strategy</b>
 * <ul>
 *   <li>Track execution time per task in each workflow spec</li>
 *   <li>Identify task consuming most total time (bottleneck)</li>
 *   <li>Alert when bottleneck changes or threshold exceeded</li>
 *   <li>Analyze task dependencies to suggest parallelization</li>
 *   <li>Calculate potential improvement from parallelization</li>
 * </ul>
 *
 * <p><b>Metrics Tracked</b>
 * <ul>
 *   <li>Total time contributed by each task</li>
 *   <li>Queue depth (pending work items)</li>
 *   <li>Wait time vs. execution time ratio</li>
 *   <li>Task resource consumption</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class BottleneckDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(BottleneckDetector.class);
    private static final double BOTTLENECK_THRESHOLD_PERCENT = 0.30; // 30% of total time
    private static final int MIN_EXECUTIONS_FOR_ANALYSIS = 5;

    /**
     * Bottleneck alert information.
     */
    public record BottleneckAlert(
            String specId,
            String taskName,
            double contributionPercent,
            long avgDurationMs,
            long queueDepth,
            String suggestion,
            Instant detectedAt
    ) {
        @Override
        public String toString() {
            return String.format("%s.%s: %.1f%% of time (%dms avg, queue=%d)",
                    specId, taskName, contributionPercent * 100, avgDurationMs, queueDepth);
        }
    }

    /**
     * Parallelization opportunity.
     */
    public record ParallelizationOpportunity(
            String specId,
            List<String> independentTasks,
            double expectedSpeedup,
            String rationale,
            Instant identifiedAt
    ) {
        @Override
        public String toString() {
            return String.format("%s: Parallelize %s (%.1fx speedup expected)",
                    specId, independentTasks, expectedSpeedup);
        }
    }

    private static class TaskBottleneckMetrics {
        final String specId;
        final String taskName;
        long totalExecutions = 0;
        long totalDuration = 0;
        long totalWaitTime = 0;
        AtomicLong queueDepth = new AtomicLong(0);
        long minDuration = Long.MAX_VALUE;
        long maxDuration = 0;
        Instant lastUpdated = Instant.now();

        TaskBottleneckMetrics(String specId, String taskName) {
            this.specId = specId;
            this.taskName = taskName;
        }

        synchronized void recordExecution(long durationMs, long waitTimeMs) {
            totalExecutions++;
            totalDuration += durationMs;
            totalWaitTime += waitTimeMs;
            minDuration = Math.min(minDuration, durationMs);
            maxDuration = Math.max(maxDuration, durationMs);
            lastUpdated = Instant.now();
        }

        synchronized double getAvgDuration() {
            return totalExecutions == 0 ? 0 : (double) totalDuration / totalExecutions;
        }

        synchronized double getContributionPercent(long totalSpecDuration) {
            return totalSpecDuration == 0 ? 0 : (double) totalDuration / totalSpecDuration;
        }

        synchronized double getWaitToExecutionRatio() {
            return totalDuration == 0 ? 0 : (double) totalWaitTime / totalDuration;
        }
    }

    private final MeterRegistry meterRegistry;
    private final Map<String, Map<String, TaskBottleneckMetrics>> specTaskMetrics;
    private final Map<String, BottleneckAlert> currentBottlenecks;
    private final List<BottleneckAlert> alertHistory;
    private final List<ParallelizationOpportunity> parallelOpportunities;
    private final List<Consumer<BottleneckAlert>> alertListeners;

    /**
     * Creates a new bottleneck detector.
     *
     * @param meterRegistry metrics registry for observability
     */
    public BottleneckDetector(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.specTaskMetrics = new ConcurrentHashMap<>();
        this.currentBottlenecks = new ConcurrentHashMap<>();
        this.alertHistory = new CopyOnWriteArrayList<>();
        this.parallelOpportunities = new CopyOnWriteArrayList<>();
        this.alertListeners = new CopyOnWriteArrayList<>();
        registerMetrics();
    }

    /**
     * Records task execution for bottleneck analysis.
     *
     * @param specId specification ID
     * @param taskName task name
     * @param durationMs execution duration
     * @param waitTimeMs time waiting in queue
     */
    public void recordTaskExecution(String specId, String taskName, long durationMs, long waitTimeMs) {
        Objects.requireNonNull(specId);
        Objects.requireNonNull(taskName);

        if (durationMs < 0 || waitTimeMs < 0) {
            return;
        }

        Map<String, TaskBottleneckMetrics> tasks = specTaskMetrics.computeIfAbsent(
                specId, k -> new ConcurrentHashMap<>());

        TaskBottleneckMetrics metrics = tasks.computeIfAbsent(taskName,
                k -> new TaskBottleneckMetrics(specId, taskName));

        metrics.recordExecution(durationMs, waitTimeMs);

        if (metrics.totalExecutions % 10 == 0) {
            analyzeBottlenecks(specId);
        }
    }

    /**
     * Updates queue depth for a task.
     *
     * @param specId specification ID
     * @param taskName task name
     * @param queueSize current queue depth
     */
    public void updateQueueDepth(String specId, String taskName, long queueSize) {
        Objects.requireNonNull(specId);
        Objects.requireNonNull(taskName);

        Map<String, TaskBottleneckMetrics> tasks = specTaskMetrics.get(specId);
        if (tasks != null) {
            TaskBottleneckMetrics metrics = tasks.get(taskName);
            if (metrics != null) {
                metrics.queueDepth.set(queueSize);
            }
        }
    }

    /**
     * Analyzes workflow bottlenecks.
     *
     * @param specId specification ID
     */
    private void analyzeBottlenecks(String specId) {
        Map<String, TaskBottleneckMetrics> tasks = specTaskMetrics.get(specId);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        long totalDuration = tasks.values().stream()
                .mapToLong(t -> t.totalDuration)
                .sum();

        if (totalDuration == 0) {
            return;
        }

        tasks.values().stream()
                .filter(m -> m.totalExecutions >= MIN_EXECUTIONS_FOR_ANALYSIS)
                .forEach(metrics -> {
                    double contribution = metrics.getContributionPercent(totalDuration);
                    if (contribution > BOTTLENECK_THRESHOLD_PERCENT) {
                        detectBottleneck(specId, metrics, contribution);
                    }
                });
    }

    private void detectBottleneck(String specId, TaskBottleneckMetrics metrics, double contribution) {
        String key = specId + ":" + metrics.taskName;
        BottleneckAlert alert = new BottleneckAlert(
                specId,
                metrics.taskName,
                contribution,
                (long) metrics.getAvgDuration(),
                metrics.queueDepth.get(),
                suggestOptimization(metrics),
                Instant.now()
        );

        BottleneckAlert previous = currentBottlenecks.put(key, alert);
        if (previous == null || !previous.taskName.equals(alert.taskName)) {
            LOGGER.warn("Bottleneck detected: {}", alert);
            alertHistory.add(alert);
            notifyListeners(alert);
            meterRegistry.counter("yawl.bottleneck.detected",
                    "spec", specId, "task", metrics.taskName).increment();
        }

        meterRegistry.gauge("yawl.bottleneck.contribution",
                java.util.Collections.singletonList(
                        io.micrometer.core.instrument.Tag.of("task", metrics.taskName)),
                contribution);
    }

    private String suggestOptimization(TaskBottleneckMetrics metrics) {
        double waitRatio = metrics.getWaitToExecutionRatio();
        long queueSize = metrics.queueDepth.get();

        if (queueSize > 5) {
            return String.format("High queue depth (%d): increase parallelism or scale horizontally", queueSize);
        } else if (waitRatio > 0.5) {
            return "High wait time: consider task splitting or dependency optimization";
        } else {
            return "Long execution time: consider distributed execution or caching";
        }
    }

    /**
     * Identifies parallelization opportunities based on task analysis.
     *
     * @param specId specification ID
     * @param independentTasks list of tasks that can run in parallel
     * @param expectedSpeedup expected speedup factor
     */
    public void suggestParallelization(String specId, List<String> independentTasks, double expectedSpeedup) {
        Objects.requireNonNull(specId);
        Objects.requireNonNull(independentTasks);

        ParallelizationOpportunity opp = new ParallelizationOpportunity(
                specId,
                new ArrayList<>(independentTasks),
                expectedSpeedup,
                "Tasks can execute in parallel without dependency conflicts",
                Instant.now()
        );

        parallelOpportunities.add(opp);
        LOGGER.info("Parallelization opportunity: {}", opp);
        meterRegistry.counter("yawl.bottleneck.parallelization_opportunity",
                "spec", specId).increment();
    }

    /**
     * Registers listener for bottleneck alerts.
     *
     * @param listener consumer to receive alerts
     */
    public void onBottleneckDetected(Consumer<BottleneckAlert> listener) {
        Objects.requireNonNull(listener);
        alertListeners.add(listener);
    }

    /**
     * Gets current bottlenecks.
     *
     * @return map of spec:task to bottleneck alert
     */
    public Map<String, BottleneckAlert> getCurrentBottlenecks() {
        return new HashMap<>(currentBottlenecks);
    }

    /**
     * Gets bottlenecks for a specific specification.
     *
     * @param specId specification ID
     * @return list of bottleneck alerts for that spec
     */
    public List<BottleneckAlert> getBottlenecksForSpec(String specId) {
        Objects.requireNonNull(specId);
        return currentBottlenecks.values().stream()
                .filter(alert -> specId.equals(alert.specId))
                .collect(Collectors.toList());
    }

    /**
     * Gets recent bottleneck alerts.
     *
     * @param limitMinutes limit to alerts from last N minutes
     * @return list of recent alerts
     */
    public List<BottleneckAlert> getRecentAlerts(int limitMinutes) {
        long cutoffTime = System.currentTimeMillis() - (long) limitMinutes * 60000;
        return alertHistory.stream()
                .filter(alert -> alert.detectedAt.toEpochMilli() > cutoffTime)
                .collect(Collectors.toList());
    }

    /**
     * Gets parallelization opportunities.
     *
     * @return list of opportunities
     */
    public List<ParallelizationOpportunity> getParallelizationOpportunities() {
        return new ArrayList<>(parallelOpportunities);
    }

    /**
     * Gets statistics for monitoring.
     *
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeBottlenecks", currentBottlenecks.size());
        stats.put("totalAlerts", alertHistory.size());
        stats.put("parallelizationOpportunities", parallelOpportunities.size());
        stats.put("specsMonitored", specTaskMetrics.size());
        stats.put("tasksMonitored", specTaskMetrics.values().stream()
                .mapToInt(Map::size)
                .sum());
        return stats;
    }

    private void notifyListeners(BottleneckAlert alert) {
        alertListeners.forEach(listener -> {
            try {
                listener.accept(alert);
            } catch (Exception e) {
                LOGGER.error("Bottleneck listener failed", e);
            }
        });
    }

    private void registerMetrics() {
        meterRegistry.gaugeCollectionSize("yawl.bottleneck.active_bottlenecks",
                Collections.emptyList(), currentBottlenecks.keySet());
        meterRegistry.gaugeCollectionSize("yawl.bottleneck.alert_history",
                Collections.emptyList(), alertHistory);
    }
}
