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
 * Type-safe model for a data modelling workspace in ODCS v3.1.0 format.
 *
 * <p>A workspace is a container for related data models, including tables,
 * relationships, domains, decision records, articles, and sketches.</p>
 *
 * <p>Builder pattern usage:</p>
 * <pre>{@code
 * DataModellingWorkspace ws = DataModellingWorkspace.builder()
 *     .id(UUID.randomUUID().toString())
 *     .name("customer-analytics")
 *     .description("Analytics workspace for customer data")
 *     .addTable(customerTable)
 *     .addRelationship(relationship)
 *     .addDomain(domain)
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataModellingWorkspace {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("tables")
    private List<DataModellingTable> tables;

    @JsonProperty("relationships")
    private List<DataModellingRelationship> relationships;

    @JsonProperty("domains")
    private List<DataModellingDomain> domains;

    @JsonProperty("decisions")
    private List<DataModellingDecision> decisions;

    @JsonProperty("articles")
    private List<DataModellingArticle> articles;

    @JsonProperty("sketches")
    private List<DataModellingSketch> sketches;

    @JsonProperty("version")
    private String version;

    @JsonProperty("metadata")
    private Object metadata; // Custom workspace metadata

    @JsonProperty("tags")
    private List<Object> tags; // Mixed tag types

    @JsonProperty("customProperties")
    private List<Object> customProperties; // CustomProperty[]

    // ── Constructors ──────────────────────────────────────────────────────────

    public DataModellingWorkspace() {
    }

    private DataModellingWorkspace(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.tables = builder.tables;
        this.relationships = builder.relationships;
        this.domains = builder.domains;
        this.decisions = builder.decisions;
        this.articles = builder.articles;
        this.sketches = builder.sketches;
        this.version = builder.version;
        this.metadata = builder.metadata;
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
        private String description;
        private List<DataModellingTable> tables;
        private List<DataModellingRelationship> relationships;
        private List<DataModellingDomain> domains;
        private List<DataModellingDecision> decisions;
        private List<DataModellingArticle> articles;
        private List<DataModellingSketch> sketches;
        private String version;
        private Object metadata;
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

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addTable(DataModellingTable table) {
            if (this.tables == null) {
                this.tables = new ArrayList<>();
            }
            this.tables.add(table);
            return this;
        }

        public Builder tables(List<DataModellingTable> tables) {
            this.tables = tables;
            return this;
        }

        public Builder addRelationship(DataModellingRelationship relationship) {
            if (this.relationships == null) {
                this.relationships = new ArrayList<>();
            }
            this.relationships.add(relationship);
            return this;
        }

        public Builder relationships(List<DataModellingRelationship> relationships) {
            this.relationships = relationships;
            return this;
        }

        public Builder addDomain(DataModellingDomain domain) {
            if (this.domains == null) {
                this.domains = new ArrayList<>();
            }
            this.domains.add(domain);
            return this;
        }

        public Builder domains(List<DataModellingDomain> domains) {
            this.domains = domains;
            return this;
        }

        public Builder addDecision(DataModellingDecision decision) {
            if (this.decisions == null) {
                this.decisions = new ArrayList<>();
            }
            this.decisions.add(decision);
            return this;
        }

        public Builder decisions(List<DataModellingDecision> decisions) {
            this.decisions = decisions;
            return this;
        }

        public Builder addArticle(DataModellingArticle article) {
            if (this.articles == null) {
                this.articles = new ArrayList<>();
            }
            this.articles.add(article);
            return this;
        }

        public Builder articles(List<DataModellingArticle> articles) {
            this.articles = articles;
            return this;
        }

        public Builder addSketch(DataModellingSketch sketch) {
            if (this.sketches == null) {
                this.sketches = new ArrayList<>();
            }
            this.sketches.add(sketch);
            return this;
        }

        public Builder sketches(List<DataModellingSketch> sketches) {
            this.sketches = sketches;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder metadata(Object metadata) {
            this.metadata = metadata;
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

        public DataModellingWorkspace build() {
            Objects.requireNonNull(name, "Workspace name is required");
            return new DataModellingWorkspace(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<DataModellingTable> getTables() { return tables; }
    public List<DataModellingRelationship> getRelationships() { return relationships; }
    public List<DataModellingDomain> getDomains() { return domains; }
    public List<DataModellingDecision> getDecisions() { return decisions; }
    public List<DataModellingArticle> getArticles() { return articles; }
    public List<DataModellingSketch> getSketches() { return sketches; }
    public String getVersion() { return version; }
    public Object getMetadata() { return metadata; }
    public List<Object> getTags() { return tags; }
    public List<Object> getCustomProperties() { return customProperties; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setTables(List<DataModellingTable> tables) { this.tables = tables; }
    public void setRelationships(List<DataModellingRelationship> relationships) { this.relationships = relationships; }
    public void setDomains(List<DataModellingDomain> domains) { this.domains = domains; }
    public void setDecisions(List<DataModellingDecision> decisions) { this.decisions = decisions; }
    public void setArticles(List<DataModellingArticle> articles) { this.articles = articles; }
    public void setSketches(List<DataModellingSketch> sketches) { this.sketches = sketches; }

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Get a table by name.
     *
     * @param tableName the table name
     * @return the table or null if not found
     */
    public DataModellingTable getTableByName(String tableName) {
        if (tables == null) return null;
        return tables.stream()
                .filter(t -> t.getName().equals(tableName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get a table by ID.
     *
     * @param tableId the table ID
     * @return the table or null if not found
     */
    public DataModellingTable getTableById(String tableId) {
        if (tables == null) return null;
        return tables.stream()
                .filter(t -> t.getId().equals(tableId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get a domain by ID.
     *
     * @param domainId the domain ID
     * @return the domain or null if not found
     */
    public DataModellingDomain getDomainById(String domainId) {
        if (domains == null) return null;
        return domains.stream()
                .filter(d -> d.getId().equals(domainId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Count tables in this workspace.
     *
     * @return the number of tables
     */
    public int getTableCount() {
        return tables != null ? tables.size() : 0;
    }

    /**
     * Count relationships in this workspace.
     *
     * @return the number of relationships
     */
    public int getRelationshipCount() {
        return relationships != null ? relationships.size() : 0;
    }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataModellingWorkspace workspace = (DataModellingWorkspace) o;
        return Objects.equals(id, workspace.id) &&
                Objects.equals(name, workspace.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "DataModellingWorkspace{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", tables=" + getTableCount() +
                ", relationships=" + getRelationshipCount() +
                '}';
    }
}
