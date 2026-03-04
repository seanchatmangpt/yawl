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

package org.yawlfoundation.yawl.stateless.listener.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.engine.YNetData;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.util.StringUtil;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.yawlfoundation.yawl.engine.YWorkItemStatus.*;

/**
 * Test cases for the YWorkItemEvent record.
 *
 * Chicago TDD: Tests use real YWorkItem instances and real workflow execution context.
 * No mocks for domain objects.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Tag("integration")
class YWorkItemEventTest {

    private YStatelessEngine engine;
    private YWorkItem enabledWorkItem;
    private YWorkItem completedWorkItem;
    private YWorkItem cancelledWorkItem;
    private YSpecification spec;

    @BeforeEach
    void setUp() throws Exception {
        engine = new YStatelessEngine();

        // Load minimal specification for testing
        String specXML = StringUtil.inputStreamToString(
            getClass().getClassLoader().getResourceAsStream("resources/MinimalSpec.xml"));
        spec = engine.unmarshalSpecification(specXML);

        // Create work items in different states
        Map<String, String> initialData = Map.of("caseId", "test-case-123");
        YNetRunner runner = engine.launchCase(spec, "test-case-123", initialData);
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());

        // Get first work item and change its state for testing
        YWorkItem workItem = workItems.get(0);
        enabledWorkItem = workItem;

        // Change status to different states for testing
        enabledWorkItem._status = statusEnabled;
        enabledWorkItem._prevStatus = statusStarted;

        completedWorkItem = workItem;
        completedWorkItem._status = statusCompleted;
        completedWorkItem._prevStatus = statusEnabled;

        cancelledWorkItem = workItem;
        cancelledWorkItem._status = statusCancelled;
        cancelledWorkItem._prevStatus = statusStarted;
    }

    @Test
    void testConstructorWithAllParameters() {
        YWorkItemStatus previousStatus = statusStarted;
        YWorkItemEvent event = new YWorkItemEvent(
            YEventType.ITEM_COMPLETED, enabledWorkItem, previousStatus);

        assertEquals(YEventType.ITEM_COMPLETED, event.getEventType());
        assertEquals(enabledWorkItem, event.getWorkItem());
        assertEquals(previousStatus, event.getPreviousStatus());
        assertEquals(statusEnabled, event.getCurrentStatus());
    }

    @Test
    void testConstructorWithoutPreviousStatus() {
        YWorkItemEvent event = new YWorkItemEvent(
            YEventType.ITEM_CANCELLED, completedWorkItem);

        assertEquals(YEventType.ITEM_CANCELLED, event.getEventType());
        assertEquals(completedWorkItem, event.getWorkItem());
        assertNull(event.getPreviousStatus());
        assertEquals(statusCompleted, event.getCurrentStatus());
    }

    @Test
    void testNullParametersThrowException() {
        assertThrows(NullPointerException.class, () ->
            new YWorkItemEvent(null, enabledWorkItem));
        assertThrows(NullPointerException.class, () ->
            new YWorkItemEvent(YEventType.ITEM_ENABLED, null));
    }

    @Test
    void testGetWorkItemWhenWorkItemIsNull() {
        // Create a work item with null status
        YWorkItem workItemWithNullStatus = enabledWorkItem;
        workItemWithNullStatus._status = null;

        YWorkItemEvent event = new YWorkItemEvent(
            YEventType.ITEM_STATUS_CHANGE, workItemWithNullStatus);

        YWorkItem currentWorkItem = event.getWorkItem();
        assertNotNull(currentWorkItem);
        assertNull(event.getCurrentStatus());
    }

    @Test
    void testBuilder() {
        YWorkItemEvent event = new YWorkItemEvent.Builder()
            .eventType(YEventType.ITEM_STARTED)
            .workItem(enabledWorkItem)
            .previousStatus(statusEnabled)
            .build();

        assertEquals(YEventType.ITEM_STARTED, event.getEventType());
        assertEquals(enabledWorkItem, event.getWorkItem());
        assertEquals(statusEnabled, event.getPreviousStatus());
    }

    @Test
    void testBuilderThrowsExceptionForMissingRequiredFields() {
        YWorkItemEvent.Builder builder = new YWorkItemEvent.Builder()
            .workItem(enabledWorkItem);

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void testBuilderChain() {
        YWorkItemEvent event = new YWorkItemEvent.Builder()
            .eventType(YEventType.ITEM_COMPLETED)
            .workItem(completedWorkItem)
            .previousStatus(statusStarted)
            .build();

        assertEquals(YEventType.ITEM_COMPLETED, event.getEventType());
        assertEquals(completedWorkItem, event.getWorkItem());
        assertEquals(statusStarted, event.getPreviousStatus());
        assertEquals(statusCompleted, event.getCurrentStatus());
    }

    @Test
    void testEqualsAndHashCode() {
        // Use same work item for testing equality
        YWorkItemEvent event1 = new YWorkItemEvent(
            YEventType.ITEM_COMPLETED, enabledWorkItem, statusStarted);
        YWorkItemEvent event2 = new YWorkItemEvent(
            YEventType.ITEM_COMPLETED, enabledWorkItem, statusStarted);
        YWorkItemEvent event3 = new YWorkItemEvent(
            YEventType.ITEM_STARTED, enabledWorkItem, statusStarted);

        assertEquals(event1, event2);
        assertNotEquals(event1, event3);
        assertEquals(event1.hashCode(), event2.hashCode());
        assertNotEquals(event1.hashCode(), event3.hashCode());
    }

    @Test
    void testToString() {
        YWorkItemEvent event = new YWorkItemEvent(
            YEventType.ITEM_COMPLETED, completedWorkItem, statusStarted);

        String toString = event.toString();
        assertTrue(toString.contains("YWorkItemEvent"));
        assertTrue(toString.contains("eventType=ITEM_COMPLETED"));
        assertTrue(toString.contains("previousStatus=STARTED"));
        assertTrue(toString.contains("caseID=" + completedWorkItem.getCaseID()));
    }
}