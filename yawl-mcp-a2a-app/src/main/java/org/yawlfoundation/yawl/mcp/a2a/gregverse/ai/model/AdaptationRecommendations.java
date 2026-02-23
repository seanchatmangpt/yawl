/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adaptation recommendations for therapy plan modifications.
 *
 * @param patientId patient identifier
 * @param recommendedChanges recommended changes
 * @param rationale rationale for adaptations
 * @param priorityChanges high-priority changes
 * @param expectedImpact expected impact of changes
 * @param generatedAt generation timestamp
 */
public record AdaptationRecommendations(
    String patientId,
    List<String> recommendedChanges,
    Map<String, String> rationale,
    List<String> priorityChanges,
    Map<String, Double> expectedImpact,
    Instant generatedAt
) {
    public AdaptationRecommendations {
        Objects.requireNonNull(patientId, "Patient ID cannot be null");
        if (recommendedChanges == null) {
            recommendedChanges = List.of();
        }
        if (rationale == null) {
            rationale = Map.of();
        }
        if (priorityChanges == null) {
            priorityChanges = List.of();
        }
        if (expectedImpact == null) {
            expectedImpact = Map.of();
        }
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }
}
