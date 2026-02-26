/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.scoring;

import org.yawlfoundation.yawl.ggen.powl.PowlModel;

/**
 * A functional interface for scoring POWL models in reinforcement learning.
 * Scores evaluate how well a candidate POWL model matches a process description.
 * Scores are normalized to the range [0.0, 1.0], where 1.0 is a perfect match.
 */
@FunctionalInterface
public interface RewardFunction {
    /**
     * Scores a candidate POWL model against a process description.
     *
     * @param candidate           the POWL model to evaluate (must not be null)
     * @param processDescription  the reference process description (must not be null)
     * @return a score in [0.0, 1.0]; 1.0 indicates perfect match, 0.0 indicates no match
     * @throws IllegalArgumentException  if candidate or processDescription is null
     * @throws RuntimeException          if scoring encounters an unrecoverable error
     */
    double score(PowlModel candidate, String processDescription);
}
