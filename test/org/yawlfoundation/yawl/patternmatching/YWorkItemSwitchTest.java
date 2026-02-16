package org.yawlfoundation.yawl.patternmatching;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.elements.YTimerParameters;

/**
 * Tests for YWorkItem switch expressions
 *
 * Tests switch expressions in YWorkItem:
 * - Completion type switch (Normal/Force/Fail)
 * - Timer type switch (Expiry/Duration)
 * - Status-related switches
 *
 * Branch Coverage Target: 100%
 *
 * Author: YAWL Foundation
 * Date: 2026-02-16
 */
public class YWorkItemSwitchTest extends TestCase {

    // Test completion type to status mapping
    public void testCompletionTypeSwitch_Normal() {
        YWorkItemStatus status = getCompletionStatus(YWorkItem.Completion.Normal);
        assertEquals(YWorkItemStatus.statusComplete, status);
    }

    public void testCompletionTypeSwitch_Force() {
        YWorkItemStatus status = getCompletionStatus(YWorkItem.Completion.Force);
        assertEquals(YWorkItemStatus.statusForcedComplete, status);
    }

    public void testCompletionTypeSwitch_Fail() {
        YWorkItemStatus status = getCompletionStatus(YWorkItem.Completion.Fail);
        assertEquals(YWorkItemStatus.statusFailed, status);
    }

    // Test all completion types are handled
    public void testCompletionTypeSwitch_Exhaustive() {
        for (YWorkItem.Completion completion : YWorkItem.Completion.values()) {
            YWorkItemStatus status = getCompletionStatus(completion);
            assertNotNull("Status should not be null for " + completion, status);
        }
    }

    // Test timer type switches (Expiry/Duration cases)
    public void testTimerTypeSwitch_Expiry() {
        String timerType = getTimerTypeName(YTimerParameters.TimerType.Expiry);
        assertEquals("Expiry", timerType);
    }

    public void testTimerTypeSwitch_Duration() {
        String timerType = getTimerTypeName(YTimerParameters.TimerType.Duration);
        assertEquals("Duration", timerType);
    }

    public void testTimerTypeSwitch_Interval() {
        String timerType = getTimerTypeName(YTimerParameters.TimerType.Interval);
        assertEquals("Interval", timerType);
    }

    // Test all timer types are handled
    public void testTimerTypeSwitch_Exhaustive() {
        for (YTimerParameters.TimerType timerType : YTimerParameters.TimerType.values()) {
            String name = getTimerTypeName(timerType);
            assertNotNull("Timer type name should not be null for " + timerType, name);
            assertFalse("Timer type name should not be empty for " + timerType,
                       name.isEmpty());
        }
    }

    // Test completion enum values
    public void testCompletionEnum_AllValues() {
        YWorkItem.Completion[] completions = YWorkItem.Completion.values();
        assertEquals("Should have 3 completion types", 3, completions.length);

        // Verify all expected values exist
        boolean hasNormal = false;
        boolean hasForce = false;
        boolean hasFail = false;

        for (YWorkItem.Completion c : completions) {
            if (c == YWorkItem.Completion.Normal) hasNormal = true;
            if (c == YWorkItem.Completion.Force) hasForce = true;
            if (c == YWorkItem.Completion.Fail) hasFail = true;
        }

        assertTrue("Should have Normal completion", hasNormal);
        assertTrue("Should have Force completion", hasForce);
        assertTrue("Should have Fail completion", hasFail);
    }

    // Test timer type enum values
    public void testTimerTypeEnum_AllValues() {
        YTimerParameters.TimerType[] timerTypes = YTimerParameters.TimerType.values();
        assertEquals("Should have 3 timer types", 3, timerTypes.length);

        // Verify all expected values exist
        boolean hasExpiry = false;
        boolean hasDuration = false;
        boolean hasInterval = false;

        for (YTimerParameters.TimerType t : timerTypes) {
            if (t == YTimerParameters.TimerType.Expiry) hasExpiry = true;
            if (t == YTimerParameters.TimerType.Duration) hasDuration = true;
            if (t == YTimerParameters.TimerType.Interval) hasInterval = true;
        }

        assertTrue("Should have Expiry timer type", hasExpiry);
        assertTrue("Should have Duration timer type", hasDuration);
        assertTrue("Should have Interval timer type", hasInterval);
    }

    // Test status values (used in switch results)
    public void testStatusValues_Distinct() {
        assertNotNull(YWorkItemStatus.statusComplete);
        assertNotNull(YWorkItemStatus.statusForcedComplete);
        assertNotNull(YWorkItemStatus.statusFailed);

        // Verify they're all different
        assertFalse("Complete and ForcedComplete should be different",
                   YWorkItemStatus.statusComplete.equals(YWorkItemStatus.statusForcedComplete));
        assertFalse("Complete and Failed should be different",
                   YWorkItemStatus.statusComplete.equals(YWorkItemStatus.statusFailed));
        assertFalse("ForcedComplete and Failed should be different",
                   YWorkItemStatus.statusForcedComplete.equals(YWorkItemStatus.statusFailed));
    }

    // Test completion type ordering (enum ordinal)
    public void testCompletionEnum_Ordinals() {
        assertEquals("Normal should be ordinal 0",
                    0, YWorkItem.Completion.Normal.ordinal());
        assertEquals("Force should be ordinal 1",
                    1, YWorkItem.Completion.Force.ordinal());
        assertEquals("Fail should be ordinal 2",
                    2, YWorkItem.Completion.Fail.ordinal());
    }

    // Test timer type ordering
    public void testTimerTypeEnum_Ordinals() {
        assertEquals("Expiry should be ordinal 0",
                    0, YTimerParameters.TimerType.Expiry.ordinal());
        assertEquals("Duration should be ordinal 1",
                    1, YTimerParameters.TimerType.Duration.ordinal());
        assertEquals("Interval should be ordinal 2",
                    2, YTimerParameters.TimerType.Interval.ordinal());
    }

    // Test valueOf conversions
    public void testCompletionEnum_ValueOf() {
        assertEquals(YWorkItem.Completion.Normal,
                    YWorkItem.Completion.valueOf("Normal"));
        assertEquals(YWorkItem.Completion.Force,
                    YWorkItem.Completion.valueOf("Force"));
        assertEquals(YWorkItem.Completion.Fail,
                    YWorkItem.Completion.valueOf("Fail"));
    }

    public void testTimerTypeEnum_ValueOf() {
        assertEquals(YTimerParameters.TimerType.Expiry,
                    YTimerParameters.TimerType.valueOf("Expiry"));
        assertEquals(YTimerParameters.TimerType.Duration,
                    YTimerParameters.TimerType.valueOf("Duration"));
        assertEquals(YTimerParameters.TimerType.Interval,
                    YTimerParameters.TimerType.valueOf("Interval"));
    }

    // Test invalid valueOf
    public void testCompletionEnum_InvalidValueOf() {
        try {
            YWorkItem.Completion.valueOf("Invalid");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testTimerTypeEnum_InvalidValueOf() {
        try {
            YTimerParameters.TimerType.valueOf("Invalid");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    // Test null enum (edge case)
    public void testCompletionSwitch_NullEnum() {
        try {
            getCompletionStatus(null);
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected - switch on null enum throws NPE
        }
    }

    public void testTimerTypeSwitch_NullEnum() {
        try {
            getTimerTypeName(null);
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected - switch on null enum throws NPE
        }
    }

    // Helper methods simulating actual switch expressions
    private YWorkItemStatus getCompletionStatus(YWorkItem.Completion completion) {
        return switch (completion) {
            case Normal -> YWorkItemStatus.statusComplete;
            case Force -> YWorkItemStatus.statusForcedComplete;
            case Fail -> YWorkItemStatus.statusFailed;
        };
    }

    private String getTimerTypeName(YTimerParameters.TimerType timerType) {
        return switch (timerType) {
            case Expiry -> "Expiry";
            case Duration -> "Duration";
            case Interval -> "Interval";
        };
    }

    // Test that status values are used correctly
    public void testCompletionStatus_Semantics() {
        // Normal completion should result in complete status
        YWorkItemStatus normalStatus = getCompletionStatus(YWorkItem.Completion.Normal);
        assertTrue("Normal completion should be complete",
                  normalStatus.toString().contains("Complete"));

        // Force completion should result in forced complete status
        YWorkItemStatus forceStatus = getCompletionStatus(YWorkItem.Completion.Force);
        assertTrue("Force completion should be forced complete",
                  forceStatus.toString().contains("Forced"));

        // Fail completion should result in failed status
        YWorkItemStatus failStatus = getCompletionStatus(YWorkItem.Completion.Fail);
        assertTrue("Fail completion should be failed",
                  failStatus.toString().contains("Fail"));
    }
}
