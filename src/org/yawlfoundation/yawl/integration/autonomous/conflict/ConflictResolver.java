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

import java.util.Collections;
import java.util.List;

/**
 * Resolves conflicts when multiple agents compete for the same work item.
 *
 * <p>Conflict resolution is not yet implemented. Both methods throw
 * {@link UnsupportedOperationException} to prevent silent no-op behaviour
 * in production. Implementations must provide a concrete subclass.</p>
 *
 * @since YAWL 6.0
 */
public class ConflictResolver {

    /**
     * Resolves conflicts for a work item among competing agents.
     *
     * @param workItem the work item with conflicts
     * @param competingAgents list of agents wanting the work item
     * @return the ID of the selected agent
     * @throws UnsupportedOperationException always — not yet implemented
     */
    public String resolveConflict(WorkItemRecord workItem, List<String> competingAgents) {
        throw new UnsupportedOperationException(
            "resolveConflict() is not implemented. Conflict resolution requires:\n" +
            "  1. A priority or load-balancing strategy injected at construction time\n" +
            "  2. A registry of agent capability scores (see AgentInfoStore)\n" +
            "  3. A deadlock-detection sweep over competing work items\n" +
            "Create a concrete subclass of ConflictResolver and inject it into the agent loop."
        );
    }

    /**
     * Gets the list of work items currently in conflict.
     *
     * @return list of conflicting work item IDs
     * @throws UnsupportedOperationException always — not yet implemented
     */
    public List<String> getConflictingWorkItems() {
        throw new UnsupportedOperationException(
            "getConflictingWorkItems() is not implemented. Conflict tracking requires:\n" +
            "  1. A concurrent map keyed by work-item ID with competing agent lists\n" +
            "  2. Registration hooks in the agent checkout path\n" +
            "  3. Eviction on successful checkout or timeout\n" +
            "Create a concrete subclass of ConflictResolver and inject it into the agent loop."
        );
    }
}