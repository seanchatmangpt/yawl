/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml.tpot2;

import java.util.Map;

/**
 * Result of TPOT2 optimization.
 *
 * @param bestPipeline string representation of best pipeline
 * @param fitnessScore fitness score (accuracy, f1, etc.)
 * @param generations number of generations completed
 * @param metadata additional metadata from optimization
 */
public record OptimizationResult(
    String bestPipeline,
    double fitnessScore,
    int generations,
    Map<String, Object> metadata
) {
    /**
     * Check if optimization succeeded.
     */
    public boolean isSuccess() {
        return fitnessScore > 0;
    }

    /**
     * Get formatted summary.
     */
    public String summary() {
        return String.format(
            "OptimizationResult[pipeline=%s, fitness=%.4f, generations=%d]",
            bestPipeline, fitnessScore, generations
        );
    }
}
