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

import java.time.Instant;

/**
 * Event fired when a workflow task begins execution.
 *
 * @param instanceId unique identifier for the workflow case
 * @param taskId     the task identifier within the workflow specification
 * @param inputJson  JSON-serialised input data for the task (may be empty string if none)
 * @param at         the instant the task started
 */
public record TaskStarted(
        String instanceId,
        String taskId,
        String inputJson,
        Instant at) implements WorkflowEvent {

    public TaskStarted {
        if (instanceId == null || instanceId.isBlank())
            throw new IllegalArgumentException("instanceId must be non-blank");
        if (taskId == null || taskId.isBlank())
            throw new IllegalArgumentException("taskId must be non-blank");
        if (inputJson == null)
            throw new IllegalArgumentException("inputJson must be non-null (use empty string for no input)");
        if (at == null)
            throw new IllegalArgumentException("at must be non-null");
    }
}
