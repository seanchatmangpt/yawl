package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Configurable weighted dimension matcher that allows customization
 * of dimension importance weights.
 *
 * Provides fine-grained control over matching criteria priorities.
 */
public class WeightedDimensionMatcher implements MatchingAlgorithm {

    private final Map<Integer, Double> dimensionWeights;
    private final boolean normalizeWeights;

    /**
     * Creates a WeightedDimensionMatcher with default weights.
     */
    public WeightedDimensionMatcher() {
        this(defaultWeights(), true);
    }

    /**
     * Creates a WeightedDimensionMatcher with custom weights.
     *
     * @param dimensionWeights Map of dimension index (0-5) to weight (0.0-1.0)
     * @param normalizeWeights Whether to normalize weights to sum to 1.0
     */
    public WeightedDimensionMatcher(Map<Integer, Double> dimensionWeights, boolean normalizeWeights) {
        this.dimensionWeights = normalizeWeights ? normalizeWeights(dimensionWeights) : dimensionWeights;
        this.normalizeWeights = normalizeWeights;
    }

    @Override
    public List<MatchResult> match(NDimensionalCoordinate patientNeeds,
                                 List<ServiceProfile> availableProviders,
                                 int maxResults) {

        // Pre-filter providers that meet basic requirements
        List<ServiceProfile> candidateProviders = availableProviders.stream()
            .filter(provider -> meetsBasicRequirements(patientNeeds, provider))
            .collect(Collectors.toList());

        if (candidateProviders.isEmpty()) {
            return Collections.emptyList();
        }

        // Calculate weighted scores for each provider
        List<ProviderScore> scoredProviders = new ArrayList<>();
        for (ServiceProfile provider : candidateProviders) {
            double score = calculateWeightedScore(patientNeeds, provider);
            scoredProviders.add(new ProviderScore(provider, score));
        }

        // Sort by score and return top matches
        return scoredProviders.stream()
            .sorted(Comparator.comparing(ProviderScore::score).reversed())
            .limit(maxResults)
            .map(ps -> new MatchResult(
                ps.provider(),
                ps.score(),
                scoredProviders.indexOf(ps) + 1,
                String.format("Weighted score with dimensions: %s", dimensionWeights)
            ))
            .collect(Collectors.toList());
    }

    @Override
    public String getAlgorithmName() {
        return "WeightedDimensionMatcher";
    }

    private boolean meetsBasicRequirements(NDimensionalCoordinate patientNeeds, ServiceProfile provider) {
        // Basic requirements that must be met
        if (!provider.meetsRatingRequirement(patientNeeds.d6RatingThreshold())) {
            return false;
        }

        if (!provider.specialty().equalsIgnoreCase(patientNeeds.d1ServiceType())) {
            return false;
        }

        return true; // Other requirements are handled in weighted scoring
    }

    private double calculateWeightedScore(NDimensionalCoordinate patientNeeds, ServiceProfile provider) {
        double totalScore = 0.0;
        double totalWeight = 0.0;

        // Dimension 1: Service type exact match
        double score1 = provider.specialty().equalsIgnoreCase(patientNeeds.d1ServiceType()) ? 1.0 : 0.0;
        totalScore += score1 * dimensionWeights.getOrDefault(0, 1.0);
        totalWeight += dimensionWeights.getOrDefault(0, 1.0);

        // Dimension 2: Specialization match
        double score2 = calculateSpecializationScore(provider, patientNeeds.d2Specialization());
        totalScore += score2 * dimensionWeights.getOrDefault(1, 1.0);
        totalWeight += dimensionWeights.getOrDefault(1, 1.0);

        // Dimension 3: Delivery mode compatibility
        double score3 = calculateDeliveryScore(provider, patientNeeds.d3DeliveryMode());
        totalScore += score3 * dimensionWeights.getOrDefault(2, 1.0);
        totalWeight += dimensionWeights.getOrDefault(2, 1.0);

        // Dimension 4: Urgency handling capability
        double score4 = calculateUrgencyScore(patientNeeds.d4Urgency(), provider);
        totalScore += score4 * dimensionWeights.getOrDefault(3, 1.0);
        totalWeight += dimensionWeights.getOrDefault(3, 1.0);

        // Dimension 5: Price compatibility
        double score5 = calculatePriceScore(patientNeeds.d5PriceRange(), provider.pricePerHour());
        totalScore += score5 * dimensionWeights.getOrDefault(4, 1.0);
        totalWeight += dimensionWeights.getOrDefault(4, 1.0);

        // Dimension 6: Rating exceedance
        double score6 = calculateRatingScore(patientNeeds.d6RatingThreshold(), provider.getAverageRating());
        totalScore += score6 * dimensionWeights.getOrDefault(5, 1.0);
        totalWeight += dimensionWeights.getOrDefault(5, 1.0);

        // Normalize by total weight if needed
        return totalWeight > 0 ? totalScore / totalWeight : totalScore;
    }

    private double calculateSpecializationScore(ServiceProfile provider, String specialization) {
        if (provider.specialty().equalsIgnoreCase(specialization)) {
            return 1.0;
        }

        // Check certifications
        long matchingCerts = provider.certifications().stream()
            .filter(cert -> cert.toLowerCase().contains(specialization.toLowerCase()))
            .count();

        return Math.min(matchingCerts * 0.3, 0.9); // Max 0.9 for multiple certs
    }

    private double calculateDeliveryScore(ServiceProfile provider, String preferredMode) {
        // Simplified: assume all providers can handle delivery modes
        // In a real system, this would be based on provider capabilities
        return 0.8;
    }

    private double calculateUrgencyScore(String urgency, ServiceProfile provider) {
        return switch (urgency.toLowerCase()) {
            case "emergency" -> provider.yearsExperience() >= 5 ? 1.0 :
                               provider.yearsExperience() >= 3 ? 0.7 : 0.3;
            case "urgent" -> provider.yearsExperience() >= 2 ? 1.0 : 0.5;
            case "routine" -> 1.0;
            default -> 0.5;
        };
    }

    private double calculatePriceScore(String priceRange, double pricePerHour) {
        return switch (priceRange.toLowerCase()) {
            case "budget" -> pricePerHour <= 75.0 ? 1.0 :
                             pricePerHour <= 100.0 ? 0.7 : 0.4;
            case "standard" -> (pricePerHour > 75.0 && pricePerHour <= 150.0) ? 1.0 :
                              pricePerHour <= 100.0 ? 0.8 : 0.6;
            case "premium" -> pricePerHour > 150.0 ? 1.0 :
                              pricePerHour > 100.0 ? 0.8 : 0.5;
            default -> 0.5;
        };
    }

    private double calculateRatingScore(String ratingThreshold, double providerRating) {
        return switch (ratingThreshold) {
            case "3+" -> providerRating >= 3.0 ? 1.0 :
                         providerRating >= 2.5 ? 0.7 : 0.3;
            case "4+" -> providerRating >= 4.0 ? 1.0 :
                         providerRating >= 3.5 ? 0.7 : 0.3;
            case "4.5+" -> providerRating >= 4.5 ? 1.0 :
                          providerRating >= 4.0 ? 0.7 : 0.3;
            default -> 0.5;
        };
    }

    private Map<Integer, Double> normalizeWeights(Map<Integer, Double> weights) {
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0) {
            return defaultWeights();
        }

        Map<Integer, Double> normalized = new HashMap<>();
        for (Map.Entry<Integer, Double> entry : weights.entrySet()) {
            normalized.put(entry.getKey(), entry.getValue() / sum);
        }
        return normalized;
    }

    private static Map<Integer, Double> defaultWeights() {
        return Map.of(
            0, 0.15,  // Service type
            1, 0.25,  // Specialization
            2, 0.10,  // Delivery mode
            3, 0.20,  // Urgency
            4, 0.15,  // Price range
            5, 0.15   // Rating threshold
        );
    }

    public static class Builder {
        private Map<Integer, Double> weights = new HashMap<>();
        private boolean normalize = true;

        public Builder setDimensionWeight(int dimension, double weight) {
            weights.put(dimension, weight);
            return this;
        }

        public Builder setServiceTypeWeight(double weight) {
            return setDimensionWeight(0, weight);
        }

        public Builder setSpecializationWeight(double weight) {
            return setDimensionWeight(1, weight);
        }

        public Builder setDeliveryModeWeight(double weight) {
            return setDimensionWeight(2, weight);
        }

        public Builder setUrgencyWeight(double weight) {
            return setDimensionWeight(3, weight);
        }

        public Builder setPriceRangeWeight(double weight) {
            return setDimensionWeight(4, weight);
        }

        public Builder setRatingThresholdWeight(double weight) {
            return setDimensionWeight(5, weight);
        }

        public Builder withNormalization(boolean normalize) {
            this.normalize = normalize;
            return this;
        }

        public WeightedDimensionMatcher build() {
            return new WeightedDimensionMatcher(weights, normalize);
        }
    }

    private record ProviderScore(ServiceProfile provider, double score) {}
}