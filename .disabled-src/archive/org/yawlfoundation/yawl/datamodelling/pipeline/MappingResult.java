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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Result of the MAP stage of the data pipeline.
 *
 * <p>Contains field mappings from source to target schema, confidence scores,
 * and generated transformation scripts.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MappingResult {

    @JsonProperty("fieldMappings")
    private List<FieldMapping> fieldMappings; // source → target field mappings

    @JsonProperty("transformationScript")
    private String transformationScript; // SQL|JQ|Python|PySpark transformation

    @JsonProperty("transformationFormat")
    private String transformationFormat; // sql, jq, python, pyspark

    @JsonProperty("timestamp")
    private Instant timestamp; // when mapping completed

    @JsonProperty("mappingCompleteness")
    private Double mappingCompleteness; // % of target fields mapped (0-1)

    @JsonProperty("averageConfidence")
    private Double averageConfidence; // average confidence of all mappings (0-1)

    @JsonProperty("unmappedFields")
    private List<String> unmappedFields; // target fields without source mapping

    @JsonProperty("ambiguousMappings")
    private Integer ambiguousMappings; // count of 1:N source→target mappings

    @JsonProperty("mappingNotes")
    private String mappingNotes; // notes on mapping decisions

    @JsonProperty("validationErrors")
    private List<String> validationErrors; // type compatibility errors

    /**
     * Constructs an empty MappingResult.
     */
    public MappingResult() {
        this.fieldMappings = new ArrayList<>();
        this.unmappedFields = new ArrayList<>();
        this.validationErrors = new ArrayList<>();
    }

    /**
     * Constructs a MappingResult with field mappings and transformation.
     *
     * @param fieldMappings field mappings; must not be null
     * @param transformationScript transformation script; may be null
     */
    public MappingResult(List<FieldMapping> fieldMappings, String transformationScript) {
        this.fieldMappings = fieldMappings;
        this.transformationScript = transformationScript;
        this.timestamp = Instant.now();
        this.unmappedFields = new ArrayList<>();
        this.validationErrors = new ArrayList<>();
    }

    // Getters and setters

    public List<FieldMapping> getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(List<FieldMapping> fieldMappings) {
        this.fieldMappings = fieldMappings;
    }

    public String getTransformationScript() {
        return transformationScript;
    }

    public void setTransformationScript(String transformationScript) {
        this.transformationScript = transformationScript;
    }

    public String getTransformationFormat() {
        return transformationFormat;
    }

    public void setTransformationFormat(String transformationFormat) {
        this.transformationFormat = transformationFormat;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Double getMappingCompleteness() {
        return mappingCompleteness;
    }

    public void setMappingCompleteness(Double mappingCompleteness) {
        this.mappingCompleteness = mappingCompleteness;
    }

    public Double getAverageConfidence() {
        return averageConfidence;
    }

    public void setAverageConfidence(Double averageConfidence) {
        this.averageConfidence = averageConfidence;
    }

    public List<String> getUnmappedFields() {
        return unmappedFields;
    }

    public void setUnmappedFields(List<String> unmappedFields) {
        this.unmappedFields = unmappedFields;
    }

    public Integer getAmbiguousMappings() {
        return ambiguousMappings;
    }

    public void setAmbiguousMappings(Integer ambiguousMappings) {
        this.ambiguousMappings = ambiguousMappings;
    }

    public String getMappingNotes() {
        return mappingNotes;
    }

    public void setMappingNotes(String mappingNotes) {
        this.mappingNotes = mappingNotes;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MappingResult that = (MappingResult) o;
        return Objects.equals(fieldMappings, that.fieldMappings)
                && Objects.equals(transformationScript, that.transformationScript)
                && Objects.equals(transformationFormat, that.transformationFormat)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(mappingCompleteness, that.mappingCompleteness)
                && Objects.equals(averageConfidence, that.averageConfidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldMappings, transformationScript, transformationFormat,
                timestamp, mappingCompleteness, averageConfidence);
    }

    @Override
    public String toString() {
        return "MappingResult{" +
                "mappingCount=" + (fieldMappings != null ? fieldMappings.size() : 0) +
                ", mappingCompleteness=" + mappingCompleteness +
                ", averageConfidence=" + averageConfidence +
                ", timestamp=" + timestamp +
                '}';
    }

    /**
     * Represents a single field mapping from source to target schema.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class FieldMapping {
        @JsonProperty("sourceField")
        private String sourceField;

        @JsonProperty("targetField")
        private String targetField;

        @JsonProperty("confidence")
        private Double confidence; // 0-1

        @JsonProperty("transformationExpression")
        private String transformationExpression; // e.g., "UPPER(source_field)"

        @JsonProperty("notes")
        private String notes;

        public FieldMapping() {
        }

        public FieldMapping(String sourceField, String targetField, Double confidence) {
            this.sourceField = sourceField;
            this.targetField = targetField;
            this.confidence = confidence;
        }

        // Getters and setters
        public String getSourceField() {
            return sourceField;
        }

        public void setSourceField(String sourceField) {
            this.sourceField = sourceField;
        }

        public String getTargetField() {
            return targetField;
        }

        public void setTargetField(String targetField) {
            this.targetField = targetField;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }

        public String getTransformationExpression() {
            return transformationExpression;
        }

        public void setTransformationExpression(String transformationExpression) {
            this.transformationExpression = transformationExpression;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FieldMapping that = (FieldMapping) o;
            return Objects.equals(sourceField, that.sourceField)
                    && Objects.equals(targetField, that.targetField)
                    && Objects.equals(confidence, that.confidence);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceField, targetField, confidence);
        }

        @Override
        public String toString() {
            return sourceField + " → " + targetField + " (" + confidence + ")";
        }
    }
}
