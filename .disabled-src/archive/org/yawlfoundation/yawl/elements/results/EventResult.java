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
 * Sealed hierarchy representing the processing result of an engine event submitted to
 * the YAWL engine via Interface A (administration) or Interface B (client services).
 *
 * <p>Every event submitted to the engine receives exactly one of three responses:</p>
 * <ul>
 *   <li>{@link EventAccepted} — the event was valid, preconditions were met, and the
 *       engine has committed the state change it represents</li>
 *   <li>{@link EventRejected} — the event was structurally valid but was refused because
 *       engine preconditions were not met (e.g., case not in correct state, insufficient
 *       permissions, resource unavailable)</li>
 *   <li>{@link EventInvalid} — the event was malformed, referenced an unknown entity, or
 *       violated a schema/type constraint; it was discarded without modifying state</li>
 * </ul>
 *
 * <p>The sealed type enables exhaustive handling at Interface A/B dispatch points:</p>
 * <pre>{@code
 * EventResult result = engine.handleEvent(event);
 * switch (result) {
 *     case EventAccepted  a -> respondOk(a.correlationID());
 *     case EventRejected  r -> respondConflict(r.rejectionReason());
 *     case EventInvalid   i -> respondBadRequest(i.validationErrors());
 * }
 * }</pre>
 *
 * <p>All subclasses are immutable. {@code eventID}, {@code eventType}, and
 * {@code processedAt} are common to all outcomes and available on this sealed parent.</p>
 *
 * @see EventAccepted
 * @see EventRejected
 * @see EventInvalid
 */
public abstract sealed class EventResult
        permits EventAccepted, EventRejected, EventInvalid {

    private final String eventID;
    private final String eventType;
    private final Instant processedAt;

    /**
     * Constructs an event result with fields common to all processing outcomes.
     *
     * @param eventID     a unique identifier for this event submission; must not be
     *                    null or blank
     * @param eventType   the type of engine event (e.g., "LAUNCH_CASE", "COMPLETE_ITEM",
     *                    "CANCEL_CASE"); must not be null or blank
     * @param processedAt the instant at which the engine finished processing this event;
     *                    must not be null
     * @throws IllegalArgumentException if {@code eventID} or {@code eventType} is null
     *                                  or blank
     * @throws NullPointerException     if {@code processedAt} is null
     */
    protected EventResult(String eventID, String eventType, Instant processedAt) {
        if (eventID == null || eventID.isBlank()) {
            throw new IllegalArgumentException("eventID must not be null or blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be null or blank");
        }
        this.eventID = eventID;
        this.eventType = eventType;
        this.processedAt = Objects.requireNonNull(processedAt, "processedAt must not be null");
    }

    /**
     * Returns the unique identifier for this event submission.
     *
     * @return event identifier; never null or blank
     */
    public final String eventID() {
        return eventID;
    }

    /**
     * Returns the type of engine event that was submitted.
     *
     * @return event type string; never null or blank
     */
    public final String eventType() {
        return eventType;
    }

    /**
     * Returns the instant at which the engine finished processing this event.
     *
     * @return processing timestamp; never null
     */
    public final Instant processedAt() {
        return processedAt;
    }

    /**
     * Returns a short status code for this event processing result
     * (e.g., "ACCEPTED", "REJECTED", "INVALID").
     *
     * @return non-null, non-blank status code
     */
    public abstract String statusCode();

    /**
     * Returns a human-readable description of the processing result.
     *
     * @return non-null message string
     */
    public abstract String detail();

    /**
     * Returns {@code true} if the event was accepted and its state change committed.
     *
     * @return {@code true} for {@link EventAccepted}; {@code false} otherwise
     */
    public final boolean isAccepted() {
        return this instanceof EventAccepted;
    }

    /**
     * Returns {@code true} if the event was refused because preconditions were not met.
     *
     * @return {@code true} for {@link EventRejected}; {@code false} otherwise
     */
    public final boolean isRejected() {
        return this instanceof EventRejected;
    }

    /**
     * Returns {@code true} if the event was discarded as malformed or referencing
     * an unknown entity.
     *
     * @return {@code true} for {@link EventInvalid}; {@code false} otherwise
     */
    public final boolean isInvalid() {
        return this instanceof EventInvalid;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[eventID=" + eventID +
                ", eventType=" + eventType +
                ", processedAt=" + processedAt +
                ", status=" + statusCode() +
                "]";
    }
}
