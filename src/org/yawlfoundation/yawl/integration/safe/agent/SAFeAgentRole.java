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

package org.yawlfoundation.yawl.integration.safe.agent;

/**
 * SAFe (Scaled Agile Framework) agent roles in the YAWL system.
 *
 * <p>Defines all roles that autonomous agents can assume to participate
 * in agile ceremonies and business processes (sprint planning, standups,
 * retrospectives, PI planning, etc.)
 *
 * @since YAWL 6.0
 */
public enum SAFeAgentRole {
    /**
     * Product Owner - Manages backlog, priorities, and stakeholder communication.
     * Responsibilities: story refinement, acceptance criteria definition, priority management.
     */
    PRODUCT_OWNER,

    /**
     * Scrum Master - Facilitates ceremonies, removes blockers, coaches team.
     * Responsibilities: sprint planning facilitation, standup coordination, impediment tracking.
     */
    SCRUM_MASTER,

    /**
     * Development Team Member - Executes tasks, completes work items.
     * Responsibilities: task implementation, code quality, sprint execution.
     */
    TEAM_MEMBER,

    /**
     * Release Train Engineer - Coordinates across teams and sprints.
     * Responsibilities: dependency tracking, cross-team communication, PI planning.
     */
    RELEASE_TRAIN_ENGINEER,

    /**
     * Architect - Designs solutions, reviews technical decisions.
     * Responsibilities: solution architecture, technical debt management, design reviews.
     */
    ARCHITECT,

    /**
     * Agile Coach - Mentors teams, improves processes.
     * Responsibilities: process improvement, team coaching, metrics analysis.
     */
    AGILE_COACH,

    /**
     * Business Analyst - Gathers requirements, analyzes business needs.
     * Responsibilities: requirement analysis, process documentation, business rule validation.
     */
    BUSINESS_ANALYST,

    /**
     * QA Tester - Verifies quality, defines test strategies.
     * Responsibilities: test execution, quality verification, test automation.
     */
    QA_TESTER
}
