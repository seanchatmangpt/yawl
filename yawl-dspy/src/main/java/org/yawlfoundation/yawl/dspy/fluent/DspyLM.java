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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fluent builder for DSPy language model configuration.
 *
 * <p>Mirrors Python: {@code dspy.LM(model="groq/llama-3.3-70b-versatile", api_key="...")}
 *
 * <h2>Python Equivalent:</h2>
 * <pre>{@code
 * # Python
 * lm = dspy.LM(
 *     model="groq/llama-3.3-70b-versatile",
 *     api_key=os.environ["GROQ_API_KEY"],
 *     temperature=0.0
 * )
 * dspy.configure(lm=lm)
 * }</pre>
 *
 * <h2>Java Fluent API:</h2>
 * <pre>{@code
 * // Java
 * Dspy.configure(lm -> lm
 *     .model("groq/llama-3.3-70b-versatile")
 *     .apiKey(System.getenv("GROQ_API_KEY"))
 *     .temperature(0.0));
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspyLM {

    private final String model;
    private final @Nullable String apiKey;
    private final @Nullable String apiBase;
    private final @Nullable Double temperature;
    private final @Nullable Integer maxTokens;
    private final @Nullable Integer numRetries;
    private final Map<String, Object> extraConfig;

    private DspyLM(Builder builder) {
        this.model = Objects.requireNonNull(builder.model, "model is required");
        this.apiKey = builder.apiKey;
        this.apiBase = builder.apiBase;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.numRetries = builder.numRetries;
        this.extraConfig = Map.copyOf(builder.extraConfig);
    }

    /**
     * Create a new builder for DspyLM.
     *
     * <p>Python: {@code dspy.LM(model="...")}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a DspyLM with just a model name (API key from environment).
     *
     * <p>Python: {@code dspy.LM("groq/llama-3.3-70b-versatile")}
     */
    public static DspyLM of(String model) {
        return builder().model(model).build();
    }

    /**
     * Create a DspyLM with model and API key.
     *
     * <p>Python: {@code dspy.LM("groq/llama-3.3-70b-versatile", api_key="...")}
     */
    public static DspyLM of(String model, String apiKey) {
        return builder().model(model).apiKey(apiKey).build();
    }

    /**
     * The model identifier (e.g., "groq/llama-3.3-70b-versatile").
     */
    public String model() {
        return model;
    }

    /**
     * The API key (optional if set via environment variable).
     */
    public @Nullable String apiKey() {
        return apiKey;
    }

    /**
     * The API base URL (optional, for custom endpoints).
     */
    public @Nullable String apiBase() {
        return apiBase;
    }

    /**
     * The temperature for generation (0.0 = deterministic).
     */
    public @Nullable Double temperature() {
        return temperature;
    }

    /**
     * Maximum tokens in the response.
     */
    public @Nullable Integer maxTokens() {
        return maxTokens;
    }

    /**
     * Number of retries on failure.
     */
    public @Nullable Integer numRetries() {
        return numRetries;
    }

    /**
     * Extra configuration options.
     */
    public Map<String, Object> extraConfig() {
        return extraConfig;
    }

    /**
     * Get the provider from the model string (e.g., "groq" from "groq/llama-3.3-70b").
     */
    public String provider() {
        int slash = model.indexOf('/');
        return slash > 0 ? model.substring(0, slash) : "openai";
    }

    /**
     * Get the model name without provider prefix.
     */
    public String modelName() {
        int slash = model.indexOf('/');
        return slash > 0 ? model.substring(slash + 1) : model;
    }

    /**
     * Convert to map for serialization to Python/Erlang.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("model", model);
        if (apiKey != null) map.put("api_key", apiKey);
        if (apiBase != null) map.put("api_base", apiBase);
        if (temperature != null) map.put("temperature", temperature);
        if (maxTokens != null) map.put("max_tokens", maxTokens);
        if (numRetries != null) map.put("num_retries", numRetries);
        map.putAll(extraConfig);
        return map;
    }

    // ── Factory methods for common providers ─────────────────────────────────────

    /**
     * Create a Groq LM with Llama 3.3 70B.
     *
     * <p>Python: {@code dspy.LM("groq/llama-3.3-70b-versatile", api_key=os.environ["GROQ_API_KEY"])}
     */
    public static DspyLM groq() {
        return builder()
            .model("groq/llama-3.3-70b-versatile")
            .apiKey(System.getenv("GROQ_API_KEY"))
            .temperature(0.0)
            .build();
    }

    /**
     * Create an OpenAI LM with GPT-4.
     *
     * <p>Python: {@code dspy.LM("openai/gpt-4", api_key=os.environ["OPENAI_API_KEY"])}
     */
    public static DspyLM openai() {
        return builder()
            .model("openai/gpt-4")
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .temperature(0.0)
            .build();
    }

    /**
     * Create an Anthropic LM with Claude 3 Opus.
     *
     * <p>Python: {@code dspy.LM("anthropic/claude-3-opus-20240229", api_key=os.environ["ANTHROPIC_API_KEY"])}
     */
    public static DspyLM anthropic() {
        return builder()
            .model("anthropic/claude-3-opus-20240229")
            .apiKey(System.getenv("ANTHROPIC_API_KEY"))
            .temperature(0.0)
            .build();
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static final class Builder {
        private String model;
        private @Nullable String apiKey;
        private @Nullable String apiBase;
        private @Nullable Double temperature;
        private @Nullable Integer maxTokens;
        private @Nullable Integer numRetries;
        private final Map<String, Object> extraConfig = new HashMap<>();

        private Builder() {}

        /**
         * Set the model identifier.
         *
         * <p>Format: "provider/model-name" (e.g., "groq/llama-3.3-70b-versatile")
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * Set the API key.
         *
         * <p>Python: {@code api_key="..."}
         */
        public Builder apiKey(@Nullable String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Set the API base URL.
         *
         * <p>Python: {@code api_base="https://..."}
         */
        public Builder apiBase(@Nullable String apiBase) {
            this.apiBase = apiBase;
            return this;
        }

        /**
         * Set the temperature for generation.
         *
         * <p>Python: {@code temperature=0.0}
         */
        public Builder temperature(@Nullable Double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * Set the maximum tokens in the response.
         *
         * <p>Python: {@code max_tokens=256}
         */
        public Builder maxTokens(@Nullable Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Set the number of retries on failure.
         *
         * <p>Python: {@code num_retries=3}
         */
        public Builder numRetries(@Nullable Integer numRetries) {
            this.numRetries = numRetries;
            return this;
        }

        /**
         * Add extra configuration option.
         */
        public Builder extra(String key, Object value) {
            this.extraConfig.put(key, value);
            return this;
        }

        public DspyLM build() {
            return new DspyLM(this);
        }
    }
}
