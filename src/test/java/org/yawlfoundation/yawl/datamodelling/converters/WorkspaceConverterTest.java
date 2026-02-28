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

package org.yawlfoundation.yawl.datamodelling.converters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingColumn;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingTable;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WorkspaceConverter.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("WorkspaceConverter Tests")
class WorkspaceConverterTest {

    private DataModellingWorkspace testWorkspace;

    @BeforeEach
    void setUp() {
        testWorkspace = DataModellingWorkspace.builder()
                .name("customer-analytics")
                .description("Customer analytics workspace")
                .version("3.1.0")
                .addTable(DataModellingTable.builder()
                        .name("customers")
                        .addColumn(DataModellingColumn.builder()
                                .name("customer_id")
                                .dataType("bigint")
                                .primaryKey(true)
                                .build())
                        .build())
                .build();
    }

    @Test
    @DisplayName("fromJson parses workspace JSON to typed object")
    void testFromJson() {
        String json = WorkspaceConverter.toJson(testWorkspace);
        DataModellingWorkspace parsed = WorkspaceConverter.fromJson(json);

        assertNotNull(parsed);
        assertEquals("customer-analytics", parsed.getName());
        assertEquals(1, parsed.getTables().size());
        assertEquals("customers", parsed.getTables().get(0).getName());
    }

    @Test
    @DisplayName("toJson serializes workspace to JSON string")
    void testToJson() {
        String json = WorkspaceConverter.toJson(testWorkspace);

        assertNotNull(json);
        assertTrue(json.contains("customer-analytics"));
        assertTrue(json.contains("\"name\":\"customer-analytics\""));
    }

    @Test
    @DisplayName("newBuilder creates a fresh builder")
    void testNewBuilder() {
        DataModellingWorkspace.Builder builder = WorkspaceConverter.newBuilder();
        assertNotNull(builder);

        DataModellingWorkspace ws = builder.name("test-workspace").build();
        assertNotNull(ws);
        assertEquals("test-workspace", ws.getName());
    }

    @Test
    @DisplayName("Round-trip preserves workspace structure")
    void testRoundTrip() {
        // Create workspace with multiple elements
        DataModellingWorkspace original = DataModellingWorkspace.builder()
                .name("complex-workspace")
                .description("Test workspace with multiple tables")
                .addTable(DataModellingTable.builder()
                        .name("users")
                        .owner("team-a")
                        .addColumn(DataModellingColumn.builder()
                                .name("user_id")
                                .dataType("uuid")
                                .primaryKey(true)
                                .nullable(false)
                                .build())
                        .addColumn(DataModellingColumn.builder()
                                .name("email")
                                .dataType("string")
                                .build())
                        .build())
                .addTable(DataModellingTable.builder()
                        .name("orders")
                        .owner("team-b")
                        .build())
                .build();

        // Serialize and deserialize
        String json = WorkspaceConverter.toJson(original);
        DataModellingWorkspace restored = WorkspaceConverter.fromJson(json);

        // Verify structure
        assertEquals(2, restored.getTables().size());

        DataModellingTable users = restored.getTableByName("users");
        assertNotNull(users);
        assertEquals("team-a", users.getOwner());
        assertEquals(2, users.getColumns().size());

        DataModellingColumn userId = users.getColumnByName("user_id");
        assertNotNull(userId);
        assertEquals("uuid", userId.getDataType());
        assertTrue(userId.getPrimaryKey());
    }
}
