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

/**
 * Bidirectional database synchronization layer.
 *
 * <p><strong>Phase 4: Database Sync</strong> provides thread-safe, idempotent
 * synchronization between YAWL data models and external database backends
 * (DuckDB, PostgreSQL).</p>
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.sync.DataModellingDatabaseSync}:
 *       Sync engine wrapper over WASM SDK database capabilities</li>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.sync.DatabaseBackendConfig}:
 *       Backend connection details and sync strategy</li>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.sync.SyncResult}:
 *       Typed outcome with change detection and checkpoint state</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Multi-backend support</strong>: DuckDB and PostgreSQL</li>
 *   <li><strong>Sync strategies</strong>: Full, incremental, delete-safe</li>
 *   <li><strong>Change detection</strong>: Track added, modified, deleted records</li>
 *   <li><strong>Checkpointing</strong>: Resume interrupted syncs from checkpoint</li>
 *   <li><strong>Thread-safety</strong>: Concurrent sync operations on different backends</li>
 *   <li><strong>Idempotency</strong>: Safe to retry failed operations</li>
 *   <li><strong>Audit trail</strong>: RDF export of all DB changes via DataLineageTracker</li>
 * </ul>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * try (DataModellingBridge bridge = new DataModellingBridge()) {
 *     DataModellingDatabaseSync sync = new DataModellingDatabaseSync(bridge);
 *
 *     // Configure DuckDB backend
 *     DatabaseBackendConfig config = DatabaseBackendConfig.builder()
 *         .backendType("duckdb")
 *         .connectionString(":memory:")
 *         .syncStrategy(SyncStrategy.INCREMENTAL)
 *         .checkpointPath("/tmp/sync-checkpoint.json")
 *         .build();
 *
 *     // Sync workspace to database
 *     SyncResult result = sync.syncWorkspaceToDB("my-workspace", config);
 *     System.out.println("Added: " + result.recordsAdded());
 *     System.out.println("Updated: " + result.recordsModified());
 *     System.out.println("Checkpoint: " + result.checkpointJson());
 * }
 * }</pre>
 *
 * <h2>Integration with Phase 0</h2>
 * <p>All sync operations are recorded via {@link org.yawlfoundation.yawl.elements.data.contract.DataLineageTracker}:</p>
 * <ul>
 *   <li>Tuple format: {@code (case-id, activity, table-change)}</li>
 *   <li>RDF export: Audit trail of all database modifications</li>
 *   <li>Compliance: Enables data provenance and impact analysis</li>
 * </ul>
 *
 * @author YAWL Foundation (Teammate 4 - Engineer)
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.datamodelling.sync;
