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
import java.util.HashMap;

/**
 * Represents the comprehensive score for a service provider across multiple criteria.
 *
 * @param providerId the provider ID
 * @param overallScore the total weighted score
 * @param ageMatchScore match based on patient age compatibility
 * @param conditionScore match based on condition treatment experience
 * @param ratingScore provider reputation score
 * @param priceScore affordability score
 * @param availabilityScore availability score
 * @param specialtyScore specialization match score
 * @param detailedScores individual category scores for transparency
 */
public record ProviderScore(
    String providerId,
    int overallScore,
    int ageMatchScore,
    int conditionScore,
    int ratingScore,
    int priceScore,
    int availabilityScore,
    int specialtyScore,
    java.util.Map<String, Integer> detailedScores
) {

    /**
     * Creates a new provider score from individual components.
     */
    public ProviderScore {
        detailedScores = java.util.Map.copyOf(detailedScores);
    }

    /**
     * Creates a basic provider score with minimal information.
     */
    public static ProviderScore basic(String providerId, int overallScore) {
        var detailedScores = new HashMap<String, Integer>();
        detailedScores.put("overall", overallScore);

        return new ProviderScore(
            providerId,
            overallScore,
            0, 0, 0, 0, 0, 0,
            detailedScores
        );
    }
}