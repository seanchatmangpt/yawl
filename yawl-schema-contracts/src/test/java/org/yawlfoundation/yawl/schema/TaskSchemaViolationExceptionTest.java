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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TaskSchemaViolationException}.
 *
 * @since 6.0.0
 */
class TaskSchemaViolationExceptionTest {

    private static TaskSchemaViolationException inputViolation(SchemaViolation... violations) {
        return new TaskSchemaViolationException(
                "ProcessOrder", "C-42", "contracts/orders-v2.yaml",
                true, List.of(violations)
        );
    }

    private static TaskSchemaViolationException outputViolation(SchemaViolation... violations) {
        return new TaskSchemaViolationException(
                "GenerateInvoice", "C-99", "contracts/invoice-v1.yaml",
                false, List.of(violations)
        );
    }

    @Test
    void constructor_inputViolation_setsAllFields() {
        var violation = SchemaViolation.missingField("order_id");
        var ex = inputViolation(violation);

        assertEquals("ProcessOrder", ex.getTaskId());
        assertEquals("C-42", ex.getCaseId());
        assertEquals("contracts/orders-v2.yaml", ex.getContractPath());
        assertTrue(ex.isInputViolation());
        assertEquals(1, ex.getViolations().size());
        assertEquals(violation, ex.getViolations().get(0));
    }

    @Test
    void constructor_outputViolation_setsAllFields() {
        var violation = SchemaViolation.missingField("invoice_id");
        var ex = outputViolation(violation);

        assertEquals("GenerateInvoice", ex.getTaskId());
        assertEquals("C-99", ex.getCaseId());
        assertEquals("contracts/invoice-v1.yaml", ex.getContractPath());
        assertFalse(ex.isInputViolation());
        assertEquals(1, ex.getViolations().size());
    }

    @Test
    void constructor_multipleViolations_storesAll() {
        var v1 = SchemaViolation.missingField("total_amount");
        var v2 = SchemaViolation.missingField("currency");
        var v3 = SchemaViolation.missingField("tax_code");
        var ex = inputViolation(v1, v2, v3);

        assertEquals(3, ex.getViolations().size());
        assertEquals(v1, ex.getViolations().get(0));
        assertEquals(v2, ex.getViolations().get(1));
        assertEquals(v3, ex.getViolations().get(2));
    }

    @Test
    void getMessage_inputViolation_indicatesInputBoundary() {
        var ex = inputViolation(SchemaViolation.missingField("order_id"));

        String message = ex.getMessage();

        assertTrue(message.contains("INPUT"), "Message must indicate INPUT boundary");
        assertFalse(message.contains("OUTPUT"), "Should not contain OUTPUT");
    }

    @Test
    void getMessage_outputViolation_indicatesOutputBoundary() {
        var ex = outputViolation(SchemaViolation.missingField("invoice_number"));

        String message = ex.getMessage();

        assertTrue(message.contains("OUTPUT"), "Message must indicate OUTPUT boundary");
        assertFalse(message.contains("INPUT"), "Should not contain INPUT in message");
    }

    @Test
    void getMessage_containsTaskId() {
        var ex = inputViolation(SchemaViolation.missingField("field"));

        String message = ex.getMessage();

        assertTrue(message.contains("ProcessOrder"), "Message must contain task ID");
    }

    @Test
    void getMessage_containsCaseId() {
        var ex = inputViolation(SchemaViolation.missingField("field"));

        String message = ex.getMessage();

        assertTrue(message.contains("C-42"), "Message must contain case ID");
    }

    @Test
    void getMessage_containsContractPath() {
        var ex = inputViolation(SchemaViolation.missingField("field"));

        String message = ex.getMessage();

        assertTrue(message.contains("contracts/orders-v2.yaml"), "Message must contain contract path");
    }

    @Test
    void getMessage_containsAllViolationDescriptions() {
        var ex = inputViolation(
                SchemaViolation.missingField("total_amount"),
                SchemaViolation.missingField("currency")
        );

        String message = ex.getMessage();

        assertTrue(message.contains("total_amount"), "Message must list first missing field");
        assertTrue(message.contains("currency"), "Message must list second missing field");
    }

    @Test
    void getMessage_multipleViolations_allDescribed() {
        var v1 = SchemaViolation.missingField("field_1");
        var v2 = SchemaViolation.missingField("field_2");
        var v3 = SchemaViolation.missingField("field_3");
        var ex = inputViolation(v1, v2, v3);

        String message = ex.getMessage();

        assertTrue(message.contains("field_1"));
        assertTrue(message.contains("field_2"));
        assertTrue(message.contains("field_3"));
        assertTrue(message.contains("MISSING_FIELD"), "Should contain violation type indicator");
    }

    @Test
    void getMessage_formatsMessageReadably() {
        var ex = inputViolation(SchemaViolation.missingField("amount"));

        String message = ex.getMessage();

        assertNotNull(message);
        assertTrue(message.length() > 0, "Message should not be empty");
        // Check for multiline structure
        assertTrue(message.contains("\n"), "Message should have multiple lines");
    }

    @Test
    void isRuntimeException_notChecked() {
        var ex = inputViolation(SchemaViolation.missingField("field"));

        assertInstanceOf(RuntimeException.class, ex, "Should be RuntimeException (unchecked)");
    }

    @Test
    void fillInStackTrace_suppressesStackTrace() {
        var ex = inputViolation(SchemaViolation.missingField("order_id"));

        // Stack trace is suppressed for performance
        assertSame(ex, ex.fillInStackTrace(), "fillInStackTrace() must return 'this'");
        assertEquals(0, ex.getStackTrace().length, "Stack trace should be empty");
    }

    @Test
    void fillInStackTrace_idempotent() {
        var ex = inputViolation(SchemaViolation.missingField("field"));

        ex.fillInStackTrace();
        ex.fillInStackTrace();
        ex.fillInStackTrace();

        assertEquals(0, ex.getStackTrace().length, "Should remain empty after multiple calls");
    }

    @Test
    void violations_returnsImmutableList() {
        var ex = inputViolation(SchemaViolation.missingField("order_id"));

        assertThrows(UnsupportedOperationException.class,
                () -> ex.getViolations().add(SchemaViolation.missingField("other")),
                "Violations list must be immutable");
    }

    @Test
    void violations_isDefensiveCopy() {
        var original = List.of(SchemaViolation.missingField("field_1"));
        var ex = new TaskSchemaViolationException(
                "Task", "C-1", "contract.yaml", true, original
        );

        // Verify it's a defensive copy
        var violations = ex.getViolations();
        assertNotSame(original, violations, "Should be a defensive copy");
        assertEquals(original, violations);
    }

    @Test
    void constructor_differentTaskIds() {
        var ex1 = new TaskSchemaViolationException(
                "Task1", "C-1", "contract.yaml", true,
                List.of(SchemaViolation.missingField("field"))
        );
        var ex2 = new TaskSchemaViolationException(
                "Task2", "C-2", "contract.yaml", true,
                List.of(SchemaViolation.missingField("field"))
        );

        assertEquals("Task1", ex1.getTaskId());
        assertEquals("Task2", ex2.getTaskId());
        assertNotEquals(ex1.getMessage(), ex2.getMessage());
    }

    @Test
    void constructor_complexTaskIds_stored() {
        var ex = new TaskSchemaViolationException(
                "ComplexTaskName_v2.1", "C-complex-42",
                "complex/path/to/contract-v3.yaml",
                true, List.of(SchemaViolation.missingField("field"))
        );

        assertEquals("ComplexTaskName_v2.1", ex.getTaskId());
        assertEquals("C-complex-42", ex.getCaseId());
        assertEquals("complex/path/to/contract-v3.yaml", ex.getContractPath());
    }

    @Test
    void constructor_singleViolation_minimumCase() {
        var ex = new TaskSchemaViolationException(
                "MinimalTask", "C-min", "minimal.yaml", true,
                List.of(SchemaViolation.missingField("required"))
        );

        assertEquals(1, ex.getViolations().size());
        assertTrue(ex.isInputViolation());
    }

    @Test
    void constructor_emptyViolationsList_allowedButUnusual() {
        // Technically should have violations, but constructor allows empty list
        var ex = new TaskSchemaViolationException(
                "Task", "C-1", "contract.yaml", true,
                List.of()
        );

        assertTrue(ex.getViolations().isEmpty());
    }

    @Test
    void getCause_isNull() {
        var ex = inputViolation(SchemaViolation.missingField("field"));

        assertNull(ex.getCause(), "No underlying cause — domain condition, not error");
    }

    @Test
    void toString_includesTaskAndCaseInfo() {
        var ex = inputViolation(SchemaViolation.missingField("field"));

        String str = ex.toString();

        assertTrue(str.contains(ex.getClass().getSimpleName()));
        assertTrue(str.contains("ProcessOrder") || str.contains("C-42"),
                "Should include identifying info in toString");
    }

    @Test
    void multipleInstancesWithSameData_equalMessages() {
        var violations = List.of(SchemaViolation.missingField("amount"));
        var ex1 = new TaskSchemaViolationException(
                "Task", "C-1", "contract.yaml", true, violations
        );
        var ex2 = new TaskSchemaViolationException(
                "Task", "C-1", "contract.yaml", true, violations
        );

        assertEquals(ex1.getMessage(), ex2.getMessage());
    }

    @Test
    void violationTypesCanBeMixed() {
        var v1 = SchemaViolation.missingField("field_1");
        var v2 = SchemaViolation.unexpectedField("field_2");
        var ex = inputViolation(v1, v2);

        assertEquals(2, ex.getViolations().size());
        assertEquals(ViolationType.MISSING_FIELD, ex.getViolations().get(0).type());
        assertEquals(ViolationType.UNEXPECTED_FIELD, ex.getViolations().get(1).type());
    }

    @Test
    void getMessage_preservesViolationOrder() {
        var v1 = SchemaViolation.missingField("aaa");
        var v2 = SchemaViolation.missingField("bbb");
        var v3 = SchemaViolation.missingField("ccc");
        var ex = inputViolation(v1, v2, v3);

        String message = ex.getMessage();

        int posAaa = message.indexOf("aaa");
        int posBbb = message.indexOf("bbb");
        int posCcc = message.indexOf("ccc");

        assertTrue(posAaa < posBbb && posBbb < posCcc,
                "Violations should be listed in order");
    }
}
