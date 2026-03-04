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
 * Client interface for LLM providers.
 *
 * <p>Abstraction over LLM backends (OpenAI, Anthropic, local models via Ollama, etc.)
 * used by DSPy modules for inference. Implementations handle:
 * <ul>
 *   <li>API authentication and connection management</li>
 *   <li>Request formatting and response parsing</li>
 *   <li>Token counting and usage tracking</li>
 *   <li>Retry logic and error handling</li>
 * </ul>
 *
 * <h2>Example implementation:</h2>
 * {@snippet :
 * public class OpenAiClient implements LlmClient {
 *     private final String apiKey;
 *     private final String model;
 *
 *     @Override
 *     public LlmResponse complete(LlmRequest request) {
 *         // Call OpenAI API
 *         return callOpenAiApi(request);
 *     }
 * }
 * }
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface LlmClient extends AutoCloseable {

    /**
     * Complete a prompt and return the LLM's response.
     *
     * @param request the completion request
     * @return the LLM response
     * @throws LlmException if the request fails
     */
    LlmResponse complete(LlmRequest request);

    /**
     * Complete a simple string prompt.
     *
     * @param prompt the prompt string
     * @return the completion text
     * @throws LlmException if the request fails
     */
    default String complete(String prompt) {
        LlmRequest request = LlmRequest.builder()
            .prompt(prompt)
            .build();
        return complete(request).text();
    }

    /**
     * Get the model identifier for this client.
     */
    String modelId();

    /**
     * Count tokens in the given text using the model's tokenizer.
     */
    int countTokens(String text);

    /**
     * Get maximum context length for this model.
     */
    int maxContextLength();

    /**
     * Check if the client is healthy and ready.
     */
    default boolean isHealthy() {
        return true;
    }

    @Override
    default void close() {
        // Default: no-op
    }
}
