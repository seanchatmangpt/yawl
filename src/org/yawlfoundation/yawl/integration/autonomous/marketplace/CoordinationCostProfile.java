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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import java.util.Objects;

/**
 * Dimension 4: Economic cost profile for an agent marketplace entry.
 *
 * <p>The CONSTRUCT architecture makes coordination costs explicit and measurable.
 * Every CONSTRUCT query has a known cost envelope (established empirically, e.g.
 * via QLever benchmarks), and the fraction of calls that require LLM inference
 * vs. pure graph matching directly determines economic value.</p>
 *
 * <p>An agent that handles 80% of its pattern matching internally without LLM
 * inference is economically more valuable in a high-throughput workflow than one
 * that routes every decision through inference — even if they produce identical
 * outputs. This profile makes that difference formally expressible and priceable.</p>
 *
 * <p>Pricing models:</p>
 * <ul>
 *   <li>{@link PricingModel#PER_QUERY} — billed per CONSTRUCT query issued</li>
 *   <li>{@link PricingModel#PER_CYCLE} — billed per full coordination cycle
 *       (query + any inference + output construction)</li>
 *   <li>{@link PricingModel#FLAT} — fixed rate regardless of query volume</li>
 * </ul>
 *
 * <p>The {@code basePricePerCycle} is a dimensionless normalized unit enabling
 * cross-agent comparison. Buyers specify a maximum acceptable price; the
 * marketplace filters agents that exceed it.</p>
 *
 * @param constructQueriesPerCall average number of CONSTRUCT queries issued per
 *                                agent invocation; must be &gt;= 0.0
 * @param llmInferenceRatio       fraction of invocations that require LLM inference
 *                                (0.0 = pure graph matching, 1.0 = all inference);
 *                                must be in [0.0, 1.0]
 * @param basePricePerCycle       normalized price per coordination cycle; must be &gt;= 0.0
 * @param pricingModel            the billing model for this agent; never null
 * @since YAWL 6.0
 */
public record CoordinationCostProfile(
        double constructQueriesPerCall,
        double llmInferenceRatio,
        double basePricePerCycle,
        PricingModel pricingModel) {

    /**
     * Billing model for an agent's coordination cost.
     */
    public enum PricingModel {
        /** Billed per CONSTRUCT query issued to the coordination graph. */
        PER_QUERY,
        /** Billed per complete coordination cycle (query + inference + output). */
        PER_CYCLE,
        /** Fixed rate, independent of query or cycle volume. */
        FLAT
    }

    /** Compact constructor: validates field constraints. */
    public CoordinationCostProfile {
        Objects.requireNonNull(pricingModel, "pricingModel is required");
        if (constructQueriesPerCall < 0.0) {
            throw new IllegalArgumentException(
                "constructQueriesPerCall must be >= 0.0, was: " + constructQueriesPerCall);
        }
        if (llmInferenceRatio < 0.0 || llmInferenceRatio > 1.0) {
            throw new IllegalArgumentException(
                "llmInferenceRatio must be in [0.0, 1.0], was: " + llmInferenceRatio);
        }
        if (basePricePerCycle < 0.0) {
            throw new IllegalArgumentException(
                "basePricePerCycle must be >= 0.0, was: " + basePricePerCycle);
        }
    }

    /**
     * Returns true if this agent's cost is within the specified budget.
     *
     * @param maxCostPerCycle maximum acceptable price per coordination cycle
     * @return true iff {@code basePricePerCycle <= maxCostPerCycle}
     */
    public boolean isWithinBudget(double maxCostPerCycle) {
        return basePricePerCycle <= maxCostPerCycle;
    }

    /**
     * Returns true if this agent operates primarily via graph matching rather
     * than LLM inference (inference ratio below the given threshold).
     *
     * <p>Agents with low inference ratios are preferable for high-throughput
     * workflow transitions where deterministic, low-latency matching is required.</p>
     *
     * @param maxInferenceRatio maximum acceptable LLM inference fraction
     * @return true iff {@code llmInferenceRatio <= maxInferenceRatio}
     */
    public boolean isGraphMatchingDominant(double maxInferenceRatio) {
        return llmInferenceRatio <= maxInferenceRatio;
    }

    /**
     * A cost profile for a pure graph-matching agent with zero inference overhead
     * and minimal query cost.
     *
     * @param basePricePerCycle the normalized price per cycle
     * @return a PER_CYCLE profile with 0.0 inference ratio
     */
    public static CoordinationCostProfile pureGraphMatching(double basePricePerCycle) {
        return new CoordinationCostProfile(1.0, 0.0, basePricePerCycle, PricingModel.PER_CYCLE);
    }

    /**
     * A cost profile for a pure inference agent that delegates all decisions
     * to an LLM without structured graph matching.
     *
     * @param basePricePerCycle the normalized price per cycle
     * @return a PER_CYCLE profile with 1.0 inference ratio and 0 CONSTRUCT queries
     */
    public static CoordinationCostProfile pureInference(double basePricePerCycle) {
        return new CoordinationCostProfile(0.0, 1.0, basePricePerCycle, PricingModel.PER_CYCLE);
    }
}
