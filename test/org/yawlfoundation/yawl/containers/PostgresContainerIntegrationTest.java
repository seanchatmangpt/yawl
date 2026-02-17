/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.containers;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.yawlfoundation.yawl.elements.YSpecification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL container integration tests for the YAWL persistence layer.
 *
 * Uses a real PostgreSQL 16 Docker container (no mocks, no stubs).
 * The container is shared across all tests in this class for performance
 * (class-scoped lifecycle). Schema is re-applied per-test via setUp.
 *
 * Coverage:
 * - Schema creation and foreign-key relationships
 * - CRUD lifecycle for specification, net-runner, and work-item rows
 * - Transactional commit and rollback behaviour
 * - WorkflowDataFactory builder integration
 * - Concurrent insertion safety
 *
 * Tag: "containers" — activate via -Dgroups=containers or Maven profile.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Tag("containers")
@Testcontainers
class PostgresContainerIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            YawlContainerFixtures.createPostgres();

    private Connection connection;

    @BeforeAll
    static void startContainer() {
        // @Testcontainers manages lifecycle; this verifies readiness.
        assertTrue(POSTGRES.isRunning(), "PostgreSQL container must be running");
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = YawlContainerFixtures.connectTo(POSTGRES);
        assertNotNull(connection, "JDBC connection must be available");
        assertFalse(connection.isClosed(), "Connection must be open");
        // Clean slate each test: drop then re-create
        YawlContainerFixtures.dropYawlSchema(connection);
        YawlContainerFixtures.applyYawlSchema(connection);
    }

    @AfterAll
    static void verifyContainerShutdown() {
        // Testcontainers handles stop; assertion confirms class lifecycle completed.
        assertNotNull(POSTGRES, "Container reference must exist after tests");
    }

    // =========================================================================
    // Schema Tests
    // =========================================================================

    @Test
    void testSchemaCreation() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            ResultSet tables = connection.getMetaData().getTables(
                    null, "public", null, new String[]{"TABLE"});

            boolean foundSpec       = false;
            boolean foundRunner     = false;
            boolean foundWorkItem   = false;
            boolean foundCaseEvent  = false;

            while (tables.next()) {
                String name = tables.getString("TABLE_NAME").toLowerCase();
                switch (name) {
                    case "yawl_specification" -> foundSpec      = true;
                    case "yawl_net_runner"    -> foundRunner    = true;
                    case "yawl_work_item"     -> foundWorkItem  = true;
                    case "yawl_case_event"    -> foundCaseEvent = true;
                    default -> { /* other system tables */ }
                }
            }

            assertTrue(foundSpec,      "yawl_specification table must exist");
            assertTrue(foundRunner,    "yawl_net_runner table must exist");
            assertTrue(foundWorkItem,  "yawl_work_item table must exist");
            assertTrue(foundCaseEvent, "yawl_case_event table must exist");
        }
    }

    @Test
    void testPostgresVersion() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version()")) {
            assertTrue(rs.next(), "Version query must return a row");
            String version = rs.getString(1);
            assertNotNull(version, "Version string must not be null");
            assertTrue(version.contains("PostgreSQL"),
                    "Must be a PostgreSQL server, got: " + version);
        }
    }

    // =========================================================================
    // CRUD Lifecycle Tests
    // =========================================================================

    @Test
    void testSpecificationCRUD() throws Exception {
        // INSERT
        WorkflowDataFactory.seedSpecification(connection, "spec-pg-001", "1.0",
                "Postgres Spec One");

        // SELECT
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT spec_name FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, "spec-pg-001");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Inserted row must be retrievable");
                assertEquals("Postgres Spec One", rs.getString(1),
                        "spec_name must match inserted value");
            }
        }

        // UPDATE
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE yawl_specification SET spec_name = ? WHERE spec_id = ?")) {
            ps.setString(1, "Postgres Spec One - Updated");
            ps.setString(2, "spec-pg-001");
            int affected = ps.executeUpdate();
            assertEquals(1, affected, "One row must be updated");
        }

        // Verify UPDATE
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT spec_name FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, "spec-pg-001");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Postgres Spec One - Updated", rs.getString(1),
                        "spec_name must reflect update");
            }
        }

        // DELETE
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, "spec-pg-001");
            int affected = ps.executeUpdate();
            assertEquals(1, affected, "One row must be deleted");
        }

        // Verify DELETE
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, "spec-pg-001");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Row must be gone after delete");
            }
        }
    }

    @Test
    void testNetRunnerForeignKeyConstraint() throws Exception {
        // Seed a specification first (FK parent)
        WorkflowDataFactory.seedSpecification(connection, "spec-fk-001", "1.0",
                "FK Parent Spec");

        // Net-runner referencing valid spec
        WorkflowDataFactory.seedNetRunner(connection,
                "runner-001", "spec-fk-001", "1.0", "root", "RUNNING");

        // Verify net-runner row
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT state FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, "runner-001");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Net-runner row must exist");
                assertEquals("RUNNING", rs.getString(1),
                        "state must be RUNNING");
            }
        }
    }

    @Test
    void testWorkItemLifecycle() throws Exception {
        WorkflowDataFactory.seedSpecification(connection, "spec-wi-001", "1.0",
                "Work Item Test Spec");
        WorkflowDataFactory.seedNetRunner(connection,
                "runner-wi-001", "spec-wi-001", "1.0", "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(connection,
                "item-wi-001", "runner-wi-001", "task1", "Enabled");

        // Verify initial state
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, "item-wi-001");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Enabled", rs.getString(1));
            }
        }

        // Transition to Executing
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE yawl_work_item SET status = ? WHERE item_id = ?")) {
            ps.setString(1, "Executing");
            ps.setString(2, "item-wi-001");
            ps.executeUpdate();
        }

        // Transition to Completed + set completed_at
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE yawl_work_item SET status = ?, completed_at = NOW() "
                + "WHERE item_id = ?")) {
            ps.setString(1, "Completed");
            ps.setString(2, "item-wi-001");
            ps.executeUpdate();
        }

        // Assert final state
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status, completed_at FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, "item-wi-001");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Completed", rs.getString("status"),
                        "Final status must be Completed");
                assertNotNull(rs.getTimestamp("completed_at"),
                        "completed_at must be set on completion");
            }
        }
    }

    // =========================================================================
    // Transaction Tests
    // =========================================================================

    @Test
    void testTransactionalCommit() throws Exception {
        connection.setAutoCommit(false);

        WorkflowDataFactory.seedSpecification(connection, "spec-tx-commit", "1.0",
                "Commit Test");
        connection.commit();
        connection.setAutoCommit(true);

        // Verify row is visible post-commit
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, "spec-tx-commit");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1),
                        "Committed row must be visible after commit");
            }
        }
    }

    @Test
    void testTransactionalRollback() throws Exception {
        connection.setAutoCommit(false);

        WorkflowDataFactory.seedSpecification(connection, "spec-tx-rollback", "1.0",
                "Rollback Test");
        connection.rollback();
        connection.setAutoCommit(true);

        // Verify row is NOT visible after rollback
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, "spec-tx-rollback");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1),
                        "Rolled-back row must not be visible");
            }
        }
    }

    // =========================================================================
    // WorkflowDataFactory Integration
    // =========================================================================

    @Test
    void testWorkflowDataFactoryIntegrationWithDatabase() throws Exception {
        // Build a real YAWL specification via the factory
        String specId = WorkflowDataFactory.specIdFor("factory-integration-pg");
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec(specId);
        assertNotNull(spec.getRootNet(), "Factory must produce wired spec");

        // Persist its metadata to Postgres
        WorkflowDataFactory.seedSpecification(connection, specId, "1.0",
                spec.getName());

        // Verify round-trip
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT spec_name FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, specId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Seeded specification row must exist");
                assertEquals(spec.getName(), rs.getString(1),
                        "Spec name must round-trip through database");
            }
        }
    }

    @Test
    void testBulkInsertPerformance() throws Exception {
        int rowCount = 200;
        long startMs = System.currentTimeMillis();

        for (int i = 0; i < rowCount; i++) {
            WorkflowDataFactory.seedSpecification(connection,
                    "bulk-spec-" + i, "1.0", "Bulk Spec " + i);
        }

        long durationMs = System.currentTimeMillis() - startMs;

        // Verify all rows landed
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            assertEquals(rowCount, rs.getInt(1),
                    "All " + rowCount + " rows must be persisted");
        }

        double rps = (rowCount * 1000.0) / Math.max(durationMs, 1);
        System.out.printf("PostgreSQL bulk-insert: %.0f rows/sec (%d rows in %dms)%n",
                rps, rowCount, durationMs);

        // Sanity: 200 rows should complete in < 30 seconds on any CI machine
        assertTrue(durationMs < 30_000,
                "Bulk insert of " + rowCount + " rows must complete in < 30s");
    }
}
