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

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A human participant that can be allocated to YAWL work items.
 *
 * <p>Each participant has a unique ID, a display name, a primary role,
 * a set of capability tags, and a current load counter used by
 * load-aware {@link ResourceAllocator} implementations.
 *
 * <p>The {@code currentLoad} counter is thread-safe and represents the
 * number of active (allocated but not yet completed) work items.
 *
 * @since YAWL 6.0
 */
public final class Participant {

    private final String id;
    private final String name;
    private final String role;
    private final Set<String> capabilities;
    private final AtomicInteger currentLoad;

    /**
     * Creates a participant with a generated ID and zero initial load.
     *
     * @param name         display name; must not be null or blank
     * @param role         primary role identifier (e.g. "manager", "analyst"); must not be null
     * @param capabilities set of capability tags; must not be null (may be empty)
     * @throws IllegalArgumentException if name is null/blank or role/capabilities is null
     */
    public Participant(String name, String role, Set<String> capabilities) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Participant name must not be null or blank");
        }
        Objects.requireNonNull(role,         "role must not be null");
        Objects.requireNonNull(capabilities, "capabilities must not be null");
        this.id           = UUID.randomUUID().toString();
        this.name         = name;
        this.role         = role;
        this.capabilities = Set.copyOf(capabilities);
        this.currentLoad  = new AtomicInteger(0);
    }

    /** Returns the unique participant ID. */
    public String getId() { return id; }

    /** Returns the participant's display name. */
    public String getName() { return name; }

    /** Returns the participant's primary role. */
    public String getRole() { return role; }

    /** Returns an unmodifiable view of the participant's capability tags. */
    public Set<String> getCapabilities() { return capabilities; }

    /** Returns the current number of active work items allocated to this participant. */
    public int getCurrentLoad() { return currentLoad.get(); }

    /** Increments the load counter by one (thread-safe). */
    public void incrementLoad() { currentLoad.incrementAndGet(); }

    /** Decrements the load counter by one, floor zero (thread-safe). */
    public void decrementLoad() { currentLoad.updateAndGet(v -> Math.max(0, v - 1)); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Participant p)) return false;
        return id.equals(p.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "Participant{id=" + id + ", name=" + name
            + ", role=" + role + ", load=" + currentLoad.get() + "}";
    }
}
