/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.integration.mcp.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Temporal Pressure Analyzer tool for the YAWL MCP server.
 *
 * <p>Analyzes time-dimension dynamics of live work items, identifying tasks that are
 * aging unusually fast or slowly, have expired timers, or are approaching deadline
 * pressure. Computes per-item age, wait time, and timer pressure metrics, then
 * aggregates per-task statistics (mean, p95, stddev) and flags items as urgent
 * based on: timer expiration, timer near-expiry, age outliers, or age thresholds.
 *
 * <p>Use cases:
 * <ul>
 *   <li>SLA monitoring: identify items nearing or past deadline</li>
 *   <li>Performance analysis: find unusually long-dwelling tasks</li>
 *   <li>Workload distribution: detect bottleneck tasks by age variance</li>
 *   <li>Escalation triggers: prioritize urgent items for human review</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class TemporalPressureTools {

    private TemporalPressureTools() {
        throw new UnsupportedOperationException(
            "TemporalPressureTools is a static factory class and cannot be instantiated.");
    }

    /**
     * Creates the temporal pressure analyzer tool specification.
     *
     * @param interfaceBClient connected InterfaceB client
     * @param sessionHandle    active YAWL session handle
     * @return tool specification for MCP registration
     */
    public static McpServerFeatures.SyncToolSpecification create(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (sessionHandle == null || sessionHandle.isBlank()) {
            throw new IllegalArgumentException("sessionHandle is required");
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("caseId", Map.of(
            "type", "string",
            "description", "Optional: case ID to scope analysis. If absent, analyzes all live work items."));
        props.put("urgentThresholdMinutes", Map.of(
            "type", "number",
            "description", "Optional: minutes after which an item is flagged urgent by age alone (default 30.0)."));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of(), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_analyze_temporal_pressure")
                .description(
                    "Analyze time-dimension dynamics of live work items to identify temporal pressure. " +
                    "Computes per-item age (time since enablement), wait time (time from enablement to start), " +
                    "and timer pressure (fraction of timer window elapsed). Aggregates per-task statistics " +
                    "(mean age, p95 age, stddev, max age) and flags items as urgent based on timer expiration, " +
                    "timer near-expiry, age outliers, or age exceeding threshold. Returns items sorted by urgency, " +
                    "with task-level statistics for bottleneck detection.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    long startMs = System.currentTimeMillis();

                    Map<String, Object> params = args.arguments();
                    String caseId = optionalStringArg(params, "caseId", null);
                    double urgentThresholdMinutes = ((Number) params.getOrDefault("urgentThresholdMinutes", 30.0)).doubleValue();

                    // Step 1: Collect work items
                    List<WorkItemRecord> items;
                    String scope;
                    if (caseId != null && !caseId.isBlank()) {
                        items = interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);
                        scope = "case_" + caseId;
                    } else {
                        items = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);
                        scope = "all_cases";
                    }

                    // Filter to live statuses only
                    items = items.stream()
                        .filter(i -> WorkItemRecord.statusEnabled.equals(i.getStatus())
                                  || WorkItemRecord.statusFired.equals(i.getStatus())
                                  || WorkItemRecord.statusExecuting.equals(i.getStatus()))
                        .collect(Collectors.toList());

                    long nowMs = System.currentTimeMillis();

                    // Step 2: Per-item time computation
                    List<Map<String, Object>> allItems = new ArrayList<>();
                    Map<String, List<Double>> taskAgeMap = new HashMap<>();

                    for (WorkItemRecord item : items) {
                        long enabledMs = parseMs(item.getEnablementTimeMs());
                        long startMs2 = parseMs(item.getStartTimeMs());
                        long expiryMs = parseMs(item.getTimerExpiry());

                        Double ageMinutes = enabledMs > 0 ? (nowMs - enabledMs) / 60000.0 : null;
                        Double waitMinutes = (startMs2 > 0 && enabledMs > 0) ? (startMs2 - enabledMs) / 60000.0 : null;

                        // Pressure ratio: how far through the timer window are we?
                        Double pressureRatio = null;
                        String timerStatus = "NONE";
                        if (expiryMs > 0) {
                            if (expiryMs <= nowMs) {
                                timerStatus = "EXPIRED";
                                pressureRatio = 2.0;  // sentinel > 1.0 signals expiration
                            } else {
                                timerStatus = "ACTIVE";
                                if (enabledMs > 0) {
                                    double elapsed = nowMs - enabledMs;
                                    double window = expiryMs - enabledMs;
                                    pressureRatio = window > 0 ? clamp(elapsed / window, 0.0, 2.0) : null;
                                }
                            }
                        }

                        // Build item map (for JSON output)
                        Map<String, Object> itemData = new LinkedHashMap<>();
                        itemData.put("work_item_id", item.getID());
                        itemData.put("task_id", item.getTaskID());
                        itemData.put("case_id", item.getCaseID());
                        itemData.put("status", item.getStatus());
                        if (ageMinutes != null) {
                            itemData.put("age_minutes", Math.round(ageMinutes * 10.0) / 10.0);
                        }
                        if (waitMinutes != null) {
                            itemData.put("wait_minutes", Math.round(waitMinutes * 10.0) / 10.0);
                        }
                        itemData.put("timer_status", timerStatus);
                        if (pressureRatio != null) {
                            itemData.put("pressure_ratio", Math.round(pressureRatio * 100.0) / 100.0);
                        }

                        allItems.add(itemData);

                        // Accumulate ages by task for stats
                        String taskId = item.getTaskID();
                        if (ageMinutes != null && taskId != null && !taskId.isBlank()) {
                            taskAgeMap.computeIfAbsent(taskId, k -> new ArrayList<>()).add(ageMinutes);
                        }
                    }

                    // Step 3: Per-task statistics
                    Map<String, Object> taskStatsMap = new LinkedHashMap<>();
                    Map<String, TaskStats> taskStatsCache = new HashMap<>();

                    for (String taskId : taskAgeMap.keySet()) {
                        List<Double> ages = taskAgeMap.get(taskId).stream()
                            .filter(Objects::nonNull)
                            .sorted()
                            .collect(Collectors.toList());

                        if (ages.isEmpty()) {
                            continue;
                        }

                        double count = ages.size();
                        double sum = ages.stream().mapToDouble(Double::doubleValue).sum();
                        double mean = sum / count;
                        double p95Index = Math.ceil(count * 0.95) - 1;
                        double p95 = ages.get((int) Math.min(p95Index, ages.size() - 1));
                        double max = ages.get(ages.size() - 1);

                        double variance = ages.stream()
                            .mapToDouble(a -> (a - mean) * (a - mean))
                            .sum() / count;
                        double stddev = Math.sqrt(variance);

                        TaskStats stats = new TaskStats(
                            (int) count,
                            Math.round(mean * 10.0) / 10.0,
                            Math.round(p95 * 10.0) / 10.0,
                            Math.round(max * 10.0) / 10.0,
                            Math.round(stddev * 10.0) / 10.0
                        );
                        taskStatsCache.put(taskId, stats);

                        Map<String, Object> taskStats = new LinkedHashMap<>();
                        taskStats.put("count", stats.count);
                        taskStats.put("mean_age_minutes", stats.mean);
                        taskStats.put("p95_age_minutes", stats.p95);
                        taskStats.put("max_age_minutes", stats.max);
                        taskStats.put("stddev_minutes", stats.stddev);
                        taskStatsMap.put(taskId, taskStats);
                    }

                    // Step 4 & 5: Age outlier detection + build urgent items list
                    List<Map<String, Object>> urgentItems = new ArrayList<>();

                    for (Map<String, Object> itemData : allItems) {
                        String taskId = (String) itemData.get("task_id");
                        Double ageMinutes = (Double) itemData.get("age_minutes");
                        Double pressureRatio = (Double) itemData.get("pressure_ratio");
                        String timerStatus = (String) itemData.get("timer_status");

                        String urgencyReason = null;

                        if ("EXPIRED".equals(timerStatus)) {
                            urgencyReason = "TIMER_EXPIRED";
                        } else if (pressureRatio != null && pressureRatio > 0.8) {
                            urgencyReason = "TIMER_NEAR_EXPIRY";
                        } else if (ageMinutes != null && taskId != null) {
                            TaskStats taskStats = taskStatsCache.get(taskId);
                            if (taskStats != null) {
                                double thresholdOutlier = taskStats.mean + 2.0 * taskStats.stddev;
                                if (ageMinutes > thresholdOutlier && ageMinutes > urgentThresholdMinutes) {
                                    urgencyReason = "AGE_OUTLIER";
                                }
                            }
                        }

                        if (urgencyReason == null && ageMinutes != null && ageMinutes > urgentThresholdMinutes) {
                            urgencyReason = "AGE_THRESHOLD";
                        }

                        if (urgencyReason != null) {
                            itemData.put("urgency_reason", urgencyReason);
                            urgentItems.add(itemData);
                        }
                    }

                    // Step 6: Sort urgent items by urgency priority
                    urgentItems.sort((a, b) -> {
                        // First: TIMER_EXPIRED (urgencyReason == "TIMER_EXPIRED")
                        String reasonA = (String) a.get("urgency_reason");
                        String reasonB = (String) b.get("urgency_reason");

                        if ("TIMER_EXPIRED".equals(reasonA) && !"TIMER_EXPIRED".equals(reasonB)) {
                            return -1;
                        } else if ("TIMER_EXPIRED".equals(reasonB) && !"TIMER_EXPIRED".equals(reasonA)) {
                            return 1;
                        }

                        // Then: by pressure_ratio descending (null treated as 0.0)
                        Double pressureA = (Double) a.get("pressure_ratio");
                        Double pressureB = (Double) b.get("pressure_ratio");
                        if (pressureA == null) pressureA = 0.0;
                        if (pressureB == null) pressureB = 0.0;
                        int pressureCmp = Double.compare(pressureB, pressureA);
                        if (pressureCmp != 0) {
                            return pressureCmp;
                        }

                        // Then: by age_minutes descending (null treated as 0.0)
                        Double ageA = (Double) a.get("age_minutes");
                        Double ageB = (Double) b.get("age_minutes");
                        if (ageA == null) ageA = 0.0;
                        if (ageB == null) ageB = 0.0;
                        return Double.compare(ageB, ageA);
                    });

                    long elapsedMs = System.currentTimeMillis() - startMs;

                    // Build final JSON result
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("scope", scope);
                    result.put("urgent_threshold_minutes", urgentThresholdMinutes);
                    result.put("total_items_analyzed", items.size());
                    result.put("urgent_item_count", urgentItems.size());
                    result.put("urgent_items", urgentItems);
                    result.put("task_statistics", taskStatsMap);
                    result.put("analysis_time_ms", elapsedMs);

                    return textResult(new ObjectMapper()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(result));

                } catch (Exception e) {
                    return errorResult("Error analyzing temporal pressure: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Parse epoch milliseconds from string representation.
     * Returns 0 if string is null, blank, or unparseable.
     */
    private static long parseMs(String msStr) {
        if (msStr == null || msStr.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(msStr.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Clamp a value to the range [min, max].
     */
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static McpSchema.CallToolResult textResult(String text) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(message)), true, null, null);
    }

    private static String optionalStringArg(Map<String, Object> args, String name,
                                             String defaultValue) {
        Object value = args.get(name);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Simple record for per-task timing statistics.
     */
    private static class TaskStats {
        final int count;
        final double mean;
        final double p95;
        final double max;
        final double stddev;

        TaskStats(int count, double mean, double p95, double max, double stddev) {
            this.count = count;
            this.mean = mean;
            this.p95 = p95;
            this.max = max;
            this.stddev = stddev;
        }
    }
}
