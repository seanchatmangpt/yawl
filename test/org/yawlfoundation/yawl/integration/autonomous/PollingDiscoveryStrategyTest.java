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
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for PollingDiscoveryStrategy.
 * Chicago TDD style - test real polling behavior with test doubles for InterfaceB.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class PollingDiscoveryStrategyTest extends TestCase {

    private PollingDiscoveryStrategy strategy;
    private TestInterfaceB interfaceB;

    public PollingDiscoveryStrategyTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        strategy = new PollingDiscoveryStrategy();
        interfaceB = new TestInterfaceB();
    }

    public void testConstructor() {
        PollingDiscoveryStrategy s = new PollingDiscoveryStrategy();
        assertNotNull(s);
    }

    public void testDiscoverWorkItemsReturnsEmptyListWhenNoItems() throws Exception {
        interfaceB.setWorkItems(new ArrayList<>());

        List<WorkItemRecord> items = strategy.discoverWorkItems(interfaceB, "session-123");

        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    public void testDiscoverWorkItemsReturnsSingleItem() throws Exception {
        WorkItemRecord wir = createWorkItem("wi-1", "Task1", "case-1");
        List<WorkItemRecord> testItems = new ArrayList<>();
        testItems.add(wir);
        interfaceB.setWorkItems(testItems);

        List<WorkItemRecord> items = strategy.discoverWorkItems(interfaceB, "session-123");

        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("wi-1", items.get(0).getID());
        assertEquals("Task1", items.get(0).getTaskName());
    }

    public void testDiscoverWorkItemsReturnsMultipleItems() throws Exception {
        List<WorkItemRecord> testItems = new ArrayList<>();
        testItems.add(createWorkItem("wi-1", "ApproveOrder", "case-1"));
        testItems.add(createWorkItem("wi-2", "CreateInvoice", "case-1"));
        testItems.add(createWorkItem("wi-3", "ShipOrder", "case-2"));
        interfaceB.setWorkItems(testItems);

        List<WorkItemRecord> items = strategy.discoverWorkItems(interfaceB, "session-123");

        assertNotNull(items);
        assertEquals(3, items.size());
        assertEquals("wi-1", items.get(0).getID());
        assertEquals("wi-2", items.get(1).getID());
        assertEquals("wi-3", items.get(2).getID());
    }

    public void testDiscoverWorkItemsRejectsNullInterfaceB() {
        try {
            strategy.discoverWorkItems(null, "session-123");
            fail("Should reject null interfaceBClient");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("interfaceBClient is required"));
        } catch (IOException e) {
            fail("Should throw IllegalArgumentException, not IOException");
        }
    }

    public void testDiscoverWorkItemsRejectsNullSessionHandle() {
        try {
            strategy.discoverWorkItems(interfaceB, null);
            fail("Should reject null sessionHandle");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("sessionHandle is required"));
        } catch (IOException e) {
            fail("Should throw IllegalArgumentException, not IOException");
        }
    }

    public void testDiscoverWorkItemsRejectsEmptySessionHandle() {
        try {
            strategy.discoverWorkItems(interfaceB, "");
            fail("Should reject empty sessionHandle");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("sessionHandle is required"));
        } catch (IOException e) {
            fail("Should throw IllegalArgumentException, not IOException");
        }
    }

    public void testDiscoverWorkItemsThrowsIOExceptionOnNullResponse() {
        interfaceB.setWorkItems(null);

        try {
            strategy.discoverWorkItems(interfaceB, "session-123");
            fail("Should throw IOException on null response");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Failed to retrieve work items"));
        }
    }

    public void testDiscoverWorkItemsPassesSessionHandleToClient() throws Exception {
        interfaceB.setWorkItems(new ArrayList<>());

        strategy.discoverWorkItems(interfaceB, "my-session-handle");

        assertEquals("my-session-handle", interfaceB.getLastSessionHandle());
    }

    public void testDiscoverWorkItemsWithVariousWorkItemStates() throws Exception {
        List<WorkItemRecord> testItems = new ArrayList<>();

        WorkItemRecord enabled = createWorkItem("wi-1", "Task1", "case-1");
        enabled.setStatus(WorkItemRecord.statusEnabled);

        WorkItemRecord executing = createWorkItem("wi-2", "Task2", "case-1");
        executing.setStatus(WorkItemRecord.statusExecuting);

        WorkItemRecord fired = createWorkItem("wi-3", "Task3", "case-2");
        fired.setStatus(WorkItemRecord.statusFired);

        testItems.add(enabled);
        testItems.add(executing);
        testItems.add(fired);

        interfaceB.setWorkItems(testItems);

        List<WorkItemRecord> items = strategy.discoverWorkItems(interfaceB, "session-123");

        assertEquals(3, items.size());
    }

    public void testDiscoverWorkItemsCalledMultipleTimes() throws Exception {
        List<WorkItemRecord> firstBatch = new ArrayList<>();
        firstBatch.add(createWorkItem("wi-1", "Task1", "case-1"));
        interfaceB.setWorkItems(firstBatch);

        List<WorkItemRecord> items1 = strategy.discoverWorkItems(interfaceB, "session-1");
        assertEquals(1, items1.size());

        List<WorkItemRecord> secondBatch = new ArrayList<>();
        secondBatch.add(createWorkItem("wi-2", "Task2", "case-2"));
        secondBatch.add(createWorkItem("wi-3", "Task3", "case-2"));
        interfaceB.setWorkItems(secondBatch);

        List<WorkItemRecord> items2 = strategy.discoverWorkItems(interfaceB, "session-1");
        assertEquals(2, items2.size());
    }

    private WorkItemRecord createWorkItem(String id, String taskName, String caseId) {
        WorkItemRecord wir = new WorkItemRecord();
        wir.setUniqueID(id);
        wir.setTaskName(taskName);
        wir.setCaseID(caseId);
        wir.setTaskID("task-" + id);
        wir.setStatus(WorkItemRecord.statusEnabled);
        return wir;
    }

    private static class TestInterfaceB extends InterfaceB_EnvironmentBasedClient {
        private List<WorkItemRecord> workItems;
        private String lastSessionHandle;

        public TestInterfaceB() {
            super("http://test:8080/yawl/ib");
        }

        public void setWorkItems(List<WorkItemRecord> items) {
            this.workItems = items;
        }

        public String getLastSessionHandle() {
            return lastSessionHandle;
        }

        @Override
        public List<WorkItemRecord> getCompleteListOfLiveWorkItems(String sessionHandle)
                throws IOException {
            this.lastSessionHandle = sessionHandle;
            return workItems;
        }
    }
}
