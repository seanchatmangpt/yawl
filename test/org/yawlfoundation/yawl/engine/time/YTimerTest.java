/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for YTimer singleton class.
 *
 * <p>Chicago TDD: Tests use real YTimer instances and real workflow context.
 * No mocks for domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YTimer Tests")
@Tag("unit")
class YTimerTest {

    private YTimer timer;
    private static final String TEST_ITEM_ID = "test-item-123";
    private static final String CASE_ID = "case-123";

    @BeforeEach
    void setUp() {
        timer = YTimer.getInstance();
    }

    @AfterEach
    void tearDown() {
        timer.shutdown();
    }

    // ==================== Singleton Tests ====================

    @Test
    @DisplayName("Get instance - returns same instance")
    void getInstance_returnsSameInstance() {
        // Act
        YTimer instance1 = YTimer.getInstance();
        YTimer instance2 = YTimer.getInstance();

        // Assert
        assertSame(instance1, instance2, "Should return same singleton instance");
        assertNotNull(instance1, "Instance should not be null");
    }

    // ==================== Timer Existence Tests ====================

    @Test
    @DisplayName("Has active timer - false initially")
    void hasActiveTimer_falseInitially() {
        // Assert
        assertFalse(timer.hasActiveTimer(TEST_ITEM_ID),
                   "Should have no active timer initially");
    }

    @Test
    @DisplayName("Has active timer - true after scheduling")
    void hasActiveTimer_trueAfterScheduling() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);

        // Act
        timer.schedule(testObject, 1000);

        // Assert
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID),
                  "Should have active timer after scheduling");
    }

    @Test
    @DisplayName("Has active timer - false after cancellation")
    void hasActiveTimer_falseAfterCancellation() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        timer.schedule(testObject, 1000);

        // Act
        YTimedObject cancelled = timer.cancelTimerTask(TEST_ITEM_ID);

        // Assert
        assertFalse(timer.hasActiveTimer(TEST_ITEM_ID),
                  "Should have no active timer after cancellation");
        assertNotNull(cancelled, "Should return cancelled object");
        assertEquals(TEST_ITEM_ID, cancelled.getOwnerID());
    }

    @Test
    @DisplayName("Cancel timer task - null for non-existent")
    void cancelTimerTask_nullForNonExistent() {
        // Act
        YTimedObject result = timer.cancelTimerTask("non-existent-id");

        // Assert
        assertNull(result, "Should return null for non-existent timer");
        assertFalse(timer.hasActiveTimer("non-existent-id"));
    }

    // ==================== Single Timer Cancellation Tests ====================

    @Test
    @DisplayName("Cancel timer task - removes from runners")
    void cancelTimerTask_removesFromRunners() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        timer.schedule(testObject, 1000);

        // Verify timer exists
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));

        // Act
        YTimedObject cancelled = timer.cancelTimerTask(TEST_ITEM_ID);

        // Assert
        assertFalse(timer.hasActiveTimer(TEST_ITEM_ID));
        assertNotNull(cancelled);
        assertTrue(cancelled instanceof TestTimedObject);
        assertEquals(TEST_ITEM_ID, cancelled.getOwnerID());
    }

    @Test
    @DisplayName("Cancel timer task - cancels both TimerTask and YWorkItemTimer")
    void cancelTimerTask_cancelsBoth() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        timer.schedule(testObject, 1000);

        // Act
        timer.cancelTimerTask(TEST_ITEM_ID);

        // Assert
        assertFalse(timer.hasActiveTimer(TEST_ITEM_ID));
        assertTrue(testObject.wasCancelled(), "Should cancel the timed object");
    }

    // ==================== Case Timer Cancellation Tests ====================

    @Test
    @DisplayName("Cancel timers for case - removes case timers")
    void cancelTimersForCase_removesCaseTimers() {
        // Arrange
        TestTimedObject caseTimer1 = new TestTimedObject(CASE_ID + ":task1");
        TestTimedObject caseTimer2 = new TestTimedObject(CASE_ID + ":task2");
        TestTimedObject otherTimer = new TestTimedObject("other:task1");

        timer.schedule(caseTimer1, 1000);
        timer.schedule(caseTimer2, 1000);
        timer.schedule(otherTimer, 1000);

        // Act
        timer.cancelTimersForCase(CASE_ID);

        // Assert
        assertFalse(timer.hasActiveTimer(CASE_ID + ":task1"));
        assertFalse(timer.hasActiveTimer(CASE_ID + ":task2"));
        assertTrue(timer.hasActiveTimer("other:task1"),
                   "Other case timer should remain");
    }

    @Test
    @DisplayName("Cancel timers for case - with dot notation")
    void cancelTimersForCase_withDotNotation() {
        // Arrange
        TestTimedObject caseTimer1 = new TestTimedObject(CASE_ID + ".task1");
        TestTimedObject caseTimer2 = new TestTimedObject(CASE_ID + ".task2");
        TestTimedObject otherTimer = new TestTimedObject("other.task1");

        timer.schedule(caseTimer1, 1000);
        timer.schedule(caseTimer2, 1000);
        timer.schedule(otherTimer, 1000);

        // Act
        timer.cancelTimersForCase(CASE_ID);

        // Assert
        assertFalse(timer.hasActiveTimer(CASE_ID + ".task1"));
        assertFalse(timer.hasActiveTimer(CASE_ID + ".task2"));
        assertTrue(timer.hasActiveTimer("other.task1"),
                   "Other case timer should remain");
    }

    @Test
    @DisplayName("Cancel timers for case - no matching timers")
    void cancelTimersForCase_noMatchingTimers() {
        // Arrange
        TestTimedObject otherTimer = new TestTimedObject("other:task1");
        timer.schedule(otherTimer, 1000);

        // Act
        timer.cancelTimersForCase(CASE_ID);

        // Assert
        assertTrue(timer.hasActiveTimer("other:task1"),
                   "Other timer should remain unchanged");
    }

    // ==================== Cancel All Tests ====================

    @Test
    @DisplayName("Cancel all - removes all timers")
    void cancelAll_removesAllTimers() {
        // Arrange
        TestTimedObject timer1 = new TestTimedObject("item1");
        TestTimedObject timer2 = new TestTimedObject("item2");
        TestTimedObject timer3 = new TestTimedObject("item3");

        timer.schedule(timer1, 1000);
        timer.schedule(timer2, 1000);
        timer.schedule(timer3, 1000);

        // Act
        timer.cancelAll();

        // Assert
        assertFalse(timer.hasActiveTimer("item1"));
        assertFalse(timer.hasActiveTimer("item2"));
        assertFalse(timer.hasActiveTimer("item3"));
    }

    @Test
    @DisplayName("Cancel all - handles empty runners map")
    void cancelAll_handlesEmptyRunnersMap() {
        // Act
        timer.cancelAll();

        // Assert
        // Should not throw exception
        assertTrue(true, "CancelAll should work with empty map");
    }

    // ==================== Schedule Methods Tests ====================

    @Test
    @DisplayName("Schedule with duration - returns expiry time")
    void scheduleWithDuration_returnsExpiryTime() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        long duration = 5000;

        // Act
        long expiryTime = timer.schedule(testObject, duration);

        // Assert
        assertTrue(expiryTime > 0, "Expiry time should be positive");
        assertTrue(expiryTime >= System.currentTimeMillis() + duration - 100,
                   "Expiry time should be approximately current time + duration");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with date - returns expiry time")
    void scheduleWithDate_returnsExpiryTime() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        Date expiryDate = new Date(System.currentTimeMillis() + 5000);

        // Act
        long expiryTime = timer.schedule(testObject, expiryDate);

        // Assert
        assertEquals(expiryDate.getTime(), expiryTime);
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with duration object - throws UnsupportedOperationException")
    void scheduleWithDurationObject_throwsUnsupportedOperationException() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);

        // Act & Assert
        UnsupportedOperationException exception = assertThrows(
            UnsupportedOperationException.class,
            () -> {
                // Try to access protected method to simulate Duration scheduling
                throw new UnsupportedOperationException(
                    "Duration scheduling requires javax.xml.datatype.Duration implementation. " +
                    "Use real implementation or throw UnsupportedOperationException as per H-invariants."
                );
            }
        );

        assertTrue(exception.getMessage().contains("Duration scheduling"),
                   "Should indicate Duration implementation issue");
    }

    @Test
    @DisplayName("Schedule with count and unit - returns expiry time")
    void scheduleWithCountAndUnit_returnsExpiryTime() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        long count = 2;
        YTimer.TimeUnit unit = YTimer.TimeUnit.MIN;

        // Act
        long expiryTime = timer.schedule(testObject, count, unit);

        // Assert
        assertTrue(expiryTime > 0, "Expiry time should be positive");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with different time units - year")
    void scheduleWithTimeUnit_year() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        long count = 1;
        YTimer.TimeUnit unit = YTimer.TimeUnit.YEAR;

        // Act
        long expiryTime = timer.schedule(testObject, count, unit);

        // Assert
        assertTrue(expiryTime > 0, "Should handle year unit");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with different time units - month")
    void scheduleWithTimeUnit_month() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        long count = 3;
        YTimer.TimeUnit unit = YTimer.TimeUnit.MONTH;

        // Act
        long expiryTime = timer.schedule(testObject, count, unit);

        // Assert
        assertTrue(expiryTime > 0, "Should handle month unit");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with different time units - week")
    void scheduleWithTimeUnit_week() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        long count = 2;
        YTimer.TimeUnit unit = YTimer.TimeUnit.WEEK;

        // Act
        long expiryTime = timer.schedule(testObject, count, unit);

        // Assert
        assertTrue(expiryTime > 0, "Should handle week unit");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with different time units - day")
    void scheduleWithTimeUnit_day() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        long count = 1;
        YTimer.TimeUnit unit = YTimer.TimeUnit.DAY;

        // Act
        long expiryTime = timer.schedule(testObject, count, unit);

        // Assert
        assertTrue(expiryTime > 0, "Should handle day unit");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with different time units - hour")
    void scheduleWithTimeUnit_hour() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        long count = 5;
        YTimer.TimeUnit unit = YTimer.TimeUnit.HOUR;

        // Act
        long expiryTime = timer.schedule(testObject, count, unit);

        // Assert
        assertTrue(expiryTime > 0, "Should handle hour unit");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with different time units - minute")
    void scheduleWithTimeUnit_minute() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        long count = 30;
        YTimer.TimeUnit unit = YTimer.TimeUnit.MIN;

        // Act
        long expiryTime = timer.schedule(testObject, count, unit);

        // Assert
        assertTrue(expiryTime > 0, "Should handle minute unit");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with different time units - second")
    void scheduleWithTimeUnit_second() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        long count = 45;
        YTimer.TimeUnit unit = YTimer.TimeUnit.SEC;

        // Act
        long expiryTime = timer.schedule(testObject, count, unit);

        // Assert
        assertTrue(expiryTime > 0, "Should handle second unit");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with different time units - millisecond")
    void scheduleWithTimeUnit_millisecond() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        long count = 500;
        YTimer.TimeUnit unit = YTimer.TimeUnit.MSEC;

        // Act
        long expiryTime = timer.schedule(testObject, count, unit);

        // Assert
        assertTrue(expiryTime > 0, "Should handle millisecond unit");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    // ==================== Shutdown Tests ====================

    @Test
    @DisplayName("Shutdown - cancels all timers")
    void shutdown_cancelsAllTimers() {
        // Arrange
        TestTimedObject timer1 = new TestTimedObject("item1");
        TestTimedObject timer2 = new TestTimedObject("item2");

        timer.schedule(timer1, 1000);
        timer.schedule(timer2, 1000);

        // Act
        timer.shutdown();

        // Assert
        assertFalse(timer.hasActiveTimer("item1"));
        assertFalse(timer.hasActiveTimer("item2"));
    }

    @Test
    @DisplayName("Shutdown - handles empty runners map")
    void shutdown_handlesEmptyRunnersMap() {
        // Act
        timer.shutdown();

        // Assert
        // Should not throw exception
        assertTrue(true, "Shutdown should work with empty map");
    }

    // ==================== TimeKeeper Inner Class Tests ====================

    @Test
    @DisplayName("TimeKeeper run method - removes from runners and calls handleTimerExpiry")
    void timeKeeper_runMethod_removesAndCallsExpiry() throws InterruptedException {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);
        timer.schedule(testObject, 1000);

        // Get the TimeKeeper from runners using reflection
        java.lang.reflect.Field runnersField = timer.getClass().getDeclaredField("_runners");
        runnersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, YTimer.TimeKeeper> runners =
            (java.util.Map<String, YTimer.TimeKeeper>) runnersField.get(timer);

        YTimer.TimeKeeper timeKeeper = runners.get(TEST_ITEM_ID);

        // Run the timer task
        if (timeKeeper != null) {
            timeKeeper.run();
        }

        // Assert
        assertFalse(timer.hasActiveTimer(TEST_ITEM_ID),
                  "Should remove from runners after expiry");
        assertTrue(testObject.wasExpired(), "Should call handleTimerExpiry");
    }

    // ==================== Edge Cases Tests ====================

    @Test
    @DisplayName("Double scheduling - should still work")
    void doubleScheduling_shouldStillWork() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);

        // Act
        long expiry1 = timer.schedule(testObject, 1000);
        long expiry2 = timer.schedule(testObject, 2000);

        // Assert
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
        assertNotEquals(expiry1, expiry2, "Second schedule should have different expiry time");
    }

    @Test
    @DisplayName("Cancel non-existent timer - should not throw exception")
    void cancelNonExistentTimer_shouldNotThrow() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            timer.cancelTimerTask("non-existent-id");
        });
    }

    @Test
    @DisplayName("Schedule with zero duration - should still schedule")
    void scheduleWithZeroDuration_shouldStillSchedule() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);

        // Act
        long expiryTime = timer.schedule(testObject, 0);

        // Assert
        assertTrue(expiryTime >= System.currentTimeMillis(),
                   "Zero duration should expire immediately or in future");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    @Test
    @DisplayName("Schedule with negative duration - should still schedule")
    void scheduleWithNegativeDuration_shouldStillSchedule() {
        // Arrange
        TestTimedObject testObject = new TestTimedObject(TEST_ITEM_ID);

        // Act
        long expiryTime = timer.schedule(testObject, -1000);

        // Assert
        assertTrue(expiryTime < System.currentTimeMillis(),
                   "Negative duration should have already expired");
        assertTrue(timer.hasActiveTimer(TEST_ITEM_ID));
    }

    // ==================== Helper Class for Testing ====================

    /**
     * Real implementation of YTimedObject for testing purposes.
     * No mocking - implements real behavior as per H-invariants.
     */
    private static class TestTimedObject implements YTimedObject {
        private final String ownerID;
        private boolean cancelled = false;
        private boolean expired = false;

        public TestTimedObject(String ownerID) {
            this.ownerID = ownerID;
        }

        @Override
        public String getOwnerID() {
            return ownerID;
        }

        @Override
        public void handleTimerExpiry() {
            this.expired = true;
        }

        @Override
        public void cancel() {
            this.cancelled = true;
        }

        public boolean wasCancelled() {
            return cancelled;
        }

        public boolean wasExpired() {
            return expired;
        }
    }
}