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

package org.yawlfoundation.yawl.datamodelling.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.datamodelling.DataModellingException;

/**
 * Wrapper for DuckDB staging database operations in the data pipeline.
 *
 * <p>Manages data ingestion into a temporary DuckDB instance for schema inference,
 * data profiling, and transformation testing. Provides high-level operations while
 * delegating actual WASM calls to {@link DataModellingBridge}.</p>
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Create and manage temporary DuckDB instances</li>
 *   <li>Ingest data from various sources (JSON, CSV, Parquet, databases)</li>
 *   <li>Execute queries for data profiling and validation</li>
 *   <li>Handle batch processing and deduplication</li>
 *   <li>Support checkpointing for large datasets</li>
 *   <li>Clean up temporary resources</li>
 * </ul>
 *
 * <p>Thread-safe via underlying DataModellingBridge context pool.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class StagingDatabase implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StagingDatabase.class);

    private final DataModellingBridge bridge;
    private final String databaseId; // unique identifier for this staging instance
    private boolean closed;

    /**
     * Constructs a StagingDatabase with the given bridge.
     *
     * @param bridge the DataModellingBridge for WASM operations; must not be null
     * @throws IllegalArgumentException if bridge is null
     */
    public StagingDatabase(DataModellingBridge bridge) {
        if (bridge == null) {
            throw new IllegalArgumentException("DataModellingBridge must not be null");
        }
        this.bridge = bridge;
        this.databaseId = "staging_" + System.nanoTime();
        this.closed = false;
        log.debug("StagingDatabase created: {}", databaseId);
    }

    /**
     * Ingests data from JSON content into the staging database.
     *
     * <p>The data is stored in a DuckDB table with a generated name based on
     * the config's sourcePath or a default name.</p>
     *
     * @param jsonData JSON array or object content; must not be null
     * @param config ingestion configuration; must not be null
     * @return result with staging location and ingestion metadata; never null
     * @throws UnsupportedOperationException DuckDB JSON ingestion requires WASM bridge support
     * @throws IllegalStateException if this StagingDatabase is closed
     */
    public IngestResult ingestFromJson(String jsonData, IngestConfig config) {
        checkNotClosed();
        if (jsonData == null || jsonData.trim().isEmpty()) {
            throw new IllegalArgumentException("jsonData must not be null or empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("IngestConfig must not be null");
        }

        throw new UnsupportedOperationException(
                "DuckDB JSON ingestion requires WASM bridge support. "
                + "Implement via DataModellingBridge.ingestJsonData(jsonData, config) when SDK exposes it. "
                + "Expected return: IngestResult with stagingLocation and rowCount from DuckDB.");
    }

    /**
     * Ingests data from CSV content into the staging database.
     *
     * @param csvData CSV content; must not be null
     * @param config ingestion configuration (delimiter, encoding, etc.); must not be null
     * @return result with staging location and ingestion metadata; never null
     * @throws UnsupportedOperationException DuckDB CSV ingestion requires WASM bridge support
     * @throws IllegalStateException if this StagingDatabase is closed
     */
    public IngestResult ingestFromCsv(String csvData, IngestConfig config) {
        checkNotClosed();
        if (csvData == null || csvData.trim().isEmpty()) {
            throw new IllegalArgumentException("csvData must not be null or empty");
        }
        if (config == null) {
            throw new IllegalArgumentException("IngestConfig must not be null");
        }

        throw new UnsupportedOperationException(
                "DuckDB CSV ingestion requires WASM bridge support. "
                + "Implement via DataModellingBridge.ingestCsvData(csvData, config) when SDK exposes it. "
                + "Expected return: IngestResult with stagingLocation, rowCount, and columnCount from DuckDB.");
    }

    /**
     * Executes a SQL query against the staging database.
     *
     * <p>Useful for data profiling, validation, and testing transformations.</p>
     *
     * @param sql SQL query; must not be null
     * @return query result as JSON string; never null
     * @throws UnsupportedOperationException DuckDB query execution requires WASM bridge support
     * @throws IllegalStateException if this StagingDatabase is closed
     */
    public String executeQuery(String sql) {
        checkNotClosed();
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("sql must not be null or empty");
        }

        throw new UnsupportedOperationException(
                "DuckDB query execution requires WASM bridge support. "
                + "Implement via DataModellingBridge.executeStagingQuery(sql) when SDK exposes it. "
                + "Expected return: JSON string with query results.");
    }

    /**
     * Profiles ingested data for schema inference.
     *
     * <p>Collects statistics on column types, cardinality, null counts, and distributions.</p>
     *
     * @param tableName the DuckDB table to profile; must not be null
     * @return data profile as JSON; never null
     * @throws UnsupportedOperationException DuckDB profiling requires WASM bridge support
     * @throws IllegalStateException if this StagingDatabase is closed
     */
    public String profileData(String tableName) {
        checkNotClosed();
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName must not be null or empty");
        }

        throw new UnsupportedOperationException(
                "DuckDB data profiling requires WASM bridge support. "
                + "Implement via DataModellingBridge.profileTable(databaseId, tableName) when SDK exposes it. "
                + "Expected return: JSON with column statistics (types, cardinality, null counts).");
    }

    /**
     * Deduplicates rows in a staging table.
     *
     * @param tableName the table to deduplicate; must not be null
     * @param strategy deduplication strategy: "exact", "fuzzy", or "semantic"; must not be null
     * @return deduplication result with before/after row counts; never null
     * @throws UnsupportedOperationException DuckDB deduplication requires WASM bridge support
     * @throws IllegalStateException if this StagingDatabase is closed
     */
    public Object deduplicateTable(String tableName, String strategy) {
        checkNotClosed();
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName must not be null or empty");
        }
        if (strategy == null || strategy.trim().isEmpty()) {
            throw new IllegalArgumentException("strategy must not be null or empty");
        }

        throw new UnsupportedOperationException(
                "DuckDB deduplication requires WASM bridge support. "
                + "Implement via DataModellingBridge.deduplicateTable(databaseId, tableName, strategy) "
                + "when SDK exposes it. "
                + "Expected return: JSON with originalRowCount, deduplicatedRowCount, and duplicateRows removed.");
    }

    /**
     * Exports a table from the staging database.
     *
     * @param tableName the table to export; must not be null
     * @param format export format: "json", "csv", "parquet"; must not be null
     * @return exported data as string or file path; never null
     * @throws UnsupportedOperationException DuckDB export requires WASM bridge support
     * @throws IllegalStateException if this StagingDatabase is closed
     */
    public String exportTable(String tableName, String format) {
        checkNotClosed();
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("tableName must not be null or empty");
        }
        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("format must not be null or empty");
        }

        throw new UnsupportedOperationException(
                "DuckDB table export requires WASM bridge support. "
                + "Implement via DataModellingBridge.exportTable(databaseId, tableName, format) "
                + "when SDK exposes it. "
                + "Expected return: exported table data as string (JSON/CSV/Parquet) or file path.");
    }

    /**
     * Returns the database ID for this staging instance.
     *
     * @return the database identifier; never null
     */
    public String getDatabaseId() {
        return databaseId;
    }

    /**
     * Checks if this StagingDatabase is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Closes this StagingDatabase, cleaning up temporary resources.
     *
     * <p>After closing, all operations will throw {@link IllegalStateException}.</p>
     *
     * @throws DataModellingException if cleanup fails
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        try {
            // Cleanup is straightforward: mark as closed
            // Actual DuckDB instance cleanup would require WASM bridge support
            log.debug("Closed StagingDatabase: {}", databaseId);
        } finally {
            closed = true;
        }
    }

    /**
     * Checks that this StagingDatabase is not closed.
     *
     * @throws IllegalStateException if closed
     */
    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("StagingDatabase is closed: " + databaseId);
        }
    }

    /**
     * Extracts a table name from the ingest config or generates a default.
     *
     * @param config the ingest configuration; must not be null
     * @return table name suitable for DuckDB; never null
     */
    private String extractTableName(IngestConfig config) {
        if (config.getSourcePath() != null && !config.getSourcePath().isEmpty()) {
            // Extract filename without extension
            String path = config.getSourcePath();
            int lastSlash = path.lastIndexOf('/');
            int lastBackslash = path.lastIndexOf('\\');
            int lastSep = Math.max(lastSlash, lastBackslash);
            String filename = lastSep >= 0 ? path.substring(lastSep + 1) : path;
            int lastDot = filename.lastIndexOf('.');
            if (lastDot > 0) {
                filename = filename.substring(0, lastDot);
            }
            return filename.replaceAll("[^a-zA-Z0-9_]", "_");
        }
        return "data_" + System.nanoTime();
    }

    @Override
    public String toString() {
        return "StagingDatabase{" +
                "databaseId='" + databaseId + '\'' +
                ", closed=" + closed +
                '}';
    }
}
