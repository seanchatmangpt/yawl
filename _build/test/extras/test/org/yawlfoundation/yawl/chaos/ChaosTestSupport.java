/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.chaos;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Utility class supporting chaos engineering tests.
 *
 * Provides common chaos injection patterns:
 * - Latency injection
 * - Failure simulation
 * - Resource limiting
 * - Blast radius control
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
final class ChaosTestSupport {

    private ChaosTestSupport() {
        throw new UnsupportedOperationException(
                "ChaosTestSupport is a static utility class");
    }

    // =========================================================================
    // Latency Injection
    // =========================================================================

    /**
     * Injects artificial latency into an operation.
     *
     * @param delayMs   milliseconds to delay
     * @param operation the operation to execute
     * @param <T>       return type
     * @return result of the operation
     */
    static <T> T withLatency(long delayMs, Supplier<T> operation) {
        try {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            return operation.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Latency injection interrupted", e);
        }
    }

    /**
     * Executes an operation with a timeout, simulating slow responses.
     *
     * @param operation the operation to execute
     * @param timeoutMs maximum time to wait
     * @param <T>       return type
     * @return result or null if timeout
     */
    static <T> T withTimeout(Supplier<T> operation, long timeoutMs) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(operation);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Operation failed", e);
        }
    }

    /**
     * Injects variable latency (jitter) into operations.
     *
     * @param minDelayMs minimum delay
     * @param maxDelayMs maximum delay
     * @param operation  the operation to execute
     * @param <T>        return type
     * @return result of the operation
     */
    static <T> T withJitter(long minDelayMs, long maxDelayMs, Supplier<T> operation) {
        long delay = minDelayMs + (long) (Math.random() * (maxDelayMs - minDelayMs));
        return withLatency(delay, operation);
    }

    // =========================================================================
    // Failure Simulation
    // =========================================================================

    /**
     * Simulates a transient failure that resolves after N attempts.
     *
     * @param failUntilAttempt attempts before success
     * @param operation        the operation to execute
     * @param <T>              return type
     * @return result of the operation
     */
    static <T> T withTransientFailure(int failUntilAttempt, Supplier<T> operation) {
        AtomicInteger attempts = new AtomicInteger(0);
        return withRetry(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < failUntilAttempt) {
                throw new RuntimeException("Simulated transient failure at attempt " + attempt);
            }
            return operation.get();
        }, failUntilAttempt + 5, 10);
    }

    /**
     * Executes with retry logic.
     *
     * @param operation        the operation to execute
     * @param maxAttempts      maximum retry attempts
     * @param backoffMs        initial backoff in milliseconds
     * @param <T>              return type
     * @return result of the operation
     */
    static <T> T withRetry(Supplier<T> operation, int maxAttempts, long backoffMs) {
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(backoffMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new RuntimeException("Operation failed after " + maxAttempts + " attempts", lastException);
    }

    /**
     * Simulates intermittent failures (flapping).
     *
     * @param successRate probability of success (0.0 to 1.0)
     * @param operation   the operation to execute
     * @param <T>         return type
     * @return result of the operation
     */
    static <T> T withIntermittentFailure(double successRate, Supplier<T> operation) {
        if (Math.random() > successRate) {
            throw new RuntimeException("Simulated intermittent failure");
        }
        return operation.get();
    }

    // =========================================================================
    // Resource Limiting
    // =========================================================================

    /**
     * Executes with a concurrency limit.
     *
     * @param maxConcurrent maximum concurrent operations
     * @param semaphore     shared semaphore for limiting
     * @param operation     the operation to execute
     * @param <T>           return type
     * @return result of the operation
     */
    static <T> T withConcurrencyLimit(int maxConcurrent,
                                       java.util.concurrent.Semaphore semaphore,
                                       Supplier<T> operation) {
        try {
            if (semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                try {
                    return operation.get();
                } finally {
                    semaphore.release();
                }
            } else {
                throw new RuntimeException("Concurrency limit reached");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for semaphore", e);
        }
    }

    /**
     * Executes with rate limiting.
     *
     * @param permitsPerSecond maximum operations per second
     * @param lastExecution    shared atomic reference for last execution time
     * @param operation        the operation to execute
     * @param <T>              return type
     * @return result of the operation
     */
    static <T> T withRateLimit(int permitsPerSecond,
                                java.util.concurrent.atomic.AtomicLong lastExecution,
                                Supplier<T> operation) {
        long minIntervalMs = 1000L / permitsPerSecond;
        long now = System.currentTimeMillis();
        long last = lastExecution.get();

        if (now - last < minIntervalMs) {
            try {
                Thread.sleep(minIntervalMs - (now - last));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastExecution.set(System.currentTimeMillis());
        return operation.get();
    }

    // =========================================================================
    // Blast Radius Control
    // =========================================================================

    /**
     * Executes with blast radius control - limits impact to specified percentage.
     *
     * @param impactPercentage percentage of calls to affect (0-100)
     * @param failureSupplier  supplies the failure for affected calls
     * @param operation        the normal operation
     * @param <T>              return type
     * @return result or failure
     */
    static <T> T withBlastRadius(int impactPercentage,
                                  Supplier<T> failureSupplier,
                                  Supplier<T> operation) {
        if (Math.random() * 100 < impactPercentage) {
            return failureSupplier.get();
        }
        return operation.get();
    }

    /**
     * Chaos experiment configuration.
     */
    static class ChaosExperiment {
        private final String name;
        private final int impactPercentage;
        private final long durationMs;
        private final AtomicBoolean active = new AtomicBoolean(false);

        ChaosExperiment(String name, int impactPercentage, long durationMs) {
            this.name = name;
            this.impactPercentage = impactPercentage;
            this.durationMs = durationMs;
        }

        void start() {
            active.set(true);
        }

        void stop() {
            active.set(false);
        }

        boolean isActive() {
            return active.get();
        }

        boolean shouldInjectChaos() {
            return isActive() && Math.random() * 100 < impactPercentage;
        }

        String getName() { return name; }
        int getImpactPercentage() { return impactPercentage; }
        long getDurationMs() { return durationMs; }
    }

    // =========================================================================
    // Metrics Collection
    // =========================================================================

    /**
     * Simple metrics collector for chaos experiments.
     */
    static class ChaosMetrics {
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger timeoutCount = new AtomicInteger(0);
        private final java.util.concurrent.atomic.AtomicLong totalLatencyMs = new java.util.concurrent.atomic.AtomicLong(0);

        void recordSuccess(long latencyMs) {
            successCount.incrementAndGet();
            totalLatencyMs.addAndGet(latencyMs);
        }

        void recordFailure() {
            failureCount.incrementAndGet();
        }

        void recordTimeout() {
            timeoutCount.incrementAndGet();
        }

        int getSuccessCount() { return successCount.get(); }
        int getFailureCount() { return failureCount.get(); }
        int getTimeoutCount() { return timeoutCount.get(); }

        double getSuccessRate() {
            int total = successCount.get() + failureCount.get() + timeoutCount.get();
            return total > 0 ? (double) successCount.get() / total * 100 : 0;
        }

        double getAverageLatencyMs() {
            int successes = successCount.get();
            return successes > 0 ? (double) totalLatencyMs.get() / successes : 0;
        }

        void reset() {
            successCount.set(0);
            failureCount.set(0);
            timeoutCount.set(0);
            totalLatencyMs.set(0);
        }

        @Override
        public String toString() {
            return String.format("ChaosMetrics{successes=%d, failures=%d, timeouts=%d, " +
                            "successRate=%.2f%%, avgLatency=%.2fms}",
                    getSuccessCount(), getFailureCount(), getTimeoutCount(),
                    getSuccessRate(), getAverageLatencyMs());
        }
    }
}
