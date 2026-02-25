package org.yawlfoundation.yawl.mcp.a2a.gregverse.analytics;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Provides real-time statistics for dashboard displays
 */
public class DashboardDataProvider {

    private final MarketplaceMetrics metrics;
    private final ExecutorService realTimeExecutor;
    private final AtomicReference<DashboardData> latestData;
    private final Map<String, MetricTrend> metricTrends;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final int dataUpdateIntervalMs;
    private final int trendWindowSize;

    public DashboardDataProvider(MarketplaceMetrics metrics, int dataUpdateIntervalMs, int trendWindowSize) {
        this.metrics = metrics;
        this.dataUpdateIntervalMs = dataUpdateIntervalMs;
        this.trendWindowSize = trendWindowSize;
        this.realTimeExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.latestData = new AtomicReference<>(createInitialDashboardData());
        this.metricTrends = new ConcurrentHashMap<>();

        // Initialize trend trackers
        initializeTrendTrackers();
    }

    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            // Start real-time data updates
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(
                this::updateDashboardData,
                0, dataUpdateIntervalMs, TimeUnit.MILLISECONDS
            );

            // Start trend calculation
            scheduler.scheduleAtFixedRate(
                this::updateMetricTrends,
                5000, 5000, TimeUnit.MILLISECONDS
            );

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
        }
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            realTimeExecutor.shutdown();
            try {
                if (!realTimeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    realTimeExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                realTimeExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public DashboardData getLatestData() {
        return latestData.get();
    }

    public CompletableFuture<DashboardData> getLatestDataAsync() {
        return CompletableFuture.supplyAsync(() -> latestData.get(), realTimeExecutor);
    }

    public MetricTrend getMetricTrend(String metricName) {
        return metricTrends.get(metricName);
    }

    public Map<String, MetricTrend> getAllMetricTrends() {
        return new HashMap<>(metricTrends);
    }

    public HealthMetrics getHealthMetrics() {
        DashboardData data = latestData.get();
        return new HealthMetrics(
            data.getActiveProviders(),
            data.getActiveConsumers(),
            data.getTotalTransactions(),
            data.getAverageTransactionValue(),
            data.getAverageResponseTime(),
            calculateProviderHealthScore(),
            calculateMarketActivityScore(),
            Instant.now()
        );
    }

    public ForecastMetrics generateForecast(int hoursAhead) {
        DashboardData current = latestData.get();

        // Simple forecasting based on recent trends
        Map<String, MetricTrend> trends = getAllMetricTrends();

        double projectedTransactions = current.getTotalTransactions() +
            (trends.get("transactionCount") != null ?
             trends.get("transactionCount").getSlope() * hoursAhead : 0);

        double projectedValue = current.getAverageTransactionValue() *
            (1 + (trends.get("avgTransactionValue") != null ?
                  trends.get("avgTransactionValue").getSlope() * hoursAhead / 100 : 0));

        double projectedResponseTime = current.getAverageResponseTime() +
            (trends.get("avgResponseTime") != null ?
             trends.get("avgResponseTime").getSlope() * hoursAhead : 0);

        return new ForecastMetrics(
            Instant.now(),
            hoursAhead,
            projectedTransactions,
            projectedValue,
            projectedResponseTime,
            calculateForecastConfidence(),
            current
        );
    }

    private void updateDashboardData() {
        try {
            DashboardData newData = createDashboardData();
            latestData.set(newData);
        } catch (Exception e) {
            System.err.println("Error updating dashboard data: " + e.getMessage());
        }
    }

    private void updateMetricTrends() {
        try {
            DashboardData current = latestData.get();
            Instant now = Instant.now();

            // Update trends for key metrics
            updateTrend("transactionCount", current.getTotalTransactions(), now);
            updateTrend("avgTransactionValue", current.getAverageTransactionValue(), now);
            updateTrend("avgResponseTime", current.getAverageResponseTime(), now);
            updateTrend("activeProviders", current.getActiveProviders(), now);
            updateTrend("activeConsumers", current.getActiveConsumers(), now);

            // Update category trends
            current.getTransactionCountsByCategory().forEach((category, count) -> {
                updateTrend("category_" + category, count, now);
            });

        } catch (Exception e) {
            System.err.println("Error updating metric trends: " + e.getMessage());
        }
    }

    private void updateTrend(String metricName, double value, Instant timestamp) {
        metricTrends.compute(metricName, (k, v) -> {
            if (v == null) {
                return new MetricTrend(metricName, value, timestamp);
            }
            return v.addPoint(value, timestamp);
        });
    }

    private void initializeTrendTrackers() {
        String[] metrics = {
            "transactionCount", "avgTransactionValue", "avgResponseTime",
            "activeProviders", "activeConsumers"
        };

        Arrays.stream(metrics).forEach(metric ->
            metricTrends.put(metric, new MetricTrend(metric, 0, Instant.now()))
        );
    }

    private DashboardData createInitialDashboardData() {
        return createDashboardData();
    }

    private DashboardData createDashboardData() {
        Instant now = Instant.now();
        return new DashboardData(
            metrics.getActiveProviderCount(),
            metrics.getActiveConsumerCount(),
            metrics.getTotalTransactionCount(),
            metrics.getAverageTransactionValue(),
            metrics.getAverageResponseTime(),
            metrics.getAveragePricesByCategory(),
            metrics.getRatingDistributionPercentages(),
            metrics.getTransactionCountsByCategory(),
            metrics.getGeographicDistribution(),
            now
        );
    }

    private double calculateProviderHealthScore() {
        int activeProviders = metrics.getActiveProviderCount();
        long totalTransactions = metrics.getTotalTransactionCount();

        // Simple scoring based on activity and transaction volume
        double activityScore = Math.min(activeProviders / 10.0, 1.0);
        double transactionScore = Math.min(totalTransactions / 1000.0, 1.0);

        return (activityScore * 0.6 + transactionScore * 0.4) * 100;
    }

    private double calculateMarketActivityScore() {
        int activeParticipants = metrics.getActiveProviderCount() + metrics.getActiveConsumerCount();
        long totalTransactions = metrics.getTotalTransactionCount();

        double participationScore = Math.min(activeParticipants / 100.0, 1.0);
        double transactionScore = Math.min(totalTransactions / 5000.0, 1.0);

        return (participationScore * 0.5 + transactionScore * 0.5) * 100;
    }

    private double calculateForecastConfidence() {
        // Simple confidence calculation based on trend consistency
        long consistentTrends = metricTrends.values().stream()
            .filter(t -> t.getR2Score() > 0.7)
            .count();

        return (consistentTrends * 100.0) / metricTrends.size();
    }

    // Data transfer objects
    public static class DashboardData {
        private final int activeProviders;
        private final int activeConsumers;
        private final long totalTransactions;
        private final double averageTransactionValue;
        private final double averageResponseTime;
        private final Map<String, Double> averagePricesByCategory;
        private final Map<Integer, Double> ratingDistribution;
        private final Map<String, Long> transactionCountsByCategory;
        private final Map<String, Long> geographicDistribution;
        private final Instant timestamp;

        public DashboardData(int activeProviders, int activeConsumers,
                           long totalTransactions, double averageTransactionValue,
                           double averageResponseTime, Map<String, Double> averagePricesByCategory,
                           Map<Integer, Double> ratingDistribution,
                           Map<String, Long> transactionCountsByCategory,
                           Map<String, Long> geographicDistribution, Instant timestamp) {
            this.activeProviders = activeProviders;
            this.activeConsumers = activeConsumers;
            this.totalTransactions = totalTransactions;
            this.averageTransactionValue = averageTransactionValue;
            this.averageResponseTime = averageResponseTime;
            this.averagePricesByCategory = averagePricesByCategory;
            this.ratingDistribution = ratingDistribution;
            this.transactionCountsByCategory = transactionCountsByCategory;
            this.geographicDistribution = geographicDistribution;
            this.timestamp = timestamp;
        }

        // Getters
        public int getActiveProviders() { return activeProviders; }
        public int getActiveConsumers() { return activeConsumers; }
        public long getTotalTransactions() { return totalTransactions; }
        public double getAverageTransactionValue() { return averageTransactionValue; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public Map<String, Double> getAveragePricesByCategory() { return averagePricesByCategory; }
        public Map<Integer, Double> getRatingDistribution() { return ratingDistribution; }
        public Map<String, Long> getTransactionCountsByCategory() { return transactionCountsByCategory; }
        public Map<String, Long> getGeographicDistribution() { return geographicDistribution; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class MetricTrend {
        private final String metricName;
        private final Deque<DataPoint> dataPoints;
        private final int maxPoints;
        private double slope;
        private double r2Score;

        public MetricTrend(String metricName, double initialValue, Instant timestamp) {
            this.metricName = metricName;
            this.maxPoints = 50; // Default value, trendWindowSize is set by constructor
            this.dataPoints = new ArrayDeque<>(maxPoints);
            this.dataPoints.add(new DataPoint(initialValue, timestamp));
        }

        public MetricTrend addPoint(double value, Instant timestamp) {
            dataPoints.add(new DataPoint(value, timestamp));

            // Remove oldest points if we exceed the window size
            while (dataPoints.size() > maxPoints) {
                dataPoints.removeFirst();
            }

            // Calculate trend
            calculateLinearRegression();

            return this;
        }

        private void calculateLinearRegression() {
            if (dataPoints.size() < 2) {
                this.slope = 0;
                this.r2Score = 0;
                return;
            }

            int n = dataPoints.size();
            double[] x = new double[n];
            double[] y = new double[n];

            // Convert timestamps to relative positions
            Instant baseTime = dataPoints.getFirst().timestamp();
            DataPoint[] pointsArray = dataPoints.toArray(new DataPoint[0]);
            for (int i = 0; i < n; i++) {
                x[i] = Duration.between(baseTime, pointsArray[i].timestamp()).toMillis() / 3600000.0; // hours
                y[i] = pointsArray[i].value();
            }

            // Calculate slope (trend)
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                sumX += x[i];
                sumY += y[i];
                sumXY += x[i] * y[i];
                sumX2 += x[i] * x[i];
            }

            this.slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

            // Calculate R² score (goodness of fit)
            double meanY = sumY / n;
            double ssTotal = 0, ssResidual = 0;
            for (int i = 0; i < n; i++) {
                double predicted = slope * x[i] + (sumY - slope * sumX) / n;
                ssTotal += Math.pow(y[i] - meanY, 2);
                ssResidual += Math.pow(y[i] - predicted, 2);
            }

            this.r2Score = ssTotal == 0 ? 0 : 1 - (ssResidual / ssTotal);
        }

        // Getters
        public String getMetricName() { return metricName; }
        public double getSlope() { return slope; }
        public double getR2Score() { return r2Score; }
        public List<DataPoint> getDataPoints() { return new ArrayList<>(dataPoints); }

        private record DataPoint(double value, Instant timestamp) {}
    }

    public static class HealthMetrics {
        private final int activeProviders;
        private final int activeConsumers;
        private final long totalTransactions;
        private final double averageTransactionValue;
        private final double averageResponseTime;
        private final double providerHealthScore;
        private final double marketActivityScore;
        private final Instant timestamp;

        public HealthMetrics(int activeProviders, int activeConsumers,
                           long totalTransactions, double averageTransactionValue,
                           double averageResponseTime, double providerHealthScore,
                           double marketActivityScore, Instant timestamp) {
            this.activeProviders = activeProviders;
            this.activeConsumers = activeConsumers;
            this.totalTransactions = totalTransactions;
            this.averageTransactionValue = averageTransactionValue;
            this.averageResponseTime = averageResponseTime;
            this.providerHealthScore = providerHealthScore;
            this.marketActivityScore = marketActivityScore;
            this.timestamp = timestamp;
        }

        // Getters
        public int getActiveProviders() { return activeProviders; }
        public int getActiveConsumers() { return activeConsumers; }
        public long getTotalTransactions() { return totalTransactions; }
        public double getAverageTransactionValue() { return averageTransactionValue; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public double getProviderHealthScore() { return providerHealthScore; }
        public double getMarketActivityScore() { return marketActivityScore; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static class ForecastMetrics {
        private final Instant generatedAt;
        private final int forecastHours;
        private final double projectedTransactions;
        private final double projectedValue;
        private final double projectedResponseTime;
        private final double confidence;
        private final DashboardData currentData;

        public ForecastMetrics(Instant generatedAt, int forecastHours,
                             double projectedTransactions, double projectedValue,
                             double projectedResponseTime, double confidence,
                             DashboardData currentData) {
            this.generatedAt = generatedAt;
            this.forecastHours = forecastHours;
            this.projectedTransactions = projectedTransactions;
            this.projectedValue = projectedValue;
            this.projectedResponseTime = projectedResponseTime;
            this.confidence = confidence;
            this.currentData = currentData;
        }

        // Getters
        public Instant getGeneratedAt() { return generatedAt; }
        public int getForecastHours() { return forecastHours; }
        public double getProjectedTransactions() { return projectedTransactions; }
        public double getProjectedValue() { return projectedValue; }
        public double getProjectedResponseTime() { return projectedResponseTime; }
        public double getConfidence() { return confidence; }
        public DashboardData getCurrentData() { return currentData; }
    }
}