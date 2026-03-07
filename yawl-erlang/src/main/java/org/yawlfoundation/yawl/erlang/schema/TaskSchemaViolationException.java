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
package org.yawlfoundation.yawl.erlang.schema;

/**
 * Thrown by {@link SchemaValidationInterceptor} when a task's input or output
 * JSON violates the declared {@link TaskSchemaContract}.
 *
 * <p>This exception causes the task to be aborted immediately — the task's
 * business logic does not execute. The violation is also published as a
 * {@link org.yawlfoundation.yawl.erlang.workflow.TaskSchemaViolation} event
 * to the {@link org.yawlfoundation.yawl.erlang.workflow.WorkflowEventBus}.</p>
 */
public final class TaskSchemaViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String taskId;
    private final String expectedSchemaSummary;
    private final String actualJson;
    private final String diff;

    /**
     * Constructs a schema violation exception.
     *
     * @param taskId                 the task identifier where the violation occurred
     * @param expectedSchemaSummary  human-readable summary of the expected schema
     * @param actualJson             the actual JSON that violated the schema
     * @param diff                   a brief description of the mismatch (e.g., "missing required field 'orderId'")
     */
    public TaskSchemaViolationException(
            String taskId,
            String expectedSchemaSummary,
            String actualJson,
            String diff) {
        super("Schema violation in task '" + taskId + "': " + diff
                + " | expected: " + expectedSchemaSummary);
        this.taskId = taskId;
        this.expectedSchemaSummary = expectedSchemaSummary;
        this.actualJson = actualJson;
        this.diff = diff;
    }

    /** The task identifier where the violation was detected. */
    public String getTaskId() { return taskId; }

    /** Human-readable summary of the expected schema (e.g., {@code "OrderInput@2.0 (5 fields, 2 required)"}). */
    public String getExpectedSchemaSummary() { return expectedSchemaSummary; }

    /** The actual JSON input that did not conform to the schema. */
    public String getActualJson() { return actualJson; }

    /** Brief description of the specific mismatch (e.g., "missing required field 'orderId'"). */
    public String getDiff() { return diff; }
}
