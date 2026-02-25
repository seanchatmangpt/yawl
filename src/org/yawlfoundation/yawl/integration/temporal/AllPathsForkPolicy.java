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
 * Fork policy that explores all enabled task paths up to a configurable maximum.
 *
 * <p>{@code AllPathsForkPolicy} enumerates all enabled tasks as separate forks,
 * respecting a maximum fork count. If more tasks are enabled than the maximum,
 * the policy returns the first N tasks. This is the simplest and most common
 * fork strategy for exhaustive case alternative analysis.</p>
 *
 * <p>Instances are immutable and thread-safe. The default maximum is 10 forks.</p>
 *
 * @since YAWL 6.0
 */
public final class AllPathsForkPolicy implements ForkPolicy {
    private final int _maxForks;

    /**
     * Creates a policy with a specified maximum fork count.
     *
     * @param maxForks the maximum number of forks to enumerate (>= 1)
     * @throws IllegalArgumentException if maxForks < 1
     */
    public AllPathsForkPolicy(int maxForks) {
        if (maxForks < 1) {
            throw new IllegalArgumentException("maxForks must be >= 1, got: " + maxForks);
        }
        _maxForks = maxForks;
    }

    /**
     * Creates a policy with the default maximum of 10 forks.
     */
    public AllPathsForkPolicy() {
        this(10);
    }

    /**
     * Returns up to maxForks task IDs from the enabled set.
     *
     * <p>If enabledTaskIds contains fewer tasks than maxForks, all tasks are returned.
     * Otherwise, the first maxForks tasks are returned in their original order.</p>
     *
     * @param enabledTaskIds list of currently enabled task IDs
     * @return unmodifiable list of task IDs to fork, size <= maxForks()
     */
    @Override
    public List<String> enumeratePaths(List<String> enabledTaskIds) {
        if (enabledTaskIds == null || enabledTaskIds.isEmpty()) {
            return List.of();
        }
        return enabledTaskIds.stream()
            .limit(_maxForks)
            .toList();
    }

    /**
     * Returns the maximum number of forks this policy allows.
     *
     * @return the configured maximum fork count
     */
    @Override
    public int maxForks() {
        return _maxForks;
    }
}
