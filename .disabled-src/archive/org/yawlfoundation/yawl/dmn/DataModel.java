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

package org.yawlfoundation.yawl.dmn;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Top-level data model container, mirroring the {@code DataModel} struct from the
 * {@code data-modelling-sdk} Rust crate.
 *
 * <p>A {@code DataModel} aggregates a named set of {@link DmnTable} definitions
 * (entities with typed columns) and the directed {@link DmnRelationship} edges that
 * connect them, forming an entity-relationship schema for DMN decision inputs.</p>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * DataModel model = DataModel.builder("LoanEligibility")
 *     .table(DmnTable.builder("Applicant")
 *         .column(DmnColumn.of("age", "integer").build())
 *         .column(DmnColumn.of("income", "double").build())
 *         .build())
 *     .table(DmnTable.builder("Product")
 *         .column(DmnColumn.of("productType", "string").build())
 *         .column(DmnColumn.of("riskBand", "string").build())
 *         .build())
 *     .relationship(DmnRelationship.builder("applicant-to-product")
 *         .fromTable("Applicant")
 *         .toTable("Product")
 *         .sourceCardinality(EndpointCardinality.ONE_ONE)
 *         .targetCardinality(EndpointCardinality.ZERO_MANY)
 *         .build())
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnTable
 * @see DmnRelationship
 * @see EndpointCardinality
 */
public final class DataModel {

    private final String name;
    private final @Nullable String description;
    private final List<DmnTable> tables;
    private final List<DmnRelationship> relationships;
    private final Map<String, DmnTable> tableIndex;

    private DataModel(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.tables = List.copyOf(builder.tables);
        this.relationships = List.copyOf(builder.relationships);
        Map<String, DmnTable> idx = new LinkedHashMap<>();
        for (DmnTable t : this.tables) {
            idx.put(t.getName(), t);
        }
        this.tableIndex = Collections.unmodifiableMap(idx);
    }

    /**
     * Returns the model identifier.
     *
     * @return the model name; never null or blank
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the optional model description.
     *
     * @return the description, or null if not set
     */
    public @Nullable String getDescription() {
        return description;
    }

    /**
     * Returns all tables in declaration order.
     *
     * @return an unmodifiable list of tables; never null
     */
    public List<DmnTable> getTables() {
        return tables;
    }

    /**
     * Returns all directed relationships in declaration order.
     *
     * @return an unmodifiable list of relationships; never null
     */
    public List<DmnRelationship> getRelationships() {
        return relationships;
    }

    /**
     * Returns the table with the given name, if present.
     *
     * @param name  the table name; must not be null
     * @return the table, or empty if not found
     */
    public Optional<DmnTable> getTable(String name) {
        return Optional.ofNullable(tableIndex.get(name));
    }

    /**
     * Returns whether this model contains a table with the given name.
     *
     * @param name  the table name; must not be null
     * @return {@code true} if the table exists
     */
    public boolean hasTable(String name) {
        return tableIndex.containsKey(name);
    }

    /**
     * Returns the number of tables in this model.
     *
     * @return the table count
     */
    public int tableCount() {
        return tables.size();
    }

    /**
     * Returns the number of relationships in this model.
     *
     * @return the relationship count
     */
    public int relationshipCount() {
        return relationships.size();
    }

    /**
     * Returns all relationships where the given table is the source (from) entity.
     *
     * @param tableName  the source table name; must not be null
     * @return an unmodifiable list of outbound relationships; never null
     */
    public List<DmnRelationship> getRelationshipsFrom(String tableName) {
        List<DmnRelationship> result = new ArrayList<>();
        for (DmnRelationship r : relationships) {
            if (tableName.equals(r.getFromTable())) {
                result.add(r);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all relationships where the given table is the target (to) entity.
     *
     * @param tableName  the target table name; must not be null
     * @return an unmodifiable list of inbound relationships; never null
     */
    public List<DmnRelationship> getRelationshipsTo(String tableName) {
        List<DmnRelationship> result = new ArrayList<>();
        for (DmnRelationship r : relationships) {
            if (tableName.equals(r.getToTable())) {
                result.add(r);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the relationship with the given name, if present.
     *
     * @param name  the relationship name; must not be null
     * @return the relationship, or empty if not found
     */
    public Optional<DmnRelationship> getRelationship(String name) {
        for (DmnRelationship r : relationships) {
            if (name.equals(r.getName())) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /**
     * Validates referential integrity: every relationship's fromTable and toTable
     * must reference a table defined in this model.
     *
     * @return a list of validation errors; empty if the model is consistent
     */
    public List<String> validateIntegrity() {
        List<String> errors = new ArrayList<>();
        for (DmnRelationship r : relationships) {
            if (!tableIndex.containsKey(r.getFromTable())) {
                errors.add("Relationship '" + r.getName()
                        + "': fromTable '" + r.getFromTable() + "' is not defined");
            }
            if (!tableIndex.containsKey(r.getToTable())) {
                errors.add("Relationship '" + r.getName()
                        + "': toTable '" + r.getToTable() + "' is not defined");
            }
        }
        return errors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DataModel that)) return false;
        return Objects.equals(name, that.name)
                && Objects.equals(tables, that.tables)
                && Objects.equals(relationships, that.relationships);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, tables, relationships);
    }

    @Override
    public String toString() {
        return "DataModel{name='" + name + "', tables=" + tables.size()
                + ", relationships=" + relationships.size() + '}';
    }

    /**
     * Returns a new Builder for constructing a DataModel.
     *
     * @param name  the model identifier; must not be null or blank
     * @return a new Builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Builder for DataModel.
     */
    public static final class Builder {
        private final String name;
        private @Nullable String description;
        private final List<DmnTable> tables = new ArrayList<>();
        private final List<DmnRelationship> relationships = new ArrayList<>();

        private Builder(String name) {
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("DataModel name must not be null or blank");
            this.name = name;
        }

        /**
         * Sets the optional description.
         *
         * @param description  the description; may be null
         * @return this builder
         */
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        /**
         * Adds a table to this model.
         *
         * @param table  the table to add; must not be null
         * @return this builder
         * @throws IllegalArgumentException if a table with the same name already exists
         */
        public Builder table(DmnTable table) {
            Objects.requireNonNull(table, "table must not be null");
            for (DmnTable t : tables) {
                if (t.getName().equals(table.getName())) {
                    throw new IllegalArgumentException(
                            "Duplicate table name: '" + table.getName() + "'");
                }
            }
            tables.add(table);
            return this;
        }

        /**
         * Adds a relationship to this model.
         *
         * @param relationship  the relationship to add; must not be null
         * @return this builder
         * @throws IllegalArgumentException if a relationship with the same name already exists
         */
        public Builder relationship(DmnRelationship relationship) {
            Objects.requireNonNull(relationship, "relationship must not be null");
            for (DmnRelationship r : relationships) {
                if (r.getName().equals(relationship.getName())) {
                    throw new IllegalArgumentException(
                            "Duplicate relationship name: '" + relationship.getName() + "'");
                }
            }
            relationships.add(relationship);
            return this;
        }

        /**
         * Builds the DataModel.
         *
         * @return a new immutable DataModel; never null
         */
        public DataModel build() {
            return new DataModel(this);
        }
    }
}
