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

import java.util.Objects;
import java.util.Map;
import org.yawlfoundation.yawl.integration.util.ParameterValidator;
import org.yawlfoundation.yawl.integration.util.SkillLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A typed column in a DMN data model table.
 *
 * <p>Mirrors the {@code Column} struct from the {@code data-modelling-sdk} Rust crate.
 * Each column has a name, a DMN type reference, an optional description, and a flag
 * indicating whether it is required.</p>
 *
 * <h2>DMN type references</h2>
 * <ul>
 *   <li>{@code string} — text values</li>
 *   <li>{@code integer} — whole numbers</li>
 *   <li>{@code double} — floating-point numbers</li>
 *   <li>{@code boolean} — true/false</li>
 *   <li>{@code date} — ISO 8601 date</li>
 *   <li>{@code time} — ISO 8601 time</li>
 *   <li>{@code dateTime} — ISO 8601 date-time</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnTable
 * @see DataModel
 */
public final class DmnColumn {

    private static final Logger log = LoggerFactory.getLogger(DmnColumn.class);
    private static final SkillLogger skillLogger = SkillLogger.forSkill("dmn-column", "DMN_Column");

    private final String name;
    private final String typeRef;
    private final boolean required;
    private final @Nullable String description;

    private DmnColumn(Builder builder) {
        this.name = builder.name;
        this.typeRef = builder.typeRef;
        this.required = builder.required;
        this.description = builder.description;
    }

    /**
     * Returns the column name (used as the variable name in DMN expressions).
     *
     * @return the column name; never null or blank
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the DMN type reference (e.g., {@code "string"}, {@code "integer"}).
     *
     * @return the type reference; never null
     */
    public String getTypeRef() {
        return typeRef;
    }

    /**
     * Returns whether a value for this column is required (non-null).
     *
     * @return {@code true} if required
     */
    public boolean isRequired() {
        return required;
    }

    /**
     * Returns the optional human-readable description.
     *
     * @return the description, or null if not set
     */
    public @Nullable String getDescription() {
        return description;
    }

    /**
     * Returns a new Builder for constructing a DmnColumn.
     *
     * @param name     the column name; must not be null or blank
     * @param typeRef  the DMN type reference; must not be null
     * @return a new Builder
     */
    public static Builder of(String name, String typeRef) {
        return new Builder(name, typeRef);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmnColumn that)) return false;
        return required == that.required
                && Objects.equals(name, that.name)
                && Objects.equals(typeRef, that.typeRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, typeRef, required);
    }

    @Override
    public String toString() {
        return "DmnColumn{name='" + name + "', typeRef='" + typeRef + "', required=" + required + '}';
    }

    /**
     * Builder for DmnColumn.
     */
    public static final class Builder {
        private final String name;
        private final String typeRef;
        private boolean required = true;
        private @Nullable String description;

        private Builder(String name, String typeRef) {
            this.name = ParameterValidator.validateRequired(Map.of("name", name), "name",
                    "Column name must not be null or blank");
            this.typeRef = ParameterValidator.validateRequired(Map.of("typeRef", typeRef), "typeRef",
                    "Column typeRef must not be null or blank");
        }

        /**
         * Sets whether this column is required.
         *
         * @param required  {@code true} to require a non-null value
         * @return this builder
         */
        public Builder required(boolean required) {
            this.required = required;
            return this;
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
         * Builds the DmnColumn.
         *
         * @return a new immutable DmnColumn; never null
         */
        public DmnColumn build() {
            return new DmnColumn(this);
        }
    }
}
