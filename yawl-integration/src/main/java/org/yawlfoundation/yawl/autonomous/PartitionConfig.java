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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous;

/**
 * Configuration for partitioning work items among multiple agents — Java 25 record edition.
 *
 * <p>Enables distributed processing of work items using consistent hashing to ensure
 * deterministic assignment without coordination between agents.</p>
 *
 * <p>Converted from a plain class to a Java 25 record:
 * <ul>
 *   <li>Immutable by construction</li>
 *   <li>Auto-generated equals, hashCode, and toString</li>
 *   <li>Canonical constructor validates the constraint {@code 0 <= agentIndex < totalAgents}</li>
 * </ul>
 *
 * @param agentIndex   the zero-based index of this agent within the partition
 * @param totalAgents  the total number of agents sharing the work
 *
 * @since YAWL 6.0
 */
public record PartitionConfig(int agentIndex, int totalAgents) {

    /**
     * Canonical constructor with validation.
     */
    public PartitionConfig {
        if (totalAgents <= 0) {
            throw new IllegalArgumentException(
                "totalAgents must be positive, got: " + totalAgents);
        }
        if (agentIndex < 0 || agentIndex >= totalAgents) {
            throw new IllegalArgumentException(
                "agentIndex must be in [0, totalAgents): got agentIndex="
                + agentIndex + ", totalAgents=" + totalAgents);
        }
    }

    /**
     * Convenience factory for a single-agent (no partitioning) configuration.
     *
     * @return partition config with agentIndex=0, totalAgents=1
     */
    public static PartitionConfig single() {
        return new PartitionConfig(0, 1);
    }

    /**
     * Determine whether this agent should process the given work item.
     *
     * <p>Uses the absolute hash of the work item ID modulo {@link #totalAgents}
     * for deterministic, coordinator-free assignment.
     *
     * @param workItemId the work item identifier
     * @return true if this agent owns the work item
     */
    public boolean shouldProcess(String workItemId) {
        int hash = Math.abs(workItemId.hashCode());
        return (hash % totalAgents) == agentIndex;
    }
}
