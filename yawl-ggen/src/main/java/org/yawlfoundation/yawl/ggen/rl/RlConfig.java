/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

/**
 * Configuration for the GRPO RL generation engine.
 *
 * @param k              number of candidate POWL models to sample (default 4, as per GRPO)
 * @param stage          which curriculum stage to use for the reward function
 * @param maxValidations maximum validation iterations per candidate (1-10)
 * @param ollamaBaseUrl  Ollama API base URL for candidate generation
 * @param ollamaModel    Ollama model name for generation
 * @param timeoutSecs    HTTP timeout in seconds for Ollama calls
 */
public record RlConfig(
    int k,
    CurriculumStage stage,
    int maxValidations,
    String ollamaBaseUrl,
    String ollamaModel,
    int timeoutSecs
) {
    public RlConfig {
        if (k < 1 || k > 16) throw new IllegalArgumentException("k must be 1-16, got: " + k);
        if (maxValidations < 1 || maxValidations > 10)
            throw new IllegalArgumentException("maxValidations must be 1-10, got: " + maxValidations);
        java.util.Objects.requireNonNull(stage, "stage");
        if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank())
            throw new IllegalArgumentException("ollamaBaseUrl must not be blank");
        if (ollamaModel == null || ollamaModel.isBlank())
            throw new IllegalArgumentException("ollamaModel must not be blank");
        if (timeoutSecs <= 0) throw new IllegalArgumentException("timeoutSecs must be positive");
    }

    /** Default config: k=4, Stage A, qwen2.5-coder model, reads OLLAMA_BASE_URL from env */
    public static RlConfig defaults() {
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5-coder");
        return new RlConfig(4, CurriculumStage.VALIDITY_GAP, 3, baseUrl, model, 60);
    }
}
