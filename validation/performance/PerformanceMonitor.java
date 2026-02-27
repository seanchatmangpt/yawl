/*
 * YAWL v6.0.0-GA Validation
 * Performance Monitor
 *
 * Monitors and validates performance metrics during validation tests
 */
package org.yawlfoundation.yawl.performance;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.*;

/**
 * Performance monitor for validation tests
 * Tracks and validates performance metrics against targets
 */
public class PerformanceMonitor {

    private final Map<String, MetricTracker> metricTrackers = new ConcurrentHashMap<>();
    private final Map<String, AlertRule> alertRules = new ConcurrentHashMap<>();
    private final List<PerformanceAlert> alerts = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public PerformanceMonitor() {
        initializeDefaultAlertRules();
        startMonitoring();
    }

    /**
     * Record case processing time
     */
    public void recordCaseProcessing(long duration, int priority) {
        String metricKey = "case-processing-" + priority;
        MetricTracker tracker = metricTrackers.computeIfAbsent(
            metricKey, k -> new MetricTracker(metricKey));

        tracker.recordValue(duration, Instant.now());
        checkAlerts(metricKey, duration);
    }

    /**
     * Record queue latency
     */
    public void recordQueueLatency(long latency) {
        String metricKey = "queue-latency";
        MetricTracker tracker = metricTrackers.computeIfAbsent(
            metricKey, k -> new MetricTracker(metricKey));

        tracker.recordValue(latency, Instant.now());
        checkAlerts(metricKey, latency);
    }

    /**
     * Record error
     */
    public void recordError(String errorType, String context) {
        String metricKey = "errors-" + errorType;
        MetricTracker tracker = metricTrackers.computeIfAbsent(
            metricKey, k -> new MetricTracker(metricKey));

        tracker.recordError(Instant.now());
        checkAlerts(metricKey, 1); // Error count
    }

    /**
     * Record successful case completion
     */
    public void recordSuccessfulCase(String caseId) {
        String metricKey = "successful-cases";
        MetricTracker tracker = metricTrackers.computeIfAbsent(
            metricKey, k -> new MetricTracker(metricKey));

        tracker.recordValue(1, Instant.now()); // Count as 1
    }

    /**
     * Check for performance degradation
     */
    public void checkPerformanceDegradation() {
        Duration oneMinute = Duration.ofMinutes(1);
        Duration fiveMinutes = Duration.ofMinutes(5);
        Duration fifteenMinutes = Duration.ofMinutes(15);

        // Check CPU usage
        checkMetricTrend("cpu-usage", oneMinute, fiveMinutes, 10);

        // Check memory usage
        checkMetricTrend("memory-usage", oneMinute, fiveMinutes, 10);

        // Check queue length
        checkMetricTrend("queue-length", oneMinute, fiveMinutes, 20);

        // Check error rates
        checkMetricTrend("error-rate", oneMinute, fiveMinutes, 50);
    }

    /**
     * Validate performance targets
     */
    public boolean validateTargets(Map<String, PerformanceTarget> targets) {
        boolean allValid = true;

        for (Map.Entry<String, PerformanceTarget> entry : targets.entrySet()) {
            String metricKey = entry.getKey();
            PerformanceTarget target = entry.getValue();

            MetricTracker tracker = metricTrackers.get(metricKey);
            if (tracker == null) continue;

            if (!validateMetricAgainstTarget(tracker, target)) {
                allValid = false;
            }
        }

        return allValid;
    }

    /**
     * Generate performance report
     */
    public PerformanceReport generateReport(Instant startTime, Instant endTime) {
        PerformanceReport report = new PerformanceReport();
        report.setStartTime(startTime);
        report.setEndTime(endTime);

        // Calculate aggregate metrics
        Map<String, MetricSummary> summaries = new HashMap<>();
        for (MetricTracker tracker : metricTrackers.values()) {
            MetricSummary summary = tracker.calculateSummary();
            summaries.put(tracker.getMetricKey(), summary);
        }
        report.setMetricSummaries(summaries);

        // Include alerts
        report.setAlerts(new ArrayList<>(alerts));

        return report;
    }

    /**
     * Stop monitoring
     */
    public void stopMonitoring() {
        scheduler.shutdown();
    }

    // Helper methods

    private void initializeDefaultAlertRules() {
        // CPU usage alerts
        alertRules.put("cpu-usage", new AlertRule(
            "cpu-usage",
            "CPU usage exceeds threshold",
            value -> value > 80,
            AlertLevel.WARNING
        ));

        // Memory usage alerts
        alertRules.put("memory-usage", new AlertRule(
            "memory-usage",
            "Memory usage exceeds threshold",
            value -> value > 85,
            AlertLevel.CRITICAL
        ));

        // Queue latency alerts
        alertRules.put("queue-latency", new AlertRule(
            "queue-latency",
            "Queue latency exceeds threshold",
            value -> value > 1000,
            AlertLevel.WARNING
        ));

        // Error rate alerts
        alertRules.put("error-rate", new AlertRule(
            "error-rate",
            "Error rate exceeds threshold",
            value -> value > 1.0,
            AlertLevel.CRITICAL
        ));
    }

    private void startMonitoring() {
        // Start periodic monitoring
        scheduler.scheduleAtFixedRate(this::checkPerformanceDegradation,
            1, 1, TimeUnit.MINUTES);

        scheduler.scheduleAtFixedRate(this::cleanupOldMetrics,
            10, 10, TimeUnit.MINUTES);
    }

    private void checkAlerts(String metricKey, double value) {
        AlertRule rule = alertRules.get(metricKey);
        if (rule != null && rule.getCondition().test(value)) {
            PerformanceAlert alert = new PerformanceAlert(
                metricKey,
                rule.getMessage(),
                value,
                rule.getLevel(),
                Instant.now()
            );
            alerts.add(alert);
            System.err.println("ALERT: " + alert);
        }
    }

    private void checkMetricTrend(String metricKey, Duration shortTerm, Duration longTerm, int threshold) {
        MetricTracker tracker = metricTrackers.get(metricKey);
        if (tracker == null) return;

        double shortTermAvg = tracker.calculateAverage(shortTerm);
        double longTermAvg = tracker.calculateAverage(longTerm);

        double trend = ((shortTermAvg - longTermAvg) / longTermAvg) * 100;
        if (Math.abs(trend) > threshold) {
            System.out.printf("TREND ALERT: %s trending %.2f%% over threshold%n", metricKey, trend);
        }
    }

    private boolean validateMetricAgainstTarget(MetricTracker tracker, PerformanceTarget target) {
        MetricSummary summary = tracker.calculateSummary();
        double currentValue = summary.getAverage();

        switch (target.getComparisonOperator()) {
            case LESS_THAN:
                return currentValue <= target.getValue();
            case GREATER_THAN:
                return currentValue >= target.getValue();
            case LESS_THAN_OR_EQUAL:
                return currentValue <= target.getValue();
            case GREATER_THAN_OR_EQUAL:
                return currentValue >= target.getValue();
            default:
                return false;
        }
    }

    private void cleanupOldMetrics() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        metricTrackers.values().forEach(tracker -> tracker.cleanupOldData(cutoff));
        alerts.removeIf(alert -> alert.getTimestamp().isBefore(cutoff));
    }

    // Inner classes

    public static class MetricTracker {
        private final String metricKey;
        private final List<MetricSample> samples = new ArrayList<>();
        private final AtomicLong errorCount = new AtomicLong(0);

        public MetricTracker(String metricKey) {
            this.metricKey = metricKey;
        }

        public void recordValue(double value, Instant timestamp) {
            synchronized (samples) {
                samples.add(new MetricSample(value, timestamp));
                if (samples.size() > 10000) {
                    samples.remove(0); // Keep only last 10000 samples
                }
            }
        }

        public void recordError(Instant timestamp) {
            errorCount.incrementAndGet();
            synchronized (samples) {
                samples.add(new MetricSample(-1, timestamp)); // -1 represents error
            }
        }

        public MetricSummary calculateSummary() {
            synchronized (samples) {
                List<Double> values = samples.stream()
                    .filter(s -> s.getValue() >= 0)
                    .map(MetricSample::getValue)
                    .collect(Collectors.toList());

                if (values.isEmpty()) {
                    return new MetricSummary(metricKey, 0, 0, 0, 0, errorCount.get());
                }

                double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                double p95 = calculateP95(values);

                return new MetricSummary(metricKey, avg, max, min, p95, errorCount.get());
            }
        }

        public double calculateAverage(Duration timeWindow) {
            Instant cutoff = Instant.now().minus(timeWindow);
            synchronized (samples) {
                return samples.stream()
                    .filter(s -> s.getTimestamp().isAfter(cutoff) && s.getValue() >= 0)
                    .mapToDouble(MetricSample::getValue)
                    .average()
                    .orElse(0);
            }
        }

        public void cleanupOldData(Instant cutoff) {
            synchronized (samples) {
                samples.removeIf(s -> s.getTimestamp().isBefore(cutoff));
            }
        }

        private double calculateP95(List<Double> values) {
            if (values.isEmpty()) return 0;
            values.sort(Double::compare);
            int p95Index = (int) (values.size() * 0.95);
            return values.get(p95Index);
        }

        public String getMetricKey() { return metricKey; }
    }

    public static class MetricSample {
        private final double value;
        private final Instant timestamp;

        public MetricSample(double value, Instant timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        public double getValue() { return value; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class MetricSummary {
        private final String metricKey;
        private final double average;
        private final double maximum;
        private final double minimum;
        private final double p95;
        private final long errorCount;

        public MetricSummary(String metricKey, double average, double maximum,
                           double minimum, double p95, long errorCount) {
            this.metricKey = metricKey;
            this.average = average;
            this.maximum = maximum;
            this.minimum = minimum;
            this.p95 = p95;
            this.errorCount = errorCount;
        }

        public String getMetricKey() { return metricKey; }
        public double getAverage() { return average; }
        public double getMaximum() { return maximum; }
        public double getMinimum() { return minimum; }
        public double getP95() { return p95; }
        public long getErrorCount() { return errorCount; }
    }

    public static class PerformanceTarget {
        private final String metricKey;
        private final double value;
        private final ComparisonOperator comparisonOperator;

        public PerformanceTarget(String metricKey, double value, ComparisonOperator operator) {
            this.metricKey = metricKey;
            this.value = value;
            this.comparisonOperator = operator;
        }

        public String getMetricKey() { return metricKey; }
        public double getValue() { return value; }
        public ComparisonOperator getComparisonOperator() { return comparisonOperator; }
    }

    public enum ComparisonOperator {
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL
    }

    public static class AlertRule {
        private final String metricKey;
        private final String message;
        private final java.util.function.Predicate<Double> condition;
        private final AlertLevel level;

        public AlertRule(String metricKey, String message,
                        java.util.function.Predicate<Double> condition,
                        AlertLevel level) {
            this.metricKey = metricKey;
            this.message = message;
            this.condition = condition;
            this.level = level;
        }

        public String getMetricKey() { return metricKey; }
        public String getMessage() { return message; }
        public java.util.function.Predicate<Double> getCondition() { return condition; }
        public AlertLevel getLevel() { return level; }
    }

    public static class PerformanceAlert {
        private final String metricKey;
        private final String message;
        private final double value;
        private final AlertLevel level;
        private final Instant timestamp;

        public PerformanceAlert(String metricKey, String message, double value,
                              AlertLevel level, Instant timestamp) {
            this.metricKey = metricKey;
            this.message = message;
            this.value = value;
            this.level = level;
            this.timestamp = timestamp;
        }

        public String getMetricKey() { return metricKey; }
        public String getMessage() { return message; }
        public double getValue() { return value; }
        public AlertLevel getLevel() { return level; }
        public Instant getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("[%s] %s: %s (value: %.2f)", level, metricKey, message, value);
        }
    }

    public enum AlertLevel {
        INFO,
        WARNING,
        CRITICAL,
        FATAL
    }

    public static class PerformanceReport {
        private Instant startTime;
        private Instant endTime;
        private Map<String, MetricSummary> metricSummaries;
        private List<PerformanceAlert> alerts;

        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        public Map<String, MetricSummary> getMetricSummaries() { return metricSummaries; }
        public void setMetricSummaries(Map<String, MetricSummary> metricSummaries) { this.metricSummaries = metricSummaries; }
        public List<PerformanceAlert> getAlerts() { return alerts; }
        public void setAlerts(List<PerformanceAlert> alerts) { this.alerts = alerts; }

        public void saveToFile(String filename) {
            try {
                String reportContent = generateReportContent();
                Files.write(Paths.get(filename), reportContent.getBytes());
            } catch (IOException e) {
                System.err.println("Failed to save performance report: " + e.getMessage());
            }
        }

        private String generateReportContent() {
            StringBuilder content = new StringBuilder();
            content.append("YAWL v6.0.0-GA Performance Report\n");
            content.append("Generated: ").append(Instant.now()).append("\n\n");
            content.append("Test Period: ").append(startTime).append(" to ").append(endTime).append("\n\n");

            content.append("=== METRICS ===\n");
            for (MetricSummary summary : metricSummaries.values()) {
                content.append(String.format("%s:\n", summary.getMetricKey()));
                content.append(String.format("  Average: %.2f\n", summary.getAverage()));
                content.append(String.format("  Maximum: %.2f\n", summary.getMaximum()));
                content.append(String.format("  Minimum: %.2f\n", summary.getMinimum()));
                content.append(String.format("  P95: %.2f\n", summary.getP95()));
                content.append(String.format("  Errors: %d\n", summary.getErrorCount()));
            }

            content.append("\n=== ALERTS ===\n");
            for (PerformanceAlert alert : alerts) {
                content.append(String.format("[%s] %s: %s (value: %.2f)\n",
                    alert.getLevel(), alert.getMetricKey(), alert.getMessage(), alert.getValue()));
            }

            return content.toString();
        }
    }
}