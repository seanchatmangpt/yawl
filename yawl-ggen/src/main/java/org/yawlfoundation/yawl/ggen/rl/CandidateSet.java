/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import java.util.List;
import java.util.Objects;

/**
 * An evaluated set of K POWL candidate models with their reward scores.
 * Produced by GrpoOptimizer.evaluateCandidates().
 *
 * @param candidates ordered list of K POWL candidate models
 * @param rewards    corresponding reward scores in [0.0, 1.0]
 */
public record CandidateSet(List<PowlModel> candidates, List<Double> rewards) {
    public CandidateSet {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(rewards, "rewards");
        if (candidates.size() != rewards.size()) {
            throw new IllegalArgumentException(
                "candidates.size() != rewards.size(): " + candidates.size() + " vs " + rewards.size());
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("CandidateSet must not be empty");
        }
        candidates = List.copyOf(candidates);
        rewards = List.copyOf(rewards);
    }

    /** Returns the index of the candidate with the highest reward. */
    public int bestIndex() {
        int best = 0;
        for (int i = 1; i < rewards.size(); i++) {
            if (rewards.get(i) > rewards.get(best)) best = i;
        }
        return best;
    }

    /** Returns the best-scoring candidate model. */
    public PowlModel best() {
        return candidates.get(bestIndex());
    }
}
