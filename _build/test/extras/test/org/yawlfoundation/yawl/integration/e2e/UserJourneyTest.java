/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.e2e;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemID;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * User journey integration tests for YAWL v6.
 *
 * Tests real-world user scenarios covering:
 * - Administrator workflows (specification management, case oversight)
 * - Participant workflows (work item handling, task completion)
 * - Observer workflows (monitoring, auditing)
 *
 * Chicago TDD: Real YAWL objects, real database, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-18
 */
@Tag("integration")
class UserJourneyTest {

    private Connection db;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:user_journey_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(db);
        createUserTables(db);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }

    // =========================================================================
    // Administrator Journey Tests
    // =========================================================================

    @Test
    void testAdministratorUploadsSpecification() throws Exception {
        // Journey: Admin uploads a new workflow specification
        String specId = "journey-admin-spec";
        String version = "1.0";
        String specName = "Order Processing Workflow";

        // Step 1: Admin creates specification
        YSpecification spec = WorkflowDataFactory.buildSequentialSpec(specId, 3);

        // Step 2: Admin uploads to engine
        WorkflowDataFactory.seedSpecification(db, specId, version, specName);

        // Step 3: Admin verifies upload
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT spec_name, created_at FROM yawl_specification "
                        + "WHERE spec_id = ? AND spec_version = ?")) {
            ps.setString(1, specId);
            ps.setString(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Specification must be uploaded");
                assertEquals(specName, rs.getString("spec_name"));
            }
        }

        // Step 4: Admin can see specification in list
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 1, "Admin can see at least one specification");
        }
    }

    @Test
    void testAdministratorMonitorsActiveCases() throws Exception {
        // Journey: Admin monitors running workflow cases
        String specId = "journey-admin-monitor";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Monitor Test");

        // Admin starts multiple cases
        int caseCount = 5;
        for (int i = 0; i < caseCount; i++) {
            String runnerId = "runner-monitor-" + i;
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                    "root", i % 2 == 0 ? "RUNNING" : "SUSPENDED");
        }

        // Admin queries active cases
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT runner_id, state FROM yawl_net_runner "
                             + "WHERE spec_id = '" + specId + "' "
                             + "ORDER BY runner_id")) {

            int runningCount = 0;
            int suspendedCount = 0;

            while (rs.next()) {
                String state = rs.getString("state");
                if ("RUNNING".equals(state)) {
                    runningCount++;
                } else if ("SUSPENDED".equals(state)) {
                    suspendedCount++;
                }
            }

            assertTrue(runningCount >= 1, "Admin sees running cases");
            assertTrue(suspendedCount >= 1, "Admin sees suspended cases");
        }
    }

    @Test
    void testAdministratorCancelsCase() throws Exception {
        // Journey: Admin cancels a problematic workflow case
        String specId = "journey-admin-cancel";
        String runnerId = "runner-cancel";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Cancel Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, "cancel-item", runnerId,
                "task_1", "Executing");

        // Admin cancels the case
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_net_runner SET state = 'CANCELLED' "
                        + "WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            ps.executeUpdate();
        }

        // Verify cancellation
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT state FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("CANCELLED", rs.getString("state"),
                        "Case must be cancelled");
            }
        }
    }

    // =========================================================================
    // Participant Journey Tests
    // =========================================================================

    @Test
    void testParticipantViewsWorklist() throws Exception {
        // Journey: Participant views their assigned work items
        String specId = "journey-participant-worklist";
        String participant = "john.doe";

        setupParticipantWorkflow(specId, participant, 5);

        // Participant queries their worklist
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT item_id, task_id, status FROM yawl_work_item "
                        + "WHERE runner_id IN ("
                        + "  SELECT runner_id FROM yawl_net_runner WHERE spec_id = ?"
                        + ") ORDER BY created_at")) {
            ps.setString(1, specId);
            try (ResultSet rs = ps.executeQuery()) {
                int itemCount = 0;
                while (rs.next()) {
                    itemCount++;
                    assertNotNull(rs.getString("item_id"));
                    assertNotNull(rs.getString("task_id"));
                    assertNotNull(rs.getString("status"));
                }
                assertEquals(5, itemCount, "Participant sees 5 work items");
            }
        }
    }

    @Test
    void testParticipantStartsWorkItem() throws Exception {
        // Journey: Participant starts a work item (Enabled -> Executing)
        String specId = "journey-participant-start";
        String runnerId = "runner-start";
        String itemId = "start-item";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Start Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "task_1", "Enabled");

        // Participant starts the work item
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_work_item SET status = 'Executing' WHERE item_id = ?")) {
            ps.setString(1, itemId);
            assertEquals(1, ps.executeUpdate(),
                    "Exactly one work item must be updated");
        }

        // Verify state change
        assertWorkItemStatus(db, itemId, "Executing");
    }

    @Test
    void testParticipantCompletesWorkItem() throws Exception {
        // Journey: Participant completes a work item (Executing -> Completed)
        String specId = "journey-participant-complete";
        String runnerId = "runner-complete";
        String itemId = "complete-item";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Complete Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "task_1", "Executing");

        // Participant completes the work item
        try (PreparedStatement ps = db.prepareStatement(
                "UPDATE yawl_work_item SET status = 'Completed', "
                        + "completed_at = NOW() WHERE item_id = ?")) {
            ps.setString(1, itemId);
            assertEquals(1, ps.executeUpdate());
        }

        // Verify completion
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT status, completed_at FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Completed", rs.getString("status"));
                assertNotNull(rs.getTimestamp("completed_at"));
            }
        }
    }

    @Test
    void testParticipantDelegatesWorkItem() throws Exception {
        // Journey: Participant delegates a work item to another participant
        String specId = "journey-participant-delegate";
        String runnerId = "runner-delegate";
        String itemId = "delegate-item";
        String originalAssignee = "john.doe";
        String newAssignee = "jane.smith";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Delegate Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "task_1", "Executing");

        // Simulate delegation via assignee tracking
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO yawl_work_item_assignee (item_id, assignee) "
                        + "VALUES (?, ?)")) {
            ps.setString(1, itemId);
            ps.setString(2, originalAssignee);
            ps.executeUpdate();

            // Update to new assignee
            ps.setString(1, itemId);
            ps.setString(2, newAssignee);
            ps.executeUpdate();
        }

        // Verify delegation
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT assignee FROM yawl_work_item_assignee "
                        + "WHERE item_id = ? ORDER BY id DESC LIMIT 1")) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(newAssignee, rs.getString("assignee"),
                        "Work item must be assigned to new participant");
            }
        }
    }

    // =========================================================================
    // Observer Journey Tests
    // =========================================================================

    @Test
    void testObserverViewsAuditTrail() throws Exception {
        // Journey: Observer views the audit trail for a completed case
        String specId = "journey-observer-audit";
        String runnerId = "runner-audit";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Audit Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                "root", "COMPLETED");

        // Generate audit trail
        String[] events = {"CASE_STARTED", "TASK_ENABLED", "TASK_EXECUTING",
                "TASK_COMPLETED", "CASE_COMPLETED"};
        for (int i = 0; i < events.length; i++) {
            recordCaseEvent(db, i + 1L, runnerId, events[i],
                    "{\"observer\":\"audit.view\"}");
        }

        // Observer queries audit trail
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT event_id, event_type, event_timestamp "
                        + "FROM yawl_case_event WHERE runner_id = ? "
                        + "ORDER BY event_id")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                int eventCount = 0;
                String previousEvent = null;

                while (rs.next()) {
                    eventCount++;
                    String eventType = rs.getString("event_type");
                    assertNotNull(eventType, "Event type must not be null");
                    assertNotNull(rs.getTimestamp("event_timestamp"),
                            "Event timestamp must not be null");

                    // Verify event order
                    if (previousEvent != null) {
                        int prevIndex = indexOf(events, previousEvent);
                        int currIndex = indexOf(events, eventType);
                        assertTrue(currIndex >= prevIndex,
                                "Events must be in chronological order");
                    }
                    previousEvent = eventType;
                }

                assertEquals(events.length, eventCount,
                        "Observer must see complete audit trail");
            }
        }
    }

    @Test
    void testObserverMonitorsSystemHealth() throws Exception {
        // Journey: Observer monitors overall system health metrics
        String specId = "journey-observer-health";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Health Test");

        // Create various cases with different states
        String[] states = {"RUNNING", "RUNNING", "COMPLETED", "FAILED", "SUSPENDED"};
        for (int i = 0; i < states.length; i++) {
            WorkflowDataFactory.seedNetRunner(db, "runner-health-" + i,
                    specId, "1.0", "root", states[i]);
        }

        // Observer queries health metrics
        Map<String, Integer> stateCounts = new HashMap<>();

        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT state, COUNT(*) as cnt FROM yawl_net_runner "
                             + "WHERE spec_id = '" + specId + "' "
                             + "GROUP BY state")) {

            while (rs.next()) {
                stateCounts.put(rs.getString("state"), rs.getInt("cnt"));
            }
        }

        assertEquals(2, stateCounts.get("RUNNING"), "Observer sees 2 running cases");
        assertEquals(1, stateCounts.get("COMPLETED"), "Observer sees 1 completed case");
        assertEquals(1, stateCounts.get("FAILED"), "Observer sees 1 failed case");
        assertEquals(1, stateCounts.get("SUSPENDED"), "Observer sees 1 suspended case");
    }

    @Test
    void testObserverGeneratesReport() throws Exception {
        // Journey: Observer generates a compliance report
        String specId = "journey-observer-report";

        // Setup workflow data
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Report Test");

        for (int i = 0; i < 10; i++) {
            String runnerId = "runner-report-" + i;
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                    "root", "COMPLETED");
            WorkflowDataFactory.seedWorkItem(db, "item-report-" + i,
                    runnerId, "task_1", "Completed");
            recordCaseEvent(db, i * 2L + 1, runnerId, "CASE_STARTED", "{}");
            recordCaseEvent(db, i * 2L + 2, runnerId, "CASE_COMPLETED", "{}");
        }

        // Observer generates summary report
        int totalCases = 0;
        int totalWorkItems = 0;
        int totalEvents = 0;

        try (Statement stmt = db.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = '"
                            + specId + "'");
            assertTrue(rs.next());
            totalCases = rs.getInt(1);
            rs.close();

            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM yawl_work_item wi "
                            + "JOIN yawl_net_runner nr ON wi.runner_id = nr.runner_id "
                            + "WHERE nr.spec_id = '" + specId + "'");
            assertTrue(rs.next());
            totalWorkItems = rs.getInt(1);
            rs.close();

            rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM yawl_case_event ce "
                            + "JOIN yawl_net_runner nr ON ce.runner_id = nr.runner_id "
                            + "WHERE nr.spec_id = '" + specId + "'");
            assertTrue(rs.next());
            totalEvents = rs.getInt(1);
            rs.close();
        }

        assertEquals(10, totalCases, "Report shows 10 cases");
        assertEquals(10, totalWorkItems, "Report shows 10 work items");
        assertEquals(20, totalEvents, "Report shows 20 events");

        System.out.println("Compliance Report:");
        System.out.println("  Total Cases: " + totalCases);
        System.out.println("  Total Work Items: " + totalWorkItems);
        System.out.println("  Total Events: " + totalEvents);
    }

    // =========================================================================
    // Cross-Role Journey Tests
    // =========================================================================

    @Test
    void testCrossRoleCollaboration() throws Exception {
        // Journey: Multiple roles collaborate on a workflow
        String specId = "journey-cross-role";
        String runnerId = "runner-cross";

        // Admin uploads specification
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", spec.getName());

        // Admin starts case
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                "root", "RUNNING");
        recordCaseEvent(db, 1, runnerId, "CASE_STARTED",
                "{\"startedBy\":\"admin\"}");

        // Participant receives and completes work item
        String itemId = "cross-item";
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "process", "Enabled");
        recordCaseEvent(db, 2, runnerId, "TASK_ENABLED",
                "{\"assignedTo\":\"participant\"}");

        updateWorkItemStatus(db, itemId, "Executing");
        recordCaseEvent(db, 3, runnerId, "TASK_EXECUTING", "{}");

        updateWorkItemStatus(db, itemId, "Completed");
        completeWorkItem(db, itemId);
        recordCaseEvent(db, 4, runnerId, "TASK_COMPLETED",
                "{\"completedBy\":\"participant\"}");

        // Admin marks case complete
        updateRunnerState(db, runnerId, "COMPLETED");
        recordCaseEvent(db, 5, runnerId, "CASE_COMPLETED",
                "{\"completedBy\":\"admin\"}");

        // Observer reviews complete audit trail
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT event_type, event_data FROM yawl_case_event "
                        + "WHERE runner_id = ? ORDER BY event_id")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertNotNull(rs.getString("event_type"));
                    assertNotNull(rs.getString("event_data"));
                }
                assertEquals(5, count, "Complete audit trail must have 5 events");
            }
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static void createUserTables(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS yawl_work_item_assignee (
                    id             INTEGER      AUTO_INCREMENT PRIMARY KEY,
                    item_id        VARCHAR(255) NOT NULL,
                    assignee       VARCHAR(255) NOT NULL,
                    assigned_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
    }

    private static void setupParticipantWorkflow(String specId,
                                                 String participant,
                                                 int workItemCount) throws Exception {
        WorkflowDataFactory.seedSpecification(db, specId, "1.0",
                "Participant Workflow");

        String runnerId = "runner-" + participant;
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                "root", "RUNNING");

        for (int i = 0; i < workItemCount; i++) {
            String itemId = "item-" + participant + "-" + i;
            String taskId = "task_" + i;
            String status = i % 3 == 0 ? "Enabled" : (i % 3 == 1 ? "Executing" : "Completed");
            WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, taskId, status);
        }
    }

    private static void updateWorkItemStatus(Connection conn,
                                             String itemId,
                                             String status) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE yawl_work_item SET status = ? WHERE item_id = ?")) {
            ps.setString(1, status);
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
    }

    private static void completeWorkItem(Connection conn,
                                         String itemId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE yawl_work_item SET completed_at = NOW(), status = 'Completed' "
                        + "WHERE item_id = ?")) {
            ps.setString(1, itemId);
            ps.executeUpdate();
        }
    }

    private static void updateRunnerState(Connection conn,
                                          String runnerId,
                                          String state) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE yawl_net_runner SET state = ? WHERE runner_id = ?")) {
            ps.setString(1, state);
            ps.setString(2, runnerId);
            ps.executeUpdate();
        }
    }

    private static void assertWorkItemStatus(Connection conn,
                                             String itemId,
                                             String expectedStatus) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expectedStatus, rs.getString("status"));
            }
        }
    }

    private static void recordCaseEvent(Connection conn,
                                        long eventId,
                                        String runnerId,
                                        String eventType,
                                        String eventData) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO yawl_case_event "
                        + "(event_id, runner_id, event_type, event_data) "
                        + "VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, eventId);
            ps.setString(2, runnerId);
            ps.setString(3, eventType);
            ps.setString(4, eventData);
            ps.executeUpdate();
        }
    }

    private static int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }
}
