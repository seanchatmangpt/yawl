/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.engine.time;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.elements.YTask;

/**
 * Comprehensive tests for YWorkItemTimer class.
 *
 * <p>Chicago TDD: Tests use real YWorkItemTimer instances, real YWorkItem objects,
 * and real workflow context. No mocks for domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YWorkItemTimer Tests")
@Tag("unit")
class YWorkItemTimerTest {

    private YWorkItemTimer workItemTimer;
    private YWorkItem workItem;
    private static final String TIMER_NAME = "testTimer";
    private static final String CASE_ID = "test-case-123";
    private static final String TASK_ID = "task-456";

    @BeforeEach
    void setUp() throws Exception {
        // Create a mock work item for testing
        YTask task = new YTask();
        task.setID(TASK_ID);
        task.setName("Mock Task");

        workItem = new YWorkItem();
        workItem.setCaseID(CASE_ID);
        workItem.setTask(task);
        workItem.setID(new YWorkItemID(CASE_ID, TASK_ID));

        // Create a work item timer
        workItemTimer = new YWorkItemTimer(workItem, TIMER_NAME, 10000); // 10 seconds
    }

    // ==================== Basic Properties Tests ====================

    @Test
    @DisplayName("Get work item")
    void getWorkItem() {
        // Assert
        assertEquals(workItem, workItemTimer.getWorkItem());
    }

    @Test
    @DisplayName("Get case ID")
    void getCaseID() {
        // Assert
        assertEquals(CASE_ID, workItemTimer.getCaseID());
    }

    @Test
    @DisplayName("Get task ID")
    void getTaskID() {
        // Assert
        assertEquals(TASK_ID, workItemTimer.getTaskID());
    }

    @Test
    @DisplayName("Get timer name")
    void getTimerName() {
        // Assert
        assertEquals(TIMER_NAME, workItemTimer.getTimerName());
    }

    @Test
    @DisplayName("Get start time")
    void getStartTime() {
        // Assert
        assertNotNull(workItemTimer.getStartTime());
        assertTrue(workItemTimer.getStartTime().isBefore(Instant.now()));
    }

    @Test
    @DisplayName("Get expiry time")
    void getExpiryTime() {
        // Assert
        assertNotNull(workItemTimer.getExpiryTime());
        assertEquals(workItemTimer.getStartTime().plusMillis(10000),
                   workItemTimer.getExpiryTime());
    }

    @Test
    @DisplayName("Get duration")
    void getDuration() {
        // Assert
        assertEquals(Duration.ofMillis(10000), workItemTimer.getDuration());
    }

    // ==================== State Tests ====================

    @Test
    @DisplayName("Get initial state - Created")
    void getInitialState() {
        // Assert
        assertEquals(YWorkItemTimer.State.Created, workItemTimer.getState());
    }

    @Test
    @DisplayName("Set and get state - Running")
    void setState_running() {
        // Act
        workItemTimer.setState(YWorkItemTimer.State.Running);

        // Assert
        assertEquals(YWorkItemTimer.State.Running, workItemTimer.getState());
    }

    @Test
    @DisplayName("Set and get state - Expired")
    void setState_expired() {
        // Act
        workItemTimer.setState(YWorkItemTimer.State.Expired);

        // Assert
        assertEquals(YWorkItemTimer.State.Expired, workItemTimer.getState());
    }

    @Test
    @DisplayName("Set and get state - Cancelled")
    void setState_cancelled() {
        // Act
        workItemTimer.setState(YWorkItemTimer.State.Cancelled);

        // Assert
        assertEquals(YWorkItemTimer.State.Cancelled, workItemTimer.getState());
    }

    @Test
    @DisplayName("Set and get state - Completed")
    void setState_completed() {
        // Act
        workItemTimer.setState(YWorkItemTimer.State.Completed);

        // Assert
        assertEquals(YWorkItemTimer.State.Completed, workItemTimer.getState());
    }

    @Test
    @DisplayName("Set and get state - null")
    void setState_null() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            workItemTimer.setState(null);
        });
    }

    // ==================== Status Tests ====================

    @Test
    @DisplayName("Is active - initially false")
    void isActiveInitially() {
        // Assert
        assertFalse(workItemTimer.isActive());
    }

    @Test
    @DisplayName("Is running - initially false")
    void isRunningInitially() {
        // Assert
        assertFalse(workItemTimer.isRunning());
    }

    @Test
    @DisplayName("Is expired - initially false")
    void isExpiredInitially() {
        // Assert
        assertFalse(workItemTimer.isExpired());
    }

    @Test
    @DisplayName("Is cancelled - initially false")
    void isCancelledInitially() {
        // Assert
        assertFalse(workItemTimer.isCancelled());
    }

    @Test
    @DisplayName("Is completed - initially false")
    void isCompletedInitially() {
        // Assert
        assertFalse(workItemTimer.isCompleted());
    }

    @Test
    @DisplayName("Is active when running")
    void isActive_whenRunning() {
        // Arrange
        workItemTimer.setState(YWorkItemTimer.State.Running);

        // Assert
        assertTrue(workItemTimer.isActive());
        assertTrue(workItemTimer.isRunning());
    }

    @Test
    @DisplayName("Is active when expired")
    void isActive_whenExpired() {
        // Arrange
        workItemTimer.setState(YWorkItemTimer.State.Expired);

        // Assert
        assertTrue(workItemTimer.isActive());
        assertTrue(workItemTimer.isExpired());
    }

    @Test
    @DisplayName("Is not active when cancelled")
    void isActive_whenCancelled() {
        // Arrange
        workItemTimer.setState(YWorkItemTimer.State.Cancelled);

        // Assert
        assertFalse(workItemTimer.isActive());
        assertTrue(workItemTimer.isCancelled());
    }

    @Test
    @DisplayName("Is not active when completed")
    void isActive_whenCompleted() {
        // Arrange
        workItemTimer.setState(YWorkItemTimer.State.Completed);

        // Assert
        assertFalse(workItemTimer.isActive());
        assertTrue(workItemTimer.isCompleted());
    }

    // ==================== State Transition Tests ====================

    @Test
    @DisplayName("Transition from Created to Running")
    void transitionToRunning() {
        // Act
        workItemTimer.start();

        // Assert
        assertEquals(YWorkItemTimer.State.Running, workItemTimer.getState());
        assertTrue(workItemTimer.isRunning());
    }

    @Test
    @DisplayName("Transition from Running to Expired")
    void transitionToExpired() {
        // Arrange
        workItemTimer.start();

        // Act
        workItemTimer.expire();

        // Assert
        assertEquals(YWorkItemTimer.State.Expired, workItemTimer.getState());
        assertTrue(workItemTimer.isExpired());
    }

    @Test
    @DisplayName("Transition from Running to Cancelled")
    void transitionToCancelled() {
        // Arrange
        workItemTimer.start();

        // Act
        workItemTimer.cancel();

        // Assert
        assertEquals(YWorkItemTimer.State.Cancelled, workItemTimer.getState());
        assertTrue(workItemTimer.isCancelled());
    }

    @Test
    @DisplayName("Transition from Running to Completed")
    void transitionToCompleted() {
        // Arrange
        workItemTimer.start();

        // Act
        workItemTimer.complete();

        // Assert
        assertEquals(YWorkItemTimer.State.Completed, workItemTimer.getState());
        assertTrue(workItemTimer.isCompleted());
    }

    @Test
    @DisplayName("Transition from Expired to Cancelled")
    void transitionFromExpiredToCancelled() {
        // Arrange
        workItemTimer.start();
        workItemTimer.expire();

        // Act
        workItemTimer.cancel();

        // Assert
        // Once expired, cannot be cancelled
        assertEquals(YWorkItemTimer.State.Expired, workItemTimer.getState());
        assertTrue(workItemTimer.isExpired());
    }

    @Test
    @DisplayName("Transition from Cancelled to Completed")
    void transitionFromCancelledToCompleted() {
        // Arrange
        workItemTimer.start();
        workItemTimer.cancel();

        // Act
        workItemTimer.complete();

        // Assert
        // Once cancelled, cannot be completed
        assertEquals(YWorkItemTimer.State.Cancelled, workItemTimer.getState());
        assertTrue(workItemTimer.isCancelled());
    }

    // ==================== Timer Control Tests ====================

    @Test
    @DisplayName("Start timer")
    void startTimer() {
        // Act
        workItemTimer.start();

        // Assert
        assertEquals(YWorkItemTimer.State.Running, workItemTimer.getState());
        assertTrue(workItemTimer.isRunning());
        assertNotNull(workItemTimer.getStartTime());
    }

    @Test
    @DisplayName("Start timer - already started")
    void startTimer_alreadyStarted() {
        // Arrange
        workItemTimer.start();

        // Act
        workItemTimer.start();

        // Assert
        // Should not cause issues
        assertEquals(YWorkItemTimer.State.Running, workItemTimer.getState());
        assertTrue(workItemTimer.isRunning());
    }

    @Test
    @DisplayName("Stop timer")
    void stopTimer() {
        // Arrange
        workItemTimer.start();

        // Act
        workItemTimer.stop();

        // Assert
        // Should transition back to Created state
        assertEquals(YWorkItemTimer.State.Created, workItemTimer.getState());
        assertFalse(workItemTimer.isRunning());
        assertFalse(workItemTimer.isExpired());
    }

    @Test
    @DisplayName("Stop timer - not running")
    void stopTimer_notRunning() {
        // Act
        workItemTimer.stop();

        // Assert
        // Should not cause issues
        assertEquals(YWorkItemTimer.State.Created, workItemTimer.getState());
    }

    @Test
    @DisplayName("Pause timer")
    void pauseTimer() {
        // Arrange
        workItemTimer.start();

        // Act
        workItemTimer.pause();

        // Assert
        // Should transition to Paused state
        assertEquals(YWorkItemTimer.State.Paused, workItemTimer.getState());
        assertFalse(workItemTimer.isRunning());
    }

    @Test
    @DisplayName("Resume timer")
    void resumeTimer() {
        // Arrange
        workItemTimer.start();
        workItemTimer.pause();

        // Act
        workItemTimer.resume();

        // Assert
        // Should transition back to Running state
        assertEquals(YWorkItemTimer.State.Running, workItemTimer.getState());
        assertTrue(workItemTimer.isRunning());
    }

    @Test
    @DisplayName("Resume timer - not paused")
    void resumeTimer_notPaused() {
        // Act
        workItemTimer.resume();

        // Assert
        // Should not cause issues
        assertEquals(YWorkItemTimer.State.Created, workItemTimer.getState());
    }

    @Test
    @DisplayName("Cancel timer")
    void cancelTimer() {
        // Arrange
        workItemTimer.start();

        // Act
        workItemTimer.cancel();

        // Assert
        assertEquals(YWorkItemTimer.State.Cancelled, workItemTimer.getState());
        assertTrue(workItemTimer.isCancelled());
        assertFalse(workItemTimer.isActive());
    }

    @Test
    @DisplayName("Complete timer")
    void completeTimer() {
        // Arrange
        workItemTimer.start();

        // Act
        workItemTimer.complete();

        // Assert
        assertEquals(YWorkItemTimer.State.Completed, workItemTimer.getState());
        assertTrue(workItemTimer.isCompleted());
        assertFalse(workItemTimer.isActive());
    }

    // ==================== Expiry Tests ====================

    @Test
    @DisplayName("Check expiry - not expired")
    void checkExpiry_notExpired() {
        // Arrange
        workItemTimer.start();

        // Act & Assert
        assertFalse(workItemTimer.hasExpired());
        assertFalse(workItemTimer.isExpired());
    }

    @Test
    @DisplayName("Check expiry - expired")
    void checkExpiry_expired() throws InterruptedException {
        // Arrange
        // Create a timer with very short duration
        YWorkItemTimer shortTimer = new YWorkItemTimer(workItem, "shortTimer", 50); // 50ms
        shortTimer.start();

        // Wait for expiry
        Thread.sleep(100);

        // Act & Assert
        assertTrue(shortTimer.hasExpired());
        assertTrue(shortTimer.isExpired());
    }

    @Test
    @DisplayName("Check expiry - manually expired")
    void checkExpiry_manuallyExpired() {
        // Arrange
        workItemTimer.start();

        // Act
        workItemTimer.expire();

        // Assert
        assertTrue(workItemTimer.hasExpired());
        assertTrue(workItemTimer.isExpired());
    }

    @Test
    @DisplayName("Force expiry")
    void forceExpiry() {
        // Arrange
        workItemTimer.start();

        // Act
        workItemTimer.forceExpire();

        // Assert
        assertEquals(YWorkItemTimer.State.Expired, workItemTimer.getState());
        assertTrue(workItemTimer.hasExpired());
        assertTrue(workItemTimer.isExpired());
    }

    // ==================== Serialization Tests ====================

    @Test
    @DisplayName("Serialize and deserialize")
    void serializeAndDeserialize() {
        // Arrange
        workItemTimer.start();
        workItemTimer.setState(YWorkItemTimer.State.Expired);

        // Act
        String serialized = workItemTimer.serialize();
        YWorkItemTimer deserialized = YWorkItemTimer.deserialize(serialized);

        // Assert
        assertNotNull(serialized);
        assertNotNull(deserialized);
        assertEquals(workItemTimer.getCaseID(), deserialized.getCaseID());
        assertEquals(workItemTimer.getTaskID(), deserialized.getTaskID());
        assertEquals(workItemTimer.getTimerName(), deserialized.getTimerName());
        assertEquals(workItemTimer.getState(), deserialized.getState());
    }

    @Test
    @DisplayName("Serialize - null values")
    void serialize_nullValues() {
        // Act
        String serialized = workItemTimer.serialize();

        // Assert
        assertNotNull(serialized);
    }

    @Test
    @DisplayName("Deserialize - null string")
    void deserialize_nullString() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemTimer.deserialize(null);
        });
    }

    @Test
    @DisplayName("Deserialize - empty string")
    void deserialize_emptyString() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemTimer.deserialize("");
        });
    }

    @Test
    @DisplayName("Deserialize - invalid format")
    void deserialize_invalidFormat() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemTimer.deserialize("invalid-format");
        });
    }

    // ==================== Equals Tests ====================

    @Test
    @DisplayName("Equals - same object")
    void equals_sameObject() {
        // Assert
        assertEquals(workItemTimer, workItemTimer);
        assertEquals(workItemTimer.hashCode(), workItemTimer.hashCode());
    }

    @Test
    @DisplayName("Equals - equal objects")
    void equals_equalObjects() {
        // Arrange
        YWorkItemTimer other = new YWorkItemTimer(workItem, TIMER_NAME, 10000);

        // Assert
        assertEquals(workItemTimer, other);
        assertEquals(workItemTimer.hashCode(), other.hashCode());
    }

    @Test
    @DisplayName("Equals - different work item")
    void equals_differentWorkItem() throws Exception {
        // Arrange
        YTask otherTask = new YTask();
        otherTask.setID("otherTask");
        YWorkItem otherWorkItem = new YWorkItem();
        otherWorkItem.setCaseID(CASE_ID);
        otherWorkItem.setTask(otherTask);
        otherWorkItem.setID(new YWorkItemID(CASE_ID, "otherTask"));

        YWorkItemTimer other = new YWorkItemTimer(otherWorkItem, TIMER_NAME, 10000);

        // Assert
        assertNotEquals(workItemTimer, other);
    }

    @Test
    @DisplayName("Equals - different timer name")
    void equals_differentTimerName() {
        // Arrange
        YWorkItemTimer other = new YWorkItemTimer(workItem, "differentTimer", 10000);

        // Assert
        assertNotEquals(workItemTimer, other);
    }

    @Test
    @DisplayName("Equals - different duration")
    void equals_differentDuration() {
        // Arrange
        YWorkItemTimer other = new YWorkItemTimer(workItem, TIMER_NAME, 5000);

        // Assert
        assertNotEquals(workItemTimer, other);
    }

    @Test
    @DisplayName("Equals - null")
    void equals_null() {
        // Assert
        assertNotEquals(workItemTimer, null);
    }

    @Test
    @DisplayName("Equals - different type")
    void equals_differentType() {
        // Assert
        assertNotEquals(workItemTimer, "string");
    }

    // ==================== String Representation Tests ====================

    @Test
    @DisplayName("To string")
    void toStringTest() {
        // Act
        String str = workItemTimer.toString();

        // Assert
        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains(TIMER_NAME));
        assertTrue(str.contains(CASE_ID));
        assertTrue(str.contains(TASK_ID));
    }

    @Test
    @DisplayName("To string - expired timer")
    void toStringTest_expiredTimer() {
        // Arrange
        workItemTimer.start();
        workItemTimer.expire();

        // Act
        String str = workItemTimer.toString();

        // Assert
        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains("expired"));
    }

    @Test
    @DisplayName("To string - cancelled timer")
    void toStringTest_cancelledTimer() {
        // Arrange
        workItemTimer.start();
        workItemTimer.cancel();

        // Act
        String str = workItemTimer.toString();

        // Assert
        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains("cancelled"));
    }

    // ==================== Validation Tests ====================

    @Test
    @DisplayName("Validate - valid timer")
    void validate_valid() {
        // Act & Assert
        assertDoesNotThrow(() -> workItemTimer.validate());
    }

    @Test
    @DisplayName("Validate - null work item")
    void validate_nullWorkItem() {
        // Arrange
        YWorkItemTimer invalid = new YWorkItemTimer(null, TIMER_NAME, 1000);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> invalid.validate());
    }

    @Test
    @DisplayName("Validate - null timer name")
    void validate_nullTimerName() {
        // Arrange
        YWorkItemTimer invalid = new YWorkItemTimer(workItem, null, 1000);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> invalid.validate());
    }

    @Test
    @DisplayName("Validate - empty timer name")
    void validate_emptyTimerName() {
        // Arrange
        YWorkItemTimer invalid = new YWorkItemTimer(workItem, "", 1000);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> invalid.validate());
    }

    @Test
    @DisplayName("Validate - negative duration")
    void validate_negativeDuration() {
        // Arrange
        YWorkItemTimer invalid = new YWorkItemTimer(workItem, TIMER_NAME, -1000);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> invalid.validate());
    }

    @Test
    @DisplayName("Validate - null start time")
    void validate_nullStartTime() {
        // Arrange
        YWorkItemTimer invalid = new YWorkItemTimer(workItem, TIMER_NAME, 1000);
        invalid.resetStartTime(null);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> invalid.validate());
    }

    // ==================== Helper Method Tests ====================

    @Test
    @DisplayName("Reset start time")
    void resetStartTime() {
        // Arrange
        Instant originalTime = workItemTimer.getStartTime();
        Instant newTime = Instant.now();

        // Act
        workItemTimer.resetStartTime(newTime);

        // Assert
        assertEquals(newTime, workItemTimer.getStartTime());
        assertNotEquals(originalTime, workItemTimer.getStartTime());
    }

    @Test
    @DisplayName("Reset start time - null")
    void resetStartTime_null() {
        // Act
        workItemTimer.resetStartTime(null);

        // Assert
        // Should handle null gracefully
        assertNotNull(workItemTimer.getStartTime());
    }

    @Test
    @DisplayName("Get time remaining")
    void getTimeRemaining() {
        // Arrange
        workItemTimer.start();

        // Act
        Duration remaining = workItemTimer.getTimeRemaining();

        // Assert
        assertNotNull(remaining);
        assertTrue(remaining.isNegative() || remaining.isZero());
    }

    @Test
    @DisplayName("Get time remaining - not started")
    void getTimeRemaining_notStarted() {
        // Act
        Duration remaining = workItemTimer.getTimeRemaining();

        // Assert
        // Should return full duration when not started
        assertEquals(Duration.ofMillis(10000), remaining);
    }

    @Test
    @DisplayName("Get time remaining - expired")
    void getTimeRemaining_expired() {
        // Arrange
        workItemTimer.start();
        workItemTimer.expire();

        // Act
        Duration remaining = workItemTimer.getTimeRemaining();

        // Assert
        // Should return zero when expired
        assertEquals(Duration.ZERO, remaining);
    }
}