/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Performance metrics collection and management system.
 *
 * Provides real-time metrics collection, aggregation, and
 * reporting for all benchmark components.
 *
 * @author YAWL Foundation
 * @version 6.0.0-GA
 */
public class BenchmarkMetrics {

    private final Map<String, MetricValue> metrics = new ConcurrentHashMap<>();
    private final Map<String, MetricCollector> collectors = new ConcurrentHashMap<>();
    private final Map<String, ObservableMetric> observableMetrics = new ConcurrentHashMap<>();
    private final Map<String, MetricAggregator> aggregators = new ConcurrentHashMap<>();

    // Performance tracking
    private final LongAdder totalOperations = new LongAdder();
    private final LongAdder totalLatency = new LongAdder();
    private final LongAdder totalErrors = new LongAdder();

    // Latency distribution
    private final ConcurrentSkipListMap<Long, LongAdder> latencyDistribution = new ConcurrentSkipListMap<>();
    private final AtomicReference<LatencyStats> latencyStats = new AtomicReference<>();

    public BenchmarkMetrics() {
        initializeDefaultMetrics();
    }

    private void initializeDefaultMetrics() {
        // Initialize core metrics
        registerMetric("throughput", 0.0);
        registerMetric("p99_latency_ms", 0.0);
        registerMetric("p95_latency_ms", 0.0);
        registerMetric("p50_latency_ms", 0.0);
        registerMetric("error_rate", 0.0);
        registerMetric("success_rate", 0.0);
        registerMetric("avg_latency_ms", 0.0);
        registerMetric("max_latency_ms", 0.0);
        registerMetric("min_latency_ms", Long.MAX_VALUE);

        // Initialize collectors for core metrics
        registerCollector("total_operations", totalOperations::sum);
        registerCollector("total_latency_ms", totalLatency::sum);
        registerCollector("total_errors", totalErrors::sum);

        // Initialize aggregators
        registerAggregator("latency_percentiles", new LatencyPercentileAggregator());
        registerAggregator("performance_summary", new PerformanceSummaryAggregator());
    }

    /**
     * Register a metric with initial value
     */
    public void registerMetric(String name, double initialValue) {
        metrics.put(name, new MetricValue(initialValue));
    }

    /**
     * Register a metric collector
     */
    public void registerCollector(String name, MetricCollector collector) {
        collectors.put(name, collector);
    }

    /**
     * Register an observable metric
     */
    public void registerObservableMetric(String name, ObservableMetric metric) {
        observableMetrics.put(name, metric);
    }

    /**
     * Register an aggregator
     */
    public void registerAggregator(String name, MetricAggregator aggregator) {
        aggregators.put(name, aggregator);
    }

    /**
     * Update a metric value
     */
    public void updateMetric(String name, double value) {
        MetricValue metric = metrics.get(name);
        if (metric != null) {
            metric.update(value);
        }
    }

    /**
     * Increment a counter metric
     */
    public void incrementMetric(String name) {
        MetricValue metric = metrics.get(name);
        if (metric != null) {
            metric.increment();
        }
    }

    /**
     * Get a metric value
     */
    public double getMetric(String name) {
        MetricValue metric = metrics.get(name);
        return metric != null ? metric.getValue() : 0.0;
    }

    /**
     * Get all metric values
     */
    public Map<String, Double> getAllMetrics() {
        Map<String, Double> result = new ConcurrentHashMap<>();
        metrics.forEach((name, value) -> result.put(name, value.getValue()));
        return result;
    }

    /**
     * Record benchmark metrics
     */
    public void recordBenchmarkMetrics(Map<String, Object> benchmarkMetrics) {
        // Record operation count
        if (benchmarkMetrics.containsKey("operations")) {
            long operations = (long) benchmarkMetrics.get("operations");
            totalOperations.add(operations);
        }

        // Record latency
        if (benchmarkMetrics.containsKey("latency_ms")) {
            double latency = (double) benchmarkMetrics.get("latency_ms");
            totalLatency.add((long) latency);

            // Update latency distribution
            long latencyBucket = (long) latency / 10; // 10ms buckets
            latencyDistribution.computeIfAbsent(latencyBucket, k -> new LongAdder()).increment();

            // Update min/max latency
            updateMetric("min_latency_ms", Math.min(getMetric("min_latency_ms"), latency));
            updateMetric("max_latency_ms", Math.max(getMetric("max_latency_ms"), latency));
        }

        // Record errors
        if (benchmarkMetrics.containsKey("errors")) {
            long errors = (long) benchmarkMetrics.get("errors");
            totalErrors.add(errors);
        }

        // Update derived metrics
        updateDerivedMetrics();

        // Update latency statistics
        updateLatencyStats();

        // Notify observable metrics
        notifyObservableMetrics(benchmarkMetrics);
    }

    /**
     * Record operation timing
     */
    public void recordOperation(long latencyMs, boolean success) {
        totalOperations.increment();
        totalLatency.add(latencyMs);

        if (!success) {
            totalErrors.increment();
        }

        // Update latency distribution
        long latencyBucket = latencyMs / 10; // 10ms buckets
        latencyDistribution.computeIfAbsent(latencyBucket, k -> new LongAdder()).increment();

        // Update metrics
        updateMetric("avg_latency_ms",
            totalOperations.sum() > 0 ? (double) totalLatency.sum() / totalOperations.sum() : 0);
        updateMetric("min_latency_ms", Math.min(getMetric("min_latency_ms"), latencyMs));
        updateMetric("max_latency_ms", Math.max(getMetric("max_latency_ms"), latencyMs));
        updateMetric("error_rate",
            totalOperations.sum() > 0 ? (double) totalErrors.sum() / totalOperations.sum() : 0);
        updateMetric("success_rate",
            totalOperations.sum() > 0 ? 1.0 - (double) totalErrors.sum() / totalOperations.sum() : 1.0);

        // Update percentiles
        updatePercentiles();
    }

    /**
     * Update derived metrics
     */
    private void updateDerivedMetrics() {
        long totalOps = totalOperations.sum();
        if (totalOps > 0) {
            updateMetric("avg_latency_ms", (double) totalLatency.sum() / totalOps);
            updateMetric("error_rate", (double) totalErrors.sum() / totalOps);
            updateMetric("success_rate", 1.0 - (double) totalErrors.sum() / totalOps);

            // Calculate throughput (operations per second)
            double throughput = calculateThroughput();
            updateMetric("throughput", throughput);
        }
    }

    /**
     * Update percentile metrics
     */
    private void updatePercentiles() {
        // Use aggregators to calculate percentiles
        Map<String, Object> percentiles = aggregators.get("latency_percentiles")
            .aggregate(metrics.entrySet().stream()
                .collect(ConcurrentHashMap::new,
                    (map, entry) -> map.put(entry.getKey(), entry.getValue().getValue()),
                    ConcurrentHashMap::putAll));

        if (percentiles.containsKey("p99")) {
            updateMetric("p99_latency_ms", (double) percentiles.get("p99"));
        }
        if (percentiles.containsKey("p95")) {
            updateMetric("p95_latency_ms", (double) percentiles.get("p95"));
        }
        if (percentiles.containsKey("p50")) {
            updateMetric("p50_latency_ms", (double) percentiles.get("p50"));
        }
    }

    /**
     * Update latency statistics
     */
    private void updateLatencyStats() {
        long totalOps = totalOperations.sum();
        if (totalOps > 0) {
            LatencyStats stats = new LatencyStats(
                totalOps,
                (double) totalLatency.sum() / totalOps,
                latencyDistribution.firstKey(),
                latencyDistribution.lastKey()
            );
            latencyStats.set(stats);
        }
    }

    /**
     * Calculate current throughput
     */
    private double calculateThroughput() {
        // This would typically be calculated based on time window
        // For now, return operations per minute
        long totalOps = totalOperations.sum();
        long elapsedMs = System.currentTimeMillis() - getStartTime();
        long elapsedMinutes = elapsedMs / (1000 * 60);
        return elapsedMinutes > 0 ? (double) totalOps / elapsedMinutes : 0;
    }

    /**
     * Get start time for metrics collection
     */
    private long getStartTime() {
        // This should be stored when metrics collection starts
        return System.currentTimeMillis() - (1000 * 60 * 5); // Default to 5 minutes ago
    }

    /**
     * Notify observable metrics of updates
     */
    private void notifyObservableMetrics(Map<String, Object> benchmarkMetrics) {
        observableMetrics.forEach((name, metric) -> {
            Object value = benchmarkMetrics.get(name);
            if (value != null) {
                metric.update(value);
            }
        });
    }

    /**
     * Get performance summary
     */
    public Map<String, Object> getPerformanceSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();

        // Add basic metrics
        summary.putAll(getAllMetrics());

        // Add derived metrics from aggregators
        summary.putAll(aggregators.get("performance_summary")
            .aggregate(metrics.entrySet().stream()
                .collect(ConcurrentHashMap::new,
                    (map, entry) -> map.put(entry.getKey(), entry.getValue().getValue()),
                    ConcurrentHashMap::putAll)));

        // Add latency statistics
        LatencyStats stats = latencyStats.get();
        if (stats != null) {
            summary.put("latency_stats", Map.of(
                "total_operations", stats.totalOperations,
                "avg_latency", stats.avgLatency,
                "min_latency", stats.minLatency,
                "max_latency", stats.maxLatency
            ));
        }

        return summary;
    }

    /**
     * Check if metric exists
     */
    public boolean containsMetric(String name) {
        return metrics.containsKey(name) || collectors.containsKey(name);
    }

    /**
     * Get metric value as string
     */
    public String getMetricAsString(String name) {
        MetricValue metric = metrics.get(name);
        return metric != null ? metric.toString() : "N/A";
    }

    /**
     * Get all metric values as formatted string
     */
    public String getAllMetricsFormatted() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Benchmark Metrics ===\n");

        getAllMetrics().forEach((name, value) -> {
            sb.append(String.format("%-20s: %.2f\n", name, value));
        });

        return sb.toString();
    }

    /**
     * Shutdown metrics collection
     */
    public void shutdown() {
        metrics.clear();
        collectors.clear();
        observableMetrics.clear();
        aggregators.clear();
        latencyDistribution.clear();
    }

    // =========================================================================
    // Nested Interfaces and Classes
    // =========================================================================

    public interface MetricCollector {
        double getValue();
    }

    public interface ObservableMetric {
        void update(Object value);
    }

    public interface MetricAggregator {
        Map<String, Object> aggregate(Map<String, Object> metrics);
    }

    public interface ThresholdMonitor {
        void checkThreshold(String metric, double value);
        void setThreshold(String metric, double threshold);
    }

    /**
     * Simple metric value holder
     */
    private static class MetricValue {
        private final DoubleAdder value = new DoubleAdder();
        private long lastUpdate;

        public MetricValue(double initialValue) {
            this.value.add(initialValue);
            this.lastUpdate = System.currentTimeMillis();
        }

        public void update(double newValue) {
            this.value.reset();
            this.value.add(newValue);
            this.lastUpdate = System.currentTimeMillis();
        }

        public void increment() {
            this.value.add(1);
            this.lastUpdate = System.currentTimeMillis();
        }

        public double getValue() {
            return value.sum();
        }

        @Override
        public String toString() {
            return String.format("%.2f", getValue());
        }
    }

    /**
     * Latency statistics holder
     */
    private static class LatencyStats {
        final long totalOperations;
        final double avgLatency;
        final long minLatency;
        final long maxLatency;

        public LatencyStats(long totalOperations, double avgLatency, long minLatency, long maxLatency) {
            this.totalOperations = totalOperations;
            this.avgLatency = avgLatency;
            this.minLatency = minLatency;
            this.maxLatency = maxLatency;
        }
    }

    /**
     * Latency percentile aggregator
     */
    private static class LatencyPercentileAggregator implements MetricAggregator {
        @Override
        public Map<String, Object> aggregate(Map<String, Object> metrics) {
            Map<String, Object> result = new ConcurrentHashMap<>();

            // Calculate percentiles from latency distribution
            // This would be implemented based on the actual distribution data

            result.put("p50", 50.0); // Placeholder
            result.put("p95", 95.0); // Placeholder
            result.put("p99", 99.0); // Placeholder

            return result;
        }
    }

    /**
     * Performance summary aggregator
     */
    private static class PerformanceSummaryAggregator implements MetricAggregator {
        @Override
        public Map<String, Object> aggregate(Map<String, Object> metrics) {
            Map<String, Object> summary = new ConcurrentHashMap<>();

            // Calculate performance score based on metrics
            double throughput = metrics.containsKey("throughput") ?
                (double) metrics.get("throughput") : 0;
            double errorRate = metrics.containsKey("error_rate") ?
                (double) metrics.get("error_rate") : 0;
            double p99Latency = metrics.containsKey("p99_latency_ms") ?
                (double) metrics.get("p99_latency_ms") : 0;

            // Calculate performance score (0-100)
            double score = calculatePerformanceScore(throughput, errorRate, p99Latency);
            summary.put("performance_score", score);

            // Performance tier
            String tier = getPerformanceTier(score);
            summary.put("performance_tier", tier);

            return summary;
        }

        private double calculatePerformanceScore(double throughput, double errorRate, double p99Latency) {
            // Simple scoring formula based on targets
            // Throughput: max 60 points (target >1000 ops/sec)
            double throughputScore = Math.min(60, throughput / 1000 * 60);

            // Error rate: max 20 points (target <1%)
            double errorScore = Math.max(0, 20 - errorRate * 2000);

            // Latency: max 20 points (target <200ms p99)
            double latencyScore = Math.max(0, 20 - p99Latency / 10);

            return throughputScore + errorScore + latencyScore;
        }

        private String getPerformanceTier(double score) {
            if (score >= 90) return "EXCEPTIONAL";
            if (score >= 80) return "EXCELLENT";
            if (score >= 70) return "GOOD";
            if (score >= 60) return "SATISFACTORY";
            if (score >= 50) return "NEEDS_IMPROVEMENT";
            return "CRITICAL";
        }
    }
}