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
import org.yawlfoundation.yawl.integration.autonomous.resilience.RetryPolicy;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for RetryPolicy with exponential backoff.
 * Chicago TDD style - testing real retry behavior.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class RetryPolicyTest extends TestCase {

    public RetryPolicyTest(String name) {
        super(name);
    }

    public void testDefaultConstructor() {
        RetryPolicy policy = new RetryPolicy();
        assertEquals(3, policy.getMaxAttempts());
        assertEquals(2000, policy.getInitialBackoffMs());
    }

    public void testCustomConstructor() {
        RetryPolicy policy = new RetryPolicy(5, 1000);
        assertEquals(5, policy.getMaxAttempts());
        assertEquals(1000, policy.getInitialBackoffMs());
    }

    public void testConstructorRejectsInvalidAttempts() {
        try {
            new RetryPolicy(0, 1000);
            fail("Should reject maxAttempts < 1");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("maxAttempts must be >= 1"));
        }
    }

    public void testConstructorRejectsInvalidBackoff() {
        try {
            new RetryPolicy(3, 0);
            fail("Should reject initialBackoffMs <= 0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("initialBackoffMs must be > 0"));
        }
    }

    public void testSuccessOnFirstAttempt() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, 100);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = policy.executeWithRetry(() -> {
            attempts.incrementAndGet();
            return "success";
        });

        assertEquals("success", result);
        assertEquals(1, attempts.get());
    }

    public void testSuccessOnSecondAttempt() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, 100);
        AtomicInteger attempts = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        String result = policy.executeWithRetry(() -> {
            int count = attempts.incrementAndGet();
            if (count == 1) {
                throw new RuntimeException("First attempt fails");
            }
            return "success";
        });

        long duration = System.currentTimeMillis() - startTime;

        assertEquals("success", result);
        assertEquals(2, attempts.get());
        assertTrue("Should have backoff delay", duration >= 100);
    }

    public void testSuccessOnThirdAttempt() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, 50);
        AtomicInteger attempts = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        String result = policy.executeWithRetry(() -> {
            int count = attempts.incrementAndGet();
            if (count <= 2) {
                throw new RuntimeException("Attempt " + count + " fails");
            }
            return "success";
        });

        long duration = System.currentTimeMillis() - startTime;

        assertEquals("success", result);
        assertEquals(3, attempts.get());
        assertTrue("Should have exponential backoff", duration >= 150);
    }

    public void testFailureAfterAllAttempts() {
        RetryPolicy policy = new RetryPolicy(3, 50);
        AtomicInteger attempts = new AtomicInteger(0);

        try {
            policy.executeWithRetry(() -> {
                attempts.incrementAndGet();
                throw new RuntimeException("Always fails");
            });
            fail("Should throw exception after all retries exhausted");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Operation failed after 3 attempts"));
            assertEquals(3, attempts.get());
        }
    }

    public void testCustomAttemptCount() throws Exception {
        RetryPolicy policy = new RetryPolicy(5, 50);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = policy.executeWithRetry(() -> {
            int count = attempts.incrementAndGet();
            if (count == 1) {
                throw new RuntimeException("First fails");
            }
            return "success";
        }, 2);

        assertEquals("success", result);
        assertEquals(2, attempts.get());
    }

    public void testRejectsNullOperation() {
        RetryPolicy policy = new RetryPolicy();
        try {
            policy.executeWithRetry(null);
            fail("Should reject null operation");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("operation cannot be null"));
        } catch (Exception e) {
            fail("Wrong exception type");
        }
    }

    public void testRejectsInvalidAttemptCount() {
        RetryPolicy policy = new RetryPolicy();
        try {
            policy.executeWithRetry(() -> "test", 0);
            fail("Should reject attempts < 1");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("attempts must be >= 1"));
        } catch (Exception e) {
            fail("Wrong exception type");
        }
    }

    public void testUncheckedVersionWrapsException() {
        RetryPolicy policy = new RetryPolicy(2, 50);
        AtomicInteger attempts = new AtomicInteger(0);

        try {
            policy.executeWithRetryUnchecked(() -> {
                attempts.incrementAndGet();
                throw new Exception("Checked exception");
            });
            fail("Should throw RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Retry failed"));
            assertEquals(2, attempts.get());
        }
    }

    public void testUncheckedVersionWithSuccess() {
        RetryPolicy policy = new RetryPolicy(2, 50);

        String result = policy.executeWithRetryUnchecked(() -> "success");

        assertEquals("success", result);
    }

    public void testExponentialBackoffCalculation() throws Exception {
        RetryPolicy policy = new RetryPolicy(4, 100);
        AtomicInteger attempts = new AtomicInteger(0);
        long[] backoffTimes = new long[3];

        long startTime = System.currentTimeMillis();
        long lastAttemptTime = startTime;

        try {
            policy.executeWithRetry(() -> {
                int count = attempts.incrementAndGet();
                long now = System.currentTimeMillis();

                if (count > 1) {
                    backoffTimes[count - 2] = now - lastAttemptTime;
                }
                lastAttemptTime = now;

                throw new RuntimeException("Fail");
            });
        } catch (Exception e) {
        }

        assertTrue("First backoff should be ~100ms", backoffTimes[0] >= 100 && backoffTimes[0] < 200);
        assertTrue("Second backoff should be ~200ms", backoffTimes[1] >= 200 && backoffTimes[1] < 300);
        assertTrue("Third backoff should be ~400ms", backoffTimes[2] >= 400 && backoffTimes[2] < 500);
    }

    public void testInterruptedRetry() {
        RetryPolicy policy = new RetryPolicy(3, 5000);
        AtomicInteger attempts = new AtomicInteger(0);

        Thread testThread = new Thread(() -> {
            try {
                policy.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new RuntimeException("Fail");
                });
            } catch (Exception e) {
                assertTrue(e.getMessage().contains("Retry interrupted"));
            }
        });

        testThread.start();

        try {
            Thread.sleep(100);
            testThread.interrupt();
            testThread.join(1000);
        } catch (InterruptedException e) {
            fail("Test thread interrupted");
        }

        assertTrue("Should have attempted at least once", attempts.get() >= 1);
        assertTrue("Should not complete all retries", attempts.get() < 3);
    }
}
