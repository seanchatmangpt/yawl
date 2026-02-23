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
 * Occupational Therapy Lifestyle Redesign Swarm — YAWL MCP/A2A integration.
 *
 * <p>This package implements a fully autonomous 8-agent swarm for occupational
 * therapy (OT) lifestyle redesign. The swarm adapts the multi-agent orchestration
 * pattern to the clinical OT domain, enabling autonomous agents to collaborate
 * in delivering comprehensive occupational therapy interventions.</p>
 *
 * <h2>Swarm Architecture</h2>
 * <p>The swarm follows the YAWL workflow pattern AGT-5 (Multi-Agent Orchestration),
 * adapted for the OT lifecycle. Each phase is handled by a dedicated autonomous agent:</p>
 * <ol>
 *   <li>{@code OTIntakeAgent} (SwarmPhase.INTAKE) — patient intake and history collection</li>
 *   <li>{@code OTAssessmentAgent} (SwarmPhase.ASSESSMENT) — COPM occupational assessment</li>
 *   <li>{@code OTGoalSettingAgent} (SwarmPhase.GOAL_SETTING) — collaborative lifestyle goal identification</li>
 *   <li>{@code OTInterventionPlannerAgent} (SwarmPhase.INTERVENTION_PLANNING) — evidence-based intervention selection</li>
 *   <li>{@code OTSchedulerAgent} (SwarmPhase.SCHEDULING) — therapy session scheduling</li>
 *   <li>{@code OTProgressMonitorAgent} (SwarmPhase.PROGRESS_MONITORING) — goal attainment scaling</li>
 *   <li>{@code OTAdaptationAgent} (SwarmPhase.ADAPTATION) — dynamic plan adaptation</li>
 *   <li>{@code OTOutcomeEvaluatorAgent} (SwarmPhase.OUTCOME_EVALUATION) — final outcome evaluation and discharge</li>
 * </ol>
 *
 * <h2>Execution Flow</h2>
 * <p>The {@link org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmCoordinator} orchestrates
 * the workflow across 8 agents. Each agent executes sequentially, with downstream
 * agents able to read and build on upstream results via a shared context map.</p>
 *
 * <pre>
 * Start
 *   ↓
 * [Agent 1: INTAKE] → context: patient history
 *   ↓
 * [Agent 2: ASSESSMENT] → context: occupational profile, COPM scores
 *   ↓
 * [Agent 3: GOAL_SETTING] → context: lifestyle goals, priorities
 *   ↓
 * [Agent 4: INTERVENTION_PLANNING] → context: evidence-based interventions
 *   ↓
 * [Agent 5: SCHEDULING] → context: therapy sessions, resource allocation
 *   ↓
 * [Agent 6: PROGRESS_MONITORING] → context: goal attainment scaling
 *   ↓
 * [Agent 7: ADAPTATION] (repeats up to maxAdaptationCycles)
 *   ↓
 * [Agent 8: OUTCOME_EVALUATION] → final outcome report, discharge planning
 *   ↓
 * End (success=true) or Error (success=false)
 * </pre>
 *
 * <h2>Entry Points</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmCoordinator} — execute the swarm</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmConfig} — configuration parameters</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.therapy.SwarmPhase} — phase enumeration</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.therapy.OTSwarmAgent} — agent interface</li>
 * </ul>
 *
 * <h2>Domain Model</h2>
 * <p>See {@link org.yawlfoundation.yawl.mcp.a2a.therapy.domain} for the occupational
 * therapy domain types: {@link org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient},
 * {@link org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OccupationalProfile},
 * {@link org.yawlfoundation.yawl.mcp.a2a.therapy.domain.LifestyleGoal},
 * {@link org.yawlfoundation.yawl.mcp.a2a.therapy.domain.TherapySession}, and
 * {@link org.yawlfoundation.yawl.mcp.a2a.therapy.domain.SwarmTaskResult}.</p>
 *
 * <h2>YAWL Workflow Integration</h2>
 * <p>The swarm is driven by a YAWL workflow specification (OT_LifestyleRedesign.yawl)
 * that orchestrates the 8 agents via the YAWL engine's work item protocol. Each agent
 * maps to one YAWL task. The coordinator launches the workflow, provides patient input
 * to the first task, monitors task completions, and collects results from each agent.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 *
 * @see org.yawlfoundation.yawl.mcp.a2a.therapy.domain
 * @see org.yawlfoundation.yawl.mcp.a2a.therapy.agents
 */
package org.yawlfoundation.yawl.mcp.a2a.therapy;
