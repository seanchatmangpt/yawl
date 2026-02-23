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

package org.yawlfoundation.yawl.mcp.a2a.therapy;

import java.time.Duration;

/**
 * Immutable configuration for an OT swarm execution run.
 *
 * <p>This record encapsulates all configuration parameters needed to execute
 * an occupational therapy lifestyle redesign swarm. Instances are created via
 * factory methods that provide sensible defaults for different scenarios.</p>
 *
 * @param patientId patient identifier to process (non-null, non-blank)
 * @param autoAdvance if true, the coordinator auto-completes each workflow task
 *        after all agents finish their work item; if false, tasks remain for manual completion
 * @param maxAdaptationCycles maximum number of plan adaptation loops before forcing outcome
 *        evaluation (0-10 inclusive)
 * @param timeout overall execution timeout (must be positive)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record OTSwarmConfig(
    String patientId,
    boolean autoAdvance,
    int maxAdaptationCycles,
    Duration timeout
) {
    /**
     * Canonical constructor with validation.
     *
     * @throws IllegalArgumentException if patientId is null/blank, or if
     *         maxAdaptationCycles or timeout violate constraints
     */
    public OTSwarmConfig {
        if (patientId == null || patientId.isBlank()) {
            throw new IllegalArgumentException("Patient ID is required and must not be blank");
        }
        if (maxAdaptationCycles < 0 || maxAdaptationCycles > 10) {
            throw new IllegalArgumentException(
                "maxAdaptationCycles must be 0-10 (inclusive), got: " + maxAdaptationCycles);
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException(
                "Timeout must be a positive duration, got: " + timeout);
        }
    }

    /**
     * Default configuration suitable for integration testing.
     *
     * <p>Enables auto-advance with 3 adaptation cycles and a 5-minute timeout.</p>
     *
     * @param patientId the patient identifier
     * @return a new OTSwarmConfig with default parameters
     */
    public static OTSwarmConfig defaults(String patientId) {
        return new OTSwarmConfig(patientId, true, 3, Duration.ofMinutes(5));
    }

    /**
     * Configuration with one adaptation cycle suitable for fast test scenarios.
     *
     * <p>Enables auto-advance with 1 adaptation cycle and a 30-second timeout.</p>
     *
     * @param patientId the patient identifier
     * @return a new OTSwarmConfig optimized for fast execution
     */
    public static OTSwarmConfig fastTest(String patientId) {
        return new OTSwarmConfig(patientId, true, 1, Duration.ofSeconds(30));
    }
}
