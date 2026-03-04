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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.eventsourcing;

import org.h2.jdbcx.JdbcDataSource;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Shared test fixture for EventSourcing integration tests using H2 in-memory database.
 *
 * <p>Provides consistent database setup/teardown and test data builders for
 * WorkflowEvent, CaseSnapshot, and related test entities.
 *
 * <h2>Usage</h2>
 * <pre>
 * {@code
 * @BeforeEach
 * void setUp() throws SQLException {
 *     dataSource = EventSourcingTestFixture.createDataSource();
 *     EventSourcingTestFixture.createSchema(dataSource);
 *     eventStore = new WorkflowEventStore(dataSource);
 * }
 *
 * @AfterEach
 * void tearDown() throws SQLException {
 *     EventSourcingTestFixture.dropSchema(dataSource);
 * }
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class EventSourcingTestFixture {

    private static final String CREATE_EVENTS_TABLE = """
        CREATE TABLE workflow_events (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            event_id VARCHAR(36) NOT NULL UNIQUE,
            spec_id VARCHAR(255) NOT NULL,
            case_id VARCHAR(255),
            seq_num BIGINT NOT NULL,
            event_type VARCHAR(64) NOT NULL,
            event_timestamp TIMESTAMP(6) NOT NULL,
            schema_version VARCHAR(16) NOT NULL DEFAULT '1.0',
            payload_json TEXT NOT NULL
        )
        """;

    private static final String CREATE_EVENTS_INDEX =
        "CREATE UNIQUE INDEX ux_case_seq ON workflow_events(case_id, seq_num)";

    private static final String CREATE_SNAPSHOTS_TABLE = """
        CREATE TABLE case_snapshots (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            case_id VARCHAR(255) NOT NULL,
            seq_num BIGINT NOT NULL,
            snapshot_ts TIMESTAMP(6) NOT NULL,
            spec_id VARCHAR(255) NOT NULL,
            status VARCHAR(32) NOT NULL,
            active_items_json TEXT NOT NULL,
            payload_json TEXT NOT NULL
        )
        """;

    private static final String CREATE_SNAPSHOTS_INDEX =
        "CREATE UNIQUE INDEX ux_snapshot_case_seq ON case_snapshots(case_id, seq_num)";

    /** Default test spec ID for test events */
    public static final String TEST_SPEC_ID = "OrderFulfillment:1.0";

    /** Default base timestamp for test events */
    public static final Instant BASE_TIMESTAMP = Instant.parse("2026-02-17T10:00:00Z");

    /** Alternative base timestamp for tests needing time manipulation */
    public static final Instant BASE_TIMESTAMP_PLUS_30 = BASE_TIMESTAMP.plusSeconds(30);

    /** Alternative base timestamp for tests needing time manipulation */
    public static final Instant BASE_TIMESTAMP_PLUS_60 = BASE_TIMESTAMP.plusSeconds(60);

    /** CaseSnapshot implementation for testing */
    public static class CaseSnapshot implements org.yawlfoundation.yawl.integration.eventsourcing.CaseSnapshot {
        private final String caseId;
        private final String specId;
        private final long sequenceNumber;
        private final Instant snapshotAt;
        private final String status;
        private final Map<String, CaseStateView.WorkItemState> activeWorkItems;
        private final Map<String, String> payload;

        public CaseSnapshot(String caseId, String specId, long sequenceNumber, Instant snapshotAt,
                           String status, Map<String, CaseStateView.WorkItemState> activeWorkItems,
                           Map<String, String> payload) {
            this.caseId = caseId;
            this.specId = specId;
            this.sequenceNumber = sequenceNumber;
            this.snapshotAt = snapshotAt;
            this.status = status;
            this.activeWorkItems = activeWorkItems != null ? activeWorkItems : Map.of();
            this.payload = payload != null ? payload : Map.of();
        }

        @Override public String getCaseId() { return caseId; }
        @Override public String getSpecId() { return specId; }
        @Override public long getSequenceNumber() { return sequenceNumber; }
        @Override public Instant getSnapshotAt() { return snapshotAt; }
        @Override public String getStatus() { return status; }
        @Override public Map<String, CaseStateView.WorkItemState> getActiveWorkItems() { return activeWorkItems; }
        @Override public Map<String, String> getPayload() { return payload; }
    }

    // Private constructor - utility class
    private EventSourcingTestFixture() {}

    /**
     * Create an H2 in-memory data source with unique database name.
     *
     * <p>Each call creates a fresh isolated database instance suitable for
     * parallel test execution.
     *
     * @return configured H2 data source
     */
    public static DataSource createDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:test-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        return ds;
    }

    /**
     * Create database schema for event sourcing tables.
     *
     * @param dataSource data source to create schema in
     * @throws SQLException if schema creation fails
     */
    public static void createSchema(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_EVENTS_TABLE);
            stmt.execute(CREATE_EVENTS_INDEX);
            stmt.execute(CREATE_SNAPSHOTS_TABLE);
            stmt.execute(CREATE_SNAPSHOTS_INDEX);
        }
    }

    /**
     * Drop all objects from the database.
     *
     * @param dataSource data source to clean up
     * @throws SQLException if cleanup fails
     */
    public static void dropSchema(DataSource dataSource) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP ALL OBJECTS");
        }
    }

    /**
     * Create a test workflow event with default values.
     *
     * @param type      event type
     * @param caseId    case identifier
     * @param workItemId work item identifier (may be null for case-level events)
     * @return constructed workflow event
     */
    public static WorkflowEvent createTestEvent(WorkflowEvent.EventType type,
                                                 String caseId,
                                                 String workItemId) {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            type,
            WorkflowEvent.SCHEMA_VERSION,
            TEST_SPEC_ID,
            caseId,
            workItemId,
            BASE_TIMESTAMP,
            Map.of()
        );
    }

    /**
     * Create a test workflow event with custom payload.
     *
     * @param type      event type
     * @param caseId    case identifier
     * @param workItemId work item identifier (may be null)
     * @param payload   event payload
     * @return constructed workflow event
     */
    public static WorkflowEvent createTestEvent(WorkflowEvent.EventType type,
                                                 String caseId,
                                                 String workItemId,
                                                 Map<String, String> payload) {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            type,
            WorkflowEvent.SCHEMA_VERSION,
            TEST_SPEC_ID,
            caseId,
            workItemId,
            BASE_TIMESTAMP,
            payload != null ? payload : Map.of()
        );
    }

    /**
     * Create a test workflow event with custom timestamp.
     *
     * @param type      event type
     * @param caseId    case identifier
     * @param workItemId work item identifier (may be null)
     * @param timestamp event timestamp
     * @return constructed workflow event
     */
    public static WorkflowEvent createTestEvent(WorkflowEvent.EventType type,
                                                 String caseId,
                                                 String workItemId,
                                                 Instant timestamp) {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            type,
            WorkflowEvent.SCHEMA_VERSION,
            TEST_SPEC_ID,
            caseId,
            workItemId,
            timestamp,
            Map.of()
        );
    }

    /**
     * Create a test workflow event with all custom values.
     *
     * @param type       event type
     * @param caseId     case identifier
     * @param workItemId work item identifier (may be null)
     * @param timestamp  event timestamp
     * @param payload    event payload
     * @return constructed workflow event
     */
    public static WorkflowEvent createTestEvent(WorkflowEvent.EventType type,
                                                 String caseId,
                                                 String workItemId,
                                                 Instant timestamp,
                                                 Map<String, String> payload) {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            type,
            WorkflowEvent.SCHEMA_VERSION,
            TEST_SPEC_ID,
            caseId,
            workItemId,
            timestamp,
            payload != null ? payload : Map.of()
        );
    }

    /**
     * Create a test workflow event with custom spec ID.
     *
     * @param type       event type
     * @param specId     specification identifier
     * @param caseId     case identifier
     * @param workItemId work item identifier (may be null)
     * @return constructed workflow event
     */
    public static WorkflowEvent createTestEventWithSpec(WorkflowEvent.EventType type,
                                                         String specId,
                                                         String caseId,
                                                         String workItemId) {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            type,
            WorkflowEvent.SCHEMA_VERSION,
            specId,
            caseId,
            workItemId,
            BASE_TIMESTAMP,
            Map.of()
        );
    }

    /**
     * Create a test case snapshot with default values.
     *
     * @param caseId         case identifier
     * @param sequenceNumber last event sequence number
     * @param status         case status
     * @return constructed case snapshot
     */
    public static CaseSnapshot createTestSnapshot(String caseId,
                                                   long sequenceNumber,
                                                   String status) {
        return new CaseSnapshot(
            caseId,
            TEST_SPEC_ID,
            sequenceNumber,
            BASE_TIMESTAMP,
            status,
            Map.of(),
            Map.of()
        );
    }

    /**
     * Create a test case snapshot with work items.
     *
     * @param caseId         case identifier
     * @param sequenceNumber last event sequence number
     * @param status         case status
     * @param workItems      active work items
     * @return constructed case snapshot
     */
    public static CaseSnapshot createTestSnapshot(String caseId,
                                                   long sequenceNumber,
                                                   String status,
                                                   Map<String, CaseStateView.WorkItemState> workItems) {
        return new CaseSnapshot(
            caseId,
            TEST_SPEC_ID,
            sequenceNumber,
            BASE_TIMESTAMP,
            status,
            workItems,
            Map.of()
        );
    }

    /**
     * Generate a unique case ID for testing.
     *
     * @param prefix prefix for the case ID
     * @return unique case ID
     */
    public static String generateCaseId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generate a unique case ID with default prefix.
     *
     * @return unique case ID
     */
    public static String generateCaseId() {
        return generateCaseId("test-case");
    }

    /**
     * Create a test workflow event with custom timestamp only.
     *
     * @param type      event type
     * @param caseId    case identifier
     * @param workItemId work item identifier (may be null)
     * @param timestamp  event timestamp
     * @return constructed workflow event
     */
    public static WorkflowEvent createTestEvent(WorkflowEvent.EventType type,
                                                 String caseId,
                                                 String workItemId,
                                                 Instant timestamp) {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            type,
            WorkflowEvent.SCHEMA_VERSION,
            TEST_SPEC_ID,
            caseId,
            workItemId,
            timestamp,
            Map.of()
        );
    }

    /**
     * Create a test workflow event with payload only (uses base timestamp).
     *
     * @param type      event type
     * @param caseId    case identifier
     * @param workItemId work item identifier (may be null)
     * @param payload    event payload
     * @return constructed workflow event
     */
    public static WorkflowEvent createTestEvent(WorkflowEvent.EventType type,
                                                 String caseId,
                                                 String workItemId,
                                                 Map<String, String> payload) {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            type,
            WorkflowEvent.SCHEMA_VERSION,
            TEST_SPEC_ID,
            caseId,
            workItemId,
            BASE_TIMESTAMP,
            payload != null ? payload : Map.of()
        );
    }

    /**
     * Generate a unique work item ID for testing.
     *
     * @param prefix prefix for the work item ID
     * @return unique work item ID
     */
    public static String generateWorkItemId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Generate a unique work item ID with default prefix.
     *
     * @return unique work item ID
     */
    public static String generateWorkItemId() {
        return generateWorkItemId("wi");
    }
}
