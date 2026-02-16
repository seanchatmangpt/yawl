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

/**
 * Tests for WorkItemTiming record.
 * Chicago TDD style - testing real record behavior.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class WorkItemTimingTest extends TestCase {

    public WorkItemTimingTest(String name) {
        super(name);
    }

    public void testBasicConstruction() {
        WorkItemTiming timing = new WorkItemTiming(
            "1000", "2000", "3000", "4000", "trigger", "expiry"
        );

        assertEquals("1000", timing.enablementTimeMs());
        assertEquals("2000", timing.firingTimeMs());
        assertEquals("3000", timing.startTimeMs());
        assertEquals("4000", timing.completionTimeMs());
        assertEquals("trigger", timing.timerTrigger());
        assertEquals("expiry", timing.timerExpiry());
    }

    public void testDefaultConstruction() {
        WorkItemTiming timing = new WorkItemTiming();

        assertNull(timing.enablementTimeMs());
        assertNull(timing.firingTimeMs());
        assertNull(timing.startTimeMs());
        assertNull(timing.completionTimeMs());
        assertNull(timing.timerTrigger());
        assertNull(timing.timerExpiry());
    }

    public void testConstructionWithoutTimers() {
        WorkItemTiming timing = new WorkItemTiming("1000", "2000", "3000", "4000");

        assertEquals("1000", timing.enablementTimeMs());
        assertEquals("2000", timing.firingTimeMs());
        assertEquals("3000", timing.startTimeMs());
        assertEquals("4000", timing.completionTimeMs());
        assertNull(timing.timerTrigger());
        assertNull(timing.timerExpiry());
    }

    public void testFormattedEnablementTime() {
        long now = System.currentTimeMillis();
        WorkItemTiming timing = new WorkItemTiming(String.valueOf(now), null, null, null);

        String formatted = timing.getEnablementTime();
        assertNotNull("Formatted time should not be null", formatted);
        assertFalse("Formatted time should not be empty", formatted.isEmpty());
    }

    public void testFormattedTimeWithNullValue() {
        WorkItemTiming timing = new WorkItemTiming();

        assertNull("Formatted time should be null for null input",
                  timing.getEnablementTime());
        assertNull("Formatted time should be null for null input",
                  timing.getFiringTime());
    }

    public void testFormattedTimeWithInvalidValue() {
        WorkItemTiming timing = new WorkItemTiming("0", null, null, null);

        assertNull("Formatted time should be null for zero timestamp",
                  timing.getEnablementTime());
    }

    public void testFormattedTimeWithNegativeValue() {
        WorkItemTiming timing = new WorkItemTiming("-100", null, null, null);

        assertNull("Formatted time should be null for negative timestamp",
                  timing.getEnablementTime());
    }

    public void testWithEnablementTime() {
        WorkItemTiming timing1 = new WorkItemTiming("1000", "2000", "3000", "4000");
        WorkItemTiming timing2 = timing1.withEnablementTime("5000");

        assertEquals("5000", timing2.enablementTimeMs());
        assertEquals("2000", timing2.firingTimeMs());
        assertEquals("3000", timing2.startTimeMs());
        assertEquals("4000", timing2.completionTimeMs());
        assertEquals("1000", timing1.enablementTimeMs()); // original unchanged
    }

    public void testWithFiringTime() {
        WorkItemTiming timing1 = new WorkItemTiming("1000", "2000", "3000", "4000");
        WorkItemTiming timing2 = timing1.withFiringTime("5000");

        assertEquals("1000", timing2.enablementTimeMs());
        assertEquals("5000", timing2.firingTimeMs());
        assertEquals("3000", timing2.startTimeMs());
        assertEquals("4000", timing2.completionTimeMs());
    }

    public void testWithStartTime() {
        WorkItemTiming timing1 = new WorkItemTiming("1000", "2000", "3000", "4000");
        WorkItemTiming timing2 = timing1.withStartTime("5000");

        assertEquals("1000", timing2.enablementTimeMs());
        assertEquals("2000", timing2.firingTimeMs());
        assertEquals("5000", timing2.startTimeMs());
        assertEquals("4000", timing2.completionTimeMs());
    }

    public void testWithCompletionTime() {
        WorkItemTiming timing1 = new WorkItemTiming("1000", "2000", "3000", "4000");
        WorkItemTiming timing2 = timing1.withCompletionTime("5000");

        assertEquals("1000", timing2.enablementTimeMs());
        assertEquals("2000", timing2.firingTimeMs());
        assertEquals("3000", timing2.startTimeMs());
        assertEquals("5000", timing2.completionTimeMs());
    }

    public void testWithTimerTrigger() {
        WorkItemTiming timing1 = new WorkItemTiming("1000", "2000", "3000", "4000",
                                                    "trigger1", "expiry1");
        WorkItemTiming timing2 = timing1.withTimerTrigger("trigger2");

        assertEquals("trigger2", timing2.timerTrigger());
        assertEquals("expiry1", timing2.timerExpiry());
    }

    public void testWithTimerExpiry() {
        WorkItemTiming timing1 = new WorkItemTiming("1000", "2000", "3000", "4000",
                                                    "trigger1", "expiry1");
        WorkItemTiming timing2 = timing1.withTimerExpiry("expiry2");

        assertEquals("trigger1", timing2.timerTrigger());
        assertEquals("expiry2", timing2.timerExpiry());
    }

    public void testImmutability() {
        WorkItemTiming original = new WorkItemTiming("1000", "2000", "3000", "4000");
        WorkItemTiming modified = original.withEnablementTime("9999");

        assertEquals("1000", original.enablementTimeMs());
        assertEquals("9999", modified.enablementTimeMs());
        assertNotSame("Modified timing should be different instance", original, modified);
    }

    public void testRecordEquality() {
        WorkItemTiming t1 = new WorkItemTiming("1000", "2000", "3000", "4000", "trig", "exp");
        WorkItemTiming t2 = new WorkItemTiming("1000", "2000", "3000", "4000", "trig", "exp");

        assertEquals("Equal records should be equal", t1, t2);
        assertEquals("Equal records should have same hash code",
                    t1.hashCode(), t2.hashCode());
    }

    public void testRecordInequality() {
        WorkItemTiming t1 = new WorkItemTiming("1000", "2000", "3000", "4000");
        WorkItemTiming t2 = new WorkItemTiming("5000", "6000", "7000", "8000");

        assertFalse("Different records should not be equal", t1.equals(t2));
    }

    public void testToString() {
        WorkItemTiming timing = new WorkItemTiming("1000", "2000", "3000", "4000");

        String str = timing.toString();
        assertNotNull("toString should not be null", str);
    }

    public void testAllFormattedTimes() {
        long now = System.currentTimeMillis();
        String nowStr = String.valueOf(now);

        WorkItemTiming timing = new WorkItemTiming(nowStr, nowStr, nowStr, nowStr);

        assertNotNull("Enablement time should be formatted", timing.getEnablementTime());
        assertNotNull("Firing time should be formatted", timing.getFiringTime());
        assertNotNull("Start time should be formatted", timing.getStartTime());
        assertNotNull("Completion time should be formatted", timing.getCompletionTime());
    }
}
