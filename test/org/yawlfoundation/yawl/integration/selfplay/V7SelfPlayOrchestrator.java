package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.coordination.events.AgentDecisionEvent;
import org.yawlfoundation.yawl.integration.selfplay.model.DesignChallenge;
import org.yawlfoundation.yawl.integration.selfplay.model.FitnessScore;
import org.yawlfoundation.yawl.integration.selfplay.model.V7DesignState;
import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;
import org.yawlfoundation.yawl.integration.selfplay.model.V7SimulationReport;
import org.yawlfoundation.yawl.safe.autonomous.ZAIOrchestrator;
import org.yawlfoundation.yawl.safe.v7.V7GapProposalService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * YAWL v7 Self-Play Orchestrator — Z.AI-integrated design loop.
 *
 * <p>Runs a self-play design loop where autonomous agents (via Z.AI framework)
 * both propose and challenge YAWL v7 specifications (AlphaGo-style). Each agent
 * is recruited dynamically via ZAIOrchestrator, invoked via A2A message passing,
 * and produces AgentDecisionEvent decisions (immutable audit trail).
 *
 * <p>Workflow phases:
 * <ol>
 *   <li><b>INITIALIZE</b> — seed the design state from the v6 gap inventory</li>
 *   <li><b>PROPOSE</b> — agents propose solutions for unaddressed gaps via A2A</li>
 *   <li><b>CHALLENGE</b> — agents adversarially review proposals via A2A</li>
 *   <li><b>EVALUATE</b> — accepted proposals merge into cumulative design state</li>
 *   <li><b>MEASURE_FITNESS</b> — V7FitnessEvaluator scores using AgentDecisionEvent metadata</li>
 *   <li><b>XOR split</b> — if fitness ≥ threshold → FINALIZE; else → loop to PROPOSE</li>
 * </ol>
 *
 * <p>Convergence: {@code fitness.total() >= fitnessThreshold} (default: 0.85).
 * Output: V7SimulationReport with all AgentDecisionEvent proposals, challenges,
 * ZAI audit log event IDs, and final fitness score.
 *
 * <p><b>Dogfooding</b>: Autonomous agents design YAWL using YAWL concepts.
 */
public class V7SelfPlayOrchestrator {

    private static final double DEFAULT_FITNESS_THRESHOLD = 0.85;
    private static final int DEFAULT_MAX_ROUNDS = 5;

    private final ZAIOrchestrator zaiOrchestrator;
    private final Map<V7Gap, V7GapProposalService> gapAgentMap;
    private final List<V7GapProposalService> proposalServices;
    private final double fitnessThreshold;
    private final int maxRounds;

    /**
     * Create an orchestrator with a ZAI framework and proposal services.
     *
     * @param zaiOrchestrator the Z.AI framework for agent recruitment
     * @param proposalServices list of V7GapProposalService implementations
     */
    public V7SelfPlayOrchestrator(
        ZAIOrchestrator zaiOrchestrator,
        List<V7GapProposalService> proposalServices
    ) {
        this(zaiOrchestrator, proposalServices, DEFAULT_FITNESS_THRESHOLD, DEFAULT_MAX_ROUNDS);
    }

    /**
     * Create an orchestrator with custom convergence parameters.
     *
     * @param zaiOrchestrator the Z.AI framework for agent recruitment
     * @param proposalServices list of V7GapProposalService implementations
     * @param fitnessThreshold fitness score at which loop converges
     * @param maxRounds maximum self-play rounds before forced termination
     */
    public V7SelfPlayOrchestrator(
        ZAIOrchestrator zaiOrchestrator,
        List<V7GapProposalService> proposalServices,
        double fitnessThreshold,
        int maxRounds
    ) {
        if (zaiOrchestrator == null) {
            throw new IllegalArgumentException("zaiOrchestrator is required");
        }
        if (proposalServices == null || proposalServices.isEmpty()) {
            throw new IllegalArgumentException("proposalServices must not be empty");
        }
        if (fitnessThreshold <= 0.0 || fitnessThreshold > 1.0) {
            throw new IllegalArgumentException("fitnessThreshold must be in (0.0, 1.0]");
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds must be >= 1");
        }

        this.zaiOrchestrator = zaiOrchestrator;
        this.proposalServices = proposalServices;
        this.fitnessThreshold = fitnessThreshold;
        this.maxRounds = maxRounds;

        // Build gap → proposal service map
        this.gapAgentMap = buildGapAgentMap(proposalServices);
    }

    /**
     * Build a map of V7Gap → primary responsible V7GapProposalService.
     */
    private static Map<V7Gap, V7GapProposalService> buildGapAgentMap(
        List<V7GapProposalService> services
    ) {
        Map<V7Gap, V7GapProposalService> map = new HashMap<>();
        for (V7GapProposalService service : services) {
            for (V7Gap gap : service.getResponsibleGaps()) {
                map.putIfAbsent(gap, service);  // First service wins
            }
        }
        return map;
    }

    /**
     * Run the self-play design loop to completion.
     * Uses Z.AI agents via A2A message passing to propose and challenge specifications.
     *
     * @return simulation report with all agent decisions, audit trail, and final fitness
     */
    public V7SimulationReport runLoop() {
        Instant started = Instant.now();

        // INITIALIZE: seed design state from v6 gap inventory
        V7DesignState state = V7DesignState.initial();
        List<String> auditLogEventIds = new ArrayList<>();

        boolean converged = false;

        // YAWL XOR-loop: repeat until fitness >= threshold or rounds exhausted
        for (int round = 1; round <= maxRounds; round++) {

            // PROPOSE phase: agents propose designs for all unaddressed gaps via A2A
            List<AgentDecisionEvent> proposals = proposeForGaps(state, round);

            if (proposals.isEmpty()) {
                // All gaps addressed — XOR-split routes to FINALIZE
                converged = state.fitnessScore().hasConverged(fitnessThreshold);
                break;
            }

            // CHALLENGE phase: agents adversarially review proposals via A2A
            List<AgentDecisionEvent> challenges = challengeProposals(proposals, state, round);

            // EVALUATE phase: determine which proposals passed challenges
            List<AgentDecisionEvent> newlyAccepted = resolveAccepted(proposals, challenges);

            // MEASURE_FITNESS phase: evaluate cumulative fitness
            List<AgentDecisionEvent> cumulativeAccepted = new ArrayList<>(state.acceptedProposals());
            cumulativeAccepted.addAll(newlyAccepted);

            FitnessScore fitness = V7FitnessEvaluator.evaluate(cumulativeAccepted, state.allChallenges());

            // Build audit event IDs list for this round
            List<String> roundEventIds = new ArrayList<>();
            for (AgentDecisionEvent event : proposals) {
                roundEventIds.add(event.getDecisionId());
            }

            // Build agent IDs and proposals-by-agent map
            List<String> roundAgentIds = new ArrayList<>();
            Map<String, AgentDecisionEvent> roundProposalsByAgent = new HashMap<>();
            for (AgentDecisionEvent proposal : proposals) {
                String agentId = proposal.getAgentId();
                if (!roundAgentIds.contains(agentId)) {
                    roundAgentIds.add(agentId);
                }
                roundProposalsByAgent.put(agentId, proposal);
            }

            auditLogEventIds.addAll(roundEventIds);

            // Create challenge records from agent decisions
            List<DesignChallenge> challengeRecords = challenges.stream()
                .map(this::toChallengeRecord)
                .collect(Collectors.toList());

            // Advance state
            state = state.nextRound(
                proposals,
                challengeRecords,
                newlyAccepted,
                fitness,
                roundEventIds,
                roundAgentIds,
                roundProposalsByAgent
            );

            // XOR split: fitness >= threshold → FINALIZE
            if (fitness.hasConverged(fitnessThreshold)) {
                converged = true;
                break;
            }
        }

        long durationMs = Instant.now().toEpochMilli() - started.toEpochMilli();

        return new V7SimulationReport(
            state.round(),
            converged,
            state.fitnessScore(),
            state.acceptedProposals(),
            state.allChallenges(),
            auditLogEventIds,
            state.addressedGaps(),
            durationMs,
            Instant.now()
        );
    }

    /**
     * PROPOSE phase: invoke agents via A2A to propose solutions for unaddressed gaps.
     */
    private List<AgentDecisionEvent> proposeForGaps(V7DesignState state, int round) {
        List<AgentDecisionEvent> proposals = new ArrayList<>();

        Set<V7Gap> unaddressed = state.unaddressedGaps();
        for (V7Gap gap : unaddressed) {
            V7GapProposalService service = gapAgentMap.get(gap);
            if (service != null) {
                AgentDecisionEvent proposal = service.proposeForGap(gap, state);
                proposals.add(proposal);
            }
        }

        return proposals;
    }

    /**
     * CHALLENGE phase: invoke agents via A2A to adversarially review proposals.
     * Each proposal is assigned to a different agent for challenge (self-play).
     */
    private List<AgentDecisionEvent> challengeProposals(
        List<AgentDecisionEvent> proposals,
        V7DesignState state,
        int round
    ) {
        List<AgentDecisionEvent> challenges = new ArrayList<>();

        for (AgentDecisionEvent proposal : proposals) {
            // Round-robin: assign challenge to a different proposal service for diversity
            V7GapProposalService challenger = selectChallenger(proposal, round);
            if (challenger != null) {
                AgentDecisionEvent challenge = challenger.challengeProposal(proposal, state, round);
                challenges.add(challenge);
            }
        }

        return challenges;
    }

    /**
     * Select a challenger agent for the given proposal (round-robin with diversity).
     */
    private V7GapProposalService selectChallenger(AgentDecisionEvent proposal, int round) {
        if (proposalServices.isEmpty()) {
            return null;
        }
        // Use proposal agent ID hash + round for pseudo-random selection
        int index = (proposal.getDecisionId().hashCode() + round) % proposalServices.size();
        return proposalServices.get(Math.abs(index));
    }

    /**
     * EVALUATE phase: determine which proposals survived the challenge.
     * A proposal is accepted if challenge decision is "ACCEPTED" or "MODIFIED".
     */
    private static List<AgentDecisionEvent> resolveAccepted(
        List<AgentDecisionEvent> proposals,
        List<AgentDecisionEvent> challenges
    ) {
        Set<String> rejectedIds = challenges.stream()
            .filter(challenge -> {
                Object decision = challenge.getMetadata().get("challenge_decision");
                return "REJECTED".equals(decision);
            })
            .map(AgentDecisionEvent::getDecisionId)
            .collect(Collectors.toSet());

        return proposals.stream()
            .filter(p -> !rejectedIds.contains(p.getDecisionId()))
            .collect(Collectors.toList());
    }

    /**
     * Convert an AgentDecisionEvent challenge to a DesignChallenge record for legacy compat.
     */
    private DesignChallenge toChallengeRecord(AgentDecisionEvent challenge) {
        Object decision = challenge.getMetadata().get("challenge_decision");
        String verdict = (String) decision;
        return new DesignChallenge(
            challenge.getDecisionId(),
            challenge.getAgentId(),
            verdict.equals("ACCEPTED"),
            verdict.equals("REJECTED"),
            (String) challenge.getMetadata().getOrDefault("reasoning", "")
        );
    }

    public double getFitnessThreshold() {
        return fitnessThreshold;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public ZAIOrchestrator getZaiOrchestrator() {
        return zaiOrchestrator;
    }

    public List<V7GapProposalService> getProposalServices() {
        return proposalServices;
    }
}
