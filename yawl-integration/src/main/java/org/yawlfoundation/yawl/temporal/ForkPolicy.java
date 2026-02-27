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

import java.util.List;

/**
 * Pluggable policy for enumerating task paths to explore during temporal case forking.
 *
 * <p>A {@code ForkPolicy} determines which enabled tasks should be explored as separate
 * forks when performing case alternative analysis. Implementations decide the enumeration
 * strategy (all enabled tasks, random subset, heuristic-guided selection, etc.) and
 * enforce policy-level fork limits.</p>
 *
 * <p>Policies are stateless and thread-safe. They are consulted once per fork operation
 * by {@link TemporalForkEngine#fork(String, ForkPolicy, java.time.Duration)} to enumerate
 * the set of initial task decisions that will each spawn a virtual thread.</p>
 *
 * @see AllPathsForkPolicy — explores all enabled tasks up to a maximum count
 *
 * @since YAWL 6.0
 */
public interface ForkPolicy {
    /**
     * Enumerates the list of task IDs to explore as separate forks.
     *
     * <p>Called once per fork operation with the list of tasks currently enabled
     * in the case. The policy returns a (possibly filtered) list of task IDs, each
     * of which will become the first decision in a separate fork's execution path.
     * The returned list respects the policy's {@link #maxForks()} limit.</p>
     *
     * @param enabledTaskIds list of task IDs currently enabled in the case
     * @return list of task IDs to fork on, never null, size <= maxForks()
     */
    List<String> enumeratePaths(List<String> enabledTaskIds);

    /**
     * Returns the maximum number of forks this policy allows.
     *
     * <p>The policy enforces this limit when enumerating paths. The
     * {@link TemporalForkEngine} respects this value and will not spawn
     * more than {@code maxForks()} virtual threads.</p>
     *
     * @return maximum fork count for this policy (>= 1)
     */
    int maxForks();
}
