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

package org.yawlfoundation.yawl.resourcing;

import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round-robin {@link ResourceAllocator} that cycles through the participant pool
 * in order, distributing work items evenly regardless of current load.
 *
 * <p>Thread-safe: the internal counter is an {@link AtomicLong}.
 *
 * @since YAWL 6.0
 */
public final class RoundRobinAllocator implements ResourceAllocator {

    private final AtomicLong counter = new AtomicLong(0);

    @Override
    public Participant allocate(YWorkItem workItem, List<Participant> pool)
            throws AllocationException {
        Objects.requireNonNull(workItem, "workItem must not be null");
        Objects.requireNonNull(pool,     "pool must not be null");
        if (pool.isEmpty()) {
            throw new AllocationException(
                "RoundRobinAllocator: participant pool is empty for task '"
                + workItem.getTaskID() + "'");
        }
        int index = (int) (counter.getAndIncrement() % pool.size());
        Participant chosen = pool.get(index);
        chosen.incrementLoad();
        return chosen;
    }

    @Override
    public String strategyName() { return "round-robin"; }
}
