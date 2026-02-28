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
import org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Result of the INFER stage of the data pipeline.
 *
 * <p>Wraps the inferred schema from ingested data, providing both raw JSON access
 * and typed conversion to {@link DataModellingWorkspace} for integration with
 * Phase 1 models.</p>
 *
 * <p>Inference includes:</p>
 * <ul>
 *   <li>Column detection and type inference</li>
 *   <li>Primary key detection</li>
 *   <li>Foreign key relationship discovery</li>
 *   <li>Constraint detection (NOT NULL, UNIQUE, etc.)</li>
 *   <li>Data quality metrics and statistics</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class InferenceResult {

    @JsonProperty("inferredSchema")
    private String inferredSchema; // raw JSON from SDK inference

    @JsonProperty("timestamp")
    private Instant timestamp; // when inference completed

    @JsonProperty("columnCount")
    private Integer columnCount; // detected columns

    @JsonProperty("primaryKeyColumns")
    private String primaryKeyColumns; // detected PK columns (comma-separated)

    @JsonProperty("foreignKeyRelationships")
    private Integer foreignKeyRelationships; // detected FK count

    @JsonProperty("constraintCount")
    private Integer constraintCount; // detected constraints

    @JsonProperty("confidenceScore")
    private Double confidenceScore; // schema confidence (0-1)

    @JsonProperty("dataQualityMetrics")
    private Object dataQualityMetrics; // quality scores per column

    @JsonProperty("detectectedFormats")
    private Map<String, String> detectedFormats; // detected formats (date, email, UUID, etc.)

    @JsonProperty("inferenceNotes")
    private String inferenceNotes; // notes on inference process

    /** Cached workspace for lazy conversion. */
    private transient DataModellingWorkspace workspace;

    /** ObjectMapper for JSON parsing. */
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Constructs an empty InferenceResult.
     */
    public InferenceResult() {
        this.detectedFormats = new HashMap<>();
    }

    /**
     * Constructs an InferenceResult with inferred schema.
     *
     * @param inferredSchema the inferred schema as JSON string; must not be null
     * @param columnCount number of detected columns; must be >= 0
     */
    public InferenceResult(String inferredSchema, Integer columnCount) {
        this.inferredSchema = inferredSchema;
        this.columnCount = columnCount;
        this.timestamp = Instant.now();
        this.detectedFormats = new HashMap<>();
    }

    /**
     * Converts the raw inferred schema JSON to a typed {@link DataModellingWorkspace}.
     *
     * <p>This method bridges Phase 2 (pipeline inference) with Phase 1 (type-safe models),
     * allowing seamless integration in workflow execution.</p>
     *
     * @return workspace representation of inferred schema; never null
     * @throws IllegalStateException if inferredSchema is null or invalid JSON
     */
    public DataModellingWorkspace asWorkspace() {
        if (workspace == null) {
            if (inferredSchema == null || inferredSchema.trim().isEmpty()) {
                throw new IllegalStateException("inferredSchema is null or empty");
            }
            try {
                workspace = mapper.readValue(inferredSchema, DataModellingWorkspace.class);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to parse inferredSchema as DataModellingWorkspace: " + e.getMessage(), e);
            }
        }
        return workspace;
    }

    /**
     * Returns the raw inferred schema as JSON string.
     *
     * @return inferred schema JSON; may be null
     */
    public String getInferredSchemaJson() {
        return inferredSchema;
    }

    // Getters and setters

    public String getInferredSchema() {
        return inferredSchema;
    }

    public void setInferredSchema(String inferredSchema) {
        this.inferredSchema = inferredSchema;
        this.workspace = null; // invalidate cache
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(Integer columnCount) {
        this.columnCount = columnCount;
    }

    public String getPrimaryKeyColumns() {
        return primaryKeyColumns;
    }

    public void setPrimaryKeyColumns(String primaryKeyColumns) {
        this.primaryKeyColumns = primaryKeyColumns;
    }

    public Integer getForeignKeyRelationships() {
        return foreignKeyRelationships;
    }

    public void setForeignKeyRelationships(Integer foreignKeyRelationships) {
        this.foreignKeyRelationships = foreignKeyRelationships;
    }

    public Integer getConstraintCount() {
        return constraintCount;
    }

    public void setConstraintCount(Integer constraintCount) {
        this.constraintCount = constraintCount;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public Object getDataQualityMetrics() {
        return dataQualityMetrics;
    }

    public void setDataQualityMetrics(Object dataQualityMetrics) {
        this.dataQualityMetrics = dataQualityMetrics;
    }

    public Map<String, String> getDetectedFormats() {
        return detectedFormats;
    }

    public void setDetectedFormats(Map<String, String> detectedFormats) {
        this.detectedFormats = detectedFormats;
    }

    public String getInferenceNotes() {
        return inferenceNotes;
    }

    public void setInferenceNotes(String inferenceNotes) {
        this.inferenceNotes = inferenceNotes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InferenceResult that = (InferenceResult) o;
        return Objects.equals(inferredSchema, that.inferredSchema)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(columnCount, that.columnCount)
                && Objects.equals(primaryKeyColumns, that.primaryKeyColumns)
                && Objects.equals(foreignKeyRelationships, that.foreignKeyRelationships)
                && Objects.equals(constraintCount, that.constraintCount)
                && Objects.equals(confidenceScore, that.confidenceScore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inferredSchema, timestamp, columnCount, primaryKeyColumns,
                foreignKeyRelationships, constraintCount, confidenceScore);
    }

    @Override
    public String toString() {
        return "InferenceResult{" +
                "columnCount=" + columnCount +
                ", primaryKeyColumns='" + primaryKeyColumns + '\'' +
                ", timestamp=" + timestamp +
                ", confidenceScore=" + confidenceScore +
                '}';
    }
}
