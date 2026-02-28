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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

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
 * <p>Thread safety is ensured via {@link AtomicReference} with compare-and-swap (CAS)
 * to eliminate carrier-thread blocking and support virtual threads efficiently.</p>
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

    /**
     * Internal sealed state hierarchy for atomic state transitions via CAS.
     */
    private sealed interface CircuitState permits Closed, Open, HalfOpen {}

    /**
     * CLOSED state: normal operation with failure counter.
     *
     * @param failures current consecutive failure count
     */
    private record Closed(int failures) implements CircuitState {}

    /**
     * OPEN state: rejecting calls, tracking when opened.
     *
     * @param openedAt instant when circuit transitioned to OPEN
     * @param failures failure count that triggered the open
     */
    private record Open(Instant openedAt, int failures) implements CircuitState {}

    /**
     * HALF_OPEN state: testing if service recovered.
     */
    private record HalfOpen() implements CircuitState {}

    private final String name;
    private final int failureThreshold;
    private final long openDurationMs;

    private final AtomicReference<CircuitState> _state =
            new AtomicReference<>(new Closed(0));

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

        // Spin-loop: check state and transition OPEN -> HALF_OPEN if timeout elapsed
        while (true) {
            CircuitState current = _state.get();
            switch (current) {
                case Closed c -> {
                    // CLOSED: proceed to execution
                    break;
                }
                case HalfOpen h -> {
                    // HALF_OPEN: proceed as probe
                    break;
                }
                case Open o -> {
                    // OPEN: check if recovery timeout has elapsed
                    long elapsedMs = Duration.between(o.openedAt(), Instant.now()).toMillis();
                    if (elapsedMs < openDurationMs) {
                        // Timeout not elapsed, fast-fail
                        throw new CircuitBreakerOpenException(name);
                    }
                    // Timeout elapsed, try transition OPEN -> HALF_OPEN
                    if (_state.compareAndSet(current, new HalfOpen())) {
                        break; // Transition succeeded, proceed as probe
                    }
                    // CAS failed (someone else transitioned), retry loop
                    continue;
                }
            }
            break; // Exit spin-loop, ready to execute
        }

        // Execute the callable outside the state machine to avoid pinning virtual threads
        try {
            T result = operation.call();

            // On success, handle state transitions via CAS
            while (true) {
                CircuitState s = _state.get();
                CircuitState next = switch (s) {
                    case HalfOpen h -> new Closed(0);   // Recovery successful
                    case Closed c -> new Closed(0);      // Success in CLOSED, reset failures
                    case Open o -> s;                    // Should not happen after pre-check
                };
                if (s == next || _state.compareAndSet(s, next)) {
                    break;
                }
            }
            return result;
        } catch (Exception e) {
            // On failure, handle state transitions via CAS
            while (true) {
                CircuitState s = _state.get();
                CircuitState next = switch (s) {
                    case HalfOpen h -> new Open(Instant.now(), failureThreshold);
                    case Closed c -> {
                        int newFailures = c.failures() + 1;
                        yield newFailures >= failureThreshold
                                ? new Open(Instant.now(), newFailures)
                                : new Closed(newFailures);
                    }
                    case Open o -> o;  // Already open, no state change on failure
                };
                if (s == next || _state.compareAndSet(s, next)) {
                    break;
                }
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
        _state.set(new Closed(0));
    }

    /**
     * Returns the current state of the circuit breaker.
     *
     * <p>If the circuit is OPEN and the recovery timeout has elapsed, this method
     * will transition to HALF_OPEN via CAS.</p>
     *
     * @return the current {@link State}
     */
    public State getState() {
        CircuitState internal = _state.get();
        return switch (internal) {
            case Closed c -> State.CLOSED;
            case HalfOpen h -> State.HALF_OPEN;
            case Open o -> {
                // Check if recovery timeout has elapsed
                long elapsedMs = Duration.between(o.openedAt(), Instant.now()).toMillis();
                if (elapsedMs >= openDurationMs) {
                    // Attempt transition OPEN -> HALF_OPEN
                    _state.compareAndSet(internal, new HalfOpen());
                    yield State.HALF_OPEN;
                }
                yield State.OPEN;
            }
        };
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
        return switch (_state.get()) {
            case Closed c -> c.failures();
            case Open o -> o.failures();
            case HalfOpen h -> 0;
        };
    }
}
