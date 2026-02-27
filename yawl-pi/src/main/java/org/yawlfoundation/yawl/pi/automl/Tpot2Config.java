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

package org.yawlfoundation.yawl.pi.automl;

/**
 * Immutable configuration for a TPOT2 AutoML training run.
 *
 * <p>Use {@link #defaults(Tpot2TaskType)} to obtain a sensible starting point,
 * then construct a custom instance with the compact record constructor when
 * specific parameter overrides are needed.
 *
 * @param taskType          process mining use case to optimise (required)
 * @param generations       number of evolutionary generations; range [1, 100], default 5
 * @param populationSize    individuals per generation; range [2, 500], default 50
 * @param maxTimeMins       hard wall-clock limit for TPOT2 in minutes; range [1, 1440], default 60
 * @param cvFolds           cross-validation folds; range [2, 10], default 5
 * @param scoringMetric     sklearn scoring string (e.g. "roc_auc", "f1_macro", "r2");
 *                          null means TPOT2 picks the default for the estimator type
 * @param nJobs             parallelism degree; -1 = all CPUs, default -1
 * @param pythonExecutable  Python binary on PATH to invoke; default "python3"
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record Tpot2Config(
        Tpot2TaskType taskType,
        int generations,
        int populationSize,
        int maxTimeMins,
        int cvFolds,
        String scoringMetric,
        int nJobs,
        String pythonExecutable
) {

    /**
     * Compact constructor validates all parameters.
     *
     * @throws NullPointerException     if taskType or pythonExecutable is null
     * @throws IllegalArgumentException if any numeric parameter is out of range,
     *                                  or if pythonExecutable is blank
     */
    public Tpot2Config {
        if (taskType == null) {
            throw new NullPointerException("taskType is required");
        }
        if (generations < 1 || generations > 100) {
            throw new IllegalArgumentException(
                "generations must be 1-100, got: " + generations);
        }
        if (populationSize < 2 || populationSize > 500) {
            throw new IllegalArgumentException(
                "populationSize must be 2-500, got: " + populationSize);
        }
        if (maxTimeMins < 1 || maxTimeMins > 1440) {
            throw new IllegalArgumentException(
                "maxTimeMins must be 1-1440, got: " + maxTimeMins);
        }
        if (cvFolds < 2 || cvFolds > 10) {
            throw new IllegalArgumentException(
                "cvFolds must be 2-10, got: " + cvFolds);
        }
        if (pythonExecutable == null || pythonExecutable.isBlank()) {
            throw new IllegalArgumentException("pythonExecutable must not be blank");
        }
    }

    /**
     * Returns a default configuration for the given task type.
     *
     * <p>Defaults: generations=5, populationSize=50, maxTimeMins=60, cvFolds=5,
     * scoringMetric=null (TPOT2 default), nJobs=-1, pythonExecutable="python3".
     *
     * @param taskType process mining task to configure for (must not be null)
     * @return default configuration
     * @throws NullPointerException if taskType is null
     */
    public static Tpot2Config defaults(Tpot2TaskType taskType) {
        return new Tpot2Config(taskType, 5, 50, 60, 5, null, -1, "python3");
    }

    /**
     * Returns a default configuration for case outcome prediction.
     * Scoring metric: "roc_auc" (binary classification).
     *
     * @return configuration optimised for CASE_OUTCOME
     */
    public static Tpot2Config forCaseOutcome() {
        return new Tpot2Config(Tpot2TaskType.CASE_OUTCOME, 5, 50, 60, 5, "roc_auc", -1, "python3");
    }

    /**
     * Returns a default configuration for remaining time prediction.
     * Scoring metric: "neg_mean_absolute_error" (regression).
     *
     * @return configuration optimised for REMAINING_TIME
     */
    public static Tpot2Config forRemainingTime() {
        return new Tpot2Config(Tpot2TaskType.REMAINING_TIME, 5, 50, 60, 5,
            "neg_mean_absolute_error", -1, "python3");
    }

    /**
     * Returns a default configuration for next activity prediction.
     * Scoring metric: "f1_macro" (multi-class classification).
     *
     * @return configuration optimised for NEXT_ACTIVITY
     */
    public static Tpot2Config forNextActivity() {
        return new Tpot2Config(Tpot2TaskType.NEXT_ACTIVITY, 5, 50, 60, 5,
            "f1_macro", -1, "python3");
    }

    /**
     * Returns a default configuration for anomaly detection.
     * Scoring metric: "roc_auc" (binary normal/anomaly classification).
     *
     * @return configuration optimised for ANOMALY_DETECTION
     */
    public static Tpot2Config forAnomalyDetection() {
        return new Tpot2Config(Tpot2TaskType.ANOMALY_DETECTION, 5, 50, 60, 5,
            "roc_auc", -1, "python3");
    }
}
