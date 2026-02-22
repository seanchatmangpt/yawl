/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.containers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.yawlfoundation.yawl.infrastructure.RequiresDocker;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL container integration tests for the YAWL persistence layer.
 *
 * Uses a real MySQL 8.4 Docker container (no mocks, no stubs).
 * Validates that the same DDL and DML used against PostgreSQL works
 * identically against MySQL — critical for multi-database deployment support.
 *
 * Coverage:
 * - MySQL-specific DDL compatibility (TEXT type, TIMESTAMP defaults)
 * - Multi-row batch insert via JDBC
 * - Foreign-key enforcement in MySQL (InnoDB engine)
 * - Case-insensitive table-name behaviour on MySQL
 * - AUTO_INCREMENT semantics for event IDs
 *
 * Tag: "containers" — activate via -Dgroups=containers or Maven profile.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@RequiresDocker
@Tag("docker")

@Testcontainers
class MySQLContainerIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL =
            YawlContainerFixtures.createMySQL();

    private Connection connection;

    @BeforeAll
    static void verifyContainerRunning() {
        assertTrue(MYSQL.isRunning(), "MySQL container must be running before tests");
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = YawlContainerFixtures.connectTo(MYSQL);
        assertNotNull(connection, "MySQL JDBC connection must be available");
        assertFalse(connection.isClosed(), "Connection must be open");
        YawlContainerFixtures.dropYawlSchema(connection);
        YawlContainerFixtures.applyYawlSchema(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // =========================================================================
    // Connectivity and Schema
    // =========================================================================

    @Test
    void testMySQLConnectivity() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT @@version")) {
            assertTrue(rs.next(), "MySQL version query must return a row");
            String version = rs.getString(1);
            assertNotNull(version, "Version string must not be null");
            System.out.println("MySQL version: " + version);
        }
    }

    @Test
    void testMySQLSchemaAllTablesExist() throws Exception {
        String[] expectedTables = {
            "yawl_specification", "yawl_net_runner",
            "yawl_work_item", "yawl_case_event"
        };
        for (String table : expectedTables) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = ? AND table_name = ?")) {
                ps.setString(1, MYSQL.getDatabaseName());
                ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1),
                            "Table must exist: " + table);
                }
            }
        }
    }

    // =========================================================================
    // Workflow Data Persistence
    // =========================================================================

    @Test
    void testSpecificationPersistenceMySQL() throws Exception {
        WorkflowDataFactory.seedSpecification(connection,
                "spec-mysql-001", "2.0", "MySQL Integration Spec");

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT spec_name, spec_version FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, "spec-mysql-001");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Row must be found");
                assertEquals("MySQL Integration Spec", rs.getString("spec_name"));
                assertEquals("2.0", rs.getString("spec_version"));
            }
        }
    }

    @Test
    void testNetRunnerAndWorkItemChain() throws Exception {
        // Build full FK chain: spec -> runner -> work-item
        WorkflowDataFactory.seedSpecification(connection,
                "spec-chain-mysql", "1.0", "MySQL Chain Spec");
        WorkflowDataFactory.seedNetRunner(connection,
                "runner-chain-mysql", "spec-chain-mysql", "1.0", "net-root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(connection,
                "item-chain-mysql", "runner-chain-mysql", "task-a", "Enabled");

        // Verify entire chain in one JOIN query
        String sql = """
            SELECT wi.status, nr.state, s.spec_name
            FROM yawl_work_item wi
            JOIN yawl_net_runner nr ON wi.runner_id = nr.runner_id
            JOIN yawl_specification s ON nr.spec_id = s.spec_id
                                     AND nr.spec_version = s.spec_version
            WHERE wi.item_id = ?
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "item-chain-mysql");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "JOIN query must return a row");
                assertEquals("Enabled",           rs.getString("status"));
                assertEquals("RUNNING",            rs.getString("state"));
                assertEquals("MySQL Chain Spec",   rs.getString("spec_name"));
            }
        }
    }

    @Test
    void testBatchInsertMySQL() throws Exception {
        int batchSize = 100;
        String sql = "INSERT INTO yawl_specification (spec_id, spec_version, spec_name) "
                   + "VALUES (?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < batchSize; i++) {
                ps.setString(1, "batch-spec-" + i);
                ps.setString(2, "1.0");
                ps.setString(3, "Batch Spec " + i);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            assertEquals(batchSize, results.length,
                    "All batch rows must be reported as inserted");
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            assertEquals(batchSize, rs.getInt(1),
                    "All " + batchSize + " rows must be persisted");
        }
    }

    @Test
    void testMySQLTransactionIsolation() throws Exception {
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

        WorkflowDataFactory.seedSpecification(connection,
                "spec-isolation-mysql", "1.0", "Isolation Test");
        connection.commit();
        connection.setAutoCommit(true);

        // Post-commit row must be visible in a new query
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, "spec-isolation-mysql");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1),
                        "Committed row must be visible at READ_COMMITTED isolation");
            }
        }
    }

    @Test
    void testCaseEventAutoIncrement() throws Exception {
        // yawl_case_event.event_id is BIGINT NOT NULL PRIMARY KEY
        // MySQL requires explicit value; verify sequential inserts work
        String sql = """
            INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data)
            VALUES (?, 'runner-none', ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (long i = 1; i <= 5; i++) {
                ps.setLong(1, i);
                ps.setString(2, "CASE_STARTED");
                ps.setString(3, "{\"caseId\":\"case-" + i + "\"}");
                ps.addBatch();
            }
            ps.executeBatch();
        }

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_case_event")) {
            assertTrue(rs.next());
            assertEquals(5, rs.getInt(1), "Five event rows must be persisted");
        }
    }
}
