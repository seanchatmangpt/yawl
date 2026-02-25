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
 * Action to reallocate task resources from one resource to another.
 *
 * <p>Used when the current resource handling a task is overwhelmed or
 * less capable; reassignment to a better-equipped resource can improve outcomes.
 *
 * @param caseId Case whose task is being reallocated
 * @param taskName Task being reallocated
 * @param fromResourceId Current resource ID
 * @param toResourceId Alternate resource ID
 * @param rationale Explanation for reallocation
 * @param expectedImprovementScore Expected outcome improvement (0.0-1.0)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record ReallocateResourceAction(
    String caseId,
    String taskName,
    String fromResourceId,
    String toResourceId,
    String rationale,
    double expectedImprovementScore
) implements ProcessAction {

    /**
     * Construct with validation.
     *
     * @throws NullPointerException if any field is null
     * @throws IllegalArgumentException if expectedImprovementScore outside [0.0, 1.0]
     */
    public ReallocateResourceAction {
        if (caseId == null) throw new NullPointerException("caseId is required");
        if (taskName == null) throw new NullPointerException("taskName is required");
        if (fromResourceId == null) throw new NullPointerException("fromResourceId is required");
        if (toResourceId == null) throw new NullPointerException("toResourceId is required");
        if (rationale == null) throw new NullPointerException("rationale is required");
        if (expectedImprovementScore < 0.0 || expectedImprovementScore > 1.0) {
            throw new IllegalArgumentException(
                "expectedImprovementScore must be in [0.0, 1.0]: " + expectedImprovementScore);
        }
    }
}
