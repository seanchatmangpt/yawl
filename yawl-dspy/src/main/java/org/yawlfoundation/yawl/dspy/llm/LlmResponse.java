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

package org.yawlfoundation.yawl.dspy.llm;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable LLM completion response.
 *
 * @param text            the generated text
 * @param inputTokens     tokens in the prompt
 * @param outputTokens    tokens in the completion
 * @param modelId         the model used
 * @param finishReason    why generation stopped
 * @param latencyMs       request latency in milliseconds
 * @param metadata        additional provider-specific metadata
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record LlmResponse(
    String text,
    int inputTokens,
    int outputTokens,
    String modelId,
    FinishReason finishReason,
    long latencyMs,
    Map<String, Object> metadata
) {
    public LlmResponse {
        if (text == null) text = "";
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    public static final class Builder {
        private String text;
        private int inputTokens;
        private int outputTokens;
        private String modelId;
        private FinishReason finishReason = FinishReason.STOP;
        private long latencyMs;
        private Map<String, Object> metadata;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder inputTokens(int inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(int outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public Builder latencyMs(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public LlmResponse build() {
            return new LlmResponse(text, inputTokens, outputTokens, modelId, finishReason, latencyMs, metadata);
        }
    }

    /**
     * Why the LLM stopped generating.
     */
    public enum FinishReason {
        STOP,
        MAX_TOKENS,
        STOP_SEQUENCE,
        CONTENT_FILTER,
        ERROR
    }
}
