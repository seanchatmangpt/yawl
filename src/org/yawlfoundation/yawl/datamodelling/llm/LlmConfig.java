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

package org.yawlfoundation.yawl.datamodelling.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Configuration for LLM-enhanced schema refinement in the data-modelling pipeline.
 *
 * <p>Supports both online (Ollama API) and offline (llama.cpp) modes with configurable
 * model selection, temperature, token limits, and timeout settings.</p>
 *
 * <p>Offline-first design: defaults to llama.cpp with automatic fallback to Ollama
 * if offline mode unavailable.</p>
 *
 * <h2>Example usage</h2>
 * <pre>{@code
 * LlmConfig config = LlmConfig.builder()
 *     .mode(LlmMode.OFFLINE)
 *     .model("llama2-7b")
 *     .temperature(0.7)
 *     .maxTokens(2048)
 *     .timeoutSeconds(30)
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LlmConfig {

    /**
     * Enumeration of LLM runtime modes.
     */
    public enum LlmMode {
        /** Offline mode via llama.cpp or compatible local inference server. */
        OFFLINE("offline"),
        /** Online mode via Ollama HTTP API. */
        ONLINE("online");

        private final String value;

        LlmMode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        /**
         * Parses a mode string (case-insensitive).
         *
         * @param value the mode value; must not be null
         * @return the enum constant; never null
         * @throws IllegalArgumentException if value doesn't match a mode
         */
        public static LlmMode fromValue(String value) {
            if (value == null) {
                throw new IllegalArgumentException("mode value must not be null");
            }
            for (LlmMode mode : values()) {
                if (mode.value.equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown LLM mode: " + value);
        }
    }

    /**
     * Enumeration of supported LLM models.
     */
    public enum ModelType {
        /** Ollama-compatible model: qwen2.5-coder. */
        QWEN_2_5_CODER("qwen2.5-coder"),
        /** Ollama-compatible model: llama2 (7B parameters). */
        LLAMA2_7B("llama2-7b"),
        /** Ollama-compatible model: llama2 (13B parameters). */
        LLAMA2_13B("llama2-13b"),
        /** Ollama-compatible model: mistral (7B parameters). */
        MISTRAL_7B("mistral-7b"),
        /** OpenAI compatible model: Claude. */
        CLAUDE("claude-opus-4-6"),
        /** Custom model name (caller-supplied). */
        CUSTOM(null);

        private final String modelId;

        ModelType(String modelId) {
            this.modelId = modelId;
        }

        public String getModelId() {
            return modelId;
        }

        /**
         * Parses a model type from its ID string.
         *
         * @param modelId the model identifier; must not be null
         * @return the enum constant or CUSTOM if not recognized
         */
        public static ModelType fromModelId(String modelId) {
            if (modelId == null) {
                return CUSTOM;
            }
            for (ModelType type : values()) {
                if (type.modelId != null && type.modelId.equals(modelId)) {
                    return type;
                }
            }
            return CUSTOM;
        }
    }

    @JsonProperty("mode")
    private LlmMode mode; // Default: OFFLINE

    @JsonProperty("model")
    private String model; // Model name or ID

    @JsonProperty("modelType")
    private ModelType modelType; // Predefined model types

    @JsonProperty("temperature")
    private Double temperature; // Sampling temperature [0.0, 2.0]; default 0.7

    @JsonProperty("maxTokens")
    private Integer maxTokens; // Maximum output tokens; default 2048

    @JsonProperty("timeoutSeconds")
    private Integer timeoutSeconds; // HTTP timeout for online mode; default 30

    @JsonProperty("baseUrl")
    private String baseUrl; // Ollama/llama.cpp base URL for online/offline modes

    @JsonProperty("enableFallback")
    private Boolean enableFallback; // Fallback to online if offline fails; default true

    @JsonProperty("contextFile")
    private String contextFile; // Optional path to documentation/context file

    @JsonProperty("systemPrompt")
    private String systemPrompt; // Custom system prompt for LLM

    // ── Constructors ──────────────────────────────────────────────────────────

    public LlmConfig() {
    }

    private LlmConfig(Builder builder) {
        this.mode = builder.mode;
        this.model = builder.model;
        this.modelType = builder.modelType;
        this.temperature = builder.temperature;
        this.maxTokens = builder.maxTokens;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.baseUrl = builder.baseUrl;
        this.enableFallback = builder.enableFallback;
        this.contextFile = builder.contextFile;
        this.systemPrompt = builder.systemPrompt;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public LlmMode getMode() {
        return mode != null ? mode : LlmMode.OFFLINE;
    }

    public String getModel() {
        return model;
    }

    public ModelType getModelType() {
        return modelType;
    }

    public Double getTemperature() {
        return temperature != null ? temperature : 0.7;
    }

    public Integer getMaxTokens() {
        return maxTokens != null ? maxTokens : 2048;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds != null ? timeoutSeconds : 30;
    }

    public String getBaseUrl() {
        return baseUrl != null ? baseUrl : "http://localhost:11434";
    }

    public Boolean getEnableFallback() {
        return enableFallback != null ? enableFallback : true;
    }

    public String getContextFile() {
        return contextFile;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * Validates configuration consistency.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (getMode() == null) {
            throw new IllegalArgumentException("LLM mode must not be null");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be null or blank");
        }
        if (getTemperature() < 0.0 || getTemperature() > 2.0) {
            throw new IllegalArgumentException(
                    "temperature must be in [0.0, 2.0], got: " + getTemperature());
        }
        if (getMaxTokens() <= 0) {
            throw new IllegalArgumentException(
                    "maxTokens must be positive, got: " + getMaxTokens());
        }
        if (getTimeoutSeconds() <= 0) {
            throw new IllegalArgumentException(
                    "timeoutSeconds must be positive, got: " + getTimeoutSeconds());
        }
        String url = getBaseUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be null or blank");
        }
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /**
     * Creates a new builder for LlmConfig.
     *
     * @return a new builder with defaults set
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for immutable LlmConfig instances.
     */
    public static final class Builder {
        private LlmMode mode = LlmMode.OFFLINE;
        private String model = "qwen2.5-coder";
        private ModelType modelType = ModelType.QWEN_2_5_CODER;
        private Double temperature = 0.7;
        private Integer maxTokens = 2048;
        private Integer timeoutSeconds = 30;
        private String baseUrl = "http://localhost:11434";
        private Boolean enableFallback = true;
        private String contextFile;
        private String systemPrompt;

        public Builder mode(LlmMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode must not be null");
            return this;
        }

        public Builder model(String model) {
            this.model = Objects.requireNonNull(model, "model must not be null");
            this.modelType = ModelType.fromModelId(model);
            return this;
        }

        public Builder modelType(ModelType modelType) {
            this.modelType = Objects.requireNonNull(modelType, "modelType must not be null");
            return this;
        }

        public Builder temperature(Double temperature) {
            if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
                throw new IllegalArgumentException(
                        "temperature must be in [0.0, 2.0], got: " + temperature);
            }
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            if (maxTokens != null && maxTokens <= 0) {
                throw new IllegalArgumentException("maxTokens must be positive, got: " + maxTokens);
            }
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder timeoutSeconds(Integer timeoutSeconds) {
            if (timeoutSeconds != null && timeoutSeconds <= 0) {
                throw new IllegalArgumentException(
                        "timeoutSeconds must be positive, got: " + timeoutSeconds);
            }
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
            return this;
        }

        public Builder enableFallback(Boolean enableFallback) {
            this.enableFallback = enableFallback;
            return this;
        }

        public Builder contextFile(String contextFile) {
            this.contextFile = contextFile;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public LlmConfig build() {
            LlmConfig config = new LlmConfig(this);
            config.validate();
            return config;
        }
    }

    // ── Equality & Hashing ────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LlmConfig llmConfig = (LlmConfig) o;
        return Objects.equals(mode, llmConfig.mode)
                && Objects.equals(model, llmConfig.model)
                && Objects.equals(modelType, llmConfig.modelType)
                && Objects.equals(temperature, llmConfig.temperature)
                && Objects.equals(maxTokens, llmConfig.maxTokens)
                && Objects.equals(timeoutSeconds, llmConfig.timeoutSeconds)
                && Objects.equals(baseUrl, llmConfig.baseUrl)
                && Objects.equals(enableFallback, llmConfig.enableFallback)
                && Objects.equals(contextFile, llmConfig.contextFile)
                && Objects.equals(systemPrompt, llmConfig.systemPrompt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, model, modelType, temperature, maxTokens, timeoutSeconds,
                baseUrl, enableFallback, contextFile, systemPrompt);
    }

    @Override
    public String toString() {
        return "LlmConfig{"
                + "mode=" + mode
                + ", model='" + model + '\''
                + ", modelType=" + modelType
                + ", temperature=" + temperature
                + ", maxTokens=" + maxTokens
                + ", timeoutSeconds=" + timeoutSeconds
                + ", baseUrl='" + baseUrl + '\''
                + ", enableFallback=" + enableFallback
                + ", contextFile='" + contextFile + '\''
                + ", systemPrompt='" + (systemPrompt != null ? "[set]" : "null") + '\''
                + '}';
    }
}
