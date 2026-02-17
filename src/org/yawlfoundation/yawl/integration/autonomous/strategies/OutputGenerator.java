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
 * Strategy for generating output XML for work item completion.
 *
 * Implementations can use templates, AI-based generation, or other mechanisms.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public interface OutputGenerator {

    /**
     * Generate output XML for the work item based on a decision.
     *
     * @param workItem the work item to complete
     * @param decision the decision context (from DecisionReasoner)
     * @return valid XML output (root = decomposition id)
     */
    String generateOutput(WorkItemRecord workItem, Object decision);
}
