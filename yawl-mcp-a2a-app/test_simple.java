import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

// Simple test without package structure
public class test_simple {
    public static void main(String[] args) {
        System.out.println("Starting simple test...");

        // Create test providers
        List<ServiceProfile> testProviders = createTestProviders(10);

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

        // Try matching with WeightedDimensionMatcher
        if (available.isEmpty()) {
            System.out.println("No providers match basic criteria!");
        } else {
            WeightedDimensionMatcher matcher = new WeightedDimensionMatcher.Builder()
                .setServiceTypeWeight(0.4)
                .setSpecializationWeight(0.4)
                .build();

            List<MatchResult> results = matcher.match(patientNeeds, available, 5);

            System.out.println("\n=== Matching Results ===");
            System.out.println("Algorithm: " + matcher.getAlgorithmName());
            System.out.println("Matches found: " + results.size());

            if (results.isEmpty()) {
                System.out.println("No matches found from filtered list!");
            } else {
                results.forEach(m -> {
                    System.out.printf("- %s (score: %.2f, rank: %.0f)%n",
                        m.provider().providerName(), m.similarityScore(), m.rank());
                });
            }
        }
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
                    case 0 -> 50.0 + (i % 10);
                    case 1 -> 100.0 + (i % 20);
                    default -> 200.0 + (i % 50);
                };

                double rating = 4.0 + (i % 10) * 0.1; // Ensure ratings are 4.0+

                return new ServiceProfile(
                    "provider-" + String.format("%04d", i),
                    "Provider " + (i + 1),
                    serviceType,
                    List.of("Certified OT", specialization + " specialist"),
                    List.of("English"),
                    Map.of(serviceType, rating),
                    rating,
                    1 + (i % 10),
                    List.of(new AvailabilitySlot(
                        LocalDateTime.now().plusDays(1).withHour(9),
                        LocalDateTime.now().plusDays(1).withHour(17),
                        List.of("assessment", "intervention")
                    )),
                    price,
                    i % 2 == 0,
                    LocalDateTime.now()
                );
            })
            .toList();
    }
}