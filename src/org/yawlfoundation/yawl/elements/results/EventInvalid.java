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
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An {@link EventResult} indicating that an engine event was malformed, referenced an
 * unknown entity, or violated a schema or type constraint and was therefore discarded
 * without modifying any engine state.
 *
 * <p>Invalidity is a structural problem with the event itself, not a state problem with
 * the engine. It is the engine-layer equivalent of HTTP 400 Bad Request. Common
 * invalidity scenarios include:</p>
 * <ul>
 *   <li>A required field is null, blank, or missing entirely</li>
 *   <li>A referenced case ID, work item ID, or specification ID does not exist in any
 *       known engine registry</li>
 *   <li>Data payload XML does not conform to the specification's variable schema</li>
 *   <li>An enum value or status code in the event is not recognised</li>
 *   <li>A numeric or temporal field is out of the permitted range</li>
 * </ul>
 *
 * <p>Multiple validation errors may be present for a single event submission. All
 * detected errors are collected and returned together in {@link #validationErrors()}
 * to allow callers to report all problems at once rather than fail-fast.</p>
 *
 * <p>Usage in exhaustive pattern matching:</p>
 * <pre>{@code
 * if (result instanceof EventInvalid i) {
 *     response.setStatus(400);
 *     response.setBody(Map.of("errors", i.validationErrors(),
 *                              "eventID", i.eventID()));
 * }
 * }</pre>
 */
public final class EventInvalid extends EventResult {

    private final List<String> validationErrors;

    /**
     * Constructs an invalid event result with a list of all detected validation errors.
     *
     * @param eventID          the unique identifier for the event submission
     * @param eventType        the type of engine event, or {@code "UNKNOWN"} if the type
     *                         itself could not be parsed
     * @param processedAt      the instant the engine detected the invalidity
     * @param validationErrors an ordered list of human-readable validation error messages;
     *                         must not be null and must contain at least one entry
     * @throws IllegalArgumentException if {@code eventID} or {@code eventType} is null or
     *                                  blank, or {@code validationErrors} is null or empty
     * @throws NullPointerException     if {@code processedAt} is null
     */
    public EventInvalid(String eventID, String eventType,
                        Instant processedAt, List<String> validationErrors) {
        super(eventID, eventType, processedAt);
        Objects.requireNonNull(validationErrors, "validationErrors must not be null");
        if (validationErrors.isEmpty()) {
            throw new IllegalArgumentException(
                    "validationErrors must contain at least one entry");
        }
        this.validationErrors = Collections.unmodifiableList(List.copyOf(validationErrors));
    }

    /**
     * Constructs an invalid event result with a single validation error.
     *
     * @param eventID        the unique identifier for the event submission
     * @param eventType      the type of engine event, or {@code "UNKNOWN"} if the type
     *                       could not be parsed
     * @param processedAt    the instant the engine detected the invalidity
     * @param validationError a single human-readable validation error message; must not
     *                        be null or blank
     * @throws IllegalArgumentException if {@code eventID}, {@code eventType}, or
     *                                  {@code validationError} is null or blank
     * @throws NullPointerException     if {@code processedAt} is null
     */
    public EventInvalid(String eventID, String eventType,
                        Instant processedAt, String validationError) {
        this(eventID, eventType, processedAt,
                validationError == null || validationError.isBlank()
                        ? List.of()
                        : List.of(validationError));
    }

    /**
     * Returns an unmodifiable, ordered list of validation errors detected in the event.
     *
     * <p>Each entry describes one specific violation. Callers should report all entries
     * to the event submitter so that they can correct all problems in a single round-trip.</p>
     *
     * @return immutable list of validation error strings; never null, never empty
     */
    public List<String> validationErrors() {
        return validationErrors;
    }

    /**
     * Returns the number of validation errors present in this result.
     *
     * @return error count; always at least 1
     */
    public int errorCount() {
        return validationErrors.size();
    }

    @Override
    public String statusCode() {
        return "INVALID";
    }

    @Override
    public String detail() {
        return "Event " + eventID() + " [" + eventType() + "] invalid (" +
                validationErrors.size() + " error" + (validationErrors.size() == 1 ? "" : "s") +
                "): " + String.join("; ", validationErrors);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EventInvalid other)) return false;
        return eventID().equals(other.eventID())
                && eventType().equals(other.eventType())
                && processedAt().equals(other.processedAt())
                && validationErrors.equals(other.validationErrors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventID(), eventType(), processedAt(), validationErrors);
    }

    @Override
    public String toString() {
        return "EventInvalid[eventID=" + eventID() +
                ", eventType=" + eventType() +
                ", processedAt=" + processedAt() +
                ", errorCount=" + validationErrors.size() +
                ", validationErrors=" + validationErrors +
                "]";
    }
}
