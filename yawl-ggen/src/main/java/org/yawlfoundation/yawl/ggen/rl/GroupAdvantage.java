/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Group Relative Policy Optimization (GRPO) advantage estimates.
 *
 * <p>For each candidate in the group: advantage_i = (reward_i - mean) / (std + ε).
 * Advantages are used to rank candidates; positive advantage = above group mean.
 *
 * @param advantages normalized advantage per candidate
 * @param mean       group mean reward
 * @param std        group reward standard deviation
 */
public record GroupAdvantage(List<Double> advantages, double mean, double std) {

    private static final double EPSILON = 1e-8;

    public GroupAdvantage {
        Objects.requireNonNull(advantages, "advantages");
        if (advantages.isEmpty()) throw new IllegalArgumentException("advantages must not be empty");
        advantages = List.copyOf(advantages);
    }

    /**
     * Computes GRPO advantage for a group of reward scores.
     *
     * @param rewards list of reward values (must not be empty)
     * @return GroupAdvantage with per-candidate advantages, group mean, and std
     * @throws IllegalArgumentException if rewards is null or empty
     */
    public static GroupAdvantage compute(List<Double> rewards) {
        Objects.requireNonNull(rewards, "rewards");
        if (rewards.isEmpty()) throw new IllegalArgumentException("rewards must not be empty");

        // Compute mean
        double mean = rewards.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

        // Compute std
        double variance = rewards.stream()
            .mapToDouble(r -> (r - mean) * (r - mean))
            .average()
            .orElseThrow();
        double std = Math.sqrt(variance);

        // Compute advantages
        List<Double> advantages = new ArrayList<>(rewards.size());
        for (double reward : rewards) {
            advantages.add((reward - mean) / (std + EPSILON));
        }

        return new GroupAdvantage(advantages, mean, std);
    }

    /**
     * Returns the index of the candidate with the highest advantage.
     * Ties broken by first occurrence.
     */
    public int bestIndex() {
        int best = 0;
        for (int i = 1; i < advantages.size(); i++) {
            if (advantages.get(i) > advantages.get(best)) best = i;
        }
        return best;
    }
}
