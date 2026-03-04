/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.bridge.router;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents the result of a bridge routing operation.
 * Immutable container for execution outcome with metadata.
 */
public final class RoutingResult {
    private final NativeCall call;
    private final Object result;
    private final Throwable error;
    private final Instant timestamp;
    private final Duration executionTime;
    private final boolean success;

    private RoutingResult(NativeCall call, Object result, Throwable error,
                         Instant timestamp, Duration executionTime) {
        this.call = call;
        this.result = result;
        this.error = error;
        this.timestamp = timestamp;
        this.executionTime = executionTime;
        this.success = error == null;
    }

    /**
     * Creates a successful routing result.
     *
     * @param result execution result
     * @param call the routed call
     * @return successful RoutingResult
     */
    public static RoutingResult success(Object result, NativeCall call) {
        Objects.requireNonNull(result, "Result cannot be null");
        Objects.requireNonNull(call, "Call cannot be null");

        Instant startTime = Instant.now();
        // Simulate execution time for demo
        Duration execTime = Duration.ofMillis(ThreadLocalRandom.current().nextInt(1, 100));
        Instant endTime = startTime.plus(execTime);

        return new RoutingResult(call, result, null, endTime, execTime);
    }

    /**
     * Creates a failed routing result.
     *
     * @param errorMessage error message
     * @param call the failed call
     * @return failed RoutingResult
     */
    public static RoutingResult failure(String errorMessage, NativeCall call) {
        return failure(errorMessage, call, null);
    }

    /**
     * Creates a failed routing result with exception.
     *
     * @param errorMessage error message
     * @param call the failed call
     * @param error underlying exception
     * @return failed RoutingResult
     */
    public static RoutingResult failure(String errorMessage, NativeCall call, Throwable error) {
        Objects.requireNonNull(errorMessage, "Error message cannot be null");
        Objects.requireNonNull(call, "Call cannot be null");

        Instant startTime = Instant.now();
        // Simulate execution time
        Duration execTime = Duration.ofMillis(ThreadLocalRandom.current().nextInt(1, 50));
        Instant endTime = startTime.plus(execTime);

        RoutingResult result = new RoutingResult(call, null,
            new BridgeRoutingException(errorMessage, error), endTime, execTime);
        return result;
    }

    /**
     * Gets the routed call.
     *
     * @return the NativeCall that was routed
     */
    public NativeCall getCall() {
        return call;
    }

    /**
     * Gets the execution result.
     *
     * @return result object, or null if failed
     */
    public Object getResult() {
        return result;
    }

    /**
     * Gets the error, if any.
     *
     * @return Throwable if failed, null if successful
     */
    public Throwable getError() {
        return error;
    }

    /**
     * Gets the timestamp of result completion.
     *
     * @return when the routing completed
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the execution time.
     *
     * @return how long the execution took
     */
    public Duration getExecutionTime() {
        return executionTime;
    }

    /**
     * Checks if the routing was successful.
     *
     * @return true if execution succeeded
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Checks if the routing failed.
     *
     * @return true if execution failed
     */
    public boolean isFailure() {
        return !success;
    }

    /**
     * Gets the error message, if any.
     *
     * @return error message or null if successful
     */
    public String getErrorMessage() {
        return error != null ? error.getMessage() : null;
    }

    /**
     * Maps the result to a new value using a function.
     *
     * @param mapper function to transform the result
     * @param <T> new type of the result
     * @return new RoutingResult with mapped value
     */
    public <T> RoutingResult map(java.util.function.Function<Object, T> mapper) {
        if (isFailure()) {
            return this;
        }

        try {
            T mapped = mapper.apply(result);
            return RoutingResult.success(mapped, call);
        } catch (Exception e) {
            return RoutingResult.failure("Mapping failed: " + e.getMessage(), call, e);
        }
    }

    /**
     * Converts the result to a string representation.
     *
     * @return string representation of the result
     */
    @Override
    public String toString() {
        if (success) {
            return "RoutingResult{" +
                   "call=" + call.toNtriple() +
                   ", result=" + result +
                   ", executionTime=" + executionTime +
                   ", timestamp=" + timestamp +
                   '}';
        } else {
            return "RoutingResult{" +
                   "call=" + call.toNtriple() +
                   ", error=" + error.getMessage() +
                   ", executionTime=" + executionTime +
                   ", timestamp=" + timestamp +
                   '}';
        }
    }
}