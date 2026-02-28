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
 * Configuration for the INFER stage of the data pipeline.
 *
 * <p>Specifies how the pipeline should infer schema from ingested data, including:
 * type inference strategies, constraint detection, and similarity analysis.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InferenceConfig {

    @JsonProperty("autoDetectPrimaryKey")
    private Boolean autoDetectPrimaryKey; // infer PK from uniqueness

    @JsonProperty("autoDetectForeignKeys")
    private Boolean autoDetectForeignKeys; // infer FK relationships

    @JsonProperty("typeInferenceMode")
    private String typeInferenceMode; // strict, lenient, semantic

    @JsonProperty("mergeSchemas")
    private Boolean mergeSchemas; // merge multiple schema inferences

    @JsonProperty("detectConstraints")
    private Boolean detectConstraints; // NOT NULL, UNIQUE, CHECK, etc.

    @JsonProperty("detectSimilarFields")
    private Boolean detectSimilarFields; // fuzzy matching on field names

    @JsonProperty("minCardinalityThreshold")
    private Double minCardinalityThreshold; // min cardinality for unique constraint (0-1)

    @JsonProperty("formatDetectionEnabled")
    private Boolean formatDetectionEnabled; // detect date, UUID, email, etc.

    @JsonProperty("statisticsLevel")
    private String statisticsLevel; // none, basic, detailed, comprehensive

    @JsonProperty("options")
    private Map<String, Object> options; // format-specific options

    /**
     * Constructs an empty InferenceConfig.
     */
    public InferenceConfig() {
        this.options = new HashMap<>();
    }

    // Getters and setters

    public Boolean getAutoDetectPrimaryKey() {
        return autoDetectPrimaryKey;
    }

    public void setAutoDetectPrimaryKey(Boolean autoDetectPrimaryKey) {
        this.autoDetectPrimaryKey = autoDetectPrimaryKey;
    }

    public Boolean getAutoDetectForeignKeys() {
        return autoDetectForeignKeys;
    }

    public void setAutoDetectForeignKeys(Boolean autoDetectForeignKeys) {
        this.autoDetectForeignKeys = autoDetectForeignKeys;
    }

    public String getTypeInferenceMode() {
        return typeInferenceMode;
    }

    public void setTypeInferenceMode(String typeInferenceMode) {
        this.typeInferenceMode = typeInferenceMode;
    }

    public Boolean getMergeSchemas() {
        return mergeSchemas;
    }

    public void setMergeSchemas(Boolean mergeSchemas) {
        this.mergeSchemas = mergeSchemas;
    }

    public Boolean getDetectConstraints() {
        return detectConstraints;
    }

    public void setDetectConstraints(Boolean detectConstraints) {
        this.detectConstraints = detectConstraints;
    }

    public Boolean getDetectSimilarFields() {
        return detectSimilarFields;
    }

    public void setDetectSimilarFields(Boolean detectSimilarFields) {
        this.detectSimilarFields = detectSimilarFields;
    }

    public Double getMinCardinalityThreshold() {
        return minCardinalityThreshold;
    }

    public void setMinCardinalityThreshold(Double minCardinalityThreshold) {
        this.minCardinalityThreshold = minCardinalityThreshold;
    }

    public Boolean getFormatDetectionEnabled() {
        return formatDetectionEnabled;
    }

    public void setFormatDetectionEnabled(Boolean formatDetectionEnabled) {
        this.formatDetectionEnabled = formatDetectionEnabled;
    }

    public String getStatisticsLevel() {
        return statisticsLevel;
    }

    public void setStatisticsLevel(String statisticsLevel) {
        this.statisticsLevel = statisticsLevel;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options != null ? options : new HashMap<>();
    }

    // Builder pattern

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Boolean autoDetectPrimaryKey;
        private Boolean autoDetectForeignKeys;
        private String typeInferenceMode;
        private Boolean mergeSchemas;
        private Boolean detectConstraints;
        private Boolean detectSimilarFields;
        private Double minCardinalityThreshold;
        private Boolean formatDetectionEnabled;
        private String statisticsLevel;
        private Map<String, Object> options = new HashMap<>();

        public Builder autoDetectPrimaryKey(Boolean autoDetectPrimaryKey) {
            this.autoDetectPrimaryKey = autoDetectPrimaryKey;
            return this;
        }

        public Builder autoDetectForeignKeys(Boolean autoDetectForeignKeys) {
            this.autoDetectForeignKeys = autoDetectForeignKeys;
            return this;
        }

        public Builder typeInferenceMode(String typeInferenceMode) {
            this.typeInferenceMode = typeInferenceMode;
            return this;
        }

        public Builder mergeSchemas(Boolean mergeSchemas) {
            this.mergeSchemas = mergeSchemas;
            return this;
        }

        public Builder detectConstraints(Boolean detectConstraints) {
            this.detectConstraints = detectConstraints;
            return this;
        }

        public Builder detectSimilarFields(Boolean detectSimilarFields) {
            this.detectSimilarFields = detectSimilarFields;
            return this;
        }

        public Builder minCardinalityThreshold(Double minCardinalityThreshold) {
            this.minCardinalityThreshold = minCardinalityThreshold;
            return this;
        }

        public Builder formatDetectionEnabled(Boolean formatDetectionEnabled) {
            this.formatDetectionEnabled = formatDetectionEnabled;
            return this;
        }

        public Builder statisticsLevel(String statisticsLevel) {
            this.statisticsLevel = statisticsLevel;
            return this;
        }

        public Builder addOption(String key, Object value) {
            this.options.put(key, value);
            return this;
        }

        public Builder options(Map<String, Object> options) {
            this.options = options != null ? new HashMap<>(options) : new HashMap<>();
            return this;
        }

        public InferenceConfig build() {
            InferenceConfig config = new InferenceConfig();
            config.autoDetectPrimaryKey = this.autoDetectPrimaryKey;
            config.autoDetectForeignKeys = this.autoDetectForeignKeys;
            config.typeInferenceMode = this.typeInferenceMode;
            config.mergeSchemas = this.mergeSchemas;
            config.detectConstraints = this.detectConstraints;
            config.detectSimilarFields = this.detectSimilarFields;
            config.minCardinalityThreshold = this.minCardinalityThreshold;
            config.formatDetectionEnabled = this.formatDetectionEnabled;
            config.statisticsLevel = this.statisticsLevel;
            config.options = this.options;
            return config;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InferenceConfig that = (InferenceConfig) o;
        return Objects.equals(autoDetectPrimaryKey, that.autoDetectPrimaryKey)
                && Objects.equals(autoDetectForeignKeys, that.autoDetectForeignKeys)
                && Objects.equals(typeInferenceMode, that.typeInferenceMode)
                && Objects.equals(mergeSchemas, that.mergeSchemas)
                && Objects.equals(detectConstraints, that.detectConstraints)
                && Objects.equals(detectSimilarFields, that.detectSimilarFields)
                && Objects.equals(minCardinalityThreshold, that.minCardinalityThreshold)
                && Objects.equals(formatDetectionEnabled, that.formatDetectionEnabled)
                && Objects.equals(statisticsLevel, that.statisticsLevel)
                && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(autoDetectPrimaryKey, autoDetectForeignKeys, typeInferenceMode,
                mergeSchemas, detectConstraints, detectSimilarFields, minCardinalityThreshold,
                formatDetectionEnabled, statisticsLevel, options);
    }

    @Override
    public String toString() {
        return "InferenceConfig{" +
                "autoDetectPrimaryKey=" + autoDetectPrimaryKey +
                ", autoDetectForeignKeys=" + autoDetectForeignKeys +
                ", typeInferenceMode='" + typeInferenceMode + '\'' +
                ", mergeSchemas=" + mergeSchemas +
                ", detectConstraints=" + detectConstraints +
                ", detectSimilarFields=" + detectSimilarFields +
                ", minCardinalityThreshold=" + minCardinalityThreshold +
                ", formatDetectionEnabled=" + formatDetectionEnabled +
                ", statisticsLevel='" + statisticsLevel + '\'' +
                ", options=" + options +
                '}';
    }
}
