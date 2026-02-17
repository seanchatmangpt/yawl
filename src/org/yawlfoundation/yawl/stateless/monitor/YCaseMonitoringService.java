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

package org.yawlfoundation.yawl.stateless.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides real-time case monitoring data for dashboards and analytics.
 * Collects statistics, metrics, and performance data from running cases.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
public class YCaseMonitoringService {

    private static final Logger logger = LoggerFactory.getLogger(YCaseMonitoringService.class);

    private final YCaseMonitor _caseMonitor;
    private final Map<YIdentifier, CaseMetrics> _metricsCache;

    /**
     * Creates a new case monitoring service.
     * @param caseMonitor the case monitor to gather data from
     */
    public YCaseMonitoringService(YCaseMonitor caseMonitor) {
        if (caseMonitor == null) {
            throw new IllegalArgumentException("Case monitor cannot be null");
        }
        _caseMonitor = caseMonitor;
        _metricsCache = new ConcurrentHashMap<>();
    }

    /**
     * Gets current case statistics across all monitored cases.
     * @return aggregated case statistics
     */
    public CaseStatistics getCaseStatistics() {
        List<YCase> allCases = getAllCases();

        CaseStatistics stats = new CaseStatistics();
        stats.totalCases = allCases.size();
        stats.activeCases = allCases.size();
        stats.timestamp = System.currentTimeMillis();

        if (!allCases.isEmpty()) {
            List<Long> completionTimes = new ArrayList<>();

            for (YCase yCase : allCases) {
                YNetRunner runner = yCase.getRunner();
                if (runner != null) {
                    if (runner.isCompleted()) {
                        stats.completedCases++;
                    }
                }
            }

            if (!completionTimes.isEmpty()) {
                stats.avgCompletionTimeMs = completionTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0);
            }
        }

        logger.debug("Generated case statistics: total={}, active={}, completed={}",
                    stats.totalCases, stats.activeCases, stats.completedCases);

        return stats;
    }

    /**
     * Gets work item distribution across tasks.
     * @return map of task ID to work item count
     */
    public Map<String, Integer> getWorkItemDistribution() {
        Map<String, Integer> distribution = new HashMap<>();

        for (YCase yCase : getAllCases()) {
            YNetRunner runner = yCase.getRunner();
            if (runner != null) {
                for (YNetRunner netRunner : runner.getAllRunnersForCase()) {
                    for (YWorkItem item : netRunner.getWorkItemRepository().getWorkItems()) {
                        String taskID = item.getTaskID();
                        distribution.put(taskID, distribution.getOrDefault(taskID, 0) + 1);
                    }
                }
            }
        }

        logger.debug("Work item distribution calculated: {} unique tasks", distribution.size());
        return distribution;
    }

    /**
     * Gets performance metrics grouped by task.
     * @return map of task ID to task performance metrics
     */
    public Map<String, TaskMetrics> getTaskPerformance() {
        Map<String, TaskMetrics> metrics = new HashMap<>();

        for (YCase yCase : getAllCases()) {
            YNetRunner runner = yCase.getRunner();
            if (runner != null) {
                for (YNetRunner netRunner : runner.getAllRunnersForCase()) {
                    for (YWorkItem item : netRunner.getWorkItemRepository().getWorkItems()) {
                        String taskID = item.getTaskID();
                        TaskMetrics taskMetrics = metrics.computeIfAbsent(
                            taskID,
                            k -> new TaskMetrics(taskID)
                        );

                        Instant enablementTime = item.getEnablementTime();
                        if (enablementTime != null && !enablementTime.equals(Instant.EPOCH)) {
                            long duration = System.currentTimeMillis() - enablementTime.toEpochMilli();
                            taskMetrics.addDuration(duration);
                            taskMetrics.incrementCount();
                        }
                    }
                }
            }
        }

        logger.debug("Task performance metrics calculated for {} tasks", metrics.size());
        return metrics;
    }

    /**
     * Gets metrics for a specific case.
     * @param caseID the case identifier
     * @return case metrics, or null if case not found
     */
    public CaseMetrics getCaseMetrics(YIdentifier caseID) {
        if (!_caseMonitor.hasCase(caseID)) {
            return null;
        }

        CaseMetrics metrics = _metricsCache.get(caseID);
        if (metrics == null || isStale(metrics)) {
            metrics = calculateCaseMetrics(caseID);
            _metricsCache.put(caseID, metrics);
        }

        return metrics;
    }

    /**
     * Gets the top N slowest running cases.
     * @param limit the maximum number of cases to return
     * @return list of case IDs with their execution times
     */
    public List<Map.Entry<YIdentifier, Long>> getSlowestCases(int limit) {
        Map<YIdentifier, Long> caseDurations = new HashMap<>();

        for (YCase yCase : getAllCases()) {
            YNetRunner runner = yCase.getRunner();
            if (runner != null) {
                YIdentifier caseID = runner.getCaseID();
                long duration = System.currentTimeMillis() - runner.getStartTime();
                caseDurations.put(caseID, duration);
            }
        }

        return caseDurations.entrySet().stream()
            .sorted(Map.Entry.<YIdentifier, Long>comparingByValue().reversed())
            .limit(limit)
            .toList();
    }

    /**
     * Clears the metrics cache.
     */
    public void clearCache() {
        _metricsCache.clear();
        logger.info("Metrics cache cleared");
    }

    /**
     * Gets all monitored cases.
     * @return list of all active cases
     */
    private List<YCase> getAllCases() {
        return _caseMonitor.getAllCases();
    }

    /**
     * Calculates metrics for a specific case.
     * @param caseID the case identifier
     * @return calculated case metrics
     */
    private CaseMetrics calculateCaseMetrics(YIdentifier caseID) {
        CaseMetrics metrics = new CaseMetrics(caseID);
        metrics.calculatedAt = System.currentTimeMillis();

        return metrics;
    }

    /**
     * Checks if cached metrics are stale.
     * @param metrics the metrics to check
     * @return true if stale, false otherwise
     */
    private boolean isStale(CaseMetrics metrics) {
        long age = System.currentTimeMillis() - metrics.calculatedAt;
        return age > 5000;
    }

    /**
     * Aggregated statistics for all cases.
     */
    public static class CaseStatistics {
        public int totalCases;
        public int activeCases;
        public int completedCases;
        public int cancelledCases;
        public double avgCompletionTimeMs;
        public long timestamp;

        @Override
        public String toString() {
            return String.format("CaseStatistics[total=%d, active=%d, completed=%d, " +
                               "cancelled=%d, avgCompletion=%.2fms]",
                               totalCases, activeCases, completedCases,
                               cancelledCases, avgCompletionTimeMs);
        }
    }

    /**
     * Performance metrics for a specific task.
     */
    public static class TaskMetrics {
        private final String taskID;
        private final List<Long> durations;
        private int executionCount;

        public TaskMetrics(String taskID) {
            this.taskID = taskID;
            this.durations = new ArrayList<>();
            this.executionCount = 0;
        }

        public void addDuration(long durationMs) {
            durations.add(durationMs);
        }

        public void incrementCount() {
            executionCount++;
        }

        public String getTaskID() {
            return taskID;
        }

        public long getAverageDurationMs() {
            if (durations.isEmpty()) {
                return 0;
            }
            return (long) durations.stream().mapToLong(Long::longValue).average().orElse(0);
        }

        public long getMaxDurationMs() {
            if (durations.isEmpty()) {
                return 0;
            }
            return durations.stream().mapToLong(Long::longValue).max().orElse(0);
        }

        public long getMinDurationMs() {
            if (durations.isEmpty()) {
                return 0;
            }
            return durations.stream().mapToLong(Long::longValue).min().orElse(0);
        }

        public int getExecutionCount() {
            return executionCount;
        }

        @Override
        public String toString() {
            return String.format("TaskMetrics[task=%s, count=%d, avg=%.2fms, min=%dms, max=%dms]",
                               taskID, executionCount, (double) getAverageDurationMs(),
                               getMinDurationMs(), getMaxDurationMs());
        }
    }

    /**
     * Metrics for a specific case.
     */
    public static class CaseMetrics {
        public final YIdentifier caseID;
        public long calculatedAt;
        public int workItemCount;
        public long runningTimeMs;

        public CaseMetrics(YIdentifier caseID) {
            this.caseID = caseID;
        }
    }
}
