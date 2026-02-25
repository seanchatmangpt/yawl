/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Outcome data for tracking therapy session results.
 *
 * @param sessionId session identifier
 * @param patientId patient identifier
 * @param sessionOutcome session outcome (SUCCESS, PARTIAL, NO_PROGRESS, REGRESSION)
 * @param goalAchievement goal achievement scores
 * @param patientSatisfaction patient satisfaction score (1-5)
 * @param therapistNotes therapist notes
 * @param recordedAt recording timestamp
 */
public record OutcomeData(
    String sessionId,
    String patientId,
    String sessionOutcome,
    Map<String, Double> goalAchievement,
    int patientSatisfaction,
    String therapistNotes,
    Instant recordedAt
) {
    public OutcomeData {
        Objects.requireNonNull(sessionId, "Session ID cannot be null");
        Objects.requireNonNull(patientId, "Patient ID cannot be null");
        if (goalAchievement == null) {
            goalAchievement = Map.of();
        }
        if (sessionOutcome == null || sessionOutcome.isBlank()) {
            sessionOutcome = "PARTIAL";
        }
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }
}
