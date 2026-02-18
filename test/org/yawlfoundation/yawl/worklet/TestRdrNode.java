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
 * Tests for {@link RdrNode} — Ripple Down Rules node behavior.
 *
 * <p>Chicago TDD: Tests use real RdrNode and RdrCondition objects.
 * No mocks, no stubs.
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 */
@DisplayName("RdrNode — RDR node condition evaluation and tree traversal")
class TestRdrNode {

    private Map<String, String> context;

    @BeforeEach
    void setUp() {
        context = new HashMap<>();
        context.put("priority", "7");
        context.put("status", "approved");
        context.put("department", "Finance");
        context.put("amount", "1500");
    }

    @Test
    @DisplayName("Constructor creates node with correct ID, condition, and conclusion")
    void testConstructor_validArguments_createsNode() {
        RdrCondition condition = new RdrCondition("priority > 5");
        RdrNode node = new RdrNode(1, condition, "HighPriorityWorklet");

        assertEquals(1, node.getId(), "Node ID should match");
        assertSame(condition, node.getCondition(), "Condition reference should match");
        assertEquals("HighPriorityWorklet", node.getConclusion(), "Conclusion should match");
        assertNull(node.getTrueChild(), "New node should have no true-child");
        assertNull(node.getFalseChild(), "New node should have no false-child");
    }

    @Test
    @DisplayName("String-expression constructor creates node with correct condition")
    void testStringConstructor_createsRdrConditionFromExpression() {
        RdrNode node = new RdrNode(2, "status = approved", "ApprovalWorklet");

        assertEquals(2, node.getId());
        assertEquals("status = approved", node.getCondition().getExpression());
        assertEquals("ApprovalWorklet", node.getConclusion());
    }

    @Test
    @DisplayName("Constructor rejects negative node ID")
    void testConstructor_negativeId_throwsException() {
        RdrCondition condition = new RdrCondition("priority > 5");
        assertThrows(IllegalArgumentException.class,
                () -> new RdrNode(-1, condition, "SomeWorklet"),
                "Negative node ID should be rejected");
    }

    @Test
    @DisplayName("Constructor rejects null condition")
    void testConstructor_nullCondition_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RdrNode(1, (RdrCondition) null, "SomeWorklet"),
                "Null condition should be rejected");
    }

    @Test
    @DisplayName("Constructor rejects null conclusion")
    void testConstructor_nullConclusion_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RdrNode(1, "priority > 5", null),
                "Null conclusion should be rejected");
    }

    @Test
    @DisplayName("Constructor rejects blank conclusion")
    void testConstructor_blankConclusion_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RdrNode(1, "priority > 5", "   "),
                "Blank conclusion should be rejected");
    }

    @Test
    @DisplayName("evaluate returns true when condition is satisfied")
    void testEvaluate_conditionSatisfied_returnsTrue() {
        RdrNode node = new RdrNode(1, "priority > 5", "HighPriorityWorklet");

        boolean result = node.evaluate(context);

        assertTrue(result, "Condition 'priority > 5' should be true for priority=7");
    }

    @Test
    @DisplayName("evaluate returns false when condition is not satisfied")
    void testEvaluate_conditionNotSatisfied_returnsFalse() {
        RdrNode node = new RdrNode(1, "priority > 10", "HighPriorityWorklet");

        boolean result = node.evaluate(context);

        assertFalse(result, "Condition 'priority > 10' should be false for priority=7");
    }

    @Test
    @DisplayName("evaluate returns false when attribute is missing from context")
    void testEvaluate_missingAttribute_returnsFalse() {
        RdrNode node = new RdrNode(1, "unknownAttr = someValue", "SomeWorklet");

        boolean result = node.evaluate(context);

        assertFalse(result, "Missing attribute should cause condition to be false");
    }

    @Test
    @DisplayName("isLeaf returns true for node with no children")
    void testIsLeaf_noChildren_returnsTrue() {
        RdrNode node = new RdrNode(1, "priority > 5", "WorkletA");

        assertTrue(node.isLeaf(), "Node with no children should be a leaf");
    }

    @Test
    @DisplayName("isLeaf returns false when true-child is set")
    void testIsLeaf_hasTrueChild_returnsFalse() {
        RdrNode node = new RdrNode(1, "priority > 5", "WorkletA");
        RdrNode child = new RdrNode(2, "priority > 9", "WorkletB");
        node.setTrueChild(child);

        assertFalse(node.isLeaf(), "Node with true-child should not be a leaf");
    }

    @Test
    @DisplayName("isLeaf returns false when false-child is set")
    void testIsLeaf_hasFalseChild_returnsFalse() {
        RdrNode node = new RdrNode(1, "priority > 5", "WorkletA");
        RdrNode child = new RdrNode(2, "status = rejected", "RejectionWorklet");
        node.setFalseChild(child);

        assertFalse(node.isLeaf(), "Node with false-child should not be a leaf");
    }

    @Test
    @DisplayName("hasTrueChild and hasFalseChild correctly report child presence")
    void testHasChildMethods_correctlyReportPresence() {
        RdrNode node = new RdrNode(1, "priority > 5", "WorkletA");
        RdrNode trueChild = new RdrNode(2, "priority > 9", "WorkletB");
        RdrNode falseChild = new RdrNode(3, "status = rejected", "WorkletC");

        assertFalse(node.hasTrueChild(), "Should have no true-child initially");
        assertFalse(node.hasFalseChild(), "Should have no false-child initially");

        node.setTrueChild(trueChild);
        assertTrue(node.hasTrueChild(), "Should have true-child after setting");
        assertFalse(node.hasFalseChild(), "Should still have no false-child");

        node.setFalseChild(falseChild);
        assertTrue(node.hasTrueChild(), "Should still have true-child");
        assertTrue(node.hasFalseChild(), "Should now have false-child");
    }

    @Test
    @DisplayName("setTrueChild and setFalseChild accept null (detaching children)")
    void testSetChildren_nullValuesDetachChildren() {
        RdrNode node = new RdrNode(1, "priority > 5", "WorkletA");
        RdrNode child = new RdrNode(2, "priority > 9", "WorkletB");

        node.setTrueChild(child);
        assertTrue(node.hasTrueChild(), "Should have true-child after set");

        node.setTrueChild(null);
        assertFalse(node.hasTrueChild(), "Should not have true-child after set to null");
        assertTrue(node.isLeaf(), "Should be leaf after detaching all children");
    }

    @Test
    @DisplayName("evaluate with equality condition matches exact values")
    void testEvaluate_equalityCondition_matchesExactValue() {
        RdrNode node = new RdrNode(1, "status = approved", "ApprovedWorklet");

        assertTrue(node.evaluate(context), "Exact equality should match");

        context.put("status", "rejected");
        assertFalse(node.evaluate(context), "Different value should not match equality");
    }

    @Test
    @DisplayName("evaluate with 'contains' operator works on string values")
    void testEvaluate_containsOperator_matchesSubstring() {
        RdrNode node = new RdrNode(1, "department contains Fin", "FinanceWorklet");

        assertTrue(node.evaluate(context), "'Finance' should contain 'Fin'");

        context.put("department", "Engineering");
        assertFalse(node.evaluate(context), "'Engineering' should not contain 'Fin'");
    }

    @Test
    @DisplayName("evaluate with numeric <= operator handles boundary correctly")
    void testEvaluate_lessOrEqualOperator_handlesBoundary() {
        RdrNode node = new RdrNode(1, "amount <= 1500", "StandardWorklet");

        assertTrue(node.evaluate(context), "1500 <= 1500 should be true (boundary)");

        context.put("amount", "1501");
        assertFalse(node.evaluate(context), "1501 <= 1500 should be false");
    }

    @Test
    @DisplayName("equals and hashCode are consistent for same-ID nodes")
    void testEqualsAndHashCode_consistentForSameIdNodes() {
        RdrCondition condA = new RdrCondition("priority > 5");
        RdrCondition condB = new RdrCondition("priority > 5");
        RdrNode nodeA = new RdrNode(1, condA, "WorkletA");
        RdrNode nodeB = new RdrNode(1, condB, "WorkletA");

        assertEquals(nodeA, nodeB, "Nodes with same ID, condition, and conclusion should be equal");
        assertEquals(nodeA.hashCode(), nodeB.hashCode(), "Equal nodes should have equal hash codes");
    }

    @Test
    @DisplayName("equals returns false for nodes with different IDs")
    void testEquals_differentIds_returnsFalse() {
        RdrNode nodeA = new RdrNode(1, "priority > 5", "WorkletA");
        RdrNode nodeB = new RdrNode(2, "priority > 5", "WorkletA");

        assertNotEquals(nodeA, nodeB, "Nodes with different IDs should not be equal");
    }

    @Test
    @DisplayName("toString includes ID, condition expression, and conclusion")
    void testToString_includesKeyFields() {
        RdrNode node = new RdrNode(42, "priority > 5", "HighPriorityWorklet");
        String str = node.toString();

        assertTrue(str.contains("42"), "toString should contain node ID");
        assertTrue(str.contains("priority > 5"), "toString should contain condition");
        assertTrue(str.contains("HighPriorityWorklet"), "toString should contain conclusion");
    }

    @Test
    @DisplayName("Node with zero ID is valid (zero is non-negative)")
    void testConstructor_zeroId_isValid() {
        RdrNode node = new RdrNode(0, "priority > 5", "DefaultWorklet");
        assertEquals(0, node.getId(), "Zero ID should be valid");
    }

    @Test
    @DisplayName("evaluate with != operator correctly identifies inequality")
    void testEvaluate_notEqualsOperator_identifiesInequality() {
        RdrNode node = new RdrNode(1, "status != approved", "NonApprovedWorklet");

        assertFalse(node.evaluate(context), "status=approved should NOT match != approved");

        context.put("status", "rejected");
        assertTrue(node.evaluate(context), "status=rejected SHOULD match != approved");
    }
}
