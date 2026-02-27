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
 * Functional interface for producing output decisions for work items processed by autonomous agents.
 *
 * <p>Implementers of this interface apply agent decision logic to work items and
 * generate appropriate output. Implementations typically perform:
 * <ul>
 *   <li>Analysis of work item input data and context</li>
 *   <li>Application of domain-specific logic and rules</li>
 *   <li>Generation of task completion output</li>
 *   <li>Handling of validation and error conditions</li>
 * </ul>
 *
 * <p>This is a functional interface and can be implemented using lambda expressions:</p>
 * <pre>{@code
 * DecisionReasoner reasoner = workItem ->
 *     "Approved: " + workItem.getTaskName();
 * }</pre>
 *
 * @since YAWL 6.0
 */
@FunctionalInterface
public interface DecisionReasoner {

    /**
     * Produces output for the given work item based on agent decision logic.
     *
     * <p>This method applies the agent's reasoning capabilities to the work item
     * and produces the output data that represents the agent's decision or result.
     * The process typically involves:
     * <ol>
     *   <li>Extracting and analyzing work item input data</li>
     *   <li>Applying business logic and domain-specific rules</li>
     *   <li>Generating appropriate output representing the decision</li>
     *   <li>Validating the output against requirements</li>
     * </ol></p>
     *
     * <p>The output format is implementation-specific and depends on the domain
     * and task requirements. Common formats include XML, JSON, or plain text.</p>
     *
     * @param workItem the work item to process and generate output for
     * @return the output data produced by applying decision logic to the work item
     * @throws IllegalArgumentException if the work item is invalid or incomplete
     * @throws RuntimeException if decision logic fails or validation errors occur
     */
    String produceOutput(WorkItemRecord workItem);
}
