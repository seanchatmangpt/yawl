/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.scoring;

import org.yawlfoundation.yawl.ggen.powl.PowlModel;

import java.util.Objects;

/**
 * A weighted composite reward function that combines two scoring strategies.
 * Useful for multi-stage reinforcement learning where different reward signals
 * are used in different phases.
 *
 * <p>Typical usage:
 * <ul>
 *   <li>Stage A: Use universal function (e.g., footprint-based)</li>
 *   <li>Stage B: Use verifiable function (e.g., LLM judge)</li>
 * </ul>
 *
 * @param universal       the "universal" reward function (e.g., always applicable)
 * @param verifiable      the "verifiable" reward function (e.g., requires external validation)
 * @param universalWeight the weight for the universal function (must be non-negative)
 * @param verifiableWeight the weight for the verifiable function (must be non-negative)
 */
public record CompositeRewardFunction(
        RewardFunction universal,
        RewardFunction verifiable,
        double universalWeight,
        double verifiableWeight
) implements RewardFunction {

    /**
     * Compact constructor enforcing non-null functions and non-negative weights.
     */
    public CompositeRewardFunction {
        Objects.requireNonNull(universal, "universal must not be null");
        Objects.requireNonNull(verifiable, "verifiable must not be null");
        if (universalWeight < 0) {
            throw new IllegalArgumentException(
                "universalWeight must be non-negative, got: " + universalWeight
            );
        }
        if (verifiableWeight < 0) {
            throw new IllegalArgumentException(
                "verifiableWeight must be non-negative, got: " + verifiableWeight
            );
        }
    }

    /**
     * Scores a candidate by computing a weighted average of the two component functions.
     * Weights are normalized to sum to 1.0 before averaging.
     *
     * @param candidate           the POWL model to score
     * @param processDescription  the process description
     * @return a weighted-average score in [0.0, 1.0]
     * @throws IllegalStateException if both weights are zero
     */
    @Override
    public double score(PowlModel candidate, String processDescription) {
        double u = universal.score(candidate, processDescription);
        double v = verifiable.score(candidate, processDescription);
        double total = universalWeight + verifiableWeight;

        if (total == 0.0) {
            throw new IllegalStateException(
                "Cannot compute composite score with both weights equal to zero"
            );
        }

        return (universalWeight * u + verifiableWeight * v) / total;
    }

    /**
     * Factory method for Stage A scoring (using only the universal function).
     *
     * @param universal the universal reward function to use
     * @return a CompositeRewardFunction with full weight on the universal function
     */
    public static CompositeRewardFunction stageA(RewardFunction universal) {
        return new CompositeRewardFunction(universal, (c, d) -> 0.0, 1.0, 0.0);
    }

    /**
     * Factory method for Stage B scoring (using only the verifiable function).
     *
     * @param verifiable the verifiable reward function to use
     * @return a CompositeRewardFunction with full weight on the verifiable function
     */
    public static CompositeRewardFunction stageB(RewardFunction verifiable) {
        return new CompositeRewardFunction((c, d) -> 0.0, verifiable, 0.0, 1.0);
    }
}
