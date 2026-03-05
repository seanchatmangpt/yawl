package org.yawlfoundation.yawl.integration.a2a.resilience;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Circuit breaker with automatic recovery via exponential backoff and health checks.
 *
 * Implements the 80/20 autonomic self-healing pattern for A2A server resilience.
 * Auto-resets after exponential backoff, runs health check before reset attempt,
 * and tracks metrics for decision-making.
 *
 * <p><b>States:</b>
 * <ul>
 *   <li><b>CLOSED</b> - Normal operation, requests flow through</li>
 *   <li><b>OPEN</b> - Circuit tripped, requests fail immediately</li>
 *   <li><b>HALF_OPEN</b> - Testing recovery, one request allowed</li>
 * </ul>
 *
 * <p><b>Recovery mechanism:</b>
 * <ol>
 *   <li>Track failure count and timestamp</li>
 *   <li>When threshold reached, OPEN circuit immediately</li>
 *   <li>After exponential backoff delay, move to HALF_OPEN</li>
 *   <li>In HALF_OPEN: run health check supplier</li>
 *   <li>If health check passes, reset to CLOSED (clear failure count)</li>
 *   <li>If health check fails, remain OPEN and increase backoff</li>
 * </ol>
 *
 * <p><b>Metrics provided:</b>
 * - Failure count
 * - State (CLOSED/OPEN/HALF_OPEN)
 * - Last failure time
 * - Next recovery attempt time
 * - Total recovery attempts
 *
 * Thread-safe via ReentrantLock. No blocking I/O in the circuit breaker itself;
 * health check is delegated to caller's supplier.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class CircuitBreakerAutoRecovery {

    private static final Logger logger = LogManager.getLogger(CircuitBreakerAutoRecovery.class);

    /**
     * Circuit breaker states
     */
    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private static final int DEFAULT_FAILURE_THRESHOLD = 5;
    private static final long DEFAULT_INITIAL_BACKOFF_MS = 1000;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    private static final long DEFAULT_MAX_BACKOFF_MS = 60000;

    private final String name;
    private final int failureThreshold;
    private final long initialBackoffMs;
    private final double backoffMultiplier;
    private final long maxBackoffMs;
    private final Supplier<Boolean> healthCheck;

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTimeMs = new AtomicLong(0);
    private final AtomicLong nextRecoveryTimeMs = new AtomicLong(0);
    private final AtomicInteger recoveryAttempts = new AtomicInteger(0);
    private volatile State state = State.CLOSED;
    private volatile long currentBackoffMs = DEFAULT_INITIAL_BACKOFF_MS;

    /**
     * Construct circuit breaker with default parameters.
     *
     * @param name name for logging and metrics
     * @param healthCheck supplier that returns true if service is healthy
     */
    public CircuitBreakerAutoRecovery(String name, Supplier<Boolean> healthCheck) {
        this(name, DEFAULT_FAILURE_THRESHOLD, DEFAULT_INITIAL_BACKOFF_MS,
            DEFAULT_BACKOFF_MULTIPLIER, DEFAULT_MAX_BACKOFF_MS, healthCheck);
    }

    /**
     * Construct circuit breaker with custom parameters.
     *
     * @param name name for logging and metrics
     * @param failureThreshold number of failures before opening circuit
     * @param initialBackoffMs initial backoff duration in milliseconds
     * @param backoffMultiplier multiplier for exponential backoff
     * @param maxBackoffMs maximum backoff duration in milliseconds
     * @param healthCheck supplier that returns true if service is healthy
     */
    public CircuitBreakerAutoRecovery(String name,
                                       int failureThreshold,
                                       long initialBackoffMs,
                                       double backoffMultiplier,
                                       long maxBackoffMs,
                                       Supplier<Boolean> healthCheck) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoffMs = maxBackoffMs;
        this.healthCheck = healthCheck;
        this.currentBackoffMs = initialBackoffMs;
    }

    /**
     * Execute operation with circuit breaker protection.
     * If circuit is OPEN and recovery time has elapsed, attempts health check
     * and auto-recovery.
     *
     * @param operation operation to execute
     * @param <T> return type
     * @return result of operation
     * @throws CircuitBreakerOpenException if circuit is OPEN
     */
    public <T> T execute(Supplier<T> operation) {
        lock.lock();
        try {
            // Check if we should attempt recovery
            if (state == State.OPEN) {
                long nowMs = System.currentTimeMillis();
                if (nowMs >= nextRecoveryTimeMs.get()) {
                    attemptRecovery();
                } else {
                    throw new CircuitBreakerOpenException(
                        "Circuit breaker '" + name + "' is OPEN. "
                        + "Next recovery attempt in "
                        + (nextRecoveryTimeMs.get() - nowMs) + " ms");
                }
            }

            // If we're in HALF_OPEN or recovered to CLOSED, allow the operation
            if (state == State.CLOSED || state == State.HALF_OPEN) {
                try {
                    T result = operation.get();
                    // Success: reset failure count
                    if (state == State.HALF_OPEN) {
                        onRecoverySuccess();
                    }
                    return result;
                } catch (Exception e) {
                    onFailure();
                    throw e;
                }
            }

            throw new CircuitBreakerOpenException(
                "Circuit breaker '" + name + "' is OPEN");

        } finally {
            lock.unlock();
        }
    }

    /**
     * Record a failure and check if threshold reached.
     */
    public void recordFailure() {
        lock.lock();
        try {
            onFailure();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Manually reset circuit breaker to CLOSED state.
     */
    public void reset() {
        lock.lock();
        try {
            state = State.CLOSED;
            failureCount.set(0);
            currentBackoffMs = initialBackoffMs;
            recoveryAttempts.set(0);
            logger.info("Circuit breaker '{}' manually reset to CLOSED", name);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get current circuit breaker state.
     */
    public State getState() {
        return state;
    }

    /**
     * Get current failure count.
     */
    public int getFailureCount() {
        return failureCount.get();
    }

    /**
     * Get time of last failure (epoch milliseconds), or 0 if no failures.
     */
    public long getLastFailureTimeMs() {
        return lastFailureTimeMs.get();
    }

    /**
     * Get next scheduled recovery attempt time (epoch milliseconds), or 0 if not scheduled.
     */
    public long getNextRecoveryTimeMs() {
        return nextRecoveryTimeMs.get();
    }

    /**
     * Get number of recovery attempts tried.
     */
    public int getRecoveryAttempts() {
        return recoveryAttempts.get();
    }

    /**
     * Get current backoff duration in milliseconds.
     */
    public long getCurrentBackoffMs() {
        return currentBackoffMs;
    }

    // Private helper methods

    private void onFailure() {
        long nowMs = System.currentTimeMillis();
        failureCount.incrementAndGet();
        lastFailureTimeMs.set(nowMs);

        if (failureCount.get() >= failureThreshold && state != State.OPEN) {
            state = State.OPEN;
            nextRecoveryTimeMs.set(nowMs + currentBackoffMs);
            logger.warn("Circuit breaker '{}' opened after {} failures. "
                + "Recovery in {} ms",
                name, failureCount.get(), currentBackoffMs);
        }
    }

    private void onRecoverySuccess() {
        state = State.CLOSED;
        failureCount.set(0);
        currentBackoffMs = initialBackoffMs;
        recoveryAttempts.set(0);
        logger.info("Circuit breaker '{}' recovered to CLOSED", name);
    }

    private void attemptRecovery() {
        state = State.HALF_OPEN;
        recoveryAttempts.incrementAndGet();
        logger.info("Circuit breaker '{}' attempting recovery (attempt #{})",
            name, recoveryAttempts.get());

        try {
            if (healthCheck.get()) {
                onRecoverySuccess();
            } else {
                // Health check failed: increase backoff and retry
                increaseBackoff();
                long nextRetry = System.currentTimeMillis() + currentBackoffMs;
                nextRecoveryTimeMs.set(nextRetry);
                state = State.OPEN;
                logger.warn("Circuit breaker '{}' health check failed. "
                    + "Next recovery in {} ms (backoff increased)",
                    name, currentBackoffMs);
            }
        } catch (Exception e) {
            // Health check threw exception: treat as failure
            increaseBackoff();
            long nextRetry = System.currentTimeMillis() + currentBackoffMs;
            nextRecoveryTimeMs.set(nextRetry);
            state = State.OPEN;
            logger.warn("Circuit breaker '{}' health check threw exception. "
                + "Next recovery in {} ms",
                name, currentBackoffMs, e);
        }
    }

    private void increaseBackoff() {
        long newBackoff = (long) (currentBackoffMs * backoffMultiplier);
        currentBackoffMs = Math.min(newBackoff, maxBackoffMs);
    }

    /**
     * Exception thrown when circuit breaker is OPEN.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
