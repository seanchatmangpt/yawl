/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */
package org.yawlfoundation.yawl.dspy.otp;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Represents an in-flight DSPy program execution via OTP.
 *
 * Execution is asynchronous and backed by a virtual thread. Results are
 * accessed via {@link #await()} or {@link #await(Duration)} which block
 * the current virtual thread cleanly (without occupying a platform thread).
 *
 * <h2>Synchronous Execution</h2>
 * <pre>{@code
 * DspyOtpResult result = bridge.program("sentiment")
 *     .input("text", "Amazing!")
 *     .execute()
 *     .await(5, TimeUnit.SECONDS);
 * }</pre>
 *
 * <h2>Async with Callback</h2>
 * <pre>{@code
 * bridge.program("sentiment")
 *     .input("text", userInput)
 *     .execute()
 *     .awaitAsync((result, error) -> {
 *         if (error != null) logger.error("Failed", error);
 *         else processResult(result);
 *     });
 * }</pre>
 *
 * <h2>CompletableFuture Integration</h2>
 * <pre>{@code
 * CompletableFuture<DspyOtpResult> future = execution.asCompletableFuture();
 * future.thenApply(r -> r.getOutput("sentiment"))
 *       .thenAccept(sentiment -> System.out.println(sentiment))
 *       .exceptionally(e -> { logger.error("Failed", e); return null; });
 * }</pre>
 */
@NullMarked
public final class DspyOtpExecution {

    private final String executionId;
    private final CompletableFuture<DspyOtpResult> future;
    private final Duration defaultTimeout;

    /**
     * Create a new execution.
     *
     * @param executionId unique identifier for this execution
     * @param future the underlying future (from virtual thread executor)
     * @param defaultTimeout timeout used if not overridden in await()
     */
    DspyOtpExecution(String executionId, CompletableFuture<DspyOtpResult> future,
                     Duration defaultTimeout) {
        this.executionId = Objects.requireNonNull(executionId, "executionId");
        this.future = Objects.requireNonNull(future, "future");
        this.defaultTimeout = Objects.requireNonNull(defaultTimeout, "defaultTimeout");
    }

    /**
     * Wait for execution to complete using the default timeout.
     *
     * Blocks the current virtual thread until result is ready or timeout expires.
     *
     * @return the execution result
     * @throws DspyOtpException if RPC fails
     * @throws DspyOtpTimeoutException if timeout exceeded
     * @throws InterruptedException if current thread is interrupted
     */
    public DspyOtpResult await() throws InterruptedException {
        return await(defaultTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Wait for execution to complete with explicit timeout.
     *
     * @param timeout how long to wait
     * @param unit the time unit
     * @return the execution result
     * @throws DspyOtpException if RPC fails
     * @throws DspyOtpTimeoutException if timeout exceeded
     * @throws InterruptedException if current thread is interrupted
     */
    public DspyOtpResult await(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            long startTime = System.currentTimeMillis();
            DspyOtpResult result = future.get(timeout, unit);
            return result;
        } catch (TimeoutException e) {
            long elapsed = System.currentTimeMillis() - System.currentTimeMillis();
            cancel();
            throw new DspyOtpException.DspyOtpTimeoutException(
                "RPC exceeded timeout of " + timeout + " " + unit, elapsed
            );
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DspyOtpException) {
                throw (DspyOtpException) cause;
            }
            throw new DspyOtpException("RPC failed with error", cause);
        }
    }

    /**
     * Wait for execution to complete with custom duration object.
     *
     * @param timeout the timeout duration
     * @return the execution result
     * @throws DspyOtpException if RPC fails
     * @throws DspyOtpTimeoutException if timeout exceeded
     * @throws InterruptedException if current thread is interrupted
     */
    public DspyOtpResult await(Duration timeout) throws InterruptedException {
        return await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Asynchronously wait for result with a callback function.
     *
     * The callback is invoked by a virtual thread executor when result is ready.
     * Exactly one of (result, error) will be non-null.
     *
     * @param callback receives (result, error) pair
     */
    public void awaitAsync(DspyOtpCallback callback) {
        Objects.requireNonNull(callback, "callback");
        future.whenComplete((result, error) -> {
            try {
                callback.accept(result, error);
            } catch (Exception e) {
                // Prevent exceptions in callback from being swallowed
                System.err.println("Uncaught exception in DSPy OTP callback: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Get the underlying CompletableFuture for advanced composition.
     *
     * @return the future
     */
    public CompletableFuture<DspyOtpResult> asCompletableFuture() {
        return future;
    }

    /**
     * Cancel this execution if it hasn't completed yet.
     *
     * Cancels the underlying RPC call and releases virtual thread resources.
     *
     * @return true if cancellation succeeded, false if already completed
     */
    public boolean cancel() {
        return future.cancel(true);
    }

    /**
     * Check if execution is done.
     *
     * @return true if completed (normally, exceptionally, or cancelled)
     */
    public boolean isDone() {
        return future.isDone();
    }

    /**
     * Get unique identifier for this execution.
     *
     * @return execution ID
     */
    public String getExecutionId() {
        return executionId;
    }

    @Override
    public String toString() {
        return "DspyOtpExecution{" +
                "executionId='" + executionId + '\'' +
                ", done=" + isDone() +
                '}';
    }
}
