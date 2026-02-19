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

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reconstructs workflow case state by replaying stored events.
 *
 * <p>The replayer applies events in sequence order to build a {@link CaseStateView}
 * representing the state of a case at a given point in time. Three replay modes:
 *
 * <ul>
 *   <li><b>Full replay</b> - {@link #replay(String)} - rebuild state from event 0</li>
 *   <li><b>Snapshot + delta</b> - {@link #replayFrom(CaseSnapshot, List)} - apply
 *       only events after the snapshot's sequence number to avoid full log scan</li>
 *   <li><b>Temporal replay</b> - {@link #replayAsOf(String, Instant)} - reconstruct
 *       state at a specific point in time for audit/debugging</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class EventReplayer {

    private static final Logger log = LoggerFactory.getLogger(EventReplayer.class);

    private final WorkflowEventStore eventStore;

    /**
     * Construct replayer backed by the given event store.
     *
     * @param eventStore the event store to read events from (must not be null)
     */
    public EventReplayer(WorkflowEventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    }

    /**
     * Replay all events for a case to reconstruct its current state.
     *
     * @param caseId case identifier (must not be blank)
     * @return reconstructed case state view
     * @throws ReplayException if the event store read fails or events are malformed
     */
    public CaseStateView replay(String caseId) throws ReplayException {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        try {
            List<WorkflowEvent> events = eventStore.loadEvents(caseId);
            log.debug("Replaying {} events for case {}", events.size(), caseId);
            return applyEvents(CaseStateView.empty(caseId), events);
        } catch (WorkflowEventStore.EventStoreException e) {
            throw new ReplayException("Failed to load events for case " + caseId, e);
        }
    }

    /**
     * Reconstruct case state from a snapshot plus subsequent delta events.
     * More efficient than full replay for cases with many historical events.
     *
     * @param snapshot    the case snapshot to start from (must not be null)
     * @param deltaEvents events that occurred after the snapshot (in sequence order)
     * @return reconstructed case state view
     * @throws ReplayException if any event cannot be applied to the snapshot state
     */
    public CaseStateView replayFrom(CaseSnapshot snapshot, List<WorkflowEvent> deltaEvents)
            throws ReplayException {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(deltaEvents, "deltaEvents must not be null");

        log.debug("Replaying {} delta events for case {} from snapshot at seq {}",
                  deltaEvents.size(), snapshot.getCaseId(), snapshot.getSequenceNumber());
        CaseStateView baseView = CaseStateView.fromSnapshot(snapshot);
        return applyEvents(baseView, deltaEvents);
    }

    /**
     * Reconstruct case state as it was at a specific point in time.
     * Events with timestamp after {@code asOf} are excluded.
     *
     * @param caseId case identifier (must not be blank)
     * @param asOf   point in time (inclusive); events after this time are excluded
     * @return case state as it appeared at the given instant
     * @throws ReplayException if the event store read fails
     */
    public CaseStateView replayAsOf(String caseId, Instant asOf) throws ReplayException {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        Objects.requireNonNull(asOf, "asOf must not be null");
        try {
            List<WorkflowEvent> events = eventStore.loadEventsAsOf(caseId, asOf);
            log.debug("Temporal replay: {} events for case {} asOf {}", events.size(), caseId, asOf);
            return applyEvents(CaseStateView.empty(caseId), events);
        } catch (WorkflowEventStore.EventStoreException e) {
            throw new ReplayException("Failed temporal replay for case " + caseId + " asOf " + asOf, e);
        }
    }

    // -------------------------------------------------------------------------
    // Event application engine
    // -------------------------------------------------------------------------

    private CaseStateView applyEvents(CaseStateView state, List<WorkflowEvent> events)
            throws ReplayException {
        for (WorkflowEvent event : events) {
            state = applyEvent(state, event);
        }
        return state;
    }

    private CaseStateView applyEvent(CaseStateView state, WorkflowEvent event)
            throws ReplayException {
        return switch (event.getEventType()) {
            case CASE_STARTED -> state.withStatus(CaseStateView.CaseStatus.RUNNING)
                                      .withSpecId(event.getSpecId())
                                      .withLastEventAt(event.getTimestamp())
                                      .withPayloadEntry("startedAt", event.getTimestamp().toString());
            case CASE_COMPLETED -> state.withStatus(CaseStateView.CaseStatus.COMPLETED)
                                        .withLastEventAt(event.getTimestamp())
                                        .withPayloadEntry("completedAt", event.getTimestamp().toString());
            case CASE_CANCELLED -> state.withStatus(CaseStateView.CaseStatus.CANCELLED)
                                        .withLastEventAt(event.getTimestamp())
                                        .withPayloadEntry("cancelledAt", event.getTimestamp().toString());
            case CASE_SUSPENDED -> state.withStatus(CaseStateView.CaseStatus.SUSPENDED)
                                        .withLastEventAt(event.getTimestamp());
            case CASE_RESUMED   -> state.withStatus(CaseStateView.CaseStatus.RUNNING)
                                        .withLastEventAt(event.getTimestamp());
            case WORKITEM_ENABLED    -> state.withActiveWorkItem(event.getWorkItemId(),
                                                                 "ENABLED", event.getTimestamp());
            case WORKITEM_STARTED    -> state.withActiveWorkItem(event.getWorkItemId(),
                                                                 "STARTED", event.getTimestamp());
            case WORKITEM_COMPLETED  -> state.withoutWorkItem(event.getWorkItemId())
                                             .withLastEventAt(event.getTimestamp());
            case WORKITEM_CANCELLED  -> state.withoutWorkItem(event.getWorkItemId())
                                             .withLastEventAt(event.getTimestamp());
            case WORKITEM_FAILED     -> state.withActiveWorkItem(event.getWorkItemId(),
                                                                 "FAILED", event.getTimestamp());
            case WORKITEM_SUSPENDED  -> state.withActiveWorkItem(event.getWorkItemId(),
                                                                 "SUSPENDED", event.getTimestamp());
            case SPEC_LOADED, SPEC_UNLOADED -> state; // spec events don't affect case state
            // ADR-025 coordination events - don't modify case state directly
            case CONFLICT_DETECTED, CONFLICT_RESOLVED,
                 HANDOFF_INITIATED, HANDOFF_COMPLETED,
                 AGENT_DECISION_MADE -> state.withLastEventAt(event.getTimestamp());
        };
    }

    /**
     * Thrown when event replay fails.
     */
    public static final class ReplayException extends Exception {

        /**
         * Construct with message and cause.
         *
         * @param message error description
         * @param cause   underlying exception
         */
        public ReplayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
