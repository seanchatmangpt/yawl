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

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

/**
 * Strategy for reasoning about work item completion and producing output.
 *
 * Implementations can use rule-based, AI-based, or other reasoning mechanisms
 * to determine how to complete a work item and generate the appropriate output XML.
 *
 * <p>This interface combines decision-making and output generation for workflows
 * that require stateless AI reasoning (e.g., using ZAI or similar services).</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public interface DecisionReasoner {

    /**
     * Produce output XML for the work item.
     *
     * <p>The reasoner analyzes the work item context, input data, and task
     * requirements to generate valid XML output for workflow completion.</p>
     *
     * @param workItem the work item to complete
     * @return valid XML output (root element = decomposition id, e.g., Approve_Purchase_Order)
     * @throws RuntimeException if output cannot be generated
     */
    String produceOutput(WorkItemRecord workItem);
}
