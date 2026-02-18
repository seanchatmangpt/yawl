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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test data seeding pattern demonstrations for YAWL integration tests.
 *
 * Documents and validates five canonical data seeding patterns used across
 * the YAWL test suite. Each pattern is tested in isolation so teams can
 * reference the code as working examples.
 *
 * Patterns:
 * 1. Minimal seed:       One spec, no dependents — fastest, for unit-like tests
 * 2. Relational chain:   Spec -> Runner -> WorkItem — FK chain for join tests
 * 3. Bulk seed:          N specs via batch insert — for volume/perf tests
 * 4. Transactional seed: All-or-nothing via JDBC transaction
 * 5. Isolated seed:      Each test gets its own DB URL — no shared state
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Tag("integration")
class TestDataSeedingPatterns {

    private Connection db;

    @BeforeEach
    void setUp() throws Exception {
        db = openFreshDb("seeding");
        YawlContainerFixtures.applyYawlSchema(db);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }

    // =========================================================================
    // Pattern 1: Minimal Seed
    // =========================================================================

    @Test
    void testPattern1_MinimalSeed() throws Exception {
        // Pattern: seed only what the test requires — nothing more.
        // Use-case: verifying spec INSERT/SELECT without FK complexity.

        WorkflowDataFactory.seedSpecification(db, "minimal-001", "1.0", "Minimal Spec");

        assertRowCount(db, "yawl_specification", 1);
        assertRowCount(db, "yawl_net_runner", 0);
        assertRowCount(db, "yawl_work_item", 0);
    }

    // =========================================================================
    // Pattern 2: Relational Chain Seed
    // =========================================================================

    @Test
    void testPattern2_RelationalChainSeed() throws Exception {
        // Pattern: build FK chain (parent -> child -> grandchild) before testing
        // join queries or cascaded deletes.
        // Use-case: verifying JOIN queries return correct data.

        WorkflowDataFactory.seedSpecification(db, "chain-spec", "1.0", "Chain Spec");
        WorkflowDataFactory.seedNetRunner(db, "chain-runner", "chain-spec", "1.0",
                "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, "chain-item", "chain-runner",
                "task-A", "Enabled");

        // Verify full chain via JOIN
        String sql = """
            SELECT wi.status, nr.state, s.spec_name
            FROM yawl_work_item wi
            JOIN yawl_net_runner nr ON wi.runner_id = nr.runner_id
            JOIN yawl_specification s
                ON nr.spec_id = s.spec_id AND nr.spec_version = s.spec_version
            WHERE wi.item_id = 'chain-item'
            """;
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next(), "JOIN query must return a row");
            assertEquals("Enabled",    rs.getString("status"));
            assertEquals("RUNNING",    rs.getString("state"));
            assertEquals("Chain Spec", rs.getString("spec_name"));
        }
    }

    // =========================================================================
    // Pattern 3: Bulk Seed
    // =========================================================================

    @Test
    void testPattern3_BulkSeed() throws Exception {
        // Pattern: insert N rows via prepared statement batch for volume tests.
        // Use-case: verifying COUNT(*), pagination, or performance baselines.

        int count = 50;
        String sql = "INSERT INTO yawl_specification (spec_id, spec_version, spec_name) "
                   + "VALUES (?, ?, ?)";

        try (PreparedStatement ps = db.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                ps.setString(1, "bulk-spec-" + i);
                ps.setString(2, "1.0");
                ps.setString(3, "Bulk Spec " + i);
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            assertEquals(count, results.length,
                    "All " + count + " rows must be reported as inserted");
        }

        assertRowCount(db, "yawl_specification", count);
    }

    // =========================================================================
    // Pattern 4: Transactional Seed
    // =========================================================================

    @Test
    void testPattern4_TransactionalSeed() throws Exception {
        // Pattern: wrap seed in a transaction so all rows land or none do.
        // Use-case: tests that must verify atomicity of multi-row setup.

        db.setAutoCommit(false);
        try {
            WorkflowDataFactory.seedSpecification(db, "tx-spec", "1.0", "TX Spec");
            WorkflowDataFactory.seedNetRunner(db, "tx-runner", "tx-spec", "1.0",
                    "root", "RUNNING");
            WorkflowDataFactory.seedWorkItem(db, "tx-item", "tx-runner",
                    "task-X", "Enabled");
            db.commit();
        } catch (Exception e) {
            db.rollback();
            throw e;
        } finally {
            db.setAutoCommit(true);
        }

        // All three rows must be present after commit
        assertRowCount(db, "yawl_specification", 1);
        assertRowCount(db, "yawl_net_runner",    1);
        assertRowCount(db, "yawl_work_item",     1);
    }

    @Test
    void testPattern4_TransactionalSeedRollback() throws Exception {
        // Pattern complement: rollback path must leave DB empty.

        db.setAutoCommit(false);

        WorkflowDataFactory.seedSpecification(db, "tx-rollback", "1.0", "TX Rollback");
        db.rollback();
        db.setAutoCommit(true);

        assertRowCount(db, "yawl_specification", 0);
    }

    // =========================================================================
    // Pattern 5: Isolated Seed (per-test independent DB)
    // =========================================================================

    @Test
    void testPattern5_IsolatedSeedNoSharedState() throws Exception {
        // Pattern: each test opens its own in-memory DB so there is no
        // shared state between tests even when run in parallel.
        // Use-case: avoiding DB_CLOSE_DELAY=-1 state leakage between tests.

        Connection db1 = openFreshDb("isolated-1");
        Connection db2 = openFreshDb("isolated-2");

        try {
            YawlContainerFixtures.applyYawlSchema(db1);
            YawlContainerFixtures.applyYawlSchema(db2);

            WorkflowDataFactory.seedSpecification(db1, "iso-spec", "1.0", "DB1 Spec");
            // db2 has no rows

            assertRowCount(db1, "yawl_specification", 1);
            assertRowCount(db2, "yawl_specification", 0); // fully isolated
        } finally {
            db1.close();
            db2.close();
        }
    }

    // =========================================================================
    // Pattern: Parameterized Seed Builder
    // =========================================================================

    @Test
    void testParameterizedSeedBuilder() throws Exception {
        // Pattern: build a seed plan as a list of lambdas, apply in order.
        // Use-case: reusable test setup that can be composed at the call site.

        List<Consumer<Connection>> seedPlan = new ArrayList<>();
        seedPlan.add(c -> {
            try {
                WorkflowDataFactory.seedSpecification(c, "builder-spec", "1.0", "Builder");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        seedPlan.add(c -> {
            try {
                WorkflowDataFactory.seedNetRunner(c, "builder-runner",
                        "builder-spec", "1.0", "root", "RUNNING");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Apply the plan
        for (Consumer<Connection> step : seedPlan) {
            step.accept(db);
        }

        assertRowCount(db, "yawl_specification", 1);
        assertRowCount(db, "yawl_net_runner",    1);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static Connection openFreshDb(String label) throws Exception {
        String url = "jdbc:h2:mem:seed_%s_%d;DB_CLOSE_DELAY=-1"
                .formatted(label, System.nanoTime());
        Connection conn = DriverManager.getConnection(url, "sa", "");
        assertNotNull(conn, "Connection must open: " + label);
        assertFalse(conn.isClosed(), "Connection must be open: " + label);
        return conn;
    }

    private static void assertRowCount(Connection conn,
                                        String tableName,
                                        int expected) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM " + tableName)) {
            assertTrue(rs.next());
            assertEquals(expected, rs.getInt(1),
                    "Table " + tableName + " must have " + expected + " rows");
        }
    }
}
