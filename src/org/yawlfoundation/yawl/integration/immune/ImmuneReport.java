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

package org.yawlfoundation.yawl.integration.immune;

import java.util.Collections;
import java.util.List;

/**
 * Cumulative report from the WorkflowImmuneSystem tracking deadlock predictions
 * over a period of time.
 *
 * <p>Summarizes:
 * <ul>
 *   <li>All deadlock predictions emitted</li>
 *   <li>Number of cases scanned</li>
 *   <li>Number of deadlocks avoided (ERROR-severity findings emitted to listener)</li>
 * </ul>
 *
 * @param predictions    list of all DeadlockPrediction records emitted (non-null, may be empty)
 * @param casesScanned   total number of cases analyzed
 * @param deadlocksAvoided number of ERROR-level findings detected and reported
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record ImmuneReport(
    List<DeadlockPrediction> predictions,
    int casesScanned,
    int deadlocksAvoided
) {
    /**
     * Canonical constructor that makes predictions immutable.
     */
    public ImmuneReport {
        if (predictions == null) {
            predictions = List.of();
        } else {
            predictions = Collections.unmodifiableList(predictions);
        }
    }
}
