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
 * Abstract base class for reasoning about work item eligibility for agent processing.
 *
 * <p>Subclasses must implement {@link #isEligible} with capability matching logic.
 * Eligibility reasoning typically involves:
 * <ol>
 *   <li>Extracting task name from {@link WorkItemRecord#getTaskName()}</li>
 *   <li>Matching against agent's declared capabilities</li>
 *   <li>Optional load checking (reject if agent overloaded)</li>
 *   <li>Any domain-specific precondition checks</li>
 * </ol></p>
 *
 * @since YAWL 6.0
 */
public abstract class EligibilityReasoner {

    /**
     * Determines if a work item is eligible for this agent to process.
     *
     * <p>Subclasses must implement this method with capability matching and any
     * other domain-specific eligibility criteria.</p>
     *
     * @param workItem the work item to evaluate
     * @return true if the work item can be processed by this agent, false otherwise
     * @throws IllegalArgumentException if workItem is null
     */
    public abstract boolean isEligible(WorkItemRecord workItem);

    /**
     * Gets the priority or confidence level of this eligibility determination.
     *
     * <p>May be overridden to support prioritized agent selection when multiple
     * agents are eligible. Default implementation returns 0 (equal priority).</p>
     *
     * @param workItem the work item for context
     * @return priority score (higher = better), or 0 for equal priority
     */
    public int getEligibilityScore(WorkItemRecord workItem) {
        return 0;
    }
}