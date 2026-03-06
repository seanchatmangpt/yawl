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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dspy.fluent;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result from a DSPy prediction.
 *
 * <p>Wraps the output of a DSPy module call with convenient accessors
 * and metadata.
 *
 * <h2>Python Equivalent:</h2>
 * <pre>{@code
 * # Python
 * result = predictor(question="What is YAWL?")
 * answer = result.answer
 * confidence = result.confidence
 * }</pre>
 *
 * <h2>Java Fluent API:</h2>
 * <pre>{@code
 * // Java
 * DspyResult result = predictor.predict("question", "What is YAWL?");
 * String answer = result.get("answer");
 * Double confidence = result.getDouble("confidence");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspyResult {

    private final Map<String, Object> values;
    private final @Nullable String rawOutput;
    private final @Nullable Long latencyMs;
    private final @Nullable Integer totalTokens;
    private final boolean complete;

    private DspyResult(Builder builder) {
        this.values = Map.copyOf(builder.values);
        this.rawOutput = builder.rawOutput;
        this.latencyMs = builder.latencyMs;
        this.totalTokens = builder.totalTokens;
        this.complete = builder.complete;
    }

    /**
     * Create a result from values.
     */
    public static DspyResult of(Map<String, Object> values) {
        return builder().values(values).build();
    }

    /**
     * Create a result from key-value pairs.
     */
    public static DspyResult of(String key, Object value, Object... more) {
        Builder builder = builder().value(key, value);
        for (int i = 0; i < more.length; i += 2) {
            if (i + 1 < more.length) {
                builder.value(more[i].toString(), more[i + 1]);
            }
        }
        return builder.build();
    }

    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get all output values.
     */
    public Map<String, Object> values() {
        return values;
    }

    /**
     * Get a value by key.
     *
     * <p>Python: {@code result.answer}
     */
    public Object get(String key) {
        return values.get(key);
    }

    /**
     * Get a value as string.
     */
    public @Nullable String getString(String key) {
        Object value = values.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Get a value as integer.
     */
    public @Nullable Integer getInteger(String key) {
        Object value = values.get(key);
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * Get a value as double.
     */
    public @Nullable Double getDouble(String key) {
        Object value = values.get(key);
        if (value instanceof Double d) return d;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * Get a value as boolean.
     */
    public @Nullable Boolean getBoolean(String key) {
        Object value = values.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return null;
    }

    /**
     * Get a value as list.
     */
    @SuppressWarnings("unchecked")
    public @Nullable List<Object> getList(String key) {
        Object value = values.get(key);
        if (value instanceof List<?> list) return (List<Object>) list;
        return null;
    }

    /**
     * Get a value as map.
     */
    @SuppressWarnings("unchecked")
    public @Nullable Map<String, Object> getMap(String key) {
        Object value = values.get(key);
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return null;
    }

    /**
     * Get an optional value.
     */
    public Optional<Object> getOptional(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /**
     * Check if a key exists.
     */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /**
     * Raw LLM output (if available).
     */
    public @Nullable String rawOutput() {
        return rawOutput;
    }

    /**
     * Latency in milliseconds (if available).
     */
    public @Nullable Long latencyMs() {
        return latencyMs;
    }

    /**
     * Total tokens used (if available).
     */
    public @Nullable Integer totalTokens() {
        return totalTokens;
    }

    /**
     * Whether all expected outputs are present.
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Get missing output keys (if any).
     */
    public List<String> missingKeys(List<String> expectedKeys) {
        return expectedKeys.stream()
            .filter(k -> !values.containsKey(k))
            .toList();
    }

    @Override
    public String toString() {
        return "DspyResult" + values;
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static final class Builder {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private @Nullable String rawOutput;
        private @Nullable Long latencyMs;
        private @Nullable Integer totalTokens;
        private boolean complete = true;

        private Builder() {}

        /**
         * Add a value.
         */
        public Builder value(String key, Object value) {
            this.values.put(key, value);
            return this;
        }

        /**
         * Add all values from a map.
         */
        public Builder values(Map<String, Object> values) {
            this.values.putAll(values);
            return this;
        }

        /**
         * Set raw LLM output.
         */
        public Builder rawOutput(@Nullable String rawOutput) {
            this.rawOutput = rawOutput;
            return this;
        }

        /**
         * Set latency in milliseconds.
         */
        public Builder latencyMs(@Nullable Long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        /**
         * Set total tokens used.
         */
        public Builder totalTokens(@Nullable Integer totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        /**
         * Set whether result is complete.
         */
        public Builder complete(boolean complete) {
            this.complete = complete;
            return this;
        }

        public DspyResult build() {
            return new DspyResult(this);
        }
    }
}
