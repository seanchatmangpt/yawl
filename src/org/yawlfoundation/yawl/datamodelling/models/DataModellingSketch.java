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
 * Type-safe model for a sketch in Excalidraw format.
 *
 * <p>Represents a visual diagram (Excalidraw format) with metadata, such as
 * ER diagrams, data flow diagrams, or architecture sketches related to
 * the data model.</p>
 *
 * <p>Builder pattern usage:</p>
 * <pre>{@code
 * DataModellingSketch sketch = DataModellingSketch.builder()
 *     .id(UUID.randomUUID().toString())
 *     .title("Customer Schema ER Diagram")
 *     .format("excalidraw")
 *     .content("{\"elements\": [...], \"appState\": {...}}")
 *     .creator("jane.doe@example.com")
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataModellingSketch {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("format")
    private String format; // excalidraw, drawio, etc.

    @JsonProperty("content")
    private String content; // JSON serialized sketch data

    @JsonProperty("thumbnail")
    private String thumbnail; // Base64 encoded PNG thumbnail

    @JsonProperty("creator")
    private String creator;

    @JsonProperty("dateCreated")
    private String dateCreated;

    @JsonProperty("dateModified")
    private String dateModified;

    @JsonProperty("lastEditedBy")
    private String lastEditedBy;

    @JsonProperty("version")
    private Integer version;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("relatedTables")
    private List<String> relatedTables; // IDs of tables depicted in sketch

    @JsonProperty("relatedDomains")
    private List<String> relatedDomains; // IDs of domains depicted in sketch

    @JsonProperty("width")
    private Integer width;

    @JsonProperty("height")
    private Integer height;

    @JsonProperty("isPublic")
    private Boolean isPublic;

    @JsonProperty("customProperties")
    private List<Object> customProperties;

    @JsonProperty("notes")
    private String notes;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DataModellingSketch() {
    }

    private DataModellingSketch(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.description = builder.description;
        this.format = builder.format;
        this.content = builder.content;
        this.thumbnail = builder.thumbnail;
        this.creator = builder.creator;
        this.dateCreated = builder.dateCreated;
        this.dateModified = builder.dateModified;
        this.lastEditedBy = builder.lastEditedBy;
        this.version = builder.version;
        this.tags = builder.tags;
        this.relatedTables = builder.relatedTables;
        this.relatedDomains = builder.relatedDomains;
        this.width = builder.width;
        this.height = builder.height;
        this.isPublic = builder.isPublic;
        this.customProperties = builder.customProperties;
        this.notes = builder.notes;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String title;
        private String description;
        private String format;
        private String content;
        private String thumbnail;
        private String creator;
        private String dateCreated;
        private String dateModified;
        private String lastEditedBy;
        private Integer version;
        private List<String> tags;
        private List<String> relatedTables;
        private List<String> relatedDomains;
        private Integer width;
        private Integer height;
        private Boolean isPublic;
        private List<Object> customProperties;
        private String notes;

        public Builder id(String id) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder thumbnail(String thumbnail) {
            this.thumbnail = thumbnail;
            return this;
        }

        public Builder creator(String creator) {
            this.creator = creator;
            return this;
        }

        public Builder dateCreated(String dateCreated) {
            this.dateCreated = dateCreated;
            return this;
        }

        public Builder dateModified(String dateModified) {
            this.dateModified = dateModified;
            return this;
        }

        public Builder lastEditedBy(String lastEditedBy) {
            this.lastEditedBy = lastEditedBy;
            return this;
        }

        public Builder version(Integer version) {
            this.version = version;
            return this;
        }

        public Builder addTag(String tag) {
            if (this.tags == null) {
                this.tags = new ArrayList<>();
            }
            this.tags.add(tag);
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder addRelatedTable(String tableId) {
            if (this.relatedTables == null) {
                this.relatedTables = new ArrayList<>();
            }
            this.relatedTables.add(tableId);
            return this;
        }

        public Builder relatedTables(List<String> relatedTables) {
            this.relatedTables = relatedTables;
            return this;
        }

        public Builder addRelatedDomain(String domainId) {
            if (this.relatedDomains == null) {
                this.relatedDomains = new ArrayList<>();
            }
            this.relatedDomains.add(domainId);
            return this;
        }

        public Builder relatedDomains(List<String> relatedDomains) {
            this.relatedDomains = relatedDomains;
            return this;
        }

        public Builder width(Integer width) {
            this.width = width;
            return this;
        }

        public Builder height(Integer height) {
            this.height = height;
            return this;
        }

        public Builder isPublic(Boolean isPublic) {
            this.isPublic = isPublic;
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

        public DataModellingSketch build() {
            Objects.requireNonNull(title, "Sketch title is required");
            return new DataModellingSketch(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getFormat() { return format; }
    public String getContent() { return content; }
    public String getThumbnail() { return thumbnail; }
    public String getCreator() { return creator; }
    public String getDateCreated() { return dateCreated; }
    public String getDateModified() { return dateModified; }
    public String getLastEditedBy() { return lastEditedBy; }
    public Integer getVersion() { return version; }
    public List<String> getTags() { return tags; }
    public List<String> getRelatedTables() { return relatedTables; }
    public List<String> getRelatedDomains() { return relatedDomains; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public Boolean getIsPublic() { return isPublic; }
    public List<Object> getCustomProperties() { return customProperties; }
    public String getNotes() { return notes; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setContent(String content) { this.content = content; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    public void setDateModified(String dateModified) { this.dateModified = dateModified; }
    public void setLastEditedBy(String lastEditedBy) { this.lastEditedBy = lastEditedBy; }
    public void setVersion(Integer version) { this.version = version; }
    public void setNotes(String notes) { this.notes = notes; }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataModellingSketch sketch = (DataModellingSketch) o;
        return Objects.equals(id, sketch.id) &&
                Objects.equals(title, sketch.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title);
    }

    @Override
    public String toString() {
        return "DataModellingSketch{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", format='" + format + '\'' +
                ", version=" + version +
                '}';
    }
}
