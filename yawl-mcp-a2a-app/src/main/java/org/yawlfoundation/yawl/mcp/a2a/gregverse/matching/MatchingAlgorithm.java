package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

import java.util.List;

/**
 * Interface for matching algorithms in GregVerse OT marketplace.
 */
public interface MatchingAlgorithm {

    /**
     * Matches patient needs with available OT service providers.
     *
     * @param patientNeeds Patient's dimensional requirements
     * @param availableProviders List of available providers
     * @param maxResults Maximum number of results to return
     * @return List of matches sorted by relevance
     */
    List<MatchResult> match(NDimensionalCoordinate patientNeeds,
                          List<ServiceProfile> availableProviders,
                          int maxResults);

    /**
     * Gets the name of this matching algorithm.
     */
    String getAlgorithmName();
}