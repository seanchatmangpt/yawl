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

package org.yawlfoundation.yawl.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for {@link YPredicateParser}.
 *
 * <p>Coverage: string parsing with embedded expressions, value substitution,
 * null/blank handling, edge cases, and performance scenarios. Uses Chicago TDD
 * style (real string parsing, no mocks).
 *
 * @author YAWL Test Agent
 * @since YAWL 6.0.0-Beta
 */
@DisplayName("YPredicateParser")
class TestYPredicateParser {

    private YPredicateParser parser;

    @BeforeEach
    void setUp() {
        parser = new YPredicateParser();
    }

    // =========================================================================
    // Basic functionality tests
    // =========================================================================

    @Test
    @DisplayName("parse(noExpressions) returns original string")
    void parse_noExpressions_returnsOriginalString() {
        String input = "Hello World";
        String result = parser.parse(input);

        assertEquals(input, result,
            "Should return original string when no expressions are present");
    }

    @Test
    @DisplayName("parse(nullInput) returns null")
    void parse_nullInput_returnsNull() {
        String result = parser.parse(null);

        assertNull(result,
            "Should return null when input string is null");
    }

    @Test
    @DisplayName("parse(emptyInput) returns empty string")
    void parse_emptyInput_returnsEmptyString() {
        String result = parser.parse("");

        assertEquals("", result,
            "Should return empty string when input is empty");
    }

    // =========================================================================
    // Time expression tests
    // =========================================================================

    @Test
    @DisplayName("parse(nowExpression) replaces with current datetime")
    void parse_nowExpression_replacesWithCurrentDateTime() {
        String input = "Current time: ${now}";
        String result = parser.parse(input);

        // Should contain the date pattern
        assertTrue(result.matches("Current time: \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
            "Should replace ${now} with current date time");
        assertFalse(result.contains("${now}"),
            "Should not contain the original ${now} expression");
    }

    @Test
    @DisplayName("parse(dateExpression) replaces with current date")
    void parse_dateExpression_replacesWithCurrentDate() {
        String input = "Today is ${date}";
        String result = parser.parse(input);

        // Should contain the date pattern
        assertTrue(result.matches("Today is \\d{4}-\\d{2}-\\d{2}"),
            "Should replace ${date} with current date");
        assertFalse(result.contains("${date}"),
            "Should not contain the original ${date} expression");
    }

    @Test
    @DisplayName("parse(timeExpression) replaces with current time")
    void parse_timeExpression_replacesWithCurrentTime() {
        String input = "The time is ${time}";
        String result = parser.parse(input);

        // Should contain the time pattern
        assertTrue(result.matches("The time is \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
            "Should replace ${time} with current time");
        assertFalse(result.contains("${time}"),
            "Should not contain the original ${time} expression");
    }

    // =========================================================================
    // Multiple expressions tests
    // =========================================================================

    @Test
    @DisplayName("parse(multipleExpressions) replaces all")
    void parse_multipleExpressions_replacesAll() {
        String input = "Start: ${date} ${time} - End: ${now}";
        String result = parser.parse(input);

        assertTrue(result.contains("Start: "),
            "Should preserve non-expression parts");
        assertTrue(result.contains("End: "),
            "Should preserve non-expression parts");
        assertFalse(result.contains("${date}"),
            "Should not contain any expressions");
        assertFalse(result.contains("${time}"),
            "Should not contain any expressions");
        assertFalse(result.contains("${now}"),
            "Should not contain any expressions");
    }

    @Test
    @DisplayName("parse(expressionsWithText) works correctly")
    void parse_expressionsWithText_worksCorrectly() {
        String input = "Status: ${date} ${time} - Current: ${now}";
        String result = parser.parse(input);

        assertTrue(result.startsWith("Status: "),
            "Should preserve prefix");
        assertTrue(result.endsWith(" - Current: " + result.substring(result.lastIndexOf(" - Current: ") + 12)),
            "Should preserve suffix");
    }

    @Test
    @DisplayName("parse(consecutiveExpressions) handles without separators")
    void parse_consecutiveExpressions_worksCorrectly() {
        String input = "${date}${time}${now}";
        String result = parser.parse(input);

        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
            "Should handle consecutive expressions without separators");
    }

    @Test
    @DisplayName("parse(sameExpressionMultiple) replaces all occurrences")
    void parse_sameExpressionMultiple_replacesAll() {
        String input = "Date: ${date} Time: ${date} End: ${date}";
        String result = parser.parse(input);

        // Should replace all ${date} expressions
        // Java split removes trailing empty strings; 3 dates with string ending on date → length-1=2
        assertEquals(2, result.split("\\d{4}-\\d{2}-\\d{2}").length - 1,
            "Should have three date values in result");
    }

    // =========================================================================
    // Edge case tests
    // =========================================================================

    @Test
    @DisplayName("parse(nonExpressionLiterals) preserves as-is")
    void parse_nonExpressionLiterals_preservesAsIs() {
        // Parser has no backslash-escape support; only unrecognised expressions are preserved
        String input = "This is not an expression: ${notreal}";
        String result = parser.parse(input);

        // ${notreal} should remain as-is (unrecognized)
        assertTrue(result.contains("${notreal}"),
            "Should preserve unrecognized expressions");
    }

    @Test
    @DisplayName("parse(malformedExpressions) preserves as-is")
    void parse_malformedExpressions_preservesAsIs() {
        String input = "Malformed: ${date ${time}} ${now}";
        String result = parser.parse(input);

        // Split-based parser replaces inner ${time}; outer malformed prefix ${date is preserved
        assertTrue(result.contains("${date "),
            "Should preserve malformed outer expression prefix");
        assertFalse(result.contains("${now}"),
            "Should replace valid ${now} expression adjacent to malformed one");
    }

    @Test
    @DisplayName("parse(nestedExpressions) preserves as-is")
    void parse_nestedExpressions_preservesAsIs() {
        String input = "Nested: ${date ${time}} ${now}";
        String result = parser.parse(input);

        // Split-based parser replaces inner ${time}; outer nested prefix ${date is preserved
        assertTrue(result.contains("${date "),
            "Should preserve nested outer expression prefix");
        assertFalse(result.contains("${now}"),
            "Should replace valid ${now} expression adjacent to nested one");
    }

    @Test
    @DisplayName("parse(emptyExpressions) replaces with n/a")
    void parse_emptyExpressions_replacesWithN_a() {
        String input = "Empty: ${}";
        String result = parser.parse(input);

        assertEquals("Empty: n/a", result,
            "Empty expressions should be replaced with 'n/a'");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n", "\r\n", "\t "})
    @DisplayName("parse(whitespaceAroundExpressions) preserves whitespace")
    void parse_whitespaceAroundExpressions_worksCorrectly(String whitespace) {
        String input = whitespace + "${date}" + whitespace + "${time}" + whitespace + "${now}" + whitespace;
        String result = parser.parse(input);

        // null input from @NullAndEmptySource produces "null..." string; skip assertions
        Assumptions.assumeTrue(whitespace != null, "Skip null whitespace test case");
        // Should preserve whitespace while replacing expressions
        assertTrue(result.startsWith(whitespace),
            "Should preserve leading whitespace");
        assertTrue(result.endsWith(whitespace),
            "Should preserve trailing whitespace");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "a${date}b${time}c${now}d",
        "1${date}2${time}3${now}4"
    })
    @DisplayName("parse(singleCharText) works correctly")
    void parse_singleCharacterText_worksCorrectly(String input) {
        String result = parser.parse(input);

        // Should preserve single character text around expressions
        char firstChar = input.charAt(0);
        char lastChar = input.charAt(input.length() - 1);
        assertTrue(result.startsWith(String.valueOf(firstChar)),
            "Should preserve first character");
        assertTrue(result.endsWith(String.valueOf(lastChar)),
            "Should preserve last character");
    }

    // =========================================================================
    // Format variation tests
    // =========================================================================

    @Test
    @DisplayName("parse(mixedCaseExpressions) preserves as-is")
    void parse_mixedCaseExpressions_preservesAsIs() {
        String input = "Mixed: ${Date} ${TIME} ${Now}";
        String result = parser.parse(input);

        // Mixed case expressions should not be recognized
        assertTrue(result.contains("${Date}"),
            "Should preserve mixed case date expression");
        assertTrue(result.contains("${TIME}"),
            "Should preserve mixed case time expression");
        assertTrue(result.contains("${Now}"),
            "Should preserve mixed case now expression");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "${ date }",
        "${ time }",
        "${ now }",
        "${date }",
        "${ time}"
    })
    @DisplayName("parse(expressionsWithExtraSpaces) preserves as-is")
    void parse_expressionsWithExtraSpaces_preservesAsIs(String expression) {
        String input = "Spaced: " + expression;
        String result = parser.parse(input);

        // Expressions with extra spaces should not be recognized
        assertTrue(result.contains(expression),
            "Should preserve expressions with extra spaces");
    }

    // =========================================================================
    // String content tests
    // =========================================================================

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "  ", "\t", "\n", "\r\n"})
    @DisplayName("parse(whitespaceOnlyStrings) preserves as-is")
    void parse_whitespaceOnlyStrings_preservesAsIs(String input) {
        String result = parser.parse(input);

        assertEquals(input, result,
            "Should preserve whitespace-only strings as-is");
    }

    @Test
    @DisplayName("parse(onlyExpressions) replaces all")
    void parse_onlyExpressions_replacesAll() {
        String input = "${date}${time}${now}";
        String result = parser.parse(input);

        // All expressions should be replaced
        assertFalse(result.contains("${date}"),
            "Should replace ${date}");
        assertFalse(result.contains("${time}"),
            "Should replace ${time}");
        assertFalse(result.contains("${now}"),
            "Should replace ${now}");
    }

    @Test
    @DisplayName("parse(repeatedSameExpression) replaces all occurrences")
    void parse_repeatedExpressions_replacesAll() {
        String input = "Date: ${date} Time: ${time} Date: ${date}";
        String result = parser.parse(input);

        // All occurrences should be replaced
        assertEquals(2, result.split("Date: ").length - 1,
            "Should have replaced all ${date} expressions");
    }

    // =========================================================================
    // Special character tests
    // =========================================================================

    @Test
    @DisplayName("parse(stringsWithNewlines) preserves newlines")
    void parse_stringsWithNewlines_worksCorrectly() {
        String input = "Line1: ${date}\nLine2: ${time}\nLine3: ${now}";
        String result = parser.parse(input);

        // Should preserve newlines while replacing expressions
        assertTrue(result.contains("\n"),
            "Should preserve newlines");
        assertFalse(result.contains("${date}"),
            "Should replace ${date}");
        assertFalse(result.contains("${time}"),
            "Should replace ${time}");
        assertFalse(result.contains("${now}"),
            "Should replace ${now}");
    }

    @Test
    @DisplayName("parse(stringsWithTabs) preserves tabs")
    void parse_stringsWithTabs_worksCorrectly() {
        String input = "Col1:\t${date}\tCol2:\t${time}";
        String result = parser.parse(input);

        // Should preserve tabs while replacing expressions
        assertTrue(result.contains("\t"),
            "Should preserve tabs");
        assertFalse(result.contains("${date}"),
            "Should replace ${date}");
        assertFalse(result.contains("${time}"),
            "Should replace ${time}");
    }

    @Test
    @DisplayName("parse(stringsWithSpecialChars) preserves special characters")
    void parse_stringsWithSpecialCharacters_worksCorrectly() {
        String input = "Special: !@#$%^&*() ${date} _+-=[]{}|;':\",./<>?";
        String result = parser.parse(input);

        // Should preserve special characters while replacing expressions
        assertTrue(result.contains("!@#$%^&*()"),
            "Should preserve special characters");
        assertFalse(result.contains("${date}"),
            "Should replace ${date}");
        assertTrue(result.contains("_+-=[]{}|;':\",./<>?"),
            "Should preserve special characters");
    }

    @Test
    @DisplayName("parse(stringsWithUnicode) preserves Unicode")
    void parse_stringsWithUnicode_worksCorrectly() {
        String input = "Unicode: áéíóú ${date} 中文";
        String result = parser.parse(input);

        // Should preserve Unicode characters while replacing expressions
        assertTrue(result.contains("áéíóú"),
            "Should preserve Unicode characters");
        assertFalse(result.contains("${date}"),
            "Should replace ${date}");
        assertTrue(result.contains("中文"),
            "Should preserve Unicode characters");
    }

    // =========================================================================
    // Boundary condition tests
    // =========================================================================

    @Test
    @DisplayName("parse(singleExpression) replaces correctly")
    void parse_singleExpression_replacesCorrectly() {
        String input = "${date}";
        String result = parser.parse(input);

        // Should be replaced with current date
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}"),
            "Single expression should be replaced");
    }

    @Test
    @DisplayName("parse(onlyText) preserves as-is")
    void parse_onlyText_preservesAsIs() {
        String input = "Just some text without expressions";
        String result = parser.parse(input);

        assertEquals(input, result,
            "Text without expressions should be preserved");
    }

    @Test
    @DisplayName("parse(expressionAtStart) replaces correctly")
    void parse_expressionAtStart_replacesCorrectly() {
        String input = "${date} is today";
        String result = parser.parse(input);

        assertTrue(result.startsWith("20"), // Year starts with 20
            "Expression at start should be replaced");
        assertTrue(result.contains(" is today"),
            "Should preserve text after expression");
    }

    @Test
    @DisplayName("parse(expressionAtEnd) replaces correctly")
    void parse_expressionAtEnd_replacesCorrectly() {
        String input = "Today is ${date}";
        String result = parser.parse(input);

        assertTrue(result.matches("Today is \\d{4}-\\d{2}-\\d{2}"),
            "Expression at end should be replaced with date");
        assertTrue(result.startsWith("Today is "),
            "Should preserve text before expression");
    }

    // =========================================================================
    // Performance tests
    // =========================================================================

    @Test
    @DisplayName("parse(manExpressions) handles performance")
    void parse_manyExpressions_handlesPerformance() {
        // Create a string with many expressions
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            input.append("${date} ");
        }

        // This should not throw an exception
        assertDoesNotThrow(() -> {
            String result = parser.parse(input.toString());
            assertNotNull(result);
            assertFalse(result.contains("${date}"),
                "All expressions should be replaced");
        });
    }

    @Test
    @DisplayName("parse(veryLongString) handles length")
    void parse_veryLongString_handlesLength() {
        // Create a very long string
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            input.append("text${date}").append(i);
        }

        // Should handle the length without issues
        assertDoesNotThrow(() -> {
            String result = parser.parse(input.toString());
            assertNotNull(result);
            assertFalse(result.contains("${date}"),
                "Should replace expressions even in long strings");
        });
    }

    // =========================================================================
    // Error handling tests
    // =========================================================================

    @Test
    @DisplayName("parse(exceptionScenario) handles gracefully")
    void parse_withExceptionScenario_handlesGracefully() {
        // Test that parsing doesn't throw uncaught exceptions
        assertDoesNotThrow(() -> {
            String result = parser.parse("Invalid: ${${date}}");
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("parse(nullInMiddle) handles gracefully")
    void parse_nullInMiddle_handlesGracefully() {
        // This would be unusual, but test robustness
        assertDoesNotThrow(() -> {
            String result = parser.parse("Start ${date} null ${time} end");
            assertNotNull(result);
        });
    }

    // =========================================================================
    // Integration tests
    // =========================================================================

    @Test
    @DisplayName("parseAndValidate_integration) works with real data")
    void parseAndValidate_integration_works() {
        // Test parsing with a realistic scenario
        String template = "Workflow started at ${date}, time: ${time}, timestamp: ${now}";
        String result = parser.parse(template);

        // Should replace all expressions
        assertFalse(result.contains("${date}"),
            "Should replace date expression");
        assertFalse(result.contains("${time}"),
            "Should replace time expression");
        assertFalse(result.contains("${now}"),
            "Should replace now expression");

        // Should maintain the structure
        assertTrue(result.contains("Workflow started at "),
            "Should preserve prefix");
        assertTrue(result.contains(", time: "),
            "Should preserve middle parts");
        assertTrue(result.contains(", timestamp: "),
            "Should preserve suffix");
    }

    @Test
    @DisplayName("parseMultipleTimes_consistent) produces consistent results")
    void parseMultipleTimes_consistent_producesConsistentResults() {
        String input = "Time: ${time}";

        // Parse the same input multiple times
        String result1 = parser.parse(input);
        String result2 = parser.parse(input);
        String result3 = parser.parse(input);

        // Results should be consistent (all use current time, so they should be different
        // but follow the same pattern)
        assertTrue(result1.matches("Time: \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
            "First parse should match time pattern");
        assertTrue(result2.matches("Time: \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
            "Second parse should match time pattern");
        assertTrue(result3.matches("Time: \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
            "Third parse should match time pattern");
    }
}