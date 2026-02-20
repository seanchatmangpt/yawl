/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.demo.report;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Individual pattern execution result for reporting.
 *
 * <p>Contains detailed information about a single pattern execution including:
 * - Pattern metadata and identification
 * - Execution status and duration
 * - Trace events and metrics
 * - Token savings analysis
 * - Error information if any</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class PatternResult {

    private final String patternId;
    private final PatternInfo patternInfo;
    private final boolean success;
    private final Instant startTime;
    private final Instant endTime;
    private final Duration duration;
    private final String error;
    private final List<TraceEvent> trace;
    private final ExecutionMetrics metrics;
    private final TokenAnalysis tokenAnalysis;

    /**
     * Create a successful pattern result
     */
    public PatternResult(String patternId,
                        PatternInfo patternInfo,
                        Instant startTime,
                        Instant endTime,
                        List<TraceEvent> trace,
                        ExecutionMetrics metrics,
                        TokenAnalysis tokenAnalysis) {
        this(patternId, patternInfo, startTime, endTime, null, trace, metrics, tokenAnalysis);
    }

    /**
     * Create a failed pattern result
     */
    public PatternResult(String patternId,
                        PatternInfo patternInfo,
                        Instant startTime,
                        Instant endTime,
                        String error,
                        List<TraceEvent> trace,
                        ExecutionMetrics metrics,
                        TokenAnalysis tokenAnalysis) {
        this.patternId = patternId;
        this.patternInfo = patternInfo;
        this.success = error == null;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = Duration.between(startTime, endTime);
        this.error = error;
        this.trace = trace;
        this.metrics = metrics;
        this.tokenAnalysis = tokenAnalysis;
    }

    /**
     * Getters
     */
    public String getPatternId() { return patternId; }
    public PatternInfo getPatternInfo() { return patternInfo; }
    public boolean isSuccess() { return success; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Duration getDuration() { return duration; }
    public String getError() { return error; }
    public List<TraceEvent> getTrace() { return trace; }
    public ExecutionMetrics getMetrics() { return metrics; }
    public TokenAnalysis getTokenAnalysis() { return tokenAnalysis; }

    /**
     * Get formatted duration
     */
    public String getFormattedDuration() {
        if (duration.toMinutes() > 0) {
            return String.format("%d min %d sec",
                duration.toMinutes(),
                duration.toSecondsPart());
        } else {
            return String.format("%d ms", duration.toMillis());
        }
    }

    /**
     * Get status icon for display
     */
    public String getStatusIcon() {
        return success ? "[PASS]" : "[FAIL]";
    }

    /**
     * Get difficulty icon
     */
    public String getDifficultyIcon() {
        if (patternInfo == null) {
            return "[UNKNOWN]";
        }
        switch (patternInfo.difficulty()) {
            case BASIC: return "[BASIC]";
            case INTERMEDIATE: return "[INTERMEDIATE]";
            case ADVANCED: return "[ADVANCED]";
            case EXPERT: return "[EXPERT]";
            default: return "[UNKNOWN]";
        }
    }

    /**
     * Get category color code for console output.
     * Returns ANSI reset code as default when no category is available.
     */
    public String getCategoryColor() {
        if (patternInfo == null || patternInfo.category() == null) {
            return getResetColor();
        }
        return patternInfo.category().getColorCode();
    }

    /**
     * Reset color code
     */
    public String getResetColor() {
        return "\u001B[0m";
    }

    /**
     * Get trace summary
     */
    public String getTraceSummary() {
        if (trace == null || trace.isEmpty()) {
            return "No trace data";
        }

        long totalEvents = trace.size();
        long errorEvents = trace.stream()
            .filter(e -> e.type().equals("error"))
            .count();
        long workItems = trace.stream()
            .filter(e -> e.type().equals("workItem"))
            .count();

        return String.format("%d events (%d errors, %d work items)",
            totalEvents, errorEvents, workItems);
    }

    /**
     * Get metrics summary
     */
    public String getMetricsSummary() {
        if (metrics == null) {
            return "No metrics data";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Work items: %d", metrics.getWorkItemCount()));
        sb.append(", ");
        sb.append(String.format("Events: %d", metrics.getEventCount()));
        sb.append(", ");
        sb.append(String.format("Duration: %s", getFormattedDuration()));

        return sb.toString();
    }

    /**
     * Get token savings summary
     */
    public String getTokenSavingsSummary() {
        if (tokenAnalysis == null) {
            return "No token analysis available";
        }

        return String.format("YAML: %d tokens | XML: %d tokens | Savings: %.1f%%",
            tokenAnalysis.getYamlTokens(),
            tokenAnalysis.getXmlTokens(),
            tokenAnalysis.getSavingsPercentage());
    }

    /**
     * Trace event record
     */
    public record TraceEvent(
        String type,              // Event type
        Object data,              // Event data
        Instant timestamp         // Event timestamp
    ) {}

    /**
     * Execution metrics with getter methods
     */
    public static class ExecutionMetrics {
        private final int workItemCount;
        private final int eventCount;
        private final Duration duration;

        public ExecutionMetrics(int workItemCount, int eventCount, Duration duration) {
            this.workItemCount = workItemCount;
            this.eventCount = eventCount;
            this.duration = duration;
        }

        public int getWorkItemCount() { return workItemCount; }
        public int getEventCount() { return eventCount; }
        public Duration getDuration() { return duration; }
    }

    /**
     * Token analysis result
     */
    public static class TokenAnalysis {
        private final int yamlTokens;
        private final int xmlTokens;
        private final double savingsPercentage;
        private final double compressionRatio;

        public TokenAnalysis(int yamlTokens, int xmlTokens) {
            this.yamlTokens = yamlTokens;
            this.xmlTokens = xmlTokens;
            this.savingsPercentage = calculateSavings(yamlTokens, xmlTokens);
            this.compressionRatio = calculateCompression(yamlTokens, xmlTokens);
        }

        public int getYamlTokens() { return yamlTokens; }
        public int getXmlTokens() { return xmlTokens; }
        public double getSavingsPercentage() { return savingsPercentage; }
        public double getCompressionRatio() { return compressionRatio; }

        private static double calculateSavings(int yamlTokens, int xmlTokens) {
            if (xmlTokens == 0) return 0;
            return (1.0 - (double) yamlTokens / xmlTokens) * 100;
        }

        private static double calculateCompression(int yamlTokens, int xmlTokens) {
            if (yamlTokens == 0) return 0;
            return (double) xmlTokens / yamlTokens;
        }
    }

    /**
     * Pattern difficulty levels
     */
    public enum Difficulty {
        BASIC,
        INTERMEDIATE,
        ADVANCED,
        EXPERT
    }

    /**
     * Pattern category with color codes
     */
    public record ResultPatternCategory(String name, String colorCode) {
        public static final String DEFAULT_COLOR = "\u001B[0m";

        public String getColorCode() {
            return colorCode != null ? colorCode : DEFAULT_COLOR;
        }
    }

    /**
     * Pattern information
     */
    public record PatternInfo(
        String id,
        String name,
        String description,
        Difficulty difficulty,
        ResultPatternCategory category,
        String yamlExample
    ) {}
}
