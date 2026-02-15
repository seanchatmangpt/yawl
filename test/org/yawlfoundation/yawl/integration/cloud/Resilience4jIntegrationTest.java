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
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.cloud;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker;
import org.yawlfoundation.yawl.integration.autonomous.resilience.RetryPolicy;
import org.yawlfoundation.yawl.integration.autonomous.resilience.FallbackHandler;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration tests for Resilience4j circuit breaker behavior under failure.
 *
 * Tests cover:
 * - Circuit breaker state transitions (CLOSED -> OPEN -> HALF_OPEN -> CLOSED)
 * - Retry policies with exponential backoff
 * - Fallback handlers and graceful degradation
 * - Bulkhead isolation
 * - Timeout handling
 * - Metrics collection
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class Resilience4jIntegrationTest extends TestCase {

    private CircuitBreaker circuitBreaker;
    private RetryPolicy retryPolicy;
    private FallbackHandler fallbackHandler;

    public Resilience4jIntegrationTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        circuitBreaker = new CircuitBreaker("test-breaker", 3, 1000);
        retryPolicy = new RetryPolicy();
        fallbackHandler = new FallbackHandler();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test circuit breaker closed state (normal operation)
     */
    public void testCircuitBreakerClosedState() throws Exception {
        assertEquals("Initial state is CLOSED", CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        
        String result = circuitBreaker.execute(() -> "success");
        assertEquals("Call succeeds", "success", result);
        assertEquals("State remains CLOSED", CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    /**
     * Test circuit breaker open state on threshold
     */
    public void testCircuitBreakerOpenState() throws Exception {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (Exception e) {
                // Expected
            }
        }
        
        assertEquals("State is OPEN after failures", CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    /**
     * Test circuit breaker fail-fast when open
     */
    public void testCircuitBreakerFailFast() throws Exception {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (Exception e) {
            }
        }
        
        AtomicInteger callCount = new AtomicInteger(0);
        try {
            circuitBreaker.execute(() -> {
                callCount.incrementAndGet();
                return "success";
            });
            fail("Should reject when OPEN");
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            assertEquals("No actual call when OPEN", 0, callCount.get());
        }
    }

    /**
     * Test circuit breaker half-open state
     */
    public void testCircuitBreakerHalfOpenState() throws Exception {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (Exception e) {
            }
        }
        
        assertEquals("State is OPEN", CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        Thread.sleep(1100);
        
        assertEquals("State transitions to HALF_OPEN", CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
    }

    /**
     * Test circuit breaker recovery from HALF_OPEN
     */
    public void testCircuitBreakerRecovery() throws Exception {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (Exception e) {
            }
        }
        
        Thread.sleep(1100);
        assertEquals("State is HALF_OPEN", CircuitBreaker.State.HALF_OPEN, circuitBreaker.getState());
        
        String result = circuitBreaker.execute(() -> "success");
        assertEquals("Call succeeds", "success", result);
        assertEquals("State returns to CLOSED", CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    /**
     * Test circuit breaker reopen from HALF_OPEN on failure
     */
    public void testCircuitBreakerReopenFromHalfOpen() throws Exception {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (Exception e) {
            }
        }
        
        Thread.sleep(1100);
        
        try {
            circuitBreaker.execute(() -> {
                throw new RuntimeException("fail");
            });
        } catch (Exception e) {
        }
        
        assertEquals("State reopens", CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    /**
     * Test retry policy with fixed interval
     */
    public void testRetryPolicyFixedInterval() throws Exception {
        retryPolicy.setMaxRetries(3);
        retryPolicy.setIntervalMs(100);
        retryPolicy.setBackoffType("fixed");
        
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        Object result = retryPolicy.execute(() -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new RuntimeException("fail " + attempt);
            }
            return "success";
        });
        
        assertEquals("Result after retries", "success", result);
        assertEquals("3 attempts made", 3, attemptCount.get());
    }

    /**
     * Test retry policy with exponential backoff
     */
    public void testRetryPolicyExponentialBackoff() throws Exception {
        retryPolicy.setMaxRetries(3);
        retryPolicy.setIntervalMs(50);
        retryPolicy.setBackoffType("exponential");
        retryPolicy.setBackoffMultiplier(2.0);
        
        AtomicInteger attemptCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        
        try {
            retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("always fail");
            });
        } catch (Exception e) {
            // Expected after all retries exhausted
        }
        
        long duration = System.currentTimeMillis() - startTime;
        assertEquals("3 retry attempts", 3, attemptCount.get());
        assertTrue("Exponential backoff takes time", duration >= 150); // 50 + 100
    }

    /**
     * Test fallback handler
     */
    public void testFallbackHandler() throws Exception {
        fallbackHandler.setFallbackValue("default_value");
        
        Object result = fallbackHandler.execute(() -> {
            throw new RuntimeException("fail");
        });
        
        assertEquals("Fallback value returned", "default_value", result);
    }

    /**
     * Test fallback with transformation
     */
    public void testFallbackWithTransformation() throws Exception {
        fallbackHandler.setFallbackFunction(ex -> "error: " + ex.getMessage());
        
        Object result = fallbackHandler.execute(() -> {
            throw new RuntimeException("operation failed");
        });
        
        assertEquals("Fallback applied with transformation", "error: operation failed", result);
    }

    /**
     * Test combined circuit breaker and retry
     */
    public void testCombinedCircuitBreakerAndRetry() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        try {
            for (int i = 0; i < 5; i++) {
                try {
                    circuitBreaker.execute(() -> {
                        attemptCount.incrementAndGet();
                        throw new RuntimeException("fail");
                    });
                } catch (Exception e) {
                    // Consume exception, continue
                }
            }
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            // Expected when threshold exceeded
        }
        
        assertTrue("Some attempts made", attemptCount.get() > 0);
    }

    /**
     * Test timeout handling
     */
    public void testTimeoutHandling() throws Exception {
        circuitBreaker.setTimeoutMs(100);
        
        try {
            circuitBreaker.execute(() -> {
                Thread.sleep(200);
                return "should timeout";
            });
            fail("Should timeout");
        } catch (CircuitBreaker.TimeoutException e) {
            assertTrue("Timeout exception thrown", true);
        }
    }

    /**
     * Test bulkhead isolation
     */
    public void testBulkheadIsolation() throws Exception {
        circuitBreaker.setBulkheadSize(2);
        
        final int threadCount = 5;
        Thread[] threads = new Thread[threadCount];
        final int[] rejectionCount = {0};
        
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    circuitBreaker.execute(() -> {
                        Thread.sleep(500);
                        return "done";
                    });
                } catch (CircuitBreaker.BulkheadException e) {
                    synchronized(rejectionCount) {
                        rejectionCount[0]++;
                    }
                }
            });
            threads[i].start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        assertTrue("Some requests rejected by bulkhead", rejectionCount[0] > 0);
    }

    /**
     * Test circuit breaker metrics
     */
    public void testCircuitBreakerMetrics() throws Exception {
        for (int i = 0; i < 10; i++) {
            try {
                circuitBreaker.execute(() -> {
                    if (i < 5) {
                        throw new RuntimeException("fail");
                    }
                    return "success";
                });
            } catch (Exception e) {
            }
        }
        
        long totalCalls = circuitBreaker.getTotalCalls();
        long successCalls = circuitBreaker.getSuccessCalls();
        long failureCalls = circuitBreaker.getFailureCalls();
        
        assertEquals("Total calls tracked", 10, totalCalls);
        assertEquals("Success calls tracked", 5, successCalls);
        assertEquals("Failure calls tracked", 5, failureCalls);
    }

    /**
     * Test retry policy jitter
     */
    public void testRetryPolicyWithJitter() throws Exception {
        retryPolicy.setMaxRetries(3);
        retryPolicy.setIntervalMs(100);
        retryPolicy.setJitterFactor(0.1);
        
        AtomicInteger attemptCount = new AtomicInteger(0);
        
        try {
            retryPolicy.execute(() -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("fail");
            });
        } catch (Exception e) {
        }
        
        assertEquals("Retries attempted with jitter", 3, attemptCount.get());
    }

    /**
     * Test concurrent circuit breaker operations
     */
    public void testConcurrentCircuitBreakerOperations() throws Exception {
        final int threadCount = 10;
        final int operationsPerThread = 20;
        Thread[] threads = new Thread[threadCount];
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failureCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        circuitBreaker.execute(() -> {
                            if ((threadIndex + j) % 3 == 0) {
                                throw new RuntimeException("fail");
                            }
                            return "success";
                        });
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                }
            });
            threads[i].start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        int totalOps = successCount.get() + failureCount.get();
        assertTrue("Operations tracked", totalOps >= threadCount * operationsPerThread);
    }

    /**
     * Test manual reset
     */
    public void testManualReset() throws Exception {
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (Exception e) {
            }
        }
        
        assertEquals("State is OPEN", CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        circuitBreaker.reset();
        
        assertEquals("State reset to CLOSED", CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    /**
     * Test eventListener for state changes
     */
    public void testStateChangeListener() throws Exception {
        final String[] lastState = {null};
        
        circuitBreaker.addStateChangeListener(newState -> {
            lastState[0] = newState.toString();
        });
        
        for (int i = 0; i < 3; i++) {
            try {
                circuitBreaker.execute(() -> {
                    throw new RuntimeException("fail");
                });
            } catch (Exception e) {
            }
        }
        
        assertEquals("State change detected", "OPEN", lastState[0]);
    }
}
