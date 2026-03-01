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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.erlang.workflow;

import java.time.Duration;

/**
 * Event fired when a workflow task completes successfully.
 *
 * @param instanceId unique identifier for the workflow case
 * @param taskId     the task identifier within the workflow specification
 * @param outputJson JSON-serialised output data from the task (may be empty string if none)
 * @param elapsed    wall-clock duration from task start to completion
 */
public record TaskCompleted(
        String instanceId,
        String taskId,
        String outputJson,
        Duration elapsed) implements WorkflowEvent {

    public TaskCompleted {
        if (instanceId == null || instanceId.isBlank())
            throw new IllegalArgumentException("instanceId must be non-blank");
        if (taskId == null || taskId.isBlank())
            throw new IllegalArgumentException("taskId must be non-blank");
        if (outputJson == null)
            throw new IllegalArgumentException("outputJson must be non-null");
        if (elapsed == null || elapsed.isNegative())
            throw new IllegalArgumentException("elapsed must be non-null and non-negative");
    }
}
