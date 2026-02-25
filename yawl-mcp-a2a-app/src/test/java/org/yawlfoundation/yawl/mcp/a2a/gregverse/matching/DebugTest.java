package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Debug test to understand why matching returns 0 results.
 */
public class DebugTest {

    public static void main(String[] args) {
        System.out.println("Starting debug test...");

        // Create matcher service
        MarketplaceMatcherService matcherService = new MarketplaceMatcherService();

        // Create test providers
        List<ServiceProfile> testProviders = createTestProviders(10); // Smaller set for debugging
        testProviders.forEach(matcherService::registerProvider);

        System.out.println("=== Registered Providers ===");
        testProviders.forEach(p -> {
            System.out.printf("%s: %s, %s, $%.2f, rating %.1f%n",
                p.providerId(), p.providerName(), p.specialty(), p.pricePerHour(), p.getAverageRating());
        });

        // Test patient needs
        NDimensionalCoordinate patientNeeds = new NDimensionalCoordinate(
            "assessment",
            "pediatric",
            "telehealth",
            "routine",
            "standard",
            "4+"
        );

        System.out.println("\n=== Patient Needs ===");
        System.out.println(patientNeeds);

        // Check basic constraints
        System.out.println("\n=== Checking Basic Constraints ===");
        for (ServiceProfile provider : testProviders) {
            boolean matchesServiceType = provider.specialty().equalsIgnoreCase(patientNeeds.d1ServiceType());
            boolean meetsRating = provider.meetsRatingRequirement(patientNeeds.d6RatingThreshold());

            System.out.printf("%s: serviceMatch=%s, ratingOk=%s%n",
                provider.providerId(), matchesServiceType, meetsRating);
        }

        // Get available providers for this need
        List<ServiceProfile> available = testProviders.stream()
            .filter(p -> p.specialty().equalsIgnoreCase(patientNeeds.d1ServiceType()))
            .filter(p -> p.meetsRatingRequirement(patientNeeds.d6RatingThreshold()))
            .toList();

        System.out.println("\n=== Filtered Providers (" + available.size() + ") ===");
        available.forEach(p -> {
            System.out.printf("- %s (%.1f rating)%n", p.providerName(), p.getAverageRating());
        });

        // Try matching
        var response = matcherService.matchAsync("debug-test", patientNeeds, "weighted", 5).join();

        System.out.println("\n=== Matching Results ===");
        System.out.println("Algorithm: " + response.algorithmUsed());
        System.out.println("Response time: " + response.responseTimeMs() + "ms");
        System.out.println("Matches found: " + response.matches().size());

        if (response.matches().isEmpty()) {
            System.out.println("No matches found - this indicates an issue with the matching algorithm");
        } else {
            response.matches().forEach(m -> {
                System.out.printf("- %s (score: %.2f)%n", m.provider().providerName(), m.similarityScore());
            });
        }

        // Test with different algorithm
        System.out.println("\n=== Testing Cosine Similarity ===");
        var cosineResponse = matcherService.matchAsync("debug-test-cosine", patientNeeds, "cosine", 5).join();
        System.out.println("Cosine matches: " + cosineResponse.matches().size());
    }

    private static List<ServiceProfile> createTestProviders(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> {
                String specialization = switch (i % 4) {
                    case 0 -> "pediatric";
                    case 1 -> "geriatric";
                    case 2 -> "mental health";
                    default -> "physical rehab";
                };

                String serviceType = switch (i % 3) {
                    case 0 -> "assessment";
                    case 1 -> "intervention";
                    default -> "scheduling";
                };

                double price = switch (i % 3) {
                    case 0 -> 50.0 + (i % 10); // Budget range
                    case 1 -> 100.0 + (i % 20); // Standard range
                    default -> 200.0 + (i % 50); // Premium range
                };

                double rating = 3.5 + (i % 15) * 0.1; // Higher ratings for testing

                return new ServiceProfile(
                    "provider-" + String.format("%04d", i),
                    "Provider " + (i + 1),
                    serviceType,
                    List.of("Certified OT", specialization + " specialist"),
                    List.of("English"),
                    Map.of(serviceType, rating),
                    rating,
                    1 + (i % 10), // 1-10 years experience
                    List.of(new AvailabilitySlot(
                        LocalDateTime.now().plusDays(1).withHour(9),
                        LocalDateTime.now().plusDays(1).withHour(17),
                        List.of("assessment", "intervention")
                    )),
                    price,
                    i % 2 == 0, // Random insurance acceptance
                    LocalDateTime.now()
                );
            })
            .toList();
    }
}