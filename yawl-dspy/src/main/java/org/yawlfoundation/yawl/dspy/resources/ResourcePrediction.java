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

package org.yawlfoundation.yawl.dspy.resources;

import java.util.Objects;

/**
 * Immutable output of DSPy resource allocation prediction.
 *
 * <p>This record represents the DSPy module's prediction for the best agent to handle
 * a given task, along with a confidence score and reasoning chain. The {@link PredictiveResourceRouter}
 * uses the confidence score to decide whether to trust the prediction (skip expensive marketplace
 * query) or fallthrough to the standard {@link org.yawlfoundation.yawl.resourcing.CapabilityMatcher}.</p>
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li><strong>predictedAgentId</strong>: The ID of the agent predicted by DSPy's Chain-of-Thought
 *       module. Must reference an actual agent in the marketplace.</li>
 *   <li><strong>confidence</strong>: A score from 0.0 to 1.0 indicating how confident the DSPy
 *       module is in this prediction. Scores > 0.85 (by default) trigger direct allocation without
 *       marketplace query; scores <= 0.85 fallthrough to the standard matcher.</li>
 *   <li><strong>reasoning</strong>: The Chain-of-Thought reasoning string produced by DSPy,
 *       explaining why this agent was predicted. Used for audit trails and debugging.</li>
 * </ul>
 *
 * <h2>Immutability Contract</h2>
 * <p>This is a Java 21+ record with no mutable fields.</p>
 *
 * @param predictedAgentId the ID of the predicted agent
 * @param confidence       the confidence score (0.0 to 1.0)
 * @param reasoning        the reasoning chain from DSPy
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record ResourcePrediction(
        String predictedAgentId,
        double confidence,
        String reasoning
) {

    /**
     * Compact constructor that validates input parameters.
     *
     * @throws NullPointerException     if predictedAgentId or reasoning is null
     * @throws IllegalArgumentException if predictedAgentId is blank,
     *                                  confidence is outside [0.0, 1.0],
     *                                  or reasoning is blank
     */
    public ResourcePrediction {
        Objects.requireNonNull(predictedAgentId, "predictedAgentId must not be null");
        if (predictedAgentId.isBlank()) {
            throw new IllegalArgumentException("predictedAgentId must not be blank");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be between 0.0 and 1.0, got " + confidence);
        }
        Objects.requireNonNull(reasoning, "reasoning must not be null");
        if (reasoning.isBlank()) {
            throw new IllegalArgumentException("reasoning must not be blank");
        }
    }
}
