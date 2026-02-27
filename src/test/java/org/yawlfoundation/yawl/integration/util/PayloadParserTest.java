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
 * Unit tests for PayloadParser utility class.
 */
class PayloadParserTest {

    @Test
    void parse_commaSeparatedKeyValuePairs() {
        String payload = "key1,value1,key2,value2,key3,value3";
        assertEquals("value1", PayloadParser.parse(payload, "key1"));
        assertEquals("value2", PayloadParser.parse(payload, "key2"));
        assertEquals("value3", PayloadParser.parse(payload, "key3"));
    }

    @Test
    void parse_semicolonSeparatedKeyValuePairs() {
        String payload = "key1;value1;key2;value2;key3;value3";
        assertEquals("value1", PayloadParser.parse(payload, "key1"));
        assertEquals("value2", PayloadParser.parse(payload, "key2"));
        assertEquals("value3", PayloadParser.parse(payload, "key3"));
    }

    @Test
    void parse_quotedValues() {
        String payload = "key1,\"value with,comma\",key2,\"value with;semicolon\"";
        assertEquals("value with,comma", PayloadParser.parse(payload, "key1"));
        assertEquals("value with;semicolon", PayloadParser.parse(payload, "key2"));
    }

    @Test
    void parse_withSpacesAroundDelimiters() {
        String payload = "key1, value1 , key2 , value2 ";
        assertEquals("value1", PayloadParser.parse(payload, "key1"));
        assertEquals("value2", PayloadParser.parse(payload, "key2"));
    }

    @Test
    void parse_missingKey_returnsDefault() {
        String payload = "key1,value1,key2,value2";
        assertEquals("default", PayloadParser.parse(payload, "missingKey", "default"));
    }

    @Test
    void parse_emptyValue_returnsEmptyString() {
        String payload = "key1,,key2,value2";
        assertEquals("", PayloadParser.parse(payload, "key1"));
    }

    @Test
    void parse_nullPayload_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> PayloadParser.parse(null, "key1"));
    }

    @Test
    void parse_emptyPayload_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> PayloadParser.parse("", "key1"));
    }

    @Test
    void parse_withDefaultString() {
        String payload = "key1,value1";
        assertEquals("default", PayloadParser.parse(payload, "missingKey", "default"));
    }

    @Test
    void parse_emptyString_withDefault() {
        assertEquals("default", PayloadParser.parse(null, "key1", "default"));
        assertEquals("default", PayloadParser.parse("", "key1", "default"));
    }

    @Test
    void parseNullKey_throwsNullPointerException() {
        String payload = "key1,value1";
        assertThrows(NullPointerException.class,
            () -> PayloadParser.parse(payload, null));
    }

    @Test
    void parseEmptyKey_throwsNullPointerException() {
        String payload = "key1,value1";
        assertThrows(NullPointerException.class,
            () -> PayloadParser.parse(payload, ""));
    }

    // Test for getInt method
    @Test
    void getInt_validNumericValue() {
        String payload = "count,42,active,1";
        assertEquals(42, PayloadParser.getInt(payload, "count"));
        assertEquals(1, PayloadParser.getInt(payload, "active"));
    }

    @Test
    void getInt_invalidNumericValue_throwsNumberFormatException() {
        String payload = "count,not_a_number";
        assertThrows(NumberFormatException.class,
            () -> PayloadParser.getInt(payload, "count"));
    }

    @Test
    void getInt_missingKey_returnsDefault() {
        String payload = "key1,value1";
        assertEquals(-1, PayloadParser.getInt(payload, "missingKey", -1));
    }

    @Test
    void getInt_withDefault() {
        String payload = "key1,not_a_number";
        assertEquals(-1, PayloadParser.getInt(payload, "key1", -1));
    }

    // Test for getBoolean method
    @Test
    void getBoolean_trueValues() {
        String payload = "flag1,true,flag2,TRUE,flag3,True,flag4,1";
        assertTrue(PayloadParser.getBoolean(payload, "flag1"));
        assertTrue(PayloadParser.getBoolean(payload, "flag2"));
        assertTrue(PayloadParser.getBoolean(payload, "flag3"));
        assertTrue(PayloadParser.getBoolean(payload, "flag4"));
    }

    @Test
    void getBoolean_falseValues() {
        String payload = "flag1,false,flag2,FALSE,flag3,False,flag4,0";
        assertFalse(PayloadParser.getBoolean(payload, "flag1"));
        assertFalse(PayloadParser.getBoolean(payload, "flag2"));
        assertFalse(PayloadParser.getBoolean(payload, "flag3"));
        assertFalse(PayloadParser.getBoolean(payload, "flag4"));
    }

    @Test
    void getBoolean_invalidBooleanValue_throwsIllegalArgumentException() {
        String payload = "flag1,maybe";
        assertThrows(IllegalArgumentException.class,
            () -> PayloadParser.getBoolean(payload, "flag1"));
    }

    @Test
    void getBoolean_missingKey_returnsDefault() {
        String payload = "key1,value1";
        assertFalse(PayloadParser.getBoolean(payload, "missingKey", false));
    }

    @Test
    void getBoolean_withDefault() {
        String payload = "flag1,maybe";
        assertTrue(PayloadParser.getBoolean(payload, "flag1", true));
    }
}