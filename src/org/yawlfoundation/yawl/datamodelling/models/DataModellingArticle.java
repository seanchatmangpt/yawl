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
 * Type-safe model for a knowledge base article.
 *
 * <p>Represents a documentation article that explains data modeling concepts,
 * guidelines, or best practices related to the data workspace.</p>
 *
 * <p>Builder pattern usage:</p>
 * <pre>{@code
 * DataModellingArticle article = DataModellingArticle.builder()
 *     .id(UUID.randomUUID().toString())
 *     .title("Customer Data Model Guidelines")
 *     .category("best-practices")
 *     .content("# Guidelines\n\nAll customer tables should...")
 *     .author("john.doe@example.com")
 *     .addTag("customer")
 *     .addTag("data-governance")
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataModellingArticle {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("slug")
    private String slug; // URL-friendly identifier

    @JsonProperty("category")
    private String category;

    @JsonProperty("content")
    private String content; // Markdown content

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("author")
    private String author;

    @JsonProperty("contributors")
    private List<String> contributors;

    @JsonProperty("dateCreated")
    private String dateCreated;

    @JsonProperty("dateModified")
    private String dateModified;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("relatedArticles")
    private List<String> relatedArticles; // IDs of related articles

    @JsonProperty("relatedTables")
    private List<String> relatedTables; // IDs of related tables

    @JsonProperty("status")
    private String status; // draft, published, archived

    @JsonProperty("viewCount")
    private Integer viewCount;

    @JsonProperty("customProperties")
    private List<Object> customProperties;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DataModellingArticle() {
    }

    private DataModellingArticle(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.slug = builder.slug;
        this.category = builder.category;
        this.content = builder.content;
        this.summary = builder.summary;
        this.author = builder.author;
        this.contributors = builder.contributors;
        this.dateCreated = builder.dateCreated;
        this.dateModified = builder.dateModified;
        this.tags = builder.tags;
        this.relatedArticles = builder.relatedArticles;
        this.relatedTables = builder.relatedTables;
        this.status = builder.status;
        this.viewCount = builder.viewCount;
        this.customProperties = builder.customProperties;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String title;
        private String slug;
        private String category;
        private String content;
        private String summary;
        private String author;
        private List<String> contributors;
        private String dateCreated;
        private String dateModified;
        private List<String> tags;
        private List<String> relatedArticles;
        private List<String> relatedTables;
        private String status;
        private Integer viewCount;
        private List<Object> customProperties;

        public Builder id(String id) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder slug(String slug) {
            this.slug = slug;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder addContributor(String contributor) {
            if (this.contributors == null) {
                this.contributors = new ArrayList<>();
            }
            this.contributors.add(contributor);
            return this;
        }

        public Builder contributors(List<String> contributors) {
            this.contributors = contributors;
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

        public Builder addRelatedArticle(String articleId) {
            if (this.relatedArticles == null) {
                this.relatedArticles = new ArrayList<>();
            }
            this.relatedArticles.add(articleId);
            return this;
        }

        public Builder relatedArticles(List<String> relatedArticles) {
            this.relatedArticles = relatedArticles;
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

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder viewCount(Integer viewCount) {
            this.viewCount = viewCount;
            return this;
        }

        public Builder addCustomProperty(Object customProperty) {
            if (this.customProperties == null) {
                this.customProperties = new ArrayList<>();
            }
            this.customProperties.add(customProperty);
            return this;
        }

        public DataModellingArticle build() {
            Objects.requireNonNull(title, "Article title is required");
            return new DataModellingArticle(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getCategory() { return category; }
    public String getContent() { return content; }
    public String getSummary() { return summary; }
    public String getAuthor() { return author; }
    public List<String> getContributors() { return contributors; }
    public String getDateCreated() { return dateCreated; }
    public String getDateModified() { return dateModified; }
    public List<String> getTags() { return tags; }
    public List<String> getRelatedArticles() { return relatedArticles; }
    public List<String> getRelatedTables() { return relatedTables; }
    public String getStatus() { return status; }
    public Integer getViewCount() { return viewCount; }
    public List<Object> getCustomProperties() { return customProperties; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setSlug(String slug) { this.slug = slug; }
    public void setCategory(String category) { this.category = category; }
    public void setContent(String content) { this.content = content; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setAuthor(String author) { this.author = author; }
    public void setDateModified(String dateModified) { this.dateModified = dateModified; }
    public void setStatus(String status) { this.status = status; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataModellingArticle article = (DataModellingArticle) o;
        return Objects.equals(id, article.id) &&
                Objects.equals(title, article.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title);
    }

    @Override
    public String toString() {
        return "DataModellingArticle{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", category='" + category + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
