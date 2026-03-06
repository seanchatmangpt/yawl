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

/**
 * Strategy interface for allocating a human {@link Participant} to a YAWL work item.
 *
 * <p>Implementations define concrete allocation policies (round-robin, least-loaded,
 * role-based, etc.). They must be thread-safe and must never return {@code null}
 * or silently swallow failures; if no suitable participant can be found, they must
 * throw {@link AllocationException}.
 *
 * @since YAWL 6.0
 */
public interface ResourceAllocator {

    /**
     * Allocates one participant from {@code pool} to the given work item.
     *
     * @param workItem the enabled YAWL work item requiring allocation; must not be null
     * @param pool     the available participants; must not be null, may be empty
     * @return the chosen participant; never null
     * @throws AllocationException      if no suitable participant can be found
     * @throws IllegalArgumentException if workItem or pool is null
     */
    Participant allocate(YWorkItem workItem, List<Participant> pool) throws AllocationException;

    /**
     * Returns a short human-readable name for this allocation strategy.
     *
     * @return non-null, non-blank strategy name
     */
    String strategyName();
}
