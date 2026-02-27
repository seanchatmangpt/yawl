/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemID;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API contract tests for YAWL v6 Interface B client.
 *
 * Validates that the public API surface meets the expected contract:
 * - Required methods are present and accessible
 * - Method signatures match expected types
 * - Return types are correct
 * - Exceptions are properly declared
 *
 * Chicago TDD: Real class reflection, real API validation, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-18
 */
@Tag("integration")
class ApiContractTest {

    // =========================================================================
    // InterfaceBClient Contract Tests
    // =========================================================================

    @Test
    void testInterfaceBClientExists() throws Exception {
        Class<?> clientClass = Class.forName(
                "org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient");
        assertNotNull(clientClass, "InterfaceBClient class must exist");
        assertTrue(clientClass.isInterface(),
                "InterfaceBClient must be an interface");
    }

    @Test
    void testInterfaceBClientRequiredMethods() throws Exception {
        Class<?> clientClass = Class.forName(
                "org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient");

        Set<String> methodNames = Arrays.stream(clientClass.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        // Required workflow methods
        String[] requiredMethods = {
            "launchCase",
            "getAvailableWorkItems",
            "getAllWorkItems",
            "startWorkItem",
            "completeWorkItem",
            "getWorkItem",
            "getCaseData",
            "getTaskDefinition"
        };

        for (String method : requiredMethods) {
            assertTrue(methodNames.contains(method),
                    "InterfaceBClient must have method: " + method);
        }
    }

    @Test
    void testLaunchCaseMethodSignature() throws Exception {
        Class<?> clientClass = Class.forName(
                "org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient");

        Method launchCase = Arrays.stream(clientClass.getMethods())
                .filter(m -> "launchCase".equals(m.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "launchCase method must exist"));

        // Return type must be String (case ID)
        assertEquals(String.class, launchCase.getReturnType(),
                "launchCase must return String (case ID)");

        // Must accept YSpecificationID as first parameter
        Class<?>[] paramTypes = launchCase.getParameterTypes();
        assertTrue(paramTypes.length >= 1,
                "launchCase must have at least one parameter");
        assertEquals(YSpecificationID.class, paramTypes[0],
                "First parameter must be YSpecificationID");
    }

    @Test
    void testWorkItemMethodsSignature() throws Exception {
        Class<?> clientClass = Class.forName(
                "org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient");

        // startWorkItem must accept YWorkItem
        Method startWorkItem = Arrays.stream(clientClass.getMethods())
                .filter(m -> "startWorkItem".equals(m.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(Arrays.asList(startWorkItem.getParameterTypes())
                .contains(YWorkItem.class),
                "startWorkItem must accept YWorkItem parameter");

        // completeWorkItem must accept YWorkItem
        Method completeWorkItem = Arrays.stream(clientClass.getMethods())
                .filter(m -> "completeWorkItem".equals(m.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(Arrays.asList(completeWorkItem.getParameterTypes())
                .contains(YWorkItem.class),
                "completeWorkItem must accept YWorkItem parameter");
    }

    // =========================================================================
    // YSpecificationID Contract Tests
    // =========================================================================

    @Test
    void testYSpecificationIdContract() {
        YSpecificationID id = new YSpecificationID("TestSpec", "1.0", "test.yawl");

        // Must have accessor methods
        assertEquals("TestSpec", id.getIdentifier(),
                "getIdentifier must return identifier");
        assertEquals("test.yawl", id.getUri(),
                "getUri must return URI");
        assertNotNull(id.getVersionAsString(),
                "getVersionAsString must return non-null");

        // Must implement equals/hashCode properly
        YSpecificationID id2 = new YSpecificationID("TestSpec", "1.0", "test.yawl");
        assertEquals(id, id2, "Equal specifications must be equal");
        assertEquals(id.hashCode(), id2.hashCode(),
                "Equal specifications must have same hashCode");

        // Must have proper toString
        assertNotNull(id.toString(), "toString must return non-null");
        assertFalse(id.toString().isEmpty(), "toString must return non-empty");
    }

    @Test
    void testYSpecificationIdInequality() {
        YSpecificationID id1 = new YSpecificationID("SpecA", "1.0", "a.yawl");
        YSpecificationID id2 = new YSpecificationID("SpecB", "1.0", "b.yawl");
        YSpecificationID id3 = new YSpecificationID("SpecA", "2.0", "a.yawl");

        assertNotEquals(id1, id2, "Different identifiers must not be equal");
        assertNotEquals(id1, id3, "Different versions must not be equal");
    }

    @Test
    void testYSpecificationIdNullSafety() {
        YSpecificationID id = new YSpecificationID("Test", "1.0", "test.yawl");

        assertFalse(id.equals(null), "equals(null) must return false");
        assertTrue(id.equals(id), "equals(self) must return true");
    }

    // =========================================================================
    // YWorkItem Contract Tests
    // =========================================================================

    @Test
    void testYWorkItemStatusContract() {
        // All required statuses must exist
        assertNotNull(YWorkItemStatus.statusEnabled, "statusEnabled must exist");
        assertNotNull(YWorkItemStatus.statusExecuting, "statusExecuting must exist");
        assertNotNull(YWorkItemStatus.statusComplete, "statusComplete must exist");
        assertNotNull(YWorkItemStatus.statusIsParent, "statusIsParent must exist");
        assertNotNull(YWorkItemStatus.statusSuspended, "statusSuspended must exist");
        assertNotNull(YWorkItemStatus.statusFired, "statusFired must exist");
        assertNotNull(YWorkItemStatus.statusDeadlocked, "statusDeadlocked must exist");
        assertNotNull(YWorkItemStatus.statusFailed, "statusFailed must exist");
        assertNotNull(YWorkItemStatus.statusDeleted, "statusDeleted must exist");
    }

    @Test
    void testYWorkItemStatusTransitions() throws Exception {
        // Create minimal work item for testing
        YSpecification spec = createMinimalSpec("status-test");
        YAtomicTask task = (YAtomicTask) spec.getRootNet().getNetElement("process");
        YIdentifier caseId = new YIdentifier(null);
        YWorkItemID wid = new YWorkItemID(caseId, "process");
        YWorkItem item = new YWorkItem(null, spec.getSpecificationID(),
                task, wid, true, false);

        // Initial status must be Enabled
        assertEquals(YWorkItemStatus.statusEnabled, item.getStatus(),
                "Initial status must be Enabled");

        // Can transition to Executing
        item.setStatus(YWorkItemStatus.statusExecuting);
        assertEquals(YWorkItemStatus.statusExecuting, item.getStatus(),
                "Status must transition to Executing");

        // Can transition to Complete
        item.setStatus(YWorkItemStatus.statusComplete);
        assertEquals(YWorkItemStatus.statusComplete, item.getStatus(),
                "Status must transition to Complete");
    }

    @Test
    void testYWorkItemIdentity() throws Exception {
        YSpecification spec = createMinimalSpec("identity-test");
        YAtomicTask task = (YAtomicTask) spec.getRootNet().getNetElement("process");
        YIdentifier caseId = new YIdentifier(null);
        YWorkItemID wid = new YWorkItemID(caseId, "process");
        YWorkItem item = new YWorkItem(null, spec.getSpecificationID(),
                task, wid, true, false);

        // ID string must be non-null and non-empty
        assertNotNull(item.getIDString(), "getIDString must not return null");
        assertFalse(item.getIDString().isEmpty(), "getIDString must not return empty");

        // Task must be accessible
        assertNotNull(item.getTask(), "getTask must not return null");
        assertEquals("process", item.getTask().getID(), "Task ID must match");
    }

    // =========================================================================
    // YWorkItemID Contract Tests
    // =========================================================================

    @Test
    void testYWorkItemIdContract() throws Exception {
        YIdentifier caseId = new YIdentifier(null);
        String taskId = "test-task";
        YWorkItemID wid = new YWorkItemID(caseId, taskId);

        assertNotNull(wid, "YWorkItemID must be constructable");
        assertNotNull(wid.toString(), "toString must not return null");
        assertFalse(wid.toString().isEmpty(), "toString must not return empty");
    }

    @Test
    void testYWorkItemIdEquality() throws Exception {
        YIdentifier caseId1 = new YIdentifier(null);
        YIdentifier caseId2 = new YIdentifier(null);

        YWorkItemID wid1 = new YWorkItemID(caseId1, "task-a");
        YWorkItemID wid2 = new YWorkItemID(caseId1, "task-a");
        YWorkItemID wid3 = new YWorkItemID(caseId2, "task-a");
        YWorkItemID wid4 = new YWorkItemID(caseId1, "task-b");

        assertEquals(wid1, wid2, "Same case and task must be equal");
        assertNotEquals(wid1, wid3, "Different case must not be equal");
        assertNotEquals(wid1, wid4, "Different task must not be equal");
    }

    // =========================================================================
    // YIdentifier Contract Tests
    // =========================================================================

    @Test
    void testYIdentifierContract() throws Exception {
        YIdentifier id1 = new YIdentifier(null);
        YIdentifier id2 = new YIdentifier(null);

        assertNotNull(id1, "YIdentifier must be constructable");
        assertNotNull(id1.toString(), "toString must not return null");
        assertFalse(id1.toString().isEmpty(), "toString must not return empty");

        // Different instances must not be equal (unique identifiers)
        assertNotEquals(id1, id2, "Different YIdentifier instances must not be equal");
    }

    // =========================================================================
    // YEngine API Surface Tests
    // =========================================================================

    @Test
    void testYEnginePublicApi() throws Exception {
        Class<?> engineClass = Class.forName("org.yawlfoundation.yawl.engine.YEngine");

        Set<String> methodNames = Arrays.stream(engineClass.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        // Core workflow lifecycle methods
        String[] coreMethods = {
            "getInstance",
            "launchCase",
            "cancelCase",
            "getSpecifications"
        };

        for (String method : coreMethods) {
            assertTrue(methodNames.contains(method),
                    "YEngine must have method: " + method);
        }
    }

    @Test
    void testYEngineImplementsInterfaceB() throws Exception {
        Class<?> engineClass = Class.forName("org.yawlfoundation.yawl.engine.YEngine");
        Class<?> interfaceB = Class.forName(
                "org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient");

        assertTrue(interfaceB.isAssignableFrom(engineClass),
                "YEngine must implement InterfaceBClient");
    }

    // =========================================================================
    // Exception Contract Tests
    // =========================================================================

    @Test
    void testExceptionHierarchy() throws Exception {
        Class<?> baseException = Class.forName(
                "org.yawlfoundation.yawl.exceptions.YAWLException");
        assertNotNull(baseException, "YAWLException must exist");
        assertTrue(Exception.class.isAssignableFrom(baseException),
                "YAWLException must extend Exception");

        // Required exception subclasses
        String[] exceptionTypes = {
            "org.yawlfoundation.yawl.exceptions.YStateException",
            "org.yawlfoundation.yawl.exceptions.YDataStateException",
            "org.yawlfoundation.yawl.exceptions.YPersistenceException",
            "org.yawlfoundation.yawl.exceptions.YQueryException",
            "org.yawlfoundation.yawl.exceptions.YEngineStateException"
        };

        for (String exceptionClass : exceptionTypes) {
            Class<?> ex = Class.forName(exceptionClass);
            assertTrue(baseException.isAssignableFrom(ex),
                    exceptionClass + " must extend YAWLException");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static YSpecification createMinimalSpec(String specId) throws Exception {
        YSpecification spec = new YSpecification(specId);
        org.yawlfoundation.yawl.elements.YNet net =
                new org.yawlfoundation.yawl.elements.YNet("root", spec);
        spec.setRootNet(net);

        org.yawlfoundation.yawl.elements.YInputCondition input =
                new org.yawlfoundation.yawl.elements.YInputCondition("input", net);
        org.yawlfoundation.yawl.elements.YOutputCondition output =
                new org.yawlfoundation.yawl.elements.YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask task = new YAtomicTask("process",
                YAtomicTask._AND, YAtomicTask._AND, net);
        net.addNetElement(task);

        return spec;
    }
}
