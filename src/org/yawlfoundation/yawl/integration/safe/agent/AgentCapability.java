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
 * Agent capabilities for SAFe ceremonies and workflow activities.
 *
 * <p>Defines discrete skills and capabilities that agents can declare
 * and be matched against when assigning work or conducting ceremonies.
 *
 * @since YAWL 6.0
 */
public enum AgentCapability {
    // Story & Backlog Management
    /**
     * Ability to refine user stories and acceptance criteria.
     */
    STORY_REFINEMENT,

    /**
     * Ability to prioritize work items based on business value.
     */
    PRIORITY_MANAGEMENT,

    /**
     * Ability to write and review acceptance criteria.
     */
    ACCEPTANCE_CRITERIA_DEFINITION,

    // Sprint Planning & Execution
    /**
     * Ability to conduct sprint planning ceremonies.
     */
    SPRINT_PLANNING,

    /**
     * Ability to lead standup meetings.
     */
    STANDUP_FACILITATION,

    /**
     * Ability to track sprint progress and metrics.
     */
    SPRINT_TRACKING,

    /**
     * Ability to conduct sprint retrospectives.
     */
    RETROSPECTIVE_FACILITATION,

    // Dependencies & Coordination
    /**
     * Ability to identify and track dependencies across teams.
     */
    DEPENDENCY_TRACKING,

    /**
     * Ability to communicate across team boundaries.
     */
    CROSS_TEAM_COMMUNICATION,

    /**
     * Ability to manage PI (Program Increment) planning.
     */
    PI_PLANNING,

    /**
     * Ability to coordinate release activities.
     */
    RELEASE_COORDINATION,

    // Architecture & Design
    /**
     * Ability to design system architecture and solutions.
     */
    ARCHITECTURE_DESIGN,

    /**
     * Ability to review technical solutions and code.
     */
    TECHNICAL_REVIEW,

    /**
     * Ability to manage and prioritize technical debt.
     */
    TECHNICAL_DEBT_MANAGEMENT,

    /**
     * Ability to evaluate and recommend technology choices.
     */
    TECHNOLOGY_EVALUATION,

    // Stakeholder Engagement
    /**
     * Ability to communicate with stakeholders and customers.
     */
    STAKEHOLDER_COMMUNICATION,

    /**
     * Ability to gather and validate business requirements.
     */
    REQUIREMENT_ANALYSIS,

    /**
     * Ability to manage business relationships.
     */
    BUSINESS_RELATIONSHIP_MANAGEMENT,

    // Process Improvement
    /**
     * Ability to analyze and improve processes.
     */
    PROCESS_IMPROVEMENT,

    /**
     * Ability to coach teams on agile practices.
     */
    TEAM_COACHING,

    /**
     * Ability to define and analyze metrics.
     */
    METRICS_ANALYSIS,

    /**
     * Ability to facilitate organizational change.
     */
    CHANGE_FACILITATION,

    // Quality & Testing
    /**
     * Ability to define and execute test strategies.
     */
    TEST_STRATEGY,

    /**
     * Ability to automate testing activities.
     */
    TEST_AUTOMATION,

    /**
     * Ability to perform quality assurance and verification.
     */
    QUALITY_ASSURANCE,

    /**
     * Ability to perform security testing and review.
     */
    SECURITY_TESTING,

    // Implementation & Development
    /**
     * Ability to implement software features.
     */
    FEATURE_IMPLEMENTATION,

    /**
     * Ability to review code quality and standards.
     */
    CODE_REVIEW,

    /**
     * Ability to troubleshoot and fix defects.
     */
    DEFECT_RESOLUTION,

    /**
     * Ability to perform system integration and deployment.
     */
    DEPLOYMENT,

    // Risk & Compliance
    /**
     * Ability to identify and manage risks.
     */
    RISK_MANAGEMENT,

    /**
     * Ability to ensure compliance with regulations and standards.
     */
    COMPLIANCE_VERIFICATION,

    /**
     * Ability to perform security and audit assessments.
     */
    AUDIT_ASSESSMENT
}
