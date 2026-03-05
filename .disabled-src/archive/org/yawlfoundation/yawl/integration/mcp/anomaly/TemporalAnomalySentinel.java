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

package org.yawlfoundation.yawl.integration.mcp.anomaly;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

/**
 * Temporal Anomaly Sentinel: Detects temporal anomalies in YAWL workflow execution.
 *
 * Analyzes running cases and work items to detect:
 * - Tasks dramatically over their expected time
 * - Cases that are stalled or making slow progress
 * - Patterns suggesting SLA violations before they happen
 *
 * Uses cross-case comparison of work item durations to establish baselines:
 * - Groups work items by (specId, taskId) across all cases
 * - Computes mean and standard deviation for each group
 * - Flags items deviating >3x the mean as anomalous
 * - For single-sample items, uses configurable default timeout threshold
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class TemporalAnomalySentinel {

    private static final double DEFAULT_EXPECTED_MINUTES = 60.0;
    private static final double CRITICAL_DEVIATION_FACTOR = 10.0;
    private static final double WARNING_DEVIATION_FACTOR = 5.0;
    private static final double CAUTION_DEVIATION_FACTOR = 2.0;

    /**
     * Analyzes work items for temporal anomalies.
     *
     * @param allWorkItems all running work items across all cases
     * @param defaultTimeoutMinutes default expected duration for unknown tasks
     * @return list of detected anomalies, sorted by risk score (highest first)
     */
    public static List<AnomalyRecord> detect(List<WorkItemRecord> allWorkItems,
                                              double defaultTimeoutMinutes) {
        if (allWorkItems == null || allWorkItems.isEmpty()) {
            return List.of();
        }

        // Group work items by (specId, taskId)
        Map<String, List<WorkItemRecord>> groupedByTask = groupBySpecTask(allWorkItems);

        // Compute baseline statistics for each group
        Map<String, TaskBaseline> baselines = computeBaselines(groupedByTask);

        // Detect anomalies
        List<AnomalyRecord> anomalies = new ArrayList<>();
        for (WorkItemRecord item : allWorkItems) {
            if (!isLiveItem(item)) {
                continue;
            }

            String taskKey = makeTaskKey(item);
            TaskBaseline baseline = baselines.get(taskKey);

            double elapsedMinutes = parseElapsedMinutes(item);
            if (elapsedMinutes < 0.1) {
                continue; // Skip items just enabled
            }

            double expectedMinutes = baseline != null ? baseline.expectedMinutes : defaultTimeoutMinutes;
            double deviationFactor = elapsedMinutes / expectedMinutes;

            if (deviationFactor >= CAUTION_DEVIATION_FACTOR) {
                int riskScore = computeRiskScore(deviationFactor);
                anomalies.add(new AnomalyRecord(
                    item.getCaseID(),
                    item.getTaskID(),
                    item.getSpecIdentifier(),
                    item.getTaskName(),
                    item.getStatus(),
                    elapsedMinutes,
                    expectedMinutes,
                    deviationFactor,
                    riskScore
                ));
            }
        }

        // Sort by risk score descending
        anomalies.sort((a, b) -> Integer.compare(b.riskScore, a.riskScore));
        return anomalies;
    }

    /**
     * Generates a formatted report of temporal anomalies.
     *
     * @param anomalies detected anomalies
     * @param totalCases total number of running cases
     * @return formatted report string
     */
    public static String generateReport(List<AnomalyRecord> anomalies, int totalCases) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("\u256D\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u256E\n");
        sb.append("\u2551").append(String.format("%-47s", "  TEMPORAL ANOMALY SENTINEL")).append("\u2551\n");
        sb.append("\u2551").append(String.format("%-47s", "  Real-time SLA Risk Detection \u2014 " +
            Instant.now().toString())).append("\u2551\n");
        sb.append("\u2570\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u256F\n\n");

        // Risk summary
        long criticalCount = anomalies.stream().filter(a -> a.riskScore >= 90).count();
        long warningCount = anomalies.stream().filter(a -> a.riskScore >= 70 && a.riskScore < 90).count();

        sb.append(String.format("RISK ASSESSMENT: %d running case%s, %d anomal%s detected\n",
            totalCases,
            totalCases == 1 ? "" : "s",
            anomalies.size(),
            anomalies.size() == 1 ? "y" : "ies"));
        sb.append("\u2500".repeat(59)).append("\n\n");

        if (anomalies.isEmpty()) {
            sb.append("\u2705  ALL SYSTEMS HEALTHY\n");
            sb.append("   No temporal anomalies detected.\n\n");
        } else {
            // Critical anomalies
            List<AnomalyRecord> critical = anomalies.stream()
                .filter(a -> a.riskScore >= 90)
                .collect(Collectors.toList());

            for (AnomalyRecord anomaly : critical) {
                appendAnomalyBlock(sb, anomaly, "\ud83d\udd34 CRITICAL ANOMALY");
                sb.append("\n");
            }

            // Warning anomalies
            List<AnomalyRecord> warnings = anomalies.stream()
                .filter(a -> a.riskScore >= 70 && a.riskScore < 90)
                .collect(Collectors.toList());

            for (AnomalyRecord anomaly : warnings) {
                appendAnomalyBlock(sb, anomaly, "\u26a0\ufe0f  WARNING ANOMALY");
                sb.append("\n");
            }

            // Healthy cases
            int healthyCount = totalCases - (int) criticalCount - (int) warningCount;
            if (healthyCount > 0) {
                sb.append("\u2705  HEALTHY\n");
                sb.append(String.format("   %d case%s on track, no anomalies detected.\n\n",
                    healthyCount, healthyCount == 1 ? "" : "s"));
            }
        }

        // Metrics footer
        sb.append("SENTINEL METRICS\n");
        sb.append("\u2500".repeat(28)).append("\n");
        sb.append(String.format("Monitored cases: %d | Anomalies: %d (CRITICAL:%d WARN:%d)\n",
            totalCases, anomalies.size(), criticalCount, warningCount));

        String health = anomalies.isEmpty() ? "HEALTHY" :
                       warningCount + criticalCount == totalCases ? "CRITICAL" :
                       "DEGRADED";
        double healthPercent = totalCases == 0 ? 100 :
                              (double) (totalCases - criticalCount - warningCount) / totalCases * 100;

        sb.append(String.format("Overall health: %s \u2014 %.0f%% of cases at risk\n",
            health, (100 - healthPercent)));

        return sb.toString();
    }

    // ===== Internal Helpers =====

    private static void appendAnomalyBlock(StringBuilder sb, AnomalyRecord anomaly, String severity) {
        sb.append(severity).append("\n");
        sb.append(String.format("   Case #%s | %s\n", anomaly.caseId, anomaly.specId));
        sb.append(String.format("   Work Item: %s [WI-%s:%s]\n",
            anomaly.taskName, anomaly.caseId, anomaly.taskId));
        sb.append(String.format("   Status: %s\n", formatStatus(anomaly.status)));
        sb.append(String.format("   Elapsed: %s | Benchmark: %s | Deviation: %.1fx expected\n",
            formatDuration(anomaly.elapsedMinutes),
            formatDuration(anomaly.expectedMinutes),
            anomaly.deviationFactor));
        sb.append(String.format("   Risk Score: %d/100 \u2014 %s\n",
            anomaly.riskScore,
            anomaly.riskScore >= 90 ? "SLA BREACH IMMINENT" :
            anomaly.riskScore >= 70 ? "Resource bottleneck detected" :
            "Monitor closely"));
        sb.append("   Action: ");
        if (anomaly.riskScore >= 90) {
            sb.append("Escalate immediately or reassign resource\n");
        } else if (anomaly.riskScore >= 70) {
            sb.append("Check resource availability\n");
        } else {
            sb.append("Review and monitor\n");
        }
    }

    private static String formatStatus(String status) {
        if ("Enabled".equals(status) || "Fired".equals(status)) {
            return "Enabled (not yet started)";
        } else if ("Executing".equals(status)) {
            return "Running";
        }
        return status;
    }

    private static String formatDuration(double minutes) {
        if (minutes < 1) {
            return String.format("%.1fs", minutes * 60);
        } else if (minutes < 60) {
            return String.format("%.0fm", minutes);
        } else {
            int hours = (int) (minutes / 60);
            int mins = (int) (minutes % 60);
            return String.format("%dh %dm", hours, mins);
        }
    }

    private static Map<String, List<WorkItemRecord>> groupBySpecTask(List<WorkItemRecord> items) {
        return items.stream()
            .filter(TemporalAnomalySentinel::isCompletedItem)
            .collect(Collectors.groupingBy(TemporalAnomalySentinel::makeTaskKey));
    }

    private static Map<String, TaskBaseline> computeBaselines(Map<String, List<WorkItemRecord>> grouped) {
        Map<String, TaskBaseline> baselines = new HashMap<>();

        for (Map.Entry<String, List<WorkItemRecord>> entry : grouped.entrySet()) {
            String taskKey = entry.getKey();
            List<WorkItemRecord> items = entry.getValue();

            if (items.isEmpty()) {
                continue;
            }

            // Compute durations for completed items
            List<Double> durations = new ArrayList<>();
            for (WorkItemRecord item : items) {
                double duration = parseElapsedMinutesForCompleted(item);
                if (duration > 0) {
                    durations.add(duration);
                }
            }

            if (durations.isEmpty()) {
                continue;
            }

            double mean = durations.stream().mapToDouble(Double::doubleValue).average().orElse(DEFAULT_EXPECTED_MINUTES);
            double stdDev = computeStdDev(durations, mean);

            // Use mean as expected time (with lower bound of 5 min)
            double expected = Math.max(mean, 5.0);

            baselines.put(taskKey, new TaskBaseline(expected, mean, stdDev));
        }

        return baselines;
    }

    private static double computeStdDev(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0;
        }
        double sumSquaredDiff = values.stream()
            .mapToDouble(v -> (v - mean) * (v - mean))
            .sum();
        return Math.sqrt(sumSquaredDiff / values.size());
    }

    private static int computeRiskScore(double deviationFactor) {
        if (deviationFactor >= CRITICAL_DEVIATION_FACTOR) {
            return 97; // 10x or more
        } else if (deviationFactor >= 8.0) {
            return 92;
        } else if (deviationFactor >= WARNING_DEVIATION_FACTOR) {
            return 75; // 5x-8x
        } else if (deviationFactor >= 3.0) {
            return 60;
        } else if (deviationFactor >= CAUTION_DEVIATION_FACTOR) {
            return 45; // 2x-3x
        } else {
            return Math.min(30, (int) (deviationFactor * 15));
        }
    }

    private static String makeTaskKey(WorkItemRecord item) {
        return item.getSpecIdentifier() + "::" + item.getTaskID();
    }

    private static boolean isLiveItem(WorkItemRecord item) {
        String status = item.getStatus();
        return "Enabled".equals(status) ||
               "Fired".equals(status) ||
               "Executing".equals(status);
    }

    private static boolean isCompletedItem(WorkItemRecord item) {
        String status = item.getStatus();
        return "Complete".equals(status) || "ForcedComplete".equals(status);
    }

    private static double parseElapsedMinutes(WorkItemRecord item) {
        String enablementTime = item.getEnablementTimeMs();
        if (enablementTime == null || enablementTime.isEmpty()) {
            return 0;
        }

        try {
            long enablementMs = Long.parseLong(enablementTime);
            if (enablementMs <= 0) {
                return 0;
            }
            return (System.currentTimeMillis() - enablementMs) / 60000.0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseElapsedMinutesForCompleted(WorkItemRecord item) {
        String enablementTime = item.getEnablementTimeMs();
        String completionTime = item.getCompletionTimeMs();

        if (enablementTime == null || enablementTime.isEmpty() ||
            completionTime == null || completionTime.isEmpty()) {
            return 0;
        }

        try {
            long enablementMs = Long.parseLong(enablementTime);
            long completionMs = Long.parseLong(completionTime);

            if (enablementMs <= 0 || completionMs <= 0) {
                return 0;
            }

            return (completionMs - enablementMs) / 60000.0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ===== Inner Classes =====

    /**
     * Record representing a detected temporal anomaly.
     */
    public static class AnomalyRecord {
        public final String caseId;
        public final String taskId;
        public final String specId;
        public final String taskName;
        public final String status;
        public final double elapsedMinutes;
        public final double expectedMinutes;
        public final double deviationFactor;
        public final int riskScore;

        public AnomalyRecord(String caseId, String taskId, String specId, String taskName,
                            String status, double elapsedMinutes, double expectedMinutes,
                            double deviationFactor, int riskScore) {
            this.caseId = caseId;
            this.taskId = taskId;
            this.specId = specId;
            this.taskName = taskName;
            this.status = status;
            this.elapsedMinutes = elapsedMinutes;
            this.expectedMinutes = expectedMinutes;
            this.deviationFactor = deviationFactor;
            this.riskScore = riskScore;
        }

        @Override
        public String toString() {
            return String.format("AnomalyRecord{case=%s, task=%s, risk=%d/100, deviation=%.1fx}",
                caseId, taskId, riskScore, deviationFactor);
        }
    }

    /**
     * Internal record for task baseline statistics.
     */
    private static class TaskBaseline {
        final double expectedMinutes;
        final double meanMinutes;
        final double stdDevMinutes;

        TaskBaseline(double expectedMinutes, double meanMinutes, double stdDevMinutes) {
            this.expectedMinutes = expectedMinutes;
            this.meanMinutes = meanMinutes;
            this.stdDevMinutes = stdDevMinutes;
        }
    }
}
