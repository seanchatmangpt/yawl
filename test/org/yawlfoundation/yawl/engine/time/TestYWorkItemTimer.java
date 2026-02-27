/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.time;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YWorkItemTimer Integration Tests
 *
 * Tests timer creation, scheduling, expiry handling, and cancellation
 * using Chicago TDD methodology with real YTimer instances.
 *
 * Coverage:
 * - Timer creation with millisecond duration
 * - Timer creation with Instant expiry time
 * - Timer creation with Duration
 * - Timer creation with TimeUnit intervals
 * - Timer expiry callback invocation
 * - Timer cancellation
 * - YTimedObject interface implementation
 * - Equals and hashCode contract
 */
@Tag("unit")
@DisplayName("YWorkItemTimer Tests")
@Execution(ExecutionMode.SAME_THREAD)  // Uses YTimer singleton
class TestYWorkItemTimer {

    private YTimer timerService;

    /**
     * Test implementation of YTimedObject that tracks expiry and cancellation.
     * This is a test fixture, not a mock - it records real state changes.
     */
    private static class TestTimedObject implements YTimedObject {
        private final String ownerId;
        private final AtomicBoolean expired = new AtomicBoolean(false);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicReference<Thread> expiryThread = new AtomicReference<>();
        private final CountDownLatch expiryLatch;
        private final CountDownLatch cancelLatch;

        TestTimedObject(String ownerId) {
            this(ownerId, new CountDownLatch(1), new CountDownLatch(1));
        }

        TestTimedObject(String ownerId, CountDownLatch expiryLatch, CountDownLatch cancelLatch) {
            this.ownerId = ownerId;
            this.expiryLatch = expiryLatch;
            this.cancelLatch = cancelLatch;
        }

        @Override
        public void handleTimerExpiry() {
            expired.set(true);
            expiryThread.set(Thread.currentThread());
            expiryLatch.countDown();
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            cancelLatch.countDown();
        }

        @Override
        public String getOwnerID() {
            return ownerId;
        }

        boolean isExpired() {
            return expired.get();
        }

        boolean isCancelled() {
            return cancelled.get();
        }

        Thread getExpiryThread() {
            return expiryThread.get();
        }
    }

    @BeforeEach
    void setUp() {
        timerService = YTimer.getInstance();
        assertNotNull(timerService, "YTimer instance should be available");
        // Clear any existing timers
        timerService.cancelAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up all timers after each test
        if (timerService != null) {
            timerService.cancelAll();
        }
    }

    @Test
    @DisplayName("Timer creation with millisecond duration")
    void testTimerCreationWithMilliseconds() {
        String workItemId = "test-workitem-001";
        long durationMs = 5000;

        YWorkItemTimer timer = new YWorkItemTimer(workItemId, durationMs, false);

        assertNotNull(timer, "Timer should be created");
        assertEquals(workItemId, timer.getOwnerID(), "Owner ID should match");
        assertTrue(timer.getEndTime() > System.currentTimeMillis(),
                "End time should be in the future");
        assertTrue(timer.getEndTime() <= System.currentTimeMillis() + durationMs + 100,
                "End time should be approximately current time + duration");
    }

    @Test
    @DisplayName("Timer creation with Instant expiry time")
    void testTimerCreationWithInstantExpiry() {
        String workItemId = "test-workitem-002";
        Instant expiryTime = Instant.now().plusSeconds(60);

        YWorkItemTimer timer = new YWorkItemTimer(workItemId, expiryTime, false);

        assertNotNull(timer, "Timer should be created");
        assertEquals(workItemId, timer.getOwnerID(), "Owner ID should match");
        assertEquals(expiryTime.toEpochMilli(), timer.getEndTime(),
                "End time should match expiry instant");
    }

    @Test
    @DisplayName("Timer creation with Duration")
    void testTimerCreationWithDuration() {
        String workItemId = "test-workitem-003";
        javax.xml.datatype.DatatypeFactory factory;
        try {
            factory = javax.xml.datatype.DatatypeFactory.newInstance();
        } catch (Exception e) {
            fail("Could not create DatatypeFactory: " + e.getMessage());
            return;
        }
        javax.xml.datatype.Duration duration = factory.newDuration("PT30S");

        YWorkItemTimer timer = new YWorkItemTimer(workItemId, duration, false);

        assertNotNull(timer, "Timer should be created");
        assertEquals(workItemId, timer.getOwnerID(), "Owner ID should match");
        long expectedEndTime = System.currentTimeMillis() + duration.getTimeInMillis(new java.util.Date());
        // Allow 500ms tolerance for test execution time
        assertTrue(Math.abs(timer.getEndTime() - expectedEndTime) < 500,
                "End time should be approximately current time + duration");
    }

    @Test
    @DisplayName("Timer creation with TimeUnit interval")
    void testTimerCreationWithTimeUnitInterval() {
        String workItemId = "test-workitem-004";
        long units = 2;
        YTimer.TimeUnit interval = YTimer.TimeUnit.MIN;

        YWorkItemTimer timer = new YWorkItemTimer(workItemId, units, interval, false);

        assertNotNull(timer, "Timer should be created");
        assertEquals(workItemId, timer.getOwnerID(), "Owner ID should match");
        long expectedEndTime = System.currentTimeMillis() + (2 * 60 * 1000);
        // Allow 500ms tolerance for test execution time
        assertTrue(Math.abs(timer.getEndTime() - expectedEndTime) < 500,
                "End time should be approximately current time + 2 minutes");
    }

    @Test
    @DisplayName("Timer fires after duration and invokes handleTimerExpiry")
    void testTimerFiresAfterDuration() throws InterruptedException {
        String workItemId = "test-workitem-fires-001";
        long durationMs = 200;
        CountDownLatch expiryLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);

        // Create a test timed object that tracks real state
        TestTimedObject testTimedObject = new TestTimedObject(workItemId, expiryLatch, cancelLatch);

        // Schedule directly with YTimer
        timerService.schedule(testTimedObject, durationMs);

        // Verify timer is active
        assertTrue(timerService.hasActiveTimer(workItemId),
                "Timer should be active before expiry");

        // Wait for timer to fire (with timeout)
        boolean fired = expiryLatch.await(1, TimeUnit.SECONDS);

        assertTrue(fired, "Timer should fire within timeout period");
        assertTrue(testTimedObject.isExpired(), "handleTimerExpiry should have been called");
        assertEquals(workItemId, testTimedObject.getOwnerID(), "Owner ID should match at expiry");
        assertNotNull(testTimedObject.getExpiryThread(), "Expiry should occur on a thread");

        // Verify timer is no longer active after expiry
        assertFalse(timerService.hasActiveTimer(workItemId),
                "Timer should not be active after expiry");
    }

    @Test
    @DisplayName("Timer cancellation prevents expiry and invokes cancel callback")
    void testTimerCancellationPreventsExpiry() throws InterruptedException {
        String workItemId = "test-workitem-cancel-001";
        long durationMs = 500;
        CountDownLatch expiryLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);

        TestTimedObject testTimedObject = new TestTimedObject(workItemId, expiryLatch, cancelLatch);

        timerService.schedule(testTimedObject, durationMs);

        // Verify timer is active
        assertTrue(timerService.hasActiveTimer(workItemId),
                "Timer should be active before cancellation");

        // Cancel the timer
        YTimedObject cancelled = timerService.cancelTimerTask(workItemId);

        assertNotNull(cancelled, "Cancel should return the timed object");
        assertEquals(workItemId, cancelled.getOwnerID(), "Cancelled object should have correct owner ID");

        // Wait for cancel callback
        boolean cancelCalled = cancelLatch.await(500, TimeUnit.MILLISECONDS);
        assertTrue(cancelCalled, "Cancel callback should be invoked");
        assertTrue(testTimedObject.isCancelled(), "cancel() should have been called on the timed object");

        // Verify timer is no longer active
        assertFalse(timerService.hasActiveTimer(workItemId),
                "Timer should not be active after cancellation");

        // Wait longer than the original duration
        Thread.sleep(700);

        // Verify expiry was never handled
        assertFalse(testTimedObject.isExpired(), "handleTimerExpiry should not have been called after cancellation");
    }

    @Test
    @DisplayName("Timer equals based on owner ID")
    void testTimerEqualsAndHashCode() {
        String ownerId = "shared-owner-id";

        YWorkItemTimer timer1 = new YWorkItemTimer();
        timer1.setOwnerID(ownerId);
        timer1.setEndTime(System.currentTimeMillis() + 5000);

        YWorkItemTimer timer2 = new YWorkItemTimer();
        timer2.setOwnerID(ownerId);
        timer2.setEndTime(System.currentTimeMillis() + 10000);

        YWorkItemTimer timer3 = new YWorkItemTimer();
        timer3.setOwnerID("different-owner");
        timer3.setEndTime(System.currentTimeMillis() + 5000);

        // Test equals
        assertEquals(timer1, timer2, "Timers with same owner ID should be equal");
        assertNotEquals(timer1, timer3, "Timers with different owner IDs should not be equal");
        assertNotEquals(timer1, null, "Timer should not equal null");
        assertNotEquals(timer1, "not a timer", "Timer should not equal different type");

        // Test hashCode
        assertEquals(timer1.hashCode(), timer2.hashCode(),
                "Equal timers should have equal hashCodes");
    }

    @Test
    @DisplayName("Timer with null owner ID uses object identity")
    void testTimerWithNullOwnerId() {
        YWorkItemTimer timer1 = new YWorkItemTimer();
        timer1.setOwnerID(null);

        YWorkItemTimer timer2 = new YWorkItemTimer();
        timer2.setOwnerID(null);

        // With null owner IDs, equals falls back to object identity
        assertNotEquals(timer1, timer2, "Timers with null owner IDs should use identity comparison");
        assertEquals(timer1, timer1, "Timer should equal itself");
    }

    @Test
    @DisplayName("YTimer hasActiveTimer returns correct status")
    void testYTimerHasActiveTimer() {
        String workItemId = "test-active-timer-001";
        long durationMs = 10000;

        // Initially no timer should be active for this work item
        assertFalse(timerService.hasActiveTimer(workItemId),
                "No timer should be active initially");

        // Schedule a timer
        TestTimedObject testTimedObject = new TestTimedObject(workItemId);
        timerService.schedule(testTimedObject, durationMs);

        // Now timer should be active
        assertTrue(timerService.hasActiveTimer(workItemId),
                "Timer should be active after scheduling");

        // Cancel it
        timerService.cancelTimerTask(workItemId);

        // Timer should no longer be active
        assertFalse(timerService.hasActiveTimer(workItemId),
                "Timer should not be active after cancellation");
    }

    @Test
    @DisplayName("YTimer cancelAll clears all timers")
    void testYTimerCancelAll() {
        String workItemId1 = "test-cancel-all-001";
        String workItemId2 = "test-cancel-all-002";
        long durationMs = 10000;

        TestTimedObject testTimedObject1 = new TestTimedObject(workItemId1);
        TestTimedObject testTimedObject2 = new TestTimedObject(workItemId2);

        timerService.schedule(testTimedObject1, durationMs);
        timerService.schedule(testTimedObject2, durationMs);

        assertTrue(timerService.hasActiveTimer(workItemId1), "Timer 1 should be active");
        assertTrue(timerService.hasActiveTimer(workItemId2), "Timer 2 should be active");

        // Cancel all
        timerService.cancelAll();

        assertFalse(timerService.hasActiveTimer(workItemId1), "Timer 1 should be cancelled");
        assertFalse(timerService.hasActiveTimer(workItemId2), "Timer 2 should be cancelled");
    }

    @Test
    @DisplayName("YTimer cancelTimersForCase cancels case-prefixed timers")
    void testYTimerCancelTimersForCase() {
        String caseId = "case-12345";
        String workItemId1 = caseId + ":task1";
        String workItemId2 = caseId + ".task2";
        String otherWorkItemId = "other-case:task3";
        long durationMs = 10000;

        TestTimedObject caseTimer1 = new TestTimedObject(workItemId1);
        TestTimedObject caseTimer2 = new TestTimedObject(workItemId2);
        TestTimedObject otherTimer = new TestTimedObject(otherWorkItemId);

        timerService.schedule(caseTimer1, durationMs);
        timerService.schedule(caseTimer2, durationMs);
        timerService.schedule(otherTimer, durationMs);

        assertTrue(timerService.hasActiveTimer(workItemId1), "Case timer 1 should be active");
        assertTrue(timerService.hasActiveTimer(workItemId2), "Case timer 2 should be active");
        assertTrue(timerService.hasActiveTimer(otherWorkItemId), "Other timer should be active");

        // Cancel timers for the case
        timerService.cancelTimersForCase(caseId);

        assertFalse(timerService.hasActiveTimer(workItemId1), "Case timer 1 should be cancelled");
        assertFalse(timerService.hasActiveTimer(workItemId2), "Case timer 2 should be cancelled");
        assertTrue(timerService.hasActiveTimer(otherWorkItemId), "Other timer should still be active");

        // Clean up
        timerService.cancelTimerTask(otherWorkItemId);
    }

    @Test
    @DisplayName("Timer state enum values are correct")
    void testTimerStateEnumValues() {
        YWorkItemTimer.State[] states = YWorkItemTimer.State.values();
        assertEquals(4, states.length, "Should have 4 state values");

        assertEquals(YWorkItemTimer.State.dormant, YWorkItemTimer.State.valueOf("dormant"));
        assertEquals(YWorkItemTimer.State.active, YWorkItemTimer.State.valueOf("active"));
        assertEquals(YWorkItemTimer.State.closed, YWorkItemTimer.State.valueOf("closed"));
        assertEquals(YWorkItemTimer.State.expired, YWorkItemTimer.State.valueOf("expired"));
    }

    @Test
    @DisplayName("Timer trigger enum values are correct")
    void testTimerTriggerEnumValues() {
        YWorkItemTimer.Trigger[] triggers = YWorkItemTimer.Trigger.values();
        assertEquals(3, triggers.length, "Should have 3 trigger values");

        assertEquals(YWorkItemTimer.Trigger.OnEnabled, YWorkItemTimer.Trigger.valueOf("OnEnabled"));
        assertEquals(YWorkItemTimer.Trigger.OnExecuting, YWorkItemTimer.Trigger.valueOf("OnExecuting"));
        assertEquals(YWorkItemTimer.Trigger.Never, YWorkItemTimer.Trigger.valueOf("Never"));
    }

    @Test
    @DisplayName("Timer setter methods work correctly")
    void testTimerSetterMethods() {
        YWorkItemTimer timer = new YWorkItemTimer();

        // Test setOwnerID
        String ownerId = "test-owner-setter";
        timer.setOwnerID(ownerId);
        assertEquals(ownerId, timer.getOwnerID(), "Owner ID should be set");

        // Test setEndTime
        long endTime = System.currentTimeMillis() + 60000;
        timer.setEndTime(endTime);
        assertEquals(endTime, timer.getEndTime(), "End time should be set");

        // Test setPersisting
        timer.setPersisting(true);
        // No getter for persisting, but we can verify it doesn't throw
        timer.setPersisting(false);
    }

    @Test
    @DisplayName("YTimer getInstance returns singleton")
    void testYTimerSingleton() {
        YTimer instance1 = YTimer.getInstance();
        YTimer instance2 = YTimer.getInstance();

        assertSame(instance1, instance2, "YTimer should be a singleton");
    }

    @Test
    @DisplayName("YTimer TimeUnit enum values are correct")
    void testYTimerTimeUnitEnumValues() {
        YTimer.TimeUnit[] units = YTimer.TimeUnit.values();
        assertEquals(8, units.length, "Should have 8 TimeUnit values");

        assertEquals(YTimer.TimeUnit.YEAR, YTimer.TimeUnit.valueOf("YEAR"));
        assertEquals(YTimer.TimeUnit.MONTH, YTimer.TimeUnit.valueOf("MONTH"));
        assertEquals(YTimer.TimeUnit.WEEK, YTimer.TimeUnit.valueOf("WEEK"));
        assertEquals(YTimer.TimeUnit.DAY, YTimer.TimeUnit.valueOf("DAY"));
        assertEquals(YTimer.TimeUnit.HOUR, YTimer.TimeUnit.valueOf("HOUR"));
        assertEquals(YTimer.TimeUnit.MIN, YTimer.TimeUnit.valueOf("MIN"));
        assertEquals(YTimer.TimeUnit.SEC, YTimer.TimeUnit.valueOf("SEC"));
        assertEquals(YTimer.TimeUnit.MSEC, YTimer.TimeUnit.valueOf("MSEC"));
    }

    @Test
    @DisplayName("Timer with different time units calculates correct end times")
    void testTimerWithDifferentTimeUnits() {
        String baseWorkItemId = "test-timeunit-";
        long now = System.currentTimeMillis();

        // Test seconds
        YWorkItemTimer timerSec = new YWorkItemTimer(baseWorkItemId + "sec", 30, YTimer.TimeUnit.SEC, false);
        assertTrue(timerSec.getEndTime() >= now + 30000 - 100, "30 seconds should add ~30000ms");

        // Test minutes
        YWorkItemTimer timerMin = new YWorkItemTimer(baseWorkItemId + "min", 5, YTimer.TimeUnit.MIN, false);
        assertTrue(timerMin.getEndTime() >= now + (5 * 60 * 1000) - 100, "5 minutes should add ~300000ms");

        // Test hours
        YWorkItemTimer timerHour = new YWorkItemTimer(baseWorkItemId + "hour", 2, YTimer.TimeUnit.HOUR, false);
        assertTrue(timerHour.getEndTime() >= now + (2 * 60 * 60 * 1000) - 100, "2 hours should add ~7200000ms");

        // Test days
        YWorkItemTimer timerDay = new YWorkItemTimer(baseWorkItemId + "day", 1, YTimer.TimeUnit.DAY, false);
        assertTrue(timerDay.getEndTime() >= now + (24 * 60 * 60 * 1000) - 100, "1 day should add ~86400000ms");
    }

    @Test
    @DisplayName("Multiple concurrent timers can be scheduled")
    void testMultipleConcurrentTimers() throws InterruptedException {
        int timerCount = 10;
        CountDownLatch latch = new CountDownLatch(timerCount);
        AtomicInteger expiryCount = new AtomicInteger(0);

        for (int i = 0; i < timerCount; i++) {
            final int index = i;
            String workItemId = "concurrent-timer-" + index;

            // Create a test timed object that counts expirations
            TestTimedObject testTimedObject = new TestTimedObject(workItemId, latch, new CountDownLatch(1)) {
                @Override
                public void handleTimerExpiry() {
                    super.handleTimerExpiry();
                    expiryCount.incrementAndGet();
                }
            };

            // Schedule with slightly staggered times
            timerService.schedule(testTimedObject, 100 + (index * 10));
        }

        // Wait for all timers to fire
        boolean allFired = latch.await(2, TimeUnit.SECONDS);

        assertTrue(allFired, "All timers should fire within timeout");
        assertEquals(timerCount, expiryCount.get(), "All timers should have expired");
    }

    @Test
    @DisplayName("Timer expiry occurs on timer thread not test thread")
    void testTimerExpiryOccursOnTimerThread() throws InterruptedException {
        String workItemId = "test-thread-timer-001";
        long durationMs = 100;
        CountDownLatch expiryLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);

        TestTimedObject testTimedObject = new TestTimedObject(workItemId, expiryLatch, cancelLatch);

        timerService.schedule(testTimedObject, durationMs);

        boolean fired = expiryLatch.await(1, TimeUnit.SECONDS);
        assertTrue(fired, "Timer should fire within timeout");

        Thread expiryThread = testTimedObject.getExpiryThread();
        assertNotNull(expiryThread, "Expiry thread should be recorded");
        assertNotEquals(Thread.currentThread(), expiryThread,
                "Timer expiry should occur on a different thread than the test thread");
    }

    @Test
    @DisplayName("Timer with past expiry time fires immediately")
    void testTimerWithPastExpiryTime() throws InterruptedException {
        String workItemId = "test-past-expiry-001";
        // Set expiry in the past (1 second ago)
        Instant pastExpiry = Instant.now().minusSeconds(1);

        CountDownLatch expiryLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        TestTimedObject testTimedObject = new TestTimedObject(workItemId, expiryLatch, cancelLatch);

        // Schedule with past time - should fire almost immediately
        timerService.schedule(testTimedObject, java.util.Date.from(pastExpiry));

        // Should fire very quickly since the time is in the past
        boolean fired = expiryLatch.await(500, TimeUnit.MILLISECONDS);
        assertTrue(fired, "Timer with past expiry should fire immediately");
        assertTrue(testTimedObject.isExpired(), "Timer should have expired");
    }
}
