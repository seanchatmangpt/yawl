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
 * Functional interface for reasoning about work item eligibility for autonomous agents.
 *
 * <p>Implementers of this interface evaluate whether a work item is suitable for
 * processing by a specific autonomous agent. Evaluations typically consider:
 * <ul>
 *   <li>Agent capabilities vs task requirements</li>
 *   <li>Work item data vs agent expertise domains</li>
 *   <li>Agent current load and availability</li>
 *   <li>Business rules and assignment constraints</li>
 * </ul>
 *
 * <p>This is a functional interface and can be implemented using lambda expressions:</p>
 * <pre>{@code
 * EligibilityReasoner reasoner = workItem ->
 *     workItem.getTaskName().equals("ApprovalTask");
 * }</pre>
 *
 * @since YAWL 6.0
 */
@FunctionalInterface
public interface EligibilityReasoner {

    /**
     * Determines whether a work item is eligible for processing by the agent.
     *
     * <p>This method performs an eligibility check to determine if the agent
     * can and should process the given work item. The evaluation criteria are
     * implementation-specific but typically include:
     * <ol>
     *   <li>Verification that the agent has required capabilities</li>
     *   <li>Validation of work item data against agent expertise</li>
     *   <li>Assessment of agent availability and workload</li>
     *   <li>Application of business rules and assignment policies</li>
     * </ol></p>
     *
     * @param workItem the work item to evaluate for eligibility
     * @return {@code true} if the work item is eligible for processing by this agent;
     *         {@code false} if the work item cannot be processed by this agent
     */
    boolean isEligible(WorkItemRecord workItem);
}
