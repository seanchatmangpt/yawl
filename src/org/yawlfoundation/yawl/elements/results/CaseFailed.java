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
 * A {@link CaseOutcome} indicating that a case instance aborted due to an error.
 *
 * <p>Case failure may arise from several conditions: a task that throws an unhandled
 * exception, a data binding violation that cannot be resolved, an OR-join deadlock
 * detected by the engine, or a resource allocation failure that exhausts all retries.
 * The failure reason and (where available) the causing exception are captured here.</p>
 *
 * <p>Usage in exhaustive pattern matching:</p>
 * <pre>{@code
 * if (outcome instanceof CaseFailed f) {
 *     incidentLogger.record(f.caseID(), f.failureReason(), f.failedAtTaskID());
 *     f.cause().ifPresent(incidentLogger::recordException);
 * }
 * }</pre>
 */
public final class CaseFailed extends CaseOutcome {

    private final String failureReason;
    private final String failedAtTaskID;
    private final Throwable cause;

    /**
     * Constructs a failed case outcome with full diagnostic information.
     *
     * @param caseID          the unique identifier of the failed case instance
     * @param specificationID the identifier of the workflow specification
     * @param durationMs      elapsed time from launch to failure in milliseconds
     * @param terminatedAt    the instant the case was aborted
     * @param failureReason   a concise, human-readable description of the failure;
     *                        must not be null or blank
     * @param failedAtTaskID  the identifier of the task at which failure was detected;
     *                        must not be null or blank
     * @param cause           the exception that triggered the failure; may be null for
     *                        semantic failures (e.g., OR-join deadlock)
     * @throws IllegalArgumentException if {@code caseID}, {@code specificationID},
     *                                  {@code failureReason}, or {@code failedAtTaskID}
     *                                  is null or blank, or {@code durationMs} is negative
     * @throws NullPointerException     if {@code terminatedAt} is null
     */
    public CaseFailed(String caseID, String specificationID,
                      long durationMs, Instant terminatedAt,
                      String failureReason, String failedAtTaskID, Throwable cause) {
        super(caseID, specificationID, durationMs, terminatedAt);
        if (failureReason == null || failureReason.isBlank()) {
            throw new IllegalArgumentException("failureReason must not be null or blank");
        }
        if (failedAtTaskID == null || failedAtTaskID.isBlank()) {
            throw new IllegalArgumentException("failedAtTaskID must not be null or blank");
        }
        this.failureReason = failureReason;
        this.failedAtTaskID = failedAtTaskID;
        this.cause = cause;
    }

    /**
     * Constructs a failed case outcome without an associated exception.
     *
     * @param caseID          the unique identifier of the failed case instance
     * @param specificationID the identifier of the workflow specification
     * @param durationMs      elapsed time from launch to failure in milliseconds
     * @param terminatedAt    the instant the case was aborted
     * @param failureReason   a concise, human-readable description of the failure
     * @param failedAtTaskID  the identifier of the task at which failure was detected
     * @throws IllegalArgumentException if any string argument is null or blank, or
     *                                  {@code durationMs} is negative
     * @throws NullPointerException     if {@code terminatedAt} is null
     */
    public CaseFailed(String caseID, String specificationID,
                      long durationMs, Instant terminatedAt,
                      String failureReason, String failedAtTaskID) {
        this(caseID, specificationID, durationMs, terminatedAt,
                failureReason, failedAtTaskID, null);
    }

    /**
     * Returns the human-readable description of why the case failed.
     *
     * @return failure reason; never null or blank
     */
    public String failureReason() {
        return failureReason;
    }

    /**
     * Returns the identifier of the task at which the failure was detected.
     *
     * <p>This is the task that was in an executing or enabled state when the error
     * occurred. Use this for targeted retry or error-handling logic.</p>
     *
     * @return task identifier; never null or blank
     */
    public String failedAtTaskID() {
        return failedAtTaskID;
    }

    /**
     * Returns the exception that caused this case to fail, if one exists.
     *
     * <p>The optional is empty for semantic failures that do not originate from a
     * Java exception (e.g., OR-join deadlock, invalid data schema).</p>
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
    public String summary() {
        return "Case " + caseID() + " failed at task [" + failedAtTaskID +
                "]: " + failureReason;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CaseFailed other)) return false;
        return caseID().equals(other.caseID())
                && specificationID().equals(other.specificationID())
                && durationMs() == other.durationMs()
                && terminatedAt().equals(other.terminatedAt())
                && failureReason.equals(other.failureReason)
                && failedAtTaskID.equals(other.failedAtTaskID)
                && Objects.equals(cause, other.cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseID(), specificationID(), durationMs(), terminatedAt(),
                failureReason, failedAtTaskID, cause);
    }

    @Override
    public String toString() {
        return "CaseFailed[caseID=" + caseID() +
                ", specificationID=" + specificationID() +
                ", durationMs=" + durationMs() +
                ", terminatedAt=" + terminatedAt() +
                ", failureReason=" + failureReason +
                ", failedAtTaskID=" + failedAtTaskID +
                ", hasCause=" + (cause != null) +
                "]";
    }
}
