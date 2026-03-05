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
import java.util.Objects;

/**
 * Result of the REFINE stage of the data pipeline.
 *
 * <p>Contains the refined schema after LLM refinement, rule application,
 * and documentation integration.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RefineResult {

    @JsonProperty("refinedSchema")
    private String refinedSchema; // refined schema as JSON

    @JsonProperty("schemaJson")
    private Object schemaJson; // parsed schema object

    @JsonProperty("timestamp")
    private Instant timestamp; // when refinement completed

    @JsonProperty("llmApplied")
    private Boolean llmApplied; // whether LLM refinement was applied

    @JsonProperty("rulesApplied")
    private Integer rulesApplied; // number of refinement rules applied

    @JsonProperty("confidenceScore")
    private Double confidenceScore; // confidence in refined schema (0-1)

    @JsonProperty("refinementNotes")
    private String refinementNotes; // notes on changes made

    @JsonProperty("patternDetections")
    private Object patternDetections; // detected semantic patterns (PII, etc.)

    /**
     * Constructs an empty RefineResult.
     */
    public RefineResult() {
    }

    /**
     * Constructs a RefineResult with refined schema.
     *
     * @param refinedSchema the refined schema as JSON string; must not be null
     * @param llmApplied whether LLM was used; must not be null
     */
    public RefineResult(String refinedSchema, Boolean llmApplied) {
        this.refinedSchema = refinedSchema;
        this.llmApplied = llmApplied;
        this.timestamp = Instant.now();
    }

    // Getters and setters

    public String getRefinedSchema() {
        return refinedSchema;
    }

    public void setRefinedSchema(String refinedSchema) {
        this.refinedSchema = refinedSchema;
    }

    public Object getSchemaJson() {
        return schemaJson;
    }

    public void setSchemaJson(Object schemaJson) {
        this.schemaJson = schemaJson;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Boolean getLlmApplied() {
        return llmApplied;
    }

    public void setLlmApplied(Boolean llmApplied) {
        this.llmApplied = llmApplied;
    }

    public Integer getRulesApplied() {
        return rulesApplied;
    }

    public void setRulesApplied(Integer rulesApplied) {
        this.rulesApplied = rulesApplied;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getRefinementNotes() {
        return refinementNotes;
    }

    public void setRefinementNotes(String refinementNotes) {
        this.refinementNotes = refinementNotes;
    }

    public Object getPatternDetections() {
        return patternDetections;
    }

    public void setPatternDetections(Object patternDetections) {
        this.patternDetections = patternDetections;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefineResult that = (RefineResult) o;
        return Objects.equals(refinedSchema, that.refinedSchema)
                && Objects.equals(schemaJson, that.schemaJson)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(llmApplied, that.llmApplied)
                && Objects.equals(rulesApplied, that.rulesApplied)
                && Objects.equals(confidenceScore, that.confidenceScore)
                && Objects.equals(refinementNotes, that.refinementNotes)
                && Objects.equals(patternDetections, that.patternDetections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refinedSchema, schemaJson, timestamp, llmApplied,
                rulesApplied, confidenceScore, refinementNotes, patternDetections);
    }

    @Override
    public String toString() {
        return "RefineResult{" +
                "refinedSchema='" + refinedSchema + '\'' +
                ", timestamp=" + timestamp +
                ", llmApplied=" + llmApplied +
                ", rulesApplied=" + rulesApplied +
                ", confidenceScore=" + confidenceScore +
                ", refinementNotes='" + refinementNotes + '\'' +
                '}';
    }
}
