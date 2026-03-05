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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * JDBC-backed repository for {@link CaseSnapshot} persistence.
 *
 * <p>Persists and retrieves case state snapshots to bound the cost of event replay.
 * Snapshots are keyed by {@code (case_id, seq_num)} allowing multiple checkpoints
 * per case. The most recent snapshot for a case is retrieved by
 * {@link #findLatest(String)}.
 *
 * <h2>Snapshot Strategy</h2>
 * <p>Take a snapshot after every N events (configurable via constructor argument).
 * The default threshold is 100 events per case. After taking a snapshot, replay only
 * loads events with seq_num > snapshot.seq_num via
 * {@link WorkflowEventStore#loadEventsSince(String, long)}.
 *
 * <h2>DDL</h2>
 * <pre>
 * CREATE TABLE case_snapshots (
 *   case_id          VARCHAR(255) NOT NULL,
 *   seq_num          BIGINT       NOT NULL,
 *   snapshot_ts      TIMESTAMP(6) NOT NULL,
 *   spec_id          VARCHAR(255) NOT NULL,
 *   status           VARCHAR(32)  NOT NULL,
 *   active_items_json TEXT        NOT NULL,
 *   payload_json     TEXT         NOT NULL,
 *   PRIMARY KEY (case_id, seq_num)
 * );
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class SnapshotRepository {

    private static final Logger log = LoggerFactory.getLogger(SnapshotRepository.class);

    private static final String INSERT_SNAPSHOT =
        "INSERT INTO case_snapshots "
      + "(case_id, seq_num, snapshot_ts, spec_id, status, active_items_json, payload_json) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_LATEST_SNAPSHOT =
        "SELECT case_id, seq_num, snapshot_ts, spec_id, status, active_items_json, payload_json "
      + "FROM case_snapshots WHERE case_id = ? ORDER BY seq_num DESC LIMIT 1";

    /** Default event count after which a new snapshot is recommended. */
    public static final int DEFAULT_SNAPSHOT_THRESHOLD = 100;

    private final DataSource   dataSource;
    private final ObjectMapper objectMapper;
    private final int          snapshotThreshold;

    /**
     * Construct with default snapshot threshold (100 events).
     *
     * @param dataSource JDBC data source with connection pool
     */
    public SnapshotRepository(DataSource dataSource) {
        this(dataSource, DEFAULT_SNAPSHOT_THRESHOLD);
    }

    /**
     * Construct with explicit snapshot threshold.
     *
     * @param dataSource        JDBC data source with connection pool
     * @param snapshotThreshold event count after which snapshots should be taken
     */
    public SnapshotRepository(DataSource dataSource, int snapshotThreshold) {
        this.dataSource        = Objects.requireNonNull(dataSource, "dataSource");
        this.snapshotThreshold = snapshotThreshold > 0 ? snapshotThreshold : DEFAULT_SNAPSHOT_THRESHOLD;
        this.objectMapper      = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Persist a case snapshot.
     *
     * @param snapshot the snapshot to save (must not be null)
     * @throws SnapshotException if persistence fails
     */
    public void save(CaseSnapshot snapshot) throws SnapshotException {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        try {
            String activeItemsJson = objectMapper.writeValueAsString(snapshot.getActiveWorkItems());
            String payloadJson     = objectMapper.writeValueAsString(snapshot.getPayload());

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(INSERT_SNAPSHOT)) {

                stmt.setString(1, snapshot.getCaseId());
                stmt.setLong(2, snapshot.getSequenceNumber());
                stmt.setObject(3, java.sql.Timestamp.from(snapshot.getSnapshotAt()));
                stmt.setString(4, snapshot.getSpecId());
                stmt.setString(5, snapshot.getStatus());
                stmt.setString(6, activeItemsJson);
                stmt.setString(7, payloadJson);
                stmt.executeUpdate();

                log.debug("Saved snapshot: case={}, seq={}", snapshot.getCaseId(),
                          snapshot.getSequenceNumber());
            }
        } catch (Exception e) {
            throw new SnapshotException("Failed to save snapshot for case " + snapshot.getCaseId(), e);
        }
    }

    /**
     * Find the most recent snapshot for a case.
     *
     * @param caseId case identifier (must not be blank)
     * @return the most recent snapshot, or empty if no snapshot exists
     * @throws SnapshotException if the read fails
     */
    public Optional<CaseSnapshot> findLatest(String caseId) throws SnapshotException {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_LATEST_SNAPSHOT)) {

            stmt.setString(1, caseId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(rs));
            }
        } catch (SnapshotException e) {
            throw e;
        } catch (SQLException e) {
            throw new SnapshotException("Failed to find latest snapshot for case " + caseId, e);
        }
    }

    /**
     * Returns whether a new snapshot should be taken based on the current event count.
     * A snapshot is recommended when the number of events since the last snapshot
     * exceeds the configured threshold.
     *
     * @param eventCountSinceSnapshot events since the last snapshot (0 if no snapshot exists)
     * @return true if a new snapshot should be taken
     */
    public boolean shouldSnapshot(long eventCountSinceSnapshot) {
        return eventCountSinceSnapshot >= snapshotThreshold;
    }

    @SuppressWarnings("unchecked")
    private CaseSnapshot mapRow(ResultSet rs) throws SnapshotException, SQLException {
        String   caseId      = rs.getString("case_id");
        long     seqNum      = rs.getLong("seq_num");
        Instant  snapshotTs  = rs.getTimestamp("snapshot_ts").toInstant();
        String   specId      = rs.getString("spec_id");
        String   status      = rs.getString("status");
        String   itemsJson   = rs.getString("active_items_json");
        String   payloadJson = rs.getString("payload_json");

        try {
            Map<String, CaseStateView.WorkItemState> activeItems =
                    objectMapper.readValue(itemsJson,
                            new TypeReference<Map<String, CaseStateView.WorkItemState>>(){});
            Map<String, String> payload =
                    objectMapper.readValue(payloadJson,
                            new TypeReference<Map<String, String>>(){});
            return new CaseSnapshot(caseId, specId, seqNum, snapshotTs, status,
                                    activeItems, payload);
        } catch (Exception e) {
            throw new SnapshotException("Failed to parse snapshot data for case " + caseId, e);
        }
    }

    /**
     * Thrown when snapshot persistence or retrieval fails.
     */
    public static final class SnapshotException extends Exception {

        /**
         * Construct with message and cause.
         *
         * @param message error description
         * @param cause   underlying exception
         */
        public SnapshotException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
