/*
 * Copyright 2026 YAWL Foundation
 */
package org.yawlfoundation.yawl.mcp.a2a.gregverse.ai.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Progress prediction containing ML-based predictions.
 *
 * @param patientId patient identifier
 * @param predictedProgress predicted progress score (0.0-1.0)
 * @param confidence confidence level (0.0-1.0)
 * @param milestonePredictions predicted milestones
 * @param riskFactors identified risk factors
 * @param predictedAt prediction timestamp
 */
public record ProgressPrediction(
    String patientId,
    double predictedProgress,
    double confidence,
    Map<String, Double> milestonePredictions,
    Map<String, String> riskFactors,
    Instant predictedAt
) {
    public ProgressPrediction {
        Objects.requireNonNull(patientId, "Patient ID cannot be null");
        if (milestonePredictions == null) {
            milestonePredictions = Map.of();
        }
        if (riskFactors == null) {
            riskFactors = Map.of();
        }
        if (predictedAt == null) {
            predictedAt = Instant.now();
        }
    }
}
