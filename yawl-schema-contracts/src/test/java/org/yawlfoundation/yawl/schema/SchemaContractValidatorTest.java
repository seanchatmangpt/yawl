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

package org.yawlfoundation.yawl.schema;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.datamodelling.model.OdcsTable;
import org.yawlfoundation.yawl.datamodelling.model.WorkspaceModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SchemaContractValidator}.
 *
 * @since 6.0.0
 */
class SchemaContractValidatorTest {

    private static WorkspaceModel contractWith(String... columns) {
        OdcsTable table = new OdcsTable("orders", "Order table", List.of(columns));
        return new WorkspaceModel("test", "Test workspace", List.of(table));
    }

    private static WorkspaceModel contractWithMultipleTables() {
        OdcsTable table1 = new OdcsTable("orders", "Orders", List.of("order_id", "customer_id"));
        OdcsTable table2 = new OdcsTable("items", "Items", List.of("item_id", "quantity"));
        return new WorkspaceModel("multi", "Multi-table", List.of(table1, table2));
    }

    @Test
    void validate_allRequiredFieldsPresent_returnsNoViolations() {
        WorkspaceModel contract = contractWith("order_id", "customer_id", "status");
        Map<String, String> actual = Map.of(
                "order_id", "12345",
                "customer_id", "CUST-001",
                "status", "CONFIRMED"
        );

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertTrue(violations.isEmpty(), "All required fields present should produce no violations");
    }

    @Test
    void validate_oneMissingField_returnsOneViolation() {
        WorkspaceModel contract = contractWith("order_id", "customer_id", "total_amount");
        Map<String, String> actual = Map.of(
                "order_id", "12345",
                "customer_id", "CUST-001"
                // total_amount is missing
        );

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertEquals(1, violations.size());
        assertEquals("total_amount", violations.get(0).field());
        assertEquals(ViolationType.MISSING_FIELD, violations.get(0).type());
    }

    @Test
    void validate_multipleMissingFields_reportsAllMissing() {
        WorkspaceModel contract = contractWith("order_id", "customer_id", "status", "total_amount", "currency");
        Map<String, String> actual = Map.of("order_id", "12345");

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertEquals(4, violations.size(), "Should report all 4 missing fields");
        assertTrue(violations.stream().allMatch(v -> v.type() == ViolationType.MISSING_FIELD));

        var missingFields = violations.stream().map(SchemaViolation::field).toList();
        assertTrue(missingFields.contains("customer_id"));
        assertTrue(missingFields.contains("status"));
        assertTrue(missingFields.contains("total_amount"));
        assertTrue(missingFields.contains("currency"));
    }

    @Test
    void validate_allFieldsMissing_reportsAll() {
        WorkspaceModel contract = contractWith("field_1", "field_2", "field_3");
        Map<String, String> actual = Map.of();

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertEquals(3, violations.size());
        violations.forEach(v -> assertEquals(ViolationType.MISSING_FIELD, v.type()));
    }

    @Test
    void validate_extraFieldInActual_noViolationLenientMode() {
        WorkspaceModel contract = contractWith("order_id");
        Map<String, String> actual = Map.of(
                "order_id", "12345",
                "extra_field", "extra_value"  // not in contract
        );

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertTrue(violations.isEmpty(), "Extra fields allowed in lenient mode");
    }

    @Test
    void validate_multipleExtraFields_allIgnoredLenient() {
        WorkspaceModel contract = contractWith("id");
        Map<String, String> actual = Map.of(
                "id", "123",
                "extra_1", "value_1",
                "extra_2", "value_2",
                "extra_3", "value_3"
        );

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertTrue(violations.isEmpty(), "All extra fields should be ignored in lenient mode");
    }

    @Test
    void validate_emptyContract_noViolations() {
        WorkspaceModel emptyContract = new WorkspaceModel("empty", "No constraints", List.of());
        Map<String, String> actual = Map.of("any_field", "any_value");

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, emptyContract);

        assertTrue(violations.isEmpty(), "Empty contract imposes no constraints");
    }

    @Test
    void validate_emptyActualData_missingRequiredField() {
        WorkspaceModel contract = contractWith("required_field");
        Map<String, String> actual = Map.of();

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertEquals(1, violations.size());
        assertEquals("required_field", violations.get(0).field());
    }

    @Test
    void validate_multipleTables_checksAllColumns() {
        WorkspaceModel contract = contractWithMultipleTables();
        Map<String, String> actual = Map.of(
                "order_id", "ORD-001",
                "customer_id", "CUST-002",
                "item_id", "ITEM-003",
                "quantity", "5"
        );

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertTrue(violations.isEmpty(), "All columns from all tables present");
    }

    @Test
    void validate_multipleTables_missingFromSecondTable() {
        WorkspaceModel contract = contractWithMultipleTables();
        Map<String, String> actual = Map.of(
                "order_id", "ORD-001",
                "customer_id", "CUST-002"
                // Missing item_id and quantity
        );

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertEquals(2, violations.size());
        var missingFields = violations.stream().map(SchemaViolation::field).toList();
        assertTrue(missingFields.contains("item_id"));
        assertTrue(missingFields.contains("quantity"));
    }

    @Test
    void validate_contractWithNoColumns_noViolations() {
        OdcsTable emptyTable = new OdcsTable("empty_table", "No columns", List.of());
        WorkspaceModel contract = new WorkspaceModel("test", "Test", List.of(emptyTable));
        Map<String, String> actual = Map.of("any", "value");

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertTrue(violations.isEmpty(), "Table with no columns imposes no constraints");
    }

    @Test
    void validate_caseInsensitiveFieldNames_matchExact() {
        WorkspaceModel contract = contractWith("OrderId");  // exact case
        Map<String, String> actual = Map.of("orderid", "123");  // different case

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        // Field names are case-sensitive — different case is a violation
        assertEquals(1, violations.size());
        assertEquals("OrderId", violations.get(0).field());
    }

    @Test
    void validate_fieldPresenceOnly_valueIsIrrelevant() {
        WorkspaceModel contract = contractWith("status");
        Map<String, String> actual = Map.of("status", "");  // empty string value

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertTrue(violations.isEmpty(), "Empty value is still present");
    }

    @Test
    void validate_largeDataSet_handlesScaling() {
        String[] manyFields = new String[100];
        for (int i = 0; i < 100; i++) {
            manyFields[i] = "field_" + i;
        }
        WorkspaceModel contract = contractWith(manyFields);

        Map<String, String> actual = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            actual.put("field_" + i, "value_" + i);
        }

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertTrue(violations.isEmpty(), "All 100 fields present");
    }

    @Test
    void validate_partialLargeDataSet_reportsMissingFields() {
        String[] manyFields = new String[100];
        for (int i = 0; i < 100; i++) {
            manyFields[i] = "field_" + i;
        }
        WorkspaceModel contract = contractWith(manyFields);

        Map<String, String> actual = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            actual.put("field_" + i, "value_" + i);
        }

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertEquals(50, violations.size(), "Missing 50 fields");
    }

    @Test
    void validate_nullActualMap_throwsNullPointerException() {
        WorkspaceModel contract = contractWith("field");

        assertThrows(NullPointerException.class,
                () -> SchemaContractValidator.validate(null, contract),
                "Null actual map should throw");
    }

    @Test
    void validate_nullContract_throwsNullPointerException() {
        Map<String, String> actual = Map.of("field", "value");

        assertThrows(NullPointerException.class,
                () -> SchemaContractValidator.validate(actual, null),
                "Null contract should throw");
    }

    @Test
    void validate_resultIsNewList_notConnected() {
        WorkspaceModel contract = contractWith("field");
        Map<String, String> actual = Map.of();

        List<SchemaViolation> violations1 = SchemaContractValidator.validate(actual, contract);
        List<SchemaViolation> violations2 = SchemaContractValidator.validate(actual, contract);

        assertEquals(violations1, violations2);
        assertNotSame(violations1, violations2, "Each call should return a new list");
    }

    @Test
    void validate_specialCharactersInFieldNames() {
        WorkspaceModel contract = contractWith("first-name", "last_name", "amount@currency");
        Map<String, String> actual = Map.of(
                "first-name", "John",
                "last_name", "Doe",
                "amount@currency", "USD"
        );

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertTrue(violations.isEmpty());
    }

    @Test
    void validate_numericFieldNames() {
        WorkspaceModel contract = contractWith("field_1", "field_2", "field_3");
        Map<String, String> actual = Map.of(
                "field_1", "value1",
                "field_2", "value2",
                "field_3", "value3"
        );

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertTrue(violations.isEmpty());
    }

    @Test
    void validate_preservesViolationOrder_matchesContractOrder() {
        WorkspaceModel contract = contractWith("z_field", "a_field", "m_field");
        Map<String, String> actual = Map.of();

        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);

        assertEquals(3, violations.size());
        // Order should match contract definition order, not alphabetical
        assertEquals("z_field", violations.get(0).field());
        assertEquals("a_field", violations.get(1).field());
        assertEquals("m_field", violations.get(2).field());
    }
}
