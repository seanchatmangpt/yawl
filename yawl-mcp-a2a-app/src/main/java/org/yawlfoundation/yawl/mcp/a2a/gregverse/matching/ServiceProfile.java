package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service provider profile for GregVerse OT marketplace.
 */
public record ServiceProfile(
    String providerId,
    String providerName,
    String specialty,
    List<String> certifications,
    List<String> availableLanguages,
    Map<String, Double> ratingByCategory, // service type -> rating
    double overallRating,
    int yearsExperience,
    List<AvailabilitySlot> availability,
    double pricePerHour,
    boolean acceptsInsurance,
    LocalDateTime lastUpdated
) {

    /**
     * Checks if provider is available at requested time.
     */
    public boolean isAvailable(LocalDateTime requestTime) {
        return availability.stream()
            .anyMatch(slot -> slot.contains(requestTime));
    }

    /**
     * Gets average rating across all service types.
     */
    public double getAverageRating() {
        if (ratingByCategory.isEmpty()) {
            return overallRating;
        }
        return ratingByCategory.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(overallRating);
    }

    /**
     * Checks if provider meets minimum rating requirement.
     */
    public boolean meetsRatingRequirement(String ratingThreshold) {
        double threshold = switch (ratingThreshold) {
            case "3+" -> 3.0;
            case "4+" -> 4.0;
            case "4.5+" -> 4.5;
            default -> 3.0;
        };
        return getAverageRating() >= threshold;
    }
}