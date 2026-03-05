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

package org.yawlfoundation.yawl.integration.autonomous.analytics;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Typed result records for workflow analytics queries, plus plain-Java parsers for
 * SPARQL result strings returned by {@link WorkflowQueryService}.
 *
 * <p>All nested records are pure Java 25 value records. Parsing is done with simple
 * regex-based extraction of SPARQL-results+JSON or Turtle literals — no external
 * RDF library is required, consistent with the project's pure-Java Turtle handling
 * convention.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * WorkflowQueryService qs = new WorkflowQueryService(engine);
 * Optional<String> raw = qs.taskBottlenecks("orderProcess", 5);
 * List<WorkflowAnalytics.TaskSummary> bottlenecks =
 *     raw.map(WorkflowAnalytics::parseTaskSummaries).orElse(List.of());
 * }</pre>
 *
 * @since YAWL 6.0
 * @see WorkflowQueryService
 * @see WorkflowEventPublisher
 */
public final class WorkflowAnalytics {

    private WorkflowAnalytics() {
        throw new UnsupportedOperationException("Analytics result container");
    }

    // =========================================================================
    // Result record types
    // =========================================================================

    /**
     * Summary statistics for a single task type across all executions.
     *
     * @param taskId        YAWL task identifier
     * @param avgDurationMs average execution duration in milliseconds
     * @param execCount     number of completed executions observed
     */
    public record TaskSummary(
            String taskId,
            double avgDurationMs,
            long execCount) {}

    /**
     * A single workflow case instance with its lifecycle times and status.
     *
     * @param caseId    YAWL case identifier
     * @param status    lifecycle status (starting|executing|completed|cancelled)
     * @param startTime when the case started (never null)
     * @param endTime   when the case ended, or {@code null} if still active
     * @param durationMs elapsed milliseconds; -1 if the case has not completed
     */
    public record CaseSummary(
            String caseId,
            String status,
            Instant startTime,
            Instant endTime,
            long durationMs) {}

    /**
     * SLA compliance summary for a specification.
     *
     * @param specId        YAWL specification identifier
     * @param slaMs         the SLA threshold used for this report
     * @param totalCases    number of completed cases inspected
     * @param violations    number of cases exceeding the SLA
     * @param violationRate fraction of cases that violated the SLA (0.0–1.0)
     */
    public record SlaReport(
            String specId,
            long slaMs,
            long totalCases,
            long violations,
            double violationRate) {}

    /**
     * Throughput measurement for one hour bucket.
     *
     * @param hourLabel  ISO-8601 hour prefix, e.g. {@code "2026-03-01T14"}
     * @param completions number of cases completed in that hour
     */
    public record Throughput(
            String hourLabel,
            long completions) {}

    /**
     * Directed transition between two tasks, with observed frequency.
     *
     * @param fromTask       task that preceded the transition
     * @param toTask         task that followed
     * @param transitionCount number of times this transition was observed
     */
    public record TaskTransition(
            String fromTask,
            String toTask,
            long transitionCount) {}

    /**
     * Full ordered execution trace for a single case.
     *
     * @param caseId        YAWL case identifier
     * @param taskSequence  ordered list of task identifiers as they were executed
     */
    public record PathTrace(
            String caseId,
            List<String> taskSequence) {}

    // =========================================================================
    // Parsers — regex-based extraction from SPARQL-results+JSON lines
    // =========================================================================

    // Pattern: "taskId" : { "type" : "literal" , "value" : "reviewOrder" }
    private static final Pattern VAL =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Parses a SPARQL results string (JSON or simplified) for task summaries.
     * Returns an empty list if the input is blank or unparseable.
     *
     * @param sparqlResult raw SPARQL result string from QLever
     * @return list of {@link TaskSummary} records (may be empty)
     */
    public static List<TaskSummary> parseTaskSummaries(String sparqlResult) {
        if (sparqlResult == null || sparqlResult.isBlank()) return List.of();
        List<TaskSummary> results = new ArrayList<>();

        // Find all binding blocks: { ... "taskId": {"value": "X"}, "avgDurationMs": ...}
        Pattern bindingBlock = Pattern.compile("\\{([^{}]+)\\}", Pattern.DOTALL);
        Matcher blockMatcher = bindingBlock.matcher(sparqlResult);

        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);
            String taskId = extractValue(block, "taskId");
            String avgMs = extractValue(block, "avgDurationMs");
            String count = extractValue(block, "execCount");

            if (taskId != null) {
                double avg = parseDouble(avgMs, 0.0);
                long cnt = parseLong(count, 0L);
                results.add(new TaskSummary(taskId, avg, cnt));
            }
        }
        return List.copyOf(results);
    }

    /**
     * Parses a SPARQL results string for case summaries.
     * Returns an empty list if the input is blank or unparseable.
     *
     * @param sparqlResult raw SPARQL result string from QLever
     * @return list of {@link CaseSummary} records (may be empty)
     */
    public static List<CaseSummary> parseCaseSummaries(String sparqlResult) {
        if (sparqlResult == null || sparqlResult.isBlank()) return List.of();
        List<CaseSummary> results = new ArrayList<>();

        Pattern bindingBlock = Pattern.compile("\\{([^{}]+)\\}", Pattern.DOTALL);
        Matcher blockMatcher = bindingBlock.matcher(sparqlResult);

        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);
            String caseId = extractValue(block, "caseId");
            String status = extractValue(block, "status");
            String startTimeStr = extractValue(block, "startTime");
            String endTimeStr = extractValue(block, "endTime");
            String durationStr = extractValue(block, "durationMs");

            if (caseId != null && startTimeStr != null) {
                Instant startTime = parseInstant(startTimeStr);
                Instant endTime = (endTimeStr != null) ? parseInstant(endTimeStr) : null;
                long durationMs = parseLong(durationStr, -1L);
                results.add(new CaseSummary(caseId,
                        status != null ? status : "unknown",
                        startTime, endTime, durationMs));
            }
        }
        return List.copyOf(results);
    }

    /**
     * Parses a SPARQL results string for throughput data (completions per hour).
     *
     * @param sparqlResult raw SPARQL result string from QLever
     * @return list of {@link Throughput} records (may be empty)
     */
    public static List<Throughput> parseThroughput(String sparqlResult) {
        if (sparqlResult == null || sparqlResult.isBlank()) return List.of();
        List<Throughput> results = new ArrayList<>();

        Pattern bindingBlock = Pattern.compile("\\{([^{}]+)\\}", Pattern.DOTALL);
        Matcher blockMatcher = bindingBlock.matcher(sparqlResult);

        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);
            String hour = extractValue(block, "hour");
            String count = extractValue(block, "completions");

            if (hour != null) {
                results.add(new Throughput(hour, parseLong(count, 0L)));
            }
        }
        return List.copyOf(results);
    }

    /**
     * Parses a SPARQL results string for task transition matrix rows.
     *
     * @param sparqlResult raw SPARQL result string from QLever
     * @return list of {@link TaskTransition} records (may be empty)
     */
    public static List<TaskTransition> parseTaskTransitions(String sparqlResult) {
        if (sparqlResult == null || sparqlResult.isBlank()) return List.of();
        List<TaskTransition> results = new ArrayList<>();

        Pattern bindingBlock = Pattern.compile("\\{([^{}]+)\\}", Pattern.DOTALL);
        Matcher blockMatcher = bindingBlock.matcher(sparqlResult);

        while (blockMatcher.find()) {
            String block = blockMatcher.group(1);
            String fromTask = extractValue(block, "fromTask");
            String toTask = extractValue(block, "toTask");
            String count = extractValue(block, "transitionCount");

            if (fromTask != null && toTask != null) {
                results.add(new TaskTransition(fromTask, toTask, parseLong(count, 0L)));
            }
        }
        return List.copyOf(results);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Extracts the "value" field for the given binding name from a SPARQL JSON binding block.
     * Returns null if the binding name is not found.
     */
    private static String extractValue(String block, String bindingName) {
        // Match: "bindingName" : { ... "value" : "X" ... }
        Pattern p = Pattern.compile(
                "\"" + Pattern.quote(bindingName) + "\"\\s*:\\s*\\{[^}]*\"value\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1) : null;
    }

    private static double parseDouble(String s, double defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private static long parseLong(String s, long defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        try { return Long.parseLong(s.trim().replaceAll("\\.0+$", "")); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return Instant.EPOCH;
        try { return Instant.parse(s.trim()); }
        catch (DateTimeParseException e) { return Instant.EPOCH; }
    }
}
