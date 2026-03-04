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

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of evaluating a DSPy module.
 *
 * @param overallScore the average score across all examples
 * @param metricScores per-metric scores
 * @param exampleScores per-example scores
 * @param failures examples that failed to produce output
 * @param timestamp when the evaluation was run
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record EvaluationResult(
    double overallScore,
    Map<String, Double> metricScores,
    List<ExampleScore> exampleScores,
    List<FailedExample> failures,
    Instant timestamp
) {

    public EvaluationResult {
        metricScores = Map.copyOf(metricScores);
        exampleScores = List.copyOf(exampleScores);
        failures = List.copyOf(failures);
    }

    /**
     * Get the number of successful evaluations.
     */
    public int successCount() {
        return exampleScores.size();
    }

    /**
     * Get the number of failed evaluations.
     */
    public int failureCount() {
        return failures.size();
    }

    /**
     * Get the total number of examples evaluated.
     */
    public int totalCount() {
        return successCount() + failureCount();
    }

    /**
     * Get the success rate (0.0 to 1.0).
     */
    public double successRate() {
        int total = totalCount();
        return total == 0 ? 0.0 : (double) successCount() / total;
    }

    /**
     * Score for a single example.
     */
    public record ExampleScore(
        int index,
        Map<String, Object> inputs,
        Map<String, Object> predicted,
        Map<String, Object> expected,
        double score,
        Map<String, Double> metricBreakdown
    ) {
        public ExampleScore {
            inputs = Map.copyOf(inputs);
            predicted = Map.copyOf(predicted);
            expected = Map.copyOf(expected);
            metricBreakdown = Map.copyOf(metricBreakdown);
        }
    }

    /**
     * A failed example evaluation.
     */
    public record FailedExample(
        int index,
        Map<String, Object> inputs,
        String error
    ) {
        public FailedExample {
            inputs = Map.copyOf(inputs);
        }
    }
}
