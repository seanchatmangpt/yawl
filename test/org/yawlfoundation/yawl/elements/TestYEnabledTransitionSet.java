package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

/**
 * Chicago TDD tests for YEnabledTransitionSet.
 * Tests deferred choice and TaskGroup operations.
 */
@DisplayName("YEnabledTransitionSet Tests")
@Tag("unit")
class TestYEnabledTransitionSet {

    private YEnabledTransitionSet transitionSet;
    private YSpecification spec;
    private YNet net;

    @BeforeEach
    void setUp() {
        transitionSet = new YEnabledTransitionSet();
        spec = new YSpecification("http://test.com/test-spec");
        net = new YNet("testNet", spec);
    }

    @Nested
    @DisplayName("YEnabledTransitionSet Creation Tests")
    class YEnabledTransitionSetCreationTests {

        @Test
        @DisplayName("TransitionSet should be empty initially")
        void transitionSetIsEmptyInitially() {
            assertTrue(transitionSet.isEmpty());
        }

        @Test
        @DisplayName("GetAllTaskGroups should return empty set initially")
        void getAllTaskGroupsReturnsEmptySetInitially() {
            assertTrue(transitionSet.getAllTaskGroups().isEmpty());
        }
    }

    @Nested
    @DisplayName("Add Task Tests")
    class AddTaskTests {

        @Test
        @DisplayName("Add task should increase size")
        void addTaskIncreasesSize() {
            YAtomicTask task = createEnabledTask("task1", "cond1");
            transitionSet.add(task);

            assertFalse(transitionSet.isEmpty());
        }

        @Test
        @DisplayName("Add multiple tasks creates task groups")
        void addMultipleTasksCreatesTaskGroups() {
            YAtomicTask task1 = createEnabledTask("task1", "cond1");
            YAtomicTask task2 = createEnabledTask("task2", "cond1");

            transitionSet.add(task1);
            transitionSet.add(task2);

            Set<YEnabledTransitionSet.TaskGroup> groups = transitionSet.getAllTaskGroups();
            assertEquals(1, groups.size()); // Both tasks enabled by same condition
        }

        @Test
        @DisplayName("Add tasks from different conditions creates multiple groups")
        void addTasksFromDifferentConditionsCreatesMultipleGroups() {
            YAtomicTask task1 = createEnabledTask("task1", "cond1");
            YAtomicTask task2 = createEnabledTask("task2", "cond2");

            transitionSet.add(task1);
            transitionSet.add(task2);

            Set<YEnabledTransitionSet.TaskGroup> groups = transitionSet.getAllTaskGroups();
            assertEquals(2, groups.size());
        }
    }

    @Nested
    @DisplayName("TaskGroup Tests")
    class TaskGroupTests {

        @Test
        @DisplayName("TaskGroup has unique ID")
        void taskGroupHasUniqueId() {
            YAtomicTask task1 = createEnabledTask("task1", "cond1");
            YAtomicTask task2 = createEnabledTask("task2", "cond2");

            transitionSet.add(task1);
            transitionSet.add(task2);

            Set<YEnabledTransitionSet.TaskGroup> groups = transitionSet.getAllTaskGroups();
            java.util.Iterator<YEnabledTransitionSet.TaskGroup> iter = groups.iterator();

            String id1 = iter.next().getID();
            String id2 = iter.next().getID();

            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("TaskGroup with atomic tasks returns correct count")
        void taskGroupWithAtomicTasksReturnsCorrectCount() {
            YAtomicTask task1 = createEnabledTaskWithDecomposition("task1", "cond1");
            YAtomicTask task2 = createEnabledTaskWithDecomposition("task2", "cond1");

            transitionSet.add(task1);
            transitionSet.add(task2);

            YEnabledTransitionSet.TaskGroup group = transitionSet.getAllTaskGroups().iterator().next();
            assertEquals(2, group.getAtomicTasks().size());
        }

        @Test
        @DisplayName("TaskGroup hasEmptyTasks returns correct value")
        void taskGroupHasEmptyTasksReturnsCorrectValue() {
            YAtomicTask emptyTask = createEnabledTask("emptyTask", "cond1"); // No decomposition

            transitionSet.add(emptyTask);

            YEnabledTransitionSet.TaskGroup group = transitionSet.getAllTaskGroups().iterator().next();
            assertTrue(group.hasEmptyTasks());
            assertEquals(1, group.getEmptyTaskCount());
        }

        @Test
        @DisplayName("TaskGroup getDeferredChoiceID returns ID when multiple atomic tasks")
        void taskGroupGetDeferredChoiceIdReturnsIdWhenMultipleAtomicTasks() {
            YAtomicTask task1 = createEnabledTaskWithDecomposition("task1", "cond1");
            YAtomicTask task2 = createEnabledTaskWithDecomposition("task2", "cond1");

            transitionSet.add(task1);
            transitionSet.add(task2);

            YEnabledTransitionSet.TaskGroup group = transitionSet.getAllTaskGroups().iterator().next();
            assertNotNull(group.getDeferredChoiceID());
        }

        @Test
        @DisplayName("TaskGroup getDeferredChoiceID returns null for single task")
        void taskGroupGetDeferredChoiceIdReturnsNullForSingleTask() {
            YAtomicTask task = createEnabledTaskWithDecomposition("task", "cond1");

            transitionSet.add(task);

            YEnabledTransitionSet.TaskGroup group = transitionSet.getAllTaskGroups().iterator().next();
            assertNull(group.getDeferredChoiceID());
        }

        @Test
        @DisplayName("TaskGroup with composite tasks")
        void taskGroupWithCompositeTasks() throws YPersistenceException {
            YCompositeTask composite = new YCompositeTask("composite", YTask._AND, YTask._AND, net);
            YCondition cond = new YCondition("cond1", net);
            cond.add(null, new YIdentifier("test"));
            composite.addPreset(new YFlow(cond, composite));

            transitionSet.add(composite);

            YEnabledTransitionSet.TaskGroup group = transitionSet.getAllTaskGroups().iterator().next();
            assertTrue(group.hasCompositeTasks());
            assertEquals(1, group.getCompositeTaskCount());
        }

        @Test
        @DisplayName("TaskGroup getRandomCompositeTaskFromGroup returns task when available")
        void taskGroupGetRandomCompositeTaskFromGroupReturnsTask() throws YPersistenceException {
            YCompositeTask composite = new YCompositeTask("composite", YTask._AND, YTask._AND, net);
            YCondition cond = new YCondition("cond1", net);
            cond.add(null, new YIdentifier("test"));
            composite.addPreset(new YFlow(cond, composite));

            transitionSet.add(composite);

            YEnabledTransitionSet.TaskGroup group = transitionSet.getAllTaskGroups().iterator().next();
            assertNotNull(group.getRandomCompositeTaskFromGroup());
        }

        @Test
        @DisplayName("TaskGroup getRandomCompositeTaskFromGroup returns null when none")
        void taskGroupGetRandomCompositeTaskFromGroupReturnsNullWhenNone() {
            YAtomicTask task = createEnabledTaskWithDecomposition("task", "cond1");

            transitionSet.add(task);

            YEnabledTransitionSet.TaskGroup group = transitionSet.getAllTaskGroups().iterator().next();
            assertNull(group.getRandomCompositeTaskFromGroup());
        }

        @Test
        @DisplayName("TaskGroup getRandomEmptyTaskFromGroup returns task when available")
        void taskGroupGetRandomEmptyTaskFromGroupReturnsTask() {
            YAtomicTask emptyTask = createEnabledTask("emptyTask", "cond1");

            transitionSet.add(emptyTask);

            YEnabledTransitionSet.TaskGroup group = transitionSet.getAllTaskGroups().iterator().next();
            assertNotNull(group.getRandomEmptyTaskFromGroup());
        }
    }

    /**
     * Creates an atomic task enabled by a condition with a token.
     */
    private YAtomicTask createEnabledTask(String taskId, String conditionId) {
        YAtomicTask task = new YAtomicTask(taskId, YTask._AND, YTask._AND, net);
        YCondition cond = new YCondition(conditionId, net);

        try {
            cond.add(null, new YIdentifier("test"));
        } catch (Exception e) {
            // Ignore for test setup
        }

        task.addPreset(new YFlow(cond, task));

        return task;
    }

    /**
     * Creates an atomic task with decomposition enabled by a condition.
     */
    private YAtomicTask createEnabledTaskWithDecomposition(String taskId, String conditionId) {
        YAtomicTask task = createEnabledTask(taskId, conditionId);
        YAWLServiceGateway gateway = new YAWLServiceGateway(taskId + "Gateway", spec);
        task.setDecompositionPrototype(gateway);
        return task;
    }
}
