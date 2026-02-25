/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Therapy recommendations containing AI-generated suggestions.
 *
 * @param patientId patient identifier
 * @param therapyGoals therapy goals addressed
 * @param interventions recommended interventions
 * @param suggestions raw suggestions from AI
 * @param generatedAt generation timestamp
 */
public record TherapyRecommendations(
    String patientId,
    List<TherapyGoal> therapyGoals,
    List<TherapyIntervention> interventions,
    List<InterventionSuggestion> suggestions,
    Instant generatedAt
) {
    public TherapyRecommendations {
        Objects.requireNonNull(patientId, "Patient ID cannot be null");
        if (therapyGoals == null) {
            therapyGoals = List.of();
        }
        if (interventions == null) {
            interventions = List.of();
        }
        if (suggestions == null) {
            suggestions = List.of();
        }
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }
}
