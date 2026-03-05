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

/**
 * SAFe ceremony message types for A2A communication.
 *
 * <p>This package defines messages for various SAFe agile ceremonies,
 * enabling communication between agents about stories, dependencies,
 * blockers, acceptance decisions, and planning events.</p>
 *
 * <h2>Message Types</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.messages.StoryCeremonyMessage}
 *       - Sprint planning with story refinement and task breakdown</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.messages.DependencyNotification}
 *       - Cross-team dependencies for architecture reviews</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.messages.BlockerNotification}
 *       - Impediments and blockers for daily standups</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.messages.AcceptanceDecision}
 *       - Story/task completion acceptance status</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.messages.PiPlanningEvent}
 *       - Quarterly planning events and commitments</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // Create and dispatch a story message
 * StoryCeremonyMessage storyMsg = StoryCeremonyMessage.builder()
 *     .ceremonyId(ceremonyId)
 *     .fromAgentId("po-001")
 *     .storyId("STORY-123")
 *     .storyTitle("Implement user dashboard")
 *     .addAcceptanceCriterion("Dashboard displays user data")
 *     .estimatedPoints(8)
 *     .addTargetRole(SAFeAgentRole.TEAM_MEMBER)
 *     .build();
 *
 * orchestrator.dispatchMessage(ceremonyId, storyMsg);
 * }</pre>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.integration.safe.messages;
