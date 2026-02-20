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

package org.yawlfoundation.yawl.integration.autonomous.strategies;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Discovers work items by polling the YAWL engine with optional partition-based
 * filtering for multi-agent load distribution.
 *
 * <p>This implementation fetches all available work items from the engine via
 * Interface B and filters them based on hash-based partitioning. When multiple
 * agents are running, each agent receives only its designated partition of items
 * to prevent duplicate work and improve load distribution.</p>
 *
 * <p>Partition strategy (hash-based):
 * <ol>
 *   <li>Compute hash of work item ID</li>
 *   <li>Map to partition: partition = hash % numAgents</li>
 *   <li>Include item if partition == agentIndex</li>
 * </ol></p>
 *
 * @since YAWL 6.0
 * @see DiscoveryStrategy
 */
public class PollingDiscoveryStrategy extends DiscoveryStrategy {

    private static final long DEFAULT_POLLING_INTERVAL = 5000; // 5 seconds

    /**
     * Discovers work items from the engine, optionally filtered by partition.
     *
     * <p>Queries the engine for all available work items and returns those
     * assigned to this agent's partition. With single agent (agentIndex=0,
     * totalAgents=1), returns all items.</p>
     *
     * @param client the Interface B client for engine communication
     * @param sessionHandle the session handle for authentication
     * @return list of work items assigned to this agent's partition
     * @throws IOException if communication with the engine fails
     * @throws IllegalArgumentException if client or sessionHandle is null/blank
     */
    @Override
    public List<WorkItemRecord> discoverWorkItems(
        InterfaceB_EnvironmentBasedClient client,
        String sessionHandle) throws IOException {

        validateInputs(client, sessionHandle);

        try {
            List<WorkItemRecord> allItems =
                client.getCompleteListOfLiveWorkItems(sessionHandle);

            if (allItems == null) {
                throw new IOException("Engine returned null work items list");
            }

            return allItems;
        } catch (IOException e) {
            throw new IOException("Failed to discover work items: " + e.getMessage(), e);
        }
    }

    /**
     * Filters work items for a specific agent based on hash-based partitioning.
     *
     * <p>Uses consistent hashing to distribute work items across multiple agents.
     * The partition assignment is deterministic: the same work item ID will always
     * map to the same agent, ensuring consistency across polling cycles.</p>
     *
     * @param allItems all work items from engine discovery
     * @param agentIndex this agent's index (0-based)
     * @param totalAgents total number of agents in the partition
     * @return work items assigned to agentIndex
     * @throws IllegalArgumentException if parameters are invalid
     */
    public List<WorkItemRecord> partitionFilter(
        List<WorkItemRecord> allItems,
        int agentIndex,
        int totalAgents) {

        validatePartitionInputs(allItems, agentIndex, totalAgents);

        if (allItems.isEmpty()) {
            return Collections.emptyList();
        }

        // Single agent gets all items
        if (totalAgents == 1) {
            return new java.util.ArrayList<>(allItems);
        }

        // Hash-based partitioning: item goes to agent if hash(id) % totalAgents == agentIndex
        return allItems.stream()
            .filter(item -> {
                String hashSource = buildHashSource(item);
                int hash = Math.abs(hashSource.hashCode());
                return (hash % totalAgents) == agentIndex;
            })
            .collect(Collectors.toList());
    }

    /**
     * Builds the hash source string for a work item.
     *
     * <p>Uses both work item ID and task name to ensure consistent distribution
     * even when item IDs might collide across different cases.</p>
     *
     * @param workItem the work item to hash
     * @return the hash source string
     */
    private String buildHashSource(WorkItemRecord workItem) {
        // Include both ID and task name for better distribution
        String id = workItem.getID() != null ? workItem.getID() : "";
        String taskName = workItem.getTaskName() != null ? workItem.getTaskName() : "";
        return id + "|" + taskName + "|";
    }

    /**
     * Validates client and session handle inputs.
     *
     * @throws IllegalArgumentException if inputs are invalid
     */
    private void validateInputs(InterfaceB_EnvironmentBasedClient client, String sessionHandle) {
        if (client == null) {
            throw new IllegalArgumentException("client cannot be null");
        }
        if (sessionHandle == null || sessionHandle.isBlank()) {
            throw new IllegalArgumentException("sessionHandle cannot be null or blank");
        }
    }

    /**
     * Validates partition filter inputs.
     *
     * @throws IllegalArgumentException if inputs are invalid
     */
    private void validatePartitionInputs(List<WorkItemRecord> allItems, int agentIndex, int totalAgents) {
        if (allItems == null) {
            throw new IllegalArgumentException("allItems cannot be null");
        }
        if (agentIndex < 0) {
            throw new IllegalArgumentException("agentIndex cannot be negative: " + agentIndex);
        }
        if (totalAgents <= 0) {
            throw new IllegalArgumentException("totalAgents must be positive: " + totalAgents);
        }
        if (agentIndex >= totalAgents) {
            throw new IllegalArgumentException(
                "agentIndex (" + agentIndex + ") must be < totalAgents (" + totalAgents + ")");
        }
    }

    @Override
    public long getPollingIntervalMs() {
        return DEFAULT_POLLING_INTERVAL;
    }
}
