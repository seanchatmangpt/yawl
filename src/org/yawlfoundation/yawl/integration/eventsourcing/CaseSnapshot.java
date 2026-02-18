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
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of workflow case state at a specific sequence checkpoint.
 *
 * <p>Snapshots bound the cost of event replay by providing a pre-materialized
 * starting point. Instead of replaying all events from the beginning, the replayer
 * starts from the snapshot and applies only the delta events that occurred after
 * the snapshot's {@link #getSequenceNumber()}.
 *
 * <h2>Snapshot Policy</h2>
 * <p>Recommended: take a snapshot every 100 events per case, or whenever a case
 * reaches a natural checkpoint (e.g., all tasks in a subnet complete). The
 * {@link SnapshotRepository} handles persistence and retrieval.
 *
 * <h2>JDBC Schema</h2>
 * <pre>
 * CREATE TABLE case_snapshots (
 *   case_id      VARCHAR(255) NOT NULL,
 *   seq_num      BIGINT       NOT NULL,
 *   snapshot_ts  TIMESTAMP(6) NOT NULL,
 *   spec_id      VARCHAR(255) NOT NULL,
 *   status       VARCHAR(32)  NOT NULL,
 *   state_json   TEXT         NOT NULL,   -- JSON of activeWorkItems map
 *   payload_json TEXT         NOT NULL,   -- JSON of payload map
 *   PRIMARY KEY (case_id, seq_num)
 * );
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class CaseSnapshot {

    private final String                          caseId;
    private final String                          specId;
    private final long                            sequenceNumber;
    private final Instant                         snapshotAt;
    private final String                          status;
    private final Map<String, CaseStateView.WorkItemState> activeWorkItems;
    private final Map<String, String>             payload;

    /**
     * Construct a case snapshot.
     *
     * @param caseId          case identifier
     * @param specId          specification identifier
     * @param sequenceNumber  last event sequence number included in this snapshot
     * @param snapshotAt      when this snapshot was taken
     * @param status          case status at snapshot time (CaseStatus name)
     * @param activeWorkItems work items active at snapshot time
     * @param payload         accumulated payload data at snapshot time
     */
    public CaseSnapshot(String caseId, String specId, long sequenceNumber,
                        Instant snapshotAt, String status,
                        Map<String, CaseStateView.WorkItemState> activeWorkItems,
                        Map<String, String> payload) {
        this.caseId          = Objects.requireNonNull(caseId, "caseId");
        this.specId          = Objects.requireNonNull(specId, "specId");
        this.sequenceNumber  = sequenceNumber;
        this.snapshotAt      = Objects.requireNonNull(snapshotAt, "snapshotAt");
        this.status          = Objects.requireNonNull(status, "status");
        this.activeWorkItems = Collections.unmodifiableMap(
                                   activeWorkItems != null ? activeWorkItems : Map.of());
        this.payload         = Collections.unmodifiableMap(payload != null ? payload : Map.of());
    }

    /**
     * Create a snapshot from a materialized case state view.
     *
     * @param stateView     the materialized state to snapshot
     * @param sequenceNumber the sequence number of the last event applied to stateView
     * @return snapshot representing the given state
     */
    public static CaseSnapshot from(CaseStateView stateView, long sequenceNumber) {
        Objects.requireNonNull(stateView, "stateView must not be null");
        String specId = stateView.getSpecId() != null ? stateView.getSpecId() : "";
        return new CaseSnapshot(
                stateView.getCaseId(),
                specId,
                sequenceNumber,
                Instant.now(),
                stateView.getStatus().name(),
                stateView.getActiveWorkItems(),
                stateView.getPayload()
        );
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Case identifier this snapshot belongs to. */
    public String getCaseId()          { return caseId; }

    /** Specification identifier. */
    public String getSpecId()          { return specId; }

    /** Sequence number of the last event included in this snapshot. */
    public long getSequenceNumber()    { return sequenceNumber; }

    /** Timestamp when this snapshot was persisted. */
    public Instant getSnapshotAt()     { return snapshotAt; }

    /** Case status name (value of {@link CaseStateView.CaseStatus}). */
    public String getStatus()          { return status; }

    /** Active work items at snapshot time. */
    public Map<String, CaseStateView.WorkItemState> getActiveWorkItems() { return activeWorkItems; }

    /** Accumulated payload data at snapshot time. */
    public Map<String, String> getPayload() { return payload; }

    @Override
    public String toString() {
        return "CaseSnapshot{caseId='" + caseId
             + "', seq=" + sequenceNumber
             + "', status=" + status
             + ", snapshotAt=" + snapshotAt + '}';
    }
}
