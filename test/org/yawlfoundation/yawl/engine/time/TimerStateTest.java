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

package org.yawlfoundation.yawl.engine.time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Java 25 record features in TimerState.
 *
 * <p>This test class validates the TimerState record implementation,
 * focusing on record validation, state transitions, and immutability guarantees.</p>
 *
 * <h2>Test Scope</h2>
 * <ul>
 *   <li>Record validation and immutability</li>
 *   <li>Timer state transitions</li>
 *   <li>Factory methods and builder pattern</li>
 *   <li>Time-related calculations and checks</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
@Tag("java25")
@Execution(ExecutionMode.CONCURRENT)
class TimerStateTest {

    private static final String TEST_OWNER_ID = "workitem-123";
    private static final long TEST_END_TIME = System.currentTimeMillis() + 5000; // 5 seconds from now
    private static final boolean TEST_PERSISTING = true;
    private static final TimerState.State TEST_STATE = TimerState.State.ACTIVE;
    private static final TimerState.TriggerType TEST_TRIGGER_TYPE = TimerState.TriggerType.ON_ENABLED;

    /**
     * Setup test environment before each test.
     */
    @BeforeEach
    void setUp() {
        // Default setup is handled by constants
    }

    @Test
    void testConstructor() {
        String ownerId = "workitem-123";
        long endTime = System.currentTimeMillis() + 5000; // 5 seconds from now
        boolean persisting = true;
        TimerState.State state = TimerState.State.ACTIVE;
        TimerState.TriggerType triggerType = TimerState.TriggerType.ON_ENABLED;

        TimerState timerState = new TimerState(
            ownerId, endTime, persisting, state, triggerType);

        assertEquals(ownerId, timerState.getOwnerId());
        assertEquals(endTime, timerState.getEndTime());
        assertTrue(timerState.isPersisting());
        assertEquals(state, timerState.getState());
        assertEquals(triggerType, timerState.getTriggerType());
    }

    @Test
    void testGetEndTimeInstant() {
        String ownerId = "workitem-123";
        long endTime = 1715472000000L; // May 10, 2024
        TimerState timerState = new TimerState(
            ownerId, endTime, true, TimerState.State.ACTIVE, TimerState.TriggerType.ON_ENABLED);

        Instant expected = Instant.ofEpochMilli(endTime);
        assertEquals(expected, timerState.getEndTimeInstant());
    }

    @Test
    void testNullParametersThrowException() {
        long endTime = System.currentTimeMillis() + 5000;
        TimerState.State state = TimerState.State.ACTIVE;
        TimerState.TriggerType triggerType = TimerState.TriggerType.ON_ENABLED;

        assertThrows(NullPointerException.class, () ->
            new TimerState(null, endTime, true, state, triggerType));
        assertThrows(NullPointerException.class, () ->
            new TimerState("workitem-123", endTime, true, null, triggerType));
        assertThrows(NullPointerException.class, () ->
            new TimerState("workitem-123", endTime, true, state, null));
    }

    @Test
    void testStateMethods() {
        String ownerId = "workitem-123";
        long endTime = System.currentTimeMillis() + 5000;
        TimerState timerState = new TimerState(
            ownerId, endTime, true, TimerState.State.ACTIVE, TimerState.TriggerType.ON_ENABLED);

        // Test isExpired
        assertFalse(timerState.isExpired());

        // Test isActive
        assertTrue(timerState.isActive());

        // Test isDormant
        assertFalse(timerState.isDormant());

        // Test with expired state
        TimerState expiredState = timerState.withState(TimerState.State.EXPIRED);
        assertTrue(expiredState.isExpired());
        assertFalse(expiredState.isActive());
        assertFalse(expiredState.isDormant());
    }

    @Test
    void testWithMethods() {
        String ownerId = "workitem-123";
        long endTime = System.currentTimeMillis() + 5000;
        TimerState timerState = new TimerState(
            ownerId, endTime, true, TimerState.State.ACTIVE, TimerState.TriggerType.ON_ENABLED);

        // Test withEndTime
        long newEndTime = endTime + 10000;
        TimerState withNewEndTime = timerState.withEndTime(newEndTime);
        assertEquals(newEndTime, withNewEndTime.getEndTime());
        assertEquals(ownerId, withNewEndTime.getOwnerId());

        // Test withState
        TimerState withNewState = timerState.withState(TimerState.State.CLOSED);
        assertEquals(TimerState.State.CLOSED, withNewState.getState());
        assertEquals(ownerId, withNewState.getOwnerId());

        // Test withPersisting
        TimerState withoutPersistence = timerState.withPersisting(false);
        assertFalse(withoutPersistence.isPersisting());
        assertEquals(ownerId, withoutPersistence.getOwnerId());
    }

    @Test
    void testFactoryMethods() {
        String ownerId = "workitem-123";
        TimerState.TriggerType triggerType = TimerState.TriggerType.ON_ENABLED;

        // Test dormant factory
        TimerState dormant = TimerState.dormant(ownerId, true, triggerType);
        assertEquals(TimerState.State.DORMANT, dormant.getState());
        assertTrue(dormant.isPersisting());
        assertEquals(triggerType, dormant.getTriggerType());

        // Test active factory
        long endTime = System.currentTimeMillis() + 5000;
        TimerState active = TimerState.active(ownerId, endTime, false, triggerType);
        assertEquals(TimerState.State.ACTIVE, active.getState());
        assertEquals(endTime, active.getEndTime());
        assertFalse(active.isPersisting());
        assertEquals(triggerType, active.getTriggerType());

        // Test expired factory
        TimerState expired = TimerState.expired(ownerId, true);
        assertEquals(TimerState.State.EXPIRED, expired.getState());
        assertTrue(expired.isPersisting());
        assertEquals(TimerState.TriggerType.NEVER, expired.getTriggerType());
    }

    @Test
    void testBuilder() {
        long endTime = System.currentTimeMillis() + 5000;
        Instant endTimeInstant = Instant.ofEpochMilli(endTime);

        TimerState timerState = new TimerState.Builder()
            .ownerId("workitem-123")
            .endTime(endTime)
            .endTime(endTimeInstant) // This should use the instant version
            .persisting(true)
            .state(TimerState.State.ACTIVE)
            .triggerType(TimerState.TriggerType.ON_ENABLED)
            .build();

        assertEquals("workitem-123", timerState.getOwnerId());
        assertEquals(endTime, timerState.getEndTime());
        assertTrue(timerState.isPersisting());
        assertEquals(TimerState.State.ACTIVE, timerState.getState());
        assertEquals(TimerState.TriggerType.ON_ENABLED, timerState.getTriggerType());
    }

    @Test
    void testBuilderThrowsExceptionForMissingRequiredFields() {
        TimerState.Builder builder = new TimerState.Builder();

        // Missing owner ID
        assertThrows(IllegalStateException.class, builder::build);

        builder.ownerId("workitem-123");

        // Missing state
        assertThrows(IllegalStateException.class, builder::build);

        builder.state(TimerState.State.ACTIVE);

        // Missing trigger type
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void testBuilderChain() {
        long endTime = System.currentTimeMillis() + 5000;

        TimerState timerState = new TimerState.Builder()
            .ownerId("workitem-123")
            .endTime(endTime)
            .persisting(true)
            .state(TimerState.State.ACTIVE)
            .triggerType(TimerState.TriggerType.ON_ENABLED)
            .build();

        assertEquals("workitem-123", timerState.getOwnerId());
        assertEquals(endTime, timerState.getEndTime());
        assertTrue(timerState.isPersisting());
        assertEquals(TimerState.State.ACTIVE, timerState.getState());
        assertEquals(TimerState.TriggerType.ON_ENABLED, timerState.getTriggerType());
    }

    @Test
    void testEqualsAndHashCode() {
        long endTime = System.currentTimeMillis() + 5000;

        TimerState timer1 = new TimerState(
            "workitem-123", endTime, true, TimerState.State.ACTIVE, TimerState.TriggerType.ON_ENABLED);
        TimerState timer2 = new TimerState(
            "workitem-123", endTime, true, TimerState.State.ACTIVE, TimerState.TriggerType.ON_ENABLED);
        TimerState timer3 = new TimerState(
            "workitem-456", endTime, true, TimerState.State.ACTIVE, TimerState.TriggerType.ON_ENABLED);
        TimerState timer4 = new TimerState(
            "workitem-123", endTime + 1000, true, TimerState.State.ACTIVE, TimerState.TriggerType.ON_ENABLED);

        assertEquals(timer1, timer2);
        assertNotEquals(timer1, timer3);
        assertNotEquals(timer1, timer4);
        assertEquals(timer1.hashCode(), timer2.hashCode());
        assertNotEquals(timer1.hashCode(), timer3.hashCode());
        assertNotEquals(timer1.hashCode(), timer4.hashCode());
    }

    @Test
    void testToString() {
        long endTime = System.currentTimeMillis() + 5000;
        TimerState timerState = new TimerState(
            "workitem-123", endTime, true, TimerState.State.ACTIVE, TimerState.TriggerType.ON_ENABLED);

        String toString = timerState.toString();
        assertTrue(toString.contains("TimerState"));
        assertTrue(toString.contains("ownerId=workitem-123"));
        assertTrue(toString.contains("endTime=" + endTime));
        assertTrue(toString.contains("persisting=true"));
        assertTrue(toString.contains("state=ACTIVE"));
        assertTrue(toString.contains("triggerType=ON_ENABLED"));
    }

    @Test
    void testEnumValues() {
        // Test State enum values
        TimerState.State[] states = TimerState.State.values();
        assertEquals(4, states.length);
        assertTrue(java.util.Arrays.asList(states).contains(TimerState.State.DORMANT));
        assertTrue(java.util.Arrays.asList(states).contains(TimerState.State.ACTIVE));
        assertTrue(java.util.Arrays.asList(states).contains(TimerState.State.CLOSED));
        assertTrue(java.util.Arrays.asList(states).contains(TimerState.State.EXPIRED));

        // Test TriggerType enum values
        TimerState.TriggerType[] triggerTypes = TimerState.TriggerType.values();
        assertEquals(3, triggerTypes.length);
        assertTrue(java.util.Arrays.asList(triggerTypes).contains(TimerState.TriggerType.ON_ENABLED));
        assertTrue(java.util.Arrays.asList(triggerTypes).contains(TimerState.TriggerType.ON_EXECUTING));
        assertTrue(java.util.Arrays.asList(triggerTypes).contains(TimerState.TriggerType.NEVER));
    }

    @Test
    void testEnumValueOf() {
        assertEquals(TimerState.State.ACTIVE, TimerState.State.valueOf("ACTIVE"));
        assertEquals(TimerState.TriggerType.ON_ENABLED, TimerState.TriggerType.valueOf("ON_ENABLED"));

        assertThrows(IllegalArgumentException.class, () -> TimerState.State.valueOf("INVALID_STATE"));
        assertThrows(IllegalArgumentException.class, () -> TimerState.TriggerType.valueOf("INVALID_TRIGGER"));
    }

    @Test
    void testImmutability() {
        String ownerId = "workitem-123";
        long endTime = System.currentTimeMillis() + 5000;
        TimerState timerState = new TimerState(
            ownerId, endTime, true, TimerState.State.ACTIVE, TimerState.TriggerType.ON_ENABLED);

        // Verify maps and collections are unmodifiable
        assertThrows(UnsupportedOperationException.class, () ->
            timerState.getEndTimeInstant()); // This is just an accessor, not modifying
    }

    @Test
    void testZeroEndTime() {
        String ownerId = "workitem-123";
        TimerState timerState = new TimerState(
            ownerId, 0, false, TimerState.State.DORMANT, TimerState.TriggerType.NEVER);

        assertEquals(0, timerState.getEndTime());
        assertEquals(Instant.EPOCH, timerState.getEndTimeInstant());
    }

    /**
     * Test record immutability and equality.
     */
    @Test
    void testRecordImmutabilityAndEquality() {
        // Create timer state using constructor
        TimerState timer1 = new TimerState(
            TEST_OWNER_ID, TEST_END_TIME, TEST_PERSISTING, TEST_STATE, TEST_TRIGGER_TYPE
        );

        // Create identical timer state
        TimerState timer2 = new TimerState(
            TEST_OWNER_ID, TEST_END_TIME, TEST_PERSISTING, TEST_STATE, TEST_TRIGGER_TYPE
        );

        // Test equality (auto-generated by record)
        assertEquals(timer1, timer2, "Identical records should be equal");
        assertEquals(timer1.hashCode(), timer2.hashCode(), "Equal records should have same hash code");

        // Test getter methods
        assertEquals(TEST_OWNER_ID, timer1.getOwnerId());
        assertEquals(TEST_END_TIME, timer1.getEndTime());
        assertEquals(TEST_PERSISTING, timer1.isPersisting());
        assertEquals(TEST_STATE, timer1.getState());
        assertEquals(TEST_TRIGGER_TYPE, timer1.getTriggerType());

        // Test immutability - verify fields are final
        assertInstanceOf(String.class, timer1.getOwnerId(), "Owner ID should be String");
        assertInstanceOf(Long.class, timer1.getEndTime(), "End time should be Long");
        assertInstanceOf(Boolean.class, timer1.isPersisting(), "Persisting should be Boolean");
        assertInstanceOf(TimerState.State.class, timer1.getState(), "State should be State enum");
        assertInstanceOf(TimerState.TriggerType.class, timer1.getTriggerType(), "Trigger type should be TriggerType enum");
    }

    /**
     * Test withEndTime method and immutability.
     */
    @Test
    void testWithEndTimeMethod() {
        TimerState original = new TimerState(
            TEST_OWNER_ID, TEST_END_TIME, TEST_PERSISTING, TEST_STATE, TEST_TRIGGER_TYPE
        );

        // Create new timer with different end time
        long newEndTime = TEST_END_TIME + 10000;
        TimerState modified = original.withEndTime(newEndTime);

        // Verify original is unchanged
        assertEquals(TEST_END_TIME, original.getEndTime(), "Original should not change");
        assertEquals(TEST_OWNER_ID, original.getOwnerId(), "Original owner ID should be preserved");

        // Verify new timer has updated end time
        assertEquals(newEndTime, modified.getEndTime(), "Modified timer should have new end time");
        assertEquals(TEST_OWNER_ID, modified.getOwnerId(), "Modified timer should preserve owner ID");
    }

    /**
     * Test withPersisting method and immutability.
     */
    @Test
    void testWithPersistingMethod() {
        TimerState original = new TimerState(
            TEST_OWNER_ID, TEST_END_TIME, TEST_PERSISTING, TEST_STATE, TEST_TRIGGER_TYPE
        );

        // Create new timer with different persisting setting
        boolean newPersisting = false;
        TimerState modified = original.withPersisting(newPersisting);

        // Verify original is unchanged
        assertEquals(TEST_PERSISTING, original.isPersisting(), "Original should not change");

        // Verify new timer has updated setting
        assertEquals(newPersisting, modified.isPersisting(), "Modified timer should have new persisting setting");
        assertEquals(TEST_END_TIME, modified.getEndTime(), "Other properties should be preserved");
    }

    /**
     * Test isActive, isExpired, and isDormant methods.
     */
    @Test
    void testTimerStateCheckMethods() {
        // Test ACTIVE timer
        TimerState activeTimer = TimerState.active(TEST_OWNER_ID, TEST_END_TIME, TEST_PERSISTING, TEST_TRIGGER_TYPE);
        assertTrue(activeTimer.isActive(), "ACTIVE timer should be active");
        assertFalse(activeTimer.isExpired(), "ACTIVE timer should not be expired");
        assertFalse(activeTimer.isDormant(), "ACTIVE timer should not be dormant");

        // Test EXPIRED timer
        TimerState expiredTimer = TimerState.expired(TEST_OWNER_ID, TEST_PERSISTING);
        assertFalse(expiredTimer.isActive(), "EXPIRED timer should not be active");
        assertTrue(expiredTimer.isExpired(), "EXPIRED timer should be expired");
        assertFalse(expiredTimer.isDormant(), "EXPIRED timer should not be dormant");

        // Test DORMANT timer
        TimerState dormantTimer = TimerState.dormant(TEST_OWNER_ID, TEST_PERSISTING, TEST_TRIGGER_TYPE);
        assertFalse(dormantTimer.isActive(), "DORMANT timer should not be active");
        assertFalse(dormantTimer.isExpired(), "DORMANT timer should not be expired");
        assertTrue(dormantTimer.isDormant(), "DORMANT timer should be dormant");
    }

    /**
     * Test getEndTimeInstant method.
     */
    @Test
    void testGetEndTimeInstant() {
        long endTimeMillis = TEST_END_TIME;
        TimerState timer = new TimerState(
            TEST_OWNER_ID, endTimeMillis, TEST_PERSISTING, TEST_STATE, TEST_TRIGGER_TYPE
        );

        // Test conversion from millis to Instant
        Instant endTimeInstant = timer.getEndTimeInstant();
        assertEquals(endTimeMillis, endTimeInstant.toEpochMilli(), "Instant should match millis value");
        assertInstanceOf(Instant.class, endTimeInstant, "Should return Instant object");
    }

    /**
     * Test builder pattern for complex construction.
     */
    @Test
    void testBuilderPattern() {
        TimerState timer = new TimerState.Builder()
            .ownerId(TEST_OWNER_ID)
            .endTime(Instant.now().plus(1, ChronoUnit.HOURS))
            .persisting(TEST_PERSISTING)
            .state(TEST_STATE)
            .triggerType(TEST_TRIGGER_TYPE)
            .build();

        assertEquals(TEST_OWNER_ID, timer.getOwnerId());
        assertEquals(TEST_STATE, timer.getState());
        assertEquals(TEST_PERSISTING, timer.isPersisting());
        assertEquals(TEST_TRIGGER_TYPE, timer.getTriggerType());
        assertInstanceOf(Instant.class, timer.getEndTimeInstant(), "Builder should accept Instant");
    }

    /**
     * Test builder validation.
     */
    @Test
    void testBuilderValidation() {
        // Test building without required fields
        TimerState.Builder builder = new TimerState.Builder()
            .persisting(TEST_PERSISTING)
            .state(TEST_STATE)
            .triggerType(TEST_TRIGGER_TYPE);

        assertThrows(IllegalStateException.class, builder::build, "Should throw error for missing owner ID");

        // Test with null state
        TimerState.Builder builder2 = new TimerState.Builder()
            .ownerId(TEST_OWNER_ID)
            .state(null);

        assertThrows(NullPointerException.class, builder2::build, "Should throw error for null state");

        // Test with null trigger type
        TimerState.Builder builder3 = new TimerState.Builder()
            .ownerId(TEST_OWNER_ID)
            .state(TEST_STATE)
            .triggerType(null);

        assertThrows(NullPointerException.class, builder3::build, "Should throw error for null trigger type");
    }

    /**
     * Test toString method.
     */
    @Test
    void testToString() {
        TimerState timer = new TimerState(
            TEST_OWNER_ID, TEST_END_TIME, TEST_PERSISTING, TEST_STATE, TEST_TRIGGER_TYPE
        );

        String toString = timer.toString();
        assertTrue(toString.contains("TimerState"), "toString should contain class name");
        assertTrue(toString.contains(TEST_OWNER_ID), "toString should contain owner ID");
        assertTrue(toString.contains(TEST_STATE.name()), "toString should contain state");
        assertTrue(toString.contains(TEST_TRIGGER_TYPE.name()), "toString should contain trigger type");
    }

    /**
     * Test transition scenarios.
     */
    @Test
    void testStateTransitionScenarios() {
        // Create a dormant timer
        TimerState dormant = TimerState.dormant(TEST_OWNER_ID, TEST_PERSISTING, TimerState.TriggerType.ON_ENABLED);

        // Transition to active
        TimerState active = dormant.withState(TimerState.State.ACTIVE);
        assertTrue(active.isActive());
        assertFalse(active.isDormant());

        // Transition to closed
        TimerState closed = active.withState(TimerState.State.CLOSED);
        assertTrue(closed.isActive()); // CLOSED is still considered active
        assertFalse(closed.isDormant());

        // Transition to expired
        TimerState expired = closed.withState(TimerState.State.EXPIRED);
        assertTrue(expired.isExpired());
        assertFalse(expired.isActive());
    }

    /**
     * Test edge cases with extreme time values.
     */
    @Test
    void testExtremeTimeValues() {
        // Test very far future
        long farFuture = Long.MAX_VALUE;
        TimerState futureTimer = new TimerState(TEST_OWNER_ID, farFuture, TEST_PERSISTING, TEST_STATE, TEST_TRIGGER_TYPE);
        assertEquals(farFuture, futureTimer.getEndTime());

        // Test zero time (epoch)
        long epochTime = 0;
        TimerState epochTimer = new TimerState(TEST_OWNER_ID, epochTime, TEST_PERSISTING, TEST_STATE, TEST_TRIGGER_TYPE);
        assertEquals(epochTime, epochTimer.getEndTime());
        assertEquals(Instant.EPOCH, epochTimer.getEndTimeInstant());
    }

    /**
     * Test thread safety with record immutability.
     */
    @Test
    void testThreadSafety() throws InterruptedException {
        TimerState timer = new TimerState(
            TEST_OWNER_ID, TEST_END_TIME, TEST_PERSISTING, TEST_STATE, TEST_TRIGGER_TYPE
        );

        // Create multiple threads to verify immutability
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                // Each thread reads the timer state
                assertEquals(TEST_OWNER_ID, timer.getOwnerId());
                assertEquals(TEST_STATE, timer.getState());
                // Verifies that the original timer remains unchanged
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify timer state is unchanged
        assertEquals(TEST_OWNER_ID, timer.getOwnerId());
        assertEquals(TEST_END_TIME, timer.getEndTime());
    }
}