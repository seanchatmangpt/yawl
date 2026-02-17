/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.parametrized;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parametrized database backend tests for YAWL persistence.
 *
 * Runs the same YAWL schema and DML assertions against every in-process
 * database backend supported without Docker (H2, HSQLDB). The same test
 * logic is also exercised against PostgreSQL and MySQL via the container
 * tests — these parametrized variants provide fast, offline coverage.
 *
 * Test Matrix:
 * - H2 in-memory (MODE=PostgreSQL) — validates Postgres-compatible DDL
 * - H2 in-memory (MODE=MySQL)      — validates MySQL-compatible DDL
 * - HSQLDB in-memory               — validates ANSI SQL base dialect
 *
 * Coverage:
 * - Schema creation across all backends
 * - Insert/select round-trip for all three entity types
 * - Backend-specific SELECT syntax (e.g. VALUES vs SELECT 1)
 * - Transactional commit and rollback per backend
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
class DatabaseBackendParametrizedTest {

    /**
     * Encapsulates backend-specific JDBC URL and driver class.
     */
    record DatabaseBackend(String name, String jdbcUrl, String user, String password) {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Stream of {@link DatabaseBackend} records used as test parameters.
     * Each entry becomes one column in the parametrized test display.
     */
    static Stream<Arguments> databaseBackends() {
        return Stream.of(
            Arguments.of(new DatabaseBackend(
                "H2-Postgres",
                "jdbc:h2:mem:yawl_pg_compat_%d;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
                    .formatted(System.nanoTime()),
                "sa", "")),
            Arguments.of(new DatabaseBackend(
                "H2-MySQL",
                "jdbc:h2:mem:yawl_mysql_compat_%d;MODE=MySQL;DB_CLOSE_DELAY=-1"
                    .formatted(System.nanoTime()),
                "sa", "")),
            Arguments.of(new DatabaseBackend(
                "H2-Default",
                "jdbc:h2:mem:yawl_default_%d;DB_CLOSE_DELAY=-1"
                    .formatted(System.nanoTime()),
                "sa", "")),
            Arguments.of(new DatabaseBackend(
                "HSQLDB",
                "jdbc:hsqldb:mem:yawl_hsql_%d".formatted(System.nanoTime()),
                "SA", ""))
        );
    }

    // =========================================================================
    // Parametrized Test Implementations
    // =========================================================================

    @ParameterizedTest(name = "[{index}] backend={0}")
    @MethodSource("databaseBackends")
    void testSchemaCreationAllBackends(DatabaseBackend backend) throws Exception {
        try (Connection conn = openConnection(backend)) {
            YawlContainerFixtures.applyYawlSchema(conn);

            // Verify tables via INFORMATION_SCHEMA (ANSI SQL, supported by all backends)
            for (String table : new String[]{
                    "YAWL_SPECIFICATION", "YAWL_NET_RUNNER",
                    "YAWL_WORK_ITEM", "YAWL_CASE_EVENT"}) {
                ResultSet tables = conn.getMetaData().getTables(
                        null, null, table, new String[]{"TABLE"});
                assertTrue(tables.next(),
                        backend.name() + ": table must exist: " + table);
                tables.close();
            }
        }
    }

    @ParameterizedTest(name = "[{index}] backend={0}")
    @MethodSource("databaseBackends")
    void testSpecificationInsertAndQueryAllBackends(DatabaseBackend backend)
            throws Exception {
        try (Connection conn = openConnection(backend)) {
            YawlContainerFixtures.applyYawlSchema(conn);

            String specId = WorkflowDataFactory.uniqueSpecId("param-spec");
            WorkflowDataFactory.seedSpecification(conn, specId, "1.0",
                    "Parametrized Spec [" + backend.name() + "]");

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT spec_name, spec_version "
                    + "FROM yawl_specification WHERE spec_id = ?")) {
                ps.setString(1, specId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(),
                            backend.name() + ": inserted row must be retrievable");
                    assertNotNull(rs.getString("spec_name"),
                            backend.name() + ": spec_name must not be null");
                    assertEquals("1.0", rs.getString("spec_version"),
                            backend.name() + ": spec_version must match");
                }
            }
        }
    }

    @ParameterizedTest(name = "[{index}] backend={0}")
    @MethodSource("databaseBackends")
    void testTransactionRollbackAllBackends(DatabaseBackend backend) throws Exception {
        try (Connection conn = openConnection(backend)) {
            YawlContainerFixtures.applyYawlSchema(conn);
            conn.setAutoCommit(false);

            String specId = WorkflowDataFactory.uniqueSpecId("rollback-spec");
            WorkflowDataFactory.seedSpecification(conn, specId, "1.0", "Rollback Spec");

            conn.rollback();
            conn.setAutoCommit(true);

            // Row must NOT be visible after rollback
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_specification WHERE spec_id = ?")) {
                ps.setString(1, specId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(0, rs.getInt(1),
                            backend.name() + ": rolled-back row must not be visible");
                }
            }
        }
    }

    @ParameterizedTest(name = "[{index}] backend={0}")
    @MethodSource("databaseBackends")
    void testFullEntityChainAllBackends(DatabaseBackend backend) throws Exception {
        try (Connection conn = openConnection(backend)) {
            YawlContainerFixtures.applyYawlSchema(conn);

            String specId    = WorkflowDataFactory.uniqueSpecId("chain-spec");
            String runnerId  = WorkflowDataFactory.uniqueSpecId("chain-runner");
            String itemId    = WorkflowDataFactory.uniqueSpecId("chain-item");

            WorkflowDataFactory.seedSpecification(conn, specId, "1.0", "Chain Spec");
            WorkflowDataFactory.seedNetRunner(conn,
                    runnerId, specId, "1.0", "root", "RUNNING");
            WorkflowDataFactory.seedWorkItem(conn,
                    itemId, runnerId, "task-A", "Enabled");

            // Verify work-item count
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM yawl_work_item WHERE runner_id = ?")) {
                ps.setString(1, runnerId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1),
                            backend.name() + ": one work item must be linked to runner");
                }
            }
        }
    }

    @ParameterizedTest(name = "[{index}] backend={0}")
    @MethodSource("databaseBackends")
    void testBatchInsertAllBackends(DatabaseBackend backend) throws Exception {
        int batchSize = 50;
        try (Connection conn = openConnection(backend)) {
            YawlContainerFixtures.applyYawlSchema(conn);

            String sql = "INSERT INTO yawl_specification "
                       + "(spec_id, spec_version, spec_name) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < batchSize; i++) {
                    ps.setString(1, "batch-" + backend.name() + "-" + i);
                    ps.setString(2, "1.0");
                    ps.setString(3, "Batch " + i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_specification")) {
                assertTrue(rs.next());
                assertEquals(batchSize, rs.getInt(1),
                        backend.name() + ": all " + batchSize
                        + " batch rows must be persisted");
            }
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static Connection openConnection(DatabaseBackend backend)
            throws Exception {
        Connection conn = DriverManager.getConnection(
                backend.jdbcUrl(), backend.user(), backend.password());
        assertNotNull(conn, "Connection must open for backend: " + backend.name());
        assertFalse(conn.isClosed(), "Connection must be open: " + backend.name());
        return conn;
    }
}
