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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks latency degradation as case count increases in stress tests.
 *
 * <p>Monitors latency percentiles (p50, p95, p99) at regular intervals (every 10K cases)
 * and detects when performance degradation indicates a breaking point. Useful for
 * understanding when latency becomes unacceptable under increasing load.</p>
 *
 * <p>Typical usage:
 * <pre>
 * LatencyDegradationAnalyzer analyzer =
 *     new LatencyDegradationAnalyzer(outputPath, 10_000);  // Sample every 10K cases
 *
 * // Record latencies as operations complete
 * for (WorkItem item : workItems) {
 *     long startTime = System.nanoTime();
 *     engine.execute(item);
 *     long latencyNs = System.nanoTime() - startTime;
 *     analyzer.recordLatency(OperationType.CASE_LAUNCH, latencyNs);
 * }
 *
 * // Periodically flush percentile measurements
 * analyzer.sampleAndFlush(currentCaseCount);
 * </pre>
 * </p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class LatencyDegradationAnalyzer {

    /**
     * Types of latency-sensitive operations tracked.
     */
    public enum OperationType {
        CASE_LAUNCH,
        WORK_ITEM_CHECKOUT,
        WORK_ITEM_COMPLETE,
        TASK_EXECUTION
    }

    /**
     * Immutable percentile snapshot at a given case count.
     */
    public record PercentileSnapshot(
            long timestamp,
            long caseCount,
            OperationType operation,
            long p50LatencyNs,
            long p95LatencyNs,
            long p99LatencyNs,
            long maxLatencyNs,
            long sampleCount) {

        /**
         * Convert to JSON for serialization.
         */
        public String toJson() {
            return String.format(
                    "{\"timestamp\":%d,\"case_count\":%d,\"operation\":\"%s\"," +
                    "\"p50_latency_ns\":%d,\"p95_latency_ns\":%d," +
                    "\"p99_latency_ns\":%d,\"max_latency_ns\":%d,\"sample_count\":%d}",
                    timestamp, caseCount, operation, p50LatencyNs, p95LatencyNs,
                    p99LatencyNs, maxLatencyNs, sampleCount);
        }
    }

    /**
     * Degradation detection result.
     */
    public record DegradationDetected(
            OperationType operation,
            long caseCount,
            long baselineP99Ns,
            long currentP99Ns,
            double degradationFactor) {

        public boolean isSignificant() {
            return degradationFactor > 2.0;  // 2× degradation is significant
        }
    }

    private final Path outputPath;
    private final long sampleIntervalCases;
    private final Map<OperationType, List<Long>> latencies;
    private final List<PercentileSnapshot> snapshots;
    private volatile long lastSampleCaseCount;

    /**
     * Create a latency analyzer.
     *
     * @param outputPath Path to write percentile snapshots (JSONL format)
     * @param sampleIntervalCases Interval between samples (e.g., 10_000)
     */
    public LatencyDegradationAnalyzer(Path outputPath, long sampleIntervalCases) {
        this.outputPath = outputPath;
        this.sampleIntervalCases = sampleIntervalCases;
        this.latencies = new ConcurrentHashMap<>();
        this.snapshots = new CopyOnWriteArrayList<>();
        this.lastSampleCaseCount = 0;

        // Initialize latency lists for each operation type
        for (OperationType op : OperationType.values()) {
            latencies.put(op, new CopyOnWriteArrayList<>());
        }
    }

    /**
     * Record a latency measurement for an operation.
     *
     * @param operation Type of operation
     * @param latencyNanoseconds Time taken for operation in nanoseconds
     */
    public void recordLatency(OperationType operation, long latencyNanoseconds) {
        if (latencyNanoseconds < 0) {
            return;  // Ignore invalid measurements
        }
        latencies.get(operation).add(latencyNanoseconds);
    }

    /**
     * Sample percentiles and flush results when case count crosses threshold.
     * Should be called periodically (e.g., after each case completion batch).
     *
     * @param currentCaseCount Current number of cases processed
     */
    public void sampleAndFlush(long currentCaseCount) {
        if (currentCaseCount - lastSampleCaseCount >= sampleIntervalCases) {
            samplePercentiles(currentCaseCount);
            lastSampleCaseCount = currentCaseCount;
        }
    }

    /**
     * Explicitly sample percentiles at current case count.
     */
    public void samplePercentiles(long caseCount) {
        long timestamp = System.currentTimeMillis();

        for (OperationType op : OperationType.values()) {
            List<Long> opLatencies = latencies.get(op);
            if (opLatencies.isEmpty()) {
                continue;
            }

            // Compute percentiles
            List<Long> sorted = new ArrayList<>(opLatencies);
            Collections.sort(sorted);

            long p50 = percentile(sorted, 50);
            long p95 = percentile(sorted, 95);
            long p99 = percentile(sorted, 99);
            long max = sorted.getLast();

            PercentileSnapshot snapshot = new PercentileSnapshot(
                    timestamp,
                    caseCount,
                    op,
                    p50,
                    p95,
                    p99,
                    max,
                    opLatencies.size());

            snapshots.add(snapshot);

            // Write to output file
            try {
                String jsonLine = snapshot.toJson() + "\n";
                Files.writeString(
                        outputPath,
                        jsonLine,
                        StandardCharsets.UTF_8,
                        Files.exists(outputPath)
                                ? StandardOpenOption.APPEND
                                : StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
            } catch (IOException e) {
                System.err.println("Failed to write latency snapshot: " + e.getMessage());
            }

            // Clear latencies for next sampling period
            opLatencies.clear();
        }
    }

    /**
     * Detect significant latency degradation by comparing against baseline.
     * Baseline is the first snapshot for each operation.
     *
     * @return List of detected degradations, empty if none
     */
    public List<DegradationDetected> detectDegradation() {
        List<DegradationDetected> degradations = new ArrayList<>();

        // Group snapshots by operation type
        Map<OperationType, List<PercentileSnapshot>> byOp = new HashMap<>();
        for (PercentileSnapshot snap : snapshots) {
            byOp.computeIfAbsent(snap.operation(), _ -> new ArrayList<>()).add(snap);
        }

        // Check each operation for degradation
        for (Map.Entry<OperationType, List<PercentileSnapshot>> entry : byOp.entrySet()) {
            OperationType op = entry.getKey();
            List<PercentileSnapshot> opSnapshots = entry.getValue();

            if (opSnapshots.size() < 2) {
                continue;  // Need at least 2 samples to compare
            }

            PercentileSnapshot baseline = opSnapshots.getFirst();
            PercentileSnapshot latest = opSnapshots.getLast();

            double factor = (double) latest.p99LatencyNs() / baseline.p99LatencyNs();
            if (factor > 2.0) {  // Significant degradation threshold
                degradations.add(new DegradationDetected(
                        op,
                        latest.caseCount(),
                        baseline.p99LatencyNs(),
                        latest.p99LatencyNs(),
                        factor));
            }
        }

        return degradations;
    }

    /**
     * Get all recorded snapshots.
     */
    public List<PercentileSnapshot> getSnapshots() {
        return List.copyOf(snapshots);
    }

    /**
     * Get snapshots for a specific operation type.
     */
    public List<PercentileSnapshot> getSnapshots(OperationType operation) {
        return snapshots.stream()
                .filter(s -> s.operation() == operation)
                .toList();
    }

    /**
     * Calculate percentile from sorted list.
     */
    private long percentile(List<Long> sorted, int percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (percentile * sorted.size() + 50) / 100;
        return sorted.get(Math.min(index, sorted.size() - 1));
    }
}
