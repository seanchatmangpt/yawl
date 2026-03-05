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

/**
 * Resilience pattern for autonomous agent operations: exponential backoff retry policy.
 *
 * <p>Provides configurable retry semantics with exponential backoff for transient failures.
 * Each retry waits twice as long as the previous retry:
 * <ul>
 *   <li>1st attempt: no delay</li>
 *   <li>2nd attempt: initialBackoffMs delay before retry</li>
 *   <li>3rd attempt: initialBackoffMs * 2 delay before retry</li>
 *   <li>4th attempt: initialBackoffMs * 4 delay before retry, etc.</li>
 * </ul>
 *
 * <p>Two execution modes:
 * <ul>
 *   <li>{@link #executeWithRetry(Callable)} — checked exception variant, throws Exception</li>
 *   <li>{@link #executeWithRetryUnchecked(Callable)} — wraps checked exceptions as RuntimeException</li>
 * </ul>
 *
 * <p>Example usage (agent operation that may timeout):
 * <pre>{@code
 * RetryPolicy policy = new RetryPolicy(5, 1000);  // 5 attempts, 1s initial backoff
 * try {
 *   Agent agent = policy.executeWithRetry(() -> agentRegistry.lookup(agentId));
 * } catch (Exception e) {
 *   logger.error("All 5 attempts exhausted", e);
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final long initialBackoffMs;

    /**
     * Create a retry policy with default settings:
     * maxAttempts = 3, initialBackoffMs = 2000.
     */
    public RetryPolicy() {
        this(3, 2000);
    }

    /**
     * Create a retry policy with custom settings.
     *
     * @param maxAttempts minimum 1; number of total attempts (includes first attempt)
     * @param initialBackoffMs minimum 1; milliseconds to wait before first retry
     * @throws IllegalArgumentException if maxAttempts &lt;= 0 or initialBackoffMs &lt;= 0
     */
    public RetryPolicy(int maxAttempts, long initialBackoffMs) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0, got: " + maxAttempts);
        }
        if (initialBackoffMs <= 0) {
            throw new IllegalArgumentException("initialBackoffMs must be > 0, got: " + initialBackoffMs);
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
    }

    /**
     * Execute an operation with retry using the policy's maxAttempts.
     *
     * <p>Calls operation up to maxAttempts times. On success, returns the result immediately.
     * On failure, sleeps for exponential backoff (initialBackoffMs * 2^attemptIndex) and retries.
     *
     * @param <T> operation result type
     * @param operation the callable to execute; must not be null
     * @return the result of operation if successful within maxAttempts
     * @throws IllegalArgumentException if operation is null
     * @throws Exception wrapping the last exception if all attempts are exhausted; message
     *         contains "attempts" and the maxAttempts count
     */
    public <T> T executeWithRetry(Callable<T> operation) throws Exception {
        return executeWithRetry(operation, this.maxAttempts);
    }

    /**
     * Execute an operation with retry using a custom attempt count.
     *
     * <p>Calls operation up to maxAttempts times (overriding the policy's default).
     * On success, returns the result immediately.
     * On failure, sleeps for exponential backoff (initialBackoffMs * 2^attemptIndex) and retries.
     *
     * @param <T> operation result type
     * @param operation the callable to execute; must not be null
     * @param maxAttempts minimum 1; number of total attempts for this call (overrides policy)
     * @return the result of operation if successful within maxAttempts
     * @throws IllegalArgumentException if operation is null or maxAttempts &lt;= 0
     * @throws Exception wrapping the last exception if all attempts are exhausted; message
     *         contains "attempts" and the maxAttempts count
     */
    public <T> T executeWithRetry(Callable<T> operation, int maxAttempts) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0 for executeWithRetry, got: " + maxAttempts);
        }

        Exception lastException = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return operation.call();
            } catch (Exception e) {
                lastException = e;

                // If this was the last attempt, break and throw
                if (attempt >= maxAttempts - 1) {
                    break;
                }

                // Calculate backoff: initialBackoffMs * 2^attempt
                long backoffMs = initialBackoffMs * (1L << attempt);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    // Restore interrupted status and re-throw the original exception
                    Thread.currentThread().interrupt();
                    throw lastException;
                }
            }
        }

        // All attempts exhausted
        Exception result = new Exception(
            "All " + maxAttempts + " attempts exhausted",
            lastException
        );
        throw result;
    }

    /**
     * Execute an operation with retry, wrapping checked exceptions as RuntimeException.
     *
     * <p>Convenience method for use in contexts where checked exceptions are inconvenient
     * (e.g., in stream operations, lambda callbacks).
     *
     * @param <T> operation result type
     * @param operation the callable to execute; must not be null
     * @return the result of operation if successful within maxAttempts
     * @throws RuntimeException wrapping the last exception if all attempts are exhausted,
     *         or if operation itself throws a checked exception; if the cause is already
     *         a RuntimeException, it is rethrown as-is
     * @throws IllegalArgumentException if operation is null (from executeWithRetry)
     */
    public <T> T executeWithRetryUnchecked(Callable<T> operation) {
        try {
            return executeWithRetry(operation);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get the policy's configured maximum attempt count.
     *
     * @return the maxAttempts setting
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Get the policy's configured initial backoff delay.
     *
     * @return the initialBackoffMs setting
     */
    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }
}
