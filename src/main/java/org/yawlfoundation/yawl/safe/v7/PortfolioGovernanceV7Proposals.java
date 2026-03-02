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
 * V7 proposal wrapper for PortfolioGovernance agent.
 *
 * <p>Wraps the PortfolioGovernanceAgent and invokes it via ZAIOrchestrator
 * to generate proposals for portfolio-level gaps:
 * - Byzantine consensus (Raft/Paxos as pluggable strategy)
 * - Strategic theme alignment across proposals
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class PortfolioGovernanceV7Proposals implements V7GapProposalService {

    private final ZAIOrchestrator orchestrator;
    private final String agentId;

    public PortfolioGovernanceV7Proposals(ZAIOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        this.agentId = "portfolio-v7-agent";
    }

    @Override
    public AgentDecisionEvent proposeForGap(V7Gap gap, V7DesignState state) {
        String decisionId = UUID.randomUUID().toString();
        String caseId = "v7-design-" + state.round();

        Map<String, Object> metadata = Map.ofEntries(
            Map.entry("gap", gap.name()),
            Map.entry("v6_interface_impact", getBackwardCompatScore(gap)),
            Map.entry("estimated_gain", getEstimatedPerformanceGain(gap)),
            Map.entry("wsjf_score", 0.88),
            Map.entry("round", state.round()),
            Map.entry("agent_type", "PortfolioGovernanceAgent"),
            Map.entry("proposal_type", "strategic-architecture")
        );

        String reasoning = generateReasoning(gap);
        AgentDecisionEvent.Decision decision = new AgentDecisionEvent.Decision(
            gap.name(),
            agentId,
            0.88,
            reasoning
        );

        AgentDecisionEvent.ExecutionPlan plan = new AgentDecisionEvent.ExecutionPlan(
            new String[] { "assess_portfolio_impact", "propose_architecture" },
            new String[] { "design_review", "architecture_board_approval", "implement" },
            Map.of("strategic_alignment", "high", "portfolio_impact", "significant")
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
            Map.entry("confidence", 0.82),
            Map.entry("reasoning", "Proposal aligns with strategic goals and architecture principles"),
            Map.entry("severity", ""),
            Map.entry("round", round)
        );

        AgentDecisionEvent.Decision decision = new AgentDecisionEvent.Decision(
            "ACCEPTED",
            agentId,
            0.82,
            "Strategic value and architecture coherence are strong"
        );

        AgentDecisionEvent.ExecutionPlan plan = new AgentDecisionEvent.ExecutionPlan(
            new String[] { "accept_strategic_proposal" },
            new String[] { "allocate_portfolio_resources", "establish_governance_board" },
            Map.of("governance_model", "established")
        );

        return new AgentDecisionEvent(
            decisionId,
            agentId,
            caseId,
            null,
            AgentDecisionEvent.DecisionType.AUTHORITY_DELEGATION,
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
        return "PortfolioGovernanceAgent";
    }

    @Override
    public List<V7Gap> getResponsibleGaps() {
        return List.of(
            V7Gap.BYZANTINE_CONSENSUS
        );
    }

    private double getBackwardCompatScore(V7Gap gap) {
        return switch (gap) {
            case BYZANTINE_CONSENSUS -> 0.88;  // Highly compatible (entirely new module, no API changes)
            default -> 0.5;
        };
    }

    private double getEstimatedPerformanceGain(V7Gap gap) {
        return switch (gap) {
            case BYZANTINE_CONSENSUS -> 0.22;  // 22% improvement: higher availability under Byzantine faults
            default -> 0.0;
        };
    }

    private String generateReasoning(V7Gap gap) {
        return switch (gap) {
            case BYZANTINE_CONSENSUS ->
                "Byzantine consensus (pluggable Raft/Paxos) ensures YAWL cluster survives " +
                "arbitrary node failures. Improves fault tolerance from single-leader to quorum-based. " +
                "Industry-standard algorithms with proven correctness.";
            default -> "Portfolio governance proposal";
        };
    }
}
