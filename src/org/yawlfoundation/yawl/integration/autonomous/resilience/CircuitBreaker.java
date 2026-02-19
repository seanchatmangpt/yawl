package org.yawlfoundation.yawl.integration.autonomous.resilience;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.resilience.observability.FallbackObservability;

/**
 * Circuit breaker for external service calls.
 * Prevents cascading failures by failing fast when service is unhealthy.
 *
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Service failing, requests fail immediately
 * - HALF_OPEN: Testing if service recovered, limited requests allowed
 *
 * Thread-safe for concurrent use.
 *
 * @author YAWL Production Validator
 * @version 5.2
 */
public class CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    /**
     * Circuit breaker states.
     */
    public enum State {
        CLOSED,      // Normal operation
        OPEN,        // Failing fast
        HALF_OPEN    // Testing recovery
    }

    private final String name;
    private final int failureThreshold;
    private final long openDurationMs;

    private final AtomicReference<State> state;
    private final AtomicInteger consecutiveFailures;
    private final AtomicLong lastFailureTime;

    /**
     * Create circuit breaker with default settings.
     * Default: 5 failures threshold, 30s open duration.
     *
     * @param name Circuit breaker name for logging
     */
    public CircuitBreaker(String name) {
        this(name, 5, 30000);
    }

    /**
     * Create circuit breaker with custom settings.
     *
     * @param name Circuit breaker name for logging
     * @param failureThreshold Number of consecutive failures before opening (must be > 0)
     * @param openDurationMs Duration to stay open in milliseconds (must be > 0)
     */
    public CircuitBreaker(String name, int failureThreshold, long openDurationMs) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be > 0, got: " + failureThreshold);
        }
        if (openDurationMs <= 0) {
            throw new IllegalArgumentException("openDurationMs must be > 0, got: " + openDurationMs);
        }

        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDurationMs = openDurationMs;

        this.state = new AtomicReference<>(State.CLOSED);
        this.consecutiveFailures = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
    }

    /**
     * Execute operation through circuit breaker.
     * Fails fast if circuit is open.
     *
     * @param operation Operation to execute
     * @param <T> Return type
     * @return Result of successful execution
     * @throws Exception If operation fails or circuit is open
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException("operation cannot be null");
        }

        State currentState = getStateAndUpdate();

        if (currentState == State.OPEN) {
            String msg = String.format("Circuit breaker [%s] is OPEN, failing fast", name);
            logger.warn(msg);
            throw new CircuitBreakerOpenException(msg);
        }

        try {
            T result = operation.call();
            onSuccess();
            return result;

        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    /**
     * Get current state and update if necessary.
     * Transitions OPEN -> HALF_OPEN if timeout elapsed.
     *
     * @return Current effective state
     */
    private State getStateAndUpdate() {
        State currentState = state.get();

        if (currentState == State.OPEN) {
            long timeSinceFailure = System.currentTimeMillis() - lastFailureTime.get();

            if (timeSinceFailure >= openDurationMs) {
                if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                    logger.info("Circuit breaker [{}] transitioning OPEN -> HALF_OPEN after {}ms",
                               name, timeSinceFailure);
                    return State.HALF_OPEN;
                }
            }
        }

        return state.get();
    }

    /**
     * Handle successful operation.
     * Transitions HALF_OPEN -> CLOSED or resets failure counter.
     */
    private void onSuccess() {
        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                consecutiveFailures.set(0);
                logger.info("Circuit breaker [{}] transitioning HALF_OPEN -> CLOSED after successful test",
                           name);
            }
        } else if (currentState == State.CLOSED) {
            int failures = consecutiveFailures.get();
            if (failures > 0) {
                consecutiveFailures.set(0);
                logger.debug("Circuit breaker [{}] reset failure counter after success", name);
            }
        }
    }

    /**
     * Handle failed operation.
     * Transitions CLOSED -> OPEN if threshold reached.
     * Transitions HALF_OPEN -> OPEN on any failure.
     */
    private void onFailure() {
        lastFailureTime.set(System.currentTimeMillis());

        State currentState = state.get();

        if (currentState == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                logger.warn("Circuit breaker [{}] transitioning HALF_OPEN -> OPEN after test failure",
                           name);
            }
        } else if (currentState == State.CLOSED) {
            int failures = consecutiveFailures.incrementAndGet();

            logger.debug("Circuit breaker [{}] recorded failure {}/{}",
                        name, failures, failureThreshold);

            if (failures >= failureThreshold) {
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    logger.error("Circuit breaker [{}] transitioning CLOSED -> OPEN after {} consecutive failures",
                                name, failures);
                }
            }
        }
    }

    /**
     * Get current circuit breaker state.
     *
     * @return Current state
     */
    public State getState() {
        return getStateAndUpdate();
    }

    /**
     * Get current consecutive failure count.
     *
     * @return Number of consecutive failures
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Reset circuit breaker to CLOSED state.
     * Clears failure counter.
     */
    public void reset() {
        state.set(State.CLOSED);
        consecutiveFailures.set(0);
        logger.info("Circuit breaker [{}] manually reset to CLOSED", name);
    }

    /**
     * Execute operation through circuit breaker with fallback.
     * If circuit is open, uses fallback supplier instead of failing fast.
     * Records all fallback operations with observability.
     *
     * @param operation Operation to execute
     * @param fallback Fallback supplier when circuit is open
     * @param <T> Return type
     * @return Result of successful execution or fallback
     * @throws Exception If operation fails and circuit is closed (no fallback used)
     */
    public <T> T executeWithFallback(Callable<T> operation,
                                     java.util.function.Supplier<T> fallback) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException("operation cannot be null");
        }

        State currentState = getStateAndUpdate();

        if (currentState == State.OPEN) {
            String msg = String.format("Circuit breaker [%s] is OPEN, using fallback", name);
            logger.warn(msg);

            if (fallback == null) {
                throw new CircuitBreakerOpenException(
                    "Circuit breaker [" + name + "] is OPEN and no fallback provided");
            }

            // Record fallback with observability
            FallbackObservability fallbackObs = FallbackObservability.getInstance();
            FallbackObservability.FallbackResult result = fallbackObs.recordCircuitBreakerFallback(
                "circuit-breaker-" + name,
                "execute",
                fallback
            );

            if (result.isStale()) {
                logger.warn("Circuit breaker [{}] fallback served stale data (age={}ms)",
                           name, result.getDataAgeMs());
            }

            @SuppressWarnings("unchecked")
            T typedResult = (T) result.getValue();
            return typedResult;
        }

        try {
            T result = operation.call();
            onSuccess();
            return result;

        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    public String getName() {
        return name;
    }

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public long getOpenDurationMs() {
        return openDurationMs;
    }

    /**
     * Exception thrown when circuit breaker is open.
     */
    public static class CircuitBreakerOpenException extends Exception {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
