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

package org.yawlfoundation.yawl.elements.results;

import java.time.Instant;
import java.util.Objects;

/**
 * Sealed hierarchy representing the outcome of executing a YAWL workflow specification.
 *
 * <p>A {@code WorkflowResult} captures the terminal state of a single workflow execution
 * attempt. Three outcomes are possible:</p>
 * <ul>
 *   <li>{@link SuccessfulWorkflow} — the workflow executed to completion without error</li>
 *   <li>{@link FailedWorkflow} — the workflow halted due to an error or exception</li>
 *   <li>{@link TimedOutWorkflow} — the workflow exceeded its configured time limit</li>
 * </ul>
 *
 * <p>The sealed type enables exhaustive switch expressions at call sites:</p>
 * <pre>{@code
 * String summary = switch (result) {
 *     case SuccessfulWorkflow s -> "OK in " + s.durationMs() + "ms: " + s.outputData();
 *     case FailedWorkflow f     -> "FAIL: " + f.reason();
 *     case TimedOutWorkflow t   -> "TIMEOUT after " + t.timeoutMs() + "ms";
 * };
 * }</pre>
 *
 * <p>All subclasses are immutable. The {@code workflowID} and {@code durationMs} fields
 * are common to all outcomes and available on the sealed parent.</p>
 *
 * @see SuccessfulWorkflow
 * @see FailedWorkflow
 * @see TimedOutWorkflow
 */
public abstract sealed class WorkflowResult
        permits SuccessfulWorkflow, FailedWorkflow, TimedOutWorkflow {

    private final String workflowID;
    private final long durationMs;
    private final Instant completedAt;

    /**
     * Constructs a workflow result with common fields.
     *
     * @param workflowID  the identifier of the workflow specification that was executed;
     *                    must not be null or blank
     * @param durationMs  the elapsed execution time in milliseconds; must be non-negative
     * @param completedAt the instant at which the workflow reached its terminal state;
     *                    must not be null
     * @throws IllegalArgumentException if {@code workflowID} is null or blank,
     *                                  or {@code durationMs} is negative
     * @throws NullPointerException     if {@code completedAt} is null
     */
    protected WorkflowResult(String workflowID, long durationMs, Instant completedAt) {
        if (workflowID == null || workflowID.isBlank()) {
            throw new IllegalArgumentException("workflowID must not be null or blank");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException(
                    "durationMs must be non-negative, got: " + durationMs);
        }
        this.workflowID = workflowID;
        this.durationMs = durationMs;
        this.completedAt = Objects.requireNonNull(completedAt, "completedAt must not be null");
    }

    /**
     * Returns the workflow specification identifier.
     *
     * @return workflow identifier; never null or blank
     */
    public final String workflowID() {
        return workflowID;
    }

    /**
     * Returns the elapsed execution time in milliseconds.
     *
     * @return duration in milliseconds; always non-negative
     */
    public final long durationMs() {
        return durationMs;
    }

    /**
     * Returns the instant at which this workflow reached its terminal state.
     *
     * @return completion timestamp; never null
     */
    public final Instant completedAt() {
        return completedAt;
    }

    /**
     * Returns a short status code for this result (e.g., "SUCCESS", "FAILED", "TIMEOUT").
     *
     * @return non-null, non-blank status code string
     */
    public abstract String statusCode();

    /**
     * Returns a human-readable description of this result.
     *
     * @return non-null message string
     */
    public abstract String message();

    /**
     * Returns {@code true} if this result represents a successful execution.
     *
     * @return {@code true} for {@link SuccessfulWorkflow}; {@code false} otherwise
     */
    public final boolean isSuccess() {
        return this instanceof SuccessfulWorkflow;
    }

    /**
     * Returns {@code true} if this result represents a failed execution.
     *
     * @return {@code true} for {@link FailedWorkflow}; {@code false} otherwise
     */
    public final boolean isFailure() {
        return this instanceof FailedWorkflow;
    }

    /**
     * Returns {@code true} if this result represents a timed-out execution.
     *
     * @return {@code true} for {@link TimedOutWorkflow}; {@code false} otherwise
     */
    public final boolean isTimeout() {
        return this instanceof TimedOutWorkflow;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[workflowID=" + workflowID +
                ", durationMs=" + durationMs +
                ", completedAt=" + completedAt +
                ", status=" + statusCode() +
                "]";
    }
}
