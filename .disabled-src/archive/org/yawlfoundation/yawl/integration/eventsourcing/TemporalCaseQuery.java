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

package org.yawlfoundation.yawl.integration.eventsourcing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Temporal query service for workflow case state history.
 *
 * <p>Answers the fundamental event-sourcing temporal query:
 * <em>"What was the state of case X at time T?"</em>
 *
 * <p>This service combines event store reads with snapshot-acceleration to efficiently
 * answer point-in-time queries across the full case history.
 *
 * <h2>Query Types</h2>
 * <ul>
 *   <li>{@link #stateAt(String, Instant)} - reconstructed case state at a specific instant</li>
 *   <li>{@link #eventsBetween(String, Instant, Instant)} - event range query for a time window</li>
 *   <li>{@link #workItemStatusAt(String, String, Instant)} - status of a specific work item at time T</li>
 *   <li>{@link #durationInStatus(String, CaseStateView.CaseStatus)} - total time spent in a given status</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class TemporalCaseQuery {

    private static final Logger log = LoggerFactory.getLogger(TemporalCaseQuery.class);

    private final WorkflowEventStore eventStore;
    private final EventReplayer      replayer;

    /**
     * Construct with event store and replayer.
     *
     * @param eventStore event store for raw event access (must not be null)
     * @param replayer   event replayer for state reconstruction (must not be null)
     */
    public TemporalCaseQuery(WorkflowEventStore eventStore, EventReplayer replayer) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.replayer   = Objects.requireNonNull(replayer, "replayer");
    }

    /**
     * Returns the reconstructed state of a case at the given point in time.
     *
     * <p>Events with timestamp strictly after {@code asOf} are excluded.
     * If {@code asOf} is before the case's CASE_STARTED event, the returned
     * state has {@link CaseStateView.CaseStatus#UNKNOWN} status.
     *
     * @param caseId case identifier (must not be blank)
     * @param asOf   the point in time to query (must not be null)
     * @return case state as it appeared at {@code asOf}
     * @throws TemporalQueryException if the event store cannot be read
     */
    public CaseStateView stateAt(String caseId, Instant asOf) throws TemporalQueryException {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        Objects.requireNonNull(asOf, "asOf must not be null");
        try {
            log.debug("Temporal query: stateAt(case={}, asOf={})", caseId, asOf);
            return replayer.replayAsOf(caseId, asOf);
        } catch (EventReplayer.ReplayException e) {
            throw new TemporalQueryException("stateAt failed for case " + caseId
                                             + " asOf " + asOf, e);
        }
    }

    /**
     * Returns all events for a case within the given time range (both endpoints inclusive).
     *
     * @param caseId case identifier (must not be blank)
     * @param from   start of the time range (must not be null)
     * @param to     end of the time range (must not be null, must be after or equal to from)
     * @return events within [from, to] in sequence order
     * @throws TemporalQueryException   if the event store cannot be read
     * @throws IllegalArgumentException if from is after to
     */
    public List<WorkflowEvent> eventsBetween(String caseId, Instant from, Instant to)
            throws TemporalQueryException {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "from (" + from + ") must not be after to (" + to + ")");
        }

        try {
            // Load all events up to 'to', then filter by >= from
            List<WorkflowEvent> eventsUpTo = eventStore.loadEventsAsOf(caseId, to);
            return eventsUpTo.stream()
                    .filter(e -> !e.getTimestamp().isBefore(from))
                    .toList();
        } catch (WorkflowEventStore.EventStoreException e) {
            throw new TemporalQueryException("eventsBetween failed for case " + caseId, e);
        }
    }

    /**
     * Returns the status of a specific work item at a given point in time.
     *
     * @param caseId     case identifier (must not be blank)
     * @param workItemId work item identifier (must not be blank)
     * @param asOf       the point in time to query (must not be null)
     * @return work item status string, or {@code "NOT_PRESENT"} if the item did not
     *         exist at that time (either before it was enabled, or after it completed)
     * @throws TemporalQueryException if the event store cannot be read
     */
    public String workItemStatusAt(String caseId, String workItemId, Instant asOf)
            throws TemporalQueryException {
        if (workItemId == null || workItemId.isBlank()) {
            throw new IllegalArgumentException("workItemId must not be blank");
        }
        CaseStateView state = stateAt(caseId, asOf);
        CaseStateView.WorkItemState itemState = state.getActiveWorkItems().get(workItemId);
        return itemState != null ? itemState.status() : "NOT_PRESENT";
    }

    /**
     * Calculates the total duration a case spent in the given status across its entire history.
     *
     * <p>For example, to find how long a case spent SUSPENDED:
     * <pre>
     * Duration suspended = query.durationInStatus(caseId, CaseStatus.SUSPENDED);
     * </pre>
     *
     * @param caseId       case identifier (must not be blank)
     * @param targetStatus the status to measure time spent in
     * @return total duration spent in the target status; {@link Duration#ZERO} if never in that status
     * @throws TemporalQueryException if the event store cannot be read
     */
    public Duration durationInStatus(String caseId, CaseStateView.CaseStatus targetStatus)
            throws TemporalQueryException {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        Objects.requireNonNull(targetStatus, "targetStatus must not be null");

        try {
            List<WorkflowEvent> events = eventStore.loadEvents(caseId);
            if (events.isEmpty()) {
                return Duration.ZERO;
            }

            Duration total        = Duration.ZERO;
            CaseStateView current = CaseStateView.empty(caseId);
            Instant enteredAt     = null;

            for (WorkflowEvent event : events) {
                CaseStateView before = current;
                // Apply each event via the replayer's internal logic by checking transitions
                current = applyStatusTransition(current, event);

                if (before.getStatus() != targetStatus && current.getStatus() == targetStatus) {
                    // Just entered target status
                    enteredAt = event.getTimestamp();
                } else if (before.getStatus() == targetStatus && current.getStatus() != targetStatus) {
                    // Just left target status
                    if (enteredAt != null) {
                        total = total.plus(Duration.between(enteredAt, event.getTimestamp()));
                        enteredAt = null;
                    }
                }
            }

            // If still in target status at end of event stream, measure to last event
            if (enteredAt != null && !events.isEmpty()) {
                Instant lastEvent = events.get(events.size() - 1).getTimestamp();
                total = total.plus(Duration.between(enteredAt, lastEvent));
            }

            return total;

        } catch (WorkflowEventStore.EventStoreException e) {
            throw new TemporalQueryException("durationInStatus failed for case " + caseId, e);
        }
    }

    private static CaseStateView applyStatusTransition(CaseStateView state, WorkflowEvent event) {
        return switch (event.getEventType()) {
            case CASE_STARTED   -> state.withStatus(CaseStateView.CaseStatus.RUNNING);
            case CASE_COMPLETED -> state.withStatus(CaseStateView.CaseStatus.COMPLETED);
            case CASE_CANCELLED -> state.withStatus(CaseStateView.CaseStatus.CANCELLED);
            case CASE_SUSPENDED -> state.withStatus(CaseStateView.CaseStatus.SUSPENDED);
            case CASE_RESUMED   -> state.withStatus(CaseStateView.CaseStatus.RUNNING);
            default             -> state;
        };
    }

    /**
     * Thrown when a temporal query cannot be executed.
     */
    public static final class TemporalQueryException extends Exception {

        /**
         * Construct with message and cause.
         *
         * @param message error description
         * @param cause   underlying exception
         */
        public TemporalQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
