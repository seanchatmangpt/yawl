/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.mcp.diff;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yawlfoundation.yawl.engine.interfce.SpecificationData;

/**
 * Semantic workflow diff engine that compares two YAWL specifications
 * and identifies behavioral changes beyond XML-level diffs.
 *
 * <p>Analyzes structural metrics (tasks, splits, joins, conditions, parameters),
 * identifies added/removed tasks, detects complexity deltas, and computes
 * change magnitude (NONE, MINOR, MODERATE, MAJOR).
 *
 * <p>Generates human-readable ASCII diff reports showing workflow behavioral impact.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WorkflowBehavioralDiffer {

    private WorkflowBehavioralDiffer() {
        throw new UnsupportedOperationException(
            "WorkflowBehavioralDiffer is a utility class and cannot be instantiated.");
    }

    /**
     * Result record for workflow behavioral diff.
     *
     * @param spec1Name name of first specification
     * @param spec2Name name of second specification
     * @param spec1Version version of first specification
     * @param spec2Version version of second specification
     * @param addedTasks set of task IDs added in v2
     * @param removedTasks set of task IDs removed in v2
     * @param changedTasks set of task IDs with changes in v2
     * @param metricsV1 metrics map for v1
     * @param metricsV2 metrics map for v2
     * @param v1Fingerprint 6-char hex fingerprint of v1
     * @param v2Fingerprint 6-char hex fingerprint of v2
     * @param structuralChangeCount total count of structural changes
     * @param magnitude change magnitude (NONE, MINOR, MODERATE, MAJOR)
     * @param regressionRisk risk level (LOW, MEDIUM, HIGH)
     */
    public record DiffResult(
            String spec1Name,
            String spec2Name,
            String spec1Version,
            String spec2Version,
            Set<String> addedTasks,
            Set<String> removedTasks,
            Set<String> changedTasks,
            Map<String, Integer> metricsV1,
            Map<String, Integer> metricsV2,
            String v1Fingerprint,
            String v2Fingerprint,
            int structuralChangeCount,
            String magnitude,
            String regressionRisk) {
    }

    /**
     * Computes semantic diff between two workflow specifications.
     *
     * @param spec1 first specification (from)
     * @param spec2 second specification (to)
     * @return DiffResult with comprehensive behavioral analysis
     * @throws IllegalArgumentException if either spec is null or lacks XML
     */
    public static DiffResult diff(SpecificationData spec1, SpecificationData spec2) {
        if (spec1 == null || spec2 == null) {
            throw new IllegalArgumentException("Both specifications must be non-null");
        }

        String xml1 = spec1.getAsXML();
        String xml2 = spec2.getAsXML();

        if (xml1 == null || xml1.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Specification 1 (" + spec1.getName() + ") has no XML data");
        }
        if (xml2 == null || xml2.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Specification 2 (" + spec2.getName() + ") has no XML data");
        }

        // Extract task IDs and metrics
        Set<String> tasksV1 = extractTaskIds(xml1);
        Set<String> tasksV2 = extractTaskIds(xml2);

        Map<String, Integer> metricsV1 = extractStructuralMetrics(xml1);
        Map<String, Integer> metricsV2 = extractStructuralMetrics(xml2);

        // Compute task differences
        Set<String> addedTasks = new HashSet<>(tasksV2);
        addedTasks.removeAll(tasksV1);

        Set<String> removedTasks = new HashSet<>(tasksV1);
        removedTasks.removeAll(tasksV2);

        Set<String> commonTasks = new HashSet<>(tasksV1);
        commonTasks.retainAll(tasksV2);

        // Detect task attribute changes (simplified: check if task appears differently)
        Set<String> changedTasks = detectTaskChanges(xml1, xml2, commonTasks);

        // Compute fingerprints
        String v1Fingerprint = computeFingerprint(metricsV1);
        String v2Fingerprint = computeFingerprint(metricsV2);

        // Count structural changes
        int changeCount = addedTasks.size() + removedTasks.size() + changedTasks.size()
                + countMetricChanges(metricsV1, metricsV2);

        // Determine magnitude
        String magnitude = determineMagnitude(changeCount);

        // Assess regression risk
        String regressionRisk = assessRegressionRisk(xml1, xml2, addedTasks, removedTasks, changedTasks);

        return new DiffResult(
                spec1.getName(),
                spec2.getName(),
                spec1.getID().getVersionAsString(),
                spec2.getID().getVersionAsString(),
                addedTasks,
                removedTasks,
                changedTasks,
                metricsV1,
                metricsV2,
                v1Fingerprint,
                v2Fingerprint,
                changeCount,
                magnitude,
                regressionRisk);
    }

    /**
     * Generates human-readable ASCII diff report.
     *
     * @param diff the diff result
     * @return formatted ASCII report
     */
    public static String generateReport(DiffResult diff) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("╔═══════════════════════════════════════════════════════════════════╗\n");
        sb.append("║         WORKFLOW BEHAVIORAL DIFF                                  ║\n");
        sb.append("║  ").append(padRight(diff.spec1Name, 24)).append(" v")
                .append(padRight(diff.spec1Version, 5)).append(" → ")
                .append(diff.spec2Name).append(" v").append(diff.spec2Version).append("  ║\n");
        sb.append("╚═══════════════════════════════════════════════════════════════════╝\n\n");

        // Structural Changes
        sb.append("STRUCTURAL CHANGES\n");
        sb.append("──────────────────\n");

        if (diff.addedTasks.isEmpty() && diff.removedTasks.isEmpty() && diff.changedTasks.isEmpty()) {
            sb.append("(no structural task changes)\n\n");
        } else {
            for (String taskId : diff.addedTasks) {
                sb.append("+ ADDED    Task '").append(taskId).append("'\n");
                sb.append("           Position: inferred from XML order\n");
                sb.append("           Impact: +1 task on potential execution path\n\n");
            }

            for (String taskId : diff.removedTasks) {
                sb.append("- REMOVED  Task '").append(taskId).append("'\n");
                sb.append("           Impact: Execution path pruned\n\n");
            }

            for (String taskId : diff.changedTasks) {
                sb.append("~ CHANGED  Task '").append(taskId).append("'\n");
                sb.append("           Attributes or configuration updated\n\n");
            }
        }

        // Complexity Delta
        sb.append("COMPLEXITY DELTA\n");
        sb.append("────────────────\n");
        sb.append(String.format("%-25s %7s %7s %10s\n", "Metric", "v1", "v2", "Change"));
        sb.append(String.format("%-25s %7s %7s %10s\n", "─────────────────────", "───", "───", "──────"));

        Set<String> allMetrics = new HashSet<>(diff.metricsV1.keySet());
        allMetrics.addAll(diff.metricsV2.keySet());

        for (String metric : allMetrics) {
            int v1Val = diff.metricsV1.getOrDefault(metric, 0);
            int v2Val = diff.metricsV2.getOrDefault(metric, 0);
            int change = v2Val - v1Val;
            String changeStr = change >= 0 ? "+" + change : String.valueOf(change);

            String metricDisplay = prettifyMetricName(metric);
            sb.append(String.format("%-25s %7d %7d %10s\n", metricDisplay, v1Val, v2Val, changeStr));
        }
        sb.append("\n");

        // Behavioral Fingerprint
        sb.append("BEHAVIORAL FINGERPRINT\n");
        sb.append("──────────────────────\n");
        sb.append("v1 fingerprint: ").append(diff.v1Fingerprint).append(" → v2 fingerprint: ")
                .append(diff.v2Fingerprint).append("\n");
        sb.append("Change magnitude: ").append(diff.magnitude).append(" (").append(diff.structuralChangeCount)
                .append(" structural changes)\n");
        sb.append("Regression risk: ").append(diff.regressionRisk).append("\n\n");

        // Recommendation
        sb.append("RECOMMENDATION\n");
        sb.append("──────────────\n");
        if (!diff.addedTasks.isEmpty() || !diff.removedTasks.isEmpty()) {
            sb.append("Verify the following before deploying:\n");
            if (!diff.addedTasks.isEmpty()) {
                sb.append("• New tasks added: ").append(diff.addedTasks).append("\n");
                sb.append("  - Check resource assignments and routing logic\n");
            }
            if (!diff.removedTasks.isEmpty()) {
                sb.append("• Tasks removed: ").append(diff.removedTasks).append("\n");
                sb.append("  - Verify downstream dependencies are satisfied\n");
            }
            if (!diff.changedTasks.isEmpty()) {
                sb.append("• Tasks changed: ").append(diff.changedTasks).append("\n");
                sb.append("  - Review attribute modifications for unintended impact\n");
            }
        } else {
            sb.append("No task-level changes detected. Review metric changes above.\n");
        }

        return sb.toString();
    }

    /**
     * Extracts all task IDs from workflow XML.
     *
     * @param xml the specification XML
     * @return set of task IDs found
     */
    private static Set<String> extractTaskIds(String xml) {
        Set<String> ids = new HashSet<>();

        // Match patterns: id="taskName" in task elements
        Pattern p = Pattern.compile("(?:id=['\"]([^'\"]+)['\"]|<task\\s+[^>]*id=['\"]([^'\"]+)['\"])");
        Matcher m = p.matcher(xml);

        while (m.find()) {
            String id = m.group(1) != null ? m.group(1) : m.group(2);
            if (id != null && !id.isEmpty() && !id.equals("root")) {
                ids.add(id);
            }
        }

        return ids;
    }

    /**
     * Extracts structural metrics from workflow XML.
     *
     * @param xml the specification XML
     * @return map of metric name to count
     */
    private static Map<String, Integer> extractStructuralMetrics(String xml) {
        Map<String, Integer> metrics = new LinkedHashMap<>();

        metrics.put("tasks", countOccurrences(xml, "<task "));
        metrics.put("and_splits", countOccurrences(xml, "and_split") + countOccurrences(xml, "AND_split"));
        metrics.put("xor_splits", countOccurrences(xml, "xor_split") + countOccurrences(xml, "XOR_split"));
        metrics.put("and_joins", countOccurrences(xml, "and_join") + countOccurrences(xml, "AND_join"));
        metrics.put("xor_joins", countOccurrences(xml, "xor_join") + countOccurrences(xml, "XOR_join"));
        metrics.put("conditions", countOccurrences(xml, "<condition "));
        metrics.put("flows", countOccurrences(xml, "<flow "));

        return metrics;
    }

    /**
     * Counts occurrences of a pattern in XML.
     *
     * @param xml the XML string
     * @param pattern the pattern to search for
     * @return count of matches
     */
    private static int countOccurrences(String xml, String pattern) {
        if (xml == null || pattern == null) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = xml.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }

    /**
     * Detects task attribute changes by comparing task definitions.
     *
     * @param xml1 first spec XML
     * @param xml2 second spec XML
     * @param commonTasks tasks present in both specs
     * @return set of task IDs with detected changes
     */
    private static Set<String> detectTaskChanges(String xml1, String xml2, Set<String> commonTasks) {
        Set<String> changed = new HashSet<>();

        for (String taskId : commonTasks) {
            String taskDef1 = extractTaskDefinition(xml1, taskId);
            String taskDef2 = extractTaskDefinition(xml2, taskId);

            if (taskDef1 != null && taskDef2 != null && !taskDef1.equals(taskDef2)) {
                changed.add(taskId);
            }
        }

        return changed;
    }

    /**
     * Extracts task definition from XML. Returns the task XML element
     * if found, or null if task not found in this spec.
     *
     * @param xml the XML string
     * @param taskId the task ID
     * @return task definition substring, or null if task not found
     */
    private static String extractTaskDefinition(String xml, String taskId) {
        if (xml == null || xml.isEmpty() || taskId == null || taskId.isEmpty()) {
            return null;
        }

        Pattern p = Pattern.compile("<task[^>]*id=['\"]" + Pattern.quote(taskId)
                + "['\"][^>]*>.*?</task>", Pattern.DOTALL);
        Matcher m = p.matcher(xml);

        if (m.find()) {
            return m.group();
        }

        return null;
    }

    /**
     * Computes a 6-character hex fingerprint from metrics.
     *
     * @param metrics the metrics map
     * @return 6-char hex string
     */
    private static String computeFingerprint(Map<String, Integer> metrics) {
        long hash = 0;

        for (Integer value : metrics.values()) {
            hash = hash * 31 + value;
        }

        return String.format("%06X", Math.abs(hash) % 0x1000000);
    }

    /**
     * Counts how many metrics changed between versions.
     *
     * @param v1 metrics for v1
     * @param v2 metrics for v2
     * @return count of metrics with non-zero change
     */
    private static int countMetricChanges(Map<String, Integer> v1, Map<String, Integer> v2) {
        int count = 0;

        for (String key : v1.keySet()) {
            int change = Math.abs(v2.getOrDefault(key, 0) - v1.getOrDefault(key, 0));
            if (change > 0) {
                count++;
            }
        }

        return count;
    }

    /**
     * Determines change magnitude based on change count.
     *
     * @param changeCount total changes
     * @return magnitude string (NONE, MINOR, MODERATE, MAJOR)
     */
    private static String determineMagnitude(int changeCount) {
        return switch (changeCount) {
            case 0 -> "NONE";
            case 1, 2 -> "MINOR";
            case 3, 4, 5 -> "MODERATE";
            default -> "MAJOR";
        };
    }

    /**
     * Assesses regression risk based on changes.
     *
     * @param xml1 first spec XML
     * @param xml2 second spec XML
     * @param addedTasks added tasks
     * @param removedTasks removed tasks
     * @param changedTasks changed tasks
     * @return risk level (LOW, MEDIUM, HIGH)
     */
    private static String assessRegressionRisk(String xml1, String xml2, Set<String> addedTasks,
            Set<String> removedTasks, Set<String> changedTasks) {

        int riskScore = 0;

        // Removed exception handlers increase risk
        int exceptionsV1 = countOccurrences(xml1, "exceptionHandler");
        int exceptionsV2 = countOccurrences(xml2, "exceptionHandler");
        if (exceptionsV2 < exceptionsV1) {
            riskScore += 3;
        }

        // Removed tasks increase risk
        riskScore += removedTasks.size() * 2;

        // New parallelism (added AND-splits) increases risk
        int andSplitsV1 = countOccurrences(xml1, "and_split") + countOccurrences(xml1, "AND_split");
        int andSplitsV2 = countOccurrences(xml2, "and_split") + countOccurrences(xml2, "AND_split");
        if (andSplitsV2 > andSplitsV1) {
            riskScore += 2;
        }

        // Changed exception paths increase risk
        riskScore += changedTasks.size();

        return switch (riskScore) {
            case 0 -> "LOW";
            case 1, 2 -> "MEDIUM";
            default -> "HIGH";
        };
    }

    /**
     * Prettifies metric name for display.
     *
     * @param metric the metric key
     * @return human-readable name
     */
    private static String prettifyMetricName(String metric) {
        return switch (metric) {
            case "tasks" -> "Task count";
            case "and_splits" -> "AND-splits";
            case "xor_splits" -> "XOR-splits";
            case "and_joins" -> "AND-joins";
            case "xor_joins" -> "XOR-joins";
            case "conditions" -> "Decision points";
            case "flows" -> "Control flows";
            default -> metric;
        };
    }

    /**
     * Pads string to right with spaces.
     *
     * @param s the string
     * @param len the target length
     * @return padded string
     */
    private static String padRight(String s, int len) {
        if (s == null) {
            s = "";
        }
        return String.format("%-" + len + "s", s);
    }
}
