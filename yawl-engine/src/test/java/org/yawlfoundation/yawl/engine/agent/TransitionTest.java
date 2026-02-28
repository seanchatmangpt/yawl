package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Petri-net Transition class.
 * Tests transition firing logic with atomic token consumption/production.
 *
 * @since Java 21
 */
@DisplayName("Petri-Net Transition Tests")
class TransitionTest {

    private String transitionId;
    private String transitionName;
    private String taskType;
    private Place inputPlace;
    private Place outputPlace;

    @BeforeEach
    void setUp() {
        transitionId = "transition_001";
        transitionName = "ApproveInvoice";
        taskType = "manual";
        inputPlace = new Place("place_001", "Pending", 5);
        outputPlace = new Place("place_002", "Approved", 0);
    }

    @Nested
    @DisplayName("Construction and Initialization")
    class ConstructionTests {

        @Test
        @DisplayName("Create regular transition")
        void testCreateRegular() {
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertEquals(transitionId, t.id());
            assertEquals(transitionName, t.name());
            assertEquals(taskType, t.taskType());
            assertFalse(t.isFinal());
            assertFalse(t.isMultiInstance());
        }

        @Test
        @DisplayName("Create final transition")
        void testCreateFinal() {
            Transition t = new Transition(transitionId, transitionName, taskType, true, false);

            assertTrue(t.isFinal());
            assertFalse(t.isMultiInstance());
        }

        @Test
        @DisplayName("Create multi-instance transition")
        void testCreateMultiInstance() {
            Transition t = new Transition(transitionId, transitionName, taskType, false, true);

            assertFalse(t.isFinal());
            assertTrue(t.isMultiInstance());
        }

        @Test
        @DisplayName("Create final multi-instance transition")
        void testCreateFinalMultiInstance() {
            Transition t = new Transition(transitionId, transitionName, taskType, true, true);

            assertTrue(t.isFinal());
            assertTrue(t.isMultiInstance());
        }

        @Test
        @DisplayName("Reject null ID")
        void testNullId() {
            assertThrows(NullPointerException.class, () ->
                    new Transition(null, transitionName, taskType)
            );
        }

        @Test
        @DisplayName("Reject null name")
        void testNullName() {
            assertThrows(NullPointerException.class, () ->
                    new Transition(transitionId, null, taskType)
            );
        }

        @Test
        @DisplayName("Reject null taskType")
        void testNullTaskType() {
            assertThrows(NullPointerException.class, () ->
                    new Transition(transitionId, transitionName, null)
            );
        }
    }

    @Nested
    @DisplayName("Firing Preconditions")
    class FiringPreconditionTests {

        @Test
        @DisplayName("Can fire when input has tokens")
        void testCanFireWithTokens() {
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertTrue(t.canFire(inputPlace));
        }

        @Test
        @DisplayName("Cannot fire when input is empty")
        void testCannotFireEmpty() {
            Place empty = new Place("empty", "Empty");
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertFalse(t.canFire(empty));
        }

        @Test
        @DisplayName("Can fire with multiple inputs")
        void testCanFireMultipleInputs() {
            Place input2 = new Place("place_003", "Secondary", 2);
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertTrue(t.canFire(inputPlace, input2));
        }

        @Test
        @DisplayName("Cannot fire if any input is empty")
        void testCannotFirePartialInputs() {
            Place emptyInput = new Place("empty", "Empty");
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertFalse(t.canFire(inputPlace, emptyInput));
        }

        @Test
        @DisplayName("Cannot fire with no inputs")
        void testCannotFireNoInputs() {
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertFalse(t.canFire(new Place[0]));
        }

        @Test
        @DisplayName("Reject null input place in single input")
        void testNullInputPlace() {
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertThrows(NullPointerException.class, () ->
                    t.canFire((Place) null)
            );
        }

        @Test
        @DisplayName("Reject null array in multiple inputs")
        void testNullInputArray() {
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertThrows(NullPointerException.class, () ->
                    t.canFire((Place[]) null)
            );
        }
    }

    @Nested
    @DisplayName("Basic Transition Firing")
    class BasicFiringTests {

        @Test
        @DisplayName("Fire consumes input token")
        void testFireConsumesToken() {
            Transition t = new Transition(transitionId, transitionName, taskType);
            int initialCount = inputPlace.getTokenCount();

            boolean success = t.fire(inputPlace, outputPlace);

            assertTrue(success);
            assertEquals(initialCount - 1, inputPlace.getTokenCount());
        }

        @Test
        @DisplayName("Fire produces output token")
        void testFireProducesToken() {
            Transition t = new Transition(transitionId, transitionName, taskType);
            int initialCount = outputPlace.getTokenCount();

            boolean success = t.fire(inputPlace, outputPlace);

            assertTrue(success);
            assertEquals(initialCount + 1, outputPlace.getTokenCount());
        }

        @Test
        @DisplayName("Cannot fire without input tokens")
        void testCannotFireNoTokens() {
            Place empty = new Place("empty", "Empty");
            Transition t = new Transition(transitionId, transitionName, taskType);

            boolean success = t.fire(empty, outputPlace);

            assertFalse(success);
            assertEquals(0, empty.getTokenCount());
            assertEquals(0, outputPlace.getTokenCount());
        }

        @Test
        @DisplayName("Fire is atomic (all-or-nothing)")
        void testFireAtomic() {
            Transition t = new Transition(transitionId, transitionName, taskType);
            Place singleToken = new Place("single", "Single", 1);

            boolean success = t.fire(singleToken, outputPlace);

            assertTrue(success);
            assertEquals(0, singleToken.getTokenCount());
            assertEquals(1, outputPlace.getTokenCount());
        }

        @Test
        @DisplayName("Reject null input in fire")
        void testFireNullInput() {
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertThrows(NullPointerException.class, () ->
                    t.fire(null, outputPlace)
            );
        }

        @Test
        @DisplayName("Reject null output in fire")
        void testFireNullOutput() {
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertThrows(NullPointerException.class, () ->
                    t.fire(inputPlace, (Place) null)
            );
        }
    }

    @Nested
    @DisplayName("Multi-Output Transition Firing")
    class MultiOutputFiringTests {

        @Test
        @DisplayName("Fire to multiple outputs")
        void testFireMultipleOutputs() {
            Place output2 = new Place("place_003", "Alternate", 0);
            Transition t = new Transition(transitionId, transitionName, taskType);

            boolean success = t.fire(inputPlace, outputPlace, output2);

            assertTrue(success);
            assertEquals(4, inputPlace.getTokenCount());
            assertEquals(1, outputPlace.getTokenCount());
            assertEquals(1, output2.getTokenCount());
        }

        @Test
        @DisplayName("Fire cannot occur without input")
        void testMultiFireNoInput() {
            Place empty = new Place("empty", "Empty");
            Place output2 = new Place("place_003", "Alternate", 0);
            Transition t = new Transition(transitionId, transitionName, taskType);

            boolean success = t.fire(empty, outputPlace, output2);

            assertFalse(success);
            assertEquals(0, outputPlace.getTokenCount());
            assertEquals(0, output2.getTokenCount());
        }

        @Test
        @DisplayName("Reject null outputs array")
        void testNullOutputArray() {
            Transition t = new Transition(transitionId, transitionName, taskType);

            assertThrows(NullPointerException.class, () ->
                    t.fire(inputPlace, (Place[]) null)
            );
        }
    }

    @Nested
    @DisplayName("Equality and Identity")
    class EqualityTests {

        @Test
        @DisplayName("Transitions with same ID and name are equal")
        void testEquality() {
            Transition t1 = new Transition(transitionId, transitionName, "manual");
            Transition t2 = new Transition(transitionId, transitionName, "automatic");

            assertEquals(t1, t2);
            assertEquals(t1.hashCode(), t2.hashCode());
        }

        @Test
        @DisplayName("Task type excluded from equality")
        void testEqualityIgnoresTaskType() {
            Transition t1 = new Transition(transitionId, transitionName, "manual");
            Transition t2 = new Transition(transitionId, transitionName, "service");

            assertEquals(t1, t2);
        }

        @Test
        @DisplayName("Final status excluded from equality")
        void testEqualityIgnoresFinal() {
            Transition t1 = new Transition(transitionId, transitionName, taskType, true, false);
            Transition t2 = new Transition(transitionId, transitionName, taskType, false, false);

            assertEquals(t1, t2);
        }

        @Test
        @DisplayName("Different IDs are not equal")
        void testDifferentIds() {
            Transition t1 = new Transition("t_001", transitionName, taskType);
            Transition t2 = new Transition("t_002", transitionName, taskType);

            assertNotEquals(t1, t2);
        }

        @Test
        @DisplayName("Different names are not equal")
        void testDifferentNames() {
            Transition t1 = new Transition(transitionId, "Approve", taskType);
            Transition t2 = new Transition(transitionId, "Reject", taskType);

            assertNotEquals(t1, t2);
        }
    }

    @Nested
    @DisplayName("String Representation")
    class ToStringTests {

        @Test
        @DisplayName("toString includes transition information")
        void testToString() {
            Transition t = new Transition(transitionId, transitionName, taskType, false, false);
            String str = t.toString();

            assertTrue(str.contains("Transition"));
            assertTrue(str.contains(transitionId));
            assertTrue(str.contains(transitionName));
            assertTrue(str.contains(taskType));
        }

        @Test
        @DisplayName("toString shows final status")
        void testToStringFinal() {
            Transition t = new Transition(transitionId, transitionName, taskType, true, false);
            String str = t.toString();

            assertTrue(str.contains("final=true"));
        }

        @Test
        @DisplayName("toString shows multi-instance status")
        void testToStringMultiInstance() {
            Transition t = new Transition(transitionId, transitionName, taskType, false, true);
            String str = t.toString();

            assertTrue(str.contains("multiInstance=true"));
        }
    }

    @Nested
    @DisplayName("Task Type Variations")
    class TaskTypeTests {

        @Test
        @DisplayName("Manual task type")
        void testManualTask() {
            Transition t = new Transition(transitionId, transitionName, "manual");
            assertEquals("manual", t.taskType());
        }

        @Test
        @DisplayName("Automatic task type")
        void testAutomaticTask() {
            Transition t = new Transition(transitionId, transitionName, "automatic");
            assertEquals("automatic", t.taskType());
        }

        @Test
        @DisplayName("Service task type")
        void testServiceTask() {
            Transition t = new Transition(transitionId, transitionName, "service");
            assertEquals("service", t.taskType());
        }
    }

    @Nested
    @DisplayName("Complex Workflow Patterns")
    class WorkflowPatternTests {

        @Test
        @DisplayName("Split pattern: one input, multiple outputs")
        void testSplitPattern() {
            Place output2 = new Place("branch2", "Branch2", 0);
            Place output3 = new Place("branch3", "Branch3", 0);
            Transition split = new Transition("split_001", "Split", "automatic");

            boolean success = split.fire(inputPlace, outputPlace, output2, output3);

            assertTrue(success);
            assertEquals(1, outputPlace.getTokenCount());
            assertEquals(1, output2.getTokenCount());
            assertEquals(1, output3.getTokenCount());
        }

        @Test
        @DisplayName("Sequential transitions")
        void testSequentialFiring() {
            Transition t1 = new Transition("t_001", "Task1", "manual");
            Transition t2 = new Transition("t_002", "Task2", "automatic");

            Place p1 = new Place("p1", "P1", 1);
            Place p2 = new Place("p2", "P2", 0);
            Place p3 = new Place("p3", "P3", 0);

            // First transition fires
            assertTrue(t1.fire(p1, p2));
            assertEquals(0, p1.getTokenCount());
            assertEquals(1, p2.getTokenCount());

            // Second transition fires
            assertTrue(t2.fire(p2, p3));
            assertEquals(0, p2.getTokenCount());
            assertEquals(1, p3.getTokenCount());
        }
    }

    @Nested
    @DisplayName("Concurrent Firing")
    class ConcurrentFiringTests {

        @Test
        @DisplayName("Concurrent fires on different transitions")
        void testConcurrentFiring() throws InterruptedException {
            Place p1 = new Place("p1", "P1", 100);
            Place p2 = new Place("p2", "P2", 0);

            Transition t1 = new Transition("t_001", "Task1", "automatic");
            Transition t2 = new Transition("t_002", "Task2", "automatic");

            int threadCount = 4;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 25; j++) {
                        if (p1.hasTokens()) {
                            t1.fire(p1, p2);
                        }
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(0, p1.getTokenCount());
            assertEquals(100, p2.getTokenCount());
        }
    }
}
