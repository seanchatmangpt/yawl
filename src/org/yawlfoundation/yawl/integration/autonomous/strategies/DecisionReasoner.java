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
 * Produces output for work items based on agent decision logic.
 *
 * <p>Output production is not yet implemented. The method throws
 * {@link UnsupportedOperationException} to prevent silent fabrication
 * of output data in production. A concrete subclass must supply the
 * domain-specific decision logic.</p>
 *
 * @since YAWL 6.0
 */
public class DecisionReasoner {

    /**
     * Produces output for the given work item.
     *
     * @param workItem the work item to process
     * @return the output data for the work item
     * @throws UnsupportedOperationException always — not yet implemented
     */
    public String produceOutput(WorkItemRecord workItem) {
        throw new UnsupportedOperationException(
            "produceOutput() is not implemented. Output production requires:\n" +
            "  1. Domain-specific business rules for each task type\n" +
            "  2. Parsing of work item input data (workItem.getDataListAsXML())\n" +
            "  3. Application of XQuery/XPath expressions to derive output values\n" +
            "  4. Serialisation of result back to YAWL data XML format\n" +
            "Create a concrete subclass of DecisionReasoner with task-specific logic."
        );
    }
}