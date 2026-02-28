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
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YMilestoneGuardedTask guard evaluation and enablement.
 *
 * Chicago TDD: Tests task-level guard evaluation against milestone conditions.
 * Validates AND, OR, XOR guard operators.
 *
 * Test Cases:
 * - Task disabled when milestone not reached
 * - Task enabled when guards satisfied (AND/OR/XOR)
 * - Guard operator switching
 * - Milestone callbacks (reached/expired)
 * - Cache updates
 *
 * @author YAWL Validation Team
 * @since 6.0
 */
@DisplayName("YMilestoneGuardedTask Guard Evaluation Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class YMilestoneGuardedTaskTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        YMilestoneGuardedTaskTest.class
    );

    private YMilestoneGuardedTask task;
    private YMilestoneCondition milestone1;
    private YMilestoneCondition milestone2;
    private YMilestoneCondition milestone3;
    private YNet container;

    @BeforeEach
    void setUp() {
        // Create container net
        YSpecification spec = new YSpecification("test-spec");
        container = spec.getNetByKey("root");

        // Create task
        task = new YMilestoneGuardedTask("task1", 1, 1, container);

        // Create milestone conditions
        milestone1 = new YMilestoneCondition("m1", "Payment", container);
        milestone2 = new YMilestoneCondition("m2", "Inventory", container);
        milestone3 = new YMilestoneCondition("m3", "Approval", container);
    }

    // ===== Test 1: Guard Addition and Retrieval =====

    @Test
    @Order(1)
    @DisplayName("T1: Add milestone guard to task")
    void testAddMilestoneGuard() {
        task.addMilestoneGuard(milestone1);

        Set<YMilestoneCondition> guards = task.getMilestoneGuards();
        assertEquals(1, guards.size(),
            "Task should have 1 guard after adding one");
        assertTrue(guards.contains(milestone1),
            "Guard set should contain added milestone");
    }

    @Test
    @Order(2)
    @DisplayName("T2: Add multiple milestone guards")
    void testAddMultipleMilestoneGuards() {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);
        task.addMilestoneGuard(milestone3);

        Set<YMilestoneCondition> guards = task.getMilestoneGuards();
        assertEquals(3, guards.size(),
            "Task should have 3 guards");
        assertTrue(guards.contains(milestone1) && guards.contains(milestone2) &&
                   guards.contains(milestone3),
            "All guards should be present");
    }

    // ===== Test 2: Guard Removal =====

    @Test
    @Order(3)
    @DisplayName("T3: Remove milestone guard from task")
    void testRemoveMilestoneGuard() {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);

        task.removeMilestoneGuard("m1");

        Set<YMilestoneCondition> guards = task.getMilestoneGuards();
        assertEquals(1, guards.size(),
            "Task should have 1 guard after removing one");
        assertFalse(guards.contains(milestone1),
            "Guard set should not contain removed milestone");
        assertTrue(guards.contains(milestone2),
            "Guard set should still contain remaining milestone");
    }

    // ===== Test 3: AND Operator (All Milestones Must Be Reached) =====

    @Test
    @Order(4)
    @DisplayName("T4: Task disabled when no milestones reached (AND)")
    void testTaskDisabledNoMilestonesReached() {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);
        task.setGuardOperator(MilestoneGuardOperator.AND);

        assertFalse(task.canExecute(),
            "Task should not execute when no milestones reached (AND)");
        assertFalse(task.areAllMilestonesReached(),
            "Not all milestones should be reached");
    }

    @Test
    @Order(5)
    @DisplayName("T5: Task disabled when some milestones reached (AND)")
    void testTaskDisabledSomeMilestonesReached() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);
        task.setGuardOperator(MilestoneGuardOperator.AND);

        // Set only one milestone reached
        milestone1.setReached(true, null);

        assertFalse(task.canExecute(),
            "Task should not execute when only some milestones reached (AND)");
        assertFalse(task.areAllMilestonesReached(),
            "Not all milestones should be reached");
    }

    @Test
    @Order(6)
    @DisplayName("T6: Task enabled when all milestones reached (AND)")
    void testTaskEnabledAllMilestonesReached() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);
        task.setGuardOperator(MilestoneGuardOperator.AND);

        milestone1.setReached(true, null);
        milestone2.setReached(true, null);

        assertTrue(task.canExecute(),
            "Task should execute when all milestones reached (AND)");
        assertTrue(task.areAllMilestonesReached(),
            "All milestones should be reached");
    }

    // ===== Test 4: OR Operator (Any Milestone Can Be Reached) =====

    @Test
    @Order(7)
    @DisplayName("T7: Task disabled when no milestones reached (OR)")
    void testTaskDisabledNoMilestonesReachedOR() {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);
        task.setGuardOperator(MilestoneGuardOperator.OR);

        assertFalse(task.canExecute(),
            "Task should not execute when no milestones reached (OR)");
        assertFalse(task.isAnyMilestoneReached(),
            "No milestones should be reached");
    }

    @Test
    @Order(8)
    @DisplayName("T8: Task enabled when any milestone reached (OR)")
    void testTaskEnabledAnyMilestoneReached() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);
        task.setGuardOperator(MilestoneGuardOperator.OR);

        milestone1.setReached(true, null);

        assertTrue(task.canExecute(),
            "Task should execute when any milestone reached (OR)");
        assertTrue(task.isAnyMilestoneReached(),
            "At least one milestone should be reached");
    }

    @Test
    @Order(9)
    @DisplayName("T9: Task enabled when all milestones reached (OR)")
    void testTaskEnabledAllMilestonesReachedOR() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);
        task.setGuardOperator(MilestoneGuardOperator.OR);

        milestone1.setReached(true, null);
        milestone2.setReached(true, null);

        assertTrue(task.canExecute(),
            "Task should execute when all milestones reached (OR)");
        assertTrue(task.isAnyMilestoneReached(),
            "At least one milestone should be reached");
    }

    // ===== Test 5: XOR Operator (Exactly One Milestone) =====

    @Test
    @Order(10)
    @DisplayName("T10: Task disabled when no milestones reached (XOR)")
    void testTaskDisabledNoMilestonesReachedXOR() {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);
        task.setGuardOperator(MilestoneGuardOperator.XOR);

        assertFalse(task.canExecute(),
            "Task should not execute when no milestones reached (XOR)");
    }

    @Test
    @Order(11)
    @DisplayName("T11: Task enabled when exactly one milestone reached (XOR)")
    void testTaskEnabledExactlyOneMilestoneReached() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);
        task.setGuardOperator(MilestoneGuardOperator.XOR);

        milestone1.setReached(true, null);

        assertTrue(task.canExecute(),
            "Task should execute when exactly one milestone reached (XOR)");
    }

    @Test
    @Order(12)
    @DisplayName("T12: Task disabled when multiple milestones reached (XOR)")
    void testTaskDisabledMultipleMilestonesReachedXOR() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);
        task.setGuardOperator(MilestoneGuardOperator.XOR);

        milestone1.setReached(true, null);
        milestone2.setReached(true, null);

        assertFalse(task.canExecute(),
            "Task should not execute when multiple milestones reached (XOR)");
    }

    // ===== Test 6: Guard Operator Switching =====

    @Test
    @Order(13)
    @DisplayName("T13: Switch guard operator from AND to OR")
    void testSwitchGuardOperator() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.addMilestoneGuard(milestone2);

        // Start with AND - requires all
        task.setGuardOperator(MilestoneGuardOperator.AND);
        milestone1.setReached(true, null);
        assertFalse(task.canExecute(),
            "With AND, should not execute with only 1 milestone reached");

        // Switch to OR - requires any
        task.setGuardOperator(MilestoneGuardOperator.OR);
        assertTrue(task.canExecute(),
            "With OR, should execute with 1 milestone reached");

        assertEquals(MilestoneGuardOperator.OR, task.getGuardOperator(),
            "Guard operator should be OR");
    }

    // ===== Test 7: Milestone Callbacks =====

    @Test
    @Order(14)
    @DisplayName("T14: On milestone reached callback updates cache")
    void testOnMilestoneReachedCallback() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.setGuardOperator(MilestoneGuardOperator.OR);

        // Manually update cache via callback
        task.onMilestoneReached("m1");

        assertTrue(task.canExecute(),
            "Task should be executable after milestone reached callback");
    }

    @Test
    @Order(15)
    @DisplayName("T15: On milestone expired callback updates cache")
    void testOnMilestoneExpiredCallback() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.setGuardOperator(MilestoneGuardOperator.AND);

        // Set initial state
        task.onMilestoneReached("m1");
        assertTrue(task.canExecute(),
            "Task should be executable when milestone reached");

        // Expire the milestone
        task.onMilestoneExpired("m1");
        assertFalse(task.canExecute(),
            "Task should not be executable after milestone expired");
    }

    // ===== Test 8: Edge Cases =====

    @Test
    @Order(16)
    @DisplayName("T16: Empty guard set")
    void testEmptyGuardSet() {
        task.setGuardOperator(MilestoneGuardOperator.AND);

        assertFalse(task.canExecute(),
            "Task should not execute with empty guard set (AND)");
        assertFalse(task.areAllMilestonesReached(),
            "All milestones check should return false with empty set");
    }

    @Test
    @Order(17)
    @DisplayName("T17: Single milestone with OR")
    void testSingleMilestoneOR() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.setGuardOperator(MilestoneGuardOperator.OR);

        milestone1.setReached(true, null);
        assertTrue(task.canExecute(),
            "Task should execute with single reached milestone (OR)");
    }

    @Test
    @Order(18)
    @DisplayName("T18: Single milestone with XOR")
    void testSingleMilestoneXOR() throws Exception {
        task.addMilestoneGuard(milestone1);
        task.setGuardOperator(MilestoneGuardOperator.XOR);

        milestone1.setReached(true, null);
        assertTrue(task.canExecute(),
            "Task should execute with single reached milestone (XOR)");
    }

    @Test
    @Order(19)
    @DisplayName("T19: Get guards returns copy (defensive)")
    void testGetGuardsReturnsCopy() {
        task.addMilestoneGuard(milestone1);

        Set<YMilestoneCondition> guards = task.getMilestoneGuards();
        int originalSize = guards.size();

        // Modify returned set
        guards.clear();

        // Original should be unchanged
        Set<YMilestoneCondition> guardsAgain = task.getMilestoneGuards();
        assertEquals(originalSize, guardsAgain.size(),
            "Returned guard set should be a copy, not a direct reference");
    }

    // ===== Test 9: Default Operator =====

    @Test
    @Order(20)
    @DisplayName("T20: Default guard operator is AND")
    void testDefaultOperatorIsAND() {
        assertEquals(MilestoneGuardOperator.AND, task.getGuardOperator(),
            "Default guard operator should be AND");
    }
}
