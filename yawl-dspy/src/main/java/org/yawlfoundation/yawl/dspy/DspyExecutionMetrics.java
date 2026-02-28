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

import java.time.Instant;

/**
 * Immutable observability metrics for DSPy program execution.
 *
 * <p>Captures timing, token usage, and quality metrics for monitoring and
 * optimization. Used for workflow observability and performance tuning.</p>
 *
 * @param compilationTimeMs      Time to compile DSPy program (ms)
 * @param executionTimeMs        Time to execute compiled program (ms)
 * @param inputTokens            Tokens in LLM input prompt (estimate or actual)
 * @param outputTokens           Tokens in LLM output response (estimate or actual)
 * @param qualityScore           Metric score (0.0-1.0) if optimized; null otherwise
 * @param cacheHit               True if program was loaded from cache
 * @param contextReused          True if execution context was reused from pool
 * @param timestamp              When metrics were captured
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record DspyExecutionMetrics(
        @JsonProperty("compilation_time_ms")
        long compilationTimeMs,

        @JsonProperty("execution_time_ms")
        long executionTimeMs,

        @JsonProperty("input_tokens")
        long inputTokens,

        @JsonProperty("output_tokens")
        long outputTokens,

        @JsonProperty("quality_score")
        @Nullable Double qualityScore,

        @JsonProperty("cache_hit")
        boolean cacheHit,

        @JsonProperty("context_reused")
        boolean contextReused,

        @JsonProperty("timestamp")
        Instant timestamp
) {
    /**
     * Returns total time (compilation + execution) in milliseconds.
     */
    public long totalTimeMs() {
        return compilationTimeMs + executionTimeMs;
    }

    /**
     * Returns total tokens (input + output).
     */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    /**
     * Creates a new builder for DspyExecutionMetrics.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DspyExecutionMetrics.
     */
    public static final class Builder {
        private long compilationTimeMs;
        private long executionTimeMs;
        private long inputTokens;
        private long outputTokens;
        private @Nullable Double qualityScore;
        private boolean cacheHit;
        private boolean contextReused;
        private Instant timestamp = Instant.now();

        /**
         * Sets compilation time in milliseconds.
         */
        public Builder compilationTimeMs(long ms) {
            this.compilationTimeMs = ms;
            return this;
        }

        /**
         * Sets execution time in milliseconds.
         */
        public Builder executionTimeMs(long ms) {
            this.executionTimeMs = ms;
            return this;
        }

        /**
         * Sets input token count.
         */
        public Builder inputTokens(long count) {
            this.inputTokens = count;
            return this;
        }

        /**
         * Sets output token count.
         */
        public Builder outputTokens(long count) {
            this.outputTokens = count;
            return this;
        }

        /**
         * Sets quality score (0.0-1.0).
         */
        public Builder qualityScore(@Nullable Double score) {
            this.qualityScore = score;
            return this;
        }

        /**
         * Sets whether program was cached.
         */
        public Builder cacheHit(boolean hit) {
            this.cacheHit = hit;
            return this;
        }

        /**
         * Sets whether context was reused from pool.
         */
        public Builder contextReused(boolean reused) {
            this.contextReused = reused;
            return this;
        }

        /**
         * Sets timestamp (defaults to Instant.now()).
         */
        public Builder timestamp(Instant instant) {
            this.timestamp = instant;
            return this;
        }

        /**
         * Builds the DspyExecutionMetrics.
         */
        public DspyExecutionMetrics build() {
            return new DspyExecutionMetrics(
                    compilationTimeMs,
                    executionTimeMs,
                    inputTokens,
                    outputTokens,
                    qualityScore,
                    cacheHit,
                    contextReused,
                    timestamp
            );
        }
    }
}
