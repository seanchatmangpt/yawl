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
 * Sealed hierarchy representing the lifecycle completion state of a YAWL case instance.
 *
 * <p>A case is a single runtime execution of a workflow net. Every case reaches exactly
 * one terminal state, represented by a permitted subtype of this class:</p>
 * <ul>
 *   <li>{@link CaseCompleted} — the case token reached the output condition normally</li>
 *   <li>{@link CaseFailed} — the case aborted due to an error or unhandled exception</li>
 *   <li>{@link CaseCancelled} — the case was explicitly cancelled by a client or operator</li>
 * </ul>
 *
 * <p>The sealed type enables exhaustive switch expressions:</p>
 * <pre>{@code
 * String outcome = switch (caseOutcome) {
 *     case CaseCompleted  c -> "Completed in " + c.durationMs() + "ms";
 *     case CaseFailed     f -> "Failed: " + f.failureReason();
 *     case CaseCancelled  cc -> "Cancelled by " + cc.cancelledBy();
 * };
 * }</pre>
 *
 * <p>All subclasses are immutable. {@code caseID} and {@code specificationID} are
 * common to all outcomes and available on this sealed parent.</p>
 *
 * @see CaseCompleted
 * @see CaseFailed
 * @see CaseCancelled
 */
public abstract sealed class CaseOutcome
        permits CaseCompleted, CaseFailed, CaseCancelled {

    private final String caseID;
    private final String specificationID;
    private final long durationMs;
    private final Instant terminatedAt;

    /**
     * Constructs a case outcome with fields common to all terminal states.
     *
     * @param caseID          the unique identifier of the case instance; must not be
     *                        null or blank
     * @param specificationID the identifier of the workflow specification from which this
     *                        case was launched; must not be null or blank
     * @param durationMs      the elapsed time from case launch to terminal state in
     *                        milliseconds; must be non-negative
     * @param terminatedAt    the instant at which this case reached its terminal state;
     *                        must not be null
     * @throws IllegalArgumentException if {@code caseID} or {@code specificationID} is
     *                                  null or blank, or {@code durationMs} is negative
     * @throws NullPointerException     if {@code terminatedAt} is null
     */
    protected CaseOutcome(String caseID, String specificationID,
                          long durationMs, Instant terminatedAt) {
        if (caseID == null || caseID.isBlank()) {
            throw new IllegalArgumentException("caseID must not be null or blank");
        }
        if (specificationID == null || specificationID.isBlank()) {
            throw new IllegalArgumentException("specificationID must not be null or blank");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException(
                    "durationMs must be non-negative, got: " + durationMs);
        }
        this.caseID = caseID;
        this.specificationID = specificationID;
        this.durationMs = durationMs;
        this.terminatedAt = Objects.requireNonNull(terminatedAt, "terminatedAt must not be null");
    }

    /**
     * Returns the unique case instance identifier.
     *
     * @return case identifier; never null or blank
     */
    public final String caseID() {
        return caseID;
    }

    /**
     * Returns the identifier of the workflow specification from which this case was launched.
     *
     * @return specification identifier; never null or blank
     */
    public final String specificationID() {
        return specificationID;
    }

    /**
     * Returns the elapsed time from case launch to terminal state in milliseconds.
     *
     * @return duration in milliseconds; always non-negative
     */
    public final long durationMs() {
        return durationMs;
    }

    /**
     * Returns the instant at which this case reached its terminal state.
     *
     * @return termination timestamp; never null
     */
    public final Instant terminatedAt() {
        return terminatedAt;
    }

    /**
     * Returns a short status code identifying the terminal state
     * (e.g., "COMPLETED", "FAILED", "CANCELLED").
     *
     * @return non-null, non-blank status code
     */
    public abstract String statusCode();

    /**
     * Returns a human-readable description of how this case terminated.
     *
     * @return non-null message string
     */
    public abstract String summary();

    /**
     * Returns {@code true} if this case reached its output condition successfully.
     *
     * @return {@code true} for {@link CaseCompleted}; {@code false} otherwise
     */
    public final boolean isCompleted() {
        return this instanceof CaseCompleted;
    }

    /**
     * Returns {@code true} if this case aborted due to an error.
     *
     * @return {@code true} for {@link CaseFailed}; {@code false} otherwise
     */
    public final boolean isFailed() {
        return this instanceof CaseFailed;
    }

    /**
     * Returns {@code true} if this case was explicitly cancelled.
     *
     * @return {@code true} for {@link CaseCancelled}; {@code false} otherwise
     */
    public final boolean isCancelled() {
        return this instanceof CaseCancelled;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[caseID=" + caseID +
                ", specificationID=" + specificationID +
                ", durationMs=" + durationMs +
                ", terminatedAt=" + terminatedAt +
                ", status=" + statusCode() +
                "]";
    }
}
