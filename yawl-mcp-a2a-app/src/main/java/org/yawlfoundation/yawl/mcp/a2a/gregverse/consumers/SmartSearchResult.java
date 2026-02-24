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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.consumers;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents the result of a smart search based on patient profile and preferences.
 *
 * @param patientId the patient ID
 * @param searchResults list of matching service providers
 * @param personalizedRecommendations personalized recommendations
 * @param searchedSpecializations specializations that were searched
 * @param searchTimestamp when the search was performed
 */
public record SmartSearchResult(
    String patientId,
    List<ServiceProviderSearchResult> searchResults,
    List<String> personalizedRecommendations,
    List<String> searchedSpecializations,
    Instant searchTimestamp
) {

    /**
     * Creates a smart search result.
     */
    public SmartSearchResult {
        searchResults = List.copyOf(searchResults);
        personalizedRecommendations = List.copyOf(personalizedRecommendations);
        searchedSpecializations = List.copyOf(searchedSpecializations);
    }

    /**
     * Gets the total number of search results.
     */
    public int getResultCount() {
        return searchResults.size();
    }

    /**
     * Returns true if the search found any results.
     */
    public boolean hasResults() {
        return !searchResults.isEmpty();
    }

    /**
     * Returns true if the search has recommendations.
     */
    public boolean hasRecommendations() {
        return !personalizedRecommendations.isEmpty();
    }

    /**
     * Gets the top N results from the search.
     */
    public List<ServiceProviderSearchResult> getTopResults(int count) {
        return searchResults.stream()
            .limit(count)
            .collect(Collectors.toList());
    }

    /**
     * Gets the best match from the search results.
     */
    public ServiceProviderSearchResult getBestMatch() {
        return searchResults.isEmpty() ? null : searchResults.get(0);
    }

    /**
     * Gets average rating of all results.
     */
    public double getAverageRating() {
        if (searchResults.isEmpty()) return 0.0;

        return searchResults.stream()
            .mapToDouble(r -> r.averageRating().doubleValue())
            .average()
            .orElse(0.0);
    }

    /**
     * Gets average price of all results.
     */
    public double getAveragePrice() {
        if (searchResults.isEmpty()) return 0.0;

        return searchResults.stream()
            .mapToDouble(r -> r.priceInCredits().doubleValue())
            .average()
            .orElse(0.0);
    }

    /**
     * Gets price range of all results.
     */
    public Map<String, Double> getPriceRange() {
        if (searchResults.isEmpty()) {
            return Map.of("min", 0.0, "max", 0.0);
        }

        var prices = searchResults.stream()
            .mapToDouble(r -> r.priceInCredits().doubleValue())
            .toArray();

        return Map.of(
            "min", prices.length > 0 ? Arrays.stream(prices).min().orElse(0.0) : 0.0,
            "max", prices.length > 0 ? Arrays.stream(prices).max().orElse(0.0) : 0.0
        );
    }

    /**
     * Gets rating distribution of results.
     */
    public Map<Integer, Long> getRatingDistribution() {
        return searchResults.stream()
            .collect(Collectors.groupingBy(
                r -> r.averageRating().intValue(),
                Collectors.counting()
            ));
    }

    /**
     * Gets count of results by specialization.
     */
    public Map<String, Long> getSpecializationCounts() {
        return searchResults.stream()
            .flatMap(r -> r.specializations().stream())
            .collect(Collectors.groupingBy(
                s -> s,
                Collectors.counting()
            ));
    }

    /**
     * Gets count of results by availability status.
     */
    public Map<String, Long> getAvailabilityCounts() {
        var available = searchResults.stream()
            .filter(ServiceProviderSearchResult::hasAvailability)
            .count();

        return Map.of(
            "available", available,
            "unavailable", searchResults.size() - available
        );
    }

    /**
     * Returns confidence level of the search results.
     */
    public String getConfidenceLevel() {
        if (searchResults.isEmpty()) return "No results found";

        if (searchResults.size() >= 10 && getAverageRating() >= 4.0) {
            return "High confidence";
        }

        if (searchResults.size() >= 5) {
            return "Medium confidence";
        }

        if (searchResults.size() >= 1) {
            return "Low confidence";
        }

        return "No results found";
    }

    /**
     * Gets relevance score of the search.
     */
    public double getRelevanceScore() {
        if (searchResults.isEmpty()) return 0.0;

        var matchScore = searchResults.size() * 10; // 10 points per result
        var ratingScore = getAverageRating() * 10; // 10 points for perfect rating
        var specializationScore = searchedSpecializations.size() * 5; // 5 points per specialization

        return Math.min(100, matchScore + ratingScore + specializationScore);
    }

    /**
     * Gets improvement suggestions for future searches.
     */
    public List<String> getImprovementSuggestions() {
        var suggestions = new java.util.ArrayList<String>();

        if (searchResults.isEmpty()) {
            suggestions.add("Try adjusting your search criteria or consider broader specializations");
        }

        if (getAverageRating() < 3.0) {
            suggestions.add("Consider increasing your minimum rating requirement");
        }

        if (getAveragePrice() > 500) {
            suggestions.add("Try setting a maximum price limit to find more affordable options");
        }

        if (searchedSpecializations.size() < 3) {
            suggestions.add("Consider searching multiple specializations for more options");
        }

        return suggestions;
    }
}