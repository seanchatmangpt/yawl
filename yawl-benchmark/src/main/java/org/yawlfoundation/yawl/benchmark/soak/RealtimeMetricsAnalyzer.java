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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Real-time metrics analysis pipeline for stress test monitoring.
 *
 * <p>Continuously monitors JSONL metrics files from parallel stress tests and provides:
 * <ul>
 *   <li>Heap growth rate trending (MB/hour, MB/min)</li>
 *   <li>GC pause anomaly detection</li>
 *   <li>Throughput trend analysis</li>
 *   <li>Threshold breach alerts</li>
 *   <li>Intermediate insights every 30 seconds</li>
 * </ul>
 * </p>
 *
 * <p>Designed to run in parallel with 3 stress tests (conservative, moderate, aggressive)
 * and provide early warning of breaking points or memory leaks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class RealtimeMetricsAnalyzer {

    /**
     * Immutable metrics snapshot parsed from JSONL.
     */
    public record MetricLine(
            long timestamp,
            long heapUsedMb,
            long heapMaxMb,
            long heapCommittedMb,
            long gcCollectionCount,
            long gcCollectionTimeMs,
            int threadCount,
            int peakThreadCount,
            long casesProcessed,
            double throughputCasesPerSec) {

        static MetricLine fromJson(String jsonLine) {
            try {
                Pattern pattern = Pattern.compile(
                    "\"timestamp\":(\\d+).*" +
                    "\"heap_used_mb\":(\\d+).*" +
                    "\"heap_max_mb\":(\\d+).*" +
                    "\"heap_committed_mb\":(\\d+).*" +
                    "\"gc_collection_count\":(\\d+).*" +
                    "\"gc_collection_time_ms\":(\\d+).*" +
                    "\"thread_count\":(\\d+).*" +
                    "\"peak_thread_count\":(\\d+).*" +
                    "\"cases_processed\":(\\d+).*" +
                    "\"throughput_cases_per_sec\":(\\d+\\.\\d+|\\d+)");

                Matcher m = pattern.matcher(jsonLine);
                if (!m.find()) {
                    throw new IllegalArgumentException("No match in: " + jsonLine);
                }

                return new MetricLine(
                    Long.parseLong(m.group(1)),
                    Long.parseLong(m.group(2)),
                    Long.parseLong(m.group(3)),
                    Long.parseLong(m.group(4)),
                    Long.parseLong(m.group(5)),
                    Long.parseLong(m.group(6)),
                    Integer.parseInt(m.group(7)),
                    Integer.parseInt(m.group(8)),
                    Long.parseLong(m.group(9)),
                    Double.parseDouble(m.group(10))
                );
            } catch (Exception e) {
                System.err.println("Failed to parse: " + jsonLine);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Immutable analysis result for a single metrics file.
     */
    public record AnalysisResult(
            String testName,
            Instant analysisTime,
            List<MetricLine> lines,
            double heapGrowthMbPerHour,
            double heapGrowthMbPerMin,
            long heapMinMb,
            long heapMaxMb,
            double heapAvgMb,
            long gcCountTotal,
            double gcPausesPerMinute,
            double avgThroughput,
            int threadCountFinal,
            String alertStatus) {

        @Override
        public String toString() {
            return String.format(
                "[%s] %s | Heap: %.0f->%.0f->%.0f MB | GC: %d total | " +
                "Throughput: %.1f cases/sec | Threads: %d | Alert: %s",
                testName, analysisTime.toString().substring(11, 19),
                heapMinMb, heapAvgMb, heapMaxMb, gcCountTotal,
                avgThroughput, threadCountFinal, alertStatus);
        }
    }

    private final Path metricsDir;
    private final Map<String, Long> fileLastReadPosition;
    private final DateTimeFormatter formatter;
    private final PrintWriter summaryWriter;
    private final boolean verbose;

    private static final long HEAP_GROWTH_ALERT_THRESHOLD_MB_PER_HOUR = 1000;
    private static final long GC_PAUSE_ALERT_THRESHOLD_PER_MINUTE = 2;
    private static final int ALERT_SUMMARY_INTERVAL_SECS = 30;

    /**
     * Create analyzer for the specified metrics directory.
     *
     * @param metricsDir Directory containing metrics-*.jsonl files
     * @param summaryOutput File to write analysis summaries (append mode)
     * @param verbose Enable verbose logging
     * @throws IOException if unable to create summary output file
     */
    public RealtimeMetricsAnalyzer(Path metricsDir, Path summaryOutput, boolean verbose)
            throws IOException {
        this.metricsDir = metricsDir;
        this.fileLastReadPosition = new HashMap<>();
        this.formatter = DateTimeFormatter.ISO_INSTANT;
        this.summaryWriter = new PrintWriter(
            Files.newBufferedWriter(summaryOutput, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND));
        this.verbose = verbose;
    }

    /**
     * Start the real-time monitoring loop (blocks until interrupted).
     * Polls for new metrics every 10 seconds and analyzes them.
     *
     * @throws IOException if unable to read metrics files
     * @throws InterruptedException if the thread is interrupted
     */
    public void startMonitoring() throws IOException, InterruptedException {
        System.out.println("[MONITOR] Starting real-time metrics analysis...");
        summaryWriter.println("# Real-time Metrics Analysis Started: " + Instant.now());
        summaryWriter.flush();

        long lastSummaryTime = System.currentTimeMillis();

        while (!Thread.currentThread().isInterrupted()) {
            analyzeAllMetricsFiles();

            long now = System.currentTimeMillis();
            if (now - lastSummaryTime > ALERT_SUMMARY_INTERVAL_SECS * 1000) {
                printDashboard();
                lastSummaryTime = now;
            }

            Thread.sleep(10_000);
        }

        System.out.println("[MONITOR] Monitoring stopped");
        summaryWriter.println("# Real-time Metrics Analysis Stopped: " + Instant.now());
        summaryWriter.flush();
        summaryWriter.close();
    }

    /**
     * Analyze all metrics files in the directory.
     *
     * @throws IOException if unable to read files
     */
    private void analyzeAllMetricsFiles() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(metricsDir,
                "metrics-*.jsonl")) {
            for (Path metricsFile : stream) {
                analyzeMetricsFile(metricsFile);
            }
        }
    }

    /**
     * Analyze a single metrics file, incrementally reading new lines.
     *
     * @param metricsFile Path to metrics JSONL file
     * @throws IOException if unable to read file
     */
    private void analyzeMetricsFile(Path metricsFile) throws IOException {
        if (!Files.exists(metricsFile)) {
            return;
        }

        String testName = metricsFile.getFileName().toString()
            .replace("metrics-", "").replace(".jsonl", "");
        long lastPos = fileLastReadPosition.getOrDefault(metricsFile.toString(), 0L);

        List<MetricLine> allLines = new ArrayList<>();
        List<String> newLines = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(metricsFile.toFile(), "r")) {
            raf.seek(lastPos);

            String line;
            while ((line = raf.readLine()) != null) {
                newLines.add(line);
                try {
                    allLines.add(MetricLine.fromJson(line));
                } catch (Exception e) {
                    if (verbose) {
                        System.err.println("Skipping malformed line: " + line);
                    }
                }
            }

            fileLastReadPosition.put(metricsFile.toString(), raf.getFilePointer());
        }

        if (!newLines.isEmpty()) {
            AnalysisResult result = analyzeMetrics(testName, allLines);
            logAnalysis(result);

            if (shouldAlert(result)) {
                alertOperator(result);
            }

            writeAnalysisFile(testName, result);
        }
    }

    /**
     * Perform statistical analysis on metric lines.
     *
     * @param testName Name of the test (conservative, moderate, aggressive)
     * @param lines Parsed metric lines
     * @return AnalysisResult with calculated statistics
     */
    private AnalysisResult analyzeMetrics(String testName, List<MetricLine> lines) {
        if (lines.isEmpty()) {
            return new AnalysisResult(
                testName, Instant.now(), List.of(),
                0, 0, 0, 0, 0, 0, 0, 0, 0, "EMPTY");
        }

        MetricLine first = lines.getFirst();
        MetricLine last = lines.getLast();

        long heapMinMb = lines.stream().mapToLong(MetricLine::heapUsedMb).min().orElse(0);
        long heapMaxMb = lines.stream().mapToLong(MetricLine::heapUsedMb).max().orElse(0);
        double heapAvgMb = lines.stream().mapToLong(MetricLine::heapUsedMb).average()
            .orElse(0.0);

        long heapDeltaMb = last.heapUsedMb - first.heapUsedMb;
        long timeDeltaMs = last.timestamp - first.timestamp;
        double heapGrowthMbPerHour = (timeDeltaMs > 0)
            ? (heapDeltaMb * 3600_000.0) / timeDeltaMs
            : 0.0;
        double heapGrowthMbPerMin = heapGrowthMbPerHour / 60.0;

        long gcCountTotal = last.gcCollectionCount;
        double gcPausesPerMinute = (timeDeltaMs > 0)
            ? (gcCountTotal * 60_000.0) / timeDeltaMs
            : 0.0;

        double avgThroughput = lines.stream()
            .mapToDouble(MetricLine::throughputCasesPerSec)
            .average()
            .orElse(0.0);

        int threadCountFinal = last.threadCount;

        String alertStatus = "OK";
        if (heapGrowthMbPerHour > HEAP_GROWTH_ALERT_THRESHOLD_MB_PER_HOUR) {
            alertStatus = "HEAP_LEAK";
        } else if (gcPausesPerMinute > GC_PAUSE_ALERT_THRESHOLD_PER_MINUTE) {
            alertStatus = "GC_STORM";
        }

        return new AnalysisResult(
            testName,
            Instant.now(),
            List.copyOf(lines),
            heapGrowthMbPerHour,
            heapGrowthMbPerMin,
            heapMinMb,
            heapMaxMb,
            heapAvgMb,
            gcCountTotal,
            gcPausesPerMinute,
            avgThroughput,
            threadCountFinal,
            alertStatus);
    }

    /**
     * Determine if analysis result should trigger an alert.
     *
     * @param result Analysis result to evaluate
     * @return true if alert conditions met
     */
    private boolean shouldAlert(AnalysisResult result) {
        return !result.alertStatus.equals("OK")
            || result.heapGrowthMbPerHour > HEAP_GROWTH_ALERT_THRESHOLD_MB_PER_HOUR
            || result.gcPausesPerMinute > GC_PAUSE_ALERT_THRESHOLD_PER_MINUTE;
    }

    /**
     * Log analysis result to console and summary file.
     *
     * @param result Analysis result to log
     */
    private void logAnalysis(AnalysisResult result) {
        System.out.println("[ANALYSIS] " + result);
        summaryWriter.println(result);
        summaryWriter.flush();
    }

    /**
     * Send alert to operator about anomalies.
     *
     * @param result Analysis result with anomaly
     */
    private void alertOperator(AnalysisResult result) {
        String alert = String.format(
            "[ALERT] %s: %s - Heap growth: %.0f MB/hour | GC: %.1f pauses/min | " +
            "Throughput: %.1f cases/sec",
            result.analysisTime.toString().substring(11, 19),
            result.testName,
            result.heapGrowthMbPerHour,
            result.gcPausesPerMinute,
            result.avgThroughput);

        System.err.println(alert);
        summaryWriter.println("# " + alert);
        summaryWriter.flush();
    }

    /**
     * Write analysis result to separate JSON file for downstream processing.
     *
     * @param testName Name of the test
     * @param result Analysis result
     * @throws IOException if unable to write file
     */
    private void writeAnalysisFile(String testName, AnalysisResult result)
            throws IOException {
        Path analysisFile = metricsDir.resolve("analysis-" + testName + ".json");

        String json = String.format(
            "{\"test_name\":\"%s\",\"timestamp\":%d,\"lines_analyzed\":%d," +
            "\"heap_growth_mb_per_hour\":%.2f,\"heap_growth_mb_per_min\":%.4f," +
            "\"heap_min_mb\":%d,\"heap_max_mb\":%d,\"heap_avg_mb\":%.1f," +
            "\"gc_count_total\":%d,\"gc_pauses_per_minute\":%.2f," +
            "\"avg_throughput\":%.2f,\"thread_count_final\":%d,\"alert_status\":\"%s\"}",
            result.testName,
            result.analysisTime.toEpochMilli(),
            result.lines.size(),
            result.heapGrowthMbPerHour,
            result.heapGrowthMbPerMin,
            result.heapMinMb,
            result.heapMaxMb,
            result.heapAvgMb,
            result.gcCountTotal,
            result.gcPausesPerMinute,
            result.avgThroughput,
            result.threadCountFinal,
            result.alertStatus);

        Files.writeString(analysisFile, json, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Print a dashboard summary of all monitored tests.
     *
     * @throws IOException if unable to read metrics files
     */
    private void printDashboard() throws IOException {
        System.out.println("\n=== METRICS DASHBOARD [" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] ===");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(metricsDir,
                "analysis-*.json")) {
            for (Path analysisFile : stream) {
                String content = Files.readString(analysisFile);
                extractAndPrint(content);
            }
        }

        System.out.println("============================================\n");
    }

    /**
     * Extract and print key metrics from JSON analysis file.
     *
     * @param json JSON content as string
     */
    private void extractAndPrint(String json) {
        Pattern testPattern = Pattern.compile("\"test_name\":\"([^\"]+)\"");
        Pattern heapPattern = Pattern.compile("\"heap_growth_mb_per_hour\":(\\d+\\.\\d+)");
        Pattern gcPattern = Pattern.compile("\"gc_pauses_per_minute\":(\\d+\\.\\d+)");
        Pattern tputPattern = Pattern.compile("\"avg_throughput\":(\\d+\\.\\d+)");
        Pattern alertPattern = Pattern.compile("\"alert_status\":\"([^\"]+)\"");

        String testName = "";
        String heapGrowth = "0.0";
        String gcPauses = "0.0";
        String throughput = "0.0";
        String alert = "OK";

        Matcher m;
        if ((m = testPattern.matcher(json)).find()) testName = m.group(1);
        if ((m = heapPattern.matcher(json)).find()) heapGrowth = m.group(1);
        if ((m = gcPattern.matcher(json)).find()) gcPauses = m.group(1);
        if ((m = tputPattern.matcher(json)).find()) throughput = m.group(1);
        if ((m = alertPattern.matcher(json)).find()) alert = m.group(1);

        System.out.printf("  %-12s | Heap: %8s MB/h | GC: %5s /min | TPut: %6s c/s | %s%n",
            testName, heapGrowth, gcPauses, throughput, alert);
    }

    /**
     * Main entry point for standalone execution.
     * Usage: java RealtimeMetricsAnalyzer <metrics-dir> <summary-output>
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: java RealtimeMetricsAnalyzer <metrics-dir> [summary-file] [verbose]");
            System.exit(1);
        }

        Path metricsDir = Paths.get(args[0]);
        Path summaryOutput = (args.length > 1)
            ? Paths.get(args[1])
            : metricsDir.resolve("realtime-analysis-summary.txt");
        boolean verbose = (args.length > 2) && args[2].equals("true");

        if (!Files.isDirectory(metricsDir)) {
            System.err.println("Metrics directory not found: " + metricsDir);
            System.exit(1);
        }

        System.out.println("Monitoring metrics in: " + metricsDir);
        System.out.println("Summary output to: " + summaryOutput);
        System.out.println("Verbose: " + verbose);
        System.out.println();

        RealtimeMetricsAnalyzer analyzer = new RealtimeMetricsAnalyzer(
            metricsDir, summaryOutput, verbose);
        analyzer.startMonitoring();
    }
}
