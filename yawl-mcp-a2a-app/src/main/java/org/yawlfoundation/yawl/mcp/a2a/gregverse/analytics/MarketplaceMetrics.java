package org.yawlfoundation.yawl.mcp.a2a.gregverse.analytics;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Real-time metric aggregations for GregVerse marketplace
 */
public class MarketplaceMetrics {

    private final Map<String, ActiveProvider> activeProviders = new ConcurrentHashMap<>();
    private final Map<String, ActiveConsumer> activeConsumers = new ConcurrentHashMap<>();
    private final AtomicLong totalTransactions = new AtomicLong(0);
    private final DoubleAdder totalTransactionValue = new DoubleAdder();
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final Map<String, CategoryMetrics> categoryMetrics = new ConcurrentHashMap<>();
    private final Map<Integer, Long> ratingDistribution = new ConcurrentHashMap<>();
    private final Map<String, Long> geographicDistribution = new ConcurrentHashMap<>();

    public void registerProviderActivity(String providerId, Instant timestamp) {
        activeProviders.compute(providerId, (k, v) ->
            v == null ? new ActiveProvider(providerId, timestamp) : v.updateActivity(timestamp));
    }

    public void registerConsumerActivity(String consumerId, Instant timestamp) {
        activeConsumers.compute(consumerId, (k, v) ->
            v == null ? new ActiveConsumer(consumerId, timestamp) : v.updateActivity(timestamp));
    }

    public void recordTransaction(String providerId, String consumerId, double value,
                                 String category, long responseTimeMs, int rating,
                                 String location) {
        totalTransactions.incrementAndGet();
        totalTransactionValue.add(value);
        totalResponseTime.addAndGet(responseTimeMs);

        // Update category metrics
        categoryMetrics.compute(category, (k, v) ->
            v == null ? new CategoryMetrics(category).recordTransaction(value, responseTimeMs)
                     : v.recordTransaction(value, responseTimeMs));

        // Update rating distribution
        ratingDistribution.merge(rating, 1L, Long::sum);

        // Update geographic distribution
        if (location != null && !location.isEmpty()) {
            geographicDistribution.merge(location, 1L, Long::sum);
        }

        // Update activity
        registerProviderActivity(providerId, Instant.now());
        registerConsumerActivity(consumerId, Instant.now());
    }

    public void pruneInactiveProviders(Instant cutoff) {
        activeProviders.entrySet().removeIf(e -> e.getValue().getLastActivity().isBefore(cutoff));
    }

    public void pruneInactiveConsumers(Instant cutoff) {
        activeConsumers.entrySet().removeIf(e -> e.getValue().getLastActivity().isBefore(cutoff));
    }

    // Getters for various metrics
    public int getActiveProviderCount() {
        return activeProviders.size();
    }

    public int getActiveConsumerCount() {
        return activeConsumers.size();
    }

    public long getTotalTransactionCount() {
        return totalTransactions.get();
    }

    public double getAverageTransactionValue() {
        long count = totalTransactions.get();
        return count == 0 ? 0 : totalTransactionValue.sum() / count;
    }

    public double getAverageResponseTime() {
        long count = totalTransactions.get();
        return count == 0 ? 0 : totalResponseTime.get() / (double) count;
    }

    public Map<String, Double> getAveragePricesByCategory() {
        return categoryMetrics.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getAveragePrice()
            ));
    }

    public Map<Integer, Double> getRatingDistributionPercentages() {
        long total = ratingDistribution.values().stream().mapToLong(Long::longValue).sum();
        if (total == 0) return Map.of();

        return ratingDistribution.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue() * 100.0 / total
            ));
    }

    public Map<String, Long> getTransactionCountsByCategory() {
        return categoryMetrics.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getTransactionCount()
            ));
    }

    public Map<String, Long> getGeographicDistribution() {
        return new HashMap<>(geographicDistribution);
    }

    public List<MetricSnapshot> getSnapshot() {
        MetricSnapshot snapshot = new MetricSnapshot(
            Instant.now(),
            getActiveProviderCount(),
            getActiveConsumerCount(),
            getTotalTransactionCount(),
            getAverageTransactionValue(),
            getAverageResponseTime(),
            getAveragePricesByCategory(),
            getRatingDistributionPercentages(),
            getTransactionCountsByCategory(),
            getGeographicDistribution()
        );

        return Collections.singletonList(snapshot);
    }

    // Nested classes for tracking activities
    private static class ActiveProvider {
        private final String providerId;
        private Instant lastActivity;

        public ActiveProvider(String providerId, Instant lastActivity) {
            this.providerId = providerId;
            this.lastActivity = lastActivity;
        }

        public ActiveProvider updateActivity(Instant newActivity) {
            this.lastActivity = newActivity;
            return this;
        }

        public Instant getLastActivity() {
            return lastActivity;
        }
    }

    private static class ActiveConsumer {
        private final String consumerId;
        private Instant lastActivity;

        public ActiveConsumer(String consumerId, Instant lastActivity) {
            this.consumerId = consumerId;
            this.lastActivity = lastActivity;
        }

        public ActiveConsumer updateActivity(Instant newActivity) {
            this.lastActivity = newActivity;
            return this;
        }

        public Instant getLastActivity() {
            return lastActivity;
        }
    }

    private static class CategoryMetrics {
        private final String category;
        private final AtomicLong transactionCount = new AtomicLong(0);
        private final DoubleAdder totalValue = new DoubleAdder();
        private final LongAdder totalResponseTime = new LongAdder();

        public CategoryMetrics(String category) {
            this.category = category;
        }

        public CategoryMetrics recordTransaction(double value, long responseTimeMs) {
            transactionCount.incrementAndGet();
            totalValue.add(value);
            totalResponseTime.add(responseTimeMs);
            return this;
        }

        public long getTransactionCount() {
            return transactionCount.get();
        }

        public double getAveragePrice() {
            long count = transactionCount.get();
            return count == 0 ? 0 : totalValue.sum() / count;
        }

        public double getAverageResponseTime() {
            long count = transactionCount.get();
            return count == 0 ? 0 : totalResponseTime.sum() / (double) count;
        }
    }

    public record MetricSnapshot(
        Instant timestamp,
        int activeProviderCount,
        int activeConsumerCount,
        long totalTransactionCount,
        double averageTransactionValue,
        double averageResponseTime,
        Map<String, Double> averagePricesByCategory,
        Map<Integer, Double> ratingDistribution,
        Map<String, Long> transactionCountsByCategory,
        Map<String, Long> geographicDistribution
    ) {}
}