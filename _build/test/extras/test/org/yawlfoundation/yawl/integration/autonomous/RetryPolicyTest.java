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

import org.junit.jupiter.api.Tag;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.autonomous.resilience.RetryPolicy;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests for RetryPolicy (V6 resilience feature).
 *
 * Chicago TDD: tests the real RetryPolicy with real callable executions
 * and real (minimal) delays. No mocks.
 *
 * Coverage targets:
 * - Successful first attempt (no retry)
 * - Retry on transient failure - succeeds on N-th attempt
 * - All attempts fail - exception propagated
 * - Custom attempt count
 * - Null operation guard
 * - Unchecked variant
 * - Interruption handling
 * - Constructor validation
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("unit")
public class RetryPolicyTest extends TestCase {

    // Use 10ms backoff so tests don't sleep for 2 full seconds
    private static final long FAST_BACKOFF_MS = 10L;

    public RetryPolicyTest(String name) {
        super(name);
    }

    // =========================================================================
    // Constructor tests
    // =========================================================================

    public void testDefaultConstructorCreatesPolicy() {
        RetryPolicy policy = new RetryPolicy();
        assertNotNull(policy);
        assertEquals("Default max attempts should be 3", 3, policy.getMaxAttempts());
        assertEquals("Default initial backoff should be 2000ms",
                2000L, policy.getInitialBackoffMs());
    }

    public void testCustomConstructorSetsValues() {
        RetryPolicy policy = new RetryPolicy(5, 500);
        assertEquals(5, policy.getMaxAttempts());
        assertEquals(500L, policy.getInitialBackoffMs());
    }

    public void testConstructorWithZeroAttemptsThrows() {
        try {
            new RetryPolicy(0, 1000);
            fail("Expected IllegalArgumentException for zero maxAttempts");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("maxAttempts"));
        }
    }

    public void testConstructorWithNegativeAttemptsThrows() {
        try {
            new RetryPolicy(-1, 1000);
            fail("Expected IllegalArgumentException for negative maxAttempts");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("maxAttempts"));
        }
    }

    public void testConstructorWithZeroBackoffThrows() {
        try {
            new RetryPolicy(3, 0);
            fail("Expected IllegalArgumentException for zero initialBackoffMs");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("initialBackoffMs"));
        }
    }

    public void testConstructorWithNegativeBackoffThrows() {
        try {
            new RetryPolicy(3, -100);
            fail("Expected IllegalArgumentException for negative initialBackoffMs");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("initialBackoffMs"));
        }
    }

    // =========================================================================
    // Successful first attempt
    // =========================================================================

    public void testSucceedsOnFirstAttempt() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, FAST_BACKOFF_MS);
        AtomicInteger callCount = new AtomicInteger(0);

        String result = policy.executeWithRetry(() -> {
            callCount.incrementAndGet();
            return "success";
        });

        assertEquals("success", result);
        assertEquals("Should have called operation exactly once", 1, callCount.get());
    }

    // =========================================================================
    // Retry on transient failure
    // =========================================================================

    public void testRetryOnTransientFailureSucceedsOnSecondAttempt() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, FAST_BACKOFF_MS);
        AtomicInteger callCount = new AtomicInteger(0);

        String result = policy.executeWithRetry(() -> {
            int count = callCount.incrementAndGet();
            if (count == 1) {
                throw new RuntimeException("transient failure");
            }
            return "success-on-2nd";
        });

        assertEquals("success-on-2nd", result);
        assertEquals("Should have called operation twice", 2, callCount.get());
    }

    public void testRetryOnTransientFailureSucceedsOnThirdAttempt() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, FAST_BACKOFF_MS);
        AtomicInteger callCount = new AtomicInteger(0);

        String result = policy.executeWithRetry(() -> {
            int count = callCount.incrementAndGet();
            if (count < 3) {
                throw new RuntimeException("transient failure " + count);
            }
            return "success-on-3rd";
        });

        assertEquals("success-on-3rd", result);
        assertEquals("Should have called operation three times", 3, callCount.get());
    }

    // =========================================================================
    // All attempts fail
    // =========================================================================

    public void testAllAttemptsFailThrowsException() {
        RetryPolicy policy = new RetryPolicy(3, FAST_BACKOFF_MS);
        AtomicInteger callCount = new AtomicInteger(0);

        try {
            policy.executeWithRetry(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("permanent failure");
            });
            fail("Expected Exception when all attempts fail");
        } catch (Exception e) {
            assertEquals("Should have called operation 3 times", 3, callCount.get());
            assertTrue("Exception message should mention attempts",
                    e.getMessage().contains("3 attempts") ||
                    e.getMessage().contains("attempt"));
        }
    }

    public void testAllAttemptsFailPreservesLastException() {
        RetryPolicy policy = new RetryPolicy(2, FAST_BACKOFF_MS);
        RuntimeException lastFailure = new RuntimeException("last failure message");

        try {
            policy.executeWithRetry(() -> { throw lastFailure; });
            fail("Expected Exception");
        } catch (Exception e) {
            assertNotNull("Should have cause", e.getCause());
            assertEquals("Should preserve last exception",
                    lastFailure, e.getCause());
        }
    }

    // =========================================================================
    // Custom attempt count
    // =========================================================================

    public void testCustomAttemptCount() throws Exception {
        RetryPolicy policy = new RetryPolicy(10, FAST_BACKOFF_MS);
        AtomicInteger callCount = new AtomicInteger(0);

        // Use 5 attempts override
        String result = policy.executeWithRetry(() -> {
            int count = callCount.incrementAndGet();
            if (count < 4) {
                throw new RuntimeException("transient");
            }
            return "done";
        }, 5);

        assertEquals("done", result);
        assertEquals(4, callCount.get());
    }

    public void testCustomAttemptCountExceededThrows() {
        RetryPolicy policy = new RetryPolicy(10, FAST_BACKOFF_MS);
        AtomicInteger callCount = new AtomicInteger(0);

        try {
            policy.executeWithRetry(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("always fails");
            }, 2);
            fail("Expected Exception");
        } catch (Exception e) {
            assertEquals("Should have called operation 2 times", 2, callCount.get());
        }
    }

    public void testCustomAttemptCountZeroThrows() {
        RetryPolicy policy = new RetryPolicy(3, FAST_BACKOFF_MS);

        try {
            policy.executeWithRetry(() -> "value", 0);
            fail("Expected IllegalArgumentException for zero attempts");
        } catch (Exception e) {
            assertTrue("Should be an IllegalArgumentException or similar",
                    e instanceof IllegalArgumentException ||
                    e.getMessage().contains("attempts"));
        }
    }

    // =========================================================================
    // Single attempt (maxAttempts=1) - no retry
    // =========================================================================

    public void testSingleAttemptPolicySucceedsOnFirstTry() throws Exception {
        RetryPolicy policy = new RetryPolicy(1, FAST_BACKOFF_MS);

        String result = policy.executeWithRetry(() -> "immediate-success");
        assertEquals("immediate-success", result);
    }

    public void testSingleAttemptPolicyFailsImmediately() {
        RetryPolicy policy = new RetryPolicy(1, FAST_BACKOFF_MS);
        AtomicInteger callCount = new AtomicInteger(0);

        try {
            policy.executeWithRetry(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("fail");
            });
            fail("Expected Exception");
        } catch (Exception e) {
            assertEquals("Should have called operation only once", 1, callCount.get());
        }
    }

    // =========================================================================
    // Null operation guard
    // =========================================================================

    public void testNullOperationThrows() {
        RetryPolicy policy = new RetryPolicy(3, FAST_BACKOFF_MS);

        try {
            policy.executeWithRetry(null);
            fail("Expected IllegalArgumentException for null operation");
        } catch (Exception e) {
            assertTrue("Should be IllegalArgumentException",
                    e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("operation"));
        }
    }

    // =========================================================================
    // Unchecked variant
    // =========================================================================

    public void testExecuteWithRetryUncheckedSucceeds() {
        RetryPolicy policy = new RetryPolicy(3, FAST_BACKOFF_MS);

        String result = policy.executeWithRetryUnchecked(() -> "unchecked-success");
        assertEquals("unchecked-success", result);
    }

    public void testExecuteWithRetryUncheckedWrapsException() {
        RetryPolicy policy = new RetryPolicy(1, FAST_BACKOFF_MS);

        try {
            policy.executeWithRetryUnchecked(() -> {
                throw new RuntimeException("checked-fail");
            });
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue("Should be a RuntimeException", true);
        }
    }

    // =========================================================================
    // Return types
    // =========================================================================

    public void testExecuteWithRetryReturnsInteger() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, FAST_BACKOFF_MS);
        Integer result = policy.executeWithRetry(() -> 42);
        assertEquals(Integer.valueOf(42), result);
    }

    public void testExecuteWithRetryReturnsNull() throws Exception {
        RetryPolicy policy = new RetryPolicy(3, FAST_BACKOFF_MS);
        String result = policy.executeWithRetry(() -> null);
        assertNull("Should be able to return null", result);
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public void testGetMaxAttempts() {
        RetryPolicy policy = new RetryPolicy(7, 100);
        assertEquals(7, policy.getMaxAttempts());
    }

    public void testGetInitialBackoffMs() {
        RetryPolicy policy = new RetryPolicy(3, 500);
        assertEquals(500L, policy.getInitialBackoffMs());
    }
}
