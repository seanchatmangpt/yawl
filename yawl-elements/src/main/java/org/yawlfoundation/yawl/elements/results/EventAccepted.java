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
 * An {@link EventResult} indicating that an engine event was accepted and its
 * corresponding state change committed successfully.
 *
 * <p>Acceptance means the engine validated all preconditions, applied the state
 * transition, persisted the result (if persistence is enabled), and has assigned a
 * correlation identifier for tracking the downstream effect. The correlation ID is
 * the entity identifier most relevant to the accepted event — for example, a newly
 * created case ID for {@code LAUNCH_CASE}, or the updated work item ID for
 * {@code COMPLETE_ITEM}.</p>
 *
 * <p>Usage in exhaustive pattern matching:</p>
 * <pre>{@code
 * if (result instanceof EventAccepted a) {
 *     response.setStatus(200);
 *     response.setBody(Map.of("correlationID", a.correlationID(),
 *                              "eventID", a.eventID()));
 * }
 * }</pre>
 */
public final class EventAccepted extends EventResult {

    private final String correlationID;

    /**
     * Constructs an accepted event result.
     *
     * @param eventID       the unique identifier for the event submission
     * @param eventType     the type of engine event (e.g., "LAUNCH_CASE")
     * @param processedAt   the instant the engine committed the state change
     * @param correlationID the identifier of the entity most relevant to this accepted
     *                      event (e.g., the new case ID for a launch, the work item ID
     *                      for a completion); must not be null or blank
     * @throws IllegalArgumentException if {@code eventID}, {@code eventType}, or
     *                                  {@code correlationID} is null or blank
     * @throws NullPointerException     if {@code processedAt} is null
     */
    public EventAccepted(String eventID, String eventType,
                         Instant processedAt, String correlationID) {
        super(eventID, eventType, processedAt);
        if (correlationID == null || correlationID.isBlank()) {
            throw new IllegalArgumentException("correlationID must not be null or blank");
        }
        this.correlationID = correlationID;
    }

    /**
     * Returns the identifier of the entity most relevant to this accepted event.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>For {@code LAUNCH_CASE}: the newly assigned case identifier</li>
     *   <li>For {@code COMPLETE_ITEM}: the work item identifier that was completed</li>
     *   <li>For {@code CANCEL_CASE}: the case identifier that was cancelled</li>
     * </ul>
     *
     * @return correlation identifier; never null or blank
     */
    public String correlationID() {
        return correlationID;
    }

    @Override
    public String statusCode() {
        return "ACCEPTED";
    }

    @Override
    public String detail() {
        return "Event " + eventID() + " [" + eventType() + "] accepted; correlationID=" +
                correlationID;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EventAccepted other)) return false;
        return eventID().equals(other.eventID())
                && eventType().equals(other.eventType())
                && processedAt().equals(other.processedAt())
                && correlationID.equals(other.correlationID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventID(), eventType(), processedAt(), correlationID);
    }

    @Override
    public String toString() {
        return "EventAccepted[eventID=" + eventID() +
                ", eventType=" + eventType() +
                ", processedAt=" + processedAt() +
                ", correlationID=" + correlationID +
                "]";
    }
}
