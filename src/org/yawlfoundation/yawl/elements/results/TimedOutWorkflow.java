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
 * A {@link WorkflowResult} indicating that a workflow was forcibly terminated because
 * it exceeded its configured execution time limit.
 *
 * <p>This result records both the configured timeout threshold ({@link #timeoutMs()})
 * and the task that was in execution at the moment the timeout fired
 * ({@link #timedOutTaskID()}). This supports post-mortem analysis and retry strategies
 * that skip or reassign the blocking task.</p>
 *
 * <p>Usage in exhaustive pattern matching:</p>
 * <pre>{@code
 * if (result instanceof TimedOutWorkflow t) {
 *     logger.warn("Workflow {} timed out after {}ms (limit: {}ms), stuck at task: {}",
 *         t.workflowID(), t.durationMs(), t.timeoutMs(), t.timedOutTaskID());
 * }
 * }</pre>
 */
public final class TimedOutWorkflow extends WorkflowResult {

    private final long timeoutMs;
    private final String timedOutTaskID;

    /**
     * Constructs a timed-out workflow result.
     *
     * @param workflowID      the identifier of the workflow specification that was executed
     * @param durationMs      elapsed execution time in milliseconds at the moment of timeout
     *                        cancellation; must be non-negative
     * @param completedAt     the instant the timeout was enforced and the workflow cancelled
     * @param timeoutMs       the configured maximum execution time in milliseconds that was
     *                        exceeded; must be positive
     * @param timedOutTaskID  the identifier of the task that was active (or blocked) when
     *                        the timeout fired; must not be null or blank
     * @throws IllegalArgumentException if {@code workflowID} or {@code timedOutTaskID} is
     *                                  null or blank, {@code durationMs} is negative, or
     *                                  {@code timeoutMs} is not positive
     * @throws NullPointerException     if {@code completedAt} is null
     */
    public TimedOutWorkflow(String workflowID, long durationMs, Instant completedAt,
                            long timeoutMs, String timedOutTaskID) {
        super(workflowID, durationMs, completedAt);
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException(
                    "timeoutMs must be positive, got: " + timeoutMs);
        }
        if (timedOutTaskID == null || timedOutTaskID.isBlank()) {
            throw new IllegalArgumentException("timedOutTaskID must not be null or blank");
        }
        this.timeoutMs = timeoutMs;
        this.timedOutTaskID = timedOutTaskID;
    }

    /**
     * Returns the configured execution time limit that this workflow exceeded.
     *
     * @return timeout threshold in milliseconds; always positive
     */
    public long timeoutMs() {
        return timeoutMs;
    }

    /**
     * Returns the identifier of the task that was active at the moment the timeout fired.
     *
     * <p>This is the task whose execution (or waiting) caused the workflow to exceed
     * its time budget. Use this to target retry or escalation logic.</p>
     *
     * @return task identifier; never null or blank
     */
    public String timedOutTaskID() {
        return timedOutTaskID;
    }

    @Override
    public String statusCode() {
        return "TIMEOUT";
    }

    @Override
    public String message() {
        return "Workflow exceeded timeout of " + timeoutMs + "ms at task: " + timedOutTaskID;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TimedOutWorkflow other)) return false;
        return workflowID().equals(other.workflowID())
                && durationMs() == other.durationMs()
                && completedAt().equals(other.completedAt())
                && timeoutMs == other.timeoutMs
                && timedOutTaskID.equals(other.timedOutTaskID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workflowID(), durationMs(), completedAt(), timeoutMs,
                timedOutTaskID);
    }

    @Override
    public String toString() {
        return "TimedOutWorkflow[workflowID=" + workflowID() +
                ", durationMs=" + durationMs() +
                ", completedAt=" + completedAt() +
                ", timeoutMs=" + timeoutMs +
                ", timedOutTaskID=" + timedOutTaskID +
                "]";
    }
}
