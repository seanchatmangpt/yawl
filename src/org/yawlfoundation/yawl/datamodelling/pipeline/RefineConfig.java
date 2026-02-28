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

package org.yawlfoundation.yawl.datamodelling.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for the REFINE stage of the data pipeline.
 *
 * <p>Specifies how the inferred schema should be refined, including LLM-based
 * refinement, manual rule application, and documentation integration.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RefineConfig {

    @JsonProperty("llmEnabled")
    private Boolean llmEnabled; // enable LLM-based schema refinement

    @JsonProperty("llmModel")
    private String llmModel; // ollama model name or API endpoint

    @JsonProperty("llmMode")
    private String llmMode; // online (Ollama API) or offline (llama.cpp)

    @JsonProperty("temperature")
    private Double temperature; // LLM temperature (0-1)

    @JsonProperty("contextPath")
    private String contextPath; // path to documentation for context

    @JsonProperty("applyManualRules")
    private Boolean applyManualRules; // apply custom refinement rules

    @JsonProperty("rulesJson")
    private String rulesJson; // custom refinement rules as JSON

    @JsonProperty("normalizeNames")
    private Boolean normalizeNames; // standardize column names

    @JsonProperty("inferDataTypes")
    private Boolean inferDataTypes; // refine data type inference

    @JsonProperty("detectPatterns")
    private Boolean detectPatterns; // detect semantic patterns (PII, etc.)

    @JsonProperty("options")
    private Map<String, Object> options; // format-specific options

    /**
     * Constructs an empty RefineConfig.
     */
    public RefineConfig() {
        this.options = new HashMap<>();
    }

    // Getters and setters

    public Boolean getLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(Boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getLlmMode() {
        return llmMode;
    }

    public void setLlmMode(String llmMode) {
        this.llmMode = llmMode;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public Boolean getApplyManualRules() {
        return applyManualRules;
    }

    public void setApplyManualRules(Boolean applyManualRules) {
        this.applyManualRules = applyManualRules;
    }

    public String getRulesJson() {
        return rulesJson;
    }

    public void setRulesJson(String rulesJson) {
        this.rulesJson = rulesJson;
    }

    public Boolean getNormalizeNames() {
        return normalizeNames;
    }

    public void setNormalizeNames(Boolean normalizeNames) {
        this.normalizeNames = normalizeNames;
    }

    public Boolean getInferDataTypes() {
        return inferDataTypes;
    }

    public void setInferDataTypes(Boolean inferDataTypes) {
        this.inferDataTypes = inferDataTypes;
    }

    public Boolean getDetectPatterns() {
        return detectPatterns;
    }

    public void setDetectPatterns(Boolean detectPatterns) {
        this.detectPatterns = detectPatterns;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefineConfig that = (RefineConfig) o;
        return Objects.equals(llmEnabled, that.llmEnabled)
                && Objects.equals(llmModel, that.llmModel)
                && Objects.equals(llmMode, that.llmMode)
                && Objects.equals(temperature, that.temperature)
                && Objects.equals(contextPath, that.contextPath)
                && Objects.equals(applyManualRules, that.applyManualRules)
                && Objects.equals(rulesJson, that.rulesJson)
                && Objects.equals(normalizeNames, that.normalizeNames)
                && Objects.equals(inferDataTypes, that.inferDataTypes)
                && Objects.equals(detectPatterns, that.detectPatterns)
                && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(llmEnabled, llmModel, llmMode, temperature, contextPath,
                applyManualRules, rulesJson, normalizeNames, inferDataTypes,
                detectPatterns, options);
    }

    @Override
    public String toString() {
        return "RefineConfig{" +
                "llmEnabled=" + llmEnabled +
                ", llmModel='" + llmModel + '\'' +
                ", llmMode='" + llmMode + '\'' +
                ", temperature=" + temperature +
                ", contextPath='" + contextPath + '\'' +
                ", applyManualRules=" + applyManualRules +
                ", rulesJson='" + rulesJson + '\'' +
                ", normalizeNames=" + normalizeNames +
                ", inferDataTypes=" + inferDataTypes +
                ", detectPatterns=" + detectPatterns +
                ", options=" + options +
                '}';
    }
}
