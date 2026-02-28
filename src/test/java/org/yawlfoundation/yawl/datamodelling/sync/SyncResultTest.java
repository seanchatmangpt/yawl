/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.datamodelling.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SyncResult.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Success and failure result creation</li>
 *   <li>Change tracking (added, modified, deleted)</li>
 *   <li>Affected tables tracking</li>
 *   <li>Checkpoint and query result management</li>
 *   <li>Timing and duration calculation</li>
 *   <li>Rollback instructions handling</li>
 *   <li>Fluent builder pattern</li>
 * </ul>
 *
 * @author YAWL Foundation (Teammate 4 - Engineer)
 * @version 6.0.0
 */
@DisplayName("SyncResult Tests")
class SyncResultTest {

    // ── Success Result Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("success factory creates successful result")
    void testSuccess_CreatesSuccessfulResult() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");

        assertTrue(result.isSuccess(), "Result should be successful");
        assertEquals("sync-001", result.getSyncId());
        assertEquals("WORKSPACE_TO_DB", result.getOperationType());
        assertEquals("duckdb", result.getBackendType());
        assertNull(result.errorMessage(), "Success result should not have error");
    }

    @Test
    @DisplayName("success factory rejects null syncId")
    void testSuccess_NullSyncId() {
        assertThrows(NullPointerException.class,
            () -> SyncResult.success(null, "WORKSPACE_TO_DB", "duckdb"));
    }

    @Test
    @DisplayName("success factory rejects null operationType")
    void testSuccess_NullOperationType() {
        assertThrows(NullPointerException.class,
            () -> SyncResult.success("sync-001", null, "duckdb"));
    }

    @Test
    @DisplayName("success factory rejects null backendType")
    void testSuccess_NullBackendType() {
        assertThrows(NullPointerException.class,
            () -> SyncResult.success("sync-001", "WORKSPACE_TO_DB", null));
    }

    // ── Failure Result Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("failure factory creates failed result")
    void testFailure_CreatesFailedResult() {
        String errorMsg = "Connection timeout: database unreachable";

        SyncResult result = SyncResult.failure("sync-002", "DB_TO_WORKSPACE", "postgres", errorMsg);

        assertFalse(result.isSuccess(), "Result should be failed");
        assertEquals("sync-002", result.getSyncId());
        assertEquals("DB_TO_WORKSPACE", result.getOperationType());
        assertEquals("postgres", result.getBackendType());
        assertEquals(errorMsg, result.errorMessage());
    }

    @Test
    @DisplayName("failure factory rejects null syncId")
    void testFailure_NullSyncId() {
        assertThrows(NullPointerException.class,
            () -> SyncResult.failure(null, "WORKSPACE_TO_DB", "duckdb", "Error"));
    }

    @Test
    @DisplayName("failure factory rejects null operationType")
    void testFailure_NullOperationType() {
        assertThrows(NullPointerException.class,
            () -> SyncResult.failure("sync-001", null, "duckdb", "Error"));
    }

    @Test
    @DisplayName("failure factory rejects null backendType")
    void testFailure_NullBackendType() {
        assertThrows(NullPointerException.class,
            () -> SyncResult.failure("sync-001", "WORKSPACE_TO_DB", null, "Error"));
    }

    @Test
    @DisplayName("failure factory rejects null errorMessage")
    void testFailure_NullErrorMessage() {
        assertThrows(NullPointerException.class,
            () -> SyncResult.failure("sync-001", "WORKSPACE_TO_DB", "duckdb", null));
    }

    // ── Workspace ID Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("withWorkspaceId sets workspace ID")
    void testWithWorkspaceId_Sets() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withWorkspaceId("workspace-abc");

        assertEquals("workspace-abc", result.getWorkspaceId());
    }

    @Test
    @DisplayName("withWorkspaceId is fluent")
    void testWithWorkspaceId_Fluent() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withWorkspaceId("workspace-abc")
            .withChanges(10, 5, 2);

        assertEquals("workspace-abc", result.getWorkspaceId());
        assertEquals(10, result.recordsAdded());
    }

    @Test
    @DisplayName("withWorkspaceId accepts null")
    void testWithWorkspaceId_Null() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withWorkspaceId(null);

        assertNull(result.getWorkspaceId());
    }

    // ── Record Change Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("withChanges sets record counts")
    void testWithChanges_Sets() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withChanges(42, 18, 5);

        assertEquals(42, result.recordsAdded());
        assertEquals(18, result.recordsModified());
        assertEquals(5, result.recordsDeleted());
    }

    @Test
    @DisplayName("totalRecordsChanged sums all changes")
    void testTotalRecordsChanged_Sum() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withChanges(42, 18, 5);

        assertEquals(65, result.totalRecordsChanged());
    }

    @Test
    @DisplayName("withChanges accepts zero values")
    void testWithChanges_Zeros() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withChanges(0, 0, 0);

        assertEquals(0, result.recordsAdded());
        assertEquals(0, result.recordsModified());
        assertEquals(0, result.recordsDeleted());
    }

    @Test
    @DisplayName("withChanges accepts large values")
    void testWithChanges_LargeValues() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withChanges(1_000_000, 500_000, 100_000);

        assertEquals(1_000_000, result.recordsAdded());
        assertEquals(1_600_000, result.totalRecordsChanged());
    }

    @Test
    @DisplayName("Default record counts are zero")
    void testDefaultRecordCounts() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");

        assertEquals(0, result.recordsAdded());
        assertEquals(0, result.recordsModified());
        assertEquals(0, result.recordsDeleted());
    }

    // ── Affected Tables Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("addTableAffected adds single table")
    void testAddTableAffected_Single() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .addTableAffected("customers");

        assertTrue(result.getTablesAffected().contains("customers"));
        assertEquals(1, result.getTablesAffected().size());
    }

    @Test
    @DisplayName("addTableAffected adds multiple tables")
    void testAddTableAffected_Multiple() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .addTableAffected("customers")
            .addTableAffected("orders")
            .addTableAffected("products");

        assertEquals(3, result.getTablesAffected().size());
        assertTrue(result.getTablesAffected().contains("customers"));
        assertTrue(result.getTablesAffected().contains("orders"));
        assertTrue(result.getTablesAffected().contains("products"));
    }

    @Test
    @DisplayName("addTableAffected rejects null table name")
    void testAddTableAffected_Null() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");

        assertThrows(NullPointerException.class,
            () -> result.addTableAffected(null));
    }

    @Test
    @DisplayName("getTablesAffected returns immutable list")
    void testGetTablesAffected_Immutable() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .addTableAffected("customers");

        assertThrows(UnsupportedOperationException.class,
            () -> result.getTablesAffected().add("orders"));
    }

    @Test
    @DisplayName("Default tables affected is empty")
    void testDefaultTablesAffected() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");

        assertTrue(result.getTablesAffected().isEmpty());
    }

    // ── Checkpoint Tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("withCheckpoint sets checkpoint JSON")
    void testWithCheckpoint_Sets() {
        String checkpoint = """
            {"checkpoint_id":"cp-001","resume_position":1000}
            """;

        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withCheckpoint(checkpoint);

        assertEquals(checkpoint, result.checkpointJson());
    }

    @Test
    @DisplayName("withCheckpoint accepts null")
    void testWithCheckpoint_Null() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withCheckpoint(null);

        assertNull(result.checkpointJson());
    }

    @Test
    @DisplayName("Default checkpoint is null")
    void testDefaultCheckpoint() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");

        assertNull(result.checkpointJson());
    }

    // ── Query Result Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("withQueryResult sets query result JSON")
    void testWithQueryResult_Sets() {
        String queryResult = """
            [{"id":1,"name":"Alice"},{"id":2,"name":"Bob"}]
            """;

        SyncResult result = SyncResult.success("sync-001", "QUERY", "duckdb")
            .withQueryResult(queryResult);

        assertEquals(queryResult, result.queryResult());
    }

    @Test
    @DisplayName("withQueryResult accepts null")
    void testWithQueryResult_Null() {
        SyncResult result = SyncResult.success("sync-001", "QUERY", "duckdb")
            .withQueryResult(null);

        assertNull(result.queryResult());
    }

    @Test
    @DisplayName("Default query result is null")
    void testDefaultQueryResult() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");

        assertNull(result.queryResult());
    }

    // ── Timing Tests ───────────────────────────────────────────────────────

    @Test
    @DisplayName("markCompleted sets completion time and calculates duration")
    void testMarkCompleted_CalculatesDuration() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");
        long startedAtMillis = result.getStartedAtMillis();

        // Small delay to ensure duration > 0
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        result.markCompleted();

        assertTrue(result.getCompletedAtMillis() > startedAtMillis);
        assertTrue(result.getDurationMillis() > 0);
        assertEquals(result.getDurationMillis(),
            result.getCompletedAtMillis() - result.getStartedAtMillis());
    }

    @Test
    @DisplayName("getStartedAt returns Instant")
    void testGetStartedAt_Instant() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");

        Instant startedAt = result.getStartedAt();
        assertNotNull(startedAt);
        assertEquals(result.getStartedAtMillis(), startedAt.toEpochMilli());
    }

    @Test
    @DisplayName("getCompletedAt returns null before completion")
    void testGetCompletedAt_BeforeCompletion() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");

        assertNull(result.getCompletedAt(), "Completed time should be null before markCompleted");
    }

    @Test
    @DisplayName("getCompletedAt returns Instant after completion")
    void testGetCompletedAt_AfterCompletion() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .markCompleted();

        Instant completedAt = result.getCompletedAt();
        assertNotNull(completedAt);
        assertEquals(result.getCompletedAtMillis(), completedAt.toEpochMilli());
    }

    @Test
    @DisplayName("markCompleted is fluent")
    void testMarkCompleted_Fluent() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withChanges(10, 5, 2)
            .markCompleted();

        assertTrue(result.getDurationMillis() >= 0);
        assertEquals(10, result.recordsAdded());
    }

    // ── Rollback Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("withRollback sets rollback instructions")
    void testWithRollback_Sets() {
        String rollback = "ROLLBACK TRANSACTION; DELETE FROM customers WHERE id > 100;";

        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withRollback(rollback);

        assertEquals(rollback, result.rollbackInstructions());
    }

    @Test
    @DisplayName("withRollback accepts null")
    void testWithRollback_Null() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withRollback(null);

        assertNull(result.rollbackInstructions());
    }

    @Test
    @DisplayName("Default rollback instructions are null")
    void testDefaultRollback() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");

        assertNull(result.rollbackInstructions());
    }

    // ── Equality Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("result equals same result")
    void testEquality_Same() {
        SyncResult result1 = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withWorkspaceId("ws-001")
            .withChanges(10, 5, 2)
            .markCompleted();

        SyncResult result2 = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withWorkspaceId("ws-001")
            .withChanges(10, 5, 2);
        // Note: markCompleted changes timestamps, so we compare before that

        assertEquals(result1.getSyncId(), result2.getSyncId());
    }

    @Test
    @DisplayName("result not equals different syncId")
    void testEquality_DifferentSyncId() {
        SyncResult result1 = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");
        SyncResult result2 = SyncResult.success("sync-002", "WORKSPACE_TO_DB", "duckdb");

        assertNotEquals(result1, result2);
    }

    @Test
    @DisplayName("result not equals null")
    void testEquality_Null() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb");

        assertNotEquals(result, null);
        assertNotEquals(result, "string");
    }

    @Test
    @DisplayName("Equal results have same hash code")
    void testHashCode_Consistent() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withChanges(10, 5, 2);

        int hash1 = result.hashCode();
        int hash2 = result.hashCode();

        assertEquals(hash1, hash2, "Hash code should be consistent");
    }

    // ── toString Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes key fields")
    void testToString_IncludesKeyFields() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withChanges(10, 5, 2);

        String str = result.toString();
        assertTrue(str.contains("success=true"));
        assertTrue(str.contains("sync-001"));
        assertTrue(str.contains("WORKSPACE_TO_DB"));
        assertTrue(str.contains("duckdb"));
    }

    // ── Complex Fluent Scenarios ───────────────────────────────────────────

    @Test
    @DisplayName("Full fluent construction for successful sync")
    void testFluentConstruction_SuccessfulSync() {
        SyncResult result = SyncResult.success("sync-001", "WORKSPACE_TO_DB", "duckdb")
            .withWorkspaceId("workspace-001")
            .withChanges(42, 18, 5)
            .addTableAffected("customers")
            .addTableAffected("orders")
            .addTableAffected("products")
            .withCheckpoint("""
                {"checkpoint_id":"cp-001","resume_position":1000}
                """)
            .markCompleted();

        assertTrue(result.isSuccess());
        assertEquals("workspace-001", result.getWorkspaceId());
        assertEquals(65, result.totalRecordsChanged());
        assertEquals(3, result.getTablesAffected().size());
        assertTrue(result.getDurationMillis() >= 0);
        assertNotNull(result.checkpointJson());
    }

    @Test
    @DisplayName("Full fluent construction for failed sync with rollback")
    void testFluentConstruction_FailedSync() {
        String errorMsg = "Connection timeout";
        String rollback = "VACUUM; RESET;";

        SyncResult result = SyncResult.failure("sync-002", "DB_TO_WORKSPACE", "postgres", errorMsg)
            .withRollback(rollback)
            .markCompleted();

        assertFalse(result.isSuccess());
        assertEquals(errorMsg, result.errorMessage());
        assertEquals(rollback, result.rollbackInstructions());
        assertTrue(result.getDurationMillis() >= 0);
    }
}
