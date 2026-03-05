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
 * An {@link EventResult} indicating that an engine event was structurally valid but
 * refused because one or more engine preconditions were not satisfied.
 *
 * <p>Rejection differs from invalidation: a rejected event is well-formed and references
 * known entities, but the engine's current state prevents it from being applied. Common
 * rejection scenarios include:</p>
 * <ul>
 *   <li>Attempting to complete a work item that is not in the {@code Executing} state</li>
 *   <li>Launching a case from a specification that is not currently loaded</li>
 *   <li>Checking out a work item already held by another participant</li>
 *   <li>Exceeding a rate limit or concurrency ceiling enforced by the engine</li>
 * </ul>
 *
 * <p>The rejection reason pinpoints the failed precondition. The engine does not modify
 * any state as a result of a rejected event — it is safe to retry once the precondition
 * is satisfied.</p>
 *
 * <p>Usage in exhaustive pattern matching:</p>
 * <pre>{@code
 * if (result instanceof EventRejected r) {
 *     response.setStatus(409);
 *     response.setBody(Map.of("rejectionReason", r.rejectionReason(),
 *                              "retryable", r.isRetryable()));
 * }
 * }</pre>
 */
public final class EventRejected extends EventResult {

    private final String rejectionReason;
    private final boolean retryable;

    /**
     * Constructs a rejected event result.
     *
     * @param eventID         the unique identifier for the event submission
     * @param eventType       the type of engine event (e.g., "COMPLETE_ITEM")
     * @param processedAt     the instant the engine evaluated and rejected the event
     * @param rejectionReason a concise, human-readable description of the failed
     *                        precondition; must not be null or blank
     * @param retryable       {@code true} if the event may succeed after the precondition
     *                        is re-evaluated (e.g., a concurrent modification conflict that
     *                        may resolve); {@code false} if the rejection is definitive
     *                        (e.g., specification not loaded)
     * @throws IllegalArgumentException if {@code eventID}, {@code eventType}, or
     *                                  {@code rejectionReason} is null or blank
     * @throws NullPointerException     if {@code processedAt} is null
     */
    public EventRejected(String eventID, String eventType,
                         Instant processedAt, String rejectionReason, boolean retryable) {
        super(eventID, eventType, processedAt);
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason must not be null or blank");
        }
        this.rejectionReason = rejectionReason;
        this.retryable = retryable;
    }

    /**
     * Returns the human-readable description of the failed precondition.
     *
     * @return rejection reason; never null or blank
     */
    public String rejectionReason() {
        return rejectionReason;
    }

    /**
     * Returns {@code true} if this rejection may be transient and the caller may retry
     * the event after the precondition is re-evaluated.
     *
     * <p>A retryable rejection is analogous to HTTP 409 Conflict — the same event may
     * succeed later. A non-retryable rejection is analogous to HTTP 400 Bad Request in
     * terms of state: the precondition will not change without a different operation
     * (e.g., loading the specification before launching a case).</p>
     *
     * @return {@code true} if retry may succeed; {@code false} if rejection is definitive
     */
    public boolean isRetryable() {
        return retryable;
    }

    @Override
    public String statusCode() {
        return "REJECTED";
    }

    @Override
    public String detail() {
        return "Event " + eventID() + " [" + eventType() + "] rejected" +
                (retryable ? " (retryable)" : " (definitive)") +
                ": " + rejectionReason;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EventRejected other)) return false;
        return eventID().equals(other.eventID())
                && eventType().equals(other.eventType())
                && processedAt().equals(other.processedAt())
                && rejectionReason.equals(other.rejectionReason)
                && retryable == other.retryable;
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventID(), eventType(), processedAt(), rejectionReason, retryable);
    }

    @Override
    public String toString() {
        return "EventRejected[eventID=" + eventID() +
                ", eventType=" + eventType() +
                ", processedAt=" + processedAt() +
                ", rejectionReason=" + rejectionReason +
                ", retryable=" + retryable +
                "]";
    }
}
