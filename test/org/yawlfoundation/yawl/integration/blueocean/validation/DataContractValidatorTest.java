/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.integration.blueocean.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.yawlfoundation.yawl.integration.blueocean.lineage.RdfLineageStore;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DataContractValidator.
 */
public class DataContractValidatorTest {
    private DataContractValidator validator;
    private RdfLineageStore lineageStore;

    @TempDir
    Path tdb2Dir;

    @TempDir
    Path luceneDir;

    @BeforeEach
    void setUp() {
        lineageStore = new RdfLineageStore(tdb2Dir.toString(), luceneDir.toString());
        validator = new DataContractValidator(lineageStore);
    }

    @Test
    void testNullLineageStoreThrows() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
                new DataContractValidator(null));
    }

    @Test
    void testRegisterContract() {
        // Given
        String taskId = "task-001";
        DataContractValidator.TaskDataContract contract =
                new DataContractValidator.TaskDataContract(taskId);
        contract.addRequiredInput("customerId", "String");

        // When
        validator.registerContract(taskId, contract);

        // Then - no exception thrown
        assertDoesNotThrow(() ->
                validator.getBlockingConstraints(null, Map.of("customerId", "123")));
    }

    @Test
    void testTaskDataContract() {
        // Given
        DataContractValidator.TaskDataContract contract =
                new DataContractValidator.TaskDataContract("task-001");

        // When
        contract.addRequiredInput("id", "Integer");
        contract.addExpectedOutput("result", "String");
        contract.addLineageRequirement("orders");
        contract.setDeadlineMinutes(30);

        // Then
        assertFalse(contract.requiredInputs.isEmpty());
        assertFalse(contract.expectedOutputs.isEmpty());
        assertFalse(contract.lineageRequirements.isEmpty());
        assertEquals(30, contract.deadlineMinutes);
    }

    @Test
    void testConstraintRecord() {
        // Given
        String code = "MISSING_INPUT";
        String message = "Required input not found";
        boolean blocking = true;

        // When
        DataContractValidator.Constraint constraint =
                new DataContractValidator.Constraint(code, message, blocking);

        // Then
        assertEquals(code, constraint.code());
        assertEquals(message, constraint.message());
        assertEquals(blocking, constraint.blocking());
    }

    @Test
    void testDataContractViolationException() {
        // When
        String message = "Input validation failed";
        DataContractValidator.DataContractViolationException exception =
                new DataContractValidator.DataContractViolationException(message);

        // Then
        assertEquals(message, exception.getMessage());
    }

    @Test
    void testDataContractViolationExceptionWithCause() {
        // When
        String message = "Validation error";
        Throwable cause = new RuntimeException("Root cause");
        DataContractValidator.DataContractViolationException exception =
                new DataContractValidator.DataContractViolationException(message, cause);

        // Then
        assertEquals(message, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testGetBlockingConstraintsWithEmptyState() {
        // Given
        DataContractValidator.TaskDataContract contract =
                new DataContractValidator.TaskDataContract("task-001");
        contract.addRequiredInput("id", "String");

        validator.registerContract("task-001", contract);

        // When
        List<DataContractValidator.Constraint> constraints =
                validator.getBlockingConstraints("task-001");

        // Then - should have blocking constraint for missing input
        assertFalse(constraints.isEmpty());
    }

    @Test
    void testEnforceDataGuardsNullThrows() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
                validator.enforceDataGuards(null));
    }

    @Test
    void testCanTaskRunWithValidState() {
        // Given
        DataContractValidator.TaskDataContract contract =
                new DataContractValidator.TaskDataContract("task-001");
        contract.addRequiredInput("id", "String");
        validator.registerContract("task-001", contract);

        Map<String, Object> state = Map.of("id", "customer-123");

        // When
        boolean canRun = validator.canTaskRun(null, state);

        // Then
        assertTrue(canRun);
    }

    @Test
    void testGetBlockingConstraintsReturnsEmptyWhenValid() {
        // Given
        DataContractValidator.TaskDataContract contract =
                new DataContractValidator.TaskDataContract("task-001");
        contract.addRequiredInput("productId", "Integer");

        validator.registerContract("task-001", contract);
        Map<String, Object> state = Map.of("productId", 456);

        // When
        List<DataContractValidator.Constraint> constraints =
                validator.getBlockingConstraints("task-001", state);

        // Then
        assertEquals(0, constraints.size());
    }

    @Test
    void testNullTaskThrows() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
                validator.getBlockingConstraints(null));
    }

    @Test
    void testNullWorkflowStateThrows() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
                validator.canTaskRun(null, null));
    }

    @Test
    void testRegisterContractNullTaskIdThrows() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
                validator.registerContract(null,
                        new DataContractValidator.TaskDataContract("task-001")));
    }

    @Test
    void testRegisterContractNullContractThrows() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
                validator.registerContract("task-001", null));
    }

    @Test
    void testAddRequiredInputNullName() {
        // Given
        DataContractValidator.TaskDataContract contract =
                new DataContractValidator.TaskDataContract("task-001");

        // When/Then
        assertThrows(NullPointerException.class, () ->
                contract.addRequiredInput(null, "String"));
    }

    @Test
    void testAddLineageRequirementNullTableId() {
        // Given
        DataContractValidator.TaskDataContract contract =
                new DataContractValidator.TaskDataContract("task-001");

        // When/Then
        assertThrows(NullPointerException.class, () ->
                contract.addLineageRequirement(null));
    }

    @Test
    void testTypeCompatibilityChecking() {
        // Given
        Map<String, Object> data = new HashMap<>();
        data.put("stringVal", "hello");
        data.put("intVal", 42);
        data.put("doubleVal", 3.14);
        data.put("boolVal", true);
        data.put("listVal", java.util.List.of(1, 2, 3));

        // When - create contract with type requirements
        DataContractValidator.TaskDataContract contract =
                new DataContractValidator.TaskDataContract("task-types");
        contract.addRequiredInput("stringVal", "String");
        contract.addRequiredInput("intVal", "int");
        contract.addRequiredInput("doubleVal", "double");

        validator.registerContract("task-types", contract);

        // Then - all types should be compatible
        List<DataContractValidator.Constraint> violations =
                validator.getBlockingConstraints("task-types", data);

        // Should only check required inputs, so empty or minimal violations
        assertTrue(violations.stream()
                .noneMatch(c -> c.code().equals("TYPE_MISMATCH")));
    }
}
