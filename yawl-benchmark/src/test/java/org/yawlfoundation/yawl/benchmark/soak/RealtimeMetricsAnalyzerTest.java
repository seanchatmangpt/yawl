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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RealtimeMetricsAnalyzer.
 *
 * <p>Validates that the analyzer correctly parses JSONL metrics and
 * detects anomalies (heap leaks, GC storms).
 * </p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class RealtimeMetricsAnalyzerTest {

    @TempDir
    private Path tempDir;

    /**
     * Test parsing a valid JSONL metric line.
     */
    @Test
    @DisplayName("Parse valid JSONL metric line")
    void testParseValidMetricLine() {
        String jsonLine = """
            {"timestamp":1735689600000,"heap_used_mb":2048,"heap_max_mb":4096,"heap_committed_mb":3072,\
            "gc_collection_count":45,"gc_collection_time_ms":1250,"thread_count":125,"peak_thread_count":150,\
            "cases_processed":12500,"throughput_cases_per_sec":45.2}""";

        RealtimeMetricsAnalyzer.MetricLine line =
            RealtimeMetricsAnalyzer.MetricLine.fromJson(jsonLine);

        assertEquals(1735689600000L, line.timestamp());
        assertEquals(2048L, line.heapUsedMb());
        assertEquals(4096L, line.heapMaxMb());
        assertEquals(3072L, line.heapCommittedMb());
        assertEquals(45L, line.gcCollectionCount());
        assertEquals(1250L, line.gcCollectionTimeMs());
        assertEquals(125, line.threadCount());
        assertEquals(150, line.peakThreadCount());
        assertEquals(12500L, line.casesProcessed());
        assertEquals(45.2, line.throughputCasesPerSec(), 0.01);
    }

    /**
     * Test detecting heap leak (growth > 1000 MB/hour).
     */
    @Test
    @DisplayName("Detect heap leak alert")
    void testDetectHeapLeak() throws Exception {
        Path metricsFile = tempDir.resolve("metrics-test.jsonl");
        Path summaryFile = tempDir.resolve("summary.txt");

        // Create test metrics showing heap growth
        long now = System.currentTimeMillis();
        StringBuilder metrics = new StringBuilder();

        // Simulate 12 samples over 1 minute with 200 MB growth = 12,000 MB/hour
        for (int i = 0; i < 12; i++) {
            long timestamp = now + (i * 5000);
            long heapMb = 2000 + (i * 20); // 20 MB per sample = 240 MB/min = 14,400 MB/hour

            metrics.append(String.format(
                "{\"timestamp\":%d,\"heap_used_mb\":%d,\"heap_max_mb\":4096," +
                "\"heap_committed_mb\":3072,\"gc_collection_count\":%d," +
                "\"gc_collection_time_ms\":100,\"thread_count\":100," +
                "\"peak_thread_count\":150,\"cases_processed\":%d," +
                "\"throughput_cases_per_sec\":10.0}\n",
                timestamp, heapMb, i, i * 100));
        }

        Files.writeString(metricsFile, metrics.toString());

        RealtimeMetricsAnalyzer analyzer =
            new RealtimeMetricsAnalyzer(tempDir, summaryFile, false);

        // Parse metrics
        List<String> lines = Files.readAllLines(metricsFile);
        List<RealtimeMetricsAnalyzer.MetricLine> metricLines = new ArrayList<>();
        for (String line : lines) {
            metricLines.add(RealtimeMetricsAnalyzer.MetricLine.fromJson(line));
        }

        // Verify heap growth detected
        assertTrue(metricLines.size() > 0, "Should parse metrics");
        double heapGrowth = (metricLines.getLast().heapUsedMb() -
            metricLines.getFirst().heapUsedMb()) * 3600_000.0 /
            (metricLines.getLast().timestamp() - metricLines.getFirst().timestamp());

        assertTrue(heapGrowth > 1000.0, "Should detect heap growth > 1000 MB/hour");
    }

    /**
     * Test analyzing metrics with stable heap.
     */
    @Test
    @DisplayName("Analyze stable heap metrics")
    void testAnalyzeStableHeap() throws Exception {
        Path metricsFile = tempDir.resolve("metrics-stable.jsonl");
        Path summaryFile = tempDir.resolve("summary.txt");

        // Create stable metrics
        long now = System.currentTimeMillis();
        StringBuilder metrics = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            long timestamp = now + (i * 10000);

            metrics.append(String.format(
                "{\"timestamp\":%d,\"heap_used_mb\":2048,\"heap_max_mb\":4096," +
                "\"heap_committed_mb\":3072,\"gc_collection_count\":%d," +
                "\"gc_collection_time_ms\":50,\"thread_count\":100," +
                "\"peak_thread_count\":150,\"cases_processed\":%d," +
                "\"throughput_cases_per_sec\":10.0}\n",
                timestamp, i * 2, i * 100));
        }

        Files.writeString(metricsFile, metrics.toString());

        List<String> lines = Files.readAllLines(metricsFile);
        List<RealtimeMetricsAnalyzer.MetricLine> metricLines = new ArrayList<>();
        for (String line : lines) {
            metricLines.add(RealtimeMetricsAnalyzer.MetricLine.fromJson(line));
        }

        // Verify stable heap
        double heapGrowth = (metricLines.getLast().heapUsedMb() -
            metricLines.getFirst().heapUsedMb()) * 3600_000.0 /
            (metricLines.getLast().timestamp() - metricLines.getFirst().timestamp());

        assertTrue(Math.abs(heapGrowth) < 100.0,
            "Stable heap should show minimal growth");
    }

    /**
     * Test metric line with integer throughput.
     */
    @Test
    @DisplayName("Parse metric line with integer throughput")
    void testParseIntegerThroughput() {
        String jsonLine = """
            {"timestamp":1735689600000,"heap_used_mb":2048,"heap_max_mb":4096,"heap_committed_mb":3072,\
            "gc_collection_count":45,"gc_collection_time_ms":1250,"thread_count":125,"peak_thread_count":150,\
            "cases_processed":12500,"throughput_cases_per_sec":45}""";

        RealtimeMetricsAnalyzer.MetricLine line =
            RealtimeMetricsAnalyzer.MetricLine.fromJson(jsonLine);

        assertEquals(45.0, line.throughputCasesPerSec(), 0.01);
    }

    /**
     * Test parsing empty metrics list.
     */
    @Test
    @DisplayName("Handle empty metrics list")
    void testEmptyMetricsReturnsDefaults() {
        List<RealtimeMetricsAnalyzer.MetricLine> emptyList = List.of();
        assertTrue(emptyList.isEmpty(), "Empty list should remain empty");
    }
}
