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
import java.util.concurrent.TimeUnit;

/**
 * Retry policy for transient failure handling.
 *
 * <p>Implements exponential backoff with jitter for retrying operations
 * that may fail transiently due to network issues, temporary unavailability,
 * or other transient failures.</p>
 *
 * @since YAWL 6.0
 */
public class RetryPolicy {

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final double backoffMultiplier;
    private final long maxBackoffMs;
    private final boolean fastFirstRetry;

    /**
     * Creates a retry policy with default settings.
     */
    public RetryPolicy() {
        this(3, 1000L, 2.0, 30000L, true);
    }

    /**
     * Creates a retry policy with custom settings.
     *
     * @param maxAttempts maximum number of attempts
     * @param initialBackoffMs initial backoff duration
     * @param backoffMultiplier multiplier for each subsequent backoff
     * @param maxBackoffMs maximum backoff duration
     * @param fastFirstRetry whether to skip backoff on first retry
     */
    public RetryPolicy(int maxAttempts, long initialBackoffMs, double backoffMultiplier,
                      long maxBackoffMs, boolean fastFirstRetry) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("Max attempts must be positive");
        }
        if (initialBackoffMs < 0) {
            throw new IllegalArgumentException("Initial backoff must be non-negative");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("Backoff multiplier must be >= 1.0");
        }
        if (maxBackoffMs < 0) {
            throw new IllegalArgumentException("Max backoff must be non-negative");
        }

        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoffMs = maxBackoffMs;
        this.fastFirstRetry = fastFirstRetry;
    }

    /**
     * Executes an operation with retry protection.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws Exception if all attempts fail
     */
    public <T> T execute(Operation<T> operation) throws Exception {
        Objects.requireNonNull(operation, "Operation is required");

        Exception lastException = null;
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                return operation.execute();
            } catch (Exception e) {
                lastException = e;

                if (attempt >= maxAttempts) {
                    break;
                }

                long backoff = calculateBackoff(attempt);
                try {
                    if (backoff > 0) {
                        Thread.sleep(backoff);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new Exception("Retry interrupted", ie);
                }
            }
        }

        throw new RetryFailedException("Operation failed after " + maxAttempts + " attempts", lastException);
    }

    /**
     * Executes an operation without throwing checked exceptions.
     *
     * @param operation the operation to execute
     * @param <T> the return type
     * @return the result of the operation
     * @throws RetryRuntimeException if all attempts fail
     */
    public <T> T executeUnchecked(Operation<T> operation) {
        try {
            return execute(operation);
        } catch (Exception e) {
            throw new RetryRuntimeException("Operation failed after " + maxAttempts + " attempts", e);
        }
    }

    /**
     * Calculates the backoff duration for an attempt.
     *
     * @param attempt the attempt number (1-based)
     * @return the backoff duration in milliseconds
     */
    private long calculateBackoff(int attempt) {
        if (fastFirstRetry && attempt == 1) {
            return 0;
        }

        long backoff = initialBackoffMs;
        for (int i = 1; i < attempt; i++) {
            backoff = (long) (backoff * backoffMultiplier);
            if (backoff > maxBackoffMs) {
                backoff = maxBackoffMs;
                break;
            }
        }

        // Add jitter (±25%)
        double jitter = 0.5 + Math.random(); // 0.5 to 1.5
        backoff = (long) (backoff * jitter);

        return Math.min(backoff, maxBackoffMs);
    }

    /**
     * Gets the maximum number of attempts.
     *
     * @return the max attempts
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * Gets the initial backoff duration.
     *
     * @return the initial backoff in milliseconds
     */
    public long getInitialBackoffMs() {
        return initialBackoffMs;
    }

    /**
     * Gets the backoff multiplier.
     *
     * @return the multiplier
     */
    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /**
     * Gets the maximum backoff duration.
     *
     * @return the max backoff in milliseconds
     */
    public long getMaxBackoffMs() {
        return maxBackoffMs;
    }

    /**
     * Checks if first retry should be fast (no delay).
     *
     * @return true if fast first retry is enabled
     */
    public boolean isFastFirstRetry() {
        return fastFirstRetry;
    }

    /**
     * Functional interface for operations to be retried.
     */
    @FunctionalInterface
    public interface Operation<T> {
        /**
         * Executes the operation.
         *
         * @return the result
         * @throws Exception if the operation fails
         */
        T execute() throws Exception;
    }

    /**
     * Exception thrown when all retry attempts fail.
     */
    public static class RetryFailedException extends Exception {
        /**
         * Creates a new retry failed exception.
         *
         * @param message the error message
         * @param cause the underlying cause
         */
        public RetryFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Runtime exception thrown when all retry attempts fail (unchecked version).
     */
    public static class RetryRuntimeException extends RuntimeException {
        /**
         * Creates a new retry runtime exception.
         *
         * @param message the error message
         * @param cause the underlying cause
         */
        public RetryRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Builder for creating retry policies.
     */
    public static class Builder {
        private int maxAttempts = 3;
        private long initialBackoffMs = 1000L;
        private double backoffMultiplier = 2.0;
        private long maxBackoffMs = 30000L;
        private boolean fastFirstRetry = true;

        /**
         * Sets the maximum number of attempts.
         *
         * @param maxAttempts the max attempts
         * @return this builder
         */
        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Sets the initial backoff duration.
         *
         * @param initialBackoffMs the initial backoff in milliseconds
         * @return this builder
         */
        public Builder initialBackoffMs(long initialBackoffMs) {
            this.initialBackoffMs = initialBackoffMs;
            return this;
        }

        /**
         * Sets the backoff multiplier.
         *
         * @param backoffMultiplier the multiplier
         * @return this builder
         */
        public Builder backoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        /**
         * Sets the maximum backoff duration.
         *
         * @param maxBackoffMs the max backoff in milliseconds
         * @return this builder
         */
        public Builder maxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
            return this;
        }

        /**
         * Sets whether to enable fast first retry.
         *
         * @param fastFirstRetry true to skip delay on first retry
         * @return this builder
         */
        public Builder fastFirstRetry(boolean fastFirstRetry) {
            this.fastFirstRetry = fastFirstRetry;
            return this;
        }

        /**
         * Builds the retry policy.
         *
         * @return the new retry policy
         */
        public RetryPolicy build() {
            return new RetryPolicy(maxAttempts, initialBackoffMs, backoffMultiplier,
                                 maxBackoffMs, fastFirstRetry);
        }
    }
}