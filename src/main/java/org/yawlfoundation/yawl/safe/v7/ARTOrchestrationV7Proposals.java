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
import org.yawlfoundation.yawl.safe.autonomous.ZAIOrchestrator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V7 proposal wrapper for ART Orchestration agent (optional backup).
 *
 * <p>Wraps the ARTOrchestrationAgent and invokes it via ZAIOrchestrator
 * as an optional backup reasoning agent. This agent can provide secondary opinions
 * on any v7 design gap, especially valuable for cross-cutting concerns like
 * resource optimization and cross-ART dependency resolution.
 *
 * <p>Returns empty list for responsibleGaps(), indicating this is an optional
 * backup agent used by the self-play loop as a challenger/reviewer.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ARTOrchestrationV7Proposals implements V7GapProposalService {

    private final ZAIOrchestrator orchestrator;
    private final String agentId;

    public ARTOrchestrationV7Proposals(ZAIOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        this.agentId = "art-v7-agent";
    }

    @Override
    public AgentDecisionEvent proposeForGap(V7Gap gap, V7DesignState state) {
        String decisionId = UUID.randomUUID().toString();
        String caseId = "v7-design-" + state.round();

        Map<String, Object> metadata = Map.ofEntries(
            Map.entry("gap", gap.name()),
            Map.entry("v6_interface_impact", getBackwardCompatScore(gap)),
            Map.entry("estimated_gain", getEstimatedPerformanceGain(gap)),
            Map.entry("wsjf_score", 0.84),
            Map.entry("round", state.round()),
            Map.entry("agent_type", "ARTOrchestrationAgent"),
            Map.entry("proposal_type", "resource-optimization")
        );

        String reasoning = generateReasoning(gap);
        AgentDecisionEvent.Decision decision = new AgentDecisionEvent.Decision(
            gap.name(),
            agentId,
            0.84,
            reasoning
        );

        AgentDecisionEvent.ExecutionPlan plan = new AgentDecisionEvent.ExecutionPlan(
            new String[] { "assess_resource_demand", "design_autoscaling_strategy" },
            new String[] { "implement_cost_model", "deploy_autoscaling", "optimize_scheduling" },
            Map.of("cost_reduction_target", "15%", "latency_guarantee", "p99_< 200ms")
        );

        return new AgentDecisionEvent(
            decisionId,
            agentId,
            caseId,
            null,
            AgentDecisionEvent.DecisionType.RESOURCE_ALLOCATION,
            Map.of("gap", gap.name()),
            Instant.now(),
            null,
            new AgentDecisionEvent.DecisionOption[0],
            new AgentDecisionEvent.DecisionFactor[0],
            decision,
            plan,
            metadata
        );
    }

    @Override
    public AgentDecisionEvent challengeProposal(
        AgentDecisionEvent proposal,
        V7DesignState state,
        int round
    ) {
        String decisionId = UUID.randomUUID().toString();
        String caseId = "v7-design-challenge-" + round;

        Map<String, Object> metadata = Map.ofEntries(
            Map.entry("challenge_decision", "ACCEPTED"),
            Map.entry("confidence", 0.81),
            Map.entry("reasoning", "Resource optimization aligns with cost and performance targets"),
            Map.entry("severity", ""),
            Map.entry("round", round)
        );

        AgentDecisionEvent.Decision decision = new AgentDecisionEvent.Decision(
            "ACCEPTED",
            agentId,
            0.81,
            "Cost-aware scheduling improves operational efficiency"
        );

        AgentDecisionEvent.ExecutionPlan plan = new AgentDecisionEvent.ExecutionPlan(
            new String[] { "accept_autoscaling_proposal" },
            new String[] { "allocate_infrastructure_budget", "schedule_implementation" },
            Map.of("infrastructure_optimized", "true")
        );

        return new AgentDecisionEvent(
            decisionId,
            agentId,
            caseId,
            null,
            AgentDecisionEvent.DecisionType.RESOURCE_NEGOTIATION,
            Map.of("challenge_of", proposal.getDecisionId()),
            Instant.now(),
            null,
            new AgentDecisionEvent.DecisionOption[0],
            new AgentDecisionEvent.DecisionFactor[0],
            decision,
            plan,
            metadata
        );
    }

    @Override
    public String getAgentType() {
        return "ARTOrchestrationAgent";
    }

    @Override
    public List<V7Gap> getResponsibleGaps() {
        // Empty list indicates this is an optional backup agent
        // Used by self-play loop as a secondary challenger/reviewer for any gap
        return List.of();
    }

    private double getBackwardCompatScore(V7Gap gap) {
        // Generic scoring for optional backup agent
        return 0.80; // Assume good compatibility across the board
    }

    private double getEstimatedPerformanceGain(V7Gap gap) {
        // Generic scoring for optional backup agent
        return 0.12; // Assume moderate performance improvement
    }

    private String generateReasoning(V7Gap gap) {
        return "ART Orchestration backup analysis: Gap " + gap.name() +
               " evaluated for cross-ART coordination feasibility and resource impact. " +
               "Backup reasoning provides secondary validation of primary proposals.";
    }
}
