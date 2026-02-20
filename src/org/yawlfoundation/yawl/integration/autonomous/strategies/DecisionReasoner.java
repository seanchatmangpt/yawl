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
 * Abstract base class for producing output for work items based on agent decision logic.
 *
 * <p>Subclasses must implement {@link #produceOutput} with domain-specific business rules
 * for deriving work item output from input data. Output production typically involves:
 * <ol>
 *   <li>Parsing of work item input data via {@link WorkItemRecord#getDataList()}</li>
 *   <li>Application of domain-specific rules to derive output values</li>
 *   <li>Serialization of result back to YAWL data XML format</li>
 * </ol></p>
 *
 * @since YAWL 6.0
 */
public abstract class DecisionReasoner {

    /**
     * Produces output for the given work item.
     *
     * <p>Subclasses must implement this method with domain-specific logic that derives
     * output data from the work item's input data and task context.</p>
     *
     * @param workItem the work item to process
     * @return the output data for the work item (typically XML-formatted)
     * @throws IllegalArgumentException if workItem is null
     */
    public abstract String produceOutput(WorkItemRecord workItem);

    /**
     * Gets the task name or type that this reasoner handles.
     *
     * <p>May be overridden by subclasses for task-specific reasoning. Default
     * implementation returns null (reasoning applies to any task).</p>
     *
     * @return the task name this reasoner handles, or null for all tasks
     */
    public String getHandledTaskName() {
        return null;
    }
}