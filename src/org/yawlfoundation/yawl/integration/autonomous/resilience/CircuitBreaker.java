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

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Circuit breaker pattern implementation for resilient service calls in the
 * YAWL autonomous agent framework.
 *
 * <p>Adapter wrapping Resilience4j 2.3.0 CircuitBreaker for backward compatibility.
 * Implements the circuit breaker state machine to prevent cascading failures
 * and provide fast-fail behavior when downstream services are degraded or unavailable.
 * The circuit has three states:
 * <ul>
 *   <li><strong>CLOSED</strong>: Normal operation, requests pass through</li>
 *   <li><strong>OPEN</strong>: Too many failures, requests fail immediately</li>
 *   <li><strong>HALF_OPEN</strong>: Testing if service recovered, single request allowed</li>
 * </ul>
 * </p>
 *
 * <p>Backed by Resilience4j 2.3.0 for production-grade resilience patterns.
 * Thread-safe via Resilience4j's internal mechanisms.</p>
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
     * Circuit breaker state enumeration, mapped from Resilience4j states.
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
    private final io.github.resilience4j.circuitbreaker.CircuitBreaker r4jBreaker;

    private static final CircuitBreakerRegistry REGISTRY = createSharedRegistry();

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

        // Create Resilience4j circuit breaker with equivalent configuration
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(100.0f)  // Trigger on failure count, not rate
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(failureThreshold)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(Duration.ofMillis(openDurationMs))
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordExceptions(Exception.class)
            .build();

        this.r4jBreaker = REGISTRY.circuitBreaker(name, config);
    }

    /**
     * Executes the provided callable, managing circuit breaker state transitions.
     *
     * <p>Delegates to Resilience4j's CircuitBreaker implementation.</p>
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

        try {
            return r4jBreaker.executeCallable(operation);
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            throw new CircuitBreakerOpenException(name);
        }
    }

    /**
     * Manually resets the circuit breaker to CLOSED state and clears failure count.
     *
     * <p>Use this to restore the circuit without waiting for the recovery timeout.</p>
     */
    public void reset() {
        r4jBreaker.reset();
    }

    /**
     * Returns the current state of the circuit breaker.
     *
     * @return the current {@link State}
     */
    public State getState() {
        return mapR4jState(r4jBreaker.getState());
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
     * <p>Note: Resilience4j tracks failure rate and counts, this returns
     * the failure count from the sliding window.</p>
     *
     * @return the consecutive failure count
     */
    public int getConsecutiveFailures() {
        var metrics = r4jBreaker.getMetrics();
        return metrics.getNumberOfFailedCalls();
    }

    // Private helper methods

    private static CircuitBreakerRegistry createSharedRegistry() {
        return CircuitBreakerRegistry.of(
            CircuitBreakerConfig.ofDefaults(),
            new RegistryEventConsumer<io.github.resilience4j.circuitbreaker.CircuitBreaker>() {
                @Override
                public void onEntryAddedEvent(EntryAddedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> event) {
                    // No-op: logging handled by Resilience4j
                }

                @Override
                public void onEntryRemovedEvent(io.github.resilience4j.core.registry.EntryRemovedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> event) {
                    // No-op
                }

                @Override
                public void onEntryReplacedEvent(io.github.resilience4j.core.registry.EntryReplacedEvent<io.github.resilience4j.circuitbreaker.CircuitBreaker> event) {
                    // No-op
                }
            }
        );
    }

    private static State mapR4jState(io.github.resilience4j.circuitbreaker.CircuitBreaker.State r4jState) {
        return switch (r4jState) {
            case CLOSED -> State.CLOSED;
            case OPEN -> State.OPEN;
            case HALF_OPEN -> State.HALF_OPEN;
            case DISABLED -> State.CLOSED;  // Treat disabled as closed
            case METRICS_ONLY -> State.CLOSED;  // Treat metrics-only as closed
        };
    }
}
