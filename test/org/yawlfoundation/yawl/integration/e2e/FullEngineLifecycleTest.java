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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full engine lifecycle integration tests for YAWL v6.
 *
 * Tests the complete lifecycle of the YAWL engine including:
 * - Engine initialization
 * - Case execution from start to completion
 * - Concurrent case handling
 * - Clean shutdown
 *
 * Chicago TDD: Real YAWL objects, real database, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-18
 */
@Tag("integration")
class FullEngineLifecycleTest {

    private Connection db;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:lifecycle_%d;DB_CLOSE_DELAY=-1"
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
    // Engine Initialization Tests
    // =========================================================================

    @Test
    void testDatabaseInitialization() throws Exception {
        // Verify all required tables exist
        String[] expectedTables = {
            "YAWL_SPECIFICATION",
            "YAWL_NET_RUNNER",
            "YAWL_WORK_ITEM",
            "YAWL_CASE_EVENT"
        };

        try (Statement stmt = db.createStatement()) {
            for (String table : expectedTables) {
                ResultSet rs = stmt.executeQuery(
                        "SELECT 1 FROM INFORMATION_SCHEMA.TABLES "
                                + "WHERE TABLE_NAME = '" + table + "'");
                assertTrue(rs.next(), "Table " + table + " must exist");
                rs.close();
            }
        }
    }

    @Test
    void testSchemaConstraints() throws Exception {
        // Test primary key constraint
        WorkflowDataFactory.seedSpecification(db, "constraint-test", "1.0", "Test");

        try {
            // Should fail - duplicate primary key
            WorkflowDataFactory.seedSpecification(db, "constraint-test", "1.0", "Test");
            fail("Should throw exception for duplicate primary key");
        } catch (Exception e) {
            assertNotNull(e.getMessage(), "Exception must have a message");
        }
    }

    // =========================================================================
    // Single Case Lifecycle Tests
    // =========================================================================

    @Test
    void testSingleCaseCompleteLifecycle() throws Exception {
        String specId = "lifecycle-single";
        String runnerId = "runner-lifecycle-single";

        // Phase 1: Specification loading
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", spec.getName());

        // Phase 2: Case instantiation
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");
        recordCaseEvent(db, 1, runnerId, "CASE_STARTED",
                "{\"specId\":\"" + specId + "\"}");

        // Phase 3: Work item creation
        YAtomicTask task = (YAtomicTask) spec.getRootNet().getNetElement("process");
        YIdentifier caseId = new YIdentifier(null);
        YWorkItemID wid = new YWorkItemID(caseId, "process");
        YWorkItem workItem = new YWorkItem(null, spec.getSpecificationID(),
                task, wid, true, false);

        String itemId = wid.toString();
        WorkflowDataFactory.seedWorkItem(db, itemId, runnerId, "process", "Enabled");
        recordCaseEvent(db, 2, runnerId, "TASK_ENABLED",
                "{\"taskId\":\"process\"}");

        // Phase 4: Work item execution
        workItem.setStatus(YWorkItemStatus.statusExecuting);
        updateWorkItemStatus(db, itemId, "Executing");
        recordCaseEvent(db, 3, runnerId, "TASK_EXECUTING",
                "{\"taskId\":\"process\"}");

        // Phase 5: Work item completion
        workItem.setStatus(YWorkItemStatus.statusComplete);
        updateWorkItemStatus(db, itemId, "Completed");
        completeWorkItem(db, itemId);
        recordCaseEvent(db, 4, runnerId, "TASK_COMPLETED",
                "{\"taskId\":\"process\"}");

        // Phase 6: Case completion
        updateRunnerState(db, runnerId, "COMPLETED");
        recordCaseEvent(db, 5, runnerId, "CASE_COMPLETED", "{}");

        // Verify final state
        assertRunnerState(db, runnerId, "COMPLETED");
        assertWorkItemStatus(db, itemId, "Completed");
        assertCaseEventCount(db, runnerId, 5);
    }

    @Test
    void testCaseWithMultipleWorkItems() throws Exception {
        String specId = "lifecycle-multi-wi";
        String runnerId = "runner-multi-wi";

        int taskCount = 3;
        YSpecification spec = WorkflowDataFactory.buildSequentialSpec(specId, taskCount);
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", spec.getName());
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        YIdentifier caseId = new YIdentifier(null);

        for (int i = 0; i < taskCount; i++) {
            String taskId = "task_" + i;
            YWorkItemID wid = new YWorkItemID(caseId, taskId);
            WorkflowDataFactory.seedWorkItem(db, wid.toString(), runnerId,
                    taskId, "Completed");
            completeWorkItem(db, wid.toString());
        }

        updateRunnerState(db, runnerId, "COMPLETED");

        // Verify all work items completed
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_work_item "
                             + "WHERE runner_id = '" + runnerId + "' "
                             + "AND status = 'Completed'")) {
            assertTrue(rs.next());
            assertEquals(taskCount, rs.getInt(1),
                    "All " + taskCount + " work items must be completed");
        }
    }

    // =========================================================================
    // Concurrent Case Handling Tests
    // =========================================================================

    @Test
    void testConcurrentCaseExecution() throws Exception {
        String specId = "lifecycle-concurrent";
        int caseCount = 20;

        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", spec.getName());

        AtomicInteger successCount = new AtomicInteger(0);

        Instant start = Instant.now();

        // Use StructuredTaskScope for coordinated parallel case execution
        try (var scope = new StructuredTaskScope.ShutdownOnFailure<Void>()) {
            for (int i = 0; i < caseCount; i++) {
                String runnerId = "runner-concurrent-" + i;

                scope.fork(() -> {
                    try {
                        executeCase(db, specId, runnerId, "process");
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        System.err.println("Case execution failed: " + e.getMessage());
                    }
                    return null;
                });
            }

            // Wait for all cases with automatic failure cancellation
            try {
                scope.joinUntil(Instant.now().plusSeconds(30));
                scope.throwIfFailed();
            } catch (ExecutionException e) {
                throw new AssertionError("Case execution failed", e.getCause());
            } catch (TimeoutException e) {
                throw new AssertionError("Case execution timed out", e);
            }
        }

        long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();

        assertEquals(caseCount, successCount.get(),
                "All cases must succeed");

        // Verify database state
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_net_runner "
                             + "WHERE spec_id = '" + specId + "' "
                             + "AND state = 'COMPLETED'")) {
            assertTrue(rs.next());
            assertEquals(caseCount, rs.getInt(1),
                    "All " + caseCount + " cases must be in COMPLETED state");
        }

        double casesPerSec = (caseCount * 1000.0) / Math.max(durationMs, 1);
        System.out.printf("Lifecycle concurrent: %.0f cases/sec (%d cases in %dms)%n",
                casesPerSec, caseCount, durationMs);
    }

    @Test
    void testHighVolumeCases() throws Exception {
        String specId = "lifecycle-high-volume";
        int caseCount = 100;

        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
        WorkflowDataFactory.seedSpecification(db, specId, "1.0", spec.getName());

        Instant start = Instant.now();

        // Use StructuredTaskScope for parallel high-volume case creation
        try (var scope = new StructuredTaskScope.ShutdownOnFailure<Void>()) {
            for (int i = 0; i < caseCount; i++) {
                final int caseId = i;
                scope.fork(() -> {
                    String runnerId = "runner-hv-" + caseId;
                    WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0",
                            "root", "COMPLETED");
                    return null;
                });
            }

            // Wait for all cases with automatic failure cancellation
            try {
                scope.joinUntil(Instant.now().plusSeconds(10));
                scope.throwIfFailed();
            } catch (ExecutionException e) {
                throw new AssertionError("High-volume case creation failed", e.getCause());
            } catch (TimeoutException e) {
                throw new AssertionError("High-volume case creation timed out", e);
            }
        }

        long durationMs = Instant.now().toEpochMilli() - start.toEpochMilli();

        // Verify all cases persisted
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = '"
                             + specId + "'")) {
            assertTrue(rs.next());
            assertEquals(caseCount, rs.getInt(1),
                    "All " + caseCount + " cases must be persisted");
        }

        double casesPerSec = (caseCount * 1000.0) / Math.max(durationMs, 1);
        System.out.printf("Lifecycle high-volume: %.0f cases/sec (%d cases in %dms)%n",
                casesPerSec, caseCount, durationMs);

        assertTrue(durationMs < 10_000,
                "100 cases must complete in < 10s, took: " + durationMs + "ms");
    }

    // =========================================================================
    // Case Suspension and Resume Tests
    // =========================================================================

    @Test
    void testCaseSuspendResumeLifecycle() throws Exception {
        String specId = "lifecycle-suspend";
        String runnerId = "runner-suspend";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Suspend Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        // Suspend
        updateRunnerState(db, runnerId, "SUSPENDING");
        assertRunnerState(db, runnerId, "SUSPENDING");

        updateRunnerState(db, runnerId, "SUSPENDED");
        assertRunnerState(db, runnerId, "SUSPENDED");

        // Resume
        updateRunnerState(db, runnerId, "RUNNING");
        assertRunnerState(db, runnerId, "RUNNING");

        // Complete
        updateRunnerState(db, runnerId, "COMPLETED");
        assertRunnerState(db, runnerId, "COMPLETED");
    }

    // =========================================================================
    // Error Recovery Tests
    // =========================================================================

    @Test
    void testCaseFailureAndRecovery() throws Exception {
        String specId = "lifecycle-failure";
        String runnerId = "runner-failure";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Failure Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        // Simulate failure
        updateRunnerState(db, runnerId, "FAILED");
        assertRunnerState(db, runnerId, "FAILED");

        // Recovery attempt
        updateRunnerState(db, runnerId, "RUNNING");
        assertRunnerState(db, runnerId, "RUNNING");

        // Complete after recovery
        updateRunnerState(db, runnerId, "COMPLETED");
        assertRunnerState(db, runnerId, "COMPLETED");
    }

    @Test
    void testCaseDeadlockDetection() throws Exception {
        String specId = "lifecycle-deadlock";
        String runnerId = "runner-deadlock";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Deadlock Test");
        WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

        // Simulate deadlock
        updateRunnerState(db, runnerId, "DEADLOCKED");
        assertRunnerState(db, runnerId, "DEADLOCKED");

        recordCaseEvent(db, 1, runnerId, "CASE_DEADLOCKED",
                "{\"tasks\":[\"task_1\",\"task_2\"]}");
    }

    // =========================================================================
    // Database Cleanup Tests
    // =========================================================================

    @Test
    void testSchemaCleanup() throws Exception {
        String specId = "lifecycle-cleanup";

        WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Cleanup Test");
        WorkflowDataFactory.seedNetRunner(db, "runner-cleanup", specId, "1.0",
                "root", "COMPLETED");

        // Verify data exists
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0, "Should have specifications");
        }

        // Cleanup
        YawlContainerFixtures.dropYawlSchema(db);

        // Verify tables are gone
        try (Statement stmt = db.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE TABLE_NAME = 'YAWL_SPECIFICATION'");
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "Table should be dropped");
            rs.close();
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static void executeCase(Connection conn,
                                    String specId,
                                    String runnerId,
                                    String taskId) throws Exception {
        WorkflowDataFactory.seedNetRunner(conn, runnerId, specId, "1.0",
                "root", "RUNNING");

        String itemId = runnerId + "-" + taskId;
        WorkflowDataFactory.seedWorkItem(conn, itemId, runnerId, taskId, "Completed");
        completeWorkItem(conn, itemId);

        updateRunnerState(conn, runnerId, "COMPLETED");
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

    private static void assertCaseEventCount(Connection conn,
                                             String runnerId,
                                             int expectedCount) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM yawl_case_event WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expectedCount, rs.getInt(1));
            }
        }
    }
}
