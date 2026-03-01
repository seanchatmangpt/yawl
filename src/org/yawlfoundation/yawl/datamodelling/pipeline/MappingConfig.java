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
 * Configuration for the MAP stage of the data pipeline.
 *
 * <p>Specifies how source schema should be mapped to target schema, including
 * field matching strategies, transformation generation, and confidence thresholds.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MappingConfig {

    @JsonProperty("sourceSchema")
    private String sourceSchema; // source schema JSON

    @JsonProperty("targetSchema")
    private String targetSchema; // target schema JSON

    @JsonProperty("matchingMode")
    private String matchingMode; // exact, fuzzy, semantic, llm

    @JsonProperty("fuzzyThreshold")
    private Double fuzzyThreshold; // minimum similarity (0-1) for fuzzy match

    @JsonProperty("llmMatching")
    private Boolean llmMatching; // use LLM for semantic field matching

    @JsonProperty("transformationFormat")
    private String transformationFormat; // sql, jq, python, pyspark

    @JsonProperty("generateDocs")
    private Boolean generateDocs; // generate transformation documentation

    @JsonProperty("validateTypes")
    private Boolean validateTypes; // validate type compatibility

    @JsonProperty("allowPartialMatch")
    private Boolean allowPartialMatch; // allow incomplete field mappings

    @JsonProperty("minConfidence")
    private Double minConfidence; // minimum confidence threshold (0-1)

    @JsonProperty("options")
    private Map<String, Object> options; // format-specific options

    /**
     * Constructs an empty MappingConfig.
     */
    public MappingConfig() {
        this.options = new HashMap<>();
    }

    /**
     * Constructs a MappingConfig with source and target schemas.
     *
     * @param sourceSchema source schema JSON; must not be null
     * @param targetSchema target schema JSON; must not be null
     */
    public MappingConfig(String sourceSchema, String targetSchema) {
        this.sourceSchema = sourceSchema;
        this.targetSchema = targetSchema;
        this.options = new HashMap<>();
    }

    // Getters and setters

    public String getSourceSchema() {
        return sourceSchema;
    }

    public void setSourceSchema(String sourceSchema) {
        this.sourceSchema = sourceSchema;
    }

    public String getTargetSchema() {
        return targetSchema;
    }

    public void setTargetSchema(String targetSchema) {
        this.targetSchema = targetSchema;
    }

    public String getMatchingMode() {
        return matchingMode;
    }

    public void setMatchingMode(String matchingMode) {
        this.matchingMode = matchingMode;
    }

    public Double getFuzzyThreshold() {
        return fuzzyThreshold;
    }

    public void setFuzzyThreshold(Double fuzzyThreshold) {
        this.fuzzyThreshold = fuzzyThreshold;
    }

    public Boolean getLlmMatching() {
        return llmMatching;
    }

    public void setLlmMatching(Boolean llmMatching) {
        this.llmMatching = llmMatching;
    }

    public String getTransformationFormat() {
        return transformationFormat;
    }

    public void setTransformationFormat(String transformationFormat) {
        this.transformationFormat = transformationFormat;
    }

    public Boolean getGenerateDocs() {
        return generateDocs;
    }

    public void setGenerateDocs(Boolean generateDocs) {
        this.generateDocs = generateDocs;
    }

    public Boolean getValidateTypes() {
        return validateTypes;
    }

    public void setValidateTypes(Boolean validateTypes) {
        this.validateTypes = validateTypes;
    }

    public Boolean getAllowPartialMatch() {
        return allowPartialMatch;
    }

    public void setAllowPartialMatch(Boolean allowPartialMatch) {
        this.allowPartialMatch = allowPartialMatch;
    }

    public Double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(Double minConfidence) {
        this.minConfidence = minConfidence;
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
        MappingConfig that = (MappingConfig) o;
        return Objects.equals(sourceSchema, that.sourceSchema)
                && Objects.equals(targetSchema, that.targetSchema)
                && Objects.equals(matchingMode, that.matchingMode)
                && Objects.equals(fuzzyThreshold, that.fuzzyThreshold)
                && Objects.equals(llmMatching, that.llmMatching)
                && Objects.equals(transformationFormat, that.transformationFormat)
                && Objects.equals(generateDocs, that.generateDocs)
                && Objects.equals(validateTypes, that.validateTypes)
                && Objects.equals(allowPartialMatch, that.allowPartialMatch)
                && Objects.equals(minConfidence, that.minConfidence)
                && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceSchema, targetSchema, matchingMode, fuzzyThreshold,
                llmMatching, transformationFormat, generateDocs, validateTypes,
                allowPartialMatch, minConfidence, options);
    }

    @Override
    public String toString() {
        return "MappingConfig{" +
                "matchingMode='" + matchingMode + '\'' +
                ", transformationFormat='" + transformationFormat + '\'' +
                ", fuzzyThreshold=" + fuzzyThreshold +
                ", minConfidence=" + minConfidence +
                '}';
    }
}
