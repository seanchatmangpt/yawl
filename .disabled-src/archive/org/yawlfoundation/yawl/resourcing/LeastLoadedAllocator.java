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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Load-aware {@link ResourceAllocator} that always picks the participant with
 * the lowest current load, breaking ties by natural list order.
 *
 * <p>Thread-safe: each call reads a snapshot of {@link Participant#getCurrentLoad()}
 * via the atomic counter and increments the winner atomically.
 *
 * @since YAWL 6.0
 */
public final class LeastLoadedAllocator implements ResourceAllocator {

    @Override
    public Participant allocate(YWorkItem workItem, List<Participant> pool)
            throws AllocationException {
        Objects.requireNonNull(workItem, "workItem must not be null");
        Objects.requireNonNull(pool,     "pool must not be null");
        if (pool.isEmpty()) {
            throw new AllocationException(
                "LeastLoadedAllocator: participant pool is empty for task '"
                + workItem.getTaskID() + "'");
        }
        Participant chosen = pool.stream()
            .min(Comparator.comparingInt(Participant::getCurrentLoad))
            .orElseThrow(() -> new AllocationException(
                "LeastLoadedAllocator: could not select participant for task '"
                + workItem.getTaskID() + "'"));
        chosen.incrementLoad();
        return chosen;
    }

    @Override
    public String strategyName() { return "least-loaded"; }
}
