package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Simple performance test to verify <100ms matching latency with 1000 providers.
 */
public class PerformanceTest {

    public static void main(String[] args) {
        System.out.println("Starting N-dimensional Marketplace Matcher Performance Test...");

        // Create matcher service
        MarketplaceMatcherService matcherService = new MarketplaceMatcherService();

        // Create test providers
        List<ServiceProfile> testProviders = createTestProviders(1000);
        testProviders.forEach(matcherService::registerProvider);

        System.out.printf("Registered %d providers%n", testProviders.size());

        // Test patient needs
        NDimensionalCoordinate patientNeeds = new NDimensionalCoordinate(
            "assessment",
            "pediatric",
            "telehealth",
            "routine",
            "standard",
            "4+"
        );

        // Performance test - run multiple iterations
        int iterations = 10;
        long totalDuration = 0;
        long minDuration = Long.MAX_VALUE;
        long maxDuration = Long.MIN_VALUE;

        System.out.println("\nRunning performance tests...");
        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();

            CompletableFuture<MarketplaceMatcherService.MatchResponse> future =
                matcherService.matchAsync("perf-test-" + i, patientNeeds, "cosine", 10);

            try {
                MarketplaceMatcherService.MatchResponse response = future.get();
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;

                totalDuration += durationMs;
                minDuration = Math.min(minDuration, durationMs);
                maxDuration = Math.max(maxDuration, durationMs);

                System.out.printf("Iteration %d: %dms, Found %d matches%n",
                    i + 1, durationMs, response.matches().size());

                // Verify response time target
                if (durationMs >= 100) {
                    System.out.println("WARNING: Response time exceeded 100ms target!");
                }

            } catch (Exception e) {
                System.err.println("Error in iteration " + i + ": " + e.getMessage());
            }
        }

        // Calculate statistics
        double avgDuration = (double) totalDuration / iterations;

        System.out.println("\n=== Performance Results ===");
        System.out.printf("Average response time: %.2fms%n", avgDuration);
        System.out.printf("Min response time: %dms%n", minDuration);
        System.out.printf("Max response time: %dms%n", maxDuration);
        System.out.println("Target: <100ms per match");

        // Test algorithm performance comparison
        testAlgorithmComparison(matcherService, patientNeeds);

        // Test batch processing
        testBatchProcessing(matcherService);

        System.out.println("\nPerformance test completed!");
    }

    private static void testAlgorithmComparison(MarketplaceMatcherService matcherService,
                                             NDimensionalCoordinate patientNeeds) {
        System.out.println("\n=== Algorithm Comparison ===");

        String[] algorithms = {"cosine", "constraint", "weighted"};

        for (String algorithm : algorithms) {
            long startTime = System.nanoTime();

            CompletableFuture<MarketplaceMatcherService.MatchResponse> future =
                matcherService.matchAsync("algo-test-" + algorithm, patientNeeds, algorithm, 10);

            try {
                MarketplaceMatcherService.MatchResponse response = future.get();
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;

                System.out.printf("%s algorithm: %dms, %d matches%n",
                    algorithm, durationMs, response.matches().size());

            } catch (Exception e) {
                System.err.println("Error testing " + algorithm + ": " + e.getMessage());
            }
        }
    }

    private static void testBatchProcessing(MarketplaceMatcherService matcherService) {
        System.out.println("\n=== Batch Processing Test ===");

        // Create multiple requests
        List<MarketplaceMatcherService.MatchRequest> requests = new ArrayList<>();

        // Create diverse patient needs
        requests.add(createRequest("batch-1", "assessment", "pediatric", "telehealth", "routine", "standard", "4+"));
        requests.add(createRequest("batch-2", "intervention", "geriatric", "in-person", "urgent", "premium", "4.5+"));
        requests.add(createRequest("batch-3", "scheduling", "mental health", "hybrid", "emergency", "budget", "3+"));
        requests.add(createRequest("batch-4", "assessment", "physical rehab", "telehealth", "routine", "standard", "4+"));
        requests.add(createRequest("batch-5", "intervention", "pediatric", "in-person", "urgent", "premium", "4+"));

        long startTime = System.nanoTime();

        // Execute batch processing
        Map<String, MarketplaceMatcherService.MatchResponse> results =
            matcherService.batchMatch(requests);

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        System.out.printf("Batch processing completed in %dms for %d requests%n",
            durationMs, requests.size());

        // Calculate average per request
        double avgPerRequest = (double) durationMs / requests.size();
        System.out.printf("Average time per request: %.2fms%n", avgPerRequest);

        // Verify all requests succeeded
        int successCount = (int) results.values().stream()
            .filter(r -> r.matches().size() > 0)
            .count();

        System.out.printf("Successful matches: %d/%d%n", successCount, requests.size());

        // Display sample results
        results.entrySet().forEach(entry -> {
            System.out.printf("Request %s: %d matches in %dms%n",
                entry.getKey(), entry.getValue().matches().size(), entry.getValue().responseTimeMs());
        });
    }

    private static List<ServiceProfile> createTestProviders(int count) {
        List<ServiceProfile> providers = new ArrayList<>();

        IntStream.range(0, count).forEach(i -> {
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

            double rating = 3.0 + (i % 30) * 0.1; // Ratings from 3.0 to 5.9

            ServiceProfile provider = new ServiceProfile(
                "provider-" + String.format("%04d", i),
                "Provider " + (i + 1),
                serviceType,
                List.of("Certified OT", specialization + " specialist"),
                List.of("English"),
                Map.of(serviceType, rating),
                rating,
                1 + (i % 10), // 1-10 years experience
                createAvailabilitySlots(),
                price,
                i % 2 == 0, // Random insurance acceptance
                LocalDateTime.now()
            );

            providers.add(provider);
        });

        return providers;
    }

    private static List<AvailabilitySlot> createAvailabilitySlots() {
        return List.of(
            new AvailabilitySlot(
                LocalDateTime.now().plusDays(1).withHour(9),
                LocalDateTime.now().plusDays(1).withHour(17),
                List.of("assessment", "intervention")
            )
        );
    }

    private static MarketplaceMatcherService.MatchRequest createRequest(
            String requestId, String serviceType, String specialization,
            String deliveryMode, String urgency, String priceRange, String ratingThreshold) {
        return new MarketplaceMatcherService.MatchRequest(
            requestId,
            new NDimensionalCoordinate(serviceType, specialization, deliveryMode, urgency, priceRange, ratingThreshold),
            "cosine",
            5
        );
    }
}