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

package org.yawlfoundation.yawl.elements.patterns;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MilestoneGuardOperator boolean logic evaluation.
 *
 * Chicago TDD: Tests the pure boolean operators (AND, OR, XOR).
 * No dependencies on other YAWL classes.
 *
 * Test Cases:
 * - AND: All states must be true
 * - OR: At least one state must be true
 * - XOR: Exactly one state must be true
 * - Edge cases (empty sets, null handling)
 * - String parsing
 *
 * @author YAWL Validation Team
 * @since 6.0
 */
@DisplayName("MilestoneGuardOperator Boolean Logic Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MilestoneGuardOperatorTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        MilestoneGuardOperatorTest.class
    );

    // ===== Test 1: AND Operator =====

    @Test
    @Order(1)
    @DisplayName("T1: AND with empty set returns false")
    void testANDEmptySet() {
        Set<Boolean> states = new HashSet<>();
        assertFalse(MilestoneGuardOperator.AND.evaluate(states),
            "AND with empty set should return false");
    }

    @Test
    @Order(2)
    @DisplayName("T2: AND with single true returns true")
    void testANDSingleTrue() {
        Set<Boolean> states = Set.of(true);
        assertTrue(MilestoneGuardOperator.AND.evaluate(states),
            "AND with single true should return true");
    }

    @Test
    @Order(3)
    @DisplayName("T3: AND with single false returns false")
    void testANDSingleFalse() {
        Set<Boolean> states = Set.of(false);
        assertFalse(MilestoneGuardOperator.AND.evaluate(states),
            "AND with single false should return false");
    }

    @Test
    @Order(4)
    @DisplayName("T4: AND with all true returns true")
    void testANDAllTrue() {
        Set<Boolean> states = Set.of(true, true, true);
        assertTrue(MilestoneGuardOperator.AND.evaluate(states),
            "AND with all true should return true");
    }

    @Test
    @Order(5)
    @DisplayName("T5: AND with all false returns false")
    void testANDAllFalse() {
        Set<Boolean> states = Set.of(false, false, false);
        assertFalse(MilestoneGuardOperator.AND.evaluate(states),
            "AND with all false should return false");
    }

    @Test
    @Order(6)
    @DisplayName("T6: AND with mixed true/false returns false")
    void testANDMixed() {
        Set<Boolean> states = Set.of(true, false);
        assertFalse(MilestoneGuardOperator.AND.evaluate(states),
            "AND with mixed true/false should return false");
    }

    @Test
    @Order(7)
    @DisplayName("T7: AND with true and single false returns false")
    void testANDMultipleTrueOneFalse() {
        Set<Boolean> states = Set.of(true, true, false);
        assertFalse(MilestoneGuardOperator.AND.evaluate(states),
            "AND with multiple true and one false should return false");
    }

    // ===== Test 2: OR Operator =====

    @Test
    @Order(8)
    @DisplayName("T8: OR with empty set returns false")
    void testOREmptySet() {
        Set<Boolean> states = new HashSet<>();
        assertFalse(MilestoneGuardOperator.OR.evaluate(states),
            "OR with empty set should return false");
    }

    @Test
    @Order(9)
    @DisplayName("T9: OR with single true returns true")
    void testORSingleTrue() {
        Set<Boolean> states = Set.of(true);
        assertTrue(MilestoneGuardOperator.OR.evaluate(states),
            "OR with single true should return true");
    }

    @Test
    @Order(10)
    @DisplayName("T10: OR with single false returns false")
    void testORSingleFalse() {
        Set<Boolean> states = Set.of(false);
        assertFalse(MilestoneGuardOperator.OR.evaluate(states),
            "OR with single false should return false");
    }

    @Test
    @Order(11)
    @DisplayName("T11: OR with all true returns true")
    void testORAllTrue() {
        Set<Boolean> states = Set.of(true, true, true);
        assertTrue(MilestoneGuardOperator.OR.evaluate(states),
            "OR with all true should return true");
    }

    @Test
    @Order(12)
    @DisplayName("T12: OR with all false returns false")
    void testORAllFalse() {
        Set<Boolean> states = Set.of(false, false, false);
        assertFalse(MilestoneGuardOperator.OR.evaluate(states),
            "OR with all false should return false");
    }

    @Test
    @Order(13)
    @DisplayName("T13: OR with mixed true/false returns true")
    void testORMixed() {
        Set<Boolean> states = Set.of(true, false);
        assertTrue(MilestoneGuardOperator.OR.evaluate(states),
            "OR with mixed true/false should return true");
    }

    @Test
    @Order(14)
    @DisplayName("T14: OR with single true and multiple false returns true")
    void testOROneTrueMultipleFalse() {
        Set<Boolean> states = Set.of(true, false, false);
        assertTrue(MilestoneGuardOperator.OR.evaluate(states),
            "OR with one true and multiple false should return true");
    }

    // ===== Test 3: XOR Operator =====

    @Test
    @Order(15)
    @DisplayName("T15: XOR with empty set returns false")
    void testXOREmptySet() {
        Set<Boolean> states = new HashSet<>();
        assertFalse(MilestoneGuardOperator.XOR.evaluate(states),
            "XOR with empty set should return false");
    }

    @Test
    @Order(16)
    @DisplayName("T16: XOR with single true returns true")
    void testXORSingleTrue() {
        Set<Boolean> states = Set.of(true);
        assertTrue(MilestoneGuardOperator.XOR.evaluate(states),
            "XOR with single true should return true");
    }

    @Test
    @Order(17)
    @DisplayName("T17: XOR with single false returns false")
    void testXORSingleFalse() {
        Set<Boolean> states = Set.of(false);
        assertFalse(MilestoneGuardOperator.XOR.evaluate(states),
            "XOR with single false should return false");
    }

    @Test
    @Order(18)
    @DisplayName("T18: XOR with all true returns false")
    void testXORAllTrue() {
        Set<Boolean> states = Set.of(true, true, true);
        assertFalse(MilestoneGuardOperator.XOR.evaluate(states),
            "XOR with all true should return false (more than one)");
    }

    @Test
    @Order(19)
    @DisplayName("T19: XOR with all false returns false")
    void testXORAllFalse() {
        Set<Boolean> states = Set.of(false, false, false);
        assertFalse(MilestoneGuardOperator.XOR.evaluate(states),
            "XOR with all false should return false (none true)");
    }

    @Test
    @Order(20)
    @DisplayName("T20: XOR with exactly one true returns true")
    void testXORExactlyOneTrue() {
        Set<Boolean> states = Set.of(true, false, false);
        assertTrue(MilestoneGuardOperator.XOR.evaluate(states),
            "XOR with exactly one true should return true");
    }

    @Test
    @Order(21)
    @DisplayName("T21: XOR with two true returns false")
    void testXORTwoTrue() {
        Set<Boolean> states = Set.of(true, true, false);
        assertFalse(MilestoneGuardOperator.XOR.evaluate(states),
            "XOR with two true should return false");
    }

    @Test
    @Order(22)
    @DisplayName("T22: XOR with mixed true/false (2+ true) returns false")
    void testXORMultipleTrue() {
        Set<Boolean> states = Set.of(true, false, true, false);
        assertFalse(MilestoneGuardOperator.XOR.evaluate(states),
            "XOR with multiple true should return false");
    }

    // ===== Test 4: String Parsing =====

    @Test
    @Order(23)
    @DisplayName("T23: Parse 'AND' string returns AND operator")
    void testParseANDString() {
        assertEquals(MilestoneGuardOperator.AND,
            MilestoneGuardOperator.fromString("AND"),
            "Parsing 'AND' should return AND operator");
    }

    @Test
    @Order(24)
    @DisplayName("T24: Parse 'OR' string returns OR operator")
    void testParseORString() {
        assertEquals(MilestoneGuardOperator.OR,
            MilestoneGuardOperator.fromString("OR"),
            "Parsing 'OR' should return OR operator");
    }

    @Test
    @Order(25)
    @DisplayName("T25: Parse 'XOR' string returns XOR operator")
    void testParseXORString() {
        assertEquals(MilestoneGuardOperator.XOR,
            MilestoneGuardOperator.fromString("XOR"),
            "Parsing 'XOR' should return XOR operator");
    }

    @Test
    @Order(26)
    @DisplayName("T26: Parse lowercase 'and' returns AND operator")
    void testParseLowercaseAND() {
        assertEquals(MilestoneGuardOperator.AND,
            MilestoneGuardOperator.fromString("and"),
            "Parsing lowercase 'and' should return AND operator");
    }

    @Test
    @Order(27)
    @DisplayName("T27: Parse lowercase 'or' returns OR operator")
    void testParseLowercaseOR() {
        assertEquals(MilestoneGuardOperator.OR,
            MilestoneGuardOperator.fromString("or"),
            "Parsing lowercase 'or' should return OR operator");
    }

    @Test
    @Order(28)
    @DisplayName("T28: Parse lowercase 'xor' returns XOR operator")
    void testParseLowercaseXOR() {
        assertEquals(MilestoneGuardOperator.XOR,
            MilestoneGuardOperator.fromString("xor"),
            "Parsing lowercase 'xor' should return XOR operator");
    }

    @Test
    @Order(29)
    @DisplayName("T29: Parse null string returns AND (default)")
    void testParseNullString() {
        assertEquals(MilestoneGuardOperator.AND,
            MilestoneGuardOperator.fromString(null),
            "Parsing null should return AND operator (default)");
    }

    @Test
    @Order(30)
    @DisplayName("T30: Parse unknown string returns AND (default)")
    void testParseUnknownString() {
        assertEquals(MilestoneGuardOperator.AND,
            MilestoneGuardOperator.fromString("UNKNOWN"),
            "Parsing unknown string should return AND operator (default)");
    }

    @Test
    @Order(31)
    @DisplayName("T31: Parse empty string returns AND (default)")
    void testParseEmptyString() {
        assertEquals(MilestoneGuardOperator.AND,
            MilestoneGuardOperator.fromString(""),
            "Parsing empty string should return AND operator (default)");
    }

    @Test
    @Order(32)
    @DisplayName("T32: Parse mixed case 'AnD' returns AND operator")
    void testParseMixedCaseAND() {
        assertEquals(MilestoneGuardOperator.AND,
            MilestoneGuardOperator.fromString("AnD"),
            "Parsing mixed case 'AnD' should return AND operator");
    }

    // ===== Test 5: Enum Values =====

    @Test
    @Order(33)
    @DisplayName("T33: AND operator has correct name")
    void testANDOperatorName() {
        assertEquals("AND", MilestoneGuardOperator.AND.name(),
            "AND operator should have name 'AND'");
    }

    @Test
    @Order(34)
    @DisplayName("T34: OR operator has correct name")
    void testOROperatorName() {
        assertEquals("OR", MilestoneGuardOperator.OR.name(),
            "OR operator should have name 'OR'");
    }

    @Test
    @Order(35)
    @DisplayName("T35: XOR operator has correct name")
    void testXOROperatorName() {
        assertEquals("XOR", MilestoneGuardOperator.XOR.name(),
            "XOR operator should have name 'XOR'");
    }

    @Test
    @Order(36)
    @DisplayName("T36: All enum values present")
    void testAllEnumValuesPresent() {
        MilestoneGuardOperator[] values = MilestoneGuardOperator.values();
        assertEquals(3, values.length,
            "Should have exactly 3 operators (AND, OR, XOR)");
    }

    // ===== Test 6: Truth Table Coverage =====

    @Test
    @Order(37)
    @DisplayName("T37: AND truth table completeness")
    void testANDTruthTableCompleteness() {
        // T,T -> T
        assertTrue(MilestoneGuardOperator.AND.evaluate(Set.of(true, true)));
        // T,F -> F
        assertFalse(MilestoneGuardOperator.AND.evaluate(Set.of(true, false)));
        // F,T -> F
        assertFalse(MilestoneGuardOperator.AND.evaluate(Set.of(false, true)));
        // F,F -> F
        assertFalse(MilestoneGuardOperator.AND.evaluate(Set.of(false, false)));
    }

    @Test
    @Order(38)
    @DisplayName("T38: OR truth table completeness")
    void testORTruthTableCompleteness() {
        // T,T -> T
        assertTrue(MilestoneGuardOperator.OR.evaluate(Set.of(true, true)));
        // T,F -> T
        assertTrue(MilestoneGuardOperator.OR.evaluate(Set.of(true, false)));
        // F,T -> T
        assertTrue(MilestoneGuardOperator.OR.evaluate(Set.of(false, true)));
        // F,F -> F
        assertFalse(MilestoneGuardOperator.OR.evaluate(Set.of(false, false)));
    }

    @Test
    @Order(39)
    @DisplayName("T39: XOR truth table completeness")
    void testXORTruthTableCompleteness() {
        // T,T -> F
        assertFalse(MilestoneGuardOperator.XOR.evaluate(Set.of(true, true)));
        // T,F -> T
        assertTrue(MilestoneGuardOperator.XOR.evaluate(Set.of(true, false)));
        // F,T -> T
        assertTrue(MilestoneGuardOperator.XOR.evaluate(Set.of(false, true)));
        // F,F -> F
        assertFalse(MilestoneGuardOperator.XOR.evaluate(Set.of(false, false)));
    }
}
