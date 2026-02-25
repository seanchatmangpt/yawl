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

package org.yawlfoundation.yawl.pi.prescriptive;

/**
 * Action to reroute a case from one task to an alternate task.
 *
 * <p>Used when the current task path is likely to fail; rerouting provides
 * an alternative execution path with better outcomes.
 *
 * @param caseId Case to reroute
 * @param fromTaskName Current task name
 * @param toTaskName Alternate task name
 * @param rationale Explanation for rerouting
 * @param expectedImprovementScore Expected outcome improvement (0.0-1.0)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record RerouteAction(
    String caseId,
    String fromTaskName,
    String toTaskName,
    String rationale,
    double expectedImprovementScore
) implements ProcessAction {

    /**
     * Construct with validation.
     *
     * @throws NullPointerException if any field is null
     * @throws IllegalArgumentException if expectedImprovementScore outside [0.0, 1.0]
     */
    public RerouteAction {
        if (caseId == null) throw new NullPointerException("caseId is required");
        if (fromTaskName == null) throw new NullPointerException("fromTaskName is required");
        if (toTaskName == null) throw new NullPointerException("toTaskName is required");
        if (rationale == null) throw new NullPointerException("rationale is required");
        if (expectedImprovementScore < 0.0 || expectedImprovementScore > 1.0) {
            throw new IllegalArgumentException(
                "expectedImprovementScore must be in [0.0, 1.0]: " + expectedImprovementScore);
        }
    }
}
