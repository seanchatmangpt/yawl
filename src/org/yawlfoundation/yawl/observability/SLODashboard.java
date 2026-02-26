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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observability;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SLO Dashboard for real-time monitoring and reporting.
 *
 * <p>Provides a unified dashboard view of all SLO metrics with support
 * for JSON and HTML report generation. Includes Chart.js visualization
 * for web-based dashboards.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Real-time SLO compliance snapshots</li>
 *   <li>Historical trend visualization</li>
 *   <li>JSON API for integration with monitoring systems</li>
 *   <li>HTML dashboard with Chart.js charts</li>
 *   <li>Configurable refresh intervals</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * SLODashboard dashboard = SLODashboard.builder()
 *     .refreshInterval(Duration.ofSeconds(30))
 *     .historySize(100)
 *     .build();
 *
 * dashboard.start();
 *
 * // Get current snapshot
 * DashboardSnapshot snapshot = dashboard.getCurrentSnapshot();
 *
 * // Generate JSON report
 * String json = dashboard.generateJsonReport(Instant.now().minus(Duration.ofHours(1)), Instant.now());
 *
 * // Generate HTML dashboard
 * String html = dashboard.generateHtmlReport();
 *
 * dashboard.stop();
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
public final class SLODashboard {

    private static final Logger LOGGER = LoggerFactory.getLogger(SLODashboard.class);
    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .create();

    /**
     * Dashboard configuration builder.
     */
    public static final class Builder {
        private Duration refreshInterval = Duration.ofSeconds(30);
        private int historySize = 100;
        private boolean autoRefresh = true;
        private String title = "YAWL SLO Dashboard";
        private SLOTracker sloTracker;

        public Builder refreshInterval(Duration interval) {
            this.refreshInterval = interval;
            return this;
        }

        public Builder historySize(int size) {
            this.historySize = size;
            return this;
        }

        public Builder autoRefresh(boolean enabled) {
            this.autoRefresh = enabled;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder sloTracker(SLOTracker tracker) {
            this.sloTracker = tracker;
            return this;
        }

        public SLODashboard build() {
            return new SLODashboard(this);
        }
    }

    /**
     * Single SLO metric entry in the dashboard.
     */
    public static final class SLOMetricEntry {
        private final String name;
        private final String displayName;
        private final double currentValue;
        private final double targetValue;
        private final SLOTracker.ComplianceStatus status;
        private final SLOTracker.TrendDirection trend;
        private final double burnRate;
        private final long totalSamples;
        private final Instant lastUpdated;

        public SLOMetricEntry(String name, String displayName, double currentValue,
                             double targetValue, SLOTracker.ComplianceStatus status,
                             SLOTracker.TrendDirection trend, double burnRate,
                             long totalSamples) {
            this.name = name;
            this.displayName = displayName;
            this.currentValue = currentValue;
            this.targetValue = targetValue;
            this.status = status;
            this.trend = trend;
            this.burnRate = burnRate;
            this.totalSamples = totalSamples;
            this.lastUpdated = Instant.now();
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public double getCurrentValue() { return currentValue; }
        public double getTargetValue() { return targetValue; }
        public SLOTracker.ComplianceStatus getStatus() { return status; }
        public SLOTracker.TrendDirection getTrend() { return trend; }
        public double getBurnRate() { return burnRate; }
        public long getTotalSamples() { return totalSamples; }
        public Instant getLastUpdated() { return lastUpdated; }

        /**
         * Gets the status color for visualization.
         */
        public String getStatusColor() {
            return switch (status) {
                case COMPLIANT -> "#28a745"; // Green
                case AT_RISK -> "#ffc107";   // Yellow
                case VIOLATION -> "#dc3545"; // Red
            };
        }

        /**
         * Gets the trend icon for visualization.
         */
        public String getTrendIcon() {
            return switch (trend) {
                case IMPROVING -> "📈";
                case DETERIORATING -> "📉";
                case STABLE -> "➡️";
                case INSUFFICIENT_DATA -> "❓";
            };
        }
    }

    /**
     * Complete dashboard snapshot at a point in time.
     */
    public static final class DashboardSnapshot {
        private final Instant timestamp;
        private final List<SLOMetricEntry> metrics;
        private final int compliantCount;
        private final int atRiskCount;
        private final int violationCount;
        private final double overallCompliance;
        private final Duration uptime;
        private final String version;

        public DashboardSnapshot(List<SLOMetricEntry> metrics, Duration uptime, String version) {
            this.timestamp = Instant.now();
            this.metrics = List.copyOf(metrics);
            this.uptime = uptime;
            this.version = version;

            int compliant = 0, atRisk = 0, violation = 0;
            double totalCompliance = 0;

            for (SLOMetricEntry entry : metrics) {
                switch (entry.getStatus()) {
                    case COMPLIANT -> compliant++;
                    case AT_RISK -> atRisk++;
                    case VIOLATION -> violation++;
                }
                totalCompliance += entry.getCurrentValue();
            }

            this.compliantCount = compliant;
            this.atRiskCount = atRisk;
            this.violationCount = violation;
            this.overallCompliance = metrics.isEmpty() ? 0 : totalCompliance / metrics.size();
        }

        public Instant getTimestamp() { return timestamp; }
        public List<SLOMetricEntry> getMetrics() { return metrics; }
        public int getCompliantCount() { return compliantCount; }
        public int getAtRiskCount() { return atRiskCount; }
        public int getViolationCount() { return violationCount; }
        public double getOverallCompliance() { return overallCompliance; }
        public Duration getUptime() { return uptime; }
        public String getVersion() { return version; }
        public int getTotalMetrics() { return metrics.size(); }

        /**
         * Gets metrics sorted by status (violations first).
         */
        public List<SLOMetricEntry> getMetricsByPriority() {
            return metrics.stream()
                .sorted((a, b) -> {
                    int statusCompare = Integer.compare(
                        b.getStatus().ordinal(),
                        a.getStatus().ordinal()
                    );
                    if (statusCompare != 0) return statusCompare;
                    return Double.compare(a.getCurrentValue(), b.getCurrentValue());
                })
                .toList();
        }
    }

    /**
     * Historical data point for trend charts.
     */
    public static final class HistoricalDataPoint {
        private final Instant timestamp;
        private final Map<String, Double> values;

        public HistoricalDataPoint(Instant timestamp, Map<String, Double> values) {
            this.timestamp = timestamp;
            this.values = Map.copyOf(values);
        }

        public Instant getTimestamp() { return timestamp; }
        public Map<String, Double> getValues() { return values; }
        public double getValue(String metricName) { return values.getOrDefault(metricName, 0.0); }
    }

    // Configuration
    private final Duration refreshInterval;
    private final int historySize;
    private final boolean autoRefresh;
    private final String title;
    private final SLOTracker sloTracker;
    private final LockContentionTracker lockContentionTracker;
    private final AndonCord andonCord;

    // State
    private final ConcurrentLinkedQueue<HistoricalDataPoint> history = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ReentrantLock historyLock = new ReentrantLock();
    private volatile Instant startTime;
    private ScheduledExecutorService scheduler;

    private SLODashboard(Builder builder) {
        this.refreshInterval = builder.refreshInterval;
        this.historySize = builder.historySize;
        this.autoRefresh = builder.autoRefresh;
        this.title = builder.title;
        this.sloTracker = builder.sloTracker != null ? builder.sloTracker : new SLOTracker();
        this.lockContentionTracker = LockContentionTracker.getInstance();
        this.andonCord = AndonCord.getInstance();

        LOGGER.info("SLODashboard created: refreshInterval={}s, historySize={}",
            refreshInterval.toSeconds(), historySize);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts the dashboard with automatic refresh.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            startTime = Instant.now();
            if (autoRefresh) {
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "slo-dashboard-refresh");
                    t.setDaemon(true);
                    return t;
                });
                scheduler.scheduleAtFixedRate(
                    this::collectSnapshot,
                    0,
                    refreshInterval.toMillis(),
                    TimeUnit.MILLISECONDS
                );
            }
            LOGGER.info("SLODashboard started");
        }
    }

    /**
     * Stops the dashboard.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            LOGGER.info("SLODashboard stopped");
        }
    }

    /**
     * Checks if the dashboard is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Gets the current dashboard snapshot.
     */
    public DashboardSnapshot getCurrentSnapshot() {
        List<SLOMetricEntry> entries = new ArrayList<>();

        for (SLOTracker.SLOType type : SLOTracker.SLOType.values()) {
            double compliance = sloTracker.getComplianceRate(type, Duration.ofHours(1));
            SLOTracker.TrendDirection trend = sloTracker.getTrend(type);
            double burnRate = sloTracker.getBurnRate(type);
            long samples = sloTracker.getTotalMetrics(type);

            SLOTracker.ComplianceStatus status = SLOTracker.ComplianceStatus.fromValues(
                compliance, type.getTargetPercentage(), type.isHigherBetter()
            );

            entries.add(new SLOMetricEntry(
                type.name(),
                formatDisplayName(type.name()),
                compliance,
                type.getTargetPercentage(),
                status,
                trend,
                burnRate,
                samples
            ));
        }

        Duration uptime = startTime != null ? Duration.between(startTime, Instant.now()) : Duration.ZERO;
        return new DashboardSnapshot(entries, uptime, "6.0.0-GA");
    }

    /**
     * Collects a snapshot for historical tracking.
     */
    private void collectSnapshot() {
        try {
            Map<String, Double> values = new HashMap<>();
            for (SLOTracker.SLOType type : SLOTracker.SLOType.values()) {
                values.put(type.name(), sloTracker.getComplianceRate(type, Duration.ofHours(1)));
            }

            HistoricalDataPoint dataPoint = new HistoricalDataPoint(Instant.now(), values);

            historyLock.lock();
            try {
                history.offer(dataPoint);
                while (history.size() > historySize) {
                    history.poll();
                }
            } finally {
                historyLock.unlock();
            }
        } catch (Exception e) {
            LOGGER.error("Error collecting dashboard snapshot", e);
        }
    }

    /**
     * Generates a JSON report for a time range.
     *
     * @param from start time
     * @param to end time
     * @return JSON formatted report
     */
    public String generateJsonReport(Instant from, Instant to) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("title", title);
        report.put("generatedAt", Instant.now().toString());
        report.put("timeRange", Map.of(
            "from", from.toString(),
            "to", to.toString()
        ));

        DashboardSnapshot snapshot = getCurrentSnapshot();
        report.put("summary", Map.of(
            "overallCompliance", snapshot.getOverallCompliance(),
            "compliantCount", snapshot.getCompliantCount(),
            "atRiskCount", snapshot.getAtRiskCount(),
            "violationCount", snapshot.getViolationCount(),
            "totalMetrics", snapshot.getTotalMetrics()
        ));

        report.put("metrics", snapshot.getMetrics().stream()
            .map(m -> Map.of(
                "name", m.getName(),
                "displayName", m.getDisplayName(),
                "currentValue", m.getCurrentValue(),
                "targetValue", m.getTargetValue(),
                "status", m.getStatus().name(),
                "trend", m.getTrend().name(),
                "burnRate", m.getBurnRate(),
                "totalSamples", m.getTotalSamples(),
                "lastUpdated", m.getLastUpdated().toString()
            ))
            .toList());

        // Include historical data
        List<HistoricalDataPoint> historyInRange = getHistoryInRange(from, to);
        report.put("history", historyInRange.stream()
            .map(h -> Map.of(
                "timestamp", h.getTimestamp().toString(),
                "values", h.getValues()
            ))
            .toList());

        return GSON.toJson(report);
    }

    /**
     * Generates an HTML dashboard with Chart.js visualization.
     *
     * @return HTML document with embedded charts
     */
    public String generateHtmlReport() {
        DashboardSnapshot snapshot = getCurrentSnapshot();
        List<HistoricalDataPoint> historyList = getHistory();

        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
                    .dashboard { max-width: 1400px; margin: 0 auto; }
                    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 20px; border-radius: 10px; margin-bottom: 20px; }
                    .header h1 { margin: 0; }
                    .header .meta { opacity: 0.8; margin-top: 10px; }
                    .summary-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 15px; margin-bottom: 20px; }
                    .card { background: white; padding: 20px; border-radius: 10px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    .card.compliant { border-left: 4px solid #28a745; }
                    .card.at-risk { border-left: 4px solid #ffc107; }
                    .card.violation { border-left: 4px solid #dc3545; }
                    .card h3 { margin: 0 0 10px 0; color: #666; font-size: 14px; text-transform: uppercase; }
                    .card .value { font-size: 32px; font-weight: bold; }
                    .metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(350px, 1fr)); gap: 15px; }
                    .metric-card { background: white; padding: 20px; border-radius: 10px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    .metric-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; }
                    .metric-name { font-weight: 600; font-size: 16px; }
                    .metric-status { padding: 4px 12px; border-radius: 20px; font-size: 12px; font-weight: 600; }
                    .metric-status.compliant { background: #d4edda; color: #155724; }
                    .metric-status.at-risk { background: #fff3cd; color: #856404; }
                    .metric-status.violation { background: #f8d7da; color: #721c24; }
                    .metric-value { font-size: 28px; font-weight: bold; margin-bottom: 5px; }
                    .metric-target { color: #666; font-size: 14px; }
                    .metric-trend { margin-top: 10px; font-size: 14px; }
                    .chart-container { background: white; padding: 20px; border-radius: 10px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-top: 20px; }
                    .chart-container canvas { max-height: 400px; }
                </style>
            </head>
            <body>
                <div class="dashboard">
                    <div class="header">
                        <h1>%s</h1>
                        <div class="meta">
                            Generated: %s | Uptime: %s | Version: %s
                        </div>
                    </div>

                    <div class="summary-cards">
                        <div class="card compliant">
                            <h3>Compliant</h3>
                            <div class="value">%d</div>
                        </div>
                        <div class="card at-risk">
                            <h3>At Risk</h3>
                            <div class="value">%d</div>
                        </div>
                        <div class="card violation">
                            <h3>Violations</h3>
                            <div class="value">%d</div>
                        </div>
                        <div class="card">
                            <h3>Overall Compliance</h3>
                            <div class="value">%.1f%%</div>
                        </div>
                    </div>

                    <div class="metrics-grid">
            """.formatted(
                title,
                title,
                DateTimeFormatter.ISO_INSTANT.format(snapshot.getTimestamp()),
                formatDuration(snapshot.getUptime()),
                snapshot.getVersion(),
                snapshot.getCompliantCount(),
                snapshot.getAtRiskCount(),
                snapshot.getViolationCount(),
                snapshot.getOverallCompliance()
            ));

        // Add metric cards
        for (SLOMetricEntry metric : snapshot.getMetrics()) {
            html.append("""
                        <div class="metric-card">
                            <div class="metric-header">
                                <span class="metric-name">%s</span>
                                <span class="metric-status %s">%s</span>
                            </div>
                            <div class="metric-value" style="color: %s">%.1f%%</div>
                            <div class="metric-target">Target: %.1f%%</div>
                            <div class="metric-trend">Trend: %s | Burn Rate: %.1f%% | Samples: %d</div>
                        </div>
                """.formatted(
                metric.getDisplayName(),
                metric.getStatus().name().toLowerCase(),
                metric.getStatus().name(),
                metric.getStatusColor(),
                metric.getCurrentValue(),
                metric.getTargetValue(),
                metric.getTrendIcon() + " " + metric.getTrend().name(),
                metric.getBurnRate() * 100,
                metric.getTotalSamples()
            ));
        }

        html.append("""
                    </div>

                    <div class="chart-container">
                        <h3>SLO Compliance Trend</h3>
                        <canvas id="trendChart"></canvas>
                    </div>
                </div>

                <script>
            """);

        // Add Chart.js data
        html.append("const labels = [")
            .append(String.join(", ", historyList.stream()
                .map(h -> "'" + h.getTimestamp().toString().substring(11, 19) + "'")
                .toList()))
            .append("];\n");

        // Add datasets for each SLO type
        html.append("const datasets = [\n");
        String[] colors = {"#4dc9f6", "#f67019", "#f53794", "#537bc4", "#acc236", "#166a8f"};
        int i = 0;
        for (SLOTracker.SLOType type : SLOTracker.SLOType.values()) {
            String color = colors[i % colors.length];
            html.append("""
                    {
                        label: '%s',
                        data: [%s],
                        borderColor: '%s',
                        backgroundColor: '%s20',
                        tension: 0.3,
                        fill: false
                    },
                """.formatted(
                type.name(),
                String.join(", ", historyList.stream()
                    .map(h -> String.valueOf(h.getValue(type.name())))
                    .toList()),
                color,
                color
            ));
            i++;
        }
        html.append("];\n");

        html.append("""
                    const ctx = document.getElementById('trendChart').getContext('2d');
                    new Chart(ctx, {
                        type: 'line',
                        data: { labels: labels, datasets: datasets },
                        options: {
                            responsive: true,
                            scales: {
                                y: { beginAtZero: true, max: 100, title: { display: true, text: 'Compliance %' } }
                            },
                            plugins: {
                                legend: { position: 'bottom' }
                            }
                        }
                    });
                </script>
            </body>
            </html>
            """);

        return html.toString();
    }

    /**
     * Gets historical data points.
     */
    public List<HistoricalDataPoint> getHistory() {
        return List.copyOf(history);
    }

    /**
     * Gets historical data within a time range.
     */
    public List<HistoricalDataPoint> getHistoryInRange(Instant from, Instant to) {
        return history.stream()
            .filter(h -> !h.getTimestamp().isBefore(from) && !h.getTimestamp().isAfter(to))
            .toList();
    }

    /**
     * Clears historical data.
     */
    public void clearHistory() {
        historyLock.lock();
        try {
            history.clear();
        } finally {
            historyLock.unlock();
        }
    }

    private String formatDisplayName(String name) {
        return name.replace("_", " ")
            .replace("VT", "Virtual Thread")
            .replace("SLO", "");
    }

    private String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
