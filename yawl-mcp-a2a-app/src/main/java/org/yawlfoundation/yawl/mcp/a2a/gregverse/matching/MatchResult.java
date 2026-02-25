package org.yawlfoundation.yawl.mcp.a2a.gregverse.matching;

/**
 * Result of a matching operation with score and provider info.
 */
public record MatchResult(
    ServiceProfile provider,
    double similarityScore,
    double rank,
    String matchingReason
) {}