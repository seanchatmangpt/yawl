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
 * Sealed interface for recommended process interventions.
 *
 * <p>Represents an action that can be taken on a workflow case to improve
 * outcomes (e.g., reroute to alternate task, escalate, reallocate resources).
 * Enables exhaustive pattern matching on action types.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public sealed interface ProcessAction
    permits RerouteAction, EscalateAction, ReallocateResourceAction, NoOpAction {

    /**
     * Get the case ID this action applies to.
     *
     * @return Case identifier
     */
    String caseId();

    /**
     * Get the rationale for this action.
     *
     * @return Human-readable explanation
     */
    String rationale();

    /**
     * Get the expected improvement score (0.0 to 1.0).
     *
     * @return Confidence that this action will improve outcomes
     */
    double expectedImprovementScore();
}
