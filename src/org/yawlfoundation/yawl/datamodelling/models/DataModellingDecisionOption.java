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
 * Type-safe model for a decision option (alternative) in an MADR record.
 *
 * <p>Represents one option considered when making an architecture decision,
 * with pros, cons, and evaluation criteria.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataModellingDecisionOption {

    @JsonProperty("id")
    private String id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("pros")
    private List<String> pros;

    @JsonProperty("cons")
    private List<String> cons;

    @JsonProperty("selected")
    private Boolean selected;

    @JsonProperty("score")
    private Double score;

    // ── Constructors ──────────────────────────────────────────────────────────

    public DataModellingDecisionOption() {
    }

    private DataModellingDecisionOption(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.description = builder.description;
        this.pros = builder.pros;
        this.cons = builder.cons;
        this.selected = builder.selected;
        this.score = builder.score;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String title;
        private String description;
        private List<String> pros;
        private List<String> cons;
        private Boolean selected;
        private Double score;

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

        public Builder addPro(String pro) {
            if (this.pros == null) {
                this.pros = new ArrayList<>();
            }
            this.pros.add(pro);
            return this;
        }

        public Builder pros(List<String> pros) {
            this.pros = pros;
            return this;
        }

        public Builder addCon(String con) {
            if (this.cons == null) {
                this.cons = new ArrayList<>();
            }
            this.cons.add(con);
            return this;
        }

        public Builder cons(List<String> cons) {
            this.cons = cons;
            return this;
        }

        public Builder selected(Boolean selected) {
            this.selected = selected;
            return this;
        }

        public Builder score(Double score) {
            this.score = score;
            return this;
        }

        public DataModellingDecisionOption build() {
            Objects.requireNonNull(title, "Option title is required");
            return new DataModellingDecisionOption(this);
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getPros() { return pros; }
    public List<String> getCons() { return cons; }
    public Boolean getSelected() { return selected; }
    public Double getScore() { return score; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPros(List<String> pros) { this.pros = pros; }
    public void setCons(List<String> cons) { this.cons = cons; }
    public void setSelected(Boolean selected) { this.selected = selected; }
    public void setScore(Double score) { this.score = score; }

    // ── equals / hashCode / toString ──────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataModellingDecisionOption option = (DataModellingDecisionOption) o;
        return Objects.equals(id, option.id) &&
                Objects.equals(title, option.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, title);
    }

    @Override
    public String toString() {
        return "DataModellingDecisionOption{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", selected=" + selected +
                '}';
    }
}
