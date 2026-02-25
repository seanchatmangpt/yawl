package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * N-dimensional Marketplace Matcher Service for GregVerse OT patient matching.
 *
 * Provides matching capabilities with multiple algorithms and performance optimization
 * for sub-100ms matching latency with 1000+ providers.
 */
public class MarketplaceMatcherService {

    private final Map<String, MatchingAlgorithm> algorithms;
    private final Map<String, ServiceProfile> providerRegistry;
    private final Map<String, List<NDimensionalCoordinate>> patientRequestCache;
    private final PerformanceMetrics metrics;

    /**
     * Creates a new MarketplaceMatcherService with default algorithms.
     */
    public MarketplaceMatcherService() {
        this.algorithms = new ConcurrentHashMap<>();
        this.providerRegistry = new ConcurrentHashMap<>();
        this.patientRequestCache = new ConcurrentHashMap<>();
        this.metrics = new PerformanceMetrics();

        // Register default algorithms
        registerAlgorithm("cosine", new CosineSimilarityMatcher());
        registerAlgorithm("constraint", new ConstraintBasedMatcher());
        registerAlgorithm("weighted", new WeightedDimensionMatcher());
    }

    /**
     * Registers a new matching algorithm.
     */
    public void registerAlgorithm(String name, MatchingAlgorithm algorithm) {
        algorithms.put(name.toLowerCase(), algorithm);
    }

    /**
     * Matches patient needs with available providers using specified algorithm.
     *
     * @param requestId Unique request identifier
     * @param patientNeeds Patient's dimensional requirements
     * @param algorithmName Name of algorithm to use
     * @param maxResults Maximum number of results
     * @return Matching result with response time metrics
     */
    public CompletableFuture<MatchResponse> matchAsync(String requestId,
                                                     NDimensionalCoordinate patientNeeds,
                                                     String algorithmName,
                                                     int maxResults) {
        long startTime = System.nanoTime();

        return CompletableFuture.supplyAsync(() -> {
            try {
                MatchingAlgorithm algorithm = algorithms.get(algorithmName.toLowerCase());
                if (algorithm == null) {
                    throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
                }

                List<ServiceProfile> providers = getAvailableProviders();

                // Apply any cached filters if applicable
                List<ServiceProfile> filteredProviders = applyPatientSpecificFilters(patientNeeds, providers);

                // Perform matching
                List<MatchResult> matches = algorithm.match(patientNeeds, filteredProviders, maxResults);

                long endTime = System.nanoTime();
                long durationMs = (endTime - startTime) / 1_000_000;

                // Update metrics
                metrics.recordMatch(algorithmName, durationMs, matches.size());

                return new MatchResponse(
                    requestId,
                    matches,
                    algorithm.getAlgorithmName(),
                    durationMs,
                    matches.size(),
                    patientNeeds
                );

            } catch (Exception e) {
                long endTime = System.nanoTime();
                long durationMs = (endTime - startTime) / 1_000_000;

                metrics.recordError(algorithmName, durationMs);

                return new MatchResponse(
                    requestId,
                    Collections.emptyList(),
                    algorithmName,
                    durationMs,
                    0,
                    patientNeeds,
                    e.getMessage()
                );
            }
        });
    }

    /**
     * Batch matching for multiple patient requests (parallel processing).
     *
     * @param requests List of patient matching requests
     * @return Map of request ID to match response
     */
    public Map<String, MatchResponse> batchMatch(List<MatchRequest> requests) {
        List<CompletableFuture<Map.Entry<String, MatchResponse>>> futures = requests.stream()
            .map(request -> matchAsync(
                request.requestId(),
                request.patientNeeds(),
                request.algorithmName(),
                request.maxResults()
            ).thenApply(response -> Map.entry(response.requestId(), response)))
            .collect(Collectors.toList());

        // Wait for all futures to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.get();
        } catch (Exception e) {
            throw new RuntimeException("Batch matching failed", e);
        }

        // Collect results
        return futures.stream()
            .filter(future -> !future.isCompletedExceptionally())
            .map(future -> {
                try {
                    return future.get();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to get match result", e);
                }
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));
    }

    /**
     * Registers a new service provider.
     */
    public void registerProvider(ServiceProfile provider) {
        providerRegistry.put(provider.providerId(), provider);
    }

    /**
     * Removes a service provider from the registry.
     */
    public void unregisterProvider(String providerId) {
        providerRegistry.remove(providerId);
    }

    /**
     * Updates a service provider's profile.
     */
    public void updateProvider(String providerId, ServiceProfile updatedProfile) {
        providerRegistry.put(providerId, updatedProfile);
    }

    /**
     * Gets performance metrics for monitoring.
     */
    public PerformanceMetrics getMetrics() {
        return metrics;
    }

    /**
     * Gets available algorithm names.
     */
    public List<String> getAvailableAlgorithms() {
        return new ArrayList<>(algorithms.keySet());
    }

    /**
     * Gets statistics about registered providers.
     */
    public ProviderStatistics getProviderStatistics() {
        return new ProviderStatistics(
            providerRegistry.size(),
            providerRegistry.values().stream()
                .mapToDouble(ServiceProfile::getAverageRating)
                .average()
                .orElse(0.0),
            providerRegistry.values().stream()
                .mapToInt(ServiceProfile::yearsExperience)
                .average()
                .orElse(0.0),
            calculateSpecializationDistribution(),
            calculatePriceDistribution()
        );
    }

    private List<ServiceProfile> getAvailableProviders() {
        // In a real implementation, this would check availability status
        return new ArrayList<>(providerRegistry.values());
    }

    private List<ServiceProfile> applyPatientSpecificFilters(NDimensionalCoordinate patientNeeds,
                                                           List<ServiceProfile> providers) {
        // Apply patient-specific filters that might be cached
        return providers.stream()
            .filter(provider -> {
                // Check if provider is available for the requested service
                if (!provider.specialty().equalsIgnoreCase(patientNeeds.d1ServiceType())) {
                    return false;
                }

                // Check rating threshold
                if (!provider.meetsRatingRequirement(patientNeeds.d6RatingThreshold())) {
                    return false;
                }

                return true;
            })
            .collect(Collectors.toList());
    }

    private Map<String, Long> calculateSpecializationDistribution() {
        return providerRegistry.values().stream()
            .collect(Collectors.groupingBy(
                ServiceProfile::specialty,
                Collectors.counting()
            ));
    }

    private Map<String, Long> calculatePriceDistribution() {
        return providerRegistry.values().stream()
            .collect(Collectors.groupingBy(
                provider -> {
                    double price = provider.pricePerHour();
                    if (price <= 75.0) return "budget";
                    if (price <= 150.0) return "standard";
                    return "premium";
                },
                Collectors.counting()
            ));
    }

    /**
     * Request for patient matching.
     */
    public record MatchRequest(
        String requestId,
        NDimensionalCoordinate patientNeeds,
        String algorithmName,
        int maxResults
    ) {}

    /**
     * Response from matching operation.
     */
    public record MatchResponse(
        String requestId,
        List<MatchResult> matches,
        String algorithmUsed,
        long responseTimeMs,
        int matchesCount,
        NDimensionalCoordinate patientNeeds,
        String errorMessage
    ) {
        public MatchResponse(String requestId, List<MatchResult> matches, String algorithmUsed,
                           long responseTimeMs, int matchesCount, NDimensionalCoordinate patientNeeds) {
            this(requestId, matches, algorithmUsed, responseTimeMs, matchesCount, patientNeeds, null);
        }
    }

    /**
     * Statistics about registered providers.
     */
    public record ProviderStatistics(
        int totalProviders,
        double averageRating,
        double averageExperienceYears,
        Map<String, Long> specializationDistribution,
        Map<String, Long> priceDistribution
    ) {}

    /**
     * Performance metrics for monitoring.
     */
    public static class PerformanceMetrics {
        private final Map<String, List<Long>> responseTimes = new ConcurrentHashMap<>();
        private final Map<String, Integer> matchCounts = new ConcurrentHashMap<>();
        private final Map<String, Integer> errorCounts = new ConcurrentHashMap<>();

        public synchronized void recordMatch(String algorithm, long responseTimeMs, int matchesCount) {
            responseTimes.computeIfAbsent(algorithm, k -> new ArrayList<>()).add(responseTimeMs);
            matchCounts.merge(algorithm, 1, Integer::sum);
        }

        public synchronized void recordError(String algorithm, long responseTimeMs) {
            responseTimes.computeIfAbsent(algorithm, k -> new ArrayList<>()).add(responseTimeMs);
            errorCounts.merge(algorithm, 1, Integer::sum);
        }

        public synchronized double getAverageResponseTime(String algorithm) {
            List<Long> times = responseTimes.get(algorithm);
            return times == null || times.isEmpty() ? 0.0 :
                   times.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }

        public synchronized int getTotalMatches(String algorithm) {
            return matchCounts.getOrDefault(algorithm, 0);
        }

        public synchronized int getTotalErrors(String algorithm) {
            return errorCounts.getOrDefault(algorithm, 0);
        }

        public synchronized double getSuccessRate(String algorithm) {
            int total = getTotalMatches(algorithm) + getTotalErrors(algorithm);
            return total == 0 ? 0.0 :
                   (double) getTotalMatches(algorithm) / total;
        }

        public synchronized Map<String, Double> getAlgorithmPerformance() {
            Map<String, Double> performance = new HashMap<>();
            for (String algorithm : responseTimes.keySet()) {
                performance.put(algorithm, getAverageResponseTime(algorithm));
            }
            return performance;
        }

        public synchronized void clear() {
            responseTimes.clear();
            matchCounts.clear();
            errorCounts.clear();
        }
    }
}