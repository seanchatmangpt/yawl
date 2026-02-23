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

package org.yawlfoundation.yawl.mcp.a2a.therapy;

/**
 * Represents a phase in the occupational therapy lifestyle redesign workflow.
 *
 * <p>Each phase represents a distinct swarm execution stage where agents collaborate
 * to advance the therapy plan.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public enum SwarmPhase {
    /** Agent 1: Patient intake and clinical risk stratification. */
    INTAKE("intake"),

    /** Agent 2: COPM occupational performance assessment. */
    ASSESSMENT("assessment"),

    /** Agent 3: Collaborative SMART lifestyle goal identification. */
    GOAL_SETTING("goal_setting"),

    /** Agent 4: Evidence-based intervention selection. */
    INTERVENTION_PLANNING("intervention_planning"),

    /** Agent 5: Therapy session scheduling and resource allocation. */
    SCHEDULING("scheduling"),

    /** Agent 6: Goal attainment scaling and progress evaluation. */
    PROGRESS_MONITORING("progress_monitoring"),

    /** Agent 7: Dynamic therapy plan adaptation. */
    ADAPTATION("adaptation"),

    /** Agent 8: Final COPM re-assessment and discharge planning. */
    OUTCOME_EVALUATION("outcome_evaluation");

    private final String displayName;

    SwarmPhase(String displayName) {
        this.displayName = displayName;
    }

    /** Get the display name for this phase. */
    public String displayName() {
        return displayName;
    }
}
