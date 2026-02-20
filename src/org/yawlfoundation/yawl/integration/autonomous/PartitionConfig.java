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
 * Configuration for partitioning work items among multiple agents.
 *
 * <p>Enables distributed processing of work items using consistent
 * hashing to ensure deterministic assignment without coordination.</p>
 *
 * @since YAWL 6.0
 */
public class PartitionConfig {

    private final int agentIndex;
    private final int totalAgents;

    /**
     * Creates a new partition configuration.
     *
     * @param agentIndex the index of this agent (0-based)
     * @param totalAgents the total number of agents in the partition
     */
    public PartitionConfig(int agentIndex, int totalAgents) {
        if (agentIndex < 0 || agentIndex >= totalAgents) {
            throw new IllegalArgumentException("agentIndex must be between 0 and totalAgents-1");
        }
        if (totalAgents <= 0) {
            throw new IllegalArgumentException("totalAgents must be positive");
        }
        this.agentIndex = agentIndex;
        this.totalAgents = totalAgents;
    }

    /**
     * Gets the index of this agent.
     *
     * @return the agent index (0-based)
     */
    public int getAgentIndex() {
        return agentIndex;
    }

    /**
     * Gets the total number of agents in the partition.
     *
     * @return the total number of agents
     */
    public int getTotalAgents() {
        return totalAgents;
    }

    /**
     * Checks if this agent should process the given work item based on partitioning.
     *
     * @param workItemId the work item ID to check
     * @return true if this agent should process the work item
     */
    public boolean shouldProcess(String workItemId) {
        int hash = Math.abs(workItemId.hashCode());
        return (hash % totalAgents) == agentIndex;
    }
}