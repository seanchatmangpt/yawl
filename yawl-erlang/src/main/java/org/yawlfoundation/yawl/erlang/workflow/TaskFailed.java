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
 * Event fired when a workflow task fails during execution.
 *
 * <p>Task failure is distinct from {@link TaskSchemaViolation}: a task failure
 * means the business logic failed (database error, external service unavailable,
 * business rule rejection). A schema violation means the data structure was wrong
 * before the task's business logic ran.</p>
 *
 * @param instanceId unique identifier for the workflow case
 * @param taskId     the task identifier within the workflow specification
 * @param reason     human-readable description of the failure cause
 * @param at         the instant the task failed
 */
public record TaskFailed(
        String instanceId,
        String taskId,
        String reason,
        Instant at) implements WorkflowEvent {

    public TaskFailed {
        if (instanceId == null || instanceId.isBlank())
            throw new IllegalArgumentException("instanceId must be non-blank");
        if (taskId == null || taskId.isBlank())
            throw new IllegalArgumentException("taskId must be non-blank");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("reason must be non-blank");
        if (at == null)
            throw new IllegalArgumentException("at must be non-null");
    }
}
