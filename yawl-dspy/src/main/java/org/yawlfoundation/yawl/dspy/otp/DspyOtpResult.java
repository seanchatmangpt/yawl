/*
 * Copyright (c) 2025 YAWL Foundation. All rights reserved.
 * This source code is licensed under the Apache License 2.0.
 */
package org.yawlfoundation.yawl.dspy.otp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of a DSPy program execution via OTP.
 *
 * Extends {@link org.yawlfoundation.yawl.dspy.DspyExecutionResult} for
 * consistency with synchronous DSPy execution.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * DspyOtpResult result = bridge.program("sentiment")
 *     .input("text", userInput)
 *     .execute()
 *     .await(5, TimeUnit.SECONDS);
 *
 * String sentiment = result.getOutput("sentiment");
 * long duration = result.getDurationMillis();
 * }</pre>
 */
@NullMarked
public final class DspyOtpResult {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Object> outputs;
    private final DspyExecutionMetrics metrics;
    private final long durationMillis;
    private final String executionId;

    /**
     * Create result from execution outputs.
     *
     * @param outputs the output map from DSPy program (not null)
     * @param metrics execution metrics (not null)
     * @param durationMillis total roundtrip time in milliseconds
     * @param executionId unique execution identifier
     */
    public DspyOtpResult(Map<String, Object> outputs, DspyExecutionMetrics metrics,
                         long durationMillis, String executionId) {
        this.outputs = Collections.unmodifiableMap(
            new HashMap<>(Objects.requireNonNull(outputs, "outputs"))
        );
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.durationMillis = durationMillis;
        this.executionId = Objects.requireNonNull(executionId, "executionId");
    }

    /**
     * Get a named output value from the DSPy program result.
     *
     * @param key output field name (e.g., "sentiment", "entities")
     * @return the output value, or null if key not present
     * @throws DspyOtpException if key does not exist in result
     */
    public @Nullable Object getOutput(String key) {
        if (!outputs.containsKey(key)) {
            throw new DspyOtpException(
                "Output key '" + key + "' not found in result. Available: " + outputs.keySet()
            );
        }
        return outputs.get(key);
    }

    /**
     * Get typed output with automatic casting.
     *
     * @param key output field name
     * @param type expected class type
     * @return the casted output value
     * @throws ClassCastException if output cannot be cast to type
     */
    @SuppressWarnings("unchecked")
    public <T> T getOutput(String key, Class<T> type) {
        Object value = getOutput(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                "Output '" + key + "' is " + value.getClass().getSimpleName() +
                ", expected " + type.getSimpleName()
            );
        }
        return (T) value;
    }

    /**
     * Get all outputs as an unmodifiable map.
     *
     * @return read-only outputs map
     */
    public Map<String, Object> asMap() {
        return outputs;
    }

    /**
     * Serialize result to JSON string.
     *
     * @return JSON representation
     */
    public String asJson() {
        try {
            return mapper.writeValueAsString(outputs);
        } catch (Exception e) {
            throw new DspyOtpException("Failed to serialize result to JSON", e);
        }
    }

    /**
     * Get execution metrics (accuracy, F1, latency, etc.).
     *
     * @return metrics object
     */
    public DspyExecutionMetrics getMetrics() {
        return metrics;
    }

    /**
     * Get total roundtrip duration in milliseconds.
     * Includes Java serialization + OTP network roundtrip + Python execution + deserialization.
     *
     * @return duration in ms
     */
    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * Get unique execution identifier assigned by the OTP bridge.
     *
     * @return execution ID
     */
    public String getExecutionId() {
        return executionId;
    }

    @Override
    public String toString() {
        return "DspyOtpResult{" +
                "executionId='" + executionId + '\'' +
                ", outputs=" + outputs +
                ", durationMillis=" + durationMillis +
                '}';
    }
}
