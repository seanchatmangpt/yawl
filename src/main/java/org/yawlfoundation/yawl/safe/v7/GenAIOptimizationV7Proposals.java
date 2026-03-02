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
 * V7 proposal wrapper for GenAIOptimization agent.
 *
 * <p>Wraps the GenAIOptimizationAgent and invokes it via ZAIOrchestrator
 * to generate proposals for performance-optimization gaps:
 * - ThreadLocal YEngine (30% test parallelization speedup)
 * - Async A2A gossip protocol (sync handoff bottleneck)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GenAIOptimizationV7Proposals implements V7GapProposalService {

    private final ZAIOrchestrator orchestrator;
    private final String agentId;

    public GenAIOptimizationV7Proposals(ZAIOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
        this.agentId = "gen-ai-v7-agent";
    }

    @Override
    public AgentDecisionEvent proposeForGap(V7Gap gap, V7DesignState state) {
        String decisionId = UUID.randomUUID().toString();
        String caseId = "v7-design-" + state.round();

        // Simulate agent proposal (in real implementation, would invoke via A2A)
        Map<String, Object> metadata = Map.ofEntries(
            Map.entry("gap", gap.name()),
            Map.entry("v6_interface_impact", getBackwardCompatScore(gap)),
            Map.entry("estimated_gain", getEstimatedPerformanceGain(gap)),
            Map.entry("wsjf_score", 0.85),
            Map.entry("round", state.round()),
            Map.entry("agent_type", "GenAIOptimizationAgent"),
            Map.entry("proposal_type", "performance-optimization")
        );

        String reasoning = generateReasoning(gap);
        AgentDecisionEvent.Decision decision = new AgentDecisionEvent.Decision(
            gap.name(),
            agentId,
            0.85,
            reasoning
        );

        AgentDecisionEvent.ExecutionPlan plan = new AgentDecisionEvent.ExecutionPlan(
            new String[] { "analyze_gap", "propose_solution" },
            new String[] { "implement", "test", "validate" },
            Map.of("implementation_complexity", "medium", "risk_level", "low")
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
            Map.entry("confidence", 0.8),
            Map.entry("reasoning", "Proposal aligns with performance optimization goals and has acceptable backward compatibility"),
            Map.entry("severity", ""),
            Map.entry("round", round)
        );

        AgentDecisionEvent.Decision decision = new AgentDecisionEvent.Decision(
            "ACCEPTED",
            agentId,
            0.8,
            "Performance gain is justified and risks are acceptable"
        );

        AgentDecisionEvent.ExecutionPlan plan = new AgentDecisionEvent.ExecutionPlan(
            new String[] { "accept_proposal" },
            new String[] { "schedule_implementation" },
            Map.of("timeline", "sprint_N+1")
        );

        return new AgentDecisionEvent(
            decisionId,
            agentId,
            caseId,
            null,
            AgentDecisionEvent.DecisionType.PRIORITY_ORDERING,
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
        return "GenAIOptimizationAgent";
    }

    @Override
    public List<V7Gap> getResponsibleGaps() {
        return List.of(
            V7Gap.THREADLOCAL_YENGINE_PARALLELIZATION,
            V7Gap.ASYNC_A2A_GOSSIP
        );
    }

    private double getBackwardCompatScore(V7Gap gap) {
        return switch (gap) {
            case THREADLOCAL_YENGINE_PARALLELIZATION -> 0.95;  // Highly compatible (internal only)
            case ASYNC_A2A_GOSSIP -> 0.80;                     // Mostly compatible (API change)
            default -> 0.5;                                     // Neutral
        };
    }

    private double getEstimatedPerformanceGain(V7Gap gap) {
        return switch (gap) {
            case THREADLOCAL_YENGINE_PARALLELIZATION -> 0.30;  // 30% improvement
            case ASYNC_A2A_GOSSIP -> 0.25;                     // 25% improvement
            default -> 0.0;
        };
    }

    /**
     * Generate reasoning for a V7 gap proposal.
     *
     * <p>When GROQ_API_KEY is set, calls GroqLlmGateway (openai/gpt-oss-20b) via reflection
     * to produce LLM-generated reasoning. Falls back to deterministic text when Groq is
     * unavailable (no API key, network failure, or rate limit).
     */
    private String generateReasoning(V7Gap gap) {
        if (isGroqAvailable()) {
            try {
                return callGroqForReasoning(gap);
            } catch (Exception e) {
                // fall through to deterministic fallback
            }
        }
        return switch (gap) {
            case THREADLOCAL_YENGINE_PARALLELIZATION ->
                "ThreadLocal YEngine enables 30% test parallelization by eliminating shared engine state. " +
                "Each test thread gets isolated YEngine instance. High backward compatibility due to internal nature.";
            case ASYNC_A2A_GOSSIP ->
                "Async A2A gossip protocol replaces sync handoff bottleneck. Uses eventual consistency model " +
                "similar to Apache Cassandra. Estimated 25% throughput improvement in high-load scenarios.";
            default -> "Performance optimization proposal";
        };
    }

    private boolean isGroqAvailable() {
        try {
            Class<?> gwClass = Class.forName("org.yawlfoundation.yawl.ggen.rl.GroqLlmGateway");
            return (boolean) gwClass.getMethod("isAvailable").invoke(null);
        } catch (Exception e) {
            return false;
        }
    }

    private String callGroqForReasoning(V7Gap gap) throws Exception {
        Class<?> gwClass = Class.forName("org.yawlfoundation.yawl.ggen.rl.GroqLlmGateway");
        Object gw = gwClass.getMethod("fromEnv", java.time.Duration.class)
                           .invoke(null, java.time.Duration.ofSeconds(30));
        String prompt = String.format(
            "You are a SAFe enterprise architect analyzing YAWL v7 design gaps. " +
            "For gap '%s', provide exactly 2 sentences of technical reasoning for the " +
            "proposed solution, covering: (1) the performance gain mechanism, " +
            "(2) backward compatibility impact. Be concise and specific.", gap.name());
        return (String) gwClass.getMethod("send", String.class, double.class)
                               .invoke(gw, prompt, 0.7);
    }
}
