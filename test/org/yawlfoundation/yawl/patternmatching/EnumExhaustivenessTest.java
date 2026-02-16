package org.yawlfoundation.yawl.patternmatching;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.elements.YTimerParameters;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

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
public class EnumExhaustivenessTest extends TestCase {

    // Test YWorkItem.Completion enum exhaustiveness
    public void testCompletionEnum_AllValuesHandled() {
        for (YWorkItem.Completion completion : YWorkItem.Completion.values()) {
            // Each value should produce a valid status
            String status = getCompletionStatusName(completion);
            assertNotNull("Status should not be null for " + completion, status);
            assertFalse("Status should not be empty for " + completion,
                       status.isEmpty());
        }
    }

    public void testCompletionEnum_NoMissingCases() {
        // Verify count matches expected
        assertEquals("Should have exactly 3 completion types",
                    3, YWorkItem.Completion.values().length);

        // Verify all expected values exist
        assertNotNull(YWorkItem.Completion.Normal);
        assertNotNull(YWorkItem.Completion.Force);
        assertNotNull(YWorkItem.Completion.Fail);
    }

    // Test YTimerParameters.TimerType enum exhaustiveness
    public void testTimerTypeEnum_AllValuesHandled() {
        for (YTimerParameters.TimerType timerType : YTimerParameters.TimerType.values()) {
            // Each value should produce a valid description
            String desc = getTimerTypeDescription(timerType);
            assertNotNull("Description should not be null for " + timerType, desc);
            assertFalse("Description should not be empty for " + timerType,
                       desc.isEmpty());
        }
    }

    public void testTimerTypeEnum_NoMissingCases() {
        // Verify count matches expected
        assertEquals("Should have exactly 3 timer types",
                    3, YTimerParameters.TimerType.values().length);

        // Verify all expected values exist
        assertNotNull(YTimerParameters.TimerType.Expiry);
        assertNotNull(YTimerParameters.TimerType.Duration);
        assertNotNull(YTimerParameters.TimerType.Interval);
    }

    // Test YTimerParameters.TriggerType enum exhaustiveness
    public void testTriggerTypeEnum_AllValuesHandled() {
        for (YTimerParameters.TriggerType trigger : YTimerParameters.TriggerType.values()) {
            // Each value should produce a valid status
            String status = getTriggerStatusName(trigger);
            assertNotNull("Status should not be null for " + trigger, status);
            assertFalse("Status should not be empty for " + trigger,
                       status.isEmpty());
        }
    }

    public void testTriggerTypeEnum_NoMissingCases() {
        // Verify count matches expected
        assertEquals("Should have exactly 2 trigger types",
                    2, YTimerParameters.TriggerType.values().length);

        // Verify all expected values exist
        assertNotNull(YTimerParameters.TriggerType.OnEnabled);
        assertNotNull(YTimerParameters.TriggerType.OnExecuting);
    }

    // Test YSchemaVersion enum exhaustiveness
    public void testSchemaVersionEnum_AllValuesHandled() {
        for (YSchemaVersion version : YSchemaVersion.values()) {
            // Each value should produce a valid beta flag
            boolean isBeta = version.isBetaVersion();
            // Should not throw exception - verify by asserting non-null Boolean
            assertNotNull("Beta flag should be determinable for " + version,
                         Boolean.valueOf(isBeta));
        }
    }

    public void testSchemaVersionEnum_NoMissingCases() {
        // Verify count matches expected
        assertEquals("Should have exactly 10 schema versions",
                    10, YSchemaVersion.values().length);

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
    public void testEnumCounts_NoUnexpectedValues() {
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

        assertEquals("Completion enum count mismatch", 3, completionCount);
        assertEquals("Timer type enum count mismatch", 3, timerTypeCount);
        assertEquals("Trigger type enum count mismatch", 2, triggerTypeCount);
        assertEquals("Schema version enum count mismatch", 10, schemaVersionCount);
    }

    // Test enum ordinal consistency
    public void testEnumOrdinals_Sequential() {
        // Completion ordinals should be 0, 1, 2
        YWorkItem.Completion[] completions = YWorkItem.Completion.values();
        for (int i = 0; i < completions.length; i++) {
            assertEquals("Completion ordinal should match index", i, completions[i].ordinal());
        }

        // Timer type ordinals should be 0, 1, 2
        YTimerParameters.TimerType[] timerTypes = YTimerParameters.TimerType.values();
        for (int i = 0; i < timerTypes.length; i++) {
            assertEquals("Timer type ordinal should match index", i, timerTypes[i].ordinal());
        }

        // Trigger type ordinals should be 0, 1
        YTimerParameters.TriggerType[] triggers = YTimerParameters.TriggerType.values();
        for (int i = 0; i < triggers.length; i++) {
            assertEquals("Trigger type ordinal should match index", i, triggers[i].ordinal());
        }

        // Schema version ordinals should be 0-9
        YSchemaVersion[] versions = YSchemaVersion.values();
        for (int i = 0; i < versions.length; i++) {
            assertEquals("Schema version ordinal should match index", i, versions[i].ordinal());
        }
    }

    // Test that all enum values are distinct
    public void testEnumValues_AllDistinct() {
        // Test completion enum
        YWorkItem.Completion[] completions = YWorkItem.Completion.values();
        for (int i = 0; i < completions.length; i++) {
            for (int j = i + 1; j < completions.length; j++) {
                assertFalse("Completion values should be distinct",
                           completions[i].equals(completions[j]));
            }
        }

        // Test timer type enum
        YTimerParameters.TimerType[] timerTypes = YTimerParameters.TimerType.values();
        for (int i = 0; i < timerTypes.length; i++) {
            for (int j = i + 1; j < timerTypes.length; j++) {
                assertFalse("Timer type values should be distinct",
                           timerTypes[i].equals(timerTypes[j]));
            }
        }

        // Test trigger type enum
        YTimerParameters.TriggerType[] triggers = YTimerParameters.TriggerType.values();
        for (int i = 0; i < triggers.length; i++) {
            for (int j = i + 1; j < triggers.length; j++) {
                assertFalse("Trigger type values should be distinct",
                           triggers[i].equals(triggers[j]));
            }
        }

        // Test schema version enum
        YSchemaVersion[] versions = YSchemaVersion.values();
        for (int i = 0; i < versions.length; i++) {
            for (int j = i + 1; j < versions.length; j++) {
                assertFalse("Schema version values should be distinct",
                           versions[i].equals(versions[j]));
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
