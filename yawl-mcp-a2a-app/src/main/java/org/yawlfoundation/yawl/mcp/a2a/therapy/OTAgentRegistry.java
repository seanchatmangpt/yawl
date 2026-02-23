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

import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTAdaptationAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTAssessmentAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTGoalSettingAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTIntakeAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTInterventionPlannerAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTOutcomeEvaluatorAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTProgressMonitorAgent;
import org.yawlfoundation.yawl.mcp.a2a.therapy.agents.OTSchedulerAgent;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Registry mapping each {@link SwarmPhase} to its dedicated {@link OTSwarmAgent}.
 *
 * <p>Instantiates all 8 OT swarm agents on construction and provides thread-safe,
 * immutable access to the agent roster for the coordinator. Each phase has exactly
 * one agent responsible for that workflow stage.</p>
 *
 * <h2>Agent Roster</h2>
 * <ul>
 *   <li>INTAKE → OTIntakeAgent (patient intake and risk stratification)</li>
 *   <li>ASSESSMENT → OTAssessmentAgent (COPM occupational assessment)</li>
 *   <li>GOAL_SETTING → OTGoalSettingAgent (collaborative goal identification)</li>
 *   <li>INTERVENTION_PLANNING → OTInterventionPlannerAgent (evidence-based planning)</li>
 *   <li>SCHEDULING → OTSchedulerAgent (session scheduling and allocation)</li>
 *   <li>PROGRESS_MONITORING → OTProgressMonitorAgent (goal attainment scaling)</li>
 *   <li>ADAPTATION → OTAdaptationAgent (dynamic plan adaptation)</li>
 *   <li>OUTCOME_EVALUATION → OTOutcomeEvaluatorAgent (final COPM re-assessment)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTAgentRegistry {

    private final Map<SwarmPhase, OTSwarmAgent> agents;

    /**
     * Constructs the registry and registers all 8 swarm agents.
     *
     * <p>Each agent is instantiated once and stored in an immutable map for
     * thread-safe access throughout the swarm execution lifecycle.</p>
     */
    public OTAgentRegistry() {
        Map<SwarmPhase, OTSwarmAgent> map = new EnumMap<>(SwarmPhase.class);
        map.put(SwarmPhase.INTAKE, new OTIntakeAgent());
        map.put(SwarmPhase.ASSESSMENT, new OTAssessmentAgent());
        map.put(SwarmPhase.GOAL_SETTING, new OTGoalSettingAgent());
        map.put(SwarmPhase.INTERVENTION_PLANNING, new OTInterventionPlannerAgent());
        map.put(SwarmPhase.SCHEDULING, new OTSchedulerAgent());
        map.put(SwarmPhase.PROGRESS_MONITORING, new OTProgressMonitorAgent());
        map.put(SwarmPhase.ADAPTATION, new OTAdaptationAgent());
        map.put(SwarmPhase.OUTCOME_EVALUATION, new OTOutcomeEvaluatorAgent());
        this.agents = Collections.unmodifiableMap(map);
    }

    /**
     * Returns the agent registered for the given phase.
     *
     * @param phase the swarm phase
     * @return the agent (never null)
     * @throws NoSuchElementException if no agent is registered for the phase
     */
    public OTSwarmAgent getAgent(SwarmPhase phase) {
        if (phase == null) {
            throw new IllegalArgumentException("Phase cannot be null");
        }
        OTSwarmAgent agent = agents.get(phase);
        if (agent == null) {
            throw new NoSuchElementException("No agent registered for phase: " + phase);
        }
        return agent;
    }

    /**
     * Returns an unmodifiable view of all registered agents.
     *
     * <p>The returned map contains all 8 agents mapped by their phases.
     * External modification is not permitted.</p>
     *
     * @return immutable map of agents
     */
    public Map<SwarmPhase, OTSwarmAgent> getAllAgents() {
        return agents;
    }

    /**
     * Returns the total number of registered agents (expected: 8).
     *
     * @return count of agents in this registry
     */
    public int size() {
        return agents.size();
    }
}
