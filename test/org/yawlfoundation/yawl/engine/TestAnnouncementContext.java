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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.yawlfoundation.yawl.engine.announcement.AnnouncementContext;

/**
 * Comprehensive tests for AnnouncementContext enum following Chicago TDD methodology.
 *
 * <p>Tests the announcement context types used during engine notifications.</p>
 *
 * @author YAWL Test Suite
 * @see AnnouncementContext
 */
@DisplayName("AnnouncementContext Enum Tests")
@Tag("unit")
class TestAnnouncementContext {

    // ========================================================================
    // Enum Values Tests
    // ========================================================================

    @Nested
    @DisplayName("Enum Values Tests")
    class EnumValuesTests {

        @Test
        @DisplayName("Enum has exactly 2 values")
        void enumHasExactly2Values() {
            AnnouncementContext[] values = AnnouncementContext.values();
            assertEquals(2, values.length, "AnnouncementContext should have 2 values");
        }

        @ParameterizedTest
        @EnumSource(AnnouncementContext.class)
        @DisplayName("All enum values are non-null")
        void allEnumValuesAreNonNull(AnnouncementContext context) {
            assertNotNull(context, "Enum value should not be null");
        }

        @Test
        @DisplayName("Enum contains NORMAL context")
        void enumContainsNormalContext() {
            assertNotNull(AnnouncementContext.NORMAL, "NORMAL should be defined");
        }

        @Test
        @DisplayName("Enum contains RECOVERING context")
        void enumContainsRecoveringContext() {
            assertNotNull(AnnouncementContext.RECOVERING, "RECOVERING should be defined");
        }
    }

    // ========================================================================
    // Name Tests
    // ========================================================================

    @Nested
    @DisplayName("Name Tests")
    class NameTests {

        @Test
        @DisplayName("NORMAL name is 'NORMAL'")
        void normalNameIsNormal() {
            assertEquals("NORMAL", AnnouncementContext.NORMAL.name());
        }

        @Test
        @DisplayName("RECOVERING name is 'RECOVERING'")
        void recoveringNameIsRecovering() {
            assertEquals("RECOVERING", AnnouncementContext.RECOVERING.name());
        }
    }

    // ========================================================================
    // Ordinal Tests
    // ========================================================================

    @Nested
    @DisplayName("Ordinal Tests")
    class OrdinalTests {

        @Test
        @DisplayName("NORMAL has ordinal 0")
        void normalHasOrdinal0() {
            assertEquals(0, AnnouncementContext.NORMAL.ordinal());
        }

        @Test
        @DisplayName("RECOVERING has ordinal 1")
        void recoveringHasOrdinal1() {
            assertEquals(1, AnnouncementContext.RECOVERING.ordinal());
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
            assertEquals(AnnouncementContext.NORMAL, AnnouncementContext.valueOf("NORMAL"));
            assertEquals(AnnouncementContext.RECOVERING, AnnouncementContext.valueOf("RECOVERING"));
        }

        @Test
        @DisplayName("valueOf throws for invalid name")
        void valueOfThrowsForInvalidName() {
            assertThrows(IllegalArgumentException.class, () -> {
                AnnouncementContext.valueOf("UNKNOWN");
            }, "valueOf should throw for unknown name");
        }

        @Test
        @DisplayName("valueOf is case sensitive")
        void valueOfIsCaseSensitive() {
            assertThrows(IllegalArgumentException.class, () -> {
                AnnouncementContext.valueOf("normal");
            }, "valueOf should be case sensitive");
        }
    }

    // ========================================================================
    // ToString Tests
    // ========================================================================

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @ParameterizedTest
        @EnumSource(AnnouncementContext.class)
        @DisplayName("toString returns non-null")
        void toStringReturnsNonNull(AnnouncementContext context) {
            assertNotNull(context.toString(), "toString should not return null");
        }

        @Test
        @DisplayName("toString returns same as name by default")
        void toStringReturnsSameAsNameByDefault() {
            assertEquals(AnnouncementContext.NORMAL.name(), AnnouncementContext.NORMAL.toString());
            assertEquals(AnnouncementContext.RECOVERING.name(), AnnouncementContext.RECOVERING.toString());
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
            for (AnnouncementContext context : AnnouncementContext.values()) {
                String result = switch (context) {
                    case NORMAL -> "normal operation";
                    case RECOVERING -> "recovery mode";
                };
                assertNotNull(result, "Switch should return non-null for " + context.name());
            }
        }

        @Test
        @DisplayName("Switch covers all cases")
        void switchCoversAllCases() {
            int normalCount = 0;
            int recoveringCount = 0;

            for (AnnouncementContext context : AnnouncementContext.values()) {
                switch (context) {
                    case NORMAL -> normalCount++;
                    case RECOVERING -> recoveringCount++;
                }
            }

            assertEquals(1, normalCount, "Should have one NORMAL");
            assertEquals(1, recoveringCount, "Should have one RECOVERING");
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
            AnnouncementContext normal1 = AnnouncementContext.NORMAL;
            AnnouncementContext normal2 = AnnouncementContext.NORMAL;
            assertSame(normal1, normal2, "Enum values should be the same instance");
        }

        @Test
        @DisplayName("Different enum values are not equal")
        void differentEnumValuesAreNotEqual() {
            assertNotEquals(AnnouncementContext.NORMAL, AnnouncementContext.RECOVERING);
        }
    }
}
