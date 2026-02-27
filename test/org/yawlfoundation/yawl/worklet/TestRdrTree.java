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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RdrTree} — RDR tree structure, rule addition, selection logic.
 *
 * <p>Chicago TDD: Real RdrTree, RdrNode, and RdrCondition instances.
 * Tests the complete tree traversal and worklet selection logic.
 *
 * @author YAWL Foundation
 * @version 6.0.0-Beta
 */
@DisplayName("RdrTree — RDR tree structure and worklet selection logic")
@Tag("unit")
class TestRdrTree {

    private RdrTree tree;
    private Map<String, String> context;

    @BeforeEach
    void setUp() {
        tree = new RdrTree("Approve_Order");
        context = new HashMap<>();
        context.put("priority", "7");
        context.put("amount", "1500");
        context.put("status", "approved");
        context.put("department", "Finance");
    }

    @Test
    @DisplayName("Constructor creates empty tree with correct task name")
    void testConstructor_validTaskName_createsEmptyTree() {
        assertEquals("Approve_Order", tree.getTaskName(), "Task name should match");
        assertNull(tree.getRoot(), "New tree should have no root");
        assertEquals(0, tree.getNodeCount(), "New tree should have zero nodes");
        assertTrue(tree.isEmpty(), "New tree should be empty");
    }

    @Test
    @DisplayName("Constructor trims whitespace from task name")
    void testConstructor_trimsTaskName() {
        RdrTree t = new RdrTree("  My_Task  ");
        assertEquals("My_Task", t.getTaskName(), "Task name should be trimmed");
    }

    @Test
    @DisplayName("Constructor rejects null task name")
    void testConstructor_nullTaskName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RdrTree(null),
                "Null task name should be rejected");
    }

    @Test
    @DisplayName("Constructor rejects blank task name")
    void testConstructor_blankTaskName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RdrTree("   "),
                "Blank task name should be rejected");
    }

    @Test
    @DisplayName("setRoot adds root node and increments node count")
    void testSetRoot_validNode_setsRootAndIncrementsCount() {
        RdrNode root = new RdrNode(1, "priority > 5", "HighPriorityWorklet");
        tree.setRoot(root);

        assertSame(root, tree.getRoot(), "Root should be the set node");
        assertEquals(1, tree.getNodeCount(), "Node count should be 1 after setting root");
        assertFalse(tree.isEmpty(), "Tree should not be empty after setting root");
    }

    @Test
    @DisplayName("setRoot rejects null")
    void testSetRoot_null_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> tree.setRoot(null),
                "Null root should be rejected");
    }

    @Test
    @DisplayName("setRoot rejects double-setting root")
    void testSetRoot_calledTwice_throwsException() {
        RdrNode root1 = new RdrNode(1, "priority > 5", "WorkletA");
        RdrNode root2 = new RdrNode(2, "priority < 3", "WorkletB");
        tree.setRoot(root1);

        assertThrows(IllegalStateException.class,
                () -> tree.setRoot(root2),
                "Setting root twice should throw exception");
    }

    @Test
    @DisplayName("addNode attaches new node as true-child of parent")
    void testAddNode_asTrueChild_attachesCorrectly() {
        RdrNode root = new RdrNode(1, "priority > 5", "StandardWorklet");
        RdrNode child = new RdrNode(2, "priority > 9", "UrgentWorklet");
        tree.setRoot(root);
        tree.addNode(child, 1, true);

        assertSame(child, root.getTrueChild(), "Child should be root's true-child");
        assertNull(root.getFalseChild(), "Root should have no false-child");
        assertEquals(2, tree.getNodeCount(), "Node count should be 2");
    }

    @Test
    @DisplayName("addNode attaches new node as false-child of parent")
    void testAddNode_asFalseChild_attachesCorrectly() {
        RdrNode root = new RdrNode(1, "priority > 5", "HighPriorityWorklet");
        RdrNode child = new RdrNode(2, "amount > 1000", "LargeAmountWorklet");
        tree.setRoot(root);
        tree.addNode(child, 1, false);

        assertSame(child, root.getFalseChild(), "Child should be root's false-child");
        assertNull(root.getTrueChild(), "Root should have no true-child");
        assertEquals(2, tree.getNodeCount(), "Node count should be 2");
    }

    @Test
    @DisplayName("addNode rejects null new node")
    void testAddNode_nullNewNode_throwsException() {
        RdrNode root = new RdrNode(1, "priority > 5", "WorkletA");
        tree.setRoot(root);

        assertThrows(IllegalArgumentException.class,
                () -> tree.addNode(null, 1, true),
                "Null new node should be rejected");
    }

    @Test
    @DisplayName("addNode rejects missing parent ID")
    void testAddNode_invalidParentId_throwsException() {
        RdrNode root = new RdrNode(1, "priority > 5", "WorkletA");
        RdrNode child = new RdrNode(2, "priority > 9", "WorkletB");
        tree.setRoot(root);

        assertThrows(IllegalStateException.class,
                () -> tree.addNode(child, 999, true),
                "Missing parent ID should throw exception");
    }

    @Test
    @DisplayName("addNode rejects duplicate true-child")
    void testAddNode_duplicateTrueChild_throwsException() {
        RdrNode root = new RdrNode(1, "priority > 5", "WorkletA");
        RdrNode child1 = new RdrNode(2, "priority > 9", "WorkletB");
        RdrNode child2 = new RdrNode(3, "priority > 8", "WorkletC");
        tree.setRoot(root);
        tree.addNode(child1, 1, true);

        assertThrows(IllegalStateException.class,
                () -> tree.addNode(child2, 1, true),
                "Adding second true-child should throw exception");
    }

    @Test
    @DisplayName("addNode on empty tree (no root) throws exception")
    void testAddNode_emptyTree_throwsException() {
        RdrNode node = new RdrNode(1, "priority > 5", "WorkletA");

        assertThrows(IllegalStateException.class,
                () -> tree.addNode(node, 0, true),
                "Adding node to empty tree should throw exception");
    }

    @Test
    @DisplayName("select on empty tree throws exception")
    void testSelect_emptyTree_throwsException() {
        assertThrows(IllegalStateException.class,
                () -> tree.select(context),
                "select() on empty tree should throw exception");
    }

    @Test
    @DisplayName("select with null context throws exception")
    void testSelect_nullContext_throwsException() {
        RdrNode root = new RdrNode(1, "priority > 5", "WorkletA");
        tree.setRoot(root);

        assertThrows(IllegalArgumentException.class,
                () -> tree.select(null),
                "select() with null context should throw exception");
    }

    @Test
    @DisplayName("select returns root conclusion when root condition is satisfied")
    void testSelect_rootConditionSatisfied_returnsRootConclusion() {
        RdrNode root = new RdrNode(1, "priority > 5", "HighPriorityWorklet");
        tree.setRoot(root);

        String selected = tree.select(context);

        assertEquals("HighPriorityWorklet", selected,
                "Should return root conclusion when root condition is satisfied");
    }

    @Test
    @DisplayName("select returns null when root condition is not satisfied")
    void testSelect_rootConditionNotSatisfied_returnsNull() {
        RdrNode root = new RdrNode(1, "priority > 10", "UrgentWorklet");
        tree.setRoot(root);

        // context has priority=7, so priority > 10 is false
        String selected = tree.select(context);

        assertNull(selected, "Should return null when no rule is satisfied");
    }

    @Test
    @DisplayName("select follows true-child path when root is satisfied and true-child is also satisfied (refinement)")
    void testSelect_trueChildSatisfied_returnsRefinedConclusion() {
        // Root: priority > 5 → StandardWorklet
        // TrueChild: priority > 9 → UrgentWorklet
        // Context has priority=7, so root satisfied but true-child (>9) not satisfied
        // Expected: StandardWorklet (root is last satisfied)
        RdrNode root = new RdrNode(1, "priority > 5", "StandardWorklet");
        RdrNode trueChild = new RdrNode(2, "priority > 9", "UrgentWorklet");
        tree.setRoot(root);
        tree.addNode(trueChild, 1, true);

        String selected = tree.select(context);

        assertEquals("StandardWorklet", selected,
                "When true-child not satisfied, root (last satisfied) is returned");
    }

    @Test
    @DisplayName("select follows true-child path when true-child condition is also satisfied")
    void testSelect_trueChildAlsoSatisfied_returnsTrueChildConclusion() {
        // Root: priority > 5 → StandardWorklet
        // TrueChild: priority > 6 → HighWorklet (priority=7, so also satisfied)
        RdrNode root = new RdrNode(1, "priority > 5", "StandardWorklet");
        RdrNode trueChild = new RdrNode(2, "priority > 6", "HighWorklet");
        tree.setRoot(root);
        tree.addNode(trueChild, 1, true);

        String selected = tree.select(context);

        assertEquals("HighWorklet", selected,
                "When both root and true-child are satisfied, true-child (most specific) is returned");
    }

    @Test
    @DisplayName("select follows false-child path when root condition is not satisfied")
    void testSelect_rootNotSatisfied_traversesFalseChild() {
        // Root: priority > 10 → NotUsed (not satisfied for priority=7)
        // FalseChild: amount > 1000 → LargeAmountWorklet (amount=1500, satisfied)
        RdrNode root = new RdrNode(1, "priority > 10", "NotUsed");
        RdrNode falseChild = new RdrNode(2, "amount > 1000", "LargeAmountWorklet");
        tree.setRoot(root);
        tree.addNode(falseChild, 1, false);

        String selected = tree.select(context);

        assertEquals("LargeAmountWorklet", selected,
                "When root not satisfied but false-child is, false-child conclusion is returned");
    }

    @Test
    @DisplayName("select in multi-level tree returns deepest satisfied node")
    void testSelect_multiLevelTree_returnsDeepestSatisfied() {
        // Build a tree:
        // Root (1): priority > 5 → StandardWorklet
        //   TrueChild (2): priority > 6 → HighWorklet
        //     TrueChild (3): priority > 8 → UrgentWorklet (NOT satisfied for priority=7)
        //     FalseChild (4): amount < 2000 → BudgetHighWorklet (satisfied: amount=1500)
        RdrNode root = new RdrNode(1, "priority > 5", "StandardWorklet");
        RdrNode node2 = new RdrNode(2, "priority > 6", "HighWorklet");
        RdrNode node3 = new RdrNode(3, "priority > 8", "UrgentWorklet");
        RdrNode node4 = new RdrNode(4, "amount < 2000", "BudgetHighWorklet");

        tree.setRoot(root);
        tree.addNode(node2, 1, true);
        tree.addNode(node3, 2, true);
        tree.addNode(node4, 2, false);

        String selected = tree.select(context);

        // priority=7: root (>5) satisfied, node2 (>6) satisfied, node3 (>8) NOT satisfied
        // From node2's false-child (node4): amount<2000 satisfied with amount=1500
        // Wait - node4 is false-child of node2, but we traversed through node2's TRUE path
        // RDR semantics: from node2 (satisfied), go to its true-child (node3)
        // node3 not satisfied, node3 has no false-child → return node2 (last satisfied)
        // Then check node2's false-child? No - RDR goes true-child first for refinement.
        // Actually node4 is false-child of node2, not relevant when node2 is satisfied.
        // Last satisfied: node2 → HighWorklet
        assertEquals("HighWorklet", selected,
                "Should return last satisfied node in refinement chain");
    }

    @Test
    @DisplayName("findNode returns null for empty tree")
    void testFindNode_emptyTree_returnsNull() {
        assertNull(tree.findNode(1), "Should return null for empty tree");
    }

    @Test
    @DisplayName("findNode locates root node by ID")
    void testFindNode_rootId_returnsRoot() {
        RdrNode root = new RdrNode(1, "priority > 5", "WorkletA");
        tree.setRoot(root);

        RdrNode found = tree.findNode(1);

        assertSame(root, found, "Should find root by ID");
    }

    @Test
    @DisplayName("findNode locates deep node by ID")
    void testFindNode_deepNodeId_returnsCorrectNode() {
        RdrNode root = new RdrNode(1, "priority > 5", "WorkletA");
        RdrNode child = new RdrNode(2, "priority > 8", "WorkletB");
        RdrNode grandchild = new RdrNode(3, "status = urgent", "WorkletC");
        tree.setRoot(root);
        tree.addNode(child, 1, true);
        tree.addNode(grandchild, 2, true);

        RdrNode found = tree.findNode(3);

        assertSame(grandchild, found, "Should find grandchild by ID");
    }

    @Test
    @DisplayName("findNode returns null for unknown ID")
    void testFindNode_unknownId_returnsNull() {
        RdrNode root = new RdrNode(1, "priority > 5", "WorkletA");
        tree.setRoot(root);

        assertNull(tree.findNode(999), "Should return null for unknown ID");
    }

    @Test
    @DisplayName("getAllNodes returns all nodes in pre-order")
    void testGetAllNodes_returnsAllNodes() {
        RdrNode root = new RdrNode(1, "priority > 5", "WorkletA");
        RdrNode child1 = new RdrNode(2, "priority > 8", "WorkletB");
        RdrNode child2 = new RdrNode(3, "amount < 1000", "WorkletC");
        tree.setRoot(root);
        tree.addNode(child1, 1, true);
        tree.addNode(child2, 1, false);

        List<RdrNode> allNodes = tree.getAllNodes();

        assertEquals(3, allNodes.size(), "Should have all 3 nodes");
        assertTrue(allNodes.contains(root), "Should contain root");
        assertTrue(allNodes.contains(child1), "Should contain child1");
        assertTrue(allNodes.contains(child2), "Should contain child2");
    }

    @Test
    @DisplayName("getAllNodes returns empty list for empty tree")
    void testGetAllNodes_emptyTree_returnsEmptyList() {
        List<RdrNode> nodes = tree.getAllNodes();
        assertTrue(nodes.isEmpty(), "Empty tree should return empty node list");
    }

    @Test
    @DisplayName("selectNode returns the last satisfied RdrNode object")
    void testSelectNode_returnsSatisfiedNode() {
        RdrNode root = new RdrNode(1, "priority > 5", "StandardWorklet");
        tree.setRoot(root);

        RdrNode selectedNode = tree.selectNode(context);

        assertSame(root, selectedNode, "Should return the satisfied root node");
    }

    @Test
    @DisplayName("selectNode returns null when no rule is satisfied")
    void testSelectNode_noRuleSatisfied_returnsNull() {
        RdrNode root = new RdrNode(1, "priority > 10", "UrgentWorklet");
        tree.setRoot(root);

        RdrNode selectedNode = tree.selectNode(context);

        assertNull(selectedNode, "Should return null when no node is satisfied");
    }

    @Test
    @DisplayName("equals and hashCode based on task name")
    void testEqualsAndHashCode_basedOnTaskName() {
        RdrTree tree1 = new RdrTree("Approve_Order");
        RdrTree tree2 = new RdrTree("Approve_Order");
        RdrTree tree3 = new RdrTree("Process_Payment");

        assertEquals(tree1, tree2, "Trees with same task name should be equal");
        assertEquals(tree1.hashCode(), tree2.hashCode(), "Equal trees should have equal hash codes");
        assertNotEquals(tree1, tree3, "Trees with different task names should not be equal");
    }

    @Test
    @DisplayName("toString includes task name and node count")
    void testToString_includesTaskNameAndCount() {
        RdrNode root = new RdrNode(1, "priority > 5", "WorkletA");
        tree.setRoot(root);

        String str = tree.toString();

        assertTrue(str.contains("Approve_Order"), "toString should contain task name");
        assertTrue(str.contains("1"), "toString should contain node count");
    }

    @Test
    @DisplayName("Complex three-level tree correctly selects via false-child chain")
    void testSelect_falseChildChain_returnsCorrectWorklet() {
        // Root (1): amount > 5000 → BigAmountWorklet (NOT satisfied for amount=1500)
        //   FalseChild (2): amount > 1000 → MediumAmountWorklet (satisfied for amount=1500)
        //     FalseChild (3): amount > 500 → SmallAmountWorklet (not reached)
        RdrNode root = new RdrNode(1, "amount > 5000", "BigAmountWorklet");
        RdrNode node2 = new RdrNode(2, "amount > 1000", "MediumAmountWorklet");
        RdrNode node3 = new RdrNode(3, "amount > 500", "SmallAmountWorklet");
        tree.setRoot(root);
        tree.addNode(node2, 1, false);
        tree.addNode(node3, 2, false);

        String selected = tree.select(context);

        // amount=1500: root (>5000) not satisfied → node2 (>1000) satisfied → last satisfied
        assertEquals("MediumAmountWorklet", selected,
                "Should traverse false-child chain and return last satisfied node");
    }
}
