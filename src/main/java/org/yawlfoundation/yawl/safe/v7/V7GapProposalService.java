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

package org.yawlfoundation.yawl.safe.v7;

import org.yawlfoundation.yawl.integration.coordination.events.AgentDecisionEvent;
import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;
import org.yawlfoundation.yawl.integration.selfplay.model.V7DesignState;

/**
 * Service interface for coordinating V7 design proposals from autonomous agents.
 *
 * <p>This interface defines how each autonomous agent type proposes solutions for
 * YAWL v7 design gaps and how proposals are challenged during the self-play loop.
 *
 * <p>Implementations follow the wrapper pattern: each V7GapProposalService wraps
 * a corresponding Z.AI agent and invokes it via ZAIOrchestrator to get AgentDecisionEvent
 * proposals and challenges.
 *
 * <h2>Implementation Pattern</h2>
 * <pre>
 * class GenAIOptimizationV7Proposals implements V7GapProposalService {
 *   private ZAIOrchestrator orchestrator;
 *
 *   public AgentDecisionEvent proposeForGap(V7Gap gap, V7DesignState state) {
 *     var agent = orchestrator.recruitAgent(
 *       AgentCapability.GEN_AI_OPTIMIZATION,
 *       "gen-ai-v7-design-agent"
 *     );
 *     return agent.submitProposal("v7-gap-proposal",
 *       Map.of("gap", gap.name(), "state", state));
 *   }
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public interface V7GapProposalService {

    /**
     * Propose a v7 design solution for the given gap.
     *
     * <p>The implementing agent analyzes the gap and current design state,
     * then proposes a solution with reasoning, WSJF score, and estimated impact.
     *
     * @param gap the v7 design gap to address
     * @param state current cumulative design state
     * @return AgentDecisionEvent containing the proposal with metadata:
     *         - "gap": gap name (String)
     *         - "v6_interface_impact": backward-compat score (0.0-1.0)
     *         - "estimated_gain": performance gain (0.0-1.0)
     *         - "wsjf_score": WSJF weighted score (0.0-1.0)
     *         - "round": self-play round number (int)
     *         - "agent_type": agent type that made proposal (String)
     */
    AgentDecisionEvent proposeForGap(V7Gap gap, V7DesignState state);

    /**
     * Challenge a v7 design proposal (adversarial review phase).
     *
     * <p>The implementing agent reviews the proposal critically, checking for:
     * - Feasibility within engineering constraints
     * - Alignment with YAWL architecture principles
     * - Compatibility with other accepted proposals
     * - Risk assessment and mitigation
     *
     * @param proposal the proposal to challenge (from proposeForGap)
     * @param state current cumulative design state
     * @param round self-play round number
     * @return AgentDecisionEvent containing the challenge decision with metadata:
     *         - "challenge_decision": "ACCEPTED" | "REJECTED" | "MODIFIED" (String)
     *         - "confidence": confidence in decision (0.0-1.0)
     *         - "severity": if rejected, severity level (String)
     *         - "reasoning": explanation of challenge decision (String)
     *         - "suggested_modifications": if MODIFIED, proposed changes (String)
     */
    AgentDecisionEvent challengeProposal(
        AgentDecisionEvent proposal,
        V7DesignState state,
        int round
    );

    /**
     * Get the agent type that this service wraps.
     * Used for routing proposals to appropriate challengers.
     */
    String getAgentType();

    /**
     * Get the list of V7Gaps that this agent is responsible for.
     * Empty list means this agent can challenge any gap.
     */
    java.util.List<V7Gap> getResponsibleGaps();
}
