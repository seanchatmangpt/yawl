/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RdrSet} — collection of RDR trees keyed by task name.
 *
 * <p>Chicago TDD: Real RdrSet, RdrTree, RdrNode, and RdrCondition objects.
 * No mocks or stubs.
 *
 * @author YAWL Foundation
 * @version 6.0.0-Beta
 */
@DisplayName("RdrSet — per-task rule tree collection and multi-task selection")
@Tag("unit")
class TestRdrSet {

    private RdrSet rdrSet;
    private Map<String, String> context;

    @BeforeEach
    void setUp() {
        rdrSet = new RdrSet("spec-001");
        context = new HashMap<>();
        context.put("priority", "7");
        context.put("amount", "1500");
        context.put("status", "approved");
    }

    @Test
    @DisplayName("Constructor creates empty set with correct spec ID")
    void testConstructor_validSpecId_createsEmptySet() {
        assertEquals("spec-001", rdrSet.getSpecificationId(), "Spec ID should match");
        assertEquals(0, rdrSet.size(), "New set should be empty");
        assertTrue(rdrSet.isEmpty(), "New set should report empty");
    }

    @Test
    @DisplayName("Constructor trims whitespace from spec ID")
    void testConstructor_trimsSpecId() {
        RdrSet set = new RdrSet("  my-spec  ");
        assertEquals("my-spec", set.getSpecificationId(), "Spec ID should be trimmed");
    }

    @Test
    @DisplayName("Constructor rejects null spec ID")
    void testConstructor_nullSpecId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RdrSet(null),
                "Null spec ID should be rejected");
    }

    @Test
    @DisplayName("Constructor rejects blank spec ID")
    void testConstructor_blankSpecId_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new RdrSet("   "),
                "Blank spec ID should be rejected");
    }

    @Test
    @DisplayName("addTree increases size and allows retrieval by task name")
    void testAddTree_validTree_increasesSizeAndIsRetrievable() {
        RdrTree tree = buildSimpleTree("Approve_Order", "priority > 5", "ApprovalWorklet");
        rdrSet.addTree(tree);

        assertEquals(1, rdrSet.size(), "Size should be 1 after adding one tree");
        assertFalse(rdrSet.isEmpty(), "Set should not be empty");
        assertSame(tree, rdrSet.getTree("Approve_Order"), "Same tree object should be returned");
    }

    @Test
    @DisplayName("addTree rejects null tree")
    void testAddTree_nullTree_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> rdrSet.addTree(null),
                "Null tree should be rejected");
    }

    @Test
    @DisplayName("addTree rejects duplicate task name")
    void testAddTree_duplicateTaskName_throwsException() {
        RdrTree tree1 = buildSimpleTree("Approve_Order", "priority > 5", "WorkletA");
        RdrTree tree2 = buildSimpleTree("Approve_Order", "amount > 100", "WorkletB");
        rdrSet.addTree(tree1);

        assertThrows(IllegalStateException.class,
                () -> rdrSet.addTree(tree2),
                "Adding tree for duplicate task name should throw exception");
    }

    @Test
    @DisplayName("putTree adds new tree or replaces existing one")
    void testPutTree_replacesExistingTree() {
        RdrTree tree1 = buildSimpleTree("Approve_Order", "priority > 5", "WorkletA");
        RdrTree tree2 = buildSimpleTree("Approve_Order", "priority > 3", "WorkletB");

        rdrSet.putTree(tree1);
        assertEquals(1, rdrSet.size());

        rdrSet.putTree(tree2);
        assertEquals(1, rdrSet.size(), "Size should remain 1 after replacement");
        assertSame(tree2, rdrSet.getTree("Approve_Order"), "Replacement tree should be returned");
    }

    @Test
    @DisplayName("putTree rejects null tree")
    void testPutTree_nullTree_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> rdrSet.putTree(null),
                "Null tree should be rejected");
    }

    @Test
    @DisplayName("getTree returns null for unknown task name")
    void testGetTree_unknownTaskName_returnsNull() {
        assertNull(rdrSet.getTree("NonExistentTask"), "Unknown task should return null tree");
    }

    @Test
    @DisplayName("getTree returns null for null task name")
    void testGetTree_nullTaskName_returnsNull() {
        assertNull(rdrSet.getTree(null), "Null task name should return null tree");
    }

    @Test
    @DisplayName("hasTree returns true for added task and false for missing task")
    void testHasTree_correctlyReportsPresence() {
        rdrSet.addTree(buildSimpleTree("Approve_Order", "priority > 5", "WorkletA"));

        assertTrue(rdrSet.hasTree("Approve_Order"), "Should have tree for added task");
        assertFalse(rdrSet.hasTree("Process_Payment"), "Should not have tree for missing task");
        assertFalse(rdrSet.hasTree(null), "Should not have tree for null task name");
    }

    @Test
    @DisplayName("removeTree removes tree and decrements size")
    void testRemoveTree_existingTask_removesTree() {
        rdrSet.addTree(buildSimpleTree("Approve_Order", "priority > 5", "WorkletA"));
        assertEquals(1, rdrSet.size());

        RdrTree removed = rdrSet.removeTree("Approve_Order");

        assertNotNull(removed, "Removed tree should not be null");
        assertEquals(0, rdrSet.size(), "Size should be 0 after removal");
        assertFalse(rdrSet.hasTree("Approve_Order"), "Task should no longer have a tree");
    }

    @Test
    @DisplayName("removeTree returns null for non-existent task")
    void testRemoveTree_nonExistentTask_returnsNull() {
        assertNull(rdrSet.removeTree("NonExistentTask"),
                "Removing non-existent task should return null");
    }

    @Test
    @DisplayName("getTrees returns unmodifiable collection of all trees")
    void testGetTrees_returnsAllTrees() {
        rdrSet.addTree(buildSimpleTree("Approve_Order", "priority > 5", "WorkletA"));
        rdrSet.addTree(buildSimpleTree("Process_Payment", "amount > 100", "WorkletB"));
        rdrSet.addTree(buildSimpleTree("Review_Request", "status = pending", "WorkletC"));

        Collection<RdrTree> trees = rdrSet.getTrees();
        assertEquals(3, trees.size(), "Should return all 3 trees");
    }

    @Test
    @DisplayName("getTaskNames returns all task names in the set")
    void testGetTaskNames_returnsAllTaskNames() {
        rdrSet.addTree(buildSimpleTree("Task_A", "priority > 5", "WorkletA"));
        rdrSet.addTree(buildSimpleTree("Task_B", "amount > 100", "WorkletB"));

        Collection<String> taskNames = rdrSet.getTaskNames();
        assertEquals(2, taskNames.size(), "Should return all 2 task names");
        assertTrue(taskNames.contains("Task_A"), "Should contain Task_A");
        assertTrue(taskNames.contains("Task_B"), "Should contain Task_B");
    }

    @Test
    @DisplayName("select returns correct worklet for a matching task and context")
    void testSelect_matchingTaskAndContext_returnsWorklet() {
        rdrSet.addTree(buildSimpleTree("Approve_Order", "priority > 5", "HighPriorityWorklet"));

        String result = rdrSet.select("Approve_Order", context);

        assertEquals("HighPriorityWorklet", result,
                "Should return worklet when rule is satisfied");
    }

    @Test
    @DisplayName("select returns null when no rule is satisfied for the task")
    void testSelect_ruleNotSatisfied_returnsNull() {
        rdrSet.addTree(buildSimpleTree("Approve_Order", "priority > 10", "UrgentWorklet"));

        String result = rdrSet.select("Approve_Order", context);

        assertNull(result, "Should return null when no rule is satisfied");
    }

    @Test
    @DisplayName("select returns null for task with no tree in set")
    void testSelect_unknownTask_returnsNull() {
        String result = rdrSet.select("NonExistentTask", context);

        assertNull(result, "Should return null for task with no tree");
    }

    @Test
    @DisplayName("select rejects null task name")
    void testSelect_nullTaskName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> rdrSet.select(null, context),
                "Null task name should be rejected");
    }

    @Test
    @DisplayName("select rejects null context")
    void testSelect_nullContext_throwsException() {
        rdrSet.addTree(buildSimpleTree("Approve_Order", "priority > 5", "WorkletA"));

        assertThrows(IllegalArgumentException.class,
                () -> rdrSet.select("Approve_Order", null),
                "Null context should be rejected");
    }

    @Test
    @DisplayName("Multiple tasks are independently selectable")
    void testSelect_multipleTasksArIndependent() {
        rdrSet.addTree(buildSimpleTree("Task_A", "priority > 5", "HighPriorityWorklet"));
        rdrSet.addTree(buildSimpleTree("Task_B", "amount > 1000", "LargeAmountWorklet"));
        rdrSet.addTree(buildSimpleTree("Task_C", "priority > 10", "NotReachable"));

        assertEquals("HighPriorityWorklet", rdrSet.select("Task_A", context),
                "Task_A should select HighPriorityWorklet (priority=7 > 5)");
        assertEquals("LargeAmountWorklet", rdrSet.select("Task_B", context),
                "Task_B should select LargeAmountWorklet (amount=1500 > 1000)");
        assertNull(rdrSet.select("Task_C", context),
                "Task_C should return null (priority=7 not > 10)");
    }

    @Test
    @DisplayName("equals and hashCode based on specification ID")
    void testEqualsAndHashCode_basedOnSpecId() {
        RdrSet set1 = new RdrSet("spec-001");
        RdrSet set2 = new RdrSet("spec-001");
        RdrSet set3 = new RdrSet("spec-002");

        assertEquals(set1, set2, "Sets with same spec ID should be equal");
        assertEquals(set1.hashCode(), set2.hashCode(), "Equal sets should have equal hash codes");
        assertNotEquals(set1, set3, "Sets with different spec IDs should not be equal");
    }

    @Test
    @DisplayName("toString includes spec ID and tree count")
    void testToString_includesSpecIdAndTreeCount() {
        rdrSet.addTree(buildSimpleTree("Task_A", "priority > 5", "WorkletA"));
        rdrSet.addTree(buildSimpleTree("Task_B", "amount > 100", "WorkletB"));

        String str = rdrSet.toString();

        assertTrue(str.contains("spec-001"), "toString should contain spec ID");
        assertTrue(str.contains("2"), "toString should contain tree count");
    }

    // --- Helper methods ---

    /**
     * Builds a simple single-node RDR tree for testing.
     */
    private RdrTree buildSimpleTree(String taskName, String conditionExpr, String conclusion) {
        RdrTree tree = new RdrTree(taskName);
        RdrNode root = new RdrNode(1, conditionExpr, conclusion);
        tree.setRoot(root);
        return tree;
    }
}
