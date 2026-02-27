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

package org.yawlfoundation.yawl.integration.autonomous.conflict;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves conflicts when multiple agents compete for the same work item.
 *
 * <p>Uses a first-come priority strategy: agents are ranked by their position
 * in the competing agents list, which represents arrival order. The first
 * agent in the list wins the conflict. Tracks active conflicts for
 * observability.</p>
 *
 * <p>Thread-safe: uses ConcurrentHashMap for conflict tracking.</p>
 *
 * @since YAWL 6.0
 */
public class ConflictResolver {

    private final Map<String, String> activeConflicts = new ConcurrentHashMap<>();

    /**
     * Resolves a conflict for a work item among competing agents.
     *
     * <p>Strategy: first-come-first-served. The first agent in the competing
     * agents list is selected as the winner. The conflict is recorded for
     * observability.</p>
     *
     * @param workItem the work item with conflicts
     * @param competingAgents list of agent IDs wanting the work item, ordered by arrival
     * @return the ID of the selected agent
     * @throws IllegalArgumentException if workItem is null or competingAgents is null/empty
     */
    public String resolveConflict(WorkItemRecord workItem, List<String> competingAgents) {
        if (workItem == null) {
            throw new IllegalArgumentException("workItem is required");
        }
        if (competingAgents == null || competingAgents.isEmpty()) {
            throw new IllegalArgumentException("competingAgents must contain at least one agent");
        }

        String workItemId = workItem.getID();
        String winner = competingAgents.getFirst();

        activeConflicts.put(workItemId, winner);

        return winner;
    }

    /**
     * Gets the work item IDs that have had conflicts resolved.
     *
     * @return unmodifiable list of work item IDs with recorded conflicts
     */
    public List<String> getConflictingWorkItems() {
        return List.copyOf(activeConflicts.keySet());
    }

    /**
     * Gets the winning agent for a previously resolved conflict.
     *
     * @param workItemId the work item ID
     * @return the winning agent ID, or null if no conflict recorded
     */
    public String getWinner(String workItemId) {
        return activeConflicts.get(workItemId);
    }

    /**
     * Clears a resolved conflict once the work item has been processed.
     *
     * @param workItemId the work item ID to clear
     */
    public void clearConflict(String workItemId) {
        if (workItemId != null) {
            activeConflicts.remove(workItemId);
        }
    }

    /**
     * Clears all recorded conflicts.
     */
    public void clearAll() {
        activeConflicts.clear();
    }

    /**
     * Gets the number of active conflicts being tracked.
     *
     * @return the count of active conflicts
     */
    public int getActiveConflictCount() {
        return activeConflicts.size();
    }
}
