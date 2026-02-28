package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.selfplay.model.DesignProposal;
import org.yawlfoundation.yawl.integration.selfplay.model.V7DesignState;
import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * YAWL v7 Design Agent — the PROPOSE side of self-play.
 *
 * <p>In each round, this agent proposes design solutions for every v7 gap that has not yet
 * been accepted into the design state. It uses fixed proposal templates derived from
 * architectural analysis of the v6 codebase.
 *
 * <p>Self-play strengthening: when re-proposing a gap that was previously rejected, the
 * agent incorporates the objection as additional rationale (addresses prior challenges).
 *
 * <p>Role separation: the same class acts as PROPOSER here and the companion
 * {@link V7ChallengeAgent} acts as CHALLENGER, which attacks this agent's own proposals.
 */
public class V7DesignAgent {

    /**
     * Immutable proposal template for one v7 gap.
     *
     * @param gap the v7 gap being addressed
     * @param title short human-readable title
     * @param rationale full rationale for why this design change is correct
     * @param backwardCompatScore 0.0–1.0; how backward-compatible the change is
     * @param performanceGain 0.0–1.0; relative performance improvement estimate
     */
    private record ProposalTemplate(
        V7Gap gap,
        String title,
        String rationale,
        double backwardCompatScore,
        double performanceGain
    ) {}

    /**
     * The 7 canonical templates — one per known v7 gap.
     * backwardCompatScore and performanceGain are derived from architectural analysis.
     */
    private static final List<ProposalTemplate> TEMPLATES = List.of(
        new ProposalTemplate(
            V7Gap.ASYNC_A2A_GOSSIP,
            "Replace sync HandoffRequestService with async gossip bus",
            "HandoffRequestService.sendHandoff() blocks the calling thread for up to 2s on network " +
            "partitions. Replacing with an async publish-subscribe gossip bus over A2A removes " +
            "the bottleneck and enables at-least-once delivery guarantees.",
            0.75,
            0.85
        ),
        new ProposalTemplate(
            V7Gap.MCP_SERVERS_SLACK_GITHUB_OBS,
            "Implement Slack, GitHub, and Observability MCP servers",
            "Three MCP servers are referenced in architecture docs but not yet implemented: " +
            "SlackMcpServer (case notifications), GitHubMcpServer (PR-triggered case launches), " +
            "ObservabilityMcpServer (YAWL metrics as OpenTelemetry spans). All are additive.",
            0.90,
            0.65
        ),
        new ProposalTemplate(
            V7Gap.DETERMINISTIC_REPLAY_BLAKE3,
            "Add Blake3 receipt chain to all engine decisions",
            "Engine decisions currently have no cryptographic proof chain. Add a " +
            "SHA3-256 (Blake3 upgrade path) receipt to every work item completion and case event. " +
            "Each receipt references the prior receipt hash, forming a tamper-evident audit chain.",
            0.85,
            0.40
        ),
        new ProposalTemplate(
            V7Gap.THREADLOCAL_YENGINE_PARALLELIZATION,
            "Bind YEngine instance per thread using Java 25 ScopedValue",
            "Current YEngine is shared across threads with synchronization, limiting parallel test " +
            "throughput. Using ScopedValue<YEngine> allows each test thread to bind its own " +
            "engine instance, removing contention and achieving ~30% test speedup.",
            0.70,
            0.90
        ),
        new ProposalTemplate(
            V7Gap.SHACL_COMPLIANCE_SHAPES,
            "Encode SOX/GDPR/HIPAA compliance rules as W3C SHACL shapes",
            "Compliance is currently enforced via hard-coded Java checks scattered across " +
            "ComplianceGovernanceAgent. Moving to declarative SHACL shapes on top of the " +
            "existing RDF event model allows external auditors to verify compliance rules " +
            "without reading Java source.",
            0.92,
            0.35
        ),
        new ProposalTemplate(
            V7Gap.BYZANTINE_CONSENSUS,
            "Pluggable Raft/Paxos strategy for multi-agent Byzantine consensus",
            "ZAIOrchestrator uses a simple 30-second timeout for dependency negotiation. " +
            "Replacing with a pluggable ConsensusStrategy interface (Raft default, Paxos option) " +
            "provides formal Byzantine fault tolerance for critical portfolio decisions.",
            0.72,
            0.55
        ),
        new ProposalTemplate(
            V7Gap.BURIED_ENGINES_MCP_A2A_WIRING,
            "Wire 4 buried engines (TemporalFork, EventDriven, Footprint, OcedBridge) to MCP/A2A",
            "Four fully-implemented engine variants in the codebase have no MCP or A2A " +
            "integration: TemporalForkEngine, EventDrivenAdaptationEngine, FootprintExtractor, " +
            "OcedBridgeFactory. Adding MCP tool endpoints and A2A protocol handlers for each " +
            "unlocks their capabilities for autonomous agent orchestration.",
            0.88,
            0.60
        )
    );

    private final String agentId;

    public V7DesignAgent(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        this.agentId = agentId;
    }

    public String getAgentId() {
        return agentId;
    }

    /**
     * Generate proposals for all v7 gaps that have not yet been accepted in the given state.
     *
     * <p>If a gap was previously proposed but rejected, the new proposal is strengthened
     * by incorporating the prior rejection as additional rationale.
     *
     * @param state current design state (read-only)
     * @return list of proposals for the next round (one per unaddressed gap)
     */
    public List<DesignProposal> propose(V7DesignState state) {
        Set<V7Gap> unaddressed = state.unaddressedGaps();
        if (unaddressed.isEmpty()) {
            return List.of();
        }

        int nextRound = state.round() + 1;
        List<DesignProposal> proposals = new ArrayList<>();

        for (ProposalTemplate template : TEMPLATES) {
            if (!unaddressed.contains(template.gap())) {
                continue;
            }

            // Check if this gap was previously proposed and rejected
            boolean wasRejected = state.allChallenges().stream()
                .anyMatch(c -> c.gap() == template.gap() && c.isRejected());

            String rationale = template.rationale();
            if (wasRejected) {
                long rejectionCount = state.allChallenges().stream()
                    .filter(c -> c.gap() == template.gap() && c.isRejected())
                    .count();
                rationale += String.format(
                    " [Addresses %d prior rejection(s): backward-compat and migration path " +
                    "are explicitly designed in the proposal.]",
                    rejectionCount
                );
            }

            String proposalId = template.gap().name() + "-r" + nextRound + "-" + agentId;

            proposals.add(new DesignProposal(
                proposalId,
                template.gap(),
                template.title(),
                rationale,
                template.backwardCompatScore(),
                template.performanceGain(),
                nextRound,
                Instant.now()
            ));
        }

        return proposals;
    }
}
