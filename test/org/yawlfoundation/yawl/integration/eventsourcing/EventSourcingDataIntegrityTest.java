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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive event sourcing data integrity validation tests.
 * Tests data consistency, temporal queries, snapshot integrity, and audit trails.
 */
@TestMethodOrder(OrderAnnotation.class)
public class EventSourcingDataIntegrityTest {

    private WorkflowEventStore eventStore;
    private CaseSnapshotRepository snapshotRepository;
    private TemporalCaseQuery temporalQuery;
    private static final String JDBC_URL = "jdbc:h2:mem:testdb";
    private static final String TEST_SPEC_ID = "spec-123";
    private static final String TEST_CASE_ID = "case-456";

    @BeforeEach
    void setUp() throws SQLException {
        // Initialize in-memory database
        Connection conn = DriverManager.getConnection(JDBC_URL);

        // Create tables
        createTables(conn);

        // Initialize components
        eventStore = new WorkflowEventStore(conn);
        snapshotRepository = new CaseSnapshotRepository(conn);
        temporalQuery = new TemporalCaseQuery(conn, eventStore, snapshotRepository);

        conn.close();
    }

    @Test
    @Order(1)
    @DisplayName("Event Sourcing: Event append consistency")
    void testEventAppendConsistency() throws Exception {
        // Generate test events
        List<WorkflowEvent> events = generateTestEvents(TEST_CASE_ID);

        // Append events
        for (WorkflowEvent event : events) {
            eventStore.appendEvent(TEST_SPEC_ID, TEST_CASE_ID, event);
        }

        // Verify all events were stored
        List<WorkflowEvent> storedEvents = eventStore.getEventsForCase(TEST_SPEC_ID, TEST_CASE_ID);
        assertEquals(events.size(), storedEvents.size(), "All events should be stored");

        // Verify event sequence integrity
        for (int i = 0; i < events.size(); i++) {
            assertEquals(events.get(i).getEventId(), storedEvents.get(i).getEventId(),
                "Event IDs should match at position " + i);
        }

        // Verify sequence numbers are sequential
        for (int i = 1; i < storedEvents.size(); i++) {
            assertEquals(storedEvents.get(i-1).getSequenceNumber() + 1,
                storedEvents.get(i).getSequenceNumber(),
                "Sequence numbers should be consecutive");
        }

        System.out.println("✅ Event append consistency validation passed");
    }

    @Test
    @Order(2)
    @DisplayName("Event Sourcing: Concurrent modification handling")
    void testConcurrentModification() throws Exception {
        // Generate two identical events (simulating concurrent writes)
        WorkflowEvent event1 = new WorkflowEvent(
            UUID.randomUUID().toString(),
            "task_started",
            Instant.now(),
            "{\"taskId\":\"task1\"}"
        );

        WorkflowEvent event2 = new WorkflowEvent(
            UUID.randomUUID().toString(),
            "task_started",
            Instant.now(),
            "{\"taskId\":\"task2\"}"
        );

        // First append should succeed
        eventStore.appendEvent(TEST_SPEC_ID, TEST_CASE_ID, event1);

        // Try to append at the same sequence number (should fail)
        assertThrows(ConcurrentModificationException.class, () -> {
            eventStore.appendEvent(TEST_SPEC_ID, TEST_CASE_ID, event2, 1L);
        }, "Concurrent append should fail with expected sequence number");

        System.out.println("✅ Concurrent modification handling validated");
    }

    @Test
    @Order(3)
    @DisplayName("Event Sourcing: Temporal query accuracy")
    void testTemporalQuery() throws Exception {
        // Generate events with different timestamps
        Instant baseTime = Instant.parse("2024-01-01T00:00:00Z");
        List<WorkflowEvent> events = new ArrayList<>();

        events.add(createEvent(baseTime.plusSeconds(10)));
        events.add(createEvent(baseTime.plusSeconds(20)));
        events.add(createEvent(baseTime.plusSeconds(30)));
        events.add(createEvent(baseTime.plusSeconds(40)));

        // Store events
        for (WorkflowEvent event : events) {
            eventStore.appendEvent(TEST_SPEC_ID, TEST_CASE_ID, event);
        }

        // Test temporal query at different time points
        Instant queryTime = baseTime.plusSeconds(25);
        List<WorkflowEvent> eventsUpToQuery = temporalQuery.getEventsUpToTime(
            TEST_SPEC_ID, TEST_CASE_ID, queryTime);

        // Should return events 1 and 2 (before query time)
        assertEquals(2, eventsUpToQuery.size(),
            "Should return 2 events before query time");

        // Verify each event is indeed before the query time
        for (WorkflowEvent event : eventsUpToQuery) {
            assertTrue(event.getTimestamp().isBefore(queryTime),
                "Event should be before query time");
        }

        System.out.println("✅ Temporal query accuracy validated");
    }

    @Test
    @Order(4)
    @DisplayName("Event Sourcing: Snapshot integrity")
    void testSnapshotIntegrity() throws Exception {
        // Generate events
        List<WorkflowEvent> events = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            events.add(createEvent(Instant.now().plusSeconds(i)));
        }

        // Store events
        for (WorkflowEvent event : events) {
            eventStore.appendEvent(TEST_SPEC_ID, TEST_CASE_ID, event);
        }

        // Create snapshot at sequence 50
        CaseSnapshot snapshot = new CaseSnapshot(
            TEST_CASE_ID,
            50L,
            Instant.now(),
            "{\"state\":{\"active\":true,\"completedTasks\":50}}"
        );
        snapshotRepository.saveSnapshot(snapshot);

        // Verify snapshot can be retrieved
        CaseSnapshot retrievedSnapshot = snapshotRepository.getSnapshot(
            TEST_CASE_ID, 50L);
        assertNotNull(retrievedSnapshot, "Snapshot should be retrievable");
        assertEquals(snapshot.getStateJson(), retrievedSnapshot.getStateJson(),
            "Snapshot state should match");

        // Test state reconstruction
        CaseStateView reconstructedState = temporalQuery.reconstructCaseState(
            TEST_SPEC_ID, TEST_CASE_ID, Instant.now());
        assertNotNull(reconstructedState, "State should be reconstructable");

        System.out.println("✅ Snapshot integrity validation passed");
    }

    @Test
    @Order(5)
    @DisplayName("Event Sourcing: Audit trail verification")
    void testAuditTrail() throws Exception {
        // Generate events with audit metadata
        WorkflowEvent event1 = new WorkflowEvent(
            UUID.randomUUID().toString(),
            "case_started",
            Instant.now(),
            "{\"initiator\":\"user1\",\"reason\":\"new_order\"}"
        );

        eventStore.appendEvent(TEST_SPEC_ID, TEST_CASE_ID, event1);

        // Retrieve audit trail
        List<AuditEntry> auditTrail = eventStore.getAuditTrail(
            TEST_SPEC_ID, TEST_CASE_ID);

        // Verify audit entries
        assertFalse(auditTrail.isEmpty(), "Audit trail should not be empty");

        AuditEntry entry = auditTrail.get(0);
        assertEquals(event1.getEventId(), entry.getEventId(),
            "Audit entry should reference correct event");
        assertNotNull(entry.getTimestamp(), "Audit entry should have timestamp");

        // Verify data integrity hash
        String dataHash = entry.getDataHash();
        assertNotNull(dataHash, "Data hash should be present");

        // Simulate audit verification
        String recalculatedHash = calculateDataHash(event1.getPayloadJson());
        assertEquals(recalculatedHash, dataHash,
            "Data hash should be consistent");

        System.out.println("✅ Audit trail verification passed");
    }

    @Test
    @Order(6)
    @DisplayName("Event Sourcing: XES export integrity")
    void testXesExport() throws Exception {
        // Generate events
        List<WorkflowEvent> events = generateTestEvents(TEST_CASE_ID);

        // Store events
        for (WorkflowEvent event : events) {
            eventStore.appendEvent(TEST_SPEC_ID, TEST_CASE_ID, event);
        }

        // Export to XES
        String xesContent = eventStore.exportToXes(TEST_SPEC_ID, TEST_CASE_ID);

        // Validate XES structure
        assertNotNull(xesContent, "XES export should not be null");
        assertTrue(xesContent.contains("<log>"), "Should contain <log> element");
        assertTrue(xesContent.contains("<trace>"), "Should contain <trace> element");
        assertTrue(xesContent.contains("<event>"), "Should contain <event> elements");

        // Count traces and events
        long traceCount = xesContent.split("<trace>").length - 1;
        long eventCount = xesContent.split("<event>").length - 1;

        assertEquals(1, traceCount, "Should have 1 trace");
        assertEquals(events.size(), eventCount, "Event count should match");

        // Validate XES schema compliance
        assertTrue(validateXesSchema(xesContent),
            "XES should conform to schema");

        System.out.printf("✅ XES export integrity validated: %d events%n", eventCount);
    }

    @Test
    @Order(7)
    @DisplayName("Event Sourcing: Data recovery from backup")
    void testDataRecovery() throws Exception {
        // Generate and store events
        List<WorkflowEvent> originalEvents = generateTestEvents(TEST_CASE_ID);
        for (WorkflowEvent event : originalEvents) {
            eventStore.appendEvent(TEST_SPEC_ID, TEST_CASE_ID, event);
        }

        // Create backup
        String backup = eventStore.exportToXes(TEST_SPEC_ID, TEST_CASE_ID);

        // Simulate data loss by clearing events
        eventStore.clearCaseEvents(TEST_SPEC_ID, TEST_CASE_ID);

        // Restore from backup
        eventStore.importFromXes(TEST_SPEC_ID, TEST_CASE_ID, backup);

        // Verify data integrity
        List<WorkflowEvent> restoredEvents = eventStore.getEventsForCase(
            TEST_SPEC_ID, TEST_CASE_ID);

        assertEquals(originalEvents.size(), restoredEvents.size(),
            "Restored event count should match original");

        // Verify event content
        for (int i = 0; i < originalEvents.size(); i++) {
            assertEquals(originalEvents.get(i).getEventId(),
                restoredEvents.get(i).getEventId(),
                "Event ID should match at position " + i);
        }

        System.out.println("✅ Data recovery from backup validated");
    }

    // Helper methods

    private void createTables(Connection conn) throws SQLException {
        // Create workflow_events table
        conn.createStatement().executeUpdate(
            "CREATE TABLE workflow_events (" +
            "  id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
            "  event_id VARCHAR(36) NOT NULL UNIQUE, " +
            "  spec_id VARCHAR(255) NOT NULL, " +
            "  case_id VARCHAR(255) NOT NULL, " +
            "  seq_num BIGINT NOT NULL, " +
            "  event_type VARCHAR(64) NOT NULL, " +
            "  event_timestamp TIMESTAMP(6) NOT NULL, " +
            "  schema_version VARCHAR(16) NOT NULL DEFAULT '1.0', " +
            "  payload_json TEXT NOT NULL, " +
            "  UNIQUE (case_id, seq_num)" +
            ")");

        // Create case_snapshots table
        conn.createStatement().executeUpdate(
            "CREATE TABLE case_snapshots (" +
            "  case_id VARCHAR(255) NOT NULL, " +
            "  seq_num BIGINT NOT NULL, " +
            "  snapshot_ts TIMESTAMP(6) NOT NULL, " +
            "  state_json TEXT NOT NULL, " +
            "  PRIMARY KEY (case_id, seq_num)" +
            ")");
    }

    private List<WorkflowEvent> generateTestEvents(String caseId) {
        List<WorkflowEvent> events = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < 10; i++) {
            events.add(new WorkflowEvent(
                UUID.randomUUID().toString(),
                "task_" + i,
                now.plusSeconds(i * 5),
                "{\"taskNum\":" + i + "}"
            ));
        }

        return events;
    }

    private WorkflowEvent createEvent(Instant timestamp) {
        return new WorkflowEvent(
            UUID.randomUUID().toString(),
            "test_event",
            timestamp,
            "{\"data\":\"test\"}"
        );
    }

    private String calculateDataHash(String data) {
        // Simple hash calculation for demonstration
        int hash = data.hashCode();
        return Integer.toHexString(hash);
    }

    private boolean validateXesSchema(String xesContent) {
        // Basic XES schema validation
        return xesContent.contains("xmlns=\"http://www.xes-standard.org\"") &&
               xesContent.contains("<extension>") &&
               xesContent.contains("<global>") &&
               xesContent.contains("<classifier>") &&
               xesContent.contains("<trace>") &&
               xesContent.contains("<event>");
    }
}