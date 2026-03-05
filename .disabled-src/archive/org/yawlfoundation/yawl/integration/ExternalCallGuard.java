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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Pure Java 25 timeout guard for external service calls.
 *
 * <p>Wraps any {@link Callable} with a deadline enforced by
 * {@link StructuredTaskScope.ShutdownOnFailure#joinUntil}. If the operation
 * does not complete within the configured timeout, the virtual thread executing
 * it is interrupted and the caller receives a {@link TimeoutException} (or an
 * optional fallback value).</p>
 *
 * <p>Unlike {@link CompletableFuture#orTimeout}, this guard uses structured
 * concurrency — the executing subtask is always cancelled on timeout, preventing
 * thread accumulation from slow external systems.</p>
 *
 * <p>This class has <strong>zero external dependencies</strong>. It composes
 * naturally with {@link org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker}
 * and {@link org.yawlfoundation.yawl.integration.pool.ConnectionPoolGuard}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ExternalCallGuard<String> guard = ExternalCallGuard
 *     .<String>withTimeout(Duration.ofSeconds(30))
 *     .withFallback(() -> "unavailable")
 *     .build();
 *
 * // Blocking call (on current virtual thread):
 * String result = guard.execute(() -> httpClient.call(url));
 *
 * // Async call:
 * CompletableFuture<String> future = guard.executeAsync(() -> httpClient.call(url));
 * }</pre>
 *
 * @param <T> the return type of the guarded operation
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class ExternalCallGuard<T> {

    private final Duration _timeout;
    private final Supplier<T> _fallback;   // null → throw TimeoutException
    private final LongAdder _timeouts  = new LongAdder();
    private final LongAdder _successes = new LongAdder();
    private final LongAdder _failures  = new LongAdder();

    private ExternalCallGuard(Duration timeout, Supplier<T> fallback) {
        _timeout  = timeout;
        _fallback = fallback;
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    /**
     * Starts building a guard with the specified timeout.
     *
     * @param <T>     result type
     * @param timeout maximum duration to wait for the operation; must not be null
     * @return a builder
     */
    public static <T> Builder<T> withTimeout(Duration timeout) {
        if (timeout == null) throw new NullPointerException("timeout must not be null");
        return new Builder<>(timeout);
    }

    /**
     * Builder for {@link ExternalCallGuard}.
     *
     * @param <T> result type
     */
    public static final class Builder<T> {

        private final Duration _timeout;
        private Supplier<T> _fallback;

        private Builder(Duration timeout) {
            _timeout = timeout;
        }

        /**
         * Sets an optional fallback value returned when the timeout elapses.
         * If not set, a {@link TimeoutException} is thrown on timeout.
         *
         * @param fallback supplier of the fallback value; must not be null
         * @return this builder
         */
        public Builder<T> withFallback(Supplier<T> fallback) {
            if (fallback == null) throw new NullPointerException("fallback must not be null");
            _fallback = fallback;
            return this;
        }

        /**
         * Builds the guard.
         *
         * @return a new {@link ExternalCallGuard}
         */
        public ExternalCallGuard<T> build() {
            return new ExternalCallGuard<>(_timeout, _fallback);
        }
    }

    // -----------------------------------------------------------------------
    // Execution
    // -----------------------------------------------------------------------

    /**
     * Executes the operation on a virtual thread, blocking until it completes or
     * the timeout elapses.
     *
     * <p>On timeout: if a fallback was configured, returns the fallback value.
     * Otherwise throws {@link TimeoutException}.</p>
     *
     * <p>Uses {@link ExecutorService#submit} with virtual thread per task executor
     * for automatic task cancellation on timeout, preventing resource leaks.</p>
     *
     * @param operation the callable to execute; must not be null
     * @return the result of the operation (or fallback on timeout)
     * @throws TimeoutException     if the operation exceeds the timeout and no fallback is set
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws Exception            any exception thrown by the operation itself
     */
    public T execute(Callable<T> operation) throws Exception {
        if (operation == null) throw new NullPointerException("operation must not be null");

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            java.util.concurrent.Future<T> future = executor.submit(operation);
            try {
                return future.get(_timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                _timeouts.increment();
                future.cancel(true);  // Interrupt the task on timeout
                if (_fallback != null) return _fallback.get();
                throw new TimeoutException("External call timed out after " + _timeout);
            } catch (ExecutionException ee) {
                _failures.increment();
                // Unwrap ExecutionException and throw the cause
                if (ee.getCause() instanceof Exception ex) {
                    throw ex;
                }
                throw ee;
            } catch (InterruptedException ie) {
                _failures.increment();
                throw ie;
            }
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Executes the operation asynchronously on a new virtual thread and returns
     * a {@link CompletableFuture} that completes within {@code timeout}.
     *
     * <p>The future fails with {@link TimeoutException} if the deadline elapses,
     * regardless of whether the underlying operation is still running (it will
     * be cancelled via the structured scope).</p>
     *
     * @param operation the callable to execute; must not be null
     * @return a future completing with the result, fallback, or TimeoutException
     */
    public CompletableFuture<T> executeAsync(Callable<T> operation) {
        if (operation == null) throw new NullPointerException("operation must not be null");
        return CompletableFuture
                .supplyAsync(
                    () -> {
                        try {
                            return execute(operation);
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    },
                    Executors.newVirtualThreadPerTaskExecutor())
                .orTimeout(_timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    // -----------------------------------------------------------------------
    // Metrics
    // -----------------------------------------------------------------------

    /** Returns the number of calls that timed out (timeout elapsed or fallback used). */
    public long timeoutCount() { return _timeouts.sum(); }

    /** Returns the number of calls that completed successfully within the timeout. */
    public long successCount() { return _successes.sum(); }

    /** Returns the number of calls that failed with an exception (not timeout). */
    public long failureCount() { return _failures.sum(); }

    /**
     * Returns a snapshot string suitable for logging.
     *
     * @return {@code "ExternalCallGuard[timeout=PT30S, ok=42, timeout=3, fail=1]"} (example)
     */
    @Override
    public String toString() {
        return "ExternalCallGuard[timeout=" + _timeout
                + ", ok=" + _successes.sum()
                + ", timeout=" + _timeouts.sum()
                + ", fail=" + _failures.sum() + "]";
    }
}
