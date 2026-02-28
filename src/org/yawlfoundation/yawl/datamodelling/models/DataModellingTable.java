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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Type-safe model for a data table/schema in ODCS v3.1.0 format.
 *
 * <p>Represents a table with columns, relationships, metadata, and quality rules.
 * Supports ODCS naming conventions and comprehensive metadata including owner,
 * infrastructure type, SLA, contacts, and custom properties.</p>
 *
 * <p>Builder pattern usage:</p>
 * <pre>{@code
 * DataModellingTable table = DataModellingTable.builder()
 *     .id(UUID.randomUUID().toString())
 *     .name("customers")
 *     .businessName("Customer Data")
 *     .description("Core customer records")
 *     .addColumn(DataModellingColumn.builder()
 *         .name("customer_id")
 *         .dataType("bigint")
 *         .primaryKey(true)
 *         .build())
 *     .owner("data-team")
 *     .infrastructureType("postgresql")
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataModellingTable {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("businessName")
    private String businessName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("physicalName")
    private String physicalName;

    @JsonProperty("physicalType")
    private String physicalType;

    @JsonProperty("dataGranularityDescription")
    private String dataGranularityDescription;

    @JsonProperty("columns")
    private List<DataModellingColumn> columns;

    @JsonProperty("owner")
    private String owner;

    @JsonProperty("infrastructureType")
    private String infrastructureType;

    @JsonProperty("medallionLayer")
    private String medallionLayer;

    @JsonProperty("scd")
    private String scd; // Slowly Changing Dimension pattern

    @JsonProperty("quality")
    private Object quality; // QualityRule[]

    @JsonProperty("authoritativeDefinitions")
    private List<Object> authoritativeDefinitions; // AuthoritativeDefinition[]

    @JsonProperty("relationships")
    private List<String> relationships; // Relationship IDs

    @JsonProperty("tags")
    private List<Object> tags; // Mixed tag types

    @JsonProperty("customProperties")
    private List<Object> customProperties; // CustomProperty[]

    @JsonProperty("slaTerm")
    private Object slaTerm; // SLA details

    @JsonProperty("contactDetails")
    private Object contactDetails; // Contact information

    @JsonProperty("notes")
    private String notes;

    @JsonProperty("version")
    private String version;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DataModellingTable() {
    }

    private DataModellingTable(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.businessName = builder.businessName;
        this.description = builder.description;
        this.physicalName = builder.physicalName;
        this.physicalType = builder.physicalType;
        this.dataGranularityDescription = builder.dataGranularityDescription;
        this.columns = builder.columns;
        this.owner = builder.owner;
        this.infrastructureType = builder.infrastructureType;
        this.medallionLayer = builder.medallionLayer;
        this.scd = builder.scd;
        this.quality = builder.quality;
        this.authoritativeDefinitions = builder.authoritativeDefinitions;
        this.relationships = builder.relationships;
        this.tags = builder.tags;
        this.customProperties = builder.customProperties;
        this.slaTerm = builder.slaTerm;
        this.contactDetails = builder.contactDetails;
        this.notes = builder.notes;
        this.version = builder.version;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String businessName;
        private String description;
        private String physicalName;
        private String physicalType;
        private String dataGranularityDescription;
        private List<DataModellingColumn> columns;
        private String owner;
        private String infrastructureType;
        private String medallionLayer;
        private String scd;
        private Object quality;
        private List<Object> authoritativeDefinitions;
        private List<String> relationships;
        private List<Object> tags;
        private List<Object> customProperties;
        private Object slaTerm;
        private Object contactDetails;
        private String notes;
        private String version;

        public Builder id(String id) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder businessName(String businessName) {
            this.businessName = businessName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder physicalName(String physicalName) {
            this.physicalName = physicalName;
            return this;
        }

        public Builder physicalType(String physicalType) {
            this.physicalType = physicalType;
            return this;
        }

        public Builder dataGranularityDescription(String dataGranularityDescription) {
            this.dataGranularityDescription = dataGranularityDescription;
            return this;
        }

        public Builder addColumn(DataModellingColumn column) {
            if (this.columns == null) {
                this.columns = new ArrayList<>();
            }
            this.columns.add(column);
            return this;
        }

        public Builder columns(List<DataModellingColumn> columns) {
            this.columns = columns;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder infrastructureType(String infrastructureType) {
            this.infrastructureType = infrastructureType;
            return this;
        }

        public Builder medallionLayer(String medallionLayer) {
            this.medallionLayer = medallionLayer;
            return this;
        }

        public Builder scd(String scd) {
            this.scd = scd;
            return this;
        }

        public Builder quality(Object quality) {
            this.quality = quality;
            return this;
        }

        public Builder addAuthoritativeDefinition(Object definition) {
            if (this.authoritativeDefinitions == null) {
                this.authoritativeDefinitions = new ArrayList<>();
            }
            this.authoritativeDefinitions.add(definition);
            return this;
        }

        public Builder addRelationship(String relationshipId) {
            if (this.relationships == null) {
                this.relationships = new ArrayList<>();
            }
            this.relationships.add(relationshipId);
            return this;
        }

        public Builder addTag(Object tag) {
            if (this.tags == null) {
                this.tags = new ArrayList<>();
            }
            this.tags.add(tag);
            return this;
        }

        public Builder addCustomProperty(Object customProperty) {
            if (this.customProperties == null) {
                this.customProperties = new ArrayList<>();
            }
            this.customProperties.add(customProperty);
            return this;
        }

        public Builder slaTerm(Object slaTerm) {
            this.slaTerm = slaTerm;
            return this;
        }

        public Builder contactDetails(Object contactDetails) {
            this.contactDetails = contactDetails;
            return this;
        }

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public DataModellingTable build() {
            Objects.requireNonNull(name, "Table name is required");
            return new DataModellingTable(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public String getBusinessName() { return businessName; }
    public String getDescription() { return description; }
    public String getPhysicalName() { return physicalName; }
    public String getPhysicalType() { return physicalType; }
    public String getDataGranularityDescription() { return dataGranularityDescription; }
    public List<DataModellingColumn> getColumns() { return columns; }
    public String getOwner() { return owner; }
    public String getInfrastructureType() { return infrastructureType; }
    public String getMedallionLayer() { return medallionLayer; }
    public String getScd() { return scd; }
    public Object getQuality() { return quality; }
    public List<Object> getAuthoritativeDefinitions() { return authoritativeDefinitions; }
    public List<String> getRelationships() { return relationships; }
    public List<Object> getTags() { return tags; }
    public List<Object> getCustomProperties() { return customProperties; }
    public Object getSlaTerm() { return slaTerm; }
    public Object getContactDetails() { return contactDetails; }
    public String getNotes() { return notes; }
    public String getVersion() { return version; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setOwner(String owner) { this.owner = owner; }
    public void setDescription(String description) { this.description = description; }
    public void setColumns(List<DataModellingColumn> columns) { this.columns = columns; }
    public void setNotes(String notes) { this.notes = notes; }

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Get a column by name.
     *
     * @param columnName the column name
     * @return the column or null if not found
     */
    public DataModellingColumn getColumnByName(String columnName) {
        if (columns == null) return null;
        return columns.stream()
                .filter(c -> c.getName().equals(columnName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the primary key column(s).
     *
     * @return list of primary key columns
     */
    public List<DataModellingColumn> getPrimaryKeyColumns() {
        if (columns == null) return new ArrayList<>();
        return columns.stream()
                .filter(c -> Boolean.TRUE.equals(c.getPrimaryKey()))
                .toList();
    }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataModellingTable table = (DataModellingTable) o;
        return Objects.equals(id, table.id) &&
                Objects.equals(name, table.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "DataModellingTable{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", columns=" + (columns != null ? columns.size() : 0) +
                ", owner='" + owner + '\'' +
                '}';
    }
}
