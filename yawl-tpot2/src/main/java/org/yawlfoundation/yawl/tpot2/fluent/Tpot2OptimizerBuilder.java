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

package org.yawlfoundation.yawl.tpot2.fluent;

import org.yawlfoundation.yawl.tpot2.Tpot2Config;
import org.yawlfoundation.yawl.tpot2.Tpot2Exception;
import org.yawlfoundation.yawl.tpot2.Tpot2TaskType;

import java.util.List;

/**
 * Fluent builder for creating TPOT2 optimizer instances.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * Tpot2Optimizer optimizer = Tpot2.optimizer()
 *     .taskType(Tpot2TaskType.CASE_OUTCOME)
 *     .trainingData(features, labels)
 *     .generations(10)
 *     .maxTimeMins(30)
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class Tpot2OptimizerBuilder {

    private Tpot2TaskType taskType;
    private List<double[]> features;
    private List<?> labels;
    private int generations = 5;
    private int populationSize = 50;
    private int maxTimeMins = 60;
    private int cvFolds = 5;
    private String scoringMetric = null;
    private int nJobs = -1;
    private String pythonExecutable = "python3";

    /**
     * Set the process mining task type.
     *
     * @param taskType the task type
     * @return this builder
     */
    public Tpot2OptimizerBuilder taskType(Tpot2TaskType taskType) {
        this.taskType = taskType;
        return this;
    }

    /**
     * Set the training data for optimization.
     *
     * @param features feature vectors (each row is a case)
     * @param labels labels corresponding to features
     * @return this builder
     */
    public Tpot2OptimizerBuilder trainingData(List<double[]> features, List<?> labels) {
        this.features = features;
        this.labels = labels;
        return this;
    }

    /**
     * Set the number of evolutionary generations.
     *
     * @param generations number of generations (1-100)
     * @return this builder
     */
    public Tpot2OptimizerBuilder generations(int generations) {
        this.generations = generations;
        return this;
    }

    /**
     * Set the population size per generation.
     *
     * @param populationSize population size (2-500)
     * @return this builder
     */
    public Tpot2OptimizerBuilder populationSize(int populationSize) {
        this.populationSize = populationSize;
        return this;
    }

    /**
     * Set the maximum time in minutes for optimization.
     *
     * @param maxTimeMins maximum time (1-1440)
     * @return this builder
     */
    public Tpot2OptimizerBuilder maxTimeMins(int maxTimeMins) {
        this.maxTimeMins = maxTimeMins;
        return this;
    }

    /**
     * Set the number of cross-validation folds.
     *
     * @param cvFolds number of folds (2-10)
     * @return this builder
     */
    public Tpot2OptimizerBuilder cvFolds(int cvFolds) {
        this.cvFolds = cvFolds;
        return this;
    }

    /**
     * Set the sklearn scoring metric.
     *
     * @param scoringMetric metric string (e.g., "roc_auc", "f1_macro", "r2")
     * @return this builder
     */
    public Tpot2OptimizerBuilder scoringMetric(String scoringMetric) {
        this.scoringMetric = scoringMetric;
        return this;
    }

    /**
     * Set the number of parallel jobs.
     *
     * @param nJobs number of jobs (-1 for all CPUs)
     * @return this builder
     */
    public Tpot2OptimizerBuilder nJobs(int nJobs) {
        this.nJobs = nJobs;
        return this;
    }

    /**
     * Set the Python executable path.
     *
     * @param pythonExecutable path to Python binary
     * @return this builder
     */
    public Tpot2OptimizerBuilder pythonExecutable(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
        return this;
    }

    /**
     * Build the optimizer instance.
     *
     * @return the configured optimizer
     * @throws IllegalStateException if required fields are not set
     * @throws Tpot2Exception if the optimizer cannot be initialized
     */
    public Tpot2Optimizer build() throws Tpot2Exception {
        if (taskType == null) {
            // Try to use global config
            if (Tpot2.isConfigured()) {
                taskType = Tpot2.getConfiguredConfig().taskType();
            } else {
                throw new IllegalStateException("taskType is required. " +
                    "Call .taskType() or Tpot2.configure() first.");
            }
        }

        Tpot2Config config = new Tpot2Config(
            taskType,
            generations,
            populationSize,
            maxTimeMins,
            cvFolds,
            scoringMetric,
            nJobs,
            pythonExecutable
        );

        return new Tpot2Optimizer(config, features, labels);
    }
}
