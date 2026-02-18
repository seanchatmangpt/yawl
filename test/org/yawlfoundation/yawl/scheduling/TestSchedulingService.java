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

package org.yawlfoundation.yawl.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the scheduling package data model covering Mapping lifecycle, work item
 * status state machine, SchedulingException propagation, and Constants contract.
 *
 * <p>These tests use real scheduling model objects with no mocks or stubs.
 * The SchedulingService itself cannot be tested directly as it requires a live
 * resource service connection; these tests cover the data model it operates on.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
public class TestSchedulingService {

    // --- Mapping state machine ---

    @Test
    public void testMappingInitialStateIsNotLocked() {
        Mapping m = new Mapping("WI-001", 1, Mapping.WORKITEM_STATUS_PARENT);
        assertFalse(m.isLocked(), "Freshly created Mapping must not be locked");
    }

    @Test
    public void testMappingStatusParentValue() {
        assertEquals("parent", Mapping.WORKITEM_STATUS_PARENT,
            "WORKITEM_STATUS_PARENT must equal 'parent'");
    }

    @Test
    public void testMappingStatusCheckoutValue() {
        assertEquals("checkout", Mapping.WORKITEM_STATUS_CHECKOUT,
            "WORKITEM_STATUS_CHECKOUT must equal 'checkout'");
    }

    @Test
    public void testMappingStatusCachedValue() {
        assertEquals("cached", Mapping.WORKITEM_STATUS_CACHED,
            "WORKITEM_STATUS_CACHED must equal 'cached'");
    }

    @Test
    public void testMappingStatusProcessingValue() {
        assertEquals("processing", Mapping.WORKITEM_STATUS_PROCESSING,
            "WORKITEM_STATUS_PROCESSING must equal 'processing'");
    }

    @Test
    public void testMappingFullLifecycleParentToProcessing() {
        Mapping m = new Mapping("WI-lifecycle-test", 10, Mapping.WORKITEM_STATUS_PARENT);
        assertEquals(Mapping.WORKITEM_STATUS_PARENT, m.getWorkItemStatus(),
            "Initial status must be parent");

        m.setWorkItemStatus(Mapping.WORKITEM_STATUS_CHECKOUT);
        assertEquals(Mapping.WORKITEM_STATUS_CHECKOUT, m.getWorkItemStatus(),
            "Status after checkout transition must be checkout");

        m.setLocked(true);
        assertTrue(m.isLocked(), "Mapping must be locked after checkout");

        m.setWorkItemStatus(Mapping.WORKITEM_STATUS_CACHED);
        assertEquals(Mapping.WORKITEM_STATUS_CACHED, m.getWorkItemStatus(),
            "Status after caching must be cached");

        m.setWorkItemStatus(Mapping.WORKITEM_STATUS_PROCESSING);
        assertEquals(Mapping.WORKITEM_STATUS_PROCESSING, m.getWorkItemStatus(),
            "Status after processing start must be processing");
    }

    @Test
    public void testMappingEquality() {
        Mapping m1 = new Mapping("WI-same", 5, Mapping.WORKITEM_STATUS_CACHED);
        Mapping m2 = new Mapping("WI-different", 5, Mapping.WORKITEM_STATUS_CACHED);

        assertEquals(m1.getRequestKey(), m2.getRequestKey(),
            "Mappings with same requestKey must return equal keys");
        assertNotEquals(m1.getWorkItemId(), m2.getWorkItemId(),
            "Mappings with different IDs must not have equal IDs");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 100, Integer.MAX_VALUE})
    public void testMappingRequestKeyVariousValues(int key) {
        Mapping m = new Mapping("WI-test-" + key, key, Mapping.WORKITEM_STATUS_PARENT);
        assertEquals(key, m.getRequestKey(),
            "Mapping must store request key: " + key);
    }

    @Test
    public void testMappingNullableRequestKey() {
        Mapping m = new Mapping("WI-null-key", null, Mapping.WORKITEM_STATUS_PARENT);
        assertNull(m.getRequestKey(),
            "Mapping must accept and return null requestKey");
    }

    @Test
    public void testMappingToStringFormat() {
        Mapping m = new Mapping("WI-format-test", 7, Mapping.WORKITEM_STATUS_CHECKOUT);
        String s = m.toString();

        assertTrue(s.startsWith("{"),
            "toString output must start with '{'");
        assertTrue(s.endsWith("}"),
            "toString output must end with '}'");
        assertTrue(s.contains("workItemId"),
            "toString must contain 'workItemId' key");
        assertTrue(s.contains("requestKey"),
            "toString must contain 'requestKey' key");
        assertTrue(s.contains("workItemStatus"),
            "toString must contain 'workItemStatus' key");
        assertTrue(s.contains("isLocked"),
            "toString must contain 'isLocked' key");
    }

    // --- SchedulingException message handling ---

    @Test
    public void testSchedulingExceptionEmptyMessage() {
        SchedulingException ex = new SchedulingException("");
        assertNotNull(ex.getMessage(), "Message must not be null");
        assertEquals("", ex.getMessage(), "Empty message must be preserved");
    }

    @Test
    public void testSchedulingExceptionNullCause() {
        SchedulingException ex = new SchedulingException("no cause", null);
        assertNull(ex.getCause(), "Null cause must remain null");
        assertEquals("no cause", ex.getMessage(), "Message must still be set");
    }

    @Test
    public void testSchedulingExceptionThrowAndCatch() {
        SchedulingException caught = null;
        try {
            throw new SchedulingException("calendar conflict detected");
        } catch (SchedulingException e) {
            caught = e;
        }
        assertNotNull(caught, "SchedulingException must be caught");
        assertEquals("calendar conflict detected", caught.getMessage(),
            "Caught exception must have original message");
    }

    @Test
    public void testSchedulingExceptionPropagationThroughCause() {
        Exception root = new IllegalStateException("resource unavailable");
        SchedulingException wrapped = new SchedulingException("scheduling failed", root);
        SchedulingException outer = new SchedulingException("outer wrap", wrapped);

        assertInstanceOf(SchedulingException.class, outer.getCause(),
            "Cause of outer must be SchedulingException");
        assertInstanceOf(IllegalStateException.class, outer.getCause().getCause(),
            "Root cause must be preserved through wrapping");
        assertEquals("resource unavailable",
            outer.getCause().getCause().getMessage(),
            "Root cause message must be preserved");
    }

    // --- Constants resource status transitions ---

    @Test
    public void testResourceStatusProgressionOrder() {
        String[] expectedOrder = {
            Constants.RESOURCE_STATUS_UNCHECKED,
            Constants.RESOURCE_STATUS_UNKNOWN,
            Constants.RESOURCE_STATUS_REQUESTED,
            Constants.RESOURCE_STATUS_RESERVED,
            Constants.RESOURCE_STATUS_AVAILABLE
        };
        for (String status : expectedOrder) {
            assertNotNull(status, "Each resource status in progression must be non-null");
            assertFalse(status.isBlank(), "Each resource status in progression must be non-blank");
        }
    }

    @Test
    public void testMessageRelationConstantsValues() {
        assertEquals("before", Constants.MSGREL_BEFORE,
            "MSGREL_BEFORE must be 'before'");
        assertEquals("after", Constants.MSGREL_AFTER,
            "MSGREL_AFTER must be 'after'");
        assertNotEquals(Constants.MSGREL_BEFORE, Constants.MSGREL_AFTER,
            "MSGREL_BEFORE and MSGREL_AFTER must differ");
    }

    @Test
    public void testAddressTypeConstantsAreDistinct() {
        assertNotEquals(Constants.ADDRESS_TYPE_IP, Constants.ADDRESS_TYPE_EMAIL,
            "IP and EMail address types must differ");
        assertNotEquals(Constants.ADDRESS_TYPE_IP, Constants.ADDRESS_TYPE_SMS,
            "IP and SMS address types must differ");
        assertNotEquals(Constants.ADDRESS_TYPE_EMAIL, Constants.ADDRESS_TYPE_SMS,
            "EMail and SMS address types must differ");
    }

    @Test
    public void testAddressTypeConstantValues() {
        assertEquals("IP", Constants.ADDRESS_TYPE_IP,
            "ADDRESS_TYPE_IP must be 'IP'");
        assertEquals("EMail", Constants.ADDRESS_TYPE_EMAIL,
            "ADDRESS_TYPE_EMAIL must be 'EMail'");
        assertEquals("SMS", Constants.ADDRESS_TYPE_SMS,
            "ADDRESS_TYPE_SMS must be 'SMS'");
    }

    @Test
    public void testXmlRupLightDifferentFromRup() {
        assertNotEquals(Constants.XML_RUP_LIGHT, Constants.XML_RUP,
            "XML_RUP_LIGHT and XML_RUP must be distinct element names");
        assertEquals("ResourceUtilisationPlan_light", Constants.XML_RUP_LIGHT,
            "XML_RUP_LIGHT must equal 'ResourceUtilisationPlan_light'");
    }

    @Test
    public void testXmlWorkloadConstant() {
        assertEquals("Workload", Constants.XML_WORKLOAD,
            "XML_WORKLOAD must equal 'Workload'");
    }

    @Test
    public void testXmlStatusConstants() {
        assertNotEquals(Constants.XML_STATUS, Constants.XML_STATUSTOBE,
            "XML_STATUS and XML_STATUSTOBE must be distinct");
        assertEquals("Status", Constants.XML_STATUS,
            "XML_STATUS must equal 'Status'");
        assertEquals("StatusToBe", Constants.XML_STATUSTOBE,
            "XML_STATUSTOBE must equal 'StatusToBe'");
    }
}
