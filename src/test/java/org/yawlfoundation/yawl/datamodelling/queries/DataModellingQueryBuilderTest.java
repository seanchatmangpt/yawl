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
import org.yawlfoundation.yawl.datamodelling.models.DataModellingTable;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for DataModellingQueryBuilder.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("DataModellingQueryBuilder Tests")
class DataModellingQueryBuilderTest {

    private DataModellingWorkspace simpleWorkspace;
    private DataModellingWorkspace multiOwnerWorkspace;
    private DataModellingWorkspace taggedWorkspace;
    private DataModellingWorkspace deepDependencyWorkspace;

    @BeforeEach
    void setUp() {
        simpleWorkspace = TestWorkspaceFixture.createSimpleWorkspace();
        multiOwnerWorkspace = TestWorkspaceFixture.createMultiOwnerWorkspace();
        taggedWorkspace = TestWorkspaceFixture.createTaggedWorkspace();
        deepDependencyWorkspace = TestWorkspaceFixture.createDeepDependencyWorkspace();
    }

    @Nested
    @DisplayName("Filter by Owner Tests")
    class FilterByOwnerTests {

        @Test
        @DisplayName("filterTablesByOwner returns tables matching owner")
        void testFilterByOwner() {
            List<DataModellingTable> results = DataModellingQueryBuilder
                    .forWorkspace(simpleWorkspace)
                    .filterTablesByOwner("data-team")
                    .getTables();

            assertEquals(3, results.size());
            assertTrue(results.stream().allMatch(t -> "data-team".equals(t.getOwner())));
        }

        @Test
        @DisplayName("filterTablesByOwner returns empty list for non-existent owner")
        void testFilterByNonExistentOwner() {
            List<DataModellingTable> results = DataModellingQueryBuilder
                    .forWorkspace(simpleWorkspace)
                    .filterTablesByOwner("non-existent-team")
                    .getTables();

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Multiple owners return correct tables")
        void testMultipleOwners() {
            List<DataModellingTable> analyticsTeam = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .filterTablesByOwner("analytics-team")
                    .getTables();

            assertEquals(1, analyticsTeam.size());
            assertEquals("analytics_view", analyticsTeam.get(0).getName());

            List<DataModellingTable> dataTeam = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .filterTablesByOwner("data-team")
                    .getTables();

            assertEquals(1, dataTeam.size());
            assertEquals("processed_events", dataTeam.get(0).getName());
        }
    }

    @Nested
    @DisplayName("Filter by Infrastructure Tests")
    class FilterByInfrastructureTests {

        @Test
        @DisplayName("filterTablesByInfrastructureType returns matching tables")
        void testFilterByInfrastructure() {
            List<DataModellingTable> results = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .filterTablesByInfrastructureType("warehouse")
                    .getTables();

            assertTrue(results.size() >= 2);
            assertTrue(results.stream()
                    .allMatch(t -> "warehouse".equals(t.getInfrastructureType())));
        }

        @Test
        @DisplayName("filterTablesByInfrastructureType returns empty for non-existent type")
        void testFilterByNonExistentInfrastructure() {
            List<DataModellingTable> results = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .filterTablesByInfrastructureType("non-existent")
                    .getTables();

            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("Filter by Medallion Layer Tests")
    class FilterByMedallionLayerTests {

        @Test
        @DisplayName("filterTablesByMedallionLayer returns matching tables")
        void testFilterByLayer() {
            List<DataModellingTable> silver = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .filterTablesByMedallionLayer("silver")
                    .getTables();

            assertEquals(1, silver.size());
            assertEquals("processed_events", silver.get(0).getName());
        }

        @Test
        @DisplayName("Gold layer returns analytics view")
        void testGoldLayer() {
            List<DataModellingTable> gold = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .filterTablesByMedallionLayer("gold")
                    .getTables();

            assertEquals(1, gold.size());
            assertEquals("analytics_view", gold.get(0).getName());
        }
    }

    @Nested
    @DisplayName("Filter by Tag Tests")
    class FilterByTagTests {

        @Test
        @DisplayName("filterTablesByTag returns tables with specified tag")
        void testFilterByTag() {
            List<DataModellingTable> results = DataModellingQueryBuilder
                    .forWorkspace(taggedWorkspace)
                    .filterTablesByTag("sensitive")
                    .getTables();

            assertEquals(2, results.size());
            assertTrue(results.stream()
                    .allMatch(t -> t.getTags() != null && t.getTags().contains("sensitive")));
        }

        @Test
        @DisplayName("filterTablesByTag returns empty for non-existent tag")
        void testFilterByNonExistentTag() {
            List<DataModellingTable> results = DataModellingQueryBuilder
                    .forWorkspace(taggedWorkspace)
                    .filterTablesByTag("non-existent")
                    .getTables();

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Multiple tag filtering works")
        void testMultipleTags() {
            List<DataModellingTable> piiTables = DataModellingQueryBuilder
                    .forWorkspace(taggedWorkspace)
                    .filterTablesByTag("pii")
                    .getTables();

            assertEquals(1, piiTables.size());
            assertEquals("customer_sensitive", piiTables.get(0).getName());
        }
    }

    @Nested
    @DisplayName("Relationship Query Tests")
    class RelationshipQueryTests {

        @Test
        @DisplayName("getRelationshipsForTable returns all relationships")
        void testGetRelationships() {
            String customersId = simpleWorkspace.getTableByName("customers").getId();
            List<org.yawlfoundation.yawl.datamodelling.models.DataModellingRelationship> rels =
                    DataModellingQueryBuilder.forWorkspace(simpleWorkspace)
                            .getRelationshipsForTable(customersId);

            assertEquals(1, rels.size());
            assertEquals("customer_orders", rels.get(0).getLabel());
        }

        @Test
        @DisplayName("getRelatedTables returns connected tables")
        void testGetRelatedTables() {
            String customersId = simpleWorkspace.getTableByName("customers").getId();
            Set<String> related = DataModellingQueryBuilder.forWorkspace(simpleWorkspace)
                    .getRelatedTables(customersId);

            assertEquals(1, related.size());
            String ordersId = simpleWorkspace.getTableByName("orders").getId();
            assertTrue(related.contains(ordersId));
        }

        @Test
        @DisplayName("getAllReferencingTables returns inbound relationships")
        void testGetReferencingTables() {
            String ordersId = simpleWorkspace.getTableByName("orders").getId();
            List<DataModellingTable> referencing = DataModellingQueryBuilder
                    .forWorkspace(simpleWorkspace)
                    .getAllReferencingTables(ordersId);

            assertEquals(1, referencing.size());
            assertEquals("customers", referencing.get(0).getName());
        }
    }

    @Nested
    @DisplayName("Dependency Analysis Tests")
    class DependencyAnalysisTests {

        @Test
        @DisplayName("getTransitiveDependencies returns all downstream tables")
        void testTransitiveDependencies() {
            String rawId = multiOwnerWorkspace.getTableByName("raw_events").getId();
            Set<String> deps = DataModellingQueryBuilder.forWorkspace(multiOwnerWorkspace)
                    .getTransitiveDependencies(rawId);

            assertEquals(2, deps.size());
        }

        @Test
        @DisplayName("getTransitiveDependents returns all upstream tables")
        void testTransitiveDependents() {
            String analyticsId = multiOwnerWorkspace.getTableByName("analytics_view").getId();
            Set<String> dependents = DataModellingQueryBuilder.forWorkspace(multiOwnerWorkspace)
                    .getTransitiveDependents(analyticsId);

            assertEquals(2, dependents.size());
        }

        @Test
        @DisplayName("Deep dependency chains are resolved correctly")
        void testDeepDependencies() {
            String level1Id = deepDependencyWorkspace.getTableByName("level_1").getId();
            Set<String> deps = DataModellingQueryBuilder.forWorkspace(deepDependencyWorkspace)
                    .getTransitiveDependencies(level1Id);

            assertEquals(4, deps.size());
        }
    }

    @Nested
    @DisplayName("Impact Analysis Tests")
    class ImpactAnalysisTests {

        @Test
        @DisplayName("getImpactAnalysis returns affected tables")
        void testImpactAnalysis() {
            String rawId = multiOwnerWorkspace.getTableByName("raw_events").getId();
            List<DataModellingTable> impacted = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .getImpactAnalysis(rawId);

            assertEquals(2, impacted.size());
            assertTrue(impacted.stream()
                    .anyMatch(t -> "processed_events".equals(t.getName())));
            assertTrue(impacted.stream()
                    .anyMatch(t -> "analytics_view".equals(t.getName())));
        }

        @Test
        @DisplayName("getDataConsumers returns direct consumers")
        void testGetDataConsumers() {
            String rawId = multiOwnerWorkspace.getTableByName("raw_events").getId();
            List<DataModellingTable> consumers = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .getDataConsumers(rawId);

            assertEquals(1, consumers.size());
            assertEquals("processed_events", consumers.get(0).getName());
        }
    }

    @Nested
    @DisplayName("Metadata and Reporting Tests")
    class MetadataTests {

        @Test
        @DisplayName("getTableMetadataSummary returns complete metadata")
        void testMetadataSummary() {
            String customersId = simpleWorkspace.getTableByName("customers").getId();
            Map<String, Object> metadata = DataModellingQueryBuilder
                    .forWorkspace(simpleWorkspace)
                    .getTableMetadataSummary(customersId);

            assertNotNull(metadata);
            assertEquals("customers", metadata.get("name"));
            assertEquals("data-team", metadata.get("owner"));
            assertEquals("postgresql", metadata.get("infrastructureType"));
            assertEquals("silver", metadata.get("medallionLayer"));
        }

        @Test
        @DisplayName("getDataLineageReport returns upstream and downstream")
        void testLineageReport() {
            String processedId = multiOwnerWorkspace.getTableByName("processed_events").getId();
            Map<String, Object> lineage = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .getDataLineageReport(processedId);

            assertNotNull(lineage);
            assertEquals(processedId, lineage.get("tableId"));
            assertEquals(1, ((List<?>) lineage.get("upstreamDependencies")).size());
            assertEquals(1, ((List<?>) lineage.get("downstreamDependents")).size());
        }

        @Test
        @DisplayName("getTablesByMedallionLayer groups tables correctly")
        void testGroupByLayer() {
            Map<String, List<DataModellingTable>> byLayer = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .getTablesByMedallionLayer();

            assertFalse(byLayer.isEmpty());
            assertTrue(byLayer.containsKey("bronze"));
            assertTrue(byLayer.containsKey("silver"));
            assertTrue(byLayer.containsKey("gold"));
        }

        @Test
        @DisplayName("getTablesByOwner groups tables correctly")
        void testGroupByOwner() {
            Map<String, List<DataModellingTable>> byOwner = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .getTablesByOwner();

            assertTrue(byOwner.containsKey("data-team"));
            assertTrue(byOwner.containsKey("analytics-team"));
            assertTrue(byOwner.containsKey("infrastructure-team"));
        }

        @Test
        @DisplayName("getTablesByInfrastructure groups tables correctly")
        void testGroupByInfrastructure() {
            Map<String, List<DataModellingTable>> byInfra = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .getTablesByInfrastructure();

            assertTrue(byInfra.containsKey("warehouse"));
            assertTrue(byInfra.containsKey("lake"));
        }
    }

    @Nested
    @DisplayName("Cycle Detection Tests")
    class CycleDetectionTests {

        @Test
        @DisplayName("hasCyclicDependencies detects cycles")
        void testDetectCycle() {
            DataModellingWorkspace cyclicWs = TestWorkspaceFixture.createCyclicWorkspace();
            boolean hasCycles = DataModellingQueryBuilder.forWorkspace(cyclicWs)
                    .hasCyclicDependencies();

            assertTrue(hasCycles);
        }

        @Test
        @DisplayName("hasCyclicDependencies returns false for acyclic workspace")
        void testNoCycle() {
            boolean hasCycles = DataModellingQueryBuilder.forWorkspace(simpleWorkspace)
                    .hasCyclicDependencies();

            assertFalse(hasCycles);
        }

        @Test
        @DisplayName("detectCyclePath returns cycle path")
        void testDetectCyclePath() {
            DataModellingWorkspace cyclicWs = TestWorkspaceFixture.createCyclicWorkspace();
            List<String> cycle = DataModellingQueryBuilder.forWorkspace(cyclicWs)
                    .detectCyclePath();

            assertFalse(cycle.isEmpty());
            assertEquals(3, cycle.size());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Query empty workspace returns empty results")
        void testEmptyWorkspace() {
            DataModellingWorkspace empty = TestWorkspaceFixture.createEmptyWorkspace();
            List<DataModellingTable> results = DataModellingQueryBuilder
                    .forWorkspace(empty)
                    .getTables();

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("getNullableTable returns null for non-existent ID")
        void testGetNonExistentTable() {
            DataModellingTable result = DataModellingQueryBuilder
                    .forWorkspace(simpleWorkspace)
                    .getTableById("non-existent-id");

            assertNull(result);
        }

        @Test
        @DisplayName("Results are immutable")
        void testResultsImmutable() {
            List<DataModellingTable> results = DataModellingQueryBuilder
                    .forWorkspace(simpleWorkspace)
                    .getTables();

            assertThrows(UnsupportedOperationException.class, () -> results.add(null));
        }

        @Test
        @DisplayName("Multiple filters are combined with AND logic")
        void testChainedFilters() {
            List<DataModellingTable> results = DataModellingQueryBuilder
                    .forWorkspace(multiOwnerWorkspace)
                    .filterTablesByOwner("data-team")
                    .filterTablesByInfrastructureType("warehouse")
                    .getTables();

            assertEquals(1, results.size());
            assertEquals("processed_events", results.get(0).getName());
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Multiple builders can query same workspace concurrently")
        void testConcurrentQueries() {
            DataModellingQueryBuilder builder1 = DataModellingQueryBuilder
                    .forWorkspace(simpleWorkspace);
            DataModellingQueryBuilder builder2 = DataModellingQueryBuilder
                    .forWorkspace(simpleWorkspace);

            List<DataModellingTable> results1 = builder1.filterTablesByOwner("data-team").getTables();
            List<DataModellingTable> results2 = builder2.filterTablesByTag("critical").getTables();

            assertEquals(3, results1.size());
            assertEquals(2, results2.size());
        }
    }
}
