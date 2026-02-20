package org.yawlfoundation.yawl.util.java25.sealed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for sealed classes with exhaustive pattern matching.
 *
 * Chicago TDD: Validates sealed class hierarchies and exhaustive
 * pattern matching in switch expressions.
 */
@DisplayName("Sealed Class Exhaustive Pattern Matching")
class SealedClassPatternMatchingTest {

    sealed interface WorkflowElement permits Task, Gateway, Event {
    }

    final class Task implements WorkflowElement {
        private final String taskId;
        private final String taskName;

        public Task(String taskId, String taskName) {
            this.taskId = taskId;
            this.taskName = taskName;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getTaskName() {
            return taskName;
        }
    }

    sealed static class Gateway implements WorkflowElement permits AndGateway, OrGateway {
        protected final String gatewayId;

        public Gateway(String gatewayId) {
            this.gatewayId = gatewayId;
        }

        public String getGatewayId() {
            return gatewayId;
        }
    }

    final static class AndGateway extends Gateway {
        public AndGateway(String gatewayId) {
            super(gatewayId);
        }
    }

    final static class OrGateway extends Gateway {
        public OrGateway(String gatewayId) {
            super(gatewayId);
        }
    }

    final class Event implements WorkflowElement {
        private final String eventId;
        private final String eventType;

        public Event(String eventId, String eventType) {
            this.eventId = eventId;
            this.eventType = eventType;
        }

        public String getEventId() {
            return eventId;
        }

        public String getEventType() {
            return eventType;
        }
    }

    @Test
    @DisplayName("Exhaustive pattern matching on sealed interface")
    void testExhaustivePatternMatching() {
        WorkflowElement task = new Task("t1", "Process");
        WorkflowElement andGate = new AndGateway("g1");
        WorkflowElement orGate = new OrGateway("g2");
        WorkflowElement event = new Event("e1", "start");

        String taskResult = processElement(task);
        String andResult = processElement(andGate);
        String orResult = processElement(orGate);
        String eventResult = processElement(event);

        assertEquals("Task: t1 (Process)", taskResult);
        assertEquals("And Gateway: g1", andResult);
        assertEquals("Or Gateway: g2", orResult);
        assertEquals("Event: e1 (start)", eventResult);
    }

    private String processElement(WorkflowElement element) {
        return switch (element) {
            case Task t -> "Task: " + t.getTaskId() + " (" + t.getTaskName() + ")";
            case AndGateway g -> "And Gateway: " + g.getGatewayId();
            case OrGateway g -> "Or Gateway: " + g.getGatewayId();
            case Event e -> "Event: " + e.getEventId() + " (" + e.getEventType() + ")";
        };
    }

    @Test
    @DisplayName("Pattern matching with instanceof operator")
    void testInstanceofPatternMatching() {
        WorkflowElement element = new Task("t2", "Validate");

        if (element instanceof Task task) {
            assertEquals("t2", task.getTaskId());
            assertEquals("Validate", task.getTaskName());
        } else {
            fail("Should match Task");
        }
    }

    @Test
    @DisplayName("Pattern matching narrows type automatically")
    void testPatternMatchingTypeNarrowing() {
        WorkflowElement element = new AndGateway("g3");

        if (element instanceof Gateway gateway) {
            assertEquals("g3", gateway.getGatewayId());

            if (gateway instanceof AndGateway andGate) {
                assertEquals("g3", andGate.getGatewayId());
            } else {
                fail("Should be AndGateway");
            }
        } else {
            fail("Should match Gateway");
        }
    }

    @Test
    @DisplayName("Sealed class prevents invalid subclasses at compile time")
    void testSealedClassCompileTimeSafety() {
        WorkflowElement task = new Task("t3", "Execute");
        assertInstanceOf(Task.class, task);

        WorkflowElement gate = new AndGateway("g4");
        assertInstanceOf(Gateway.class, gate);
        assertInstanceOf(AndGateway.class, gate);
    }

    @Test
    @DisplayName("Sealed class hierarchy is correctly structured")
    void testSealedClassHierarchy() {
        Task task = new Task("t4", "Process");
        AndGateway and = new AndGateway("g5");
        OrGateway or = new OrGateway("g6");
        Event event = new Event("e2", "end");

        assertTrue(task instanceof WorkflowElement);
        assertTrue(and instanceof WorkflowElement);
        assertTrue(and instanceof Gateway);
        assertTrue(or instanceof WorkflowElement);
        assertTrue(or instanceof Gateway);
        assertTrue(event instanceof WorkflowElement);
    }

    @Test
    @DisplayName("Multiple permitted subclasses all work correctly")
    void testAllPermittedSubclasses() {
        Gateway[] gateways = {
            new AndGateway("and1"),
            new AndGateway("and2"),
            new OrGateway("or1"),
            new OrGateway("or2")
        };

        for (Gateway g : gateways) {
            String result = switch (g) {
                case AndGateway ag -> "AND";
                case OrGateway og -> "OR";
            };

            assertTrue(result.equals("AND") || result.equals("OR"));
        }
    }

    @Test
    @DisplayName("Pattern matching with guards (future feature)")
    void testPatternMatchingLogic() {
        WorkflowElement[] elements = {
            new Task("t5", "Quick"),
            new Task("t6", "LongTaskName"),
            new AndGateway("g7"),
            new Event("e3", "timeout")
        };

        for (WorkflowElement elem : elements) {
            boolean isLongName = switch (elem) {
                case Task t when t.getTaskName().length() > 5 -> true;
                case Task t -> false;
                case Gateway g -> false;
                case Event e -> false;
            };

            if (elem instanceof Task task) {
                assertEquals(task.getTaskName().length() > 5, isLongName);
            }
        }
    }

    @Test
    @DisplayName("Switch expression covers all sealed subclasses")
    void testSwitchExpressionCompleteness() {
        WorkflowElement[] elements = {
            new Task("t7", "Work"),
            new AndGateway("g8"),
            new OrGateway("g9"),
            new Event("e4", "signal")
        };

        for (WorkflowElement elem : elements) {
            String type = switch (elem) {
                case Task t -> "TASK";
                case AndGateway g -> "AND";
                case OrGateway g -> "OR";
                case Event e -> "EVENT";
            };

            assertNotNull(type);
            assertTrue(
                type.equals("TASK") || type.equals("AND") ||
                type.equals("OR") || type.equals("EVENT")
            );
        }
    }

    @Test
    @DisplayName("Sealed class prevents runtime type confusion")
    void testSealedClassTypeSafety() {
        WorkflowElement task = new Task("t8", "Execute");
        WorkflowElement and = new AndGateway("g10");

        assertNotEquals(task.getClass(), and.getClass());
        assertTrue(task instanceof Task);
        assertFalse(task instanceof Gateway);
        assertTrue(and instanceof Gateway);
        assertFalse(and instanceof Event);
    }

    @Test
    @DisplayName("Nested sealed class hierarchy works correctly")
    void testNestedSealedHierarchy() {
        Gateway gate1 = new AndGateway("nested1");
        Gateway gate2 = new OrGateway("nested2");

        assertEquals("nested1", gate1.getGatewayId());
        assertEquals("nested2", gate2.getGatewayId());

        assertTrue(gate1 instanceof AndGateway);
        assertTrue(gate2 instanceof OrGateway);
    }

    @Test
    @DisplayName("Multiple instances of same sealed subclass work independently")
    void testMultipleSealedInstances() {
        Task t1 = new Task("ta1", "A");
        Task t2 = new Task("ta2", "B");
        AndGateway g1 = new AndGateway("ga1");
        AndGateway g2 = new AndGateway("ga2");

        assertNotEquals(t1.getTaskId(), t2.getTaskId());
        assertNotEquals(g1.getGatewayId(), g2.getGatewayId());

        assertEquals("ta1", t1.getTaskId());
        assertEquals("ta2", t2.getTaskId());
        assertEquals("ga1", g1.getGatewayId());
        assertEquals("ga2", g2.getGatewayId());
    }

    @Test
    @DisplayName("Pattern matching works with null checks")
    void testPatternMatchingWithNullSafety() {
        WorkflowElement elem = new Task("t9", "Check");

        if (elem instanceof Task task) {
            assertNotNull(task.getTaskId());
            assertNotNull(task.getTaskName());
        }
    }
}
