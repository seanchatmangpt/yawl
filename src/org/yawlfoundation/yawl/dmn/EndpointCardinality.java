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

/**
 * Crow's feet endpoint cardinality for data model relationships.
 *
 * <p>Models the min/max participation of an entity in a relationship,
 * following the notation from the {@code data-modelling-sdk} (Rust) and
 * the crow's foot ERD notation used in data modelling standards.</p>
 *
 * <h2>Cardinality matrix</h2>
 * <pre>
 * Notation  | Symbol | Min | Max     | Meaning
 * ----------+--------+-----+---------+----------------------------
 * ZERO_ONE  | 0..1   | 0   | 1       | Optional, at most one
 * ONE_ONE   | 1..1   | 1   | 1       | Exactly one (mandatory)
 * ZERO_MANY | 0..*   | 0   | UNBND   | Optional, many allowed
 * ONE_MANY  | 1..*   | 1   | UNBND   | At least one, many allowed
 * </pre>
 *
 * <h2>Usage in YAWL-DMN</h2>
 * <p>In DMN context, cardinality describes how many instances of input data
 * participate in a decision: a {@code ONE_ONE} input is required and singular,
 * while {@code ZERO_MANY} allows multi-value collection inputs.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnRelationship
 * @see DataModel
 */
public enum EndpointCardinality {

    /**
     * Optional, singular (0..1). The entity may or may not participate,
     * but if it does, it contributes exactly one value.
     * Crow's foot notation: circle-dash.
     */
    ZERO_ONE(0, 1, "0..1"),

    /**
     * Mandatory, singular (1..1). The entity must participate exactly once.
     * Crow's foot notation: double-dash.
     */
    ONE_ONE(1, 1, "1..1"),

    /**
     * Optional, unbounded (0..*). The entity may not participate, or may
     * contribute many values.
     * Crow's foot notation: circle-crow's-foot.
     */
    ZERO_MANY(0, Integer.MAX_VALUE, "0..*"),

    /**
     * Mandatory, unbounded (1..*). The entity must participate at least once,
     * and may contribute many values.
     * Crow's foot notation: dash-crow's-foot.
     */
    ONE_MANY(1, Integer.MAX_VALUE, "1..*");

    /** Sentinel value representing no upper bound. */
    public static final int UNBOUNDED = Integer.MAX_VALUE;

    private final int min;
    private final int max;
    private final String notation;

    EndpointCardinality(int min, int max, String notation) {
        this.min = min;
        this.max = max;
        this.notation = notation;
    }

    /**
     * Returns the minimum number of participating instances (0 or 1).
     *
     * @return the minimum participation count; always &ge; 0
     */
    public int getMin() {
        return min;
    }

    /**
     * Returns the maximum number of participating instances.
     *
     * <p>Returns {@link #UNBOUNDED} ({@code Integer.MAX_VALUE}) for unbounded cardinalities.</p>
     *
     * @return the maximum participation count; never negative
     */
    public int getMax() {
        return max;
    }

    /**
     * Returns whether participation is mandatory (min &ge; 1).
     *
     * @return {@code true} for ONE_ONE and ONE_MANY
     */
    public boolean isMandatory() {
        return min >= 1;
    }

    /**
     * Returns whether multiple instances are permitted (max &gt; 1).
     *
     * @return {@code true} for ZERO_MANY and ONE_MANY
     */
    public boolean isMultiValued() {
        return max > 1;
    }

    /**
     * Returns whether the upper bound is unbounded.
     *
     * @return {@code true} for ZERO_MANY and ONE_MANY
     */
    public boolean isUnbounded() {
        return max == UNBOUNDED;
    }

    /**
     * Returns the crow's feet notation string (e.g., {@code "1..*"}).
     *
     * @return the notation string; never null
     */
    public String getNotation() {
        return notation;
    }

    /**
     * Validates that {@code count} satisfies this cardinality.
     *
     * @param count  the actual instance count; must be &ge; 0
     * @return {@code true} if count is within [min, max]
     */
    public boolean accepts(int count) {
        return count >= min && count <= max;
    }

    /**
     * Resolves an {@code EndpointCardinality} from a crow's feet notation string.
     *
     * <p>Accepts canonical notation ({@code "0..1"}, {@code "1..1"}, {@code "0..*"},
     * {@code "1..*"}) and common aliases ({@code "0..n"}, {@code "1..n"}, {@code "many"}).</p>
     *
     * @param notation  the notation string; must not be null
     * @return the matching cardinality; defaults to {@link #ZERO_ONE} if unrecognised
     */
    public static EndpointCardinality fromNotation(String notation) {
        if (notation == null) return ZERO_ONE;
        return switch (notation.trim()) {
            case "0..1", "01", "?", "optional" -> ZERO_ONE;
            case "1..1", "11", "1", "exactly-one", "mandatory" -> ONE_ONE;
            case "0..*", "0..n", "0..N", "*", "many", "zero-many" -> ZERO_MANY;
            case "1..*", "1..n", "1..N", "+", "one-many", "one-or-more" -> ONE_MANY;
            default -> ZERO_ONE;
        };
    }

    @Override
    public String toString() {
        return notation;
    }
}
