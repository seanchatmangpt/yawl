package org.yawlfoundation.yawl.integration.mcp.autonomic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Autonomic Circuit Breaker — Prevent cascading failures (80/20 Win).
 *
 * <p>Pattern: Detects repeated failures and automatically stops attempting
 * operations that are failing. Prevents wasting resources, triggering alerts,
 * cascading system failures.
 *
 * <p>States:
 * - CLOSED: normal operation (< 5% failures)
 * - OPEN: too many failures, reject all requests immediately
 * - HALF_OPEN: recovery mode, allow limited requests
 *
 * <p>Saves: prevents 100+ wasted requests when engine is down.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class AutonomicCircuitBreaker {

    private static final int FAILURE_THRESHOLD = 5; // Open after 5 failures
    private static final long RECOVERY_TIMEOUT_MS = 10_000; // Try recovery after 10s
    private static final int HALF_OPEN_REQUESTS = 3; // Allow 3 requests in half-open

    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicInteger halfOpenRequests = new AtomicInteger(0);

    private final String operationName;

    public AutonomicCircuitBreaker(String operationName) {
        this.operationName = operationName;
    }

    /**
     * Check if operation should be allowed.
     * Throws CircuitBreakerOpenException if circuit is open.
     */
    public void checkPermission() throws CircuitBreakerOpenException {
        State currentState = state;

        if (currentState == State.CLOSED) {
            return; // Normal operation
        }

        if (currentState == State.OPEN) {
            // Check if enough time has passed to recover
            if (System.currentTimeMillis() - lastFailureTime.get() > RECOVERY_TIMEOUT_MS) {
                state = State.HALF_OPEN;
                halfOpenRequests.set(0);
                return; // Allow one more attempt
            }
            throw new CircuitBreakerOpenException(
                operationName + " circuit breaker is OPEN - operation temporarily disabled");
        }

        if (currentState == State.HALF_OPEN) {
            // Allow limited requests during recovery
            if (halfOpenRequests.incrementAndGet() <= HALF_OPEN_REQUESTS) {
                return; // Allow this request
            }
            throw new CircuitBreakerOpenException(
                operationName + " circuit breaker HALF_OPEN - recovery in progress");
        }
    }

    /**
     * Record operation success - reduces failure count.
     */
    public void recordSuccess() {
        successCount.incrementAndGet();
        failureCount.set(0);

        if (state == State.HALF_OPEN) {
            // Recovery successful
            state = State.CLOSED;
            halfOpenRequests.set(0);
        }
    }

    /**
     * Record operation failure - may open circuit.
     */
    public void recordFailure() {
        failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (failureCount.get() >= FAILURE_THRESHOLD && state == State.CLOSED) {
            state = State.OPEN;
        }

        if (state == State.HALF_OPEN) {
            // Recovery failed
            state = State.OPEN;
            failureCount.set(0);
        }
    }

    /**
     * Get circuit breaker state.
     */
    public State getState() {
        return state;
    }

    /**
     * Manual reset (for testing or maintenance).
     */
    public void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        halfOpenRequests.set(0);
    }

    /**
     * Diagnostic info.
     */
    @Override
    public String toString() {
        return String.format(
            "CircuitBreaker{operation=%s, state=%s, failures=%d, successes=%d}",
            operationName, state.name(), failureCount.get(), successCount.get());
    }

    /**
     * Circuit states.
     */
    public enum State {
        CLOSED("Normal operation"),
        OPEN("Too many failures - rejecting requests"),
        HALF_OPEN("Recovery mode - limited requests allowed");

        private final String description;

        State(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Thrown when circuit is open.
     */
    public static class CircuitBreakerOpenException extends Exception {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
