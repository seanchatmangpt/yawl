/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.bridge.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CallPattern enum.
 */
class CallPatternTest {

    @Test
    @DisplayName("All enum values have correct pattern strings")
    void testEnumPatternStrings() {
        assertEquals("jvm", CallPattern.JVM.getPattern());
        assertEquals("beam", CallPattern.BEAM.getPattern());
        assertEquals("direct", CallPattern.DIRECT.getPattern());
    }

    @ParameterizedTest
    @MethodSource("validPatterns")
    @DisplayName("Valid patterns convert correctly to enum")
    void testFromStringValid(String pattern) {
        CallPattern converted = CallPattern.fromString(pattern);
        assertNotNull(converted);

        // Case insensitive
        CallPattern upper = CallPattern.fromString(pattern.toUpperCase());
        assertEquals(converted, upper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "jvm", "BEAM", "DIRECT"})
    @DisplayName("Invalid patterns throw exception")
    void testFromStringInvalid(String pattern) {
        assertThrows(IllegalArgumentException.class, () -> CallPattern.fromString(pattern));
    }

    @Test
    @DisplayName("Null pattern throws exception")
    void testFromStringNull() {
        assertThrows(IllegalArgumentException.class, () -> CallPattern.fromString(null));
    }

    @Test
    @DisplayName("Executable patterns are identified correctly")
    void testIsExecutable() {
        assertTrue(CallPattern.JVM.isExecutable());
        assertTrue(CallPattern.BEAM.isExecutable());
        assertFalse(CallPattern.DIRECT.isExecutable());
    }

    @Test
    @DisplayName("Descriptions are non-null and informative")
    void testGetDescriptions() {
        assertNotNull(CallPattern.JVM.getDescription());
        assertTrue(CallPattern.JVM.getDescription().contains("JVM"));

        assertNotNull(CallPattern.BEAM.getDescription());
        assertTrue(CallPattern.BEAM.getDescription().contains("BEAM"));

        assertNotNull(CallPattern.DIRECT.getDescription());
        assertTrue(CallPattern.DIRECT.getDescription().contains("blocked"));
    }

    private static String[] validPatterns() {
        return new String[]{"jvm", "beam", "direct", "JVM", "Beam", "DiReCt"};
    }
}