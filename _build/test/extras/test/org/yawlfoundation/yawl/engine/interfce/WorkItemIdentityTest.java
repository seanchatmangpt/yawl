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

package org.yawlfoundation.yawl.engine.interfce;

import org.junit.jupiter.api.Tag;

import junit.framework.TestCase;

/**
 * Tests for WorkItemIdentity record.
 * Chicago TDD style - testing real record behavior.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Tag("unit")
public class WorkItemIdentityTest extends TestCase {

    public WorkItemIdentityTest(String name) {
        super(name);
    }

    public void testBasicConstruction() {
        WorkItemIdentity identity = new WorkItemIdentity(
            "spec001", "1.0", "http://spec.uri", "case123", "task456", "unique789"
        );

        assertEquals("spec001", identity.specIdentifier());
        assertEquals("1.0", identity.specVersion());
        assertEquals("http://spec.uri", identity.specURI());
        assertEquals("case123", identity.caseID());
        assertEquals("task456", identity.taskID());
        assertEquals("unique789", identity.uniqueID());
    }

    public void testMinimalConstruction() {
        WorkItemIdentity identity = new WorkItemIdentity("case123", "task456");

        assertEquals("case123", identity.caseID());
        assertEquals("task456", identity.taskID());
        assertEquals("0.1", identity.specVersion());
        assertNull(identity.specIdentifier());
        assertNull(identity.specURI());
        assertNull(identity.uniqueID());
    }

    public void testConstructionWithoutUniqueID() {
        WorkItemIdentity identity = new WorkItemIdentity(
            "spec001", "2.0", "http://spec.uri", "case123", "task456"
        );

        assertEquals("spec001", identity.specIdentifier());
        assertEquals("2.0", identity.specVersion());
        assertNull(identity.uniqueID());
    }

    public void testDefaultSpecVersion() {
        WorkItemIdentity identity = new WorkItemIdentity(
            null, null, "http://spec.uri", "case123", "task456", null
        );

        assertEquals("0.1", identity.specVersion());
    }

    public void testEmptySpecVersionDefaultsToZeroOne() {
        WorkItemIdentity identity = new WorkItemIdentity(
            "spec001", "", "http://spec.uri", "case123", "task456", null
        );

        assertEquals("0.1", identity.specVersion());
    }

    public void testGetID() {
        WorkItemIdentity identity = new WorkItemIdentity("case123", "task456");

        assertEquals("case123:task456", identity.getID());
    }

    public void testGetRootCaseID() {
        WorkItemIdentity identity1 = new WorkItemIdentity("1.2.3.4", "task");
        assertEquals("1", identity1.getRootCaseID());

        WorkItemIdentity identity2 = new WorkItemIdentity("42", "task");
        assertEquals("42", identity2.getRootCaseID());

        WorkItemIdentity identity3 = new WorkItemIdentity("10.20", "task");
        assertEquals("10", identity3.getRootCaseID());
    }

    public void testGetNetIDForParentStatus() {
        WorkItemIdentity identity = new WorkItemIdentity("1.2.3", "task");

        String netID = identity.getNetID(WorkItemRecord.statusIsParent);
        assertEquals("1.2.3", netID);
    }

    public void testGetNetIDForNonParentStatus() {
        WorkItemIdentity identity = new WorkItemIdentity("1.2.3", "task");

        String netID = identity.getNetID(WorkItemRecord.statusExecuting);
        assertEquals("1.2", netID);
    }

    public void testGetNetIDForRootCase() {
        WorkItemIdentity identity = new WorkItemIdentity("42", "task");

        String netID = identity.getNetID(WorkItemRecord.statusExecuting);
        assertEquals("42", netID);
    }

    public void testGetParentIDForEnabledItem() {
        WorkItemIdentity identity = new WorkItemIdentity("1.2.3", "task_a");

        String parentID = identity.getParentID(true);
        assertNull("Parent ID should be null for enabled items", parentID);
    }

    public void testGetParentIDForExecutingItem() {
        WorkItemIdentity identity = new WorkItemIdentity("1.2.3", "task_a");

        String parentID = identity.getParentID(false);
        assertEquals("1.2:task_a", parentID);
    }

    public void testGetParentIDForRootCase() {
        WorkItemIdentity identity = new WorkItemIdentity("42", "task_a");

        String parentID = identity.getParentID(false);
        assertNull("Parent ID should be null for root case", parentID);
    }

    public void testRequiredCaseID() {
        try {
            new WorkItemIdentity(null, "1.0", "uri", null, "task", null);
            fail("Should throw NullPointerException for null caseID");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("caseID"));
        }
    }

    public void testRequiredTaskID() {
        try {
            new WorkItemIdentity(null, "1.0", "uri", "case", null, null);
            fail("Should throw NullPointerException for null taskID");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("taskID"));
        }
    }

    public void testRecordEquality() {
        WorkItemIdentity id1 = new WorkItemIdentity("spec", "1.0", "uri", "case", "task", "unique");
        WorkItemIdentity id2 = new WorkItemIdentity("spec", "1.0", "uri", "case", "task", "unique");

        assertEquals("Equal records should be equal", id1, id2);
        assertEquals("Equal records should have same hash code",
                    id1.hashCode(), id2.hashCode());
    }

    public void testRecordInequality() {
        WorkItemIdentity id1 = new WorkItemIdentity("case1", "task1");
        WorkItemIdentity id2 = new WorkItemIdentity("case2", "task2");

        assertFalse("Different records should not be equal", id1.equals(id2));
    }

    public void testToString() {
        WorkItemIdentity identity = new WorkItemIdentity("case123", "task456");

        String str = identity.toString();
        assertNotNull("toString should not be null", str);
        assertTrue("toString should contain caseID", str.contains("case123"));
        assertTrue("toString should contain taskID", str.contains("task456"));
    }
}
