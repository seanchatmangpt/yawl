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

/**
 * A directed relationship between two {@link DmnTable} entities in a {@link DataModel}.
 *
 * <p>Mirrors the {@code Relationship} struct from the {@code data-modelling-sdk} Rust crate,
 * extended with {@link EndpointCardinality} on both the source and target ends
 * (crow's feet notation).</p>
 *
 * <h2>Min/max semantics in DMN</h2>
 * <p>The cardinality at each endpoint constrains how many instances of that entity participate
 * in a decision:</p>
 * <ul>
 *   <li>Source {@link EndpointCardinality#ONE_ONE ONE_ONE} → exactly one input row required</li>
 *   <li>Target {@link EndpointCardinality#ZERO_MANY ZERO_MANY} → multiple output rows possible</li>
 *   <li>Source {@link EndpointCardinality#ONE_MANY ONE_MANY} → multi-value COLLECT input</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * DmnRelationship rel = DmnRelationship.builder("applicant-to-product")
 *     .fromTable("Applicant")
 *     .toTable("Product")
 *     .sourceCardinality(EndpointCardinality.ONE_ONE)
 *     .targetCardinality(EndpointCardinality.ZERO_MANY)
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see EndpointCardinality
 * @see DataModel
 */
public final class DmnRelationship {

    private final String name;
    private final String fromTable;
    private final String toTable;
    private final EndpointCardinality sourceCardinality;
    private final EndpointCardinality targetCardinality;
    private final @Nullable String fromColumn;
    private final @Nullable String toColumn;

    private DmnRelationship(Builder builder) {
        this.name = builder.name;
        this.fromTable = builder.fromTable;
        this.toTable = builder.toTable;
        this.sourceCardinality = builder.sourceCardinality;
        this.targetCardinality = builder.targetCardinality;
        this.fromColumn = builder.fromColumn;
        this.toColumn = builder.toColumn;
    }

    /**
     * Returns the relationship identifier.
     *
     * @return the name; never null
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the source table name.
     *
     * @return the source table; never null
     */
    public String getFromTable() {
        return fromTable;
    }

    /**
     * Returns the target table name.
     *
     * @return the target table; never null
     */
    public String getToTable() {
        return toTable;
    }

    /**
     * Returns the cardinality at the source (from) endpoint.
     *
     * <p>Describes how many source entity instances participate. This maps to
     * the <em>min</em> (mandatory vs optional) and <em>max</em> (one vs many)
     * multiplicity at the source end.</p>
     *
     * @return the source cardinality; never null
     */
    public EndpointCardinality getSourceCardinality() {
        return sourceCardinality;
    }

    /**
     * Returns the cardinality at the target (to) endpoint.
     *
     * <p>Describes how many target entity instances are associated. When
     * {@link EndpointCardinality#isMultiValued()}, the relationship supports
     * COLLECT-style multi-value DMN outputs.</p>
     *
     * @return the target cardinality; never null
     */
    public EndpointCardinality getTargetCardinality() {
        return targetCardinality;
    }

    /**
     * Returns the optional foreign key column on the source table.
     *
     * @return the source column, or null if not specified
     */
    public @Nullable String getFromColumn() {
        return fromColumn;
    }

    /**
     * Returns the optional referenced column on the target table.
     *
     * @return the target column, or null if not specified
     */
    public @Nullable String getToColumn() {
        return toColumn;
    }

    /**
     * Returns the minimum number of source participants ({@code sourceCardinality.getMin()}).
     *
     * @return min source participation count
     */
    public int sourceMin() {
        return sourceCardinality.getMin();
    }

    /**
     * Returns the maximum number of source participants ({@code sourceCardinality.getMax()}).
     *
     * @return max source participation count ({@link EndpointCardinality#UNBOUNDED} if unbounded)
     */
    public int sourceMax() {
        return sourceCardinality.getMax();
    }

    /**
     * Returns the minimum number of target participants ({@code targetCardinality.getMin()}).
     *
     * @return min target participation count
     */
    public int targetMin() {
        return targetCardinality.getMin();
    }

    /**
     * Returns the maximum number of target participants ({@code targetCardinality.getMax()}).
     *
     * @return max target participation count ({@link EndpointCardinality#UNBOUNDED} if unbounded)
     */
    public int targetMax() {
        return targetCardinality.getMax();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmnRelationship that)) return false;
        return Objects.equals(name, that.name)
                && Objects.equals(fromTable, that.fromTable)
                && Objects.equals(toTable, that.toTable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, fromTable, toTable);
    }

    @Override
    public String toString() {
        return "DmnRelationship{'" + fromTable + "' " + sourceCardinality.getNotation()
                + " → " + targetCardinality.getNotation() + " '" + toTable + "'}";
    }

    /**
     * Returns a new Builder for constructing a DmnRelationship.
     *
     * @param name  the relationship identifier; must not be null or blank
     * @return a new Builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Builder for DmnRelationship.
     */
    public static final class Builder {
        private final String name;
        private String fromTable = "";
        private String toTable = "";
        private EndpointCardinality sourceCardinality = EndpointCardinality.ONE_ONE;
        private EndpointCardinality targetCardinality = EndpointCardinality.ZERO_MANY;
        private @Nullable String fromColumn;
        private @Nullable String toColumn;

        private Builder(String name) {
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("Relationship name must not be null or blank");
            this.name = name;
        }

        /**
         * Sets the source table name.
         *
         * @param fromTable  the source table; must not be null or blank
         * @return this builder
         */
        public Builder fromTable(String fromTable) {
            this.fromTable = Objects.requireNonNull(fromTable);
            return this;
        }

        /**
         * Sets the target table name.
         *
         * @param toTable  the target table; must not be null or blank
         * @return this builder
         */
        public Builder toTable(String toTable) {
            this.toTable = Objects.requireNonNull(toTable);
            return this;
        }

        /**
         * Sets the source endpoint cardinality (min/max at source).
         *
         * @param cardinality  the source cardinality; must not be null
         * @return this builder
         */
        public Builder sourceCardinality(EndpointCardinality cardinality) {
            this.sourceCardinality = Objects.requireNonNull(cardinality);
            return this;
        }

        /**
         * Sets the target endpoint cardinality (min/max at target).
         *
         * @param cardinality  the target cardinality; must not be null
         * @return this builder
         */
        public Builder targetCardinality(EndpointCardinality cardinality) {
            this.targetCardinality = Objects.requireNonNull(cardinality);
            return this;
        }

        /**
         * Sets the optional foreign key column on the source table.
         *
         * @param fromColumn  the source column; may be null
         * @return this builder
         */
        public Builder fromColumn(@Nullable String fromColumn) {
            this.fromColumn = fromColumn;
            return this;
        }

        /**
         * Sets the optional referenced column on the target table.
         *
         * @param toColumn  the target column; may be null
         * @return this builder
         */
        public Builder toColumn(@Nullable String toColumn) {
            this.toColumn = toColumn;
            return this;
        }

        /**
         * Builds the DmnRelationship.
         *
         * @return a new immutable DmnRelationship; never null
         * @throws IllegalStateException if fromTable or toTable is not set
         */
        public DmnRelationship build() {
            if (fromTable.isBlank()) throw new IllegalStateException("fromTable must be set");
            if (toTable.isBlank()) throw new IllegalStateException("toTable must be set");
            return new DmnRelationship(this);
        }
    }
}
