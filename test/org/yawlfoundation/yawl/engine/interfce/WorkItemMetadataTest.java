/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.interfce;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorkItemMetadata record.
 * Chicago TDD style - testing real record behavior.
 *
 * Record field order: (attributeTable, taskName, documentation, allowsDynamicCreation,
 * requiresManualResourcing, codelet, deferredChoiceGroupID, customFormURL,
 * logPredicateStarted, logPredicateCompletion)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Tag("unit")
class WorkItemMetadataTest {

    @Test
    void testFullConstruction() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("key1", "value1");

        WorkItemMetadata metadata = new WorkItemMetadata(
            attrs, "TaskName", "Documentation", "true", "false",
            "codelet123", "groupID", "/custom.jsp",
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

    @Test
    void testDefaultConstruction() {
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

    @Test
    void testBasicConstruction() {
        WorkItemMetadata metadata = new WorkItemMetadata("MyTask", "My docs");

        assertEquals("MyTask", metadata.taskName());
        assertEquals("My docs", metadata.documentation());
        assertNull(metadata.allowsDynamicCreation());
        assertNull(metadata.requiresManualResourcing());
    }

    @Test
    void testIsDynamicCreationAllowed() {
        WorkItemMetadata m1 = new WorkItemMetadata(null, "Task", null, "true", null,
                                                   null, null, null, null, null);
        assertTrue(m1.isDynamicCreationAllowed(), "Should allow dynamic creation");

        WorkItemMetadata m2 = new WorkItemMetadata(null, "Task", null, "True", null,
                                                   null, null, null, null, null);
        assertTrue(m2.isDynamicCreationAllowed(),
                  "Should allow dynamic creation (case insensitive)");

        WorkItemMetadata m3 = new WorkItemMetadata(null, "Task", null, "false", null,
                                                   null, null, null, null, null);
        assertFalse(m3.isDynamicCreationAllowed(), "Should not allow dynamic creation");

        WorkItemMetadata m4 = new WorkItemMetadata();
        assertFalse(m4.isDynamicCreationAllowed(),
                   "Should not allow dynamic creation when null");
    }

    @Test
    void testIsAutoTask() {
        WorkItemMetadata m1 = new WorkItemMetadata(null, "Task", null, null, "false",
                                                   null, null, null, null, null);
        assertTrue(m1.isAutoTask(), "Should be auto task");

        WorkItemMetadata m2 = new WorkItemMetadata(null, "Task", null, null, "False",
                                                   null, null, null, null, null);
        assertTrue(m2.isAutoTask(), "Should be auto task (case insensitive)");

        WorkItemMetadata m3 = new WorkItemMetadata(null, "Task", null, null, "true",
                                                   null, null, null, null, null);
        assertFalse(m3.isAutoTask(), "Should not be auto task");

        WorkItemMetadata m4 = new WorkItemMetadata();
        assertFalse(m4.isAutoTask(), "Should not be auto task when null");
    }

    @Test
    void testIsManualResourcingRequired() {
        WorkItemMetadata m1 = new WorkItemMetadata(null, "Task", null, null, "false",
                                                   null, null, null, null, null);
        assertFalse(m1.isManualResourcingRequired(),
                   "Manual resourcing should not be required");

        WorkItemMetadata m2 = new WorkItemMetadata(null, "Task", null, null, "true",
                                                   null, null, null, null, null);
        assertTrue(m2.isManualResourcingRequired(),
                  "Manual resourcing should be required");
    }

    @Test
    void testIsDeferredChoiceGroupMember() {
        WorkItemMetadata m1 = new WorkItemMetadata(null, "Task", null, null, null,
                                                   null, "group123", null, null, null);
        assertTrue(m1.isDeferredChoiceGroupMember(),
                  "Should be deferred choice group member");

        WorkItemMetadata m2 = new WorkItemMetadata();
        assertFalse(m2.isDeferredChoiceGroupMember(),
                   "Should not be deferred choice group member");
    }

    @Test
    void testHasDocumentation() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task", "Some docs");
        assertTrue(m1.hasDocumentation(), "Should have documentation");

        WorkItemMetadata m2 = new WorkItemMetadata("Task", null);
        assertFalse(m2.hasDocumentation(), "Should not have documentation");
    }

    @Test
    void testWithTaskName() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task1", "Docs");
        WorkItemMetadata m2 = m1.withTaskName("Task2");

        assertEquals("Task2", m2.taskName());
        assertEquals("Docs", m2.documentation());
        assertEquals("Task1", m1.taskName()); // original unchanged
    }

    @Test
    void testWithDocumentation() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task", "Doc1");
        WorkItemMetadata m2 = m1.withDocumentation("Doc2");

        assertEquals("Task", m2.taskName());
        assertEquals("Doc2", m2.documentation());
        assertEquals("Doc1", m1.documentation()); // original unchanged
    }

    @Test
    void testWithAttributes() {
        Map<String, String> attrs1 = new HashMap<>();
        attrs1.put("k1", "v1");

        Map<String, String> attrs2 = new HashMap<>();
        attrs2.put("k2", "v2");

        WorkItemMetadata m1 = new WorkItemMetadata(attrs1, "Task", null, null, null,
                                                   null, null, null, null, null);
        WorkItemMetadata m2 = m1.withAttributes(attrs2);

        assertEquals(attrs2, m2.attributeTable());
        assertEquals("v2", m2.attributeTable().get("k2"));
        assertEquals(attrs1, m1.attributeTable()); // original unchanged
    }

    @Test
    void testWithCustomFormURL() {
        WorkItemMetadata m1 = new WorkItemMetadata(null, "Task", null, null, null,
                                                   null, null, "/form1.jsp", null, null);
        WorkItemMetadata m2 = m1.withCustomFormURL("/form2.jsp");

        assertEquals("/form2.jsp", m2.customFormURL());
        assertEquals("/form1.jsp", m1.customFormURL()); // original unchanged
    }

    @Test
    void testWithLogPredicates() {
        WorkItemMetadata m1 = new WorkItemMetadata(null, "Task", null, null, null,
                                                   null, null, null, "pred1", "pred2");
        WorkItemMetadata m2 = m1.withLogPredicates("newPred1", "newPred2");

        assertEquals("newPred1", m2.logPredicateStarted());
        assertEquals("newPred2", m2.logPredicateCompletion());
        assertEquals("pred1", m1.logPredicateStarted()); // original unchanged
    }

    @Test
    void testImmutability() {
        WorkItemMetadata original = new WorkItemMetadata("Task1", "Doc1");
        WorkItemMetadata modified = original.withTaskName("Task2");

        assertEquals("Task1", original.taskName());
        assertEquals("Task2", modified.taskName());
        assertNotSame(original, modified, "Modified metadata should be different instance");
    }

    @Test
    void testRecordEquality() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("k", "v");

        WorkItemMetadata m1 = new WorkItemMetadata(attrs, "Task", "Doc", "true", "false",
                                                   "code", "group", "/form.jsp",
                                                   "pred1", "pred2");
        WorkItemMetadata m2 = new WorkItemMetadata(attrs, "Task", "Doc", "true", "false",
                                                   "code", "group", "/form.jsp",
                                                   "pred1", "pred2");

        assertEquals(m1, m2, "Equal records should be equal");
        assertEquals(m1.hashCode(), m2.hashCode(),
                    "Equal records should have same hash code");
    }

    @Test
    void testRecordInequality() {
        WorkItemMetadata m1 = new WorkItemMetadata("Task1", "Doc1");
        WorkItemMetadata m2 = new WorkItemMetadata("Task2", "Doc2");

        assertNotEquals(m1, m2, "Different records should not be equal");
    }

    @Test
    void testToString() {
        WorkItemMetadata metadata = new WorkItemMetadata("MyTask", "MyDocs");

        String str = metadata.toString();
        assertNotNull(str, "toString should not be null");
        assertTrue(str.contains("MyTask"), "toString should contain taskName");
    }
}
