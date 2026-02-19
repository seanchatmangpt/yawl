/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous.strategies;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

/**
 * Polling-based discovery strategy for YAWL work items.
 *
 * Queries InterfaceB for all live work items. This is extracted from
 * the original PartyAgent implementation and made configurable.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class PollingDiscoveryStrategy implements DiscoveryStrategy {

    /**
     * Create a polling discovery strategy.
     */
    public PollingDiscoveryStrategy() {
    }

    @Override
    public List<WorkItemRecord> discoverWorkItems(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) throws IOException {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new IllegalArgumentException("sessionHandle is required");
        }

        List<WorkItemRecord> items = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);

        if (items == null) {
            throw new IOException("Failed to retrieve work items from YAWL engine (null response)");
        }

        return items;
    }

    /**
     * Filter work items based on partition strategy.
     *
     * <p>Uses hash-based partitioning to distribute work items across multiple
     * agents using the formula: (hash % totalAgents) == agentIndex. This ensures
     * even distribution and consistent assignment of work items to partitions.</p>
     *
     * @param items list of work items to filter
     * @param agentIndex current agent's partition index (0-based)
     * @param totalAgents total number of agents in the partition
     * @return filtered list containing only work items for this partition
     * @throws IllegalArgumentException if arguments are invalid
     */
    public List<WorkItemRecord> partitionFilter(
            List<WorkItemRecord> items,
            int agentIndex,
            int totalAgents) {

        if (items == null) {
            throw new IllegalArgumentException("items list is required");
        }
        if (agentIndex < 0) {
            throw new IllegalArgumentException("agentIndex must be non-negative");
        }
        if (totalAgents <= 0) {
            throw new IllegalArgumentException("totalAgents must be positive");
        }
        if (agentIndex >= totalAgents) {
            throw new IllegalArgumentException("agentIndex must be less than totalAgents");
        }

        Predicate<WorkItemRecord> partitionPredicate = workItem -> {
            // Generate hash from work item attributes for consistent partitioning
            String hashSource = buildHashSource(workItem);
            int hash = hashSource.hashCode();
            // Use modulo operator to determine partition assignment
            return (hash % totalAgents) == agentIndex;
        };

        // Filter items for this partition
        return items.stream()
                .filter(partitionPredicate)
                .toList();
    }

    /**
     * Build hash source string from work item attributes.
     *
     * <p>Combines multiple work item attributes to create a unique hash source
     * that ensures consistent partitioning across restarts. Uses task name,
     * case ID, and task ID for robust hash generation.</p>
     *
     * @param workItem the work item to build hash from
     * @return string source for hash calculation
     */
    private String buildHashSource(WorkItemRecord workItem) {
        StringBuilder source = new StringBuilder();

        // Task name (primary partition key)
        String taskName = workItem.getTaskName();
        if (taskName == null || taskName.isEmpty()) {
            taskName = workItem.getTaskID();
        }
        source.append(taskName);

        // Case ID (secondary partition key)
        source.append("|").append(workItem.getCaseID());

        // Work item ID (tertiary partition key)
        source.append("|").append(workItem.getID());

        return source.toString();
    }
}
