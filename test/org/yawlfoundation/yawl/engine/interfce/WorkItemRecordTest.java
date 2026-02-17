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

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Tests for WorkItemRecord and its Java 25 record components.
 * Chicago TDD style - testing real object construction and behavior.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class WorkItemRecordTest extends TestCase {

    private WorkItemRecord workItem;

    public WorkItemRecordTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        workItem = new WorkItemRecord("case123", "task456", "http://spec.uri",
                                     WorkItemRecord.statusEnabled);
    }

    public void testIdentityRecord() {
        WorkItemIdentity identity = workItem.identity();

        assertNotNull("Identity record should not be null", identity);
        assertEquals("case123", identity.caseID());
        assertEquals("task456", identity.taskID());
        assertEquals("case123:task456", identity.getID());
        assertEquals("case123:task456", workItem.getID());
    }

    public void testIdentityRecordCaching() {
        WorkItemIdentity identity1 = workItem.identity();
        WorkItemIdentity identity2 = workItem.identity();

        assertSame("Identity record should be cached", identity1, identity2);
    }

    public void testIdentityRecordInvalidation() {
        WorkItemIdentity identity1 = workItem.identity();

        workItem.setCaseID("newCase");

        WorkItemIdentity identity2 = workItem.identity();
        assertNotSame("Identity record should be invalidated after modification",
                     identity1, identity2);
        assertEquals("newCase", identity2.caseID());
        assertEquals("newCase:task456", workItem.getID());
    }

    public void testTimingRecord() {
        long now = System.currentTimeMillis();
        String nowStr = String.valueOf(now);

        workItem.setEnablementTimeMs(nowStr);
        workItem.setStartTimeMs(nowStr);

        WorkItemTiming timing = workItem.timing();

        assertNotNull("Timing record should not be null", timing);
        assertEquals(nowStr, timing.enablementTimeMs());
        assertEquals(nowStr, timing.startTimeMs());
        assertNotNull("Formatted enablement time should not be null",
                     timing.getEnablementTime());
        assertNotNull("Formatted start time should not be null",
                     timing.getStartTime());
    }

    public void testTimingRecordCaching() {
        workItem.setEnablementTimeMs("1000000");

        WorkItemTiming timing1 = workItem.timing();
        WorkItemTiming timing2 = workItem.timing();

        assertSame("Timing record should be cached", timing1, timing2);
    }

    public void testTimingRecordInvalidation() {
        workItem.setEnablementTimeMs("1000000");
        WorkItemTiming timing1 = workItem.timing();

        workItem.setStartTimeMs("2000000");

        WorkItemTiming timing2 = workItem.timing();
        assertNotSame("Timing record should be invalidated after modification",
                     timing1, timing2);
        assertEquals("2000000", timing2.startTimeMs());
    }

    public void testMetadataRecord() {
        workItem.setTaskName("TestTask");
        workItem.setDocumentation("Test documentation");
        workItem.setAllowsDynamicCreation("true");
        workItem.setRequiresManualResourcing("false");

        WorkItemMetadata metadata = workItem.metadata();

        assertNotNull("Metadata record should not be null", metadata);
        assertEquals("TestTask", metadata.taskName());
        assertEquals("Test documentation", metadata.documentation());
        assertTrue("Dynamic creation should be allowed",
                  metadata.isDynamicCreationAllowed());
        assertTrue("Should be auto task", metadata.isAutoTask());
        assertTrue("Should have documentation", metadata.hasDocumentation());
    }

    public void testMetadataRecordCaching() {
        workItem.setTaskName("TestTask");

        WorkItemMetadata metadata1 = workItem.metadata();
        WorkItemMetadata metadata2 = workItem.metadata();

        assertSame("Metadata record should be cached", metadata1, metadata2);
    }

    public void testMetadataRecordInvalidation() {
        workItem.setTaskName("FirstTask");
        WorkItemMetadata metadata1 = workItem.metadata();

        workItem.setTaskName("SecondTask");

        WorkItemMetadata metadata2 = workItem.metadata();
        assertNotSame("Metadata record should be invalidated after modification",
                     metadata1, metadata2);
        assertEquals("SecondTask", metadata2.taskName());
    }

    public void testBackwardCompatibility() {
        workItem.setSpecIdentifier("spec001");
        workItem.setSpecVersion("2.0");
        workItem.setSpecURI("http://example.com/spec");
        workItem.setCaseID("case789");
        workItem.setTaskID("task012");

        assertEquals("spec001", workItem.getSpecIdentifier());
        assertEquals("2.0", workItem.getSpecVersion());
        assertEquals("http://example.com/spec", workItem.getSpecURI());
        assertEquals("case789", workItem.getCaseID());
        assertEquals("task012", workItem.getTaskID());
        assertEquals("case789:task012", workItem.getID());
    }

    public void testRootCaseID() {
        workItem.setCaseID("1.2.3");
        assertEquals("1", workItem.getRootCaseID());

        workItem.setCaseID("42");
        assertEquals("42", workItem.getRootCaseID());
    }

    public void testNetID() {
        workItem.setCaseID("1.2.3");
        workItem.setStatus(WorkItemRecord.statusExecuting);
        assertEquals("1.2", workItem.getNetID());

        workItem.setStatus(WorkItemRecord.statusIsParent);
        assertEquals("1.2.3", workItem.getNetID());
    }

    public void testParentID() {
        workItem.setCaseID("1.2.3");
        workItem.setTaskID("task_a");
        workItem.setStatus(WorkItemRecord.statusExecuting);

        assertEquals("1.2:task_a", workItem.getParentID());

        workItem.setStatus(WorkItemRecord.statusEnabled);
        assertNull("Parent ID should be null for enabled items", workItem.getParentID());
    }

    public void testDeferredChoiceGroupMember() {
        assertFalse("Should not be deferred choice group member initially",
                   workItem.isDeferredChoiceGroupMember());

        workItem.setDeferredChoiceGroupID("group123");
        assertTrue("Should be deferred choice group member",
                  workItem.isDeferredChoiceGroupMember());
    }

    public void testAutoTask() {
        workItem.setRequiresManualResourcing("true");
        assertFalse("Should not be auto task", workItem.isAutoTask());
        assertTrue("Should require manual resourcing",
                  workItem.isManualResourcingRequired());

        workItem.setRequiresManualResourcing("false");
        assertTrue("Should be auto task", workItem.isAutoTask());
        assertFalse("Should not require manual resourcing",
                   workItem.isManualResourcingRequired());
    }

    public void testExtendedAttributes() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("key1", "value1");
        attrs.put("key2", "value2");

        workItem.setExtendedAttributes(attrs);

        Map<String, String> retrieved = workItem.getAttributeTable();
        assertEquals("value1", retrieved.get("key1"));
        assertEquals("value2", retrieved.get("key2"));
    }

    public void testTimingFormattedDates() {
        long now = System.currentTimeMillis();
        String nowStr = String.valueOf(now);

        workItem.setEnablementTimeMs(nowStr);
        workItem.setFiringTimeMs(nowStr);
        workItem.setStartTimeMs(nowStr);
        workItem.setCompletionTimeMs(nowStr);

        assertNotNull("Enablement time should be formatted",
                     workItem.getEnablementTime());
        assertNotNull("Firing time should be formatted",
                     workItem.getFiringTime());
        assertNotNull("Start time should be formatted",
                     workItem.getStartTime());
        assertNotNull("Completion time should be formatted",
                     workItem.getCompletionTime());
    }

    public void testCloning() throws CloneNotSupportedException {
        workItem.setTaskName("OriginalTask");
        workItem.setEnablementTimeMs("1000000");

        WorkItemRecord clone = workItem.clone();

        assertNotNull("Clone should not be null", clone);
        assertEquals("OriginalTask", clone.getTaskName());
        assertEquals("1000000", clone.getEnablementTimeMs());
        assertEquals(workItem.getID(), clone.getID());
    }

    public void testEqualsAndHashCode() {
        WorkItemRecord wir1 = new WorkItemRecord("case1", "task1", "uri", "Enabled");
        wir1.setUniqueID("unique1");

        WorkItemRecord wir2 = new WorkItemRecord("case1", "task1", "uri", "Enabled");
        wir2.setUniqueID("unique1");

        assertEquals("Equal work items should be equal", wir1, wir2);
        assertEquals("Equal work items should have same hash code",
                    wir1.hashCode(), wir2.hashCode());
    }

    public void testToString() {
        assertEquals("case123:task456", workItem.toString());
    }

    public void testXMLSerialization() {
        workItem.setSpecIdentifier("spec001");
        workItem.setTaskName("TestTask");
        workItem.setEnablementTimeMs("1000000");

        String xml = workItem.toXML();

        assertNotNull("XML should not be null", xml);
        assertTrue("XML should contain workItemRecord tag",
                  xml.contains("<workItemRecord"));
        assertTrue("XML should contain case ID", xml.contains("<caseid>case123</caseid>"));
        assertTrue("XML should contain task ID", xml.contains("<taskid>task456</taskid>"));
    }

    public void testIdentityRecordWithUniqueID() {
        workItem.setUniqueID("unique789");

        WorkItemIdentity identity = workItem.identity();
        assertEquals("unique789", identity.uniqueID());
    }

    public void testTimingRecordWithTimers() {
        workItem.setTimerTrigger("trigger123");
        workItem.setTimerExpiry("expiry456");

        WorkItemTiming timing = workItem.timing();
        assertEquals("trigger123", timing.timerTrigger());
        assertEquals("expiry456", timing.timerExpiry());
    }

    public void testMetadataWithCustomFormURL() {
        workItem.setCustomFormURL("/custom/form.jsp");

        WorkItemMetadata metadata = workItem.metadata();
        assertEquals("/custom/form.jsp", metadata.customFormURL());
        assertEquals("/custom/form.jsp", workItem.getCustomFormURL());
    }

    public void testMetadataWithLogPredicates() {
        workItem.setLogPredicateStarted("start predicate");
        workItem.setLogPredicateCompletion("completion predicate");

        WorkItemMetadata metadata = workItem.metadata();
        assertEquals("start predicate", metadata.logPredicateStarted());
        assertEquals("completion predicate", metadata.logPredicateCompletion());
    }
}
