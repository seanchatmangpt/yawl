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
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the scheduling package core data model: Constants, SchedulingException,
 * and Mapping. Converted from JUnit 3 (extends TestCase) to JUnit 5 annotations.
 *
 * <p>The original TestCalendarManager tested remote ResourceCalendarGatewayClient calls
 * which required a live scheduling service. Those integration paths are unavailable in
 * this environment. This test class covers the self-contained data model layer that
 * backs the scheduling service.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
public class TestCalendarManager {

    private static final String WORK_ITEM_ID = "WI-abc123-task1";
    private static final Integer REQUEST_KEY = 42;

    private Mapping mapping;

    @BeforeEach
    public void setUp() {
        mapping = new Mapping(WORK_ITEM_ID, REQUEST_KEY, Mapping.WORKITEM_STATUS_CHECKOUT);
    }

    // --- Constants interface ---

    @Test
    public void testResourceStatusConstantsAreDistinct() {
        String[] statuses = {
            Constants.RESOURCE_STATUS_UNCHECKED,
            Constants.RESOURCE_STATUS_UNKNOWN,
            Constants.RESOURCE_STATUS_NOTAVAILABLE,
            Constants.RESOURCE_STATUS_AVAILABLE,
            Constants.RESOURCE_STATUS_REQUESTED,
            Constants.RESOURCE_STATUS_RESERVED
        };
        for (int i = 0; i < statuses.length; i++) {
            assertNotNull(statuses[i], "Status at index " + i + " must not be null");
            for (int j = i + 1; j < statuses.length; j++) {
                assertNotEquals(statuses[i], statuses[j],
                    "Resource status constants must be distinct: index " + i + " vs " + j);
            }
        }
    }

    @Test
    public void testUtilisationTypeConstantsAreDistinct() {
        assertNotEquals(Constants.UTILISATION_TYPE_PLAN, Constants.UTILISATION_TYPE_BEGIN,
            "POU and SOU must be distinct");
        assertNotEquals(Constants.UTILISATION_TYPE_PLAN, Constants.UTILISATION_TYPE_END,
            "POU and EOU must be distinct");
        assertNotEquals(Constants.UTILISATION_TYPE_BEGIN, Constants.UTILISATION_TYPE_END,
            "SOU and EOU must be distinct");
    }

    @Test
    public void testUtilisationTypeAbbreviations() {
        assertEquals("POU", Constants.UTILISATION_TYPE_PLAN,
            "Plan utilisation type must be POU");
        assertEquals("SOU", Constants.UTILISATION_TYPE_BEGIN,
            "Begin utilisation type must be SOU");
        assertEquals("EOU", Constants.UTILISATION_TYPE_END,
            "End utilisation type must be EOU");
    }

    @Test
    public void testXmlConstantsNonNull() {
        assertNotNull(Constants.XML_RUP, "XML_RUP constant must not be null");
        assertNotNull(Constants.XML_ACTIVITY, "XML_ACTIVITY constant must not be null");
        assertNotNull(Constants.XML_RESERVATION, "XML_RESERVATION constant must not be null");
        assertNotNull(Constants.XML_RESOURCE, "XML_RESOURCE constant must not be null");
        assertNotNull(Constants.XML_CASEID, "XML_CASEID constant must not be null");
        assertNotNull(Constants.XML_FROM, "XML_FROM constant must not be null");
        assertNotNull(Constants.XML_TO, "XML_TO constant must not be null");
    }

    @Test
    public void testXmlConstantValuesMatchExpectedNames() {
        assertEquals("ResourceUtilisationPlan", Constants.XML_RUP,
            "XML_RUP must equal 'ResourceUtilisationPlan'");
        assertEquals("Activity", Constants.XML_ACTIVITY,
            "XML_ACTIVITY must equal 'Activity'");
        assertEquals("Reservation", Constants.XML_RESERVATION,
            "XML_RESERVATION must equal 'Reservation'");
        assertEquals("CaseId", Constants.XML_CASEID,
            "XML_CASEID must equal 'CaseId'");
        assertEquals("From", Constants.XML_FROM,
            "XML_FROM must equal 'From'");
        assertEquals("To", Constants.XML_TO,
            "XML_TO must equal 'To'");
    }

    @Test
    public void testCsvDelimiterIsCommaSeparated() {
        String[] parts = ("alpha" + Constants.CSV_DELIMITER + "beta").split(Constants.CSV_DELIMITER.trim());
        assertEquals(2, parts.length, "CSV_DELIMITER must produce 2 tokens from 'alpha, beta'");
    }

    // --- SchedulingException ---

    @Test
    public void testSchedulingExceptionPreservesMessage() {
        SchedulingException ex = new SchedulingException("calendar unavailable");
        assertEquals("calendar unavailable", ex.getMessage(),
            "SchedulingException must preserve the message");
    }

    @Test
    public void testSchedulingExceptionWithCause() {
        RuntimeException cause = new RuntimeException("root cause");
        SchedulingException ex = new SchedulingException("wrapped error", cause);
        assertEquals("wrapped error", ex.getMessage(),
            "SchedulingException must preserve wrapped message");
        assertSame(cause, ex.getCause(),
            "SchedulingException must preserve the cause");
    }

    @Test
    public void testSchedulingExceptionIsCheckedException() {
        SchedulingException ex = new SchedulingException("test");
        assertInstanceOf(Exception.class, ex,
            "SchedulingException must extend Exception (checked)");
        // SchedulingException extends Exception directly, not RuntimeException
        boolean isRuntime = (ex instanceof Exception)
                && ex.getClass().getSuperclass().equals(Exception.class);
        assertTrue(isRuntime,
            "SchedulingException must extend Exception directly (not RuntimeException)");
    }

    @Test
    public void testSchedulingExceptionCauseChaining() {
        IOException ioEx = new IOException("disk read error");
        SchedulingException ex = new SchedulingException("io failure during scheduling", ioEx);
        assertInstanceOf(IOException.class, ex.getCause(),
            "Cause must be preserved as IOException");
        assertEquals("disk read error", ex.getCause().getMessage(),
            "Cause message must be preserved in chain");
    }

    // --- Mapping ---

    @Test
    public void testMappingConstructorSetsFields() {
        assertEquals(WORK_ITEM_ID, mapping.getWorkItemId(),
            "Mapping must store workItemId from constructor");
        assertEquals(REQUEST_KEY, mapping.getRequestKey(),
            "Mapping must store requestKey from constructor");
        assertEquals(Mapping.WORKITEM_STATUS_CHECKOUT, mapping.getWorkItemStatus(),
            "Mapping must store workItemStatus from constructor");
    }

    @Test
    public void testMappingWorkItemStatusTransitions() {
        mapping.setWorkItemStatus(Mapping.WORKITEM_STATUS_CACHED);
        assertEquals(Mapping.WORKITEM_STATUS_CACHED, mapping.getWorkItemStatus(),
            "Mapping status must update to CACHED");

        mapping.setWorkItemStatus(Mapping.WORKITEM_STATUS_PROCESSING);
        assertEquals(Mapping.WORKITEM_STATUS_PROCESSING, mapping.getWorkItemStatus(),
            "Mapping status must update to PROCESSING");
    }

    @Test
    public void testMappingLockLifecycle() {
        assertFalse(mapping.isLocked(),
            "New Mapping must start unlocked");

        mapping.setLocked(true);
        assertTrue(mapping.isLocked(),
            "Mapping must be locked after setLocked(true)");

        mapping.setLocked(false);
        assertFalse(mapping.isLocked(),
            "Mapping must be unlocked after setLocked(false)");
    }

    @Test
    public void testMappingWorkItemIdMutable() {
        String newId = "WI-updated-789";
        mapping.setWorkItemId(newId);
        assertEquals(newId, mapping.getWorkItemId(),
            "Mapping must return updated workItemId");
    }

    @Test
    public void testMappingRequestKeyMutable() {
        Integer newKey = 99;
        mapping.setRequestKey(newKey);
        assertEquals(newKey, mapping.getRequestKey(),
            "Mapping must return updated requestKey");
    }

    @Test
    public void testMappingStatusConstantsAreDistinct() {
        String[] statuses = {
            Mapping.WORKITEM_STATUS_PARENT,
            Mapping.WORKITEM_STATUS_CHECKOUT,
            Mapping.WORKITEM_STATUS_CACHED,
            Mapping.WORKITEM_STATUS_PROCESSING
        };
        for (int i = 0; i < statuses.length; i++) {
            assertNotNull(statuses[i], "Status constant at index " + i + " must not be null");
            for (int j = i + 1; j < statuses.length; j++) {
                assertNotEquals(statuses[i], statuses[j],
                    "Mapping status constants must be distinct: index " + i + " vs " + j);
            }
        }
    }

    @Test
    public void testMappingToStringContainsAllFields() {
        String result = mapping.toString();
        assertNotNull(result, "toString must not return null");
        assertTrue(result.contains(WORK_ITEM_ID),
            "toString must contain the workItemId");
        assertTrue(result.contains(REQUEST_KEY.toString()),
            "toString must contain the requestKey");
        assertTrue(result.contains(Mapping.WORKITEM_STATUS_CHECKOUT),
            "toString must contain the workItemStatus");
    }

    @Test
    public void testMappingToStringContainsLockState() {
        mapping.setLocked(true);
        String lockedStr = mapping.toString();
        assertTrue(lockedStr.contains("true"),
            "toString must reflect locked=true");

        mapping.setLocked(false);
        String unlockedStr = mapping.toString();
        assertTrue(unlockedStr.contains("false"),
            "toString must reflect locked=false");
    }

    // --- Constants XSD datatype arrays ---

    @Test
    public void testXsdDatatypeArraysNonNull() {
        assertNotNull(Constants.XSDDatatypes_String,
            "XSDDatatypes_String array must not be null");
        assertNotNull(Constants.XSDDatatypes_DateTime,
            "XSDDatatypes_DateTime array must not be null");
        assertNotNull(Constants.XSDDatatypes_Duration,
            "XSDDatatypes_Duration array must not be null");
        assertNotNull(Constants.XSDDatatypes_Int,
            "XSDDatatypes_Int array must not be null");
        assertNotNull(Constants.XSDDatatypes_Long,
            "XSDDatatypes_Long array must not be null");
        assertNotNull(Constants.XSDDatatypes_Double,
            "XSDDatatypes_Double array must not be null");
        assertNotNull(Constants.XSDDatatypes_Boolean,
            "XSDDatatypes_Boolean array must not be null");
    }

    @Test
    public void testXsdDatatypeArraysNonEmpty() {
        assertTrue(Constants.XSDDatatypes_String.length > 0,
            "XSDDatatypes_String must be non-empty");
        assertTrue(Constants.XSDDatatypes_DateTime.length > 0,
            "XSDDatatypes_DateTime must be non-empty");
        assertTrue(Constants.XSDDatatypes_Duration.length > 0,
            "XSDDatatypes_Duration must be non-empty");
        assertTrue(Constants.XSDDatatypes_Int.length > 0,
            "XSDDatatypes_Int must be non-empty");
    }

    @Test
    public void testXsdDateTimeContainsDateTime() {
        boolean found = false;
        for (String t : Constants.XSDDatatypes_DateTime) {
            if ("dateTime".equals(t)) { found = true; break; }
        }
        assertTrue(found, "XSDDatatypes_DateTime must contain 'dateTime'");
    }

    @Test
    public void testXsdIntContainsIntAndInteger() {
        boolean hasInt = false;
        boolean hasInteger = false;
        for (String t : Constants.XSDDatatypes_Int) {
            if ("int".equals(t)) hasInt = true;
            if ("integer".equals(t)) hasInteger = true;
        }
        assertTrue(hasInt, "XSDDatatypes_Int must contain 'int'");
        assertTrue(hasInteger, "XSDDatatypes_Int must contain 'integer'");
    }

    @Test
    public void testXsdBooleanContainsBoolean() {
        boolean found = false;
        for (String t : Constants.XSDDatatypes_Boolean) {
            if ("boolean".equals(t)) { found = true; break; }
        }
        assertTrue(found, "XSDDatatypes_Boolean must contain 'boolean'");
    }

    // --- IOException used by SchedulingException cause ---

    private static class IOException extends java.io.IOException {
        IOException(String msg) { super(msg); }
    }
}
