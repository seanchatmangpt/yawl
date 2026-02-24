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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for SafeNumberParser utility class.
 * Tests safe number parsing with null/blank handling and exception scenarios.
 *
 * @author YAWL Test Agent
 * @since YAWL 6.0.0
 */
class SafeNumberParserTest {

    // Integer tests
    @Test
    @DisplayName("Should parse valid integer string")
    void parseIntOrDefault_validIntegerString_returnsCorrectValue() {
        int result = SafeNumberParser.parseIntOrDefault("42", 0);

        assertEquals(42, result,
            "Should parse valid integer string correctly");
    }

    @Test
    @DisplayName("Should parse integer with whitespace")
    void parseIntOrDefault_integerWithWhitespace_returnsCorrectValue() {
        int result = SafeNumberParser.parseIntOrDefault("  42  ", 0);

        assertEquals(42, result,
            "Should parse integer with surrounding whitespace");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should return default for null or blank integer string")
    void parseIntOrDefault_nullOrBlank_returnsDefault(String value) {
        int result = SafeNumberParser.parseIntOrDefault(value, 99);

        assertEquals(99, result,
            "Should return default value for null or blank input");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-number", "abc", "123abc", "abc123", "12.34", "1e10", "infinity"
    })
    @DisplayName("Should return default for invalid integer strings")
    void parseIntOrDefault_invalidString_returnsDefault(String value) {
        int result = SafeNumberParser.parseIntOrDefault(value, 99);

        assertEquals(99, result,
            "Should return default for non-integer strings");
    }

    @Test
    @DisplayName("Should parse valid Integer to null")
    void parseIntOrNull_validIntegerString_returnsInteger() {
        Integer result = SafeNumberParser.parseIntOrNull("42");

        assertEquals(42, result,
            "Should parse valid integer string to Integer");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should return null for null or blank integer string")
    void parseIntOrNull_nullOrBlank_returnsNull(String value) {
        Integer result = SafeNumberParser.parseIntOrNull(value);

        assertNull(result,
            "Should return null for null or blank input");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-number", "abc", "123abc", "abc123", "12.34", "1e10", "infinity"
    })
    @DisplayName("Should return null for invalid integer strings")
    void parseIntOrNull_invalidString_returnsNull(String value) {
        Integer result = SafeNumberParser.parseIntOrNull(value);

        assertNull(result,
            "Should return null for non-integer strings");
    }

    @Test
    @DisplayName("Should parse valid integer with or throw")
    void parseIntOrThrow_validIntegerString_returnsCorrectValue() {
        int result = SafeNumberParser.parseIntOrThrow("42", "test context");

        assertEquals(42, result,
            "Should parse valid integer string correctly");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should throw for null or blank integer string")
    void parseIntOrThrow_nullOrBlank_throwsNumberFormatException(String value) {
        NumberFormatException exception = assertThrows(
            NumberFormatException.class,
            () -> SafeNumberParser.parseIntOrThrow(value, "test context")
        );

        assertTrue(exception.getMessage().contains("value is null or blank"),
            "Exception should indicate null or blank value");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-number", "abc", "123abc", "abc123", "12.34", "1e10", "infinity"
    })
    @DisplayName("Should throw for invalid integer strings")
    void parseIntOrThrow_invalidString_throwsNumberFormatException(String value) {
        NumberFormatException exception = assertThrows(
            NumberFormatException.class,
            () -> SafeNumberParser.parseIntOrThrow(value, "test context")
        );

        assertTrue(exception.getMessage().contains("is not a valid integer"),
            "Exception should indicate invalid integer format");
    }

    // Long tests
    @Test
    @DisplayName("Should parse valid long string")
    void parseLongOrDefault_validLongString_returnsCorrectValue() {
        long result = SafeNumberParser.parseLongOrDefault("42", 0L);

        assertEquals(42L, result,
            "Should parse valid long string correctly");
    }

    @Test
    @DisplayName("Should parse long with whitespace")
    void parseLongOrDefault_longWithWhitespace_returnsCorrectValue() {
        long result = SafeNumberParser.parseLongOrDefault("  42  ", 0L);

        assertEquals(42L, result,
            "Should parse long with surrounding whitespace");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should return default for null or blank long string")
    void parseLongOrDefault_nullOrBlank_returnsDefault(String value) {
        long result = SafeNumberParser.parseLongOrDefault(value, 99L);

        assertEquals(99L, result,
            "Should return default value for null or blank input");
    }

    @Test
    @DisplayName("Should parse valid Long to null")
    void parseLongOrNull_validLongString_returnsLong() {
        Long result = SafeNumberParser.parseLongOrNull("42");

        assertEquals(42L, result,
            "Should parse valid long string to Long");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should return null for null or blank long string")
    void parseLongOrNull_nullOrBlank_returnsNull(String value) {
        Long result = SafeNumberParser.parseLongOrNull(value);

        assertNull(result,
            "Should return null for null or blank input");
    }

    @Test
    @DisplayName("Should parse valid long with or throw")
    void parseLongOrThrow_validLongString_returnsCorrectValue() {
        long result = SafeNumberParser.parseLongOrThrow("42", "test context");

        assertEquals(42L, result,
            "Should parse valid long string correctly");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should throw for null or blank long string")
    void parseLongOrThrow_nullOrBlank_throwsNumberFormatException(String value) {
        NumberFormatException exception = assertThrows(
            NumberFormatException.class,
            () -> SafeNumberParser.parseLongOrThrow(value, "test context")
        );

        assertTrue(exception.getMessage().contains("value is null or blank"),
            "Exception should indicate null or blank value");
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0",
        "1, 1",
        "-1, -1",
        "2147483647, 2147483647", // Integer.MAX_VALUE
        "-2147483648, -2147483648", // Integer.MIN_VALUE
        "2147483648, 2147483648", // Above Integer.MAX_VALUE
        "-2147483649, -2147483649" // Below Integer.MIN_VALUE
    })
    @DisplayName("Should parse edge case long values correctly")
    void parseLongOrThrow_edgeCases_returnsCorrectValue(String input, long expected) {
        long result = SafeNumberParser.parseLongOrThrow(input, "test context");

        assertEquals(expected, result,
            "Should parse edge case long value correctly");
    }

    // Double tests
    @Test
    @DisplayName("Should parse valid double string")
    void parseDoubleOrDefault_validDoubleString_returnsCorrectValue() {
        double result = SafeNumberParser.parseDoubleOrDefault("42.5", 0.0);

        assertEquals(42.5, result, 0.001,
            "Should parse valid double string correctly");
    }

    @Test
    @DisplayName("Should parse double with whitespace")
    void parseDoubleOrDefault_doubleWithWhitespace_returnsCorrectValue() {
        double result = SafeNumberParser.parseDoubleOrDefault("  42.5  ", 0.0);

        assertEquals(42.5, result, 0.001,
            "Should parse double with surrounding whitespace");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should return default for null or blank double string")
    void parseDoubleOrDefault_nullOrBlank_returnsDefault(String value) {
        double result = SafeNumberParser.parseDoubleOrDefault(value, 99.0);

        assertEquals(99.0, result, 0.001,
            "Should return default value for null or blank input");
    }

    @Test
    @DisplayName("Should parse valid Double to null")
    void parseDoubleOrNull_validDoubleString_returnsDouble() {
        Double result = SafeNumberParser.parseDoubleOrNull("42.5");

        assertEquals(42.5, result, 0.001,
            "Should parse valid double string to Double");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should return null for null or blank double string")
    void parseDoubleOrNull_nullOrBlank_returnsNull(String value) {
        Double result = SafeNumberParser.parseDoubleOrNull(value);

        assertNull(result,
            "Should return null for null or blank input");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-number", "abc", "123abc", "12.34.56", "1e10", "infinity", "NaN"
    })
    @DisplayName("Should return null for invalid double strings")
    void parseDoubleOrNull_invalidString_returnsNull(String value) {
        Double result = SafeNumberParser.parseDoubleOrNull(value);

        assertNull(result,
            "Should return null for non-double strings");
    }

    @Test
    @DisplayName("Should parse valid double with or throw")
    void parseDoubleOrThrow_validDoubleString_returnsCorrectValue() {
        double result = SafeNumberParser.parseDoubleOrThrow("42.5", "test context");

        assertEquals(42.5, result, 0.001,
            "Should parse valid double string correctly");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should throw for null or blank double string")
    void parseDoubleOrThrow_nullOrBlank_throwsNumberFormatException(String value) {
        NumberFormatException exception = assertThrows(
            NumberFormatException.class,
            () -> SafeNumberParser.parseDoubleOrThrow(value, "test context")
        );

        assertTrue(exception.getMessage().contains("value is null or blank"),
            "Exception should indicate null or blank value");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-number", "abc", "123abc", "12.34.56", "1e10", "infinity", "NaN"
    })
    @DisplayName("Should throw for invalid double strings")
    void parseDoubleOrThrow_invalidString_throwsNumberFormatException(String value) {
        NumberFormatException exception = assertThrows(
            NumberFormatException.class,
            () -> SafeNumberParser.parseDoubleOrThrow(value, "test context")
        );

        assertTrue(exception.getMessage().contains("is not a valid double"),
            "Exception should indicate invalid double format");
    }

    // BigDecimal tests
    @Test
    @DisplayName("Should parse valid BigDecimal string")
    void parseBigDecimalOrDefault_validDecimalString_returnsCorrectValue() {
        BigDecimal result = SafeNumberParser.parseBigDecimalOrDefault("42.5", BigDecimal.ZERO);

        assertEquals(new BigDecimal("42.5"), result,
            "Should parse valid decimal string correctly");
    }

    @Test
    @DisplayName("Should parse decimal with whitespace")
    void parseBigDecimalOrDefault_decimalWithWhitespace_returnsCorrectValue() {
        BigDecimal result = SafeNumberParser.parseBigDecimalOrDefault("  42.5  ", BigDecimal.ZERO);

        assertEquals(new BigDecimal("42.5"), result,
            "Should parse decimal with surrounding whitespace");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should return default for null or blank decimal string")
    void parseBigDecimalOrDefault_nullOrBlank_returnsDefault(String value) {
        BigDecimal result = SafeNumberParser.parseBigDecimalOrDefault(value, BigDecimal.TEN);

        assertEquals(BigDecimal.TEN, result,
            "Should return default value for null or blank input");
    }

    @Test
    @DisplayName("Should parse valid BigDecimal to null")
    void parseBigDecimalOrNull_validDecimalString_returnsBigDecimal() {
        BigDecimal result = SafeNumberParser.parseBigDecimalOrNull("42.5");

        assertEquals(new BigDecimal("42.5"), result,
            "Should parse valid decimal string to BigDecimal");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should return null for null or blank decimal string")
    void parseBigDecimalOrNull_nullOrBlank_returnsNull(String value) {
        BigDecimal result = SafeNumberParser.parseBigDecimalOrNull(value);

        assertNull(result,
            "Should return null for null or blank input");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-number", "abc", "123abc", "12.34.56", "1e10", "infinity", "NaN",
        "12345678901234567890.1234567890" // Very large number
    })
    @DisplayName("Should return null for invalid decimal strings")
    void parseBigDecimalOrNull_invalidString_returnsNull(String value) {
        BigDecimal result = SafeNumberParser.parseBigDecimalOrNull(value);

        assertNull(result,
            "Should return null for non-decimal strings");
    }

    @Test
    @DisplayName("Should parse valid decimal with or throw")
    void parseBigDecimalOrThrow_validDecimalString_returnsCorrectValue() {
        BigDecimal result = SafeNumberParser.parseBigDecimalOrThrow("42.5", "test context");

        assertEquals(new BigDecimal("42.5"), result,
            "Should parse valid decimal string correctly");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n"})
    @DisplayName("Should throw for null or blank decimal string")
    void parseBigDecimalOrThrow_nullOrBlank_throwsNumberFormatException(String value) {
        NumberFormatException exception = assertThrows(
            NumberFormatException.class,
            () -> SafeNumberParser.parseBigDecimalOrThrow(value, "test context")
        );

        assertTrue(exception.getMessage().contains("value is null or blank"),
            "Exception should indicate null or blank value");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-number", "abc", "123abc", "12.34.56", "1e10", "infinity", "NaN"
    })
    @DisplayName("Should throw for invalid decimal strings")
    void parseBigDecimalOrThrow_invalidString_throwsNumberFormatException(String value) {
        NumberFormatException exception = assertThrows(
            NumberFormatException.class,
            () -> SafeNumberParser.parseBigDecimalOrThrow(value, "test context")
        );

        assertTrue(exception.getMessage().contains("is not a valid decimal number"),
            "Exception should indicate invalid decimal format");
    }

    // Boundary value tests
    @ParameterizedTest
    @CsvSource({
        "Integer.MIN_VALUE, -2147483648",
        "Integer.MAX_VALUE, 2147483647",
        "Long.MIN_VALUE, -9223372036854775808",
        "Long.MAX_VALUE, 9223372036854775807",
        "Double.MIN_VALUE, 4.9E-324",
        "Double.MAX_VALUE, 1.7976931348623157E308",
        "Double.MIN_NORMAL, 2.2250738585072014E-308"
    })
    @DisplayName("Should parse boundary values correctly")
    void parseOrDefault_boundaryValues_returnsCorrectValue(String type, String input) {
        switch (type) {
            case "Integer.MIN_VALUE":
            case "Integer.MAX_VALUE":
                int intResult = SafeNumberParser.parseIntOrDefault(input, 0);
                assertEquals(Integer.parseInt(input), intResult);
                break;
            case "Long.MIN_VALUE":
            case "Long.MAX_VALUE":
                long longResult = SafeNumberParser.parseLongOrDefault(input, 0L);
                assertEquals(Long.parseLong(input), longResult);
                break;
            case "Double.MIN_VALUE":
            case "Double.MAX_VALUE":
            case "Double.MIN_NORMAL":
                double doubleResult = SafeNumberParser.parseDoubleOrDefault(input, 0.0);
                assertEquals(Double.parseDouble(input), doubleResult, 0.0);
                break;
        }
    }

    // Scientific notation tests
    @ParameterizedTest
    @CsvSource({
        "1e10, 10000000000.0",
        "1E-5, 0.00001",
        "123.456e3, 123456.0",
        "123.456E-3, 0.123456"
    })
    @DisplayName("Should parse scientific notation correctly")
    void parseOrDefault_scientificNotation_returnsCorrectValue(String input, double expected) {
        double result = SafeNumberParser.parseDoubleOrDefault(input, 0.0);

        assertEquals(expected, result, 0.000001,
            "Should parse scientific notation correctly");
    }

    // Error context tests
    @Test
    @DisplayName("Should include error context in exception message")
    void parseOrThrow_includesContextInErrorMessage() {
        NumberFormatException exception = assertThrows(
            NumberFormatException.class,
            () -> SafeNumberParser.parseIntOrThrow("invalid", "user ID")
        );

        assertTrue(exception.getMessage().contains("user ID"),
            "Exception message should include error context");
        assertTrue(exception.getMessage().contains("invalid\" is not a valid integer"),
            "Exception message should include original value");
    }

    // Trailing whitespace tests
    @ParameterizedTest
    @ValueSource(strings = {"42", "42.5", "123.456"})
    @DisplayName("Should handle trailing whitespace correctly")
    void parseOrDefault_trailingWhitespace_returnsCorrectValue(String value) {
        int result = SafeNumberParser.parseIntOrDefault(value + "   ", 0);
        assertEquals(Integer.parseInt(value), result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"42", "42.5", "123.456"})
    @DisplayName("Should handle leading whitespace correctly")
    void parseOrDefault_leadingWhitespace_returnsCorrectValue(String value) {
        int result = SafeNumberParser.parseIntOrDefault("   " + value, 0);
        assertEquals(Integer.parseInt(value), result);
    }

    @Test
    @DisplayName("Should handle null BigDecimal default")
    void parseOrDefault_withNullDefault_returnsParsedValue() {
        BigDecimal result = SafeNumberParser.parseBigDecimalOrDefault("42.5", null);

        assertEquals(new BigDecimal("42.5"), result,
            "Should return parsed value even with null default");
    }
}