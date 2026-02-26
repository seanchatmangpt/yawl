/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import java.io.IOException;

/**
 * Functional interface representing the boundary between the GRPO sampling loop
 * and the underlying LLM backend (e.g., Ollama, OpenAI).
 *
 * <p>The single method accepts a fully-assembled prompt and a sampling temperature,
 * and returns the raw text response. All prompt engineering (ProMoAI strategies,
 * knowledge injection, few-shot examples) is done by the caller before invoking
 * this gateway.
 *
 * <p>The interface exists to enable deterministic unit testing of the retry and
 * self-correction logic in {@link OllamaCandidateSampler} without requiring a
 * live Ollama server or HTTP mocking framework.
 */
@FunctionalInterface
public interface LlmGateway {

    /**
     * Sends the given prompt to the LLM and returns the text response.
     *
     * @param prompt      the fully-assembled generation or correction prompt
     * @param temperature sampling temperature in [0.0, 2.0]; higher values increase diversity
     * @return the raw text response from the LLM (may be a valid or invalid POWL expression)
     * @throws IOException if the LLM call fails (network error, HTTP error, timeout)
     */
    String send(String prompt, double temperature) throws IOException;
}
