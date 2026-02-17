/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.StaticMappingReasoner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Integration tests for StaticMappingReasoner (V6 feature).
 *
 * Chicago TDD: tests real WorkItemRecord objects and real file I/O for
 * properties loading. No mocks.
 *
 * Coverage targets:
 * - Direct mapping (exact task name match)
 * - Wildcard matching (* and ?)
 * - Capability inclusion/exclusion
 * - File loading (real file I/O)
 * - addMapping() programmatic API
 * - Guard conditions
 * - clearMappings() and getConfiguredTasks()
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class StaticMappingReasonerTest extends TestCase {

    private AgentCapability orderingCapability;
    private AgentCapability financeCapability;
    private StaticMappingReasoner orderingReasoner;

    public StaticMappingReasonerTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        orderingCapability = new AgentCapability("Ordering",
                "procurement, purchase orders, approvals");
        financeCapability = new AgentCapability("Finance",
                "invoicing, payments, accounts");
        orderingReasoner = new StaticMappingReasoner(orderingCapability);
    }

    // =========================================================================
    // Constructor tests
    // =========================================================================

    public void testConstructorWithCapabilityOnly() {
        StaticMappingReasoner reasoner = new StaticMappingReasoner(orderingCapability);
        assertNotNull(reasoner);
        assertTrue("Should have no configured tasks initially",
                reasoner.getConfiguredTasks().isEmpty());
    }

    public void testConstructorWithNullCapabilityThrows() {
        try {
            new StaticMappingReasoner(null);
            fail("Expected IllegalArgumentException for null capability");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("capability"));
        }
    }

    public void testConstructorWithMappings() {
        Map<String, Set<String>> mappings = new HashMap<>();
        mappings.put("Approve_Order", Set.of("Ordering", "Finance"));
        StaticMappingReasoner reasoner = new StaticMappingReasoner(orderingCapability, mappings);
        assertFalse("Should have configured tasks", reasoner.getConfiguredTasks().isEmpty());
    }

    public void testConstructorWithNullMappingsThrows() {
        try {
            new StaticMappingReasoner(orderingCapability, null);
            fail("Expected IllegalArgumentException for null mappings");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("mappings"));
        }
    }

    // =========================================================================
    // addMapping() tests
    // =========================================================================

    public void testAddMappingWithSetSucceeds() {
        Set<String> caps = new HashSet<>();
        caps.add("Ordering");
        caps.add("Finance");
        orderingReasoner.addMapping("Approve_Purchase_Order", caps);

        Set<String> configured = orderingReasoner.getConfiguredTasks();
        assertTrue("Task should be configured", configured.contains("Approve_Purchase_Order"));
    }

    public void testAddMappingWithStringSucceeds() {
        orderingReasoner.addMapping("Create_Invoice", "Finance, Accounting");

        Set<String> caps = orderingReasoner.getCapabilitiesForTask("Create_Invoice");
        assertFalse("Should have capabilities for task", caps.isEmpty());
        assertTrue(caps.contains("Finance"));
        assertTrue(caps.contains("Accounting"));
    }

    public void testAddMappingNullTaskNameThrows() {
        try {
            orderingReasoner.addMapping((String) null, "Ordering");
            fail("Expected IllegalArgumentException for null taskName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("taskName"));
        }
    }

    public void testAddMappingEmptyCapabilitiesStringThrows() {
        try {
            orderingReasoner.addMapping("SomeTask", "");
            fail("Expected IllegalArgumentException for empty capabilitiesStr");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("capabilitiesStr"));
        }
    }

    public void testAddMappingEmptyCapabilitySetThrows() {
        try {
            orderingReasoner.addMapping("SomeTask", new HashSet<>());
            fail("Expected IllegalArgumentException for empty capabilities set");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("capabilities"));
        }
    }

    // =========================================================================
    // isEligible() - direct mapping tests
    // =========================================================================

    public void testEligibleForExactMatchTask() {
        orderingReasoner.addMapping("Approve_Purchase_Order", "Ordering, Finance");

        WorkItemRecord wir = createWorkItem("case1", "Approve_Purchase_Order");
        assertTrue("Ordering agent should be eligible for Approve_Purchase_Order",
                orderingReasoner.isEligible(wir));
    }

    public void testNotEligibleWhenCapabilityNotInMapping() {
        StaticMappingReasoner financeReasoner = new StaticMappingReasoner(financeCapability);
        financeReasoner.addMapping("Ship_Order", "Logistics, Carrier");

        WorkItemRecord wir = createWorkItem("case1", "Ship_Order");
        assertFalse("Finance agent should not be eligible for Ship_Order",
                financeReasoner.isEligible(wir));
    }

    public void testNotEligibleForUnknownTask() {
        orderingReasoner.addMapping("Approve_Purchase_Order", "Ordering");

        WorkItemRecord wir = createWorkItem("case1", "Unknown_Task");
        assertFalse("Agent should not be eligible for unmapped task",
                orderingReasoner.isEligible(wir));
    }

    public void testEligibleWithMultipleCapabilitiesInMapping() {
        orderingReasoner.addMapping("Process_Order", "Ordering,Finance,Logistics");

        WorkItemRecord wir = createWorkItem("case1", "Process_Order");
        assertTrue("Ordering agent should be eligible", orderingReasoner.isEligible(wir));

        StaticMappingReasoner financeReasoner = new StaticMappingReasoner(financeCapability);
        financeReasoner.addMapping("Process_Order", "Ordering,Finance,Logistics");
        assertTrue("Finance agent should also be eligible", financeReasoner.isEligible(wir));
    }

    public void testBothAgentsEligibleForSharedTask() {
        StaticMappingReasoner financeReasoner = new StaticMappingReasoner(financeCapability);
        orderingReasoner.addMapping("Approve_Purchase_Order", "Ordering, Finance");
        financeReasoner.addMapping("Approve_Purchase_Order", "Ordering, Finance");

        WorkItemRecord workItem = createWorkItem("100.3", "Approve_Purchase_Order");

        assertTrue("Ordering should be eligible for Approve_Purchase_Order",
                orderingReasoner.isEligible(workItem));
        assertTrue("Finance should be eligible for Approve_Purchase_Order",
                financeReasoner.isEligible(workItem));
    }

    // =========================================================================
    // isEligible() - wildcard matching
    // =========================================================================

    public void testWildcardStarMatchesAnyTask() {
        orderingReasoner.addMapping("*", "Ordering");

        WorkItemRecord wir1 = createWorkItem("case1", "Any_Task");
        WorkItemRecord wir2 = createWorkItem("case2", "Another_Task");
        assertTrue("Wildcard * should match any task", orderingReasoner.isEligible(wir1));
        assertTrue("Wildcard * should match any task", orderingReasoner.isEligible(wir2));
    }

    public void testWildcardQuestionMarkMatchesSingleChar() {
        orderingReasoner.addMapping("Task_?", "Ordering");

        WorkItemRecord wir1 = createWorkItem("case1", "Task_A");
        WorkItemRecord wir2 = createWorkItem("case2", "Task_B");
        WorkItemRecord wir3 = createWorkItem("case3", "Task_AB");

        assertTrue("Task_? should match Task_A", orderingReasoner.isEligible(wir1));
        assertTrue("Task_? should match Task_B", orderingReasoner.isEligible(wir2));
        assertFalse("Task_? should NOT match Task_AB", orderingReasoner.isEligible(wir3));
    }

    public void testWildcardStarInMiddleOfPattern() {
        orderingReasoner.addMapping("Approve_*", "Ordering");

        WorkItemRecord wir1 = createWorkItem("case1", "Approve_Purchase_Order");
        WorkItemRecord wir2 = createWorkItem("case2", "Approve_Invoice");
        WorkItemRecord wir3 = createWorkItem("case3", "Reject_Order");

        assertTrue("Approve_* should match Approve_Purchase_Order",
                orderingReasoner.isEligible(wir1));
        assertTrue("Approve_* should match Approve_Invoice",
                orderingReasoner.isEligible(wir2));
        assertFalse("Approve_* should NOT match Reject_Order",
                orderingReasoner.isEligible(wir3));
    }

    // =========================================================================
    // isEligible() - null work item
    // =========================================================================

    public void testEligibleWithNullWorkItemThrows() {
        try {
            orderingReasoner.isEligible(null);
            fail("Expected IllegalArgumentException for null workItem");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("workItem"));
        }
    }

    // =========================================================================
    // isEligible() - falls back to taskID when taskName is null
    // =========================================================================

    public void testFallsBackToTaskIDWhenTaskNameIsNull() {
        orderingReasoner.addMapping("task-id-001", "Ordering");

        WorkItemRecord wir = new WorkItemRecord("case1", "task-id-001",
                "http://spec", WorkItemRecord.statusEnabled);
        // taskName defaults to null, taskID is "task-id-001"
        assertTrue("Should use taskID as fallback when taskName is null",
                orderingReasoner.isEligible(wir));
    }

    // =========================================================================
    // Case sensitivity test
    // =========================================================================

    public void testEligibilityIsCaseSensitiveOnDomainName() {
        AgentCapability lowerCaseCapability = new AgentCapability("ordering",
                "lower case domain name test");
        StaticMappingReasoner lowerReasoner = new StaticMappingReasoner(lowerCaseCapability);
        lowerReasoner.addMapping("Create_Purchase_Order", "Ordering");

        WorkItemRecord workItem = createWorkItem("700.1", "Create_Purchase_Order");
        assertFalse("Case-sensitive: 'ordering' should not match 'Ordering' capability",
                lowerReasoner.isEligible(workItem));
    }

    // =========================================================================
    // clearMappings() and getCapabilitiesForTask() tests
    // =========================================================================

    public void testClearMappingsRemovesAllMappings() {
        orderingReasoner.addMapping("Task_A", "Ordering");
        orderingReasoner.addMapping("Task_B", "Finance");

        assertFalse("Should have tasks before clear",
                orderingReasoner.getConfiguredTasks().isEmpty());

        orderingReasoner.clearMappings();

        assertTrue("Should have no tasks after clear",
                orderingReasoner.getConfiguredTasks().isEmpty());
    }

    public void testGetCapabilitiesForTask() {
        orderingReasoner.addMapping("Approve_Order", "Ordering, Finance");

        Set<String> caps = orderingReasoner.getCapabilitiesForTask("Approve_Order");
        assertEquals(2, caps.size());
        assertTrue(caps.contains("Ordering"));
        assertTrue(caps.contains("Finance"));
    }

    public void testGetCapabilitiesForUnknownTaskReturnsEmpty() {
        Set<String> caps = orderingReasoner.getCapabilitiesForTask("Nonexistent_Task");
        assertNotNull(caps);
        assertTrue("Should return empty set for unknown task", caps.isEmpty());
    }

    public void testGetCapabilitiesForNullTaskReturnsEmpty() {
        Set<String> caps = orderingReasoner.getCapabilitiesForTask(null);
        assertNotNull(caps);
        assertTrue("Should return empty set for null task name", caps.isEmpty());
    }

    // =========================================================================
    // File loading tests
    // =========================================================================

    public void testLoadFromRealPropertiesFile() throws IOException {
        File tempFile = File.createTempFile("test-mappings", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter fw = new FileWriter(tempFile)) {
            fw.write("# Test mappings\n");
            fw.write("Approve_Purchase_Order=Ordering,Finance\n");
            fw.write("Ship_Order=Logistics,Carrier\n");
            fw.write("Create_Invoice=Finance,Accounting\n");
        }

        StaticMappingReasoner reasoner = new StaticMappingReasoner(orderingCapability);
        reasoner.loadFromFile(tempFile.getAbsolutePath());

        Set<String> tasks = reasoner.getConfiguredTasks();
        assertTrue("Should have Approve_Purchase_Order",
                tasks.contains("Approve_Purchase_Order"));
        assertTrue("Should have Ship_Order", tasks.contains("Ship_Order"));
        assertTrue("Should have Create_Invoice", tasks.contains("Create_Invoice"));

        WorkItemRecord wir = createWorkItem("case1", "Approve_Purchase_Order");
        assertTrue("Should be eligible after loading from file",
                reasoner.isEligible(wir));
    }

    public void testLoadFromFileWithNullPathThrows() throws IOException {
        try {
            orderingReasoner.loadFromFile(null);
            fail("Expected IllegalArgumentException for null filePath");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("filePath"));
        }
    }

    public void testLoadFromFileWithNonexistentFileThrows() {
        try {
            orderingReasoner.loadFromFile("/nonexistent/path/mappings.properties");
            fail("Expected IOException for nonexistent file");
        } catch (IOException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // getConfiguredTasks() defensive copy test
    // =========================================================================

    public void testGetConfiguredTasksReturnsDefensiveCopy() {
        orderingReasoner.addMapping("Task_A", "Ordering");

        Set<String> tasks1 = orderingReasoner.getConfiguredTasks();
        tasks1.add("Injected_Task");

        Set<String> tasks2 = orderingReasoner.getConfiguredTasks();
        assertFalse("Modifying returned set should not affect internal state",
                tasks2.contains("Injected_Task"));
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private WorkItemRecord createWorkItem(String caseID, String taskName) {
        WorkItemRecord wir = new WorkItemRecord(caseID, taskName + "_id",
                "http://spec.uri", WorkItemRecord.statusEnabled);
        wir.setTaskName(taskName);
        return wir;
    }
}
