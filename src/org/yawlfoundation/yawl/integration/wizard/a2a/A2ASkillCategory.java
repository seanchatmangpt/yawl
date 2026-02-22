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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.wizard.a2a;

/**
 * Classification of A2A (Agent-to-Agent) skills by functional domain.
 *
 * <p>Skills are categorized by the type of workflow operation they enable,
 * allowing the wizard to recommend appropriate skills based on the selected
 * workflow pattern.
 *
 * @since YAWL 6.0
 */
public enum A2ASkillCategory {
    /**
     * Skills for launching and controlling workflow execution.
     * Examples: launch_workflow, cancel_workflow.
     */
    WORKFLOW_EXECUTION,

    /**
     * Skills for querying workflow and case state.
     * Examples: query_workflows.
     */
    WORKFLOW_QUERY,

    /**
     * Skills for managing individual work items.
     * Examples: manage_workitems.
     */
    WORKITEM_MANAGEMENT,

    /**
     * Skills for coordinating between agents.
     * Examples: handoff_workitem.
     */
    AGENT_COORDINATION
}
