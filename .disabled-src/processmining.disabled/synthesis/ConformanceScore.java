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

package org.yawlfoundation.yawl.integration.processmining.synthesis;

import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlProcess;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlPlace;

/**
 * Conformance metrics for process mining quality assessment.
 * Measures how well a mined/discovered process model conforms to the input event log
 * and generalizes to unseen behavior.
 * Immutable record.
 *
 * @param fitness        0.0-1.0, proportion of event log traces that fit the model
 * @param precision      0.0-1.0, proportion of model behavior that is observed in log
 * @param generalization 0.0-1.0, model's ability to generalize beyond observed log
 * @param placeCount     Number of places in the model
 * @param transitionCount Number of transitions in the model
 * @param arcCount       Number of arcs in the model
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public record ConformanceScore(
        double fitness,
        double precision,
        double generalization,
        int placeCount,
        int transitionCount,
        int arcCount
) {

    /**
     * Validates score ranges and structure sizes.
     */
    public ConformanceScore {
        if (fitness < 0.0 || fitness > 1.0) {
            throw new IllegalArgumentException("Fitness must be in range [0.0, 1.0]");
        }
        if (precision < 0.0 || precision > 1.0) {
            throw new IllegalArgumentException("Precision must be in range [0.0, 1.0]");
        }
        if (generalization < 0.0 || generalization > 1.0) {
            throw new IllegalArgumentException("Generalization must be in range [0.0, 1.0]");
        }
        if (placeCount < 0) {
            throw new IllegalArgumentException("Place count cannot be negative");
        }
        if (transitionCount < 0) {
            throw new IllegalArgumentException("Transition count cannot be negative");
        }
        if (arcCount < 0) {
            throw new IllegalArgumentException("Arc count cannot be negative");
        }
    }

    /**
     * Checks if this model has high conformance.
     * High conformance = fitness >= 0.9 AND precision >= 0.8.
     *
     * @return true if model is well-conforming
     */
    public boolean isHighConformance() {
        return fitness >= 0.9 && precision >= 0.8;
    }

    /**
     * Computes weighted overall score combining all three metrics.
     * Weighting: 50% fitness, 30% precision, 20% generalization.
     *
     * @return weighted score in range [0.0, 1.0]
     */
    public double overallScore() {
        return 0.5 * fitness + 0.3 * precision + 0.2 * generalization;
    }

    /**
     * Creates a human-readable summary of conformance scores.
     *
     * @return formatted summary string
     */
    public String summary() {
        return String.format(
            "Fitness: %.2f, Precision: %.2f, Generalization: %.2f, Overall: %.3f",
            fitness, precision, generalization, overallScore()
        );
    }

    /**
     * Creates a detailed summary including model structure.
     *
     * @return formatted detailed summary
     */
    public String detailedSummary() {
        return String.format(
            "%s | Places: %d, Transitions: %d, Arcs: %d",
            summary(), placeCount, transitionCount, arcCount
        );
    }

    /**
     * Computes conformance score from a PnmlProcess structure alone.
     * Uses structural metrics (not log-based):
     * - fitness: based on well-formedness
     * - precision: based on complexity (not overfitted)
     * - generalization: based on balance between structure
     *
     * This is a simplified structural assessment; full conformance
     * analysis requires event log comparison.
     *
     * @param process PnmlProcess to score
     * @return ConformanceScore reflecting structural quality
     * @throws IllegalArgumentException if process is invalid
     */
    public static ConformanceScore fromProcess(PnmlProcess process) {
        if (!process.isValid()) {
            throw new IllegalArgumentException("Process is not structurally valid");
        }

        int placeCount = process.places().size();
        int transitionCount = process.transitions().size();
        int arcCount = process.arcs().size();

        // Structural fitness: penalize unbalanced in/out degrees
        double fitness = computeStructuralFitness(process);

        // Structural precision: penalize excessive branching/merging
        double precision = computeStructuralPrecision(process);

        // Generalization: reward balanced structure
        double generalization = computeGeneralization(placeCount, transitionCount, arcCount);

        return new ConformanceScore(fitness, precision, generalization,
            placeCount, transitionCount, arcCount);
    }

    /**
     * Computes fitness based on structural balance (in-degree = out-degree).
     *
     * @param process PnmlProcess
     * @return fitness score 0.0-1.0
     */
    private static double computeStructuralFitness(PnmlProcess process) {
        int balancedNodes = 0;
        int totalNodes = process.places().size() + process.transitions().size();

        if (totalNodes == 0) {
            return 0.0;
        }

        // Check if start place has no incoming arcs (source)
        try {
            PnmlPlace start = process.startPlace();
            if (process.incomingArcs(start.id()).isEmpty()) {
                balancedNodes++;
            }
        } catch (IllegalStateException ignored) {
        }

        // Check if end places have no outgoing arcs (sink)
        for (PnmlPlace end : process.endPlaces()) {
            if (process.outgoingArcs(end.id()).isEmpty()) {
                balancedNodes++;
            }
        }

        // Simple fitness: proportion of well-formed boundary nodes
        return Math.min(1.0, balancedNodes / 2.0);
    }

    /**
     * Computes precision based on low complexity (not overfitted).
     *
     * @param process PnmlProcess
     * @return precision score 0.0-1.0
     */
    private static double computeStructuralPrecision(PnmlProcess process) {
        int placeCount = process.places().size();
        int transitionCount = process.transitions().size();
        int arcCount = process.arcs().size();

        // Complexity penalty: if too many arcs per transition, model may be overfitted
        if (transitionCount == 0) {
            return 1.0;
        }

        double avgArcsPerTransition = (double) arcCount / transitionCount;

        // Precision decreases if model is too dense (overfitted)
        // Sweet spot: 2-3 arcs per transition
        if (avgArcsPerTransition <= 1.0) {
            return 0.7;
        } else if (avgArcsPerTransition <= 3.0) {
            return 0.95;
        } else if (avgArcsPerTransition <= 5.0) {
            return 0.85;
        } else {
            return 0.6;  // Very dense, likely overfitted
        }
    }

    /**
     * Computes generalization based on structure balance.
     *
     * @param placeCount Number of places
     * @param transitionCount Number of transitions
     * @param arcCount Number of arcs
     * @return generalization score 0.0-1.0
     */
    private static double computeGeneralization(int placeCount, int transitionCount, int arcCount) {
        // Balanced nets have roughly equal places and transitions
        if (placeCount == 0 && transitionCount == 0) {
            return 0.0;
        }

        if (placeCount == 0 || transitionCount == 0) {
            return 0.5;  // Degenerate case
        }

        double ratio = (double) placeCount / transitionCount;

        // Ideal ratio is 1.0 (balanced)
        double balance = 1.0 - Math.abs(ratio - 1.0) / Math.max(ratio, 1.0 / ratio);

        // Also consider complexity: moderate arc count is good for generalization
        double densityScore = 1.0;
        double avgArcs = (double) arcCount / Math.max(1, placeCount + transitionCount);
        if (avgArcs > 4.0) {
            densityScore = 0.7;  // Too complex
        }

        return balance * 0.7 + densityScore * 0.3;
    }
}
