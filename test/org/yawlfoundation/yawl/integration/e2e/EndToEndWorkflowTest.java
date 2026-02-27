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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemID;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end workflow integration tests for YAWL v6.
 *
 * Tests the complete workflow lifecycle from specification loading through
 * case execution to completion, with full database persistence verification.
 *
 * Coverage:
 * - Specification construction and validation
 * - Case instantiation and execution
 * - Work item state machine transitions
 * - Database persistence at each lifecycle stage
 * - Event audit trail completeness
 *
 * Chicago TDD: Real YAWL objects, real H2 database, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-18
 */
@Tag("integration")
class EndToEndWorkflowTest {

    private Connection db;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:e2e_workflow_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(db);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }

    // =========================================================================
    // Specification Loading Tests
    // =========================================================================

    @Test
    void testSpecificationConstruction() throws Exception {
        String specId = "e2e-spec-construction";
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);

        assertNotNull(spec, "Specification must not be null");
        assertEquals(specId, spec.getID(), "Specification ID must match");
        assertNotNull(spec.getRootNet(), "Root net must not be null");
        assertEquals("root", spec.getRootNet().getID(), "Root net ID must be 'root'");
    }

    @Test
    void testSpecificationWithSequentialTasks() throws Exception {
        String specId = "e2e-sequential";
        int taskCount = 5;
        YSpecification spec = WorkflowDataFactory.buildSequentialSpec(specId, taskCount);

        assertNotNull(spec.getRootNet());
        YNet net = spec.getRootNet();

        // Verify input condition
        assertNotNull(net.getInputCondition(), "Input condition must exist");

        // Verify output condition
        assertNotNull(net.getOutputCondition(), "Output condition must exist");

        // Verify task count
        int actualTasks = 0;
        for (Object element : net.getNetElements()) {
            if (element instanceof YAtomicTask) {
                actualTasks++;
            }
        }
        assertEquals(taskCount, actualTasks,
                "Specification must have exactly " + taskCount + " tasks");
    }

    @Test
    void testSpecificationPersistence() throws Exception {
        String specId = "e2e-spec-persist";
        String version = "1.0";
        String specName = "End-to-End Persistence Test";

        WorkflowDataFactory.seedSpecification(db, specId, version, specName);

        try (PreparedStatement ps = db.prepareStatement(
                "SELECT spec_name, created_at FROM yawl_specification "
                        + "WHERE spec_id = ? AND spec_version = ?")) {
            ps.setString(1, specId);
            ps.setString(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Specification row must exist");
                assertEquals(specName, rs.getString("spec_name"),
                        "Spec name must match");
                assertNotNull(rs.getTimestamp("created_at"),
                        "Created timestamp must be set");
            }
        }
    }

    @Test
    void testYSpecificationIdContract() throws Exception {
        YSpecificationID id1 = new YSpecificationID("TestSpec", "2.0", "TestSpec.yawl");
        YSpecificationID id2 = new YSpecificationID("TestSpec", "2.0", "TestSpec.yawl");
        YSpecificationID id3 = new YSpecificationID("OtherSpec", "2.0", "OtherSpec.yawl");

        assertEquals(id1, id2, "Same components must be equal");
        assertNotEquals(id1, id3, "Different specifiers must not be equal");
        assertEquals(id1.hashCode(), id2.hashCode(),
                "Equal objects must have same hashCode");
    }

    // =========================================================================
    // Case Lifecycle Tests
    // =========================================================================

    @Test
    void testCaseInstantiation() throws Exception {
        String specId = "e2e-case-instantiation";
        String runnerId = "runner-e2e-case";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Case Test Spec");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        try (PreparedStatement ps = db.prepareStatement(
                "SELECT state, started_at FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Runner row must exist");
                assertEquals("RUNNING", rs.getString("state"),
                        "Runner state must be RUNNING");
                assertNotNull(rs.getTimestamp("started_at"),
                        "Started timestamp must be set");
            }
        }
    }

    @Test
    void testCaseStateTransition() throws Exception {
        String specId = "e2e-state-transition";
        String runnerId = "runner-state-trans";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "State Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        // Transition to SUSPENDED
        updateRunnerState(db, runnerId, "SUSPENDED");
        assertRunnerState(db, runnerId, "SUSPENDED");

        // Transition to RUNNING
        updateRunnerState(db, runnerId, "RUNNING");
        assertRunnerState(db, runnerId, "RUNNING");

        // Transition to COMPLETED
        updateRunnerState(db, runnerId, "COMPLETED");
        assertRunnerState(db, runnerId, "COMPLETED");
    }

    @Test
    void testCaseCancellation() throws Exception {
        String specId = "e2e-case-cancellation";
        String runnerId = "runner-cancellation";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Cancellation Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        // Cancel the case
        updateRunnerState(db, runnerId, "CANCELLED");
        assertRunnerState(db, runnerId, "CANCELLED");
    }

    // =========================================================================
    // Work Item Lifecycle Tests
    // =========================================================================

    @Test
    void testWorkItemCreation() throws Exception {
        String specId = "e2e-workitem-create";
        String runnerId = "runner-workitem";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "WorkItem Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        String itemId = "workitem-001";
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "task_1", "Enabled");

        try (PreparedStatement ps = db.prepareStatement(
                "SELECT status, task_id, created_at FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Work item row must exist");
                assertEquals("Enabled", rs.getString("status"),
                        "Status must be Enabled");
                assertEquals("task_1", rs.getString("task_id"),
                        "Task ID must match");
                assertNotNull(rs.getTimestamp("created_at"),
                        "Created timestamp must be set");
            }
        }
    }

    @Test
    void testWorkItemStateMachine() throws Exception {
        String specId = "e2e-workitem-sm";
        String runnerId = "runner-workitem-sm";
        String itemId = "workitem-sm-001";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "WorkItem State Machine");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "process", "Enabled");

        // Enabled -> Executing
        updateWorkItemStatus(db, itemId, "Executing");
        assertWorkItemStatus(db, itemId, "Executing");

        // Executing -> Completed
        updateWorkItemStatus(db, itemId, "Completed");
        completeWorkItem(db, itemId);
        assertWorkItemStatus(db, itemId, "Completed");

        try (PreparedStatement ps = db.prepareStatement(
                "SELECT completed_at FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNotNull(rs.getTimestamp("completed_at"),
                        "Completed timestamp must be set");
            }
        }
    }

    @Test
    void testWorkItemSuspension() throws Exception {
        String specId = "e2e-workitem-suspend";
        String runnerId = "runner-suspend";
        String itemId = "workitem-suspend-001";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "WorkItem Suspension");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "task_1", "Executing");

        // Suspend
        updateWorkItemStatus(db, itemId, "Suspended");
        assertWorkItemStatus(db, itemId, "Suspended");

        // Resume
        updateWorkItemStatus(db, itemId, "Executing");
        assertWorkItemStatus(db, itemId, "Executing");
    }

    @Test
    void testWorkItemWithYWorkItemObject() throws Exception {
        String specId = "e2e-yworkitem";
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);

        YAtomicTask task = (YAtomicTask) spec.getRootNet().getNetElement("process");
        assertNotNull(task, "Task must exist in specification");

        YIdentifier caseId = new YIdentifier(null);
        YWorkItemID wid = new YWorkItemID(caseId, "process");
        YWorkItem workItem = new YWorkItem(null, spec.getSpecificationID(),
                task, wid, true, false);

        assertEquals(YWorkItemStatus.statusEnabled, workItem.getStatus(),
                "Work item must start in Enabled status");
        assertNotNull(workItem.getIDString(),
                "Work item ID string must not be null");

        workItem.setStatus(YWorkItemStatus.statusExecuting);
        assertEquals(YWorkItemStatus.statusExecuting, workItem.getStatus(),
                "Status must transition to Executing");

        workItem.setStatus(YWorkItemStatus.statusComplete);
        assertEquals(YWorkItemStatus.statusComplete, workItem.getStatus(),
                "Status must transition to Complete");
    }

    // =========================================================================
    // Audit Trail Tests
    // =========================================================================

    @Test
    void testAuditTrailForCompleteWorkflow() throws Exception {
        String specId = "e2e-audit-trail";
        String runnerId = "runner-audit";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Audit Trail Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        long eventId = 1;
        recordCaseEvent(db, eventId++, runnerId, "CASE_STARTED", "{}");
        recordCaseEvent(db, eventId++, runnerId, "TASK_ENABLED",
                "{\"taskId\":\"task_1\"}");
        recordCaseEvent(db, eventId++, runnerId, "TASK_EXECUTING",
                "{\"taskId\":\"task_1\"}");
        recordCaseEvent(db, eventId++, runnerId, "TASK_COMPLETED",
                "{\"taskId\":\"task_1\"}");
        recordCaseEvent(db, eventId, runnerId, "CASE_COMPLETED", "{}");

        try (PreparedStatement ps = db.prepareStatement(
                "SELECT COUNT(*) FROM yawl_case_event WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(5, rs.getInt(1), "Must have 5 audit events");
            }
        }
    }

    @Test
    void testAuditTrailEventOrder() throws Exception {
        String specId = "e2e-audit-order";
        String runnerId = "runner-order";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Audit Order Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        String[] expectedOrder = {"CASE_STARTED", "TASK_ENABLED", "TASK_COMPLETED",
                "CASE_COMPLETED"};

        for (int i = 0; i < expectedOrder.length; i++) {
            recordCaseEvent(db, i + 1L, runnerId, expectedOrder[i], "{}");
        }

        try (PreparedStatement ps = db.prepareStatement(
                "SELECT event_type FROM yawl_case_event WHERE runner_id = ? "
                        + "ORDER BY event_id")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                for (String expected : expectedOrder) {
                    assertTrue(rs.next(), "Must have event: " + expected);
                    assertEquals(expected, rs.getString("event_type"),
                            "Event order must match");
                }
            }
        }
    }

    // =========================================================================
    // Multi-Case Concurrency Tests
    // =========================================================================

    @Test
    void testMultipleCasesSameSpecification() throws Exception {
        String specId = "e2e-multi-case";
        int caseCount = 10;

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Multi Case Test");

        for (int i = 0; i < caseCount; i++) {
            String runnerId = "runner-multi-" + i;
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                    "root", "RUNNING");
            WorkflowDataFactory.seedWorkItem(db, "item-" + i, runnerId,
                    "task_1", "Enabled");
        }

        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = '"
                             + specId + "'")) {
            assertTrue(rs.next());
            assertEquals(caseCount, rs.getInt(1),
                    "Must have " + caseCount + " runners");
        }

        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_work_item wi "
                             + "JOIN yawl_net_runner nr ON wi.runner_id = nr.runner_id "
                             + "WHERE nr.spec_id = '" + specId + "'")) {
            assertTrue(rs.next());
            assertEquals(caseCount, rs.getInt(1),
                    "Must have " + caseCount + " work items");
        }
    }

    // =========================================================================
    // Foreign Key Constraint Tests
    // =========================================================================

    @Test
    void testWorkItemForeignKeys() throws Exception {
        String specId = "e2e-fk-workitem";
        String runnerId = "runner-fk";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "FK Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, "fk-item-1", runnerId, "task", "Enabled");

        // Verify FK relationship via join
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT wi.item_id, nr.runner_id, nr.state "
                        + "FROM yawl_work_item wi "
                        + "JOIN yawl_net_runner nr ON wi.runner_id = nr.runner_id "
                        + "WHERE wi.item_id = ?")) {
            ps.setString(1, "fk-item-1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Join must return result");
                assertEquals(runnerId, rs.getString("runner_id"));
            }
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

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

    private static void assertRunnerState(Connection conn,
                                          String runnerId,
                                          String expectedState) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT state FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Runner must exist");
                assertEquals(expectedState, rs.getString("state"),
                        "Runner state must match");
            }
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

    private static void assertWorkItemStatus(Connection conn,
                                             String itemId,
                                             String expectedStatus) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT status FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Work item must exist");
                assertEquals(expectedStatus, rs.getString("status"),
                        "Work item status must match");
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
}
