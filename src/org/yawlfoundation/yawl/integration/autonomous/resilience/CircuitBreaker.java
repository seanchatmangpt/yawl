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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.resilience;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Circuit breaker for fault tolerance and resilience.
 *
 * <p>Implements the circuit breaker pattern to prevent cascading failures
 * and provide graceful degradation when external services are unavailable.</p>
 *
 * <p>States:
 * - CLOSED: Normal operation, calls pass through
 * - OPEN: Fast fail, immediately throws exception
 * - HALF_OPEN: Trial call, if succeeds returns to CLOSED, if fails back to OPEN</p>
 *
 * @since YAWL 6.0
 */
public class CircuitBreaker {

    /**
     * Circuit breaker states.
     */
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final long openDurationMs;
    private final ReentrantLock stateLock = new ReentrantLock();

    private volatile State state = State.CLOSED;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastStateChangeTime = new AtomicLong(System.currentTimeMillis());

    /**
     * Creates a circuit breaker with default settings.
     *
     * @param name the circuit breaker name
     */
    public CircuitBreaker(String name) {
        this(name, 5, 60000); // Default: 5 failures, 60s timeout
    }

    /**
     * Creates a circuit breaker with custom settings.
     *
     * @param name the circuit breaker name
     * @param failureThreshold number of failures before opening
     * @param openDurationMs duration to stay open before allowing trial
     */
    public CircuitBreaker(String name, int failureThreshold, long openDurationMs) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("Failure threshold must be positive");
        }
        if (openDurationMs <= 0) {
            throw new IllegalArgumentException("Open duration must be positive");
        }

        this.name = name.trim();
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;
    }

    /**
     * Executes a callable with circuit breaker protection.
     *
     * @param callable the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws CircuitBreakerOpenException if circuit is open
     * @throws Exception if the callable throws an exception
     */
    public <T> T execute(Callable<T> callable) throws CircuitBreakerOpenException, Exception {
        Objects.requireNonNull(callable, "Callable is required");

        checkState();

        try {
            T result = callable.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    /**
     * Checks if the circuit breaker allows execution.
     *
     * @throws CircuitBreakerOpenException if circuit is open
     */
    private void checkState() throws CircuitBreakerOpenException {
        stateLock.lock();
        try {
            if (state == State.OPEN) {
                if (shouldAllowTrial()) {
                    setState(State.HALF_OPEN);
                } else {
                    throw new CircuitBreakerOpenException(
                        "CircuitBreaker '" + name + "' is OPEN and not ready for trial");
                }
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Checks if a trial should be allowed in OPEN state.
     *
     * @return true if enough time has passed
     */
    private boolean shouldAllowTrial() {
        long now = System.currentTimeMillis();
        long timeSinceLastFailure = now - lastFailureTime.get();
        return timeSinceLastFailure >= openDurationMs;
    }

    /**
     * Called when a call succeeds.
     */
    private void onSuccess() {
        stateLock.lock();
        try {
            consecutiveFailures.set(0);
            if (state == State.HALF_OPEN) {
                setState(State.CLOSED);
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Called when a call fails.
     */
    private void onFailure() {
        stateLock.lock();
        try {
            int failures = consecutiveFailures.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());

            if (state == State.CLOSED && failures >= failureThreshold) {
                setState(State.OPEN);
            } else if (state == State.HALF_OPEN) {
                setState(State.OPEN);
            }
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Sets the circuit breaker state.
     *
     * @param newState the new state
     */
    private void setState(State newState) {
        this.state = newState;
        this.lastStateChangeTime.set(System.currentTimeMillis());
        consecutiveFailures.set(0);
    }

    /**
     * Gets the current state.
     *
     * @return the current state
     */
    public State getState() {
        return state;
    }

    /**
     * Gets the number of consecutive failures.
     *
     * @return the failure count
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Gets the failure threshold.
     *
     * @return the threshold
     */
    public int getFailureThreshold() {
        return failureThreshold;
    }

    /**
     * Gets the duration the circuit stays open.
     *
     * @return the duration in milliseconds
     */
    public long getOpenDurationMs() {
        return openDurationMs;
    }

    /**
     * Gets the circuit breaker name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the time since the last state change.
     *
     * @return the time in milliseconds
     */
    public long getTimeSinceStateChange() {
        return System.currentTimeMillis() - lastStateChangeTime.get();
    }

    /**
     * Forces the circuit breaker to reset to CLOSED state.
     */
    public void reset() {
        stateLock.lock();
        try {
            setState(State.CLOSED);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Forces the circuit breaker to OPEN state.
     */
    public void open() {
        stateLock.lock();
        try {
            setState(State.OPEN);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Gets circuit breaker statistics for monitoring.
     *
     * @return statistics string
     */
    public String getStats() {
        return String.format(
            "CircuitBreaker{name='%s', state=%s, failures=%d, threshold=%d, timeSinceChange=%dms}",
            name, state, consecutiveFailures.get(), failureThreshold, getTimeSinceStateChange());
    }

    /**
     * Functional interface for operations to be protected by the circuit breaker.
     */
    @FunctionalInterface
    public interface Callable<T> {
        /**
         * Executes the operation.
         *
         * @return the result
         * @throws Exception if the operation fails
         */
        T call() throws Exception;
    }

    /**
     * Exception thrown when the circuit breaker is open.
     */
    public static class CircuitBreakerOpenException extends Exception {
        /**
         * Creates a new circuit breaker open exception.
         *
         * @param message the error message
         */
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}