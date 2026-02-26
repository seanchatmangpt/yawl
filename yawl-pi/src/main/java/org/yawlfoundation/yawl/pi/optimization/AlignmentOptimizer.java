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

package org.yawlfoundation.yawl.pi.optimization;

import java.util.List;

/**
 * Computes optimal alignment between observed and reference activity sequences.
 *
 * <p>Uses Levenshtein distance (edit distance) to find the minimum cost alignment.
 * Each move (insertion, deletion, substitution) has unit cost. Synchronous moves
 * (matches) have zero cost.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class AlignmentOptimizer {

    /**
     * Create a new alignment optimizer.
     */
    public AlignmentOptimizer() {
    }

    /**
     * Compute optimal alignment between observed and reference sequences.
     *
     * @param observedActivities activities from event log
     * @param referenceActivities activities from process model
     * @return alignment result with cost metrics
     */
    public AlignmentResult align(List<String> observedActivities,
                                  List<String> referenceActivities) {
        if (observedActivities == null || observedActivities.isEmpty()) {
            return new AlignmentResult(
                observedActivities != null ? observedActivities : List.of(),
                referenceActivities != null ? referenceActivities : List.of(),
                0, 0, referenceActivities != null ? referenceActivities.size() : 0,
                referenceActivities != null ? referenceActivities.size() : 0,
                0.0
            );
        }

        if (referenceActivities == null || referenceActivities.isEmpty()) {
            return new AlignmentResult(
                observedActivities,
                referenceActivities != null ? referenceActivities : List.of(),
                0, observedActivities.size(), 0,
                observedActivities.size(),
                0.0
            );
        }

        int obsLen = observedActivities.size();
        int refLen = referenceActivities.size();

        // Compute Levenshtein distance using dynamic programming
        int[][] dp = new int[obsLen + 1][refLen + 1];

        // Initialize base cases
        for (int i = 0; i <= obsLen; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= refLen; j++) {
            dp[0][j] = j;
        }

        // Fill DP table
        for (int i = 1; i <= obsLen; i++) {
            for (int j = 1; j <= refLen; j++) {
                String obs = observedActivities.get(i - 1);
                String ref = referenceActivities.get(j - 1);

                if (obs.equals(ref)) {
                    dp[i][j] = dp[i - 1][j - 1];  // Match: zero cost
                } else {
                    dp[i][j] = 1 + Math.min(
                        Math.min(dp[i - 1][j], dp[i][j - 1]),  // insertion or deletion
                        dp[i - 1][j - 1]  // substitution
                    );
                }
            }
        }

        // Backtrack to count moves
        int synchronous = 0;
        int moveOnLog = 0;
        int moveOnModel = 0;

        int i = obsLen, j = refLen;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                String obs = observedActivities.get(i - 1);
                String ref = referenceActivities.get(j - 1);

                if (obs.equals(ref)) {
                    synchronous++;
                    i--;
                    j--;
                } else if (dp[i][j] == dp[i - 1][j - 1] + 1) {
                    i--;
                    j--;
                } else if (dp[i][j] == dp[i - 1][j] + 1) {
                    moveOnLog++;
                    i--;
                } else {
                    moveOnModel++;
                    j--;
                }
            } else if (i > 0) {
                moveOnLog++;
                i--;
            } else {
                moveOnModel++;
                j--;
            }
        }

        double alignmentCost = moveOnLog + moveOnModel;
        double fitnessDelta = synchronous / (double) Math.max(obsLen, refLen);

        return new AlignmentResult(
            observedActivities,
            referenceActivities,
            synchronous,
            moveOnLog,
            moveOnModel,
            alignmentCost,
            fitnessDelta
        );
    }
}
