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

package org.yawlfoundation.yawl.pi.predictive;

import java.util.List;

/**
 * Immutable training dataset for predictive models.
 *
 * <p>Tabular format with feature vectors (rows) and corresponding labels (completed/failed).
 * Used for training ONNX models and validating prediction accuracy.
 *
 * @param featureNames Column names (e.g., "caseDurationMs", "taskCount")
 * @param rows Feature vectors, one row per case
 * @param labels Outcome labels ("completed" or "failed") per row, same length as rows
 * @param specificationId Workflow specification this dataset is extracted from
 * @param caseCount Total number of cases in dataset
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record TrainingDataset(
    List<String> featureNames,
    List<double[]> rows,
    List<String> labels,
    String specificationId,
    int caseCount
) {

    /**
     * Construct with validation.
     *
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if rows and labels have different lengths
     */
    public TrainingDataset {
        if (featureNames == null) throw new NullPointerException("featureNames is required");
        if (rows == null) throw new NullPointerException("rows is required");
        if (labels == null) throw new NullPointerException("labels is required");
        if (specificationId == null) throw new NullPointerException("specificationId is required");
        if (rows.size() != labels.size()) {
            throw new IllegalArgumentException(
                "rows and labels must have equal length: " +
                rows.size() + " != " + labels.size());
        }
    }
}
