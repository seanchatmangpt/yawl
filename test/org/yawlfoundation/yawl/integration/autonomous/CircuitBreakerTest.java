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
 * Tests for CircuitBreaker with state transitions.
 * Chicago TDD style - testing real circuit breaker behavior.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class CircuitBreakerTest extends TestCase {

    public CircuitBreakerTest(String name) {
        super(name);
    }

    public void testDefaultConstructor() {
        CircuitBreaker breaker = new CircuitBreaker("test");
        assertEquals("test", breaker.getName());
        assertEquals(5, breaker.getFailureThreshold());
        assertEquals(30000, breaker.getOpenDurationMs());
        assertEquals(State.CLOSED, breaker.getState());
    }

    public void testCustomConstructor() {
        CircuitBreaker breaker = new CircuitBreaker("test", 3, 5000);
        assertEquals("test", breaker.getName());
        assertEquals(3, breaker.getFailureThreshold());
        assertEquals(5000, breaker.getOpenDurationMs());
        assertEquals(State.CLOSED, breaker.getState());
    }

    public void testConstructorRejectsNullName() {
        try {
            new CircuitBreaker(null);
            fail("Should reject null name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("name cannot be null"));
        }
    }

    public void testConstructorRejectsEmptyName() {
        try {
            new CircuitBreaker("");
            fail("Should reject empty name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("name cannot be null or empty"));
        }
    }

    public void testConstructorRejectsInvalidThreshold() {
        try {
            new CircuitBreaker("test", 0, 5000);
            fail("Should reject threshold <= 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("failureThreshold must be > 0"));
        }
    }

    public void testConstructorRejectsInvalidDuration() {
        try {
            new CircuitBreaker("test", 3, 0);
            fail("Should reject duration <= 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("openDurationMs must be > 0"));
        }
    }

    public void testSuccessfulOperation() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("test", 3, 1000);

        String result = breaker.execute(() -> "success");

        assertEquals("success", result);
        assertEquals(State.CLOSED, breaker.getState());
        assertEquals(0, breaker.getConsecutiveFailures());
    }

    public void testSingleFailureKeepsClosed() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("test", 3, 1000);

        try {
            breaker.execute(() -> {
                throw new RuntimeException("fail");
            });
            fail("Should propagate exception");
        } catch (RuntimeException e) {
            assertEquals("fail", e.getMessage());
        }

        assertEquals(State.CLOSED, breaker.getState());
        assertEquals(1, breaker.getConsecutiveFailures());
    }

    public void testMultipleFailuresOpenCircuit() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("test", 3, 1000);
        AtomicInteger attempts = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            try {
                breaker.execute(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("fail");
                });
            } catch (RuntimeException e) {
            }
        }

        assertEquals(State.OPEN, breaker.getState());
        assertEquals(3, breaker.getConsecutiveFailures());
        assertEquals(3, attempts.get());
    }

    public void testOpenCircuitFailsFast() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("test", 2, 1000);
        AtomicInteger attempts = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            try {
                breaker.execute(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("fail");
                });
            } catch (RuntimeException e) {
            }
        }

        assertEquals(State.OPEN, breaker.getState());

        try {
            breaker.execute(() -> {
                attempts.incrementAndGet();
                return "success";
            });
            fail("Should fail fast when OPEN");
        } catch (CircuitBreakerOpenException e) {
            assertTrue(e.getMessage().contains("is OPEN"));
        }

        assertEquals(2, attempts.get());
    }

    public void testTransitionToHalfOpen() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("test", 2, 100);

        for (int i = 0; i < 2; i++) {
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (RuntimeException e) {
            }
        }

        assertEquals(State.OPEN, breaker.getState());

        Thread.sleep(150);

        assertEquals(State.HALF_OPEN, breaker.getState());
    }

    public void testHalfOpenSuccessCloses() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("test", 2, 100);

        for (int i = 0; i < 2; i++) {
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (RuntimeException e) {
            }
        }

        Thread.sleep(150);

        String result = breaker.execute(() -> "success");

        assertEquals("success", result);
        assertEquals(State.CLOSED, breaker.getState());
        assertEquals(0, breaker.getConsecutiveFailures());
    }

    public void testHalfOpenFailureReopens() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("test", 2, 100);

        for (int i = 0; i < 2; i++) {
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (RuntimeException e) {
            }
        }

        Thread.sleep(150);
        assertEquals(State.HALF_OPEN, breaker.getState());

        try {
            breaker.execute(() -> {
                throw new RuntimeException("fail again");
            });
        } catch (RuntimeException e) {
        }

        assertEquals(State.OPEN, breaker.getState());
    }

    public void testSuccessResetsFailureCounter() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("test", 3, 1000);

        try {
            breaker.execute(() -> {
                throw new RuntimeException("fail");
            });
        } catch (RuntimeException e) {
        }

        assertEquals(1, breaker.getConsecutiveFailures());

        breaker.execute(() -> "success");

        assertEquals(0, breaker.getConsecutiveFailures());
        assertEquals(State.CLOSED, breaker.getState());
    }

    public void testManualReset() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("test", 2, 1000);

        for (int i = 0; i < 2; i++) {
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (RuntimeException e) {
            }
        }

        assertEquals(State.OPEN, breaker.getState());

        breaker.reset();

        assertEquals(State.CLOSED, breaker.getState());
        assertEquals(0, breaker.getConsecutiveFailures());

        String result = breaker.execute(() -> "success");
        assertEquals("success", result);
    }

    public void testRejectsNullOperation() {
        CircuitBreaker breaker = new CircuitBreaker("test");
        try {
            breaker.execute(null);
            fail("Should reject null operation");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("operation cannot be null"));
        } catch (Exception e) {
            fail("Wrong exception type");
        }
    }

    public void testConcurrentOperations() throws Exception {
        CircuitBreaker breaker = new CircuitBreaker("test", 5, 1000);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    breaker.execute(() -> {
                        if (index % 2 == 0) {
                            return "success";
                        } else {
                            throw new RuntimeException("fail");
                        }
                    });
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(10, successes.get() + failures.get());
        assertTrue("Some operations succeeded", successes.get() > 0);
        assertTrue("Some operations failed", failures.get() > 0);
    }
}
