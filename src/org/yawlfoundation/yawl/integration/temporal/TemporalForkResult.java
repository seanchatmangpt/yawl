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

package org.yawlfoundation.yawl.integration.temporal;

import java.time.Duration;
import java.util.List;

/**
 * Aggregated result of temporal case exploration across multiple forks.
 *
 * <p>A {@code TemporalForkResult} contains the complete set of execution paths
 * explored, execution statistics, and convenience methods for outcome analysis.
 * It identifies the dominant (most common) outcome when multiple forks reach
 * the same state, enabling comparison of workflow alternatives.</p>
 *
 * <p>Produced by {@link TemporalForkEngine#fork(String, ForkPolicy, Duration)}
 * after all virtual threads complete. Immutable and safe for concurrent access.</p>
 *
 * @param forks list of all completed forks (outcomes)
 * @param dominantOutcomeIndex index into {@code forks} list of the most common outcome,
 *                             or -1 if all forks produced unique outcomes
 * @param wallTime total wall-clock duration for entire fork exploration
 * @param requestedForks number of forks requested by policy
 * @param completedForks number of forks that actually completed
 *
 * @since YAWL 6.0
 */
public record TemporalForkResult(
    List<CaseFork> forks,
    int dominantOutcomeIndex,
    Duration wallTime,
    int requestedForks,
    int completedForks
) {
    /**
     * Validates that all requested forks completed successfully.
     *
     * @return true if {@code completedForks == requestedForks}, false otherwise
     */
    public boolean allForksCompleted() {
        return completedForks == requestedForks;
    }

    /**
     * Returns the fork with the dominant (most common) outcome.
     *
     * <p>If all forks produced unique outcomes (dominantOutcomeIndex == -1),
     * returns the first fork (index 0). Never null if forks list is non-empty.</p>
     *
     * @return the CaseFork at {@code dominantOutcomeIndex}, or first fork if index == -1
     * @throws IndexOutOfBoundsException if forks list is empty
     */
    public CaseFork getDominantFork() {
        if (dominantOutcomeIndex >= 0) {
            return forks.get(dominantOutcomeIndex);
        }
        return forks.get(0);
    }
}
