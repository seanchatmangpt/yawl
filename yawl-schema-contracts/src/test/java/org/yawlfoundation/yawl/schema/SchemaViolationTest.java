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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SchemaViolation} record.
 *
 * @since 6.0.0
 */
class SchemaViolationTest {

    @Test
    void missingField_factoryMethod_setsAllFields() {
        var violation = SchemaViolation.missingField("order_id");

        assertEquals("order_id", violation.field());
        assertEquals("present", violation.expected());
        assertEquals("missing", violation.actual());
        assertEquals(ViolationType.MISSING_FIELD, violation.type());
    }

    @Test
    void missingField_factoryMethod_multipleFields_correctValues() {
        var v1 = SchemaViolation.missingField("customer_id");
        var v2 = SchemaViolation.missingField("total_amount");

        assertEquals("customer_id", v1.field());
        assertEquals("total_amount", v2.field());
        assertEquals(ViolationType.MISSING_FIELD, v1.type());
        assertEquals(ViolationType.MISSING_FIELD, v2.type());
    }

    @Test
    void unexpectedField_factoryMethod_setsAllFields() {
        var violation = SchemaViolation.unexpectedField("extra_field");

        assertEquals("extra_field", violation.field());
        assertEquals("absent", violation.expected());
        assertEquals("present", violation.actual());
        assertEquals(ViolationType.UNEXPECTED_FIELD, violation.type());
    }

    @Test
    void unexpectedField_factoryMethod_multipleFields() {
        var v1 = SchemaViolation.unexpectedField("unexpected_1");
        var v2 = SchemaViolation.unexpectedField("unexpected_2");

        assertEquals("unexpected_1", v1.field());
        assertEquals("unexpected_2", v2.field());
        assertEquals(ViolationType.UNEXPECTED_FIELD, v1.type());
        assertEquals(ViolationType.UNEXPECTED_FIELD, v2.type());
    }

    @Test
    void describe_missingField_returnsHumanReadableString() {
        var violation = SchemaViolation.missingField("total_amount");

        String description = violation.describe();

        assertTrue(description.contains("MISSING_FIELD"), "Should contain violation type");
        assertTrue(description.contains("total_amount"), "Should contain field name");
        assertTrue(description.contains("present"), "Should contain expected");
        assertTrue(description.contains("missing"), "Should contain actual");
    }

    @Test
    void describe_unexpectedField_returnsHumanReadableString() {
        var violation = SchemaViolation.unexpectedField("legacy_field");

        String description = violation.describe();

        assertTrue(description.contains("UNEXPECTED_FIELD"), "Should contain violation type");
        assertTrue(description.contains("legacy_field"), "Should contain field name");
        assertTrue(description.contains("absent"), "Should contain expected");
        assertTrue(description.contains("present"), "Should contain actual");
    }

    @Test
    void describe_format_matches_expectedPattern() {
        var violation = SchemaViolation.missingField("order_id");
        String description = violation.describe();

        // Expected format: "TYPE: 'field' (expected: X, actual: Y)"
        assertTrue(description.contains("'order_id'"), "Field name should be quoted");
        assertTrue(description.contains("(expected:"), "Should have expected clause");
        assertTrue(description.contains("actual:"), "Should have actual clause");
    }

    @Test
    void record_equality_sameValues() {
        var v1 = new SchemaViolation("field", "present", "missing", ViolationType.MISSING_FIELD);
        var v2 = new SchemaViolation("field", "present", "missing", ViolationType.MISSING_FIELD);

        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void record_inequality_differentFieldNames() {
        var v1 = SchemaViolation.missingField("order_id");
        var v2 = SchemaViolation.missingField("customer_id");

        assertNotEquals(v1, v2);
    }

    @Test
    void record_inequality_differentViolationTypes() {
        var v1 = SchemaViolation.missingField("field");
        var v2 = SchemaViolation.unexpectedField("field");

        assertNotEquals(v1, v2);
    }

    @Test
    void record_toString_contains_allComponents() {
        var violation = SchemaViolation.missingField("amount");
        String str = violation.toString();

        // Record toString includes all fields
        assertTrue(str.contains("amount"));
        assertTrue(str.contains("MISSING_FIELD"));
    }

    @Test
    void record_immutability_componentsFinal() {
        var violation = SchemaViolation.missingField("id");

        // Records are immutable — cannot modify fields
        assertEquals("id", violation.field());
        assertEquals(ViolationType.MISSING_FIELD, violation.type());
        // Attempting to modify would fail at compile time in actual code
    }

    @Test
    void customConstructor_explicitValues() {
        var violation = new SchemaViolation("custom_field", "expected_val", "actual_val",
                ViolationType.UNEXPECTED_FIELD);

        assertEquals("custom_field", violation.field());
        assertEquals("expected_val", violation.expected());
        assertEquals("actual_val", violation.actual());
        assertEquals(ViolationType.UNEXPECTED_FIELD, violation.type());
    }
}
