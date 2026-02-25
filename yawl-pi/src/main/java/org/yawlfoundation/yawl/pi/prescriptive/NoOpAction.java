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
 * Action representing "do nothing" - let the case proceed normally.
 *
 * <p>Used when risk score is low and no intervention is needed.
 * Always present as a baseline option in recommendation lists.
 *
 * @param caseId Case ID
 * @param rationale Explanation for not intervening
 * @param expectedImprovementScore Baseline score (typically 0.1)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public record NoOpAction(
    String caseId,
    String rationale,
    double expectedImprovementScore
) implements ProcessAction {

    /**
     * Construct with validation.
     *
     * @throws NullPointerException if caseId or rationale is null
     * @throws IllegalArgumentException if expectedImprovementScore outside [0.0, 1.0]
     */
    public NoOpAction {
        if (caseId == null) throw new NullPointerException("caseId is required");
        if (rationale == null) throw new NullPointerException("rationale is required");
        if (expectedImprovementScore < 0.0 || expectedImprovementScore > 1.0) {
            throw new IllegalArgumentException(
                "expectedImprovementScore must be in [0.0, 1.0]: " + expectedImprovementScore);
        }
    }
}
