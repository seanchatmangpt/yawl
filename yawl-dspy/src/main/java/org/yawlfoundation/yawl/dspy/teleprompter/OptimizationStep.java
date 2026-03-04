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

import org.yawlfoundation.yawl.dspy.signature.Example;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A step in the optimization process (for debugging/analysis).
 *
 * @param stepNumber the step index
 * @param description what happened in this step
 * @param examplesAdded examples added to the module
 * @param scoreBefore score before this step
 * @param scoreAfter score after this step
 * @param timestamp when this step occurred
 * @param metadata additional information
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record OptimizationStep(
    int stepNumber,
    String description,
    List<Example> examplesAdded,
    double scoreBefore,
    double scoreAfter,
    Instant timestamp,
    Map<String, Object> metadata
) {

    public OptimizationStep {
        examplesAdded = examplesAdded != null ? List.copyOf(examplesAdded) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Get the score improvement from this step.
     */
    public double improvement() {
        return scoreAfter - scoreBefore;
    }
}
