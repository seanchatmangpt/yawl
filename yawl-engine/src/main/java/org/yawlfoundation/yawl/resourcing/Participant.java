/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

/**
 * Minimal Participant interface for yawl-engine.
 *
 * This is a minimal stub to break the circular dependency between yawl-engine
 * and yawl-resourcing modules. The full implementation is in the yawl-resourcing module.
 *
 * Usage in yawl-engine:
 * - YWorkItem stores Participant reference for resource allocation
 * - Engine tracks which participant is assigned to each work item
 *
 * Full implementation: yawl-resourcing/src/main/java/org/yawlfoundation/yawl/resourcing/Participant.java
 *
 * @since 6.0.0 (Java 25 modernization)
 */
public class Participant {

    private final String id;
    private final String fullName;
    private final String email;

    /**
     * Creates a minimal Participant instance.
     *
     * @param id unique participant identifier
     * @param fullName participant's full name
     * @param email participant's email address
     */
    public Participant(String id, String fullName, String email) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
    }

    /**
     * Gets the unique identifier for this participant.
     *
     * @return participant ID
     */
    public String getID() {
        return id;
    }

    /**
     * Gets the full name of this participant.
     *
     * @return participant's full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Gets the email address of this participant.
     *
     * @return participant's email
     */
    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        return "Participant{" +
                "id='" + id + '\'' +
                ", fullName='" + fullName + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Participant that = (Participant) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
