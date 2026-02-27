/*
 * YAWL v6.0.0-GA Performance Monitor Framework
 *
 * Comprehensive performance monitoring for benchmark agents
 * Includes metrics collection, aggregation, and reporting
 */

package org.yawlfoundation.yawl.benchmark.framework;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced performance monitoring system for benchmark agents
 * Collects metrics for:
 * - Latency measurements
 * - Throughput analysis
 * - Memory usage tracking
 * - Error rate monitoring
 * - Virtual thread efficiency
 */
public class PerformanceMonitor {

    // Time-based metrics
    private final Map<String, List<Long>> operationLatencies;
    private final Map<String, List<Instant>> operationTimestamps;

    // Concurrency metrics
    private final Map<String, AtomicLong> throughputMetrics;
    private final Map<String, AtomicLong> virtualThreadMetrics;

    // Resource metrics
    private final Map<String, List<Long>> memoryUsageMetrics;
    private final Map<String, List<Double>> cpuUsageMetrics;

    // Error metrics
    private final Map<String, Long> errorCounts;
    private final Map<String, List<BaseBenchmarkAgent.BenchmarkError>> errorHistory;

    // Benchmark metadata
    private final String agentName;
    private final Map<String, Object> metadata;

    // Aggregates for fast access
    private final Map<String, MetricSummary> cachedSummaries;

    public PerformanceMonitor(String agentName) {
        this.agentName = agentName;
        this.metadata = new ConcurrentHashMap<>();

        // Initialize metrics containers
        this.operationLatencies = new ConcurrentHashMap<>();
        this.operationTimestamps = new ConcurrentHashMap<>();
        this.throughputMetrics = new ConcurrentHashMap<>();
        this.virtualThreadMetrics = new ConcurrentHashMap<>();
        this.memoryUsageMetrics = new ConcurrentHashMap<>();
        this.cpuUsageMetrics = new ConcurrentHashMap<>();
        this.errorCounts = new ConcurrentHashMap<>();
        this.errorHistory = new ConcurrentHashMap<>();
        this.cachedSummaries = new ConcurrentHashMap<>();

        // Initialize counters
        throughputMetrics.put("operations", new AtomicLong(0));
        throughputMetrics.put("success", new AtomicLong(0));
        throughputMetrics.put("failures", new AtomicLong(0));
        virtualThreadMetrics.put("total_created", new AtomicLong(0));
        virtualThreadMetrics.put("active_count", new AtomicLong(0));
    }

    /**
     * Record operation completion time
     */
    public void recordOperation(int threadCount, long duration, long successes, long failures) {
        String key = "latency_" + threadCount + "_threads";
        operationLatencies.computeIfAbsent(key, k -> new ArrayList<>()).add(duration);

        Instant now = Instant.now();
        operationTimestamps.computeIfAbsent(key, k -> new ArrayList<>()).add(now);

        // Update throughput metrics
        throughputMetrics.get("operations").addAndGet(threadCount);
        throughputMetrics.get("success").addAndGet(successes);
        throughputMetrics.get("failures").addAndGet(failures);
    }

    /**
     * Record virtual thread metrics
     */
    public void recordVirtualThreadOperation(long startTime, long endTime, boolean successful) {
        long duration = endTime - startTime;
        String key = "virtual_thread_latency";

        operationLatencies.computeIfAbsent(key, k -> new ArrayList<>()).add(duration);

        throughputMetrics.get("operations").incrementAndGet();
        if (successful) {
            throughputMetrics.get("success").incrementAndGet();
        } else {
            throughputMetrics.get("failures").incrementAndGet();
        }
    }

    /**
     * Record memory usage
     */
    public void recordMemoryUsage(long bytesUsed) {
        memoryUsageMetrics.computeIfAbsent("current_usage", k -> new ArrayList<>()).add(bytesUsed);

        // Calculate average memory usage over time
        double avgMemory = calculateAverage(memoryUsageMetrics.get("current_usage"));
        cpuUsageMetrics.computeIfAbsent("memory_trend", k -> new ArrayList<>()).add(avgMemory);
    }

    /**
     * Record CPU usage percentage
     */
    public void recordCPUUsage(double cpuPercentage) {
        cpuUsageMetrics.computeIfAbsent("cpu_usage", k -> new ArrayList<>()).add(cpuPercentage);
    }

    /**
     * Record error
     */
    public void recordError(BaseBenchmarkAgent.BenchmarkError error) {
        String errorType = error.errorType();
        errorCounts.merge(errorType, 1L, Long::sum);

        errorHistory.computeIfAbsent(errorType, k -> new ArrayList<>()).add(error);
    }

    /**
     * Get performance summary for key metrics
     */
    public MetricSummary getSummaryForMetric(String metricKey) {
        // Return cached version if available
        MetricSummary cached = cachedSummaries.get(metricKey);
        if (cached != null) {
            return cached;
        }

        // Calculate new summary
        List<Long> values = operationLatencies.get(metricKey);
        if (values == null || values.isEmpty()) {
            return new MetricSummary(metricKey, 0, 0, 0, 0, 0);
        }

        double average = values.stream().mapToLong(Long::longValue).average().orElse(0);
        double maximum = values.stream().mapToLong(Long::longValue).max().orElse(0);
        double minimum = values.stream().mapToLong(Long::longValue).min().orElse(0);
        double p95 = calculateP95(values);
        long errorCount = errorCounts.getOrDefault(metricKey, 0L);

        MetricSummary summary = new MetricSummary(metricKey, average, maximum, minimum, p95, errorCount);
        cachedSummaries.put(metricKey, summary);
        return summary;
    }

    /**
     * Generate comprehensive performance summary
     */
    public Map<String, MetricSummary> generateSummary() {
        Map<String, MetricSummary> summary = new HashMap<>();

        // Latency metrics
        operationLatencies.keySet().forEach(key -> {
            summary.put(key, getSummaryForMetric(key));
        });

        // Throughput metrics
        throughputMetrics.forEach((key, counter) -> {
            summary.put("throughput_" + key,
                new MetricSummary(key, counter.get(), counter.get(), counter.get(), counter.get(), 0));
        });

        // Virtual thread metrics
        virtualThreadMetrics.forEach((key, counter) -> {
            summary.put("virtual_thread_" + key,
                new MetricSummary(key, counter.get(), counter.get(), counter.get(), counter.get(), 0));
        });

        // Memory metrics
        memoryUsageMetrics.forEach((key, values) -> {
            double avg = calculateAverage(values);
            summary.put("memory_" + key,
                new MetricSummary(key, avg, Collections.max(values), Collections.min(values), avg, 0));
        });

        // Error metrics
        errorCounts.forEach((key, count) -> {
            summary.put("error_" + key,
                new MetricSummary(key, count, count, count, count, 0));
        });

        return summary;
    }

    /**
     * Get performance insights
     */
    public PerformanceInsights getInsights() {
        Map<String, MetricSummary> summary = generateSummary();

        // Calculate key metrics
        double avgLatency = getSummaryForMetric("latency_1_threads").average();
        double throughput = throughputMetrics.get("operations").get();
        double successRate = throughputMetrics.get("success").get() /
                            Math.max(throughput, 1);
        double errorRate = throughputMetrics.get("failures").get() /
                          Math.max(throughput, 1);

        // Calculate virtual thread efficiency
        long virtualOps = virtualThreadMetrics.get("total_created").get();
        long regularOps = throughputMetrics.get("operations").get();
        double virtualEfficiency = virtualOps > 0 ? (double) virtualOps / (virtualOps + regularOps) : 0;

        return new PerformanceInsights(
            agentName,
            avgLatency,
            throughput,
            successRate,
            errorRate,
            virtualEfficiency,
            getBottlenecks(summary)
        );
    }

    /**
     * Identify performance bottlenecks
     */
    private List<String> getBottlenecks(Map<String, MetricSummary> summary) {
        List<String> bottlenecks = new ArrayList<>();

        // High latency patterns
        summary.entrySet().stream()
            .filter(e -> e.getKey().startsWith("latency_") && e.getValue().p95() > 1000)
            .map(Map.Entry::getKey)
            .forEach(bottlenecks::add);

        // High error rates
        summary.entrySet().stream()
            .filter(e -> e.getKey().startsWith("error_") && e.getValue().average() > 0.05)
            .map(Map.Entry::getKey)
            .forEach(bottlenecks::add);

        // Memory issues
        summary.entrySet().stream()
            .filter(e -> e.getKey().startsWith("memory_") && e.getValue().average() > 1024 * 1024 * 10) // 10MB+
            .map(Map.Entry::getKey)
            .forEach(bottlenecks::add);

        return bottlenecks;
    }

    // Helper methods
    private double calculateAverage(List<Long> values) {
        if (values.isEmpty()) return 0;
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private double calculateP95(List<Long> values) {
        if (values.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compare);
        int p95Index = (int) (sorted.size() * 0.95);
        return sorted.get(p95Index);
    }

    /**
     * Performance insights summary
     */
    public static record PerformanceInsights(
        String agentName,
        double averageLatency,
        double throughput,
        double successRate,
        double errorRate,
        double virtualThreadEfficiency,
        List<String> bottlenecks
    ) {}

    /**
     * Individual metric summary
     */
    public static record MetricSummary(
        String metricKey,
        double average,
        double maximum,
        double minimum,
        double p95,
        long errorCount
    ) {}
}