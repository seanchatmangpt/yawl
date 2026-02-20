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

package org.yawlfoundation.yawl.integration.autonomous.conflict;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.AgentInfoStore;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Resolves conflicts when multiple agents compete for the same work item.
 *
 * <p>Implements priority-based conflict resolution using agent capability match
 * scores and an optional load-balancing strategy. Tracks conflicting work items
 * in a concurrent map for auditability and deadlock detection.</p>
 *
 * @since YAWL 6.0
 */
public class ConflictResolver {

    private final AgentInfoStore agentStore;
    private final Map<String, List<String>> conflictRegistry = new ConcurrentHashMap<>();

    /**
     * Creates a new conflict resolver with an agent information store.
     *
     * @param agentStore the store for agent information and capability lookup
     */
    public ConflictResolver(AgentInfoStore agentStore) {
        this.agentStore = agentStore;
    }

    /**
     * Resolves conflicts for a work item among competing agents.
     *
     * <p>Resolution strategy (in priority order):
     * 1. Capability match scoring - agents with better capability match win
     * 2. Load balancing - among equal capability scores, prefer lower load
     * 3. First-come-first-served - if all else is equal, first agent in list wins</p>
     *
     * @param workItem the work item with conflicts
     * @param competingAgents list of agents wanting the work item
     * @return the ID of the selected agent
     * @throws IllegalArgumentException if workItem is null or competingAgents is empty
     */
    public String resolveConflict(WorkItemRecord workItem, List<String> competingAgents) {
        if (workItem == null) {
            throw new IllegalArgumentException("workItem cannot be null");
        }
        if (competingAgents == null || competingAgents.isEmpty()) {
            throw new IllegalArgumentException("competingAgents cannot be null or empty");
        }

        // Register this conflict for tracking
        conflictRegistry.put(workItem.getID(), new ArrayList<>(competingAgents));

        // If only one agent, no conflict to resolve
        if (competingAgents.size() == 1) {
            return competingAgents.get(0);
        }

        // Score each agent based on capability match
        String selectedAgent = competingAgents.stream()
            .max((agent1, agent2) -> {
                int score1 = computeCapabilityScore(agent1, workItem);
                int score2 = computeCapabilityScore(agent2, workItem);

                if (score1 != score2) {
                    return Integer.compare(score1, score2);
                }

                // If capability scores are equal, use load balancing
                int load1 = getAgentLoad(agent1);
                int load2 = getAgentLoad(agent2);

                if (load1 != load2) {
                    // Lower load wins
                    return Integer.compare(load2, load1);
                }

                // If all else is equal, preserve first-come-first-served
                return 0;
            })
            .orElse(competingAgents.get(0)); // Fallback to first agent

        return selectedAgent;
    }

    /**
     * Computes a capability match score for an agent and work item.
     *
     * <p>Score is based on task name matching against agent capabilities.
     * Exact match = 100, partial match = 50, no match = 0.</p>
     */
    private int computeCapabilityScore(String agentId, WorkItemRecord workItem) {
        AgentInfo agent = agentStore.getAgent(agentId);
        if (agent == null || agent.getCapabilities() == null || agent.getCapabilities().isEmpty()) {
            return 0;
        }

        String taskName = workItem.getName();
        if (taskName == null || taskName.isEmpty()) {
            return 0;
        }

        // Check for exact match
        if (agent.getCapabilities().contains(taskName)) {
            return 100;
        }

        // Check for partial match (case-insensitive substring)
        String taskNameLower = taskName.toLowerCase();
        for (String capability : agent.getCapabilities()) {
            if (capability != null && capability.toLowerCase().contains(taskNameLower)) {
                return 50;
            }
        }

        return 0;
    }

    /**
     * Gets the current load for an agent (number of active work items).
     *
     * <p>This is a placeholder that returns 0. In a production system, this
     * would query a work item registry or agent state manager.</p>
     */
    private int getAgentLoad(String agentId) {
        return 0;
    }

    /**
     * Gets the set of work items currently in conflict.
     *
     * @return list of conflicting work item IDs
     */
    public List<String> getConflictingWorkItems() {
        return new ArrayList<>(conflictRegistry.keySet());
    }

    /**
     * Gets the competing agents for a specific work item.
     *
     * @param workItemId the work item ID to query
     * @return list of competing agent IDs, or empty list if no conflict registered
     */
    public List<String> getCompetingAgents(String workItemId) {
        return conflictRegistry.getOrDefault(workItemId, List.of());
    }

    /**
     * Clears the conflict entry for a resolved work item.
     *
     * @param workItemId the work item ID to clear from the registry
     */
    public void clearConflict(String workItemId) {
        conflictRegistry.remove(workItemId);
    }

    /**
     * Clears all conflicts from the registry.
     */
    public void clearAllConflicts() {
        conflictRegistry.clear();
    }
}