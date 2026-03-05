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
import java.util.Optional;

/**
 * A {@link WorkflowResult} indicating that a workflow halted due to a runtime error,
 * constraint violation, or unhandled exception.
 *
 * <p>This result captures the primary failure reason as a human-readable string, plus
 * an optional {@link Throwable} for cases where a Java exception caused the failure.
 * When no exception is present (e.g., a semantic failure such as an OR-join deadlock),
 * {@link #cause()} returns an empty {@link Optional}.</p>
 *
 * <p>Usage in exhaustive pattern matching:</p>
 * <pre>{@code
 * if (result instanceof FailedWorkflow f) {
 *     logger.error("Workflow {} failed: {}", f.workflowID(), f.reason());
 *     f.cause().ifPresent(ex -> logger.error("Caused by:", ex));
 * }
 * }</pre>
 */
public final class FailedWorkflow extends WorkflowResult {

    private final String reason;
    private final Throwable cause;

    /**
     * Constructs a failed workflow result with an associated exception.
     *
     * @param workflowID  the identifier of the workflow specification that was executed
     * @param durationMs  elapsed execution time in milliseconds (time until failure)
     * @param completedAt the instant the workflow reached the failed terminal state
     * @param reason      a concise, human-readable description of why the workflow failed;
     *                    must not be null or blank
     * @param cause       the exception that caused the failure; may be null if the failure
     *                    is semantic rather than exception-driven
     * @throws IllegalArgumentException if {@code workflowID} is null or blank,
     *                                  {@code durationMs} is negative, or {@code reason}
     *                                  is null or blank
     * @throws NullPointerException     if {@code completedAt} is null
     */
    public FailedWorkflow(String workflowID, long durationMs, Instant completedAt,
                          String reason, Throwable cause) {
        super(workflowID, durationMs, completedAt);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be null or blank");
        }
        this.reason = reason;
        this.cause = cause;
    }

    /**
     * Constructs a failed workflow result without an associated exception (semantic failure).
     *
     * @param workflowID  the identifier of the workflow specification that was executed
     * @param durationMs  elapsed execution time in milliseconds (time until failure)
     * @param completedAt the instant the workflow reached the failed terminal state
     * @param reason      a concise, human-readable description of why the workflow failed;
     *                    must not be null or blank
     * @throws IllegalArgumentException if {@code workflowID} is null or blank,
     *                                  {@code durationMs} is negative, or {@code reason}
     *                                  is null or blank
     * @throws NullPointerException     if {@code completedAt} is null
     */
    public FailedWorkflow(String workflowID, long durationMs, Instant completedAt,
                          String reason) {
        this(workflowID, durationMs, completedAt, reason, null);
    }

    /**
     * Returns the human-readable description of the failure reason.
     *
     * @return reason string; never null or blank
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns the exception that caused this workflow failure, if one exists.
     *
     * <p>The optional is empty for semantic failures (e.g., OR-join deadlock, invalid
     * data binding) that do not originate from a Java exception.</p>
     *
     * @return an {@link Optional} containing the causing {@link Throwable}, or empty
     */
    public Optional<Throwable> cause() {
        return Optional.ofNullable(cause);
    }

    @Override
    public String statusCode() {
        return "FAILED";
    }

    @Override
    public String message() {
        return reason;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FailedWorkflow other)) return false;
        return workflowID().equals(other.workflowID())
                && durationMs() == other.durationMs()
                && completedAt().equals(other.completedAt())
                && reason.equals(other.reason)
                && Objects.equals(cause, other.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workflowID(), durationMs(), completedAt(), reason, cause);
    }

    @Override
    public String toString() {
        return "FailedWorkflow[workflowID=" + workflowID() +
                ", durationMs=" + durationMs() +
                ", completedAt=" + completedAt() +
                ", reason=" + reason +
                ", hasCause=" + (cause != null) +
                "]";
    }
}
