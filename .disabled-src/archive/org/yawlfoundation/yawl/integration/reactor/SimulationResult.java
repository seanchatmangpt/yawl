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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.reactor;

import java.util.List;
import java.util.Objects;

/**
 * Result of shadow-simulating a proposed spec mutation.
 *
 * <p>Captures the verification and performance evaluation results from running
 * a proposed mutation against historical execution patterns or test cases.</p>
 *
 * @param soundnessOk true if the mutated spec passes soundness verification
 * @param avgCycleTimeMs average cycle time from simulation (0 if not computed)
 * @param successfulReplays number of test replays that succeeded
 * @param totalReplays total number of test replays attempted
 * @param violations list of violations detected (empty if sound)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record SimulationResult(
    boolean soundnessOk,
    double avgCycleTimeMs,
    int successfulReplays,
    int totalReplays,
    List<String> violations
) {
    /**
     * Constructs a SimulationResult with validation.
     *
     * @throws NullPointerException if violations is null
     */
    public SimulationResult {
        if (violations == null) {
            violations = List.of();
        }
    }

    /**
     * Factory method: creates a successful sound result.
     *
     * @param replays number of successful replays
     * @return SimulationResult with soundnessOk=true
     */
    public static SimulationResult sound(int replays) {
        return new SimulationResult(true, 0.0, replays, replays, List.of());
    }

    /**
     * Factory method: creates an unsound result with violations.
     *
     * @param violations list of detected violations
     * @return SimulationResult with soundnessOk=false
     */
    public static SimulationResult unsound(List<String> violations) {
        return new SimulationResult(false, 0.0, 0, 0, violations);
    }

    /**
     * Returns the success rate as a percentage (0-100).
     *
     * @return success rate, or 0 if no replays
     */
    public double successRate() {
        if (totalReplays == 0) {
            return 0.0;
        }
        return (100.0 * successfulReplays) / totalReplays;
    }
}
