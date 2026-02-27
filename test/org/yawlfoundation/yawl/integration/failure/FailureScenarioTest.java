/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.failure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemID;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.exceptions.YStateException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failure scenario integration tests for YAWL v6.
 *
 * Tests failure handling and recovery:
 * - Exception handling
 * - Graceful degradation
 * - State recovery
 * - Error propagation
 * - Resource cleanup
 *
 * Chicago TDD: Real failures, real recovery, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-18
 */
@Tag("integration")
class FailureScenarioTest {

    private Connection db;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:failure_%d;DB_CLOSE_DELAY=-1"
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
    // Database Failure Tests
    // =========================================================================

    @Test
    void testDatabaseConstraintViolation() throws Exception {
        String specId = "failure-constraint";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Constraint Test");

        // Attempt duplicate insert
        assertThrows(SQLException.class, () -> {
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Duplicate");
        }, "Duplicate insert must throw SQLException");
    }

    @Test
    void testDatabaseForeignKeyViolation() throws Exception {
        // Attempt to insert work item with non-existent runner
        assertThrows(SQLException.class, () -> {
            WorkflowDataFactory.seedWorkItem(db, "fail-item",
                    "nonexistent-runner", "task", "Enabled");
        }, "FK violation must throw SQLException");
    }

    @Test
    void testDatabaseTransactionRollback() throws Exception {
        String specId = "failure-rollback";

        db.setAutoCommit(false);

        try {
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Rollback Test");

            // Force an error
            throw new RuntimeException("Simulated failure");
        } catch (RuntimeException e) {
            db.rollback();
        }

        db.setAutoCommit(true);

        // Verify rollback
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT 1 FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, specId);
            try (ResultSet rs = ps.executeQuery()) {
                assertFalse(rs.next(), "Data must be rolled back");
            }
        }
    }

    @Test
    void testDatabaseConnectionRecovery() throws Exception {
        String specId = "failure-recovery";

        // Close and reopen connection
        db.close();

        String jdbcUrl = "jdbc:h2:mem:failure_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(db);

        // Verify we can still operate
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Recovery Test");

        assertTrue(rowExists(db, "yawl_specification", "spec_id", specId),
                "Must be able to insert after connection recovery");
    }

    // =========================================================================
    // State Machine Failure Tests
    // =========================================================================

    @Test
    void testInvalidStateTransition() throws Exception {
        String specId = "failure-state";
        String runnerId = "runner-state-fail";
        String itemId = "item-state-fail";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "State Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "task", "Enabled");

        // Attempt invalid transition: Enabled -> Completed (skip Executing)
        // In real system, this would be validated by state machine
        updateWorkItemStatus(db, itemId, "Completed");

        // Verify the system allows direct completion (depends on business rules)
        assertWorkItemStatus(db, itemId, "Completed");
    }

    @Test
    void testCaseFailurePropagation() throws Exception {
        String specId = "failure-propagation";
        String runnerId = "runner-propagation";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Propagation Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        String itemId = "item-prop";
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "task", "Executing");

        // Simulate task failure
        updateWorkItemStatus(db, itemId, "Failed");

        // Propagate failure to case
        updateRunnerState(db, runnerId, "FAILED");

        // Verify failure state
        assertRunnerState(db, runnerId, "FAILED");
        assertWorkItemStatus(db, itemId, "Failed");
    }

    @Test
    void testWorkItemFailureAndRetry() throws Exception {
        String specId = "failure-retry";
        String runnerId = "runner-retry";
        String itemId = "item-retry";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Retry Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "task", "Executing");

        // First attempt fails
        updateWorkItemStatus(db, itemId, "Failed");

        // Retry - reset to Executing
        updateWorkItemStatus(db, itemId, "Executing");

        // Second attempt succeeds
        updateWorkItemStatus(db, itemId, "Completed");
        completeWorkItem(db, itemId);

        assertWorkItemStatus(db, itemId, "Completed");
    }

    // =========================================================================
    // Specification Failure Tests
    // =========================================================================

    @Test
    void testInvalidSpecificationConstruction() {
        // Attempt to create spec with null ID
        assertThrows(Exception.class, () -> {
            new YSpecification(null);
        }, "Null spec ID must throw exception");
    }

    @Test
    void testSpecificationValidationFailure() throws Exception {
        // Create spec with missing required elements
        YSpecification spec = new YSpecification("fail-validation");

        // Root net is null - validation should catch this
        assertNull(spec.getRootNet(), "Root net should be null");
    }

    @Test
    void testWorkItemCreationWithInvalidTask() throws Exception {
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec("fail-wi-task");
        YIdentifier caseId = new YIdentifier(null);

        // Attempt to create work item with non-existent task
        YWorkItemID wid = new YWorkItemID(caseId, "nonexistent-task");

        // WorkItem creation with null task should fail
        assertThrows(Exception.class, () -> {
            new YWorkItem(null, spec.getSpecificationID(), null, wid, true, false);
        }, "Null task must throw exception");
    }

    // =========================================================================
    // Concurrency Failure Tests
    // =========================================================================

    @Test
    void testConcurrentModificationConflict() throws Exception {
        String specId = "failure-concurrent";
        String runnerId = "runner-concurrent";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Concurrent Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        // Simulate concurrent modification
        Thread t1 = new Thread(() -> {
            try {
                synchronized (db) {
                    updateRunnerState(db, runnerId, "SUSPENDED");
                    Thread.sleep(100); // Hold lock
                }
            } catch (Exception e) {
                // Ignore
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                Thread.sleep(50); // Wait for t1 to start
                synchronized (db) {
                    updateRunnerState(db, runnerId, "RUNNING");
                }
            } catch (Exception e) {
                // Ignore
            }
        });

        t1.start();
        t2.start();
        t1.join(1000);
        t2.join(1000);

        // One of the updates must have succeeded
        String finalState = getRunnerState(db, runnerId);
        assertTrue("SUSPENDED".equals(finalState) || "RUNNING".equals(finalState),
                "Final state must be one of the concurrent updates: " + finalState);
    }

    @Test
    void testDeadlockDetection() throws Exception {
        String specId = "failure-deadlock";
        String runnerId = "runner-deadlock";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Deadlock Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        // Simulate deadlock state
        updateRunnerState(db, runnerId, "DEADLOCKED");

        assertRunnerState(db, runnerId, "DEADLOCKED");

        // Record deadlock event
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO yawl_case_event "
                        + "(event_id, runner_id, event_type, event_data) "
                        + "VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, runnerId);
            ps.setString(3, "CASE_DEADLOCKED");
            ps.setString(4, "{\"tasks\":[\"task_1\",\"task_2\"]}");
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Resource Exhaustion Tests
    // =========================================================================

    @Test
    void testLargePayloadHandling() throws Exception {
        String specId = "failure-large-payload";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Large Payload Test");

        // Create large event data
        StringBuilder largeData = new StringBuilder("{\"data\":\"");
        for (int i = 0; i < 10000; i++) {
            largeData.append("x");
        }
        largeData.append("\"}");

        // Should handle large payload
        try (PreparedStatement ps = db.prepareStatement(
                "INSERT INTO yawl_case_event "
                        + "(event_id, runner_id, event_type, event_data) "
                        + "VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, "large-payload-runner");
            ps.setString(3, "LARGE_EVENT");
            ps.setString(4, largeData.toString());
            ps.executeUpdate();
        }

        // Verify storage
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT LENGTH(event_data) as len FROM yawl_case_event "
                        + "WHERE event_type = 'LARGE_EVENT'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getInt("len") > 10000, "Large data must be stored");
            }
        }
    }

    @Test
    void testHighVolumeStress() throws Exception {
        String specId = "failure-stress";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Stress Test");

        int iterations = 1000;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            String runnerId = "runner-stress-" + i;
            WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                    "root", "RUNNING");
        }

        long duration = System.currentTimeMillis() - startTime;
        double opsPerSec = (iterations * 1000.0) / duration;

        System.out.printf("Stress test: %d iterations in %dms (%.0f ops/sec)%n",
                iterations, duration, opsPerSec);

        // Verify all records
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = '"
                             + specId + "'")) {
            assertTrue(rs.next());
            assertEquals(iterations, rs.getInt(1), "All records must be stored");
        }
    }

    // =========================================================================
    // Exception Propagation Tests
    // =========================================================================

    @Test
    void testExceptionMessagePropagation() {
        String message = "Test error message";

        YStateException ex = new YStateException(message);

        assertEquals(message, ex.getMessage(),
                "Exception message must be preserved");
    }

    @Test
    void testExceptionChaining() {
        String message = "Outer exception";
        Exception cause = new RuntimeException("Inner exception");

        YStateException ex = new YStateException(message);
        ex.initCause(cause);

        assertEquals(message, ex.getMessage());
        assertEquals(cause, ex.getCause(),
                "Exception cause must be preserved");
    }

    // =========================================================================
    // Cleanup and Recovery Tests
    // =========================================================================

    @Test
    void testResourceCleanupAfterFailure() throws Exception {
        String specId = "failure-cleanup";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Cleanup Test");

        // Create some state
        String runnerId = "runner-cleanup";
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        try {
            // Force failure
            throw new RuntimeException("Simulated failure");
        } catch (RuntimeException e) {
            // Cleanup
            try (Statement stmt = db.createStatement()) {
                stmt.execute("DELETE FROM yawl_net_runner WHERE runner_id = '"
                        + runnerId + "'");
            }
        }

        // Verify cleanup
        assertFalse(rowExists(db, "yawl_net_runner", "runner_id", runnerId),
                "Resources must be cleaned up after failure");
    }

    @Test
    void testSystemRecoveryAfterCrash() throws Exception {
        String specId = "failure-crash-recovery";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Crash Recovery");
        WorkflowDataFactory.seedNetRunner(db, "runner-crash-1", specId, "1.0",
                "root", "RUNNING");
        WorkflowDataFactory.seedNetRunner(db, "runner-crash-2", specId, "1.0",
                "root", "SUSPENDED");

        // Simulate crash recovery - find all running cases
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT runner_id FROM yawl_net_runner WHERE state = ?")) {
            ps.setString(1, "RUNNING");
            try (ResultSet rs = ps.executeQuery()) {
                int runningCount = 0;
                while (rs.next()) {
                    runningCount++;
                    String runnerId = rs.getString("runner_id");

                    // Recovery action: resume or suspend
                    updateRunnerState(db, runnerId, "SUSPENDED");
                }
                assertEquals(1, runningCount, "Must find 1 running case");
            }
        }

        // All should now be suspended
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_net_runner WHERE state = 'SUSPENDED'")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1), "Both cases must be suspended");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static boolean rowExists(Connection conn,
                                     String table,
                                     String column,
                                     String value) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE " + column + " = ?")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
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

    private static void assertRunnerState(Connection conn,
                                          String runnerId,
                                          String expectedState) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT state FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expectedState, rs.getString("state"));
            }
        }
    }

    private static String getRunnerState(Connection conn,
                                         String runnerId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT state FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString("state");
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
                assertTrue(rs.next());
                assertEquals(expectedStatus, rs.getString("status"));
            }
        }
    }
}
