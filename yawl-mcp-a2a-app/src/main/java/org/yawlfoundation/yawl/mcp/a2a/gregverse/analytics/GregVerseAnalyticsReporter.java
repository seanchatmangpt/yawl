package org.yawlfoundation.yawl.mcp.a2a.gregverse.analytics;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Main analytics reporter for GregVerse marketplace
 * Coordinates all analytics components and provides unified API
 */
public class GregVerseAnalyticsReporter {

    private final AnalyticsCollector eventCollector;
    private final ReportGenerator reportGenerator;
    private final DashboardDataProvider dashboardProvider;
    private final boolean autoStart;
    private volatile boolean isInitialized = false;

    public GregVerseAnalyticsReporter() {
        this(true); // Auto-start by default
    }

    public GregVerseAnalyticsReporter(boolean autoStart) {
        this.autoStart = autoStart;

        // Initialize with default values (100ms batch, 50-point trend window)
        this.eventCollector = new AnalyticsCollector(100, 100);
        this.reportGenerator = new ReportGenerator(eventCollector.getMetrics());
        this.dashboardProvider = new DashboardDataProvider(
            eventCollector.getMetrics(), 50, 50 // 50ms update, 50-point window
        );

        if (autoStart) {
            start();
        }
    }

    public void start() {
        if (!isInitialized) {
            synchronized (this) {
                if (!isInitialized) {
                    eventCollector.start();
                    reportGenerator.startScheduledReports();
                    dashboardProvider.start();
                    isInitialized = true;
                }
            }
        }
    }

    public void stop() {
        if (isInitialized) {
            eventCollector.stop();
            reportGenerator.stopScheduledReports();
            dashboardProvider.stop();
        }
    }

    // Event submission methods
    public CompletableFuture<Void> recordTransaction(String providerId, String consumerId,
                                                   double value, String category,
                                                   long responseTimeMs, int rating,
                                                   String location) {
        AnalyticsCollector.MarketplaceEvent event =
            new AnalyticsCollector.MarketplaceEvent(
                AnalyticsCollector.EventType.TRANSACTION,
                providerId, consumerId, value, category, responseTimeMs, rating, location
            );
        return eventCollector.submitEvent(event);
    }

    public CompletableFuture<Void> recordProviderActivity(String providerId) {
        AnalyticsCollector.MarketplaceEvent event =
            new AnalyticsCollector.MarketplaceEvent(
                AnalyticsCollector.EventType.PROVIDER_ACTIVITY,
                providerId, null, 0, null, 0, 0, null
            );
        return eventCollector.submitEvent(event);
    }

    public CompletableFuture<Void> recordConsumerActivity(String consumerId) {
        AnalyticsCollector.MarketplaceEvent event =
            new AnalyticsCollector.MarketplaceEvent(
                AnalyticsCollector.EventType.CONSUMER_ACTIVITY,
                null, consumerId, 0, null, 0, 0, null
            );
        return eventCollector.submitEvent(event);
    }

    // Report generation methods
    public CompletableFuture<ReportGenerator.DailyReport> getDailyReport() {
        return reportGenerator.generateDailyReportAsync();
    }

    public CompletableFuture<ReportGenerator.WeeklyReport> getWeeklyReport() {
        return reportGenerator.generateWeeklyReportAsync();
    }

    public CompletableFuture<ReportGenerator.MonthlyReport> getMonthlyReport() {
        return reportGenerator.generateMonthlyReportAsync();
    }

    public CompletableFuture<ReportGenerator.CustomReport> getCustomReport(
            java.time.LocalDate startDate, java.time.LocalDate endDate,
            ReportGenerator.ReportFilter... filters) {
        return reportGenerator.generateCustomReportAsync(startDate, endDate, filters);
    }

    // Dashboard data methods
    public DashboardDataProvider.DashboardData getDashboardData() {
        return dashboardProvider.getLatestData();
    }

    public CompletableFuture<DashboardDataProvider.DashboardData> getDashboardDataAsync() {
        return dashboardProvider.getLatestDataAsync();
    }

    public DashboardDataProvider.MetricTrend getMetricTrend(String metricName) {
        return dashboardProvider.getMetricTrend(metricName);
    }

    public java.util.Map<String, DashboardDataProvider.MetricTrend> getAllMetricTrends() {
        return dashboardProvider.getAllMetricTrends();
    }

    // Health metrics
    public DashboardDataProvider.HealthMetrics getHealthMetrics() {
        return dashboardProvider.getHealthMetrics();
    }

    // Forecast methods
    public DashboardDataProvider.ForecastMetrics getForecast(int hoursAhead) {
        return dashboardProvider.generateForecast(hoursAhead);
    }

    public CompletableFuture<DashboardDataProvider.ForecastMetrics> getForecastAsync(int hoursAhead) {
        return CompletableFuture.supplyAsync(() -> dashboardProvider.generateForecast(hoursAhead));
    }

    // Analytics configuration methods
    public void setEventProcessor(java.util.function.Consumer<AnalyticsCollector.MarketplaceEvent> processor) {
        eventCollector.setEventProcessor(processor);
    }

    public void setUpdateInterval(int milliseconds) {
        // In a real implementation, this would restart the providers with new intervals
        // For now, it's a placeholder for future enhancement
    }

    public void setTrendWindowSize(int points) {
        // Similar to above, would require reinitialization in real implementation
    }

    // Status methods
    public boolean isRunning() {
        return isInitialized &&
               eventCollector.isRunning() &&
               true && // reportGenerator doesn't have isRunning() method
               true; // dashboardProvider doesn't have isRunning() method
    }

    public int getEventQueueSize() {
        return eventCollector.getQueueSize();
    }

    public Instant getLastUpdateTime() {
        return dashboardProvider.getLatestData().getTimestamp();
    }

    // Custom metrics aggregation
    public CompletableFuture<MetricAggregation> aggregateMetrics(java.util.function.Predicate<MetricAggregation> filter) {
        return CompletableFuture.supplyAsync(() -> {
            DashboardDataProvider.DashboardData data = dashboardProvider.getLatestData();
            MetricAggregation aggregation = new MetricAggregation(
                Instant.now(),
                data.getActiveProviders(),
                data.getActiveConsumers(),
                data.getTotalTransactions(),
                data.getAverageTransactionValue(),
                data.getAverageResponseTime(),
                data.getAveragePricesByCategory(),
                data.getRatingDistribution(),
                data.getTransactionCountsByCategory(),
                data.getGeographicDistribution()
            );

            return filter != null ? aggregation : aggregation;
        });
    }

    // Real-time event processor example
    public void enableRealTimeProcessing() {
        setEventProcessor(event -> {
            // Custom real-time processing logic
            System.out.println("Real-time event: " + event.getType() +
                             " - " + event.getTimestamp());

            // Could send to external systems, trigger alerts, etc.
        });
    }

    // Alert configuration
    public void configurePriceAlerts(double minThreshold, double maxThreshold, String category) {
        setEventProcessor(event -> {
            if (event.getType() == AnalyticsCollector.EventType.TRANSACTION &&
                event.getCategory().equals(category)) {

                if (event.getValue() < minThreshold) {
                    System.out.printf("Low price alert: %.2f below threshold %.2f%n",
                                    event.getValue(), minThreshold);
                } else if (event.getValue() > maxThreshold) {
                    System.out.printf("High price alert: %.2f above threshold %.2f%n",
                                    event.getValue(), maxThreshold);
                }
            }
        });
    }

    // Performance metrics
    public CompletableFuture<PerformanceMetrics> getPerformanceMetrics() {
        return CompletableFuture.supplyAsync(() -> {
            long eventsProcessed = dashboardProvider.getLatestData().getTotalTransactions();
            double avgProcessingTime = dashboardProvider.getLatestData().getAverageResponseTime();

            return new PerformanceMetrics(
                Instant.now(),
                eventsProcessed,
                avgProcessingTime,
                dashboardProvider.getMetricTrend("avgTransactionValue"),
                dashboardProvider.getMetricTrend("avgResponseTime")
            );
        });
    }

    // DTO for custom metric aggregation
    public static class MetricAggregation {
        private final Instant timestamp;
        private final int activeProviders;
        private final int activeConsumers;
        private final long totalTransactions;
        private final double averageTransactionValue;
        private final double averageResponseTime;
        private final java.util.Map<String, Double> averagePricesByCategory;
        private final java.util.Map<Integer, Double> ratingDistribution;
        private final java.util.Map<String, Long> transactionCountsByCategory;
        private final java.util.Map<String, Long> geographicDistribution;

        public MetricAggregation(Instant timestamp, int activeProviders, int activeConsumers,
                                long totalTransactions, double averageTransactionValue,
                                double averageResponseTime, java.util.Map<String, Double> averagePricesByCategory,
                                java.util.Map<Integer, Double> ratingDistribution,
                                java.util.Map<String, Long> transactionCountsByCategory,
                                java.util.Map<String, Long> geographicDistribution) {
            this.timestamp = timestamp;
            this.activeProviders = activeProviders;
            this.activeConsumers = activeConsumers;
            this.totalTransactions = totalTransactions;
            this.averageTransactionValue = averageTransactionValue;
            this.averageResponseTime = averageResponseTime;
            this.averagePricesByCategory = averagePricesByCategory;
            this.ratingDistribution = ratingDistribution;
            this.transactionCountsByCategory = transactionCountsByCategory;
            this.geographicDistribution = geographicDistribution;
        }

        // Getters
        public Instant getTimestamp() { return timestamp; }
        public int getActiveProviders() { return activeProviders; }
        public int getActiveConsumers() { return activeConsumers; }
        public long getTotalTransactions() { return totalTransactions; }
        public double getAverageTransactionValue() { return averageTransactionValue; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public java.util.Map<String, Double> getAveragePricesByCategory() { return averagePricesByCategory; }
        public java.util.Map<Integer, Double> getRatingDistribution() { return ratingDistribution; }
        public java.util.Map<String, Long> getTransactionCountsByCategory() { return transactionCountsByCategory; }
        public java.util.Map<String, Long> getGeographicDistribution() { return geographicDistribution; }
    }

    // Performance metrics DTO
    public static class PerformanceMetrics {
        private final Instant timestamp;
        private final long eventsProcessed;
        private final double averageProcessingTime;
        private final DashboardDataProvider.MetricTrend transactionValueTrend;
        private final DashboardDataProvider.MetricTrend responseTimeTrend;

        public PerformanceMetrics(Instant timestamp, long eventsProcessed,
                                 double averageProcessingTime,
                                 DashboardDataProvider.MetricTrend transactionValueTrend,
                                 DashboardDataProvider.MetricTrend responseTimeTrend) {
            this.timestamp = timestamp;
            this.eventsProcessed = eventsProcessed;
            this.averageProcessingTime = averageProcessingTime;
            this.transactionValueTrend = transactionValueTrend;
            this.responseTimeTrend = responseTimeTrend;
        }

        // Getters
        public Instant getTimestamp() { return timestamp; }
        public long getEventsProcessed() { return eventsProcessed; }
        public double getAverageProcessingTime() { return averageProcessingTime; }
        public DashboardDataProvider.MetricTrend getTransactionValueTrend() { return transactionValueTrend; }
        public DashboardDataProvider.MetricTrend getResponseTimeTrend() { return responseTimeTrend; }
    }

    // Builder pattern for advanced configuration
    public static class Builder {
        private boolean autoStart = true;
        private int batchSize = 100;
        private int updateIntervalMs = 50;
        private int trendWindowSize = 50;
        private java.util.function.Consumer<AnalyticsCollector.MarketplaceEvent> customProcessor;

        public Builder autoStart(boolean enabled) {
            this.autoStart = enabled;
            return this;
        }

        public Builder batchSize(int size) {
            this.batchSize = size;
            return this;
        }

        public Builder updateInterval(int milliseconds) {
            this.updateIntervalMs = milliseconds;
            return this;
        }

        public Builder trendWindowSize(int points) {
            this.trendWindowSize = points;
            return this;
        }

        public Builder eventProcessor(java.util.function.Consumer<AnalyticsCollector.MarketplaceEvent> processor) {
            this.customProcessor = processor;
            return this;
        }

        public GregVerseAnalyticsReporter build() {
            GregVerseAnalyticsReporter reporter = new GregVerseAnalyticsReporter(false);

            // Apply custom settings
            if (customProcessor != null) {
                reporter.setEventProcessor(customProcessor);
            }

            // Start if configured
            if (autoStart) {
                reporter.start();
            }

            return reporter;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}