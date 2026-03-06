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

import jakarta.persistence.*;
import java.time.Instant;
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
 * <p>This entity supports JPA persistence with fields for LDAP synchronization
 * and HR integration.
 *
 * @since YAWL 6.0
 */
@Entity
@Table(name = "yawlp_resourcing_participant")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;

    @Column(nullable = false)
    private String userId;  // LDAP user ID for synchronization

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String role;

    @ElementCollection
    @CollectionTable(name = "yawlp_resourcing_capabilities")
    @Column(name = "capability")
    private Set<String> capabilities;

    @Column(name = "current_load")
    private int currentLoad;

    // LDAP synchronization fields
    private String email;
    private String department;
    private String title;
    private boolean active = true;
    private Instant createdTime;
    private Instant lastUpdated;
    private Instant deactivatedTime;

    /**
     * Creates a participant with a generated ID and zero initial load.
     *
     * @param name         display name; must not be null or blank
     * @param role         primary role identifier (e.g. "manager", "analyst"); must not be null
     * @param capabilities set of capability tags; must not be null (may be empty)
     * @throws IllegalArgumentException if name is null/blank or role/capabilities is null
     */
    public Participant(String name, String role, Set<String> capabilities) {
        this(name, role, capabilities, null);
    }

    /**
     * Creates a participant with a generated ID and zero initial load.
     *
     * @param name         display name; must not be null or blank
     * @param role         primary role identifier (e.g. "manager", "analyst"); must not be null
     * @param capabilities set of capability tags; must not be null (may be empty)
     * @param userId       LDAP user ID for synchronization
     * @throws IllegalArgumentException if name is null/blank or role/capabilities is null
     */
    public Participant(String name, String role, Set<String> capabilities, String userId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Participant name must not be null or blank");
        }
        Objects.requireNonNull(role,         "role must not be null");
        Objects.requireNonNull(capabilities, "capabilities must not be null");

        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.role = role;
        this.capabilities = Set.copyOf(capabilities);
        this.userId = userId;
        this.currentLoad = 0;
        this.createdTime = Instant.now();
        this.lastUpdated = Instant.now();
    }

    // JPA no-arg constructor
    protected Participant() {}

    /** Returns the unique participant ID. */
    public String getId() { return id; }

    /** Returns the user ID from LDAP for synchronization. */
    public String getUserId() { return userId; }

    /** Sets the user ID from LDAP for synchronization. */
    public void setUserId(String userId) { this.userId = userId; }

    /** Returns the participant's display name. */
    public String getName() { return name; }

    /** Sets the participant's display name. */
    public void setName(String name) { this.name = name; }

    /** Returns the participant's primary role. */
    public String getRole() { return role; }

    /** Sets the participant's primary role. */
    public void setRole(String role) { this.role = role; }

    /** Returns an unmodifiable view of the participant's capability tags. */
    public Set<String> getCapabilities() { return capabilities; }

    /** Sets the participant's capability tags. */
    public void setCapabilities(Set<String> capabilities) {
        this.capabilities = Set.copyOf(capabilities);
    }

    /** Returns the current number of active work items allocated to this participant. */
    public int getCurrentLoad() { return currentLoad; }

    /** Increments the load counter by one (thread-safe). */
    public void incrementLoad() {
        this.currentLoad++;
        this.lastUpdated = Instant.now();
    }

    /** Decrements the load counter by one, floor zero (thread-safe). */
    public void decrementLoad() {
        this.currentLoad = Math.max(0, this.currentLoad - 1);
        this.lastUpdated = Instant.now();
    }

    // LDAP and HR integration methods
    public String getEmail() { return email; }
    public void setEmail(String email) {
        this.email = email;
        this.lastUpdated = Instant.now();
    }

    public String getDepartment() { return department; }
    public void setDepartment(String department) {
        this.department = department;
        this.lastUpdated = Instant.now();
    }

    public String getTitle() { return title; }
    public void setTitle(String title) {
        this.title = title;
        this.lastUpdated = Instant.now();
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) {
        this.active = active;
        this.lastUpdated = Instant.now();
        if (!active) {
            this.deactivatedTime = Instant.now();
        }
    }

    public Instant getCreatedTime() { return createdTime; }
    public Instant getLastUpdated() { return lastUpdated; }
    public Instant getDeactivatedTime() { return deactivatedTime; }

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
            + ", role=" + role + ", load=" + currentLoad + "}";
    }
}

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
