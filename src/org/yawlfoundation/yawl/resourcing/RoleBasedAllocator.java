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

/**
 * Role-filtering {@link ResourceAllocator} that narrows the pool to participants
 * whose role matches the required role, then delegates to a configurable
 * inner allocator for the final selection.
 *
 * <p>The required role is derived from the work item's task ID by convention:
 * the task ID is treated as the role name. If no participant in the pool holds
 * the required role, an {@link AllocationException} is thrown — there is no
 * silent fallback to the full pool.
 *
 * <p>Example usage:
 * <pre>{@code
 * ResourceAllocator allocator = new RoleBasedAllocator(
 *     new LeastLoadedAllocator(),
 *     "approver"          // override role; pass null to derive from task ID
 * );
 * Participant p = allocator.allocate(workItem, allParticipants);
 * }</pre>
 *
 * @since YAWL 6.0
 */
public final class RoleBasedAllocator implements ResourceAllocator {

    private final ResourceAllocator delegate;
    private final String requiredRole;   // null → derive from task ID

    /**
     * Creates a role-based allocator that derives the required role from each
     * work item's task ID and delegates final selection to {@code delegate}.
     *
     * @param delegate the inner allocator used after role filtering; must not be null
     */
    public RoleBasedAllocator(ResourceAllocator delegate) {
        this(delegate, null);
    }

    /**
     * Creates a role-based allocator with an explicit required role.
     *
     * @param delegate     the inner allocator used after role filtering; must not be null
     * @param requiredRole the role that participants must hold; if null, the task ID is used
     */
    public RoleBasedAllocator(ResourceAllocator delegate, String requiredRole) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        this.delegate     = delegate;
        this.requiredRole = requiredRole;
    }

    @Override
    public Participant allocate(YWorkItem workItem, List<Participant> pool)
            throws AllocationException {
        Objects.requireNonNull(workItem, "workItem must not be null");
        Objects.requireNonNull(pool,     "pool must not be null");

        String role = (requiredRole != null) ? requiredRole : workItem.getTaskID();

        List<Participant> eligible = pool.stream()
            .filter(p -> role.equalsIgnoreCase(p.getRole()))
            .toList();

        if (eligible.isEmpty()) {
            throw new AllocationException(
                "RoleBasedAllocator: no participant with role '" + role
                + "' found in pool of " + pool.size() + " for task '"
                + workItem.getTaskID() + "'");
        }

        return delegate.allocate(workItem, eligible);
    }

    @Override
    public String strategyName() {
        return "role-based(" + delegate.strategyName() + ")";
    }
}
