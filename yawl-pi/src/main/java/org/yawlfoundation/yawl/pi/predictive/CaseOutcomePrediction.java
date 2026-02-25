/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.pi.predictive;

import java.time.Instant;

/**
 * Immutable prediction result for case completion/failure outcome.
 *
 * <p>Provides probability estimates and risk assessments for a specific case,
 * derived either from ONNX model inference or DNA oracle historical analysis.
 *
 * @param caseId Workflow case being predicted
 * @param completionProbability Probability of successful completion (0.0 to 1.0)
 * @param riskScore Normalized risk level (0.0 to 1.0)
 * @param primaryRiskFactor Human-readable description of highest risk (e.g., "long execution time")
 * @param fromOnnxModel True if prediction came from ONNX model, false if from DNA oracle
 * @param predictedAt Timestamp of prediction generation
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record CaseOutcomePrediction(
    String caseId,
    double completionProbability,
    double riskScore,
    String primaryRiskFactor,
    boolean fromOnnxModel,
    Instant predictedAt
) {

    /**
     * Construct with validation.
     *
     * @throws NullPointerException if caseId, primaryRiskFactor, or predictedAt is null
     * @throws IllegalArgumentException if probabilities are outside [0.0, 1.0]
     */
    public CaseOutcomePrediction {
        if (caseId == null) throw new NullPointerException("caseId is required");
        if (primaryRiskFactor == null) throw new NullPointerException("primaryRiskFactor is required");
        if (predictedAt == null) throw new NullPointerException("predictedAt is required");
        if (completionProbability < 0.0 || completionProbability > 1.0) {
            throw new IllegalArgumentException(
                "completionProbability must be in [0.0, 1.0]: " + completionProbability);
        }
        if (riskScore < 0.0 || riskScore > 1.0) {
            throw new IllegalArgumentException(
                "riskScore must be in [0.0, 1.0]: " + riskScore);
        }
    }
}
