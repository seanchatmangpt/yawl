import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

// Test using classes from compiled package
public class test_simple2 {
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

        if (available.isEmpty()) {
            System.out.println("No providers match basic criteria!");
        } else {
            // Try matching with WeightedDimensionMatcher
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
                // Let's debug the scoring
                System.out.println("\n=== Debug Scoring ===");
                for (ServiceProfile p : available) {
                    double[] patientVector = patientNeeds.toVector();
                    double[] providerVector = createProviderVector(p, patientNeeds);
                    double score = calculateCosineSimilarity(patientVector, providerVector);
                    System.out.printf("%s: score=%.4f%n", p.providerName(), score);
                }
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

    private static double[] createProviderVector(ServiceProfile provider, NDimensionalCoordinate patientNeeds) {
        // Simplified vector creation for debugging
        return new double[] {
            provider.specialty().equalsIgnoreCase(patientNeeds.d1ServiceType()) ? 1.0 : 0.0,
            provider.specialty().equalsIgnoreCase(patientNeeds.d2Specialization()) ? 1.0 : 0.0,
            0.8, // Delivery mode compatibility
            1.0, // Urgency handling
            1.0, // Price compatibility
            provider.getAverageRating() / 5.0 // Rating
        };
    }

    private static double calculateCosineSimilarity(double[] vectorA, double[] vectorB) {
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
}