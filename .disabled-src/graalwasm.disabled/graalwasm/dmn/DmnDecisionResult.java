/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to importing workflow technology.
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

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of evaluating a DMN decision or decision table.
 *
 * <p>The shape of the result depends on the {@link DmnHitPolicy} of the evaluated table:</p>
 * <ul>
 *   <li><b>Single-hit</b> (UNIQUE, FIRST, ANY, PRIORITY): {@link #getSingleResult()} contains
 *       the matched output row; {@link #getMatchedRules()} contains exactly one entry.</li>
 *   <li><b>Multi-hit</b> (COLLECT, RULE_ORDER, OUTPUT_ORDER): {@link #getMatchedRules()}
 *       contains all matched output rows in the appropriate order; {@link #getSingleResult()}
 *       is empty.</li>
 *   <li><b>No match</b>: both are empty; {@link #hasResult()} returns {@code false}.</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * DmnDecisionResult result = bridge.evaluateDecision(model, "eligibility", ctx);
 *
 * // Single-hit (UNIQUE or FIRST)
 * result.getSingleResult().ifPresent(row -> {
 *     String status = (String) row.get("eligibilityStatus");
 * });
 *
 * // Multi-hit (COLLECT)
 * for (Map<String, Object> row : result.getMatchedRules()) {
 *     String action = (String) row.get("action");
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnWasmBridge
 * @see DmnHitPolicy
 */
public final class DmnDecisionResult {

    private final String decisionId;
    private final DmnHitPolicy hitPolicy;
    private final List<Map<String, Object>> matchedRules;

    private DmnDecisionResult(Builder builder) {
        this.decisionId = builder.decisionId;
        this.hitPolicy = builder.hitPolicy;
        this.matchedRules = List.copyOf(builder.matchedRules);
    }

    /**
     * Returns the ID of the decision that produced this result.
     *
     * @return the decision ID; never null
     */
    public String getDecisionId() {
        return decisionId;
    }

    /**
     * Returns the hit policy of the decision table that was evaluated.
     *
     * @return the hit policy; never null
     */
    public DmnHitPolicy getHitPolicy() {
        return hitPolicy;
    }

    /**
     * Returns all matched output rows.
     *
     * <p>For single-hit policies, this list contains at most one entry.
     * For multi-hit policies, it contains all matching rows in policy-defined order.</p>
     *
     * @return an unmodifiable list of output rows; never null, may be empty
     */
    public List<Map<String, Object>> getMatchedRules() {
        return matchedRules;
    }

    /**
     * Returns the single matched output row for single-hit policies.
     *
     * <p>Empty if no rules matched or this is a multi-hit result (use
     * {@link #getMatchedRules()} instead for multi-hit policies).</p>
     *
     * @return the single result row, or empty if no match or multi-hit
     */
    public Optional<Map<String, Object>> getSingleResult() {
        if (hitPolicy.isSingleHit() && !matchedRules.isEmpty()) {
            return Optional.of(matchedRules.getFirst());
        }
        return Optional.empty();
    }

    /**
     * Returns whether this result contains at least one matched rule.
     *
     * @return {@code true} if at least one rule matched
     */
    public boolean hasResult() {
        return !matchedRules.isEmpty();
    }

    /**
     * Returns the number of matched rules.
     *
     * @return the match count (0 if no rules matched)
     */
    public int matchCount() {
        return matchedRules.size();
    }

    /**
     * Convenience method: returns the value of a named output from the single result.
     *
     * <p>Returns {@code null} if no match, not a single-hit policy, or the output
     * name is not present.</p>
     *
     * @param outputName  the output variable name; must not be null
     * @return the output value, or null if not available
     */
    public @Nullable Object getSingleOutputValue(String outputName) {
        return getSingleResult()
                .map(row -> row.get(outputName))
                .orElse(null);
    }

    /**
     * Convenience method for COLLECT / multi-hit: returns all values for a named output.
     *
     * @param outputName  the output variable name; must not be null
     * @return list of all values for this output across matched rules; never null, may be empty
     */
    public List<Object> collectOutputValues(String outputName) {
        return matchedRules.stream()
                .map(row -> row.get(outputName))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public String toString() {
        return "DmnDecisionResult{decisionId='" + decisionId + '\''
                + ", hitPolicy=" + hitPolicy
                + ", matchCount=" + matchedRules.size() + '}';
    }

    /**
     * Returns a new Builder for constructing a result.
     *
     * @param decisionId  the decision ID; must not be null
     * @param hitPolicy   the hit policy; must not be null
     * @return a new Builder; never null
     */
    public static Builder builder(String decisionId, DmnHitPolicy hitPolicy) {
        return new Builder(decisionId, hitPolicy);
    }

    /**
     * Builder for DmnDecisionResult.
     */
    public static final class Builder {
        private final String decisionId;
        private final DmnHitPolicy hitPolicy;
        private final java.util.ArrayList<Map<String, Object>> matchedRules = new java.util.ArrayList<>();

        private Builder(String decisionId, DmnHitPolicy hitPolicy) {
            this.decisionId = Objects.requireNonNull(decisionId, "decisionId must not be null");
            this.hitPolicy = Objects.requireNonNull(hitPolicy, "hitPolicy must not be null");
        }

        /**
         * Adds a matched rule output row.
         *
         * @param outputRow  the output variable map; must not be null
         * @return this builder
         */
        public Builder addMatchedRule(Map<String, Object> outputRow) {
            matchedRules.add(Collections.unmodifiableMap(new java.util.LinkedHashMap<>(outputRow)));
            return this;
        }

        /**
         * Builds the DmnDecisionResult.
         *
         * @return a new immutable DmnDecisionResult; never null
         */
        public DmnDecisionResult build() {
            return new DmnDecisionResult(this);
        }
    }
}
