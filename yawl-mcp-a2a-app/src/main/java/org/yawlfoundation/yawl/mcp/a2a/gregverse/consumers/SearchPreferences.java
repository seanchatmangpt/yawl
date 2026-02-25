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

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents preferences for searching services in the marketplace.
 *
 * @param patientId the patient ID
 * @param searchQuery the search query string
 * @param maxResults maximum number of results to return
 * @param maxPrice maximum price willing to pay
 * @param minRating minimum acceptable rating
 * @param preferredSpecializations list of preferred specializations
 * @param includeInactive whether to include inactive listings
 * @param sortBy sorting preference (rating, price, date)
 * @param sortOrder sorting order (asc, desc)
 */
public record SearchPreferences(
    String patientId,
    String searchQuery,
    Integer maxResults,
    BigDecimal maxPrice,
    BigDecimal minRating,
    java.util.List<String> preferredSpecializations,
    Boolean includeInactive,
    String sortBy,
    String sortOrder
) {

    /**
     * Sort options.
     */
    public static final String SORT_BY_RATING = "rating";
    public static final String SORT_BY_PRICE = "price";
    public static final String SORT_BY_DATE = "date";
    public static final String SORT_BY_RELEVANCE = "relevance";

    public static final String SORT_ASC = "asc";
    public static final String SORT_DESC = "desc";

    /**
     * Creates search preferences with required fields.
     */
    public SearchPreferences {
        Objects.requireNonNull(patientId, "Patient ID is required");
        maxResults = maxResults != null ? maxResults : 10;
        sortBy = sortBy != null ? sortBy : SORT_BY_RATING;
        sortOrder = sortOrder != null ? sortOrder : SORT_DESC;
        preferredSpecializations = preferredSpecializations != null ?
            java.util.List.copyOf(preferredSpecializations) : java.util.List.of();
        includeInactive = includeInactive != null ? includeInactive : false;
    }

    /**
     * Creates search preferences for a specific patient.
     */
    public static SearchPreferences forPatient(String patientId) {
        return new SearchPreferences(
            patientId,
            null,
            10,
            null,
            null,
            null,
            false,
            SORT_BY_RATING,
            SORT_DESC
        );
    }

    /**
     * Creates search preferences with a specific query.
     */
    public static SearchPreferences withQuery(String patientId, String query) {
        return new SearchPreferences(
            patientId,
            query,
            10,
            null,
            null,
            null,
            false,
            SORT_BY_RELEVANCE,
            SORT_DESC
        );
    }

    /**
     * Creates search preferences with price constraints.
     */
    public static SearchPreferences withPriceLimit(
            String patientId, BigDecimal maxPrice, Integer maxResults) {
        return new SearchPreferences(
            patientId,
            null,
            maxResults,
            maxPrice,
            null,
            null,
            false,
            SORT_BY_PRICE,
            SORT_ASC
        );
    }

    /**
     * Creates search preferences with rating requirements.
     */
    public static SearchPreferences withRatingRequirement(
            String patientId, BigDecimal minRating, Integer maxResults) {
        return new SearchPreferences(
            patientId,
            null,
            maxResults,
            null,
            minRating,
            null,
            false,
            SORT_BY_RATING,
            SORT_DESC
        );
    }

    /**
     * Returns true if preferences include a specific query.
     */
    public boolean hasQuery() {
        return searchQuery != null && !searchQuery.trim().isEmpty();
    }

    /**
     * Returns true if preferences include price limits.
     */
    public boolean hasPriceLimit() {
        return maxPrice != null;
    }

    /**
     * Returns true if preferences include rating requirements.
     */
    public boolean hasRatingRequirement() {
        return minRating != null;
    }

    /**
     * Returns true if preferences include specialization preferences.
     */
    public boolean hasSpecializationPreferences() {
        return preferredSpecializations != null && !preferredSpecializations.isEmpty();
    }

    /**
     * Validates the sort parameters.
     */
    public boolean isValidSort() {
        return switch (sortBy) {
            case SORT_BY_RATING, SORT_BY_PRICE, SORT_BY_DATE, SORT_BY_RELEVANCE -> true;
            default -> false;
        };
    }

    /**
     * Validates the sort order parameters.
     */
    public boolean isValidSortOrder() {
        return SORT_ASC.equals(sortOrder) || SORT_DESC.equals(sortOrder);
    }

    /**
     * Gets the sort order as a comparator direction.
     */
    public boolean isAscendingOrder() {
        return SORT_ASC.equals(sortOrder);
    }

    /**
     * Gets the default sort field based on available preferences.
     */
    public String getDefaultSortField() {
        if (hasPriceLimit()) return SORT_BY_PRICE;
        if (hasRatingRequirement()) return SORT_BY_RATING;
        return SORT_BY_RATING;
    }
}