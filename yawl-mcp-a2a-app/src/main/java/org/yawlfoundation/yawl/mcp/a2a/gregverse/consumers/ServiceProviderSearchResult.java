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

import org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation.GregVerseMarketplace;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation.GregVerseMarketplace.MarketplaceEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the result of searching for service providers in the marketplace.
 *
 * @param entryId the marketplace entry ID
 * @param providerId the provider agent ID
 * @param providerName the provider's display name
 * @param skillName the name of the service skill
 * @param description detailed service description
 * @param priceInCredits the price for the service
 * @param specializations set of specializations offered
 * @param tags searchable tags
 * @param averageRating average provider rating (1-5)
 * @param relevanceScore relevance score for the search query
 * @param hasAvailability whether provider has availability
 * @param conditionsTreated medical conditions treated
 */
public record ServiceProviderSearchResult(
    String entryId,
    String providerId,
    String providerName,
    String skillName,
    String description,
    BigDecimal priceInCredits,
    Set<String> specializations,
    Set<String> tags,
    BigDecimal averageRating,
    int relevanceScore,
    boolean hasAvailability,
    Set<String> conditionsTreated
) {

    /**
     * Creates a ServiceProviderSearchResult from a marketplace entry.
     *
     * @param entry the marketplace entry
     * @param marketplace the marketplace instance for rating lookup
     * @return a new ServiceProviderSearchResult
     */
    public static ServiceProviderSearchResult fromMarketplaceEntry(
            MarketplaceEntry entry, GregVerseMarketplace marketplace) {

        var specializations = extractSpecializations(entry);
        var conditionsTreated = extractConditionsTreated(entry);
        var hasAvailability = checkAvailability(entry);
        var averageRating = marketplace.getAverageRating(entry.skillId());

        return new ServiceProviderSearchResult(
            entry.entryId(),
            entry.sellerAgentId(),
            "Provider " + entry.sellerAgentId(), // In real implementation, would map to provider name
            entry.skillName(),
            entry.description(),
            entry.priceInCredits(),
            specializations,
            new HashSet<>(entry.tags()),
            averageRating,
            calculateRelevanceScore(entry),
            hasAvailability,
            conditionsTreated
        );
    }

    /**
     * Returns true if this provider offers the given specialization.
     */
    public boolean hasSpecialization(String specialization) {
        return specializations.contains(specialization);
    }

    /**
     * Returns true if this provider treats the given condition.
     */
    public boolean treatsCondition(String condition) {
        return conditionsTreated.contains(condition.toLowerCase());
    }

    // Helper methods

    private static Set<String> extractSpecializations(MarketplaceEntry entry) {
        // In a real implementation, this would be parsed from the description or tags
        return entry.tags().stream()
            .filter(tag -> tag.contains("specialization") || tag.contains("expert"))
            .collect(Collectors.toSet());
    }

    private static Set<String> extractConditionsTreated(MarketplaceEntry entry) {
        // In a real implementation, this would be parsed from the description
        var description = entry.description().toLowerCase();
        return Set.of(
            "stroke", "depression", "arthritis", "chronic pain", "anxiety",
            "brain injury", "fracture", "post-surgical"
        ).stream()
            .filter(condition -> description.contains(condition))
            .collect(Collectors.toSet());
    }

    private static boolean checkAvailability(MarketplaceEntry entry) {
        // In real implementation, would check actual availability calendar
        return entry.active(); // Simple implementation assumes active = available
    }

    private static int calculateRelevanceScore(MarketplaceEntry entry) {
        // Simple relevance calculation based on listing age and activity
        var ageDays = java.time.Duration.between(entry.listedAt(), java.time.Instant.now()).toDays();
        return Math.max(0, 100 - (int) ageDays * 2); // Newer listings have higher score
    }
}