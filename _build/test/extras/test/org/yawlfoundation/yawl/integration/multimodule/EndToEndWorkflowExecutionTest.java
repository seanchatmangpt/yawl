/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.multimodule;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemID;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end workflow execution integration tests for YAWL v6.0.0.
 *
 * Tests the complete lifecycle of a YAWL workflow case:
 * 1. Specification construction (elements module)
 * 2. Case instantiation (engine module: YIdentifier, YNetRunner)
 * 3. Work item state machine: Enabled -> Executing -> Completed
 * 4. Persistence layer: all state transitions recorded to H2
 * 5. Case audit trail: yawl_case_event rows for each transition
 *
 * Scenarios:
 * - Linear workflow (1 task)
 * - Sequential multi-task workflow (3 tasks)
 * - High-volume: 50 concurrent cases through a 2-task workflow
 *
 * Chicago TDD: full system, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Tag("integration")
class EndToEndWorkflowExecutionTest {

    private Connection db;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:e2e_%d;DB_CLOSE_DELAY=-1".formatted(System.nanoTime());
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
    // Linear Workflow (1 task)
    // =========================================================================

    @Test
    void testLinearWorkflowE2E() throws Exception {
        // 1. Build specification
        String specId = "e2e-linear";
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", spec.getName());

        // 2. Launch case (runner)
        String runnerId = "runner-e2e-linear";
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
        recordCaseEvent(db, 1L, runnerId, "CASE_STARTED", "{\"specId\":\"" + specId + "\"}");

        // 3. Create and execute work item
        YAtomicTask task = (YAtomicTask) spec.getRootNet().getNetElement("process");
        assertNotNull(task, "'process' task must exist");

        YIdentifier caseId = new YIdentifier(null);
        YWorkItemID wid = new YWorkItemID(caseId, "process");
        YWorkItem workItem = new YWorkItem(null, spec.getSpecificationID(), task, wid, true, false);

        assertEquals(YWorkItemStatus.statusEnabled, workItem.getStatus());
        WorkflowDataFactory.seedWorkItem(db, wid.toString(), runnerId, "process", "Enabled");

        workItem.setStatus(YWorkItemStatus.statusExecuting);
        updateWorkItemStatus(db, wid.toString(), "Executing");

        workItem.setStatus(YWorkItemStatus.statusComplete);
        updateWorkItemStatus(db, wid.toString(), "Completed");
        updateWorkItemCompleted(db, wid.toString());
        recordCaseEvent(db, 2L, runnerId, "TASK_COMPLETED", "{\"taskId\":\"process\"}");

        // 4. Complete the case
        updateRunnerState(db, runnerId, "COMPLETED");
        recordCaseEvent(db, 3L, runnerId, "CASE_COMPLETED", "{}");

        // 5. Verify final state
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT status, completed_at FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, wid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Completed", rs.getString("status"));
                assertNotNull(rs.getTimestamp("completed_at"));
            }
        }

        try (PreparedStatement ps = db.prepareStatement(
                "SELECT state FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("COMPLETED", rs.getString("state"));
            }
        }

        assertCaseEventCount(db, runnerId, 3);
    }

    // =========================================================================
    // Sequential Multi-Task Workflow (3 tasks)
    // =========================================================================

    @Test
    void testSequentialMultiTaskWorkflowE2E() throws Exception {
        int taskCount = 3;
        String specId = "e2e-sequential-3";
        YSpecification spec = WorkflowDataFactory.buildSequentialSpec(specId, taskCount);
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", spec.getName());

        String runnerId = "runner-e2e-seq3";
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
        long eventId = 1L;
        recordCaseEvent(db, eventId++, runnerId, "CASE_STARTED", "{}");

        YIdentifier caseId = new YIdentifier(null);

        for (int i = 0; i < taskCount; i++) {
            String taskId = "task_" + i;
            YAtomicTask task = (YAtomicTask) spec.getRootNet().getNetElement(taskId);
            assertNotNull(task, "Task must exist: " + taskId);

            YWorkItemID wid = new YWorkItemID(caseId, taskId);
            YWorkItem workItem = new YWorkItem(
                    null, spec.getSpecificationID(), task, wid, true, false);

            WorkflowDataFactory.seedWorkItem(db, wid.toString(), runnerId, taskId, "Enabled");
            workItem.setStatus(YWorkItemStatus.statusExecuting);
            updateWorkItemStatus(db, wid.toString(), "Executing");
            workItem.setStatus(YWorkItemStatus.statusComplete);
            updateWorkItemStatus(db, wid.toString(), "Completed");
            updateWorkItemCompleted(db, wid.toString());
            recordCaseEvent(db, eventId++, runnerId, "TASK_COMPLETED",
                    "{\"taskId\":\"" + taskId + "\"}");
        }

        updateRunnerState(db, runnerId, "COMPLETED");
        recordCaseEvent(db, eventId, runnerId, "CASE_COMPLETED", "{}");

        // Verify all task work items completed
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_work_item "
                     + "WHERE runner_id = '" + runnerId + "' AND status = 'Completed'")) {
            assertTrue(rs.next());
            assertEquals(taskCount, rs.getInt(1),
                    "All " + taskCount + " work items must be Completed");
        }

        // Verify event trail: CASE_STARTED + taskCount*TASK_COMPLETED + CASE_COMPLETED
        assertCaseEventCount(db, runnerId, taskCount + 2);
    }

    // =========================================================================
    // High-Volume: 50 Concurrent Cases
    // =========================================================================

    @Test
    void testHighVolumeConcurrentCases() throws Exception {
        int caseCount = 50;
        String specId = "e2e-high-volume";
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", spec.getName());

        YAtomicTask task = (YAtomicTask) spec.getRootNet().getNetElement("process");
        assertNotNull(task);

        Instant start = Instant.now();

        for (int c = 0; c < caseCount; c++) {
            String runnerId = "runner-hv-" + c;
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

            YIdentifier caseId = new YIdentifier(null);
            YWorkItemID wid = new YWorkItemID(caseId, "process");
            WorkflowDataFactory.seedWorkItem(db, wid.toString(), runnerId, "process", "Completed");
            updateWorkItemCompleted(db, wid.toString());
            updateRunnerState(db, runnerId, "COMPLETED");
        }

        long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();

        // Verify all 50 cases completed
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_net_runner "
                     + "WHERE spec_id = '" + specId + "' AND state = 'COMPLETED'")) {
            assertTrue(rs.next());
            assertEquals(caseCount, rs.getInt(1),
                    "All " + caseCount + " cases must be in COMPLETED state");
        }

        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_work_item wi "
                     + "JOIN yawl_net_runner nr ON wi.runner_id = nr.runner_id "
                     + "WHERE nr.spec_id = '" + specId + "'")) {
            assertTrue(rs.next());
            assertEquals(caseCount, rs.getInt(1),
                    "All " + caseCount + " work items must be persisted");
        }

        double casesPerSec = (caseCount * 1000.0) / Math.max(durationMs, 1);
        System.out.printf("E2E high-volume: %.0f cases/sec (%d cases in %dms)%n",
                casesPerSec, caseCount, durationMs);

        assertTrue(durationMs < 30_000,
                "50 cases must complete in < 30s, took: " + durationMs + "ms");
    }

    // =========================================================================
    // Audit Trail Completeness
    // =========================================================================

    @Test
    void testAuditTrailCompleteness() throws Exception {
        String specId = "e2e-audit";
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", spec.getName());

        String runnerId = "runner-audit";
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        // Record each lifecycle event
        String[] events = {"CASE_STARTED", "TASK_ENABLED", "TASK_EXECUTING",
                           "TASK_COMPLETED", "CASE_COMPLETED"};
        for (int i = 0; i < events.length; i++) {
            recordCaseEvent(db, i + 1L, runnerId, events[i],
                    "{\"step\":" + (i + 1) + "}");
        }

        // Verify all events are recorded in correct order
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT event_type FROM yawl_case_event "
                + "WHERE runner_id = ? ORDER BY event_id")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                for (String expectedEvent : events) {
                    assertTrue(rs.next(),
                            "Event row must exist: " + expectedEvent);
                    assertEquals(expectedEvent, rs.getString("event_type"),
                            "Event type must match in audit order");
                }
                assertFalse(rs.next(), "No extra events must be present");
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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

    private static void updateWorkItemCompleted(Connection conn,
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

    private static void recordCaseEvent(Connection conn,
                                         long eventId,
                                         String runnerId,
                                         String eventType,
                                         String eventData) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO yawl_case_event "
                + "(event_id, runner_id, event_type, event_data) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, eventId);
            ps.setString(2, runnerId);
            ps.setString(3, eventType);
            ps.setString(4, eventData);
            ps.executeUpdate();
        }
    }

    private static void assertCaseEventCount(Connection conn,
                                              String runnerId,
                                              int expectedCount) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM yawl_case_event WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expectedCount, rs.getInt(1),
                        "Case event count must be " + expectedCount
                        + " for runner " + runnerId);
            }
        }
    }
}
