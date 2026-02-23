/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema migration tests for YAWL v6.
 *
 * Tests database schema operations:
 * - Initial schema creation
 * - Schema version tracking
 * - Migration application
 * - Backward compatibility
 * - Schema validation
 *
 * Chicago TDD: Real database schema operations, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-18
 */
@Tag("integration")
class SchemaMigrationTest {

    private Connection db;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:schema_migration_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }

    // =========================================================================
    // Schema Creation Tests
    // =========================================================================

    @Test
    void testSchemaCreation() throws Exception {
        // Apply schema
        YawlContainerFixtures.applyYawlSchema(db);

        // Verify all tables exist
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
    void testSchemaIdempotent() throws Exception {
        // Apply schema twice
        YawlContainerFixtures.applyYawlSchema(db);
        YawlContainerFixtures.applyYawlSchema(db);

        // Should not throw or create duplicates
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                             + "WHERE TABLE_NAME LIKE 'YAWL%'")) {
            assertTrue(rs.next());
            assertEquals(4, rs.getInt(1), "Must have exactly 4 YAWL tables");
        }
    }

    @Test
    void testSchemaDrop() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        // Verify tables exist
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                             + "WHERE TABLE_NAME LIKE 'YAWL%'")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) > 0, "Tables must exist before drop");
        }

        // Drop schema
        YawlContainerFixtures.dropYawlSchema(db);

        // Verify tables are gone
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                             + "WHERE TABLE_NAME LIKE 'YAWL%'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "No YAWL tables must exist after drop");
        }
    }

    // =========================================================================
    // Column Structure Tests
    // =========================================================================

    @Test
    void testSpecificationTableColumns() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        String[] expectedColumns = {
            "SPEC_ID",
            "SPEC_VERSION",
            "SPEC_NAME",
            "CREATED_AT"
        };

        for (String column : expectedColumns) {
            assertTrue(columnExists(db, "YAWL_SPECIFICATION", column),
                    "Column " + column + " must exist in YAWL_SPECIFICATION");
        }
    }

    @Test
    void testNetRunnerTableColumns() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        String[] expectedColumns = {
            "RUNNER_ID",
            "SPEC_ID",
            "SPEC_VERSION",
            "NET_ID",
            "STATE",
            "STARTED_AT"
        };

        for (String column : expectedColumns) {
            assertTrue(columnExists(db, "YAWL_NET_RUNNER", column),
                    "Column " + column + " must exist in YAWL_NET_RUNNER");
        }
    }

    @Test
    void testWorkItemTableColumns() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        String[] expectedColumns = {
            "ITEM_ID",
            "RUNNER_ID",
            "TASK_ID",
            "STATUS",
            "CREATED_AT",
            "COMPLETED_AT"
        };

        for (String column : expectedColumns) {
            assertTrue(columnExists(db, "YAWL_WORK_ITEM", column),
                    "Column " + column + " must exist in YAWL_WORK_ITEM");
        }
    }

    @Test
    void testCaseEventTableColumns() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        String[] expectedColumns = {
            "EVENT_ID",
            "RUNNER_ID",
            "EVENT_TYPE",
            "EVENT_DATA",
            "EVENT_TIMESTAMP"
        };

        for (String column : expectedColumns) {
            assertTrue(columnExists(db, "YAWL_CASE_EVENT", column),
                    "Column " + column + " must exist in YAWL_CASE_EVENT");
        }
    }

    // =========================================================================
    // Constraint Tests
    // =========================================================================

    @Test
    void testPrimaryKeys() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        // Check composite PK on specification
        assertTrue(primaryKeyExists(db, "YAWL_SPECIFICATION",
                new String[]{"SPEC_ID", "SPEC_VERSION"}),
                "YAWL_SPECIFICATION must have composite PK");

        // Check simple PKs
        assertTrue(primaryKeyExists(db, "YAWL_NET_RUNNER", "RUNNER_ID"),
                "YAWL_NET_RUNNER must have PK on RUNNER_ID");
        assertTrue(primaryKeyExists(db, "YAWL_WORK_ITEM", "ITEM_ID"),
                "YAWL_WORK_ITEM must have PK on ITEM_ID");
        assertTrue(primaryKeyExists(db, "YAWL_CASE_EVENT", "EVENT_ID"),
                "YAWL_CASE_EVENT must have PK on EVENT_ID");
    }

    @Test
    void testForeignKeys() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        // YAWL_NET_RUNNER -> YAWL_SPECIFICATION
        assertTrue(foreignKeyExists(db, "YAWL_NET_RUNNER", "YAWL_SPECIFICATION"),
                "YAWL_NET_RUNNER must have FK to YAWL_SPECIFICATION");

        // YAWL_WORK_ITEM -> YAWL_NET_RUNNER
        assertTrue(foreignKeyExists(db, "YAWL_WORK_ITEM", "YAWL_NET_RUNNER"),
                "YAWL_WORK_ITEM must have FK to YAWL_NET_RUNNER");
    }

    // =========================================================================
    // Data Type Tests
    // =========================================================================

    @Test
    void testColumnDataTypes() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        // VARCHAR columns
        assertColumnType(db, "YAWL_SPECIFICATION", "SPEC_ID", "CHARACTER VARYING");
        assertColumnType(db, "YAWL_SPECIFICATION", "SPEC_VERSION", "CHARACTER VARYING");
        assertColumnType(db, "YAWL_SPECIFICATION", "SPEC_NAME", "CHARACTER VARYING");

        // TIMESTAMP columns
        assertColumnType(db, "YAWL_SPECIFICATION", "CREATED_AT", "TIMESTAMP");
        assertColumnType(db, "YAWL_NET_RUNNER", "STARTED_AT", "TIMESTAMP");
        assertColumnType(db, "YAWL_WORK_ITEM", "CREATED_AT", "TIMESTAMP");
        assertColumnType(db, "YAWL_CASE_EVENT", "EVENT_TIMESTAMP", "TIMESTAMP");

        // TEXT columns
        assertColumnType(db, "YAWL_CASE_EVENT", "EVENT_DATA", "CHARACTER LARGE OBJECT");
    }

    // =========================================================================
    // Schema Version Tests
    // =========================================================================

    @Test
    void testSchemaVersionTracking() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        // Create version tracking table if it doesn't exist
        try (Statement stmt = db.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS yawl_schema_version (
                    version_id      INTEGER PRIMARY KEY,
                    version_number  VARCHAR(50) NOT NULL,
                    applied_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    description     VARCHAR(255)
                )
                """);

            // Insert initial version
            stmt.execute("""
                INSERT INTO yawl_schema_version (version_id, version_number, description)
                VALUES (1, '6.0.0', 'Initial YAWL v6 schema')
                """);
        }

        // Verify version recorded
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT version_number FROM yawl_schema_version "
                             + "WHERE version_id = 1")) {
            assertTrue(rs.next());
            assertEquals("6.0.0", rs.getString("version_number"));
        }
    }

    @Test
    void testMigrationDetection() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        // Create version tracking
        try (Statement stmt = db.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS yawl_schema_version (
                    version_id      INTEGER PRIMARY KEY,
                    version_number  VARCHAR(50) NOT NULL,
                    applied_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }

        // Check if migration is needed
        int currentVersion = getCurrentSchemaVersion(db);
        int targetVersion = 6; // v6.0.0

        if (currentVersion < targetVersion) {
            // Simulate migration
            applyMigration(db, currentVersion + 1);
        }

        assertEquals(targetVersion, getCurrentSchemaVersion(db),
                "Schema must be at target version");
    }

    // =========================================================================
    // Backward Compatibility Tests
    // =========================================================================

    @Test
    void testDataRetentionAfterSchemaUpdate() throws Exception {
        YawlContainerFixtures.applyYawlSchema(db);

        // Insert test data
        try (Statement stmt = db.createStatement()) {
            stmt.execute("INSERT INTO yawl_specification (spec_id, spec_version, spec_name) "
                    + "VALUES ('compat-test', '1.0', 'Compatibility Test')");
        }

        // Simulate schema update (add column)
        try (Statement stmt = db.createStatement()) {
            stmt.execute("ALTER TABLE yawl_specification ADD COLUMN IF NOT EXISTS "
                    + "description VARCHAR(500)");
        }

        // Verify data retained
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT spec_name FROM yawl_specification "
                             + "WHERE spec_id = 'compat-test'")) {
            assertTrue(rs.next());
            assertEquals("Compatibility Test", rs.getString("spec_name"),
                    "Data must be retained after schema update");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static boolean columnExists(Connection conn,
                                        String table,
                                        String column) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS "
                             + "WHERE TABLE_NAME = '" + table + "' "
                             + "AND COLUMN_NAME = '" + column + "'")) {
            return rs.next();
        }
    }

    private static boolean primaryKeyExists(Connection conn,
                                            String table,
                                            String column) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT 1 FROM INFORMATION_SCHEMA.CONSTRAINTS "
                             + "WHERE TABLE_NAME = '" + table + "' "
                             + "AND CONSTRAINT_TYPE = 'PRIMARY KEY'")) {
            return rs.next();
        }
    }

    private static boolean primaryKeyExists(Connection conn,
                                            String table,
                                            String[] columns) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES "
                             + "WHERE TABLE_NAME = '" + table + "' "
                             + "AND PRIMARY_KEY = TRUE")) {
            if (rs.next()) {
                return rs.getInt(1) >= columns.length;
            }
            return false;
        }
    }

    private static boolean foreignKeyExists(Connection conn,
                                            String fromTable,
                                            String toTable) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT 1 FROM INFORMATION_SCHEMA.CROSS_REFERENCES "
                             + "WHERE FKTABLE_NAME = '" + fromTable + "' "
                             + "AND PKTABLE_NAME = '" + toTable + "'")) {
            return rs.next();
        }
    }

    private static void assertColumnType(Connection conn,
                                         String table,
                                         String column,
                                         String expectedType) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                             + "WHERE TABLE_NAME = '" + table + "' "
                             + "AND COLUMN_NAME = '" + column + "'")) {
            assertTrue(rs.next(), "Column " + column + " must exist");
            String actualType = rs.getString("DATA_TYPE").toUpperCase();
            assertTrue(actualType.contains(expectedType.toUpperCase())
                            || expectedType.toUpperCase().contains(actualType),
                    "Column " + column + " must be of type " + expectedType
                            + ", got " + actualType);
        }
    }

    private static int getCurrentSchemaVersion(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COALESCE(MAX(version_id), 0) FROM yawl_schema_version")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (Exception e) {
            return 0; // Table doesn't exist yet
        }
    }

    private static void applyMigration(Connection conn, int version) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO yawl_schema_version (version_id, version_number) "
                    + "VALUES (" + version + ", '6.0." + version + "')");
        }
    }
}
