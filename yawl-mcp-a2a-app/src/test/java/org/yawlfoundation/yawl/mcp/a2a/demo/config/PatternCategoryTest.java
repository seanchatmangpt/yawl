/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.demo.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.NullSource;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for PatternCategory using Chicago TDD style.
 * Tests enum behavior, color codes, lookups, and pattern ID categorization.
 */
@DisplayName("PatternCategory Configuration Tests")
class PatternCategoryTest {

    private static final String RESET_CODE = PatternCategory.RESET_CODE;

    @Nested
    @DisplayName("Enum Values Tests")
    class EnumValuesTests {

        @Test
        @DisplayName("Enum should have all expected categories")
        void enumShouldHaveAllExpectedCategories() {
            PatternCategory[] values = PatternCategory.values();

            // Should have 17 categories
            assertEquals(17, values.length, "Should have 17 pattern categories");

            // Check all expected categories exist
            Arrays.asList("BASIC", "BRANCHING", "MULTI_INSTANCE", "STATE_BASED", "DISTRIBUTED",
                          "EVENT_DRIVEN", "AI_ML", "ENTERPRISE", "AGENT", "GREGVERSE_SCENARIO",
                          "ADVANCED_BRANCHING", "STRUCTURAL", "ITERATION", "TERMINATION",
                          "EXTENDED", "CANCELLATION", "UNCLASSIFIED")
                .forEach(expectedName -> {
                    boolean found = false;
                    for (PatternCategory category : values) {
                        if (category.name().equals(expectedName)) {
                            found = true;
                            break;
                        }
                    }
                    assertTrue(found, "Category " + expectedName + " should exist");
                });
        }

        @Test
        @DisplayName("All categories should have display names and color codes")
        void allCategoriesShouldHaveDisplayNamesAndColorCodes() {
            PatternCategory[] values = PatternCategory.values();

            for (PatternCategory category : values) {
                assertNotNull(category.getDisplayName(),
                           "Category " + category.name() + " should have display name");
                assertFalse(category.getDisplayName().trim().isEmpty(),
                           "Category " + category.name() + " display name should not be empty");

                assertNotNull(category.getColorCode(),
                           "Category " + category.name() + " should have color code");
                assertFalse(category.getColorCode().trim().isEmpty(),
                           "Category " + category.name() + " color code should not be empty");
            }
        }
    }

    @Nested
    @DisplayName("Color Code Tests")
    class ColorCodeTests {

        @Test
        @DisplayName("All color codes should be valid ANSI escape sequences")
        void allColorCodesShouldBeValidAnsiSequences() {
            PatternCategory[] values = PatternCategory.values();

            for (PatternCategory category : values) {
                String colorCode = category.getColorCode();

                // Color codes should start with ANSI escape sequence
                assertTrue(colorCode.startsWith("\u001B["),
                           "Color code for " + category.name() + " should start with ESC");

                // Color codes should end with a valid ANSI code
                assertTrue(colorCode.matches("\u001B\\[[0-9;]*m"),
                           "Color code for " + category.name() + " should be valid ANSI sequence");
            }
        }

        @Test
        @DisplayName("RESET_CODE should be valid ANSI reset sequence")
        void resetCodeShouldBeValidAnsiReset() {
            assertEquals("\u001B[0m", RESET_CODE);
            assertTrue(RESET_CODE.startsWith("\u001B["));
            assertTrue(RESET_CODE.endsWith("m"));
        }

        @Test
        @DisplayName("colorize method should append reset code")
        void colorizeMethodShouldAppendResetCode() {
            String text = "Test Text";
            String colored = PatternCategory.BASIC.colorize(text);

            // Should start with color code
            assertTrue(colored.startsWith(PatternCategory.BASIC.getColorCode()));
            // Should end with reset code
            assertTrue(colored.endsWith(RESET_CODE));
            // Should contain the original text in between
            assertTrue(colored.contains(text));
        }
    }

    @Nested
    @DisplayName("Lookup Methods Tests")
    class LookupMethodsTests {

        @Test
        @DisplayName("fromDisplayName should find category by exact match")
        void fromDisplayNameShouldFindCategoryByExactMatch() {
            PatternCategory category = PatternCategory.fromDisplayName("Basic Control Flow");
            assertEquals(PatternCategory.BASIC, category);
        }

        @Test
        @DisplayName("fromDisplayName should be case insensitive")
        void fromDisplayNameShouldBeCaseInsensitive() {
            PatternCategory category1 = PatternCategory.fromDisplayName("BASIC CONTROL FLOW");
            PatternCategory category2 = PatternCategory.fromDisplayName("basic control flow");
            PatternCategory category3 = PatternCategory.fromDisplayName("BaSiC CoNtRoL fLoW");

            assertEquals(PatternCategory.BASIC, category1);
            assertEquals(PatternCategory.BASIC, category2);
            assertEquals(PatternCategory.BASIC, category3);
        }

        @Test
        @DisplayName("fromDisplayName should return null for unknown name")
        void fromDisplayNameShouldReturnNullForUnknownName() {
            assertNull(PatternCategory.fromDisplayName("Unknown Category"));
            assertNull(PatternCategory.fromDisplayName(""));
            assertNull(PatternCategory.fromDisplayName(null));
        }

        @Test
        @DisplayName("fromName should find category by enum name")
        void fromNameShouldFindCategoryByName() {
            PatternCategory category = PatternCategory.fromName("BASIC");
            assertEquals(PatternCategory.BASIC, category);
        }

        @Test
        @DisplayName("fromName should be case insensitive")
        void fromNameShouldBeCaseInsensitive() {
            PatternCategory category1 = PatternCategory.fromName("basic");
            PatternCategory category2 = PatternCategory.fromName("BASIC");
            PatternCategory category3 = PatternCategory.fromName("Basic");

            assertEquals(PatternCategory.BASIC, category1);
            assertEquals(PatternCategory.BASIC, category2);
            assertEquals(PatternCategory.BASIC, category3);
        }

        @Test
        @DisplayName("fromName should return null for unknown enum name")
        void fromNameShouldReturnNullForUnknownEnumName() {
            assertNull(PatternCategory.fromName("UNKNOWN"));
            assertNull(PatternCategory.fromName(""));
            assertNull(PatternCategory.fromName(null));
        }

        @Test
        @DisplayName("fromPatternId should categorize basic control flow patterns (WCP 1-5)")
        void fromPatternIdShouldCategorizeBasicPatterns() {
            assertEquals(PatternCategory.BASIC, PatternCategory.fromPatternId("WCP-1"));
            assertEquals(PatternCategory.BASIC, PatternCategory.fromPatternId("WCP-2"));
            assertEquals(PatternCategory.BASIC, PatternCategory.fromPatternId("WCP-3"));
            assertEquals(PatternCategory.BASIC, PatternCategory.fromPatternId("WCP-4"));
            assertEquals(PatternCategory.BASIC, PatternCategory.fromPatternId("WCP-5"));
        }

        @Test
        @DisplayName("fromPatternId should categorize branching patterns (WCP 6-10)")
        void fromPatternIdShouldCategorizeBranchingPatterns() {
            assertEquals(PatternCategory.BRANCHING, PatternCategory.fromPatternId("WCP-6"));
            assertEquals(PatternCategory.BRANCHING, PatternCategory.fromPatternId("WCP-7"));
            assertEquals(PatternCategory.BRANCHING, PatternCategory.fromPatternId("WCP-8"));
            assertEquals(PatternCategory.BRANCHING, PatternCategory.fromPatternId("WCP-9"));
            assertEquals(PatternCategory.BRANCHING, PatternCategory.fromPatternId("WCP-10"));
        }

        @Test
        @DisplayName("fromPatternId should categorize multi-instance patterns (WCP 12-17)")
        void fromPatternIdShouldCategorizeMultiInstancePatterns() {
            assertEquals(PatternCategory.MULTI_INSTANCE, PatternCategory.fromPatternId("WCP-12"));
            assertEquals(PatternCategory.MULTI_INSTANCE, PatternCategory.fromPatternId("WCP-13"));
            assertEquals(PatternCategory.MULTI_INSTANCE, PatternCategory.fromPatternId("WCP-14"));
            assertEquals(PatternCategory.MULTI_INSTANCE, PatternCategory.fromPatternId("WCP-15"));
            assertEquals(PatternCategory.MULTI_INSTANCE, PatternCategory.fromPatternId("WCP-16"));
            assertEquals(PatternCategory.MULTI_INSTANCE, PatternCategory.fromPatternId("WCP-17"));
        }

        @Test
        @DisplayName("fromPatternId should categorize state-based patterns (WCP 18-21)")
        void fromPatternIdShouldCategorizeStateBasedPatterns() {
            assertEquals(PatternCategory.STATE_BASED, PatternCategory.fromPatternId("WCP-18"));
            assertEquals(PatternCategory.STATE_BASED, PatternCategory.fromPatternId("WCP-19"));
            assertEquals(PatternCategory.STATE_BASED, PatternCategory.fromPatternId("WCP-20"));
            assertEquals(PatternCategory.STATE_BASED, PatternCategory.fromPatternId("WCP-21"));
        }

        @Test
        @DisplayName("fromPatternId should categorize extended patterns (WCP 23-43)")
        void fromPatternIdShouldCategorizeExtendedPatterns() {
            assertEquals(PatternCategory.EXTENDED, PatternCategory.fromPatternId("WCP-23"));
            assertEquals(PatternCategory.EXTENDED, PatternCategory.fromPatternId("WCP-43"));
        }

        @Test
        @DisplayName("fromPatternId should categorize distributed patterns (WCP 44-50)")
        void fromPatternIdShouldCategorizeDistributedPatterns() {
            assertEquals(PatternCategory.DISTRIBUTED, PatternCategory.fromPatternId("WCP-44"));
            assertEquals(PatternCategory.DISTRIBUTED, PatternCategory.fromPatternId("WCP-50"));
        }

        @Test
        @DisplayName("fromPatternId should categorize event-driven patterns (WCP 51-59)")
        void fromPatternIdShouldCategorizeEventDrivenPatterns() {
            assertEquals(PatternCategory.EVENT_DRIVEN, PatternCategory.fromPatternId("WCP-51"));
            assertEquals(PatternCategory.EVENT_DRIVEN, PatternCategory.fromPatternId("WCP-59"));
        }

        @Test
        @DisplayName("fromPatternId should categorize AI/ML patterns (WCP 60-68)")
        void fromPatternIdShouldCategorizeAIMLPatterns() {
            assertEquals(PatternCategory.AI_ML, PatternCategory.fromPatternId("WCP-60"));
            assertEquals(PatternCategory.AI_ML, PatternCategory.fromPatternId("WCP-68"));
        }

        @Test
        @DisplayName("fromPatternId should categorize enterprise patterns (ENT-1-8)")
        void fromPatternIdShouldCategorizeEnterprisePatterns() {
            assertEquals(PatternCategory.ENTERPRISE, PatternCategory.fromPatternId("ENT-1"));
            assertEquals(PatternCategory.ENTERPRISE, PatternCategory.fromPatternId("ENT-8"));
        }

        @Test
        @DisplayName("fromPatternId should categorize agent patterns (AGT-1-5)")
        void fromPatternIdShouldCategorizeAgentPatterns() {
            assertEquals(PatternCategory.AGENT, PatternCategory.fromPatternId("AGT-1"));
            assertEquals(PatternCategory.AGENT, PatternCategory.fromPatternId("AGT-5"));
        }

        @Test
        @DisplayName("fromPatternId should categorize GregVerse scenarios (GVS/GV)")
        void fromPatternIdShouldCategorizeGregVerseScenarios() {
            assertEquals(PatternCategory.GREGVERSE_SCENARIO, PatternCategory.fromPatternId("GVS-1"));
            assertEquals(PatternCategory.GREGVERSE_SCENARIO, PatternCategory.fromPatternId("GV-1"));
        }

        @Test
        @DisplayName("fromPatternId should categorize cancellation patterns")
        void fromPatternIdShouldCategorizeCancellationPatterns() {
            assertEquals(PatternCategory.CANCELLATION, PatternCategory.fromPatternId("WCP-19-CF"));
            assertEquals(PatternCategory.CANCELLATION, PatternCategory.fromPatternId("WCP-20-CF"));
            assertEquals(PatternCategory.CANCELLATION, PatternCategory.fromPatternId("WCP-22"));
        }

        @Test
        @DisplayName("fromPatternId should categorize iteration patterns")
        void fromPatternIdShouldCategorizeIterationPatterns() {
            assertEquals(PatternCategory.ITERATION, PatternCategory.fromPatternId("WCP-28"));
            assertEquals(PatternCategory.ITERATION, PatternCategory.fromPatternId("WCP-31"));
        }

        @Test
        @DisplayName("fromPatternId should categorize structural patterns")
        void fromPatternIdShouldCategorizeStructuralPatterns() {
            assertEquals(PatternCategory.STRUCTURAL, PatternCategory.fromPatternId("WCP-11"));
        }

        @Test
        @DisplayName("fromPatternId should return UNCLASSIFIED for unknown patterns")
        void fromPatternIdShouldReturnUnclassifiedForUnknownPatterns() {
            assertEquals(PatternCategory.UNCLASSIFIED, PatternCategory.fromPatternId("UNKNOWN-1"));
            assertEquals(PatternCategory.UNCLASSIFIED, PatternCategory.fromPatternId(""));
            assertEquals(PatternCategory.UNCLASSIFIED, PatternCategory.fromPatternId(null));
            assertEquals(PatternCategory.UNCLASSIFIED, PatternCategory.fromPatternId("XYZ-1"));
        }

        @Test
        @DisplayName("fromPatternId should handle case insensitivity")
        void fromPatternIdShouldHandleCaseInsensitivity() {
            assertEquals(PatternCategory.BASIC, PatternCategory.fromPatternId("wcp-1"));
            assertEquals(PatternCategory.BASIC, PatternCategory.fromPatternId("WCP-1"));
            assertEquals(PatternCategory.BASIC, PatternCategory.fromPatternId("wcp-1"));
        }

        @Test
        @DisplayName("fromPatternId should handle invalid numbers in WCP pattern IDs")
        void fromPatternIdShouldHandleInvalidNumbersInWCPPatterns() {
            // Invalid numbers should return UNCLASSIFIED
            assertEquals(PatternCategory.UNCLASSIFIED, PatternCategory.fromPatternId("WCP-0"));
            assertEquals(PatternCategory.UNCLASSIFIED, PatternCategory.fromPatternId("WCP-69"));
            assertEquals(PatternCategory.UNCLASSIFIED, PatternCategory.fromPatternId("WCP-100"));
            assertEquals(PatternCategory.UNCLASSIFIED, PatternCategory.fromPatternId("WCP-ABC"));
        }
    }

    @Nested
    @DisplayName("isEnabledIn Tests")
    class IsEnabledInTests {

        @Test
        @DisplayName("isEnabledIn should return true when filter list is empty")
        void isEnabledInShouldReturnTrueWhenFilterListIsEmpty() {
            assertTrue(PatternCategory.BASIC.isEnabledIn(Collections.emptyList()));
        }

        @Test
        @DisplayName("isEnabledIn should return true when filter list is null")
        void isEnabledInShouldReturnTrueWhenFilterListIsNull() {
            assertTrue(PatternCategory.BASIC.isEnabledIn(null));
        }

        @Test
        @DisplayName("isEnabledIn should return true when category is in filter list")
        void isEnabledInShouldReturnTrueWhenCategoryIsInFilterList() {
            List<PatternCategory> filter = List.of(PatternCategory.BASIC, PatternCategory.ADVANCED_BRANCHING);

            assertTrue(PatternCategory.BASIC.isEnabledIn(filter));
            assertTrue(PatternCategory.ADVANCED_BRANCHING.isEnabledIn(filter));
        }

        @Test
        @DisplayName("isEnabledIn should return false when category is not in filter list")
        void isEnabledInShouldReturnFalseWhenCategoryIsNotInFilterList() {
            List<PatternCategory> filter = List.of(PatternCategory.BASIC, PatternCategory.ADVANCED_BRANCHING);

            assertFalse(PatternCategory.AI_ML.isEnabledIn(filter));
            assertFalse(PatternCategory.ENTERPRISE.isEnabledIn(filter));
        }
    }

    @Nested
    @DisplayName("Display Name Tests")
    class DisplayNameTests {

        @Test
        @DisplayName("All categories should have non-null, non-empty display names")
        void allCategoriesShouldHaveNonNullDisplayNames() {
            PatternCategory[] values = PatternCategory.values();

            for (PatternCategory category : values) {
                assertNotNull(category.getDisplayName(),
                           "Category " + category.name() + " should have display name");
                assertFalse(category.getDisplayName().trim().isEmpty(),
                           "Category " + category.name() + " display name should not be empty");
            }
        }

        @Test
        @DisplayName("Display names should be user-friendly")
        void displayNamesShouldBeUserFriendly() {
            PatternCategory[] values = PatternCategory.values();

            for (PatternCategory category : values) {
                String displayName = category.getDisplayName();

                // Should not contain underscores
                assertFalse(displayName.contains("_"),
                           "Display name should not contain underscores: " + displayName);

                // Should not contain enum-style all caps
                assertFalse(displayName.equals(category.name()),
                           "Display name should be different from enum name: " + displayName);

                // Should have proper capitalization
                assertTrue(displayName.matches("^[A-Z][a-zA-Z\\s\\-/]*[a-zA-Z]$"),
                           "Display name should be properly capitalized: " + displayName);
            }
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("All methods should handle null input gracefully")
        void allMethodsShouldHandleNullInputGracefully() {
            assertNull(PatternCategory.fromDisplayName(null));
            assertNull(PatternCategory.fromName(null));
            assertEquals(PatternCategory.UNCLASSIFIED, PatternCategory.fromPatternId(null));

            assertTrue(PatternCategory.BASIC.isEnabledIn(null));
        }

        @Test
        @DisplayName("All categories should be unique")
        void allCategoriesShouldBeUnique() {
            PatternCategory[] values = PatternCategory.values();

            // Check no duplicate enum names
            for (int i = 0; i < values.length; i++) {
                for (int j = i + 1; j < values.length; j++) {
                    assertNotEquals(values[i].name(), values[j].name(),
                                   "Categories should have unique enum names");
                }
            }

            // Check no duplicate display names
            List<String> displayNames = new ArrayList<>();
            for (PatternCategory category : values) {
                assertFalse(displayNames.contains(category.getDisplayName()),
                           "Duplicate display name found: " + category.getDisplayName());
                displayNames.add(category.getDisplayName());
            }
        }

        @Test
        @DisplayName("Color codes should not be the same as reset code")
        void colorCodesShouldNotBeSameAsResetCode() {
            PatternCategory[] values = PatternCategory.values();

            for (PatternCategory category : values) {
                assertNotEquals(RESET_CODE, category.getColorCode(),
                               "Color code should not be reset code for category: " + category.name());
            }
        }
    }
}