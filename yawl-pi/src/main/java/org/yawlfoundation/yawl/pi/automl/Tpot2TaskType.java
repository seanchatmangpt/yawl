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
 * Process mining use cases that TPOT2 AutoML can optimise.
 *
 * <p>Each value determines which sklearn estimator family the Python runner selects:
 * classification ({@link #CASE_OUTCOME}, {@link #NEXT_ACTIVITY}, {@link #ANOMALY_DETECTION})
 * or regression ({@link #REMAINING_TIME}).
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public enum Tpot2TaskType {

    /**
     * Predict whether a workflow case will complete successfully or fail/be cancelled.
     * TPOT2 uses TPOTClassifier with binary labels: "completed" and "failed".
     */
    CASE_OUTCOME,

    /**
     * Estimate the remaining wall-clock time until case completion.
     * TPOT2 uses TPOTRegressor targeting a remaining_time_ms numeric column.
     */
    REMAINING_TIME,

    /**
     * Predict the most likely next activity in an incomplete case trace.
     * TPOT2 uses TPOTClassifier with multi-class labels (activity names).
     */
    NEXT_ACTIVITY,

    /**
     * Detect anomalous case behaviour — traces that deviate from the process model.
     * TPOT2 uses TPOTClassifier with binary labels: "normal" and "anomaly".
     */
    ANOMALY_DETECTION;

    /**
     * Returns true if this task type uses a classifier (vs. a regressor).
     *
     * <p>Used by {@code tpot2_runner.py} to select the correct estimator without
     * duplicating the switch logic in both Java and Python.
     *
     * @return true for CASE_OUTCOME, NEXT_ACTIVITY, ANOMALY_DETECTION; false for REMAINING_TIME
     */
    public boolean isClassification() {
        return this != REMAINING_TIME;
    }
}
