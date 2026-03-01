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

import org.yawlfoundation.yawl.datamodelling.models.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Test fixture factory for creating sample data modelling workspaces.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class TestWorkspaceFixture {

    private TestWorkspaceFixture() {
        // Utility class
    }

    /**
     * Create a simple workspace with basic tables for testing.
     */
    public static DataModellingWorkspace createSimpleWorkspace() {
        DataModellingTable customersTable = DataModellingTable.builder()
                .name("customers")
                .businessName("Customer Master Data")
                .description("Customer information")
                .owner("data-team")
                .infrastructureType("postgresql")
                .medallionLayer("silver")
                .addColumn(DataModellingColumn.builder()
                        .name("customer_id")
                        .dataType("bigint")
                        .primaryKey(true)
                        .build())
                .addColumn(DataModellingColumn.builder()
                        .name("name")
                        .dataType("varchar")
                        .build())
                .addTag("critical")
                .build();

        DataModellingTable ordersTable = DataModellingTable.builder()
                .name("orders")
                .businessName("Customer Orders")
                .description("Customer order transactions")
                .owner("data-team")
                .infrastructureType("postgresql")
                .medallionLayer("silver")
                .addColumn(DataModellingColumn.builder()
                        .name("order_id")
                        .dataType("bigint")
                        .primaryKey(true)
                        .build())
                .addColumn(DataModellingColumn.builder()
                        .name("customer_id")
                        .dataType("bigint")
                        .build())
                .addTag("critical")
                .build();

        DataModellingTable orderItemsTable = DataModellingTable.builder()
                .name("order_items")
                .businessName("Order Items")
                .description("Line items in orders")
                .owner("data-team")
                .infrastructureType("postgresql")
                .medallionLayer("silver")
                .addColumn(DataModellingColumn.builder()
                        .name("item_id")
                        .dataType("bigint")
                        .primaryKey(true)
                        .build())
                .addColumn(DataModellingColumn.builder()
                        .name("order_id")
                        .dataType("bigint")
                        .build())
                .build();

        DataModellingRelationship customerOrdersRel = DataModellingRelationship.builder()
                .label("customer_orders")
                .sourceTableId(customersTable.getId())
                .targetTableId(ordersTable.getId())
                .sourceCardinality("exactlyOne")
                .targetCardinality("oneOrMany")
                .flowDirection("sourceToTarget")
                .relationshipType("foreignKey")
                .build();

        DataModellingRelationship orderItemsRel = DataModellingRelationship.builder()
                .label("order_line_items")
                .sourceTableId(ordersTable.getId())
                .targetTableId(orderItemsTable.getId())
                .sourceCardinality("exactlyOne")
                .targetCardinality("oneOrMany")
                .flowDirection("sourceToTarget")
                .relationshipType("dataFlow")
                .build();

        return DataModellingWorkspace.builder()
                .name("ecommerce-workspace")
                .description("E-commerce data model")
                .version("3.1.0")
                .addTable(customersTable)
                .addTable(ordersTable)
                .addTable(orderItemsTable)
                .addRelationship(customerOrdersRel)
                .addRelationship(orderItemsRel)
                .build();
    }

    /**
     * Create a workspace with multiple owners and infrastructure types.
     */
    public static DataModellingWorkspace createMultiOwnerWorkspace() {
        DataModellingTable analyticsTable = DataModellingTable.builder()
                .name("analytics_view")
                .businessName("Analytics View")
                .description("Aggregated analytics data")
                .owner("analytics-team")
                .infrastructureType("warehouse")
                .medallionLayer("gold")
                .addTag("public")
                .addColumn(DataModellingColumn.builder().name("metric_id").dataType("bigint").build())
                .build();

        DataModellingTable rawDataTable = DataModellingTable.builder()
                .name("raw_events")
                .businessName("Raw Events")
                .description("Raw event stream")
                .owner("infrastructure-team")
                .infrastructureType("lake")
                .medallionLayer("bronze")
                .addColumn(DataModellingColumn.builder().name("event_id").dataType("bigint").build())
                .build();

        DataModellingTable processedTable = DataModellingTable.builder()
                .name("processed_events")
                .businessName("Processed Events")
                .description("Cleaned event data")
                .owner("data-team")
                .infrastructureType("warehouse")
                .medallionLayer("silver")
                .addTag("pii")
                .addColumn(DataModellingColumn.builder().name("event_id").dataType("bigint").build())
                .build();

        DataModellingRelationship rel1 = DataModellingRelationship.builder()
                .label("raw_to_processed")
                .sourceTableId(rawDataTable.getId())
                .targetTableId(processedTable.getId())
                .flowDirection("sourceToTarget")
                .relationshipType("dataFlow")
                .build();

        DataModellingRelationship rel2 = DataModellingRelationship.builder()
                .label("processed_to_analytics")
                .sourceTableId(processedTable.getId())
                .targetTableId(analyticsTable.getId())
                .flowDirection("sourceToTarget")
                .relationshipType("dataFlow")
                .build();

        return DataModellingWorkspace.builder()
                .name("multi-layer-workspace")
                .description("Multi-owner, multi-infrastructure workspace")
                .addTable(analyticsTable)
                .addTable(rawDataTable)
                .addTable(processedTable)
                .addRelationship(rel1)
                .addRelationship(rel2)
                .build();
    }

    /**
     * Create a workspace with cyclic dependencies for testing cycle detection.
     */
    public static DataModellingWorkspace createCyclicWorkspace() {
        DataModellingTable tableA = DataModellingTable.builder()
                .name("table_a")
                .owner("team-a")
                .build();

        DataModellingTable tableB = DataModellingTable.builder()
                .name("table_b")
                .owner("team-b")
                .build();

        DataModellingTable tableC = DataModellingTable.builder()
                .name("table_c")
                .owner("team-c")
                .build();

        // Create cycle: A -> B -> C -> A
        DataModellingRelationship relAB = DataModellingRelationship.builder()
                .label("a_to_b")
                .sourceTableId(tableA.getId())
                .targetTableId(tableB.getId())
                .relationshipType("dataFlow")
                .build();

        DataModellingRelationship relBC = DataModellingRelationship.builder()
                .label("b_to_c")
                .sourceTableId(tableB.getId())
                .targetTableId(tableC.getId())
                .relationshipType("dataFlow")
                .build();

        DataModellingRelationship relCA = DataModellingRelationship.builder()
                .label("c_to_a")
                .sourceTableId(tableC.getId())
                .targetTableId(tableA.getId())
                .relationshipType("dataFlow")
                .build();

        return DataModellingWorkspace.builder()
                .name("cyclic-workspace")
                .description("Workspace with circular dependencies")
                .addTable(tableA)
                .addTable(tableB)
                .addTable(tableC)
                .addRelationship(relAB)
                .addRelationship(relBC)
                .addRelationship(relCA)
                .build();
    }

    /**
     * Create a workspace with deep dependency chains.
     */
    public static DataModellingWorkspace createDeepDependencyWorkspace() {
        List<DataModellingTable> tables = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            tables.add(DataModellingTable.builder()
                    .name("level_" + i)
                    .owner("data-team")
                    .infrastructureType("postgresql")
                    .medallionLayer(i <= 2 ? "bronze" : i <= 4 ? "silver" : "gold")
                    .build());
        }

        List<DataModellingRelationship> relationships = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            relationships.add(DataModellingRelationship.builder()
                    .label("level_" + (i + 1) + "_to_" + (i + 2))
                    .sourceTableId(tables.get(i).getId())
                    .targetTableId(tables.get(i + 1).getId())
                    .relationshipType("dataFlow")
                    .build());
        }

        DataModellingWorkspace.Builder wsBuilder = DataModellingWorkspace.builder()
                .name("deep-dependency-workspace")
                .description("Workspace with deep dependency chains");

        for (DataModellingTable table : tables) {
            wsBuilder.addTable(table);
        }
        for (DataModellingRelationship rel : relationships) {
            wsBuilder.addRelationship(rel);
        }

        return wsBuilder.build();
    }

    /**
     * Create a workspace with tags for tag-based filtering tests.
     */
    public static DataModellingWorkspace createTaggedWorkspace() {
        DataModellingTable table1 = DataModellingTable.builder()
                .name("customer_sensitive")
                .owner("data-team")
                .addTag("sensitive")
                .addTag("pii")
                .addTag("regulated")
                .build();

        DataModellingTable table2 = DataModellingTable.builder()
                .name("product_public")
                .owner("marketing-team")
                .addTag("public")
                .addTag("reference")
                .build();

        DataModellingTable table3 = DataModellingTable.builder()
                .name("transaction_sensitive")
                .owner("finance-team")
                .addTag("sensitive")
                .addTag("financial")
                .build();

        DataModellingTable table4 = DataModellingTable.builder()
                .name("analytics_aggregated")
                .owner("analytics-team")
                .addTag("aggregated")
                .addTag("public")
                .build();

        return DataModellingWorkspace.builder()
                .name("tagged-workspace")
                .description("Workspace with comprehensive tagging")
                .addTable(table1)
                .addTable(table2)
                .addTable(table3)
                .addTable(table4)
                .build();
    }

    /**
     * Create an empty workspace for testing edge cases.
     */
    public static DataModellingWorkspace createEmptyWorkspace() {
        return DataModellingWorkspace.builder()
                .name("empty-workspace")
                .description("Empty workspace for edge case testing")
                .build();
    }
}
