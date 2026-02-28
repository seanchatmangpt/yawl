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

package org.yawlfoundation.yawl.dspy;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable result of executing a DSPy program.
 *
 * <p>Contains the output dictionary (Python dict → Java Map conversion),
 * optional execution trace for debugging, and metrics for observability.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * DspyExecutionResult result = bridge.execute(program, inputs);
 *
 * // Output contains DSPy module's return value
 * Map<String, Object> output = result.output();
 * String sentiment = (String) output.get("sentiment");
 * Double confidence = (Double) output.get("confidence");
 *
 * // Trace for debugging (if enabled)
 * if (result.trace() != null) {
 *     System.out.println("Execution trace: " + result.trace());
 * }
 *
 * // Metrics for observability
 * DspyExecutionMetrics metrics = result.metrics();
 * System.out.println("Total time: " + metrics.totalTimeMs() + "ms");
 * System.out.println("Tokens: " + metrics.totalTokens());
 * }</pre>
 *
 * @param output   Python dict converted to Map<String, Object>
 * @param trace    Optional execution trace (for debugging)
 * @param metrics  Observability metrics including timing and token usage
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record DspyExecutionResult(
        @JsonProperty("output")
        Map<String, Object> output,

        @JsonProperty("trace")
        @Nullable String trace,

        @JsonProperty("metrics")
        DspyExecutionMetrics metrics
) {
    /**
     * Compact constructor with validation.
     */
    public DspyExecutionResult {
        Objects.requireNonNull(output, "Output map must not be null");
        Objects.requireNonNull(metrics, "Metrics must not be null");
    }

    /**
     * Creates a new builder for DspyExecutionResult.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DspyExecutionResult with fluent API.
     */
    public static final class Builder {
        private Map<String, Object> output;
        private @Nullable String trace;
        private DspyExecutionMetrics metrics;

        /**
         * Sets the output dictionary.
         */
        public Builder output(Map<String, Object> map) {
            this.output = Objects.requireNonNull(map, "output must not be null");
            return this;
        }

        /**
         * Sets the execution trace (optional).
         */
        public Builder trace(@Nullable String trace) {
            this.trace = trace;
            return this;
        }

        /**
         * Sets the metrics.
         */
        public Builder metrics(DspyExecutionMetrics metrics) {
            this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
            return this;
        }

        /**
         * Builds the DspyExecutionResult.
         *
         * @throws IllegalStateException if output or metrics is not set
         */
        public DspyExecutionResult build() {
            if (output == null) {
                throw new IllegalStateException("Output must be set");
            }
            if (metrics == null) {
                throw new IllegalStateException("Metrics must be set");
            }
            return new DspyExecutionResult(output, trace, metrics);
        }
    }

    /**
     * Returns the value associated with a key in the output map.
     *
     * <p>Convenience method for accessing output values with explicit null handling.</p>
     *
     * @param key the output key
     * @return the value, or null if key is not present
     */
    public @Nullable Object getValue(String key) {
        return output.get(key);
    }

    /**
     * Returns the value associated with a key, casting to the given type.
     *
     * <p>Convenience method for type-safe access to output values.</p>
     *
     * @param <T>  the expected type
     * @param key  the output key
     * @param type the type to cast to
     * @return the value cast to type, or null if key is not present or type mismatch
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getValue(String key, Class<T> type) {
        Object value = output.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Returns true if the output map contains the specified key.
     */
    public boolean hasKey(String key) {
        return output.containsKey(key);
    }

    /**
     * Returns the number of keys in the output map.
     */
    public int outputSize() {
        return output.size();
    }
}
