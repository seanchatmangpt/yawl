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

import java.math.BigDecimal;

/**
 * Centralised, safe numeric parsing utility for the YAWL engine.
 *
 * <p>All methods in this class follow one of three contracts:
 * <ol>
 *   <li><strong>OrDefault</strong> – returns the caller-supplied default when the input
 *       is {@code null}, blank, or not a valid number.  Use this for optional inputs
 *       where a sensible fallback exists (e.g. configuration properties).</li>
 *   <li><strong>OrNull</strong> – returns {@code null} when the input is {@code null},
 *       blank, or not a valid number.  Use this when {@code null} is a meaningful
 *       sentinel (e.g. optional JSON fields).</li>
 *   <li><strong>OrThrow</strong> – throws {@link NumberFormatException} with a
 *       caller-supplied contextual message when the input is {@code null}, blank, or
 *       not a valid number.  Use this whenever an invalid value is a hard error
 *       (e.g. required configuration, schema data, HTTP key parameters).</li>
 * </ol>
 *
 * <p>No method in this class silently swallows an error and returns a fake default
 * without the caller explicitly requesting a default.  Every parse path is explicit.
 *
 * @author YAWL Foundation (safe-parse utility, 2026)
 */
public final class SafeNumberParser {

    private SafeNumberParser() {
        throw new UnsupportedOperationException(
                "SafeNumberParser is a static utility class and must not be instantiated.");
    }

    // -------------------------------------------------------------------------
    // Integer
    // -------------------------------------------------------------------------

    /**
     * Parses {@code value} as an {@code int}, returning {@code defaultValue} when
     * {@code value} is {@code null}, blank, or not a valid integer.
     *
     * @param value        the string to parse; may be {@code null}
     * @param defaultValue value to return when parsing is not possible
     * @return the parsed integer, or {@code defaultValue}
     */
    public static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses {@code value} as an {@code Integer}, returning {@code null} when
     * {@code value} is {@code null}, blank, or not a valid integer.
     *
     * @param value the string to parse; may be {@code null}
     * @return the parsed Integer, or {@code null}
     */
    public static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses {@code value} as an {@code int}, throwing {@link NumberFormatException}
     * with {@code errorContext} prepended to the message when the value is invalid.
     *
     * @param value        the string to parse; must not be {@code null} or blank
     * @param errorContext human-readable description of what is being parsed,
     *                     included in the exception message
     * @return the parsed integer
     * @throws NumberFormatException if {@code value} is {@code null}, blank, or
     *                               cannot be parsed as an integer
     */
    public static int parseIntOrThrow(String value, String errorContext) {
        if (value == null || value.isBlank()) {
            throw new NumberFormatException(
                    errorContext + ": value is null or blank");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(
                    errorContext + ": \"" + value + "\" is not a valid integer");
        }
    }

    // -------------------------------------------------------------------------
    // Long
    // -------------------------------------------------------------------------

    /**
     * Parses {@code value} as a {@code long}, returning {@code defaultValue} when
     * {@code value} is {@code null}, blank, or not a valid long.
     *
     * @param value        the string to parse; may be {@code null}
     * @param defaultValue value to return when parsing is not possible
     * @return the parsed long, or {@code defaultValue}
     */
    public static long parseLongOrDefault(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses {@code value} as a {@code Long}, returning {@code null} when
     * {@code value} is {@code null}, blank, or not a valid long.
     *
     * @param value the string to parse; may be {@code null}
     * @return the parsed Long, or {@code null}
     */
    public static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses {@code value} as a {@code long}, throwing {@link NumberFormatException}
     * with {@code errorContext} prepended to the message when the value is invalid.
     *
     * @param value        the string to parse; must not be {@code null} or blank
     * @param errorContext human-readable description of what is being parsed,
     *                     included in the exception message
     * @return the parsed long
     * @throws NumberFormatException if {@code value} is {@code null}, blank, or
     *                               cannot be parsed as a long
     */
    public static long parseLongOrThrow(String value, String errorContext) {
        if (value == null || value.isBlank()) {
            throw new NumberFormatException(
                    errorContext + ": value is null or blank");
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(
                    errorContext + ": \"" + value + "\" is not a valid long integer");
        }
    }

    // -------------------------------------------------------------------------
    // Double
    // -------------------------------------------------------------------------

    /**
     * Parses {@code value} as a {@code double}, returning {@code defaultValue} when
     * {@code value} is {@code null}, blank, or not a valid double.
     *
     * @param value        the string to parse; may be {@code null}
     * @param defaultValue value to return when parsing is not possible
     * @return the parsed double, or {@code defaultValue}
     */
    public static double parseDoubleOrDefault(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Parses {@code value} as a {@code Double}, returning {@code null} when
     * {@code value} is {@code null}, blank, or not a valid double.
     *
     * @param value the string to parse; may be {@code null}
     * @return the parsed Double, or {@code null}
     */
    public static Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses {@code value} as a {@code double}, throwing {@link NumberFormatException}
     * with {@code errorContext} prepended to the message when the value is invalid.
     *
     * @param value        the string to parse; must not be {@code null} or blank
     * @param errorContext human-readable description of what is being parsed,
     *                     included in the exception message
     * @return the parsed double
     * @throws NumberFormatException if {@code value} is {@code null}, blank, or
     *                               cannot be parsed as a double
     */
    public static double parseDoubleOrThrow(String value, String errorContext) {
        if (value == null || value.isBlank()) {
            throw new NumberFormatException(
                    errorContext + ": value is null or blank");
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(
                    errorContext + ": \"" + value + "\" is not a valid double");
        }
    }

    // -------------------------------------------------------------------------
    // BigDecimal
    // -------------------------------------------------------------------------

    /**
     * Parses {@code value} as a {@link BigDecimal}, returning {@code defaultValue}
     * when {@code value} is {@code null}, blank, or not a valid decimal number.
     *
     * @param value        the string to parse; may be {@code null}
     * @param defaultValue value to return when parsing is not possible
     * @return the parsed BigDecimal, or {@code defaultValue}
     */
    public static BigDecimal parseBigDecimalOrDefault(String value, BigDecimal defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException | ArithmeticException e) {
            return defaultValue;
        }
    }

    /**
     * Parses {@code value} as a {@link BigDecimal}, returning {@code null} when
     * {@code value} is {@code null}, blank, or not a valid decimal number.
     *
     * @param value the string to parse; may be {@code null}
     * @return the parsed BigDecimal, or {@code null}
     */
    public static BigDecimal parseBigDecimalOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /**
     * Parses {@code value} as a {@link BigDecimal}, throwing
     * {@link NumberFormatException} with {@code errorContext} prepended to the
     * message when the value is invalid.
     *
     * @param value        the string to parse; must not be {@code null} or blank
     * @param errorContext human-readable description of what is being parsed,
     *                     included in the exception message
     * @return the parsed BigDecimal
     * @throws NumberFormatException if {@code value} is {@code null}, blank, or
     *                               cannot be parsed as a BigDecimal
     */
    public static BigDecimal parseBigDecimalOrThrow(String value, String errorContext) {
        if (value == null || value.isBlank()) {
            throw new NumberFormatException(
                    errorContext + ": value is null or blank");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException | ArithmeticException e) {
            throw new NumberFormatException(
                    errorContext + ": \"" + value + "\" is not a valid decimal number");
        }
    }
}
