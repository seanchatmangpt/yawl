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

package org.yawlfoundation.yawl.integration.autonomous.resilience;

import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Circuit breaker pattern implementation for resilient service calls in the
 * YAWL autonomous agent framework.
 *
 * <p>Implements the circuit breaker state machine to prevent cascading failures
 * and provide fast-fail behavior when downstream services are degraded or unavailable.
 * The circuit has three states:
 * <ul>
 *   <li><strong>CLOSED</strong>: Normal operation, requests pass through</li>
 *   <li><strong>OPEN</strong>: Too many failures, requests fail immediately</li>
 *   <li><strong>HALF_OPEN</strong>: Testing if service recovered, single request allowed</li>
 * </ul>
 * </p>
 *
 * <p>Thread safety is ensured via {@link ReentrantLock} to support virtual threads
 * without pinning.</p>
 *
 * <p>Typical usage:
 * <pre>
 * CircuitBreaker breaker = new CircuitBreaker("payment-service", 5, 30000);
 * try {
 *   PaymentResult result = breaker.execute(() -> paymentService.charge(order));
 * } catch (CircuitBreakerOpenException e) {
 *   // Service is degraded, use fallback
 *   handleDegraded();
 * }
 * </pre>
 * </p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class CircuitBreaker {

    /**
     * Circuit breaker state enumeration.
     */
    public enum State {
        /** Circuit allows calls to proceed normally */
        CLOSED,
        /** Circuit rejects calls immediately (fast-fail) */
        OPEN,
        /** Circuit allows a single test call to determine if service recovered */
        HALF_OPEN
    }

    /**
     * Exception thrown when the circuit breaker is OPEN and rejects the call.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        /**
         * Constructs an exception with message containing circuit name and OPEN status.
         *
         * @param circuitName name of the circuit breaker
         */
        public CircuitBreakerOpenException(String circuitName) {
            super("Circuit breaker '" + circuitName + "' is OPEN");
        }
    }

    private final String name;
    private final int failureThreshold;
    private final long openDurationMs;

    private final ReentrantLock lock = new ReentrantLock();

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private long lastFailureTimeMs = 0;

    /**
     * Constructs a circuit breaker with default settings.
     *
     * <p>Defaults: failureThreshold=5, openDurationMs=30000</p>
     *
     * @param name the circuit breaker name (must not be null or empty)
     * @throws IllegalArgumentException if name is null or empty
     */
    public CircuitBreaker(String name) {
        this(name, 5, 30000);
    }

    /**
     * Constructs a circuit breaker with custom settings.
     *
     * @param name the circuit breaker name (must not be null or empty)
     * @param failureThreshold number of consecutive failures before opening (must be > 0)
     * @param openDurationMs duration in milliseconds to stay open before attempting recovery (must be > 0)
     * @throws IllegalArgumentException if name is null/empty, failureThreshold <= 0, or openDurationMs <= 0
     */
    public CircuitBreaker(String name, int failureThreshold, long openDurationMs) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or empty");
        }
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be > 0");
        }
        if (openDurationMs <= 0) {
            throw new IllegalArgumentException("openDurationMs must be > 0");
        }

        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
    }

    /**
     * Executes the provided callable, managing circuit breaker state transitions.
     *
     * <p><strong>CLOSED state:</strong> Executes the callable. On success, resets failure count.
     * On failure, increments failure count; if threshold reached, transitions to OPEN.
     * The original exception is rethrown.
     * </p>
     *
     * <p><strong>OPEN state:</strong> Checks if open duration has elapsed. If not elapsed,
     * throws CircuitBreakerOpenException immediately (fast-fail). If elapsed, transitions
     * to HALF_OPEN and allows the execution.
     * </p>
     *
     * <p><strong>HALF_OPEN state:</strong> Executes the callable as a recovery test.
     * On success, transitions to CLOSED and resets failure count. On failure,
     * transitions back to OPEN.
     * </p>
     *
     * @param <T> the return type of the callable
     * @param operation the callable to execute (must not be null)
     * @return the result of the callable execution
     * @throws IllegalArgumentException if operation is null
     * @throws CircuitBreakerOpenException if circuit is OPEN and recovery timeout not elapsed
     * @throws Exception any exception thrown by the operation (when execution is allowed)
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }

        lock.lock();
        try {
            // Check if OPEN -> HALF_OPEN transition should occur
            if (state == State.OPEN && System.currentTimeMillis() - lastFailureTimeMs >= openDurationMs) {
                state = State.HALF_OPEN;
            }

            // In OPEN state (and timeout not elapsed), reject the call
            if (state == State.OPEN) {
                throw new CircuitBreakerOpenException(name);
            }

            // Release lock during actual operation execution
        } finally {
            lock.unlock();
        }

        // Execute the callable outside the lock to avoid pinning virtual threads
        try {
            T result = operation.call();

            // On success, handle state transitions
            lock.lock();
            try {
                if (state == State.HALF_OPEN) {
                    // Recovery successful
                    state = State.CLOSED;
                    consecutiveFailures = 0;
                    lastFailureTimeMs = 0;
                } else if (state == State.CLOSED) {
                    // Maintain success in CLOSED state
                    consecutiveFailures = 0;
                }
            } finally {
                lock.unlock();
            }

            return result;
        } catch (Exception e) {
            // On failure, handle state transitions
            lock.lock();
            try {
                lastFailureTimeMs = System.currentTimeMillis();

                if (state == State.HALF_OPEN) {
                    // Recovery failed, reopen
                    state = State.OPEN;
                } else if (state == State.CLOSED) {
                    consecutiveFailures++;
                    if (consecutiveFailures >= failureThreshold) {
                        state = State.OPEN;
                    }
                }
            } finally {
                lock.unlock();
            }

            throw e;
        }
    }

    /**
     * Manually resets the circuit breaker to CLOSED state and clears failure count.
     *
     * <p>Use this to restore the circuit without waiting for the recovery timeout.</p>
     */
    public void reset() {
        lock.lock();
        try {
            state = State.CLOSED;
            consecutiveFailures = 0;
            lastFailureTimeMs = 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the current state of the circuit breaker.
     *
     * <p>If the circuit is OPEN and the recovery timeout has elapsed, this method
     * will transition to HALF_OPEN.</p>
     *
     * @return the current {@link State}
     */
    public State getState() {
        lock.lock();
        try {
            // Check and apply OPEN -> HALF_OPEN transition if applicable
            if (state == State.OPEN && System.currentTimeMillis() - lastFailureTimeMs >= openDurationMs) {
                state = State.HALF_OPEN;
            }
            return state;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the circuit breaker name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the failure threshold.
     *
     * @return the number of consecutive failures before opening
     */
    public int getFailureThreshold() {
        return failureThreshold;
    }

    /**
     * Returns the open duration in milliseconds.
     *
     * @return the duration to stay open before attempting recovery
     */
    public long getOpenDurationMs() {
        return openDurationMs;
    }

    /**
     * Returns the current count of consecutive failures.
     *
     * @return the consecutive failure count
     */
    public int getConsecutiveFailures() {
        lock.lock();
        try {
            return consecutiveFailures;
        } finally {
            lock.unlock();
        }
    }
}
