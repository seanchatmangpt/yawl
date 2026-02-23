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
 * Eight specialized agents implementing the OT lifestyle redesign swarm.
 *
 * <p>Each agent implements {@link org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmAgent}
 * and handles exactly one {@link org.yawlfoundation.yawl.mcp.a2a.therapy.SwarmPhase}.</p>
 *
 * <h2>Agent Implementations</h2>
 * <ul>
 *   <li>{@code OTIntakeAgent} — SwarmPhase.INTAKE: Collect patient history, referral, and goals</li>
 *   <li>{@code OTAssessmentAgent} — SwarmPhase.ASSESSMENT: COPM occupational performance assessment</li>
 *   <li>{@code OTGoalSettingAgent} — SwarmPhase.GOAL_SETTING: Identify and prioritize lifestyle goals</li>
 *   <li>{@code OTInterventionPlannerAgent} — SwarmPhase.INTERVENTION_PLANNING: Select evidence-based interventions</li>
 *   <li>{@code OTSchedulerAgent} — SwarmPhase.SCHEDULING: Schedule therapy sessions</li>
 *   <li>{@code OTProgressMonitorAgent} — SwarmPhase.PROGRESS_MONITORING: Goal attainment scaling</li>
 *   <li>{@code OTAdaptationAgent} — SwarmPhase.ADAPTATION: Adapt therapy plan dynamically</li>
 *   <li>{@code OTOutcomeEvaluatorAgent} — SwarmPhase.OUTCOME_EVALUATION: Final outcome evaluation</li>
 * </ul>
 *
 * <h2>Agent Characteristics</h2>
 * <p>All agents are:</p>
 * <ul>
 *   <li><strong>Stateless</strong> — no per-instance state; thread-safe across concurrent executions</li>
 *   <li><strong>Context-aware</strong> — read upstream results from the shared context map</li>
 *   <li><strong>Result-producing</strong> — write structured outputs to context for downstream agents</li>
 *   <li><strong>Independent</strong> — implement domain-specific logic without cross-agent dependencies</li>
 * </ul>
 *
 * <h2>Execution Order</h2>
 * <p>Agents execute in the following fixed order:</p>
 * <ol>
 *   <li>INTAKE → ASSESSMENT → GOAL_SETTING → INTERVENTION_PLANNING</li>
 *   <li>SCHEDULING → PROGRESS_MONITORING</li>
 *   <li>ADAPTATION (repeats up to maxAdaptationCycles times)</li>
 *   <li>OUTCOME_EVALUATION</li>
 * </ol>
 *
 * <p>Each agent receives the results from all prior agents in the context map.
 * The ADAPTATION agent can loop back to INTERVENTION_PLANNING if progress is
 * insufficient (subject to maxAdaptationCycles limit).</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 *
 * @see org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmAgent
 * @see org.yawlfoundation.yawl.mcp.a2a.therapy.SwarmPhase
 * @see org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmCoordinator
 */
package org.yawlfoundation.yawl.mcp.a2a.therapy.agents;
