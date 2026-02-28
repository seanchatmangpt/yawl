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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.benchmark.soak;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Detects capacity breaking points during stress testing.
 *
 * <p>Monitors key metrics (throughput, GC pause times, heap growth, latency) and
 * identifies when the system enters a degraded state. A breaking point is detected
 * when any of these conditions are sustained for >5 minutes:
 * <ul>
 *   <li>Throughput drops >30% from baseline</li>
 *   <li>GC pause p99 exceeds 100ms</li>
 *   <li>Heap growth >500MB/hour</li>
 *   <li>p99 latency exceeds 1 second</li>
 * </ul>
 * </p>
 *
 * <p>This analyzer runs continuously during a soak test and emits alerts when
 * breaking points are detected, allowing the test harness to make informed decisions
 * about continuing or stopping the test.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class CapacityBreakingPointAnalyzer {

    /**
     * Configuration for breaking point thresholds.
     */
    public static class ThresholdConfig {
        public double throughputCliffPct = 30.0;  // Drop >30%
        public long gcPauseMaxMs = 100;           // p99 GC pause >100ms
        public long heapGrowthMaxMbPerHour = 500; // Growth >500MB/hour
        public long latencyP99MaxMs = 1000;       // p99 latency >1s
        public long sustainedDurationMinutes = 5;  // Duration threshold

        public ThresholdConfig() {
        }

        public ThresholdConfig(double throughput, long gcPause, long heapGrowth,
                               long latency, long sustainedDuration) {
            this.throughputCliffPct = throughput;
            this.gcPauseMaxMs = gcPause;
            this.heapGrowthMaxMbPerHour = heapGrowth;
            this.latencyP99MaxMs = latency;
            this.sustainedDurationMinutes = sustainedDuration;
        }
    }

    /**
     * Immutable breaking point detection result.
     */
    public record BreakingPoint(
            String reason,
            long detectedAtCaseCount,
            long detectedAtTimeMs,
            Map<String, Object> metrics,
            String recommendation) {

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"reason\":\"").append(reason).append("\",");
            sb.append("\"detected_at_case_count\":").append(detectedAtCaseCount).append(",");
            sb.append("\"detected_at_time_ms\":").append(detectedAtTimeMs).append(",");
            sb.append("\"recommendation\":\"").append(recommendation).append("\",");
            sb.append("\"metrics\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                Object val = entry.getValue();
                if (val instanceof String) {
                    sb.append("\"").append(val).append("\"");
                } else {
                    sb.append(val);
                }
                first = false;
            }
            sb.append("}}");
            return sb.toString();
        }
    }

    private final ThresholdConfig config;
    private final Path outputPath;
    private final List<BreakingPoint> detectedPoints;
    private final Deque<HealthSnapshot> recentSnapshots;
    private double baselineThroughputCasesPerSec;
    private volatile boolean breakingPointDetected;

    /**
     * Internal health snapshot from metrics.
     */
    private record HealthSnapshot(
            long timestamp,
            long caseCount,
            double throughputCasesPerSec,
            long gcPauseP99Ms,
            long heapGrowthMbPerHour,
            long latencyP99Ms) {
    }

    /**
     * Create a breaking point analyzer with default thresholds.
     *
     * @param outputPath Path to write breaking point analysis
     */
    public CapacityBreakingPointAnalyzer(Path outputPath) {
        this(outputPath, new ThresholdConfig());
    }

    /**
     * Create a breaking point analyzer with custom thresholds.
     *
     * @param outputPath Path to write breaking point analysis
     * @param config Custom threshold configuration
     */
    public CapacityBreakingPointAnalyzer(Path outputPath, ThresholdConfig config) {
        this.config = config;
        this.outputPath = outputPath;
        this.detectedPoints = new CopyOnWriteArrayList<>();
        this.recentSnapshots = new ArrayDeque<>(60); // Keep 60 snapshots (5min at 5s interval)
        this.baselineThroughputCasesPerSec = 0.0;
        this.breakingPointDetected = false;
    }

    /**
     * Evaluate metrics snapshot and detect breaking point if thresholds exceeded.
     * Should be called periodically (e.g., every 5 seconds during test).
     *
     * @param caseCount Current case count
     * @param throughputCasesPerSec Current throughput
     * @param gcPauseP99Ms Current GC pause p99
     * @param heapGrowthMbPerHour Current heap growth rate
     * @param latencyP99Ms Current latency p99
     */
    public void evaluateMetrics(long caseCount,
                                double throughputCasesPerSec,
                                long gcPauseP99Ms,
                                long heapGrowthMbPerHour,
                                long latencyP99Ms) {
        long timestamp = System.currentTimeMillis();

        // Initialize baseline from first evaluation
        if (baselineThroughputCasesPerSec == 0.0 && throughputCasesPerSec > 0) {
            baselineThroughputCasesPerSec = throughputCasesPerSec;
        }

        // Add to recent snapshots (keep rolling window)
        HealthSnapshot snapshot = new HealthSnapshot(
                timestamp,
                caseCount,
                throughputCasesPerSec,
                gcPauseP99Ms,
                heapGrowthMbPerHour,
                latencyP99Ms);
        recentSnapshots.addLast(snapshot);
        while (recentSnapshots.size() > 60) {
            recentSnapshots.removeFirst();
        }

        // Check for breaking point conditions
        if (recentSnapshots.size() >= 60) {  // Need sustained data for decision
            checkAndDetectBreakingPoint(caseCount, timestamp);
        }
    }

    /**
     * Check if any breaking point thresholds are violated in sustained window.
     */
    private void checkAndDetectBreakingPoint(long caseCount, long timestamp) {
        if (breakingPointDetected) {
            return;  // Already detected
        }

        HealthSnapshot latest = recentSnapshots.getLast();

        // Check throughput cliff
        if (baselineThroughputCasesPerSec > 0) {
            double throughputDrop = ((baselineThroughputCasesPerSec - latest.throughputCasesPerSec)
                    / baselineThroughputCasesPerSec) * 100;
            if (throughputDrop > config.throughputCliffPct) {
                recordBreakingPoint(
                        "Throughput cliff detected",
                        caseCount,
                        timestamp,
                        "Throughput dropped " + String.format("%.1f", throughputDrop) + "% " +
                        "(from " + String.format("%.0f", baselineThroughputCasesPerSec) +
                        " to " + String.format("%.0f", latest.throughputCasesPerSec) + " cases/sec)",
                        "Consider increasing resources or reducing load");
                return;
            }
        }

        // Check GC pause time
        if (latest.gcPauseP99Ms > config.gcPauseMaxMs) {
            recordBreakingPoint(
                    "GC pause degradation",
                    caseCount,
                    timestamp,
                    "GC pause p99=" + latest.gcPauseP99Ms + "ms exceeds " +
                    config.gcPauseMaxMs + "ms threshold",
                    "Consider tuning GC or increasing heap size");
            return;
        }

        // Check heap growth rate
        if (latest.heapGrowthMbPerHour > config.heapGrowthMaxMbPerHour) {
            recordBreakingPoint(
                    "Excessive heap growth",
                    caseCount,
                    timestamp,
                    "Heap growth rate=" + latest.heapGrowthMbPerHour + "MB/hour " +
                    "exceeds " + config.heapGrowthMaxMbPerHour + "MB/hour threshold",
                    "Investigate potential memory leak or reduce load");
            return;
        }

        // Check latency p99
        if (latest.latencyP99Ms > config.latencyP99MaxMs) {
            recordBreakingPoint(
                    "Latency degradation",
                    caseCount,
                    timestamp,
                    "p99 latency=" + latest.latencyP99Ms + "ms exceeds " +
                    config.latencyP99MaxMs + "ms threshold",
                    "System approaching saturation; reduce load or scale out");
            return;
        }
    }

    /**
     * Record a detected breaking point and write to file.
     */
    private void recordBreakingPoint(String reason, long caseCount, long timestamp,
                                    String metricsDescription, String recommendation) {
        HealthSnapshot latest = recentSnapshots.getLast();
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("case_count", caseCount);
        metrics.put("throughput_cases_per_sec", latest.throughputCasesPerSec);
        metrics.put("gc_pause_p99_ms", latest.gcPauseP99Ms);
        metrics.put("heap_growth_mb_per_hour", latest.heapGrowthMbPerHour);
        metrics.put("latency_p99_ms", latest.latencyP99Ms);
        metrics.put("details", metricsDescription);

        BreakingPoint point = new BreakingPoint(
                reason,
                caseCount,
                timestamp,
                metrics,
                recommendation);

        detectedPoints.add(point);
        breakingPointDetected = true;

        // Write to output file
        try {
            String jsonLine = point.toJson() + "\n";
            Files.writeString(
                    outputPath,
                    jsonLine,
                    StandardCharsets.UTF_8,
                    Files.exists(outputPath)
                            ? StandardOpenOption.APPEND
                            : StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            System.err.println("Failed to write breaking point analysis: " + e.getMessage());
        }

        System.err.println("BREAKING POINT DETECTED: " + reason);
        System.err.println("  Case Count: " + caseCount);
        System.err.println("  Details: " + metricsDescription);
        System.err.println("  Recommendation: " + recommendation);
    }

    /**
     * Check if a breaking point has been detected.
     */
    public boolean isBreakingPointDetected() {
        return breakingPointDetected;
    }

    /**
     * Get all detected breaking points.
     */
    public List<BreakingPoint> getDetectedPoints() {
        return List.copyOf(detectedPoints);
    }

    /**
     * Get most recent detected breaking point, if any.
     */
    public Optional<BreakingPoint> getMostRecentBreakingPoint() {
        return detectedPoints.isEmpty() ? Optional.empty()
                : Optional.of(detectedPoints.getLast());
    }

    /**
     * Reset detection state (for multiple test runs).
     */
    public void reset() {
        detectedPoints.clear();
        recentSnapshots.clear();
        baselineThroughputCasesPerSec = 0.0;
        breakingPointDetected = false;
    }
}
