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

package org.yawlfoundation.yawl.pi.automl;

/**
 * Immutable result of a TPOT2 AutoML training run.
 *
 * <p>Contains the best pipeline's cross-validated score, a human-readable
 * description of the pipeline structure, the serialised ONNX model bytes for
 * registration with {@link org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry},
 * and timing information.
 *
 * @param taskType            the process mining use case that was optimised
 * @param bestScore           cross-validated score of the best pipeline (interpretation
 *                            depends on scoring metric, e.g. ROC-AUC, R²)
 * @param pipelineDescription human-readable description of the best sklearn pipeline
 * @param onnxModelBytes      serialised ONNX model bytes; never null, never empty
 * @param trainingTimeMs      wall-clock time consumed by the subprocess in milliseconds
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record Tpot2Result(
        Tpot2TaskType taskType,
        double bestScore,
        String pipelineDescription,
        byte[] onnxModelBytes,
        long trainingTimeMs
) {

    /**
     * Compact constructor validates all parameters.
     *
     * @throws NullPointerException     if taskType, pipelineDescription, or onnxModelBytes is null
     * @throws IllegalArgumentException if onnxModelBytes is empty or trainingTimeMs is negative
     */
    public Tpot2Result {
        if (taskType == null) {
            throw new NullPointerException("taskType is required");
        }
        if (pipelineDescription == null) {
            throw new NullPointerException("pipelineDescription is required");
        }
        if (onnxModelBytes == null) {
            throw new NullPointerException("onnxModelBytes is required");
        }
        if (onnxModelBytes.length == 0) {
            throw new IllegalArgumentException("onnxModelBytes must not be empty");
        }
        if (trainingTimeMs < 0) {
            throw new IllegalArgumentException(
                "trainingTimeMs must be non-negative, got: " + trainingTimeMs);
        }
    }
}
