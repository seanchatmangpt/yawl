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

import java.util.List;
import java.util.Map;

/**
 * Immutable LLM completion request.
 *
 * @param prompt       the prompt to complete
 * @param messages     chat-style messages (alternative to prompt)
 * @param maxTokens    maximum tokens to generate
 * @param temperature  sampling temperature (0-2)
 * @param topP         nucleus sampling parameter
 * @param stopSequences stop generation at these sequences
 * @param metadata     additional provider-specific metadata
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record LlmRequest(
    @Nullable String prompt,
    @Nullable List<ChatMessage> messages,
    int maxTokens,
    double temperature,
    double topP,
    List<String> stopSequences,
    Map<String, Object> metadata
) {
    public LlmRequest {
        if (prompt == null && (messages == null || messages.isEmpty())) {
            throw new IllegalArgumentException("Either prompt or messages must be provided");
        }
        messages = messages != null ? List.copyOf(messages) : List.of();
        stopSequences = stopSequences != null ? List.copyOf(stopSequences) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String prompt;
        private List<ChatMessage> messages;
        private int maxTokens = 2048;
        private double temperature = 0.7;
        private double topP = 1.0;
        private List<String> stopSequences;
        private Map<String, Object> metadata;

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder addMessage(ChatMessage.Role role, String content) {
            if (this.messages == null) {
                this.messages = new java.util.ArrayList<>();
            }
            this.messages.add(new ChatMessage(role, content));
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(double topP) {
            this.topP = topP;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public LlmRequest build() {
            return new LlmRequest(prompt, messages, maxTokens, temperature, topP, stopSequences, metadata);
        }
    }
}
