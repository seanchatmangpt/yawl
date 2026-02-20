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

package org.yawlfoundation.yawl.integration.autonomous.strategies;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Discovery strategy that polls the YAWL engine for live work items.
 *
 * <p>Queries the engine via InterfaceB to retrieve all currently enabled work items,
 * with support for hash-based partitioning to distribute work items across multiple
 * agent instances (ADR-025).</p>
 *
 * <p>Partition strategy: {@code Math.abs(hashSource.hashCode()) % totalAgents == agentIndex},
 * where hashSource is built from the work item's ID and task name.</p>
 *
 * @since YAWL 6.0
 */
public class PollingDiscoveryStrategy implements DiscoveryStrategy {

    /**
     * Discovers all live work items by polling the YAWL engine.
     *
     * @param client the InterfaceB client for engine communication
     * @param sessionHandle the session handle for authentication
     * @return list of live work items from the engine
     * @throws IOException if communication with engine fails or response is null
     * @throws IllegalArgumentException if client or sessionHandle is null/empty
     */
    @Override
    public List<WorkItemRecord> discoverWorkItems(InterfaceB_EnvironmentBasedClient client,
                                                  String sessionHandle) throws IOException {
        if (client == null) {
            throw new IllegalArgumentException("client is required");
        }
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new IllegalArgumentException("sessionHandle is required");
        }

        List<WorkItemRecord> items = client.getCompleteListOfLiveWorkItems(sessionHandle);
        if (items == null) {
            throw new IOException("Engine returned null response for live work items");
        }
        return items;
    }

    /**
     * Filters work items for a specific agent partition using consistent hashing.
     *
     * <p>Each work item is assigned to exactly one agent based on:
     * {@code Math.abs(buildHashSource(item).hashCode()) % totalAgents == agentIndex}</p>
     *
     * @param items the full list of work items to partition
     * @param agentIndex the index of this agent (0-based)
     * @param totalAgents the total number of agents in the partition
     * @return the work items assigned to this agent
     * @throws IllegalArgumentException if items is null, agentIndex is negative,
     *         totalAgents is zero/negative, or agentIndex >= totalAgents
     */
    public List<WorkItemRecord> partitionFilter(List<WorkItemRecord> items,
                                                int agentIndex, int totalAgents) {
        if (items == null) {
            throw new IllegalArgumentException("items list is required");
        }
        if (totalAgents <= 0) {
            throw new IllegalArgumentException("totalAgents must be positive");
        }
        if (agentIndex < 0 || agentIndex >= totalAgents) {
            throw new IllegalArgumentException(
                    "agentIndex must be between 0 and totalAgents-1, got " + agentIndex);
        }

        if (totalAgents == 1) {
            return List.copyOf(items);
        }

        return items.stream()
                .filter(item -> {
                    String hashSource = buildHashSource(item);
                    int hash = Math.abs(hashSource.hashCode());
                    return (hash % totalAgents) == agentIndex;
                })
                .collect(Collectors.toList());
    }

    /**
     * Builds the hash source string for consistent partition assignment.
     *
     * <p>Combines the work item ID and task name to produce a deterministic
     * hash source that distributes evenly across partitions.</p>
     *
     * @param workItem the work item to hash
     * @return the hash source string
     */
    private String buildHashSource(WorkItemRecord workItem) {
        String id = workItem.getID();
        String taskName = workItem.getTaskName();
        return id + "|" + (taskName != null ? taskName : "") + "|";
    }
}
