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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit test for MarketplaceMatcherService functionality.
 * Verifies that the service correctly matches patient needs with providers.
 */
class MarketplaceMatcherServiceTest {

    private MarketplaceMatcherService matcherService;
    private NDimensionalCoordinate patientNeeds;

    @BeforeEach
    void setUp() {
        matcherService = new MarketplaceMatcherService();

        // Register test providers
        List<ServiceProfile> testProviders = createTestProviders();
        testProviders.forEach(matcherService::registerProvider);

        // Set up test patient needs
        patientNeeds = new NDimensionalCoordinate(
            "assessment",
            "pediatric",
            "telehealth",
            "routine",
            "standard",
            "4+"
        );
    }

    @Test
    @DisplayName("Should match providers using cosine similarity algorithm")
    void testCosineSimilarityMatching() {
        CompletableFuture<MarketplaceMatcherService.MatchResponse> future =
            matcherService.matchAsync("test-cosine", patientNeeds, "cosine", 5);

        MarketplaceMatcherService.MatchResponse response = future.join();

        assertNotNull(response);
        assertEquals("test-cosine", response.requestId());
        assertEquals("CosineSimilarityMatcher", response.algorithmUsed());
        assertTrue(response.responseTimeMs() >= 0);
        assertNotNull(response.matches());
        assertNotNull(response.patientNeeds());
    }

    @Test
    @DisplayName("Should match providers using constraint-based algorithm")
    void testConstraintBasedMatching() {
        CompletableFuture<MarketplaceMatcherService.MatchResponse> future =
            matcherService.matchAsync("test-constraint", patientNeeds, "constraint", 5);

        MarketplaceMatcherService.MatchResponse response = future.join();

        assertNotNull(response);
        assertEquals("test-constraint", response.requestId());
        assertEquals("ConstraintBasedMatcher", response.algorithmUsed());
        assertTrue(response.responseTimeMs() >= 0);
        assertNotNull(response.matches());
    }

    @Test
    @DisplayName("Should match providers using weighted dimension algorithm")
    void testWeightedDimensionMatching() {
        CompletableFuture<MarketplaceMatcherService.MatchResponse> future =
            matcherService.matchAsync("test-weighted", patientNeeds, "weighted", 5);

        MarketplaceMatcherService.MatchResponse response = future.join();

        assertNotNull(response);
        assertEquals("test-weighted", response.requestId());
        assertEquals("WeightedDimensionMatcher", response.algorithmUsed());
        assertTrue(response.responseTimeMs() >= 0);
        assertNotNull(response.matches());
    }

    @Test
    @DisplayName("Should return empty results when no providers match criteria")
    void testNoMatchingProviders() {
        // Create patient needs that won't match any registered providers
        NDimensionalCoordinate uniqueNeeds = new NDimensionalCoordinate(
            "surgery",
            "cardiac",
            "in-person",
            "emergency",
            "premium",
            "5+"
        );

        CompletableFuture<MarketplaceMatcherService.MatchResponse> future =
            matcherService.matchAsync("test-no-match", uniqueNeeds, "cosine", 5);

        MarketplaceMatcherService.MatchResponse response = future.join();

        assertNotNull(response);
        assertEquals("test-no-match", response.requestId());
        assertTrue(response.matches().isEmpty());
        assertEquals(0, response.matchesCount());
    }

    @Test
    @DisplayName("Should handle unknown algorithm gracefully")
    void testUnknownAlgorithm() {
        CompletableFuture<MarketplaceMatcherService.MatchResponse> future =
            matcherService.matchAsync("test-unknown", patientNeeds, "nonexistent", 5);

        MarketplaceMatcherService.MatchResponse response = future.join();

        assertNotNull(response);
        assertEquals("test-unknown", response.requestId());
        assertTrue(response.matches().isEmpty());
        assertEquals(0, response.matchesCount());
        assertNotNull(response.errorMessage());
        assertTrue(response.errorMessage().contains("Unknown algorithm"));
    }

    @Test
    @DisplayName("Should perform batch matching successfully")
    void testBatchMatching() {
        List<MarketplaceMatcherService.MatchRequest> requests = List.of(
            new MarketplaceMatcherService.MatchRequest(
                "batch-1",
                new NDimensionalCoordinate("assessment", "pediatric", "telehealth", "routine", "standard", "4+"),
                "cosine",
                3
            ),
            new MarketplaceMatcherService.MatchRequest(
                "batch-2",
                new NDimensionalCoordinate("intervention", "geriatric", "in-person", "urgent", "premium", "4.5+"),
                "weighted",
                5
            )
        );

        Map<String, MarketplaceMatcherService.MatchResponse> results =
            matcherService.batchMatch(requests);

        assertNotNull(results);
        assertEquals(2, results.size());
        assertTrue(results.containsKey("batch-1"));
        assertTrue(results.containsKey("batch-2"));

        // Verify all responses are valid
        results.values().forEach(response -> {
            assertNotNull(response);
            assertNotNull(response.matches());
            assertTrue(response.responseTimeMs() >= 0);
        });
    }

    @Test
    @DisplayName("Should provide performance metrics")
    void testPerformanceMetrics() {
        // Perform some matches to generate metrics
        matcherService.matchAsync("metrics-test-1", patientNeeds, "cosine", 5).join();
        matcherService.matchAsync("metrics-test-2", patientNeeds, "cosine", 5).join();

        MarketplaceMatcherService.PerformanceMetrics metrics = matcherService.getMetrics();

        assertNotNull(metrics);
        assertTrue(metrics.getTotalMatches("cosine") >= 0);
        assertTrue(metrics.getTotalErrors("cosine") >= 0);
        assertEquals(0.0, metrics.getAverageResponseTime("nonexistent")); // Should return 0 for unknown algorithm
    }

    @Test
    @DisplayName("Should list available algorithms")
    void testAvailableAlgorithms() {
        List<String> algorithms = matcherService.getAvailableAlgorithms();

        assertNotNull(algorithms);
        assertFalse(algorithms.isEmpty());
        assertTrue(algorithms.contains("cosine"));
        assertTrue(algorithms.contains("constraint"));
        assertTrue(algorithms.contains("weighted"));
    }

    @Test
    @DisplayName("Should provide provider statistics")
    void testProviderStatistics() {
        MarketplaceMatcherService.ProviderStatistics stats = matcherService.getProviderStatistics();

        assertNotNull(stats);
        assertEquals(3, stats.totalProviders()); // We created 3 test providers
        assertTrue(stats.averageRating() >= 0);
        assertTrue(stats.averageExperienceYears() >= 0);
        assertNotNull(stats.specializationDistribution());
        assertNotNull(stats.priceDistribution());
    }

    private List<ServiceProfile> createTestProviders() {
        return List.of(
            createTestProvider("provider-1", "pediatric", 4.5, 100.0),
            createTestProvider("provider-2", "geriatric", 4.0, 150.0),
            createTestProvider("provider-3", "mental health", 5.0, 200.0)
        );
    }

    private ServiceProfile createTestProvider(String providerId, String specialty,
                                            double rating, double price) {
        return new ServiceProfile(
            providerId,
            "Test " + providerId,
            "assessment",
            List.of("Certified OT", specialty + " specialist"),
            List.of("English"),
            Map.of("assessment", rating),
            rating,
            5,
            List.of(new AvailabilitySlot(
                LocalDateTime.now().plusDays(1).withHour(9),
                LocalDateTime.now().plusDays(1).withHour(17),
                List.of("assessment", "intervention")
            )),
            price,
            true,
            LocalDateTime.now()
        );
    }
}