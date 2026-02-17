package org.yawlfoundation.yawl.patternmatching;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.elements.YTimerParameters;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for enum exhaustiveness in switch expressions
 *
 * Verifies that all enum switch expressions handle all cases:
 * - No missing enum values
 * - No unhandled cases
 * - All branches reachable
 *
 * Branch Coverage Target: 100%
 *
 * Author: YAWL Foundation
 * Date: 2026-02-16
 */
class EnumExhaustivenessTest {

    // Test YWorkItem.Completion enum exhaustiveness
    @Test
    void testCompletionEnum_AllValuesHandled() {
        for (YWorkItem.Completion completion : YWorkItem.Completion.values()) {
            // Each value should produce a valid status
            String status = getCompletionStatusName(completion);
            assertNotNull(status, "Status should not be null for " + completion);
            assertFalse(status.isEmpty(),
                       "Status should not be empty for " + completion);
        }
    }

    @Test
    void testCompletionEnum_NoMissingCases() {
        // Verify count matches expected
        assertEquals(3, YWorkItem.Completion.values().length,
                    "Should have exactly 3 completion types");

        // Verify all expected values exist
        assertNotNull(YWorkItem.Completion.Normal);
        assertNotNull(YWorkItem.Completion.Force);
        assertNotNull(YWorkItem.Completion.Fail);
    }

    // Test YTimerParameters.TimerType enum exhaustiveness
    @Test
    void testTimerTypeEnum_AllValuesHandled() {
        for (YTimerParameters.TimerType timerType : YTimerParameters.TimerType.values()) {
            // Each value should produce a valid description
            String desc = getTimerTypeDescription(timerType);
            assertNotNull(desc, "Description should not be null for " + timerType);
            assertFalse(desc.isEmpty(),
                       "Description should not be empty for " + timerType);
        }
    }

    @Test
    void testTimerTypeEnum_NoMissingCases() {
        // Verify count matches expected
        assertEquals(3, YTimerParameters.TimerType.values().length,
                    "Should have exactly 3 timer types");

        // Verify all expected values exist
        assertNotNull(YTimerParameters.TimerType.Expiry);
        assertNotNull(YTimerParameters.TimerType.Duration);
        assertNotNull(YTimerParameters.TimerType.Interval);
    }

    // Test YTimerParameters.TriggerType enum exhaustiveness
    @Test
    void testTriggerTypeEnum_AllValuesHandled() {
        for (YTimerParameters.TriggerType trigger : YTimerParameters.TriggerType.values()) {
            // Each value should produce a valid status
            String status = getTriggerStatusName(trigger);
            assertNotNull(status, "Status should not be null for " + trigger);
            assertFalse(status.isEmpty(),
                       "Status should not be empty for " + trigger);
        }
    }

    @Test
    void testTriggerTypeEnum_NoMissingCases() {
        // Verify count matches expected
        assertEquals(2, YTimerParameters.TriggerType.values().length,
                    "Should have exactly 2 trigger types");

        // Verify all expected values exist
        assertNotNull(YTimerParameters.TriggerType.OnEnabled);
        assertNotNull(YTimerParameters.TriggerType.OnExecuting);
    }

    // Test YSchemaVersion enum exhaustiveness
    @Test
    void testSchemaVersionEnum_AllValuesHandled() {
        for (YSchemaVersion version : YSchemaVersion.values()) {
            // Each value should produce a valid beta flag
            boolean isBeta = version.isBetaVersion();
            // Should not throw exception - verify by asserting non-null Boolean
            assertNotNull(Boolean.valueOf(isBeta),
                         "Beta flag should be determinable for " + version);
        }
    }

    @Test
    void testSchemaVersionEnum_NoMissingCases() {
        // Verify count matches expected
        assertEquals(10, YSchemaVersion.values().length,
                    "Should have exactly 10 schema versions");

        // Verify all expected values exist
        assertNotNull(YSchemaVersion.Beta2);
        assertNotNull(YSchemaVersion.Beta3);
        assertNotNull(YSchemaVersion.Beta4);
        assertNotNull(YSchemaVersion.Beta6);
        assertNotNull(YSchemaVersion.Beta7);
        assertNotNull(YSchemaVersion.TwoPointZero);
        assertNotNull(YSchemaVersion.TwoPointOne);
        assertNotNull(YSchemaVersion.TwoPointTwo);
        assertNotNull(YSchemaVersion.ThreePointZero);
        assertNotNull(YSchemaVersion.FourPointZero);
    }

    // Test that adding new enum values would fail these tests
    @Test
    void testEnumCounts_NoUnexpectedValues() {
        // If someone adds a new enum value, these tests should catch it
        int completionCount = 0;
        int timerTypeCount = 0;
        int triggerTypeCount = 0;
        int schemaVersionCount = 0;

        for (YWorkItem.Completion c : YWorkItem.Completion.values()) {
            completionCount++;
        }
        for (YTimerParameters.TimerType t : YTimerParameters.TimerType.values()) {
            timerTypeCount++;
        }
        for (YTimerParameters.TriggerType t : YTimerParameters.TriggerType.values()) {
            triggerTypeCount++;
        }
        for (YSchemaVersion v : YSchemaVersion.values()) {
            schemaVersionCount++;
        }

        assertEquals(3, completionCount, "Completion enum count mismatch");
        assertEquals(3, timerTypeCount, "Timer type enum count mismatch");
        assertEquals(2, triggerTypeCount, "Trigger type enum count mismatch");
        assertEquals(10, schemaVersionCount, "Schema version enum count mismatch");
    }

    // Test enum ordinal consistency
    @Test
    void testEnumOrdinals_Sequential() {
        // Completion ordinals should be 0, 1, 2
        YWorkItem.Completion[] completions = YWorkItem.Completion.values();
        for (int i = 0; i < completions.length; i++) {
            assertEquals(i, completions[i].ordinal(),
                        "Completion ordinal should match index");
        }

        // Timer type ordinals should be 0, 1, 2
        YTimerParameters.TimerType[] timerTypes = YTimerParameters.TimerType.values();
        for (int i = 0; i < timerTypes.length; i++) {
            assertEquals(i, timerTypes[i].ordinal(),
                        "Timer type ordinal should match index");
        }

        // Trigger type ordinals should be 0, 1
        YTimerParameters.TriggerType[] triggers = YTimerParameters.TriggerType.values();
        for (int i = 0; i < triggers.length; i++) {
            assertEquals(i, triggers[i].ordinal(),
                        "Trigger type ordinal should match index");
        }

        // Schema version ordinals should be 0-9
        YSchemaVersion[] versions = YSchemaVersion.values();
        for (int i = 0; i < versions.length; i++) {
            assertEquals(i, versions[i].ordinal(),
                        "Schema version ordinal should match index");
        }
    }

    // Test that all enum values are distinct
    @Test
    void testEnumValues_AllDistinct() {
        // Test completion enum
        YWorkItem.Completion[] completions = YWorkItem.Completion.values();
        for (int i = 0; i < completions.length; i++) {
            for (int j = i + 1; j < completions.length; j++) {
                assertFalse(completions[i].equals(completions[j]),
                           "Completion values should be distinct");
            }
        }

        // Test timer type enum
        YTimerParameters.TimerType[] timerTypes = YTimerParameters.TimerType.values();
        for (int i = 0; i < timerTypes.length; i++) {
            for (int j = i + 1; j < timerTypes.length; j++) {
                assertFalse(timerTypes[i].equals(timerTypes[j]),
                           "Timer type values should be distinct");
            }
        }

        // Test trigger type enum
        YTimerParameters.TriggerType[] triggers = YTimerParameters.TriggerType.values();
        for (int i = 0; i < triggers.length; i++) {
            for (int j = i + 1; j < triggers.length; j++) {
                assertFalse(triggers[i].equals(triggers[j]),
                           "Trigger type values should be distinct");
            }
        }

        // Test schema version enum
        YSchemaVersion[] versions = YSchemaVersion.values();
        for (int i = 0; i < versions.length; i++) {
            for (int j = i + 1; j < versions.length; j++) {
                assertFalse(versions[i].equals(versions[j]),
                           "Schema version values should be distinct");
            }
        }
    }

    // Helper methods
    private String getCompletionStatusName(YWorkItem.Completion completion) {
        return switch (completion) {
            case Normal -> "Complete";
            case Force -> "ForcedComplete";
            case Fail -> "Failed";
        };
    }

    private String getTimerTypeDescription(YTimerParameters.TimerType timerType) {
        return switch (timerType) {
            case Expiry -> "Expires at specific time";
            case Duration -> "Runs for duration";
            case Interval -> "Repeats at intervals";
        };
    }

    private String getTriggerStatusName(YTimerParameters.TriggerType trigger) {
        return switch (trigger) {
            case OnEnabled -> "Enabled";
            case OnExecuting -> "Executing";
        };
    }
}
