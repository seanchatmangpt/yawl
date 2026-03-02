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

package org.yawlfoundation.yawl.integration.adaptation;

/**
 * Severity levels for process events in the event-driven adaptation engine.
 * Severity determines the urgency and type of adaptation action required.
 *
 * <p>Levels are ordered from lowest to highest severity: LOW &lt; MEDIUM &lt; HIGH &lt; CRITICAL.</p>
 *
 * @author YAWL Foundation
 * @since 6.0
 */
public enum EventSeverity {
    /**
     * Low severity: informational events, no immediate action required.
     * Example: routine status updates, metrics within normal range.
     */
    LOW(1),

    /**
     * Medium severity: noteworthy events requiring monitoring or minor adjustments.
     * Example: performance degradation, non-critical warnings.
     */
    MEDIUM(2),

    /**
     * High severity: significant events requiring immediate attention and adaptation.
     * Example: resource exhaustion, workflow anomalies, fraud indicators.
     */
    HIGH(3),

    /**
     * Critical severity: extreme events requiring urgent intervention.
     * Example: security breaches, system failures, critical business rule violations.
     */
    CRITICAL(4);

    private final int level;

    EventSeverity(int level) {
        this.level = level;
    }

    /**
     * Returns the numeric level of this severity (1-4, where 1=LOW, 4=CRITICAL).
     * Use for comparing severity: {@code event.severity().level() >= threshold}.
     *
     * @return the numeric level of this severity
     */
    public int level() {
        return level;
    }

    /**
     * Checks if this severity is at least the given threshold severity.
     *
     * @param threshold the minimum severity level required
     * @return true if this severity is greater than or equal to threshold
     */
    public boolean isAtLeast(EventSeverity threshold) {
        return this.level >= threshold.level;
    }
}
