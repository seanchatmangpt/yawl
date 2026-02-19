package org.yawlfoundation.yawl.integration.autonomous.resilience;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.resilience.observability.FallbackObservability;
import org.yawlfoundation.yawl.resilience.observability.RetryObservability;

/**
 * Exponential backoff retry wrapper for transient failures.
 * Implements retry logic with exponential backoff: 2s, 4s, 8s, etc.
 *
 * Thread-safe for concurrent use.
 *
 * Integrates with RetryObservability for OpenTelemetry metrics and tracing.
 *
 * @author YAWL Production Validator
 * @version 6.0.0
 */
public class RetryPolicy {

    private static final Logger logger = LoggerFactory.getLogger(RetryPolicy.class);
    private static final long INITIAL_BACKOFF_MS = 2000;
    private static final String COMPONENT_NAME = "retry-policy";

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final RetryObservability observability;

    /**
     * Create retry policy with default settings.
     * Default: 3 attempts, 2s initial backoff.
     */
    public RetryPolicy() {
        this(3, INITIAL_BACKOFF_MS);
    }

    /**
     * Create retry policy with custom settings.
     *
     * @param maxAttempts Maximum number of attempts (must be >= 1)
     * @param initialBackoffMs Initial backoff in milliseconds (must be > 0)
     */
    public RetryPolicy(int maxAttempts, long initialBackoffMs) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
        }
        if (initialBackoffMs <= 0) {
            throw new IllegalArgumentException("initialBackoffMs must be > 0, got: " + initialBackoffMs);
        }

        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.observability = RetryObservability.getInstance();
    }

    /**
     * Execute operation with retry logic.
     * Uses exponential backoff on failures.
     *
     * @param operation Operation to execute
     * @param <T> Return type
     * @return Result of successful execution
     * @throws Exception Last exception if all attempts fail
     */
    public <T> T executeWithRetry(Callable<T> operation) throws Exception {
        return executeWithRetry(operation, "execute", maxAttempts);
    }

    /**
     * Execute operation with retry logic and operation name for observability.
     *
     * @param operationName Name of the operation for metrics
     * @param operation Operation to execute
     * @param <T> Return type
     * @return Result of successful execution
     * @throws Exception Last exception if all attempts fail
     */
    public <T> T executeWithRetry(String operationName, Callable<T> operation) throws Exception {
        return executeWithRetry(operation, operationName, maxAttempts);
    }

    /**
     * Execute operation with specified number of retry attempts.
     * Uses exponential backoff on failures.
     *
     * @param operation Operation to execute
     * @param attempts Number of attempts (must be >= 1)
     * @param <T> Return type
     * @return Result of successful execution
     * @throws Exception Last exception if all attempts fail
     */
    public <T> T executeWithRetry(Callable<T> operation, int attempts) throws Exception {
        return executeWithRetry(operation, "execute", attempts);
    }

    /**
     * Execute operation with specified number of retry attempts and observability.
     * Uses exponential backoff on failures.
     *
     * @param operation Operation to execute
     * @param operationName Name of the operation for metrics
     * @param attempts Number of attempts (must be >= 1)
     * @param <T> Return type
     * @return Result of successful execution
     * @throws Exception Last exception if all attempts fail
     */
    public <T> T executeWithRetry(Callable<T> operation, String operationName, int attempts) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException("operation cannot be null");
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be >= 1, got: " + attempts);
        }

        Exception lastException = null;
        long backoffMs = 0;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            RetryObservability.RetryContext ctx = observability.startRetry(
                    COMPONENT_NAME, operationName, attempt, attempts, backoffMs);

            try {
                T result = operation.call();

                if (attempt > 1) {
                    logger.info("Operation succeeded on attempt {}/{}", attempt, attempts);
                }

                ctx.recordSuccess();
                observability.completeSequence(COMPONENT_NAME, operationName, true, attempt);
                return result;

            } catch (Exception e) {
                lastException = e;
                ctx.recordFailure(e);

                if (attempt < attempts) {
                    backoffMs = calculateBackoff(attempt);

                    logger.warn("Operation failed on attempt {}/{}, retrying after {}ms: {}",
                               attempt, attempts, backoffMs, e.getMessage());

                    observability.recordBackoff(COMPONENT_NAME, operationName, backoffMs);

                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry interrupted, aborting operation");
                        observability.completeSequence(COMPONENT_NAME, operationName, false, attempt);
                        throw new Exception("Retry interrupted", ie);
                    }
                } else {
                    logger.error("Operation failed on final attempt {}/{}: {}",
                                attempt, attempts, e.getMessage());
                    observability.completeSequence(COMPONENT_NAME, operationName, false, attempt);
                }
            }
        }

        throw new Exception("Operation failed after " + attempts + " attempts", lastException);
    }

    /**
     * Calculate exponential backoff delay.
     * Formula: initialBackoff * 2^(attempt - 1)
     *
     * @param attempt Current attempt number (1-based)
     * @return Backoff delay in milliseconds
     */
    private long calculateBackoff(int attempt) {
        return initialBackoffMs * (long) Math.pow(2, attempt - 1);
    }

    /**
     * Execute operation with retry, converting checked exceptions to runtime.
     * Useful for operations that shouldn't throw checked exceptions.
     *
     * @param operation Operation to execute
     * @param <T> Return type
     * @return Result of successful execution
     * @throws RuntimeException If all attempts fail
     */
    public <T> T executeWithRetryUnchecked(Callable<T> operation) {
        try {
            return executeWithRetry(operation);
        } catch (Exception e) {
            throw new RuntimeException("Retry failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute operation with retry and fallback on exhaustion.
     * If all retry attempts fail, uses fallback supplier instead of throwing.
     * Records fallback operations with FallbackObservability.
     *
     * @param operation Operation to execute
     * @param fallback Fallback supplier when all retries exhausted
     * @param operationName Name for observability
     * @param <T> Return type
     * @return Result of successful execution or fallback
     */
    public <T> T executeWithRetryAndFallback(Callable<T> operation,
                                             Supplier<T> fallback,
                                             String operationName) {
        if (operation == null) {
            throw new IllegalArgumentException("operation cannot be null");
        }

        Exception lastException = null;
        long backoffMs = 0;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            RetryObservability.RetryContext ctx = observability.startRetry(
                    COMPONENT_NAME, operationName, attempt, maxAttempts, backoffMs);

            try {
                T result = operation.call();

                if (attempt > 1) {
                    logger.info("Operation [{}] succeeded on attempt {}/{}",
                               operationName, attempt, maxAttempts);
                }

                ctx.recordSuccess();
                observability.completeSequence(COMPONENT_NAME, operationName, true, attempt);
                return result;

            } catch (Exception e) {
                lastException = e;
                ctx.recordFailure(e);

                if (attempt < maxAttempts) {
                    backoffMs = calculateBackoff(attempt);

                    logger.warn("Operation [{}] failed on attempt {}/{}, retrying after {}ms: {}",
                               operationName, attempt, maxAttempts, backoffMs, e.getMessage());

                    observability.recordBackoff(COMPONENT_NAME, operationName, backoffMs);

                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry interrupted, using fallback");
                        break;
                    }
                } else {
                    logger.error("Operation [{}] failed on final attempt {}/{}, using fallback: {}",
                                operationName, attempt, maxAttempts, e.getMessage());
                    observability.completeSequence(COMPONENT_NAME, operationName, false, attempt);
                }
            }
        }

        // All retries exhausted - use fallback with observability
        if (fallback != null) {
            FallbackObservability fallbackObs = FallbackObservability.getInstance();
            FallbackObservability.FallbackResult result = fallbackObs.recordRetryExhaustedFallback(
                COMPONENT_NAME,
                operationName,
                fallback::get,
                lastException
            );

            if (result.isStale()) {
                logger.warn("Retry exhausted fallback [{}] served stale data (age={}ms)",
                           operationName, result.getDataAgeMs());
            }

            @SuppressWarnings("unchecked")
            T typedResult = (T) result.getValue();
            return typedResult;
        }

        throw new RuntimeException("Operation [" + operationName + "] failed after " +
            maxAttempts + " attempts and no fallback provided", lastException);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }
}
