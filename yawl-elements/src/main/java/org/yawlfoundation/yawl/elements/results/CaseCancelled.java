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
 * A {@link CaseOutcome} indicating that a case instance was explicitly cancelled before
 * it could complete normally.
 *
 * <p>Cancellation is a deliberate operator or client action, distinct from failure.
 * A cancelled case was running correctly but was terminated early — for example, because
 * the underlying business event it was serving became irrelevant, or a human supervisor
 * decided to abort. The actor that initiated the cancellation is captured in
 * {@link #cancelledBy()} and an optional free-text rationale in {@link #cancellationNote()}.</p>
 *
 * <p>Usage in exhaustive pattern matching:</p>
 * <pre>{@code
 * if (outcome instanceof CaseCancelled cc) {
 *     auditLog.recordCancellation(cc.caseID(), cc.cancelledBy(),
 *         cc.cancellationNote(), cc.terminatedAt());
 * }
 * }</pre>
 */
public final class CaseCancelled extends CaseOutcome {

    private final String cancelledBy;
    private final String cancellationNote;

    /**
     * Constructs a cancelled case outcome with a full rationale note.
     *
     * @param caseID           the unique identifier of the cancelled case instance
     * @param specificationID  the identifier of the workflow specification
     * @param durationMs       elapsed time from launch to cancellation in milliseconds
     * @param terminatedAt     the instant the cancellation was applied
     * @param cancelledBy      the identifier of the actor (user ID, agent ID, or system
     *                         component) that initiated cancellation; must not be null or blank
     * @param cancellationNote a free-text explanation for the cancellation; must not be null
     *                         (use empty string when no note is provided)
     * @throws IllegalArgumentException if {@code caseID}, {@code specificationID}, or
     *                                  {@code cancelledBy} is null or blank, or
     *                                  {@code durationMs} is negative
     * @throws NullPointerException     if {@code terminatedAt} or {@code cancellationNote}
     *                                  is null
     */
    public CaseCancelled(String caseID, String specificationID,
                         long durationMs, Instant terminatedAt,
                         String cancelledBy, String cancellationNote) {
        super(caseID, specificationID, durationMs, terminatedAt);
        if (cancelledBy == null || cancelledBy.isBlank()) {
            throw new IllegalArgumentException("cancelledBy must not be null or blank");
        }
        this.cancelledBy = cancelledBy;
        this.cancellationNote = Objects.requireNonNull(cancellationNote,
                "cancellationNote must not be null; use empty string when no note is provided");
    }

    /**
     * Constructs a cancelled case outcome without an explanatory note.
     *
     * @param caseID          the unique identifier of the cancelled case instance
     * @param specificationID the identifier of the workflow specification
     * @param durationMs      elapsed time from launch to cancellation in milliseconds
     * @param terminatedAt    the instant the cancellation was applied
     * @param cancelledBy     the identifier of the actor that initiated cancellation
     * @throws IllegalArgumentException if {@code caseID}, {@code specificationID}, or
     *                                  {@code cancelledBy} is null or blank, or
     *                                  {@code durationMs} is negative
     * @throws NullPointerException     if {@code terminatedAt} is null
     */
    public CaseCancelled(String caseID, String specificationID,
                         long durationMs, Instant terminatedAt,
                         String cancelledBy) {
        this(caseID, specificationID, durationMs, terminatedAt, cancelledBy, "");
    }

    /**
     * Returns the identifier of the actor that initiated the cancellation.
     *
     * <p>This may be a user ID (e.g., "user:admin"), an agent ID
     * (e.g., "agent:supervisor-agent"), or a system component name
     * (e.g., "system:timeout-watchdog").</p>
     *
     * @return actor identifier; never null or blank
     */
    public String cancelledBy() {
        return cancelledBy;
    }

    /**
     * Returns the free-text rationale provided at the time of cancellation.
     *
     * @return cancellation note; never null, may be empty when no note was provided
     */
    public String cancellationNote() {
        return cancellationNote;
    }

    @Override
    public String statusCode() {
        return "CANCELLED";
    }

    @Override
    public String summary() {
        String note = cancellationNote.isEmpty() ? "" : " (" + cancellationNote + ")";
        return "Case " + caseID() + " cancelled by [" + cancelledBy + "]" + note;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CaseCancelled other)) return false;
        return caseID().equals(other.caseID())
                && specificationID().equals(other.specificationID())
                && durationMs() == other.durationMs()
                && terminatedAt().equals(other.terminatedAt())
                && cancelledBy.equals(other.cancelledBy)
                && cancellationNote.equals(other.cancellationNote);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseID(), specificationID(), durationMs(), terminatedAt(),
                cancelledBy, cancellationNote);
    }

    @Override
    public String toString() {
        return "CaseCancelled[caseID=" + caseID() +
                ", specificationID=" + specificationID() +
                ", durationMs=" + durationMs() +
                ", terminatedAt=" + terminatedAt() +
                ", cancelledBy=" + cancelledBy +
                ", cancellationNote=" + (cancellationNote.isEmpty() ? "<none>" : cancellationNote) +
                "]";
    }
}
