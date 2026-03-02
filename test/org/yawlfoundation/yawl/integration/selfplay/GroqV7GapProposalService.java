package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.coordination.events.AgentDecisionEvent;
import org.yawlfoundation.yawl.integration.groq.GroqService;
import org.yawlfoundation.yawl.integration.selfplay.model.V7DesignState;
import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;
import org.yawlfoundation.yawl.safe.v7.V7GapProposalService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V7GapProposalService backed by Groq LLM for real A2A-style proposal and challenge generation.
 *
 * <p>Covers all 7 YAWL v7 gaps. Uses {@link GroqService#chatWithContext} for LLM calls:
 * <ul>
 *   <li><b>PROPOSE</b>: Groq generates technical reasoning; static scores from V7DesignAgent templates.</li>
 *   <li><b>CHALLENGE</b>: Groq adversarially reviews proposal; parses ACCEPTED/MODIFIED/REJECTED verdict.</li>
 * </ul>
 *
 * <p>On any Groq failure, falls back to acceptance with deterministic reasoning so the self-play
 * loop completes. Used by {@link V7SelfPlayGroqTest} (skipped when GROQ_API_KEY is absent).
 */
public class GroqV7GapProposalService implements V7GapProposalService {

    private static final String AGENT_ID   = "groq-v7-agent";
    private static final String AGENT_TYPE = "GroqLlmAgent";

    private static final String PROPOSE_SYSTEM =
        "You are a senior YAWL workflow engine architect. " +
        "Analyze a YAWL v7 design gap and provide exactly 2 sentences of technical justification " +
        "for the proposed solution: (1) how it improves the system, (2) backward compatibility impact. " +
        "Be specific and concise. No preamble, no bullet points — just 2 sentences.";

    private static final String CHALLENGE_SYSTEM =
        "You are an adversarial YAWL architect performing a design review. " +
        "Review the proposed solution for a YAWL v7 gap. " +
        "Start your response with exactly one word on its own line: ACCEPTED, MODIFIED, or REJECTED. " +
        "Then provide 1-2 sentences explaining your verdict. " +
        "Most well-reasoned proposals with high backward compatibility should be ACCEPTED.";

    // Static backward-compat and performance-gain scores per gap (from V7DesignAgent templates)
    private static final Map<V7Gap, double[]> GAP_SCORES = Map.ofEntries(
        Map.entry(V7Gap.ASYNC_A2A_GOSSIP,                    new double[]{0.75, 0.85}),
        Map.entry(V7Gap.MCP_SERVERS_SLACK_GITHUB_OBS,        new double[]{0.90, 0.65}),
        Map.entry(V7Gap.DETERMINISTIC_REPLAY_BLAKE3,         new double[]{0.85, 0.40}),
        Map.entry(V7Gap.THREADLOCAL_YENGINE_PARALLELIZATION, new double[]{0.70, 0.90}),
        Map.entry(V7Gap.SHACL_COMPLIANCE_SHAPES,             new double[]{0.92, 0.35}),
        Map.entry(V7Gap.BYZANTINE_CONSENSUS,                 new double[]{0.72, 0.55}),
        Map.entry(V7Gap.BURIED_ENGINES_MCP_A2A_WIRING,       new double[]{0.88, 0.60})
    );

    private final GroqService groq;

    public GroqV7GapProposalService(GroqService groq) {
        this.groq = groq;
    }

    @Override
    public AgentDecisionEvent proposeForGap(V7Gap gap, V7DesignState state) {
        double[] scores = GAP_SCORES.getOrDefault(gap, new double[]{0.75, 0.50});
        double compat = scores[0];
        double gain   = scores[1];
        String reasoning = generateProposalReasoning(gap, compat, gain);

        Map<String, Object> metadata = Map.ofEntries(
            Map.entry("gap",                gap.name()),
            Map.entry("v6_interface_impact", compat),
            Map.entry("estimated_gain",      gain),
            Map.entry("wsjf_score",          0.85),
            Map.entry("round",               state.round()),
            Map.entry("agent_type",          AGENT_TYPE)
        );

        AgentDecisionEvent.Decision decision = new AgentDecisionEvent.Decision(
            gap.name(), AGENT_ID, compat, reasoning);

        AgentDecisionEvent.ExecutionPlan plan = new AgentDecisionEvent.ExecutionPlan(
            new String[]{"analyze_gap", "propose_solution"},
            new String[]{"implement", "test", "validate"},
            Map.of("gain", gain, "compat", compat));

        return new AgentDecisionEvent(
            UUID.randomUUID().toString(),
            AGENT_ID,
            "v7-design-" + state.round(),
            null,
            AgentDecisionEvent.DecisionType.RESOURCE_ALLOCATION,
            Map.of("gap", gap.name()),
            Instant.now(),
            null,
            new AgentDecisionEvent.DecisionOption[0],
            new AgentDecisionEvent.DecisionFactor[0],
            decision,
            plan,
            metadata);
    }

    @Override
    public AgentDecisionEvent challengeProposal(AgentDecisionEvent proposal,
                                                V7DesignState state,
                                                int round) {
        String groqResponse   = generateChallengeVerdict(proposal, round);
        String verdict        = extractVerdict(groqResponse);
        String reasoning      = extractReasoning(groqResponse);

        Map<String, Object> metadata = Map.ofEntries(
            Map.entry("challenge_decision", verdict),
            Map.entry("confidence",         0.80),
            Map.entry("reasoning",          reasoning),
            Map.entry("severity",           "REJECTED".equals(verdict) ? "high" : ""),
            Map.entry("round",              round)
        );

        AgentDecisionEvent.Decision decision = new AgentDecisionEvent.Decision(
            verdict, AGENT_ID, 0.80, reasoning);

        AgentDecisionEvent.ExecutionPlan plan = new AgentDecisionEvent.ExecutionPlan(
            new String[]{"review_proposal", "issue_verdict"},
            new String[]{"schedule_implementation"},
            Map.of("timeline", "sprint_N+1"));

        return new AgentDecisionEvent(
            UUID.randomUUID().toString(),
            AGENT_ID,
            "v7-challenge-" + round,
            null,
            AgentDecisionEvent.DecisionType.PRIORITY_ORDERING,
            Map.of("challenge_of", proposal.getDecisionId()),
            Instant.now(),
            null,
            new AgentDecisionEvent.DecisionOption[0],
            new AgentDecisionEvent.DecisionFactor[0],
            decision,
            plan,
            metadata);
    }

    @Override
    public String getAgentType() {
        return AGENT_TYPE;
    }

    @Override
    public List<V7Gap> getResponsibleGaps() {
        return List.of(
            V7Gap.ASYNC_A2A_GOSSIP,
            V7Gap.MCP_SERVERS_SLACK_GITHUB_OBS,
            V7Gap.DETERMINISTIC_REPLAY_BLAKE3,
            V7Gap.THREADLOCAL_YENGINE_PARALLELIZATION,
            V7Gap.SHACL_COMPLIANCE_SHAPES,
            V7Gap.BYZANTINE_CONSENSUS,
            V7Gap.BURIED_ENGINES_MCP_A2A_WIRING
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String generateProposalReasoning(V7Gap gap, double compat, double gain) {
        try {
            String prompt = String.format(
                "YAWL v7 gap: %s (%s). Backward compat: %.0f%%, performance gain: %.0f%%. " +
                "In 2 sentences, justify this design change.",
                gap.name(), gap.description, compat * 100, gain * 100);
            return groq.chatWithContext(PROPOSE_SYSTEM, prompt);
        } catch (Exception e) {
            return String.format(
                "Addresses %s with %.0f%% estimated performance improvement. " +
                "Backward compatibility score: %.0f%% — minimal API surface change.",
                gap.name(), gain * 100, compat * 100);
        }
    }

    private String generateChallengeVerdict(AgentDecisionEvent proposal, int round) {
        try {
            String gap      = (String) proposal.getMetadata().getOrDefault("gap", "unknown");
            String reason   = proposal.getFinalDecision().getReasoning();
            double compat   = ((Number) proposal.getMetadata()
                                   .getOrDefault("v6_interface_impact", 0.75)).doubleValue();
            String prompt   = String.format(
                "YAWL v7 proposal for gap %s (round %d, compat %.0f%%): \"%s\". Issue verdict.",
                gap, round, compat * 100, reason);
            return groq.chatWithContext(CHALLENGE_SYSTEM, prompt);
        } catch (Exception e) {
            return "ACCEPTED\nFallback acceptance — Groq unavailable during challenge phase.";
        }
    }

    /** Extract ACCEPTED, MODIFIED, or REJECTED from the first line of Groq response. */
    private static String extractVerdict(String groqResponse) {
        if (groqResponse == null) return "ACCEPTED";
        String firstLine = groqResponse.strip();
        int nl = firstLine.indexOf('\n');
        if (nl > 0) firstLine = firstLine.substring(0, nl).strip();
        String upper = firstLine.toUpperCase();
        if (upper.contains("REJECTED")) return "REJECTED";
        if (upper.contains("MODIFIED")) return "MODIFIED";
        return "ACCEPTED";
    }

    /** Extract reasoning after the verdict line. */
    private static String extractReasoning(String groqResponse) {
        if (groqResponse == null || groqResponse.isBlank()) return "No reasoning provided.";
        int nl = groqResponse.indexOf('\n');
        if (nl > 0 && nl < groqResponse.length() - 1) {
            return groqResponse.substring(nl + 1).strip();
        }
        return groqResponse.strip();
    }
}
