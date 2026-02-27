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

package org.yawlfoundation.yawl.graalwasm.dmn;

/**
 * DMN 1.3 hit policy enumeration.
 *
 * <p>The hit policy governs which rules in a decision table are applied when
 * evaluating the table against a given input context. DMN specifies seven
 * hit policies; all are supported here.</p>
 *
 * <h2>Single-hit policies</h2>
 * <ul>
 *   <li>{@link #UNIQUE} — at most one rule matches; error if multiple match</li>
 *   <li>{@link #FIRST} — first matching rule (in table order) applies</li>
 *   <li>{@link #ANY} — multiple rules may match, but all must produce identical output</li>
 *   <li>{@link #PRIORITY} — first matching rule after output values are sorted by priority</li>
 * </ul>
 *
 * <h2>Multi-hit policies</h2>
 * <ul>
 *   <li>{@link #COLLECT} — all matching rules contribute; aggregation optional</li>
 *   <li>{@link #RULE_ORDER} — all matching rules in definition order</li>
 *   <li>{@link #OUTPUT_ORDER} — all matching rules ordered by output priority</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnWasmBridge
 * @see DmnDecisionResult
 */
public enum DmnHitPolicy {

    /**
     * Unique: at most one rule may match. Multiple matches are a model error.
     * DMN annotation: U
     */
    UNIQUE("UNIQUE", "U"),

    /**
     * First: first matching rule in definition order applies.
     * DMN annotation: F
     */
    FIRST("FIRST", "F"),

    /**
     * Any: multiple rules may match, but all must have identical output values.
     * DMN annotation: A
     */
    ANY("ANY", "A"),

    /**
     * Collect: all matching rules contribute. Aggregation operators:
     * SUM (+), MIN (<), MAX (>), COUNT (#). Default is list.
     * DMN annotation: C
     */
    COLLECT("COLLECT", "C"),

    /**
     * Rule order: all matching rules in the order they appear in the table.
     * DMN annotation: R
     */
    RULE_ORDER("RULE ORDER", "R"),

    /**
     * Priority: first match after rules are ordered by output value priority.
     * DMN annotation: P
     */
    PRIORITY("PRIORITY", "P"),

    /**
     * Output order: all matches ordered by output value priority.
     * DMN annotation: O
     */
    OUTPUT_ORDER("OUTPUT ORDER", "O");

    private final String dmnName;
    private final String annotation;

    DmnHitPolicy(String dmnName, String annotation) {
        this.dmnName = dmnName;
        this.annotation = annotation;
    }

    /**
     * Returns the canonical DMN specification name (e.g., "RULE ORDER").
     *
     * @return the DMN name; never null
     */
    public String getDmnName() {
        return dmnName;
    }

    /**
     * Returns the single-letter DMN table annotation (e.g., "R").
     *
     * @return the annotation; never null
     */
    public String getAnnotation() {
        return annotation;
    }

    /**
     * Returns whether this is a single-hit policy (returns one result row).
     *
     * @return {@code true} for UNIQUE, FIRST, ANY, PRIORITY; {@code false} otherwise
     */
    public boolean isSingleHit() {
        return this == UNIQUE || this == FIRST || this == ANY || this == PRIORITY;
    }

    /**
     * Returns whether this is a multi-hit policy (may return multiple result rows).
     *
     * @return {@code true} for COLLECT, RULE_ORDER, OUTPUT_ORDER; {@code false} otherwise
     */
    public boolean isMultiHit() {
        return !isSingleHit();
    }

    /**
     * Resolves a DMN hit policy by its canonical name or single-letter annotation.
     *
     * <p>Matching is case-insensitive. Accepts both full names ("RULE ORDER") and
     * annotations ("R").</p>
     *
     * @param value  the DMN name or annotation; must not be null
     * @return the matching DmnHitPolicy; defaults to {@link #UNIQUE} if unrecognised
     */
    public static DmnHitPolicy fromDmnValue(String value) {
        if (value == null || value.isBlank()) {
            return UNIQUE;
        }
        String v = value.trim().toUpperCase();
        for (DmnHitPolicy hp : values()) {
            if (hp.dmnName.equals(v) || hp.annotation.equals(v)) {
                return hp;
            }
        }
        return UNIQUE; // DMN default
    }
}
