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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test cases for the YWorkItemEvent record.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class YWorkItemEventTest {

    @Test
    void testConstructorWithAllParameters() {
        YWorkItem workItem = mock(YWorkItem.class);
        when(workItem.getCaseID()).thenReturn("case1");
        when(workItem.getStatus()).thenReturn(YWorkItemStatus.statusEnabled);

        YWorkItemStatus previousStatus = YWorkItemStatus.statusStarted;
        YWorkItemEvent event = new YWorkItemEvent(
            YEventType.ITEM_COMPLETED, workItem, previousStatus);

        assertEquals(YEventType.ITEM_COMPLETED, event.getEventType());
        assertEquals(workItem, event.getWorkItem());
        assertEquals(previousStatus, event.getPreviousStatus());
        assertEquals(YWorkItemStatus.statusEnabled, event.getCurrentStatus());
    }

    @Test
    void testConstructorWithoutPreviousStatus() {
        YWorkItem workItem = mock(YWorkItem.class);
        when(workItem.getCaseID()).thenReturn("case1");
        when(workItem.getStatus()).thenReturn(YWorkItemStatus.statusCompleted);

        YWorkItemEvent event = new YWorkItemEvent(
            YEventType.ITEM_CANCELLED, workItem);

        assertEquals(YEventType.ITEM_CANCELLED, event.getEventType());
        assertEquals(workItem, event.getWorkItem());
        assertNull(event.getPreviousStatus());
        assertEquals(YWorkItemStatus.statusCompleted, event.getCurrentStatus());
    }

    @Test
    void testNullParametersThrowException() {
        YWorkItem workItem = mock(YWorkItem.class);

        assertThrows(NullPointerException.class, () ->
            new YWorkItemEvent(null, workItem));
        assertThrows(NullPointerException.class, () ->
            new YWorkItemEvent(YEventType.ITEM_ENABLED, null));
    }

    @Test
    void testGetWorkItemWhenWorkItemIsNull() {
        YWorkItem workItem = mock(YWorkItem.class);
        when(workItem.getCaseID()).thenReturn("case1");

        YWorkItemEvent event = new YWorkItemEvent(
            YEventType.ITEM_STATUS_CHANGE, workItem);

        when(workItem.getStatus()).thenReturn(null);

        YWorkItem currentWorkItem = event.getWorkItem();
        assertNotNull(currentWorkItem);
        assertNull(event.getCurrentStatus());
    }

    @Test
    void testBuilder() {
        YWorkItem workItem = mock(YWorkItem.class);
        when(workItem.getCaseID()).thenReturn("case1");
        when(workItem.getStatus()).thenReturn(YWorkItemStatus.statusEnabled);

        YWorkItemEvent event = new YWorkItemEvent.Builder()
            .eventType(YEventType.ITEM_STARTED)
            .workItem(workItem)
            .previousStatus(YWorkItemStatus.statusEnabled)
            .build();

        assertEquals(YEventType.ITEM_STARTED, event.getEventType());
        assertEquals(workItem, event.getWorkItem());
        assertEquals(YWorkItemStatus.statusEnabled, event.getPreviousStatus());
    }

    @Test
    void testBuilderThrowsExceptionForMissingRequiredFields() {
        YWorkItem workItem = mock(YWorkItem.class);

        YWorkItemEvent.Builder builder = new YWorkItemEvent.Builder()
            .workItem(workItem);

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void testBuilderChain() {
        YWorkItem workItem = mock(YWorkItem.class);
        when(workItem.getCaseID()).thenReturn("case1");
        when(workItem.getStatus()).thenReturn(YWorkItemStatus.statusCompleted);

        YWorkItemEvent event = new YWorkItemEvent.Builder()
            .eventType(YEventType.ITEM_COMPLETED)
            .workItem(workItem)
            .previousStatus(YWorkItemStatus.statusStarted)
            .build();

        assertEquals(YEventType.ITEM_COMPLETED, event.getEventType());
        assertEquals(workItem, event.getWorkItem());
        assertEquals(YWorkItemStatus.statusStarted, event.getPreviousStatus());
        assertEquals(YWorkItemStatus.statusCompleted, event.getCurrentStatus());
    }

    @Test
    void testEqualsAndHashCode() {
        YWorkItem workItem1 = mock(YWorkItem.class);
        when(workItem1.getCaseID()).thenReturn("case1");
        when(workItem1.getStatus()).thenReturn(YWorkItemStatus.statusEnabled);

        YWorkItem workItem2 = mock(YWorkItem.class);
        when(workItem2.getCaseID()).thenReturn("case1");
        when(workItem2.getStatus()).thenReturn(YWorkItemStatus.statusEnabled);

        YWorkItemEvent event1 = new YWorkItemEvent(
            YEventType.ITEM_COMPLETED, workItem1, YWorkItemStatus.statusStarted);
        YWorkItemEvent event2 = new YWorkItemEvent(
            YEventType.ITEM_COMPLETED, workItem1, YWorkItemStatus.statusStarted);
        YWorkItemEvent event3 = new YWorkItemEvent(
            YEventType.ITEM_STARTED, workItem1, YWorkItemStatus.statusStarted);

        assertEquals(event1, event2);
        assertNotEquals(event1, event3);
        assertEquals(event1.hashCode(), event2.hashCode());
        assertNotEquals(event1.hashCode(), event3.hashCode());
    }

    @Test
    void testToString() {
        YWorkItem workItem = mock(YWorkItem.class);
        when(workItem.getCaseID()).thenReturn("case1");
        when(workItem.getStatus()).thenReturn(YWorkItemStatus.statusCompleted);

        YWorkItemEvent event = new YWorkItemEvent(
            YEventType.ITEM_COMPLETED, workItem, YWorkItemStatus.statusStarted);

        String toString = event.toString();
        assertTrue(toString.contains("YWorkItemEvent"));
        assertTrue(toString.contains("eventType=ITEM_COMPLETED"));
        assertTrue(toString.contains("previousStatus=STARTED"));
        assertTrue(toString.contains("caseID=case1"));
    }
}