/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comprehensive tests for YWorkItemStatus enum following Chicago TDD methodology.
 *
 * <p>Tests the work item lifecycle status values and their string conversions.</p>
 *
 * @author YAWL Test Suite
 * @see YWorkItemStatus
 */
@DisplayName("YWorkItemStatus Enum Tests")
@Tag("unit")
class TestYWorkItemStatus {

    // ========================================================================
    // Enum Values Tests
    // ========================================================================

    @Nested
    @DisplayName("Enum Values Tests")
    class EnumValuesTests {

        @Test
        @DisplayName("Enum has exactly 13 values")
        void enumHasExactly13Values() {
            YWorkItemStatus[] values = YWorkItemStatus.values();
            assertEquals(13, values.length,
                    "YWorkItemStatus should have exactly 13 values");
        }

        @ParameterizedTest
        @EnumSource(YWorkItemStatus.class)
        @DisplayName("All enum values are non-null")
        void allEnumValuesAreNonNull(YWorkItemStatus status) {
            assertNotNull(status, "Enum value should not be null");
        }

        @Test
        @DisplayName("Enum contains all expected status values")
        void enumContainsAllExpectedStatusValues() {
            assertNotNull(YWorkItemStatus.statusEnabled);
            assertNotNull(YWorkItemStatus.statusFired);
            assertNotNull(YWorkItemStatus.statusExecuting);
            assertNotNull(YWorkItemStatus.statusComplete);
            assertNotNull(YWorkItemStatus.statusIsParent);
            assertNotNull(YWorkItemStatus.statusDeadlocked);
            assertNotNull(YWorkItemStatus.statusDeleted);
            assertNotNull(YWorkItemStatus.statusWithdrawn);
            assertNotNull(YWorkItemStatus.statusForcedComplete);
            assertNotNull(YWorkItemStatus.statusFailed);
            assertNotNull(YWorkItemStatus.statusSuspended);
            assertNotNull(YWorkItemStatus.statusCancelledByCase);
            assertNotNull(YWorkItemStatus.statusDiscarded);
        }
    }

    // ========================================================================
    // ToString Tests
    // ========================================================================

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("statusEnabled toString is 'Enabled'")
        void statusEnabledToStringIsEnabled() {
            assertEquals("Enabled", YWorkItemStatus.statusEnabled.toString());
        }

        @Test
        @DisplayName("statusFired toString is 'Fired'")
        void statusFiredToStringIsFired() {
            assertEquals("Fired", YWorkItemStatus.statusFired.toString());
        }

        @Test
        @DisplayName("statusExecuting toString is 'Executing'")
        void statusExecutingToStringIsExecuting() {
            assertEquals("Executing", YWorkItemStatus.statusExecuting.toString());
        }

        @Test
        @DisplayName("statusComplete toString is 'Complete'")
        void statusCompleteToStringIsComplete() {
            assertEquals("Complete", YWorkItemStatus.statusComplete.toString());
        }

        @Test
        @DisplayName("statusIsParent toString is 'Is parent'")
        void statusIsParentToStringIsIsParent() {
            assertEquals("Is parent", YWorkItemStatus.statusIsParent.toString());
        }

        @Test
        @DisplayName("statusDeadlocked toString is 'Deadlocked'")
        void statusDeadlockedToStringIsDeadlocked() {
            assertEquals("Deadlocked", YWorkItemStatus.statusDeadlocked.toString());
        }

        @Test
        @DisplayName("statusDeleted toString is 'Cancelled'")
        void statusDeletedToStringIsCancelled() {
            assertEquals("Cancelled", YWorkItemStatus.statusDeleted.toString());
        }

        @Test
        @DisplayName("statusWithdrawn toString is 'Withdrawn'")
        void statusWithdrawnToStringIsWithdrawn() {
            assertEquals("Withdrawn", YWorkItemStatus.statusWithdrawn.toString());
        }

        @Test
        @DisplayName("statusForcedComplete toString is 'ForcedComplete'")
        void statusForcedCompleteToStringIsForcedComplete() {
            assertEquals("ForcedComplete", YWorkItemStatus.statusForcedComplete.toString());
        }

        @Test
        @DisplayName("statusFailed toString is 'Failed'")
        void statusFailedToStringIsFailed() {
            assertEquals("Failed", YWorkItemStatus.statusFailed.toString());
        }

        @Test
        @DisplayName("statusSuspended toString is 'Suspended'")
        void statusSuspendedToStringIsSuspended() {
            assertEquals("Suspended", YWorkItemStatus.statusSuspended.toString());
        }

        @Test
        @DisplayName("statusCancelledByCase toString is 'CancelledByCase'")
        void statusCancelledByCaseToStringIsCancelledByCase() {
            assertEquals("CancelledByCase", YWorkItemStatus.statusCancelledByCase.toString());
        }

        @Test
        @DisplayName("statusDiscarded toString is 'Discarded'")
        void statusDiscardedToStringIsDiscarded() {
            assertEquals("Discarded", YWorkItemStatus.statusDiscarded.toString());
        }
    }

    // ========================================================================
    // FromString Tests
    // ========================================================================

    @Nested
    @DisplayName("FromString Tests")
    class FromStringTests {

        @Test
        @DisplayName("fromString returns correct enum for valid strings")
        void fromStringReturnsCorrectEnumForValidStrings() {
            assertEquals(YWorkItemStatus.statusEnabled, YWorkItemStatus.fromString("Enabled"));
            assertEquals(YWorkItemStatus.statusFired, YWorkItemStatus.fromString("Fired"));
            assertEquals(YWorkItemStatus.statusExecuting, YWorkItemStatus.fromString("Executing"));
            assertEquals(YWorkItemStatus.statusComplete, YWorkItemStatus.fromString("Complete"));
            assertEquals(YWorkItemStatus.statusIsParent, YWorkItemStatus.fromString("Is parent"));
            assertEquals(YWorkItemStatus.statusDeadlocked, YWorkItemStatus.fromString("Deadlocked"));
            assertEquals(YWorkItemStatus.statusDeleted, YWorkItemStatus.fromString("Cancelled"));
            assertEquals(YWorkItemStatus.statusWithdrawn, YWorkItemStatus.fromString("Withdrawn"));
            assertEquals(YWorkItemStatus.statusForcedComplete, YWorkItemStatus.fromString("ForcedComplete"));
            assertEquals(YWorkItemStatus.statusFailed, YWorkItemStatus.fromString("Failed"));
            assertEquals(YWorkItemStatus.statusSuspended, YWorkItemStatus.fromString("Suspended"));
            assertEquals(YWorkItemStatus.statusCancelledByCase, YWorkItemStatus.fromString("CancelledByCase"));
            assertEquals(YWorkItemStatus.statusDiscarded, YWorkItemStatus.fromString("Discarded"));
        }

        @Test
        @DisplayName("fromString returns null for unknown strings")
        void fromStringReturnsNullForUnknownStrings() {
            assertNull(YWorkItemStatus.fromString("Unknown"),
                    "fromString should return null for unknown string");
        }

        @Test
        @DisplayName("fromString returns null for null input")
        void fromStringReturnsNullForNullInput() {
            assertNull(YWorkItemStatus.fromString(null),
                    "fromString should return null for null input");
        }

        @Test
        @DisplayName("fromString is case sensitive")
        void fromStringIsCaseSensitive() {
            assertNull(YWorkItemStatus.fromString("enabled"),
                    "fromString should be case sensitive");
            assertNull(YWorkItemStatus.fromString("ENABLED"),
                    "fromString should be case sensitive");
        }

        @Test
        @DisplayName("fromString is consistent with toString")
        void fromStringIsConsistentWithToString() {
            for (YWorkItemStatus status : YWorkItemStatus.values()) {
                assertEquals(status, YWorkItemStatus.fromString(status.toString()),
                        "fromString(toString(x)) should equal x for " + status.name());
            }
        }
    }

    // ========================================================================
    // Roundtrip Tests
    // ========================================================================

    @Nested
    @DisplayName("Roundtrip Tests")
    class RoundtripTests {

        @ParameterizedTest
        @EnumSource(YWorkItemStatus.class)
        @DisplayName("toString then fromString roundtrip")
        void toStringThenFromStringRoundtrip(YWorkItemStatus status) {
            String str = status.toString();
            YWorkItemStatus result = YWorkItemStatus.fromString(str);
            assertEquals(status, result,
                    "Roundtrip should return original status for " + status.name());
        }
    }

    // ========================================================================
    // Equality Tests
    // ========================================================================

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Enum values are singletons")
        void enumValuesAreSingletons() {
            YWorkItemStatus enabled1 = YWorkItemStatus.statusEnabled;
            YWorkItemStatus enabled2 = YWorkItemStatus.statusEnabled;

            assertSame(enabled1, enabled2, "Enum values should be the same instance");
        }

        @Test
        @DisplayName("Different enum values are not equal")
        void differentEnumValuesAreNotEqual() {
            assertNotEquals(YWorkItemStatus.statusEnabled, YWorkItemStatus.statusFired);
            assertNotEquals(YWorkItemStatus.statusExecuting, YWorkItemStatus.statusComplete);
            assertNotEquals(YWorkItemStatus.statusSuspended, YWorkItemStatus.statusFailed);
        }
    }

    // ========================================================================
    // Switch Usage Tests
    // ========================================================================

    @Nested
    @DisplayName("Switch Usage Tests")
    class SwitchUsageTests {

        @Test
        @DisplayName("Can use in enhanced switch statement")
        void canUseInEnhancedSwitchStatement() {
            for (YWorkItemStatus status : YWorkItemStatus.values()) {
                String category = switch (status) {
                    case statusEnabled, statusFired, statusExecuting -> "live";
                    case statusComplete, statusForcedComplete -> "completed";
                    case statusFailed -> "failed";
                    case statusSuspended -> "suspended";
                    case statusDeadlocked -> "deadlocked";
                    case statusIsParent -> "parent";
                    case statusDeleted, statusWithdrawn, statusCancelledByCase, statusDiscarded -> "terminated";
                };
                assertNotNull(category, "Switch should return non-null for " + status);
            }
        }

        @Test
        @DisplayName("Switch exhaustiveness is enforced")
        void switchExhaustivenessIsEnforced() {
            // This test verifies that the switch covers all cases
            // If a new status is added and switch is not updated, compilation will fail
            int count = 0;
            for (YWorkItemStatus status : YWorkItemStatus.values()) {
                switch (status) {
                    case statusEnabled -> count++;
                    case statusFired -> count++;
                    case statusExecuting -> count++;
                    case statusComplete -> count++;
                    case statusIsParent -> count++;
                    case statusDeadlocked -> count++;
                    case statusDeleted -> count++;
                    case statusWithdrawn -> count++;
                    case statusForcedComplete -> count++;
                    case statusFailed -> count++;
                    case statusSuspended -> count++;
                    case statusCancelledByCase -> count++;
                    case statusDiscarded -> count++;
                }
            }
            assertEquals(13, count, "Should have counted all 13 statuses");
        }
    }

    // ========================================================================
    // Ordinal Tests
    // ========================================================================

    @Nested
    @DisplayName("Ordinal Tests")
    class OrdinalTests {

        @Test
        @DisplayName("Ordinals are unique")
        void ordinalsAreUnique() {
            java.util.Set<Integer> ordinals = new java.util.HashSet<>();
            for (YWorkItemStatus status : YWorkItemStatus.values()) {
                assertTrue(ordinals.add(status.ordinal()),
                        "Ordinal should be unique for " + status.name());
            }
        }

        @Test
        @DisplayName("Ordinals are sequential starting from 0")
        void ordinalsAreSequentialStartingFrom0() {
            YWorkItemStatus[] values = YWorkItemStatus.values();
            for (int i = 0; i < values.length; i++) {
                assertEquals(i, values[i].ordinal(),
                        "Ordinal should match position for " + values[i].name());
            }
        }
    }
}
