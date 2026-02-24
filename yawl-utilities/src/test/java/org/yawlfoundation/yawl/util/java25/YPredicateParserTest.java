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
 * Comprehensive test suite for YPredicateParser utility class.
 * Tests string parsing with embedded expressions and value substitution.
 *
 * @author YAWL Test Agent
 * @since YAWL 6.0.0
 */
class YPredicateParserTest {

    private YPredicateParser parser;

    @BeforeEach
    void setUp() {
        parser = new YPredicateParser();
    }

    @Test
    @DisplayName("Should return original string when no expressions present")
    void parse_noExpressions_returnsOriginalString() {
        String input = "Hello World";
        String result = parser.parse(input);

        assertEquals(input, result,
            "Should return original string when no expressions are present");
    }

    @Test
    @DisplayName("Should return null input when string is null")
    void parse_nullInput_returnsNull() {
        String result = parser.parse(null);

        assertNull(result,
            "Should return null when input string is null");
    }

    @Test
    @DisplayName("Should return empty input when string is empty")
    void parse_emptyInput_returnsEmptyString() {
        String result = parser.parse("");

        assertEquals("", result,
            "Should return empty string when input is empty");
    }

    @Test
    @DisplayName("Should parse ${now} expression")
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
    @DisplayName("Should parse ${date} expression")
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
    @DisplayName("Should parse ${time} expression")
    void parse_timeExpression_replacesWithCurrentTime() {
        String input = "The time is ${time}";
        String result = parser.parse(input);

        // Should contain the time pattern
        assertTrue(result.matches("The time is \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
            "Should replace ${time} with current time");
        assertFalse(result.contains("${time}"),
            "Should not contain the original ${time} expression");
    }

    @Test
    @DisplayName("Should parse multiple expressions in the same string")
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
    @DisplayName("Should handle expressions with text around them")
    void parse_expressionsWithText_worksCorrectly() {
        String input = "Status: ${date} ${time} - Current: ${now}";
        String result = parser.parse(input);

        assertTrue(result.startsWith("Status: "),
            "Should preserve prefix");
        assertTrue(result.endsWith(" - Current: " + result.substring(result.lastIndexOf(" - Current: ") + 13)),
            "Should preserve suffix");
    }

    @Test
    @DisplayName("Should handle consecutive expressions")
    void parse_consecutiveExpressions_worksCorrectly() {
        String input = "${date}${time}${now}";
        String result = parser.parse(input);

        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}"),
            "Should handle consecutive expressions without separators");
    }

    @Test
    @DisplayName("Should handle non-expressions that look like expressions")
    void parse_nonExpressionLiterals_preservesAsIs() {
        String input = "This is not an expression: \\${date} or ${notreal}";
        String result = parser.parse(input);

        // \\${date} should remain as-is (escaped)
        // ${notreal} should remain as-is (unrecognized)
        assertTrue(result.contains("\\${date}"),
            "Should preserve escaped expressions");
        assertTrue(result.contains("${notreal}"),
            "Should preserve unrecognized expressions");
    }

    @Test
    @DisplayName("Should handle malformed expressions")
    void parse_malformedExpressions_preservesAsIs() {
        String input = "Malformed: ${date ${time}} ${now}";
        String result = parser.parse(input);

        // Malformed expressions should be preserved
        assertTrue(result.contains("${date ${time}}"),
            "Should preserve malformed expressions");
    }

    @Test
    @DisplayName("Should handle nested expressions")
    void parse_nestedExpressions_preservesAsIs() {
        String input = "Nested: ${date ${time}} ${now}";
        String result = parser.parse(input);

        // Nested expressions should be preserved
        assertTrue(result.contains("${date ${time}}"),
            "Should preserve nested expressions");
    }

    @Test
    @DisplayName("Should handle empty expressions")
    void parse_emptyExpressions_replacesWithN_a() {
        String input = "Empty: ${}";
        String result = parser.parse(input);

        assertEquals("Empty: n/a", result,
            "Empty expressions should be replaced with 'n/a'");
    }

    @Test
    @DisplayName("Should handle whitespace around expressions")
    void parse_whitespaceAroundExpressions_worksCorrectly() {
        String input = "  ${date}  ${time}  ${now}  ";
        String result = parser.parse(input);

        // Should preserve whitespace while replacing expressions
        assertTrue(result.startsWith("  "),
            "Should preserve leading whitespace");
        assertTrue(result.endsWith("  "),
            "Should preserve trailing whitespace");
    }

    @Test
    @DisplayName("Should handle single character text around expressions")
    void parse_singleCharacterText_worksCorrectly() {
        String input = "a${date}b${time}c${now}d";
        String result = parser.parse(input);

        assertTrue(result.startsWith("a"),
            "Should preserve single character prefix");
        assertTrue(result.endsWith("d"),
            "Should preserve single character suffix");
    }

    @Test
    @DisplayName("Should handle very long strings")
    void parse_longString_worksCorrectly() {
        StringBuilder longInput = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            longInput.append("Text ").append(i).append(" ${date} ");
        }

        String result = parser.parse(longInput.toString());

        // Should handle the length without issues
        assertTrue(result.length() > 0,
            "Should handle long strings without errors");
        assertFalse(result.contains("${date}"),
            "Should replace all ${date} expressions even in long strings");
    }

    // Test with custom map data (would normally require SaxonUtil, but we'll test the logic)
    @Test
    @DisplayName("Should handle string with no expressions containing map syntax")
    void parse_noExpressionsWithMapSyntax_preservesAsIs() {
        String input = "Normal text with: key=value but no expressions";
        String result = parser.parse(input);

        assertEquals(input, result,
            "Should preserve text that looks like map syntax but has no expressions");
    }

    @Test
    @DisplayName("Should handle mixed case expressions")
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

    @Test
    @DisplayName("Should handle expressions with extra spaces")
    void parse_expressionsWithExtraSpaces_preservesAsIs() {
        String input = "Spaced: ${ date } ${ time } ${ now }";
        String result = parser.parse(input);

        // Expressions with extra spaces should not be recognized
        assertTrue(result.contains("${ date }"),
            "Should preserve expressions with extra spaces");
        assertTrue(result.contains("${ time }"),
            "Should preserve expressions with extra spaces");
        assertTrue(result.contains("${ now }"),
            "Should preserve expressions with extra spaces");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "  ", "\t", "\n", "\r\n"})
    @DisplayName("Should handle whitespace-only strings correctly")
    void parse_whitespaceOnlyStrings_preservesAsIs(String input) {
        String result = parser.parse(input);

        assertEquals(input, result,
            "Should preserve whitespace-only strings as-is");
    }

    @Test
    @DisplayName("Should handle strings with only expressions")
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
    @DisplayName("Should handle strings with repeated expressions")
    void parse_repeatedExpressions_replacesAll() {
        String input = "Date: ${date} Time: ${time} Date: ${date}";
        String result = parser.parse(input);

        // All occurrences should be replaced
        assertEquals(1, result.split("Date: ").length - 1,
            "Should have replaced all ${date} expressions");
    }

    @Test
    @DisplayName("Should handle expressions with numbers")
    void parse_expressionsWithNumbers_worksCorrectly() {
        String input = "Date1: ${date} Date2: ${date}";
        String result = parser.parse(input);

        // Both expressions should be replaced
        assertFalse(result.contains("${date}"),
            "Should replace all ${date} expressions");
    }

    @Test
    @DisplayName("Should handle strings with newlines")
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
    @DisplayName("Should handle strings with tabs")
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
    @DisplayName("Should handle strings with special characters")
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
    @DisplayName("Should handle strings with Unicode characters")
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

    @Test
    @DisplayName("Should handle edge case with single expression")
    void parse_singleExpression_replacesCorrectly() {
        String input = "${date}";
        String result = parser.parse(input);

        // Should be replaced with current date
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}"),
            "Single expression should be replaced");
    }

    @Test
    @DisplayName("Should handle edge case with only text")
    void parse_onlyText_preservesAsIs() {
        String input = "Just some text without expressions";
        String result = parser.parse(input);

        assertEquals(input, result,
            "Text without expressions should be preserved");
    }

    @Test
    @DisplayName("Should handle expression at start of string")
    void parse_expressionAtStart_replacesCorrectly() {
        String input = "${date} is today";
        String result = parser.parse(input);

        assertTrue(result.startsWith("20"), // Year starts with 20
            "Expression at start should be replaced");
        assertTrue(result.contains(" is today"),
            "Should preserve text after expression");
    }

    @Test
    @DisplayName("Should handle expression at end of string")
    void parse_expressionAtEnd_replacesCorrectly() {
        String input = "Today is ${date}";
        String result = parser.parse(input);

        assertTrue(result.endsWith("-"), // Date ends with month-day
            "Expression at end should be replaced");
        assertTrue(result.startsWith("Today is "),
            "Should preserve text before expression");
    }

    @Test
    @DisplayName("Should handle multiple same type expressions")
    void parse_multipleSameTypeExpressions_replacesAll() {
        String input = "Date1: ${date} Date2: ${date} Date3: ${date}";
        String result = parser.parse(input);

        // All expressions should be replaced
        assertEquals(0, result.split("\\${date}").length - 1,
            "Should replace all ${date} expressions");
    }

    @Test
    @DisplayName("Should handle performance with many expressions")
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
    @DisplayName("Should handle edge case with escaped backslash")
    void parse_escapedBackslash_preservesCorrectly() {
        String input = "Escaped: \\${date} not an expression";
        String result = parser.parse(input);

        // The \\${date} should remain as-is
        assertTrue(result.contains("\\${date}"),
            "Should preserve escaped expressions");
    }

    @Test
    @DisplayName("Should handle mixed valid and invalid expressions")
    void parse_mixedExpressions_worksCorrectly() {
        String input = "Valid: ${date} Invalid: ${fake} Valid: ${time}";
        String result = parser.parse(input);

        // Should replace valid expressions but preserve invalid ones
        assertFalse(result.contains("${date}"),
            "Should replace valid ${date} expression");
        assertTrue(result.contains("${fake}"),
            "Should preserve invalid ${fake} expression");
        assertFalse(result.contains("${time}"),
            "Should replace valid ${time} expression");
    }
}