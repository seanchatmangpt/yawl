package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Constraint-based matcher that first filters by hard constraints,
 * then ranks by soft constraints using YAWL workflow patterns.
 *
 * Implements WCP-4 (Exclusive Choice) for urgency routing,
 * WCP-6 (Multi-Choice) for multi-specialization, and
 * WCP-21 (Deferred Choice) for patient preference.
 */
public class ConstraintBasedMatcher implements MatchingAlgorithm {

    private final Map<String, Integer> urgencyPriority = Map.of(
        "emergency", 3,
        "urgent", 2,
        "routine", 1
    );

    @Override
    public List<MatchResult> match(NDimensionalCoordinate patientNeeds,
                                 List<ServiceProfile> availableProviders,
                                 int maxResults) {

        // Step 1: Apply hard constraints (must-haves)
        List<ServiceProfile> filteredProviders = applyHardConstraints(patientNeeds, availableProviders);

        if (filteredProviders.isEmpty()) {
            return Collections.emptyList();
        }

        // Step 2: Group by urgency for exclusive choice routing (WCP-4)
        Map<String, List<ServiceProfile>> providersByUrgency = filteredProviders.stream()
            .collect(Collectors.groupingBy(p -> "routine")); // Default grouping, urgency handling is done in filtering

        // Step 3: Apply multi-choice routing for specializations (WCP-6)
        List<ServiceProfile> prioritizedProviders = applyMultiChoiceRouting(patientNeeds, providersByUrgency);

        // Step 4: Apply patient preference routing (WCP-21 - Deferred Choice)
        List<ServiceProfile> finalRanking = applyDeferredChoice(patientNeeds, prioritizedProviders);

        // Step 5: Return top matches
        return finalRanking.stream()
            .limit(maxResults)
            .map(provider -> new MatchResult(
                provider,
                calculateFinalScore(patientNeeds, provider),
                finalRanking.indexOf(provider) + 1,
                "Constraint-based matching with workflow pattern routing"
            ))
            .collect(Collectors.toList());
    }

    @Override
    public String getAlgorithmName() {
        return "ConstraintBasedMatcher";
    }

    private List<ServiceProfile> applyHardConstraints(NDimensionalCoordinate patientNeeds,
                                                     List<ServiceProfile> providers) {
        return providers.stream()
            .filter(provider -> meetsHardConstraints(patientNeeds, provider))
            .collect(Collectors.toList());
    }

    private boolean meetsHardConstraints(NDimensionalCoordinate patientNeeds,
                                       ServiceProfile provider) {
        // Hard constraint 1: Rating threshold
        if (!provider.meetsRatingRequirement(patientNeeds.d6RatingThreshold())) {
            return false;
        }

        // Hard constraint 2: Service type match
        if (!provider.specialty().equalsIgnoreCase(patientNeeds.d1ServiceType())) {
            return false;
        }

        // Hard constraint 3: Price range compatibility
        if (!isPriceCompatible(patientNeeds.d5PriceRange(), provider.pricePerHour())) {
            return false;
        }

        // Hard constraint 4: Urgency capability check
        if (!canHandleUrgency(patientNeeds.d4Urgency(), provider)) {
            return false;
        }

        return true;
    }

    private boolean canHandleUrgency(String urgency, ServiceProfile provider) {
        return switch (urgency.toLowerCase()) {
            case "emergency" -> provider.yearsExperience() >= 5; // Must have experience for emergency
            case "urgent" -> provider.yearsExperience() >= 2;   // Some experience for urgent cases
            case "routine" -> true; // Any provider can handle routine
            default -> true;
        };
    }

    private Map<String, List<ServiceProfile>> groupByUrgency(List<ServiceProfile> providers) {
        return Map.of("all", providers); // Simplified grouping for now
    }

    private List<ServiceProfile> applyMultiChoiceRouting(NDimensionalCoordinate patientNeeds,
                                                        Map<String, List<ServiceProfile>> providersByUrgency) {
        List<ServiceProfile> result = new ArrayList<>();

        // Process emergency cases first
        List<ServiceProfile> emergencyProviders = providersByUrgency.getOrDefault("emergency", List.of());
        result.addAll(rankBySpecialization(emergencyProviders, patientNeeds));

        // Then urgent cases
        List<ServiceProfile> urgentProviders = providersByUrgency.getOrDefault("urgent", List.of());
        result.addAll(rankBySpecialization(urgentProviders, patientNeeds));

        // Finally routine cases
        List<ServiceProfile> routineProviders = providersByUrgency.getOrDefault("routine", List.of());
        result.addAll(rankBySpecialization(routineProviders, patientNeeds));

        return result;
    }

    private List<ServiceProfile> rankBySpecialization(List<ServiceProfile> providers,
                                                      NDimensionalCoordinate patientNeeds) {
        return providers.stream()
            .sorted(Comparator
                .comparingDouble((ServiceProfile p) ->
                    calculateSpecializationMatch(p, patientNeeds.d2Specialization()))
                .reversed()
                .thenComparing(p -> -p.yearsExperience())
                .thenComparing(p -> -p.getAverageRating())
            )
            .collect(Collectors.toList());
    }

    private double calculateSpecializationMatch(ServiceProfile provider, String specialization) {
        if (provider.specialty().equalsIgnoreCase(specialization)) {
            return 1.0;
        }

        // Check certifications for matching specialization
        return provider.certifications().stream()
            .mapToInt(cert -> cert.toLowerCase().contains(specialization.toLowerCase()) ? 1 : 0)
            .sum() * 0.3; // 0.3 points per matching certification
    }

    private List<ServiceProfile> applyDeferredChoice(NDimensionalCoordinate patientNeeds,
                                                    List<ServiceProfile> providers) {
        // WCP-21: Deferred Choice - apply patient preferences if available
        // In this implementation, we apply preference weights

        return providers.stream()
            .sorted(Comparator
                .comparingDouble((ServiceProfile p) ->
                    calculatePreferenceScore(patientNeeds, p))
                .reversed()
            )
            .collect(Collectors.toList());
    }

    private double calculatePreferenceScore(NDimensionalCoordinate patientNeeds, ServiceProfile provider) {
        double score = 0.0;

        // Prefer providers with higher ratings
        score += provider.getAverageRating() * 0.4;

        // Prefer providers with more experience
        score += Math.min(provider.yearsExperience() / 10.0, 1.0) * 0.3;

        // Prefer providers who match specialization exactly
        score += (provider.specialty().equalsIgnoreCase(patientNeeds.d2Specialization()) ? 1.0 : 0.5) * 0.3;

        return score;
    }

    private double calculateFinalScore(NDimensionalCoordinate patientNeeds, ServiceProfile provider) {
        double baseScore = calculatePreferenceScore(patientNeeds, provider);

        // Boost score based on urgency handling
        int urgencyBoost = urgencyPriority.getOrDefault(patientNeeds.d4Urgency().toLowerCase(), 1);
        baseScore *= urgencyBoost;

        // Adjust for price range compatibility
        double priceMultiplier = calculatePriceMultiplier(patientNeeds.d5PriceRange(), provider.pricePerHour());
        baseScore *= priceMultiplier;

        return baseScore;
    }

    private double calculatePriceMultiplier(String priceRange, double pricePerHour) {
        return switch (priceRange.toLowerCase()) {
            case "budget" -> pricePerHour <= 75.0 ? 1.2 : 1.0;
            case "standard" -> 1.0;
            case "premium" -> pricePerHour > 150.0 ? 1.1 : 0.9;
            default -> 1.0;
        };
    }

    private boolean isPriceCompatible(String priceRange, double pricePerHour) {
        return switch (priceRange.toLowerCase()) {
            case "budget" -> pricePerHour <= 75.0;
            case "standard" -> pricePerHour > 75.0 && pricePerHour <= 150.0;
            case "premium" -> pricePerHour > 150.0;
            default -> true;
        };
    }

    /**
     * Helper record to track patient needs during filtering.
     */
    private record ServicePriority(ServiceProfile provider, NDimensionalCoordinate needs) {}
}