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

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration policy for the Living Process Reactor.
 *
 * <p>Controls thresholds, sampling requirements, and automation behavior
 * for the continuous workflow optimization cycle.</p>
 *
 * @param driftThreshold percentage increase in avg execution time (0-100) that triggers mutation
 * @param minSamplesBeforeMutation minimum completed cases before proposing mutation
 * @param autoCommit whether to automatically commit validated mutations
 * @param maxAutoCommitRisk highest risk level for mutations eligible for auto-commit
 * @param cycleInterval how often the reactor checks metrics and runs a cycle
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record ReactorPolicy(
    double driftThreshold,
    int minSamplesBeforeMutation,
    boolean autoCommit,
    SpecMutation.RiskLevel maxAutoCommitRisk,
    Duration cycleInterval
) {
    /**
     * Constructs a ReactorPolicy with validation.
     *
     * @throws NullPointerException if maxAutoCommitRisk or cycleInterval are null
     * @throws IllegalArgumentException if driftThreshold or minSamples are invalid
     */
    public ReactorPolicy {
        Objects.requireNonNull(maxAutoCommitRisk, "maxAutoCommitRisk must not be null");
        Objects.requireNonNull(cycleInterval, "cycleInterval must not be null");

        if (driftThreshold < 0 || driftThreshold > 100) {
            throw new IllegalArgumentException("driftThreshold must be in range [0, 100]");
        }
        if (minSamplesBeforeMutation < 1) {
            throw new IllegalArgumentException("minSamplesBeforeMutation must be >= 1");
        }
        if (cycleInterval.isNegative() || cycleInterval.isZero()) {
            throw new IllegalArgumentException("cycleInterval must be positive");
        }
    }

    /**
     * Returns the default reactor policy.
     *
     * @return policy with: driftThreshold=20%, minSamples=10, autoCommit=false, maxRisk=LOW, interval=5min
     */
    public static ReactorPolicy defaults() {
        return new ReactorPolicy(
            0.20,
            10,
            false,
            SpecMutation.RiskLevel.LOW,
            Duration.ofMinutes(5)
        );
    }

    /**
     * Returns a lenient policy for testing/development.
     *
     * @return policy with lower thresholds for faster feedback
     */
    public static ReactorPolicy lenient() {
        return new ReactorPolicy(
            0.10,
            3,
            false,
            SpecMutation.RiskLevel.MEDIUM,
            Duration.ofSeconds(30)
        );
    }
}
