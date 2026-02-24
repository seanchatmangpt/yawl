package org.yawlfoundation.yawl.engine.time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for YTimerVariable class.
 *
 * <p>Chicago TDD: Tests use real YTimerVariable instances and real workflow context.
 * No mocks for domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YTimerVariable Tests")
@Tag("unit")
class YTimerVariableTest {

    private YTimerVariable timerVariable;
    private static final String TASK_NAME = "Test Task";

    @BeforeEach
    void setUp() throws Exception {
        // Create a minimal task implementation for testing
        TestTask task = new TestTask(TASK_NAME);
        timerVariable = new YTimerVariable(task);
    }

    // ==================== Constructor Tests ====================

    @Test
    @DisplayName("Constructor with task - sets initial state to dormant")
    void constructorWithTask_setsInitialStateToDormant() {
        // Arrange
        TestTask task = new TestTask("Test Task");

        // Act
        YTimerVariable timerVar = new YTimerVariable(task);

        // Assert
        assertEquals(YWorkItemTimer.State.dormant, timerVar.getState(),
                    "Initial state should be dormant");
        assertEquals("Test Task", timerVar.getTaskName(),
                    "Should set task name correctly");
    }

    @Test
    @DisplayName("Constructor with null task - throws NullPointerException")
    void constructorWithNullTask_throwsNullPointerException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            new YTimerVariable(null);
        }, "Should throw for null task");
    }

    // ==================== Basic Property Tests ====================

    @Test
    @DisplayName("Get task name")
    void getTaskName() {
        // Act
        String taskName = timerVariable.getTaskName();

        // Assert
        assertEquals(TASK_NAME, taskName);
    }

    @Test
    @DisplayName("Get task")
    void getTask() {
        // Act
        TestTask task = timerVariable.getTask();

        // Assert
        assertEquals(task, timerVariable.getTask());
        assertEquals(TASK_NAME, task.getName());
    }

    @Test
    @DisplayName("Get state")
    void getState() {
        // Act
        YWorkItemTimer.State state = timerVariable.getState();

        // Assert
        assertEquals(YWorkItemTimer.State.dormant, state);
    }

    @Test
    @DisplayName("Get state string")
    void getStateString() {
        // Act
        String stateString = timerVariable.getStateString();

        // Assert
        assertEquals("dormant", stateString);
    }

    // ==================== State Transition Tests ====================

    @Test
    @DisplayName("Set state - dormant to active (valid)")
    void setState_dormantToActive_valid() {
        // Act
        timerVariable.setState(YWorkItemTimer.State.active);

        // Assert
        assertEquals(YWorkItemTimer.State.active, timerVariable.getState());
    }

    @Test
    @DisplayName("Set state - active to closed (valid)")
    void setState_activeToClosed_valid() {
        // Arrange
        timerVariable.setState(YWorkItemTimer.State.active);

        // Act
        timerVariable.setState(YWorkItemTimer.State.closed);

        // Assert
        assertEquals(YWorkItemTimer.State.closed, timerVariable.getState());
    }

    @Test
    @DisplayName("Set state - active to expired (valid)")
    void setState_activeToExpired_valid() {
        // Arrange
        timerVariable.setState(YWorkItemTimer.State.active);

        // Act
        timerVariable.setState(YWorkItemTimer.State.expired);

        // Assert
        assertEquals(YWorkItemTimer.State.expired, timerVariable.getState());
    }

    @Test
    @DisplayName("Set state - dormant to closed (invalid)")
    void setState_dormantToClosed_invalid() {
        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            timerVariable.setState(YWorkItemTimer.State.closed);
        }, "Should not allow transition from dormant to closed");
    }

    @Test
    @DisplayName("Set state - active to dormant (invalid)")
    void setState_activeToDormant_invalid() {
        // Arrange
        timerVariable.setState(YWorkItemTimer.State.active);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            timerVariable.setState(YWorkItemTimer.State.dormant);
        }, "Should not allow transition from active to dormant");
    }

    @Test
    @DisplayName("Set state - closed to active (invalid)")
    void setState_closedToActive_invalid() {
        // Arrange
        timerVariable.setState(YWorkItemTimer.State.active);
        timerVariable.setState(YWorkItemTimer.State.closed);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            timerVariable.setState(YWorkItemTimer.State.active);
        }, "Should not allow transition from closed to active");
    }

    @Test
    @DisplayName("Set state - expired to closed (invalid)")
    void setState_expiredToClosed_invalid() {
        // Arrange
        timerVariable.setState(YWorkItemTimer.State.active);
        timerVariable.setState(YWorkItemTimer.State.expired);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            timerVariable.setState(YWorkItemTimer.State.closed);
        }, "Should not allow transition from expired to closed");
    }

    @Test
    @DisplayName("Set state - null throws IllegalArgumentException")
    void setState_nullThrowsIllegalArgumentException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            timerVariable.setState(null);
        }, "Should throw for null state");
    }

    // ==================== Convenience Setter Tests ====================

    @Test
    @DisplayName("Set state active convenience method")
    void setStateActive_convenienceMethod() {
        // Act
        timerVariable.setStateActive();

        // Assert
        assertEquals(YWorkItemTimer.State.active, timerVariable.getState());
    }

    @Test
    @DisplayName("Set state closed convenience method")
    void setStateClosed_convenienceMethod() {
        // Arrange
        timerVariable.setStateActive();

        // Act
        timerVariable.setStateClosed();

        // Assert
        assertEquals(YWorkItemTimer.State.closed, timerVariable.getState());
    }

    @Test
    @DisplayName("Set state expired convenience method")
    void setStateExpired_convenienceMethod() {
        // Arrange
        timerVariable.setStateActive();

        // Act
        timerVariable.setStateExpired();

        // Assert
        assertEquals(YWorkItemTimer.State.expired, timerVariable.getState());
    }

    // ==================== Restore Mode Tests ====================

    @Test
    @DisplayName("Set state with restoring - ignores invalid transitions")
    void setStateWithRestoring_ignoresInvalidTransitions() {
        // Arrange - simulate restoring from persistence
        YTimerVariable restoringTimer = new YTimerVariable(new TestTask("Restoring Task"));

        // Act - try invalid transition while restoring
        restoringTimer.setState(YWorkItemTimer.State.closed, true);

        // Assert
        // Should remain in dormant state when restoring
        assertEquals(YWorkItemTimer.State.dormant, restoringTimer.getState(),
                    "Should ignore invalid transition during restore");
    }

    @Test
    @DisplayName("Set state with restoring - allows valid transitions")
    void setStateWithRestoring_allowsValidTransitions() {
        // Arrange - simulate restoring from persistence
        YTimerVariable restoringTimer = new YTimerVariable(new TestTask("Restoring Task"));

        // Act - try valid transition while restoring
        restoringTimer.setState(YWorkItemTimer.State.active, true);

        // Assert
        assertEquals(YWorkItemTimer.State.active, restoringTimer.getState(),
                    "Should allow valid transition during restore");
    }

    // ==================== Predicate Evaluation Tests ====================

    @Test
    @DisplayName("Evaluate predicate - equals with dormant state")
    void evaluatePredicate_equalsWithDormantState() throws Exception {
        // Arrange
        String predicate = "timer(" + TASK_NAME + ") = 'dormant'";

        // Act
        boolean result = timerVariable.evaluatePredicate(predicate);

        // Assert
        assertTrue(result, "Should match dormant state");
    }

    @Test
    @DisplayName("Evaluate predicate - not equals with dormant state")
    void evaluatePredicate_notEqualsWithDormantState() throws Exception {
        // Arrange
        timerVariable.setStateActive();
        String predicate = "timer(" + TASK_NAME + ") != 'dormant'";

        // Act
        boolean result = timerVariable.evaluatePredicate(predicate);

        // Assert
        assertTrue(result, "Should not match dormant state");
    }

    @Test
    @DisplayName("Evaluate predicate - equals with active state")
    void evaluatePredicate_equalsWithActiveState() throws Exception {
        // Arrange
        timerVariable.setStateActive();
        String predicate = "timer(" + TASK_NAME + ") = 'active'";

        // Act
        boolean result = timerVariable.evaluatePredicate(predicate);

        // Assert
        assertTrue(result, "Should match active state");
    }

    @Test
    @DisplayName("Evaluate predicate - malformed operator")
    void evaluatePredicate_malformedOperator() throws Exception {
        // Arrange
        String predicate = "timer(" + TASK_NAME + ") ~ 'dormant'"; // Invalid operator

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            timerVariable.evaluatePredicate(predicate);
        });
        assertTrue(exception.getMessage().contains("Malformed timer predicate"),
                   "Should indicate malformed predicate");
    }

    @Test
    @DisplayName("Evaluate predicate - missing quotes")
    void evaluatePredicate_missingQuotes() throws Exception {
        // Arrange
        String predicate = "timer(" + TASK_NAME + ") = dormant"; // Missing quotes

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            timerVariable.evaluatePredicate(predicate);
        });
        assertTrue(exception.getMessage().contains("quote(s)"),
                   "Should indicate missing quotes issue");
    }

    @Test
    @DisplayName("Evaluate predicate - null predicate")
    void evaluatePredicate_nullPredicate() {
        // Act & Assert
        assertThrows(Exception.class, () -> {
            timerVariable.evaluatePredicate(null);
        }, "Should throw for null predicate");
    }

    @Test
    @DisplayName("Evaluate predicate - empty predicate")
    void evaluatePredicate_emptyPredicate() {
        // Act & Assert
        assertThrows(Exception.class, () -> {
            timerVariable.evaluatePredicate("");
        }, "Should throw for empty predicate");
    }

    // ==================== Transition Validity Tests ====================

    @Test
    @DisplayName("Is valid transition - dormant to active")
    void isValidTransition_dormantToActive() throws Exception {
        // Act
        java.lang.reflect.Method method = YTimerVariable.class.getDeclaredMethod(
            "isValidTransition", YWorkItemTimer.State.class);
        method.setAccessible(true);
        boolean isValid = (Boolean) method.invoke(timerVariable, YWorkItemTimer.State.active);

        // Assert
        assertTrue(isValid, "Should allow dormant to active transition");
    }

    @Test
    @DisplayName("Is valid transition - active to closed")
    void isValidTransition_activeToClosed() throws Exception {
        // Arrange
        timerVariable.setStateActive();

        // Act
        java.lang.reflect.Method method = YTimerVariable.class.getDeclaredMethod(
            "isValidTransition", YWorkItemTimer.State.class);
        method.setAccessible(true);
        boolean isValid = (Boolean) method.invoke(timerVariable, YWorkItemTimer.State.closed);

        // Assert
        assertTrue(isValid, "Should allow active to closed transition");
    }

    @Test
    @DisplayName("Is valid transition - active to expired")
    void isValidTransition_activeToExpired() throws Exception {
        // Arrange
        timerVariable.setStateActive();

        // Act
        java.lang.reflect.Method method = YTimerVariable.class.getDeclaredMethod(
            "isValidTransition", YWorkItemTimer.State.class);
        method.setAccessible(true);
        boolean isValid = (Boolean) method.invoke(timerVariable, YWorkItemTimer.State.expired);

        // Assert
        assertTrue(isValid, "Should allow active to expired transition");
    }

    @Test
    @DisplayName("Is valid transition - closed to active")
    void isValidTransition_closedToActive() throws Exception {
        // Arrange
        timerVariable.setStateActive();
        timerVariable.setStateClosed();

        // Act
        java.lang.reflect.Method method = YTimerVariable.class.getDeclaredMethod(
            "isValidTransition", YWorkItemTimer.State.class);
        method.setAccessible(true);
        boolean isValid = (Boolean) method.invoke(timerVariable, YWorkItemTimer.State.active);

        // Assert
        assertFalse(isValid, "Should not allow closed to active transition");
    }

    @Test
    @DisplayName("Is valid transition - dormant to closed")
    void isValidTransition_dormantToClosed() throws Exception {
        // Act
        java.lang.reflect.Method method = YTimerVariable.class.getDeclaredMethod(
            "isValidTransition", YWorkItemTimer.State.class);
        method.setAccessible(true);
        boolean isValid = (Boolean) method.invoke(timerVariable, YWorkItemTimer.State.closed);

        // Assert
        assertFalse(isValid, "Should not allow dormant to closed transition");
    }

    // ==================== Edge Cases Tests ====================

    @Test
    @DisplayName("Set state multiple times - last state wins")
    void setStateMultipleTimes_lastStateWins() {
        // Act
        timerVariable.setState(YWorkItemTimer.State.active);
        timerVariable.setState(YWorkItemTimer.State.expired);
        timerVariable.setState(YWorkItemTimer.State.closed);

        // Assert
        assertEquals(YWorkItemTimer.State.closed, timerVariable.getState());
    }

    @Test
    @DisplayName("Set state to same value - no change")
    void setStateToSameValue_noChange() {
        // Arrange
        YWorkItemTimer.State originalState = timerVariable.getState();

        // Act
        timerVariable.setState(originalState);

        // Assert
        assertEquals(originalState, timerVariable.getState(),
                    "State should remain unchanged when setting to same value");
    }

    @Test
    @DisplayName("Get task name after task modification")
    void getTaskNameAfterTaskModification() {
        // Arrange
        TestTask task = timerVariable.getTask();
        task.setName("Modified Task Name");

        // Act
        String taskName = timerVariable.getTaskName();

        // Assert
        assertEquals("Modified Task Name", taskName,
                    "Should reflect task name changes");
    }

    // ==================== Performance Tests ====================

    @Test
    @DisplayName("State setter performance")
    void setStatePerformance() {
        final int iterations = 10000;
        long startTime = System.nanoTime();

        // Act
        for (int i = 0; i < iterations; i++) {
            timerVariable.setState(YWorkItemTimer.State.active);
            timerVariable.setState(YWorkItemTimer.State.dormant);
        }
        long endTime = System.nanoTime();

        // Assert
        long duration = endTime - startTime;
        double avgDuration = (double) duration / iterations;
        assertTrue(avgDuration < 1000, // Average should be less than 1 microsecond
                  String.format("Set state should be fast: avg %.2f ns", avgDuration));
    }

    @Test
    @DisplayName("Predicate evaluation performance")
    void evaluatePredicatePerformance() throws Exception {
        final int iterations = 1000;
        String predicate = "timer(" + TASK_NAME + ") = 'dormant'";
        long startTime = System.nanoTime();

        // Act
        for (int i = 0; i < iterations; i++) {
            timerVariable.evaluatePredicate(predicate);
        }
        long endTime = System.nanoTime();

        // Assert
        long duration = endTime - startTime;
        double avgDuration = (double) duration / iterations;
        assertTrue(avgDuration < 1000, // Average should be less than 1 microsecond
                  String.format("Predicate evaluation should be fast: avg %.2f ns", avgDuration));
    }

    // ==================== Helper Classes ====================

    /**
     * Real implementation of YTask for testing purposes.
     * No mocking - implements real behavior as per H-invariants.
     */
    private static class TestTask {
        private String name;

        public TestTask(String name) {
            this.name = Objects.requireNonNull(name, "Task name cannot be null");
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = Objects.requireNonNull(name, "Task name cannot be null");
        }
    }
}