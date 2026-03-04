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

package org.yawlfoundation.yawl.dspy.teleprompter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.evaluate.EvaluationResult;
import org.yawlfoundation.yawl.dspy.evaluate.Metric;
import org.yawlfoundation.yawl.dspy.module.Module;
import org.yawlfoundation.yawl.dspy.module.ModuleException;
import org.yawlfoundation.yawl.dspy.signature.Example;
import org.yawlfoundation.yawl.dspy.signature.SignatureResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Evaluates a DSPy module on a test set.
 *
 * <p>Usage:
 * {@snippet :
 * var evaluator = Evaluator.builder()
 *     .metric(Metric.accuracy("outcome"))
 *     .parallel(true)
 *     .build();
 *
 * EvaluationResult result = evaluator.evaluate(module, testset);
 * System.out.println("Accuracy: " + result.overallScore());
 * }
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class Evaluator {

    private static final Logger log = LoggerFactory.getLogger(Evaluator.class);

    private final List<Metric> metrics;
    private final boolean parallel;
    private final int maxFailures;

    private Evaluator(Builder builder) {
        this.metrics = List.copyOf(builder.metrics);
        this.parallel = builder.parallel;
        this.maxFailures = builder.maxFailures;
    }

    /**
     * Evaluate a module on a test set.
     */
    public EvaluationResult evaluate(Module<?> module, List<Example> testset) {
        log.info("Evaluating module on {} examples", testset.size());

        List<EvaluationResult.ExampleScore> exampleScores = new ArrayList<>();
        List<EvaluationResult.FailedExample> failures = new ArrayList<>();
        Map<String, List<Double>> metricScores = new LinkedHashMap<>();

        for (Metric metric : metrics) {
            metricScores.put(metric.name(), new ArrayList<>());
        }

        int failureCount = 0;

        for (int i = 0; i < testset.size(); i++) {
            Example example = testset.get(i);

            if (failureCount >= maxFailures) {
                log.warn("Max failures ({}) reached, stopping evaluation", maxFailures);
                break;
            }

            try {
                SignatureResult result;
                if (parallel) {
                    result = CompletableFuture.supplyAsync(
                        () -> module.run(example.inputs()),
                        Executors.newVirtualThreadPerTaskExecutor()
                    ).join();
                } else {
                    result = module.run(example.inputs());
                }

                Map<String, Double> breakdown = new LinkedHashMap<>();
                double totalScore = 0.0;

                for (Metric metric : metrics) {
                    double score = metric.score(result, example.outputs(), Map.of());
                    metricScores.get(metric.name()).add(score);
                    breakdown.put(metric.name(), score);
                    totalScore += score;
                }

                double avgScore = totalScore / metrics.size();

                exampleScores.add(new EvaluationResult.ExampleScore(
                    i,
                    example.inputs(),
                    result.values(),
                    example.outputs(),
                    avgScore,
                    breakdown
                ));

            } catch (ModuleException e) {
                failureCount++;
                failures.add(new EvaluationResult.FailedExample(
                    i,
                    example.inputs(),
                    e.getMessage()
                ));
                log.debug("Example {} failed: {}", i, e.getMessage());
            }
        }

        // Calculate aggregate metric scores
        Map<String, Double> aggregateScores = new LinkedHashMap<>();
        for (var entry : metricScores.entrySet()) {
            List<Double> scores = entry.getValue();
            double avg = scores.isEmpty() ? 0.0 : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            aggregateScores.put(entry.getKey(), avg);
        }

        double overallScore = aggregateScores.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);

        log.info("Evaluation complete: score={}, successes={}, failures={}",
            overallScore, exampleScores.size(), failures.size());

        return new EvaluationResult(
            overallScore,
            aggregateScores,
            exampleScores,
            failures,
            Instant.now()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<Metric> metrics = List.of(Metric.exactMatch());
        private boolean parallel = false;
        private int maxFailures = 100;

        public Builder metrics(List<Metric> metrics) {
            this.metrics = metrics;
            return this;
        }

        public Builder addMetric(Metric metric) {
            if (this.metrics == null) {
                this.metrics = new ArrayList<>();
            }
            var newList = new ArrayList<>(this.metrics);
            newList.add(metric);
            this.metrics = newList;
            return this;
        }

        public Builder parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        public Builder maxFailures(int maxFailures) {
            this.maxFailures = maxFailures;
            return this;
        }

        public Evaluator build() {
            if (metrics == null || metrics.isEmpty()) {
                throw new IllegalArgumentException("at least one metric required");
            }
            return new Evaluator(this);
        }
    }
}
