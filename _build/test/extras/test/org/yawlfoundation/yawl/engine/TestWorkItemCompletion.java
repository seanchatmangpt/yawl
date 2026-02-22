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
 * Comprehensive tests for WorkItemCompletion enum following Chicago TDD methodology.
 *
 * <p>Tests the work item completion types: Normal, Force, Fail, and Invalid.</p>
 *
 * @author YAWL Test Suite
 * @see WorkItemCompletion
 */
@DisplayName("WorkItemCompletion Enum Tests")
@Tag("unit")
class TestWorkItemCompletion {

    // ========================================================================
    // Enum Values Tests
    // ========================================================================

    @Nested
    @DisplayName("Enum Values Tests")
    class EnumValuesTests {

        @Test
        @DisplayName("Enum has exactly 4 values")
        void enumHasExactly4Values() {
            WorkItemCompletion[] values = WorkItemCompletion.values();
            assertEquals(4, values.length,
                    "WorkItemCompletion should have exactly 4 values: Normal, Force, Fail, Invalid");
        }

        @ParameterizedTest
        @EnumSource(WorkItemCompletion.class)
        @DisplayName("All enum values are non-null")
        void allEnumValuesAreNonNull(WorkItemCompletion completion) {
            assertNotNull(completion, "Enum value should not be null");
        }

        @Test
        @DisplayName("Enum contains Normal completion")
        void enumContainsNormalCompletion() {
            assertNotNull(WorkItemCompletion.Normal, "Normal should be defined");
        }

        @Test
        @DisplayName("Enum contains Force completion")
        void enumContainsForceCompletion() {
            assertNotNull(WorkItemCompletion.Force, "Force should be defined");
        }

        @Test
        @DisplayName("Enum contains Fail completion")
        void enumContainsFailCompletion() {
            assertNotNull(WorkItemCompletion.Fail, "Fail should be defined");
        }

        @Test
        @DisplayName("Enum contains Invalid completion")
        void enumContainsInvalidCompletion() {
            assertNotNull(WorkItemCompletion.Invalid, "Invalid should be defined");
        }
    }

    // ========================================================================
    // fromInt Method Tests
    // ========================================================================

    @Nested
    @DisplayName("fromInt Method Tests")
    class FromIntMethodTests {

        @Test
        @DisplayName("fromInt(0) returns Normal")
        void fromInt0ReturnsNormal() {
            assertEquals(WorkItemCompletion.Normal, WorkItemCompletion.fromInt(0),
                    "fromInt(0) should return Normal");
        }

        @Test
        @DisplayName("fromInt(1) returns Force")
        void fromInt1ReturnsForce() {
            assertEquals(WorkItemCompletion.Force, WorkItemCompletion.fromInt(1),
                    "fromInt(1) should return Force");
        }

        @Test
        @DisplayName("fromInt(2) returns Fail")
        void fromInt2ReturnsFail() {
            assertEquals(WorkItemCompletion.Fail, WorkItemCompletion.fromInt(2),
                    "fromInt(2) should return Fail");
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 3, 100, Integer.MAX_VALUE, Integer.MIN_VALUE})
        @DisplayName("fromInt returns Invalid for unknown values")
        void fromIntReturnsInvalidForUnknownValues(int value) {
            assertEquals(WorkItemCompletion.Invalid, WorkItemCompletion.fromInt(value),
                    "fromInt should return Invalid for unknown value: " + value);
        }

        @Test
        @DisplayName("fromInt is consistent for valid values")
        void fromIntIsConsistentForValidValues() {
            assertEquals(WorkItemCompletion.Normal, WorkItemCompletion.fromInt(0),
                    "First call should return Normal");
            assertEquals(WorkItemCompletion.Normal, WorkItemCompletion.fromInt(0),
                    "Second call should also return Normal");
        }
    }

    // ========================================================================
    // Ordinal Tests
    // ========================================================================

    @Nested
    @DisplayName("Ordinal Tests")
    class OrdinalTests {

        @Test
        @DisplayName("Normal has ordinal 0")
        void normalHasOrdinal0() {
            assertEquals(0, WorkItemCompletion.Normal.ordinal(),
                    "Normal should have ordinal 0");
        }

        @Test
        @DisplayName("Force has ordinal 1")
        void forceHasOrdinal1() {
            assertEquals(1, WorkItemCompletion.Force.ordinal(),
                    "Force should have ordinal 1");
        }

        @Test
        @DisplayName("Fail has ordinal 2")
        void failHasOrdinal2() {
            assertEquals(2, WorkItemCompletion.Fail.ordinal(),
                    "Fail should have ordinal 2");
        }

        @Test
        @DisplayName("Invalid has ordinal 3")
        void invalidHasOrdinal3() {
            assertEquals(3, WorkItemCompletion.Invalid.ordinal(),
                    "Invalid should have ordinal 3");
        }
    }

    // ========================================================================
    // ValueOf Tests
    // ========================================================================

    @Nested
    @DisplayName("ValueOf Tests")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf returns correct enum for valid name")
        void valueOfReturnsCorrectEnumForValidName() {
            assertEquals(WorkItemCompletion.Normal, WorkItemCompletion.valueOf("Normal"));
            assertEquals(WorkItemCompletion.Force, WorkItemCompletion.valueOf("Force"));
            assertEquals(WorkItemCompletion.Fail, WorkItemCompletion.valueOf("Fail"));
            assertEquals(WorkItemCompletion.Invalid, WorkItemCompletion.valueOf("Invalid"));
        }

        @Test
        @DisplayName("valueOf throws for invalid name")
        void valueOfThrowsForInvalidName() {
            assertThrows(IllegalArgumentException.class, () -> {
                WorkItemCompletion.valueOf("UNKNOWN");
            }, "valueOf should throw for unknown name");
        }

        @Test
        @DisplayName("valueOf is case sensitive")
        void valueOfIsCaseSensitive() {
            assertThrows(IllegalArgumentException.class, () -> {
                WorkItemCompletion.valueOf("normal");
            }, "valueOf should be case sensitive");
        }
    }

    // ========================================================================
    // Name Tests
    // ========================================================================

    @Nested
    @DisplayName("Name Tests")
    class NameTests {

        @ParameterizedTest
        @EnumSource(WorkItemCompletion.class)
        @DisplayName("name returns correct string")
        void nameReturnsCorrectString(WorkItemCompletion completion) {
            String name = completion.name();
            assertNotNull(name, "name should not be null");
            assertTrue(name.length() > 0, "name should not be empty");
        }

        @Test
        @DisplayName("Normal name is 'Normal'")
        void normalNameIsNormal() {
            assertEquals("Normal", WorkItemCompletion.Normal.name());
        }

        @Test
        @DisplayName("Force name is 'Force'")
        void forceNameIsForce() {
            assertEquals("Force", WorkItemCompletion.Force.name());
        }

        @Test
        @DisplayName("Fail name is 'Fail'")
        void failNameIsFail() {
            assertEquals("Fail", WorkItemCompletion.Fail.name());
        }

        @Test
        @DisplayName("Invalid name is 'Invalid'")
        void invalidNameIsInvalid() {
            assertEquals("Invalid", WorkItemCompletion.Invalid.name());
        }
    }

    // ========================================================================
    // ToString Tests
    // ========================================================================

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @ParameterizedTest
        @EnumSource(WorkItemCompletion.class)
        @DisplayName("toString returns non-null")
        void toStringReturnsNonNull(WorkItemCompletion completion) {
            assertNotNull(completion.toString(), "toString should not return null");
        }

        @Test
        @DisplayName("toString returns same as name by default")
        void toStringReturnsSameAsNameByDefault() {
            for (WorkItemCompletion completion : WorkItemCompletion.values()) {
                assertEquals(completion.name(), completion.toString(),
                        "toString should equal name for " + completion.name());
            }
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
            WorkItemCompletion normal1 = WorkItemCompletion.Normal;
            WorkItemCompletion normal2 = WorkItemCompletion.Normal;

            assertSame(normal1, normal2, "Enum values should be the same instance");
        }

        @Test
        @DisplayName("Enum values are equal to themselves")
        void enumValuesAreEqualToThemselves() {
            assertEquals(WorkItemCompletion.Normal, WorkItemCompletion.Normal);
            assertEquals(WorkItemCompletion.Force, WorkItemCompletion.Force);
            assertEquals(WorkItemCompletion.Fail, WorkItemCompletion.Fail);
            assertEquals(WorkItemCompletion.Invalid, WorkItemCompletion.Invalid);
        }

        @Test
        @DisplayName("Different enum values are not equal")
        void differentEnumValuesAreNotEqual() {
            assertNotEquals(WorkItemCompletion.Normal, WorkItemCompletion.Force);
            assertNotEquals(WorkItemCompletion.Normal, WorkItemCompletion.Fail);
            assertNotEquals(WorkItemCompletion.Force, WorkItemCompletion.Fail);
            assertNotEquals(WorkItemCompletion.Invalid, WorkItemCompletion.Normal);
        }
    }

    // ========================================================================
    // Switch Usage Tests
    // ========================================================================

    @Nested
    @DisplayName("Switch Usage Tests")
    class SwitchUsageTests {

        @Test
        @DisplayName("Can use in switch statement")
        void canUseInSwitchStatement() {
            for (WorkItemCompletion completion : WorkItemCompletion.values()) {
                String result = switch (completion) {
                    case Normal -> "normal completion";
                    case Force -> "forced completion";
                    case Fail -> "failed completion";
                    case Invalid -> "invalid completion";
                };
                assertNotNull(result, "Switch should return non-null for " + completion);
            }
        }

        @Test
        @DisplayName("Switch covers all cases")
        void switchCoversAllCases() {
            int normalCount = 0;
            int forceCount = 0;
            int failCount = 0;
            int invalidCount = 0;

            for (WorkItemCompletion completion : WorkItemCompletion.values()) {
                switch (completion) {
                    case Normal -> normalCount++;
                    case Force -> forceCount++;
                    case Fail -> failCount++;
                    case Invalid -> invalidCount++;
                }
            }

            assertEquals(1, normalCount, "Should have one Normal");
            assertEquals(1, forceCount, "Should have one Force");
            assertEquals(1, failCount, "Should have one Fail");
            assertEquals(1, invalidCount, "Should have one Invalid");
        }
    }
}
