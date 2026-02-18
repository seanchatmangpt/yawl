/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RdrCondition} — RDR condition evaluation against data contexts.
 *
 * <p>Chicago TDD: Real RdrCondition objects evaluated against real Map contexts.
 * Tests all supported operators and edge cases.
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 */
@DisplayName("RdrCondition — condition expression evaluation against data contexts")
class TestRdrCondition {

    private Map<String, String> context;

    @BeforeEach
    void setUp() {
        context = new HashMap<>();
        context.put("priority", "7");
        context.put("amount", "1500.50");
        context.put("status", "approved");
        context.put("department", "Finance");
        context.put("code", "ABC-123");
    }

    @Test
    @DisplayName("Constructor creates condition with trimmed expression")
    void testConstructor_validExpression_createsCondition() {
        RdrCondition cond = new RdrCondition("  priority > 5  ");
        assertEquals("priority > 5", cond.getExpression(),
                "Expression should be stored trimmed");
    }

    @Test
    @DisplayName("Constructor rejects null expression")
    void testConstructor_nullExpression_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RdrCondition(null),
                "Null expression should be rejected");
    }

    @Test
    @DisplayName("Constructor rejects blank expression")
    void testConstructor_blankExpression_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RdrCondition("   "),
                "Blank expression should be rejected");
    }

    @Test
    @DisplayName("evaluate with = operator returns true for exact match")
    void testEvaluate_equalityOperator_exactMatch() {
        RdrCondition cond = new RdrCondition("status = approved");
        assertTrue(cond.evaluate(context), "status = approved should match 'approved'");
    }

    @Test
    @DisplayName("evaluate with = operator returns false for non-matching value")
    void testEvaluate_equalityOperator_nonMatch() {
        RdrCondition cond = new RdrCondition("status = rejected");
        assertFalse(cond.evaluate(context), "status = rejected should not match 'approved'");
    }

    @Test
    @DisplayName("evaluate with != operator returns true when values differ")
    void testEvaluate_notEqualOperator_valuesAreDifferent() {
        RdrCondition cond = new RdrCondition("status != rejected");
        assertTrue(cond.evaluate(context), "status != rejected should be true for 'approved'");
    }

    @Test
    @DisplayName("evaluate with != operator returns false when values are equal")
    void testEvaluate_notEqualOperator_valuesAreEqual() {
        RdrCondition cond = new RdrCondition("status != approved");
        assertFalse(cond.evaluate(context), "status != approved should be false for 'approved'");
    }

    @Test
    @DisplayName("evaluate with > operator returns true when actual is greater")
    void testEvaluate_greaterThanOperator_actualIsGreater() {
        RdrCondition cond = new RdrCondition("priority > 5");
        assertTrue(cond.evaluate(context), "priority > 5 should be true for priority=7");
    }

    @Test
    @DisplayName("evaluate with > operator returns false when actual equals expected")
    void testEvaluate_greaterThanOperator_actualEqualsExpected() {
        RdrCondition cond = new RdrCondition("priority > 7");
        assertFalse(cond.evaluate(context), "priority > 7 should be false for priority=7 (not strictly greater)");
    }

    @Test
    @DisplayName("evaluate with > operator returns false when actual is less")
    void testEvaluate_greaterThanOperator_actualIsLess() {
        RdrCondition cond = new RdrCondition("priority > 10");
        assertFalse(cond.evaluate(context), "priority > 10 should be false for priority=7");
    }

    @Test
    @DisplayName("evaluate with < operator returns true when actual is less")
    void testEvaluate_lessThanOperator_actualIsLess() {
        RdrCondition cond = new RdrCondition("priority < 10");
        assertTrue(cond.evaluate(context), "priority < 10 should be true for priority=7");
    }

    @Test
    @DisplayName("evaluate with < operator returns false when actual is greater")
    void testEvaluate_lessThanOperator_actualIsGreater() {
        RdrCondition cond = new RdrCondition("priority < 5");
        assertFalse(cond.evaluate(context), "priority < 5 should be false for priority=7");
    }

    @Test
    @DisplayName("evaluate with >= operator returns true at boundary (equal)")
    void testEvaluate_greaterOrEqualOperator_atBoundary() {
        RdrCondition cond = new RdrCondition("priority >= 7");
        assertTrue(cond.evaluate(context), "priority >= 7 should be true for priority=7");
    }

    @Test
    @DisplayName("evaluate with >= operator returns true above boundary")
    void testEvaluate_greaterOrEqualOperator_aboveBoundary() {
        RdrCondition cond = new RdrCondition("priority >= 5");
        assertTrue(cond.evaluate(context), "priority >= 5 should be true for priority=7");
    }

    @Test
    @DisplayName("evaluate with >= operator returns false below boundary")
    void testEvaluate_greaterOrEqualOperator_belowBoundary() {
        RdrCondition cond = new RdrCondition("priority >= 8");
        assertFalse(cond.evaluate(context), "priority >= 8 should be false for priority=7");
    }

    @Test
    @DisplayName("evaluate with <= operator returns true at boundary (equal)")
    void testEvaluate_lessOrEqualOperator_atBoundary() {
        RdrCondition cond = new RdrCondition("priority <= 7");
        assertTrue(cond.evaluate(context), "priority <= 7 should be true for priority=7");
    }

    @Test
    @DisplayName("evaluate with <= operator returns false above boundary")
    void testEvaluate_lessOrEqualOperator_aboveBoundary() {
        RdrCondition cond = new RdrCondition("priority <= 5");
        assertFalse(cond.evaluate(context), "priority <= 5 should be false for priority=7");
    }

    @Test
    @DisplayName("evaluate with 'contains' operator returns true when substring found")
    void testEvaluate_containsOperator_substringFound() {
        RdrCondition cond = new RdrCondition("department contains Fin");
        assertTrue(cond.evaluate(context), "'Finance' should contain 'Fin'");
    }

    @Test
    @DisplayName("evaluate with 'contains' operator returns false when substring not found")
    void testEvaluate_containsOperator_substringNotFound() {
        RdrCondition cond = new RdrCondition("department contains Eng");
        assertFalse(cond.evaluate(context), "'Finance' should not contain 'Eng'");
    }

    @Test
    @DisplayName("evaluate with 'startsWith' operator returns true when string starts with prefix")
    void testEvaluate_startsWithOperator_matchingPrefix() {
        RdrCondition cond = new RdrCondition("code startsWith ABC");
        assertTrue(cond.evaluate(context), "'ABC-123' should start with 'ABC'");
    }

    @Test
    @DisplayName("evaluate with 'startsWith' operator returns false when string does not start with prefix")
    void testEvaluate_startsWithOperator_nonMatchingPrefix() {
        RdrCondition cond = new RdrCondition("code startsWith XYZ");
        assertFalse(cond.evaluate(context), "'ABC-123' should not start with 'XYZ'");
    }

    @Test
    @DisplayName("evaluate with 'endsWith' operator returns true when string ends with suffix")
    void testEvaluate_endsWithOperator_matchingSuffix() {
        RdrCondition cond = new RdrCondition("code endsWith 123");
        assertTrue(cond.evaluate(context), "'ABC-123' should end with '123'");
    }

    @Test
    @DisplayName("evaluate with 'endsWith' operator returns false when string does not end with suffix")
    void testEvaluate_endsWithOperator_nonMatchingSuffix() {
        RdrCondition cond = new RdrCondition("code endsWith 456");
        assertFalse(cond.evaluate(context), "'ABC-123' should not end with '456'");
    }

    @Test
    @DisplayName("evaluate returns false for missing attribute")
    void testEvaluate_missingAttribute_returnsFalse() {
        RdrCondition cond = new RdrCondition("nonExistentAttr = someValue");
        assertFalse(cond.evaluate(context), "Missing attribute should cause false result");
    }

    @Test
    @DisplayName("evaluate rejects null context")
    void testEvaluate_nullContext_throwsException() {
        RdrCondition cond = new RdrCondition("priority > 5");
        assertThrows(IllegalArgumentException.class,
                () -> cond.evaluate(null),
                "Null context should throw exception");
    }

    @Test
    @DisplayName("evaluate returns false for malformed expression (wrong format)")
    void testEvaluate_malformedExpression_returnsFalse() {
        // Only one part — no operator or value
        RdrCondition cond = new RdrCondition("priority");
        assertFalse(cond.evaluate(context),
                "Malformed expression without operator should return false");
    }

    @Test
    @DisplayName("evaluate returns false for unknown operator")
    void testEvaluate_unknownOperator_returnsFalse() {
        RdrCondition cond = new RdrCondition("priority MATCHES 7");
        assertFalse(cond.evaluate(context), "Unknown operator should return false");
    }

    @Test
    @DisplayName("evaluate with decimal number comparison works correctly")
    void testEvaluate_decimalNumberComparison_worksCorrectly() {
        RdrCondition cond = new RdrCondition("amount > 1000.0");
        assertTrue(cond.evaluate(context),
                "amount=1500.50 should be > 1000.0");
    }

    @Test
    @DisplayName("equals and hashCode based on expression")
    void testEqualsAndHashCode_basedOnExpression() {
        RdrCondition cond1 = new RdrCondition("priority > 5");
        RdrCondition cond2 = new RdrCondition("priority > 5");
        RdrCondition cond3 = new RdrCondition("priority < 5");

        assertEquals(cond1, cond2, "Conditions with same expression should be equal");
        assertEquals(cond1.hashCode(), cond2.hashCode(), "Equal conditions should have equal hash codes");
        assertNotEquals(cond1, cond3, "Conditions with different expressions should not be equal");
    }

    @Test
    @DisplayName("toString returns the expression string")
    void testToString_returnsExpression() {
        RdrCondition cond = new RdrCondition("priority > 5");
        assertEquals("priority > 5", cond.toString(), "toString should return the expression");
    }

    @Test
    @DisplayName("evaluate with empty context returns false for any condition")
    void testEvaluate_emptyContext_returnsFalse() {
        RdrCondition cond = new RdrCondition("priority > 5");
        assertFalse(cond.evaluate(new HashMap<>()), "Empty context should return false");
    }
}
