/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.dspy.evaluate;

import org.yawlfoundation.yawl.dspy.signature.SignatureResult;

import java.util.Map;

/**
 * Evaluation metric for DSPy module outputs.
 *
 * <p>Metrics score the quality of module outputs against expected values.
 * They are used by teleprompters to optimize modules and by evaluation
 * pipelines to measure performance.
 *
 * <h2>Built-in metrics:</h2>
 * <ul>
 *   <li>{@link #exactMatch()} - Binary: output matches expected exactly</li>
 *   <li>{@link #contains()} - Checks if output contains expected string</li>
 *   <li>{@link #semanticSimilarity()} - Uses LLM to judge similarity</li>
 *   <li>{@link #accuracy()} - For classification tasks</li>
 * </ul>
 *
 * <h2>Custom metric:</h2>
 * {@snippet :
 * Metric caseOutcomeMetric = (predicted, expected, trace) -> {
 *     String predOutcome = predicted.getString("outcome");
 *     String expOutcome = expected.getString("outcome");
 *     double confidenceDiff = Math.abs(
 *         predicted.getDouble("confidence") - expected.getDouble("confidence")
 *     );
 *
 *     if (!predOutcome.equals(expOutcome)) return 0.0;
 *     if (confidenceDiff > 0.2) return 0.5;
 *     return 1.0;
 * };
 * }
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@FunctionalInterface
public interface Metric {

    /**
     * Score a predicted result against expected values.
     *
     * @param predicted the predicted result from the module
     * @param expected the ground truth values
     * @param trace execution trace (may contain intermediate steps)
     * @return a score between 0.0 (worst) and 1.0 (best)
     */
    double score(SignatureResult predicted, Map<String, Object> expected, Map<String, Object> trace);

    /**
     * Get the name of this metric for logging.
     */
    default String name() {
        return getClass().getSimpleName();
    }

    // ── Built-in metrics ───────────────────────────────────────────────────────

    /**
     * Exact match metric - 1.0 if outputs match exactly, 0.0 otherwise.
     */
    static Metric exactMatch() {
        return new ExactMatchMetric();
    }

    /**
     * Contains metric - 1.0 if output contains expected substring.
     */
    static Metric contains() {
        return (predicted, expected, trace) -> {
            for (var entry : expected.entrySet()) {
                Object predVal = predicted.values().get(entry.getKey());
                if (predVal == null || !predVal.toString().contains(entry.getValue().toString())) {
                    return 0.0;
                }
            }
            return 1.0;
        };
    }

    /**
     * Case-insensitive contains metric.
     */
    static Metric containsIgnoreCase() {
        return (predicted, expected, trace) -> {
            for (var entry : expected.entrySet()) {
                Object predVal = predicted.values().get(entry.getKey());
                if (predVal == null) return 0.0;
                String predStr = predVal.toString().toLowerCase();
                String expStr = entry.getValue().toString().toLowerCase();
                if (!predStr.contains(expStr)) return 0.0;
            }
            return 1.0;
        };
    }

    /**
     * Numeric tolerance metric - 1.0 if within tolerance, 0.0 otherwise.
     */
    static Metric numericTolerance(double tolerance) {
        return (predicted, expected, trace) -> {
            for (var entry : expected.entrySet()) {
                Object predVal = predicted.values().get(entry.getKey());
                if (!(predVal instanceof Number predNum) || !(entry.getValue() instanceof Number expNum)) {
                    return 0.0;
                }
                if (Math.abs(predNum.doubleValue() - expNum.doubleValue()) > tolerance) {
                    return 0.0;
                }
            }
            return 1.0;
        };
    }

    /**
     * Accuracy metric for classification - counts correct predictions.
     */
    static Metric accuracy(String... fields) {
        return new AccuracyMetric(fields);
    }

    /**
     * F1 score metric for binary classification.
     */
    static Metric f1Score(String positiveField, String positiveValue) {
        return new F1Metric(positiveField, positiveValue);
    }

    /**
     * Composite metric that averages multiple metrics.
     */
    static Metric composite(Metric... metrics) {
        return new CompositeMetric(metrics);
    }

    /**
     * Weighted composite metric.
     */
    static Metric weighted(Map<Metric, Double> weightedMetrics) {
        return new WeightedMetric(weightedMetrics);
    }
}
