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

/**
 * Reasons about whether a work item is eligible for processing by an agent.
 *
 * <p>Eligibility reasoning is not yet implemented. The method throws
 * {@link UnsupportedOperationException} to prevent the agent loop from
 * claiming all work items are eligible (which is incorrect). A concrete
 * subclass must supply real capability matching logic.</p>
 *
 * @since YAWL 6.0
 */
public class EligibilityReasoner {

    /**
     * Determines if a work item is eligible for this agent.
     *
     * @param workItem the work item to evaluate
     * @return true if the work item can be processed by this agent
     * @throws UnsupportedOperationException always — not yet implemented
     */
    public boolean isEligible(WorkItemRecord workItem) {
        throw new UnsupportedOperationException(
            "isEligible() is not implemented. Eligibility reasoning requires:\n" +
            "  1. A capability model for the agent (set of task names or resource roles)\n" +
            "  2. Extraction of task name / resource role from workItem.getTaskName()\n" +
            "  3. Matching logic (exact, wildcard, or role-hierarchy)\n" +
            "  4. Optional load check: reject if agent has too many active items\n" +
            "Create a concrete subclass of EligibilityReasoner and inject it into the agent."
        );
    }
}