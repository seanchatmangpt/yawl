/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Progress data containing patient progress information.
 *
 * @param patientId patient identifier
 * @param sessionsCompleted number of sessions completed
 * @param overallProgress overall progress percentage (0-100)
 * @param goalProgress progress per goal
 * @param improvements list of improvements
 * @param challenges list of challenges
 * @param metrics additional metrics
 */
public record ProgressData(
    String patientId,
    int sessionsCompleted,
    double overallProgress,
    Map<String, Double> goalProgress,
    List<String> improvements,
    List<String> challenges,
    Map<String, Object> metrics
) {
    public ProgressData {
        Objects.requireNonNull(patientId, "Patient ID cannot be null");
        if (goalProgress == null) {
            goalProgress = Map.of();
        }
        if (improvements == null) {
            improvements = List.of();
        }
        if (challenges == null) {
            challenges = List.of();
        }
        if (metrics == null) {
            metrics = Map.of();
        }
    }
}
