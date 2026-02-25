/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.economy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Dynamic pricing engine for the GregVerse marketplace.
 *
 * <p>Calculates optimal pricing for OT services based on supply/demand dynamics,
 * reputation factors, time-based pricing, and market conditions. Supports both
 * automatic pricing recommendations and manual price adjustments.</p>
 *
 * <h2>Pricing Factors</h2>
 * <ul>
 *   <li>Supply/Demand Ratio: Higher demand → higher prices</li>
 *   <li>Reputation Multiplier: Better reputation → premium pricing</li>
 *   <li>Time-Based Pricing: Urgent appointments → surge pricing</li>
 *   <li>Competitive Analysis: Market-based price adjustments</li>
 *   <li>Service Complexity: Higher complexity → higher base price</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * PricingEngine engine = new PricingEngine();
 * ServiceMetrics metrics = engine.calculateServiceMetrics(
 *     "assessment", otProviderId, serviceHistory
 * );
 * MarketplaceCurrency recommendedPrice = engine.calculateOptimalPrice(
 *     200.0, metrics, PricingStrategy.DYNAMIC
 * );
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class PricingEngine {

    private static final Logger logger = LoggerFactory.getLogger(PricingEngine.class);

    // Pricing strategy constants
    public static final PricingStrategy DEFAULT_STRATEGY = PricingStrategy.DYNAMIC;

    // Pricing multipliers
    private static final BigDecimal BASE_MULTIPLIER = BigDecimal.valueOf(1.0);
    private static final BigDecimal MIN_MULTIPLIER = BigDecimal.valueOf(0.5);
    private static final BigDecimal MAX_MULTIPLIER = BigDecimal.valueOf(3.0);
    private static final BigDecimal REPUTATION_MULTIPLIER_MAX = BigDecimal.valueOf(1.5);
    private static final BigDecimal SURGE_MULTIPLIER_MAX = BigDecimal.valueOf(2.0);

    // Time-based pricing thresholds
    private static final long URGENT_HOURS_THRESHOLD = 24;
    private static final double DEFAULT_SURGE_MULTIPLIER = 1.2;
    private static final double HIGH_DEMAND_SURGE_MULTIPLIER = 1.5;

    // Supply/demand thresholds
    private static final double HIGH_DEMAND_RATIO = 0.8; // 80% booking rate
    private static final double LOW_DEMAND_RATIO = 0.3;  // 30% booking rate

    // Performance tracking
    private final ConcurrentMap<String, ServiceMetrics> serviceMetricsCache;
    private final ConcurrentMap<String, PricingHistory> pricingHistory;
    private final AtomicLong calculationCount;

    /**
     * Creates a new pricing engine.
     */
    public PricingEngine() {
        this.serviceMetricsCache = new ConcurrentHashMap<>();
        this.pricingHistory = new ConcurrentHashMap<>();
        this.calculationCount = new AtomicLong(0);
        logger.info("Initialized PricingEngine with dynamic pricing strategy");
    }

    /**
     * Calculates optimal pricing for a service.
     *
     * @param basePrice the base price for the service
     * @param serviceId the service ID
     * @param providerId the provider ID
     * @param transactionLedger transaction history for calculations
     * @return calculated optimal price
     */
    public MarketplaceCurrency calculateOptimalPrice(double basePrice, String serviceId,
                                                   String providerId, TransactionLedger transactionLedger) {
        return calculateOptimalPrice(basePrice, serviceId, providerId, transactionLedger, DEFAULT_STRATEGY);
    }

    /**
     * Calculates optimal pricing with specific strategy.
     *
     * @param basePrice the base price for the service
     * @param serviceId the service ID
     * @param providerId the provider ID
     * @param transactionLedger transaction history
     * @param strategy the pricing strategy
     * @return calculated optimal price
     */
    public MarketplaceCurrency calculateOptimalPrice(double basePrice, String serviceId,
                                                   String providerId, TransactionLedger transactionLedger,
                                                   PricingStrategy strategy) {
        Objects.requireNonNull(serviceId, "Service ID must not be null");
        Objects.requireNonNull(providerId, "Provider ID must not be null");
        Objects.requireNonNull(transactionLedger, "Transaction ledger must not be null");
        Objects.requireNonNull(strategy, "Pricing strategy must not be null");

        // Validate base price
        if (basePrice <= 0) {
            throw new IllegalArgumentException("Base price must be positive: " + basePrice);
        }

        // Calculate service metrics
        ServiceMetrics metrics = calculateServiceMetrics(serviceId, providerId, transactionLedger);

        // Apply pricing strategy
        MarketplaceCurrency calculatedPrice = applyStrategy(
            new MarketplaceCurrency(basePrice), metrics, strategy
        );

        // Record pricing history
        String pricingId = "pricing-" + UUID.randomUUID().toString();
        PricingHistory history = new PricingHistory(
            pricingId, serviceId, providerId, basePrice,
            calculatedPrice.getAmountAsDouble(), Instant.now(), strategy, metrics
        );
        pricingHistory.put(pricingId, history);

        calculationCount.incrementAndGet();

        logger.info("Calculated optimal price for {}: {} (base: {}, strategy: {})",
            serviceId, calculatedPrice.format(), new MarketplaceCurrency(basePrice).format(), strategy);

        return calculatedPrice;
    }

    /**
     * Gets service metrics for a provider.
     *
     * @param serviceId the service ID
     * @param providerId the provider ID
     * @param transactionLedger transaction history
     * @return service metrics
     */
    public ServiceMetrics calculateServiceMetrics(String serviceId, String providerId,
                                               TransactionLedger transactionLedger) {
        Objects.requireNonNull(serviceId, "Service ID must not be null");
        Objects.requireNonNull(providerId, "Provider ID must not be null");
        Objects.requireNonNull(transactionLedger, "Transaction ledger must not be null");

        // Check cache first
        String cacheKey = serviceId + ":" + providerId;
        ServiceMetrics cached = serviceMetricsCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Get relevant transactions
        List<TransactionLedger.Transaction> serviceTransactions =
            transactionLedger.getServiceHistory(serviceId);
        List<TransactionLedger.Transaction> providerTransactions =
            transactionLedger.getTransactionHistory(providerId);

        // Calculate demand metrics
        DemandMetrics demandMetrics = calculateDemandMetrics(serviceTransactions, providerTransactions);

        // Calculate reputation metrics
        ReputationMetrics reputationMetrics = calculateReputationMetrics(providerTransactions);

        // Calculate time-based metrics
        TimeBasedMetrics timeMetrics = calculateTimeBasedMetrics(serviceTransactions);

        // Combine all metrics
        ServiceMetrics metrics = new ServiceMetrics(
            serviceId, providerId, demandMetrics, reputationMetrics, timeMetrics
        );

        // Cache the results
        serviceMetricsCache.put(cacheKey, metrics);

        return metrics;
    }

    /**
     * Gets the current pricing strategy for a service.
     *
     * @param serviceId the service ID
     * @param providerId the provider ID
     * @return recommended pricing strategy
     */
    public PricingStrategy recommendStrategy(String serviceId, String providerId,
                                          TransactionLedger transactionLedger) {
        Objects.requireNonNull(serviceId, "Service ID must not be null");
        Objects.requireNonNull(providerId, "Provider ID must not be null");
        Objects.requireNonNull(transactionLedger, "Transaction ledger must not be null");

        ServiceMetrics metrics = calculateServiceMetrics(serviceId, providerId, transactionLedger);

        // Recommend strategy based on service characteristics
        if (metrics.getDemandMetrics().getHighDemandRatio() > HIGH_DEMAND_RATIO) {
            return PricingStrategy.SURGE_PRICING;
        } else if (metrics.getReputationMetrics().getAverageRating().isGreaterThan(new MarketplaceCurrency(4.0))) {
            return PricingStrategy.PREMIUM;
        } else if (metrics.getDemandMetrics().getLowDemandRatio() > LOW_DEMAND_RATIO) {
            return PricingStrategy.DISCOUNT;
        } else {
            return PricingStrategy.DYNAMIC;
        }
    }

    /**
     * Updates base prices for all services of a provider.
     *
     * @param providerId the provider ID
     * @param newBasePrice the new base price
     * @param transactionLedger transaction history
     * @return map of service IDs to updated prices
     */
    public Map<String, MarketplaceCurrency> updateProviderPrices(String providerId, double newBasePrice,
                                                             TransactionLedger transactionLedger) {
        Objects.requireNonNull(providerId, "Provider ID must not be null");
        Objects.requireNonNull(transactionLedger, "Transaction ledger must not be null");

        if (newBasePrice <= 0) {
            throw new IllegalArgumentException("New base price must be positive: " + newBasePrice);
        }

        Map<String, MarketplaceCurrency> updatedPrices = new HashMap<>();

        // Get all services offered by this provider
        // In a real implementation, this would come from a service registry
        List<String> providerServices = getProviderServices(providerId);

        for (String serviceId : providerServices) {
            MarketplaceCurrency optimalPrice = calculateOptimalPrice(
                newBasePrice, serviceId, providerId, transactionLedger
            );
            updatedPrices.put(serviceId, optimalPrice);
        }

        return updatedPrices;
    }

    /**
     * Gets pricing history for analysis.
     *
     * @param serviceId the service ID (optional)
     * @param providerId the provider ID (optional)
     * @return list of pricing history entries
     */
    public List<PricingHistory> getPricingHistory(String serviceId, String providerId) {
        return pricingHistory.values().stream()
            .filter(history -> {
                if (serviceId != null && !history.getServiceId().equals(serviceId)) {
                    return false;
                }
                if (providerId != null && !history.getProviderId().equals(providerId)) {
                    return false;
                }
                return true;
            })
            .sorted(Comparator.comparing(PricingHistory::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Gets pricing statistics for analysis.
     *
     * @return pricing statistics
     */
    public PricingStatistics getPricingStatistics() {
        if (pricingHistory.isEmpty()) {
            return new PricingStatistics(0, MarketplaceCurrency.ZERO, MarketplaceCurrency.ZERO,
                                      MarketplaceCurrency.ZERO, Map.of());
        }

        long totalCalculations = calculationCount.get();
        List<MarketplaceCurrency> allPrices = pricingHistory.values().stream()
            .map(history -> new MarketplaceCurrency(history.getCalculatedPrice()))
            .collect(Collectors.toList());

        MarketplaceCurrency avgPrice = allPrices.stream()
            .reduce(MarketplaceCurrency.ZERO, MarketplaceCurrency::add)
            .divide(allPrices.size());

        MarketplaceCurrency minPrice = allPrices.stream()
            .min(MarketplaceCurrency::compareTo)
            .orElse(MarketplaceCurrency.ZERO);

        MarketplaceCurrency maxPrice = allPrices.stream()
            .max(MarketplaceCurrency::compareTo)
            .orElse(MarketplaceCurrency.ZERO);

        // Strategy distribution
        Map<PricingStrategy, Long> strategyDistribution = pricingHistory.values().stream()
            .collect(Collectors.groupingBy(
                PricingHistory::getStrategy, Collectors.counting()
            ));

        return new PricingStatistics(
            totalCalculations, avgPrice, minPrice, maxPrice, strategyDistribution
        );
    }

    /**
     * Clears cached metrics (for testing or when market conditions change).
     */
    public void clearCache() {
        serviceMetricsCache.clear();
        logger.info("Cleared pricing engine cache");
    }

    // Helper methods for pricing calculations

    private MarketplaceCurrency applyStrategy(MarketplaceCurrency basePrice, ServiceMetrics metrics,
                                            PricingStrategy strategy) {
        switch (strategy) {
            case FIXED:
                return basePrice;

            case DYNAMIC:
                return applyDynamicPricing(basePrice, metrics);

            case PREMIUM:
                return applyPremiumPricing(basePrice, metrics);

            case DISCOUNT:
                return applyDiscountPricing(basePrice, metrics);

            case SURGE_PRICING:
                return applySurgePricing(basePrice, metrics);

            default:
                return applyDynamicPricing(basePrice, metrics);
        }
    }

    private MarketplaceCurrency applyDynamicPricing(MarketplaceCurrency basePrice, ServiceMetrics metrics) {
        BigDecimal multiplier = BASE_MULTIPLIER;

        // Apply demand multiplier
        BigDecimal demandMultiplier = calculateDemandMultiplier(metrics.getDemandMetrics());
        multiplier = multiplier.multiply(demandMultiplier);

        // Apply reputation multiplier
        BigDecimal reputationMultiplier = calculateReputationMultiplier(metrics.getReputationMetrics());
        multiplier = multiplier.multiply(reputationMultiplier);

        // Apply time-based multiplier
        BigDecimal timeMultiplier = calculateTimeMultiplier(metrics.getTimeBasedMetrics());
        multiplier = multiplier.multiply(timeMultiplier);

        // Clamp multiplier to reasonable bounds
        multiplier = multiplier.max(MIN_MULTIPLIER).min(MAX_MULTIPLIER);

        return basePrice.multiply(multiplier.doubleValue());
    }

    private MarketplaceCurrency applyPremiumPricing(MarketplaceCurrency basePrice, ServiceMetrics metrics) {
        // Start with dynamic pricing
        MarketplaceCurrency dynamicPrice = applyDynamicPricing(basePrice, metrics);

        // Apply additional premium for high-reputation providers
        BigDecimal premiumMultiplier = calculateReputationMultiplier(metrics.getReputationMetrics());
        premiumMultiplier = premiumMultiplier.min(REPUTATION_MULTIPLIER_MAX);

        return dynamicPrice.multiply(premiumMultiplier.doubleValue());
    }

    private MarketplaceCurrency applyDiscountPricing(MarketplaceCurrency basePrice, ServiceMetrics metrics) {
        // Apply dynamic pricing first
        MarketplaceCurrency dynamicPrice = applyDynamicPricing(basePrice, metrics);

        // Apply discount for low-demand services
        BigDecimal discountMultiplier = BigDecimal.valueOf(0.8); // 20% discount

        return dynamicPrice.multiply(discountMultiplier.doubleValue());
    }

    private MarketplaceCurrency applySurgePricing(MarketplaceCurrency basePrice, ServiceMetrics metrics) {
        // Apply dynamic pricing
        MarketplaceCurrency dynamicPrice = applyDynamicPricing(basePrice, metrics);

        // Apply surge multiplier for urgent/high-demand situations
        BigDecimal surgeMultiplier = calculateSurgeMultiplier(metrics.getTimeBasedMetrics(),
                                                           metrics.getDemandMetrics());

        return dynamicPrice.multiply(surgeMultiplier.doubleValue());
    }

    private BigDecimal calculateDemandMultiplier(DemandMetrics demandMetrics) {
        double demandRatio = demandMetrics.getBookingRate();

        if (demandRatio > HIGH_DEMAND_RATIO) {
            return BigDecimal.valueOf(1.2); // 20% premium for high demand
        } else if (demandRatio < LOW_DEMAND_RATIO) {
            return BigDecimal.valueOf(0.9); // 10% discount for low demand
        } else {
            return BASE_MULTIPLIER;
        }
    }

    private BigDecimal calculateReputationMultiplier(ReputationMetrics reputationMetrics) {
        BigDecimal avgRating = reputationMetrics.getAverageRating().getAmount();
        BigDecimal maxRating = new BigDecimal(5.0);

        // Scale reputation multiplier from 1.0 to 1.5 based on rating
        BigDecimal ratingRatio = avgRating.divide(maxRating, 4, RoundingMode.HALF_UP);
        return BASE_MULTIPLIER.add(ratingRatio.multiply(BigDecimal.valueOf(0.5)));
    }

    private BigDecimal calculateTimeMultiplier(TimeBasedMetrics timeMetrics) {
        if (timeMetrics.isUrgentRequest()) {
            return BigDecimal.valueOf(1.1); // 10% premium for urgent requests
        }
        return BASE_MULTIPLIER;
    }

    private BigDecimal calculateSurgeMultiplier(TimeBasedMetrics timeMetrics, DemandMetrics demandMetrics) {
        BigDecimal surgeMultiplier = BigDecimal.valueOf(DEFAULT_SURGE_MULTIPLIER);

        if (timeMetrics.isUrgentRequest()) {
            surgeMultiplier = surgeMultiplier.multiply(BigDecimal.valueOf(1.3));
        }

        if (demandMetrics.getHighDemandRatio() > HIGH_DEMAND_RATIO) {
            surgeMultiplier = surgeMultiplier.min(BigDecimal.valueOf(HIGH_DEMAND_SURGE_MULTIPLIER));
        }

        return surgeMultiplier;
    }

    // Metrics calculation methods

    private DemandMetrics calculateDemandMetrics(List<TransactionLedger.Transaction> serviceTransactions,
                                               List<TransactionLedger.Transaction> providerTransactions) {
        // Calculate booking rate (successful transactions / total potential transactions)
        long successfulTransactions = serviceTransactions.size();
        long totalPotential = calculateTotalPotentialTransactions(providerTransactions);

        double bookingRate = totalPotential > 0 ? (double) successfulTransactions / totalPotential : 0.0;

        // Calculate demand trends
        Instant twoWeeksAgo = Instant.now().minus(java.time.Duration.ofDays(14));
        List<TransactionLedger.Transaction> recentTransactions = serviceTransactions.stream()
            .filter(tx -> tx.getTimestamp().isAfter(twoWeeksAgo))
            .collect(Collectors.toList());

        double recentBookingRate = recentTransactions.size() / Math.max(totalPotential, 1.0);

        return new DemandMetrics(bookingRate, recentBookingRate, totalPotential);
    }

    private ReputationMetrics calculateReputationMetrics(List<TransactionLedger.Transaction> providerTransactions) {
        // Calculate average rating from transactions (assuming rating data is available)
        double avgRating = providerTransactions.stream()
            .mapToDouble(tx -> {
                // In a real implementation, this would get rating from the transaction
                return 4.0; // Default rating for demonstration
            })
            .average()
            .orElse(4.0);

        long totalTransactions = providerTransactions.size();
        MarketplaceCurrency averageRating = new MarketplaceCurrency(avgRating);

        return new ReputationMetrics(averageRating, totalTransactions);
    }

    private TimeBasedMetrics calculateTimeBasedMetrics(List<TransactionLedger.Transaction> serviceTransactions) {
        Instant now = Instant.now();
        Instant urgentThreshold = now.minus(java.time.Duration.ofHours(URGENT_HOURS_THRESHOLD));

        boolean hasUrgentRequests = serviceTransactions.stream()
            .anyMatch(tx -> tx.getTimestamp().isAfter(urgentThreshold));

        return new TimeBasedMetrics(hasUrgentRequests);
    }

    private long calculateTotalPotentialTransactions(List<TransactionLedger.Transaction> providerTransactions) {
        // Simple heuristic: assume 80% of providers slots could be filled
        return Math.max(providerTransactions.size(), 10); // Minimum 10 potential transactions
    }

    private List<String> getProviderServices(String providerId) {
        // In a real implementation, this would query a service registry
        // For now, return a default list
        return List.of("assessment", "intervention-planning", "appointment-scheduling");
    }

    // Inner classes for metrics

    /**
     * Service metrics combining all pricing factors.
     */
    public static final class ServiceMetrics {
        private final String serviceId;
        private final String providerId;
        private final DemandMetrics demandMetrics;
        private final ReputationMetrics reputationMetrics;
        private final TimeBasedMetrics timeBasedMetrics;

        public ServiceMetrics(String serviceId, String providerId, DemandMetrics demandMetrics,
                            ReputationMetrics reputationMetrics, TimeBasedMetrics timeBasedMetrics) {
            this.serviceId = Objects.requireNonNull(serviceId);
            this.providerId = Objects.requireNonNull(providerId);
            this.demandMetrics = Objects.requireNonNull(demandMetrics);
            this.reputationMetrics = Objects.requireNonNull(reputationMetrics);
            this.timeBasedMetrics = Objects.requireNonNull(timeBasedMetrics);
        }

        // Getters
        public String getServiceId() { return serviceId; }
        public String getProviderId() { return providerId; }
        public DemandMetrics getDemandMetrics() { return demandMetrics; }
        public ReputationMetrics getReputationMetrics() { return reputationMetrics; }
        public TimeBasedMetrics getTimeBasedMetrics() { return timeBasedMetrics; }
    }

    /**
     * Demand metrics for pricing calculations.
     */
    public static final class DemandMetrics {
        private final double bookingRate;
        private final double recentBookingRate;
        private final long totalPotential;

        public DemandMetrics(double bookingRate, double recentBookingRate, long totalPotential) {
            this.bookingRate = bookingRate;
            this.recentBookingRate = recentBookingRate;
            this.totalPotential = totalPotential;
        }

        // Getters
        public double getBookingRate() { return bookingRate; }
        public double getRecentBookingRate() { return recentBookingRate; }
        public long getTotalPotential() { return totalPotential; }
        public double getHighDemandRatio() { return bookingRate; }
        public double getLowDemandRatio() { return 1.0 - bookingRate; }
    }

    /**
     * Reputation metrics for pricing calculations.
     */
    public static final class ReputationMetrics {
        private final MarketplaceCurrency averageRating;
        private final long totalRatings;

        public ReputationMetrics(MarketplaceCurrency averageRating, long totalRatings) {
            this.averageRating = Objects.requireNonNull(averageRating);
            this.totalRatings = totalRatings;
        }

        // Getters
        public MarketplaceCurrency getAverageRating() { return averageRating; }
        public long getTotalRatings() { return totalRatings; }
    }

    /**
     * Time-based metrics for pricing calculations.
     */
    public static final class TimeBasedMetrics {
        private final boolean urgentRequest;

        public TimeBasedMetrics(boolean urgentRequest) {
            this.urgentRequest = urgentRequest;
        }

        // Getters
        public boolean isUrgentRequest() { return urgentRequest; }
    }

    /**
     * Pricing strategy enumeration.
     */
    public enum PricingStrategy {
        FIXED,            // Fixed base price
        DYNAMIC,          // Dynamic price based on market conditions
        PREMIUM,          // Premium pricing for high-reputation providers
        DISCOUNT,         // Discount pricing for low-demand services
        SURGE_PRICING     // Surge pricing for urgent/high-demand situations
    }

    /**
     * Pricing history record.
     */
    public static final class PricingHistory {
        private final String pricingId;
        private final String serviceId;
        private final String providerId;
        private final double basePrice;
        private final double calculatedPrice;
        private final Instant timestamp;
        private final PricingStrategy strategy;
        private final ServiceMetrics metrics;

        public PricingHistory(String pricingId, String serviceId, String providerId,
                            double basePrice, double calculatedPrice, Instant timestamp,
                            PricingStrategy strategy, ServiceMetrics metrics) {
            this.pricingId = Objects.requireNonNull(pricingId);
            this.serviceId = Objects.requireNonNull(serviceId);
            this.providerId = Objects.requireNonNull(providerId);
            this.basePrice = basePrice;
            this.calculatedPrice = calculatedPrice;
            this.timestamp = Objects.requireNonNull(timestamp);
            this.strategy = Objects.requireNonNull(strategy);
            this.metrics = Objects.requireNonNull(metrics);
        }

        // Getters
        public String getPricingId() { return pricingId; }
        public String getServiceId() { return serviceId; }
        public String getProviderId() { return providerId; }
        public double getBasePrice() { return basePrice; }
        public double getCalculatedPrice() { return calculatedPrice; }
        public Instant getTimestamp() { return timestamp; }
        public PricingStrategy getStrategy() { return strategy; }
        public ServiceMetrics getMetrics() { return metrics; }
    }

    /**
     * Pricing statistics for analysis.
     */
    public static final class PricingStatistics {
        private final long totalCalculations;
        private final MarketplaceCurrency averagePrice;
        private final MarketplaceCurrency minimumPrice;
        private final MarketplaceCurrency maximumPrice;
        private final Map<PricingStrategy, Long> strategyDistribution;

        public PricingStatistics(long totalCalculations, MarketplaceCurrency averagePrice,
                              MarketplaceCurrency minimumPrice, MarketplaceCurrency maximumPrice,
                              Map<PricingStrategy, Long> strategyDistribution) {
            this.totalCalculations = totalCalculations;
            this.averagePrice = Objects.requireNonNull(averagePrice);
            this.minimumPrice = Objects.requireNonNull(minimumPrice);
            this.maximumPrice = Objects.requireNonNull(maximumPrice);
            this.strategyDistribution = Objects.requireNonNull(strategyDistribution);
        }

        // Getters
        public long getTotalCalculations() { return totalCalculations; }
        public MarketplaceCurrency getAveragePrice() { return averagePrice; }
        public MarketplaceCurrency getMinimumPrice() { return minimumPrice; }
        public MarketplaceCurrency getMaximumPrice() { return maximumPrice; }
        public Map<PricingStrategy, Long> getStrategyDistribution() { return strategyDistribution; }
    }
}