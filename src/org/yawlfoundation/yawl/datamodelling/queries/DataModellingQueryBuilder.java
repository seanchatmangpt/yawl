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

package org.yawlfoundation.yawl.datamodelling.queries;

import org.jspecify.annotations.Nullable;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingRelationship;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingTable;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Fluent query builder for advanced filtering and relationship analysis on
 * data modelling workspaces.
 *
 * <p>Provides a chainable API for querying tables, filtering by owner/infrastructure/tags,
 * and analyzing relationships and dependencies. Thread-safe for concurrent query execution.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * List<DataModellingTable> silverTables = DataModellingQueryBuilder
 *     .forWorkspace(workspace)
 *     .filterTablesByOwner("data-team")
 *     .filterTablesByMedallionLayer("silver")
 *     .getTables();
 *
 * Set<String> dependencies = DataModellingQueryBuilder
 *     .forWorkspace(workspace)
 *     .getTransitiveDependencies("orders-table-id");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DataModellingQueryBuilder {

    private final DataModellingWorkspace workspace;
    private final List<Predicate<DataModellingTable>> tablePredicates;
    private final Map<String, List<String>> relationshipCache;

    // ── Constructor ────────────────────────────────────────────────────────────

    private DataModellingQueryBuilder(DataModellingWorkspace workspace) {
        this.workspace = Objects.requireNonNull(workspace, "Workspace cannot be null");
        this.tablePredicates = new ArrayList<>();
        this.relationshipCache = buildRelationshipCache(workspace);
    }

    /**
     * Create a new query builder for the given workspace.
     *
     * @param workspace the workspace to query (required)
     * @return a new query builder
     */
    public static DataModellingQueryBuilder forWorkspace(DataModellingWorkspace workspace) {
        return new DataModellingQueryBuilder(workspace);
    }

    // ── Filter Methods (Chainable) ─────────────────────────────────────────────

    /**
     * Add a filter for tables owned by the specified owner.
     *
     * @param owner the owner name/email
     * @return this builder for chaining
     */
    public DataModellingQueryBuilder filterTablesByOwner(String owner) {
        tablePredicates.add(AdvancedFiltering.byOwner(owner));
        return this;
    }

    /**
     * Add a filter for tables with the specified infrastructure type.
     *
     * @param infrastructureType the infrastructure type (e.g. "postgresql", "warehouse")
     * @return this builder for chaining
     */
    public DataModellingQueryBuilder filterTablesByInfrastructureType(String infrastructureType) {
        tablePredicates.add(AdvancedFiltering.byInfrastructureType(infrastructureType));
        return this;
    }

    /**
     * Add a filter for tables with the specified medallion layer.
     *
     * @param layer the medallion layer (e.g. "bronze", "silver", "gold")
     * @return this builder for chaining
     */
    public DataModellingQueryBuilder filterTablesByMedallionLayer(String layer) {
        tablePredicates.add(AdvancedFiltering.byMedallionLayer(layer));
        return this;
    }

    /**
     * Add a filter for tables with the specified tag.
     *
     * @param tag the tag value to filter by
     * @return this builder for chaining
     */
    public DataModellingQueryBuilder filterTablesByTag(String tag) {
        tablePredicates.add(AdvancedFiltering.byTag(tag));
        return this;
    }

    /**
     * Add a custom filter predicate.
     *
     * @param predicate the predicate to apply
     * @return this builder for chaining
     */
    public DataModellingQueryBuilder filterTables(Predicate<DataModellingTable> predicate) {
        tablePredicates.add(predicate);
        return this;
    }

    // ── Terminal Query Methods ─────────────────────────────────────────────────

    /**
     * Execute the query and return filtered tables.
     *
     * @return an immutable list of tables matching all filters
     */
    public List<DataModellingTable> getTables() {
        if (workspace.getTables() == null || workspace.getTables().isEmpty()) {
            return Collections.emptyList();
        }

        Predicate<DataModellingTable> combinedPredicate = tablePredicates.stream()
                .reduce(t -> true, Predicate::and);

        return Collections.unmodifiableList(
                workspace.getTables().stream()
                        .filter(combinedPredicate)
                        .collect(Collectors.toList())
        );
    }

    /**
     * Get a single table by ID.
     *
     * @param tableId the table ID
     * @return the table or null if not found
     */
    @Nullable
    public DataModellingTable getTableById(String tableId) {
        if (workspace.getTables() == null) {
            return null;
        }
        return workspace.getTables().stream()
                .filter(t -> tableId.equals(t.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all tables related to the specified table via direct relationships.
     *
     * @param tableId the table ID
     * @return an immutable set of related table IDs
     */
    public Set<String> getRelatedTables(String tableId) {
        Set<String> related = new HashSet<>();
        if (workspace.getRelationships() == null) {
            return Collections.unmodifiableSet(related);
        }

        for (DataModellingRelationship rel : workspace.getRelationships()) {
            if (tableId.equals(rel.getSourceTableId())) {
                related.add(rel.getTargetTableId());
            } else if (tableId.equals(rel.getTargetTableId())) {
                related.add(rel.getSourceTableId());
            }
        }
        return Collections.unmodifiableSet(related);
    }

    /**
     * Get all relationships for a specific table (both as source and target).
     *
     * @param tableId the table ID
     * @return an immutable list of relationships involving this table
     */
    public List<DataModellingRelationship> getRelationshipsForTable(String tableId) {
        if (workspace.getRelationships() == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(
                workspace.getRelationships().stream()
                        .filter(r -> tableId.equals(r.getSourceTableId()) ||
                                tableId.equals(r.getTargetTableId()))
                        .collect(Collectors.toList())
        );
    }

    /**
     * Get all tables that reference the specified table (inbound relationships).
     *
     * @param tableId the table ID
     * @return an immutable list of tables that reference this table
     */
    public List<DataModellingTable> getAllReferencingTables(String tableId) {
        if (workspace.getRelationships() == null) {
            return Collections.emptyList();
        }

        Set<String> referencingIds = workspace.getRelationships().stream()
                .filter(r -> tableId.equals(r.getTargetTableId()))
                .map(DataModellingRelationship::getSourceTableId)
                .collect(Collectors.toSet());

        if (referencingIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<DataModellingTable> referencingTables = new ArrayList<>();
        for (DataModellingTable table : workspace.getTables()) {
            if (referencingIds.contains(table.getId())) {
                referencingTables.add(table);
            }
        }
        return Collections.unmodifiableList(referencingTables);
    }

    /**
     * Get transitive closure of dependencies for a table.
     * Returns all tables that the specified table depends on (directly or indirectly).
     *
     * @param tableId the table ID
     * @return an immutable set of all transitive dependency table IDs
     */
    public Set<String> getTransitiveDependencies(String tableId) {
        Set<String> dependencies = new HashSet<>();
        Set<String> visited = new HashSet<>();
        buildTransitiveDependencies(tableId, dependencies, visited);
        return Collections.unmodifiableSet(dependencies);
    }

    /**
     * Get the transitive closure of dependents for a table.
     * Returns all tables that depend on the specified table (directly or indirectly).
     *
     * @param tableId the table ID
     * @return an immutable set of all transitive dependent table IDs
     */
    public Set<String> getTransitiveDependents(String tableId) {
        Set<String> dependents = new HashSet<>();
        Set<String> visited = new HashSet<>();
        buildTransitiveDependents(tableId, dependents, visited);
        return Collections.unmodifiableSet(dependents);
    }

    /**
     * Detect if there are circular dependencies in the workspace.
     *
     * @return true if any circular dependency is detected, false otherwise
     */
    public boolean hasCyclicDependencies() {
        if (workspace.getTables() == null || workspace.getTables().isEmpty()) {
            return false;
        }
        for (DataModellingTable table : workspace.getTables()) {
            if (hasCycle(table.getId(), new HashSet<>(), new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detect circular dependencies and return the cycle path if found.
     *
     * @return a list representing the cycle path, or empty list if no cycle
     */
    public List<String> detectCyclePath() {
        if (workspace.getTables() == null || workspace.getTables().isEmpty()) {
            return Collections.emptyList();
        }
        for (DataModellingTable table : workspace.getTables()) {
            List<String> cycle = findCycle(table.getId(), new HashSet<>(), new ArrayList<>());
            if (!cycle.isEmpty()) {
                return Collections.unmodifiableList(cycle);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Get impact analysis: which tables are impacted if the specified table changes.
     *
     * @param tableId the table ID
     * @return an immutable list of impacted tables
     */
    public List<DataModellingTable> getImpactAnalysis(String tableId) {
        Set<String> impactedIds = getTransitiveDependents(tableId);
        if (impactedIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<DataModellingTable> impacted = new ArrayList<>();
        if (workspace.getTables() != null) {
            for (DataModellingTable table : workspace.getTables()) {
                if (impactedIds.contains(table.getId())) {
                    impacted.add(table);
                }
            }
        }
        return Collections.unmodifiableList(impacted);
    }

    // ── Helper Methods (Private) ───────────────────────────────────────────────

    /**
     * Build a cache of table relationships for efficient traversal.
     * Cache structure: tableId -> list of tables it depends on.
     */
    private Map<String, List<String>> buildRelationshipCache(DataModellingWorkspace ws) {
        Map<String, List<String>> cache = new HashMap<>();
        if (ws.getRelationships() == null) {
            return cache;
        }

        for (DataModellingRelationship rel : ws.getRelationships()) {
            if ("dataFlow".equals(rel.getRelationshipType()) ||
                "dependency".equals(rel.getRelationshipType())) {
                String sourceId = rel.getSourceTableId();
                cache.computeIfAbsent(sourceId, k -> new ArrayList<>())
                        .add(rel.getTargetTableId());
            }
        }
        return cache;
    }

    /**
     * Recursively build transitive dependencies.
     */
    private void buildTransitiveDependencies(String tableId, Set<String> dependencies,
            Set<String> visited) {
        if (visited.contains(tableId)) {
            return;
        }
        visited.add(tableId);

        List<String> directDeps = relationshipCache.getOrDefault(tableId, Collections.emptyList());
        for (String depId : directDeps) {
            if (dependencies.add(depId)) {
                buildTransitiveDependencies(depId, dependencies, visited);
            }
        }
    }

    /**
     * Recursively build transitive dependents (reverse dependencies).
     */
    private void buildTransitiveDependents(String tableId, Set<String> dependents,
            Set<String> visited) {
        if (visited.contains(tableId)) {
            return;
        }
        visited.add(tableId);

        // Find all tables that depend on tableId
        for (Map.Entry<String, List<String>> entry : relationshipCache.entrySet()) {
            if (entry.getValue().contains(tableId)) {
                String dependentId = entry.getKey();
                if (dependents.add(dependentId)) {
                    buildTransitiveDependents(dependentId, dependents, visited);
                }
            }
        }
    }

    /**
     * Detect if there's a cycle starting from a given table.
     */
    private boolean hasCycle(String tableId, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(tableId)) {
            return true;
        }
        if (visited.contains(tableId)) {
            return false;
        }

        visited.add(tableId);
        recursionStack.add(tableId);

        List<String> neighbors = relationshipCache.getOrDefault(tableId, Collections.emptyList());
        for (String neighbor : neighbors) {
            if (hasCycle(neighbor, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(tableId);
        return false;
    }

    /**
     * Find a cycle path in the graph starting from a given table.
     */
    private List<String> findCycle(String tableId, Set<String> visited, List<String> path) {
        if (path.contains(tableId)) {
            // Found cycle: return from first occurrence
            return new ArrayList<>(path.subList(path.indexOf(tableId), path.size()));
        }

        if (visited.contains(tableId)) {
            return Collections.emptyList();
        }

        visited.add(tableId);
        path.add(tableId);

        List<String> neighbors = relationshipCache.getOrDefault(tableId, Collections.emptyList());
        for (String neighbor : neighbors) {
            List<String> cycle = findCycle(neighbor, visited, new ArrayList<>(path));
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }

        return Collections.emptyList();
    }
}
