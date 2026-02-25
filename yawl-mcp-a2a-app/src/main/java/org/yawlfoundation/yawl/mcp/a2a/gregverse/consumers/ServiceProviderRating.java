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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a provider rating from patient perspective.
 *
 * @param patientId the patient who provided the rating
 * @param providerId the provider being rated
 * @param serviceId the service that was rated
 * @param totalRatings total number of ratings by this patient
 * @param averageRating average rating across all ratings
 * @param individualRatings list of individual rating entries
 */
public record ServiceProviderRating(
    String patientId,
    String providerId,
    String serviceId,
    long totalRatings,
    BigDecimal averageRating,
    List<IndividualRating> individualRatings
) {

    /**
     * Individual rating entry.
     *
     * @param rating the rating value (1-5)
     * @param feedback optional feedback text
     * @param timestamp when the rating was given
     */
    public record IndividualRating(
        int rating,
        String feedback,
        Instant timestamp
    ) {}

    /**
     * Creates an empty rating for a new patient-provider relationship.
     */
    public static ServiceProviderRating empty(String patientId, String providerId, String serviceId) {
        return new ServiceProviderRating(
            patientId,
            providerId,
            serviceId,
            0L,
            BigDecimal.ZERO,
            new ArrayList<>()
        );
    }

    /**
     * Creates a new rating with a single rating.
     */
    public static ServiceProviderRating initialRating(
            String patientId, String providerId, String serviceId, int rating, String feedback) {

        Objects.requireNonNull(patientId);
        Objects.requireNonNull(providerId);
        Objects.requireNonNull(serviceId);

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        var individual = new IndividualRating(rating, feedback, Instant.now());
        var total = BigDecimal.valueOf(rating);

        return new ServiceProviderRating(
            patientId,
            providerId,
            serviceId,
            1L,
            total,
            List.of(individual)
        );
    }

    /**
     * Adds a new rating and returns an updated rating record.
     */
    public ServiceProviderRating addRating(int newRating, String feedback) {
        if (newRating < 1 || newRating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        var newIndividual = new IndividualRating(newRating, feedback, Instant.now());
        var newIndividualList = new ArrayList<>(individualRatings);
        newIndividualList.add(newIndividual);

        var newTotal = totalRatings + 1;
        var weightedSum = BigDecimal.ZERO;

        for (var rating : newIndividualList) {
            weightedSum = weightedSum.add(BigDecimal.valueOf(rating.rating()));
        }

        var newAverage = weightedSum.divide(
            BigDecimal.valueOf(newTotal), 2, RoundingMode.HALF_UP
        );

        return new ServiceProviderRating(
            patientId,
            providerId,
            serviceId,
            newTotal,
            newAverage,
            newIndividualList
        );
    }

    /**
     * Returns true if this patient has rated this provider.
     */
    public boolean hasRating() {
        return totalRatings > 0;
    }

    /**
     * Returns true if the patient has provided feedback.
     */
    public boolean hasFeedback() {
        return individualRatings.stream()
            .anyMatch(rating -> rating.feedback() != null && !rating.feedback().isBlank());
    }

    /**
     * Gets the latest rating.
     */
    public IndividualRating getLatestRating() {
        return individualRatings.isEmpty() ? null : individualRatings.get(individualRatings.size() - 1);
    }

    /**
     * Gets all feedback from this patient.
     */
    public List<String> getAllFeedback() {
        return individualRatings.stream()
            .map(IndividualRating::feedback)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Returns the rating quality based on the number of ratings.
     */
    public String getRatingQuality() {
        if (totalRatings == 0) return "No ratings yet";
        if (totalRatings == 1) return "Single rating";
        if (totalRatings < 5) return "Limited feedback";
        if (totalRatings < 10) return "Moderate feedback";
        return "Well-established rating";
    }

    /**
     * Returns true if the average rating is above 4.0.
     */
    public boolean isHighlyRated() {
        return averageRating.compareTo(BigDecimal.valueOf(4.0)) >= 0;
    }

    /**
     * Returns true if the average rating is below 2.0.
     */
    public boolean isPoorlyRated() {
        return averageRating.compareTo(BigDecimal.valueOf(2.0)) < 0;
    }
}