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

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for WorkItemMetadata record.
 * Chicago TDD style - testing real record behavior.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class WorkItemMetadataTest extends TestCase {

    public WorkItemMetadataTest(String name) {
        super(name);
    }

    public void testFullConstruction() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("key1", "value1");

        WorkItemMetadata metadata = new WorkItemMetadata(
            "TaskName", "Documentation", "true", "false",
            "codelet123", "groupID", attrs, "/custom.jsp",
            "startPredicate", "completionPredicate"
        );

        assertEquals("TaskName", metadata.taskName());
        assertEquals("Documentation", metadata.documentation());
        assertEquals("true", metadata.allowsDynamicCreation());
        assertEquals("false", metadata.requiresManualResourcing());
        assertEquals("codelet123", metadata.codelet());
        assertEquals("groupID", metadata.deferredChoiceGroupID());
        assertEquals(attrs, metadata.attributeTable());
        assertEquals("/custom.jsp", metadata.customFormURL());
        assertEquals("startPredicate", metadata.logPredicateStarted());
        assertEquals("completionPredicate", metadata.logPredicateCompletion());
    }

    public void testDefaultConstruction() {
        WorkItemMetadata metadata = new WorkItemMetadata();

        assertNull(metadata.taskName());
        assertNull(metadata.documentation());
        assertNull(metadata.allowsDynamicCreation());
        assertNull(metadata.requiresManualResourcing());
        assertNull(metadata.codelet());
        assertNull(metadata.deferredChoiceGroupID());
        assertNull(metadata.attributeTable());
        assertNull(metadata.customFormURL());
        assertNull(metadata.logPredicateStarted());
        assertNull(metadata.logPredicateCompletion());
    }

    public void testBasicConstruction() {
        WorkItemMetadata metadata = new WorkItemMetadata("MyTask", "My docs");

        assertEquals("MyTask", metadata.taskName());
        assertEquals("My docs", metadata.documentation());
        assertNull(metadata.allowsDynamicCreation());
        assertNull(metadata.requiresManualResourcing());
    }

    public void testIsDynamicCreationAllowed() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task", null, "true", null,
                                                   null, null, null, null, null, null);
        assertTrue("Should allow dynamic creation", m1.isDynamicCreationAllowed());

        WorkItemMetadata m2 = new WorkItemMetadata("Task", null, "True", null,
                                                   null, null, null, null, null, null);
        assertTrue("Should allow dynamic creation (case insensitive)",
                  m2.isDynamicCreationAllowed());

        WorkItemMetadata m3 = new WorkItemMetadata("Task", null, "false", null,
                                                   null, null, null, null, null, null);
        assertFalse("Should not allow dynamic creation", m3.isDynamicCreationAllowed());

        WorkItemMetadata m4 = new WorkItemMetadata();
        assertFalse("Should not allow dynamic creation when null",
                   m4.isDynamicCreationAllowed());
    }

    public void testIsAutoTask() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task", null, null, "false",
                                                   null, null, null, null, null, null);
        assertTrue("Should be auto task", m1.isAutoTask());

        WorkItemMetadata m2 = new WorkItemMetadata("Task", null, null, "False",
                                                   null, null, null, null, null, null);
        assertTrue("Should be auto task (case insensitive)", m2.isAutoTask());

        WorkItemMetadata m3 = new WorkItemMetadata("Task", null, null, "true",
                                                   null, null, null, null, null, null);
        assertFalse("Should not be auto task", m3.isAutoTask());

        WorkItemMetadata m4 = new WorkItemMetadata();
        assertFalse("Should not be auto task when null", m4.isAutoTask());
    }

    public void testIsManualResourcingRequired() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task", null, null, "false",
                                                   null, null, null, null, null, null);
        assertFalse("Manual resourcing should not be required",
                   m1.isManualResourcingRequired());

        WorkItemMetadata m2 = new WorkItemMetadata("Task", null, null, "true",
                                                   null, null, null, null, null, null);
        assertTrue("Manual resourcing should be required",
                  m2.isManualResourcingRequired());
    }

    public void testIsDeferredChoiceGroupMember() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task", null, null, null,
                                                   null, "group123", null, null, null, null);
        assertTrue("Should be deferred choice group member",
                  m1.isDeferredChoiceGroupMember());

        WorkItemMetadata m2 = new WorkItemMetadata();
        assertFalse("Should not be deferred choice group member",
                   m2.isDeferredChoiceGroupMember());
    }

    public void testHasDocumentation() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task", "Some docs");
        assertTrue("Should have documentation", m1.hasDocumentation());

        WorkItemMetadata m2 = new WorkItemMetadata("Task", null);
        assertFalse("Should not have documentation", m2.hasDocumentation());
    }

    public void testWithTaskName() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task1", "Docs");
        WorkItemMetadata m2 = m1.withTaskName("Task2");

        assertEquals("Task2", m2.taskName());
        assertEquals("Docs", m2.documentation());
        assertEquals("Task1", m1.taskName()); // original unchanged
    }

    public void testWithDocumentation() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task", "Doc1");
        WorkItemMetadata m2 = m1.withDocumentation("Doc2");

        assertEquals("Task", m2.taskName());
        assertEquals("Doc2", m2.documentation());
        assertEquals("Doc1", m1.documentation()); // original unchanged
    }

    public void testWithAttributes() {
        Map<String, String> attrs1 = new HashMap<>();
        attrs1.put("k1", "v1");

        Map<String, String> attrs2 = new HashMap<>();
        attrs2.put("k2", "v2");

        WorkItemMetadata m1 = new WorkItemMetadata("Task", null, null, null,
                                                   null, null, attrs1, null, null, null);
        WorkItemMetadata m2 = m1.withAttributes(attrs2);

        assertEquals(attrs2, m2.attributeTable());
        assertEquals("v2", m2.attributeTable().get("k2"));
        assertEquals(attrs1, m1.attributeTable()); // original unchanged
    }

    public void testWithCustomFormURL() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task", null, null, null,
                                                   null, null, null, "/form1.jsp", null, null);
        WorkItemMetadata m2 = m1.withCustomFormURL("/form2.jsp");

        assertEquals("/form2.jsp", m2.customFormURL());
        assertEquals("/form1.jsp", m1.customFormURL()); // original unchanged
    }

    public void testWithLogPredicates() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task", null, null, null,
                                                   null, null, null, null, "pred1", "pred2");
        WorkItemMetadata m2 = m1.withLogPredicates("newPred1", "newPred2");

        assertEquals("newPred1", m2.logPredicateStarted());
        assertEquals("newPred2", m2.logPredicateCompletion());
        assertEquals("pred1", m1.logPredicateStarted()); // original unchanged
    }

    public void testImmutability() {
        WorkItemMetadata original = new WorkItemMetadata("Task1", "Doc1");
        WorkItemMetadata modified = original.withTaskName("Task2");

        assertEquals("Task1", original.taskName());
        assertEquals("Task2", modified.taskName());
        assertNotSame("Modified metadata should be different instance",
                     original, modified);
    }

    public void testRecordEquality() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("k", "v");

        WorkItemMetadata m1 = new WorkItemMetadata("Task", "Doc", "true", "false",
                                                   "code", "group", attrs, "/form.jsp",
                                                   "pred1", "pred2");
        WorkItemMetadata m2 = new WorkItemMetadata("Task", "Doc", "true", "false",
                                                   "code", "group", attrs, "/form.jsp",
                                                   "pred1", "pred2");

        assertEquals("Equal records should be equal", m1, m2);
        assertEquals("Equal records should have same hash code",
                    m1.hashCode(), m2.hashCode());
    }

    public void testRecordInequality() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task1", "Doc1");
        WorkItemMetadata m2 = new WorkItemMetadata("Task2", "Doc2");

        assertFalse("Different records should not be equal", m1.equals(m2));
    }

    public void testToString() {
        WorkItemMetadata metadata = new WorkItemMetadata("MyTask", "MyDocs");

        String str = metadata.toString();
        assertNotNull("toString should not be null", str);
        assertTrue("toString should contain taskName", str.contains("MyTask"));
    }
}
