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

package org.yawlfoundation.yawl.datamodelling.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.UUID;

/**
 * Type-safe model for a relationship between two data tables.
 *
 * <p>Represents a relationship with crow's feet notation cardinality,
 * data flow direction, relationship type, and optional metadata (SLA,
 * contacts, infrastructure type, visual information).</p>
 *
 * <p>Cardinality options (EndpointCardinality):
 * <ul>
 *   <li>zeroOrOne: 0..1</li>
 *   <li>exactlyOne: 1..1</li>
 *   <li>zeroOrMany: 0..*</li>
 *   <li>oneOrMany: 1..*</li>
 * </ul>
 *
 * <p>Flow directions:
 * <ul>
 *   <li>sourceToTarget: unidirectional source → target</li>
 *   <li>targetToSource: unidirectional target → source</li>
 *   <li>bidirectional: bidirectional flow</li>
 * </ul>
 *
 * <p>Builder pattern usage:</p>
 * <pre>{@code
 * DataModellingRelationship rel = DataModellingRelationship.builder()
 *     .id(UUID.randomUUID().toString())
 *     .label("orders_customers")
 *     .sourceTableId(customersTableId)
 *     .targetTableId(ordersTableId)
 *     .sourceCardinality("exactlyOne")
 *     .targetCardinality("oneOrMany")
 *     .flowDirection("sourceToTarget")
 *     .relationshipType("foreignKey")
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataModellingRelationship {

    @JsonProperty("id")
    private String id;

    @JsonProperty("label")
    private String label;

    @JsonProperty("sourceTableId")
    private String sourceTableId;

    @JsonProperty("targetTableId")
    private String targetTableId;

    @JsonProperty("sourceKey")
    private String sourceKey;

    @JsonProperty("targetKey")
    private String targetKey;

    @JsonProperty("sourceCardinality")
    private String sourceCardinality; // zeroOrOne, exactlyOne, zeroOrMany, oneOrMany

    @JsonProperty("targetCardinality")
    private String targetCardinality; // zeroOrOne, exactlyOne, zeroOrMany, oneOrMany

    @JsonProperty("flowDirection")
    private String flowDirection; // sourceToTarget, targetToSource, bidirectional

    @JsonProperty("relationshipType")
    private String relationshipType; // dataFlow, dependency, foreignKey, etl

    @JsonProperty("color")
    private String color;

    @JsonProperty("sourceHandle")
    private String sourceHandle;

    @JsonProperty("targetHandle")
    private String targetHandle;

    @JsonProperty("drawioEdgeId")
    private String drawioEdgeId;

    @JsonProperty("foreignKeyDetails")
    private Object foreignKeyDetails; // Foreign key constraint details

    @JsonProperty("etlJobMetadata")
    private Object etlJobMetadata; // ETL job information

    @JsonProperty("sla")
    private Object sla; // SLA property

    @JsonProperty("contactDetails")
    private Object contactDetails; // Contact information

    @JsonProperty("infrastructureType")
    private String infrastructureType;

    @JsonProperty("visualMetadata")
    private Object visualMetadata; // Diagram metadata

    @JsonProperty("notes")
    private String notes;

    @JsonProperty("owner")
    private String owner;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DataModellingRelationship() {
    }

    private DataModellingRelationship(Builder builder) {
        this.id = builder.id;
        this.label = builder.label;
        this.sourceTableId = builder.sourceTableId;
        this.targetTableId = builder.targetTableId;
        this.sourceKey = builder.sourceKey;
        this.targetKey = builder.targetKey;
        this.sourceCardinality = builder.sourceCardinality;
        this.targetCardinality = builder.targetCardinality;
        this.flowDirection = builder.flowDirection;
        this.relationshipType = builder.relationshipType;
        this.color = builder.color;
        this.sourceHandle = builder.sourceHandle;
        this.targetHandle = builder.targetHandle;
        this.drawioEdgeId = builder.drawioEdgeId;
        this.foreignKeyDetails = builder.foreignKeyDetails;
        this.etlJobMetadata = builder.etlJobMetadata;
        this.sla = builder.sla;
        this.contactDetails = builder.contactDetails;
        this.infrastructureType = builder.infrastructureType;
        this.visualMetadata = builder.visualMetadata;
        this.notes = builder.notes;
        this.owner = builder.owner;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String label;
        private String sourceTableId;
        private String targetTableId;
        private String sourceKey;
        private String targetKey;
        private String sourceCardinality;
        private String targetCardinality;
        private String flowDirection;
        private String relationshipType;
        private String color;
        private String sourceHandle;
        private String targetHandle;
        private String drawioEdgeId;
        private Object foreignKeyDetails;
        private Object etlJobMetadata;
        private Object sla;
        private Object contactDetails;
        private String infrastructureType;
        private Object visualMetadata;
        private String notes;
        private String owner;

        public Builder id(String id) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder sourceTableId(String sourceTableId) {
            this.sourceTableId = sourceTableId;
            return this;
        }

        public Builder targetTableId(String targetTableId) {
            this.targetTableId = targetTableId;
            return this;
        }

        public Builder sourceKey(String sourceKey) {
            this.sourceKey = sourceKey;
            return this;
        }

        public Builder targetKey(String targetKey) {
            this.targetKey = targetKey;
            return this;
        }

        public Builder sourceCardinality(String sourceCardinality) {
            this.sourceCardinality = sourceCardinality;
            return this;
        }

        public Builder targetCardinality(String targetCardinality) {
            this.targetCardinality = targetCardinality;
            return this;
        }

        public Builder flowDirection(String flowDirection) {
            this.flowDirection = flowDirection;
            return this;
        }

        public Builder relationshipType(String relationshipType) {
            this.relationshipType = relationshipType;
            return this;
        }

        public Builder color(String color) {
            this.color = color;
            return this;
        }

        public Builder sourceHandle(String sourceHandle) {
            this.sourceHandle = sourceHandle;
            return this;
        }

        public Builder targetHandle(String targetHandle) {
            this.targetHandle = targetHandle;
            return this;
        }

        public Builder drawioEdgeId(String drawioEdgeId) {
            this.drawioEdgeId = drawioEdgeId;
            return this;
        }

        public Builder foreignKeyDetails(Object foreignKeyDetails) {
            this.foreignKeyDetails = foreignKeyDetails;
            return this;
        }

        public Builder etlJobMetadata(Object etlJobMetadata) {
            this.etlJobMetadata = etlJobMetadata;
            return this;
        }

        public Builder sla(Object sla) {
            this.sla = sla;
            return this;
        }

        public Builder contactDetails(Object contactDetails) {
            this.contactDetails = contactDetails;
            return this;
        }

        public Builder infrastructureType(String infrastructureType) {
            this.infrastructureType = infrastructureType;
            return this;
        }

        public Builder visualMetadata(Object visualMetadata) {
            this.visualMetadata = visualMetadata;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public DataModellingRelationship build() {
            Objects.requireNonNull(sourceTableId, "Source table ID is required");
            Objects.requireNonNull(targetTableId, "Target table ID is required");
            return new DataModellingRelationship(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getLabel() { return label; }
    public String getSourceTableId() { return sourceTableId; }
    public String getTargetTableId() { return targetTableId; }
    public String getSourceKey() { return sourceKey; }
    public String getTargetKey() { return targetKey; }
    public String getSourceCardinality() { return sourceCardinality; }
    public String getTargetCardinality() { return targetCardinality; }
    public String getFlowDirection() { return flowDirection; }
    public String getRelationshipType() { return relationshipType; }
    public String getColor() { return color; }
    public String getSourceHandle() { return sourceHandle; }
    public String getTargetHandle() { return targetHandle; }
    public String getDrawioEdgeId() { return drawioEdgeId; }
    public Object getForeignKeyDetails() { return foreignKeyDetails; }
    public Object getEtlJobMetadata() { return etlJobMetadata; }
    public Object getSla() { return sla; }
    public Object getContactDetails() { return contactDetails; }
    public String getInfrastructureType() { return infrastructureType; }
    public Object getVisualMetadata() { return visualMetadata; }
    public String getNotes() { return notes; }
    public String getOwner() { return owner; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setLabel(String label) { this.label = label; }
    public void setSourceTableId(String sourceTableId) { this.sourceTableId = sourceTableId; }
    public void setTargetTableId(String targetTableId) { this.targetTableId = targetTableId; }
    public void setFlowDirection(String flowDirection) { this.flowDirection = flowDirection; }
    public void setNotes(String notes) { this.notes = notes; }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataModellingRelationship that = (DataModellingRelationship) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(sourceTableId, that.sourceTableId) &&
                Objects.equals(targetTableId, that.targetTableId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sourceTableId, targetTableId);
    }

    @Override
    public String toString() {
        return "DataModellingRelationship{" +
                "id='" + id + '\'' +
                ", label='" + label + '\'' +
                ", sourceTableId='" + sourceTableId + '\'' +
                ", targetTableId='" + targetTableId + '\'' +
                ", flowDirection='" + flowDirection + '\'' +
                '}';
    }
}
