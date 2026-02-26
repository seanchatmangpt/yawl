/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.lifecycle;

import java.time.Duration;

/**
 * Combined configuration for the zero cognitive load life management system.
 *
 * <p>Controls all three autonomous sub-systems:</p>
 * <ul>
 *   <li>OT Lifestyle Redesign Swarm — maxAdaptationCycles, timeout</li>
 *   <li>Van der Aalst WCP Demo — runPatternDemo flag, timeout</li>
 *   <li>GregVerse Self-Play — runAdvisors flag</li>
 * </ul>
 *
 * @param timeout              per-system execution timeout (positive)
 * @param maxAdaptationCycles  OT adaptation loop cap 0–10
 * @param runPatternDemo       whether to invoke the van der Aalst WCP layer
 * @param runAdvisors          whether to invoke the GregVerse self-play layer
 * @param outputPath           JSON file path for the final result
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record LifeManagementConfig(
    Duration timeout,
    int maxAdaptationCycles,
    boolean runPatternDemo,
    boolean runAdvisors,
    String outputPath
) {
    /** Canonical constructor with validation. */
    public LifeManagementConfig {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("LifeManagementConfig.timeout must be positive");
        }
        if (maxAdaptationCycles < 0 || maxAdaptationCycles > 10) {
            throw new IllegalArgumentException(
                "LifeManagementConfig.maxAdaptationCycles must be 0–10, got: " + maxAdaptationCycles);
        }
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalArgumentException("LifeManagementConfig.outputPath must be non-blank");
        }
    }

    /**
     * Production-ready defaults: 5-minute timeout, 3 adaptation cycles,
     * all three layers enabled.
     *
     * @return default configuration
     */
    public static LifeManagementConfig defaults() {
        return new LifeManagementConfig(
            Duration.ofMinutes(5),
            3,
            true,
            true,
            "life-management-result.json"
        );
    }

    /**
     * Fast configuration for integration tests: 30-second timeout, 1 adaptation
     * cycle, all layers enabled.
     *
     * @return fast-test configuration
     */
    public static LifeManagementConfig fastTest() {
        return new LifeManagementConfig(
            Duration.ofSeconds(30),
            1,
            true,
            true,
            "test-life-result.json"
        );
    }

    /** Timeout as whole seconds (for APIs that require int). */
    public int timeoutSeconds() {
        return (int) timeout.toSeconds();
    }
}
