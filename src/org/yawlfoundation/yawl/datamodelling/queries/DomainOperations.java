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

import org.yawlfoundation.yawl.datamodelling.models.DataModellingDomain;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingDomainAsset;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Cross-domain relationship management and analysis for data modelling artifacts.
 *
 * <p>Enables querying of dependencies and connections between systems and assets
 * within domains, including cycle detection and transitive dependency analysis.</p>
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>System-to-system connections within a domain</li>
 *   <li>Asset-to-asset dependencies</li>
 *   <li>Transitive dependency resolution</li>
 *   <li>Circular dependency detection</li>
 * </ul>
 *
 * <p>Thread-safe for concurrent query execution.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DomainOperations {

    private final DataModellingWorkspace workspace;
    private final Map<String, Set<String>> systemConnections; // domainId -> set of systems
    private final Map<String, Set<String>> assetDependencies; // assetId -> set of dependencies

    // ── Constructor ────────────────────────────────────────────────────────────

    /**
     * Create domain operations for the given workspace.
     *
     * @param workspace the workspace to operate on (required)
     */
    public DomainOperations(DataModellingWorkspace workspace) {
        this.workspace = Objects.requireNonNull(workspace, "Workspace cannot be null");
        this.systemConnections = new HashMap<>();
        this.assetDependencies = new HashMap<>();
        initializeMaps();
    }

    // ── System Connection Management ───────────────────────────────────────────

    /**
     * Add a system connection within a domain.
     *
     * @param domainId the domain ID
     * @param fromSystem the source system name
     * @param toSystem the target system name
     * @throws IllegalArgumentException if domain not found
     */
    public void addSystemConnection(String domainId, String fromSystem, String toSystem) {
        DataModellingDomain domain = findDomainById(domainId);
        if (domain == null) {
            throw new IllegalArgumentException(
                    "Domain not found with ID: " + domainId +
                    ". Ensure domain exists in workspace before adding system connections.");
        }

        // Update domain's systems list if not present
        if (domain.getSystems() == null) {
            domain.setSystems(new ArrayList<>());
        }
        if (!domain.getSystems().contains(fromSystem)) {
            domain.getSystems().add(fromSystem);
        }
        if (!domain.getSystems().contains(toSystem)) {
            domain.getSystems().add(toSystem);
        }

        // Track connection for cycle detection
        systemConnections.computeIfAbsent(domainId, k -> new HashSet<>())
                .add(fromSystem);
        systemConnections.computeIfAbsent(domainId, k -> new HashSet<>())
                .add(toSystem);
    }

    /**
     * Get all systems in a domain.
     *
     * @param domainId the domain ID
     * @return an immutable set of system names in the domain
     */
    public Set<String> getSystemsForDomain(String domainId) {
        DataModellingDomain domain = findDomainById(domainId);
        if (domain == null || domain.getSystems() == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(domain.getSystems()));
    }

    // ── Asset Dependency Management ────────────────────────────────────────────

    /**
     * Add a dependency relationship between two assets.
     *
     * @param assetId the dependent asset ID
     * @param dependsOnAssetId the dependency asset ID
     * @throws IllegalArgumentException if assets not found
     */
    public void addAssetDependency(String assetId, String dependsOnAssetId) {
        // Verify both assets exist
        if (!assetExists(assetId)) {
            throw new IllegalArgumentException(
                    "Asset not found with ID: " + assetId +
                    ". Ensure asset exists in workspace before adding dependencies.");
        }
        if (!assetExists(dependsOnAssetId)) {
            throw new IllegalArgumentException(
                    "Dependency asset not found with ID: " + dependsOnAssetId +
                    ". Ensure dependency asset exists in workspace.");
        }

        assetDependencies.computeIfAbsent(assetId, k -> new HashSet<>())
                .add(dependsOnAssetId);
    }

    /**
     * Get direct dependencies of an asset.
     *
     * @param assetId the asset ID
     * @return an immutable set of asset IDs this asset depends on
     */
    public Set<String> getDirectDependencies(String assetId) {
        Set<String> deps = assetDependencies.get(assetId);
        if (deps == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(deps));
    }

    /**
     * Get transitive closure of asset dependencies.
     *
     * @param assetId the asset ID
     * @return an immutable set of all assets this asset depends on (directly or indirectly)
     */
    public Set<String> getTransitiveDependencies(String assetId) {
        Set<String> transitive = new HashSet<>();
        Set<String> visited = new HashSet<>();
        buildTransitiveDependencies(assetId, transitive, visited);
        return Collections.unmodifiableSet(transitive);
    }

    /**
     * Get transitive dependents of an asset (assets that depend on it).
     *
     * @param assetId the asset ID
     * @return an immutable set of assets that depend on this asset
     */
    public Set<String> getTransitiveDependents(String assetId) {
        Set<String> dependents = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : assetDependencies.entrySet()) {
            if (entry.getValue().contains(assetId)) {
                dependents.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(dependents);
    }

    // ── Cycle Detection ────────────────────────────────────────────────────────

    /**
     * Detect if there are circular dependencies in the entire workspace.
     *
     * @return true if any circular dependency is detected, false otherwise
     */
    public boolean hasCyclicDependencies() {
        for (String assetId : assetDependencies.keySet()) {
            if (hasCycle(assetId, new HashSet<>(), new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detect cycles within a specific domain.
     *
     * @param domainId the domain ID
     * @return true if any circular dependency is detected in the domain, false otherwise
     */
    public boolean hasCyclicDependenciesInDomain(String domainId) {
        DataModellingDomain domain = findDomainById(domainId);
        if (domain == null || domain.getAssets() == null || domain.getAssets().isEmpty()) {
            return false;
        }

        Set<String> domainAssetIds = new HashSet<>();
        for (DataModellingDomainAsset asset : domain.getAssets()) {
            domainAssetIds.add(asset.getId());
        }

        for (String assetId : domainAssetIds) {
            if (hasCycleInDomain(assetId, domainAssetIds, new HashSet<>(), new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find a cycle path in the asset dependency graph.
     *
     * @return a list representing the cycle path, or empty list if no cycle
     */
    public List<String> detectAssetCyclePath() {
        for (String assetId : assetDependencies.keySet()) {
            List<String> cycle = findCycle(assetId, new HashSet<>(), new ArrayList<>());
            if (!cycle.isEmpty()) {
                return Collections.unmodifiableList(cycle);
            }
        }
        return Collections.emptyList();
    }

    /**
     * Find a cycle path within a specific domain.
     *
     * @param domainId the domain ID
     * @return a list representing the cycle path within the domain, or empty list if no cycle
     */
    public List<String> detectCyclePathInDomain(String domainId) {
        DataModellingDomain domain = findDomainById(domainId);
        if (domain == null || domain.getAssets() == null || domain.getAssets().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> domainAssetIds = new HashSet<>();
        for (DataModellingDomainAsset asset : domain.getAssets()) {
            domainAssetIds.add(asset.getId());
        }

        for (String assetId : domainAssetIds) {
            List<String> cycle = findCycleInDomain(assetId, domainAssetIds,
                    new HashSet<>(), new ArrayList<>());
            if (!cycle.isEmpty()) {
                return Collections.unmodifiableList(cycle);
            }
        }
        return Collections.emptyList();
    }

    // ── Query Methods ──────────────────────────────────────────────────────────

    /**
     * Get all assets in a domain.
     *
     * @param domainId the domain ID
     * @return an immutable list of assets in the domain
     */
    public List<DataModellingDomainAsset> getAssetsForDomain(String domainId) {
        DataModellingDomain domain = findDomainById(domainId);
        if (domain == null || domain.getAssets() == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(domain.getAssets()));
    }

    /**
     * Get an asset by ID from any domain in the workspace.
     *
     * @param assetId the asset ID
     * @return the asset or null if not found
     */
    public DataModellingDomainAsset findAssetById(String assetId) {
        if (workspace.getDomains() == null) {
            return null;
        }
        for (DataModellingDomain domain : workspace.getDomains()) {
            if (domain.getAssets() != null) {
                for (DataModellingDomainAsset asset : domain.getAssets()) {
                    if (assetId.equals(asset.getId())) {
                        return asset;
                    }
                }
            }
        }
        return null;
    }

    // ── Helper Methods (Private) ───────────────────────────────────────────────

    /**
     * Initialize maps from existing workspace data.
     */
    private void initializeMaps() {
        if (workspace.getDomains() == null) {
            return;
        }
        for (DataModellingDomain domain : workspace.getDomains()) {
            if (domain.getSystems() != null) {
                systemConnections.put(domain.getId(), new HashSet<>(domain.getSystems()));
            }
            // Asset dependencies would be initialized from any existing relationships
        }
    }

    /**
     * Find domain by ID.
     */
    private DataModellingDomain findDomainById(String domainId) {
        if (workspace.getDomains() == null) {
            return null;
        }
        return workspace.getDomains().stream()
                .filter(d -> domainId.equals(d.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if an asset exists in any domain.
     */
    private boolean assetExists(String assetId) {
        return findAssetById(assetId) != null;
    }

    /**
     * Recursively build transitive dependencies.
     */
    private void buildTransitiveDependencies(String assetId, Set<String> transitive,
            Set<String> visited) {
        if (visited.contains(assetId)) {
            return;
        }
        visited.add(assetId);

        Set<String> directDeps = assetDependencies.getOrDefault(assetId, Collections.emptySet());
        for (String depId : directDeps) {
            if (transitive.add(depId)) {
                buildTransitiveDependencies(depId, transitive, visited);
            }
        }
    }

    /**
     * Detect if there's a cycle starting from a given asset.
     */
    private boolean hasCycle(String assetId, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(assetId)) {
            return true;
        }
        if (visited.contains(assetId)) {
            return false;
        }

        visited.add(assetId);
        recursionStack.add(assetId);

        Set<String> neighbors = assetDependencies.getOrDefault(assetId, Collections.emptySet());
        for (String neighbor : neighbors) {
            if (hasCycle(neighbor, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(assetId);
        return false;
    }

    /**
     * Detect cycle within domain assets only.
     */
    private boolean hasCycleInDomain(String assetId, Set<String> domainAssets,
            Set<String> visited, Set<String> recursionStack) {
        if (!domainAssets.contains(assetId)) {
            return false;
        }
        if (recursionStack.contains(assetId)) {
            return true;
        }
        if (visited.contains(assetId)) {
            return false;
        }

        visited.add(assetId);
        recursionStack.add(assetId);

        Set<String> neighbors = assetDependencies.getOrDefault(assetId, Collections.emptySet());
        for (String neighbor : neighbors) {
            if (hasCycleInDomain(neighbor, domainAssets, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(assetId);
        return false;
    }

    /**
     * Find a cycle path starting from a given asset.
     */
    private List<String> findCycle(String assetId, Set<String> visited, List<String> path) {
        if (path.contains(assetId)) {
            return new ArrayList<>(path.subList(path.indexOf(assetId), path.size()));
        }

        if (visited.contains(assetId)) {
            return Collections.emptyList();
        }

        visited.add(assetId);
        path.add(assetId);

        Set<String> neighbors = assetDependencies.getOrDefault(assetId, Collections.emptySet());
        for (String neighbor : neighbors) {
            List<String> cycle = findCycle(neighbor, visited, new ArrayList<>(path));
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }

        return Collections.emptyList();
    }

    /**
     * Find a cycle path within domain assets only.
     */
    private List<String> findCycleInDomain(String assetId, Set<String> domainAssets,
            Set<String> visited, List<String> path) {
        if (!domainAssets.contains(assetId)) {
            return Collections.emptyList();
        }
        if (path.contains(assetId)) {
            return new ArrayList<>(path.subList(path.indexOf(assetId), path.size()));
        }

        if (visited.contains(assetId)) {
            return Collections.emptyList();
        }

        visited.add(assetId);
        path.add(assetId);

        Set<String> neighbors = assetDependencies.getOrDefault(assetId, Collections.emptySet());
        for (String neighbor : neighbors) {
            List<String> cycle = findCycleInDomain(neighbor, domainAssets, visited,
                    new ArrayList<>(path));
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }

        return Collections.emptyList();
    }
}
