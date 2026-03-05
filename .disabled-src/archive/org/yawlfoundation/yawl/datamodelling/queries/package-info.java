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
 * Phase 5: Advanced Filtering & Querying for Data Modelling Workspaces.
 *
 * <p>This package provides a comprehensive query layer for analyzing and filtering
 * data modelling artifacts including tables, relationships, domains, and assets.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li><strong>DataModellingQueryBuilder</strong>: Fluent API for querying workspaces
 *       with support for filtering by owner, infrastructure, medallion layer, and tags.
 *       Enables complex relationship analysis including transitive dependencies,
 *       impact analysis, and cycle detection.</li>
 *   <li><strong>AdvancedFiltering</strong>: Predicate factories for table filtering
 *       with support for owner, infrastructure type, medallion layer, and tag-based
 *       queries with boolean combinations (AND, OR, NOT).</li>
 *   <li><strong>DomainOperations</strong>: Cross-domain relationship management
 *       including system connections, asset dependencies, and cycle detection
 *       within domain boundaries.</li>
 * </ul>
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><strong>Type-Safe Queries</strong>: All queries operate on Java 25+ typed models
 *       from Phase 1, not raw JSON strings.</li>
 *   <li><strong>Fluent API</strong>: Chainable filter methods for expressive queries.</li>
 *   <li><strong>Thread-Safe</strong>: All query builders are thread-safe for concurrent use.</li>
 *   <li><strong>Immutable Results</strong>: Query results returned as unmodifiable collections
 *       to prevent accidental mutations.</li>
 *   <li><strong>Performance</strong>: Relationship caching and efficient graph traversal
 *       for large workspaces.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Filter tables by owner and medallion layer
 * List<DataModellingTable> silverTables = DataModellingQueryBuilder
 *     .forWorkspace(workspace)
 *     .filterTablesByOwner("data-team")
 *     .filterTablesByMedallionLayer("silver")
 *     .getTables();
 *
 * // Find all tables impacted by changes to a specific table
 * List<DataModellingTable> impacted = DataModellingQueryBuilder
 *     .forWorkspace(workspace)
 *     .getImpactAnalysis("orders-table-id");
 *
 * // Detect circular dependencies
 * if (DataModellingQueryBuilder.forWorkspace(workspace).hasCyclicDependencies()) {
 *     List<String> cycle = DataModellingQueryBuilder
 *         .forWorkspace(workspace)
 *         .detectCyclePath();
 * }
 *
 * // Domain-level operations
 * DomainOperations ops = new DomainOperations(workspace);
 * ops.addAssetDependency("asset1-id", "asset2-id");
 * Set<String> transitive = ops.getTransitiveDependencies("asset1-id");
 * }</pre>
 *
 * <h2>RDF Integration (Phase 0)</h2>
 * <p>The query layer integrates with Phase 0's RDF export capabilities for advanced
 * lineage analysis. Future versions will support SPARQL queries over RDF graphs for:</p>
 * <ul>
 *   <li>Process-data lineage: Which workflows read/write table X?</li>
 *   <li>Data provenance: Complete lineage for a specific case/instance</li>
 *   <li>Impact analysis: Unified process + data flow visualization</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All query builders and operations are stateless and thread-safe. Multiple threads
 * can safely query the same workspace concurrently via different builder instances.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.datamodelling.queries;
