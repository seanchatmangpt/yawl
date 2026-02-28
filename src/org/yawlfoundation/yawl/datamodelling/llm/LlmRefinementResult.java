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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Result of LLM-enhanced schema refinement.
 *
 * <p>Contains the refined schema, LLM confidence score, detailed suggestions,
 * and metadata about the refinement process.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class LlmRefinementResult {

    @JsonProperty("refinedSchema")
    private String refinedSchema; // The LLM-refined schema JSON/YAML

    @JsonProperty("confidence")
    private Double confidence; // LLM confidence score [0.0, 1.0]

    @JsonProperty("suggestions")
    private List<String> suggestions; // Detailed suggestions from LLM

    @JsonProperty("applied")
    private List<String> applied; // Which suggestions were applied

    @JsonProperty("skipped")
    private List<String> skipped; // Which suggestions were not applied

    @JsonProperty("fallbackUsed")
    private Boolean fallbackUsed; // Whether fallback mode was used (offline->online)

    @JsonProperty("modelUsed")
    private String modelUsed; // Which model was actually used

    @JsonProperty("executionTimeMs")
    private Long executionTimeMs; // Total execution time

    @JsonProperty("rawLlmResponse")
    private String rawLlmResponse; // Raw response from LLM (for debugging)

    @JsonProperty("errorMessage")
    private String errorMessage; // Error message if refinement failed

    // ── Constructors ──────────────────────────────────────────────────────────

    public LlmRefinementResult() {
        this.suggestions = new ArrayList<>();
        this.applied = new ArrayList<>();
        this.skipped = new ArrayList<>();
        this.fallbackUsed = false;
    }

    private LlmRefinementResult(Builder builder) {
        this.refinedSchema = builder.refinedSchema;
        this.confidence = builder.confidence;
        this.suggestions = builder.suggestions;
        this.applied = builder.applied;
        this.skipped = builder.skipped;
        this.fallbackUsed = builder.fallbackUsed;
        this.modelUsed = builder.modelUsed;
        this.executionTimeMs = builder.executionTimeMs;
        this.rawLlmResponse = builder.rawLlmResponse;
        this.errorMessage = builder.errorMessage;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getRefinedSchema() {
        return refinedSchema;
    }

    public Double getConfidence() {
        return confidence;
    }

    public List<String> getSuggestions() {
        return suggestions != null ? suggestions : new ArrayList<>();
    }

    public List<String> getApplied() {
        return applied != null ? applied : new ArrayList<>();
    }

    public List<String> getSkipped() {
        return skipped != null ? skipped : new ArrayList<>();
    }

    public Boolean getFallbackUsed() {
        return fallbackUsed != null ? fallbackUsed : false;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public String getRawLlmResponse() {
        return rawLlmResponse;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns true if the refinement succeeded without errors.
     *
     * @return true if refinedSchema is present and errorMessage is null
     */
    public boolean isSuccess() {
        return refinedSchema != null && !refinedSchema.isBlank() && errorMessage == null;
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /**
     * Creates a new builder for LlmRefinementResult.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for immutable LlmRefinementResult instances.
     */
    public static final class Builder {
        private String refinedSchema;
        private Double confidence;
        private List<String> suggestions = new ArrayList<>();
        private List<String> applied = new ArrayList<>();
        private List<String> skipped = new ArrayList<>();
        private Boolean fallbackUsed = false;
        private String modelUsed;
        private Long executionTimeMs;
        private String rawLlmResponse;
        private String errorMessage;

        public Builder refinedSchema(String refinedSchema) {
            this.refinedSchema = refinedSchema;
            return this;
        }

        public Builder confidence(Double confidence) {
            if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
                throw new IllegalArgumentException(
                        "confidence must be in [0.0, 1.0], got: " + confidence);
            }
            this.confidence = confidence;
            return this;
        }

        public Builder suggestions(List<String> suggestions) {
            this.suggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
            return this;
        }

        public Builder addSuggestion(String suggestion) {
            if (suggestion != null && !suggestion.isBlank()) {
                this.suggestions.add(suggestion);
            }
            return this;
        }

        public Builder applied(List<String> applied) {
            this.applied = applied != null ? new ArrayList<>(applied) : new ArrayList<>();
            return this;
        }

        public Builder addApplied(String applied) {
            if (applied != null && !applied.isBlank()) {
                this.applied.add(applied);
            }
            return this;
        }

        public Builder skipped(List<String> skipped) {
            this.skipped = skipped != null ? new ArrayList<>(skipped) : new ArrayList<>();
            return this;
        }

        public Builder addSkipped(String skipped) {
            if (skipped != null && !skipped.isBlank()) {
                this.skipped.add(skipped);
            }
            return this;
        }

        public Builder fallbackUsed(Boolean fallbackUsed) {
            this.fallbackUsed = fallbackUsed;
            return this;
        }

        public Builder modelUsed(String modelUsed) {
            this.modelUsed = modelUsed;
            return this;
        }

        public Builder executionTimeMs(Long executionTimeMs) {
            this.executionTimeMs = executionTimeMs;
            return this;
        }

        public Builder rawLlmResponse(String rawLlmResponse) {
            this.rawLlmResponse = rawLlmResponse;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public LlmRefinementResult build() {
            return new LlmRefinementResult(this);
        }
    }

    // ── Equality & Hashing ────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LlmRefinementResult that = (LlmRefinementResult) o;
        return Objects.equals(refinedSchema, that.refinedSchema)
                && Objects.equals(confidence, that.confidence)
                && Objects.equals(suggestions, that.suggestions)
                && Objects.equals(applied, that.applied)
                && Objects.equals(skipped, that.skipped)
                && Objects.equals(fallbackUsed, that.fallbackUsed)
                && Objects.equals(modelUsed, that.modelUsed)
                && Objects.equals(executionTimeMs, that.executionTimeMs)
                && Objects.equals(rawLlmResponse, that.rawLlmResponse)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refinedSchema, confidence, suggestions, applied, skipped,
                fallbackUsed, modelUsed, executionTimeMs, rawLlmResponse, errorMessage);
    }

    @Override
    public String toString() {
        return "LlmRefinementResult{"
                + "success=" + isSuccess()
                + ", confidence=" + confidence
                + ", suggestionsCount=" + getSuggestions().size()
                + ", appliedCount=" + getApplied().size()
                + ", fallbackUsed=" + fallbackUsed
                + ", modelUsed='" + modelUsed + '\''
                + ", executionTimeMs=" + executionTimeMs
                + ", errorMessage=" + (errorMessage != null ? "[present]" : "null")
                + '}';
    }
}
