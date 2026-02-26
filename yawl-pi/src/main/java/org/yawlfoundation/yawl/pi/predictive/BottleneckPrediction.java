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
 * Immutable prediction of workflow bottleneck (slowest task).
 *
 * <p>Identifies the task with highest expected wait time in a specification,
 * enabling proactive resource allocation and optimization.
 *
 * @param specificationId Workflow specification being analyzed
 * @param bottleneckTaskName Name of the task with highest wait time
 * @param expectedWaitMs Predicted average wait time in milliseconds
 * @param confidence Confidence level (0.0 to 1.0) based on sample size
 * @param predictedAt Timestamp of prediction generation
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record BottleneckPrediction(
    String specificationId,
    String bottleneckTaskName,
    double expectedWaitMs,
    double confidence,
    Instant predictedAt
) {

    /**
     * Construct with validation.
     *
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if expectedWaitMs is negative or confidence outside [0.0, 1.0]
     */
    public BottleneckPrediction {
        if (specificationId == null) throw new NullPointerException("specificationId is required");
        if (bottleneckTaskName == null) throw new NullPointerException("bottleneckTaskName is required");
        if (predictedAt == null) throw new NullPointerException("predictedAt is required");
        if (expectedWaitMs < 0.0) {
            throw new IllegalArgumentException("expectedWaitMs must be non-negative: " + expectedWaitMs);
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0.0, 1.0]: " + confidence);
        }
    }
}
