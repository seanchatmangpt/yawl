package org.yawlfoundation.yawl.observability.actor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Dashboard data provider for YAWL actor monitoring.
 *
 * Provides real-time and historical data for actor monitoring dashboards:
 * - Real-time actor health metrics
 * - Performance charts and graphs
 * - Alert status and history
 * - Resource utilization metrics
 * - Message flow visualization
 * - System health overview
 *
 * Supports multiple dashboard types: operational, performance, health.
 */
public class ActorDashboardData {

    private final ActorHealthMetrics healthMetrics;
    private final ActorAlertManager alertManager;
    private final ConcurrentHashMap<String, MetricTimeSeries> timeSeriesData;
    private final DateTimeFormatter dateTimeFormatter;

    /**
     * Creates a new ActorDashboardData instance.
     */
    public ActorDashboardData(ActorHealthMetrics healthMetrics, ActorAlertManager alertManager) {
        this.healthMetrics = healthMetrics;
        this.alertManager = alertManager;
        this.timeSeriesData = new ConcurrentHashMap<>();
        this.dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // Initialize time series data stores
        initializeTimeSeries();
    }

    /**
     * Initializes time series data stores.
     */
    private void initializeTimeSeries() {
        // Actor count time series
        timeSeriesData.put("actor_count", new MetricTimeSeries("actor_count", 3600)); // 1 hour data

        // Queue depth time series
        timeSeriesData.put("queue_depth", new MetricTimeSeries("queue_depth", 3600));

        // Memory usage time series
        timeSeriesData.put("memory_usage", new MetricTimeSeries("memory_usage", 3600));

        // Message processing rate time series
        timeSeriesData.put("message_rate", new MetricTimeSeries("message_rate", 3600));

        // Error rate time series
        timeSeriesData.put("error_rate", new MetricTimeSeries("error_rate", 3600));

        // System health score time series
        timeSeriesData.put("health_score", new MetricTimeSeries("health_score", 3600));
    }

    /**
     * Records a metric value for time series tracking.
     */
    public void recordMetric(String metricName, double value) {
        MetricTimeSeries timeSeries = timeSeriesData.get(metricName);
        if (timeSeries != null) {
            timeSeries.addPoint(value);
        }
    }

    /**
     * Gets dashboard overview data.
     */
    public DashboardOverview getDashboardOverview() {
        ActorHealthMetrics.ActorHealthSummary healthSummary = healthMetrics.getHealthSummary();
        ActorAlertManager.AlertStatistics alertStats = alertManager.getAlertStatistics();

        return new DashboardOverview(
                LocalDateTime.now(),
                healthSummary,
                alertStats,
                calculateSystemLoad(),
                getActiveActorsByType(),
                getRecentAlerts(10),
                getTopSlowActors(5)
        );
    }

    /**
     * Gets actor health dashboard data.
     */
    public ActorHealthDashboard getActorHealthDashboard() {
        ActorHealthMetrics.ActorHealthSummary summary = healthMetrics.getHealthSummary();
        List<ActorHealthMetrics.ActorHealthStatus> allActors = healthMetrics.getAllActiveActors();

        // Group actors by health status
        Map<ActorHealthMetrics.HealthStatus, List<ActorHealthMetrics.ActorHealthStatus>> actorsByHealth =
                allActors.stream()
                        .collect(Collectors.groupingBy(ActorHealthMetrics.ActorHealthStatus::getHealthStatus));

        return new ActorHealthDashboard(
                summary,
                actorsByHealth.getOrDefault(ActorHealthMetrics.HealthStatus.HEALTHY, Collections.emptyList()),
                actorsByHealth.getOrDefault(ActorHealthMetrics.HealthStatus.WARNING, Collections.emptyList()),
                actorsByHealth.getOrDefault(ActorHealthMetrics.HealthStatus.UNHEALTHY, Collections.emptyList()),
                actorsByHealth.getOrDefault(ActorHealthMetrics.HealthStatus.UNKNOWN, Collections.emptyList()),
                getHealthTrendData()
        );
    }

    /**
     * Gets performance dashboard data.
     */
    public PerformanceDashboard getPerformanceDashboard() {
        return new PerformanceDashboard(
                getPerformanceMetrics(),
                getThroughputChart(),
                getLatencyChart(),
                getQueueDepthChart(),
                getCpuUsageChart(),
                getMemoryUsageChart()
        );
    }

    /**
     * Gets alert dashboard data.
     */
    public AlertDashboard getAlertDashboard() {
        ActorAlertManager.AlertStatistics stats = alertManager.getAlertStatistics();
        List<ActorAlertManager.AlertHistory> recentAlerts = getRecentAlerts(50);

        return new AlertDashboard(
                stats,
                recentAlerts,
                getAlertTrendData(),
                getAlertDistributionByType(),
                getAlertDistributionBySeverity()
        );
    }

    /**
     * Gets real-time metrics.
     */
    public Map<String, Object> getRealTimeMetrics() {
        ActorHealthMetrics.ActorHealthSummary summary = healthMetrics.getHealthSummary();
        ActorAlertManager.AlertStatistics alertStats = alertManager.getAlertStatistics();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("timestamp", LocalDateTime.now().format(dateTimeFormatter));
        metrics.put("activeActorCount", summary.getActiveActorCount());
        metrics.put("unhealthyActorCount", summary.getUnhealthyActorCount());
        metrics.put("suspendedActorCount", summary.getSuspendedActorCount());
        metrics.put("totalQueueDepth", summary.getTotalQueueDepth());
        metrics.put("totalMemoryBytes", summary.getTotalMemoryBytes());
        metrics.put("systemHealthScore", summary.getSystemHealthScore());
        metrics.put("activeAlertCount", alertStats.getActiveAlertCount());
        metrics.put("criticalAlertCount", alertStats.getSeverityCounts().getOrDefault(ActorAlertManager.AlertSeverity.CRITICAL, 0));
        metrics.put("warningAlertCount", alertStats.getSeverityCounts().getOrDefault(ActorAlertManager.AlertSeverity.WARNING, 0));
        metrics.put("infoAlertCount", alertStats.getSeverityCounts().getOrDefault(ActorAlertManager.AlertSeverity.INFO, 0));

        return metrics;
    }

    /**
     * Gets time series data for a metric.
     */
    public TimeSeriesData getTimeSeriesData(String metricName, int points) {
        MetricTimeSeries timeSeries = timeSeriesData.get(metricName);
        if (timeSeries != null) {
            return timeSeries.getLastPoints(points);
        }
        return new TimeSeriesData(metricName, Collections.emptyList());
    }

    /**
     * Gets dashboard configuration.
     */
    public DashboardConfiguration getDashboardConfiguration() {
        return new DashboardConfiguration(
                List.of("overview", "health", "performance", "alerts", "details"),
                Map.of(
                        "refreshInterval", 5000,
                        "timeRange", 3600000, // 1 hour
                        "maxPoints", 100
                ),
                List.of(
                        new DashboardWidget("systemHealth", "System Health", "gauge", 12, 4),
                        new DashboardWidget("actorCount", "Active Actors", "gauge", 6, 4),
                        new DashboardWidget("queueDepth", "Queue Depth", "lineChart", 12, 6),
                        new DashboardWidget("memoryUsage", "Memory Usage", "areaChart", 6, 6),
                        new DashboardWidget("throughput", "Throughput", "lineChart", 6, 6),
                        new DashboardWidget("alerts", "Recent Alerts", "list", 12, 6)
                )
        );
    }

    // Private helper methods

    /**
     * Calculates system load.
     */
    private double calculateSystemLoad() {
        // This would integrate with system metrics
        // For now, return a placeholder value
        return 0.75;
    }

    /**
     * Gets active actors grouped by type.
     */
    private Map<String, Integer> getActiveActorsByType() {
        List<ActorHealthMetrics.ActorHealthStatus> allActors = healthMetrics.getAllActiveActors();
        return allActors.stream()
                .collect(Collectors.groupingBy(
                        actor -> actor.getActorType(),
                        Collectors.summingInt(actor -> 1)
                ));
    }

    /**
     * Gets recent alerts.
     */
    private List<ActorAlertManager.AlertHistory> getRecentAlerts(int limit) {
        return alertManager.getAlertHistoryForActor(null, limit);
    }

    /**
     * Gets top slow actors.
     */
    private List<String> getTopSlowActors(int limit) {
        List<ActorHealthMetrics.ActorHealthStatus> allActors = healthMetrics.getAllActiveActors();
        return allActors.stream()
                .sorted((a1, a2) -> Double.compare(
                        a1.getAverageProcessingTime(),
                        a2.getAverageProcessingTime()
                ))
                .limit(limit)
                .map(ActorHealthMetrics.ActorHealthStatus::getActorId)
                .collect(Collectors.toList());
    }

    /**
     * Gets health trend data.
     */
    private TimeSeriesData getHealthTrendData() {
        return getTimeSeriesData("health_score", 60);
    }

    /**
     * Gets performance metrics.
     */
    private PerformanceMetrics getPerformanceMetrics() {
        return new PerformanceMetrics(
                calculateAverageLatency(),
                calculateThroughput(),
                calculateErrorRate(),
                calculateQueueWaitTime()
        );
    }

    /**
     * Gets throughput chart data.
     */
    private TimeSeriesData getThroughputChart() {
        return getTimeSeriesData("message_rate", 60);
    }

    /**
     * Gets latency chart data.
     */
    private TimeSeriesData getLatencyChart() {
        return getTimeSeriesData("latency", 60);
    }

    /**
     * Gets queue depth chart data.
     */
    private TimeSeriesData getQueueDepthChart() {
        return getTimeSeriesData("queue_depth", 60);
    }

    /**
     * Gets CPU usage chart data.
     */
    private TimeSeriesData getCpuUsageChart() {
        return getTimeSeriesData("cpu_usage", 60);
    }

    /**
     * Gets memory usage chart data.
     */
    private TimeSeriesData getMemoryUsageChart() {
        return getTimeSeriesData("memory_usage", 60);
    }

    /**
     * Gets alert trend data.
     */
    private TimeSeriesData getAlertTrendData() {
        return getTimeSeriesData("alert_rate", 60);
    }

    /**
     * Gets alert distribution by type.
     */
    private Map<String, Integer> getAlertDistributionByType() {
        List<ActorAlertManager.AlertHistory> alerts = getRecentAlerts(100);
        return alerts.stream()
                .collect(Collectors.groupingBy(
                        alert -> alert.getType().name(),
                        Collectors.summingInt(alert -> 1)
                ));
    }

    /**
     * Gets alert distribution by severity.
     */
    private Map<String, Integer> getAlertDistributionBySeverity() {
        List<ActorAlertManager.AlertHistory> alerts = getRecentAlerts(100);
        return alerts.stream()
                .collect(Collectors.groupingBy(
                        alert -> alert.getSeverity().name(),
                        Collectors.summingInt(alert -> 1)
                ));
    }

    /**
     * Calculates average latency.
     */
    private double calculateAverageLatency() {
        List<ActorHealthMetrics.ActorHealthStatus> allActors = healthMetrics.getAllActiveActors();
        return allActors.stream()
                .mapToDouble(ActorHealthMetrics.ActorHealthStatus::getAverageLatency)
                .average()
                .orElse(0.0);
    }

    /**
     * Calculates throughput.
     */
    private double calculateThroughput() {
        // This would calculate messages per second
        return 1000.0; // Placeholder
    }

    /**
     * Calculates error rate.
     */
    private double calculateErrorRate() {
        List<ActorHealthMetrics.ActorHealthStatus> allActors = healthMetrics.getAllActiveActors();
        long totalErrors = allActors.stream()
                .mapToLong(ActorHealthMetrics.ActorHealthStatus::getMessageErrors)
                .sum();
        long totalProcessed = allActors.stream()
                .mapToLong(ActorHealthMetrics.ActorHealthStatus::getMessagesProcessed)
                .sum();
        return totalProcessed > 0 ? (double) totalErrors / totalProcessed : 0.0;
    }

    /**
     * Calculates average queue wait time.
     */
    private double calculateQueueWaitTime() {
        List<ActorHealthMetrics.ActorHealthStatus> allActors = healthMetrics.getAllActiveActors();
        return allActors.stream()
                .mapToDouble(ActorHealthMetrics.ActorHealthStatus::getAverageQueueWaitTime)
                .average()
                .orElse(0.0);
    }

    // Supporting classes

    /**
     * Dashboard overview data.
     */
    public static final class DashboardOverview {
        private final LocalDateTime timestamp;
        private final ActorHealthMetrics.ActorHealthSummary healthSummary;
        private final ActorAlertManager.AlertStatistics alertStatistics;
        private final double systemLoad;
        private final Map<String, Integer> actorsByType;
        private final List<ActorAlertManager.AlertHistory> recentAlerts;
        private final List<String> topSlowActors;

        public DashboardOverview(LocalDateTime timestamp,
                               ActorHealthMetrics.ActorHealthSummary healthSummary,
                               ActorAlertManager.AlertStatistics alertStatistics,
                               double systemLoad,
                               Map<String, Integer> actorsByType,
                               List<ActorAlertManager.AlertHistory> recentAlerts,
                               List<String> topSlowActors) {
            this.timestamp = timestamp;
            this.healthSummary = healthSummary;
            this.alertStatistics = alertStatistics;
            this.systemLoad = systemLoad;
            this.actorsByType = actorsByType;
            this.recentAlerts = recentAlerts;
            this.topSlowActors = topSlowActors;
        }

        // Getters
        public LocalDateTime getTimestamp() { return timestamp; }
        public ActorHealthMetrics.ActorHealthSummary getHealthSummary() { return healthSummary; }
        public ActorAlertManager.AlertStatistics getAlertStatistics() { return alertStatistics; }
        public double getSystemLoad() { return systemLoad; }
        public Map<String, Integer> getActorsByType() { return actorsByType; }
        public List<ActorAlertManager.AlertHistory> getRecentAlerts() { return recentAlerts; }
        public List<String> getTopSlowActors() { return topSlowActors; }
    }

    /**
     * Actor health dashboard data.
     */
    public static final class ActorHealthDashboard {
        private final ActorHealthMetrics.ActorHealthSummary summary;
        private final List<ActorHealthMetrics.ActorHealthStatus> healthyActors;
        private final List<ActorHealthMetrics.ActorHealthStatus> warningActors;
        private final List<ActorHealthMetrics.ActorHealthStatus> unhealthyActors;
        private final List<ActorHealthMetrics.ActorHealthStatus> unknownActors;
        private final TimeSeriesData healthTrend;

        public ActorHealthDashboard(ActorHealthMetrics.ActorHealthSummary summary,
                                   List<ActorHealthMetrics.ActorHealthStatus> healthyActors,
                                   List<ActorHealthMetrics.ActorHealthStatus> warningActors,
                                   List<ActorHealthMetrics.ActorHealthStatus> unhealthyActors,
                                   List<ActorHealthMetrics.ActorHealthStatus> unknownActors,
                                   TimeSeriesData healthTrend) {
            this.summary = summary;
            this.healthyActors = healthyActors;
            this.warningActors = warningActors;
            this.unhealthyActors = unhealthyActors;
            this.unknownActors = unknownActors;
            this.healthTrend = healthTrend;
        }

        // Getters
        public ActorHealthMetrics.ActorHealthSummary getSummary() { return summary; }
        public List<ActorHealthMetrics.ActorHealthStatus> getHealthyActors() { return healthyActors; }
        public List<ActorHealthMetrics.ActorHealthStatus> getWarningActors() { return warningActors; }
        public List<ActorHealthMetrics.ActorHealthStatus> getUnhealthyActors() { return unhealthyActors; }
        public List<ActorHealthMetrics.ActorHealthStatus> getUnknownActors() { return unknownActors; }
        public TimeSeriesData getHealthTrend() { return healthTrend; }
    }

    /**
     * Performance dashboard data.
     */
    public static final class PerformanceDashboard {
        private final PerformanceMetrics metrics;
        private final TimeSeriesData throughputChart;
        private final TimeSeriesData latencyChart;
        private final TimeSeriesData queueDepthChart;
        private final TimeSeriesData cpuUsageChart;
        private final TimeSeriesData memoryUsageChart;

        public PerformanceDashboard(PerformanceMetrics metrics,
                                  TimeSeriesData throughputChart,
                                  TimeSeriesData latencyChart,
                                  TimeSeriesData queueDepthChart,
                                  TimeSeriesData cpuUsageChart,
                                  TimeSeriesData memoryUsageChart) {
            this.metrics = metrics;
            this.throughputChart = throughputChart;
            this.latencyChart = latencyChart;
            this.queueDepthChart = queueDepthChart;
            this.cpuUsageChart = cpuUsageChart;
            this.memoryUsageChart = memoryUsageChart;
        }

        // Getters
        public PerformanceMetrics getMetrics() { return metrics; }
        public TimeSeriesData getThroughputChart() { return throughputChart; }
        public TimeSeriesData getLatencyChart() { return latencyChart; }
        public TimeSeriesData getQueueDepthChart() { return queueDepthChart; }
        public TimeSeriesData getCpuUsageChart() { return cpuUsageChart; }
        public TimeSeriesData getMemoryUsageChart() { return memoryUsageChart; }
    }

    /**
     * Alert dashboard data.
     */
    public static final class AlertDashboard {
        private final ActorAlertManager.AlertStatistics statistics;
        private final List<ActorAlertManager.AlertHistory> recentAlerts;
        private final TimeSeriesData alertTrend;
        private final Map<String, Integer> distributionByType;
        private final Map<String, Integer> distributionBySeverity;

        public AlertDashboard(ActorAlertManager.AlertStatistics statistics,
                            List<ActorAlertManager.AlertHistory> recentAlerts,
                            TimeSeriesData alertTrend,
                            Map<String, Integer> distributionByType,
                            Map<String, Integer> distributionBySeverity) {
            this.statistics = statistics;
            this.recentAlerts = recentAlerts;
            this.alertTrend = alertTrend;
            this.distributionByType = distributionByType;
            this.distributionBySeverity = distributionBySeverity;
        }

        // Getters
        public ActorAlertManager.AlertStatistics getStatistics() { return statistics; }
        public List<ActorAlertManager.AlertHistory> getRecentAlerts() { return recentAlerts; }
        public TimeSeriesData getAlertTrend() { return alertTrend; }
        public Map<String, Integer> getDistributionByType() { return distributionByType; }
        public Map<String, Integer> getDistributionBySeverity() { return distributionBySeverity; }
    }

    /**
     * Performance metrics.
     */
    public static final class PerformanceMetrics {
        private final double averageLatencyMs;
        private final double throughputPerSecond;
        private final double errorRate;
        private final double averageQueueWaitTimeMs;

        public PerformanceMetrics(double averageLatencyMs, double throughputPerSecond,
                               double errorRate, double averageQueueWaitTimeMs) {
            this.averageLatencyMs = averageLatencyMs;
            this.throughputPerSecond = throughputPerSecond;
            this.errorRate = errorRate;
            this.averageQueueWaitTimeMs = averageQueueWaitTimeMs;
        }

        // Getters
        public double getAverageLatencyMs() { return averageLatencyMs; }
        public double getThroughputPerSecond() { return throughputPerSecond; }
        public double getErrorRate() { return errorRate; }
        public double getAverageQueueWaitTimeMs() { return averageQueueWaitTimeMs; }
    }

    /**
     * Time series data.
     */
    public static final class TimeSeriesData {
        private final String metricName;
        private final List<DataPoint> dataPoints;

        public TimeSeriesData(String metricName, List<DataPoint> dataPoints) {
            this.metricName = metricName;
            this.dataPoints = dataPoints;
        }

        public TimeSeriesData(String metricName, List<Double> values, List<Long> timestamps) {
            this.metricName = metricName;
            this.dataPoints = new ArrayList<>();
            for (int i = 0; i < values.size() && i < timestamps.size(); i++) {
                this.dataPoints.add(new DataPoint(values.get(i), timestamps.get(i)));
            }
        }

        // Getters
        public String getMetricName() { return metricName; }
        public List<DataPoint> getDataPoints() { return dataPoints; }

        public double[] getValues() {
            return dataPoints.stream().mapToDouble(DataPoint::getValue).toArray();
        }

        public long[] getTimestamps() {
            return dataPoints.stream().mapToLong(DataPoint::getTimestamp).toArray();
        }
    }

    /**
     * Data point in time series.
     */
    public static final class DataPoint {
        private final double value;
        private final long timestamp;

        public DataPoint(double value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }

        // Getters
        public double getValue() { return value; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Dashboard configuration.
     */
    public static final class DashboardConfiguration {
        private final List<String> availableViews;
        private final Map<String, Object> settings;
        private final List<DashboardWidget> widgets;

        public DashboardConfiguration(List<String> availableViews,
                                   Map<String, Object> settings,
                                   List<DashboardWidget> widgets) {
            this.availableViews = availableViews;
            this.settings = settings;
            this.widgets = widgets;
        }

        // Getters
        public List<String> getAvailableViews() { return availableViews; }
        public Map<String, Object> getSettings() { return settings; }
        public List<DashboardWidget> getWidgets() { return widgets; }
    }

    /**
     * Dashboard widget configuration.
     */
    public static final class DashboardWidget {
        private final String id;
        private final String title;
        private final String type;
        private final int width;
        private final int height;

        public DashboardWidget(String id, String title, String type, int width, int height) {
            this.id = id;
            this.title = title;
            this.type = type;
            this.width = width;
            this.height = height;
        }

        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
    }

    /**
     * Internal time series metric storage.
     */
    private class MetricTimeSeries {
        private final String metricName;
        private final int maxPoints;
        private final List<DataPoint> dataPoints;
        private long lastUpdate;

        public MetricTimeSeries(String metricName, int maxPoints) {
            this.metricName = metricName;
            this.maxPoints = maxPoints;
            this.dataPoints = new ArrayList<>(maxPoints);
            this.lastUpdate = System.currentTimeMillis();
        }

        public synchronized void addPoint(double value) {
            long timestamp = System.currentTimeMillis();
            dataPoints.add(new DataPoint(value, timestamp));

            // Remove old points if we exceed the limit
            while (dataPoints.size() > maxPoints) {
                dataPoints.remove(0);
            }

            lastUpdate = timestamp;
        }

        public synchronized TimeSeriesData getLastPoints(int count) {
            int start = Math.max(0, dataPoints.size() - count);
            List<DataPoint> points = new ArrayList<>(dataPoints.subList(start, dataPoints.size()));
            return new TimeSeriesData(metricName, points);
        }
    }
}