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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Record of a single reactor cycle execution.
 *
 * <p>Captures metrics snapshot, proposed mutations, simulation results,
 * and the outcome of the cycle (whether a mutation was proposed/committed).</p>
 *
 * @param cycleId unique identifier for this cycle
 * @param startTime when the cycle began
 * @param metricsSnapshot workflow metrics at cycle time
 * @param proposedMutation the mutation proposed (null if no drift detected)
 * @param simulationResult result of simulating the proposed mutation
 *                         (null if no mutation proposed)
 * @param committed true if the mutation was committed to the workflow spec
 * @param durationMs how long the cycle took to execute
 * @param outcome describes the cycle result
 *              (NO_DRIFT, MUTATION_REJECTED, MUTATION_COMMITTED, MUTATION_PROPOSED)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record ReactorCycle(
    String cycleId,
    Instant startTime,
    Map<String, Double> metricsSnapshot,
    SpecMutation proposedMutation,
    SimulationResult simulationResult,
    boolean committed,
    long durationMs,
    String outcome
) {
    /**
     * Constructs a ReactorCycle with validation.
     *
     * @throws NullPointerException if cycleId or startTime are null
     */
    public ReactorCycle {
        Objects.requireNonNull(cycleId, "cycleId must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");

        if (metricsSnapshot == null) {
            metricsSnapshot = Map.of();
        }
        if (outcome == null || outcome.isBlank()) {
            outcome = "UNKNOWN";
        }
    }

    /**
     * Returns true if this cycle proposed or committed a mutation.
     *
     * @return true iff proposedMutation is not null
     */
    public boolean hasMutation() {
        return proposedMutation != null;
    }

    /**
     * Returns true if this cycle was successful (sound mutation, if proposed).
     *
     * @return true iff no mutation or simulation succeeded
     */
    public boolean isSuccessful() {
        if (simulationResult == null) {
            return !hasMutation(); // No mutation = success
        }
        return simulationResult.soundnessOk();
    }
}
