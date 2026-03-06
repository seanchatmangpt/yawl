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
import org.yawlfoundation.yawl.tpot2.Tpot2TaskType;

/**
 * Fluent builder for TPOT2 configuration.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * Tpot2Config config = new Tpot2ConfigBuilder()
 *     .taskType(Tpot2TaskType.CASE_OUTCOME)
 *     .generations(10)
 *     .populationSize(100)
 *     .maxTimeMins(30)
 *     .scoringMetric("roc_auc")
 *     .build();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class Tpot2ConfigBuilder {

    private Tpot2TaskType taskType;
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
     * @param taskType the task type (CASE_OUTCOME, REMAINING_TIME, NEXT_ACTIVITY, ANOMALY_DETECTION)
     * @return this builder
     */
    public Tpot2ConfigBuilder taskType(Tpot2TaskType taskType) {
        this.taskType = taskType;
        return this;
    }

    /**
     * Set the number of evolutionary generations.
     *
     * @param generations number of generations (1-100)
     * @return this builder
     */
    public Tpot2ConfigBuilder generations(int generations) {
        this.generations = generations;
        return this;
    }

    /**
     * Set the population size per generation.
     *
     * @param populationSize population size (2-500)
     * @return this builder
     */
    public Tpot2ConfigBuilder populationSize(int populationSize) {
        this.populationSize = populationSize;
        return this;
    }

    /**
     * Set the maximum time in minutes for optimization.
     *
     * @param maxTimeMins maximum time (1-1440)
     * @return this builder
     */
    public Tpot2ConfigBuilder maxTimeMins(int maxTimeMins) {
        this.maxTimeMins = maxTimeMins;
        return this;
    }

    /**
     * Set the number of cross-validation folds.
     *
     * @param cvFolds number of folds (2-10)
     * @return this builder
     */
    public Tpot2ConfigBuilder cvFolds(int cvFolds) {
        this.cvFolds = cvFolds;
        return this;
    }

    /**
     * Set the sklearn scoring metric.
     *
     * @param scoringMetric metric string (e.g., "roc_auc", "f1_macro", "r2")
     * @return this builder
     */
    public Tpot2ConfigBuilder scoringMetric(String scoringMetric) {
        this.scoringMetric = scoringMetric;
        return this;
    }

    /**
     * Set the number of parallel jobs.
     *
     * @param nJobs number of jobs (-1 for all CPUs)
     * @return this builder
     */
    public Tpot2ConfigBuilder nJobs(int nJobs) {
        this.nJobs = nJobs;
        return this;
    }

    /**
     * Set the Python executable path.
     *
     * @param pythonExecutable path to Python binary
     * @return this builder
     */
    public Tpot2ConfigBuilder pythonExecutable(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
        return this;
    }

    /**
     * Build the immutable configuration.
     *
     * @return the built configuration
     * @throws NullPointerException if taskType is not set
     */
    public Tpot2Config build() {
        if (taskType == null) {
            throw new NullPointerException("taskType is required");
        }
        return new Tpot2Config(
            taskType,
            generations,
            populationSize,
            maxTimeMins,
            cvFolds,
            scoringMetric,
            nJobs,
            pythonExecutable
        );
    }
}
