/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import org.jdom2.Document;
import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.exceptions.YStateException;

/**
 * Test suite for WorkflowPatternProcessor.
 * Tests all YAWL workflow pattern implementations.
 */
public class WorkflowPatternProcessorTest {

    private YNetRunner netRunner;
    private YEnabledTransitionSet enabledTransitions;
    private YPersistenceManager pmgr;
    private WorkflowPatternProcessor processor;

    @BeforeEach
    void setUp() {
        // Mock dependencies
        netRunner = mock(YNetRunner.class);
        enabledTransitions = mock(YEnabledTransitionSet.class);
        pmgr = mock(YPersistenceManager.class);

        // Initialize processor
        processor = new WorkflowPatternProcessor(netRunner, enabledTransitions, pmgr);
    }

    @Test
    void testWCP1_SequencePattern() throws Exception {
        // Setup composite task group
        YEnabledTransitionSet.TaskGroup group = mock(YEnabledTransitionSet.TaskGroup.class);
        List<YEnabledTransitionSet.TaskGroup> groups = Arrays.asList(group);

        when(enabledTransitions.getAllTaskGroups()).thenReturn(groups);
        when(group.hasCompositeTasks()).thenReturn(true);
        when(group.getTaskCount()).thenReturn(1);

        YCompositeTask compositeTask = mock(YCompositeTask.class);
        when(group.getRandomCompositeTaskFromGroup()).thenReturn(compositeTask);
        when(netRunner.endOfNetReached()).thenReturn(false);

        // Execute sequence pattern
        processor.processWorkflowPatterns();

        // Verify the composite task was fired
        verify(netRunner).fireCompositeTask(compositeTask, pmgr);
    }

    @Test
    void testWCP5_SimpleMergePattern() throws Exception {
        // Setup empty task group (XOR-join)
        YEnabledTransitionSet.TaskGroup group = mock(YEnabledTransitionSet.TaskGroup.class);
        List<YEnabledTransitionSet.TaskGroup> groups = Arrays.asList(group);

        when(enabledTransitions.getAllTaskGroups()).thenReturn(groups);
        when(group.hasCompositeTasks()).thenReturn(false);
        when(group.hasEmptyTasks()).thenReturn(true);

        YAtomicTask atomicTask = mock(YAtomicTask.class);
        when(group.getRandomEmptyTaskFromGroup()).thenReturn(atomicTask);
        when(netRunner.endOfNetReached()).thenReturn(false);

        // Execute simple merge pattern
        processor.processWorkflowPatterns();

        // Verify the atomic task was processed
        verify(netRunner).processEmptyTask(atomicTask, pmgr);
    }

    @Test
    void testWCP2_ParallelSplitPattern() throws Exception {
        // Setup AND-split task group
        YEnabledTransitionSet.TaskGroup group = mock(YEnabledTransitionSet.TaskGroup.class);
        List<YEnabledTransitionSet.TaskGroup> groups = Arrays.asList(group);

        when(enabledTransitions.getAllTaskGroups()).thenReturn(groups);
        when(group.hasCompositeTasks()).thenReturn(false);
        when(group.hasEmptyTasks()).thenReturn(false);
        when(group.getSplitType()).thenReturn(YTask.SPLIT_AND);

        List<YAtomicTask> atomicTasks = Arrays.asList(
            mock(YAtomicTask.class),
            mock(YAtomicTask.class)
        );
        when(group.getAtomicTasks()).thenReturn(atomicTasks);
        when(group.getDeferredChoiceID()).thenReturn("parallel-group");

        when(netRunner.endOfNetReached()).thenReturn(false);

        // Execute parallel split pattern
        processor.processWorkflowPatterns();

        // Verify both tasks were fired in parallel
        verify(netRunner, times(2)).fireAtomicTask(any(), anyString(), eq(pmgr));
    }

    @Test
    void testWCP4_ExclusiveChoicePattern() throws Exception {
        // Setup XOR-split task group
        YEnabledTransitionSet.TaskGroup group = mock(YEnabledTransitionSet.TaskGroup.class);
        List<YEnabledTransitionSet.TaskGroup> groups = Arrays.asList(group);

        when(enabledTransitions.getAllTaskGroups()).thenReturn(groups);
        when(group.hasCompositeTasks()).thenReturn(false);
        when(group.hasEmptyTasks()).thenReturn(false);
        when(group.getSplitType()).thenReturn(YTask.SPLIT_XOR);

        YAtomicTask task1 = mock(YAtomicTask.class);
        YAtomicTask task2 = mock(YAtomicTask.class);
        YAtomicTask selectedTask = mock(YAtomicTask.class);

        List<YAtomicTask> atomicTasks = Arrays.asList(task1, task2, selectedTask);
        when(group.getAtomicTasks()).thenReturn(atomicTasks);
        when(group.getDeferredChoiceID()).thenReturn("xor-group");

        // Setup condition evaluation
        when(task1.hasConditions()).thenReturn(true);
        when(task1.checkConditions(any())).thenReturn(false);
        when(task2.hasConditions()).thenReturn(true);
        when(task2.checkConditions(any())).thenReturn(false);
        when(selectedTask.hasConditions()).thenReturn(true);
        when(selectedTask.checkConditions(any())).thenReturn(true);

        when(netRunner.endOfNetReached()).thenReturn(false);
        when(netRunner.fireAtomicTask(selectedTask, "xor-group", pmgr))
            .thenReturn(mock(YAnnouncement.class));

        // Execute exclusive choice pattern
        processor.processWorkflowPatterns();

        // Verify only the selected task was fired
        verify(netRunner).fireAtomicTask(selectedTask, "xor-group", pmgr);
        verify(netRunner).withdrawEnabledTask(task1, pmgr);
        verify(netRunner).withdrawEnabledTask(task2, pmgr);
    }

    @Test
    void testWCP6_9_ORSplitPattern() throws Exception {
        // Setup OR-split task group
        YEnabledTransitionSet.TaskGroup group = mock(YEnabledTransitionSet.TaskGroup.class);
        List<YEnabledTransitionSet.TaskGroup> groups = Arrays.asList(group);

        when(enabledTransitions.getAllTaskGroups()).thenReturn(groups);
        when(group.hasCompositeTasks()).thenReturn(false);
        when(group.hasEmptyTasks()).thenReturn(false);
        when(group.getSplitType()).thenReturn(YTask.SPLIT_OR);

        List<YAtomicTask> atomicTasks = Arrays.asList(
            mock(YAtomicTask.class),
            mock(YAtomicTask.class)
        );
        when(group.getAtomicTasks()).thenReturn(atomicTasks);
        when(group.getDeferredChoiceID()).thenReturn("or-group");

        when(netRunner.endOfNetReached()).thenReturn(false);
        when(netRunner.fireAtomicTask(any(), anyString(), eq(pmgr)))
            .thenReturn(mock(YAnnouncement.class));

        // Execute OR-split pattern
        processor.processWorkflowPatterns();

        // Verify exactly one task was fired
        verify(netRunner, times(1)).fireAtomicTask(any(), anyString(), eq(pmgr));
    }

    @Test
    void testWCP12_15_MultipleInstancePattern() throws Exception {
        // Setup multiple instance task group
        YEnabledTransitionSet.TaskGroup group = mock(YEnabledTransitionSet.TaskGroup.class);
        List<YEnabledTransitionSet.TaskGroup> groups = Arrays.asList(group);

        when(enabledTransitions.getAllTaskGroups()).thenReturn(groups);
        when(group.hasCompositeTasks()).thenReturn(false);
        when(group.hasEmptyTasks()).thenReturn(false);
        when(group.isMultiInstance()).thenReturn(true);

        List<YAtomicTask> atomicTasks = Arrays.asList(
            mock(YAtomicTask.class),
            mock(YAtomicTask.class)
        );
        when(group.getAtomicTasks()).thenReturn(atomicTasks);
        when(group.getDeferredChoiceID()).thenReturn("multi-instance-group");

        when(netRunner.endOfNetReached()).thenReturn(false);
        when(netRunner.fireAtomicTask(any(), anyString(), eq(pmgr)))
            .thenReturn(mock(YAnnouncement.class));

        // Execute multiple instance pattern
        processor.processWorkflowPatterns();

        // Verify both instances were fired
        verify(netRunner, times(2)).fireAtomicTask(any(), anyString(), eq(pmgr));
    }

    @Test
    void testWCP3_SynchronizationPattern() throws Exception {
        // Setup task group for synchronization
        String groupID = "sync-group";
        List<YTask> tasks = Arrays.asList(mock(YTask.class), mock(YTask.class));

        // Simulate tasks that will complete
        YWorkItem completedWorkItem = mock(YWorkItem.class);
        when(completedWorkItem.hasCompletedStatus()).thenReturn(true);

        when(netRunner.getWorkItemRepository()).thenReturn(mock(YWorkItemRepository.class));
        when(netRunner.getWorkItemRepository().get(any(), any())).thenReturn(completedWorkItem);

        // Track the task group
        processor.trackTaskGroup(groupID, tasks);

        // Test synchronization
        assertTrue(processor.isTaskGroupComplete(groupID));

        // Test synchronization method
        assertDoesNotThrow(() -> processor.synchronizeTaskGroup(groupID));
    }

    @Test
    void testWCP16_18_StateBasedPattern() throws Exception {
        // Setup state-based pattern
        YEnabledTransitionSet.TaskGroup group = mock(YEnabledTransitionSet.TaskGroup.class);
        List<YEnabledTransitionSet.TaskGroup> groups = Arrays.asList(group);

        when(enabledTransitions.getAllTaskGroups()).thenReturn(groups);
        when(group.hasCompositeTasks()).thenReturn(false);
        when(group.hasEmptyTasks()).thenReturn(false);
        when(group.getSplitType()).thenReturn(YTask.SPLIT_XOR);

        YAtomicTask task = mock(YAtomicTask.class);
        List<YAtomicTask> atomicTasks = Arrays.asList(task);
        when(group.getAtomicTasks()).thenReturn(atomicTasks);

        when(netRunner.endOfNetReached()).thenReturn(false);
        when(netRunner.fireAtomicTask(task, anyString(), eq(pmgr)))
            .thenReturn(mock(YAnnouncement.class));
        when(netRunner.getNetLock()).thenReturn(new ReentrantLock());

        // Execute state-based pattern
        processor.processStateBasedPattern(group);

        // Verify the task was processed
        verify(netRunner).fireAtomicTask(task, anyString(), eq(pmgr));
    }

    @Test
    void testWCP19_25_CancellationPattern() throws Exception {
        // Setup running tasks for cancellation
        List<YTask> tasks = Arrays.asList(mock(YTask.class), mock(YTask.class));
        String groupID = "cancel-group";

        YTask task1 = tasks.get(0);
        YTask task2 = tasks.get(1);

        when(task1.t_isCompleted()).thenReturn(false);
        when(task2.t_isCompleted()).thenReturn(false);

        // Track the task group
        processor.trackTaskGroup(groupID, tasks);

        // Test cancellation
        processor.cancelRunningTasks();

        // Verify tasks were cancelled
        verify(task1).t_isCompleted();
        verify(task2).t_isCompleted();
        verify(netRunner).cancelTask(pmgr, task1.getID());
        verify(netRunner).cancelTask(pmgr, task2.getID());
    }

    @Test
    void testTaskGroupStatusTracking() {
        // Test active task count
        List<YTask> tasks = Arrays.asList(mock(YTask.class), mock(YTask.class));
        processor.trackTaskGroup("test-group", tasks);

        assertTrue(processor.hasActiveTasks());
        assertEquals(2, processor.getActiveTaskCount());
    }

    @Test
    void testEndOfNetCondition() throws Exception {
        // Setup scenario where net has reached end
        YEnabledTransitionSet.TaskGroup group = mock(YEnabledTransitionSet.TaskGroup.class);
        List<YEnabledTransitionSet.TaskGroup> groups = Arrays.asList(group);

        when(enabledTransitions.getAllTaskGroups()).thenReturn(groups);
        when(group.hasCompositeTasks()).thenReturn(true);
        when(group.getRandomCompositeTaskFromGroup()).thenReturn(mock(YCompositeTask.class));
        when(netRunner.endOfNetReached()).thenReturn(true);

        // Execute - should not fire any tasks
        processor.processWorkflowPatterns();

        // Verify no tasks were fired
        verify(netRunner, never()).fireCompositeTask(any(), any());
    }

    @Test
    void testEmptyTaskGroup() throws Exception {
        // Setup empty task group
        YEnabledTransitionSet.TaskGroup group = mock(YEnabledTransitionSet.TaskGroup.class);
        List<YEnabledTransitionSet.TaskGroup> groups = Arrays.asList(group);

        when(enabledTransitions.getAllTaskGroups()).thenReturn(groups);
        when(group.hasCompositeTasks()).thenReturn(false);
        when(group.hasEmptyTasks()).thenReturn(false);

        List<YAtomicTask> emptyTasks = new ArrayList<>();
        when(group.getAtomicTasks()).thenReturn(emptyTasks);

        // Execute - should not throw exception
        assertDoesNotThrow(() -> processor.processWorkflowPatterns());
    }
}