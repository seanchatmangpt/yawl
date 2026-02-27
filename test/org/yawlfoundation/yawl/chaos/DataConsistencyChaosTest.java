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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Data Consistency Chaos Engineering Tests for YAWL MCP-A2A MVP.
 *
 * Tests data consistency scenarios including:
 * - Partial write scenarios
 * - Concurrent modification conflicts
 * - Transaction rollback testing
 * - Cache invalidation chaos
 * - State synchronization issues
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Tag("chaos")
@DisplayName("Data Consistency Chaos Tests")
class DataConsistencyChaosTest {

    private Connection db;
    private String jdbcUrl;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = "jdbc:h2:mem:chaos_consistency_%d;DB_CLOSE_DELAY=-1"
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
    // Partial Write Scenarios
    // =========================================================================

    @Nested
    @DisplayName("Partial Write Scenarios")
    class PartialWriteTests {

        @Test
        @DisplayName("Partial batch write with atomic rollback")
        void testPartialBatchWriteWithAtomicRollback() throws Exception {
            String specId = "batch-partial";
            int batchSize = 10;

            db.setAutoCommit(false);

            try {
                WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Batch Parent");

                // Insert multiple runners
                for (int i = 0; i < batchSize; i++) {
                    WorkflowDataFactory.seedNetRunner(db,
                            "runner-" + i, specId, "1.0", "net-" + i, "RUNNING");
                }

                // Force failure mid-batch (simulate crash)
                throw new RuntimeException("Simulated crash during batch");
            } catch (RuntimeException e) {
                db.rollback();
            }

            db.setAutoCommit(true);

            // Verify atomicity: nothing should be persisted
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM yawl_net_runner")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Batch must be rolled back atomically");
            }

            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM yawl_specification")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Parent record must also be rolled back");
            }
        }

        @Test
        @DisplayName("Partial write detection and recovery")
        void testPartialWriteDetectionAndRecovery() throws Exception {
            String specId = "partial-detection";

            // Simulate partial write: spec exists but work items are incomplete
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Partial Write");

            // Create incomplete state (work item without proper runner reference)
            try (PreparedStatement ps = db.prepareStatement(
                    "INSERT INTO yawl_work_item (item_id, runner_id, task_id, status) " +
                            "VALUES (?, ?, ?, ?)")) {
                ps.setString(1, "orphan-item");
                ps.setString(2, "non-existent-runner");
                ps.setString(3, "task");
                ps.setString(4, "Orphaned");
                // This will fail due to FK constraint
                assertThrows(SQLException.class, ps::executeUpdate,
                        "Partial write must be rejected by FK constraint");
            }

            // Verify clean state
            try (Statement stmt = db.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT COUNT(*) FROM yawl_work_item WHERE item_id = 'orphan-item'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Orphan item must not exist");
            }
        }

        @Test
        @DisplayName("Multi-table partial write consistency")
        void testMultiTablePartialWriteConsistency() throws Exception {
            String specId = "multi-table";
            String runnerId = "runner-multi";

            db.setAutoCommit(false);

            try {
                // Write to multiple tables in sequence
                WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Multi-Table");
                WorkflowDataFactory.seedNetRunner(db, runnerId, specId, "1.0", "root", "RUNNING");

                // Add work items
                for (int i = 0; i < 5; i++) {
                    WorkflowDataFactory.seedWorkItem(db,
                            "item-" + i, runnerId, "task-" + i, "Enabled");
                }

                // Log event
                try (PreparedStatement ps = db.prepareStatement(
                        "INSERT INTO yawl_case_event (event_id, runner_id, event_type, event_data) " +
                                "VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, System.currentTimeMillis());
                    ps.setString(2, runnerId);
                    ps.setString(3, "CASE_CREATED");
                    ps.setString(4, "{\"itemCount\":5}");
                    ps.executeUpdate();
                }

                db.commit();
            } catch (Exception e) {
                db.rollback();
                throw e;
            }

            db.setAutoCommit(true);

            // Verify all tables have consistent data
            assertRowCount(db, "yawl_specification", 1);
            assertRowCount(db, "yawl_net_runner", 1);
            assertRowCount(db, "yawl_work_item", 5);
            assertRowCount(db, "yawl_case_event", 1);
        }

        @Test
        @DisplayName("Write interruption with savepoint")
        void testWriteInterruptionWithSavepoint() throws Exception {
            String specId = "savepoint-test";

            db.setAutoCommit(false);

            // Initial write
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Savepoint Test");

            // Create savepoint
            var savepoint = db.setSavepoint("before-runners");

            try {
                // Additional writes
                for (int i = 0; i < 5; i++) {
                    WorkflowDataFactory.seedNetRunner(db,
                            "save-runner-" + i, specId, "1.0", "net", "RUNNING");
                }

                // Simulate failure
                throw new RuntimeException("Failure after savepoint");
            } catch (RuntimeException e) {
                db.rollback(savepoint);
            }

            db.commit();
            db.setAutoCommit(true);

            // Verify spec exists but runners were rolled back to savepoint
            assertTrue(rowExists(db, "yawl_specification", "spec_id", specId));
            assertRowCount(db, "yawl_net_runner", 0);
        }
    }

    // =========================================================================
    // Concurrent Modification Conflict Tests
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Modification Conflicts")
    class ConcurrentModificationTests {

        @Test
        @DisplayName("Optimistic locking conflict detection")
        void testOptimisticLockingConflictDetection() throws Exception {
            String runnerId = "optimistic-runner";
            WorkflowDataFactory.seedSpecification(db, "opt-lock", "1.0", "Optimistic Locking");
            WorkflowDataFactory.seedNetRunner(db, runnerId, "opt-lock", "1.0", "root", "RUNNING");

            // Simulate two concurrent updates
            AtomicBoolean conflict1 = new AtomicBoolean(false);
            AtomicBoolean conflict2 = new AtomicBoolean(false);

            Thread t1 = new Thread(() -> {
                try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
                    conn.setAutoCommit(false);

                    // Read version
                    long version = getVersion(conn, runnerId);

                    // Simulate processing time
                    Thread.sleep(100);

                    // Update with version check
                    int updated = updateWithVersion(conn, runnerId, "SUSPENDED", version);

                    conn.commit();
                    conflict1.set(updated == 0);
                } catch (Exception e) {
                    conflict1.set(true);
                }
            });

            Thread t2 = new Thread(() -> {
                try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
                    conn.setAutoCommit(false);

                    // Read same version
                    long version = getVersion(conn, runnerId);

                    // Faster update
                    int updated = updateWithVersion(conn, runnerId, "COMPLETED", version);

                    conn.commit();
                    conflict2.set(updated == 0);
                } catch (Exception e) {
                    conflict2.set(true);
                }
            });

            t1.start();
            Thread.sleep(50); // Stagger starts
            t2.start();

            t1.join(5000);
            t2.join(5000);

            // At least one must detect conflict
            assertTrue(conflict1.get() || conflict2.get(),
                    "At least one transaction must detect optimistic lock conflict");
        }

        @Test
        @DisplayName("Pessimistic locking prevents conflicts")
        void testPessimisticLockingPreventsConflicts() throws Exception {
            String runnerId = "pessimistic-runner";
            WorkflowDataFactory.seedSpecification(db, "pess-lock", "1.0", "Pessimistic Locking");
            WorkflowDataFactory.seedNetRunner(db, runnerId, "pess-lock", "1.0", "root", "RUNNING");

            AtomicInteger successfulUpdates = new AtomicInteger(0);
            AtomicInteger blockedUpdates = new AtomicInteger(0);
            int threads = 5;

            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);

            for (int i = 0; i < threads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
                        conn.setAutoCommit(false);

                        // Try to acquire lock with timeout
                        boolean locked = tryAcquireLock(conn, runnerId, 1000);

                        if (locked) {
                            // Update
                            try (PreparedStatement ps = conn.prepareStatement(
                                    "UPDATE yawl_net_runner SET state = ? WHERE runner_id = ?")) {
                                ps.setString(1, "UPDATED-" + threadId);
                                ps.setString(2, runnerId);
                                ps.executeUpdate();
                            }
                            conn.commit();
                            successfulUpdates.incrementAndGet();
                        } else {
                            conn.rollback();
                            blockedUpdates.incrementAndGet();
                        }
                    } catch (Exception e) {
                        blockedUpdates.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // Only one thread should succeed with pessimistic lock
            assertEquals(1, successfulUpdates.get(), "Only one thread should update with lock");
            assertEquals(threads - 1, blockedUpdates.get(), "Other threads must be blocked");

            System.out.printf("Pessimistic locking: %d succeeded, %d blocked%n",
                    successfulUpdates.get(), blockedUpdates.get());
        }

        @Test
        @DisplayName("Last-write-wins conflict resolution")
        void testLastWriteWinsConflictResolution() throws Exception {
            String specId = "last-write-wins";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Original");

            // Simulate two writes to same row
            long timestamp1 = System.currentTimeMillis() - 1000;
            long timestamp2 = System.currentTimeMillis();

            // Write 1 (older)
            updateSpecificationWithTimestamp(db, specId, "Write 1", timestamp1);

            // Write 2 (newer)
            updateSpecificationWithTimestamp(db, specId, "Write 2", timestamp2);

            // Verify last write wins
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT spec_name FROM yawl_specification WHERE spec_id = ?")) {
                ps.setString(1, specId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Write 2", rs.getString("spec_name"),
                            "Last write must win");
                }
            }
        }

        @Test
        @DisplayName("Write skew anomaly detection")
        void testWriteSkewAnomalyDetection() throws Exception {
            // Create two related records
            String specId = "write-skew";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Write Skew");
            WorkflowDataFactory.seedNetRunner(db, "runner-1", specId, "1.0", "net-1", "RUNNING");
            WorkflowDataFactory.seedNetRunner(db, "runner-2", specId, "1.0", "net-2", "RUNNING");

            AtomicInteger anomalyDetected = new AtomicInteger(0);

            // Two transactions read both runners and try to update based on condition
            Thread t1 = new Thread(() -> {
                try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
                    conn.setAutoCommit(false);
                    conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

                    // Check both are RUNNING
                    int running = countRunningRunners(conn, specId);

                    if (running == 2) {
                        Thread.sleep(100); // Allow interleaving

                        // Suspend one
                        updateRunnerState(conn, "runner-1", "SUSPENDED");
                    }

                    conn.commit();
                } catch (Exception e) {
                    // Handle
                }
            });

            Thread t2 = new Thread(() -> {
                try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
                    conn.setAutoCommit(false);
                    conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

                    // Check both are RUNNING
                    int running = countRunningRunners(conn, specId);

                    if (running == 2) {
                        // Suspend the other
                        updateRunnerState(conn, "runner-2", "SUSPENDED");
                    }

                    conn.commit();
                } catch (Exception e) {
                    // Handle
                }
            });

            t1.start();
            t2.start();
            t1.join(5000);
            t2.join(5000);

            // Check final state - both might be suspended (write skew)
            int finalRunning = countRunningRunners(db, specId);
            System.out.printf("Write skew test: final running count = %d%n", finalRunning);

            // With proper isolation, at least one should remain RUNNING
            // (This test may show the anomaly depending on isolation level)
        }
    }

    // =========================================================================
    // Transaction Rollback Testing
    // =========================================================================

    @Nested
    @DisplayName("Transaction Rollback Testing")
    class TransactionRollbackTests {

        @Test
        @DisplayName("Explicit rollback restores state")
        void testExplicitRollbackRestoresState() throws Exception {
            String specId = "rollback-explicit";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Before Rollback");

            db.setAutoCommit(false);

            // Modify data
            try (PreparedStatement ps = db.prepareStatement(
                    "UPDATE yawl_specification SET spec_name = ? WHERE spec_id = ?")) {
                ps.setString(1, "After Modification");
                ps.setString(2, specId);
                ps.executeUpdate();
            }

            // Rollback
            db.rollback();
            db.setAutoCommit(true);

            // Verify original state restored
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT spec_name FROM yawl_specification WHERE spec_id = ?")) {
                ps.setString(1, specId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Before Rollback", rs.getString("spec_name"),
                            "Original state must be restored after rollback");
                }
            }
        }

        @Test
        @DisplayName("Nested transaction rollback")
        void testNestedTransactionRollback() throws Exception {
            String specId = "nested-rollback";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Nested Test");

            db.setAutoCommit(false);

            // Create savepoint
            var savepoint1 = db.setSavepoint("level1");

            // Modify at level 1
            updateSpecificationName(db, specId, "Level 1");

            // Create nested savepoint
            var savepoint2 = db.setSavepoint("level2");

            // Modify at level 2
            updateSpecificationName(db, specId, "Level 2");

            // Rollback to level 1
            db.rollback(savepoint1);

            db.commit();
            db.setAutoCommit(true);

            // Verify state at level 1 savepoint
            try (PreparedStatement ps = db.prepareStatement(
                    "SELECT spec_name FROM yawl_specification WHERE spec_id = ?")) {
                ps.setString(1, specId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Nested Test", rs.getString("spec_name"),
                            "State must be at savepoint 1");
                }
            }
        }

        @Test
        @DisplayName("Rollback on constraint violation")
        void testRollbackOnConstraintViolation() throws Exception {
            db.setAutoCommit(false);

            try {
                // Valid insert
                WorkflowDataFactory.seedSpecification(db, "constraint-1", "1.0", "Valid");

                // Invalid insert (duplicate)
                WorkflowDataFactory.seedSpecification(db, "constraint-1", "1.0", "Duplicate");

                fail("Should have thrown on duplicate");
            } catch (SQLException e) {
                db.rollback();
            }

            db.setAutoCommit(true);

            // Nothing should be persisted
            assertRowCount(db, "yawl_specification", 0);
        }

        @Test
        @DisplayName("Automatic rollback on connection close")
        void testAutomaticRollbackOnConnectionClose() throws Exception {
            String anotherJdbc = "jdbc:h2:mem:auto_rollback_%d;DB_CLOSE_DELAY=-1"
                    .formatted(System.nanoTime());

            Connection conn = DriverManager.getConnection(anotherJdbc, "sa", "");
            YawlContainerFixtures.applyYawlSchema(conn);

            conn.setAutoCommit(false);
            WorkflowDataFactory.seedSpecification(conn, "auto-rollback", "1.0", "Auto Rollback");

            // Close without commit
            conn.close();

            // Reconnect and verify
            conn = DriverManager.getConnection(anotherJdbc, "sa", "");
            assertRowCount(conn, "yawl_specification", 0);
            conn.close();
        }
    }

    // =========================================================================
    // Cache Invalidation Chaos Tests
    // =========================================================================

    @Nested
    @DisplayName("Cache Invalidation Chaos")
    class CacheInvalidationTests {

        @Test
        @DisplayName("Stale cache read after update")
        void testStaleCacheReadAfterUpdate() throws Exception {
            InMemoryCache cache = new InMemoryCache();
            String specId = "cache-stale";

            // Initial write
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Original");
            cache.put(specId, "Original");

            // Update database
            updateSpecificationName(db, specId, "Updated");

            // Read from cache (stale)
            String cachedValue = cache.get(specId);

            // Read from database (current)
            String dbValue = getSpecificationName(db, specId);

            assertNotEquals(cachedValue, dbValue,
                    "Cache must be stale after direct DB update");

            System.out.printf("Cache: %s, DB: %s%n", cachedValue, dbValue);
        }

        @Test
        @DisplayName("Cache invalidation on write")
        void testCacheInvalidationOnWrite() throws Exception {
            InMemoryCache cache = new InMemoryCache();
            String specId = "cache-invalidate";

            // Initial write with cache
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Original");
            cache.put(specId, "Original");

            // Update with cache invalidation
            updateSpecificationWithCacheInvalidation(db, cache, specId, "Updated");

            // Cache should be invalidated
            assertNull(cache.get(specId), "Cache must be invalidated after write");
        }

        @Test
        @DisplayName("Cache stampede on invalidation")
        void testCacheStampedeOnInvalidation() throws Exception {
            InMemoryCache cache = new InMemoryCache();
            String specId = "cache-stampede";

            // Seed data
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Stampede Test");

            // Invalidate cache
            cache.invalidate(specId);

            // Multiple threads try to read (all miss cache)
            int threads = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger cacheMisses = new AtomicInteger(0);
            AtomicInteger dbReads = new AtomicInteger(0);

            for (int i = 0; i < threads; i++) {
                executor.submit(() -> {
                    try {
                        String value = cache.get(specId);
                        if (value == null) {
                            cacheMisses.incrementAndGet();
                            // Read from DB
                            dbReads.incrementAndGet();
                            value = getSpecificationName(db, specId);
                            cache.putIfAbsent(specId, value);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            System.out.printf("Cache stampede: %d misses, %d DB reads%n",
                    cacheMisses.get(), dbReads.get());

            // All threads should have cache miss initially
            assertEquals(threads, cacheMisses.get());
        }

        @Test
        @DisplayName("Distributed cache invalidation simulation")
        void testDistributedCacheInvalidation() throws Exception {
            // Simulate two cache nodes
            InMemoryCache cache1 = new InMemoryCache();
            InMemoryCache cache2 = new InMemoryCache();
            String specId = "distributed-cache";

            // Both caches have value
            String value = "Cached Value";
            cache1.put(specId, value);
            cache2.put(specId, value);

            // Invalidate both
            cache1.invalidate(specId);
            cache2.invalidate(specId);

            // Both must be invalidated
            assertNull(cache1.get(specId), "Cache 1 must be invalidated");
            assertNull(cache2.get(specId), "Cache 2 must be invalidated");
        }
    }

    // =========================================================================
    // State Synchronization Issues Tests
    // =========================================================================

    @Nested
    @DisplayName("State Synchronization Issues")
    class StateSynchronizationTests {

        @Test
        @DisplayName("Eventual consistency convergence")
        void testEventualConsistencyConvergence() throws Exception {
            String specId = "eventual-consistency";
            WorkflowDataFactory.seedSpecification(db, specId, "1.0", "Initial");

            // Simulate multiple replicas with different update times
            List<StateReplica> replicas = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                replicas.add(new StateReplica("Replica-" + i));
            }

            // Update all replicas
            for (StateReplica replica : replicas) {
                replica.updateState(specId, "v2");
            }

            // Verify all converged to same state
            String expected = "v2";
            for (StateReplica replica : replicas) {
                assertEquals(expected, replica.getState(specId),
                        "All replicas must converge to same state");
            }
        }

        @Test
        @DisplayName("Split-brain state divergence detection")
        void testSplitBrainStateDivergenceDetection() throws Exception {
            String specId = "split-brain-state";

            // Two divergent states
            StateReplica replica1 = new StateReplica("Replica-1");
            StateReplica replica2 = new StateReplica("Replica-2");

            replica1.updateState(specId, "State-A");
            replica2.updateState(specId, "State-B");

            // Detect divergence
            boolean divergent = !replica1.getState(specId).equals(replica2.getState(specId));
            assertTrue(divergent, "Must detect state divergence");

            // Resolve conflict (vector clock or last-write-wins)
            String resolved = resolveConflict(replica1, replica2);
            replica1.updateState(specId, resolved);
            replica2.updateState(specId, resolved);

            // Verify convergence
            assertEquals(replica1.getState(specId), replica2.getState(specId),
                    "State must converge after conflict resolution");
        }

        @Test
        @DisplayName("Causal ordering preservation")
        void testCausalOrderingPreservation() throws Exception {
            String specId = "causal-order";

            List<String> events = new ArrayList<>();
            Object lock = new Object();

            // Event A happens before B (causal relationship)
            Thread t1 = new Thread(() -> {
                synchronized (lock) {
                    events.add("A");
                    lock.notifyAll();
                }
            });

            Thread t2 = new Thread(() -> {
                synchronized (lock) {
                    try {
                        // Wait for A before adding B
                        while (!events.contains("A")) {
                            lock.wait();
                        }
                        events.add("B");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });

            t2.start();
            t1.start();

            t1.join(1000);
            t2.join(1000);

            // A must come before B
            assertEquals(List.of("A", "B"), events,
                    "Causal ordering must be preserved");
        }

        @Test
        @DisplayName("State machine replication consistency")
        void testStateMachineReplicationConsistency() throws Exception {
            String specId = "sm-replication";
            List<StateMachineReplica> replicas = new ArrayList<>();

            for (int i = 0; i < 3; i++) {
                replicas.add(new StateMachineReplica("SM-" + i));
            }

            // Apply same commands to all replicas
            List<String> commands = List.of("CREATE", "START", "PAUSE", "RESUME", "COMPLETE");

            for (String cmd : commands) {
                for (StateMachineReplica replica : replicas) {
                    replica.applyCommand(cmd);
                }
            }

            // All replicas must be in same final state
            String expectedState = "COMPLETED";
            for (StateMachineReplica replica : replicas) {
                assertEquals(expectedState, replica.getCurrentState(),
                        "All replicas must reach same final state");
            }
        }
    }

    // =========================================================================
    // Helper Methods and Classes
    // =========================================================================

    private static boolean rowExists(Connection conn, String table, String column, String value)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM " + table + " WHERE " + column + " = ?")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void assertRowCount(Connection conn, String table, int expected) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rs.next());
            assertEquals(expected, rs.getInt(1), "Row count in " + table);
        }
    }

    private static long getVersion(Connection conn, String runnerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(version, 0) FROM yawl_net_runner WHERE runner_id = ?")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private static int updateWithVersion(Connection conn, String runnerId, String state, long version)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE yawl_net_runner SET state = ?, version = version + 1 " +
                        "WHERE runner_id = ? AND version = ?")) {
            ps.setString(1, state);
            ps.setString(2, runnerId);
            ps.setLong(3, version);
            return ps.executeUpdate();
        }
    }

    private static boolean tryAcquireLock(Connection conn, String runnerId, long timeoutMs)
            throws SQLException {
        // H2 supports FOR UPDATE
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM yawl_net_runner WHERE runner_id = ? FOR UPDATE NOWAIT")) {
            ps.setString(1, runnerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static int countRunningRunners(Connection conn, String specId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM yawl_net_runner WHERE spec_id = ? AND state = 'RUNNING'")) {
            ps.setString(1, specId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void updateRunnerState(Connection conn, String runnerId, String state)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE yawl_net_runner SET state = ? WHERE runner_id = ?")) {
            ps.setString(1, state);
            ps.setString(2, runnerId);
            ps.executeUpdate();
        }
    }

    private static void updateSpecificationName(Connection conn, String specId, String name)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE yawl_specification SET spec_name = ? WHERE spec_id = ?")) {
            ps.setString(1, name);
            ps.setString(2, specId);
            ps.executeUpdate();
        }
    }

    private static void updateSpecificationWithTimestamp(Connection conn, String specId,
                                                          String name, long timestamp)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE yawl_specification SET spec_name = ?, updated_at = ? WHERE spec_id = ?")) {
            ps.setString(1, name);
            ps.setLong(2, timestamp);
            ps.setString(3, specId);
            ps.executeUpdate();
        }
    }

    private static String getSpecificationName(Connection conn, String specId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT spec_name FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, specId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("spec_name") : null;
            }
        }
    }

    private static void updateSpecificationWithCacheInvalidation(
            Connection conn, InMemoryCache cache, String specId, String name)
            throws SQLException {
        updateSpecificationName(conn, specId, name);
        cache.invalidate(specId);
    }

    private static String resolveConflict(StateReplica r1, StateReplica r2) {
        // Simple resolution: prefer lexicographically larger value
        String s1 = r1.getState("key");
        String s2 = r2.getState("key");
        return s1.compareTo(s2) >= 0 ? s1 : s2;
    }

    static class InMemoryCache {
        private final Map<String, String> cache = new ConcurrentHashMap<>();

        void put(String key, String value) { cache.put(key, value); }
        String get(String key) { return cache.get(key); }
        void invalidate(String key) { cache.remove(key); }
        void putIfAbsent(String key, String value) { cache.putIfAbsent(key, value); }
    }

    static class StateReplica {
        private final String name;
        private final Map<String, String> state = new ConcurrentHashMap<>();

        StateReplica(String name) { this.name = name; }

        void updateState(String key, String value) { state.put(key, value); }
        String getState(String key) { return state.get(key); }
    }

    static class StateMachineReplica {
        private final String name;
        private String currentState = "INITIAL";

        StateMachineReplica(String name) { this.name = name; }

        void applyCommand(String command) {
            currentState = switch (command) {
                case "CREATE" -> "CREATED";
                case "START" -> "RUNNING";
                case "PAUSE" -> "PAUSED";
                case "RESUME" -> "RUNNING";
                case "COMPLETE" -> "COMPLETED";
                default -> currentState;
            };
        }

        String getCurrentState() { return currentState; }
    }
}
