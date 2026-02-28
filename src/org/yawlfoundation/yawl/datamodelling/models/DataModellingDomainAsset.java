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
 * Type-safe model for a data asset within a domain.
 *
 * <p>An asset represents a concrete data resource (table, database, API, etc.)
 * that belongs to a domain.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataModellingDomainAsset {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("assetType")
    private String assetType; // table, database, api, file, etc.

    @JsonProperty("owner")
    private String owner;

    @JsonProperty("location")
    private String location;

    @JsonProperty("format")
    private String format; // JSON, CSV, Parquet, SQL, etc.

    @JsonProperty("tags")
    private List<Object> tags;

    @JsonProperty("customProperties")
    private List<Object> customProperties;

    @JsonProperty("notes")
    private String notes;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DataModellingDomainAsset() {
    }

    private DataModellingDomainAsset(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.assetType = builder.assetType;
        this.owner = builder.owner;
        this.location = builder.location;
        this.format = builder.format;
        this.tags = builder.tags;
        this.customProperties = builder.customProperties;
        this.notes = builder.notes;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String description;
        private String assetType;
        private String owner;
        private String location;
        private String format;
        private List<Object> tags;
        private List<Object> customProperties;
        private String notes;

        public Builder id(String id) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder assetType(String assetType) {
            this.assetType = assetType;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
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

        public Builder notes(String notes) {
            this.notes = notes;
            return this;
        }

        public DataModellingDomainAsset build() {
            Objects.requireNonNull(name, "Asset name is required");
            return new DataModellingDomainAsset(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getAssetType() { return assetType; }
    public String getOwner() { return owner; }
    public String getLocation() { return location; }
    public String getFormat() { return format; }
    public List<Object> getTags() { return tags; }
    public List<Object> getCustomProperties() { return customProperties; }
    public String getNotes() { return notes; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public void setOwner(String owner) { this.owner = owner; }
    public void setLocation(String location) { this.location = location; }
    public void setFormat(String format) { this.format = format; }
    public void setNotes(String notes) { this.notes = notes; }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataModellingDomainAsset asset = (DataModellingDomainAsset) o;
        return Objects.equals(id, asset.id) &&
                Objects.equals(name, asset.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "DataModellingDomainAsset{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", assetType='" + assetType + '\'' +
                '}';
    }
}
