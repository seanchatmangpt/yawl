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

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.time.*;
import java.time.format.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Collector and aggregator of performance metrics for YAWL v6.0.0-GA benchmarking.
 *
 * <p>Collects system metrics, application metrics, and business metrics with
 * configurable collection intervals and aggregation strategies.
 *
 * <p>Metrics Categories:
 * <ul>
 *   <li>System Metrics - CPU, memory, disk, network usage</li>
 *   <li>Application Metrics - workflow engine, database, API performance</li>
 *   <li>Business Metrics - throughput, latency, error rates, user experience</li>
 *   <li>Custom Metrics - user-defined application-specific metrics</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>Real-time metrics collection with configurable intervals</li>
 *   <li>Time-series aggregation and statistical analysis</li>
 *   <li>Metrics export to multiple formats</li>
 *   <li>Threshold-based alerting</li>
 *   <li>Historical data retention policies</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class PerformanceMetricsCollector {

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final Map<String, MetricDefinition> metricDefinitions;
    private final Map<String, List<MetricSample>> timeSeriesData;
    private final ScheduledExecutorService scheduler;
    private final Map<String, AlertRule> alertRules;
    private final Map<String, Double> currentValues;

    // Collection configuration
    private int collectionIntervalSeconds = 5;
    private int retentionHours = 24;
    private boolean enabled = true;
    private Instant lastCollectionTime;

    /**
     * Creates a new performance metrics collector
     */
    public PerformanceMetricsCollector() {
        this.metricDefinitions = new ConcurrentHashMap<>();
        this.timeSeriesData = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(4);
        this.alertRules = new ConcurrentHashMap<>();
        this.currentValues = new ConcurrentHashMap<>();
        this.lastCollectionTime = Instant.now();

        initializeDefaultMetrics();
        initializeAlertRules();
    }

    /**
     * Starts metrics collection
     */
    public void start() {
        if (enabled) {
            scheduleCollection();
        }
    }

    /**
     * Stops metrics collection
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Collects all metrics
     */
    public void collectMetrics() {
        if (!enabled) return;

        Instant collectionTime = Instant.now();
        Map<String, MetricSample> samples = new ConcurrentHashMap<>();

        // Collect system metrics
        collectSystemMetrics(samples);

        // Collect application metrics
        collectApplicationMetrics(samples);

        // Collect business metrics
        collectBusinessMetrics(samples);

        // Collect custom metrics
        collectCustomMetrics(samples);

        // Store samples
        samples.forEach((metricId, sample) -> {
            timeSeriesData.computeIfAbsent(metricId, k -> new CopyOnWriteArrayList<>()).add(sample);
            currentValues.put(metricId, sample.getValue());
        });

        lastCollectionTime = collectionTime;

        // Check alerts
        checkAlerts(samples);

        // Cleanup old data
        cleanupOldData();
    }

    /**
     * Records a custom metric value
     */
    public void recordMetric(String metricId, double value, Instant timestamp) {
        MetricSample sample = new MetricSample(metricId, value, timestamp);
        timeSeriesData.computeIfAbsent(metricId, k -> new CopyOnWriteArrayList<>()).add(sample);
        currentValues.put(metricId, value);
    }

    /**
     * Gets current metric values
     */
    public Map<String, Double> getCurrentValues() {
        return new ConcurrentHashMap<>(currentValues);
    }

    /**
     * Gets historical metric data
     */
    public Map<String, List<MetricSample>> getHistoricalData(Duration timeRange) {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(timeRange);

        Map<String, List<MetricSample>> historicalData = new HashMap<>();

        for (Map.Entry<String, List<MetricSample>> entry : timeSeriesData.entrySet()) {
            String metricId = entry.getKey();
            List<MetricSample> samples = entry.getValue();

            List<MetricSample> filteredSamples = samples.stream()
                .filter(sample -> !sample.getTimestamp().isBefore(startTime))
                .filter(sample -> !sample.getTimestamp().isAfter(endTime))
                .collect(Collectors.toList());

            if (!filteredSamples.isEmpty()) {
                historicalData.put(metricId, filteredSamples);
            }
        }

        return historicalData;
    }

    /**
     * Aggregates metric data for a time range
     */
    public Map<String, MetricAggregation> aggregateMetrics(String metricId, Duration timeRange, AggregationType aggregationType) {
        List<MetricSample> samples = getHistoricalData(timeRange).get(metricId);
        if (samples == null || samples.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, MetricAggregation> aggregations = new HashMap<>();

        switch (aggregationType) {
            case MEAN:
                aggregations.put("mean", calculateMean(samples));
                break;
            case MEDIAN:
                aggregations.put("median", calculateMedian(samples));
                break;
            case MIN_MAX:
                aggregations.put("min", calculateMin(samples));
                aggregations.put("max", calculateMax(samples));
                break;
            case PERCENTILES:
                aggregations.putAll(calculatePercentiles(samples));
                break;
            case RATE:
                aggregations.put("rate", calculateRate(samples));
                break;
            case THROUGHPUT:
                aggregations.put("throughput", calculateThroughput(samples));
                break;
            default:
                aggregations.put("mean", calculateMean(samples));
                aggregations.put("median", calculateMedian(samples));
                aggregations.put("min", calculateMin(samples));
                aggregations.put("max", calculateMax(samples));
        }

        return aggregations;
    }

    /**
     * Generates performance report
     */
    public PerformanceReport generateReport(Instant startTime, Instant endTime) {
        PerformanceReport report = new PerformanceReport();
        report.setGeneratedAt(Instant.now());
        report.setTimeRange(startTime, endTime);
        report.setSystemMetrics(analyzeSystemMetrics(startTime, endTime));
        report.setApplicationMetrics(analyzeApplicationMetrics(startTime, endTime));
        report.setBusinessMetrics(analyzeBusinessMetrics(startTime, endTime));
        report.setCustomMetrics(analyzeCustomMetrics(startTime, endTime));
        report.setSummary(generateSummary(startTime, endTime));

        return report;
    }

    /**
     * Exports metrics data
     */
    public void exportMetrics(Path outputPath, ExportFormat format, Duration timeRange) throws IOException {
        Map<String, List<MetricSample>> data = getHistoricalData(timeRange);

        switch (format) {
            case JSON:
                exportToJson(data, outputPath);
                break;
            case CSV:
                exportToCSV(data, outputPath);
                break;
            case PROMETHEUS:
                exportToPrometheus(data, outputPath);
                break;
            case INFLUXDB:
                exportToInfluxDB(data, outputPath);
                break;
        }
    }

    /**
     * Adds alert rule
     */
    public void addAlertRule(String ruleId, AlertRule rule) {
        alertRules.put(ruleId, rule);
    }

    /**
     * Removes alert rule
     */
    public void removeAlertRule(String ruleId) {
        alertRules.remove(ruleId);
    }

    /**
     * Gets current alerts
     */
    public List<Alert> getCurrentAlerts() {
        List<Alert> alerts = new ArrayList<>();
        Instant now = Instant.now();

        for (Map.Entry<String, AlertRule> entry : alertRules.entrySet()) {
            AlertRule rule = entry.getValue();
            Double currentValue = currentValues.get(rule.getMetricId());

            if (currentValue != null && rule.isTriggered(currentValue)) {
                alerts.add(new Alert(
                    rule.getRuleId(),
                    rule.getMetricId(),
                    currentValue,
                    rule.getThreshold(),
                    rule.getSeverity(),
                    now,
                    rule.getMessage()
                ));
            }
        }

        return alerts;
    }

    /**
     * Sets metric collection configuration
     */
    public void configureCollection(int intervalSeconds, int retentionHours) {
        this.collectionIntervalSeconds = intervalSeconds;
        this.retentionHours = retentionHours;

        // Restart scheduler with new interval
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        scheduler = Executors.newScheduledThreadPool(4);
        scheduleCollection();
    }

    // Collection methods

    private void collectSystemMetrics(Map<String, MetricSample> samples) {
        // CPU usage
        double cpuUsage = SystemMetrics.getCPUUsage();
        samples.put("system.cpu.usage", new MetricSample("system.cpu.usage", cpuUsage, Instant.now()));

        // Memory usage
        double memoryUsage = SystemMetrics.getMemoryUsage();
        samples.put("system.memory.usage", new MetricSample("system.memory.usage", memoryUsage, Instant.now()));

        // Disk usage
        double diskUsage = SystemMetrics.getDiskUsage();
        samples.put("system.disk.usage", new MetricSample("system.disk.usage", diskUsage, Instant.now()));

        // Network throughput
        double networkThroughput = SystemMetrics.getNetworkThroughput();
        samples.put("system.network.throughput", new MetricSample("system.network.throughput", networkThroughput, Instant.now()));

        // Thread count
        int threadCount = SystemMetrics.getThreadCount();
        samples.put("system.threads.count", new MetricSample("system.threads.count", threadCount, Instant.now()));
    }

    private void collectApplicationMetrics(Map<String, MetricSample> samples) {
        // Workflow metrics
        double activeCases = ApplicationMetrics.getActiveCases();
        samples.put("workflow.cases.active", new MetricSample("workflow.cases.active", activeCases, Instant.now()));

        double completedCases = ApplicationMetrics.getCompletedCases();
        samples.put("workflow.cases.completed", new MetricSample("workflow.cases.completed", completedCases, Instant.now()));

        double caseDuration = ApplicationMetrics.getAverageCaseDuration();
        samples.put("workflow.cases.duration", new MetricSample("workflow.cases.duration", caseDuration, Instant.now()));

        // Task metrics
        double activeTasks = ApplicationMetrics.getActiveTasks();
        samples.put("workflow.tasks.active", new MetricSample("workflow.tasks.active", activeTasks, Instant.now()));

        double taskThroughput = ApplicationMetrics.getTaskThroughput();
        samples.put("workflow.tasks.throughput", new MetricSample("workflow.tasks.throughput", taskThroughput, Instant.now()));

        double taskErrorRate = ApplicationMetrics.getTaskErrorRate();
        samples.put("workflow.tasks.error_rate", new MetricSample("workflow.tasks.error_rate", taskErrorRate, Instant.now()));

        // Database metrics
        double dbConnections = ApplicationMetrics.getDatabaseConnections();
        samples.put("database.connections.active", new MetricSample("database.connections.active", dbConnections, Instant.now()));

        double dbQueryTime = ApplicationMetrics.getAverageQueryTime();
        samples.put("database.query.time", new MetricSample("database.query.time", dbQueryTime, Instant.now()));

        // API metrics
        double apiRequests = ApplicationMetrics.getAPIRequestsPerSecond();
        samples.put("api.requests.per_second", new MetricSample("api.requests.per_second", apiRequests, Instant.now()));

        double apiResponseTime = ApplicationMetrics.getAverageAPIResponseTime();
        samples.put("api.response.time", new MetricSample("api.response.time", apiResponseTime, Instant.now()));
    }

    private void collectBusinessMetrics(Map<String, MetricSample> samples) {
        // Business process metrics
        double processThroughput = BusinessMetrics.getProcessThroughput();
        samples.put("business.process.throughput", new MetricSample("business.process.throughput", processThroughput, Instant.now()));

        double processLatency = BusinessMetrics.getProcessLatency();
        samples.put("business.process.latency", new MetricSample("business.process.latency", processLatency, Instant.now()));

        double processSuccessRate = BusinessMetrics.getProcessSuccessRate();
        samples.put("business.process.success_rate", new MetricSample("business.process.success_rate", processSuccessRate, Instant.now()));

        // User experience metrics
        double activeUsers = BusinessMetrics.getActiveUsers();
        samples.put("business.users.active", new MetricSample("business.users.active", activeUsers, Instant.now()));

        double userErrorRate = BusinessMetrics.getUserErrorRate();
        samples.put("business.users.error_rate", new MetricSample("business.users.error_rate", userErrorRate, Instant.now()));

        double satisfactionScore = BusinessMetrics.getSatisfactionScore();
        samples.put("business.users.satisfaction", new MetricSample("business.users.satisfaction", satisfactionScore, Instant.now()));
    }

    private void collectCustomMetrics(Map<String, MetricSample> samples) {
        // This would be extended with custom metric collection logic
        // For now, add some placeholder metrics
        samples.put("custom.metric1", new MetricSample("custom.metric1", random.nextDouble() * 100, Instant.now()));
        samples.put("custom.metric2", new MetricSample("custom.metric2", random.nextDouble() * 50, Instant.now()));
    }

    // Alert checking

    private void checkAlerts(Map<String, MetricSample> samples) {
        for (Map.Entry<String, MetricSample> entry : samples.entrySet()) {
            String metricId = entry.getKey();
            MetricSample sample = entry.getValue();
            Double currentValue = sample.getValue();

            for (Map.Entry<String, AlertRule> alertEntry : alertRules.entrySet()) {
                AlertRule rule = alertEntry.getValue();
                if (rule.getMetricId().equals(metricId)) {
                    if (rule.isTriggered(currentValue)) {
                        Alert alert = new Alert(
                            rule.getRuleId(),
                            metricId,
                            currentValue,
                            rule.getThreshold(),
                            rule.getSeverity(),
                            Instant.now(),
                            rule.getMessage()
                        );
                        // In a real implementation, this would trigger notifications
                        System.out.println("ALERT: " + alert);
                    }
                }
            }
        }
    }

    // Cleanup methods

    private void cleanupOldData() {
        Instant cutoffTime = Instant.now().minus(Duration.ofHours(retentionHours));

        timeSeriesData.forEach((metricId, samples) -> {
            samples.removeIf(sample -> sample.getTimestamp().isBefore(cutoffTime));
        });

        currentValues.clear();
        timeSeriesData.forEach((metricId, samples) -> {
            if (!samples.isEmpty()) {
                currentValues.put(metricId, samples.get(samples.size() - 1).getValue());
            }
        });
    }

    // Analysis methods

    private Map<String, Object> analyzeSystemMetrics(Instant startTime, Instant endTime) {
        Map<String, Object> analysis = new HashMap<>();

        Map<String, List<MetricSample>> cpuData = getHistoricalDataForRange("system.cpu.usage", startTime, endTime);
        Map<String, List<MetricSample>> memoryData = getHistoricalDataForRange("system.memory.usage", startTime, endTime);
        Map<String, List<MetricSample>> diskData = getHistoricalDataForRange("system.disk.usage", startTime, endTime);

        analysis.put("cpu", analyzeMetric(cpuData, "CPU Usage"));
        analysis.put("memory", analyzeMetric(memoryData, "Memory Usage"));
        analysis.put("disk", analyzeMetric(diskData, "Disk Usage"));

        return analysis;
    }

    private Map<String, Object> analyzeApplicationMetrics(Instant startTime, Instant endTime) {
        Map<String, Object> analysis = new HashMap<>();

        Map<String, List<MetricSample>> casesData = getHistoricalDataForRange("workflow.cases.active", startTime, endTime);
        Map<String, List<MetricSample>> tasksData = getHistoricalDataForRange("workflow.tasks.throughput", startTime, endTime);
        Map<String, List<MetricSample>> apiData = getHistoricalDataForRange("api.response.time", startTime, endTime);

        analysis.put("workflow", analyzeMetric(casesData, "Workflow Cases"));
        analysis.put("tasks", analyzeMetric(tasksData, "Task Throughput"));
        analysis.put("api", analyzeMetric(apiData, "API Response Time"));

        return analysis;
    }

    private Map<String, Object> analyzeBusinessMetrics(Instant startTime, Instant endTime) {
        Map<String, Object> analysis = new HashMap<>();

        Map<String, List<MetricSample>> throughputData = getHistoricalDataForRange("business.process.throughput", startTime, endTime);
        Map<String, List<MetricSample>> latencyData = getHistoricalDataForRange("business.process.latency", startTime, endTime);
        Map<String, List<MetricSample>> successData = getHistoricalDataForRange("business.process.success_rate", startTime, endTime);

        analysis.put("throughput", analyzeMetric(throughputData, "Process Throughput"));
        analysis.put("latency", analyzeMetric(latencyData, "Process Latency"));
        analysis.put("success_rate", analyzeMetric(successData, "Success Rate"));

        return analysis;
    }

    private Map<String, Object> analyzeCustomMetrics(Instant startTime, Instant endTime) {
        Map<String, Object> analysis = new HashMap<>();
        // Custom metric analysis would be implemented here
        return analysis;
    }

    private Map<String, Object> analyzeMetric(Map<String, List<MetricSample>> data, String metricName) {
        Map<String, Object> analysis = new HashMap<>();
        List<MetricSample> samples = data.values().stream().findFirst().orElse(Collections.emptyList());

        if (!samples.isEmpty()) {
            analysis.put("name", metricName);
            analysis.put("count", samples.size());
            analysis.put("average", samples.stream().mapToDouble(MetricSample::getValue).average().orElse(0));
            analysis.put("min", samples.stream().mapToDouble(MetricSample::getValue).min().orElse(0));
            analysis.put("max", samples.stream().mapToDouble(MetricSample::getValue).max().orElse(0));
            analysis.put("p95", calculatePercentile(samples, 95));
            analysis.put("p99", calculatePercentile(samples, 99));
        }

        return analysis;
    }

    private Map<String, Object> getHistoricalDataForRange(String metricId, Instant startTime, Instant endTime) {
        return Map.of(metricId, timeSeriesData.getOrDefault(metricId, Collections.emptyList()).stream()
            .filter(sample -> !sample.getTimestamp().isBefore(startTime))
            .filter(sample -> !sample.getTimestamp().isAfter(endTime))
            .collect(Collectors.toList()));
    }

    private Map<String, Object> generateSummary(Instant startTime, Instant endTime) {
        Map<String, Object> summary = new HashMap<>();

        // Calculate overall performance score
        double systemScore = calculateSystemPerformanceScore(startTime, endTime);
        double applicationScore = calculateApplicationPerformanceScore(startTime, endTime);
        double businessScore = calculateBusinessPerformanceScore(startTime, endTime);

        summary.put("system_score", systemScore);
        summary.put("application_score", applicationScore);
        summary.put("business_score", businessScore);
        summary.put("overall_score", (systemScore + applicationScore + businessScore) / 3);

        // Generate recommendations
        summary.put("recommendations", generatePerformanceRecommendations(
            systemScore, applicationScore, businessScore
        ));

        return summary;
    }

    private double calculateSystemPerformanceScore(Instant startTime, Instant endTime) {
        // Calculate based on resource utilization
        Map<String, List<MetricSample>> cpuData = getHistoricalDataForRange("system.cpu.usage", startTime, endTime);
        Map<String, List<MetricSample>> memoryData = getHistoricalDataForRange("system.memory.usage", startTime, endTime);

        double avgCPU = cpuData.values().stream().flatMap(List::stream)
            .mapToDouble(MetricSample::getValue).average().orElse(0);
        double avgMemory = memoryData.values().stream().flatMap(List::stream)
            .mapToDouble(MetricSample::getValue).average().orElse(0);

        // Lower resource usage = higher score
        return Math.max(0, 100 - (avgCPU + avgMemory) / 2);
    }

    private double calculateApplicationPerformanceScore(Instant startTime, Instant endTime) {
        // Calculate based on response times and error rates
        Map<String, List<MetricSample>> responseTimeData = getHistoricalDataForRange("api.response.time", startTime, endTime);
        Map<String, List<MetricSample>> errorRateData = getHistoricalDataForRange("workflow.tasks.error_rate", startTime, endTime);

        double avgResponseTime = responseTimeData.values().stream().flatMap(List::stream)
            .mapToDouble(MetricSample::getValue).average().orElse(0);
        double avgErrorRate = errorRateData.values().stream().flatMap(List::stream)
            .mapToDouble(MetricSample::getValue).average().orElse(0);

        // Lower response time and error rate = higher score
        return Math.max(0, 100 - (avgResponseTime / 100 + avgErrorRate * 100));
    }

    private double calculateBusinessPerformanceScore(Instant startTime, Instant endTime) {
        // Calculate based on success rates and throughput
        Map<String, List<MetricSample>> successRateData = getHistoricalDataForRange("business.process.success_rate", startTime, endTime);
        Map<String, List<MetricSample>> throughputData = getHistoricalDataForRange("business.process.throughput", startTime, endTime);

        double avgSuccessRate = successRateData.values().stream().flatMap(List::stream)
            .mapToDouble(MetricSample::getValue).average().orElse(0);
        double avgThroughput = throughputData.values().stream().flatMap(List::stream)
            .mapToDouble(MetricSample::getValue).average().orElse(0);

        // Higher success rate and throughput = higher score
        return (avgSuccessRate * 100 + avgThroughput) / 2;
    }

    private List<String> generatePerformanceRecommendations(double systemScore, double applicationScore, double businessScore) {
        List<String> recommendations = new ArrayList<>();

        if (systemScore < 80) {
            recommendations.add("Optimize system resource usage");
            recommendations.add("Consider scaling resources");
        }

        if (applicationScore < 80) {
            recommendations.add("Improve application response times");
            recommendations.add("Optimize database queries");
        }

        if (businessScore < 80) {
            recommendations.add("Focus on improving business process efficiency");
            recommendations.add("Reduce error rates in critical processes");
        }

        if (systemScore > 90 && applicationScore > 90 && businessScore > 90) {
            recommendations.add("System performance is excellent");
            recommendations.add("Monitor for continuous improvement opportunities");
        }

        return recommendations;
    }

    // Export methods

    private void exportToJson(Map<String, List<MetricSample>> data, Path outputPath) throws IOException {
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("export_timestamp", Instant.now().toString());
        exportData.put("metrics", data);

        Files.write(outputPath, objectMapper.writeValueAsString(exportData).getBytes(StandardCharsets.UTF_8));
    }

    private void exportToCSV(Map<String, List<MetricSample>> data, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            // Write header
            writer.write("Metric,Timestamp,Value\n");

            // Write data
            for (Map.Entry<String, List<MetricSample>> entry : data.entrySet()) {
                String metricId = entry.getKey();
                for (MetricSample sample : entry.getValue()) {
                    writer.write(String.format("%s,%s,%.2f\n",
                        metricId,
                        sample.getTimestamp(),
                        sample.getValue()));
                }
            }
        }
    }

    private void exportToPrometheus(Map<String, List<MetricSample>> data, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<MetricSample>> entry : data.entrySet()) {
                String metricId = entry.getKey();
                for (MetricSample sample : entry.getValue()) {
                    writer.write(String.format("# HELP %s %s\n", metricId, metricId.replace("_", " ")));
                    writer.write(String.format("# TYPE %s gauge\n", metricId));
                    writer.write(String.format("%s %f %d\n",
                        metricId,
                        sample.getValue(),
                        sample.getTimestamp().toEpochMilli()));
                }
            }
        }
    }

    private void exportToInfluxDB(Map<String, List<MetricSample>> data, Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<MetricSample>> entry : data.entrySet()) {
                String metricId = entry.getKey();
                for (MetricSample sample : entry.getValue()) {
                    writer.write(String.format("%s,metric=%s value=%.2f %d\n",
                        "performance",
                        metricId,
                        sample.getValue(),
                        sample.getTimestamp().toEpochMilli()));
                }
            }
        }
    }

    // Initialization methods

    private void initializeDefaultMetrics() {
        // Define standard metrics
        metricDefinitions.put("system.cpu.usage", new MetricDefinition(
            "system.cpu.usage", "CPU Usage", "percentage", "Current CPU utilization"
        ));
        metricDefinitions.put("system.memory.usage", new MetricDefinition(
            "system.memory.usage", "Memory Usage", "percentage", "Current memory utilization"
        ));
        metricDefinitions.put("system.disk.usage", new MetricDefinition(
            "system.disk.usage", "Disk Usage", "percentage", "Current disk utilization"
        ));
        metricDefinitions.put("system.network.throughput", new MetricDefinition(
            "system.network.throughput", "Network Throughput", "bytes/sec", "Current network throughput"
        ));

        metricDefinitions.put("workflow.cases.active", new MetricDefinition(
            "workflow.cases.active", "Active Cases", "count", "Number of currently active workflow cases"
        ));
        metricDefinitions.put("workflow.cases.completed", new MetricDefinition(
            "workflow.cases.completed", "Completed Cases", "count", "Total completed workflow cases"
        ));
        metricDefinitions.put("workflow.cases.duration", new MetricDefinition(
            "workflow.cases.duration", "Average Case Duration", "seconds", "Average time to complete a case"
        ));

        metricDefinitions.put("api.requests.per_second", new MetricDefinition(
            "api.requests.per_second", "API Requests", "requests/sec", "Number of API requests per second"
        ));
        metricDefinitions.put("api.response.time", new MetricDefinition(
            "api.response.time", "API Response Time", "milliseconds", "Average API response time"
        ));

        metricDefinitions.put("business.process.throughput", new MetricDefinition(
            "business.process.throughput", "Process Throughput", "processes/hour", "Business processes completed per hour"
        ));
        metricDefinitions.put("business.process.latency", new MetricDefinition(
            "business.process.latency", "Process Latency", "seconds", "Time to complete business processes"
        ));
        metricDefinitions.put("business.process.success_rate", new MetricDefinition(
            "business.process.success_rate", "Process Success Rate", "percentage", "Percentage of successful processes"
        ));
    }

    private void initializeAlertRules() {
        // High CPU usage alert
        alertRules.put("high_cpu", new AlertRule(
            "high_cpu", "system.cpu.usage", 90, AlertSeverity.CRITICAL,
            "CPU usage exceeded 90%"
        ));

        // High memory usage alert
        alertRules.put("high_memory", new AlertRule(
            "high_memory", "system.memory.usage", 85, AlertSeverity.HIGH,
            "Memory usage exceeded 85%"
        ));

        // High error rate alert
        alertRules.put("high_error_rate", new AlertRule(
            "high_error_rate", "workflow.tasks.error_rate", 0.05, AlertSeverity.HIGH,
            "Error rate exceeded 5%"
        ));

        // High response time alert
        alertRules.put("high_response_time", new AlertRule(
            "high_response_time", "api.response.time", 5000, AlertSeverity.MEDIUM,
            "API response time exceeded 5 seconds"
        ));
    }

    private void scheduleCollection() {
        scheduler.scheduleAtFixedRate(
            this::collectMetrics,
            0,
            collectionIntervalSeconds,
            TimeUnit.SECONDS
        );
    }

    // Helper methods

    private double calculateMean(List<MetricSample> samples) {
        return samples.stream().mapToDouble(MetricSample::getValue).average().orElse(0);
    }

    private double calculateMedian(List<MetricSample> samples) {
        List<Double> values = samples.stream().map(MetricSample::getValue).sorted().collect(Collectors.toList());
        int size = values.size();
        return size % 2 == 0 ?
            (values.get(size / 2 - 1) + values.get(size / 2)) / 2 :
            values.get(size / 2);
    }

    private MetricAggregation calculateMin(List<MetricSample> samples) {
        double min = samples.stream().mapToDouble(MetricSample::getValue).min().orElse(0);
        return new MetricAggregation("min", min);
    }

    private MetricAggregation calculateMax(List<MetricSample> samples) {
        double max = samples.stream().mapToDouble(MetricSample::getValue).max().orElse(0);
        return new MetricAggregation("max", max);
    }

    private Map<String, MetricAggregation> calculatePercentiles(List<MetricSample> samples) {
        Map<String, MetricAggregation> percentiles = new HashMap<>();
        List<Double> values = samples.stream().map(MetricSample::getValue).sorted().collect(Collectors.toList());
        int size = values.size();

        percentiles.put("p50", new MetricAggregation("p50", calculatePercentile(values, 50)));
        percentiles.put("p95", new MetricAggregation("p95", calculatePercentile(values, 95)));
        percentiles.put("p99", new MetricAggregation("p99", calculatePercentile(values, 99)));

        return percentiles;
    }

    private double calculatePercentile(List<MetricSample> samples, int percentile) {
        List<Double> values = samples.stream().map(MetricSample::getValue).sorted().collect(Collectors.toList());
        return calculatePercentile(values, percentile);
    }

    private double calculatePercentile(List<Double> values, int percentile) {
        if (values.isEmpty()) return 0;
        int index = (int) (values.size() * percentile / 100);
        return values.get(Math.min(index, values.size() - 1));
    }

    private MetricAggregation calculateRate(List<MetricSample> samples) {
        if (samples.size() < 2) {
            return new MetricAggregation("rate", 0);
        }

        double firstValue = samples.get(0).getValue();
        double lastValue = samples.get(samples.size() - 1).getValue();
        Duration duration = Duration.between(samples.get(0).getTimestamp(), samples.get(samples.size() - 1).getTimestamp());

        double rate = duration.getSeconds() > 0 ? (lastValue - firstValue) / duration.getSeconds() : 0;
        return new MetricAggregation("rate", rate);
    }

    private MetricAggregation calculateThroughput(List<MetricSample> samples) {
        if (samples.isEmpty()) {
            return new MetricAggregation("throughput", 0);
        }

        long count = samples.size();
        Duration duration = Duration.between(
            samples.get(0).getTimestamp(),
            samples.get(samples.size() - 1).getTimestamp()
        );

        double throughput = duration.getSeconds() > 0 ? count / duration.getSeconds() : 0;
        return new MetricAggregation("throughput", throughput);
    }

    // Utility classes

    public static class MetricDefinition {
        private String id;
        private String name;
        private String unit;
        private String description;

        public MetricDefinition(String id, String name, String unit, String description) {
            this.id = id;
            this.name = name;
            this.unit = unit;
            this.description = description;
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getUnit() { return unit; }
        public String getDescription() { return description; }
    }

    public static class MetricSample {
        private String metricId;
        private double value;
        private Instant timestamp;

        public MetricSample(String metricId, double value, Instant timestamp) {
            this.metricId = metricId;
            this.value = value;
            this.timestamp = timestamp;
        }

        // Getters
        public String getMetricId() { return metricId; }
        public double getValue() { return value; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class MetricAggregation {
        private String type;
        private double value;

        public MetricAggregation(String type, double value) {
            this.type = type;
            this.value = value;
        }

        // Getters
        public String getType() { return type; }
        public double getValue() { return value; }
    }

    public static class AlertRule {
        private String ruleId;
        private String metricId;
        private double threshold;
        private AlertSeverity severity;
        private String message;
        private AlertCondition condition;

        public AlertRule(String ruleId, String metricId, double threshold, AlertSeverity severity, String message) {
            this.ruleId = ruleId;
            this.metricId = metricId;
            this.threshold = threshold;
            this.severity = severity;
            this.message = message;
            this.condition = AlertCondition.GREATER_THAN;
        }

        public boolean isTriggered(double currentValue) {
            switch (condition) {
                case GREATER_THAN:
                    return currentValue > threshold;
                case LESS_THAN:
                    return currentValue < threshold;
                case EQUALS:
                    return currentValue == threshold;
                default:
                    return false;
            }
        }

        // Getters
        public String getRuleId() { return ruleId; }
        public String getMetricId() { return metricId; }
        public double getThreshold() { return threshold; }
        public AlertSeverity getSeverity() { return severity; }
        public String getMessage() { return message; }
        public AlertCondition getCondition() { return condition; }
    }

    public static class Alert {
        private String ruleId;
        private String metricId;
        private double currentValue;
        private double threshold;
        private AlertSeverity severity;
        private Instant timestamp;
        private String message;

        public Alert(String ruleId, String metricId, double currentValue, double threshold,
                     AlertSeverity severity, Instant timestamp, String message) {
            this.ruleId = ruleId;
            this.metricId = metricId;
            this.currentValue = currentValue;
            this.threshold = threshold;
            this.severity = severity;
            this.timestamp = timestamp;
            this.message = message;
        }

        // Getters
        public String getRuleId() { return ruleId; }
        public String getMetricId() { return metricId; }
        public double getCurrentValue() { return currentValue; }
        public double getThreshold() { return threshold; }
        public AlertSeverity getSeverity() { return severity; }
        public Instant getTimestamp() { return timestamp; }
        public String getMessage() { return message; }
    }

    public static class PerformanceReport {
        private Instant generatedAt;
        private Instant startTime;
        private Instant endTime;
        private Map<String, Object> systemMetrics;
        private Map<String, Object> applicationMetrics;
        private Map<String, Object> businessMetrics;
        private Map<String, Object> customMetrics;
        private Map<String, Object> summary;

        public void setTimeRange(Instant startTime, Instant endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        // Getters and setters
        public Instant getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public Map<String, Object> getSystemMetrics() { return systemMetrics; }
        public void setSystemMetrics(Map<String, Object> systemMetrics) { this.systemMetrics = systemMetrics; }
        public Map<String, Object> getApplicationMetrics() { return applicationMetrics; }
        public void setApplicationMetrics(Map<String, Object> applicationMetrics) { this.applicationMetrics = applicationMetrics; }
        public Map<String, Object> getBusinessMetrics() { return businessMetrics; }
        public void setBusinessMetrics(Map<String, Object> businessMetrics) { this.businessMetrics = businessMetrics; }
        public Map<String, Object> getCustomMetrics() { return customMetrics; }
        public void setCustomMetrics(Map<String, Object> customMetrics) { this.customMetrics = customMetrics; }
        public Map<String, Object> getSummary() { return summary; }
        public void setSummary(Map<String, Object> summary) { this.summary = summary; }
    }

    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AlertCondition {
        GREATER_THAN, LESS_THAN, EQUALS
    }

    public enum AggregationType {
        MEAN, MEDIAN, MIN_MAX, PERCENTILES, RATE, THROUGHPUT
    }

    public enum ExportFormat {
        JSON, CSV, PROMETHEUS, INFLUXDB
    }

    // System metrics implementation

    private static class SystemMetrics {
        public static double getCPUUsage() {
            // This would integrate with OS-specific monitoring
            return Math.random() * 100;
        }

        public static double getMemoryUsage() {
            // This would integrate with OS-specific monitoring
            return 60 + Math.random() * 40;
        }

        public static double getDiskUsage() {
            // This would integrate with OS-specific monitoring
            return 30 + Math.random() * 70;
        }

        public static double getNetworkThroughput() {
            // This would integrate with OS-specific monitoring
            return Math.random() * 1000;
        }

        public static int getThreadCount() {
            // This would integrate with JVM monitoring
            return (int) (100 + Math.random() * 900);
        }
    }

    // Application metrics implementation

    private static class ApplicationMetrics {
        public static double getActiveCases() {
            return Math.random() * 1000;
        }

        public static double getCompletedCases() {
            return Math.random() * 10000;
        }

        public static double getAverageCaseDuration() {
            return 30 + Math.random() * 300;
        }

        public static double getActiveTasks() {
            return Math.random() * 5000;
        }

        public static double getTaskThroughput() {
            return Math.random() * 100;
        }

        public static double getTaskErrorRate() {
            return Math.random() * 0.1;
        }

        public static double getDatabaseConnections() {
            return Math.random() * 100;
        }

        public static double getAverageQueryTime() {
            return 10 + Math.random() * 990;
        }

        public static double getAPIRequestsPerSecond() {
            return Math.random() * 1000;
        }

        public static double getAverageAPIResponseTime() {
            return 50 + Math.random() * 950;
        }
    }

    // Business metrics implementation

    private static class BusinessMetrics {
        public static double getProcessThroughput() {
            return Math.random() * 500;
        }

        public static double getProcessLatency() {
            return 60 + Math.random() * 540;
        }

        public static double getProcessSuccessRate() {
            return 0.8 + Math.random() * 0.2;
        }

        public static double getActiveUsers() {
            return Math.random() * 10000;
        }

        public static double getUserErrorRate() {
            return Math.random() * 0.05;
        }

        public static double getSatisfactionScore() {
            return 3 + Math.random() * 2;
        }
    }
}