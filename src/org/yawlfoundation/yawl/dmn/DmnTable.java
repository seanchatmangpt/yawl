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
import org.yawlfoundation.yawl.integration.util.ParameterValidator;
import org.yawlfoundation.yawl.integration.util.SkillLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A named table of typed columns in a DMN data model.
 *
 * <p>Mirrors the {@code Table} struct from the {@code data-modelling-sdk} Rust crate.
 * A table groups related {@link DmnColumn} definitions that together describe the
 * input or output schema for a set of DMN decisions.</p>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * DmnTable applicant = DmnTable.builder("Applicant")
 *     .column(DmnColumn.of("age", "integer").build())
 *     .column(DmnColumn.of("income", "double").build())
 *     .column(DmnColumn.of("employmentStatus", "string")
 *         .required(false).build())
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnColumn
 * @see DataModel
 */
public final class DmnTable {

    private static final Logger log = LoggerFactory.getLogger(DmnTable.class);
    private static final SkillLogger skillLogger = SkillLogger.forSkill("dmn-table", "DMN_Table");

    private final String name;
    private final @Nullable String description;
    private final List<DmnColumn> columns;
    private final Map<String, DmnColumn> columnIndex;

    private DmnTable(Builder builder) {
        skillLogger.debug("Creating DmnTable '{}'", builder.name);
        this.name = builder.name;
        this.description = builder.description;
        this.columns = List.copyOf(builder.columns);
        Map<String, DmnColumn> idx = new LinkedHashMap<>();
        for (DmnColumn col : this.columns) {
            idx.put(col.getName(), col);
        }
        this.columnIndex = Collections.unmodifiableMap(idx);

        skillLogger.debug("Created DmnTable '{}' with {} columns", this.name, this.columns.size());
    }

    /**
     * Returns the table name (used as the entity identifier in the data model).
     *
     * @return the table name; never null or blank
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the optional description.
     *
     * @return the description, or null if not set
     */
    public @Nullable String getDescription() {
        return description;
    }

    /**
     * Returns all columns in declaration order.
     *
     * @return an unmodifiable list of columns; never null
     */
    public List<DmnColumn> getColumns() {
        return columns;
    }

    /**
     * Returns the column with the given name, if present.
     *
     * @param name  the column name; must not be null
     * @return the column, or empty if not found
     */
    public Optional<DmnColumn> getColumn(String name) {
        return Optional.ofNullable(columnIndex.get(name));
    }

    /**
     * Returns whether this table defines a column with the given name.
     *
     * @param name  the column name; must not be null
     * @return {@code true} if the column exists
     */
    public boolean hasColumn(String name) {
        return columnIndex.containsKey(name);
    }

    /**
     * Returns the number of columns in this table.
     *
     * @return the column count
     */
    public int columnCount() {
        return columns.size();
    }

    /**
     * Validates a row map against this table's column definitions.
     *
     * <p>Checks that all required columns are present and non-null.</p>
     *
     * @param row  the variable name → value map; must not be null
     * @return a list of validation errors (empty if valid)
     */
    public List<String> validateRow(Map<String, Object> row) {
        List<String> errors = new ArrayList<>();
        for (DmnColumn col : columns) {
            if (col.isRequired() && !row.containsKey(col.getName())) {
                errors.add("Required column '" + col.getName() + "' is missing from row");
            } else if (col.isRequired() && row.get(col.getName()) == null) {
                errors.add("Required column '" + col.getName() + "' is null");
            }
        }
        return errors;
    }

    /**
     * Returns a new Builder for constructing a DmnTable.
     *
     * @param name  the table name; must not be null or blank
     * @return a new Builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmnTable that)) return false;
        return Objects.equals(name, that.name) && Objects.equals(columns, that.columns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, columns);
    }

    @Override
    public String toString() {
        return "DmnTable{name='" + name + "', columns=" + columns.size() + '}';
    }

    /**
     * Builder for DmnTable.
     */
    public static final class Builder {
        private final String name;
        private @Nullable String description;
        private final List<DmnColumn> columns = new ArrayList<>();

        private Builder(String name) {
            this.name = ParameterValidator.validateRequired(Map.of("name", name), "name",
                    "Table name must not be null or blank");
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
         * Adds a column to this table.
         *
         * @param column  the column to add; must not be null
         * @return this builder
         */
        public Builder column(DmnColumn column) {
            ParameterValidator.validateNotNull(column, "column");
            columns.add(column);
            skillLogger.debug("Added column '{}' to DmnTable '{}'", column.getName(), this.name);
            return this;
        }

        /**
         * Builds the DmnTable.
         *
         * @return a new immutable DmnTable; never null
         */
        public DmnTable build() {
            return new DmnTable(this);
        }
    }
}
