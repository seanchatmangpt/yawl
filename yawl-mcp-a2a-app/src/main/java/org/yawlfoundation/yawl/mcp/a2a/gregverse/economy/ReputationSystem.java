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
 * Reputation system for the GregVerse marketplace.
 *
 * <p>Manages agent ratings, trust scores, and reputation metrics for service providers (OTs)
 * and consumers. Supports multi-dimensional reputation including quality, reliability,
 * communication, and overall satisfaction scores.</p>
 *
 * <h2>Reputation Dimensions</h2>
 * <ul>
 *   <li>Quality Rating: Service quality and effectiveness</li>
 *   <li>Reliability: On-time delivery and consistency</li>
 *   <li>Communication: Responsiveness and clarity</li>
 *   <li>Overall Satisfaction: General client satisfaction</li>
 *   <li>Trust Score: Aggregated reputation score (0-100)</li>
 * </ul>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Multi-dimensional rating system</li>
 *   <li>Weighted reputation calculations</li>
 *   <li>Time-decay for recent emphasis</li>
 *   <li>Trust-based recommendations</li>
 *   <li>Reputation history tracking</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ReputationSystem system = new ReputationSystem();
 * // Add a rating after service completion
 * system.addRating("provider-001", "client-002", "assessment", 5, 4, 5, 5);
 * // Get provider's reputation
 * ProviderReputation reputation = system.getProviderReputation("provider-001");
 * // Get recommended providers
 * List<ProviderSummary> recommendations = system.getTopProviders("assessment", 3);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class ReputationSystem {

    private static final Logger logger = LoggerFactory.getLogger(ReputationSystem.class);

    // Rating constants
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final BigDecimal MIN_TRUST_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_TRUST_SCORE = new BigDecimal(100);

    // Time decay constants (ratings from last 30 days weighted more heavily)
    private static final long DECAY_PERIOD_DAYS = 30;
    private static final BigDecimal DECAY_WEIGHT = new BigDecimal(0.7); // 70% weight to recent ratings

    // Weighting for reputation dimensions
    private static final Map<ReputationDimension, BigDecimal> DIMENSION_WEIGHTS = Map.of(
        ReputationDimension.QUALITY, new BigDecimal(0.4),      // 40% weight
        ReputationDimension.RELIABILITY, new BigDecimal(0.3),   // 30% weight
        ReputationDimension.COMMUNICATION, new BigDecimal(0.2), // 20% weight
        ReputationDimension.SATISFACTION, new BigDecimal(0.1)   // 10% weight
    );

    // Data storage
    private final ConcurrentMap<String, ProviderReputation> providerReputations;
    private final ConcurrentMap<String, ClientReputation> clientReputations;
    private final ConcurrentMap<String, ServiceRating> serviceRatings;
    private final ConcurrentMap<String, ReputationHistory> reputationHistory;

    // Performance optimization
    private final AtomicLong ratingCount;
    private volatile Instant lastUpdated;

    /**
     * Creates a new reputation system.
     */
    public ReputationSystem() {
        this.providerReputations = new ConcurrentHashMap<>();
        this.clientReputations = new ConcurrentHashMap<>();
        this.serviceRatings = new ConcurrentHashMap<>();
        this.reputationHistory = new ConcurrentHashMap<>();
        this.ratingCount = new AtomicLong(0);
        this.lastUpdated = Instant.now();
        logger.info("Initialized ReputationSystem with multi-dimensional ratings");
    }

    /**
     * Adds a multi-dimensional rating for a service provider.
     *
     * @param providerId the provider ID
     * @param clientId the client ID
     * @param serviceId the service ID
     * @param qualityRating service quality rating (1-5)
     * @param reliabilityRating reliability rating (1-5)
     * @param communicationRating communication rating (1-5)
     * @param satisfactionRating satisfaction rating (1-5)
     * @return the updated provider reputation
     * @throws IllegalArgumentException if ratings are invalid
     */
    public ProviderReputation addRating(String providerId, String clientId, String serviceId,
                                      int qualityRating, int reliabilityRating,
                                      int communicationRating, int satisfactionRating) {
        Objects.requireNonNull(providerId, "Provider ID must not be null");
        Objects.requireNonNull(clientId, "Client ID must not be null");
        Objects.requireNonNull(serviceId, "Service ID must not be null");

        // Validate ratings
        validateRating(qualityRating, "Quality");
        validateRating(reliabilityRating, "Reliability");
        validateRating(communicationRating, "Communication");
        validateRating(satisfactionRating, "Satisfaction");

        // Create multi-dimensional rating
        MultiDimensionalRating rating = new MultiDimensionalRating(
            qualityRating, reliabilityRating, communicationRating, satisfactionRating
        );

        // Create service rating record
        String ratingId = "rating-" + UUID.randomUUID().toString();
        Instant timestamp = Instant.now();
        ServiceRating serviceRating = new ServiceRating(
            ratingId, providerId, clientId, serviceId, rating, timestamp
        );

        // Store the rating
        serviceRatings.put(ratingId, serviceRating);

        // Update provider reputation
        ProviderReputation providerRep = providerReputations.computeIfAbsent(
            providerId, id -> new ProviderReputation(id)
        );
        ProviderReputation updatedRep = providerRep.addRating(serviceRating);

        // Update client reputation
        ClientReputation clientRep = clientReputations.computeIfAbsent(
            clientId, id -> new ClientReputation(id)
        );
        clientRep.addRatingGiven(serviceRating);

        // Record reputation history
        ReputationHistory history = new ReputationHistory(
            providerId, updatedRep.getTrustScore(), timestamp
        );
        reputationHistory.put(ratingId, history);

        // Update counters
        ratingCount.incrementAndGet();
        lastUpdated = Instant.now();

        logger.info("Added multi-dimensional rating for provider {}: quality={}, reliability={}, " +
                   "communication={}, satisfaction={}", providerId, qualityRating, reliabilityRating,
                   communicationRating, satisfactionRating);

        return updatedRep;
    }

    /**
     * Gets a provider's current reputation.
     *
     * @param providerId the provider ID
     * @return provider reputation, or empty if not found
     */
    public Optional<ProviderReputation> getProviderReputation(String providerId) {
        Objects.requireNonNull(providerId, "Provider ID must not be null");
        return Optional.ofNullable(providerReputations.get(providerId));
    }

    /**
     * Gets a client's reputation.
     *
     * @param clientId the client ID
     * @return client reputation, or empty if not found
     */
    public Optional<ClientReputation> getClientReputation(String clientId) {
        Objects.requireNonNull(clientId, "Client ID must not be null");
        return Optional.ofNullable(clientReputations.get(clientId));
    }

    /**
     * Gets all ratings for a provider.
     *
     * @param providerId the provider ID
     * @return list of service ratings
     */
    public List<ServiceRating> getProviderRatings(String providerId) {
        Objects.requireNonNull(providerId, "Provider ID must not be null");
        return serviceRatings.values().stream()
            .filter(rating -> rating.getProviderId().equals(providerId))
            .sorted(Comparator.comparing(ServiceRating::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Gets all ratings given by a client.
     *
     * @param clientId the client ID
     * @return list of service ratings given by the client
     */
    public List<ServiceRating> getClientGivenRatings(String clientId) {
        Objects.requireNonNull(clientId, "Client ID must not be null");
        return serviceRatings.values().stream()
            .filter(rating -> rating.getClientId().equals(clientId))
            .sorted(Comparator.comparing(ServiceRating::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Gets ratings for a specific service.
     *
     * @param serviceId the service ID
     * @return list of service ratings for the service
     */
    public List<ServiceRating> getServiceRatings(String serviceId) {
        Objects.requireNonNull(serviceId, "Service ID must not be null");
        return serviceRatings.values().stream()
            .filter(rating -> rating.getServiceId().equals(serviceId))
            .sorted(Comparator.comparing(ServiceRating::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Gets top providers for a service based on reputation.
     *
     * @param serviceId the service ID
     * @param limit maximum number of providers to return
     * @return list of provider summaries sorted by trust score
     */
    public List<ProviderSummary> getTopProviders(String serviceId, int limit) {
        Objects.requireNonNull(serviceId, "Service ID must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive: " + limit);
        }

        // Get all ratings for this service
        List<ServiceRating> serviceRatings = getServiceRatings(serviceId);

        // Group ratings by provider
        Map<String, List<ServiceRating>> ratingsByProvider = serviceRatings.stream()
            .collect(Collectors.groupingBy(ServiceRating::getProviderId));

        // Calculate average rating for each provider
        Map<String, ProviderSummary> providerSummaries = ratingsByProvider.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    String providerId = entry.getKey();
                    List<ServiceRating> providerRatings = entry.getValue();

                    ProviderReputation rep = providerReputations.get(providerId);
                    BigDecimal trustScore = rep != null ? rep.getTrustScore() : BigDecimal.ZERO;

                    return new ProviderSummary(
                        providerId,
                        calculateAverageRating(providerRatings),
                        trustScore,
                        providerRatings.size()
                    );
                }
            ));

        // Sort by trust score and return top providers
        return providerSummaries.values().stream()
            .sorted(Comparator.comparing(ProviderSummary::getTrustScore).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Gets recommended providers for a client based on preferences.
     *
     * @param clientId the client ID
     * @param preferredServices list of preferred service types
     * @param minTrustScore minimum trust score threshold
     * @return list of recommended providers
     */
    public List<ProviderSummary> getRecommendedProviders(String clientId, List<String> preferredServices,
                                                        BigDecimal minTrustScore) {
        Objects.requireNonNull(clientId, "Client ID must not be null");
        Objects.requireNonNull(preferredServices, "Preferred services must not be null");
        Objects.requireNonNull(minTrustScore, "Minimum trust score must not be null");

        if (minTrustScore.compareTo(MIN_TRUST_SCORE) < 0 ||
            minTrustScore.compareTo(MAX_TRUST_SCORE) > 0) {
            throw new IllegalArgumentException("Trust score must be between 0 and 100");
        }

        // Get providers who offer the preferred services
        Set<String> eligibleProviders = new HashSet<>();
        for (String serviceId : preferredServices) {
            List<ServiceRating> serviceRatings = getServiceRatings(serviceId);
            serviceRatings.stream()
                .map(ServiceRating::getProviderId)
                .forEach(eligibleProviders::add);
        }

        // Create provider summaries and filter by trust score
        return eligibleProviders.stream()
            .map(providerId -> {
                ProviderReputation rep = providerReputations.get(providerId);
                if (rep == null) return null;

                BigDecimal trustScore = rep.getTrustScore();
                if (trustScore.compareTo(minTrustScore) < 0) return null;

                List<ServiceRating> providerRatings = getProviderRatings(providerId);
                BigDecimal avgRating = calculateAverageRating(providerRatings);

                return new ProviderSummary(
                    providerId,
                    avgRating,
                    trustScore,
                    providerRatings.size()
                );
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(ProviderSummary::getTrustScore).reversed()
                .thenComparing(ProviderSummary::getAverageRating).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Calculates the overall rating for a provider.
     *
     * @param providerId the provider ID
     * @return overall rating (weighted average of dimensions)
     */
    public BigDecimal calculateOverallRating(String providerId) {
        Objects.requireNonNull(providerId, "Provider ID must not be null");

        ProviderReputation rep = providerReputations.get(providerId);
        if (rep == null) {
            return BigDecimal.ZERO;
        }

        // Calculate weighted average of all dimensions
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;

        for (ReputationDimension dimension : ReputationDimension.values()) {
            BigDecimal dimensionScore = rep.getDimensionScore(dimension);
            BigDecimal weight = DIMENSION_WEIGHTS.get(dimension);

            weightedSum = weightedSum.add(dimensionScore.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }

        return totalWeight.compareTo(BigDecimal.ZERO) > 0 ?
            weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Updates a provider's reputation score based on new metrics.
     *
     * @param providerId the provider ID
     * @param newScores new scores for each dimension
     * @return updated provider reputation
     */
    public ProviderReputation updateProviderScores(String providerId,
                                                Map<ReputationDimension, BigDecimal> newScores) {
        Objects.requireNonNull(providerId, "Provider ID must not be null");
        Objects.requireNonNull(newScores, "New scores must not be null");

        ProviderReputation rep = providerReputations.get(providerId);
        if (rep == null) {
            throw new IllegalArgumentException("Provider not found: " + providerId);
        }

        // Apply time decay to existing scores
        ProviderReputation decayedRep = applyTimeDecay(rep);

        // Update with new scores
        ProviderReputation updatedRep = decayedRep.updateScores(newScores);

        // Update storage
        providerReputations.put(providerId, updatedRep);

        lastUpdated = Instant.now();
        logger.info("Updated scores for provider {}: {}", providerId, newScores);

        return updatedRep;
    }

    /**
     * Gets reputation statistics for the marketplace.
     *
     * @return reputation statistics
     */
    public ReputationStatistics getReputationStatistics() {
        long totalProviders = providerReputations.size();
        long totalClients = clientReputations.size();
        long totalRatings = ratingCount.get();

        // Calculate average scores
        BigDecimal avgProviderTrust = providerReputations.values().stream()
            .map(ProviderReputation::getTrustScore)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(totalProviders > 0 ? totalProviders : 1), 2, RoundingMode.HALF_UP);

        BigDecimal avgOverallRating = providerReputations.values().stream()
            .map(rep -> calculateOverallRatingFromMultiDimensional(getLatestRating(rep)))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(totalProviders > 0 ? totalProviders : 1), 2, RoundingMode.HALF_UP);

        // Rating distribution
        Map<Integer, Long> ratingDistribution = new HashMap<>();
        for (int i = MIN_RATING; i <= MAX_RATING; i++) {
            ratingDistribution.put(i, 0L);
        }

        serviceRatings.values().forEach(rating -> {
            int overallRating = calculateOverallRatingFromMultiDimensional(rating.getRating()).intValue();
            ratingDistribution.merge(overallRating, 1L, Long::sum);
        });

        return new ReputationStatistics(
            totalProviders, totalClients, totalRatings,
            avgProviderTrust, avgOverallRating, ratingDistribution
        );
    }

    /**
     * Gets reputation history for a provider.
     *
     * @param providerId the provider ID
     * @return list of reputation history entries
     */
    public List<ReputationHistory> getProviderHistory(String providerId) {
        Objects.requireNonNull(providerId, "Provider ID must not be null");
        return reputationHistory.values().stream()
            .filter(history -> history.getProviderId().equals(providerId))
            .sorted(Comparator.comparing(ReputationHistory::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Validates system integrity.
     *
     * @return true if system is valid
     */
    public boolean validateIntegrity() {
        try {
            // Check consistency between ratings and reputations
            for (ServiceRating rating : serviceRatings.values()) {
                ProviderReputation providerRep = providerReputations.get(rating.getProviderId());
                if (providerRep == null) {
                    logger.error("Integrity check failed: rating for non-existent provider {}",
                                rating.getProviderId());
                    return false;
                }
            }

            // Check trust score bounds
            for (ProviderReputation rep : providerReputations.values()) {
                if (rep.getTrustScore().compareTo(MIN_TRUST_SCORE) < 0 ||
                    rep.getTrustScore().compareTo(MAX_TRUST_SCORE) > 0) {
                    logger.error("Integrity check failed: trust score out of bounds for provider {}",
                                rep.getProviderId());
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            logger.error("Reputation system integrity check failed", e);
            return false;
        }
    }

    /**
     * Clears all reputation data (for testing or reset).
     */
    public void clear() {
        providerReputations.clear();
        clientReputations.clear();
        serviceRatings.clear();
        reputationHistory.clear();
        ratingCount.set(0);
        lastUpdated = Instant.now();
        logger.info("Cleared all reputation data");
    }

    // Helper methods

    private void validateRating(int rating, String dimensionName) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException(
                String.format("%s rating must be between %d and %d: %d",
                    dimensionName, MIN_RATING, MAX_RATING, rating)
            );
        }
    }

    private BigDecimal calculateAverageRating(List<ServiceRating> ratings) {
        if (ratings.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = ratings.stream()
            .map(rating -> calculateOverallRatingFromMultiDimensional(rating.getRating()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(BigDecimal.valueOf(ratings.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateOverallRatingFromMultiDimensional(MultiDimensionalRating rating) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (ReputationDimension dimension : ReputationDimension.values()) {
            BigDecimal score = BigDecimal.valueOf(rating.getDimensionScore(dimension));
            BigDecimal weight = DIMENSION_WEIGHTS.get(dimension);

            weightedSum = weightedSum.add(score.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }

        return totalWeight.compareTo(BigDecimal.ZERO) > 0 ?
            weightedSum.divide(totalWeight, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private ProviderReputation applyTimeDecay(ProviderReputation original) {
        Instant now = Instant.now();
        Instant decayCutoff = now.minus(java.time.Duration.ofDays(DECAY_PERIOD_DAYS));

        // Get recent ratings
        List<ServiceRating> recentRatings = serviceRatings.values().stream()
            .filter(rating -> rating.getProviderId().equals(original.getProviderId()) &&
                             rating.getTimestamp().isAfter(decayCutoff))
            .collect(Collectors.toList());

        List<ServiceRating> oldRatings = serviceRatings.values().stream()
            .filter(rating -> rating.getProviderId().equals(original.getProviderId()) &&
                             rating.getTimestamp().isBefore(decayCutoff))
            .collect(Collectors.toList());

        // Apply decay factor to old ratings
        BigDecimal decayFactor = DECAY_WEIGHT;
        List<ServiceRating> decayedOldRatings = oldRatings.stream()
            .map(rating -> {
                MultiDimensionalRating oldRating = rating.getRating();
                MultiDimensionalRating decayedRating = oldRating.multiply(decayFactor.doubleValue());
                return new ServiceRating(
                    rating.getRatingId(), rating.getProviderId(), rating.getClientId(),
                    rating.getServiceId(), decayedRating, rating.getTimestamp()
                );
            })
            .collect(Collectors.toList());

        // Combine ratings
        List<ServiceRating> allRatings = new ArrayList<>();
        allRatings.addAll(recentRatings);
        allRatings.addAll(decayedOldRatings);

        // Recalculate reputation with decayed ratings
        ProviderReputation decayedRep = new ProviderReputation(original.getProviderId());
        for (ServiceRating combinedRating : allRatings) {
            decayedRep = decayedRep.addRating(combinedRating);
        }

        return decayedRep;
    }

    /**
     * Gets the latest rating for a provider.
     */
    private MultiDimensionalRating getLatestRating(ProviderReputation providerRep) {
        String providerId = providerRep.getProviderId();
        return serviceRatings.values().stream()
            .filter(rating -> rating.getProviderId().equals(providerId))
            .max(Comparator.comparing(ServiceRating::getTimestamp))
            .map(ServiceRating::getRating)
            .orElse(new MultiDimensionalRating(0, 0, 0, 0));
    }

    // Inner classes for data structures

    /**
     * Multi-dimensional rating for a service.
     */
    public static final class MultiDimensionalRating {
        private final int qualityRating;
        private final int reliabilityRating;
        private final int communicationRating;
        private final int satisfactionRating;

        public MultiDimensionalRating(int qualityRating, int reliabilityRating,
                                    int communicationRating, int satisfactionRating) {
            this.qualityRating = qualityRating;
            this.reliabilityRating = reliabilityRating;
            this.communicationRating = communicationRating;
            this.satisfactionRating = satisfactionRating;
        }

        // Getters
        public int getQualityRating() { return qualityRating; }
        public int getReliabilityRating() { return reliabilityRating; }
        public int getCommunicationRating() { return communicationRating; }
        public int getSatisfactionRating() { return satisfactionRating; }

        public int getDimensionScore(ReputationDimension dimension) {
            switch (dimension) {
                case QUALITY: return qualityRating;
                case RELIABILITY: return reliabilityRating;
                case COMMUNICATION: return communicationRating;
                case SATISFACTION: return satisfactionRating;
                default: throw new IllegalArgumentException("Unknown dimension: " + dimension);
            }
        }

        public MultiDimensionalRating multiply(double factor) {
            return new MultiDimensionalRating(
                (int) Math.round(qualityRating * factor),
                (int) Math.round(reliabilityRating * factor),
                (int) Math.round(communicationRating * factor),
                (int) Math.round(satisfactionRating * factor)
            );
        }
    }

    /**
     * Service rating record.
     */
    public static final class ServiceRating {
        private final String ratingId;
        private final String providerId;
        private final String clientId;
        private final String serviceId;
        private final MultiDimensionalRating rating;
        private final Instant timestamp;

        public ServiceRating(String ratingId, String providerId, String clientId,
                           String serviceId, MultiDimensionalRating rating, Instant timestamp) {
            this.ratingId = Objects.requireNonNull(ratingId);
            this.providerId = Objects.requireNonNull(providerId);
            this.clientId = Objects.requireNonNull(clientId);
            this.serviceId = Objects.requireNonNull(serviceId);
            this.rating = Objects.requireNonNull(rating);
            this.timestamp = Objects.requireNonNull(timestamp);
        }

        // Getters
        public String getRatingId() { return ratingId; }
        public String getProviderId() { return providerId; }
        public String getClientId() { return clientId; }
        public String getServiceId() { return serviceId; }
        public MultiDimensionalRating getRating() { return rating; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * Provider reputation information.
     */
    public static final class ProviderReputation {
        private final String providerId;
        private final Map<ReputationDimension, BigDecimal> dimensionScores;
        private final BigDecimal trustScore;
        private final long totalRatings;
        private final Instant lastUpdated;

        public ProviderReputation(String providerId) {
            this(providerId, new HashMap<>(), BigDecimal.ZERO, 0L, Instant.now());
        }

        private ProviderReputation(String providerId, Map<ReputationDimension, BigDecimal> dimensionScores,
                                BigDecimal trustScore, long totalRatings, Instant lastUpdated) {
            this.providerId = Objects.requireNonNull(providerId);
            this.dimensionScores = Objects.requireNonNull(dimensionScores);
            this.trustScore = Objects.requireNonNull(trustScore);
            this.totalRatings = totalRatings;
            this.lastUpdated = Objects.requireNonNull(lastUpdated);
        }

        public ProviderReputation addRating(ServiceRating rating) {
            MultiDimensionalRating multiRating = rating.getRating();
            Instant timestamp = rating.getTimestamp();

            // Update dimension scores
            Map<ReputationDimension, BigDecimal> newScores = new HashMap<>(dimensionScores);

            for (ReputationDimension dimension : ReputationDimension.values()) {
                BigDecimal currentScore = newScores.getOrDefault(dimension, BigDecimal.ZERO);
                BigDecimal newScore = calculateNewScore(currentScore, multiRating.getDimensionScore(dimension), totalRatings);
                newScores.put(dimension, newScore);
            }

            // Calculate new trust score
            BigDecimal newTrustScore = calculateTrustScore(newScores);

            return new ProviderReputation(
                providerId, newScores, newTrustScore, totalRatings + 1, timestamp
            );
        }

        public ProviderReputation updateScores(Map<ReputationDimension, BigDecimal> newScores) {
            BigDecimal newTrustScore = calculateTrustScore(newScores);
            return new ProviderReputation(
                providerId, new HashMap<>(newScores), newTrustScore, totalRatings, Instant.now()
            );
        }

        // Getters
        public String getProviderId() { return providerId; }
        public Map<ReputationDimension, BigDecimal> getDimensionScores() { return dimensionScores; }
        public BigDecimal getTrustScore() { return trustScore; }
        public long getTotalRatings() { return totalRatings; }
        public Instant getLastUpdated() { return lastUpdated; }

        public BigDecimal getDimensionScore(ReputationDimension dimension) {
            return dimensionScores.getOrDefault(dimension, BigDecimal.ZERO);
        }

        private BigDecimal calculateNewScore(BigDecimal currentScore, int newRating, long count) {
            if (count == 0) {
                return BigDecimal.valueOf(newRating);
            }

            BigDecimal currentSum = currentScore.multiply(BigDecimal.valueOf(count));
            BigDecimal newSum = currentSum.add(BigDecimal.valueOf(newRating));
            return newSum.divide(BigDecimal.valueOf(count + 1), 2, RoundingMode.HALF_UP);
        }

        private BigDecimal calculateTrustScore(Map<ReputationDimension, BigDecimal> scores) {
            BigDecimal weightedSum = BigDecimal.ZERO;
            BigDecimal totalWeight = BigDecimal.ZERO;

            for (Map.Entry<ReputationDimension, BigDecimal> entry : scores.entrySet()) {
                BigDecimal score = entry.getValue();
                BigDecimal weight = DIMENSION_WEIGHTS.get(entry.getKey());

                weightedSum = weightedSum.add(score.multiply(weight));
                totalWeight = totalWeight.add(weight);
            }

            if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }

            BigDecimal normalizedScore = weightedSum.divide(totalWeight, 2, RoundingMode.HALF_UP);

            // Scale to 0-100 range
            return normalizedScore.multiply(new BigDecimal(20)).max(MIN_TRUST_SCORE).min(MAX_TRUST_SCORE);
        }
    }

    /**
     * Client reputation information.
     */
    public static final class ClientReputation {
        private final String clientId;
        private final long totalRatingsGiven;
        private final Instant lastRatingGiven;

        public ClientReputation(String clientId) {
            this(clientId, 0L, null);
        }

        private ClientReputation(String clientId, long totalRatingsGiven, Instant lastRatingGiven) {
            this.clientId = Objects.requireNonNull(clientId);
            this.totalRatingsGiven = totalRatingsGiven;
            this.lastRatingGiven = lastRatingGiven;
        }

        public ClientReputation addRatingGiven(ServiceRating rating) {
            Instant timestamp = rating.getTimestamp();
            return new ClientReputation(
                clientId, totalRatingsGiven + 1, timestamp
            );
        }

        // Getters
        public String getClientId() { return clientId; }
        public long getTotalRatingsGiven() { return totalRatingsGiven; }
        public Instant getLastRatingGiven() { return lastRatingGiven; }
    }

    /**
     * Provider summary for recommendations.
     */
    public static final class ProviderSummary {
        private final String providerId;
        private final BigDecimal averageRating;
        private final BigDecimal trustScore;
        private final long ratingCount;

        public ProviderSummary(String providerId, BigDecimal averageRating,
                             BigDecimal trustScore, long ratingCount) {
            this.providerId = Objects.requireNonNull(providerId);
            this.averageRating = Objects.requireNonNull(averageRating);
            this.trustScore = Objects.requireNonNull(trustScore);
            this.ratingCount = ratingCount;
        }

        // Getters
        public String getProviderId() { return providerId; }
        public BigDecimal getAverageRating() { return averageRating; }
        public BigDecimal getTrustScore() { return trustScore; }
        public long getRatingCount() { return ratingCount; }
    }

    /**
     * Reputation history record.
     */
    public static final class ReputationHistory {
        private final String providerId;
        private final BigDecimal trustScore;
        private final Instant timestamp;

        public ReputationHistory(String providerId, BigDecimal trustScore, Instant timestamp) {
            this.providerId = Objects.requireNonNull(providerId);
            this.trustScore = Objects.requireNonNull(trustScore);
            this.timestamp = Objects.requireNonNull(timestamp);
        }

        // Getters
        public String getProviderId() { return providerId; }
        public BigDecimal getTrustScore() { return trustScore; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * Reputation statistics for the marketplace.
     */
    public static final class ReputationStatistics {
        private final long totalProviders;
        private final long totalClients;
        private final long totalRatings;
        private final BigDecimal averageTrustScore;
        private final BigDecimal averageOverallRating;
        private final Map<Integer, Long> ratingDistribution;

        public ReputationStatistics(long totalProviders, long totalClients, long totalRatings,
                                  BigDecimal averageTrustScore, BigDecimal averageOverallRating,
                                  Map<Integer, Long> ratingDistribution) {
            this.totalProviders = totalProviders;
            this.totalClients = totalClients;
            this.totalRatings = totalRatings;
            this.averageTrustScore = Objects.requireNonNull(averageTrustScore);
            this.averageOverallRating = Objects.requireNonNull(averageOverallRating);
            this.ratingDistribution = Objects.requireNonNull(ratingDistribution);
        }

        // Getters
        public long getTotalProviders() { return totalProviders; }
        public long getTotalClients() { return totalClients; }
        public long getTotalRatings() { return totalRatings; }
        public BigDecimal getAverageTrustScore() { return averageTrustScore; }
        public BigDecimal getAverageOverallRating() { return averageOverallRating; }
        public Map<Integer, Long> getRatingDistribution() { return ratingDistribution; }
    }

    /**
     * Reputation dimensions enumeration.
     */
    public enum ReputationDimension {
        QUALITY,          // Service quality and effectiveness
        RELIABILITY,      // On-time delivery and consistency
        COMMUNICATION,    // Responsiveness and clarity
        SATISFACTION      // Overall client satisfaction
    }
}