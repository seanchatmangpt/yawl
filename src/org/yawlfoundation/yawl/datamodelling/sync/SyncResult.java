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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Typed result of a database synchronization operation.
 *
 * <p>Captures the outcome of {@link DataModellingDatabaseSync} operations,
 * including change detection, checkpoint state, and audit trail.</p>
 *
 * <h2>Contents</h2>
 * <ul>
 *   <li><strong>Records changed</strong>: Added, modified, deleted counts</li>
 *   <li><strong>Tables affected</strong>: Which tables were impacted</li>
 *   <li><strong>Checkpoint state</strong>: Serialized state for resume capability</li>
 *   <li><strong>Timeline</strong>: Start/end timestamps and operation duration</li>
 *   <li><strong>RDF audit trail</strong>: (via DataLineageTracker integration)</li>
 *   <li><strong>Rollback capability</strong>: Undo instructions if needed</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SyncResult result = sync.syncWorkspaceToDB(workspace, config);
 *
 * if (result.isSuccess()) {
 *     System.out.println("Added: " + result.recordsAdded());
 *     System.out.println("Modified: " + result.recordsModified());
 *     System.out.println("Checkpoint: " + result.checkpointJson());
 *     // Persist checkpoint for resume capability
 *     Files.writeString(Paths.get("/tmp/sync-checkpoint.json"), result.checkpointJson());
 * } else {
 *     System.err.println("Sync failed: " + result.errorMessage());
 *     System.out.println("Rollback: " + result.rollbackInstructions());
 * }
 * }</pre>
 *
 * @author YAWL Foundation (Teammate 4 - Engineer)
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class SyncResult {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("sync_id")
    private String syncId;

    @JsonProperty("operation_type")
    private String operationType; // WORKSPACE_TO_DB, DB_TO_WORKSPACE, QUERY

    @JsonProperty("backend_type")
    private String backendType;

    @JsonProperty("workspace_id")
    @Nullable
    private String workspaceId;

    @JsonProperty("records_added")
    private long recordsAdded = 0;

    @JsonProperty("records_modified")
    private long recordsModified = 0;

    @JsonProperty("records_deleted")
    private long recordsDeleted = 0;

    @JsonProperty("tables_affected")
    private List<String> tablesAffected = new ArrayList<>();

    @JsonProperty("checkpoint_json")
    @Nullable
    private String checkpointJson;

    @JsonProperty("query_result")
    @Nullable
    private String queryResult;

    @JsonProperty("started_at")
    private long startedAtMillis;

    @JsonProperty("completed_at")
    private long completedAtMillis;

    @JsonProperty("duration_millis")
    private long durationMillis = 0;

    @JsonProperty("error_message")
    @Nullable
    private String errorMessage;

    @JsonProperty("rollback_instructions")
    @Nullable
    private String rollbackInstructions;

    /**
     * Constructs an empty result (for JSON deserialization).
     */
    public SyncResult() {
        this.startedAtMillis = System.currentTimeMillis();
    }

    // ── Factory methods ────────────────────────────────────────────────────

    /**
     * Creates a successful sync result.
     *
     * @param syncId the unique sync operation identifier; must not be null
     * @param operationType the operation type; must not be null
     * @param backendType the backend type; must not be null
     * @return a new result; never null
     */
    public static SyncResult success(String syncId, String operationType, String backendType) {
        SyncResult result = new SyncResult();
        result.syncId = Objects.requireNonNull(syncId, "syncId must not be null");
        result.operationType = Objects.requireNonNull(operationType, "operationType must not be null");
        result.backendType = Objects.requireNonNull(backendType, "backendType must not be null");
        result.success = true;
        return result;
    }

    /**
     * Creates a failed sync result.
     *
     * @param syncId the unique sync operation identifier; must not be null
     * @param operationType the operation type; must not be null
     * @param backendType the backend type; must not be null
     * @param errorMessage the error message; must not be null
     * @return a new result; never null
     */
    public static SyncResult failure(String syncId, String operationType, String backendType,
                                     String errorMessage) {
        SyncResult result = new SyncResult();
        result.syncId = Objects.requireNonNull(syncId, "syncId must not be null");
        result.operationType = Objects.requireNonNull(operationType, "operationType must not be null");
        result.backendType = Objects.requireNonNull(backendType, "backendType must not be null");
        result.errorMessage = Objects.requireNonNull(errorMessage, "errorMessage must not be null");
        result.success = false;
        return result;
    }

    // ── Getters ────────────────────────────────────────────────────────────

    /**
     * Returns whether the sync operation succeeded.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the unique sync operation identifier.
     *
     * @return sync ID; never null
     */
    public String getSyncId() {
        return syncId;
    }

    /**
     * Returns the operation type (WORKSPACE_TO_DB, DB_TO_WORKSPACE, QUERY).
     *
     * @return operation type; never null
     */
    public String getOperationType() {
        return operationType;
    }

    /**
     * Returns the backend type (duckdb, postgres).
     *
     * @return backend type; never null
     */
    public String getBackendType() {
        return backendType;
    }

    /**
     * Returns the workspace ID if applicable.
     *
     * @return workspace ID or null
     */
    public @Nullable String getWorkspaceId() {
        return workspaceId;
    }

    /**
     * Returns the number of records added.
     *
     * @return record count added
     */
    public long recordsAdded() {
        return recordsAdded;
    }

    /**
     * Returns the number of records modified.
     *
     * @return record count modified
     */
    public long recordsModified() {
        return recordsModified;
    }

    /**
     * Returns the number of records deleted.
     *
     * @return record count deleted
     */
    public long recordsDeleted() {
        return recordsDeleted;
    }

    /**
     * Returns the total number of records changed.
     *
     * @return total changes
     */
    public long totalRecordsChanged() {
        return recordsAdded + recordsModified + recordsDeleted;
    }

    /**
     * Returns the list of affected table names.
     *
     * @return list of table names; never null
     */
    public List<String> getTablesAffected() {
        return Collections.unmodifiableList(tablesAffected);
    }

    /**
     * Returns the checkpoint JSON for resuming interrupted syncs.
     *
     * @return checkpoint JSON or null if not applicable
     */
    public @Nullable String checkpointJson() {
        return checkpointJson;
    }

    /**
     * Returns the query result (for QUERY operations).
     *
     * @return query result JSON or null
     */
    public @Nullable String queryResult() {
        return queryResult;
    }

    /**
     * Returns the start time as epoch milliseconds.
     *
     * @return epoch milliseconds
     */
    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    /**
     * Returns the start time as an Instant.
     *
     * @return Instant; never null
     */
    public Instant getStartedAt() {
        return Instant.ofEpochMilli(startedAtMillis);
    }

    /**
     * Returns the completion time as epoch milliseconds.
     *
     * @return epoch milliseconds (0 if not yet completed)
     */
    public long getCompletedAtMillis() {
        return completedAtMillis;
    }

    /**
     * Returns the completion time as an Instant.
     *
     * @return Instant or null if not yet completed
     */
    public @Nullable Instant getCompletedAt() {
        return completedAtMillis > 0 ? Instant.ofEpochMilli(completedAtMillis) : null;
    }

    /**
     * Returns the operation duration in milliseconds.
     *
     * @return duration in milliseconds
     */
    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * Returns the error message if the operation failed.
     *
     * @return error message or null
     */
    public @Nullable String errorMessage() {
        return errorMessage;
    }

    /**
     * Returns rollback instructions if the operation failed.
     *
     * @return rollback instructions or null
     */
    public @Nullable String rollbackInstructions() {
        return rollbackInstructions;
    }

    // ── Setters (fluent) ───────────────────────────────────────────────────

    /**
     * Sets the workspace ID.
     *
     * @param workspaceId the workspace ID; may be null
     * @return this result
     */
    public SyncResult withWorkspaceId(@Nullable String workspaceId) {
        this.workspaceId = workspaceId;
        return this;
    }

    /**
     * Sets the record counts.
     *
     * @param added number of records added
     * @param modified number of records modified
     * @param deleted number of records deleted
     * @return this result
     */
    public SyncResult withChanges(long added, long modified, long deleted) {
        this.recordsAdded = added;
        this.recordsModified = modified;
        this.recordsDeleted = deleted;
        return this;
    }

    /**
     * Adds an affected table name.
     *
     * @param tableName the table name; must not be null
     * @return this result
     */
    public SyncResult addTableAffected(String tableName) {
        this.tablesAffected.add(Objects.requireNonNull(tableName, "tableName must not be null"));
        return this;
    }

    /**
     * Sets the checkpoint JSON.
     *
     * @param checkpointJson the checkpoint JSON; may be null
     * @return this result
     */
    public SyncResult withCheckpoint(@Nullable String checkpointJson) {
        this.checkpointJson = checkpointJson;
        return this;
    }

    /**
     * Sets the query result.
     *
     * @param queryResult the query result JSON; may be null
     * @return this result
     */
    public SyncResult withQueryResult(@Nullable String queryResult) {
        this.queryResult = queryResult;
        return this;
    }

    /**
     * Marks the operation as completed and calculates duration.
     *
     * @return this result
     */
    public SyncResult markCompleted() {
        this.completedAtMillis = System.currentTimeMillis();
        this.durationMillis = this.completedAtMillis - this.startedAtMillis;
        return this;
    }

    /**
     * Sets the rollback instructions.
     *
     * @param instructions the rollback instructions; may be null
     * @return this result
     */
    public SyncResult withRollback(@Nullable String instructions) {
        this.rollbackInstructions = instructions;
        return this;
    }

    // ── Equality & hashing ─────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SyncResult that = (SyncResult) o;
        return success == that.success &&
                recordsAdded == that.recordsAdded &&
                recordsModified == that.recordsModified &&
                recordsDeleted == that.recordsDeleted &&
                startedAtMillis == that.startedAtMillis &&
                completedAtMillis == that.completedAtMillis &&
                durationMillis == that.durationMillis &&
                Objects.equals(syncId, that.syncId) &&
                Objects.equals(operationType, that.operationType) &&
                Objects.equals(backendType, that.backendType) &&
                Objects.equals(workspaceId, that.workspaceId) &&
                Objects.equals(tablesAffected, that.tablesAffected) &&
                Objects.equals(checkpointJson, that.checkpointJson) &&
                Objects.equals(queryResult, that.queryResult) &&
                Objects.equals(errorMessage, that.errorMessage) &&
                Objects.equals(rollbackInstructions, that.rollbackInstructions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, syncId, operationType, backendType, workspaceId,
                recordsAdded, recordsModified, recordsDeleted, tablesAffected,
                checkpointJson, queryResult, startedAtMillis, completedAtMillis,
                durationMillis, errorMessage, rollbackInstructions);
    }

    @Override
    public String toString() {
        return "SyncResult{" +
                "success=" + success +
                ", syncId='" + syncId + '\'' +
                ", operationType='" + operationType + '\'' +
                ", backendType='" + backendType + '\'' +
                ", recordsAdded=" + recordsAdded +
                ", recordsModified=" + recordsModified +
                ", recordsDeleted=" + recordsDeleted +
                ", tablesAffected=" + tablesAffected +
                ", durationMillis=" + durationMillis +
                '}';
    }
}
