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

import org.jspecify.annotations.Nullable;
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

/**
 * BootstrapFewShot teleprompter - generates few-shot examples from successful runs.
 *
 * <p>BootstrapFewShot works by:
 * <ol>
 *   <li>Running the module on training examples</li>
 *   <li>Collecting outputs that score well on the metric</li>
 *   <li>Adding successful input/output pairs as few-shot examples</li>
 *   <li>Returning an optimized module with the bootstrapped examples</li>
 * </ol>
 *
 * <h2>Usage:</h2>
 * {@snippet :
 * // Define training data
 * List<Example> trainset = loadTrainingData();
 *
 * // Define evaluation metric
 * Metric metric = Metric.accuracy("outcome");
 *
 * // Create optimizer
 * var optimizer = BootstrapFewShot.<Predict<?>>builder()
 *     .metric(metric)
 *     .maxExamples(5)
 *     .minScore(0.8)
 *     .build();
 *
 * // Optimize module
 * Predict<?> optimized = optimizer.compile(predictor, trainset);
 * }
 *
 * @param <M> the module type being optimized
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class BootstrapFewShot<M extends Module<?>> implements Teleprompter<M> {

    private static final Logger log = LoggerFactory.getLogger(BootstrapFewShot.class);

    private final Metric metric;
    private final int maxExamples;
    private final double minScore;
    private final int maxRounds;
    private final List<OptimizationStep> trace;

    private BootstrapFewShot(Builder<M> builder) {
        this.metric = builder.metric;
        this.maxExamples = builder.maxExamples;
        this.minScore = builder.minScore;
        this.maxRounds = builder.maxRounds;
        this.trace = new ArrayList<>();
    }

    @Override
    public String name() {
        return "BootstrapFewShot";
    }

    @Override
    @SuppressWarnings("unchecked")
    public M compile(M module, List<Example> trainset) {
        log.info("Starting BootstrapFewShot optimization with {} examples", trainset.size());
        trace.clear();

        if (trainset.isEmpty()) {
            throw new OptimizationException(name(),
                OptimizationException.ErrorKind.NO_VALID_EXAMPLES,
                "Training set is empty");
        }

        // Step 1: Bootstrap examples by running module on training data
        List<BootstrappedExample> bootstrapped = bootstrapExamples(module, trainset);

        if (bootstrapped.isEmpty()) {
            throw new OptimizationException(name(),
                OptimizationException.ErrorKind.ALL_BOOTSTRAPS_FAILED,
                "No examples passed the metric threshold");
        }

        log.info("Bootstrapped {} successful examples", bootstrapped.size());

        // Step 2: Select best examples (highest scores, up to maxExamples)
        List<BootstrappedExample> selected = selectBestExamples(bootstrapped, maxExamples);

        // Step 3: Create optimized module with few-shot examples
        List<Example> fewShotExamples = selected.stream()
            .map(be -> Example.of(be.inputs, be.outputs))
            .toList();

        M optimized = (M) module.withExamples(fewShotExamples);

        // Record optimization step
        trace.add(new OptimizationStep(
            1,
            "Bootstrapped %d examples, selected %d for few-shot".formatted(bootstrapped.size(), selected.size()),
            fewShotExamples,
            0.0,
            selected.stream().mapToDouble(be -> be.score).average().orElse(0.0),
            Instant.now(),
            Map.of("totalBootstrapped", bootstrapped.size())
        ));

        log.info("Optimization complete: added {} few-shot examples", fewShotExamples.size());
        return optimized;
    }

    @Override
    public List<OptimizationStep> trace() {
        return List.copyOf(trace);
    }

    private List<BootstrappedExample> bootstrapExamples(M module, List<Example> trainset) {
        List<BootstrappedExample> results = new ArrayList<>();

        for (int i = 0; i < trainset.size(); i++) {
            Example example = trainset.get(i);

            try {
                log.debug("Bootstrapping example {}/{}", i + 1, trainset.size());

                // Run module
                SignatureResult result = module.run(example.inputs());

                // Score with metric
                double score = metric.score(result, example.outputs(), Map.of());

                log.debug("Example {} scored {}", i + 1, score);

                if (score >= minScore) {
                    results.add(new BootstrappedExample(
                        example.inputs(),
                        result.values(),
                        score,
                        i
                    ));
                }

            } catch (ModuleException e) {
                log.warn("Example {} failed: {}", i + 1, e.getMessage());
            }
        }

        return results;
    }

    private List<BootstrappedExample> selectBestExamples(List<BootstrappedExample> examples, int maxCount) {
        return examples.stream()
            .sorted((a, b) -> Double.compare(b.score, a.score)) // Highest first
            .limit(maxCount)
            .toList();
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static <M extends Module<?>> Builder<M> builder() {
        return new Builder<>();
    }

    public static final class Builder<M extends Module<?>> {
        private Metric metric = Metric.exactMatch();
        private int maxExamples = 5;
        private double minScore = 0.8;
        private int maxRounds = 1;

        /**
         * Set the evaluation metric.
         */
        public Builder<M> metric(Metric metric) {
            this.metric = metric;
            return this;
        }

        /**
         * Set maximum number of few-shot examples to include.
         */
        public Builder<M> maxExamples(int maxExamples) {
            this.maxExamples = maxExamples;
            return this;
        }

        /**
         * Set minimum score threshold for including an example.
         */
        public Builder<M> minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        /**
         * Set maximum number of bootstrap rounds.
         */
        public Builder<M> maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public BootstrapFewShot<M> build() {
            if (metric == null) {
                throw new IllegalArgumentException("metric is required");
            }
            return new BootstrapFewShot<>(this);
        }
    }

    // ── Inner types ─────────────────────────────────────────────────────────

    private record BootstrappedExample(
        Map<String, Object> inputs,
        Map<String, Object> outputs,
        double score,
        int originalIndex
    ) {}
}
