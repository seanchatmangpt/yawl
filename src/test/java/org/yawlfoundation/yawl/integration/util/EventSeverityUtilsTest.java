/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL (Yet Another Workflow Language).
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EventSeverityUtils utility class.
 */
class EventSeverityUtilsTest {

    @Test
    void parseSeverity_validInput_returnsNormalized() {
        assertEquals("HIGH", EventSeverityUtils.parseSeverity("high"));
        assertEquals("CRITICAL", EventSeverityUtils.parseSeverity("CRITICAL"));
        assertEquals("MEDIUM", EventSeverityUtils.parseSeverity("medium"));
        assertEquals("LOW", EventSeverityUtils.parseSeverity("Low"));
        assertEquals("INFO", EventSeverityUtils.parseSeverity("info"));
    }

    @Test
    void parseSeverity_invalidInput_throwsException() {
        assertThrows(IllegalArgumentException.class,
            () -> EventSeverityUtils.parseSeverity("invalid"));
        assertThrows(IllegalArgumentException.class,
            () -> EventSeverityUtils.parseSeverity("UNKNOWN"));
        assertThrows(IllegalArgumentException.class,
            () -> EventSeverityUtils.parseSeverity("not_a_severity"));
    }

    @Test
    void parseSeverity_nullInput_returnsDefault() {
        assertEquals("MEDIUM", EventSeverityUtils.parseSeverity(null));
    }

    @Test
    void parseSeverity_emptyInput_returnsDefault() {
        assertEquals("MEDIUM", EventSeverityUtils.parseSeverity(""));
    }

    @Test
    void parseSeverity_whitespaceOnlyInput_returnsDefault() {
        assertEquals("MEDIUM", EventSeverityUtils.parseSeverity("   "));
    }

    @Test
    void isValidSeverity_validReturnsTrue() {
        assertTrue(EventSeverityUtils.isValidSeverity("HIGH"));
        assertTrue(EventSeverityUtils.isValidSeverity("CRITICAL"));
        assertTrue(EventSeverityUtils.isValidSeverity("MEDIUM"));
        assertTrue(EventSeverityUtils.isValidSeverity("LOW"));
        assertTrue(EventSeverityUtils.isValidSeverity("INFO"));
    }

    @Test
    void isValidSeverity_invalidReturnsFalse() {
        assertFalse(EventSeverityUtils.isValidSeverity("invalid"));
        assertFalse(EventSeverityUtils.isValidSeverity("UNKNOWN"));
        assertFalse(EventSeverityUtils.isValidSeverity("not_a_severity"));
        assertFalse(EventSeverityUtils.isValidSeverity("random_string"));
    }

    @Test
    void isValidSeverity_nullReturnsFalse() {
        assertFalse(EventSeverityUtils.isValidSeverity(null));
    }

    @Test
    void isValidSeverity_emptyReturnsFalse() {
        assertFalse(EventSeverityUtils.isValidSeverity(""));
    }

    @Test
    void isValidSeverity_whitespaceOnlyReturnsFalse() {
        assertFalse(EventSeverityUtils.isValidSeverity("   "));
    }

    @Test
    void isValidSeverity_caseInsensitive() {
        assertTrue(EventSeverityUtils.isValidSeverity("high"));
        assertTrue(EventSeverityUtils.isValidSeverity("High"));
        assertTrue(EventSeverityUtils.isValidSeverity("HIGH"));
    }
}