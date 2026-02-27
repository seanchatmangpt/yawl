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

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SafeNumberParser}.
 *
 * Covers: null input, blank input, whitespace-padded input, valid values, overflow,
 * underflow, leading/trailing garbage, and all three behavioural contracts
 * (OrDefault, OrNull, OrThrow) for each numeric type (int, long, double, BigDecimal).
 */
@Tag("unit")
class TestSafeNumberParser {

    // -------------------------------------------------------------------------
    // Utility: instantiation must be prevented
    // -------------------------------------------------------------------------

    @Test
    void instantiation_throwsUnsupportedOperationException() {
        try {
            var ctor = SafeNumberParser.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            assertThrows(Exception.class, ctor::newInstance,
                    "SafeNumberParser must not be instantiatable");
        } catch (NoSuchMethodException e) {
            fail("SafeNumberParser must have a private no-arg constructor");
        }
    }

    // =========================================================================
    // Integer
    // =========================================================================

    // --- parseIntOrDefault ---

    @Test
    void parseIntOrDefault_validInteger_returnsValue() {
        assertEquals(42, SafeNumberParser.parseIntOrDefault("42", -1));
    }

    @Test
    void parseIntOrDefault_negativeInteger_returnsValue() {
        assertEquals(-7, SafeNumberParser.parseIntOrDefault("-7", 0));
    }

    @Test
    void parseIntOrDefault_whitespace_padded_returnsValue() {
        assertEquals(100, SafeNumberParser.parseIntOrDefault("  100  ", 0));
    }

    @Test
    void parseIntOrDefault_nullInput_returnsDefault() {
        assertEquals(-1, SafeNumberParser.parseIntOrDefault(null, -1));
    }

    @Test
    void parseIntOrDefault_blankInput_returnsDefault() {
        assertEquals(-1, SafeNumberParser.parseIntOrDefault("   ", -1));
    }

    @Test
    void parseIntOrDefault_emptyString_returnsDefault() {
        assertEquals(0, SafeNumberParser.parseIntOrDefault("", 0));
    }

    @Test
    void parseIntOrDefault_alpha_returnsDefault() {
        assertEquals(99, SafeNumberParser.parseIntOrDefault("abc", 99));
    }

    @Test
    void parseIntOrDefault_float_string_returnsDefault() {
        assertEquals(0, SafeNumberParser.parseIntOrDefault("3.14", 0));
    }

    @Test
    void parseIntOrDefault_overflow_returnsDefault() {
        assertEquals(-1, SafeNumberParser.parseIntOrDefault("99999999999", -1));
    }

    @Test
    void parseIntOrDefault_intMinValue_returnsValue() {
        assertEquals(Integer.MIN_VALUE,
                SafeNumberParser.parseIntOrDefault(String.valueOf(Integer.MIN_VALUE), 0));
    }

    @Test
    void parseIntOrDefault_intMaxValue_returnsValue() {
        assertEquals(Integer.MAX_VALUE,
                SafeNumberParser.parseIntOrDefault(String.valueOf(Integer.MAX_VALUE), 0));
    }

    // --- parseIntOrNull ---

    @Test
    void parseIntOrNull_validInteger_returnsBoxedValue() {
        assertEquals(Integer.valueOf(7), SafeNumberParser.parseIntOrNull("7"));
    }

    @Test
    void parseIntOrNull_nullInput_returnsNull() {
        assertNull(SafeNumberParser.parseIntOrNull(null));
    }

    @Test
    void parseIntOrNull_blankInput_returnsNull() {
        assertNull(SafeNumberParser.parseIntOrNull("  "));
    }

    @Test
    void parseIntOrNull_nonNumeric_returnsNull() {
        assertNull(SafeNumberParser.parseIntOrNull("not-a-number"));
    }

    @Test
    void parseIntOrNull_overflow_returnsNull() {
        assertNull(SafeNumberParser.parseIntOrNull("2147483648")); // MAX_VALUE + 1
    }

    // --- parseIntOrThrow ---

    @Test
    void parseIntOrThrow_validInteger_returnsValue() {
        assertEquals(1234, SafeNumberParser.parseIntOrThrow("1234", "test context"));
    }

    @Test
    void parseIntOrThrow_whitespace_padded_returnsValue() {
        assertEquals(-50, SafeNumberParser.parseIntOrThrow("  -50 ", "test context"));
    }

    @Test
    void parseIntOrThrow_nullInput_throwsWithContext() {
        NumberFormatException ex = assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseIntOrThrow(null, "null port test"));
        assertTrue(ex.getMessage().contains("null port test"),
                "Exception message must include the error context");
    }

    @Test
    void parseIntOrThrow_blankInput_throwsWithContext() {
        NumberFormatException ex = assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseIntOrThrow("", "blank param test"));
        assertTrue(ex.getMessage().contains("blank param test"));
    }

    @Test
    void parseIntOrThrow_invalidInput_throwsWithContextAndValue() {
        NumberFormatException ex = assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseIntOrThrow("xyz", "A2A_PORT env var"));
        assertTrue(ex.getMessage().contains("A2A_PORT env var"),
                "Exception message must include the error context");
        assertTrue(ex.getMessage().contains("xyz"),
                "Exception message must include the offending value");
    }

    @Test
    void parseIntOrThrow_overflow_throwsWithContext() {
        assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseIntOrThrow("9999999999", "overflow test"));
    }

    // =========================================================================
    // Long
    // =========================================================================

    // --- parseLongOrDefault ---

    @Test
    void parseLongOrDefault_validLong_returnsValue() {
        assertEquals(1_000_000_000_000L,
                SafeNumberParser.parseLongOrDefault("1000000000000", -1L));
    }

    @Test
    void parseLongOrDefault_nullInput_returnsDefault() {
        assertEquals(-1L, SafeNumberParser.parseLongOrDefault(null, -1L));
    }

    @Test
    void parseLongOrDefault_blankInput_returnsDefault() {
        assertEquals(0L, SafeNumberParser.parseLongOrDefault("  ", 0L));
    }

    @Test
    void parseLongOrDefault_nonNumeric_returnsDefault() {
        assertEquals(42L, SafeNumberParser.parseLongOrDefault("abc", 42L));
    }

    @Test
    void parseLongOrDefault_longMinValue_returnsValue() {
        assertEquals(Long.MIN_VALUE,
                SafeNumberParser.parseLongOrDefault(String.valueOf(Long.MIN_VALUE), 0L));
    }

    @Test
    void parseLongOrDefault_longMaxValue_returnsValue() {
        assertEquals(Long.MAX_VALUE,
                SafeNumberParser.parseLongOrDefault(String.valueOf(Long.MAX_VALUE), 0L));
    }

    @Test
    void parseLongOrDefault_overflow_returnsDefault() {
        assertEquals(-1L, SafeNumberParser.parseLongOrDefault("99999999999999999999", -1L));
    }

    // --- parseLongOrNull ---

    @Test
    void parseLongOrNull_validLong_returnsBoxedValue() {
        assertEquals(Long.valueOf(1234567890123L),
                SafeNumberParser.parseLongOrNull("1234567890123"));
    }

    @Test
    void parseLongOrNull_nullInput_returnsNull() {
        assertNull(SafeNumberParser.parseLongOrNull(null));
    }

    @Test
    void parseLongOrNull_blankInput_returnsNull() {
        assertNull(SafeNumberParser.parseLongOrNull(""));
    }

    @Test
    void parseLongOrNull_nonNumeric_returnsNull() {
        assertNull(SafeNumberParser.parseLongOrNull("1.5e10"));
    }

    // --- parseLongOrThrow ---

    @Test
    void parseLongOrThrow_validLong_returnsValue() {
        assertEquals(9876543210L,
                SafeNumberParser.parseLongOrThrow("9876543210", "timer expiry test"));
    }

    @Test
    void parseLongOrThrow_nullInput_throwsWithContext() {
        NumberFormatException ex = assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseLongOrThrow(null, "epoch ms field"));
        assertTrue(ex.getMessage().contains("epoch ms field"));
    }

    @Test
    void parseLongOrThrow_invalidInput_throwsWithContextAndValue() {
        NumberFormatException ex = assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseLongOrThrow("bad-timestamp", "timer ticks in spec XML"));
        assertTrue(ex.getMessage().contains("timer ticks in spec XML"));
        assertTrue(ex.getMessage().contains("bad-timestamp"));
    }

    @Test
    void parseLongOrThrow_whitespace_padded_returnsValue() {
        assertEquals(100L, SafeNumberParser.parseLongOrThrow("  100  ", "padded test"));
    }

    // =========================================================================
    // Double
    // =========================================================================

    // --- parseDoubleOrDefault ---

    @Test
    void parseDoubleOrDefault_validDouble_returnsValue() {
        assertEquals(3.14, SafeNumberParser.parseDoubleOrDefault("3.14", 0.0), 1e-10);
    }

    @Test
    void parseDoubleOrDefault_validNegative_returnsValue() {
        assertEquals(-2.5, SafeNumberParser.parseDoubleOrDefault("-2.5", 0.0), 1e-10);
    }

    @Test
    void parseDoubleOrDefault_nullInput_returnsDefault() {
        assertEquals(0.0, SafeNumberParser.parseDoubleOrDefault(null, 0.0), 1e-10);
    }

    @Test
    void parseDoubleOrDefault_blankInput_returnsDefault() {
        assertEquals(-1.0, SafeNumberParser.parseDoubleOrDefault("   ", -1.0), 1e-10);
    }

    @Test
    void parseDoubleOrDefault_nonNumeric_returnsDefault() {
        assertEquals(1.0, SafeNumberParser.parseDoubleOrDefault("not-a-double", 1.0), 1e-10);
    }

    @Test
    void parseDoubleOrDefault_scientificNotation_returnsValue() {
        assertEquals(1.5e10, SafeNumberParser.parseDoubleOrDefault("1.5e10", 0.0), 1.0);
    }

    // --- parseDoubleOrNull ---

    @Test
    void parseDoubleOrNull_validDouble_returnsBoxedValue() {
        assertNotNull(SafeNumberParser.parseDoubleOrNull("2.718"));
        assertEquals(2.718, SafeNumberParser.parseDoubleOrNull("2.718"), 1e-10);
    }

    @Test
    void parseDoubleOrNull_nullInput_returnsNull() {
        assertNull(SafeNumberParser.parseDoubleOrNull(null));
    }

    @Test
    void parseDoubleOrNull_nonNumeric_returnsNull() {
        assertNull(SafeNumberParser.parseDoubleOrNull("abc"));
    }

    // --- parseDoubleOrThrow ---

    @Test
    void parseDoubleOrThrow_validDouble_returnsValue() {
        assertEquals(0.5, SafeNumberParser.parseDoubleOrThrow("0.5", "WSIF double param"), 1e-10);
    }

    @Test
    void parseDoubleOrThrow_nullInput_throwsWithContext() {
        NumberFormatException ex = assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseDoubleOrThrow(null, "WSIF double param \"x\""));
        assertTrue(ex.getMessage().contains("WSIF double param"));
    }

    @Test
    void parseDoubleOrThrow_invalidInput_throwsWithContextAndValue() {
        NumberFormatException ex = assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseDoubleOrThrow("NaN_value", "screen width"));
        assertTrue(ex.getMessage().contains("screen width"));
        assertTrue(ex.getMessage().contains("NaN_value"));
    }

    // =========================================================================
    // BigDecimal
    // =========================================================================

    // --- parseBigDecimalOrDefault ---

    @Test
    void parseBigDecimalOrDefault_validDecimal_returnsValue() {
        assertEquals(new BigDecimal("123.456"),
                SafeNumberParser.parseBigDecimalOrDefault("123.456", BigDecimal.ZERO));
    }

    @Test
    void parseBigDecimalOrDefault_highPrecision_returnsExactValue() {
        BigDecimal expected = new BigDecimal("1.23456789012345678901234567890");
        assertEquals(expected,
                SafeNumberParser.parseBigDecimalOrDefault(
                        "1.23456789012345678901234567890", BigDecimal.ZERO));
    }

    @Test
    void parseBigDecimalOrDefault_nullInput_returnsDefault() {
        assertEquals(BigDecimal.ONE,
                SafeNumberParser.parseBigDecimalOrDefault(null, BigDecimal.ONE));
    }

    @Test
    void parseBigDecimalOrDefault_blankInput_returnsDefault() {
        assertEquals(BigDecimal.ZERO,
                SafeNumberParser.parseBigDecimalOrDefault("  ", BigDecimal.ZERO));
    }

    @Test
    void parseBigDecimalOrDefault_nonNumeric_returnsDefault() {
        assertEquals(BigDecimal.TEN,
                SafeNumberParser.parseBigDecimalOrDefault("bad", BigDecimal.TEN));
    }

    // --- parseBigDecimalOrNull ---

    @Test
    void parseBigDecimalOrNull_validDecimal_returnsValue() {
        assertNotNull(SafeNumberParser.parseBigDecimalOrNull("99.99"));
        assertEquals(new BigDecimal("99.99"), SafeNumberParser.parseBigDecimalOrNull("99.99"));
    }

    @Test
    void parseBigDecimalOrNull_nullInput_returnsNull() {
        assertNull(SafeNumberParser.parseBigDecimalOrNull(null));
    }

    @Test
    void parseBigDecimalOrNull_nonNumeric_returnsNull() {
        assertNull(SafeNumberParser.parseBigDecimalOrNull("not-a-decimal"));
    }

    @Test
    void parseBigDecimalOrNull_whitespace_padded_returnsValue() {
        assertEquals(new BigDecimal("7"),
                SafeNumberParser.parseBigDecimalOrNull("  7  "));
    }

    // --- parseBigDecimalOrThrow ---

    @Test
    void parseBigDecimalOrThrow_validDecimal_returnsValue() {
        assertEquals(new BigDecimal("0.001"),
                SafeNumberParser.parseBigDecimalOrThrow("0.001", "fee amount"));
    }

    @Test
    void parseBigDecimalOrThrow_nullInput_throwsWithContext() {
        NumberFormatException ex = assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseBigDecimalOrThrow(null, "order total"));
        assertTrue(ex.getMessage().contains("order total"));
    }

    @Test
    void parseBigDecimalOrThrow_invalidInput_throwsWithContextAndValue() {
        NumberFormatException ex = assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseBigDecimalOrThrow("$1,000", "price field"));
        assertTrue(ex.getMessage().contains("price field"));
        assertTrue(ex.getMessage().contains("$1,000"));
    }

    // =========================================================================
    // Cross-cutting edge cases
    // =========================================================================

    @Test
    void allTypes_tabCharacter_treatedAsBlankOrDefault() {
        // \t is whitespace, isBlank() returns true → default/null/throw as appropriate
        assertEquals(0, SafeNumberParser.parseIntOrDefault("\t", 0));
        assertNull(SafeNumberParser.parseIntOrNull("\t"));
        assertThrows(NumberFormatException.class,
                () -> SafeNumberParser.parseIntOrThrow("\t", "tab context"));
    }

    @Test
    void allTypes_newlineCharacter_treatedAsBlank() {
        assertEquals(0L, SafeNumberParser.parseLongOrDefault("\n", 0L));
        assertNull(SafeNumberParser.parseLongOrNull("\n"));
    }

    @Test
    void parseIntOrThrow_leadingPluSign_throwsOrParses() {
        // Java's Integer.parseInt does NOT accept leading '+' before Java 7 but does from Java 7+
        // This test documents the actual behavior (should parse on Java 25):
        assertDoesNotThrow(() -> SafeNumberParser.parseIntOrThrow("+42", "plus sign test"));
        assertEquals(42, SafeNumberParser.parseIntOrThrow("+42", "plus sign test"));
    }

    @Test
    void parseLongOrThrow_zero_returnsZero() {
        assertEquals(0L, SafeNumberParser.parseLongOrThrow("0", "zero test"));
    }

    @Test
    void parseDoubleOrThrow_infinity_throwsOrReturnsInfinity() {
        // Double.parseDouble("Infinity") is valid in Java
        double result = SafeNumberParser.parseDoubleOrThrow("Infinity", "infinity test");
        assertTrue(Double.isInfinite(result));
    }

    @Test
    void parseDoubleOrDefault_nan_returnsNaN() {
        // Double.parseDouble("NaN") is valid in Java
        double result = SafeNumberParser.parseDoubleOrDefault("NaN", -1.0);
        assertTrue(Double.isNaN(result));
    }
}
