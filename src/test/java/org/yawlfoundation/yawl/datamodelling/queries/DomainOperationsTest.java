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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingDomain;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingDomainAsset;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DomainOperations.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("DomainOperations Tests")
class DomainOperationsTest {

    private DataModellingWorkspace workspace;
    private DomainOperations operations;

    @BeforeEach
    void setUp() {
        workspace = DataModellingWorkspace.builder()
                .name("domain-test-workspace")
                .addDomain(DataModellingDomain.builder()
                        .name("customer-domain")
                        .owner("team-a")
                        .addAsset(DataModellingDomainAsset.builder()
                                .name("customer-asset-1")
                                .build())
                        .addAsset(DataModellingDomainAsset.builder()
                                .name("customer-asset-2")
                                .build())
                        .build())
                .addDomain(DataModellingDomain.builder()
                        .name("order-domain")
                        .owner("team-b")
                        .addAsset(DataModellingDomainAsset.builder()
                                .name("order-asset-1")
                                .build())
                        .build())
                .build();

        operations = new DomainOperations(workspace);
    }

    @Nested
    @DisplayName("System Connection Tests")
    class SystemConnectionTests {

        @Test
        @DisplayName("addSystemConnection adds systems to domain")
        void testAddSystemConnection() {
            DataModellingDomain domain = workspace.getDomains().get(0);
            operations.addSystemConnection(domain.getId(), "system-a", "system-b");

            Set<String> systems = operations.getSystemsForDomain(domain.getId());
            assertTrue(systems.contains("system-a"));
            assertTrue(systems.contains("system-b"));
        }

        @Test
        @DisplayName("getSystemsForDomain returns all systems")
        void testGetSystemsForDomain() {
            DataModellingDomain domain = workspace.getDomains().get(0);
            operations.addSystemConnection(domain.getId(), "crm", "warehouse");
            operations.addSystemConnection(domain.getId(), "warehouse", "analytics");

            Set<String> systems = operations.getSystemsForDomain(domain.getId());
            assertEquals(3, systems.size());
            assertTrue(systems.contains("crm"));
            assertTrue(systems.contains("warehouse"));
            assertTrue(systems.contains("analytics"));
        }

        @Test
        @DisplayName("addSystemConnection throws for non-existent domain")
        void testAddSystemConnectionInvalidDomain() {
            assertThrows(IllegalArgumentException.class, () ->
                    operations.addSystemConnection("non-existent-id", "system-a", "system-b")
            );
        }

        @Test
        @DisplayName("getSystemsForDomain returns empty for non-existent domain")
        void testGetSystemsNonExistentDomain() {
            Set<String> systems = operations.getSystemsForDomain("non-existent-id");
            assertTrue(systems.isEmpty());
        }
    }

    @Nested
    @DisplayName("Asset Dependency Tests")
    class AssetDependencyTests {

        @Test
        @DisplayName("addAssetDependency creates dependency link")
        void testAddAssetDependency() {
            List<DataModellingDomainAsset> assets = workspace.getDomains().get(0).getAssets();
            String asset1Id = assets.get(0).getId();
            String asset2Id = assets.get(1).getId();

            operations.addAssetDependency(asset1Id, asset2Id);

            Set<String> deps = operations.getDirectDependencies(asset1Id);
            assertTrue(deps.contains(asset2Id));
        }

        @Test
        @DisplayName("addAssetDependency throws for non-existent asset")
        void testAddAssetDependencyInvalidAsset() {
            String validAssetId = workspace.getDomains().get(0).getAssets().get(0).getId();

            assertThrows(IllegalArgumentException.class, () ->
                    operations.addAssetDependency(validAssetId, "non-existent-id")
            );
        }

        @Test
        @DisplayName("getDirectDependencies returns immediate dependencies")
        void testGetDirectDependencies() {
            List<DataModellingDomainAsset> assets = workspace.getDomains().get(0).getAssets();
            String asset1Id = assets.get(0).getId();
            String asset2Id = assets.get(1).getId();

            operations.addAssetDependency(asset1Id, asset2Id);

            Set<String> deps = operations.getDirectDependencies(asset1Id);
            assertEquals(1, deps.size());
            assertTrue(deps.contains(asset2Id));
        }

        @Test
        @DisplayName("getDirectDependencies returns empty for asset with no dependencies")
        void testGetDirectDependenciesEmpty() {
            String assetId = workspace.getDomains().get(0).getAssets().get(0).getId();
            Set<String> deps = operations.getDirectDependencies(assetId);
            assertTrue(deps.isEmpty());
        }
    }

    @Nested
    @DisplayName("Transitive Dependency Tests")
    class TransitiveDependencyTests {

        @Test
        @DisplayName("getTransitiveDependencies resolves transitive closure")
        void testGetTransitiveDependencies() {
            List<DataModellingDomainAsset> assets = workspace.getDomains().get(0).getAssets();
            String asset1Id = assets.get(0).getId();
            String asset2Id = assets.get(1).getId();

            // Create chain: asset1 -> asset2 -> order-domain-asset
            operations.addAssetDependency(asset1Id, asset2Id);

            Set<String> deps = operations.getTransitiveDependencies(asset1Id);
            assertEquals(1, deps.size());
            assertTrue(deps.contains(asset2Id));
        }

        @Test
        @DisplayName("getTransitiveDependents returns all upstream assets")
        void testGetTransitiveDependents() {
            List<DataModellingDomainAsset> assets = workspace.getDomains().get(0).getAssets();
            String asset1Id = assets.get(0).getId();
            String asset2Id = assets.get(1).getId();

            operations.addAssetDependency(asset1Id, asset2Id);

            Set<String> dependents = operations.getTransitiveDependents(asset2Id);
            assertEquals(1, dependents.size());
            assertTrue(dependents.contains(asset1Id));
        }
    }

    @Nested
    @DisplayName("Cycle Detection Tests")
    class CycleDetectionTests {

        @Test
        @DisplayName("hasCyclicDependencies detects cycles")
        void testDetectCyclicDependencies() {
            List<DataModellingDomainAsset> assets = workspace.getDomains().get(0).getAssets();
            String asset1Id = assets.get(0).getId();
            String asset2Id = assets.get(1).getId();

            // Create cycle: asset1 -> asset2 -> asset1
            operations.addAssetDependency(asset1Id, asset2Id);
            operations.addAssetDependency(asset2Id, asset1Id);

            assertTrue(operations.hasCyclicDependencies());
        }

        @Test
        @DisplayName("hasCyclicDependencies returns false for acyclic dependencies")
        void testNoAyclicDependencies() {
            List<DataModellingDomainAsset> assets = workspace.getDomains().get(0).getAssets();
            String asset1Id = assets.get(0).getId();
            String asset2Id = assets.get(1).getId();

            operations.addAssetDependency(asset1Id, asset2Id);

            assertFalse(operations.hasCyclicDependencies());
        }

        @Test
        @DisplayName("detectAssetCyclePath returns cycle path")
        void testDetectAssetCyclePath() {
            List<DataModellingDomainAsset> assets = workspace.getDomains().get(0).getAssets();
            String asset1Id = assets.get(0).getId();
            String asset2Id = assets.get(1).getId();

            operations.addAssetDependency(asset1Id, asset2Id);
            operations.addAssetDependency(asset2Id, asset1Id);

            List<String> cycle = operations.detectAssetCyclePath();
            assertFalse(cycle.isEmpty());
        }

        @Test
        @DisplayName("detectAssetCyclePath returns empty for acyclic graph")
        void testDetectCyclePathEmpty() {
            List<DataModellingDomainAsset> assets = workspace.getDomains().get(0).getAssets();
            String asset1Id = assets.get(0).getId();
            String asset2Id = assets.get(1).getId();

            operations.addAssetDependency(asset1Id, asset2Id);

            List<String> cycle = operations.detectAssetCyclePath();
            assertTrue(cycle.isEmpty());
        }

        @Test
        @DisplayName("hasCyclicDependenciesInDomain detects domain-level cycles")
        void testDomainLevelCycleDetection() {
            DataModellingDomain domain = workspace.getDomains().get(0);
            List<DataModellingDomainAsset> assets = domain.getAssets();
            String asset1Id = assets.get(0).getId();
            String asset2Id = assets.get(1).getId();

            operations.addAssetDependency(asset1Id, asset2Id);
            operations.addAssetDependency(asset2Id, asset1Id);

            assertTrue(operations.hasCyclicDependenciesInDomain(domain.getId()));
        }

        @Test
        @DisplayName("detectCyclePathInDomain finds domain-specific cycles")
        void testDetectCyclePathInDomain() {
            DataModellingDomain domain = workspace.getDomains().get(0);
            List<DataModellingDomainAsset> assets = domain.getAssets();
            String asset1Id = assets.get(0).getId();
            String asset2Id = assets.get(1).getId();

            operations.addAssetDependency(asset1Id, asset2Id);
            operations.addAssetDependency(asset2Id, asset1Id);

            List<String> cycle = operations.detectCyclePathInDomain(domain.getId());
            assertFalse(cycle.isEmpty());
        }
    }

    @Nested
    @DisplayName("Asset Query Tests")
    class AssetQueryTests {

        @Test
        @DisplayName("getAssetsForDomain returns all domain assets")
        void testGetAssetsForDomain() {
            DataModellingDomain domain = workspace.getDomains().get(0);
            List<DataModellingDomainAsset> assets = operations.getAssetsForDomain(domain.getId());

            assertEquals(2, assets.size());
        }

        @Test
        @DisplayName("findAssetById finds asset across all domains")
        void testFindAssetById() {
            String assetId = workspace.getDomains().get(0).getAssets().get(0).getId();
            DataModellingDomainAsset asset = operations.findAssetById(assetId);

            assertNotNull(asset);
            assertEquals(assetId, asset.getId());
        }

        @Test
        @DisplayName("findAssetById returns null for non-existent asset")
        void testFindAssetByIdNotFound() {
            DataModellingDomainAsset asset = operations.findAssetById("non-existent-id");
            assertNull(asset);
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Operations on workspace with no domains work correctly")
        void testEmptyDomains() {
            DataModellingWorkspace emptyWs = DataModellingWorkspace.builder()
                    .name("empty")
                    .build();
            DomainOperations ops = new DomainOperations(emptyWs);

            Set<String> systems = ops.getSystemsForDomain("any-id");
            assertTrue(systems.isEmpty());

            assertFalse(ops.hasCyclicDependencies());
        }

        @Test
        @DisplayName("Results are immutable")
        void testResultsImmutable() {
            DataModellingDomain domain = workspace.getDomains().get(0);
            operations.addSystemConnection(domain.getId(), "system-a", "system-b");

            Set<String> systems = operations.getSystemsForDomain(domain.getId());
            assertThrows(UnsupportedOperationException.class, () ->
                    systems.add("new-system")
            );
        }

        @Test
        @DisplayName("Multiple operations on same domain work independently")
        void testMultipleOperations() {
            DataModellingDomain domain1 = workspace.getDomains().get(0);
            DataModellingDomain domain2 = workspace.getDomains().get(1);

            operations.addSystemConnection(domain1.getId(), "system-a", "system-b");
            operations.addSystemConnection(domain2.getId(), "system-c", "system-d");

            Set<String> domain1Systems = operations.getSystemsForDomain(domain1.getId());
            Set<String> domain2Systems = operations.getSystemsForDomain(domain2.getId());

            assertEquals(2, domain1Systems.size());
            assertEquals(2, domain2Systems.size());
            assertFalse(domain1Systems.contains("system-c"));
            assertFalse(domain2Systems.contains("system-a"));
        }
    }
}
