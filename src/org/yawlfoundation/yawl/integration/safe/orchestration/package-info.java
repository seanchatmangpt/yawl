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
 * SAFe ceremony orchestration for coordinating agile ceremonies.
 *
 * <p>This package provides orchestration of SAFe ceremonies including
 * sprint planning, standups, retrospectives, and PI planning by
 * coordinating agents through A2A and MCP communication.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.orchestration.SAFeCeremonyOrchestrator}
 *       - Orchestrates ceremony lifecycle and message routing</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.safe.orchestration.CeremonyEventListener}
 *       - Listener interface for ceremony events</li>
 * </ul>
 *
 * <h2>Ceremony Types</h2>
 * <ul>
 *   <li><strong>SPRINT_PLANNING</strong> - Story refinement and sprint task assignment</li>
 *   <li><strong>STANDUP</strong> - Daily sync of blockers and progress</li>
 *   <li><strong>RETROSPECTIVE</strong> - Team reflection and process improvement</li>
 *   <li><strong>ARCHITECTURE_REVIEW</strong> - Technical decision review</li>
 *   <li><strong>DEPENDENCY_SYNC</strong> - Cross-team dependency coordination</li>
 *   <li><strong>PI_PLANNING</strong> - Quarterly planning across teams</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * SAFeAgentRegistry registry = new SAFeAgentRegistry();
 * // ... register agents ...
 *
 * SAFeCeremonyOrchestrator orchestrator = new SAFeCeremonyOrchestrator(registry);
 * orchestrator.addEventListener(new LoggingEventListener());
 *
 * // Initiate a sprint planning ceremony
 * CeremonyContext context = CeremonyContext.create("SPRINT_PLANNING")
 *     .withTeamId("team-alpha")
 *     .withSprintId("sprint-01");
 *
 * String ceremonyId = orchestrator.initiateCeremony(context);
 *
 * // Dispatch story message
 * StoryCeremonyMessage msg = StoryCeremonyMessage.builder()
 *     .ceremonyId(ceremonyId)
 *     // ... configure message ...
 *     .build();
 *
 * orchestrator.dispatchMessage(ceremonyId, msg);
 *
 * // Complete ceremony
 * orchestrator.completeCeremony(ceremonyId,
 *     SAFeCeremonyOrchestrator.CeremonyOutcome.SUCCESS);
 * }</pre>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.integration.safe.orchestration;
