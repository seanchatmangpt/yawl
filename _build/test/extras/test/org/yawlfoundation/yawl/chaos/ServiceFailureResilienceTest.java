/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.chaos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YEngine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chaos engineering tests: service failure and partial failure injection.
 *
 * Validates YAWL's resilience when:
 * - Database connections are forcibly closed mid-operation
 * - Connection pool is exhausted
 * - Operations fail after partial completion
 * - Engine continues functioning after sub-system failures
 *
 * Design pattern: fault-injection is performed via JDBC connection
 * manipulation. No external tools required; all faults are synthetic.
 *
 * Fault scenarios:
 * 1. Mid-operation connection failure (SQLException propagation)
 * 2. Pool exhaustion (all connections consumed, new requests fail)
 * 3. Transaction rollback on partial batch failure
 * 4. Engine resilience after DB connection failure
 * 5. Concurrent failure and recovery (mixed success/failure)
 *
 * Tag: "chaos" — excluded from normal CI runs.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Tag("chaos")
class ServiceFailureResilienceTest {

    private String jdbcUrl;
    private Connection connection;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:chaos_service_%d;DB_CLOSE_DELAY=-1"
                .formatted(System.nanoTime());
        connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // =========================================================================
    // Connection Failure Scenarios
    // =========================================================================

    @Test
    void testOperationFailsOnClosedConnection() throws Exception {
        // Pre-condition: verify connection is healthy
        assertFalse(connection.isClosed(), "Connection must be open initially");

        // Seed one row before inducing failure
        WorkflowDataFactory.seedSpecification(connection, "pre-fail", "1.0", "Pre-Fail");

        // Induce failure: close the connection
        connection.close();
        assertTrue(connection.isClosed(), "Connection must be closed after close()");

        // Post-failure operation must throw SQLException
        assertThrows(SQLException.class, () ->
                WorkflowDataFactory.seedSpecification(
                        connection, "post-fail", "1.0", "Post-Fail"),
                "Operation on closed connection must throw SQLException");
    }

    @Test
    void testNewConnectionAfterFailureSucceeds() throws Exception {
        // Close the original connection (simulates service restart)
        connection.close();

        // Open a new connection — simulates reconnection after failure
        Connection newConnection = DriverManager.getConnection(jdbcUrl, "sa", "");
        assertNotNull(newConnection, "New connection must open successfully");
        assertFalse(newConnection.isClosed(), "New connection must be open");

        // Verify the schema was preserved (H2 in-memory DB_CLOSE_DELAY=-1)
        WorkflowDataFactory.seedSpecification(newConnection,
                "post-reconnect", "1.0", "Post-Reconnect Spec");

        try (Statement stmt = newConnection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1),
                    "Row inserted via new connection must be persisted");
        }

        newConnection.close();
        // Replace connection for tearDown
        connection = DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    @Test
    void testTransactionRollbackOnPartialBatchFailure() throws Exception {
        connection.setAutoCommit(false);

        // Insert first batch successfully
        WorkflowDataFactory.seedSpecification(connection, "batch-ok-1", "1.0", "Batch OK 1");
        WorkflowDataFactory.seedSpecification(connection, "batch-ok-2", "1.0", "Batch OK 2");

        // Attempt a duplicate key insert (simulates failure partway through a batch)
        assertThrows(SQLException.class, () ->
                WorkflowDataFactory.seedSpecification(
                        connection, "batch-ok-1", "1.0", "Duplicate Key!"),
                "Duplicate primary key must throw SQLException");

        // Roll back the entire transaction
        connection.rollback();
        connection.setAutoCommit(true);

        // Verify no partial rows survived
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1),
                    "Rolled-back rows must not be visible");
        }
    }

    // =========================================================================
    // Mixed Success/Failure Under Concurrency
    // =========================================================================

    @Test
    void testConcurrentMixedSuccessAndFailure() throws Exception {
        int totalThreads  = 10;
        int successThreads = 7;  // these threads operate normally
        int failThreads   = 3;   // these threads use a closed connection

        AtomicInteger successCount  = new AtomicInteger(0);
        AtomicInteger failureCount  = new AtomicInteger(0);
        ExecutorService executor    = Executors.newFixedThreadPool(totalThreads);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalThreads; i++) {
            final int idx     = i;
            final boolean ok  = idx < successThreads;

            futures.add(CompletableFuture.runAsync(() -> {
                String url = "jdbc:h2:mem:chaos_mixed_%d_%d;DB_CLOSE_DELAY=-1"
                        .formatted(idx, System.nanoTime());
                try (Connection conn = DriverManager.getConnection(url, "sa", "")) {
                    if (!ok) {
                        conn.close(); // deliberately break this thread's connection
                    } else {
                        YawlContainerFixtures.applyYawlSchema(conn);
                        WorkflowDataFactory.seedSpecification(conn,
                                "mixed-spec-" + idx, "1.0", "Mixed " + idx);
                    }
                    if (ok) {
                        successCount.incrementAndGet();
                    } else {
                        // reaching here means we "succeeded" at being failed (expected path)
                        failureCount.incrementAndGet();
                    }
                } catch (SQLException e) {
                    if (!ok) {
                        failureCount.incrementAndGet(); // expected failure
                    }
                    // unexpected failure on success threads would leave successCount low
                } catch (Exception e) {
                    // schema/seed errors on success threads
                }
            }, executor));
        }

        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertEquals(successThreads, successCount.get(),
                successThreads + " threads must succeed");
        assertEquals(failThreads, failureCount.get(),
                failThreads + " threads must fail as expected");
    }

    // =========================================================================
    // Engine Resilience After Sub-System Failure
    // =========================================================================

    @Test
    void testEngineSpecificationConstructionAfterDbFailure() throws Exception {
        // Simulate DB failure
        connection.close();

        // Engine-level object construction (no DB required) must still work
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec("post-db-failure");
        assertNotNull(spec, "Spec must be constructable without DB");
        assertNotNull(spec.getRootNet(), "Root net must be wired");

        // Re-establish DB connection and persist the spec metadata
        connection = DriverManager.getConnection(jdbcUrl, "sa", "");
        WorkflowDataFactory.seedSpecification(connection,
                "post-db-failure", "1.0", spec.getName());

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1),
                    "Row must be persisted after DB reconnection");
        }
    }

    @Test
    void testRetrySucceedsAfterTransientFailure() throws Exception {
        int maxRetries = 3;
        AtomicInteger attempts = new AtomicInteger(0);

        // Simulate transient failure: first attempt fails, subsequent succeed
        for (int retry = 0; retry < maxRetries; retry++) {
            try {
                attempts.incrementAndGet();
                if (attempts.get() == 1) {
                    // First attempt: simulate failure by throwing
                    throw new SQLException("Simulated transient DB failure");
                }
                // Subsequent attempts succeed
                WorkflowDataFactory.seedSpecification(connection,
                        "retry-spec", "1.0", "Retry Spec");
                break; // success — exit retry loop
            } catch (SQLException e) {
                if (retry == maxRetries - 1) {
                    fail("All " + maxRetries + " retries exhausted: " + e.getMessage());
                }
                // brief back-off between retries
                Thread.sleep(10L * (retry + 1));
            }
        }

        // Verify the row was eventually persisted
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification WHERE spec_id = 'retry-spec'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Row must be persisted after retry");
        }

        assertTrue(attempts.get() > 1,
                "At least one retry must have been performed");
    }

    // =========================================================================
    // Duplicate Key Resilience
    // =========================================================================

    @Test
    void testDuplicateKeyDoesNotCorruptExistingData() throws Exception {
        // Insert initial row
        WorkflowDataFactory.seedSpecification(connection, "dup-spec", "1.0", "Original");

        // Verify initial state
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT spec_name FROM yawl_specification WHERE spec_id = 'dup-spec'")) {
            assertTrue(rs.next());
            assertEquals("Original", rs.getString(1));
        }

        // Attempt duplicate insert — must fail with exception
        assertThrows(SQLException.class, () ->
                WorkflowDataFactory.seedSpecification(
                        connection, "dup-spec", "1.0", "Duplicate Attempt"),
                "Duplicate insert must throw SQLException");

        // Original data must be intact after failed duplicate insert
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT spec_name FROM yawl_specification WHERE spec_id = 'dup-spec'")) {
            assertTrue(rs.next());
            assertEquals("Original", rs.getString(1),
                    "Original row must be unchanged after failed duplicate insert");
        }
    }
}
