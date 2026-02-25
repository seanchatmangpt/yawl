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

/**
 * Represents the result of rating a service provider.
 *
 * @param patientId the patient who provided the rating
 * @param providerId the provider that was rated
 * @param serviceId the service that was rated
 * @param rating the rating given (1-5)
 * @param feedback the feedback provided
 * @param updatedRating the updated rating information
 * @param ratingTimestamp when the rating was submitted
 */
public record ProviderRatingResult(
    String patientId,
    String providerId,
    String serviceId,
    int rating,
    String feedback,
    ServiceProviderRating updatedRating,
    Instant ratingTimestamp
) {

    /**
     * Creates a rating result.
     */
    public ProviderRatingResult {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
    }

    /**
     * Returns true if feedback was provided.
     */
    public boolean hasFeedback() {
        return feedback != null && !feedback.trim().isEmpty();
    }

    /**
     * Gets a formatted rating display.
     */
    public String getFormattedRating() {
        return rating + " star" + (rating != 1 ? "s" : "");
    }

    /**
     * Gets a user-friendly rating message.
     */
    public String getRatingMessage() {
        return switch (rating) {
            case 5 -> "Excellent service! Provider exceeded expectations.";
            case 4 -> "Good service with room for improvement.";
            case 3 -> "Average service, met basic requirements.";
            case 2 -> "Below average service needs improvement.";
            case 1 -> "Poor service did not meet expectations.";
            default -> "Invalid rating";
        };
    }

    /**
     * Returns the rating quality description.
     */
    public String getRatingQuality() {
        return updatedRating.getRatingQuality();
    }

    /**
     * Returns true if this was the patient's first rating of this provider.
     */
    public boolean isFirstRating() {
        return updatedRating.totalRatings() == 1;
    }

    /**
     * Returns true if this improves the provider's rating.
     */
    public boolean isImprovingRating() {
        return rating > 3; // Assuming 3 is average
    }

    /**
     * Returns true if this decreases the provider's rating.
     */
    public boolean isDecreasingRating() {
        return rating < 3;
    }
}