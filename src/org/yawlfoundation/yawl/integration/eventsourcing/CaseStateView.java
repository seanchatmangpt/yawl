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

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable materialized view of a workflow case's state, reconstructed by event replay.
 *
 * <p>This is a pure value object. Each "with*" method returns a new instance with the
 * specified field updated; the original instance is unmodified. This immutable design
 * makes the replayer thread-safe and snapshot-compatible.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class CaseStateView {

    /**
     * Lifecycle status of a workflow case.
     */
    public enum CaseStatus {
        /** Case has not yet received its first event. */
        UNKNOWN,
        /** Case is actively executing. */
        RUNNING,
        /** Case execution is paused. */
        SUSPENDED,
        /** Case completed normally. */
        COMPLETED,
        /** Case was cancelled before completion. */
        CANCELLED
    }

    /**
     * Status of an individual work item within the case.
     */
    public record WorkItemState(String workItemId, String status, Instant stateAt) {}

    private final String                   caseId;
    private final String                   specId;
    private final CaseStatus               status;
    private final Instant                  lastEventAt;
    private final Map<String, WorkItemState> activeWorkItems;
    private final Map<String, String>       payload;

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Create an empty (UNKNOWN status) case state view for the given case ID.
     * Used as the starting point for full replay.
     *
     * @param caseId case identifier
     * @return empty case state view
     */
    public static CaseStateView empty(String caseId) {
        return new CaseStateView(caseId, null, CaseStatus.UNKNOWN,
                                 null, Map.of(), Map.of());
    }

    /**
     * Create a case state view pre-populated from a snapshot.
     * Used as the starting point for snapshot + delta replay.
     *
     * @param snapshot the snapshot to restore from
     * @return case state view initialized from snapshot data
     */
    public static CaseStateView fromSnapshot(CaseSnapshot snapshot) {
        return new CaseStateView(
                snapshot.getCaseId(),
                snapshot.getSpecId(),
                CaseStatus.valueOf(snapshot.getStatus()),
                snapshot.getSnapshotAt(),
                snapshot.getActiveWorkItems(),
                snapshot.getPayload()
        );
    }

    private CaseStateView(String caseId, String specId, CaseStatus status,
                          Instant lastEventAt,
                          Map<String, WorkItemState> activeWorkItems,
                          Map<String, String> payload) {
        this.caseId          = Objects.requireNonNull(caseId, "caseId");
        this.specId          = specId;
        this.status          = Objects.requireNonNull(status, "status");
        this.lastEventAt     = lastEventAt;
        this.activeWorkItems = Collections.unmodifiableMap(
                                   activeWorkItems != null ? activeWorkItems : Map.of());
        this.payload         = Collections.unmodifiableMap(
                                   payload != null ? payload : Map.of());
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Case identifier. */
    public String getCaseId()           { return caseId; }

    /** Specification ID. May be null before CASE_STARTED event. */
    public String getSpecId()           { return specId; }

    /** Current case lifecycle status. */
    public CaseStatus getStatus()       { return status; }

    /** Timestamp of the most recent event applied to this view. */
    public Instant getLastEventAt()     { return lastEventAt; }

    /** Currently active (non-completed) work items and their states. */
    public Map<String, WorkItemState> getActiveWorkItems() { return activeWorkItems; }

    /** Accumulated payload data from all events. */
    public Map<String, String> getPayload() { return payload; }

    // -------------------------------------------------------------------------
    // Immutable update methods (return new instances)
    // -------------------------------------------------------------------------

    /** Return a new view with the given status. */
    public CaseStateView withStatus(CaseStatus newStatus) {
        return new CaseStateView(caseId, specId, newStatus, lastEventAt, activeWorkItems, payload);
    }

    /** Return a new view with the given spec ID. */
    public CaseStateView withSpecId(String newSpecId) {
        return new CaseStateView(caseId, newSpecId, status, lastEventAt, activeWorkItems, payload);
    }

    /** Return a new view with the given last-event timestamp. */
    public CaseStateView withLastEventAt(Instant newLastEventAt) {
        return new CaseStateView(caseId, specId, status, newLastEventAt, activeWorkItems, payload);
    }

    /** Return a new view with the given work item added or updated to the given status. */
    public CaseStateView withActiveWorkItem(String workItemId, String itemStatus, Instant stateAt) {
        if (workItemId == null || workItemId.isBlank()) {
            return this; // no-op for events without work item ID
        }
        Map<String, WorkItemState> updated = new LinkedHashMap<>(activeWorkItems);
        updated.put(workItemId, new WorkItemState(workItemId, itemStatus, stateAt));
        return new CaseStateView(caseId, specId, status, stateAt, updated, payload);
    }

    /** Return a new view with the given work item removed (completed or cancelled). */
    public CaseStateView withoutWorkItem(String workItemId) {
        if (workItemId == null || !activeWorkItems.containsKey(workItemId)) {
            return this; // no-op
        }
        Map<String, WorkItemState> updated = new LinkedHashMap<>(activeWorkItems);
        updated.remove(workItemId);
        return new CaseStateView(caseId, specId, status, lastEventAt, updated, payload);
    }

    /** Return a new view with an additional payload entry. */
    public CaseStateView withPayloadEntry(String key, String value) {
        Map<String, String> updated = new LinkedHashMap<>(payload);
        updated.put(key, value);
        return new CaseStateView(caseId, specId, status, lastEventAt, activeWorkItems, updated);
    }

    @Override
    public String toString() {
        return "CaseStateView{caseId='" + caseId
             + "', specId='" + specId
             + "', status=" + status
             + ", activeWorkItems=" + activeWorkItems.size()
             + ", lastEventAt=" + lastEventAt + '}';
    }
}
