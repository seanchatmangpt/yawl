package org.yawlfoundation.yawl.integration.autonomous.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

/**
 * Exponential backoff retry wrapper for transient failures.
 * Implements retry logic with exponential backoff: 2s, 4s, 8s, etc.
 *
 * Thread-safe for concurrent use.
 *
 * @author YAWL Production Validator
 * @version 5.2
 */
public class RetryPolicy {

    private static final Logger logger = LoggerFactory.getLogger(RetryPolicy.class);
    private static final long INITIAL_BACKOFF_MS = 2000;

    private final int maxAttempts;
    private final long initialBackoffMs;

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
        return executeWithRetry(operation, maxAttempts);
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
        if (operation == null) {
            throw new IllegalArgumentException("operation cannot be null");
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be >= 1, got: " + attempts);
        }

        Exception lastException = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                T result = operation.call();

                if (attempt > 1) {
                    logger.info("Operation succeeded on attempt {}/{}", attempt, attempts);
                }

                return result;

            } catch (Exception e) {
                lastException = e;

                if (attempt < attempts) {
                    long backoffMs = calculateBackoff(attempt);

                    logger.warn("Operation failed on attempt {}/{}, retrying after {}ms: {}",
                               attempt, attempts, backoffMs, e.getMessage());

                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Retry interrupted, aborting operation");
                        throw new Exception("Retry interrupted", ie);
                    }
                } else {
                    logger.error("Operation failed on final attempt {}/{}: {}",
                                attempt, attempts, e.getMessage());
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

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }
}
