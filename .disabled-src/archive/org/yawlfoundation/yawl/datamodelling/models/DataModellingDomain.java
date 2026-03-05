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
 * Type-safe model for a data domain in ODCS v3.1.0 format.
 *
 * <p>Domains are logical groupings of related systems, assets, and data resources.
 * A domain can contain multiple systems and assets that are related to a specific
 * business area or organizational context.</p>
 *
 * <p>Builder pattern usage:</p>
 * <pre>{@code
 * DataModellingDomain domain = DataModellingDomain.builder()
 *     .id(UUID.randomUUID().toString())
 *     .name("customer-domain")
 *     .description("All customer-related systems and data")
 *     .owner("data-team")
 *     .addSystem("crm-system")
 *     .addAsset(asset)
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataModellingDomain {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("owner")
    private String owner;

    @JsonProperty("systems")
    private List<String> systems; // System names/IDs

    @JsonProperty("assets")
    private List<DataModellingDomainAsset> assets;

    @JsonProperty("owner_email")
    private String ownerEmail;

    @JsonProperty("contact_details")
    private Object contactDetails;

    @JsonProperty("tags")
    private List<Object> tags; // Mixed tag types

    @JsonProperty("customProperties")
    private List<Object> customProperties; // CustomProperty[]

    @JsonProperty("sla")
    private Object sla; // SLA details

    @JsonProperty("notes")
    private String notes;

    @JsonProperty("version")
    private String version;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DataModellingDomain() {
    }

    private DataModellingDomain(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.owner = builder.owner;
        this.systems = builder.systems;
        this.assets = builder.assets;
        this.ownerEmail = builder.ownerEmail;
        this.contactDetails = builder.contactDetails;
        this.tags = builder.tags;
        this.customProperties = builder.customProperties;
        this.sla = builder.sla;
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
        private String description;
        private String owner;
        private List<String> systems;
        private List<DataModellingDomainAsset> assets;
        private String ownerEmail;
        private Object contactDetails;
        private List<Object> tags;
        private List<Object> customProperties;
        private Object sla;
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

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder addSystem(String system) {
            if (this.systems == null) {
                this.systems = new ArrayList<>();
            }
            this.systems.add(system);
            return this;
        }

        public Builder systems(List<String> systems) {
            this.systems = systems;
            return this;
        }

        public Builder addAsset(DataModellingDomainAsset asset) {
            if (this.assets == null) {
                this.assets = new ArrayList<>();
            }
            this.assets.add(asset);
            return this;
        }

        public Builder assets(List<DataModellingDomainAsset> assets) {
            this.assets = assets;
            return this;
        }

        public Builder ownerEmail(String ownerEmail) {
            this.ownerEmail = ownerEmail;
            return this;
        }

        public Builder contactDetails(Object contactDetails) {
            this.contactDetails = contactDetails;
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

        public Builder sla(Object sla) {
            this.sla = sla;
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

        public DataModellingDomain build() {
            Objects.requireNonNull(name, "Domain name is required");
            return new DataModellingDomain(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getOwner() { return owner; }
    public List<String> getSystems() { return systems; }
    public List<DataModellingDomainAsset> getAssets() { return assets; }
    public String getOwnerEmail() { return ownerEmail; }
    public Object getContactDetails() { return contactDetails; }
    public List<Object> getTags() { return tags; }
    public List<Object> getCustomProperties() { return customProperties; }
    public Object getSla() { return sla; }
    public String getNotes() { return notes; }
    public String getVersion() { return version; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setOwner(String owner) { this.owner = owner; }
    public void setSystems(List<String> systems) { this.systems = systems; }
    public void setAssets(List<DataModellingDomainAsset> assets) { this.assets = assets; }
    public void setNotes(String notes) { this.notes = notes; }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataModellingDomain domain = (DataModellingDomain) o;
        return Objects.equals(id, domain.id) &&
                Objects.equals(name, domain.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "DataModellingDomain{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", owner='" + owner + '\'' +
                ", systems=" + (systems != null ? systems.size() : 0) +
                '}';
    }

    // ── Inner class: DomainAsset ──────────────────────────────────────────────

    /**
     * Represents an asset within a domain.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Builder$DomainAsset {
        // Placeholder for future asset structure
    }
}
