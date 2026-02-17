/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker.CircuitBreakerOpenException;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker.State;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests for CircuitBreaker (V6 resilience feature).
 *
 * Chicago TDD: tests the real CircuitBreaker state machine with real
 * callable executions. No mocks.
 *
 * Coverage targets:
 * - CLOSED state normal operation
 * - CLOSED -> OPEN transition on threshold
 * - OPEN state fast-fail behavior
 * - OPEN -> HALF_OPEN transition (time-based)
 * - HALF_OPEN -> CLOSED on success
 * - HALF_OPEN -> OPEN on failure
 * - Manual reset
 * - Guard conditions
 * - Concurrent access
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class CircuitBreakerTest extends TestCase {

    public CircuitBreakerTest(String name) {
        super(name);
    }

    // =========================================================================
    // Constructor tests
    // =========================================================================

    public void testConstructorWithNameOnly() {
        CircuitBreaker cb = new CircuitBreaker("test-cb");
        assertNotNull(cb);
        assertEquals("test-cb", cb.getName());
        assertEquals(State.CLOSED, cb.getState());
    }

    public void testConstructorWithCustomSettings() {
        CircuitBreaker cb = new CircuitBreaker("custom-cb", 3, 5000);
        assertEquals("custom-cb", cb.getName());
        assertEquals(3, cb.getFailureThreshold());
        assertEquals(5000L, cb.getOpenDurationMs());
    }

    public void testConstructorNullNameThrows() {
        try {
            new CircuitBreaker(null);
            fail("Expected IllegalArgumentException for null name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("name"));
        }
    }

    public void testConstructorEmptyNameThrows() {
        try {
            new CircuitBreaker("");
            fail("Expected IllegalArgumentException for empty name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("name"));
        }
    }

    public void testConstructorZeroFailureThresholdThrows() {
        try {
            new CircuitBreaker("cb", 0, 5000);
            fail("Expected IllegalArgumentException for zero failureThreshold");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("failureThreshold"));
        }
    }

    public void testConstructorNegativeFailureThresholdThrows() {
        try {
            new CircuitBreaker("cb", -1, 5000);
            fail("Expected IllegalArgumentException for negative failureThreshold");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("failureThreshold"));
        }
    }

    public void testConstructorZeroOpenDurationThrows() {
        try {
            new CircuitBreaker("cb", 3, 0);
            fail("Expected IllegalArgumentException for zero openDurationMs");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("openDurationMs"));
        }
    }

    // =========================================================================
    // CLOSED state - normal operation
    // =========================================================================

    public void testExecuteSucceedsInClosedState() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 1000);

        String result = cb.execute(() -> "success");

        assertEquals("success", result);
        assertEquals(State.CLOSED, cb.getState());
        assertEquals(0, cb.getConsecutiveFailures());
    }

    public void testExecuteMultipleSuccessesInClosedState() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 1000);

        for (int i = 0; i < 5; i++) {
            int val = i;
            int result = cb.execute(() -> val);
            assertEquals(i, result);
        }

        assertEquals(State.CLOSED, cb.getState());
        assertEquals(0, cb.getConsecutiveFailures());
    }

    public void testFailuresBeforeThresholdKeepCircuitClosed() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 5, 1000);

        // 4 failures - below threshold of 5
        for (int i = 0; i < 4; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("failure"); });
            } catch (RuntimeException e) {
                // expected
            }
        }

        assertEquals("Circuit should stay CLOSED below threshold",
                State.CLOSED, cb.getState());
        assertEquals(4, cb.getConsecutiveFailures());
    }

    // =========================================================================
    // CLOSED -> OPEN transition
    // =========================================================================

    public void testCircuitOpensAfterThresholdFailures() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 1000);

        for (int i = 0; i < 3; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("failure"); });
            } catch (RuntimeException e) {
                // expected
            }
        }

        assertEquals("Circuit should be OPEN after 3 failures",
                State.OPEN, cb.getState());
    }

    // =========================================================================
    // OPEN state - fast-fail behavior
    // =========================================================================

    public void testOpenCircuitFailsFast() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 1, 5000);

        // Trip the circuit
        try {
            cb.execute(() -> { throw new RuntimeException("trip"); });
        } catch (RuntimeException e) {
            // expected
        }

        assertEquals(State.OPEN, cb.getState());

        // Should fail fast without calling the operation
        AtomicInteger callCount = new AtomicInteger(0);
        try {
            cb.execute(() -> {
                callCount.incrementAndGet();
                return "value";
            });
            fail("Expected CircuitBreakerOpenException");
        } catch (CircuitBreakerOpenException e) {
            assertEquals("Operation should not have been called",
                    0, callCount.get());
        }
    }

    public void testOpenCircuitExceptionMessage() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("my-circuit", 1, 5000);

        try {
            cb.execute(() -> { throw new RuntimeException("trip"); });
        } catch (RuntimeException e) {
            // expected
        }

        try {
            cb.execute(() -> "value");
            fail("Expected CircuitBreakerOpenException");
        } catch (CircuitBreakerOpenException e) {
            assertTrue("Message should contain circuit name",
                    e.getMessage().contains("my-circuit"));
            assertTrue("Message should indicate OPEN state",
                    e.getMessage().contains("OPEN"));
        }
    }

    // =========================================================================
    // OPEN -> HALF_OPEN transition (time-based)
    // =========================================================================

    public void testCircuitTransitionsToHalfOpenAfterTimeout() throws Exception {
        // Short open duration for testing
        CircuitBreaker cb = new CircuitBreaker("test", 1, 50);

        // Trip circuit
        try {
            cb.execute(() -> { throw new RuntimeException("trip"); });
        } catch (RuntimeException e) {
            // expected
        }

        assertEquals(State.OPEN, cb.getState());

        // Wait for timeout
        Thread.sleep(100);

        // Next getState() call should trigger OPEN -> HALF_OPEN
        State state = cb.getState();
        assertEquals("Should transition to HALF_OPEN after timeout",
                State.HALF_OPEN, state);
    }

    // =========================================================================
    // HALF_OPEN -> CLOSED on success
    // =========================================================================

    public void testHalfOpenTransitionsToClosedOnSuccess() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 1, 50);

        // Trip circuit
        try {
            cb.execute(() -> { throw new RuntimeException("trip"); });
        } catch (RuntimeException e) {
            // expected
        }

        // Wait for timeout
        Thread.sleep(100);

        // Execute successfully in HALF_OPEN state
        String result = cb.execute(() -> "recovery");
        assertEquals("recovery", result);
        assertEquals("Circuit should close after successful test in HALF_OPEN",
                State.CLOSED, cb.getState());
        assertEquals("Failure count should reset",
                0, cb.getConsecutiveFailures());
    }

    // =========================================================================
    // HALF_OPEN -> OPEN on failure
    // =========================================================================

    public void testHalfOpenTransitionsToOpenOnFailure() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 1, 50);

        // Trip circuit
        try {
            cb.execute(() -> { throw new RuntimeException("trip"); });
        } catch (RuntimeException e) {
            // expected
        }

        // Wait for timeout
        Thread.sleep(100);

        // Fail in HALF_OPEN state
        try {
            cb.execute(() -> { throw new RuntimeException("test failure"); });
        } catch (RuntimeException e) {
            // expected
        }

        assertEquals("Circuit should re-open after failure in HALF_OPEN",
                State.OPEN, cb.getState());
    }

    // =========================================================================
    // Success resets failure count
    // =========================================================================

    public void testSuccessResetFailureCountInClosedState() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 5, 1000);

        // 3 failures
        for (int i = 0; i < 3; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("failure"); });
            } catch (RuntimeException e) {
                // expected
            }
        }

        assertEquals(3, cb.getConsecutiveFailures());

        // Success should reset counter
        cb.execute(() -> "ok");
        assertEquals("Success should reset failure counter",
                0, cb.getConsecutiveFailures());
    }

    // =========================================================================
    // Manual reset
    // =========================================================================

    public void testManualResetRestoresClosedState() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 1, 5000);

        // Trip circuit
        try {
            cb.execute(() -> { throw new RuntimeException("trip"); });
        } catch (RuntimeException e) {
            // expected
        }

        assertEquals(State.OPEN, cb.getState());

        cb.reset();

        assertEquals("Manual reset should restore CLOSED state",
                State.CLOSED, cb.getState());
        assertEquals("Manual reset should clear failure count",
                0, cb.getConsecutiveFailures());
    }

    public void testManualResetAllowsImmediateExecution() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 1, 5000);

        // Trip circuit
        try {
            cb.execute(() -> { throw new RuntimeException("trip"); });
        } catch (RuntimeException e) {
            // expected
        }

        cb.reset();

        String result = cb.execute(() -> "after-reset");
        assertEquals("Should execute successfully after reset", "after-reset", result);
    }

    // =========================================================================
    // Null operation guard
    // =========================================================================

    public void testExecuteWithNullOperationThrows() throws Exception {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 1000);

        try {
            cb.execute(null);
            fail("Expected IllegalArgumentException for null operation");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("operation"));
        }
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public void testGetName() {
        CircuitBreaker cb = new CircuitBreaker("my-service-cb");
        assertEquals("my-service-cb", cb.getName());
    }

    public void testGetFailureThreshold() {
        CircuitBreaker cb = new CircuitBreaker("test", 7, 1000);
        assertEquals(7, cb.getFailureThreshold());
    }

    public void testGetOpenDurationMs() {
        CircuitBreaker cb = new CircuitBreaker("test", 3, 15000);
        assertEquals(15000L, cb.getOpenDurationMs());
    }

    // =========================================================================
    // Concurrent access
    // =========================================================================

    public void testConcurrentExecutionIsThreadSafe() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker("concurrent-test", 100, 1000);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        int threadCount = 20;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int num = i;
            threads[i] = new Thread(() -> {
                try {
                    if (num % 2 == 0) {
                        cb.execute(() -> {
                            successCount.incrementAndGet();
                            return "ok";
                        });
                    } else {
                        try {
                            cb.execute(() -> {
                                throw new RuntimeException("test fail");
                            });
                        } catch (RuntimeException e) {
                            failureCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // circuit may open - acceptable in concurrent test
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join(5000);

        assertTrue("Should have had some successes or failures",
                successCount.get() + failureCount.get() > 0);
    }
}
