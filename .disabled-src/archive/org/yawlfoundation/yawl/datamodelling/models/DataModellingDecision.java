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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Type-safe model for an architecture decision record (MADR format).
 *
 * <p>Represents a decision in the Markdown Architecture Decision Record (MADR)
 * format, documenting the decision, context, options considered, and rationale.</p>
 *
 * <p>Builder pattern usage:</p>
 * <pre>{@code
 * DataModellingDecision decision = DataModellingDecision.builder()
 *     .id(UUID.randomUUID().toString())
 *     .title("Use PostgreSQL for customer data warehouse")
 *     .status("accepted")
 *     .context("Need a reliable OLAP database for analytics")
 *     .decision("PostgreSQL with columnar extension")
 *     .consequences("Simpler stack, good performance for our workloads")
 *     .addAuthor("john.doe@example.com")
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataModellingDecision {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("status")
    private String status; // accepted, rejected, deprecated, superseded

    @JsonProperty("context")
    private String context;

    @JsonProperty("decision")
    private String decision;

    @JsonProperty("consequences")
    private String consequences;

    @JsonProperty("rationale")
    private String rationale;

    @JsonProperty("options")
    private List<DataModellingDecisionOption> options;

    @JsonProperty("authors")
    private List<String> authors;

    @JsonProperty("reviewers")
    private List<String> reviewers;

    @JsonProperty("dateCreated")
    private String dateCreated;

    @JsonProperty("dateModified")
    private String dateModified;

    @JsonProperty("affects")
    private List<String> affects; // IDs of affected entities (tables, etc.)

    @JsonProperty("tags")
    private List<Object> tags;

    @JsonProperty("customProperties")
    private List<Object> customProperties;

    @JsonProperty("notes")
    private String notes;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DataModellingDecision() {
    }

    private DataModellingDecision(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.status = builder.status;
        this.context = builder.context;
        this.decision = builder.decision;
        this.consequences = builder.consequences;
        this.rationale = builder.rationale;
        this.options = builder.options;
        this.authors = builder.authors;
        this.reviewers = builder.reviewers;
        this.dateCreated = builder.dateCreated;
        this.dateModified = builder.dateModified;
        this.affects = builder.affects;
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
        private String title;
        private String status;
        private String context;
        private String decision;
        private String consequences;
        private String rationale;
        private List<DataModellingDecisionOption> options;
        private List<String> authors;
        private List<String> reviewers;
        private String dateCreated;
        private String dateModified;
        private List<String> affects;
        private List<Object> tags;
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

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public Builder decision(String decision) {
            this.decision = decision;
            return this;
        }

        public Builder consequences(String consequences) {
            this.consequences = consequences;
            return this;
        }

        public Builder rationale(String rationale) {
            this.rationale = rationale;
            return this;
        }

        public Builder addOption(DataModellingDecisionOption option) {
            if (this.options == null) {
                this.options = new ArrayList<>();
            }
            this.options.add(option);
            return this;
        }

        public Builder options(List<DataModellingDecisionOption> options) {
            this.options = options;
            return this;
        }

        public Builder addAuthor(String author) {
            if (this.authors == null) {
                this.authors = new ArrayList<>();
            }
            this.authors.add(author);
            return this;
        }

        public Builder authors(List<String> authors) {
            this.authors = authors;
            return this;
        }

        public Builder addReviewer(String reviewer) {
            if (this.reviewers == null) {
                this.reviewers = new ArrayList<>();
            }
            this.reviewers.add(reviewer);
            return this;
        }

        public Builder reviewers(List<String> reviewers) {
            this.reviewers = reviewers;
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

        public Builder addAffects(String entityId) {
            if (this.affects == null) {
                this.affects = new ArrayList<>();
            }
            this.affects.add(entityId);
            return this;
        }

        public Builder affects(List<String> affects) {
            this.affects = affects;
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

        public DataModellingDecision build() {
            Objects.requireNonNull(title, "Decision title is required");
            return new DataModellingDecision(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getContext() { return context; }
    public String getDecision() { return decision; }
    public String getConsequences() { return consequences; }
    public String getRationale() { return rationale; }
    public List<DataModellingDecisionOption> getOptions() { return options; }
    public List<String> getAuthors() { return authors; }
    public List<String> getReviewers() { return reviewers; }
    public String getDateCreated() { return dateCreated; }
    public String getDateModified() { return dateModified; }
    public List<String> getAffects() { return affects; }
    public List<Object> getTags() { return tags; }
    public List<Object> getCustomProperties() { return customProperties; }
    public String getNotes() { return notes; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setStatus(String status) { this.status = status; }
    public void setContext(String context) { this.context = context; }
    public void setDecision(String decision) { this.decision = decision; }
    public void setConsequences(String consequences) { this.consequences = consequences; }
    public void setNotes(String notes) { this.notes = notes; }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataModellingDecision decision = (DataModellingDecision) o;
        return Objects.equals(id, decision.id) &&
                Objects.equals(title, decision.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title);
    }

    @Override
    public String toString() {
        return "DataModellingDecision{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
