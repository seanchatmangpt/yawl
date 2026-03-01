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

/**
 * Event fired when a task receives data that violates its declared schema contract.
 *
 * <p>Schema violations are distinct from task failures: a violation means the data
 * structure itself was wrong (missing required field, wrong type) — the task's
 * business logic has not run. The task is aborted immediately on violation.</p>
 *
 * <p>High rates of violations at a specific task boundary indicate an upstream
 * integration problem (e.g., an upstream service changed its output schema without
 * coordinating with the workflow).</p>
 *
 * @param instanceId     unique identifier for the workflow case
 * @param taskId         the task identifier within the workflow specification
 * @param expectedSchema human-readable description of the expected schema contract
 * @param actualJson     the actual JSON payload that violated the contract
 * @param diffSummary    a brief description of the difference (e.g., "missing required field 'orderId'")
 */
public record TaskSchemaViolation(
        String instanceId,
        String taskId,
        String expectedSchema,
        String actualJson,
        String diffSummary) implements WorkflowEvent {

    public TaskSchemaViolation {
        if (instanceId == null || instanceId.isBlank())
            throw new IllegalArgumentException("instanceId must be non-blank");
        if (taskId == null || taskId.isBlank())
            throw new IllegalArgumentException("taskId must be non-blank");
        if (expectedSchema == null)
            throw new IllegalArgumentException("expectedSchema must be non-null");
        if (actualJson == null)
            throw new IllegalArgumentException("actualJson must be non-null");
        if (diffSummary == null || diffSummary.isBlank())
            throw new IllegalArgumentException("diffSummary must be non-blank");
    }
}
