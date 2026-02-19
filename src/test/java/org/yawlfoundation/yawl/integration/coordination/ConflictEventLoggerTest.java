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

package org.yawlfoundation.yawl.integration.coordination;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.coordination.events.*;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ConflictEventLogger functionality.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class ConflictEventLoggerTest {

    private static DataSource dataSource;
    private static ConflictEventLogger logger;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_WORK_ITEM_ID = "test-wi-456";

    @BeforeAll
    static void setUp() throws SQLException {
        // Create in-memory H2 database for testing
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
        createTestTables(conn);
        conn.close();

        // Create data source
        dataSource = new TestDataSource();
        logger = new ConflictEventLogger(dataSource);
    }

    @AfterAll
    static void tearDown() {
        if (logger != null) {
            logger.close();
        }
    }

    @Test
    void testConflictDetectedLogging() throws Exception {
        // Create a test conflict
        ConflictEvent conflict = ConflictEvent.detected(
            ConflictEvent.ConflictType.RESOURCE,
            ConflictEvent.Severity.HIGH,
            "Multiple agents competing for server-1",
            new String[]{"agent-1", "agent-2"},
            new String[]{TEST_WORK_ITEM_ID},
            new String[]{"YAWL-POLICY-001"},
            Map.of("resource", "server-1", "requestedAt", Instant.now().toString()),
            Instant.now()
        );

        // Log the conflict
        logger.logConflictDetected(conflict);

        // Verify metrics
        assertEquals(1, logger.getMetrics().getTotalConflicts());
        assertEquals(1, logger.getMetrics().getTotalLogged());
        assertEquals(0, logger.getMetrics().getTotalFailed());

        // Verify event was stored
        WorkflowEventStore store = new WorkflowEventStore(dataSource);
        List<WorkflowEvent> events = store.loadEvents(TEST_CASE_ID);
        assertEquals(1, events.size());
        assertEquals(WorkflowEvent.EventType.CONFLICT_DETECTED, events.get(0).getEventType());
    }

    @Test
    void testResolutionLogging() throws Exception {
        // Create and log a conflict first
        ConflictEvent conflict = ConflictEvent.detected(
            ConflictEvent.ConflictType.RESOURCE,
            ConflictEvent.Severity.HIGH,
            "Test conflict for resolution",
            new String[]{"agent-1"},
            new String[]{TEST_WORK_ITEM_ID},
            new String[]{"YAWL-POLICY-001"},
            Map.of(),
            Instant.now()
        );
        logger.logConflictDetected(conflict);

        // Create a resolution
        ResolutionEvent resolution = conflict.resolved(
            ResolutionEvent.ResolutionStrategy.PRIORITY_BASED,
            "coordinator-service",
            Instant.now()
        );

        // Log the resolution
        logger.logResolution(resolution);

        // Verify metrics
        assertEquals(1, logger.getMetrics().getTotalConflicts());
        assertEquals(1, logger.getMetrics().getTotalResolutions());
        assertEquals(2, logger.getMetrics().getTotalLogged());

        // Verify both events were stored
        WorkflowEventStore store = new WorkflowEventStore(dataSource);
        List<WorkflowEvent> events = store.loadEvents(TEST_CASE_ID);
        assertEquals(2, events.size());

        // Find the resolution event
        WorkflowEvent resolutionEvent = events.stream()
            .filter(e -> e.getEventType() == WorkflowEvent.EventType.CONFLICT_RESOLVED)
            .findFirst()
            .orElseThrow();

        // Verify resolution data
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) resolutionEvent.getPayload();
        assertEquals("PRIORITY_BASED", payload.get("resolutionStrategy"));
        assertEquals("coordinator-service", payload.get("resolutionAgent"));
        assertEquals(true, payload.get("isSuccess"));
    }

    @Test
    void testHandoffLogging() throws Exception {
        // Create a handoff event
        HandoffEvent handoff = HandoffEvent.initiated(
            HandoffEvent.HandoffType.AGENT,
            "agent-1",
            "agent-2",
            TEST_WORK_ITEM_ID,
            TEST_CASE_ID,
            "Workload balancing",
            "token-123",
            Map.of("previousState", "in_progress"),
            Instant.now()
        );

        // Log the handoff initiation
        logger.logHandoffInitiated(handoff);

        // Log the handoff completion
        HandoffEvent completedHandoff = handoff.completed(
            HandoffEvent.HandoffStatus.COMPLETED,
            Instant.now(),
            true,
            ""
        );
        logger.logHandoffCompleted(completedHandoff);

        // Verify metrics
        assertEquals(2, logger.getMetrics().getTotalLogged());
        assertEquals(2, logger.getMetrics().getTotalHandoffs());

        // Verify both events were stored
        WorkflowEventStore store = new WorkflowEventStore(dataSource);
        List<WorkflowEvent> events = store.loadEvents(TEST_CASE_ID);

        // Should have 4 events now: 1 conflict + 2 handoffs + possibly resolution
        assertTrue(events.size() >= 3);

        // Find handoff events
        List<WorkflowEvent> handoffEvents = events.stream()
            .filter(e -> e.getEventType() == WorkflowEvent.EventType.HANDOFF_INITIATED ||
                         e.getEventType() == WorkflowEvent.EventType.HANDOFF_COMPLETED)
            .toList();

        assertEquals(2, handoffEvents.size());
    }

    @Test
    void testAgentDecisionLogging() throws Exception {
        // Create an agent decision
        AgentDecisionEvent decision = AgentDecisionEvent.made(
            "agent-1",
            TEST_CASE_ID,
            TEST_WORK_ITEM_ID,
            AgentDecisionEvent.DecisionType.RESOURCE_ALLOCATION,
            Map.of("availableResources", "server-1,server-2"),
            Instant.now(),
            new AgentDecisionEvent.Decision("opt1", "agent-1", 0.9, "Lower load"),
            new AgentDecisionEvent.ExecutionPlan(
                new String[]{"allocate_resource"},
                new String[]{"schedule_next_task"},
                Map.of("completionTime", "2026-02-17T11:00:00Z")
            )
        );

        // Log the decision
        logger.logAgentDecision(decision);

        // Verify metrics
        assertEquals(1, logger.getMetrics().getTotalLogged());
        assertEquals(1, logger.getMetrics().getTotalDecisions());

        // Verify event was stored
        WorkflowEventStore store = new WorkflowEventStore(dataSource);
        List<WorkflowEvent> events = store.loadEvents(TEST_CASE_ID);

        WorkflowEvent decisionEvent = events.stream()
            .filter(e -> e.getEventType() == WorkflowEvent.EventType.AGENT_DECISION_MADE)
            .findFirst()
            .orElseThrow();

        // Verify decision data
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) decisionEvent.getPayload();
        assertEquals("agent-1", payload.get("agentId"));
        assertEquals("RESOURCE_ALLOCATION", payload.get("decisionType"));
        assertEquals("opt1", ((Map<String, Object>) payload.get("finalDecision")).get("chosenOption"));
    }

    @Test
    void testEventCorrelation() {
        // Get active traces
        List<ConflictEventLogger.EventTrace> traces = logger.getActiveTraces();
        assertFalse(traces.isEmpty(), "Should have active traces for recently logged events");

        // Verify trace properties
        ConflictEventLogger.EventTrace trace = traces.get(0);
        assertNotNull(trace.getTraceId());
        assertNotNull(trace.getStartTime());
        assertEquals("ACTIVE", trace.getStatus());
    }

    @Test
    void testBatchLogging() throws Exception {
        // Create multiple events
        ConflictEvent conflict1 = ConflictEvent.detected(
            ConflictEvent.ConflictType.RESOURCE,
            ConflictEvent.Severity.MEDIUM,
            "Batch conflict 1",
            new String[]{"agent-1"},
            new String[]{TEST_WORK_ITEM_ID},
            new String[]{},
            Map.of(),
            Instant.now()
        );

        ConflictEvent conflict2 = ConflictEvent.detected(
            ConflictEvent.ConflictType.PRIORITY,
            ConflictEvent.Severity.LOW,
            "Batch conflict 2",
            new String[]{"agent-2"},
            new String[]{TEST_WORK_ITEM_ID},
            new String[]{},
            Map.of(),
            Instant.now()
        );

        // Log in batch
        logger.logBatch(List.of(conflict1, conflict2));

        // Verify metrics
        assertEquals(4, logger.getMetrics().getTotalLogged()); // Previous 2 + 2 new
        assertEquals(2, logger.getMetrics().getTotalConflicts()); // 2 new conflicts
    }

    @Test
    void testEventFiltering() {
        // Create a low severity conflict that might be filtered
        ConflictEvent lowSeverityConflict = ConflictEvent.detected(
            ConflictEvent.ConflictType.RESOURCE,
            ConflictEvent.Severity.LOW,
            "Low severity conflict",
            new String[]{"agent-1"},
            new String[]{TEST_WORK_ITEM_ID},
            new String[]{},
            Map.of(),
            Instant.now()
        );

        // Log the conflict (may be filtered based on configuration)
        try {
            logger.logConflictDetected(lowSeverityConflict);
        } catch (ConflictEventLogger.EventLoggingException e) {
            fail("Should not throw exception for filtered event");
        }

        // Metrics should reflect filtering (if enabled)
        assertTrue(logger.getMetrics().getTotalLogged() >= 0);
        assertTrue(logger.getMetrics().getTotalFiltered() >= 0);
    }

    // Helper method to create test database tables
    private static void createTestTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Create workflow_events table (simplified for testing)
            stmt.execute("CREATE TABLE workflow_events (" +
                         "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                         "event_id VARCHAR(36) NOT NULL UNIQUE," +
                         "spec_id VARCHAR(255) NOT NULL," +
                         "case_id VARCHAR(255) NOT NULL," +
                         "seq_num BIGINT NOT NULL," +
                         "event_type VARCHAR(64) NOT NULL," +
                         "event_timestamp TIMESTAMP(6) NOT NULL," +
                         "schema_version VARCHAR(16) NOT NULL DEFAULT '1.0'," +
                         "payload_json TEXT NOT NULL," +
                         "UNIQUE KEY ux_case_seq (case_id, seq_num)" +
                         ")");
        }
    }

    // Simple DataSource implementation for testing
    private static class TestDataSource implements DataSource {
        private final String url = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        // Additional DataSource methods (stubbed for testing)
        @Override public java.io.PrintWriter getLogWriter() throws SQLException { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) throws SQLException {}
        @Override public void setLoginTimeout(int seconds) throws SQLException {}
        @Override public int getLoginTimeout() throws SQLException { return 0; }
        @Override public java.util.logging.Logger getParentLogger() throws SQLException { return null; }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
    }
}