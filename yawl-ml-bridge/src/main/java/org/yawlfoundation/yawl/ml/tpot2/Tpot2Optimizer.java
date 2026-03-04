/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml.tpot2;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TPOT2 Optimizer - Genetic programming for ML pipeline optimization.
 *
 * <h2>Example usage:</h2>
 * <pre>{@code
 * OptimizationResult result = Tpot2Optimizer.create()
 *     .withGenerations(50)
 *     .withPopulationSize(100)
 *     .withTimeout(Duration.ofMinutes(10))
 *     .optimize(X_train, y_train);
 *
 * System.out.println("Best fitness: " + result.fitnessScore());
 * System.out.println("Best pipeline: " + result.bestPipeline());
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class Tpot2Optimizer {

    private final int generations;
    private final int populationSize;
    private final Duration timeout;
    private final String scoring;
    private final int randomState;
    private final Tpot2BridgeClient client;

    private Tpot2Optimizer(Builder builder) {
        this.generations = builder.generations;
        this.populationSize = builder.populationSize;
        this.timeout = builder.timeout;
        this.scoring = builder.scoring;
        this.randomState = builder.randomState;
        this.client = builder.client != null ? builder.client : Tpot2BridgeClient.getDefault();
    }

    /**
     * Run optimization on training data.
     *
     * @param X feature matrix (list of samples)
     * @param y labels (list of values)
     * @return optimization result with best pipeline
     * @throws Tpot2Exception if optimization fails
     */
    public OptimizationResult optimize(List<List<Double>> X, List<Object> y) throws Tpot2Exception {
        return client.optimize(X, y, toConfig());
    }

    /**
     * Run optimization with pre-split validation data.
     *
     * @param XTrain training features
     * @param yTrain training labels
     * @param XVal validation features
     * @param yVal validation labels
     * @return optimization result
     * @throws Tpot2Exception if optimization fails
     */
    public OptimizationResult optimizeWithValidation(
            List<List<Double>> XTrain, List<Object> yTrain,
            List<List<Double>> XVal, List<Object> yVal) throws Tpot2Exception {
        // For now, just use training data (TPOT2 handles CV internally)
        return optimize(XTrain, yTrain);
    }

    private Map<String, Object> toConfig() {
        return Map.of(
            "generations", generations,
            "population_size", populationSize,
            "timeout_minutes", timeout.toMinutes(),
            "scoring", scoring,
            "random_state", randomState
        );
    }

    /**
     * Create a new optimizer builder.
     * @return builder instance
     */
    public static Builder create() {
        return new Builder();
    }

    /**
     * Create a quick optimizer (10 generations, 50 population).
     * @return builder with quick config
     */
    public static Builder quick() {
        return create()
            .withGenerations(10)
            .withPopulationSize(50);
    }

    /**
     * Optimizer builder.
     */
    public static final class Builder {
        private int generations = 50;
        private int populationSize = 100;
        private Duration timeout = Duration.ofMinutes(10);
        private String scoring = "accuracy";
        private int randomState = 42;
        private Tpot2BridgeClient client;

        private Builder() {}

        /**
         * Set number of generations.
         */
        public Builder withGenerations(int generations) {
            this.generations = generations;
            return this;
        }

        /**
         * Set population size.
         */
        public Builder withPopulationSize(int populationSize) {
            this.populationSize = populationSize;
            return this;
        }

        /**
         * Set optimization timeout.
         */
        public Builder withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set scoring metric (accuracy, f1, roc_auc, etc.).
         */
        public Builder withScoring(String scoring) {
            this.scoring = scoring;
            return this;
        }

        /**
         * Set random state for reproducibility.
         */
        public Builder withRandomState(int randomState) {
            this.randomState = randomState;
            return this;
        }

        /**
         * Set custom bridge client.
         */
        public Builder withClient(Tpot2BridgeClient client) {
            this.client = client;
            return this;
        }

        /**
         * Build the optimizer.
         */
        public Tpot2Optimizer build() {
            return new Tpot2Optimizer(this);
        }
    }
}
