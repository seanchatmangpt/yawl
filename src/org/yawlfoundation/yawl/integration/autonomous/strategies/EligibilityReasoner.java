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
 * Strategy for determining if an agent should handle a work item.
 *
 * Implementations can use rule-based, AI-based, or other reasoning mechanisms.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public interface EligibilityReasoner {

    /**
     * Determine if this agent should handle the work item.
     *
     * @param workItem the work item to evaluate
     * @return true if the agent should handle it
     */
    boolean isEligible(WorkItemRecord workItem);
}
