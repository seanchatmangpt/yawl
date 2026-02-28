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
 * Type-safe model for a data column/field in ODCS v3.1.0 format.
 *
 * <p>Represents a single column with complete ODCS property support including
 * nested properties, constraints, quality rules, and metadata. Supports both
 * flat columns and nested OBJECT/ARRAY types.</p>
 *
 * <p>Builder pattern usage:</p>
 * <pre>{@code
 * DataModellingColumn col = DataModellingColumn.builder()
 *     .id(UUID.randomUUID().toString())
 *     .name("customer_id")
 *     .dataType("bigint")
 *     .primaryKey(true)
 *     .nullable(false)
 *     .businessName("Customer Identifier")
 *     .description("Unique customer identifier")
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataModellingColumn {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("businessName")
    private String businessName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("dataType")
    private String dataType;

    @JsonProperty("physicalType")
    private String physicalType;

    @JsonProperty("physicalName")
    private String physicalName;

    @JsonProperty("nullable")
    private Boolean nullable;

    @JsonProperty("primaryKey")
    private Boolean primaryKey;

    @JsonProperty("primaryKeyPosition")
    private Integer primaryKeyPosition;

    @JsonProperty("unique")
    private Boolean unique;

    @JsonProperty("partitioned")
    private Boolean partitioned;

    @JsonProperty("partitionKeyPosition")
    private Integer partitionKeyPosition;

    @JsonProperty("clustered")
    private Boolean clustered;

    @JsonProperty("classification")
    private String classification;

    @JsonProperty("criticalDataElement")
    private Boolean criticalDataElement;

    @JsonProperty("encryptedName")
    private Boolean encryptedName;

    @JsonProperty("defaultValue")
    private String defaultValue;

    @JsonProperty("examples")
    private List<String> examples;

    @JsonProperty("enumValues")
    private List<String> enumValues;

    @JsonProperty("properties")
    private List<DataModellingColumn> properties; // For OBJECT types

    @JsonProperty("items")
    private DataModellingColumn items; // For ARRAY types

    @JsonProperty("quality")
    private Object quality; // QualityRule[]

    @JsonProperty("tags")
    private List<Object> tags; // Mixed tag types

    @JsonProperty("customProperties")
    private List<Object> customProperties; // CustomProperty[]

    // ── Constructors ──────────────────────────────────────────────────────────

    public DataModellingColumn() {
    }

    private DataModellingColumn(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.businessName = builder.businessName;
        this.description = builder.description;
        this.dataType = builder.dataType;
        this.physicalType = builder.physicalType;
        this.physicalName = builder.physicalName;
        this.nullable = builder.nullable;
        this.primaryKey = builder.primaryKey;
        this.primaryKeyPosition = builder.primaryKeyPosition;
        this.unique = builder.unique;
        this.partitioned = builder.partitioned;
        this.partitionKeyPosition = builder.partitionKeyPosition;
        this.clustered = builder.clustered;
        this.classification = builder.classification;
        this.criticalDataElement = builder.criticalDataElement;
        this.encryptedName = builder.encryptedName;
        this.defaultValue = builder.defaultValue;
        this.examples = builder.examples;
        this.enumValues = builder.enumValues;
        this.properties = builder.properties;
        this.items = builder.items;
        this.quality = builder.quality;
        this.tags = builder.tags;
        this.customProperties = builder.customProperties;
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
        private String dataType;
        private String physicalType;
        private String physicalName;
        private Boolean nullable;
        private Boolean primaryKey;
        private Integer primaryKeyPosition;
        private Boolean unique;
        private Boolean partitioned;
        private Integer partitionKeyPosition;
        private Boolean clustered;
        private String classification;
        private Boolean criticalDataElement;
        private Boolean encryptedName;
        private String defaultValue;
        private List<String> examples;
        private List<String> enumValues;
        private List<DataModellingColumn> properties;
        private DataModellingColumn items;
        private Object quality;
        private List<Object> tags;
        private List<Object> customProperties;

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

        public Builder dataType(String dataType) {
            this.dataType = dataType;
            return this;
        }

        public Builder physicalType(String physicalType) {
            this.physicalType = physicalType;
            return this;
        }

        public Builder physicalName(String physicalName) {
            this.physicalName = physicalName;
            return this;
        }

        public Builder nullable(Boolean nullable) {
            this.nullable = nullable;
            return this;
        }

        public Builder primaryKey(Boolean primaryKey) {
            this.primaryKey = primaryKey;
            return this;
        }

        public Builder primaryKeyPosition(Integer primaryKeyPosition) {
            this.primaryKeyPosition = primaryKeyPosition;
            return this;
        }

        public Builder unique(Boolean unique) {
            this.unique = unique;
            return this;
        }

        public Builder partitioned(Boolean partitioned) {
            this.partitioned = partitioned;
            return this;
        }

        public Builder partitionKeyPosition(Integer partitionKeyPosition) {
            this.partitionKeyPosition = partitionKeyPosition;
            return this;
        }

        public Builder clustered(Boolean clustered) {
            this.clustered = clustered;
            return this;
        }

        public Builder classification(String classification) {
            this.classification = classification;
            return this;
        }

        public Builder criticalDataElement(Boolean criticalDataElement) {
            this.criticalDataElement = criticalDataElement;
            return this;
        }

        public Builder encryptedName(Boolean encryptedName) {
            this.encryptedName = encryptedName;
            return this;
        }

        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder addExample(String example) {
            if (this.examples == null) {
                this.examples = new ArrayList<>();
            }
            this.examples.add(example);
            return this;
        }

        public Builder addEnumValue(String enumValue) {
            if (this.enumValues == null) {
                this.enumValues = new ArrayList<>();
            }
            this.enumValues.add(enumValue);
            return this;
        }

        public Builder addProperty(DataModellingColumn property) {
            if (this.properties == null) {
                this.properties = new ArrayList<>();
            }
            this.properties.add(property);
            return this;
        }

        public Builder items(DataModellingColumn items) {
            this.items = items;
            return this;
        }

        public Builder quality(Object quality) {
            this.quality = quality;
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

        public DataModellingColumn build() {
            Objects.requireNonNull(name, "Column name is required");
            return new DataModellingColumn(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public String getBusinessName() { return businessName; }
    public String getDescription() { return description; }
    public String getDataType() { return dataType; }
    public String getPhysicalType() { return physicalType; }
    public String getPhysicalName() { return physicalName; }
    public Boolean getNullable() { return nullable; }
    public Boolean getPrimaryKey() { return primaryKey; }
    public Integer getPrimaryKeyPosition() { return primaryKeyPosition; }
    public Boolean getUnique() { return unique; }
    public Boolean getPartitioned() { return partitioned; }
    public Integer getPartitionKeyPosition() { return partitionKeyPosition; }
    public Boolean getClustered() { return clustered; }
    public String getClassification() { return classification; }
    public Boolean getCriticalDataElement() { return criticalDataElement; }
    public Boolean getEncryptedName() { return encryptedName; }
    public String getDefaultValue() { return defaultValue; }
    public List<String> getExamples() { return examples; }
    public List<String> getEnumValues() { return enumValues; }
    public List<DataModellingColumn> getProperties() { return properties; }
    public DataModellingColumn getItems() { return items; }
    public Object getQuality() { return quality; }
    public List<Object> getTags() { return tags; }
    public List<Object> getCustomProperties() { return customProperties; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public void setDescription(String description) { this.description = description; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public void setNullable(Boolean nullable) { this.nullable = nullable; }
    public void setPrimaryKey(Boolean primaryKey) { this.primaryKey = primaryKey; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataModellingColumn column = (DataModellingColumn) o;
        return Objects.equals(id, column.id) &&
                Objects.equals(name, column.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "DataModellingColumn{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", dataType='" + dataType + '\'' +
                ", nullable=" + nullable +
                ", primaryKey=" + primaryKey +
                '}';
    }
}
