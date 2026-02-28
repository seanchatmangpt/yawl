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
import org.yawlfoundation.yawl.datamodelling.DataModellingException;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingColumn;
import org.yawlfoundation.yawl.datamodelling.models.DataModellingTable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonObjectMapper singleton.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("JsonObjectMapper Tests")
class JsonObjectMapperTest {

    private DataModellingTable testTable;

    @BeforeEach
    void setUp() {
        testTable = DataModellingTable.builder()
                .id("table-1")
                .name("customers")
                .description("Customer master data")
                .owner("data-team")
                .build();
    }

    @Test
    @DisplayName("getInstance returns non-null ObjectMapper")
    void testGetInstance() {
        assertNotNull(JsonObjectMapper.getInstance());
    }

    @Test
    @DisplayName("toJson serializes table to JSON string")
    void testToJson() {
        String json = JsonObjectMapper.toJson(testTable);
        assertNotNull(json);
        assertTrue(json.contains("customers"));
        assertTrue(json.contains("\"name\":\"customers\""));
    }

    @Test
    @DisplayName("parseJson deserializes JSON to table object")
    void testParseJson() {
        String json = JsonObjectMapper.toJson(testTable);
        DataModellingTable parsed = JsonObjectMapper.parseJson(json, DataModellingTable.class);

        assertNotNull(parsed);
        assertEquals("table-1", parsed.getId());
        assertEquals("customers", parsed.getName());
        assertEquals("data-team", parsed.getOwner());
    }

    @Test
    @DisplayName("toJson throws on null object")
    void testToJsonThrowsOnNull() {
        assertThrows(DataModellingException.class, () -> {
            JsonObjectMapper.toJson(null);
        });
    }

    @Test
    @DisplayName("parseJson throws on null JSON")
    void testParseJsonThrowsOnNull() {
        assertThrows(DataModellingException.class, () -> {
            JsonObjectMapper.parseJson(null, DataModellingTable.class);
        });
    }

    @Test
    @DisplayName("parseJson throws on empty JSON")
    void testParseJsonThrowsOnEmpty() {
        assertThrows(DataModellingException.class, () -> {
            JsonObjectMapper.parseJson("", DataModellingTable.class);
        });
    }

    @Test
    @DisplayName("parseJson throws on malformed JSON")
    void testParseJsonThrowsOnMalformed() {
        assertThrows(DataModellingException.class, () -> {
            JsonObjectMapper.parseJson("{invalid json", DataModellingTable.class);
        });
    }

    @Test
    @DisplayName("Round-trip conversion preserves data")
    void testRoundTrip() {
        // Create table with multiple attributes
        DataModellingTable table = DataModellingTable.builder()
                .id("t-1")
                .name("orders")
                .businessName("Sales Orders")
                .description("All customer orders")
                .owner("sales-team")
                .infrastructureType("postgresql")
                .addColumn(DataModellingColumn.builder()
                        .id("c-1")
                        .name("order_id")
                        .dataType("bigint")
                        .primaryKey(true)
                        .build())
                .build();

        // Serialize to JSON
        String json = JsonObjectMapper.toJson(table);

        // Deserialize back to object
        DataModellingTable restored = JsonObjectMapper.parseJson(json, DataModellingTable.class);

        // Verify all data preserved
        assertEquals("t-1", restored.getId());
        assertEquals("orders", restored.getName());
        assertEquals("Sales Orders", restored.getBusinessName());
        assertEquals("postgresql", restored.getInfrastructureType());
        assertNotNull(restored.getColumns());
        assertEquals(1, restored.getColumns().size());

        DataModellingColumn col = restored.getColumns().get(0);
        assertEquals("order_id", col.getName());
        assertEquals("bigint", col.getDataType());
        assertTrue(col.getPrimaryKey());
    }

    @Test
    @DisplayName("JSON objects with null fields are handled correctly")
    void testJsonWithNullFields() {
        // Create minimal table
        DataModellingTable table = DataModellingTable.builder()
                .name("minimal_table")
                .build();

        String json = JsonObjectMapper.toJson(table);
        DataModellingTable parsed = JsonObjectMapper.parseJson(json, DataModellingTable.class);

        assertNotNull(parsed);
        assertEquals("minimal_table", parsed.getName());
        assertNull(parsed.getDescription());
        assertNull(parsed.getOwner());
    }
}
