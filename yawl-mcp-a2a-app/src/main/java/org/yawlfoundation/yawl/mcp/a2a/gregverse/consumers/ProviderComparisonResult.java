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
import java.util.List;

/**
 * Represents the result of comparing multiple service providers.
 *
 * @param patientId the patient ID for this comparison
 * @param providerScores list of all providers with their scores
 * @param bestMatch the top-rated provider recommendation
 * @param comparisonTimestamp when the comparison was performed
 */
public record ProviderComparisonResult(
    String patientId,
    List<ProviderScore> providerScores,
    ProviderScore bestMatch,
    Instant comparisonTimestamp
) {

    /**
     * Creates a new comparison result.
     */
    public ProviderComparisonResult {
        providerScores = List.copyOf(providerScores);
    }

    /**
     * Returns the top N providers from the comparison.
     *
     * @param count the number of top providers to return
     * @return list of top providers
     */
    public List<ProviderScore> getTopProviders(int count) {
        return providerScores.stream()
            .limit(count)
            .toList();
    }

    /**
     * Returns the average rating of all compared providers.
     */
    public double getAverageRating() {
        return providerScores.stream()
            .mapToInt(ProviderScore::overallScore)
            .average()
            .orElse(0.0);
    }
}