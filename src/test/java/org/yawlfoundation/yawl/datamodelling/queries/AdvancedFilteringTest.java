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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AdvancedFiltering predicates.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("AdvancedFiltering Tests")
class AdvancedFilteringTest {

    private DataModellingWorkspace workspace;
    private List<DataModellingTable> tables;

    @BeforeEach
    void setUp() {
        workspace = TestWorkspaceFixture.createMultiOwnerWorkspace();
        tables = workspace.getTables();
    }

    @Nested
    @DisplayName("Owner Filtering Tests")
    class OwnerFilteringTests {

        @Test
        @DisplayName("byOwner filters by exact owner")
        void testByOwner() {
            Predicate<DataModellingTable> filter = AdvancedFiltering.byOwner("data-team");
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(1, results.size());
            assertTrue(results.stream().allMatch(t -> "data-team".equals(t.getOwner())));
        }

        @Test
        @DisplayName("byOwners filters by multiple owners")
        void testByOwners() {
            Collection<String> owners = Arrays.asList("data-team", "analytics-team");
            Predicate<DataModellingTable> filter = AdvancedFiltering.byOwners(owners);
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("byOwner returns no results for non-existent owner")
        void testNonExistentOwner() {
            Predicate<DataModellingTable> filter = AdvancedFiltering.byOwner("non-existent");
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertTrue(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("Infrastructure Type Filtering Tests")
    class InfrastructureFilteringTests {

        @Test
        @DisplayName("byInfrastructureType filters by type")
        void testByInfrastructureType() {
            Predicate<DataModellingTable> filter =
                    AdvancedFiltering.byInfrastructureType("warehouse");
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertTrue(results.size() >= 1);
            assertTrue(results.stream()
                    .allMatch(t -> "warehouse".equals(t.getInfrastructureType())));
        }

        @Test
        @DisplayName("byInfrastructureTypes filters by multiple types")
        void testByInfrastructureTypes() {
            Collection<String> types = Arrays.asList("warehouse", "lake");
            Predicate<DataModellingTable> filter =
                    AdvancedFiltering.byInfrastructureTypes(types);
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(3, results.size());
        }
    }

    @Nested
    @DisplayName("Medallion Layer Filtering Tests")
    class MedallionLayerFilteringTests {

        @Test
        @DisplayName("byMedallionLayer filters by layer")
        void testByMedallionLayer() {
            Predicate<DataModellingTable> filter =
                    AdvancedFiltering.byMedallionLayer("silver");
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(1, results.size());
            assertTrue(results.stream()
                    .allMatch(t -> "silver".equals(t.getMedallionLayer())));
        }

        @Test
        @DisplayName("byMedallionLayers filters by multiple layers")
        void testByMedallionLayers() {
            Collection<String> layers = Arrays.asList("bronze", "gold");
            Predicate<DataModellingTable> filter =
                    AdvancedFiltering.byMedallionLayers(layers);
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(2, results.size());
        }
    }

    @Nested
    @DisplayName("Tag Filtering Tests")
    class TagFilteringTests {

        @BeforeEach
        void setUpTaggedWorkspace() {
            workspace = TestWorkspaceFixture.createTaggedWorkspace();
            tables = workspace.getTables();
        }

        @Test
        @DisplayName("byTag filters by single tag")
        void testByTag() {
            Predicate<DataModellingTable> filter = AdvancedFiltering.byTag("sensitive");
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("byTags filters by multiple tags (OR logic)")
        void testByTags() {
            Collection<String> tags = Arrays.asList("sensitive", "public");
            Predicate<DataModellingTable> filter = AdvancedFiltering.byTags(tags);
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(4, results.size());
        }

        @Test
        @DisplayName("byAllTags filters by all required tags (AND logic)")
        void testByAllTags() {
            Collection<String> tags = Arrays.asList("sensitive", "pii");
            Predicate<DataModellingTable> filter = AdvancedFiltering.byAllTags(tags);
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(1, results.size());
            assertEquals("customer_sensitive", results.get(0).getName());
        }

        @Test
        @DisplayName("byTagNot filters out tables with tag")
        void testByTagNot() {
            Predicate<DataModellingTable> filter = AdvancedFiltering.byTagNot("public");
            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(2, results.size());
            assertTrue(results.stream()
                    .noneMatch(t -> t.getTags() != null && t.getTags().contains("public")));
        }
    }

    @Nested
    @DisplayName("Boolean Combination Tests")
    class BooleanCombinationTests {

        @Test
        @DisplayName("and() combines predicates with AND logic")
        void testAnd() {
            Predicate<DataModellingTable> ownerFilter =
                    AdvancedFiltering.byOwner("data-team");
            Predicate<DataModellingTable> infraFilter =
                    AdvancedFiltering.byInfrastructureType("warehouse");
            Predicate<DataModellingTable> combined =
                    AdvancedFiltering.and(ownerFilter, infraFilter);

            List<DataModellingTable> results = tables.stream()
                    .filter(combined)
                    .toList();

            assertEquals(1, results.size());
            assertEquals("processed_events", results.get(0).getName());
        }

        @Test
        @DisplayName("andAll() combines multiple predicates")
        void testAndAll() {
            Predicate<DataModellingTable> filter = AdvancedFiltering.andAll(
                    AdvancedFiltering.byOwner("data-team"),
                    AdvancedFiltering.byInfrastructureType("warehouse")
            );

            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(1, results.size());
        }

        @Test
        @DisplayName("or() combines predicates with OR logic")
        void testOr() {
            Predicate<DataModellingTable> layer1 =
                    AdvancedFiltering.byMedallionLayer("bronze");
            Predicate<DataModellingTable> layer2 =
                    AdvancedFiltering.byMedallionLayer("gold");
            Predicate<DataModellingTable> combined =
                    AdvancedFiltering.or(layer1, layer2);

            List<DataModellingTable> results = tables.stream()
                    .filter(combined)
                    .toList();

            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("orAny() combines multiple predicates")
        void testOrAny() {
            Predicate<DataModellingTable> filter = AdvancedFiltering.orAny(
                    AdvancedFiltering.byOwner("analytics-team"),
                    AdvancedFiltering.byOwner("infrastructure-team")
            );

            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("not() negates a predicate")
        void testNot() {
            Predicate<DataModellingTable> filter =
                    AdvancedFiltering.not(AdvancedFiltering.byOwner("data-team"));

            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("Complex combination: (owner=data OR owner=analytics) AND infra=warehouse")
        void testComplexCombination() {
            Predicate<DataModellingTable> ownerFilter = AdvancedFiltering.orAny(
                    AdvancedFiltering.byOwner("data-team"),
                    AdvancedFiltering.byOwner("analytics-team")
            );
            Predicate<DataModellingTable> infraFilter =
                    AdvancedFiltering.byInfrastructureType("warehouse");
            Predicate<DataModellingTable> combined =
                    AdvancedFiltering.and(ownerFilter, infraFilter);

            List<DataModellingTable> results = tables.stream()
                    .filter(combined)
                    .toList();

            assertEquals(2, results.size());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Filtering with null values returns false")
        void testNullHandling() {
            DataModellingTable tableWithoutOwner = DataModellingTable.builder()
                    .name("unowned-table")
                    .build();

            Predicate<DataModellingTable> filter = AdvancedFiltering.byOwner("some-owner");
            assertFalse(filter.test(tableWithoutOwner));
        }

        @Test
        @DisplayName("Empty collection filtering works correctly")
        void testEmptyCollection() {
            Collection<String> emptyOwners = Arrays.asList();
            Predicate<DataModellingTable> filter = AdvancedFiltering.byOwners(emptyOwners);

            List<DataModellingTable> results = tables.stream()
                    .filter(filter)
                    .toList();

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Filters are stateless and reusable")
        void testPredicateReusability() {
            Predicate<DataModellingTable> filter = AdvancedFiltering.byOwner("data-team");

            List<DataModellingTable> results1 = tables.stream().filter(filter).toList();
            List<DataModellingTable> results2 = tables.stream().filter(filter).toList();

            assertEquals(results1.size(), results2.size());
            assertEquals(results1, results2);
        }
    }
}
