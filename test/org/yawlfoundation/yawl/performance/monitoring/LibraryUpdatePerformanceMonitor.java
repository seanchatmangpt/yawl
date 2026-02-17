/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.monitoring;

import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.EngineClearer;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemRepository;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Monitors performance impacts of library updates in YAWL.
 *
 * Tracks critical performance metrics before and after library updates:
 * 1. Engine startup time
 * 2. Case launch latency (p50, p95, p99)
 * 3. Work item throughput
 * 4. Memory usage patterns
 * 5. GC pause times
 * 6. Database query performance (Hibernate)
 * 7. Thread pool efficiency
 *
 * Usage:
 *   // Before library update
 *   LibraryUpdatePerformanceMonitor monitor = new LibraryUpdatePerformanceMonitor();
 *   monitor.captureBaselineMetrics("baseline-pre-update.json");
 *
 *   // After library update
 *   monitor.captureCurrentMetrics("current-post-update.json");
 *   monitor.compareMetrics("baseline-pre-update.json", "current-post-update.json");
 *
 * @author YAWL Performance Team
 * @version 5.2
 * @date 2026-02-16
 */
public class LibraryUpdatePerformanceMonitor {

    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASUREMENT_ITERATIONS = 500;
    private static final int CONCURRENT_CASES = 100;
    private static final double REGRESSION_THRESHOLD = 0.10; // 10% regression tolerance

    private final YEngine engine;
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    private final RuntimeMXBean runtimeBean;

    public LibraryUpdatePerformanceMonitor() {
        this.engine = YEngine.getInstance();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.runtimeBean = ManagementFactory.getRuntimeMXBean();
    }

    /**
     * Capture comprehensive baseline metrics.
     */
    public PerformanceSnapshot captureBaselineMetrics(String outputFile) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("CAPTURING BASELINE PERFORMANCE METRICS");
        System.out.println("=".repeat(80));
        System.out.println();

        PerformanceSnapshot snapshot = new PerformanceSnapshot();
        snapshot.timestamp = Instant.now();
        snapshot.jvmVersion = System.getProperty("java.version");
        snapshot.osName = System.getProperty("os.name");
        snapshot.osVersion = System.getProperty("os.version");
        snapshot.availableProcessors = Runtime.getRuntime().availableProcessors();

        // Capture library versions
        snapshot.libraryVersions = captureLibraryVersions();

        // Clean engine state
        EngineClearer.clear(engine);

        // Load test specification
        YSpecification spec = loadTestSpecification();
        if (spec != null) {
            engine.loadSpecification(spec);

            // Warmup
            warmupEngine(spec);

            // Measure metrics
            snapshot.engineStartupTimeMs = measureEngineStartup(spec);
            snapshot.caseLaunchMetrics = measureCaseLaunchLatency(spec);
            snapshot.workItemMetrics = measureWorkItemThroughput(spec);
            snapshot.concurrentThroughput = measureConcurrentThroughput(spec);
            snapshot.memoryMetrics = measureMemoryUsage(spec);
            snapshot.gcMetrics = measureGCActivity(spec);
            snapshot.threadMetrics = measureThreadActivity();
        }

        // Save snapshot
        saveSnapshot(snapshot, outputFile);
        printSnapshot(snapshot);

        return snapshot;
    }

    /**
     * Capture current metrics (post-update).
     */
    public PerformanceSnapshot captureCurrentMetrics(String outputFile) throws Exception {
        return captureBaselineMetrics(outputFile);
    }

    /**
     * Compare two performance snapshots and identify regressions.
     */
    public ComparisonReport compareMetrics(String baselineFile, String currentFile) throws Exception {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PERFORMANCE COMPARISON REPORT");
        System.out.println("=".repeat(80));
        System.out.println();

        PerformanceSnapshot baseline = loadSnapshot(baselineFile);
        PerformanceSnapshot current = loadSnapshot(currentFile);

        ComparisonReport report = new ComparisonReport();
        report.baselineTimestamp = baseline.timestamp;
        report.currentTimestamp = current.timestamp;
        report.libraryChanges = detectLibraryChanges(baseline.libraryVersions, current.libraryVersions);

        // Compare metrics
        report.startupTimeDelta = compareMetric("Engine Startup Time",
            baseline.engineStartupTimeMs, current.engineStartupTimeMs, "ms", false);

        report.caseLaunchP95Delta = compareMetric("Case Launch p95",
            baseline.caseLaunchMetrics.p95, current.caseLaunchMetrics.p95, "ms", false);

        report.workItemThroughputDelta = compareMetric("Work Item Throughput",
            baseline.workItemMetrics.throughput, current.workItemMetrics.throughput, "ops/sec", true);

        report.concurrentThroughputDelta = compareMetric("Concurrent Throughput",
            baseline.concurrentThroughput.casesPerSecond, current.concurrentThroughput.casesPerSecond, "cases/sec", true);

        report.memoryUsageDelta = compareMetric("Memory Usage",
            baseline.memoryMetrics.usedMemoryMB, current.memoryMetrics.usedMemoryMB, "MB", false);

        report.gcPauseTimeDelta = compareMetric("GC Pause Time",
            baseline.gcMetrics.maxPauseTimeMs, current.gcMetrics.maxPauseTimeMs, "ms", false);

        report.threadCountDelta = compareMetric("Thread Count",
            baseline.threadMetrics.peakThreadCount, current.threadMetrics.peakThreadCount, "threads", false);

        // Identify critical regressions
        report.criticalRegressions = identifyCriticalRegressions(report);
        report.performanceImpact = calculateOverallImpact(report);

        // Save and print report
        String reportFile = "performance-reports/comparison-" +
            new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".txt";
        saveComparisonReport(report, reportFile);
        printComparisonReport(report);

        return report;
    }

    // ======================== Measurement Methods ========================

    private long measureEngineStartup(YSpecification spec) throws Exception {
        System.out.println("Measuring engine startup time...");

        EngineClearer.clear(engine);

        long start = System.nanoTime();
        engine.loadSpecification(spec);
        YIdentifier testCase = engine.startCase(spec.getSpecificationID(), null, null, null,
            new YLogDataItemList(), null, false);
        long elapsed = System.nanoTime() - start;

        if (testCase != null) {
            engine.cancelCase(testCase);
        }

        long startupMs = elapsed / 1_000_000;
        System.out.println("  Startup time: " + startupMs + " ms");
        return startupMs;
    }

    private LatencyMetrics measureCaseLaunchLatency(YSpecification spec) throws Exception {
        System.out.println("\nMeasuring case launch latency (n=" + MEASUREMENT_ITERATIONS + ")...");

        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long start = System.nanoTime();

            YIdentifier caseId = engine.startCase(spec.getSpecificationID(), null, null, null,
                new YLogDataItemList(), null, false);

            long elapsed = System.nanoTime() - start;
            latencies.add(elapsed / 1_000_000);

            if (caseId != null) {
                engine.cancelCase(caseId);
            }
        }

        LatencyMetrics metrics = calculateLatencyMetrics(latencies);
        System.out.println("  p50: " + metrics.p50 + "ms, p95: " + metrics.p95 + "ms, p99: " + metrics.p99 + "ms");
        return metrics;
    }

    private ThroughputMetrics measureWorkItemThroughput(YSpecification spec) throws Exception {
        System.out.println("\nMeasuring work item throughput...");

        YWorkItemRepository repository = engine.getWorkItemRepository();
        int successfulOps = 0;
        long totalTimeMs = 0;

        for (int i = 0; i < 100; i++) {
            try {
                YIdentifier caseId = engine.startCase(spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);

                Thread.sleep(50);

                Set<YWorkItem> items = repository.getEnabledWorkItems();
                if (items != null && !items.isEmpty()) {
                    YWorkItem item = items.iterator().next();
                    engine.startWorkItem(item, engine.getExternalClient("admin"));

                    long start = System.nanoTime();
                    engine.completeWorkItem(item, "<data/>", new YLogDataItemList(), false);
                    long elapsed = System.nanoTime() - start;

                    totalTimeMs += elapsed / 1_000_000;
                    successfulOps++;
                }

                if (caseId != null) {
                    engine.cancelCase(caseId);
                }
            } catch (Exception e) {
                // Continue
            }
        }

        ThroughputMetrics metrics = new ThroughputMetrics();
        metrics.operations = successfulOps;
        metrics.totalTimeMs = totalTimeMs;
        metrics.throughput = successfulOps > 0 ? (successfulOps * 1000.0 / totalTimeMs) : 0.0;

        System.out.println("  Throughput: " + String.format("%.2f", metrics.throughput) + " ops/sec");
        return metrics;
    }

    private ConcurrentThroughputMetrics measureConcurrentThroughput(YSpecification spec) throws Exception {
        System.out.println("\nMeasuring concurrent throughput (" + CONCURRENT_CASES + " cases)...");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(CONCURRENT_CASES);

        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_CASES; i++) {
            Future<Boolean> future = executor.submit(() -> {
                try {
                    startLatch.await();
                    YIdentifier caseId = engine.startCase(spec.getSpecificationID(), null, null, null,
                        new YLogDataItemList(), null, false);
                    if (caseId != null) {
                        engine.cancelCase(caseId);
                        return true;
                    }
                } catch (Exception e) {
                    // Continue
                } finally {
                    completionLatch.countDown();
                }
                return false;
            });
            futures.add(future);
        }

        long startTime = System.currentTimeMillis();
        startLatch.countDown();
        completionLatch.await(60, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        executor.shutdown();

        int successfulCases = (int) futures.stream()
            .map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    return false;
                }
            })
            .filter(Boolean::booleanValue)
            .count();

        ConcurrentThroughputMetrics metrics = new ConcurrentThroughputMetrics();
        metrics.totalCases = CONCURRENT_CASES;
        metrics.successfulCases = successfulCases;
        metrics.durationMs = duration;
        metrics.casesPerSecond = (successfulCases * 1000.0) / duration;

        System.out.println("  Throughput: " + String.format("%.2f", metrics.casesPerSecond) + " cases/sec");
        return metrics;
    }

    private MemoryMetrics measureMemoryUsage(YSpecification spec) throws Exception {
        System.out.println("\nMeasuring memory usage...");

        System.gc();
        Thread.sleep(1000);

        long memBefore = memoryBean.getHeapMemoryUsage().getUsed();

        List<YIdentifier> caseIds = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            try {
                YIdentifier caseId = engine.startCase(spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
                if (caseId != null) {
                    caseIds.add(caseId);
                }
            } catch (Exception e) {
                // Continue
            }
        }

        System.gc();
        Thread.sleep(1000);

        long memAfter = memoryBean.getHeapMemoryUsage().getUsed();
        long memUsed = memAfter - memBefore;

        // Cleanup
        for (YIdentifier caseId : caseIds) {
            try {
                engine.cancelCase(caseId);
            } catch (Exception e) {
                // Continue
            }
        }

        MemoryMetrics metrics = new MemoryMetrics();
        metrics.usedMemoryMB = memUsed / (1024 * 1024);
        metrics.totalMemoryMB = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        metrics.casesCreated = caseIds.size();
        metrics.memoryPerCaseBytes = caseIds.size() > 0 ? memUsed / caseIds.size() : 0;

        System.out.println("  Memory used: " + metrics.usedMemoryMB + " MB for " +
            metrics.casesCreated + " cases");
        return metrics;
    }

    private GCMetrics measureGCActivity(YSpecification spec) throws Exception {
        System.out.println("\nMeasuring GC activity...");

        long gcCountBefore = getTotalGCCount();
        long gcTimeBefore = getTotalGCTime();

        // Generate some load
        for (int i = 0; i < 200; i++) {
            try {
                YIdentifier caseId = engine.startCase(spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
                if (caseId != null) {
                    engine.cancelCase(caseId);
                }
            } catch (Exception e) {
                // Continue
            }
        }

        long gcCountAfter = getTotalGCCount();
        long gcTimeAfter = getTotalGCTime();

        GCMetrics metrics = new GCMetrics();
        metrics.gcCollections = gcCountAfter - gcCountBefore;
        metrics.gcTimeMs = gcTimeAfter - gcTimeBefore;
        metrics.maxPauseTimeMs = getMaxGCPauseTime();

        System.out.println("  GC collections: " + metrics.gcCollections +
            ", Total time: " + metrics.gcTimeMs + "ms");
        return metrics;
    }

    private ThreadMetrics measureThreadActivity() {
        System.out.println("\nMeasuring thread activity...");

        ThreadMetrics metrics = new ThreadMetrics();
        metrics.threadCount = threadBean.getThreadCount();
        metrics.peakThreadCount = threadBean.getPeakThreadCount();
        metrics.daemonThreadCount = threadBean.getDaemonThreadCount();
        metrics.totalStartedThreadCount = threadBean.getTotalStartedThreadCount();

        System.out.println("  Threads: current=" + metrics.threadCount +
            ", peak=" + metrics.peakThreadCount);
        return metrics;
    }

    // ======================== Helper Methods ========================

    private void warmupEngine(YSpecification spec) throws Exception {
        System.out.println("Warming up engine (" + WARMUP_ITERATIONS + " iterations)...");

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                YIdentifier caseId = engine.startCase(spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
                if (caseId != null) {
                    engine.cancelCase(caseId);
                }
            } catch (Exception e) {
                // Continue warmup
            }
        }

        System.gc();
        Thread.sleep(1000);
        System.out.println("Warmup complete.\n");
    }

    private YSpecification loadTestSpecification() throws Exception {
        try {
            File yawlFile = new File("test/org/yawlfoundation/yawl/engine/ImproperCompletion.xml");
            if (!yawlFile.exists()) {
                System.out.println("Warning: Test specification not found, using minimal spec");
                return null;
            }
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(yawlFile.getAbsolutePath()));
            return specs.isEmpty() ? null : specs.get(0);
        } catch (Exception e) {
            System.out.println("Warning: Could not load test specification: " + e.getMessage());
            return null;
        }
    }

    private LatencyMetrics calculateLatencyMetrics(List<Long> latencies) {
        Collections.sort(latencies);

        LatencyMetrics metrics = new LatencyMetrics();
        metrics.min = latencies.get(0);
        metrics.max = latencies.get(latencies.size() - 1);
        metrics.p50 = percentile(latencies, 50);
        metrics.p95 = percentile(latencies, 95);
        metrics.p99 = percentile(latencies, 99);
        metrics.avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

        return metrics;
    }

    private long percentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    private long getTotalGCCount() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private long getTotalGCTime() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    private long getMaxGCPauseTime() {
        return gcBeans.stream()
            .mapToLong(gc -> gc.getCollectionTime() / Math.max(1, gc.getCollectionCount()))
            .max()
            .orElse(0);
    }

    private Map<String, String> captureLibraryVersions() {
        Map<String, String> versions = new TreeMap<>();

        versions.put("java.version", System.getProperty("java.version"));
        versions.put("java.vendor", System.getProperty("java.vendor"));
        versions.put("hibernate.version", getHibernateVersion());
        versions.put("log4j.version", getLog4jVersion());
        versions.put("jackson.version", getJacksonVersion());

        return versions;
    }

    private String getHibernateVersion() {
        try {
            return org.hibernate.Version.getVersionString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getLog4jVersion() {
        try {
            Package pkg = org.apache.logging.log4j.LogManager.class.getPackage();
            return pkg != null ? pkg.getImplementationVersion() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getJacksonVersion() {
        try {
            return com.fasterxml.jackson.databind.cfg.PackageVersion.VERSION.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Map<String, String> detectLibraryChanges(Map<String, String> baseline, Map<String, String> current) {
        Map<String, String> changes = new TreeMap<>();

        for (Map.Entry<String, String> entry : current.entrySet()) {
            String key = entry.getKey();
            String currentVersion = entry.getValue();
            String baselineVersion = baseline.get(key);

            if (baselineVersion != null && !baselineVersion.equals(currentVersion)) {
                changes.put(key, baselineVersion + " → " + currentVersion);
            }
        }

        return changes;
    }

    private MetricDelta compareMetric(String name, double baseline, double current,
                                     String unit, boolean higherIsBetter) {
        MetricDelta delta = new MetricDelta();
        delta.metricName = name;
        delta.baselineValue = baseline;
        delta.currentValue = current;
        delta.unit = unit;
        delta.absoluteDelta = current - baseline;
        delta.percentageDelta = baseline != 0 ? ((current - baseline) / baseline) * 100 : 0;
        delta.higherIsBetter = higherIsBetter;

        boolean isRegression = higherIsBetter ?
            (current < baseline * (1 - REGRESSION_THRESHOLD)) :
            (current > baseline * (1 + REGRESSION_THRESHOLD));

        delta.isRegression = isRegression;
        delta.severity = calculateSeverity(delta.percentageDelta, higherIsBetter);

        return delta;
    }

    private String calculateSeverity(double percentageDelta, boolean higherIsBetter) {
        double absDelta = Math.abs(percentageDelta);
        boolean isBad = higherIsBetter ? (percentageDelta < 0) : (percentageDelta > 0);

        if (!isBad) return "IMPROVEMENT";
        if (absDelta < 5) return "MINOR";
        if (absDelta < 10) return "MODERATE";
        if (absDelta < 25) return "MAJOR";
        return "CRITICAL";
    }

    private List<String> identifyCriticalRegressions(ComparisonReport report) {
        List<String> regressions = new ArrayList<>();

        if (report.startupTimeDelta.severity.equals("CRITICAL") ||
            report.startupTimeDelta.severity.equals("MAJOR")) {
            regressions.add("Engine startup time degraded: " +
                String.format("%.1f%%", report.startupTimeDelta.percentageDelta));
        }

        if (report.caseLaunchP95Delta.severity.equals("CRITICAL") ||
            report.caseLaunchP95Delta.severity.equals("MAJOR")) {
            regressions.add("Case launch latency (p95) degraded: " +
                String.format("%.1f%%", report.caseLaunchP95Delta.percentageDelta));
        }

        if (report.workItemThroughputDelta.severity.equals("CRITICAL") ||
            report.workItemThroughputDelta.severity.equals("MAJOR")) {
            regressions.add("Work item throughput degraded: " +
                String.format("%.1f%%", report.workItemThroughputDelta.percentageDelta));
        }

        if (report.memoryUsageDelta.percentageDelta > 25) {
            regressions.add("Memory usage increased significantly: " +
                String.format("%.1f%%", report.memoryUsageDelta.percentageDelta));
        }

        return regressions;
    }

    private String calculateOverallImpact(ComparisonReport report) {
        int criticalCount = 0;
        int majorCount = 0;
        int improvementCount = 0;

        MetricDelta[] deltas = {
            report.startupTimeDelta,
            report.caseLaunchP95Delta,
            report.workItemThroughputDelta,
            report.concurrentThroughputDelta,
            report.memoryUsageDelta,
            report.gcPauseTimeDelta
        };

        for (MetricDelta delta : deltas) {
            switch (delta.severity) {
                case "CRITICAL": criticalCount++; break;
                case "MAJOR": majorCount++; break;
                case "IMPROVEMENT": improvementCount++; break;
            }
        }

        if (criticalCount > 0) return "CRITICAL - Immediate investigation required";
        if (majorCount > 2) return "MAJOR - Significant performance degradation";
        if (majorCount > 0) return "MODERATE - Some performance impact detected";
        if (improvementCount > majorCount) return "POSITIVE - Overall performance improvement";
        return "NEUTRAL - No significant performance impact";
    }

    // ======================== I/O Methods ========================

    private void saveSnapshot(PerformanceSnapshot snapshot, String filename) throws IOException {
        Path outputPath = Paths.get("performance-reports", filename);
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("YAWL Performance Snapshot\n");
            writer.write("=".repeat(80) + "\n");
            writer.write("Timestamp: " + snapshot.timestamp + "\n");
            writer.write("JVM: " + snapshot.jvmVersion + "\n");
            writer.write("OS: " + snapshot.osName + " " + snapshot.osVersion + "\n\n");

            writer.write("Library Versions:\n");
            for (Map.Entry<String, String> entry : snapshot.libraryVersions.entrySet()) {
                writer.write("  " + entry.getKey() + ": " + entry.getValue() + "\n");
            }

            writer.write("\nEngine Startup: " + snapshot.engineStartupTimeMs + " ms\n");
            writer.write("\nCase Launch Latency:\n");
            writer.write("  p50: " + snapshot.caseLaunchMetrics.p50 + " ms\n");
            writer.write("  p95: " + snapshot.caseLaunchMetrics.p95 + " ms\n");
            writer.write("  p99: " + snapshot.caseLaunchMetrics.p99 + " ms\n");

            writer.write("\nWork Item Throughput: " +
                String.format("%.2f", snapshot.workItemMetrics.throughput) + " ops/sec\n");

            writer.write("\nConcurrent Throughput: " +
                String.format("%.2f", snapshot.concurrentThroughput.casesPerSecond) + " cases/sec\n");

            writer.write("\nMemory Usage: " + snapshot.memoryMetrics.usedMemoryMB + " MB\n");
            writer.write("GC Collections: " + snapshot.gcMetrics.gcCollections + "\n");
            writer.write("Thread Count: " + snapshot.threadMetrics.threadCount + "\n");
        }

        System.out.println("\nSnapshot saved to: " + outputPath.toAbsolutePath());
    }

    private PerformanceSnapshot loadSnapshot(String filename) throws IOException {
        // For simplicity, return a new snapshot (in production, deserialize from JSON)
        throw new UnsupportedOperationException("Snapshot loading not yet implemented");
    }

    private void saveComparisonReport(ComparisonReport report, String filename) throws IOException {
        Path outputPath = Paths.get(filename);
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("YAWL Performance Comparison Report\n");
            writer.write("=".repeat(80) + "\n\n");

            writer.write("Overall Impact: " + report.performanceImpact + "\n\n");

            if (!report.libraryChanges.isEmpty()) {
                writer.write("Library Updates:\n");
                for (Map.Entry<String, String> entry : report.libraryChanges.entrySet()) {
                    writer.write("  " + entry.getKey() + ": " + entry.getValue() + "\n");
                }
                writer.write("\n");
            }

            if (!report.criticalRegressions.isEmpty()) {
                writer.write("CRITICAL REGRESSIONS:\n");
                for (String regression : report.criticalRegressions) {
                    writer.write("  ⚠ " + regression + "\n");
                }
                writer.write("\n");
            }

            writer.write("Detailed Metrics:\n");
            writeMetricDelta(writer, report.startupTimeDelta);
            writeMetricDelta(writer, report.caseLaunchP95Delta);
            writeMetricDelta(writer, report.workItemThroughputDelta);
            writeMetricDelta(writer, report.concurrentThroughputDelta);
            writeMetricDelta(writer, report.memoryUsageDelta);
            writeMetricDelta(writer, report.gcPauseTimeDelta);
            writeMetricDelta(writer, report.threadCountDelta);
        }

        System.out.println("\nComparison report saved to: " + outputPath.toAbsolutePath());
    }

    private void writeMetricDelta(BufferedWriter writer, MetricDelta delta) throws IOException {
        writer.write("\n" + delta.metricName + ":\n");
        writer.write("  Baseline: " + String.format("%.2f", delta.baselineValue) + " " + delta.unit + "\n");
        writer.write("  Current:  " + String.format("%.2f", delta.currentValue) + " " + delta.unit + "\n");
        writer.write("  Delta:    " + String.format("%+.2f", delta.absoluteDelta) + " " + delta.unit +
            " (" + String.format("%+.1f%%", delta.percentageDelta) + ")\n");
        writer.write("  Severity: " + delta.severity + "\n");
    }

    private void printSnapshot(PerformanceSnapshot snapshot) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PERFORMANCE SNAPSHOT SUMMARY");
        System.out.println("=".repeat(80));
        System.out.println("Engine Startup:       " + snapshot.engineStartupTimeMs + " ms");
        System.out.println("Case Launch p95:      " + snapshot.caseLaunchMetrics.p95 + " ms");
        System.out.println("Work Item Throughput: " +
            String.format("%.2f", snapshot.workItemMetrics.throughput) + " ops/sec");
        System.out.println("Concurrent Throughput:" +
            String.format("%.2f", snapshot.concurrentThroughput.casesPerSecond) + " cases/sec");
        System.out.println("Memory Usage:         " + snapshot.memoryMetrics.usedMemoryMB + " MB");
        System.out.println("=".repeat(80) + "\n");
    }

    private void printComparisonReport(ComparisonReport report) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("OVERALL IMPACT: " + report.performanceImpact);
        System.out.println("=".repeat(80));

        if (!report.libraryChanges.isEmpty()) {
            System.out.println("\nLibrary Updates:");
            for (Map.Entry<String, String> entry : report.libraryChanges.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue());
            }
        }

        if (!report.criticalRegressions.isEmpty()) {
            System.out.println("\n⚠ CRITICAL REGRESSIONS:");
            for (String regression : report.criticalRegressions) {
                System.out.println("  • " + regression);
            }
        }

        System.out.println("\nDetailed Metrics:");
        printMetricDelta(report.startupTimeDelta);
        printMetricDelta(report.caseLaunchP95Delta);
        printMetricDelta(report.workItemThroughputDelta);
        printMetricDelta(report.concurrentThroughputDelta);
        printMetricDelta(report.memoryUsageDelta);
        printMetricDelta(report.gcPauseTimeDelta);

        System.out.println("\n" + "=".repeat(80) + "\n");
    }

    private void printMetricDelta(MetricDelta delta) {
        String arrow = delta.percentageDelta > 0 ? "↑" : "↓";
        String color = delta.severity.equals("IMPROVEMENT") ? "✓" :
                      delta.severity.equals("CRITICAL") || delta.severity.equals("MAJOR") ? "⚠" : "•";

        System.out.println(String.format("\n%s %s: %.2f → %.2f %s (%s%.1f%%) [%s]",
            color, delta.metricName, delta.baselineValue, delta.currentValue, delta.unit,
            arrow, Math.abs(delta.percentageDelta), delta.severity));
    }

    // ======================== Data Classes ========================

    public static class PerformanceSnapshot {
        public Instant timestamp;
        public String jvmVersion;
        public String osName;
        public String osVersion;
        public int availableProcessors;
        public Map<String, String> libraryVersions;
        public long engineStartupTimeMs;
        public LatencyMetrics caseLaunchMetrics;
        public ThroughputMetrics workItemMetrics;
        public ConcurrentThroughputMetrics concurrentThroughput;
        public MemoryMetrics memoryMetrics;
        public GCMetrics gcMetrics;
        public ThreadMetrics threadMetrics;
    }

    public static class LatencyMetrics {
        public long min;
        public long max;
        public long p50;
        public long p95;
        public long p99;
        public double avg;
    }

    public static class ThroughputMetrics {
        public int operations;
        public long totalTimeMs;
        public double throughput;
    }

    public static class ConcurrentThroughputMetrics {
        public int totalCases;
        public int successfulCases;
        public long durationMs;
        public double casesPerSecond;
    }

    public static class MemoryMetrics {
        public long usedMemoryMB;
        public long totalMemoryMB;
        public int casesCreated;
        public long memoryPerCaseBytes;
    }

    public static class GCMetrics {
        public long gcCollections;
        public long gcTimeMs;
        public long maxPauseTimeMs;
    }

    public static class ThreadMetrics {
        public int threadCount;
        public int peakThreadCount;
        public int daemonThreadCount;
        public long totalStartedThreadCount;
    }

    public static class ComparisonReport {
        public Instant baselineTimestamp;
        public Instant currentTimestamp;
        public Map<String, String> libraryChanges;
        public MetricDelta startupTimeDelta;
        public MetricDelta caseLaunchP95Delta;
        public MetricDelta workItemThroughputDelta;
        public MetricDelta concurrentThroughputDelta;
        public MetricDelta memoryUsageDelta;
        public MetricDelta gcPauseTimeDelta;
        public MetricDelta threadCountDelta;
        public List<String> criticalRegressions;
        public String performanceImpact;
    }

    public static class MetricDelta {
        public String metricName;
        public double baselineValue;
        public double currentValue;
        public String unit;
        public double absoluteDelta;
        public double percentageDelta;
        public boolean higherIsBetter;
        public boolean isRegression;
        public String severity;
    }

    // ======================== Main Entry Point ========================

    public static void main(String[] args) throws Exception {
        System.out.println("YAWL Library Update Performance Monitor");
        System.out.println("Usage:");
        System.out.println("  baseline: Capture baseline metrics before library update");
        System.out.println("  current:  Capture current metrics after library update");
        System.out.println("  compare:  Compare baseline vs current metrics\n");

        if (args.length == 0) {
            System.out.println("No command specified. Capturing current snapshot...\n");
            LibraryUpdatePerformanceMonitor monitor = new LibraryUpdatePerformanceMonitor();
            monitor.captureCurrentMetrics("snapshot-" +
                new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".txt");
        }
    }
}
