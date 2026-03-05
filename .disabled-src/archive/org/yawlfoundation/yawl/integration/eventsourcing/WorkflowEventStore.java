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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Append-only JDBC-backed event store for YAWL workflow events.
 *
 * <p>Implements the Event Sourcing pattern where workflow events are the authoritative
 * source of truth for case state. Events are immutable once written; the store is
 * strictly append-only. Current case state is derived by replaying events.
 *
 * <h2>Optimistic Concurrency Control</h2>
 * <p>Concurrent writers supply the expected next sequence number. If another writer
 * has already appended at that position, a unique constraint violation is caught and
 * converted to {@link ConcurrentModificationException}. The caller must refresh
 * its view of the event stream and retry.
 *
 * <h2>DDL (run once at schema initialization)</h2>
 * <pre>
 * CREATE TABLE workflow_events (
 *   id              BIGINT PRIMARY KEY AUTO_INCREMENT,
 *   event_id        VARCHAR(36)  NOT NULL UNIQUE,
 *   spec_id         VARCHAR(255) NOT NULL,
 *   case_id         VARCHAR(255),
 *   seq_num         BIGINT       NOT NULL,
 *   event_type      VARCHAR(64)  NOT NULL,
 *   event_timestamp TIMESTAMP(6) NOT NULL,
 *   schema_version  VARCHAR(16)  NOT NULL DEFAULT '1.0',
 *   payload_json    TEXT         NOT NULL,
 *   INDEX idx_case_seq (case_id, seq_num),
 *   UNIQUE KEY ux_case_seq (case_id, seq_num)
 * );
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class WorkflowEventStore {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventStore.class);

    private static final String INSERT_EVENT =
        "INSERT INTO workflow_events "
      + "(event_id, spec_id, case_id, seq_num, event_type, event_timestamp, schema_version, payload_json) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_EVENTS_FOR_CASE =
        "SELECT event_id, spec_id, case_id, seq_num, event_type, event_timestamp, schema_version, payload_json "
      + "FROM workflow_events WHERE case_id = ? ORDER BY seq_num ASC";

    private static final String SELECT_EVENTS_FOR_CASE_UP_TO_TIME =
        "SELECT event_id, spec_id, case_id, seq_num, event_type, event_timestamp, schema_version, payload_json "
      + "FROM workflow_events WHERE case_id = ? AND event_timestamp <= ? ORDER BY seq_num ASC";

    private static final String SELECT_EVENTS_FROM_SEQ =
        "SELECT event_id, spec_id, case_id, seq_num, event_type, event_timestamp, schema_version, payload_json "
      + "FROM workflow_events WHERE case_id = ? AND seq_num > ? ORDER BY seq_num ASC";

    private static final String SELECT_MAX_SEQ =
        "SELECT COALESCE(MAX(seq_num), -1) FROM workflow_events WHERE case_id = ?";

    private static final String SELECT_CASE_IDS_FOR_SPEC =
        "SELECT DISTINCT case_id FROM workflow_events WHERE spec_id = ? AND case_id IS NOT NULL ORDER BY case_id";

    private final DataSource   dataSource;
    private final ObjectMapper objectMapper;

    /**
     * Construct event store backed by the given JDBC data source.
     *
     * @param dataSource JDBC data source with a connection pool (must not be null)
     * @throws EventStoreException if schema initialization fails
     */
    public WorkflowEventStore(DataSource dataSource) throws EventStoreException {
        this.dataSource   = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        initializeSchema();
    }

    /**
     * Initialize database schema on first use. Creates the workflow_events table if needed.
     *
     * @throws EventStoreException if schema initialization fails
     */
    private void initializeSchema() throws EventStoreException {
        // Try creating table with minimal schema first - if it fails due to exists, that's OK
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS workflow_events (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "event_id VARCHAR(36) NOT NULL UNIQUE," +
                "spec_id VARCHAR(255) NOT NULL," +
                "case_id VARCHAR(255)," +
                "seq_num BIGINT NOT NULL," +
                "event_type VARCHAR(64) NOT NULL," +
                "event_timestamp TIMESTAMP NOT NULL," +
                "schema_version VARCHAR(16) NOT NULL DEFAULT '1.0'," +
                "payload_json TEXT NOT NULL," +
                "UNIQUE (case_id, seq_num)" +
                ")");
            log.debug("Event store schema initialized");
        } catch (SQLException e) {
            // If "already exists" error, table is fine - ignore it
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                log.debug("Event store table already exists");
                return;
            }
            // For any other error, throw it
            throw new EventStoreException("Failed to initialize event store schema", e);
        }
    }

    /**
     * Append a workflow event to the store with optimistic concurrency control.
     *
     * <p>The caller supplies the expected next sequence number. If another thread
     * has already written to that position, this method throws
     * {@link ConcurrentModificationException} and the caller must retry after
     * re-reading the event stream.
     *
     * @param event           the event to append (must not be null)
     * @param expectedSeqNum  the expected next sequence number (0 for first event of a case)
     * @throws EventStoreException          if the write fails for reasons other than concurrency
     * @throws ConcurrentModificationException if the expected sequence number is already taken
     */
    public void append(WorkflowEvent event, long expectedSeqNum)
            throws EventStoreException, ConcurrentModificationException {
        Objects.requireNonNull(event, "event must not be null");
        if (expectedSeqNum < 0) {
            throw new IllegalArgumentException("expectedSeqNum must be >= 0, got: " + expectedSeqNum);
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event.getPayload());
        } catch (Exception e) {
            throw new EventStoreException("Failed to serialize event payload for " + event.getEventId(), e);
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_EVENT)) {

            stmt.setString(1, event.getEventId());
            stmt.setString(2, event.getSpecId());
            stmt.setString(3, event.getCaseId());
            stmt.setLong(4, expectedSeqNum);
            stmt.setString(5, event.getEventType().name());
            stmt.setObject(6, java.sql.Timestamp.from(event.getTimestamp()));
            stmt.setString(7, event.getSchemaVersion());
            stmt.setString(8, payloadJson);

            stmt.executeUpdate();
            log.debug("Appended event: id={}, type={}, case={}, seq={}",
                      event.getEventId(), event.getEventType(), event.getCaseId(), expectedSeqNum);

        } catch (SQLException e) {
            // Detect unique constraint violation on (case_id, seq_num)
            // SQLState 23000 = integrity constraint violation (standard)
            // SQLState 23505 = unique violation (PostgreSQL)
            if (e.getSQLState() != null && (e.getSQLState().startsWith("23"))) {
                throw new ConcurrentModificationException(
                        "Sequence number conflict for case '" + event.getCaseId()
                        + "' at seq=" + expectedSeqNum + ". Another writer appended first.",
                        event.getCaseId(), expectedSeqNum);
            }
            throw new EventStoreException("Failed to append event " + event.getEventId(), e);
        }
    }

    /**
     * Append an event, automatically using the next available sequence number.
     * Internally fetches the current max sequence and increments by 1.
     *
     * <p>Not suitable for high-concurrency scenarios where multiple threads write
     * events for the same case simultaneously; use {@link #append(WorkflowEvent, long)}
     * with optimistic concurrency control in that case.
     *
     * @param event the event to append (must not be null)
     * @return the sequence number assigned to this event
     * @throws EventStoreException if the write fails
     */
    public long appendNext(WorkflowEvent event) throws EventStoreException {
        Objects.requireNonNull(event, "event must not be null");
        String caseId = event.getCaseId();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long nextSeq;
                try (PreparedStatement maxStmt = conn.prepareStatement(SELECT_MAX_SEQ)) {
                    maxStmt.setString(1, caseId);
                    try (ResultSet rs = maxStmt.executeQuery()) {
                        nextSeq = rs.next() ? rs.getLong(1) + 1 : 0;
                    }
                }

                String payloadJson = objectMapper.writeValueAsString(event.getPayload());
                try (PreparedStatement insertStmt = conn.prepareStatement(INSERT_EVENT)) {
                    insertStmt.setString(1, event.getEventId());
                    insertStmt.setString(2, event.getSpecId());
                    insertStmt.setString(3, caseId);
                    insertStmt.setLong(4, nextSeq);
                    insertStmt.setString(5, event.getEventType().name());
                    insertStmt.setObject(6, java.sql.Timestamp.from(event.getTimestamp()));
                    insertStmt.setString(7, event.getSchemaVersion());
                    insertStmt.setString(8, payloadJson);
                    insertStmt.executeUpdate();
                }
                conn.commit();
                log.debug("Appended event (auto-seq): id={}, type={}, case={}, seq={}",
                          event.getEventId(), event.getEventType(), caseId, nextSeq);
                return nextSeq;

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            if (e instanceof EventStoreException ese) {
                throw ese;
            }
            throw new EventStoreException("Failed to append next event for case " + caseId, e);
        }
    }

    /**
     * Load all events for a case in sequence order.
     *
     * @param caseId case identifier (must not be blank)
     * @return ordered list of events (empty if case has no events)
     * @throws EventStoreException if the read fails
     */
    public List<WorkflowEvent> loadEvents(String caseId) throws EventStoreException {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_EVENTS_FOR_CASE)) {
            stmt.setString(1, caseId);
            return executeEventQuery(stmt);
        } catch (EventStoreException e) {
            throw e;
        } catch (SQLException e) {
            throw new EventStoreException("Failed to load events for case " + caseId, e);
        }
    }

    /**
     * Load events for a case up to (inclusive) a specific point in time.
     * Used for temporal queries: "what was the state of case X at time T?".
     *
     * @param caseId  case identifier (must not be blank)
     * @param asOf    cutoff timestamp (inclusive)
     * @return ordered list of events with timestamp &lt;= asOf
     * @throws EventStoreException if the read fails
     */
    public List<WorkflowEvent> loadEventsAsOf(String caseId, Instant asOf)
            throws EventStoreException {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        Objects.requireNonNull(asOf, "asOf must not be null");
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_EVENTS_FOR_CASE_UP_TO_TIME)) {
            stmt.setString(1, caseId);
            stmt.setObject(2, java.sql.Timestamp.from(asOf));
            return executeEventQuery(stmt);
        } catch (EventStoreException e) {
            throw e;
        } catch (SQLException e) {
            throw new EventStoreException("Failed to load events for case " + caseId + " asOf " + asOf, e);
        }
    }

    /**
     * Load events for a case starting after a given sequence number.
     * Used for snapshot-based replay: supply the snapshot's sequence number
     * to fetch only the delta events that occurred after the snapshot.
     *
     * @param caseId      case identifier (must not be blank)
     * @param afterSeqNum sequence number to start after (exclusive); use -1 for all events
     * @return ordered list of events with seq_num > afterSeqNum
     * @throws EventStoreException if the read fails
     */
    public List<WorkflowEvent> loadEventsSince(String caseId, long afterSeqNum)
            throws EventStoreException {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be blank");
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_EVENTS_FROM_SEQ)) {
            stmt.setString(1, caseId);
            stmt.setLong(2, afterSeqNum);
            return executeEventQuery(stmt);
        } catch (EventStoreException e) {
            throw e;
        } catch (SQLException e) {
            throw new EventStoreException(
                    "Failed to load events for case " + caseId + " since seq " + afterSeqNum, e);
        }
    }

    /**
     * Load all distinct case IDs recorded for a given specification.
     *
     * @param specId specification identifier (must not be blank)
     * @return sorted list of distinct case IDs (may be empty)
     * @throws EventStoreException if the read fails
     */
    public List<String> loadCaseIds(String specId) throws EventStoreException {
        if (specId == null || specId.isBlank()) {
            throw new IllegalArgumentException("specId must not be blank");
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_CASE_IDS_FOR_SPEC)) {
            stmt.setString(1, specId);
            List<String> caseIds = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    caseIds.add(rs.getString("case_id"));
                }
            }
            return caseIds;
        } catch (SQLException e) {
            throw new EventStoreException("Failed to load case IDs for spec " + specId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<WorkflowEvent> executeEventQuery(PreparedStatement stmt)
            throws EventStoreException, SQLException {
        List<WorkflowEvent> events = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String eventId       = rs.getString("event_id");
                String specId        = rs.getString("spec_id");
                String caseId        = rs.getString("case_id");
                String eventTypeStr  = rs.getString("event_type");
                String schemaVersion = rs.getString("schema_version");
                Instant timestamp    = rs.getTimestamp("event_timestamp").toInstant();
                String payloadJson   = rs.getString("payload_json");

                WorkflowEvent.EventType eventType;
                try {
                    eventType = WorkflowEvent.EventType.valueOf(eventTypeStr);
                } catch (IllegalArgumentException e) {
                    throw new EventStoreException("Unknown event type '" + eventTypeStr
                                                  + "' for event " + eventId, e);
                }

                Map<String, String> payload;
                try {
                    payload = objectMapper.readValue(payloadJson, Map.class);
                } catch (Exception e) {
                    throw new EventStoreException("Failed to parse payload JSON for event " + eventId, e);
                }

                events.add(new WorkflowEvent(eventId, eventType, schemaVersion, specId,
                                             caseId, null, timestamp, payload));
            }
        }
        return events;
    }

    /**
     * Thrown when the event store cannot complete a read or write operation.
     */
    public static final class EventStoreException extends Exception {

        /**
         * Construct with message.
         *
         * @param message error description
         */
        public EventStoreException(String message) {
            super(message);
        }

        /**
         * Construct with message and cause.
         *
         * @param message error description
         * @param cause   underlying exception
         */
        public EventStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Thrown when two writers attempt to append at the same sequence position.
     * The caller must re-read the event stream and retry with the updated sequence number.
     */
    public static final class ConcurrentModificationException extends Exception {

        private final String caseId;
        private final long   conflictingSeq;

        /**
         * Construct with conflict details.
         *
         * @param message       error description
         * @param caseId        the case where the conflict occurred
         * @param conflictingSeq the sequence number that caused the conflict
         */
        public ConcurrentModificationException(String message, String caseId, long conflictingSeq) {
            super(message);
            this.caseId         = caseId;
            this.conflictingSeq = conflictingSeq;
        }

        /** Returns the case ID where the sequence conflict occurred. */
        public String getCaseId() { return caseId; }

        /** Returns the sequence number that was already taken by another writer. */
        public long getConflictingSeq() { return conflictingSeq; }
    }
}
