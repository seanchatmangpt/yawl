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
 * Tests for StaticMappingReasoner.
 * Chicago TDD style - test real rule-based eligibility checking.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class StaticMappingReasonerTest extends TestCase {

    private AgentCapability capability;
    private StaticMappingReasoner reasoner;

    public StaticMappingReasonerTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        capability = new AgentCapability("Ordering", "procurement, purchase orders");
        reasoner = new StaticMappingReasoner(capability);
    }

    public void testConstructorWithCapability() {
        StaticMappingReasoner r = new StaticMappingReasoner(capability);
        assertNotNull(r);
    }

    public void testConstructorRejectsNullCapability() {
        try {
            new StaticMappingReasoner(null);
            fail("Should reject null capability");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("capability is required"));
        }
    }

    public void testConstructorWithMappings() {
        Map<String, Set<String>> mappings = new HashMap<>();
        Set<String> capabilities = new HashSet<>();
        capabilities.add("Ordering");
        mappings.put("ApproveOrder", capabilities);

        StaticMappingReasoner r = new StaticMappingReasoner(capability, mappings);
        assertNotNull(r);
        assertEquals(1, r.getConfiguredTasks().size());
    }

    public void testConstructorRejectsNullMappings() {
        try {
            new StaticMappingReasoner(capability, null);
            fail("Should reject null mappings");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("mappings cannot be null"));
        }
    }

    public void testAddMappingWithSet() {
        Set<String> capabilities = new HashSet<>();
        capabilities.add("Ordering");
        capabilities.add("Finance");

        reasoner.addMapping("ApproveOrder", capabilities);

        assertTrue(reasoner.getConfiguredTasks().contains("ApproveOrder"));
        Set<String> mapped = reasoner.getCapabilitiesForTask("ApproveOrder");
        assertEquals(2, mapped.size());
        assertTrue(mapped.contains("Ordering"));
        assertTrue(mapped.contains("Finance"));
    }

    public void testAddMappingWithString() {
        reasoner.addMapping("CreateInvoice", "Finance,Accounting");

        Set<String> mapped = reasoner.getCapabilitiesForTask("CreateInvoice");
        assertEquals(2, mapped.size());
        assertTrue(mapped.contains("Finance"));
        assertTrue(mapped.contains("Accounting"));
    }

    public void testAddMappingRejectsNullTaskName() {
        Set<String> capabilities = new HashSet<>();
        capabilities.add("Ordering");

        try {
            reasoner.addMapping(null, capabilities);
            fail("Should reject null taskName");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("taskName is required"));
        }
    }

    public void testAddMappingRejectsEmptyCapabilities() {
        try {
            reasoner.addMapping("Task", new HashSet<>());
            fail("Should reject empty capabilities");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("capabilities cannot be empty"));
        }
    }

    public void testIsEligibleWithExactMatch() {
        reasoner.addMapping("ApproveOrder", "Ordering,Finance");

        WorkItemRecord wir = createWorkItem("wi-1", "ApproveOrder", "case-1");

        boolean eligible = reasoner.isEligible(wir);

        assertTrue("Should be eligible for matching task", eligible);
    }

    public void testIsEligibleWithNonMatch() {
        reasoner.addMapping("CreateInvoice", "Finance,Accounting");

        WorkItemRecord wir = createWorkItem("wi-1", "CreateInvoice", "case-1");

        boolean eligible = reasoner.isEligible(wir);

        assertFalse("Should not be eligible for non-matching capability", eligible);
    }

    public void testIsEligibleWithWildcard() {
        reasoner.addMapping("*", "Ordering");

        WorkItemRecord wir1 = createWorkItem("wi-1", "AnyTask", "case-1");
        WorkItemRecord wir2 = createWorkItem("wi-2", "AnotherTask", "case-1");

        assertTrue("Should match any task with wildcard", reasoner.isEligible(wir1));
        assertTrue("Should match any task with wildcard", reasoner.isEligible(wir2));
    }

    public void testIsEligibleWithPatternMatch() {
        reasoner.addMapping("Approve*", "Ordering");

        WorkItemRecord approve1 = createWorkItem("wi-1", "ApproveOrder", "case-1");
        WorkItemRecord approve2 = createWorkItem("wi-2", "ApprovePayment", "case-1");
        WorkItemRecord create = createWorkItem("wi-3", "CreateOrder", "case-1");

        assertTrue("Should match ApproveOrder", reasoner.isEligible(approve1));
        assertTrue("Should match ApprovePayment", reasoner.isEligible(approve2));
        assertFalse("Should not match CreateOrder", reasoner.isEligible(create));
    }

    public void testIsEligibleWithQuestionMarkPattern() {
        reasoner.addMapping("Task?", "Ordering");

        WorkItemRecord task1 = createWorkItem("wi-1", "Task1", "case-1");
        WorkItemRecord task2 = createWorkItem("wi-2", "Task2", "case-1");
        WorkItemRecord task10 = createWorkItem("wi-3", "Task10", "case-1");

        assertTrue("Should match Task1", reasoner.isEligible(task1));
        assertTrue("Should match Task2", reasoner.isEligible(task2));
        assertFalse("Should not match Task10 (two chars)", reasoner.isEligible(task10));
    }

    public void testIsEligibleRejectsNullWorkItem() {
        try {
            reasoner.isEligible(null);
            fail("Should reject null workItem");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("workItem is required"));
        }
    }

    public void testIsEligibleReturnsFalseWhenNoMappings() {
        WorkItemRecord wir = createWorkItem("wi-1", "AnyTask", "case-1");

        boolean eligible = reasoner.isEligible(wir);

        assertFalse("Should return false when no mappings configured", eligible);
    }

    public void testGetConfiguredTasks() {
        reasoner.addMapping("Task1", "Ordering");
        reasoner.addMapping("Task2", "Finance");
        reasoner.addMapping("Task3", "Shipping");

        Set<String> tasks = reasoner.getConfiguredTasks();

        assertEquals(3, tasks.size());
        assertTrue(tasks.contains("Task1"));
        assertTrue(tasks.contains("Task2"));
        assertTrue(tasks.contains("Task3"));
    }

    public void testGetCapabilitiesForTask() {
        reasoner.addMapping("ApproveOrder", "Ordering,Finance,Management");

        Set<String> capabilities = reasoner.getCapabilitiesForTask("ApproveOrder");

        assertEquals(3, capabilities.size());
        assertTrue(capabilities.contains("Ordering"));
        assertTrue(capabilities.contains("Finance"));
        assertTrue(capabilities.contains("Management"));
    }

    public void testGetCapabilitiesForNonExistentTask() {
        Set<String> capabilities = reasoner.getCapabilitiesForTask("NonExistent");

        assertNotNull(capabilities);
        assertTrue(capabilities.isEmpty());
    }

    public void testClearMappings() {
        reasoner.addMapping("Task1", "Ordering");
        reasoner.addMapping("Task2", "Finance");
        assertEquals(2, reasoner.getConfiguredTasks().size());

        reasoner.clearMappings();

        assertEquals(0, reasoner.getConfiguredTasks().size());
    }

    public void testLoadFromFile() throws Exception {
        File tempFile = File.createTempFile("mappings", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("ApproveOrder=Ordering,Finance\n");
            writer.write("CreateInvoice=Finance,Accounting\n");
            writer.write("ShipOrder=Logistics,Carrier\n");
        }

        reasoner.loadFromFile(tempFile.getAbsolutePath());

        assertEquals(3, reasoner.getConfiguredTasks().size());
        assertTrue(reasoner.getCapabilitiesForTask("ApproveOrder").contains("Ordering"));
        assertTrue(reasoner.getCapabilitiesForTask("CreateInvoice").contains("Finance"));
        assertTrue(reasoner.getCapabilitiesForTask("ShipOrder").contains("Logistics"));
    }

    public void testLoadFromFileRejectsNullPath() {
        try {
            reasoner.loadFromFile(null);
            fail("Should reject null filePath");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("filePath is required"));
        } catch (IOException e) {
            fail("Should throw IllegalArgumentException, not IOException");
        }
    }

    public void testLoadFromFileRejectsNonExistentFile() {
        try {
            reasoner.loadFromFile("/nonexistent/path/to/file.properties");
            fail("Should reject non-existent file");
        } catch (Exception e) {
            assertNotNull(e);
        }
    }

    public void testLoadFromFileWithEmptyLines() throws Exception {
        File tempFile = File.createTempFile("mappings", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("ApproveOrder=Ordering\n");
            writer.write("\n");
            writer.write("# Comment line\n");
            writer.write("CreateInvoice=Finance\n");
        }

        reasoner.loadFromFile(tempFile.getAbsolutePath());

        assertTrue(reasoner.getConfiguredTasks().contains("ApproveOrder"));
        assertTrue(reasoner.getConfiguredTasks().contains("CreateInvoice"));
    }

    public void testMultipleCapabilitiesPerTask() {
        AgentCapability orderingCap = new AgentCapability("Ordering", "procurement");
        AgentCapability financeCap = new AgentCapability("Finance", "accounting");

        StaticMappingReasoner orderingReasoner = new StaticMappingReasoner(orderingCap);
        StaticMappingReasoner financeReasoner = new StaticMappingReasoner(financeCap);

        orderingReasoner.addMapping("ApproveOrder", "Ordering,Finance");
        financeReasoner.addMapping("ApproveOrder", "Ordering,Finance");

        WorkItemRecord wir = createWorkItem("wi-1", "ApproveOrder", "case-1");

        assertTrue("Ordering agent should be eligible", orderingReasoner.isEligible(wir));
        assertTrue("Finance agent should be eligible", financeReasoner.isEligible(wir));
    }

    public void testCaseSensitiveTaskNames() {
        reasoner.addMapping("ApproveOrder", "Ordering");

        WorkItemRecord exact = createWorkItem("wi-1", "ApproveOrder", "case-1");
        WorkItemRecord lowercase = createWorkItem("wi-2", "approveorder", "case-1");

        assertTrue("Should match exact case", reasoner.isEligible(exact));
        assertFalse("Should not match different case", reasoner.isEligible(lowercase));
    }

    public void testWhitespaceHandling() {
        reasoner.addMapping("  Task1  ", "  Ordering  ,  Finance  ");

        Set<String> capabilities = reasoner.getCapabilitiesForTask("Task1");

        assertEquals(2, capabilities.size());
        assertTrue(capabilities.contains("Ordering"));
        assertTrue(capabilities.contains("Finance"));
    }

    private WorkItemRecord createWorkItem(String id, String taskName, String caseId) {
        WorkItemRecord wir = new WorkItemRecord();
        wir.setUniqueID(id);
        wir.setTaskName(taskName);
        wir.setCaseID(caseId);
        wir.setTaskID("task-" + id);
        return wir;
    }
}
