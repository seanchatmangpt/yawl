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

package org.yawlfoundation.yawl.integration.orderfulfillment;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

/**
 * Supplies richer task context from an MCP server (e.g. task_completion_guide prompt).
 * When available, enhances DecisionWorkflow prompts with spec-aware guidance.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public interface McpTaskContextSupplier {

    /**
     * Get task completion guidance for a work item.
     *
     * @param workItem the work item
     * @return guidance text, or null if unavailable
     */
    String getTaskCompletionGuide(WorkItemRecord workItem);
}
