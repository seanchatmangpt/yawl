package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Vector-based matching using cosine similarity algorithm.
 *
 * This matcher treats each dimension as a feature vector and calculates
 * cosine similarity between patient needs and provider capabilities.
 */
public class CosineSimilarityMatcher implements MatchingAlgorithm {

    private static final Map<Integer, Double> DIMENSION_WEIGHTS = Map.of(
        0, 1.0,  // Service type
        1, 1.5,  // Specialization (higher weight)
        2, 1.0,  // Delivery mode
        3, 2.0,  // Urgency (highest weight)
        4, 0.8,  // Price range
        5, 1.2   // Rating threshold
    );

    @Override
    public List<MatchResult> match(NDimensionalCoordinate patientNeeds,
                                 List<ServiceProfile> availableProviders,
                                 int maxResults) {

        // Filter providers by basic constraints
        List<ServiceProfile> filteredProviders = availableProviders.stream()
            .filter(provider -> meetsBasicConstraints(patientNeeds, provider))
            .collect(Collectors.toList());

        if (filteredProviders.isEmpty()) {
            return Collections.emptyList();
        }

        // Create vector representation
        double[] patientVector = createWeightedVector(patientNeeds);

        // Calculate similarity scores
        List<ProviderScore> scores = new ArrayList<>();
        for (ServiceProfile provider : filteredProviders) {
            double[] providerVector = createProviderVector(provider, patientNeeds);
            double similarity = calculateCosineSimilarity(patientVector, providerVector);
            scores.add(new ProviderScore(provider, similarity));
        }

        // Sort by similarity and return top matches
        return scores.stream()
            .sorted(Comparator.comparing(ProviderScore::score).reversed())
            .limit(maxResults)
            .map(ps -> new MatchResult(
                ps.provider(),
                ps.score(),
                scores.indexOf(ps) + 1,
                "Best match based on cosine similarity with weighted dimensions"
            ))
            .collect(Collectors.toList());
    }

    @Override
    public String getAlgorithmName() {
        return "CosineSimilarityMatcher";
    }

    private boolean meetsBasicConstraints(NDimensionalCoordinate patientNeeds,
                                        ServiceProfile provider) {
        // Check rating threshold
        if (!provider.meetsRatingRequirement(patientNeeds.d6RatingThreshold())) {
            return false;
        }

        // Check price range compatibility
        if (!isPriceCompatible(patientNeeds.d5PriceRange(), provider.pricePerHour())) {
            return false;
        }

        // Check if provider offers the requested service type
        if (!provider.specialty().equalsIgnoreCase(patientNeeds.d1ServiceType())) {
            return false;
        }

        return true;
    }

    private boolean isPriceCompatible(String priceRange, double pricePerHour) {
        return switch (priceRange.toLowerCase()) {
            case "budget" -> pricePerHour <= 75.0;
            case "standard" -> pricePerHour > 75.0 && pricePerHour <= 150.0;
            case "premium" -> pricePerHour > 150.0;
            default -> true;
        };
    }

    private double[] createWeightedVector(NDimensionalCoordinate coordinate) {
        double[] baseVector = coordinate.toVector();
        double[] weightedVector = new double[baseVector.length];

        for (int i = 0; i < baseVector.length; i++) {
            weightedVector[i] = baseVector[i] * DIMENSION_WEIGHTS.getOrDefault(i, 1.0);
        }

        return weightedVector;
    }

    private double[] createProviderVector(ServiceProfile provider,
                                       NDimensionalCoordinate patientNeeds) {
        double[] vector = new double[6];

        // Service type match (exact match = 1.0, else 0.0)
        vector[0] = provider.specialty().equalsIgnoreCase(patientNeeds.d1ServiceType()) ? 1.0 : 0.0;

        // Specialization match (consider certifications and experience)
        vector[1] = calculateSpecializationScore(provider, patientNeeds.d2Specialization());

        // Delivery mode compatibility
        vector[2] = calculateDeliveryModeScore(provider, patientNeeds.d3DeliveryMode());

        // Urgency handling capability (higher experience = better)
        vector[3] = Math.min(provider.yearsExperience() / 10.0, 1.0);

        // Price compatibility (inverse relationship - cheaper is better for budget)
        vector[4] = calculatePriceScore(patientNeeds.d5PriceRange(), provider.pricePerHour());

        // Rating score
        vector[5] = provider.getAverageRating() / 5.0; // Normalize to 0-1

        return createWeightedVector(new NDimensionalCoordinate(
            vector[0] > 0.5 ? "match" : "no_match",
            vector[1] > 0.5 ? "match" : "no_match",
            vector[2] > 0.5 ? "match" : "no_match",
            vector[3] > 0.5 ? "match" : "no_match",
            vector[4] > 0.5 ? "match" : "no_match",
            vector[5] > 0.5 ? "match" : "no_match"
        ));
    }

    private double calculateSpecializationScore(ServiceProfile provider, String specialization) {
        if (provider.specialty().equalsIgnoreCase(specialization)) {
            return 1.0;
        }

        // Check if certifications include the specialization
        return provider.certifications().stream()
            .anyMatch(cert -> cert.toLowerCase().contains(specialization.toLowerCase())) ? 0.7 : 0.3;
    }

    private double calculateDeliveryModeScore(ServiceProfile provider, String preferredMode) {
        // In this simplified version, assume all providers support all modes
        // In a real implementation, this would be stored in the provider profile
        return 0.8;
    }

    private double calculatePriceScore(String priceRange, double pricePerHour) {
        return switch (priceRange.toLowerCase()) {
            case "budget" -> pricePerHour <= 75.0 ? 1.0 : 0.5;
            case "standard" -> (pricePerHour > 75.0 && pricePerHour <= 150.0) ? 1.0 : 0.6;
            case "premium" -> pricePerHour > 150.0 ? 1.0 : 0.4;
            default -> 0.5;
        };
    }

    private double calculateCosineSimilarity(double[] vectorA, double[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must be of same length");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ProviderScore(ServiceProfile provider, double score) {}
}